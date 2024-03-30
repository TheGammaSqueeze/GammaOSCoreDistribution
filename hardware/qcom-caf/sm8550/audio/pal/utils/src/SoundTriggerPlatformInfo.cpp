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
 *
 * Copyright (c) 2022 Qualcomm Innovation Center, Inc. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted (subject to the limitations in the
 * disclaimer below) provided that the following conditions are met:
 *
 *   * Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *
 *   * Redistributions in binary form must reproduce the above
 *     copyright notice, this list of conditions and the following
 *     disclaimer in the documentation and/or other materials provided
 *     with the distribution.
 *
 *   * Neither the name of Qualcomm Innovation Center, Inc. nor the names of its
 *     contributors may be used to endorse or promote products derived
 *     from this software without specific prior written permission.
 *
 * NO EXPRESS OR IMPLIED LICENSES TO ANY PARTY'S PATENT RIGHTS ARE
 * GRANTED BY THIS LICENSE. THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT
 * HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER
 * IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR
 * OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

#include "ACDPlatformInfo.h"
#include "VoiceUIPlatformInfo.h"
#include "SoundTriggerPlatformInfo.h"

#define LOG_TAG "PAL: SoundTriggerPlatformInfo"

CaptureProfile::CaptureProfile(const std::string name) :
    name_(name),
    device_id_(PAL_DEVICE_IN_MIN),
    sample_rate_(16000),
    channels_(1),
    bitwidth_(16),
    device_pp_kv_(std::make_pair(0, 0)),
    snd_name_("va-mic"),
    is_ec_req_(false)
{
}

void CaptureProfile::HandleStartTag(const char* tag, const char** attribs)
{
    PAL_DBG(LOG_TAG, "Got start tag %s", tag);

    if (!strcmp(tag, "param")) {
        uint32_t i = 0;

        while (attribs[i]) {
            if (!strcmp(attribs[i], "device_id")) {
                auto itr = deviceIdLUT.find(attribs[++i]);
                if (itr == deviceIdLUT.end()) {
                    PAL_ERR(LOG_TAG, "could not find key %s in lookup table",
                            attribs[i]);
                } else {
                    device_id_ = itr->second;
                }
            } else if (!strcmp(attribs[i], "sample_rate")) {
                sample_rate_ = std::stoi(attribs[++i]);
            } else if (!strcmp(attribs[i], "bit_width")) {
                bitwidth_ = std::stoi(attribs[++i]);
            } else if (!strcmp(attribs[i], "channels")) {
                channels_ = std::stoi(attribs[++i]);
            } else if (!strcmp(attribs[i], "snd_name")) {
                snd_name_ = attribs[++i];
            } else if (!strcmp(attribs[i], "ec_ref")) {
                is_ec_req_ = !strncasecmp(attribs[++i], "true", 4) ? true : false;
            } else {
                PAL_ERR(LOG_TAG, "Invalid attribute %s", attribs[i++]);
            }
            ++i; /* move to next attribute */
        }
    } else {
        PAL_ERR(LOG_TAG, "Invalid tag %s", (char *)tag);
    }
}

/*
 * Priority compare result indicated by return value as below:
 * 1. CAPTURE_PROFILE_PRIORITY_HIGH
 *     current capture profile has higher priority than cap_prof
 * 2. CAPTURE_PROFILE_PRIORITY_LOW
 *     current capture profile has lower priority than cap_prof
 * 3. CAPTURE_PROFILE_PRIORITY_SAME
 *     current capture profile has same priority than cap_prof
 */
int32_t CaptureProfile::ComparePriority(std::shared_ptr<CaptureProfile> cap_prof)
{
    int32_t priority_check = 0;

    if (!cap_prof) {
        priority_check = CAPTURE_PROFILE_PRIORITY_HIGH;
    } else {
        // only compare channels for priority for now
        if (channels_ < cap_prof->GetChannels()) {
            priority_check = CAPTURE_PROFILE_PRIORITY_LOW;
        } else if (channels_ > cap_prof->GetChannels()) {
            priority_check = CAPTURE_PROFILE_PRIORITY_HIGH;
        } else {
            priority_check = CAPTURE_PROFILE_PRIORITY_SAME;
        }
    }

    return priority_check;
}

std::shared_ptr<SoundTriggerPlatformInfo> SoundTriggerPlatformInfo::me_ = nullptr;
bool SoundTriggerPlatformInfo::lpi_enable_ = true;
bool SoundTriggerPlatformInfo::support_nlpi_switch_ = true;
bool SoundTriggerPlatformInfo::support_device_switch_ = false;
bool SoundTriggerPlatformInfo::enable_debug_dumps_ = false;
bool SoundTriggerPlatformInfo::concurrent_capture_ = false;
bool SoundTriggerPlatformInfo::concurrent_voice_call_ = false;
bool SoundTriggerPlatformInfo::concurrent_voip_call_ = false;
bool SoundTriggerPlatformInfo::low_latency_bargein_enable_ = false;

SoundTriggerPlatformInfo::SoundTriggerPlatformInfo() : curr_child_(nullptr)
{
}

std::shared_ptr<SoundTriggerPlatformInfo> SoundTriggerPlatformInfo::GetInstance()
{
    if (!me_)
        me_ = std::shared_ptr<SoundTriggerPlatformInfo>(new SoundTriggerPlatformInfo);
    return me_;
}

void SoundTriggerPlatformInfo::ReadCapProfileNames(StOperatingModes mode,
                                                   const char** attribs,
                                                   st_op_modes_t& op_modes)
{
    uint32_t i = 0;

    while (attribs[i]) {
        if (!strcmp(attribs[i], "capture_profile_handset")) {
            op_modes[std::make_pair(mode, ST_INPUT_MODE_HANDSET)] =
                capture_profile_map_.at(std::string(attribs[++i]));
        } else if(!strcmp(attribs[i], "capture_profile_headset")) {
            op_modes[std::make_pair(mode, ST_INPUT_MODE_HEADSET)] =
                capture_profile_map_.at(std::string(attribs[++i]));
        } else {
            PAL_ERR(LOG_TAG, "Error:%d got unexpected attribute: %s",
                    -EINVAL, attribs[i]);
        }
        ++i; /* move to next attribute */
    }
}

void SoundTriggerPlatformInfo::HandleStartTag(const char* tag, const char** attribs)
{
    /* Delegate to child element if currently active */
    if (curr_child_) {
        curr_child_->HandleStartTag(tag, attribs);
        return;
    }

    PAL_DBG(LOG_TAG, "Got start tag %s", tag);

    if (!strcmp(tag, "vui_platform_info")) {
        curr_child_ = std::static_pointer_cast<SoundTriggerXml>(
                        VoiceUIPlatformInfo::GetInstance());
        return;
    }

    if (!strcmp(tag, "acd_platform_info")) {
        curr_child_ = std::static_pointer_cast<SoundTriggerXml>(
                        ACDPlatformInfo::GetInstance());
        return;
    }

    if (!strcmp(tag, "capture_profile")) {
        if (attribs[0] && !strcmp(attribs[0], "name")) {
            curr_child_ = std::static_pointer_cast<SoundTriggerXml>(
                    std::make_shared<CaptureProfile>(attribs[1]));
            return;
        } else {
            PAL_ERR(LOG_TAG,"missing name attrib for tag %s", tag);
            return;
        }
    }

    if (!strcmp(tag, "common_config") || !strcmp(tag, "capture_profile_list")) {
        PAL_VERBOSE(LOG_TAG, "tag:%s appeared, nothing to do", tag);
        return;
    }

    if (!strcmp(tag, "param")) {
        uint32_t i = 0;

        while (attribs[i]) {
            if (!attribs[i]) {
                PAL_ERR(LOG_TAG,"missing attrib value for tag %s", tag);
            } else if (!strcmp(attribs[i], "lpi_enable")) {
                lpi_enable_ =
                    !strncasecmp(attribs[++i], "true", 4) ? true : false;
            } else if (!strcmp(attribs[i], "support_nlpi_switch")) {
                support_nlpi_switch_ =
                    !strncasecmp(attribs[++i], "true", 4) ? true : false;
            } else if (!strcmp(attribs[i], "support_device_switch")) {
                support_device_switch_ =
                    !strncasecmp(attribs[++i], "true", 4) ? true : false;
            } else if (!strcmp(attribs[i], "enable_debug_dumps")) {
                enable_debug_dumps_ =
                    !strncasecmp(attribs[++i], "true", 4) ? true : false;
            } else if (!strcmp(attribs[i], "concurrent_capture")) {
                concurrent_capture_ =
                    !strncasecmp(attribs[++i], "true", 4) ? true : false;
            } else if (!strcmp(attribs[i], "concurrent_voice_call") &&
                       concurrent_capture_) {
                concurrent_voice_call_ =
                    !strncasecmp(attribs[++i], "true", 4) ? true : false;
            } else if (!strcmp(attribs[i], "concurrent_voip_call") &&
                       concurrent_capture_) {
                concurrent_voip_call_ =
                    !strncasecmp(attribs[++i], "true", 4) ? true : false;
            } else if (!strcmp(attribs[i], "low_latency_bargein_enable")) {
                low_latency_bargein_enable_ =
                    !strncasecmp(attribs[++i], "true", 4) ? true : false;
            } else {
                PAL_ERR(LOG_TAG, "Invalid attribute %s", attribs[i++]);
            }
            ++i; /* move to next attribute */
        }
    } else {
        PAL_ERR(LOG_TAG, "Invalid tag %s", tag);
    }
}

void SoundTriggerPlatformInfo::HandleEndTag(struct xml_userdata *data, const char* tag)
{
    PAL_DBG(LOG_TAG, "Got end tag %s", tag);

    if (!strcmp(tag, "capture_profile")) {
        std::shared_ptr<CaptureProfile> cap_prof(
            std::static_pointer_cast<CaptureProfile>(curr_child_));

        const auto res = capture_profile_map_.insert(
            std::make_pair(cap_prof->GetName(), cap_prof));
        if (!res.second)
            PAL_ERR(LOG_TAG, "Failed to insert to map");
        curr_child_ = nullptr;
    } else if (!strcmp(tag, "acd_platform_info") ||
               !strcmp(tag, "vui_platform_info")) {
        curr_child_ = nullptr;
    }

    if (curr_child_)
        curr_child_->HandleEndTag(data, tag);

    return;
}
