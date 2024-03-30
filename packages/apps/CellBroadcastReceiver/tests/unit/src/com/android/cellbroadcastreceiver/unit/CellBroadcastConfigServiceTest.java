/**
 * Copyright (C) 2016 The Android Open Source Project
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
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.SharedPreferences;
import android.telephony.SmsCbMessage;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.cellbroadcastreceiver.CellBroadcastChannelManager.CellBroadcastChannelRange;
import com.android.cellbroadcastreceiver.CellBroadcastConfigService;
import com.android.cellbroadcastreceiver.CellBroadcastSettings;
import com.android.cellbroadcastreceiver.unit.CellBroadcastTest;
import com.android.internal.telephony.ISms;
import com.android.internal.telephony.cdma.sms.SmsEnvelope;
import com.android.internal.telephony.gsm.SmsCbConstants;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Cell broadcast config service tests
 */
public class CellBroadcastConfigServiceTest extends CellBroadcastTest {

    @Mock
    ISms.Stub mMockedSmsService;

    @Mock
    SharedPreferences mMockedSharedPreferences;

    @Mock
    SubscriptionManager mMockSubscriptionManager;

    @Mock
    SubscriptionInfo mMockSubscriptionInfo;

    @Mock
    Intent mIntent;

    private CellBroadcastConfigService mConfigService;

    @Before
    public void setUp() throws Exception {
        super.setUp(getClass().getSimpleName());
        mConfigService = spy(new CellBroadcastConfigService());
        TelephonyManager.disableServiceHandleCaching();

        Class[] cArgs = new Class[1];
        cArgs[0] = Context.class;

        Method method = ContextWrapper.class.getDeclaredMethod("attachBaseContext", cArgs);
        method.setAccessible(true);
        method.invoke(mConfigService, mContext);

        doReturn(mMockedSharedPreferences).when(mContext)
                .getSharedPreferences(anyString(), anyInt());

        mMockedServiceManager.replaceService("isms", mMockedSmsService);
        doReturn(mMockedSmsService).when(mMockedSmsService).queryLocalInterface(anyString());

        putResources(com.android.cellbroadcastreceiver.R.array
                .cmas_presidential_alerts_channels_range_strings, new String[]{
                "0x1112-0x1112:rat=gsm",
                "0x1000-0x1000:rat=cdma",
                "0x111F-0x111F:rat=gsm",
        });
        putResources(com.android.cellbroadcastreceiver.R.array
                .cmas_alert_extreme_channels_range_strings, new String[]{
                "0x1113-0x1114:rat=gsm",
                "0x1001-0x1001:rat=cdma",
                "0x1120-0x1121:rat=gsm",
        });
        putResources(com.android.cellbroadcastreceiver.R.array
                .cmas_alerts_severe_range_strings, new String[]{
                "0x1115-0x111A:rat=gsm",
                "0x1002-0x1002:rat=cdma",
                "0x1122-0x1127:rat=gsm",
        });
        putResources(com.android.cellbroadcastreceiver.R.array
                .required_monthly_test_range_strings, new String[]{
                "0x111C-0x111C:rat=gsm",
                "0x1004-0x1004:rat=cdma",
                "0x1129-0x1129:rat=gsm",
        });
        putResources(com.android.cellbroadcastreceiver.R.array
                .exercise_alert_range_strings, new String[]{
                "0x111D-0x111D:rat=gsm",
                "0x112A-0x112A:rat=gsm",
        });
        putResources(com.android.cellbroadcastreceiver.R.array
                .operator_defined_alert_range_strings, new String[]{
                "0x111E-0x111E:rat=gsm",
                "0x112B-0x112B:rat=gsm",
        });
        putResources(com.android.cellbroadcastreceiver.R.array
                .etws_alerts_range_strings, new String[]{
                "0x1100-0x1102:rat=gsm",
                "0x1104-0x1104:rat=gsm",
        });
        putResources(com.android.cellbroadcastreceiver.R.array
                .etws_test_alerts_range_strings, new String[]{
                "0x1103-0x1103:rat=gsm",
        });
        putResources(com.android.cellbroadcastreceiver.R.array
                .cmas_amber_alerts_channels_range_strings, new String[]{
                "0x111B-0x111B:rat=gsm",
                "0x1003-0x1003:rat=cdma",
                "0x1128-0x1128:rat=gsm",
        });
        putResources(com.android.cellbroadcastreceiver.R.array
                .geo_fencing_trigger_messages_range_strings, new String[]{
                    "0x1130:rat=gsm, emergency=true",
                });
        putResources(com.android.cellbroadcastreceiver.R.array
                .state_local_test_alert_range_strings, new String[]{
                    "0x112E:rat=gsm, emergency=true",
                    "0x112F:rat=gsm, emergency=true",
                });
        putResources(com.android.cellbroadcastreceiver.R.array
                .public_safety_messages_channels_range_strings, new String[]{
                    "0x112C:rat=gsm, emergency=true",
                    "0x112D:rat=gsm, emergency=true",
                });
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
        TelephonyManager.enableServiceHandleCaching();
    }

    private void setCellBroadcastRange(int subId, boolean isEnableOnly,
            boolean enable, List<CellBroadcastChannelRange> ranges) throws Exception {

        Class[] cArgs = new Class[4];
        cArgs[0] = Integer.TYPE;
        cArgs[1] = Boolean.TYPE;
        cArgs[2] = Boolean.TYPE;
        cArgs[3] = List.class;

        Method method =
                CellBroadcastConfigService.class.getDeclaredMethod("setCellBroadcastRange", cArgs);
        method.setAccessible(true);

        method.invoke(mConfigService, subId, isEnableOnly, enable, ranges);
    }

    /**
     * Test enable cell broadcast range
     */
    @Test
    @SmallTest
    public void testEnableCellBroadcastRange() throws Exception {
        ArrayList<CellBroadcastChannelRange> result = new ArrayList<>();
        result.add(new CellBroadcastChannelRange(mContext,
                SubscriptionManager.DEFAULT_SUBSCRIPTION_ID, mResources, "10-20"));
        setCellBroadcastRange(SubscriptionManager.DEFAULT_SUBSCRIPTION_ID, false, true, result);
        ArgumentCaptor<Integer> captorStart = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> captorEnd = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> captorType = ArgumentCaptor.forClass(Integer.class);

        verify(mMockedSmsService, times(1)).enableCellBroadcastRangeForSubscriber(anyInt(),
                captorStart.capture(), captorEnd.capture(), captorType.capture());

        assertEquals(10, captorStart.getValue().intValue());
        assertEquals(20, captorEnd.getValue().intValue());
        assertEquals(1, captorType.getValue().intValue());

        setCellBroadcastRange(SubscriptionManager.DEFAULT_SUBSCRIPTION_ID, true, true, result);

        verify(mMockedSmsService, times(2)).enableCellBroadcastRangeForSubscriber(anyInt(),
                captorStart.capture(), captorEnd.capture(), captorType.capture());
        assertEquals(10, captorStart.getValue().intValue());
        assertEquals(20, captorEnd.getValue().intValue());
        assertEquals(1, captorType.getValue().intValue());
    }

    /**
     * Test disable cell broadcast range
     */
    @Test
    @SmallTest
    public void testDisableCellBroadcastRange() throws Exception {
        ArrayList<CellBroadcastChannelRange> result = new ArrayList<>();
        result.add(new CellBroadcastChannelRange(mContext,
                SubscriptionManager.DEFAULT_SUBSCRIPTION_ID, mResources, "10-20"));
        setCellBroadcastRange(SubscriptionManager.DEFAULT_SUBSCRIPTION_ID, false, false, result);
        ArgumentCaptor<Integer> captorStart = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> captorEnd = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> captorType = ArgumentCaptor.forClass(Integer.class);

        verify(mMockedSmsService, times(1)).disableCellBroadcastRangeForSubscriber(anyInt(),
                captorStart.capture(), captorEnd.capture(), captorType.capture());

        assertEquals(10, captorStart.getValue().intValue());
        assertEquals(20, captorEnd.getValue().intValue());
        assertEquals(1, captorType.getValue().intValue());

        setCellBroadcastRange(SubscriptionManager.DEFAULT_SUBSCRIPTION_ID, true, false, result);

        verify(mMockedSmsService, times(1)).disableCellBroadcastRangeForSubscriber(anyInt(),
                captorStart.capture(), captorEnd.capture(), captorType.capture());
    }

    private void setPreference(String pref, boolean value) {
        doReturn(value).when(mMockedSharedPreferences).getBoolean(eq(pref), eq(true));
    }

    /**
     * Test enabling channels for default countries (US)
     */
    @Test
    @SmallTest
    public void testEnablingChannelsDefault() throws Exception {
        setPreference(CellBroadcastSettings.KEY_ENABLE_ALERTS_MASTER_TOGGLE, true);
        setPreference(CellBroadcastSettings.KEY_ENABLE_CMAS_EXTREME_THREAT_ALERTS, true);
        setPreference(CellBroadcastSettings.KEY_ENABLE_CMAS_SEVERE_THREAT_ALERTS, true);
        setPreference(CellBroadcastSettings.KEY_ENABLE_CMAS_AMBER_ALERTS, true);

        mConfigService.enableCellBroadcastChannels(SubscriptionManager.DEFAULT_SUBSCRIPTION_ID);

        verify(mMockedSmsService, times(1)).enableCellBroadcastRangeForSubscriber(
                eq(0),
                eq(SmsEnvelope.SERVICE_CATEGORY_CMAS_PRESIDENTIAL_LEVEL_ALERT),
                eq(SmsEnvelope.SERVICE_CATEGORY_CMAS_PRESIDENTIAL_LEVEL_ALERT),
                eq(SmsCbMessage.MESSAGE_FORMAT_3GPP2));

        verify(mMockedSmsService, times(1)).enableCellBroadcastRangeForSubscriber(
                eq(0),
                eq(SmsEnvelope.SERVICE_CATEGORY_CMAS_EXTREME_THREAT),
                eq(SmsEnvelope.SERVICE_CATEGORY_CMAS_EXTREME_THREAT),
                eq(SmsCbMessage.MESSAGE_FORMAT_3GPP2));

        verify(mMockedSmsService, times(1)).enableCellBroadcastRangeForSubscriber(
                eq(0),
                eq(SmsEnvelope.SERVICE_CATEGORY_CMAS_SEVERE_THREAT),
                eq(SmsEnvelope.SERVICE_CATEGORY_CMAS_SEVERE_THREAT),
                eq(SmsCbMessage.MESSAGE_FORMAT_3GPP2));

        verify(mMockedSmsService, times(1)).enableCellBroadcastRangeForSubscriber(
                eq(0),
                eq(SmsEnvelope.SERVICE_CATEGORY_CMAS_CHILD_ABDUCTION_EMERGENCY),
                eq(SmsEnvelope.SERVICE_CATEGORY_CMAS_CHILD_ABDUCTION_EMERGENCY),
                eq(SmsCbMessage.MESSAGE_FORMAT_3GPP2));

        verify(mMockedSmsService, times(1)).disableCellBroadcastRangeForSubscriber(
                eq(0),
                eq(SmsEnvelope.SERVICE_CATEGORY_CMAS_TEST_MESSAGE),
                eq(SmsEnvelope.SERVICE_CATEGORY_CMAS_TEST_MESSAGE),
                eq(SmsCbMessage.MESSAGE_FORMAT_3GPP2));


        // GSM
        verify(mMockedSmsService, times(1)).enableCellBroadcastRangeForSubscriber(
                eq(0),
                eq(SmsCbConstants.MESSAGE_ID_ETWS_EARTHQUAKE_WARNING),
                eq(SmsCbConstants.MESSAGE_ID_ETWS_EARTHQUAKE_AND_TSUNAMI_WARNING),
                eq(SmsCbMessage.MESSAGE_FORMAT_3GPP));

        verify(mMockedSmsService, times(1)).enableCellBroadcastRangeForSubscriber(
                eq(0),
                eq(SmsCbConstants.MESSAGE_ID_ETWS_OTHER_EMERGENCY_TYPE),
                eq(SmsCbConstants.MESSAGE_ID_ETWS_OTHER_EMERGENCY_TYPE),
                eq(SmsCbMessage.MESSAGE_FORMAT_3GPP));

        verify(mMockedSmsService, times(1)).disableCellBroadcastRangeForSubscriber(
                eq(0),
                eq(SmsCbConstants.MESSAGE_ID_ETWS_TEST_MESSAGE),
                eq(SmsCbConstants.MESSAGE_ID_ETWS_TEST_MESSAGE),
                eq(SmsCbMessage.MESSAGE_FORMAT_3GPP));

        verify(mMockedSmsService, times(1)).enableCellBroadcastRangeForSubscriber(
                eq(0),
                eq(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_PRESIDENTIAL_LEVEL),
                eq(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_PRESIDENTIAL_LEVEL),
                eq(SmsCbMessage.MESSAGE_FORMAT_3GPP));

        verify(mMockedSmsService, times(1)).enableCellBroadcastRangeForSubscriber(
                eq(0),
                eq(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_IMMEDIATE_OBSERVED),
                eq(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_IMMEDIATE_LIKELY),
                eq(SmsCbMessage.MESSAGE_FORMAT_3GPP));

        verify(mMockedSmsService, times(1)).enableCellBroadcastRangeForSubscriber(
                eq(0),
                eq(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_EXPECTED_OBSERVED),
                eq(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_SEVERE_EXPECTED_LIKELY),
                eq(SmsCbMessage.MESSAGE_FORMAT_3GPP));

        verify(mMockedSmsService, times(1)).enableCellBroadcastRangeForSubscriber(
                eq(0),
                eq(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_CHILD_ABDUCTION_EMERGENCY),
                eq(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_CHILD_ABDUCTION_EMERGENCY),
                eq(SmsCbMessage.MESSAGE_FORMAT_3GPP));

        verify(mMockedSmsService, times(1)).disableCellBroadcastRangeForSubscriber(
                eq(0),
                eq(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_REQUIRED_MONTHLY_TEST),
                eq(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_REQUIRED_MONTHLY_TEST),
                eq(SmsCbMessage.MESSAGE_FORMAT_3GPP));

        verify(mMockedSmsService, times(1)).disableCellBroadcastRangeForSubscriber(
                eq(0),
                eq(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXERCISE),
                eq(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXERCISE),
                eq(SmsCbMessage.MESSAGE_FORMAT_3GPP));

        verify(mMockedSmsService, times(1)).disableCellBroadcastRangeForSubscriber(
                eq(0),
                eq(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_PUBLIC_SAFETY),
                eq(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_PUBLIC_SAFETY),
                eq(SmsCbMessage.MESSAGE_FORMAT_3GPP));

        verify(mMockedSmsService, times(1)).disableCellBroadcastRangeForSubscriber(
                eq(0),
                eq(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_STATE_LOCAL_TEST),
                eq(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_STATE_LOCAL_TEST),
                eq(SmsCbMessage.MESSAGE_FORMAT_3GPP));

        verify(mMockedSmsService, times(1)).enableCellBroadcastRangeForSubscriber(
                eq(0),
                eq(SmsCbConstants.MESSAGE_ID_CMAS_GEO_FENCING_TRIGGER),
                eq(SmsCbConstants.MESSAGE_ID_CMAS_GEO_FENCING_TRIGGER),
                eq(SmsCbMessage.MESSAGE_FORMAT_3GPP));
    }

    /**
     * Test enabling channels for Presidential alert
     */
    @Test
    @SmallTest
    public void testEnablingPresidential() throws Exception {
        setPreference(CellBroadcastSettings.KEY_ENABLE_ALERTS_MASTER_TOGGLE, true);
        mConfigService.enableCellBroadcastChannels(SubscriptionManager.DEFAULT_SUBSCRIPTION_ID);

        verify(mMockedSmsService, times(1)).enableCellBroadcastRangeForSubscriber(
                eq(0),
                eq(SmsEnvelope.SERVICE_CATEGORY_CMAS_PRESIDENTIAL_LEVEL_ALERT),
                eq(SmsEnvelope.SERVICE_CATEGORY_CMAS_PRESIDENTIAL_LEVEL_ALERT),
                eq(SmsCbMessage.MESSAGE_FORMAT_3GPP2));

        verify(mMockedSmsService, times(1)).enableCellBroadcastRangeForSubscriber(
                eq(0),
                eq(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_PRESIDENTIAL_LEVEL),
                eq(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_PRESIDENTIAL_LEVEL),
                eq(SmsCbMessage.MESSAGE_FORMAT_3GPP));

        verify(mMockedSmsService, times(1)).enableCellBroadcastRangeForSubscriber(
                eq(0),
                eq(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_PRESIDENTIAL_LEVEL_LANGUAGE),
                eq(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_PRESIDENTIAL_LEVEL_LANGUAGE),
                eq(SmsCbMessage.MESSAGE_FORMAT_3GPP));

        setPreference(CellBroadcastSettings.KEY_ENABLE_ALERTS_MASTER_TOGGLE, false);
        mConfigService.enableCellBroadcastChannels(SubscriptionManager.DEFAULT_SUBSCRIPTION_ID);

        verify(mMockedSmsService, times(2)).enableCellBroadcastRangeForSubscriber(
                eq(0),
                eq(SmsEnvelope.SERVICE_CATEGORY_CMAS_PRESIDENTIAL_LEVEL_ALERT),
                eq(SmsEnvelope.SERVICE_CATEGORY_CMAS_PRESIDENTIAL_LEVEL_ALERT),
                eq(SmsCbMessage.MESSAGE_FORMAT_3GPP2));

        verify(mMockedSmsService, times(2)).enableCellBroadcastRangeForSubscriber(
                eq(0),
                eq(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_PRESIDENTIAL_LEVEL),
                eq(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_PRESIDENTIAL_LEVEL),
                eq(SmsCbMessage.MESSAGE_FORMAT_3GPP));

        verify(mMockedSmsService, times(2)).enableCellBroadcastRangeForSubscriber(
                eq(0),
                eq(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_PRESIDENTIAL_LEVEL_LANGUAGE),
                eq(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_PRESIDENTIAL_LEVEL_LANGUAGE),
                eq(SmsCbMessage.MESSAGE_FORMAT_3GPP));

        setPreference(CellBroadcastSettings.KEY_ENABLE_ALERTS_MASTER_TOGGLE, true);
        mConfigService.enableCellBroadcastChannels(SubscriptionManager.DEFAULT_SUBSCRIPTION_ID);

        verify(mMockedSmsService, times(3)).enableCellBroadcastRangeForSubscriber(
                eq(0),
                eq(SmsEnvelope.SERVICE_CATEGORY_CMAS_PRESIDENTIAL_LEVEL_ALERT),
                eq(SmsEnvelope.SERVICE_CATEGORY_CMAS_PRESIDENTIAL_LEVEL_ALERT),
                eq(SmsCbMessage.MESSAGE_FORMAT_3GPP2));

        verify(mMockedSmsService, times(3)).enableCellBroadcastRangeForSubscriber(
                eq(0),
                eq(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_PRESIDENTIAL_LEVEL),
                eq(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_PRESIDENTIAL_LEVEL),
                eq(SmsCbMessage.MESSAGE_FORMAT_3GPP));

        verify(mMockedSmsService, times(3)).enableCellBroadcastRangeForSubscriber(
                eq(0),
                eq(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_PRESIDENTIAL_LEVEL_LANGUAGE),
                eq(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_PRESIDENTIAL_LEVEL_LANGUAGE),
                eq(SmsCbMessage.MESSAGE_FORMAT_3GPP));

    }

    /**
     * Test enabling channels for extreme alert
     */
    @Test
    @SmallTest
    public void testEnablingExtreme() throws Exception {
        setPreference(CellBroadcastSettings.KEY_ENABLE_ALERTS_MASTER_TOGGLE, true);
        setPreference(CellBroadcastSettings.KEY_ENABLE_CMAS_EXTREME_THREAT_ALERTS, true);
        mConfigService.enableCellBroadcastChannels(SubscriptionManager.DEFAULT_SUBSCRIPTION_ID);

        verify(mMockedSmsService, times(1)).enableCellBroadcastRangeForSubscriber(
                eq(0),
                eq(SmsEnvelope.SERVICE_CATEGORY_CMAS_EXTREME_THREAT),
                eq(SmsEnvelope.SERVICE_CATEGORY_CMAS_EXTREME_THREAT),
                eq(SmsCbMessage.MESSAGE_FORMAT_3GPP2));

        verify(mMockedSmsService, times(1)).enableCellBroadcastRangeForSubscriber(
                eq(0),
                eq(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_IMMEDIATE_OBSERVED),
                eq(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_IMMEDIATE_LIKELY),
                eq(SmsCbMessage.MESSAGE_FORMAT_3GPP));

        verify(mMockedSmsService, times(1)).enableCellBroadcastRangeForSubscriber(
                eq(0),
                eq(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_IMMEDIATE_OBSERVED_LANGUAGE),
                eq(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_IMMEDIATE_LIKELY_LANGUAGE),
                eq(SmsCbMessage.MESSAGE_FORMAT_3GPP));

        setPreference(CellBroadcastSettings.KEY_ENABLE_CMAS_EXTREME_THREAT_ALERTS, false);
        mConfigService.enableCellBroadcastChannels(SubscriptionManager.DEFAULT_SUBSCRIPTION_ID);

        verify(mMockedSmsService, times(1)).disableCellBroadcastRangeForSubscriber(
                eq(0),
                eq(SmsEnvelope.SERVICE_CATEGORY_CMAS_EXTREME_THREAT),
                eq(SmsEnvelope.SERVICE_CATEGORY_CMAS_EXTREME_THREAT),
                eq(SmsCbMessage.MESSAGE_FORMAT_3GPP2));

        verify(mMockedSmsService, times(1)).disableCellBroadcastRangeForSubscriber(
                eq(0),
                eq(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_IMMEDIATE_OBSERVED),
                eq(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_IMMEDIATE_LIKELY),
                eq(SmsCbMessage.MESSAGE_FORMAT_3GPP));

        verify(mMockedSmsService, times(1)).disableCellBroadcastRangeForSubscriber(
                eq(0),
                eq(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_IMMEDIATE_OBSERVED_LANGUAGE),
                eq(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_IMMEDIATE_LIKELY_LANGUAGE),
                eq(SmsCbMessage.MESSAGE_FORMAT_3GPP));

        setPreference(CellBroadcastSettings.KEY_ENABLE_CMAS_EXTREME_THREAT_ALERTS, true);
        mConfigService.enableCellBroadcastChannels(SubscriptionManager.DEFAULT_SUBSCRIPTION_ID);

        verify(mMockedSmsService, times(2)).enableCellBroadcastRangeForSubscriber(
                eq(0),
                eq(SmsEnvelope.SERVICE_CATEGORY_CMAS_EXTREME_THREAT),
                eq(SmsEnvelope.SERVICE_CATEGORY_CMAS_EXTREME_THREAT),
                eq(SmsCbMessage.MESSAGE_FORMAT_3GPP2));

        verify(mMockedSmsService, times(2)).enableCellBroadcastRangeForSubscriber(
                eq(0),
                eq(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_IMMEDIATE_OBSERVED),
                eq(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_IMMEDIATE_LIKELY),
                eq(SmsCbMessage.MESSAGE_FORMAT_3GPP));

        verify(mMockedSmsService, times(2)).enableCellBroadcastRangeForSubscriber(
                eq(0),
                eq(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_IMMEDIATE_OBSERVED_LANGUAGE),
                eq(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_IMMEDIATE_LIKELY_LANGUAGE),
                eq(SmsCbMessage.MESSAGE_FORMAT_3GPP));

    }

    /**
     * Test enabling channels for severe alert
     */
    @Test
    @SmallTest
    public void testEnablingSevere() throws Exception {
        setPreference(CellBroadcastSettings.KEY_ENABLE_ALERTS_MASTER_TOGGLE, true);
        setPreference(CellBroadcastSettings.KEY_ENABLE_CMAS_SEVERE_THREAT_ALERTS, true);
        mConfigService.enableCellBroadcastChannels(SubscriptionManager.DEFAULT_SUBSCRIPTION_ID);

        verify(mMockedSmsService, times(1)).enableCellBroadcastRangeForSubscriber(
                eq(0),
                eq(SmsEnvelope.SERVICE_CATEGORY_CMAS_SEVERE_THREAT),
                eq(SmsEnvelope.SERVICE_CATEGORY_CMAS_SEVERE_THREAT),
                eq(SmsCbMessage.MESSAGE_FORMAT_3GPP2));

        verify(mMockedSmsService, times(1)).enableCellBroadcastRangeForSubscriber(
                eq(0),
                eq(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_EXPECTED_OBSERVED),
                eq(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_SEVERE_EXPECTED_LIKELY),
                eq(SmsCbMessage.MESSAGE_FORMAT_3GPP));

        verify(mMockedSmsService, times(1)).enableCellBroadcastRangeForSubscriber(
                eq(0),
                eq(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_EXPECTED_OBSERVED_LANGUAGE),
                eq(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_SEVERE_EXPECTED_LIKELY_LANGUAGE),
                eq(SmsCbMessage.MESSAGE_FORMAT_3GPP));

        setPreference(CellBroadcastSettings.KEY_ENABLE_CMAS_SEVERE_THREAT_ALERTS, false);
        mConfigService.enableCellBroadcastChannels(SubscriptionManager.DEFAULT_SUBSCRIPTION_ID);

        verify(mMockedSmsService, times(1)).disableCellBroadcastRangeForSubscriber(
                eq(0),
                eq(SmsEnvelope.SERVICE_CATEGORY_CMAS_SEVERE_THREAT),
                eq(SmsEnvelope.SERVICE_CATEGORY_CMAS_SEVERE_THREAT),
                eq(SmsCbMessage.MESSAGE_FORMAT_3GPP2));

        verify(mMockedSmsService, times(1)).disableCellBroadcastRangeForSubscriber(
                eq(0),
                eq(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_EXPECTED_OBSERVED),
                eq(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_SEVERE_EXPECTED_LIKELY),
                eq(SmsCbMessage.MESSAGE_FORMAT_3GPP));

        verify(mMockedSmsService, times(1)).disableCellBroadcastRangeForSubscriber(
                eq(0),
                eq(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_EXPECTED_OBSERVED_LANGUAGE),
                eq(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_SEVERE_EXPECTED_LIKELY_LANGUAGE),
                eq(SmsCbMessage.MESSAGE_FORMAT_3GPP));

        setPreference(CellBroadcastSettings.KEY_ENABLE_CMAS_SEVERE_THREAT_ALERTS, true);
        mConfigService.enableCellBroadcastChannels(SubscriptionManager.DEFAULT_SUBSCRIPTION_ID);

        verify(mMockedSmsService, times(2)).enableCellBroadcastRangeForSubscriber(
                eq(0),
                eq(SmsEnvelope.SERVICE_CATEGORY_CMAS_SEVERE_THREAT),
                eq(SmsEnvelope.SERVICE_CATEGORY_CMAS_SEVERE_THREAT),
                eq(SmsCbMessage.MESSAGE_FORMAT_3GPP2));

        verify(mMockedSmsService, times(2)).enableCellBroadcastRangeForSubscriber(
                eq(0),
                eq(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_EXPECTED_OBSERVED),
                eq(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_SEVERE_EXPECTED_LIKELY),
                eq(SmsCbMessage.MESSAGE_FORMAT_3GPP));

        verify(mMockedSmsService, times(2)).enableCellBroadcastRangeForSubscriber(
                eq(0),
                eq(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_EXPECTED_OBSERVED_LANGUAGE),
                eq(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_SEVERE_EXPECTED_LIKELY_LANGUAGE),
                eq(SmsCbMessage.MESSAGE_FORMAT_3GPP));
    }

    /**
     * Test enabling channels for amber alert
     */
    @Test
    @SmallTest
    public void testEnablingAmber() throws Exception {
        setPreference(CellBroadcastSettings.KEY_ENABLE_ALERTS_MASTER_TOGGLE, true);
        setPreference(CellBroadcastSettings.KEY_ENABLE_CMAS_AMBER_ALERTS, true);
        mConfigService.enableCellBroadcastChannels(SubscriptionManager.DEFAULT_SUBSCRIPTION_ID);

        verify(mMockedSmsService, times(1)).enableCellBroadcastRangeForSubscriber(
                eq(0),
                eq(SmsEnvelope.SERVICE_CATEGORY_CMAS_CHILD_ABDUCTION_EMERGENCY),
                eq(SmsEnvelope.SERVICE_CATEGORY_CMAS_CHILD_ABDUCTION_EMERGENCY),
                eq(SmsCbMessage.MESSAGE_FORMAT_3GPP2));

        verify(mMockedSmsService, times(1)).enableCellBroadcastRangeForSubscriber(
                eq(0),
                eq(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_CHILD_ABDUCTION_EMERGENCY),
                eq(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_CHILD_ABDUCTION_EMERGENCY),
                eq(SmsCbMessage.MESSAGE_FORMAT_3GPP));

        verify(mMockedSmsService, times(1)).enableCellBroadcastRangeForSubscriber(
                eq(0),
                eq(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_CHILD_ABDUCTION_EMERGENCY_LANGUAGE),
                eq(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_CHILD_ABDUCTION_EMERGENCY_LANGUAGE),
                eq(SmsCbMessage.MESSAGE_FORMAT_3GPP));

        setPreference(CellBroadcastSettings.KEY_ENABLE_CMAS_AMBER_ALERTS, false);
        mConfigService.enableCellBroadcastChannels(SubscriptionManager.DEFAULT_SUBSCRIPTION_ID);

        verify(mMockedSmsService, times(1)).disableCellBroadcastRangeForSubscriber(
                eq(0),
                eq(SmsEnvelope.SERVICE_CATEGORY_CMAS_CHILD_ABDUCTION_EMERGENCY),
                eq(SmsEnvelope.SERVICE_CATEGORY_CMAS_CHILD_ABDUCTION_EMERGENCY),
                eq(SmsCbMessage.MESSAGE_FORMAT_3GPP2));

        verify(mMockedSmsService, times(1)).disableCellBroadcastRangeForSubscriber(
                eq(0),
                eq(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_CHILD_ABDUCTION_EMERGENCY),
                eq(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_CHILD_ABDUCTION_EMERGENCY),
                eq(SmsCbMessage.MESSAGE_FORMAT_3GPP));

        verify(mMockedSmsService, times(1)).disableCellBroadcastRangeForSubscriber(
                eq(0),
                eq(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_CHILD_ABDUCTION_EMERGENCY_LANGUAGE),
                eq(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_CHILD_ABDUCTION_EMERGENCY_LANGUAGE),
                eq(SmsCbMessage.MESSAGE_FORMAT_3GPP));

        setPreference(CellBroadcastSettings.KEY_ENABLE_CMAS_AMBER_ALERTS, true);
        mConfigService.enableCellBroadcastChannels(SubscriptionManager.DEFAULT_SUBSCRIPTION_ID);

        verify(mMockedSmsService, times(2)).enableCellBroadcastRangeForSubscriber(
                eq(0),
                eq(SmsEnvelope.SERVICE_CATEGORY_CMAS_CHILD_ABDUCTION_EMERGENCY),
                eq(SmsEnvelope.SERVICE_CATEGORY_CMAS_CHILD_ABDUCTION_EMERGENCY),
                eq(SmsCbMessage.MESSAGE_FORMAT_3GPP2));

        verify(mMockedSmsService, times(2)).enableCellBroadcastRangeForSubscriber(
                eq(0),
                eq(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_CHILD_ABDUCTION_EMERGENCY),
                eq(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_CHILD_ABDUCTION_EMERGENCY),
                eq(SmsCbMessage.MESSAGE_FORMAT_3GPP));

        verify(mMockedSmsService, times(2)).enableCellBroadcastRangeForSubscriber(
                eq(0),
                eq(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_CHILD_ABDUCTION_EMERGENCY_LANGUAGE),
                eq(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_CHILD_ABDUCTION_EMERGENCY_LANGUAGE),
                eq(SmsCbMessage.MESSAGE_FORMAT_3GPP));
    }

    /**
     * Test enabling channels for ETWS alert
     */
    @Test
    @SmallTest
    public void testEnablingETWS() throws Exception {
        setPreference(CellBroadcastSettings.KEY_ENABLE_ALERTS_MASTER_TOGGLE, true);
        mConfigService.enableCellBroadcastChannels(SubscriptionManager.DEFAULT_SUBSCRIPTION_ID);

        verify(mMockedSmsService, times(1)).enableCellBroadcastRangeForSubscriber(
                eq(0),
                eq(SmsCbConstants.MESSAGE_ID_ETWS_EARTHQUAKE_WARNING),
                eq(SmsCbConstants.MESSAGE_ID_ETWS_EARTHQUAKE_AND_TSUNAMI_WARNING),
                eq(SmsCbMessage.MESSAGE_FORMAT_3GPP));

        verify(mMockedSmsService, times(1)).enableCellBroadcastRangeForSubscriber(
                eq(0),
                eq(SmsCbConstants.MESSAGE_ID_ETWS_OTHER_EMERGENCY_TYPE),
                eq(SmsCbConstants.MESSAGE_ID_ETWS_OTHER_EMERGENCY_TYPE),
                eq(SmsCbMessage.MESSAGE_FORMAT_3GPP));

        setPreference(CellBroadcastSettings.KEY_ENABLE_ALERTS_MASTER_TOGGLE, false);
        mConfigService.enableCellBroadcastChannels(SubscriptionManager.DEFAULT_SUBSCRIPTION_ID);

        verify(mMockedSmsService, times(1)).disableCellBroadcastRangeForSubscriber(
                eq(0),
                eq(SmsCbConstants.MESSAGE_ID_ETWS_EARTHQUAKE_WARNING),
                eq(SmsCbConstants.MESSAGE_ID_ETWS_EARTHQUAKE_AND_TSUNAMI_WARNING),
                eq(SmsCbMessage.MESSAGE_FORMAT_3GPP));

        verify(mMockedSmsService, times(1)).disableCellBroadcastRangeForSubscriber(
                eq(0),
                eq(SmsCbConstants.MESSAGE_ID_ETWS_OTHER_EMERGENCY_TYPE),
                eq(SmsCbConstants.MESSAGE_ID_ETWS_OTHER_EMERGENCY_TYPE),
                eq(SmsCbMessage.MESSAGE_FORMAT_3GPP));

        setPreference(CellBroadcastSettings.KEY_ENABLE_ALERTS_MASTER_TOGGLE, true);
        mConfigService.enableCellBroadcastChannels(SubscriptionManager.DEFAULT_SUBSCRIPTION_ID);

        verify(mMockedSmsService, times(2)).enableCellBroadcastRangeForSubscriber(
                eq(0),
                eq(SmsCbConstants.MESSAGE_ID_ETWS_EARTHQUAKE_WARNING),
                eq(SmsCbConstants.MESSAGE_ID_ETWS_EARTHQUAKE_AND_TSUNAMI_WARNING),
                eq(SmsCbMessage.MESSAGE_FORMAT_3GPP));

        verify(mMockedSmsService, times(2)).enableCellBroadcastRangeForSubscriber(
                eq(0),
                eq(SmsCbConstants.MESSAGE_ID_ETWS_OTHER_EMERGENCY_TYPE),
                eq(SmsCbConstants.MESSAGE_ID_ETWS_OTHER_EMERGENCY_TYPE),
                eq(SmsCbMessage.MESSAGE_FORMAT_3GPP));
    }

    /**
     * Test enabling channels for geo-fencing message
     */
    @Test
    @SmallTest
    public void testEnablingGeoFencingTriggeredChannel() throws Exception {
        mConfigService.enableCellBroadcastChannels(SubscriptionManager.DEFAULT_SUBSCRIPTION_ID);

        verify(mMockedSmsService, times(1)).enableCellBroadcastRangeForSubscriber(
                eq(0),
                eq(SmsCbConstants.MESSAGE_ID_CMAS_GEO_FENCING_TRIGGER),
                eq(SmsCbConstants.MESSAGE_ID_CMAS_GEO_FENCING_TRIGGER),
                eq(SmsCbMessage.MESSAGE_FORMAT_3GPP));

        putResources(com.android.cellbroadcastreceiver.R.array
                .geo_fencing_trigger_messages_range_strings, new String[]{
                });

        mConfigService.enableCellBroadcastChannels(SubscriptionManager.DEFAULT_SUBSCRIPTION_ID);
        verify(mMockedSmsService, times(0)).disableCellBroadcastRangeForSubscriber(
                eq(0),
                eq(SmsCbConstants.MESSAGE_ID_CMAS_GEO_FENCING_TRIGGER),
                eq(SmsCbConstants.MESSAGE_ID_CMAS_GEO_FENCING_TRIGGER),
                eq(SmsCbMessage.MESSAGE_FORMAT_3GPP));
    }

    /**
     * Test enabling channels for non-cmas series message
     */
    @Test
    @SmallTest
    public void testEnablingNonCmasMessages() throws Exception {
        putResources(com.android.cellbroadcastreceiver.R.array
                .emergency_alerts_channels_range_strings, new String[]{
                    "0xA000:rat=gsm",
                });
        setPreference(CellBroadcastSettings.KEY_ENABLE_ALERTS_MASTER_TOGGLE, true);
        setPreference(CellBroadcastSettings.KEY_ENABLE_EMERGENCY_ALERTS, true);
        mConfigService.enableCellBroadcastChannels(SubscriptionManager.DEFAULT_SUBSCRIPTION_ID);

        verify(mMockedSmsService, times(1)).enableCellBroadcastRangeForSubscriber(
                eq(0),
                eq(0xA000),
                eq(0xA000),
                eq(SmsCbMessage.MESSAGE_FORMAT_3GPP));

        setPreference(CellBroadcastSettings.KEY_ENABLE_ALERTS_MASTER_TOGGLE, true);
        setPreference(CellBroadcastSettings.KEY_ENABLE_EMERGENCY_ALERTS, false);
        mConfigService.enableCellBroadcastChannels(SubscriptionManager.DEFAULT_SUBSCRIPTION_ID);

        verify(mMockedSmsService, times(1)).disableCellBroadcastRangeForSubscriber(
                eq(0),
                eq(0xA000),
                eq(0xA000),
                eq(SmsCbMessage.MESSAGE_FORMAT_3GPP));
    }

    /**
     * Test enabling channels for additional channels
     */
    @Test
    @SmallTest
    public void testEnablingAdditionalChannels() throws Exception {
        putResources(com.android.cellbroadcastreceiver.R.array
                .additional_cbs_channels_strings, new String[]{
                    "0x032:type=area, emergency=false",
                });
        doReturn(true).when(mMockedSharedPreferences).getBoolean(
                eq(CellBroadcastSettings.KEY_ENABLE_AREA_UPDATE_INFO_ALERTS), eq(false));
        doReturn(mResources).when(mConfigService).getResources(anyInt(), anyString());
        putResources(com.android.cellbroadcastreceiver.R.bool.config_showAreaUpdateInfoSettings,
                true);

        mConfigService.enableCellBroadcastChannels(SubscriptionManager.DEFAULT_SUBSCRIPTION_ID);

        verify(mMockedSmsService, times(1)).enableCellBroadcastRangeForSubscriber(
                eq(0),
                eq(0x032),
                eq(0x032),
                eq(SmsCbMessage.MESSAGE_FORMAT_3GPP));

        doReturn(false).when(mMockedSharedPreferences).getBoolean(
                eq(CellBroadcastSettings.KEY_ENABLE_AREA_UPDATE_INFO_ALERTS), eq(false));
        mConfigService.enableCellBroadcastChannels(SubscriptionManager.DEFAULT_SUBSCRIPTION_ID);

        verify(mMockedSmsService, times(1)).disableCellBroadcastRangeForSubscriber(
                eq(0),
                eq(0x032),
                eq(0x032),
                eq(SmsCbMessage.MESSAGE_FORMAT_3GPP));
    }

    /**
     * Test enabling channels for local test channels
     */
    @Test
    @SmallTest
    public void testEnablingLocalTestChannels() throws Exception {
        setPreference(CellBroadcastSettings.KEY_ENABLE_ALERTS_MASTER_TOGGLE, true);

        // check disable when setting is shown and preference is false
        setPreference(CellBroadcastSettings.KEY_ENABLE_STATE_LOCAL_TEST_ALERTS, false);
        putResources(com.android.cellbroadcastreceiver.R.bool
                .show_state_local_test_settings, true);
        putResources(com.android.cellbroadcastreceiver.R.bool
                .state_local_test_alerts_enabled_default, true);

        mConfigService.enableCellBroadcastChannels(SubscriptionManager.DEFAULT_SUBSCRIPTION_ID);

        verify(mMockedSmsService, times(1)).disableCellBroadcastRangeForSubscriber(
                eq(0),
                eq(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_STATE_LOCAL_TEST),
                eq(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_STATE_LOCAL_TEST),
                eq(SmsCbMessage.MESSAGE_FORMAT_3GPP));

        // check disable when setting is not shown and default preference is false
        putResources(com.android.cellbroadcastreceiver.R.bool
                .show_state_local_test_settings, false);
        putResources(com.android.cellbroadcastreceiver.R.bool
                .state_local_test_alerts_enabled_default, false);

        mConfigService.enableCellBroadcastChannels(SubscriptionManager.DEFAULT_SUBSCRIPTION_ID);

        verify(mMockedSmsService, times(2)).disableCellBroadcastRangeForSubscriber(
                eq(0),
                eq(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_STATE_LOCAL_TEST),
                eq(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_STATE_LOCAL_TEST),
                eq(SmsCbMessage.MESSAGE_FORMAT_3GPP));

        // check enable when setting is not shown and default preference is true
        putResources(com.android.cellbroadcastreceiver.R.bool
                .show_state_local_test_settings, false);
        putResources(com.android.cellbroadcastreceiver.R.bool
                .state_local_test_alerts_enabled_default, true);

        mConfigService.enableCellBroadcastChannels(SubscriptionManager.DEFAULT_SUBSCRIPTION_ID);

        verify(mMockedSmsService, times(1)).enableCellBroadcastRangeForSubscriber(
                eq(0),
                eq(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_STATE_LOCAL_TEST),
                eq(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_STATE_LOCAL_TEST),
                eq(SmsCbMessage.MESSAGE_FORMAT_3GPP));

        // check enable when setting is shown and preference is true
        doReturn(true).when(mMockedSharedPreferences).getBoolean(
                eq(CellBroadcastSettings.KEY_ENABLE_STATE_LOCAL_TEST_ALERTS), eq(false));
        putResources(com.android.cellbroadcastreceiver.R.bool
                .show_state_local_test_settings, true);
        putResources(com.android.cellbroadcastreceiver.R.bool
                .state_local_test_alerts_enabled_default, false);

        mConfigService.enableCellBroadcastChannels(SubscriptionManager.DEFAULT_SUBSCRIPTION_ID);

        verify(mMockedSmsService, times(2)).enableCellBroadcastRangeForSubscriber(
                eq(0),
                eq(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_STATE_LOCAL_TEST),
                eq(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_STATE_LOCAL_TEST),
                eq(SmsCbMessage.MESSAGE_FORMAT_3GPP));

    }

    /**
     * Test handling the intent to enable channels
     */
    @Test
    @SmallTest
    public void testOnHandleIntentActionEnableChannels() throws Exception {
        List<SubscriptionInfo> sl = new ArrayList<>();
        sl.add(mMockSubscriptionInfo);
        doReturn(SubscriptionManager.INVALID_SUBSCRIPTION_ID).when(
                mMockSubscriptionInfo).getSubscriptionId();
        doReturn(mContext).when(mConfigService).getApplicationContext();
        doReturn(mMockSubscriptionManager).when(mContext).getSystemService(anyString());
        doReturn(sl).when(mMockSubscriptionManager).getActiveSubscriptionInfoList();
        doReturn(CellBroadcastConfigService.ACTION_ENABLE_CHANNELS).when(mIntent).getAction();
        doNothing().when(mConfigService).enableCellBroadcastChannels(anyInt());
        doNothing().when(mConfigService).enableCellBroadcastRoamingChannelsAsNeeded(anyInt());

        Method method = CellBroadcastConfigService.class.getDeclaredMethod(
                "onHandleIntent", new Class[]{Intent.class});
        method.setAccessible(true);
        method.invoke(mConfigService, mIntent);

        verify(mConfigService, times(1)).enableCellBroadcastChannels(
                eq(SubscriptionManager.INVALID_SUBSCRIPTION_ID));
        verify(mConfigService, times(1)).enableCellBroadcastRoamingChannelsAsNeeded(
                eq(SubscriptionManager.INVALID_SUBSCRIPTION_ID));
    }

    /**
     * Test enabling cell broadcast roaming channels as needed
     */
    @Test
    @SmallTest
    public void testEnableCellBroadcastRoamingChannelsAsNeeded() throws Exception {
        doReturn("").when(mMockedSharedPreferences).getString(anyString(), anyString());

        mConfigService.enableCellBroadcastRoamingChannelsAsNeeded(
                SubscriptionManager.DEFAULT_SUBSCRIPTION_ID);

        //do nothing if operator is empty
        verify(mConfigService, never()).getResources(anyInt(), anyString());

        Context mockContext = mock(Context.class);
        doReturn(mResources).when(mockContext).getResources();
        doReturn(mockContext).when(mContext).createConfigurationContext(any());
        doReturn("123").when(mMockedSharedPreferences).getString(anyString(), anyString());
        doReturn(mResources).when(mConfigService).getResources(anyInt(), anyString());
        putResources(com.android.cellbroadcastreceiver.R.bool.master_toggle_enabled_default, true);
        putResources(com.android.cellbroadcastreceiver.R.bool
                .extreme_threat_alerts_enabled_default, true);
        putResources(com.android.cellbroadcastreceiver.R.bool
                .severe_threat_alerts_enabled_default, true);
        putResources(com.android.cellbroadcastreceiver.R.bool.amber_alerts_enabled_default, true);
        putResources(com.android.cellbroadcastreceiver.R.bool.show_test_settings, true);
        putResources(com.android.cellbroadcastreceiver.R.bool.test_alerts_enabled_default, true);
        putResources(com.android.cellbroadcastreceiver.R.bool
                .test_exercise_alerts_enabled_default, true);
        putResources(com.android.cellbroadcastreceiver.R.bool
                .test_operator_defined_alerts_enabled_default, true);
        putResources(com.android.cellbroadcastreceiver.R.bool
                .area_update_info_alerts_enabled_default, true);
        putResources(com.android.cellbroadcastreceiver.R.bool
                .public_safety_messages_enabled_default, true);
        putResources(com.android.cellbroadcastreceiver.R.bool
                .state_local_test_alerts_enabled_default, true);
        putResources(com.android.cellbroadcastreceiver.R.bool
                .emergency_alerts_enabled_default, true);

        mConfigService.enableCellBroadcastRoamingChannelsAsNeeded(
                SubscriptionManager.DEFAULT_SUBSCRIPTION_ID);

        // should not disable channel
        verify(mMockedSmsService, never()).disableCellBroadcastRangeForSubscriber(
                anyInt(), anyInt(), anyInt(), anyInt());

        verify(mMockedSmsService, times(1)).enableCellBroadcastRangeForSubscriber(
                eq(0),
                eq(SmsEnvelope.SERVICE_CATEGORY_CMAS_PRESIDENTIAL_LEVEL_ALERT),
                eq(SmsEnvelope.SERVICE_CATEGORY_CMAS_PRESIDENTIAL_LEVEL_ALERT),
                eq(SmsCbMessage.MESSAGE_FORMAT_3GPP2));

        verify(mMockedSmsService, times(1)).enableCellBroadcastRangeForSubscriber(
                eq(0),
                eq(SmsEnvelope.SERVICE_CATEGORY_CMAS_EXTREME_THREAT),
                eq(SmsEnvelope.SERVICE_CATEGORY_CMAS_EXTREME_THREAT),
                eq(SmsCbMessage.MESSAGE_FORMAT_3GPP2));

        verify(mMockedSmsService, times(1)).enableCellBroadcastRangeForSubscriber(
                eq(0),
                eq(SmsEnvelope.SERVICE_CATEGORY_CMAS_SEVERE_THREAT),
                eq(SmsEnvelope.SERVICE_CATEGORY_CMAS_SEVERE_THREAT),
                eq(SmsCbMessage.MESSAGE_FORMAT_3GPP2));

        verify(mMockedSmsService, times(1)).enableCellBroadcastRangeForSubscriber(
                eq(0),
                eq(SmsEnvelope.SERVICE_CATEGORY_CMAS_CHILD_ABDUCTION_EMERGENCY),
                eq(SmsEnvelope.SERVICE_CATEGORY_CMAS_CHILD_ABDUCTION_EMERGENCY),
                eq(SmsCbMessage.MESSAGE_FORMAT_3GPP2));

        verify(mMockedSmsService, times(1)).enableCellBroadcastRangeForSubscriber(
                eq(0),
                eq(SmsEnvelope.SERVICE_CATEGORY_CMAS_TEST_MESSAGE),
                eq(SmsEnvelope.SERVICE_CATEGORY_CMAS_TEST_MESSAGE),
                eq(SmsCbMessage.MESSAGE_FORMAT_3GPP2));

        // GSM
        verify(mMockedSmsService, times(1)).enableCellBroadcastRangeForSubscriber(
                eq(0),
                eq(SmsCbConstants.MESSAGE_ID_ETWS_EARTHQUAKE_WARNING),
                eq(SmsCbConstants.MESSAGE_ID_ETWS_EARTHQUAKE_AND_TSUNAMI_WARNING),
                eq(SmsCbMessage.MESSAGE_FORMAT_3GPP));

        verify(mMockedSmsService, times(1)).enableCellBroadcastRangeForSubscriber(
                eq(0),
                eq(SmsCbConstants.MESSAGE_ID_ETWS_OTHER_EMERGENCY_TYPE),
                eq(SmsCbConstants.MESSAGE_ID_ETWS_OTHER_EMERGENCY_TYPE),
                eq(SmsCbMessage.MESSAGE_FORMAT_3GPP));

        verify(mMockedSmsService, times(1)).enableCellBroadcastRangeForSubscriber(
                eq(0),
                eq(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_PRESIDENTIAL_LEVEL),
                eq(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_PRESIDENTIAL_LEVEL),
                eq(SmsCbMessage.MESSAGE_FORMAT_3GPP));

        verify(mMockedSmsService, times(1)).enableCellBroadcastRangeForSubscriber(
                eq(0),
                eq(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_IMMEDIATE_OBSERVED),
                eq(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_IMMEDIATE_LIKELY),
                eq(SmsCbMessage.MESSAGE_FORMAT_3GPP));

        verify(mMockedSmsService, times(1)).enableCellBroadcastRangeForSubscriber(
                eq(0),
                eq(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXTREME_EXPECTED_OBSERVED),
                eq(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_SEVERE_EXPECTED_LIKELY),
                eq(SmsCbMessage.MESSAGE_FORMAT_3GPP));

        verify(mMockedSmsService, times(1)).enableCellBroadcastRangeForSubscriber(
                eq(0),
                eq(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_CHILD_ABDUCTION_EMERGENCY),
                eq(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_CHILD_ABDUCTION_EMERGENCY),
                eq(SmsCbMessage.MESSAGE_FORMAT_3GPP));

        verify(mMockedSmsService, times(1)).enableCellBroadcastRangeForSubscriber(
                eq(0),
                eq(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_REQUIRED_MONTHLY_TEST),
                eq(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_REQUIRED_MONTHLY_TEST),
                eq(SmsCbMessage.MESSAGE_FORMAT_3GPP));

        verify(mMockedSmsService, times(1)).enableCellBroadcastRangeForSubscriber(
                eq(0),
                eq(SmsCbConstants.MESSAGE_ID_ETWS_TEST_MESSAGE),
                eq(SmsCbConstants.MESSAGE_ID_ETWS_TEST_MESSAGE),
                eq(SmsCbMessage.MESSAGE_FORMAT_3GPP));

        verify(mMockedSmsService, times(1)).enableCellBroadcastRangeForSubscriber(
                eq(0),
                eq(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_OPERATOR_DEFINED_USE),
                eq(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_OPERATOR_DEFINED_USE),
                eq(SmsCbMessage.MESSAGE_FORMAT_3GPP));

        verify(mMockedSmsService, times(1)).enableCellBroadcastRangeForSubscriber(
                eq(0),
                eq(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXERCISE),
                eq(SmsCbConstants.MESSAGE_ID_CMAS_ALERT_EXERCISE),
                eq(SmsCbMessage.MESSAGE_FORMAT_3GPP));
    }
}
