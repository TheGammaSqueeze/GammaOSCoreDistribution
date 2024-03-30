/*
** Copyright (c) 2019, The Linux Foundation. All rights reserved.
**
** Redistribution and use in source and binary forms, with or without
** modification, are permitted provided that the following conditions are
** met:
**   * Redistributions of source code must retain the above copyright
**     notice, this list of conditions and the following disclaimer.
**   * Redistributions in binary form must reproduce the above
**     copyright notice, this list of conditions and the following
**     disclaimer in the documentation and/or other materials provided
**     with the distribution.
**   * Neither the name of The Linux Foundation nor the names of its
**     contributors may be used to endorse or promote products derived
**     from this software without specific prior written permission.
**
** THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
** WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
** MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
** ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
** BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
** CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
** SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
** BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
** WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
** OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
** IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
**/

#define LOG_TAG "agm_client_wrapper"

#include <binder/ProcessState.h>
#include <binder/IServiceManager.h>
#include <binder/IInterface.h>
#include <binder/IBinder.h>
#include <log/log.h>
#include <utils/Log.h>
#include <qti-agm-service/ipc_interface.h>
#include <agm/agm_api.h>
#include <qti-agm-service/agm_death_notifier.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <fcntl.h>
#include <sys/shm.h>
#include <sys/stat.h>
#include <utils/RefBase.h>
#include <binder/IPCThreadState.h>
#include <pthread.h>
#include "utils.h"
#ifdef DYNAMIC_LOG_ENABLED
#include <log_xml_parser.h>
#define LOG_MASK AGM_MOD_FILE_AGM_CLIENT_WRAPPER
#include <log_utils.h>
#endif

using namespace android;

sp<server_death_notifier> Server_death_notifier = NULL;
sp<IAgmService> agm_client = NULL;
bool agm_server_died = false;


android::sp<IAgmService> get_agm_server()
{
    if (agm_client == 0) {
        AGM_LOGD("Agm client initialized\n");
        android:: sp<android::IBinder> binder =
                   android::defaultServiceManager()->getService(android::String16("AgmService"));
        agm_client = android::interface_cast<IAgmService>(binder);
        AGM_LOGV("got service handle\n");
        if (Server_death_notifier == NULL) {
            Server_death_notifier = new server_death_notifier();
            binder->linkToDeath(Server_death_notifier);
            AGM_LOGV("%s : server linked to death \n", __func__);
        }
    }
    return agm_client;
}

server_death_notifier::server_death_notifier()
{
    AGM_LOGV("%s\n", __func__);
    sp<ProcessState> proc(ProcessState::self());
    proc->startThreadPool();
}

void server_death_notifier::binderDied(const wp<IBinder>& who __unused)
{
    agm_server_died = true;
    AGM_LOGE("Agm server died !! and I am notified\n");
    //add further functionality
}

int agm_register_service_crash_callback(agm_service_crash_cb cb, uint64_t cookie)
{
    return 0;
}

int agm_aif_set_media_config(uint32_t audio_intf,
                             struct agm_media_config *media_config)
{
    if (!agm_server_died) {
        android::sp<IAgmService> agm_client = get_agm_server();
        return agm_client->ipc_agm_audio_intf_set_media_config(audio_intf,
                                                            media_config);
    }
    AGM_LOGE("%s: agm service is not running\n", __func__);
    return -EAGAIN;
}

int agm_session_set_config(uint64_t handle,
                struct agm_session_config *session_config,
                struct agm_media_config *media_config,
                struct agm_buffer_config *buffer_config)
{
    if (!agm_server_died) {
        android::sp<IAgmService> agm_client = get_agm_server();
        return agm_client->ipc_agm_session_set_config(handle, session_config,
                                                media_config, buffer_config);
    }
    AGM_LOGE("%s: agm service is not running\n", __func__);
    return -EAGAIN;
}

int agm_init(){
     /*agm_init in IPC happens in context of the server*/
      return 0;
}

int agm_deinit(){
     /*agm_deinit in IPC happens in context of the server*/
      return 0;
}

int agm_aif_set_metadata(uint32_t audio_intf, uint32_t size, uint8_t *metadata){
    if(!agm_server_died)
    {
        android::sp<IAgmService> agm_client = get_agm_server();
        return agm_client->ipc_agm_audio_intf_set_metadata(audio_intf, size,
                                                                  metadata);
    }
    AGM_LOGE("%s: agm service is not running\n", __func__);
    return -EAGAIN;
}

int agm_session_set_metadata(uint32_t session_id, uint32_t size,
                                             uint8_t *metadata){
    if(!agm_server_died)
    {
        android::sp<IAgmService> agm_client = get_agm_server();
        return agm_client->ipc_agm_session_set_metadata(session_id, size,
                                                               metadata);
    }
    AGM_LOGE("%s: agm service is not running\n", __func__);
    return -EAGAIN;
}

int agm_session_aif_set_metadata(uint32_t session_id, uint32_t audio_intf,
                                        uint32_t size, uint8_t *metadata){
    if(!agm_server_died)
    {
        android::sp<IAgmService> agm_client = get_agm_server();
        return agm_client->ipc_agm_session_audio_inf_set_metadata(session_id,
                                                 audio_intf, size, metadata);
    }
    AGM_LOGE("%s: agm service is not running\n", __func__);
    return -EAGAIN;
}

int agm_session_close(uint64_t handle)
{
    if (!agm_server_died) {
        android::sp<IAgmService> agm_client = get_agm_server();
        return agm_client->ipc_agm_session_close(handle);
    }
    AGM_LOGE("%s: agm service is not running\n", __func__);
    return -EAGAIN;
}

int agm_session_prepare(uint64_t handle)
{
    if (!agm_server_died) {
        android::sp<IAgmService> agm_client = get_agm_server();
        return agm_client->ipc_agm_session_prepare(handle);
    }
    AGM_LOGE("%s: agm service is not running\n", __func__);
    return -EAGAIN;
}

int agm_session_start(uint64_t handle)
{
    if (!agm_server_died) {
        android::sp<IAgmService> agm_client = get_agm_server();
        return agm_client->ipc_agm_session_start(handle);
    }
    AGM_LOGE("%s: agm service is not running\n", __func__);
    return -EAGAIN;
}

int agm_session_stop(uint64_t handle)
{
    if (!agm_server_died) {
        android::sp<IAgmService> agm_client = get_agm_server();
        return agm_client->ipc_agm_session_stop(handle);
    }
    AGM_LOGE("%s: agm service is not running\n", __func__);
    return -EAGAIN;
}

int agm_session_pause(uint64_t handle)
{
    if (!agm_server_died) {
        android::sp<IAgmService> agm_client = get_agm_server();
        return agm_client->ipc_agm_session_pause(handle);
    }
    AGM_LOGE("%s: agm service is not running\n", __func__);
    return -EAGAIN;
}

int agm_session_resume(uint64_t handle)
{
    if (!agm_server_died) {
        android::sp<IAgmService> agm_client = get_agm_server();
        return agm_client->ipc_agm_session_resume(handle);
    }
    AGM_LOGE("%s: agm service is not running\n", __func__);
    return -EAGAIN;
}

int agm_session_open(uint32_t session_id,
                     enum agm_session_mode sess_mode,
                     uint64_t *handle)
{
    if (!agm_server_died) {
        android::sp<IAgmService> agm_client = get_agm_server();
        return agm_client->ipc_agm_session_open(session_id, sess_mode, handle);
    }
    AGM_LOGE("%s: agm service is not running\n", __func__);
    return -EAGAIN;
}

int  agm_session_aif_connect(uint32_t session_id, uint32_t audio_intf,
                                                           bool state)
{
    if (!agm_server_died) {
        android::sp<IAgmService> agm_client = get_agm_server();
        return agm_client->ipc_agm_session_audio_inf_connect(session_id,
                                                     audio_intf, state);
    }
    AGM_LOGE("%s: agm service is not running\n", __func__);
    return -EAGAIN;
}

int agm_session_read(uint64_t handle, void *buf, size_t *byte_count)
{

    if (!agm_server_died) {
        android::sp<IAgmService> agm_client = get_agm_server();
        if (!handle)
           return -EINVAL;

        return agm_client->ipc_agm_session_read(handle, buf, byte_count);
    }
    AGM_LOGE("%s: agm service is not running\n", __func__);
    return -EAGAIN;
}

int agm_session_write(uint64_t handle, void *buf, size_t *byte_count)
{

    if (!agm_server_died) {
        android::sp<IAgmService> agm_client = get_agm_server();
        if (!handle)
           return -EINVAL;

        return agm_client->ipc_agm_session_write(handle, buf, byte_count);
    }
    AGM_LOGE("%s: agm service is not running\n", __func__);
    return -EAGAIN;
}


int agm_session_set_loopback(uint32_t capture_session_id,
                uint32_t playback_session_id, bool state)
{
    if (!agm_server_died) {
        android::sp<IAgmService> agm_client = get_agm_server();
        return agm_client->ipc_agm_session_set_loopback(capture_session_id,
                                               playback_session_id, state);
    }
    AGM_LOGE("%s: agm service is not running\n", __func__);
    return -EAGAIN;
}

size_t agm_get_hw_processed_buff_cnt(uint64_t handle, enum direction dir)
{
    if (!agm_server_died) {
        android::sp<IAgmService> agm_client = get_agm_server();
        return agm_client->ipc_agm_get_hw_processed_buff_cnt(handle, dir);
    }
    AGM_LOGE("%s: agm service is not running\n", __func__);
    return -EAGAIN;
}

int agm_get_aif_info_list(struct aif_info *aif_list, size_t *num_aif_info)
{
    if (!agm_server_died) {
        android::sp<IAgmService> agm_client = get_agm_server();
        return agm_client->ipc_agm_get_aif_info_list(aif_list, num_aif_info);
    }
    AGM_LOGE("%s: agm service is not running\n", __func__);
    return -EAGAIN;
}

int agm_session_aif_get_tag_module_info(uint32_t session_id, uint32_t aif_id,
                                                 void *payload, size_t *size)
{
    if (!agm_server_died) {
        android::sp<IAgmService> agm_client = get_agm_server();
        return agm_client->ipc_agm_session_aif_get_tag_module_info(session_id,
                                                       aif_id, payload, size);
    }
    AGM_LOGE("%s: agm service is not running\n", __func__);
    return -EAGAIN;
}

int agm_aif_set_params(uint32_t aif_id,void *payload, size_t size)
{
    if (!agm_server_died) {
        android::sp<IAgmService> agm_client = get_agm_server();
        return agm_client->ipc_agm_aif_set_params(aif_id,
                                                          payload, size);
    }
    AGM_LOGE("%s: agm service is not running\n", __func__);
    return -EAGAIN;
}

int agm_session_aif_set_params(uint32_t session_id, uint32_t aif_id,
                                         void *payload, size_t size)
{
    if (!agm_server_died) {
        android::sp<IAgmService> agm_client = get_agm_server();
        return agm_client->ipc_agm_session_aif_set_params(session_id, aif_id,
                                                              payload, size);
    }
    AGM_LOGE("%s: agm service is not running\n", __func__);
    return -EAGAIN;
}

int agm_session_set_params(uint32_t session_id, void *payload, size_t size)
{
    if (!agm_server_died) {
        android::sp<IAgmService> agm_client = get_agm_server();
        return agm_client->ipc_agm_session_set_params(session_id, payload,
                                                                    size);
    }
    AGM_LOGE("%s: agm service is not running\n", __func__);
    return -EAGAIN;
}

int agm_set_params_with_tag(uint32_t session_id, uint32_t aif_id,
                               struct agm_tag_config *tag_config)
{
    if (!agm_server_died) {
        android::sp<IAgmService> agm_client = get_agm_server();
        return agm_client->ipc_agm_set_params_with_tag(session_id, aif_id,
                                                              tag_config);
    }
    AGM_LOGE("%s: agm service is not running\n", __func__);
    return -EAGAIN;
}

int agm_session_register_for_events(uint32_t session_id,
                  struct agm_event_reg_cfg *evt_reg_cfg)
{
    if (!agm_server_died) {
        android::sp<IAgmService> agm_client = get_agm_server();
        return agm_client->ipc_agm_session_register_for_events(session_id,
                                                             evt_reg_cfg);
    }
    AGM_LOGE("%s: agm service is not running\n", __func__);
    return -EAGAIN;
}
int agm_session_register_cb(uint32_t session_id, agm_event_cb cb,
                        enum event_type event, void *client_data)
{
    if (!agm_server_died) {
        android::sp<IAgmService> agm_client = get_agm_server();
        return agm_client->ipc_agm_session_register_cb(session_id, cb, event,
                                                                client_data);
    }
    AGM_LOGE("%s: agm service is not running\n", __func__);
    return -EAGAIN;
}

int agm_session_set_ec_ref(uint32_t capture_session_id, uint32_t aif_id,
                                                             bool state)
{
    if (!agm_server_died) {
        android::sp<IAgmService> agm_client = get_agm_server();
        return agm_client->ipc_agm_session_set_ec_ref(capture_session_id,
                                                          aif_id, state);
    }
    AGM_LOGE("%s: agm service is not running\n", __func__);
    return -EAGAIN;
}

int agm_session_aif_set_cal(uint32_t session_id, uint32_t audio_intf,
                                   struct agm_cal_config *cal_config)
{
    if (!agm_server_died) {
        android::sp<IAgmService> agm_client = get_agm_server();
        return agm_client->ipc_agm_session_aif_set_cal(session_id, audio_intf,
                                                                  cal_config);
    }
    AGM_LOGE("%s: agm service is not running\n", __func__);
    return -EAGAIN;
}

int agm_session_eos(uint64_t handle)
{
    if(!agm_server_died) {
        android::sp<IAgmService> agm_client = get_agm_server();
        return agm_client->ipc_agm_session_eos(handle);
    }
    AGM_LOGE("%s: agm service is not running\n", __func__);
    return -EAGAIN;
}

int agm_get_session_time(uint64_t handle, uint64_t *timestamp)
{
    if(!agm_server_died) {
        android::sp<IAgmService> agm_client = get_agm_server();
        return agm_client->ipc_agm_get_session_time(handle, timestamp);
    }
    AGM_LOGE("%s: agm service is not running\n", __func__);
    return -EAGAIN;
}

int agm_session_get_params(uint32_t session_id, void *payload, size_t size)
{
    if(!agm_server_died)
    {
        android::sp<IAgmService> agm_client = get_agm_server();
        return agm_client->ipc_agm_session_get_params(session_id, payload, size);
    }
    ALOGE("%s: agm service is not running\n", __func__);
    return -EAGAIN;
}

int agm_get_buffer_timestamp(uint32_t session_id, uint64_t *timestamp)
{
    if(!agm_server_died) {
        android::sp<IAgmService> agm_client = get_agm_server();
        return agm_client->ipc_agm_get_buffer_timestamp(session_id, timestamp);
    }
    ALOGE("%s: agm service is not running\n", __func__);
    return -EAGAIN;
}

int agm_set_gapless_session_metadata(uint64_t handle,
                         enum agm_gapless_silence_type type,
                         uint32_t silence)
{
    if(!agm_server_died) {
        android::sp<IAgmService> agm_client = get_agm_server();
        return agm_client->ipc_agm_set_gapless_session_metadata(handle, type,
                                                                silence);
    }
    ALOGE("%s: agm service is not running\n", __func__);
    return -EAGAIN;
}

int agm_session_get_buf_info(uint32_t session_id, struct agm_buf_info *buf_info,
                             uint32_t flag)
{
    if(!agm_server_died) {
        android::sp<IAgmService> agm_client = get_agm_server();
        return agm_client->ipc_agm_session_get_buf_info(session_id, buf_info,
                                                        flag);
    }
    ALOGE("%s: agm service is not running\n", __func__);
    return -EAGAIN;
}
