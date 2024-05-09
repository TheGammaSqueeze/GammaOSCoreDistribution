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

#define LOG_TAG "hwc-drm-device"

#include "drmdevice.h"
#include "drmconnector.h"
#include "drmcrtc.h"
#include "drmencoder.h"
#include "drmeventlistener.h"
#include "drmplane.h"
#include "rockchip/utils/drmdebug.h"
#include "rockchip/drmtype.h"


#include <errno.h>
#include <fcntl.h>
#include <stdint.h>
#include <xf86drm.h>
#include <xf86drmMode.h>
#include <drm_fourcc.h>
#include <cinttypes>

#include <cutils/properties.h>
#include <log/log.h>

//XML prase
#include <tinyxml2.h>


#define DEFAULT_PRIORITY 10

namespace android {

DrmDevice::DrmDevice() : event_listener_(this) {
}

DrmDevice::~DrmDevice() {
  event_listener_.Exit();
}
bool PlaneSortByZpos(const DrmPlane* plane1,const DrmPlane* plane2)
{
    int ret = 0;
    uint64_t zpos1,zpos2;
    std::tie(ret, zpos1) = plane1->zpos_property().value();
    std::tie(ret, zpos2) = plane2->zpos_property().value();
    return zpos1 < zpos2;
}

bool SortByWinType(const PlaneGroup* planeGroup1,const PlaneGroup* planeGroup2)
{
    return planeGroup1->win_type < planeGroup2->win_type;
}

bool PlaneSortByArea(const DrmPlane* plane1, const DrmPlane* plane2)
{
    uint64_t area1=0,area2=0;
    if(plane1->area_id_property().id() && plane2->area_id_property().id())
    {
        uint64_t parm = 0;

        std::tie(parm, area1) = plane1->area_id_property().value();
        std::tie(parm, area2) = plane2->area_id_property().value();
    }
    return area1 < area2;
}

void DrmDevice::init_white_modes(void){
  tinyxml2::XMLDocument doc;

  doc.LoadFile("/system/usr/share/resolution_white.xml");

  tinyxml2::XMLElement* root=doc.RootElement();
  /* usr tingxml2 to parse resolution.xml */
  if (!root)
    return;

  tinyxml2::XMLElement* resolution =root->FirstChildElement("resolution");

  while (resolution) {
    drmModeModeInfo m;

  #define PARSE(x) \
    tinyxml2::XMLElement* _##x = resolution->FirstChildElement(#x); \
    if (!_##x) { \
      ALOGE("------> failed to parse %s\n", #x); \
      resolution = resolution->NextSiblingElement(); \
      continue; \
    } \
    m.x = atoi(_##x->GetText())
  #define PARSE_HEX(x) \
    tinyxml2::XMLElement* _##x = resolution->FirstChildElement(#x); \
    if (!_##x) { \
      ALOGE("------> failed to parse %s\n", #x); \
      resolution = resolution->NextSiblingElement(); \
      continue; \
    } \
    sscanf(_##x->GetText(), "%x", &m.x);

    PARSE(clock);
    PARSE(hdisplay);
    PARSE(hsync_start);
    PARSE(hsync_end);
    PARSE(hskew);
    PARSE(vdisplay);
    PARSE(vsync_start);
    PARSE(vsync_end);
    PARSE(vscan);
    PARSE(vrefresh);
    PARSE(htotal);
    PARSE(vtotal);
    PARSE_HEX(flags);

    DrmMode mode(&m);
    /* add modes in "resolution.xml" to white list */
    white_modes_.push_back(mode);
    resolution = resolution->NextSiblingElement();
  }
}

/**
 * Verify if the given DrmMode structure is allowed to be used based on a whitelist of display modes.
 *
 * @param m DrmMode structure to verify.
 * @return True if the given DrmMode structure is in the whitelist, false otherwise.
 */
bool DrmDevice::mode_verify(const DrmMode &m) {
  // If the whitelist is empty, no verification is needed.
  if (!white_modes_.size())
    return true;

  // Check if the given DrmMode structure matches any of the elements in white_modes_ using the std::any_of() function.
  bool is_mode_valid = std::any_of(white_modes_.begin(), white_modes_.end(), [&](const DrmMode &mode) {
    // Define the condition for matching using a lambda expression.
    return (mode.h_display() == m.h_display() &&
            mode.v_display() == m.v_display() &&
            mode.h_total() == m.h_total() &&
            mode.v_total() == m.v_total() &&
            mode.clock() == m.clock() &&
            mode.flags() == m.flags() &&
            mode.h_sync_start() == m.h_sync_start() &&
            mode.h_sync_end() == m.h_sync_end() &&
            mode.h_skew() == m.h_skew() &&
            mode.v_sync_start() == m.v_sync_start() &&
            mode.v_sync_end() == m.v_sync_end());
  });

  return is_mode_valid;
}

int DrmDevice::InitEnvFromXml(){

  char xml_path[PROPERTY_VALUE_MAX];
  property_get(DRM_XML_PATH_NAME, xml_path, "/vendor/etc/HwComposerEnv.xml");

  tinyxml2::XMLDocument doc;
  int ret = doc.LoadFile(xml_path);
  if(ret){
    HWC2_ALOGW("Can't find %s file. ret=%d", xml_path, ret);
    return -1;
  }

  HWC2_ALOGI("Load %s success.", xml_path);

  tinyxml2::XMLElement* HwComposerEnv = doc.RootElement();
  /* usr tingxml2 to parse resolution.xml */
  if (!HwComposerEnv){
    HWC2_ALOGW("Can't %s:RootElement fail.", xml_path);
    return -1;
  }

  memset(&DmXml_, 0x0, sizeof(DmXml_));

  const char* verison = "1.1.1";
  ret = HwComposerEnv->QueryStringAttribute( "Version", &verison);
  if(ret){
    HWC2_ALOGW("Can't find %s verison info. ret=%d", xml_path, ret);
    return -1;
  }

  sscanf(verison, "%d.%d.%d", &DmXml_.Version.Major,
                              &DmXml_.Version.Minor,
                              &DmXml_.Version.PatchLevel);


  tinyxml2::XMLElement* pDisplayMode = HwComposerEnv->FirstChildElement("DsiplayMode");
  if (!pDisplayMode){
    HWC2_ALOGE("Can't %s:DsiplayMode fail.", xml_path);
    return -1;
  }

  pDisplayMode->QueryIntAttribute( "Mode", &DmXml_.Mode);
  pDisplayMode->QueryIntAttribute( "FbWidth", &DmXml_.FbWidth);
  pDisplayMode->QueryIntAttribute( "FbHeight", &DmXml_.FbHeight);
  pDisplayMode->QueryIntAttribute( "ConnectorCnt", &DmXml_.ConnectorCnt);
  HWC2_ALOGI("Version=%d.%d.%d Mode=%d FbWidth=%d FbHeight=%d ConnectorCnt=%d",
              DmXml_.Version.Major, DmXml_.Version.Minor, DmXml_.Version.PatchLevel,
              DmXml_.Mode, DmXml_.FbWidth, DmXml_.FbHeight, DmXml_.ConnectorCnt);

  tinyxml2::XMLElement* pConnector = pDisplayMode->FirstChildElement("Connector");
  if (!pConnector){
    HWC2_ALOGE("Can't %s:Connector fail.", xml_path);
    return -1;
  }
  while (pConnector) {
    static int iConnectorCnt = 0;

    #define PARSE_INT(x) \
    tinyxml2::XMLElement* _##x = pConnector->FirstChildElement(#x); \
    if (!_##x) { \
      HWC2_ALOGE("index=%d failed to parse %s\n", iConnectorCnt, #x); \
      pConnector = pConnector->NextSiblingElement(); \
      continue; \
    } \
    DmXml_.ConnectorInfo[iConnectorCnt].x = atoi(_##x->GetText())

    #define PARSE_STR(x) \
    tinyxml2::XMLElement* _##x = pConnector->FirstChildElement(#x); \
    if (!_##x) { \
      HWC2_ALOGE("index=%d failed to parse %s\n", iConnectorCnt, #x); \
      pConnector = pConnector->NextSiblingElement(); \
      continue; \
    } \
    strncpy(DmXml_.ConnectorInfo[iConnectorCnt].x, \
           _##x->GetText(), \
           sizeof(_##x->GetText()));

    PARSE_STR(Type);
    PARSE_INT(TypeId);
    PARSE_INT(SrcX);
    PARSE_INT(SrcY);
    PARSE_INT(SrcW);
    PARSE_INT(SrcH);
    PARSE_INT(DstX);
    PARSE_INT(DstY);
    PARSE_INT(DstW);
    PARSE_INT(DstH);

    HWC2_ALOGI("Connector[%d] type=%s-%d [%d,%d,%d,%d]=>[%d,%d,%d,%d]",
                iConnectorCnt,
                DmXml_.ConnectorInfo[iConnectorCnt].Type,
                DmXml_.ConnectorInfo[iConnectorCnt].TypeId,
                DmXml_.ConnectorInfo[iConnectorCnt].SrcX,
                DmXml_.ConnectorInfo[iConnectorCnt].SrcY,
                DmXml_.ConnectorInfo[iConnectorCnt].SrcW,
                DmXml_.ConnectorInfo[iConnectorCnt].SrcH,
                DmXml_.ConnectorInfo[iConnectorCnt].DstX,
                DmXml_.ConnectorInfo[iConnectorCnt].DstY,
                DmXml_.ConnectorInfo[iConnectorCnt].DstW,
                DmXml_.ConnectorInfo[iConnectorCnt].DstH);
    iConnectorCnt++;
    pConnector = pConnector->NextSiblingElement();
  }

  DmXml_.Valid = true;
  return 0;
}

int DrmDevice::UpdateInfoFromXml(){
  if(!DmXml_.Valid){
    HWC2_ALOGW("DmXml_.Valid = %d, ", DmXml_.Valid);
    return -1;
  }

  if(DmXml_.Mode == DRM_DISPLAY_MODE_NORMAL){
    HWC2_ALOGI("DmXml_.Mode = %d ", DmXml_.Mode);
    return 0;
  }

  for(int i = 0; i <  DmXml_.ConnectorCnt; i++){
    for(auto &conn : connectors_) {
      const char *conn_name = connector_type_str(conn->type());
      if(!strncmp(conn_name, DmXml_.ConnectorInfo[i].Type, strlen(conn_name)) &&
          DmXml_.ConnectorInfo[i].TypeId == conn->type_id()){
        if(DmXml_.Mode == DRM_DISPLAY_MODE_SPLICE){
          static bool spilt_main_connector = false;
          if(!spilt_main_connector){
            spilt_main_connector = true;
            conn->setCropSpiltPrimary();
          }
          if(conn->setCropSpilt(DmXml_.FbWidth,
                                DmXml_.FbHeight,
                                DmXml_.ConnectorInfo[i].SrcX,
                                DmXml_.ConnectorInfo[i].SrcY,
                                DmXml_.ConnectorInfo[i].SrcW,
                                DmXml_.ConnectorInfo[i].SrcH)){
            HWC2_ALOGW("%s-%d enter CropSpilt Mode fail.",
                        connector_type_str(conn->type()), conn->type_id());
          }else{
            HWC2_ALOGI("%s-%d enter %s CropSpilt Mode.",
                        connector_type_str(conn->type()), conn->type_id(),
                        conn->IsSpiltPrimary() ? "Primary" : "External");
          }
        }else if(DmXml_.Mode == DRM_DISPLAY_MODE_HORIZONTAL_SPILT){
          if(conn->setHorizontalSpilt()){
            HWC2_ALOGW("%s-%d enter HorizontalSpilt Mode fail.",
                        connector_type_str(conn->type()), conn->type_id());
          }else{
            HWC2_ALOGI("%s-%d enter HorizontalSpilt Mode.",
                        connector_type_str(conn->type()), conn->type_id());
          }
        }
      }
    }
  }
  return 0;
}

void DrmDevice::InitResevedPlane(){
  // Reserved DrmPlane
  char reserved_planes_name[PROPERTY_VALUE_MAX] = {0};
  hwc_get_string_property("vendor.hwc.reserved_plane_name","NULL",reserved_planes_name);

  if(strcmp(reserved_planes_name,"NULL")){
    int reserved_plane_win_type = 0;
    std::string reserved_name;
    std::stringstream ss(reserved_planes_name);
    while(getline(ss, reserved_name, ',')) {
      for(auto &plane_group : plane_groups_){
        for(auto &p : plane_group->planes){
          if(!strcmp(p->name(),reserved_name.c_str())){
            plane_group->bReserved = true;
            reserved_plane_win_type = plane_group->win_type;
            HWC2_ALOGI("Reserved DrmPlane %s , win_type = 0x%x",
                reserved_planes_name,reserved_plane_win_type);
            break;
          }else{
            plane_group->bReserved = false;
          }
        }
    }

    // RK3566 must reserved a extra DrmPlane.
    if(soc_id_ == 0x3566 || soc_id_ == 0x3566a){
      switch(reserved_plane_win_type){
        case DRM_PLANE_TYPE_CLUSTER0_WIN0:
          reserved_plane_win_type |= DRM_PLANE_TYPE_CLUSTER1_WIN0;
          break;
        case DRM_PLANE_TYPE_CLUSTER0_WIN1:
          reserved_plane_win_type |= DRM_PLANE_TYPE_CLUSTER0_WIN0;
          break;
        case DRM_PLANE_TYPE_ESMART0_WIN0:
          reserved_plane_win_type |= DRM_PLANE_TYPE_ESMART1_WIN0;
          break;
        case DRM_PLANE_TYPE_ESMART1_WIN0:
          reserved_plane_win_type |= DRM_PLANE_TYPE_ESMART0_WIN0;
          break;
        case DRM_PLANE_TYPE_SMART0_WIN0:
          reserved_plane_win_type |= DRM_PLANE_TYPE_SMART1_WIN0;
          break;
        case DRM_PLANE_TYPE_SMART1_WIN0:
          reserved_plane_win_type |= DRM_PLANE_TYPE_SMART0_WIN0;
          break;
        default:
          reserved_plane_win_type = 0;
          break;
        }
        for(auto &plane_group : plane_groups_){
          if(reserved_plane_win_type & plane_group->win_type){
            plane_group->bReserved = true;
            ALOGI("%s,line=%d CommirMirror Reserved win_type = 0x%x",
              __FUNCTION__,__LINE__,reserved_plane_win_type);
            break;
          }else{
            plane_group->bReserved = false;
          }
        }
      }
    }
  }
  return;
}

std::tuple<int, int> DrmDevice::Init(int num_displays) {
  init_white_modes();
  int ret = InitEnvFromXml();
  if(ret){
    HWC2_ALOGW("InitEnvFromXml fail, non-fatal error, check for ok.");
  }
  // Baseparameter init.
  baseparameter_.Init();

  /* 避免错误打开 npu deviecs 而导致问题
   *  GKI版本原来的 /dev/dri/card0 设备可能会是NPU设备
   *  故需要修改成 drmOpen("rockchip", NULL)，避免出错
   */
  fd_.Set(drmOpen("rockchip", NULL));
  if (fd() < 0) {
    ALOGE("Failed to open drm rockchip devices %s", strerror(-errno));
    return std::make_tuple(-ENODEV, 0);
  }

  drmVersionPtr version = drmGetVersion(fd());
  if(version != NULL){
#ifdef version_major
#undef version_major
#endif
#ifdef version_minor
#undef version_minor
#endif
    drm_version_ = version->version_major;
    ALOGI("DrmVersion=%d.%d.%d",version->version_major,version->version_minor,version->version_patchlevel);
    drmFreeVersion(version);
  }

  // 更新全局 kernel drm 版本信息
  gSetDrmVersion(drm_version_);

  ret = drmSetClientCap(fd(), DRM_CLIENT_CAP_UNIVERSAL_PLANES, 1);
  if (ret) {
    ALOGE("Failed to set universal plane cap %d", ret);
    return std::make_tuple(ret, 0);
  }

  ret = drmSetClientCap(fd(), DRM_CLIENT_CAP_ATOMIC, 1);
  if (ret) {
    ALOGE("Failed to set atomic cap %d", ret);
    return std::make_tuple(ret, 0);
  }

#ifdef DRM_CLIENT_CAP_WRITEBACK_CONNECTORS
  ret = drmSetClientCap(fd(), DRM_CLIENT_CAP_WRITEBACK_CONNECTORS, 1);
  if (ret) {
    ALOGI("Failed to set writeback cap %d", ret);
    ret = 0;
  }
#endif

//    Android 11 and kernel 5.10 not need this call.
//    //Open Multi-area support.
//    ret = drmSetClientCap(fd(), DRM_CLIENT_CAP_SHARE_PLANES, 1);
//    if (ret) {
//      ALOGE("Failed to set share planes %d", ret);
//      return std::make_tuple(ret, 0);
//    }

#if USE_NO_ASPECT_RATIO
    //Disable Aspect Ratio
    ret = drmSetClientCap(fd(), DRM_CLIENT_CAP_ASPECT_RATIO, 0);
    if (ret) {
      ALOGE("Failed to disable Aspect Ratio %d", ret);
      return std::make_tuple(ret, 0);
    }
#endif

  drmModeResPtr res = drmModeGetResources(fd());
  if (!res) {
    ALOGE("Failed to get DrmDevice resources");
    return std::make_tuple(-ENODEV, 0);
  }

  min_resolution_ = std::pair<uint32_t, uint32_t>(res->min_width,
                                                  res->min_height);
  max_resolution_ = std::pair<uint32_t, uint32_t>(res->max_width,
                                                  res->max_height);

  // Assumes that the primary display will always be in the first
  // drm_device opened.
  bool found_primary = num_displays != 0;

  for (int i = 0; !ret && i < res->count_crtcs; ++i) {
    drmModeCrtcPtr c = drmModeGetCrtc(fd(), res->crtcs[i]);
    if (!c) {
      ALOGE("Failed to get crtc %d", res->crtcs[i]);
      ret = -ENODEV;
      break;
    }

    std::unique_ptr<DrmCrtc> crtc(new DrmCrtc(this, c, i));
    drmModeFreeCrtc(c);

    ret = crtc->Init();
    if (ret) {
      ALOGE("Failed to initialize crtc %d", res->crtcs[i]);
      break;
    }
    soc_id_ = crtc->get_soc_id();
    crtcs_.emplace_back(std::move(crtc));
  }

  std::vector<int> possible_clones;
  for (int i = 0; !ret && i < res->count_encoders; ++i) {
    drmModeEncoderPtr e = drmModeGetEncoder(fd(), res->encoders[i]);
    if (!e) {
      ALOGE("Failed to get encoder %d", res->encoders[i]);
      ret = -ENODEV;
      break;
    }

    std::vector<DrmCrtc *> possible_crtcs;
    DrmCrtc *current_crtc = NULL;
    for (auto &crtc : crtcs_) {
      if ((1 << crtc->pipe()) & e->possible_crtcs)
        possible_crtcs.push_back(crtc.get());

      if (crtc->id() == e->crtc_id)
        current_crtc = crtc.get();
    }

    std::unique_ptr<DrmEncoder> enc(
        new DrmEncoder(e, current_crtc, possible_crtcs));
    possible_clones.push_back(e->possible_clones);
    drmModeFreeEncoder(e);

    encoders_.emplace_back(std::move(enc));
  }

  for (unsigned int i = 0; i < encoders_.size(); i++) {
    for (unsigned int j = 0; j < encoders_.size(); j++)
      if (possible_clones[i] & (1 << j))
        encoders_[i]->AddPossibleClone(encoders_[j].get());
  }

  for (int i = 0; !ret && i < res->count_connectors; ++i) {
    drmModeConnectorPtr c = drmModeGetConnector(fd(), res->connectors[i]);
    if (!c) {
      ALOGE("Failed to get connector %d", res->connectors[i]);
      ret = -ENODEV;
      break;
    }

    std::vector<DrmEncoder *> possible_encoders;
    DrmEncoder *current_encoder = NULL;
    for (int j = 0; j < c->count_encoders; ++j) {
      for (auto &encoder : encoders_) {
        if (encoder->id() == c->encoders[j])
          possible_encoders.push_back(encoder.get());
        if (encoder->id() == c->encoder_id)
          current_encoder = encoder.get();
      }
    }
    std::unique_ptr<DrmConnector> conn(
        new DrmConnector(this, c, current_encoder, possible_encoders));


    drmModeFreeConnector(c);

    ret = conn->Init();
    if (ret) {
      ALOGE("Init connector %d failed", res->connectors[i]);
      break;
    }
    conn->UpdateModes();

    if (conn->writeback()){
      writeback_connectors_.emplace_back(std::move(conn));
    }else
      connectors_.emplace_back(std::move(conn));
  }

  // Spicling Mode
  if(UpdateInfoFromXml()){
    HWC2_ALOGW("UpdateInfoFromXml fail, non-fatal error, check for ok.");
  }

  ConfigurePossibleDisplays();

  DrmConnector *primary = NULL;
  if(isRK3528(soc_id_)){
    for (auto &conn : connectors_) {
      if(conn->type() == DRM_MODE_CONNECTOR_HDMIA){
        primary = conn.get();
        found_primary = true;
      }
    }
  }else{
    for (auto &conn : connectors_) {
      if (!(conn->possible_displays() & HWC_DISPLAY_PRIMARY_BIT))
        continue;
      if (conn->internal())
        continue;
      if (conn->state() != DRM_MODE_CONNECTED)
        continue;
      found_primary = true;
      if(NULL == primary){
        primary = conn.get();
      }else{
        // High priority devices can become the primary
        if(conn.get()->priority() < primary->priority()){
          primary = conn.get();
        }
      }
    }
  }

  if (!found_primary) {
    for (auto &conn : connectors_) {
      if (!(conn->possible_displays() & HWC_DISPLAY_PRIMARY_BIT))
        continue;
      if (conn->state() != DRM_MODE_CONNECTED)
        continue;
      found_primary = true;
      if(NULL == primary){
        primary = conn.get();
      }else{
        // High priority devices can become the primary
        if(conn.get()->priority() < primary->priority()){
          primary = conn.get();
        }
      }
    }
  }

  if (!found_primary) {
    for (auto &conn : connectors_) {
      if (!(conn->possible_displays() & HWC_DISPLAY_PRIMARY_BIT))
        continue;
      found_primary = true;
      if(NULL == primary){
        primary = conn.get();
      }else{
        // High priority devices can become the primary
        if(conn.get()->priority() < primary->priority()){
          primary = conn.get();
        }
      }
    }
  }

  if (!found_primary) {
    for (auto &conn : connectors_) {
      found_primary = true;
      conn->set_possible_displays(conn->possible_displays() | HWC_DISPLAY_PRIMARY_BIT);
      primary = conn.get();
      if (primary) break;
    }
  }

  if (!found_primary) {
    ALOGE("failed to find primary display\n");
    return std::make_tuple(-ENODEV, 0);
  }else{
    if(primary != NULL){
      primary->set_display(num_displays);
      displays_[num_displays] = num_displays;
      ++num_displays;
    }
  }

  for (auto &conn : connectors_) {
    if (primary == conn.get())
      continue;
    conn->set_display(num_displays);
    displays_[num_displays] = num_displays;
    ++num_displays;
  }

  // SpiltMode
  for (auto &conn : connectors_) {
    if(conn->isHorizontalSpilt()){
      HWC2_ALOGI("%s enable isHorizontalSpilt, to create SpiltModeDisplay id=0x%x",conn->unique_name(),conn->GetSpiltModeId());
      int spilt_display_id = conn->GetSpiltModeId();
      displays_[spilt_display_id] = spilt_display_id;
    }
  }

  if (res)
    drmModeFreeResources(res);

  // Catch-all for the above loops
  if (ret)
    return std::make_tuple(ret, 0);

  drmModePlaneResPtr plane_res = drmModeGetPlaneResources(fd());
  if (!plane_res) {
    ALOGE("Failed to get plane resources");
    return std::make_tuple(-ENOENT, 0);
  }

  for (uint32_t i = 0; i < plane_res->count_planes; ++i) {
    drmModePlanePtr p = drmModeGetPlane(fd(), plane_res->planes[i]);
    if (!p) {
      ALOGE("Failed to get plane %d", plane_res->planes[i]);
      ret = -ENODEV;
      break;
    }

    std::unique_ptr<DrmPlane> plane(new DrmPlane(this, p, soc_id_));

    ret = plane->Init();
    if (ret) {
      ALOGE("Init plane %d failed", plane_res->planes[i]);
      drmModeFreePlane(p);
      break;
    }
    uint64_t share_id,zpos,crtc_id;
    std::tie(ret, share_id) = plane->share_id_property().value();
    std::tie(ret, zpos) = plane->zpos_property().value();
    std::tie(ret, crtc_id) = plane->crtc_property().value();

    std::vector<PlaneGroup*>::const_iterator iter;
    for (iter = plane_groups_.begin();
     iter != plane_groups_.end(); ++iter){
      if((*iter)->share_id == share_id){
        (*iter)->planes.push_back(plane.get());
        break;
      }
    }
    if(iter == plane_groups_.end()){
      PlaneGroup* plane_group = new PlaneGroup();
      plane_group->bUse= false;
      plane_group->zpos = zpos;
      plane_group->possible_crtcs = p->possible_crtcs;
      plane_group->share_id = share_id;
      plane_group->win_type = plane->win_type();
      plane_group->planes.push_back(plane.get());
      plane_groups_.push_back(plane_group);
    }

    for (uint32_t j = 0; j < p->count_formats; j++) {
      if (p->formats[j] == DRM_FORMAT_NV12 ||
         p->formats[j] == DRM_FORMAT_NV21) {
             plane->set_yuv(true);
      }
    }
    sort_planes_.emplace_back(plane.get());

    drmModeFreePlane(p);

    planes_.emplace_back(std::move(plane));

  }

  std::sort(sort_planes_.begin(),sort_planes_.end(),PlaneSortByZpos);

  for (std::vector<DrmPlane*>::const_iterator iter= sort_planes_.begin();
     iter != sort_planes_.end(); ++iter) {
     uint64_t share_id,zpos;
     int error = 0;
     std::tie(error, share_id) = (*iter)->share_id_property().value();
     std::tie(error, zpos) = (*iter)->zpos_property().value();
     ALOGD_IF(LogLevel(DBG_DEBUG),"sort_planes_ share_id=%" PRIu64 ",zpos=%" PRIu64 "",share_id,zpos);
  }

  for (std::vector<PlaneGroup *> ::const_iterator iter = plane_groups_.begin();
         iter != plane_groups_.end(); ++iter)
  {
      ALOGD_IF(LogLevel(DBG_DEBUG),"Plane groups: zpos=%d,share_id=%" PRIu64 ",plane size=%zu",
          (*iter)->zpos,(*iter)->share_id,(*iter)->planes.size());
      for(std::vector<DrmPlane*> ::const_iterator iter_plane = (*iter)->planes.begin();
         iter_plane != (*iter)->planes.end(); ++iter_plane)
      {
          ALOGD_IF(LogLevel(DBG_DEBUG),"\tPlane id=%d",(*iter_plane)->id());
      }
  }
  ALOGD_IF(LogLevel(DBG_DEBUG),"--------------------sort plane--------------------");
  std::sort(plane_groups_.begin(),plane_groups_.end(),SortByWinType);
  for (std::vector<PlaneGroup *> ::const_iterator iter = plane_groups_.begin();
         iter != plane_groups_.end(); ++iter)
  {
      ALOGD_IF(LogLevel(DBG_DEBUG),"Plane groups: zpos=%d,share_id=%" PRIu64 ",plane size=%zu,possible_crtcs=0x%x",
          (*iter)->zpos,(*iter)->share_id,(*iter)->planes.size(),(*iter)->possible_crtcs);
      std::sort((*iter)->planes.begin(),(*iter)->planes.end(), PlaneSortByArea);
      for(std::vector<DrmPlane*> ::const_iterator iter_plane = (*iter)->planes.begin();
         iter_plane != (*iter)->planes.end(); ++iter_plane)
      {
          uint64_t area=0;
          if((*iter_plane)->area_id_property().id()) {
              uint64_t parm = 0;
              std::tie(parm, area) = (*iter_plane)->area_id_property().value();
          }
          ALOGD_IF(LogLevel(DBG_DEBUG),"\tPlane id=%d,area id=%" PRIu64 "",(*iter_plane)->id(),area);
      }
  }

  // Reserved DrmPlane
  InitResevedPlane();

  drmModeFreePlaneResources(plane_res);
  if (ret)
    return std::make_tuple(ret, 0);

  ret = event_listener_.Init();
  if (ret) {
    ALOGE("Can't initialize event listener %d", ret);
    return std::make_tuple(ret, 0);
  }

  hwcPlatform_ = HwcPlatform::CreateInstance(this);
  if (!hwcPlatform_) {
    ALOGE("Failed to create HwcPlatform instance");
    return std::make_tuple(-1, 0);
  }

  return std::make_tuple(ret, displays_.size());
}

bool DrmDevice::HandlesDisplay(int display) const {
  return displays_.find(display) != displays_.end();
}

void DrmDevice::SetCommitMirrorDisplayId(int display){
  commit_mirror_display_id_ = display;
}

int DrmDevice::GetCommitMirrorDisplayId() const {
  return commit_mirror_display_id_;
}

DrmConnector *DrmDevice::GetConnectorForDisplay(int display) const {
  for (auto &conn : connectors_) {
    if (conn->display() == (display & ~DRM_CONNECTOR_SPILT_MODE_MASK))
      return conn.get();
  }
  return NULL;
}

DrmConnector *DrmDevice::GetWritebackConnectorForDisplay(int display) const {
  for (auto &conn : writeback_connectors_) {
      return conn.get();
  }
  return NULL;
}

// TODO what happens when hotplugging
DrmConnector *DrmDevice::AvailableWritebackConnector(int display) const {
  DrmConnector *writeback_conn = GetWritebackConnectorForDisplay(display);
  DrmConnector *display_conn = GetConnectorForDisplay(display);
  // If we have a writeback already attached to the same CRTC just use that,
  // if possible.
  if (display_conn && writeback_conn &&
      writeback_conn->encoder()->CanClone(display_conn->encoder()))
    return writeback_conn;

  // Use another CRTC if available and doesn't have any connector
  for (auto &crtc : crtcs_) {
    if (crtc->display() == display)
      continue;
    display_conn = GetConnectorForDisplay(crtc->display());
    // If we have a display connected don't use it for writeback
    if (display_conn && display_conn->state() == DRM_MODE_CONNECTED)
      continue;
    writeback_conn = GetWritebackConnectorForDisplay(crtc->display());
    if (writeback_conn)
      return writeback_conn;
  }
  return NULL;
}

DrmCrtc *DrmDevice::GetCrtcForDisplay(int display) const {
  for (auto &crtc : crtcs_) {
    if (crtc->display() == (display & ~DRM_CONNECTOR_SPILT_MODE_MASK))
      return crtc.get();
  }
  return NULL;
}

DrmPlane *DrmDevice::GetPlane(uint32_t id) const {
  for (auto &plane : planes_) {
    if (plane->id() == id)
      return plane.get();
  }
  return NULL;
}

const std::vector<std::unique_ptr<DrmCrtc>> &DrmDevice::crtcs() const {
  return crtcs_;
}

uint32_t DrmDevice::next_mode_id() {
  return ++mode_id_;
}

int DrmDevice::TryEncoderForDisplay(int display, DrmEncoder *enc) {
  /* First try to use the currently-bound crtc */
  DrmCrtc *current_crtc = enc->crtc();
  if (current_crtc && current_crtc->can_bind(display)) {
    current_crtc->set_display(display);
    enc->set_crtc(current_crtc);
    return 0;
  }

  /* Try to find a possible crtc which will work */
  for (DrmCrtc *crtc : enc->possible_crtcs()) {
    /* We've already tried this earlier */
    if (crtc == enc->crtc())
      continue;

    if (crtc->can_bind(display)) {
      crtc->set_display(display);
      enc->set_crtc(crtc);
      return 0;
    }
  }

  /* We can't use the encoder, but nothing went wrong, try another one */
  return -EAGAIN;
}

int DrmDevice::CreateDisplayPipe(DrmConnector *connector) {
  int display = connector->display();
  /* Try to use current setup first */
  if (connector->encoder()) {
    int ret = TryEncoderForDisplay(display, connector->encoder());
    if (!ret) {
      return 0;
    } else if (ret != -EAGAIN) {
      ALOGE("Could not set mode %d/%d", display, ret);
      return ret;
    }
  }

  for (DrmEncoder *enc : connector->possible_encoders()) {
    int ret = TryEncoderForDisplay(display, enc);
    if (!ret) {
      connector->set_encoder(enc);
      return 0;
    } else if (ret != -EAGAIN) {
      ALOGE("Could not set mode %d/%d", display, ret);
      return ret;
    }
  }
  ALOGE("Could not find a suitable encoder/crtc for display %d",
        connector->display());
  return -ENODEV;
}

// Attach writeback connector to the CRTC linked to the display_conn
int DrmDevice::AttachWriteback(DrmConnector *display_conn) {
  DrmCrtc *display_crtc = display_conn->encoder()->crtc();
  if (GetWritebackConnectorForDisplay(display_crtc->display()) != NULL) {
    ALOGE("Display already has writeback attach to it");
    return -EINVAL;
  }
  for (auto &writeback_conn : writeback_connectors_) {
    if (writeback_conn->display() >= 0)
      continue;
    for (DrmEncoder *writeback_enc : writeback_conn->possible_encoders()) {
      for (DrmCrtc *possible_crtc : writeback_enc->possible_crtcs()) {
        if (possible_crtc != display_crtc)
          continue;
        // Use just encoders which had not been bound already
        if (writeback_enc->can_bind(display_crtc->display())) {
          writeback_enc->set_crtc(display_crtc);
          writeback_conn->set_encoder(writeback_enc);
          writeback_conn->set_display(display_crtc->display());
          writeback_conn->UpdateModes();
          return 0;
        }
      }
    }
  }
  return -EINVAL;
}

int DrmDevice::CreatePropertyBlob(void *data, size_t length,
                                  uint32_t *blob_id) {
  struct drm_mode_create_blob create_blob;
  memset(&create_blob, 0, sizeof(create_blob));
  create_blob.length = length;
  create_blob.data = (__u64)data;

  int ret = drmIoctl(fd(), DRM_IOCTL_MODE_CREATEPROPBLOB, &create_blob);
  if (ret) {
    ALOGE("Failed to create mode property blob %d", ret);
    return ret;
  }
  *blob_id = create_blob.blob_id;
  return 0;
}

int DrmDevice::DestroyPropertyBlob(uint32_t blob_id) {
  if (!blob_id)
    return 0;

  struct drm_mode_destroy_blob destroy_blob;
  memset(&destroy_blob, 0, sizeof(destroy_blob));
  destroy_blob.blob_id = (__u32)blob_id;
  int ret = drmIoctl(fd(), DRM_IOCTL_MODE_DESTROYPROPBLOB, &destroy_blob);
  if (ret) {
    ALOGE("Failed to destroy mode property blob %" PRIu32 "/%d", blob_id, ret);
    return ret;
  }
  return 0;
}

DrmEventListener *DrmDevice::event_listener() {
  return &event_listener_;
}

int DrmDevice::GetProperty(uint32_t obj_id, uint32_t obj_type,
                           const char *prop_name, DrmProperty *property) {
  drmModeObjectPropertiesPtr props;

  props = drmModeObjectGetProperties(fd(), obj_id, obj_type);
  if (!props) {
    ALOGE("Failed to get properties for %d/%x", obj_id, obj_type);
    return -ENODEV;
  }

  bool found = false;
  for (int i = 0; !found && (size_t)i < props->count_props; ++i) {
    drmModePropertyPtr p = drmModeGetProperty(fd(), props->props[i]);
    if (!strcmp(p->name, prop_name)) {
      property->Init(p, props->prop_values[i]);
      found = true;
    }
    drmModeFreeProperty(p);
  }

  drmModeFreeObjectProperties(props);
  return found ? 0 : -ENOENT;
}

int DrmDevice::GetPlaneProperty(const DrmPlane &plane, const char *prop_name,
                                DrmProperty *property) {
  return GetProperty(plane.id(), DRM_MODE_OBJECT_PLANE, prop_name, property);
}

int DrmDevice::GetCrtcProperty(const DrmCrtc &crtc, const char *prop_name,
                               DrmProperty *property) {
  return GetProperty(crtc.id(), DRM_MODE_OBJECT_CRTC, prop_name, property);
}

int DrmDevice::GetConnectorProperty(const DrmConnector &connector,
                                    const char *prop_name,
                                    DrmProperty *property) {
  return GetProperty(connector.id(), DRM_MODE_OBJECT_CONNECTOR, prop_name,
                     property);
}

// RK surport
void DrmDevice::ConfigurePossibleDisplays(){
  char primary_name[PROPERTY_VALUE_MAX];
  char extend_name[PROPERTY_VALUE_MAX];
  int primary_length, extend_length;
  int default_display_possible = 0;
  std::string conn_name;
  char acConnName[50];

  primary_length = property_get("vendor.hwc.device.primary", primary_name, NULL);
  extend_length = property_get("vendor.hwc.device.extend", extend_name, NULL);

  if (!primary_length)
    default_display_possible |= HWC_DISPLAY_PRIMARY_BIT;
  if (!extend_length)
    default_display_possible |= HWC_DISPLAY_EXTERNAL_BIT;

  for (auto &conn : connectors_) {
    /*
     * build_in connector default only support on primary display
     */
    if (conn->internal())
      conn->set_possible_displays(default_display_possible & HWC_DISPLAY_PRIMARY_BIT);
    else
      conn->set_possible_displays(default_display_possible & HWC_DISPLAY_EXTERNAL_BIT);
  }

  if (primary_length) {
    std::stringstream ss(primary_name);
    uint32_t connector_priority = 1;
    while(getline(ss, conn_name, ',')) {
      for (auto &conn : connectors_) {
        snprintf(acConnName,50,"%s-%d",connector_type_str(conn->type()),conn->type_id());
        if (!strcmp(connector_type_str(conn->type()), conn_name.c_str()) ||
            !strcmp(acConnName, conn_name.c_str()))
        {
          conn->set_priority(connector_priority);
          conn->set_possible_displays(HWC_DISPLAY_PRIMARY_BIT);
          connector_priority++;
        }
      }
    }
  }

  if (extend_length) {
    std::stringstream ss(extend_name);
    uint32_t connector_priority = 1;
    while(getline(ss, conn_name, ',')) {
      for (auto &conn : connectors_) {
        snprintf(acConnName,50,"%s-%d",connector_type_str(conn->type()),conn->type_id());
        if (!strcmp(connector_type_str(conn->type()), conn_name.c_str()) ||
            !strcmp(acConnName, conn_name.c_str()))
        {
          conn->set_priority(connector_priority);
          conn->set_possible_displays(conn->possible_displays() | HWC_DISPLAY_EXTERNAL_BIT);
          connector_priority++;
        }
      }
    }
  }
  return;
}

int DrmDevice::UpdateDisplayGamma(int display_id){
  std::unique_lock<std::recursive_mutex> lock(mRecursiveMutex);
  DrmConnector *conn = GetConnectorForDisplay(display_id);
  if (!conn || conn->state() != DRM_MODE_CONNECTED ||
      !conn->encoder() || !conn->encoder()->crtc()){

    return 0;
  }

  int ret=0;
  DrmCrtc *crtc = conn->encoder()->crtc();
  if(crtc->gamma_lut_property().id() == 0){
    ALOGI("%s,line=%d %s crtc-id=%d not support gamma.",__FUNCTION__,__LINE__,connector_type_str(conn->type()),crtc->id());

    return 0;
  }

  const struct disp_info* info = conn->baseparameter_info();
  if(info != NULL){
    unsigned blob_id = 0;
    int size = info->gamma_lut_data.size;
    struct drm_color_lut gamma_lut[size];
    for (int i = 0; i < size; i++) {
      gamma_lut[i].red = info->gamma_lut_data.lred[i];
      gamma_lut[i].green = info->gamma_lut_data.lgreen[i];
      gamma_lut[i].blue = info->gamma_lut_data.lblue[i];
    }
    ret = drmModeCreatePropertyBlob(fd_.get(), gamma_lut, sizeof(gamma_lut), &blob_id);
    if(ret){
      ALOGE("%s,line=%d %s crtc-id=%d CreatePropertyBlob  fail.",__FUNCTION__,__LINE__,connector_type_str(conn->type()),crtc->id());

      return ret;
    }
    ret = drmModeObjectSetProperty(fd_.get(), crtc->id(), DRM_MODE_OBJECT_CRTC, crtc->gamma_lut_property().id(), blob_id);
    if(ret){
      ALOGE("%s,line=%d %s crtc-id=%d gamma fail.",__FUNCTION__,__LINE__,connector_type_str(conn->type()),crtc->id());

      return ret;
    }
    ALOGD_IF(LogLevel(DBG_VERBOSE),"%s,line=%d, display=%d crtc-id=%d set Gamma success!",__FUNCTION__,__LINE__,
                                    crtc->id(),display_id);
  }

  return ret;

}

int DrmDevice::UpdateDisplay3DLut(int display_id){
  std::unique_lock<std::recursive_mutex> lock(mRecursiveMutex);
  DrmConnector *conn = GetConnectorForDisplay(display_id);

  if (!conn || conn->state() != DRM_MODE_CONNECTED ||
      !conn->encoder() || !conn->encoder()->crtc()){

    return 0;
  }

  DrmCrtc *crtc = conn->encoder()->crtc();
  if(crtc->cubic_lut_property().id() == 0){
    ALOGI("%s,line=%d %s crtc-id=%d not support cubic lut.",__FUNCTION__,__LINE__,connector_type_str(conn->type()),crtc->id());

    return 0;
  }
  const struct disp_info* info = conn->baseparameter_info();

  int ret=0;
  if(info != NULL){
    unsigned blob_id = 0;
    int size = info->cubic_lut_data.size;
    struct drm_color_lut cubit_lut[size];
    for (int i = 0; i < size; i++) {
      cubit_lut[i].red = info->cubic_lut_data.lred[i];
      cubit_lut[i].green = info->cubic_lut_data.lgreen[i];
      cubit_lut[i].blue = info->cubic_lut_data.lblue[i];
    }
    ret = drmModeCreatePropertyBlob(fd_.get(), cubit_lut, sizeof(cubit_lut), &blob_id);
    if(ret){
      ALOGE("%s,line=%d %s crtc-id=%d CreatePropertyBlob  fail.",__FUNCTION__,__LINE__,connector_type_str(conn->type()),crtc->id());

      return ret;
    }
    ret = drmModeObjectSetProperty(fd_.get(), crtc->id(), DRM_MODE_OBJECT_CRTC, crtc->cubic_lut_property().id(), blob_id);
    if(ret){
      ALOGE("%s,line=%d %s crtc-id=%d 3D Lut fail.",__FUNCTION__,__LINE__,connector_type_str(conn->type()),crtc->id());

      return ret;
    }
    ALOGD_IF(LogLevel(DBG_VERBOSE),"%s,line=%d, display=%d crtc-id=%d set 3DLut success!",__FUNCTION__,__LINE__,
                                    crtc->id(),display_id);
  }

  return ret;

}

int DrmDevice::UpdateDisplayMode(int display_id){
  std::unique_lock<std::recursive_mutex> lock(mRecursiveMutex);
  DrmConnector *conn = GetConnectorForDisplay(display_id);
  if (!conn || conn->state() != DRM_MODE_CONNECTED   ||
      !conn->current_mode().id() || !conn->encoder() ||
      !conn->encoder()->crtc() ||
       conn->current_mode() == conn->active_mode()){
    return 0;
  }

  // 判断是否存在Mirror模式
  if(conn->encoder() && conn->encoder()->crtc()) {
    DrmCrtc* crtc = conn->encoder()->crtc();
    DrmConnector *conn_mirror = NULL;
    // 检查是否存在 ConnectorMirror方式
    // 若存在，解除 ConnectorMirrot方式，断开所有与 crtc 绑定的 Connector
    // 若不存在，正常解绑 Connector 与 Crtc
    bool is_mirror = false;
    for(auto &temp_conn : connectors_){
      if(temp_conn.get() == conn)
        continue;
      if(temp_conn->encoder() &&
          temp_conn->encoder()->crtc() &&
          temp_conn->encoder()->crtc() == crtc){
        conn_mirror = temp_conn.get();
        is_mirror = true;
      }
    }
    if(is_mirror && conn_mirror != NULL){
      bool mirror_exist_mode = conn_mirror->isExistMode(conn->current_mode());
      HWC2_ALOGI("%s-%d will update display-mode=%dx%dp%f, %s-%d mirror display %s",
                connector_type_str(conn->type()),
                conn->type_id(),
                conn->current_mode().h_display(),
                conn->current_mode().v_display(),
                conn->current_mode().v_refresh(),
                connector_type_str(conn_mirror->type()),
                conn_mirror->type_id(),
                mirror_exist_mode ? "support" : "not support");
      // 若当前 Connector 不存在 Mirror模式
      if(!mirror_exist_mode){
        // 若存在Mirror模式
        int ret = ReleaseDpyResByMirror(conn_mirror->display(), conn_mirror, crtc, DmcuNone);
        if(ret){
          HWC2_ALOGE("display-id=%d ReleaseDpyResByMirror fail!.\n", display_id);
          return ret;
        }
        ret = BindDpyRes(display_id);
        if(ret){
          HWC2_ALOGE("display-id=%d BindDpyRes fail!.\n", display_id);
          return ret;
        }
      }
    }
  }

  //  Disable all plane resource with this connetor.
  {
    int ret;
    drmModeAtomicReqPtr pset = drmModeAtomicAlloc();
    if (!pset) {
      ALOGE("%s:line=%d Failed to allocate property set",__FUNCTION__, __LINE__);
      return -ENOMEM;
    }

    DrmCrtc *crtc = conn->encoder()->crtc();
    // Disable DrmPlane resource.
    for(auto &plane_group : plane_groups_){
      uint32_t crtc_mask = 1 << crtc->pipe();
      if(!plane_group->acquire(crtc_mask))
          continue;
      for(auto &plane : plane_group->planes){
        if(!plane)
          continue;
        ret = drmModeAtomicAddProperty(pset, plane->id(),
                                        plane->crtc_property().id(), 0) < 0 ||
              drmModeAtomicAddProperty(pset, plane->id(), plane->fb_property().id(),
                                        0) < 0;
        if (ret) {
          ALOGE("Failed to add plane %d disable to pset", plane->id());
          drmModeAtomicFree(pset);
          pset=NULL;
          return ret;
        }
        HWC2_ALOGI("Crtc-id = %d disable plane-id = %d", crtc->id(), plane->id());
      }
    }

    uint32_t flags = DRM_MODE_ATOMIC_ALLOW_MODESET;
    ret = drmModeAtomicCommit(fd_.get(), pset, flags, this);
    if (ret < 0) {
      ALOGE("%s:line=%d Failed to commit pset ret=%d\n", __FUNCTION__, __LINE__, ret);
      drmModeAtomicFree(pset);
      pset=NULL;
      return ret;
    }
    drmModeAtomicFree(pset);
    pset = NULL;
  }

  int ret;
  drmModeAtomicReqPtr pset = drmModeAtomicAlloc();
  if (!pset) {
    ALOGE("%s:line=%d Failed to allocate property set",__FUNCTION__, __LINE__);
    return -ENOMEM;
  }

  uint32_t blob_id[1] = {0};

  struct drm_mode_modeinfo drm_mode;
  memset(&drm_mode, 0, sizeof(drm_mode));
  conn->current_mode().ToDrmModeModeInfo(&drm_mode);
  ALOGD_IF(LogLevel(DBG_VERBOSE),"%s,line=%d, current_mode id=%d , w=%d,h=%d",__FUNCTION__,__LINE__,
            conn->current_mode().id(),conn->current_mode().h_display(),conn->current_mode().v_display());
  ret = CreatePropertyBlob(&drm_mode, sizeof(drm_mode), &blob_id[0]);
  if(ret){
    ALOGE("%s:line=%d Failed to CreatePropertyBlob ret=%d\n", __FUNCTION__, __LINE__, ret);
    drmModeAtomicFree(pset);
    pset=NULL;
    return ret;
  }

  DrmCrtc *crtc = conn->encoder()->crtc();

//  connector->SetDpmsMode(DRM_MODE_DPMS_ON);
//  DRM_ATOMIC_ADD_PROP(conn->id(), conn->dpms_property().id(), DRM_MODE_DPMS_ON);
    DRM_ATOMIC_ADD_PROP(conn->id(), conn->crtc_id_property().id(), crtc->id());
    DRM_ATOMIC_ADD_PROP(crtc->id(), crtc->mode_property().id(), blob_id[0]);
    DRM_ATOMIC_ADD_PROP(crtc->id(), crtc->active_property().id(), 1);

  uint32_t flags = DRM_MODE_ATOMIC_ALLOW_MODESET;
  ret = drmModeAtomicCommit(fd_.get(), pset, flags, this);
  if (ret < 0) {
    ALOGE("%s:line=%d Failed to commit pset ret=%d\n", __FUNCTION__, __LINE__, ret);
    drmModeAtomicFree(pset);
    pset=NULL;
    return ret;
  }

  if (blob_id[0])
    DestroyPropertyBlob(blob_id[0]);

  conn->set_active_mode(conn->current_mode());

#ifdef RK3528
  // RK3528 解码支持prescale,故希望获取屏幕分辨率作为是否开启prescale的依据
  char mode_name[50] = {0};
  sprintf(mode_name,"%dx%dp%d",conn->current_mode().h_display(),
                              conn->current_mode().v_display(),
                              (int)conn->current_mode().v_refresh());
  property_set("vendor.hwc.resolution_mode", mode_name);
#endif

  drmModeAtomicFree(pset);
  pset=NULL;

  hotplug_timeline++;

  return 0;
}

// Update VRR refresh rate
int DrmDevice::UpdateVrrRefreshRate(int display_id, int refresh_rate){
  std::unique_lock<std::recursive_mutex> lock(mRecursiveMutex);
  DrmConnector *conn = GetConnectorForDisplay(display_id);
  // 1. 检查 Connector 状态
  int ret = CheckConnectorState(display_id, conn);
  if(ret){
    return ret;
  }

  DrmCrtc *crtc = conn->encoder()->crtc();
  if(crtc != NULL && crtc->variable_refresh_rate().id() > 0){

    drmModeAtomicReqPtr pset = drmModeAtomicAlloc();
    if (!pset) {
      HWC2_ALOGE("%s:line=%d Failed to allocate property set",__FUNCTION__, __LINE__);
      return -ENOMEM;
    }

    uint64_t min_refresh_rate = 0;
    uint64_t max_refresh_rate = 0;
    std::tie(ret, min_refresh_rate) = crtc->min_refresh_rate().value();
    std::tie(ret, max_refresh_rate) = crtc->max_refresh_rate().value();
    if(refresh_rate < min_refresh_rate) refresh_rate = min_refresh_rate;
    if(refresh_rate > max_refresh_rate) refresh_rate = max_refresh_rate;
    ret = drmModeAtomicAddProperty(pset, crtc->id(),
                                  crtc->variable_refresh_rate().id(), refresh_rate) < 0;
    if (ret) {
      ALOGE("Failed to add variable_refresh_rate property %d to crtc %d",
            crtc->variable_refresh_rate().id(), crtc->id());
      drmModeAtomicFree(pset);
      pset=NULL;
      return -EINVAL;
    }
    // AtomicCommit
    uint32_t flags = DRM_MODE_ATOMIC_ALLOW_MODESET;
    ret = drmModeAtomicCommit(fd_.get(), pset, flags, this);
    if (ret < 0) {
      ALOGE("%s:line=%d Failed to commit pset ret=%d\n", __FUNCTION__, __LINE__, ret);
      drmModeAtomicFree(pset);
      pset=NULL;
      return ret;
    }
    drmModeAtomicFree(pset);
    pset=NULL;
    HWC2_ALOGI("display-id=%d Update Refresh Rate = %d success!.", display_id, refresh_rate);
  }

  return 0;
}

// 检查 Connector 状态
int DrmDevice::CheckConnectorState(int display_id, DrmConnector *conn){
  if (!conn) {
    HWC2_ALOGE("Failed to find display-id=%d connector\n", display_id);
    return -EINVAL;
  }

  if(conn->state() != DRM_MODE_CONNECTED){
    HWC2_ALOGE("display-id=%d connector state is disconnected\n",display_id);
    return -EINVAL;
  }
  return 0;
}

// 获取可用的 Crtc 资源
int DrmDevice::FindAvailableCrtc(int display_id, DrmConnector *conn, DrmCrtc **out_crtc){
  // 1. 第一次遍历所有可获取的空闲Crtc资源
  int ret = FindAvailableCrtcByFirst(display_id, conn, out_crtc);
  if(!ret)
    return ret;

  // 2. 尝试使用 ConnectorMirror方式
  ret = FindAvailableCrtcByMirror(display_id, conn, out_crtc);
  if(!ret)
    return ret;
  HWC2_ALOGI("Can't find available crtc for display-id=%d with conn[%d] by mirror.",
      display_id, conn->id());

  // 3. 若判断是否存在优先级，进行第二次遍历Crtc资源
  //    -> 若存在，则进行优先级抢占
  //    -> 若不存在，则直接抢占
  ret = FindAvailableCrtcByCompete(display_id, conn, out_crtc);
  if(!ret)
    return ret;
  HWC2_ALOGI("Can't find available crtc for display-id=%d with conn[%d] by compete.",
      display_id, conn->id());

  // 4. 没有找到可用的Crtc资源
  //    更新状态查询接口信息
  char conn_name[50];
  char property_conn_name[50];
  snprintf(conn_name,50,"%s-%d:connected-no-crtc",connector_type_str(conn->type()),conn->type_id());
  snprintf(property_conn_name,50,"vendor.hwc.device.display-%d", display_id);
  property_set(property_conn_name, conn_name);
  conn->set_hwc_state(HwcConnnectorStete::NO_CRTC);
  HWC2_ALOGW("Can't find available crtc for display-id=%d with conn[%d].",
      display_id, conn->id());
  return ret;
}

// 获取可用的 Crtc 资源
int DrmDevice::FindAvailableCrtcByFirst(int display_id, DrmConnector *conn, DrmCrtc **out_crtc){
  conn->set_encoder(NULL);
  for (DrmEncoder *enc : conn->possible_encoders()) {
    for (DrmCrtc *crtc : enc->possible_crtcs()) {
      if(crtc->can_bind(conn->display())){
        crtc->set_display(conn->display());
        enc->set_crtc(crtc);
        conn->set_encoder(enc);
        conn->set_hwc_state(HwcConnnectorStete::NORMAL);
        *out_crtc = crtc;
        HWC2_ALOGI("Find display-id=%d with conn[%d] crtc=%d success!\n",
            display_id, conn->id(), crtc->id());
        return 0;
      }
    }
  }
  // 2. 尝试获取状态未连接的Connector crtc
  for (DrmEncoder *enc : conn->possible_encoders()) {
    for (DrmCrtc *crtc : enc->possible_crtcs()) {
      int temp_display_id = crtc->display();
      DrmConnector* temp_conn = GetConnectorForDisplay(temp_display_id);
      // 2.1. 检查待竞争的Connector状态
      //      -> 若状态不正常，则直接抢占
      //      -> 若状态正常，则进行优先级抢占
      int ret = CheckConnectorState(temp_display_id, temp_conn);
      if(ret){ // 状态不正常
        // 解绑 temp_conn 与 crtc.
        ReleaseConnectorAndCrtc(temp_display_id,
                                temp_conn,
                                crtc);
        crtc->set_display(conn->display());
        enc->set_crtc(crtc);
        conn->set_encoder(enc);
        conn->set_hwc_state(HwcConnnectorStete::HOLD_CRTC);
        *out_crtc = crtc;
        HWC2_ALOGI("Find display-id=%d with conn[%d] crtc=%d success!",
            display_id, conn->id(), crtc->id());
        return 0;
      }
    }
  }
  return -1;
}

// 获取可用的 Crtc 资源
int DrmDevice::FindAvailableCrtcByMirror(int display_id, DrmConnector *conn, DrmCrtc **out_crtc){
  // 2. 尝试使用 ConnectorMirror方式
  for (DrmEncoder *enc : conn->possible_encoders()) {
    for (DrmCrtc *crtc : enc->possible_crtcs()) {
      int temp_display_id = crtc->display();
      DrmConnector* temp_conn = GetConnectorForDisplay(temp_display_id);
      // 2.1. 检查待竞争的Connector状态
      //      -> 若状态不正常，则直接退出Mirror方式
      //      -> 若状态正常，则进行Mirror匹配
      int ret = CheckConnectorState(temp_display_id, temp_conn);
      if(ret){ // 状态不正常
        continue;
      }else{
        DrmMode mirror_mode = temp_conn->active_mode();
        DrmMode current_mode = conn->current_mode();
        if(mirror_mode.id() > 0 && current_mode.id() > 0 &&
           current_mode.equal_no_flag_and_type(mirror_mode)){
          // mirror 不会修改crtc diplsy id
          // crtc->set_display(conn->display());
          enc->set_crtc(crtc);
          conn->set_encoder(enc);
          conn->set_hwc_state(HwcConnnectorStete::MIRROR_CRTC);
          temp_conn->set_hwc_state(HwcConnnectorStete::MIRROR_CRTC);
          *out_crtc = crtc;
          HWC2_ALOGI("Find display-id=%d with conn[%d] crtc=%d success!",
              display_id, conn->id(), crtc->id());
          return 0;
        }
      }
    }
  }
  return -1;
}

// 获取可用的 Crtc 资源
int DrmDevice::FindAvailableCrtcByCompete(int display_id, DrmConnector *conn, DrmCrtc **out_crtc){
  // 3. 若判断是否存在优先级，进行第二次遍历Crtc资源
  //    -> 若存在，则进行优先级抢占
  //    -> 若不存在，则直接抢占
  if(conn->priority() > 0){ // 存在优先级
    for (DrmEncoder *enc : conn->possible_encoders()) {
      for (DrmCrtc *crtc : enc->possible_crtcs()) {
        int temp_display_id = crtc->display();
        DrmConnector* temp_conn = GetConnectorForDisplay(temp_display_id);
        // 3.1. 检查待竞争的Connector状态
        //      -> 若状态不正常，则直接抢占
        //      -> 若状态正常，则进行优先级抢占
        int ret = CheckConnectorState(temp_display_id, temp_conn);
        if(ret){ // 状态不正常
          // 解绑 temp_conn 与 crtc.
          ReleaseConnectorAndCrtc(temp_display_id,
                                  temp_conn,
                                  crtc);
          crtc->set_display(conn->display());
          enc->set_crtc(crtc);
          conn->set_encoder(enc);
          conn->set_hwc_state(HwcConnnectorStete::HOLD_CRTC);
          *out_crtc = crtc;
          HWC2_ALOGI("Find display-id=%d with conn[%d] crtc=%d success!",
              display_id, conn->id(), crtc->id());
          return 0;
        }else{ // 若状态正常，则进行优先级抢占
          if(conn->priority() < temp_conn->priority()){
            // 解绑 temp_conn 与 crtc.
            ReleaseConnectorAndCrtc(temp_display_id,
                                    temp_conn,
                                    crtc);
            crtc->set_display(conn->display());
            enc->set_crtc(crtc);
            conn->set_encoder(enc);
            conn->set_hwc_state(HwcConnnectorStete::HOLD_CRTC);
            *out_crtc = crtc;
            HWC2_ALOGI("Find display-id=%d with conn[%d] crtc=%d success!",
                display_id, conn->id(), crtc->id());
            return 0;
          }
        }
      }
    }
  }else{
    for (DrmEncoder *enc : conn->possible_encoders()) {
      for (DrmCrtc *crtc : enc->possible_crtcs()) {
        int temp_display_id = crtc->display();
        DrmConnector * temp_conn = GetConnectorForDisplay(temp_display_id);
          // 解绑 temp_conn 与 crtc.
          ReleaseConnectorAndCrtc(temp_display_id,
                                  temp_conn,
                                  crtc);
          crtc->set_display(conn->display());
          enc->set_crtc(crtc);
          conn->set_encoder(enc);
          conn->set_hwc_state(HwcConnnectorStete::HOLD_CRTC);
          *out_crtc = crtc;
          HWC2_ALOGI("Find display-id=%d with conn[%d] crtc=%d success!",
              display_id, conn->id(), crtc->id());
          return 0;
      }
    }
  }
  // 没有找到可用的Crtc资源
  return -1;
}

// 绑定 Connector 与 Crtc 资源
int DrmDevice::BindConnectorAndCrtc(int display_id, DrmConnector* conn, DrmCrtc* crtc){
  // 更新状态查询接口信息
  char conn_name[50];
  char property_conn_name[50];
  if(conn->hwc_state() == MIRROR_CRTC){
    snprintf(conn_name,50,"%s-%d:%d:connected:mirror",connector_type_str(conn->type()),conn->type_id(),crtc->id());
  }else if(conn->hwc_state() == HOLD_CRTC){
    snprintf(conn_name,50,"%s-%d:%d:connected:compete",connector_type_str(conn->type()),conn->type_id(),crtc->id());
  }else{
    snprintf(conn_name,50,"%s-%d:%d:connected",connector_type_str(conn->type()),conn->type_id(),crtc->id());
  }
  snprintf(property_conn_name,50,"vendor.hwc.device.display-%d", display_id);
  property_set(property_conn_name, conn_name);

  // Check display mode.
  DrmMode current_mode = conn->current_mode();
  if(!current_mode.id()){
    HWC2_ALOGI("display-id=%d conn-id=%d current-id=%d is invalid.",
              display_id,conn->id(),conn->current_mode().id());
    return -EINVAL;
  }

  // 如果开机阶段当前设置的分辨率与 kernel uboot 初始化不一致，则需要关闭所有图层
  if(!current_mode.equal_no_flag_and_type(crtc->kernel_mode())){
    HWC2_ALOGI("Display-id=%d kernel-mode not equal to current-mode,"
               "must to disable all plane.", display_id);
    current_mode.dump();
    crtc->kernel_mode().dump();
    if(DisableAllPlaneForCrtc(display_id, crtc, true, NULL)){
      HWC2_ALOGW("display-id=%d crtc-id=%d display all plane fail!.", display_id, crtc->id());
    }
  }

  if(crtc->output_width_property().id() > 0){
    // 检查 crtc 的输出能力是否可以支持输出当前设置的分辨率，若不支持，则需要切换到支持的分辨率
    bool crtc_support_current_mode = true;
    uint64_t crtc_output_width_max = crtc->get_output_width();
    uint64_t crtc_output_dclk = crtc->get_output_dlck();
    // OUTPUT_WIDTH / OUTPUT_DCLK 用来计算VP的输出能力，计算公式为：
    // 1. 输出分辨率宽度限制： drmModeModeInfo.htotal <= OUTPUT_WIDTH
    // 2. 输出分辨率高度与刷新率限制：
    //   drmModeModeInfo.htotal * drmModeModeInfo.vtotal * drmModeModeInfo.vrefresh <= OUTPUT_DCLK
    if(current_mode.h_display() >  crtc_output_width_max){
      crtc_support_current_mode = false;
    }

    if(current_mode.h_display() *
      current_mode.v_display() *
      (uint64_t)current_mode.v_refresh() >  crtc_output_dclk){
      crtc_support_current_mode = false;
    }

    if(!crtc_support_current_mode){
      HWC2_ALOGW("current_mode=%dx%d@%f not support, vp-w=%" PRIu64 " vp-dclk=%" PRIu64  " must to change other mode.",
                current_mode.h_display(),
                current_mode.v_display(),
                current_mode.v_refresh(),
                crtc_output_width_max,
                crtc_output_dclk);
      conn->GetSuitableMode(display_id, crtc_output_width_max, crtc_output_dclk);
      current_mode = conn->current_mode();
    }
  }

  drmModeAtomicReqPtr pset = drmModeAtomicAlloc();
  if (!pset) {
    ALOGE("%s:line=%d Failed to allocate property set",__FUNCTION__, __LINE__);
    return -ENOMEM;
  }

  // Config display mode
  int ret;
  uint32_t blob_id[1] = {0};
  struct drm_mode_modeinfo drm_mode;
  memset(&drm_mode, 0, sizeof(drm_mode));
  conn->current_mode().ToDrmModeModeInfo(&drm_mode);
  HWC2_ALOGI("current_mode id=%d , w=%d,h=%d,fps=%f ",
              conn->current_mode().id(),
              conn->current_mode().h_display(),
              conn->current_mode().v_display(),
              conn->current_mode().v_refresh());
  CreatePropertyBlob(&drm_mode, sizeof(drm_mode), &blob_id[0]);

  // Enable DrmConnector DPMS on.
  // The note is due to HJC's suggestion that the DRM driver
  // will actively call the DPMS_ON interface when connecting Crtc and Connector,
  // and no additional calls are required.
  // conn->SetDpmsMode(DRM_MODE_DPMS_ON);

  // Bind DrmCrtc and DrmConnector
  DRM_ATOMIC_ADD_PROP(conn->id(), conn->crtc_id_property().id(), crtc->id());
  DRM_ATOMIC_ADD_PROP(crtc->id(), crtc->mode_property().id(), blob_id[0]);
  DRM_ATOMIC_ADD_PROP(crtc->id(), crtc->active_property().id(), 1);


#ifdef BOARD_BUILD_GKI
  // WriteBack connector only be binded by PrimaryDisplay
  if(display_id == 0){
    DrmConnector* writeback_conn = GetWritebackConnectorForDisplay(0);
    if(writeback_conn != NULL &&
       writeback_conn->writeback_fb_id().id() != 0 &&
       writeback_conn->writeback_out_fence().id() != 0){
      DRM_ATOMIC_ADD_PROP(writeback_conn->id(), writeback_conn->crtc_id_property().id(), crtc->id());
    }
  }
#endif

  uint32_t flags = DRM_MODE_ATOMIC_ALLOW_MODESET;
  ret = drmModeAtomicCommit(fd_.get(), pset, flags, this);
  if (ret < 0) {
    ALOGE("%s:line=%d Failed to commit pset ret=%d\n", __FUNCTION__, __LINE__, ret);
    drmModeAtomicFree(pset);
    pset=NULL;
    return ret;
  }
  drmModeAtomicFree(pset);
  pset=NULL;

  HWC2_ALOGI("display-id=%d Bind Connector-id=%d Crtc-id=%d success!.",
              display_id, conn->id(), crtc->id());

  DestroyPropertyBlob(blob_id[0]);

  conn->set_active_mode(conn->current_mode());

#ifdef RK3528
  // RK3528 解码支持prescale,故希望获取屏幕分辨率作为是否开启prescale的依据
  char mode_name[50] = {0};
  sprintf(mode_name,"%dx%dp%d",conn->current_mode().h_display(),
                              conn->current_mode().v_display(),
                              (int)conn->current_mode().v_refresh());
  property_set("vendor.hwc.resolution_mode", mode_name);
#endif

  return 0;
}

// 关闭当前 Crtc 与 Connector 资源
int DrmDevice::ReleaseConnectorAndCrtcNoCommit(int display_id,
                                               DrmConnector* conn,
                                               DrmCrtc *crtc,
                                               drmModeAtomicReqPtr pset){
  int ret = 0;
  if (!conn) {
    HWC2_ALOGE("Failed to find display-id=%d connector", display_id);
    return -EINVAL;
  }

  if(!pset){
    HWC2_ALOGE("pset is null, display-id=%d", display_id);
    return -EINVAL;
  }

  // 解绑需要关闭所有图层
  if(DisableAllPlaneForCrtc(display_id, crtc, false, pset)){
    HWC2_ALOGE("Failed to disable all plane for display %d", display_id);
  }

  // Disable DrmConnector resource.
  // The note is due to HJC's suggestion that the DRM driver
  // will actively call the DPMS_OFF interface when disconnecting the CRTC from the Connector,
  // and no additional calls are required.
  // conn->SetDpmsMode(DRM_MODE_DPMS_OFF);
  DRM_ATOMIC_ADD_PROP(conn->id(), conn->crtc_id_property().id(), 0);
  // Disable DrmCrtc resource.
  DRM_ATOMIC_ADD_PROP(crtc->id(), crtc->mode_property().id(), 0);
  DRM_ATOMIC_ADD_PROP(crtc->id(), crtc->active_property().id(), 0);

  HWC2_ALOGI("Add display-id=%d %s-%d Crtc-id=%d Release req success!.", display_id,
                                                                 connector_type_str(conn->type()),
                                                                 conn->type_id(),
                                                                 crtc->id());
  crtc->set_display(-1);
  conn->set_encoder(NULL);
  conn->set_hwc_state(HwcConnnectorStete::RELEASE_CRTC);

  // 更新属性状态
  char conn_name[50];
  char property_conn_name[50];
  snprintf(conn_name,50,"%s-%d:%d:release",connector_type_str(conn->type()),conn->type_id(),crtc->id());
  snprintf(property_conn_name,50,"vendor.hwc.device.display-%d",display_id);
  property_set(property_conn_name, conn_name);
  return 0;
}

// 关闭当前 Crtc 与 Connector 资源
int DrmDevice::ReleaseConnectorAndCrtc(int display_id, DrmConnector* conn, DrmCrtc *crtc){
  int ret;
  if (!conn) {
    HWC2_ALOGE("Failed to find display-id=%d connector", display_id);
    return -EINVAL;
  }

  drmModeAtomicReqPtr pset = drmModeAtomicAlloc();
  if (!pset) {
    ALOGE("%s:line=%d Failed to allocate property set",__FUNCTION__, __LINE__);
    return -ENOMEM;
  }

  // 解绑需要关闭所有图层
  if(DisableAllPlaneForCrtc(display_id, crtc, false, pset)){
    HWC2_ALOGE("Failed to disable all plane for display %d", display_id);
  }

  // Disable DrmConnector resource.
  // The note is due to HJC's suggestion that the DRM driver
  // will actively call the DPMS_OFF interface when disconnecting the CRTC from the Connector,
  // and no additional calls are required.
  // conn->SetDpmsMode(DRM_MODE_DPMS_OFF);
  DRM_ATOMIC_ADD_PROP(conn->id(), conn->crtc_id_property().id(), 0);
  // Disable DrmCrtc resource.
  DRM_ATOMIC_ADD_PROP(crtc->id(), crtc->mode_property().id(), 0);
  DRM_ATOMIC_ADD_PROP(crtc->id(), crtc->active_property().id(), 0);

  // AtomicCommit
  uint32_t flags = DRM_MODE_ATOMIC_ALLOW_MODESET;
  ret = drmModeAtomicCommit(fd_.get(), pset, flags, this);
  if (ret < 0) {
    HWC2_ALOGW("display-id=%d %s-%d Crtc-id=%d Release fail! ret=%d",
                display_id,
                connector_type_str(conn->type()),
                conn->type_id(),
                crtc->id(),
                ret);
    drmModeAtomicFree(pset);
    pset=NULL;
    return ret;
  }

  HWC2_ALOGI("display-id=%d %s-%d Crtc-id=%d Release success!.", display_id,
                                                                 connector_type_str(conn->type()),
                                                                 conn->type_id(),
                                                                 crtc->id());
  drmModeAtomicFree(pset);
  pset=NULL;

  crtc->set_display(-1);
  conn->set_encoder(NULL);
  conn->set_hwc_state(HwcConnnectorStete::RELEASE_CRTC);
  // 更新属性状态
  char conn_name[50];
  char property_conn_name[50];
  snprintf(conn_name,50,"%s-%d:%d:release",connector_type_str(conn->type()),conn->type_id(),crtc->id());
  snprintf(property_conn_name,50,"vendor.hwc.device.display-%d",display_id);
  property_set(property_conn_name, conn_name);
  return 0;
}

// 关闭所有 DrmPlane
int DrmDevice::DisableAllPlaneForCrtc(int display_id,
                                      DrmCrtc *crtc,
                                      bool commit,
                                      drmModeAtomicReqPtr pset){
  int ret;


  if(commit){
    if(pset != NULL){
      drmModeAtomicFree(pset);
      pset=NULL;
    }
    pset = drmModeAtomicAlloc();
    if (!pset) {
      ALOGE("%s:line=%d Failed to allocate property set",__FUNCTION__, __LINE__);
      return -ENOMEM;
    }
  }

  // Disable DrmPlane resource.
  for(auto &plane_group : plane_groups_){
    uint32_t crtc_mask = 1 << crtc->pipe();
    if(!plane_group->acquire(crtc_mask))
        continue;
    for(auto &plane : plane_group->planes){
      if(!plane)
        continue;
      ret = drmModeAtomicAddProperty(pset, plane->id(),
                                      plane->crtc_property().id(), 0) < 0 ||
            drmModeAtomicAddProperty(pset, plane->id(), plane->fb_property().id(),
                                      0) < 0;
      if (ret) {
        drmModeAtomicFree(pset);
        pset=NULL;
        HWC2_ALOGE("Failed to add plane %d disable to pset", plane->id());
        return ret;
      }
      HWC2_ALOGD_IF_DEBUG("disable CRTC(%d), disable plane-id = %d", crtc->id(),plane->id());
    }
  }

  if(commit){
    // AtomicCommit
    uint32_t flags = DRM_MODE_ATOMIC_ALLOW_MODESET;
    ret = drmModeAtomicCommit(fd_.get(), pset, flags, this);
    if (ret < 0) {
      ALOGE("%s:line=%d Failed to commit pset ret=%d\n", __FUNCTION__, __LINE__, ret);
      drmModeAtomicFree(pset);
      pset=NULL;
      return ret;
    }
    drmModeAtomicFree(pset);
    pset=NULL;
  }
  return 0;
}

// Bind DrmConnector and DrmCrtc resource.
int DrmDevice::BindDpyRes(int display_id){
  std::unique_lock<std::recursive_mutex> lock(mRecursiveMutex);
  DrmConnector *conn = GetConnectorForDisplay(display_id);
  // 1. 检查 Connector 状态
  int ret = CheckConnectorState(display_id, conn);
  if(ret){
    return ret;
  }

  // 2. 获取可用的 crtc 资源
  DrmCrtc *crtc = NULL;
  ret = FindAvailableCrtc(display_id, conn, &crtc);
  if(ret){
    return ret;
  }

  // 3. 绑定 Connector and Crtc 资源并使能
  ret = BindConnectorAndCrtc(display_id, conn, crtc);
  if(ret){
    return ret;
  }

  // 4. 绑定 DrmPlane 资源
  ret = hwcPlatform_->TryAssignPlane(this);
  if(ret){
    HWC2_ALOGW("TryAssignPlane fail, ret = %d", ret);
    return ret;
  }

  // 若上次休眠当前 display-id 存在 Mirror Connector
  // 则恢复上次休眠前状态
  if(mMapMirrorStateStore_.count(display_id)){
    auto mirror_connectors = mMapMirrorStateStore_.find(display_id);
    if(mirror_connectors != mMapMirrorStateStore_.end()){
      for(auto temp_connector : mirror_connectors->second){
        if(!temp_connector)
          continue;
        // 1. 检查 Connector 状态
        ret = CheckConnectorState(temp_connector->display(), temp_connector);
        if(ret){
          return ret;
        }

        // 2. 获取可用的 crtc 资源
        DrmCrtc *tmp_crtc = NULL;
        ret = FindAvailableCrtc(temp_connector->display(), temp_connector, &tmp_crtc);
        if(ret){
          return ret;
        }

        // 3. 绑定 Connector and Crtc 资源并使能
        ret = BindConnectorAndCrtc(temp_connector->display(), temp_connector, tmp_crtc);
        if(ret){
          return ret;
        }
      }
    }
    mMapMirrorStateStore_.erase(mirror_connectors);
  }

  return 0;
}

// Release DrmConnector and DrmCrtc resource.
int DrmDevice::ReleaseDpyRes(int display_id, DrmModeChangeUsage usage ){
  std::unique_lock<std::recursive_mutex> lock(mRecursiveMutex);
  int ret;
  DrmConnector *conn = GetConnectorForDisplay(display_id);
  if (!conn) {
    HWC2_ALOGE("Failed to find display-id=%d connector\n", display_id);
    return -EINVAL;
  }

  if(conn->encoder() && conn->encoder()->crtc()) {
    DrmCrtc* crtc = conn->encoder()->crtc();
    // 检查是否存在 ConnectorMirror方式
    // 若存在，解除 ConnectorMirrot方式，断开所有与 crtc 绑定的 Connector
    // 若不存在，正常解绑 Connector 与 Crtc
    bool is_mirror = false;
    for(auto &temp_conn : connectors_){
      if(temp_conn.get() == conn)
        continue;
      if(temp_conn->encoder() &&
          temp_conn->encoder()->crtc() &&
          temp_conn->encoder()->crtc() == crtc){
        is_mirror = true;
      }
    }
    // 若当前 Connector 不存在 Mirror模式
    if(!is_mirror){
      ret = ReleaseDpyResByNormal(display_id, conn, crtc);
      if(ret){
        HWC2_ALOGE("display-id=%d ReleaseDpyResByNormal fail!.\n", display_id);
        return ret;
      }
    }else{// 若存在Mirror模式
      ret = ReleaseDpyResByMirror(display_id, conn, crtc, usage);
      if(ret){
        HWC2_ALOGE("display-id=%d ReleaseDpyResByMirror fail!.\n", display_id);
        return ret;
      }
    }
  }

  // 4. 绑定 DrmPlane 资源
  ret = hwcPlatform_->TryAssignPlane(this);
  if(ret){
    HWC2_ALOGW("TryAssignPlane fail, ret = %d", ret);
    return ret;
  }

  return 0;
}

// Release DrmConnector and DrmCrtc resource.
int DrmDevice::ReleaseDpyResByMirror(int display_id,
                                     DrmConnector* conn,
                                     DrmCrtc* crtc,
                                     DrmModeChangeUsage usage){

  int ret;
  drmModeAtomicReqPtr pset = drmModeAtomicAlloc();
  if (!pset) {
    HWC2_ALOGE("%s:line=%d Failed to allocate property set",__FUNCTION__, __LINE__);
    return -ENOMEM;
  }

  // 1. 解绑当前 Connector 与 Crtc.
  ret = ReleaseConnectorAndCrtcNoCommit(display_id, conn, crtc, pset);
  if(ret){
    HWC2_ALOGE("Add display-id=%d %s-%d Crtc-id=%d Release req Fail!.",
                display_id, connector_type_str(conn->type()),
                conn->type_id(), crtc->id());
    drmModeAtomicFree(pset);
    pset=NULL;
    return ret;
  }

  // 2. 遍历所有 Connector , 关闭所有绑定在同个Crtc上的Connector资源
  std::vector<DrmConnector*> store_mirror_conn;
  for(auto &temp_conn : connectors_){
    if(temp_conn.get() == conn)
      continue;
    if(temp_conn->encoder() &&
        temp_conn->encoder()->crtc() &&
        temp_conn->encoder()->crtc() == crtc){
      int temp_display_id = temp_conn->display();
      DrmCrtc* temp_crtc = temp_conn->encoder()->crtc();
      ret = ReleaseConnectorAndCrtcNoCommit(temp_display_id, temp_conn.get(), temp_crtc, pset);
      if(ret){
        HWC2_ALOGE("Add display-id=%d %s-%d Crtc-id=%d Release req Fail!.",
                    temp_display_id, connector_type_str(temp_conn->type()),
                    temp_conn->type_id(), temp_crtc->id());
        drmModeAtomicFree(pset);
        pset=NULL;
        return ret;
      }
      store_mirror_conn.push_back(temp_conn.get());
    }
  }

#ifdef BOARD_BUILD_GKI
  // WriteBack connector only be binded by PrimaryDisplay
  if(display_id == 0){
    DrmConnector* writeback_conn = GetWritebackConnectorForDisplay(0);
    if(writeback_conn != NULL &&
       writeback_conn->writeback_fb_id().id() != 0 &&
       writeback_conn->writeback_out_fence().id() != 0){
      DRM_ATOMIC_ADD_PROP(writeback_conn->id(), writeback_conn->crtc_id_property().id(), 0);
    }
  }
#endif

  // 3. AtomicCommit
  uint32_t flags = DRM_MODE_ATOMIC_ALLOW_MODESET;
  ret = drmModeAtomicCommit(fd_.get(), pset, flags, this);
  if (ret < 0) {
    ALOGE("%s:line=%d Failed to commit pset ret=%d\n", __FUNCTION__, __LINE__, ret);
    drmModeAtomicFree(pset);
    pset=NULL;
    return ret;
  }
  drmModeAtomicFree(pset);
  pset=NULL;

  HWC2_ALOGI("display-id=%d %s-%d Crtc-id=%d Release Mirror Mode Success!.",
              display_id, connector_type_str(conn->type()),
              conn->type_id(), crtc->id());

  char conn_name[50];
  char property_conn_name[50];
  snprintf(conn_name,50,"%s-%d:%d:disconnected",connector_type_str(conn->type()),conn->type_id(),crtc->id());
  snprintf(property_conn_name,50,"vendor.hwc.device.display-%d",display_id);
  property_set(property_conn_name, conn_name);

  // 休眠/唤醒等电源操作不进行重新绑定操作
  // 记录当前状态，表明休眠时是存在Mirror Connector
  // 下次唤醒时需要重新绑定 Mirror Connector
  if(usage == DmcuReleaseByPowerMode){
    // 如果存在与当前屏幕绑定的 Mirror Connector
    // 则保存当前状态，下次唤醒时恢复 Mirror 状态
    if(store_mirror_conn.size() > 0){
      mMapMirrorStateStore_[display_id] = store_mirror_conn;
      for(auto &temp_conn : store_mirror_conn){
        if (!temp_conn) {
          continue;
        }
        snprintf(conn_name,50,"%s-%d:%d:mirror-disconnected",
                 connector_type_str(temp_conn->type()),
                 temp_conn->type_id(),
                 crtc->id());
        snprintf(property_conn_name,50,"vendor.hwc.device.display-%d", temp_conn->display());
        property_set(property_conn_name, conn_name);
      }
    }
  }else{
    // 4. 重新绑定其他 Connector与 Crtc
    for(auto temp_conn : store_mirror_conn){
      int temp_display_id = temp_conn->display();
      // 4.1 检查 Connector 状态
      ret = CheckConnectorState(temp_display_id, temp_conn);
      if (ret) {
        return ret;
      }

      // 4.2 获取可用的 crtc 资源
      DrmCrtc *temp_crtc = NULL;
      ret = FindAvailableCrtc(temp_display_id, temp_conn, &temp_crtc);
      if (ret) {
        return ret;
      }

      // 4.3 绑定 Connector and Crtc 资源并使能
      ret = BindConnectorAndCrtc(temp_display_id, temp_conn, temp_crtc);
      if (ret) {
        return ret;
      }
      HWC2_ALOGI("display-id=%d %s-%d Crtc-id=%d exit Mirror Mode Success! Enter Normal Mode.",
                  temp_display_id, connector_type_str(temp_conn->type()),
                  temp_conn->type_id(), temp_crtc->id());
    }
  }

  return 0;
}

// Release DrmConnector and DrmCrtc resource.
int DrmDevice::ReleaseDpyResByNormal(int display_id,
                                     DrmConnector* conn,
                                     DrmCrtc* crtc){
  int ret;
  drmModeAtomicReqPtr pset = drmModeAtomicAlloc();
  if (!pset) {
    HWC2_ALOGE("%s:line=%d Failed to allocate property set",__FUNCTION__, __LINE__);
    return -ENOMEM;
  }

  // Disable DrmConnector resource.
  // The note is due to HJC's suggestion that the DRM driver
  // will actively call the DPMS_OFF interface when disconnecting the CRTC from the Connector,
  // and no additional calls are required.
  // conn->SetDpmsMode(DRM_MODE_DPMS_OFF);
  DRM_ATOMIC_ADD_PROP(conn->id(), conn->crtc_id_property().id(), 0);

  // Disable DrmPlane resource.
  DisableAllPlaneForCrtc(display_id, crtc, false, pset);

  // Disable DrmCrtc resource.
  DRM_ATOMIC_ADD_PROP(crtc->id(), crtc->mode_property().id(), 0);
  DRM_ATOMIC_ADD_PROP(crtc->id(), crtc->active_property().id(), 0);

#ifdef BOARD_BUILD_GKI
  // WriteBack connector only be binded by PrimaryDisplay
  if(display_id == 0){
    DrmConnector* writeback_conn = GetWritebackConnectorForDisplay(0);
    if(writeback_conn != NULL &&
       writeback_conn->writeback_fb_id().id() != 0 &&
       writeback_conn->writeback_out_fence().id() != 0){
      DRM_ATOMIC_ADD_PROP(writeback_conn->id(), writeback_conn->crtc_id_property().id(), 0);
    }
  }
#endif

  // AtomicCommit
  uint32_t flags = DRM_MODE_ATOMIC_ALLOW_MODESET;
  ret = drmModeAtomicCommit(fd_.get(), pset, flags, this);
  if (ret < 0) {
    ALOGE("%s:line=%d Failed to commit pset ret=%d\n", __FUNCTION__, __LINE__, ret);
    drmModeAtomicFree(pset);
    pset=NULL;
    return ret;
  }

  drmModeAtomicFree(pset);
  pset=NULL;

  HWC2_ALOGI("display-id=%d PowerDown success!.", display_id);

  crtc->set_display(-1);
  conn->set_encoder(NULL);
  char conn_name[50];
  char property_conn_name[50];
  snprintf(conn_name,50,"%s-%d:%d:disconnected",connector_type_str(conn->type()),conn->type_id(),crtc->id());
  snprintf(property_conn_name,50,"vendor.hwc.device.display-%d",display_id);
  property_set(property_conn_name, conn_name);
  return 0;
}



int DrmDevice::timeline(void) {
  return hotplug_timeline;
}

#define ARRAY_SIZE(arr) (sizeof(arr) / sizeof((arr)[0]))
static inline int64_t U642I64(uint64_t val)
{
  return (int64_t)*((int64_t *)&val);
}

struct type_name {
  int type;
  const char *name;
};

#define type_name_fn(res) \
  const char * DrmDevice::res##_str(int type) {      \
    unsigned int i;         \
    for (i = 0; i < ARRAY_SIZE(res##_names); i++) { \
      if (res##_names[i].type == type)  \
        return res##_names[i].name; \
    }           \
    return "(invalid)";       \
  }

struct type_name encoder_type_names[] = {
  { DRM_MODE_ENCODER_NONE, "none" },
  { DRM_MODE_ENCODER_DAC, "DAC" },
  { DRM_MODE_ENCODER_TMDS, "TMDS" },
  { DRM_MODE_ENCODER_LVDS, "LVDS" },
  { DRM_MODE_ENCODER_TVDAC, "TVDAC" },
};

type_name_fn(encoder_type)

struct type_name connector_status_names[] = {
  { DRM_MODE_CONNECTED, "connected" },
  { DRM_MODE_DISCONNECTED, "disconnected" },
  { DRM_MODE_UNKNOWNCONNECTION, "unknown" },
};

type_name_fn(connector_status)

struct type_name connector_type_names[] = {
  { DRM_MODE_CONNECTOR_Unknown, "unknown" },
  { DRM_MODE_CONNECTOR_VGA, "VGA" },
  { DRM_MODE_CONNECTOR_DVII, "DVI-I" },
  { DRM_MODE_CONNECTOR_DVID, "DVI-D" },
  { DRM_MODE_CONNECTOR_DVIA, "DVI-A" },
  { DRM_MODE_CONNECTOR_Composite, "composite" },
  { DRM_MODE_CONNECTOR_SVIDEO, "s-video" },
  { DRM_MODE_CONNECTOR_LVDS, "LVDS" },
  { DRM_MODE_CONNECTOR_Component, "component" },
  { DRM_MODE_CONNECTOR_9PinDIN, "9-pin DIN" },
  { DRM_MODE_CONNECTOR_DisplayPort, "DP" },
  { DRM_MODE_CONNECTOR_HDMIA, "HDMI-A" },
  { DRM_MODE_CONNECTOR_HDMIB, "HDMI-B" },
  { DRM_MODE_CONNECTOR_TV, "TV" },
  { DRM_MODE_CONNECTOR_eDP, "eDP" },
  { DRM_MODE_CONNECTOR_VIRTUAL, "Virtual" },
  { DRM_MODE_CONNECTOR_DSI, "DSI" },
  { DRM_MODE_CONNECTOR_DPI, "DPI" },
};

type_name_fn(connector_type)

#define bit_name_fn(res)					\
  const char * res##_str(int type, std::ostringstream *out) {       \
    unsigned int i;           \
    const char *sep = "";         \
    for (i = 0; i < ARRAY_SIZE(res##_names); i++) {   \
      if (type & (1 << i)) {        \
        *out << sep << res##_names[i];  \
        sep = ", ";       \
      }           \
    }             \
    return NULL;            \
  }

static const char *mode_type_names[] = {
  "builtin",
  "clock_c",
  "crtc_c",
  "preferred",
  "default",
  "userdef",
  "driver",
};

static bit_name_fn(mode_type)

static const char *mode_flag_names[] = {
  "phsync",
  "nhsync",
  "pvsync",
  "nvsync",
  "interlace",
  "dblscan",
  "csync",
  "pcsync",
  "ncsync",
  "hskew",
  "bcast",
  "pixmux",
  "dblclk",
  "clkdiv2"
};
static bit_name_fn(mode_flag)


void DrmDevice::DumpMode(drmModeModeInfo *mode, std::ostringstream *out) {
  *out << mode->name << " " << mode->vrefresh << " "
       << mode->hdisplay << " " << mode->hsync_start << " "
       << mode->hsync_end << " " << mode->htotal << " "
       << mode->vdisplay << " " << mode->vsync_start << " "
       << mode->vsync_end << " " << mode->vtotal;

  *out << " flags: ";
  mode_flag_str(mode->flags, out);
  *out << " types: " << mode->type << "\n";
    mode_type_str(mode->type, out);
}

void DrmDevice::DumpBlob(uint32_t blob_id, std::ostringstream *out) {
  uint32_t i;
  unsigned char *blob_data;
  drmModePropertyBlobPtr blob;

  blob = drmModeGetPropertyBlob(fd(), blob_id);
  if (!blob) {
    *out << "\n";
    return;
  }

  blob_data = (unsigned char*)blob->data;

  for (i = 0; i < blob->length; i++) {
    if (i % 16 == 0)
      *out << "\n\t\t\t";
    *out << std::hex << blob_data[i];
  }
  *out << "\n";

  drmModeFreePropertyBlob(blob);
}

void DrmDevice::DumpProp(drmModePropertyPtr prop,
          uint32_t prop_id, uint64_t value, std::ostringstream *out) {
  int i;

  *out << "\t" << prop_id;
  if (!prop) {
    *out << "\n";
    return;
  }
  out->str("");
  *out << " " << prop->name << ":\n";

  *out << "\t\tflags:";
  if (prop->flags & DRM_MODE_PROP_PENDING)
    *out << " pending";
  if (prop->flags & DRM_MODE_PROP_IMMUTABLE)
    *out << " immutable";
  if (drm_property_type_is(prop, DRM_MODE_PROP_SIGNED_RANGE))
    *out << " signed range";
  if (drm_property_type_is(prop, DRM_MODE_PROP_RANGE))
    *out << " range";
  if (drm_property_type_is(prop, DRM_MODE_PROP_ENUM))
    *out << " enum";
  if (drm_property_type_is(prop, DRM_MODE_PROP_BITMASK))
    *out << " bitmask";
  if (drm_property_type_is(prop, DRM_MODE_PROP_BLOB))
    *out << " blob";
  if (drm_property_type_is(prop, DRM_MODE_PROP_OBJECT))
    *out << " object";
  *out << "\n";

  if (drm_property_type_is(prop, DRM_MODE_PROP_SIGNED_RANGE)) {
    *out << "\t\tvalues:";
    for (i = 0; i < prop->count_values; i++)
      *out << U642I64(prop->values[i]);
    *out << "\n";
  }

  if (drm_property_type_is(prop, DRM_MODE_PROP_RANGE)) {
    *out << "\t\tvalues:";
    for (i = 0; i < prop->count_values; i++)
      *out << prop->values[i];
    *out << "\n";
  }

  if (drm_property_type_is(prop, DRM_MODE_PROP_ENUM)) {
    *out << "\t\tenums:";
    for (i = 0; i < prop->count_enums; i++)
      *out << prop->enums[i].name << "=" << prop->enums[i].value;
    *out << "\n";
  } else if (drm_property_type_is(prop, DRM_MODE_PROP_BITMASK)) {
    *out << "\t\tvalues:";
    for (i = 0; i < prop->count_enums; i++)
      *out << prop->enums[i].name << "=" << std::hex << (1LL << prop->enums[i].value);
    *out << "\n";
  } else {
    assert(prop->count_enums == 0);
  }

  if (drm_property_type_is(prop, DRM_MODE_PROP_BLOB)) {
    *out << "\t\tblobs:\n";
    for (i = 0; i < prop->count_blobs; i++)
      DumpBlob(prop->blob_ids[i], out);
    *out << "\n";
  } else {
    assert(prop->count_blobs == 0);
  }

  *out << "\t\tvalue:";
  if (drm_property_type_is(prop, DRM_MODE_PROP_BLOB))
    DumpBlob(value, out);
  else
    *out << value;

    *out << "\n";
}

int DrmDevice::DumpProperty(uint32_t obj_id, uint32_t obj_type, std::ostringstream *out) {
  drmModePropertyPtr* prop_info;
  drmModeObjectPropertiesPtr props;

  props = drmModeObjectGetProperties(fd(), obj_id, obj_type);
  if (!props) {
    ALOGE("Failed to get properties for %d/%x", obj_id, obj_type);
    return -ENODEV;
  }
  prop_info = (drmModePropertyPtr*)malloc(props->count_props * sizeof *prop_info);
  if (!prop_info) {
    ALOGE("Malloc drmModePropertyPtr array failed");
    return -ENOMEM;
  }

  *out << "  props:\n";
  for (int i = 0;(size_t)i < props->count_props; ++i) {
    prop_info[i] = drmModeGetProperty(fd(), props->props[i]);

    DumpProp(prop_info[i],props->props[i],props->prop_values[i],out);

    drmModeFreeProperty(prop_info[i]);
  }

  drmModeFreeObjectProperties(props);
  free(prop_info);
  return 0;
}

int DrmDevice::DumpPlaneProperty(const DrmPlane &plane, std::ostringstream *out) {
  return DumpProperty(plane.id(), DRM_MODE_OBJECT_PLANE, out);
}

int DrmDevice::DumpCrtcProperty(const DrmCrtc &crtc, std::ostringstream *out) {
  return DumpProperty(crtc.id(), DRM_MODE_OBJECT_CRTC, out);
}

int DrmDevice::DumpConnectorProperty(const DrmConnector &connector, std::ostringstream *out) {
   return DumpProperty(connector.id(), DRM_MODE_OBJECT_CONNECTOR, out);
}

bool DrmDevice::GetHdrPanelMetadata(DrmConnector *conn, struct drm_hdr_static_metadata_infoframe* blob_data) {
  drmModePropertyBlobPtr blob;
  drmModeObjectPropertiesPtr props;
  props = drmModeObjectGetProperties(fd(), conn->id(), DRM_MODE_OBJECT_CONNECTOR);
  if (!props) {
    ALOGE("Failed to get properties for %d/%x", conn->id(), DRM_MODE_OBJECT_CONNECTOR);
    return false;
  }

  bool found = false;
  int value;
  for (int i = 0; !found && (size_t)i < props->count_props; ++i) {
    drmModePropertyPtr p = drmModeGetProperty(fd(), props->props[i]);
    if (p && !strcmp(p->name, "HDR_PANEL_METADATA")) {

      if (!drm_property_type_is(p, DRM_MODE_PROP_BLOB))
      {
          ALOGE("%s:line=%d,is not blob",__FUNCTION__,__LINE__);
          drmModeFreeProperty(p);
          drmModeFreeObjectProperties(props);
          return false;
      }

      if (!p->count_blobs)
        value = props->prop_values[i];
      else
        value = p->blob_ids[0];
      blob = drmModeGetPropertyBlob(fd(), value);
      if (!blob) {
        ALOGE("%s:line=%d, blob is null",__FUNCTION__,__LINE__);
        drmModeFreeProperty(p);
        drmModeFreeObjectProperties(props);
        return false;
      }

      memcpy(blob_data, blob->data, blob->length);
      // ALOGI("rk-debug blob_data=%zu blob->length=%" PRIu32 ,sizeof(struct drm_hdr_static_metadata_infoframe),
      //                                             blob->length);

      drmModeFreePropertyBlob(blob);

      found = true;
    }
    drmModeFreeProperty(p);
  }

  drmModeFreeObjectProperties(props);

  return found;
}

void DrmDevice::FlipResolutionSwitchHandler(int display_id) {
  event_listener_.FlipResolutionSwitchHandler(display_id);
}

bool DrmDevice::is_hdr_panel_support_st2084(DrmConnector *conn) const {
  return (conn->get_hdr_metadata_ptr()->eotf & (1 << SMPTE_ST2084)) > 0;
}

bool DrmDevice::is_hdr_panel_support_HLG(DrmConnector *conn) const {
  return (conn->get_hdr_metadata_ptr()->eotf & (1 << HLG)) > 0;
}


bool DrmDevice::is_plane_support_hdr2sdr(DrmCrtc *crtc) const
{
    bool bHdr2sdr = false;
    for (std::vector<PlaneGroup *> ::const_iterator iter = plane_groups_.begin();
           iter != plane_groups_.end(); ++iter)
    {
           for(std::vector<DrmPlane*> ::const_iterator iter_plane = (*iter)->planes.begin();
           iter_plane != (*iter)->planes.end(); ++iter_plane)
        {
            if((*iter_plane)->GetCrtcSupported(*crtc) && (*iter_plane)->get_hdr2sdr())
            {
                bHdr2sdr = true;
                break;
            }
        }
    }

    return bHdr2sdr;
}

int DrmDevice::UpdateConnectorBaseInfo(unsigned int connector_type,
               unsigned int connector_id, struct disp_info *info){
  return baseparameter_.UpdateConnectorBaseInfo(connector_type,connector_id,info);
}

int DrmDevice::DumpConnectorBaseInfo(unsigned int connector_type,
               unsigned int connector_id, struct disp_info *info){
  return baseparameter_.DumpConnectorBaseInfo(connector_type,connector_id,info);
}

int DrmDevice::SetScreenInfo(unsigned int connector_type,
               unsigned int connector_id, int index, struct screen_info *info){
  return baseparameter_.SetScreenInfo(connector_type,connector_id,index,info);
}

}  // namespace android
