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

#include "ExynosPrimaryDisplayModule.h"

#include <android-base/file.h>
#include <json/reader.h>
#include <json/value.h>

#include <cmath>

#include "BrightnessController.h"
#include "ExynosDisplayDrmInterfaceModule.h"
#include "ExynosHWCDebug.h"

#ifdef FORCE_GPU_COMPOSITION
extern exynos_hwc_control exynosHWCControl;
#endif

mpp_phycal_type_t getMPPTypeFromDPPChannel(uint32_t channel) {

    for (int i=0; i < MAX_DECON_DMA_TYPE; i++){
        if(IDMA_CHANNEL_MAP[i].channel == channel)
            return IDMA_CHANNEL_MAP[i].type;
    }

    return MPP_P_TYPE_MAX;
}

using namespace gs101;

// enable map layerDataMappingInfo comparison in needDisplayColorSetting()
inline bool operator==(const ExynosPrimaryDisplayModule::DisplaySceneInfo::LayerMappingInfo &lm1,
                       const ExynosPrimaryDisplayModule::DisplaySceneInfo::LayerMappingInfo &lm2) {
    return lm1.dppIdx == lm2.dppIdx && lm1.planeId == lm2.planeId;
}

ExynosPrimaryDisplayModule::ExynosPrimaryDisplayModule(uint32_t index, ExynosDevice* device)
      : ExynosPrimaryDisplay(index, device) {
#ifdef FORCE_GPU_COMPOSITION
    exynosHWCControl.forceGpu = true;
#endif
}

ExynosPrimaryDisplayModule::~ExynosPrimaryDisplayModule () {
}

void ExynosPrimaryDisplayModule::usePreDefinedWindow(bool use)
{
#ifdef FIX_BASE_WINDOW_INDEX
    /* Use fixed base window index */
    mBaseWindowIndex = FIX_BASE_WINDOW_INDEX;
    return;
#endif

    if (use) {
        mBaseWindowIndex = PRIMARY_DISP_BASE_WIN[mDevice->mDisplayMode];
        mMaxWindowNum = mDisplayInterface->getMaxWindowNum() - PRIMARY_DISP_BASE_WIN[mDevice->mDisplayMode];
    } else {
        mBaseWindowIndex = 0;
        mMaxWindowNum = mDisplayInterface->getMaxWindowNum();
    }
}

int32_t ExynosPrimaryDisplayModule::validateWinConfigData()
{
    bool flagValidConfig = true;

    if (ExynosDisplay::validateWinConfigData() != NO_ERROR)
        flagValidConfig = false;

    for (size_t i = 0; i < mDpuData.configs.size(); i++) {
        struct exynos_win_config_data &config = mDpuData.configs[i];
        if (config.state == config.WIN_STATE_BUFFER) {
            bool configInvalid = false;
            uint32_t mppType = config.assignedMPP->mPhysicalType;
            if ((config.src.w != config.dst.w) ||
                (config.src.h != config.dst.h)) {
                if ((mppType == MPP_DPP_GF) ||
                    (mppType == MPP_DPP_VG) ||
                    (mppType == MPP_DPP_VGF)) {
                    DISPLAY_LOGE("WIN_CONFIG error: invalid assign id : "
                            "%zu,  s_w : %d, d_w : %d, s_h : %d, d_h : %d, mppType : %d",
                            i, config.src.w, config.dst.w, config.src.h, config.dst.h, mppType);
                    configInvalid = true;
                }
            }
            if (configInvalid) {
                config.state = config.WIN_STATE_DISABLED;
                flagValidConfig = false;
            }
        }
    }
    if (flagValidConfig)
        return NO_ERROR;
    else
        return -EINVAL;
}

void ExynosPrimaryDisplayModule::doPreProcessing() {
    ExynosDisplay::doPreProcessing();

    if (mDevice->checkNonInternalConnection()) {
        mDisplayControl.adjustDisplayFrame = true;
    } else {
        mDisplayControl.adjustDisplayFrame = false;
    }
}

int32_t ExynosPrimaryDisplayModule::getColorModes(
        uint32_t* outNumModes, int32_t* outModes)
{
    IDisplayColorGS101* displayColorInterface = getDisplayColorInterface();
    const DisplayType display = getDisplayTypeFromIndex(mIndex);
    const ColorModesMap colorModeMap = displayColorInterface == nullptr
            ? ColorModesMap()
            : displayColorInterface->ColorModesAndRenderIntents(display);
    ALOGD("%s: size(%zu)", __func__, colorModeMap.size());
    if (outModes == nullptr) {
        *outNumModes = colorModeMap.size();
        return HWC2_ERROR_NONE;
    }
    if (*outNumModes != colorModeMap.size()) {
        DISPLAY_LOGE("%s: Invalid color mode size(%d), It should be(%zu)",
                __func__, *outNumModes, colorModeMap.size());
        return HWC2_ERROR_BAD_PARAMETER;
    }

    uint32_t index = 0;
    for (const auto &it : colorModeMap)
    {
        outModes[index] = static_cast<int32_t>(it.first);
        ALOGD("\tmode[%d] %d", index, outModes[index]);
        index++;
    }

    return HWC2_ERROR_NONE;
}

int32_t ExynosPrimaryDisplayModule::setColorMode(int32_t mode)
{
    ALOGD("%s: mode(%d)", __func__, mode);
    IDisplayColorGS101* displayColorInterface = getDisplayColorInterface();
    const DisplayType display = getDisplayTypeFromIndex(mIndex);
    const ColorModesMap colorModeMap = displayColorInterface == nullptr
            ? ColorModesMap()
            : displayColorInterface->ColorModesAndRenderIntents(display);
    hwc::ColorMode colorMode =
        static_cast<hwc::ColorMode>(mode);
    const auto it = colorModeMap.find(colorMode);
    if (it == colorModeMap.end()) {
        DISPLAY_LOGE("%s: Invalid color mode(%d)", __func__, mode);
        return HWC2_ERROR_BAD_PARAMETER;
    }
    mDisplaySceneInfo.setColorMode(colorMode);

    if (mColorMode != mode)
        setGeometryChanged(GEOMETRY_DISPLAY_COLOR_MODE_CHANGED);
    mColorMode = (android_color_mode_t)mode;

    return HWC2_ERROR_NONE;
}

int32_t ExynosPrimaryDisplayModule::getRenderIntents(int32_t mode,
        uint32_t* outNumIntents, int32_t* outIntents)
{
    IDisplayColorGS101* displayColorInterface = getDisplayColorInterface();
    const DisplayType display = getDisplayTypeFromIndex(mIndex);
    const ColorModesMap colorModeMap = displayColorInterface == nullptr
            ? ColorModesMap()
            : displayColorInterface->ColorModesAndRenderIntents(display);
    ALOGD("%s, size(%zu)", __func__, colorModeMap.size());
    hwc::ColorMode colorMode =
        static_cast<hwc::ColorMode>(mode);
    const auto it = colorModeMap.find(colorMode);
    if (it == colorModeMap.end()) {
        DISPLAY_LOGE("%s: Invalid color mode(%d)", __func__, mode);
        return HWC2_ERROR_BAD_PARAMETER;
    }
    auto &renderIntents = it->second;
    if (outIntents == NULL) {
        *outNumIntents = renderIntents.size();
        ALOGD("\tintent num(%zu)", renderIntents.size());
        return HWC2_ERROR_NONE;
    }
    if (*outNumIntents != renderIntents.size()) {
        DISPLAY_LOGE("%s: Invalid intent size(%d), It should be(%zu)",
                __func__, *outNumIntents, renderIntents.size());
        return HWC2_ERROR_BAD_PARAMETER;
    }

    for (uint32_t i = 0; i < renderIntents.size(); i++)
    {
        outIntents[i] = static_cast<uint32_t>(renderIntents[i]);
        ALOGD("\tintent[%d] %d", i, outIntents[i]);
    }

    return HWC2_ERROR_NONE;
}

int32_t ExynosPrimaryDisplayModule::setColorModeWithRenderIntent(int32_t mode,
        int32_t intent)
{
    IDisplayColorGS101* displayColorInterface = getDisplayColorInterface();
    const DisplayType display = getDisplayTypeFromIndex(mIndex);
    const ColorModesMap colorModeMap = displayColorInterface == nullptr
            ? ColorModesMap()
            : displayColorInterface->ColorModesAndRenderIntents(display);
    hwc::ColorMode colorMode =
        static_cast<hwc::ColorMode>(mode);
    hwc::RenderIntent renderIntent =
        static_cast<hwc::RenderIntent>(intent);

    const auto mode_it = colorModeMap.find(colorMode);
    if (mode_it == colorModeMap.end()) {
        DISPLAY_LOGE("%s: Invalid color mode(%d)", __func__, mode);
        return HWC2_ERROR_BAD_PARAMETER;
    }

    auto &renderIntents = mode_it->second;
    auto intent_it = std::find(renderIntents.begin(),
            renderIntents.end(), renderIntent);
    if (intent_it == renderIntents.end()) {
        DISPLAY_LOGE("%s: Invalid render intent(%d)", __func__, intent);
        return HWC2_ERROR_BAD_PARAMETER;
    }

    mDisplaySceneInfo.setColorMode(colorMode);
    mDisplaySceneInfo.setRenderIntent(renderIntent);

    if (mColorMode != mode) {
        ALOGD("%s: mode(%d), intent(%d)", __func__, mode, intent);
        setGeometryChanged(GEOMETRY_DISPLAY_COLOR_MODE_CHANGED);
    }
    mColorMode = (android_color_mode_t)mode;

    return HWC2_ERROR_NONE;
}

int32_t ExynosPrimaryDisplayModule::setColorTransform(
        const float* matrix, int32_t hint)
{
    if ((hint < HAL_COLOR_TRANSFORM_IDENTITY) ||
        (hint > HAL_COLOR_TRANSFORM_CORRECT_TRITANOPIA))
        return HWC2_ERROR_BAD_PARAMETER;
    ALOGI("%s:: %d, %d", __func__, mColorTransformHint, hint);
    if (mColorTransformHint != hint)
        setGeometryChanged(GEOMETRY_DISPLAY_COLOR_TRANSFORM_CHANGED);
    mColorTransformHint = hint;
#ifdef HWC_SUPPORT_COLOR_TRANSFORM
    mDisplaySceneInfo.setColorTransform(matrix);
#endif
    return HWC2_ERROR_NONE;

}

int32_t ExynosPrimaryDisplayModule::getClientTargetProperty(
        hwc_client_target_property_t* outClientTargetProperty,
        HwcDimmingStage *outDimmingStage) {
    IDisplayColorGS101* displayColorInterface = getDisplayColorInterface();
    if (displayColorInterface == nullptr) {
        ALOGI("%s dc interface not created", __func__);
        return ExynosDisplay::getClientTargetProperty(outClientTargetProperty);
    }

    const DisplayType display = getDisplayTypeFromIndex(mIndex);
    hwc::PixelFormat pixelFormat;
    hwc::Dataspace dataspace;
    bool dimming_linear;
    if (!displayColorInterface->GetBlendingProperty(display, pixelFormat, dataspace,
                                                    dimming_linear)) {
        outClientTargetProperty->pixelFormat = toUnderlying(pixelFormat);
        outClientTargetProperty->dataspace = toUnderlying(dataspace);
        if (outDimmingStage != nullptr)
            *outDimmingStage = dimming_linear
                              ? HwcDimmingStage::DIMMING_LINEAR
                              : HwcDimmingStage::DIMMING_OETF;

        return HWC2_ERROR_NONE;
    }

    ALOGW("%s failed to get property of blending stage", __func__);
    return ExynosDisplay::getClientTargetProperty(outClientTargetProperty);
}

int32_t ExynosPrimaryDisplayModule::setLayersColorData()
{
    int32_t ret = 0;
    uint32_t layerNum = 0;

    // TODO: b/212616164 remove dimSdrRatio
    float dimSdrRatio = mBrightnessController->getSdrDimRatioForInstantHbm();
    for (uint32_t i = 0; i < mLayers.size(); i++)
    {
        ExynosLayer* layer = mLayers[i];

        if (layer->mValidateCompositionType == HWC2_COMPOSITION_CLIENT)
            continue;

        LayerColorData& layerColorData =
            mDisplaySceneInfo.getLayerColorDataInstance(layerNum);

        /* set layer data mapping info */
        if ((ret = mDisplaySceneInfo.setLayerDataMappingInfo(layer, layerNum))
                != NO_ERROR) {
            DISPLAY_LOGE("%s: layer[%d] setLayerDataMappingInfo fail, layerNum(%d)",
                    __func__, i, layerNum);
            return ret;
        }


        if ((ret = mDisplaySceneInfo.setLayerColorData(layerColorData, layer,
                                                       dimSdrRatio)) != NO_ERROR) {
            DISPLAY_LOGE("%s: layer[%d] setLayerColorData fail, layerNum(%d)",
                    __func__, i, layerNum);
            return ret;
        }

        layerNum++;
    }

    if (mClientCompositionInfo.mHasCompositionLayer) {
        LayerColorData& layerColorData =
            mDisplaySceneInfo.getLayerColorDataInstance(layerNum);

        /* set layer data mapping info */
        if ((ret = mDisplaySceneInfo.setLayerDataMappingInfo(&mClientCompositionInfo,
                                                             layerNum)) != NO_ERROR) {
            DISPLAY_LOGE("%s: setLayerDataMappingInfo fail for client composition", __func__);
            return ret;
        }

        if ((ret = mDisplaySceneInfo.setClientCompositionColorData(
                 mClientCompositionInfo, layerColorData, dimSdrRatio)) != NO_ERROR) {
          DISPLAY_LOGE("%s: setClientCompositionColorData fail", __func__);
          return ret;
        }

        layerNum++;
    }

    /* Resize layer_data when layers were destroyed */
    if (layerNum < mDisplaySceneInfo.displayScene.layer_data.size())
        mDisplaySceneInfo.displayScene.layer_data.resize(layerNum);

    return NO_ERROR;
}

bool ExynosPrimaryDisplayModule::hasDppForLayer(ExynosMPPSource* layer)
{
    IDisplayColorGS101* displayColorInterface = getDisplayColorInterface();
    if (displayColorInterface == nullptr) {
        return false;
    }

    if (mDisplaySceneInfo.layerDataMappingInfo.count(layer) == 0)
        return false;

    uint32_t index =  mDisplaySceneInfo.layerDataMappingInfo[layer].dppIdx;
    const DisplayType display = getDisplayTypeFromIndex(mIndex);
    auto size = displayColorInterface->GetPipelineData(display)->Dpp().size();
    if (index >= size) {
        DISPLAY_LOGE("%s: invalid dpp index(%d) dpp size(%zu)", __func__, index, size);
        return false;
    }

    return true;
}

const IDisplayColorGS101::IDpp& ExynosPrimaryDisplayModule::getDppForLayer(ExynosMPPSource* layer)
{
    uint32_t index = mDisplaySceneInfo.layerDataMappingInfo[layer].dppIdx;
    IDisplayColorGS101* displayColorInterface = getDisplayColorInterface();
    const DisplayType display = getDisplayTypeFromIndex(mIndex);
    return displayColorInterface->GetPipelineData(display)->Dpp()[index].get();
}

int32_t ExynosPrimaryDisplayModule::getDppIndexForLayer(ExynosMPPSource* layer)
{
    if (mDisplaySceneInfo.layerDataMappingInfo.count(layer) == 0)
        return -1;
    uint32_t index = mDisplaySceneInfo.layerDataMappingInfo[layer].dppIdx;

    return static_cast<int32_t>(index);
}

int ExynosPrimaryDisplayModule::deliverWinConfigData()
{
    int ret = 0;
    ExynosDisplayDrmInterfaceModule *moduleDisplayInterface =
        (ExynosDisplayDrmInterfaceModule*)(mDisplayInterface.get());
    IDisplayColorGS101* displayColorInterface = getDisplayColorInterface();

    bool forceDisplayColorSetting = false;
    if (!mDisplaySceneInfo.displaySettingDelivered || isForceColorUpdate())
        forceDisplayColorSetting = true;

    setForceColorUpdate(false);

    if (displayColorInterface != nullptr) {
        moduleDisplayInterface->setColorSettingChanged(
            mDisplaySceneInfo.needDisplayColorSetting(),
            forceDisplayColorSetting);
    }

    ret = ExynosDisplay::deliverWinConfigData();

    checkAtcAnimation();

    if (mDpuData.enable_readback &&
       !mDpuData.readback_info.requested_from_service)
        mDisplaySceneInfo.displaySettingDelivered = false;
    else
        mDisplaySceneInfo.displaySettingDelivered = true;

    return ret;
}

LayerColorData& ExynosPrimaryDisplayModule::DisplaySceneInfo::getLayerColorDataInstance(
        uint32_t index)
{
    size_t currentSize = displayScene.layer_data.size();
    if (index >= currentSize) {
        displayScene.layer_data.resize(currentSize+1);
        colorSettingChanged = true;
    }
    return displayScene.layer_data[index];
}

int32_t ExynosPrimaryDisplayModule::DisplaySceneInfo::setLayerDataMappingInfo(
        ExynosMPPSource* layer, uint32_t index)
{
    if (layerDataMappingInfo.count(layer) != 0) {
        ALOGE("layer mapping is already inserted (layer: %p, index:%d)",
                layer, index);
        return -EINVAL;
    }
    // if assigned displaycolor dppIdx changes, do not reuse it (force plane color update).
    uint32_t oldPlaneId = prev_layerDataMappingInfo.count(layer) != 0 &&
                    prev_layerDataMappingInfo[layer].dppIdx == index
            ? prev_layerDataMappingInfo[layer].planeId
            : UINT_MAX;
    layerDataMappingInfo.insert(std::make_pair(layer, LayerMappingInfo{ index, oldPlaneId }));

    return NO_ERROR;
}

void ExynosPrimaryDisplayModule::DisplaySceneInfo::setLayerDataspace(
        LayerColorData& layerColorData,
        hwc::Dataspace dataspace)
{
    if (layerColorData.dataspace != dataspace) {
        colorSettingChanged = true;
        layerColorData.dataspace = dataspace;
    }
}

void ExynosPrimaryDisplayModule::DisplaySceneInfo::disableLayerHdrStaticMetadata(
        LayerColorData& layerColorData)
{
    if (layerColorData.static_metadata.is_valid) {
        colorSettingChanged = true;
        layerColorData.static_metadata.is_valid = false;
    }
}

void ExynosPrimaryDisplayModule::DisplaySceneInfo::setLayerHdrStaticMetadata(
        LayerColorData& layerColorData,
        const ExynosHdrStaticInfo &exynosHdrStaticInfo)
{
    if (layerColorData.static_metadata.is_valid == false) {
        colorSettingChanged = true;
        layerColorData.static_metadata.is_valid = true;
    }

    updateInfoSingleVal(layerColorData.static_metadata.display_red_primary_x,
            exynosHdrStaticInfo.sType1.mR.x);
    updateInfoSingleVal(layerColorData.static_metadata.display_red_primary_y,
            exynosHdrStaticInfo.sType1.mR.y);
    updateInfoSingleVal(layerColorData.static_metadata.display_green_primary_x,
            exynosHdrStaticInfo.sType1.mG.x);
    updateInfoSingleVal(layerColorData.static_metadata.display_green_primary_y,
            exynosHdrStaticInfo.sType1.mG.y);
    updateInfoSingleVal(layerColorData.static_metadata.display_blue_primary_x,
            exynosHdrStaticInfo.sType1.mB.x);
    updateInfoSingleVal(layerColorData.static_metadata.display_blue_primary_y,
            exynosHdrStaticInfo.sType1.mB.y);
    updateInfoSingleVal(layerColorData.static_metadata.white_point_x,
            exynosHdrStaticInfo.sType1.mW.x);
    updateInfoSingleVal(layerColorData.static_metadata.white_point_y,
            exynosHdrStaticInfo.sType1.mW.y);
    updateInfoSingleVal(layerColorData.static_metadata.max_luminance,
            exynosHdrStaticInfo.sType1.mMaxDisplayLuminance);
    updateInfoSingleVal(layerColorData.static_metadata.min_luminance,
            exynosHdrStaticInfo.sType1.mMinDisplayLuminance);
    updateInfoSingleVal(layerColorData.static_metadata.max_content_light_level,
            exynosHdrStaticInfo.sType1.mMaxContentLightLevel);
    updateInfoSingleVal(
            layerColorData.static_metadata.max_frame_average_light_level,
            exynosHdrStaticInfo.sType1.mMaxFrameAverageLightLevel);
}

void ExynosPrimaryDisplayModule::DisplaySceneInfo::setLayerColorTransform(
        LayerColorData& layerColorData,
        std::array<float, TRANSFORM_MAT_SIZE> &matrix)
{
    updateInfoSingleVal(layerColorData.matrix, matrix);
}

void ExynosPrimaryDisplayModule::DisplaySceneInfo::disableLayerHdrDynamicMetadata(
        LayerColorData& layerColorData)
{
    if (layerColorData.dynamic_metadata.is_valid) {
        colorSettingChanged = true;
        layerColorData.dynamic_metadata.is_valid = false;
    }
}

void ExynosPrimaryDisplayModule::DisplaySceneInfo::setLayerHdrDynamicMetadata(
        LayerColorData& layerColorData,
        const ExynosHdrDynamicInfo &exynosHdrDynamicInfo)
{
    if (layerColorData.dynamic_metadata.is_valid == false) {
        colorSettingChanged = true;
        layerColorData.dynamic_metadata.is_valid = true;
    }
    updateInfoSingleVal(layerColorData.dynamic_metadata.display_maximum_luminance,
            exynosHdrDynamicInfo.data.display_maximum_luminance);

    if (!std::equal(layerColorData.dynamic_metadata.maxscl.begin(),
                layerColorData.dynamic_metadata.maxscl.end(),
                exynosHdrDynamicInfo.data.maxscl)) {
        colorSettingChanged = true;
        for (uint32_t i = 0 ; i < layerColorData.dynamic_metadata.maxscl.size(); i++) {
            layerColorData.dynamic_metadata.maxscl[i] =
                exynosHdrDynamicInfo.data.maxscl[i];
        }
    }
    static constexpr uint32_t DYNAMIC_META_DAT_SIZE = 15;

    updateInfoVectorVal(layerColorData.dynamic_metadata.maxrgb_percentages,
            exynosHdrDynamicInfo.data.maxrgb_percentages,
            DYNAMIC_META_DAT_SIZE);
    updateInfoVectorVal(layerColorData.dynamic_metadata.maxrgb_percentiles,
            exynosHdrDynamicInfo.data.maxrgb_percentiles,
            DYNAMIC_META_DAT_SIZE);
    updateInfoSingleVal(layerColorData.dynamic_metadata.tm_flag,
            exynosHdrDynamicInfo.data.tone_mapping.tone_mapping_flag);
    updateInfoSingleVal(layerColorData.dynamic_metadata.tm_knee_x,
            exynosHdrDynamicInfo.data.tone_mapping.knee_point_x);
    updateInfoSingleVal(layerColorData.dynamic_metadata.tm_knee_y,
            exynosHdrDynamicInfo.data.tone_mapping.knee_point_y);
    updateInfoVectorVal(layerColorData.dynamic_metadata.bezier_curve_anchors,
            exynosHdrDynamicInfo.data.tone_mapping.bezier_curve_anchors,
            DYNAMIC_META_DAT_SIZE);
}

int32_t ExynosPrimaryDisplayModule::DisplaySceneInfo::setClientCompositionColorData(
        const ExynosCompositionInfo &clientCompositionInfo, LayerColorData& layerData,
        float dimSdrRatio)
{
    layerData.dim_ratio = 1.0f;
    setLayerDataspace(layerData,
                      static_cast<hwc::Dataspace>(clientCompositionInfo.mDataSpace));
    disableLayerHdrStaticMetadata(layerData);
    disableLayerHdrDynamicMetadata(layerData);

    if (dimSdrRatio != 1.0) {
        std::array<float, TRANSFORM_MAT_SIZE> scaleMatrix = {
            dimSdrRatio, 0.0, 0.0, 0.0,
            0.0, dimSdrRatio, 0.0, 0.0,
            0.0, 0.0, dimSdrRatio, 0.0,
            0.0, 0.0, 0.0, 1.0
        };
        setLayerColorTransform(layerData, scaleMatrix);
    } else {
        static std::array<float, TRANSFORM_MAT_SIZE> defaultMatrix {
            1.0, 0.0, 0.0, 0.0,
            0.0, 1.0, 0.0, 0.0,
            0.0, 0.0, 1.0, 0.0,
            0.0, 0.0, 0.0, 1.0
        };
        setLayerColorTransform(layerData, defaultMatrix);
    }

    return NO_ERROR;
}

int32_t ExynosPrimaryDisplayModule::DisplaySceneInfo::setLayerColorData(
        LayerColorData& layerData, ExynosLayer* layer, float dimSdrRatio)
{
    layerData.is_solid_color_layer = layer->isDimLayer();
    layerData.solid_color.r = layer->mColor.r;
    layerData.solid_color.g = layer->mColor.g;
    layerData.solid_color.b = layer->mColor.b;
    layerData.solid_color.a = layer->mColor.a;
    layerData.dim_ratio = layer->mPreprocessedInfo.sdrDimRatio;
    setLayerDataspace(layerData,
            static_cast<hwc::Dataspace>(layer->mDataSpace));
    if (layer->mIsHdrLayer && layer->getMetaParcel() != nullptr) {
        if (layer->getMetaParcel()->eType & VIDEO_INFO_TYPE_HDR_STATIC)
            setLayerHdrStaticMetadata(layerData, layer->getMetaParcel()->sHdrStaticInfo);
        else
            disableLayerHdrStaticMetadata(layerData);

        if (layer->getMetaParcel()->eType & VIDEO_INFO_TYPE_HDR_DYNAMIC)
            setLayerHdrDynamicMetadata(layerData, layer->getMetaParcel()->sHdrDynamicInfo);
        else
            disableLayerHdrDynamicMetadata(layerData);
    } else {
        disableLayerHdrStaticMetadata(layerData);
        disableLayerHdrDynamicMetadata(layerData);
    }

    static std::array<float, TRANSFORM_MAT_SIZE> defaultMatrix {
        1.0, 0.0, 0.0, 0.0,
        0.0, 1.0, 0.0, 0.0,
        0.0, 0.0, 1.0, 0.0,
        0.0, 0.0, 0.0, 1.0
    };

    if (dimSdrRatio == 1.0 || layer->mIsHdrLayer) {
        if (layer->mLayerColorTransform.enable)
            setLayerColorTransform(layerData,
                    layer->mLayerColorTransform.mat);
        else
            setLayerColorTransform(layerData,
                    defaultMatrix);
    } else {
        if (layer->mLayerColorTransform.enable) {
            std::array<float, TRANSFORM_MAT_SIZE> scaleMatrix =
                layer->mLayerColorTransform.mat;

            // scale coeffs
            scaleMatrix[0] *= dimSdrRatio;
            scaleMatrix[1] *= dimSdrRatio;
            scaleMatrix[2] *= dimSdrRatio;
            scaleMatrix[4] *= dimSdrRatio;
            scaleMatrix[5] *= dimSdrRatio;
            scaleMatrix[6] *= dimSdrRatio;
            scaleMatrix[8] *= dimSdrRatio;
            scaleMatrix[9] *= dimSdrRatio;
            scaleMatrix[10] *= dimSdrRatio;

            // scale offsets
            scaleMatrix[12] *= dimSdrRatio;
            scaleMatrix[13] *= dimSdrRatio;
            scaleMatrix[14] *= dimSdrRatio;

            setLayerColorTransform(layerData, scaleMatrix);
        } else {
            std::array<float, TRANSFORM_MAT_SIZE> scaleMatrix = {
                dimSdrRatio, 0.0, 0.0, 0.0,
                0.0, dimSdrRatio, 0.0, 0.0,
                0.0, 0.0, dimSdrRatio, 0.0,
                0.0, 0.0, 0.0, 1.0
            };

            setLayerColorTransform(layerData, scaleMatrix);
        }
    }

    return NO_ERROR;
}

int32_t ExynosPrimaryDisplayModule::updateColorConversionInfo()
{
    int ret = 0;
    IDisplayColorGS101* displayColorInterface = getDisplayColorInterface();
    if (displayColorInterface == nullptr) {
        return ret;
    }

    /* clear flag and layer mapping info before setting */
    mDisplaySceneInfo.reset();

    if ((ret = setLayersColorData()) != NO_ERROR)
        return ret;

    mDisplaySceneInfo.displayScene.bm = mBrightnessController->isGhbmOn()
            ? displaycolor::BrightnessMode::BM_HBM
            : displaycolor::BrightnessMode::BM_NOMINAL;

    mDisplaySceneInfo.displayScene.force_hdr = mBrightnessController->isDimSdr();
    mDisplaySceneInfo.displayScene.lhbm_on = mBrightnessController->isLhbmOn();
    mDisplaySceneInfo.displayScene.hdr_layer_state = mBrightnessController->getHdrLayerState();
    mDisplaySceneInfo.displayScene.dbv = mBrightnessController->getBrightnessLevel();

    if (hwcCheckDebugMessages(eDebugColorManagement))
        mDisplaySceneInfo.printDisplayScene();

    const DisplayType display = getDisplayTypeFromIndex(mIndex);
    if ((ret = displayColorInterface->Update(display, mDisplaySceneInfo.displayScene)) != 0) {
        DISPLAY_LOGE("Display Scene update error (%d)", ret);
        return ret;
    }

    return ret;
}

int32_t ExynosPrimaryDisplayModule::updatePresentColorConversionInfo()
{
    int ret = NO_ERROR;
    IDisplayColorGS101* displayColorInterface = getDisplayColorInterface();
    if (displayColorInterface == nullptr) {
        return ret;
    }

    ExynosDisplayDrmInterfaceModule *moduleDisplayInterface =
        (ExynosDisplayDrmInterfaceModule*)(mDisplayInterface.get());
    auto refresh_rate = moduleDisplayInterface->getDesiredRefreshRate();
    if (refresh_rate > 0) {
        mDisplaySceneInfo.displayScene.refresh_rate = refresh_rate;
    }

    mDisplaySceneInfo.displayScene.lhbm_on = mBrightnessController->isLhbmOn();
    mDisplaySceneInfo.displayScene.dbv = mBrightnessController->getBrightnessLevel();
    const DisplayType display = getDisplayTypeFromIndex(mIndex);
    if ((ret = displayColorInterface->UpdatePresent(display, mDisplaySceneInfo.displayScene)) !=
        0) {
        DISPLAY_LOGE("Display Scene update error (%d)", ret);
        return ret;
    }

    return ret;
}

int32_t ExynosPrimaryDisplayModule::getColorAdjustedDbv(uint32_t &dbv_adj) {
    IDisplayColorGS101* displayColorInterface = getDisplayColorInterface();
    if (displayColorInterface == nullptr) {
        return NO_ERROR;
    }

    const DisplayType display = getDisplayTypeFromIndex(mIndex);
    dbv_adj = displayColorInterface->GetPipelineData(display)->Panel().GetAdjustedBrightnessLevel();
    return NO_ERROR;
}

bool ExynosPrimaryDisplayModule::DisplaySceneInfo::needDisplayColorSetting()
{
    /* TODO: Check if we can skip color setting */
    /* For now, propage setting every frame */
    return true;

    if (colorSettingChanged)
        return true;
    if (prev_layerDataMappingInfo != layerDataMappingInfo)
        return true;

    return false;
}

void ExynosPrimaryDisplayModule::DisplaySceneInfo::printDisplayScene()
{
    ALOGD("======================= DisplayScene info ========================");
    ALOGD("dpu_bit_depth: %d", static_cast<uint32_t>(displayScene.dpu_bit_depth));
    ALOGD("color_mode: %d", static_cast<uint32_t>(displayScene.color_mode));
    ALOGD("render_intent: %d", static_cast<uint32_t>(displayScene.render_intent));
    ALOGD("matrix");
    for (uint32_t i = 0; i < 16; (i += 4)) {
        ALOGD("%f, %f, %f, %f",
                displayScene.matrix[i], displayScene.matrix[i+1],
                displayScene.matrix[i+2], displayScene.matrix[i+3]);
    }
    ALOGD("layer: %zu ++++++",
            displayScene.layer_data.size());
    for (uint32_t i = 0; i < displayScene.layer_data.size(); i++) {
        ALOGD("layer[%d] info", i);
        printLayerColorData(displayScene.layer_data[i]);
    }

    ALOGD("layerDataMappingInfo: %zu ++++++",
            layerDataMappingInfo.size());
    for (auto layer : layerDataMappingInfo) {
        ALOGD("[layer: %p] [%d, %d]", layer.first, layer.second.dppIdx, layer.second.planeId);
    }
}

void ExynosPrimaryDisplayModule::DisplaySceneInfo::printLayerColorData(
    const LayerColorData& layerData)
{
    ALOGD("dataspace: 0x%8x", static_cast<uint32_t>(layerData.dataspace));
    ALOGD("matrix");
    for (uint32_t i = 0; i < 16; (i += 4)) {
        ALOGD("%f, %f, %f, %f",
                layerData.matrix[i], layerData.matrix[i+1],
                layerData.matrix[i+2], layerData.matrix[i+3]);
    }
    ALOGD("static_metadata.is_valid(%d)", layerData.static_metadata.is_valid);
    if (layerData.static_metadata.is_valid) {
        ALOGD("\tdisplay_red_primary(%d, %d)",
                layerData.static_metadata.display_red_primary_x,
                layerData.static_metadata.display_red_primary_y);
        ALOGD("\tdisplay_green_primary(%d, %d)",
                layerData.static_metadata.display_green_primary_x,
                layerData.static_metadata.display_green_primary_y);
        ALOGD("\tdisplay_blue_primary(%d, %d)",
                layerData.static_metadata.display_blue_primary_x,
                layerData.static_metadata.display_blue_primary_y);
        ALOGD("\twhite_point(%d, %d)",
                layerData.static_metadata.white_point_x,
                layerData.static_metadata.white_point_y);
    }
    ALOGD("dynamic_metadata.is_valid(%d)", layerData.dynamic_metadata.is_valid);
    if (layerData.dynamic_metadata.is_valid) {
        ALOGD("\tdisplay_maximum_luminance: %d",
                layerData.dynamic_metadata.display_maximum_luminance);
        ALOGD("\tmaxscl(%d, %d, %d)", layerData.dynamic_metadata.maxscl[0],
                layerData.dynamic_metadata.maxscl[1],
                layerData.dynamic_metadata.maxscl[2]);
        ALOGD("\ttm_flag(%d)", layerData.dynamic_metadata.tm_flag);
        ALOGD("\ttm_knee_x(%d)", layerData.dynamic_metadata.tm_knee_x);
        ALOGD("\ttm_knee_y(%d)", layerData.dynamic_metadata.tm_knee_y);
    }
}

bool ExynosPrimaryDisplayModule::parseAtcProfile() {
    Json::Value root;
    Json::CharReaderBuilder reader_builder;
    std::unique_ptr<Json::CharReader> reader(reader_builder.newCharReader());
    std::string atc_profile;

    if (!android::base::ReadFileToString(kAtcProfilePath, &atc_profile)) {
        atc_profile = kAtcJsonRaw;
        ALOGI("Use default atc profile file");
    }

    if (!reader->parse(atc_profile.c_str(), atc_profile.c_str() + atc_profile.size(), &root,
                       nullptr)) {
        ALOGE("Failed to parse atc profile file");
        return false;
    }

    ALOGI("Atc Profile version = %s", root[kAtcProfileVersionStr].asString().c_str());
    Json::Value nodes = root[kAtcProfileModesStr];
    atc_mode mode;

    for (Json::Value::ArrayIndex i = 0; i < nodes.size(); ++i) {
        std::string name = nodes[i][kAtcProfileModeNameStr].asString();

        if (nodes[i][kAtcProfileLuxMapStr].size() != nodes[i][kAtcProfileAlMapStr].size() &&
            nodes[i][kAtcProfileAlMapStr].size() != nodes[i][kAtcProfileStMapStr].size()) {
            ALOGE("Atc profile is unavailable !");
            return false;
        }

        uint32_t map_cnt = nodes[i][kAtcProfileLuxMapStr].size();

        mode.lux_map.clear();
        for (uint32_t index = 0; index < map_cnt; ++index) {
            mode.lux_map.emplace_back(atc_lux_map{nodes[i][kAtcProfileLuxMapStr][index].asUInt(),
                                                  nodes[i][kAtcProfileAlMapStr][index].asUInt(),
                                                  nodes[i][kAtcProfileStMapStr][index].asUInt()});
        }

        if (!nodes[i][kAtcProfileStUpStepStr].empty())
            mode.st_up_step = nodes[i][kAtcProfileStUpStepStr].asUInt();
        else
            mode.st_up_step = kAtcStStep;

        if (!nodes[i][kAtcProfileStDownStepStr].empty())
            mode.st_down_step = nodes[i][kAtcProfileStDownStepStr].asUInt();
        else
            mode.st_down_step = kAtcStStep;

        if (nodes[i][kAtcProfileSubSettingStr].size() != kAtcSubSetting.size()) return false;

        for (auto it = kAtcSubSetting.begin(); it != kAtcSubSetting.end(); it++) {
            mode.sub_setting[it->first.c_str()] =
                    nodes[i][kAtcProfileSubSettingStr][it->first.c_str()].asUInt();
        }
        auto ret = mAtcModeSetting.insert(std::make_pair(name.c_str(), mode));
        if (ret.second == false) {
            ALOGE("Atc mode %s is already existed!", ret.first->first.c_str());
            return false;
        }
    }

    if (mAtcModeSetting.find(kAtcModeNormalStr) == mAtcModeSetting.end()) {
        ALOGW("Failed to find atc normal mode");
        return false;
    }
    return true;
}

bool ExynosPrimaryDisplayModule::isLbeSupported() {
    return mLbeSupported;
}

void ExynosPrimaryDisplayModule::initLbe() {
    if (!parseAtcProfile()) {
        ALOGD("Failed to parseAtcMode");
        mAtcInit = false;
        return;
    }

    mAtcInit = true;
    mAtcAmbientLight.node = String8::format(ATC_AMBIENT_LIGHT_FILE_NAME, mIndex);
    mAtcAmbientLight.value.set_dirty();
    mAtcStrength.node = String8::format(ATC_ST_FILE_NAME, mIndex);
    mAtcStrength.value.set_dirty();
    mAtcEnable.node = String8::format(ATC_ENABLE_FILE_NAME, mIndex);
    mAtcEnable.value.set_dirty();

    for (auto it = kAtcSubSetting.begin(); it != kAtcSubSetting.end(); it++) {
        mAtcSubSetting[it->first.c_str()].node = String8::format(it->second.c_str(), mIndex);
        mAtcSubSetting[it->first.c_str()].value.set_dirty();
    }
    mLbeSupported = true;
}

uint32_t ExynosPrimaryDisplayModule::getAtcLuxMapIndex(std::vector<atc_lux_map> map, uint32_t lux) {
    uint32_t index = 0;
    for (uint32_t i = 0; i < map.size(); i++) {
        if (lux < map[i].lux) {
            break;
        }
        index = i;
    }

    return index;
}

int32_t ExynosPrimaryDisplayModule::setAtcStrength(uint32_t strength) {
    mAtcStrength.value.store(strength);
    if (mAtcStrength.value.is_dirty()) {
        if (writeIntToFile(mAtcStrength.node, mAtcStrength.value.get()) != NO_ERROR) return -EPERM;
        mAtcStrength.value.clear_dirty();
    }
    return NO_ERROR;
}

int32_t ExynosPrimaryDisplayModule::setAtcAmbientLight(uint32_t ambient_light) {
    mAtcAmbientLight.value.store(ambient_light);
    if (mAtcAmbientLight.value.is_dirty()) {
        if (writeIntToFile(mAtcAmbientLight.node, mAtcAmbientLight.value.get()) != NO_ERROR)
            return -EPERM;
        mAtcAmbientLight.value.clear_dirty();
    }

    return NO_ERROR;
}

int32_t ExynosPrimaryDisplayModule::setAtcMode(std::string mode_name) {
    auto mode_data = mAtcModeSetting.find(mode_name);
    uint32_t ambient_light = 0;
    uint32_t strength = 0;
    bool enable = (!mode_name.empty()) && (mode_data != mAtcModeSetting.end());

    if (enable) {
        atc_mode mode = mode_data->second;
        for (auto it = kAtcSubSetting.begin(); it != kAtcSubSetting.end(); it++) {
            mAtcSubSetting[it->first.c_str()].value.store(mode.sub_setting[it->first.c_str()]);
            if (mAtcSubSetting[it->first.c_str()].value.is_dirty()) {
                if (writeIntToFile(mAtcSubSetting[it->first.c_str()].node,
                                   mAtcSubSetting[it->first.c_str()].value.get()) != NO_ERROR)
                    return -EPERM;
                mAtcSubSetting[it->first.c_str()].value.clear_dirty();
            }
        }
        mAtcStUpStep = mode.st_up_step;
        mAtcStDownStep = mode.st_down_step;

        uint32_t index = getAtcLuxMapIndex(mode.lux_map, mCurrentLux);
        ambient_light = mode.lux_map[index].al;
        strength = mode.lux_map[index].st;
    }

    if (setAtcAmbientLight(ambient_light) != NO_ERROR) {
        ALOGE("Fail to set atc ambient light for %s mode", mode_name.c_str());
        return -EPERM;
    }

    if (setAtcStDimming(strength) != NO_ERROR) {
        ALOGE("Fail to set atc st dimming for %s mode", mode_name.c_str());
        return -EPERM;
    }

    if (!enable && isInAtcAnimation()) {
        mPendingAtcOff = true;
    } else {
        if (setAtcEnable(enable) != NO_ERROR) {
            ALOGE("Fail to set atc enable = %d", enable);
            return -EPERM;
        }
        mPendingAtcOff = false;
    }

    mCurrentAtcModeName = enable ? mode_name : "NULL";
    ALOGI("atc enable=%d (mode=%s, pending off=%s)", enable, mCurrentAtcModeName.c_str(),
          mPendingAtcOff ? "true" : "false");
    return NO_ERROR;
}
void ExynosPrimaryDisplayModule::setLbeState(LbeState state) {
    if (!mAtcInit) return;
    std::string modeStr;
    bool enhanced_hbm = false;
    switch (state) {
        case LbeState::OFF:
            mCurrentLux = 0;
            break;
        case LbeState::NORMAL:
            modeStr = kAtcModeNormalStr;
            break;
        case LbeState::HIGH_BRIGHTNESS:
            modeStr = kAtcModeHbmStr;
            break;
        case LbeState::POWER_SAVE:
            modeStr = kAtcModePowerSaveStr;
            break;
        case LbeState::HIGH_BRIGHTNESS_ENHANCE:
            modeStr = kAtcModeHbmStr;
            enhanced_hbm = true;
            break;
        default:
            ALOGE("Lbe state not support");
            return;
    }

    if (setAtcMode(modeStr) != NO_ERROR) return;

    mBrightnessController->processEnhancedHbm(enhanced_hbm);
    mBrightnessController->setOutdoorVisibility(state);

    if (mCurrentLbeState != state) {
        mCurrentLbeState = state;
        mDevice->onRefresh(mDisplayId);
    }
    ALOGI("Lbe state %hhd", mCurrentLbeState);
}

void ExynosPrimaryDisplayModule::setLbeAmbientLight(int value) {
    if (!mAtcInit) return;

    auto it = mAtcModeSetting.find(mCurrentAtcModeName);
    if (it == mAtcModeSetting.end()) {
        ALOGE("Atc mode not found");
        return;
    }
    atc_mode mode = it->second;

    uint32_t index = getAtcLuxMapIndex(mode.lux_map, value);
    if (setAtcAmbientLight(mode.lux_map[index].al) != NO_ERROR) {
        ALOGE("Failed to set atc ambient light");
        return;
    }

    if (setAtcStDimming(mode.lux_map[index].st) != NO_ERROR) {
        ALOGE("Failed to set atc st dimming");
        return;
    }

    if (mAtcLuxMapIndex != index) {
        mAtcLuxMapIndex = index;
        mDevice->onRefresh(mDisplayId);
    }
    mCurrentLux = value;
}

LbeState ExynosPrimaryDisplayModule::getLbeState() {
    return mCurrentLbeState;
}

PanelCalibrationStatus ExynosPrimaryDisplayModule::getPanelCalibrationStatus() {
    auto displayColorInterface = getDisplayColorInterface();
    if (displayColorInterface == nullptr) {
        return PanelCalibrationStatus::UNCALIBRATED;
    }

    auto displayType = getBuiltInDisplayType();
    auto calibrationInfo = displayColorInterface->GetCalibrationInfo(displayType);

    if (calibrationInfo.factory_cal_loaded) {
        return PanelCalibrationStatus::ORIGINAL;
    } else if (calibrationInfo.golden_cal_loaded) {
        return PanelCalibrationStatus::GOLDEN;
    } else {
        return PanelCalibrationStatus::UNCALIBRATED;
    }
}

int32_t ExynosPrimaryDisplayModule::setAtcStDimming(uint32_t value) {
    Mutex::Autolock lock(mAtcStMutex);
    int32_t strength = mAtcStrength.value.get();
    if (mAtcStTarget != value) {
        mAtcStTarget = value;
        uint32_t step = mAtcStTarget > strength ? mAtcStUpStep : mAtcStDownStep;

        int diff = value - strength;
        uint32_t count = (std::abs(diff) + step - 1) / step;
        mAtcStStepCount = count;
        ALOGI("setup atc st dimming=%d, count=%d, step=%d", value, count, step);
    }

    if (mAtcStStepCount == 0 && !mAtcStrength.value.is_dirty()) return NO_ERROR;

    if ((strength + mAtcStUpStep) < mAtcStTarget) {
        strength = strength + mAtcStUpStep;
    } else if (strength > (mAtcStTarget + mAtcStDownStep)) {
        strength = strength - mAtcStDownStep;
    } else {
        strength = mAtcStTarget;
    }

    if (setAtcStrength(strength) != NO_ERROR) {
        ALOGE("Failed to set atc st");
        return -EPERM;
    }

    if (mAtcStStepCount > 0) mAtcStStepCount--;
    return NO_ERROR;
}

int32_t ExynosPrimaryDisplayModule::setAtcEnable(bool enable) {
    mAtcEnable.value.store(enable);
    if (mAtcEnable.value.is_dirty()) {
        if (writeIntToFile(mAtcEnable.node, enable) != NO_ERROR) return -EPERM;
        mAtcEnable.value.clear_dirty();
    }
    return NO_ERROR;
}

void ExynosPrimaryDisplayModule::checkAtcAnimation() {
    if (!isInAtcAnimation()) return;

    if (setAtcStDimming(mAtcStTarget) != NO_ERROR) {
        ALOGE("Failed to set atc st dimming");
        return;
    }

    if (mPendingAtcOff && mAtcStStepCount == 0) {
        if (setAtcEnable(false) != NO_ERROR) {
            ALOGE("Failed to set atc enable to off");
            return;
        }
        mPendingAtcOff = false;
        ALOGI("atc enable is off (pending off=false)");
    }

    mDevice->onRefresh(mDisplayId);
}

int32_t ExynosPrimaryDisplayModule::setPowerMode(int32_t mode) {
    hwc2_power_mode_t prevPowerModeState = mPowerModeState.value_or(HWC2_POWER_MODE_OFF);
    int32_t ret;

    ret = ExynosPrimaryDisplay::setPowerMode(mode);

    if ((ret == HWC2_ERROR_NONE) && isDisplaySwitched(mode, prevPowerModeState)) {
        ExynosDeviceModule* device = static_cast<ExynosDeviceModule*>(mDevice);

        device->setActiveDisplay(mIndex);
        setForceColorUpdate(true);
    }
    return ret;
}

bool ExynosPrimaryDisplayModule::isDisplaySwitched(int32_t mode, int32_t prevMode) {
    ExynosDeviceModule* device = static_cast<ExynosDeviceModule*>(mDevice);

    return (device->getActiveDisplay() != mIndex) && (prevMode == HWC_POWER_MODE_OFF) &&
            (mode != HWC_POWER_MODE_OFF);
}

bool ExynosPrimaryDisplayModule::isColorCalibratedByDevice() {
    const DisplayType display = getDisplayTypeFromIndex(mIndex);
    IDisplayColorGS101* displayColorInterface = getDisplayColorInterface();
    return displayColorInterface->GetCalibrationInfo(display).factory_cal_loaded;
};
