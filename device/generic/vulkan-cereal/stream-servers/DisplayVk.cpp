#include "DisplayVk.h"

#include <algorithm>
#include <chrono>
#include <glm/glm.hpp>
#include <glm/gtx/matrix_transform_2d.hpp>
#include <thread>

#include "host-common/GfxstreamFatalError.h"
#include "host-common/logging.h"
#include "vulkan/VkCommonOperations.h"
#include "vulkan/VkFormatUtils.h"
#include "vulkan/vk_enum_string_helper.h"

using emugl::ABORT_REASON_OTHER;
using emugl::FatalError;

#define DISPLAY_VK_ERROR(fmt, ...)                                                            \
    do {                                                                                      \
        fprintf(stderr, "%s(%s:%d): " fmt "\n", __func__, __FILE__, __LINE__, ##__VA_ARGS__); \
        fflush(stderr);                                                                       \
    } while (0)

#define DISPLAY_VK_ERROR_ONCE(fmt, ...)              \
    do {                                             \
        static bool displayVkInternalLogged = false; \
        if (!displayVkInternalLogged) {              \
            DISPLAY_VK_ERROR(fmt, ##__VA_ARGS__);    \
            displayVkInternalLogged = true;          \
        }                                            \
    } while (0)

namespace {

bool shouldRecreateSwapchain(VkResult result) {
    switch (result) {
        case VK_SUBOPTIMAL_KHR:
        case VK_ERROR_OUT_OF_DATE_KHR:
        // b/217229121: drivers may return VK_ERROR_FULL_SCREEN_EXCLUSIVE_MODE_LOST_EXT in
        // vkQueuePresentKHR even if VK_EXT_full_screen_exclusive is not enabled.
        case VK_ERROR_FULL_SCREEN_EXCLUSIVE_MODE_LOST_EXT:
            return true;

        default:
            return false;
    }
}

VkResult waitForVkQueueIdleWithRetry(const goldfish_vk::VulkanDispatch& vk, VkQueue queue) {
    using namespace std::chrono_literals;
    constexpr uint32_t retryLimit = 5;
    constexpr std::chrono::duration waitInterval = 4ms;
    VkResult res = vk.vkQueueWaitIdle(queue);
    for (uint32_t retryTimes = 1; retryTimes < retryLimit && res == VK_TIMEOUT; retryTimes++) {
        INFO("VK_TIMEOUT returned from vkQueueWaitIdle with %" PRIu32 " attempt. Wait for %" PRIu32
             "ms before another attempt.",
             retryTimes,
             static_cast<uint32_t>(
                 std::chrono::duration_cast<std::chrono::milliseconds>(waitInterval).count()));
        std::this_thread::sleep_for(waitInterval);
        res = vk.vkQueueWaitIdle(queue);
    }
    return res;
}

}  // namespace

DisplayVk::DisplayVk(const goldfish_vk::VulkanDispatch& vk, VkPhysicalDevice vkPhysicalDevice,
                     uint32_t swapChainQueueFamilyIndex, uint32_t compositorQueueFamilyIndex,
                     VkDevice vkDevice, VkQueue compositorVkQueue,
                     std::shared_ptr<android::base::Lock> compositorVkQueueLock,
                     VkQueue swapChainVkqueue,
                     std::shared_ptr<android::base::Lock> swapChainVkQueueLock)
    : m_vk(vk),
      m_vkPhysicalDevice(vkPhysicalDevice),
      m_swapChainQueueFamilyIndex(swapChainQueueFamilyIndex),
      m_compositorQueueFamilyIndex(compositorQueueFamilyIndex),
      m_vkDevice(vkDevice),
      m_compositorVkQueue(compositorVkQueue),
      m_compositorVkQueueLock(compositorVkQueueLock),
      m_swapChainVkQueue(swapChainVkqueue),
      m_swapChainVkQueueLock(swapChainVkQueueLock),
      m_vkCommandPool(VK_NULL_HANDLE),
      m_swapChainStateVk(nullptr),
      m_compositorVk(nullptr),
      m_surfaceState(nullptr) {
    // TODO(kaiyili): validate the capabilites of the passed in Vulkan
    // components.
    VkCommandPoolCreateInfo commandPoolCi = {
        .sType = VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO,
        .flags = VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT,
        .queueFamilyIndex = m_compositorQueueFamilyIndex,
    };
    VK_CHECK(m_vk.vkCreateCommandPool(m_vkDevice, &commandPoolCi, nullptr, &m_vkCommandPool));

    VkSamplerCreateInfo samplerCi = {.sType = VK_STRUCTURE_TYPE_SAMPLER_CREATE_INFO,
                                     .magFilter = VK_FILTER_LINEAR,
                                     .minFilter = VK_FILTER_LINEAR,
                                     .mipmapMode = VK_SAMPLER_MIPMAP_MODE_LINEAR,
                                     .addressModeU = VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_BORDER,
                                     .addressModeV = VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_BORDER,
                                     .addressModeW = VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_BORDER,
                                     .mipLodBias = 0.0f,
                                     .anisotropyEnable = VK_FALSE,
                                     .maxAnisotropy = 1.0f,
                                     .compareEnable = VK_FALSE,
                                     .compareOp = VK_COMPARE_OP_ALWAYS,
                                     .minLod = 0.0f,
                                     .maxLod = 0.0f,
                                     .borderColor = VK_BORDER_COLOR_INT_TRANSPARENT_BLACK,
                                     .unnormalizedCoordinates = VK_FALSE};
    VK_CHECK(m_vk.vkCreateSampler(m_vkDevice, &samplerCi, nullptr, &m_compositionVkSampler));
}

DisplayVk::~DisplayVk() {
    {
        android::base::AutoLock lock(*m_swapChainVkQueueLock);
        VK_CHECK(waitForVkQueueIdleWithRetry(m_vk, m_swapChainVkQueue));
    }
    {
        android::base::AutoLock lock(*m_compositorVkQueueLock);
        VK_CHECK(waitForVkQueueIdleWithRetry(m_vk, m_compositorVkQueue));
    }
    m_postResourceFuture = std::nullopt;
    m_composeResourceFuture = std::nullopt;
    m_compositorVkRenderTargets.clear();
    m_vk.vkDestroySampler(m_vkDevice, m_compositionVkSampler, nullptr);
    m_surfaceState.reset();
    m_compositorVk.reset();
    m_swapChainStateVk.reset();
    m_vk.vkDestroyCommandPool(m_vkDevice, m_vkCommandPool, nullptr);
}

void DisplayVk::bindToSurface(VkSurfaceKHR surface, uint32_t width, uint32_t height) {
    {
        android::base::AutoLock lock(*m_compositorVkQueueLock);
        VK_CHECK(waitForVkQueueIdleWithRetry(m_vk, m_compositorVkQueue));
    }
    {
        android::base::AutoLock lock(*m_swapChainVkQueueLock);
        VK_CHECK(waitForVkQueueIdleWithRetry(m_vk, m_swapChainVkQueue));
    }
    m_postResourceFuture = std::nullopt;
    m_composeResourceFuture = std::nullopt;
    m_compositorVkRenderTargets = std::deque<std::shared_ptr<CompositorVkRenderTarget>>(
        k_compositorVkRenderTargetCacheSize, nullptr);
    m_compositorVk.reset();
    m_swapChainStateVk.reset();

    if (!SwapChainStateVk::validateQueueFamilyProperties(m_vk, m_vkPhysicalDevice, surface,
                                                         m_swapChainQueueFamilyIndex)) {
        GFXSTREAM_ABORT(FatalError(ABORT_REASON_OTHER))
            << "DisplayVk can't create VkSwapchainKHR with given VkDevice and VkSurfaceKHR.";
    }
    auto swapChainCi = SwapChainStateVk::createSwapChainCi(
        m_vk, surface, m_vkPhysicalDevice, width, height,
        {m_swapChainQueueFamilyIndex, m_compositorQueueFamilyIndex});
    if (!swapChainCi) {
        GFXSTREAM_ABORT(FatalError(ABORT_REASON_OTHER))
            << "Failed to create VkSwapchainCreateInfoKHR.";
    }
    VkFormatProperties formatProps;
    m_vk.vkGetPhysicalDeviceFormatProperties(m_vkPhysicalDevice,
                                             swapChainCi->mCreateInfo.imageFormat, &formatProps);
    if (!(formatProps.optimalTilingFeatures & VK_FORMAT_FEATURE_COLOR_ATTACHMENT_BIT)) {
        GFXSTREAM_ABORT(FatalError(ABORT_REASON_OTHER))
            << "DisplayVk: The image format chosen for present VkImage can't be used as the color "
               "attachment, and therefore can't be used as the render target of CompositorVk.";
    }
    m_swapChainStateVk =
        std::make_unique<SwapChainStateVk>(m_vk, m_vkDevice, swapChainCi->mCreateInfo);
    m_compositorVk = CompositorVk::create(
        m_vk, m_vkDevice, m_vkPhysicalDevice, m_compositorVkQueue, m_compositorVkQueueLock,
        k_compositorVkRenderTargetFormat, VK_IMAGE_LAYOUT_UNDEFINED,
        VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL, m_swapChainStateVk->getVkImageViews().size(),
        m_vkCommandPool, m_compositionVkSampler);

    int numSwapChainImages = m_swapChainStateVk->getVkImages().size();

    m_postResourceFuture = std::async(std::launch::deferred, [this] {
                               return PostResource::create(m_vk, m_vkDevice, m_vkCommandPool);
                           }).share();
    m_postResourceFuture.value().wait();

    m_inFlightFrameIndex = 0;

    m_composeResourceFuture = std::async(std::launch::deferred, [this] {
        return ComposeResource::create(m_vk, m_vkDevice, m_vkCommandPool);
    });
    m_composeResourceFuture.value().wait();
    auto surfaceState = std::make_unique<SurfaceState>();
    surfaceState->m_height = height;
    surfaceState->m_width = width;
    m_surfaceState = std::move(surfaceState);
}

std::shared_ptr<DisplayVk::DisplayBufferInfo> DisplayVk::createDisplayBuffer(
    VkImage image, const VkImageCreateInfo& vkImageCreateInfo) {
    return std::shared_ptr<DisplayBufferInfo>(
        new DisplayBufferInfo(m_vk, m_vkDevice, vkImageCreateInfo, image));
}

std::tuple<bool, std::shared_future<void>> DisplayVk::post(
    std::shared_ptr<DisplayBufferInfo> displayBufferPtr) {
    auto completedFuture = std::async(std::launch::deferred, [] {}).share();
    completedFuture.wait();
    if (!displayBufferPtr) {
        fprintf(stderr, "%s: warning: null ptr passed to post buffer\n", __func__);
        return std::make_tuple(true, std::move(completedFuture));
    }
    if (!m_swapChainStateVk || !m_surfaceState) {
        DISPLAY_VK_ERROR("Haven't bound to a surface, can't post ColorBuffer.");
        return std::make_tuple(true, std::move(completedFuture));
    }
    if (!canPost(displayBufferPtr->m_vkImageCreateInfo)) {
        DISPLAY_VK_ERROR("Can't post ColorBuffer.");
        return std::make_tuple(true, std::move(completedFuture));
    }

    std::shared_ptr<PostResource> postResource = m_postResourceFuture.value().get();
    VkSemaphore imageReadySem = postResource->m_swapchainImageAcquireSemaphore;

    uint32_t imageIndex;
    VkResult acquireRes =
        m_vk.vkAcquireNextImageKHR(m_vkDevice, m_swapChainStateVk->getSwapChain(), UINT64_MAX,
                                   imageReadySem, VK_NULL_HANDLE, &imageIndex);
    if (shouldRecreateSwapchain(acquireRes)) {
        return std::make_tuple(false, std::shared_future<void>());
    }
    VK_CHECK(acquireRes);

    VkCommandBuffer cmdBuff = postResource->m_vkCommandBuffer;
    VK_CHECK(m_vk.vkResetCommandBuffer(cmdBuff, 0));
    VkCommandBufferBeginInfo beginInfo = {
        .sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO,
        .flags = VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT,
    };
    VK_CHECK(m_vk.vkBeginCommandBuffer(cmdBuff, &beginInfo));
    VkImageMemoryBarrier presentToXferDstBarrier = {
        .sType = VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER,
        .srcAccessMask = VK_ACCESS_MEMORY_WRITE_BIT,
        .dstAccessMask = VK_ACCESS_MEMORY_WRITE_BIT,
        .oldLayout = VK_IMAGE_LAYOUT_UNDEFINED,
        .newLayout = VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
        .srcQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED,
        .dstQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED,
        .image = m_swapChainStateVk->getVkImages()[imageIndex],
        .subresourceRange = {.aspectMask = VK_IMAGE_ASPECT_COLOR_BIT,
                             .baseMipLevel = 0,
                             .levelCount = 1,
                             .baseArrayLayer = 0,
                             .layerCount = 1}};
    m_vk.vkCmdPipelineBarrier(cmdBuff, VK_PIPELINE_STAGE_TRANSFER_BIT,
                              VK_PIPELINE_STAGE_TRANSFER_BIT, 0, 0, nullptr, 0, nullptr, 1,
                              &presentToXferDstBarrier);
    VkImageBlit region = {
        .srcSubresource = {.aspectMask = VK_IMAGE_ASPECT_COLOR_BIT,
                           .mipLevel = 0,
                           .baseArrayLayer = 0,
                           .layerCount = 1},
        .srcOffsets = {{0, 0, 0},
                       {static_cast<int32_t>(displayBufferPtr->m_vkImageCreateInfo.extent.width),
                        static_cast<int32_t>(displayBufferPtr->m_vkImageCreateInfo.extent.height),
                        1}},
        .dstSubresource = {.aspectMask = VK_IMAGE_ASPECT_COLOR_BIT,
                           .mipLevel = 0,
                           .baseArrayLayer = 0,
                           .layerCount = 1},
        .dstOffsets = {{0, 0, 0},
                       {static_cast<int32_t>(m_surfaceState->m_width),
                        static_cast<int32_t>(m_surfaceState->m_height), 1}},
    };
    VkFormat displayBufferFormat = displayBufferPtr->m_vkImageCreateInfo.format;
    VkImageTiling displayBufferTiling = displayBufferPtr->m_vkImageCreateInfo.tiling;
    VkFilter filter = VK_FILTER_NEAREST;
    VkFormatFeatureFlags displayBufferFormatFeatures =
        getFormatFeatures(displayBufferFormat, displayBufferTiling);
    if (formatIsDepthOrStencil(displayBufferFormat)) {
        DISPLAY_VK_ERROR_ONCE(
            "The format of the display buffer, %s, is a depth/stencil format, we can only use the "
            "VK_FILTER_NEAREST filter according to VUID-vkCmdBlitImage-srcImage-00232.",
            string_VkFormat(displayBufferFormat));
        filter = VK_FILTER_NEAREST;
    } else if (!(displayBufferFormatFeatures & VK_FORMAT_FEATURE_SAMPLED_IMAGE_FILTER_LINEAR_BIT)) {
        DISPLAY_VK_ERROR_ONCE(
            "The format of the display buffer, %s, with the tiling, %s, doesn't support "
            "VK_FORMAT_FEATURE_SAMPLED_IMAGE_FILTER_LINEAR_BIT, so we can only use the "
            "VK_FILTER_NEAREST filter according VUID-vkCmdBlitImage-filter-02001. The supported "
            "features are %s.",
            string_VkFormat(displayBufferFormat), string_VkImageTiling(displayBufferTiling),
            string_VkFormatFeatureFlags(displayBufferFormatFeatures).c_str());
        filter = VK_FILTER_NEAREST;
    } else {
        filter = VK_FILTER_LINEAR;
    }
    m_vk.vkCmdBlitImage(cmdBuff, displayBufferPtr->m_vkImage, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                        m_swapChainStateVk->getVkImages()[imageIndex],
                        VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, 1, &region, filter);
    VkImageMemoryBarrier xferDstToPresentBarrier = {
        .sType = VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER,
        .srcAccessMask = VK_ACCESS_MEMORY_WRITE_BIT,
        .dstAccessMask = VK_ACCESS_MEMORY_READ_BIT,
        .oldLayout = VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
        .newLayout = VK_IMAGE_LAYOUT_PRESENT_SRC_KHR,
        .srcQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED,
        .dstQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED,
        .image = m_swapChainStateVk->getVkImages()[imageIndex],
        .subresourceRange = {.aspectMask = VK_IMAGE_ASPECT_COLOR_BIT,
                             .baseMipLevel = 0,
                             .levelCount = 1,
                             .baseArrayLayer = 0,
                             .layerCount = 1}};
    m_vk.vkCmdPipelineBarrier(cmdBuff, VK_PIPELINE_STAGE_TRANSFER_BIT,
                              VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT, 0, 0, nullptr, 0, nullptr, 1,
                              &xferDstToPresentBarrier);
    VK_CHECK(m_vk.vkEndCommandBuffer(cmdBuff));

    VkFence postCompleteFence = postResource->m_swapchainImageReleaseFence;
    VK_CHECK(m_vk.vkResetFences(m_vkDevice, 1, &postCompleteFence));
    VkSemaphore postCompleteSemaphore = postResource->m_swapchainImageReleaseSemaphore;
    VkPipelineStageFlags waitStages[] = {VK_PIPELINE_STAGE_TRANSFER_BIT};
    VkSubmitInfo submitInfo = {.sType = VK_STRUCTURE_TYPE_SUBMIT_INFO,
                               .waitSemaphoreCount = 1,
                               .pWaitSemaphores = &imageReadySem,
                               .pWaitDstStageMask = waitStages,
                               .commandBufferCount = 1,
                               .pCommandBuffers = &cmdBuff,
                               .signalSemaphoreCount = 1,
                               .pSignalSemaphores = &postCompleteSemaphore};
    {
        android::base::AutoLock lock(*m_compositorVkQueueLock);
        VK_CHECK(m_vk.vkQueueSubmit(m_compositorVkQueue, 1, &submitInfo, postCompleteFence));
    }
    std::shared_future<std::shared_ptr<PostResource>> postResourceFuture =
        std::async(std::launch::deferred, [postCompleteFence, postResource, displayBufferPtr,
                                           this]() mutable {
            VK_CHECK(m_vk.vkWaitForFences(m_vkDevice, 1, &postCompleteFence, VK_TRUE, UINT64_MAX));
            // Explicitly reset displayBufferPtr here to make sure the lambda actually capture
            // displayBufferPtr to correctly extend the lifetime of displayBufferPtr until the
            // rendering completes.
            displayBufferPtr.reset();
            return postResource;
        }).share();
    m_postResourceFuture = postResourceFuture;

    auto swapChain = m_swapChainStateVk->getSwapChain();
    VkPresentInfoKHR presentInfo = {.sType = VK_STRUCTURE_TYPE_PRESENT_INFO_KHR,
                                    .waitSemaphoreCount = 1,
                                    .pWaitSemaphores = &postCompleteSemaphore,
                                    .swapchainCount = 1,
                                    .pSwapchains = &swapChain,
                                    .pImageIndices = &imageIndex};
    VkResult presentRes;
    {
        android::base::AutoLock lock(*m_swapChainVkQueueLock);
        presentRes = m_vk.vkQueuePresentKHR(m_swapChainVkQueue, &presentInfo);
    }
    if (shouldRecreateSwapchain(presentRes)) {
        postResourceFuture.wait();
        return std::make_tuple(false, std::shared_future<void>());
    }
    VK_CHECK(presentRes);
    return std::make_tuple(true, std::async(std::launch::deferred, [postResourceFuture] {
                                     // We can't directly wait for the VkFence here, because we
                                     // share the VkFences on different frames, but we don't share
                                     // the future on different frames. If we directly wait for the
                                     // VkFence here, we may wait for a different frame if a new
                                     // frame starts to be drawn before this future is waited.
                                     postResourceFuture.wait();
                                 }).share());
}

std::tuple<bool, std::shared_future<void>> DisplayVk::compose(
    uint32_t numLayers, const ComposeLayer layers[],
    std::vector<std::shared_ptr<DisplayBufferInfo>> composeBuffers,
    std::shared_ptr<DisplayBufferInfo> targetBuffer) {
    std::shared_future<void> completedFuture = std::async(std::launch::deferred, [] {}).share();
    completedFuture.wait();

    if (!m_swapChainStateVk || !m_compositorVk) {
        DISPLAY_VK_ERROR("Haven't bound to a surface, can't compose color buffer.");
        // The surface hasn't been created yet, hence we don't return
        // std::nullopt to request rebinding.
        return std::make_tuple(true, std::move(completedFuture));
    }

    std::vector<std::unique_ptr<ComposeLayerVk>> composeLayers;
    for (int i = 0; i < numLayers; ++i) {
        if (layers[i].cbHandle == 0) {
            // When ColorBuffer handle is 0, it's expected that no ColorBuffer
            // is not found.
            continue;
        }
        if (!composeBuffers[i]) {
            DISPLAY_VK_ERROR("warning: null ptr passed to compose buffer for layer %d.", i);
            continue;
        }
        const auto& db = *composeBuffers[i];
        if (!canCompositeFrom(db.m_vkImageCreateInfo)) {
            DISPLAY_VK_ERROR("Can't composite from a display buffer. Skip the layer.");
            continue;
        }
        auto layer = ComposeLayerVk::createFromHwc2ComposeLayer(
            m_compositionVkSampler, composeBuffers[i]->m_vkImageView, layers[i],
            composeBuffers[i]->m_vkImageCreateInfo.extent.width,
            composeBuffers[i]->m_vkImageCreateInfo.extent.height,
            targetBuffer->m_vkImageCreateInfo.extent.width,
            targetBuffer->m_vkImageCreateInfo.extent.height);
        composeLayers.emplace_back(std::move(layer));
    }

    if (composeLayers.empty()) {
        return std::make_tuple(true, std::move(completedFuture));
    }

    if (!targetBuffer) {
        DISPLAY_VK_ERROR("warning null ptr passed to compose target.");
        return std::make_tuple(true, std::move(completedFuture));
    }
    if (!canCompositeTo(targetBuffer->m_vkImageCreateInfo)) {
        DISPLAY_VK_ERROR("Can't write the result of the composition to the display buffer.");
        return std::make_tuple(true, std::move(completedFuture));
    }

    std::shared_ptr<CompositorVkRenderTarget> compositorVkRenderTarget =
        targetBuffer->m_compositorVkRenderTarget.lock();
    if (!compositorVkRenderTarget) {
        compositorVkRenderTarget = m_compositorVk->createRenderTarget(
            targetBuffer->m_vkImageView, targetBuffer->m_vkImageCreateInfo.extent.width,
            targetBuffer->m_vkImageCreateInfo.extent.height);
        if (!compositorVkRenderTarget) {
            GFXSTREAM_ABORT(FatalError(ABORT_REASON_OTHER))
                << "Failed to create CompositorVkRenderTarget for the target display buffer.";
        }
        m_compositorVkRenderTargets.pop_back();
        m_compositorVkRenderTargets.push_front(compositorVkRenderTarget);
        targetBuffer->m_compositorVkRenderTarget = compositorVkRenderTarget;
    }

    std::future<std::unique_ptr<ComposeResource>> composeResourceFuture =
        std::move(m_composeResourceFuture.value());
    if (!composeResourceFuture.valid()) {
        GFXSTREAM_ABORT(FatalError(ABORT_REASON_OTHER))
            << "Invalid composeResourceFuture in m_postResourceFutures.";
    }
    std::unique_ptr<ComposeResource> composeResource = composeResourceFuture.get();

    if (compareAndSaveComposition(m_inFlightFrameIndex, numLayers, layers, composeBuffers)) {
        auto composition = std::make_unique<Composition>(std::move(composeLayers));
        m_compositorVk->setComposition(m_inFlightFrameIndex, std::move(composition));
    }

    VkCommandBuffer cmdBuff = composeResource->m_vkCommandBuffer;
    VK_CHECK(m_vk.vkResetCommandBuffer(cmdBuff, 0));

    VkCommandBufferBeginInfo beginInfo = {
        .sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO,
        .flags = VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT,
    };
    VK_CHECK(m_vk.vkBeginCommandBuffer(cmdBuff, &beginInfo));
    m_compositorVk->recordCommandBuffers(m_inFlightFrameIndex, cmdBuff, *compositorVkRenderTarget);
    // Insert a VkImageMemoryBarrier so that the vkCmdBlitImage in post will wait for the rendering
    // to the render target to complete.
    VkImageMemoryBarrier renderTargetBarrier = {
        .sType = VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER,
        .srcAccessMask = VK_ACCESS_MEMORY_WRITE_BIT,
        .dstAccessMask = VK_ACCESS_MEMORY_READ_BIT,
        .oldLayout = VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
        .newLayout = VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
        .srcQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED,
        .dstQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED,
        .image = targetBuffer->m_vkImage,
        .subresourceRange = {.aspectMask = VK_IMAGE_ASPECT_COLOR_BIT,
                             .baseMipLevel = 0,
                             .levelCount = 1,
                             .baseArrayLayer = 0,
                             .layerCount = 1}};
    m_vk.vkCmdPipelineBarrier(cmdBuff, VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT,
                              VK_PIPELINE_STAGE_TRANSFER_BIT, 0, 0, nullptr, 0, nullptr, 1,
                              &renderTargetBarrier);
    VK_CHECK(m_vk.vkEndCommandBuffer(cmdBuff));

    VkFence composeCompleteFence = composeResource->m_composeCompleteFence;
    VK_CHECK(m_vk.vkResetFences(m_vkDevice, 1, &composeCompleteFence));
    VkPipelineStageFlags waitStages[] = {VK_PIPELINE_STAGE_TRANSFER_BIT};
    VkSubmitInfo submitInfo = {.sType = VK_STRUCTURE_TYPE_SUBMIT_INFO,
                               .waitSemaphoreCount = 0,
                               .pWaitSemaphores = nullptr,
                               .pWaitDstStageMask = waitStages,
                               .commandBufferCount = 1,
                               .pCommandBuffers = &cmdBuff,
                               .signalSemaphoreCount = 0,
                               .pSignalSemaphores = nullptr};
    {
        android::base::AutoLock lock(*m_compositorVkQueueLock);
        VK_CHECK(m_vk.vkQueueSubmit(m_compositorVkQueue, 1, &submitInfo, composeCompleteFence));
    }

    m_composeResourceFuture =
        std::async(std::launch::deferred,
                   [composeCompleteFence, composeResource = std::move(composeResource),
                    composeBuffers = std::move(composeBuffers), targetBuffer, this]() mutable {
                       VK_CHECK(m_vk.vkWaitForFences(m_vkDevice, 1, &composeCompleteFence, VK_TRUE,
                                                     UINT64_MAX));
                       // Explicitly clear the composeBuffers here to ensure the lambda does
                       // caputure composeBuffers and correctly extend the lifetime of related
                       // DisplayBufferInfo until the render completes.
                       composeBuffers.clear();
                       // Explicitly reset the targetBuffer here to ensure the lambda does caputure
                       // targetBuffer and correctly extend the lifetime of the DisplayBufferInfo
                       // until the render completes.
                       targetBuffer.reset();
                       return std::move(composeResource);
                   });
    m_inFlightFrameIndex = (m_inFlightFrameIndex + 1) % m_swapChainStateVk->getVkImages().size();
    return post(targetBuffer);
}

VkFormatFeatureFlags DisplayVk::getFormatFeatures(VkFormat format, VkImageTiling tiling) {
    auto i = m_vkFormatProperties.find(format);
    if (i == m_vkFormatProperties.end()) {
        VkFormatProperties formatProperties;
        m_vk.vkGetPhysicalDeviceFormatProperties(m_vkPhysicalDevice, format, &formatProperties);
        i = m_vkFormatProperties.emplace(format, formatProperties).first;
    }
    const VkFormatProperties& formatProperties = i->second;
    VkFormatFeatureFlags formatFeatures = 0;
    if (tiling == VK_IMAGE_TILING_LINEAR) {
        formatFeatures = formatProperties.linearTilingFeatures;
    } else if (tiling == VK_IMAGE_TILING_OPTIMAL) {
        formatFeatures = formatProperties.optimalTilingFeatures;
    } else {
        DISPLAY_VK_ERROR("Unknown tiling %#" PRIx64 ".", static_cast<uint64_t>(tiling));
    }
    return formatFeatures;
}

bool DisplayVk::canPost(const VkImageCreateInfo& postImageCi) {
    // According to VUID-vkCmdBlitImage-srcImage-01999, the format features of srcImage must contain
    // VK_FORMAT_FEATURE_BLIT_SRC_BIT.
    VkFormatFeatureFlags formatFeatures = getFormatFeatures(postImageCi.format, postImageCi.tiling);
    if (!(formatFeatures & VK_FORMAT_FEATURE_BLIT_SRC_BIT)) {
        DISPLAY_VK_ERROR(
            "VK_FORMAT_FEATURE_BLIT_SRC_BLIT is not supported for VkImage with format %s, tilling "
            "%s. Supported features are %s.",
            string_VkFormat(postImageCi.format), string_VkImageTiling(postImageCi.tiling),
            string_VkFormatFeatureFlags(formatFeatures).c_str());
        return false;
    }

    // According to VUID-vkCmdBlitImage-srcImage-06421, srcImage must not use a format that requires
    // a sampler Y’CBCR conversion.
    if (formatRequiresSamplerYcbcrConversion(postImageCi.format)) {
        DISPLAY_VK_ERROR("Format %s requires a sampler Y'CbCr conversion. Can't be used to post.",
                         string_VkFormat(postImageCi.format));
        return false;
    }

    if (!(postImageCi.usage & VK_IMAGE_USAGE_TRANSFER_SRC_BIT)) {
        // According to VUID-vkCmdBlitImage-srcImage-00219, srcImage must have been created with
        // VK_IMAGE_USAGE_TRANSFER_SRC_BIT usage flag.
        DISPLAY_VK_ERROR(
            "The VkImage is not created with the VK_IMAGE_USAGE_TRANSFER_SRC_BIT usage flag. The "
            "usage flags are %s.",
            string_VkImageUsageFlags(postImageCi.usage).c_str());
        return false;
    }

    VkFormat swapChainFormat = m_swapChainStateVk->getFormat();
    if (formatIsSInt(postImageCi.format) || formatIsSInt(swapChainFormat)) {
        // According to VUID-vkCmdBlitImage-srcImage-00229, if either of srcImage or dstImage was
        // created with a signed integer VkFormat, the other must also have been created with a
        // signed integer VkFormat.
        if (!(formatIsSInt(postImageCi.format) && formatIsSInt(m_swapChainStateVk->getFormat()))) {
            DISPLAY_VK_ERROR(
                "The format(%s) doesn't match with the format of the presentable image(%s): either "
                "of the formats is a signed integer VkFormat, but the other is not.",
                string_VkFormat(postImageCi.format), string_VkFormat(swapChainFormat));
            return false;
        }
    }

    if (formatIsUInt(postImageCi.format) || formatIsUInt(swapChainFormat)) {
        // According to VUID-vkCmdBlitImage-srcImage-00230, if either of srcImage or dstImage was
        // created with an unsigned integer VkFormat, the other must also have been created with an
        // unsigned integer VkFormat.
        if (!(formatIsUInt(postImageCi.format) && formatIsUInt(swapChainFormat))) {
            DISPLAY_VK_ERROR(
                "The format(%s) doesn't match with the format of the presentable image(%s): either "
                "of the formats is an unsigned integer VkFormat, but the other is not.",
                string_VkFormat(postImageCi.format), string_VkFormat(swapChainFormat));
            return false;
        }
    }

    if (formatIsDepthOrStencil(postImageCi.format) || formatIsDepthOrStencil(swapChainFormat)) {
        // According to VUID-vkCmdBlitImage-srcImage-00231, if either of srcImage or dstImage was
        // created with a depth/stencil format, the other must have exactly the same format.
        if (postImageCi.format != swapChainFormat) {
            DISPLAY_VK_ERROR(
                "The format(%s) doesn't match with the format of the presentable image(%s): either "
                "of the formats is a depth/stencil VkFormat, but the other is not the same format.",
                string_VkFormat(postImageCi.format), string_VkFormat(swapChainFormat));
            return false;
        }
    }

    if (postImageCi.samples != VK_SAMPLE_COUNT_1_BIT) {
        // According to VUID-vkCmdBlitImage-srcImage-00233, srcImage must have been created with a
        // samples value of VK_SAMPLE_COUNT_1_BIT.
        DISPLAY_VK_ERROR(
            "The VkImage is not created with the VK_SAMPLE_COUNT_1_BIT samples value. The samples "
            "value is %s.",
            string_VkSampleCountFlagBits(postImageCi.samples));
        return false;
    }
    if (postImageCi.flags & VK_IMAGE_CREATE_SUBSAMPLED_BIT_EXT) {
        // According to VUID-vkCmdBlitImage-dstImage-02545, dstImage and srcImage must not have been
        // created with flags containing VK_IMAGE_CREATE_SUBSAMPLED_BIT_EXT.
        DISPLAY_VK_ERROR(
            "The VkImage can't be created with flags containing "
            "VK_IMAGE_CREATE_SUBSAMPLED_BIT_EXT. The flags are %s.",
            string_VkImageCreateFlags(postImageCi.flags).c_str());
        return false;
    }
    return true;
}

bool DisplayVk::canCompositeFrom(const VkImageCreateInfo& imageCi) {
    VkFormatFeatureFlags formatFeatures = getFormatFeatures(imageCi.format, imageCi.tiling);
    if (!(formatFeatures & VK_FORMAT_FEATURE_SAMPLED_IMAGE_BIT)) {
        DISPLAY_VK_ERROR(
            "The format, %s, with tiling, %s, doesn't support the "
            "VK_FORMAT_FEATURE_SAMPLED_IMAGE_BIT feature. All supported features are %s.",
            string_VkFormat(imageCi.format), string_VkImageTiling(imageCi.tiling),
            string_VkFormatFeatureFlags(formatFeatures).c_str());
        return false;
    }
    return true;
}

bool DisplayVk::canCompositeTo(const VkImageCreateInfo& imageCi) {
    VkFormatFeatureFlags formatFeatures = getFormatFeatures(imageCi.format, imageCi.tiling);
    if (!(formatFeatures & VK_FORMAT_FEATURE_COLOR_ATTACHMENT_BIT)) {
        DISPLAY_VK_ERROR(
            "The format, %s, with tiling, %s, doesn't support the "
            "VK_FORMAT_FEATURE_COLOR_ATTACHMENT_BIT feature. All supported features are %s.",
            string_VkFormat(imageCi.format), string_VkImageTiling(imageCi.tiling),
            string_VkFormatFeatureFlags(formatFeatures).c_str());
        return false;
    }
    if (!(imageCi.usage & VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT)) {
        DISPLAY_VK_ERROR(
            "The VkImage is not created with the VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT usage flag. "
            "The usage flags are %s.",
            string_VkImageUsageFlags(imageCi.usage).c_str());
        return false;
    }
    if (imageCi.format != k_compositorVkRenderTargetFormat) {
        DISPLAY_VK_ERROR(
            "The format of the image, %s, is not supported by the CompositorVk as the render "
            "target.",
            string_VkFormat(imageCi.format));
        return false;
    }
    return true;
}

bool DisplayVk::compareAndSaveComposition(
    uint32_t renderTargetIndex, uint32_t numLayers, const ComposeLayer layers[],
    const std::vector<std::shared_ptr<DisplayBufferInfo>>& composeBuffers) {
    if (!m_surfaceState) {
        GFXSTREAM_ABORT(FatalError(ABORT_REASON_OTHER))
            << "Haven't bound to a surface, can't compare and save composition.";
    }
    auto [iPrevComposition, compositionNotFound] =
        m_surfaceState->m_prevCompositions.emplace(renderTargetIndex, 0);
    auto& prevComposition = iPrevComposition->second;
    bool compositionChanged = false;
    if (numLayers == prevComposition.size()) {
        for (int i = 0; i < numLayers; i++) {
            if (composeBuffers[i] == nullptr) {
                // If the display buffer of the current layer doesn't exist, we
                // check if the layer at the same index in the previous
                // composition doesn't exist either.
                if (prevComposition[i] == nullptr) {
                    continue;
                } else {
                    compositionChanged = true;
                    break;
                }
            }
            if (prevComposition[i] == nullptr) {
                // If the display buffer of the current layer exists but the
                // layer at the same index in the previous composition doesn't
                // exist, the composition is changed.
                compositionChanged = true;
                break;
            }
            const auto& prevLayer = *prevComposition[i];
            const auto prevDisplayBufferPtr = prevLayer.m_displayBuffer.lock();
            // prevLayer.m_displayBuffer is a weak pointer, so if
            // prevDisplayBufferPtr is null, the color buffer
            // prevDisplayBufferPtr pointed to should have been released or
            // re-allocated, and we should consider the composition is changed.
            // If prevDisplayBufferPtr exists and it points to the same display
            // buffer as the input composeBuffers[i] we consider the composition
            // not changed.
            if (!prevDisplayBufferPtr || prevDisplayBufferPtr != composeBuffers[i]) {
                compositionChanged = true;
                break;
            }
            const auto& prevHwc2Layer = prevLayer.m_hwc2Layer;
            const auto hwc2Layer = layers[i];
            compositionChanged =
                (prevHwc2Layer.cbHandle != hwc2Layer.cbHandle) ||
                (prevHwc2Layer.composeMode != hwc2Layer.composeMode) ||
                (prevHwc2Layer.displayFrame.left != hwc2Layer.displayFrame.left) ||
                (prevHwc2Layer.displayFrame.top != hwc2Layer.displayFrame.top) ||
                (prevHwc2Layer.displayFrame.right != hwc2Layer.displayFrame.right) ||
                (prevHwc2Layer.displayFrame.bottom != hwc2Layer.displayFrame.bottom) ||
                (prevHwc2Layer.crop.left != hwc2Layer.crop.left) ||
                (prevHwc2Layer.crop.top != hwc2Layer.crop.top) ||
                (prevHwc2Layer.crop.right != hwc2Layer.crop.right) ||
                (prevHwc2Layer.crop.bottom != hwc2Layer.crop.bottom) ||
                (prevHwc2Layer.blendMode != hwc2Layer.blendMode) ||
                (prevHwc2Layer.alpha != hwc2Layer.alpha) ||
                (prevHwc2Layer.color.r != hwc2Layer.color.r) ||
                (prevHwc2Layer.color.g != hwc2Layer.color.g) ||
                (prevHwc2Layer.color.b != hwc2Layer.color.b) ||
                (prevHwc2Layer.color.a != hwc2Layer.color.a) ||
                (prevHwc2Layer.transform != hwc2Layer.transform);
            if (compositionChanged) {
                break;
            }
        }
    } else {
        compositionChanged = true;
    }
    bool needsSave = compositionNotFound || compositionChanged;
    if (needsSave) {
        prevComposition.clear();
        for (int i = 0; i < numLayers; i++) {
            if (composeBuffers[i] == nullptr) {
                prevComposition.emplace_back(nullptr);
                continue;
            }
            auto layer = std::make_unique<SurfaceState::Layer>();
            layer->m_hwc2Layer = layers[i];
            layer->m_displayBuffer = composeBuffers[i];
            prevComposition.emplace_back(std::move(layer));
        }
    }
    return needsSave;
}

DisplayVk::DisplayBufferInfo::DisplayBufferInfo(const goldfish_vk::VulkanDispatch& vk,
                                                VkDevice vkDevice,
                                                const VkImageCreateInfo& vkImageCreateInfo,
                                                VkImage image)
    : m_vk(vk),
      m_vkDevice(vkDevice),
      m_vkImageCreateInfo(vk_make_orphan_copy(vkImageCreateInfo)),
      m_vkImage(image),
      m_vkImageView(VK_NULL_HANDLE),
      m_compositorVkRenderTarget() {
    VkImageViewCreateInfo imageViewCi = {
        .sType = VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO,
        .image = image,
        .viewType = VK_IMAGE_VIEW_TYPE_2D,
        .format = m_vkImageCreateInfo.format,
        .components = {.r = VK_COMPONENT_SWIZZLE_IDENTITY,
                       .g = VK_COMPONENT_SWIZZLE_IDENTITY,
                       .b = VK_COMPONENT_SWIZZLE_IDENTITY,
                       .a = VK_COMPONENT_SWIZZLE_IDENTITY},
        .subresourceRange = {.aspectMask = VK_IMAGE_ASPECT_COLOR_BIT,
                             .baseMipLevel = 0,
                             .levelCount = 1,
                             .baseArrayLayer = 0,
                             .layerCount = 1}};
    VK_CHECK(m_vk.vkCreateImageView(m_vkDevice, &imageViewCi, nullptr, &m_vkImageView));
}

DisplayVk::DisplayBufferInfo::~DisplayBufferInfo() {
    m_vk.vkDestroyImageView(m_vkDevice, m_vkImageView, nullptr);
}

std::shared_ptr<DisplayVk::PostResource> DisplayVk::PostResource::create(
    const goldfish_vk::VulkanDispatch &vk, VkDevice vkDevice, VkCommandPool vkCommandPool) {
    VkFenceCreateInfo fenceCi = {
        .sType = VK_STRUCTURE_TYPE_FENCE_CREATE_INFO,
    };
    VkFence fence;
    VK_CHECK(vk.vkCreateFence(vkDevice, &fenceCi, nullptr, &fence));
    VkSemaphore semaphores[2];
    for (uint32_t i = 0; i < std::size(semaphores); i++) {
        VkSemaphoreCreateInfo semaphoreCi = {
            .sType = VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO,
        };
        VK_CHECK(vk.vkCreateSemaphore(vkDevice, &semaphoreCi, nullptr, &semaphores[i]));
    }
    VkCommandBuffer commandBuffer;
    VkCommandBufferAllocateInfo commandBufferAllocInfo = {
        .sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO,
        .commandPool = vkCommandPool,
        .level = VK_COMMAND_BUFFER_LEVEL_PRIMARY,
        .commandBufferCount = 1,
    };
    VK_CHECK(vk.vkAllocateCommandBuffers(vkDevice, &commandBufferAllocInfo, &commandBuffer));
    return std::shared_ptr<PostResource>(new PostResource(
        vk, vkDevice, vkCommandPool, fence, semaphores[0], semaphores[1], commandBuffer));
}

DisplayVk::PostResource::~PostResource() {
    m_vk.vkFreeCommandBuffers(m_vkDevice, m_vkCommandPool, 1, &m_vkCommandBuffer);
    m_vk.vkDestroyFence(m_vkDevice, m_swapchainImageReleaseFence, nullptr);
    m_vk.vkDestroySemaphore(m_vkDevice, m_swapchainImageAcquireSemaphore, nullptr);
    m_vk.vkDestroySemaphore(m_vkDevice, m_swapchainImageReleaseSemaphore, nullptr);
}

DisplayVk::PostResource::PostResource(const goldfish_vk::VulkanDispatch& vk, VkDevice vkDevice,
                                      VkCommandPool vkCommandPool,
                                      VkFence swapchainImageReleaseFence,
                                      VkSemaphore swapchainImageAcquireSemaphore,
                                      VkSemaphore swapchainImageReleaseSemaphore,
                                      VkCommandBuffer vkCommandBuffer)
    : m_swapchainImageReleaseFence(swapchainImageReleaseFence),
      m_swapchainImageAcquireSemaphore(swapchainImageAcquireSemaphore),
      m_swapchainImageReleaseSemaphore(swapchainImageReleaseSemaphore),
      m_vkCommandBuffer(vkCommandBuffer),
      m_vk(vk),
      m_vkDevice(vkDevice),
      m_vkCommandPool(vkCommandPool) {}

std::unique_ptr<DisplayVk::ComposeResource> DisplayVk::ComposeResource::create(
    const goldfish_vk::VulkanDispatch &vk, VkDevice vkDevice, VkCommandPool vkCommandPool) {
    VkFenceCreateInfo fenceCi = {
        .sType = VK_STRUCTURE_TYPE_FENCE_CREATE_INFO,
    };
    VkFence fence;
    VK_CHECK(vk.vkCreateFence(vkDevice, &fenceCi, nullptr, &fence));

    VkCommandBuffer commandBuffer;
    VkCommandBufferAllocateInfo commandBufferAllocInfo = {
        .sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO,
        .commandPool = vkCommandPool,
        .level = VK_COMMAND_BUFFER_LEVEL_PRIMARY,
        .commandBufferCount = 1,
    };
    VK_CHECK(vk.vkAllocateCommandBuffers(vkDevice, &commandBufferAllocInfo, &commandBuffer));

    return std::unique_ptr<ComposeResource>(
        new ComposeResource(vk, vkDevice, vkCommandPool, fence, commandBuffer));
}

DisplayVk::ComposeResource::~ComposeResource() {
    m_vk.vkFreeCommandBuffers(m_vkDevice, m_vkCommandPool, 1, &m_vkCommandBuffer);
    m_vk.vkDestroyFence(m_vkDevice, m_composeCompleteFence, nullptr);
}

DisplayVk::ComposeResource::ComposeResource(const goldfish_vk::VulkanDispatch& vk,
                                            VkDevice vkDevice, VkCommandPool vkCommandPool,
                                            VkFence composeCompleteFence,
                                            VkCommandBuffer vkCommandBuffer)
    : m_composeCompleteFence(composeCompleteFence),
      m_vkCommandBuffer(vkCommandBuffer),
      m_vk(vk),
      m_vkDevice(vkDevice),
      m_vkCommandPool(vkCommandPool) {}
