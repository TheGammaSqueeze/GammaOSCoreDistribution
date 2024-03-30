/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.nearby.halfsheet.utils;

import static com.android.server.nearby.common.fastpair.service.UserActionHandlerBase.EXTRA_COMPANION_APP;
import static com.android.server.nearby.fastpair.UserActionHandler.ACTION_FAST_PAIR;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.net.URISyntaxException;

import service.proto.Cache;

/**
 * Util class in half sheet apk
 */
public class FastPairUtils {

    /** FastPair util method check certain app is install on the device or not. */
    public static boolean isAppInstalled(Context context, String packageName) {
        try {
            context.getPackageManager().getPackageInfo(packageName, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    /** FastPair util method to properly format the action url extra. */
    @Nullable
    public static String getCompanionAppFromActionUrl(String actionUrl) {
        try {
            Intent intent = Intent.parseUri(actionUrl, Intent.URI_INTENT_SCHEME);
            if (!intent.getAction().equals(ACTION_FAST_PAIR)) {
                Log.e("FastPairUtils", "Companion app launch attempted from malformed action url");
                return null;
            }
            return intent.getStringExtra(EXTRA_COMPANION_APP);
        } catch (URISyntaxException e) {
            Log.e("FastPairUtils", "FastPair: fail to get companion app info from discovery item");
            return null;
        }
    }

    /**
     * Converts {@link service.proto.Cache.StoredDiscoveryItem} from
     * {@link service.proto.Cache.ScanFastPairStoreItem}
     */
    public static Cache.StoredDiscoveryItem convertFrom(Cache.ScanFastPairStoreItem item) {
        return convertFrom(item, /* isSubsequentPair= */ false);
    }

    /**
     * Converts a {@link service.proto.Cache.ScanFastPairStoreItem}
     * to a {@link service.proto.Cache.StoredDiscoveryItem}.
     *
     * <p>This is needed to make the new Fast Pair scanning stack compatible with the rest of the
     * legacy Fast Pair code.
     */
    public static Cache.StoredDiscoveryItem convertFrom(
            Cache.ScanFastPairStoreItem item, boolean isSubsequentPair) {
        return Cache.StoredDiscoveryItem.newBuilder()
                .setId(item.getModelId())
                .setFirstObservationTimestampMillis(item.getFirstObservationTimestampMillis())
                .setLastObservationTimestampMillis(item.getLastObservationTimestampMillis())
                .setActionUrl(item.getActionUrl())
                .setActionUrlType(Cache.ResolvedUrlType.APP)
                .setTitle(
                        isSubsequentPair
                                ? item.getFastPairStrings().getTapToPairWithoutAccount()
                                : item.getDeviceName())
                .setMacAddress(item.getAddress())
                .setState(Cache.StoredDiscoveryItem.State.STATE_ENABLED)
                .setTriggerId(item.getModelId())
                .setIconPng(item.getIconPng())
                .setIconFifeUrl(item.getIconFifeUrl())
                .setDescription(
                        isSubsequentPair
                                ? item.getDeviceName()
                                : item.getFastPairStrings().getTapToPairWithoutAccount())
                .setAuthenticationPublicKeySecp256R1(item.getAntiSpoofingPublicKey())
                .setCompanionDetail(item.getCompanionDetail())
                .setFastPairStrings(item.getFastPairStrings())
                .setFastPairInformation(
                        Cache.FastPairInformation.newBuilder()
                                .setDataOnlyConnection(item.getDataOnlyConnection())
                                .setTrueWirelessImages(item.getTrueWirelessImages())
                                .setAssistantSupported(item.getAssistantSupported())
                                .setCompanyName(item.getCompanyName()))
                .build();
    }

    /**
     * Returns true the application is installed and can be opened on device.
     */
    public static boolean isLaunchable(@NonNull Context context, String companionApp) {
        return isAppInstalled(context, companionApp)
                && createCompanionAppIntent(context, companionApp, null) != null;
    }

    /**
     * Returns an intent to launch given the package name and bluetooth address (if provided).
     * Returns null if no such an intent can be found.
     */
    @Nullable
    public static Intent createCompanionAppIntent(@NonNull Context context, String packageName,
            @Nullable String address) {
        Intent intent = context.getPackageManager().getLaunchIntentForPackage(packageName);
        if (intent == null) {
            return null;
        }
        if (address != null) {
            BluetoothAdapter adapter = getBluetoothAdapter(context);
            if (adapter != null) {
                intent.putExtra(BluetoothDevice.EXTRA_DEVICE, adapter.getRemoteDevice(address));
            }
        }
        return intent;
    }

    @Nullable
    private static BluetoothAdapter getBluetoothAdapter(@NonNull Context context) {
        BluetoothManager bluetoothManager = context.getSystemService(BluetoothManager.class);
        return bluetoothManager == null ? null : bluetoothManager.getAdapter();
    }

    private FastPairUtils() {}
}
