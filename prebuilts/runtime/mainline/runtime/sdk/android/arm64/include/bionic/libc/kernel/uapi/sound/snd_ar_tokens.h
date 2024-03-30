/****************************************************************************
 ****************************************************************************
 ***
 ***   This header was automatically generated from a Linux kernel header
 ***   of the same name, to make information necessary for userspace to
 ***   call into the kernel available to libc.  It contains only constants,
 ***   structures, and macros generated from the original header, and thus,
 ***   contains no copyrightable information.
 ***
 ***   To edit the content of this header, modify the corresponding
 ***   source file (e.g. under external/kernel-headers/original/) then
 ***   run bionic/libc/kernel/tools/update_all.py
 ***
 ***   Any manual change here will be lost the next time this script will
 ***   be run. You've been warned!
 ***
 ****************************************************************************
 ****************************************************************************/
#ifndef __SND_AR_TOKENS_H__
#define __SND_AR_TOKENS_H__
#define APM_SUB_GRAPH_PERF_MODE_LOW_POWER 0x1
#define APM_SUB_GRAPH_PERF_MODE_LOW_LATENCY 0x2
#define APM_SUB_GRAPH_DIRECTION_TX 0x1
#define APM_SUB_GRAPH_DIRECTION_RX 0x2
#define APM_SUB_GRAPH_SID_AUDIO_PLAYBACK 0x1
#define APM_SUB_GRAPH_SID_AUDIO_RECORD 0x2
#define APM_SUB_GRAPH_SID_VOICE_CALL 0x3
#define APM_CONTAINER_CAP_ID_PP 0x1
#define APM_CONTAINER_CAP_ID_CD 0x2
#define APM_CONTAINER_CAP_ID_EP 0x3
#define APM_CONTAINER_CAP_ID_OLC 0x4
#define APM_CONT_GRAPH_POS_STREAM 0x1
#define APM_CONT_GRAPH_POS_PER_STR_PER_DEV 0x2
#define APM_CONT_GRAPH_POS_STR_DEV 0x3
#define APM_CONT_GRAPH_POS_GLOBAL_DEV 0x4
#define APM_PROC_DOMAIN_ID_MDSP 0x1
#define APM_PROC_DOMAIN_ID_ADSP 0x2
#define APM_PROC_DOMAIN_ID_SDSP 0x4
#define APM_PROC_DOMAIN_ID_CDSP 0x5
#define PCM_INTERLEAVED 1
#define PCM_DEINTERLEAVED_PACKED 2
#define PCM_DEINTERLEAVED_UNPACKED 3
#define AR_I2S_WS_SRC_EXTERNAL 0
#define AR_I2S_WS_SRC_INTERNAL 1
enum ar_event_types {
  AR_EVENT_NONE = 0,
  AR_PGA_DAPM_EVENT
};
#define SND_SOC_AR_TPLG_FE_BE_GRAPH_CTL_MIX 256
#define SND_SOC_AR_TPLG_VOL_CTL 257
#define AR_TKN_DAI_INDEX 1
#define AR_TKN_U32_SUB_GRAPH_INSTANCE_ID 2
#define AR_TKN_U32_SUB_GRAPH_PERF_MODE 3
#define AR_TKN_U32_SUB_GRAPH_DIRECTION 4
#define AR_TKN_U32_SUB_GRAPH_SCENARIO_ID 5
#define AR_TKN_U32_CONTAINER_INSTANCE_ID 100
#define AR_TKN_U32_CONTAINER_CAPABILITY_ID 101
#define AR_TKN_U32_CONTAINER_STACK_SIZE 102
#define AR_TKN_U32_CONTAINER_GRAPH_POS 103
#define AR_TKN_U32_CONTAINER_PROC_DOMAIN 104
#define AR_TKN_U32_MODULE_ID 200
#define AR_TKN_U32_MODULE_INSTANCE_ID 201
#define AR_TKN_U32_MODULE_MAX_IP_PORTS 202
#define AR_TKN_U32_MODULE_MAX_OP_PORTS 203
#define AR_TKN_U32_MODULE_IN_PORTS 204
#define AR_TKN_U32_MODULE_OUT_PORTS 205
#define AR_TKN_U32_MODULE_SRC_OP_PORT_ID 206
#define AR_TKN_U32_MODULE_DST_IN_PORT_ID 207
#define AR_TKN_U32_MODULE_SRC_INSTANCE_ID 208
#define AR_TKN_U32_MODULE_DST_INSTANCE_ID 209
#define AR_TKN_U32_MODULE_HW_IF_IDX 250
#define AR_TKN_U32_MODULE_HW_IF_TYPE 251
#define AR_TKN_U32_MODULE_FMT_INTERLEAVE 252
#define AR_TKN_U32_MODULE_FMT_DATA 253
#define AR_TKN_U32_MODULE_FMT_SAMPLE_RATE 254
#define AR_TKN_U32_MODULE_FMT_BIT_DEPTH 255
#define AR_TKN_U32_MODULE_SD_LINE_IDX 256
#define AR_TKN_U32_MODULE_WS_SRC 257
#define AR_TKN_U32_MODULE_FRAME_SZ_FACTOR 258
#define AR_TKN_U32_MODULE_LOG_CODE 259
#define AR_TKN_U32_MODULE_LOG_TAP_POINT_ID 260
#define AR_TKN_U32_MODULE_LOG_MODE 261
#endif
