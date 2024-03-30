/*
 * Copyright (c) 2019-2021, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 *       copyright notice, this list of conditions and the following
 *       disclaimer in the documentation and/or other materials provided
 *       with the distribution.
 *     * Neither the name of The Linux Foundation nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

#define LOG_TAG "PAL: bt_bundle"
//#define LOG_NDEBUG 0

#include <log/log.h>
#include <errno.h>
#include <stdlib.h>
#include <string.h>
#include "bt_bundle.h"
#include "bt_base.h"
#include <common_enc_dec_api.h>
#include <media_fmt_api.h>
#include <ldac_encoder_api.h>
#include <aac_encoder_api.h>

static int bt_aac_populate_enc_frame_size_ctrl(custom_block_t *blk, uint32_t ctl_type,
                                        uint32_t ctl_value)
{
    struct param_id_enc_frame_size_control_t *param = NULL;

    blk->param_id = PARAM_ID_ENC_FRAME_SIZE_CONTROL;
    blk->payload_sz = sizeof(struct param_id_enc_frame_size_control_t);
    blk->payload = calloc(1, blk->payload_sz);
    if (!blk->payload) {
        blk->payload_sz = 0;
        return -ENOMEM;
    }

    param = (struct param_id_enc_frame_size_control_t*) blk->payload;
    param->frame_size_control_type = ctl_type;
    param->frame_size_control_value = ctl_value;
    return 0;
}


static int bt_aac_populate_bitrate_level_map(custom_block_t *blk,
        struct quality_level_to_bitrate_info *level_map)
{
    struct param_id_aac_bitrate_level_map_payload_t *param = NULL;
    uint32_t tbl_size = level_map->num_levels * sizeof(struct aac_bitrate_level_map_t);

    blk->param_id = PARAM_ID_AAC_BIT_RATE_LEVEL_MAP;
    blk->payload_sz = sizeof(struct param_id_aac_bitrate_level_map_payload_t);
    blk->payload_sz += tbl_size;
    blk->payload = calloc(1, blk->payload_sz);
    if (!blk->payload) {
        blk->payload_sz = 0;
        return -ENOMEM;
    }

    if (sizeof(aac_bitrate_level_map_t) != sizeof(bit_rate_level_map_t)) {
        ALOGE("%s: level map size mismatches", __func__);
        return -EINVAL;
    }

    param = (struct param_id_aac_bitrate_level_map_payload_t *)blk->payload;
    param->num_levels = level_map->num_levels;
    aac_bitrate_level_map_t *tbl_ptr = (aac_bitrate_level_map_t *)((int8_t *)blk->payload +
        sizeof(struct param_id_aac_bitrate_level_map_payload_t));

    memcpy(tbl_ptr, &(level_map->bit_rate_level_map[0]), tbl_size);
    return 0;
}

static int aac_pack_enc_config(bt_codec_t *codec, void *src, void **dst)
{
    audio_aac_encoder_config_t *aac_bt_cfg = NULL;
    bt_enc_payload_t *enc_payload = NULL;
    struct aac_enc_cfg_t *aac_enc_cfg = NULL;
    int num_blks = 5, i = 0, ret = 0;
    custom_block_t *blk[5] = {NULL};

    ALOGV("%s", __func__);

    if ((src == NULL) || (dst == NULL)) {
        ALOGE("%s: invalid input parameters", __func__);
        return -EINVAL;
    }

    aac_bt_cfg = (audio_aac_encoder_config_t *)src;

    enc_payload = (bt_enc_payload_t *)calloc(1, sizeof(bt_enc_payload_t) +
                   num_blks * sizeof(custom_block_t *));
    if (enc_payload == NULL) {
        ALOGE("%s: fail to allocate memory", __func__);
        return -ENOMEM;
    }
    enc_payload->bit_format     = aac_bt_cfg->bits_per_sample;
    enc_payload->sample_rate    = aac_bt_cfg->sampling_rate;
    enc_payload->channel_count  = aac_bt_cfg->channels;
    enc_payload->is_abr_enabled = aac_bt_cfg->abr_ctl_ptr && aac_bt_cfg->abr_ctl_ptr->is_abr_enabled;
    enc_payload->num_blks       = num_blks;

    for (i = 0; i < num_blks; i++) {
        blk[i] = (custom_block_t *)calloc(1, sizeof(custom_block_t));
        if (!blk[i]) {
            ret = -ENOMEM;
            goto free_payload;
        }
    }

    ALOGD("%s: AAC ABR mode is %d", __func__, enc_payload->is_abr_enabled);
    if (!enc_payload->is_abr_enabled) {
        /* populate payload for PARAM_ID_REAL_MODULE_ID */
        ret = bt_base_populate_real_module_id(blk[0], MODULE_ID_AAC_ENC);
    } else {
        /* populate payload for PARAM_ID_AAC_BIT_RATE_LEVEL_MAP */
        ret = bt_aac_populate_bitrate_level_map(blk[0],
                &(aac_bt_cfg->abr_ctl_ptr->level_to_bitrate_map));
    }
    if (ret)
        goto free_payload;

    /* populate payload for PARAM_ID_ENC_BITRATE */
    ret = bt_base_populate_enc_bitrate(blk[1], aac_bt_cfg->bitrate);
    if (ret)
        goto free_payload;

    /* populate payload for PARAM_ID_ENCODER_OUTPUT_CONFIG */
    aac_enc_cfg = (struct aac_enc_cfg_t *)calloc(1, sizeof(struct aac_enc_cfg_t));
    if (aac_enc_cfg == NULL) {
        ALOGE("%s: fail to allocate memory", __func__);
        ret = -ENOMEM;
        goto free_payload;
    }

    switch (aac_bt_cfg->enc_mode) {
        case 0:
            aac_enc_cfg->enc_mode = AAC_AOT_LC;
            break;
        case 2:
            aac_enc_cfg->enc_mode = AAC_AOT_PS;
            break;
        case 1:
        default:
            aac_enc_cfg->enc_mode = AAC_AOT_SBR;
            break;
    }
    ALOGD("%s: AAC encoder mode is %d", __func__, aac_enc_cfg->enc_mode);
    aac_enc_cfg->aac_fmt_flag = aac_bt_cfg->format_flag;
    ret = bt_base_populate_enc_output_cfg(blk[2], MEDIA_FMT_ID_AAC,
                              aac_enc_cfg, sizeof(struct aac_enc_cfg_t));
    free(aac_enc_cfg);
    if (ret)
        goto free_payload;

    /* populate payload for PARAM_ID_ENC_FRAME_SIZE_CONTROL */
    ret = bt_aac_populate_enc_frame_size_ctrl(blk[3], aac_bt_cfg->frame_ctl.ctl_type,
                                              aac_bt_cfg->frame_ctl.ctl_value);
    if (ret)
        goto free_payload;

    /* populate payload for PARAM_ID_ENC_FRAME_SIZE_CONTROL specially for bit rate mode */
    if (aac_bt_cfg->frame_ctl_ptr != NULL) {
        ALOGD("%s: AAC VBR ctl_type %d, ctl_value %d", __func__,
                aac_bt_cfg->frame_ctl_ptr->ctl_type, aac_bt_cfg->frame_ctl_ptr->ctl_value);
        ret = bt_aac_populate_enc_frame_size_ctrl(blk[4],
                                                  aac_bt_cfg->frame_ctl_ptr->ctl_type,
                                                  aac_bt_cfg->frame_ctl_ptr->ctl_value);
    } else {
        ret = bt_aac_populate_enc_frame_size_ctrl(blk[4], BIT_RATE_MODE, 0 /* VBR DISABLED */);
    }
    if (ret)
        goto free_payload;

    enc_payload->blocks[0] = blk[0];
    enc_payload->blocks[1] = blk[1];
    enc_payload->blocks[2] = blk[2];
    enc_payload->blocks[3] = blk[3];
    enc_payload->blocks[4] = blk[4];

    *dst = enc_payload;
    codec->payload = enc_payload;

    return ret;
free_payload:
    for (i = 0; i < num_blks; i++) {
        if (blk[i]) {
            if (blk[i]->payload)
                free(blk[i]->payload);
            free(blk[i]);
        }
    }
    if (enc_payload)
        free(enc_payload);
    return ret;
}

static int aac_pack_dec_config(bt_codec_t *codec __unused, void *src __unused, void **dst __unused) {
    return 0;
}

static int sbc_pack_enc_config(bt_codec_t *codec, void *src, void **dst)
{
    audio_sbc_encoder_config_t *sbc_bt_cfg = NULL;
    bt_enc_payload_t *enc_payload = NULL;
    struct sbc_enc_cfg_t *sbc_enc_cfg = NULL;
    int ret = 0, num_blks = 3, i = 0;
    custom_block_t *blk[3] = {NULL};

    ALOGV("%s", __func__);
    if ((src == NULL) || (dst == NULL)) {
        ALOGE("%s: invalid input parameters", __func__);
        return -EINVAL;
    }

    sbc_bt_cfg = (audio_sbc_encoder_config_t *)src;

    enc_payload = (bt_enc_payload_t *)calloc(1, sizeof(bt_enc_payload_t) +
                   num_blks * sizeof(custom_block_t *));
    if (enc_payload == NULL) {
        ALOGE("%s: fail to allocate memory", __func__);
        return -ENOMEM;
    }
    enc_payload->bit_format  = sbc_bt_cfg->bits_per_sample;
    enc_payload->sample_rate = sbc_bt_cfg->sampling_rate;
    enc_payload->num_blks    = num_blks;

    for (i = 0; i < num_blks; i++) {
        blk[i] = (custom_block_t *)calloc(1, sizeof(custom_block_t));
        if (!blk[i]) {
            ret = -ENOMEM;
            goto free_payload;
        }
    }

    /* populate payload for PARAM_ID_REAL_MODULE_ID */
    ret = bt_base_populate_real_module_id(blk[0], MODULE_ID_SBC_ENC);
    if (ret)
        goto free_payload;

    /* populate payload for PARAM_ID_ENC_BITRATE */
    ret = bt_base_populate_enc_bitrate(blk[1], sbc_bt_cfg->bitrate);
    if (ret)
        goto free_payload;

    /* populate payload for PARAM_ID_ENCODER_OUTPUT_CONFIG */
    sbc_enc_cfg = (struct sbc_enc_cfg_t *)calloc(1, sizeof(struct sbc_enc_cfg_t));
    if (sbc_enc_cfg == NULL) {
        ALOGE("%s: fail to allocate memory", __func__);
        ret = -ENOMEM;
        goto free_payload;
    }

    sbc_enc_cfg->num_subbands = sbc_bt_cfg->subband;
    sbc_enc_cfg->blk_len      = sbc_bt_cfg->blk_len;
    switch (sbc_bt_cfg->channels) {
        case 0:
            sbc_enc_cfg->channel_mode = MEDIA_FMT_SBC_CHANNEL_MODE_MONO;
            enc_payload->channel_count = 1;
            break;
        case 1:
            sbc_enc_cfg->channel_mode = MEDIA_FMT_SBC_CHANNEL_MODE_DUAL_MONO;
            enc_payload->channel_count = 2;
            break;
        case 3:
            sbc_enc_cfg->channel_mode = MEDIA_FMT_SBC_CHANNEL_MODE_JOINT_STEREO;
            enc_payload->channel_count = 2;
            break;
        case 2:
        default:
            sbc_enc_cfg->channel_mode = MEDIA_FMT_SBC_CHANNEL_MODE_STEREO;
            enc_payload->channel_count = 2;
            break;
    }
    if (sbc_bt_cfg->alloc)
        sbc_enc_cfg->alloc_method = MEDIA_FMT_SBC_ALLOCATION_METHOD_LOUDNESS;
    else
        sbc_enc_cfg->alloc_method = MEDIA_FMT_SBC_ALLOCATION_METHOD_SNR;

    ret = bt_base_populate_enc_output_cfg(blk[2], MEDIA_FMT_ID_SBC,
                              sbc_enc_cfg, sizeof(struct sbc_enc_cfg_t));
    free(sbc_enc_cfg);
    if (ret)
        goto free_payload;

    enc_payload->blocks[0] = blk[0];
    enc_payload->blocks[1] = blk[1];
    enc_payload->blocks[2] = blk[2];
    *dst = enc_payload;
    codec->payload = enc_payload;

    return ret;
free_payload:
    for (i = 0; i < num_blks; i++) {
        if (blk[i]) {
            if (blk[i]->payload)
                free(blk[i]->payload);
            free(blk[i]);
        }
    }
    if (enc_payload)
        free(enc_payload);
    return ret;
}

static int sbc_pack_dec_config(bt_codec_t *codec __unused, void *src __unused, void **dst __unused)
{
    return 0;
}

static int celt_pack_enc_config(bt_codec_t *codec, void *src, void **dst)
{
    audio_celt_encoder_config_t *celt_bt_cfg = NULL;
    bt_enc_payload_t *enc_payload = NULL;
    struct celt_enc_cfg_t *celt_enc_cfg = NULL;
    int ret = 0, num_blks = 3, i = 0;
    custom_block_t *blk[3] = {NULL};

    ALOGV("%s", __func__);
    if ((src == NULL) || (dst == NULL)) {
        ALOGE("%s: invalid input parameters", __func__);
        return -EINVAL;
    }

    celt_bt_cfg = (audio_celt_encoder_config_t *)src;

    enc_payload = (bt_enc_payload_t *)calloc(1, sizeof(bt_enc_payload_t) +
                   num_blks * sizeof(custom_block_t *));
    if (enc_payload == NULL) {
        ALOGE("%s: fail to allocate memory", __func__);
        return -ENOMEM;
    }
    enc_payload->bit_format    = celt_bt_cfg->bits_per_sample;
    enc_payload->sample_rate   = celt_bt_cfg->sampling_rate;
    enc_payload->channel_count = celt_bt_cfg->channels;
    enc_payload->num_blks      = num_blks;

    for (i = 0; i < num_blks; i++) {
        blk[i] = (custom_block_t *)calloc(1, sizeof(custom_block_t));
        if (!blk[i]) {
            ret = -ENOMEM;
            goto free_payload;
        }
    }

    /* populate payload for PARAM_ID_REAL_MODULE_ID */
    ret = bt_base_populate_real_module_id(blk[0], MODULE_ID_CELT_ENC);
    if (ret)
        goto free_payload;

    /* populate payload for PARAM_ID_ENC_BITRATE */
    ret = bt_base_populate_enc_bitrate(blk[1], celt_bt_cfg->bitrate);
    if (ret)
        goto free_payload;

    /* populate payload for PARAM_ID_ENCODER_OUTPUT_CONFIG */
    celt_enc_cfg = (struct celt_enc_cfg_t *)calloc(1, sizeof(struct celt_enc_cfg_t));
    if (celt_enc_cfg == NULL) {
        ALOGE("%s: fail to allocate memory", __func__);
        ret = -ENOMEM;
        goto free_payload;
    }

    celt_enc_cfg->frame_size = celt_bt_cfg->frame_size;
    celt_enc_cfg->complexity = celt_bt_cfg->complexity;
    celt_enc_cfg->prediction_mode = celt_bt_cfg->prediction_mode;
    celt_enc_cfg->vbr_flag = celt_bt_cfg->vbr_flag;
    ret = bt_base_populate_enc_output_cfg(blk[2], MEDIA_FMT_ID_CELT,
                              celt_enc_cfg, sizeof(struct celt_enc_cfg_t));
    free(celt_enc_cfg);
    if (ret)
        goto free_payload;

    enc_payload->blocks[0] = blk[0];
    enc_payload->blocks[1] = blk[1];
    enc_payload->blocks[2] = blk[2];
    *dst = enc_payload;
    codec->payload = enc_payload;

    return ret;
free_payload:
    for (i = 0; i < num_blks; i++) {
        if (blk[i]) {
            if (blk[i]->payload)
                free(blk[i]->payload);
            free(blk[i]);
        }
    }
    if (enc_payload)
        free(enc_payload);
    return ret;
}

static int bt_ldac_populate_bitrate_level_map(custom_block_t *blk,
        struct quality_level_to_bitrate_info *level_map)
{
    struct param_id_ldac_bitrate_level_map_payload_t *param = NULL;
    uint32_t tbl_size = level_map->num_levels * sizeof(struct bitrate_level_map_t);

    blk->param_id = PARAM_ID_LDAC_BIT_RATE_LEVEL_MAP;
    blk->payload_sz = sizeof(struct param_id_ldac_bitrate_level_map_payload_t);
    blk->payload_sz += tbl_size;
    blk->payload = calloc(1, blk->payload_sz);
    if (!blk->payload) {
        blk->payload_sz = 0;
        return -ENOMEM;
    }

    if (sizeof(bitrate_level_map_t) != sizeof(bit_rate_level_map_t)) {
        ALOGE("%s: level map size mismatches", __func__);
        return -EINVAL;
    }

    param = (struct param_id_ldac_bitrate_level_map_payload_t *)blk->payload;
    param->num_levels = level_map->num_levels;
    bitrate_level_map_t *tbl_ptr = (bitrate_level_map_t *)((int8_t *)blk->payload +
        sizeof(struct param_id_ldac_bitrate_level_map_payload_t));

    memcpy(tbl_ptr, &(level_map->bit_rate_level_map[0]), tbl_size);
    return 0;
}

static int ldac_pack_enc_config(bt_codec_t *codec, void *src, void **dst)
{
    audio_ldac_encoder_config_t *ldac_bt_cfg = NULL;
    bt_enc_payload_t *enc_payload = NULL;
    int ret = 0, num_blks = 2, i = 0;
    custom_block_t *blk[2] = {NULL};

    ALOGV("%s", __func__);
    if ((src == NULL) || (dst == NULL)) {
        ALOGE("%s: invalid input parameters", __func__);
        return -EINVAL;
    }

    ldac_bt_cfg = (audio_ldac_encoder_config_t *)src;

    enc_payload = (bt_enc_payload_t *)calloc(1, sizeof(bt_enc_payload_t) +
                   num_blks * sizeof(custom_block_t *));
    if (enc_payload == NULL) {
        ALOGE("%s: fail to allocate memory", __func__);
        return -ENOMEM;
    }
    enc_payload->bit_format     = ldac_bt_cfg->bits_per_sample;
    enc_payload->sample_rate    = ldac_bt_cfg->sampling_rate;
    enc_payload->is_abr_enabled = ldac_bt_cfg->is_abr_enabled;
    enc_payload->num_blks       = num_blks;

    switch (ldac_bt_cfg->channel_mode) {
        case 4:
            enc_payload->channel_count = 1;
            break;
        case 2:
        case 1:
        default:
            enc_payload->channel_count = 2;
            break;
    }

    for (i = 0; i < num_blks; i++) {
        blk[i] = (custom_block_t *)calloc(1, sizeof(custom_block_t));
        if (!blk[i]) {
            ret = -ENOMEM;
            goto free_payload;
        }
    }

    /* populate payload for PARAM_ID_ENC_BITRATE */
    ret = bt_base_populate_enc_bitrate(blk[0], ldac_bt_cfg->bit_rate);
    if (ret)
        goto free_payload;

    /* populate payload for PARAM_ID_LDAC_BIT_RATE_LEVEL_MAP */
    ret = bt_ldac_populate_bitrate_level_map(blk[1], &(ldac_bt_cfg->level_to_bitrate_map));
    if (ret)
        goto free_payload;

    enc_payload->blocks[0] = blk[0];
    enc_payload->blocks[1] = blk[1];
    *dst = enc_payload;
    codec->payload = enc_payload;

    return ret;
free_payload:
    for (i = 0; i < num_blks; i++) {
        if (blk[i]) {
            if (blk[i]->payload)
                free(blk[i]->payload);
            free(blk[i]);
        }
    }
    if (enc_payload)
        free(enc_payload);
    return ret;
}

static int bt_bundle_populate_payload(bt_codec_t *codec, void *src, void **dst)
{
    config_fn_t config_fn = NULL;

    if (codec->payload)
        free(codec->payload);

    switch (codec->codecFmt) {
        case CODEC_TYPE_AAC:
            config_fn = ((codec->direction == ENC) ? &aac_pack_enc_config :
                                                     &aac_pack_dec_config);
            break;
        case CODEC_TYPE_SBC:
            config_fn = ((codec->direction == ENC) ? &sbc_pack_enc_config :
                                                     &sbc_pack_dec_config);
            break;
        case CODEC_TYPE_CELT:
            config_fn = ((codec->direction == ENC) ? &celt_pack_enc_config :
                                                     NULL);
            break;
        case CODEC_TYPE_LDAC:
            config_fn = ((codec->direction == ENC) ? &ldac_pack_enc_config :
                                                     NULL);
            break;
        default:
            ALOGD("%s unsupported codecFmt %d\n", __func__, codec->codecFmt);
    }

    if (config_fn != NULL) {
        return config_fn(codec, src, dst);
    }

    return -EINVAL;
}

static uint64_t bt_bundle_get_decoder_latency(bt_codec_t *codec,
                                       uint32_t slatency __unused)
{
    uint32_t latency = 0;

    switch (codec->codecFmt) {
        case CODEC_TYPE_SBC:
            latency = DEFAULT_SINK_LATENCY_SBC;
            break;
        case CODEC_TYPE_AAC:
            latency = DEFAULT_SINK_LATENCY_AAC;
            break;
        default:
            latency = 200;
            ALOGD("No valid decoder defined, setting latency to %dms", latency);
            break;
    }
    return (uint64_t)latency;
}

static uint64_t bt_bundle_get_encoder_latency(bt_codec_t *codec,
                                       uint32_t slatency)
{
    uint32_t latency = 0;

    switch (codec->codecFmt) {
        case CODEC_TYPE_AAC:
            latency = ENCODER_LATENCY_AAC;
            latency += (slatency <= 0) ? DEFAULT_SINK_LATENCY_AAC : slatency;
            break;
        case CODEC_TYPE_SBC:
            latency = ENCODER_LATENCY_SBC;
            latency += (slatency <= 0) ? DEFAULT_SINK_LATENCY_SBC : slatency;
            break;
        case CODEC_TYPE_CELT:
            latency = ENCODER_LATENCY_CELT;
            latency += (slatency <= 0) ? DEFAULT_SINK_LATENCY_CELT : slatency;
            break;
        case CODEC_TYPE_LDAC:
            latency = ENCODER_LATENCY_LDAC;
            latency += (slatency <= 0) ? DEFAULT_SINK_LATENCY_LDAC : slatency;
            break;
        default:
            latency = 200;
            break;
    }
    ALOGV("%s: codecFmt %u, direction %d, slatency %u, latency %u",
            __func__, codec->codecFmt, codec->direction, slatency, latency);
    return latency;
}

static uint64_t bt_bundle_get_codec_latency(bt_codec_t *codec, uint32_t slatency)
{

    if (codec->direction == ENC)
        return bt_bundle_get_encoder_latency(codec, slatency);
    else
        return bt_bundle_get_decoder_latency(codec, slatency);
}

int bt_bundle_query_num_codecs(bt_codec_t *codec __unused) {
    ALOGV("%s", __func__);
    return NUM_CODEC;
}

void bt_bundle_close(bt_codec_t *codec)
{
    bt_enc_payload_t *payload = codec->payload;
    int num_blks, i;
    custom_block_t *blk;

    if (payload) {
        num_blks = payload->num_blks;
        for (i = 0; i < num_blks; i++) {
            blk = payload->blocks[i];
            if (blk) {
                if (blk->payload)
                    free(blk->payload);
                free(blk);
                blk = NULL;
            }
        }
        free(payload);
    }
    free(codec);
}

__attribute__ ((visibility ("default")))
int plugin_open(bt_codec_t **codec, uint32_t codecFmt, codec_type direction)
{
    bt_codec_t *bt_codec = NULL;


    bt_codec = calloc(1, sizeof(bt_codec_t));
    if (!bt_codec) {
        ALOGE("%s: Memory allocation failed\n", __func__);
        return -ENOMEM;
    }

    bt_codec->codecFmt = codecFmt;
    bt_codec->direction = direction;
    bt_codec->plugin_populate_payload  = bt_bundle_populate_payload;
    bt_codec->plugin_get_codec_latency = bt_bundle_get_codec_latency;
    bt_codec->plugin_query_num_codecs  = bt_bundle_query_num_codecs;
    bt_codec->close_plugin  = bt_bundle_close;

    *codec = bt_codec;
    return 0;
}
