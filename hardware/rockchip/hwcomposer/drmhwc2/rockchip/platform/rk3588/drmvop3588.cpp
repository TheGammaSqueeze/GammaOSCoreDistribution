/*
 * Copyright (C) 2020 Rockchip Electronics Co.Ltd.
 *
 * Modification based on code covered by the Apache License, Version 2.0 (the "License").
 * You may not use this software except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS TO YOU ON AN "AS IS" BASIS
 * AND ANY AND ALL WARRANTIES AND REPRESENTATIONS WITH RESPECT TO SUCH SOFTWARE, WHETHER EXPRESS,
 * IMPLIED, STATUTORY OR OTHERWISE, INCLUDING WITHOUT LIMITATION, ANY IMPLIED WARRANTIES OF TITLE,
 * NON-INFRINGEMENT, MERCHANTABILITY, SATISFACTROY QUALITY, ACCURACY OR FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.
 *
 * IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
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
#define ATRACE_TAG ATRACE_TAG_GRAPHICS
#define LOG_TAG "drm-vop-3588"

#include "rockchip/platform/drmvop3588.h"
#include "drmdevice.h"

#include "im2d.hpp"

#include <drm_fourcc.h>
#include <log/log.h>

//XML prase
#include <tinyxml2.h>

using namespace android;
#define ALIGN_DOWN( value, base)	(value & (~(base-1)) )
#ifndef ALIGN
#define ALIGN( value, base ) (((value) + ((base) - 1)) & ~((base) - 1))
#endif

void Vop3588::Init(){

  ctx.state.bMultiAreaEnable = hwc_get_bool_property("vendor.hwc.multi_area_enable","true");

  ctx.state.bMultiAreaScaleEnable = hwc_get_bool_property("vendor.hwc.multi_area_scale_mode","true");

  ctx.state.bRgaPolicyEnable = hwc_get_int_property("vendor.hwc.enable_rga_policy","1") > 0;

  ctx.state.iVopMaxOverlay4KPlane = hwc_get_int_property("vendor.hwc.vop_max_overlay_4k_plane","0");

  memset(ctx.state.accelerate_app_name, 0x00, sizeof(ctx.state.accelerate_app_name));
  hwc_get_string_property("vendor.hwc.accelerate_app_name",
                          "rk_handwrite_sf",
                          ctx.state.accelerate_app_name);
  // SVEP 配置参数初始化
  InitSvep();
}
int Vop3588::InitSvep(){

#ifdef USE_LIBSR
  InitSvepSrEnv();
#endif

#ifdef USE_LIBSVEP_MEMC
  InitSvepMemcEnv();
#endif
  return 0;
}

#ifdef USE_LIBSR
int Vop3588::InitSvepSrEnv(){
  if(mSrEnv_.mValid)
    return 0;

  char xml_path[PROPERTY_VALUE_MAX];
  property_get("vendor.hwc.svep_xml_path", xml_path, "/vendor/etc/HwcSvepEnv.xml");

  tinyxml2::XMLDocument doc;
  int ret = doc.LoadFile(xml_path);
  if(ret){
    HWC2_ALOGW("Can't find %s file. ret=%d", xml_path, ret);
    return -1;
  }

  HWC2_ALOGI("Load %s success.", xml_path);

  tinyxml2::XMLElement* HwcSvepEnv = doc.RootElement();
  /* usr tingxml2 to parse resolution.xml */
  if (!HwcSvepEnv){
    HWC2_ALOGW("Can't %s:RootElement fail.", xml_path);
    return -1;
  }

  mSrEnv_.mSvepWhitelist_.clear();
  mSrEnv_.mSvepBlacklist_.clear();

  const char* verison = "1.1.1";
  ret = HwcSvepEnv->QueryStringAttribute( "Version", &verison);
  if(ret){
    HWC2_ALOGW("Can't find %s verison info. ret=%d", xml_path, ret);
    return -1;
  }

  sscanf(verison, "%d.%d.%d", &mSrEnv_.mVersion.Major,
                              &mSrEnv_.mVersion.Minor,
                              &mSrEnv_.mVersion.PatchLevel);


  tinyxml2::XMLElement* pWhitelist = HwcSvepEnv->FirstChildElement("Whitelist");
  if (!pWhitelist){
    HWC2_ALOGW("Can't %s:Whitelist fail. Maybe not set.", xml_path);
  }else{
    int iLayerNameCnt = 0;
    tinyxml2::XMLElement* pWhiteKey = pWhitelist->FirstChildElement("WhiteKeywords");
    if (!pWhiteKey) {
      HWC2_ALOGW("index=%d failed to parse %s\n", iLayerNameCnt, "WhiteKeywords"); \
    }else{
      while (pWhiteKey) {
        mSrEnv_.mSvepWhitelist_.emplace_back(pWhiteKey->GetText());
        HWC2_ALOGI("SR Whitelist[%d]=%s",
                    iLayerNameCnt, mSrEnv_.mSvepWhitelist_[iLayerNameCnt].c_str());
        iLayerNameCnt++;
        pWhiteKey = pWhiteKey->NextSiblingElement();
      }
    }
  }

  tinyxml2::XMLElement* pBlacklist = HwcSvepEnv->FirstChildElement("Blacklist");
  if (!pBlacklist){
    HWC2_ALOGW("Can't %s:Blacklist fail. Maybe not set.", xml_path);
  }else{
    int iLayerNameCnt = 0;
    tinyxml2::XMLElement* pBlackKey = pBlacklist->FirstChildElement("BlackKeywords");
    if (!pBlackKey) {
      HWC2_ALOGW("index=%d failed to parse %s\n", iLayerNameCnt, "BlackKeywords");
    }else{
      while (pBlackKey) {
        mSrEnv_.mSvepBlacklist_.emplace_back(pBlackKey->GetText());

        HWC2_ALOGI("SR Blacklist[%d]=%s",
                    iLayerNameCnt, mSrEnv_.mSvepBlacklist_[iLayerNameCnt].c_str());
        iLayerNameCnt++;
        pBlackKey = pBlackKey->NextSiblingElement();
      }
    }
  }

  mSrEnv_.mValid = true;
  return 0;
}

bool Vop3588::SvepSrAllowedByBlacklist(DrmHwcLayer* layer){
  if(mSrEnv_.mValid){
    // 此黑名单内的应用名不参与 SR 处理
    for(auto &black_key : mSrEnv_.mSvepBlacklist_){
      if(layer->sLayerName_.find(black_key) != std::string::npos){
        HWC2_ALOGD_IF_DEBUG("Sr %s in BlackList! not to SR.", layer->sLayerName_.c_str());
        return false;
      }
    }
  }
  return true;
}

bool Vop3588::SvepSrAllowedByWhitelist(DrmHwcLayer* layer){
  if(mSrEnv_.mValid){
    // 此白名单内的应用名直接参与 SR 处理
    for(auto &white_key : mSrEnv_.mSvepWhitelist_){
      if(layer->sLayerName_.find(white_key) != std::string::npos){
        HWC2_ALOGD_IF_DEBUG("Sr %s in Whitelist! force to SR.", layer->sLayerName_.c_str());
        if(mSrEnv_.mSvepWhitelistUid_.size() > 3){
          mSrEnv_.mSvepWhitelistUid_.clear();
        }
        // 使用LayerId主要因为在部分场景，LayerName可能会发生变化，例如：
        // 视频解码LayerName可能为：
        //  SurfaceView[com.youdao.hw.videoplayer..
        //  SurfaceTexture-1-6467-0..
        // 测试过程发现，LayerId是没有变化的，故可以通过LayerId来找到白名单图层
        mSrEnv_.mSvepWhitelistUid_.insert(layer->uId_);
        return true;
      }
    }
  }
  if(mSrEnv_.mSvepWhitelistUid_.count(layer->uId_) > 0){
        HWC2_ALOGD_IF_DEBUG("Sr uid=%d is %s in Whitelist! force to SR.", layer->uId_, layer->sLayerName_.c_str());
        return true;
  }
  return false;
}

#define SVEP_SUPPORT_MAX_FPS 45
bool Vop3588::SvepSrAllowedByLocalPolicy(DrmHwcLayer* layer){
  // 视频大于4K则不使用 SR.
  if(layer->iWidth_ > 4096){
    HWC2_ALOGD_IF_DEBUG("disable-sr: intput too big, input-info (%d,%d) name=%s",
                        layer->iWidth_,
                        layer->iHeight_,
                        layer->sLayerName_.c_str());
    return false;
  }

  // 如果不是视频格式，并且不在白名单内，则不使用SR
  if(!layer->bYuv_ && !SvepSrAllowedByWhitelist(layer)){
    HWC2_ALOGD_IF_DEBUG("disable-sr: %s-YUV, can't find in Whitelist name=%s",
                        (layer->bYuv_ ? "Is" : "Not"),
                        layer->sLayerName_.c_str());
    return false;
  }

  // 如果SurfaceFlinger 请求Client合成，则不采用SR策略
  // 例如高斯模糊效果
  if(layer->sf_composition == HWC2::Composition::Client){
    HWC2_ALOGD_IF_DEBUG("disable-sr: SF request Client, name=%s",
                        layer->sLayerName_.c_str());
    return false;
  }

  bool yuv_10bit = false;
  switch(layer->iFormat_){
    case HAL_PIXEL_FORMAT_YCrCb_NV12_10 :
    case HAL_PIXEL_FORMAT_YCbCr_422_SP_10 :
    case HAL_PIXEL_FORMAT_YCrCb_420_SP_10 :
    case HAL_PIXEL_FORMAT_YUV420_10BIT_I :
      yuv_10bit = true;
      break;
    default:
      yuv_10bit = false;
      break;
  }

  // 10bit 视频不使用SR
  if(yuv_10bit){
    HWC2_ALOGD_IF_DEBUG("disable-sr: is 10bit YUV, SR unsupport, name=%s",
                        layer->sLayerName_.c_str());
    return false;
  }

  // SR 不支持所有YUV planer变种格式
  // bool unsupport_yuv_p = false;
  // switch(layer->uFourccFormat_){
  //   case DRM_FORMAT_YUV420:
  //   case DRM_FORMAT_YVU420:
  //   case DRM_FORMAT_YUV422:
  //   case DRM_FORMAT_YVU422:
  //   case DRM_FORMAT_YUV444:
  //   case DRM_FORMAT_YVU444:
  //     unsupport_yuv_p = true;
  //     break;
  //   default:
  //     break;
  // }

  // if(unsupport_yuv_p){
  //   HWC2_ALOGD_IF_DEBUG("disable-sr: unsupport yuv p format. fourcc=%x", layer->uFourccFormat_);
  //   return false;
  // }

  // 如果图层本身就是2倍缩小的场景，则不建议使用SR.
  if(layer->fHScaleMul_ > 2.0 && layer->fVScaleMul_ > 2.0){
    HWC2_ALOGD_IF_DEBUG("disable-sr: scale-rate is too big fHScaleMul_=%f fVScaleMul_=%f SR unsupport, name=%s",
                        layer->fHScaleMul_,
                        layer->fVScaleMul_,
                        layer->sLayerName_.c_str());
    return false;
  }

  // 开机动画不使用超分
  char value[PROPERTY_VALUE_MAX];
  property_get("service.bootanim.exit", value, "0");
  if(atoi(value) == 0){
    HWC2_ALOGD_IF_DEBUG("disable-sr: during bootanim disable SR, name=%s",
                        layer->sLayerName_.c_str());
    return false;
  }

  // // 视频屏幕占比60%以下不使用SR
  uint64_t allow_rate = hwc_get_int_property("vendor.hwc.disable_svep_dis_area_rate","60");
  uint64_t dis_w = layer->display_frame.right - layer->display_frame.left;
  uint64_t dis_h = layer->display_frame.bottom - layer->display_frame.top;
  uint64_t dis_area_size = dis_w * dis_h;
  uint64_t screen_size = ctx.state.iDisplayWidth_ * ctx.state.iDisplayHeight_;
  // 100 表示屏占比 100%，
  uint64_t video_area_rate = dis_area_size * 100 / screen_size;
  if(video_area_rate < allow_rate){
    HWC2_ALOGD_IF_DEBUG("disable-sr: video_area_rate=%" PRIu64 "%% name=%s",video_area_rate, layer->sLayerName_.c_str());
    return false;
  }

  // HWC内部会计算图层刷新率，若刷新率大于35帧，则关闭SR-SR功能
  if(layer->fRealMaxFps_ > SVEP_SUPPORT_MAX_FPS){
    HWC2_ALOGD_IF_DEBUG("disable-sr: video_max_fps=%d name=%s",layer->fRealMaxFps_, layer->sLayerName_.c_str());
    return false;
  }

  return true;
}
#endif

#ifdef USE_LIBSVEP_MEMC
int Vop3588::InitSvepMemcEnv(){
  if(mMemcEnv_.mValid)
    return 0;

  char xml_path[PROPERTY_VALUE_MAX];
  property_get("vendor.hwc.svep_memc_xml_path", xml_path, "/vendor/etc/HwcSvepMemcEnv.xml");

  tinyxml2::XMLDocument doc;
  int ret = doc.LoadFile(xml_path);
  if(ret){
    HWC2_ALOGW("Can't find %s file. ret=%d", xml_path, ret);
    return -1;
  }

  HWC2_ALOGI("Load %s success.", xml_path);

  tinyxml2::XMLElement* HwcSvepEnv = doc.RootElement();
  /* usr tingxml2 to parse resolution.xml */
  if (!HwcSvepEnv){
    HWC2_ALOGW("Can't %s:RootElement fail.", xml_path);
    return -1;
  }

  mMemcEnv_.mSvepWhitelist_.clear();
  mMemcEnv_.mSvepBlacklist_.clear();

  const char* verison = "1.1.1";
  ret = HwcSvepEnv->QueryStringAttribute( "Version", &verison);
  if(ret){
    HWC2_ALOGW("Can't find %s verison info. ret=%d", xml_path, ret);
    return -1;
  }

  sscanf(verison, "%d.%d.%d", &mMemcEnv_.mVersion.Major,
                              &mMemcEnv_.mVersion.Minor,
                              &mMemcEnv_.mVersion.PatchLevel);


  tinyxml2::XMLElement* pWhitelist = HwcSvepEnv->FirstChildElement("Whitelist");
  if (!pWhitelist){
    HWC2_ALOGW("Can't %s:Whitelist fail. Maybe not set.", xml_path);
  }else{
    int iLayerNameCnt = 0;
    tinyxml2::XMLElement* pWhiteKey = pWhitelist->FirstChildElement("WhiteKeywords");
    if (!pWhiteKey) {
      HWC2_ALOGW("index=%d failed to parse %s\n", iLayerNameCnt, "WhiteKeywords"); \
    }else{
      while (pWhiteKey) {
        mMemcEnv_.mSvepWhitelist_.emplace_back(pWhiteKey->GetText());
        HWC2_ALOGI("MEMC Whitelist[%d]=%s",
                    iLayerNameCnt, mMemcEnv_.mSvepWhitelist_[iLayerNameCnt].c_str());
        iLayerNameCnt++;
        pWhiteKey = pWhiteKey->NextSiblingElement();
      }
    }
  }

  tinyxml2::XMLElement* pBlacklist = HwcSvepEnv->FirstChildElement("Blacklist");
  if (!pBlacklist){
    HWC2_ALOGW("Can't %s:Blacklist fail. Maybe not set.", xml_path);
  }else{
    int iLayerNameCnt = 0;
    tinyxml2::XMLElement* pBlackKey = pBlacklist->FirstChildElement("BlackKeywords");
    if (!pBlackKey) {
      HWC2_ALOGW("index=%d failed to parse %s\n", iLayerNameCnt, "BlackKeywords");
    }else{
      while (pBlackKey) {
        mMemcEnv_.mSvepBlacklist_.emplace_back(pBlackKey->GetText());

        HWC2_ALOGI("MEMC Blacklist[%d]=%s",
                    iLayerNameCnt, mMemcEnv_.mSvepBlacklist_[iLayerNameCnt].c_str());
        iLayerNameCnt++;
        pBlackKey = pBlackKey->NextSiblingElement();
      }
    }
  }

  mMemcEnv_.mValid = true;
  return 0;
}

bool Vop3588::SvepMemcAllowedByBlacklist(DrmHwcLayer* layer){
  if(mMemcEnv_.mValid){
    // 此黑名单内的应用名不参与 SR 处理
    for(auto &black_key : mMemcEnv_.mSvepBlacklist_){
      if(layer->sLayerName_.find(black_key) != std::string::npos){
        HWC2_ALOGD_IF_DEBUG("Sr %s in BlackList! not to SR.", layer->sLayerName_.c_str());
        return false;
      }
    }
  }
  return true;
}

bool Vop3588::SvepMemcAllowedByWhitelist(DrmHwcLayer* layer){
  if(mMemcEnv_.mValid){
    // 此白名单内的应用名直接参与 SR 处理
    for(auto &white_key : mMemcEnv_.mSvepWhitelist_){
      if(layer->sLayerName_.find(white_key) != std::string::npos){
        HWC2_ALOGD_IF_DEBUG("Sr %s in Whitelist! force to SR.", layer->sLayerName_.c_str());
        return true;
      }
    }
  }
  return false;
}

#define SVEP_MEMC_SUPPORT_MAX_FPS 40
bool Vop3588::SvepMemcAllowedByLocalPolicy(DrmHwcLayer* layer){
  // 视频大于4K则不使用 SR.
  if(layer->iWidth_ > 4096)
    return false;

  // 如果不是视频格式，并且不在白名单内，则不使用SR
  if(!layer->bYuv_ && !SvepMemcAllowedByWhitelist(layer))
    return false;

  bool yuv_10bit = false;
  switch(layer->iFormat_){
    case HAL_PIXEL_FORMAT_YCrCb_NV12_10 :
    case HAL_PIXEL_FORMAT_YCbCr_422_SP_10 :
    case HAL_PIXEL_FORMAT_YCrCb_420_SP_10 :
    case HAL_PIXEL_FORMAT_YUV420_10BIT_I :
      yuv_10bit = true;
      break;
    default:
      yuv_10bit = false;
      break;
  }

  // 10bit 视频不使用SR
  if(yuv_10bit){
    return false;
  }

  // MEMC 不支持所有YUV planer变种格式
  // bool unsupport_yuv_p = false;
  // switch(layer->uFourccFormat_){
  //   case DRM_FORMAT_YUV420:
  //   case DRM_FORMAT_YVU420:
  //   case DRM_FORMAT_YUV422:
  //   case DRM_FORMAT_YVU422:
  //   case DRM_FORMAT_YUV444:
  //   case DRM_FORMAT_YVU444:
  //     unsupport_yuv_p = true;
  //     break;
  //   default:
  //     break;
  // }

  // if(unsupport_yuv_p){
  //   HWC2_ALOGD_IF_DEBUG("disable-memc: unsupport yuv p format. fourcc=%x", layer->uFourccFormat_);
  //   return false;
  // }

  // 开机动画不使用超分
  char value[PROPERTY_VALUE_MAX];
  property_get("service.bootanim.exit", value, "0");
  if(atoi(value) == 0){
    return false;
  }

  // HWC内部会计算图层刷新率，若刷新率大于40帧，则关闭SR-MEMC功能
  if(layer->fRealMaxFps_ > SVEP_MEMC_SUPPORT_MAX_FPS){
    HWC2_ALOGD_IF_DEBUG("disable-memc: video_max_fps=%d name=%s",layer->fRealMaxFps_, layer->sLayerName_.c_str());
    return false;
  }

  return true;
}
#endif

bool Vop3588::SupportPlatform(uint32_t soc_id){
  switch(soc_id){
    case 0x3588:
      return true;
    default:
      break;
  }
  return false;
}

int Vop3588::TryHwcPolicy(
    std::vector<DrmCompositionPlane> *composition,
    std::vector<DrmHwcLayer*> &layers,
    std::vector<PlaneGroup *> &plane_groups,
    DrmCrtc *crtc,
    bool gles_policy) {
  int ret;
  // Get PlaneGroup
  if(plane_groups.size()==0){
    ALOGE("%s,line=%d can't get plane_groups size=%zu",__FUNCTION__,__LINE__,plane_groups.size());
    return -1;
  }

  // Init context
  InitContext(layers,plane_groups,crtc,gles_policy);

#if (defined USE_LIBSR) || (defined USE_LIBSVEP_MEMC)
  // Try to match rga policy
  if(ctx.state.setHwcPolicy.count(HWC_SR_OVERLAY_LOPICY)){
    ret = TrySvepPolicy(composition,layers,crtc,plane_groups);
    if(!ret){
      return 0;
    }else{
      ALOGD_IF(LogLevel(DBG_DEBUG),"Match rga policy fail, try to match other policy.");

#ifdef USE_LIBSR
      mLastMode_ = SrMode::UN_SUPPORT;
#endif
    }
  }
#endif

  // Try to match overlay policy
  if(ctx.state.setHwcPolicy.count(HWC_OVERLAY_LOPICY)){
    ret = TryOverlayPolicy(composition,layers,crtc,plane_groups);
    if(!ret)
      return 0;
    else{
      ALOGD_IF(LogLevel(DBG_DEBUG),"Match overlay policy fail, try to match other policy.");
      TryMix();
    }
  }

  // Try to match GLES Accelerate policy
  if(ctx.state.setHwcPolicy.count(HWC_ACCELERATE_LOPICY)){
    ret = TryAcceleratePolicy(composition,layers,crtc,plane_groups);
    if(!ret)
      return 0;
  }

  // Try to match mix policy
  if(ctx.state.setHwcPolicy.count(HWC_MIX_LOPICY)){
    ret = TryMixPolicy(composition,layers,crtc,plane_groups);
    if(!ret)
      return 0;
    else{
      ALOGD_IF(LogLevel(DBG_DEBUG),"Match mix policy fail, try to match other policy.");
      ctx.state.setHwcPolicy.insert(HWC_GLES_POLICY);
    }
  }

  // Try to match GLES policy
  if(ctx.state.setHwcPolicy.count(HWC_GLES_SIDEBAND_LOPICY)){
    ret = TryGlesSidebandPolicy(composition,layers,crtc,plane_groups);
    if(!ret)
      return 0;
  }


  // Try to match GLES policy
  if(ctx.state.setHwcPolicy.count(HWC_GLES_POLICY)){
    ret = TryGLESPolicy(composition,layers,crtc,plane_groups);
    if(!ret)
      return 0;
  }

  ALOGE("%s,%d Can't match HWC policy",__FUNCTION__,__LINE__);
  return -1;
}

bool Vop3588::HasLayer(std::vector<DrmHwcLayer*>& layer_vector,DrmHwcLayer *layer){
        for (std::vector<DrmHwcLayer*>::const_iterator iter = layer_vector.begin();
               iter != layer_vector.end(); ++iter) {
            if((*iter)->uId_==layer->uId_)
                return true;
          }

          return false;
}

int Vop3588::IsXIntersect(hwc_rect_t* rec,hwc_rect_t* rec2){
    if(rec2->top == rec->top)
        return 1;
    else if(rec2->top < rec->top)
    {
        if(rec2->bottom > rec->top)
            return 1;
        else
            return 0;
    }
    else
    {
        if(rec->bottom > rec2->top  )
            return 1;
        else
            return 0;
    }
    return 0;
}


bool Vop3588::IsRec1IntersectRec2(hwc_rect_t* rec1, hwc_rect_t* rec2){
    int iMaxLeft,iMaxTop,iMinRight,iMinBottom;
    HWC2_ALOGD_IF_VERBOSE("is_not_intersect: rec1[%d,%d,%d,%d],rec2[%d,%d,%d,%d]",rec1->left,rec1->top,
        rec1->right,rec1->bottom,rec2->left,rec2->top,rec2->right,rec2->bottom);

    iMaxLeft = rec1->left > rec2->left ? rec1->left: rec2->left;
    iMaxTop = rec1->top > rec2->top ? rec1->top: rec2->top;
    iMinRight = rec1->right <= rec2->right ? rec1->right: rec2->right;
    iMinBottom = rec1->bottom <= rec2->bottom ? rec1->bottom: rec2->bottom;

    if(iMaxLeft > iMinRight || iMaxTop > iMinBottom)
        return false;
    else
        return true;

    return false;
}

bool Vop3588::IsLayerCombine(DrmHwcLayer * layer_one,DrmHwcLayer * layer_two){
    if(!ctx.state.bMultiAreaEnable)
      return false;

    // 8K display mode must to disable MultilArea Mode.
    if(ctx.state.b8kMode_)
      return false;

    //multi region only support RGBA888 RGBX8888 RGB888 565 BGRA888 NV12
    if(layer_one->iFormat_ >= HAL_PIXEL_FORMAT_YCrCb_NV12_10
        || layer_two->iFormat_ >= HAL_PIXEL_FORMAT_YCrCb_NV12_10
        || (layer_one->iFormat_ != layer_two->iFormat_)
        || (layer_one->bAfbcd_ != layer_two->bAfbcd_)
        || layer_one->alpha!= layer_two->alpha
        || ((layer_one->bScale_ || layer_two->bScale_) && !ctx.state.bMultiAreaScaleEnable)
        || IsRec1IntersectRec2(&layer_one->display_frame,&layer_two->display_frame)
        || IsXIntersect(&layer_one->display_frame,&layer_two->display_frame)
        )
    {
        HWC2_ALOGD_IF_VERBOSE("is_layer_combine layer one alpha=%d,is_scale=%d",layer_one->alpha,layer_one->bScale_);
        HWC2_ALOGD_IF_VERBOSE("is_layer_combine layer two alpha=%d,is_scale=%d",layer_two->alpha,layer_two->bScale_);
        return false;
    }

    return true;
}

int Vop3588::CombineLayer(LayerMap& layer_map,std::vector<DrmHwcLayer*> &layers,uint32_t iPlaneSize){

    /*Group layer*/
    int zpos = 0;
    size_t i,j;
    uint32_t sort_cnt=0;
    bool is_combine = false;

    layer_map.clear();

    for (i = 0; i < layers.size(); ) {
        if(!layers[i]->bUse_)
            continue;

        sort_cnt=0;
        if(i == 0)
        {
            layer_map[zpos].push_back(layers[0]);
        }

        for(j = i+1; j < layers.size(); j++) {
            DrmHwcLayer *layer_one = layers[j];
            //layer_one.index = j;
            is_combine = false;

            for(size_t k = 0; k <= sort_cnt; k++ ) {
                DrmHwcLayer *layer_two = layers[j-1-k];
                //layer_two.index = j-1-k;
                //juage the layer is contained in layer_vector
                bool bHasLayerOne = HasLayer(layer_map[zpos],layer_one);
                bool bHasLayerTwo = HasLayer(layer_map[zpos],layer_two);

                //If it contain both of layers,then don't need to go down.
                if(bHasLayerOne && bHasLayerTwo)
                    continue;

                if(IsLayerCombine(layer_one,layer_two)) {
                    //append layer into layer_vector of layer_map_.
                    if(!bHasLayerOne && !bHasLayerTwo)
                    {
                        layer_map[zpos].emplace_back(layer_one);
                        layer_map[zpos].emplace_back(layer_two);
                        is_combine = true;
                    }
                    else if(!bHasLayerTwo)
                    {
                        is_combine = true;
                        for(std::vector<DrmHwcLayer*>::const_iterator iter= layer_map[zpos].begin();
                            iter != layer_map[zpos].end();++iter)
                        {
                            if((*iter)->uId_==layer_one->uId_)
                                    continue;

                            if(!IsLayerCombine(*iter,layer_two))
                            {
                                is_combine = false;
                                break;
                            }
                        }

                        if(is_combine)
                            layer_map[zpos].emplace_back(layer_two);
                    }
                    else if(!bHasLayerOne)
                    {
                        is_combine = true;
                        for(std::vector<DrmHwcLayer*>::const_iterator iter= layer_map[zpos].begin();
                            iter != layer_map[zpos].end();++iter)
                        {
                            if((*iter)->uId_==layer_two->uId_)
                                    continue;

                            if(!IsLayerCombine(*iter,layer_one))
                            {
                                is_combine = false;
                                break;
                            }
                        }

                        if(is_combine)
                            layer_map[zpos].emplace_back(layer_one);
                    }
                }

                if(!is_combine)
                {
                    //if it cann't combine two layer,it need start a new group.
                    if(!bHasLayerOne)
                    {
                        zpos++;
                        layer_map[zpos].emplace_back(layer_one);
                    }
                    is_combine = false;
                    break;
                }
             }
             sort_cnt++; //update sort layer count
             if(!is_combine)
             {
                break;
             }
        }

        if(is_combine)  //all remain layer or limit MOST_WIN_ZONES layer is combine well,it need start a new group.
            zpos++;
        if(sort_cnt)
            i+=sort_cnt;    //jump the sort compare layers.
        else
            i++;
    }

  // RK3588 sort layer by ypos
  for (LayerMap::iterator iter = layer_map.begin();
       iter != layer_map.end(); ++iter) {
        if(iter->second.size() > 1) {
            for(i = 0; i < iter->second.size()-1; i++) {
                for(j = i + 1; j < iter->second.size(); j++) {
                     if(iter->second[i]->display_frame.top > iter->second[j]->display_frame.top) {
                        HWC2_ALOGD_IF_VERBOSE("swap %d and %d",iter->second[i]->uId_,iter->second[j]->uId_);
                        std::swap(iter->second[i],iter->second[j]);
                     }
                 }
            }
        }
  }


  for (LayerMap::iterator iter = layer_map.begin();
       iter != layer_map.end(); ++iter) {
        ALOGD_IF(LogLevel(DBG_DEBUG),"layer map id=%d,size=%zu",iter->first,iter->second.size());
        for(std::vector<DrmHwcLayer*>::const_iterator iter_layer = iter->second.begin();
            iter_layer != iter->second.end();++iter_layer)
        {
             ALOGD_IF(LogLevel(DBG_DEBUG),"\tlayer id=%u , name=%s",(*iter_layer)->uId_,(*iter_layer)->sLayerName_.c_str());
        }
  }

    if((int)layer_map.size() > iPlaneSize)
    {
        ALOGD_IF(LogLevel(DBG_DEBUG),"map size=%zu should not bigger than plane size=%d", layer_map.size(), iPlaneSize);
        return -1;
    }

    return 0;

}

bool Vop3588::HasGetNoAfbcUsablePlanes(DrmCrtc *crtc, std::vector<PlaneGroup *> &plane_groups) {
    std::vector<DrmPlane *> usable_planes;
    //loop plane groups.
    for (std::vector<PlaneGroup *> ::const_iterator iter = plane_groups.begin();
       iter != plane_groups.end(); ++iter) {
            if(!(*iter)->bUse)
                //only count the first plane in plane group.
                std::copy_if((*iter)->planes.begin(), (*iter)->planes.begin()+1,
                       std::back_inserter(usable_planes),
                       [=](DrmPlane *plane) {
                       return !plane->is_use() && plane->GetCrtcSupported(*crtc) && !plane->get_afbc(); }
                       );
  }
  return usable_planes.size() > 0;;
}

bool Vop3588::HasGetNoYuvUsablePlanes(DrmCrtc *crtc, std::vector<PlaneGroup *> &plane_groups) {
    std::vector<DrmPlane *> usable_planes;
    //loop plane groups.
    for (std::vector<PlaneGroup *> ::const_iterator iter = plane_groups.begin();
       iter != plane_groups.end(); ++iter) {
            if(!(*iter)->bUse)
                //only count the first plane in plane group.
                std::copy_if((*iter)->planes.begin(), (*iter)->planes.begin()+1,
                       std::back_inserter(usable_planes),
                       [=](DrmPlane *plane) {
                       return !plane->is_use() && plane->GetCrtcSupported(*crtc) && !plane->get_yuv(); }
                       );
  }
  return usable_planes.size() > 0;;
}

bool Vop3588::HasGetNoScaleUsablePlanes(DrmCrtc *crtc, std::vector<PlaneGroup *> &plane_groups) {
    std::vector<DrmPlane *> usable_planes;
    //loop plane groups.
    for (std::vector<PlaneGroup *> ::const_iterator iter = plane_groups.begin();
       iter != plane_groups.end(); ++iter) {
            if(!(*iter)->bUse)
                //only count the first plane in plane group.
                std::copy_if((*iter)->planes.begin(), (*iter)->planes.begin()+1,
                       std::back_inserter(usable_planes),
                       [=](DrmPlane *plane) {
                       return !plane->is_use() && plane->GetCrtcSupported(*crtc) && !plane->get_scale(); }
                       );
  }
  return usable_planes.size() > 0;;
}

bool Vop3588::HasGetNoAlphaUsablePlanes(DrmCrtc *crtc, std::vector<PlaneGroup *> &plane_groups) {
    std::vector<DrmPlane *> usable_planes;
    //loop plane groups.
    for (std::vector<PlaneGroup *> ::const_iterator iter = plane_groups.begin();
       iter != plane_groups.end(); ++iter) {
            if(!(*iter)->bUse)
                //only count the first plane in plane group.
                std::copy_if((*iter)->planes.begin(), (*iter)->planes.begin()+1,
                       std::back_inserter(usable_planes),
                       [=](DrmPlane *plane) {
                       return !plane->is_use() && plane->GetCrtcSupported(*crtc) && !plane->alpha_property().id(); }
                       );
  }
  return usable_planes.size() > 0;
}

bool Vop3588::HasGetNoEotfUsablePlanes(DrmCrtc *crtc, std::vector<PlaneGroup *> &plane_groups) {
    std::vector<DrmPlane *> usable_planes;
    //loop plane groups.
    for (std::vector<PlaneGroup *> ::const_iterator iter = plane_groups.begin();
       iter != plane_groups.end(); ++iter) {
            if(!(*iter)->bUse)
                //only count the first plane in plane group.
                std::copy_if((*iter)->planes.begin(), (*iter)->planes.begin()+1,
                       std::back_inserter(usable_planes),
                       [=](DrmPlane *plane) {
                       return !plane->is_use() && plane->GetCrtcSupported(*crtc) && !plane->get_hdr2sdr(); }
                       );
  }
  return usable_planes.size() > 0;
}

bool Vop3588::GetCrtcSupported(const DrmCrtc &crtc, uint32_t possible_crtc_mask) {
  return !!((1 << crtc.pipe()) & possible_crtc_mask);
}

bool Vop3588::HasPlanesWithSize(DrmCrtc *crtc, int layer_size, std::vector<PlaneGroup *> &plane_groups) {
    //loop plane groups.
    for (std::vector<PlaneGroup *> ::const_iterator iter = plane_groups.begin();
       iter != plane_groups.end(); ++iter) {
            if(GetCrtcSupported(*crtc, (*iter)->possible_crtcs) && !(*iter)->bUse &&
                (*iter)->planes.size() == (size_t)layer_size)
                return true;
  }
  return false;
}

int Vop3588::MatchPlane(std::vector<DrmCompositionPlane> *composition_planes,
                   std::vector<PlaneGroup *> &plane_groups,
                   DrmCompositionPlane::Type type, DrmCrtc *crtc,
                   std::pair<int, std::vector<DrmHwcLayer*>> layers, int zpos, bool match_best=false) {

  uint32_t layer_size = layers.second.size();
  bool b_yuv=false,b_scale=false,b_alpha=false,b_hdr2sdr=false,b_afbc=false;
  std::vector<PlaneGroup *> ::const_iterator iter;
  uint64_t rotation = 0;
  uint64_t alpha = 0xFF;
  uint16_t eotf = TRADITIONAL_GAMMA_SDR;
  bool bMulArea = layer_size > 0 ? true : false;
  bool b8kMode = ctx.state.b8kMode_;
  bool b4k120Mode = ctx.state.b4k120pMode_;


  //loop plane groups.
  for (iter = plane_groups.begin();
     iter != plane_groups.end(); ++iter) {
     HWC2_ALOGD_IF_VERBOSE("line=%d,last zpos=%d,group(%" PRIu64 ") zpos=%d,group bUse=%d,crtc=0x%x,"
                                   "current_crtc=0x%x,possible_crtcs=0x%x",
                                   __LINE__, zpos, (*iter)->share_id, (*iter)->zpos, (*iter)->bUse,
                                   (1<<crtc->pipe()), (*iter)->current_crtc_,(*iter)->possible_crtcs);
      //find the match zpos plane group
      if(!(*iter)->bUse && !(*iter)->bReserved && (((1<<crtc->pipe()) & (*iter)->current_crtc_) > 0))
      {
          HWC2_ALOGD_IF_VERBOSE("line=%d,layer_size=%d,planes size=%zu",__LINE__,layer_size,(*iter)->planes.size());

          //find the match combine layer count with plane size.
          if(layer_size <= (*iter)->planes.size())
          {
              uint32_t combine_layer_count = 0;

              //loop layer
              for(std::vector<DrmHwcLayer*>::const_iterator iter_layer= layers.second.begin();
                  iter_layer != layers.second.end();++iter_layer)
              {
                  //reset is_match to false
                  (*iter_layer)->bMatch_ = false;

                  if(match_best || (*iter_layer)->iBestPlaneType > 0){
                      if(!((*iter)->win_type & (*iter_layer)->iBestPlaneType)){
                          HWC2_ALOGD_IF_VERBOSE("line=%d, plane_group win-type = 0x%" PRIx64 " , layer best-type = %x, not match ",
                          __LINE__,(*iter)->win_type, (*iter_layer)->iBestPlaneType);
                          continue;
                      }
                  }

                  //loop plane
                  for(std::vector<DrmPlane*> ::const_iterator iter_plane=(*iter)->planes.begin();
                      !(*iter)->planes.empty() && iter_plane != (*iter)->planes.end(); ++iter_plane)
                  {
                      HWC2_ALOGD_IF_VERBOSE("line=%d,crtc=0x%x,%s is_use=%d,possible_crtc_mask=0x%x",__LINE__,(1<<crtc->pipe()),
                              (*iter_plane)->name(),(*iter_plane)->is_use(),(*iter_plane)->get_possible_crtc_mask());

                      if(!(*iter_plane)->is_use() && (*iter_plane)->GetCrtcSupported(*crtc))
                      {
                          bool bNeed = false;

                          // Cluster 0, 初始化 Cluster 图层匹配参数
                          if((*iter_plane)->win_type() & PLANE_RK3588_CLUSTER0_WIN0){
                                ctx.state.bClu0Used = false;
                                ctx.state.iClu0UsedZ = -1;
                                ctx.state.bClu0TwoWinMode = true;
                                ctx.state.iClu0UsedDstXOffset = 0;
                          }
                          // Cluster 1, 初始化 Cluster 图层匹配参数
                          if((*iter_plane)->win_type() & PLANE_RK3588_CLUSTER1_WIN0){
                                ctx.state.bClu1Used = false;
                                ctx.state.iClu1UsedZ = -1;
                                ctx.state.bClu1TwoWinMode = true;
                                ctx.state.iClu1UsedDstXOffset = 0;
                          }

                          // Cluster 2, 初始化 Cluster 图层匹配参数
                          if((*iter_plane)->win_type() & PLANE_RK3588_CLUSTER2_WIN0){
                                ctx.state.bClu2Used = false;
                                ctx.state.iClu2UsedZ = -1;
                                ctx.state.bClu2TwoWinMode = true;
                                ctx.state.iClu2UsedDstXOffset = 0;
                          }
                          // Cluster 3, 初始化 Cluster 图层匹配参数
                          if((*iter_plane)->win_type() & PLANE_RK3588_CLUSTER3_WIN0){
                                ctx.state.bClu3Used = false;
                                ctx.state.iClu3UsedZ = -1;
                                ctx.state.bClu3TwoWinMode = true;
                                ctx.state.iClu3UsedDstXOffset = 0;
                          }
                          // 保证Cluster two-win-mode zpos 连续，否则关闭two-win模式
                          if(ctx.state.bClu0Used && ((*iter_plane)->win_type() & PLANE_RK3588_CLUSTER0_WIN1) > 0){
                            if(((zpos - ctx.state.iClu0UsedZ) != 1 && !(zpos == ctx.state.iClu0UsedZ)) ||
                               (ctx.state.iClu0UsedFormat != (*iter_layer)->uFourccFormat_) ||
                               (ctx.state.iClu0UsedAfbc != (*iter_layer)->bAfbcd_)){
                            ctx.state.bClu0TwoWinMode = false;
                            }
                          }

                          // 保证Cluster two-win-mode zpos 连续，否则关闭two-win模式
                          if(ctx.state.bClu1Used && ((*iter_plane)->win_type() & PLANE_RK3588_CLUSTER1_WIN1) > 0){
                            if(((zpos - ctx.state.iClu1UsedZ) != 1 && !(zpos == ctx.state.iClu1UsedZ)) ||
                               (ctx.state.iClu1UsedFormat != (*iter_layer)->uFourccFormat_) ||
                               (ctx.state.iClu1UsedAfbc != (*iter_layer)->bAfbcd_)){
                            ctx.state.bClu1TwoWinMode = false;
                            }
                          }

                          // 保证Cluster two-win-mode zpos 连续，否则关闭two-win模式
                          if(ctx.state.bClu2Used && ((*iter_plane)->win_type() & PLANE_RK3588_CLUSTER2_WIN1) > 0){
                            if(((zpos - ctx.state.iClu2UsedZ) != 1 && !(zpos == ctx.state.iClu2UsedZ)) ||
                               (ctx.state.iClu2UsedFormat != (*iter_layer)->uFourccFormat_) ||
                               (ctx.state.iClu2UsedAfbc != (*iter_layer)->bAfbcd_)){
                            ctx.state.bClu2TwoWinMode = false;
                            }
                          }

                          // 保证Cluster two-win-mode zpos 连续，否则关闭two-win模式
                          if(ctx.state.bClu3Used && ((*iter_plane)->win_type() & PLANE_RK3588_CLUSTER3_WIN1) > 0){
                            if(((zpos - ctx.state.iClu3UsedZ) != 1 && !(zpos == ctx.state.iClu3UsedZ)) ||
                               (ctx.state.iClu3UsedFormat != (*iter_layer)->uFourccFormat_) ||
                               (ctx.state.iClu3UsedAfbc != (*iter_layer)->bAfbcd_)){
                            ctx.state.bClu3TwoWinMode = false;
                            }
                          }

                          // 其他的Cluster限制条件
                          if(((*iter_plane)->win_type() & PLANE_RK3588_CLUSTER0_WIN1) > 0){
                            if(!ctx.state.bClu0TwoWinMode){
                              ALOGD_IF(LogLevel(DBG_DEBUG),"%s disable Cluster two win mode",(*iter_plane)->name());
                              continue;
                            }
                            // Two-Win 模式offset需要奇偶对齐
                            int dst_x_offset = (*iter_layer)->display_frame.left;
                            if((ctx.state.iClu0UsedDstXOffset % 2) !=  (dst_x_offset % 2)){
                              ctx.state.bClu0TwoWinMode = false;
                              ALOGD_IF(LogLevel(DBG_DEBUG),"%s can't overlay win0-dst-x=%d,win1-dst-x=%d",(*iter_plane)->name(),ctx.state.iClu0UsedDstXOffset,dst_x_offset);
                              continue;
                            }
                            // 输入输出分辨率要求小于2048
                            int src_w = (*iter_layer)->source_crop.right - (*iter_layer)->source_crop.left;
                            int dst_w = (*iter_layer)->display_frame.right - (*iter_layer)->display_frame.left;
                            if(src_w > 2048 || dst_w > 2048){
                              ctx.state.bClu0TwoWinMode = false;
                              ALOGD_IF(LogLevel(DBG_DEBUG),"%s can't overlay src_w=%d, dst_w=%d",(*iter_plane)->name(),src_w,dst_w);
                              continue;
                            }
                          }
                          // 其他的Cluster限制条件
                          if(((*iter_plane)->win_type() & PLANE_RK3588_CLUSTER1_WIN1) > 0){
                            if(!ctx.state.bClu1TwoWinMode){
                              ALOGD_IF(LogLevel(DBG_DEBUG),"%s disable Cluster two win mode",(*iter_plane)->name());
                              continue;
                            }
                            // Two-Win 模式offset需要奇偶对齐
                            int dst_x_offset = (*iter_layer)->display_frame.left;
                            if((ctx.state.iClu1UsedDstXOffset % 2) !=  (dst_x_offset % 2)){
                              ctx.state.bClu1TwoWinMode = false;
                              ALOGD_IF(LogLevel(DBG_DEBUG),"%s can't overlay win0-dst-x=%d,win1-dst-x=%d",(*iter_plane)->name(),ctx.state.iClu1UsedDstXOffset,dst_x_offset);
                              continue;
                            }
                            // 输入输出分辨率要求小于2048
                            int src_w = (*iter_layer)->source_crop.right - (*iter_layer)->source_crop.left;
                            int dst_w = (*iter_layer)->display_frame.right - (*iter_layer)->display_frame.left;
                            if(src_w > 2048 || dst_w > 2048){
                              ctx.state.bClu1TwoWinMode = false;
                              ALOGD_IF(LogLevel(DBG_DEBUG),"%s can't overlay src_w=%d, dst_w=%d",(*iter_plane)->name(),src_w,dst_w);
                              continue;
                            }
                          }
                          // 其他的Cluster限制条件
                          if(((*iter_plane)->win_type() & PLANE_RK3588_CLUSTER2_WIN1) > 0){
                            if(!ctx.state.bClu2TwoWinMode){
                              ALOGD_IF(LogLevel(DBG_DEBUG),"%s disable Cluster two win mode",(*iter_plane)->name());
                              continue;
                            }
                            // Two-Win 模式offset需要奇偶对齐
                            int dst_x_offset = (*iter_layer)->display_frame.left;
                            if((ctx.state.iClu2UsedDstXOffset % 2) !=  (dst_x_offset % 2)){
                              ctx.state.bClu2TwoWinMode = false;
                              ALOGD_IF(LogLevel(DBG_DEBUG),"%s can't overlay win0-dst-x=%d,win1-dst-x=%d",(*iter_plane)->name(),ctx.state.iClu1UsedDstXOffset,dst_x_offset);
                              continue;
                            }
                            // 输入输出分辨率要求小于2048
                            int src_w = (*iter_layer)->source_crop.right - (*iter_layer)->source_crop.left;
                            int dst_w = (*iter_layer)->display_frame.right - (*iter_layer)->display_frame.left;
                            if(src_w > 2048 || dst_w > 2048){
                              ctx.state.bClu2TwoWinMode = false;
                              ALOGD_IF(LogLevel(DBG_DEBUG),"%s can't overlay src_w=%d, dst_w=%d",(*iter_plane)->name(),src_w,dst_w);
                              continue;
                            }
                          }
                          // 其他的Cluster限制条件
                          if(((*iter_plane)->win_type() & PLANE_RK3588_CLUSTER3_WIN1) > 0){
                            if(!ctx.state.bClu3TwoWinMode){
                              ALOGD_IF(LogLevel(DBG_DEBUG),"%s disable Cluster two win mode",(*iter_plane)->name());
                              continue;
                            }
                            // Two-Win 模式offset需要奇偶对齐
                            int dst_x_offset = (*iter_layer)->display_frame.left;
                            if((ctx.state.iClu3UsedDstXOffset % 2) !=  (dst_x_offset % 2)){
                              ctx.state.bClu3TwoWinMode = false;
                              ALOGD_IF(LogLevel(DBG_DEBUG),"%s can't overlay win0-dst-x=%d,win1-dst-x=%d",(*iter_plane)->name(),ctx.state.iClu1UsedDstXOffset,dst_x_offset);
                              continue;
                            }
                            // 输入输出分辨率要求小于2048
                            int src_w = (*iter_layer)->source_crop.right - (*iter_layer)->source_crop.left;
                            int dst_w = (*iter_layer)->display_frame.right - (*iter_layer)->display_frame.left;
                            if(src_w > 2048 || dst_w > 2048){
                              ctx.state.bClu3TwoWinMode = false;
                              ALOGD_IF(LogLevel(DBG_DEBUG),"%s can't overlay src_w=%d, dst_w=%d",(*iter_plane)->name(),src_w,dst_w);
                              continue;
                            }
                          }

                          // Format
                          if((*iter_plane)->is_support_format((*iter_layer)->uFourccFormat_,(*iter_layer)->bAfbcd_)){
                            bNeed = true;
                          }else{
                            // FB-Target 如果匹配失败，尝试反转AFBC压缩格式再匹配
                            if((*iter_layer)->bFbTarget_ &&
                               (hwc_get_int_property("vendor.gralloc.no_afbc_for_fb_target_layer","0") == 0) &&
                               (*iter_plane)->is_support_format((*iter_layer)->uFourccFormat_,!(*iter_layer)->bAfbcd_)){
                                (*iter_layer)->bAfbcd_ = !(*iter_layer)->bAfbcd_;
                            }else{
                              ALOGD_IF(LogLevel(DBG_DEBUG),"%s cann't support fourcc=0x%x afbcd = %d",(*iter_plane)->name(),(*iter_layer)->uFourccFormat_,(*iter_layer)->bAfbcd_);
                              continue;
                            }
                          }

                          // Input info
                          int input_w = (int)((*iter_layer)->source_crop.right - (*iter_layer)->source_crop.left);
                          int input_h = (int)((*iter_layer)->source_crop.bottom - (*iter_layer)->source_crop.top);
                          if(b8kMode ? (*iter_plane)->is_support_input_8k(input_w,input_h) : \
                                       (*iter_plane)->is_support_input(input_w,input_h)){
                            bNeed = true;
                          }else{
                            ALOGD_IF(LogLevel(DBG_DEBUG),"%s cann't support intput (%d,%d), max_input_range is (%d,%d)",
                                    (*iter_plane)->name(),input_w,input_h,(*iter_plane)->get_input_w_max(),(*iter_plane)->get_input_h_max());
                            continue;

                          }

                          // Output info
                          int output_w = (*iter_layer)->display_frame.right - (*iter_layer)->display_frame.left;
                          int output_h = (*iter_layer)->display_frame.bottom - (*iter_layer)->display_frame.top;

                          if(b8kMode ? (*iter_plane)->is_support_output_8k(output_w,output_h) : \
                                       (*iter_plane)->is_support_output(output_w,output_h)){
                            bNeed = true;
                          }else{
                            ALOGD_IF(LogLevel(DBG_DEBUG),"%s cann't support output (%d,%d), max_input_range is (%d,%d)",
                                    (*iter_plane)->name(),output_w,output_h,(*iter_plane)->get_output_w_max(),(*iter_plane)->get_output_h_max());
                            continue;

                          }

                          // Scale
                          // RK3588 源数据宽大于 4096 且在 8K 分辨率条件下，缩放系数需要做调整：
                          //   Cluster:目前仅支持 0.9-1.1 的居中缩放;
                          //   Esmart：可以支持 0.125-8 缩放；
                          bool b8kInputScaleMode = false;
                          if(b8kMode && (input_w > 4096))
                            b8kInputScaleMode = true;

                          // RK3588 源数据宽大于 3840 小于 4096 且在 8K 分辨率条件下，缩小系数需要做调整：
                          //   Cluster:目前仅支持 0.9-1 的居中缩小;
                          //   Esmart：可以支持 0.125-8 缩放；
                          bool b4kInputScaleMode = false;
                          if(b8kMode && (input_w >= 3840 && input_w <= 4096))
                            b4kInputScaleMode = true;

                          // 居中缩放标志，容忍值设置为 2 pixel
                          bool bCenterScale =
                               (ctx.state.iDisplayWidth_  - (output_w + 2 * (*iter_layer)->display_frame.left)) < 2 &&
                               (ctx.state.iDisplayHeight_ - (output_h + 2 * (*iter_layer)->display_frame.top))  < 2;

                          // 8K分辨率目前仅支持居中缩放
                          if(b8kInputScaleMode){
                            if( bCenterScale &&
                                (*iter_plane)->is_support_scale_8k((*iter_layer)->fHScaleMul_) &&
                                (*iter_plane)->is_support_scale_8k((*iter_layer)->fVScaleMul_) ){
                              bNeed = true;
                            }else{
                              ALOGD_IF(LogLevel(DBG_DEBUG),"%s cann't support bCenterScale(%d) factor(%f,%f)",
                                        (*iter_plane)->name(),
                                        bCenterScale,
                                        (*iter_layer)->fHScaleMul_,
                                        (*iter_layer)->fVScaleMul_);
                              continue;
                            }
                          }else if(b4kInputScaleMode){
                            if((*iter_plane)->is_support_scale((*iter_layer)->fHScaleMul_) &&
                                (*iter_plane)->is_support_scale((*iter_layer)->fVScaleMul_) &&
                                // 放大无要求
                                (( (*iter_layer)->fHScaleMul_ <= 1.0 &&
                                  (*iter_layer)->fVScaleMul_ <= 1.0)   ||
                                // 缩小有居中缩小要求
                                (bCenterScale &&
                                  (*iter_layer)->fHScaleMul_ < 1.1 &&
                                  (*iter_layer)->fVScaleMul_ < 1.1))){
                              bNeed = true;
                            }else{
                              ALOGD_IF(LogLevel(DBG_DEBUG),"%s cann't support bCenterScale(%d) factor(%f,%f)",
                                        (*iter_plane)->name(),
                                        bCenterScale,
                                        (*iter_layer)->fHScaleMul_,
                                        (*iter_layer)->fVScaleMul_);
                              continue;
                            }
                          }else{
                            if((*iter_plane)->is_support_scale((*iter_layer)->fHScaleMul_) &&
                               (*iter_plane)->is_support_scale((*iter_layer)->fVScaleMul_)){
                                bNeed = true;
                            }else{
                              ALOGD_IF(LogLevel(DBG_DEBUG),"%s cann't support scale factor(%f,%f)",
                                        (*iter_plane)->name(),
                                        (*iter_layer)->fHScaleMul_,
                                        (*iter_layer)->fVScaleMul_);
                              continue;
                            }
                          }

                          // Scale 4K 120帧分辨率下
                          // RK3588 源数据宽大于3840时，不支持缩小
                          bool b4k120ScaleMode = false;
                          if(b4k120Mode && (input_w >= 3840))
                            b4k120ScaleMode = true;

                          if(b4k120ScaleMode && ( ((*iter_layer)->fHScaleMul_ > 1.0)
                                                   || ((*iter_layer)->fVScaleMul_ > 1.0) ) ){
                            ALOGD_IF(LogLevel(DBG_DEBUG),"%s 8K120p cann't support input(%dx%d) scale factor(%f,%f)",
                                    (*iter_plane)->name(), input_w,input_h,
                                    (*iter_layer)->fHScaleMul_, (*iter_layer)->fVScaleMul_);
                            continue;
                          }

                          // Alpha
                          if ((*iter_layer)->blending == DrmHwcBlending::kPreMult)
                              alpha = (*iter_layer)->alpha;
                          b_alpha = (*iter_plane)->alpha_property().id()?true:false;
                          if(alpha != 0xFF)
                          {
                              if(!b_alpha)
                              {
                                  ALOGV("layer id=%d, %s",(*iter_layer)->uId_,(*iter_plane)->name());
                                  ALOGD_IF(LogLevel(DBG_DEBUG),"%s cann't support alpha,layer alpha=0x%x,alpha id=%d",
                                          (*iter_plane)->name(),(*iter_layer)->alpha,(*iter_plane)->alpha_property().id());
                                  continue;
                              }
                              else
                                  bNeed = true;
                          }

                          // HDR
                          bool hdr_layer = (*iter_layer)->bHdr_;
                          b_hdr2sdr = crtc->get_hdr();
                          if(hdr_layer){
                              if(!b_hdr2sdr){
                                  ALOGV("layer id=%d, %s",(*iter_layer)->uId_,(*iter_plane)->name());
                                  ALOGD_IF(LogLevel(DBG_DEBUG),"%s cann't support hdr layer,layer hdr=%d, crtc can_hdr=%d",
                                          (*iter_plane)->name(),hdr_layer,b_hdr2sdr);
                                  continue;
                              }
                              else
                                  bNeed = true;
                          }

                          // Only YUV use Cluster rotate
                          if(b8kMode ? (*iter_plane)->is_support_transform_8k((*iter_layer)->transform) : \
                                       (*iter_plane)->is_support_transform((*iter_layer)->transform)){

                            if(((*iter_plane)->win_type() & PLANE_RK3588_ALL_CLUSTER_MASK) &&
                                !(*iter_layer)->bAfbcd_ && (*iter_layer)->transform != DRM_MODE_ROTATE_0){
                              // Cluster only rotate afbc format
                              ALOGD_IF(LogLevel(DBG_DEBUG),"%s cann't support noAfbc(%d) layer transform",
                                        (*iter_plane)->name(), (*iter_layer)->bAfbcd_);
                              continue;
                            }
                            if(((*iter_layer)->transform & (DRM_MODE_REFLECT_X | DRM_MODE_ROTATE_90 | DRM_MODE_ROTATE_270)) != 0){
                              // Cluster rotate must 64 align
                              if(((*iter_layer)->iStride_ % 64 != 0)){
                                ALOGD_IF(LogLevel(DBG_DEBUG),"%s cann't support layer transform(xmirror or 90 or 270) 0x%x and iStride_ = %d",
                                        (*iter_plane)->name(), (*iter_layer)->transform,(*iter_layer)->iStride_);
                                continue;
                              }
                            }

                            if(((*iter_layer)->transform & (DRM_MODE_ROTATE_90 | DRM_MODE_ROTATE_270)) != 0){
                              //Cluster rotate input_h must <= 2048
                              if(input_h > 2048){
                                ALOGD_IF(LogLevel(DBG_DEBUG),"%s cann't support layer transform(90 or 270) 0x%x and input_h = %d",
                                        (*iter_plane)->name(), (*iter_layer)->transform,input_h);
                                continue;
                              }
                            }
                          }else{
                              ALOGD_IF(LogLevel(DBG_DEBUG),"%s cann't support layer transform 0x%x, support 0x%x",
                                      (*iter_plane)->name(), (*iter_layer)->transform,(*iter_plane)->get_transform());
                              continue;
                          }

                          ALOGD_IF(LogLevel(DBG_DEBUG),"MatchPlane: match id=%d name=%s, Plane=%s, zops=%d",
                              (*iter_layer)->uId_,
                              (*iter_layer)->sLayerName_.c_str(),
                              (*iter_plane)->name(),zpos);
                          //Find the match plane for layer,it will be commit.
                          composition_planes->emplace_back(type, (*iter_plane), crtc, (*iter_layer)->iDrmZpos_);
                          (*iter_layer)->bMatch_ = true;
                          (*iter_plane)->set_use(true);
                          composition_planes->back().set_zpos(zpos);
                          combine_layer_count++;

                          // Cluster disable two win mode?
                          if((*iter_plane)->win_type() & PLANE_RK3588_CLUSTER0_WIN0){
                              ctx.state.bClu0Used = true;
                              ctx.state.iClu0UsedZ = zpos;
                              ctx.state.iClu0UsedDstXOffset = (*iter_layer)->display_frame.left;
                              ctx.state.iClu0UsedFormat = (*iter_layer)->uFourccFormat_;
                              ctx.state.iClu0UsedAfbc = (*iter_layer)->bAfbcd_;
                              if(input_w > 2048  ||
                                 output_w > 2048 ||
                                 eotf != TRADITIONAL_GAMMA_SDR ||
                                 ((*iter_layer)->transform & (DRM_MODE_ROTATE_90 | DRM_MODE_ROTATE_270)) != 0 ||
                                 b8kMode){
                                  ctx.state.bClu0TwoWinMode = false;
                              }else{
                                  ctx.state.bClu0TwoWinMode = true;
                              }
                          }else if((*iter_plane)->win_type() & PLANE_RK3588_CLUSTER1_WIN0){
                              ctx.state.bClu1Used = true;
                              ctx.state.iClu1UsedZ = zpos;
                              ctx.state.iClu1UsedDstXOffset = (*iter_layer)->display_frame.left;
                              ctx.state.iClu1UsedFormat = (*iter_layer)->uFourccFormat_;
                              ctx.state.iClu1UsedAfbc = (*iter_layer)->bAfbcd_;
                              if(input_w > 2048  ||
                                 output_w > 2048 ||
                                 eotf != TRADITIONAL_GAMMA_SDR ||
                                 ((*iter_layer)->transform & (DRM_MODE_ROTATE_90 | DRM_MODE_ROTATE_270)) != 0 ||
                                 b8kMode){
                                  ctx.state.bClu1TwoWinMode = false;
                              }else{
                                  ctx.state.bClu1TwoWinMode = true;
                              }
                          }else if((*iter_plane)->win_type() & PLANE_RK3588_CLUSTER2_WIN0){
                              ctx.state.bClu2Used = true;
                              ctx.state.iClu2UsedZ = zpos;
                              ctx.state.iClu2UsedDstXOffset = (*iter_layer)->display_frame.left;
                              ctx.state.iClu2UsedFormat = (*iter_layer)->uFourccFormat_;
                              ctx.state.iClu2UsedAfbc = (*iter_layer)->bAfbcd_;
                              if(input_w > 2048  ||
                                 output_w > 2048 ||
                                 eotf != TRADITIONAL_GAMMA_SDR ||
                                 ((*iter_layer)->transform & (DRM_MODE_ROTATE_90 | DRM_MODE_ROTATE_270)) != 0 ||
                                 b8kMode){
                                  ctx.state.bClu2TwoWinMode = false;
                              }else{
                                  ctx.state.bClu2TwoWinMode = true;
                              }
                          }else if((*iter_plane)->win_type() & PLANE_RK3588_CLUSTER3_WIN0){
                              ctx.state.bClu3Used = true;
                              ctx.state.iClu3UsedZ = zpos;
                              ctx.state.iClu3UsedDstXOffset = (*iter_layer)->display_frame.left;
                              ctx.state.iClu3UsedFormat = (*iter_layer)->uFourccFormat_;
                              ctx.state.iClu3UsedAfbc = (*iter_layer)->bAfbcd_;
                              if(input_w > 2048  ||
                                 output_w > 2048 ||
                                 eotf != TRADITIONAL_GAMMA_SDR ||
                                 ((*iter_layer)->transform & (DRM_MODE_ROTATE_90 | DRM_MODE_ROTATE_270)) != 0 ||
                                 b8kMode){
                                  ctx.state.bClu3TwoWinMode = false;
                              }else{
                                  ctx.state.bClu3TwoWinMode = true;
                              }
                          }
                          break;
                      }
                  }
              }
              if(combine_layer_count == layer_size)
              {
                  ALOGD_IF(LogLevel(DBG_VERBOSE),"line=%d all match",__LINE__);
                  (*iter)->bUse = true;
                  return 0;
              }
          }
      }

  }
  return -1;
}

void Vop3588::ResetPlaneGroups(std::vector<PlaneGroup *> &plane_groups){
  for (auto &plane_group : plane_groups){
    for(auto &p : plane_group->planes)
      p->set_use(false);
      plane_group->bUse = false;
  }
  return;
}

void Vop3588::ResetLayer(std::vector<DrmHwcLayer*>& layers){
    for (auto &drmHwcLayer : layers){
      drmHwcLayer->bMatch_ = false;
    }
    return;
}

int Vop3588::MatchBestPlanes(
    std::vector<DrmCompositionPlane> *composition,
    std::vector<DrmHwcLayer*> &layers, DrmCrtc *crtc,
    std::vector<PlaneGroup *> &plane_groups) {
  ResetLayer(layers);
  ResetPlaneGroups(plane_groups);
  composition->clear();
  LayerMap layer_map;
  CombineLayer(layer_map, layers, plane_groups.size());

  // Fill up the remaining planes
  int zpos = 0;
  for (auto i = layer_map.begin(); i != layer_map.end(); i = layer_map.erase(i)) {
    int ret = MatchPlane(composition, plane_groups, DrmCompositionPlane::Type::kLayer,
                      crtc, std::make_pair(i->first, i->second),zpos, true);
    // We don't have any planes left
    if (ret == -ENOENT){
      ALOGD_IF(LogLevel(DBG_DEBUG),"Failed to match all layer, try other HWC policy ret = %d,line = %d",ret,__LINE__);
      ResetLayer(layers);
      ResetPlaneGroups(plane_groups);
      return ret;
    }else if (ret) {
      ALOGD_IF(LogLevel(DBG_DEBUG),"Failed to match all layer, try other HWC policy ret = %d, line = %d",ret,__LINE__);
      ResetLayer(layers);
      ResetPlaneGroups(plane_groups);
      return ret;
    }
    zpos++;
  }

  return 0;
}


int Vop3588::MatchPlanes(
    std::vector<DrmCompositionPlane> *composition,
    std::vector<DrmHwcLayer*> &layers, DrmCrtc *crtc,
    std::vector<PlaneGroup *> &plane_groups) {
  ResetLayer(layers);
  ResetPlaneGroups(plane_groups);
  composition->clear();
  LayerMap layer_map;
  CombineLayer(layer_map, layers, plane_groups.size());

  int total_size = 0;

  // Fill up the remaining planes
  int zpos = 0;
  for (auto i = layer_map.begin(); i != layer_map.end(); i = layer_map.erase(i)) {
    int ret = MatchPlane(composition, plane_groups, DrmCompositionPlane::Type::kLayer,
                      crtc, std::make_pair(i->first, i->second),zpos);
    if (ret) {
      ALOGD_IF(LogLevel(DBG_DEBUG),"Failed to match all layer, try other HWC policy ret = %d, line = %d",ret,__LINE__);
      ResetLayer(layers);
      ResetPlaneGroups(plane_groups);
      composition->clear();
      return ret;
    }
    zpos++;

    // 总数据量超过，iVopMaxOverlay4KPlane 层 4K RGBA 图层，则认为匹配失败。
    if(ctx.state.iVopMaxOverlay4KPlane > 0 ){
      for( auto layer : i->second){
        if(layer->iSize_ > 0){
          total_size += layer->iSize_;
        }
        HWC2_ALOGD_IF_DEBUG(" total_size =%d + %s size=%d",total_size,layer->sLayerName_.c_str(),layer->iSize_);
      }
      if( total_size > 4096*2160*4*ctx.state.iVopMaxOverlay4KPlane ){
        HWC2_ALOGD_IF_DEBUG("total_size (%d) is too big to fail match policy.", total_size);
        ResetLayer(layers);
        ResetPlaneGroups(plane_groups);
        composition->clear();
        return -1;
      }
    }
  }
  return 0;
}
int  Vop3588::GetPlaneGroups(DrmCrtc *crtc, std::vector<PlaneGroup *>&out_plane_groups){
  DrmDevice *drm = crtc->getDrmDevice();
  out_plane_groups.clear();
  std::vector<PlaneGroup *> all_plane_groups = drm->GetPlaneGroups();
  for(auto &plane_group : all_plane_groups){
    if(plane_group->acquire(1 << crtc->pipe()))
      out_plane_groups.push_back(plane_group);
  }

  return out_plane_groups.size() > 0 ? 0 : -1;
}

void Vop3588::ResetLayerFromTmpExceptFB(std::vector<DrmHwcLayer*>& layers,
                                              std::vector<DrmHwcLayer*>& tmp_layers){
  for (auto i = layers.begin(); i != layers.end();){
      if((*i)->bFbTarget_){
          tmp_layers.emplace_back(std::move(*i));
          i = layers.erase(i);
          continue;
      }
      i++;
  }
  for (auto i = tmp_layers.begin(); i != tmp_layers.end();){
    if((*i)->bFbTarget_){
      i++;
      continue;
    }
    layers.emplace_back(std::move(*i));
    i = tmp_layers.erase(i);
  }
  //sort
  for (auto i = layers.begin(); i != layers.end()-1; i++){
     for (auto j = i+1; j != layers.end(); j++){
        if((*i)->iZpos_ > (*j)->iZpos_){
           std::swap(*i, *j);
        }
     }
  }

  return;
}


void Vop3588::ResetLayerFromTmp(std::vector<DrmHwcLayer*>& layers,
                                              std::vector<DrmHwcLayer*>& tmp_layers){
  for (auto i = tmp_layers.begin(); i != tmp_layers.end();){
         layers.emplace_back(std::move(*i));
         i = tmp_layers.erase(i);
     }
     //sort
     for (auto i = layers.begin(); i != layers.end()-1; i++){
         for (auto j = i+1; j != layers.end(); j++){
             if((*i)->iZpos_ > (*j)->iZpos_){
                 std::swap(*i, *j);
             }
         }
     }

    return;
}

void Vop3588::MoveFbToTmp(std::vector<DrmHwcLayer*>& layers,
                                       std::vector<DrmHwcLayer*>& tmp_layers){
  for (auto i = layers.begin(); i != layers.end();){
      if((*i)->bFbTarget_){
          tmp_layers.emplace_back(std::move(*i));
          i = layers.erase(i);
          continue;
      }
      i++;
  }
  int zpos = 0;
  for(auto &layer : layers){
    layer->iDrmZpos_ = zpos;
    zpos++;
  }

  zpos = 0;
  for(auto &layer : tmp_layers){
    layer->iDrmZpos_ = zpos;
    zpos++;
  }
  return;
}

void Vop3588::OutputMatchLayer(int iFirst, int iLast,
                                          std::vector<DrmHwcLayer *>& layers,
                                          std::vector<DrmHwcLayer *>& tmp_layers){

  if(iFirst < 0 || iLast < 0 || iFirst > iLast)
  {
      HWC2_ALOGD_IF_DEBUG("invalid value iFirst=%d, iLast=%d", iFirst, iLast);
      return;
  }

  int interval = layers.size()-1-iLast;
  ALOGD_IF(LogLevel(DBG_DEBUG), "OutputMatchLayer iFirst=%d,iLast,=%d,interval=%d",iFirst,iLast,interval);
  for (auto i = layers.begin() + iFirst; i != layers.end() - interval;)
  {
      //move gles layers
      tmp_layers.emplace_back(std::move(*i));
      i = layers.erase(i);
  }

  //add fb layer.
  int pos = iFirst;
  for (auto i = tmp_layers.begin(); i != tmp_layers.end();)
  {
      if((*i)->bFbTarget_){
          layers.insert(layers.begin() + pos, std::move(*i));
          pos++;
          i = tmp_layers.erase(i);
          continue;
      }
      i++;
  }
  int zpos = 0;
  for(auto &layer : layers){
    layer->iDrmZpos_ = zpos;
    zpos++;
  }
  return;
}
int Vop3588::TryOverlayPolicy(
    std::vector<DrmCompositionPlane> *composition,
    std::vector<DrmHwcLayer*> &layers, DrmCrtc *crtc,
    std::vector<PlaneGroup *> &plane_groups) {
  ALOGD_IF(LogLevel(DBG_DEBUG), "%s:line=%d",__FUNCTION__,__LINE__);
  std::vector<DrmHwcLayer*> tmp_layers;
  ResetLayer(layers);
  ResetPlaneGroups(plane_groups);
  //save fb into tmp_layers
  MoveFbToTmp(layers, tmp_layers);
  int ret = MatchPlanes(composition,layers,crtc,plane_groups);
  if(!ret)
    return ret;
  else{
    ResetLayerFromTmp(layers,tmp_layers);
    return -1;
  }
  return 0;
}

int Vop3588::TryRgaOverlayPolicy(
    std::vector<DrmCompositionPlane> *composition,
    std::vector<DrmHwcLayer*> &layers, DrmCrtc *crtc,
    std::vector<PlaneGroup *> &plane_groups) {
  if(!ctx.state.bRgaPolicyEnable){
    HWC2_ALOGD_IF_DEBUG("bRgaPolicyEnable=%d skip TryRgaOverlayPolicy", ctx.state.bRgaPolicyEnable);
    return -1;
  }
  ALOGD_IF(LogLevel(DBG_DEBUG), "%s:line=%d",__FUNCTION__,__LINE__);
  std::vector<DrmHwcLayer*> tmp_layers;
  ResetLayer(layers);
  ResetPlaneGroups(plane_groups);

  bool rga_layer_ready = false;
  bool use_laster_rga_layer = false;
  std::shared_ptr<DrmBuffer> dst_buffer;
  static uint64_t last_buffer_id = 0;
  int releaseFence = -1;
  rga_buffer_t src;
  rga_buffer_t dst;
  rga_buffer_t pat;
  im_rect src_rect;
  im_rect dst_rect;
  im_rect pat_rect;
  memset(&src, 0, sizeof(rga_buffer_t));
  memset(&dst, 0, sizeof(rga_buffer_t));
  memset(&pat, 0, sizeof(rga_buffer_t));
  memset(&src_rect, 0, sizeof(im_rect));
  memset(&dst_rect, 0, sizeof(im_rect));
  memset(&pat_rect, 0, sizeof(im_rect));
  int usage = 0;

  for(auto &drmLayer : layers){
    if(drmLayer->bYuv_){
        if(last_buffer_id != drmLayer->uBufferId_){
          // TODO: afbc 暂时不支持 crop 裁剪，目前会出现RGA输出花屏问题
          // 2023/08/24 删除这部分限制, 最新版本可能已经支持
          // if(drmLayer->bAfbcd_){
          //   int crop_w =  (int)(drmLayer->source_crop.right - drmLayer->source_crop.left);
          //   if(crop_w != drmLayer->iStride_){
          //     HWC2_ALOGD_IF_DEBUG("RGA can't handle crop_w=%d stride=%d afbc yuv layer.",
          //                crop_w, drmLayer->iStride_);
          //     continue;
          //   }
          // }

          // TODO: RGA 最大宽度仅支持8176
          if(drmLayer->iWidth_ > 8176){
            HWC2_ALOGD_IF_DEBUG("RGA can't handle iWidth_=%d yuv layer, rga max is 8176.",
                        drmLayer->iWidth_);
            continue;
          }

          bool rga_scale_max = false;
          // RGA 有缩放倍数限制
          if((drmLayer->fHScaleMul_ < 0.125 ||
              drmLayer->fHScaleMul_ > 8.0   ||
              drmLayer->fVScaleMul_ < 0.125 ||
              drmLayer->fVScaleMul_ > 8.0)){
              rga_scale_max = true;
          }

          bool yuv_10bit = false;
          switch(drmLayer->iFormat_){
          case HAL_PIXEL_FORMAT_YUV420_10BIT_I:
          case HAL_PIXEL_FORMAT_YCrCb_NV12_10:
            yuv_10bit = true;
            break;
          default:
            break;
          }

          if(yuv_10bit){
            // RGA 内部特殊修改，需要满足byte_stride 64对齐，width 2对齐
            dst_buffer = rgaBufferQueue_->DequeueDrmBuffer(ALIGN(ctx.state.iDisplayWidth_, 2),
                                                           ctx.state.iDisplayHeight_,
                                                           HAL_PIXEL_FORMAT_YCrCb_NV12_10,
                                                           RK_GRALLOC_USAGE_STRIDE_ALIGN_64 |
                                                           MALI_GRALLOC_USAGE_NO_AFBC |
                                                           RK_GRALLOC_USAGE_WITHIN_4G,
                                                           "RGA-SurfaceView");
          }else{
            dst_buffer = rgaBufferQueue_->DequeueDrmBuffer(ctx.state.iDisplayWidth_,
                                                           ctx.state.iDisplayHeight_,
                                                           HAL_PIXEL_FORMAT_YCrCb_NV12,
                                                           RK_GRALLOC_USAGE_STRIDE_ALIGN_16 |
                                                           MALI_GRALLOC_USAGE_NO_AFBC |
                                                           RK_GRALLOC_USAGE_WITHIN_4G,
                                                           "RGA-SurfaceView");

          }

          if(dst_buffer == NULL){
            HWC2_ALOGD_IF_DEBUG("DequeueDrmBuffer fail!, skip this policy.");
            continue;
          }

          // Set src buffer info
          src.fd      = drmLayer->iFd_;
          src.width   = drmLayer->iWidth_;
          src.height  = drmLayer->iHeight_;
          src.hstride = drmLayer->iHeightStride_;
          src.format  = drmLayer->iFormat_;

          // RGA 的特殊修改，需要通过 wstride
          if(drmLayer->uFourccFormat_ == DRM_FORMAT_NV15)
            src.wstride = drmLayer->iByteStride_;
          else
            src.wstride = drmLayer->iStride_;

          if(drmLayer->iFormat_ == HAL_PIXEL_FORMAT_YUV420_8BIT_I){
            src.format = HAL_PIXEL_FORMAT_YCrCb_NV12;
          }else if(drmLayer->iFormat_ == HAL_PIXEL_FORMAT_YUV420_10BIT_I){
            src.format = HAL_PIXEL_FORMAT_YCrCb_NV12_10;
          }

          // AFBC format
          if(drmLayer->bAfbcd_)
            src.rd_mode = IM_FBC_MODE;

          // Set src rect info
          src_rect.x = ALIGN_DOWN((int)drmLayer->source_crop.left,2);
          src_rect.y = ALIGN_DOWN((int)drmLayer->source_crop.top,2);
          src_rect.width  = ALIGN_DOWN((int)(drmLayer->source_crop.right  - drmLayer->source_crop.left),2);
          src_rect.height = ALIGN_DOWN((int)(drmLayer->source_crop.bottom - drmLayer->source_crop.top),2);

          // Set dst buffer info
          dst.fd      = dst_buffer->GetFd();
          dst.width   = dst_buffer->GetWidth();
          dst.height  = dst_buffer->GetHeight();
          // RGA 的特殊修改，需要通过 wstride
          if(dst_buffer->GetFourccFormat() == DRM_FORMAT_NV15)
            dst.wstride = dst_buffer->GetByteStride();
          else
            dst.wstride = dst_buffer->GetStride();

          dst.hstride = dst_buffer->GetHeightStride();
          dst.format  = dst_buffer->GetFormat();

          // AFBC format
          if(0)
            dst.rd_mode = IM_FBC_MODE;

          // 若缩放倍数超出RGA最大缩小倍数，则进行二次缩放，倍率设置为6
          if(rga_scale_max){
            int scale_max_rate = 4;

            // Set dst rect info
            dst_rect.x = 0;
            dst_rect.y = 0;
            dst_rect.width  = ALIGN_DOWN((int)(drmLayer->source_crop.right
                                                - drmLayer->source_crop.left) / scale_max_rate,2);
            dst_rect.height = ALIGN_DOWN((int)(drmLayer->source_crop.bottom
                                                - drmLayer->source_crop.top) / scale_max_rate,2);
          }else{
            // Set dst rect info
            dst_rect.x = 0;
            dst_rect.y = 0;
            dst_rect.width  = ALIGN_DOWN((int)(drmLayer->display_frame.right  - drmLayer->display_frame.left),2);
            dst_rect.height = ALIGN_DOWN((int)(drmLayer->display_frame.bottom - drmLayer->display_frame.top),2);
          }

          // 处理旋转
          switch(drmLayer->transform){
          case DRM_MODE_ROTATE_0:
            usage = 0;
            break;
          case DRM_MODE_ROTATE_0 | DRM_MODE_REFLECT_X:
            usage = IM_HAL_TRANSFORM_FLIP_H;
            break;
          case DRM_MODE_ROTATE_0 | DRM_MODE_REFLECT_Y:
            usage = IM_HAL_TRANSFORM_FLIP_V;
            break;
          case DRM_MODE_ROTATE_90:
            usage = IM_HAL_TRANSFORM_ROT_90;
            break;
          case DRM_MODE_ROTATE_0 | DRM_MODE_REFLECT_X | DRM_MODE_REFLECT_Y:
            usage = IM_HAL_TRANSFORM_ROT_180;
            break;
          case DRM_MODE_ROTATE_270:
            usage = IM_HAL_TRANSFORM_ROT_270;
            break;
          // RGA2/RGA3的 flip + rotate 场景，硬件内部处理是先 rotate 再 flip
          // 而 Android 请求的是先 flip 再 rotate，故此请求需要做转换
          // Android请求 flip-v + rotate-90  等价于 rotate-90 + flip-h
          case DRM_MODE_ROTATE_0 | DRM_MODE_REFLECT_Y | DRM_MODE_ROTATE_90 :
            usage = IM_HAL_TRANSFORM_ROT_90 | IM_HAL_TRANSFORM_FLIP_H ;
            break;
          // Android请求 flip-h + rotate-90  等价于 rotate-90 + flip-v
          case DRM_MODE_ROTATE_0 | DRM_MODE_REFLECT_X | DRM_MODE_ROTATE_90:
            usage = IM_HAL_TRANSFORM_ROT_90 | IM_HAL_TRANSFORM_FLIP_V;
            break;
          default:
            usage = 0;
            ALOGE_IF(LogLevel(DBG_DEBUG),"Unknow sf transform 0x%x", drmLayer->transform);
          }

          IM_STATUS im_state;
          // Call Im2d 格式转换
          im_state = imcheck_composite(src, dst, pat, src_rect, dst_rect, pat_rect, usage | IM_ASYNC);
          if(im_state != IM_STATUS_NOERROR){
            HWC2_ALOGE("call im2d scale fail, %s",imStrError(im_state));
            break;
          }

          hwc_frect_t source_crop;
          source_crop.left   = dst_rect.x;
          source_crop.top    = dst_rect.y;
          source_crop.right  = dst_rect.x + dst_rect.width;
          source_crop.bottom = dst_rect.y + dst_rect.height;
          drmLayer->UpdateAndStoreInfoFromDrmBuffer(dst_buffer->GetHandle(),
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
                                                    DRM_MODE_ROTATE_0);
          rga_layer_ready = true;
          drmLayer->iBestPlaneType = PLANE_RK3588_ALL_ESMART_MASK;
          drmLayer->pRgaBuffer_ = dst_buffer;
          drmLayer->bUseRga_ = true;
          break;
        }else{
          dst_buffer = rgaBufferQueue_->BackDrmBuffer();

          if(dst_buffer == NULL){
            HWC2_ALOGD_IF_DEBUG("DequeueDrmBuffer fail!, skip this policy.");
            break;
          }

          hwc_frect_t source_crop;
          source_crop.left  = 0;
          source_crop.top   = 0;
          source_crop.right =   ALIGN_DOWN((int)(drmLayer->display_frame.right  - drmLayer->display_frame.left),2);
          source_crop.bottom  = ALIGN_DOWN((int)(drmLayer->display_frame.bottom - drmLayer->display_frame.top),2);
          drmLayer->UpdateAndStoreInfoFromDrmBuffer(dst_buffer->GetHandle(),
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
                                                    DRM_MODE_ROTATE_0);
          use_laster_rga_layer = true;
          drmLayer->bUseRga_ = true;
          drmLayer->iBestPlaneType = PLANE_RK3588_ALL_ESMART_MASK;
          drmLayer->pRgaBuffer_ = dst_buffer;
          break;
        }
      }
  }
  if(rga_layer_ready){
    ALOGD_IF(LogLevel(DBG_DEBUG), "%s:line=%d rga layer ready, to matchPlanes",__FUNCTION__,__LINE__);
    int ret = 0;
    if(ctx.request.iSkipCnt > 0){
      ret = TryMixSkipPolicy(composition,layers,crtc,plane_groups);
    }else{
      ret = TryOverlayPolicy(composition,layers,crtc,plane_groups);
      if(ret){
        ret = TryMixVideoPolicy(composition,layers,crtc,plane_groups);
      }
    }
    if(!ret){ // Match sucess, to call im2d interface
      for(auto &drmLayer : layers){
        if(drmLayer->bUseRga_){
          im_opt_t imOpt;
          memset(&imOpt, 0x00, sizeof(im_opt_t));
          imOpt.core = IM_SCHEDULER_RGA3_CORE0 | IM_SCHEDULER_RGA3_CORE1;

          IM_STATUS im_state = improcess(src, dst, pat, src_rect, dst_rect, pat_rect, 0, &releaseFence, &imOpt, usage | IM_ASYNC);
          if(im_state != IM_STATUS_SUCCESS){
            HWC2_ALOGE("call im2d scale fail, %s",imStrError(im_state));
            rgaBufferQueue_->QueueBuffer(dst_buffer);
            drmLayer->ResetInfoFromStore();
            drmLayer->bUseRga_ = false;
            ret = -1;
            break;
          }
          dst_buffer->SetFinishFence(dup(releaseFence));
          drmLayer->pRgaBuffer_ = dst_buffer;
          drmLayer->acquire_fence = sp<AcquireFence>(new AcquireFence(releaseFence));
          rgaBufferQueue_->QueueBuffer(dst_buffer);
          last_buffer_id = drmLayer->uBufferId_;
          return ret;
        }
      }
      ResetLayerFromTmp(layers,tmp_layers);
      return ret;
    }else{ // Match fail, skip rga policy
      HWC2_ALOGD_IF_DEBUG(" MatchPlanes fail! reset DrmHwcLayer.");
      for(auto &drmLayer : layers){
        if(drmLayer->bUseRga_){
          rgaBufferQueue_->QueueBuffer(dst_buffer);
          drmLayer->ResetInfoFromStore();
          drmLayer->bUseRga_ = false;
        }
      }
      ResetLayerFromTmp(layers,tmp_layers);
      return -1;
    }
  }else if(use_laster_rga_layer){
    ALOGD_IF(LogLevel(DBG_DEBUG), "%s:line=%d rga layer ready, to matchPlanes",__FUNCTION__,__LINE__);
    int ret = -1;
    if(ctx.request.iSkipCnt > 0){
      ret = TryMixSkipPolicy(composition,layers,crtc,plane_groups);
    }else{
      ret = TryOverlayPolicy(composition,layers,crtc,plane_groups);
      if(ret){
        ret = TryMixVideoPolicy(composition,layers,crtc,plane_groups);
      }
    }
    if(!ret){ // Match sucess, to call im2d interface
      HWC2_ALOGD_IF_DEBUG("Use last rga layer.");
      return ret;
    }
  }
  HWC2_ALOGD_IF_DEBUG("fail!, No layer use RGA policy.");
  ResetLayerFromTmp(layers,tmp_layers);
  return -1;
}

/*************************mix SidebandStream*************************
   DisplayId=0, Connector 345, Type = HDMI-A-1, Connector state = DRM_MODE_CONNECTED , frame_no = 6611
  ------+-----+-----------+-----------+--------------------+-------------+------------+--------------------------------+------------------------+------------+------------
    id  |  z  |  sf-type  |  hwc-type |       handle       |  transform  |    blnd    |     source crop (l,t,r,b)      |          frame         | dataspace  | name
  ------+-----+-----------+-----------+--------------------+-------------+------------+--------------------------------+------------------------+------------+------------
   0050 | 000 |  Sideband |    Device | 000000000000000000 | None        | None       |    0.0,    0.0,   -1.0,   -1.0 |    0,    0, 1920, 1080 |          0 | allocateBuffer
   0059 | 001 |    Device |    Client | 00b40000751ec3ec30 | None        | Premultipl | 1829.0,   20.0, 1900.0,   59.0 | 1829,   20, 1900,   59 |          0 | com.tencent.start.tv/com.tencent.start.ui.PlayActivity#0
   0071 | 002 |    Device |    Client | 00b40000751ec403d0 | None        | Premultipl |    0.0,    0.0,  412.0, 1080.0 | 1508,    0, 1920, 1080 |          0 | PopupWindow:55de2f2#0
  ------+-----+-----------+-----------+--------------------+-------------+------------+--------------------------------+------------------------+------------+------------
************************************************************/
int Vop3588::TryGlesSidebandPolicy(
    std::vector<DrmCompositionPlane> *composition,
    std::vector<DrmHwcLayer*> &layers, DrmCrtc *crtc,
    std::vector<PlaneGroup *> &plane_groups) {
  ALOGD_IF(LogLevel(DBG_DEBUG), "%s:line=%d",__FUNCTION__,__LINE__);
  ResetLayer(layers);
  ResetPlaneGroups(plane_groups);
  std::vector<DrmHwcLayer *> tmp_layers;
  //save fb into tmp_layers
  MoveFbToTmp(layers, tmp_layers);

  std::pair<int, int> layer_indices(-1, -1);

  int sideband_index = -1;
  for(auto& layer : layers){
    if(layer->bSidebandStreamLayer_){
      sideband_index = layer->iDrmZpos_;
    }
  }
  if(sideband_index != 0){
    ALOGD_IF(LogLevel(DBG_DEBUG), "%s:gles sideband index (%d), skip!",__FUNCTION__, sideband_index);
    ResetLayerFromTmp(layers,tmp_layers);
    return -1;
  }

  if((layers.size() - 1) >= 1){
    layer_indices.first = sideband_index + 1;
    layer_indices.second = layers.size() - 1;
  }

  ALOGD_IF(LogLevel(DBG_DEBUG), "%s:gles sideband (%d,%d)",__FUNCTION__,layer_indices.first, layer_indices.second);
  OutputMatchLayer(layer_indices.first, layer_indices.second, layers, tmp_layers);
  int ret = MatchPlanes(composition,layers,crtc,plane_groups);
  if(!ret){
    return ret;
  }

  ResetLayerFromTmp(layers,tmp_layers);
  return ret;
}


/*************************AcceleratePolicy*************************
   DisplayId=0, Connector 345, Type = HDMI-A-1, Connector state = DRM_MODE_CONNECTED , frame_no = 6611
  ------+-----+-----------+-----------+--------------------+-------------+------------+--------------------------------+------------------------+------------+------------
    id  |  z  |  sf-type  |  hwc-type |       handle       |  transform  |    blnd    |     source crop (l,t,r,b)      |          frame         | dataspace  | name
  ------+-----+-----------+-----------+--------------------+-------------+------------+--------------------------------+------------------------+------------+------------
   0050 | 000 |  Sideband |    Device | 000000000000000000 | None        | None       |    0.0,    0.0,   -1.0,   -1.0 |    0,    0, 1920, 1080 |          0 | allocateBuffer
   0059 | 001 |    Device |    Client | 00b40000751ec3ec30 | None        | Premultipl | 1829.0,   20.0, 1900.0,   59.0 | 1829,   20, 1900,   59 |          0 | com.tencent.start.tv/com.tencent.start.ui.PlayActivity#0
   0071 | 002 |    Device |    Device | 00b40000751ec403d0 | None        | Premultipl |    0.0,    0.0,  412.0, 1080.0 | 1508,    0, 1920, 1080 |          0 | rk_handwrite_sf
  ------+-----+-----------+-----------+--------------------+-------------+------------+--------------------------------+------------------------+------------+------------
************************************************************/
int Vop3588::TryAcceleratePolicy(
    std::vector<DrmCompositionPlane> *composition,
    std::vector<DrmHwcLayer*> &layers, DrmCrtc *crtc,
    std::vector<PlaneGroup *> &plane_groups) {
  ALOGD_IF(LogLevel(DBG_DEBUG), "%s:line=%d",__FUNCTION__,__LINE__);
  ResetLayer(layers);
  ResetPlaneGroups(plane_groups);
  std::vector<DrmHwcLayer *> tmp_layers;
  //save fb into tmp_layers
  MoveFbToTmp(layers, tmp_layers);

  std::pair<int, int> layer_indices(-1, -1);

  // 找到
  int accelerate_index = -1;
  for(auto& layer : layers){
    if(layer->bAccelerateLayer_){
      accelerate_index = layer->iDrmZpos_;
      break;
    }
  }

  // 两层及以上的手写加速图层
  if(layers.size() >= 2){
    // 取手写图层下一层作为 mix 初始图层
    layer_indices.first = accelerate_index - 2;
    if(layers.size() == 2){
      // 若只有两个图层，则取第
      layer_indices.second = layer_indices.first;
    }else{
      layer_indices.second = accelerate_index - 1;
    }
  }

  ALOGD_IF(LogLevel(DBG_DEBUG), "%s:mix accelerate layer (%d,%d)",__FUNCTION__,layer_indices.first, layer_indices.second);
  OutputMatchLayer(layer_indices.first, layer_indices.second, layers, tmp_layers);
  int ret = MatchPlanes(composition,layers,crtc,plane_groups);
  if(!ret)
    return ret;
  else{
    ResetLayerFromTmpExceptFB(layers,tmp_layers);
    for(--layer_indices.first; layer_indices.first > 0; --layer_indices.first){
      ALOGD_IF(LogLevel(DBG_DEBUG), "%s:mix accelerate layer (%d,%d)",__FUNCTION__,layer_indices.first, layer_indices.second);
      OutputMatchLayer(layer_indices.first, layer_indices.second, layers, tmp_layers);
      ret = MatchPlanes(composition,layers,crtc,plane_groups);
      if(!ret)
        return ret;
      else{
        ResetLayerFromTmpExceptFB(layers,tmp_layers);
        continue;
      }
    }
  }
  ResetLayerFromTmp(layers,tmp_layers);
  return ret;
}

/*************************mix SidebandStream*************************
   DisplayId=0, Connector 345, Type = HDMI-A-1, Connector state = DRM_MODE_CONNECTED , frame_no = 6611
  ------+-----+-----------+-----------+--------------------+-------------+------------+--------------------------------+------------------------+------------+------------
    id  |  z  |  sf-type  |  hwc-type |       handle       |  transform  |    blnd    |     source crop (l,t,r,b)      |          frame         | dataspace  | name
  ------+-----+-----------+-----------+--------------------+-------------+------------+--------------------------------+------------------------+------------+------------
   0050 | 000 |  Sideband |    Device | 000000000000000000 | None        | None       |    0.0,    0.0,   -1.0,   -1.0 |    0,    0, 1920, 1080 |          0 | allocateBuffer
   0059 | 001 |    Device |    Client | 00b40000751ec3ec30 | None        | Premultipl | 1829.0,   20.0, 1900.0,   59.0 | 1829,   20, 1900,   59 |          0 | com.tencent.start.tv/com.tencent.start.ui.PlayActivity#0
   0071 | 002 |    Device |    Client | 00b40000751ec403d0 | None        | Premultipl |    0.0,    0.0,  412.0, 1080.0 | 1508,    0, 1920, 1080 |          0 | PopupWindow:55de2f2#0
  ------+-----+-----------+-----------+--------------------+-------------+------------+--------------------------------+------------------------+------------+------------
************************************************************/
int Vop3588::TryMixSidebandPolicy(
    std::vector<DrmCompositionPlane> *composition,
    std::vector<DrmHwcLayer*> &layers, DrmCrtc *crtc,
    std::vector<PlaneGroup *> &plane_groups) {
  ALOGD_IF(LogLevel(DBG_DEBUG), "%s:line=%d",__FUNCTION__,__LINE__);
  ResetLayer(layers);
  ResetPlaneGroups(plane_groups);
  std::vector<DrmHwcLayer *> tmp_layers;
  //save fb into tmp_layers
  MoveFbToTmp(layers, tmp_layers);

  std::pair<int, int> layer_indices(-1, -1);

  if((int)layers.size() < 4)
    layer_indices.first = layers.size() - 2 <= 0 ? 1 : layers.size() - 2;
  else
    layer_indices.first = 3;

  layer_indices.second = layers.size() - 1;
  ALOGD_IF(LogLevel(DBG_DEBUG), "%s:mix sideband (%d,%d)",__FUNCTION__,layer_indices.first, layer_indices.second);
  OutputMatchLayer(layer_indices.first, layer_indices.second, layers, tmp_layers);
  int ret = MatchPlanes(composition,layers,crtc,plane_groups);
  if(!ret)
    return ret;
  else{
    ResetLayerFromTmpExceptFB(layers,tmp_layers);
    for(--layer_indices.first; layer_indices.first > 0; --layer_indices.first){
      ResetLayerFromTmpExceptFB(layers,tmp_layers);
      ALOGD_IF(LogLevel(DBG_DEBUG), "%s:mix sideband (%d,%d)",__FUNCTION__,layer_indices.first, layer_indices.second);
      OutputMatchLayer(layer_indices.first, layer_indices.second, layers, tmp_layers);
      ret = MatchPlanes(composition,layers,crtc,plane_groups);
      if(!ret)
        return ret;
      else{
        ResetLayerFromTmp(layers,tmp_layers);
     }
   }
 }

  ResetLayerFromTmp(layers,tmp_layers);
  return ret;
}

int Vop3588::TryMixSkipPolicy(
    std::vector<DrmCompositionPlane> *composition,
    std::vector<DrmHwcLayer*> &layers, DrmCrtc *crtc,
    std::vector<PlaneGroup *> &plane_groups) {
  ALOGD_IF(LogLevel(DBG_DEBUG), "%s:line=%d",__FUNCTION__,__LINE__);
  ResetLayer(layers);
  ResetPlaneGroups(plane_groups);

  int iPlaneSize = plane_groups.size();

  if(iPlaneSize == 0){
    ALOGE_IF(LogLevel(DBG_DEBUG), "%s:line=%d, iPlaneSize = %d, skip TryMixSkipPolicy",
              __FUNCTION__,__LINE__,iPlaneSize);
  }

  std::vector<DrmHwcLayer *> tmp_layers;
  // Since we can't composite HWC_SKIP_LAYERs by ourselves, we'll let SF
  // handle all layers in between the first and last skip layers. So find the
  // outer indices and mark everything in between as HWC_FRAMEBUFFER
  std::pair<int, int> skip_layer_indices(-1, -1);

  //save fb into tmp_layers
  MoveFbToTmp(layers, tmp_layers);

  //caculate the first and last skip layer
  int i = 0;
  for (auto &layer : layers) {
    if (!layer->bSkipLayer_ && !layer->bGlesCompose_){
      i++;
      continue;
    }

    if (skip_layer_indices.first == -1)
      skip_layer_indices.first = i;
    skip_layer_indices.second = i;
    i++;
  }

  if(skip_layer_indices.first != -1){
    int skipCnt = skip_layer_indices.second - skip_layer_indices.first + 1;
    ALOGE_IF(LogLevel(DBG_DEBUG), "%s:line=%d, skipCnt = %d, first = %d, second = %d",
              __FUNCTION__, __LINE__, skipCnt, skip_layer_indices.first, skip_layer_indices.second);
  }else{
    ALOGE_IF(LogLevel(DBG_DEBUG), "%s:line=%d, can't find any skip layer, first = %d, second = %d",
              __FUNCTION__,__LINE__,skip_layer_indices.first,skip_layer_indices.second);
    ResetLayerFromTmp(layers,tmp_layers);
    return -1;
  }

  HWC2_ALOGD_IF_DEBUG("mix skip (%d,%d)",skip_layer_indices.first, skip_layer_indices.second);
  OutputMatchLayer(skip_layer_indices.first, skip_layer_indices.second, layers, tmp_layers);
  int ret = MatchPlanes(composition,layers,crtc,plane_groups);
  if(!ret){
    return ret;
  }else{
    ResetLayerFromTmpExceptFB(layers,tmp_layers);
    int first = skip_layer_indices.first;
    int last = skip_layer_indices.second;
    // 建议zpos大的图层走GPU合成
    for(last++; last < layers.size(); last++){
      HWC2_ALOGD_IF_DEBUG("mix skip (%d,%d)",skip_layer_indices.first, skip_layer_indices.second);
      OutputMatchLayer(first, last, layers, tmp_layers);
      ret = MatchPlanes(composition,layers,crtc,plane_groups);
      if(ret){
        ALOGD_IF(LogLevel(DBG_DEBUG), "%s:line=%d fail match (%d,%d)",__FUNCTION__,__LINE__,first, last);
        ResetLayerFromTmpExceptFB(layers,tmp_layers);
        continue;
      }else{
        return ret;
      }
    }

    last = layers.size() - 1;
    // 逐步建议知道zpos=0走GPU合成，即全GPU合成
    for(first--; first >= 0; first--){
      HWC2_ALOGD_IF_DEBUG("mix skip (%d,%d)",skip_layer_indices.first, skip_layer_indices.second);
      OutputMatchLayer(first, last, layers, tmp_layers);
      ret = MatchPlanes(composition,layers,crtc,plane_groups);
      if(ret){
        ALOGD_IF(LogLevel(DBG_DEBUG), "%s:line=%d fail match (%d,%d)",__FUNCTION__,__LINE__,first, last);
        ResetLayerFromTmpExceptFB(layers,tmp_layers);
        continue;
      }else{
        return ret;
      }
    }
  }
  ResetLayerFromTmp(layers,tmp_layers);
  return ret;
}
#if (defined USE_LIBSR) || (defined USE_LIBSVEP_MEMC)
int Vop3588::TrySvepPolicy(
    std::vector<DrmCompositionPlane> *composition,
    std::vector<DrmHwcLayer*> &layers, DrmCrtc *crtc,
    std::vector<PlaneGroup *> &plane_groups) {
  ALOGD_IF(LogLevel(DBG_DEBUG), "%s:line=%d",__FUNCTION__,__LINE__);

  DrmDevice *drm = crtc->getDrmDevice();
  DrmConnector *conn = drm->GetConnectorForDisplay(crtc->display());
  // 只有主屏可以享受视频 SR 效果
  if(conn && conn->state() == DRM_MODE_CONNECTED &&
      conn->display() != 0){
      HWC2_ALOGD_IF_DEBUG("Only Primary Display enable SR function. display=%d", conn->display());
      return -1;
  }

  int ret = -1;
#ifdef USE_LIBSR
  if(hwc_get_int_property(SR_MODE_NAME, "0") > 0){
    ret = TrySrPolicy(composition, layers, crtc, plane_groups);
    if(ret){
      HWC2_ALOGD_IF_DEBUG("TrySrPolicy match fail.");
    }else{
      HWC2_ALOGD_IF_DEBUG("TrySrPolicy match success.");
#ifdef USE_LIBSVEP_MEMC
      ClearMemcJob();
#endif
      return ret;
    }
  }
#endif

#ifdef USE_LIBSVEP_MEMC
  if(hwc_get_int_property(MEMC_MODE_NAME, "0") > 0){
    ret = TryMemcPolicy(composition, layers, crtc, plane_groups);
    if(ret){
      HWC2_ALOGD_IF_DEBUG("TrySrPolicy match fail.");
      ClearMemcJob();
    }else{
      HWC2_ALOGD_IF_DEBUG("TryMemcPolicy match success.");
      return ret;
    }
  }
#endif

  return -1;
}

bool Vop3588::TrySvepOverlay(){
  ctx.state.setHwcPolicy.insert(HWC_SR_OVERLAY_LOPICY);
  return true;
}

#endif

#ifdef USE_LIBSR
int Vop3588::TrySrPolicy(std::vector<DrmCompositionPlane> *composition,
                      std::vector<DrmHwcLayer*> &layers, DrmCrtc *crtc,
                      std::vector<PlaneGroup *> &plane_groups){
  ALOGD_IF(LogLevel(DBG_DEBUG), "%s:line=%d",__FUNCTION__,__LINE__);
  std::vector<DrmHwcLayer*> tmp_layers;
  ResetLayer(layers);
  ResetPlaneGroups(plane_groups);

  int svep_mode = HWC2_SR_SR;
  char value[PROPERTY_VALUE_MAX];
  // SR_RUNTIME_DISABLE_NAME 主要适用于前端系统服务判断当前场景无法使用SR模式才设置为 1
  // 例如 30帧以上片源 或 低延迟场景
  int svep_runtime_disable = hwc_get_int_property(SR_RUNTIME_DISABLE_NAME,"0");
  bool sr_mode = false;
  // Match policy first
  HWC2_ALOGD_IF_DEBUG("%s=%d bSrReady_=%d",SR_MODE_NAME, svep_mode, bSrReady_);
  // 只有主屏可以享受视频 SR 效果
  if(svep_runtime_disable == 0){
    // Match policy first
    sr_mode = true;
  }

  static bool last_sr_mode = false;
  if(!sr_mode){
    last_sr_mode = sr_mode;
    return -1;
  }

  // 0. SR模块初始化
  if(svep_sr_.get() != NULL){
    SrError error = svep_sr_->Init(SR_VERSION, true);
    if (error != SrError::None){
        HWC2_ALOGD_IF_DEBUG("Sr Init fail, plase check License.\n");
        return -1;
    }
  }else{
    bSrReady_ = true;
  }

  bool rga_layer_ready = false;
  bool use_laster_rga_layer = false;
  std::shared_ptr<DrmBuffer> dst_buffer;

  SrImageInfo sr_src_;
  SrImageInfo sr_dst_;

  // 以下参数更新后需要强制触发svep处理更新图像数据
  property_get(SR_ENHANCEMENT_RATE_NAME, value, "0");
  int enhancement_rate = atoi(value);
  property_get(SR_CONTRAST_MODE_NAME, value, "0");
  int contrast_mode = atoi(value);
  property_get(SR_CONTRAST_MODE_OFFSET, value, "0");
  int contrast_offset = atoi(value);
  property_get(SR_OSD_DISABLE_MODE, value, "0");
  int diable_osd_mode = atoi(value);
  property_get(SR_OSD_VIDEO_ONELINE_MODE, value, "0");
  int osd_oneline_mode = atoi(value);
  property_get(SR_OSD_VIDEO_ONELINE_WATI_SEC, value, "12");
  int osd_oneline_wait_second = atoi(value);
  static uint64_t last_buffer_id = 0;
  static int last_enhancement_rate = 0;
  static int last_contrast_mode = 0;
  static int last_contrast_offset = 0;

  for(auto &drmLayer : layers){
    if(SvepSrAllowedByLocalPolicy(drmLayer) &&
       SvepSrAllowedByBlacklist(drmLayer)){
        ALOGD_IF(LogLevel(DBG_DEBUG), "%s:line=%d",__FUNCTION__,__LINE__);
        // 部分参数变化后需要强制更新
        if(last_sr_mode != sr_mode ||
           last_buffer_id != drmLayer->uBufferId_  ||
           last_enhancement_rate != enhancement_rate ||
           last_contrast_mode != contrast_mode ||
           last_contrast_offset != contrast_offset){
          ALOGD_IF(LogLevel(DBG_DEBUG), "%s:line=%d",__FUNCTION__,__LINE__);

          // 2. 设置SR强度
          SrError error = svep_sr_->SetEnhancementRate(enhancement_rate);
          if (error != SrError::None)
          {
              HWC2_ALOGE("Sr SetEnhancementRate fail.\n");
              continue;
          }

          // 3. 设置SR对比模式为扫描模式
          error = svep_sr_->SetContrastMode(contrast_mode, contrast_offset);
          if (error != SrError::None)
          {
              HWC2_ALOGE("Sr SetContrastMode fail.\n");
              continue;
          }

          // 4. 设置SR OSD模式与字符串
          error = svep_sr_->SetOsdMode(SR_OSD_ENABLE_VIDEO, SR_OSD_VIDEO_STR);
          if (error != SrError::None)
          {
              HWC2_ALOGE("Sr SetOsdMode fail.\n");
              continue;
          }

          // 处理旋转
          SrRotateMode rotate = SR_ROTATE_0;
          switch(drmLayer->transform){
          case DRM_MODE_ROTATE_0:
            rotate = SR_ROTATE_0;
            break;
          case DRM_MODE_ROTATE_0 | DRM_MODE_REFLECT_X :
            rotate = SR_REFLECT_X;
            break;
          case DRM_MODE_ROTATE_0 | DRM_MODE_REFLECT_Y:
            rotate = SR_REFLECT_Y;
            break;
          case DRM_MODE_ROTATE_90:
            rotate = SR_ROTATE_90;
            break;
          case DRM_MODE_ROTATE_0 | DRM_MODE_REFLECT_X | DRM_MODE_REFLECT_Y:
            rotate = SR_ROTATE_180;
            break;
          case DRM_MODE_ROTATE_270:
            rotate = SR_ROTATE_270;
            break;
          // case DRM_MODE_ROTATE_0 | DRM_MODE_REFLECT_X | DRM_MODE_ROTATE_90 :
          //   usage = IM_HAL_TRANSFORM_FLIP_H | IM_HAL_TRANSFORM_ROT_90;
          //   break;
          // case DRM_MODE_ROTATE_0 | DRM_MODE_REFLECT_Y | DRM_MODE_ROTATE_90:
          //   usage = IM_HAL_TRANSFORM_FLIP_V | IM_HAL_TRANSFORM_ROT_90;
          //   break;
          default:
            rotate = SR_ROTATE_0;
            ALOGE_IF(LogLevel(DBG_DEBUG),"Unknow sf transform 0x%x", drmLayer->transform);
          }

          // 4. 设置SR rotate 模式
          error = svep_sr_->SetRotateMode(rotate);
          if (error != SrError::None)
          {
              HWC2_ALOGE("Sr SetOsdMode fail.\n");
              continue;
          }

          // 2. Set buffer Info
          sr_src_.mBufferInfo_.iFd_     = drmLayer->iFd_;
          sr_src_.mBufferInfo_.iWidth_  = drmLayer->iWidth_;
          sr_src_.mBufferInfo_.iHeight_ = drmLayer->iHeight_;
          sr_src_.mBufferInfo_.iFormat_ = drmLayer->uFourccFormat_;
          sr_src_.mBufferInfo_.iStride_ = drmLayer->iStride_;
          sr_src_.mBufferInfo_.iSize_   = drmLayer->iSize_;
          sr_src_.mBufferInfo_.uBufferId_ = drmLayer->uBufferId_;
          sr_src_.mBufferInfo_.uColorSpace_ = (uint64_t)drmLayer->eDataSpace_;
          if(drmLayer->bAfbcd_){
            if(drmLayer->iFormat_ == HAL_PIXEL_FORMAT_YUV420_8BIT_I){
              sr_src_.mBufferInfo_.iFormat_ = drmLayer->uFourccFormat_;
            }
            sr_src_.mBufferInfo_.uMask_ = SR_AFBC_FORMATE;
          }

          sr_src_.mCrop_.iLeft_  = (int)drmLayer->source_crop.left;
          sr_src_.mCrop_.iTop_   = (int)drmLayer->source_crop.top;
          sr_src_.mCrop_.iRight_ = (int)drmLayer->source_crop.right;
          sr_src_.mCrop_.iBottom_= (int)drmLayer->source_crop.bottom;

          SrMode sr_mde = SrMode::UN_SUPPORT;
          SrError ret = svep_sr_->MatchSrMode(&sr_src_,
                                   (ctx.state.b8kMode_ ? SR_OUTPUT_8K_MODE : SR_MODE_NONE),
                                   &sr_mde);
          if(ret){
            printf("Sr SetSrcImage fail\n");
            continue;
          }

          // 3. Get dst info
          SrImageInfo target_image_info;
          error = svep_sr_->GetDetImageInfo(&target_image_info);
          if(error){
            printf("Sr GetDstRequireInfo fail\n");
            continue;
          }

          // 4. Alloc dst_buffer
            dst_buffer = bufferQueue_->DequeueDrmBuffer(target_image_info.mBufferInfo_.iWidth_,
                                                        target_image_info.mBufferInfo_.iHeight_,
                                                        HAL_PIXEL_FORMAT_YCrCb_NV12,
                                                        RK_GRALLOC_USAGE_STRIDE_ALIGN_64 |
                                                        RK_GRALLOC_USAGE_WITHIN_4G |
                                                        MALI_GRALLOC_USAGE_NO_AFBC,
                                                        "SR-SurfaceView",
                                                        drmLayer->uId_);

          if(dst_buffer == NULL){
            HWC2_ALOGD_IF_DEBUG("DequeueDrmBuffer fail!, skip this policy.");
            continue;
          }

          SrOsdMode osd_mode = SR_OSD_ENABLE_VIDEO;
          const wchar_t* osd_str = SR_OSD_VIDEO_STR;
          if(diable_osd_mode > 0){
            osd_mode = SR_OSD_DISABLE;
          }else{
            if(osd_oneline_mode > 0){
              // 视频播放SR若干帧后，采用oneline OSD模式
              if(mLastMode_ != sr_mde){
                struct timeval tp;
                gettimeofday(&tp, NULL);
                mLastMode_ = sr_mde;
                mSrBeginTimeMs_ = tp.tv_sec * 1000 + tp.tv_usec / 1000;
                mEnableOnelineMode_ = false;
              }
              if(!mEnableOnelineMode_){
                struct timeval tp;
                gettimeofday(&tp, NULL);
                uint64_t current_time = tp.tv_sec * 1000 + tp.tv_usec / 1000;
                if((current_time - mSrBeginTimeMs_) > osd_oneline_wait_second * 1000){
                  mEnableOnelineMode_ = true;
                }
              }else{
                osd_mode = SR_OSD_ENABLE_VIDEO_ONELINE;
                osd_str = SR_OSD_VIDEO_ONELINE_STR;
              }
            }
          }

          error = svep_sr_->SetOsdMode(osd_mode, osd_str);
          if (error != SrError::None)
          {
              HWC2_ALOGE("Sr SetOsdMode fail.\n");
              continue;
          }
          // 5. Set buffer Info
          sr_dst_.mBufferInfo_.iFd_     = dst_buffer->GetFd();
          sr_dst_.mBufferInfo_.iWidth_  = dst_buffer->GetWidth();
          sr_dst_.mBufferInfo_.iHeight_ = dst_buffer->GetHeight();
          sr_dst_.mBufferInfo_.iFormat_ = dst_buffer->GetFourccFormat();
          sr_dst_.mBufferInfo_.iStride_ = dst_buffer->GetStride();
          sr_dst_.mBufferInfo_.iSize_   = dst_buffer->GetSize();
          sr_dst_.mBufferInfo_.uBufferId_ = dst_buffer->GetBufferId();

          sr_dst_.mCrop_.iLeft_  = target_image_info.mCrop_.iLeft_;
          sr_dst_.mCrop_.iTop_   = target_image_info.mCrop_.iTop_;
          sr_dst_.mCrop_.iRight_ = target_image_info.mCrop_.iRight_;
          sr_dst_.mCrop_.iBottom_= target_image_info.mCrop_.iBottom_;


          hwc_frect_t source_crop;
          source_crop.left   = target_image_info.mCrop_.iLeft_;
          source_crop.top    = target_image_info.mCrop_.iTop_;
          source_crop.right  = target_image_info.mCrop_.iRight_;
          source_crop.bottom = target_image_info.mCrop_.iBottom_;
          dst_buffer->SetCrop(target_image_info.mCrop_.iLeft_,
                              target_image_info.mCrop_.iTop_,
                              target_image_info.mCrop_.iRight_,
                              target_image_info.mCrop_.iBottom_);
          drmLayer->UpdateAndStoreInfoFromDrmBuffer(dst_buffer->GetHandle(),
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
                                                    DRM_MODE_ROTATE_0);
          rga_layer_ready = true;
          drmLayer->bUseSr_ = true;
          drmLayer->iBestPlaneType = PLANE_RK3588_ALL_ESMART_MASK;
          break;
        }else{
          std::shared_ptr<DrmBuffer> output_buffer = bufferQueue_->BackDrmBuffer();
          if(output_buffer == NULL){
            HWC2_ALOGD_IF_DEBUG("DequeueDrmBuffer fail!, skip this policy.");
            break;
          }
          int left = 0, top = 0, right = 0, bottom = 0;
          output_buffer->GetCrop(&left,
                                 &top,
                                 &right,
                                 &bottom);
          hwc_frect_t source_crop;
          source_crop.left   = left;
          source_crop.top    = top;
          source_crop.right  = right;
          source_crop.bottom = bottom;
          drmLayer->UpdateAndStoreInfoFromDrmBuffer(output_buffer->GetHandle(),
                                                    output_buffer->GetFd(),
                                                    output_buffer->GetFormat(),
                                                    output_buffer->GetWidth(),
                                                    output_buffer->GetHeight(),
                                                    output_buffer->GetStride(),
                                                    output_buffer->GetHeightStride(),
                                                    output_buffer->GetByteStride(),
                                                    output_buffer->GetSize(),
                                                    output_buffer->GetUsage(),
                                                    output_buffer->GetFourccFormat(),
                                                    output_buffer->GetModifier(),
                                                    output_buffer->GetByteStridePlanes(),
                                                    output_buffer->GetName(),
                                                    source_crop,
                                                    output_buffer->GetBufferId(),
                                                    output_buffer->GetGemHandle(),
                                                    DRM_MODE_ROTATE_0);
          use_laster_rga_layer = true;
          drmLayer->bUseSr_ = true;
          drmLayer->iBestPlaneType = PLANE_RK3588_ALL_ESMART_MASK;
          drmLayer->pSrBuffer_ = output_buffer;
          drmLayer->acquire_fence = sp<AcquireFence>(new AcquireFence(output_buffer->GetFinishFence()));
          break;
        }
      }
  }
  if(rga_layer_ready){
    ALOGD_IF(LogLevel(DBG_DEBUG), "%s:line=%d rga layer ready, to matchPlanes",__FUNCTION__,__LINE__);
    int ret = 0;
    if(ctx.request.iSkipCnt > 0){
      ret = TryMixSkipPolicy(composition,layers,crtc,plane_groups);
    }else{
      ret = TryOverlayPolicy(composition,layers,crtc,plane_groups);
      if(ret){
        ret = TryMixVideoPolicy(composition,layers,crtc,plane_groups);
      }
    }
    if(!ret){ // Match sucess, to call im2d interface
      for(auto &drmLayer : layers){
        if(drmLayer->bUseSr_){
          int output_fence = 0;
          // 13. RunAsync
          SrError error = svep_sr_->RunAsync(&sr_src_, &sr_dst_, &output_fence);
          if (error != SrError::None){
            HWC2_ALOGD_IF_DEBUG("RunAsync fail!");
            drmLayer->bUseSr_ = false;
            drmLayer->ResetInfoFromStore();
            bufferQueue_->QueueBuffer(dst_buffer);
            return -1;
          }else{
            last_buffer_id = drmLayer->storeLayerInfo_.uBufferId_;
            last_sr_mode = sr_mode;
            last_contrast_mode = contrast_mode;
            last_enhancement_rate = enhancement_rate;
            last_contrast_offset = contrast_offset;
            dst_buffer->SetFinishFence(output_fence);
            drmLayer->pSrBuffer_ = dst_buffer;
            drmLayer->acquire_fence = sp<AcquireFence>(new AcquireFence(dst_buffer->GetFinishFence()));
            bufferQueue_->QueueBuffer(dst_buffer);
            return 0;
          }
        }
      }
      ResetLayerFromTmp(layers,tmp_layers);
      return ret;
    }else{ // Match fail, skip rga policy
      HWC2_ALOGD_IF_DEBUG(" MatchPlanes fail! reset DrmHwcLayer.");
      for(auto &drmLayer : layers){
        if(drmLayer->bUseSr_){
          bufferQueue_->QueueBuffer(dst_buffer);
          drmLayer->ResetInfoFromStore();
          drmLayer->bUseSr_ = false;
        }
      }
      ResetLayerFromTmp(layers,tmp_layers);
      return -1;
    }
  }else if(use_laster_rga_layer){
    ALOGD_IF(LogLevel(DBG_DEBUG), "%s:line=%d SR layer ready, to matchPlanes",__FUNCTION__,__LINE__);
    int ret = -1;
    if(ctx.request.iSkipCnt > 0){
      ret = TryMixSkipPolicy(composition,layers,crtc,plane_groups);
    }else{
      ret = TryOverlayPolicy(composition,layers,crtc,plane_groups);
      if(ret){
        ret = TryMixVideoPolicy(composition,layers,crtc,plane_groups);
      }
    }
    if(!ret){ // Match sucess, to call im2d interface
      HWC2_ALOGD_IF_DEBUG("Use last SR layer.");
      return ret;
    }else{
      for(auto &drmLayer : layers){
        if(drmLayer->bUseSr_){
          last_buffer_id = drmLayer->storeLayerInfo_.uBufferId_;
          last_sr_mode = sr_mode;
          last_contrast_mode = contrast_mode;
          last_enhancement_rate = enhancement_rate;
          last_contrast_offset = contrast_offset;
          drmLayer->ResetInfoFromStore();
          drmLayer->bUseSr_ = false;
        }
      }

    }
  }
  HWC2_ALOGD_IF_DEBUG("fail!, No layer use SR policy.");
  ResetLayerFromTmp(layers,tmp_layers);
  return -1;
}
#endif

#ifdef USE_LIBSVEP_MEMC
int Vop3588::ClearMemcJob(){
  // memc初始化
  if(svep_memc_.get() != NULL){
    if(svep_memc_->Init(MEMC_VERSION, true)){
      HWC2_ALOGD_IF_DEBUG("Memc init failed.");
      return -1;
    }
  }

  // 创建ctx参数
  int ret = svep_memc_->ClearResource();
  if(ret){
    HWC2_ALOGD_IF_ERR("Memc MpClearResource failed. ret=%d", ret);
    return ret;
  }

  HWC2_ALOGD_IF_DEBUG("Memc MpClearResource success.");
  return 0;

}

int Vop3588::TryMemcPolicy(std::vector<DrmCompositionPlane> *composition,
                           std::vector<DrmHwcLayer*> &layers, DrmCrtc *crtc,
                           std::vector<PlaneGroup *> &plane_groups){

  ALOGD_IF(LogLevel(DBG_DEBUG), "%s:line=%d",__FUNCTION__,__LINE__);
  std::vector<DrmHwcLayer*> tmp_layers;
  ResetLayer(layers);
  ResetPlaneGroups(plane_groups);

  // SR_RUNTIME_DISABLE_NAME 主要适用于前端系统服务判断当前场景无法使用SR模式才设置为 1
  // 例如 30帧以上片源 或 低延迟场景
  int svep_runtime_disable = hwc_get_int_property(MEMC_RUNTIME_DISABLE_NAME,"0");
  bool memc_mode = false;
  // Match policy first
  HWC2_ALOGD_IF_DEBUG("%s=%d bMemcReady_=%d",MEMC_MODE_NAME, HWC2_SR_MEMC, bMemcReady_);
  // 只有主屏可以享受视频 SR 效果
  if(svep_runtime_disable == 0){
    // Match policy first
    memc_mode = true;
  }

  static bool last_memc_mode = false;
  if(!memc_mode){
    last_memc_mode = memc_mode;
    return -1;
  }

  // memc初始化
  if(svep_memc_.get() != NULL){
    if(svep_memc_->Init(MEMC_VERSION, true)){
      HWC2_ALOGD_IF_DEBUG("Memc init failed.");
      return -1;
    }else{
      bMemcReady_ = true;
    }
  }

  // 如果　memc 不是连续的帧，则需要清空Memc内部队列
  if(ctx.request.frame_no_ - uMemcFrameNo_ != 1){
    ClearMemcJob();
  }

  bool memc_layer_ready = false;
  bool use_laster_memc_layer = false;
  std::shared_ptr<DrmBuffer> dst_buffer;
  static uint64_t last_buffer_id = 0;
  int releaseFence = -1;

  MemcImageInfo memcSrcInfo;
  MemcImageInfo memcReqInfo;
  MemcImageInfo memcDstInfo;

  bool enableMemcComparation = (hwc_get_int_property(MEMC_CONTRAST_MODE_NAME, "0") > 0);
  bool enableMemcOsd = (hwc_get_int_property(MEMC_OSD_DISABLE_MODE, "0") == 0);
  bool enableMemcOsdOneline = (hwc_get_int_property(MEMC_OSD_VIDEO_ONELINE_MODE, "0") > 0);
  int osd_oneline_wait_second = hwc_get_int_property(MEMC_OSD_VIDEO_ONELINE_WATI_SEC, "12");
  MEMC_MODE memc_match_mode = MEMC_MODE::MEMC_UN_SUPPORT;
  for(auto &drmLayer : layers){
    if(SvepMemcAllowedByLocalPolicy(drmLayer) &&
       SvepMemcAllowedByBlacklist(drmLayer)){
        ALOGD_IF(LogLevel(DBG_DEBUG), "%s:line=%d",__FUNCTION__,__LINE__);
        if(last_buffer_id != drmLayer->uBufferId_ ||
           last_memc_mode != memc_mode){
           last_memc_mode = memc_mode;
           last_buffer_id = drmLayer->uBufferId_;//更新为当前帧的uBufferId_
          // set src info
          memcSrcInfo.mBufferInfo_.iFd_     = drmLayer->iFd_;
          memcSrcInfo.mBufferInfo_.iWidth_  = drmLayer->iWidth_;
          memcSrcInfo.mBufferInfo_.iHeight_ = drmLayer->iHeight_;
          memcSrcInfo.mBufferInfo_.iFormat_ = drmLayer->iFormat_;
          memcSrcInfo.mBufferInfo_.iStride_ = drmLayer->iStride_;
          memcSrcInfo.mBufferInfo_.uBufferId_ = drmLayer->uBufferId_;
          memcSrcInfo.mBufferInfo_.uColorSpace_ = (uint64_t)drmLayer->eDataSpace_;

          if(drmLayer->iFormat_ == HAL_PIXEL_FORMAT_YUV420_8BIT_I){
            memcSrcInfo.mBufferInfo_.iFormat_ = HAL_PIXEL_FORMAT_YCrCb_NV12;
          }else if(drmLayer->iFormat_ == HAL_PIXEL_FORMAT_YUV420_10BIT_I){
            memcSrcInfo.mBufferInfo_.iFormat_ = HAL_PIXEL_FORMAT_YCrCb_NV12_10;
          }

          // AFBC format
          if(drmLayer->bAfbcd_){
            memcSrcInfo.mBufferInfo_.uMask_ = MEMC_AFBC_FORMAT;
          }

          memcSrcInfo.mCrop_.iLeft_  = drmLayer->source_crop.left;
          memcSrcInfo.mCrop_.iTop_   = drmLayer->source_crop.top;
          memcSrcInfo.mCrop_.iRight_ = drmLayer->source_crop.right;
          memcSrcInfo.mCrop_.iBottom_= drmLayer->source_crop.bottom;


          MEMC_MODE memc_mode = MEMC_MODE::MEMC_UN_SUPPORT;
          int ret = svep_memc_->MatchMemcMode(&memcSrcInfo, &memc_match_mode);
          if(ret != MEMC_ERROR::MEMC_NO_ERROR){
            HWC2_ALOGD_IF_DEBUG("MatchMemcMode fail!, skip this policy. ret=%d", ret);
            drmLayer->bUseMemc_ = false;
            continue;
          }

          //get dst info
          ret = svep_memc_->GetDstImageInfo(&memcReqInfo);
          if(ret != MEMC_ERROR::MEMC_NO_ERROR){
            HWC2_ALOGD_IF_DEBUG("GetDstImageInfo fail!, skip this policy. ret=%d", ret);
            continue;
          }
          dst_buffer = memcBufferQueue_->DequeueDrmBuffer(memcReqInfo.mBufferInfo_.iWidth_,
                                                        memcReqInfo.mBufferInfo_.iHeight_,
                                                        memcReqInfo.mBufferInfo_.iFormat_,
                                                        RK_GRALLOC_USAGE_STRIDE_ALIGN_16 |
                                                        MALI_GRALLOC_USAGE_NO_AFBC |
                                                        RK_GRALLOC_USAGE_WITHIN_4G,
                                                        "MEMC-SurfaceView");

          if(dst_buffer == NULL){
            HWC2_ALOGD_IF_DEBUG("DequeueDrmBuffer fail!, skip this policy.");
            continue;
          }

          // set dst info
          memcDstInfo.mBufferInfo_.iFd_     = dst_buffer->GetFd();
          memcDstInfo.mBufferInfo_.iWidth_  = dst_buffer->GetWidth();
          memcDstInfo.mBufferInfo_.iHeight_ = dst_buffer->GetHeight();
          memcDstInfo.mBufferInfo_.iFormat_ = dst_buffer->GetFormat();
          memcDstInfo.mBufferInfo_.iStride_ = dst_buffer->GetStride();
          memcDstInfo.mBufferInfo_.uBufferId_ = dst_buffer->GetBufferId();

          memcDstInfo.mCrop_.iLeft_  = memcReqInfo.mCrop_.iLeft_;
          memcDstInfo.mCrop_.iTop_   = memcReqInfo.mCrop_.iTop_;
          memcDstInfo.mCrop_.iRight_ = memcReqInfo.mCrop_.iRight_;
          memcDstInfo.mCrop_.iBottom_= memcReqInfo.mCrop_.iBottom_;

          // update drmlayer info
          hwc_frect_t source_crop;
          source_crop.left   = memcDstInfo.mCrop_.iLeft_;
          source_crop.top    = memcDstInfo.mCrop_.iTop_;
          source_crop.right  = ALIGN_DOWN((int)(memcDstInfo.mCrop_.Width()),2);
          source_crop.bottom = ALIGN_DOWN((int)(memcDstInfo.mCrop_.Height()),2);
          drmLayer->UpdateAndStoreInfoFromDrmBuffer(dst_buffer->GetHandle(),
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
                                                    DRM_MODE_ROTATE_0);
          memc_layer_ready = true;
          drmLayer->bUseMemc_ = true;
          break;
        }else{
          // set src info
          memcSrcInfo.mBufferInfo_.iFd_     = drmLayer->iFd_;
          memcSrcInfo.mBufferInfo_.iWidth_  = drmLayer->iWidth_;
          memcSrcInfo.mBufferInfo_.iHeight_ = drmLayer->iHeight_;
          memcSrcInfo.mBufferInfo_.iFormat_ = drmLayer->iFormat_;
          memcSrcInfo.mBufferInfo_.iStride_ = drmLayer->iStride_;
          memcSrcInfo.mBufferInfo_.uBufferId_ = drmLayer->uBufferId_;
          memcSrcInfo.mBufferInfo_.uColorSpace_ = (uint64_t)drmLayer->eDataSpace_;

          if(drmLayer->iFormat_ == HAL_PIXEL_FORMAT_YUV420_8BIT_I){
            memcSrcInfo.mBufferInfo_.iFormat_ = HAL_PIXEL_FORMAT_YCrCb_NV12;
          }else if(drmLayer->iFormat_ == HAL_PIXEL_FORMAT_YUV420_10BIT_I){
            memcSrcInfo.mBufferInfo_.iFormat_ = HAL_PIXEL_FORMAT_YCrCb_NV12_10;
          }

          // AFBC format
          if(drmLayer->bAfbcd_){
            memcSrcInfo.mBufferInfo_.uMask_ = MEMC_AFBC_FORMAT;
          }

          /*RK内部 encode IP 对 YUV422 AFBC 的打包处理都一样
            将 HAL_PIXEL_FORMAT_YCBCR_422_I 转换为 RGA3 能处理的 RK_FORMAT_YCbCr_422_SP 格式即可进行解码*/
          if(drmLayer->iFormat_ == HAL_PIXEL_FORMAT_YCBCR_422_I && drmLayer->bAfbcd_){
            memcSrcInfo.mBufferInfo_.iFormat_ = RK_FORMAT_YCbCr_422_SP;
          }

          memcSrcInfo.mCrop_.iLeft_  = drmLayer->source_crop.left;
          memcSrcInfo.mCrop_.iTop_   = drmLayer->source_crop.top;
          memcSrcInfo.mCrop_.iRight_ = drmLayer->source_crop.right;
          memcSrcInfo.mCrop_.iBottom_= drmLayer->source_crop.bottom;

          MEMC_MODE memc_mode = MEMC_MODE::MEMC_UN_SUPPORT;
          int ret = svep_memc_->MatchMemcMode(&memcSrcInfo, &memc_match_mode);
          if(ret != MEMC_ERROR::MEMC_NO_ERROR){
            HWC2_ALOGD_IF_DEBUG("MatchMemcMode fail!, skip this policy. ret=%d", ret);
            drmLayer->bUseMemc_ = false;
            continue;
          }

          //get dst info
          ret = svep_memc_->GetDstImageInfo(&memcReqInfo);
          if(ret != MEMC_ERROR::MEMC_NO_ERROR){
            HWC2_ALOGD_IF_DEBUG("GetDstImageInfo fail!, skip this policy. ret=%d", ret);
            continue;
          }
          dst_buffer = memcBufferQueue_->DequeueDrmBuffer(memcReqInfo.mBufferInfo_.iWidth_,
                                                        memcReqInfo.mBufferInfo_.iHeight_,
                                                        memcReqInfo.mBufferInfo_.iFormat_,
                                                        RK_GRALLOC_USAGE_STRIDE_ALIGN_16 |
                                                        MALI_GRALLOC_USAGE_NO_AFBC |
                                                        RK_GRALLOC_USAGE_WITHIN_4G,
                                                        "MEMC-SurfaceView");


          if(dst_buffer == NULL){
            HWC2_ALOGD_IF_DEBUG("DequeueDrmBuffer fail!, skip this policy.");
            continue;
          }

          memcDstInfo.mBufferInfo_.iFd_     = dst_buffer->GetFd();
          memcDstInfo.mBufferInfo_.iWidth_  = dst_buffer->GetWidth();
          memcDstInfo.mBufferInfo_.iHeight_ = dst_buffer->GetHeight();
          memcDstInfo.mBufferInfo_.iFormat_ = dst_buffer->GetFormat();
          memcDstInfo.mBufferInfo_.iStride_ = dst_buffer->GetStride();
          memcDstInfo.mBufferInfo_.uBufferId_ = dst_buffer->GetBufferId();

          memcDstInfo.mCrop_.iLeft_  = memcReqInfo.mCrop_.iLeft_;
          memcDstInfo.mCrop_.iTop_   = memcReqInfo.mCrop_.iTop_;
          memcDstInfo.mCrop_.iRight_ = memcReqInfo.mCrop_.iRight_;
          memcDstInfo.mCrop_.iBottom_= memcReqInfo.mCrop_.iBottom_;

          hwc_frect_t source_crop;
          source_crop.left  = memcDstInfo.mCrop_.iLeft_;
          source_crop.top   = memcDstInfo.mCrop_.iTop_;
          source_crop.right =   ALIGN_DOWN((int)(memcDstInfo.mCrop_.Width()),2);
          source_crop.bottom  = ALIGN_DOWN((int)(memcDstInfo.mCrop_.Height()),2);
          drmLayer->UpdateAndStoreInfoFromDrmBuffer(dst_buffer->GetHandle(),
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
                                                    DRM_MODE_ROTATE_0);

          use_laster_memc_layer = true;
          drmLayer->bUseMemc_ = true;
          drmLayer->iBestPlaneType = PLANE_RK3588_ALL_ESMART_MASK;
          break;
        }
      }
  }

  if(memc_layer_ready || use_laster_memc_layer){
    ALOGD_IF(LogLevel(DBG_DEBUG), "%s:line=%d memc layer ready, to matchPlanes",__FUNCTION__,__LINE__);
    int ret = 0;
    if(ctx.request.iSkipCnt > 0){
      ret = TryMixSkipPolicy(composition,layers,crtc,plane_groups);
    }else{
      ret = TryOverlayPolicy(composition,layers,crtc,plane_groups);
      if(ret){
        ret = TryMixVideoPolicy(composition,layers,crtc,plane_groups);
      }
    }
    if(!ret){ // Match sucess, to call im2d interface
      for(auto &drmLayer : layers){
        if(drmLayer->bUseMemc_){
          if(enableMemcOsd){
            MEMC_OSD_MODE osd_mode = MEMC_OSD_ENABLE_VIDEO;
            const wchar_t* osd_str = MEMC_OSD_VIDEO_STR;
            if(enableMemcOsdOneline > 0){
              // 视频播放SR若干帧后，采用oneline OSD模式
              if(mMemcLastMode_ != memc_match_mode){
                struct timeval tp;
                gettimeofday(&tp, NULL);
                mMemcLastMode_ = memc_match_mode;
                mMemcBeginTimeMs_ = tp.tv_sec * 1000 + tp.tv_usec / 1000;
                mMemcEnableOnelineMode_ = false;
              }
              if(!mMemcEnableOnelineMode_){
                struct timeval tp;
                gettimeofday(&tp, NULL);
                uint64_t current_time = tp.tv_sec * 1000 + tp.tv_usec / 1000;
                if((current_time - mMemcBeginTimeMs_) > osd_oneline_wait_second * 1000){
                  mMemcEnableOnelineMode_ = true;
                }
              }else{
                osd_mode = MEMC_OSD_ENABLE_VIDEO_ONELINE;
                osd_str = MEMC_OSD_VIDEO_ONELINE_STR;
              }
            }
            svep_memc_->SetOsdMode(osd_mode, osd_str);
          }else{
            svep_memc_->SetOsdMode(MEMC_OSD_DISABLE, NULL);
          }
          svep_memc_->SetContrastMode(enableMemcComparation);
          int memc_fence = -1;
          int ret = svep_memc_->RunAsync(&memcSrcInfo, &memcDstInfo, &memc_fence);
          if(ret != MEMC_ERROR::MEMC_NO_ERROR){
            HWC2_ALOGD_IF_DEBUG("MpRunAsync fail!, skip this policy. ret=%d", ret);
            memcBufferQueue_->QueueBuffer(dst_buffer);
            drmLayer->ResetInfoFromStore();
            drmLayer->bUseMemc_ = false;
            break;
          }

          dst_buffer->SetFinishFence(dup(memc_fence));
          drmLayer->pSrBuffer_ = dst_buffer;
          drmLayer->acquire_fence = sp<AcquireFence>(new AcquireFence(memc_fence));
          memcBufferQueue_->QueueBuffer(dst_buffer);
          uMemcFrameNo_ = ctx.request.frame_no_;
          mMemcLastMode_ = memc_match_mode;
          return 0;
        }
      }
      ResetLayerFromTmp(layers,tmp_layers);
      return ret;
    }else{ // Match fail, skip rga policy
      HWC2_ALOGD_IF_DEBUG("MatchPlanes fail! reset DrmHwcLayer.");
      for(auto &drmLayer : layers){
        if(drmLayer->bUseMemc_){
          memcBufferQueue_->QueueBuffer(dst_buffer);
          drmLayer->ResetInfoFromStore();
          drmLayer->bUseMemc_ = false;
        }
      }
      ResetLayerFromTmp(layers,tmp_layers);
      return -1;
    }
  }

  HWC2_ALOGD_IF_DEBUG("fail!, No layer use MEMC policy.");
  ResetLayerFromTmp(layers,tmp_layers);
  return -1;
}

#endif

/*************************mix video*************************
 Video ovelay
-----------+----------+------+------+----+------+-------------+--------------------------------+------------------------+------
       HWC | 711aa61700 | 0000 | 0000 | 00 | 0100 | ? 00000017  |    0.0,    0.0, 3840.0, 2160.0 |  600,  562, 1160,  982 | SurfaceView - MediaView
      GLES | 711ab1e580 | 0000 | 0000 | 00 | 0105 | RGBA_8888   |    0.0,    0.0,  560.0,  420.0 |  600,  562, 1160,  982 | MediaView
      GLES | 70b34c9c80 | 0000 | 0000 | 00 | 0105 | RGBA_8888   |    0.0,    0.0, 2400.0,    2.0 |    0,    0, 2400,    2 | StatusBar
      GLES | 70b34c9080 | 0000 | 0000 | 00 | 0105 | RGBA_8888   |    0.0,    0.0, 2400.0,   84.0 |    0, 1516, 2400, 1600 | taskbar
      GLES | 711ec5a900 | 0000 | 0002 | 00 | 0105 | RGBA_8888   |    0.0,    0.0,   39.0,   49.0 | 1136, 1194, 1175, 1243 | Sprite
************************************************************/
int Vop3588::TryMixVideoPolicy(
    std::vector<DrmCompositionPlane> *composition,
    std::vector<DrmHwcLayer*> &layers, DrmCrtc *crtc,
    std::vector<PlaneGroup *> &plane_groups) {
  ALOGD_IF(LogLevel(DBG_DEBUG), "%s:line=%d",__FUNCTION__,__LINE__);
  ResetLayer(layers);
  ResetPlaneGroups(plane_groups);
  std::vector<DrmHwcLayer *> tmp_layers;
  //save fb into tmp_layers
  MoveFbToTmp(layers, tmp_layers);

  int iPlaneSize = plane_groups.size();
  std::pair<int, int> layer_indices(-1, -1);

  if((int)layers.size() < 4)
    layer_indices.first = layers.size() - 2 <= 0 ? 1 : layers.size() - 2;
  else
    layer_indices.first = 3;
  layer_indices.second = layers.size() - 1;
  ALOGD_IF(LogLevel(DBG_DEBUG), "%s:mix video (%d,%d)",__FUNCTION__,layer_indices.first, layer_indices.second);
  OutputMatchLayer(layer_indices.first, layer_indices.second, layers, tmp_layers);
  int ret = MatchPlanes(composition,layers,crtc,plane_groups);
  if(!ret)
    return ret;
  else{
    ResetLayerFromTmpExceptFB(layers,tmp_layers);
    for(--layer_indices.first; layer_indices.first > 0; --layer_indices.first){
      ALOGD_IF(LogLevel(DBG_DEBUG), "%s:mix video (%d,%d)",__FUNCTION__,layer_indices.first, layer_indices.second);
      OutputMatchLayer(layer_indices.first, layer_indices.second, layers, tmp_layers);
      ret = MatchPlanes(composition,layers,crtc,plane_groups);
      if(!ret)
        return ret;
      else{
        ResetLayerFromTmpExceptFB(layers,tmp_layers);
        continue;
      }
    }
  }
  ResetLayerFromTmp(layers,tmp_layers);
  return ret;
}

/*************************mix up*************************
-----------+----------+------+------+----+------+-------------+--------------------------------+------------------------+------
       HWC | 711aa61e80 | 0000 | 0000 | 00 | 0100 | RGBx_8888   |    0.0,    0.0, 2400.0, 1600.0 |    0,    0, 2400, 1600 | com.android.systemui.ImageWallpaper
       HWC | 711ab1ef00 | 0000 | 0000 | 00 | 0105 | RGBA_8888   |    0.0,    0.0, 2400.0, 1600.0 |    0,    0, 2400, 1600 | com.android.launcher3/com.android.launcher3.Launcher
       HWC | 711aa61700 | 0000 | 0000 | 00 | 0100 | ? 00000017  |    0.0,    0.0, 3840.0, 2160.0 |  600,  562, 1160,  982 | SurfaceView - MediaView
      GLES | 711ab1e580 | 0000 | 0000 | 00 | 0105 | RGBA_8888   |    0.0,    0.0,  560.0,  420.0 |  600,  562, 1160,  982 | MediaView
      GLES | 70b34c9c80 | 0000 | 0000 | 00 | 0105 | RGBA_8888   |    0.0,    0.0, 2400.0,    2.0 |    0,    0, 2400,    2 | StatusBar
      GLES | 70b34c9080 | 0000 | 0000 | 00 | 0105 | RGBA_8888   |    0.0,    0.0, 2400.0,   84.0 |    0, 1516, 2400, 1600 | taskbar
      GLES | 711ec5a900 | 0000 | 0002 | 00 | 0105 | RGBA_8888   |    0.0,    0.0,   39.0,   49.0 | 1136, 1194, 1175, 1243 | Sprite
************************************************************/
int Vop3588::TryMixUpPolicy(
    std::vector<DrmCompositionPlane> *composition,
    std::vector<DrmHwcLayer*> &layers, DrmCrtc *crtc,
    std::vector<PlaneGroup *> &plane_groups) {
  ALOGD_IF(LogLevel(DBG_DEBUG), "%s:line=%d",__FUNCTION__,__LINE__);
  ResetLayer(layers);
  ResetPlaneGroups(plane_groups);
  std::vector<DrmHwcLayer *> tmp_layers;
  //save fb into tmp_layers
  MoveFbToTmp(layers, tmp_layers);

  int iPlaneSize = plane_groups.size();

  if(ctx.request.iAfbcdCnt == 0){
    for(auto &plane_group : plane_groups){
      if(plane_group->win_type & PLANE_RK3588_ALL_CLUSTER_MASK)
        iPlaneSize--;
    }
  }

  if(iPlaneSize == 0){
    ALOGE_IF(LogLevel(DBG_DEBUG), "%s:line=%d, iPlaneSize = %d, skip TryMixSkipPolicy",
              __FUNCTION__,__LINE__,iPlaneSize);
  }

  std::pair<int, int> layer_indices(-1, -1);

  if((int)layers.size() < 4)
    layer_indices.first = layers.size() - 2 <= 0 ? 1 : layers.size() - 2;
  else
    layer_indices.first = 3;
  layer_indices.second = layers.size() - 1;
  ALOGD_IF(LogLevel(DBG_DEBUG), "%s:mix video (%d,%d)",__FUNCTION__,layer_indices.first, layer_indices.second);
  OutputMatchLayer(layer_indices.first, layer_indices.second, layers, tmp_layers);
  int ret = MatchPlanes(composition,layers,crtc,plane_groups);
  if(!ret)
    return ret;
  else{
    ResetLayerFromTmpExceptFB(layers,tmp_layers);
    for(--layer_indices.first; layer_indices.first > 0; --layer_indices.first){
      ALOGD_IF(LogLevel(DBG_DEBUG), "%s:mix video (%d,%d)",__FUNCTION__,layer_indices.first, layer_indices.second);
      OutputMatchLayer(layer_indices.first, layer_indices.second, layers, tmp_layers);
      ret = MatchPlanes(composition,layers,crtc,plane_groups);
      if(!ret)
        return ret;
      else{
        ResetLayerFromTmpExceptFB(layers,tmp_layers);
        continue;
      }
    }
  }

  ResetLayerFromTmp(layers,tmp_layers);
  return ret;
}

/*************************mix down*************************
 Sprite layer
-----------+----------+------+------+----+------+-------------+--------------------------------+------------------------+------
      GLES | 711aa61e80 | 0000 | 0000 | 00 | 0100 | RGBx_8888   |    0.0,    0.0, 2400.0, 1600.0 |    0,    0, 2400, 1600 | com.android.systemui.ImageWallpaper
      GLES | 711ab1ef00 | 0000 | 0000 | 00 | 0105 | RGBA_8888   |    0.0,    0.0, 2400.0, 1600.0 |    0,    0, 2400, 1600 | com.android.launcher3/com.android.launcher3.Launcher
      GLES | 711aa61100 | 0000 | 0000 | 00 | 0105 | RGBA_8888   |    0.0,    0.0, 2400.0,    2.0 |    0,    0, 2400,    2 | StatusBar
       HWC | 711ec5ad80 | 0000 | 0000 | 00 | 0105 | RGBA_8888   |    0.0,    0.0, 2400.0,   84.0 |    0, 1516, 2400, 1600 | taskbar
       HWC | 711ec5a900 | 0000 | 0002 | 00 | 0105 | RGBA_8888   |    0.0,    0.0,   39.0,   49.0 |  941,  810,  980,  859 | Sprite
************************************************************/
int Vop3588::TryMixDownPolicy(
    std::vector<DrmCompositionPlane> *composition,
    std::vector<DrmHwcLayer*> &layers, DrmCrtc *crtc,
    std::vector<PlaneGroup *> &plane_groups) {
  ALOGD_IF(LogLevel(DBG_DEBUG), "%s:line=%d",__FUNCTION__,__LINE__);
  ResetLayer(layers);
  ResetPlaneGroups(plane_groups);
  std::vector<DrmHwcLayer *> tmp_layers;

  //save fb into tmp_layers
  MoveFbToTmp(layers, tmp_layers);

  std::pair<int, int> layer_indices(-1, -1);
  int iPlaneSize = plane_groups.size();
  layer_indices.first = 0;
  layer_indices.second = 0;
  ALOGD_IF(LogLevel(DBG_DEBUG), "%s:mix down (%d,%d)",__FUNCTION__,layer_indices.first, layer_indices.second);
  OutputMatchLayer(layer_indices.first, layer_indices.second, layers, tmp_layers);
  int ret = MatchPlanes(composition,layers,crtc,plane_groups);
  if(!ret){
    return ret;

  }else{
    ResetLayerFromTmpExceptFB(layers,tmp_layers);
    for(int i = 1; i < layers.size(); i++){
      layer_indices.first = 0;
      layer_indices.second = i;
      ALOGD_IF(LogLevel(DBG_DEBUG), "%s:mix down (%d,%d)",__FUNCTION__,layer_indices.first, layer_indices.second);
      OutputMatchLayer(layer_indices.first, layer_indices.second, layers, tmp_layers);
      ret = MatchPlanes(composition,layers,crtc,plane_groups);
      if(!ret)
        return ret;
      else{
        ResetLayerFromTmpExceptFB(layers,tmp_layers);
        continue;
      }
    }
  }
  ResetLayerFromTmp(layers,tmp_layers);
  return ret;
}

int Vop3588::TryMixPolicy(
    std::vector<DrmCompositionPlane> *composition,
    std::vector<DrmHwcLayer*> &layers, DrmCrtc *crtc,
    std::vector<PlaneGroup *> &plane_groups) {
  ALOGD_IF(LogLevel(DBG_DEBUG), "%s:line=%d",__FUNCTION__,__LINE__);
  int ret;

  if(ctx.state.setHwcPolicy.count(HWC_SIDEBAND_LOPICY)){
    ret = TryMixSidebandPolicy(composition,layers,crtc,plane_groups);
    if(!ret)
      return 0;
    else
      return ret;
  }

  if(ctx.state.setHwcPolicy.count(HWC_MIX_SKIP_LOPICY)){
    ret = TryMixSkipPolicy(composition,layers,crtc,plane_groups);
    if(!ret)
      return 0;
    else
      return ret;
  }

  if(ctx.state.setHwcPolicy.count(HWC_MIX_VIDEO_LOPICY)){
    ret = TryMixVideoPolicy(composition,layers,crtc,plane_groups);
    if(!ret)
      return 0;
  }

  if(ctx.state.setHwcPolicy.count(HWC_RGA_OVERLAY_LOPICY)){
    ret = TryRgaOverlayPolicy(composition,layers,crtc,plane_groups);
    if(!ret)
      return 0;
  }

  if(ctx.state.setHwcPolicy.count(HWC_MIX_UP_LOPICY)){
    ret = TryMixUpPolicy(composition,layers,crtc,plane_groups);
    if(!ret)
      return 0;

  }
  if(ctx.state.setHwcPolicy.count(HWC_MIX_DOWN_LOPICY)){
    ret = TryMixDownPolicy(composition,layers,crtc,plane_groups);
    if(!ret)
      return 0;
  }

  if(ctx.state.setHwcPolicy.count(HWC_MIX_DOWN_LOPICY)){
    ret = TryMixDownPolicy(composition,layers,crtc,plane_groups);
    if(!ret)
      return 0;
  }

  return -1;
}

int Vop3588::TryGLESPolicy(
    std::vector<DrmCompositionPlane> *composition,
    std::vector<DrmHwcLayer*> &layers, DrmCrtc *crtc,
    std::vector<PlaneGroup *> &plane_groups) {
  ALOGD_IF(LogLevel(DBG_DEBUG), "%s:line=%d",__FUNCTION__,__LINE__);
  std::vector<DrmHwcLayer*> fb_target;
  ResetLayer(layers);
  ResetPlaneGroups(plane_groups);
  //save fb into tmp_layers
  MoveFbToTmp(layers, fb_target);

  if(fb_target.size()==1){
    DrmHwcLayer* fb_layer = fb_target[0];
    // If there is a Cluster layer, FB enables AFBC
    if(ctx.support.iAfbcdCnt > 0){
      ctx.state.bDisableFBAfbcd = false;

      // Check FB target property
      ctx.state.bDisableFBAfbcd =
        hwc_get_int_property("vendor.gralloc.no_afbc_for_fb_target_layer","0") > 0;

      // If FB-target unable to meet the scaling requirements, AFBC must be disable.
      if((fb_layer->fHScaleMul_ > 4.0 || fb_layer->fHScaleMul_ < 0.25) ||
         (fb_layer->fVScaleMul_ > 4.0 || fb_layer->fVScaleMul_ < 0.25) ){
        ctx.state.bDisableFBAfbcd = true;
        ALOGI_IF(LogLevel(DBG_DEBUG),"%s,line=%d FB-target over max scale factor,"
              " FB-target must disable AFBC(%d).",
             __FUNCTION__,__LINE__,ctx.state.bDisableFBAfbcd);
      }
      if(ctx.state.bDisableFBAfbcd){
        fb_layer->bAfbcd_ = false;
      }else{
        fb_layer->bAfbcd_ = true;
        ALOGD_IF(LogLevel(DBG_DEBUG),"%s,line=%d Has Cluster Plane, FB enables AFBC",__FUNCTION__,__LINE__);
      }
    }else{
        fb_layer->bAfbcd_ = false;
        ALOGD_IF(LogLevel(DBG_DEBUG),"%s,line=%d Has Cluster Plane, FB enables AFBC",__FUNCTION__,__LINE__);
    }
  }

  int ret = MatchPlanes(composition,fb_target,crtc,plane_groups);
  if(!ret)
    return ret;
  else{
    ResetLayerFromTmp(layers,fb_target);
    return -1;
  }
  return 0;
}

void Vop3588::UpdateResevedPlane(DrmCrtc *crtc){
  // Reserved DrmPlane
  char reserved_plane_name[PROPERTY_VALUE_MAX] = {0};
  hwc_get_string_property("vendor.hwc.reserved_plane_name","NULL",reserved_plane_name);

  if(strlen(ctx.support.arrayReservedPlaneName) == 0 ||
     strcmp(reserved_plane_name,ctx.support.arrayReservedPlaneName)){
    strncpy(ctx.support.arrayReservedPlaneName,reserved_plane_name,strlen(reserved_plane_name)+1);
    DrmDevice *drm = crtc->getDrmDevice();
    std::vector<PlaneGroup *> all_plane_groups = drm->GetPlaneGroups();

    if(strcmp(reserved_plane_name,"NULL")){
      std::string reserved_name;
      std::stringstream ss(reserved_plane_name);
      while(getline(ss, reserved_name, ',')) {
        for(auto &plane_group : all_plane_groups){
          for(auto &p : plane_group->planes){
            if(!strcmp(p->name(),reserved_name.c_str())){
              plane_group->bReserved = true;
              int reserved_plane_win_type = plane_group->win_type;
              HWC2_ALOGI("Reserved DrmPlane %s , win_type = 0x%x",
                  reserved_plane_name,reserved_plane_win_type);
              break;
            }else{
              plane_group->bReserved = false;
            }
          }
        }
      }
    }
  }
  return;
}

/*
 * CLUSTER_AFBC_DECODE_MAX_RATE = 3.2
 * (src(W*H)/dst(W*H))/(aclk/dclk) > CLUSTER_AFBC_DECODE_MAX_RATE to use GLES compose.
 * Notes: (4096,1714)=>(1080,603) appear( DDR 1560M ), CLUSTER_AFBC_DECODE_MAX_RATE=2.839350
 * Notes: (4096,1714)=>(1200,900) appear( DDR 1056M ), CLUSTER_AFBC_DECODE_MAX_RATE=2.075307
 */
#define CLUSTER_AFBC_DECODE_MAX_RATE 2.0
bool Vop3588::CheckGLESLayer(DrmHwcLayer *layer){
  // RK356x can't overlay RGBA1010102
  // if(layer->iFormat_ == HAL_PIXEL_FORMAT_RGBA_1010102){
  //   HWC2_ALOGD_IF_DEBUG("[%s]：RGBA1010102 format, not support overlay.",
  //             layer->sLayerName_.c_str());
  //   return true;
  // }


  int act_w = static_cast<int>(layer->source_crop.right - layer->source_crop.left);
  int act_h = static_cast<int>(layer->source_crop.bottom - layer->source_crop.top);
  int dst_w = static_cast<int>(layer->display_frame.right - layer->display_frame.left);
  int dst_h = static_cast<int>(layer->display_frame.bottom - layer->display_frame.top);

  // RK platform VOP can't display src/dst w/h < 4 layer.
  if(act_w < 4 || act_h < 4 || dst_w < 4 || dst_h < 4){
    HWC2_ALOGD_IF_DEBUG("[%s]：[%dx%d] => [%dx%d] too small to use GLES composer.",
              layer->sLayerName_.c_str(),act_w,act_h,dst_w,dst_h);
    return true;
  }

  // RK356x Cluster can't overlay act_w % 4 != 0 afbcd layer.
  if(layer->bAfbcd_){
    // RK3588 不存在以下限制
    // if(act_w % 4 != 0){
    //   HWC2_ALOGD_IF_DEBUG("[%s]：act_w=%d Cluster must act_w %% 4 != 0.",
    //           layer->sLayerName_.c_str(),act_w);
    //   return true;
    // }

    // RK3588 Cluster性能与RK356x差异比较大，这部分限制暂时先关闭。
    //  (src(W*H)/dst(W*H))/(aclk/dclk) > rate = CLUSTER_AFBC_DECODE_MAX_RATE, Use GLES compose
    // if(layer->uAclk_ > 0 && layer->uDclk_ > 0){
    //     char value[PROPERTY_VALUE_MAX];
    //     property_get("vendor.hwc.cluster_afbc_decode_max_rate", value, "0");
    //     double cluster_afbc_decode_max_rate = atof(value);

    //     HWC2_ALOGD_IF_VERBOSE("[%s]：scale-rate=%f, allow_rate = %f, "
    //               "property_rate=%f, fHScaleMul_ = %f, fVScaleMul_ = %f, uAclk_ = %d, uDclk_=%d ",
    //               layer->sLayerName_.c_str(),
    //               (layer->fHScaleMul_ * layer->fVScaleMul_) / (layer->uAclk_/(layer->uDclk_ * 1.0)),
    //               cluster_afbc_decode_max_rate ,CLUSTER_AFBC_DECODE_MAX_RATE,
    //               layer->fHScaleMul_ ,layer->fVScaleMul_ ,layer->uAclk_ ,layer->uDclk_);
    //   if(cluster_afbc_decode_max_rate > 0){
    //     if((layer->fHScaleMul_ * layer->fVScaleMul_) / (layer->uAclk_/(layer->uDclk_ * 1.0)) > cluster_afbc_decode_max_rate){
    //       HWC2_ALOGD_IF_DEBUG("[%s]：scale too large(%f) to use GLES composer, allow_rate = %f, "
    //                 "property_rate=%f, fHScaleMul_ = %f, fVScaleMul_ = %f, uAclk_ = %d, uDclk_=%d ",
    //                 layer->sLayerName_.c_str(),
    //                 (layer->fHScaleMul_ * layer->fVScaleMul_) / (layer->uAclk_/(layer->uDclk_ * 1.0)),
    //                 CLUSTER_AFBC_DECODE_MAX_RATE,
    //                 cluster_afbc_decode_max_rate, layer->fHScaleMul_ ,
    //                 layer->fVScaleMul_ ,layer->uAclk_ ,layer->uDclk_);
    //       return true;
    //     }
    //   }else if((layer->fHScaleMul_ * layer->fVScaleMul_) / (layer->uAclk_/(layer->uDclk_ * 1.0)) > CLUSTER_AFBC_DECODE_MAX_RATE){
    //     HWC2_ALOGD_IF_DEBUG("[%s]：scale too large(%f) to use GLES composer, allow_rate = %f, "
    //               "property_rate=%f, fHScaleMul_ = %f, fVScaleMul_ = %f, uAclk_ = %d, uDclk_=%d ",
    //               layer->sLayerName_.c_str(),
    //               (layer->fHScaleMul_ * layer->fVScaleMul_) / (layer->uAclk_/(layer->uDclk_ * 1.0)),
    //               CLUSTER_AFBC_DECODE_MAX_RATE,
    //               cluster_afbc_decode_max_rate, layer->fHScaleMul_ ,
    //               layer->fVScaleMul_ ,layer->uAclk_ ,layer->uDclk_);
    //     return true;
    //   }
    // }
  }

  // RK356x Esmart can't overlay act_w % 16 == 1 and fHScaleMul_ < 1.0 layer.
  // if(!layer->bAfbcd_){
  //   if(act_w % 16 == 1 && layer->fHScaleMul_ < 1.0){
  //     HWC2_ALOGD_IF_DEBUG("[%s]：RK356x Esmart can't overlay act_w %% 16 == 1 and fHScaleMul_ < 1.0 layer.",
  //             layer->sLayerName_.c_str());
  //     return true;
  //   }

  //   int dst_w = static_cast<int>(layer->display_frame.right - layer->display_frame.left);
  //   if(dst_w % 2 == 1 && layer->fHScaleMul_ < 1.0){
  //     HWC2_ALOGD_IF_DEBUG("[%s]：RK356x Esmart can't overlay dst_w %% 2 == 1 and fHScaleMul_ < 1.0 layer.",
  //             layer->sLayerName_.c_str());
  //     return true;
  //   }
  // }

  if(layer->transform == -1){
    HWC2_ALOGD_IF_DEBUG("[%s]：layer->transform = %d is invalidate",
            layer->sLayerName_.c_str(), layer->transform);
    return true;
  }

  // YUV bt709 full range vop 不支持输入
  if(layer->bYuv_ &&
    ((layer->eDataSpace_ & HAL_DATASPACE_STANDARD_BT709) > 0) &&
    ((layer->eDataSpace_ & HAL_DATASPACE_RANGE_FULL) > 0) ){
    // vop不支持输入bt709，但是sideband又要求vop输入
    // 故目前的解决方案是强制修改 bt709 full range 为 vop支持的色域空间：
    // CSC：RGB : BT709 Limit range
    //      YUV : BT601 limit range
    if(layer->bSidebandStreamLayer_){
      HWC2_ALOGD_IF_DEBUG("[%s]:sideband layer->dataspace= 0x%" PRIx32 " is BT709-Full, force cvt BT709-Limit",
              layer->sLayerName_.c_str(), layer->eDataSpace_);
      if(gIsDrmVerison6_1()){
        layer->uColorSpace.colorspace_kernel_6_1_.color_encoding_ = DRM_COLOR_YCBCR_BT709;
        layer->uColorSpace.colorspace_kernel_6_1_.color_range_ = DRM_COLOR_YCBCR_LIMITED_RANGE;
      }else{
        layer->uColorSpace.colorspace_kernel_510_ = V4L2_COLORSPACE_REC709;
      }
    }else{
      HWC2_ALOGD_IF_DEBUG("[%s]:layer->dataspace= 0x%" PRIx32 " is BT709-Full, vop npsupport input.",
              layer->sLayerName_.c_str(), layer->eDataSpace_);
      return true;
    }
  }

  switch(layer->sf_composition){
    //case HWC2::Composition::Sideband:
    case HWC2::Composition::SolidColor:
      HWC2_ALOGD_IF_DEBUG("[%s]：sf_composition =0x%x not support overlay.",
              layer->sLayerName_.c_str(),layer->sf_composition);
      return true;
    case HWC2::Composition::Client:
      if(layer->bYuv_ && layer->sf_handle != NULL){
        return false;
      }else{
        HWC2_ALOGD_IF_DEBUG("[%s]：sf_composition =0x%x not support overlay.",
              layer->sLayerName_.c_str(),layer->sf_composition);
        return true;
      }
      break;
    default:
      break;
  }
  return false;
}

void Vop3588::InitRequestContext(std::vector<DrmHwcLayer*> &layers){

  ctx.request.frame_no_++;
  // Collect layer info
  ctx.request.iAfbcdCnt=0;
  ctx.request.iAfbcdScaleCnt=0;
  ctx.request.iAfbcdYuvCnt=0;
  ctx.request.iAfcbdLargeYuvCnt=0;
  ctx.request.iAfbcdRotateCnt=0;
  ctx.request.iAfbcdHdrCnt=0;

  ctx.request.iScaleCnt=0;
  ctx.request.iYuvCnt=0;
  ctx.request.iLargeYuvCnt=0;
  ctx.request.iSkipCnt=0;
  ctx.request.iRotateCnt=0;
  ctx.request.iHdrCnt=0;

  ctx.request.bSidebandStreamMode=false;

  for(auto &layer : layers){
    if(CheckGLESLayer(layer)){
      layer->bGlesCompose_ = true;
    }else{
      layer->bGlesCompose_ = false;
    }

    if(layer->bFbTarget_)
      continue;

    if(layer->bSkipLayer_ || layer->bGlesCompose_){
      ctx.request.iSkipCnt++;
      continue;
    }

    if(strstr(layer->sLayerName_.c_str(), ctx.state.accelerate_app_name)){
      ctx.request.accelerate_app_exist_ = true;
      layer->bAccelerateLayer_ = true;
    }

    if(layer->bSidebandStreamLayer_)
      ctx.request.bSidebandStreamMode=true;

    if(layer->bAfbcd_){
      ctx.request.iAfbcdCnt++;

      if(layer->bScale_)
        ctx.request.iAfbcdScaleCnt++;

      if(layer->bYuv_){
        ctx.request.iAfbcdYuvCnt++;
        int dst_w = static_cast<int>(layer->display_frame.right - layer->display_frame.left);
        if(layer->iWidth_ > 2048 || layer->bHdr_ || dst_w > 2048){
          ctx.request.iAfcbdLargeYuvCnt++;
        }
      }

      if(layer->transform != DRM_MODE_ROTATE_0)
        ctx.request.iAfbcdRotateCnt++;

      if(layer->bHdr_)
        ctx.request.iAfbcdHdrCnt++;

    }else{

      ctx.request.iCnt++;

      if(layer->bScale_)
        ctx.request.iScaleCnt++;

      if(layer->bYuv_){
        ctx.request.iYuvCnt++;
        if(layer->iWidth_ > 2048){
          ctx.request.iLargeYuvCnt++;
        }
      }

      if(layer->transform != DRM_MODE_ROTATE_0)
        ctx.request.iRotateCnt++;

      if(layer->bHdr_)
        ctx.request.iHdrCnt++;
    }
  }
  return;
}

void Vop3588::InitSupportContext(
    std::vector<PlaneGroup *> &plane_groups,
    DrmCrtc *crtc){
  // Collect Plane resource info
  ctx.support.iAfbcdCnt=0;
  ctx.support.iAfbcdScaleCnt=0;
  ctx.support.iAfbcdYuvCnt=0;
  ctx.support.iAfbcdRotateCnt=0;
  ctx.support.iAfbcdHdrCnt=0;

  ctx.support.iCnt=0;
  ctx.support.iScaleCnt=0;
  ctx.support.iYuvCnt=0;
  ctx.support.iRotateCnt=0;
  ctx.support.iHdrCnt=0;

  // Update DrmPlane
  UpdateResevedPlane(crtc);

  for(auto &plane_group : plane_groups){
    if(plane_group->bReserved)
      continue;
    for(auto &p : plane_group->planes){
      if(p->get_afbc()){

        ctx.support.iAfbcdCnt++;

        if(p->get_scale())
          ctx.support.iAfbcdScaleCnt++;

        if(p->get_yuv())
          ctx.support.iAfbcdYuvCnt++;

        if(p->get_rotate())
          ctx.support.iAfbcdRotateCnt++;

        if(p->get_hdr2sdr())
          ctx.support.iAfbcdHdrCnt++;

      }else{

        ctx.support.iCnt++;

        if(p->get_scale())
          ctx.support.iScaleCnt++;

        if(p->get_yuv())
          ctx.support.iYuvCnt++;

        if(p->get_rotate())
          ctx.support.iRotateCnt++;

        if(p->get_hdr2sdr())
          ctx.support.iHdrCnt++;
      }
    }
  }
  return;
}

void Vop3588::InitStateContext(
    std::vector<DrmHwcLayer*> &layers,
    std::vector<PlaneGroup *> &plane_groups,
    DrmCrtc *crtc){

  ctx.state.iVopMaxOverlay4KPlane = hwc_get_int_property("vendor.hwc.vop_max_overlay_4k_plane","0");
  ctx.state.bRgaPolicyEnable = hwc_get_int_property("vendor.hwc.enable_rga_policy","1") > 0;

  HWC2_ALOGD_IF_DEBUG("bMultiAreaEnable=%d, bMultiAreaScaleEnable=%d iVopMaxOverlay4KPlane=%d bRgaPolicyEnable=%d",
            ctx.state.bMultiAreaEnable,
            ctx.state.bMultiAreaScaleEnable,
            ctx.state.iVopMaxOverlay4KPlane, ctx.state.bRgaPolicyEnable);

  // Check dispaly Mode : 8K Mode or 4K 120 Mode
  DrmDevice *drm = crtc->getDrmDevice();
  DrmConnector *conn = drm->GetConnectorForDisplay(crtc->display());
  if(conn && conn->state() == DRM_MODE_CONNECTED){
    DrmMode mode = conn->current_mode();
    if(ctx.state.b8kMode_ != mode.is_8k_mode()){
      HWC2_ALOGD_IF_DEBUG("%s 8K Mode.", mode.is_8k_mode() ? "Enter" : "Quit");
    }
    if(ctx.state.b4k120pMode_ != mode.is_4k120p_mode()){
      HWC2_ALOGD_IF_DEBUG("%s 4K 120 Mode.", mode.is_4k120p_mode() ? "Enter" : "Quit");
    }
    // Story Display Mode
    ctx.state.iDisplayWidth_ = mode.h_display();
    ctx.state.iDisplayHeight_ = mode.v_display();

    ctx.state.b8kMode_     = mode.is_8k_mode();
    ctx.state.b4k120pMode_ = mode.is_4k120p_mode();
    if(ctx.state.b8kMode_){
      // 8K mode Rreserved Cluster-1 and Esmart-1
      for(auto &plane_group : plane_groups){
        for(auto &p : plane_group->planes){
          if(p->win_type() & PLANE_RK3588_ALL_CLUSTER1_MASK ||
              p->win_type() & PLANE_RK3588_ALL_CLUSTER3_MASK||
              p->win_type() & PLANE_RK3588_ALL_ESMART1_MASK ||
              p->win_type() & PLANE_RK3588_ALL_ESMART3_MASK){
            plane_group->bReserved = true;
            HWC2_ALOGD_IF_DEBUG("Reserved 8K plane name=%s", p->name());

          }
        }
      }
      for(auto &layer : layers){
        if(layer->bFbTarget_){
            HWC2_ALOGD_IF_DEBUG("8K Mode, disable Fb-target Afbc");
            layer->bAfbcd_ = 0;
        }
      }
    }else{
      for(auto &plane_group : plane_groups){
        for(auto &p : plane_group->planes){
          plane_group->bReserved = false;
        }
      }
    }
  }

  // FB-target need disable AFBCD?
  ctx.state.bDisableFBAfbcd = false;
  for(auto &layer : layers){
    if(layer->bFbTarget_){
      if(ctx.support.iAfbcdCnt == 0){
        ctx.state.bDisableFBAfbcd = true;
        ALOGI_IF(LogLevel(DBG_DEBUG),"%s,line=%d No Cluster must to overlay Video, FB-target must disable AFBC(%d).",
            __FUNCTION__,__LINE__,ctx.state.bDisableFBAfbcd);
      }

      if(ctx.request.iAfcbdLargeYuvCnt > 0 && ctx.support.iAfbcdYuvCnt <= 2){
        ctx.state.bDisableFBAfbcd = true;
        ALOGI_IF(LogLevel(DBG_DEBUG),"%s,line=%d All Cluster must to overlay Video, FB-target must disable AFBC(%d).",
            __FUNCTION__,__LINE__,ctx.state.bDisableFBAfbcd);
      }

      // If FB-target unable to meet the scaling requirements, AFBC must be disable.
      if((layer->fHScaleMul_ > 4.0 || layer->fHScaleMul_ < 0.25) ||
         (layer->fVScaleMul_ > 4.0 || layer->fVScaleMul_ < 0.25) ){
        ctx.state.bDisableFBAfbcd = true;
        ALOGI_IF(LogLevel(DBG_DEBUG),"%s,line=%d FB-target over max scale factor, FB-target must disable AFBC(%d).",
             __FUNCTION__,__LINE__,ctx.state.bDisableFBAfbcd);
      }

      if(ctx.state.bDisableFBAfbcd){
        layer->bAfbcd_ = 0;
      }
      break;
    }
  }
  return;
}

bool Vop3588::TryOverlay(){
  if(ctx.request.iAfbcdCnt <= ctx.support.iAfbcdCnt &&
     ctx.request.iScaleCnt <= ctx.support.iScaleCnt &&
     ctx.request.iYuvCnt <= ctx.support.iYuvCnt &&
     ctx.request.iRotateCnt <= ctx.support.iRotateCnt &&
     ctx.request.iSkipCnt == 0){
    ctx.state.setHwcPolicy.insert(HWC_OVERLAY_LOPICY);
    return true;
  }
  return false;
}

void Vop3588::TryMix(){
  ctx.state.setHwcPolicy.insert(HWC_MIX_LOPICY);
  ctx.state.setHwcPolicy.insert(HWC_MIX_UP_LOPICY);
  if(ctx.support.iYuvCnt > 0 || ctx.support.iAfbcdYuvCnt > 0){
    ctx.state.setHwcPolicy.insert(HWC_RGA_OVERLAY_LOPICY);
    ctx.state.setHwcPolicy.insert(HWC_MIX_VIDEO_LOPICY);
  }

  if(ctx.request.iSkipCnt > 0)
    ctx.state.setHwcPolicy.insert(HWC_MIX_SKIP_LOPICY);
  if(ctx.request.bSidebandStreamMode)
    ctx.state.setHwcPolicy.insert(HWC_SIDEBAND_LOPICY);

  if(ctx.request.accelerate_app_exist_){
    ALOGD_IF(LogLevel(DBG_DEBUG),"accelerate_app_exist_ , soc_id=%x", ctx.state.iSocId);
    ctx.state.setHwcPolicy.insert(HWC_ACCELERATE_LOPICY);
  }
}

int Vop3588::InitContext(
    std::vector<DrmHwcLayer*> &layers,
    std::vector<PlaneGroup *> &plane_groups,
    DrmCrtc *crtc,
    bool gles_policy){

  ctx.state.setHwcPolicy.clear();
  ctx.state.iSocId = crtc->get_soc_id();

  InitRequestContext(layers);
  InitSupportContext(plane_groups,crtc);
  InitStateContext(layers,plane_groups,crtc);

  //force go into GPU
  int iMode = hwc_get_int_property("vendor.hwc.compose_policy","0");

  if((iMode!=1 || gles_policy) && iMode != 2){
    ctx.state.setHwcPolicy.insert(HWC_GLES_POLICY);
    if(ctx.request.bSidebandStreamMode){
        ctx.state.setHwcPolicy.insert(HWC_GLES_SIDEBAND_LOPICY);
    }
    if(ctx.request.accelerate_app_exist_){
      ALOGD_IF(LogLevel(DBG_DEBUG),"accelerate_app_exist_ , soc_id=%x", ctx.state.iSocId);
      ctx.state.setHwcPolicy.insert(HWC_ACCELERATE_LOPICY);
    }
    ALOGD_IF(LogLevel(DBG_DEBUG),"Force use GLES compose, iMode=%d, gles_policy=%d, soc_id=%x",iMode,gles_policy,ctx.state.iSocId);
    return 0;
  }

  ALOGD_IF(LogLevel(DBG_DEBUG),"request:afbcd=%d,scale=%d,yuv=%d,rotate=%d,hdr=%d,skip=%d\n"
          "support:afbcd=%d,scale=%d,yuv=%d,rotate=%d,hdr=%d, %s,line=%d,",
          ctx.request.iAfbcdCnt,ctx.request.iScaleCnt,ctx.request.iYuvCnt,
          ctx.request.iRotateCnt,ctx.request.iHdrCnt,ctx.request.iSkipCnt,
          ctx.support.iAfbcdCnt,ctx.support.iScaleCnt,ctx.support.iYuvCnt,
          ctx.support.iRotateCnt,ctx.support.iHdrCnt,
          __FUNCTION__,__LINE__);

#if (defined USE_LIBSR) || (defined USE_LIBSVEP_MEMC)
  TrySvepOverlay();
#endif

#ifdef USE_LIBSR
  DrmDevice *drm = crtc->getDrmDevice();
  DrmConnector *conn = drm->GetConnectorForDisplay(crtc->display());
  // 只有主屏可以享受视频 SR 效果
  if(conn && conn->state() == DRM_MODE_CONNECTED &&
      conn->display() == 0){
      HWC2_ALOGD_IF_DEBUG("Only Primary Display enable SR function. display=%d", conn->display());
    // YouDao need sr init.
    if(svep_sr_.get() != NULL){
      SrError error = svep_sr_->Init(SR_VERSION, true);
      if (error != SrError::None){
          HWC2_ALOGD_IF_DEBUG("Sr Init fail, plase check License.\n");
      }
    }
  }
#endif

#ifdef USE_LIBPQ
  int iPqMode = hwc_get_int_property("persist.vendor.tvinput.rkpq.mode","0");
  // Match policy first
  HWC2_ALOGD_IF_DEBUG("%s=%d ","persist.vendor.tvinput.rkpq.mode", iPqMode);
  if(iPqMode > 0){
  //   DrmDevice *drm = crtc->getDrmDevice();
  //   DrmConnector *conn = drm->GetConnectorForDisplay(crtc->display());
    ctx.state.setHwcPolicy.insert(HWC_GLES_POLICY);
    if(ctx.request.bSidebandStreamMode){
      ctx.state.setHwcPolicy.insert(HWC_GLES_SIDEBAND_LOPICY);
    }
    return 0;
  }
#endif

  if(!TryOverlay())
    TryMix();

  return 0;
}

