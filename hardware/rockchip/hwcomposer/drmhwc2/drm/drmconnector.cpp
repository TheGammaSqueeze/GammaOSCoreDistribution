/*
 * Copyright (C) 2015 The Android Open Source Project
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

#define LOG_TAG "hwc-drm-connector"

#include "drmconnector.h"
#include "drmdevice.h"
#include "rockchip/utils/drmdebug.h"

#include <errno.h>
#include <stdint.h>

#include <log/log.h>
#include <xf86drmMode.h>

namespace android {

#define GET_BCSH_PROPERTY_VALUE(display_id, func_str, type_str, output_value) \
  snprintf(bcsh_property,PROPERTY_VALUE_MAX,func_str,type_str);\
  ret = property_get(bcsh_property,bcsh_value,""); \
  if(!ret){ \
    if(display_id == HWC_DISPLAY_PRIMARY){ \
      snprintf(bcsh_property,PROPERTY_VALUE_MAX,func_str,"main"); \
    }else{ \
      snprintf(bcsh_property,PROPERTY_VALUE_MAX,func_str,"aux"); \
    } \
    ret = property_get(bcsh_property,bcsh_value,""); \
    if(ret){ \
      output_value = atoi(bcsh_value); \
      exist_suitable_property = true;  \
    } \
  }else{ \
    output_value = atoi(bcsh_value); \
    exist_suitable_property = true;  \
  }

#define ALOGI_BEST_MODE_INFO(mode)  \
        ALOGI("%s,line=%d, Find best mode-id=%d : %dx%d%c%f",\
                __FUNCTION__,__LINE__,mode.id(),        \
                mode.h_display(),mode.v_display(), \
                (flags & DRM_MODE_FLAG_INTERLACE) > 0 ? 'c' : 'p', mode.v_refresh())

DrmConnector::DrmConnector(DrmDevice *drm, drmModeConnectorPtr c,
                           DrmEncoder *current_encoder,
                           std::vector<DrmEncoder *> &possible_encoders)
    : drm_(drm),
      id_(c->connector_id),
      encoder_(current_encoder),
      display_(-1),
      type_(c->connector_type),
      type_id_(c->connector_type_id),
      unique_id_(0),
      priority_(0),
      state_(c->connection),
      mm_width_(c->mmWidth),
      mm_height_(c->mmHeight),
      possible_encoders_(possible_encoders),
      connector_(c),
      possible_displays_(0),
      bModeReady_(false),
      bSupportSt2084_(false),
      bSupportHLG_(false),
      baseparameter_ready_(false){
}

int DrmConnector::Init() {
  int ret = drm_->GetConnectorProperty(*this, "DPMS", &dpms_property_);
  if (ret) {
    ALOGE("Could not get DPMS property\n");
    return ret;
  }
  ret = drm_->GetConnectorProperty(*this, "CRTC_ID", &crtc_id_property_);
  if (ret) {
    ALOGE("Could not get CRTC_ID property\n");
    return ret;
  }
  if (writeback()) {
    ret = drm_->GetConnectorProperty(*this, "WRITEBACK_PIXEL_FORMATS",
                                     &writeback_pixel_formats_);
    if (ret) {
      ALOGE("Could not get WRITEBACK_PIXEL_FORMATS connector_id = %d\n", id_);
      return ret;
    }
    ret = drm_->GetConnectorProperty(*this, "WRITEBACK_FB_ID",
                                     &writeback_fb_id_);
    if (ret) {
      ALOGE("Could not get WRITEBACK_FB_ID connector_id = %d\n", id_);
      return ret;
    }
    ret = drm_->GetConnectorProperty(*this, "WRITEBACK_OUT_FENCE_PTR",
                                     &writeback_out_fence_);
    if (ret) {
      ALOGE("Could not get WRITEBACK_OUT_FENCE_PTR connector_id = %d\n", id_);
      return ret;
    }
  }

  ret = drm_->GetConnectorProperty(*this, "brightness", &brightness_id_property_);
  if (ret)
    ALOGW("Could not get brightness property\n");

  ret = drm_->GetConnectorProperty(*this, "contrast", &contrast_id_property_);
  if (ret)
    ALOGW("Could not get contrast property\n");

  ret = drm_->GetConnectorProperty(*this, "saturation", &saturation_id_property_);
  if (ret)
    ALOGW("Could not get saturation property\n");

  ret = drm_->GetConnectorProperty(*this, "hue", &hue_id_property_);
  if (ret)
    ALOGW("Could not get hue property\n");

  ret = drm_->GetConnectorProperty(*this, "HDR_OUTPUT_METADATA", &hdr_metadata_property_);
  if (ret)
    ALOGW("Could not get hdr output metadata property\n");

  ret = drm_->GetConnectorProperty(*this, "HDR_PANEL_METADATA", &hdr_panel_property_);
  if (ret)
    ALOGW("Could not get hdr panel metadata property\n");

  // Kernel version 5.10 starts using new attribute definitions Colorspace
  ret = drm_->GetConnectorProperty(*this, "Colorspace", &colorspace_property_);
  if (ret){
    ALOGW("Could not get Colorspace property, try to get hdmi_output_colorimetry property.\n");
    // Before Kernel version 5.10 starts using old attribute definitions hdmi_output_colorimetry
    ret = drm_->GetConnectorProperty(*this, "hdmi_output_colorimetry", &colorspace_property_);
    if(ret){
      ALOGW("Could not get hdmi_output_colorimetry property.\n");
    }
  }

  // Kernel version 5.10 starts using new attribute definitions color_format
  ret = drm_->GetConnectorProperty(*this, "color_format", &color_format_property_);
  if (ret) {
    ALOGW("Could not get color_format property, try to get hdmi_output_format property.\n");
    // Before Kernel version 5.10 using old attribute definitions hdmi_output_format
    ret = drm_->GetConnectorProperty(*this, "hdmi_output_format", &color_format_property_);
    if(ret){
      ALOGW("Could not get hdmi_output_format property.\n");
    }
  }

  // Kernel version 5.10 starts using new attribute definitions color_depth
  ret = drm_->GetConnectorProperty(*this, "color_depth", &color_depth_property_);
  if (ret) {
    ALOGW("Could not get color_depth property, try to get hdmi_output_depth\n");
    // Before Kernel version 5.10 using old attribute definitions hdmi_output_depth
    ret = drm_->GetConnectorProperty(*this, "hdmi_output_depth", &color_depth_property_);
    if(ret){
      ALOGW("Could not get hdmi_output_depth property\n");
    }
  }

  // Kernel version 5.10 to get color_format_caps
  ret = drm_->GetConnectorProperty(*this, "color_format_caps", &color_format_caps_property_);
  if (ret) {
    ALOGW("Could not get hdmi_output_format property\n");
  }

  // Kernel version 5.10 to get color_depth_caps
  ret = drm_->GetConnectorProperty(*this, "color_depth_caps", &color_depth_caps_property_);
  if (ret) {
   ALOGW("Could not get hdmi_output_depth property\n");
  }

  unique_id_=0;
  ret = drm_->GetConnectorProperty(*this, "CONNECTOR_ID", &connector_id_property_);
  if (ret) {
    ALOGW("Could not get CONNECTOR_ID property\n");
  }else{
    std::tie(ret,unique_id_) = connector_id_property_.value();
  }

  drm_->GetHdrPanelMetadata(this,&hdr_metadata_);
  bSupportSt2084_ = drm_->is_hdr_panel_support_st2084(this);
  bSupportHLG_    = drm_->is_hdr_panel_support_HLG(this);
  drmHdr_.clear();
  if(bSupportSt2084_){
      drmHdr_.push_back(DrmHdr(DRM_HWC_HDR10,
                        hdr_metadata_.max_display_mastering_luminance,
                        (hdr_metadata_.max_display_mastering_luminance + hdr_metadata_.min_display_mastering_luminance) / 2,
                        hdr_metadata_.min_display_mastering_luminance));
  }

  if(bSupportHLG_){
      drmHdr_.push_back(DrmHdr(DRM_HWC_HLG,
                        hdr_metadata_.max_display_mastering_luminance,
                        (hdr_metadata_.max_display_mastering_luminance + hdr_metadata_.min_display_mastering_luminance) / 2,
                        hdr_metadata_.min_display_mastering_luminance));
  }

  // Update Baseparameter Info
  ret = drm_->UpdateConnectorBaseInfo(type_,unique_id_,&baseparameter_);
  if(ret){
    ALOGI("UpdateConnectorBaseInfo fail, the device may not have a baseparameter.");
    baseparameter_ready_=false;
  }else{
    baseparameter_ready_=true;
  }

  snprintf(cUniqueName_,30,"%s-%d",drm_->connector_type_str(type_),unique_id_);

  bSpiltMode_=false;
  ret = drm_->GetConnectorProperty(*this, "USER_SPLIT_MODE", &spilt_mode_property_);
  if (ret) {
    ALOGW("Could not get USER_SPLIT_MODE property\n");
  }else{
    std::tie(ret,bSpiltMode_) = spilt_mode_property_.value();
  }

  return 0;
}

uint32_t DrmConnector::id() const {
  return id_;
}

int DrmConnector::display() const {
  return display_;
}

void DrmConnector::set_display(int display) {
  display_ = display;
}
int DrmConnector::priority() const{
  return priority_;
}
void DrmConnector::set_priority(uint32_t priority){
  priority_ = priority;
}

uint32_t DrmConnector::possible_displays() const {
  return possible_displays_;
}

void DrmConnector::set_possible_displays(uint32_t possible_displays) {
  possible_displays_ = possible_displays;
}

bool DrmConnector::internal() const {

  if(!possible_displays_){
    return type_ == DRM_MODE_CONNECTOR_LVDS || type_ == DRM_MODE_CONNECTOR_eDP ||
           type_ == DRM_MODE_CONNECTOR_DSI ||
           type_ == DRM_MODE_CONNECTOR_VIRTUAL || type_ == DRM_MODE_CONNECTOR_DPI;
  }else{
    return (possible_displays_ & HWC_DISPLAY_PRIMARY_BIT) > 0;
  }
}

bool DrmConnector::external() const {
  if(!possible_displays_){
    return type_ == DRM_MODE_CONNECTOR_HDMIA ||
           type_ == DRM_MODE_CONNECTOR_DisplayPort ||
           type_ == DRM_MODE_CONNECTOR_DVID || type_ == DRM_MODE_CONNECTOR_DVII ||
           type_ == DRM_MODE_CONNECTOR_VGA;
  }else{
    return (possible_displays_ & HWC_DISPLAY_EXTERNAL_BIT) > 0;
  }
}

bool DrmConnector::hotplug() const{
  return type_ == DRM_MODE_CONNECTOR_HDMIA ||
          type_ == DRM_MODE_CONNECTOR_DisplayPort ||
          type_ == DRM_MODE_CONNECTOR_DVID || type_ == DRM_MODE_CONNECTOR_DVII ||
          type_ == DRM_MODE_CONNECTOR_VGA;
}

bool DrmConnector::writeback() const {
#ifdef DRM_MODE_CONNECTOR_WRITEBACK
  return type_ == DRM_MODE_CONNECTOR_WRITEBACK;
#else
  return false;
#endif
}

bool DrmConnector::valid_type() const {
  return internal() || external() || writeback();
}

int DrmConnector::UpdateModes() {
  std::unique_lock<std::recursive_mutex> lock(mRecursiveMutex);

  int fd = drm_->fd();

  drmModeConnectorPtr c = drmModeGetConnector(fd, id_);
  if (!c) {
    ALOGE("Failed to get connector %d", id_);
    return -ENODEV;
  }

  drm_->GetHdrPanelMetadata(this, &hdr_metadata_);
  //When Plug-in/Plug-out TV panel,some Property of the connector will need be updated.
  bSupportSt2084_ = drm_->is_hdr_panel_support_st2084(this);
  bSupportHLG_    = drm_->is_hdr_panel_support_HLG(this);

  state_ = c->connection;

  if (!c->count_modes)
    state_ = DRM_MODE_DISCONNECTED;

  bool preferred_mode_found = false;
  std::vector<DrmMode> new_modes;
  for (int i = 0; i < c->count_modes; ++i) {
    bool exists = false;
    for (const DrmMode &mode : modes_) {
      if (mode == c->modes[i]) {
        if(type_ == DRM_MODE_CONNECTOR_HDMIA || type_ == DRM_MODE_CONNECTOR_DisplayPort){
            //filter mode by /system/usr/share/resolution_white.xml.
            if(drm_->mode_verify(mode)){
                new_modes.push_back(mode);
                exists = true;
                break;
            }
        }else{
            new_modes.push_back(mode);
            exists = true;
            break;
        }
      }
    }
    if (exists)
      continue;

    DrmMode m(&c->modes[i]);
    if ((type_ == DRM_MODE_CONNECTOR_HDMIA || type_ == DRM_MODE_CONNECTOR_DisplayPort) && !drm_->mode_verify(m))
      continue;

    m.set_id(drm_->next_mode_id());
    new_modes.push_back(m);

    // Use only the first DRM_MODE_TYPE_PREFERRED mode found
    if (!preferred_mode_found &&
        (new_modes.back().type() & DRM_MODE_TYPE_PREFERRED)) {
      preferred_mode_id_ = new_modes.back().id();
      preferred_mode_found = true;
    }
  }
  modes_.swap(new_modes);

  //Get original mode from connector
  std::vector<DrmMode> new_raw_modes;
  for (int i = 0; i < c->count_modes; ++i) {
    bool exists = false;
    for (const DrmMode &mode : modes_) {
      if (mode == c->modes[i]) {
        new_raw_modes.push_back(mode);
        exists = true;
        break;
      }
    }
    if (exists)
      continue;

    DrmMode m(&c->modes[i]);
    m.set_id(drm_->next_mode_id());
    new_raw_modes.push_back(m);
  }
  raw_modes_.swap(new_raw_modes);

  if (!preferred_mode_found && modes_.size() != 0) {
    preferred_mode_id_ = modes_[0].id();
  }

  bModeReady_ = true;

  HWC2_ALOGD_IF_DEBUG("conn=%d state=%d count_modes.size=%d modes_.size=%zu new_raw_modes.size=%zu",
        id_, state_,c->count_modes, modes_.size(),raw_modes_.size());


  drmModeFreeConnector(c);

  // VRR
  UpdateVrrModes();

  return 0;
}

int DrmConnector::UpdateVrrModes(){

  if(!encoder() || !(encoder()->crtc()) || encoder()->crtc()->variable_refresh_rate().id() == 0){
    return 0;
  }

  if(modes_.size() != 1){
    return 0;
  }

  int ret = 0;
  vrr_modes_.clear();

  DrmCrtc* crtc = encoder()->crtc();
  uint64_t min_refresh_rate = 0;
  uint64_t max_refresh_rate = 0;
  std::tie(ret, min_refresh_rate) = crtc->min_refresh_rate().value();
  std::tie(ret, max_refresh_rate) = crtc->max_refresh_rate().value();

  if(min_refresh_rate == 0 || max_refresh_rate == 0){
    return 0;
  }

  for(uint64_t fps = max_refresh_rate ; fps >= min_refresh_rate; fps -= 10){
    vrr_modes_.push_back(fps);
    if(fps < 10)
      break;
  }

  return 0;

}

int DrmConnector::UpdateDisplayMode(int display_id, int update_base_timeline){
  std::unique_lock<std::recursive_mutex> lock(mRecursiveMutex);
  char resolution_value[PROPERTY_VALUE_MAX]={0};
  char resolution_property[PROPERTY_VALUE_MAX]={0};
  uint32_t width, height, flags, clock;
  uint32_t hsync_start, hsync_end, htotal;
  uint32_t vsync_start, vsync_end, vtotal;
  bool interlaced;
  float vrefresh;
  char val;
  uint32_t MaxResolution = 0,temp;

  snprintf(resolution_property,PROPERTY_VALUE_MAX,"persist.vendor.resolution.%s",cUniqueName_);
  property_get(resolution_property, resolution_value, "Unkonw");

  ALOGI("%s,line=%d, display=%d %s=%s",__FUNCTION__,__LINE__,display_id,resolution_property,resolution_value);

  if(!strcmp(resolution_value,"Unkonw")){
    if(display_id == HWC_DISPLAY_PRIMARY){
      property_get("persist.vendor.resolution.main", resolution_value, "Unkonw");
    }else{
      property_get("persist.vendor.resolution.aux", resolution_value, "Unkonw");
    }
    ALOGI("%s,line=%d, display=%d persist.vendor.resolution.%s=%s",__FUNCTION__,__LINE__,display_id,
          display_id == HWC_DISPLAY_PRIMARY ? "main" : "aux",resolution_value);
  }

  if(strcmp(resolution_value,"Unkonw") != 0){
    ALOGI("%s,line=%d, resolution_value=%s",__FUNCTION__,__LINE__,resolution_value);
    int len = sscanf(resolution_value, "%dx%d@%f-%d-%d-%d-%d-%d-%d-%x-%d",
                     &width, &height, &vrefresh, &hsync_start,
                     &hsync_end, &htotal, &vsync_start,&vsync_end,
                     &vtotal, &flags, &clock);
    // Support clock
    if (len == 11 && width != 0 && height != 0) {
      for (const DrmMode &conn_mode : modes()) {
        if (conn_mode.equal(width, height, hsync_start, hsync_end,
                            htotal, vsync_start, vsync_end, vtotal, flags, clock)) {
          set_best_mode(conn_mode);
          ALOGI_BEST_MODE_INFO(conn_mode);
          return 0;
        }
      }
    }

    // Old resolution format
    if (len == 10 && width != 0 && height != 0) {
      for (const DrmMode &conn_mode : modes()) {
        if (conn_mode.equal(width, height, vrefresh, hsync_start, hsync_end,
                            htotal, vsync_start, vsync_end, vtotal, flags)) {
          set_best_mode(conn_mode);
          ALOGI_BEST_MODE_INFO(conn_mode);
          return 0;
        }
      }
    }

    uint32_t ivrefresh;
    len = sscanf(resolution_value, "%dx%d%c%d", &width, &height, &val, &ivrefresh);

    if (val == 'i')
      interlaced = true;
    else
      interlaced = false;
    if (len == 4 && width != 0 && height != 0) {
      for (const DrmMode &conn_mode : modes()) {
        if (conn_mode.equal(width, height, ivrefresh, interlaced)) {
          set_best_mode(conn_mode);
          ALOGI_BEST_MODE_INFO(conn_mode);
          return 0;
        }
      }
    }
  }else{ // resolution_value is Unkonw
    if(baseparameter_ready_ && !strcmp(resolution_value,"Unkonw")){
      ALOGI("%s,line=%d, can't find suitable Resolution Property, try to use Baseparameter.",__FUNCTION__,__LINE__);
      if(update_base_timeline != iTimeline_){
        iTimeline_ = update_base_timeline;
        int ret = drm_->UpdateConnectorBaseInfo(type_,unique_id_,&baseparameter_);
        if(ret){
          ALOGW("%s,line=%d,UpdateConnectorBaseInfo fail, the device may not have a baseparameter.",__FUNCTION__,__LINE__);
        }
      }
      width       = baseparameter_.screen_info[0].resolution.hdisplay;
      height      = baseparameter_.screen_info[0].resolution.vdisplay;
      hsync_start = baseparameter_.screen_info[0].resolution.hsync_start;
      hsync_end   = baseparameter_.screen_info[0].resolution.hsync_end;
      htotal      = baseparameter_.screen_info[0].resolution.htotal;
      vsync_start = baseparameter_.screen_info[0].resolution.vsync_start;
      vsync_end   = baseparameter_.screen_info[0].resolution.vsync_end;
      vtotal      = baseparameter_.screen_info[0].resolution.vtotal;
      flags       = baseparameter_.screen_info[0].resolution.flags;
      clock       = baseparameter_.screen_info[0].resolution.clock;
      // Support clock
      if (width != 0 && height != 0) {
        for (const DrmMode &conn_mode : modes()) {
          if (conn_mode.equal(width, height, hsync_start, hsync_end,
                              htotal, vsync_start, vsync_end, vtotal, flags, clock)) {
            set_best_mode(conn_mode);
            ALOGI_BEST_MODE_INFO(conn_mode);
            return 0;
          }
        }
      }
    }
  }

  for (const DrmMode &conn_mode : modes()) {
    if (conn_mode.type() & DRM_MODE_TYPE_PREFERRED) {
      set_best_mode(conn_mode);
      ALOGI_BEST_MODE_INFO(conn_mode);
      return 0;
    }
  }

  // 20230104: 参考uboot分辨率获取逻辑，如果获取不到最佳分辨率，则直接使用白名单列表第一个分辨率
  for (const DrmMode &conn_mode : modes()) {
    set_best_mode(conn_mode);
    ALOGI_BEST_MODE_INFO(conn_mode);
    return 0;
  }

  //use raw modes to get mode.
  for (const DrmMode &conn_mode : raw_modes()) {
    if (conn_mode.type() & DRM_MODE_TYPE_PREFERRED) {
      set_best_mode(conn_mode);
      ALOGI_BEST_MODE_INFO(conn_mode);
      return 0;
    }
  }

  // 20230104: 如果白名单分辨率一个都没有，直接获取分辨率列表第一个分辨率
  for (const DrmMode &conn_mode : raw_modes()) {
    set_best_mode(conn_mode);
    ALOGI_BEST_MODE_INFO(conn_mode);
    return 0;
  }

  ALOGE("Error: Should not get here display=%d %s %d\n", display_id, __FUNCTION__, __LINE__);
  DrmMode mode;
  set_best_mode(mode);

  return 0;
}

int DrmConnector::GetSuitableMode(int display_id, uint64_t max_width, uint64_t dlck){
  std::unique_lock<std::recursive_mutex> lock(mRecursiveMutex);
  uint32_t flags = 0;
  for (const DrmMode &conn_mode : modes()) {
    if (conn_mode.type() & DRM_MODE_TYPE_PREFERRED) {
      if(conn_mode.h_display() >  max_width){
        continue;
      }

      if(conn_mode.h_display() *
        conn_mode.v_display() *
        (uint64_t)conn_mode.v_refresh() >  dlck){
        continue;
      }
      set_best_mode(conn_mode);
      set_current_mode(conn_mode);
      ALOGI_BEST_MODE_INFO(conn_mode);
      return 0;
    }
  }

  // 20230104: 参考uboot分辨率获取逻辑，如果获取不到最佳分辨率，则直接使用白名单列表第一个分辨率
  for (const DrmMode &conn_mode : modes()) {
    if(conn_mode.h_display() >  max_width){
      continue;
    }

    if(conn_mode.h_display() *
      conn_mode.v_display() *
      (uint64_t)conn_mode.v_refresh() >  dlck){
      continue;
    }
    set_best_mode(conn_mode);
    set_current_mode(conn_mode);
    ALOGI_BEST_MODE_INFO(conn_mode);
    return 0;
  }

  //use raw modes to get mode.
  for (const DrmMode &conn_mode : raw_modes()) {
    if (conn_mode.type() & DRM_MODE_TYPE_PREFERRED) {
      if(conn_mode.h_display() >  max_width){
        continue;
      }

      if(conn_mode.h_display() *
        conn_mode.v_display() *
        (uint64_t)conn_mode.v_refresh() >  dlck){
        continue;
      }
      set_best_mode(conn_mode);
      set_current_mode(conn_mode);
      ALOGI_BEST_MODE_INFO(conn_mode);
      return 0;
    }
  }

  // 20230104: 如果白名单分辨率一个都没有，直接获取分辨率列表第一个分辨率
  for (const DrmMode &conn_mode : raw_modes()) {
      if(conn_mode.h_display() >  max_width){
        continue;
      }

      if(conn_mode.h_display() *
        conn_mode.v_display() *
        (uint64_t)conn_mode.v_refresh() >  dlck){
        continue;
      }
      set_best_mode(conn_mode);
      set_current_mode(conn_mode);
      ALOGI_BEST_MODE_INFO(conn_mode);
    return 0;
  }

  ALOGE("Error: Should not get here display=%d %s %d\n", display_id, __FUNCTION__, __LINE__);
  DrmMode mode;
  set_best_mode(mode);

  return 0;
}

int DrmConnector::SetDisplayModeInfo(int display_id) {
  int ret = 0;
  const DrmMode mode = current_mode();
  if(baseparameter_ready_){
    baseparameter_.screen_info[0].resolution.hdisplay = mode.h_display();
    baseparameter_.screen_info[0].resolution.vdisplay = mode.v_display();
    baseparameter_.screen_info[0].resolution.vrefresh = static_cast<int>(mode.v_refresh());
    baseparameter_.screen_info[0].resolution.hsync_start = mode.h_sync_start();
    baseparameter_.screen_info[0].resolution.hsync_end = mode.h_sync_end();
    baseparameter_.screen_info[0].resolution.htotal = mode.h_total();
    baseparameter_.screen_info[0].resolution.vsync_start = mode.v_sync_start();
    baseparameter_.screen_info[0].resolution.vsync_end = mode.v_sync_end();
    baseparameter_.screen_info[0].resolution.vtotal = mode.v_total();
    baseparameter_.screen_info[0].resolution.flags = mode.flags();
    baseparameter_.screen_info[0].resolution.clock = mode.clock();
    ret = drm_->SetScreenInfo(type_,unique_id_,0,baseparameter_.screen_info);
    if(ret){
      ALOGW("%s,line=%d,display-id=%d %s SetScreenInfo fail!",__FUNCTION__,__LINE__,display_id,cUniqueName_);
      return ret;
    }
  }

  return ret;
}

int DrmConnector::UpdateOverscan(int display_id, char *overscan_value){
  char overscan_property[PROPERTY_VALUE_MAX]={0};

  snprintf(overscan_property,PROPERTY_VALUE_MAX,"persist.vendor.overscan.%s",cUniqueName_);
  property_get(overscan_property, overscan_value, "Unkonw");

  // ALOGI("%s,line=%d, display=%d %s=%s",__FUNCTION__,__LINE__,display_id,overscan_property,overscan_value);

  if(!strcmp(overscan_value,"Unkonw")){
    if(display_id == HWC_DISPLAY_PRIMARY){
      property_get("persist.vendor.overscan.main", overscan_value, "Unkonw");
    }else{
      property_get("persist.vendor.overscan.aux", overscan_value, "Unkonw");
    }
    // ALOGI("%s,line=%d, display=%d persist.vendor.overscan.%s=%s",__FUNCTION__,__LINE__,display_id,
    //       display_id == HWC_DISPLAY_PRIMARY ? "main" : "aux", overscan_value);
  }
  return 0;
}

int DrmConnector::UpdateBCSH(int display_id, int update_base_timeline){
  std::unique_lock<std::recursive_mutex> lock(mRecursiveMutex);
  uint32_t brightness=50, contrast=50, saturation=50, hue=50;
  int ret = 0;
  bool exist_suitable_property=false;
  char bcsh_property[PROPERTY_VALUE_MAX]={0};
  char bcsh_value[PROPERTY_VALUE_MAX]={0};

  GET_BCSH_PROPERTY_VALUE(display_id, "persist.vendor.brightness.%s", cUniqueName_, brightness);
  GET_BCSH_PROPERTY_VALUE(display_id, "persist.vendor.contrast.%s",   cUniqueName_, contrast);
  GET_BCSH_PROPERTY_VALUE(display_id, "persist.vendor.saturation.%s", cUniqueName_, saturation);
  GET_BCSH_PROPERTY_VALUE(display_id, "persist.vendor.hue.%s",        cUniqueName_, hue);

  if(!exist_suitable_property && baseparameter_ready_){
    ALOGI("%s,line=%d, %s can't find suitable BCSH Property, try to use Baseparameter.",
          __FUNCTION__,__LINE__,cUniqueName_);
    if(update_base_timeline != iTimeline_){
      iTimeline_ = update_base_timeline;
      int ret = drm_->UpdateConnectorBaseInfo(type_,unique_id_,&baseparameter_);
      if(ret){
        ALOGW("%s,line=%d,%s UpdateConnectorBaseInfo fail, the device may not have a baseparameter.",
        __FUNCTION__,__LINE__,cUniqueName_);
      }
    }
    brightness = baseparameter_.bcsh_info.brightness;
    contrast   = baseparameter_.bcsh_info.contrast;
    saturation = baseparameter_.bcsh_info.saturation;
    hue        = baseparameter_.bcsh_info.hue;
  }

  ALOGI("%s,line=%d, %s BCSH=[%d,%d,%d,%d]",__FUNCTION__,__LINE__,cUniqueName_,brightness, contrast, saturation, hue);

  if(uBrightness_ != brightness || uContrast_ != contrast ||
     uSaturation_ != saturation || uHue_ != hue){
    drmModeAtomicReqPtr pset = drmModeAtomicAlloc();
    if (!pset) {
      ALOGE("Failed to allocate property set");
      return -ENOMEM;
    }
    DRM_ATOMIC_ADD_PROP(id(), brightness_id_property().id(),brightness > 100 ? 100 : brightness)
    DRM_ATOMIC_ADD_PROP(id(), contrast_id_property().id(),contrast > 100 ? 100 : contrast)
    DRM_ATOMIC_ADD_PROP(id(), saturation_id_property().id(),saturation > 100 ? 100 : saturation)
    DRM_ATOMIC_ADD_PROP(id(), hue_id_property().id(),hue > 100 ? 100 : hue)

    uint32_t flags = 0;
    ret = drmModeAtomicCommit(drm_->fd(), pset, flags, this);
    if (ret < 0) {
      ALOGE("Failed to commit pset ret=%d\n", ret);
      drmModeAtomicFree(pset);
      pset=NULL;
      return ret;
    }
    drmModeAtomicFree(pset);
    pset=NULL;
    uBrightness_ = brightness;
    uContrast_ = contrast;
    uSaturation_ = saturation;
    uHue_ = hue;
  }
  return 0;
}

bool DrmConnector::ParseHdmiOutputFormat(char* strprop, int *format, int *depth) {
    if (!strcmp(strprop, "Auto")) {
        *format = output_ycbcr_high_subsampling;
        *depth = Automatic;
        return true;
    }

    if (!strcmp(strprop, "RGB-8bit")) {
        *format = output_rgb;
        *depth = depth_24bit;
        return true;
    }

    if (!strcmp(strprop, "RGB-10bit")) {
        *format = output_rgb;
        *depth = depth_30bit;
        return true;
    }

    if (!strcmp(strprop, "YCBCR444-8bit")) {
        *format = output_ycbcr444;
        *depth = depth_24bit;
        return true;
    }

    if (!strcmp(strprop, "YCBCR444-10bit")) {
        *format = output_ycbcr444;
        *depth = depth_30bit;
        return true;
    }

    if (!strcmp(strprop, "YCBCR422-8bit")) {
        *format = output_ycbcr422;
        *depth = depth_24bit;
        return true;
    }

    if (!strcmp(strprop, "YCBCR422-10bit")) {
        *format = output_ycbcr422;
        *depth = depth_30bit;
        return true;
    }

    if (!strcmp(strprop, "YCBCR420-8bit")) {
        *format = output_ycbcr420;
        *depth = depth_24bit;
        return true;
    }

    if (!strcmp(strprop, "YCBCR420-10bit")) {
        *format = output_ycbcr420;
        *depth = depth_30bit;
        return true;
    }
    ALOGE("hdmi output format is invalid. [%s]", strprop);
    return false;
}

int DrmConnector::UpdateOutputFormat(int display_id, int update_base_timeline){
  std::unique_lock<std::recursive_mutex> lock(mRecursiveMutex);
  if(!(color_format_property().id() > 0 || color_depth_property().id() > 0)){
    return 0;
  }

  int ret = 0;
  bool update = false;
  int color_format = -1;
  int color_depth = -1;
  bool need_change_format = false,need_change_depth = false;
  bool exist_suitable_property = false;
  char output_format_pro[PROPERTY_VALUE_MAX]={0};
  char output_format_value[PROPERTY_VALUE_MAX]={0};

  snprintf(output_format_pro,PROPERTY_VALUE_MAX,"persist.vendor.color.%s",cUniqueName_);
  ret = property_get(output_format_pro,output_format_value,"");
  if(!ret){
    if(display_id == HWC_DISPLAY_PRIMARY){
      snprintf(output_format_pro,PROPERTY_VALUE_MAX,"persist.vendor.color.%s","main");
    }else{
      snprintf(output_format_pro,PROPERTY_VALUE_MAX,"persist.vendor.color.%s","aux");
    }
    ret = property_get(output_format_pro,output_format_value,"");
    if(ret){
      exist_suitable_property = true;
    }
  }else{
    exist_suitable_property = true;
  }

  if(exist_suitable_property){
    ret = ParseHdmiOutputFormat(output_format_value, &color_format, &color_depth);
    if (ret == false) {
      ALOGE("Get color fail! to use default ");
      color_format = output_rgb;
      color_depth = depth_24bit;
    }
  }else if(baseparameter_ready_){
    ALOGI("%s,line=%d, %s can't find suitable output format Property, try to use Baseparameter.",
          __FUNCTION__,__LINE__,cUniqueName_);
    if(update_base_timeline != iTimeline_){
      iTimeline_ = update_base_timeline;
      int ret = drm_->UpdateConnectorBaseInfo(type_,unique_id_,&baseparameter_);
      if(ret){
        ALOGW("%s,line=%d,%s UpdateConnectorBaseInfo fail, the device may not have a baseparameter.",
        __FUNCTION__,__LINE__,cUniqueName_);
      }
    }
    color_format = baseparameter_.screen_info[0].format;
    color_depth   = baseparameter_.screen_info[0].depthc;
  }else{
    color_format = output_ycbcr_high_subsampling;
    color_depth = depth_24bit;
  }


  if(color_format != -1 && uColorFormat_ != color_format) {
    update = true;
    need_change_format = true;
  }

  if(color_depth != -1 && uColorDepth_ != color_depth) {
    update = true;
    need_change_depth = true;
  }

  if(!update)
    return 0;

  drmModeAtomicReqPtr pset = drmModeAtomicAlloc();
  if (!pset) {
      ALOGE("%s:line=%d Failed to allocate property set", __FUNCTION__, __LINE__);
      return false;
  }

  if(need_change_format > 0) {
    ALOGI("%s,line=%d %s change hdmi output format: %d", __FUNCTION__,__LINE__, cUniqueName_, color_format);
    ret = drmModeAtomicAddProperty(pset, id(), color_format_property().id(), color_format);
    if (ret < 0) {
      ALOGE("%s:line=%d Failed to add prop[%d] to [%d]", __FUNCTION__, __LINE__, color_format_property().id(), id());
    }
  }

  if(need_change_depth > 0) {
    ALOGI("%s,line=%d %s change hdmi output depth: %d", __FUNCTION__,__LINE__, cUniqueName_, color_depth);
    ret = drmModeAtomicAddProperty(pset, id(), color_depth_property().id(), color_depth);
    if (ret < 0) {
      ALOGE("%s:line=%d Failed to add prop[%d] to [%d]", __FUNCTION__, __LINE__, color_depth_property().id(), id());
    }
  }

  ret = drmModeAtomicCommit(drm_->fd(), pset, DRM_MODE_ATOMIC_ALLOW_MODESET, drm_);
  if (ret < 0) {
    ALOGE("%s:line=%d %s Failed to commit! ret=%d", __FUNCTION__, __LINE__, cUniqueName_, ret);
  }else{
    uColorFormat_ = color_format;
    uColorDepth_ = color_depth;
  }

  drmModeAtomicFree(pset);
  pset = NULL;

  return 0;
}

int DrmConnector::UpdateOutputFormat(drmModeAtomicReqPtr pset){
  std::unique_lock<std::recursive_mutex> lock(mRecursiveMutex);
  if(!(color_format_property().id() > 0 || color_depth_property().id() > 0)){
    return 0;
  }

  int ret = 0;
  bool update = false;
  int color_format = -1;
  int color_depth = -1;
  bool need_change_format = false,need_change_depth = false;
  bool exist_suitable_property = false;
  char output_format_pro[PROPERTY_VALUE_MAX]={0};
  char output_format_value[PROPERTY_VALUE_MAX]={0};

  snprintf(output_format_pro,PROPERTY_VALUE_MAX,"persist.vendor.color.%s",cUniqueName_);
  ret = property_get(output_format_pro,output_format_value,"");
  if(!ret){
    if(display_ == HWC_DISPLAY_PRIMARY){
      snprintf(output_format_pro,PROPERTY_VALUE_MAX,"persist.vendor.color.%s","main");
    }else{
      snprintf(output_format_pro,PROPERTY_VALUE_MAX,"persist.vendor.color.%s","aux");
    }
    ret = property_get(output_format_pro,output_format_value,"");
    if(ret){
      exist_suitable_property = true;
    }
  }else{
    exist_suitable_property = true;
  }

  if(exist_suitable_property){
    ret = ParseHdmiOutputFormat(output_format_value, &color_format, &color_depth);
    if (ret == false) {
      ALOGE("Get color fail! to use default ");
      color_format = output_ycbcr_high_subsampling;
      color_depth = depth_24bit;
    }
  }else if(baseparameter_ready_){
    ALOGI("%s,line=%d, %s can't find suitable output format Property, try to use Baseparameter.",
          __FUNCTION__,__LINE__,cUniqueName_);
    int ret = drm_->UpdateConnectorBaseInfo(type_,unique_id_,&baseparameter_);
    if(ret){
      ALOGW("%s,line=%d,%s UpdateConnectorBaseInfo fail, the device may not have a baseparameter.",
      __FUNCTION__,__LINE__,cUniqueName_);
    }
    color_format = baseparameter_.screen_info[0].format;
    color_depth   = baseparameter_.screen_info[0].depthc;
  }else{
    color_format = output_ycbcr_high_subsampling;
    color_depth = depth_24bit;
  }

  ALOGI("%s,line=%d %s change hdmi output format: %d", __FUNCTION__,__LINE__, cUniqueName_, color_format);
  ret = drmModeAtomicAddProperty(pset, id(), color_format_property().id(), color_format);
  if (ret < 0) {
    ALOGE("%s:line=%d Failed to add prop[%d] to [%d]", __FUNCTION__, __LINE__, color_format_property().id(), id());
  }

  ALOGI("%s,line=%d %s change hdmi output depth: %d", __FUNCTION__,__LINE__, cUniqueName_, color_depth);
  ret = drmModeAtomicAddProperty(pset, id(), color_depth_property().id(), color_depth);
  if (ret < 0) {
    ALOGE("%s:line=%d Failed to add prop[%d] to [%d]", __FUNCTION__, __LINE__, color_depth_property().id(), id());
  }

  uColorFormat_ = color_format;
  uColorDepth_ = color_depth;

  return 0;
}

int DrmConnector::GetFramebufferInfo(int display_id, uint32_t *w, uint32_t *h, uint32_t *fps) {
  char framebuffer_value[PROPERTY_VALUE_MAX]={0};
  char framebuffer_property[PROPERTY_VALUE_MAX]={0};
  uint32_t width=0, height=0, vrefresh=0;

  snprintf(framebuffer_property,PROPERTY_VALUE_MAX,"persist.vendor.framebuffer.%s",cUniqueName_);
  property_get(framebuffer_property, framebuffer_value, "Unkonw");

  ALOGI("%s,line=%d, display=%d %s=%s",__FUNCTION__,__LINE__,display_id,framebuffer_property,framebuffer_value);

  if(!strcmp(framebuffer_value,"Unkonw")){
    if(display_id == HWC_DISPLAY_PRIMARY){
      property_get("persist.vendor.framebuffer.main", framebuffer_value, "Unkonw");
    }else{
      property_get("persist.vendor.framebuffer.aux", framebuffer_value, "Unkonw");
    }
    ALOGI("%s,line=%d, display=%d persist.vendor.framebuffer.%s=%s",__FUNCTION__,__LINE__,display_id,
          display_id == HWC_DISPLAY_PRIMARY ? "main" : "aux",framebuffer_value);
  }

  if(!strcmp(framebuffer_value,"Unkonw")){
    if(baseparameter_ready_){
      *w = baseparameter_.framebuffer_info.framebuffer_width;
      *h   = baseparameter_.framebuffer_info.framebuffer_height;
      *fps = baseparameter_.framebuffer_info.fps;
    }else{
      *w = 0;
      *h = 0;
      *fps = 0;
    }
  }else{
    sscanf(framebuffer_value, "%dx%d@%d", &width, &height, &vrefresh);
    *w = width;
    *h   = height;
    *fps = vrefresh;
  }
  return 0;
}

const DrmMode &DrmConnector::active_mode() const {
  return active_mode_;
}

const DrmMode &DrmConnector::best_mode() const {
  return best_mode_;
}

const DrmMode &DrmConnector::current_mode() const {
  return current_mode_;
}

void DrmConnector::set_best_mode(const DrmMode &mode) {
  best_mode_ = mode;
}

void DrmConnector::set_active_mode(const DrmMode &mode) {
  active_mode_ = mode;
}

void DrmConnector::set_current_mode(const DrmMode &mode) {
  current_mode_ = mode;
}

void DrmConnector::SetDpmsMode(uint32_t dpms_mode) {
  int ret = drmModeConnectorSetProperty(drm_->fd(), id_, dpms_property_.id(), dpms_mode);
  if (ret) {
    ALOGE("Failed to set dpms mode %d %d", ret, dpms_mode);
    return;
  }
}


bool DrmConnector::isExistMode(const DrmMode &in_mode){
  for(auto&mode : modes_){
    if(mode.equal_no_flag_and_type(in_mode)){
      return true;
    }
  }
  return false;
}

const DrmProperty &DrmConnector::dpms_property() const {
  return dpms_property_;
}

const DrmProperty &DrmConnector::crtc_id_property() const {
  return crtc_id_property_;
}

const DrmProperty &DrmConnector::writeback_pixel_formats() const {
  return writeback_pixel_formats_;
}

const DrmProperty &DrmConnector::writeback_fb_id() const {
  return writeback_fb_id_;
}

const DrmProperty &DrmConnector::writeback_out_fence() const {
  return writeback_out_fence_;
}

DrmEncoder *DrmConnector::encoder() const {
  return encoder_;
}

void DrmConnector::set_encoder(DrmEncoder *encoder) {
  encoder_ = encoder;
}

drmModeConnection DrmConnector::state() {
  return state_;
}

bool DrmConnector::hwc_state_change_and_plug(){
  if(plug_){
    plug_ = false;
    return true;
  }
  return false;
}

HwcConnnectorStete DrmConnector::hwc_state(){
  return hwc_state_;
}

int DrmConnector::set_hwc_state(HwcConnnectorStete state){
  if(state == NORMAL &&
     (hwc_state_ == NO_CRTC ||
      hwc_state_ == RELEASE_CRTC ||
      hwc_state_ == MIRROR_CRTC)){
    plug_ = true;
  }
  hwc_state_ = state;
  return 0;
}

void DrmConnector::update_hotplug_state(){
  std::unique_lock<std::recursive_mutex> lock(mRecursiveMutex);
  hotplug_state_ = state_;
}

drmModeConnection DrmConnector::hotplug_state(){
  std::unique_lock<std::recursive_mutex> lock(mRecursiveMutex);
  return hotplug_state_;
}

uint32_t DrmConnector::mm_width() const {
  return mm_width_;
}

uint32_t DrmConnector::mm_height() const {
  return mm_height_;
}

bool DrmConnector::is_hdmi_support_hdr() const
{
    return (hdr_metadata_property_.id() && bSupportSt2084_) || (hdr_metadata_property_.id() && bSupportHLG_);
}

int DrmConnector::switch_hdmi_hdr_mode(drmModeAtomicReqPtr pset,
                                       android_dataspace_t input_colorspace,
                                       bool is_10bit){
  std::unique_lock<std::recursive_mutex> lock(mRecursiveMutex);
  HWC2_ALOGD_IF_DEBUG("conn-id=%d, isSupportSt2084 = %d, isSupportHLG = %d , colorspace = %x",
                      id(),isSupportSt2084(),isSupportHLG(),input_colorspace);

  if (!pset) {
      ALOGE("%s:line=%d Failed to allocate property set", __FUNCTION__, __LINE__);
      return -1;
  }

  // 释放上一次的 Blob
  if (blob_id_){
      drm_->DestroyPropertyBlob(blob_id_);
      blob_id_ = 0;
  }

  struct hdr_output_metadata hdr_metadata;
  memset(&hdr_metadata, 0, sizeof(struct hdr_output_metadata));

#ifdef ANDROID_S
  hdr_metadata_infoframe &hdmi_metadata_type = hdr_metadata.hdmi_metadata_type1;
#elif ANDROID_P
  hdr_metadata_infoframe &hdmi_metadata_type = hdr_metadata.hdmi_metadata_type1;
#else
  hdr_metadata_infoframe &hdmi_metadata_type = hdr_metadata.hdmi_metadata_type;
#endif

  if((input_colorspace & HAL_DATASPACE_TRANSFER_MASK) == HAL_DATASPACE_TRANSFER_ST2084
          && isSupportSt2084()){
      HWC2_ALOGD_IF_DEBUG("has st2084");
      hdmi_metadata_type.eotf = SMPTE_ST2084;
  }else if((input_colorspace & HAL_DATASPACE_TRANSFER_MASK) == HAL_DATASPACE_TRANSFER_HLG
            && isSupportHLG()){
      HWC2_ALOGD_IF_DEBUG("has HLG");
      hdmi_metadata_type.eotf = HLG;
  }else{
      hdmi_metadata_type.eotf = TRADITIONAL_GAMMA_SDR;
  }

  DrmColorspaceType colorspace = DrmColorspaceType::DEFAULT;
  int ret = -1;
  bool hdr_state_update = false;
  if(hdr_metadata_property().id()){
      HWC2_ALOGD_IF_DEBUG("hdr_metadata eotf=0x%x", hdmi_metadata_type.eotf);
      drm_->CreatePropertyBlob(&hdr_metadata, sizeof(struct hdr_output_metadata), &blob_id_);
      ret = drmModeAtomicAddProperty(pset, id(), hdr_metadata_property().id(), blob_id_);
      if (ret < 0) {
        HWC2_ALOGE("Failed to add prop[%d] to [%d]", hdr_metadata_property().id(), id());
      }
  }

  if(colorspace_property().id()){
      if((input_colorspace & HAL_DATASPACE_STANDARD_BT2020) == HAL_DATASPACE_STANDARD_BT2020){
          // DrmVersion=3 is Kernel 5.10 Support all DrmColorspaceType
          if(drm_->getDrmVersion() == 3){
            if(uColorFormat_ == output_rgb){
              colorspace = DrmColorspaceType::BT2020_RGB;
            } else {
              colorspace = DrmColorspaceType::BT2020_YCC;
            }
          }else{ // Kernel 4.19 only support BT2020_RGB
              colorspace = DrmColorspaceType::BT2020_RGB;
          }
      }

      HWC2_ALOGD_IF_DEBUG("change bt2020 colorspace=%d", colorspace);
      ret = drmModeAtomicAddProperty(pset, id(), colorspace_property().id(), colorspace);
      if (ret < 0) {
        HWC2_ALOGE("Failed to add prop[%d] to [%d]", colorspace_property().id(), id());
      }
  }

  int color_depth = depth_24bit;
  if(hdmi_metadata_type.eotf == TRADITIONAL_GAMMA_SDR){
    UpdateOutputFormat(pset);
  }else{
    color_depth = is_10bit ? depth_30bit : depth_24bit;
    HWC2_ALOGD_IF_DEBUG("change hdmi output format: %d", uColorFormat_);
    ret = drmModeAtomicAddProperty(pset, id(), color_format_property().id(), uColorFormat_);
    if (ret < 0) {
      HWC2_ALOGE("Failed to add prop[%d] to [%d]", color_format_property().id(), id());
    }

    HWC2_ALOGD_IF_DEBUG("change hdmi output depth: %d", color_depth);
    ret = drmModeAtomicAddProperty(pset, id(), color_depth_property().id(), color_depth);
    if (ret < 0) {
      HWC2_ALOGE("Failed to add prop[%d] to [%d]", color_depth_property().id(), id());
    }
  }

  memcpy(&last_hdr_metadata_, &hdr_metadata, sizeof(struct hdr_output_metadata));
  colorspace_ = colorspace;
  uColorDepth_ = color_depth;
  return 0;

}

int DrmConnector::switch_hdmi_hdr_mode_by_medadata(drmModeAtomicReqPtr pset,
                                                   uint32_t color_prim,
                                                   hdr_output_metadata *hdr_metadata,
                                                   bool is_10bit){
  std::unique_lock<std::recursive_mutex> lock(mRecursiveMutex);
  HWC2_ALOGD_IF_DEBUG("conn-id=%d 10bit=%d, isSupportSt2084 = %d, isSupportHLG = %d",
                      id(), is_10bit, isSupportSt2084(), isSupportHLG());

  if (!pset) {
      ALOGE("%s:line=%d Failed to allocate property set", __FUNCTION__, __LINE__);
      return -1;
  }

#ifdef ANDROID_S
  hdr_metadata_infoframe &hdmi_metadata_type = hdr_metadata->hdmi_metadata_type1;
#elif ANDROID_P
  hdr_metadata_infoframe &hdmi_metadata_type = hdr_metadata->hdmi_metadata_type1;
#else
  hdr_metadata_infoframe &hdmi_metadata_type = hdr_metadata->hdmi_metadata_type;
#endif

  HWC2_ALOGD_IF_DEBUG("hdr_metadata: metadata_type=%d",hdr_metadata->metadata_type);
  HWC2_ALOGD_IF_DEBUG("hdr_metadata: color_prim=%x eotf=%d metadata_type=%d \n"
                      "display_primaries[0][x,y]=[%d,%d][%d,%d][%d,%d]\n"
                      "white_point[x,y]=[%d,%d]\n"
                      "max_display_mastering_luminance=%d\n"
                      "min_display_mastering_luminance=%d\n"
                      "max_cll=%d\n"
                      "max_fall=%d\n",
                      color_prim,
                      hdmi_metadata_type.eotf,
                      hdmi_metadata_type.metadata_type,
                      hdmi_metadata_type.display_primaries[0].x,
                      hdmi_metadata_type.display_primaries[0].y,
                      hdmi_metadata_type.display_primaries[1].x,
                      hdmi_metadata_type.display_primaries[1].y,
                      hdmi_metadata_type.display_primaries[2].x,
                      hdmi_metadata_type.display_primaries[2].y,
                      hdmi_metadata_type.white_point.x,
                      hdmi_metadata_type.white_point.y,
                      hdmi_metadata_type.max_display_mastering_luminance,
                      hdmi_metadata_type.min_display_mastering_luminance,
                      hdmi_metadata_type.max_cll,
                      hdmi_metadata_type.max_fall);

#ifdef ANDROID_S
  hdr_metadata_infoframe &last_hdmi_metadata_type = last_hdr_metadata_.hdmi_metadata_type1;
#elif ANDROID_P
  hdr_metadata_infoframe &last_hdmi_metadata_type = last_hdr_metadata_.hdmi_metadata_type1;
#else
  hdr_metadata_infoframe &last_hdmi_metadata_type = last_hdr_metadata_.hdmi_metadata_type;
#endif

  DrmColorspaceType colorspace = DrmColorspaceType::DEFAULT;
  // DrmVersion=3 is Kernel 5.10 Support all DrmColorspaceType
  if(color_prim == COLOR_PRIM_BT2020){
    if(drm_->getDrmVersion() == 3){
      if(uColorFormat_ == output_rgb){
        colorspace = DrmColorspaceType::BT2020_RGB;
      } else {
        colorspace = DrmColorspaceType::BT2020_YCC;
      }
    }else{ // Kernel 4.19 only support BT2020_RGB
        colorspace = DrmColorspaceType::BT2020_RGB;
    }
  }else{
    colorspace = DrmColorspaceType::DEFAULT;
  }

  int color_depth = is_10bit ? depth_30bit : depth_24bit;

  if(last_hdmi_metadata_type.eotf == hdmi_metadata_type.eotf &&
    colorspace_ == colorspace &&
    uColorDepth_ == color_depth &&
    !memcmp(&last_hdr_metadata_, hdr_metadata, sizeof(struct hdr_output_metadata))){
    HWC2_ALOGD_IF_DEBUG("eotf / colorspace / color_depth / hdr_output_metadata is same, skip update.");
    return 0;
  }

  // 释放上一次的 Blob
  if (blob_id_){
      drm_->DestroyPropertyBlob(blob_id_);
      blob_id_ = 0;
  }

  int ret = -1;
  if(hdr_metadata_property().id()){
      HWC2_ALOGD_IF_DEBUG("hdr_metadata eotf=0x%x", hdmi_metadata_type.eotf);
      drm_->CreatePropertyBlob(hdr_metadata, sizeof(struct hdr_output_metadata), &blob_id_);
      ret = drmModeAtomicAddProperty(pset, id(), hdr_metadata_property().id(), blob_id_);
      if (ret < 0) {
        HWC2_ALOGE("Failed to add prop[%d] to [%d]", hdr_metadata_property().id(), id());
      }
  }

  if(colorspace_property().id()){
    HWC2_ALOGD_IF_DEBUG("change %s colorspace=%d",
      ((colorspace == DrmColorspaceType::DEFAULT) ? "bt709" : "bt2020"), colorspace);
    ret = drmModeAtomicAddProperty(pset, id(), colorspace_property().id(), colorspace);
    if (ret < 0) {
      HWC2_ALOGE("Failed to add prop[%d] to [%d]", colorspace_property().id(), id());
    }
  }

  // TODO: SDR 模式不去修改 color depth
  // 2:自动模式: 电视支持 HDR模式播放HDR视频则切换HDR模式，否则使用SDR模式
  // 1:HDR模式: 等同自动模式
  // 0:SDR模式: 电视强制使用SDR模式，HDR片源也采用SDR显示
  if(hwc_get_int_property("persist.sys.vivid.hdr_mode", "2") == 0 || hdmi_metadata_type.eotf == TRADITIONAL_GAMMA_SDR){
    UpdateOutputFormat(pset);
  }else{
    HWC2_ALOGD_IF_DEBUG("change hdmi output format: %d", uColorFormat_);
    ret = drmModeAtomicAddProperty(pset, id(), color_format_property().id(), uColorFormat_);
    if (ret < 0) {
      HWC2_ALOGE("Failed to add prop[%d] to [%d]", color_format_property().id(), id());
    }

    HWC2_ALOGD_IF_DEBUG("change hdmi output depth: %d", color_depth);
    ret = drmModeAtomicAddProperty(pset, id(), color_depth_property().id(), color_depth);
    if (ret < 0) {
      HWC2_ALOGE("Failed to add prop[%d] to [%d]", color_depth_property().id(), id());
    }
  }

  memcpy(&last_hdr_metadata_, hdr_metadata, sizeof(struct hdr_output_metadata));
  colorspace_ = colorspace;
  uColorDepth_ = color_depth;
  return 0;
}


const DrmProperty &DrmConnector::brightness_id_property() const {
  return brightness_id_property_;
}
const DrmProperty &DrmConnector::contrast_id_property() const {
  return contrast_id_property_;
}
const DrmProperty &DrmConnector::saturation_id_property() const {
  return saturation_id_property_;
}
const DrmProperty &DrmConnector::hue_id_property() const {
  return hue_id_property_;
}

const DrmProperty &DrmConnector::hdr_metadata_property() const {
  return hdr_metadata_property_;
}

const DrmProperty &DrmConnector::hdr_panel_property() const {
  return hdr_panel_property_;
}

const DrmProperty &DrmConnector::colorspace_property() const {
  return colorspace_property_;
}

const DrmProperty &DrmConnector::color_format_property() const {
  return color_format_property_;
}

const DrmProperty &DrmConnector::color_depth_property() const {
  return color_depth_property_;
}

int DrmConnector::GetSpiltModeId() const {
  return (display_ + DRM_CONNECTOR_SPILT_MODE_MASK);
}

bool DrmConnector::isHorizontalSpilt() const {
  return bHorizontalSpilt_;
}

int DrmConnector::setHorizontalSpilt(){
  bHorizontalSpilt_ = true;
  return 0;
}

bool DrmConnector::isCropSpilt() const {
  return bCropSpilt_;
}

int DrmConnector::setCropSpiltPrimary(){
  bSpiltPrimary_ = true;
  return 0;
}

bool DrmConnector::IsSpiltPrimary(){
  return bSpiltPrimary_;
}

int DrmConnector::setCropSpilt(int32_t fbWidth,
                               int32_t fbHeight,
                               int32_t srcX,
                               int32_t srcY,
                               int32_t srcW,
                               int32_t srcH){
  bCropSpilt_ = true;
  FbWidth_ = fbWidth;
  FbHeight_ = fbHeight;
  SrcX_ = srcX;
  SrcY_ = srcY;
  SrcW_ = srcW;
  SrcH_ = srcH;
  return 0;
}

int DrmConnector::getCropSpiltFb(int32_t *fbWidth, int32_t *fbHeight){
  *fbWidth = FbWidth_;
  *fbHeight = FbHeight_;
  return 0;
}

int DrmConnector::getCropInfo(int32_t *srcX, int32_t *srcY, int32_t *srcW, int32_t *srcH){
  *srcX = SrcX_;
  *srcY = SrcY_;
  *srcW = SrcW_;
  *srcH = SrcH_;
  return 0;
}

}  // namespace android
