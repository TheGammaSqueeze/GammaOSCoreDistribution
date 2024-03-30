/*
 * Copyright (C) 2012 The Android Open Source Project
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
//#define LOG_NDEBUG 0

#define ATRACE_TAG (ATRACE_TAG_GRAPHICS | ATRACE_TAG_HAL)

#include "ExynosPrimaryDisplay.h"

#include <linux/fb.h>
#include <poll.h>

#include <chrono>
#include <fstream>

#include "BrightnessController.h"
#include "ExynosDevice.h"
#include "ExynosDisplayDrmInterface.h"
#include "ExynosDisplayDrmInterfaceModule.h"
#include "ExynosExternalDisplay.h"
#include "ExynosHWCDebug.h"
#include "ExynosHWCHelper.h"
#include "ExynosLayer.h"

extern struct exynos_hwc_control exynosHWCControl;

using namespace SOC_VERSION;
constexpr auto nsecsPerSec = std::chrono::nanoseconds(1s).count();

static const std::map<const DisplayType, const std::string> panelSysfsPath =
        {{DisplayType::DISPLAY_PRIMARY, "/sys/devices/platform/exynos-drm/primary-panel/"},
         {DisplayType::DISPLAY_SECONDARY, "/sys/devices/platform/exynos-drm/secondary-panel/"}};

static constexpr const char *PROPERTY_BOOT_MODE = "persist.vendor.display.primary.boot_config";

static std::string loadPanelGammaCalibration(const std::string &file) {
    std::ifstream ifs(file);

    if (!ifs.is_open()) {
        ALOGW("Unable to open gamma calibration '%s', error = %s", file.c_str(), strerror(errno));
        return {};
    }

    std::string raw_data, gamma;
    char ch;
    while (std::getline(ifs, raw_data, '\r')) {
        gamma.append(raw_data);
        gamma.append(1, ' ');
        ifs.get(ch);
        if (ch != '\n') {
            gamma.append(1, ch);
        }
    }
    ifs.close();

    /* eliminate space character in the last byte */
    if (!gamma.empty()) {
        gamma.pop_back();
    }

    return gamma;
}

ExynosPrimaryDisplay::ExynosPrimaryDisplay(uint32_t index, ExynosDevice *device)
      : ExynosDisplay(index, device),
        mMinIdleRefreshRate(0),
        mRefreshRateDelayNanos(0),
        mLastRefreshRateAppliedNanos(0),
        mAppliedActiveConfig(0),
        mDisplayIdleTimerEnabled(false),
        mDisplayNeedHandleIdleExit(false) {
    // TODO : Hard coded here
    mNumMaxPriorityAllowed = 5;

    /* Initialization */
    mType = HWC_DISPLAY_PRIMARY;
    mIndex = index;
    mDisplayId = getDisplayId(mType, mIndex);
    mFramesToReachLhbmPeakBrightness =
            property_get_int32("vendor.primarydisplay.lhbm.frames_to_reach_peak_brightness", 3);

    // Allow to enable dynamic recomposition after every power on
    // since it will always be disabled for every power off
    // TODO(b/268474771): to enable DR by default if video mode panel is detected
    if (property_get_int32("vendor.display.dynamic_recomposition", 0) & (1 << index)) {
        mDRDefault = true;
        mDREnable = true;
    }

    // Prepare multi resolution
    // Will be exynosHWCControl.multiResoultion
    mResolutionInfo.nNum = 1;
    mResolutionInfo.nResolution[0].w = 1440;
    mResolutionInfo.nResolution[0].h = 2960;
    mResolutionInfo.nDSCYSliceSize[0] = 40;
    mResolutionInfo.nDSCXSliceSize[0] = 1440 / 2;
    mResolutionInfo.nPanelType[0] = PANEL_DSC;
    mResolutionInfo.nResolution[1].w = 1080;
    mResolutionInfo.nResolution[1].h = 2220;
    mResolutionInfo.nDSCYSliceSize[1] = 30;
    mResolutionInfo.nDSCXSliceSize[1] = 1080 / 2;
    mResolutionInfo.nPanelType[1] = PANEL_DSC;
    mResolutionInfo.nResolution[2].w = 720;
    mResolutionInfo.nResolution[2].h = 1480;
    mResolutionInfo.nDSCYSliceSize[2] = 74;
    mResolutionInfo.nDSCXSliceSize[2] = 720;
    mResolutionInfo.nPanelType[2] = PANEL_LEGACY;

    char value[PROPERTY_VALUE_MAX];
    const char *earlyWakeupNodeBase = EARLY_WAKUP_NODE_0_BASE;
    if (getDisplayTypeFromIndex(mIndex) == DisplayType::DISPLAY_SECONDARY &&
        property_get("vendor.display.secondary_early_wakeup_node", value, "") > 0) {
        earlyWakeupNodeBase = value;
    }
    mEarlyWakeupDispFd = fopen(earlyWakeupNodeBase, "w");
    if (mEarlyWakeupDispFd == nullptr)
        ALOGE("open %s failed! %s", earlyWakeupNodeBase, strerror(errno));
    mBrightnessController = std::make_unique<BrightnessController>(
            mIndex, [this]() { mDevice->onRefresh(mDisplayId); },
            [this]() { updatePresentColorConversionInfo(); });
}

ExynosPrimaryDisplay::~ExynosPrimaryDisplay()
{
    if (mEarlyWakeupDispFd) {
        fclose(mEarlyWakeupDispFd);
        mEarlyWakeupDispFd = nullptr;
    }

    if (mDisplayNeedHandleIdleExitOfs.is_open()) {
        mDisplayNeedHandleIdleExitOfs.close();
    }
}

void ExynosPrimaryDisplay::setDDIScalerEnable(int width, int height) {

    if (exynosHWCControl.setDDIScaler == false) return;

    ALOGI("DDISCALER Info : setDDIScalerEnable(w=%d,h=%d)", width, height);
    mNewScaledWidth = width;
    mNewScaledHeight = height;
    mXres = width;
    mYres = height;
}

int ExynosPrimaryDisplay::getDDIScalerMode(int width, int height) {

    if (exynosHWCControl.setDDIScaler == false) return 1;

    // Check if panel support support resolution or not.
    for (uint32_t i=0; i < mResolutionInfo.nNum; i++) {
        if (mResolutionInfo.nResolution[i].w * mResolutionInfo.nResolution[i].h ==
                static_cast<uint32_t>(width * height))
            return i + 1;
    }

    return 1; // WQHD
}

int32_t ExynosPrimaryDisplay::doDisplayConfigInternal(hwc2_config_t config) {
    if (!mPowerModeState.has_value() || (*mPowerModeState != HWC2_POWER_MODE_ON)) {
        mPendActiveConfig = config;
        mConfigRequestState = hwc_request_state_t::SET_CONFIG_STATE_DONE;
        DISPLAY_LOGI("%s:: Pending desired Config: %d", __func__, config);
        return NO_ERROR;
    }
    return ExynosDisplay::doDisplayConfigInternal(config);
}

int32_t ExynosPrimaryDisplay::getActiveConfigInternal(hwc2_config_t *outConfig) {
    if (outConfig && mPendActiveConfig != UINT_MAX) {
        *outConfig = mPendActiveConfig;
        return HWC2_ERROR_NONE;
    }
    return ExynosDisplay::getActiveConfigInternal(outConfig);
}

int32_t ExynosPrimaryDisplay::setActiveConfigInternal(hwc2_config_t config, bool force) {
    hwc2_config_t cur_config;

    getActiveConfigInternal(&cur_config);
    if (cur_config == config) {
        ALOGI("%s:: Same display config is set", __func__);
        return HWC2_ERROR_NONE;
    }
    if (!mPowerModeState.has_value() || (*mPowerModeState != HWC2_POWER_MODE_ON)) {
        mPendActiveConfig = config;
        return HWC2_ERROR_NONE;
    }
    return ExynosDisplay::setActiveConfigInternal(config, force);
}

int32_t ExynosPrimaryDisplay::applyPendingConfig() {
    hwc2_config_t config;

    if (mPendActiveConfig != UINT_MAX) {
        config = mPendActiveConfig;
        mPendActiveConfig = UINT_MAX;
    } else {
        getActiveConfigInternal(&config);
    }

    return ExynosDisplay::setActiveConfigInternal(config, true);
}

int32_t ExynosPrimaryDisplay::setBootDisplayConfig(int32_t config) {
    auto hwcConfig = static_cast<hwc2_config_t>(config);

    const auto &it = mDisplayConfigs.find(hwcConfig);
    if (it == mDisplayConfigs.end()) {
        DISPLAY_LOGE("%s: invalid config %d", __func__, config);
        return HWC2_ERROR_BAD_CONFIG;
    }

    const auto &mode = it->second;
    if (mode.vsyncPeriod == 0)
        return HWC2_ERROR_BAD_CONFIG;

    int refreshRate = round(nsecsPerSec / mode.vsyncPeriod * 0.1f) * 10;
    char modeStr[PROPERTY_VALUE_MAX];
    int ret = snprintf(modeStr, sizeof(modeStr), "%dx%d@%d",
             mode.width, mode.height, refreshRate);
    if (ret <= 0)
        return HWC2_ERROR_BAD_CONFIG;

    ALOGD("%s: mode=%s (%d) vsyncPeriod=%d", __func__, modeStr, config,
            mode.vsyncPeriod);
    ret = property_set(PROPERTY_BOOT_MODE, modeStr);

    return !ret ? HWC2_ERROR_NONE : HWC2_ERROR_BAD_CONFIG;
}

int32_t ExynosPrimaryDisplay::clearBootDisplayConfig() {
    auto ret = property_set(PROPERTY_BOOT_MODE, nullptr);

    ALOGD("%s: clearing boot mode", __func__);
    return !ret ? HWC2_ERROR_NONE : HWC2_ERROR_BAD_CONFIG;
}

int32_t ExynosPrimaryDisplay::getPreferredDisplayConfigInternal(int32_t *outConfig) {
    char modeStr[PROPERTY_VALUE_MAX];
    auto ret = property_get(PROPERTY_BOOT_MODE, modeStr, "");

    if (ret <= 0) {
        return mDisplayInterface->getDefaultModeId(outConfig);
    }

    int width, height;
    int fps = 0;

    ret = sscanf(modeStr, "%dx%d@%d", &width, &height, &fps);
    if ((ret < 3) || !fps) {
        ALOGD("%s: unable to find boot config for mode: %s", __func__, modeStr);
        return HWC2_ERROR_BAD_CONFIG;
    }

    return lookupDisplayConfigs(width, height, fps, outConfig);
}

int32_t ExynosPrimaryDisplay::setPowerOn() {
    ATRACE_CALL();
    updateAppliedActiveConfig(0, 0);
    int ret = NO_ERROR;
    if (mDisplayId != 0 || !mFirstPowerOn) {
        ret = applyPendingConfig();
    }

    if (!mPowerModeState.has_value() || (*mPowerModeState == HWC2_POWER_MODE_OFF)) {
        // check the dynamic recomposition thread by following display
        mDevice->checkDynamicRecompositionThread();
        if (ret) {
            mDisplayInterface->setPowerMode(HWC2_POWER_MODE_ON);
        }
        setGeometryChanged(GEOMETRY_DISPLAY_POWER_ON);
    }

    {
        std::lock_guard<std::mutex> lock(mPowerModeMutex);
        mPowerModeState = HWC2_POWER_MODE_ON;
        if (mNotifyPowerOn) {
            mPowerOnCondition.notify_one();
            mNotifyPowerOn = false;
        }
    }

    if (mFirstPowerOn) {
        firstPowerOn();
    }

    return HWC2_ERROR_NONE;
}

int32_t ExynosPrimaryDisplay::setPowerOff() {
    ATRACE_CALL();

    clearDisplay(true);

    // check the dynamic recomposition thread by following display
    mDevice->checkDynamicRecompositionThread();

    mDisplayInterface->setPowerMode(HWC2_POWER_MODE_OFF);

    {
        std::lock_guard<std::mutex> lock(mPowerModeMutex);
        mPowerModeState = HWC2_POWER_MODE_OFF;
    }

    /* It should be called from validate() when the screen is on */
    mSkipFrame = true;
    setGeometryChanged(GEOMETRY_DISPLAY_POWER_OFF);
    if ((mRenderingState >= RENDERING_STATE_VALIDATED) &&
        (mRenderingState < RENDERING_STATE_PRESENTED))
        closeFencesForSkipFrame(RENDERING_STATE_VALIDATED);
    mRenderingState = RENDERING_STATE_NONE;

    // in the case user turns off screen when LHBM is on
    // TODO: b/236433238 considering a lock for mLhbmOn state
    mLhbmOn = false;
    return HWC2_ERROR_NONE;
}

int32_t ExynosPrimaryDisplay::setPowerDoze(hwc2_power_mode_t mode) {
    ATRACE_CALL();

    if (!mDisplayInterface->isDozeModeAvailable()) {
        return HWC2_ERROR_UNSUPPORTED;
    }

    if (mPowerModeState.has_value() &&
        ((*mPowerModeState == HWC2_POWER_MODE_OFF) || (*mPowerModeState == HWC2_POWER_MODE_ON))) {
        if (mDisplayInterface->setLowPowerMode()) {
            ALOGI("Not support LP mode.");
            return HWC2_ERROR_UNSUPPORTED;
        }
    }

    {
        std::lock_guard<std::mutex> lock(mPowerModeMutex);
        mPowerModeState = mode;
    }

    // LHBM will be disabled in the kernel while entering AOD mode if it's
    // already enabled. Reset the state to avoid the sync problem.
    mBrightnessController->resetLhbmState();
    mLhbmOn = false;

    ExynosDisplay::updateRefreshRateHint();

    return HWC2_ERROR_NONE;
}

int32_t ExynosPrimaryDisplay::setPowerMode(int32_t mode) {
    Mutex::Autolock lock(mDisplayMutex);

    if (mode == static_cast<int32_t>(ext_hwc2_power_mode_t::PAUSE)) {
        mode = HWC2_POWER_MODE_OFF;
        mPauseDisplay = true;
    } else if (mode == static_cast<int32_t>(ext_hwc2_power_mode_t::RESUME)) {
        mode = HWC2_POWER_MODE_ON;
        mPauseDisplay = false;
    }

    if (mPowerModeState.has_value() && (mode == static_cast<int32_t>(mPowerModeState.value()))) {
        ALOGI("Skip power mode transition due to the same power state.");
        return HWC2_ERROR_NONE;
    }

    int fb_blank = (mode != HWC2_POWER_MODE_OFF) ? FB_BLANK_UNBLANK : FB_BLANK_POWERDOWN;
    ALOGD("%s:: FBIOBLANK mode(%d), blank(%d)", __func__, mode, fb_blank);

    if (fb_blank == FB_BLANK_POWERDOWN)
        mDREnable = false;
    else
        mDREnable = mDRDefault;

    switch (mode) {
        case HWC2_POWER_MODE_DOZE_SUSPEND:
        case HWC2_POWER_MODE_DOZE:
            return setPowerDoze(static_cast<hwc2_power_mode_t>(mode));
        case HWC2_POWER_MODE_OFF:
            setPowerOff();
            break;
        case HWC2_POWER_MODE_ON:
            setPowerOn();
            break;
        default:
            return HWC2_ERROR_BAD_PARAMETER;
    }

    ExynosDisplay::updateRefreshRateHint();

    return HWC2_ERROR_NONE;
}

void ExynosPrimaryDisplay::firstPowerOn() {
    SetCurrentPanelGammaSource(DisplayType::DISPLAY_PRIMARY, PanelGammaSource::GAMMA_CALIBRATION);
    mFirstPowerOn = false;
    getDisplayIdleTimerEnabled(mDisplayIdleTimerEnabled);
    initDisplayHandleIdleExit();
}

bool ExynosPrimaryDisplay::getHDRException(ExynosLayer* __unused layer)
{
    return false;
}

void ExynosPrimaryDisplay::initDisplayInterface(uint32_t interfaceType)
{
    if (interfaceType == INTERFACE_TYPE_DRM)
        mDisplayInterface = std::make_unique<ExynosPrimaryDisplayDrmInterfaceModule>((ExynosDisplay *)this);
    else
        LOG_ALWAYS_FATAL("%s::Unknown interface type(%d)",
                __func__, interfaceType);
    mDisplayInterface->init(this);

    mDpuData.init(mMaxWindowNum, mDevice->getSpecialPlaneNum(mDisplayId));
    mLastDpuData.init(mMaxWindowNum, mDevice->getSpecialPlaneNum(mDisplayId));
    ALOGI("window configs size(%zu) rcd configs zie(%zu)", mDpuData.configs.size(),
          mDpuData.rcdConfigs.size());
}

std::string ExynosPrimaryDisplay::getPanelSysfsPath(const DisplayType &type) {
    if ((type < DisplayType::DISPLAY_PRIMARY) || (type >= DisplayType::DISPLAY_MAX)) {
        ALOGE("Invalid display panel type %d", type);
        return {};
    }

    auto iter = panelSysfsPath.find(type);
    if (iter == panelSysfsPath.end()) {
        return {};
    }

    return iter->second;
}

int32_t ExynosPrimaryDisplay::SetCurrentPanelGammaSource(const DisplayType type,
                                                         const PanelGammaSource &source) {
    std::string &&panel_sysfs_path = getPanelSysfsPath(type);
    if (panel_sysfs_path.empty()) {
        return HWC2_ERROR_UNSUPPORTED;
    }

    std::ifstream ifs;
    std::string &&path = panel_sysfs_path + "panel_name";
    ifs.open(path, std::ifstream::in);
    if (!ifs.is_open()) {
        ALOGW("Unable to access panel name path '%s' (%s)", path.c_str(), strerror(errno));
        return HWC2_ERROR_UNSUPPORTED;
    }
    std::string panel_name;
    std::getline(ifs, panel_name);
    ifs.close();

    path = panel_sysfs_path + "serial_number";
    ifs.open(path, std::ifstream::in);
    if (!ifs.is_open()) {
        ALOGW("Unable to access panel id path '%s' (%s)", path.c_str(), strerror(errno));
        return HWC2_ERROR_UNSUPPORTED;
    }
    std::string panel_id;
    std::getline(ifs, panel_id);
    ifs.close();

    std::string gamma_node = panel_sysfs_path + "gamma";
    if (access(gamma_node.c_str(), W_OK)) {
        ALOGW("Unable to access panel gamma calibration node '%s' (%s)", gamma_node.c_str(),
              strerror(errno));
        return HWC2_ERROR_UNSUPPORTED;
    }

    std::string &&gamma_data = "default";
    if (source == PanelGammaSource::GAMMA_CALIBRATION) {
        std::string gamma_cal_file(kDisplayCalFilePath);
        gamma_cal_file.append(kPanelGammaCalFilePrefix)
                .append(1, '_')
                .append(panel_name)
                .append(1, '_')
                .append(panel_id)
                .append(".cal");
        if (access(gamma_cal_file.c_str(), R_OK)) {
            ALOGI("Fail to access `%s` (%s), try golden gamma calibration", gamma_cal_file.c_str(),
                  strerror(errno));
            gamma_cal_file = kDisplayCalFilePath;
            gamma_cal_file.append(kPanelGammaCalFilePrefix)
                    .append(1, '_')
                    .append(panel_name)
                    .append(".cal");
        }
        gamma_data = loadPanelGammaCalibration(gamma_cal_file);
    }

    if (gamma_data.empty()) {
        return HWC2_ERROR_UNSUPPORTED;
    }

    std::ofstream ofs(gamma_node);
    if (!ofs.is_open()) {
        ALOGW("Unable to open gamma node '%s', error = %s", gamma_node.c_str(), strerror(errno));
        return HWC2_ERROR_UNSUPPORTED;
    }
    ofs.write(gamma_data.c_str(), gamma_data.size());
    ofs.close();

    currentPanelGammaSource = source;
    return HWC2_ERROR_NONE;
}

bool ExynosPrimaryDisplay::isLhbmSupported() {
    return mBrightnessController->isLhbmSupported();
}

// This function should be called by other threads (e.g. sensor HAL).
// HWCService can call this function but it should be for test purpose only.
int32_t ExynosPrimaryDisplay::setLhbmState(bool enabled) {
    // NOTE: mLhbmOn could be set to false at any time by setPowerOff in another
    // thread. Make sure no side effect if that happens. Or add lock if we have
    // to when new code is added.
    ATRACE_CALL();
    {
        ATRACE_NAME("wait for power mode on");
        std::unique_lock<std::mutex> lock(mPowerModeMutex);
        if (mPowerModeState != HWC2_POWER_MODE_ON) {
            mNotifyPowerOn = true;
            if (!mPowerOnCondition.wait_for(lock, std::chrono::milliseconds(2000), [this]() {
                    return (mPowerModeState == HWC2_POWER_MODE_ON);
                })) {
                ALOGW("%s(%d) wait for power mode on timeout !", __func__, enabled);
                return TIMED_OUT;
            }
        }
    }

    if (enabled) {
        ATRACE_NAME("wait for peak refresh rate");
        std::unique_lock<std::mutex> lock(mPeakRefreshRateMutex);
        mNotifyPeakRefreshRate = true;
        if (!mPeakRefreshRateCondition.wait_for(lock,
                                                std::chrono::milliseconds(
                                                        kLhbmWaitForPeakRefreshRateMs),
                                                [this]() { return isCurrentPeakRefreshRate(); })) {
            ALOGW("setLhbmState(on) wait for peak refresh rate timeout !");
            return TIMED_OUT;
        }
    }

    if (enabled) {
        setLHBMRefreshRateThrottle(kLhbmRefreshRateThrottleMs);
    }

    bool wasDisabled =
            mBrightnessController
                    ->checkSysfsStatus(BrightnessController::kLocalHbmModeFileNode,
                                       {std::to_string(static_cast<int>(
                                               BrightnessController::LhbmMode::DISABLED))},
                                       0);
    if (!enabled && wasDisabled) {
        ALOGW("lhbm is at DISABLED state, skip disabling");
        return NO_ERROR;
    } else if (enabled && !wasDisabled) {
        requestLhbm(true);
        ALOGI("lhbm is at ENABLING or ENABLED state, re-enable to reset timeout timer");
        return NO_ERROR;
    }

    int64_t lhbmEnablingNanos;
    std::vector<std::string> checkingValue = {
            std::to_string(static_cast<int>(BrightnessController::LhbmMode::DISABLED))};
    if (enabled) {
        checkingValue = {std::to_string(static_cast<int>(BrightnessController::LhbmMode::ENABLING)),
                         std::to_string(static_cast<int>(BrightnessController::LhbmMode::ENABLED))};
        lhbmEnablingNanos = systemTime(SYSTEM_TIME_MONOTONIC);
    }
    requestLhbm(enabled);
    constexpr uint32_t kSysfsCheckTimeoutMs = 500;
    ALOGI("setLhbmState =%d", enabled);
    bool succeed =
            mBrightnessController->checkSysfsStatus(BrightnessController::kLocalHbmModeFileNode,
                                                    checkingValue, ms2ns(kSysfsCheckTimeoutMs));
    if (!succeed) {
        ALOGE("failed to update lhbm mode");
        if (enabled) {
            setLHBMRefreshRateThrottle(0);
        }
        return -ENODEV;
    }

    if (enabled) {
        int64_t lhbmEnablingDoneNanos = systemTime(SYSTEM_TIME_MONOTONIC);
        bool enablingStateSupported = !mFramesToReachLhbmPeakBrightness;
        if (enablingStateSupported) {
            ATRACE_NAME("lhbm_wait_peak_brightness");
            if (!mBrightnessController
                         ->checkSysfsStatus(BrightnessController::kLocalHbmModeFileNode,
                                            {std::to_string(static_cast<int>(
                                                    BrightnessController::LhbmMode::ENABLED))},
                                            ms2ns(kSysfsCheckTimeoutMs))) {
                ALOGE("failed to wait for lhbm becoming effective");
                return -EIO;
            }
        } else {
            // lhbm takes effect at next vblank
            ATRACE_NAME("lhbm_wait_apply");
            if (mDisplayInterface->waitVBlank()) {
                ALOGE("%s failed to wait vblank for taking effect", __func__);
                return -ENODEV;
            }
            ATRACE_NAME("lhbm_wait_peak_brightness");
            for (int32_t i = mFramesToReachLhbmPeakBrightness; i > 0; i--) {
                if (mDisplayInterface->waitVBlank()) {
                    ALOGE("%s failed to wait vblank for peak brightness, %d", __func__, i);
                    return -ENODEV;
                }
            }
        }
        ALOGI("lhbm delay mode: %s, latency(ms): total: %d cmd: %d\n",
              enablingStateSupported ? "poll" : "fixed",
              static_cast<int>((systemTime(SYSTEM_TIME_MONOTONIC) - lhbmEnablingNanos) / 1000000),
              static_cast<int>((lhbmEnablingDoneNanos - lhbmEnablingNanos) / 1000000));
    } else {
        setLHBMRefreshRateThrottle(0);
        // lhbm takes effect at next vblank
        ATRACE_NAME("lhbm_wait_apply");
        if (mDisplayInterface->waitVBlank()) {
            ALOGE("%s failed to wait vblank for taking effect", __func__);
            return -ENODEV;
        }
    }

    mLhbmOn = enabled;
    if (!mPowerModeState.has_value() || (*mPowerModeState == HWC2_POWER_MODE_OFF && mLhbmOn)) {
        mLhbmOn = false;
        ALOGE("%s power off during request lhbm on", __func__);
        return -EINVAL;
    }
    return NO_ERROR;
}

bool ExynosPrimaryDisplay::getLhbmState() {
    return mLhbmOn;
}

void ExynosPrimaryDisplay::setLHBMRefreshRateThrottle(const uint32_t delayMs) {
    ATRACE_CALL();

    if (delayMs) {
        // make new throttle take effect
        mLastRefreshRateAppliedNanos = systemTime(SYSTEM_TIME_MONOTONIC);
    }

    setRefreshRateThrottleNanos(std::chrono::duration_cast<std::chrono::nanoseconds>(
                                        std::chrono::milliseconds(delayMs))
                                        .count(),
                                VrrThrottleRequester::LHBM);
}

void ExynosPrimaryDisplay::setEarlyWakeupDisplay() {
    if (mEarlyWakeupDispFd) {
        writeFileNode(mEarlyWakeupDispFd, 1);
    }
}

void ExynosPrimaryDisplay::setExpectedPresentTime(uint64_t timestamp) {
    mExpectedPresentTime.store(timestamp);
}

uint64_t ExynosPrimaryDisplay::getPendingExpectedPresentTime() {
    if (mExpectedPresentTime.is_dirty()) {
        return mExpectedPresentTime.get();
    }

    return 0;
}

void ExynosPrimaryDisplay::applyExpectedPresentTime() {
    mExpectedPresentTime.clear_dirty();
}

int32_t ExynosPrimaryDisplay::setDisplayIdleTimer(const int32_t timeoutMs) {
    bool support = false;
    if (getDisplayIdleTimerSupport(support) || support == false) {
        return HWC2_ERROR_UNSUPPORTED;
    }

    if (timeoutMs < 0) {
        return HWC2_ERROR_BAD_PARAMETER;
    }

    if (timeoutMs > 0) {
        setDisplayIdleDelayNanos(std::chrono::duration_cast<std::chrono::nanoseconds>(
                                         std::chrono::milliseconds(timeoutMs))
                                         .count(),
                                 DispIdleTimerRequester::SF);
    }

    bool enabled = (timeoutMs > 0);
    if (enabled != mDisplayIdleTimerEnabled) {
        if (setDisplayIdleTimerEnabled(enabled) == NO_ERROR) {
            mDisplayIdleTimerEnabled = enabled;
        }
    }

    return HWC2_ERROR_NONE;
}

int32_t ExynosPrimaryDisplay::getDisplayIdleTimerEnabled(bool &enabled) {
    bool support = false;
    if (getDisplayIdleTimerSupport(support) || support == false) {
        return HWC2_ERROR_UNSUPPORTED;
    }

    const std::string path = getPanelSysfsPath(getDisplayTypeFromIndex(mIndex)) + "panel_idle";
    std::ifstream ifs(path);
    if (!ifs.is_open()) {
        ALOGW("%s() unable to open node '%s', error = %s", __func__, path.c_str(), strerror(errno));
        return errno;
    } else {
        std::string panel_idle;
        std::getline(ifs, panel_idle);
        ifs.close();
        enabled = (panel_idle == "1");
        ALOGI("%s() get panel_idle(%d) from the sysfs node", __func__, enabled);
    }
    return NO_ERROR;
}

int32_t ExynosPrimaryDisplay::setDisplayIdleTimerEnabled(const bool enabled) {
    const std::string path = getPanelSysfsPath(getDisplayTypeFromIndex(mIndex)) + "panel_idle";
    std::ofstream ofs(path);
    if (!ofs.is_open()) {
        ALOGW("%s() unable to open node '%s', error = %s", __func__, path.c_str(), strerror(errno));
        return errno;
    } else {
        ofs << enabled;
        ofs.close();
        ALOGI("%s() writes panel_idle(%d) to the sysfs node", __func__, enabled);
    }
    return NO_ERROR;
}

int32_t ExynosPrimaryDisplay::setDisplayIdleDelayNanos(const int32_t delayNanos,
                                                       const DispIdleTimerRequester requester) {
    std::lock_guard<std::mutex> lock(mDisplayIdleDelayMutex);

    int64_t maxDelayNanos = 0;
    mDisplayIdleTimerNanos[toUnderlying(requester)] = delayNanos;
    for (uint32_t i = 0; i < toUnderlying(DispIdleTimerRequester::MAX); i++) {
        if (mDisplayIdleTimerNanos[i] > maxDelayNanos) {
            maxDelayNanos = mDisplayIdleTimerNanos[i];
        }
    }

    if (mDisplayIdleDelayNanos == maxDelayNanos) {
        return NO_ERROR;
    }

    mDisplayIdleDelayNanos = maxDelayNanos;

    const int32_t displayIdleDelayMs = std::chrono::duration_cast<std::chrono::milliseconds>(
                                               std::chrono::nanoseconds(mDisplayIdleDelayNanos))
                                               .count();
    const std::string path = getPanelSysfsPath(DisplayType::DISPLAY_PRIMARY) + "idle_delay_ms";
    std::ofstream ofs(path);
    if (!ofs.is_open()) {
        ALOGW("%s() unable to open node '%s', error = %s", __func__, path.c_str(), strerror(errno));
        return errno;
    } else {
        ofs << displayIdleDelayMs;
        ALOGI("%s() writes idle_delay_ms(%d) to the sysfs node (0x%x)", __func__,
              displayIdleDelayMs, ofs.rdstate());
        ofs.close();
    }
    return NO_ERROR;
}

void ExynosPrimaryDisplay::initDisplayHandleIdleExit() {
    if (bool support; getDisplayIdleTimerSupport(support) || support == false) {
        return;
    }

    const std::string path =
            getPanelSysfsPath(getDisplayTypeFromIndex(mIndex)) + "panel_need_handle_idle_exit";
    mDisplayNeedHandleIdleExitOfs.open(path, std::ofstream::out);
    if (!mDisplayNeedHandleIdleExitOfs.is_open()) {
        ALOGI("%s() '%s' doesn't exist(%s)", __func__, path.c_str(), strerror(errno));
    }

    setDisplayNeedHandleIdleExit(false, true);
}

void ExynosPrimaryDisplay::setDisplayNeedHandleIdleExit(const bool needed, const bool force) {
    if (!mDisplayNeedHandleIdleExitOfs.is_open()) {
        return;
    }

    if (needed == mDisplayNeedHandleIdleExit && !force) {
        return;
    }

    mDisplayNeedHandleIdleExitOfs << needed;
    if (mDisplayNeedHandleIdleExitOfs.fail()) {
        ALOGW("%s() failed to write panel_need_handle_idle_exit(%d) to sysfs node %s", __func__,
              needed, strerror(errno));
        return;
    }

    mDisplayNeedHandleIdleExitOfs.flush();
    if (mDisplayNeedHandleIdleExitOfs.fail()) {
        ALOGW("%s() failed to flush panel_need_handle_idle_exit(%d) to sysfs node %s", __func__,
              needed, strerror(errno));
        return;
    }

    ALOGI("%s() writes panel_need_handle_idle_exit(%d) to sysfs node", __func__, needed);
    mDisplayNeedHandleIdleExit = needed;
}

void ExynosPrimaryDisplay::handleDisplayIdleEnter(const uint32_t idleTeRefreshRate) {
    Mutex::Autolock lock(mDisplayMutex);
    uint32_t btsRefreshRate = getBtsRefreshRate();
    if (idleTeRefreshRate <= btsRefreshRate) {
        return;
    }

    bool needed = false;
    for (size_t i = 0; i < mLayers.size(); i++) {
        if (mLayers[i]->mOtfMPP && mLayers[i]->mM2mMPP == nullptr &&
            !mLayers[i]->checkBtsCap(idleTeRefreshRate)) {
            needed = true;
            break;
        }
    }

    setDisplayNeedHandleIdleExit(needed, false);
}

int ExynosPrimaryDisplay::setMinIdleRefreshRate(const int fps) {
    mMinIdleRefreshRate = fps;

    const std::string path = getPanelSysfsPath(getDisplayTypeFromIndex(mIndex)) + "min_vrefresh";
    std::ofstream ofs(path);
    if (!ofs.is_open()) {
        ALOGW("Unable to open node '%s', error = %s", path.c_str(), strerror(errno));
        return errno;
    } else {
        ofs << mMinIdleRefreshRate;
        ofs.close();
        ALOGI("ExynosPrimaryDisplay::%s() writes min_vrefresh(%d) to the sysfs node", __func__,
              fps);
    }
    return NO_ERROR;
}

int ExynosPrimaryDisplay::setRefreshRateThrottleNanos(const int64_t delayNanos,
                                                      const VrrThrottleRequester requester) {
    ALOGI("%s() requester(%u) set delay to %" PRId64 "ns", __func__, toUnderlying(requester),
          delayNanos);
    if (delayNanos < 0) {
        ALOGW("%s() set invalid delay(%" PRId64 ")", __func__, delayNanos);
        return BAD_VALUE;
    }

    std::lock_guard<std::mutex> lock(mIdleRefreshRateThrottleMutex);

    int64_t maxDelayNanos = 0;
    mVrrThrottleNanos[toUnderlying(requester)] = delayNanos;
    for (uint32_t i = 0; i < toUnderlying(VrrThrottleRequester::MAX); i++) {
        if (mVrrThrottleNanos[i] > maxDelayNanos) {
            maxDelayNanos = mVrrThrottleNanos[i];
        }
    }

    if (mRefreshRateDelayNanos == maxDelayNanos) {
        return NO_ERROR;
    }

    mRefreshRateDelayNanos = maxDelayNanos;

    return setDisplayIdleDelayNanos(mRefreshRateDelayNanos, DispIdleTimerRequester::VRR_THROTTLE);
}

void ExynosPrimaryDisplay::dump(String8 &result) {
    ExynosDisplay::dump(result);
    result.appendFormat("Display idle timer: %s\n",
                        (mDisplayIdleTimerEnabled) ? "enabled" : "disabled");
    for (uint32_t i = 0; i < toUnderlying(DispIdleTimerRequester::MAX); i++) {
        result.appendFormat("\t[%u] vote to %" PRId64 " ns\n", i, mDisplayIdleTimerNanos[i]);
    }
    result.appendFormat("Min idle refresh rate: %d\n", mMinIdleRefreshRate);
    result.appendFormat("Refresh rate delay: %" PRId64 " ns\n", mRefreshRateDelayNanos);
    for (uint32_t i = 0; i < toUnderlying(VrrThrottleRequester::MAX); i++) {
        result.appendFormat("\t[%u] vote to %" PRId64 " ns\n", i, mVrrThrottleNanos[i]);
    }
    result.appendFormat("\n");
}

void ExynosPrimaryDisplay::calculateTimeline(
        hwc2_config_t config, hwc_vsync_period_change_constraints_t *vsyncPeriodChangeConstraints,
        hwc_vsync_period_change_timeline_t *outTimeline) {
    int64_t desiredUpdateTime = vsyncPeriodChangeConstraints->desiredTimeNanos;
    const int64_t origDesiredUpdateTime = desiredUpdateTime;
    const int64_t threshold = mRefreshRateDelayNanos;
    int64_t lastUpdateDelta = 0;
    int64_t actualChangeTime = 0;
    bool isDelayed = false;

    /* actualChangeTime includes transient duration */
    mDisplayInterface->getVsyncAppliedTime(config, &actualChangeTime);

    outTimeline->refreshRequired = true;

    /* when refresh rate is from high to low */
    if (threshold != 0 && mLastRefreshRateAppliedNanos != 0 &&
        mDisplayConfigs[mActiveConfig].vsyncPeriod < mDisplayConfigs[config].vsyncPeriod) {
        lastUpdateDelta = desiredUpdateTime - mLastRefreshRateAppliedNanos;
        if (lastUpdateDelta < threshold) {
            /* in this case, the active config change needs to be delayed */
            isDelayed = true;
            desiredUpdateTime += threshold - lastUpdateDelta;
        }
    }
    mVsyncPeriodChangeConstraints.desiredTimeNanos = desiredUpdateTime;

    getConfigAppliedTime(mVsyncPeriodChangeConstraints.desiredTimeNanos, actualChangeTime,
                         outTimeline->newVsyncAppliedTimeNanos, outTimeline->refreshTimeNanos);

    if (isDelayed) {
        DISPLAY_LOGD(eDebugDisplayConfig,
                     "requested config : %d(%d)->%d(%d) is delayed! "
                     "delta %" PRId64 ", delay %" PRId64 ", threshold %" PRId64 ", "
                     "desired %" PRId64 "->%" PRId64 ", newVsyncAppliedTimeNanos : %" PRId64
                     ", refreshTimeNanos:%" PRId64,
                     mActiveConfig, mDisplayConfigs[mActiveConfig].vsyncPeriod, config,
                     mDisplayConfigs[config].vsyncPeriod, lastUpdateDelta,
                     threshold - lastUpdateDelta, threshold, origDesiredUpdateTime,
                     mVsyncPeriodChangeConstraints.desiredTimeNanos,
                     outTimeline->newVsyncAppliedTimeNanos, outTimeline->refreshTimeNanos);
    } else {
        DISPLAY_LOGD(eDebugDisplayConfig,
                     "requested config : %d(%d)->%d(%d), "
                     "lastUpdateDelta %" PRId64 ", threshold %" PRId64 ", "
                     "desired %" PRId64 ", newVsyncAppliedTimeNanos : %" PRId64 "",
                     mActiveConfig, mDisplayConfigs[mActiveConfig].vsyncPeriod, config,
                     mDisplayConfigs[config].vsyncPeriod, lastUpdateDelta, threshold,
                     mVsyncPeriodChangeConstraints.desiredTimeNanos,
                     outTimeline->newVsyncAppliedTimeNanos);
    }
}

void ExynosPrimaryDisplay::updateAppliedActiveConfig(const hwc2_config_t newConfig,
                                                     const int64_t ts) {
    if (mAppliedActiveConfig == 0 ||
        getDisplayVsyncPeriodFromConfig(mAppliedActiveConfig) !=
                getDisplayVsyncPeriodFromConfig(newConfig)) {
        DISPLAY_LOGD(eDebugDisplayConfig,
                     "%s mAppliedActiveConfig(%d->%d), mLastRefreshRateAppliedNanos(%" PRIu64
                     " -> %" PRIu64 ")",
                     __func__, mAppliedActiveConfig, newConfig, mLastRefreshRateAppliedNanos, ts);
        mLastRefreshRateAppliedNanos = ts;
    }

    mAppliedActiveConfig = newConfig;
}

void ExynosPrimaryDisplay::checkBtsReassignResource(const uint32_t vsyncPeriod,
                                                    const uint32_t btsVsyncPeriod) {
    ATRACE_CALL();
    uint32_t refreshRate = static_cast<uint32_t>(round(nsecsPerSec / vsyncPeriod * 0.1f) * 10);

    if (vsyncPeriod < btsVsyncPeriod) {
        for (size_t i = 0; i < mLayers.size(); i++) {
            if (mLayers[i]->mOtfMPP && mLayers[i]->mM2mMPP == nullptr &&
                !mLayers[i]->checkBtsCap(refreshRate)) {
                mLayers[i]->setGeometryChanged(GEOMETRY_DEVICE_CONFIG_CHANGED);
                break;
            }
        }
    } else if (vsyncPeriod > btsVsyncPeriod) {
        for (size_t i = 0; i < mLayers.size(); i++) {
            if (mLayers[i]->mOtfMPP && mLayers[i]->mM2mMPP) {
                float srcWidth = mLayers[i]->mSourceCrop.right - mLayers[i]->mSourceCrop.left;
                float srcHeight = mLayers[i]->mSourceCrop.bottom - mLayers[i]->mSourceCrop.top;
                float resolution = srcWidth * srcHeight * refreshRate / 1000;
                float ratioVertical = static_cast<float>(mLayers[i]->mDisplayFrame.bottom -
                                                         mLayers[i]->mDisplayFrame.top) /
                        mYres;

                if (mLayers[i]->mOtfMPP->checkDownscaleCap(resolution, ratioVertical)) {
                    mLayers[i]->setGeometryChanged(GEOMETRY_DEVICE_CONFIG_CHANGED);
                    break;
                }
            }
        }
    }
}

bool ExynosPrimaryDisplay::isDbmSupported() {
    return mBrightnessController->isDbmSupported();
}

int32_t ExynosPrimaryDisplay::setDbmState(bool enabled) {
    mBrightnessController->processDimBrightness(enabled);
    return NO_ERROR;
}
