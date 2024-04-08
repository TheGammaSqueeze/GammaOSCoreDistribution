/*
 * Copyright (C) 2020 The Android Open Source Project
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

#ifndef ROCKIT_DIRECT_RTLIBDEFINE_H
#define ROCKIT_DIRECT_RTLIBDEFINE_H

#include <stdint.h>
#include "rt_error.h"
#include "RockitExtAdec.h"

namespace android {

#define ROCKIT_PLAYER_LIB_NAME          "/system/lib/librockit.so"

#define CREATE_PLAYER_FUNC_NAME         "createRockitPlayer"
#define DESTROY_PLAYER_FUNC_NAME        "destroyRockitPlayer"

#define CREATE_METADATA_FUNC_NAME       "createRockitMetaData"
#define DESTROY_METADATA_FUNC_NAME      "destroyRockitMetaData"

#define CREATE_METARETRIEVER_FUNC_NAME  "createRTMetadataRetriever"
#define DESTROY_METARETRIEVER_FUNC_NAME "destroyRTMetadataRetriever"

#define REGISTER_DECODER_FUNC_NAME      "RockitRegisterDecoder"
#define UNREGISTER_DECODER_FUNC_NAME    "RockitUnRegisterDecoder"

// rockit player
typedef void * createRockitPlayerFunc();
typedef void   destroyRockitPlayerFunc(void **player);

// rockit meta
typedef void * createRockitMetaDataFunc();
typedef void   destroyRockitMetaDataFunc(void **meta);

// rockit meta data retriever
typedef void * createMetaDataRetrieverFunc();
typedef void   destroyMetaDataRetrieverFunc(void **retriever);

// rockit register codec
typedef RT_RET registerDecoderFunc(int32_t *ps32Handle, const RTAdecDecoder *pstDecoder);
typedef RT_RET unRegisterDecoderFunc(int32_t s32Handle);

/**************************************************************
 * NOTE:
 * all define below must keep sync with codes of rockit,
 * or will lead to problems
 **************************************************************/


enum RTTrackType {
    RTTRACK_TYPE_UNKNOWN = -1,  // < Usually treated as AVMEDIA_TYPE_DATA
    RTTRACK_TYPE_VIDEO,
    RTTRACK_TYPE_AUDIO,
    RTTRACK_TYPE_DATA,          // < Opaque data information usually continuous
    RTTRACK_TYPE_SUBTITLE,
    RTTRACK_TYPE_ATTACHMENT,    // < Opaque data information usually sparse

    RTTRACK_TYPE_MEDIA,         // this is not a really type of tracks
                                // it means video,audio,subtitle

    RTTRACK_TYPE_MAX
};

typedef enum _ResVideoIdx {
    RES_VIDEO_ROTATION = 0,
} ResVideoIdx;

typedef enum _ResAudioIdx {
    RES_AUDIO_BITRATE = 0,
    RES_AUDIO_BIT_PER_SAMPLE = 1,
} ResAudioIdx;

typedef struct _RockitTrackInfo {
    int32_t  mCodecType;
    int32_t  mCodecID;
    uint32_t mCodecOriginID;
    int32_t  mIdx;

    /* video track features */
    int32_t  mWidth;
    int32_t  mHeight;
    float    mFrameRate;

    /* audio track features*/
    int64_t  mChannelLayout;
    int32_t  mChannels;
    int32_t  mSampleRate;

    /* subtitle track features*/

    /* language */
    char     lang[16];
    char     mine[16];

    bool     mProbeDisabled;
    /* use reserved first when extend this structure */
    int8_t   mReserved[64];
} RockitTrackInfor;

#define RT_VIDEO_FMT_MASK                   0x000f0000
#define RT_VIDEO_FMT_YUV                    0x00000000
#define RT_VIDEO_FMT_RGB                    0x00010000

typedef enum _RTVideoFormat {
    RT_FMT_YUV420SP        = RT_VIDEO_FMT_YUV,         /* YYYY... UV...            */
    RT_FMT_YUV420SP_10BIT,
    RT_FMT_YUV422SP,                                   /* YYYY... UVUV...          */
    RT_FMT_YUV422SP_10BIT,                             ///< Not part of ABI
    RT_FMT_YUV420P,                                    /* YYYY... UUUU... VVVV     */
    RT_FMT_YUV420SP_VU,                                /* YYYY... VUVUVU...        */
    RT_FMT_YUV422P,                                    /* YYYY... UUUU... VVVV     */
    RT_FMT_YUV422SP_VU,                                /* YYYY... VUVUVU...        */
    RT_FMT_YUV422_YUYV,                                /* YUYVYUYV...              */
    RT_FMT_YUV422_UYVY,                                /* UYVYUYVY...              */
    RT_FMT_YUV400SP,                                   /* YYYY...                  */
    RT_FMT_YUV440SP,                                   /* YYYY... UVUV...          */
    RT_FMT_YUV411SP,                                   /* YYYY... UV...            */
    RT_FMT_YUV444SP,                                   /* YYYY... UVUVUVUV...      */
    RT_FMT_YUV_BUTT,
    RT_FMT_RGB565          = RT_VIDEO_FMT_RGB,         /* 16-bit RGB               */
    RT_FMT_BGR565,                                     /* 16-bit RGB               */
    RT_FMT_RGB555,                                     /* 15-bit RGB               */
    RT_FMT_BGR555,                                     /* 15-bit RGB               */
    RT_FMT_RGB444,                                     /* 12-bit RGB               */
    RT_FMT_BGR444,                                     /* 12-bit RGB               */
    RT_FMT_RGB888,                                     /* 24-bit RGB               */
    RT_FMT_BGR888,                                     /* 24-bit RGB               */
    RT_FMT_RGB101010,                                  /* 30-bit RGB               */
    RT_FMT_BGR101010,                                  /* 30-bit RGB               */
    RT_FMT_ARGB8888,                                   /* 32-bit RGB               */
    RT_FMT_ABGR8888,                                   /* 32-bit RGB               */
    RT_FMT_RGB_BUTT,
    RT_FMT_BUTT            = RT_FMT_RGB_BUTT,
} RTVideoFormat;

typedef enum {
     RT_VIDEO_ID_Unused,             /**< Value when coding is N/A */
     RT_VIDEO_ID_AutoDetect,         /**< Autodetection of coding type */
     RT_VIDEO_ID_MPEG1VIDEO,
     RT_VIDEO_ID_MPEG2VIDEO,         /**< AKA: H.262 */
     RT_VIDEO_ID_H263,               /**< H.263 */
     RT_VIDEO_ID_MPEG4,              /**< MPEG-4 */
     RT_VIDEO_ID_WMV,                /**< Windows Media Video (WMV1,WMV2,WMV3)*/
     RT_VIDEO_ID_RV,                 /**< all versions of Real Video */
     RT_VIDEO_ID_AVC,                /**< H.264/AVC */
     RT_VIDEO_ID_MJPEG,              /**< Motion JPEG */
     RT_VIDEO_ID_VP8,                /**< VP8 */
     RT_VIDEO_ID_VP9,                /**< VP9 */
     RT_VIDEO_ID_HEVC,               /**< ITU H.265/HEVC */
     RT_VIDEO_ID_DolbyVision,        /**< Dolby Vision */
     RT_VIDEO_ID_ImageHEIC,          /**< HEIF image encoded with HEVC */
     RT_VIDEO_ID_JPEG,               /**< JPEG */
     RT_VIDEO_ID_VC1 = 0x01000000,   /**< Windows Media Video (WMV1,WMV2,WMV3)*/
     RT_VIDEO_ID_FLV1,               /**< Sorenson H.263 */
     RT_VIDEO_ID_DIVX3,              /**< DIVX3 */
     RT_VIDEO_ID_VP6,
     RT_VIDEO_ID_AVSPLUS,            /**< AVS+ profile=0x48 */
     RT_VIDEO_ID_AVS,                /**< AVS  profile=0x20 */
     RT_VIDEO_ID_AVS2,               /**< AVS2*/
     RT_VIDEO_ID_AV1,                /**< AV1 */
     /* *< Reserved region for introducing Khronos Standard Extensions */
     RT_VIDEO_ID_KhronosExtensions = 0x2F000000,
     /* *< Reserved region for introducing Vendor Extensions */
     RT_VIDEO_ID_VendorStartUnused = 0x3F000000,
     RT_VIDEO_ID_Max = 0x3FFFFFFF,

     RT_AUDIO_ID_Unused = 0x40000000,  /**< Placeholder value when coding is N/A  */
     RT_AUDIO_ID_AutoDetect,  /**< auto detection of audio format */
     RT_AUDIO_ID_PCM_ALAW,    /** <g711a> */
     RT_AUDIO_ID_PCM_MULAW,   /** <g711u> */
     RT_AUDIO_ID_PCM_S16LE,   /**< Any variant of PCM_S16LE coding */
     RT_AUDIO_ID_PCM_S24LE,   /**< Any variant of PCM_S24LE coding */
     RT_AUDIO_ID_PCM_S32LE,   /**< Any variant of PCM_S32LE coding */
     RT_AUDIO_ID_ADPCM_G722,         /**< Any variant of ADPCM_G722 encoded data */
     RT_AUDIO_ID_ADPCM_G726,         /**< Any variant of ADPCM_G726 encoded data */
     RT_AUDIO_ID_ADPCM_G726LE,       /**< G.726 ADPCM little-endian encoded data*/
     RT_AUDIO_ID_ADPCM_IMA_QT,       /**< Any variant of ADPCM_IMA encoded data */
     RT_AUDIO_ID_AMR_NB,      /**< Any variant of AMR_NB encoded data */
     RT_AUDIO_ID_AMR_WB,      /**< Any variant of AMR_WB encoded data */
     RT_AUDIO_ID_GSMFR,       /**< Any variant of GSM fullrate (i.e. GSM610) */
     RT_AUDIO_ID_GSMEFR,      /**< Any variant of GSM Enhanced Fullrate encoded data*/
     RT_AUDIO_ID_GSMHR,       /**< Any variant of GSM Halfrate encoded data */
     RT_AUDIO_ID_PDCFR,       /**< Any variant of PDC Fullrate encoded data */
     RT_AUDIO_ID_PDCEFR,      /**< Any variant of PDC Enhanced Fullrate encoded data */
     RT_AUDIO_ID_PDCHR,       /**< Any variant of PDC Halfrate encoded data */
     RT_AUDIO_ID_TDMAFR,      /**< Any variant of TDMA Fullrate encoded data (TIA/EIA-136-420) */
     RT_AUDIO_ID_TDMAEFR,     /**< Any variant of TDMA Enhanced Fullrate encoded data (TIA/EIA-136-410) */
     RT_AUDIO_ID_QCELP8,      /**< Any variant of QCELP 8kbps encoded data */
     RT_AUDIO_ID_QCELP13,     /**< Any variant of QCELP 13kbps encoded data */
     RT_AUDIO_ID_EVRC,        /**< Any variant of EVRC encoded data */
     RT_AUDIO_ID_SMV,         /**< Any variant of SMV encoded data */
     RT_AUDIO_ID_G729,        /**< Any variant of G.729 encoded data */
     RT_AUDIO_ID_OPUS,        /**< Any variant of OPUS encoded data */
     RT_AUDIO_ID_AAC,         /**< Any variant of AAC encoded data */
     RT_AUDIO_ID_MP3,         /**< Any variant of MP3 encoded data */
     RT_AUDIO_ID_SBC,         /**< Any variant of SBC encoded data */
     RT_AUDIO_ID_VORBIS,      /**< Any variant of VORBIS encoded data */
     RT_AUDIO_ID_WMA,         /**< Any variant of WMA encoded data */
     RT_AUDIO_ID_RA,          /**< Any variant of RA encoded data */
     RT_AUDIO_ID_MIDI,        /**< Any variant of MIDI encoded data */
     RT_AUDIO_ID_FLAC,        /**< Any variant of FLAC encoded data */
     RT_AUDIO_ID_APE = 0x50000000,
     /**< Reserved region for introducing Khronos Standard Extensions */
     RT_AUDIO_CodingKhronosExtensions = 0x6F000000,
     /**< Reserved region for introducing Vendor Extensions */
     RT_AUDIO_CodingVendorStartUnused = 0x7F000000,
     RT_AUDIO_ID_WMAV1,
     RT_AUDIO_ID_WMAV2,
     RT_AUDIO_ID_WMAPRO,
     RT_AUDIO_ID_WMALOSSLESS,
     RT_AUDIO_ID_MP1,
     RT_AUDIO_ID_MP2,
     /**< add audio bitstream Codec ID define for RT> */
     RT_AUDIO_ID_DTS,
     RT_AUDIO_ID_AC3,
     RT_AUDIO_ID_EAC3,
     RT_AUDIO_ID_DOLBY_TRUEHD,
     RT_AUDIO_ID_MLP,
     RT_AUDIO_ID_DTS_HD,
     RT_AUDIO_CodingMax = 0x7FFFFFFF,

     /* subtitle codecs */
     RT_SUB_ID_Unused = 0x17000,          ///< A dummy ID pointing at the start of subtitle codecs.
     RT_SUB_ID_DVD,
     RT_SUB_ID_DVB,
     RT_SUB_ID_TEXT,  ///< raw UTF-8 text
     RT_SUB_ID_XSUB,
     RT_SUB_ID_SSA,
     RT_SUB_ID_MOV_TEXT,
     RT_SUB_ID_HDMV_PGS,
     RT_SUB_ID_DVB_TELETEXT,
     RT_SUB_ID_SRT,

     RT_SUB_ID_MICRODVD   = 0x17800,
     RT_SUB_ID_EIA_608,
     RT_SUB_ID_JACOSUB,
     RT_SUB_ID_SAMI,
     RT_SUB_ID_REALTEXT,
     RT_SUB_ID_STL,
     RT_SUB_ID_SUBVIEWER1,
     RT_SUB_ID_SUBVIEWER,
     RT_SUB_ID_SUBRIP,
     RT_SUB_ID_WEBVTT,
     RT_SUB_ID_MPL2,
     RT_SUB_ID_VPLAYER,
     RT_SUB_ID_PJS,
     RT_SUB_ID_ASS,
     RT_SUB_ID_HDMV_TEXT,
     RT_SUB_CodingMax
} RTCodecID;

}
#endif // ROCKIT_DIRECT_RTLIBDEFINE_H
