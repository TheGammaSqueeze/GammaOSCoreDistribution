// Copyright (C) 2018 The Android Open Source Project
// Copyright (C) 2018 Google Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
#pragma once

#include <vulkan/vulkan.h>

#ifdef __cplusplus
#include <algorithm>
extern "C" {
#endif

#define VK_ANDROID_native_buffer 1
#define VK_ANDROID_NATIVE_BUFFER_EXTENSION_NUMBER 11

/* NOTE ON VK_ANDROID_NATIVE_BUFFER_SPEC_VERSION 6
 *
 * This version of the extension transitions from gralloc0 to gralloc1 usage
 * flags (int -> 2x uint64_t). The WSI implementation will temporarily continue
 * to fill out deprecated fields in VkNativeBufferANDROID, and will call the
 * deprecated vkGetSwapchainGrallocUsageANDROID if the new
 * vkGetSwapchainGrallocUsage2ANDROID is not supported. This transitionary
 * backwards-compatibility support is temporary, and will likely be removed in
 * (along with all gralloc0 support) in a future release.
 */
#define VK_ANDROID_NATIVE_BUFFER_SPEC_VERSION     7
#define VK_ANDROID_NATIVE_BUFFER_EXTENSION_NAME   "VK_ANDROID_native_buffer"

#define VK_ANDROID_NATIVE_BUFFER_ENUM(type,id)    ((type)(1000000000 + (1000 * (VK_ANDROID_NATIVE_BUFFER_EXTENSION_NUMBER - 1)) + (id)))
#define VK_STRUCTURE_TYPE_NATIVE_BUFFER_ANDROID   VK_ANDROID_NATIVE_BUFFER_ENUM(VkStructureType, 0)
#define VK_STRUCTURE_TYPE_SWAPCHAIN_IMAGE_CREATE_INFO_ANDROID VK_ANDROID_NATIVE_BUFFER_ENUM(VkStructureType, 1)
#define VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_PRESENTATION_PROPERTIES_ANDROID VK_ANDROID_NATIVE_BUFFER_ENUM(VkStructureType, 2)

typedef enum VkSwapchainImageUsageFlagBitsANDROID {
    VK_SWAPCHAIN_IMAGE_USAGE_SHARED_BIT_ANDROID = 0x00000001,
    VK_SWAPCHAIN_IMAGE_USAGE_FLAG_BITS_MAX_ENUM = 0x7FFFFFFF
} VkSwapchainImageUsageFlagBitsANDROID;
typedef VkFlags VkSwapchainImageUsageFlagsANDROID;

typedef struct {
    VkStructureType             sType; // must be VK_STRUCTURE_TYPE_NATIVE_BUFFER_ANDROID
    const void*                 pNext;

    // Buffer handle and stride returned from gralloc alloc()
    const uint32_t*             handle;
    int                         stride;

    // Gralloc format and usage requested when the buffer was allocated.
    int                         format;
    int                         usage; // DEPRECATED in SPEC_VERSION 6
    // -- Added in SPEC_VERSION 6 --
    uint64_t                consumer;
    uint64_t                producer;
} VkNativeBufferANDROID;

typedef struct {
    VkStructureType                        sType; // must be VK_STRUCTURE_TYPE_SWAPCHAIN_IMAGE_CREATE_INFO_ANDROID
    const void*                            pNext;

    VkSwapchainImageUsageFlagsANDROID      usage;
} VkSwapchainImageCreateInfoANDROID;

typedef struct {
    VkStructureType                        sType; // must be VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_PRESENTATION_PROPERTIES_ANDROID
    const void*                            pNext;

    VkBool32                               sharedImage;
} VkPhysicalDevicePresentationPropertiesANDROID;

// -- DEPRECATED in SPEC_VERSION 6 --
typedef VkResult (VKAPI_PTR *PFN_vkGetSwapchainGrallocUsageANDROID)(VkDevice device, VkFormat format, VkImageUsageFlags imageUsage, int* grallocUsage);
// -- ADDED in SPEC_VERSION 6 --
typedef VkResult (VKAPI_PTR *PFN_vkGetSwapchainGrallocUsage2ANDROID)(VkDevice device, VkFormat format, VkImageUsageFlags imageUsage, VkSwapchainImageUsageFlagsANDROID swapchainImageUsage, uint64_t* grallocConsumerUsage, uint64_t* grallocProducerUsage);
typedef VkResult (VKAPI_PTR *PFN_vkAcquireImageANDROID)(VkDevice device, VkImage image, int nativeFenceFd, VkSemaphore semaphore, VkFence fence);
typedef VkResult (VKAPI_PTR *PFN_vkQueueSignalReleaseImageANDROID)(VkQueue queue, uint32_t waitSemaphoreCount, const VkSemaphore* pWaitSemaphores, VkImage image, int* pNativeFenceFd);

typedef VkResult (VKAPI_PTR *PFN_vkMapMemoryIntoAddressSpaceGOOGLE)(VkDevice device, VkDeviceMemory memory, uint64_t* pAddress);

#define VK_GOOGLE_gfxstream 1
#define VK_GOOGLE_GFXSTREAM_EXTENSION_NUMBER 386

#define VK_GOOGLE_GFXSTREAM_ENUM(type,id)    ((type)(1000000000 + (1000 * (VK_GOOGLE_GFXSTREAM_EXTENSION_NUMBER - 1)) + (id)))
#define VK_STRUCTURE_TYPE_IMPORT_COLOR_BUFFER_GOOGLE   VK_GOOGLE_GFXSTREAM_ENUM(VkStructureType, 0)
#define VK_STRUCTURE_TYPE_IMPORT_PHYSICAL_ADDRESS_GOOGLE   VK_GOOGLE_GFXSTREAM_ENUM(VkStructureType, 1)
#define VK_STRUCTURE_TYPE_IMPORT_BUFFER_GOOGLE   VK_GOOGLE_GFXSTREAM_ENUM(VkStructureType, 2)

typedef struct {
    VkStructureType sType; // must be VK_STRUCTURE_TYPE_IMPORT_COLOR_BUFFER_GOOGLE
    const void* pNext;
    uint32_t colorBuffer;
} VkImportColorBufferGOOGLE;

typedef struct {
    VkStructureType sType; // must be VK_STRUCTURE_TYPE_IMPORT_PHYSICAL_ADDRESS_GOOGLE
    const void* pNext;
    uint64_t physicalAddress;
    VkDeviceSize size;
    VkFormat format;
    VkImageTiling tiling;
    uint32_t tilingParameter;
} VkImportPhysicalAddressGOOGLE;

typedef struct {
    VkStructureType sType; // must be VK_STRUCTURE_TYPE_IMPORT_BUFFER_GOOGLE
    const void* pNext;
    uint32_t buffer;
} VkImportBufferGOOGLE;

typedef VkResult (VKAPI_PTR *PFN_vkRegisterImageColorBufferGOOGLE)(VkDevice device, VkImage image, uint32_t colorBuffer);
typedef VkResult (VKAPI_PTR *PFN_vkRegisterBufferColorBufferGOOGLE)(VkDevice device, VkBuffer image, uint32_t colorBuffer);

typedef VkResult (VKAPI_PTR *PFN_vkGetMemoryHostAddressInfoGOOGLE)(VkDevice device, VkDeviceMemory memory, uint64_t* pAddress, uint64_t* pSize);

typedef VkResult (VKAPI_PTR *PFN_vkFreeMemorySyncGOOGLE)(VkDevice device, VkDeviceMemory memory, const VkAllocationCallbacks* pAllocationCallbacks);

#define VK_ANDROID_external_memory_android_hardware_buffer 1
struct AHardwareBuffer;
struct VkAndroidHardwareBufferPropertiesANDROID;
struct VkMemoryGetAndroidHardwareBufferInfoANDROID;

#ifndef VK_USE_PLATFORM_ANDROID_KHR

typedef struct VkAndroidHardwareBufferUsageANDROID {
    VkStructureType    sType;
    void*              pNext;
    uint64_t           androidHardwareBufferUsage;
} VkAndroidHardwareBufferUsageANDROID;

typedef struct VkAndroidHardwareBufferPropertiesANDROID {
    VkStructureType    sType;
    void*              pNext;
    VkDeviceSize       allocationSize;
    uint32_t           memoryTypeBits;
} VkAndroidHardwareBufferPropertiesANDROID;

typedef struct VkAndroidHardwareBufferFormatPropertiesANDROID {
    VkStructureType                  sType;
    void*                            pNext;
    VkFormat                         format;
    uint64_t                         externalFormat;
    VkFormatFeatureFlags             formatFeatures;
    VkComponentMapping               samplerYcbcrConversionComponents;
    VkSamplerYcbcrModelConversion    suggestedYcbcrModel;
    VkSamplerYcbcrRange              suggestedYcbcrRange;
    VkChromaLocation                 suggestedXChromaOffset;
    VkChromaLocation                 suggestedYChromaOffset;
} VkAndroidHardwareBufferFormatPropertiesANDROID;

typedef struct VkImportAndroidHardwareBufferInfoANDROID {
    VkStructureType            sType;
    const void*                pNext;
    struct AHardwareBuffer*    buffer;
} VkImportAndroidHardwareBufferInfoANDROID;

typedef struct VkMemoryGetAndroidHardwareBufferInfoANDROID {
    VkStructureType    sType;
    const void*        pNext;
    VkDeviceMemory     memory;
} VkMemoryGetAndroidHardwareBufferInfoANDROID;

typedef struct VkExternalFormatANDROID {
    VkStructureType    sType;
    void*              pNext;
    uint64_t           externalFormat;
} VkExternalFormatANDROID;


typedef VkResult (VKAPI_PTR *PFN_vkGetAndroidHardwareBufferPropertiesANDROID)(VkDevice device, const struct AHardwareBuffer* buffer, VkAndroidHardwareBufferPropertiesANDROID* pProperties);
typedef VkResult (VKAPI_PTR *PFN_vkGetMemoryAndroidHardwareBufferANDROID)(VkDevice device, const VkMemoryGetAndroidHardwareBufferInfoANDROID* pInfo, struct AHardwareBuffer** pBuffer);

#ifndef VK_NO_PROTOTYPES
VKAPI_ATTR VkResult VKAPI_CALL vkGetAndroidHardwareBufferPropertiesANDROID(
    VkDevice                                    device,
    const struct AHardwareBuffer*               buffer,
    VkAndroidHardwareBufferPropertiesANDROID*   pProperties);

VKAPI_ATTR VkResult VKAPI_CALL vkGetMemoryAndroidHardwareBufferANDROID(
    VkDevice                                    device,
    const VkMemoryGetAndroidHardwareBufferInfoANDROID* pInfo,
    struct AHardwareBuffer**                    pBuffer);
#endif

#endif // __Fuchsia__

#define VK_GOOGLE_sized_descriptor_update_template 1

typedef void (VKAPI_PTR *PFN_vkUpdateDescriptorSetWithTemplateSizedGOOGLE)(
    VkDevice device, VkDescriptorSet descriptorSet, VkDescriptorUpdateTemplate descriptorUpdateTemplate,
    uint32_t imageInfoCount,
    uint32_t bufferInfoCount,
    uint32_t bufferViewCount,
    const uint32_t* pImageInfoEntryIndices,
    const uint32_t* pBufferInfoEntryIndices,
    const uint32_t* pBufferViewEntryIndices,
    const VkDescriptorImageInfo* pImageInfos,
    const VkDescriptorBufferInfo* pBufferInfos,
    const VkBufferView* pBufferViews);

#define VK_GOOGLE_async_command_buffers 1

typedef void (VKAPI_PTR *PFN_vkBeginCommandBufferAsyncGOOGLE)(
    VkCommandBuffer commandBuffer,
    const VkCommandBufferBeginInfo* pBeginInfo);
typedef void (VKAPI_PTR *PFN_vkEndCommandBufferAsyncGOOGLE)(
    VkCommandBuffer commandBuffer);
typedef void (VKAPI_PTR *PFN_vkResetCommandBufferAsyncGOOGLE)(
    VkCommandBuffer commandBuffer,
    VkCommandBufferResetFlags flags);
typedef void (VKAPI_PTR *PFN_vkCommandBufferHostSyncGOOGLE)(
    VkCommandBuffer commandBuffer,
    uint32_t needHostSync,
    uint32_t sequenceNumber);

typedef void (VKAPI_PTR *PFN_vkCreateImageWithRequirementsGOOGLE)(
    VkDevice device, const VkImageCreateInfo* pCreateInfo, const VkAllocationCallbacks* pAllocator, VkImage* pImage, VkMemoryRequirements* pMemoryRequirements);

#ifdef VK_USE_PLATFORM_FUCHSIA

#ifndef VK_FUCHSIA_buffer_collection_x

#define VK_FUCHSIA_buffer_collection_x 1
VK_DEFINE_NON_DISPATCHABLE_HANDLE(VkBufferCollectionFUCHSIAX)
#define VK_FUCHSIA_BUFFER_COLLECTION_X_SPEC_VERSION 1
#define VK_FUCHSIA_BUFFER_COLLECTION_X_EXTENSION_NAME \
    "VK_FUCHSIA_buffer_collection_x"

typedef enum VkImageFormatConstraintsFlagBitsFUCHSIAX {
    VK_IMAGE_FORMAT_CONSTRAINTS_FLAG_BITS_MAX_ENUM_FUCHSIAX = 0x7FFFFFFF
} VkImageFormatConstraintsFlagBitsFUCHSIAX;
typedef VkFlags VkImageFormatConstraintsFlagsFUCHSIAX;

typedef enum VkImageConstraintsInfoFlagBitsFUCHSIAX {
    VK_IMAGE_CONSTRAINTS_INFO_CPU_READ_RARELY_FUCHSIAX = 0x00000001,
    VK_IMAGE_CONSTRAINTS_INFO_CPU_READ_OFTEN_FUCHSIAX = 0x00000002,
    VK_IMAGE_CONSTRAINTS_INFO_CPU_WRITE_RARELY_FUCHSIAX = 0x00000004,
    VK_IMAGE_CONSTRAINTS_INFO_CPU_WRITE_OFTEN_FUCHSIAX = 0x00000008,
    VK_IMAGE_CONSTRAINTS_INFO_PROTECTED_OPTIONAL_FUCHSIAX = 0x00000010,
    VK_IMAGE_CONSTRAINTS_INFO_FLAG_BITS_MAX_ENUM_FUCHSIAX = 0x7FFFFFFF
} VkImageConstraintsInfoFlagBitsFUCHSIAX;
typedef VkFlags VkImageConstraintsInfoFlagsFUCHSIAX;
typedef struct VkBufferCollectionCreateInfoFUCHSIAX {
    VkStructureType sType;
    const void* pNext;
    uint32_t collectionToken;
} VkBufferCollectionCreateInfoFUCHSIAX;

typedef struct VkImportMemoryBufferCollectionFUCHSIAX {
    VkStructureType sType;
    const void* pNext;
    VkBufferCollectionFUCHSIAX collection;
    uint32_t index;
} VkImportMemoryBufferCollectionFUCHSIAX;

typedef struct VkBufferCollectionImageCreateInfoFUCHSIAX {
    VkStructureType sType;
    const void* pNext;
    VkBufferCollectionFUCHSIAX collection;
    uint32_t index;
} VkBufferCollectionImageCreateInfoFUCHSIAX;

typedef struct VkBufferConstraintsInfoFUCHSIAX {
    VkStructureType sType;
    const void* pNext;
    const VkBufferCreateInfo* pBufferCreateInfo;
    VkFormatFeatureFlags requiredFormatFeatures;
    uint32_t minCount;
} VkBufferConstraintsInfoFUCHSIAX;

typedef struct VkBufferCollectionBufferCreateInfoFUCHSIAX {
    VkStructureType sType;
    const void* pNext;
    VkBufferCollectionFUCHSIAX collection;
    uint32_t index;
} VkBufferCollectionBufferCreateInfoFUCHSIAX;

typedef struct VkBufferCollectionPropertiesFUCHSIAX {
    VkStructureType sType;
    void* pNext;
    uint32_t memoryTypeBits;
    uint32_t count;
} VkBufferCollectionPropertiesFUCHSIAX;

typedef struct VkSysmemColorSpaceFUCHSIAX {
    VkStructureType sType;
    const void* pNext;
    uint32_t colorSpace;
} VkSysmemColorSpaceFUCHSIAX;

typedef struct VkBufferCollectionProperties2FUCHSIAX {
    VkStructureType sType;
    void* pNext;
    uint32_t memoryTypeBits;
    uint32_t bufferCount;
    uint32_t createInfoIndex;
    uint64_t sysmemFormat;
    VkFormatFeatureFlags formatFeatures;
    VkSysmemColorSpaceFUCHSIAX colorSpace;
    VkComponentMapping samplerYcbcrConversionComponents;
    VkSamplerYcbcrModelConversion suggestedYcbcrModel;
    VkSamplerYcbcrRange suggestedYcbcrRange;
    VkChromaLocation suggestedXChromaOffset;
    VkChromaLocation suggestedYChromaOffset;
} VkBufferCollectionProperties2FUCHSIAX;

typedef struct VkImageFormatConstraintsInfoFUCHSIAX {
    VkStructureType sType;
    const void* pNext;
    VkFormatFeatureFlags requiredFormatFeatures;
    VkImageFormatConstraintsFlagsFUCHSIAX flags;
    uint64_t sysmemFormat;
    uint32_t colorSpaceCount;
    const VkSysmemColorSpaceFUCHSIAX* pColorSpaces;
} VkImageFormatConstraintsInfoFUCHSIAX;

typedef struct VkImageConstraintsInfoFUCHSIAX {
    VkStructureType sType;
    const void* pNext;
    uint32_t createInfoCount;
    const VkImageCreateInfo* pCreateInfos;
    const VkImageFormatConstraintsInfoFUCHSIAX* pFormatConstraints;
    uint32_t minBufferCount;
    uint32_t maxBufferCount;
    uint32_t minBufferCountForCamping;
    uint32_t minBufferCountForDedicatedSlack;
    uint32_t minBufferCountForSharedSlack;
    VkImageConstraintsInfoFlagsFUCHSIAX flags;
} VkImageConstraintsInfoFUCHSIAX;

#define VK_STRUCTURE_TYPE_BUFFER_COLLECTION_CREATE_INFO_FUCHSIAX \
    ((VkStructureType)1000367000)
#define VK_STRUCTURE_TYPE_IMPORT_MEMORY_BUFFER_COLLECTION_FUCHSIAX \
    ((VkStructureType)1000367004)
#define VK_STRUCTURE_TYPE_BUFFER_COLLECTION_IMAGE_CREATE_INFO_FUCHSIAX \
    ((VkStructureType)1000367005)
#define VK_STRUCTURE_TYPE_BUFFER_COLLECTION_PROPERTIES_FUCHSIAX \
    ((VkStructureType)1000367006)
#define VK_STRUCTURE_TYPE_BUFFER_CONSTRAINTS_INFO_FUCHSIAX \
    ((VkStructureType)1000367007)
#define VK_STRUCTURE_TYPE_BUFFER_COLLECTION_BUFFER_CREATE_INFO_FUCHSIAX \
    ((VkStructureType)1000367008)
#define VK_STRUCTURE_TYPE_IMAGE_CONSTRAINTS_INFO_FUCHSIAX \
    ((VkStructureType)1000367009)
#define VK_STRUCTURE_TYPE_IMAGE_FORMAT_CONSTRAINTS_INFO_FUCHSIAX \
    ((VkStructureType)1000367010)
#define VK_STRUCTURE_TYPE_BUFFER_COLLECTION_PROPERTIES2_FUCHSIAX \
    ((VkStructureType)1000367011)

#endif  // VK_FUCHSIA_buffer_collection_x

#endif  // VK_USE_PLATFORM_FUCHSIA

// VulkanStream features
#define VULKAN_STREAM_FEATURE_NULL_OPTIONAL_STRINGS_BIT (1 << 0)
#define VULKAN_STREAM_FEATURE_IGNORED_HANDLES_BIT (1 << 1)
#define VULKAN_STREAM_FEATURE_SHADER_FLOAT16_INT8_BIT (1 << 2)
#define VULKAN_STREAM_FEATURE_QUEUE_SUBMIT_WITH_COMMANDS_BIT (1 << 3)

#define VK_YCBCR_CONVERSION_DO_NOTHING ((VkSamplerYcbcrConversion)0x1111111111111111)

// Stuff we advertised but didn't define the structs for it yet because
// we also needed to update our vulkan headers and xml

#ifndef VK_VERSION_1_2

typedef struct VkPhysicalDeviceShaderFloat16Int8Features {
    VkStructureType    sType;
    void*              pNext;
    VkBool32           shaderFloat16;
    VkBool32           shaderInt8;
} VkPhysicalDeviceShaderFloat16Int8Features;


#define VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_SHADER_FLOAT16_INT8_FEATURES \
    ((VkStructureType)1000082000)

#endif

#define VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_SHADER_FLOAT16_INT8_FEATURES_KHR \
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_SHADER_FLOAT16_INT8_FEATURES

#define VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_FLOAT16_INT8_FEATURES_KHR \
    VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_SHADER_FLOAT16_INT8_FEATURES

#ifndef VK_KHR_shader_float16_int8

#define VK_KHR_shader_float16_int8 1
#define VK_KHR_SHADER_FLOAT16_INT8_SPEC_VERSION 1
#define VK_KHR_SHADER_FLOAT16_INT8_EXTENSION_NAME "VK_KHR_shader_float16_int8"
typedef VkPhysicalDeviceShaderFloat16Int8Features VkPhysicalDeviceShaderFloat16Int8FeaturesKHR;
typedef VkPhysicalDeviceShaderFloat16Int8Features VkPhysicalDeviceFloat16Int8FeaturesKHR;

#endif

#define VK_GOOGLE_gfxstream 1

typedef void (VKAPI_PTR *PFN_vkQueueHostSyncGOOGLE)(
    VkQueue queue, uint32_t needHostSync, uint32_t sequenceNumber);
typedef void (VKAPI_PTR *PFN_vkQueueSubmitAsyncGOOGLE)(
    VkQueue queue, uint32_t submitCount, const VkSubmitInfo* pSubmits, VkFence fence);
typedef void (VKAPI_PTR *PFN_vkQueueWaitIdleAsyncGOOGLE)(VkQueue queue);
typedef void (VKAPI_PTR *PFN_vkQueueBindSparseAsyncGOOGLE)(
    VkQueue queue, uint32_t bindInfoCount, const VkBindSparseInfo* pBindInfo, VkFence fence);

typedef VkResult (VKAPI_PTR *PFN_vkGetLinearImageLayoutGOOGLE)(VkDevice device, VkFormat format, VkDeviceSize* pOffset, VkDeviceSize* pRowPitchAlignment);
typedef VkResult (VKAPI_PTR *PFN_vkGetLinearImageLayout2GOOGLE)(VkDevice device, const VkImageCreateInfo* pCreateInfo, VkDeviceSize* pOffset, VkDeviceSize* pRowPitchAlignment);

typedef void (VKAPI_PTR *PFN_vkQueueFlushCommandsGOOGLE)(VkQueue queue, VkDeviceSize dataSize, const void* pData);
typedef void (VKAPI_PTR *PFN_vkQueueCommitDescriptorSetUpdatesGOOGLE)(VkQueue queue, uint32_t descriptorPoolCount, const VkDescriptorPool* pDescriptorPools, uint32_t descriptorSetCount, const VkDescriptorSetLayout* pDescriptorSetLayouts, const uint64_t* pDescriptorSetPoolIds, const uint32_t* pDescriptorSetWhichPool, const uint32_t* pDescriptorSetPendingAllocation, const uint32_t* pDescriptorWriteStartingIndices, uint32_t pendingDescriptorWriteCount, const VkWriteDescriptorSet* pPendingDescriptorWrites);
typedef void (VKAPI_PTR *PFN_vkCollectDescriptorPoolIdsGOOGLE)(VkDevice device, VkDescriptorPool descriptorPool, uint32_t* pPoolIdCount, uint64_t* pPoolIds);
typedef void (VKAPI_PTR *PFN_vkQueueSignalReleaseImageANDROIDAsyncGOOGLE)(VkQueue queue, uint32_t waitSemaphoreCount, const VkSemaphore* pWaitSemaphores, VkImage image);

// TODO(liyl): Remove once Android vulkan-headers is updated
// to 1.2.184 or higher.

#if VK_HEADER_VERSION < 184

typedef struct VkPipelineCacheHeaderVersionOne {
    uint32_t                        headerSize;
    VkPipelineCacheHeaderVersion    headerVersion;
    uint32_t                        vendorID;
    uint32_t                        deviceID;
    uint8_t                         pipelineCacheUUID[VK_UUID_SIZE];
} VkPipelineCacheHeaderVersionOne;

#endif  // VK_HEADER_VERSION < 184

// TODO(liyl): Remove once Android vulkan-headers is updated
// to 1.2.195 or higher.

#ifdef VK_EXT_image_drm_format_modifier

#if VK_EXT_IMAGE_DRM_FORMAT_MODIFIER_SPEC_VERSION < 2

#ifndef VK_KHR_format_feature_flags2
typedef uint64_t VkFormatFeatureFlags2KHR;
#endif  // VK_KHR_format_feature_flags2

#define VK_STRUCTURE_TYPE_DRM_FORMAT_MODIFIER_PROPERTIES_LIST_2_EXT ((VkStructureType)1000158006)
typedef struct VkDrmFormatModifierProperties2EXT {
    uint64_t                    drmFormatModifier;
    uint32_t                    drmFormatModifierPlaneCount;
    VkFormatFeatureFlags2KHR    drmFormatModifierTilingFeatures;
} VkDrmFormatModifierProperties2EXT;

typedef struct VkDrmFormatModifierPropertiesList2EXT {
    VkStructureType                       sType;
    void*                                 pNext;
    uint32_t                              drmFormatModifierCount;
    VkDrmFormatModifierProperties2EXT*    pDrmFormatModifierProperties;
} VkDrmFormatModifierPropertiesList2EXT;

#endif  // VK_EXT_IMAGE_DRM_FORMAT_MODIFIER_SPEC_VERSION < 2

#endif  // VK_EXT_image_drm_format_modifier

// TODO(liyl): Remove once Android vulkan-headers is updated
// to 1.2.195 or higher.

#ifdef VK_ANDROID_external_memory_android_hardware_buffer

#if VK_ANDROID_EXTERNAL_MEMORY_ANDROID_HARDWARE_BUFFER_SPEC_VERSION < 4

#define VK_STRUCTURE_TYPE_ANDROID_HARDWARE_BUFFER_FORMAT_PROPERTIES_2_ANDROID ((VkStructureType)1000129006)
typedef struct VkAndroidHardwareBufferFormatProperties2ANDROID {
    VkStructureType                  sType;
    void*                            pNext;
    VkFormat                         format;
    uint64_t                         externalFormat;
    VkFormatFeatureFlags2KHR         formatFeatures;
    VkComponentMapping               samplerYcbcrConversionComponents;
    VkSamplerYcbcrModelConversion    suggestedYcbcrModel;
    VkSamplerYcbcrRange              suggestedYcbcrRange;
    VkChromaLocation                 suggestedXChromaOffset;
    VkChromaLocation                 suggestedYChromaOffset;
} VkAndroidHardwareBufferFormatProperties2ANDROID;

#endif  // VK_ANDROID_EXTERNAL_MEMORY_ANDROID_HARDWARE_BUFFER_SPEC_VERSION < 4

#endif  // VK_ANDROID_external_memory_android_hardware_buffer

#ifdef __cplusplus
} // extern "C"
#endif

#ifdef __cplusplus

template<class T, typename F>
bool arrayany(const T* arr, uint32_t begin, uint32_t end, const F& func) {
    const T* e = arr + end;
    return std::find_if(arr + begin, e, func) != e;
}

#define DEFINE_ALIAS_FUNCTION(ORIGINAL_FN, ALIAS_FN) \
template <typename... Args> \
inline auto ALIAS_FN(Args&&... args) -> decltype(ORIGINAL_FN(std::forward<Args>(args)...)) { \
  return ORIGINAL_FN(std::forward<Args>(args)...); \
}

#endif
