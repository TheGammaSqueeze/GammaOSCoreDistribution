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

#include <hardware/hwcomposer_defs.h>
#include "ExynosExternalDisplay.h"
#include "ExynosDevice.h"
#include <errno.h>
#include "ExynosLayer.h"
#include "ExynosHWCHelper.h"
#include "ExynosHWCDebug.h"
#include "ExynosDisplayDrmInterface.h"
#include "ExynosDisplayDrmInterfaceModule.h"
#include <linux/fb.h>

#define SKIP_FRAME_COUNT 3
extern struct exynos_hwc_control exynosHWCControl;

using namespace SOC_VERSION;

ExynosExternalDisplay::ExynosExternalDisplay(uint32_t index, ExynosDevice *device)
    :   ExynosDisplay(index, device)
{
    DISPLAY_LOGD(eDebugExternalDisplay, "");

    mType = HWC_DISPLAY_EXTERNAL;
    mIndex = index;
    mDisplayId = getDisplayId(mType, mIndex);

    mDisplayControl.cursorSupport = true;

    mEnabled = false;
    mBlanked = false;

	mXres = 0;
    mYres = 0;
    mXdpi = 0;
    mYdpi = 0;
    mVsyncPeriod = 0;
    mSkipStartFrame = 0;
    mSkipFrameCount = -1;
    mIsSkipFrame = false;
    mActiveConfigIndex = 0;
    mVirtualDisplayState = 0;

    //TODO : Hard coded currently
    mNumMaxPriorityAllowed = 1;
    mPowerModeState = (hwc2_power_mode_t)HWC_POWER_MODE_OFF;
}

ExynosExternalDisplay::~ExynosExternalDisplay()
{

}

void ExynosExternalDisplay::init()
{

}

void ExynosExternalDisplay::deInit()
{

}

int ExynosExternalDisplay::openExternalDisplay()
{
    DISPLAY_LOGD(eDebugExternalDisplay, "");

    int ret = 0;
    setVsyncEnabledInternal(HWC2_VSYNC_ENABLE);

    mSkipFrameCount = SKIP_FRAME_COUNT;
    mSkipStartFrame = 0;
    mActiveConfigIndex = 0;
    mPlugState = true;

    if (mLayers.size() != 0) {
        mLayers.clear();
    }

    DISPLAY_LOGD(eDebugExternalDisplay, "open fd for External Display(%d)", ret);

    return ret;
}

void ExynosExternalDisplay::closeExternalDisplay()
{
    DISPLAY_LOGD(eDebugExternalDisplay, "");

    setVsyncEnabledInternal(HWC2_VSYNC_DISABLE);

    if (mPowerModeState.has_value() &&
        (*mPowerModeState != (hwc2_power_mode_t)HWC_POWER_MODE_OFF)) {
        if (mDisplayInterface->setPowerMode(HWC_POWER_MODE_OFF) < 0) {
            DISPLAY_LOGE("%s: set powermode ioctl failed errno : %d", __func__, errno);
            return;
        }
    }

    mPowerModeState = (hwc2_power_mode_t)HWC_POWER_MODE_OFF;

    DISPLAY_LOGD(eDebugExternalDisplay, "Close fd for External Display");

    mPlugState = false;
    mEnabled = false;
    mBlanked = false;
    mSkipFrameCount = SKIP_FRAME_COUNT;

    for (size_t i = 0; i < mLayers.size(); i++) {
        ExynosLayer *layer = mLayers[i];
        layer->mAcquireFence = fence_close(layer->mAcquireFence, this, FENCE_TYPE_SRC_ACQUIRE, FENCE_IP_LAYER);
        layer->mReleaseFence = -1;
        layer->mLayerBuffer = NULL;
    }
}

int ExynosExternalDisplay::getDisplayConfigs(uint32_t* outNumConfigs, hwc2_config_t* outConfigs)
{
    DISPLAY_LOGD(eDebugExternalDisplay, "");

    if (!mHpdStatus)
        return -1;
    return mDisplayInterface->getDisplayConfigs(outNumConfigs, outConfigs);
}

int32_t ExynosExternalDisplay::getActiveConfig(hwc2_config_t* outConfig) {
    DISPLAY_LOGD(eDebugExternalDisplay, "");

    if (!mHpdStatus)
        return -1;

    *outConfig = mActiveConfigIndex;

    return HWC2_ERROR_NONE;
}

void ExynosExternalDisplay::hotplug(){
    DISPLAY_LOGD(eDebugExternalDisplay, "");

    mDevice->onHotPlug(mDisplayId, mHpdStatus);
}

bool ExynosExternalDisplay::handleRotate()
{
    // FIXME: HWC2_COMPOSITION_SCREENSHOT is not dfeind in AOSP
    //        HWC guys should fix this.
    if (mSkipStartFrame < SKIP_EXTERNAL_FRAME) {
#if 0
        for (size_t i = 0; i < mLayers.size(); i++) {
            ExynosLayer *layer = mLayers[i];
            if (layer->mCompositionType == HWC2_COMPOSITION_SCREENSHOT)
                layer->mCompositionType = HWC2_COMPOSITION_DEVICE;
        }
#endif
        mIsSkipFrame = false;
        return false;
    }

#if 0
    for (size_t i = 0; i < mLayers.size(); i++) {
        ExynosLayer *layer = mLayers[i];
        if (layer->mCompositionType == HWC2_COMPOSITION_SCREENSHOT) {
            DISPLAY_LOGD(eDebugExternalDisplay, "include rotation animation layer");
            layer->mOverlayInfo = eSkipRotateAnim;
            for (size_t j = 0; j < mLayers.size(); j++) {
                ExynosLayer *skipLayer = mLayers[j];
                skipLayer->mValidateCompositionType = HWC2_COMPOSITION_DEVICE;
            }
            mIsSkipFrame = true;
            return true;
        }
    }
#endif
    mIsSkipFrame = false;
    return false;
}

bool ExynosExternalDisplay::checkRotate()
{
    // FIXME: HWC2_COMPOSITION_SCREENSHOT is not dfeind in AOSP
    //        HWC guys should fix this.
#if 0
    for (size_t i = 0; i < mLayers.size(); i++) {
        ExynosLayer *layer = mLayers[i];

        if (layer->mCompositionType == HWC2_COMPOSITION_SCREENSHOT) {
            return true;
        }
    }
#endif
    return false;
}

int32_t ExynosExternalDisplay::validateDisplay(
        uint32_t* outNumTypes, uint32_t* outNumRequests) {
    Mutex::Autolock lock(mExternalMutex);
    DISPLAY_LOGD(eDebugExternalDisplay, "");

    int32_t ret;
    mSkipFrame = false;

    if (mSkipStartFrame < SKIP_EXTERNAL_FRAME) {
        ALOGI("[ExternalDisplay] %s : setGeometryChanged [%d/%d]", __func__, mSkipStartFrame, SKIP_EXTERNAL_FRAME);
        initDisplay();
        /*
         * geometry should be set before ExynosDisplay::validateDisplay is called
         * not to skip resource assignment
         */
        if (mPlugState)
            setGeometryChanged(GEOMETRY_DEVICE_DISPLAY_ADDED);
        else
            setGeometryChanged(GEOMETRY_DEVICE_DISPLAY_REMOVED);
    }

    if (handleRotate()) {
        if ((ret = mResourceManager->initResourcesState(this)) != NO_ERROR)
            DISPLAY_LOGE("[ExternalDisplay] %s : initResourcesState fail, ret(%d)", __func__, ret);
        mDevice->setGeometryChanged(GEOMETRY_LAYER_UNKNOWN_CHANGED);
        mClientCompositionInfo.initializeInfos(this);
        mExynosCompositionInfo.initializeInfos(this);
        mRenderingState = RENDERING_STATE_VALIDATED;
        return HWC2_ERROR_NONE;
    }

    if (mSkipStartFrame < SKIP_EXTERNAL_FRAME) {
        /*
         * Set mIsSkipFrame before calling ExynosDisplay::validateDisplay()
         * startPostProcessing() that is called by ExynosDisplay::validateDisplay()
         * checks mIsSkipFrame.
         */
        mIsSkipFrame = true;
    }

    ret = ExynosDisplay::validateDisplay(outNumTypes, outNumRequests);

    if (mSkipStartFrame < SKIP_EXTERNAL_FRAME) {
        initDisplay();
        mRenderingState = RENDERING_STATE_VALIDATED;
        for (size_t i = 0; i < mLayers.size(); i++) {
            ExynosLayer *layer = mLayers[i];
            if (layer && (layer->mValidateCompositionType == HWC2_COMPOSITION_DEVICE ||
                layer->mValidateCompositionType == HWC2_COMPOSITION_EXYNOS)) {
                layer->mValidateCompositionType = HWC2_COMPOSITION_CLIENT;
                layer->mReleaseFence = layer->mAcquireFence;
            }
        }
        mSkipStartFrame++;

        ALOGI("[ExternalDisplay] %s : Skip start frame [%d/%d]", __func__, mSkipStartFrame, SKIP_EXTERNAL_FRAME);
    }

    return ret;
}

int32_t ExynosExternalDisplay::canSkipValidate() {

    /*
     * SurfaceFlinger may call vadlidate, present for a few frame
     * even though external display is disconnected.
     * Cammands for primary display can be discarded if validate is skipped
     * in this case. HWC should return error not to skip validate.
     */
    if ((mHpdStatus == false) || (mBlanked == true))
        return SKIP_ERR_DISP_NOT_CONNECTED;

    if ((mSkipStartFrame > (SKIP_EXTERNAL_FRAME - 1)) && (mEnabled == false) &&
        (mPowerModeState.has_value() &&
         (*mPowerModeState == (hwc2_power_mode_t)HWC_POWER_MODE_NORMAL)))
        return SKIP_ERR_DISP_NOT_POWER_ON;

    if (checkRotate() || (mIsSkipFrame) ||
        (mSkipStartFrame < SKIP_EXTERNAL_FRAME))
        return SKIP_ERR_FORCE_VALIDATE;

    return ExynosDisplay::canSkipValidate();
}

int32_t ExynosExternalDisplay::presentDisplay(
    int32_t* outRetireFence)
{
    Mutex::Autolock lock(mExternalMutex);
    DISPLAY_LOGD(eDebugExternalDisplay, "");
    int32_t ret;

    if (mSkipFrame) {
        ALOGI("[%d] presentDisplay is skipped by mSkipFrame", mDisplayId);
        closeFencesForSkipFrame(RENDERING_STATE_PRESENTED);
        setGeometryChanged(GEOMETRY_DISPLAY_FORCE_VALIDATE);
        *outRetireFence = -1;
        for (size_t i=0; i < mLayers.size(); i++) {
            mLayers[i]->mReleaseFence = -1;
        }
        if (mRenderingState == RENDERING_STATE_NONE) {
            ALOGD("\tThis is the first frame after power on");
            ret = HWC2_ERROR_NONE;
        } else {
            ALOGD("\tThis is the second frame after power on");
            ret = HWC2_ERROR_NOT_VALIDATED;
        }
        mRenderingState = RENDERING_STATE_PRESENTED;
        mDevice->onRefresh(mDisplayId);
        return ret;
    }

    if ((mIsSkipFrame) || (mHpdStatus == false) || (mBlanked == true)) {
        if ((exynosHWCControl.skipValidate == true) &&
            ((mRenderingState == RENDERING_STATE_PRESENTED) ||
             (mRenderingState == RENDERING_STATE_NONE))) {

            if (mDevice->canSkipValidate() == false) {
                mRenderingState = RENDERING_STATE_NONE;
                return HWC2_ERROR_NOT_VALIDATED;
            } else {
                DISPLAY_LOGD(eDebugSkipValidate, "validate is skipped");
            }
        }

        *outRetireFence = -1;
        for (size_t i = 0; i < mLayers.size(); i++) {
            ExynosLayer *layer = mLayers[i];
            layer->mAcquireFence = fence_close(layer->mAcquireFence, this,
                    FENCE_TYPE_SRC_ACQUIRE, FENCE_IP_LAYER);
            layer->mReleaseFence = -1;
        }
        mClientCompositionInfo.mAcquireFence =
            fence_close(mClientCompositionInfo.mAcquireFence, this,
                    FENCE_TYPE_SRC_ACQUIRE, FENCE_IP_FB);
        mClientCompositionInfo.mReleaseFence = -1;

        /* this frame is not presented, but mRenderingState is updated to RENDERING_STATE_PRESENTED */
        initDisplay();

        /*
         * Resource assignment information was initialized during skipping frames
         * So resource assignment for the first displayed frame after skpping frames
         * should not be skipped
         */
        setGeometryChanged(GEOMETRY_DISPLAY_FORCE_VALIDATE);

        mDevice->onRefresh(mDisplayId);

        return HWC2_ERROR_NONE;
    }

    ret = ExynosDisplay::presentDisplay(outRetireFence);

    return ret;
}
int32_t ExynosExternalDisplay::setClientTarget(
        buffer_handle_t target,
        int32_t acquireFence, int32_t /*android_dataspace_t*/ dataspace) {
    buffer_handle_t handle = NULL;
    if (target != NULL)
        handle = target;
    if ((mClientCompositionInfo.mHasCompositionLayer == true) &&
        (handle == NULL) &&
        (mClientCompositionInfo.mSkipFlag == false)) {
        /*
         * openExternalDisplay() can be called between validateDisplay and getChangedCompositionTypes.
         * Then getChangedCompositionTypes() returns no layer because openExternalDisplay() clears mLayers.
         * SurfaceFlinger might not change compositionType to HWC2_COMPOSITION_CLIENT.
         * Handle can be NULL in this case. It is not error case.
         */
        if (mSkipStartFrame == 0) {
            if (acquireFence >= 0)
                fence_close(acquireFence, this, FENCE_TYPE_SRC_ACQUIRE, FENCE_IP_FB);
            acquireFence = -1;
            mClientCompositionInfo.setTargetBuffer(this, handle, acquireFence, (android_dataspace)dataspace);
            return NO_ERROR;
        }
    }
    return ExynosDisplay::setClientTarget(target, acquireFence, dataspace);
}

int ExynosExternalDisplay::enable()
{
    ALOGI("[ExternalDisplay] %s +", __func__);

    if (mEnabled)
        return HWC2_ERROR_NONE;

    if (mHpdStatus == false) {
        ALOGI("HPD is not connected");
        return HWC2_ERROR_NONE;
    }

    if (openExternalDisplay() < 0)
        return HWC2_ERROR_UNSUPPORTED;

    if (mDisplayInterface->setPowerMode(HWC_POWER_MODE_NORMAL) < 0){
        DISPLAY_LOGE("set powermode ioctl failed errno : %d", errno);
        return HWC2_ERROR_UNSUPPORTED;
    }

    mEnabled = true;

    ALOGI("[ExternalDisplay] %s -", __func__);

    return HWC2_ERROR_NONE;
}

int ExynosExternalDisplay::disable()
{
    ALOGI("[ExternalDisplay] %s +", __func__);

    if (!mEnabled)
        return HWC2_ERROR_NONE;

    if (mSkipStartFrame > (SKIP_EXTERNAL_FRAME - 1)) {
        clearDisplay(true);
    } else {
        ALOGI("Skip clearDisplay to avoid resource conflict");
    }

    if (mDisplayInterface->setPowerMode(HWC_POWER_MODE_OFF) < 0){
        DISPLAY_LOGE("set powermode ioctl failed errno : %d", errno);
        return HWC2_ERROR_UNSUPPORTED;
    }

    mPowerModeState = (hwc2_power_mode_t)HWC_POWER_MODE_OFF;

    ALOGI("[ExternalDisplay] %s -", __func__);

    return HWC2_ERROR_NONE;
}

int32_t ExynosExternalDisplay::setPowerMode(
        int32_t /*hwc2_power_mode_t*/ mode) {
    Mutex::Autolock lock(mExternalMutex);
    {
        Mutex::Autolock lock(mDisplayMutex);

        /* TODO state check routine should be added */

        int fb_blank = 0;
        int err = 0;
        if (mode == HWC_POWER_MODE_OFF) {
            fb_blank = FB_BLANK_POWERDOWN;
            err = disable();
        } else {
            fb_blank = FB_BLANK_UNBLANK;
            err = enable();
        }

        if (err != 0) {
            DISPLAY_LOGE("set powermode ioctl failed errno : %d", errno);
            return HWC2_ERROR_UNSUPPORTED;
        }

        if (fb_blank == FB_BLANK_POWERDOWN)
            mDREnable = false;
        else if (fb_blank == FB_BLANK_UNBLANK)
            mDREnable = mDRDefault;

        // check the dynamic recomposition thread by following display power status
        mDevice->checkDynamicRecompositionThread();

        mPowerModeState = (hwc2_power_mode_t)mode;

        DISPLAY_LOGD(eDebugExternalDisplay, "%s:: mode(%d), blank(%d)", __func__, mode, fb_blank);

        if (mode == HWC_POWER_MODE_OFF) {
            /* It should be called from validate() when the screen is on */
            mSkipFrame = true;
            setGeometryChanged(GEOMETRY_DISPLAY_POWER_OFF);
            if ((mRenderingState >= RENDERING_STATE_VALIDATED) &&
                (mRenderingState < RENDERING_STATE_PRESENTED))
                closeFencesForSkipFrame(RENDERING_STATE_VALIDATED);
            mRenderingState = RENDERING_STATE_NONE;
        } else {
            setGeometryChanged(GEOMETRY_DISPLAY_POWER_ON);
        }
    }
    return HWC2_ERROR_NONE;
}

int32_t ExynosExternalDisplay::startPostProcessing() {
    if ((mHpdStatus == false) || (mBlanked == true) || mIsSkipFrame) {
        ALOGI("%s:: skip startPostProcessing display(%d) mHpdStatus(%d)",
                __func__, mDisplayId, mHpdStatus);
        return NO_ERROR;
    }
    return ExynosDisplay::startPostProcessing();
}

bool ExynosExternalDisplay::getHDRException(ExynosLayer* __unused layer)
{
    bool ret = false;

    if (mExternalHdrSupported) {
        ret = true;
    }
    return ret;
}

void ExynosExternalDisplay::handleHotplugEvent()
{
    bool hpd_temp = 0;

    if (!mDevice->isCallbackAvailable(HWC2_CALLBACK_HOTPLUG)) return;

    char cablestate_name[MAX_DEV_NAME + 1];
    cablestate_name[MAX_DEV_NAME] = '\0';
    sprintf(cablestate_name, DP_CABLE_STATE_NAME, DP_LINK_NAME);

    int sw_fd = open(cablestate_name, O_RDONLY);
    char val;

    if (sw_fd >= 0) {
        if (read(sw_fd, &val, 1) == 1) {
            if (val == '1')
                hpd_temp = true;
            else if (val == '0')
                hpd_temp = false;
        }
        hwcFdClose(sw_fd);
    }

    {
        Mutex::Autolock lock(mExternalMutex);
        {
            Mutex::Autolock lock(mDisplayMutex);
            mHpdStatus = hpd_temp;
            if (mHpdStatus) {
                if (openExternalDisplay() < 0) {
                    ALOGE("Failed to openExternalDisplay");
                    mHpdStatus = false;
                    return;
                }
            }
            else {
                disable();
                closeExternalDisplay();
            }
            hotplug();
            mDevice->onRefresh(mDisplayId);
        }
    }

    ALOGI("HPD status changed to %s", mHpdStatus ? "enabled" : "disabled");
}

void ExynosExternalDisplay::initDisplayInterface(uint32_t interfaceType)
{
    if (interfaceType == INTERFACE_TYPE_DRM)
        mDisplayInterface = std::make_unique<ExynosExternalDisplayDrmInterfaceModule>((ExynosDisplay *)this);
    else
        LOG_ALWAYS_FATAL("%s::Unknown interface type(%d)",
                __func__, interfaceType);
    mDisplayInterface->init(this);
}
