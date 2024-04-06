//
/////////////////////////////////////////////////////////////////////////
// Copyright(c) 2022 by Rockchip Corp. All right reserved.
//
// This file is Rock-chip's property. It contains Rock-chip's trade secret,
// proprietary and confidential information.
// The information and code contained in this file is only for authorized
// Rock-chip employees to design, create, modify, or review.
// DO NOT DISTRIBUTE, DO NOT DUPLICATE OR TRANSMIT IN ANY FORM WITHOUT
// PROPER AUTHORIZATION.
// If you are not an intended recipient of this file, you must not copy,
// distribute, modify, or take any action in reliance on it.
// If you have received this file in error, please immediately notify
// Rock-chip and permanently delete the original and any copy of any file
// and any printout thereof.
//
//////////////////////////////////////////////////////////////////////////
//
// Last update 2022-10-21

#pragma once
#ifndef __RKPQ_API_H_
#define __RKPQ_API_H_

#include <stdint.h>

#ifdef __cplusplus
extern "C"
{
#endif

/**
 *  Definition of flags for rkpq_init()
 */
#define RKPQ_FLAG_DEFAULT               0x00000000
#define RKPQ_FLAG_PERF_DETAIL           0x00000001  /* reserved */
#define RKPQ_FLAG_HIGH_PERFORM          0x00000002  /* fuse some PQ modules to achive high performence */
#define RKPQ_FLAG_CALC_MEAN_LUMA        0x00000008  /* calculate mean luma value (full-range) when processing */
#define RKPQ_FLAG_CVT_RANGE_ONLY        0x00000010  /* convert between full and limited range only, no PQ modules to run */


/**
 *  Definition of const numbers
 */
#define RKPQ_MAX_PLANE_NUM              3   // max number of planes supported. Only support {NV24, NV12, RGBA} right now!
#define RKPQ_MAX_PERFORM_NUM            32  // max number of proc performence count
#define RKPQ_MAX_IMG_FMT_NUM            32  // max number of supported image formats
#define RKPQ_MAX_CLR_SPC_NUM            32  // max number of supported color spaces
#define RKPQ_MAX_PQ_MODULE_NUM          32  // max number of supported PQ modules
#define RKPQ_DCI_LUT_SIZE               33  // valid DCI_Y LUT range [0:32]
#define RKPQ_ACM_LUT_LENGTH_Y           9   // valid ACM_Y LUT range [0:8]
#define RKPQ_ACM_LUT_LENGTH_H           65  // valid ACM_H LUT range [0:64]
#define RKPQ_ACM_LUT_LENGTH_S           13  // valid ACM_S LUT range [0:12]
#define RKPQ_SHP_PEAKING_BAND_NUM       4   // number of valid bands for Sharp module
#define RKPQ_ZME_COEF_LENGTH            8   // valid ZME coefs range [0:7]


/**
 * Forward declaration
 */
typedef void* rkpq_context;
struct _RKPQ_CSC_CFG;
struct _RKPQ_DCI_CFG;
struct _RKPQ_ACM_CFG;
struct _RKPQ_SHP_CFG;
struct _RKPQ_SR_CFG;
struct _RKPQ_ZME_CFG;

/**
 * the PQ modules, use rkpq_query() to check if supported
 */
typedef enum _rkpq_module
{
    RKPQ_MODULE_CSC,    /* Color Space Convert */
    RKPQ_MODULE_SHP,    /* Sharpen */
    RKPQ_MODULE_DCI,    /* Dynamic Contrast Improvement */
    RKPQ_MODULE_ACM,    /* Auto Color Management */
    RKPQ_MODULE_SR,     /* Super Resolution */
    RKPQ_MODULE_ZME,    /* Zoom Manage Engine */
    // RKPQ_MODULE_3DLUT,  /* 3D LUT | TODO */
    RKPQ_MODULE_MAX,    /* the max PQ module value, please DO NOT use this item! */
} rkpq_module;

/**
 * the query commands, see rkpq_query() for more detail infos
 */
typedef enum _rkpq_query_cmd
{
    RKPQ_QUERY_SDK_VERSION = 0,         /* get the SDK version info */
    RKPQ_QUERY_PERF_INFO,               /* get the performence info after rkpq_proc() */
    RKPQ_QUERY_IMG_FMT_INPUT_SUPPORT,   /* get the supported image formats for input */
    RKPQ_QUERY_IMG_FMT_OUTPUT_SUPPORT,  /* get the supported image formats for output */
    RKPQ_QUERY_IMG_FMT_CHANGE_SUPPORT,  /* get the flag if enable change image format when running */
    // RKPQ_QUERY_IMG_RES_INPUT_SUPPORT,   /* get the supported image resolutions for input */
    // RKPQ_QUERY_IMG_RES_OUTPUT_SUPPORT,  /* get the supported image resolutions for output */
    RKPQ_QUERY_IMG_RES_CHANGE_SUPPORT,  /* get the flag if enable change image resolution when running */
    RKPQ_QUERY_IMG_COLOR_SPACE_SUPPORT, /* get the supported image color space */
    RKPQ_QUERY_IMG_BUF_INFO,            /* get the image buffer infos with known image format & size */
    RKPQ_QUERY_IMG_ALIGNMENT_OCL,       /* get the OpenCL image alignment size in width, unit: pixel */
    RKPQ_QUERY_RKNN_SUPPORT,            /* get the RKNN supported flag for SR */
    RKPQ_QUERY_MEAN_LUMA,               /* get the mean luma value (full-range) of the output image after rkpq_proc() */
    RKPQ_QUERY_MODULES_SUPPORT,         /* get the supported PQ modules */
    // RKPQ_QUERY_3DLUT_AI_TABLE,          /* get the 3D-LUT table from RKNN result */
    RKPQ_QUERY_MAX,                     /* the max query command value, please DO NOT use this item! */
} rkpq_query_cmd;

/**
 * the image formats supported
 * @Detail:
 *  - bpp means 'bits per pixel',
 *  - bpc means 'bits per component'.
 */
typedef enum _rkpq_image_format
{
    // YUV
    RKPQ_IMG_FMT_YUV_MIN = 0,   /* the min YUV format value, please DO NOT use this item! */
    RKPQ_IMG_FMT_NV24 = 0,      /* YUV444SP, 2 plane YCbCr, 24bpp/8 bpc, non-subsampled Cr:Cb plane */
    RKPQ_IMG_FMT_NV16,          /* YUV422SP, 2 plane YCbCr, 16bpp/8 bpc, 2x1 subsampled Cr:Cb plane */
    RKPQ_IMG_FMT_NV12,          /* YUV420SP, 2 plane YCbCr, 12bpp/8 bpc, 2x2 subsampled Cr:Cb plane */
    RKPQ_IMG_FMT_NV15,          /* YUV420SP, 2 plane YCbCr, 15bpp/10bpc, 10bit packed data */
    RKPQ_IMG_FMT_NV20,          /* YUV422SP, 2 plane YCbCr, 20bpp/10bpc, 10bit packed data, output supported only */ /* reserved */
    RKPQ_IMG_FMT_NV30,          /* YUV444SP, 2 plane YCbCr, 30bpp/10bpc, 10bit packed data, output supported only */
    RKPQ_IMG_FMT_P010,          /* YUV420SP, 2 plane YCbCr, 24bpp/16bpc, 10bit unpacked data with MSB aligned, output supported only */
    RKPQ_IMG_FMT_P210,          /* YUV422SP, 2 plane YCbCr, 32bpp/16bpc, 10bit unpacked data with MSB aligned, output supported only */ /* reserved */
    RKPQ_IMG_FMT_Q410,          /* YUV444P , 3 plane YCbCr, 48bpp/16bpc, 10bit unpacked data with MSB aligned, output supported only */
    RKPQ_IMG_FMT_YUV_MAX,       /* the max YUV format value, please DO NOT use this item! */

    // RGB
    RKPQ_IMG_FMT_RGB_MIN = 1000,/* the min RGB format value, please DO NOT use this item! */
    RKPQ_IMG_FMT_RGBA = 1000,   /* RGBA8888, 32bpp */
    RKPQ_IMG_FMT_RG24,          /* RGB888, 24bpp */
    RKPQ_IMG_FMT_BG24,          /* BGR888, 24bpp */
    RKPQ_IMG_FMT_AB30,          /* ABGR2101010, reserved */
    RKPQ_IMG_FMT_RGB_MAX,       /* the max RGB format value, please DO NOT use this item! */
} rkpq_img_fmt;

/**
 * the color space supported
 */
typedef enum _rkpq_color_space
{
    RKPQ_CLR_SPC_YUV_601_LIMITED,       /* ITU-R BT.601 (Limited-range) for SDTV (720P) */
    RKPQ_CLR_SPC_YUV_601_FULL,          /* ITU-R BT.601 Full-range      for SDTV (720P) */
    RKPQ_CLR_SPC_YUV_709_LIMITED,       /* ITU-R BT.709 (Limited-range) for HDTV (1080P) */
    RKPQ_CLR_SPC_YUV_709_FULL,          /* ITU-R BT.709 Full-range      for HDTV (1080P) */
    RKPQ_CLR_SPC_YUV_2020_LIMITED,      /* reserved. ITU-R BT.2020 (Limited-range) for UHDTV (4K/8K) */
    RKPQ_CLR_SPC_YUV_2020_FULL,         /* reserved. ITU-R BT.2020 Full-range      for UHDTV (4K/8K) */
    RKPQ_CLR_SPC_RGB_LIMITED,           /* RGB Limited-range */
    RKPQ_CLR_SPC_RGB_FULL,              /* RGB Full-range */
    RKPQ_CLR_SPC_MAX,                   /* the max color space value, please DO NOT use this item! */
} rkpq_clr_spc;

/**
 *  the information for RKPQ_QUERY_SDK_VERSION
 */
typedef struct _rkpq_version_info
{
    uint32_t    nVerMajor;      /* the major number */
    uint32_t    nVerMinor;      /* the minor number */
    uint32_t    nVerRvson;      /* the revision number */
    char        sVerInfo[64];   /* the full version info string */
} rkpq_version_info;

/**
 * the information for RKPQ_QUERY_PERF_INFO
 */
typedef struct _rkpq_perf_info
{
    float   fTimeCostInit;      /* cost time of rkpq_init() interface */
    float   fTimeCostDeinit;    /* invalid */
    float   fTimeCostProcs[RKPQ_MAX_PERFORM_NUM];
} rkpq_perf_info;

/**
 * the information for RKPQ_QUERY_IMG_FMT_INPUT_SUPPORT & RKPQ_QUERY_IMG_FMT_OUTPUT_SUPPORT
 */
typedef struct _rkpq_imgfmt_info
{
    int32_t     aValidFmts[RKPQ_MAX_IMG_FMT_NUM];   /* see rkldc_img_fmt */
    uint32_t    nValidFmtNum;                       /* number of valid formats, <= RKPQ_MAX_IMG_FMT_NUM */
} rkpq_imgfmt_info;

/**
 * the information for RKPQ_QUERY_IMG_COLOR_SPACE_SUPPORT
 */
typedef struct _rkpq_clrspc_info
{
    int32_t     aValidSpcs[RKPQ_MAX_CLR_SPC_NUM];   /* see rkpq_clr_spc */
    uint32_t    nValidSpcNum;                       /* number of valid color spaces, <= RKPQ_MAX_CLR_SPCE_NUM */
} rkpq_clrspc_info;

/**
 * the information for RKPQ_QUERY_IMG_BUF_INFO
 * @Detail: bpc means 'bits per component'
 */
typedef struct _rkpq_imgbuf_info
{
    uint32_t    nColorSpace;                        /* [i] see rkpq_clr_spc */
    uint32_t    nPixFmt;                            /* [i] see rkpq_img_fmt */
    uint32_t    nPixWid;                            /* [i] pixel width */
    uint32_t    nPixHgt;                            /* [i] pixel height */
    uint32_t    nEleDepth;                          /* [i] element depth (bpc), unit: bit */
    uint32_t    nAlignment;                         /* [i] buffer alignment length / row pitch, unit: byte */
    uint32_t    aWidStrides[RKPQ_MAX_PLANE_NUM];    /* [i/o] image padding width of each plane, unit: byte. aWidStrides[0] always >= nPixWid.
                                                        Set aWidStrides[0] if padding exist, and aWidStrides[1:2] will be auto updated according to `nPixFmt`.
                                                        If aWidStrides[0] = 0, all the values will be updated with `nAlignment`. */
    uint32_t    aHgtStrides[RKPQ_MAX_PLANE_NUM];    /* [i/o] image padding height of each plane, unit: line.
                                                        Set aHgtStrides[0] if padding exist, and aHgtStrides[1:2] will be auto updated according to `nPixFmt`.
                                                        If aHgtStrides[0] = 0, it will be updated equal to `nPixHgt`. */
    uint32_t    nPixWidStrd;                        /* [o] pixel width stride with padding, unit: pixel */
    uint32_t    nPlaneNum;                          /* [o] number of valid planes */
    size_t      nFrameSize;                         /* [o] full frame size, unit: byte */
    size_t      aPlaneSizes[RKPQ_MAX_PLANE_NUM];    /* [o] size of each plane, unit: byte */
    uint32_t    aPlaneElems[RKPQ_MAX_PLANE_NUM];    /* [o] element number of each plane */
} rkpq_imgbuf_info;

/**
 * the information for RKPQ_QUERY_MODULES_SUPPORT
 */
typedef struct _rkpq_module_info
{
    int32_t     aValidMods[RKPQ_MAX_PQ_MODULE_NUM]; /* see rkpq_module */
    uint32_t    nValidModNum;                       /* number of valid PQ modules, <= RKPQ_MAX_PQ_MODULE_NUM */
} rkpq_module_info;


/**
 * init parameters
 */
typedef struct _RKPQ_Init_Params
{
    rkpq_imgbuf_info    stSrcImgInfo;       /* [i] src image buffer info */
    rkpq_imgbuf_info    stDstImgInfo;       /* [i] dst image buffer info */

    // init flag
    uint32_t            nInitFlag;          /* [i] see RKPQ_FLAG_XXXX */
} RKPQ_Init_Params;


/**
 * proc parameters
 */
typedef struct _RKPQ_Proc_Params
{
    // Data information
    uint32_t            nFrameIdx;                          /* [i] image index in the video sequence */

    uint8_t             *pImgSrcs[RKPQ_MAX_PLANE_NUM];      /* [i] plane pointers of src buffer */
    int32_t             fdImgSrc;                           /* [i] file descriptor, used to import hardware buffer */
    uint32_t            nSrcBufSize;                        /* [i] src buffer size, it should be >= stSrcImgInfo::nFrameSize! unit: byte */
    uint8_t             *pImgDsts[RKPQ_MAX_PLANE_NUM];      /* [i] plane pointers of dst buffer */
    int32_t             fdImgDst;                           /* [i] file descriptor, used to import hardware buffer */
    uint32_t            nDstBufSize;                        /* [i] dst buffer size, it should be >= stDstImgInfo::nFrameSize! unit: byte */

    // Proc flags
    uint32_t            bEnablePropControl;                 /* [i] enable real-time control with adb properties. PQTool will not work if set to 1 */
    uint32_t            bEnableSliderControl;               /* [i] enable real-time control with PQTool */
    uint32_t            bEnableCalcMeanLuma;                /* [i] enable calculate mean luma value (full-range) of output image, */
                                                            /* init flag 'RKPQ_FLAG_CALC_MEAN_LUMA' shoule be set in 'RKPQ_Init_Params::nInitFlag' */
    uint32_t            bEnableConvertFLOnly;               /* [i] enable convert input image range between full and limited, */
                                                            /* init flag 'RKPQ_FLAG_CVT_RANGE_ONLY' shoule be set in 'RKPQ_Init_Params::nInitFlag' */
    uint32_t            nProcWidth;                         /* [i] proc width ref to 'stSrcImgInfo::nPixWid', range: [0, 9600], unit: pixel */
    uint32_t            bIsIntraFrame;                      /* [i] if the video frame is the intra frame */
    uint32_t            aReservedFlags[24];                 /* reserved array, for new flags added in the future */

    // return values
    uint32_t            nMeanLuma;                          /* [o] return the mean luma value (full-range) after rkpq_proc() */

    // Module configurations
    _RKPQ_CSC_CFG       *pConfigCSC;                         /* [i] set to NULL if donot want to open this module */
    _RKPQ_DCI_CFG       *pConfigDCI;                         /* [i] set to NULL if donot want to open this module */
    _RKPQ_ACM_CFG       *pConfigACM;                         /* [i] set to NULL if donot want to open this module */
    _RKPQ_SHP_CFG       *pConfigSHP;                         /* [i] set to NULL if donot want to open this module */
    _RKPQ_SR_CFG        *pConfigSR;                          /* [i] set to NULL if donot want to open this module */
    _RKPQ_ZME_CFG       *pConfigZME;                         /* [i] set to NULL if donot want to open this module */
    // TODO: add new module here

} RKPQ_Proc_Params;



/**
 * the configurations for PQ modules
 */
/* CSC configuration */
typedef struct _RKPQ_CSC_CFG
{
    // M4_BOOL_DESC("bEnableCSC", "1")
    bool        bEnableCSC;

    // M4_NUMBER_DESC("nBrightness", "u32", M4_RANGE(0,511), "256", M4_DIGIT(0))
    uint32_t    nBrightness;
    // M4_NUMBER_DESC("nHue", "u32", M4_RANGE(0,511), "256", M4_DIGIT(0))
    uint32_t    nHue;
    // M4_NUMBER_DESC("nContrast", "u32", M4_RANGE(0,511), "256", M4_DIGIT(0))
    uint32_t    nContrast;
    // M4_NUMBER_DESC("nSaturation", "u32", M4_RANGE(0,511), "256", M4_DIGIT(0))
    uint32_t    nSaturation;
    // M4_NUMBER_DESC("nRGain", "u32", M4_RANGE(0,511), "256", M4_DIGIT(0))
    uint32_t    nRGain;
    // M4_NUMBER_DESC("nGGain", "u32", M4_RANGE(0,511), "256", M4_DIGIT(0))
    uint32_t    nGGain;
    // M4_NUMBER_DESC("nBGain", "u32", M4_RANGE(0,511), "256", M4_DIGIT(0))
    uint32_t    nBGain;
    // M4_NUMBER_DESC("nROffset", "u32", M4_RANGE(0,511), "256", M4_DIGIT(0))
    uint32_t    nROffset;
    // M4_NUMBER_DESC("nGOffset", "u32", M4_RANGE(0,511), "256", M4_DIGIT(0))
    uint32_t    nGOffset;
    // M4_NUMBER_DESC("nBOffset", "u32", M4_RANGE(0,511), "256", M4_DIGIT(0))
    uint32_t    nBOffset;

    void setDefault();
} RKPQ_CSC_CFG;

/* DCI configuration */
typedef struct _RKPQ_DCI_CFG
{
    // M4_BOOL_DESC("bEnableDCI", "1")
    bool        bEnableDCI;

    // M4_ARRAY_MARK_DESC("aWgtCoefLow", "u16", M4_SIZE(1,33),  M4_RANGE(0, 1024), "[0,54,109,163,217,265,312,359,406,444,481,519,556,587,618,648,679,708,738,767,796,819,842,864,887,903,918,934,949,959,969,979,989]", M4_DIGIT(0), M4_DYNAMIC(1), "curve_table")
    uint16_t    aWgtCoefLow[RKPQ_DCI_LUT_SIZE];
    // M4_ARRAY_MARK_DESC("aWgtCoefMid", "u16", M4_SIZE(1,33),  M4_RANGE(0, 1024), "[0,20,40,60,80,112,145,178,211,269,327,384,442,490,538,582,626,663,701,738,775,798,821,843,866,885,905,924,943,963,983,1003,1023]", M4_DIGIT(0), M4_DYNAMIC(1), "curve_table")
    uint16_t    aWgtCoefMid[RKPQ_DCI_LUT_SIZE];
    // M4_ARRAY_MARK_DESC("aWgtCoefHigh", "u16", M4_SIZE(1,33),  M4_RANGE(0, 1024), "[0,9,17,26,35,57,80,102,125,151,178,205,232,261,289,318,346,379,412,445,478,508,538,571,607,643,685,733,793,856,916,970,1023]", M4_DIGIT(0), M4_DYNAMIC(1), "curve_table")
    uint16_t    aWgtCoefHigh[RKPQ_DCI_LUT_SIZE];
    // M4_ARRAY_MARK_DESC("aWeightLow", "u16", M4_SIZE(1,32),  M4_RANGE(0, 32), "[16, 16, 16, 16, 14, 12, 10, 8, 6, 4, 2, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0]", M4_DIGIT(0), M4_DYNAMIC(1), "curve_table")
    uint16_t    aWeightLow[RKPQ_DCI_LUT_SIZE - 1];
    // M4_ARRAY_MARK_DESC("aWeightMid", "u16", M4_SIZE(1,32),  M4_RANGE(0, 32), "[0, 0, 0, 0, 3, 6, 9, 12, 15, 18, 18, 18, 18, 18, 18, 18, 18, 18, 18, 18, 18, 18, 15, 12, 9, 6, 3, 0, 0, 0, 0]", M4_DIGIT(0), M4_DYNAMIC(1), "curve_table")
    uint16_t    aWeightMid[RKPQ_DCI_LUT_SIZE - 1];
    // M4_ARRAY_MARK_DESC("aWeightHigh", "u16", M4_SIZE(1,32),  M4_RANGE(0, 32), "[0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 16, 16, 16, 16]", M4_DIGIT(0), M4_DYNAMIC(1), "curve_table")
    uint16_t    aWeightHigh[RKPQ_DCI_LUT_SIZE - 1];

    /* only for env vars below: */
    uint32_t    nContrastGlobal;        /* Range: [0, 511], default: 256. */
    uint32_t    nContrastDark;          /* Range: [0, 511], default: 256. */
    uint32_t    nContrastLight;         /* Range: [0, 511], default: 256. */

    void setDefault();
} RKPQ_DCI_CFG;

/* ACM configuration */
typedef struct _RKPQ_ACM_CFG
{
    // M4_BOOL_DESC("bEnableACM", "1")
    bool        bEnableACM;

    // M4_ARRAY_MARK_DESC("aTableDeltaYbyH", "u8", M4_SIZE(1,65),  M4_RANGE(0, 255), "[128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128]", M4_DIGIT(0), M4_DYNAMIC(1), "curve_table")
    uint8_t     aTableDeltaYbyH[RKPQ_ACM_LUT_LENGTH_H];
    // M4_ARRAY_MARK_DESC("aTableDeltaHbyH", "u8", M4_SIZE(1,65),  M4_RANGE(0, 127), "[64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64]", M4_DIGIT(0), M4_DYNAMIC(1), "curve_table")
    uint8_t     aTableDeltaHbyH[RKPQ_ACM_LUT_LENGTH_H];
    // M4_ARRAY_MARK_DESC("aTableDeltaSbyH", "u8", M4_SIZE(1,65),  M4_RANGE(0, 255), "[128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128, 128]", M4_DIGIT(0), M4_DYNAMIC(1), "curve_table")
    uint8_t     aTableDeltaSbyH[RKPQ_ACM_LUT_LENGTH_H];
    // M4_ARRAY_MARK_DESC("aTableGainYbyY", "u8", M4_SIZE(1,9),  M4_RANGE(0, 128), "[128, 128, 128, 128, 128, 128, 128, 128, 128]", M4_DIGIT(0), M4_DYNAMIC(1), "curve_table")
    uint8_t     aTableGainYbyY[RKPQ_ACM_LUT_LENGTH_Y];
    // M4_ARRAY_MARK_DESC("aTableGainHbyY", "u8", M4_SIZE(1,9),  M4_RANGE(0, 128), "[128, 128, 128, 128, 128, 128, 128, 128, 128]", M4_DIGIT(0), M4_DYNAMIC(1), "curve_table")
    uint8_t     aTableGainHbyY[RKPQ_ACM_LUT_LENGTH_Y];
    // M4_ARRAY_MARK_DESC("aTableGainSbyY", "u8", M4_SIZE(1,9),  M4_RANGE(0, 128), "[128, 128, 128, 128, 128, 128, 128, 128, 128]", M4_DIGIT(0), M4_DYNAMIC(1), "curve_table")
    uint8_t     aTableGainSbyY[RKPQ_ACM_LUT_LENGTH_Y];
    // M4_ARRAY_MARK_DESC("aTableGainYbyS", "u8", M4_SIZE(1,13),  M4_RANGE(0, 128), "[128, 128, 128, 128, 128, 128, 128, 128, 128]", M4_DIGIT(0), M4_DYNAMIC(1), "curve_table")
    uint8_t     aTableGainYbyS[RKPQ_ACM_LUT_LENGTH_S];
    // M4_ARRAY_MARK_DESC("aTableGainHbyS", "u8", M4_SIZE(1,13),  M4_RANGE(0, 128), "[128, 128, 128, 128, 128, 128, 128, 128, 128]", M4_DIGIT(0), M4_DYNAMIC(1), "curve_table")
    uint8_t     aTableGainHbyS[RKPQ_ACM_LUT_LENGTH_S];
    // M4_ARRAY_MARK_DESC("aTableGainSbyS", "u8", M4_SIZE(1,13),  M4_RANGE(0, 128), "[128, 128, 128, 128, 128, 128, 128, 128, 128]", M4_DIGIT(0), M4_DYNAMIC(1), "curve_table")
    uint8_t     aTableGainSbyS[RKPQ_ACM_LUT_LENGTH_S];
    // M4_NUMBER_DESC("nLumGain", "u32", M4_RANGE(0,511), "256", M4_DIGIT(0))
    uint32_t    nLumGain;
    // M4_NUMBER_DESC("nHueGain", "u32", M4_RANGE(0,511), "256", M4_DIGIT(0))
    uint32_t    nHueGain;
    // M4_NUMBER_DESC("nSatGain", "u32", M4_RANGE(0,511), "256", M4_DIGIT(0))
    uint32_t    nSatGain;

    /* only for env vars below: */
    uint32_t    nHueRed;            /* Range: [0, 511], default: 256. */
    uint32_t    nHueGreen;          /* Range: [0, 511], default: 256. */
    uint32_t    nHueBlue;           /* Range: [0, 511], default: 256. */
    uint32_t    nHueSkin;           /* Range: [0, 511], default: 256. */
    uint32_t    nSaturation;        /* Range: [0, 511], default: 256. */

    void setDefault();
} RKPQ_ACM_CFG;

/* Sharp configuration */
typedef struct _RKPQ_SHP_CFG
{
    // M4_BOOL_DESC("bEnableSHP", "1")
    bool        bEnableSHP;
    // M4_NUMBER_DESC("nPeakingGain", "u32", M4_RANGE(0,1024), "256", M4_DIGIT(0))
    uint32_t    nPeakingGain;

    // M4_BOOL_DESC("bEnableShootCtrl", "1")
    bool        bEnableShootCtrl;
    // M4_NUMBER_DESC("nShootCtrlOver", "u32", M4_RANGE(0,128), "36", M4_DIGIT(0))
    uint32_t    nShootCtrlOver;
    // M4_NUMBER_DESC("nShootCtrlUnder", "u32", M4_RANGE(0,128), "36", M4_DIGIT(0))
    uint32_t    nShootCtrlUnder;

    // M4_BOOL_DESC("bEnableCoringCtrl", "1")
    bool        bEnableCoringCtrl;
    // M4_ARRAY_MARK_DESC("aCoringCtrlRatio", "u16", M4_SIZE(1,4),  M4_RANGE(512, 2048), "[2048,2048,2048,2048]", M4_DIGIT(0), M4_DYNAMIC(1), "curve_table")
    uint16_t    aCoringCtrlRatio[RKPQ_SHP_PEAKING_BAND_NUM];
    // M4_ARRAY_MARK_DESC("aCoringCtrlZero", "u16", M4_SIZE(1,4),  M4_RANGE(0, 32), "[4,4,4,4]", M4_DIGIT(0), M4_DYNAMIC(1), "curve_table")
    uint16_t    aCoringCtrlZero[RKPQ_SHP_PEAKING_BAND_NUM];
    // M4_ARRAY_MARK_DESC("aCoringCtrlThrd", "u16", M4_SIZE(1,4),  M4_RANGE(0, 64), "[40,40,40,40]", M4_DIGIT(0), M4_DYNAMIC(1), "curve_table")
    uint16_t    aCoringCtrlThrd[RKPQ_SHP_PEAKING_BAND_NUM];

    // M4_BOOL_DESC("bEnableGainCtrl", "1")
    bool        bEnableGainCtrl;
    // M4_ARRAY_MARK_DESC("aGainCtrlPos", "u16", M4_SIZE(1,4),  M4_RANGE(0, 2048), "[1024,1024,1024,1024]", M4_DIGIT(0), M4_DYNAMIC(1), "curve_table")
    uint16_t    aGainCtrlPos[RKPQ_SHP_PEAKING_BAND_NUM];

    // M4_BOOL_DESC("bEnableLimitCtrl", "0")
    bool        bEnableLimitCtrl;
    // M4_ARRAY_MARK_DESC("aLimitCtrlPos0", "u16", M4_SIZE(1,4),  M4_RANGE(0, 127), "[64,64,64,64]", M4_DIGIT(0), M4_DYNAMIC(1), "curve_table")
    uint16_t    aLimitCtrlPos0[RKPQ_SHP_PEAKING_BAND_NUM];
    // M4_ARRAY_MARK_DESC("aLimitCtrlPos1", "u16", M4_SIZE(1,4),  M4_RANGE(0, 127), "[120,120,120,120]", M4_DIGIT(0), M4_DYNAMIC(1), "curve_table")
    uint16_t    aLimitCtrlPos1[RKPQ_SHP_PEAKING_BAND_NUM];
    // M4_ARRAY_MARK_DESC("aLimitCtrlBndPos", "u16", M4_SIZE(1,4),  M4_RANGE(0, 127), "[65,65,65,65]", M4_DIGIT(0), M4_DYNAMIC(1), "curve_table")
    uint16_t    aLimitCtrlBndPos[RKPQ_SHP_PEAKING_BAND_NUM];
    // M4_ARRAY_MARK_DESC("aLimitCtrlRatio", "u16", M4_SIZE(1,4),  M4_RANGE(0, 512), "[128,128,128,128]", M4_DIGIT(0), M4_DYNAMIC(1), "curve_table")
    uint16_t    aLimitCtrlRatio[RKPQ_SHP_PEAKING_BAND_NUM];

    void setDefault();
} RKPQ_SHP_CFG;

/* SR configuration */
typedef struct _RKPQ_SR_CFG
{
    // M4_BOOL_DESC("bEnableSR", "1")
    bool        bEnableSR;

    /* dir filter & interp */
    // M4_BOOL_DESC("bEnableDirFilter", "1")
    bool        bEnableDirFilter;
    // M4_NUMBER_DESC("nEdgeThreshold", "u32", M4_RANGE(0,255), "30", M4_DIGIT(0))
    uint32_t    nEdgeThreshold;
    // M4_NUMBER_DESC("nSinglePixelRetain", "u32", M4_RANGE(5,20), "10", M4_DIGIT(0))
    uint32_t    nSinglePixelRetain;
    // M4_NUMBER_DESC("nSinglePixelAband", "u32", M4_RANGE(180,220), "200", M4_DIGIT(0))
    uint32_t    nSinglePixelAband;      /* not work, Reserved */
    // M4_NUMBER_DESC("nMinNeighborCandNum", "u32", M4_RANGE(1,5), "3", M4_DIGIT(0))
    uint32_t    nMinNeighborCandNum;
    // M4_NUMBER_DESC("nMinMainDirPercent", "u32", M4_RANGE(0,255), "128", M4_DIGIT(0))
    uint32_t    nMinMainDirPercent;

    /* RKNN-based SR */
    // M4_BOOL_DESC("bEnableRknnSR", "1")
    bool        bEnableRknnSR;
    // M4_BOOL_DESC("bEnableUsm", "1")
    bool        bEnableUsm;
    // M4_NUMBER_DESC("nUsmGain", "u32", M4_RANGE(0,255), "128", M4_DIGIT(0))
    uint32_t    nUsmGain;
    // M4_NUMBER_DESC("nUsmCtrlOver", "u32", M4_RANGE(0,255), "128", M4_DIGIT(0))
    uint32_t    nUsmCtrlOver;
    // M4_NUMBER_DESC("nUsmCtrlUnder", "u32", M4_RANGE(0,255), "128", M4_DIGIT(0))
    uint32_t    nUsmCtrlUnder;
    // M4_NUMBER_DESC("nColorStrength", "u32", M4_RANGE(0,256), "128", M4_DIGIT(0))
    uint32_t    nColorStrength;
    // M4_NUMBER_DESC("nEdgeStrength", "u32", M4_RANGE(0,256), "128", M4_DIGIT(0))
    uint32_t    nEdgeStrength;

    /* for future use */
    uint32_t    aReservedData[20];

    void setDefault();
} RKPQ_SR_CFG;

/* Scaler configuration */
typedef struct _RKPQ_ZME_CFG
{
    // M4_BOOL_DESC("bEnableSR", "1")
    bool        bEnableZME;

    // M4_BOOL_DESC("bEnableDeringing", "1")
    bool        bEnableDeringing;

    // M4_ARRAY_MARK_DESC("aVerCoefs", "s16", M4_SIZE(1, 8), M4_RANGE(0, 512), "[-8, -20, 404, 180, -52, 8, 0, 0]", M4_DIGIT(0), M4_DYNAMIC(1), "curve_table")
    int16_t     aVerCoefs[RKPQ_ZME_COEF_LENGTH];
    // M4_ARRAY_MARK_DESC("aHorCoefs", "s16", M4_SIZE(1, 8), M4_RANGE(0, 512), "[4, -8, -20, 400, 180, -56, 12, 0]", M4_DIGIT(0), M4_DYNAMIC(1), "curve_table")
    int16_t     aHorCoefs[RKPQ_ZME_COEF_LENGTH];

    /* for future use */
    uint32_t    aReservedData[23];

    void setDefault();
} RKPQ_ZME_CFG;


/**
 * @Function: rkpq_init()
 * @Descrptn: rkpq_context initialization, create a rkpq_context instance.
 * @Params:
 *      rkpq_context *pContext - the pq handle of context
 *      RKPQ_Init_Params *pInitParam - the API initialize parameters
 * @Return: error code, 0 indicates everything is ok.
 */
int rkpq_init(rkpq_context *pContext, RKPQ_Init_Params *pInitParam);

/**
 * @Function: rkpq_proc()
 * @Descrptn: call a rkpq_context instance to execute.
 * @Params:
 *      rkpq_context context - the pq handle of context
 *      RKPQ_API_Params pProcParam - the API execute parameters
 * @Return: error code, 0 indicates everything is ok.
 */
int rkpq_proc(rkpq_context context, RKPQ_Proc_Params *pProcParam);

/**
 * @Function: rkpq_deinit()
 * @Descrptn: release rkpq_context resource created by rkpq_init().
 * @Params:
 *      rkpq_context context - the pq handle of context
 * @Return: error code, 0 indicates everything is ok.
 */
int rkpq_deinit(rkpq_context context);

/**
 * @Function: rkpq_query()
 * @Descrptn: query the information about image, buffer or others.
 * @Params:
 *      rkpq_context context - the pq handle of context
 *      rkpq_query_cmd cmd - the query command, see rkpq_query_cmd
 *      size_t size - the buffer size of retuned information
 *      void* info - the buffer pointer of retuned information value
 * @Return: error code, 0 indicates everything is ok.
 * @Detail: the detail explanation of the arguments lists below:
 *  |       Query Command               |   Need A Context  |   Return Type     |
 *  | --------------------------------- | ----------------- | ----------------- |
 *  | RKPQ_QUERY_SDK_VERSION            |       no          | rkpq_version_info |
 *  | RKPQ_QUERY_PERF_INFO              |       YES         | rkpq_perf_info    |
 *  | RKPQ_QUERY_IMG_FMT_INPUT_SUPPORT  |       no          | rkpq_imgfmt_info  |
 *  | RKPQ_QUERY_IMG_FMT_OUTPUT_SUPPORT |       no          | rkpq_imgfmt_info  |
 *  | RKPQ_QUERY_IMG_FMT_CHANGE_SUPPORT |       no          | uint32_t          |
 *  | RKPQ_QUERY_IMG_RES_CHANGE_SUPPORT |       no          | uint32_t          |
 *  | RKPQ_QUERY_IMG_COLOR_SPACE_SUPPORT|       no          | rkpq_clrspc_info  |
 *  | RKPQ_QUERY_IMG_BUF_INFO           |       no          | rkpq_imgbuf_info  |
 *  | RKPQ_QUERY_IMG_ALIGNMENT_OCL      |       YES         | uint32_t          |
 *  | RKPQ_QUERY_RKNN_SUPPORT           |       YES         | uint32_t          |
 *  | RKPQ_QUERY_MEAN_LUMA              |       YES         | uint32_t          |
 *  | RKPQ_QUERY_MODULES_SUPPORT        |       YES         | rkpq_module_info  |
 *  | RKPQ_QUERY_3DLUT_AI_TABLE         |       YES         | uint16_t[14739]   |
 */
int rkpq_query(rkpq_context context, rkpq_query_cmd cmd, size_t size, void* info);

/**
 * @Function: rkpq_set_default_cfg()
 * @Descrptn: set the module configurations to default values.
 * @Params:
 *      rkpq_context context - the pq handle of context
 *      RKPQ_API_Params pProcParam - the API execute parameters
 * @Return: error code, 0 indicates everything is ok.
 */
int rkpq_set_default_cfg(RKPQ_Proc_Params *pProcParam);

/**
 * @Function: rkpq_set_loglevel()
 * @Descrptn: set the log level.
 * @Params:
 *      int logLevel - the log level, valid range: [0, 4]
 * @Return: error code, 0 indicates everything is ok.
 */
int rkpq_set_loglevel(int logLevel);


#ifdef __cplusplus
}
#endif


#endif // __RKPQ_API_H_
