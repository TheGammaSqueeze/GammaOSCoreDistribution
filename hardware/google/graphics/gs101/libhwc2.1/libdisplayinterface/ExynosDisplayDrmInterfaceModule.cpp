/*
 * Copyright (C) 2020 The Android Open Source Project
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

#include "BrightnessController.h"
#include "ExynosDisplayDrmInterfaceModule.h"
#include "ExynosPrimaryDisplayModule.h"
#include <drm/samsung_drm.h>

using BrightnessRange = BrightnessController::BrightnessRange;

template <typename T, typename M>
int32_t convertDqeMatrixDataToMatrix(T &colorMatrix, M &mat,
                                     uint32_t dimension) {
    if (colorMatrix.coeffs.size() != (dimension * dimension)) {
        HWC_LOGE(nullptr, "Invalid coeff size(%zu)",
                colorMatrix.coeffs.size());
        return -EINVAL;
    }
    for (uint32_t i = 0; i < (dimension * dimension); i++) {
        mat.coeffs[i] = colorMatrix.coeffs[i];
    }

    if (colorMatrix.offsets.size() != dimension) {
        HWC_LOGE(nullptr, "Invalid offset size(%zu)",
                colorMatrix.offsets.size());
        return -EINVAL;
    }
    for (uint32_t i = 0; i < dimension; i++) {
        mat.offsets[i] = colorMatrix.offsets[i];
    }
    return NO_ERROR;
}

using namespace gs101;

/////////////////////////////////////////////////// ExynosDisplayDrmInterfaceModule //////////////////////////////////////////////////////////////////
ExynosDisplayDrmInterfaceModule::ExynosDisplayDrmInterfaceModule(ExynosDisplay *exynosDisplay)
: ExynosDisplayDrmInterface(exynosDisplay)
{
}

ExynosDisplayDrmInterfaceModule::~ExynosDisplayDrmInterfaceModule()
{
}

void ExynosDisplayDrmInterfaceModule::parseBpcEnums(const DrmProperty& property)
{
    const std::vector<std::pair<uint32_t, const char *>> bpcEnums = {
        {static_cast<uint32_t>(BPC_UNSPECIFIED), "Unspecified"},
        {static_cast<uint32_t>(BPC_8), "8bpc"},
        {static_cast<uint32_t>(BPC_10), "10bpc"},
    };

    ALOGD("Init bpc enums");
    DrmEnumParser::parseEnums(property, bpcEnums, mBpcEnums);
    for (auto &e : mBpcEnums) {
        ALOGD("bpc [bpc: %d, drm: %" PRId64 "]", e.first, e.second);
    }
}

int32_t ExynosDisplayDrmInterfaceModule::initDrmDevice(DrmDevice *drmDevice)
{
    int ret = NO_ERROR;
    if ((ret = ExynosDisplayDrmInterface::initDrmDevice(drmDevice)) != NO_ERROR)
        return ret;

    if (isPrimary() == false)
        return ret;

    mOldDqeBlobs.init(drmDevice);

    initOldDppBlobs(drmDevice);
    if (mDrmCrtc->force_bpc_property().id())
        parseBpcEnums(mDrmCrtc->force_bpc_property());

    mOldHistoBlobs.init(drmDevice);

    return ret;
}

void ExynosDisplayDrmInterfaceModule::destroyOldBlobs(
        std::vector<uint32_t> &oldBlobs)
{
    for (auto &blob : oldBlobs) {
        mDrmDevice->DestroyPropertyBlob(blob);
    }
    oldBlobs.clear();
}

int32_t ExynosDisplayDrmInterfaceModule::createCgcBlobFromIDqe(
        const IDisplayColorGS101::IDqe &dqe, uint32_t &blobId)
{
    struct cgc_lut cgc;
    const IDisplayColorGS101::IDqe::CgcData &cgcData = dqe.Cgc();

    if (cgcData.config == nullptr) {
        ALOGE("no CGC config");
        return -EINVAL;
    }

    if ((cgcData.config->r_values.size() != DRM_SAMSUNG_CGC_LUT_REG_CNT) ||
        (cgcData.config->g_values.size() != DRM_SAMSUNG_CGC_LUT_REG_CNT) ||
        (cgcData.config->b_values.size() != DRM_SAMSUNG_CGC_LUT_REG_CNT)) {
        ALOGE("CGC data size is not same (r: %zu, g: %zu: b: %zu)",
                cgcData.config->r_values.size(),
                cgcData.config->g_values.size(),
                cgcData.config->b_values.size());
        return -EINVAL;
    }

    for (uint32_t i = 0; i < DRM_SAMSUNG_CGC_LUT_REG_CNT; i++) {
        cgc.r_values[i] = cgcData.config->r_values[i];
        cgc.g_values[i] = cgcData.config->g_values[i];
        cgc.b_values[i] = cgcData.config->b_values[i];
    }
    int ret = mDrmDevice->CreatePropertyBlob(&cgc, sizeof(cgc_lut), &blobId);
    if (ret) {
        HWC_LOGE(mExynosDisplay, "Failed to create cgc blob %d", ret);
        return ret;
    }
    return NO_ERROR;
}

int32_t ExynosDisplayDrmInterfaceModule::createDegammaLutBlobFromIDqe(
        const IDisplayColorGS101::IDqe &dqe, uint32_t &blobId)
{
    int ret = 0;
    uint64_t lut_size = 0;

    if (dqe.DegammaLut().config == nullptr) {
        ALOGE("no degamma config");
        return -EINVAL;
    }

    std::tie(ret, lut_size) = mDrmCrtc->degamma_lut_size_property().value();
    if (ret < 0) {
         HWC_LOGE(mExynosDisplay, "%s: there is no degamma_lut_size (ret = %d)",
                 __func__, ret);
         return ret;
    }
    if (lut_size != IDisplayColorGS101::IDqe::DegammaLutData::ConfigType::kLutLen) {
        HWC_LOGE(mExynosDisplay, "%s: invalid lut size (%" PRId64 ")",
                __func__, lut_size);
        return -EINVAL;
    }

    struct drm_color_lut color_lut[IDisplayColorGS101::IDqe::DegammaLutData::ConfigType::kLutLen];
    for (uint32_t i = 0; i < lut_size; i++) {
        color_lut[i].red = dqe.DegammaLut().config->values[i];
    }
    ret = mDrmDevice->CreatePropertyBlob(color_lut, sizeof(color_lut), &blobId);
    if (ret) {
        HWC_LOGE(mExynosDisplay, "Failed to create degamma lut blob %d", ret);
        return ret;
    }
    return NO_ERROR;
}

int32_t ExynosDisplayDrmInterfaceModule::createRegammaLutBlobFromIDqe(
        const IDisplayColorGS101::IDqe &dqe, uint32_t &blobId)
{
    int ret = 0;
    uint64_t lut_size = 0;

    if (dqe.RegammaLut().config == nullptr) {
        ALOGE("no regamma config");
        return -EINVAL;
    }

    std::tie(ret, lut_size) = mDrmCrtc->gamma_lut_size_property().value();
    if (ret < 0) {
         HWC_LOGE(mExynosDisplay, "%s: there is no gamma_lut_size (ret = %d)",
                 __func__, ret);
         return ret;
    }
    if (lut_size != IDisplayColorGS101::IDqe::DegammaLutData::ConfigType::kLutLen) {
        HWC_LOGE(mExynosDisplay, "%s: invalid lut size (%" PRId64 ")",
                __func__, lut_size);
        return -EINVAL;
    }

    struct drm_color_lut color_lut[IDisplayColorGS101::IDqe::DegammaLutData::ConfigType::kLutLen];
    for (uint32_t i = 0; i < lut_size; i++) {
        color_lut[i].red = dqe.RegammaLut().config->r_values[i];
        color_lut[i].green = dqe.RegammaLut().config->g_values[i];
        color_lut[i].blue = dqe.RegammaLut().config->b_values[i];
    }
    ret = mDrmDevice->CreatePropertyBlob(color_lut, sizeof(color_lut), &blobId);
    if (ret) {
        HWC_LOGE(mExynosDisplay, "Failed to create gamma lut blob %d", ret);
        return ret;
    }
    return NO_ERROR;
}

int32_t ExynosDisplayDrmInterfaceModule::createGammaMatBlobFromIDqe(
        const IDisplayColorGS101::IDqe &dqe, uint32_t &blobId)
{
    int ret = 0;
    struct exynos_matrix gamma_matrix;
    if ((ret = convertDqeMatrixDataToMatrix(
                    dqe.GammaMatrix().config->matrix_data, gamma_matrix, DRM_SAMSUNG_MATRIX_DIMENS)) != NO_ERROR)
    {
        HWC_LOGE(mExynosDisplay, "Failed to convert gamma matrix");
        return ret;
    }
    ret = mDrmDevice->CreatePropertyBlob(&gamma_matrix, sizeof(gamma_matrix), &blobId);
    if (ret) {
        HWC_LOGE(mExynosDisplay, "Failed to create gamma matrix blob %d", ret);
        return ret;
    }

    return NO_ERROR;
}

int32_t ExynosDisplayDrmInterfaceModule::createLinearMatBlobFromIDqe(
        const IDisplayColorGS101::IDqe &dqe, uint32_t &blobId)
{
    int ret = 0;
    struct exynos_matrix linear_matrix;
    if ((ret = convertDqeMatrixDataToMatrix(
                    dqe.LinearMatrix().config->matrix_data, linear_matrix, DRM_SAMSUNG_MATRIX_DIMENS)) != NO_ERROR)
    {
        HWC_LOGE(mExynosDisplay, "Failed to convert linear matrix");
        return ret;
    }
    ret = mDrmDevice->CreatePropertyBlob(&linear_matrix, sizeof(linear_matrix), &blobId);
    if (ret) {
        HWC_LOGE(mExynosDisplay, "Failed to create linear matrix blob %d", ret);
        return ret;
    }

    return NO_ERROR;
}

int32_t ExynosDisplayDrmInterfaceModule::createDispDitherBlobFromIDqe(
        const IDisplayColorGS101::IDqe &dqe, uint32_t &blobId)
{
    int ret = 0;
    const IDisplayColorGS101::IDqe::DqeControlData& dqeControl = dqe.DqeControl();
    if (dqeControl.config->disp_dither_override == false) {
        blobId = 0;
        return ret;
    }

    ret = mDrmDevice->CreatePropertyBlob((void*)&dqeControl.config->disp_dither_reg,
            sizeof(dqeControl.config->disp_dither_reg), &blobId);
    if (ret) {
        HWC_LOGE(mExynosDisplay, "Failed to create disp dither blob %d", ret);
        return ret;
    }

    return NO_ERROR;
}

int32_t ExynosDisplayDrmInterfaceModule::createCgcDitherBlobFromIDqe(
        const IDisplayColorGS101::IDqe &dqe, uint32_t &blobId)
{
    int ret = 0;
    const IDisplayColorGS101::IDqe::DqeControlData& dqeControl = dqe.DqeControl();
    if (dqeControl.config->cgc_dither_override == false) {
        blobId = 0;
        return ret;
    }

    ret = mDrmDevice->CreatePropertyBlob((void*)&dqeControl.config->cgc_dither_reg,
            sizeof(dqeControl.config->cgc_dither_reg), &blobId);
    if (ret) {
        HWC_LOGE(mExynosDisplay, "Failed to create disp dither blob %d", ret);
        return ret;
    }
    return NO_ERROR;
}

int32_t ExynosDisplayDrmInterfaceModule::createEotfBlobFromIDpp(
        const IDisplayColorGS101::IDpp &dpp, uint32_t &blobId)
{
    struct hdr_eotf_lut eotf_lut;

    if (dpp.EotfLut().config == nullptr) {
        ALOGE("no dpp eotf config");
        return -EINVAL;
    }

    if ((dpp.EotfLut().config->tf_data.posx.size() != DRM_SAMSUNG_HDR_EOTF_LUT_LEN) ||
        (dpp.EotfLut().config->tf_data.posy.size() != DRM_SAMSUNG_HDR_EOTF_LUT_LEN)) {
        HWC_LOGE(mExynosDisplay, "%s: eotf pos size (%zu, %zu)",
                __func__, dpp.EotfLut().config->tf_data.posx.size(),
                dpp.EotfLut().config->tf_data.posy.size());
        return -EINVAL;
    }

    for (uint32_t i = 0; i < DRM_SAMSUNG_HDR_EOTF_LUT_LEN; i++) {
        eotf_lut.posx[i] = dpp.EotfLut().config->tf_data.posx[i];
        eotf_lut.posy[i] = dpp.EotfLut().config->tf_data.posy[i];
    }
    int ret = mDrmDevice->CreatePropertyBlob(&eotf_lut, sizeof(eotf_lut), &blobId);
    if (ret) {
        HWC_LOGE(mExynosDisplay, "Failed to create eotf lut blob %d", ret);
        return ret;
    }
    return NO_ERROR;
}

int32_t ExynosDisplayDrmInterfaceModule::createGmBlobFromIDpp(
        const IDisplayColorGS101::IDpp &dpp, uint32_t &blobId)
{
    int ret = 0;
    struct hdr_gm_data gm_matrix;

    if (dpp.Gm().config == nullptr) {
        ALOGE("no dpp GM config");
        return -EINVAL;
    }

    if ((ret = convertDqeMatrixDataToMatrix(dpp.Gm().config->matrix_data, gm_matrix,
                                            DRM_SAMSUNG_HDR_GM_DIMENS)) != NO_ERROR)
    {
        HWC_LOGE(mExynosDisplay, "Failed to convert gm matrix");
        return ret;
    }
    ret = mDrmDevice->CreatePropertyBlob(&gm_matrix, sizeof(gm_matrix), &blobId);
    if (ret) {
        HWC_LOGE(mExynosDisplay, "Failed to create gm matrix blob %d", ret);
        return ret;
    }
    return NO_ERROR;
}

int32_t ExynosDisplayDrmInterfaceModule::createDtmBlobFromIDpp(
        const IDisplayColorGS101::IDpp &dpp, uint32_t &blobId)
{
    struct hdr_tm_data tm_data;

    if (dpp.Dtm().config == nullptr) {
        ALOGE("no dpp DTM config");
        return -EINVAL;
    }

    if ((dpp.Dtm().config->tf_data.posx.size() != DRM_SAMSUNG_HDR_TM_LUT_LEN) ||
        (dpp.Dtm().config->tf_data.posy.size() != DRM_SAMSUNG_HDR_TM_LUT_LEN)) {
        HWC_LOGE(mExynosDisplay, "%s: dtm pos size (%zu, %zu)",
                __func__, dpp.Dtm().config->tf_data.posx.size(),
                dpp.Dtm().config->tf_data.posy.size());
        return -EINVAL;
    }

    for (uint32_t i = 0; i < DRM_SAMSUNG_HDR_TM_LUT_LEN; i++) {
        tm_data.posx[i] = dpp.Dtm().config->tf_data.posx[i];
        tm_data.posy[i] = dpp.Dtm().config->tf_data.posy[i];
    }

    tm_data.coeff_r = dpp.Dtm().config->coeff_r;
    tm_data.coeff_g = dpp.Dtm().config->coeff_g;
    tm_data.coeff_b = dpp.Dtm().config->coeff_b;
    tm_data.rng_x_min = dpp.Dtm().config->rng_x_min;
    tm_data.rng_x_max = dpp.Dtm().config->rng_x_max;
    tm_data.rng_y_min = dpp.Dtm().config->rng_y_min;
    tm_data.rng_y_max = dpp.Dtm().config->rng_y_max;

    int ret = mDrmDevice->CreatePropertyBlob(&tm_data, sizeof(tm_data), &blobId);
    if (ret) {
        HWC_LOGE(mExynosDisplay, "Failed to create tm_data blob %d", ret);
        return ret;
    }

    return NO_ERROR;
}
int32_t ExynosDisplayDrmInterfaceModule::createOetfBlobFromIDpp(
        const IDisplayColorGS101::IDpp &dpp, uint32_t &blobId)
{
    struct hdr_oetf_lut oetf_lut;

    if (dpp.OetfLut().config == nullptr) {
        ALOGE("no dpp OETF config");
        return -EINVAL;
    }

    if ((dpp.OetfLut().config->tf_data.posx.size() != DRM_SAMSUNG_HDR_OETF_LUT_LEN) ||
        (dpp.OetfLut().config->tf_data.posy.size() != DRM_SAMSUNG_HDR_OETF_LUT_LEN)) {
        HWC_LOGE(mExynosDisplay, "%s: oetf pos size (%zu, %zu)",
                __func__, dpp.OetfLut().config->tf_data.posx.size(),
                dpp.OetfLut().config->tf_data.posy.size());
        return -EINVAL;
    }

    for (uint32_t i = 0; i < DRM_SAMSUNG_HDR_OETF_LUT_LEN; i++) {
        oetf_lut.posx[i] = dpp.OetfLut().config->tf_data.posx[i];
        oetf_lut.posy[i] = dpp.OetfLut().config->tf_data.posy[i];
    }
    int ret = mDrmDevice->CreatePropertyBlob(&oetf_lut, sizeof(oetf_lut), &blobId);
    if (ret) {
        HWC_LOGE(mExynosDisplay, "Failed to create oetf lut blob %d", ret);
        return ret;
    }
    return NO_ERROR;
}

template<typename StageDataType>
int32_t ExynosDisplayDrmInterfaceModule::setDisplayColorBlob(
        const DrmProperty &prop,
        const uint32_t type,
        const StageDataType &stage,
        const IDisplayColorGS101::IDqe &dqe,
        ExynosDisplayDrmInterface::DrmModeAtomicReq &drmReq)
{
    /* dirty bit is valid only if enable is true */
    if (!prop.id())
        return NO_ERROR;
    if (!mForceDisplayColorSetting && stage.enable && !stage.dirty)
        return NO_ERROR;

    int32_t ret = 0;
    uint32_t blobId = 0;

    if (stage.enable) {
        switch (type) {
            case DqeBlobs::CGC:
                ret = createCgcBlobFromIDqe(dqe, blobId);
                break;
            case DqeBlobs::DEGAMMA_LUT:
                ret = createDegammaLutBlobFromIDqe(dqe, blobId);
                break;
            case DqeBlobs::REGAMMA_LUT:
                ret = createRegammaLutBlobFromIDqe(dqe, blobId);
                break;
            case DqeBlobs::GAMMA_MAT:
                ret = createGammaMatBlobFromIDqe(dqe, blobId);
                break;
            case DqeBlobs::LINEAR_MAT:
                ret = createLinearMatBlobFromIDqe(dqe, blobId);
                break;
            case DqeBlobs::DISP_DITHER:
                ret = createDispDitherBlobFromIDqe(dqe, blobId);
                break;
            case DqeBlobs::CGC_DITHER:
                ret = createCgcDitherBlobFromIDqe(dqe, blobId);
                break;
            default:
                ret = -EINVAL;
        }
        if (ret != NO_ERROR) {
            HWC_LOGE(mExynosDisplay, "%s: create blob fail", __func__);
            return ret;
        }
    }

    /* Skip setting when previous and current setting is same with 0 */
    if ((blobId == 0) && (mOldDqeBlobs.getBlob(type) == 0))
        return ret;

    if ((ret = drmReq.atomicAddProperty(mDrmCrtc->id(), prop, blobId)) < 0) {
        HWC_LOGE(mExynosDisplay, "%s: Fail to set property",
                __func__);
        return ret;
    }
    mOldDqeBlobs.addBlob(type, blobId);

    // disp_dither and cgc dither are part of DqeCtrl stage and the notification
    // will be sent after all data in DqeCtrl stage are applied.
    if (type != DqeBlobs::DISP_DITHER && type != DqeBlobs::CGC_DITHER)
        stage.NotifyDataApplied();

    return ret;
}
int32_t ExynosDisplayDrmInterfaceModule::setDisplayColorSetting(
        ExynosDisplayDrmInterface::DrmModeAtomicReq &drmReq)
{
    if (isPrimary() == false)
        return NO_ERROR;
    if (!mForceDisplayColorSetting && !mColorSettingChanged)
        return NO_ERROR;

    ExynosPrimaryDisplayModule* display =
        (ExynosPrimaryDisplayModule*)mExynosDisplay;

    int ret = NO_ERROR;
    const IDisplayColorGS101::IDqe &dqe = display->getDqe();

    if ((mDrmCrtc->cgc_lut_property().id() != 0) &&
        (ret = setDisplayColorBlob(mDrmCrtc->cgc_lut_property(),
                static_cast<uint32_t>(DqeBlobs::CGC),
                dqe.Cgc(), dqe, drmReq) != NO_ERROR)) {
        HWC_LOGE(mExynosDisplay, "%s: set Cgc blob fail", __func__);
        return ret;
    }
    if ((ret = setDisplayColorBlob(mDrmCrtc->degamma_lut_property(),
                static_cast<uint32_t>(DqeBlobs::DEGAMMA_LUT),
                dqe.DegammaLut(), dqe, drmReq) != NO_ERROR)) {
        HWC_LOGE(mExynosDisplay, "%s: set DegammaLut blob fail", __func__);
        return ret;
    }
    if ((ret = setDisplayColorBlob(mDrmCrtc->gamma_lut_property(),
                static_cast<uint32_t>(DqeBlobs::REGAMMA_LUT),
                dqe.RegammaLut(), dqe, drmReq) != NO_ERROR)) {
        HWC_LOGE(mExynosDisplay, "%s: set RegammaLut blob fail", __func__);
        return ret;
    }
    if ((ret = setDisplayColorBlob(mDrmCrtc->gamma_matrix_property(),
                static_cast<uint32_t>(DqeBlobs::GAMMA_MAT),
                dqe.GammaMatrix(), dqe, drmReq) != NO_ERROR)) {
        HWC_LOGE(mExynosDisplay, "%s: set GammaMatrix blob fail", __func__);
        return ret;
    }
    if ((ret = setDisplayColorBlob(mDrmCrtc->linear_matrix_property(),
                static_cast<uint32_t>(DqeBlobs::LINEAR_MAT),
                dqe.LinearMatrix(), dqe, drmReq) != NO_ERROR)) {
        HWC_LOGE(mExynosDisplay, "%s: set LinearMatrix blob fail", __func__);
        return ret;
    }
    if ((ret = setDisplayColorBlob(mDrmCrtc->disp_dither_property(),
                static_cast<uint32_t>(DqeBlobs::DISP_DITHER),
                dqe.DqeControl(), dqe, drmReq) != NO_ERROR)) {
        HWC_LOGE(mExynosDisplay, "%s: set DispDither blob fail", __func__);
        return ret;
    }
    if ((ret = setDisplayColorBlob(mDrmCrtc->cgc_dither_property(),
                static_cast<uint32_t>(DqeBlobs::CGC_DITHER),
                dqe.DqeControl(), dqe, drmReq) != NO_ERROR)) {
        HWC_LOGE(mExynosDisplay, "%s: set CgcDither blob fail", __func__);
        return ret;
    }

    const DrmProperty &prop_force_bpc = mDrmCrtc->force_bpc_property();
    if (prop_force_bpc.id()) {
        uint32_t bpc = static_cast<uint32_t>(BPC_UNSPECIFIED);
        if (dqe.DqeControl().enable) {
            if (dqe.DqeControl().config->force_10bpc)
                bpc = static_cast<uint32_t>(BPC_10);
        }
        auto [bpcEnum, ret] = DrmEnumParser::halToDrmEnum(bpc, mBpcEnums);
        if (ret < 0) {
            HWC_LOGE(mExynosDisplay, "Fail to convert bpc(%d)", bpc);
        } else {
            if ((ret = drmReq.atomicAddProperty(mDrmCrtc->id(), prop_force_bpc,
                            bpcEnum, true)) < 0) {
                HWC_LOGE(mExynosDisplay, "%s: Fail to set force bpc property",
                        __func__);
            }
        }
    }
    dqe.DqeControl().NotifyDataApplied();

    return NO_ERROR;
}

template<typename StageDataType>
int32_t ExynosDisplayDrmInterfaceModule::setPlaneColorBlob(
        const std::unique_ptr<DrmPlane> &plane,
        const DrmProperty &prop,
        const uint32_t type,
        const StageDataType &stage,
        const IDisplayColorGS101::IDpp &dpp,
        const uint32_t dppIndex,
        ExynosDisplayDrmInterface::DrmModeAtomicReq &drmReq,
        bool forceUpdate)
{
    /* dirty bit is valid only if enable is true */
    if (!prop.id() || (stage.enable && !stage.dirty && !forceUpdate))
        return NO_ERROR;

    uint32_t ix = 0;
    for (;ix < mOldDppBlobs.size(); ix++) {
        if (mOldDppBlobs[ix].planeId == plane->id()) {
            break;
        }
    }
    if (ix >= mOldDppBlobs.size()) {
        HWC_LOGE(mExynosDisplay, "%s: could not find plane %d", __func__, plane->id());
        return -EINVAL;
    }
    DppBlobs &oldDppBlobs = mOldDppBlobs[ix];

    int32_t ret = 0;
    uint32_t blobId = 0;

    if (stage.enable) {
        switch (type) {
            case DppBlobs::EOTF:
                ret = createEotfBlobFromIDpp(dpp, blobId);
                break;
            case DppBlobs::GM:
                ret = createGmBlobFromIDpp(dpp, blobId);
                break;
            case DppBlobs::DTM:
                ret = createDtmBlobFromIDpp(dpp, blobId);
                break;
            case DppBlobs::OETF:
                ret = createOetfBlobFromIDpp(dpp, blobId);
                break;
            default:
                ret = -EINVAL;
        }
        if (ret != NO_ERROR) {
            HWC_LOGE(mExynosDisplay, "%s: create blob fail", __func__);
            return ret;
        }
    }

    /* Skip setting when previous and current setting is same with 0 */
    if ((blobId == 0) && (oldDppBlobs.getBlob(type) == 0) && !forceUpdate)
        return ret;

    if ((ret = drmReq.atomicAddProperty(plane->id(), prop, blobId)) < 0) {
        HWC_LOGE(mExynosDisplay, "%s: Fail to set property",
                __func__);
        return ret;
    }

    oldDppBlobs.addBlob(type, blobId);
    stage.NotifyDataApplied();

    return ret;
}

int32_t ExynosDisplayDrmInterfaceModule::setPlaneColorSetting(
        ExynosDisplayDrmInterface::DrmModeAtomicReq &drmReq,
        const std::unique_ptr<DrmPlane> &plane,
        const exynos_win_config_data &config, uint32_t &solidColor)
{
    if ((mColorSettingChanged == false) ||
        (isPrimary() == false))
        return NO_ERROR;

    if ((config.assignedMPP == nullptr) ||
        (config.assignedMPP->mAssignedSources.size() == 0)) {
        HWC_LOGE(mExynosDisplay, "%s:: config's mpp source size is invalid",
                __func__);
        return -EINVAL;
    }
    ExynosMPPSource* mppSource = config.assignedMPP->mAssignedSources[0];
    if (mppSource->mSourceType >= MPP_SOURCE_MAX) {
        HWC_LOGE(mExynosDisplay,
                "%s: invalid mpp source type (%d)", __func__, mppSource->mSourceType);
        return -EINVAL;
    }

    ExynosPrimaryDisplayModule* display = (ExynosPrimaryDisplayModule*)mExynosDisplay;

    /*
     * Color conversion of Client and Exynos composition buffer
     * is already addressed by GLES or G2D. But as of now, 'dim SDR' is only
     * supported by HWC/displaycolor, we need put client composition under
     * control of HWC/displaycolor.
     */
    if (!display->hasDppForLayer(mppSource)) {
        if (mppSource->mSourceType == MPP_SOURCE_LAYER) {
            HWC_LOGE(mExynosDisplay,
                "%s: layer need color conversion but there is no IDpp",
                __func__);
            return -EINVAL;
        } else if (mppSource->mSourceType == MPP_SOURCE_COMPOSITION_TARGET) {
            return NO_ERROR;
        } else {
            HWC_LOGE(mExynosDisplay,
                "%s: invalid mpp source type (%d)", __func__, mppSource->mSourceType);
            return -EINVAL;
        }
    }

    if (mppSource->mSourceType == MPP_SOURCE_LAYER) {
        ExynosLayer* layer = (ExynosLayer*)mppSource;

        /* color conversion was already handled by m2mMPP */
        if ((layer->mM2mMPP != nullptr) &&
            (layer->mSrcImg.dataSpace != layer->mMidImg.dataSpace)) {
            return NO_ERROR;
        }
    }

    const IDisplayColorGS101::IDpp &dpp = display->getDppForLayer(mppSource);
    const uint32_t dppIndex = static_cast<uint32_t>(display->getDppIndexForLayer(mppSource));
    bool planeChanged = display->checkAndSaveLayerPlaneId(mppSource, plane->id());

    auto &color = dpp.SolidColor();
    // exynos_win_config_data.color ARGB
    solidColor = (color.a << 24) | (color.r << 16) | (color.g << 8) | color.b;

    int ret = 0;
    if ((ret = setPlaneColorBlob(plane, plane->eotf_lut_property(),
                static_cast<uint32_t>(DppBlobs::EOTF),
                dpp.EotfLut(), dpp, dppIndex, drmReq, planeChanged) != NO_ERROR)) {
        HWC_LOGE(mExynosDisplay, "%s: dpp[%d] set oetf blob fail",
                __func__, dppIndex);
        return ret;
    }
    if ((ret = setPlaneColorBlob(plane, plane->gammut_matrix_property(),
                static_cast<uint32_t>(DppBlobs::GM),
                dpp.Gm(), dpp, dppIndex, drmReq, planeChanged) != NO_ERROR)) {
        HWC_LOGE(mExynosDisplay, "%s: dpp[%d] set GM blob fail",
                __func__, dppIndex);
        return ret;
    }
    if ((ret = setPlaneColorBlob(plane, plane->tone_mapping_property(),
                static_cast<uint32_t>(DppBlobs::DTM),
                dpp.Dtm(), dpp, dppIndex, drmReq, planeChanged) != NO_ERROR)) {
        HWC_LOGE(mExynosDisplay, "%s: dpp[%d] set DTM blob fail",
                __func__, dppIndex);
        return ret;
    }
    if ((ret = setPlaneColorBlob(plane, plane->oetf_lut_property(),
                static_cast<uint32_t>(DppBlobs::OETF),
                dpp.OetfLut(), dpp, dppIndex, drmReq, planeChanged) != NO_ERROR)) {
        HWC_LOGE(mExynosDisplay, "%s: dpp[%d] set OETF blob fail",
                __func__, dppIndex);
        return ret;
    }

    return 0;
}

ExynosDisplayDrmInterfaceModule::SaveBlob::~SaveBlob()
{
    for (auto &it: blobs) {
        mDrmDevice->DestroyPropertyBlob(it);
    }
    blobs.clear();
}

void ExynosDisplayDrmInterfaceModule::SaveBlob::addBlob(
        uint32_t type, uint32_t blob)
{
    if (type >= blobs.size()) {
        ALOGE("Invalid dqe blop type: %d", type);
        return;
    }
    if (blobs[type] > 0)
        mDrmDevice->DestroyPropertyBlob(blobs[type]);

    blobs[type] = blob;
}

uint32_t ExynosDisplayDrmInterfaceModule::SaveBlob::getBlob(uint32_t type)
{
    if (type >= blobs.size()) {
        ALOGE("Invalid dqe blop type: %d", type);
        return 0;
    }
    return blobs[type];
}

void ExynosDisplayDrmInterfaceModule::getDisplayInfo(
        std::vector<displaycolor::DisplayInfo> &display_info) {
    displaycolor::DisplayInfo primary_display;
    auto &tb = primary_display.brightness_table;
    auto *brightnessTable = mExynosDisplay->mBrightnessController->getBrightnessTable();

    tb.nbm_nits_min = brightnessTable[toUnderlying(BrightnessRange::NORMAL)].mNitsStart;
    tb.nbm_nits_max = brightnessTable[toUnderlying(BrightnessRange::NORMAL)].mNitsEnd;
    tb.nbm_dbv_min = brightnessTable[toUnderlying(BrightnessRange::NORMAL)].mBklStart;
    tb.nbm_dbv_max = brightnessTable[toUnderlying(BrightnessRange::NORMAL)].mBklEnd;

    tb.hbm_nits_min = brightnessTable[toUnderlying(BrightnessRange::HBM)].mNitsStart;
    tb.hbm_nits_max = brightnessTable[toUnderlying(BrightnessRange::HBM)].mNitsEnd;
    tb.hbm_dbv_min = brightnessTable[toUnderlying(BrightnessRange::HBM)].mBklStart;
    tb.hbm_dbv_max = brightnessTable[toUnderlying(BrightnessRange::HBM)].mBklEnd;

    primary_display.panel_name = GetPanelName();
    primary_display.panel_serial = GetPanelSerial();

    display_info.push_back(primary_display);
}

const std::string ExynosDisplayDrmInterfaceModule::GetPanelInfo(const std::string &sysfs_rel,
                                                                char delim) {
    ExynosPrimaryDisplayModule* display = (ExynosPrimaryDisplayModule*)mExynosDisplay;
    const DisplayType type = display->getBuiltInDisplayType();
    const std::string &sysfs = display->getPanelSysfsPath(type);

    if (sysfs.empty()) {
        return "";
    }

    std::string info;
    if (readLineFromFile(sysfs + "/" + sysfs_rel, info, delim) != OK) {
        ALOGE("failed reading %s/%s", sysfs.c_str(), sysfs_rel.c_str());
        return "";
    }

    return info;
}

/* For Histogram */
int32_t ExynosDisplayDrmInterfaceModule::createHistoRoiBlob(uint32_t &blobId) {
    struct histogram_roi histo_roi;

    std::unique_lock<std::mutex> lk((mHistogramInfo->mSetHistInfoMutex));
    histo_roi.start_x = mHistogramInfo->getHistogramROI().start_x;
    histo_roi.start_y = mHistogramInfo->getHistogramROI().start_y;
    histo_roi.hsize = mHistogramInfo->getHistogramROI().hsize;
    histo_roi.vsize = mHistogramInfo->getHistogramROI().vsize;

    int ret = mDrmDevice->CreatePropertyBlob(&histo_roi, sizeof(histo_roi), &blobId);
    if (ret) {
        HWC_LOGE(mExynosDisplay, "Failed to create histogram roi blob %d", ret);
        return ret;
    }

    return NO_ERROR;
}

int32_t ExynosDisplayDrmInterfaceModule::createHistoWeightsBlob(uint32_t &blobId) {
    struct histogram_weights histo_weights;

    std::unique_lock<std::mutex> lk((mHistogramInfo->mSetHistInfoMutex));
    histo_weights.weight_r = mHistogramInfo->getHistogramWeights().weight_r;
    histo_weights.weight_g = mHistogramInfo->getHistogramWeights().weight_g;
    histo_weights.weight_b = mHistogramInfo->getHistogramWeights().weight_b;

    int ret = mDrmDevice->CreatePropertyBlob(&histo_weights, sizeof(histo_weights), &blobId);
    if (ret) {
        HWC_LOGE(mExynosDisplay, "Failed to create histogram weights blob %d", ret);
        return ret;
    }

    return NO_ERROR;
}

int32_t ExynosDisplayDrmInterfaceModule::setDisplayHistoBlob(
        const DrmProperty &prop, const uint32_t type,
        ExynosDisplayDrmInterface::DrmModeAtomicReq &drmReq) {
    if (!prop.id()) return NO_ERROR;

    int32_t ret = NO_ERROR;
    uint32_t blobId = 0;

    switch (type) {
        case HistoBlobs::ROI:
            ret = createHistoRoiBlob(blobId);
            break;
        case HistoBlobs::WEIGHTS:
            ret = createHistoWeightsBlob(blobId);
            break;
        default:
            ret = -EINVAL;
    }
    if (ret != NO_ERROR) {
        HWC_LOGE(mExynosDisplay, "%s: Failed to create blob", __func__);
        return ret;
    }

    /* Skip setting when previous and current setting is same with 0 */
    if ((blobId == 0) && (mOldHistoBlobs.getBlob(type) == 0)) return ret;

    if ((ret = drmReq.atomicAddProperty(mDrmCrtc->id(), prop, blobId)) < 0) {
        HWC_LOGE(mExynosDisplay, "%s: Failed to add property", __func__);
        return ret;
    }
    mOldHistoBlobs.addBlob(type, blobId);

    return ret;
}

int32_t ExynosDisplayDrmInterfaceModule::setDisplayHistogramSetting(
        ExynosDisplayDrmInterface::DrmModeAtomicReq &drmReq) {
    if ((isHistogramInfoRegistered() == false) || (isPrimary() == false)) return NO_ERROR;

    int ret = NO_ERROR;

    if ((ret = setDisplayHistoBlob(mDrmCrtc->histogram_roi_property(),
                                   static_cast<uint32_t>(HistoBlobs::ROI), drmReq) != NO_ERROR)) {
        HWC_LOGE(mExynosDisplay, "%s: Failed to set Histo_ROI blob", __func__);
        return ret;
    }
    if ((ret = setDisplayHistoBlob(mDrmCrtc->histogram_weights_property(),
                                   static_cast<uint32_t>(HistoBlobs::WEIGHTS),
                                   drmReq) != NO_ERROR)) {
        HWC_LOGE(mExynosDisplay, "%s: Failed to set Histo_Weights blob", __func__);
        return ret;
    }

    const DrmProperty &prop_histo_threshold = mDrmCrtc->histogram_threshold_property();
    if (prop_histo_threshold.id()) {
        if ((ret = drmReq.atomicAddProperty(mDrmCrtc->id(), prop_histo_threshold,
                                            (uint64_t)(mHistogramInfo->getHistogramThreshold()),
                                            true)) < 0) {
            HWC_LOGE(mExynosDisplay, "%s: Failed to set histogram thereshold property", __func__);
            return ret;
        }
    }

    return NO_ERROR;
}

int32_t ExynosDisplayDrmInterfaceModule::setHistogramControl(hidl_histogram_control_t control) {
    if ((isHistogramInfoRegistered() == false) || (isPrimary() == false)) return NO_ERROR;

    int ret = NO_ERROR;
    uint32_t crtc_id = mDrmCrtc->id();

    if (control == hidl_histogram_control_t::HISTOGRAM_CONTROL_REQUEST) {
        ret = mDrmDevice->CallVendorIoctl(DRM_IOCTL_EXYNOS_HISTOGRAM_REQUEST, (void *)&crtc_id);
    } else if (control == hidl_histogram_control_t::HISTOGRAM_CONTROL_CANCEL) {
        ret = mDrmDevice->CallVendorIoctl(DRM_IOCTL_EXYNOS_HISTOGRAM_CANCEL, (void *)&crtc_id);
    }

    return ret;
}

int32_t ExynosDisplayDrmInterfaceModule::setHistogramData(void *bin) {
    if (!bin) return -EINVAL;

    /*
     * There are two handling methods.
     * For ContentSampling in HWC_2.3 API, histogram bin needs to be accumulated.
     * For Histogram IDL, histogram bin need to be sent to IDL block.
     */
    if (mHistogramInfo->getHistogramType() == HistogramInfo::HistogramType::HISTOGRAM_HIDL) {
        (mHistogramInfo.get())->callbackHistogram((char16_t *)bin);
    } else {
        /*
         * ContentSampling in HWC2.3 API is not supported
         */
        return -ENOTSUP;
    }

    return NO_ERROR;
}

//////////////////////////////////////////////////// ExynosPrimaryDisplayDrmInterfaceModule //////////////////////////////////////////////////////////////////
ExynosPrimaryDisplayDrmInterfaceModule::ExynosPrimaryDisplayDrmInterfaceModule(ExynosDisplay *exynosDisplay)
: ExynosDisplayDrmInterfaceModule(exynosDisplay)
{
}

ExynosPrimaryDisplayDrmInterfaceModule::~ExynosPrimaryDisplayDrmInterfaceModule()
{
}

//////////////////////////////////////////////////// ExynosExternalDisplayDrmInterfaceModule //////////////////////////////////////////////////////////////////
ExynosExternalDisplayDrmInterfaceModule::ExynosExternalDisplayDrmInterfaceModule(ExynosDisplay *exynosDisplay)
: ExynosDisplayDrmInterfaceModule(exynosDisplay)
{
}

ExynosExternalDisplayDrmInterfaceModule::~ExynosExternalDisplayDrmInterfaceModule()
{
}
