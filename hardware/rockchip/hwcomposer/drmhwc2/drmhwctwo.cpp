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

#define ATRACE_TAG ATRACE_TAG_GRAPHICS
#define LOG_TAG "hwc-drm-two"

#include "drmhwctwo.h"
#include "drmdisplaycomposition.h"
#include "drmlayer.h"
#include "platform.h"
#include "vsyncworker.h"
#include "rockchip/utils/drmdebug.h"
#include "rockchip/drmgralloc.h"
#include <im2d.hpp>
#include <drm_fourcc.h>
#include <rga.h>

#include <inttypes.h>
#include <string>

#include <cutils/properties.h>
#include <hardware/hardware.h>
#include <hardware/hwcomposer2.h>
#include <log/log.h>
#include <utils/Trace.h>

#include <linux/fb.h>


#define hwcMIN(x, y)			(((x) <= (y)) ?  (x) :  (y))
#define hwcMAX(x, y)			(((x) >= (y)) ?  (x) :  (y))

#ifndef IS_ALIGN
#define IS_ALIGN(val, align) (((val) & (align - 1)) == 0)
#endif

#ifndef ALIGN
#define ALIGN(value, base) (((value) + ((base)-1)) & ~((base)-1))
#endif

#ifndef ALIGN_DOWN
#define ALIGN_DOWN(value, base) (value & (~(base - 1)))
#endif

namespace android {

static inline long __currentTime(){
    struct timeval tp;
    gettimeofday(&tp, NULL);
    return static_cast<long>(tp.tv_sec) * 1000000 + tp.tv_usec;
}
#define ALOGD_HWC2_DRM_LAYER_INFO(log_level, drmHwcLayers) \
    if(LogLevel(log_level)){ \
      String8 output; \
      for(auto &drmHwcLayer : drmHwcLayers) {\
        drmHwcLayer.DumpInfo(output); \
        ALOGD_IF(LogLevel(log_level),"%s",output.string()); \
        output.clear(); \
      }\
    }

class DrmVsyncCallback : public VsyncCallback {
 public:
  DrmVsyncCallback(hwc2_callback_data_t data, hwc2_function_pointer_t hook)
      : data_(data), hook_(hook) {
  }

  void Callback(int display, int64_t timestamp) {
    auto hook = reinterpret_cast<HWC2_PFN_VSYNC>(hook_);
    if(hook){
      hook(data_, display, timestamp);
    }
  }

 private:
  hwc2_callback_data_t data_;
  hwc2_function_pointer_t hook_;
};

class DrmInvalidateCallback : public InvalidateCallback {
 public:
  DrmInvalidateCallback(hwc2_callback_data_t data, hwc2_function_pointer_t hook)
      : data_(data), hook_(hook) {
  }

  void Callback(int display) {
    auto hook = reinterpret_cast<HWC2_PFN_REFRESH>(hook_);
    if(hook){
      hook(data_, display);
    }
  }

 private:
  hwc2_callback_data_t data_;
  hwc2_function_pointer_t hook_;
};


DrmHwcTwo::DrmHwcTwo()
  : resource_manager_(ResourceManager::getInstance()) {
  common.tag = HARDWARE_DEVICE_TAG;
  common.version = HWC_DEVICE_API_VERSION_2_0;
  common.close = HookDevClose;
  getCapabilities = HookDevGetCapabilities;
  getFunction = HookDevGetFunction;
}

HWC2::Error DrmHwcTwo::CreateDisplay(hwc2_display_t displ,
                                     HWC2::DisplayType type) {
  HWC2_ALOGD_IF_VERBOSE("display-id=%" PRIu64 " type=%s" , displ,
                        (type == HWC2::DisplayType::Physical ? "Physical" : "Virtual"));

  DrmDevice *drm = resource_manager_->GetDrmDevice(displ);
  std::shared_ptr<Importer> importer = resource_manager_->GetImporter(displ);
  if (!drm || !importer) {
    ALOGE("Failed to get a valid drmresource and importer");
    return HWC2::Error::NoResources;
  }
  displays_.emplace(std::piecewise_construct, std::forward_as_tuple(displ),
                    std::forward_as_tuple(resource_manager_, drm, importer,
                                          displ, type));

  displays_.at(displ).Init();
  return HWC2::Error::None;
}

HWC2::Error DrmHwcTwo::Init() {
  HWC2_ALOGD_IF_VERBOSE();
  int rv = resource_manager_->Init(this);
  if (rv) {
    ALOGE("Can't initialize the resource manager %d", rv);
    return HWC2::Error::NoResources;
  }

  HWC2::Error ret = HWC2::Error::None;
  for (auto &map_display : resource_manager_->getDisplays()) {
    ret = CreateDisplay(map_display.second, HWC2::DisplayType::Physical);
    if (ret != HWC2::Error::None) {
      ALOGE("Failed to create display %d with error %d", map_display.second, ret);
      return ret;
    }
  }

  auto &drmDevices = resource_manager_->GetDrmDevices();
  for (auto &device : drmDevices) {
    device->RegisterHotplugHandler(new DrmHotplugHandler(this, device.get()));
  }
  return ret;
}

hwc2_drm_display_t* DrmHwcTwo::GetDisplayCtxPtr(hwc2_display_t display_id){
  if(displays_.count(display_id)){
    auto &display = displays_.at(display_id);
    return display.GetDisplayCtxPtr();
  }
  return NULL;
}

template <typename... Args>
static inline HWC2::Error unsupported(char const *func, Args... /*args*/) {
  ALOGV("Unsupported function: %s", func);
  return HWC2::Error::Unsupported;
}

static inline void supported(char const *func) {
  ALOGV("Supported function: %s", func);
}

HWC2::Error DrmHwcTwo::CreateVirtualDisplay(uint32_t width, uint32_t height,
                                            int32_t *format,
                                            hwc2_display_t *display) {
  HWC2_ALOGD_IF_VERBOSE("w=%u,h=%u,f=%d",width,height,*format);
  HWC2::Error ret = HWC2::Error::None;
  int physical_display_num = resource_manager_->getDisplayCount();
  int virtual_display_id = physical_display_num + mVirtualDisplayCount_;
  if(!displays_.count(virtual_display_id)){
    char value[PROPERTY_VALUE_MAX];
    property_get("vendor.hwc.virtual_display_write_back_id", value, "0");
    int write_back_id = atoi(value);
    DrmDevice *drm = resource_manager_->GetDrmDevice(write_back_id);
    std::shared_ptr<Importer> importer = resource_manager_->GetImporter(write_back_id);
    if (!drm || !importer) {
      ALOGE("Failed to get a valid drmresource and importer");
      return HWC2::Error::NoResources;
    }
    displays_.emplace(std::piecewise_construct, std::forward_as_tuple(virtual_display_id),
                      std::forward_as_tuple(resource_manager_, drm, importer,
                                            virtual_display_id,
                                            HWC2::DisplayType::Virtual));
    displays_.at(virtual_display_id).InitVirtual();
    *display = virtual_display_id;
    *format = HAL_PIXEL_FORMAT_RGBA_8888;
    mVirtualDisplayCount_++;
    resource_manager_->EnableWriteBackMode(write_back_id);
    HWC2_ALOGI("Support VDS: w=%u,h=%u,f=%d display-id=%d",width,height,*format,virtual_display_id);
    auto &display = resource_manager_->GetHwc2()->displays_.at(0);
    display.InvalidateControl(30,-1);
    return HWC2::Error::None;
  }

  return HWC2::Error::NoResources;
}

HWC2::Error DrmHwcTwo::DestroyVirtualDisplay(hwc2_display_t display) {

  HWC2_ALOGD_IF_VERBOSE();
  auto virtual_display = displays_.find(display);
  if(virtual_display != displays_.end()){
	  displays_.erase(virtual_display);
    resource_manager_->DisableWriteBackMode(resource_manager_->GetWBDisplay());
    HWC2_ALOGI("VDS: display-id=%" PRIu64 , display);
    mVirtualDisplayCount_--;
    auto &display = resource_manager_->GetHwc2()->displays_.at(0);
    display.InvalidateControl(30,0);
    return HWC2::Error::None;
  }

  return HWC2::Error::BadDisplay;
}

void DrmHwcTwo::Dump(uint32_t *size, char *buffer) {
  if (buffer != nullptr) {
      auto copiedBytes = mDumpString.copy(buffer, *size);
      *size = static_cast<uint32_t>(copiedBytes);
      return;
  }
  String8 output;

  char acVersion[50] = {0};
  strcpy(acVersion,GHWC_VERSION);

  output.appendFormat("-- HWC2 Version %s by bin.li@rock-chips.com --\n",acVersion);
  for(auto &map_disp: displays_){
    output.append("\n");
    if((map_disp.second.DumpDisplayInfo(output)) < 0)
      continue;
  }
  mDumpString = output.string();
  *size = static_cast<uint32_t>(mDumpString.size());
  return;
}

uint32_t DrmHwcTwo::GetMaxVirtualDisplayCount() {
  HWC2_ALOGI();

  // DSI 固件不支持 HW VirtualDisplay.
  if(hwc_get_int_property("ro.vendor.rk_sdk","0") == 0){
      HWC2_ALOGI("Maybe GSI SDK, to disable HW VirtualDisplay\n");
      return 0;
  }

  char value[PROPERTY_VALUE_MAX];
  property_get("vendor.hwc.max_virtual_display_count", value, "5");
  return atoi(value);
}

static bool isValid(HWC2::Callback descriptor) {
    switch (descriptor) {
        case HWC2::Callback::Hotplug: // Fall-through
        case HWC2::Callback::Refresh: // Fall-through
        case HWC2::Callback::Vsync: return true;
        default: return false;
    }
}

HWC2::Error DrmHwcTwo::RegisterCallback(int32_t descriptor,
                                        hwc2_callback_data_t data,
                                        hwc2_function_pointer_t function) {
  HWC2_ALOGD_IF_VERBOSE();

  auto callback = static_cast<HWC2::Callback>(descriptor);

  if (!isValid(callback)) {
      return HWC2::Error::BadParameter;
  }

  if (!function) {
    callbacks_.erase(callback);
    switch (callback) {
      case HWC2::Callback::Vsync: {
        for (std::pair<const hwc2_display_t, DrmHwcTwo::HwcDisplay> &d :
             displays_)
          d.second.UnregisterVsyncCallback();
        break;
      }
      case HWC2::Callback::Refresh: {
        for (std::pair<const hwc2_display_t, DrmHwcTwo::HwcDisplay> &d :
             displays_)
          d.second.UnregisterInvalidateCallback();
          break;
      }
      default:
        break;
    }
    return HWC2::Error::None;
  }

  callbacks_.emplace(callback, HwcCallback(data, function));

  switch (callback) {
    case HWC2::Callback::Hotplug: {
      auto hotplug = reinterpret_cast<HWC2_PFN_HOTPLUG>(function);
      hotplug(data, HWC_DISPLAY_PRIMARY,
              static_cast<int32_t>(HWC2::Connection::Connected));
      // 主屏已经向SurfaceFlinger注册
      mHasRegisterDisplay_.insert(HWC_DISPLAY_PRIMARY);
      auto &drmDevices = resource_manager_->GetDrmDevices();
      for (auto &device : drmDevices)
        HandleInitialHotplugState(device.get());
      break;
    }
    case HWC2::Callback::Vsync: {
      for (std::pair<const hwc2_display_t, DrmHwcTwo::HwcDisplay> &d :
           displays_)
        d.second.RegisterVsyncCallback(data, function);
      break;
    }
    case HWC2::Callback::Refresh: {
      for (std::pair<const hwc2_display_t, DrmHwcTwo::HwcDisplay> &d :
           displays_)
        d.second.RegisterInvalidateCallback(data, function);
        break;
    }
    default:
      break;
  }
  return HWC2::Error::None;
}

DrmHwcTwo::HwcDisplay::HwcDisplay(ResourceManager *resource_manager,
                                  DrmDevice *drm,
                                  std::shared_ptr<Importer> importer,
                                  hwc2_display_t handle, HWC2::DisplayType type)
    : resource_manager_(resource_manager),
      drm_(drm),
      importer_(importer),
      handle_(handle),
      type_(type),
      client_layer_(UINT32_MAX,drm),
      output_layer_(UINT32_MAX,drm),
      init_success_(false){
}

int DrmHwcTwo::HwcDisplay::ClearDisplay() {
  if(!init_success_){
    HWC2_ALOGE("display=%" PRIu64 " init_success_=%d skip.", handle_, init_success_);
    return -1;
  }

  if(connector_ != NULL &&
     connector_->hwc_state() != HwcConnnectorStete::RELEASE_CRTC){
    compositor_->ClearDisplay();
  }

  HWC2_ALOGD_IF_VERBOSE("display-id=%" PRIu64,handle_);
  return 0;
}

int DrmHwcTwo::HwcDisplay::ActiveModeChange(bool change) {
  HWC2_ALOGD_IF_VERBOSE("display-id=%" PRIu64,handle_);
  bActiveModeChange_ = change;
  return 0;
}

bool DrmHwcTwo::HwcDisplay::IsActiveModeChange() {
  HWC2_ALOGD_IF_VERBOSE("display-id=%" PRIu64,handle_);
  return bActiveModeChange_;
}

HWC2::Error DrmHwcTwo::HwcDisplay::Init() {

  HWC2_ALOGD_IF_VERBOSE("display-id=%" PRIu64,handle_);

  int display = static_cast<int>(handle_);

  if(sync_timeline_.isValid()){
    HWC2_ALOGD_IF_INFO("sync_timeline_ fd = %d isValid", sync_timeline_.getFd());
  }

  connector_ = drm_->GetConnectorForDisplay(display);
  if (!connector_) {
    ALOGE("Failed to get connector for display %d", display);
    return HWC2::Error::BadDisplay;
  }

  int ret = vsync_worker_.Init(drm_, display);
  if (ret) {
    ALOGE("Failed to create event worker for d=%d %d\n", display, ret);
    return HWC2::Error::BadDisplay;
  }

  ret = invalidate_worker_.Init(display);
  if (ret) {
    ALOGE("Failed to create invalidate worker for d=%d %d\n", display, ret);
    return HWC2::Error::BadDisplay;
  }

  if(connector_->state() != DRM_MODE_CONNECTED){
    ALOGI("Connector %u type=%s, type_id=%d, state is DRM_MODE_DISCONNECTED, skip init.\n",
          connector_->id(),
          drm_->connector_type_str(connector_->type()),
          connector_->type_id());
    return HWC2::Error::NoResources;
  }

  // RK3528 HDMI/TV互斥模式要求，若HDMI已连接，则 TV不注册
  if(gIsRK3528() && connector_->type() == DRM_MODE_CONNECTOR_TV){
    DrmConnector* primary = drm_->GetConnectorForDisplay(HWC_DISPLAY_PRIMARY);
    if(primary && primary->state() == DRM_MODE_CONNECTED){
      ret = drm_->ReleaseDpyRes(handle_);
      if (ret) {
        HWC2_ALOGE("Failed to ReleaseDpyRes for display=%d %d\n", display, ret);
        return HWC2::Error::NoResources;
      }
      return HWC2::Error::None;
    }
  }

  UpdateDisplayMode();
  ret = drm_->BindDpyRes(handle_);
  if (ret) {
    HWC2_ALOGE("Failed to BindDpyRes for display=%d %d\n", display, ret);
    return HWC2::Error::NoResources;
  }
  UpdateDisplayInfo();

  ret = drm_->UpdateDisplayGamma(handle_);
  if (ret) {
    HWC2_ALOGE("Failed to UpdateDisplayGamma for display=%d %d\n", display, ret);
  }

  ret = drm_->UpdateDisplay3DLut(handle_);
  if (ret) {
    HWC2_ALOGE("Failed to UpdateDisplay3DLut for display=%d %d\n", display, ret);
  }

  crtc_ = drm_->GetCrtcForDisplay(display);
  if (!crtc_) {
    ALOGE("Failed to get crtc for display %d", display);
    return HWC2::Error::BadDisplay;
  }

  // VRR
  ret = connector_->UpdateModes();
  if(ret){
    ALOGE("Failed to update display modes %d", ret);
    return HWC2::Error::BadDisplay;
  }
  bVrrDisplay_ = crtc_->is_vrr();

  // 更新 hotplug 状态
  connector_->update_hotplug_state();

  planner_ = Planner::CreateInstance(drm_);
  if (!planner_) {
    ALOGE("Failed to create planner instance for composition");
    return HWC2::Error::NoResources;
  }

  compositor_ = resource_manager_->GetDrmDisplayCompositor(crtc_);
  ret = compositor_->Init(resource_manager_, display);
  if (ret) {
    ALOGE("Failed display compositor init for display %d (%d)", display, ret);
    return HWC2::Error::NoResources;
  }

  // CropSpilt must to
  if(connector_->isCropSpilt()){
    std::unique_ptr<DrmDisplayComposition> composition = compositor_->CreateComposition();
    composition->Init(drm_, crtc_, importer_.get(), planner_.get(), frame_no_, handle_);
    composition->SetDpmsMode(DRM_MODE_DPMS_ON);
    ret = compositor_->QueueComposition(std::move(composition));
    if (ret) {
      HWC2_ALOGE("Failed to apply the dpms composition ret=%d", ret);
    }
  }

  // soc_id
  ctx_.soc_id = resource_manager_->getSocId();
  // display_id
  ctx_.display_id = display;
  // display-type
  ctx_.display_type = connector_->type();
  // vop aclk
  ctx_.aclk = crtc_->get_aclk();
  // Baseparameter Info
  ctx_.baseparameter_info = connector_->baseparameter_info();
  // Standard Switch Resolution Mode
  ctx_.bStandardSwitchResolution = hwc_get_bool_property("vendor.hwc.enable_display_configs","false");

  HWC2::Error error = ChosePreferredConfig();
  if(error != HWC2::Error::None){
    ALOGE("Failed to chose prefererd config for display %d (%d)", display, error);
    return error;
  }

  init_success_ = true;

  return HWC2::Error::None;
}


HWC2::Error DrmHwcTwo::HwcDisplay::InitVirtual() {

  HWC2_ALOGD_IF_VERBOSE("display-id=%" PRIu64 " type=%s",handle_,
                        (type_ == HWC2::DisplayType::Physical ? "Physical" : "Virtual"));

  int display = static_cast<int>(handle_);

  connector_ = drm_->GetWritebackConnectorForDisplay(0);
  if (!connector_) {
    ALOGE("Failed to get connector for display %d", display);
    return HWC2::Error::BadDisplay;
  }

  init_success_ = true;
  frame_no_ = 0;
  return HWC2::Error::None;
}

HWC2::Error DrmHwcTwo::HwcDisplay::CheckStateAndReinit(bool clear_layer) {

  HWC2_ALOGD_IF_VERBOSE("display-id=%" PRIu64,handle_);

  int display = static_cast<int>(handle_);

  connector_ = drm_->GetConnectorForDisplay(display);
  if (!connector_) {
    ALOGE("Failed to get connector for display %d", display);
    return HWC2::Error::BadDisplay;
  }

  if(connector_->state() != DRM_MODE_CONNECTED){
    ALOGI("Connector %u type=%s, type_id=%d, state is DRM_MODE_DISCONNECTED, skip init.\n",
          connector_->id(),drm_->connector_type_str(connector_->type()),connector_->type_id());
    return HWC2::Error::NoResources;
  }

  UpdateDisplayMode();
  int ret = drm_->BindDpyRes(handle_);
  if (ret) {
    HWC2_ALOGE("Failed to BindDpyRes for display=%d %d\n", display, ret);
    return HWC2::Error::NoResources;
  }

  UpdateDisplayInfo();

  crtc_ = drm_->GetCrtcForDisplay(display);
  if (!crtc_) {
    ALOGE("Failed to get crtc for display %d", display);
    return HWC2::Error::BadDisplay;
  }

  bVrrDisplay_ = crtc_->is_vrr();

  ret = drm_->UpdateDisplayGamma(handle_);
  if (ret) {
    HWC2_ALOGE("Failed to UpdateDisplayGamma for display=%d %d\n", display, ret);
  }

  ret = drm_->UpdateDisplay3DLut(handle_);
  if (ret) {
    HWC2_ALOGE("Failed to UpdateDisplay3DLut for display=%d %d\n", display, ret);
  }

  // Reset HwcLayer resource
  if(clear_layer && handle_ != HWC_DISPLAY_PRIMARY){
    // Clear Layers
    for(auto &map_layer : layers_){
      map_layer.second.clear();
    }
    // Bug: #359894
    // layers_ not clear may cause error log:
    // "E hwc-platform-drm-generic: ImportBuffer fail fd=7,w=-1,h=-1,bo->format=AB24 ..."
    layers_.clear();
    // Clear Client Target Layer
    client_layer_.clear();
  }

  compositor_ = resource_manager_->GetDrmDisplayCompositor(crtc_);
  ret = compositor_->Init(resource_manager_, display);
  if (ret) {
    ALOGE("Failed display compositor init for display %d (%d)", display, ret);
    return HWC2::Error::NoResources;
  }

  if(init_success_){
    return HWC2::Error::None;
  }

  planner_ = Planner::CreateInstance(drm_);
  if (!planner_) {
    ALOGE("Failed to create planner instance for composition");
    return HWC2::Error::NoResources;
  }

  // soc_id
  ctx_.soc_id = resource_manager_->getSocId();
  // display_id
  ctx_.display_id = display;
  // display-type
  ctx_.display_type = connector_->type();
  // vop aclk
  ctx_.aclk = crtc_->get_aclk();
  // Baseparameter Info
  ctx_.baseparameter_info = connector_->baseparameter_info();
  // Standard Switch Resolution Mode
  ctx_.bStandardSwitchResolution = hwc_get_bool_property("vendor.hwc.enable_display_configs","false");

  HWC2::Error error = ChosePreferredConfig();
  if(error != HWC2::Error::None){
    ALOGE("Failed to chose prefererd config for display %d (%d)", display, error);
    return error;
  }

  init_success_ = true;

  return HWC2::Error::None;
}


HWC2::Error DrmHwcTwo::HwcDisplay::CheckDisplayState(){
  HWC2_ALOGD_IF_VERBOSE("display-id=%" PRIu64,handle_);
  int display = static_cast<int>(handle_);

  if(!init_success_){
    ALOGE_IF(LogLevel(DBG_ERROR),"Display %d not init success! %s,line=%d", display,
          __FUNCTION__, __LINE__);
    return HWC2::Error::BadDisplay;
  }

  connector_ = drm_->GetConnectorForDisplay(display);
  if (!connector_) {
    ALOGE_IF(LogLevel(DBG_ERROR),"Failed to get connector for display %d, %s,line=%d",
          display, __FUNCTION__, __LINE__);
    return HWC2::Error::BadDisplay;
  }

  if(connector_->state() != DRM_MODE_CONNECTED){
    ALOGE_IF(LogLevel(DBG_ERROR),"Connector %u type=%s, type_id=%d, state is DRM_MODE_DISCONNECTED, skip init, %s,line=%d\n",
          connector_->id(),drm_->connector_type_str(connector_->type()),connector_->type_id(),
          __FUNCTION__, __LINE__);
    return HWC2::Error::NoResources;
  }

  crtc_ = drm_->GetCrtcForDisplay(display);
  if (!crtc_) {
    ALOGE_IF(LogLevel(DBG_ERROR),"Failed to get crtc for display %d, %s,line=%d", display,
          __FUNCTION__, __LINE__);
    return HWC2::Error::BadDisplay;
  }

  if(!layers_.size()){
    ALOGE_IF(LogLevel(DBG_ERROR),"display %d layer size is %zu, %s,line=%d", display, layers_.size(),
          __FUNCTION__, __LINE__);
    return HWC2::Error::BadLayer;
  }

  return HWC2::Error::None;
}


HWC2::Error DrmHwcTwo::HwcDisplay::ChosePreferredConfig() {
  HWC2_ALOGD_IF_VERBOSE("display-id=%" PRIu64,handle_);
  // Fetch the number of modes from the display
  uint32_t num_configs;
  HWC2::Error err = GetDisplayConfigs(&num_configs, NULL);
  if (err != HWC2::Error::None || !num_configs)
    return err;

  err = SetActiveConfig(connector_->active_mode().id());
  return err;
}

HWC2::Error DrmHwcTwo::HwcDisplay::RegisterVsyncCallback(
    hwc2_callback_data_t data, hwc2_function_pointer_t func) {
  HWC2_ALOGD_IF_VERBOSE("display-id=%" PRIu64,handle_);
  auto callback = std::make_shared<DrmVsyncCallback>(data, func);
  vsync_worker_.RegisterCallback(std::move(callback));
  return HWC2::Error::None;
}

HWC2::Error DrmHwcTwo::HwcDisplay::RegisterInvalidateCallback(
    hwc2_callback_data_t data, hwc2_function_pointer_t func) {
  HWC2_ALOGD_IF_VERBOSE("display-id=%" PRIu64,handle_);
  auto callback = std::make_shared<DrmInvalidateCallback>(data, func);
  invalidate_worker_.RegisterCallback(std::move(callback));
  return HWC2::Error::None;
}

HWC2::Error DrmHwcTwo::HwcDisplay::UnregisterVsyncCallback() {
  HWC2_ALOGD_IF_VERBOSE("display-id=%" PRIu64,handle_);;
  vsync_worker_.RegisterCallback(NULL);
  return HWC2::Error::None;
}

HWC2::Error DrmHwcTwo::HwcDisplay::UnregisterInvalidateCallback() {
  HWC2_ALOGD_IF_VERBOSE("display-id=%" PRIu64,handle_);
  invalidate_worker_.RegisterCallback(NULL);
  return HWC2::Error::None;
}


HWC2::Error DrmHwcTwo::HwcDisplay::AcceptDisplayChanges() {
  HWC2_ALOGD_IF_VERBOSE("display-id=%" PRIu64,handle_);
  for (std::pair<const hwc2_layer_t, DrmHwcTwo::HwcLayer> &l : layers_)
    l.second.accept_type_change();
  return HWC2::Error::None;
}

HWC2::Error DrmHwcTwo::HwcDisplay::CreateLayer(hwc2_layer_t *layer) {
  layers_.emplace(static_cast<hwc2_layer_t>(layer_idx_), HwcLayer(layer_idx_, drm_));
  *layer = static_cast<hwc2_layer_t>(layer_idx_);
  ++layer_idx_;
  HWC2_ALOGD_IF_VERBOSE("display-id=%" PRIu64 ", layer-id=%" PRIu64,handle_,*layer);
  return HWC2::Error::None;
}

HWC2::Error DrmHwcTwo::HwcDisplay::DestroyLayer(hwc2_layer_t layer) {
  HWC2_ALOGD_IF_VERBOSE("display-id=%" PRIu64 ", layer-id=%" PRIu64,handle_,layer);
  auto map_layer = layers_.find(layer);
  if (map_layer != layers_.end()){
    map_layer->second.clear();
    layers_.erase(layer);
    return HWC2::Error::None;
  }else{
    return HWC2::Error::BadLayer;
  }
}

HWC2::Error DrmHwcTwo::HwcDisplay::GetActiveConfig(hwc2_config_t *config) {
  HWC2_ALOGD_IF_VERBOSE("display-id=%" PRIu64 ,handle_);

  if(ctx_.bStandardSwitchResolution){
    DrmMode const &mode = connector_->active_mode();
    if (mode.id() == 0)
      return HWC2::Error::BadConfig;

    DrmMode const &best_mode = connector_->best_mode();


    if(connector_->isHorizontalSpilt()){
      ctx_.framebuffer_width = best_mode.h_display() / 2;
      ctx_.framebuffer_height = best_mode.v_display();
    }else{
      ctx_.framebuffer_width = best_mode.h_display();
      ctx_.framebuffer_height = best_mode.v_display();
    }

    *config = mode.id();
  }else{
    *config = 0;
  }
  HWC2_ALOGD_IF_VERBOSE("display-id=%" PRIu64 " config-id=%d" ,handle_,*config);
  return HWC2::Error::None;
}

HWC2::Error DrmHwcTwo::HwcDisplay::GetChangedCompositionTypes(
    uint32_t *num_elements, hwc2_layer_t *layers, int32_t *types) {
  HWC2_ALOGD_IF_VERBOSE("display-id=%" PRIu64,handle_);
  uint32_t num_changes = 0;
  for (std::pair<const hwc2_layer_t, DrmHwcTwo::HwcLayer> &l : layers_) {
    if (l.second.type_changed()) {
      if (layers && num_changes < *num_elements)
        layers[num_changes] = l.first;
      if (types && num_changes < *num_elements)
        types[num_changes] = static_cast<int32_t>(l.second.validated_type());
      ++num_changes;
    }
  }
  if (!layers && !types)
    *num_elements = num_changes;

  return HWC2::Error::None;
}

HWC2::Error DrmHwcTwo::HwcDisplay::GetClientTargetSupport(uint32_t width,
                                                          uint32_t height,
                                                          int32_t /*format*/,
                                                          int32_t dataspace) {
  HWC2_ALOGD_IF_VERBOSE("display-id=%" PRIu64,handle_);
  std::pair<uint32_t, uint32_t> min = drm_->min_resolution();
  std::pair<uint32_t, uint32_t> max = drm_->max_resolution();

  if (width < min.first || height < min.second)
    return HWC2::Error::Unsupported;

  if (width > max.first || height > max.second)
    return HWC2::Error::Unsupported;

  if (dataspace != HAL_DATASPACE_UNKNOWN &&
      dataspace != HAL_DATASPACE_STANDARD_UNSPECIFIED)
    return HWC2::Error::Unsupported;

  // TODO: Validate format can be handled by either GL or planes
  return HWC2::Error::None;
}

HWC2::Error DrmHwcTwo::HwcDisplay::GetColorModes(uint32_t *num_modes,
                                                 int32_t *modes) {
  HWC2_ALOGD_IF_VERBOSE("display-id=%" PRIu64,handle_);
  if (!modes)
    *num_modes = 1;

  if (modes)
    *modes = HAL_COLOR_MODE_NATIVE;

  return HWC2::Error::None;
}

HWC2::Error DrmHwcTwo::HwcDisplay::GetDisplayAttribute(hwc2_config_t config,
                                                       int32_t attribute_in,
                                                       int32_t *value) {
  HWC2_ALOGD_IF_VERBOSE("display-id=%" PRIu64,handle_);

  if(ctx_.bStandardSwitchResolution){
    auto mode = std::find_if(sf_modes_.begin(),
                             sf_modes_.end(),
                             [config](DrmMode const &m) {
                               return m.id() == config;
                             });
    if (mode == sf_modes_.end()) {
      ALOGE("Could not find active mode for %d", config);
      return HWC2::Error::BadConfig;
    }

    static const int32_t kUmPerInch = 25400;
    uint32_t mm_width = connector_->mm_width();
    uint32_t mm_height = connector_->mm_height();
    auto attribute = static_cast<HWC2::Attribute>(attribute_in);
    switch (attribute) {
      case HWC2::Attribute::Width:
        *value = mode->h_display();
        break;
      case HWC2::Attribute::Height:
        *value = mode->v_display();
        break;
      case HWC2::Attribute::VsyncPeriod:
        // in nanoseconds
        *value = 1000 * 1000 * 1000 / mode->v_refresh();
        break;
      case HWC2::Attribute::DpiX:
        // Dots per 1000 inches
        *value = mm_width ? (mode->h_display() * kUmPerInch) / mm_width : -1;
        break;
      case HWC2::Attribute::DpiY:
        // Dots per 1000 inches
        *value = mm_height ? (mode->v_display() * kUmPerInch) / mm_height : -1;
        break;
      default:
        *value = -1;
        return HWC2::Error::BadConfig;
    }
  }else{

    static const int32_t kUmPerInch = 25400;
    uint32_t mm_width = connector_->mm_width();
    uint32_t mm_height = connector_->mm_height();
    int w = ctx_.framebuffer_width;
    int h = ctx_.framebuffer_height;
    int vrefresh = ctx_.vrefresh;
    // VRR
    const std::vector<int> vrr_mode = connector_->vrr_modes();
    if (bVrrDisplay_ && vrr_mode.size() > 1
       && config < vrr_mode.size()) {
      vrefresh = vrr_mode[config];
    }
    auto attribute = static_cast<HWC2::Attribute>(attribute_in);
    switch (attribute) {
      case HWC2::Attribute::Width:
        *value = w;
        break;
      case HWC2::Attribute::Height:
        *value = h;
        break;
      case HWC2::Attribute::VsyncPeriod:
        // in nanoseconds
        *value = 1000 * 1000 * 1000 / vrefresh;
        break;
      case HWC2::Attribute::DpiX:
        // Dots per 1000 inches
        *value = mm_width ? (w * kUmPerInch) / mm_width : -1;
        break;
      case HWC2::Attribute::DpiY:
        // Dots per 1000 inches
        *value = mm_height ? (h * kUmPerInch) / mm_height : -1;
        break;
      default:
        *value = -1;
        return HWC2::Error::BadConfig;
    }
  }
  return HWC2::Error::None;
}

HWC2::Error DrmHwcTwo::HwcDisplay::GetDisplayConfigs(uint32_t *num_configs,
                                                     hwc2_config_t *configs) {
  HWC2_ALOGD_IF_VERBOSE("display-id=%" PRIu64,handle_);
  // Since this callback is normally invoked twice (once to get the count, and
  // once to populate configs), we don't really want to read the edid
  // redundantly. Instead, only update the modes on the first invocation. While
  // it's possible this will result in stale modes, it'll all come out in the
  // wash when we try to set the active config later.
  if (!configs) {
    if (!connector_->ModesReady()) {
      int ret = connector_->UpdateModes();
      if(ret){
        ALOGE("Failed to update display modes %d", ret);
        return HWC2::Error::BadDisplay;
      }
    }
  }
  if(ctx_.bStandardSwitchResolution){
    // Since the upper layers only look at vactive/hactive/refresh, height and
    // width, it doesn't differentiate interlaced from progressive and other
    // similar modes. Depending on the order of modes we return to SF, it could
    // end up choosing a suboptimal configuration and dropping the preferred
    // mode. To workaround this, don't offer interlaced modes to SF if there is
    // at least one non-interlaced alternative and only offer a single WxH@R
    // mode with at least the prefered mode from in DrmConnector::UpdateModes()

    // TODO: Remove the following block of code until AOSP handles all modes
    std::vector<DrmMode> sel_modes;

    // Add the preferred mode first to be sure it's not dropped
    auto preferred_mode = std::find_if(connector_->modes().begin(),
                             connector_->modes().end(), [&](DrmMode const &m) {
                               return m.id() ==
                                      connector_->get_preferred_mode_id();
                             });
    if (preferred_mode != connector_->modes().end())
      sel_modes.push_back(*preferred_mode);

    // Add the active mode if different from preferred mode
    if (connector_->active_mode().id() != connector_->get_preferred_mode_id())
      sel_modes.push_back(connector_->active_mode());

    // Cycle over the modes and filter out "similar" modes, keeping only the
    // first ones in the order given by DRM (from CEA ids and timings order)
    for (const DrmMode &mode : connector_->modes()) {
      // TODO: Remove this when 3D Attributes are in AOSP
      if (mode.flags() & DRM_MODE_FLAG_3D_MASK)
        continue;

      // TODO: Remove this when the Interlaced attribute is in AOSP
      if (mode.flags() & DRM_MODE_FLAG_INTERLACE) {
        auto m = std::find_if(connector_->modes().begin(),
                              connector_->modes().end(),
                              [&mode](DrmMode const &m) {
                                return !(m.flags() & DRM_MODE_FLAG_INTERLACE) &&
                                       m.h_display() == mode.h_display() &&
                                       m.v_display() == mode.v_display();
                              });
        if (m == connector_->modes().end())
          sel_modes.push_back(mode);

        continue;
      }

      // Search for a similar WxH@R mode in the filtered list and drop it if
      // another mode with the same WxH@R has already been selected
      // TODO: Remove this when AOSP handles duplicates modes
      auto m = std::find_if(sel_modes.begin(), sel_modes.end(),
                            [&mode](DrmMode const &m) {
                              return m.h_display() == mode.h_display() &&
                                     m.v_display() == mode.v_display() &&
                                     m.v_refresh() == mode.v_refresh();
                            });
      if (m == sel_modes.end())
        sel_modes.push_back(mode);
    }

    auto num_modes = static_cast<uint32_t>(sel_modes.size());
    sf_modes_.swap(sel_modes);
    if (!configs) {
      *num_configs = num_modes;
      return HWC2::Error::None;
    }

    uint32_t idx = 0;
    for (const DrmMode &mode : sel_modes) {
      if (idx >= *num_configs)
        break;
      configs[idx++] = mode.id();
    }


    *num_configs = sf_modes_.size();
  }else{
    UpdateDisplayInfo();
    const DrmMode best_mode = connector_->active_mode();

    char framebuffer_size[PROPERTY_VALUE_MAX];
    uint32_t width = 0, height = 0 , vrefresh = 0;

    connector_->GetFramebufferInfo(handle_, &width, &height, &vrefresh);

    if (width && height) {
      ctx_.framebuffer_width = width;
      ctx_.framebuffer_height = height;
      ctx_.vrefresh = vrefresh ? vrefresh : 60;
    } else if (best_mode.h_display() && best_mode.v_display() && best_mode.v_refresh()) {
      ctx_.framebuffer_width = best_mode.h_display();
      ctx_.framebuffer_height = best_mode.v_display();
      ctx_.vrefresh = best_mode.v_refresh();
      /*
       * RK3588：Limit to 4096x2160 if large than 2160p
       * Other:  Limit to 1920x1080 if large than 2160p
       */
      if(isRK3588(resource_manager_->getSocId())){
        if (ctx_.framebuffer_height >= 2160 && ctx_.framebuffer_width >= ctx_.framebuffer_height) {
          ctx_.framebuffer_width = ctx_.framebuffer_width * (2160.0 / ctx_.framebuffer_height);
          ctx_.framebuffer_height = 2160;
        }
      }else{
        if (ctx_.framebuffer_height >= 2160 && ctx_.framebuffer_width >= ctx_.framebuffer_height) {
          ctx_.framebuffer_width = ctx_.framebuffer_width * (1080.0 / ctx_.framebuffer_height);
          ctx_.framebuffer_height = 1080;
        }
      }
    } else {
      ctx_.framebuffer_width = 1920;
      ctx_.framebuffer_height = 1080;
      ctx_.vrefresh = 60;
      ALOGE("Failed to find available display mode for display %" PRIu64 "\n", handle_);
    }

    if(connector_->isHorizontalSpilt()){
      ctx_.rel_xres = best_mode.h_display() / DRM_CONNECTOR_SPILT_RATIO;
      ctx_.rel_yres = best_mode.v_display();
      ctx_.framebuffer_width = ctx_.framebuffer_width / DRM_CONNECTOR_SPILT_RATIO;
      if(handle_ >= DRM_CONNECTOR_SPILT_MODE_MASK){
        ctx_.rel_xoffset = best_mode.h_display() / DRM_CONNECTOR_SPILT_RATIO;
        ctx_.rel_yoffset = 0;//best_mode.v_display() / 2;
      }
    }else if(connector_->isCropSpilt()){
      int32_t fb_w = 0, fb_h = 0;
      connector_->getCropSpiltFb(&fb_w, &fb_h);
      ctx_.framebuffer_width = fb_w;
      ctx_.framebuffer_height = fb_h;
      ctx_.rel_xres = best_mode.h_display();
      ctx_.rel_yres = best_mode.v_display();
    }else{
      ctx_.rel_xres = best_mode.h_display();
      ctx_.rel_yres = best_mode.v_display();
    }

    // 动态可变刷新率要求将刷新率上报
    if(best_mode.v_refresh() > 0){
      ctx_.vrefresh = best_mode.v_refresh();
    }

    // AFBC limit
    if(handle_ == HWC_DISPLAY_PRIMARY){
      bool disable_afbdc = false;

      if(isRK356x(resource_manager_->getSocId())){
        if(ctx_.framebuffer_width % 4 != 0){
          disable_afbdc = true;
          HWC2_ALOGI("RK356x primary framebuffer size %dx%d not support AFBC, to disable AFBC\n",
                      ctx_.framebuffer_width,ctx_.framebuffer_height);
        }
      }
      if(hwc_get_int_property("ro.vendor.rk_sdk","0") == 0){
          disable_afbdc = true;
          HWC2_ALOGI("Maybe GSI SDK, to disable AFBC\n");
      }
      if(disable_afbdc){
        property_set( "vendor.gralloc.no_afbc_for_fb_target_layer", "1");
      }
    }

    const std::vector<int> vrr_mode = connector_->vrr_modes();
    if(bVrrDisplay_ && vrr_mode.size() > 1){
      if (!configs) {
        *num_configs = vrr_mode.size();
        return HWC2::Error::None;
      }
      *num_configs = vrr_mode.size();

      for(int index = 0 ; index <= vrr_mode.size(); index++){
        configs[index] = index;
      }
    }else{
      if (!configs) {
        *num_configs = 1;
        return HWC2::Error::None;
      }
      *num_configs = 1;
      configs[0] = 0;
    }
  }

  return HWC2::Error::None;
}

HWC2::Error DrmHwcTwo::HwcDisplay::GetDisplayName(uint32_t *size, char *name) {
  HWC2_ALOGD_IF_VERBOSE("display-id=%" PRIu64,handle_);
  std::ostringstream stream;
  stream << "display-" << connector_->id();
  std::string string = stream.str();
  size_t length = string.length();
  if (!name) {
    *size = length;
    return HWC2::Error::None;
  }

  *size = std::min<uint32_t>(static_cast<uint32_t>(length - 1), *size);
  strncpy(name, string.c_str(), *size);
  return HWC2::Error::None;
}

HWC2::Error DrmHwcTwo::HwcDisplay::GetDisplayRequests(int32_t *display_requests,
                                                      uint32_t *num_elements,
                                                      hwc2_layer_t *layers,
                                                      int32_t *layer_requests) {
  HWC2_ALOGD_IF_VERBOSE("display-id=%" PRIu64,handle_);

  uint32_t num_request = 0;
  if(hwc_get_int_property("ro.vendor.rk_sdk","0") == 0){
    HWC2_ALOGD_IF_INFO("Maybe GSI SDK, to disable AFBC\n");
    if (!layers || !layer_requests){
      *num_elements = num_request;
      return HWC2::Error::None;
    }else{
      *display_requests = 0;
      return HWC2::Error::None;
    }
  }

  // RK3528 Mali 不支持AFBC
  if(gIsRK3528()){
    if (!layers || !layer_requests){
      *num_elements = num_request;
      return HWC2::Error::None;
    }else{
      *display_requests = 0;
      return HWC2::Error::None;
    }
  }

  // TODO: I think virtual display should request
  //      HWC2_DISPLAY_REQUEST_WRITE_CLIENT_TARGET_TO_OUTPUT here
  uint32_t client_layer_id = false;
  for (std::pair<const hwc2_layer_t, DrmHwcTwo::HwcLayer> &l : layers_) {
    if (l.second.validated_type() == HWC2::Composition::Client) {
        client_layer_id = l.first;
        break;
    }
  }

  if(client_layer_id > 0 && validate_success_ && !client_layer_.isAfbc()){
    num_request++;
    if(display_requests){
      // RK: Reuse HWC2_DISPLAY_REQUEST_FLIP_CLIENT_TARGET definition to
      //     implement ClientTarget feature.
      *display_requests = HWC2_DISPLAY_REQUEST_FLIP_CLIENT_TARGET;
    }
  }else{
      *display_requests = 0;
  }

  if (!layers || !layer_requests)
    *num_elements = num_request;
  else{
    layers[0] = client_layer_id;
    layer_requests[0] = 0;
  }

  return HWC2::Error::None;
}

HWC2::Error DrmHwcTwo::HwcDisplay::GetDisplayType(int32_t *type) {
  HWC2_ALOGD_IF_VERBOSE("display-id=%" PRIu64,handle_);
  *type = static_cast<int32_t>(type_);
  return HWC2::Error::None;
}

HWC2::Error DrmHwcTwo::HwcDisplay::GetDozeSupport(int32_t *support) {
  HWC2_ALOGD_IF_VERBOSE("display-id=%" PRIu64,handle_);
  *support = 0;
  return HWC2::Error::None;
}

HWC2::Error DrmHwcTwo::HwcDisplay::GetHdrCapabilities(
    uint32_t *num_types, int32_t *types, float * max_luminance,
    float *max_average_luminance, float * min_luminance) {
  HWC2_ALOGD_IF_VERBOSE("display-id=%" PRIu64,handle_);

  int display = static_cast<int>(handle_);
  int HdrIndex = 0;

    if (!connector_) {
    ALOGE("%s:Failed to get connector for display %d line=%d", __FUNCTION__,display,__LINE__);
    return HWC2::Error::None;
  }
  if(!connector_->ModesReady()){
    int ret = connector_->UpdateModes();
    if (ret) {
      ALOGE("Failed to update display modes %d", ret);
      return HWC2::Error::None;
    }
  }
  const std::vector<DrmHdr> hdr_support_list = connector_->get_hdr_support_list();

  if(types == NULL){
      *num_types = hdr_support_list.size();
      return HWC2::Error::None;
  }

  for(const DrmHdr &hdr_mode : hdr_support_list){
      types[HdrIndex] = hdr_mode.drmHdrType;
      *max_luminance = hdr_mode.outMaxLuminance;
      *max_average_luminance = hdr_mode.outMaxAverageLuminance;
      *min_luminance = hdr_mode.outMinLuminance;
      HdrIndex++;
  }
  *num_types = hdr_support_list.size();

  return HWC2::Error::None;
}

HWC2::Error DrmHwcTwo::HwcDisplay::GetReleaseFences(uint32_t *num_elements,
                                                    hwc2_layer_t *layers,
                                                    int32_t *fences) {
  HWC2_ALOGD_IF_VERBOSE("display-id=%" PRIu64,handle_);

  uint32_t num_layers = 0;

  for (std::pair<const hwc2_layer_t, DrmHwcTwo::HwcLayer> &l : layers_) {
    ++num_layers;
    if (layers == NULL || fences == NULL) {
      continue;
    } else if (num_layers > *num_elements) {
      ALOGW("Overflow num_elements %d/%d", num_layers, *num_elements);
      return HWC2::Error::None;
    }

    layers[num_layers - 1] = l.first;
    fences[num_layers - 1] = l.second.release_fence()->isValid() ? dup(l.second.release_fence()->getFd()) : -1;
    if(LogLevel(DBG_VERBOSE))
      HWC2_ALOGD_IF_VERBOSE("Check Layer %" PRIu64 " Release(%d) %s Info: size=%d act=%d signal=%d err=%d",
                          l.first,l.second.release_fence()->isValid(),l.second.release_fence()->getName().c_str(),
                          l.second.release_fence()->getSize(), l.second.release_fence()->getActiveCount(),
                          l.second.release_fence()->getSignaledCount(), l.second.release_fence()->getErrorCount());
    // HWC2_ALOGD_IF_DEBUG("GetReleaseFences [%" PRIu64 "][%d]",layers[num_layers - 1],fences[num_layers - 1]);
    // the new fence semantics for a frame n by returning the fence from frame n-1. For frame 0,
    // the adapter returns NO_FENCE.
  }
  *num_elements = num_layers;
  return HWC2::Error::None;
}

void DrmHwcTwo::HwcDisplay::AddFenceToRetireFence(int fd) {
  HWC2_ALOGD_IF_VERBOSE("display-id=%" PRIu64,handle_);

  char acBuf[32];
  int retire_fence_fd = -1;
  if (fd < 0){
    // Collet all layer releaseFence
    const sp<ReleaseFence> client_rf = client_layer_.back_release_fence();
    if(client_rf->isValid()){
      retire_fence_fd = dup(client_rf->getFd());
      sprintf(acBuf,"RTD%" PRIu64 "-FN%d-%d", handle_, frame_no_, 0);
    }
    for (std::pair<const hwc2_layer_t, DrmHwcTwo::HwcLayer> &hwc2layer : layers_) {
    if(hwc2layer.second.validated_type() != HWC2::Composition::Device)
        continue;
      // the new fence semantics for a frame n by returning the fence from frame n-1. For frame 0,
      // the adapter returns NO_FENCE.
      const sp<ReleaseFence> rf = hwc2layer.second.back_release_fence();
      if (rf->isValid()){
        // cur_retire_fence is null
        if(retire_fence_fd > 0){
          sprintf(acBuf,"RTD%" PRIu64 "-FN%d-%" PRIu64,handle_, frame_no_, hwc2layer.first);
          int retire_fence_merge = rf->merge(retire_fence_fd, acBuf);
          if(retire_fence_merge > 0){
            close(retire_fence_fd);
            retire_fence_fd = retire_fence_merge;
            HWC2_ALOGD_IF_DEBUG("RetireFence(%d) %s frame = %d merge %s sucess!", retire_fence_fd, acBuf, frame_no_, rf->getName().c_str());
          }else{
            HWC2_ALOGE("RetireFence(%d) %s frame = %d merge %s faile!", retire_fence_fd, acBuf,frame_no_, rf->getName().c_str());
          }
        }else{
          retire_fence_fd = dup(rf->getFd());
          continue;
        }
      }
    }
  }else{
    retire_fence_fd = fd;
  }
  d_retire_fence_.add(retire_fence_fd, acBuf);
  return;
}

bool SortByZpos(const DrmHwcLayer &drmHwcLayer1, const DrmHwcLayer &drmHwcLayer2){
    return drmHwcLayer1.iZpos_ < drmHwcLayer2.iZpos_;
}

HWC2::Error DrmHwcTwo::HwcDisplay::ModifyHwcLayerDisplayFrame(bool only_fb_scale) {

  bool need_overscan_by_scale = false;
  // RK3588 不支持Overscan
  if(gIsRK3588()){
    need_overscan_by_scale = true;
  }

  // 隔行扫描分辨率overscan效果比较差
  if(connector_ &&
     connector_->current_mode().id() > 0 &&
     connector_->current_mode().interlaced() > 0){
    need_overscan_by_scale = true;
  }

  // 使能 scale
  if(need_overscan_by_scale){

    for(auto &drmLayer : drm_hwc_layers_){
      if(only_fb_scale && !drmLayer.bFbTarget_)
        continue;
      drmLayer.ModifyDisplayFrameForOverscan(&ctx_);
    }
  }
  return HWC2::Error::None;
}

HWC2::Error DrmHwcTwo::HwcDisplay::InitDrmHwcLayer() {
  drm_hwc_layers_.clear();

  // now that they're ordered by z, add them to the composition
  for (auto &hwc2layer : layers_) {
    drm_hwc_layers_.emplace_back();
    DrmHwcLayer &drmHwclayer = drm_hwc_layers_.back();
    hwc2layer.second.PopulateDrmLayer(hwc2layer.first, &drmHwclayer, &ctx_, frame_no_);
  }

  std::sort(drm_hwc_layers_.begin(),drm_hwc_layers_.end(),SortByZpos);

  uint32_t client_id = 0;
  drm_hwc_layers_.emplace_back();
  DrmHwcLayer &client_target_layer = drm_hwc_layers_.back();
  client_layer_.PopulateFB(client_id, &client_target_layer, &ctx_, frame_no_, true);
#ifdef USE_LIBPQ
  if(handle_ == 0){
    int ret = client_layer_.DoPq(true, &client_target_layer, &ctx_);
    if(ret){
      HWC2_ALOGE("ClientLayer DoPq fail, ret = %d", ret);
    }
  }
#endif

  ALOGD_HWC2_DRM_LAYER_INFO((DBG_INFO),drm_hwc_layers_);

  return HWC2::Error::None;
}



HWC2::Error DrmHwcTwo::HwcDisplay::ValidatePlanes() {
  HWC2_ALOGD_IF_VERBOSE("display-id=%" PRIu64,handle_);
  int ret;

  InitDrmHwcLayer();

  // 需要修改 HwcLayer display frame 的情况列举：
  // 1. RK3588 不支持Overscan
  // 2. 隔行扫描分辨率 overscan 效果较差
  // 3. RK3528 运营商版本需要提供视频显示区域修改接口
  ModifyHwcLayerDisplayFrame(false);

  std::vector<DrmHwcLayer *> layers;
  layers.reserve(drm_hwc_layers_.size());
  for(size_t i = 0; i < drm_hwc_layers_.size(); ++i){
      layers.push_back(&drm_hwc_layers_[i]);
  }

  std::vector<PlaneGroup *> plane_groups;
  DrmDevice *drm = crtc_->getDrmDevice();
  plane_groups.clear();
  std::vector<PlaneGroup *> all_plane_groups = drm->GetPlaneGroups();
  for(auto &plane_group : all_plane_groups){
    if(plane_group->acquire(1 << crtc_->pipe(), handle_)){
      plane_groups.push_back(plane_group);
    }
  }

  std::tie(ret,
           composition_planes_) = planner_->TryHwcPolicy(layers, plane_groups, crtc_,
                                                         static_screen_opt_ ||
                                                         force_gles_ ||
                                                         connector_->isCropSpilt());
  if (ret){
    ALOGE("First, GLES policy fail ret=%d", ret);
    return HWC2::Error::BadConfig;
  }

  for (auto &drm_hwc_layer : drm_hwc_layers_) {
    if(drm_hwc_layer.bFbTarget_){
      if(drm_hwc_layer.bAfbcd_)
        client_layer_.EnableAfbc();
      else
        client_layer_.DisableAfbc();
      continue;
    }
    if(drm_hwc_layer.bMatch_){
      auto map_hwc2layer = layers_.find(drm_hwc_layer.uId_);
      map_hwc2layer->second.set_validated_type(HWC2::Composition::Device);
      if(drm_hwc_layer.bUseSr_){
        ALOGD_IF(LogLevel(DBG_INFO),"[%.4" PRIu32 "]=Device-Sr : %s",drm_hwc_layer.uId_,drm_hwc_layer.sLayerName_.c_str());
      }else if(drm_hwc_layer.bUseMemc_){
        ALOGD_IF(LogLevel(DBG_INFO),"[%.4" PRIu32 "]=Device-Memc : %s",drm_hwc_layer.uId_,drm_hwc_layer.sLayerName_.c_str());
      }else{
        ALOGD_IF(LogLevel(DBG_INFO),"[%.4" PRIu32 "]=Device : %s",drm_hwc_layer.uId_,drm_hwc_layer.sLayerName_.c_str());
      }
    }else{
      auto map_hwc2layer = layers_.find(drm_hwc_layer.uId_);
      map_hwc2layer->second.set_validated_type(HWC2::Composition::Client);
      ALOGD_IF(LogLevel(DBG_INFO),"[%.4" PRIu32 "]=Client : %s",drm_hwc_layer.uId_,drm_hwc_layer.sLayerName_.c_str());
    }
  }
#if (defined USE_LIBSR) || (defined USE_LIBSVEP_MEMC)
  // Update svep state.
  UpdateSvepState();
#endif

  return HWC2::Error::None;
}

void DrmHwcTwo::HwcDisplay::UpdateSvepState() {

  // 只有主屏可以开启SVEP模式，其他屏幕不需要更新SVEP状态
  if(handle_ > 0)
    return;

  bool exist_svep_layer = std::any_of(drm_hwc_layers_.begin(), drm_hwc_layers_.end(),
                                    [](const auto& drm_hwc_layer) {
                                      return drm_hwc_layer.bUseSr_ || drm_hwc_layer.bUseMemc_;
                                    });

  if(exist_svep_layer != bLastSvepState_){

    // story last_svep_state
    bLastSvepState_ = exist_svep_layer;
    if(exist_svep_layer){
      property_set("vendor.hwc.svep_state","1");
    }else{
      property_set("vendor.hwc.svep_state","0");
    }

    // update ddr state
    int fd_ddr_state = open("/sys/class/devfreq/dmc/system_status", O_WRONLY);
    if (fd_ddr_state < 0) {
        HWC2_ALOGD_IF_DEBUG("failed to open /sys/class/devfreq/dmc/system_status ret =%d", fd_ddr_state);
    }else{
      if(exist_svep_layer){
        // S 状态是专门提供给SVEP的场景变频, 进入SVEP场景变频
        write(fd_ddr_state, "S", sizeof(char));
      }else{
        // s 状态是专门提供给SVEP的场景变频, 退出SVEP场景变频
        write(fd_ddr_state, "s", sizeof(char));
      }
      close(fd_ddr_state);
    }
  }
  return ;
}

hwc2_drm_display_t* DrmHwcTwo::HwcDisplay::GetDisplayCtxPtr(){
  return &ctx_;
}

int DrmHwcTwo::HwcDisplay::ImportBuffers() {
  int ret = 0;
  // 匹配 DrmPlane 图层，请求获取 GemHandle
  bool use_client_layer = false;
  for (std::pair<const hwc2_layer_t, DrmHwcTwo::HwcLayer> &l : layers_){
    if(l.second.sf_type() == HWC2::Composition::Client){
      use_client_layer = true;
    }
    for (auto &drm_hwc_layer : drm_hwc_layers_) {
      // 如果图层没有采用Overlay,则不需要获取GemHandle
      if(!drm_hwc_layer.bMatch_)
        continue;
#if (defined USE_LIBSR) || (defined USE_LIBSVEP_MEMC)
      // 如果是超分处理后的图层，已经更新了GemHandle参数，则不再获取GemHandle
      if(drm_hwc_layer.bUseSr_)
        continue;
      // 如果是超分处理后的图层，已经更新了GemHandle参数，则不再获取GemHandle
      if(drm_hwc_layer.bUseMemc_)
        continue;
#endif
      // 如果是超分处理后的图层，已经更新了GemHandle参数，则不再获取GemHandle
      if(drm_hwc_layer.bUseRga_)
        continue;

      // SidebandStream 不需要获取GemHandle
      if(drm_hwc_layer.bSidebandStreamLayer_)
        continue;

      if(drm_hwc_layer.uId_ == l.first){
        ret = l.second.initOrGetGemhanleFromCache(&drm_hwc_layer);
        if (ret) {
          ALOGE("Failed to get_gemhanle layer-id=%" PRIu64  ", ret=%d", l.first, ret);
          return ret;
        }
      }
    }
  }

  // 若存在 GPU 合成, 则 ClientLayer 请求获取 GemHandle
  if(use_client_layer){
    for (auto &drm_hwc_layer : drm_hwc_layers_) {
      if(drm_hwc_layer.bFbTarget_){
        uint32_t client_id = 0;
        client_layer_.PopulateFB(client_id, &drm_hwc_layer, &ctx_, frame_no_, false);
        ret = client_layer_.initOrGetGemhanleFromCache(&drm_hwc_layer);
        if (ret) {
          ALOGE("Failed to get_gemhanle client_layer, ret=%d", ret);
          return ret;
        }
#ifdef USE_LIBPQ
        if(handle_ == 0){
          ret = client_layer_.DoPq(false, &drm_hwc_layer, &ctx_);
          if(ret){
            HWC2_ALOGE("ClientLayer DoPq fail, ret = %d", ret);
          }
        }
#endif
      }
    }
    ModifyHwcLayerDisplayFrame(true);
  }


  // 所有匹配 DrmPlane 图层，请求 Import 获取 FbId
  for (auto &drm_hwc_layer : drm_hwc_layers_) {
    if(!use_client_layer && drm_hwc_layer.bFbTarget_)
      continue;
    // 若不是Overlay图层则不进行ImportBuffer
    if(!drm_hwc_layer.bMatch_)
      continue;

    // SidebandStream 图层不需要执行 import 操作
    if(drm_hwc_layer.bSidebandStreamLayer_)
      continue;
    // 执行ImportBuffer,获取FbId
    ret = drm_hwc_layer.ImportBuffer(importer_.get());
    if (ret) {
      ALOGE("Failed to import layer, ret=%d", ret);
      return ret;
    }
  }

  return ret;
}


HWC2::Error DrmHwcTwo::HwcDisplay::CreateComposition() {
  HWC2_ALOGD_IF_VERBOSE("display-id=%" PRIu64,handle_);
  int ret;

  std::vector<DrmCompositionDisplayLayersMap> layers_map;
  layers_map.emplace_back();
  DrmCompositionDisplayLayersMap &map = layers_map.back();
  map.display = static_cast<int>(handle_);
  map.geometry_changed = true;

  ret = ImportBuffers();
  if(ret){
      HWC2_ALOGE("Failed to ImportBuffers, ret=%d", ret);
      return HWC2::Error::NoResources;
  }

  for (auto &drm_hwc_layer : drm_hwc_layers_) {
    if(drm_hwc_layer.bMatch_)
      map.layers.emplace_back(std::move(drm_hwc_layer));
  }

  std::unique_ptr<DrmDisplayComposition> composition = compositor_->CreateComposition();
  composition->Init(drm_, crtc_, importer_.get(), planner_.get(), frame_no_, handle_);

  // TODO: Don't always assume geometry changed
  ret = composition->SetLayers(map.layers.data(), map.layers.size(), true);
  if (ret) {
    ALOGE("Failed to set layers in the composition ret=%d", ret);
    return HWC2::Error::BadLayer;
  }
  for(auto &composition_plane :composition_planes_)
    ret = composition->AddPlaneComposition(std::move(composition_plane));

  ret = composition->DisableUnusedPlanes();
  if (ret) {
    ALOGE("Failed to plan the composition ret=%d", ret);
    return HWC2::Error::BadConfig;
  }

  // 利用 vendor.hwc.disable_releaseFence 属性强制关闭ReleaseFence，主要用于调试
  char value[PROPERTY_VALUE_MAX];
  property_get("vendor.hwc.disable_releaseFence", value, "0");
  if(atoi(value) == 0){
    ret = composition->CreateAndAssignReleaseFences(sync_timeline_);
    for (std::pair<const hwc2_layer_t, DrmHwcTwo::HwcLayer> &l : layers_){
      if(l.second.sf_type() == HWC2::Composition::Device){
        sp<ReleaseFence> rf = composition->GetReleaseFence(l.first);
        l.second.set_release_fence(rf);
      }else{
        l.second.set_release_fence(ReleaseFence::NO_FENCE);
      }
    }
    sp<ReleaseFence> rf = composition->GetReleaseFence(0);
    client_layer_.set_release_fence(rf);
    AddFenceToRetireFence(composition->take_out_fence());
  }

  // 配置 HDR mode
  composition->SetDisplayHdrMode(ctx_.hdr_mode, ctx_.dataspace);

  // 配置丢帧模式
  composition->SetDropMode(resource_manager_->IsCompositionDropMode());

  ret = compositor_->QueueComposition(std::move(composition));
  return HWC2::Error::None;
}

HWC2::Error DrmHwcTwo::HwcDisplay::PresentVirtualDisplay(int32_t *retire_fence) {
  ATRACE_CALL();

  HWC2_ALOGD_IF_VERBOSE("display-id=%" PRIu64,handle_);

  *retire_fence = -1;
  // 若虚拟屏图层数为0，则不做任何处理
  if(!layers_.size()){
    HWC2_ALOGD_IF_INFO("display %" PRIu64 " layer size is %zu", handle_, layers_.size());
    return HWC2::Error::None;
  }

  if(bUseWriteBack_ &&
     resource_manager_->isWBMode() &&
     !resource_manager_->IsDisableHwVirtualDisplay()){
    if(resource_manager_->isWBMode()){
      const std::shared_ptr<LayerInfoCache>
        bufferinfo = output_layer_.GetBufferInfo();

      // 每个目标的Buffer都需要初始化YUV数据
      if(!mHasResetBufferId_.count(bufferinfo->uBufferId_)){
        rga_buffer_t src;
        rga_buffer_t dst;
        rga_buffer_t pat;
        im_rect src_rect;
        im_rect dst_rect;
        im_rect pat_rect;

        memset(&src, 0x0, sizeof(rga_buffer_t));
        memset(&dst, 0x0, sizeof(rga_buffer_t));
        memset(&pat, 0x0, sizeof(rga_buffer_t));
        memset(&src_rect, 0x0, sizeof(im_rect));
        memset(&dst_rect, 0x0, sizeof(im_rect));
        memset(&pat_rect, 0x0, sizeof(im_rect));

        std::shared_ptr<DrmBuffer> resetBuffer = resource_manager_->GetResetWBBuffer();

        // Set src buffer info
        src.fd      = resetBuffer->GetFd();
        src.width   = resetBuffer->GetWidth();
        src.height  = resetBuffer->GetHeight();
        src.wstride = resetBuffer->GetStride();
        src.hstride = resetBuffer->GetHeightStride();
        src.format  = resetBuffer->GetFormat();

        // Set src rect info
        src_rect.x = 0;
        src_rect.y = 0;
        src_rect.width  = resetBuffer->GetWidth();
        src_rect.height = resetBuffer->GetHeight();

        // Set dst buffer info
        dst.fd      = bufferinfo->iFd_;
        dst.width   = bufferinfo->iWidth_;
        dst.height  = bufferinfo->iHeight_;
        dst.wstride = bufferinfo->iStride_;
        dst.hstride = bufferinfo->iHeightStride_;
        // 虚拟屏的格式通常为 HAL_PIXEL_FORMAT_IMPLEMENTATION_DEFINED
        // 由 Gralloc 决定具体格式，对应格式需要查询 uFourccFormat_ 才能确定
        // 实际申请格式，由于RGA不支持Fourcc格式，所以要做个转换。
        switch (bufferinfo->uFourccFormat_) {
          case DRM_FORMAT_BGR888:
            dst.format = HAL_PIXEL_FORMAT_RGB_888;
            break;
          case DRM_FORMAT_ARGB8888:
            dst.format = HAL_PIXEL_FORMAT_BGRA_8888;
            break;
          case DRM_FORMAT_XBGR8888:
            // dst.format = HAL_PIXEL_FORMAT_RGBX_8888;
            dst.format = HAL_PIXEL_FORMAT_BGRA_8888;
            break;
          case DRM_FORMAT_ABGR8888:
            dst.format = HAL_PIXEL_FORMAT_RGBA_8888;
            break;
          case DRM_FORMAT_ABGR2101010:
            dst.format = HAL_PIXEL_FORMAT_RGBA_1010102;
            break;
          //Fix color error in NenaMark2 and Taiji
          case DRM_FORMAT_BGR565:
            dst.format = HAL_PIXEL_FORMAT_RGB_565;
            break;
          case DRM_FORMAT_YVU420:
            dst.format = HAL_PIXEL_FORMAT_YV12;
            break;
          case DRM_FORMAT_NV12:
            dst.format = HAL_PIXEL_FORMAT_YCrCb_NV12;
            break;
          case DRM_FORMAT_NV12_10:
            dst.format = HAL_PIXEL_FORMAT_YCrCb_NV12_10;
            break;
          default:
            ALOGE("Cannot convert uFourccFormat_=%c%c%c%c to hal format, use default format nv12.",
                  bufferinfo->uFourccFormat_, bufferinfo->uFourccFormat_ >> 8,
                  bufferinfo->uFourccFormat_ >> 16, bufferinfo->uFourccFormat_ >> 24);
            dst.format = HAL_PIXEL_FORMAT_YCrCb_NV12;
        }

        dst_rect.x = 0;
        dst_rect.y = 0;
        dst_rect.width  = bufferinfo->iWidth_;
        dst_rect.height = bufferinfo->iHeight_;

        dst_rect.x = ALIGN_DOWN( dst_rect.x, 2);
        dst_rect.y = ALIGN_DOWN( dst_rect.y, 2);
        dst_rect.width  = ALIGN_DOWN( dst_rect.width, 2);
        dst_rect.height = ALIGN_DOWN( dst_rect.height, 2);

        if(AFBC_FORMAT_MOD_BLOCK_SIZE_16x16 == (bufferinfo->uModifier_ & AFBC_FORMAT_MOD_BLOCK_SIZE_16x16)){
          dst.rd_mode = IM_FBC_MODE;
        }

        IM_STATUS im_state = IM_STATUS_NOERROR;;

        im_opt_t imOpt;
        memset(&imOpt, 0x00, sizeof(im_opt_t));
        imOpt.core = IM_SCHEDULER_RGA3_CORE0 | IM_SCHEDULER_RGA3_CORE1;

        // Call Im2d 格式转换
        im_state = improcess(src, dst, pat, src_rect, dst_rect, pat_rect, 0, NULL, &imOpt, 0);

        if(im_state == IM_STATUS_SUCCESS){
          HWC2_ALOGD_IF_DEBUG("call im2d reset Success");
          mHasResetBufferId_.insert(bufferinfo->uBufferId_);
        }else{
          HWC2_ALOGE("call im2d reset fail, ret=%d Error=%s", im_state, imStrError(im_state));
        }
      }

      rga_buffer_t dst;
      im_rect dst_rect;

      memset(&dst, 0x00, sizeof(rga_buffer_t));
      memset(&dst_rect, 0x00, sizeof(im_rect));

      // Set dst buffer info
      dst.fd      = bufferinfo->iFd_;
      dst.width   = bufferinfo->iWidth_;
      dst.height  = bufferinfo->iHeight_;
      dst.wstride = bufferinfo->iStride_;
      dst.hstride = bufferinfo->iHeightStride_;
      // 虚拟屏的格式通常为 HAL_PIXEL_FORMAT_IMPLEMENTATION_DEFINED
      // 由 Gralloc 决定具体格式，对应格式需要查询 uFourccFormat_ 才能确定
      // 实际申请格式，由于RGA不支持Fourcc格式，所以垚做个转换。
      switch (bufferinfo->uFourccFormat_) {
        case DRM_FORMAT_BGR888:
          dst.format = HAL_PIXEL_FORMAT_RGB_888;
          break;
        case DRM_FORMAT_ARGB8888:
          dst.format = HAL_PIXEL_FORMAT_BGRA_8888;
          break;
        case DRM_FORMAT_XBGR8888:
            // dst.format = HAL_PIXEL_FORMAT_RGBX_8888;
            dst.format = HAL_PIXEL_FORMAT_BGRA_8888;
          break;
        case DRM_FORMAT_ABGR8888:
          dst.format = HAL_PIXEL_FORMAT_RGBA_8888;
          break;
        case DRM_FORMAT_ABGR2101010:
          dst.format = HAL_PIXEL_FORMAT_RGBA_1010102;
          break;
        //Fix color error in NenaMark2 and Taiji
        case DRM_FORMAT_BGR565:
          dst.format = HAL_PIXEL_FORMAT_RGB_565;
          break;
        case DRM_FORMAT_YVU420:
          dst.format = HAL_PIXEL_FORMAT_YV12;
          break;
        case DRM_FORMAT_NV12:
          dst.format = HAL_PIXEL_FORMAT_YCrCb_NV12;
          break;
        case DRM_FORMAT_NV12_10:
          dst.format = HAL_PIXEL_FORMAT_YCrCb_NV12_10;
          break;
        default:
          ALOGE("Cannot convert uFourccFormat_=%c%c%c%c to hal format, use default format nv12.",
                bufferinfo->uFourccFormat_, bufferinfo->uFourccFormat_ >> 8,
                bufferinfo->uFourccFormat_ >> 16, bufferinfo->uFourccFormat_ >> 24);
          dst.format = HAL_PIXEL_FORMAT_YCrCb_NV12;
      }

      // 为了确保录屏数据宽高比一致，故需要对目标的区域做修正
      DrmMode wbMode = resource_manager_->GetWBMode();
      if(wbMode.width()  != bufferinfo->iWidth_ ||
          wbMode.height() != bufferinfo->iHeight_){
        if((wbMode.width() * 1.0 / bufferinfo->iWidth_) >
            (wbMode.height() * 1.0 /  bufferinfo->iHeight_)){
          dst_rect.width = bufferinfo->iWidth_;
          dst_rect.height = (int)(bufferinfo->iWidth_ * wbMode.height() / (wbMode.width() * 1.0));
          dst_rect.x = 0;
          dst_rect.y = (bufferinfo->iHeight_ - dst_rect.height) / 2;
        }else{
          dst_rect.width = (int)((bufferinfo->iHeight_) * wbMode.width() / (wbMode.height() * 1.0));
          dst_rect.height = bufferinfo->iHeight_;
          dst_rect.x = (bufferinfo->iWidth_ - dst_rect.width) / 2;
          dst_rect.y = 0;
        }
      }else{
        dst_rect.x = 0;
        dst_rect.y = 0;
        dst_rect.width  = bufferinfo->iWidth_;
        dst_rect.height = bufferinfo->iHeight_;
      }

      dst_rect.x = ALIGN_DOWN( dst_rect.x, 2);
      dst_rect.y = ALIGN_DOWN( dst_rect.y, 2);
      dst_rect.width  = ALIGN_DOWN( dst_rect.width, 2);
      dst_rect.height = ALIGN_DOWN( dst_rect.height, 2);

      if(AFBC_FORMAT_MOD_BLOCK_SIZE_16x16 == (bufferinfo->uModifier_ & AFBC_FORMAT_MOD_BLOCK_SIZE_16x16)){
        dst.rd_mode = IM_FBC_MODE;
      }

      int ret = resource_manager_->OutputWBBuffer((int)handle_, dst, dst_rect, retire_fence, &wb_frame_no_);
      if(ret){
        HWC2_ALOGE("OutputWBBuffer fail!");
      }

      // 添加调试接口，抓打印传递给SurfaceFlinger的 Buffer
      char value[PROPERTY_VALUE_MAX];
      property_get("debug.wb.dump", value, "0");
      if(atoi(value) > 0) {
        output_layer_.DumpData();
      }
    }
  }else{
    if(client_layer_.acquire_fence() != NULL){
      if(client_layer_.acquire_fence()->wait(1500)){
          HWC2_ALOGE("WB client layer wait acquirefence 1500ms timeout!");
      }
    }
  }

  ++frame_no_;
  return HWC2::Error::None;
}
HWC2::Error DrmHwcTwo::HwcDisplay::PresentDisplay(int32_t *retire_fence) {
  ATRACE_CALL();

  if(isVirtual()){
    return PresentVirtualDisplay(retire_fence);;
  }

  int32_t merge_retire_fence = -1;
  // 拼接主屏需要遍历其他拼接子屏幕
  if(connector_->IsSpiltPrimary()){
    DoMirrorDisplay(&merge_retire_fence);
  }

  if(!init_success_){
    HWC2_ALOGD_IF_ERR("init_success_=%d skip.",init_success_);
    *retire_fence = merge_retire_fence;
    return HWC2::Error::None;
  }

  DumpAllLayerData();

  HWC2::Error ret;
  ret = CheckDisplayState();
  if(ret != HWC2::Error::None ||
     !validate_success_ ||
     connector_->type() == DRM_MODE_CONNECTOR_VIRTUAL){
    ALOGE_IF(LogLevel(DBG_ERROR),"Check display %" PRIu64 " state fail %s, %s,line=%d", handle_,
          validate_success_? "" : "or validate fail.",__FUNCTION__, __LINE__);
    if(ret == HWC2::Error::BadLayer){
      ClearDisplay();
    }
  }else{
    ret = CreateComposition();
    if (ret == HWC2::Error::BadLayer) {
      // Can we really have no client or device layers?
      *retire_fence = merge_retire_fence;
      return HWC2::Error::None;
    }
  }

  if(merge_retire_fence > 0){
    if(d_retire_fence_.get()->isValid()){
      char acBuf[32];
      sprintf(acBuf,"RTD%" PRIu64 "M-FN%d-%d", handle_, frame_no_, 0);
      sp<ReleaseFence> rt = sp<ReleaseFence>(new ReleaseFence(merge_retire_fence, acBuf));
      *retire_fence = rt->merge(d_retire_fence_.get()->getFd(), acBuf);
    }else{
      *retire_fence = merge_retire_fence;
    }
  }else{
    // The retire fence returned here is for the last frame, so return it and
    // promote the next retire fence
    *retire_fence = d_retire_fence_.get()->isValid() ? dup(d_retire_fence_.get()->getFd()) : -1;
    if(LogLevel(DBG_DEBUG)){
      HWC2_ALOGD_IF_DEBUG("Return RetireFence(%d) %s frame = %d Info: size=%d act=%d signal=%d err=%d",
                      d_retire_fence_.get()->isValid(),
                      d_retire_fence_.get()->getName().c_str(), frame_no_,
                      d_retire_fence_.get()->getSize(),d_retire_fence_.get()->getActiveCount(),
                      d_retire_fence_.get()->getSignaledCount(),d_retire_fence_.get()->getErrorCount());
    }
  }

  ++frame_no_;

  UpdateTimerState(!static_screen_opt_);

  if(IsActiveModeChange())
    drm_->FlipResolutionSwitchHandler((int)handle_);
  return HWC2::Error::None;
}

HWC2::Error DrmHwcTwo::HwcDisplay::SetActiveConfig(hwc2_config_t config) {
  HWC2_ALOGD_IF_VERBOSE("display-id=%" PRIu64 " config=%d",handle_,config);
  if(ctx_.bStandardSwitchResolution){
    auto mode = std::find_if(sf_modes_.begin(),
                             sf_modes_.end(),
                             [config](DrmMode const &m) {
                               return m.id() == config;
                             });
    if (mode == sf_modes_.end()) {
      ALOGE("Could not find active mode for %d", config);
      return HWC2::Error::BadConfig;
    }

  //  std::unique_ptr<DrmDisplayComposition> composition = compositor_
  //                                                           .CreateComposition();
  //  composition->Init(drm_, crtc_, importer_.get(), planner_.get(), frame_no_);
  //  int ret = composition->SetDisplayMode(*mode);
  //  ret = compositor_.QueueComposition(std::move(composition));
  //  if (ret) {
  //    ALOGE("Failed to queue dpms composition on %d", ret);
  //    return HWC2::Error::BadConfig;
  //  }
    connector_->set_best_mode(*mode);

    connector_->set_current_mode(*mode);
    ctx_.rel_xres = (*mode).h_display();
    ctx_.rel_yres = (*mode).v_display();

    // Setup the client layer's dimensions
    hwc_rect_t display_frame = {.left = 0,
                                .top = 0,
                                .right = static_cast<int>(mode->h_display()),
                                .bottom = static_cast<int>(mode->v_display())};
    client_layer_.SetLayerDisplayFrame(display_frame);
    hwc_frect_t source_crop = {.left = 0.0f,
                              .top = 0.0f,
                              .right = mode->h_display() + 0.0f,
                              .bottom = mode->v_display() + 0.0f};
    client_layer_.SetLayerSourceCrop(source_crop);

    drm_->UpdateDisplayMode(handle_);
    // SetDisplayModeInfo cost 2.5ms - 5ms, a A few cases cost 10ms - 20ms
    connector_->SetDisplayModeInfo(handle_);
  }else{
    if(connector_->isCropSpilt()){
      int32_t srcX, srcY, srcW, srcH;
      connector_->getCropInfo(&srcX, &srcY, &srcW, &srcH);
      hwc_rect_t display_frame = {.left = 0,
                                  .top = 0,
                                  .right = static_cast<int>(ctx_.framebuffer_width),
                                  .bottom = static_cast<int>(ctx_.framebuffer_height)};
      client_layer_.SetLayerDisplayFrame(display_frame);
      hwc_frect_t source_crop = {.left = srcX + 0.0f,
                                 .top  = srcY + 0.0f,
                                 .right = srcX + srcW + 0.0f,
                                 .bottom = srcY + srcH + 0.0f};
      client_layer_.SetLayerSourceCrop(source_crop);

    }else{
      // Setup the client layer's dimensions
      hwc_rect_t display_frame = {.left = 0,
                                  .top = 0,
                                  .right = static_cast<int>(ctx_.framebuffer_width),
                                  .bottom = static_cast<int>(ctx_.framebuffer_height)};
      client_layer_.SetLayerDisplayFrame(display_frame);
      hwc_frect_t source_crop = {.left = 0.0f,
                                .top = 0.0f,
                                .right = ctx_.framebuffer_width + 0.0f,
                                .bottom = ctx_.framebuffer_height + 0.0f};
      client_layer_.SetLayerSourceCrop(source_crop);
    }
    // VRR
    UpdateRefreshRate(config);
  }

  return HWC2::Error::None;
}

HWC2::Error DrmHwcTwo::HwcDisplay::UpdateRefreshRate(hwc2_config_t config) {
  HWC2_ALOGD_IF_VERBOSE("display-id=%" PRIu64 " config=%d", handle_, config);
  if(!bVrrDisplay_)
    return HWC2::Error::None;

  const std::vector<int> vrr_mode = connector_->vrr_modes();
  if(config < vrr_mode.size()){
    int refresh_rate = vrr_mode[config];
    int ret = drm_->UpdateVrrRefreshRate(handle_, refresh_rate);
    if(ret){
      HWC2_ALOGE("display=%" PRIu64 " config=%d refresh_rate=%d UpdateVrrRefreshRate fail!",
                  handle_, config, refresh_rate);
      return HWC2::Error::BadConfig;
    }
  }

  return HWC2::Error::BadConfig;
}

HWC2::Error DrmHwcTwo::HwcDisplay::SetClientTarget(buffer_handle_t target,
                                                   int32_t acquire_fence,
                                                   int32_t dataspace,
                                                   hwc_region_t /*damage*/) {
  HWC2_ALOGD_IF_VERBOSE("display-id=%" PRIu64 ", Buffer=%p, acq_fence=%d, dataspace=%x",
                         handle_,target,acquire_fence,dataspace);

  // 动态切换刷新率过程中SurfaceFlinger会出现 SetClientTarget target=null的情况
  // 为了避免错误日志打印，故暂时对这种情况进行规避；
  if(target == NULL){
    HWC2_ALOGW("Buffer is NULL, skip SetClientTarget");
    return HWC2::Error::None;
  }

  client_layer_.CacheBufferInfo(target);
  client_layer_.set_acquire_fence(sp<AcquireFence>(new AcquireFence(acquire_fence)));
  client_layer_.SetLayerDataspace(dataspace);
  return HWC2::Error::None;
}

HWC2::Error DrmHwcTwo::HwcDisplay::SetColorMode(int32_t mode) {
  HWC2_ALOGD_IF_VERBOSE("display-id=%" PRIu64 ", mode=%x",handle_,mode);

  if (mode != HAL_COLOR_MODE_NATIVE)
    return HWC2::Error::BadParameter;

  color_mode_ = mode;
  return HWC2::Error::None;
}

HWC2::Error DrmHwcTwo::HwcDisplay::SetColorTransform(const float *matrix,
                                                     int32_t hint) {
  HWC2_ALOGD_IF_VERBOSE("display-id=%" PRIu64 ", hint=%x",handle_,hint);
  // TODO: Force client composition if we get this
  // hint definition from android_color_transform_t in system/core/libsystem/include/system/graphics-base-v1.0.h
  force_gles_ = (hint > 0);
  unsupported(__func__, matrix, hint);
  return HWC2::Error::None;
}

HWC2::Error DrmHwcTwo::HwcDisplay::SetOutputBuffer(buffer_handle_t buffer,
                                                   int32_t release_fence) {
  HWC2_ALOGD_IF_VERBOSE("display-id=%" PRIu64 ", buffer=%p, rel_fence=%d",handle_,buffer,release_fence);
  // TODO: Need virtual display support
  output_layer_.set_output_buffer(buffer);
  if(release_fence > 0){
    /* release_fence will be close in this file hardware/interfaces/
     *   graphics/composer/2.1/utils/passthrough/include/composer-passthrough/2.1/HwcHal.h+319
     *       int32_t err = mDispatch.setOutputBuffer(mDevice, display, buffer, releaseFence);
     *       // unlike in setClientTarget, releaseFence is owned by us
     *       if (err == HWC2_ERROR_NONE && releaseFence >= 0) {
     *           close(releaseFence);
     *       }
     */
    int32_t new_release_fence = dup(release_fence);
    String8 output;
    output.appendFormat("%s-F%" PRIu32 "-Fd%d",__FUNCTION__,frame_no_,new_release_fence);
    sp<ReleaseFence> release = sp<ReleaseFence>(new ReleaseFence(new_release_fence, output.c_str()));
    output_layer_.set_release_fence(release);
    HWC2_ALOGD_IF_DEBUG("Release=%d(%d) %s Info: size=%d act=%d signal=%d err=%d",
                            release->getFd(),release->isValid(),release->getName().c_str(),
                            release->getSize(), release->getActiveCount(),
                            release->getSignaledCount(), release->getErrorCount());
  }
  return HWC2::Error::None;
}

HWC2::Error DrmHwcTwo::HwcDisplay::SyncPowerMode() {
  HWC2_ALOGD_IF_VERBOSE("display-id=%" PRIu64 " bNeedSyncPMState_=%d",handle_, bNeedSyncPMState_);

  if(!init_success_){
    HWC2_ALOGE("init_success_=%d skip.",init_success_);
    return HWC2::Error::BadDisplay;
  }

  if(!bNeedSyncPMState_){
    HWC2_ALOGI("bNeedSyncPMState_=%d don't need to sync PowerMode state.",bNeedSyncPMState_);
    return HWC2::Error::None;
  }

  HWC2::Error error = SetPowerMode((int32_t)mPowerMode_);
  if(error != HWC2::Error::None){
    HWC2_ALOGE("SetPowerMode fail %d", error);
    return error;
  }

  bNeedSyncPMState_ = false;
  return HWC2::Error::None;
}

HWC2::Error DrmHwcTwo::HwcDisplay::SetPowerMode(int32_t mode_in) {
  HWC2_ALOGD_IF_VERBOSE("display-id=%" PRIu64 ", mode_in=%d",handle_,mode_in);

  // 拼接屏幕主屏需要更新拼接副屏的电源状态
  if(connector_->IsSpiltPrimary()){
    for (auto &conn : drm_->connectors()) {
      if(!conn->isCropSpilt()){
        continue;
      }
      int display_id = conn->display();
      if(!conn->IsSpiltPrimary()){
        auto &display = resource_manager_->GetHwc2()->displays_.at(display_id);
        display.SetPowerMode(mode_in);
      }
    }
  }

  uint64_t dpms_value = 0;
  mPowerMode_ = static_cast<HWC2::PowerMode>(mode_in);
  switch (mPowerMode_) {
    case HWC2::PowerMode::Off:
      dpms_value = DRM_MODE_DPMS_OFF;
      break;
    case HWC2::PowerMode::On:
      dpms_value = DRM_MODE_DPMS_ON;
      break;
    case HWC2::PowerMode::Doze:
    case HWC2::PowerMode::DozeSuspend:
      ALOGI("Power mode %d is unsupported\n", mPowerMode_);
      return HWC2::Error::Unsupported;
    default:
      ALOGI("Power mode %d is BadParameter\n", mPowerMode_);
      return HWC2::Error::BadParameter;
  };

  if(!init_success_){
    bNeedSyncPMState_ = true;
    HWC2_ALOGE("init_success_=%d skip.",init_success_);
    return HWC2::Error::BadDisplay;
  }

  std::unique_ptr<DrmDisplayComposition> composition = compositor_->CreateComposition();
  composition->Init(drm_, crtc_, importer_.get(), planner_.get(), frame_no_, handle_);
  composition->SetDpmsMode(dpms_value);
  int ret = compositor_->QueueComposition(std::move(composition));
  if (ret) {
    ALOGE("Failed to apply the dpms composition ret=%d", ret);
    return HWC2::Error::BadParameter;
  }

  int fb0_fd = resource_manager_->getFb0Fd();
  if(fb0_fd<=0)
    ALOGE_IF(LogLevel(DBG_ERROR),"%s,line=%d fb0_fd = %d can't operation /dev/graphics/fb0 node.",
              __FUNCTION__,__LINE__,fb0_fd);
  int fb_blank = 0;
  if(dpms_value == DRM_MODE_DPMS_OFF)
    fb_blank = FB_BLANK_POWERDOWN;
  else if(dpms_value == DRM_MODE_DPMS_ON)
    fb_blank = FB_BLANK_UNBLANK;
  else
    ALOGE("dpmsValue is invalid value= %" PRIu64 "",dpms_value);

  if(fb_blank != fb_blanked && fb0_fd > 0){
    int err = ioctl(fb0_fd, FBIOBLANK, fb_blank);
    ALOGD_IF(LogLevel(DBG_DEBUG),"%s Notice fb_blank to fb=%d", __FUNCTION__, fb_blank);
    if (err < 0) {
      ALOGE("fb_blank ioctl failed(%d) display=%" PRIu64 ",fb_blank=%d,dpmsValue=%" PRIu64 "",
          errno,handle_,fb_blank,dpms_value);
    }
  }

  fb_blanked = fb_blank;

  if(dpms_value == DRM_MODE_DPMS_OFF){
    ClearDisplay();
    ret = drm_->ReleaseDpyRes(handle_, DmcuReleaseByPowerMode);
    if (ret) {
      HWC2_ALOGE("Failed to ReleaseDpyRes for display=%" PRIu64 " %d\n", handle_, ret);
    }
    if(isRK3566(resource_manager_->getSocId())){
      int display_id = drm_->GetCommitMirrorDisplayId();
      DrmConnector *extend = drm_->GetConnectorForDisplay(display_id);
      if(extend != NULL){
        int extend_display_id = extend->display();
        auto &display = resource_manager_->GetHwc2()->displays_.at(extend_display_id);
        display.ClearDisplay();
        ret = drm_->ReleaseDpyRes(extend_display_id);
        if (ret) {
          HWC2_ALOGE("Failed to ReleaseDpyRes for display=%d %d\n", extend_display_id, ret);
        }
      }
    }
  }else{
    if(connector_->hotplug()){
      ret = connector_->UpdateModes();
      if (ret) {
        HWC2_ALOGE("Failed to UpdateModes for display=%" PRIu64 " ret=%d\n", handle_, ret);
      }
    }
    HoplugEventTmeline();
    ret = UpdateDisplayMode();
    if (ret) {
      HWC2_ALOGE("Failed to UpdateDisplayMode for display=%" PRIu64 " ret=%d\n", handle_, ret);
    }
    ret = drm_->BindDpyRes(handle_);
    if (ret) {
      HWC2_ALOGE("Failed to BindDpyRes for display=%" PRIu64 " ret=%d\n", handle_, ret);
    }
    UpdateDisplayInfo();
    if(isRK3566(resource_manager_->getSocId())){
      ALOGD_IF(LogLevel(DBG_DEBUG),"SetPowerMode display-id=%" PRIu64 ",soc is rk3566" ,handle_);
      int display_id = drm_->GetCommitMirrorDisplayId();
      DrmConnector *extend = drm_->GetConnectorForDisplay(display_id);
      if(extend != NULL){
        int extend_display_id = extend->display();
        ret = drm_->BindDpyRes(extend_display_id);
        if (ret) {
          HWC2_ALOGE("Failed to BindDpyRes for display=%d ret=%d\n", extend_display_id, ret);
        }
      }
    }
  }
  return HWC2::Error::None;
}

HWC2::Error DrmHwcTwo::HwcDisplay::SetVsyncEnabled(int32_t enabled) {
  HWC2_ALOGD_IF_VERBOSE("display-id=%" PRIu64 ", enable=%d",handle_,enabled);
  vsync_worker_.VSyncControl(HWC2_VSYNC_ENABLE == enabled);
  return HWC2::Error::None;
}
HWC2::Error DrmHwcTwo::HwcDisplay::ValidateVirtualDisplay(uint32_t *num_types,
                                                          uint32_t *num_requests) {
    if(LogLevel(DBG_INFO)){
      DumpDisplayLayersInfo();
    }

    if(!layers_.size()){
      HWC2_ALOGI("display %" PRIu64 " layer size is %zu, %s,line=%d", handle_, layers_.size(),
            __FUNCTION__, __LINE__);
      return HWC2::Error::None;
    }

    // 强制设置系统刷新为30帧
    InvalidateControl(30,-1);

    bUseWriteBack_ = true;

    // 提供仅 Sideband 模式下开启 hw Virtual Display 功能接口
    char value[PROPERTY_VALUE_MAX];
    property_get("vendor.hwc.only_sideband_use_wb", value, "0");
    if(atoi(value) > 0){
      bUseWriteBack_ = false;
      bool exist_sideband_stream = false;
      // 只有存在 SidebandStream 的录屏才会使用 WriteBack;
      for (std::pair<const hwc2_layer_t, DrmHwcTwo::HwcLayer> &l : layers_) {
        DrmHwcTwo::HwcLayer &layer = l.second;
        if(layer.sf_type() == HWC2::Composition::Sideband){
          exist_sideband_stream = true;
        }
      }

      if(exist_sideband_stream){
        bUseWriteBack_ = true;
      }
    }

    HWC2_ALOGI("frame_no_ = %d", frame_no_);

    // 获取 WriteBack id
    int WBDisplayId = resource_manager_->GetWBDisplay();
    // 检查是否正确使能 Hw Virtual Display 功能
    if(WBDisplayId >= 0 &&
       resource_manager_->isWBMode() &&
       !resource_manager_->IsDisableHwVirtualDisplay()){
      DrmConnector *connector = drm_->GetConnectorForDisplay(WBDisplayId);
      if (!connector) {
        HWC2_ALOGD_IF_DEBUG("Failed to get WB connector for display=%" PRIu64 " wb-display %d frame_no=%d", handle_, WBDisplayId, frame_no_);
        bUseWriteBack_ = false;
      }else{
        if(connector->state() != DRM_MODE_CONNECTED){
          HWC2_ALOGD_IF_DEBUG("WB Connector %u type=%s, type_id=%d, state is DRM_MODE_DISCONNECTED,"
                              " skip init. display=%" PRIu64 " wb-display %d frame_no=%d",
                connector->id(),drm_->connector_type_str(connector->type()),connector->type_id(),
                handle_, WBDisplayId, frame_no_);;
          bUseWriteBack_ = false;
        }

        DrmCrtc *crtc = drm_->GetCrtcForDisplay(WBDisplayId);
        if (!crtc) {
          HWC2_ALOGD_IF_DEBUG("Failed to get crtc for display=%" PRIu64 " wb-display %d frame_no=%d", handle_, WBDisplayId, frame_no_);
          bUseWriteBack_ = false;
        }

        if(resource_manager_->GetFinishWBBufferSize() == 0){
          HWC2_ALOGD_IF_DEBUG("WB buffer not ready, display=%" PRIu64 " wb-display %d frame_no=%d", handle_, WBDisplayId, frame_no_);
          bUseWriteBack_ = false;
        }
      }
    }else{
      bUseWriteBack_ = false;
      HWC2_ALOGD_IF_DEBUG("WB display %d is invalid, disable HW VDS.", WBDisplayId);
    }

    for (std::pair<const hwc2_layer_t, DrmHwcTwo::HwcLayer> &l : layers_) {
      DrmHwcTwo::HwcLayer &layer = l.second;
      if(bUseWriteBack_){
        layer.set_validated_type(HWC2::Composition::Device);
      }else{
        layer.set_validated_type(HWC2::Composition::Client);
      }
      ++*num_types;
    }
    *num_requests = 0;

    return HWC2::Error::None;
}
HWC2::Error DrmHwcTwo::HwcDisplay::ValidateDisplay(uint32_t *num_types,
                                                   uint32_t *num_requests) {
  ATRACE_CALL();
  HWC2_ALOGD_IF_VERBOSE("display-id=%" PRIu64 ,handle_);

  // 虚拟屏
  if(isVirtual()){
    return ValidateVirtualDisplay(num_types, num_requests);;
  }

  if(LogLevel(DBG_DEBUG))
    DumpDisplayLayersInfo();

  if(!init_success_){
    HWC2_ALOGD_IF_ERR("init_success_=%d skip.",init_success_);
    if(connector_->IsSpiltPrimary()){
      for (std::pair<const hwc2_layer_t, DrmHwcTwo::HwcLayer> &l : layers_){
          l.second.set_validated_type(HWC2::Composition::Client);
      }
    }else{
      for (std::pair<const hwc2_layer_t, DrmHwcTwo::HwcLayer> &l : layers_){
          l.second.set_validated_type(l.second.sf_type());
      }
    }
    return HWC2::Error::None;
  }
  // Enable/disable debug log
  UpdateLogLevel();
  UpdateBCSH();
  UpdateHdmiOutputFormat();
  UpdateOverscan();
  if(!ctx_.bStandardSwitchResolution){
    UpdateDisplayMode();
    drm_->UpdateDisplayMode(handle_);
    if(isRK3566(resource_manager_->getSocId())){
      int display_id = drm_->GetCommitMirrorDisplayId();
      drm_->UpdateDisplayMode(display_id);
    }
    UpdateDisplayInfo();
  }

  // 虚拟屏幕
  if(connector_->type() == DRM_MODE_CONNECTOR_VIRTUAL){
      for (std::pair<const hwc2_layer_t, DrmHwcTwo::HwcLayer> &l : layers_){
          l.second.set_validated_type(l.second.sf_type());
      }
      return HWC2::Error::None;
  }

  // update sideband mode
  UpdateSidebandMode();

  *num_types = 0;
  *num_requests = 0;

  HWC2::Error ret;

  for (std::pair<const hwc2_layer_t, DrmHwcTwo::HwcLayer> &l : layers_){
    if(gIsRK3528()){
      l.second.set_validated_type(HWC2::Composition::Device);
    }else{
      l.second.set_validated_type(HWC2::Composition::Client);
    }
  }

  ret = CheckDisplayState();
  if(ret != HWC2::Error::None){
    ALOGE_IF(LogLevel(DBG_ERROR),"Check display %" PRIu64 " state fail, %s,line=%d", handle_,
          __FUNCTION__, __LINE__);
    composition_planes_.clear();
    validate_success_ = false;
    return HWC2::Error::None;
  }

  ret = ValidatePlanes();
  if (ret != HWC2::Error::None){
    ALOGE("%s fail , ret = %d,line = %d",__FUNCTION__,ret,__LINE__);
    validate_success_ = false;
    return HWC2::Error::BadConfig;
  }

  SwitchHdrMode();
  // Static screen opt
  UpdateTimerEnable();
  // Enable Self-refresh mode.
  SelfRefreshEnable();
  for (std::pair<const hwc2_layer_t, DrmHwcTwo::HwcLayer> &l : layers_) {
    DrmHwcTwo::HwcLayer &layer = l.second;
    // We can only handle layers of Device type, send everything else to SF
    if (layer.validated_type() != HWC2::Composition::Device) {
      layer.set_validated_type(HWC2::Composition::Client);
      ++*num_types;
    }
  }

  if(!client_layer_.isAfbc()){
    ++(*num_requests);
  }
  validate_success_ = true;
  return *num_types ? HWC2::Error::HasChanges : HWC2::Error::None;
}

#ifdef ANDROID_S
HWC2::Error DrmHwcTwo::HwcDisplay::GetDisplayConnectionType(uint32_t *outType) {
	if (connector_->internal())
		*outType = static_cast<uint32_t>(HWC2::DisplayConnectionType::Internal);
	else if (connector_->external())
		*outType = static_cast<uint32_t>(HWC2::DisplayConnectionType::External);
	else
		return HWC2::Error::BadConfig;

	return HWC2::Error::None;
}

HWC2::Error DrmHwcTwo::HwcDisplay::GetDisplayVsyncPeriod(
				hwc2_vsync_period_t *outVsyncPeriod /* ns */) {
	supported(__func__);

	DrmMode const &mode = connector_->active_mode();

	if (mode.id() == 0)
		return HWC2::Error::BadConfig;

	*outVsyncPeriod = 1E9 / mode.v_refresh();

	return HWC2::Error::None;
}
#endif //ANDROID_S

HWC2::Error DrmHwcTwo::HwcLayer::SetCursorPosition(int32_t x, int32_t y) {
  HWC2_ALOGD_IF_VERBOSE("layer-id=%d"", x=%d, y=%d" ,id_,x,y);
  mCurrentState.cursor_x_ = x;
  mCurrentState.cursor_y_ = y;
  return HWC2::Error::None;
}

int DrmHwcTwo::HwcDisplay::DumpDisplayInfo(String8 &output){

  output.appendFormat(" DisplayId=%" PRIu64 ", Connector %u, Type = %s-%u, Connector state = %s\n",handle_,
                        connector_->id(),
                        isVirtual() ? "Virtual" : drm_->connector_type_str(connector_->type()),
                        connector_->type_id(),
                        connector_->state() == DRM_MODE_CONNECTED ? "DRM_MODE_CONNECTED" : "DRM_MODE_DISCONNECTED");

  if(connector_->state() != DRM_MODE_CONNECTED)
    return -1;

  DrmMode const &active_mode = connector_->active_mode();
  if (active_mode.id() == 0){
    return -1;
  }

  output.appendFormat("  NumHwLayers=%zu, activeModeId=%u, %s%c%.2f, colorMode = %d, bStandardSwitchResolution=%d\n",
                        get_layers().size(),
                        active_mode.id(), active_mode.name().c_str(),'p' ,active_mode.v_refresh(),
                        color_mode_,ctx_.bStandardSwitchResolution);
  if(sf_modes_.size() > 0){
    uint32_t idx = 0;

    for (const DrmMode &mode : sf_modes_) {
      if(active_mode.id() == mode.id())
        output.appendFormat("    Config[%2u] = %s%c%.2f mode-id=%d (active)\n",idx, mode.name().c_str(), 'p' , mode.v_refresh(),mode.id());
      else
        output.appendFormat("    Config[%2u] = %s%c%.2f mode-id=%d \n",idx, mode.name().c_str(), 'p' , mode.v_refresh(),mode.id());
      idx++;
    }
  }

  output.append(
              "------+-----+-----------+-----------+--------------------+-------------+------------+--------------------------------+------------------------+------------+--------+------------\n"
              "  id  |  z  |  sf-type  |  hwc-type |       handle       |  transform  |    blnd    |     source crop (l,t,r,b)      |          frame         | dataspace  |  mFps  | name       \n"
              "------+-----+-----------+-----------+--------------------+-------------+------------+--------------------------------+------------------------+------------+--------+------------\n");
  for (uint32_t z_order = 0; z_order <= layers_.size(); z_order++) {
    for (auto &map_layer : layers_) {
      HwcLayer &layer = map_layer.second;
      if(layer.z_order() == z_order){
        layer.DumpLayerInfo(output);
        break;
      }
    }
  }

  output.append("------+-----+-----------+-----------+--------------------+-------------+------------+--------------------------------+------------------------+------------+--------+------------\n");
  output.append("DrmHwcLayer Dump:\n");

  for(auto &drmHwcLayer : drm_hwc_layers_)
      drmHwcLayer.DumpInfo(output);

  return 0;
}

int DrmHwcTwo::HwcDisplay::DumpDisplayLayersInfo(String8 &output){

  output.appendFormat(" DisplayId=%" PRIu64 ", Connector %u, Type = %s-%u, Connector state = %s , frame_no = %d\n",handle_,
                        connector_->id(),
                        isVirtual() ? "Virtual" : drm_->connector_type_str(connector_->type()),
                        connector_->type_id(),
                        connector_->state() == DRM_MODE_CONNECTED ? "DRM_MODE_CONNECTED" : "DRM_MODE_DISCONNECTED",
                        frame_no_);

  output.append(
              "------+-----+-----------+-----------+--------------------+-------------+------------+--------------------------------+------------------------+------------+--------+------------\n"
              "  id  |  z  |  req-type | fina-type |       handle       |  transform  |    blnd    |     source crop (l,t,r,b)      |          frame         | dataspace  |  mFps  | name       \n"
              "------+-----+-----------+-----------+--------------------+-------------+------------+--------------------------------+------------------------+------------+--------+------------\n");
  for (uint32_t z_order = 0; z_order <= layers_.size(); z_order++) {
    for (auto &map_layer : layers_) {
      HwcLayer &layer = map_layer.second;
      if(layer.z_order() == z_order){
        layer.DumpLayerInfo(output);
        break;
      }
    }
  }
  output.append("------+-----+-----------+-----------+--------------------+-------------+------------+--------------------------------+------------------------+------------+--------+------------\n");
  return 0;
}

int DrmHwcTwo::HwcDisplay::DumpDisplayLayersInfo(){
  String8 output;
  output.appendFormat(" DisplayId=%" PRIu64 ", Connector %u, Type = %s-%u, Connector state = %s , frame_no = %d\n",handle_,
                        connector_->id(),
                        isVirtual() ? "Virtual" : drm_->connector_type_str(connector_->type()),
                        connector_->type_id(),
                        connector_->state() == DRM_MODE_CONNECTED ? "DRM_MODE_CONNECTED" : "DRM_MODE_DISCONNECTED",
                        frame_no_);

  output.append(
              "------+-----+-----------+-----------+--------------------+-------------+------------+--------------------------------+------------------------+------------+--------+------------\n"
              "  id  |  z  |  sf-type  |  hwc-type |       handle       |  transform  |    blnd    |     source crop (l,t,r,b)      |          frame         | dataspace  |  mFps  | name       \n"
              "------+-----+-----------+-----------+--------------------+-------------+------------+--------------------------------+------------------------+------------+--------+------------\n");
  ALOGD("%s",output.string());
  for (uint32_t z_order = 0; z_order <= layers_.size(); z_order++) {
    for (auto &map_layer : layers_) {
      HwcLayer &layer = map_layer.second;
      if(layer.z_order() == z_order){
        output.clear();
        layer.DumpLayerInfo(output);
        ALOGD("%s",output.string());
        break;
      }
    }
  }
  output.clear();
  output.append("------+-----+-----------+-----------+--------------------+-------------+------------+--------------------------------+------------------------+------------+--------+------------\n");
  ALOGD("%s",output.string());
  return 0;
}
int DrmHwcTwo::HwcDisplay::DumpAllLayerData(){
  char pro_value[PROPERTY_VALUE_MAX];
  property_get( PROPERTY_TYPE ".dump",pro_value,0);
  if(!strcmp(pro_value,"true")){
    for (auto &map_layer : layers_) {
      HwcLayer &layer = map_layer.second;
      layer.DumpData();
    }
    if(client_layer_.buffer() != NULL)
      client_layer_.DumpData();

    for(auto &drm_layer : drm_hwc_layers_){
      if(drm_layer.bUseSr_ && drm_layer.pSrBuffer_){
        drm_layer.pSrBuffer_->DumpData();
      }
    }
    for(auto &drm_layer : drm_hwc_layers_){
      if(drm_layer.bUseRga_ && drm_layer.pRgaBuffer_){
        drm_layer.pRgaBuffer_->DumpData();
      }
    }
  }

  return 0;
}

int DrmHwcTwo::HwcDisplay::HoplugEventTmeline(){
  ctx_.hotplug_timeline++;
  return 0;
}

int DrmHwcTwo::HwcDisplay::UpdateDisplayMode(){

  if(!ctx_.bStandardSwitchResolution){
    int timeline;
    int display_id = static_cast<int>(handle_);
    timeline = property_get_int32("vendor.display.timeline", -1);
    if(timeline && timeline == ctx_.display_timeline && ctx_.hotplug_timeline == drm_->timeline())
      return 0;
    ctx_.display_timeline = timeline;
    ctx_.hotplug_timeline = drm_->timeline();
    int ret = connector_->UpdateDisplayMode(display_id, timeline);
    if(!ret){
      const DrmMode best_mode = connector_->best_mode();
      connector_->set_current_mode(best_mode);
      // will change display resolution, to clear all display.
      if(!(connector_->current_mode() == connector_->active_mode())){
        ClearDisplay();
        // 标识 Display mode 发生改变
        ActiveModeChange(true);
      }
    }

    if(isRK3566(resource_manager_->getSocId())){
      bool mirror_mode = true;
      display_id = drm_->GetCommitMirrorDisplayId();
      DrmConnector *conn_mirror = drm_->GetConnectorForDisplay(display_id);
      if(!conn_mirror || conn_mirror->state() != DRM_MODE_CONNECTED){
        ALOGI_IF(LogLevel(DBG_DEBUG),"%s,line=%d disable bCommitMirrorMode",__FUNCTION__,__LINE__);
        mirror_mode = false;
      }

      if(mirror_mode){
        ret = conn_mirror->UpdateDisplayMode(display_id, timeline);
        if(!ret){
          const DrmMode best_mode = conn_mirror->best_mode();
          conn_mirror->set_current_mode(best_mode);
        }
      }
    }
  }
  return 0;
}

int DrmHwcTwo::HwcDisplay::UpdateDisplayInfo(){
  if(!ctx_.bStandardSwitchResolution){
    const DrmMode active_mode = connector_->active_mode();
    if(connector_->isHorizontalSpilt()){
      ctx_.rel_xres = active_mode.h_display() / DRM_CONNECTOR_SPILT_RATIO;
      ctx_.rel_yres = active_mode.v_display();
      if(handle_ >= DRM_CONNECTOR_SPILT_MODE_MASK){
        ctx_.rel_xoffset = active_mode.h_display() / DRM_CONNECTOR_SPILT_RATIO;
        ctx_.rel_yoffset = 0;//best_mode.v_display() / 2;
      }
    }else if(connector_->isCropSpilt()){
      ctx_.rel_xres = active_mode.h_display();
      ctx_.rel_yres = active_mode.v_display();
    }else{
      ctx_.rel_xres = active_mode.h_display();
      ctx_.rel_yres = active_mode.v_display();
    }
    ctx_.dclk = active_mode.clock();
  }
  return 0;
}

int DrmHwcTwo::HwcDisplay::UpdateOverscan(){
  connector_->UpdateOverscan(handle_, ctx_.overscan_value);
  return 0;
}

int DrmHwcTwo::HwcDisplay::UpdateHdmiOutputFormat(){
  int timeline = 0;

  timeline = property_get_int32( "vendor.display.timeline", -1);
  /*
   * force update propetry when timeline is zero or not exist.
   */
  if (timeline && timeline == ctx_.display_timeline && ctx_.hotplug_timeline == drm_->timeline())
      return 0;

  connector_->UpdateOutputFormat(handle_, timeline);

  if(isRK3566(resource_manager_->getSocId())){
    bool mirror_mode = true;
    int display_id = drm_->GetCommitMirrorDisplayId();
    DrmConnector *conn_mirror = drm_->GetConnectorForDisplay(display_id);
    if(!conn_mirror || conn_mirror->state() != DRM_MODE_CONNECTED){
      ALOGI_IF(LogLevel(DBG_DEBUG),"%s,line=%d disable bCommitMirrorMode",__FUNCTION__,__LINE__);
      mirror_mode = false;
    }

    if(mirror_mode){
      conn_mirror->UpdateOutputFormat(display_id, timeline);
    }
  }

  return 0;
}

int DrmHwcTwo::HwcDisplay::UpdateBCSH(){

  int timeline = property_get_int32("vendor.display.timeline", -1);
  /*
   * force update propetry when timeline is zero or not exist.
   */
  if (timeline && timeline == ctx_.bcsh_timeline)
    return 0;
  connector_->UpdateBCSH(handle_,timeline);

  if(isRK3566(resource_manager_->getSocId())){
    bool mirror_mode = true;
    int display_id = drm_->GetCommitMirrorDisplayId();
    DrmConnector *conn_mirror = drm_->GetConnectorForDisplay(display_id);
    if(!conn_mirror || conn_mirror->state() != DRM_MODE_CONNECTED){
      ALOGI_IF(LogLevel(DBG_DEBUG),"%s,line=%d disable bCommitMirrorMode",__FUNCTION__,__LINE__);
      mirror_mode = false;
    }
    if(mirror_mode){
      conn_mirror->UpdateBCSH(display_id,timeline);
    }
  }

  ctx_.bcsh_timeline = timeline;

  return 0;
}
bool DrmHwcTwo::HwcDisplay::DisableHdrModeRK3588(){
  DrmMode active_mode = connector_->active_mode();
  // 如果是8K分辨率模式，HDR片源没有走 overlay 策略，则关闭HDR
  // 主要原因是VOP硬件限制，要求最底层为 HDR dataspace，GPU合成输出为SDR，
  // 不满足条件，则需要关闭HDR模式
  if(active_mode.id() > 0 && active_mode.is_8k_mode()){
    for(auto &drmHwcLayer : drm_hwc_layers_){
      if(drmHwcLayer.bHdr_){
        // 没有被硬件图层匹配，则说明使用GPU合成
        if(!drmHwcLayer.bMatch_){
          HWC2_ALOGD_IF_DEBUG("HDR video compose by GLES on 8k resolution, Fource Disable HDR mode.");
          return true;
        }
      }
    }
  }
  return false;
}

bool DrmHwcTwo::HwcDisplay::DisableHdrMode(){
  bool exist_hdr_layer = false;
  int  hdr_area_ratio = 0;

  for(auto &drmHwcLayer : drm_hwc_layers_){
    if(drmHwcLayer.bHdr_){
      exist_hdr_layer = true;
      int src_w = (int)(drmHwcLayer.source_crop.right - drmHwcLayer.source_crop.left);
      int src_h = (int)(drmHwcLayer.source_crop.bottom - drmHwcLayer.source_crop.top);
      int src_area_size = src_w * src_h;
      int dis_w = drmHwcLayer.display_frame.right - drmHwcLayer.display_frame.left;
      int dis_h = drmHwcLayer.display_frame.bottom - drmHwcLayer.display_frame.top;
      int dis_area_size = dis_w * dis_h;
      // 视频缩小倍数*10，*10的原因是 vendor.hwc.hdr_video_area 为整型，不支持浮点数
      hdr_area_ratio = dis_area_size * 10 / src_area_size;
      int screen_size = ctx_.rel_xres * ctx_.rel_yres;
      // 视频占屏幕面积*10，取缩小倍数与视频占用屏幕面积较大值，实现与操作
      // 即需要同时满足视频缩小60%，屏占60%才关闭HDR模式
      if(hdr_area_ratio < (dis_area_size * 10 / screen_size))
        hdr_area_ratio = dis_area_size * 10 / screen_size;
    }
  }

  if(exist_hdr_layer){
    // 存在 HDR图层，判断是否存在强制关闭HDR属性，若存在则关闭HDR模式
    char value[PROPERTY_VALUE_MAX];
    property_get("persist.vendor.hwc.hdr_force_disable", value, "0");
    if(atoi(value) > 0){
      if(ctx_.hdr_mode != DRM_HWC_SDR){
        HWC2_ALOGD_IF_DEBUG("Exit HDR mode success");
        property_set("vendor.hwc.hdr_state","FORCE-NORMAL");
      }
      HWC2_ALOGD_IF_DEBUG("Fource Disable HDR mode.");
      return true;
    }

    // 存在 HDR图层，判断HDR视频的屏幕占比与缩放倍率，满足条件则关闭HDR模式
    property_get("persist.vendor.hwc.hdr_video_area", value, "6");
    if(atoi(value) > hdr_area_ratio){
      if(ctx_.hdr_mode != DRM_HWC_SDR){
        HWC2_ALOGD_IF_DEBUG("Exit HDR mode success");
        property_set("vendor.hwc.hdr_state","FORCE-NORMAL");
      }
      HWC2_ALOGD_IF_DEBUG("Force Disable HDR mode.");
      return true;
    }
  }

  if(!exist_hdr_layer && ctx_.hdr_mode != DRM_HWC_SDR){
    ALOGD_IF(LogLevel(DBG_DEBUG),"Exit HDR mode success");
    property_set("vendor.hwc.hdr_state","NORMAL");
    return true;
  }

  return false;
}

int DrmHwcTwo::HwcDisplay::EnableMetadataHdrMode(DrmHwcLayer& hdrLayer){
  HWC2_ALOGD_IF_INFO("Id=%d Name=%s ", hdrLayer.uId_, hdrLayer.sLayerName_.c_str());

  if(ctx_.display_type == DRM_MODE_CONNECTOR_TV){
    HWC2_ALOGD_IF_INFO("RK3528 TV unsupport HDR2SDR, Id=%d Name=%s eDataSpace_=0x%x eotf=%d",
                      hdrLayer.uId_, hdrLayer.sLayerName_.c_str(),
                      hdrLayer.eDataSpace_,
                      hdrLayer.uEOTF);
    return -1;
  }

  if(hdrLayer.bSideband2_){
    HWC2_ALOGD_IF_ERR("Sideband2 layer skip, Id=%d Name=%s zpos=%d match=%d",
                      hdrLayer.uId_, hdrLayer.sLayerName_.c_str(), hdrLayer.iZpos_, hdrLayer.bMatch_);
    return -1;
  }

  // Next hdr zpos must be 0
  if(hdrLayer.iZpos_ > 0){
    HWC2_ALOGD_IF_ERR("Next hdr zpos must be 0, Id=%d Name=%s zpos=%d",
                      hdrLayer.uId_, hdrLayer.sLayerName_.c_str(), hdrLayer.iZpos_);
    return -1;
  }

  if(!hdrLayer.bMatch_){
    HWC2_ALOGD_IF_ERR("Next hdr not overlay, Id=%d Name=%s zpos=%d match=%d",
                      hdrLayer.uId_, hdrLayer.sLayerName_.c_str(), hdrLayer.iZpos_, hdrLayer.bMatch_);
    return -1;
  }


  // 算法解析库是否存在
  DrmHdrParser* dhp = DrmHdrParser::Get();
  if(dhp == NULL){
    HWC2_ALOGD_IF_ERR("Fail to get DrmHdrParser, use SDR mode, Id=%d Name=%s ", hdrLayer.uId_, hdrLayer.sLayerName_.c_str());
    return -1;
  }

  // 显示器是否支持HDR
  bool is_hdr_display = connector_->is_hdmi_support_hdr();
  // 是否为 HDR 片源
  bool is_input_hdr = hdrLayer.bHdr_;
  // 2:自动模式: 电视支持 HDR模式播放HDR视频则切换HDR模式，否则使用SDR模式
  // 1:HDR模式: 等同自动模式
  // 0:SDR模式: 电视强制使用SDR模式，HDR片源也采用SDR显示
  int user_hdr_mode = hwc_get_int_property("persist.sys.vivid.hdr_mode", "2");
  // 可能存在模式：SDR2SDR,HDR2SDR,SDR2HDR,HDR2HDR
  bool is_output_hdr = false;
  // 2:自动模式: 电视支持 HDR模式播放HDR视频则切换HDR模式，否则使用SDR模式
  // 1:HDR模式: 电视支持 HDR模式则强制使用HDR模式，SDR片源也采用HDR模式输出
  if((user_hdr_mode == 2 && is_hdr_display && is_input_hdr) ||
     (user_hdr_mode == 1 && is_hdr_display && is_input_hdr)){
    is_output_hdr = true;
  }else{
    is_output_hdr = false;
  }

  // 如果输入是 SDR 且输出为SDR,则不需要进行任何处理
  if(is_input_hdr == false && is_output_hdr == false){
    HWC2_ALOGD_IF_INFO("Use SDR2SDR mode.");
    return -1;
  }


  DrmGralloc* gralloc = DrmGralloc::getInstance();
  if(gralloc == NULL){
    HWC2_ALOGD_IF_INFO("DrmGralloc is null, Use SDR2SDR mode.");
    return -1;
  }

  // debug 打印耗时
  long t0 = __currentTime();

  // 用于判断是否存在 metadata 信息
  bool codec_meta_exist = false;
  // 获取存储 metadata 信息的offset
  int64_t offset = gralloc->hwc_get_offset_of_dynamic_hdr_metadata(hdrLayer.sf_handle);
  if(offset < 0){
    HWC2_ALOGD_IF_ERR("Fail to get hdr metadata offset, Id=%d Name=%s ", hdrLayer.uId_, hdrLayer.sLayerName_.c_str());
  }
  // offset > 0 则认为存在 Metadata
  codec_meta_exist = offset > 0;
  HWC2_ALOGD_IF_INFO("dynamic_hdr_metadata offset=%" PRIi64, offset);

  // 初始化参数
  memset(&hdrLayer.metadataHdrParam_, 0x00, sizeof(rk_hdr_parser_params_t));
  // 如果输出模式为HDR
  if(is_output_hdr){
    // Android bt2020 or bt709
    switch(hdrLayer.eDataSpace_ & HAL_DATASPACE_STANDARD_MASK){
      case HAL_DATASPACE_STANDARD_BT2020:
      case HAL_DATASPACE_STANDARD_BT2020_CONSTANT_LUMINANCE :
        hdrLayer.metadataHdrParam_.hdr_hdmi_meta.color_prim = COLOR_PRIM_BT2020;
        break;
      default:
        hdrLayer.metadataHdrParam_.hdr_hdmi_meta.color_prim = COLOR_PRIM_BT709;
        break;
    }

    // 片源为 HLG，且电视支持 HLG ，则选择 HLG bypass 模式
    if(hdrLayer.uEOTF == HLG && connector_->isSupportHLG()){
      hdrLayer.metadataHdrParam_.hdr_hdmi_meta.eotf = SINK_EOTF_HLG;
    // 片源为 HDR10，且电视支持 HDR10 ，则选择 HDR10 bypass 模式
    }else if(hdrLayer.uEOTF == SMPTE_ST2084 && connector_->isSupportSt2084()){
      hdrLayer.metadataHdrParam_.hdr_hdmi_meta.eotf = SINK_EOTF_ST2084;
    // 若没有匹配的 HDR 模式，则优先使用 HDR10 输出
    }else{
      if(connector_->isSupportSt2084()){
        hdrLayer.metadataHdrParam_.hdr_hdmi_meta.eotf = SINK_EOTF_ST2084;
      }else if(connector_->isSupportHLG()){
        hdrLayer.metadataHdrParam_.hdr_hdmi_meta.eotf = SINK_EOTF_HLG;
      }
    }
    // hdr10 最小亮度应该是0.05,算法提供接口是要求外部数值 0.05*100=5
    hdrLayer.metadataHdrParam_.hdr_hdmi_meta.dst_min = 5;
    hdrLayer.metadataHdrParam_.hdr_hdmi_meta.dst_max = hwc_get_int_property("persist.sys.vivid.max_brightness", "1000") * 100;
  }else{
    hdrLayer.metadataHdrParam_.hdr_hdmi_meta.color_prim = COLOR_PRIM_BT709;
    hdrLayer.metadataHdrParam_.hdr_hdmi_meta.eotf = SINK_EOTF_GAMMA_SDR;
    hdrLayer.metadataHdrParam_.hdr_hdmi_meta.dst_min = 10;
    hdrLayer.metadataHdrParam_.hdr_hdmi_meta.dst_max = hwc_get_int_property("persist.sys.vivid.max_brightness", "100") * 100;
  }

  void *cpu_addr = NULL;
  if(codec_meta_exist){
    // 获取Medata地址
    cpu_addr = gralloc->hwc_get_handle_lock(hdrLayer.sf_handle, hdrLayer.iWidth_, hdrLayer.iHeight_);
    if(cpu_addr == NULL){
      HWC2_ALOGD_IF_ERR("Fail to lock dma buffer, Id=%d Name=%s ", hdrLayer.uId_, hdrLayer.sLayerName_.c_str());
      hdrLayer.metadataHdrParam_.codec_meta_exist = false;
      hdrLayer.metadataHdrParam_.p_hdr_codec_meta = NULL;
    }else{
      uint16_t *u16_cpu_metadata = (uint16_t *)((uint8_t *)cpu_addr + offset);
      hdrLayer.metadataHdrParam_.codec_meta_exist = codec_meta_exist;
      hdrLayer.metadataHdrParam_.p_hdr_codec_meta = (RkMetaHdrHeader*)u16_cpu_metadata;

      // 如果当前设置 hdr 显示模式为 HLG bypass，则需要检测 HLG 片源是否为 dynamic Hdr
      // 若为 dynamic Hdr，则需要将输出模式修改为 Hdr10,若不支持Hdr10,则输出SDR
      // 原因是目前 VOP3 是参考 VividHdr标准实现，标准内部没有支持 dynamic hlg hdr 直出模式
      if(hdrLayer.uEOTF == HLG &&
         hdrLayer.metadataHdrParam_.hdr_hdmi_meta.eotf == SINK_EOTF_HLG){
        int ret = dhp->MetadataHdrparserFormat(&hdrLayer.metadataHdrParam_,
                                               &hdrLayer.metadataHdrFmtInfo_);
        if(ret){
          HWC2_ALOGD_IF_ERR("MetadataHdrparserFormat, Id=%d Name=%s ", hdrLayer.uId_, hdrLayer.sLayerName_.c_str());
          hdrLayer.metadataHdrParam_.hdr_hdmi_meta.eotf = SINK_EOTF_ST2084;
        }else{
          if(hdrLayer.metadataHdrFmtInfo_.hdr_format == HDRVIVID){
            if(connector_->isSupportSt2084()){
              hdrLayer.metadataHdrParam_.hdr_hdmi_meta.eotf = SINK_EOTF_ST2084;
              HWC2_ALOGD_IF_INFO("Id=%d Name=%s is HLG dynamic, convert to HDR10.", hdrLayer.uId_, hdrLayer.sLayerName_.c_str());
            }else{
              hdrLayer.metadataHdrParam_.hdr_hdmi_meta.eotf = SINK_EOTF_GAMMA_SDR;
              HWC2_ALOGD_IF_INFO("Id=%d Name=%s is HLG dynamic, convert to SDR.", hdrLayer.uId_, hdrLayer.sLayerName_.c_str());
            }
          }
        }
      }
    }
  }else{
    // Metadata 不存在，则使用 Android Dataspace
    hdrLayer.metadataHdrParam_.codec_meta_exist = false;
    hdrLayer.metadataHdrParam_.p_hdr_codec_meta = NULL;

    // Android bt2020 or bt709
    switch(hdrLayer.eDataSpace_ & HAL_DATASPACE_STANDARD_MASK){
      case HAL_DATASPACE_STANDARD_BT2020:
      case HAL_DATASPACE_STANDARD_BT2020_CONSTANT_LUMINANCE :
        hdrLayer.metadataHdrParam_.hdr_dataspace_info.color_prim = COLOR_PRIM_BT2020;
        break;
      default:
        hdrLayer.metadataHdrParam_.hdr_dataspace_info.color_prim = COLOR_PRIM_BT709;
        break;
    }

    // Android st2084 / HLG / SDR
    switch(hdrLayer.eDataSpace_ & HAL_DATASPACE_TRANSFER_MASK){
      case HAL_DATASPACE_TRANSFER_ST2084:
        hdrLayer.metadataHdrParam_.hdr_dataspace_info.eotf = SINK_EOTF_ST2084;
        break;
      case HAL_DATASPACE_TRANSFER_HLG :
        hdrLayer.metadataHdrParam_.hdr_dataspace_info.eotf = SINK_EOTF_HLG;
        break;
      default:
        hdrLayer.metadataHdrParam_.hdr_dataspace_info.eotf = SINK_EOTF_GAMMA_SDR;
        break;
    }

    // Android full / limit range
    switch(hdrLayer.eDataSpace_ & HAL_DATASPACE_RANGE_MASK){
      case HAL_DATASPACE_RANGE_FULL:
        hdrLayer.metadataHdrParam_.hdr_dataspace_info.range = RANGE_FULL;
        break;
      case HAL_DATASPACE_RANGE_LIMITED :
        hdrLayer.metadataHdrParam_.hdr_dataspace_info.range = RANGE_LIMITED;
        break;
      default:
        hdrLayer.metadataHdrParam_.hdr_dataspace_info.range = RANGE_LIMITED;
        break;
    }
  }

  hdrLayer.metadataHdrParam_.hdr_user_cfg.hdr_pq_max_y_mode = 0;
  hdrLayer.metadataHdrParam_.hdr_user_cfg.hdr_dst_gamma = 2.2;
  hdrLayer.metadataHdrParam_.hdr_user_cfg.s2h_sm_ratio = 1.0;
  hdrLayer.metadataHdrParam_.hdr_user_cfg.s2h_scale_ratio = 1.0;
  hdrLayer.metadataHdrParam_.hdr_user_cfg.s2h_sdr_color_space = 2;
  hdrLayer.metadataHdrParam_.hdr_user_cfg.hdr_debug_cfg.print_input_meta = 0;
  hdrLayer.metadataHdrParam_.hdr_user_cfg.hdr_debug_cfg.hdr_log_level = 0;

  if(hwc_get_int_property("vendor.hwc.vivid_hdr_debug", "0") > 0){
    hdrLayer.uEOTF = hwc_get_int_property("vendor.hwc.vivid_layer_eotf", "0");
    hdrLayer.metadataHdrParam_.codec_meta_exist = hwc_get_bool_property("vendor.hwc.vivid_codec_meta_exist", "true");
    hdrLayer.metadataHdrParam_.hdr_hdmi_meta.color_prim = hwc_get_int_property("vendor.hwc.vivid_color_prim", "0");
    hdrLayer.metadataHdrParam_.hdr_hdmi_meta.eotf = hwc_get_int_property("vendor.hwc.vivid_eotf", "0");
    hdrLayer.metadataHdrParam_.hdr_hdmi_meta.red_x = hwc_get_int_property("vendor.hwc.vivid_red_x", "0");
    hdrLayer.metadataHdrParam_.hdr_hdmi_meta.red_y = hwc_get_int_property("vendor.hwc.vivid_red_y", "0");
    hdrLayer.metadataHdrParam_.hdr_hdmi_meta.green_x = hwc_get_int_property("vendor.hwc.vivid_green_x", "0");
    hdrLayer.metadataHdrParam_.hdr_hdmi_meta.green_y = hwc_get_int_property("vendor.hwc.vivid_green_y", "0");
    hdrLayer.metadataHdrParam_.hdr_hdmi_meta.white_point_x = hwc_get_int_property("vendor.hwc.vivid_white_point_x", "0");
    hdrLayer.metadataHdrParam_.hdr_hdmi_meta.white_point_y = hwc_get_int_property("vendor.hwc.vivid_white_point_y", "0");
    hdrLayer.metadataHdrParam_.hdr_hdmi_meta.dst_min = hwc_get_int_property("vendor.hwc.vivid_dst_min", "10");
    hdrLayer.metadataHdrParam_.hdr_hdmi_meta.dst_max = hwc_get_int_property("vendor.hwc.vivid_dst_max", "10000");

    hdrLayer.metadataHdrParam_.hdr_dataspace_info.color_prim = hwc_get_int_property("vendor.hwc.vivid_dataspace_pri", "0");
    hdrLayer.metadataHdrParam_.hdr_dataspace_info.eotf = hwc_get_int_property("vendor.hwc.vivid_dataspace_eotf", "0");
    hdrLayer.metadataHdrParam_.hdr_dataspace_info.range = hwc_get_int_property("vendor.hwc.vivid_dataspace_range", "0");

    hdrLayer.metadataHdrParam_.hdr_user_cfg.hdr_pq_max_y_mode = hwc_get_int_property("vendor.hwc.vivid_hdr_pq_max_y_mode", "0");
    hdrLayer.metadataHdrParam_.hdr_user_cfg.hdr_dst_gamma = (hwc_get_int_property("vendor.hwc.vivid_hdr_dst_gamma", "22") * 1.0 / 10);
    hdrLayer.metadataHdrParam_.hdr_user_cfg.s2h_sm_ratio = hwc_get_int_property("vendor.hwc.vivid_s2h_sm_ratio", "10") * 1.0 / 10;
    hdrLayer.metadataHdrParam_.hdr_user_cfg.s2h_scale_ratio = hwc_get_int_property("vendor.hwc.vivid_s2h_scale_ratio", "10") * 1.0 / 10;
    hdrLayer.metadataHdrParam_.hdr_user_cfg.s2h_sdr_color_space = hwc_get_int_property("vendor.hwc.vivid_s2h_sdr_color_space", "2");
    hdrLayer.metadataHdrParam_.hdr_user_cfg.hdr_debug_cfg.print_input_meta = hwc_get_int_property("vendor.hwc.vivid_print_input_meta", "1");
    hdrLayer.metadataHdrParam_.hdr_user_cfg.hdr_debug_cfg.hdr_log_level = hwc_get_int_property("vendor.hwc.vivid_hdr_log_level", "7");
  }

  HWC2_ALOGD_IF_INFO("hdr_hdmi_meta: user_hdr_mode(%d) layer eDataSpace=0x%x eotf=%d => codec_meta_exist(%d) hdr_dataspace_info: color_prim=%d eotf=%d range=%d",
            user_hdr_mode,
            hdrLayer.eDataSpace_,
            hdrLayer.uEOTF,
            hdrLayer.metadataHdrParam_.codec_meta_exist,
            hdrLayer.metadataHdrParam_.hdr_dataspace_info.color_prim,
            hdrLayer.metadataHdrParam_.hdr_dataspace_info.eotf,
            hdrLayer.metadataHdrParam_.hdr_dataspace_info.range);
  HWC2_ALOGD_IF_INFO("hdr_hdmi_meta: color_prim=%d eotf=%d red_x=%d red_y=%d green_x=%d green_y=%d white_point_x=%d white_point_y=%d dst_min=%d dst_max=%d",
            hdrLayer.metadataHdrParam_.hdr_hdmi_meta.color_prim,
            hdrLayer.metadataHdrParam_.hdr_hdmi_meta.eotf,
            hdrLayer.metadataHdrParam_.hdr_hdmi_meta.red_x,
            hdrLayer.metadataHdrParam_.hdr_hdmi_meta.red_y,
            hdrLayer.metadataHdrParam_.hdr_hdmi_meta.green_x,
            hdrLayer.metadataHdrParam_.hdr_hdmi_meta.green_y,
            hdrLayer.metadataHdrParam_.hdr_hdmi_meta.white_point_x,
            hdrLayer.metadataHdrParam_.hdr_hdmi_meta.white_point_y,
            hdrLayer.metadataHdrParam_.hdr_hdmi_meta.dst_min,
            hdrLayer.metadataHdrParam_.hdr_hdmi_meta.dst_max);

  HWC2_ALOGD_IF_INFO("hdr_user_cfg: hdr_pq_max_y_mode=%d hdr_dst_gamma=%f s2h_sm_ratio=%f s2h_scale_ratio=%f s2h_sdr_color_space=%d print_input_meta=%d hdr_log_level=%d",
            hdrLayer.metadataHdrParam_.hdr_user_cfg.hdr_pq_max_y_mode,
            hdrLayer.metadataHdrParam_.hdr_user_cfg.hdr_dst_gamma,
            hdrLayer.metadataHdrParam_.hdr_user_cfg.s2h_sm_ratio,
            hdrLayer.metadataHdrParam_.hdr_user_cfg.s2h_scale_ratio,
            hdrLayer.metadataHdrParam_.hdr_user_cfg.s2h_sdr_color_space,
            hdrLayer.metadataHdrParam_.hdr_user_cfg.hdr_debug_cfg.print_input_meta,
            hdrLayer.metadataHdrParam_.hdr_user_cfg.hdr_debug_cfg.hdr_log_level);

  int ret = dhp->MetadataHdrParser(&hdrLayer.metadataHdrParam_);
  if(ret){
    HWC2_ALOGD_IF_ERR("Fail to call MetadataHdrParser ret=%d Id=%d Name=%s ",
                      ret,
                      hdrLayer.uId_,
                      hdrLayer.sLayerName_.c_str());
    if(cpu_addr != NULL)
      gralloc->hwc_get_handle_unlock(hdrLayer.sf_handle);
    return ret;
  }

  if(cpu_addr != NULL)
    gralloc->hwc_get_handle_unlock(hdrLayer.sf_handle);

  hdrLayer.IsMetadataHdr_ = true;
  ctx_.hdr_mode = DRM_HWC_METADATA_HDR;
  ctx_.dataspace = hdrLayer.eDataSpace_;
  HWC2_ALOGD_IF_INFO("Use HdrParser mode.");
  return 0;
}

int DrmHwcTwo::HwcDisplay::EnableHdrMode(DrmHwcLayer& hdrLayer){
  HWC2_ALOGD_IF_INFO("Id=%d Name=%s ", hdrLayer.uId_, hdrLayer.sLayerName_.c_str());
  if(connector_->is_hdmi_support_hdr()){
    if(ctx_.hdr_mode != DRM_HWC_HDR10){
      ALOGD_IF(LogLevel(DBG_DEBUG),"Enable HDR mode success");
      ctx_.hdr_mode = DRM_HWC_HDR10;
      ctx_.dataspace = hdrLayer.eDataSpace_;
      property_set("vendor.hwc.hdr_state","HDR");
    }
    return 0;
  }
  return -1;
}


int DrmHwcTwo::HwcDisplay::UpdateSidebandMode(){

  if(handle_ > 0){
    return 0;
  }

  // UpdateSideband state
  DrmVideoProducer* dvp = DrmVideoProducer::getInstance();
  if(!dvp->IsValid()){
    return -1;
  }

  // 判断是否存在Sideband图层，并保存tunnel_id信息
  int tunnel_id = 0;
  for (std::pair<const hwc2_layer_t, DrmHwcTwo::HwcLayer> &l : layers_){
    if(l.second.isSidebandLayer()){
      tunnel_id = l.second.getTunnelId();
    }
  }

  // 若存在合法 tunnel_id
  if(tunnel_id > 0){
    if(tunnel_id != iLastTunnelId_){
      if(iLastTunnelId_ > 0){
        // tunnel id 不一致则先断开连接旧连接
        int ret = dvp->DestoryConnection((int(handle_) + 1000), iLastTunnelId_);
        if(ret){
          HWC2_ALOGD_IF_ERR("DestoryConnection display=%" PRIu64 " tunnel-id=%d fail ret=%d", handle_, iLastTunnelId_, ret);
        }else{
          HWC2_ALOGD_IF_INFO("DestoryConnection display=%" PRIu64 " tunnel-id=%d success ret=%d", handle_, iLastTunnelId_, ret);
        }
      }
      // 创建新连接
      int ret = dvp->CreateConnection((int(handle_) + 1000), tunnel_id);
      if(ret){
        HWC2_ALOGD_IF_ERR("CreateConnection display=%" PRIu64 " fail tunnel-id=%d ret=%d", handle_, tunnel_id, ret);
      }else{
        HWC2_ALOGD_IF_INFO("CreateConnection display=%" PRIu64 " tunnel-id=%d success ret=%d", handle_, tunnel_id, ret);
      }
      iLastTunnelId_ = tunnel_id;
    }
  }else{
    if(iLastTunnelId_ > 0){
      // tunnel id 不一致则先断开连接旧连接
      int ret = dvp->DestoryConnection((int(handle_) + 1000), iLastTunnelId_);
      if(ret){
        HWC2_ALOGD_IF_ERR("DestoryConnection display=%" PRIu64 " tunnel-id=%d fail ret=%d", handle_, iLastTunnelId_, ret);
      }else{
        HWC2_ALOGD_IF_INFO("DestoryConnection display=%" PRIu64 " tunnel-id=%d success ret=%d", handle_, iLastTunnelId_, ret);
        iLastTunnelId_ = 0;
      }
    }
  }
  return 0;
}

int DrmHwcTwo::HwcDisplay::SwitchHdrMode(){
  // 需要HDR模式,找到 HDR layer,判断当前采用HDR模式
  for(auto &drmHwcLayer : drm_hwc_layers_){
    if(drmHwcLayer.bYuv_){
      // RK3528 HDR 模式特殊处理
      if(gIsRK3528()){
        if(EnableMetadataHdrMode(drmHwcLayer) == 0){
          return 0;
        }
      // 其他平台的 HDR 模式处理
      }else{
        if(drmHwcLayer.bHdr_){
          // 其他平台通用的判断是否需要进入HDR模式逻辑
          if(DisableHdrMode()){
            ctx_.hdr_mode = DRM_HWC_SDR;
            ctx_.dataspace = HAL_DATASPACE_UNKNOWN;
            return 0;
          }
          // RK3588 平台特殊的判断逻辑
          if(DisableHdrModeRK3588()){
            ctx_.hdr_mode = DRM_HWC_SDR;
            ctx_.dataspace = HAL_DATASPACE_UNKNOWN;
            return 0;
          }

          if(!EnableHdrMode(drmHwcLayer)){
            return 0;
          }
        }
      }
    }
  }

  ctx_.hdr_mode = DRM_HWC_SDR;
  ctx_.dataspace = HAL_DATASPACE_UNKNOWN;
  return 0;
}

int DrmHwcTwo::HwcDisplay::UpdateTimerEnable(){
  bool enable_timer = true;
  for(auto &drmHwcLayer : drm_hwc_layers_){
    // Video
    if(drmHwcLayer.bYuv_){
      ALOGD_IF(LogLevel(DBG_DEBUG),"Yuv %s timer!",static_screen_timer_enable_ ? "Enable" : "Disable");
      enable_timer = false;
      break;
    }

#if (defined USE_LIBSR) || (defined USE_LIBSVEP_MEMC)
    // Sr
    if(drmHwcLayer.bUseSr_){
      ALOGD_IF(LogLevel(DBG_DEBUG),"Sr %s timer!",static_screen_timer_enable_ ? "Enable" : "Disable");
      enable_timer = false;
      break;
    }
    // Memc
    if(drmHwcLayer.bUseMemc_){
      ALOGD_IF(LogLevel(DBG_DEBUG),"Sr %s timer!",static_screen_timer_enable_ ? "Enable" : "Disable");
      enable_timer = false;
      break;
    }
#endif

    // Sideband
    if(drmHwcLayer.bSidebandStreamLayer_){
      enable_timer = false;
      break;
    }

    // Surface w/h is larger than FB
    int crop_w = static_cast<int>(drmHwcLayer.source_crop.right - drmHwcLayer.source_crop.left);
    int crop_h = static_cast<int>(drmHwcLayer.source_crop.bottom - drmHwcLayer.source_crop.top);
    if(crop_w * crop_h > ctx_.framebuffer_width * ctx_.framebuffer_height){
      ALOGD_IF(LogLevel(DBG_DEBUG),"LargeSurface %s timer!",static_screen_timer_enable_ ? "Enable" : "Disable");
      enable_timer = false;
      break;
    }
  }
  static_screen_timer_enable_ = enable_timer;
  return 0;
}
int DrmHwcTwo::HwcDisplay::SelfRefreshEnable(){
  bool enable_self_refresh = false;
  int self_fps = 10;
  for(auto &drmHwcLayer : drm_hwc_layers_){

#if (defined USE_LIBSR) || (defined USE_LIBSVEP_MEMC)
    // Sr
    if(drmHwcLayer.bUseSr_){
      HWC2_ALOGD_IF_DEBUG("Sr Enable SelfRefresh!");
      enable_self_refresh = true;
      self_fps = 10;
      break;
    }
    // MEMC
    if(drmHwcLayer.bUseMemc_){
      HWC2_ALOGD_IF_DEBUG("Memc Enable SelfRefresh!");
      enable_self_refresh = true;
      self_fps = 60;
      break;
    }
#endif

    if(drmHwcLayer.bAccelerateLayer_ && !drmHwcLayer.bMatch_){
      enable_self_refresh = true;
      self_fps = 30;
      break;
    }
  }

  if(resource_manager_->isWBMode()){
    if(self_fps < 30)
      self_fps = 30;
  }

  if(enable_self_refresh){
    InvalidateControl(self_fps,-1);
  }
  return 0 ;
}

int DrmHwcTwo::HwcDisplay::UpdateTimerState(bool gles_comp){
    struct itimerval tv = {{0,0},{0,0}};

    if (static_screen_timer_enable_ && gles_comp) {
        int interval_value = hwc_get_int_property( "vendor.hwc.static_screen_opt_time", "2500");
        interval_value = interval_value > 5000? 5000:interval_value;
        interval_value = interval_value < 250? 250:interval_value;
        tv.it_value.tv_sec = interval_value / 1000;
        tv.it_value.tv_usec=( interval_value % 1000) * 1000;
        HWC2_ALOGD_IF_VERBOSE("reset timer! interval_value = %d",interval_value);
    } else {
        static_screen_opt_=false;
        tv.it_value.tv_usec = 0;
        ALOGD_IF(LogLevel(DBG_DEBUG),"close timer!");
    }
    setitimer(ITIMER_REAL, &tv, NULL);
    return 0;
}

int DrmHwcTwo::HwcDisplay::EntreStaticScreen(uint64_t refresh, int refresh_cnt){
    static_screen_opt_=true;
    invalidate_worker_.InvalidateControl(refresh, refresh_cnt);
    return 0;
}

int DrmHwcTwo::HwcDisplay::InvalidateControl(uint64_t refresh, int refresh_cnt){
    invalidate_worker_.InvalidateControl(refresh, refresh_cnt);
    return 0;
}

int DrmHwcTwo::HwcDisplay::DoMirrorDisplay(int32_t *retire_fence){
  if(!connector_->isCropSpilt()){
    return 0;
  }

  if(!connector_->IsSpiltPrimary()){
    return 0;
  }

  int32_t merge_rt_fence = -1;
  int32_t display_cnt = 1;
  for (auto &conn : drm_->connectors()) {
    if(!conn->isCropSpilt()){
      continue;
    }
    int display_id = conn->display();
    if(!conn->IsSpiltPrimary()){
      auto &display = resource_manager_->GetHwc2()->displays_.at(display_id);
      if (conn->state() == DRM_MODE_CONNECTED) {
        static hwc2_layer_t layer_id = 0;
        if(display.has_layer(layer_id)){
        }else{
          display.CreateLayer(&layer_id);
        }
        HwcLayer &layer = display.get_layer(layer_id);
        hwc_rect_t frame = {0,0,1920,1080};
        layer.SetLayerDisplayFrame(frame);
        hwc_frect_t crop = {0.0, 0.0, 1920.0, 1080.0};
        layer.SetLayerSourceCrop(crop);
        layer.SetLayerZOrder(0);
        layer.SetLayerBlendMode(HWC2_BLEND_MODE_NONE);
        layer.SetLayerPlaneAlpha(1.0);
        layer.SetLayerCompositionType(HWC2_COMPOSITION_DEVICE);
        // layer.SetLayerBuffer(NULL,-1);
        layer.SetLayerTransform(0);
        uint32_t num_types;
        uint32_t num_requests;
        display.ValidateDisplay(&num_types,&num_requests);
        // display.GetChangedCompositionTypes();
        // display.GetDisplayRequests();
        display.AcceptDisplayChanges();
        hwc_region_t damage;
        display.SetClientTarget(client_layer_.buffer(),
                                dup(client_layer_.acquire_fence()->getFd()),
                                0,
                                damage);
        int32_t rt_fence;
        display.PresentDisplay(&rt_fence);
        if(merge_rt_fence > 0){
            char acBuf[32];
            sprintf(acBuf,"RTD%" PRIu64 "M-FN%d-%d", handle_, frame_no_, display_cnt++);
            sp<ReleaseFence> rt = sp<ReleaseFence>(new ReleaseFence(rt_fence, acBuf));
            if(rt->isValid()){
              sprintf(acBuf,"RTD%" PRIu64 "M-FN%d-%d",handle_, frame_no_, display_cnt++);
              int32_t merge_rt_fence_temp = merge_rt_fence;
              merge_rt_fence = rt->merge(merge_rt_fence, acBuf);
              close(merge_rt_fence_temp);
            }else{
              HWC2_ALOGE("connector %u type=%s, type_id=%d is MirrorDisplay get retireFence fail.\n",
                          conn->id(),
                          drm_->connector_type_str(conn->type()),
                          conn->type_id());
            }
        }else{
          merge_rt_fence = rt_fence;
        }
      }
    }
  }
  *retire_fence = merge_rt_fence;
  return 0;
}

HWC2::Error DrmHwcTwo::HwcLayer::SetLayerBlendMode(int32_t mode) {
  HWC2_ALOGD_IF_VERBOSE("layer-id=%d"", blend=%d" ,id_,mode);
  mCurrentState.blending_ = static_cast<HWC2::BlendMode>(mode);
  return HWC2::Error::None;
}

HWC2::Error DrmHwcTwo::HwcLayer::SetLayerBuffer(buffer_handle_t buffer,
                                                int32_t acquire_fence) {
  HWC2_ALOGD_IF_VERBOSE("layer-id=%d"", buffer=%p, acq_fence=%d" ,id_,buffer,acquire_fence);
  //Deleting the following logic may cause the problem that the handle cannot be updated
  // The buffer and acquire_fence are handled elsewhere
  //  if (sf_type_ == HWC2::Composition::Client ||
  //      sf_type_ == HWC2::Composition::Sideband ||
  //      sf_type_ == HWC2::Composition::SolidColor)
  //    return HWC2::Error::None;
  if (mCurrentState.sf_type_ == HWC2::Composition::Sideband){
    return HWC2::Error::None;
  }

  // 应用端可能会不销毁 Surface ，直接将Sideband图层修改为一般图层，故需要重置Sideband相关配置
  bSideband2_ = false;
  bSideband2Valid_ = false;
  sidebandStreamHandle_ = NULL;

  // 部分video不希望使用cache逻辑，因为可能会导致oom问题
  bool need_cache = true;
  ResourceManager* rm = ResourceManager::getInstance();
  int buffer_limit_size = rm->GetCacheBufferLimitSize();
  if(buffer_limit_size > 0){
    int format = drmGralloc_->hwc_get_handle_attibute(buffer,ATT_FORMAT);
    uint32_t fourcc = drmGralloc_->hwc_get_handle_fourcc_format(buffer);
    if(drmGralloc_->is_yuv_format(format, fourcc)){
      int width = drmGralloc_->hwc_get_handle_attibute(buffer_,ATT_WIDTH);
      int height = drmGralloc_->hwc_get_handle_attibute(buffer_,ATT_HEIGHT);
      if( width * height > buffer_limit_size){
        need_cache = false;
      }
    }
  }

  if(need_cache){
    CacheBufferInfo(buffer);
  }else{
    NoCacheBufferInfo(buffer);
  }
  acquire_fence_ = sp<AcquireFence>(new AcquireFence(acquire_fence));
  return HWC2::Error::None;
}

HWC2::Error DrmHwcTwo::HwcLayer::SetLayerColor(hwc_color_t color) {
  HWC2_ALOGD_IF_VERBOSE("layer-id=%d"", color [r,g,b,a]=[%d,%d,%d,%d]" ,id_,color.r,color.g,color.b,color.a);
  // TODO: Punt to client composition here?
  mCurrentState.color_ = color;
  return HWC2::Error::None;
}

HWC2::Error DrmHwcTwo::HwcLayer::SetLayerCompositionType(int32_t type) {
  HWC2_ALOGD_IF_VERBOSE("layer-id=%d"", type=0x%x" ,id_,type);
  mCurrentState.sf_type_ = static_cast<HWC2::Composition>(type);
  return HWC2::Error::None;
}

HWC2::Error DrmHwcTwo::HwcLayer::SetLayerDataspace(int32_t dataspace) {
  HWC2_ALOGD_IF_VERBOSE("layer-id=%d"", dataspace=0x%x" ,id_,dataspace);
  mCurrentState.dataspace_ = static_cast<android_dataspace_t>(dataspace);
  return HWC2::Error::None;
}

HWC2::Error DrmHwcTwo::HwcLayer::SetLayerDisplayFrame(hwc_rect_t frame) {
  HWC2_ALOGD_IF_VERBOSE("layer-id=%d"", frame=[%d,%d,%d,%d]" ,id_,frame.left,frame.top,frame.right,frame.bottom);
  mCurrentState.display_frame_ = frame;
  return HWC2::Error::None;
}

HWC2::Error DrmHwcTwo::HwcLayer::SetLayerPlaneAlpha(float alpha) {
  HWC2_ALOGD_IF_VERBOSE("layer-id=%d"", alpha=%f" ,id_,alpha);
  mCurrentState.alpha_ = alpha;
  return HWC2::Error::None;
}

HWC2::Error DrmHwcTwo::HwcLayer::SetLayerSidebandStream(
    const native_handle_t *stream) {
  HWC2_ALOGD_IF_VERBOSE("layer-id=%d stream=%p",id_, stream);
  ResourceManager* rm = ResourceManager::getInstance();
  if(rm->IsSidebandStream2Mode()){
    if(stream != NULL){
      vt_sideband_data_t *sbi = (vt_sideband_data_t *)(stream->data);
      // 如果 Tunnel Id 有效，并且是未连接状态，则创建连接
      if(sbi->tunnel_id != mSidebandInfo_.tunnel_id){
        HWC2_ALOGD_IF_DEBUG("SidebandStream: layer-id=%d. version=%d numFds=%d numInts=%d",
                    id_,
                    stream->version,
                    stream->numFds,
                    stream->numInts);
        HWC2_ALOGD_IF_DEBUG("SidebandStream: version=%d sizeof=%zu tunnel-id=%d session-id=%" PRIu64 " crop[%d,%d,%d,%d] "
                  " w=%d h=%d ws=%d hs=%d bs=%d f=%d transform=%d size=%d modifier=%d"
                  " usage=0x%" PRIx64 " dataSpace=0x%" PRIx64 " afbc=%d fps=%" PRIu64 "",
                    stream->data[0],
                    sizeof(vt_sideband_data_t),
                    sbi->tunnel_id,
                    sbi->session_id,
                    sbi->crop.left,
                    sbi->crop.top,
                    sbi->crop.right,
                    sbi->crop.bottom,
                    sbi->width,
                    sbi->height,
                    sbi->hor_stride,
                    sbi->ver_stride,
                    sbi->byte_stride,
                    sbi->format,
                    sbi->transform,
                    sbi->size,
                    sbi->modifier,
                    sbi->usage,
                    sbi->data_space,
                    sbi->is_afbc,
                    sbi->fps);
          bSideband2Valid_=true;
          memcpy(&mSidebandInfo_, sbi, sizeof(vt_sideband_data_t));
      }
      // sbi->tunnel_id 不等于0才认为是有效的Sideband 2.0 handle
      if(sbi->tunnel_id != 0){
        sidebandStreamHandle_ = stream;
      }else{// 否则设置为NULL
        sidebandStreamHandle_ = NULL;
      }
    }
    bSideband2_ = true;
  }else{
    setSidebandStream(stream);
  }

  return HWC2::Error::None;
}

HWC2::Error DrmHwcTwo::HwcLayer::SetLayerSourceCrop(hwc_frect_t crop) {
  HWC2_ALOGD_IF_VERBOSE("layer-id=%d"", frame=[%f,%f,%f,%f]" ,id_,crop.left,crop.top,crop.right,crop.bottom);
  mCurrentState.source_crop_ = crop;
  return HWC2::Error::None;
}

HWC2::Error DrmHwcTwo::HwcLayer::SetLayerSurfaceDamage(hwc_region_t damage) {
  HWC2_ALOGD_IF_VERBOSE("layer-id=%d",id_);
  // TODO: We don't use surface damage, marking as unsupported
  unsupported(__func__, damage);
  return HWC2::Error::None;
}

HWC2::Error DrmHwcTwo::HwcLayer::SetLayerTransform(int32_t transform) {
  HWC2_ALOGD_IF_VERBOSE("layer-id=%d" ", transform=%x",id_,transform);
  mCurrentState.transform_ = static_cast<HWC2::Transform>(transform);
  return HWC2::Error::None;
}

HWC2::Error DrmHwcTwo::HwcLayer::SetLayerVisibleRegion(hwc_region_t visible) {
  HWC2_ALOGD_IF_VERBOSE("layer-id=%d",id_);
  // TODO: We don't use this information, marking as unsupported
  unsupported(__func__, visible);
  return HWC2::Error::None;
}

HWC2::Error DrmHwcTwo::HwcLayer::SetLayerZOrder(uint32_t order) {
  HWC2_ALOGD_IF_VERBOSE("layer-id=%d" ", z=%d",id_,order);
  mCurrentState.z_order_ = order;
  return HWC2::Error::None;
}


void DrmHwcTwo::HwcLayer::PopulateSidebandLayer(DrmHwcLayer *drmHwcLayer,
                                                 hwc2_drm_display_t* ctx) {
    // sideband layer
    if(bSideband2_){
      if(bSideband2Valid_){
        drmHwcLayer->iTunnelId_ = mSidebandInfo_.tunnel_id;
        drmHwcLayer->bSidebandStreamLayer_ = true;
        drmHwcLayer->sf_handle     = NULL;
        drmHwcLayer->SetDisplayFrame(mCurrentState.display_frame_, ctx);

        hwc_frect source_crop;
        source_crop.left   = mSidebandInfo_.crop.left;
        source_crop.top    = mSidebandInfo_.crop.top;
        source_crop.right  = mSidebandInfo_.crop.right;
        source_crop.bottom = mSidebandInfo_.crop.bottom;
        drmHwcLayer->SetSourceCrop(source_crop);

        drmHwcLayer->SetTransform(mCurrentState.transform_);
        // Commit mirror function
        drmHwcLayer->SetDisplayFrameMirror(mCurrentState.display_frame_);

        drmHwcLayer->iFd_     = -1;
        drmHwcLayer->iWidth_  = mSidebandInfo_.crop.right - mSidebandInfo_.crop.left;
        drmHwcLayer->iHeight_ = mSidebandInfo_.crop.bottom - mSidebandInfo_.crop.top;
        drmHwcLayer->iStride_ = mSidebandInfo_.crop.right - mSidebandInfo_.crop.left;;
        drmHwcLayer->iFormat_ = mSidebandInfo_.format;
        drmHwcLayer->iUsage   = mSidebandInfo_.usage;
        drmHwcLayer->iHeightStride_ = mSidebandInfo_.crop.bottom - mSidebandInfo_.crop.top;
        drmHwcLayer->uFourccFormat_   = drmGralloc_->hwc_get_fourcc_from_hal_format(mSidebandInfo_.format);
        drmHwcLayer->bSideband2_ = true;
        // 通过 Sideband Handle is_afbc 来判断图层是否为AFBC压缩格式
        drmHwcLayer->uModifier_ = (mSidebandInfo_.is_afbc > 0) ? AFBC_FORMAT_MOD_BLOCK_SIZE_16x16 : 0;
        drmHwcLayer->uGemHandle_ = 0;
        drmHwcLayer->sLayerName_ = std::string("SidebandStream-2.0");
        drmHwcLayer->eDataSpace_ = (android_dataspace_t)mSidebandInfo_.data_space;
      }else{
        drmHwcLayer->iFd_     = -1;
        drmHwcLayer->iWidth_  = -1;
        drmHwcLayer->iHeight_ = -1;
        drmHwcLayer->iStride_ = -1;
        drmHwcLayer->iFormat_ = -1;
        drmHwcLayer->iUsage   = 0;
        drmHwcLayer->iHeightStride_ = -1;
        drmHwcLayer->uFourccFormat_   = 0x20202020; //0x20 is space
        drmHwcLayer->uModifier_ = 0;
        drmHwcLayer->uGemHandle_ = 0;
        drmHwcLayer->sLayerName_.clear();
      }
    }else{
      drmHwcLayer->bSidebandStreamLayer_ = true;
      drmHwcLayer->sf_handle     = mCurrentState.sidebandStreamHandle_;
      drmHwcLayer->SetDisplayFrame(mCurrentState.display_frame_, ctx);

      hwc_frect source_crop;
      source_crop.top = 0;
      source_crop.left = 0;
      source_crop.right = pBufferInfo_->iWidth_;
      source_crop.bottom = pBufferInfo_->iHeight_;
      drmHwcLayer->SetSourceCrop(source_crop);

      drmHwcLayer->SetTransform(mCurrentState.transform_);
      // Commit mirror function
      drmHwcLayer->SetDisplayFrameMirror(mCurrentState.display_frame_);

      if(mCurrentState.sidebandStreamHandle_){
        drmHwcLayer->iFd_     = pBufferInfo_->iFd_.get();
        drmHwcLayer->iWidth_  = pBufferInfo_->iWidth_;
        drmHwcLayer->iHeight_ = pBufferInfo_->iHeight_;
        drmHwcLayer->iStride_ = pBufferInfo_->iStride_;
        drmHwcLayer->iFormat_ = pBufferInfo_->iFormat_;
        drmHwcLayer->iUsage   = pBufferInfo_->iUsage_;
        drmHwcLayer->iHeightStride_   = pBufferInfo_->iHeightStride_;
        drmHwcLayer->iByteStride_     = pBufferInfo_->iByteStride_;
        drmHwcLayer->uFourccFormat_   = pBufferInfo_->uFourccFormat_;
        drmHwcLayer->uModifier_       = pBufferInfo_->uModifier_;
        drmHwcLayer->sLayerName_      = pBufferInfo_->sLayerName_;
      }else{
        drmHwcLayer->iFd_     = -1;
        drmHwcLayer->iWidth_  = -1;
        drmHwcLayer->iHeight_ = -1;
        drmHwcLayer->iStride_ = -1;
        drmHwcLayer->iFormat_ = -1;
        drmHwcLayer->iUsage   = 0;
        drmHwcLayer->iHeightStride_ = -1;
        drmHwcLayer->uFourccFormat_   = 0x20202020; //0x20 is space
        drmHwcLayer->uModifier_ = 0;
        drmHwcLayer->uGemHandle_ = 0;
        drmHwcLayer->sLayerName_.clear();
      }
    }

    drmHwcLayer->Init();
    return;
}

void DrmHwcTwo::HwcLayer::PopulateNormalLayer(DrmHwcLayer *drmHwcLayer,
                                              hwc2_drm_display_t* ctx) {
    drmHwcLayer->SetDisplayFrame(mCurrentState.display_frame_, ctx);
    drmHwcLayer->SetSourceCrop(mCurrentState.source_crop_);
    drmHwcLayer->SetTransform(mCurrentState.transform_);
    // Commit mirror function
    drmHwcLayer->SetDisplayFrameMirror(mCurrentState.display_frame_);

    if(buffer_){
      drmHwcLayer->sf_handle =  pBufferInfo_->native_buffer_;
      drmHwcLayer->uBufferId_ = pBufferInfo_->uBufferId_;
      drmHwcLayer->iFd_     = pBufferInfo_->iFd_.get();
      drmHwcLayer->iWidth_  = pBufferInfo_->iWidth_;
      drmHwcLayer->iHeight_ = pBufferInfo_->iHeight_;
      drmHwcLayer->iStride_ = pBufferInfo_->iStride_;
      drmHwcLayer->iSize_   = pBufferInfo_->iSize_;
      drmHwcLayer->iFormat_ = pBufferInfo_->iFormat_;
      drmHwcLayer->iUsage   = pBufferInfo_->iUsage_;
      drmHwcLayer->iHeightStride_   = pBufferInfo_->iHeightStride_;
      drmHwcLayer->iByteStride_     = pBufferInfo_->iByteStride_;
      drmHwcLayer->uFourccFormat_   = pBufferInfo_->uFourccFormat_;
      drmHwcLayer->uModifier_       = pBufferInfo_->uModifier_;
      drmHwcLayer->sLayerName_      = pBufferInfo_->sLayerName_;
      drmHwcLayer->uByteStridePlanes_ = pBufferInfo_->uByteStridePlanes_;
    }else{
      drmHwcLayer->iFd_     = -1;
      drmHwcLayer->iWidth_  = -1;
      drmHwcLayer->iHeight_ = -1;
      drmHwcLayer->iStride_ = -1;
      drmHwcLayer->iSize_   = -1;
      drmHwcLayer->iFormat_ = -1;
      drmHwcLayer->iUsage   = 0;
      drmHwcLayer->iHeightStride_ = -1;
      drmHwcLayer->uFourccFormat_   = 0x20202020; //0x20 is space
      drmHwcLayer->uModifier_ = 0;
      drmHwcLayer->uGemHandle_ = 0;
      drmHwcLayer->sLayerName_.clear();
      drmHwcLayer->uByteStridePlanes_.clear();
    }
    drmHwcLayer->Init();
    return;
}

void DrmHwcTwo::HwcLayer::PopulateDrmLayer(hwc2_layer_t layer_id, DrmHwcLayer *drmHwcLayer,
                                                 hwc2_drm_display_t* ctx, uint32_t frame_no) {
  drmHwcLayer->uId_        = layer_id;
  drmHwcLayer->iZpos_      = mCurrentState.z_order_;
  drmHwcLayer->uFrameNo_   = frame_no;
  drmHwcLayer->bFbTarget_  = false;
  drmHwcLayer->bSkipLayer_ = false;
  drmHwcLayer->bUse_       = true;
  drmHwcLayer->eDataSpace_ = mCurrentState.dataspace_;
  drmHwcLayer->alpha       = static_cast<uint16_t>(255.0f * mCurrentState.alpha_ + 0.5f);
  drmHwcLayer->sf_composition = sf_type();
  drmHwcLayer->iBestPlaneType = 0;
  drmHwcLayer->bSidebandStreamLayer_ = false;
  drmHwcLayer->bMatch_ = false;
  drmHwcLayer->IsMetadataHdr_ = false;
  drmHwcLayer->bSideband2_ = false;
  drmHwcLayer->fRealFps_ = GetRealFps();
  drmHwcLayer->fRealMaxFps_ = GetRealMaxFps();

#ifdef RK3528
  // RK3528 仅 VOP支持AFBC格式，如果遇到以下两个问题需要启用解码预缩小功能：
  // 1. AFBC格式无法Overlay，需要启用预缩小关闭AFBC并缩小;
  // 2. 视频缩放倍率超过VOP硬件限制，需要启用预缩小减少后端缩小倍数；
  drmHwcLayer->bNeedPreScale_ = false;
  drmHwcLayer->bIsPreScale_ = false;
#endif

  drmHwcLayer->acquire_fence = acquire_fence_;

  drmHwcLayer->iFbWidth_ = ctx->framebuffer_width;
  drmHwcLayer->iFbHeight_ = ctx->framebuffer_height;

  drmHwcLayer->uAclk_ = ctx->aclk;
  drmHwcLayer->uDclk_ = ctx->dclk;
  drmHwcLayer->SetBlend(mCurrentState.blending_);
  // SidebandStream layer
  if(sidebandStreamHandle_ != NULL){
    PopulateSidebandLayer(drmHwcLayer, ctx);
  }else{
    PopulateNormalLayer(drmHwcLayer, ctx);
  }
#ifdef RK3528
 if(gIsRK3528()){
   // 调试命令
   int enable_prescale_video = hwc_get_int_property("debug.hwc.enable_prescale_video", "0");
   if(enable_prescale_video > 0 && drmHwcLayer->bYuv_){
     metadata_for_rkvdec_scaling_t* metadata = NULL;
     drmGralloc_->lock_rkvdec_scaling_metadata(buffer_, &metadata);
     HWC2_ALOGD_IF_INFO("lock_rkvdec_scaling_metadata buffer_=%p metadata=%p", buffer_, metadata);
     if(metadata != NULL){
      metadata->requestMask = enable_prescale_video;
       if(metadata->replyMask > 0){
         memcpy(&(drmHwcLayer->mMetadata_), metadata, sizeof(metadata_for_rkvdec_scaling_t));
         drmHwcLayer->bNeedPreScale_ = true;
         drmHwcLayer->bIsPreScale_ = true;

         hwc_frect source_crop;
         source_crop.top    = metadata->srcTop;
         source_crop.left   = metadata->srcLeft;
         source_crop.right  = metadata->srcRight;
         source_crop.bottom = metadata->srcBottom;
         drmHwcLayer->SetSourceCrop(source_crop);

         drmHwcLayer->iWidth_  = metadata->width;
         drmHwcLayer->iHeight_ = metadata->height;
         drmHwcLayer->iStride_ = metadata->pixel_stride;
         drmHwcLayer->iFormat_ = metadata->format;
         drmHwcLayer->iUsage   = metadata->usage;
         drmHwcLayer->iByteStride_     = metadata->byteStride[0];
         drmHwcLayer->uModifier_       = metadata->modifier;
         drmHwcLayer->uFourccFormat_ = drmGralloc_->hwc_get_fourcc_from_hal_format(metadata->format);
         drmHwcLayer->Init();
       }

       // 打印参数
       HWC2_ALOGD_IF_INFO("Name=%s metadata = %p", pBufferInfo_->sLayerName_.c_str(), metadata);
       HWC2_ALOGD_IF_INFO("version=0x%" PRIx64 " requestMask=0x%" PRIx64" "
                          "replyMask=0x%" PRIx64 " BufferId=0x%" PRIx64,
                           metadata->version,
                           metadata->requestMask,
                           metadata->replyMask,
                           drmHwcLayer->uBufferId_);
       HWC2_ALOGD_IF_INFO("w=%d h=%d s=%d f=%d m=0x%" PRIx64 " usage=0x%x ",
                                                           metadata->width,
                                                           metadata->height,
                                                           metadata->pixel_stride,
                                                           metadata->format,
                                                           metadata->modifier,
                                                           metadata->usage);
       HWC2_ALOGD_IF_INFO("crop=(%d,%d,%d,%d) ", metadata->srcLeft,
                                         metadata->srcTop,
                                         metadata->srcRight,
                                         metadata->srcBottom);
       HWC2_ALOGD_IF_INFO("layer_cnt=%d offset=%d,%d,%d,%d byteStride=%d,%d,%d,%d) ",
                                         metadata->layer_cnt,
                                         metadata->offset[0],
                                         metadata->offset[1],
                                         metadata->offset[2],
                                         metadata->offset[3],
                                         metadata->byteStride[0],
                                         metadata->byteStride[1],
                                         metadata->byteStride[2],
                                         metadata->byteStride[3]);

       drmGralloc_->unlock_rkvdec_scaling_metadata(buffer_);
     }
   }
 }
#endif
  return;
}

void DrmHwcTwo::HwcLayer::PopulateFB(hwc2_layer_t layer_id, DrmHwcLayer *drmHwcLayer,
                                         hwc2_drm_display_t* ctx, uint32_t frame_no, bool validate) {
  drmHwcLayer->uId_        = layer_id;
  drmHwcLayer->uFrameNo_   = frame_no;
  drmHwcLayer->bFbTarget_  = true;
  drmHwcLayer->bUse_       = true;
  drmHwcLayer->bSkipLayer_ = false;
  drmHwcLayer->blending    = DrmHwcBlending::kPreMult;
  drmHwcLayer->iZpos_      = mCurrentState.z_order_;
  drmHwcLayer->alpha       = static_cast<uint16_t>(255.0f * mCurrentState.alpha_ + 0.5f);
  drmHwcLayer->iBestPlaneType = 0;

  if(!validate){
    drmHwcLayer->sf_handle     = buffer_;
    drmHwcLayer->acquire_fence = acquire_fence_;
  }else{
    // Commit mirror function
    drmHwcLayer->SetDisplayFrameMirror(mCurrentState.display_frame_);
    drmHwcLayer->bMatch_ = false;
  }

  drmHwcLayer->iFbWidth_ = ctx->framebuffer_width;
  drmHwcLayer->iFbHeight_ = ctx->framebuffer_height;

  drmHwcLayer->uAclk_ = ctx->aclk;
  drmHwcLayer->uDclk_ = ctx->dclk;

  drmHwcLayer->SetDisplayFrame(mCurrentState.display_frame_, ctx);
  drmHwcLayer->SetSourceCrop(mCurrentState.source_crop_);
  drmHwcLayer->SetTransform(mCurrentState.transform_);

  if(buffer_ && !validate){
    drmHwcLayer->iFd_     = pBufferInfo_->iFd_.get();
    drmHwcLayer->iWidth_  = pBufferInfo_->iWidth_;
    drmHwcLayer->iHeight_ = pBufferInfo_->iHeight_;
    drmHwcLayer->iStride_ = pBufferInfo_->iStride_;
    drmHwcLayer->iSize_   = pBufferInfo_->iSize_;
    drmHwcLayer->iFormat_ = pBufferInfo_->iFormat_;
    drmHwcLayer->iUsage   = pBufferInfo_->iUsage_;
    drmHwcLayer->iHeightStride_   = pBufferInfo_->iHeightStride_;
    drmHwcLayer->iByteStride_     = pBufferInfo_->iByteStride_;
    drmHwcLayer->uFourccFormat_   = pBufferInfo_->uFourccFormat_;
    drmHwcLayer->uModifier_       = pBufferInfo_->uModifier_;
    drmHwcLayer->sLayerName_      = pBufferInfo_->sLayerName_;
  }else{
    drmHwcLayer->iFd_     = -1;
    drmHwcLayer->iWidth_  = -1;
    drmHwcLayer->iHeight_ = -1;
    drmHwcLayer->iStride_ = -1;
    // 由于 validate 没有实际的handle, 故此处的size通过crop信息预估,格式为 RGBA
    drmHwcLayer->iSize_   = (mCurrentState.source_crop_.right - mCurrentState.source_crop_.left) *
                            (mCurrentState.source_crop_.bottom - mCurrentState.source_crop_.top) * 4;
    drmHwcLayer->iFormat_ = -1;
    drmHwcLayer->iUsage   = 0;
    drmHwcLayer->iHeightStride_ = -1;
    drmHwcLayer->uFourccFormat_   = DRM_FORMAT_ABGR8888; // fb target default DRM_FORMAT_ABGR8888
    drmHwcLayer->uModifier_ = 0;
    drmHwcLayer->uGemHandle_      = 0;
    drmHwcLayer->sLayerName_ = std::string("FramebufferSurface");
  }

  drmHwcLayer->Init();
  return;
}


#ifdef USE_LIBPQ
int DrmHwcTwo::HwcLayer::DoPq(bool validate, DrmHwcLayer *drmHwcLayer, hwc2_drm_display_t* ctx){
  char value[PROPERTY_VALUE_MAX];
  property_get("persist.vendor.tvinput.rkpq.mode", value, "0");
  bool pq_mode_enable = atoi(value) > 0;

  if(pq_mode_enable == 1){
    static bool use_pq_fb = false;

    if(validate){
      if(bufferQueue_ == NULL){
        bufferQueue_ = std::make_shared<DrmBufferQueue>();
      }
      if(pq_ == NULL){
        pq_ = Pq::Get();
        if(pq_ != NULL){
          bPqReady_ = true;
          HWC2_ALOGI("Pq module ready. to enable PqMode.");
        }
      } else {
          bPqReady_ = true;
          HWC2_ALOGI("Pq module ready. to enable PqMode.");
      }
      if(bPqReady_){
        // 1. Init Ctx
        int ret = pq_->InitCtx(pqCtx_);
        if(ret){
          HWC2_ALOGE("Pq ctx init fail");
          return ret;
        }
        // 2. Set buffer Info
        PqImageInfo src;
        src.mBufferInfo_.iFd_     = 1;
        src.mBufferInfo_.iWidth_  = drmHwcLayer->iFbWidth_;
        src.mBufferInfo_.iHeight_ = drmHwcLayer->iFbHeight_;
        src.mBufferInfo_.iFormat_ = HAL_PIXEL_FORMAT_RGBA_8888;
        // src.mBufferInfo_.iSize_   = drmHwcLayer->iFbWidth_ * drmHwcLayer->iFbHeight_ * 4;
        src.mBufferInfo_.iStride_ = drmHwcLayer->iFbWidth_;
        src.mBufferInfo_.uBufferId_ = 0x1;

        src.mCrop_.iLeft_  = (int)drmHwcLayer->source_crop.left;
        src.mCrop_.iTop_   = (int)drmHwcLayer->source_crop.top;
        src.mCrop_.iRight_ = (int)drmHwcLayer->source_crop.right;
        src.mCrop_.iBottom_= (int)drmHwcLayer->source_crop.bottom;

        ret = pq_->SetSrcImage(pqCtx_, src);
        if(ret){
          printf("pq SetSrcImage fail\n");
          return ret;
        }
        use_pq_fb = true;
      }
    }else if(use_pq_fb){
      use_pq_fb = false;
      if(bufferQueue_ == NULL){
        bufferQueue_ = std::make_shared<DrmBufferQueue>();
      }
      if(pq_ == NULL){
        pq_ = Pq::Get();
        if(pq_ != NULL){
          bPqReady_ = true;
          HWC2_ALOGI("pq module ready. to enable pqMode.");
        }
      }
      if(bPqReady_){
        // 1. Init Ctx
        int ret = pq_->InitCtx(pqCtx_);
        if(ret){
          HWC2_ALOGE("Pq ctx init fail");
          return ret;
        }
        // 2. Set buffer Info
        PqImageInfo src;
        src.mBufferInfo_.iFd_     = drmHwcLayer->iFd_;
        src.mBufferInfo_.iWidth_  = drmHwcLayer->iWidth_;
        src.mBufferInfo_.iHeight_ = drmHwcLayer->iHeight_;
        src.mBufferInfo_.iFormat_ = drmHwcLayer->iFormat_;
        src.mBufferInfo_.iStride_ = drmHwcLayer->iStride_;
        // src.mBufferInfo_.iSize_   = drmHwcLayer->iSize_;
        src.mBufferInfo_.uBufferId_ = drmHwcLayer->uBufferId_;
        src.mBufferInfo_.uDataSpace_ = (uint64_t)drmHwcLayer->eDataSpace_;

        src.mCrop_.iLeft_  = (int)drmHwcLayer->source_crop.left;
        src.mCrop_.iTop_   = (int)drmHwcLayer->source_crop.top;
        src.mCrop_.iRight_ = (int)drmHwcLayer->source_crop.right;
        src.mCrop_.iBottom_= (int)drmHwcLayer->source_crop.bottom;

        ret = pq_->SetSrcImage(pqCtx_, src);
        if(ret){
          printf("Pq SetSrcImage fail\n");
          return ret;
        }

        // 4. Alloc Dst buffer
        std::shared_ptr<DrmBuffer> dst_buffer;
        dst_buffer = bufferQueue_->DequeueDrmBuffer(ctx->framebuffer_width,
                                                    ctx->framebuffer_height,
                                                    HAL_PIXEL_FORMAT_YCBCR_444_888,
                                                    // PQ 算法要求 256 对齐，Gralloc可用的只有256奇数倍对齐
                                                    // 暂时按照 256 奇数倍对齐，后续查看情况
                                                    // TODO: 最终PQ库内部修改为64对齐即可
                                                    RK_GRALLOC_USAGE_STRIDE_ALIGN_64 |
                                                    MALI_GRALLOC_USAGE_NO_AFBC,
                                                    "PQ-FB-target");

        if(dst_buffer == NULL){
          HWC2_ALOGD_IF_DEBUG("DequeueDrmBuffer fail!, skip this policy.");
          return -1;
        }

        // 5. Set buffer Info
        PqImageInfo dst;
        dst.mBufferInfo_.iFd_     = dst_buffer->GetFd();
        dst.mBufferInfo_.iWidth_  = dst_buffer->GetWidth();
        dst.mBufferInfo_.iHeight_ = dst_buffer->GetHeight();
        dst.mBufferInfo_.iFormat_ = dst_buffer->GetFormat();
        dst.mBufferInfo_.iStride_ = dst_buffer->GetStride();
        dst.mBufferInfo_.uBufferId_ = dst_buffer->GetBufferId();

        dst.mCrop_.iLeft_  = (int)drmHwcLayer->source_crop.left;
        dst.mCrop_.iTop_   = (int)drmHwcLayer->source_crop.top;
        dst.mCrop_.iRight_ = (int)drmHwcLayer->source_crop.right;
        dst.mCrop_.iBottom_= (int)drmHwcLayer->source_crop.bottom;

        dst.mCrop_.iLeft_  = 0;
        dst.mCrop_.iTop_   = 0;
        dst.mCrop_.iRight_ = ctx->framebuffer_width;
        dst.mCrop_.iBottom_= ctx->framebuffer_height;

        ret = pq_->SetDstImage(pqCtx_, dst);
        if(ret){
          printf("Pq SetSrcImage fail\n");
          bufferQueue_->QueueBuffer(dst_buffer);
          return ret;
        }

        hwc_frect_t source_crop;
        source_crop.left   = 0;
        source_crop.top    = 0;
        source_crop.right  = ctx->framebuffer_width;
        source_crop.bottom = ctx->framebuffer_height;
        drmHwcLayer->UpdateAndStoreInfoFromDrmBuffer(dst_buffer->GetHandle(),
                                                  dst_buffer->GetFd(),
                                                  dst_buffer->GetFormat(),
                                                  dst_buffer->GetWidth(),
                                                  dst_buffer->GetHeight(),
                                                  dst_buffer->GetStride(),
                                                  dst_buffer->GetHeightStride(),
                                                  dst_buffer->GetByteStride(),
                                                  dst_buffer->GetSize(),
                                                  dst_buffer->GetUsage(),
                                                  dst_buffer->GetFourccFormat(),
                                                  dst_buffer->GetModifier(),
                                                  dst_buffer->GetByteStridePlanes(),
                                                  dst_buffer->GetName(),
                                                  source_crop,
                                                  dst_buffer->GetBufferId(),
                                                  dst_buffer->GetGemHandle(),
                                                  drmHwcLayer->transform);
        if(drmHwcLayer->acquire_fence->isValid()){
          ret = drmHwcLayer->acquire_fence->wait(1500);
          if(ret){
            HWC2_ALOGE("wait Fb-Target 1500ms timeout, ret=%d",ret);
            drmHwcLayer->bUsePq_ = false;
            bufferQueue_->QueueBuffer(dst_buffer);
            return ret;
          }
        }
        int output_fence = 0;
        ret = pq_->RunAsync(pqCtx_, &output_fence);
        if(ret){
          HWC2_ALOGD_IF_DEBUG("RunAsync fail!");
          drmHwcLayer->bUsePq_ = false;
          bufferQueue_->QueueBuffer(dst_buffer);
          return ret;
        }
        dst_buffer->SetFinishFence(dup(output_fence));
        drmHwcLayer->acquire_fence = sp<AcquireFence>(new AcquireFence(output_fence));

        property_get("vendor.dump", value, "false");
        if(!strcmp(value, "true")){
          drmHwcLayer->acquire_fence->wait();
          dst_buffer->DumpData();
        }
        bufferQueue_->QueueBuffer(dst_buffer);
      }
    }
    drmHwcLayer->uFourccFormat_ = DRM_FORMAT_NV24;
  } else {
      if(bPqReady_) {
          pq_->DeInit();
          bPqReady_ = false;
      }
  }
  drmHwcLayer->Init();
  if(gIsDrmVerison6_1()){
    drmHwcLayer->uColorSpace.colorspace_kernel_6_1_.color_encoding_ = DRM_COLOR_YCBCR_BT601;
    drmHwcLayer->uColorSpace.colorspace_kernel_6_1_.color_range_ = DRM_COLOR_YCBCR_FULL_RANGE;
  }else{
    drmHwcLayer->uColorSpace.colorspace_kernel_510_ = V4L2_COLORSPACE_JPEG;
  }
  return 0;
}
#endif
void DrmHwcTwo::HwcLayer::DumpLayerInfo(String8 &output) {

  output.appendFormat( " %04" PRIu32 " | %03" PRIu32 " | %9s | %9s | %-18.18" PRIxPTR " |"
                       " %-11.11s | %-10.10s |%7.1f,%7.1f,%7.1f,%7.1f |%5d,%5d,%5d,%5d |"
                       " %10x | %5.1f  | %s | 0x%" PRIx64 "\n",
                    id_,
                    mCurrentState.z_order_,
                    to_string(mCurrentState.sf_type_).c_str(),
                    to_string(mCurrentState.validated_type_).c_str(),
                    intptr_t(buffer_),
                    to_string(mCurrentState.transform_).c_str(),
                    to_string(mCurrentState.blending_).c_str(),
                    mCurrentState.source_crop_.left,
                    mCurrentState.source_crop_.top,
                    mCurrentState.source_crop_.right,
                    mCurrentState.source_crop_.bottom,
                    mCurrentState.display_frame_.left,
                    mCurrentState.display_frame_.top,
                    mCurrentState.display_frame_.right,
                    mCurrentState.display_frame_.bottom,
                    mCurrentState.dataspace_,
                    GetFps(),
                    layer_name_.c_str(),
                    pBufferInfo_ != NULL ? pBufferInfo_->uBufferId_ : -1);
  return;
}
int DrmHwcTwo::HwcLayer::DumpData() {
  if(!buffer_)
    ALOGI_IF(LogLevel(DBG_INFO),"%s,line=%d LayerId=%u Buffer is null.",__FUNCTION__,__LINE__,id_);

  void* cpu_addr = NULL;
  static int frame_cnt =0;
  int width, height, stride, byte_stride, size;
  int ret = 0;
  width  = drmGralloc_->hwc_get_handle_attibute(buffer_,ATT_WIDTH);
  height = drmGralloc_->hwc_get_handle_attibute(buffer_,ATT_HEIGHT);
  stride = drmGralloc_->hwc_get_handle_attibute(buffer_,ATT_STRIDE);
  size   = drmGralloc_->hwc_get_handle_attibute(buffer_,ATT_SIZE);
  byte_stride = drmGralloc_->hwc_get_handle_attibute(buffer_,ATT_BYTE_STRIDE);

  cpu_addr = drmGralloc_->hwc_get_handle_lock(buffer_,width,height);
  if(cpu_addr == NULL) {
    ALOGE("%s, line = %d, LayerId = %u, lock fail", __FUNCTION__, __LINE__, id_);
    return -1;
  }

  FILE * pfile = NULL;
  char data_name[100] ;
  system("mkdir /data/dump/ && chmod /data/dump/ 777 ");
  sprintf(data_name,"/data/dump/%d_%5.5s_id-%d_%dx%d_z-%d.bin",
          frame_cnt++,layer_name_.size() < 5 ? "unset" : layer_name_.c_str(),
          id_,stride,height,mCurrentState.z_order_);

  pfile = fopen(data_name,"wb");
  if(pfile)
  {
      fwrite((const void *)cpu_addr,(size_t)(size),1,pfile);
      fflush(pfile);
      fclose(pfile);
      ALOGD(" dump surface layer_id=%d ,data_name %s,w:%d,h:%d,stride :%d,size=%d,cpu_addr=%p",
          id_,data_name,width,height,byte_stride,size,cpu_addr);
  }
  else
  {
      ALOGE("Open %s fail", data_name);
      ALOGD(" dump surface layer_id=%d ,data_name %s,w:%d,h:%d,stride :%d,size=%d,cpu_addr=%p",
          id_,data_name,width,height,byte_stride,size,cpu_addr);
  }

  ret = drmGralloc_->hwc_get_handle_unlock(buffer_);
  if(ret){
    ALOGE("%s,line=%d, LayerId=%u, unlock fail ret = %d ",__FUNCTION__,__LINE__,id_,ret);
    return ret;
  }

  return ret;

}

bool DrmHwcTwo::IsHasRegisterDisplayId(hwc2_display_t displayid){
  return mHasRegisterDisplay_.count(displayid) > 0;
}

void DrmHwcTwo::HandleDisplayHotplug(hwc2_display_t displayid, int state) {
  auto cb = callbacks_.find(HWC2::Callback::Hotplug);
  if (cb == callbacks_.end())
    return;

  if(isRK3566(resource_manager_->getSocId())){
      if(displayid != HWC_DISPLAY_PRIMARY){
        auto &drmDevices = resource_manager_->GetDrmDevices();
        for (auto &device : drmDevices) {
          if(state==DRM_MODE_CONNECTED)
            device->SetCommitMirrorDisplayId(displayid);
          else
            device->SetCommitMirrorDisplayId(-1);
        }
        ALOGD_IF(LogLevel(DBG_DEBUG),"HandleDisplayHotplug skip display-id=%" PRIu64 " state=%d",displayid,state);
        return;
      }
  }

  if(displayid == HWC_DISPLAY_PRIMARY && state == HWC2_CONNECTION_DISCONNECTED)
    return;

  auto hotplug = reinterpret_cast<HWC2_PFN_HOTPLUG>(cb->second.func);
  hotplug(cb->second.data, displayid,
          (state == DRM_MODE_CONNECTED ? HWC2_CONNECTION_CONNECTED
                                       : HWC2_CONNECTION_DISCONNECTED));
  // 通过 mHasRegisterDisplay_ 记录已经向 SurfaceFlinger 注册的 display-id
  if(state == DRM_MODE_CONNECTED)
    mHasRegisterDisplay_.insert(displayid);
  else{
    mHasRegisterDisplay_.erase(displayid);
  }
}

void DrmHwcTwo::HandleInitialHotplugState(DrmDevice *drmDevice) {
    // RK3528 HDMI/TV互斥模式要求，若HDMI已连接，则 TV不注册
    if(gIsRK3528()){
      drmDevice->FlipHotplugEventForInit();
      return;
    }

    for (auto &conn : drmDevice->connectors()) {
      if (conn->state() != DRM_MODE_CONNECTED)
        continue;
      for (auto &crtc : drmDevice->crtc()) {
        if(conn->display() != crtc->display())
          continue;
        // HWC_DISPLAY_PRIMARY display have been hotplug
        if(conn->display() == HWC_DISPLAY_PRIMARY){
          // SpiltDisplay Hotplug
          if(conn->isHorizontalSpilt()){
            HandleDisplayHotplug((conn->GetSpiltModeId()), conn->state());
            ALOGI("HWC2 Init: SF register connector %u type=%s, type_id=%d SpiltDisplay=%d\n",
              conn->id(),drmDevice->connector_type_str(conn->type()),conn->type_id(),conn->GetSpiltModeId());
          }
          continue;
        }
        // SpiltDisplay Hotplug
        if(conn->isCropSpilt()){
          if(conn->IsSpiltPrimary()){
            HandleDisplayHotplug(conn->display(), conn->state());
            ALOGI("HWC2 Init: SF register connector %u type=%s, type_id=%d display-id=%d\n",
              conn->id(),drmDevice->connector_type_str(conn->type()),conn->type_id(),conn->display());
              continue;
          }else{
            // CropSpilt
            HWC2_ALOGI("HWC2 Init: not to register connector %u type=%s, type_id=%d isCropSpilt=%d\n",
                      conn->id(),drmDevice->connector_type_str(conn->type()),
                      conn->type_id(),
                      conn->isCropSpilt());
            continue;
          }
        }

        ALOGI("HWC2 Init: SF register connector %u type=%s, type_id=%d \n",
          conn->id(),drmDevice->connector_type_str(conn->type()),conn->type_id());
        HandleDisplayHotplug(conn->display(), conn->state());
        // SpiltDisplay Hotplug
        if(conn->isHorizontalSpilt()){
          HandleDisplayHotplug((conn->GetSpiltModeId()), conn->state());
          ALOGI("HWC2 Init: SF register connector %u type=%s, type_id=%d SpiltDisplay=%d\n",
            conn->id(),drmDevice->connector_type_str(conn->type()),conn->type_id(),conn->GetSpiltModeId());
        }
      }
    }
}

void DrmHwcTwo::DrmHotplugHandler::HdmiTvOnlyOne(PLUG_EVENT_TYPE hdmi_hotplug_state){
  if(!gIsRK3528())
    return;

  // RK3528 HDMI拔出，则需要注册 TV 到 SurfaceFlinger
  if(hdmi_hotplug_state == DRM_HOTPLUG_UNPLUG_EVENT){
    for (auto &conn : drm_->connectors()) {
      if(conn->type() == DRM_MODE_CONNECTOR_TV){
        drmModeConnection cur_state = conn->state();
        if(cur_state == DRM_MODE_CONNECTED){
          int display_id = conn->display();
          auto &display = hwc2_->displays_.at(display_id);
          int ret = (int32_t)display.HoplugEventTmeline();
          ret |= (int32_t)display.UpdateDisplayMode();
          ret |= (int32_t)display.CheckStateAndReinit(!hwc2_->IsHasRegisterDisplayId(display_id));
          ret |= (int32_t)display.ChosePreferredConfig();
          if(ret != 0){
            HWC2_ALOGE("hwc_hotplug: %s connector %u type=%s type_id=%d state is error, skip hotplug.",
                      cur_state == DRM_MODE_CONNECTED ? "Plug" : "Unplug",
                      conn->id(),drm_->connector_type_str(conn->type()), conn->type_id());
          }else{
            HWC2_ALOGI("hwc_hotplug: %s connector %u type=%s type_id=%d send hotplug event to SF.",
                      cur_state == DRM_MODE_CONNECTED ? "Plug" : "Unplug",
                      conn->id(),drm_->connector_type_str(conn->type()),conn->type_id());
            hwc2_->HandleDisplayHotplug(display_id, cur_state);
            display.SyncPowerMode();
          }
        }
      }
    }
  // RK3528 HDMI接入，则需要销毁 TV 到 SurfaceFlinger
  }else{
    // 检查HDMI连接状态
    bool hdmi_conneted = false;
    for (auto &conn : drm_->connectors()) {
      if(conn->type() == DRM_MODE_CONNECTOR_HDMIA){
        hdmi_conneted = (conn->state() == DRM_MODE_CONNECTED);
      }
    }
    // 若HDMI已连接，则需要销毁 TV display
    if(hdmi_conneted){
      for (auto &conn : drm_->connectors()) {
        if(conn->type() == DRM_MODE_CONNECTOR_TV){
          int display_id = conn->display();
          auto &display = hwc2_->displays_.at(display_id);
          display.SetPowerMode(HWC2_POWER_MODE_OFF);
          HWC2_ALOGI("hwc_hotplug: Unplug connector %u type=%s type_id=%d send unhotplug event to SF.",
                    conn->id(),drm_->connector_type_str(conn->type()),conn->type_id());
          hwc2_->HandleDisplayHotplug(display_id, DRM_MODE_DISCONNECTED);
        }
      }
    }
  }
  return ;
}


void DrmHwcTwo::DrmHotplugHandler::HandleEvent(uint64_t timestamp_us) {
  int32_t ret = 0;
  bool primary_change = true;
  PLUG_EVENT_TYPE event_type = DRM_HOTPLUG_NONE;
  for (auto &conn : drm_->connectors()) {
    ret = 0;
    // RK3528 TV 不需要处理TV的热插拔事件
    if(gIsRK3528() && conn->type() == DRM_MODE_CONNECTOR_TV){
      ALOGI("hwc_hotplug: RK3528 not handle type=%s-%d hotplug event.\n",
            drm_->connector_type_str(conn->type()), conn->type_id());
      continue;
    }

    drmModeConnection old_state = conn->hotplug_state();
    conn->ResetModesReady();
    conn->UpdateModes();
    conn->update_hotplug_state();
    drmModeConnection cur_state = conn->hotplug_state();
    if(!conn->ModesReady())
      continue;
    if (cur_state == old_state)
      continue;

    // 当前状态为未连接，则为拔出事件
    if(cur_state == DRM_MODE_DISCONNECTED){
      event_type = DRM_HOTPLUG_UNPLUG_EVENT;
    }else{
      event_type = DRM_HOTPLUG_PLUG_EVENT;
    }

    ALOGI("hwc_hotplug: %s event @%" PRIu64 " for connector %u type=%s, type_id=%d\n",
          cur_state == DRM_MODE_CONNECTED ? "Plug" : "Unplug", timestamp_us, conn->id(),
          drm_->connector_type_str(conn->type()),conn->type_id());

    // RK3528 HDMI/TV 互斥功能需要提前处理 TV display
    if(gIsRK3528() && conn->type() == DRM_MODE_CONNECTOR_HDMIA)
      HdmiTvOnlyOne(event_type);

    int display_id = conn->display();
    primary_change = (display_id == 0);
    auto &display = hwc2_->displays_.at(display_id);
    if (cur_state == DRM_MODE_CONNECTED) {
      ret |= (int32_t)display.HoplugEventTmeline();
      ret |= (int32_t)display.UpdateDisplayMode();
      ret |= (int32_t)display.CheckStateAndReinit(!hwc2_->IsHasRegisterDisplayId(display_id));
      ret |= (int32_t)display.ChosePreferredConfig();
      if(ret != 0){
        HWC2_ALOGE("hwc_hotplug: %s connector %u type=%s type_id=%d state is error, skip hotplug.",
                   cur_state == DRM_MODE_CONNECTED ? "Plug" : "Unplug",
                   conn->id(),drm_->connector_type_str(conn->type()),conn->type_id());
      }else if(conn->isCropSpilt()){
          HWC2_ALOGI("hwc_hotplug: %s connector %u type=%s type_id=%d isCropSpilt skip hotplug.",
                    cur_state == DRM_MODE_CONNECTED ? "Plug" : "Unplug",
                    conn->id(),drm_->connector_type_str(conn->type()),conn->type_id());
          display.SetPowerMode(HWC2_POWER_MODE_ON);
      }else{
        HWC2_ALOGI("hwc_hotplug: %s connector %u type=%s type_id=%d send hotplug event to SF.",
                   cur_state == DRM_MODE_CONNECTED ? "Plug" : "Unplug",
                   conn->id(),drm_->connector_type_str(conn->type()),conn->type_id());
        hwc2_->HandleDisplayHotplug(display_id, cur_state);
        display.SyncPowerMode();
      }
    }else{
      ret |= (int32_t)display.ClearDisplay();
      ret |= (int32_t)drm_->ReleaseDpyRes(display_id);
      if(ret != 0){
        HWC2_ALOGE("hwc_hotplug: %s connector %u type=%s type_id=%d state is error, skip hotplug.",
                   cur_state == DRM_MODE_CONNECTED ? "Plug" : "Unplug",
                   conn->id(),drm_->connector_type_str(conn->type()),conn->type_id());
      }else if(conn->isCropSpilt()){
          HWC2_ALOGI("hwc_hotplug: %s connector %u type=%s type_id=%d isCropSpilt skip hotplug.",
                    cur_state == DRM_MODE_CONNECTED ? "Plug" : "Unplug",
                    conn->id(),drm_->connector_type_str(conn->type()),conn->type_id());
        // display.SetPowerMode(HWC2_POWER_MODE_OFF);
      }else{
        HWC2_ALOGI("hwc_hotplug: %s connector %u type=%s type_id=%d send hotplug event to SF.",
                   cur_state == DRM_MODE_CONNECTED ? "Plug" : "Unplug",
                   conn->id(),drm_->connector_type_str(conn->type()),conn->type_id());
        hwc2_->HandleDisplayHotplug(display_id, cur_state);
      }
    }

    // SpiltDisplay Hoplug.
    ret = 0;
    if(conn->isHorizontalSpilt()){
      display_id = conn->GetSpiltModeId();
      auto &spilt_display = hwc2_->displays_.at(display_id);
      if (cur_state == DRM_MODE_CONNECTED) {
        ret |= (int32_t)spilt_display.HoplugEventTmeline();
        ret |= (int32_t)spilt_display.UpdateDisplayMode();
        ret |= (int32_t)spilt_display.CheckStateAndReinit(!hwc2_->IsHasRegisterDisplayId(display_id));
        ret |= (int32_t)spilt_display.ChosePreferredConfig();
        if(ret != 0){
          HWC2_ALOGE("hwc_hotplug: %s connector %u type=%s type_id=%d state is error, skip hotplug.",
                    cur_state == DRM_MODE_CONNECTED ? "Plug" : "Unplug",
                    conn->id(),drm_->connector_type_str(conn->type()),conn->type_id());
        }else{
          HWC2_ALOGI("hwc_hotplug: %s connector %u type=%s type_id=%d send hotplug event to SF.",
                    cur_state == DRM_MODE_CONNECTED ? "Plug" : "Unplug",
                    conn->id(),drm_->connector_type_str(conn->type()),conn->type_id());
          hwc2_->HandleDisplayHotplug(display_id, cur_state);
          spilt_display.SyncPowerMode();
        }
      }else{
      ret |= (int32_t)spilt_display.ClearDisplay();
      ret |= (int32_t)drm_->ReleaseDpyRes(display_id);
      if(ret != 0){
        HWC2_ALOGE("hwc_hotplug: %s connector %u type=%s type_id=%d state is error, skip hotplug.",
                  cur_state == DRM_MODE_CONNECTED ? "Plug" : "Unplug",
                  conn->id(),drm_->connector_type_str(conn->type()),conn->type_id());
      }else{
        HWC2_ALOGI("hwc_hotplug: %s connector %u type=%s type_id=%d send hotplug event to SF.",
                  cur_state == DRM_MODE_CONNECTED ? "Plug" : "Unplug",
                  conn->id(),drm_->connector_type_str(conn->type()),conn->type_id());
        hwc2_->HandleDisplayHotplug(display_id, cur_state);
      }
    }
    }
  }


  if(primary_change){
    for (auto &conn : drm_->connectors()) {
      // RK3528 不需要此功能
      if(gIsRK3528()){
        continue;
      }
      // 多屏拼接不需要重新注册屏幕
      if(conn->isCropSpilt()){
        continue;
      }
      int display_id = conn->display();
      drmModeConnection state = conn->state();
      if (display_id != 0 && state == DRM_MODE_CONNECTED) {
        HWC2_ALOGI("hwc_hotplug: primary_change Plug connector %u type=%s type_id=%d send hotplug event to SF.",
                  conn->id(),drm_->connector_type_str(conn->type()),conn->type_id());
        hwc2_->HandleDisplayHotplug(display_id, state);
      }
    }
  }

  // 拔出事件，说明存在crtc资源释放
  if(event_type == DRM_HOTPLUG_UNPLUG_EVENT){
    for (auto &conn : drm_->connectors()) {
      // 多屏拼接不需要重新注册屏幕
      if(conn->isCropSpilt()){
        continue;
      }
      ret = 0;
      drmModeConnection cur_state = conn->state();
      HwcConnnectorStete cur_hwc_state = conn->hwc_state();
      if(cur_state == DRM_MODE_CONNECTED){
        if(conn->hwc_state_change_and_plug()){
          int display_id = conn->display();
          auto &display = hwc2_->displays_.at(display_id);
          ret |= (int32_t)display.HoplugEventTmeline();
          ret |= (int32_t)display.UpdateDisplayMode();
          ret |= (int32_t)display.CheckStateAndReinit(!hwc2_->IsHasRegisterDisplayId(display_id));
          ret |= (int32_t)display.ChosePreferredConfig();
          if(ret != 0){
            HWC2_ALOGE("hwc_hotplug: %s connector %u type=%s type_id=%d state is error, skip hotplug.",
                      cur_state == DRM_MODE_CONNECTED ? "Plug" : "Unplug",
                      conn->id(),drm_->connector_type_str(conn->type()), conn->type_id());
          }else{
            HWC2_ALOGI("hwc_hotplug: %s connector %u type=%s type_id=%d send hotplug event to SF.",
                      cur_state == DRM_MODE_CONNECTED ? "Plug" : "Unplug",
                      conn->id(),drm_->connector_type_str(conn->type()),conn->type_id());
            hwc2_->HandleDisplayHotplug(display_id, cur_state);
            display.SyncPowerMode();
          }
        }
      }
    }
  }

  auto &display = hwc2_->displays_.at(0);
  display.InvalidateControl(5,20);

  return;
}

void DrmHwcTwo::DrmHotplugHandler::HandleResolutionSwitchEvent(int display_id) {
  // 若系统没有设置为动态更新模式的话，则不进行分辨率更新
  ResourceManager* rm = ResourceManager::getInstance();
  if(!rm->IsDynamicDisplayMode()){
    return;
  }

  DrmConnector *connector = drm_->GetConnectorForDisplay(display_id);
  if (!connector) {
    ALOGE("Failed to get connector for display %d", display_id);
    return;
  }

  auto &display = hwc2_->displays_.at(display_id);
  HWC2::Error error = display.ChosePreferredConfig();
  if(error != HWC2::Error::None){
    HWC2_ALOGE("hwc_resolution_switch: connector %u type=%s, type_id=%d ChosePreferredConfig fail.\n",
                  connector->id(),
                  drm_->connector_type_str(connector->type()),
                  connector->type_id());
    return;
  }

  if(display.IsActiveModeChange()){
    HWC2_ALOGI("hwc_resolution_switch: connector %u type=%s, type_id=%d\n",
                  connector->id(),
                  drm_->connector_type_str(connector->type()),
                  connector->type_id());
    hwc2_->HandleDisplayHotplug(display_id, DRM_MODE_CONNECTED);
    auto &primary = hwc2_->displays_.at(0);
    primary.InvalidateControl(5,20);
    display.ActiveModeChange(false);
  }

  return;
}


// static
int DrmHwcTwo::HookDevClose(hw_device_t * /*dev*/) {
  unsupported(__func__);
  return 0;
}

// static
void DrmHwcTwo::HookDevGetCapabilities(hwc2_device_t * /*dev*/,
                                       uint32_t *out_count,
                                       int32_t * out_capabilities) {

  if(out_capabilities == NULL){
    *out_count = 1;
    return;
  }

  out_capabilities[0] = static_cast<int32_t>(HWC2::Capability::SidebandStream);
}

// static
hwc2_function_pointer_t DrmHwcTwo::HookDevGetFunction(
    struct hwc2_device * /*dev*/, int32_t descriptor) {
  supported(__func__);
  auto func = static_cast<HWC2::FunctionDescriptor>(descriptor);
  switch (func) {
    // Device functions
    case HWC2::FunctionDescriptor::CreateVirtualDisplay:
      return ToHook<HWC2_PFN_CREATE_VIRTUAL_DISPLAY>(
          DeviceHook<int32_t, decltype(&DrmHwcTwo::CreateVirtualDisplay),
                     &DrmHwcTwo::CreateVirtualDisplay, uint32_t, uint32_t,
                     int32_t *, hwc2_display_t *>);
    case HWC2::FunctionDescriptor::DestroyVirtualDisplay:
      return ToHook<HWC2_PFN_DESTROY_VIRTUAL_DISPLAY>(
          DeviceHook<int32_t, decltype(&DrmHwcTwo::DestroyVirtualDisplay),
                     &DrmHwcTwo::DestroyVirtualDisplay, hwc2_display_t>);
    case HWC2::FunctionDescriptor::Dump:
      return ToHook<HWC2_PFN_DUMP>(
          DeviceHook<void, decltype(&DrmHwcTwo::Dump), &DrmHwcTwo::Dump,
                     uint32_t *, char *>);
    case HWC2::FunctionDescriptor::GetMaxVirtualDisplayCount:
      return ToHook<HWC2_PFN_GET_MAX_VIRTUAL_DISPLAY_COUNT>(
          DeviceHook<uint32_t, decltype(&DrmHwcTwo::GetMaxVirtualDisplayCount),
                     &DrmHwcTwo::GetMaxVirtualDisplayCount>);
    case HWC2::FunctionDescriptor::RegisterCallback:
      return ToHook<HWC2_PFN_REGISTER_CALLBACK>(
          DeviceHook<int32_t, decltype(&DrmHwcTwo::RegisterCallback),
                     &DrmHwcTwo::RegisterCallback, int32_t,
                     hwc2_callback_data_t, hwc2_function_pointer_t>);

    // Display functions
    case HWC2::FunctionDescriptor::AcceptDisplayChanges:
      return ToHook<HWC2_PFN_ACCEPT_DISPLAY_CHANGES>(
          DisplayHook<decltype(&HwcDisplay::AcceptDisplayChanges),
                      &HwcDisplay::AcceptDisplayChanges>);
    case HWC2::FunctionDescriptor::CreateLayer:
      return ToHook<HWC2_PFN_CREATE_LAYER>(
          DisplayHook<decltype(&HwcDisplay::CreateLayer),
                      &HwcDisplay::CreateLayer, hwc2_layer_t *>);
    case HWC2::FunctionDescriptor::DestroyLayer:
      return ToHook<HWC2_PFN_DESTROY_LAYER>(
          DisplayHook<decltype(&HwcDisplay::DestroyLayer),
                      &HwcDisplay::DestroyLayer, hwc2_layer_t>);
    case HWC2::FunctionDescriptor::GetActiveConfig:
      return ToHook<HWC2_PFN_GET_ACTIVE_CONFIG>(
          DisplayHook<decltype(&HwcDisplay::GetActiveConfig),
                      &HwcDisplay::GetActiveConfig, hwc2_config_t *>);
    case HWC2::FunctionDescriptor::GetChangedCompositionTypes:
      return ToHook<HWC2_PFN_GET_CHANGED_COMPOSITION_TYPES>(
          DisplayHook<decltype(&HwcDisplay::GetChangedCompositionTypes),
                      &HwcDisplay::GetChangedCompositionTypes, uint32_t *,
                      hwc2_layer_t *, int32_t *>);
    case HWC2::FunctionDescriptor::GetClientTargetSupport:
      return ToHook<HWC2_PFN_GET_CLIENT_TARGET_SUPPORT>(
          DisplayHook<decltype(&HwcDisplay::GetClientTargetSupport),
                      &HwcDisplay::GetClientTargetSupport, uint32_t, uint32_t,
                      int32_t, int32_t>);
    case HWC2::FunctionDescriptor::GetColorModes:
      return ToHook<HWC2_PFN_GET_COLOR_MODES>(
          DisplayHook<decltype(&HwcDisplay::GetColorModes),
                      &HwcDisplay::GetColorModes, uint32_t *, int32_t *>);
    case HWC2::FunctionDescriptor::GetDisplayAttribute:
      return ToHook<HWC2_PFN_GET_DISPLAY_ATTRIBUTE>(
          DisplayHook<decltype(&HwcDisplay::GetDisplayAttribute),
                      &HwcDisplay::GetDisplayAttribute, hwc2_config_t, int32_t,
                      int32_t *>);
    case HWC2::FunctionDescriptor::GetDisplayConfigs:
      return ToHook<HWC2_PFN_GET_DISPLAY_CONFIGS>(
          DisplayHook<decltype(&HwcDisplay::GetDisplayConfigs),
                      &HwcDisplay::GetDisplayConfigs, uint32_t *,
                      hwc2_config_t *>);
    case HWC2::FunctionDescriptor::GetDisplayName:
      return ToHook<HWC2_PFN_GET_DISPLAY_NAME>(
          DisplayHook<decltype(&HwcDisplay::GetDisplayName),
                      &HwcDisplay::GetDisplayName, uint32_t *, char *>);
    case HWC2::FunctionDescriptor::GetDisplayRequests:
      return ToHook<HWC2_PFN_GET_DISPLAY_REQUESTS>(
          DisplayHook<decltype(&HwcDisplay::GetDisplayRequests),
                      &HwcDisplay::GetDisplayRequests, int32_t *, uint32_t *,
                      hwc2_layer_t *, int32_t *>);
    case HWC2::FunctionDescriptor::GetDisplayType:
      return ToHook<HWC2_PFN_GET_DISPLAY_TYPE>(
          DisplayHook<decltype(&HwcDisplay::GetDisplayType),
                      &HwcDisplay::GetDisplayType, int32_t *>);
    case HWC2::FunctionDescriptor::GetDozeSupport:
      return ToHook<HWC2_PFN_GET_DOZE_SUPPORT>(
          DisplayHook<decltype(&HwcDisplay::GetDozeSupport),
                      &HwcDisplay::GetDozeSupport, int32_t *>);
    case HWC2::FunctionDescriptor::GetHdrCapabilities:
      return ToHook<HWC2_PFN_GET_HDR_CAPABILITIES>(
          DisplayHook<decltype(&HwcDisplay::GetHdrCapabilities),
                      &HwcDisplay::GetHdrCapabilities, uint32_t *, int32_t *,
                      float *, float *, float *>);
    case HWC2::FunctionDescriptor::GetReleaseFences:
      return ToHook<HWC2_PFN_GET_RELEASE_FENCES>(
          DisplayHook<decltype(&HwcDisplay::GetReleaseFences),
                      &HwcDisplay::GetReleaseFences, uint32_t *, hwc2_layer_t *,
                      int32_t *>);
    case HWC2::FunctionDescriptor::PresentDisplay:
      return ToHook<HWC2_PFN_PRESENT_DISPLAY>(
          DisplayHook<decltype(&HwcDisplay::PresentDisplay),
                      &HwcDisplay::PresentDisplay, int32_t *>);
    case HWC2::FunctionDescriptor::SetActiveConfig:
      return ToHook<HWC2_PFN_SET_ACTIVE_CONFIG>(
          DisplayHook<decltype(&HwcDisplay::SetActiveConfig),
                      &HwcDisplay::SetActiveConfig, hwc2_config_t>);
    case HWC2::FunctionDescriptor::SetClientTarget:
      return ToHook<HWC2_PFN_SET_CLIENT_TARGET>(
          DisplayHook<decltype(&HwcDisplay::SetClientTarget),
                      &HwcDisplay::SetClientTarget, buffer_handle_t, int32_t,
                      int32_t, hwc_region_t>);
    case HWC2::FunctionDescriptor::SetColorMode:
      return ToHook<HWC2_PFN_SET_COLOR_MODE>(
          DisplayHook<decltype(&HwcDisplay::SetColorMode),
                      &HwcDisplay::SetColorMode, int32_t>);
    case HWC2::FunctionDescriptor::SetColorTransform:
      return ToHook<HWC2_PFN_SET_COLOR_TRANSFORM>(
          DisplayHook<decltype(&HwcDisplay::SetColorTransform),
                      &HwcDisplay::SetColorTransform, const float *, int32_t>);
    case HWC2::FunctionDescriptor::SetOutputBuffer:
      return ToHook<HWC2_PFN_SET_OUTPUT_BUFFER>(
          DisplayHook<decltype(&HwcDisplay::SetOutputBuffer),
                      &HwcDisplay::SetOutputBuffer, buffer_handle_t, int32_t>);
    case HWC2::FunctionDescriptor::SetPowerMode:
      return ToHook<HWC2_PFN_SET_POWER_MODE>(
          DisplayHook<decltype(&HwcDisplay::SetPowerMode),
                      &HwcDisplay::SetPowerMode, int32_t>);
    case HWC2::FunctionDescriptor::SetVsyncEnabled:
      return ToHook<HWC2_PFN_SET_VSYNC_ENABLED>(
          DisplayHook<decltype(&HwcDisplay::SetVsyncEnabled),
                      &HwcDisplay::SetVsyncEnabled, int32_t>);
    case HWC2::FunctionDescriptor::ValidateDisplay:
      return ToHook<HWC2_PFN_VALIDATE_DISPLAY>(
          DisplayHook<decltype(&HwcDisplay::ValidateDisplay),
                      &HwcDisplay::ValidateDisplay, uint32_t *, uint32_t *>);

#ifdef ANDROID_S
	case HWC2::FunctionDescriptor::GetDisplayConnectionType:
	  return ToHook<HWC2_PFN_GET_DISPLAY_CONNECTION_TYPE>(
		  DisplayHook<decltype(&HwcDisplay::GetDisplayConnectionType),
					  &HwcDisplay::GetDisplayConnectionType, uint32_t *>);
	case HWC2::FunctionDescriptor::GetDisplayVsyncPeriod:
	  return ToHook<HWC2_PFN_GET_DISPLAY_VSYNC_PERIOD>(
		  DisplayHook<decltype(&HwcDisplay::GetDisplayVsyncPeriod),
					  &HwcDisplay::GetDisplayVsyncPeriod,
					  hwc2_vsync_period_t *>);
#endif //ANDROID_S

    // Layer functions
    case HWC2::FunctionDescriptor::SetCursorPosition:
      return ToHook<HWC2_PFN_SET_CURSOR_POSITION>(
          LayerHook<decltype(&HwcLayer::SetCursorPosition),
                    &HwcLayer::SetCursorPosition, int32_t, int32_t>);
    case HWC2::FunctionDescriptor::SetLayerBlendMode:
      return ToHook<HWC2_PFN_SET_LAYER_BLEND_MODE>(
          LayerHook<decltype(&HwcLayer::SetLayerBlendMode),
                    &HwcLayer::SetLayerBlendMode, int32_t>);
    case HWC2::FunctionDescriptor::SetLayerBuffer:
      return ToHook<HWC2_PFN_SET_LAYER_BUFFER>(
          LayerHook<decltype(&HwcLayer::SetLayerBuffer),
                    &HwcLayer::SetLayerBuffer, buffer_handle_t, int32_t>);
    case HWC2::FunctionDescriptor::SetLayerColor:
      return ToHook<HWC2_PFN_SET_LAYER_COLOR>(
          LayerHook<decltype(&HwcLayer::SetLayerColor),
                    &HwcLayer::SetLayerColor, hwc_color_t>);
    case HWC2::FunctionDescriptor::SetLayerCompositionType:
      return ToHook<HWC2_PFN_SET_LAYER_COMPOSITION_TYPE>(
          LayerHook<decltype(&HwcLayer::SetLayerCompositionType),
                    &HwcLayer::SetLayerCompositionType, int32_t>);
    case HWC2::FunctionDescriptor::SetLayerDataspace:
      return ToHook<HWC2_PFN_SET_LAYER_DATASPACE>(
          LayerHook<decltype(&HwcLayer::SetLayerDataspace),
                    &HwcLayer::SetLayerDataspace, int32_t>);
    case HWC2::FunctionDescriptor::SetLayerDisplayFrame:
      return ToHook<HWC2_PFN_SET_LAYER_DISPLAY_FRAME>(
          LayerHook<decltype(&HwcLayer::SetLayerDisplayFrame),
                    &HwcLayer::SetLayerDisplayFrame, hwc_rect_t>);
    case HWC2::FunctionDescriptor::SetLayerPlaneAlpha:
      return ToHook<HWC2_PFN_SET_LAYER_PLANE_ALPHA>(
          LayerHook<decltype(&HwcLayer::SetLayerPlaneAlpha),
                    &HwcLayer::SetLayerPlaneAlpha, float>);
    case HWC2::FunctionDescriptor::SetLayerSidebandStream:
      return ToHook<HWC2_PFN_SET_LAYER_SIDEBAND_STREAM>(
          LayerHook<decltype(&HwcLayer::SetLayerSidebandStream),
                    &HwcLayer::SetLayerSidebandStream,
                    const native_handle_t *>);
    case HWC2::FunctionDescriptor::SetLayerSourceCrop:
      return ToHook<HWC2_PFN_SET_LAYER_SOURCE_CROP>(
          LayerHook<decltype(&HwcLayer::SetLayerSourceCrop),
                    &HwcLayer::SetLayerSourceCrop, hwc_frect_t>);
    case HWC2::FunctionDescriptor::SetLayerSurfaceDamage:
      return ToHook<HWC2_PFN_SET_LAYER_SURFACE_DAMAGE>(
          LayerHook<decltype(&HwcLayer::SetLayerSurfaceDamage),
                    &HwcLayer::SetLayerSurfaceDamage, hwc_region_t>);
    case HWC2::FunctionDescriptor::SetLayerTransform:
      return ToHook<HWC2_PFN_SET_LAYER_TRANSFORM>(
          LayerHook<decltype(&HwcLayer::SetLayerTransform),
                    &HwcLayer::SetLayerTransform, int32_t>);
    case HWC2::FunctionDescriptor::SetLayerVisibleRegion:
      return ToHook<HWC2_PFN_SET_LAYER_VISIBLE_REGION>(
          LayerHook<decltype(&HwcLayer::SetLayerVisibleRegion),
                    &HwcLayer::SetLayerVisibleRegion, hwc_region_t>);
    case HWC2::FunctionDescriptor::SetLayerZOrder:
      return ToHook<HWC2_PFN_SET_LAYER_Z_ORDER>(
          LayerHook<decltype(&HwcLayer::SetLayerZOrder),
                    &HwcLayer::SetLayerZOrder, uint32_t>);
    case HWC2::FunctionDescriptor::Invalid:
    default:
      return NULL;
  }
}

// static
int DrmHwcTwo::HookDevOpen(const struct hw_module_t *module, const char *name,
                           struct hw_device_t **dev) {
  if (strcmp(name, HWC_HARDWARE_COMPOSER)) {
    ALOGE("Invalid module name- %s", name);
    return -EINVAL;
  }
  InitDebugModule();

  std::unique_ptr<DrmHwcTwo> ctx(new DrmHwcTwo());
  if (!ctx) {
    ALOGE("Failed to allocate DrmHwcTwo");
    return -ENOMEM;
  }

  HWC2::Error err = ctx->Init();
  if (err != HWC2::Error::None) {
    ALOGE("Failed to initialize DrmHwcTwo err=%d\n", err);
    return -EINVAL;
  }

  g_ctx = ctx.get();

  signal(SIGALRM, StaticScreenOptHandler);

  property_set("vendor.hwc.hdr_state","NORMAL");

  ctx->common.module = const_cast<hw_module_t *>(module);
  *dev = &ctx->common;
  ctx.release();

  return 0;
}
}  // namespace android

static struct hw_module_methods_t hwc2_module_methods = {
    .open = android::DrmHwcTwo::HookDevOpen,
};

hw_module_t HAL_MODULE_INFO_SYM = {
    .tag = HARDWARE_MODULE_TAG,
    .module_api_version = HARDWARE_MODULE_API_VERSION(2, 0),
    .id = HWC_HARDWARE_MODULE_ID,
    .name = "DrmHwcTwo module",
    .author = "The Android Open Source Project",
    .methods = &hwc2_module_methods,
    .dso = NULL,
    .reserved = {0},
};
