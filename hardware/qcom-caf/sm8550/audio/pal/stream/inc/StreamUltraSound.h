/*
 * Copyright (c) 2021, The Linux Foundation. All rights reserved.
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
 *
 * Changes from Qualcomm Innovation Center are provided under the following license:
 * Copyright (c) 2022-2023 Qualcomm Innovation Center, Inc. All rights reserved.
 * SPDX-License-Identifier: BSD-3-Clause-Clear
 */

#ifndef STREAMULTRASOUND_H_
#define STREAMULTRASOUND_H_

#include "StreamCommon.h"
#include "ResourceManager.h"
#include "Device.h"
#include "Session.h"

class StreamUltraSound : public StreamCommon
{
public:
    StreamUltraSound(const struct pal_stream_attributes *sattr, struct pal_device *dattr,
                     const uint32_t no_of_devices, const struct modifier_kv *modifiers,
                     const uint32_t no_of_modifiers, const std::shared_ptr<ResourceManager> rm);
    ~StreamUltraSound();
   int32_t setVolume( struct pal_volume_data *volume __unused) {return 0;}
   int32_t setParameters(uint32_t param_id, void *payload);
   int32_t start();
   int32_t stop();
   int32_t setUltraSoundGain_l(pal_ultrasound_gain_t new_gain);
   int32_t setUltraSoundGain(pal_ultrasound_gain_t new_gain);
private:
    pal_ultrasound_gain_t gain;
    static void HandleCallBack(uint64_t hdl, uint32_t event_id,
                               void *data, uint32_t event_size, uint32_t miid);
    void HandleEvent(uint32_t event_id, void *data, uint32_t event_size);
};

#endif//STREAMULTRASOUND_H_
