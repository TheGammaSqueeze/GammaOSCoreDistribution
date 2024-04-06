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

#include <cwchar>

// Verison info
#define SR_MAGIC   0x83991906
#define SR_VERSION "SR-2.0.5"

#ifdef __ANDROID__
// Android Property
#define SR_VERSION_NAME "vendor.svep.version"
// Release property interface.
#define SR_MODE_NAME                  "persist.sys.svep.mode"
#define SR_RUNTIME_DISABLE_NAME       "sys.svep.runtime_disable"
#define SR_ENHANCEMENT_RATE_NAME      "persist.sys.svep.enhancement_rate"
#define SR_CONTRAST_MODE_NAME         "persist.sys.svep.contrast_mode"
#define SR_CONTRAST_MODE_OFFSET       "persist.sys.svep.contrast_offset_ratio"
#define SR_OSD_DISABLE_MODE           "persist.sys.svep.disable_sr_osd"
#define SR_OSD_VIDEO_ONELINE_MODE     "persist.sys.svep.enable_oneline_osd"
#define SR_AVG_COST_TIME_NAME         "vendor.svep.avg_cost_time"
#define SR_OSD_VIDEO_ONELINE_WATI_SEC "persist.sys.svep.oneline_osd_wait_second"

// One line OSD
#define SR_OSD_VIDEO_ONELINE_STR L"AI"
// 30hz, 360 is 12 second.
#define SR_OSD_VIDEO_ONELINE_CNT 360

// Vendor Storage ID.
#define SR_VENDOR_AUTH_ID           "ro.vendor.svep.vsid"

#define SR_CONTRAST_MODE_ENABLE     1
#define SR_CONTRAST_MODE_LINE_WIDTH 4;

// Debug Property.
#define SR_DEBUG_NAME "vendor.svep.log"
#endif

// OSD string interface.
#define SR_OSD_VIDEO_STR L"RKNPU-SVEP-SR"

enum SrError
{
    None = 0,        /* 无错误，正常 */
    BadVersion,      /* 版本错误     */
    BadStage,        /* 流程错误     */
    BadParameter,    /* 参数错误     */
    BadLicence,      /* 授权错误     */
    BadInit,         /* 初始化错误   */
    BadOperate,      /* 操作错误     */
    FailAndTryAgain, /* 失败并再尝试  */
};

enum SrMode
{
    UN_SUPPORT = 0, /* 不支持模式 */
    SR_360p,        /* 360p模型 */
    SR_540p,        /* 540p模型 */
    SR_720p,        /* 720p模型 */
    SR_1080p,       /* 1080p模型 */
    SR_2160p,       /* 2160p模型 */
    SR_4320p,       /* 4320p模型 */
    SR_4320p_v2,    /* 4320p_v2模型 */
};

enum SrModeUsage
{
    SR_MODE_NONE      = 0,      /* 一般模式 */
    SR_OUTPUT_8K_MODE = 1 << 1, /* 8K输出模式 */
};

enum SrBufferMask
{
    SR_BUFFER_NONE  = 0,     /* 无特殊标志 */
    SR_AFBC_FORMATE = 1 << 1 /* AFBC压缩格式 */
};

enum SrOsdMode
{
    SR_OSD_DISABLE = 0,  /* 关闭OSD */
    SR_OSD_ENABLE_VIDEO, /* 使能ODS */
    SR_OSD_ENABLE_VIDEO_ONELINE,
};

enum SrRotateMode
{
    SR_ROTATE_0   = 0 << 0,
    SR_ROTATE_90  = 1 << 1,
    SR_ROTATE_180 = 1 << 2,
    SR_ROTATE_270 = 1 << 3,
    SR_REFLECT_X  = 1 << 4,
    SR_REFLECT_Y  = 1 << 5,
};

struct SrVersion
{
    int iMajor_;      /* 主版本 */
    int iMinor_;      /* 副版本 */
    int iPatchLevel_; /* 补丁版本 */
};
