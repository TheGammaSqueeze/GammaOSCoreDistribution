/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#ifndef ANDROID_DRM_EVENT_LISTENER_H_
#define ANDROID_DRM_EVENT_LISTENER_H_

#include "autofd.h"
#include "worker.h"

#include <sys/epoll.h>

namespace android {

class DrmDevice;

class DrmEventHandler {
 public:
  DrmEventHandler() {
  }
  virtual ~DrmEventHandler() {
  }

  virtual void handleEvent(uint64_t timestamp_us) = 0;
};

class DrmHistogramEventHandler {
public:
    DrmHistogramEventHandler() {}
    virtual ~DrmHistogramEventHandler() {}

    virtual void handleHistogramEvent(uint32_t crtc_id, void *) = 0;
};

class DrmTUIEventHandler {
 public:
  DrmTUIEventHandler() {
  }
  virtual ~DrmTUIEventHandler() {
  }

  virtual void handleTUIEvent() = 0;
};

class DrmPanelIdleEventHandler {
 public:
  DrmPanelIdleEventHandler() {}
  virtual ~DrmPanelIdleEventHandler() {}

  virtual void handleIdleEnterEvent(char const *event) = 0;
};

class DrmEventListener : public Worker {
 static constexpr const char kTUIStatusPath[] = "/sys/devices/platform/exynos-drm/tui_status";
 static const uint32_t maxFds = 3;
 public:
  DrmEventListener(DrmDevice *drm);
  virtual ~DrmEventListener();

  int Init();

  void RegisterHotplugHandler(DrmEventHandler *handler);
  void UnRegisterHotplugHandler(DrmEventHandler *handler);
  void RegisterHistogramHandler(DrmHistogramEventHandler *handler);
  void UnRegisterHistogramHandler(DrmHistogramEventHandler *handler);
  void RegisterTUIHandler(DrmTUIEventHandler *handler);
  void UnRegisterTUIHandler(DrmTUIEventHandler *handler);
  void RegisterPanelIdleHandler(DrmPanelIdleEventHandler *handler);
  void UnRegisterPanelIdleHandler(DrmPanelIdleEventHandler *handler);

  bool IsDrmInTUI();

  static void FlipHandler(int fd, unsigned int sequence, unsigned int tv_sec,
                          unsigned int tv_usec, void *user_data);

 protected:
  virtual void Routine();

 private:
  void UEventHandler();
  void DRMEventHandler();
  void TUIEventHandler();

  UniqueFd epoll_fd_;
  UniqueFd uevent_fd_;
  UniqueFd tuievent_fd_;

  DrmDevice *drm_;
  std::unique_ptr<DrmEventHandler> hotplug_handler_;
  std::unique_ptr<DrmHistogramEventHandler> histogram_handler_;
  std::unique_ptr<DrmTUIEventHandler> tui_handler_;
  std::unique_ptr<DrmPanelIdleEventHandler> panel_idle_handler_;
};
}  // namespace android

#endif
