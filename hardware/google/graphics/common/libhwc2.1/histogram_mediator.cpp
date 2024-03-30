/*
 * Copyright (C) 2022 The Android Open Source Project
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
#include "histogram_mediator.h"

histogram::HistogramMediator::HistogramMediator(ExynosDisplay *display) {
    mDisplay = display;
    ExynosDisplayDrmInterface *moduleDisplayInterface =
            static_cast<ExynosDisplayDrmInterface *>(display->mDisplayInterface.get());
    mIDLHistogram = std::make_shared<HistogramReceiver>();

    moduleDisplayInterface->registerHistogramInfo(mIDLHistogram);
    moduleDisplayInterface->getPanelResolution();
}
uint32_t histogram::HistogramMediator::getFrameCount() {
    ExynosDisplayDrmInterface *moduleDisplayInterface =
            static_cast<ExynosDisplayDrmInterface *>(mDisplay->mDisplayInterface.get());
    return moduleDisplayInterface->getFrameCount();
}

bool histogram::HistogramMediator::isDisplayPowerOff() {
    if (!mDisplay->mPowerModeState.has_value() ||
        ((mDisplay->mPowerModeState.value() == HWC2_POWER_MODE_OFF) ||
         (mDisplay->mPowerModeState.value() == HWC2_POWER_MODE_DOZE))) {
        return true;
    }
    return false;
}

bool histogram::HistogramMediator::isSecureContentPresenting() {
    Mutex::Autolock lock(mDisplay->mDRMutex);
    for (uint32_t i = 0; i < mDisplay->mLayers.size(); i++) {
        ExynosLayer *layer = mDisplay->mLayers[i];
        if (layer != NULL && layer->isDrm()) { /* there is some DRM layer */
            return true;
        }
    }
    return false;
}
histogram::HistogramErrorCode histogram::HistogramMediator::requestHist() {
    if (isSecureContentPresenting()) { /* there is some DRM layer */
        return histogram::HistogramErrorCode::DRM_PLAYING;
    }
    ExynosDisplayDrmInterface *moduleDisplayInterface =
            static_cast<ExynosDisplayDrmInterface *>(mDisplay->mDisplayInterface.get());

    if (moduleDisplayInterface->setHistogramControl(
                hidl_histogram_control_t::HISTOGRAM_CONTROL_REQUEST) != NO_ERROR) {
        return histogram::HistogramErrorCode::ENABLE_HIST_ERROR;
    }
    {
        std::unique_lock<std::mutex> lk(mIDLHistogram->mDataCollectingMutex);
        mIDLHistogram->mHistReq_pending = true;
    }
    return histogram::HistogramErrorCode::NONE;
}

histogram::HistogramErrorCode histogram::HistogramMediator::cancelHistRequest() {
    ExynosDisplayDrmInterface *moduleDisplayInterface =
            static_cast<ExynosDisplayDrmInterface *>(mDisplay->mDisplayInterface.get());

    if (moduleDisplayInterface->setHistogramControl(
                hidl_histogram_control_t::HISTOGRAM_CONTROL_CANCEL) != NO_ERROR) {
        return histogram::HistogramErrorCode::DISABLE_HIST_ERROR;
    }
    return histogram::HistogramErrorCode::NONE;
}

void histogram::HistogramMediator::HistogramReceiver::callbackHistogram(char16_t *bin) {
    std::unique_lock<std::mutex> lk(mDataCollectingMutex);
    if (mHistReq_pending == true) {
        std::memcpy(mHistData, bin, HISTOGRAM_BINS_SIZE * sizeof(char16_t));
        mHistReq_pending = false;
    }
    mHistData_cv.notify_all();
}

int histogram::HistogramMediator::calculateThreshold(const RoiRect &roi) {
    int threshold = ((roi.bottom - roi.top) * (roi.right - roi.left)) >> 16;
    return threshold + 1;
}

histogram::HistogramErrorCode histogram::HistogramMediator::setRoiWeightThreshold(
        const RoiRect roi, const Weight weight, const HistogramPos pos) {
    int threshold = calculateThreshold(roi);
    mIDLHistogram->setHistogramROI((uint16_t)roi.left, (uint16_t)roi.top,
                                   (uint16_t)(roi.right - roi.left),
                                   (uint16_t)(roi.bottom - roi.top));
    mIDLHistogram->setHistogramWeights(weight.weightR, weight.weightG, weight.weightB);
    mIDLHistogram->setHistogramThreshold(threshold);
    mIDLHistogram->setHistogramPos(pos);

    return histogram::HistogramErrorCode::NONE;
}

histogram::HistogramErrorCode histogram::HistogramMediator::collectRoiLuma(
        std::vector<char16_t> *buf) {
    std::unique_lock<std::mutex> lk(mIDLHistogram->mDataCollectingMutex);

    mIDLHistogram->mHistData_cv.wait_for(lk, std::chrono::milliseconds(50), [this]() {
        return (!isDisplayPowerOff() && !mIDLHistogram->mHistReq_pending);
    });
    if (mIDLHistogram->mHistReq_pending == false) setSampleFrameCounter(getFrameCount());
    buf->assign(mIDLHistogram->mHistData, mIDLHistogram->mHistData + HISTOGRAM_BINS_SIZE);

    return histogram::HistogramErrorCode::NONE;
}

histogram::RoiRect histogram::HistogramMediator::calRoi(RoiRect roi) {
    RoiRect roi_return = {-1, -1, -1, -1};
    ExynosDisplayDrmInterface *moduleDisplayInterface =
            static_cast<ExynosDisplayDrmInterface *>(mDisplay->mDisplayInterface.get());
    roi_return.left = roi.left * moduleDisplayInterface->getActiveModeHDisplay() /
            moduleDisplayInterface->panelHsize();
    roi_return.top = roi.top * moduleDisplayInterface->getActiveModeVDisplay() /
            moduleDisplayInterface->panelVsize();
    roi_return.right = roi.right * moduleDisplayInterface->getActiveModeHDisplay() /
            moduleDisplayInterface->panelHsize();
    roi_return.bottom = roi.bottom * moduleDisplayInterface->getActiveModeVDisplay() /
            moduleDisplayInterface->panelVsize();
    return roi_return;
}
