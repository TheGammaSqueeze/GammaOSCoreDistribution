/*
 * Copyright (c) 2020 Rockchip Electronics Co., Ltd
 */

#include "Power.h"

#include <aidl/android/hardware/power/Boost.h>
#define LOG_TAG "PowerAIDL"

#include <android-base/logging.h>
#include <cutils/properties.h>
#include <string>
#include <log/log.h>
#include <dirent.h>
#include <inttypes.h>

#include <android-base/file.h>
#include <android-base/logging.h>
#include <android-base/properties.h>
#include <android-base/stringprintf.h>
#include <android-base/strings.h>
#define DEBUG_EN 0

#define BUFFER_LENGTH 64
#define DEV_FREQ_PATH "/sys/class/devfreq"
#define CPU_CLUST_INFO_PATH "/sys/devices/system/cpu/cpufreq"

static int is_inited = 0;
static int is_performance = 0;

using ::android::base::StringPrintf;

namespace aidl {
namespace android {
namespace hardware {
namespace power {
namespace impl {
namespace rockchip {

#define PW_LOG_DEBUG(...) if (DEBUG_EN) ALOGD(__VA_ARGS__)

void sysfs_read(std::string path, std::string *buf) {
    if (!::android::base::ReadFileToString(path, buf)) {
        ALOGE("Error to open %s", path.c_str());
        std::string realpath;
        if (!::android::base::Realpath(path, &realpath)) {
            ALOGE("Realpath is not exist!");
        } else {
            ALOGI("Trying read from realpath: %s", realpath.c_str());
            sysfs_read(realpath, buf);
        }
    } else {
        PW_LOG_DEBUG("read from %s value %s", path.c_str(), buf->c_str());
    }
}

static void sysfs_write(const char *path, const char *s) {
    PW_LOG_DEBUG("write %s %s", path, s);

    if (access(path, F_OK) < 0) return;

    char buf[80];
    int len;
    int fd = open(path, O_WRONLY);

    if (fd < 0) {
        strerror_r(errno, buf, sizeof(buf));
        ALOGE("Error opening %s: %s\n", path, buf);
        return;
    }

    len = write(fd, s, strlen(s));
    if (len < 0) {
        strerror_r(errno, buf, sizeof(buf));
        ALOGE("Error writing to %s: %s\n", path, buf);
    }
    close(fd);
}

ClusterInfo::ClusterInfo(const ClusterType type, const std::string& clust) : _type(type) {
    std::string minPath, maxPath;
    switch (type) {
        case ClusterType::CPU:
            _minFreqPath = StringPrintf("%s/%s/scaling_min_freq",
                                        CPU_CLUST_INFO_PATH, clust.c_str());
            _maxFreqPath = StringPrintf("%s/%s/scaling_max_freq",
                                        CPU_CLUST_INFO_PATH, clust.c_str());
            _govPath = StringPrintf("%s/%s/scaling_governor",
                                    CPU_CLUST_INFO_PATH, clust.c_str());
            minPath = StringPrintf("%s/%s/cpuinfo_min_freq",
                                   CPU_CLUST_INFO_PATH, clust.c_str());
            maxPath = StringPrintf("%s/%s/cpuinfo_max_freq",
                                   CPU_CLUST_INFO_PATH, clust.c_str());
            break;
        case ClusterType::GPU:
            _minFreqPath = StringPrintf("%s/min_freq", clust.c_str());
            _maxFreqPath = StringPrintf("%s/max_freq", clust.c_str());
            _govPath = StringPrintf("%s/governor", clust.c_str());
            minPath = _minFreqPath;
            maxPath = _maxFreqPath;
            break;
        case ClusterType::DDR:
            _minFreqPath = StringPrintf("%s/min_freq", clust.c_str());
            _maxFreqPath = StringPrintf("%s/max_freq", clust.c_str());
            _govPath = StringPrintf("%s/system_status", clust.c_str());
            minPath = _minFreqPath;
            maxPath = _maxFreqPath;
            break;
        default:
            break;
    }
    if (minPath == "" || maxPath == "") {
        ALOGW("Failed to register, minPath/maxPath is null!");
        return;
    }
    sysfs_read(minPath, &_minFreq);
    sysfs_read(maxPath, &_maxFreq);
    sysfs_read(_govPath, &_govDefault);
    ALOGI("Registered: %s", toString().c_str());
}

std::string ClusterInfo::toString() {
    std::string type;
    switch (_type) {
        case ClusterType::CPU:
            type = "CPU";
            break;
        case ClusterType::GPU:
            type = "GPU";
            break;
        case ClusterType::DDR:
            type = "DDR";
            break;
        default:
            type = "unknown";
            break;
    }
    return StringPrintf("%s \nmin: %smax: %s",
                        type.c_str(), _minFreq.c_str(), _maxFreq.c_str());
}

void ClusterInfo::setMinFreq(const std::string& freq) {
    sysfs_write(_minFreqPath.c_str(), freq.c_str());
}

void ClusterInfo::setMaxFreq(const std::string& freq) {
    sysfs_write(_maxFreqPath.c_str(), freq.c_str());
}

void ClusterInfo::setPerformance(bool on) {
    if (on) {
        setMinFreq(_maxFreq);
    } else {
        setMinFreq(_minFreq);
    }
}

void ClusterInfo::setGov(const std::string& governor) {
    sysfs_write(_govPath.c_str(), governor.c_str());
}

void ClusterInfo::setPowerSave(bool on) {
    switch (getType()) {
        case ClusterType::CPU:
            [[fallthrough]];
        case ClusterType::GPU:
            setGov(on ? "powersave" : _govDefault);
        case ClusterType::DDR:
            setGov(on ? "l" : "L");
            break;
        default:
            break;
    }
}

void ClusterInfo::setInteractive() {
    switch (getType()) {
        case ClusterType::CPU:
            setGov(_govDefault);
            break;
        default:
            break;
    }
}

void Power::initPlatform() {

    if (is_inited || (_boot_complete <= 0)) return;

    ALOGI("version 12.0\n");
    auto findWithPath = [&](const char *path, ClusterType type) {
        std::unique_ptr<DIR, decltype(&closedir)>dir(opendir(path), closedir);
        if (!dir) return;
        std::string name;
        dirent* dp;
        while ((dp = readdir(dir.get())) != nullptr) {
            if (type == ClusterType::GPU) {
                name = dp->d_name;
                if (strstr(name.c_str(), "gpu") != NULL) {
                    ClusterInfo gpu = ClusterInfo(ClusterType::GPU,
                                                  StringPrintf("%s/%s", DEV_FREQ_PATH, dp->d_name));
                    clusterList.push_back(gpu);
                    break;
                }
            } else if (type == ClusterType::CPU) {
                if (dp->d_name[0] == '.') {
                    continue;
                }
                ClusterInfo cpu = ClusterInfo(ClusterType::CPU, dp->d_name);
                clusterList.push_back(cpu);
            }
        }
    };

    findWithPath(CPU_CLUST_INFO_PATH, ClusterType::CPU);
    findWithPath(DEV_FREQ_PATH, ClusterType::GPU);

    ClusterInfo ddr = ClusterInfo(ClusterType::DDR, "/sys/class/devfreq/dmc");
    clusterList.push_back(ddr);
    is_inited = 1;
}

void Power::getSupportedPlatform() {
    if (_mode_support_int < 0) {
        _boost_support_int = property_get_int64("ro.vendor.power.boost_support", 0x003F);
        // Disable power save by default.
        _mode_support_int = property_get_int64("ro.vendor.power.mode_support", 0x7FFF & 0xDF9F);
        ALOGI("Initial with boost: %" PRId64", mode: %" PRId64,
              _boost_support_int, _mode_support_int);
    }

    if (_boot_complete <= 0) {
        _boot_complete = property_get_bool("vendor.boot_completed", 0);
        PW_LOG_DEBUG("Boot complete: %s", _boot_complete?"true":"false");
    }
    initPlatform();
}

ndk::ScopedAStatus Power::setMode(Mode type, bool enabled) {
    PW_LOG_DEBUG("Power setMode: %d to: %s", static_cast<int32_t>(type), (enabled?"on":"off"));
    getSupportedPlatform();
    switch (type) {
        case Mode::DOUBLE_TAP_TO_WAKE:
        break;
        case Mode::LOW_POWER:
            powerSave(enabled);
        break;
        case Mode::SUSTAINED_PERFORMANCE:
        break;
        case Mode::FIXED_PERFORMANCE:
            performanceBoost(enabled);
        break;
        case Mode::VR:
        break;
        case Mode::LAUNCH:
            performanceBoost(enabled);
        break;
        case Mode::EXPENSIVE_RENDERING:
        break;
        case Mode::INTERACTIVE:
            if (enabled) interactive();
        break;
        case Mode::DEVICE_IDLE:
            powerSave(enabled);
        break;
        case Mode::DISPLAY_INACTIVE:
            for (auto gpu : clusterList) {
                if (gpu.getType() == ClusterType::GPU) {
                    gpu.setPowerSave(enabled);
                }
            }
        break;
        case Mode::AUDIO_STREAMING_LOW_LATENCY:
        break;
        case Mode::CAMERA_STREAMING_SECURE:
        break;
        case Mode::CAMERA_STREAMING_LOW:
        break;
        case Mode::CAMERA_STREAMING_MID:
        break;
        case Mode::CAMERA_STREAMING_HIGH:
        break;
        default:
        break;
    }
    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus Power::setBoost(Boost type, int32_t durationMs) {
    PW_LOG_DEBUG("Power setBoost: %d, duration: %d", static_cast<int32_t>(type), durationMs);
    getSupportedPlatform();
    switch (type) {
        // Touch screen
        case Boost::INTERACTION:
        case Boost::DISPLAY_UPDATE_IMMINENT:
        case Boost::ML_ACC:
        case Boost::AUDIO_LAUNCH:
        case Boost::CAMERA_LAUNCH:
        case Boost::CAMERA_SHOT:
        default:
        break;
    }
    return ndk::ScopedAStatus::ok();
}

/**
 * _PLACEHOLDER_,           DOUBLE_TAP_TO_WAKE,     LOW_POWER,              SUSTAINED_PERFORMANCE,
 * FIXED_PERFORMANCE,       VR,                     LAUNCH,                 EXPENSIVE_RENDERING,
 * INTERACTIVE,             DEVICE_IDLE,            DISPLAY_INACTIVE,       AUDIO_STREAMING_LOW_LATENCY,
 * CAMERA_STREAMING_SECURE, CAMERA_STREAMING_LOW,   CAMERA_STREAMING_MID,   CAMERA_STREAMING_HIGH
 */
ndk::ScopedAStatus Power::isModeSupported(Mode type, bool* _aidl_return) {
    PW_LOG_DEBUG("Power isModeSupported: %d", static_cast<int32_t>(type));
    getSupportedPlatform();
    switch (type) {
        case Mode::DOUBLE_TAP_TO_WAKE:
            *_aidl_return = 0x4000 & _mode_support_int;
        break;
        case Mode::LOW_POWER:
            *_aidl_return = 0x2000 & _mode_support_int;
        break;
        case Mode::SUSTAINED_PERFORMANCE:
            *_aidl_return = 0x1000 & _mode_support_int;
        break;
        case Mode::FIXED_PERFORMANCE:
            *_aidl_return = 0x0800 & _mode_support_int;
        break;
        case Mode::VR:
            *_aidl_return = 0x0400 & _mode_support_int;
        break;
        case Mode::LAUNCH:
            *_aidl_return = 0x0200 & _mode_support_int;
        break;
        case Mode::EXPENSIVE_RENDERING:
            *_aidl_return = 0x0100 & _mode_support_int;
        break;
        case Mode::INTERACTIVE:
            *_aidl_return = 0x0080 & _mode_support_int;
        break;
        case Mode::DEVICE_IDLE:
            *_aidl_return = 0x0040 & _mode_support_int;
        break;
        case Mode::DISPLAY_INACTIVE:
            *_aidl_return = 0x0020 & _mode_support_int;
        break;
        case Mode::AUDIO_STREAMING_LOW_LATENCY:
            *_aidl_return = 0x0010 & _mode_support_int;
        break;
        case Mode::CAMERA_STREAMING_SECURE:
            *_aidl_return = 0x0008 & _mode_support_int;
        break;
        case Mode::CAMERA_STREAMING_LOW:
            *_aidl_return = 0x0004 & _mode_support_int;
        break;
        case Mode::CAMERA_STREAMING_MID:
            *_aidl_return = 0x0002 & _mode_support_int;
        break;
        case Mode::CAMERA_STREAMING_HIGH:
            *_aidl_return = 0x0001 & _mode_support_int;
        break;
        default:
            *_aidl_return = false;
        break;
    }
    return ndk::ScopedAStatus::ok();
}

/**
 * Boost type defined from:
 * hardware/interfaces/power/aidl/android/hardware/power/Boost.aidl
 *
 * platform : _PLACEHOLDER_, _PLACEHOLDER_, INTERACTION,  DISPLAY_UPDATE_IMMINENT,
 *            ML_AAC,        AUDIO_LAUNCH,  CAMERA_LUNCH, CAMERA_SHOT
 *
 * rk3399 : 0x003F
 * rk3326 : 0x003F
 * ...
 */
ndk::ScopedAStatus Power::isBoostSupported(Boost type, bool* _aidl_return) {
    PW_LOG_DEBUG("Power isBoostSupported: %d", static_cast<int32_t>(type));
    getSupportedPlatform();
    switch (type) {
        // Touch screen
        case Boost::INTERACTION:
            *_aidl_return = 0x0020 & _boost_support_int;
        break;
        // Refresh screen
        case Boost::DISPLAY_UPDATE_IMMINENT:
            *_aidl_return = 0x0010 & _boost_support_int;
        break;
        // ML accelerator
        case Boost::ML_ACC:
            *_aidl_return = 0x0008 & _boost_support_int;
        break;
        case Boost::AUDIO_LAUNCH:
            *_aidl_return = 0x0004 & _boost_support_int;
        break;
        case Boost::CAMERA_LAUNCH:
            *_aidl_return = 0x0002 & _boost_support_int;
        break;
        case Boost::CAMERA_SHOT:
            *_aidl_return = 0x0001 & _boost_support_int;
        break;
        default:
            *_aidl_return = false;
        break;
    }
    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus Power::createHintSession(int32_t, int32_t, const std::vector<int32_t>&, int64_t,
                                            std::shared_ptr<IPowerHintSession>* _aidl_return) {
    *_aidl_return = nullptr;
    return ndk::ScopedAStatus::fromExceptionCode(EX_UNSUPPORTED_OPERATION);
}

ndk::ScopedAStatus Power::getHintSessionPreferredRate(int64_t* outNanoseconds) {
    *outNanoseconds = -1;
    return ndk::ScopedAStatus::fromExceptionCode(EX_UNSUPPORTED_OPERATION);
}

void Power::performanceBoost(bool on) {
    if (!_boot_complete) {
        PW_LOG_DEBUG("RK performance_boost skiped during boot!");
        return;
    }

    if (!on) is_performance = 0;

    if (is_performance == 0) {
        PW_LOG_DEBUG("RK performance_boost Entered! on=%d",on);
        for (auto cluster : clusterList) {
            cluster.setPerformance(on);
        }
        if (on) is_performance = 1;
    }
}

void Power::powerSave(bool on) {
    PW_LOG_DEBUG("RK powersave Entered!");
    for (auto cluster : clusterList) {
        cluster.setPowerSave(on);
    }
}

void Power::interactive() {
    if (!_boot_complete) {
        PW_LOG_DEBUG("RK interactive skiped during boot!");
        return;
    }
    PW_LOG_DEBUG("RK interactive Entered!");
    for (auto cluster : clusterList) {
        cluster.setInteractive();
    }
}

}  // namespace rockchip
}  // namespace impl
}  // namespace power
}  // namespace hardware
}  // namespace android
}  // namespace aidl
