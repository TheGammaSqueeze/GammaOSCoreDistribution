#include <gtest/gtest.h>

#include "CompositorVk.h"

#include <algorithm>
#include <array>
#include <glm/gtx/matrix_transform_2d.hpp>
#include <memory>
#include <optional>

#include "base/Lock.h"
#include "tests/VkTestUtils.h"
#include "vulkan/VulkanDispatch.h"
#include "vulkan/vk_util.h"

class CompositorVkTest : public ::testing::Test {
   protected:
    using RenderTarget = emugl::RenderResourceVk<VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL,
                                                 VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT>;
    using RenderTexture = emugl::RenderTextureVk;

    static void SetUpTestCase() { k_vk = emugl::vkDispatch(false); }

    static constexpr uint32_t k_numOfRenderTargets = 10;
    static constexpr uint32_t k_renderTargetWidth = 255;
    static constexpr uint32_t k_renderTargetHeight = 255;
    static constexpr uint32_t k_renderTargetNumOfPixels =
        k_renderTargetWidth * k_renderTargetHeight;

    void SetUp() override {
        ASSERT_NE(k_vk, nullptr);
        createInstance();
        pickPhysicalDevice();
        createLogicalDevice();

        VkFormatProperties formatProperties;
        k_vk->vkGetPhysicalDeviceFormatProperties(m_vkPhysicalDevice, RenderTarget::k_vkFormat,
                                                  &formatProperties);
        if (!(formatProperties.optimalTilingFeatures & VK_FORMAT_FEATURE_COLOR_ATTACHMENT_BIT)) {
            GTEST_SKIP();
        }
        k_vk->vkGetPhysicalDeviceFormatProperties(m_vkPhysicalDevice, RenderTexture::k_vkFormat,
                                                  &formatProperties);
        if (!(formatProperties.optimalTilingFeatures & VK_FORMAT_FEATURE_SAMPLED_IMAGE_BIT)) {
            GTEST_SKIP();
        }

        VkCommandPoolCreateInfo commandPoolCi = {
            .sType = VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO,
            .queueFamilyIndex = m_compositorQueueFamilyIndex};
        ASSERT_EQ(k_vk->vkCreateCommandPool(m_vkDevice, &commandPoolCi, nullptr, &m_vkCommandPool),
                  VK_SUCCESS);

        VkCommandBufferAllocateInfo cmdBuffAllocInfo = {
            .sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO,
            .commandPool = m_vkCommandPool,
            .level = VK_COMMAND_BUFFER_LEVEL_PRIMARY,
            .commandBufferCount = k_numOfRenderTargets};
        m_vkCommandBuffers.resize(k_numOfRenderTargets);
        VK_CHECK(k_vk->vkAllocateCommandBuffers(m_vkDevice, &cmdBuffAllocInfo,
                                                m_vkCommandBuffers.data()));

        k_vk->vkGetDeviceQueue(m_vkDevice, m_compositorQueueFamilyIndex, 0, &m_compositorVkQueue);
        ASSERT_TRUE(m_compositorVkQueue != VK_NULL_HANDLE);

        m_compositorVkQueueLock = std::make_shared<android::base::Lock>();

        for (uint32_t i = 0; i < k_numOfRenderTargets; i++) {
            auto renderTarget =
                RenderTarget::create(*k_vk, m_vkDevice, m_vkPhysicalDevice, m_compositorVkQueue,
                                     m_vkCommandPool, k_renderTargetHeight, k_renderTargetWidth);
            ASSERT_NE(renderTarget, nullptr);
            m_renderTargets.emplace_back(std::move(renderTarget));
        }

        m_renderTargetImageViews.resize(m_renderTargets.size());
        ASSERT_EQ(std::transform(m_renderTargets.begin(), m_renderTargets.end(),
                                 m_renderTargetImageViews.begin(),
                                 [](const std::unique_ptr<const RenderTarget> &renderTarget) {
                                     return renderTarget->m_vkImageView;
                                 }),
                  m_renderTargetImageViews.end());
        setUpRGBASampler();
    }

    void TearDown() override {
        k_vk->vkDestroySampler(m_vkDevice, m_rgbaVkSampler, nullptr);
        k_vk->vkFreeCommandBuffers(m_vkDevice, m_vkCommandPool, m_vkCommandBuffers.size(),
                                   m_vkCommandBuffers.data());
        m_vkCommandBuffers.clear();
        m_renderTargets.clear();
        k_vk->vkDestroyCommandPool(m_vkDevice, m_vkCommandPool, nullptr);
        k_vk->vkDestroyDevice(m_vkDevice, nullptr);
        m_vkDevice = VK_NULL_HANDLE;
        k_vk->vkDestroyInstance(m_vkInstance, nullptr);
        m_vkInstance = VK_NULL_HANDLE;
    }

    std::unique_ptr<CompositorVk> createCompositor() {
        return CompositorVk::create(
            *k_vk, m_vkDevice, m_vkPhysicalDevice, m_compositorVkQueue, m_compositorVkQueueLock,
            RenderTarget::k_vkFormat, RenderTarget::k_vkImageLayout, RenderTarget::k_vkImageLayout,
            m_renderTargetImageViews.size(), m_vkCommandPool, m_rgbaVkSampler);
    }

    std::vector<std::unique_ptr<CompositorVkRenderTarget>> createCompositorRenderTargets(
        CompositorVk &compositor) {
        std::vector<std::unique_ptr<CompositorVkRenderTarget>> res;
        for (VkImageView imageView : m_renderTargetImageViews) {
            res.emplace_back(compositor.createRenderTarget(imageView, k_renderTargetWidth,
                                                           k_renderTargetHeight));
        }
        return res;
    }

    void setUpRGBASampler() {
        VkSamplerCreateInfo samplerCi = {.sType = VK_STRUCTURE_TYPE_SAMPLER_CREATE_INFO,
                                         .magFilter = VK_FILTER_NEAREST,
                                         .minFilter = VK_FILTER_NEAREST,
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
        VK_CHECK(k_vk->vkCreateSampler(m_vkDevice, &samplerCi, nullptr, &m_rgbaVkSampler));
    }

    static const goldfish_vk::VulkanDispatch *k_vk;
    VkInstance m_vkInstance = VK_NULL_HANDLE;
    VkPhysicalDevice m_vkPhysicalDevice = VK_NULL_HANDLE;
    uint32_t m_compositorQueueFamilyIndex = 0;
    VkDevice m_vkDevice = VK_NULL_HANDLE;
    std::vector<std::unique_ptr<const RenderTarget>> m_renderTargets;
    std::vector<VkImageView> m_renderTargetImageViews;
    VkCommandPool m_vkCommandPool = VK_NULL_HANDLE;
    VkQueue m_compositorVkQueue = VK_NULL_HANDLE;
    std::shared_ptr<android::base::Lock> m_compositorVkQueueLock;
    std::vector<VkCommandBuffer> m_vkCommandBuffers;
    VkSampler m_rgbaVkSampler = VK_NULL_HANDLE;

   private:
    void createInstance() {
        VkApplicationInfo appInfo = {.sType = VK_STRUCTURE_TYPE_APPLICATION_INFO,
                                     .pNext = nullptr,
                                     .pApplicationName = "emulator CompositorVk unittest",
                                     .applicationVersion = VK_MAKE_VERSION(1, 0, 0),
                                     .pEngineName = "No Engine",
                                     .engineVersion = VK_MAKE_VERSION(1, 0, 0),
                                     .apiVersion = VK_API_VERSION_1_1};
        VkInstanceCreateInfo instanceCi = {.sType = VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO,
                                           .pApplicationInfo = &appInfo,
                                           .enabledExtensionCount = 0,
                                           .ppEnabledExtensionNames = nullptr};
        ASSERT_EQ(k_vk->vkCreateInstance(&instanceCi, nullptr, &m_vkInstance), VK_SUCCESS);
        ASSERT_TRUE(m_vkInstance != VK_NULL_HANDLE);
    }

    void pickPhysicalDevice() {
        uint32_t physicalDeviceCount = 0;
        ASSERT_EQ(k_vk->vkEnumeratePhysicalDevices(m_vkInstance, &physicalDeviceCount, nullptr),
                  VK_SUCCESS);
        ASSERT_GT(physicalDeviceCount, 0);
        std::vector<VkPhysicalDevice> physicalDevices(physicalDeviceCount);
        ASSERT_EQ(k_vk->vkEnumeratePhysicalDevices(m_vkInstance, &physicalDeviceCount,
                                                   physicalDevices.data()),
                  VK_SUCCESS);
        for (const auto &device : physicalDevices) {
            uint32_t queueFamilyCount = 0;
            k_vk->vkGetPhysicalDeviceQueueFamilyProperties(device, &queueFamilyCount, nullptr);
            ASSERT_GT(queueFamilyCount, 0);
            std::vector<VkQueueFamilyProperties> queueFamilyProperties(queueFamilyCount);
            k_vk->vkGetPhysicalDeviceQueueFamilyProperties(device, &queueFamilyCount,
                                                           queueFamilyProperties.data());
            uint32_t queueFamilyIndex = 0;
            for (; queueFamilyIndex < queueFamilyCount; queueFamilyIndex++) {
                if (CompositorVk::validateQueueFamilyProperties(
                        queueFamilyProperties[queueFamilyIndex])) {
                    break;
                }
            }
            if (queueFamilyIndex == queueFamilyCount) {
                continue;
            }

            m_compositorQueueFamilyIndex = queueFamilyIndex;
            m_vkPhysicalDevice = device;
            return;
        }
        FAIL() << "Can't find a suitable VkPhysicalDevice.";
    }

    void createLogicalDevice() {
        const float queuePriority = 1.0f;
        VkDeviceQueueCreateInfo queueCi = {.sType = VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO,
                                           .queueFamilyIndex = m_compositorQueueFamilyIndex,
                                           .queueCount = 1,
                                           .pQueuePriorities = &queuePriority};
        VkPhysicalDeviceFeatures2 features = {.sType = VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_FEATURES_2,
                                              .pNext = nullptr};
        VkDeviceCreateInfo deviceCi = {.sType = VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO,
                                       .pNext = &features,
                                       .queueCreateInfoCount = 1,
                                       .pQueueCreateInfos = &queueCi,
                                       .enabledLayerCount = 0,
                                       .enabledExtensionCount = 0,
                                       .ppEnabledExtensionNames = nullptr};
        ASSERT_EQ(k_vk->vkCreateDevice(m_vkPhysicalDevice, &deviceCi, nullptr, &m_vkDevice),
                  VK_SUCCESS);
        ASSERT_TRUE(m_vkDevice != VK_NULL_HANDLE);
    }
};

const goldfish_vk::VulkanDispatch *CompositorVkTest::k_vk = nullptr;

TEST_F(CompositorVkTest, Init) { ASSERT_NE(createCompositor(), nullptr); }

TEST_F(CompositorVkTest, ValidateQueueFamilyProperties) {
    VkQueueFamilyProperties properties = {};
    properties.queueFlags &= ~VK_QUEUE_GRAPHICS_BIT;
    ASSERT_FALSE(CompositorVk::validateQueueFamilyProperties(properties));
    properties.queueFlags |= VK_QUEUE_GRAPHICS_BIT;
    ASSERT_TRUE(CompositorVk::validateQueueFamilyProperties(properties));
}

TEST_F(CompositorVkTest, EmptyCompositionShouldDrawABlackFrame) {
    std::vector<uint32_t> pixels(k_renderTargetNumOfPixels);
    for (uint32_t i = 0; i < k_renderTargetNumOfPixels; i++) {
        uint8_t v = static_cast<uint8_t>((i / 4) & 0xff);
        uint8_t *pixel = reinterpret_cast<uint8_t *>(&pixels[i]);
        pixel[0] = v;
        pixel[1] = v;
        pixel[2] = v;
        pixel[3] = 0xff;
    }
    for (uint32_t i = 0; i < k_numOfRenderTargets; i++) {
        ASSERT_TRUE(m_renderTargets[i]->write(pixels));
        auto maybeImageBytes = m_renderTargets[i]->read();
        ASSERT_TRUE(maybeImageBytes.has_value());
        for (uint32_t i = 0; i < k_renderTargetNumOfPixels; i++) {
            ASSERT_EQ(pixels[i], maybeImageBytes.value()[i]);
        }
    }

    auto compositor = createCompositor();
    auto renderTargets = createCompositorRenderTargets(*compositor);
    ASSERT_NE(compositor, nullptr);

    // render to render targets with event index
    std::vector<VkCommandBuffer> cmdBuffs = {};
    for (uint32_t i = 0; i < k_numOfRenderTargets; i++) {
        VkCommandBufferBeginInfo beginInfo = {
            .sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO,
            .flags = VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT,
        };
        VK_CHECK(k_vk->vkBeginCommandBuffer(m_vkCommandBuffers[i], &beginInfo));
        compositor->recordCommandBuffers(i, m_vkCommandBuffers[i], *renderTargets[i]);
        VK_CHECK(k_vk->vkEndCommandBuffer(m_vkCommandBuffers[i]));
        if (i % 2 == 0) {
            cmdBuffs.emplace_back(m_vkCommandBuffers[i]);
        }
    }
    VkSubmitInfo submitInfo = {.sType = VK_STRUCTURE_TYPE_SUBMIT_INFO,
                               .commandBufferCount = static_cast<uint32_t>(cmdBuffs.size()),
                               .pCommandBuffers = cmdBuffs.data()};
    ASSERT_EQ(k_vk->vkQueueSubmit(m_compositorVkQueue, 1, &submitInfo, VK_NULL_HANDLE), VK_SUCCESS);

    ASSERT_EQ(k_vk->vkQueueWaitIdle(m_compositorVkQueue), VK_SUCCESS);
    for (uint32_t i = 0; i < k_numOfRenderTargets; i++) {
        auto maybeImagePixels = m_renderTargets[i]->read();
        ASSERT_TRUE(maybeImagePixels.has_value());
        const auto &imagePixels = maybeImagePixels.value();
        for (uint32_t j = 0; j < k_renderTargetNumOfPixels; j++) {
            const auto pixel = reinterpret_cast<const uint8_t *>(&imagePixels[j]);
            // should only render to render targets with even index
            if (i % 2 == 0) {
                ASSERT_EQ(pixel[0], 0);
                ASSERT_EQ(pixel[1], 0);
                ASSERT_EQ(pixel[2], 0);
                ASSERT_EQ(pixel[3], 0xff);
            } else {
                ASSERT_EQ(pixels[j], imagePixels[j]);
            }
        }
    }
}

TEST_F(CompositorVkTest, SimpleComposition) {
    constexpr uint32_t textureLeft = 30;
    constexpr uint32_t textureRight = 50;
    constexpr uint32_t textureTop = 10;
    constexpr uint32_t textureBottom = 40;
    constexpr uint32_t textureWidth = textureRight - textureLeft;
    constexpr uint32_t textureHeight = textureBottom - textureTop;
    auto texture = RenderTexture::create(*k_vk, m_vkDevice, m_vkPhysicalDevice, m_compositorVkQueue,
                                         m_vkCommandPool, textureWidth, textureHeight);
    uint32_t textureColor;
    uint8_t *textureColor_ = reinterpret_cast<uint8_t *>(&textureColor);
    textureColor_[0] = 0xff;
    textureColor_[1] = 0;
    textureColor_[2] = 0;
    textureColor_[3] = 0xff;
    std::vector<uint32_t> pixels(textureWidth * textureHeight, textureColor);
    ASSERT_TRUE(texture->write(pixels));
    auto compositor = createCompositor();
    ASSERT_NE(compositor, nullptr);

    ComposeLayer composeLayer = {
        0 /* No color buffer handle */,
        HWC2_COMPOSITION_DEVICE,
        {
            /* frame in which the texture shows up on the display */
            textureLeft,
            textureTop,
            textureRight,
            textureBottom,
        },
        {
            /* how much of the texture itself to show */
            0.0,
            0.0,
            static_cast<float>(textureWidth),
            static_cast<float>(textureHeight),
        },
        HWC2_BLEND_MODE_PREMULTIPLIED /* blend mode */,
        1.0 /* alpha */,
        {0, 0, 0, 0} /* color (N/A) */,
        HWC_TRANSFORM_NONE /* transform (no rotation */,
    };

    std::unique_ptr<ComposeLayerVk> composeLayerVkPtr = ComposeLayerVk::createFromHwc2ComposeLayer(
        m_rgbaVkSampler, texture->m_vkImageView, composeLayer, textureWidth, textureHeight,
        k_renderTargetWidth, k_renderTargetHeight);

    std::vector<std::unique_ptr<ComposeLayerVk>> layers;
    layers.emplace_back(std::move(composeLayerVkPtr));

    auto composition = std::make_unique<Composition>(std::move(layers));
    auto renderTargets = createCompositorRenderTargets(*compositor);
    compositor->setComposition(0, std::move(composition));

    VkCommandBuffer cmdBuff = m_vkCommandBuffers[0];
    VkCommandBufferBeginInfo beginInfo = {
        .sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO,
        .flags = VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT,
    };
    VK_CHECK(k_vk->vkBeginCommandBuffer(cmdBuff, &beginInfo));
    compositor->recordCommandBuffers(0, cmdBuff, *renderTargets[0]);
    VK_CHECK(k_vk->vkEndCommandBuffer(cmdBuff));

    VkSubmitInfo submitInfo = {.sType = VK_STRUCTURE_TYPE_SUBMIT_INFO,
                               .commandBufferCount = 1,
                               .pCommandBuffers = &cmdBuff};
    ASSERT_EQ(k_vk->vkQueueSubmit(m_compositorVkQueue, 1, &submitInfo, VK_NULL_HANDLE), VK_SUCCESS);
    ASSERT_EQ(k_vk->vkQueueWaitIdle(m_compositorVkQueue), VK_SUCCESS);

    auto maybeImagePixels = m_renderTargets[0]->read();
    ASSERT_TRUE(maybeImagePixels.has_value());
    const auto &imagePixels = maybeImagePixels.value();

    for (uint32_t i = 0; i < k_renderTargetHeight; i++) {
        for (uint32_t j = 0; j < k_renderTargetWidth; j++) {
            uint32_t offset = i * k_renderTargetWidth + j;
            const uint8_t *pixel = reinterpret_cast<const uint8_t *>(&imagePixels[offset]);
            EXPECT_EQ(pixel[1], 0);
            EXPECT_EQ(pixel[2], 0);
            EXPECT_EQ(pixel[3], 0xff);
            if (i >= textureTop && i < textureBottom && j >= textureLeft && j < textureRight) {
                EXPECT_EQ(pixel[0], 0xff);
            } else {
                EXPECT_EQ(pixel[0], 0);
            }
        }
    }
}

TEST_F(CompositorVkTest, CompositingWithDifferentCompositionOnMultipleTargets) {
    constexpr uint32_t textureWidth = 20;
    constexpr uint32_t textureHeight = 30;

    ComposeLayer defaultComposeLayer = {
        0 /* No color buffer handle */,
        HWC2_COMPOSITION_DEVICE,
        {
            /* display frame (to be replaced for each of k_numOfRenderTargets */
            0,
            0,
            0,
            0,
        },
        {
            /* how much of the texture itself to show */
            0.0,
            0.0,
            static_cast<float>(textureWidth),
            static_cast<float>(textureHeight),
        },
        HWC2_BLEND_MODE_PREMULTIPLIED /* blend mode */,
        1.0 /* alpha */,
        {0, 0, 0, 0} /* color (N/A) */,
        HWC_TRANSFORM_NONE /* transform (no rotation */,
    };

    std::vector<ComposeLayer> composeLayers(k_numOfRenderTargets);
    for (int i = 0; i < k_numOfRenderTargets; i++) {
        composeLayers[i] = defaultComposeLayer;
        int left = (i * 30) % (k_renderTargetWidth - textureWidth);
        int top = (i * 20) % (k_renderTargetHeight - textureHeight);
        int right = left + textureWidth;
        int bottom = top + textureHeight;
        composeLayers[i].displayFrame = {
            left,
            top,
            right,
            bottom,
        };
    }

    auto texture = RenderTexture::create(*k_vk, m_vkDevice, m_vkPhysicalDevice, m_compositorVkQueue,
                                         m_vkCommandPool, textureWidth, textureHeight);
    uint32_t textureColor;
    uint8_t *textureColor_ = reinterpret_cast<uint8_t *>(&textureColor);
    textureColor_[0] = 0xff;
    textureColor_[1] = 0;
    textureColor_[2] = 0;
    textureColor_[3] = 0xff;
    std::vector<uint32_t> pixels(textureWidth * textureHeight, textureColor);
    ASSERT_TRUE(texture->write(pixels));
    auto compositor = createCompositor();
    ASSERT_NE(compositor, nullptr);
    auto renderTargets = createCompositorRenderTargets(*compositor);
    for (int i = 0; i < k_numOfRenderTargets; i++) {
        std::unique_ptr<ComposeLayerVk> composeLayerVkPtr =
            ComposeLayerVk::createFromHwc2ComposeLayer(
                m_rgbaVkSampler, texture->m_vkImageView, composeLayers[i], textureWidth,
                textureHeight, k_renderTargetWidth, k_renderTargetHeight);

        std::vector<std::unique_ptr<ComposeLayerVk>> layers;
        layers.emplace_back(std::move(composeLayerVkPtr));

        auto composition = std::make_unique<Composition>(std::move(layers));
        compositor->setComposition(i, std::move(composition));

        VkCommandBuffer cmdBuff = m_vkCommandBuffers[i];
        VkCommandBufferBeginInfo beginInfo = {
            .sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO,
            .flags = VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT,
        };
        VK_CHECK(k_vk->vkBeginCommandBuffer(cmdBuff, &beginInfo));
        compositor->recordCommandBuffers(i, cmdBuff, *renderTargets[i]);
        VK_CHECK(k_vk->vkEndCommandBuffer(cmdBuff));

        VkSubmitInfo submitInfo = {.sType = VK_STRUCTURE_TYPE_SUBMIT_INFO,
                                   .commandBufferCount = 1,
                                   .pCommandBuffers = &cmdBuff};
        ASSERT_EQ(k_vk->vkQueueSubmit(m_compositorVkQueue, 1, &submitInfo, VK_NULL_HANDLE),
                  VK_SUCCESS);
        ASSERT_EQ(k_vk->vkQueueWaitIdle(m_compositorVkQueue), VK_SUCCESS);

        auto maybeImagePixels = m_renderTargets[i]->read();
        ASSERT_TRUE(maybeImagePixels.has_value());
        const auto &imagePixels = maybeImagePixels.value();

        for (uint32_t j = 0; j < k_renderTargetHeight; j++) {
            for (uint32_t k = 0; k < k_renderTargetWidth; k++) {
                uint32_t offset = j * k_renderTargetWidth + k;
                const uint8_t *pixel = reinterpret_cast<const uint8_t *>(&imagePixels[offset]);
                EXPECT_EQ(pixel[1], 0);
                EXPECT_EQ(pixel[2], 0);
                EXPECT_EQ(pixel[3], 0xff);
                if (j >= composeLayers[i].displayFrame.top &&
                    j < composeLayers[i].displayFrame.bottom &&
                    k >= composeLayers[i].displayFrame.left &&
                    k < composeLayers[i].displayFrame.right) {
                    EXPECT_EQ(pixel[0], 0xff);
                } else {
                    EXPECT_EQ(pixel[0], 0);
                }
            }
        }
    }
}
