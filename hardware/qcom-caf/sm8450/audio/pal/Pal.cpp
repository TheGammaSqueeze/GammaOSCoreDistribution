/*
 * Copyright (c) 2019-2021, The Linux Foundation. All rights reserved.
 * Copyright (c) 2022-2023 Qualcomm Innovation Center, Inc. All rights reserved.
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

#define LOG_TAG "PAL: API"

#include <set>
#include <unistd.h>
#include <stdlib.h>
#include <PalApi.h>
#include "Stream.h"
#include "Device.h"
#include "ResourceManager.h"
#include "PalCommon.h"
class Stream;

/*
 * enable_gcov - Enable gcov for pal
 *
 * Prerequisites
 *   Should be call from CATF
 */

/*void enable_gcov()
{
    __gcov_flush();
}*/

static void notify_concurrent_stream(pal_stream_type_t type,
                                     pal_stream_direction_t dir,
                                     bool active)
{
    std::shared_ptr<ResourceManager> rm = ResourceManager::getInstance();

    if (!rm) {
        PAL_ERR(LOG_TAG,"Resource manager unavailable");
        return;
    }

    PAL_DBG(LOG_TAG, "Notify concurrent stream type %d, direction %d, active %d",
        type, dir, active);
    rm->ConcurrentStreamStatus(type, dir, active);
}

/*
 * pal_init - Initialize PAL
 *
 * Return 0 on success or error code otherwise
 *
 * Prerequisites
 *    None.
 */
int32_t pal_init(void)
{
    PAL_DBG(LOG_TAG, "Enter.");
    int32_t ret = 0;
    std::shared_ptr<ResourceManager> ri = NULL;
    try {
        ri = ResourceManager::getInstance();
    } catch (const std::exception& e) {
        PAL_ERR(LOG_TAG, "pal init failed: %s", e.what());
        ret = -EINVAL;
        goto exit;
    }
    ret = ri->initSndMonitor();
    if (ret != 0) {
        PAL_ERR(LOG_TAG, "snd monitor init failed");
        goto exit;
    }

    ri->init();

    ret = ri->initContextManager();
    if (ret != 0) {
        PAL_ERR(LOG_TAG, "ContextManager init failed, error:%d", ret);
        goto exit;
    }

exit:
    PAL_DBG(LOG_TAG, "Exit. exit status : %d ", ret);
    return ret;
}

/*
 * pal_deinit - De-initialize PAL
 *
 * Prerequisites
 *    PAL must be initialized.
 */
void pal_deinit(void)
{
    PAL_DBG(LOG_TAG, "Enter.");

    std::shared_ptr<ResourceManager> ri = NULL;

    try {
        ri = ResourceManager::getInstance();
    } catch (const std::exception& e) {
        PAL_ERR(LOG_TAG, "ResourceManager::getInstance() failed: %s", e.what());
    }
    ri->deInitContextManager();

    ResourceManager::deinit();
    PAL_DBG(LOG_TAG, "Exit.");
    return;
}


int32_t pal_stream_open(struct pal_stream_attributes *attributes,
                        uint32_t no_of_devices, struct pal_device *devices,
                        uint32_t no_of_modifiers, struct modifier_kv *modifiers,
                        pal_stream_callback cb, uint64_t cookie,
                        pal_stream_handle_t **stream_handle)
{
    uint64_t *stream = NULL;
    Stream *s = NULL;
    int status;
    struct pal_stream_attributes sAttr;
    std::shared_ptr<ResourceManager> rm = NULL;

    rm = ResourceManager::getInstance();
    if (!rm) {
        PAL_ERR(LOG_TAG, "Invalid resource manager");
        status = -EINVAL;
        return status;
    }

    if (!attributes) {
        status = -EINVAL;
        PAL_ERR(LOG_TAG, "Invalid input parameters status %d", status);
        return status;
    }

    PAL_INFO(LOG_TAG, "Enter, stream type:%d", attributes->type);

    try {
        s = Stream::create(attributes, devices, no_of_devices, modifiers,
                           no_of_modifiers);
    } catch (const std::exception& e) {
        status = -EINVAL;
        PAL_ERR(LOG_TAG, "Stream create failed: %s", e.what());
        Stream::handleStreamException(attributes, cb, cookie);
        goto exit;
    }
    if (!s) {
        status = -EINVAL;
        PAL_ERR(LOG_TAG, "stream creation failed status %d", status);
        goto exit;
    }
    status = s->open();
    if (0 != status) {
        PAL_ERR(LOG_TAG, "pal_stream_open failed with status %d", status);
        if (s->close() != 0) {
            PAL_ERR(LOG_TAG, "stream closed failed.");
        }
        delete s;
        goto exit;
    }

    s->getStreamAttributes(&sAttr);
    notify_concurrent_stream(sAttr.type, sAttr.direction, true);

    if (cb)
       s->registerCallBack(cb, cookie);

    rm->initStreamUserCounter(s);
    stream = reinterpret_cast<uint64_t *>(s);
    *stream_handle = stream;
exit:
    PAL_INFO(LOG_TAG, "Exit. Value of stream_handle %pK, status %d", stream, status);
    return status;
}

int32_t pal_stream_close(pal_stream_handle_t *stream_handle)
{
    Stream *s = NULL;
    int status;
    struct pal_stream_attributes sAttr;
    std::shared_ptr<ResourceManager> rm = NULL;
    if (!stream_handle) {
        status = -EINVAL;
        PAL_ERR(LOG_TAG, "Invalid stream handle status %d", status);
        return status;
    }
    PAL_INFO(LOG_TAG, "Enter. Stream handle :%pK", stream_handle);

    rm = ResourceManager::getInstance();
    if (!rm) {
        PAL_ERR(LOG_TAG, "Invalid resource manager");
        status = -EINVAL;
        return status;
    }

    rm->lockValidStreamMutex();
    if (!rm->isActiveStream(stream_handle)) {
        status = -EINVAL;
        rm->unlockValidStreamMutex();
        return status;
    }
    rm->unlockValidStreamMutex();

    s = reinterpret_cast<Stream *>(stream_handle);
    s->setCachedState(STREAM_IDLE);
    status = s->close();

    if (rm->deactivateStreamUserCounter(s)) {
        PAL_ERR(LOG_TAG, "stream is being closed by another client");
        return 0;
    }

    if (0 != status) {
        PAL_ERR(LOG_TAG, "stream closed failed. status %d", status);
        goto exit;
    }
exit:
    s->getStreamAttributes(&sAttr);
    notify_concurrent_stream(sAttr.type, sAttr.direction, false);
    delete s;
    rm->eraseStreamUserCounter(s);
    PAL_INFO(LOG_TAG, "Exit. status %d", status);
    return status;
}

int32_t pal_stream_start(pal_stream_handle_t *stream_handle)
{
    Stream *s = NULL;
    std::shared_ptr<ResourceManager> rm = NULL;
    int status;
    if (!stream_handle) {
        status = -EINVAL;
        PAL_ERR(LOG_TAG, "Invalid stream handle status %d", status);
        return status;
    }
    PAL_INFO(LOG_TAG, "Enter. Stream handle %pK", stream_handle);

    rm = ResourceManager::getInstance();
    if (!rm) {
        PAL_ERR(LOG_TAG, "Invalid resource manager");
        status = -EINVAL;
        goto exit;
    }

    rm->lockValidStreamMutex();
    if (!rm->isActiveStream(stream_handle)) {
        rm->unlockValidStreamMutex();
        status = -EINVAL;
        goto exit;
    }

    s = reinterpret_cast<Stream *>(stream_handle);
    status = rm->increaseStreamUserCounter(s);
    if (0 != status) {
        rm->unlockValidStreamMutex();
        PAL_ERR(LOG_TAG, "failed to increase stream user count");
        goto exit;
    }
    rm->unlockValidStreamMutex();

    status = s->start();

    rm->lockValidStreamMutex();
    rm->decreaseStreamUserCounter(s);
    rm->unlockValidStreamMutex();

    if (0 != status) {
        PAL_ERR(LOG_TAG, "stream start failed. status %d", status);
        goto exit;
    }

exit:
    PAL_INFO(LOG_TAG, "Exit. status %d", status);
    return status;
}

int32_t pal_stream_stop(pal_stream_handle_t *stream_handle)
{
    Stream *s = NULL;
    std::shared_ptr<ResourceManager> rm = NULL;
    int status;

    if (!stream_handle) {
        status = -EINVAL;
        PAL_ERR(LOG_TAG, "Invalid stream handle status %d", status);
        return status;
    }
    PAL_INFO(LOG_TAG, "Enter. Stream handle :%pK", stream_handle);
    rm = ResourceManager::getInstance();
    if (!rm) {
        PAL_ERR(LOG_TAG, "Invalid resource manager");
        status = -EINVAL;
        goto exit;
    }

    rm->lockValidStreamMutex();
    if (!rm->isActiveStream(stream_handle)) {
        rm->unlockValidStreamMutex();
        status = -EINVAL;
        goto exit;
    }

    s = reinterpret_cast<Stream *>(stream_handle);
    status = rm->increaseStreamUserCounter(s);
    if (0 != status) {
        rm->unlockValidStreamMutex();
        PAL_ERR(LOG_TAG, "failed to increase stream user count");
        goto exit;
    }
    rm->unlockValidStreamMutex();
    s->setCachedState(STREAM_STOPPED);
    status = s->stop();

    rm->lockValidStreamMutex();
    rm->decreaseStreamUserCounter(s);
    rm->unlockValidStreamMutex();

    if (0 != status) {
        PAL_ERR(LOG_TAG, "stream stop failed. status : %d", status);
        goto exit;
    }

exit:
    PAL_INFO(LOG_TAG, "Exit. status %d", status);
    return status;
}

ssize_t pal_stream_write(pal_stream_handle_t *stream_handle, struct pal_buffer *buf)
{
    Stream *s = NULL;
    int status;
    std::shared_ptr<ResourceManager> rm = NULL;

    rm = ResourceManager::getInstance();
    if (!rm) {
        PAL_ERR(LOG_TAG, "Invalid resource manager");
        status = -EINVAL;
        return status;
    }
    rm->lockValidStreamMutex();
    if (!stream_handle || !rm->isActiveStream(stream_handle) || !buf) {
        rm->unlockValidStreamMutex();
        status = -EINVAL;
        PAL_ERR(LOG_TAG, "Invalid input parameters status %d", status);
        return status;
    }

    PAL_VERBOSE(LOG_TAG, "Enter. Stream handle :%pK", stream_handle);
    s =  reinterpret_cast<Stream *>(stream_handle);
    status = rm->increaseStreamUserCounter(s);
    if (0 != status) {
        rm->unlockValidStreamMutex();
        PAL_ERR(LOG_TAG, "failed to increase stream user count");
        return status;
    }
    rm->unlockValidStreamMutex();

    status = s->write(buf);
    if (status < 0) {
        PAL_ERR(LOG_TAG, "stream write failed status %d", status);
    }

    rm->lockValidStreamMutex();
    rm->decreaseStreamUserCounter(s);
    rm->unlockValidStreamMutex();

    PAL_VERBOSE(LOG_TAG, "Exit. status %d", status);
    return status;
}

ssize_t pal_stream_read(pal_stream_handle_t *stream_handle, struct pal_buffer *buf)
{
    Stream *s = NULL;
    int status;
    std::shared_ptr<ResourceManager> rm = NULL;

    rm = ResourceManager::getInstance();
    if (!rm) {
        PAL_ERR(LOG_TAG, "Invalid resource manager");
        status = -EINVAL;
        return status;
    }
    rm->lockValidStreamMutex();
    if (!stream_handle || !rm->isActiveStream(stream_handle) || !buf) {
        rm->unlockValidStreamMutex();
        status = -EINVAL;
        PAL_ERR(LOG_TAG, "Invalid input parameters status %d", status);
        return status;
    }

    PAL_VERBOSE(LOG_TAG, "Enter. Stream handle :%pK", stream_handle);
    s =  reinterpret_cast<Stream *>(stream_handle);
    status = rm->increaseStreamUserCounter(s);
    if (0 != status) {
        rm->unlockValidStreamMutex();
        PAL_ERR(LOG_TAG, "failed to increase stream user count");
        return status;
    }
    rm->unlockValidStreamMutex();

    status = s->read(buf);
    if (status < 0) {
        PAL_ERR(LOG_TAG, "stream read failed status %d", status);
    }

    rm->lockValidStreamMutex();
    rm->decreaseStreamUserCounter(s);
    rm->unlockValidStreamMutex();
    PAL_VERBOSE(LOG_TAG, "Exit. status %d", status);
    return status;
}

int32_t pal_stream_get_param(pal_stream_handle_t *stream_handle,
                             uint32_t param_id, pal_param_payload **param_payload)
{
    Stream *s = NULL;
    int status;
    std::shared_ptr<ResourceManager> rm = NULL;

    rm = ResourceManager::getInstance();
    if (!rm) {
        status = -EINVAL;
        PAL_ERR(LOG_TAG, "Invalid resource manager");
        return status;
    }

    rm->lockValidStreamMutex();
    if (!stream_handle || !rm->isActiveStream(stream_handle)) {
        rm->unlockValidStreamMutex();
        status = -EINVAL;
        PAL_ERR(LOG_TAG,  "Invalid input parameters status %d", status);
        return status;
    }

    PAL_DBG(LOG_TAG, "Enter. Stream handle :%pK", stream_handle);
    s =  reinterpret_cast<Stream *>(stream_handle);
    status = rm->increaseStreamUserCounter(s);
    if (0 != status) {
        rm->unlockValidStreamMutex();
        PAL_ERR(LOG_TAG, "failed to increase stream user count");
        return status;
    }
    rm->unlockValidStreamMutex();

    status = s->getParameters(param_id, (void **)param_payload);
    if (0 != status) {
        PAL_ERR(LOG_TAG, "get parameters failed status %d param_id %u", status, param_id);
    }

    rm->lockValidStreamMutex();
    rm->decreaseStreamUserCounter(s);
    rm->unlockValidStreamMutex();
    PAL_DBG(LOG_TAG, "Exit. status %d", status);
    return status;
}

int32_t pal_stream_set_param(pal_stream_handle_t *stream_handle, uint32_t param_id,
                             pal_param_payload *param_payload)
{
    Stream *s = NULL;
    int status;
    std::shared_ptr<ResourceManager> rm = NULL;

    rm = ResourceManager::getInstance();
    if (!rm) {
        status = -EINVAL;
        PAL_ERR(LOG_TAG, "Invalid resource manager");
        return status;
    }

    rm->lockValidStreamMutex();
    if (!stream_handle || !rm->isActiveStream(stream_handle)) {
        rm->unlockValidStreamMutex();
        status = -EINVAL;
        PAL_ERR(LOG_TAG,  "Invalid stream handle, status %d", status);
        return status;
    }

    PAL_DBG(LOG_TAG, "Enter. Stream handle :%pK param_id %d", stream_handle,
            param_id);
    s =  reinterpret_cast<Stream *>(stream_handle);
    status = rm->increaseStreamUserCounter(s);
    if (0 != status) {
        rm->unlockValidStreamMutex();
        PAL_ERR(LOG_TAG, "failed to increase stream user count");
        return status;
    }
    rm->unlockValidStreamMutex();

    status = s->setParameters(param_id, (void *)param_payload);

    rm->lockValidStreamMutex();
    rm->decreaseStreamUserCounter(s);
    rm->unlockValidStreamMutex();

    if (0 != status) {
        PAL_ERR(LOG_TAG, "set parameters failed status %d param_id %u", status, param_id);
        return status;
    }

    if (param_id == PAL_PARAM_ID_STOP_BUFFERING) {
        PAL_DBG(LOG_TAG, "Buffering stopped, handle deferred LPI<->NLPI switch");
        rm->handleDeferredSwitch();
    }

    PAL_DBG(LOG_TAG, "Exit. status %d", status);
    return status;
}

int32_t pal_stream_set_volume(pal_stream_handle_t *stream_handle,
                              struct pal_volume_data *volume)
{
    Stream *s = NULL;
    int status;
    std::shared_ptr<ResourceManager> rm = NULL;
    rm = ResourceManager::getInstance();
    if (!rm) {
        PAL_ERR(LOG_TAG, "Invalid resource manager");
        status = -EINVAL;
        return status;
    }

    if (!stream_handle || !volume) {
        status = -EINVAL;
        PAL_ERR(LOG_TAG,"Invalid input parameters status %d", status);
        return status;
    }
    PAL_DBG(LOG_TAG, "Enter. Stream handle :%pK", stream_handle);

    rm->lockValidStreamMutex();
    if (!rm->isActiveStream(stream_handle)) {
        rm->unlockValidStreamMutex();
        status = -EINVAL;
        return status;
    }

    s =  reinterpret_cast<Stream *>(stream_handle);
    status = rm->increaseStreamUserCounter(s);
    if (0 != status) {
        rm->unlockValidStreamMutex();
        PAL_ERR(LOG_TAG, "failed to increase stream user count");
        return status;
    }
    rm->unlockValidStreamMutex();

    status = s->setVolume(volume);

    rm->lockValidStreamMutex();
    rm->decreaseStreamUserCounter(s);
    rm->unlockValidStreamMutex();

    if (0 != status) {
        PAL_ERR(LOG_TAG, "setVolume failed with status %d", status);
        return status;
    }
    PAL_DBG(LOG_TAG, "Exit. status %d", status);
    return status;
}

int32_t pal_stream_set_mute(pal_stream_handle_t *stream_handle, bool state)
{
    Stream *s = NULL;
    std::shared_ptr<ResourceManager> rm = NULL;
    int status = 0;

    if (!stream_handle) {
        status = -EINVAL;
        PAL_ERR(LOG_TAG, "Invalid stream handle status %d", status);
        return status;
    }

    rm = ResourceManager::getInstance();
    if (!rm) {
        status = -EINVAL;
        PAL_ERR(LOG_TAG, "Invalid resource manager");
        return status;
    }

    PAL_DBG(LOG_TAG, "Enter. Stream handle :%pK", stream_handle);

    rm->lockValidStreamMutex();
    if (!rm->isActiveStream(stream_handle)) {
        rm->unlockValidStreamMutex();
        status = -EINVAL;
        goto exit;
    }

    s =  reinterpret_cast<Stream *>(stream_handle);
    status = rm->increaseStreamUserCounter(s);
    if (0 != status) {
        rm->unlockValidStreamMutex();
        PAL_ERR(LOG_TAG, "failed to increase stream user count");
        goto exit;
    }
    rm->unlockValidStreamMutex();
    status = s->mute(state);

    rm->lockValidStreamMutex();
    rm->decreaseStreamUserCounter(s);
    rm->unlockValidStreamMutex();

    if (0 != status) {
        PAL_ERR(LOG_TAG, "mute failed with status %d", status);
        return status;
    }

exit:
    PAL_DBG(LOG_TAG, "Exit. status %d", status);
    return status;
}

int32_t pal_stream_pause(pal_stream_handle_t *stream_handle)
{
    Stream *s = NULL;
    int status;
    std::shared_ptr<ResourceManager> rm = NULL;

    rm = ResourceManager::getInstance();
    if (!rm) {
        PAL_ERR(LOG_TAG, "Invalid resource manager");
        status = -EINVAL;
        return status;
    }

    rm->lockValidStreamMutex();
    if (!stream_handle || !rm->isActiveStream(stream_handle)) {
        rm->unlockValidStreamMutex();
        status = -EINVAL;
        PAL_ERR(LOG_TAG, "Invalid stream handle status %d", status);
        return status;
    }

    PAL_DBG(LOG_TAG, "Enter. Stream handle :%pK", stream_handle);
    s =  reinterpret_cast<Stream *>(stream_handle);
    status = rm->increaseStreamUserCounter(s);
    if (0 != status) {
        rm->unlockValidStreamMutex();
        PAL_ERR(LOG_TAG, "failed to increase stream user count");
        return status;
    }
    rm->unlockValidStreamMutex();

    status = s->pause();
    if (0 != status) {
        PAL_ERR(LOG_TAG, "pal_stream_pause failed with status %d", status);
    }
    rm->lockValidStreamMutex();
    rm->decreaseStreamUserCounter(s);
    rm->unlockValidStreamMutex();
    PAL_DBG(LOG_TAG, "Exit. status %d", status);
    return status;
}

int32_t pal_stream_resume(pal_stream_handle_t *stream_handle)
{
    Stream *s = NULL;
    int status;
    std::shared_ptr<ResourceManager> rm = NULL;

    rm = ResourceManager::getInstance();
    if (!rm) {
        PAL_ERR(LOG_TAG, "Invalid resource manager");
        status = -EINVAL;
        return status;
    }

    rm->lockValidStreamMutex();
    if (!stream_handle || !rm->isActiveStream(stream_handle)) {
        rm->unlockValidStreamMutex();
        status = -EINVAL;
        PAL_ERR(LOG_TAG, "Invalid stream handle status %d", status);
        return status;
    }

    PAL_DBG(LOG_TAG, "Enter. Stream handle :%pK", stream_handle);
    s =  reinterpret_cast<Stream *>(stream_handle);
    status = rm->increaseStreamUserCounter(s);
    if (0 != status) {
        rm->unlockValidStreamMutex();
        PAL_ERR(LOG_TAG, "failed to increase stream user count");
        return status;
    }
    rm->unlockValidStreamMutex();

    status = s->resume();
    if (0 != status) {
        PAL_ERR(LOG_TAG, "resume failed with status %d", status);
    }
    rm->lockValidStreamMutex();
    rm->decreaseStreamUserCounter(s);
    rm->unlockValidStreamMutex();
    PAL_DBG(LOG_TAG, "Exit. status %d", status);
    return status;
}

int32_t pal_stream_drain(pal_stream_handle_t *stream_handle, pal_drain_type_t type)
{
    Stream *s = NULL;
    int status;
    std::shared_ptr<ResourceManager> rm = NULL;

    if (!stream_handle) {
        status = -EINVAL;
        PAL_ERR(LOG_TAG, "Invalid stream handle status %d", status);
        return status;
    }

    PAL_DBG(LOG_TAG, "Enter. Stream handle :%pK", stream_handle);

    rm = ResourceManager::getInstance();
    if (!rm) {
        PAL_ERR(LOG_TAG, "Invalid resource manager");
        status = -EINVAL;
        goto exit;
    }

    rm->lockValidStreamMutex();
    if (!rm->isActiveStream(stream_handle)) {
        rm->unlockValidStreamMutex();
        status = -EINVAL;
        goto exit;
    }

    s =  reinterpret_cast<Stream *>(stream_handle);
    status = rm->increaseStreamUserCounter(s);
    if (0 != status) {
        rm->unlockValidStreamMutex();
        PAL_ERR(LOG_TAG, "failed to increase stream user count");
        goto exit;
    }
    rm->unlockValidStreamMutex();

    status = s->drain(type);

    rm->lockValidStreamMutex();
    rm->decreaseStreamUserCounter(s);
    rm->unlockValidStreamMutex();

    if (0 != status) {
        PAL_ERR(LOG_TAG, "drain failed with status %d", status);
        return status;
    }
exit:
    PAL_DBG(LOG_TAG, "Exit. status %d", status);
    return status;
}

int32_t pal_stream_flush(pal_stream_handle_t *stream_handle)
{
    Stream *s = NULL;
    int status;
    std::shared_ptr<ResourceManager> rm = NULL;

    rm = ResourceManager::getInstance();
    if (!rm) {
        PAL_ERR(LOG_TAG, "Invalid resource manager");
        status = -EINVAL;
        return status;
    }

    rm->lockValidStreamMutex();
    if (!stream_handle || !rm->isActiveStream(stream_handle)) {
        rm->unlockValidStreamMutex();
        status = -EINVAL;
        PAL_ERR(LOG_TAG, "Invalid stream handle status %d", status);
        return status;
    }

    PAL_DBG(LOG_TAG, "Enter. Stream handle :%pK", stream_handle);
    s =  reinterpret_cast<Stream *>(stream_handle);
    status = rm->increaseStreamUserCounter(s);
    if (0 != status) {
        rm->unlockValidStreamMutex();
        PAL_ERR(LOG_TAG, "failed to increase stream user count");
        return status;
    }
    rm->unlockValidStreamMutex();

    status = s->flush();
    if (0 != status) {
        PAL_ERR(LOG_TAG, "flush failed with status %d", status);
    }

    rm->lockValidStreamMutex();
    rm->decreaseStreamUserCounter(s);
    rm->unlockValidStreamMutex();
    PAL_DBG(LOG_TAG, "Exit. status %d", status);
    return status;
}

int32_t pal_stream_suspend(pal_stream_handle_t *stream_handle)
{
    Stream *s = NULL;
    int status;
    std::shared_ptr<ResourceManager> rm = NULL;

    rm = ResourceManager::getInstance();
    if (!rm) {
        PAL_ERR(LOG_TAG, "Invalid resource manager");
        status = -EINVAL;
        return status;
    }

    rm->lockValidStreamMutex();
    if (!stream_handle || !rm->isActiveStream(stream_handle)) {
        rm->unlockValidStreamMutex();
        status = -EINVAL;
        PAL_ERR(LOG_TAG, "Invalid stream handle status %d", status);
        return status;
    }

    PAL_DBG(LOG_TAG, "Enter. Stream handle :%pK", stream_handle);
    s =  reinterpret_cast<Stream *>(stream_handle);
    status = rm->increaseStreamUserCounter(s);
    if (0 != status) {
        rm->unlockValidStreamMutex();
        PAL_ERR(LOG_TAG, "failed to increase stream user count");
        return status;
    }
    rm->unlockValidStreamMutex();

    status = s->suspend();
    if (0 != status) {
        PAL_ERR(LOG_TAG, "suspend failed with status %d", status);
    }

    rm->lockValidStreamMutex();
    rm->decreaseStreamUserCounter(s);
    rm->unlockValidStreamMutex();
    PAL_DBG(LOG_TAG, "Exit. status %d", status);
    return status;
}

int32_t pal_stream_set_buffer_size (pal_stream_handle_t *stream_handle,
                                    pal_buffer_config *in_buffer_cfg,
                                    pal_buffer_config *out_buffer_cfg)
{
    Stream *s = NULL;
    int status;
    std::shared_ptr<ResourceManager> rm = NULL;

    rm = ResourceManager::getInstance();
    if (!rm) {
        PAL_ERR(LOG_TAG, "Invalid resource manager");
        status = -EINVAL;
        return status;
    }

    rm->lockValidStreamMutex();
    if (!stream_handle || !rm->isActiveStream(stream_handle)) {
        rm->unlockValidStreamMutex();
        status = -EINVAL;
        PAL_ERR(LOG_TAG, "Invalid stream handle status %d", status);
        return status;
    }

    PAL_DBG(LOG_TAG, "Enter. Stream handle :%pK", stream_handle);
    s =  reinterpret_cast<Stream *>(stream_handle);
    status = rm->increaseStreamUserCounter(s);
    if (0 != status) {
        rm->unlockValidStreamMutex();
        PAL_ERR(LOG_TAG, "failed to increase stream user count");
        return status;
    }
    rm->unlockValidStreamMutex();

    status = s->setBufInfo(in_buffer_cfg, out_buffer_cfg);
    if (0 != status) {
        PAL_ERR(LOG_TAG, "pal_stream_set_buffer_size failed with status %d", status);
    }
    rm->lockValidStreamMutex();
    rm->decreaseStreamUserCounter(s);
    rm->unlockValidStreamMutex();
    PAL_DBG(LOG_TAG, "Exit. status %d", status);
    return status;
}

int32_t pal_get_timestamp(pal_stream_handle_t *stream_handle,
                          struct pal_session_time *stime)
{
    Stream *s = NULL;
    int status = -EINVAL;
    std::shared_ptr<ResourceManager> rm = NULL;
    PAL_DBG(LOG_TAG, "Enter. Stream handle :%pK\n", stream_handle);

    rm = ResourceManager::getInstance();
    if (!rm) {
        PAL_ERR(LOG_TAG, "Invalid resource manager");
        return status;
    }

    rm->lockValidStreamMutex();
    if (!stream_handle || !rm->isActiveStream(stream_handle)) {
        rm->unlockValidStreamMutex();
        status = -EINVAL;
        PAL_ERR(LOG_TAG, "Invalid stream handle status %d", status);
        return status;
    }
    s =  reinterpret_cast<Stream *>(stream_handle);
    status = rm->increaseStreamUserCounter(s);
    if (0 != status) {
        rm->unlockValidStreamMutex();
        PAL_ERR(LOG_TAG, "failed to increase stream user count");
        return status;
    }
    rm->unlockValidStreamMutex();
    status = s->getTimestamp(stime);

    rm->lockValidStreamMutex();
    rm->decreaseStreamUserCounter(s);
    rm->unlockValidStreamMutex();

    if (0 != status) {
        PAL_ERR(LOG_TAG, "pal_get_timestamp failed with status %d\n", status);
        return status;
    }

    PAL_VERBOSE(LOG_TAG, "stime->session_time.value_lsw = %u, stime->session_time.value_msw = %u \n", stime->session_time.value_lsw, stime->session_time.value_msw);
    PAL_VERBOSE(LOG_TAG, "stime->absolute_time.value_lsw = %u, stime->absolute_time.value_msw = %u \n", stime->absolute_time.value_lsw, stime->absolute_time.value_msw);
    PAL_VERBOSE(LOG_TAG, "stime->timestamp.value_lsw = %u, stime->timestamp.value_msw = %u \n", stime->timestamp.value_lsw, stime->timestamp.value_msw);

    PAL_DBG(LOG_TAG, "Exit. status %d", status);
    return status;
}

int32_t pal_add_remove_effect(pal_stream_handle_t *stream_handle,
                       pal_audio_effect_t effect, bool enable)
{
    Stream *s = NULL;
    int status = 0;
    std::shared_ptr<ResourceManager> rm = NULL;

    rm = ResourceManager::getInstance();
    if (!rm) {
        PAL_ERR(LOG_TAG, "Invalid resource manager");
        status = -EINVAL;
        return status;
    }

    rm->lockValidStreamMutex();
    if (!stream_handle || !rm->isActiveStream(stream_handle)) {
        rm->unlockValidStreamMutex();
        status = -EINVAL;
        PAL_ERR(LOG_TAG, "Invalid stream handle status %d", status);
        return status;
    }

    PAL_DBG(LOG_TAG, "Enter. Stream handle :%pK", stream_handle);
    s =  reinterpret_cast<Stream *>(stream_handle);
    status = rm->increaseStreamUserCounter(s);
    if (0 != status) {
        rm->unlockValidStreamMutex();
        PAL_ERR(LOG_TAG, "failed to increase stream user count");
        return status;
    }
    rm->unlockValidStreamMutex();

    status = s->addRemoveEffect(effect, enable);
    if (0 != status) {
        PAL_ERR(LOG_TAG, "pal_add_effect failed with status %d", status);
    }

    rm->lockValidStreamMutex();
    rm->decreaseStreamUserCounter(s);
    rm->unlockValidStreamMutex();
    PAL_DBG(LOG_TAG, "Exit. status %d", status);
    return status;

}

int32_t pal_stream_set_device(pal_stream_handle_t *stream_handle,
                           uint32_t no_of_devices, struct pal_device *devices)
{
    int status = -EINVAL;
    Stream *s = NULL;
    std::shared_ptr<ResourceManager> rm = NULL;
    struct pal_stream_attributes sattr;
    struct pal_device_info devinfo = {};
    struct pal_device *pDevices = NULL;
    std::vector <std::shared_ptr<Device>> aDevices;
    std::vector <struct pal_device> palDevices;

    if (!stream_handle) {
        status = -EINVAL;
        PAL_ERR(LOG_TAG, "Invalid stream handle status %d", status);
        return status;
    }

    PAL_INFO(LOG_TAG, "Enter. Stream handle :%pK", stream_handle);

    if (no_of_devices == 0 || !devices) {
        status = -EINVAL;
        PAL_ERR(LOG_TAG, "Invalid device status %d", status);
        return status;
    }

    rm = ResourceManager::getInstance();
    if (!rm) {
        status = -EINVAL;
        PAL_ERR(LOG_TAG, "Invalid resource manager");
        return status;
    }

    rm->lockValidStreamMutex();
    if (!rm->isActiveStream(stream_handle)) {
        rm->unlockValidStreamMutex();
        status = -EINVAL;
        return status;
    }

    /* Choose best device config for this stream */
    /* TODO: Decide whether to update device config or not based on flag */
    s = reinterpret_cast<Stream *>(stream_handle);
    status = rm->increaseStreamUserCounter(s);
    if (0 != status) {
        rm->unlockValidStreamMutex();
        PAL_ERR(LOG_TAG, "failed to increase stream user count");
        return status;
    }
    rm->unlockValidStreamMutex();

    s->getStreamAttributes(&sattr);

    // device switch will be handled in global param setting for SVA
    if (sattr.type == PAL_STREAM_VOICE_UI) {
        PAL_DBG(LOG_TAG,
                "Device switch handles in global param set, skip here");
        goto exit;
    }
    if (sattr.type == PAL_STREAM_VOICE_CALL_RECORD ||
        sattr.type == PAL_STREAM_VOICE_CALL_MUSIC) {
        PAL_DBG(LOG_TAG,
                "Device switch skipped for Incall record/music stream");
        status = 0;
        goto exit;
    }

    s->getAssociatedPalDevices(palDevices);
    s->getAssociatedDevices(aDevices);
    if (!palDevices.empty() && !aDevices.empty()) {
        std::set<pal_device_id_t> activeDevices, curPalDevices;
        std::set<pal_device_id_t> newDevices;
        bool force_switch = s->isA2dpMuted();

        for (auto &dev : aDevices)
            activeDevices.insert((pal_device_id_t)dev->getSndDeviceId());

        for (auto palDev: palDevices) {
            curPalDevices.insert(palDev.id);
            // check if custom key matches for stream associated pal device
            for (int i = 0; i < no_of_devices; i++) {
                if (palDev.id == devices[i].id) {
                    if (strcmp(devices[i].custom_config.custom_key,
                        palDev.custom_config.custom_key) != 0) {
                        PAL_DBG(LOG_TAG, "diff custom key found, force device switch");
                        force_switch = true;
                    }
                    break;
                }
            }
            if (force_switch)
                break;
        }
        if (!force_switch) {
            for (int i = 0; i < no_of_devices; i++) {
                newDevices.insert(devices[i].id);
                if ((devices[i].id == PAL_DEVICE_OUT_BLUETOOTH_A2DP) ||
                    (devices[i].id == PAL_DEVICE_OUT_BLUETOOTH_SCO) ||
                    (devices[i].id == PAL_DEVICE_IN_BLUETOOTH_SCO_HEADSET)) {
                    PAL_DBG(LOG_TAG, "always switch device for bt device");
                    force_switch = true;
                    break;
                }
            }
        }
        if (!force_switch && (activeDevices == newDevices) &&
                             (curPalDevices == newDevices)) {
            status = 0;
            PAL_DBG(LOG_TAG, "devices are same, no need to switch");
            goto exit;
        }
    }

    pDevices = (struct pal_device *) calloc(no_of_devices, sizeof(struct pal_device));

    if (!pDevices) {
        status = -ENOMEM;
        PAL_ERR(LOG_TAG, "Memory alloc failed");
        goto exit;
    }

    ar_mem_cpy(pDevices, no_of_devices * sizeof(struct pal_device),
            devices, no_of_devices * sizeof(struct pal_device));

    for (int i = 0; i < no_of_devices; i++) {
        if (strlen(pDevices[i].custom_config.custom_key)) {
            PAL_DBG(LOG_TAG, "Device has custom key %s",
                              pDevices[i].custom_config.custom_key);
        } else {
            PAL_DBG(LOG_TAG, "Device has no custom key");
            strlcpy(pDevices[i].custom_config.custom_key, "", PAL_MAX_CUSTOM_KEY_SIZE);
        }
        status = rm->getDeviceConfig((struct pal_device *)&pDevices[i], &sattr);
        if (status) {
           PAL_ERR(LOG_TAG, "Failed to get Device config, err: %d", status);
           goto exit;
        }
    }
    // TODO: Check with RM if the same device is being used by other stream with different
    // configuration then update corresponding stream device configuration also based on priority.
    PAL_DBG(LOG_TAG, "Stream handle :%pK no_of_devices %d first_device id %d",
            stream_handle, no_of_devices, pDevices[0].id);

    status = s->switchDevice(s, no_of_devices, pDevices);
    if (0 != status) {
        PAL_ERR(LOG_TAG, "failed with status %d", status);
        goto exit;
    }

exit:
    rm->lockValidStreamMutex();
    rm->decreaseStreamUserCounter(s);
    rm->unlockValidStreamMutex();
    if (pDevices)
        free(pDevices);
    PAL_INFO(LOG_TAG, "Exit. status %d", status);
    return status;
}

int32_t pal_stream_get_tags_with_module_info(pal_stream_handle_t *stream_handle,
                           size_t *size, uint8_t *payload)
{
    int status = 0;
    Stream *s = NULL;
    std::shared_ptr<ResourceManager> rm = NULL;

    rm = ResourceManager::getInstance();
    if (!rm) {
        status = -EINVAL;
        PAL_ERR(LOG_TAG, "Invalid resource manager");
        return status;
    }

    rm->lockValidStreamMutex();
    if (!stream_handle || !rm->isActiveStream(stream_handle)) {
        rm->unlockValidStreamMutex();
        status = -EINVAL;
        PAL_ERR(LOG_TAG, "Invalid stream handle status %d", status);
        return status;
    }

    PAL_DBG(LOG_TAG, "Enter. Stream handle :%pK", stream_handle);

    s =  reinterpret_cast<Stream *>(stream_handle);
    status = rm->increaseStreamUserCounter(s);
    if (0 != status) {
        rm->unlockValidStreamMutex();
        PAL_ERR(LOG_TAG, "failed to increase stream user count");
        return status;
    }
    rm->unlockValidStreamMutex();

    status = s->getTagsWithModuleInfo(size, payload);

    rm->lockValidStreamMutex();
    rm->decreaseStreamUserCounter(s);
    rm->unlockValidStreamMutex();
    PAL_DBG(LOG_TAG, "Exit. Stream handle: %pK, status %d", stream_handle, status);
    return status;
}

int32_t pal_set_param(uint32_t param_id, void *param_payload,
                      size_t payload_size)
{
    PAL_DBG(LOG_TAG, "Enter: param id %d", param_id);
    int status = 0;
    std::shared_ptr<ResourceManager> rm = NULL;

    rm = ResourceManager::getInstance();
    if (rm) {
        status = rm->setParameter(param_id, param_payload, payload_size);
        if (0 != status) {
            PAL_ERR(LOG_TAG, "Failed to set global parameter %u, status %d",
                    param_id, status);
        }
    } else {
        PAL_ERR(LOG_TAG, "Pal has not been initialized yet");
        status = -EINVAL;
    }
    PAL_DBG(LOG_TAG, "Exit, status %d", status);
    return status;
}

int32_t pal_get_param(uint32_t param_id, void **param_payload,
                      size_t *payload_size, void *query)
{
    int status = 0;
    std::shared_ptr<ResourceManager> rm = NULL;

    rm = ResourceManager::getInstance();

    PAL_DBG(LOG_TAG, "Enter:");

    if (rm) {
        status = rm->getParameter(param_id, param_payload, payload_size, query);
        if (0 != status) {
            PAL_ERR(LOG_TAG, "Failed to get global parameter %u, status %d",
                    param_id, status);
        }
    } else {
        PAL_ERR(LOG_TAG, "Pal has not been initialized yet");
        status = -EINVAL;
    }
    PAL_DBG(LOG_TAG, "Exit, status %d", status);
    return status;
}

int32_t pal_stream_get_mmap_position(pal_stream_handle_t *stream_handle,
                              struct pal_mmap_position *position)
{
    Stream *s = NULL;
    int status;
    std::shared_ptr<ResourceManager> rm = NULL;

    rm = ResourceManager::getInstance();
    if (!rm) {
        status = -EINVAL;
        PAL_ERR(LOG_TAG, "Invalid resource manager");
        return status;
    }

    rm->lockValidStreamMutex();
    if (!stream_handle || !rm->isActiveStream(stream_handle)) {
        rm->unlockValidStreamMutex();
        status = -EINVAL;
        PAL_ERR(LOG_TAG, "Invalid stream handle status %d", status);
        return status;
    }

    PAL_DBG(LOG_TAG, "Enter. Stream handle :%pK", stream_handle);
    s =  reinterpret_cast<Stream *>(stream_handle);
    status = rm->increaseStreamUserCounter(s);
    if (0 != status) {
        rm->unlockValidStreamMutex();
        PAL_ERR(LOG_TAG, "failed to increase stream user count");
        return status;
    }
    rm->unlockValidStreamMutex();

    status = s->GetMmapPosition(position);
    if (0 != status) {
        PAL_ERR(LOG_TAG, "pal_stream_get_mmap_position failed with status %d", status);
    }

    rm->lockValidStreamMutex();
    rm->decreaseStreamUserCounter(s);
    rm->unlockValidStreamMutex();
    PAL_DBG(LOG_TAG, "Exit. status %d", status);
    return status;
}

int32_t pal_stream_create_mmap_buffer(pal_stream_handle_t *stream_handle,
                              int32_t min_size_frames,
                              struct pal_mmap_buffer *info)
{
    Stream *s = NULL;
    int status;
    std::shared_ptr<ResourceManager> rm = NULL;

    rm = ResourceManager::getInstance();
    if (!rm) {
        status = -EINVAL;
        PAL_ERR(LOG_TAG, "Invalid resource manager");
        return status;
    }

    rm->lockValidStreamMutex();
    if (!stream_handle || !rm->isActiveStream(stream_handle)) {
        rm->unlockValidStreamMutex();
        status = -EINVAL;
        PAL_ERR(LOG_TAG, "Invalid stream handle status %d", status);
        return status;
    }

    PAL_DBG(LOG_TAG, "Enter. Stream handle :%pK", stream_handle);
    s =  reinterpret_cast<Stream *>(stream_handle);
    status = rm->increaseStreamUserCounter(s);
    if (0 != status) {
        rm->unlockValidStreamMutex();
        PAL_ERR(LOG_TAG, "failed to increase stream user count");
        return status;
    }
    rm->unlockValidStreamMutex();

    status = s->createMmapBuffer(min_size_frames, info);
    if (0 != status) {
        PAL_ERR(LOG_TAG, "pal_stream_create_mmap_buffer failed with status %d", status);
    }

    rm->lockValidStreamMutex();
    rm->decreaseStreamUserCounter(s);
    rm->unlockValidStreamMutex();
    PAL_DBG(LOG_TAG, "Exit. status %d", status);
    return status;
}

int32_t pal_register_global_callback(pal_global_callback cb, uint64_t cookie)
{
    std::shared_ptr<ResourceManager> rm = NULL;

    PAL_DBG(LOG_TAG, "Enter. global callback %pK", cb);
    rm = ResourceManager::getInstance();

    if (cb != NULL) {
        rm->globalCb = cb;
        rm->cookie = cookie;
    }
    PAL_DBG(LOG_TAG, "Exit");
    return 0;
}

int32_t pal_gef_rw_param(uint32_t param_id, void *param_payload,
                      size_t payload_size, pal_device_id_t pal_device_id,
                      pal_stream_type_t pal_stream_type, unsigned int dir)
{
    int status = 0;
    std::shared_ptr<ResourceManager> rm = NULL;

    rm = ResourceManager::getInstance();

    PAL_DBG(LOG_TAG, "Enter.");

    if (rm) {
        if (GEF_PARAM_WRITE == dir) {
            status = rm->setParameter(param_id, param_payload, payload_size,
                                        pal_device_id, pal_stream_type);
            if (0 != status) {
                PAL_ERR(LOG_TAG, "Failed to set global parameter %u, status %d",
                        param_id, status);
            }
        } else {
            status = rm->getParameter(param_id, param_payload, payload_size,
                                        pal_device_id, pal_stream_type);
            if (0 != status) {
                PAL_ERR(LOG_TAG, "Failed to set global parameter %u, status %d",
                        param_id, status);
            }
        }
    } else {
        PAL_ERR(LOG_TAG, "Pal has not been initialized yet");
        status = -EINVAL;
    }
    PAL_DBG(LOG_TAG, "Exit:");

    return status;
}

int32_t pal_gef_rw_param_acdb(uint32_t param_id __unused, void *param_payload,
                      size_t payload_size __unused, pal_device_id_t pal_device_id,
                      pal_stream_type_t pal_stream_type, uint32_t sample_rate,
                      uint32_t instance_id, uint32_t dir, bool is_play )
{
    int status = 0;
    std::shared_ptr<ResourceManager> rm = NULL;
    rm = ResourceManager::getInstance();

    PAL_DBG(LOG_TAG, "Enter.");
    if (rm) {
        status = rm->rwParameterACDB(param_id, param_payload, payload_size,
                                        pal_device_id, pal_stream_type,
                                        sample_rate, instance_id, dir, is_play);
        if (0 != status) {
            PAL_ERR(LOG_TAG, "Failed to rw global parameter %u, status %d",
                        param_id, status);
        }
    } else {
        PAL_ERR(LOG_TAG, "Pal has not been initialized yet");
        status = -EINVAL;
    }
    PAL_DBG(LOG_TAG, "Exit, status %d", status);

    return status;
}
