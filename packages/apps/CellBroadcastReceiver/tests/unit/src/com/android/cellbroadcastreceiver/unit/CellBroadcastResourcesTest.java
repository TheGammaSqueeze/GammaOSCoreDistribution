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

package com.android.cellbroadcastreceiver.unit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.telephony.SmsCbCmasInfo;
import android.telephony.SmsCbLocation;
import android.telephony.SmsCbMessage;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;

import com.android.cellbroadcastreceiver.CellBroadcastResources;
import com.android.internal.telephony.gsm.SmsCbConstants;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.lang.reflect.Method;

public class CellBroadcastResourcesTest {

    @Mock
    private Context mContext;

    @Mock
    private Resources mResources;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        doReturn(mResources).when(mContext).getResources();
        String stringResultToReturn = "";
        doReturn(stringResultToReturn).when(mResources).getString(anyInt());
    }

    @Test
    public void testGetMessageDetails() {
        SmsCbMessage smsCbMessage = new SmsCbMessage(1, 2, 0, new SmsCbLocation(),
                SmsCbConstants.MESSAGE_ID_ETWS_EARTHQUAKE_AND_TSUNAMI_WARNING, "language", "body",
                SmsCbMessage.MESSAGE_PRIORITY_EMERGENCY, null, new SmsCbCmasInfo(0, 2, 3, 4, 5, 6),
                0, 1);
        CharSequence details = CellBroadcastResources.getMessageDetails(mContext, true,
                smsCbMessage, -1, false,
                null);
        assertNotNull(details);
    }

    @Test
    public void testGetMessageDetailsCmasMessage() {
        SmsCbMessage smsCbMessage = new SmsCbMessage(1, 2, 0, new SmsCbLocation(),
                SmsCbConstants.MESSAGE_ID_CMAS_ALERT_PRESIDENTIAL_LEVEL, "language", "body",
                SmsCbMessage.MESSAGE_PRIORITY_EMERGENCY, null, new SmsCbCmasInfo(0, 2, 3, 4, 5, 6),
                0, 1);
        CharSequence details = CellBroadcastResources.getMessageDetails(mContext, true,
                smsCbMessage, -1, false,
                null);
        assertNotNull(details);
    }

    @Test
    public void testGetCmasCategoryResId() throws Exception {
        int[] cats = {SmsCbCmasInfo.CMAS_CATEGORY_GEO, SmsCbCmasInfo.CMAS_CATEGORY_MET,
                SmsCbCmasInfo.CMAS_CATEGORY_SAFETY, SmsCbCmasInfo.CMAS_CATEGORY_SECURITY,
                SmsCbCmasInfo.CMAS_CATEGORY_RESCUE, SmsCbCmasInfo.CMAS_CATEGORY_FIRE,
                SmsCbCmasInfo.CMAS_CATEGORY_HEALTH, SmsCbCmasInfo.CMAS_CATEGORY_ENV,
                SmsCbCmasInfo.CMAS_CATEGORY_TRANSPORT, SmsCbCmasInfo.CMAS_CATEGORY_INFRA,
                SmsCbCmasInfo.CMAS_CATEGORY_CBRNE, SmsCbCmasInfo.CMAS_CATEGORY_OTHER};
        for (int c : cats) {
            assertNotEquals(0, getCmasCategoryResId(new SmsCbCmasInfo(0, c, 0, 0, 0, 0)));
        }

        assertEquals(0, getCmasCategoryResId(new SmsCbCmasInfo(
                0, SmsCbCmasInfo.CMAS_CATEGORY_UNKNOWN, 0, 0, 0, 0)));
    }

    @Test
    public void testGetDialogTitleResource() throws Exception {
        SubscriptionManager mockSubManager = mock(SubscriptionManager.class);
        doReturn(mockSubManager).when(mContext).getSystemService(
                eq(Context.TELEPHONY_SUBSCRIPTION_SERVICE));
        SubscriptionInfo mockSubInfo = mock(SubscriptionInfo.class);
        doReturn(mockSubInfo).when(mockSubManager).getActiveSubscriptionInfo(anyInt());
        Context mockContext2 = mock(Context.class);
        doReturn(mResources).when(mockContext2).getResources();
        Configuration config = new Configuration();
        doReturn(config).when(mResources).getConfiguration();
        doReturn(mockContext2).when(mContext).createConfigurationContext(any());

        FakeSharedPreferences mFakeSharedPreferences = new FakeSharedPreferences();
        doReturn(mFakeSharedPreferences).when(mContext).getSharedPreferences(anyString(), anyInt());
        putResources(com.android.cellbroadcastreceiver.R.array
                .cmas_alert_extreme_channels_range_strings, new String[]{
                    "0x1113-0x1114:rat=gsm",
                    "0x1001-0x1001:rat=cdma",
                    "0x1120-0x1121:rat=gsm",
                });
        putResources(com.android.cellbroadcastreceiver.R.array
                .public_safety_messages_channels_range_strings, new String[]{
                    "0x112C:rat=gsm, emergency=true",
                    "0x112D:rat=gsm, emergency=true",
                });
        SmsCbMessage smsCbMessage = new SmsCbMessage(1, 2, 0, new SmsCbLocation(),
                SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_IMMEDIATE_OBSERVED, "language", "body",
                SmsCbMessage.MESSAGE_PRIORITY_EMERGENCY, null, new SmsCbCmasInfo(0, 2, 3, 4, 5, 6),
                0, 1);
        int expectedResult = getDialogTitleResource(mContext, smsCbMessage);

        SmsCbMessage testSmsCbMessage = new SmsCbMessage(1, 2, 0, new SmsCbLocation(),
                SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_IMMEDIATE_OBSERVED, "language", "body",
                SmsCbMessage.MESSAGE_PRIORITY_EMERGENCY, null, new SmsCbCmasInfo(0, 2, 3,
                SmsCbCmasInfo.CMAS_SEVERITY_EXTREME, SmsCbCmasInfo.CMAS_URGENCY_IMMEDIATE,
                SmsCbCmasInfo.CMAS_CERTAINTY_LIKELY), 0, 1);

        int result = getDialogTitleResource(mContext, testSmsCbMessage);
        assertEquals(expectedResult, result);

        SmsCbMessage testPublicSafetyMessage = new SmsCbMessage(1, 2, 0, new SmsCbLocation(),
                SmsCbConstants.MESSAGE_ID_CMAS_ALERT_PUBLIC_SAFETY, "language", "body",
                SmsCbMessage.MESSAGE_PRIORITY_EMERGENCY, null, new SmsCbCmasInfo(0, 2, 3,
                SmsCbCmasInfo.CMAS_SEVERITY_EXTREME, SmsCbCmasInfo.CMAS_URGENCY_IMMEDIATE,
                SmsCbCmasInfo.CMAS_CERTAINTY_LIKELY), 0, 1);
        result = getDialogTitleResource(mContext, testPublicSafetyMessage);
        assertNotEquals(expectedResult, result);
    }

    @Test
    public void testGetCmasResponseResId() throws Exception {
        int[] resps = {SmsCbCmasInfo.CMAS_RESPONSE_TYPE_SHELTER,
                SmsCbCmasInfo.CMAS_RESPONSE_TYPE_EVACUATE,
                SmsCbCmasInfo.CMAS_RESPONSE_TYPE_PREPARE,
                SmsCbCmasInfo.CMAS_RESPONSE_TYPE_EXECUTE,
                SmsCbCmasInfo.CMAS_RESPONSE_TYPE_MONITOR,
                SmsCbCmasInfo.CMAS_RESPONSE_TYPE_AVOID,
                SmsCbCmasInfo.CMAS_RESPONSE_TYPE_ASSESS,
                SmsCbCmasInfo.CMAS_RESPONSE_TYPE_NONE};
        for (int r : resps) {
            assertNotEquals(0, getCmasResponseResId(new SmsCbCmasInfo(0, 0, r, 0, 0, 0)));
        }

        assertEquals(0, getCmasResponseResId(new SmsCbCmasInfo(
                0, 0, SmsCbCmasInfo.CMAS_RESPONSE_TYPE_UNKNOWN, 0, 0, 0)));
    }

    private int getCmasCategoryResId(SmsCbCmasInfo info) throws Exception {
        Method method = CellBroadcastResources.class.getDeclaredMethod(
                "getCmasCategoryResId", SmsCbCmasInfo.class);
        method.setAccessible(true);
        return (int) method.invoke(null, info);
    }

    private int getCmasResponseResId(SmsCbCmasInfo info) throws Exception {
        Method method = CellBroadcastResources.class.getDeclaredMethod(
                "getCmasResponseResId", SmsCbCmasInfo.class);
        method.setAccessible(true);
        return (int) method.invoke(null, info);
    }

    private int getDialogTitleResource(Context context, SmsCbMessage info) throws Exception {
        Method method = CellBroadcastResources.class.getDeclaredMethod(
                "getDialogTitleResource", Context.class, SmsCbMessage.class);
        method.setAccessible(true);
        return (int) method.invoke(null, context, info);
    }

    void putResources(int id, String[] values) {
        doReturn(values).when(mResources).getStringArray(eq(id));
    }
}
