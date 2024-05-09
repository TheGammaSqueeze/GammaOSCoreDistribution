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

#include <stdint.h>

#include "SrType.h"
#include "utils/autofd.h"

class SrRect
{
public:
    int iLeft_;   /* 矩形区域 left点坐标 */
    int iTop_;    /* 矩形区域 top点坐标 */
    int iRight_;  /* 矩形区域 right点坐标 */
    int iBottom_; /* 矩形区域 bottom点坐标 */

    int Width() const;
    int Height() const;

    SrRect() : iLeft_(0), iTop_(0), iRight_(0), iBottom_(0) {}
    SrRect(const SrRect& rhs);
    SrRect& operator=(const SrRect& rhs);
    bool operator!=(const SrRect& rhs);
    bool isValid() const;
};

class SrBufferInfo
{
public:
    int iFd_;     /* 图像内容fd文件描述符，通常为 dma-buffer fd */
    int iWidth_;  /* 描述图像宽度，单位为 pixel */
    int iHeight_; /* 描述图像高度，单位为 pixel */
    int iFormat_; /* 描述图像格式，单位为 drm_fourcc */
    int iStride_; /* 描述图像行长度 stride，单位为 pixel */
    int iSize_;   /* 描述图像完整尺寸，单位为 byte */
    uint64_t uBufferId_; /* 描述图像唯一ID, 通常由分配器唯一分配 */
    uint64_t uColorSpace_; /* 描述图像色域信息 */
    int uMask_;            /* 描述图像特殊的标志，例如 AFBC标志 */

    SrBufferInfo()
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
    SrBufferInfo(const SrBufferInfo& rhs);
    SrBufferInfo& operator=(const SrBufferInfo& rhs);
    bool isValid() const;
};

class SrImageInfo
{
public:
    SrBufferInfo mBufferInfo_; /* 描述图像信息结构 */
    SrRect mCrop_;             /* 描述图像crop信息结构 */
    SrUniqueFd
        mAcquireFence_; /* AcquireFence，标志源图像已完成，可进行读写操作 */
    bool mValid; /* 图像是否有效 */
    SrImageInfo() : mAcquireFence_(-1), mValid(0){};
    SrImageInfo(const SrImageInfo& rhs);

    SrImageInfo& operator=(const SrImageInfo& rhs);
};