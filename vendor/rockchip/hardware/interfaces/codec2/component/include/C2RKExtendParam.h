/*
 * Copyright (C) 2022 Rockchip Electronics Co. LTD
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

#ifndef ANDROID_C2_RK_EXTEND_PARAMS_H
#define ANDROID_C2_RK_EXTEND_PARAMS_H

#include <C2Param.h>
#include <C2Config.h>

enum ExtendedC2ParamIndexKind : C2Param::type_index_t {
    kParamIndexSceneMode = C2Param::TYPE_INDEX_VENDOR_START,

    /* static capability queries */
    kParamIndexMLVECDriVersion,
    kParamIndexMLVECMaxLayerCount,
    kParamIndexMLVECLowLatencyMode,
    kParamIndexMLVECMaxLTRFrames,
    kParamIndexMLVECPreOPSupport,
    /* static configuration parameters */
    kParamIndexMLVECProfileLvel,
    kParamIndexMLVECSliceSpacing,
    kParamIndexMLVECRateControl,
    kParamIndexMLVECSetLTRFrames,
    kParamIndexMLVECSetSarSize,
    kParamIndexMLVECInputQueueCtl,
    /* dynamic Configuration parameters */
    kParamIndexMLVECLtrCtlMarkFrm,
    kParamIndexMLVECLtrCtlUseFrm,
    kParamIndexMLVECFrameQPCtl,
    kParamIndexMLVECBaseLayerPID,
    kParamIndexMLVECTriggerTime,
    kParamIndexMLVECDownScalar,
    kParamIndexMLVECInputCrop,

    kParamIndexSliceSize,
};

typedef C2PortParam<C2Info, C2Int32Value, kParamIndexSceneMode> C2StreamSceneModeInfo;
constexpr char C2_PARAMKEY_SCENE_MODE[] = "scene-mode";

typedef C2PortParam<C2Info, C2Int32Value, kParamIndexSliceSize> C2StreamSliceSizeInfo;
constexpr char C2_PARAMKEY_SLICE_SIZE[] = "slice-size";

/*
 * 1. MLVEC hardware driver version
 *    key-name: vendor.rtc-ext-enc-caps-vt-driver-version.number
 */
struct C2NumberStruct {
    int32_t number;
    inline C2NumberStruct() : number(0) { }
    inline C2NumberStruct(int32_t _number) : number(_number) {}

    const static std::vector<C2FieldDescriptor> _FIELD_LIST;
    static const std::vector<C2FieldDescriptor> FieldList();
};

typedef C2PortParam<C2Info, C2NumberStruct, kParamIndexMLVECDriVersion> C2DriverVersion;
constexpr char C2_PARAMKEY_MLVEC_ENC_DRI_VERSION[] = "rtc-ext-enc-caps-vt-driver-version";
constexpr char C2_PARAMKEY_MLVEC_DEC_DRI_VERSION[] = "rtc-ext-dec-caps-vt-driver-version";


/*
 * 2. the maximal number of support tsvc layer count
 *    key-name - vendor.rtc-ext-enc-caps-temporal-layers.max-p-count
 */
struct C2MaxLayersStruct {
    int32_t count;
    C2MaxLayersStruct() : count(0) { }
    C2MaxLayersStruct(int32_t _count) : count(_count) {}

    const static std::vector<C2FieldDescriptor> _FIELD_LIST;
    static const std::vector<C2FieldDescriptor> FieldList();
};

typedef C2PortParam<C2Info, C2MaxLayersStruct, kParamIndexMLVECMaxLayerCount> C2MaxLayerCount;
constexpr char C2_PARAMKEY_MLVEC_MAX_TEMPORAL_LAYERS[] = "rtc-ext-enc-caps-temporal-layers";

/*
 * 3. enforces the encoder/decoder to run in low latency mode. When the value is
 *    TRUE, encoder must (1) enforce 1-in-1-out behavior, (2) generate bitstreams
 *    with syntax element.
 *    key-name - vendor.rtc-ext-enc-low-latency.enable
 */
struct C2ModeEnableStruct {
    int32_t enable;
    C2ModeEnableStruct() : enable(0) { }
    C2ModeEnableStruct(int32_t _enable) : enable(_enable) {}

    const static std::vector<C2FieldDescriptor> _FIELD_LIST;
    static const std::vector<C2FieldDescriptor> FieldList();
};

typedef C2PortParam<C2Info, C2ModeEnableStruct, kParamIndexMLVECLowLatencyMode> C2LowLatencyMode;
constexpr char C2_PARAMKEY_MLVEC_ENC_LOW_LATENCY_MODE[] = "rtc-ext-enc-low-latency";
constexpr char C2_PARAMKEY_MLVEC_DEC_LOW_LATENCY_MODE[] = "rtc-ext-dec-low-latency";


/*
 * 4. MaxLTRFrames is the maximal number of LTR frames supported by the encoder.
 *    The value must be smaller than or equal to nMaxRefFrames and greater than
 *    or equal to 2
 *    key-name - vendor.rtc-ext-enc-caps-ltr.max-count
 */
struct C2MaxCntStruct {
    int32_t count;
    C2MaxCntStruct() : count(0) { }
    C2MaxCntStruct(int32_t _count) : count(_count) {}

    const static std::vector<C2FieldDescriptor> _FIELD_LIST;
    static const std::vector<C2FieldDescriptor> FieldList();
};

typedef C2PortParam<C2Info, C2MaxCntStruct, kParamIndexMLVECMaxLTRFrames> C2MaxLTRFramesCount;
constexpr char C2_PARAMKEY_MLVEC_MAX_LTR_FRAMES[] = "rtc-ext-enc-caps-ltr";

/*
 * 5. - "Resize support" indicates what down scaling factors supported by the
 *       encoder when combined resizing with encoding is supported.
 *       key-name - vendor.rtc-ext-enc-caps-preprocess.max-downscale-factor
 *    - "Rotation support" indicates the encoder supports rotation or not.
 *       key-name - vendor.rtc-ext-enc-caps-preprocess.rotation
 */
struct C2PreOPStruct {
    int32_t scale;
    int32_t rotation;
    C2PreOPStruct() : scale(0), rotation(0) { }
    C2PreOPStruct(int32_t _scale, int32_t _rotation) : scale(_scale), rotation(_rotation) {}

    const static std::vector<C2FieldDescriptor> _FIELD_LIST;
    static const std::vector<C2FieldDescriptor> FieldList();
};

typedef C2PortParam<C2Info, C2PreOPStruct, kParamIndexMLVECPreOPSupport> C2PreOPSupport;
constexpr char C2_PARAMKEY_MLVEC_PRE_OP[] = "rtc-ext-enc-caps-preprocess";

/*
 * 6. profile and level
 *    key-name: vendor.rtc-ext-enc-custom-profile-level.profile(level)
 */
struct C2ProfileStruct {
    int32_t profile;
    int32_t level;
    C2ProfileStruct() : profile(0), level(0) { }
    C2ProfileStruct(int32_t _profile, int32_t _level) : profile(_profile), level(_level) {}

    const static std::vector<C2FieldDescriptor> _FIELD_LIST;
    static const std::vector<C2FieldDescriptor> FieldList();
};

typedef C2PortParam<C2Info, C2ProfileStruct, kParamIndexMLVECProfileLvel> C2MProfileLevel;
constexpr char C2_PARAMKEY_MLVEC_PROFILE_LEVEL[] = "rtc-ext-enc-custom-profile-level";

/*
 * 7. SliceHeaderSpacing indicates the number of MBs in a slice
 *    key-name: vendor.rtc-ext-enc-slice.spacing
 */
struct C2SpacingStruct {
    int32_t spacing;
    C2SpacingStruct() : spacing(0) { }
    C2SpacingStruct(int32_t _spacing) : spacing(_spacing) {}

    const static std::vector<C2FieldDescriptor> _FIELD_LIST;
    static const std::vector<C2FieldDescriptor> FieldList();
};

typedef C2PortParam<C2Info, C2SpacingStruct, kParamIndexMLVECSliceSpacing> C2SliceSpacing;
constexpr char C2_PARAMKEY_MLVEC_SLICE_SPACING[] = "rtc-ext-enc-slice";

/*
 * 8. RateControl defines the encoding rate control mode, 0 means disable
 *    internal RC, and use constan QP set by app.
 *    key-name: vendor.rtc-ext-enc-bitrate-mode.value
 */
typedef C2PortParam<C2Info, C2Int32Value, kParamIndexMLVECRateControl> C2RateControl;
constexpr char C2_PARAMKEY_MLVEC_RATE_CONTROL[] = "rtc-ext-enc-bitrate-mode";

/*
 * 9. LTRFrames is the number of LTR frames controlled by the application
 *    key-name: vendor.rtc-ext-enc-ltr-count.num-ltr-frames
 */
struct C2NumLTRFrmsStruct {
    int32_t num;
    C2NumLTRFrmsStruct() : num(0) { }
    C2NumLTRFrmsStruct(int32_t _num) : num(_num) {}

    const static std::vector<C2FieldDescriptor> _FIELD_LIST;
    static const std::vector<C2FieldDescriptor> FieldList();
};

typedef C2PortParam<C2Info, C2NumLTRFrmsStruct, kParamIndexMLVECSetLTRFrames> C2NumLTRFrms;
constexpr char C2_PARAMKEY_MLVEC_NUM_LTR_FRAMES[] = "rtc-ext-enc-ltr-count";

/*
 * 10. SarWidth and SarHeight maps to SPS VUIsyntax element
 *     key-name: vendor.rtc-ext-enc-sar.width \ vendor.rtc-ext-enc-sar.height
 */
typedef C2PortParam<C2Info, C2PictureSizeStruct, kParamIndexMLVECSetSarSize> C2SarSize;
constexpr char C2_PARAMKEY_MLVEC_SET_SAR_SIZE[] = "rtc-ext-enc-sar";

/*
 * 11. InputQueueControl
 *     key-name: vendor.rtc-ext-enc-app-input-control.enable
 */
typedef C2PortParam<C2Info, C2ModeEnableStruct, kParamIndexMLVECInputQueueCtl> C2InputQueuCtl;
constexpr char C2_PARAMKEY_MLVEC_INPUT_QUEUE_CTL[] = "rtc-ext-enc-app-input-control";

/*
 * 12. long-term frames control: MarkLTR
 *     key-name: vendor.rtc-ext-enc-ltr.mark-frame
 */
struct C2LtrMarkStruct {
    int32_t markFrame;
    C2LtrMarkStruct() : markFrame(0) { }
    C2LtrMarkStruct(int32_t _markFrame) : markFrame(_markFrame) {}

    const static std::vector<C2FieldDescriptor> _FIELD_LIST;
    static const std::vector<C2FieldDescriptor> FieldList();
};

typedef C2PortParam<C2Info, C2LtrMarkStruct, kParamIndexMLVECLtrCtlMarkFrm> C2LtrCtlMark;
constexpr char C2_PARAMKEY_MLVEC_LTR_CTL_MARK[] = "rtc-ext-enc-ltr";

/*
 * 13. long-term frames control: UseLTR
 *     key-name: vendor.rtc-ext-enc-ltr.use-frame
 */
struct C2LtrUseStruct {
    int32_t useFrame;
    C2LtrUseStruct() : useFrame(0) { }
    C2LtrUseStruct(int32_t _useFrame) : useFrame(_useFrame) {}

    const static std::vector<C2FieldDescriptor> _FIELD_LIST;
    static const std::vector<C2FieldDescriptor> FieldList();
};

typedef C2PortParam<C2Info, C2LtrUseStruct, kParamIndexMLVECLtrCtlUseFrm> C2LtrCtlUse;
constexpr char C2_PARAMKEY_MLVEC_LTR_CTL_USE[] = "rtc-ext-enc-ltr";

/*
 * 14. FrameQP specifies the quantization parameter (QP) value of the next frame
 *     key-name: vendor.rtc-ext-enc-frame-qp.value
 */
typedef C2PortParam<C2Info, C2Int32Value, kParamIndexMLVECFrameQPCtl> C2FrameQPCtl;
constexpr char C2_PARAMKEY_MLVEC_FRAME_QP_CTL[] = "rtc-ext-enc-frame-qp";

/*
 * 15. BaseLayerPID changes the value of H.264 syntax element priority_id of
 *     the base temporal layer(i.e.with temporal_id equal to 0), starting from
 *     the next base layer frame
 *     key-name: vendor.rtc-ext-enc-base-layer-pid.value
 */
typedef C2PortParam<C2Info, C2Int32Value, kParamIndexMLVECBaseLayerPID> C2BaseLayerPid;
constexpr char C2_PARAMKEY_MLVEC_BASE_LAYER_PID[] = "rtc-ext-enc-base-layer-pid";

/*
 * 16. DynamicConfigurationTimestamp specifies timestamp of the frame which
 *     dynamic configuration should apply to.
 *     key-name: vendor.rtc-ext-enc-input-trigger.timestamp
 */
struct C2TimestampStruct {
    int64_t timestamp;
    C2TimestampStruct() : timestamp(0) { }
    C2TimestampStruct(int64_t _timestamp) : timestamp(_timestamp) {}

    const static std::vector<C2FieldDescriptor> _FIELD_LIST;
    static const std::vector<C2FieldDescriptor> FieldList();
};

typedef C2PortParam<C2Info, C2TimestampStruct, kParamIndexMLVECTriggerTime> C2TriggerTime;
constexpr char C2_PARAMKEY_MLVEC_TRIGGER_TIME[] = "rtc-ext-enc-input-trigger";

/*
 * 17. DownScaleWidth & DownScaleHeight indicate the down scaled output resolution
 *     of encoder, if the encoder has the capability of internal resizing.
 *     key-name: vendor.rtc-ext-down-scalar.output-width(height)
 */
struct C2ScalarStruct {
    int32_t width;
    int32_t height;
    C2ScalarStruct() : width(0), height(0) { }
    C2ScalarStruct(int32_t _width, int32_t _height) : width(_width), height(_height) {}

    const static std::vector<C2FieldDescriptor> _FIELD_LIST;
    static const std::vector<C2FieldDescriptor> FieldList();
};

typedef C2PortParam<C2Info, C2ScalarStruct, kParamIndexMLVECDownScalar> C2DownScalar;
constexpr char C2_PARAMKEY_MLVEC_DOWN_SCALAR[] = "rtc-ext-down-scalar";

/*
 * 18. InputCrop in combination with input frame size and output bitstream resolution
 *     specify crop,scaling and combined crop/scaling operation
 *     key-name: vendor.rtc-ext-enc-input.crop-left
 */
struct C2CropStruct {
    int32_t left;
    int32_t right;
    int32_t width;
    int32_t height;
    C2CropStruct() : left(0), right(0), width(0), height(0) { }
    C2CropStruct(int32_t _left, int32_t _right, int32_t _width, int32_t _height)
        : left(_left), right(_right), width(_width), height(_height) {}

    const static std::vector<C2FieldDescriptor> _FIELD_LIST;
    static const std::vector<C2FieldDescriptor> FieldList();
};

typedef C2PortParam<C2Info, C2CropStruct, kParamIndexMLVECInputCrop> C2InputCrop;
constexpr char C2_PARAMKEY_MLVEC_INPUT_CROP[] = "rtc-ext-enc-input";

#endif  // ANDROID_C2_RK_EXTEND_PARAMS_H
