/****************************************************************************
 *
 *    Copyright (c) 2023 by Rockchip Corp.  All rights reserved.
 *
 *    The material in this file is confidential and contains trade secrets
 *    of Rockchip Corporation. This is proprietary information owned by
 *    Rockchip Corporation. No part of this work may be disclosed,
 *    reproduced, copied, transmitted, or used in any way for any purpose,
 *    without the express written permission of Rockchip Corporation.
 *
 *****************************************************************************/
#pragma once

#include "MemcAutoFd.h"
#include <stdint.h>
#include <utils/Trace.h>
#include <cutils/properties.h>
#include <time.h>

namespace android {

#define MEMC_MAGIC 0x83991906
#define MEMC_VERSION "Memc-1.4.3"
// 用于使能MEMC输出
#define MEMC_MODE_NAME               "persist.sys.memc.mode"
// 用于应用动态关闭MEMC输出
#define MEMC_RUNTIME_DISABLE_NAME    "sys.svep.runtime_disable"
// 用于开启对比模式
#define MEMC_CONTRAST_MODE_NAME      "persist.sys.memc.contrast_mode"
// 用于配置osd单行模式
#define MEMC_OSD_VIDEO_ONELINE_MODE  "persist.sys.memc.enable_oneline_osd"
// 用于配置正常字幕模式到单行模式的等待时间，单位秒
#define MEMC_OSD_VIDEO_ONELINE_WATI_SEC "persist.sys.svep.oneline_osd_wait_second"
// 关闭 MEMC osd
#define MEMC_OSD_DISABLE_MODE "persist.sys.svep.disable_memc_osd"
// 用于输出单帧耗时
#define MEMC_AVG_COST_TIME_NAME      "vendor.svep.avg_cost_time"
// 版本号
#define MEMC_VERSION_NAME "vendor.memc.version"
// 调试日志接口
#define MEMC_DEBUG_NAME "vendor.memc.log"

// Vendor Storage ID.
#define MEMC_VENDOR_AUTHOR_ID "ro.vendor.memc.vsid"
// OSD string interface.
#define MEMC_OSD_VIDEO_STR L"RKNPU-SVEP-MEMC"
// One line OSD
#define MEMC_OSD_VIDEO_ONELINE_STR L"AI"

enum MEMC_ERROR {
    MEMC_NO_ERROR = 0,
    MEMC_BAD_VERSION,
    MEMC_BAD_STAGE,
    MEMC_BAD_PARAM,
    MEMC_BAD_LICENCE,
    MEMC_UN_SUPPORTED,
    MEMC_UN_SUCCESS,
    MEMC_INIT_FAILED,
    MEMC_FAIL_AND_TRY_AGAIN,
};

enum MEMC_BUFFER_MASK {
    NONE = 0,
    MEMC_AFBC_FORMAT = 1 << 1,
};

enum MEMC_MODE {
    MEMC_UN_SUPPORT = 0,
    MEMC_720P,
    MEMC_1080P,
    MEMC_4K,
};

struct MemcVersion{
  int iMajor_;
  int iMinor_;
  int iPatchLevel_;
};

enum MEMC_OSD_MODE {
    MEMC_OSD_DISABLE = 0,
    MEMC_OSD_ENABLE_VIDEO,
    MEMC_OSD_ENABLE_VIDEO_ONELINE,
};

enum MEMC_ROTATE_MODE
{
    MEMC_ROTATE_0 = 0 << 0,
    MEMC_ROTATE_90 = 1 << 1,
    MEMC_ROTATE_180 = 1 << 2,
    MEMC_ROTATE_270 = 1 << 3,
    MEMC_REFLECT_X = 1 << 4,
    MEMC_REFLECT_Y = 1 << 5,
};

class MemcRect{
public:
    int iLeft_;   /* 矩形区域 left点坐标 */
    int iTop_;    /* 矩形区域 top点坐标 */
    int iRight_;  /* 矩形区域 right点坐标 */
    int iBottom_; /* 矩形区域 bottom点坐标 */

    int Width() const;
    int Height() const;

    MemcRect() : iLeft_(0), iTop_(0), iRight_(0), iBottom_(0) {}
    MemcRect(const MemcRect& rhs);
    MemcRect& operator=(const MemcRect& rhs);
    bool operator!=(const MemcRect& rhs);
    bool isValid() const;
};

class MemcBufferInfo{
public:
    int iFd_;     /* 图像内容fd文件描述符，通常为 dma-buffer fd */
    int iWidth_;  /* 描述图像宽度，单位为 pixel */
    int iHeight_; /* 描述图像高度，单位为 pixel */
    int iFormat_; /* 描述图像格式，单位为 drm_fourcc */
    int iStride_; /* 描述图像行长度 stride，单位为 pixel */
    int iSize_;   /* 描述图像完整尺寸，单位为 byte */
    uint64_t uBufferId_;   /* 描述图像唯一ID, 通常由分配器唯一分配 */
    uint64_t uColorSpace_; /* 描述图像色域信息 */
    int uMask_; /* 描述图像特殊的标志，例如 AFBC标志 */

    MemcBufferInfo()
        : iFd_(-1),
          iWidth_(0),
          iHeight_(0),
          iFormat_(0),
          iStride_(0),
          iSize_(0),
          uBufferId_(0),
          uColorSpace_(0),
          uMask_(0)
    {
    }
    MemcBufferInfo(const MemcBufferInfo& rhs);
    MemcBufferInfo& operator=(const MemcBufferInfo& rhs);
    bool isValid() const;
};

class MemcImageInfo{
public:
    MemcBufferInfo mBufferInfo_; /* 描述图像信息结构 */
    MemcRect mCrop_;             /* 描述图像crop信息结构 */
    MemcUniqueFd mAcquireFence_;     /* AcquireFence，标志源图像已完成，可进行读写操作 */
    bool mValid; /* 图像是否有效 */

    MemcImageInfo() : mAcquireFence_(-1), mValid(0){};
    MemcImageInfo(const MemcImageInfo& rhs);
    MemcImageInfo& operator=(const MemcImageInfo& rhs);
};

} // namespace android
