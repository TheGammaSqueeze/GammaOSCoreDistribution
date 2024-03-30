/*
 *
 * Copyright 2017 Samsung Electronics S.LSI Co. LTD
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

/*
 * @file    VendorVideoAPI.h
 * @author  TaeHwan Kim    (t_h_kim@samsung.com)
 *          ByungGwan Kang (bk0917.kang@samsung.com)
 * @version 1.0
 * @history
 *   2017.06.02 : Create
 */

#ifndef VENDOR_VIDEO_API_H_
#define VENDOR_VIDEO_API_H_

#include <stdio.h>

#ifdef __cplusplus
extern "C" {
#endif
#define MAX_HDR10PLUS_SIZE 1024

typedef enum _ExynosVideoInfoType {
    VIDEO_INFO_TYPE_INVALID            = 0,
    VIDEO_INFO_TYPE_HDR_STATIC         = 0x1 << 0,
    VIDEO_INFO_TYPE_COLOR_ASPECTS      = 0x1 << 1,
    VIDEO_INFO_TYPE_INTERLACED         = 0x1 << 2,
    VIDEO_INFO_TYPE_YSUM_DATA          = 0x1 << 3,
    VIDEO_INFO_TYPE_HDR_DYNAMIC        = 0x1 << 4,
    VIDEO_INFO_TYPE_CHECK_PIXEL_FORMAT = 0x1 << 5,
    VIDEO_INFO_TYPE_ROI_INFO           = 0x1 << 7,
    VIDEO_INFO_TYPE_CROP_INFO          = 0x1 << 8,
} ExynosVideoInfoType;

typedef struct _ExynosVideoYSUMData {
    unsigned int high;
    unsigned int low;
} ExynosVideoYSUMData;

typedef struct _ExynosColorAspects {
    int mRange;
    int mPrimaries;
    int mTransfer;
    int mMatrixCoeffs;
} ExynosColorAspects;

typedef struct _ExynosPrimaries {
    unsigned int x;
    unsigned int y;
} ExynosPrimaries;

typedef struct _ExynosType1 {
    ExynosPrimaries mR;
    ExynosPrimaries mG;
    ExynosPrimaries mB;
    ExynosPrimaries mW;
    unsigned int    mMaxDisplayLuminance;
    unsigned int    mMinDisplayLuminance;
    unsigned int    mMaxContentLightLevel;
    unsigned int    mMaxFrameAverageLightLevel;
} ExynosType1;

typedef struct _ExynosHdrStaticInfo {
    int mID;
    union {
        ExynosType1 sType1;
    };
} ExynosHdrStaticInfo;

typedef struct _ExynosHdrDynamicInfo {
    unsigned int valid;

    struct {
        unsigned char  country_code;
        unsigned short provider_code;
        unsigned short provider_oriented_code;

        unsigned char  application_identifier;
        unsigned short application_version;

        unsigned int  display_maximum_luminance;

        unsigned int maxscl[3];

        unsigned char num_maxrgb_percentiles;
        unsigned char maxrgb_percentages[15];
        unsigned int  maxrgb_percentiles[15];

        struct {
            unsigned short  tone_mapping_flag;
            unsigned short  knee_point_x;
            unsigned short  knee_point_y;
            unsigned short  num_bezier_curve_anchors;
            unsigned short  bezier_curve_anchors[15];
        } tone_mapping;
    } data;

    unsigned int reserved[42];
} ExynosHdrDynamicInfo;

typedef struct _ExynosVideoCrop {
    int left;
    int top;
    int width;
    int height;
} ExynosVideoCrop;

#define MAX_ROIINFO_SIZE 32400
typedef struct _ExynosVideoROIData {
    int     nUpperQpOffset;
    int     nLowerQpOffset;
    int     bUseRoiInfo;
    int     nRoiMBInfoSize;
    char    pRoiMBInfo[MAX_ROIINFO_SIZE];
} ExynosVideoROIData;

typedef struct _ExynosVideoDecData {
    int nInterlacedType;
} ExynosVideoDecData;

typedef struct _ExynosVideoEncData {
    ExynosVideoYSUMData sYsumData;
    unsigned long long pRoiData;  /* for fixing byte alignment on 64x32 problem */
} ExynosVideoEncData;

typedef struct _ExynosVideoMeta {
    ExynosVideoInfoType eType;

    ExynosHdrStaticInfo  sHdrStaticInfo;
    ExynosColorAspects   sColorAspects;

    union {
        ExynosVideoDecData dec;
        ExynosVideoEncData enc;
    } data;

    ExynosHdrDynamicInfo sHdrDynamicInfo;

    int nPixelFormat;
    ExynosVideoCrop crop;
} ExynosVideoMeta;

int Exynos_parsing_user_data_registered_itu_t_t35(ExynosHdrDynamicInfo *dest, void *src, int size);
int Exynos_dynamic_meta_to_itu_t_t35(ExynosHdrDynamicInfo *src, char *dst);

#ifdef __cplusplus
}
#endif

#endif /* VENDOR_VIDEO_API_H_ */
