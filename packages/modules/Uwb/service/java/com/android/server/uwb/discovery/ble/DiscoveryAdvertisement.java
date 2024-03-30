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
package com.android.server.uwb.discovery.ble;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.os.ParcelUuid;
import android.util.Log;

import com.android.server.uwb.discovery.info.FiraProfileSupportInfo;
import com.android.server.uwb.discovery.info.RegulatoryInfo;
import com.android.server.uwb.discovery.info.UwbIndicationData;
import com.android.server.uwb.discovery.info.VendorSpecificData;
import com.android.server.uwb.util.ArrayUtils;
import com.android.server.uwb.util.DataTypeConversionUtil;
import com.android.server.uwb.util.Hex;

import com.google.common.primitives.Bytes;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Holds data of the BLE discovery advertisement according to FiRa BLE OOB v1.0 specification.
 */
public class DiscoveryAdvertisement {
    private static final String LOG_TAG = DiscoveryAdvertisement.class.getSimpleName();

    // The FiRa service UUID for connector primary and connector secondary as defined in Bluetooth
    // Specification Supplement v10.
    public static final String FIRA_CP_SERVICE_UUID = "FFF3";
    public static final String FIRA_CS_SERVICE_UUID = "FFF4";

    /**
     * Generate a Parcelable wrapper around UUID.
     *
     * @param uuid 16-bit ID (4 characters hex) assigned by Bluetooth specification for a particular
     *     service.
     * @return full 128-bit {@link ParcelUuid}, else null if invalid.
     */
    public static ParcelUuid getParcelUuid(String uuid) {
        if (uuid.length() != 4) {
            throw new IllegalStateException(
                    String.format(
                            "Failed to getParcelUuid from UUID string %s. UUID is expected to be 4"
                                    + " characters",
                            uuid));
        }
        return ParcelUuid.fromString("0000" + uuid + "-0000-1000-8000-00805F9B34FB");
    }

    // Size of the fields inside the advertisement.
    private static final int LENGTH_SIZE = 1;
    private static final int DATA_TYPE_SIZE = 1;
    private static final int SERVICE_UUID_SIZE = 2;

    private static final int MIN_ADVETISEMENT_SIZE =
            LENGTH_SIZE + DATA_TYPE_SIZE + SERVICE_UUID_SIZE;

    // Data type field value assigned by the Bluetooth GAP.
    private static final byte DATA_TYPE = 0x16;

    // Mask and value of the FiRa specific field type field within each AD field.
    private static final byte FIRA_SPECIFIC_FIELD_TYPE_MASK = (byte) 0xF0;
    private static final byte FIRA_SPECIFIC_FIELD_TYPE_UWB_INDICATION_DATA = 0x1;
    private static final byte FIRA_SPECIFIC_FIELD_TYPE_VENDOR_SPECIFIC_DATA = 0x2;
    private static final byte FIRA_SPECIFIC_FIELD_TYPE_UWB_REGULATORY_INFO = 0x3;
    private static final byte FIRA_SPECIFIC_FIELD_TYPE_FIRA_PROFILE_SUPPORT_INFO = 0x4;

    // FiRa specific field length field within each AD field.
    private static final byte FIRA_SPECIFIC_FIELD_LENGTH_MASK = 0x0F;

    public final String serviceUuid;
    public final UwbIndicationData uwbIndicationData;
    public final RegulatoryInfo regulatoryInfo;
    public final FiraProfileSupportInfo firaProfileSupportInfo;
    public final VendorSpecificData[] vendorSpecificData;

    /**
     * Generate the DiscoveryAdvertisement from raw bytes arrays.
     *
     * @param serviceData byte array containing the UWB BLE Advertiser Service Data encoding based
     *     on the FiRa specification.
     * @param manufacturerSpecificData byte array containing the UWB BLE Advertiser Manufacturer
     *     Specific Data encoding based on the FiRa specification.
     * @return decode bytes into {@link DiscoveryAdvertisement}, else null if invalid.
     */
    @Nullable
    public static DiscoveryAdvertisement fromBytes(
            @Nullable byte[] serviceData, @Nullable byte[] manufacturerSpecificData) {
        if (ArrayUtils.isEmpty(serviceData)) {
            logw("Failed to convert empty into BLE Discovery advertisement.");
            return null;
        }

        if (serviceData.length < MIN_ADVETISEMENT_SIZE) {
            logw(
                    "Failed to convert bytes into BLE Discovery advertisement due to invalid"
                            + " advertisement size.");
            return null;
        }

        ByteBuffer byteBuffer = ByteBuffer.wrap(serviceData);
        int length = Byte.toUnsignedInt(byteBuffer.get());
        if (length != serviceData.length - LENGTH_SIZE) {
            logw(
                    "Failed to convert bytes into BLE Discovery advertisement due to unmatched"
                            + " advertisement size.");
            return null;
        }

        byte dataType = byteBuffer.get();
        if (dataType != DATA_TYPE) {
            logw(
                    "Failed to convert bytes into BLE Discovery advertisement due to unmatched"
                            + " advertisement data type.");
            return null;
        }
        // In little endian encoding
        byte[] serviceUuidBytes = new byte[SERVICE_UUID_SIZE];
        byteBuffer.get(serviceUuidBytes);
        String serviceUuid = Hex.encodeUpper(new byte[] {serviceUuidBytes[1], serviceUuidBytes[0]});
        if (!serviceUuid.equals(FIRA_CP_SERVICE_UUID)
                && !serviceUuid.equals(FIRA_CS_SERVICE_UUID)) {
            logw(
                    "Failed to convert bytes into BLE Discovery advertisement due to invalid FiRa"
                            + " advertisement service uuid="
                            + serviceUuid);
            return null;
        }

        UwbIndicationData uwbIndicationData = null;
        RegulatoryInfo regulatoryInfo = null;
        FiraProfileSupportInfo firaProfileSupportInfo = null;
        List<VendorSpecificData> vendorSpecificData = new ArrayList<>();

        while (byteBuffer.hasRemaining()) {
            // Parsing the next block of FiRa specific field based on given field type and length.
            byte firstByte = byteBuffer.get();
            byte fieldType = (byte) ((firstByte & FIRA_SPECIFIC_FIELD_TYPE_MASK) >> 4);
            byte fieldLength = (byte) (firstByte & FIRA_SPECIFIC_FIELD_LENGTH_MASK);
            if (byteBuffer.remaining() < fieldLength) {
                logw(
                        "Failed to convert bytes into BLE Discovery advertisement due to byte"
                                + " ended unexpectedly.");
                return null;
            }
            byte[] fieldBytes = new byte[fieldLength];
            byteBuffer.get(fieldBytes);

            if (fieldType == FIRA_SPECIFIC_FIELD_TYPE_UWB_INDICATION_DATA) {
                if (uwbIndicationData != null) {
                    logw(
                            "Failed to convert bytes into BLE Discovery advertisement due to"
                                    + " duplicate uwb indication data field.");
                    return null;
                }
                uwbIndicationData = UwbIndicationData.fromBytes(fieldBytes);
            } else if (fieldType == FIRA_SPECIFIC_FIELD_TYPE_UWB_REGULATORY_INFO) {
                if (regulatoryInfo != null) {
                    logw(
                            "Failed to convert bytes into BLE Discovery advertisement due to"
                                    + " duplicate regulatory info field.");
                    return null;
                }
                regulatoryInfo = RegulatoryInfo.fromBytes(fieldBytes);
            } else if (fieldType == FIRA_SPECIFIC_FIELD_TYPE_FIRA_PROFILE_SUPPORT_INFO) {
                if (firaProfileSupportInfo != null) {
                    logw(
                            "Failed to convert bytes into BLE Discovery advertisement due to"
                                    + " duplicate FiRa profile support info field.");
                    return null;
                }
                firaProfileSupportInfo = FiraProfileSupportInfo.fromBytes(fieldBytes);
            } else if (fieldType == FIRA_SPECIFIC_FIELD_TYPE_VENDOR_SPECIFIC_DATA) {
                // There can be multiple Vendor specific data fields.
                VendorSpecificData data = VendorSpecificData.fromBytes(fieldBytes);
                if (data != null) {
                    vendorSpecificData.add(data);
                }
            } else {
                logw(
                        "Failed to convert bytes into BLE Discovery advertisement due to invalid"
                                + " field type "
                                + fieldType);
                return null;
            }
        }

        // product/implementation specific data inside “Service Data” AD type object with CS UUID.
        // It should be used only if the GAP Advertiser role doesn’t support exposing “Manufacturer
        // Specific Data” AD type object.
        if (!ArrayUtils.isEmpty(manufacturerSpecificData)) {
            ByteBuffer vendorByteBuffer = ByteBuffer.wrap(manufacturerSpecificData);
            byte firstByte = vendorByteBuffer.get();
            byte fieldType = (byte) ((firstByte & FIRA_SPECIFIC_FIELD_TYPE_MASK) >> 4);
            byte fieldLength = (byte) (firstByte & FIRA_SPECIFIC_FIELD_LENGTH_MASK);
            if (fieldType == FIRA_SPECIFIC_FIELD_TYPE_VENDOR_SPECIFIC_DATA) {
                if (vendorByteBuffer.remaining() < fieldLength) {
                    logw(
                            "Failed to convert bytes into BLE Discovery advertisement due to"
                                    + " manufacturer specific data ended unexpectedly.");
                    return null;
                }
                byte[] fieldBytes = new byte[fieldLength];
                vendorByteBuffer.get(fieldBytes);
                VendorSpecificData data = VendorSpecificData.fromBytes(fieldBytes);
                if (!vendorSpecificData.isEmpty()) {
                    logw(
                            "Failed to convert bytes into BLE Discovery advertisement due to Vendor"
                                + " Specific Data exist in both Service Data AD and Manufacturer"
                                + " Specific Data AD.");
                    return null;
                }
                vendorSpecificData.add(data);
            }
        }

        return new DiscoveryAdvertisement(
                serviceUuid,
                uwbIndicationData,
                regulatoryInfo,
                firaProfileSupportInfo,
                vendorSpecificData.toArray(new VendorSpecificData[0]));
    }

    /**
     * Generate raw bytes array from DiscoveryAdvertisement.
     *
     * @param adv the UWB BLE discovery Advertisement.
     * @param includeVendorSpecificData specify if the vendorSpecificData to be included in the
     *     advertisement bytes.
     * @return encoded bytes into byte array based on the FiRa specification.
     */
    public static byte[] toBytes(
            @NonNull DiscoveryAdvertisement adv, boolean includeVendorSpecificData) {
        byte[] data = convertMetadata(adv.serviceUuid);

        if (adv.uwbIndicationData != null) {
            data = Bytes.concat(data, convertUwbIndicationData(adv.uwbIndicationData));
        }
        if (adv.regulatoryInfo != null) {
            data = Bytes.concat(data, convertRegulatoryInfo(adv.regulatoryInfo));
        }
        if (adv.firaProfileSupportInfo != null) {
            data = Bytes.concat(data, convertFiraProfileSupportInfo(adv.firaProfileSupportInfo));
        }
        if (includeVendorSpecificData) {
            for (VendorSpecificData d : adv.vendorSpecificData) {
                data = Bytes.concat(data, convertVendorSpecificData(d));
            }
        }

        return Bytes.concat(new byte[] {convertByteLength(data.length)}, data);
    }

    /**
     * Generate raw bytes array from DiscoveryAdvertisement.vendorSpecificData.
     *
     * @param adv the UWB BLE discovery Advertisement.
     * @return encoded Manufacturer Specific Data into byte array based on the FiRa specification.
     */
    public static byte[] getManufacturerSpecificDataInBytes(@NonNull DiscoveryAdvertisement adv) {
        if (adv.vendorSpecificData.length > 0) {
            return convertVendorSpecificData(adv.vendorSpecificData[0]);
        }
        return null;
    }

    private static byte[] convertMetadata(String serviceUuid) {
        byte[] uuidBytes = Hex.decode(serviceUuid);
        return new byte[] {DATA_TYPE, uuidBytes[1], uuidBytes[0]};
    }

    private static byte convertByteLength(int size) {
        return DataTypeConversionUtil.i32ToByteArray(size)[3];
    }

    private static byte[] convertUwbIndicationData(UwbIndicationData uwbIndicationData) {
        byte[] data = UwbIndicationData.toBytes(uwbIndicationData);
        return Bytes.concat(
                new byte[] {
                    (byte)
                            (((FIRA_SPECIFIC_FIELD_TYPE_UWB_INDICATION_DATA << 4)
                                            & FIRA_SPECIFIC_FIELD_TYPE_MASK)
                                    | (convertByteLength(data.length)
                                            & FIRA_SPECIFIC_FIELD_LENGTH_MASK))
                },
                data);
    }

    private static byte[] convertRegulatoryInfo(RegulatoryInfo regulatoryInfo) {
        byte[] data = RegulatoryInfo.toBytes(regulatoryInfo);
        return Bytes.concat(
                new byte[] {
                    (byte)
                            (((FIRA_SPECIFIC_FIELD_TYPE_UWB_REGULATORY_INFO << 4)
                                            & FIRA_SPECIFIC_FIELD_TYPE_MASK)
                                    | (convertByteLength(data.length)
                                            & FIRA_SPECIFIC_FIELD_LENGTH_MASK))
                },
                data);
    }

    private static byte[] convertFiraProfileSupportInfo(
            FiraProfileSupportInfo firaProfileSupportInfo) {
        byte[] data = FiraProfileSupportInfo.toBytes(firaProfileSupportInfo);
        return Bytes.concat(
                new byte[] {
                    (byte)
                            (((FIRA_SPECIFIC_FIELD_TYPE_FIRA_PROFILE_SUPPORT_INFO << 4)
                                            & FIRA_SPECIFIC_FIELD_TYPE_MASK)
                                    | (convertByteLength(data.length)
                                            & FIRA_SPECIFIC_FIELD_LENGTH_MASK))
                },
                data);
    }

    private static byte[] convertVendorSpecificData(VendorSpecificData vendorSpecificData) {
        byte[] data = VendorSpecificData.toBytes(vendorSpecificData);
        return Bytes.concat(
                new byte[] {
                    (byte)
                            (((FIRA_SPECIFIC_FIELD_TYPE_VENDOR_SPECIFIC_DATA << 4)
                                            & FIRA_SPECIFIC_FIELD_TYPE_MASK)
                                    | (convertByteLength(data.length)
                                            & FIRA_SPECIFIC_FIELD_LENGTH_MASK))
                },
                data);
    }

    public DiscoveryAdvertisement(
            String serviceUuid,
            @Nullable UwbIndicationData uwbIndicationData,
            @Nullable RegulatoryInfo regulatoryInfo,
            @Nullable FiraProfileSupportInfo firaProfileSupportInfo,
            @Nullable VendorSpecificData[] vendorSpecificData) {
        this.serviceUuid = serviceUuid;
        this.uwbIndicationData = uwbIndicationData;
        this.regulatoryInfo = regulatoryInfo;
        this.firaProfileSupportInfo = firaProfileSupportInfo;
        this.vendorSpecificData = vendorSpecificData;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("DiscoveryAdvertisement: serviceUuid=")
                .append(serviceUuid)
                .append(" uwbIndicationData={")
                .append(uwbIndicationData)
                .append("} regulatoryInfo={")
                .append(regulatoryInfo)
                .append("} firaProfileSupportInfo={")
                .append(firaProfileSupportInfo)
                .append("} ")
                .append(Arrays.toString(vendorSpecificData));
        return sb.toString();
    }

    private static void logw(String log) {
        Log.w(LOG_TAG, log);
    }
}
