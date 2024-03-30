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

package com.android.cts.managedprofile;

import android.net.Uri;
import android.telephony.TelephonyManager;
import android.telephony.data.ApnSetting;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

/**
 * Test override APN APIs.
 * TODO: b/232548859
 */
public class OverrideApnTest extends BaseManagedProfileTest {
    private static final String TEST_APN_NAME = "testEnterpriseApnName";
    private static final String UPDATE_APN_NAME = "updateEnterpriseApnName";
    private static final String TEST_ENTRY_NAME = "testEnterpriseEntryName";
    private static final String UPDATE_ETNRY_NAME = "updateEnterpriseEntryName";
    private static final String TEST_OPERATOR_NUMERIC = "123456789";
    private static final int TEST_PROXY_PORT = 123;
    private static final String TEST_PROXY_ADDRESS = "123.123.123.123";
    private static final Uri TEST_MMSC = Uri.parse("http://www.google.com");
    private static final String TEST_USER_NAME = "testUser";
    private static final String TEST_PASSWORD = "testPassword";
    private static final int TEST_AUTH_TYPE = ApnSetting.AUTH_TYPE_CHAP;
    private static final int TEST_APN_TYPE_BITMASK = ApnSetting.TYPE_ENTERPRISE;
    private static final int TEST_APN_TYPE_BITMASK_WRONG = ApnSetting.TYPE_DEFAULT;
    private static final int TEST_PROTOCOL = ApnSetting.PROTOCOL_IPV4V6;
    private static final int TEST_NETWORK_TYPE_BITMASK = TelephonyManager.NETWORK_TYPE_CDMA;
    private static final int TEST_MVNO_TYPE = ApnSetting.MVNO_TYPE_GID;
    private static final boolean TEST_ENABLED = true;
    private static final int TEST_CARRIER_ID = 100;
    private static final int UPDATE_CARRIER_ID = 101;

    private static final ApnSetting TEST_APN_FULL;
    static {
        TEST_APN_FULL = new ApnSetting.Builder()
            .setApnName(TEST_APN_NAME)
            .setEntryName(TEST_ENTRY_NAME)
            .setOperatorNumeric(TEST_OPERATOR_NUMERIC)
            .setProxyAddress(TEST_PROXY_ADDRESS)
            .setProxyPort(TEST_PROXY_PORT)
            .setMmsc(TEST_MMSC)
            .setMmsProxyAddress(TEST_PROXY_ADDRESS)
            .setMmsProxyPort(TEST_PROXY_PORT)
            .setUser(TEST_USER_NAME)
            .setPassword(TEST_PASSWORD)
            .setAuthType(TEST_AUTH_TYPE)
            .setApnTypeBitmask(TEST_APN_TYPE_BITMASK)
            .setProtocol(TEST_PROTOCOL)
            .setRoamingProtocol(TEST_PROTOCOL)
            .setNetworkTypeBitmask(TEST_NETWORK_TYPE_BITMASK)
            .setMvnoType(TEST_MVNO_TYPE)
            .setCarrierEnabled(TEST_ENABLED)
            .setCarrierId(TEST_CARRIER_ID)
            .build();
    }

    private static InetAddress getProxyInetAddress(String proxyAddress) {
        try {
            return InetAddress.getByName(proxyAddress);
        } catch (UnknownHostException e) {
            return null;
        }
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        List<ApnSetting> apnList = mDevicePolicyManager.getOverrideApns(ADMIN_RECEIVER_COMPONENT);
        for (ApnSetting apn : apnList) {
            boolean deleted = mDevicePolicyManager.removeOverrideApn(ADMIN_RECEIVER_COMPONENT,
                    apn.getId());
            assertTrue("Failed to clean up override APNs.", deleted);
        }
    }

    public void testAddGetRemoveOverrideApn() throws Exception {
        int insertedId = mDevicePolicyManager.addOverrideApn(ADMIN_RECEIVER_COMPONENT,
                TEST_APN_FULL);
        assertTrue(insertedId != 0);
        List<ApnSetting> apnList = mDevicePolicyManager.getOverrideApns(ADMIN_RECEIVER_COMPONENT);

        assertEquals(1, apnList.size());
        assertEquals(TEST_OPERATOR_NUMERIC, apnList.get(0).getOperatorNumeric());
        assertEquals(TEST_ENTRY_NAME, apnList.get(0).getEntryName());
        assertEquals(TEST_APN_NAME, apnList.get(0).getApnName());
        assertEquals(getProxyInetAddress(TEST_PROXY_ADDRESS), apnList.get(0).getProxyAddress());
        assertEquals(TEST_PROXY_ADDRESS, apnList.get(0).getProxyAddressAsString());
        assertEquals(TEST_PROXY_PORT, apnList.get(0).getProxyPort());
        assertEquals(TEST_MMSC, apnList.get(0).getMmsc());
        assertEquals(getProxyInetAddress(TEST_PROXY_ADDRESS), apnList.get(0).getMmsProxyAddress());
        assertEquals(TEST_PROXY_ADDRESS, apnList.get(0).getMmsProxyAddressAsString());
        assertEquals(TEST_PROXY_PORT, apnList.get(0).getMmsProxyPort());
        assertEquals(TEST_USER_NAME, apnList.get(0).getUser());
        assertEquals(TEST_PASSWORD, apnList.get(0).getPassword());
        assertEquals(TEST_AUTH_TYPE, apnList.get(0).getAuthType());
        assertEquals(TEST_APN_TYPE_BITMASK, apnList.get(0).getApnTypeBitmask());
        assertEquals(TEST_PROTOCOL, apnList.get(0).getProtocol());
        assertEquals(TEST_PROTOCOL, apnList.get(0).getRoamingProtocol());
        assertEquals(TEST_ENABLED, apnList.get(0).isEnabled());
        assertEquals(TEST_MVNO_TYPE, apnList.get(0).getMvnoType());
        assertEquals(TEST_NETWORK_TYPE_BITMASK, apnList.get(0).getNetworkTypeBitmask());
        assertEquals(TEST_CARRIER_ID, apnList.get(0).getCarrierId());

        assertTrue(mDevicePolicyManager.removeOverrideApn(ADMIN_RECEIVER_COMPONENT, insertedId));
        apnList = mDevicePolicyManager.getOverrideApns(ADMIN_RECEIVER_COMPONENT);
        assertEquals(0, apnList.size());
    }

    public void testAddOverrideApnIncorrectApn() throws Exception {
        int insertedId = mDevicePolicyManager.addOverrideApn(ADMIN_RECEIVER_COMPONENT,
                TEST_APN_FULL);
        assertTrue(insertedId != 0);
        List<ApnSetting> apnList = mDevicePolicyManager.getOverrideApns(ADMIN_RECEIVER_COMPONENT);

        assertEquals(1, apnList.size());
        assertEquals(TEST_OPERATOR_NUMERIC, apnList.get(0).getOperatorNumeric());
        assertEquals(TEST_ENTRY_NAME, apnList.get(0).getEntryName());
        assertEquals(TEST_APN_NAME, apnList.get(0).getApnName());
        assertEquals(getProxyInetAddress(TEST_PROXY_ADDRESS), apnList.get(0).getProxyAddress());
        assertEquals(TEST_PROXY_ADDRESS, apnList.get(0).getProxyAddressAsString());
        assertEquals(TEST_PROXY_PORT, apnList.get(0).getProxyPort());
        assertEquals(TEST_MMSC, apnList.get(0).getMmsc());
        assertEquals(getProxyInetAddress(TEST_PROXY_ADDRESS), apnList.get(0).getMmsProxyAddress());
        assertEquals(TEST_PROXY_ADDRESS, apnList.get(0).getMmsProxyAddressAsString());
        assertEquals(TEST_PROXY_PORT, apnList.get(0).getMmsProxyPort());
        assertEquals(TEST_USER_NAME, apnList.get(0).getUser());
        assertEquals(TEST_PASSWORD, apnList.get(0).getPassword());
        assertEquals(TEST_AUTH_TYPE, apnList.get(0).getAuthType());
        assertEquals(TEST_APN_TYPE_BITMASK_WRONG, apnList.get(0).getApnTypeBitmask());
        assertEquals(TEST_PROTOCOL, apnList.get(0).getProtocol());
        assertEquals(TEST_PROTOCOL, apnList.get(0).getRoamingProtocol());
        assertEquals(TEST_ENABLED, apnList.get(0).isEnabled());
        assertEquals(TEST_MVNO_TYPE, apnList.get(0).getMvnoType());
        assertEquals(TEST_NETWORK_TYPE_BITMASK, apnList.get(0).getNetworkTypeBitmask());
        assertEquals(TEST_CARRIER_ID, apnList.get(0).getCarrierId());

        apnList = mDevicePolicyManager.getOverrideApns(ADMIN_RECEIVER_COMPONENT);
        assertEquals(0, apnList.size());
    }

    public void testUpdateOverrideApn() throws Exception {
        int insertedId = mDevicePolicyManager.addOverrideApn(ADMIN_RECEIVER_COMPONENT,
                TEST_APN_FULL);
        assertNotSame(-1, insertedId);

        final ApnSetting updateApn = new ApnSetting.Builder()
                .setApnName(UPDATE_APN_NAME)
                .setEntryName(UPDATE_ETNRY_NAME)
                .setOperatorNumeric(TEST_OPERATOR_NUMERIC)
                .setProxyAddress(TEST_PROXY_ADDRESS)
                .setProxyPort(TEST_PROXY_PORT)
                .setMmsc(TEST_MMSC)
                .setMmsProxyAddress(TEST_PROXY_ADDRESS)
                .setMmsProxyPort(TEST_PROXY_PORT)
                .setUser(TEST_USER_NAME)
                .setPassword(TEST_PASSWORD)
                .setAuthType(TEST_AUTH_TYPE)
                .setApnTypeBitmask(TEST_APN_TYPE_BITMASK)
                .setProtocol(TEST_PROTOCOL)
                .setRoamingProtocol(TEST_PROTOCOL)
                .setNetworkTypeBitmask(TEST_NETWORK_TYPE_BITMASK)
                .setMvnoType(TEST_MVNO_TYPE)
                .setCarrierEnabled(TEST_ENABLED)
                .setCarrierId(UPDATE_CARRIER_ID)
                .build();
        assertTrue(mDevicePolicyManager.updateOverrideApn(ADMIN_RECEIVER_COMPONENT,
                insertedId, updateApn));

        List<ApnSetting> apnList = mDevicePolicyManager.getOverrideApns(ADMIN_RECEIVER_COMPONENT);

        assertEquals(1, apnList.size());
        assertEquals(TEST_OPERATOR_NUMERIC, apnList.get(0).getOperatorNumeric());
        assertEquals(UPDATE_ETNRY_NAME, apnList.get(0).getEntryName());
        assertEquals(UPDATE_APN_NAME, apnList.get(0).getApnName());
        assertEquals(getProxyInetAddress(TEST_PROXY_ADDRESS), apnList.get(0).getProxyAddress());
        assertEquals(TEST_PROXY_ADDRESS, apnList.get(0).getProxyAddressAsString());
        assertEquals(TEST_PROXY_PORT, apnList.get(0).getProxyPort());
        assertEquals(TEST_MMSC, apnList.get(0).getMmsc());
        assertEquals(getProxyInetAddress(TEST_PROXY_ADDRESS), apnList.get(0).getMmsProxyAddress());
        assertEquals(TEST_PROXY_ADDRESS, apnList.get(0).getMmsProxyAddressAsString());
        assertEquals(TEST_PROXY_PORT, apnList.get(0).getMmsProxyPort());
        assertEquals(TEST_USER_NAME, apnList.get(0).getUser());
        assertEquals(TEST_PASSWORD, apnList.get(0).getPassword());
        assertEquals(TEST_AUTH_TYPE, apnList.get(0).getAuthType());
        assertEquals(TEST_APN_TYPE_BITMASK, apnList.get(0).getApnTypeBitmask());
        assertEquals(TEST_PROTOCOL, apnList.get(0).getProtocol());
        assertEquals(TEST_PROTOCOL, apnList.get(0).getRoamingProtocol());
        assertEquals(TEST_ENABLED, apnList.get(0).isEnabled());
        assertEquals(TEST_MVNO_TYPE, apnList.get(0).getMvnoType());
        assertEquals(UPDATE_CARRIER_ID, apnList.get(0).getCarrierId());

        assertTrue(mDevicePolicyManager.removeOverrideApn(ADMIN_RECEIVER_COMPONENT, insertedId));
    }

    public void testUpdateOverrideApnWrongApn() throws Exception {
        int insertedId = mDevicePolicyManager.addOverrideApn(ADMIN_RECEIVER_COMPONENT,
                TEST_APN_FULL);
        assertNotSame(-1, insertedId);

        final ApnSetting updateApn = new ApnSetting.Builder()
                .setApnName(UPDATE_APN_NAME)
                .setEntryName(UPDATE_ETNRY_NAME)
                .setOperatorNumeric(TEST_OPERATOR_NUMERIC)
                .setProxyAddress(TEST_PROXY_ADDRESS)
                .setProxyPort(TEST_PROXY_PORT)
                .setMmsc(TEST_MMSC)
                .setMmsProxyAddress(TEST_PROXY_ADDRESS)
                .setMmsProxyPort(TEST_PROXY_PORT)
                .setUser(TEST_USER_NAME)
                .setPassword(TEST_PASSWORD)
                .setAuthType(TEST_AUTH_TYPE)
                .setApnTypeBitmask(TEST_APN_TYPE_BITMASK_WRONG)
                .setProtocol(TEST_PROTOCOL)
                .setRoamingProtocol(TEST_PROTOCOL)
                .setNetworkTypeBitmask(TEST_NETWORK_TYPE_BITMASK)
                .setMvnoType(TEST_MVNO_TYPE)
                .setCarrierEnabled(TEST_ENABLED)
                .setCarrierId(UPDATE_CARRIER_ID)
                .build();
        assertTrue(mDevicePolicyManager.updateOverrideApn(ADMIN_RECEIVER_COMPONENT,
                insertedId, updateApn));
        List<ApnSetting> apnList = mDevicePolicyManager.getOverrideApns(ADMIN_RECEIVER_COMPONENT);
        assertEquals(0, apnList.size());
    }
}
