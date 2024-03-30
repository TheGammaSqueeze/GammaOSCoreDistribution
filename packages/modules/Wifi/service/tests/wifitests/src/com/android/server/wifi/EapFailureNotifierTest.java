/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.server.wifi;

import static com.android.server.wifi.EapFailureNotifier.ERROR_MESSAGE_OVERLAY_PREFIX;
import static com.android.server.wifi.EapFailureNotifier.ERROR_MESSAGE_OVERLAY_UNKNOWN_ERROR_CODE;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.validateMockitoUsage;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.res.Resources;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiContext;
import android.net.wifi.WifiStringResourceWrapper;
import android.provider.Settings;
import android.service.notification.StatusBarNotification;

import androidx.test.filters.SmallTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Unit tests for {@link com.android.server.wifi.EapFailureNotifier}.
 */
@SmallTest
public class EapFailureNotifierTest extends WifiBaseTest {
    private static final String TEST_SETTINGS_PACKAGE = "test.com.android.settings";
    private static final String NOTIFICATION_TAG = "com.android.wifi";

    @Mock WifiContext mContext;
    @Mock Resources mResources;
    @Mock WifiStringResourceWrapper mResourceWrapper;
    @Mock WifiNotificationManager mWifiNotificationManager;
    @Mock FrameworkFacade mFrameworkFacade;
    @Mock Notification mNotification;
    @Mock
    WifiCarrierInfoManager mWifiCarrierInfoManager;
    @Mock WifiConfiguration mWifiConfiguration;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS) private Notification.Builder mNotificationBuilder;
    private static final int KNOWN_ERROR_CODE = 32764;
    private static final int UNKNOWN_ERROR_CODE = 12345;
    private static final String SSID_1 = "Carrier_AP_1";
    private static final String SSID_2 = "Carrier_AP_2";
    private static final String ERROR_MESSAGE = "Error Message";
    private static final String ERROR_MESSAGE_UNKNOWN_ERROR_CODE =
            "Error Message Unknown Error Code";

    EapFailureNotifier mEapFailureNotifier;

    /**
     * Sets up for unit test
     */
    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        when(mWifiCarrierInfoManager.getBestMatchSubscriptionId(mWifiConfiguration)).thenReturn(0);
        when(mContext.getResources()).thenReturn(mResources);
        when(mContext.getStringResourceWrapper(anyInt(), anyInt())).thenReturn(mResourceWrapper);
        when(mResourceWrapper.getString(eq(ERROR_MESSAGE_OVERLAY_PREFIX + UNKNOWN_ERROR_CODE),
                anyString())).thenReturn(null);
        when(mResourceWrapper.getString(eq(ERROR_MESSAGE_OVERLAY_PREFIX + KNOWN_ERROR_CODE),
                anyString())).thenReturn(ERROR_MESSAGE);
        when(mResourceWrapper.getString(eq(ERROR_MESSAGE_OVERLAY_UNKNOWN_ERROR_CODE),
                anyString())).thenReturn(ERROR_MESSAGE_UNKNOWN_ERROR_CODE);
        when(mContext.createPackageContext(anyString(), eq(0))).thenReturn(mContext);
        when(mContext.getWifiOverlayApkPkgName()).thenReturn("test.com.android.wifi.resources");
        when(mFrameworkFacade.getSettingsPackageName(any())).thenReturn(TEST_SETTINGS_PACKAGE);
        mEapFailureNotifier =
                new EapFailureNotifier(mContext, mFrameworkFacade, mWifiCarrierInfoManager,
                        mWifiNotificationManager);
    }

    @After
    public void cleanUp() throws Exception {
        validateMockitoUsage();
    }

    /**
     * Verify that a eap failure notification will be generated with the following conditions :
     * No eap failure notification of eap failure is displayed now.
     * Error code is defined by carrier
     * @throws Exception
     */
    @Test
    public void onEapFailureWithDefinedErrorCodeWithoutNotificationShown() throws Exception {
        when(mFrameworkFacade.makeNotificationBuilder(any(),
                eq(WifiService.NOTIFICATION_NETWORK_ALERTS))).thenReturn(mNotificationBuilder);
        StatusBarNotification[] activeNotifications = new StatusBarNotification[1];
        activeNotifications[0] = new StatusBarNotification("android", "", 56, "", 0, 0, 0,
                mNotification, android.os.Process.myUserHandle(), 0);
        when(mWifiNotificationManager.getActiveNotifications()).thenReturn(activeNotifications);
        mWifiConfiguration.SSID = SSID_2;
        mEapFailureNotifier.onEapFailure(KNOWN_ERROR_CODE, mWifiConfiguration, true);
        verify(mWifiNotificationManager).notify(eq(EapFailureNotifier.NOTIFICATION_ID), any());
        ArgumentCaptor<Intent> intent = ArgumentCaptor.forClass(Intent.class);
        verify(mFrameworkFacade).getActivity(
                eq(mContext), eq(0), intent.capture(),
                eq(PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        assertEquals(TEST_SETTINGS_PACKAGE, intent.getValue().getPackage());
        assertEquals(Settings.ACTION_WIFI_SETTINGS, intent.getValue().getAction());
    }

    /**
     * Verify that a eap failure notification will not be generated with the following conditions :
     * No eap failure notification of eap failure is displayed now.
     * Error code is defined by carrier
     * showNotification = false
     */
    @Test
    public void onEapFailure_showNotificationFalse_notShown() throws Exception {
        when(mFrameworkFacade.makeNotificationBuilder(any(),
                eq(WifiService.NOTIFICATION_NETWORK_ALERTS))).thenReturn(mNotificationBuilder);
        StatusBarNotification[] activeNotifications = new StatusBarNotification[1];
        activeNotifications[0] = new StatusBarNotification("android", "", 56, "", 0, 0, 0,
                mNotification, android.os.Process.myUserHandle(), 0);
        when(mWifiNotificationManager.getActiveNotifications()).thenReturn(activeNotifications);
        mWifiConfiguration.SSID = SSID_2;
        mEapFailureNotifier.onEapFailure(KNOWN_ERROR_CODE, mWifiConfiguration, false);
        verify(mWifiNotificationManager, never()).notify(anyInt(), any());
    }

    /**
     * Verify that a eap failure notification will be generated with the following conditions :
     * Previous notification of eap failure is still displayed on the notification bar.
     * Ssid of previous notification is not same as current ssid
     * Error code is defined by carrier
     * @throws Exception
     */
    @Test
    public void onEapFailureWithDefinedErrorCodeWithNotificationShownWithoutSameSsid()
            throws Exception {
        when(mFrameworkFacade.makeNotificationBuilder(any(),
                eq(WifiService.NOTIFICATION_NETWORK_ALERTS))).thenReturn(mNotificationBuilder);
        StatusBarNotification[] activeNotifications = new StatusBarNotification[1];
        activeNotifications[0] = new StatusBarNotification("android", "", 57, "", 0, 0, 0,
                mNotification, android.os.Process.myUserHandle(), 0);
        when(mWifiNotificationManager.getActiveNotifications()).thenReturn(activeNotifications);
        mEapFailureNotifier.setCurrentShownSsid(SSID_1);
        mWifiConfiguration.SSID = SSID_2;
        mEapFailureNotifier.onEapFailure(KNOWN_ERROR_CODE, mWifiConfiguration, true);
        verify(mWifiNotificationManager).notify(eq(EapFailureNotifier.NOTIFICATION_ID), any());
        ArgumentCaptor<Intent> intent = ArgumentCaptor.forClass(Intent.class);
        verify(mFrameworkFacade).getActivity(
                eq(mContext), eq(0), intent.capture(),
                eq(PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        assertEquals(TEST_SETTINGS_PACKAGE, intent.getValue().getPackage());
        assertEquals(Settings.ACTION_WIFI_SETTINGS, intent.getValue().getAction());
    }

    /**
     * Verify that a eap failure notification will Not be generated with the following conditions :
     * Previous notification of eap failure is still displayed on the notification bar.
     * Ssid of previous notification is same as current ssid
     * Error code is defined by carrier
     * @throws Exception
     */
    @Test
    public void onEapFailureWithDefinedErrorCodeWithNotificationShownWithSameSsid()
            throws Exception {
        when(mFrameworkFacade.makeNotificationBuilder(any(),
                eq(WifiService.NOTIFICATION_NETWORK_ALERTS))).thenReturn(mNotificationBuilder);
        StatusBarNotification[] activeNotifications = new StatusBarNotification[1];
        activeNotifications[0] = new StatusBarNotification("android", "", 57, "", 0, 0, 0,
                mNotification, android.os.Process.myUserHandle(), 0);
        when(mWifiNotificationManager.getActiveNotifications()).thenReturn(activeNotifications);
        mEapFailureNotifier.setCurrentShownSsid(SSID_1);
        mWifiConfiguration.SSID = SSID_1;
        mEapFailureNotifier.onEapFailure(KNOWN_ERROR_CODE, mWifiConfiguration, true);
        verify(mFrameworkFacade, never()).makeNotificationBuilder(any(),
                eq(WifiService.NOTIFICATION_NETWORK_ALERTS));
    }

    /**
     * Verify that a eap failure notification will Not be generated with the following conditions :
     * No eap failure notification of eap failure is displayed now.
     * Error code is defined by carrier
     * Message resource is empty
     * @throws Exception
     */
    @Test
    public void onEapFailureWithDefinedErrorCodeWithoutMessage()
            throws Exception {
        when(mFrameworkFacade.makeNotificationBuilder(any(),
                eq(WifiService.NOTIFICATION_NETWORK_ALERTS))).thenReturn(mNotificationBuilder);
        StatusBarNotification[] activeNotifications = new StatusBarNotification[1];
        activeNotifications[0] = new StatusBarNotification("android", "", 56, "", 0, 0, 0,
                mNotification, android.os.Process.myUserHandle(), 0);
        when(mWifiNotificationManager.getActiveNotifications()).thenReturn(activeNotifications);
        when(mResourceWrapper.getString(eq(ERROR_MESSAGE_OVERLAY_PREFIX + KNOWN_ERROR_CODE),
                anyString())).thenReturn("");
        mEapFailureNotifier.setCurrentShownSsid(SSID_1);
        mWifiConfiguration.SSID = SSID_1;
        mEapFailureNotifier.onEapFailure(KNOWN_ERROR_CODE, mWifiConfiguration, true);
        verify(mFrameworkFacade, never()).makeNotificationBuilder(any(),
                eq(WifiService.NOTIFICATION_NETWORK_ALERTS));
    }

    /**
     * Verify that a eap failure notification will be generated with the following conditions :
     * No eap failure notification of eap failure is displayed now.
     * Error code is unknown
     * @throws Exception
     */
    @Test
    public void onEapFailureWithUnknownErrorCode() throws Exception {
        when(mFrameworkFacade.makeNotificationBuilder(any(),
                eq(WifiService.NOTIFICATION_NETWORK_ALERTS))).thenReturn(mNotificationBuilder);
        StatusBarNotification[] activeNotifications = new StatusBarNotification[1];
        activeNotifications[0] = new StatusBarNotification("android", "", 56, "", 0, 0, 0,
                mNotification, android.os.Process.myUserHandle(), 0);
        when(mWifiNotificationManager.getActiveNotifications()).thenReturn(activeNotifications);
        mWifiConfiguration.SSID = SSID_1;
        mEapFailureNotifier.onEapFailure(UNKNOWN_ERROR_CODE, mWifiConfiguration, true);
        verify(mWifiNotificationManager).notify(eq(EapFailureNotifier.NOTIFICATION_ID), any());
        ArgumentCaptor<Intent> intent = ArgumentCaptor.forClass(Intent.class);
        verify(mFrameworkFacade).getActivity(
                eq(mContext), eq(0), intent.capture(),
                eq(PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        assertEquals(TEST_SETTINGS_PACKAGE, intent.getValue().getPackage());
        assertEquals(Settings.ACTION_WIFI_SETTINGS, intent.getValue().getAction());
    }

    /**
     * Verify that no eap failure notification will be generated with the following conditions :
     * No eap failure notification of eap failure is displayed now.
     * Error code is negative
     * @throws Exception
     */
    @Test
    public void onEapFailureWithNegativeErrorCode() throws Exception {
        when(mFrameworkFacade.makeNotificationBuilder(any(),
                eq(WifiService.NOTIFICATION_NETWORK_ALERTS))).thenReturn(mNotificationBuilder);
        StatusBarNotification[] activeNotifications = new StatusBarNotification[1];
        activeNotifications[0] = new StatusBarNotification("android", "", 56, "", 0, 0, 0,
                mNotification, android.os.Process.myUserHandle(), 0);
        when(mWifiNotificationManager.getActiveNotifications()).thenReturn(activeNotifications);
        mWifiConfiguration.SSID = SSID_1;
        mEapFailureNotifier.onEapFailure(-1, mWifiConfiguration, true);
        verify(mFrameworkFacade, never()).makeNotificationBuilder(any(),
                eq(WifiService.NOTIFICATION_NETWORK_ALERTS));
    }
}
