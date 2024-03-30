/*
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: MIT
 */

#ifndef VKR_DEVICE_H
#define VKR_DEVICE_H

#include "vkr_common.h"

#include "vkr_context.h"

struct vkr_device {
   struct vkr_object base;

   struct vkr_physical_device *physical_device;

   /* Vulkan 1.2 */
   PFN_vkGetSemaphoreCounterValue GetSemaphoreCounterValue;
   PFN_vkWaitSemaphores WaitSemaphores;
   PFN_vkSignalSemaphore SignalSemaphore;
   PFN_vkGetDeviceMemoryOpaqueCaptureAddress GetDeviceMemoryOpaqueCaptureAddress;
   PFN_vkGetBufferOpaqueCaptureAddress GetBufferOpaqueCaptureAddress;
   PFN_vkGetBufferDeviceAddress GetBufferDeviceAddress;
   PFN_vkResetQueryPool ResetQueryPool;
   PFN_vkCreateRenderPass2 CreateRenderPass2;
   PFN_vkCmdBeginRenderPass2 CmdBeginRenderPass2;
   PFN_vkCmdNextSubpass2 CmdNextSubpass2;
   PFN_vkCmdEndRenderPass2 CmdEndRenderPass2;
   PFN_vkCmdDrawIndirectCount CmdDrawIndirectCount;
   PFN_vkCmdDrawIndexedIndirectCount CmdDrawIndexedIndirectCount;

   PFN_vkCmdBindTransformFeedbackBuffersEXT cmd_bind_transform_feedback_buffers;
   PFN_vkCmdBeginTransformFeedbackEXT cmd_begin_transform_feedback;
   PFN_vkCmdEndTransformFeedbackEXT cmd_end_transform_feedback;
   PFN_vkCmdBeginQueryIndexedEXT cmd_begin_query_indexed;
   PFN_vkCmdEndQueryIndexedEXT cmd_end_query_indexed;
   PFN_vkCmdDrawIndirectByteCountEXT cmd_draw_indirect_byte_count;

   PFN_vkGetImageDrmFormatModifierPropertiesEXT get_image_drm_format_modifier_properties;

   PFN_vkGetMemoryFdPropertiesKHR get_memory_fd_properties;

   struct list_head queues;

   mtx_t free_sync_mutex;
   struct list_head free_syncs;

   struct list_head objects;
};
VKR_DEFINE_OBJECT_CAST(device, VK_OBJECT_TYPE_DEVICE, VkDevice)

void
vkr_context_init_device_dispatch(struct vkr_context *ctx);

void
vkr_device_destroy(struct vkr_context *ctx, struct vkr_device *dev);

static inline bool
vkr_device_should_track_object(const struct vkr_object *obj)
{
   assert(vkr_is_recognized_object_type(obj->type));

   switch (obj->type) {
   case VK_OBJECT_TYPE_INSTANCE:        /* non-device objects */
   case VK_OBJECT_TYPE_PHYSICAL_DEVICE: /* non-device objects */
   case VK_OBJECT_TYPE_DEVICE:          /* device itself */
   case VK_OBJECT_TYPE_QUEUE:           /* not tracked as device objects */
   case VK_OBJECT_TYPE_COMMAND_BUFFER:  /* pool objects */
   case VK_OBJECT_TYPE_DESCRIPTOR_SET:  /* pool objects */
      return false;
   default:
      return true;
   }
}

static inline void
vkr_device_add_object(struct vkr_context *ctx,
                      struct vkr_device *dev,
                      struct vkr_object *obj)
{
   vkr_context_add_object(ctx, obj);

   assert(vkr_device_should_track_object(obj));
   list_add(&obj->track_head, &dev->objects);
}

static inline void
vkr_device_remove_object(struct vkr_context *ctx,
                         UNUSED struct vkr_device *dev,
                         struct vkr_object *obj)
{
   assert(vkr_device_should_track_object(obj));
   list_del(&obj->track_head);

   /* this frees obj */
   vkr_context_remove_object(ctx, obj);
}

#endif /* VKR_DEVICE_H */
