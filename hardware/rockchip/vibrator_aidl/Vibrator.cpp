/*
 * Copyright (C) 2019 The Android Open Source Project
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

#include "vibrator-impl/Vibrator.h"

#undef LOG_TAG
#define LOG_TAG "Vibrator-aidl"

namespace aidl {
namespace android {
namespace hardware {
namespace vibrator {

static constexpr int32_t kComposeDelayMaxMs = 1000;
static constexpr int32_t kComposeSizeMax = 256;
static constexpr int32_t kClickEffect = 10;
static constexpr int32_t kTickEffect = 20;
static constexpr int32_t kDoubleClickEffect = 30;
static constexpr int32_t kHeavyClickEffect = 40;

#define TIMEOUT_STR_LEN     20

static const char THE_DEVICE[] = "/sys/class/timed_output/vibrator/enable";

static bool device_exists(const char *file) {
    int fd = -1;

    fd = TEMP_FAILURE_RETRY(open(file, O_RDWR));
    if(fd < 0) {
        return false;
    }

    close(fd);
    return true;
}

static bool vibra_exists() {
    return device_exists(THE_DEVICE);
}

static int write_value(const char *file, const char *value)
{
    int to_write = 0, written = -1, ret = 0;
    int fd = -1;

    fd = TEMP_FAILURE_RETRY(open(file, O_WRONLY));
    if (fd < 0) {
        return -errno;
    }

    to_write = strlen(value) + 1;
    written = TEMP_FAILURE_RETRY(write(fd, value, to_write));
    if (written == -1) {
        ret = -errno;
    } else if (written != to_write) {
        /* even though EAGAIN is an errno value that could be set
           by write() in some cases, none of them apply here.  So, this return
           value can be clearly identified when debugging and suggests the
           caller that it may try to call vibrator_on() again */
        ret = -EAGAIN;
    } else {
        ret = 0;
    }

    errno = 0;
    close(fd);

    return ret;
}

static int sendit(unsigned int timeout_ms)
{
    char value[TIMEOUT_STR_LEN]; /* large enough for millions of years */

    int bytes = snprintf(value, sizeof(value), "%u", timeout_ms);
    if (bytes >= sizeof(value)) return -EINVAL;
    return write_value(THE_DEVICE, value);
}

static int vibra_on(unsigned int timeout_ms)
{
    /* constant on, up to maximum allowed time */
    return sendit(timeout_ms);
}

static int vibra_off()
{
    return sendit(0);
}

static const char LED_DEVICE[] = "/sys/class/leds/vibrator";

static int write_led_file(const char *file, const char *value)
{
    char file_str[50];

    int bytes = snprintf(file_str, sizeof(file_str), "%s/%s", LED_DEVICE, file);
    if (bytes >= sizeof(file_str)) return -EINVAL;
    return write_value(file_str, value);
}

static bool vibra_led_exists()
{
    char file_str[50];

    int bytes = snprintf(file_str, sizeof(file_str), "%s/%s", LED_DEVICE, "activate");
    if (bytes >= sizeof(file_str)) return -EINVAL;
    return device_exists(file_str);
}

static int vibra_led_on( unsigned int timeout_ms)
{
    int ret;
    char value[TIMEOUT_STR_LEN]; /* large enough for millions of years */

    ret = write_led_file("state", "1");
    if (ret)
        return ret;

    int bytes = snprintf(value, sizeof(value), "%u\n", timeout_ms);
    if (bytes >= sizeof(value)) return -EINVAL;
    ret = write_led_file("duration", value);
    if (ret)
        return ret;

    return write_led_file("activate", "1");
}

static int vibra_led_off()
{
    return write_led_file("activate", "0");
}

ndk::ScopedAStatus Vibrator::getCapabilities(int32_t* _aidl_return) {
    LOG(INFO) << "Vibrator reporting capabilities";
    if (_aidl_return == nullptr) {
        return ndk::ScopedAStatus(AStatus_fromExceptionCode(EX_ILLEGAL_ARGUMENT));
    }
#ifndef ENABLE_VIBRATOR_EFFECT
    *_aidl_return = 0;
#else
    *_aidl_return = IVibrator::CAP_ON_CALLBACK | IVibrator::CAP_PERFORM_CALLBACK |
                    IVibrator::CAP_AMPLITUDE_CONTROL | IVibrator::CAP_EXTERNAL_CONTROL |
                    IVibrator::CAP_EXTERNAL_AMPLITUDE_CONTROL | IVibrator::CAP_COMPOSE_EFFECTS |
                    IVibrator::CAP_ALWAYS_ON_CONTROL;
#endif

    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus Vibrator::off() {
    ALOGD("Vibrator off");
    if (vibra_exists()) {
        ALOGD("Vibrator using timed_output");
        vibra_off();
    } else if (vibra_led_exists()) {
        ALOGD("Vibrator using LED trigger");
        vibra_led_off();
    } else {
        ALOGI("Vibrator device does not exist. Cannot start vibrator");
    }
    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus Vibrator::on(int32_t timeoutMs,
                                const std::shared_ptr<IVibratorCallback>& callback) {
    ALOGI("Vibrator on for timeoutMs: %d", timeoutMs);

    if (vibra_exists()) {
        ALOGD("Vibrator using timed_output");
        vibra_on(timeoutMs);
    } else if (vibra_led_exists()) {
        ALOGD("Vibrator using LED trigger");
        vibra_led_on(timeoutMs);
    } else {
        ALOGI("Vibrator device does not exist. Cannot start vibrator");
    }
    if (callback != nullptr) {
#ifndef ENABLE_VIBRATOR_EFFECT
        return ndk::ScopedAStatus(AStatus_fromExceptionCode(EX_UNSUPPORTED_OPERATION));
#else
        std::thread([=] {
            ALOGD("Starting on on another thread");
            usleep(timeoutMs * 1000);
            ALOGD("Notifying on complete");
            if (!callback->onComplete().isOk()) {
                ALOGI("Failed to call onComplete");
            }
        }).detach();
    return ndk::ScopedAStatus::ok();
#endif
    } else {
        return ndk::ScopedAStatus::ok();
    }

}

ndk::ScopedAStatus Vibrator::perform(Effect effect, EffectStrength strength,
                                     const std::shared_ptr<IVibratorCallback>& callback,
                                     int32_t* _aidl_return) {
    uint32_t timeMS = 0;
    ndk::ScopedAStatus status;

    ALOGD("Vibrator perform %d, %d", (int)strength, (int)effect);
    if (callback != nullptr) {
        return ndk::ScopedAStatus(AStatus_fromExceptionCode(EX_UNSUPPORTED_OPERATION));
    }

    if (effect != Effect::CLICK && effect != Effect::TICK
        && effect != Effect::TEXTURE_TICK && effect != Effect::DOUBLE_CLICK
        && effect != Effect::HEAVY_CLICK) {
        return ndk::ScopedAStatus(AStatus_fromExceptionCode(EX_UNSUPPORTED_OPERATION));
    }
    if (strength != EffectStrength::LIGHT && strength != EffectStrength::MEDIUM &&
        strength != EffectStrength::STRONG) {
        return ndk::ScopedAStatus(AStatus_fromExceptionCode(EX_UNSUPPORTED_OPERATION));
    }

    switch (effect) {
        case Effect::TEXTURE_TICK:
            timeMS = kTickEffect;
            break;
        case Effect::CLICK:
            timeMS = kClickEffect;
            break;
        case Effect::DOUBLE_CLICK:
            timeMS = kDoubleClickEffect;
            break;
        case Effect::TICK:
            timeMS = kTickEffect;
            break;
        case Effect::HEAVY_CLICK:
            timeMS = kHeavyClickEffect;
            break;
        default:
            timeMS = 100;
            break;
    }

    status = on(timeMS, nullptr);
    if (!status.isOk()) {
        return status;
    } else {
        *_aidl_return = timeMS;
        return ndk::ScopedAStatus::ok();
    }
}

ndk::ScopedAStatus Vibrator::getSupportedEffects(std::vector<Effect>* _aidl_return) {

    *_aidl_return = {Effect::CLICK, Effect::TICK, Effect::TEXTURE_TICK,
        Effect::DOUBLE_CLICK, Effect::HEAVY_CLICK};

    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus Vibrator::setAmplitude(float amplitude) {
    LOG(INFO) << "Vibrator set amplitude: " << amplitude;
#ifndef ENABLE_VIBRATOR_EFFECT
    return ndk::ScopedAStatus(AStatus_fromExceptionCode(EX_UNSUPPORTED_OPERATION));
#else
    if (amplitude <= 0.0f || amplitude > 1.0f) {
        return ndk::ScopedAStatus(AStatus_fromExceptionCode(EX_ILLEGAL_ARGUMENT));
    }
    return ndk::ScopedAStatus::ok();
#endif
}

ndk::ScopedAStatus Vibrator::setExternalControl(bool enabled) {
    LOG(INFO) << "Vibrator set external control: " << enabled;
#ifndef ENABLE_VIBRATOR_EFFECT
    return ndk::ScopedAStatus(AStatus_fromExceptionCode(EX_UNSUPPORTED_OPERATION));
#else
    return ndk::ScopedAStatus::ok();
#endif
}

ndk::ScopedAStatus Vibrator::getCompositionDelayMax(int32_t* maxDelayMs) {
    *maxDelayMs = kComposeDelayMaxMs;
    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus Vibrator::getCompositionSizeMax(int32_t* maxSize) {
    *maxSize = kComposeSizeMax;
    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus Vibrator::getSupportedPrimitives(std::vector<CompositePrimitive>* supported) {
#ifndef ENABLE_VIBRATOR_EFFECT
    *supported = {};
#else
    *supported = {
            CompositePrimitive::NOOP,       CompositePrimitive::CLICK,
            CompositePrimitive::THUD,       CompositePrimitive::SPIN,
            CompositePrimitive::QUICK_RISE, CompositePrimitive::SLOW_RISE,
            CompositePrimitive::QUICK_FALL, CompositePrimitive::LIGHT_TICK,
    };
#endif
    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus Vibrator::getPrimitiveDuration(CompositePrimitive primitive,
                                                  int32_t* durationMs) {
    if (primitive != CompositePrimitive::NOOP) {
        *durationMs = 100;
    } else {
        *durationMs = 0;
    }
    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus Vibrator::compose(const std::vector<CompositeEffect>& composite,
                                     const std::shared_ptr<IVibratorCallback>& callback) {
    if (composite.size() > kComposeSizeMax) {
        return ndk::ScopedAStatus::fromExceptionCode(EX_ILLEGAL_ARGUMENT);
    }

    std::vector<CompositePrimitive> supported;
    getSupportedPrimitives(&supported);

    for (auto& e : composite) {
        if (e.delayMs > kComposeDelayMaxMs) {
            return ndk::ScopedAStatus::fromExceptionCode(EX_ILLEGAL_ARGUMENT);
        }
        if (e.scale < 0.0f || e.scale > 1.0f) {
            return ndk::ScopedAStatus::fromExceptionCode(EX_ILLEGAL_ARGUMENT);
        }
        if (std::find(supported.begin(), supported.end(), e.primitive) == supported.end()) {
            return ndk::ScopedAStatus::fromExceptionCode(EX_UNSUPPORTED_OPERATION);
        }
    }

    std::thread([=] {
        LOG(INFO) << "Starting compose on another thread";

        for (auto& e : composite) {
            if (e.delayMs) {
                usleep(e.delayMs * 1000);
            }
            LOG(INFO) << "triggering primitive " << static_cast<int>(e.primitive) << " @ scale "
                      << e.scale;
        }

        if (callback != nullptr) {
            LOG(INFO) << "Notifying perform complete";
            callback->onComplete();
        }
    }).detach();

    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus Vibrator::getSupportedAlwaysOnEffects(std::vector<Effect>* _aidl_return) {
    return getSupportedEffects(_aidl_return);
}

ndk::ScopedAStatus Vibrator::alwaysOnEnable(int32_t id, Effect effect, EffectStrength strength) {
    std::vector<Effect> effects;
    getSupportedAlwaysOnEffects(&effects);

    if (std::find(effects.begin(), effects.end(), effect) == effects.end()) {
        return ndk::ScopedAStatus::fromExceptionCode(EX_UNSUPPORTED_OPERATION);
    } else {
        LOG(INFO) << "Enabling always-on ID " << id << " with " << toString(effect) << "/"
                  << toString(strength);
        return ndk::ScopedAStatus::ok();
    }
}

ndk::ScopedAStatus Vibrator::alwaysOnDisable(int32_t id) {
    LOG(INFO) << "Disabling always-on ID " << id;
    return ndk::ScopedAStatus::ok();
}

}  // namespace vibrator
}  // namespace hardware
}  // namespace android
}  // namespace aidl
