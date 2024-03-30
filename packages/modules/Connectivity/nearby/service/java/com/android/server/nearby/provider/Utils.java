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

package com.android.server.nearby.provider;

import android.accounts.Account;
import android.annotation.Nullable;
import android.nearby.aidl.FastPairAccountKeyDeviceMetadataParcel;
import android.nearby.aidl.FastPairAntispoofKeyDeviceMetadataParcel;
import android.nearby.aidl.FastPairDeviceMetadataParcel;
import android.nearby.aidl.FastPairDiscoveryItemParcel;
import android.nearby.aidl.FastPairEligibleAccountParcel;

import com.android.server.nearby.fastpair.footprint.FastPairUploadInfo;

import com.google.protobuf.ByteString;

import java.util.ArrayList;
import java.util.List;

import service.proto.Cache;
import service.proto.Data;
import service.proto.FastPairString.FastPairStrings;
import service.proto.Rpcs;

/**
 * Utility functions to convert between different data classes.
 */
class Utils {

    static List<Data.FastPairDeviceWithAccountKey> convertToFastPairDevicesWithAccountKey(
            @Nullable FastPairAccountKeyDeviceMetadataParcel[] metadataParcels) {
        if (metadataParcels == null) {
            return new ArrayList<Data.FastPairDeviceWithAccountKey>(0);
        }

        List<Data.FastPairDeviceWithAccountKey> fpDeviceList =
                new ArrayList<>(metadataParcels.length);
        for (FastPairAccountKeyDeviceMetadataParcel metadataParcel : metadataParcels) {
            if (metadataParcel == null) {
                continue;
            }
            Data.FastPairDeviceWithAccountKey.Builder fpDeviceBuilder =
                    Data.FastPairDeviceWithAccountKey.newBuilder();
            if (metadataParcel.deviceAccountKey != null) {
                fpDeviceBuilder.setAccountKey(
                        ByteString.copyFrom(metadataParcel.deviceAccountKey));
            }
            if (metadataParcel.sha256DeviceAccountKeyPublicAddress != null) {
                fpDeviceBuilder.setSha256AccountKeyPublicAddress(
                        ByteString.copyFrom(metadataParcel.sha256DeviceAccountKeyPublicAddress));
            }

            Cache.StoredDiscoveryItem.Builder storedDiscoveryItemBuilder =
                    Cache.StoredDiscoveryItem.newBuilder();

            if (metadataParcel.discoveryItem != null) {
                if (metadataParcel.discoveryItem.actionUrl != null) {
                    storedDiscoveryItemBuilder.setActionUrl(metadataParcel.discoveryItem.actionUrl);
                }
                Cache.ResolvedUrlType urlType = Cache.ResolvedUrlType.forNumber(
                        metadataParcel.discoveryItem.actionUrlType);
                if (urlType != null) {
                    storedDiscoveryItemBuilder.setActionUrlType(urlType);
                }
                if (metadataParcel.discoveryItem.appName != null) {
                    storedDiscoveryItemBuilder.setAppName(metadataParcel.discoveryItem.appName);
                }
                if (metadataParcel.discoveryItem.authenticationPublicKeySecp256r1 != null) {
                    storedDiscoveryItemBuilder.setAuthenticationPublicKeySecp256R1(
                            ByteString.copyFrom(
                                    metadataParcel.discoveryItem.authenticationPublicKeySecp256r1));
                }
                if (metadataParcel.discoveryItem.description != null) {
                    storedDiscoveryItemBuilder.setDescription(
                            metadataParcel.discoveryItem.description);
                }
                if (metadataParcel.discoveryItem.deviceName != null) {
                    storedDiscoveryItemBuilder.setDeviceName(
                            metadataParcel.discoveryItem.deviceName);
                }
                if (metadataParcel.discoveryItem.displayUrl != null) {
                    storedDiscoveryItemBuilder.setDisplayUrl(
                            metadataParcel.discoveryItem.displayUrl);
                }
                storedDiscoveryItemBuilder.setFirstObservationTimestampMillis(
                        metadataParcel.discoveryItem.firstObservationTimestampMillis);
                if (metadataParcel.discoveryItem.iconFifeUrl != null) {
                    storedDiscoveryItemBuilder.setIconFifeUrl(
                            metadataParcel.discoveryItem.iconFifeUrl);
                }
                if (metadataParcel.discoveryItem.iconPng != null) {
                    storedDiscoveryItemBuilder.setIconPng(
                            ByteString.copyFrom(metadataParcel.discoveryItem.iconPng));
                }
                if (metadataParcel.discoveryItem.id != null) {
                    storedDiscoveryItemBuilder.setId(metadataParcel.discoveryItem.id);
                }
                storedDiscoveryItemBuilder.setLastObservationTimestampMillis(
                        metadataParcel.discoveryItem.lastObservationTimestampMillis);
                if (metadataParcel.discoveryItem.macAddress != null) {
                    storedDiscoveryItemBuilder.setMacAddress(
                            metadataParcel.discoveryItem.macAddress);
                }
                if (metadataParcel.discoveryItem.packageName != null) {
                    storedDiscoveryItemBuilder.setPackageName(
                            metadataParcel.discoveryItem.packageName);
                }
                storedDiscoveryItemBuilder.setPendingAppInstallTimestampMillis(
                        metadataParcel.discoveryItem.pendingAppInstallTimestampMillis);
                storedDiscoveryItemBuilder.setRssi(metadataParcel.discoveryItem.rssi);
                Cache.StoredDiscoveryItem.State state =
                        Cache.StoredDiscoveryItem.State.forNumber(
                                metadataParcel.discoveryItem.state);
                if (state != null) {
                    storedDiscoveryItemBuilder.setState(state);
                }
                if (metadataParcel.discoveryItem.title != null) {
                    storedDiscoveryItemBuilder.setTitle(metadataParcel.discoveryItem.title);
                }
                if (metadataParcel.discoveryItem.triggerId != null) {
                    storedDiscoveryItemBuilder.setTriggerId(metadataParcel.discoveryItem.triggerId);
                }
                storedDiscoveryItemBuilder.setTxPower(metadataParcel.discoveryItem.txPower);
            }
            if (metadataParcel.metadata != null) {
                FastPairStrings.Builder stringsBuilder = FastPairStrings.newBuilder();
                if (metadataParcel.metadata.connectSuccessCompanionAppInstalled != null) {
                    stringsBuilder.setPairingFinishedCompanionAppInstalled(
                            metadataParcel.metadata.connectSuccessCompanionAppInstalled);
                }
                if (metadataParcel.metadata.connectSuccessCompanionAppNotInstalled != null) {
                    stringsBuilder.setPairingFinishedCompanionAppNotInstalled(
                            metadataParcel.metadata.connectSuccessCompanionAppNotInstalled);
                }
                if (metadataParcel.metadata.failConnectGoToSettingsDescription != null) {
                    stringsBuilder.setPairingFailDescription(
                            metadataParcel.metadata.failConnectGoToSettingsDescription);
                }
                if (metadataParcel.metadata.initialNotificationDescription != null) {
                    stringsBuilder.setTapToPairWithAccount(
                            metadataParcel.metadata.initialNotificationDescription);
                }
                if (metadataParcel.metadata.initialNotificationDescriptionNoAccount != null) {
                    stringsBuilder.setTapToPairWithoutAccount(
                            metadataParcel.metadata.initialNotificationDescriptionNoAccount);
                }
                if (metadataParcel.metadata.initialPairingDescription != null) {
                    stringsBuilder.setInitialPairingDescription(
                            metadataParcel.metadata.initialPairingDescription);
                }
                if (metadataParcel.metadata.retroactivePairingDescription != null) {
                    stringsBuilder.setRetroactivePairingDescription(
                            metadataParcel.metadata.retroactivePairingDescription);
                }
                if (metadataParcel.metadata.subsequentPairingDescription != null) {
                    stringsBuilder.setSubsequentPairingDescription(
                            metadataParcel.metadata.subsequentPairingDescription);
                }
                if (metadataParcel.metadata.waitLaunchCompanionAppDescription != null) {
                    stringsBuilder.setWaitAppLaunchDescription(
                            metadataParcel.metadata.waitLaunchCompanionAppDescription);
                }
                storedDiscoveryItemBuilder.setFastPairStrings(stringsBuilder.build());

                Cache.FastPairInformation.Builder fpInformationBuilder =
                        Cache.FastPairInformation.newBuilder();
                Rpcs.TrueWirelessHeadsetImages.Builder imagesBuilder =
                        Rpcs.TrueWirelessHeadsetImages.newBuilder();
                if (metadataParcel.metadata.trueWirelessImageUrlCase != null) {
                    imagesBuilder.setCaseUrl(metadataParcel.metadata.trueWirelessImageUrlCase);
                }
                if (metadataParcel.metadata.trueWirelessImageUrlLeftBud != null) {
                    imagesBuilder.setLeftBudUrl(
                            metadataParcel.metadata.trueWirelessImageUrlLeftBud);
                }
                if (metadataParcel.metadata.trueWirelessImageUrlRightBud != null) {
                    imagesBuilder.setRightBudUrl(
                            metadataParcel.metadata.trueWirelessImageUrlRightBud);
                }
                fpInformationBuilder.setTrueWirelessImages(imagesBuilder.build());
                Rpcs.DeviceType deviceType =
                        Rpcs.DeviceType.forNumber(metadataParcel.metadata.deviceType);
                if (deviceType != null) {
                    fpInformationBuilder.setDeviceType(deviceType);
                }

                storedDiscoveryItemBuilder.setFastPairInformation(fpInformationBuilder.build());
            }
            fpDeviceBuilder.setDiscoveryItem(storedDiscoveryItemBuilder.build());
            fpDeviceList.add(fpDeviceBuilder.build());
        }
        return fpDeviceList;
    }

    static List<Account> convertToAccountList(
            @Nullable FastPairEligibleAccountParcel[] accountParcels) {
        if (accountParcels == null) {
            return new ArrayList<Account>(0);
        }
        List<Account> accounts = new ArrayList<Account>(accountParcels.length);
        for (FastPairEligibleAccountParcel parcel : accountParcels) {
            if (parcel != null && parcel.account != null) {
                accounts.add(parcel.account);
            }
        }
        return accounts;
    }

    private static @Nullable Rpcs.Device convertToDevice(
            FastPairAntispoofKeyDeviceMetadataParcel metadata) {

        Rpcs.Device.Builder deviceBuilder = Rpcs.Device.newBuilder();
        if (metadata.antispoofPublicKey != null) {
            deviceBuilder.setAntiSpoofingKeyPair(Rpcs.AntiSpoofingKeyPair.newBuilder()
                    .setPublicKey(ByteString.copyFrom(metadata.antispoofPublicKey))
                    .build());
        }
        if (metadata.deviceMetadata != null) {
            Rpcs.TrueWirelessHeadsetImages.Builder imagesBuilder =
                    Rpcs.TrueWirelessHeadsetImages.newBuilder();
            if (metadata.deviceMetadata.trueWirelessImageUrlLeftBud != null) {
                imagesBuilder.setLeftBudUrl(metadata.deviceMetadata.trueWirelessImageUrlLeftBud);
            }
            if (metadata.deviceMetadata.trueWirelessImageUrlRightBud != null) {
                imagesBuilder.setRightBudUrl(metadata.deviceMetadata.trueWirelessImageUrlRightBud);
            }
            if (metadata.deviceMetadata.trueWirelessImageUrlCase != null) {
                imagesBuilder.setCaseUrl(metadata.deviceMetadata.trueWirelessImageUrlCase);
            }
            deviceBuilder.setTrueWirelessImages(imagesBuilder.build());
            if (metadata.deviceMetadata.imageUrl != null) {
                deviceBuilder.setImageUrl(metadata.deviceMetadata.imageUrl);
            }
            if (metadata.deviceMetadata.intentUri != null) {
                deviceBuilder.setIntentUri(metadata.deviceMetadata.intentUri);
            }
            if (metadata.deviceMetadata.name != null) {
                deviceBuilder.setName(metadata.deviceMetadata.name);
            }
            Rpcs.DeviceType deviceType =
                    Rpcs.DeviceType.forNumber(metadata.deviceMetadata.deviceType);
            if (deviceType != null) {
                deviceBuilder.setDeviceType(deviceType);
            }
            deviceBuilder.setBleTxPower(metadata.deviceMetadata.bleTxPower)
                    .setTriggerDistance(metadata.deviceMetadata.triggerDistance);
        }

        return deviceBuilder.build();
    }

    private static @Nullable ByteString convertToImage(
            FastPairAntispoofKeyDeviceMetadataParcel metadata) {
        if (metadata.deviceMetadata == null || metadata.deviceMetadata.image == null) {
            return null;
        }

        return ByteString.copyFrom(metadata.deviceMetadata.image);
    }

    private static @Nullable Rpcs.ObservedDeviceStrings
            convertToObservedDeviceStrings(FastPairAntispoofKeyDeviceMetadataParcel metadata) {
        if (metadata.deviceMetadata == null) {
            return null;
        }

        Rpcs.ObservedDeviceStrings.Builder stringsBuilder = Rpcs.ObservedDeviceStrings.newBuilder();
        if (metadata.deviceMetadata.connectSuccessCompanionAppInstalled != null) {
            stringsBuilder.setConnectSuccessCompanionAppInstalled(
                    metadata.deviceMetadata.connectSuccessCompanionAppInstalled);
        }
        if (metadata.deviceMetadata.connectSuccessCompanionAppNotInstalled != null) {
            stringsBuilder.setConnectSuccessCompanionAppNotInstalled(
                    metadata.deviceMetadata.connectSuccessCompanionAppNotInstalled);
        }
        if (metadata.deviceMetadata.downloadCompanionAppDescription != null) {
            stringsBuilder.setDownloadCompanionAppDescription(
                    metadata.deviceMetadata.downloadCompanionAppDescription);
        }
        if (metadata.deviceMetadata.failConnectGoToSettingsDescription != null) {
            stringsBuilder.setFailConnectGoToSettingsDescription(
                    metadata.deviceMetadata.failConnectGoToSettingsDescription);
        }
        if (metadata.deviceMetadata.initialNotificationDescription != null) {
            stringsBuilder.setInitialNotificationDescription(
                    metadata.deviceMetadata.initialNotificationDescription);
        }
        if (metadata.deviceMetadata.initialNotificationDescriptionNoAccount != null) {
            stringsBuilder.setInitialNotificationDescriptionNoAccount(
                    metadata.deviceMetadata.initialNotificationDescriptionNoAccount);
        }
        if (metadata.deviceMetadata.initialPairingDescription != null) {
            stringsBuilder.setInitialPairingDescription(
                    metadata.deviceMetadata.initialPairingDescription);
        }
        if (metadata.deviceMetadata.openCompanionAppDescription != null) {
            stringsBuilder.setOpenCompanionAppDescription(
                    metadata.deviceMetadata.openCompanionAppDescription);
        }
        if (metadata.deviceMetadata.retroactivePairingDescription != null) {
            stringsBuilder.setRetroactivePairingDescription(
                    metadata.deviceMetadata.retroactivePairingDescription);
        }
        if (metadata.deviceMetadata.subsequentPairingDescription != null) {
            stringsBuilder.setSubsequentPairingDescription(
                    metadata.deviceMetadata.subsequentPairingDescription);
        }
        if (metadata.deviceMetadata.unableToConnectDescription != null) {
            stringsBuilder.setUnableToConnectDescription(
                    metadata.deviceMetadata.unableToConnectDescription);
        }
        if (metadata.deviceMetadata.unableToConnectTitle != null) {
            stringsBuilder.setUnableToConnectTitle(
                    metadata.deviceMetadata.unableToConnectTitle);
        }
        if (metadata.deviceMetadata.updateCompanionAppDescription != null) {
            stringsBuilder.setUpdateCompanionAppDescription(
                    metadata.deviceMetadata.updateCompanionAppDescription);
        }
        if (metadata.deviceMetadata.waitLaunchCompanionAppDescription != null) {
            stringsBuilder.setWaitLaunchCompanionAppDescription(
                    metadata.deviceMetadata.waitLaunchCompanionAppDescription);
        }

        return stringsBuilder.build();
    }

    static @Nullable Rpcs.GetObservedDeviceResponse
            convertToGetObservedDeviceResponse(
                    @Nullable FastPairAntispoofKeyDeviceMetadataParcel metadata) {
        if (metadata == null) {
            return null;
        }

        Rpcs.GetObservedDeviceResponse.Builder responseBuilder =
                Rpcs.GetObservedDeviceResponse.newBuilder();

        Rpcs.Device device = convertToDevice(metadata);
        if (device != null) {
            responseBuilder.setDevice(device);
        }
        ByteString image = convertToImage(metadata);
        if (image != null) {
            responseBuilder.setImage(image);
        }
        Rpcs.ObservedDeviceStrings strings = convertToObservedDeviceStrings(metadata);
        if (strings != null) {
            responseBuilder.setStrings(strings);
        }

        return responseBuilder.build();
    }

    static @Nullable FastPairAccountKeyDeviceMetadataParcel
            convertToFastPairAccountKeyDeviceMetadata(
            @Nullable FastPairUploadInfo uploadInfo) {
        if (uploadInfo == null) {
            return null;
        }

        FastPairAccountKeyDeviceMetadataParcel accountKeyDeviceMetadataParcel =
                new FastPairAccountKeyDeviceMetadataParcel();
        if (uploadInfo.getAccountKey() != null) {
            accountKeyDeviceMetadataParcel.deviceAccountKey =
                    uploadInfo.getAccountKey().toByteArray();
        }
        if (uploadInfo.getSha256AccountKeyPublicAddress() != null) {
            accountKeyDeviceMetadataParcel.sha256DeviceAccountKeyPublicAddress =
                    uploadInfo.getSha256AccountKeyPublicAddress().toByteArray();
        }
        if (uploadInfo.getStoredDiscoveryItem() != null) {
            accountKeyDeviceMetadataParcel.metadata =
                    convertToFastPairDeviceMetadata(uploadInfo.getStoredDiscoveryItem());
            accountKeyDeviceMetadataParcel.discoveryItem =
                    convertToFastPairDiscoveryItem(uploadInfo.getStoredDiscoveryItem());
        }

        return accountKeyDeviceMetadataParcel;
    }

    private static @Nullable FastPairDiscoveryItemParcel
            convertToFastPairDiscoveryItem(Cache.StoredDiscoveryItem storedDiscoveryItem) {
        FastPairDiscoveryItemParcel discoveryItemParcel = new FastPairDiscoveryItemParcel();
        discoveryItemParcel.actionUrl = storedDiscoveryItem.getActionUrl();
        discoveryItemParcel.actionUrlType = storedDiscoveryItem.getActionUrlType().getNumber();
        discoveryItemParcel.appName = storedDiscoveryItem.getAppName();
        discoveryItemParcel.authenticationPublicKeySecp256r1 =
                storedDiscoveryItem.getAuthenticationPublicKeySecp256R1().toByteArray();
        discoveryItemParcel.description = storedDiscoveryItem.getDescription();
        discoveryItemParcel.deviceName = storedDiscoveryItem.getDeviceName();
        discoveryItemParcel.displayUrl = storedDiscoveryItem.getDisplayUrl();
        discoveryItemParcel.firstObservationTimestampMillis =
                storedDiscoveryItem.getFirstObservationTimestampMillis();
        discoveryItemParcel.iconFifeUrl = storedDiscoveryItem.getIconFifeUrl();
        discoveryItemParcel.iconPng = storedDiscoveryItem.getIconPng().toByteArray();
        discoveryItemParcel.id = storedDiscoveryItem.getId();
        discoveryItemParcel.lastObservationTimestampMillis =
                storedDiscoveryItem.getLastObservationTimestampMillis();
        discoveryItemParcel.macAddress = storedDiscoveryItem.getMacAddress();
        discoveryItemParcel.packageName = storedDiscoveryItem.getPackageName();
        discoveryItemParcel.pendingAppInstallTimestampMillis =
                storedDiscoveryItem.getPendingAppInstallTimestampMillis();
        discoveryItemParcel.rssi = storedDiscoveryItem.getRssi();
        discoveryItemParcel.state = storedDiscoveryItem.getState().getNumber();
        discoveryItemParcel.title = storedDiscoveryItem.getTitle();
        discoveryItemParcel.triggerId = storedDiscoveryItem.getTriggerId();
        discoveryItemParcel.txPower = storedDiscoveryItem.getTxPower();

        return discoveryItemParcel;
    }

    /*  Do we upload these?
        String downloadCompanionAppDescription =
             bundle.getString("downloadCompanionAppDescription");
        String locale = bundle.getString("locale");
        String openCompanionAppDescription = bundle.getString("openCompanionAppDescription");
        float triggerDistance = bundle.getFloat("triggerDistance");
        String unableToConnectDescription = bundle.getString("unableToConnectDescription");
        String unableToConnectTitle = bundle.getString("unableToConnectTitle");
        String updateCompanionAppDescription = bundle.getString("updateCompanionAppDescription");
    */
    private static @Nullable FastPairDeviceMetadataParcel
            convertToFastPairDeviceMetadata(Cache.StoredDiscoveryItem storedDiscoveryItem) {
        FastPairStrings fpStrings = storedDiscoveryItem.getFastPairStrings();

        FastPairDeviceMetadataParcel metadataParcel = new FastPairDeviceMetadataParcel();
        metadataParcel.connectSuccessCompanionAppInstalled =
                fpStrings.getPairingFinishedCompanionAppInstalled();
        metadataParcel.connectSuccessCompanionAppNotInstalled =
                fpStrings.getPairingFinishedCompanionAppNotInstalled();
        metadataParcel.failConnectGoToSettingsDescription = fpStrings.getPairingFailDescription();
        metadataParcel.initialNotificationDescription = fpStrings.getTapToPairWithAccount();
        metadataParcel.initialNotificationDescriptionNoAccount =
                fpStrings.getTapToPairWithoutAccount();
        metadataParcel.initialPairingDescription = fpStrings.getInitialPairingDescription();
        metadataParcel.retroactivePairingDescription = fpStrings.getRetroactivePairingDescription();
        metadataParcel.subsequentPairingDescription = fpStrings.getSubsequentPairingDescription();
        metadataParcel.waitLaunchCompanionAppDescription = fpStrings.getWaitAppLaunchDescription();

        Cache.FastPairInformation fpInformation = storedDiscoveryItem.getFastPairInformation();
        metadataParcel.trueWirelessImageUrlCase =
                fpInformation.getTrueWirelessImages().getCaseUrl();
        metadataParcel.trueWirelessImageUrlLeftBud =
                fpInformation.getTrueWirelessImages().getLeftBudUrl();
        metadataParcel.trueWirelessImageUrlRightBud =
                fpInformation.getTrueWirelessImages().getRightBudUrl();
        metadataParcel.deviceType = fpInformation.getDeviceType().getNumber();

        return metadataParcel;
    }
}
