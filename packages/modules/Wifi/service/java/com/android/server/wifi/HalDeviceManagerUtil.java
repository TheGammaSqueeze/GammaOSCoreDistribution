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

package com.android.server.wifi;

import android.annotation.NonNull;
import android.hardware.wifi.V1_6.IWifiChip;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

/**
 * Utility methods for HalDeviceManager.
 */
public class HalDeviceManagerUtil {
    static class StaticChipInfo {
        private int mChipId;
        private long mChipCapabilities;
        private @NonNull ArrayList<android.hardware.wifi.V1_6.IWifiChip.ChipMode> mAvailableModes =
                new ArrayList<>();

        StaticChipInfo(
                int chipId,
                long chipCapabilities,
                @NonNull ArrayList<android.hardware.wifi.V1_6.IWifiChip.ChipMode> availableModes) {
            mChipId = chipId;
            mChipCapabilities = chipCapabilities;
            if (availableModes != null) {
                mAvailableModes = availableModes;
            }
        }

        int getChipId() {
            return mChipId;
        }

        long getChipCapabilities() {
            return mChipCapabilities;
        }

        ArrayList<android.hardware.wifi.V1_6.IWifiChip.ChipMode> getAvailableModes() {
            return mAvailableModes;
        }
    }

    private static final String KEY_CHIP_ID = "chipId";
    private static final String KEY_CHIP_CAPABILITIES = "chipCapabilities";
    private static final String KEY_AVAILABLE_MODES = "availableModes";

    static JSONObject staticChipInfoToJson(@NonNull StaticChipInfo staticChipInfo)
            throws JSONException {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put(KEY_CHIP_ID, staticChipInfo.getChipId());
        jsonObject.put(KEY_CHIP_CAPABILITIES, staticChipInfo.getChipCapabilities());
        JSONArray availableModesJson = new JSONArray();
        for (android.hardware.wifi.V1_6.IWifiChip.ChipMode mode
                : staticChipInfo.getAvailableModes()) {
            availableModesJson.put(chipModeToJson(mode));
        }
        jsonObject.put(KEY_AVAILABLE_MODES, availableModesJson);
        return jsonObject;
    }

    static StaticChipInfo jsonToStaticChipInfo(JSONObject jsonObject) throws JSONException {
        ArrayList<IWifiChip.ChipMode> availableModes =
                new ArrayList<>();
        int chipId = jsonObject.getInt(KEY_CHIP_ID);
        long chipCapabilities = jsonObject.getLong(KEY_CHIP_CAPABILITIES);
        JSONArray modesJson = jsonObject.getJSONArray(KEY_AVAILABLE_MODES);
        for (int i = 0; i < modesJson.length(); i++) {
            availableModes.add(jsonToChipMode(modesJson.getJSONObject(i)));
        }
        return new StaticChipInfo(chipId, chipCapabilities, availableModes);
    }

    private static final String KEY_ID = "id";
    private static final String KEY_AVAILABLE_COMBINATIONS = "availableCombinations";

    private static JSONObject chipModeToJson(android.hardware.wifi.V1_6.IWifiChip.ChipMode chipMode)
            throws JSONException {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put(KEY_ID, chipMode.id);
        JSONArray availableCombinationsJson = new JSONArray();
        for (IWifiChip.ChipConcurrencyCombination combo
                : chipMode.availableCombinations) {
            availableCombinationsJson.put(chipConcurrencyCombinationToJson(combo));
        }
        jsonObject.put(KEY_AVAILABLE_COMBINATIONS, availableCombinationsJson);
        return jsonObject;
    }

    private static android.hardware.wifi.V1_6.IWifiChip.ChipMode jsonToChipMode(
            JSONObject jsonObject) throws JSONException  {
        android.hardware.wifi.V1_6.IWifiChip.ChipMode chipMode =
                new android.hardware.wifi.V1_6.IWifiChip.ChipMode();
        chipMode.id = jsonObject.getInt(KEY_ID);
        JSONArray availableCombinationsJson =
                jsonObject.getJSONArray(KEY_AVAILABLE_COMBINATIONS);
        for (int i = 0; i < availableCombinationsJson.length(); i++) {
            chipMode.availableCombinations.add(jsonToChipConcurrencyCombination(
                    availableCombinationsJson.getJSONObject(i)));
        }
        return chipMode;
    }

    private static final String KEY_CONCURRENCY_LIMITS = "limits";

    private static JSONObject chipConcurrencyCombinationToJson(
            IWifiChip.ChipConcurrencyCombination combo) throws JSONException {
        JSONObject jsonObject = new JSONObject();
        JSONArray limitsJson = new JSONArray();
        for (IWifiChip.ChipConcurrencyCombinationLimit limit : combo.limits) {
            limitsJson.put(chipConcurrencyCombinationLimitToJson(limit));
        }
        jsonObject.put(KEY_CONCURRENCY_LIMITS, limitsJson);
        return jsonObject;
    }

    private static IWifiChip.ChipConcurrencyCombination jsonToChipConcurrencyCombination(
            JSONObject jsonObject) throws JSONException {
        IWifiChip.ChipConcurrencyCombination combo = new IWifiChip.ChipConcurrencyCombination();
        combo.limits = new ArrayList<>();
        JSONArray limitsJson = jsonObject.getJSONArray(KEY_CONCURRENCY_LIMITS);
        for (int i = 0; i < limitsJson.length(); i++) {
            combo.limits.add(
                    jsonToChipConcurrencyCombinationLimit(limitsJson.getJSONObject(i)));
        }
        return combo;
    }

    private static final String KEY_MAX_IFACES = "maxIfaces";
    private static final String KEY_TYPES = "types";

    private static JSONObject chipConcurrencyCombinationLimitToJson(
            IWifiChip.ChipConcurrencyCombinationLimit limit) throws JSONException {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put(KEY_MAX_IFACES, limit.maxIfaces);
        jsonObject.put(KEY_TYPES, new JSONArray(limit.types));
        return jsonObject;
    }

    private static IWifiChip.ChipConcurrencyCombinationLimit jsonToChipConcurrencyCombinationLimit(
            JSONObject jsonObject) throws JSONException {
        IWifiChip.ChipConcurrencyCombinationLimit
                limit = new IWifiChip.ChipConcurrencyCombinationLimit();
        limit.maxIfaces = jsonObject.getInt(KEY_MAX_IFACES);
        limit.types = new ArrayList<>();
        JSONArray limitsJson = jsonObject.getJSONArray(KEY_TYPES);
        for (int i = 0; i < limitsJson.length(); i++) {
            limit.types.add(limitsJson.getInt(i));
        }
        return limit;
    }
}

