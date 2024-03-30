/*
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: MIT
 */

#include "vkr_device_memory.h"

#include "venus-protocol/vn_protocol_renderer_transport.h"

#include "vkr_device_memory_gen.h"
#include "vkr_physical_device.h"

static bool
vkr_get_fd_handle_type_from_virgl_fd_type(
   struct vkr_physical_device *dev,
   enum virgl_resource_fd_type fd_type,
   VkExternalMemoryHandleTypeFlagBits *out_handle_type)
{
   assert(dev);
   assert(out_handle_type);

   switch (fd_type) {
   case VIRGL_RESOURCE_FD_DMABUF:
      if (!dev->EXT_external_memory_dma_buf)
         return false;
      *out_handle_type = VK_EXTERNAL_MEMORY_HANDLE_TYPE_DMA_BUF_BIT_EXT;
      break;
   case VIRGL_RESOURCE_FD_OPAQUE:
      if (!dev->KHR_external_memory_fd)
         return false;
      *out_handle_type = VK_EXTERNAL_MEMORY_HANDLE_TYPE_OPAQUE_FD_BIT;
      break;
   default:
      return false;
   }

   return true;
}

static void
vkr_dispatch_vkAllocateMemory(struct vn_dispatch_context *dispatch,
                              struct vn_command_vkAllocateMemory *args)
{
   struct vkr_context *ctx = dispatch->data;

   struct vkr_device *dev = vkr_device_from_handle(args->device);

#ifdef FORCE_ENABLE_DMABUF
   VkExportMemoryAllocateInfo local_export_info;
   if (dev->physical_device->EXT_external_memory_dma_buf) {
      VkExportMemoryAllocateInfo *export_info = vkr_find_pnext(
         args->pAllocateInfo->pNext, VK_STRUCTURE_TYPE_EXPORT_MEMORY_ALLOCATE_INFO);
      if (export_info) {
         export_info->handleTypes |= VK_EXTERNAL_MEMORY_HANDLE_TYPE_DMA_BUF_BIT_EXT;
      } else {
         local_export_info = (const VkExportMemoryAllocateInfo){
            .sType = VK_STRUCTURE_TYPE_EXPORT_MEMORY_ALLOCATE_INFO,
            .pNext = args->pAllocateInfo->pNext,
            .handleTypes = VK_EXTERNAL_MEMORY_HANDLE_TYPE_DMA_BUF_BIT_EXT,
         };
         ((VkMemoryAllocateInfo *)args->pAllocateInfo)->pNext = &local_export_info;
      }
   }
#endif

   /* translate VkImportMemoryResourceInfoMESA into VkImportMemoryFdInfoKHR */
   VkImportMemoryResourceInfoMESA *import_resource_info = NULL;
   VkImportMemoryFdInfoKHR import_fd_info = {
      .sType = VK_STRUCTURE_TYPE_IMPORT_MEMORY_FD_INFO_KHR,
      .fd = -1,
   };
   VkBaseInStructure *pprev = (VkBaseInStructure *)args->pAllocateInfo;
   while (pprev->pNext) {
      if (pprev->pNext->sType == VK_STRUCTURE_TYPE_IMPORT_MEMORY_RESOURCE_INFO_MESA) {
         import_resource_info = (VkImportMemoryResourceInfoMESA *)pprev->pNext;
         import_fd_info.pNext = pprev->pNext->pNext;
         pprev->pNext = (const struct VkBaseInStructure *)&import_fd_info;
         break;
      }
      pprev = (VkBaseInStructure *)pprev->pNext;
   }
   if (import_resource_info) {
      uint32_t res_id = import_resource_info->resourceId;
      struct vkr_resource_attachment *att =
         util_hash_table_get(ctx->resource_table, uintptr_to_pointer(res_id));
      if (!att) {
         vkr_cs_decoder_set_fatal(&ctx->decoder);
         return;
      }

      enum virgl_resource_fd_type fd_type =
         virgl_resource_export_fd(att->resource, &import_fd_info.fd);
      if (!vkr_get_fd_handle_type_from_virgl_fd_type(dev->physical_device, fd_type,
                                                     &import_fd_info.handleType)) {
         close(import_fd_info.fd);
         args->ret = VK_ERROR_INVALID_EXTERNAL_HANDLE;
         return;
      }
   }

   const VkPhysicalDeviceMemoryProperties *mem_props =
      &dev->physical_device->memory_properties;
   const uint32_t mt_index = args->pAllocateInfo->memoryTypeIndex;
   const uint32_t property_flags = mem_props->memoryTypes[mt_index].propertyFlags;

   /* get valid fd types */
   uint32_t valid_fd_types = 0;
   const VkBaseInStructure *pnext = args->pAllocateInfo->pNext;
   while (pnext) {
      if (pnext->sType == VK_STRUCTURE_TYPE_EXPORT_MEMORY_ALLOCATE_INFO) {
         const VkExportMemoryAllocateInfo *export = (const void *)pnext;

         if (export->handleTypes & VK_EXTERNAL_MEMORY_HANDLE_TYPE_OPAQUE_FD_BIT)
            valid_fd_types |= 1 << VIRGL_RESOURCE_FD_OPAQUE;
         if (export->handleTypes & VK_EXTERNAL_MEMORY_HANDLE_TYPE_DMA_BUF_BIT_EXT)
            valid_fd_types |= 1 << VIRGL_RESOURCE_FD_DMABUF;

         break;
      }
      pnext = pnext->pNext;
   }

   struct vkr_device_memory *mem = vkr_device_memory_create_and_add(ctx, args);
   if (!mem) {
      if (import_resource_info)
         close(import_fd_info.fd);
      return;
   }

   mem->device = args->device;
   mem->property_flags = property_flags;
   mem->valid_fd_types = valid_fd_types;
   list_inithead(&mem->exported_head);
}

static void
vkr_dispatch_vkFreeMemory(struct vn_dispatch_context *dispatch,
                          struct vn_command_vkFreeMemory *args)
{
   struct vkr_device_memory *mem = vkr_device_memory_from_handle(args->memory);
   if (!mem)
      return;

   list_del(&mem->exported_head);

   vkr_device_memory_destroy_and_remove(dispatch->data, args);
}

static void
vkr_dispatch_vkGetDeviceMemoryCommitment(
   UNUSED struct vn_dispatch_context *dispatch,
   struct vn_command_vkGetDeviceMemoryCommitment *args)
{
   vn_replace_vkGetDeviceMemoryCommitment_args_handle(args);
   vkGetDeviceMemoryCommitment(args->device, args->memory, args->pCommittedMemoryInBytes);
}

static void
vkr_dispatch_vkGetDeviceMemoryOpaqueCaptureAddress(
   UNUSED struct vn_dispatch_context *dispatch,
   struct vn_command_vkGetDeviceMemoryOpaqueCaptureAddress *args)
{
   struct vkr_device *dev = vkr_device_from_handle(args->device);

   vn_replace_vkGetDeviceMemoryOpaqueCaptureAddress_args_handle(args);
   args->ret = dev->GetDeviceMemoryOpaqueCaptureAddress(args->device, args->pInfo);
}

static void
vkr_dispatch_vkGetMemoryResourcePropertiesMESA(
   struct vn_dispatch_context *dispatch,
   struct vn_command_vkGetMemoryResourcePropertiesMESA *args)
{
   struct vkr_context *ctx = dispatch->data;
   struct vkr_device *dev = vkr_device_from_handle(args->device);

   struct vkr_resource_attachment *att =
      util_hash_table_get(ctx->resource_table, uintptr_to_pointer(args->resourceId));
   if (!att) {
      vkr_cs_decoder_set_fatal(&ctx->decoder);
      return;
   }

   int fd = -1;
   enum virgl_resource_fd_type fd_type = virgl_resource_export_fd(att->resource, &fd);
   VkExternalMemoryHandleTypeFlagBits handle_type;
   if (!vkr_get_fd_handle_type_from_virgl_fd_type(dev->physical_device, fd_type,
                                                  &handle_type) ||
       handle_type != VK_EXTERNAL_MEMORY_HANDLE_TYPE_DMA_BUF_BIT_EXT) {
      close(fd);
      args->ret = VK_ERROR_INVALID_EXTERNAL_HANDLE;
      return;
   }

   VkMemoryFdPropertiesKHR mem_fd_props = {
      .sType = VK_STRUCTURE_TYPE_MEMORY_FD_PROPERTIES_KHR,
      .pNext = NULL,
      .memoryTypeBits = 0,
   };
   vn_replace_vkGetMemoryResourcePropertiesMESA_args_handle(args);
   args->ret =
      dev->get_memory_fd_properties(args->device, handle_type, fd, &mem_fd_props);
   if (args->ret != VK_SUCCESS) {
      close(fd);
      return;
   }

   args->pMemoryResourceProperties->memoryTypeBits = mem_fd_props.memoryTypeBits;

   VkMemoryResourceAllocationSizeProperties100000MESA *alloc_size_props = vkr_find_pnext(
      args->pMemoryResourceProperties->pNext,
      VK_STRUCTURE_TYPE_MEMORY_RESOURCE_ALLOCATION_SIZE_PROPERTIES_100000_MESA);
   if (alloc_size_props)
      alloc_size_props->allocationSize = lseek(fd, 0, SEEK_END);

   close(fd);
}

void
vkr_context_init_device_memory_dispatch(struct vkr_context *ctx)
{
   struct vn_dispatch_context *dispatch = &ctx->dispatch;

   dispatch->dispatch_vkAllocateMemory = vkr_dispatch_vkAllocateMemory;
   dispatch->dispatch_vkFreeMemory = vkr_dispatch_vkFreeMemory;
   dispatch->dispatch_vkMapMemory = NULL;
   dispatch->dispatch_vkUnmapMemory = NULL;
   dispatch->dispatch_vkFlushMappedMemoryRanges = NULL;
   dispatch->dispatch_vkInvalidateMappedMemoryRanges = NULL;
   dispatch->dispatch_vkGetDeviceMemoryCommitment =
      vkr_dispatch_vkGetDeviceMemoryCommitment;
   dispatch->dispatch_vkGetDeviceMemoryOpaqueCaptureAddress =
      vkr_dispatch_vkGetDeviceMemoryOpaqueCaptureAddress;

   dispatch->dispatch_vkGetMemoryResourcePropertiesMESA =
      vkr_dispatch_vkGetMemoryResourcePropertiesMESA;
}
