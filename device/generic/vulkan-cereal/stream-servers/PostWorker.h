/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
#pragma once

#include <EGL/egl.h>
#include <GLES/gl.h>
#include <GLES3/gl3.h>
#include <vulkan/vulkan.h>

#include <functional>
#include <future>
#include <optional>
#include <vector>

#include "DisplayVk.h"
#include "Hwc2.h"
#include "PostCommands.h"
#include "base/Compiler.h"
#include "base/Lock.h"
#include "base/MessageChannel.h"
#include "host-common/window_agent.h"

class ColorBuffer;
class FrameBuffer;
struct RenderThreadInfo;

class PostWorker {
   public:
    using BindSubwinCallback = std::function<bool(void)>;

    PostWorker(BindSubwinCallback&& cb, bool mainThreadPostingOnly, EGLContext eglContext,
               EGLSurface eglSurface, DisplayVk*);
    ~PostWorker();

    // post: posts the next color buffer.
    // Assumes framebuffer lock is held.
    void post(ColorBuffer* cb);

    // viewport: (re)initializes viewport dimensions.
    // Assumes framebuffer lock is held.
    // This is called whenever the subwindow needs a refresh
    // (FrameBuffer::setupSubWindow).
    void viewport(int width, int height);

    // compose: compse the layers into final framebuffer. The callback will be
    // called when the CPU side job completes. The passed in future in the
    // callback will be completed when the GPU opereation completes.
    void compose(ComposeDevice* p, uint32_t bufferSize,
                 std::shared_ptr<Post::ComposeCallback>);

    // compose: compse the layers into final framebuffer, version 2. The
    // callback will be called when the CPU side job completes. The passed in
    // future in the callback will be completed when the GPU opereation
    // completes.
    void compose(ComposeDevice_v2* p, uint32_t bufferSize,
                 std::shared_ptr<Post::ComposeCallback>);

    // clear: blanks out emulator display when refreshing the subwindow
    // if there is no last posted color buffer to show yet.
    void clear();

    void screenshot(ColorBuffer* cb, int screenwidth, int screenheight,
                    GLenum format, GLenum type, int skinRotation, void* pixels);

   private:
    // Impl versions of the above, so we can run it from separate threads
    void postImpl(ColorBuffer* cb);
    void viewportImpl(int width, int height);
    void composeImpl(const ComposeDevice* p);
    std::shared_future<void> composev2Impl(const ComposeDevice_v2* p);
    void clearImpl();

    // Subwindow binding
    void bind();
    void unbind();

    void glesComposeLayer(ComposeLayer* l, uint32_t w, uint32_t h);
    void fillMultiDisplayPostStruct(ComposeLayer* l, hwc_rect_t displayArea,
                                    hwc_frect_t cropArea,
                                    hwc_transform_t transform);

    // If m_mainThreadPostingOnly is true, schedule the task to UI thread by
    // using m_runOnUiThread. Otherwise, execute the task on the current thread.
    void runTask(std::packaged_task<void()>);

   private:
    using UiThreadRunner = std::function<void(UiUpdateFunc, void*, bool)>;
    struct PostArgs {
        ColorBuffer* postCb;
        int width;
        int height;
        std::vector<char> composeBuffer;
    };

    FrameBuffer* mFb;

    std::function<bool(void)> mBindSubwin;

    bool m_needsToRebindWindow = true;
    int m_viewportWidth = 0;
    int m_viewportHeight = 0;
    GLuint m_composeFbo = 0;

    bool m_mainThreadPostingOnly = false;
    UiThreadRunner m_runOnUiThread = 0;
    EGLContext mContext = EGL_NO_CONTEXT;

    // The implementation for Vulkan native swapchain. Only initialized when
    // useVulkan is set when calling FrameBuffer::initialize(). PostWorker
    // doesn't take the ownership of this DisplayVk object.
    DisplayVk* const m_displayVk;
    // With Vulkan swapchain, compose also means to post to the WSI surface.
    // In this case, don't do anything in the subsequent resource flush.
    std::optional<uint32_t> m_lastVkComposeColorBuffer = std::nullopt;
    std::unordered_map<uint32_t, std::shared_future<void>> m_composeTargetToComposeFuture;

    bool isComposeTargetReady(uint32_t targetHandle);

    DISALLOW_COPY_AND_ASSIGN(PostWorker);
};
