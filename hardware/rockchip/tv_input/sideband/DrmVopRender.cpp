#include <fcntl.h>
#include <errno.h>
#include "DrmVopRender.h"
#include "log/log.h"
#include <unistd.h>

#include <sys/mman.h>
#include <cutils/properties.h>

#include "common/TvInput_Buffer_Manager.h"
#include "common/Utils.h"

#define HAS_ATOMIC 1

#define PROPERTY_TYPE "vendor"

#ifdef LOG_TAG
#undef LOG_TAG
#define LOG_TAG "tv_input_VopRender"
#endif

namespace android {

#define ALIGN_DOWN( value, base)     (value & (~(base-1)) )
#define ALIGN(x, a) (((x) + (a)-1) & ~((a)-1))

DrmVopRender::DrmVopRender()
    : mDrmFd(0),
    mSidebandPlaneId(0)
{
    memset(&mOutputs, 0, sizeof(mOutputs));
    ALOGE("DrmVopRender");
}

DrmVopRender::~DrmVopRender()
{
   // WARN_IF_NOT_DEINIT();
    ALOGE("DrmVopRender delete ");
}

DrmVopRender* DrmVopRender::GetInstance() {
    static DrmVopRender instance;
    return &instance;
}

bool DrmVopRender::initialize()
{
    Mutex::Autolock autoLock(mVopPlaneLock);
    ALOGE("initialize in");
    /*if (mInitialized) {
        ALOGE(">>Drm object has been initialized");
        return true;
    }*/

    const char *path = "/dev/dri/card0";

    mDrmFd = open(path, O_RDWR);
    if (mDrmFd < 0) {
        ALOGD("failed to open Drm, error: %s", strerror(errno));
        return false;
    }
    ALOGE("mDrmFd = %d", mDrmFd);

    for (const auto &fbidMap : mFbidMap) {
        int fbid = fbidMap.second;
        ALOGE("%s find last fbid=%d", __FUNCTION__, fbid);
        if (drmModeRmFB(mDrmFd, fbid))
            ALOGE("Failed to rm fb %d", fbid);
    }
    mFbidMap.clear();

    memset(&mOutputs, 0, sizeof(mOutputs));
    mInitialized = true;
    int ret = hw_get_module(GRALLOC_HARDWARE_MODULE_ID,
                      (const hw_module_t **)&gralloc_);

    if (ret) {
        ALOGE("Failed to open gralloc module");
        return ret;
    } else {
        ALOGD("open gralloc module successful !");
    }
    return true;
}

void DrmVopRender::deinitialize()
{
    ALOGE("deinitialize in");
    if(!mInitialized) return;
    Mutex::Autolock autoLock(mVopPlaneLock);
    for (int i = 0; i < OUTPUT_MAX; i++) {
        resetOutput(i);
    }

    if (mDrmFd) {
        close(mDrmFd);
        mDrmFd = 0;
    }

    for (const auto &fbidMap : mFbidMap) {
        int fbid = fbidMap.second;
        if (drmModeRmFB(mDrmFd, fbid))
            ALOGE("Failed to rm fb");
    }

    mInitialized = false;
}

void DrmVopRender::DestoryFB() {
    Mutex::Autolock autoLock(mVopPlaneLock);
    for (const auto &fbidMap : mFbidMap) {
        int fbid = fbidMap.second;
        ALOGV("%s fbid=%d", __FUNCTION__, fbid);
        if (drmModeRmFB(mDrmFd, fbid))
            ALOGE("Failed to rm fb");
    }
    mFbidMap.clear();
}

bool DrmVopRender::detect() {
    Mutex::Autolock autoLock(mVopPlaneLock);
    detect(HWC_DISPLAY_PRIMARY);
    mEnableSkipFrame = false;
    return true;
}

bool DrmVopRender::detect(int device)
{
    if (!mInitialized) {
        return false;
    }
    ALOGE("detect device=%d", device);
    int outputIndex = getOutputIndex(device);
    if (outputIndex < 0 ) {
        return false;
    }

    char prop_name[PROPERTY_VALUE_MAX] = {0};
    char prop_value[PROPERTY_VALUE_MAX] = {0};
    for (int i=0; i< MAX_DISPLAY_NUM; i++) {
        sprintf(prop_name, "vendor.hwc.device.display-%d", i);
        property_get(prop_name, prop_value, "0:0:0");
        ALOGE("%s=%s", prop_name, prop_value);
        /*if (strcmp(prop_value, "0") == 0) {
            break;
        }*/
        bool connected = strstr(prop_value, ":connected");
        if (mDisplayInfos.empty() || i > mDisplayInfos.size()-1) {
            DisplayInfo info;
            memset(&info, 0, sizeof(info));
            info.display_id = i;
            info.connected = connected;
            if (mEnableOverScan) {
                char *saveptr;
                int num_tokens = 0;
                char* token = strtok_r(prop_value, ":", &saveptr);
                while (token != NULL){
                    if (num_tokens == 0) {
                        strcpy(info.connector_name, token);
                    } else if (num_tokens == 1) {
                        info.crtc_id = (int)atoi(token);
                    }
                    num_tokens++;
                    token = strtok_r(NULL, ":", &saveptr);
                }
                saveptr = NULL;
            }
            ALOGE("=====push display info %d %d %s=====", i, info.crtc_id, info.connector_name);
            mDisplayInfos.push_back(info);
        } else {
            mDisplayInfos[i].connected = connected;
            if (mEnableOverScan && mDisplayInfos[i].crtc_id == 0 && strcmp(prop_value, "0:0:0") != 0) {
                char *saveptr;
                int num_tokens = 0;
                char* token = strtok_r(prop_value, ":", &saveptr);
                while (token != NULL) {
                    if (num_tokens == 0) {
                        strcpy(mDisplayInfos[i].connector_name, token);
                    } else if (num_tokens == 1) {
                        mDisplayInfos[i].crtc_id = (int)atoi(token);
                    }
                    num_tokens++;
                    token = strtok_r(NULL, ":", &saveptr);
                }
                saveptr = NULL;
                ALOGE("=====update display info %d %d %s=====", i, mDisplayInfos[i].crtc_id, mDisplayInfos[i].connector_name);
            }
        }
    }

    resetOutput(outputIndex);
    drmModeConnectorPtr connector = NULL;
    DrmOutput *output = &mOutputs[outputIndex];
    bool ret = false;
    // get drm resources
    drmModeResPtr resources = drmModeGetResources(mDrmFd);
    if (!resources) {
        ALOGE("fail to get drm resources, error: %s", strerror(errno));
        return false;
    }

    ret = drmSetClientCap(mDrmFd, DRM_CLIENT_CAP_UNIVERSAL_PLANES, 1);
    if (ret) {
        ALOGE("Failed to set atomic cap %s", strerror(errno));
        return ret;
    }
    ret = drmSetClientCap(mDrmFd, DRM_CLIENT_CAP_ATOMIC, 1);
    if (ret) {
        ALOGE("Failed to set atomic cap %s", strerror(errno));
        return ret;
    }

    output->res = resources;
    ALOGD("resources->count_connectors=%d",resources->count_connectors);
    // find connector for the given device
    for (int i = 0; i < resources->count_connectors; i++) {
        if (!resources->connectors || !resources->connectors[i]) {
            ALOGE("fail to get drm resources connectors, error: %s", strerror(errno));
            continue;
        }
        nsecs_t startTime = systemTime();
        connector = drmModeGetConnector(mDrmFd, resources->connectors[i]);
        long usedTime = (long)((systemTime() - startTime)/1000000);
        if (usedTime > 2000) {
            ALOGD("%s ===========timeout===========%ld", __FUNCTION__, usedTime);
        }
        if (!connector) {
            ALOGE("drmModeGetConnector failed");
            continue;
        }

        if (connector->connection != DRM_MODE_CONNECTED) {
            ALOGE("+++device %d is not connected", device);
            if (i == resources->count_connectors -1) {
                drmModeFreeConnector(connector);
                ret = true;
                break;
            }
            continue;
        }

        DrmModeInfo drmModeInfo;
        memset(&drmModeInfo, 0, sizeof(drmModeInfo));
        drmModeInfo.connector = connector;
        output->connected = true;
        ALOGD("connector %d connected",outputIndex);
        // get proper encoder for the given connector
        if (connector->encoder_id) {
            ALOGD("Drm connector has encoder attached on device %d", device);
            drmModeInfo.encoder = drmModeGetEncoder(mDrmFd, connector->encoder_id);
            if (!drmModeInfo.encoder) {
                ALOGD("failed to get encoder from a known encoder id");
                // fall through to get an encoder
            }
        }

        if (!drmModeInfo.encoder) {
            ALOGD("getting encoder for device %d", device);
            drmModeEncoderPtr encoder;
            for (int j = 0; j < resources->count_encoders; j++) {
                if (!resources->encoders || !resources->encoders[j]) {
                    ALOGE("fail to get drm resources encoders, error: %s", strerror(errno));
                    continue;
                }

                encoder = drmModeGetEncoder(mDrmFd, resources->encoders[i]);
                if (!encoder) {
                    ALOGE("drmModeGetEncoder failed");
                    continue;
                }
                ALOGD("++++encoder_type=%d,device=%d",encoder->encoder_type,getDrmEncoder(device));
                if (encoder->encoder_type == getDrmEncoder(device)) {
                    drmModeInfo.encoder = encoder;
                    break;
                }
                drmModeFreeEncoder(encoder);
                encoder = NULL;
            }
        }
        if (!drmModeInfo.encoder) {
            ALOGE("failed to get drm encoder");
            break;
        }

        // get an attached crtc or spare crtc
        if (drmModeInfo.encoder->crtc_id) {
            ALOGD("Drm encoder has crtc attached on device %d", device);
            drmModeInfo.crtc = drmModeGetCrtc(mDrmFd, drmModeInfo.encoder->crtc_id);
            if (!drmModeInfo.crtc) {
                ALOGE("failed to get crtc from a known crtc id");
                // fall through to get a spare crtc
            }
        }
        if (!drmModeInfo.crtc) {
            ALOGE("getting crtc for device %d %d", device, i);
            drmModeCrtcPtr crtc;
            for (int j = 0; j < resources->count_crtcs; j++) {
                if (!resources->crtcs || !resources->crtcs[j]) {
                    ALOGE("fail to get drm resources crtcs, error: %s", strerror(errno));
                    continue;
                }

                crtc = drmModeGetCrtc(mDrmFd, resources->crtcs[j]);
                if (!crtc) {
                    ALOGE("drmModeGetCrtc failed");
                    continue;
                }

                // check if legal crtc to the encoder
                if (drmModeInfo.encoder->possible_crtcs & (1<<j)) {
                    drmModeInfo.crtc = crtc;
                }
                drmModeObjectPropertiesPtr props;
                drmModePropertyPtr prop;
                props = drmModeObjectGetProperties(mDrmFd, crtc->crtc_id, DRM_MODE_OBJECT_CRTC);
                if (!props) {
                    ALOGD("Failed to found props crtc[%d] %s\n",
                        crtc->crtc_id, strerror(errno));
                    continue;
                }
                for (uint32_t i = 0; i < props->count_props; i++) {
                    prop = drmModeGetProperty(mDrmFd, props->props[i]);
                    if (!strcmp(prop->name, "ACTIVE")) {
                        ALOGD("Crtc id=%d is ACTIVE.", crtc->crtc_id);
                        if (props->prop_values[i]) {
                            drmModeInfo.crtc = crtc;
                            ALOGD("Crtc id=%d is active",crtc->crtc_id);
                            break;
                        }
                    }
                }
            }
        }
        if (!drmModeInfo.crtc) {
            ALOGE("failed to get drm crtc");
            break;
        } else {
            drmModeObjectPropertiesPtr props;
            drmModePropertyPtr prop;
            props = drmModeObjectGetProperties(mDrmFd, drmModeInfo.crtc->crtc_id, DRM_MODE_OBJECT_CRTC);
            if (props) {
                for (uint32_t i = 0; i < props->count_props; i++) {
                    prop = drmModeGetProperty(mDrmFd, props->props[i]);
                    if (!strcmp(prop->name, "PLANE_MASK")) {
                        uint64_t plane_mask_value = props->prop_values[i];
                        //ALOGD("PLANE_MASK=%d", (int)plane_mask_value);
                        for (i = 0; i < prop->count_enums; i++) {
                            uint64_t enums_value = 1LL << prop->enums[i].value;
                            if ((plane_mask_value & enums_value) == enums_value) {
                                strcpy(drmModeInfo.crtc_plane_mask+strlen(drmModeInfo.crtc_plane_mask), prop->enums[i].name);
                            }
                        }
                    }
                    drmModeFreeProperty(prop);
                }
                drmModeFreeObjectProperties(props);
            }
        }
        output->plane_res = drmModeGetPlaneResources(mDrmFd);
        ALOGD("drmModeGetPlaneResources successful. index=%d", i);
        output->mDrmModeInfos.push_back(drmModeInfo);
        //break;
    }

    if (output->mDrmModeInfos.empty()) {
        ALOGD("final mDrmModeInfos is empty");
        for (int i = 0; i < mDisplayInfos.size(); i++) {
            ALOGE("empty drmmodeinfo force set connected false");
            mDisplayInfos[i].connected = false;
        }
    } else {
        int last_crtc_id = -1;
        int total_crtc_id_num = 0;
        for (int i=0; i<output->mDrmModeInfos.size(); i++) {
            if (output->mDrmModeInfos[i].crtc) {
                int crtc_id = output->mDrmModeInfos[i].crtc->crtc_id;
                total_crtc_id_num++;
                ALOGD("final  crtc->crtc_id %d %s", crtc_id, output->mDrmModeInfos[i].crtc_plane_mask);
                nsecs_t startTime = systemTime();
               output->mDrmModeInfos[i].props = drmModeObjectGetProperties(mDrmFd, output->mDrmModeInfos[i].crtc->crtc_id, DRM_MODE_OBJECT_CRTC);
                long usedTime = (long)((systemTime() - startTime)/1000000);
                if (usedTime > 2000) {
                    ALOGD("%s ===========timeout===========%ld", __FUNCTION__, usedTime);
                }
               if (!output->mDrmModeInfos[i].props) {
                   ALOGE("Failed to found props crtc[%d] %s\n", output->mDrmModeInfos[i].crtc->crtc_id, strerror(errno));
               }
                if (last_crtc_id == crtc_id) {
                    ALOGE("same crtc_id need reconnect");
                    for (int j =0; j < mDisplayInfos.size(); j++) {
                        mDisplayInfos[j].connected = false;
                    }
                } else {
                    last_crtc_id = crtc_id;
                    for (int j = 0; j < mDisplayInfos.size(); j++) {
                        if (crtc_id == mDisplayInfos[j].crtc_id) {
                            strcpy(output->mDrmModeInfos[i].connector_name, mDisplayInfos[j].connector_name);
                            ALOGE("index=%d, %d %s", i, crtc_id, output->mDrmModeInfos[i].connector_name);
                        }
                    }
                }
            }
        }
        for (int j = 0; j < mDisplayInfos.size(); j++) {
            if (mDisplayInfos[j].connected) {
                if (total_crtc_id_num > 0) {
                    total_crtc_id_num--;
                } else {
                    ALOGE("find not crtcid display");
                    mDisplayInfos[j].connected = false;
                }
            }
        }
    }

    drmModeFreeResources(resources);

    return ret;
}

uint32_t DrmVopRender::getDrmEncoder(int device)
{
    if (device == HWC_DISPLAY_PRIMARY)
        return 2;
    else if (device == HWC_DISPLAY_EXTERNAL)
        return DRM_MODE_ENCODER_TMDS;
    return DRM_MODE_ENCODER_NONE;
}

uint32_t DrmVopRender::ConvertHalFormatToDrm(uint32_t hal_format) {
  switch (hal_format) {
    case HAL_PIXEL_FORMAT_BGR_888:
	return DRM_FORMAT_RGB888;
    case HAL_PIXEL_FORMAT_RGB_888:
      return DRM_FORMAT_BGR888;
    case HAL_PIXEL_FORMAT_BGRA_8888:
      return DRM_FORMAT_ARGB8888;
    case HAL_PIXEL_FORMAT_RGBX_8888:
      return DRM_FORMAT_XBGR8888;
    case HAL_PIXEL_FORMAT_RGBA_8888:
    case HAL_PIXEL_FORMAT_IMPLEMENTATION_DEFINED:
      return DRM_FORMAT_ABGR8888;
    //Fix color error in NenaMark2.
    case HAL_PIXEL_FORMAT_RGB_565:
      return DRM_FORMAT_RGB565;
    case HAL_PIXEL_FORMAT_YV12:
      return DRM_FORMAT_YVU420;
    case HAL_PIXEL_FORMAT_YCrCb_NV12:
      return DRM_FORMAT_NV12;
    case HAL_PIXEL_FORMAT_YCrCb_NV12_10:
      return DRM_FORMAT_NV15;
    case HAL_PIXEL_FORMAT_YCbCr_422_SP: 
      return DRM_FORMAT_NV16;
    case HAL_PIXEL_FORMAT_YCbCr_444_888:
      return DRM_FORMAT_NV24;
    default:
      ALOGE("Cannot convert hal format to drm format %u", hal_format);
      return -EINVAL;
  }
}

int DrmVopRender::FindSidebandPlane(int device) {
    Mutex::Autolock autoLock(mVopPlaneLock);
    drmModePlanePtr plane;
    drmModeObjectPropertiesPtr props;
    drmModePropertyPtr prop;
    int find_plan_id = 0;
    int outputIndex = getOutputIndex(device);
    if (outputIndex < 0 ) {
        ALOGE("invalid device");
        return false;
    }
    DrmOutput *output= &mOutputs[outputIndex];
    if (!output->connected) {
        ALOGE("device is not connected,outputIndex=%d",outputIndex);
        return false;
    }
    //ALOGD("output->plane_res->count_planes %d", output->plane_res->count_planes);
    if (output->plane_res == NULL) {
        ALOGE("%s output->plane_res is NULL", __FUNCTION__);
        return false;
    }
    int plandIdCount = 0;
    if (!output->mDrmModeInfos.empty()) {
        for (int i=0; i<output->mDrmModeInfos.size(); i++) {
            output->mDrmModeInfos[i].plane_id = -1;
            plandIdCount++;
        }
    }

    if (mDebugLevel == 3) {
        ALOGE("start to find ASYNC_COMMIT output->plane_res->count_planes=%d", output->plane_res->count_planes);
    }
    for(uint32_t i = 0; i < output->plane_res->count_planes; i++) {
        if (plandIdCount == 0) {
            break;
        }
        plane = drmModeGetPlane(mDrmFd, output->plane_res->planes[i]);
        props = drmModeObjectGetProperties(mDrmFd, plane->plane_id, DRM_MODE_OBJECT_PLANE);
        if (!props) {
            ALOGE("Failed to found props plane[%d] %s\n",plane->plane_id, strerror(errno));
           return -ENODEV;
        }
        int plane_id;
        for (uint32_t j = 0; j < props->count_props; j++) {
            prop = drmModeGetProperty(mDrmFd, props->props[j]);
            if (!strcmp(prop->name, "ASYNC_COMMIT")) {
                if (mDebugLevel == 3) {
                    ALOGE("find ASYNC_COMMIT plane id=%d value=%lld====%d-%d", plane->plane_id, (long long)props->prop_values[j], i, j);
                }
                if (props->prop_values[j] != 0) {
                    plane_id = plane->plane_id;
                }
            } else if (plane_id > 0 && !strcmp(prop->name, "NAME")) {
                if (prop->count_enums > 0) {
                    char* win_name = strstr(prop->enums[0].name, "-");
                    if (win_name && !output->mDrmModeInfos.empty()) {
                        char plane_name[10] = {""};
                        strncpy(plane_name, prop->enums[0].name, strlen(prop->enums[0].name)-strlen(win_name));
                        for (int k=0; k<output->mDrmModeInfos.size(); k++) {
                            ALOGV("crtc_plane_mask=%s  plane_name=%s", output->mDrmModeInfos[k].crtc_plane_mask, plane_name);
                            if (strstr(output->mDrmModeInfos[k].crtc_plane_mask, plane_name)) {
                                output->mDrmModeInfos[k].plane_id = plane_id;
                                ALOGV("set plan_id=%d crtc_id=%d to pos=%d", plane_id, output->mDrmModeInfos[k].crtc->crtc_id, k);
                                find_plan_id = plane_id;
                                plandIdCount--;
                                break;
                            }
                        }
                    }
                }
                if (prop) {
                    drmModeFreeProperty(prop);
                }
                break;
            }
            if (prop)
                drmModeFreeProperty(prop);
        }
        if(props)
            drmModeFreeObjectProperties(props);
        if(plane)
            drmModeFreePlane(plane);
    }
    return find_plan_id;
}

int DrmVopRender::getFbLength(buffer_handle_t handle) {
    if (!handle) {
        ALOGE("%s buffer_handle_t is NULL.", __FUNCTION__);
        return -1;
    } else {
        ALOGE("%s %p", __FUNCTION__, handle);
    }

    common::TvInputBufferManager* tvBufferMgr = common::TvInputBufferManager::GetInstance();
    return tvBufferMgr->GetHandleBufferSize(handle);
}

int DrmVopRender::getFbid(buffer_handle_t handle, int hdmiInType) {
    Mutex::Autolock autoLock(mVopPlaneLock);
    if (!handle) {
        ALOGE("%s buffer_handle_t is NULL.", __FUNCTION__);
        return -1;
    }

    common::TvInputBufferManager* tvBufferMgr = common::TvInputBufferManager::GetInstance();

    hwc_drm_bo_t bo;
    int fd = 0;
    int ret = 0;
    int src_w = 0;
    int src_h = 0;
    int src_format = 0;
    int src_stride = 0;

    fd = (int)tvBufferMgr->GetHandleFd(handle);
    std::map<int, int>::iterator it = mFbidMap.find(fd);
    int fbid = 0;
    if (it == mFbidMap.end()) {
        memset(&bo, 0, sizeof(hwc_drm_bo_t));
        uint32_t gem_handle;
        size_t plane_size;
        fd = (int)tvBufferMgr->GetHandleFd(handle);
        ret = drmPrimeFDToHandle(mDrmFd, fd, &gem_handle);
        src_w = tvBufferMgr->GetWidth(handle);
        src_h = tvBufferMgr->GetHeight(handle);
        src_format = tvBufferMgr->GetHalPixelFormat(handle);
        plane_size = tvBufferMgr->GetNumPlanes(handle);
        ALOGV("format=%d, plane_size = %zu", src_format, plane_size);
        if (hdmiInType == HDMIIN_TYPE_MIPICSI) {
            if (src_format == HAL_PIXEL_FORMAT_BGR_888) {
                src_stride = src_w * 3;
            } else if (src_format != HAL_PIXEL_FORMAT_YCrCb_NV12_10) {
                src_stride = src_w;
            } else {
                src_stride = (int)tvBufferMgr->GetPlaneStride(handle, 0);
            }
        } else {
            src_stride = (int)tvBufferMgr->GetPlaneStride(handle, 0);
        }

        //gralloc_->perform(gralloc_, GRALLOC_MODULE_PERFORM_GET_HADNLE_WIDTH, handle, &src_w);
        //gralloc_->perform(gralloc_, GRALLOC_MODULE_PERFORM_GET_HADNLE_HEIGHT, handle, &src_h);
        //gralloc_->perform(gralloc_, GRALLOC_MODULE_PERFORM_GET_HADNLE_FORMAT, handle, &src_format);
        //gralloc_->perform(gralloc_, GRALLOC_MODULE_PERFORM_GET_HADNLE_BYTE_STRIDE, handle, &src_stride);
        bo.width = src_w;
        bo.height = src_h;
        //bo.format = ConvertHalFormatToDrm(HAL_PIXEL_FORMAT_YCrCb_NV12);
        bo.format = ConvertHalFormatToDrm(src_format);
        if (src_format == HAL_PIXEL_FORMAT_YCrCb_NV12_10) {
            bo.pitches[0] = ALIGN(src_stride / 4 * 5, 64);
        } else {
            bo.pitches[0] = src_stride;
        }
        bo.gem_handles[0] = gem_handle;
        bo.offsets[0] = 0;
        if(src_format == HAL_PIXEL_FORMAT_YCrCb_NV12
            || src_format == HAL_PIXEL_FORMAT_YCrCb_NV12_10
            || src_format == HAL_PIXEL_FORMAT_YCbCr_422_SP)
        {
            bo.pitches[1] = bo.pitches[0];
            bo.gem_handles[1] = gem_handle;
            bo.offsets[1] = bo.pitches[1] * bo.height;
        } else if (src_format == HAL_PIXEL_FORMAT_YCbCr_444_888) {
            if (src_w == src_stride) {
                bo.pitches[1] = bo.pitches[0] * 2;
            } else {
                bo.pitches[1] = ALIGN(src_w * 2, 64);
            }
            bo.gem_handles[1] = gem_handle;
            bo.offsets[1] = bo.pitches[0] * bo.height;
        }
        //if (src_format == HAL_PIXEL_FORMAT_YCrCb_NV12_10) {
        //    bo.width = src_w / 1.25;
        //    bo.width = ALIGN_DOWN(bo.width, 2);
        //}
        ALOGD("width=%d,height=%d,format=%x,fd=%d,src_stride=%d, pitched=%d-%d",
            bo.width, bo.height, bo.format, fd, src_stride, bo.pitches[0], bo.pitches[1]);
        ret = drmModeAddFB2(mDrmFd, bo.width, bo.height, bo.format, bo.gem_handles,\
                     bo.pitches, bo.offsets, &bo.fb_id, 0);
        fbid = bo.fb_id;
        ALOGD("drmModeAddFB2 ret = %s fbid=%d", strerror(ret), fbid);
        mFbidMap.insert(std::make_pair(fd, fbid));
    } else {
        fbid = it->second;
    }
    if (fbid <= 0) {
        ALOGD("fbid is error.");
        return -1;
    }

    return fbid;
}

void DrmVopRender::resetOutput(int index)
{
    ALOGE("resetOutput index=%d", index);
    DrmOutput *output = &mOutputs[index];

    output->connected = false;
    memset(&output->mode, 0, sizeof(drmModeModeInfo));

    if (!output->mDrmModeInfos.empty()) {
        for (int i=0; i<output->mDrmModeInfos.size(); i++) {
            if (output->mDrmModeInfos[i].connector) {
                drmModeFreeConnector(output->mDrmModeInfos[i].connector);
                output->mDrmModeInfos[i].connector = 0;
            }
            if (output->mDrmModeInfos[i].encoder) {
                drmModeFreeEncoder(output->mDrmModeInfos[i].encoder);
                output->mDrmModeInfos[i].encoder = 0;
            }
            if (output->mDrmModeInfos[i].crtc) {
                drmModeFreeCrtc(output->mDrmModeInfos[i].crtc);
                output->mDrmModeInfos[i].crtc = 0;
            }
        }
        output->mDrmModeInfos.clear();
    }

    if (output->fbId) {
        drmModeRmFB(mDrmFd, output->fbId);
        output->fbId = 0;
    }
    if (output->fbHandle) {
        output->fbHandle = 0;
    }
}

bool DrmVopRender::needRedetect() {
    char prop_name[PROPERTY_VALUE_MAX] = {0};
    char prop_value[PROPERTY_VALUE_MAX] = {0};
    for (int i=0; i<mDisplayInfos.size(); i++) {
        sprintf(prop_name, "vendor.hwc.device.display-%d", i);
        property_get(prop_name, prop_value, "0");
        if (strstr(prop_value, ":connected")) {
            if(!mDisplayInfos[i].connected) {
                return true;
            }
        } else {
            if(mDisplayInfos[i].connected) {
                return true;
            }
        }
    }
    return false;
}

void DrmVopRender::setDebugLevel(int debugLevel) {
    if (mDebugLevel != debugLevel) {
        mDebugLevel = debugLevel;
    }
}

bool DrmVopRender::SetDrmPlane(int device, int32_t width, int32_t height,
        buffer_handle_t handle, int displayRatio, int hdmiInType) {
    if (mDebugLevel == 3) {
        ALOGE("%s come in, device=%d, handle=%p", __FUNCTION__, device, handle);
    }
    if (mEnableSkipFrame) {
        nsecs_t now = systemTime();
        if (now - mSkipFrameStartTime < SKIP_FRAME_TIME) {
            if (mDebugLevel == 3) {
                ALOGE("%s come in, skip frame", __FUNCTION__);
            }
            return false;
        }
        mEnableSkipFrame = false;
    }
    if (needRedetect() && mInitialized) {
        ALOGE("=================needRedetect===================");
        DestoryFB();
        ClearDrmPlaneContent(device, 0, 0);
        detect(HWC_DISPLAY_PRIMARY);
        mSkipFrameStartTime = systemTime();
        mEnableSkipFrame = true;
        return false;
    }

    int ret = 0;
    int find_plan_id = FindSidebandPlane(device);
    mSidebandPlaneId = find_plan_id;
    bool findAvailedPlane = find_plan_id > 0;
    int fb_id = findAvailedPlane?getFbid(handle, hdmiInType):-1;
    int flags = 0;
    int src_left = 0;
    int src_top = 0;
    int src_right = 0;
    int src_bottom = 0;
    int dst_left = 0;
    int dst_top = 0;
    int dst_right = 0;
    int dst_bottom = 0;
    int src_w = 0;
    int src_h = 0;
    int dst_w = 0;
    int dst_h = 0;
    char sideband_crop[PROPERTY_VALUE_MAX];
    memset(sideband_crop, 0, sizeof(sideband_crop));
    DrmOutput *output= &mOutputs[device];
    int length = 0;//property_get("vendor.hwc.sideband.crop", sideband_crop, NULL);
    if (length > 0) {
       sscanf(sideband_crop, "%d-%d-%d-%d-%d-%d-%d-%d",\
              &src_left, &src_top, &src_right, &src_bottom,\
              &dst_left, &dst_top, &dst_right, &dst_bottom);
       dst_w = dst_right - dst_left;
       dst_h = dst_bottom - dst_top;
    /*} else {
       dst_w = output->crtc->width;
       dst_h = output->crtc->height;*/
    }
    src_w = width;
    src_h = height;

    if (!mInitialized || !findAvailedPlane || fb_id < 0) {
        if (mDebugLevel == 3) {
            ALOGE("%s come in %d, %d, %d", __FUNCTION__, mInitialized, findAvailedPlane, fb_id);
        }
        return false;
    }

    //gralloc_->perform(gralloc_, GRALLOC_MODULE_PERFORM_GET_HADNLE_FORMAT, handle, &src_format);
    //ALOGV("dst_w %d dst_h %d src_w %d src_h %d in", dst_w, dst_h, src_w, src_h);
    //ALOGV("mDrmFd=%d plane_id=%d, output->crtc->crtc_id=%d fb_id=%d flags=%d", mDrmFd, plane_id, output->crtc->crtc_id, fb_id, flags);
    if (!output->mDrmModeInfos.empty()) {
        for (int i=0; i<output->mDrmModeInfos.size(); i++) {
            DrmModeInfo_t drmModeInfo = output->mDrmModeInfos[i];
            int plane_id = drmModeInfo.plane_id;
            if (plane_id > 0) {
                int overscan[] = {100, 100, 100, 100};
                if (mEnableOverScan) {
                    int connector_name_len = strlen(drmModeInfo.connector_name);
                    if (connector_name_len > 0) {
                        char overscan_prop[PROPERTY_VALUE_MAX] = {0};
                        char overscan_name[PROPERTY_VALUE_MAX] = {0};
                        std::string temp1(drmModeInfo.connector_name, connector_name_len - 1);
                        std::string temp2(1, drmModeInfo.connector_name[connector_name_len - 1]);
                        sprintf(overscan_name, "%s%s%d", TV_INPUT_OVERSCAN_PREF, temp1.c_str(), atoi(temp2.c_str()) - 1);
                        property_get(overscan_name, overscan_prop, "0");
                        if (strcmp(overscan_prop, "0") != 0) {
                            const char split[] = ",";
                            char* res = strtok(overscan_prop + 8, split);
                            int overscan_index = 0;
                            while (res != NULL) {
                                overscan[overscan_index++] = (int)atoi(res);
                                res = strtok(NULL, split);
                            }
                        }
                    }
                }
                dst_w = drmModeInfo.crtc->width;
                dst_h = drmModeInfo.crtc->height;
                int ratio_w = dst_w;
                int ratio_h = dst_h;
                if (displayRatio == SCREEN_16_9) {
                    ratio_h = dst_w * 9 /16;
                } else if (displayRatio == SCREEN_4_3) {
                    ratio_h = dst_w * 3 /4;
                }
                if (dst_h < ratio_h) {
                    ratio_h = dst_h;
                    if (displayRatio == SCREEN_16_9) {
                        ratio_w = dst_h * 16 /9;
                    } else if (displayRatio == SCREEN_4_3) {
                        ratio_w = dst_h * 4 /3;
                    }
                }
                if (dst_w < ratio_w) {
                    ratio_w = dst_w;
                }
                int offset_l = 0;
                int offset_t = 0;
                int offset_r = 0;
                int offset_b = 0;
                if (mEnableOverScan) {
                    offset_l = dst_w * (100 - overscan[0]) / 200;
                    offset_t = dst_h * (100 - overscan[1]) / 200;
                    offset_r = dst_w * (100 - overscan[2]) / 200;
                    offset_b = dst_h * (100 - overscan[3]) / 200;
                }
                int32_t crtc_x = dst_left + (dst_w - ratio_w) / 2 + offset_l;
                int32_t crtc_y = dst_top + (dst_h - ratio_h) / 2 + offset_t;
                uint32_t crtc_w = ALIGN_DOWN(ratio_w - offset_l - offset_r, 2);
                uint32_t crtc_h = ALIGN_DOWN(ratio_h - offset_t - offset_b, 2);
                ret = drmModeSetPlane(mDrmFd, plane_id,
                    drmModeInfo.crtc->crtc_id, fb_id, flags,
                    crtc_x, crtc_y, crtc_w, crtc_h,
                    0, 0,
                    src_w << 16, src_h << 16);
                if (mDebugLevel == 3) {
                    ALOGE("drmModeSetPlane ret=%s mDrmFd=%d plane_id=%d, crtc_id=%d, fb_id=%d, flags=%d, %d %d, %d %d %d %d",
                        strerror(ret), mDrmFd, plane_id, drmModeInfo.crtc->crtc_id, fb_id, flags, dst_w, dst_h, crtc_x, crtc_y, crtc_w, crtc_h);
                }
            }
        }
    }
    ALOGV("%s end.", __FUNCTION__);
    return true;
}

bool DrmVopRender::ClearDrmPlaneContent(int device, int32_t width, int32_t height)
{
    Mutex::Autolock autoLock(mVopPlaneLock);
    ALOGD("%s come in, device=%d", __FUNCTION__, device);
    bool ret = true;
    int plane_id = 0;//FindSidebandPlane(device);
    // drmModeAtomicReqPtr reqPtr = drmModeAtomicAlloc();
    DrmOutput *output= &mOutputs[device];
    drmModePlanePtr plane;
    drmModeObjectPropertiesPtr props;
    drmModePropertyPtr prop;
    //props = drmModeObjectGetProperties(mDrmFd, output->crtc->crtc_id, DRM_MODE_OBJECT_CRTC);

    if (output->plane_res == NULL) {
        ALOGE("%s output->plane_res is NULL", __FUNCTION__);
        prop = NULL;
        return -1;
    }

    for(uint32_t i = 0; i < output->plane_res->count_planes; i++) {
        plane = drmModeGetPlane(mDrmFd, output->plane_res->planes[i]);
        props = drmModeObjectGetProperties(mDrmFd, plane->plane_id, DRM_MODE_OBJECT_PLANE);
        if (!props) {
            ALOGE("Failed to found props plane[%d] %s\n",plane->plane_id, strerror(errno));
           return -ENODEV;
        }
        for (uint32_t j = 0; j < props->count_props; j++) {
            prop = drmModeGetProperty(mDrmFd, props->props[j]);
            if (!strcmp(prop->name, "ASYNC_COMMIT")) {
                if (props->prop_values[j] != 0) {
                    plane_id = plane->plane_id;
                    // ret = drmModeAtomicAddProperty(reqPtr, plane_id, prop->prop_id, 0) < 0;
                    ret =  drmModeObjectSetProperty(mDrmFd, plane_id, 0, prop->prop_id, 0) < 0;
                    if (ret) {
                        ALOGE("drmModeObjectSetProperty failed");
                        drmModeFreeProperty(prop);
                        // drmModeAtomicFree(reqPtr);
                        return false;
                    } else {
                        ALOGD("drmModeObjectSetProperty successful.");
                    }
                    break;
                }
            }
            if (prop)
                drmModeFreeProperty(prop);
        }
        if(props)
            drmModeFreeObjectProperties(props);
        if(plane)
            drmModeFreePlane(plane);
    }
    prop = NULL;


/*
    for (uint32_t i = 0; i < props->count_props; i++) {
        prop = drmModeGetProperty(mDrmFd, props->props[i]);
        ALOGD("%s prop->name=%s", __FUNCTION__, prop->name);
        if (!strcmp(prop->name, "CRTC_ID")) {
            // ret = drmModeAtomicAddProperty(reqPtr, plane_id, prop->prop_id, 0) < 0;
            ret =  drmModeObjectSetProperty(mDrmFd, plane_id, 0, prop->prop_id, 0) < 0;
            if (ret) {
                ALOGE("drmModeAtomicAddProperty failed");
                drmModeFreeProperty(prop);
                // drmModeAtomicFree(reqPtr);
                return false;
            }
        }
        if (!strcmp(prop->name, "FB_ID")) {
            // ret = drmModeAtomicAddProperty(reqPtr, plane_id, prop->prop_id, 0) < 0;
            ret =  drmModeObjectSetProperty(mDrmFd, plane_id, 0, prop->prop_id, 0) < 0;
            if (ret) {
                ALOGE("drmModeAtomicAddProperty failed");
                drmModeFreeProperty(prop);
                // drmModeAtomicFree(reqPtr);
                return false;
            }
        }
    }
    drmModeFreeProperty(prop);
    drmModeAtomicFree(reqPtr);
    prop = NULL;
*/
    /*int ret = 0;
    int plane_id = FindSidebandPlane(device);
    int flags = 0;
    int src_left = 0;
    int src_top = 0;
    int src_right = 0;
    int src_bottom = 0;
    int dst_left = 0;
    int dst_top = 0;
    int dst_right = 0;
    int dst_bottom = 0;
    int src_w = 0;
    int src_h = 0;
    int dst_w = 0;
    int dst_h = 0;
    char sideband_crop[PROPERTY_VALUE_MAX];
    memset(sideband_crop, 0, sizeof(sideband_crop));
    DrmOutput *output= &mOutputs[device];
    int length = property_get("vendor.hwc.sideband.crop", sideband_crop, NULL);
    if (length > 0) {
       sscanf(sideband_crop, "%d-%d-%d-%d-%d-%d-%d-%d",\
              &src_left, &src_top, &src_right, &src_bottom,\
              &dst_left, &dst_top, &dst_right, &dst_bottom);
       dst_w = dst_right - dst_left;
       dst_h = dst_bottom - dst_top;
    } else {
       dst_w = output->crtc->width;
       dst_h = output->crtc->height;
    }
    src_w = width;
    src_h = height;
    if (plane_id > 0) {
        ret = drmModeSetPlane(mDrmFd, plane_id,
                          0, 0, flags,
                          dst_left, dst_top,
                          dst_w, dst_h,
                          0, 0,
                          src_w << 16, src_h << 16);
        ALOGV("drmModeSetPlane ret=%s", strerror(ret));

    }*/
    return ret;
}

int DrmVopRender::getOutputIndex(int device)
{
    switch (device) {
    case HWC_DISPLAY_PRIMARY:
        return OUTPUT_PRIMARY;
    case HWC_DISPLAY_EXTERNAL:
        return OUTPUT_EXTERNAL;
    default:
        ALOGD("invalid display device");
        break;
    }

    return -1;
}

int DrmVopRender::getSidebandPlaneId() {
    return mSidebandPlaneId;
}
} // namespace android

