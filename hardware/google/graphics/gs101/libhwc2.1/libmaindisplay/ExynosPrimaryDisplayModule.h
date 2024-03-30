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
#ifndef EXYNOS_DISPLAY_MODULE_H
#define EXYNOS_DISPLAY_MODULE_H

#include <gs101/displaycolor/displaycolor_gs101.h>

#include "ExynosDeviceModule.h"
#include "ExynosDisplay.h"
#include "ExynosLayer.h"
#include "ExynosPrimaryDisplay.h"

constexpr char kAtcJsonRaw[] =
        "{\"version\":\"0.0\",\"modes\":[{\"name\":\"normal\",\"lux_map\":[0,5000,10000,"
        "50000,70000],\"ambient_light_map\":[0,0,12,32,63],\"strength_map\":[0,0,128,128,200],"
        "\"st_up_step\":2, \"st_down_step\":2,"
        "\"sub_setting\":{\"local_tone_gain\":128,\"noise_suppression_gain\":128,\"dither\":0,"
        "\"plain_weight_1\":10,\"plain_weight_2\":14,\"color_transform_mode\":2,\"preprocessing_"
        "enable\":1,\"upgrade_on\":0,\"TDR_max\":900,\"TDR_min\":256,\"backlight\":255,\"dimming_"
        "step\":4,\"scale_mode\":1,\"threshold_1\":1,\"threshold_2\":1,\"threshold_3\":1,\"gain_"
        "limit\":511,\"lt_calc_ab_shift\":1}}]}";

constexpr char kAtcProfilePath[] = "vendor/etc/atc_profile.json";
constexpr char kAtcProfileVersionStr[] = "version";
constexpr char kAtcProfileModesStr[] = "modes";
constexpr char kAtcProfileModeNameStr[] = "name";
constexpr char kAtcProfileLuxMapStr[] = "lux_map";
constexpr char kAtcProfileAlMapStr[] = "ambient_light_map";
constexpr char kAtcProfileStMapStr[] = "strength_map";
constexpr char kAtcProfileSubSettingStr[] = "sub_setting";
constexpr char kAtcProfileStUpStepStr[] = "st_up_step";
constexpr char kAtcProfileStDownStepStr[] = "st_down_step";
constexpr uint32_t kAtcStStep = 2;

constexpr char kAtcModeNormalStr[] = "normal";
constexpr char kAtcModeHbmStr[] = "hbm";
constexpr char kAtcModePowerSaveStr[] = "power_save";

#define ATC_AMBIENT_LIGHT_FILE_NAME "/sys/class/dqe%d/atc/ambient_light"
#define ATC_ST_FILE_NAME "/sys/class/dqe%d/atc/st"
#define ATC_ENABLE_FILE_NAME "/sys/class/dqe%d/atc/en"
#define ATC_LT_FILE_NAME "/sys/class/dqe%d/atc/lt"
#define ATC_NS_FILE_NAME "/sys/class/dqe%d/atc/ns"
#define ATC_DITHER_FILE_NAME "/sys/class/dqe%d/atc/dither"
#define ATC_PL_W1_FILE_NAME "/sys/class/dqe%d/atc/pl_w1"
#define ATC_PL_W2_FILE_NAME "/sys/class/dqe%d/atc/pl_w2"
#define ATC_CTMODE_FILE_NAME "/sys/class/dqe%d/atc/ctmode"
#define ATC_PP_EN_FILE_NAME "/sys/class/dqe%d/atc/pp_en"
#define ATC_UPGRADE_ON_FILE_NAME "/sys/class/dqe%d/atc/upgrade_on"
#define ATC_TDR_MAX_FILE_NAME "/sys/class/dqe%d/atc/tdr_max"
#define ATC_TDR_MIN_FILE_NAME "/sys/class/dqe%d/atc/tdr_min"
#define ATC_BACKLIGHT_FILE_NAME "/sys/class/dqe%d/atc/back_light"
#define ATC_DSTEP_FILE_NAME "/sys/class/dqe%d/atc/dstep"
#define ATC_SCALE_MODE_FILE_NAME "/sys/class/dqe%d/atc/scale_mode"
#define ATC_THRESHOLD_1_FILE_NAME "/sys/class/dqe%d/atc/threshold_1"
#define ATC_THRESHOLD_2_FILE_NAME "/sys/class/dqe%d/atc/threshold_2"
#define ATC_THRESHOLD_3_FILE_NAME "/sys/class/dqe%d/atc/threshold_3"
#define ATC_GAIN_LIMIT_FILE_NAME "/sys/class/dqe%d/atc/gain_limit"
#define ATC_LT_CALC_AB_SHIFT_FILE_NAME "/sys/class/dqe%d/atc/lt_calc_ab_shift"

const std::unordered_map<std::string, std::string> kAtcSubSetting =
        {{"local_tone_gain", ATC_LT_FILE_NAME},
         {"noise_suppression_gain", ATC_NS_FILE_NAME},
         {"dither", ATC_DITHER_FILE_NAME},
         {"plain_weight_1", ATC_PL_W1_FILE_NAME},
         {"plain_weight_2", ATC_PL_W2_FILE_NAME},
         {"color_transform_mode", ATC_CTMODE_FILE_NAME},
         {"preprocessing_enable", ATC_PP_EN_FILE_NAME},
         {"upgrade_on", ATC_UPGRADE_ON_FILE_NAME},
         {"TDR_max", ATC_TDR_MAX_FILE_NAME},
         {"TDR_min", ATC_TDR_MIN_FILE_NAME},
         {"backlight", ATC_BACKLIGHT_FILE_NAME},
         {"dimming_step", ATC_DSTEP_FILE_NAME},
         {"scale_mode", ATC_SCALE_MODE_FILE_NAME},
         {"threshold_1", ATC_THRESHOLD_1_FILE_NAME},
         {"threshold_2", ATC_THRESHOLD_2_FILE_NAME},
         {"threshold_3", ATC_THRESHOLD_3_FILE_NAME},
         {"gain_limit", ATC_GAIN_LIMIT_FILE_NAME},
         {"lt_calc_ab_shift", ATC_LT_CALC_AB_SHIFT_FILE_NAME}};

namespace gs101 {

using namespace displaycolor;

class ExynosPrimaryDisplayModule : public ExynosPrimaryDisplay {
    public:
        ExynosPrimaryDisplayModule(uint32_t index, ExynosDevice *device);
        ~ExynosPrimaryDisplayModule();
        void usePreDefinedWindow(bool use);
        virtual int32_t validateWinConfigData();
        void doPreProcessing();
        virtual int32_t getColorModes(
                uint32_t* outNumModes,
                int32_t* outModes);
        virtual int32_t setColorMode(int32_t mode);
        virtual int32_t getRenderIntents(int32_t mode, uint32_t* outNumIntents,
                int32_t* outIntents);
        virtual int32_t setColorModeWithRenderIntent(int32_t mode,
                int32_t intent);
        virtual int32_t setColorTransform(const float* matrix, int32_t hint);
        virtual int32_t getClientTargetProperty(
                hwc_client_target_property_t* outClientTargetProperty,
                HwcDimmingStage *outDimmingStage = nullptr) override;
        virtual int deliverWinConfigData();
        virtual int32_t updateColorConversionInfo();
        virtual int32_t updatePresentColorConversionInfo();
        virtual bool checkRrCompensationEnabled() {
            const DisplayType display = getDisplayTypeFromIndex(mIndex);
            IDisplayColorGS101* displayColorInterface = getDisplayColorInterface();
            return displayColorInterface
                ? displayColorInterface->IsRrCompensationEnabled(display)
                : false;
        }

        virtual bool isColorCalibratedByDevice();

        virtual int32_t getColorAdjustedDbv(uint32_t &dbv_adj);

        virtual void initLbe();
        virtual bool isLbeSupported();
        virtual void setLbeState(LbeState state);
        virtual void setLbeAmbientLight(int value);
        virtual LbeState getLbeState();

        virtual PanelCalibrationStatus getPanelCalibrationStatus();

        class DisplaySceneInfo {
            public:
                struct LayerMappingInfo {
                    bool operator==(const LayerMappingInfo &rhs) const {
                        return ((dppIdx == rhs.dppIdx) && (planeId == rhs.planeId));
                    }

                    // index in DisplayScene::layer_data
                    uint32_t dppIdx;
                    // assigned drm plane id in last color setting update
                    uint32_t planeId;
                };
                bool colorSettingChanged = false;
                bool displaySettingDelivered = false;
                DisplayScene displayScene;

                /*
                 * Index of LayerColorData in DisplayScene::layer_data
                 * and assigned plane id in last color setting update.
                 * for each layer, including client composition
                 * key: ExynosMPPSource*
                 * data: LayerMappingInfo
                 */
                std::map<ExynosMPPSource*, LayerMappingInfo> layerDataMappingInfo;
                std::map<ExynosMPPSource*, LayerMappingInfo> prev_layerDataMappingInfo;

                void reset() {
                    colorSettingChanged = false;
                    prev_layerDataMappingInfo = layerDataMappingInfo;
                    layerDataMappingInfo.clear();
                };

                template <typename T, typename M>
                void updateInfoSingleVal(T &dst, M &src) {
                    if (src != dst) {
                        colorSettingChanged = true;
                        dst = src;
                    }
                };

                template <typename T, typename M>
                void updateInfoVectorVal(std::vector<T> &dst, M *src, uint32_t size) {
                    if ((dst.size() != size) ||
                        !std::equal(dst.begin(), dst.end(), src)) {
                        colorSettingChanged = true;
                        dst.resize(size);
                        for (uint32_t i = 0; i < size; i++) {
                            dst[i] = src[i];
                        }
                    }
                };

                void setColorMode(hwc::ColorMode mode) {
                    updateInfoSingleVal(displayScene.color_mode, mode);
                };

                void setRenderIntent(hwc::RenderIntent intent) {
                    updateInfoSingleVal(displayScene.render_intent, intent);
                };

                void setColorTransform(const float* matrix) {
                    for (uint32_t i = 0; i < displayScene.matrix.size(); i++) {
                        if (displayScene.matrix[i] != matrix[i]) {
                            colorSettingChanged = true;
                            displayScene.matrix[i] = matrix[i];
                        }
                    }
                }

                LayerColorData& getLayerColorDataInstance(uint32_t index);
                int32_t setLayerDataMappingInfo(ExynosMPPSource* layer, uint32_t index);
                void setLayerDataspace(LayerColorData& layerColorData,
                        hwc::Dataspace dataspace);
                void disableLayerHdrStaticMetadata(LayerColorData& layerColorData);
                void setLayerHdrStaticMetadata(LayerColorData& layerColorData,
                        const ExynosHdrStaticInfo& exynosHdrStaticInfo);
                void setLayerColorTransform(LayerColorData& layerColorData,
                        std::array<float, TRANSFORM_MAT_SIZE> &matrix);
                void disableLayerHdrDynamicMetadata(LayerColorData& layerColorData);
                void setLayerHdrDynamicMetadata(LayerColorData& layerColorData,
                        const ExynosHdrDynamicInfo& exynosHdrDynamicInfo);
                int32_t setLayerColorData(LayerColorData& layerData,
                        ExynosLayer* layer, float dimSdrRatio);
                int32_t setClientCompositionColorData(
                    const ExynosCompositionInfo& clientCompositionInfo,
                    LayerColorData& layerData, float dimSdrRatio);
                bool needDisplayColorSetting();
                void printDisplayScene();
                void printLayerColorData(const LayerColorData& layerData);
        };

        bool hasDisplayColor() {
            IDisplayColorGS101* displayColorInterface = getDisplayColorInterface();
            return displayColorInterface != nullptr;
        }

        /* Call getDppForLayer() only if hasDppForLayer() is true */
        bool hasDppForLayer(ExynosMPPSource* layer);
        const IDisplayColorGS101::IDpp& getDppForLayer(ExynosMPPSource* layer);
        int32_t getDppIndexForLayer(ExynosMPPSource* layer);
        /* Check if layer's assigned plane id has changed, save the new planeId.
         * call only if hasDppForLayer is true */
        bool checkAndSaveLayerPlaneId(ExynosMPPSource* layer, uint32_t planeId) {
            auto &info = mDisplaySceneInfo.layerDataMappingInfo[layer];
            bool change = info.planeId != planeId;
            info.planeId = planeId;
            return change;
        }

        size_t getNumOfDpp() {
            const DisplayType display = getDisplayTypeFromIndex(mIndex);
            IDisplayColorGS101* displayColorInterface = getDisplayColorInterface();
            return displayColorInterface->GetPipelineData(display)->Dpp().size();
        };

        const IDisplayColorGS101::IDqe& getDqe()
        {
            const DisplayType display = getDisplayTypeFromIndex(mIndex);
            IDisplayColorGS101* displayColorInterface = getDisplayColorInterface();
            return displayColorInterface->GetPipelineData(display)->Dqe();
        };

        // primary or secondary
        DisplayType getBuiltInDisplayType() { return getDisplayTypeFromIndex(mIndex); }

    private:
        int32_t setLayersColorData();
        DisplaySceneInfo mDisplaySceneInfo;

        struct atc_lux_map {
            uint32_t lux;
            uint32_t al;
            uint32_t st;
        };

        struct atc_mode {
            std::vector<atc_lux_map> lux_map;
            std::unordered_map<std::string, int32_t> sub_setting;
            uint32_t st_up_step;
            uint32_t st_down_step;
        };
        struct atc_sysfs {
            String8 node;
            CtrlValue<int32_t> value;
        };

        bool parseAtcProfile();
        int32_t setAtcMode(std::string mode_name);
        uint32_t getAtcLuxMapIndex(std::vector<atc_lux_map>, uint32_t lux);
        int32_t setAtcAmbientLight(uint32_t ambient_light);
        int32_t setAtcStrength(uint32_t strenght);
        int32_t setAtcStDimming(uint32_t target);
        int32_t setAtcEnable(bool enable);
        void checkAtcAnimation();
        bool isInAtcAnimation() {
            if (mAtcStStepCount > 0)
                return true;
            else
                return false;
        };

        IDisplayColorGS101* getDisplayColorInterface() {
            ExynosDeviceModule* device = (ExynosDeviceModule*)mDevice;
            return device->getDisplayColorInterface();
        }

        bool isForceColorUpdate() const { return mForceColorUpdate; }
        void setForceColorUpdate(bool force) { mForceColorUpdate = force; }
        bool isDisplaySwitched(int32_t mode, int32_t prevMode);

        std::map<std::string, atc_mode> mAtcModeSetting;
        bool mAtcInit;
        LbeState mCurrentLbeState = LbeState::OFF;
        std::string mCurrentAtcModeName;
        uint32_t mCurrentLux = 0;
        uint32_t mAtcLuxMapIndex = 0;
        struct atc_sysfs mAtcAmbientLight;
        struct atc_sysfs mAtcStrength;
        struct atc_sysfs mAtcEnable;
        std::unordered_map<std::string, struct atc_sysfs> mAtcSubSetting;
        uint32_t mAtcStStepCount = 0;
        uint32_t mAtcStTarget = 0;
        uint32_t mAtcStUpStep;
        uint32_t mAtcStDownStep;
        Mutex mAtcStMutex;
        bool mPendingAtcOff;
        bool mForceColorUpdate = false;
        bool mLbeSupported = false;

    protected:
        virtual int32_t setPowerMode(int32_t mode) override;
};

}  // namespace gs101

#endif
