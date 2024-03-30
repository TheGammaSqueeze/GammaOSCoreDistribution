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

import static com.google.common.truth.Truth.assertThat;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.server.uwb.discovery.info.ChannelPowerInfo;
import com.android.server.uwb.discovery.info.FiraProfileSupportInfo;
import com.android.server.uwb.discovery.info.FiraProfileSupportInfo.FiraProfile;
import com.android.server.uwb.discovery.info.RegulatoryInfo;
import com.android.server.uwb.discovery.info.RegulatoryInfo.SourceOfInfo;
import com.android.server.uwb.discovery.info.SecureComponentInfo;
import com.android.server.uwb.discovery.info.SecureComponentInfo.SecureComponentProtocolType;
import com.android.server.uwb.discovery.info.SecureComponentInfo.SecureComponentType;
import com.android.server.uwb.discovery.info.UwbIndicationData;
import com.android.server.uwb.discovery.info.VendorSpecificData;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;

/**
 * Unit test for {@link DiscoveryAdvertisement}
 */
@RunWith(AndroidJUnit4.class)
@SmallTest
public class DiscoveryAdvertisementTest {

    private static final byte[] BYTES =
            new byte[] {
                0x20, 0x16, (byte) 0xF4, (byte) 0xFF,
                // UwbIndicationData
                0x14, (byte) 0b11101001, (byte) 0x9C, (byte) 0xF1, 0x11,
                // RegulatoryInfo
                0x39, 0x41, 0x55, 0x53, 0x62, 0x1E, 0x3B, 0x7F, (byte) 0xD8, (byte) 0x9F,
                // FiraProfileSupportInfo
                0x41, 0x1,
                // VendorSpecificData
                0x25, 0x75, 0x00, 0x10, (byte) 0xFF, 0x00,
                // VendorSpecificData
                0x25, 0x75, 0x00, 0x10, (byte) 0xFF, 0x00,
            };
    private static final String SERVICE_UUID = "FFF4";
    private static final byte[] UWB_INDICATION_DATA_BYTES =
            new byte[] {(byte) 0b11101001, (byte) 0x9C, (byte) 0xF1, 0x11};
    private static final byte[] VENDOR_SPECIFIC_DATA_BYTES =
            new byte[] {0x75, 0x00, 0x10, (byte) 0xFF, 0x00};
    private static final byte[] REGULATORY_INFO_BYTES =
            new byte[] {0x41, 0x55, 0x53, 0x62, 0x1E, 0x3B, 0x7F, (byte) 0xD8, (byte) 0x9F};
    private static final byte[] FIRA_PROFILE_SUPPORT_INFO_BYTES = new byte[] {0x1};
    private static final byte[] MIN_BYTES = new byte[] {0x03, 0x16, (byte) 0xF3, (byte) 0xFF};
    private static final byte[] BYTES_NO_VENDOR =
            new byte[] {
                0x14, 0x16, (byte) 0xF4, (byte) 0xFF,
                // UwbIndicationData
                0x14, (byte) 0b11101001, (byte) 0x9C, (byte) 0xF1, 0x11,
                // RegulatoryInfo
                0x39, 0x41, 0x55, 0x53, 0x62, 0x1E, 0x3B, 0x7F, (byte) 0xD8, (byte) 0x9F,
                // FiraProfileSupportInfo
                0x41, 0x1
            };

    private static final DiscoveryAdvertisement ADVERTISEMENT =
            new DiscoveryAdvertisement(
                    DiscoveryAdvertisement.FIRA_CS_SERVICE_UUID,
                    new UwbIndicationData(
                            /*firaUwbSupport=*/ true,
                            /*iso14443Support=*/ true,
                            /*uwbRegulartoryInfoAvailableInAd=*/ true,
                            /*uwbRegulartoryInfoAvailableInOob=*/ false,
                            /*firaProfileInfoAvailableInAd=*/ true,
                            /*firaProfileInfoAvailableInOob=*/ false,
                            /*dualGapRoleSupport=*/ true,
                            /*bluetoothRssiThresholdDbm=*/ -100,
                            new SecureComponentInfo[] {
                                new SecureComponentInfo(
                                        /*staticIndication=*/ true,
                                        /*secid=*/ 113,
                                        SecureComponentType.ESE_NONREMOVABLE,
                                        SecureComponentProtocolType
                                                .FIRA_OOB_ADMINISTRATIVE_PROTOCOL)
                            }),
                    new RegulatoryInfo(
                            SourceOfInfo.SATELLITE_NAVIGATION_SYSTEM,
                            /*outdoorsTransmittionPermitted=*/ true,
                            /*countryCode=*/ "US",
                            /*timestampSecondsSinceEpoch=*/ 1646148479,
                            new ChannelPowerInfo[] {
                                new ChannelPowerInfo(
                                        /*firstChannel=*/ 13,
                                        /*numOfChannels=*/ 4,
                                        /*isIndoor=*/ false,
                                        /*averagePowerLimitDbm=*/ -97)
                            }),
                    new FiraProfileSupportInfo(new FiraProfile[] {FiraProfile.PACS}),
                    new VendorSpecificData[] {
                        new VendorSpecificData(
                                /*firstChannel=*/ 117, new byte[] {0x10, (byte) 0xFF, 0x00}),
                        new VendorSpecificData(
                                /*firstChannel=*/ 117, new byte[] {0x10, (byte) 0xFF, 0x00}),
                    });

    @Test
    public void fromBytes_emptyData() {
        assertThat(DiscoveryAdvertisement.fromBytes(new byte[] {}, null)).isNull();
    }

    @Test
    public void fromBytes_dataTooShort() {
        assertThat(DiscoveryAdvertisement.fromBytes(new byte[] {0x0, 0x1, 0x2}, null)).isNull();
    }

    @Test
    public void fromBytes_unmatedDataSize() {
        // Specified data size is 0xF1, actual size is 8.
        byte[] bytes = new byte[] {(byte) 0xF1, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01};
        assertThat(DiscoveryAdvertisement.fromBytes(bytes, null)).isNull();
    }

    @Test
    public void fromBytes_invalidDataType() {
        // Specified data type is 0x55, expect 0x16.
        byte[] bytes = new byte[] {0x08, 0x55, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01};
        assertThat(DiscoveryAdvertisement.fromBytes(bytes, null)).isNull();
    }

    @Test
    public void fromBytes_invalidServiceUuid() {
        // Specified service uuid is 0xFFFF, expect 0xFFF3 or 0xFFF4.
        byte[] bytes =
                new byte[] {0x08, 0x16, (byte) 0xFF, (byte) 0xFF, 0x01, 0x01, 0x01, 0x01, 0x01};
        assertThat(DiscoveryAdvertisement.fromBytes(bytes, null)).isNull();
    }

    @Test
    public void fromBytes_dataEndedUnexpectedly() {
        // Specified field size is 0xF, actual size is 4.
        byte[] bytes =
                new byte[] {0x08, 0x16, (byte) 0xF3, (byte) 0xFF, 0x1F, 0x01, 0x01, 0x01, 0x01};
        assertThat(DiscoveryAdvertisement.fromBytes(bytes, null)).isNull();
    }

    @Test
    public void fromBytes_invalidFieldType() {
        // Specified field type is 0x9, expect 1-4
        byte[] bytes =
                new byte[] {
                    0x08, 0x16, (byte) 0xF3, (byte) 0xFF, (byte) 0x94, 0x01, 0x01, 0x01, 0x01
                };
        assertThat(DiscoveryAdvertisement.fromBytes(bytes, null)).isNull();
    }

    @Test
    public void fromBytes_vendorSpecificDataExistedInBothAd() {
        // Specified service uuid is 0xFFFF, expect 0xFFF3 or 0xFFF4.
        byte[] bytes =
                new byte[] {0x08, 0x16, (byte) 0xF3, (byte) 0xFF, 0x24, 0x75, 0x00, 0x10, 0x20};
        byte[] vendor_bytes = new byte[] {0x23, 0x75, 0x00, 0x10};
        assertThat(DiscoveryAdvertisement.fromBytes(bytes, vendor_bytes)).isNull();
    }

    @Test
    public void fromBytes_vendorSpecificDataEndedUnexpectedly() {
        // Specified vendor data size is 0x4, actual size is 3.
        byte[] vendor_bytes = new byte[] {0x24, 0x75, 0x00, 0x10};
        assertThat(DiscoveryAdvertisement.fromBytes(MIN_BYTES, vendor_bytes)).isNull();
    }

    @Test
    public void fromBytes_succeed() {
        DiscoveryAdvertisement adv = DiscoveryAdvertisement.fromBytes(BYTES, null);
        assertThat(adv).isNotNull();

        assertThat(adv.serviceUuid).isEqualTo(SERVICE_UUID);
        assertThat(UwbIndicationData.toBytes(adv.uwbIndicationData))
                .isEqualTo(UWB_INDICATION_DATA_BYTES);
        assertThat(RegulatoryInfo.toBytes(adv.regulatoryInfo)).isEqualTo(REGULATORY_INFO_BYTES);
        assertThat(FiraProfileSupportInfo.toBytes(adv.firaProfileSupportInfo))
                .isEqualTo(FIRA_PROFILE_SUPPORT_INFO_BYTES);
        assertThat(adv.vendorSpecificData.length).isEqualTo(2);
        assertThat(VendorSpecificData.toBytes(adv.vendorSpecificData[0]))
                .isEqualTo(VENDOR_SPECIFIC_DATA_BYTES);

        final String expectedString =
                "DiscoveryAdvertisement: serviceUuid="
                        + adv.serviceUuid
                        + " uwbIndicationData={"
                        + adv.uwbIndicationData
                        + "} regulatoryInfo={"
                        + adv.regulatoryInfo
                        + "} firaProfileSupportInfo={"
                        + adv.firaProfileSupportInfo
                        + "} "
                        + Arrays.toString(adv.vendorSpecificData);

        assertThat(ADVERTISEMENT.toString()).isEqualTo(expectedString);
    }

    @Test
    public void toBytes_succeedWithoutVendorData() {
        assertThat(ADVERTISEMENT).isNotNull();

        byte[] result =
                DiscoveryAdvertisement.toBytes(ADVERTISEMENT, /*includeVendorSpecificData=*/ false);
        assertThat(result).isEqualTo(BYTES_NO_VENDOR);
    }

    @Test
    public void toBytes_succeedWithVendorData() {
        assertThat(ADVERTISEMENT).isNotNull();

        byte[] result =
                DiscoveryAdvertisement.toBytes(ADVERTISEMENT, /*includeVendorSpecificData=*/ true);
        assertThat(result).isEqualTo(BYTES);
    }

    @Test
    public void getManufacturerSpecificDataInBytes_succeed() {
        assertThat(ADVERTISEMENT).isNotNull();

        byte[] result = DiscoveryAdvertisement.getManufacturerSpecificDataInBytes(ADVERTISEMENT);
        byte[] expected = new byte[] {0x25, 0x75, 0x00, 0x10, (byte) 0xFF, 0x00};
        assertThat(result).isEqualTo(expected);
    }

    @Test
    public void getManufacturerSpecificDataInBytes_noData() {
        DiscoveryAdvertisement adv = DiscoveryAdvertisement.fromBytes(MIN_BYTES, null);
        assertThat(adv).isNotNull();

        assertThat(DiscoveryAdvertisement.getManufacturerSpecificDataInBytes(adv)).isNull();
    }
}
