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

#include "platform.h"
#include "rockchip/platform/drmhwc3528.h"
#include "drmdevice.h"

#include <log/log.h>

namespace android {

#define ARRAY_SIZE(arr) (sizeof(arr) / sizeof((arr)[0]))

void Hwc3528::Init(){
}

bool Hwc3528::SupportPlatform(uint32_t soc_id){
  switch(soc_id){
    case 0x3528:
      return true;
    default:
      break;
  }
  return false;
}

struct assign_plane_group_3528{
	int display_type;
  uint64_t drm_type_mask;
  bool have_assigin;
};
struct assign_plane_group_3528 assign_mask_default_3528[] = {
  { DRM_MODE_CONNECTOR_HDMIA , PLANE_RK3528_ALL_CLUSTER0_MASK |
                               PLANE_RK3528_ALL_ESMART0_MASK  |
                               PLANE_RK3528_ALL_ESMART1_MASK  |
                               PLANE_RK3528_ALL_ESMART2_MASK  , false},
  { DRM_MODE_CONNECTOR_TV , PLANE_RK3528_ALL_ESMART2_MASK |
                            PLANE_RK3528_ALL_ESMART3_MASK, false},
};

int Hwc3528::assignPlaneByHWC(DrmDevice* drm){
  HWC2_ALOGW("Crtc PlaneMask not set, have to use HwcPlaneMask, please check Crtc::PlaneMask info.");
  std::vector<PlaneGroup*> all_plane_group = drm->GetPlaneGroups();
  for (auto &conn : drm->connectors()) {
    int display_id = conn->display();
    if(conn->state() != DRM_MODE_CONNECTED){
      HWC2_ALOGE("display=%d conn state() is disconnect.", display_id);
      continue;
    }

    DrmCrtc *crtc = drm->GetCrtcForDisplay(display_id);
    if(!crtc){
        HWC2_ALOGE("display=%d crtc is NULL.", display_id);
        continue;
    }

    uint64_t plane_mask=0;
    for(int i = 0; i < ARRAY_SIZE(assign_mask_default_3528);i++){
      if(conn->type() == assign_mask_default_3528[i].display_type){
        plane_mask = assign_mask_default_3528[i].drm_type_mask;
        break;
      }
    }

    uint32_t crtc_mask = 1 << crtc->pipe();
    ALOGI_IF(DBG_INFO,"%s,line=%d, crtc-id=%d mask=0x%x ,plane_mask=0x%" PRIx64 ,__FUNCTION__,__LINE__,
             crtc->id(),crtc_mask,plane_mask);
    for(auto &plane_group : all_plane_group){
      uint64_t plane_group_win_type = plane_group->win_type;
      if((plane_mask & plane_group_win_type) == plane_group_win_type){
        plane_group->set_current_crtc(crtc_mask, display_id);
      }
    }
  }

  for(auto &plane_group : all_plane_group){
    if((plane_group->win_type & PLANE_RK3528_ALL_ESMART2_MASK) > 0){
      // RK3528 图层切换后需要延迟使用
      plane_group->delay_use_cnt = 16;
    }
    ALOGI_IF(DBG_INFO,"%s,line=%d, name=%s cur_crtcs_mask=0x%x delay_use_cnt=%d",__FUNCTION__,__LINE__,
             plane_group->planes[0]->name(),
             plane_group->current_crtc_,
             plane_group->delay_use_cnt);
  }
  return 0;
}

int Hwc3528::assignPlaneByPlaneMask(DrmDevice* drm){
  std::vector<PlaneGroup*> all_plane_group = drm->GetPlaneGroups();
  for (auto &conn : drm->connectors()) {
    int display_id = conn->display();
    if(conn->state() != DRM_MODE_CONNECTED){
      HWC2_ALOGE("display=%d conn state() is disconnect.", display_id);
      continue;
    }
    DrmCrtc *crtc = drm->GetCrtcForDisplay(display_id);
    if(!crtc){
        HWC2_ALOGE("display=%d crtc is NULL.", display_id);
        continue;
    }

    uint32_t crtc_mask = 1 << crtc->pipe();
    uint64_t plane_mask = crtc->get_plane_mask();
    HWC2_ALOGI("display-id=%d crtc-id=%d mask=0x%x ,plane_mask=0x%" PRIx64,
            display_id, crtc->id(), crtc_mask, plane_mask);
    for(auto &plane_group : all_plane_group){
      uint64_t plane_group_win_type = plane_group->win_type;
      if(((plane_mask & plane_group_win_type) == plane_group_win_type)){
        plane_group->set_current_crtc(crtc_mask, display_id & 0xf);
      }
    }
  }

  for(auto &plane_group : all_plane_group){
    HWC2_ALOGI("name=%s cur_crtcs_mask=0x%x possible-display=%" PRIi64,
            plane_group->planes[0]->name(),plane_group->current_crtc_,plane_group->possible_display_);

  }
  return 0;
}

int Hwc3528::TryAssignPlane(DrmDevice* drm){
  int ret = -1;
  bool exist_plane_mask = false;

  for (auto &conn : drm->connectors()) {
    int display_id = conn->display();
    if(conn->state() != DRM_MODE_CONNECTED)
      continue;
    DrmCrtc *crtc = drm->GetCrtcForDisplay(display_id);
    if(!crtc){
      HWC2_ALOGE("display %d crtc is NULL.", display_id);
      continue;
    }
    // Exist PlaneMask
    if(crtc->get_plane_mask() > 0){
      exist_plane_mask = true;
    }
  }

  if(exist_plane_mask){
    ret = assignPlaneByPlaneMask(drm);
  }else{
    ret = assignPlaneByHWC(drm);
  }

  return ret;
}
}

