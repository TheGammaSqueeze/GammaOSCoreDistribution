/*
 * Copyright 2018 The Android Open Source Project
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

package com.android.bluetooth.a2dp;

import android.bluetooth.BluetoothCodecConfig;
import android.bluetooth.BluetoothCodecConfig.CodecPriority;
import android.bluetooth.BluetoothCodecStatus;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.Resources.NotFoundException;
import android.media.AudioManager;
import android.os.SystemProperties;
import android.util.Log;

import com.android.bluetooth.R;

import java.util.List;
import java.util.Objects;
/*
 * A2DP Codec Configuration setup.
 */
class A2dpCodecConfig {
    private static final boolean DBG = true;
    private static final String TAG = "A2dpCodecConfig";

    // TODO(b/240635097): remove in U
    private static final int SOURCE_CODEC_TYPE_OPUS = 6;

    private Context mContext;
    private A2dpNativeInterface mA2dpNativeInterface;

    private BluetoothCodecConfig[] mCodecConfigPriorities;
    private @CodecPriority int mA2dpSourceCodecPrioritySbc =
            BluetoothCodecConfig.CODEC_PRIORITY_DEFAULT;
    private @CodecPriority int mA2dpSourceCodecPriorityAac =
            BluetoothCodecConfig.CODEC_PRIORITY_DEFAULT;
    private @CodecPriority int mA2dpSourceCodecPriorityAptx =
            BluetoothCodecConfig.CODEC_PRIORITY_DEFAULT;
    private @CodecPriority int mA2dpSourceCodecPriorityAptxHd =
            BluetoothCodecConfig.CODEC_PRIORITY_DEFAULT;
    private @CodecPriority int mA2dpSourceCodecPriorityLdac =
            BluetoothCodecConfig.CODEC_PRIORITY_DEFAULT;
    private @CodecPriority int mA2dpSourceCodecPriorityLc3 =
            BluetoothCodecConfig.CODEC_PRIORITY_DEFAULT;
    private @CodecPriority int mA2dpSourceCodecPriorityOpus =
            BluetoothCodecConfig.CODEC_PRIORITY_DEFAULT;

    private BluetoothCodecConfig[] mCodecConfigOffloading = new BluetoothCodecConfig[0];

    A2dpCodecConfig(Context context, A2dpNativeInterface a2dpNativeInterface) {
        mContext = context;
        mA2dpNativeInterface = a2dpNativeInterface;
        mCodecConfigPriorities = assignCodecConfigPriorities();

        AudioManager audioManager = mContext.getSystemService(AudioManager.class);
        if (audioManager == null) {
          Log.w(TAG, "Can't obtain the codec offloading prefernece from null AudioManager");
          return;
        }
        mCodecConfigOffloading = audioManager.getHwOffloadFormatsSupportedForA2dp()
                                             .toArray(mCodecConfigOffloading);
    }

    BluetoothCodecConfig[] codecConfigPriorities() {
        return mCodecConfigPriorities;
    }

    BluetoothCodecConfig[] codecConfigOffloading() {
        return mCodecConfigOffloading;
    }

    void setCodecConfigPreference(BluetoothDevice device,
                                  BluetoothCodecStatus codecStatus,
                                  BluetoothCodecConfig newCodecConfig) {
        Objects.requireNonNull(codecStatus);

        // Check whether the codecConfig is selectable for this Bluetooth device.
        List<BluetoothCodecConfig> selectableCodecs = codecStatus.getCodecsSelectableCapabilities();
        if (!selectableCodecs.stream().anyMatch(codec ->
                codec.isMandatoryCodec())) {
            // Do not set codec preference to native if the selectableCodecs not contain mandatory
            // codec. The reason could be remote codec negotiation is not completed yet.
            Log.w(TAG, "setCodecConfigPreference: must have mandatory codec before changing.");
            return;
        }
        if (!codecStatus.isCodecConfigSelectable(newCodecConfig)) {
            Log.w(TAG, "setCodecConfigPreference: invalid codec "
                    + Objects.toString(newCodecConfig));
            return;
        }

        // Check whether the codecConfig would change current codec config.
        int prioritizedCodecType = getPrioitizedCodecType(newCodecConfig, selectableCodecs);
        BluetoothCodecConfig currentCodecConfig = codecStatus.getCodecConfig();
        if (prioritizedCodecType == currentCodecConfig.getCodecType()
                && (prioritizedCodecType != newCodecConfig.getCodecType()
                || (currentCodecConfig.similarCodecFeedingParameters(newCodecConfig)
                && currentCodecConfig.sameCodecSpecificParameters(newCodecConfig)))) {
            // Same codec with same parameters, no need to send this request to native.
            Log.w(TAG, "setCodecConfigPreference: codec not changed.");
            return;
        }

        BluetoothCodecConfig[] codecConfigArray = new BluetoothCodecConfig[1];
        codecConfigArray[0] = newCodecConfig;
        mA2dpNativeInterface.setCodecConfigPreference(device, codecConfigArray);
    }

    void enableOptionalCodecs(BluetoothDevice device, BluetoothCodecConfig currentCodecConfig) {
        if (currentCodecConfig != null && !currentCodecConfig.isMandatoryCodec()) {
            Log.i(TAG, "enableOptionalCodecs: already using optional codec "
                    + BluetoothCodecConfig.getCodecName(currentCodecConfig.getCodecType()));
            return;
        }

        BluetoothCodecConfig[] codecConfigArray = assignCodecConfigPriorities();
        if (codecConfigArray == null) {
            return;
        }

        // Set the mandatory codec's priority to default, and remove the rest
        for (int i = 0; i < codecConfigArray.length; i++) {
            BluetoothCodecConfig codecConfig = codecConfigArray[i];
            if (!codecConfig.isMandatoryCodec()) {
                codecConfigArray[i] = null;
            }
        }

        mA2dpNativeInterface.setCodecConfigPreference(device, codecConfigArray);
    }

    void disableOptionalCodecs(BluetoothDevice device, BluetoothCodecConfig currentCodecConfig) {
        if (currentCodecConfig != null && currentCodecConfig.isMandatoryCodec()) {
            Log.i(TAG, "disableOptionalCodecs: already using mandatory codec.");
            return;
        }

        BluetoothCodecConfig[] codecConfigArray = assignCodecConfigPriorities();
        if (codecConfigArray == null) {
            return;
        }
        // Set the mandatory codec's priority to highest, and remove the rest
        for (int i = 0; i < codecConfigArray.length; i++) {
            BluetoothCodecConfig codecConfig = codecConfigArray[i];
            if (codecConfig.isMandatoryCodec()) {
                codecConfig.setCodecPriority(BluetoothCodecConfig.CODEC_PRIORITY_HIGHEST);
            } else {
                codecConfigArray[i] = null;
            }
        }
        mA2dpNativeInterface.setCodecConfigPreference(device, codecConfigArray);
    }

    // Get the codec type of the highest priority of selectableCodecs and codecConfig.
    private int getPrioitizedCodecType(BluetoothCodecConfig codecConfig,
            List<BluetoothCodecConfig> selectableCodecs) {
        BluetoothCodecConfig prioritizedCodecConfig = codecConfig;
        for (BluetoothCodecConfig config : selectableCodecs) {
            if (prioritizedCodecConfig == null) {
                prioritizedCodecConfig = config;
            }
            if (config.getCodecPriority() > prioritizedCodecConfig.getCodecPriority()) {
                prioritizedCodecConfig = config;
            }
        }
        return prioritizedCodecConfig.getCodecType();
    }

    // Assign the A2DP Source codec config priorities
    private BluetoothCodecConfig[] assignCodecConfigPriorities() {
        Resources resources = mContext.getResources();
        if (resources == null) {
            return null;
        }

        int value;
        try {
            value = SystemProperties.getInt(
                "bluetooth.a2dp.source.sbc_priority.config",
                resources.getInteger(R.integer.a2dp_source_codec_priority_sbc));
        } catch (NotFoundException e) {
            value = BluetoothCodecConfig.CODEC_PRIORITY_DEFAULT;
        }
        if ((value >= BluetoothCodecConfig.CODEC_PRIORITY_DISABLED) && (value
                < BluetoothCodecConfig.CODEC_PRIORITY_HIGHEST)) {
            mA2dpSourceCodecPrioritySbc = value;
        }

        try {
            value = SystemProperties.getInt(
                "bluetooth.a2dp.source.aac_priority.config",
                resources.getInteger(R.integer.a2dp_source_codec_priority_aac));
        } catch (NotFoundException e) {
            value = BluetoothCodecConfig.CODEC_PRIORITY_DEFAULT;
        }
        if ((value >= BluetoothCodecConfig.CODEC_PRIORITY_DISABLED) && (value
                < BluetoothCodecConfig.CODEC_PRIORITY_HIGHEST)) {
            mA2dpSourceCodecPriorityAac = value;
        }

        try {
            value = SystemProperties.getInt(
                "bluetooth.a2dp.source.aptx_priority.config",
                resources.getInteger(R.integer.a2dp_source_codec_priority_aptx));
        } catch (NotFoundException e) {
            value = BluetoothCodecConfig.CODEC_PRIORITY_DEFAULT;
        }
        if ((value >= BluetoothCodecConfig.CODEC_PRIORITY_DISABLED) && (value
                < BluetoothCodecConfig.CODEC_PRIORITY_HIGHEST)) {
            mA2dpSourceCodecPriorityAptx = value;
        }

        try {
            value = SystemProperties.getInt(
                "bluetooth.a2dp.source.aptx_hd_priority.config",
                resources.getInteger(R.integer.a2dp_source_codec_priority_aptx_hd));
        } catch (NotFoundException e) {
            value = BluetoothCodecConfig.CODEC_PRIORITY_DEFAULT;
        }
        if ((value >= BluetoothCodecConfig.CODEC_PRIORITY_DISABLED) && (value
                < BluetoothCodecConfig.CODEC_PRIORITY_HIGHEST)) {
            mA2dpSourceCodecPriorityAptxHd = value;
        }

        try {
            value = SystemProperties.getInt(
                "bluetooth.a2dp.source.ldac_priority.config",
                resources.getInteger(R.integer.a2dp_source_codec_priority_ldac));
        } catch (NotFoundException e) {
            value = BluetoothCodecConfig.CODEC_PRIORITY_DEFAULT;
        }
        if ((value >= BluetoothCodecConfig.CODEC_PRIORITY_DISABLED) && (value
                < BluetoothCodecConfig.CODEC_PRIORITY_HIGHEST)) {
            mA2dpSourceCodecPriorityLdac = value;
        }

        try {
            value = SystemProperties.getInt(
                "bluetooth.a2dp.source.lc3_priority.config",
                resources.getInteger(R.integer.a2dp_source_codec_priority_lc3));
        } catch (NotFoundException e) {
            value = BluetoothCodecConfig.CODEC_PRIORITY_DEFAULT;
        }
        if ((value >= BluetoothCodecConfig.CODEC_PRIORITY_DISABLED) && (value
                < BluetoothCodecConfig.CODEC_PRIORITY_HIGHEST)) {
            mA2dpSourceCodecPriorityLc3 = value;
        }

        try {
            value = resources.getInteger(R.integer.a2dp_source_codec_priority_opus);
        } catch (NotFoundException e) {
            value = BluetoothCodecConfig.CODEC_PRIORITY_DEFAULT;
        }
        if ((value >= BluetoothCodecConfig.CODEC_PRIORITY_DISABLED) && (value
                < BluetoothCodecConfig.CODEC_PRIORITY_HIGHEST)) {
            mA2dpSourceCodecPriorityOpus = value;
        }

        BluetoothCodecConfig codecConfig;
        BluetoothCodecConfig[] codecConfigArray =
                new BluetoothCodecConfig[6];
        codecConfig = new BluetoothCodecConfig.Builder()
                .setCodecType(BluetoothCodecConfig.SOURCE_CODEC_TYPE_SBC)
                .setCodecPriority(mA2dpSourceCodecPrioritySbc)
                .build();
        codecConfigArray[0] = codecConfig;
        codecConfig = new BluetoothCodecConfig.Builder()
                .setCodecType(BluetoothCodecConfig.SOURCE_CODEC_TYPE_AAC)
                .setCodecPriority(mA2dpSourceCodecPriorityAac)
                .build();
        codecConfigArray[1] = codecConfig;
        codecConfig = new BluetoothCodecConfig.Builder()
                .setCodecType(BluetoothCodecConfig.SOURCE_CODEC_TYPE_APTX)
                .setCodecPriority(mA2dpSourceCodecPriorityAptx)
                .build();
        codecConfigArray[2] = codecConfig;
        codecConfig = new BluetoothCodecConfig.Builder()
                .setCodecType(BluetoothCodecConfig.SOURCE_CODEC_TYPE_APTX_HD)
                .setCodecPriority(mA2dpSourceCodecPriorityAptxHd)
                .build();
        codecConfigArray[3] = codecConfig;
        codecConfig = new BluetoothCodecConfig.Builder()
                .setCodecType(BluetoothCodecConfig.SOURCE_CODEC_TYPE_LDAC)
                .setCodecPriority(mA2dpSourceCodecPriorityLdac)
                .build();
        codecConfigArray[4] = codecConfig;
        codecConfig = new BluetoothCodecConfig.Builder()
                // TODO(b/240635097): update in U
                .setCodecType(SOURCE_CODEC_TYPE_OPUS)
                .setCodecPriority(mA2dpSourceCodecPriorityOpus)
                .build();
        codecConfigArray[5] = codecConfig;

        return codecConfigArray;
    }

    public void switchCodecByBufferSize(
            BluetoothDevice device, boolean isLowLatency, int currentCodecType) {
        // TODO(b/240635097): update in U
        if ((isLowLatency && currentCodecType == SOURCE_CODEC_TYPE_OPUS)
                || (!isLowLatency && currentCodecType != SOURCE_CODEC_TYPE_OPUS)) {
            return;
        }
        BluetoothCodecConfig[] codecConfigArray = assignCodecConfigPriorities();
        for (int i = 0; i < codecConfigArray.length; i++){
            BluetoothCodecConfig codecConfig = codecConfigArray[i];
            // TODO(b/240635097): update in U
            if (codecConfig.getCodecType() == SOURCE_CODEC_TYPE_OPUS) {
                if (isLowLatency) {
                    codecConfig.setCodecPriority(BluetoothCodecConfig.CODEC_PRIORITY_HIGHEST);
                } else {
                    codecConfig.setCodecPriority(BluetoothCodecConfig.CODEC_PRIORITY_DISABLED);
                }
            } else {
                codecConfigArray[i] = null;
            }
        }
        mA2dpNativeInterface.setCodecConfigPreference(device, codecConfigArray);
    }
}

