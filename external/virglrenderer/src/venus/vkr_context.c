/*
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: MIT
 */

#include "vkr_context.h"

#include "pipe/p_state.h"
#include "venus-protocol/vn_protocol_renderer_dispatches.h"
#include "virgl_protocol.h" /* for transfer_mode */
#include "vrend_iov.h"

#include "vkr_buffer.h"
#include "vkr_command_buffer.h"
#include "vkr_context.h"
#include "vkr_cs.h"
#include "vkr_descriptor_set.h"
#include "vkr_device.h"
#include "vkr_device_memory.h"
#include "vkr_image.h"
#include "vkr_instance.h"
#include "vkr_physical_device.h"
#include "vkr_pipeline.h"
#include "vkr_query_pool.h"
#include "vkr_queue.h"
#include "vkr_render_pass.h"
#include "vkr_ring.h"
#include "vkr_transport.h"

void
vkr_context_add_instance(struct vkr_context *ctx,
                         struct vkr_instance *instance,
                         const char *name)
{
   vkr_context_add_object(ctx, &instance->base);

   assert(!ctx->instance);
   ctx->instance = instance;

   if (name && name[0] != '\0') {
      assert(!ctx->instance_name);
      ctx->instance_name = strdup(name);
   }
}

void
vkr_context_remove_instance(struct vkr_context *ctx, struct vkr_instance *instance)
{
   assert(ctx->instance && ctx->instance == instance);
   ctx->instance = NULL;

   if (ctx->instance_name) {
      free(ctx->instance_name);
      ctx->instance_name = NULL;
   }

   vkr_context_remove_object(ctx, &instance->base);
}

static void
vkr_dispatch_debug_log(UNUSED struct vn_dispatch_context *dispatch, const char *msg)
{
   vkr_log(msg);
}

static void
vkr_context_init_dispatch(struct vkr_context *ctx)
{
   struct vn_dispatch_context *dispatch = &ctx->dispatch;

   dispatch->data = ctx;
   dispatch->debug_log = vkr_dispatch_debug_log;

   dispatch->encoder = (struct vn_cs_encoder *)&ctx->encoder;
   dispatch->decoder = (struct vn_cs_decoder *)&ctx->decoder;

   vkr_context_init_transport_dispatch(ctx);

   vkr_context_init_instance_dispatch(ctx);
   vkr_context_init_physical_device_dispatch(ctx);
   vkr_context_init_device_dispatch(ctx);

   vkr_context_init_queue_dispatch(ctx);
   vkr_context_init_fence_dispatch(ctx);
   vkr_context_init_semaphore_dispatch(ctx);
   vkr_context_init_event_dispatch(ctx);

   vkr_context_init_device_memory_dispatch(ctx);

   vkr_context_init_buffer_dispatch(ctx);
   vkr_context_init_buffer_view_dispatch(ctx);

   vkr_context_init_image_dispatch(ctx);
   vkr_context_init_image_view_dispatch(ctx);
   vkr_context_init_sampler_dispatch(ctx);
   vkr_context_init_sampler_ycbcr_conversion_dispatch(ctx);

   vkr_context_init_descriptor_set_layout_dispatch(ctx);
   vkr_context_init_descriptor_pool_dispatch(ctx);
   vkr_context_init_descriptor_set_dispatch(ctx);
   vkr_context_init_descriptor_update_template_dispatch(ctx);

   vkr_context_init_render_pass_dispatch(ctx);
   vkr_context_init_framebuffer_dispatch(ctx);

   vkr_context_init_query_pool_dispatch(ctx);

   vkr_context_init_shader_module_dispatch(ctx);
   vkr_context_init_pipeline_layout_dispatch(ctx);
   vkr_context_init_pipeline_cache_dispatch(ctx);
   vkr_context_init_pipeline_dispatch(ctx);

   vkr_context_init_command_pool_dispatch(ctx);
   vkr_context_init_command_buffer_dispatch(ctx);
}

static int
vkr_context_submit_fence_locked(struct virgl_context *base,
                                uint32_t flags,
                                uint64_t queue_id,
                                void *fence_cookie)
{
   struct vkr_context *ctx = (struct vkr_context *)base;
   struct vkr_queue *queue;
   VkResult result;

   queue = util_hash_table_get_u64(ctx->object_table, queue_id);
   if (!queue)
      return -EINVAL;
   struct vkr_device *dev = queue->device;

   struct vkr_queue_sync *sync =
      vkr_device_alloc_queue_sync(dev, flags, queue->base.id, fence_cookie);
   if (!sync)
      return -ENOMEM;

   result = vkQueueSubmit(queue->base.handle.queue, 0, NULL, sync->fence);
   if (result != VK_SUCCESS) {
      vkr_device_free_queue_sync(dev, sync);
      return -1;
   }

   if (vkr_renderer_flags & VKR_RENDERER_THREAD_SYNC) {
      mtx_lock(&queue->mutex);
      list_addtail(&sync->head, &queue->pending_syncs);
      mtx_unlock(&queue->mutex);
      cnd_signal(&queue->cond);
   } else {
      list_addtail(&sync->head, &queue->pending_syncs);
   }

   if (LIST_IS_EMPTY(&queue->busy_head))
      list_addtail(&queue->busy_head, &ctx->busy_queues);

   return 0;
}

static int
vkr_context_submit_fence(struct virgl_context *base,
                         uint32_t flags,
                         uint64_t queue_id,
                         void *fence_cookie)
{
   struct vkr_context *ctx = (struct vkr_context *)base;
   int ret;

   mtx_lock(&ctx->mutex);
   ret = vkr_context_submit_fence_locked(base, flags, queue_id, fence_cookie);
   mtx_unlock(&ctx->mutex);
   return ret;
}

static void
vkr_context_retire_fences_locked(struct virgl_context *base)
{
   struct vkr_context *ctx = (struct vkr_context *)base;
   struct vkr_queue_sync *sync, *sync_tmp;
   struct vkr_queue *queue, *queue_tmp;

   assert(!(vkr_renderer_flags & VKR_RENDERER_ASYNC_FENCE_CB));

   /* retire syncs from destroyed devices */
   LIST_FOR_EACH_ENTRY_SAFE (sync, sync_tmp, &ctx->signaled_syncs, head) {
      /* queue_id might have already get reused but is opaque to the clients */
      ctx->base.fence_retire(&ctx->base, sync->queue_id, sync->fence_cookie);
      free(sync);
   }
   list_inithead(&ctx->signaled_syncs);

   /* flush first and once because the per-queue sync threads might write to
    * it any time
    */
   if (ctx->fence_eventfd >= 0)
      flush_eventfd(ctx->fence_eventfd);

   LIST_FOR_EACH_ENTRY_SAFE (queue, queue_tmp, &ctx->busy_queues, busy_head) {
      struct vkr_device *dev = queue->device;
      struct list_head retired_syncs;
      bool queue_empty;

      vkr_queue_get_signaled_syncs(queue, &retired_syncs, &queue_empty);

      LIST_FOR_EACH_ENTRY_SAFE (sync, sync_tmp, &retired_syncs, head) {
         ctx->base.fence_retire(&ctx->base, sync->queue_id, sync->fence_cookie);
         vkr_device_free_queue_sync(dev, sync);
      }

      if (queue_empty)
         list_delinit(&queue->busy_head);
   }
}

static void
vkr_context_retire_fences(struct virgl_context *base)
{
   struct vkr_context *ctx = (struct vkr_context *)base;

   if (vkr_renderer_flags & VKR_RENDERER_ASYNC_FENCE_CB)
      return;

   mtx_lock(&ctx->mutex);
   vkr_context_retire_fences_locked(base);
   mtx_unlock(&ctx->mutex);
}

static int
vkr_context_get_fencing_fd(struct virgl_context *base)
{
   struct vkr_context *ctx = (struct vkr_context *)base;
   return ctx->fence_eventfd;
}

static int
vkr_context_submit_cmd(struct virgl_context *base, const void *buffer, size_t size)
{
   struct vkr_context *ctx = (struct vkr_context *)base;
   int ret = 0;

   mtx_lock(&ctx->mutex);

   /* CS error is considered fatal (destroy the context?) */
   if (vkr_cs_decoder_get_fatal(&ctx->decoder)) {
      mtx_unlock(&ctx->mutex);
      return EINVAL;
   }

   vkr_cs_decoder_set_stream(&ctx->decoder, buffer, size);

   while (vkr_cs_decoder_has_command(&ctx->decoder)) {
      vn_dispatch_command(&ctx->dispatch);
      if (vkr_cs_decoder_get_fatal(&ctx->decoder)) {
         ret = EINVAL;
         break;
      }
   }

   vkr_cs_decoder_reset(&ctx->decoder);

   mtx_unlock(&ctx->mutex);

   return ret;
}

static int
vkr_context_get_blob_locked(struct virgl_context *base,
                            uint64_t blob_id,
                            uint32_t flags,
                            struct virgl_context_blob *blob)
{
   struct vkr_context *ctx = (struct vkr_context *)base;
   struct vkr_device_memory *mem;
   enum virgl_resource_fd_type fd_type = VIRGL_RESOURCE_FD_INVALID;

   mem = util_hash_table_get_u64(ctx->object_table, blob_id);
   if (!mem || mem->base.type != VK_OBJECT_TYPE_DEVICE_MEMORY)
      return EINVAL;

   /* a memory can only be exported once; we don't want two resources to point
    * to the same storage.
    */
   if (mem->exported)
      return EINVAL;

   if (!mem->valid_fd_types)
      return EINVAL;

   if (flags & VIRGL_RENDERER_BLOB_FLAG_USE_MAPPABLE) {
      const bool host_visible = mem->property_flags & VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT;
      if (!host_visible)
         return EINVAL;
   }

   if (flags & VIRGL_RENDERER_BLOB_FLAG_USE_CROSS_DEVICE) {
      if (!(mem->valid_fd_types & (1 << VIRGL_RESOURCE_FD_DMABUF)))
         return EINVAL;

      fd_type = VIRGL_RESOURCE_FD_DMABUF;
   }

   if (fd_type == VIRGL_RESOURCE_FD_INVALID) {
      /* prefer dmabuf for easier mapping?  prefer opaque for performance? */
      if (mem->valid_fd_types & (1 << VIRGL_RESOURCE_FD_DMABUF))
         fd_type = VIRGL_RESOURCE_FD_DMABUF;
      else if (mem->valid_fd_types & (1 << VIRGL_RESOURCE_FD_OPAQUE))
         fd_type = VIRGL_RESOURCE_FD_OPAQUE;
   }

   int fd = -1;
   if (fd_type != VIRGL_RESOURCE_FD_INVALID) {
      VkExternalMemoryHandleTypeFlagBits handle_type;
      switch (fd_type) {
      case VIRGL_RESOURCE_FD_DMABUF:
         handle_type = VK_EXTERNAL_MEMORY_HANDLE_TYPE_DMA_BUF_BIT_EXT;
         break;
      case VIRGL_RESOURCE_FD_OPAQUE:
         handle_type = VK_EXTERNAL_MEMORY_HANDLE_TYPE_OPAQUE_FD_BIT;
         break;
      default:
         return EINVAL;
      }

      VkResult result = ctx->instance->get_memory_fd(
         mem->device,
         &(VkMemoryGetFdInfoKHR){
            .sType = VK_STRUCTURE_TYPE_MEMORY_GET_FD_INFO_KHR,
            .memory = mem->base.handle.device_memory,
            .handleType = handle_type,
         },
         &fd);
      if (result != VK_SUCCESS)
         return EINVAL;
   }

   blob->type = fd_type;
   blob->u.fd = fd;

   if (flags & VIRGL_RENDERER_BLOB_FLAG_USE_MAPPABLE) {
      const bool host_coherent =
         mem->property_flags & VK_MEMORY_PROPERTY_HOST_COHERENT_BIT;
      const bool host_cached = mem->property_flags & VK_MEMORY_PROPERTY_HOST_CACHED_BIT;

      /* XXX guessed */
      if (host_coherent) {
         blob->map_info =
            host_cached ? VIRGL_RENDERER_MAP_CACHE_CACHED : VIRGL_RENDERER_MAP_CACHE_WC;
      } else {
         blob->map_info = VIRGL_RENDERER_MAP_CACHE_WC;
      }
   } else {
      blob->map_info = VIRGL_RENDERER_MAP_CACHE_NONE;
   }

   blob->renderer_data = mem;

   return 0;
}

static int
vkr_context_get_blob(struct virgl_context *base,
                     uint64_t blob_id,
                     uint32_t flags,
                     struct virgl_context_blob *blob)
{
   struct vkr_context *ctx = (struct vkr_context *)base;
   int ret;

   mtx_lock(&ctx->mutex);
   ret = vkr_context_get_blob_locked(base, blob_id, flags, blob);
   /* XXX unlock in vkr_context_get_blob_done on success */
   if (ret)
      mtx_unlock(&ctx->mutex);

   return ret;
}

static void
vkr_context_get_blob_done(struct virgl_context *base,
                          uint32_t res_id,
                          struct virgl_context_blob *blob)
{
   struct vkr_context *ctx = (struct vkr_context *)base;
   struct vkr_device_memory *mem = blob->renderer_data;

   mem->exported = true;
   mem->exported_res_id = res_id;
   list_add(&mem->exported_head, &ctx->newly_exported_memories);

   /* XXX locked in vkr_context_get_blob */
   mtx_unlock(&ctx->mutex);
}

static int
vkr_context_transfer_3d_locked(struct virgl_context *base,
                               struct virgl_resource *res,
                               const struct vrend_transfer_info *info,
                               int transfer_mode)
{
   struct vkr_context *ctx = (struct vkr_context *)base;
   struct vkr_resource_attachment *att;
   const struct iovec *iov;
   int iov_count;

   if (info->level || info->stride || info->layer_stride)
      return EINVAL;

   if (info->iovec) {
      iov = info->iovec;
      iov_count = info->iovec_cnt;
   } else {
      iov = res->iov;
      iov_count = res->iov_count;
   }

   if (!iov || !iov_count)
      return 0;

   att = util_hash_table_get(ctx->resource_table, uintptr_to_pointer(res->res_id));
   if (!att)
      return EINVAL;

   assert(att->resource == res);

   /* TODO transfer via dmabuf (and find a solution to coherency issues) */
   if (LIST_IS_EMPTY(&att->memories)) {
      vkr_log("unable to transfer without VkDeviceMemory (TODO)");
      return EINVAL;
   }

   struct vkr_device_memory *mem =
      LIST_ENTRY(struct vkr_device_memory, att->memories.next, exported_head);
   const VkMappedMemoryRange range = {
      .sType = VK_STRUCTURE_TYPE_MAPPED_MEMORY_RANGE,
      .memory = mem->base.handle.device_memory,
      .offset = info->box->x,
      .size = info->box->width,
   };

   void *ptr;
   VkResult result =
      vkMapMemory(mem->device, range.memory, range.offset, range.size, 0, &ptr);
   if (result != VK_SUCCESS)
      return EINVAL;

   if (transfer_mode == VIRGL_TRANSFER_TO_HOST) {
      vrend_read_from_iovec(iov, iov_count, range.offset, ptr, range.size);
      vkFlushMappedMemoryRanges(mem->device, 1, &range);
   } else {
      vkInvalidateMappedMemoryRanges(mem->device, 1, &range);
      vrend_write_to_iovec(iov, iov_count, range.offset, ptr, range.size);
   }

   vkUnmapMemory(mem->device, range.memory);

   return 0;
}

static int
vkr_context_transfer_3d(struct virgl_context *base,
                        struct virgl_resource *res,
                        const struct vrend_transfer_info *info,
                        int transfer_mode)
{
   struct vkr_context *ctx = (struct vkr_context *)base;
   int ret;

   mtx_lock(&ctx->mutex);
   ret = vkr_context_transfer_3d_locked(base, res, info, transfer_mode);
   mtx_unlock(&ctx->mutex);

   return ret;
}

static void
vkr_context_attach_resource_locked(struct virgl_context *base, struct virgl_resource *res)
{
   struct vkr_context *ctx = (struct vkr_context *)base;
   struct vkr_resource_attachment *att;

   att = util_hash_table_get(ctx->resource_table, uintptr_to_pointer(res->res_id));
   if (att) {
      assert(att->resource == res);
      return;
   }

   att = calloc(1, sizeof(*att));
   if (!att)
      return;

   /* TODO When in multi-process mode, we cannot share a virgl_resource as-is
    * to another process.  The resource must have a valid fd, and only the fd
    * and the iov can be sent the other process.
    *
    * For vrend-to-vkr sharing, we can get the fd from pipe_resource.
    */

   att->resource = res;
   list_inithead(&att->memories);

   /* associate a memory with the resource, if any */
   struct vkr_device_memory *mem;
   LIST_FOR_EACH_ENTRY (mem, &ctx->newly_exported_memories, exported_head) {
      if (mem->exported_res_id == res->res_id) {
         list_del(&mem->exported_head);
         list_addtail(&mem->exported_head, &att->memories);
         break;
      }
   }

   util_hash_table_set(ctx->resource_table, uintptr_to_pointer(res->res_id), att);
}

static void
vkr_context_attach_resource(struct virgl_context *base, struct virgl_resource *res)
{
   struct vkr_context *ctx = (struct vkr_context *)base;
   mtx_lock(&ctx->mutex);
   vkr_context_attach_resource_locked(base, res);
   mtx_unlock(&ctx->mutex);
}

static void
vkr_context_detach_resource(struct virgl_context *base, struct virgl_resource *res)
{
   struct vkr_context *ctx = (struct vkr_context *)base;

   mtx_lock(&ctx->mutex);
   util_hash_table_remove(ctx->resource_table, uintptr_to_pointer(res->res_id));
   mtx_unlock(&ctx->mutex);
}

static void
vkr_context_destroy(struct virgl_context *base)
{
   /* TODO Move the entire teardown process to a separate thread so that the main thread
    * cannot get blocked by the vkDeviceWaitIdle upon device destruction.
    */
   struct vkr_context *ctx = (struct vkr_context *)base;

   struct vkr_ring *ring, *ring_tmp;
   LIST_FOR_EACH_ENTRY_SAFE (ring, ring_tmp, &ctx->rings, head) {
      vkr_ring_stop(ring);
      vkr_ring_destroy(ring);
   }

   if (ctx->instance) {
      vkr_log("destroying context %d (%s) with a valid instance", ctx->base.ctx_id,
              vkr_context_get_name(ctx));

      vkr_instance_destroy(ctx, ctx->instance);
   }

   util_hash_table_destroy(ctx->resource_table);
   util_hash_table_destroy_u64(ctx->object_table);

   struct vkr_queue_sync *sync, *tmp;
   LIST_FOR_EACH_ENTRY_SAFE (sync, tmp, &ctx->signaled_syncs, head)
      free(sync);

   if (ctx->fence_eventfd >= 0)
      close(ctx->fence_eventfd);

   vkr_cs_decoder_fini(&ctx->decoder);

   mtx_destroy(&ctx->mutex);
   free(ctx->debug_name);
   free(ctx);
}

static void
vkr_context_init_base(struct vkr_context *ctx)
{
   ctx->base.destroy = vkr_context_destroy;
   ctx->base.attach_resource = vkr_context_attach_resource;
   ctx->base.detach_resource = vkr_context_detach_resource;
   ctx->base.transfer_3d = vkr_context_transfer_3d;
   ctx->base.get_blob = vkr_context_get_blob;
   ctx->base.get_blob_done = vkr_context_get_blob_done;
   ctx->base.submit_cmd = vkr_context_submit_cmd;

   ctx->base.get_fencing_fd = vkr_context_get_fencing_fd;
   ctx->base.retire_fences = vkr_context_retire_fences;
   ctx->base.submit_fence = vkr_context_submit_fence;
}

static void
destroy_func_object(void *val)
{
   struct vkr_object *obj = val;
   free(obj);
}

static void
destroy_func_resource(void *val)
{
   struct vkr_resource_attachment *att = val;
   struct vkr_device_memory *mem, *tmp;

   LIST_FOR_EACH_ENTRY_SAFE (mem, tmp, &att->memories, exported_head)
      list_delinit(&mem->exported_head);

   free(att);
}

struct virgl_context *
vkr_context_create(size_t debug_len, const char *debug_name)
{
   struct vkr_context *ctx;

   /* TODO inject a proxy context when multi-process */

   ctx = calloc(1, sizeof(*ctx));
   if (!ctx)
      return NULL;

   ctx->debug_name = malloc(debug_len + 1);
   if (!ctx->debug_name) {
      free(ctx);
      return NULL;
   }

   memcpy(ctx->debug_name, debug_name, debug_len);
   ctx->debug_name[debug_len] = '\0';

#ifdef ENABLE_VENUS_VALIDATE
   ctx->validate_level = VKR_CONTEXT_VALIDATE_ON;
   ctx->validate_fatal = false; /* TODO set this to true */
#else
   ctx->validate_level = VKR_CONTEXT_VALIDATE_NONE;
   ctx->validate_fatal = false;
#endif
   if (VKR_DEBUG(VALIDATE))
      ctx->validate_level = VKR_CONTEXT_VALIDATE_FULL;

   if (mtx_init(&ctx->mutex, mtx_plain) != thrd_success) {
      free(ctx->debug_name);
      free(ctx);
      return NULL;
   }

   list_inithead(&ctx->rings);

   ctx->object_table = util_hash_table_create_u64(destroy_func_object);
   ctx->resource_table =
      util_hash_table_create(hash_func_u32, compare_func, destroy_func_resource);
   if (!ctx->object_table || !ctx->resource_table)
      goto fail;

   list_inithead(&ctx->newly_exported_memories);

   vkr_cs_decoder_init(&ctx->decoder, ctx->object_table);
   vkr_cs_encoder_init(&ctx->encoder, &ctx->decoder.fatal_error);

   vkr_context_init_base(ctx);
   vkr_context_init_dispatch(ctx);

   if ((vkr_renderer_flags & VKR_RENDERER_THREAD_SYNC) &&
       !(vkr_renderer_flags & VKR_RENDERER_ASYNC_FENCE_CB)) {
      ctx->fence_eventfd = create_eventfd(0);
      if (ctx->fence_eventfd < 0)
         goto fail;
   } else {
      ctx->fence_eventfd = -1;
   }

   list_inithead(&ctx->busy_queues);
   list_inithead(&ctx->signaled_syncs);

   return &ctx->base;

fail:
   if (ctx->object_table)
      util_hash_table_destroy_u64(ctx->object_table);
   if (ctx->resource_table)
      util_hash_table_destroy(ctx->resource_table);
   mtx_destroy(&ctx->mutex);
   free(ctx->debug_name);
   free(ctx);
   return NULL;
}
