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
#include "PostWorker.h"

#include <string.h>

#include <chrono>

#include "ColorBuffer.h"
#include "Debug.h"
#include "DispatchTables.h"
#include "FrameBuffer.h"
#include "OpenGLESDispatch/EGLDispatch.h"
#include "OpenGLESDispatch/GLESv2Dispatch.h"
#include "RenderThreadInfo.h"
#include "base/Tracing.h"
#include "host-common/GfxstreamFatalError.h"
#include "host-common/logging.h"
#include "host-common/misc.h"
#include "vulkan/VkCommonOperations.h"

using emugl::ABORT_REASON_OTHER;
using emugl::FatalError;

#define POST_DEBUG 0
#if POST_DEBUG >= 1
#define DD(fmt, ...) \
    fprintf(stderr, "%s:%d| " fmt, __func__, __LINE__, ##__VA_ARGS__)
#else
#define DD(fmt, ...) (void)0
#endif

#define POST_ERROR(fmt, ...)                                                  \
    do {                                                                      \
        fprintf(stderr, "%s(%s:%d): " fmt "\n", __func__, __FILE__, __LINE__, \
                ##__VA_ARGS__);                                               \
        fflush(stderr);                                                       \
    } while (0)

static void sDefaultRunOnUiThread(UiUpdateFunc f, void* data, bool wait) {
    (void)f;
    (void)data;
    (void)wait;
}

PostWorker::PostWorker(PostWorker::BindSubwinCallback&& cb, bool mainThreadPostingOnly,
                       EGLContext eglContext, EGLSurface,
                       DisplayVk* displayVk)
    : mFb(FrameBuffer::getFB()),
      mBindSubwin(cb),
      m_mainThreadPostingOnly(mainThreadPostingOnly),
      m_runOnUiThread(m_mainThreadPostingOnly ? emugl::get_emugl_window_operations().runOnUiThread
                                              : sDefaultRunOnUiThread),
      mContext(eglContext),
      m_displayVk(displayVk) {}

void PostWorker::fillMultiDisplayPostStruct(ComposeLayer* l,
                                            hwc_rect_t displayArea,
                                            hwc_frect_t cropArea,
                                            hwc_transform_t transform) {
    l->composeMode = HWC2_COMPOSITION_DEVICE;
    l->blendMode = HWC2_BLEND_MODE_NONE;
    l->transform = transform;
    l->alpha = 1.0;
    l->displayFrame = displayArea;
    l->crop = cropArea;
}

void PostWorker::postImpl(ColorBuffer* cb) {
    // bind the subwindow eglSurface
    if (!m_mainThreadPostingOnly && m_needsToRebindWindow) {
        m_needsToRebindWindow = !mBindSubwin();
        if (m_needsToRebindWindow) {
            // Do not proceed if fail to bind to the window.
            return;
        }
    }

    if (m_displayVk) {
        bool shouldSkip = m_lastVkComposeColorBuffer == cb->getHndl();
        m_lastVkComposeColorBuffer = std::nullopt;
        if (shouldSkip) {
            return;
        }
        goldfish_vk::acquireColorBuffersForHostComposing({}, cb->getHndl());
        auto [success, waitForGpu] = m_displayVk->post(cb->getDisplayBufferVk());
        goldfish_vk::releaseColorBufferFromHostComposing({cb->getHndl()});
        if (success) {
            waitForGpu.wait();
        } else {
            m_needsToRebindWindow = true;
        }
        return;
    }

    float dpr = mFb->getDpr();
    int windowWidth = mFb->windowWidth();
    int windowHeight = mFb->windowHeight();
    float px = mFb->getPx();
    float py = mFb->getPy();
    int zRot = mFb->getZrot();
    hwc_transform_t rotation = (hwc_transform_t)0;

    cb->waitSync();

    // Find the x and y values at the origin when "fully scrolled."
    // Multiply by 2 because the texture goes from -1 to 1, not 0 to 1.
    // Multiply the windowing coordinates by DPR because they ignore
    // DPR, but the viewport includes DPR.
    float fx = 2.f * (m_viewportWidth  - windowWidth  * dpr) / (float)m_viewportWidth;
    float fy = 2.f * (m_viewportHeight - windowHeight * dpr) / (float)m_viewportHeight;

    // finally, compute translation values
    float dx = px * fx;
    float dy = py * fy;

    if (emugl::get_emugl_multi_display_operations().isMultiDisplayEnabled()) {
        uint32_t combinedW, combinedH;
        emugl::get_emugl_multi_display_operations().getCombinedDisplaySize(&combinedW, &combinedH);
        mFb->getTextureDraw()->prepareForDrawLayer();
        int32_t start_id = -1, x, y;
        uint32_t id, w, h, c;
        while(emugl::get_emugl_multi_display_operations().getNextMultiDisplay(start_id, &id,
                                                                              &x, &y, &w, &h,
                                                                              nullptr, nullptr,
                                                                              &c)) {
            if ((id != 0) && (w == 0 || h == 0 || c == 0)) {
                start_id = id;
                continue;
            }
            ColorBuffer* multiDisplayCb = id == 0 ? cb : mFb->findColorBuffer(c).get();
            if (multiDisplayCb == nullptr) {
                start_id = id;
                continue;
            }
            ComposeLayer l;
            hwc_rect_t displayArea = { .left = (int)x,
                                       .top = (int)y,
                                       .right = (int)(x + w),
                                       .bottom = (int)(y + h) };
            hwc_frect_t cropArea = { .left = 0.0,
                                     .top = (float)multiDisplayCb->getHeight(),
                                     .right = (float)multiDisplayCb->getWidth(),
                                     .bottom = 0.0 };
            fillMultiDisplayPostStruct(&l, displayArea, cropArea, rotation);
            multiDisplayCb->postLayer(&l, combinedW, combinedH);
            start_id = id;
        }
        mFb->getTextureDraw()->cleanupForDrawLayer();
    }
    else if (emugl::get_emugl_window_operations().isFolded()) {
        mFb->getTextureDraw()->prepareForDrawLayer();
        ComposeLayer l;
        int x, y, w, h;
        emugl::get_emugl_window_operations().getFoldedArea(&x, &y, &w, &h);
        hwc_rect_t displayArea = { .left = 0,
                                   .top = 0,
                                   .right = windowWidth,
                                   .bottom = windowHeight };
        hwc_frect_t cropArea = { .left = (float)x,
                                 .top = (float)(y + h),
                                 .right = (float)(x + w),
                                 .bottom = (float)y };
        switch ((int)zRot/90) {
            case 1:
                rotation = HWC_TRANSFORM_ROT_270;
                break;
            case 2:
                rotation = HWC_TRANSFORM_ROT_180;
                break;
            case 3:
                rotation = HWC_TRANSFORM_ROT_90;
                break;
            default: ;
        }

        fillMultiDisplayPostStruct(&l, displayArea, cropArea, rotation);
        cb->postLayer(&l, m_viewportWidth/dpr, m_viewportHeight/dpr);
        mFb->getTextureDraw()->cleanupForDrawLayer();
    }
    else {
        // render the color buffer to the window and apply the overlay
        GLuint tex = cb->scale();
        cb->postWithOverlay(tex, zRot, dx, dy);
    }

    s_egl.eglSwapBuffers(mFb->getDisplay(), mFb->getWindowSurface());
}

// Called whenever the subwindow needs a refresh (FrameBuffer::setupSubWindow).
// This rebinds the subwindow context (to account for
// when the refresh is a display change, for instance)
// and resets the posting viewport.
void PostWorker::viewportImpl(int width, int height) {
    if (!m_mainThreadPostingOnly) {
        // For GLES, we rebind the subwindow eglSurface unconditionally: this
        // could be from a display change, but we want to avoid binding
        // VkSurfaceKHR too frequently, because that's too expensive.
        if (!m_displayVk || m_needsToRebindWindow) {
            m_needsToRebindWindow = !mBindSubwin();
            if (m_needsToRebindWindow) {
                // Do not proceed if fail to bind to the window.
                return;
            }
        }
    }

    if (m_displayVk) {
        return;
    }

    float dpr = mFb->getDpr();
    m_viewportWidth = width * dpr;
    m_viewportHeight = height * dpr;
    s_gles2.glViewport(0, 0, m_viewportWidth, m_viewportHeight);
}

// Called when the subwindow refreshes, but there is no
// last posted color buffer to show to the user. Instead of
// displaying whatever happens to be in the back buffer,
// clear() is useful for outputting consistent colors.
void PostWorker::clearImpl() {
    if (m_displayVk) {
        GFXSTREAM_ABORT(FatalError(ABORT_REASON_OTHER))
            << "PostWorker with Vulkan doesn't support clear";
    }
#ifndef __linux__
    s_gles2.glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT |
                    GL_STENCIL_BUFFER_BIT);
    s_egl.eglSwapBuffers(mFb->getDisplay(), mFb->getWindowSurface());
#endif
}

void PostWorker::composeImpl(const ComposeDevice* p) {
    if (m_displayVk) {
        GFXSTREAM_ABORT(FatalError(ABORT_REASON_OTHER))
            << "PostWorker with Vulkan doesn't support ComposeV1";
    }
    // bind the subwindow eglSurface
    if (!m_mainThreadPostingOnly && m_needsToRebindWindow) {
        m_needsToRebindWindow = !mBindSubwin();
        if (m_needsToRebindWindow) {
            // Do not proceed if fail to bind to the window.
            return;
        }
    }

    GLint vport[4] = { 0, };

    auto cbPtr = mFb->findColorBuffer(p->targetHandle);
    if (!cbPtr) {
        s_gles2.glBindFramebuffer(GL_FRAMEBUFFER, 0);
        s_gles2.glViewport(vport[0], vport[1], vport[2], vport[3]);
        return;
    }

    GL_SCOPED_DEBUG_GROUP("PostWorker::composeImpl(into ColorBuffer{hndl:%d tex:%d})", cbPtr->getHndl(), cbPtr->getTexture());

    ComposeLayer* l = (ComposeLayer*)p->layer;

    s_gles2.glGetIntegerv(GL_VIEWPORT, vport);
    s_gles2.glViewport(0, 0, mFb->getWidth(),mFb->getHeight());
    if (!m_composeFbo) {
        s_gles2.glGenFramebuffers(1, &m_composeFbo);
    }
    s_gles2.glBindFramebuffer(GL_FRAMEBUFFER, m_composeFbo);
    s_gles2.glFramebufferTexture2D(GL_FRAMEBUFFER,
                                   GL_COLOR_ATTACHMENT0_OES,
                                   GL_TEXTURE_2D,
                                   cbPtr->getTexture(),
                                   0);

    DD("worker compose %d layers\n", p->numLayers);
    mFb->getTextureDraw()->prepareForDrawLayer();
    for (int i = 0; i < p->numLayers; i++, l++) {
        DD("\tcomposeMode %d color %d %d %d %d blendMode "
               "%d alpha %f transform %d %d %d %d %d "
               "%f %f %f %f\n",
               l->composeMode, l->color.r, l->color.g, l->color.b,
               l->color.a, l->blendMode, l->alpha, l->transform,
               l->displayFrame.left, l->displayFrame.top,
               l->displayFrame.right, l->displayFrame.bottom,
               l->crop.left, l->crop.top, l->crop.right,
               l->crop.bottom);
        glesComposeLayer(l, mFb->getWidth(), mFb->getHeight());
    }

    cbPtr->setSync();

    s_gles2.glBindFramebuffer(GL_FRAMEBUFFER, 0);
    s_gles2.glViewport(vport[0], vport[1], vport[2], vport[3]);
    mFb->getTextureDraw()->cleanupForDrawLayer();
}

std::shared_future<void> PostWorker::composev2Impl(const ComposeDevice_v2* p) {
    std::shared_future<void> completedFuture =
        std::async(std::launch::deferred, [] {}).share();
    completedFuture.wait();
    // bind the subwindow eglSurface
    if (!m_mainThreadPostingOnly && m_needsToRebindWindow) {
        m_needsToRebindWindow = !mBindSubwin();
        if (m_needsToRebindWindow) {
            // Do not proceed if fail to bind to the window.
            return completedFuture;
        }
    }
    ComposeLayer* l = (ComposeLayer*)p->layer;
    auto targetColorBufferPtr = mFb->findColorBuffer(p->targetHandle);

    if (m_displayVk) {
        if (!targetColorBufferPtr) {
            GFXSTREAM_ABORT(FatalError(ABORT_REASON_OTHER)) <<
                                "Failed to retrieve the composition target buffer";
        }
        // We don't copy the render result to the targetHandle color buffer
        // when using the Vulkan native host swapchain, because we directly
        // render to the swapchain image instead of rendering onto a
        // ColorBuffer, and we don't readback from the ColorBuffer so far.
        std::vector<ColorBufferPtr> cbs;  // Keep ColorBuffers alive
        cbs.emplace_back(targetColorBufferPtr);
        std::vector<std::shared_ptr<DisplayVk::DisplayBufferInfo>>
            composeBuffers;
        std::vector<uint32_t> layerColorBufferHandles;
        for (int i = 0; i < p->numLayers; ++i) {
            auto colorBufferPtr = mFb->findColorBuffer(l[i].cbHandle);
            if (!colorBufferPtr) {
                composeBuffers.push_back(nullptr);
                continue;
            }
            auto db = colorBufferPtr->getDisplayBufferVk();
            composeBuffers.push_back(db);
            if (!db) {
                continue;
            }
            cbs.push_back(colorBufferPtr);
            layerColorBufferHandles.emplace_back(l[i].cbHandle);
        }
        goldfish_vk::acquireColorBuffersForHostComposing(layerColorBufferHandles, p->targetHandle);
        auto [success, waitForGpu] = m_displayVk->compose(
            p->numLayers, l, composeBuffers, targetColorBufferPtr->getDisplayBufferVk());
        goldfish_vk::setColorBufferCurrentLayout(p->targetHandle,
                                                 VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL);
        std::vector<uint32_t> colorBufferHandles(layerColorBufferHandles.begin(),
                                                 layerColorBufferHandles.end());
        colorBufferHandles.emplace_back(p->targetHandle);
        goldfish_vk::releaseColorBufferFromHostComposing(colorBufferHandles);
        if (!success) {
            m_needsToRebindWindow = true;
            waitForGpu = completedFuture;
        }
        m_lastVkComposeColorBuffer = p->targetHandle;
        return waitForGpu;
    }

    GLint vport[4] = { 0, };
    s_gles2.glGetIntegerv(GL_VIEWPORT, vport);
    uint32_t w, h;
    emugl::get_emugl_multi_display_operations().getMultiDisplay(p->displayId,
                                                                nullptr,
                                                                nullptr,
                                                                &w,
                                                                &h,
                                                                nullptr,
                                                                nullptr,
                                                                nullptr);
    s_gles2.glViewport(0, 0, w, h);
    if (!m_composeFbo) {
        s_gles2.glGenFramebuffers(1, &m_composeFbo);
    }
    s_gles2.glBindFramebuffer(GL_FRAMEBUFFER, m_composeFbo);

    if (!targetColorBufferPtr) {
        s_gles2.glBindFramebuffer(GL_FRAMEBUFFER, 0);
        s_gles2.glViewport(vport[0], vport[1], vport[2], vport[3]);
        return completedFuture;
    }

    s_gles2.glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0_OES,
                                   GL_TEXTURE_2D,
                                   targetColorBufferPtr->getTexture(), 0);

    DD("worker compose %d layers\n", p->numLayers);
    mFb->getTextureDraw()->prepareForDrawLayer();
    for (int i = 0; i < p->numLayers; i++, l++) {
        DD("\tcomposeMode %d color %d %d %d %d blendMode "
               "%d alpha %f transform %d %d %d %d %d "
               "%f %f %f %f\n",
               l->composeMode, l->color.r, l->color.g, l->color.b,
               l->color.a, l->blendMode, l->alpha, l->transform,
               l->displayFrame.left, l->displayFrame.top,
               l->displayFrame.right, l->displayFrame.bottom,
               l->crop.left, l->crop.top, l->crop.right,
               l->crop.bottom);
        glesComposeLayer(l, w, h);
    }

    targetColorBufferPtr->setSync();
    s_gles2.glBindFramebuffer(GL_FRAMEBUFFER, 0);
    s_gles2.glViewport(vport[0], vport[1], vport[2], vport[3]);
    mFb->getTextureDraw()->cleanupForDrawLayer();
    return completedFuture;
}

void PostWorker::bind() {
    if (m_mainThreadPostingOnly && !m_displayVk) {
        if (mFb->getDisplay() != EGL_NO_DISPLAY) {
            EGLint res = s_egl.eglMakeCurrent(mFb->getDisplay(), mFb->getWindowSurface(), mFb->getWindowSurface(), mContext);
            if (!res) fprintf(stderr, "%s: error in binding: 0x%x\n", __func__, s_egl.eglGetError());
        } else {
            fprintf(stderr, "%s: no display!\n", __func__);
        }
    } else {
        mBindSubwin();
    }
}

void PostWorker::unbind() {
    if (m_displayVk) {
        return;
    }
    if (mFb->getDisplay() != EGL_NO_DISPLAY) {
        s_egl.eglMakeCurrent(mFb->getDisplay(), EGL_NO_SURFACE, EGL_NO_SURFACE,
                             EGL_NO_CONTEXT);
    }
}

void PostWorker::glesComposeLayer(ComposeLayer* l, uint32_t w, uint32_t h) {
    if (m_displayVk) {
        GFXSTREAM_ABORT(FatalError(ABORT_REASON_OTHER)) <<
                            "Should not reach with native vulkan swapchain enabled.";
    }
    if (l->composeMode == HWC2_COMPOSITION_DEVICE) {
        ColorBufferPtr cb = mFb->findColorBuffer(l->cbHandle);
        if (!cb) {
            // bad colorbuffer handle
            // ERR("%s: fail to find colorbuffer %d\n", __FUNCTION__, l->cbHandle);
            return;
        }

        GL_SCOPED_DEBUG_GROUP("PostWorker::glesComposeLayer(layer ColorBuffer{hndl:%d tex:%d})", cb->getHndl(), cb->getTexture());
        cb->postLayer(l, w, h);
    }
    else {
        // no Colorbuffer associated with SOLID_COLOR mode
        mFb->getTextureDraw()->drawLayer(l, w, h, 1, 1, 0);
    }
}

void PostWorker::screenshot(
    ColorBuffer* cb,
    int width,
    int height,
    GLenum format,
    GLenum type,
    int rotation,
    void* pixels) {
    if (m_displayVk) {
        GFXSTREAM_ABORT(FatalError(ABORT_REASON_OTHER)) <<
                            "Screenshot not supported with native Vulkan swapchain enabled.";
    }
    cb->readPixelsScaled(
        width, height, format, type, rotation, pixels);
}

PostWorker::~PostWorker() {
    if (mFb->getDisplay() != EGL_NO_DISPLAY) {
        s_egl.eglMakeCurrent(mFb->getDisplay(), EGL_NO_SURFACE, EGL_NO_SURFACE,
                             EGL_NO_CONTEXT);
    }
}

void PostWorker::post(ColorBuffer* cb) {
    runTask(std::packaged_task<void()>([cb, this] { postImpl(cb); }));
}

void PostWorker::viewport(int width, int height) {
    runTask(std::packaged_task<void()>(
        [width, height, this] { viewportImpl(width, height); }));
}

void PostWorker::compose(ComposeDevice* p, uint32_t bufferSize,
                         std::shared_ptr<Post::ComposeCallback> callback) {
    std::vector<char> buffer(bufferSize, 0);
    memcpy(buffer.data(), p, bufferSize);
    runTask(std::packaged_task<void()>([buffer = std::move(buffer),
                                        callback = std::move(callback), this] {
        auto completedFuture = std::async(std::launch::deferred, [] {}).share();
        auto composeDevice =
            reinterpret_cast<const ComposeDevice*>(buffer.data());
        if (!isComposeTargetReady(composeDevice->targetHandle)) {
            ERR("The last composition on the target buffer hasn't completed.");
        }
        composeImpl(composeDevice);
        m_composeTargetToComposeFuture.emplace(composeDevice->targetHandle, completedFuture);
        (*callback)(completedFuture);
    }));
}

void PostWorker::compose(ComposeDevice_v2* p, uint32_t bufferSize,
                         std::shared_ptr<Post::ComposeCallback> callback) {
    std::vector<char> buffer(bufferSize, 0);
    memcpy(buffer.data(), p, bufferSize);
    runTask(std::packaged_task<void()>([buffer = std::move(buffer),
                                        callback = std::move(callback), this] {
        auto composeDevice =
            reinterpret_cast<const ComposeDevice_v2*>(buffer.data());
        if (!isComposeTargetReady(composeDevice->targetHandle)) {
            ERR("The last composition on the target buffer hasn't completed.");
        }
        auto completedFuture = composev2Impl(composeDevice);
        m_composeTargetToComposeFuture.emplace(composeDevice->targetHandle, completedFuture);
        (*callback)(completedFuture);
    }));
}

void PostWorker::clear() {
    runTask(std::packaged_task<void()>([this] { clearImpl(); }));
}

void PostWorker::runTask(std::packaged_task<void()> task) {
    using Task = std::packaged_task<void()>;
    auto taskPtr = std::make_unique<Task>(std::move(task));
    if (m_mainThreadPostingOnly) {
        m_runOnUiThread(
            [](void* data) {
                std::unique_ptr<Task> taskPtr(reinterpret_cast<Task*>(data));
                (*taskPtr)();
            },
            taskPtr.release(), false);
    } else {
        (*taskPtr)();
    }
}

bool PostWorker::isComposeTargetReady(uint32_t targetHandle) {
    // Even if the target ColorBuffer has already been destroyed, the compose future should have
    // been waited and set to the ready state.
    for (auto i = m_composeTargetToComposeFuture.begin();
         i != m_composeTargetToComposeFuture.end();) {
        auto& composeFuture = i->second;
        if (composeFuture.wait_for(std::chrono::seconds(0)) == std::future_status::ready) {
            i = m_composeTargetToComposeFuture.erase(i);
        } else {
            i++;
        }
    }
    if (m_composeTargetToComposeFuture.find(targetHandle) == m_composeTargetToComposeFuture.end()) {
        return true;
    }
    return false;
}
