#ifndef DISPLAY_VK_H
#define DISPLAY_VK_H

#include <deque>
#include <functional>
#include <future>
#include <memory>
#include <optional>
#include <tuple>
#include <unordered_map>
#include <unordered_set>

#include "CompositorVk.h"
#include "Hwc2.h"
#include "RenderContext.h"
#include "SwapChainStateVk.h"
#include "base/Lock.h"
#include "vulkan/cereal/common/goldfish_vk_dispatch.h"

// The DisplayVk class holds the Vulkan and other states required to draw a
// frame in a host window.

class DisplayVk {
   public:
    class DisplayBufferInfo {
       public:
        ~DisplayBufferInfo();

       private:
        DisplayBufferInfo(const goldfish_vk::VulkanDispatch &, VkDevice, const VkImageCreateInfo &,
                          VkImage);

        const goldfish_vk::VulkanDispatch &m_vk;
        VkDevice m_vkDevice;
        VkImageCreateInfo m_vkImageCreateInfo;

        VkImage m_vkImage;
        VkImageView m_vkImageView;
        // m_compositorVkRenderTarget will be created when the first time the ColorBuffer is used as
        // the render target of DisplayVk::compose. DisplayVk owns the m_compositorVkRenderTarget so
        // that when the CompositorVk is recreated m_compositorVkRenderTarget can be restored to
        // nullptr.
        std::weak_ptr<CompositorVkRenderTarget> m_compositorVkRenderTarget;

        friend class DisplayVk;
    };
    DisplayVk(const goldfish_vk::VulkanDispatch &, VkPhysicalDevice,
              uint32_t swapChainQueueFamilyIndex, uint32_t compositorQueueFamilyIndex, VkDevice,
              VkQueue compositorVkQueue, std::shared_ptr<android::base::Lock> compositorVkQueueLock,
              VkQueue swapChainVkQueue, std::shared_ptr<android::base::Lock> swapChainVkQueueLock);
    ~DisplayVk();
    void bindToSurface(VkSurfaceKHR, uint32_t width, uint32_t height);
    // The caller is responsible to make sure the VkImage lives longer than the DisplayBufferInfo
    // created here.
    std::shared_ptr<DisplayBufferInfo> createDisplayBuffer(VkImage, const VkImageCreateInfo &);
    // The first component of the returned tuple is false when the swapchain is no longer valid and
    // bindToSurface() needs to be called again. When the first component is true, the second
    // component of the returned tuple is a/ future that will complete when the GPU side of work
    // completes. The caller is responsible to guarantee the synchronization and the layout of
    // DisplayBufferInfo::m_vkImage is VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL.
    std::tuple<bool, std::shared_future<void>> post(std::shared_ptr<DisplayBufferInfo>);

    // dstWidth and dstHeight describe the size of the render target the guest
    // "thinks" it composes to, essentially, the virtual display size. Note that
    // this can be different from the actual window size. The first component of
    // the returned tuple is false when the swapchain is no longer valid and
    // bindToSurface() needs to be called again. When the first component is
    // true, the second component of the returned tuple is a future that will
    // complete when the GPU side of work completes.
    std::tuple<bool, std::shared_future<void>> compose(
        uint32_t numLayers, const ComposeLayer layers[],
        std::vector<std::shared_ptr<DisplayBufferInfo>> composeBuffers,
        std::shared_ptr<DisplayBufferInfo> renderTarget);

   private:
    VkFormatFeatureFlags getFormatFeatures(VkFormat, VkImageTiling);
    bool canPost(const VkImageCreateInfo &);
    // Check if the VkImage can be used as the compose layer to be sampled from.
    bool canCompositeFrom(const VkImageCreateInfo &);
    // Check if the VkImage can be used as the render target of the composition.
    bool canCompositeTo(const VkImageCreateInfo &);
    // Returns if the composition specified by the parameter is different from
    // the previous composition. If the composition is different, update the
    // previous composition stored in m_surfaceState. Must be called after
    // bindToSurface() is called.
    bool compareAndSaveComposition(
        uint32_t renderTargetIndex, uint32_t numLayers, const ComposeLayer layers[],
        const std::vector<std::shared_ptr<DisplayBufferInfo>> &composeBuffers);

    const goldfish_vk::VulkanDispatch &m_vk;
    VkPhysicalDevice m_vkPhysicalDevice;
    uint32_t m_swapChainQueueFamilyIndex;
    uint32_t m_compositorQueueFamilyIndex;
    VkDevice m_vkDevice;
    VkQueue m_compositorVkQueue;
    std::shared_ptr<android::base::Lock> m_compositorVkQueueLock;
    VkQueue m_swapChainVkQueue;
    std::shared_ptr<android::base::Lock> m_swapChainVkQueueLock;
    VkCommandPool m_vkCommandPool;
    VkSampler m_compositionVkSampler;

    class PostResource {
       public:
        const VkFence m_swapchainImageReleaseFence;
        const VkSemaphore m_swapchainImageAcquireSemaphore;
        const VkSemaphore m_swapchainImageReleaseSemaphore;
        const VkCommandBuffer m_vkCommandBuffer;
        static std::shared_ptr<PostResource> create(const goldfish_vk::VulkanDispatch &, VkDevice,
                                                    VkCommandPool);
        ~PostResource();
        DISALLOW_COPY_ASSIGN_AND_MOVE(PostResource);

       private:
        PostResource(const goldfish_vk::VulkanDispatch &, VkDevice, VkCommandPool,
                     VkFence swapchainImageReleaseFence, VkSemaphore swapchainImageAcquireSemaphore,
                     VkSemaphore swapchainImageReleaseSemaphore, VkCommandBuffer);
        const goldfish_vk::VulkanDispatch &m_vk;
        const VkDevice m_vkDevice;
        const VkCommandPool m_vkCommandPool;
    };

    std::deque<std::shared_ptr<PostResource>> m_freePostResources;
    std::optional<std::shared_future<std::shared_ptr<PostResource>>> m_postResourceFuture;

    class ComposeResource {
       public:
        const VkFence m_composeCompleteFence;
        const VkCommandBuffer m_vkCommandBuffer;
        static std::unique_ptr<ComposeResource> create(const goldfish_vk::VulkanDispatch &,
                                                       VkDevice, VkCommandPool);
        ~ComposeResource();
        DISALLOW_COPY_ASSIGN_AND_MOVE(ComposeResource);

       private:
        ComposeResource(const goldfish_vk::VulkanDispatch &, VkDevice, VkCommandPool, VkFence,
                        VkCommandBuffer);
        const goldfish_vk::VulkanDispatch &m_vk;
        const VkDevice m_vkDevice;
        const VkCommandPool m_vkCommandPool;
    };

    int m_inFlightFrameIndex;
    std::optional<std::future<std::unique_ptr<ComposeResource>>> m_composeResourceFuture;

    std::unique_ptr<SwapChainStateVk> m_swapChainStateVk;
    std::unique_ptr<CompositorVk> m_compositorVk;
    static constexpr uint32_t k_compositorVkRenderTargetCacheSize = 128;
    std::deque<std::shared_ptr<CompositorVkRenderTarget>> m_compositorVkRenderTargets;
    static constexpr VkFormat k_compositorVkRenderTargetFormat = VK_FORMAT_R8G8B8A8_UNORM;
    struct SurfaceState {
        struct Layer {
            ComposeLayer m_hwc2Layer;
            std::weak_ptr<DisplayBufferInfo> m_displayBuffer;
        };

        uint32_t m_width = 0;
        uint32_t m_height = 0;
        std::unordered_map<uint32_t, std::vector<std::unique_ptr<Layer>>> m_prevCompositions;
    };
    std::unique_ptr<SurfaceState> m_surfaceState;

    std::unordered_map<VkFormat, VkFormatProperties> m_vkFormatProperties;
};

#endif
