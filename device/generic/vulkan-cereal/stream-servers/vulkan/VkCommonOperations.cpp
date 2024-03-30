// Copyright 2018 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either expresso or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
#include "VkCommonOperations.h"

#include <GLES2/gl2.h>
#include <GLES2/gl2ext.h>
#include <GLES3/gl3.h>
#include <stdio.h>
#include <string.h>
#include <vulkan/vk_enum_string_helper.h>

#include <iomanip>
#include <ostream>
#include <sstream>
#include <unordered_set>

#include "FrameBuffer.h"
#include "VulkanDispatch.h"
#include "base/Lock.h"
#include "base/Lookup.h"
#include "base/Optional.h"
#include "base/StaticMap.h"
#include "base/System.h"
#include "base/Tracing.h"
#include "common/goldfish_vk_dispatch.h"
#include "host-common/GfxstreamFatalError.h"
#include "host-common/vm_operations.h"

#ifdef _WIN32
#include <windows.h>
#else
#include <fcntl.h>
#include <unistd.h>
#endif

#ifdef __APPLE__
#include <CoreFoundation/CoreFoundation.h>
#endif

#define VK_COMMON_ERROR(fmt,...) fprintf(stderr, "%s:%d " fmt "\n", __func__, __LINE__, ##__VA_ARGS__);
#define VK_COMMON_LOG(fmt,...) fprintf(stdout, "%s:%d " fmt "\n", __func__, __LINE__, ##__VA_ARGS__);
#define VK_COMMON_VERBOSE(fmt,...) if (android::base::isVerboseLogging()) fprintf(stderr, "%s:%d " fmt "\n", __func__, __LINE__, ##__VA_ARGS__);

using android::base::AutoLock;
using android::base::Optional;
using android::base::StaticLock;
using android::base::StaticMap;

using android::base::kNullopt;
using emugl::ABORT_REASON_OTHER;
using emugl::FatalError;

namespace goldfish_vk {

namespace {

constexpr size_t kPageBits = 12;
constexpr size_t kPageSize = 1u << kPageBits;

}  // namespace

static StaticMap<VkDevice, uint32_t>
sKnownStagingTypeIndices;

static android::base::StaticLock sVkEmulationLock;

VK_EXT_MEMORY_HANDLE dupExternalMemory(VK_EXT_MEMORY_HANDLE h) {
#ifdef _WIN32
    auto myProcessHandle = GetCurrentProcess();
    VK_EXT_MEMORY_HANDLE res;
    DuplicateHandle(
        myProcessHandle, h, // source process and handle
        myProcessHandle, &res, // target process and pointer to handle
        0 /* desired access (ignored) */,
        true /* inherit */,
        DUPLICATE_SAME_ACCESS /* same access option */);
    return res;
#else
    return dup(h);
#endif
}

bool getStagingMemoryTypeIndex(
    VulkanDispatch* vk,
    VkDevice device,
    const VkPhysicalDeviceMemoryProperties* memProps,
    uint32_t* typeIndex) {

    auto res = sKnownStagingTypeIndices.get(device);

    if (res) {
        *typeIndex = *res;
        return true;
    }

    VkBufferCreateInfo testCreateInfo = {
        VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO, 0, 0,
        4096,
        // To be a staging buffer, it must support being
        // both a transfer src and dst.
        VK_BUFFER_USAGE_TRANSFER_DST_BIT |
        VK_BUFFER_USAGE_TRANSFER_SRC_BIT,
        // TODO: See if buffers over shared queues need to be
        // considered separately
        VK_SHARING_MODE_EXCLUSIVE,
        0, nullptr,
    };

    VkBuffer testBuffer;
    VkResult testBufferCreateRes =
        vk->vkCreateBuffer(device, &testCreateInfo, nullptr, &testBuffer);

    if (testBufferCreateRes != VK_SUCCESS) {
        VK_COMMON_ERROR(
            "Could not create test buffer "
            "for staging buffer query. VkResult: 0x%llx",
            (unsigned long long)testBufferCreateRes);
        return false;
    }

    VkMemoryRequirements memReqs;
    vk->vkGetBufferMemoryRequirements(device, testBuffer, &memReqs);

    // To be a staging buffer, we need to allow CPU read/write access.
    // Thus, we need the memory type index both to be host visible
    // and to be supported in the memory requirements of the buffer.
    bool foundSuitableStagingMemoryType = false;
    uint32_t stagingMemoryTypeIndex = 0;

    for (uint32_t i = 0; i < VK_MAX_MEMORY_TYPES; ++i) {
        const auto& typeInfo = memProps->memoryTypes[i];
        bool hostVisible =
            typeInfo.propertyFlags & VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT;
        bool hostCached =
            typeInfo.propertyFlags & VK_MEMORY_PROPERTY_HOST_CACHED_BIT;
        bool allowedInBuffer = (1 << i) & memReqs.memoryTypeBits;
        if (hostVisible && hostCached && allowedInBuffer) {
            foundSuitableStagingMemoryType = true;
            stagingMemoryTypeIndex = i;
            break;
        }
    }

    vk->vkDestroyBuffer(device, testBuffer, nullptr);

    if (!foundSuitableStagingMemoryType) {
        std::stringstream ss;
        ss <<
            "Could not find suitable memory type index " <<
            "for staging buffer. Memory type bits: " <<
            std::hex << memReqs.memoryTypeBits << "\n" <<
            "Available host visible memory type indices:" << "\n";
        for (uint32_t i = 0; i < VK_MAX_MEMORY_TYPES; ++i) {
            if (memProps->memoryTypes[i].propertyFlags &
                VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT) {
                ss << "Host visible memory type index: %u" << i << "\n";
            }
            if (memProps->memoryTypes[i].propertyFlags &
                VK_MEMORY_PROPERTY_HOST_CACHED_BIT) {
                ss << "Host cached memory type index: %u" << i << "\n";
            }
        }

        VK_COMMON_ERROR("Error: %s", ss.str().c_str());

        return false;
    }

    sKnownStagingTypeIndices.set(device, stagingMemoryTypeIndex);
    *typeIndex = stagingMemoryTypeIndex;

    return true;
}

static VkEmulation* sVkEmulation = nullptr;

static bool extensionsSupported(
    const std::vector<VkExtensionProperties>& currentProps,
    const std::vector<const char*>& wantedExtNames) {

    std::vector<bool> foundExts(wantedExtNames.size(), false);

    for (uint32_t i = 0; i < currentProps.size(); ++i) {
        VK_COMMON_VERBOSE("has extension: %s", currentProps[i].extensionName);
        for (size_t j = 0; j < wantedExtNames.size(); ++j) {
            if (!strcmp(wantedExtNames[j], currentProps[i].extensionName)) {
                foundExts[j] = true;
            }
        }
    }

    for (size_t i = 0; i < wantedExtNames.size(); ++i) {
        bool found = foundExts[i];
        // LOG(VERBOSE) << "needed extension: " << wantedExtNames[i]
        //              << " found: " << found;
        if (!found) {
            // LOG(VERBOSE) << wantedExtNames[i] << " not found, bailing.";
            return false;
        }
    }

    return true;
}

// For a given ImageSupportInfo, populates usageWithExternalHandles and
// requiresDedicatedAllocation. memoryTypeBits are populated later once the
// device is created, beacuse that needs a test image to be created.
// If we don't support external memory, it's assumed dedicated allocations are
// not needed.
// Precondition: sVkEmulation instance has been created and ext memory caps known.
// Returns false if the query failed.
static bool getImageFormatExternalMemorySupportInfo(
    VulkanDispatch* vk,
    VkPhysicalDevice physdev,
    VkEmulation::ImageSupportInfo* info) {

    // Currently there is nothing special we need to do about
    // VkFormatProperties2, so just use the normal version
    // and put it in the format2 struct.
    VkFormatProperties outFormatProps;
    vk->vkGetPhysicalDeviceFormatProperties(
            physdev, info->format, &outFormatProps);

    info->formatProps2 = {
        VK_STRUCTURE_TYPE_FORMAT_PROPERTIES_2, 0,
        outFormatProps,
    };

    if (!sVkEmulation->instanceSupportsExternalMemoryCapabilities) {
        info->supportsExternalMemory = false;
        info->requiresDedicatedAllocation = false;

        VkImageFormatProperties outImageFormatProps;
        VkResult res = vk->vkGetPhysicalDeviceImageFormatProperties(
                physdev, info->format, info->type, info->tiling,
                info->usageFlags, info->createFlags, &outImageFormatProps);

        if (res != VK_SUCCESS) {
            if (res == VK_ERROR_FORMAT_NOT_SUPPORTED) {
                info->supported = false;
                return true;
            } else {
                fprintf(stderr,
                        "%s: vkGetPhysicalDeviceImageFormatProperties query "
                        "failed with %d "
                        "for format 0x%x type 0x%x usage 0x%x flags 0x%x\n",
                        __func__, res, info->format, info->type,
                        info->usageFlags, info->createFlags);
                return false;
            }
        }

        info->supported = true;

        info->imageFormatProps2 = {
            VK_STRUCTURE_TYPE_IMAGE_FORMAT_PROPERTIES_2, 0,
            outImageFormatProps,
        };

        // LOG(VERBOSE) << "Supported (not externally): "
        //     << string_VkFormat(info->format) << " "
        //     << string_VkImageType(info->type) << " "
        //     << string_VkImageTiling(info->tiling) << " "
        //     << string_VkImageUsageFlagBits(
        //            (VkImageUsageFlagBits)info->usageFlags);

        return true;
    }

    VkPhysicalDeviceExternalImageFormatInfo extInfo = {
        VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_EXTERNAL_IMAGE_FORMAT_INFO, 0,
        VK_EXT_MEMORY_HANDLE_TYPE_BIT,
    };

    VkPhysicalDeviceImageFormatInfo2 formatInfo2 = {
        VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_IMAGE_FORMAT_INFO_2, &extInfo,
        info->format, info->type, info->tiling,
        info->usageFlags, info->createFlags,
    };

    VkExternalImageFormatProperties outExternalProps = {
        VK_STRUCTURE_TYPE_EXTERNAL_IMAGE_FORMAT_PROPERTIES,
        0,
        {
            (VkExternalMemoryFeatureFlags)0,
            (VkExternalMemoryHandleTypeFlags)0,
            (VkExternalMemoryHandleTypeFlags)0,
        },
    };

    VkImageFormatProperties2 outProps2 = {
        VK_STRUCTURE_TYPE_IMAGE_FORMAT_PROPERTIES_2, &outExternalProps,
        {
            { 0, 0, 0},
            0, 0,
            1, 0,
        }
    };

    VkResult res = sVkEmulation->getImageFormatProperties2Func(
        physdev,
        &formatInfo2,
        &outProps2);

    if (res != VK_SUCCESS) {
        if (res == VK_ERROR_FORMAT_NOT_SUPPORTED) {
            info->supported = false;
            return true;
        } else {
            fprintf(stderr,
                    "%s: vkGetPhysicalDeviceImageFormatProperties2KHR query "
                    "failed "
                    "for format 0x%x type 0x%x usage 0x%x flags 0x%x\n",
                    __func__, info->format, info->type, info->usageFlags,
                    info->createFlags);
            return false;
        }
    }

    info->supported = true;

    VkExternalMemoryFeatureFlags featureFlags =
        outExternalProps.externalMemoryProperties.externalMemoryFeatures;

    VkExternalMemoryHandleTypeFlags exportImportedFlags =
        outExternalProps.externalMemoryProperties.exportFromImportedHandleTypes;

    // Don't really care about export form imported handle types yet
    (void)exportImportedFlags;

    VkExternalMemoryHandleTypeFlags compatibleHandleTypes =
        outExternalProps.externalMemoryProperties.compatibleHandleTypes;

    info->supportsExternalMemory =
        (VK_EXT_MEMORY_HANDLE_TYPE_BIT & compatibleHandleTypes) &&
        (VK_EXTERNAL_MEMORY_FEATURE_EXPORTABLE_BIT & featureFlags) &&
        (VK_EXTERNAL_MEMORY_FEATURE_IMPORTABLE_BIT & featureFlags);

    info->requiresDedicatedAllocation =
        (VK_EXTERNAL_MEMORY_FEATURE_DEDICATED_ONLY_BIT & featureFlags);

    info->imageFormatProps2 = outProps2;
    info->extFormatProps = outExternalProps;
    info->imageFormatProps2.pNext = &info->extFormatProps;

    // LOG(VERBOSE) << "Supported: "
    //              << string_VkFormat(info->format) << " "
    //              << string_VkImageType(info->type) << " "
    //              << string_VkImageTiling(info->tiling) << " "
    //              << string_VkImageUsageFlagBits(
    //                         (VkImageUsageFlagBits)info->usageFlags)
    //              << " "
    //              << "supportsExternalMemory? " << info->supportsExternalMemory
    //              << " "
    //              << "requiresDedicated? " << info->requiresDedicatedAllocation;

    return true;
}

// Vulkan driverVersions are bit-shift packs of their dotted versions
// For example, nvidia driverversion 1934229504 unpacks to 461.40
// note: while this is equivalent to VkPhysicalDeviceDriverProperties.driverInfo on NVIDIA,
// on intel that value is simply "Intel driver".
static std::string decodeDriverVersion(uint32_t vendorId, uint32_t driverVersion) {
    std::stringstream result;
    switch (vendorId) {
        case 0x10DE: {
            // Nvidia. E.g. driverVersion = 1934229504(0x734a0000) maps to 461.40
            uint32_t major = driverVersion >> 22;
            uint32_t minor = (driverVersion >> 14) & 0xff;
            uint32_t build = (driverVersion >> 6) & 0xff;
            uint32_t revision = driverVersion & 0x3f;
            result << major << '.' << minor << '.' << build << '.' << revision;
            break;
        }
        case 0x8086: {
            // Intel. E.g. driverVersion = 1647866(0x1924fa) maps to 100.9466 (27.20.100.9466)
            uint32_t high = driverVersion >> 14;
            uint32_t low = driverVersion & 0x3fff;
            result << high << '.' << low;
            break;
        }
        case 0x002:  // amd
        default: {
            uint32_t major = VK_VERSION_MAJOR(driverVersion);
            uint32_t minor = VK_VERSION_MINOR(driverVersion);
            uint32_t patch = VK_VERSION_PATCH(driverVersion);
            result << major << "." << minor << "." << patch;
            break;
        }
    }
    return result.str();
}

static std::vector<VkEmulation::ImageSupportInfo> getBasicImageSupportList() {
    std::vector<VkFormat> formats = {
        // Cover all the gralloc formats
        VK_FORMAT_R8G8B8A8_UNORM,
        VK_FORMAT_R8G8B8_UNORM,

        VK_FORMAT_R5G6B5_UNORM_PACK16,

        VK_FORMAT_R16G16B16A16_SFLOAT,
        VK_FORMAT_R16G16B16_SFLOAT,

        VK_FORMAT_B8G8R8A8_UNORM,

        VK_FORMAT_R8_UNORM,

        VK_FORMAT_A2R10G10B10_UINT_PACK32,
        VK_FORMAT_A2R10G10B10_UNORM_PACK32,

        // Compressed texture formats
        VK_FORMAT_ETC2_R8G8B8A8_UNORM_BLOCK,
        VK_FORMAT_ASTC_4x4_UNORM_BLOCK,

        // TODO: YUV formats used in Android
        // Fails on Mac
        VK_FORMAT_G8_B8R8_2PLANE_420_UNORM,
        VK_FORMAT_G8_B8R8_2PLANE_422_UNORM,
        VK_FORMAT_G8_B8_R8_3PLANE_420_UNORM,
        VK_FORMAT_G8_B8_R8_3PLANE_422_UNORM,

    };

    std::vector<VkImageType> types = {
        VK_IMAGE_TYPE_2D,
    };

    std::vector<VkImageTiling> tilings = {
        VK_IMAGE_TILING_LINEAR,
        VK_IMAGE_TILING_OPTIMAL,
    };

    std::vector<VkImageUsageFlags> usageFlags = {
        VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT,
        VK_IMAGE_USAGE_INPUT_ATTACHMENT_BIT,
        VK_IMAGE_USAGE_SAMPLED_BIT,
        VK_IMAGE_USAGE_TRANSFER_SRC_BIT,
        VK_IMAGE_USAGE_TRANSFER_DST_BIT,
    };

    std::vector<VkImageCreateFlags> createFlags = {
        0,
    };

    std::vector<VkEmulation::ImageSupportInfo> res;

    // Currently: 12 formats, 2 tilings, 5 usage flags -> 120 cases
    // to check
    for (auto f : formats) {
        for (auto t : types) {
            for (auto ti : tilings) {
                for (auto u : usageFlags) {
                    for (auto c : createFlags) {
                        VkEmulation::ImageSupportInfo info;
                        info.format = f;
                        info.type = t;
                        info.tiling = ti;
                        info.usageFlags = u;
                        info.createFlags = c;
                        res.push_back(info);
                    }
                }
            }
        }
    }

    return res;
}

VkEmulation* createGlobalVkEmulation(VulkanDispatch* vk) {
#define VK_EMU_INIT_RETURN_ON_ERROR(...) \
    do {                                 \
        ERR(__VA_ARGS__);                \
        return nullptr;                  \
    } while (0)

    AutoLock lock(sVkEmulationLock);

    if (sVkEmulation) return sVkEmulation;

    if (!emugl::vkDispatchValid(vk)) {
        VK_EMU_INIT_RETURN_ON_ERROR("Dispatch is invalid.");
    }

    sVkEmulation = new VkEmulation;

    sVkEmulation->gvk = vk;
    auto gvk = vk;

    std::vector<const char*> externalMemoryInstanceExtNames = {
        "VK_KHR_external_memory_capabilities",
        "VK_KHR_get_physical_device_properties2",
    };

    std::vector<const char*> externalMemoryDeviceExtNames = {
        "VK_KHR_dedicated_allocation",
        "VK_KHR_get_memory_requirements2",
        "VK_KHR_external_memory",
#ifdef _WIN32
        "VK_KHR_external_memory_win32",
#else
        "VK_KHR_external_memory_fd",
#endif
    };

    uint32_t extCount = 0;
    gvk->vkEnumerateInstanceExtensionProperties(nullptr, &extCount, nullptr);
    std::vector<VkExtensionProperties>& exts = sVkEmulation->instanceExtensions;
    exts.resize(extCount);
    gvk->vkEnumerateInstanceExtensionProperties(nullptr, &extCount, exts.data());

    bool externalMemoryCapabilitiesSupported =
        extensionsSupported(exts, externalMemoryInstanceExtNames);
    bool moltenVKSupported = (vk->vkGetMTLTextureMVK != nullptr) &&
        (vk->vkSetMTLTextureMVK != nullptr);

    VkInstanceCreateInfo instCi = {
        VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO,
        0, 0, nullptr, 0, nullptr,
        0, nullptr,
    };

    std::unordered_set<const char*> enabledExtensions;

    if (externalMemoryCapabilitiesSupported) {
        for (auto extension : externalMemoryInstanceExtNames) {
            enabledExtensions.emplace(extension);
        }
    }

    if (moltenVKSupported) {
        // We don't need both moltenVK and external memory. Disable
        // external memory if moltenVK is supported.
        externalMemoryCapabilitiesSupported = false;
        enabledExtensions.clear();
    }

    for (auto extension : SwapChainStateVk::getRequiredInstanceExtensions()) {
        enabledExtensions.emplace(extension);
    }
    std::vector<const char*> enabledExtensions_(enabledExtensions.begin(),
                                                enabledExtensions.end());
    instCi.enabledExtensionCount =
        static_cast<uint32_t>(enabledExtensions_.size());
    instCi.ppEnabledExtensionNames = enabledExtensions_.data();

    VkApplicationInfo appInfo = {
        VK_STRUCTURE_TYPE_APPLICATION_INFO, 0,
        "AEMU", 1,
        "AEMU", 1,
        VK_MAKE_VERSION(1, 0, 0),
    };

    instCi.pApplicationInfo = &appInfo;

    // Can we know instance version early?
    if (gvk->vkEnumerateInstanceVersion) {
        // LOG(VERBOSE) << "global loader has vkEnumerateInstanceVersion.";
        uint32_t instanceVersion;
        VkResult res = gvk->vkEnumerateInstanceVersion(&instanceVersion);
        if (VK_SUCCESS == res) {
            if (instanceVersion >= VK_MAKE_VERSION(1, 1, 0)) {
                // LOG(VERBOSE) << "global loader has vkEnumerateInstanceVersion returning >= 1.1.";
                appInfo.apiVersion = VK_MAKE_VERSION(1, 1, 0);
            }
        }
    }

    // LOG(VERBOSE) << "Creating instance, asking for version "
    //              << VK_VERSION_MAJOR(appInfo.apiVersion) << "."
    //              << VK_VERSION_MINOR(appInfo.apiVersion) << "."
    //              << VK_VERSION_PATCH(appInfo.apiVersion) << " ...";

    VkResult res = gvk->vkCreateInstance(&instCi, nullptr, &sVkEmulation->instance);

    if (res != VK_SUCCESS) {
        VK_EMU_INIT_RETURN_ON_ERROR("Failed to create Vulkan instance. Error %s.",
                                    string_VkResult(res));
    }

    // Create instance level dispatch.
    sVkEmulation->ivk = new VulkanDispatch;
    init_vulkan_dispatch_from_instance(
        vk, sVkEmulation->instance, sVkEmulation->ivk);

    auto ivk = sVkEmulation->ivk;

    if (!vulkan_dispatch_check_instance_VK_VERSION_1_0(ivk)) {
        fprintf(stderr, "%s: Warning: Vulkan 1.0 APIs missing from instance\n", __func__);
    }

    if (ivk->vkEnumerateInstanceVersion) {
        uint32_t instanceVersion;
        VkResult enumInstanceRes = ivk->vkEnumerateInstanceVersion(&instanceVersion);
        if ((VK_SUCCESS == enumInstanceRes) &&
            instanceVersion >= VK_MAKE_VERSION(1, 1, 0)) {
            if (!vulkan_dispatch_check_instance_VK_VERSION_1_1(ivk)) {
                fprintf(stderr, "%s: Warning: Vulkan 1.1 APIs missing from instance (1st try)\n", __func__);
            }
        }

        if (appInfo.apiVersion < VK_MAKE_VERSION(1, 1, 0) &&
            instanceVersion >= VK_MAKE_VERSION(1, 1, 0)) {
            // LOG(VERBOSE) << "Found out that we can create a higher version instance.";
            appInfo.apiVersion = VK_MAKE_VERSION(1, 1, 0);

            gvk->vkDestroyInstance(sVkEmulation->instance, nullptr);

            VkResult res = gvk->vkCreateInstance(&instCi, nullptr, &sVkEmulation->instance);

            if (res != VK_SUCCESS) {
                VK_EMU_INIT_RETURN_ON_ERROR("Failed to create Vulkan 1.1 instance. Error %s.",
                                            string_VkResult(res));
            }

            init_vulkan_dispatch_from_instance(
                vk, sVkEmulation->instance, sVkEmulation->ivk);

            // LOG(VERBOSE) << "Created Vulkan 1.1 instance on second try.";

            if (!vulkan_dispatch_check_instance_VK_VERSION_1_1(ivk)) {
                fprintf(stderr, "%s: Warning: Vulkan 1.1 APIs missing from instance (2nd try)\n", __func__);
            }
        }
    }

    sVkEmulation->vulkanInstanceVersion = appInfo.apiVersion;

    sVkEmulation->instanceSupportsExternalMemoryCapabilities =
        externalMemoryCapabilitiesSupported;
    sVkEmulation->instanceSupportsMoltenVK = moltenVKSupported;

    if (sVkEmulation->instanceSupportsExternalMemoryCapabilities) {
        sVkEmulation->getImageFormatProperties2Func = vk_util::getVkInstanceProcAddrWithFallback<
            vk_util::vk_fn_info::GetPhysicalDeviceImageFormatProperties2>(
            {ivk->vkGetInstanceProcAddr, vk->vkGetInstanceProcAddr}, sVkEmulation->instance);
        sVkEmulation->getPhysicalDeviceProperties2Func = vk_util::getVkInstanceProcAddrWithFallback<
            vk_util::vk_fn_info::GetPhysicalDeviceProperties2>(
            {ivk->vkGetInstanceProcAddr, vk->vkGetInstanceProcAddr}, sVkEmulation->instance);
    }
    sVkEmulation->getPhysicalDeviceFeatures2Func =
        vk_util::getVkInstanceProcAddrWithFallback<vk_util::vk_fn_info::GetPhysicalDeviceFeatures2>(
            {ivk->vkGetInstanceProcAddr, vk->vkGetInstanceProcAddr}, sVkEmulation->instance);

    if (sVkEmulation->instanceSupportsMoltenVK) {
        sVkEmulation->setMTLTextureFunc = reinterpret_cast<PFN_vkSetMTLTextureMVK>(
                vk->vkGetInstanceProcAddr(
                        sVkEmulation->instance, "vkSetMTLTextureMVK"));

        if (!sVkEmulation->setMTLTextureFunc) {
            VK_EMU_INIT_RETURN_ON_ERROR("Cannot find vkSetMTLTextureMVK.");
        }
       sVkEmulation->getMTLTextureFunc = reinterpret_cast<PFN_vkGetMTLTextureMVK>(
                vk->vkGetInstanceProcAddr(
                        sVkEmulation->instance, "vkGetMTLTextureMVK"));
        if (!sVkEmulation->getMTLTextureFunc) {
            VK_EMU_INIT_RETURN_ON_ERROR("Cannot find vkGetMTLTextureMVK.");
        }
        // LOG(VERBOSE) << "Instance supports VK_MVK_moltenvk.";
    }

    uint32_t physdevCount = 0;
    ivk->vkEnumeratePhysicalDevices(sVkEmulation->instance, &physdevCount,
                                   nullptr);
    std::vector<VkPhysicalDevice> physdevs(physdevCount);
    ivk->vkEnumeratePhysicalDevices(sVkEmulation->instance, &physdevCount,
                                   physdevs.data());

    // LOG(VERBOSE) << "Found " << physdevCount << " Vulkan physical devices.";

    if (physdevCount == 0) {
        VK_EMU_INIT_RETURN_ON_ERROR("No physical devices available.");
    }

    std::vector<VkEmulation::DeviceSupportInfo> deviceInfos(physdevCount);

    for (int i = 0; i < physdevCount; ++i) {
        ivk->vkGetPhysicalDeviceProperties(physdevs[i],
                                           &deviceInfos[i].physdevProps);

        // LOG(VERBOSE) << "Considering Vulkan physical device " << i << ": "
        //              << deviceInfos[i].physdevProps.deviceName;

        // It's easier to figure out the staging buffer along with
        // external memories if we have the memory properties on hand.
        ivk->vkGetPhysicalDeviceMemoryProperties(physdevs[i],
                                                &deviceInfos[i].memProps);

        uint32_t deviceExtensionCount = 0;
        ivk->vkEnumerateDeviceExtensionProperties(
            physdevs[i], nullptr, &deviceExtensionCount, nullptr);
        std::vector<VkExtensionProperties>& deviceExts = deviceInfos[i].extensions;
        deviceExts.resize(deviceExtensionCount);
        ivk->vkEnumerateDeviceExtensionProperties(
            physdevs[i], nullptr, &deviceExtensionCount, deviceExts.data());

        deviceInfos[i].supportsExternalMemory = false;
        deviceInfos[i].glInteropSupported = 0; // set later

        if (sVkEmulation->instanceSupportsExternalMemoryCapabilities) {
            deviceInfos[i].supportsExternalMemory =
                extensionsSupported(deviceExts, externalMemoryDeviceExtNames);
            deviceInfos[i].supportsIdProperties =
                sVkEmulation->getPhysicalDeviceProperties2Func != nullptr;
            deviceInfos[i].supportsDriverProperties =
                extensionsSupported(deviceExts, {VK_KHR_DRIVER_PROPERTIES_EXTENSION_NAME}) ||
                (deviceInfos[i].physdevProps.apiVersion >= VK_API_VERSION_1_2);

            if (!sVkEmulation->getPhysicalDeviceProperties2Func) {
                fprintf(stderr, "%s: warning: device claims to support ID properties "
                        "but vkGetPhysicalDeviceProperties2 could not be found\n", __func__);
            }
        }

        if (sVkEmulation->getPhysicalDeviceProperties2Func) {
            VkPhysicalDeviceProperties2 deviceProps = {
                VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_PROPERTIES_2_KHR,
            };
            VkPhysicalDeviceIDProperties idProps = {
                VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_ID_PROPERTIES_KHR,
            };
            VkPhysicalDeviceDriverPropertiesKHR driverProps = {
                VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_DRIVER_PROPERTIES_KHR,
            };

            auto devicePropsChain = vk_make_chain_iterator(&deviceProps);

            if (deviceInfos[i].supportsIdProperties) {
                vk_append_struct(&devicePropsChain, &idProps);
            }

            if (deviceInfos[i].supportsDriverProperties) {
                vk_append_struct(&devicePropsChain, &driverProps);
            }

            sVkEmulation->getPhysicalDeviceProperties2Func(
                physdevs[i],
                &deviceProps);

            deviceInfos[i].idProps = vk_make_orphan_copy(idProps);

            std::stringstream driverVendorBuilder;
            driverVendorBuilder << "Vendor " << std::hex << std::setfill('0') << std::showbase
                                << deviceInfos[i].physdevProps.vendorID;

            std::string decodedDriverVersion = decodeDriverVersion(
                deviceInfos[i].physdevProps.vendorID,
                deviceInfos[i].physdevProps.driverVersion);

            std::stringstream driverVersionBuilder;
            driverVersionBuilder << "Driver Version " << std::hex << std::setfill('0')
                                 << std::showbase << deviceInfos[i].physdevProps.driverVersion
                                 << " Decoded As " << decodedDriverVersion;

            std::string driverVendor = driverVendorBuilder.str();
            std::string driverVersion = driverVersionBuilder.str();
            if (deviceInfos[i].supportsDriverProperties && driverProps.driverID) {
                driverVendor = std::string{driverProps.driverName} + " (" + driverVendor + ")";
                driverVersion = std::string{driverProps.driverInfo} + " (" +
                                string_VkDriverId(driverProps.driverID) + " " + driverVersion + ")";
            }

            deviceInfos[i].driverVendor = driverVendor;
            deviceInfos[i].driverVersion = driverVersion;
        }

        deviceInfos[i].hasSamplerYcbcrConversionExtension =
            extensionsSupported(deviceExts, {VK_KHR_SAMPLER_YCBCR_CONVERSION_EXTENSION_NAME});
        if (sVkEmulation->getPhysicalDeviceFeatures2Func) {
            VkPhysicalDeviceFeatures2 features2 = {
                .sType = VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_FEATURES_2,
            };
            auto features2Chain = vk_make_chain_iterator(&features2);
            VkPhysicalDeviceSamplerYcbcrConversionFeatures samplerYcbcrConversionFeatures = {
                .sType = VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_SAMPLER_YCBCR_CONVERSION_FEATURES,
            };
            vk_append_struct(&features2Chain, &samplerYcbcrConversionFeatures);
            sVkEmulation->getPhysicalDeviceFeatures2Func(physdevs[i], &features2);

            deviceInfos[i].supportsSamplerYcbcrConversion =
                samplerYcbcrConversionFeatures.samplerYcbcrConversion == VK_TRUE;
        }

        uint32_t queueFamilyCount = 0;
        ivk->vkGetPhysicalDeviceQueueFamilyProperties(
                physdevs[i], &queueFamilyCount, nullptr);
        std::vector<VkQueueFamilyProperties> queueFamilyProps(queueFamilyCount);
        ivk->vkGetPhysicalDeviceQueueFamilyProperties(
                physdevs[i], &queueFamilyCount, queueFamilyProps.data());

        for (uint32_t j = 0; j < queueFamilyCount; ++j) {
            auto count = queueFamilyProps[j].queueCount;
            auto flags = queueFamilyProps[j].queueFlags;

            bool hasGraphicsQueueFamily =
                (count > 0 && (flags & VK_QUEUE_GRAPHICS_BIT));
            bool hasComputeQueueFamily =
                (count > 0 && (flags & VK_QUEUE_COMPUTE_BIT));

            deviceInfos[i].hasGraphicsQueueFamily =
                deviceInfos[i].hasGraphicsQueueFamily ||
                hasGraphicsQueueFamily;

            deviceInfos[i].hasComputeQueueFamily =
                deviceInfos[i].hasComputeQueueFamily ||
                hasComputeQueueFamily;

            if (hasGraphicsQueueFamily) {
                deviceInfos[i].graphicsQueueFamilyIndices.push_back(j);
                // LOG(VERBOSE) << "Graphics queue family index: " << j;
            }

            if (hasComputeQueueFamily) {
                deviceInfos[i].computeQueueFamilyIndices.push_back(j);
                // LOG(VERBOSE) << "Compute queue family index: " << j;
            }
        }
    }

    // Of all the devices enumerated, find the best one. Try to find a device
    // with graphics queue as the highest priority, then ext memory, then
    // compute.

    // Graphics queue is highest priority since without that, we really
    // shouldn't be using the driver. Although, one could make a case for doing
    // some sorts of things if only a compute queue is available (such as for
    // AI), that's not really the priority yet.

    // As for external memory, we really should not be running on any driver
    // without external memory support, but we might be able to pull it off, and
    // single Vulkan apps might work via CPU transfer of the rendered frames.

    // Compute support is treated as icing on the cake and not relied upon yet
    // for anything critical to emulation. However, we might potentially use it
    // to perform image format conversion on GPUs where that's not natively
    // supported.

    // Another implicit choice is to select only one Vulkan device. This makes
    // things simple for now, but we could consider utilizing multiple devices
    // in use cases that make sense, if/when they come up.

    std::vector<uint32_t> deviceScores(physdevCount, 0);

    for (uint32_t i = 0; i < physdevCount; ++i) {
        uint32_t deviceScore = 0;
        if (deviceInfos[i].hasGraphicsQueueFamily) deviceScore += 10000;
        if (deviceInfos[i].supportsExternalMemory) deviceScore += 1000;
        if (deviceInfos[i].physdevProps.deviceType == VK_PHYSICAL_DEVICE_TYPE_DISCRETE_GPU ||
            deviceInfos[i].physdevProps.deviceType == VK_PHYSICAL_DEVICE_TYPE_VIRTUAL_GPU) {
            deviceScore += 100;
        }
        if (deviceInfos[i].physdevProps.deviceType == VK_PHYSICAL_DEVICE_TYPE_INTEGRATED_GPU) {
            deviceScore += 50;
        }
        deviceScores[i] = deviceScore;
    }

    uint32_t maxScoringIndex = 0;
    uint32_t maxScore = 0;

    // If we don't support physical device ID properties,
    // just pick the first physical device.
    if (!sVkEmulation->instanceSupportsExternalMemoryCapabilities) {
        fprintf(stderr, "%s: warning: instance doesn't support "
            "external memory capabilities, picking first physical device\n", __func__);
        maxScoringIndex = 0;
    } else {
        for (uint32_t i = 0; i < physdevCount; ++i) {
            if (deviceScores[i] > maxScore) {
                maxScoringIndex = i;
                maxScore = deviceScores[i];
            }
        }
    }

    sVkEmulation->physdev = physdevs[maxScoringIndex];
    sVkEmulation->deviceInfo = deviceInfos[maxScoringIndex];
    // Postcondition: sVkEmulation has valid device support info

    // Ask about image format support here.
    // TODO: May have to first ask when selecting physical devices
    // (e.g., choose between Intel or NVIDIA GPU for certain image format
    // support)
    sVkEmulation->imageSupportInfo = getBasicImageSupportList();
    for (size_t i = 0; i < sVkEmulation->imageSupportInfo.size(); ++i) {
        getImageFormatExternalMemorySupportInfo(
                ivk, sVkEmulation->physdev, &sVkEmulation->imageSupportInfo[i]);
    }

    if (!sVkEmulation->deviceInfo.hasGraphicsQueueFamily) {
        VK_EMU_INIT_RETURN_ON_ERROR("No Vulkan devices with graphics queues found.");
    }

    auto deviceVersion = sVkEmulation->deviceInfo.physdevProps.apiVersion;
    VK_COMMON_LOG("Selecting Vulkan device: %s", sVkEmulation->deviceInfo.physdevProps.deviceName);

    // LOG(VERBOSE) << "Version: "
    //              << VK_VERSION_MAJOR(deviceVersion) << "." << VK_VERSION_MINOR(deviceVersion) << "." << VK_VERSION_PATCH(deviceVersion);
    // LOG(VERBOSE) << "Has graphics queue? "
    //              << sVkEmulation->deviceInfo.hasGraphicsQueueFamily;
    // LOG(VERBOSE) << "Has external memory support? "
    //              << sVkEmulation->deviceInfo.supportsExternalMemory;
    // LOG(VERBOSE) << "Has compute queue? "
    //              << sVkEmulation->deviceInfo.hasComputeQueueFamily;

    float priority = 1.0f;
    VkDeviceQueueCreateInfo dqCi = {
        VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO, 0, 0,
        sVkEmulation->deviceInfo.graphicsQueueFamilyIndices[0],
        1, &priority,
    };

    std::unordered_set<const char*> selectedDeviceExtensionNames_;

    if (sVkEmulation->deviceInfo.supportsExternalMemory) {
        for (auto extension : externalMemoryDeviceExtNames) {
            selectedDeviceExtensionNames_.emplace(extension);
        }
    }
    for (auto extension : SwapChainStateVk::getRequiredDeviceExtensions()) {
        selectedDeviceExtensionNames_.emplace(extension);
    }
    if (sVkEmulation->deviceInfo.hasSamplerYcbcrConversionExtension) {
        selectedDeviceExtensionNames_.emplace(VK_KHR_SAMPLER_YCBCR_CONVERSION_EXTENSION_NAME);
    }
    std::vector<const char*> selectedDeviceExtensionNames(
        selectedDeviceExtensionNames_.begin(),
        selectedDeviceExtensionNames_.end());

    VkDeviceCreateInfo dCi = {};
    dCi.sType = VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO;
    dCi.queueCreateInfoCount = 1;
    dCi.pQueueCreateInfos = &dqCi;
    dCi.enabledExtensionCount =
        static_cast<uint32_t>(selectedDeviceExtensionNames.size());
    dCi.ppEnabledExtensionNames = selectedDeviceExtensionNames.data();

    // Setting up VkDeviceCreateInfo::pNext
    auto deviceCiChain = vk_make_chain_iterator(&dCi);

    VkPhysicalDeviceFeatures2 features = {.sType = VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_FEATURES_2};
    vk_append_struct(&deviceCiChain, &features);

    std::unique_ptr<VkPhysicalDeviceSamplerYcbcrConversionFeatures> samplerYcbcrConversionFeatures =
        nullptr;
    if (sVkEmulation->deviceInfo.supportsSamplerYcbcrConversion) {
        samplerYcbcrConversionFeatures =
            std::make_unique<VkPhysicalDeviceSamplerYcbcrConversionFeatures>(
                VkPhysicalDeviceSamplerYcbcrConversionFeatures{
                    .sType = VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_SAMPLER_YCBCR_CONVERSION_FEATURES,
                    .samplerYcbcrConversion = VK_TRUE,
                });
        vk_append_struct(&deviceCiChain, samplerYcbcrConversionFeatures.get());
    }

    ivk->vkCreateDevice(sVkEmulation->physdev, &dCi, nullptr,
                        &sVkEmulation->device);

    if (res != VK_SUCCESS) {
        VK_EMU_INIT_RETURN_ON_ERROR("Failed to create Vulkan device. Error %s.",
                                    string_VkResult(res));
    }

    // device created; populate dispatch table
    sVkEmulation->dvk = new VulkanDispatch;
    init_vulkan_dispatch_from_device(
        ivk, sVkEmulation->device, sVkEmulation->dvk);

    auto dvk = sVkEmulation->dvk;

    // Check if the dispatch table has everything 1.1 related
    if (!vulkan_dispatch_check_device_VK_VERSION_1_0(dvk)) {
        fprintf(stderr, "%s: Warning: Vulkan 1.0 APIs missing from device.\n", __func__);
    }
    if (deviceVersion >= VK_MAKE_VERSION(1, 1, 0)) {
        if (!vulkan_dispatch_check_device_VK_VERSION_1_1(dvk)) {
            fprintf(stderr, "%s: Warning: Vulkan 1.1 APIs missing from device\n", __func__);
        }
    }

    if (sVkEmulation->deviceInfo.supportsExternalMemory) {
        sVkEmulation->deviceInfo.getImageMemoryRequirements2Func =
            reinterpret_cast<PFN_vkGetImageMemoryRequirements2KHR>(
                dvk->vkGetDeviceProcAddr(
                    sVkEmulation->device, "vkGetImageMemoryRequirements2KHR"));
        if (!sVkEmulation->deviceInfo.getImageMemoryRequirements2Func) {
            VK_EMU_INIT_RETURN_ON_ERROR("Cannot find vkGetImageMemoryRequirements2KHR.");
        }
        sVkEmulation->deviceInfo.getBufferMemoryRequirements2Func =
            reinterpret_cast<PFN_vkGetBufferMemoryRequirements2KHR>(
                dvk->vkGetDeviceProcAddr(
                    sVkEmulation->device, "vkGetBufferMemoryRequirements2KHR"));
        if (!sVkEmulation->deviceInfo.getBufferMemoryRequirements2Func) {
            VK_EMU_INIT_RETURN_ON_ERROR("Cannot find vkGetBufferMemoryRequirements2KHR");
        }
#ifdef _WIN32
        sVkEmulation->deviceInfo.getMemoryHandleFunc =
                reinterpret_cast<PFN_vkGetMemoryWin32HandleKHR>(
                        dvk->vkGetDeviceProcAddr(sVkEmulation->device,
                                                "vkGetMemoryWin32HandleKHR"));
#else
        sVkEmulation->deviceInfo.getMemoryHandleFunc =
                reinterpret_cast<PFN_vkGetMemoryFdKHR>(
                        dvk->vkGetDeviceProcAddr(sVkEmulation->device,
                                                "vkGetMemoryFdKHR"));
#endif
        if (!sVkEmulation->deviceInfo.getMemoryHandleFunc) {
            VK_EMU_INIT_RETURN_ON_ERROR("Cannot find vkGetMemory(Fd|Win32Handle)KHR");
        }
    }

    // LOG(VERBOSE) << "Vulkan logical device created and extension functions obtained.\n";

    sVkEmulation->queueLock = std::make_shared<android::base::Lock>();
    {
        android::base::AutoLock lock(*sVkEmulation->queueLock);
        dvk->vkGetDeviceQueue(
            sVkEmulation->device,
            sVkEmulation->deviceInfo.graphicsQueueFamilyIndices[0], 0,
            &sVkEmulation->queue);
    }

    sVkEmulation->queueFamilyIndex =
            sVkEmulation->deviceInfo.graphicsQueueFamilyIndices[0];

    // LOG(VERBOSE) << "Vulkan device queue obtained.";

    VkCommandPoolCreateInfo poolCi = {
        VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO, 0,
        VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT,
        sVkEmulation->queueFamilyIndex,
    };

    VkResult poolCreateRes = dvk->vkCreateCommandPool(
            sVkEmulation->device, &poolCi, nullptr, &sVkEmulation->commandPool);

    if (poolCreateRes != VK_SUCCESS) {
        VK_EMU_INIT_RETURN_ON_ERROR("Failed to create command pool. Error: %s.",
                                    string_VkResult(poolCreateRes));
    }

    VkCommandBufferAllocateInfo cbAi = {
        VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO, 0,
        sVkEmulation->commandPool, VK_COMMAND_BUFFER_LEVEL_PRIMARY, 1,
    };

    VkResult cbAllocRes = dvk->vkAllocateCommandBuffers(
            sVkEmulation->device, &cbAi, &sVkEmulation->commandBuffer);

    if (cbAllocRes != VK_SUCCESS) {
        VK_EMU_INIT_RETURN_ON_ERROR("Failed to allocate command buffer. Error: %s.",
                                    string_VkResult(cbAllocRes));
    }

    VkFenceCreateInfo fenceCi = {
        VK_STRUCTURE_TYPE_FENCE_CREATE_INFO, 0, 0,
    };

    VkResult fenceCreateRes = dvk->vkCreateFence(
        sVkEmulation->device, &fenceCi, nullptr,
        &sVkEmulation->commandBufferFence);

    if (fenceCreateRes != VK_SUCCESS) {
        VK_EMU_INIT_RETURN_ON_ERROR("Failed to create fence for command buffer. Error: %s.",
                                    string_VkResult(fenceCreateRes));
    }

    // At this point, the global emulation state's logical device can alloc
    // memory and send commands. However, it can't really do much yet to
    // communicate the results without the staging buffer. Set that up here.
    // Note that the staging buffer is meant to use external memory, with a
    // non-external-memory fallback.

    VkBufferCreateInfo bufCi = {
        VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO, 0, 0,
        sVkEmulation->staging.size,
        VK_BUFFER_USAGE_TRANSFER_DST_BIT |
        VK_BUFFER_USAGE_TRANSFER_SRC_BIT,
        VK_SHARING_MODE_EXCLUSIVE,
        0, nullptr,
    };

    VkResult bufCreateRes =
            dvk->vkCreateBuffer(sVkEmulation->device, &bufCi, nullptr,
                               &sVkEmulation->staging.buffer);

    if (bufCreateRes != VK_SUCCESS) {
        VK_EMU_INIT_RETURN_ON_ERROR("Failed to create staging buffer index. Error: %s.",
                                    string_VkResult(bufCreateRes));
    }

    VkMemoryRequirements memReqs;
    dvk->vkGetBufferMemoryRequirements(sVkEmulation->device,
                                      sVkEmulation->staging.buffer, &memReqs);

    sVkEmulation->staging.memory.size = memReqs.size;

    bool gotStagingTypeIndex = getStagingMemoryTypeIndex(
            dvk, sVkEmulation->device, &sVkEmulation->deviceInfo.memProps,
            &sVkEmulation->staging.memory.typeIndex);

    if (!gotStagingTypeIndex) {
        VK_EMU_INIT_RETURN_ON_ERROR("Failed to determine staging memory type index.");
    }

    if (!((1 << sVkEmulation->staging.memory.typeIndex) &
          memReqs.memoryTypeBits)) {
        VK_EMU_INIT_RETURN_ON_ERROR(
            "Failed: Inconsistent determination of memory type index for staging buffer");
    }

    if (!allocExternalMemory(dvk, &sVkEmulation->staging.memory,
                             false /* not external */,
                             kNullopt /* deviceAlignment */)) {
        VK_EMU_INIT_RETURN_ON_ERROR("Failed to allocate memory for staging buffer.");
    }

    VkResult stagingBufferBindRes = dvk->vkBindBufferMemory(
        sVkEmulation->device,
        sVkEmulation->staging.buffer,
        sVkEmulation->staging.memory.memory, 0);

    if (stagingBufferBindRes != VK_SUCCESS) {
        VK_EMU_INIT_RETURN_ON_ERROR("Failed to bind memory for staging buffer.");
    }

    // LOG(VERBOSE) << "Vulkan global emulation state successfully initialized.";
    sVkEmulation->live = true;

    sVkEmulation->transferQueueCommandBufferPool.resize(0);

    return sVkEmulation;
}

void initVkEmulationFeatures(std::unique_ptr<VkEmulationFeatures> features) {
    if (!sVkEmulation || !sVkEmulation->live) {
        ERR("VkEmulation is either not initialized or destroyed.");
        return;
    }

    AutoLock lock(sVkEmulationLock);
    INFO("Initializing VkEmulation features:");
    INFO("    glInteropSupported: %s", features->glInteropSupported ? "true" : "false");
    INFO("    useDeferredCommands: %s", features->deferredCommands ? "true" : "false");
    INFO("    createResourceWithRequirements: %s",
         features->createResourceWithRequirements ? "true" : "false");
    INFO("    useVulkanNativeSwapchain: %s", features->useVulkanNativeSwapchain ? "true" : "false");
    INFO("    enable guestRenderDoc: %s", features->guestRenderDoc ? "true" : "false");
    sVkEmulation->deviceInfo.glInteropSupported = features->glInteropSupported;
    sVkEmulation->useDeferredCommands = features->deferredCommands;
    sVkEmulation->useCreateResourcesWithRequirements = features->createResourceWithRequirements;
    sVkEmulation->guestRenderDoc = std::move(features->guestRenderDoc);

    if (features->useVulkanNativeSwapchain) {
        if (sVkEmulation->displayVk) {
            ERR("Reset VkEmulation::displayVk.");
        }
        sVkEmulation->displayVk = std::make_unique<DisplayVk>(
            *sVkEmulation->ivk, sVkEmulation->physdev, sVkEmulation->queueFamilyIndex,
            sVkEmulation->queueFamilyIndex, sVkEmulation->device, sVkEmulation->queue,
            sVkEmulation->queueLock, sVkEmulation->queue, sVkEmulation->queueLock);
    }
}

VkEmulation* getGlobalVkEmulation() {
    if (sVkEmulation && !sVkEmulation->live) return nullptr;
    return sVkEmulation;
}

void teardownGlobalVkEmulation() {
    if (!sVkEmulation) return;

    // Don't try to tear down something that did not set up completely; too risky
    if (!sVkEmulation->live) return;

    sVkEmulation->displayVk.reset();

    freeExternalMemoryLocked(sVkEmulation->dvk, &sVkEmulation->staging.memory);

    sVkEmulation->ivk->vkDestroyDevice(sVkEmulation->device, nullptr);
    sVkEmulation->gvk->vkDestroyInstance(sVkEmulation->instance, nullptr);

    sVkEmulation->live = false;
    delete sVkEmulation;
    sVkEmulation = nullptr;
}

// Precondition: sVkEmulation has valid device support info
bool allocExternalMemory(VulkanDispatch* vk,
                         VkEmulation::ExternalMemoryInfo* info,
                         bool actuallyExternal,
                         Optional<uint64_t> deviceAlignment) {
    VkExportMemoryAllocateInfo exportAi = {
        VK_STRUCTURE_TYPE_EXPORT_MEMORY_ALLOCATE_INFO, 0,
        VK_EXT_MEMORY_HANDLE_TYPE_BIT,
    };

    VkExportMemoryAllocateInfo* exportAiPtr = nullptr;

    if (sVkEmulation->deviceInfo.supportsExternalMemory &&
        actuallyExternal) {
        exportAiPtr = &exportAi;
    }

    info->actualSize = (info->size + 2 * kPageSize - 1) / kPageSize * kPageSize;
    VkMemoryAllocateInfo allocInfo = {
            VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO,
            exportAiPtr,
            info->actualSize,
            info->typeIndex,
    };

    bool memoryAllocated = false;
    std::vector<VkDeviceMemory> allocationAttempts;
    constexpr size_t kMaxAllocationAttempts = 20u;

    while (!memoryAllocated) {
        VkResult allocRes = vk->vkAllocateMemory(
                sVkEmulation->device, &allocInfo, nullptr, &info->memory);

        if (allocRes != VK_SUCCESS) {
            // LOG(VERBOSE) << "allocExternalMemory: failed in vkAllocateMemory: "
            //              << allocRes;
            break;
        }

        if (sVkEmulation->deviceInfo.memProps.memoryTypes[info->typeIndex]
                    .propertyFlags &
            VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT) {
            VkResult mapRes =
                    vk->vkMapMemory(sVkEmulation->device, info->memory, 0,
                                    info->actualSize, 0, &info->mappedPtr);
            if (mapRes != VK_SUCCESS) {
                // LOG(VERBOSE) << "allocExternalMemory: failed in vkMapMemory: "
                //              << mapRes;
                break;
            }
        }

        uint64_t mappedPtrPageOffset =
                reinterpret_cast<uint64_t>(info->mappedPtr) % kPageSize;

        if (  // don't care about alignment (e.g. device-local memory)
                !deviceAlignment.hasValue() ||
                // If device has an alignment requirement larger than current
                // host pointer alignment (i.e. the lowest 1 bit of mappedPtr),
                // the only possible way to make mappedPtr valid is to ensure
                // that it is already aligned to page.
                mappedPtrPageOffset == 0u ||
                // If device has an alignment requirement smaller or equals to
                // current host pointer alignment, clients can set a offset
                // |kPageSize - mappedPtrPageOffset| in vkBindImageMemory to
                // make it aligned to page and compatible with device
                // requirements.
                (kPageSize - mappedPtrPageOffset) % deviceAlignment.value() == 0) {
            // allocation success.
            memoryAllocated = true;
        } else {
            allocationAttempts.push_back(info->memory);

            // LOG(VERBOSE) << "allocExternalMemory: attempt #"
            //              << allocationAttempts.size()
            //              << " failed; deviceAlignment: "
            //              << deviceAlignment.valueOr(0)
            //              << " mappedPtrPageOffset: " << mappedPtrPageOffset;

            if (allocationAttempts.size() >= kMaxAllocationAttempts) {
                // LOG(VERBOSE) << "allocExternalMemory: unable to allocate"
                //              << " memory with CPU mapped ptr aligned to page";
                break;
            }
        }
    }

    // clean up previous failed attempts
    for (const auto& mem : allocationAttempts) {
        vk->vkFreeMemory(sVkEmulation->device, mem, nullptr /* allocator */);
    }
    if (!memoryAllocated) {
        return false;
    }

    if (!sVkEmulation->deviceInfo.supportsExternalMemory ||
        !actuallyExternal) {
        return true;
    }

#ifdef _WIN32
    VkMemoryGetWin32HandleInfoKHR getWin32HandleInfo = {
        VK_STRUCTURE_TYPE_MEMORY_GET_WIN32_HANDLE_INFO_KHR, 0,
        info->memory, VK_EXT_MEMORY_HANDLE_TYPE_BIT,
    };
    VkResult exportRes =
        sVkEmulation->deviceInfo.getMemoryHandleFunc(
            sVkEmulation->device, &getWin32HandleInfo,
            &info->exportedHandle);
#else
    VkMemoryGetFdInfoKHR getFdInfo = {
        VK_STRUCTURE_TYPE_MEMORY_GET_FD_INFO_KHR, 0,
        info->memory, VK_EXT_MEMORY_HANDLE_TYPE_BIT,
    };
    VkResult exportRes =
        sVkEmulation->deviceInfo.getMemoryHandleFunc(
            sVkEmulation->device, &getFdInfo,
            &info->exportedHandle);
#endif

    if (exportRes != VK_SUCCESS) {
        // LOG(VERBOSE) << "allocExternalMemory: Failed to get external memory "
        //                 "native handle: "
        //              << exportRes;
        return false;
    }

    info->actuallyExternal = true;

    return true;
}

void freeExternalMemoryLocked(VulkanDispatch* vk,
                              VkEmulation::ExternalMemoryInfo* info) {
    if (!info->memory)
        return;

    if (sVkEmulation->deviceInfo.memProps.memoryTypes[info->typeIndex]
                .propertyFlags &
        VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT) {
        if (sVkEmulation->occupiedGpas.find(info->gpa) !=
            sVkEmulation->occupiedGpas.end()) {
            sVkEmulation->occupiedGpas.erase(info->gpa);
            get_emugl_vm_operations().unmapUserBackedRam(info->gpa,
                                                         info->sizeToPage);
            info->gpa = 0u;
        }

        vk->vkUnmapMemory(sVkEmulation->device, info->memory);
        info->mappedPtr = nullptr;
        info->pageAlignedHva = nullptr;
    }

    vk->vkFreeMemory(sVkEmulation->device, info->memory, nullptr);

    info->memory = VK_NULL_HANDLE;

    if (info->exportedHandle != VK_EXT_MEMORY_HANDLE_INVALID) {
#ifdef _WIN32
        CloseHandle(info->exportedHandle);
#else
        close(info->exportedHandle);
#endif
        info->exportedHandle = VK_EXT_MEMORY_HANDLE_INVALID;
    }
}

bool importExternalMemory(VulkanDispatch* vk,
                          VkDevice targetDevice,
                          const VkEmulation::ExternalMemoryInfo* info,
                          VkDeviceMemory* out) {
#ifdef _WIN32
    VkImportMemoryWin32HandleInfoKHR importInfo = {
        VK_STRUCTURE_TYPE_IMPORT_MEMORY_WIN32_HANDLE_INFO_KHR, 0,
        VK_EXT_MEMORY_HANDLE_TYPE_BIT,
        info->exportedHandle,
        0,
    };
#else
    VkImportMemoryFdInfoKHR importInfo = {
        VK_STRUCTURE_TYPE_IMPORT_MEMORY_FD_INFO_KHR, 0,
        VK_EXT_MEMORY_HANDLE_TYPE_BIT,
        dupExternalMemory(info->exportedHandle),
    };
#endif
    VkMemoryAllocateInfo allocInfo = {
        VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO,
        &importInfo,
        info->size,
        info->typeIndex,
    };

    VkResult res = vk->vkAllocateMemory(targetDevice, &allocInfo, nullptr, out);

    if (res != VK_SUCCESS) {
        // LOG(ERROR) << "importExternalMemory: Failed with " << res;
        return false;
    }

    return true;
}

bool importExternalMemoryDedicatedImage(
    VulkanDispatch* vk,
    VkDevice targetDevice,
    const VkEmulation::ExternalMemoryInfo* info,
    VkImage image,
    VkDeviceMemory* out) {

    VkMemoryDedicatedAllocateInfo dedicatedInfo = {
        VK_STRUCTURE_TYPE_MEMORY_DEDICATED_ALLOCATE_INFO, 0,
        image,
        VK_NULL_HANDLE,
    };

#ifdef _WIN32
    VkImportMemoryWin32HandleInfoKHR importInfo = {
        VK_STRUCTURE_TYPE_IMPORT_MEMORY_WIN32_HANDLE_INFO_KHR,
        &dedicatedInfo,
        VK_EXT_MEMORY_HANDLE_TYPE_BIT,
        info->exportedHandle,
        0,
    };
#else
    VkImportMemoryFdInfoKHR importInfo = {
        VK_STRUCTURE_TYPE_IMPORT_MEMORY_FD_INFO_KHR,
        &dedicatedInfo,
        VK_EXT_MEMORY_HANDLE_TYPE_BIT,
        info->exportedHandle,
    };
#endif
    VkMemoryAllocateInfo allocInfo = {
        VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO,
        &importInfo,
        info->size,
        info->typeIndex,
    };

    VkResult res = vk->vkAllocateMemory(targetDevice, &allocInfo, nullptr, out);

    if (res != VK_SUCCESS) {
        // LOG(ERROR) << "importExternalMemoryDedicatedImage: Failed with " << res;
        return false;
    }

    return true;
}

static VkFormat glFormat2VkFormat(GLint internalformat) {
    switch (internalformat) {
        case GL_LUMINANCE:
            return VK_FORMAT_R8_UNORM;
        case GL_RGB:
        case GL_RGB8:
            return VK_FORMAT_R8G8B8_UNORM;
        case GL_RGB565:
            return VK_FORMAT_R5G6B5_UNORM_PACK16;
        case GL_RGB16F:
            return VK_FORMAT_R16G16B16_SFLOAT;
        case GL_RGBA:
        case GL_RGBA8:
            return VK_FORMAT_R8G8B8A8_UNORM;
        case GL_RGB5_A1_OES:
            return VK_FORMAT_A1R5G5B5_UNORM_PACK16;
        case GL_RGBA4_OES:
            return VK_FORMAT_R4G4B4A4_UNORM_PACK16;
        case GL_RGB10_A2:
        case GL_UNSIGNED_INT_10_10_10_2_OES:
            return VK_FORMAT_A2R10G10B10_UNORM_PACK32;
        case GL_BGR10_A2_ANGLEX:
            return VK_FORMAT_A2B10G10R10_UNORM_PACK32;
        case GL_RGBA16F:
            return VK_FORMAT_R16G16B16A16_SFLOAT;
        case GL_BGRA_EXT:
        case GL_BGRA8_EXT:
            return VK_FORMAT_B8G8R8A8_UNORM;;
        default:
            return VK_FORMAT_R8G8B8A8_UNORM;
    }
};

bool isColorBufferVulkanCompatible(uint32_t colorBufferHandle) {
    auto fb = FrameBuffer::getFB();

    int width;
    int height;
    GLint internalformat;

    if (!fb->getColorBufferInfo(colorBufferHandle, &width, &height,
                                &internalformat)) {
        return false;
    }

    VkFormat vkFormat = glFormat2VkFormat(internalformat);

    for (const auto& supportInfo : sVkEmulation->imageSupportInfo) {
        if (supportInfo.format == vkFormat && supportInfo.supported) {
            return true;
        }
    }

    return false;
}

static uint32_t lastGoodTypeIndex(uint32_t indices) {
    for (int32_t i = 31; i >= 0; --i) {
        if (indices & (1 << i)) {
            return i;
        }
    }
    return 0;
}

static uint32_t lastGoodTypeIndexWithMemoryProperties(
        uint32_t indices,
        VkMemoryPropertyFlags memoryProperty) {
    for (int32_t i = 31; i >= 0; --i) {
        if ((indices & (1u << i)) &&
            (!memoryProperty ||
             (sVkEmulation->deviceInfo.memProps.memoryTypes[i].propertyFlags &
              memoryProperty))) {
            return i;
        }
    }
    return 0;
}

// pNext, sharingMode, queueFamilyIndexCount, pQueueFamilyIndices, and initialLayout won't be
// filled.
static std::unique_ptr<VkImageCreateInfo> generateColorBufferVkImageCreateInfo_locked(
    VkFormat format, uint32_t width, uint32_t height, VkImageTiling tiling) {
    const VkFormatProperties* maybeFormatProperties = nullptr;
    for (const auto& supportInfo : sVkEmulation->imageSupportInfo) {
        if (supportInfo.format == format && supportInfo.supported) {
            maybeFormatProperties = &supportInfo.formatProps2.formatProperties;
            break;
        }
    }
    if (!maybeFormatProperties) {
        ERR("Format %s is not supported.", string_VkFormat(format));
        return nullptr;
    }
    const VkFormatProperties& formatProperties = *maybeFormatProperties;

    constexpr std::pair<VkFormatFeatureFlags, VkImageUsageFlags> formatUsagePairs[] = {
        {VK_FORMAT_FEATURE_COLOR_ATTACHMENT_BIT,
            VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT|VK_IMAGE_USAGE_INPUT_ATTACHMENT_BIT},
        {VK_FORMAT_FEATURE_SAMPLED_IMAGE_BIT,
            VK_IMAGE_USAGE_SAMPLED_BIT},
        {VK_FORMAT_FEATURE_TRANSFER_SRC_BIT,
            VK_IMAGE_USAGE_TRANSFER_SRC_BIT},
        {VK_FORMAT_FEATURE_TRANSFER_DST_BIT,
            VK_IMAGE_USAGE_TRANSFER_DST_BIT},
        {VK_FORMAT_FEATURE_BLIT_SRC_BIT,
            VK_IMAGE_USAGE_TRANSFER_SRC_BIT},
    };
    VkFormatFeatureFlags tilingFeatures = (tiling == VK_IMAGE_TILING_OPTIMAL)
                                              ? formatProperties.optimalTilingFeatures
                                              : formatProperties.linearTilingFeatures;

    VkImageUsageFlags usage = 0;
    for (const auto& formatUsage : formatUsagePairs) {
        usage |= (tilingFeatures & formatUsage.first) ? formatUsage.second : 0u;
    }

    return std::make_unique<VkImageCreateInfo>(VkImageCreateInfo{
        .sType = VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO,
        // The caller is responsible to fill pNext.
        .pNext = nullptr,
        .flags = 0,
        .imageType = VK_IMAGE_TYPE_2D,
        .format = format,
        .extent =
            {
                .width = width,
                .height = height,
                .depth = 1,
            },
        .mipLevels = 1,
        .arrayLayers = 1,
        .samples = VK_SAMPLE_COUNT_1_BIT,
        .tiling = tiling,
        .usage = usage,
        // The caller is responsible to fill sharingMode.
        .sharingMode = VK_SHARING_MODE_MAX_ENUM,
        // The caller is responsible to fill queueFamilyIndexCount.
        .queueFamilyIndexCount = 0,
        // The caller is responsible to fill pQueueFamilyIndices.
        .pQueueFamilyIndices = nullptr,
        // The caller is responsible to fill initialLayout.
        .initialLayout = VK_IMAGE_LAYOUT_MAX_ENUM,
    });
}

std::unique_ptr<VkImageCreateInfo> generateColorBufferVkImageCreateInfo(VkFormat format,
                                                                        uint32_t width,
                                                                        uint32_t height,
                                                                        VkImageTiling tiling) {
    if (!sVkEmulation || !sVkEmulation->live) {
        GFXSTREAM_ABORT(FatalError(ABORT_REASON_OTHER)) << "Host Vulkan device lost";
    }
    AutoLock lock(sVkEmulationLock);
    return generateColorBufferVkImageCreateInfo_locked(format, width, height, tiling);
}

// TODO(liyl): Currently we can only specify required memoryProperty
// for a color buffer.
//
// Ideally we would like to specify a memory type index directly from
// localAllocInfo.memoryTypeIndex when allocating color buffers in
// vkAllocateMemory(). But this type index mechanism breaks "Modify the
// allocation size and type index to suit the resulting image memory
// size." which seems to be needed to keep the Android/Fuchsia guest
// memory type index consistent across guest allocations, and without
// which those guests might end up import allocating from a color buffer
// with mismatched type indices.
//
// We should make it so the guest can only allocate external images/
// buffers of one type index for image and one type index for buffer
// to begin with, via filtering from the host.
bool setupVkColorBuffer(uint32_t colorBufferHandle,
                        bool vulkanOnly,
                        uint32_t memoryProperty,
                        bool* exported,
                        VkDeviceSize* allocSize,
                        uint32_t* typeIndex,
                        void** mappedPtr) {
    if (!isColorBufferVulkanCompatible(colorBufferHandle)) return false;

    auto vk = sVkEmulation->dvk;

    auto fb = FrameBuffer::getFB();

    int width;
    int height;
    GLint internalformat;
    FrameworkFormat frameworkFormat;

    if (!fb->getColorBufferInfo(colorBufferHandle, &width, &height,
                                &internalformat, &frameworkFormat)) {
        return false;
    }

    AutoLock lock(sVkEmulationLock);

    auto infoPtr = android::base::find(sVkEmulation->colorBuffers, colorBufferHandle);

    // Already setup
    if (infoPtr) {
        // Setting exported is required for on_vkCreateImage backed by
        // an AHardwareBuffer.
        if (exported) *exported = infoPtr->glExported;
        // Update the allocation size to what the host driver wanted, or we
        // might get VK_ERROR_OUT_OF_DEVICE_MEMORY and a host crash
        if (allocSize) *allocSize = infoPtr->memory.size;
        // Update the type index to what the host driver wanted, or we might
        // get VK_ERROR_DEVICE_LOST
        if (typeIndex) *typeIndex = infoPtr->memory.typeIndex;
        // Update the mappedPtr to what the host driver wanted, otherwise we
        // may map the same memory twice.
        if (mappedPtr)
            *mappedPtr = infoPtr->memory.mappedPtr;
        return true;
    }

    VkFormat vkFormat;
    bool glCompatible = (frameworkFormat == FRAMEWORK_FORMAT_GL_COMPATIBLE);
    switch (frameworkFormat) {
        case FrameworkFormat::FRAMEWORK_FORMAT_GL_COMPATIBLE:
            vkFormat = glFormat2VkFormat(internalformat);
            break;
        case FrameworkFormat::FRAMEWORK_FORMAT_NV12:
            vkFormat = VK_FORMAT_G8_B8R8_2PLANE_420_UNORM;
            break;
        case FrameworkFormat::FRAMEWORK_FORMAT_YV12:
        case FrameworkFormat::FRAMEWORK_FORMAT_YUV_420_888:
            vkFormat = VK_FORMAT_G8_B8_R8_3PLANE_420_UNORM;
            break;
        default:
            vkFormat = glFormat2VkFormat(internalformat);
            fprintf(stderr, "WARNING: unsupported framework format %d\n", frameworkFormat);
            break;
    }

    VkEmulation::ColorBufferInfo res;

    res.handle = colorBufferHandle;

    // TODO
    res.frameworkFormat = frameworkFormat;
    res.frameworkStride = 0;

    VkImageTiling tiling = (memoryProperty & VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT)
                               ? VK_IMAGE_TILING_LINEAR
                               : VK_IMAGE_TILING_OPTIMAL;
    std::unique_ptr<VkImageCreateInfo> imageCi =
        generateColorBufferVkImageCreateInfo_locked(vkFormat, width, height, tiling);
    // pNext will be filled later.
    imageCi->sharingMode = VK_SHARING_MODE_EXCLUSIVE;
    imageCi->queueFamilyIndexCount = 0;
    imageCi->pQueueFamilyIndices = nullptr;
    imageCi->initialLayout = VK_IMAGE_LAYOUT_UNDEFINED;

    res.extent = imageCi->extent;
    res.format = imageCi->format;
    res.type = imageCi->imageType;
    res.tiling = imageCi->tiling;
    res.usageFlags = imageCi->usage;
    res.createFlags = imageCi->flags;
    res.sharingMode = imageCi->sharingMode;

    // Create the image. If external memory is supported, make it external.
    VkExternalMemoryImageCreateInfo extImageCi = {
        VK_STRUCTURE_TYPE_EXTERNAL_MEMORY_IMAGE_CREATE_INFO, 0,
        VK_EXT_MEMORY_HANDLE_TYPE_BIT,
    };

    VkExternalMemoryImageCreateInfo* extImageCiPtr = nullptr;

    if (sVkEmulation->deviceInfo.supportsExternalMemory) {
        extImageCiPtr = &extImageCi;
    }

    imageCi->pNext = extImageCiPtr;

    VkResult createRes =
        vk->vkCreateImage(sVkEmulation->device, imageCi.get(), nullptr, &res.image);
    if (createRes != VK_SUCCESS) {
        // LOG(VERBOSE) << "Failed to create Vulkan image for ColorBuffer "
        //              << colorBufferHandle;
        return false;
    }

    vk->vkGetImageMemoryRequirements(sVkEmulation->device, res.image,
                                     &res.memReqs);

    // Currently we only care about two memory properties: DEVICE_LOCAL
    // and HOST_VISIBLE; other memory properties specified in
    // rcSetColorBufferVulkanMode2() call will be ignored for now.
    memoryProperty = memoryProperty & (VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT |
                                       VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT);

    res.memory.size = res.memReqs.size;

    // Determine memory type.
    if (memoryProperty) {
        res.memory.typeIndex = lastGoodTypeIndexWithMemoryProperties(
                res.memReqs.memoryTypeBits, memoryProperty);
    } else {
        res.memory.typeIndex = lastGoodTypeIndex(res.memReqs.memoryTypeBits);
    }

    // LOG(VERBOSE) << "ColorBuffer " << colorBufferHandle
    //              << ", allocation size and type index: " << res.memory.size
    //              << ", " << res.memory.typeIndex
    //              << ", allocated memory property: "
    //              << sVkEmulation->deviceInfo.memProps
    //                         .memoryTypes[res.memory.typeIndex]
    //                         .propertyFlags
    //              << ", requested memory property: " << memoryProperty;

    bool isHostVisible = memoryProperty & VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT;
    Optional<uint64_t> deviceAlignment =
            isHostVisible ? Optional<uint64_t>(res.memReqs.alignment) : kNullopt;
    bool allocRes = allocExternalMemory(
            vk, &res.memory, true /*actuallyExternal*/, deviceAlignment);

    if (!allocRes) {
        // LOG(VERBOSE) << "Failed to allocate ColorBuffer with Vulkan backing.";
        return false;
    }

    res.memory.pageOffset =
            reinterpret_cast<uint64_t>(res.memory.mappedPtr) % kPageSize;
    res.memory.bindOffset =
            res.memory.pageOffset ? kPageSize - res.memory.pageOffset : 0u;

    VkResult bindImageMemoryRes =
            vk->vkBindImageMemory(sVkEmulation->device, res.image,
                                  res.memory.memory, res.memory.bindOffset);

    if (bindImageMemoryRes != VK_SUCCESS) {
        fprintf(stderr, "%s: Failed to bind image memory. %d\n", __func__,
        bindImageMemoryRes);
        return false;
    }

    if (sVkEmulation->instanceSupportsMoltenVK) {
        sVkEmulation->getMTLTextureFunc(res.image, &res.mtlTexture);
        if (!res.mtlTexture) {
            fprintf(stderr, "%s: Failed to get MTLTexture.\n", __func__);
        }

#ifdef __APPLE__
        CFRetain(res.mtlTexture);
#endif
    }

    if (sVkEmulation->deviceInfo.supportsExternalMemory &&
        glCompatible &&
        FrameBuffer::getFB()->importMemoryToColorBuffer(
            dupExternalMemory(res.memory.exportedHandle), res.memory.size, false /* dedicated */,
            vulkanOnly, colorBufferHandle, res.image, *imageCi)) {
        res.glExported = true;
    }

    if (exported) *exported = res.glExported;
    if (allocSize) *allocSize = res.memory.size;
    if (typeIndex) *typeIndex = res.memory.typeIndex;
    if (mappedPtr)
        *mappedPtr = res.memory.mappedPtr;

    res.ownedByHost = std::make_shared<std::atomic_bool>(true);

    sVkEmulation->colorBuffers[colorBufferHandle] = res;
    return true;
}

bool teardownVkColorBuffer(uint32_t colorBufferHandle) {
    if (!sVkEmulation || !sVkEmulation->live) return false;

    auto vk = sVkEmulation->dvk;

    AutoLock lock(sVkEmulationLock);

    auto infoPtr = android::base::find(sVkEmulation->colorBuffers, colorBufferHandle);

    if (!infoPtr) return false;

    auto& info = *infoPtr;
    {
        android::base::AutoLock lock(*sVkEmulation->queueLock);
        VK_CHECK(vk->vkQueueWaitIdle(sVkEmulation->queue));
    }
    vk->vkDestroyImage(sVkEmulation->device, info.image, nullptr);
    freeExternalMemoryLocked(vk, &info.memory);

#ifdef __APPLE__
    if (info.mtlTexture) {
        CFRelease(info.mtlTexture);
    }
#endif

    sVkEmulation->colorBuffers.erase(colorBufferHandle);

    return true;
}

VkEmulation::ColorBufferInfo getColorBufferInfo(uint32_t colorBufferHandle) {
    VkEmulation::ColorBufferInfo res;

    AutoLock lock(sVkEmulationLock);

    auto infoPtr = android::base::find(sVkEmulation->colorBuffers, colorBufferHandle);

    if (!infoPtr) return res;

    res = *infoPtr;
    return res;
}

bool updateColorBufferFromVkImage(uint32_t colorBufferHandle) {
    if (!sVkEmulation || !sVkEmulation->live) return false;

    auto vk = sVkEmulation->dvk;

    AutoLock lock(sVkEmulationLock);

    auto infoPtr = android::base::find(sVkEmulation->colorBuffers, colorBufferHandle);

    if (!infoPtr) {
        // Color buffer not found; this is usually OK.
        return false;
    }

    if (!infoPtr->image) {
        fprintf(stderr, "%s: error: ColorBuffer 0x%x has no VkImage\n", __func__, colorBufferHandle);
        return false;
    }

    if (infoPtr->glExported ||
        (infoPtr->vulkanMode == VkEmulation::VulkanMode::VulkanOnly) ||
        infoPtr->frameworkFormat != FrameworkFormat::FRAMEWORK_FORMAT_GL_COMPATIBLE) {
        // No sync needed if exported to GL or in Vulkan-only mode
        return true;
    }

    // Record our synchronization commands.
    VkCommandBufferBeginInfo beginInfo = {
        VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO, 0,
        VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT,
        nullptr /* no inheritance info */,
    };

    vk->vkBeginCommandBuffer(
        sVkEmulation->commandBuffer,
        &beginInfo);

    // From the spec: If an application does not need the contents of a resource
    // to remain valid when transferring from one queue family to another, then
    // the ownership transfer should be skipped.

    // We definitely need to transition the image to
    // VK_TRANSFER_SRC_OPTIMAL and back.

    VkImageMemoryBarrier presentToTransferSrc = {
        VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER, 0,
        0,
        VK_ACCESS_HOST_READ_BIT,
        infoPtr->currentLayout,
        VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
        VK_QUEUE_FAMILY_IGNORED,
        VK_QUEUE_FAMILY_IGNORED,
        infoPtr->image,
        {
            VK_IMAGE_ASPECT_COLOR_BIT,
            0, 1, 0, 1,
        },
    };

    vk->vkCmdPipelineBarrier(
        sVkEmulation->commandBuffer,
        VK_PIPELINE_STAGE_ALL_COMMANDS_BIT,
        VK_PIPELINE_STAGE_ALL_COMMANDS_BIT,
        0,
        0, nullptr,
        0, nullptr,
        1, &presentToTransferSrc);

    infoPtr->currentLayout = VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL;

    // Copy to staging buffer
    uint32_t bpp = 4; /* format always rgba8...not */
    switch (infoPtr->format) {
        case VK_FORMAT_R5G6B5_UNORM_PACK16:
            bpp = 2;
            break;
        case VK_FORMAT_R8G8B8_UNORM:
            bpp = 3;
            break;
        default:
        case VK_FORMAT_R8G8B8A8_UNORM:
            bpp = 4;
            break;
    }
    VkBufferImageCopy region = {
        0 /* buffer offset */,
        infoPtr->extent.width,
        infoPtr->extent.height,
        {
            VK_IMAGE_ASPECT_COLOR_BIT,
            0, 0, 1,
        },
        { 0, 0, 0 },
        infoPtr->extent,
    };

    vk->vkCmdCopyImageToBuffer(
        sVkEmulation->commandBuffer,
        infoPtr->image,
        VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
        sVkEmulation->staging.buffer,
        1, &region);

    vk->vkEndCommandBuffer(sVkEmulation->commandBuffer);

    VkSubmitInfo submitInfo = {
        VK_STRUCTURE_TYPE_SUBMIT_INFO, 0,
        0, nullptr,
        nullptr,
        1, &sVkEmulation->commandBuffer,
        0, nullptr,
    };

    {
        android::base::AutoLock lock(*sVkEmulation->queueLock);
        vk->vkQueueSubmit(sVkEmulation->queue, 1, &submitInfo,
                          sVkEmulation->commandBufferFence);
    }

    static constexpr uint64_t ANB_MAX_WAIT_NS =
        5ULL * 1000ULL * 1000ULL * 1000ULL;

    vk->vkWaitForFences(
        sVkEmulation->device, 1, &sVkEmulation->commandBufferFence,
        VK_TRUE, ANB_MAX_WAIT_NS);
    vk->vkResetFences(
        sVkEmulation->device, 1, &sVkEmulation->commandBufferFence);

    VkMappedMemoryRange toInvalidate = {
        VK_STRUCTURE_TYPE_MAPPED_MEMORY_RANGE, 0,
        sVkEmulation->staging.memory.memory,
        0, VK_WHOLE_SIZE,
    };

    vk->vkInvalidateMappedMemoryRanges(
        sVkEmulation->device, 1, &toInvalidate);

    FrameBuffer::getFB()->
        replaceColorBufferContents(
            colorBufferHandle,
            sVkEmulation->staging.memory.mappedPtr,
            bpp * infoPtr->extent.width * infoPtr->extent.height);

    return true;
}

bool updateVkImageFromColorBuffer(uint32_t colorBufferHandle) {
    if (!sVkEmulation || !sVkEmulation->live) return false;

    auto vk = sVkEmulation->dvk;

    AutoLock lock(sVkEmulationLock);

    auto infoPtr = android::base::find(sVkEmulation->colorBuffers, colorBufferHandle);

    if (!infoPtr) {
        // Color buffer not found; this is usually OK.
        return false;
    }

    if (infoPtr->frameworkFormat == FrameworkFormat::FRAMEWORK_FORMAT_GL_COMPATIBLE && (
        infoPtr->glExported ||
        infoPtr->vulkanMode == VkEmulation::VulkanMode::VulkanOnly)) {
        // No sync needed if exported to GL or in Vulkan-only mode
        return true;
    }

    size_t cbNumBytes = 0;
    bool readRes = FrameBuffer::getFB()->
        readColorBufferContents(
            colorBufferHandle, &cbNumBytes, nullptr);
    if (!readRes) {
        fprintf(stderr, "%s: Failed to read color buffer 0x%x\n",
                __func__, colorBufferHandle);
        return false;
    }

    if (cbNumBytes > sVkEmulation->staging.memory.size) {
        fprintf(stderr,
            "%s: Not enough space to read to staging buffer. "
            "Wanted: 0x%llx Have: 0x%llx\n", __func__,
            (unsigned long long)cbNumBytes,
            (unsigned long long)(sVkEmulation->staging.memory.size));
        return false;
    }

    readRes = FrameBuffer::getFB()->
        readColorBufferContents(
            colorBufferHandle, &cbNumBytes,
            sVkEmulation->staging.memory.mappedPtr);

    if (!readRes) {
        fprintf(stderr, "%s: Failed to read color buffer 0x%x (at glReadPixels)\n",
                __func__, colorBufferHandle);
        return false;
    }

    // Record our synchronization commands.
    VkCommandBufferBeginInfo beginInfo = {
        VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO, 0,
        VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT,
        nullptr /* no inheritance info */,
    };

    vk->vkBeginCommandBuffer(
        sVkEmulation->commandBuffer,
        &beginInfo);

    // From the spec: If an application does not need the contents of a resource
    // to remain valid when transferring from one queue family to another, then
    // the ownership transfer should be skipped.

    // We definitely need to transition the image to
    // VK_TRANSFER_SRC_OPTIMAL and back.

    VkImageMemoryBarrier presentToTransferSrc = {
        VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER, 0,
        0,
        VK_ACCESS_MEMORY_READ_BIT | VK_ACCESS_MEMORY_WRITE_BIT,
        infoPtr->currentLayout,
        VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
        VK_QUEUE_FAMILY_IGNORED,
        VK_QUEUE_FAMILY_IGNORED,
        infoPtr->image,
        {
            VK_IMAGE_ASPECT_COLOR_BIT,
            0, 1, 0, 1,
        },
    };

    infoPtr->currentLayout =
        VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL;

    vk->vkCmdPipelineBarrier(
        sVkEmulation->commandBuffer,
        VK_PIPELINE_STAGE_ALL_COMMANDS_BIT,
        VK_PIPELINE_STAGE_ALL_COMMANDS_BIT,
        0,
        0, nullptr,
        0, nullptr,
        1, &presentToTransferSrc);

    // Copy to staging buffer
    std::vector<VkBufferImageCopy> regions;
    if (infoPtr->frameworkFormat == FrameworkFormat::FRAMEWORK_FORMAT_GL_COMPATIBLE) {
        regions.push_back({
            0 /* buffer offset */,
            infoPtr->extent.width,
            infoPtr->extent.height,
            {
                VK_IMAGE_ASPECT_COLOR_BIT,
                0, 0, 1,
            },
            { 0, 0, 0 },
            infoPtr->extent,
        });
    } else {
        // YUV formats
        bool swapUV = infoPtr->frameworkFormat == FRAMEWORK_FORMAT_YV12;
        VkExtent3D subplaneExtent = {
            infoPtr->extent.width / 2,
            infoPtr->extent.height / 2,
            1
        };
        regions.push_back({
            0 /* buffer offset */,
            infoPtr->extent.width,
            infoPtr->extent.height,
            {
                VK_IMAGE_ASPECT_PLANE_0_BIT,
                0, 0, 1,
            },
            { 0, 0, 0 },
            infoPtr->extent,
        });
        regions.push_back({
            infoPtr->extent.width * infoPtr->extent.height /* buffer offset */,
            subplaneExtent.width,
            subplaneExtent.height,
            {
                (VkImageAspectFlags)(swapUV ? VK_IMAGE_ASPECT_PLANE_2_BIT : VK_IMAGE_ASPECT_PLANE_1_BIT),
                0, 0, 1,
            },
            { 0, 0, 0 },
            subplaneExtent,
        });
        if (infoPtr->frameworkFormat == FRAMEWORK_FORMAT_YUV_420_888
            || infoPtr->frameworkFormat == FRAMEWORK_FORMAT_YV12) {
            regions.push_back({
                infoPtr->extent.width * infoPtr->extent.height
                    + subplaneExtent.width * subplaneExtent.height,
                subplaneExtent.width,
                subplaneExtent.height,
                {
                   (VkImageAspectFlags)(swapUV ? VK_IMAGE_ASPECT_PLANE_1_BIT : VK_IMAGE_ASPECT_PLANE_2_BIT),
                    0, 0, 1,
                },
                { 0, 0, 0 },
                subplaneExtent,
            });
        }
    }

    vk->vkCmdCopyBufferToImage(
        sVkEmulation->commandBuffer,
        sVkEmulation->staging.buffer,
        infoPtr->image,
        VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
        regions.size(), regions.data());

    vk->vkEndCommandBuffer(sVkEmulation->commandBuffer);

    VkSubmitInfo submitInfo = {
        VK_STRUCTURE_TYPE_SUBMIT_INFO, 0,
        0, nullptr,
        nullptr,
        1, &sVkEmulation->commandBuffer,
        0, nullptr,
    };

    {
        android::base::AutoLock lock(*sVkEmulation->queueLock);
        vk->vkQueueSubmit(sVkEmulation->queue, 1, &submitInfo,
                          sVkEmulation->commandBufferFence);
    }

    static constexpr uint64_t ANB_MAX_WAIT_NS =
        5ULL * 1000ULL * 1000ULL * 1000ULL;

    vk->vkWaitForFences(
        sVkEmulation->device, 1, &sVkEmulation->commandBufferFence,
        VK_TRUE, ANB_MAX_WAIT_NS);
    vk->vkResetFences(
        sVkEmulation->device, 1, &sVkEmulation->commandBufferFence);

    VkMappedMemoryRange toInvalidate = {
        VK_STRUCTURE_TYPE_MAPPED_MEMORY_RANGE, 0,
        sVkEmulation->staging.memory.memory,
        0, VK_WHOLE_SIZE,
    };

    vk->vkInvalidateMappedMemoryRanges(
        sVkEmulation->device, 1, &toInvalidate);
    return true;
}

VK_EXT_MEMORY_HANDLE getColorBufferExtMemoryHandle(uint32_t colorBuffer) {
    if (!sVkEmulation || !sVkEmulation->live) return VK_EXT_MEMORY_HANDLE_INVALID;

    AutoLock lock(sVkEmulationLock);

    auto infoPtr = android::base::find(sVkEmulation->colorBuffers, colorBuffer);

    if (!infoPtr) {
        // Color buffer not found; this is usually OK.
        return VK_EXT_MEMORY_HANDLE_INVALID;
    }

    return infoPtr->memory.exportedHandle;
}

bool setColorBufferVulkanMode(uint32_t colorBuffer, uint32_t vulkanMode) {
    if (!sVkEmulation || !sVkEmulation->live) return VK_EXT_MEMORY_HANDLE_INVALID;

    AutoLock lock(sVkEmulationLock);

    auto infoPtr = android::base::find(sVkEmulation->colorBuffers, colorBuffer);

    if (!infoPtr) {
        return false;
    }

    infoPtr->vulkanMode = static_cast<VkEmulation::VulkanMode>(vulkanMode);

    return true;
}

MTLTextureRef getColorBufferMTLTexture(uint32_t colorBuffer) {
    if (!sVkEmulation || !sVkEmulation->live) return nullptr;

    AutoLock lock(sVkEmulationLock);

    auto infoPtr = android::base::find(sVkEmulation->colorBuffers, colorBuffer);

    if (!infoPtr) {
        // Color buffer not found; this is usually OK.
        return nullptr;
    }

#ifdef __APPLE__
    CFRetain(infoPtr->mtlTexture);
#endif
    return infoPtr->mtlTexture;
}

int32_t mapGpaToBufferHandle(uint32_t bufferHandle,
                             uint64_t gpa,
                             uint64_t size) {
    if (!sVkEmulation || !sVkEmulation->live)
        return VK_ERROR_DEVICE_LOST;

    AutoLock lock(sVkEmulationLock);

    VkEmulation::ExternalMemoryInfo* memoryInfoPtr = nullptr;

    auto colorBufferInfoPtr =
            android::base::find(sVkEmulation->colorBuffers, bufferHandle);
    if (colorBufferInfoPtr) {
        memoryInfoPtr = &colorBufferInfoPtr->memory;
    }
    auto bufferInfoPtr =
            android::base::find(sVkEmulation->buffers, bufferHandle);
    if (bufferInfoPtr) {
        memoryInfoPtr = &bufferInfoPtr->memory;
    }

    if (!memoryInfoPtr) {
        return VK_ERROR_INVALID_EXTERNAL_HANDLE;
    }

    // memory should be already mapped to host.
    if (!memoryInfoPtr->mappedPtr) {
        return VK_ERROR_MEMORY_MAP_FAILED;
    }

    memoryInfoPtr->gpa = gpa;
    memoryInfoPtr->pageAlignedHva =
            reinterpret_cast<uint8_t*>(memoryInfoPtr->mappedPtr) +
            memoryInfoPtr->bindOffset;

    size_t rawSize = memoryInfoPtr->size + memoryInfoPtr->pageOffset;
    if (size && size < rawSize) {
        rawSize = size;
    }

    memoryInfoPtr->sizeToPage = ((rawSize + kPageSize - 1) >> kPageBits)
                                << kPageBits;

    // LOG(VERBOSE) << "mapGpaToColorBuffer: hva = " << memoryInfoPtr->mappedPtr
    //              << ", pageAlignedHva = " << memoryInfoPtr->pageAlignedHva
    //              << " -> [ " << memoryInfoPtr->gpa << ", "
    //              << memoryInfoPtr->gpa + memoryInfoPtr->sizeToPage << " ]";

    if (sVkEmulation->occupiedGpas.find(gpa) !=
        sVkEmulation->occupiedGpas.end()) {
        // emugl::emugl_crash_reporter("FATAL: already mapped gpa 0x%lx! ", gpa);
        return VK_ERROR_MEMORY_MAP_FAILED;
    }

    get_emugl_vm_operations().mapUserBackedRam(
            gpa, memoryInfoPtr->pageAlignedHva, memoryInfoPtr->sizeToPage);

    sVkEmulation->occupiedGpas.insert(gpa);

    return memoryInfoPtr->pageOffset;
}

bool setupVkBuffer(uint32_t bufferHandle,
                   bool vulkanOnly,
                   uint32_t memoryProperty,
                   bool* exported,
                   VkDeviceSize* allocSize,
                   uint32_t* typeIndex) {
    if (vulkanOnly == false) {
        fprintf(stderr, "Data buffers should be vulkanOnly. Setup failed.\n");
        return false;
    }

    auto vk = sVkEmulation->dvk;
    auto fb = FrameBuffer::getFB();

    int size;
    if (!fb->getBufferInfo(bufferHandle, &size)) {
        return false;
    }

    AutoLock lock(sVkEmulationLock);

    auto infoPtr = android::base::find(sVkEmulation->buffers, bufferHandle);

    // Already setup
    if (infoPtr) {
        // Update the allocation size to what the host driver wanted, or we
        // might get VK_ERROR_OUT_OF_DEVICE_MEMORY and a host crash
        if (allocSize)
            *allocSize = infoPtr->memory.size;
        // Update the type index to what the host driver wanted, or we might
        // get VK_ERROR_DEVICE_LOST
        if (typeIndex)
            *typeIndex = infoPtr->memory.typeIndex;
        return true;
    }

    VkEmulation::BufferInfo res;

    res.handle = bufferHandle;

    res.size = size;
    res.usageFlags = VK_BUFFER_USAGE_INDEX_BUFFER_BIT |
                     VK_BUFFER_USAGE_VERTEX_BUFFER_BIT |
                     VK_BUFFER_USAGE_STORAGE_BUFFER_BIT |
                     VK_BUFFER_USAGE_STORAGE_TEXEL_BUFFER_BIT |
                     VK_BUFFER_USAGE_TRANSFER_SRC_BIT |
                     VK_BUFFER_USAGE_TRANSFER_DST_BIT;
    res.createFlags = 0;

    res.sharingMode = VK_SHARING_MODE_EXCLUSIVE;

    // Create the image. If external memory is supported, make it external.
    VkExternalMemoryBufferCreateInfo extBufferCi = {
            VK_STRUCTURE_TYPE_EXTERNAL_MEMORY_BUFFER_CREATE_INFO,
            0,
            VK_EXT_MEMORY_HANDLE_TYPE_BIT,
    };

    VkExternalMemoryBufferCreateInfo* extBufferCiPtr = nullptr;
    if (sVkEmulation->deviceInfo.supportsExternalMemory) {
        extBufferCiPtr = &extBufferCi;
    }

    VkBufferCreateInfo bufferCi = {
            VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO,
            extBufferCiPtr,
            res.createFlags,
            res.size,
            res.usageFlags,
            res.sharingMode,
            /* queueFamilyIndexCount */ 0,
            /* pQueueFamilyIndices */ nullptr,
    };

    VkResult createRes = vk->vkCreateBuffer(sVkEmulation->device, &bufferCi,
                                            nullptr, &res.buffer);

    if (createRes != VK_SUCCESS) {
        // LOG(VERBOSE) << "Failed to create Vulkan Buffer for Buffer "
                     // << bufferHandle;
        return false;
    }

    vk->vkGetBufferMemoryRequirements(sVkEmulation->device, res.buffer,
                                      &res.memReqs);

    // Currently we only care about two memory properties: DEVICE_LOCAL
    // and HOST_VISIBLE; other memory properties specified in
    // rcSetColorBufferVulkanMode2() call will be ignored for now.
    memoryProperty = memoryProperty & (VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT |
                                       VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT);

    res.memory.size = res.memReqs.size;

    // Determine memory type.
    if (memoryProperty) {
        res.memory.typeIndex = lastGoodTypeIndexWithMemoryProperties(
                res.memReqs.memoryTypeBits, memoryProperty);
    } else {
        res.memory.typeIndex = lastGoodTypeIndex(res.memReqs.memoryTypeBits);
    }

    // LOG(VERBOSE) << "Buffer " << bufferHandle
    //              << "allocation size and type index: " << res.memory.size
    //              << ", " << res.memory.typeIndex
    //              << ", allocated memory property: "
    //              << sVkEmulation->deviceInfo.memProps
    //                         .memoryTypes[res.memory.typeIndex]
    //                         .propertyFlags
    //              << ", requested memory property: " << memoryProperty;

    bool isHostVisible = memoryProperty & VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT;
    Optional<uint64_t> deviceAlignment =
            isHostVisible ? Optional<uint64_t>(res.memReqs.alignment) : kNullopt;
    bool allocRes = allocExternalMemory(
            vk, &res.memory, true /* actuallyExternal */, deviceAlignment);

    if (!allocRes) {
        // LOG(VERBOSE) << "Failed to allocate ColorBuffer with Vulkan backing.";
    }

    res.memory.pageOffset =
            reinterpret_cast<uint64_t>(res.memory.mappedPtr) % kPageSize;
    res.memory.bindOffset =
            res.memory.pageOffset ? kPageSize - res.memory.pageOffset : 0u;

    VkResult bindBufferMemoryRes = vk->vkBindBufferMemory(
            sVkEmulation->device, res.buffer, res.memory.memory, 0);

    if (bindBufferMemoryRes != VK_SUCCESS) {
        fprintf(stderr, "%s: Failed to bind buffer memory. %d\n", __func__,
                bindBufferMemoryRes);
        return bindBufferMemoryRes;
    }

    bool isHostVisibleMemory =
            memoryProperty & VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT;

    if (isHostVisibleMemory) {
        VkResult mapMemoryRes =
                vk->vkMapMemory(sVkEmulation->device, res.memory.memory, 0,
                                res.memory.size, {}, &res.memory.mappedPtr);

        if (mapMemoryRes != VK_SUCCESS) {
            fprintf(stderr, "%s: Failed to map image memory. %d\n", __func__,
                    mapMemoryRes);
            return false;
        }
    }

    res.glExported = false;
    if (exported)
        *exported = res.glExported;
    if (allocSize)
        *allocSize = res.memory.size;
    if (typeIndex)
        *typeIndex = res.memory.typeIndex;

    sVkEmulation->buffers[bufferHandle] = res;
    return allocRes;
}

bool teardownVkBuffer(uint32_t bufferHandle) {
    if (!sVkEmulation || !sVkEmulation->live)
        return false;

    auto vk = sVkEmulation->dvk;
    AutoLock lock(sVkEmulationLock);

    auto infoPtr = android::base::find(sVkEmulation->buffers, bufferHandle);
    if (!infoPtr)
        return false;
    {
        android::base::AutoLock lock(*sVkEmulation->queueLock);
        VK_CHECK(vk->vkQueueWaitIdle(sVkEmulation->queue));
    }
    auto& info = *infoPtr;

    vk->vkDestroyBuffer(sVkEmulation->device, info.buffer, nullptr);
    freeExternalMemoryLocked(vk, &info.memory);
    sVkEmulation->buffers.erase(bufferHandle);

    return true;
}

VK_EXT_MEMORY_HANDLE getBufferExtMemoryHandle(uint32_t bufferHandle) {
    if (!sVkEmulation || !sVkEmulation->live)
        return VK_EXT_MEMORY_HANDLE_INVALID;

    AutoLock lock(sVkEmulationLock);

    auto infoPtr = android::base::find(sVkEmulation->buffers, bufferHandle);
    if (!infoPtr) {
        // Color buffer not found; this is usually OK.
        return VK_EXT_MEMORY_HANDLE_INVALID;
    }

    return infoPtr->memory.exportedHandle;
}

VkExternalMemoryHandleTypeFlags
transformExternalMemoryHandleTypeFlags_tohost(
    VkExternalMemoryHandleTypeFlags bits) {

    VkExternalMemoryHandleTypeFlags res = bits;

    // Transform Android/Fuchsia/Linux bits to host bits.
    if (bits & VK_EXTERNAL_MEMORY_HANDLE_TYPE_OPAQUE_FD_BIT) {
        res &= ~VK_EXTERNAL_MEMORY_HANDLE_TYPE_OPAQUE_FD_BIT;
    }

#ifdef _WIN32
    res &= ~VK_EXTERNAL_MEMORY_HANDLE_TYPE_OPAQUE_WIN32_BIT;
    res &= ~VK_EXTERNAL_MEMORY_HANDLE_TYPE_OPAQUE_WIN32_KMT_BIT;
#endif

    if (bits & VK_EXTERNAL_MEMORY_HANDLE_TYPE_ANDROID_HARDWARE_BUFFER_BIT_ANDROID) {
        res &= ~VK_EXTERNAL_MEMORY_HANDLE_TYPE_ANDROID_HARDWARE_BUFFER_BIT_ANDROID;
        res |= VK_EXT_MEMORY_HANDLE_TYPE_BIT;
    }

    if (bits & VK_EXTERNAL_MEMORY_HANDLE_TYPE_ZIRCON_VMO_BIT_FUCHSIA) {
        res &= ~VK_EXTERNAL_MEMORY_HANDLE_TYPE_ZIRCON_VMO_BIT_FUCHSIA;
        res |= VK_EXT_MEMORY_HANDLE_TYPE_BIT;
    }

    if (bits & VK_EXTERNAL_MEMORY_HANDLE_TYPE_ZIRCON_VMO_BIT_FUCHSIA) {
        res &= ~VK_EXTERNAL_MEMORY_HANDLE_TYPE_ZIRCON_VMO_BIT_FUCHSIA;
        res |= VK_EXT_MEMORY_HANDLE_TYPE_BIT;
    }
    return res;
}

VkExternalMemoryHandleTypeFlags
transformExternalMemoryHandleTypeFlags_fromhost(
    VkExternalMemoryHandleTypeFlags hostBits,
    VkExternalMemoryHandleTypeFlags wantedGuestHandleType) {

    VkExternalMemoryHandleTypeFlags res = hostBits;

    if (res & VK_EXT_MEMORY_HANDLE_TYPE_BIT) {
        res &= ~VK_EXT_MEMORY_HANDLE_TYPE_BIT;
        res |= wantedGuestHandleType;
    }

#ifdef _WIN32
    res &= ~VK_EXTERNAL_MEMORY_HANDLE_TYPE_OPAQUE_WIN32_BIT;
    res &= ~VK_EXTERNAL_MEMORY_HANDLE_TYPE_OPAQUE_WIN32_KMT_BIT;
#endif

    return res;
}

VkExternalMemoryProperties
transformExternalMemoryProperties_tohost(
    VkExternalMemoryProperties props) {
    VkExternalMemoryProperties res = props;
    res.exportFromImportedHandleTypes =
        transformExternalMemoryHandleTypeFlags_tohost(
            props.exportFromImportedHandleTypes);
    res.compatibleHandleTypes =
        transformExternalMemoryHandleTypeFlags_tohost(
            props.compatibleHandleTypes);
    return res;
}

VkExternalMemoryProperties
transformExternalMemoryProperties_fromhost(
    VkExternalMemoryProperties props,
    VkExternalMemoryHandleTypeFlags wantedGuestHandleType) {
    VkExternalMemoryProperties res = props;
    res.exportFromImportedHandleTypes =
        transformExternalMemoryHandleTypeFlags_fromhost(
            props.exportFromImportedHandleTypes,
            wantedGuestHandleType);
    res.compatibleHandleTypes =
        transformExternalMemoryHandleTypeFlags_fromhost(
            props.compatibleHandleTypes,
            wantedGuestHandleType);
    return res;
}

// Allocate a ready to use VkCommandBuffer for queue transfer. The caller needs
// to signal the returned VkFence when the VkCommandBuffer completes.
static std::tuple<VkCommandBuffer, VkFence> allocateQueueTransferCommandBuffer_locked() {
    auto vk = sVkEmulation->dvk;
    // Check if a command buffer in the pool is ready to use. If the associated
    // VkFence is ready, vkGetFenceStatus will return VK_SUCCESS, and the
    // associated command buffer should be ready to use, so we return that
    // command buffer with the associated VkFence. If the associated VkFence is
    // not ready, vkGetFenceStatus will return VK_NOT_READY, we will continue to
    // search and test the next command buffer. If the VkFence is in an error
    // state, vkGetFenceStatus will return with other VkResult variants, we will
    // abort.
    for (auto& [commandBuffer, fence] : sVkEmulation->transferQueueCommandBufferPool) {
        auto res = vk->vkGetFenceStatus(sVkEmulation->device, fence);
        if (res == VK_SUCCESS) {
            VK_CHECK(vk->vkResetFences(sVkEmulation->device, 1, &fence));
            VK_CHECK(vk->vkResetCommandBuffer(
                commandBuffer,
                VK_COMMAND_BUFFER_RESET_RELEASE_RESOURCES_BIT));
            return std::make_tuple(commandBuffer, fence);
        }
        if (res == VK_NOT_READY) {
            continue;
        }
        // We either have a device lost, or an invalid fence state. For the device lost case,
        // VK_CHECK will ensure we capture the relevant streams.
        VK_CHECK(res);
    }
    VkCommandBuffer commandBuffer;
    VkCommandBufferAllocateInfo allocateInfo = {
        .sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO,
        .pNext = nullptr,
        .commandPool = sVkEmulation->commandPool,
        .level = VK_COMMAND_BUFFER_LEVEL_PRIMARY,
        .commandBufferCount = 1,
    };
    VK_CHECK(vk->vkAllocateCommandBuffers(sVkEmulation->device, &allocateInfo,
                                          &commandBuffer));
    VkFence fence;
    VkFenceCreateInfo fenceCi = {
        .sType = VK_STRUCTURE_TYPE_FENCE_CREATE_INFO,
        .pNext = nullptr,
        .flags = 0,
    };
    VK_CHECK(
        vk->vkCreateFence(sVkEmulation->device, &fenceCi, nullptr, &fence));

    sVkEmulation->transferQueueCommandBufferPool.emplace_back(commandBuffer, fence);

    VK_COMMON_VERBOSE(
        "Create a new command buffer for queue transfer for a total of %d "
        "transfer command buffers",
        static_cast<int>(sVkEmulation->transferQueueCommandBufferPool.size()));

    return std::make_tuple(commandBuffer, fence);
}

void acquireColorBuffersForHostComposing(const std::vector<uint32_t>& layerColorBuffers,
                                         uint32_t renderTargetColorBuffer) {
    if (!sVkEmulation || !sVkEmulation->live) {
        GFXSTREAM_ABORT(FatalError(ABORT_REASON_OTHER)) << "Host Vulkan device lost";
    }

    std::vector<std::tuple<uint32_t, VkImageLayout>> colorBuffersAndLayouts;
    for (uint32_t layerColorBuffer : layerColorBuffers) {
        colorBuffersAndLayouts.emplace_back(
            layerColorBuffer, FrameBuffer::getFB()->getVkImageLayoutForComposeLayer());
    }
    colorBuffersAndLayouts.emplace_back(renderTargetColorBuffer,
                                        VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL);
    AutoLock lock(sVkEmulationLock);
    auto vk = sVkEmulation->dvk;

    std::vector<std::tuple<VkEmulation::ColorBufferInfo*, VkImageLayout>>
        colorBufferInfosAndLayouts;
    for (auto [colorBufferHandle, newLayout] : colorBuffersAndLayouts) {
        VkEmulation::ColorBufferInfo *infoPtr =
            android::base::find(sVkEmulation->colorBuffers, colorBufferHandle);
        if (!infoPtr) {
            VK_COMMON_ERROR("Invalid ColorBuffer handle %d.",
                            static_cast<int>(colorBufferHandle));
            continue;
        }
        colorBufferInfosAndLayouts.emplace_back(infoPtr, newLayout);
    }

    std::vector<VkImageMemoryBarrier> queueTransferBarriers;
    std::stringstream transferredColorBuffers;
    for (auto [infoPtr, _] : colorBufferInfosAndLayouts) {
        if (infoPtr->ownedByHost->load()) {
            VK_COMMON_VERBOSE("Skipping queue transfer from guest to host for ColorBuffer(id = %d)",
                              static_cast<int>(infoPtr->handle));
            continue;
        }
        VkImageMemoryBarrier queueTransferBarrier = {
            .sType = VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER,
            .pNext = nullptr,
            .srcAccessMask = VK_ACCESS_MEMORY_WRITE_BIT | VK_ACCESS_MEMORY_READ_BIT,
            // VK_ACCESS_SHADER_READ_BIT for the compose layers, and VK_ACCESS_TRANSFER_READ_BIT for
            // the render target/post source.
            .dstAccessMask = VK_ACCESS_SHADER_READ_BIT | VK_ACCESS_TRANSFER_READ_BIT,
            .oldLayout = VK_IMAGE_LAYOUT_PRESENT_SRC_KHR,
            .newLayout = VK_IMAGE_LAYOUT_PRESENT_SRC_KHR,
            .srcQueueFamilyIndex = VK_QUEUE_FAMILY_EXTERNAL,
            .dstQueueFamilyIndex = sVkEmulation->queueFamilyIndex,
            .image = infoPtr->image,
            .subresourceRange =
                {
                    .aspectMask = VK_IMAGE_ASPECT_COLOR_BIT,
                    .baseMipLevel = 0,
                    .levelCount = 1,
                    .baseArrayLayer = 0,
                    .layerCount = 1,
                },
        };
        queueTransferBarriers.emplace_back(queueTransferBarrier);
        transferredColorBuffers << infoPtr->handle << " ";
        infoPtr->ownedByHost->store(true);
        infoPtr->currentLayout = VK_IMAGE_LAYOUT_PRESENT_SRC_KHR;
    }

    std::vector<VkImageMemoryBarrier> layoutTransitionBarriers;
    for (auto [infoPtr, newLayout] : colorBufferInfosAndLayouts) {
        if (newLayout == VK_IMAGE_LAYOUT_UNDEFINED || infoPtr->currentLayout == newLayout) {
            continue;
        }
        VkImageMemoryBarrier layoutTransitionBarrier = {
            .sType = VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER,
            .pNext = nullptr,
            .srcAccessMask = VK_ACCESS_MEMORY_WRITE_BIT | VK_ACCESS_MEMORY_READ_BIT,
            // VK_ACCESS_SHADER_READ_BIT for the compose layers, and VK_ACCESS_TRANSFER_READ_BIT for
            // the render target/post source.
            .dstAccessMask = VK_ACCESS_SHADER_READ_BIT | VK_ACCESS_TRANSFER_READ_BIT,
            .oldLayout = infoPtr->currentLayout,
            .newLayout = newLayout,
            .srcQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED,
            .dstQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED,
            .image = infoPtr->image,
            .subresourceRange =
                {
                    .aspectMask = VK_IMAGE_ASPECT_COLOR_BIT,
                    .baseMipLevel = 0,
                    .levelCount = 1,
                    .baseArrayLayer = 0,
                    .layerCount = 1,
                },
        };
        layoutTransitionBarriers.emplace_back(layoutTransitionBarrier);
        infoPtr->currentLayout = newLayout;
    }

    auto [commandBuffer, fence] = allocateQueueTransferCommandBuffer_locked();

    VkCommandBufferBeginInfo beginInfo = {
        .sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO,
        .pNext = nullptr,
        .flags = 0,
        .pInheritanceInfo = nullptr,
    };
    VK_CHECK(vk->vkBeginCommandBuffer(commandBuffer, &beginInfo));
    if (!queueTransferBarriers.empty()) {
        vk->vkCmdPipelineBarrier(commandBuffer, VK_PIPELINE_STAGE_ALL_COMMANDS_BIT,
                                 VK_PIPELINE_STAGE_ALL_COMMANDS_BIT, 0, 0, nullptr, 0, nullptr,
                                 static_cast<uint32_t>(queueTransferBarriers.size()),
                                 queueTransferBarriers.data());
    }
    if (!layoutTransitionBarriers.empty()) {
        vk->vkCmdPipelineBarrier(commandBuffer, VK_PIPELINE_STAGE_ALL_COMMANDS_BIT,
                                 VK_PIPELINE_STAGE_ALL_COMMANDS_BIT, 0, 0, nullptr, 0, nullptr,
                                 static_cast<uint32_t>(layoutTransitionBarriers.size()),
                                 layoutTransitionBarriers.data());
    }
    VK_CHECK(vk->vkEndCommandBuffer(commandBuffer));

    // We assume the host Vulkan compositor lives on the same queue, so we don't
    // need to use semaphore to synchronize with the host compositor.
    VkSubmitInfo submitInfo = {
        .sType = VK_STRUCTURE_TYPE_SUBMIT_INFO,
        .pNext = nullptr,
        .waitSemaphoreCount = 0,
        .pWaitSemaphores = nullptr,
        .pWaitDstStageMask = nullptr,
        .commandBufferCount = 1,
        .pCommandBuffers = &commandBuffer,
        .signalSemaphoreCount = 0,
        .pSignalSemaphores = nullptr,
    };
    {
        std::stringstream ss;
        ss << __func__
           << ": submitting commands to issue acquire queue transfer from "
              "guest to host for ColorBuffer("
           << transferredColorBuffers.str() << ")";
        AEMU_SCOPED_TRACE(ss.str().c_str());
        android::base::AutoLock lock(*sVkEmulation->queueLock);
        VK_CHECK(vk->vkQueueSubmit(sVkEmulation->queue, 1, &submitInfo, fence));
    }
}

static VkFence doReleaseColorBufferForGuestRendering(
    const std::vector<uint32_t>& colorBufferHandles) {
    if (!sVkEmulation || !sVkEmulation->live) {
        GFXSTREAM_ABORT(FatalError(ABORT_REASON_OTHER)) << "Host Vulkan device lost";
    }

    AutoLock lock(sVkEmulationLock);
    auto vk = sVkEmulation->dvk;

    std::stringstream transferredColorBuffers;
    std::vector<VkImageMemoryBarrier> layoutTransitionBarriers;
    std::vector<VkImageMemoryBarrier> queueTransferBarriers;
    for (uint32_t colorBufferHandle : colorBufferHandles) {
        auto infoPtr =
            android::base::find(sVkEmulation->colorBuffers, colorBufferHandle);
        if (!infoPtr) {
            VK_COMMON_ERROR("Invalid ColorBuffer handle %d.",
                            static_cast<int>(colorBufferHandle));
            continue;
        }
        if (!infoPtr->ownedByHost->load()) {
            VK_COMMON_VERBOSE(
                "Skipping queue transfer from host to guest for "
                "ColorBuffer(id = %d)",
                static_cast<int>(colorBufferHandle));
            continue;
        }
        VkImageMemoryBarrier layoutTransitionBarrier = {
            .sType = VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER,
            .pNext = nullptr,
            .srcAccessMask = VK_ACCESS_MEMORY_READ_BIT | VK_ACCESS_MEMORY_WRITE_BIT,
            .dstAccessMask = VK_ACCESS_MEMORY_READ_BIT | VK_ACCESS_MEMORY_WRITE_BIT,
            .oldLayout = infoPtr->currentLayout,
            .newLayout = VK_IMAGE_LAYOUT_PRESENT_SRC_KHR,
            .srcQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED,
            .dstQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED,
            .image = infoPtr->image,
            .subresourceRange =
                {
                    .aspectMask = VK_IMAGE_ASPECT_COLOR_BIT,
                    .baseMipLevel = 0,
                    .levelCount = 1,
                    .baseArrayLayer = 0,
                    .layerCount = 1,
                },
        };
        layoutTransitionBarriers.emplace_back(layoutTransitionBarrier);
        infoPtr->currentLayout = layoutTransitionBarrier.newLayout;

        VkImageMemoryBarrier queueTransferBarrier = {
            .sType = VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER,
            .pNext = nullptr,
            .srcAccessMask = VK_ACCESS_MEMORY_READ_BIT | VK_ACCESS_MEMORY_WRITE_BIT,
            .dstAccessMask = VK_ACCESS_MEMORY_READ_BIT | VK_ACCESS_MEMORY_WRITE_BIT,
            .oldLayout = infoPtr->currentLayout,
            .newLayout = infoPtr->currentLayout,
            .srcQueueFamilyIndex = sVkEmulation->queueFamilyIndex,
            .dstQueueFamilyIndex = VK_QUEUE_FAMILY_EXTERNAL,
            .image = infoPtr->image,
            .subresourceRange =
                {
                    .aspectMask = VK_IMAGE_ASPECT_COLOR_BIT,
                    .baseMipLevel = 0,
                    .levelCount = 1,
                    .baseArrayLayer = 0,
                    .layerCount = 1,
                },
        };
        queueTransferBarriers.emplace_back(queueTransferBarrier);
        transferredColorBuffers << colorBufferHandle << " ";
        infoPtr->ownedByHost->store(false);
    }

    auto [commandBuffer, fence] = allocateQueueTransferCommandBuffer_locked();

    VK_CHECK(vk->vkResetCommandBuffer(commandBuffer, 0));
    VkCommandBufferBeginInfo beginInfo = {
        .sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO,
        .pNext = nullptr,
        .flags = VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT,
        .pInheritanceInfo = nullptr,
    };
    VK_CHECK(vk->vkBeginCommandBuffer(commandBuffer, &beginInfo));
    vk->vkCmdPipelineBarrier(commandBuffer, VK_PIPELINE_STAGE_ALL_COMMANDS_BIT,
                             VK_PIPELINE_STAGE_ALL_COMMANDS_BIT, 0, 0, nullptr, 0, nullptr,
                             static_cast<uint32_t>(layoutTransitionBarriers.size()),
                             layoutTransitionBarriers.data());
    vk->vkCmdPipelineBarrier(commandBuffer, VK_PIPELINE_STAGE_ALL_COMMANDS_BIT,
                             VK_PIPELINE_STAGE_ALL_COMMANDS_BIT, 0, 0, nullptr, 0, nullptr,
                             static_cast<uint32_t>(queueTransferBarriers.size()),
                             queueTransferBarriers.data());
    VK_CHECK(vk->vkEndCommandBuffer(commandBuffer));
    // We assume the host Vulkan compositor lives on the same queue, so we don't
    // need to use semaphore to synchronize with the host compositor.
    VkSubmitInfo submitInfo = {
        .sType = VK_STRUCTURE_TYPE_SUBMIT_INFO,
        .pNext = nullptr,
        .waitSemaphoreCount = 0,
        .pWaitSemaphores = nullptr,
        .pWaitDstStageMask = nullptr,
        .commandBufferCount = 1,
        .pCommandBuffers = &commandBuffer,
        .signalSemaphoreCount = 0,
        .pSignalSemaphores = nullptr,
    };

    {
        std::stringstream ss;
        ss << __func__
           << ": submitting commands to issue release queue transfer from host "
              "to guest for ColorBuffer("
           << transferredColorBuffers.str() << ")";
        AEMU_SCOPED_TRACE(ss.str().c_str());
        android::base::AutoLock lock(*sVkEmulation->queueLock);
        VK_CHECK(vk->vkQueueSubmit(sVkEmulation->queue, 1, &submitInfo, fence));
    }
    return fence;
}

void releaseColorBufferFromHostComposing(const std::vector<uint32_t>& colorBufferHandles) {
    doReleaseColorBufferForGuestRendering(colorBufferHandles);
}

void releaseColorBufferFromHostComposingSync(const std::vector<uint32_t>& colorBufferHandles) {
    VkFence fence = doReleaseColorBufferForGuestRendering(colorBufferHandles);
    if (!sVkEmulation || !sVkEmulation->live) {
        GFXSTREAM_ABORT(FatalError(ABORT_REASON_OTHER)) << "Host Vulkan device lost";
    }

    AutoLock lock(sVkEmulationLock);
    auto vk = sVkEmulation->dvk;
    static constexpr uint64_t ANB_MAX_WAIT_NS =
        5ULL * 1000ULL * 1000ULL * 1000ULL;
    VK_CHECK(vk->vkWaitForFences(sVkEmulation->device, 1, &fence, VK_TRUE,
                                 ANB_MAX_WAIT_NS));
}

void setColorBufferCurrentLayout(uint32_t colorBufferHandle, VkImageLayout layout) {
    AutoLock lock(sVkEmulationLock);

    auto infoPtr = android::base::find(sVkEmulation->colorBuffers, colorBufferHandle);
    if (!infoPtr) {
        VK_COMMON_ERROR("Invalid ColorBuffer handle %d.", static_cast<int>(colorBufferHandle));
        return;
    }
    infoPtr->currentLayout = layout;
}

} // namespace goldfish_vk
