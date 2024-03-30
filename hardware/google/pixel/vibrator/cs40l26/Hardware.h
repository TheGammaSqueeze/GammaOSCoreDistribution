/*
 * Copyright (C) 2021 The Android Open Source Project
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
#pragma once

#include <algorithm>

#include "HardwareBase.h"
#include "Vibrator.h"

#define PROC_SND_PCM "/proc/asound/pcm"
#define HAPTIC_PCM_DEVICE_SYMBOL "haptic nohost playback"

static struct pcm_config haptic_nohost_config = {
        .channels = 1,
        .rate = 48000,
        .period_size = 80,
        .period_count = 2,
        .format = PCM_FORMAT_S16_LE,
};

enum WaveformIndex : uint16_t {
    /* Physical waveform */
    WAVEFORM_LONG_VIBRATION_EFFECT_INDEX = 0,
    WAVEFORM_RESERVED_INDEX_1 = 1,
    WAVEFORM_CLICK_INDEX = 2,
    WAVEFORM_SHORT_VIBRATION_EFFECT_INDEX = 3,
    WAVEFORM_THUD_INDEX = 4,
    WAVEFORM_SPIN_INDEX = 5,
    WAVEFORM_QUICK_RISE_INDEX = 6,
    WAVEFORM_SLOW_RISE_INDEX = 7,
    WAVEFORM_QUICK_FALL_INDEX = 8,
    WAVEFORM_LIGHT_TICK_INDEX = 9,
    WAVEFORM_LOW_TICK_INDEX = 10,
    WAVEFORM_RESERVED_MFG_1,
    WAVEFORM_RESERVED_MFG_2,
    WAVEFORM_RESERVED_MFG_3,
    WAVEFORM_MAX_PHYSICAL_INDEX,
    /* OWT waveform */
    WAVEFORM_COMPOSE = WAVEFORM_MAX_PHYSICAL_INDEX,
    WAVEFORM_PWLE,
    /*
     * Refer to <linux/input.h>, the WAVEFORM_MAX_INDEX must not exceed 96.
     * #define FF_GAIN          0x60  // 96 in decimal
     * #define FF_MAX_EFFECTS   FF_GAIN
     */
    WAVEFORM_MAX_INDEX,
};

namespace aidl {
namespace android {
namespace hardware {
namespace vibrator {

class HwApi : public Vibrator::HwApi, private HwApiBase {
  public:
    HwApi() {
        open("calibration/f0_stored", &mF0);
        open("default/f0_offset", &mF0Offset);
        open("calibration/redc_stored", &mRedc);
        open("calibration/q_stored", &mQ);
        open("default/vibe_state", &mVibeState);
        open("default/num_waves", &mEffectCount);
        open("default/owt_free_space", &mOwtFreeSpace);
        open("default/f0_comp_enable", &mF0CompEnable);
        open("default/redc_comp_enable", &mRedcCompEnable);
        open("default/delay_before_stop_playback_us", &mMinOnOffInterval);
    }

    bool setF0(std::string value) override { return set(value, &mF0); }
    bool setF0Offset(uint32_t value) override { return set(value, &mF0Offset); }
    bool setRedc(std::string value) override { return set(value, &mRedc); }
    bool setQ(std::string value) override { return set(value, &mQ); }
    bool getEffectCount(uint32_t *value) override { return get(value, &mEffectCount); }
    bool pollVibeState(uint32_t value, int32_t timeoutMs) override {
        return poll(value, &mVibeState, timeoutMs);
    }
    bool hasOwtFreeSpace() override { return has(mOwtFreeSpace); }
    bool getOwtFreeSpace(uint32_t *value) override { return get(value, &mOwtFreeSpace); }
    bool setF0CompEnable(bool value) override { return set(value, &mF0CompEnable); }
    bool setRedcCompEnable(bool value) override { return set(value, &mRedcCompEnable); }
    bool setMinOnOffInterval(uint32_t value) override { return set(value, &mMinOnOffInterval); }
    // TODO(b/234338136): Need to add the force feedback HW API test cases
    bool setFFGain(int fd, uint16_t value) override {
        struct input_event gain = {
                .type = EV_FF,
                .code = FF_GAIN,
                .value = value,
        };
        if (write(fd, (const void *)&gain, sizeof(gain)) != sizeof(gain)) {
            return false;
        }
        return true;
    }
    bool setFFEffect(int fd, struct ff_effect *effect, uint16_t timeoutMs) override {
        if (((*effect).replay.length != timeoutMs) || (ioctl(fd, EVIOCSFF, effect) < 0)) {
            ALOGE("setFFEffect fail");
            return false;
        } else {
            return true;
        }
    }
    bool setFFPlay(int fd, int8_t index, bool value) override {
        struct input_event play = {
                .type = EV_FF,
                .code = static_cast<uint16_t>(index),
                .value = value,
        };
        if (write(fd, (const void *)&play, sizeof(play)) != sizeof(play)) {
            return false;
        } else {
            return true;
        }
    }
    bool getHapticAlsaDevice(int *card, int *device) override {
        std::string line;
        std::ifstream myfile(PROC_SND_PCM);
        if (myfile.is_open()) {
            while (getline(myfile, line)) {
                if (line.find(HAPTIC_PCM_DEVICE_SYMBOL) != std::string::npos) {
                    std::stringstream ss(line);
                    std::string currentToken;
                    std::getline(ss, currentToken, ':');
                    sscanf(currentToken.c_str(), "%d-%d", card, device);
                    return true;
                }
            }
            myfile.close();
        } else {
            ALOGE("Failed to read file: %s", PROC_SND_PCM);
        }
        return false;
    }
    bool setHapticPcmAmp(struct pcm **haptic_pcm, bool enable, int card, int device) override {
        int ret = 0;

        if (enable) {
            *haptic_pcm = pcm_open(card, device, PCM_OUT, &haptic_nohost_config);
            if (!pcm_is_ready(*haptic_pcm)) {
                ALOGE("cannot open pcm_out driver: %s", pcm_get_error(*haptic_pcm));
                goto fail;
            }

            ret = pcm_prepare(*haptic_pcm);
            if (ret < 0) {
                ALOGE("cannot prepare haptic_pcm: %s", pcm_get_error(*haptic_pcm));
                goto fail;
            }

            ret = pcm_start(*haptic_pcm);
            if (ret < 0) {
                ALOGE("cannot start haptic_pcm: %s", pcm_get_error(*haptic_pcm));
                goto fail;
            }

            return true;
        } else {
            if (*haptic_pcm) {
                pcm_close(*haptic_pcm);
                *haptic_pcm = NULL;
            }
            return true;
        }

    fail:
        pcm_close(*haptic_pcm);
        *haptic_pcm = NULL;
        return false;
    }
    bool uploadOwtEffect(int fd, uint8_t *owtData, uint32_t numBytes, struct ff_effect *effect,
                         uint32_t *outEffectIndex, int *status) override {
        (*effect).u.periodic.custom_len = numBytes / sizeof(uint16_t);
        delete[] ((*effect).u.periodic.custom_data);
        (*effect).u.periodic.custom_data = new int16_t[(*effect).u.periodic.custom_len]{0x0000};
        if ((*effect).u.periodic.custom_data == nullptr) {
            ALOGE("Failed to allocate memory for custom data\n");
            *status = EX_NULL_POINTER;
            return false;
        }
        memcpy((*effect).u.periodic.custom_data, owtData, numBytes);

        if ((*effect).id != -1) {
            ALOGE("(*effect).id != -1");
        }

        /* Create a new OWT waveform to update the PWLE or composite effect. */
        (*effect).id = -1;
        if (ioctl(fd, EVIOCSFF, effect) < 0) {
            ALOGE("Failed to upload effect %d (%d): %s", *outEffectIndex, errno, strerror(errno));
            delete[] ((*effect).u.periodic.custom_data);
            *status = EX_ILLEGAL_STATE;
            return false;
        }

        if ((*effect).id >= FF_MAX_EFFECTS || (*effect).id < 0) {
            ALOGE("Invalid waveform index after upload OWT effect: %d", (*effect).id);
            *status = EX_ILLEGAL_ARGUMENT;
            return false;
        }
        *outEffectIndex = (*effect).id;
        *status = 0;
        return true;
    }
    bool eraseOwtEffect(int fd, int8_t effectIndex, std::vector<ff_effect> *effect) override {
        uint32_t effectCountBefore, effectCountAfter, i, successFlush = 0;

        if (effectIndex < WAVEFORM_MAX_PHYSICAL_INDEX) {
            ALOGE("Invalid waveform index for OWT erase: %d", effectIndex);
            return false;
        }

        if (effectIndex < WAVEFORM_MAX_INDEX) {
            /* Normal situation. Only erase the effect which we just played. */
            if (ioctl(fd, EVIOCRMFF, effectIndex) < 0) {
                ALOGE("Failed to erase effect %d (%d): %s", effectIndex, errno, strerror(errno));
            }
            for (i = WAVEFORM_MAX_PHYSICAL_INDEX; i < WAVEFORM_MAX_INDEX; i++) {
                if ((*effect)[i].id == effectIndex) {
                    (*effect)[i].id = -1;
                    break;
                }
            }
        } else {
            /* Flush all non-prestored effects of ff-core and driver. */
            getEffectCount(&effectCountBefore);
            for (i = WAVEFORM_MAX_PHYSICAL_INDEX; i < FF_MAX_EFFECTS; i++) {
                if (ioctl(fd, EVIOCRMFF, i) >= 0) {
                    successFlush++;
                }
            }
            getEffectCount(&effectCountAfter);
            ALOGW("Flushed effects: ff: %d; driver: %d -> %d; success: %d", effectIndex,
                  effectCountBefore, effectCountAfter, successFlush);
            /* Reset all OWT effect index of HAL. */
            for (i = WAVEFORM_MAX_PHYSICAL_INDEX; i < WAVEFORM_MAX_INDEX; i++) {
                (*effect)[i].id = -1;
            }
        }
        return true;
    }

    void debug(int fd) override { HwApiBase::debug(fd); }

  private:
    std::ofstream mF0;
    std::ofstream mF0Offset;
    std::ofstream mRedc;
    std::ofstream mQ;
    std::ifstream mEffectCount;
    std::ifstream mVibeState;
    std::ifstream mOwtFreeSpace;
    std::ofstream mF0CompEnable;
    std::ofstream mRedcCompEnable;
    std::ofstream mMinOnOffInterval;
};

class HwCal : public Vibrator::HwCal, private HwCalBase {
  private:
    static constexpr char VERSION[] = "version";
    static constexpr char F0_CONFIG[] = "f0_measured";
    static constexpr char REDC_CONFIG[] = "redc_measured";
    static constexpr char Q_CONFIG[] = "q_measured";
    static constexpr char TICK_VOLTAGES_CONFIG[] = "v_tick";
    static constexpr char CLICK_VOLTAGES_CONFIG[] = "v_click";
    static constexpr char LONG_VOLTAGES_CONFIG[] = "v_long";

    static constexpr uint32_t VERSION_DEFAULT = 2;
    static constexpr int32_t DEFAULT_FREQUENCY_SHIFT = 0;
    static constexpr std::array<uint32_t, 2> V_TICK_DEFAULT = {1, 100};
    static constexpr std::array<uint32_t, 2> V_CLICK_DEFAULT = {1, 100};
    static constexpr std::array<uint32_t, 2> V_LONG_DEFAULT = {1, 100};

  public:
    HwCal() {}

    bool getVersion(uint32_t *value) override {
        if (getPersist(VERSION, value)) {
            return true;
        }
        *value = VERSION_DEFAULT;
        return true;
    }
    bool getLongFrequencyShift(int32_t *value) override {
        return getProperty("long.frequency.shift", value, DEFAULT_FREQUENCY_SHIFT);
    }
    bool getF0(std::string *value) override { return getPersist(F0_CONFIG, value); }
    bool getRedc(std::string *value) override { return getPersist(REDC_CONFIG, value); }
    bool getQ(std::string *value) override { return getPersist(Q_CONFIG, value); }
    bool getTickVolLevels(std::array<uint32_t, 2> *value) override {
        if (getPersist(TICK_VOLTAGES_CONFIG, value)) {
            return true;
        }
        *value = V_TICK_DEFAULT;
        return true;
    }
    bool getClickVolLevels(std::array<uint32_t, 2> *value) override {
        if (getPersist(CLICK_VOLTAGES_CONFIG, value)) {
            return true;
        }
        *value = V_CLICK_DEFAULT;
        return true;
    }
    bool getLongVolLevels(std::array<uint32_t, 2> *value) override {
        if (getPersist(LONG_VOLTAGES_CONFIG, value)) {
            return true;
        }
        *value = V_LONG_DEFAULT;
        return true;
    }
    bool isChirpEnabled() override {
        bool value;
        getProperty("chirp.enabled", &value, false);
        return value;
    }
    bool getSupportedPrimitives(uint32_t *value) override {
        return getProperty("supported_primitives", value, (uint32_t)0);
    }
    bool isF0CompEnabled() override {
        bool value;
        getProperty("f0.comp.enabled", &value, true);
        return value;
    }
    bool isRedcCompEnabled() override {
        bool value;
        getProperty("redc.comp.enabled", &value, true);
        return value;
    }
    void debug(int fd) override { HwCalBase::debug(fd); }
};

}  // namespace vibrator
}  // namespace hardware
}  // namespace android
}  // namespace aidl
