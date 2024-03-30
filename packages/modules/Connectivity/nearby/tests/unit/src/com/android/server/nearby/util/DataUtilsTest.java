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

import static com.google.common.io.BaseEncoding.base16;
import static com.google.common.truth.Truth.assertThat;

import com.google.protobuf.ByteString;

import org.junit.Test;

import service.proto.Cache;
import service.proto.FastPairString.FastPairStrings;
import service.proto.Rpcs;
import service.proto.Rpcs.GetObservedDeviceResponse;

public final class DataUtilsTest {
    private static final String BLUETOOTH_ADDRESS = "00:11:22:33:FF:EE";
    private static final String APP_PACKAGE = "test_package";
    private static final String APP_ACTION_URL =
            "intent:#Intent;action=cto_be_set%3AACTION_MAGIC_PAIR;"
                    + "package=to_be_set;"
                    + "component=to_be_set;"
                    + "to_be_set%3AEXTRA_COMPANION_APP="
                    + APP_PACKAGE
                    + ";end";
    private static final long DEVICE_ID = 12;
    private static final String DEVICE_NAME = "My device";
    private static final byte[] DEVICE_PUBLIC_KEY = base16().decode("0123456789ABCDEF");
    private static final String DEVICE_COMPANY = "Company name";
    private static final byte[] DEVICE_IMAGE = new byte[] {0x00, 0x01, 0x10, 0x11};
    private static final String DEVICE_IMAGE_URL = "device_image_url";
    private static final String AUTHORITY = "com.android.test";
    private static final String SIGNATURE_HASH = "as8dfbyu2duas7ikanvklpaclo2";
    private static final String ACCOUNT = "test@gmail.com";

    private static final String MESSAGE_INIT_NOTIFY_DESCRIPTION = "message 1";
    private static final String MESSAGE_INIT_NOTIFY_DESCRIPTION_NO_ACCOUNT = "message 2";
    private static final String MESSAGE_INIT_PAIR_DESCRIPTION = "message 3 %s";
    private static final String MESSAGE_COMPANION_INSTALLED = "message 4";
    private static final String MESSAGE_COMPANION_NOT_INSTALLED = "message 5";
    private static final String MESSAGE_SUBSEQUENT_PAIR_DESCRIPTION = "message 6";
    private static final String MESSAGE_RETROACTIVE_PAIR_DESCRIPTION = "message 7";
    private static final String MESSAGE_WAIT_LAUNCH_COMPANION_APP_DESCRIPTION = "message 8";
    private static final String MESSAGE_FAIL_CONNECT_DESCRIPTION = "message 9";
    private static final String MESSAGE_FAST_PAIR_TV_CONNECT_DEVICE_NO_ACCOUNT_DESCRIPTION =
            "message 10";
    private static final String MESSAGE_ASSISTANT_HALF_SHEET_DESCRIPTION = "message 11";
    private static final String MESSAGE_ASSISTANT_NOTIFICATION_DESCRIPTION = "message 12";

    @Test
    public void test_toScanFastPairStoreItem_withAccount() {
        Cache.ScanFastPairStoreItem item = DataUtils.toScanFastPairStoreItem(
                createObservedDeviceResponse(), BLUETOOTH_ADDRESS, ACCOUNT);
        assertThat(item.getAddress()).isEqualTo(BLUETOOTH_ADDRESS);
        assertThat(item.getActionUrl()).isEqualTo(APP_ACTION_URL);
        assertThat(item.getDeviceName()).isEqualTo(DEVICE_NAME);
        assertThat(item.getIconPng()).isEqualTo(ByteString.copyFrom(DEVICE_IMAGE));
        assertThat(item.getIconFifeUrl()).isEqualTo(DEVICE_IMAGE_URL);
        assertThat(item.getAntiSpoofingPublicKey())
                .isEqualTo(ByteString.copyFrom(DEVICE_PUBLIC_KEY));

        FastPairStrings strings = item.getFastPairStrings();
        assertThat(strings.getTapToPairWithAccount()).isEqualTo(MESSAGE_INIT_NOTIFY_DESCRIPTION);
        assertThat(strings.getTapToPairWithoutAccount())
                .isEqualTo(MESSAGE_INIT_NOTIFY_DESCRIPTION_NO_ACCOUNT);
        assertThat(strings.getInitialPairingDescription())
                .isEqualTo(String.format(MESSAGE_INIT_PAIR_DESCRIPTION, DEVICE_NAME));
        assertThat(strings.getPairingFinishedCompanionAppInstalled())
                .isEqualTo(MESSAGE_COMPANION_INSTALLED);
        assertThat(strings.getPairingFinishedCompanionAppNotInstalled())
                .isEqualTo(MESSAGE_COMPANION_NOT_INSTALLED);
        assertThat(strings.getSubsequentPairingDescription())
                .isEqualTo(MESSAGE_SUBSEQUENT_PAIR_DESCRIPTION);
        assertThat(strings.getRetroactivePairingDescription())
                .isEqualTo(MESSAGE_RETROACTIVE_PAIR_DESCRIPTION);
        assertThat(strings.getWaitAppLaunchDescription())
                .isEqualTo(MESSAGE_WAIT_LAUNCH_COMPANION_APP_DESCRIPTION);
        assertThat(strings.getPairingFailDescription())
                .isEqualTo(MESSAGE_FAIL_CONNECT_DESCRIPTION);
    }

    @Test
    public void test_toScanFastPairStoreItem_withoutAccount() {
        Cache.ScanFastPairStoreItem item = DataUtils.toScanFastPairStoreItem(
                createObservedDeviceResponse(), BLUETOOTH_ADDRESS, /* account= */ null);
        FastPairStrings strings = item.getFastPairStrings();
        assertThat(strings.getInitialPairingDescription())
                .isEqualTo(MESSAGE_INIT_NOTIFY_DESCRIPTION_NO_ACCOUNT);
    }

    @Test
    public void test_toString() {
        Cache.ScanFastPairStoreItem item = DataUtils.toScanFastPairStoreItem(
                createObservedDeviceResponse(), BLUETOOTH_ADDRESS, ACCOUNT);
        FastPairStrings strings = item.getFastPairStrings();

        assertThat(DataUtils.toString(strings))
                .isEqualTo("FastPairStrings[tapToPairWithAccount=message 1, "
                        + "tapToPairWithoutAccount=message 2, "
                        + "initialPairingDescription=message 3 " + DEVICE_NAME + ", "
                        + "pairingFinishedCompanionAppInstalled=message 4, "
                        + "pairingFinishedCompanionAppNotInstalled=message 5, "
                        + "subsequentPairingDescription=message 6, "
                        + "retroactivePairingDescription=message 7, "
                        + "waitAppLaunchDescription=message 8, "
                        + "pairingFailDescription=message 9]");
    }

    private static GetObservedDeviceResponse createObservedDeviceResponse() {
        return GetObservedDeviceResponse.newBuilder()
                .setDevice(
                        Rpcs.Device.newBuilder()
                                .setId(DEVICE_ID)
                                .setName(DEVICE_NAME)
                                .setAntiSpoofingKeyPair(
                                        Rpcs.AntiSpoofingKeyPair
                                                .newBuilder()
                                                .setPublicKey(
                                                        ByteString.copyFrom(DEVICE_PUBLIC_KEY)))
                                .setIntentUri(APP_ACTION_URL)
                                .setDataOnlyConnection(true)
                                .setAssistantSupported(false)
                                .setCompanionDetail(
                                        Rpcs.CompanionAppDetails.newBuilder()
                                                .setAuthority(AUTHORITY)
                                                .setCertificateHash(SIGNATURE_HASH)
                                                .build())
                                .setCompanyName(DEVICE_COMPANY)
                                .setImageUrl(DEVICE_IMAGE_URL))
                .setImage(ByteString.copyFrom(DEVICE_IMAGE))
                .setStrings(
                        Rpcs.ObservedDeviceStrings.newBuilder()
                                .setInitialNotificationDescription(MESSAGE_INIT_NOTIFY_DESCRIPTION)
                                .setInitialNotificationDescriptionNoAccount(
                                        MESSAGE_INIT_NOTIFY_DESCRIPTION_NO_ACCOUNT)
                                .setInitialPairingDescription(MESSAGE_INIT_PAIR_DESCRIPTION)
                                .setConnectSuccessCompanionAppInstalled(MESSAGE_COMPANION_INSTALLED)
                                .setConnectSuccessCompanionAppNotInstalled(
                                        MESSAGE_COMPANION_NOT_INSTALLED)
                                .setSubsequentPairingDescription(
                                        MESSAGE_SUBSEQUENT_PAIR_DESCRIPTION)
                                .setRetroactivePairingDescription(
                                        MESSAGE_RETROACTIVE_PAIR_DESCRIPTION)
                                .setWaitLaunchCompanionAppDescription(
                                        MESSAGE_WAIT_LAUNCH_COMPANION_APP_DESCRIPTION)
                                .setFailConnectGoToSettingsDescription(
                                        MESSAGE_FAIL_CONNECT_DESCRIPTION))
                .build();
    }
}
