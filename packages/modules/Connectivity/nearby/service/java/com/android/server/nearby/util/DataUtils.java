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

package com.android.server.nearby.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import service.proto.Cache.ScanFastPairStoreItem;
import service.proto.Cache.StoredDiscoveryItem;
import service.proto.FastPairString.FastPairStrings;
import service.proto.Rpcs.Device;
import service.proto.Rpcs.GetObservedDeviceResponse;
import service.proto.Rpcs.ObservedDeviceStrings;

/**
 * Utils class converts different data types {@link ScanFastPairStoreItem},
 * {@link StoredDiscoveryItem} and {@link GetObservedDeviceResponse},
 *
 */
public final class DataUtils {

    /**
     * Converts a {@link GetObservedDeviceResponse} to a {@link ScanFastPairStoreItem}.
     */
    public static ScanFastPairStoreItem toScanFastPairStoreItem(
            GetObservedDeviceResponse observedDeviceResponse,
            @NonNull String bleAddress, @Nullable String account) {
        Device device = observedDeviceResponse.getDevice();
        String deviceName = device.getName();
        return ScanFastPairStoreItem.newBuilder()
                .setAddress(bleAddress)
                .setActionUrl(device.getIntentUri())
                .setDeviceName(deviceName)
                .setIconPng(observedDeviceResponse.getImage())
                .setIconFifeUrl(device.getImageUrl())
                .setAntiSpoofingPublicKey(device.getAntiSpoofingKeyPair().getPublicKey())
                .setFastPairStrings(getFastPairStrings(observedDeviceResponse, deviceName, account))
                .build();
    }

    /**
     * Prints readable string for a {@link ScanFastPairStoreItem}.
     */
    public static String toString(ScanFastPairStoreItem item) {
        return "ScanFastPairStoreItem=[address:" + item.getAddress()
                + ", actionUr:" + item.getActionUrl()
                + ", deviceName:" + item.getDeviceName()
                + ", iconPng:" + item.getIconPng()
                + ", iconFifeUrl:" + item.getIconFifeUrl()
                + ", antiSpoofingKeyPair:" + item.getAntiSpoofingPublicKey()
                + ", fastPairStrings:" + toString(item.getFastPairStrings())
                + "]";
    }

    /**
     * Prints readable string for a {@link FastPairStrings}
     */
    public static String toString(FastPairStrings fastPairStrings) {
        return "FastPairStrings["
                + "tapToPairWithAccount=" + fastPairStrings.getTapToPairWithAccount()
                + ", tapToPairWithoutAccount=" + fastPairStrings.getTapToPairWithoutAccount()
                + ", initialPairingDescription=" + fastPairStrings.getInitialPairingDescription()
                + ", pairingFinishedCompanionAppInstalled="
                + fastPairStrings.getPairingFinishedCompanionAppInstalled()
                + ", pairingFinishedCompanionAppNotInstalled="
                + fastPairStrings.getPairingFinishedCompanionAppNotInstalled()
                + ", subsequentPairingDescription="
                + fastPairStrings.getSubsequentPairingDescription()
                + ", retroactivePairingDescription="
                + fastPairStrings.getRetroactivePairingDescription()
                + ", waitAppLaunchDescription=" + fastPairStrings.getWaitAppLaunchDescription()
                + ", pairingFailDescription=" + fastPairStrings.getPairingFailDescription()
                + "]";
    }

    private static FastPairStrings getFastPairStrings(GetObservedDeviceResponse response,
            String deviceName, @Nullable String account) {
        ObservedDeviceStrings strings = response.getStrings();
        return FastPairStrings.newBuilder()
                .setTapToPairWithAccount(strings.getInitialNotificationDescription())
                .setTapToPairWithoutAccount(
                        strings.getInitialNotificationDescriptionNoAccount())
                .setInitialPairingDescription(account == null
                        ? strings.getInitialNotificationDescriptionNoAccount()
                        : String.format(strings.getInitialPairingDescription(),
                                deviceName, account))
                .setPairingFinishedCompanionAppInstalled(
                        strings.getConnectSuccessCompanionAppInstalled())
                .setPairingFinishedCompanionAppNotInstalled(
                        strings.getConnectSuccessCompanionAppNotInstalled())
                .setSubsequentPairingDescription(strings.getSubsequentPairingDescription())
                .setRetroactivePairingDescription(strings.getRetroactivePairingDescription())
                .setWaitAppLaunchDescription(strings.getWaitLaunchCompanionAppDescription())
                .setPairingFailDescription(strings.getFailConnectGoToSettingsDescription())
                .build();
    }
}
