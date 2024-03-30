/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.services.telephony;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.PropertyInvalidatedCache;
import android.content.ComponentName;
import android.content.Context;
import android.telecom.PhoneAccountHandle;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telephony.emergency.EmergencyNumber;
import android.test.mock.MockContext;

import androidx.test.runner.AndroidJUnit4;

import com.android.internal.telephony.ITelephony;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Unit tests for {@link TelephonyManager}. */
@RunWith(AndroidJUnit4.class)
public class TelephonyManagerTest {
    private static final String PKG_NAME = "Unittest.TelephonyManagerTest";
    private static final String TAG = "TelephonyManagerTest";
    private static final PhoneAccountHandle TEST_HANDLE1 = new PhoneAccountHandle(
            new ComponentName("com.test", "Test"), "1");
    private static final int TEST_SUBID_1 = 1;
    private static final PhoneAccountHandle TEST_HANDLE2 = new PhoneAccountHandle(
            new ComponentName("com.test", "Test"), "2");
    private static final int TEST_SUBID_2 = 2;

    private ITelephony mMockITelephony;
    private SubscriptionManager mMockSubscriptionManager;
    private Context mMockContext;

    private TelephonyManager mTelephonyManager;

    private final MockContext mContext =
            new MockContext() {
                @Override
                public String getOpPackageName() {
                    return PKG_NAME;
                }
                @Override
                public String getAttributionTag() {
                    return TAG;
                }
                @Override
                public Context getApplicationContext() {
                    return null;
                }
                @Override
                public Object getSystemService(String name) {
                    switch (name) {
                        case (Context.TELEPHONY_SUBSCRIPTION_SERVICE) : {
                            return mMockSubscriptionManager;
                        }
                    }
                    return null;
                }
            };

    @Before
    public void setUp() throws Exception {
        mMockITelephony = mock(ITelephony.class);
        mMockSubscriptionManager = mock(SubscriptionManager.class);
        mMockContext = mock(Context.class);
        when(mMockContext.getSystemService(eq(Context.TELEPHONY_SUBSCRIPTION_SERVICE)))
                .thenReturn(mMockSubscriptionManager);

        mTelephonyManager = new TelephonyManager(mContext);
        TelephonyManager.setupITelephonyForTest(mMockITelephony);
        TelephonyManager.enableServiceHandleCaching();
    }

    @After
    public void tearDown() throws Exception {
        TelephonyManager.setupITelephonyForTest(null);
        TelephonyManager.disableServiceHandleCaching();
    }

    @Test
    public void testFilterEmergencyNumbersByCategories() throws Exception {
        Map<Integer, List<EmergencyNumber>> emergencyNumberLists = new HashMap<>();
        List<EmergencyNumber> emergencyNumberList = new ArrayList<>();
        EmergencyNumber number_police = new EmergencyNumber(
                "911",
                "us",
                "30",
                EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_POLICE,
                new ArrayList<String>(),
                EmergencyNumber.EMERGENCY_NUMBER_SOURCE_NETWORK_SIGNALING,
                EmergencyNumber.EMERGENCY_CALL_ROUTING_NORMAL);
        EmergencyNumber number_fire = new EmergencyNumber(
                "912",
                "us",
                "30",
                EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_FIRE_BRIGADE,
                new ArrayList<String>(),
                EmergencyNumber.EMERGENCY_NUMBER_SOURCE_NETWORK_SIGNALING,
                EmergencyNumber.EMERGENCY_CALL_ROUTING_NORMAL);
        emergencyNumberList.add(number_police);
        emergencyNumberList.add(number_fire);
        final int test_sub_id = 1;
        emergencyNumberLists.put(test_sub_id, emergencyNumberList);

        Map<Integer, List<EmergencyNumber>> returnedEmergencyNumberLists =
                mTelephonyManager.filterEmergencyNumbersByCategories(emergencyNumberLists,
                        EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_POLICE);

        // Verify the returned number list contains only the police number(s)
        List<EmergencyNumber> returnedEmergencyNumberList = returnedEmergencyNumberLists.get(
                test_sub_id);
        for (EmergencyNumber num : returnedEmergencyNumberList) {
            assertTrue(num.isInEmergencyServiceCategories(
                    EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_POLICE));
        }
    }

    @Test
    public void testGetEmergencyNumberListForCategories() throws Exception {
        Map<Integer, List<EmergencyNumber>> emergencyNumberLists = new HashMap<>();
        List<EmergencyNumber> emergencyNumberList = new ArrayList<>();
        EmergencyNumber number_police = new EmergencyNumber(
                "911",
                "us",
                "30",
                EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_POLICE,
                new ArrayList<String>(),
                EmergencyNumber.EMERGENCY_NUMBER_SOURCE_NETWORK_SIGNALING,
                EmergencyNumber.EMERGENCY_CALL_ROUTING_NORMAL);
        EmergencyNumber number_fire = new EmergencyNumber(
                "912",
                "us",
                "30",
                EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_FIRE_BRIGADE,
                new ArrayList<String>(),
                EmergencyNumber.EMERGENCY_NUMBER_SOURCE_NETWORK_SIGNALING,
                EmergencyNumber.EMERGENCY_CALL_ROUTING_NORMAL);
        emergencyNumberList.add(number_police);
        emergencyNumberList.add(number_fire);
        final int test_sub_id = 1;
        emergencyNumberLists.put(test_sub_id, emergencyNumberList);
        when(mMockITelephony.getEmergencyNumberList(eq(PKG_NAME), eq(TAG))).thenReturn(
                emergencyNumberLists);

        // Call TelephonyManager.getEmergencyNumberList(Category)
        Map<Integer, List<EmergencyNumber>> returnedEmergencyNumberLists =
                mTelephonyManager.getEmergencyNumberList(
                        EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_POLICE);

        // Verify the ITelephony service is called
        verify(mMockITelephony, times(1)).getEmergencyNumberList(eq(PKG_NAME), eq(TAG));

        // Verify the returned number list contains only the police number(s)
        List<EmergencyNumber> returnedEmergencyNumberList = returnedEmergencyNumberLists.get(
                test_sub_id);
        for (EmergencyNumber num : returnedEmergencyNumberList) {
            assertTrue(num.isInEmergencyServiceCategories(
                    EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_POLICE));
        }
    }

    /**
     * Verifies that {@link TelephonyManager#getSubscriptionId(PhoneAccountHandle)} is properly
     * using a property invalidated cache.
     * @throws Exception
     */
    @Test
    public void testGetSubscriptionIdCache() throws Exception {
        PropertyInvalidatedCache.invalidateCache(TelephonyManager.CACHE_KEY_PHONE_ACCOUNT_TO_SUBID);
        when(mMockITelephony.getSubIdForPhoneAccountHandle(eq(TEST_HANDLE1),
                anyString(), anyString())).thenReturn(TEST_SUBID_1);
        when(mMockITelephony.getSubIdForPhoneAccountHandle(eq(TEST_HANDLE2),
                anyString(), anyString())).thenReturn(TEST_SUBID_2);

        // Ensure queries for phone account handles come back consistently.
        assertEquals(TEST_SUBID_1, mTelephonyManager.getSubscriptionId(TEST_HANDLE1));
        assertEquals(TEST_SUBID_1, mTelephonyManager.getSubscriptionId(TEST_HANDLE1));
        assertEquals(TEST_SUBID_2, mTelephonyManager.getSubscriptionId(TEST_HANDLE2));
        assertEquals(TEST_SUBID_2, mTelephonyManager.getSubscriptionId(TEST_HANDLE2));

        // We should have only had a single call to the underlying AIDL, however.  The cache should
        // have protected us from calling this multiple times.
        verify(mMockITelephony, times(1)).getSubIdForPhoneAccountHandle(eq(TEST_HANDLE1),
                anyString(), anyString());
        verify(mMockITelephony, times(1)).getSubIdForPhoneAccountHandle(eq(TEST_HANDLE2),
                anyString(), anyString());
    }
}
