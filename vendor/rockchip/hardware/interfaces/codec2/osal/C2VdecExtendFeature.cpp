#include <log/log.h>
#include <errno.h>
#include <inttypes.h>
#include <cutils/properties.h>
#include "C2VdecExtendFeature.h"

namespace android {

#define PERFORM_SET_OFFSET_OF_DYNAMIC_HDR_METADATA           0x08100017
#define PERFORM_GET_OFFSET_OF_DYNAMIC_HDR_METADATA           0x08100018
#define PERFORM_LOCK_RKVDEC_SCALING_METADATA                 0x08100019
#define PERFORM_UNLOCK_RKVDEC_SCALING_METADATA               0x0810001A
#define PERFORM_GET_BUFFER_ID                                0x0810001B
#define PERFORM_GET_USAGE                                    0x0feeff03

#ifndef GRALLOC_MODULE_PERFORM_LOCK_RKVDEC_SCALING_METADATA
typedef struct metadata_for_rkvdec_scaling_t {
    uint64_t version;
    // mask
    uint64_t requestMask;
    uint64_t replyMask;

    // buffer info
    uint32_t width;   // pixel_w
    uint32_t height;  // pixel_h
    uint32_t format;  // drm_fourcc
    uint64_t modifier;// modifier
    uint32_t usage;   // usage
    uint32_t pixel_stride; // pixel_stride

    // image info
    uint32_t srcLeft;
    uint32_t srcTop;
    uint32_t srcRight;
    uint32_t srcBottom;

    // buffer layout
    uint32_t layer_cnt;
    uint32_t fd[4];
    uint32_t offset[4];
    uint32_t byteStride[4];
} metadata_for_rkvdec_scaling_t;
#endif

class GrallocModule {
public:
    static GrallocModule* getInstance() {
        static GrallocModule _gInstance;
        return &_gInstance;
    }

    int setDynamicHdrMeta(buffer_handle_t hnd, int64_t offset);
    int getDynamicHdrMeta(buffer_handle_t hnd, int64_t *offset);
    int mapScaleMeta(buffer_handle_t hnd, metadata_for_rkvdec_scaling_t** metadata);
    int unmapScaleMeta(buffer_handle_t hnd);
    int getHndBufId(buffer_handle_t hnd, uint64_t *buffer_id);
    int getUsage(buffer_handle_t hnd, uint64_t *usage);

private:
    GrallocModule();
    virtual ~GrallocModule();
    const gralloc_module_t *mGralloc;
};

GrallocModule::GrallocModule() {
    mGralloc = NULL;
    int ret = hw_get_module(GRALLOC_HARDWARE_MODULE_ID, (const hw_module_t **)&mGralloc);
    if (ret)
        ALOGE("hw_get_module fail");
}

GrallocModule::~GrallocModule() {
}

int GrallocModule::setDynamicHdrMeta(buffer_handle_t hnd, int64_t offset) {
    int ret = 0;
    int op = PERFORM_SET_OFFSET_OF_DYNAMIC_HDR_METADATA;


    if (mGralloc && mGralloc->perform) {
        ret = mGralloc->perform(mGralloc, op, hnd, offset);
    } else {
        ret = -EINVAL;
    }

    if (ret != 0) {
        ALOGE("%s:cann't set dynamic hdr metadata from gralloc", __FUNCTION__);
        goto exit;
    }

exit:
    return ret;
}

int GrallocModule::getDynamicHdrMeta(buffer_handle_t hnd, int64_t *offset) {
    int ret = 0;
    int op = PERFORM_GET_OFFSET_OF_DYNAMIC_HDR_METADATA;

    if (mGralloc && mGralloc->perform) {
        ret = mGralloc->perform(mGralloc, op, hnd, offset);
    } else {
        ret = -EINVAL;
    }

    if(ret != 0) {
        ALOGE("%s:cann't get dynamic hdr metadata from gralloc", __FUNCTION__);
        goto exit;
    }

exit:
    return ret;
}

int GrallocModule::mapScaleMeta(buffer_handle_t hnd, metadata_for_rkvdec_scaling_t** metadata) {
    int ret = 0;
    int op = PERFORM_LOCK_RKVDEC_SCALING_METADATA;

    if (mGralloc && mGralloc->perform) {
        ret = mGralloc->perform(mGralloc, op, hnd, metadata);
    } else {
        ret = -EINVAL;
    }

    if (ret != 0) {
        ALOGE("%s:cann't lock rkdevc_scaling_metadata from gralloc", __FUNCTION__);
        goto exit;
    }

exit:
    return ret;
}

int GrallocModule::unmapScaleMeta(buffer_handle_t hnd) {
    int ret = 0;
    int op = PERFORM_UNLOCK_RKVDEC_SCALING_METADATA;

    if (mGralloc && mGralloc->perform) {
        ret = mGralloc->perform(mGralloc, op, hnd);
    } else {
        ret = -EINVAL;
    }

    if (ret != 0) {
        ALOGE("%s:cann't unlock rkdevc_scaling_metadata from gralloc", __FUNCTION__);
        goto exit;
    }

exit:
    return ret;
}

int GrallocModule::getHndBufId(buffer_handle_t hnd, uint64_t *buffer_id) {
    int ret = 0;
    int op = PERFORM_GET_BUFFER_ID;

    if (mGralloc && mGralloc->perform) {
        ret = mGralloc->perform(mGralloc, op, hnd, buffer_id);
    } else {
        ret = -EINVAL;
    }

    if (ret != 0) {
        ALOGE("%s:cann't get buf id from gralloc", __FUNCTION__);
        goto exit;
    }

exit:
    return ret;
}

int GrallocModule::getUsage(buffer_handle_t hnd, uint64_t *usage) {
    int ret = 0;
    int op = PERFORM_GET_USAGE;

    if (mGralloc && mGralloc->perform) {
        ret = mGralloc->perform(mGralloc, op, hnd, usage);
    } else {
        ret = -EINVAL;
    }

    if(ret != 0) {
        ALOGE("%s:cann't get usage from gralloc", __FUNCTION__);
        goto exit;
    }

exit:
    return ret;
}

int C2VdecExtendFeature::configFrameHdrDynamicMeta(buffer_handle_t hnd, int64_t offset)
{
    int ret = 0;
    int64_t dynamicHdrOffset = offset;

    ret = GrallocModule::getInstance()->setDynamicHdrMeta(hnd, dynamicHdrOffset);
    if (ret)
        return ret;

    return ret;
}

int C2VdecExtendFeature::checkNeedScale(buffer_handle_t hnd) {
    int ret = 0;
    int need = 0;
    uint64_t bufId = 0;
    uint64_t usage = 0;

    metadata_for_rkvdec_scaling_t* metadata = NULL;
    GrallocModule::getInstance()->getHndBufId(hnd, &bufId);
    GrallocModule::getInstance()->getUsage(hnd, &usage);
    ret = GrallocModule::getInstance()->mapScaleMeta(hnd, &metadata);
    if (!ret) {
        /*
         * NOTE: After info change realloc buf, buf has not processed by hwc,
         * metadata->requestMask is default value 0. So we define:
         * requestMask = 1 : need scale
         * requestMask = 2 : no need scale
         * other : keep same as before
         */
        switch (metadata->requestMask) {
        case 1:
            need = 1;
            ALOGD("bufId:0x%" PRIx64" hwc need scale", bufId);
            break;
        case 2:
            need = 0;
            ALOGD("bufId:0x%" PRIx64" hwc no need scale", bufId);
            break;
        default:
            need = -1;
            break;
        }
        GrallocModule::getInstance()->unmapScaleMeta(hnd);
    }

    return need;
}

int C2VdecExtendFeature::configFrameScaleMeta(
        buffer_handle_t hnd, C2PreScaleParam *scaleParam) {
    int ret = 0;
    metadata_for_rkvdec_scaling_t* metadata = NULL;

    ret = GrallocModule::getInstance()->mapScaleMeta(hnd, &metadata);
    if (!ret) {
        int32_t thumbWidth     = scaleParam->thumbWidth;
        int32_t thumbHeight    = scaleParam->thumbHeight;
        int32_t thumbHorStride = scaleParam->thumbHorStride;
        uint64_t usage         = 0;

        metadata->replyMask     = 1;
        /*
         * NOTE: keep same with gralloc
         * width = stride, crop real size
         */
        metadata->width         = thumbHorStride;
        metadata->height        = thumbHeight;
        metadata->pixel_stride  = thumbHorStride;
        metadata->format        = scaleParam->format;

        // NV12 8/10 bit nfbc, modifier = 0
        metadata->modifier      = 0;

        metadata->srcLeft       = 0;
        metadata->srcTop        = 0;
        metadata->srcRight      = thumbWidth;
        metadata->srcBottom     = thumbHeight;
        metadata->offset[0]     = scaleParam->yOffset;
        metadata->offset[1]     = scaleParam->uvOffset;
        metadata->byteStride[0] = thumbHorStride;
        metadata->byteStride[1] = thumbHorStride;

        GrallocModule::getInstance()->getUsage(hnd, &usage);
        metadata->usage         = (uint32_t)usage;
    }

    ret = GrallocModule::getInstance()->unmapScaleMeta(hnd);

    return ret;
}

}
