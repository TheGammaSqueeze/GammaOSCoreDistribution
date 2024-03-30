/*
 * Copyright 2019 The Android Open Source Project
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

package com.android.server.bluetooth;

import static com.android.server.bluetooth.BluetoothAirplaneModeListener.APM_BT_NOTIFICATION;
import static com.android.server.bluetooth.BluetoothAirplaneModeListener.APM_ENHANCEMENT;
import static com.android.server.bluetooth.BluetoothAirplaneModeListener.APM_USER_TOGGLED_BLUETOOTH;
import static com.android.server.bluetooth.BluetoothAirplaneModeListener.APM_WIFI_BT_NOTIFICATION;
import static com.android.server.bluetooth.BluetoothAirplaneModeListener.NOTIFICATION_NOT_SHOWN;
import static com.android.server.bluetooth.BluetoothAirplaneModeListener.NOTIFICATION_SHOWN;
import static com.android.server.bluetooth.BluetoothAirplaneModeListener.UNUSED;
import static com.android.server.bluetooth.BluetoothAirplaneModeListener.USED;
import static com.android.server.bluetooth.BluetoothAirplaneModeListener.WIFI_APM_STATE;

import static org.mockito.Mockito.*;

import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.Looper;
import android.provider.Settings;

import androidx.test.filters.MediumTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class BluetoothAirplaneModeListenerTest {
    private static final String PACKAGE_NAME = "TestPackage";

    private BluetoothAirplaneModeListener mBluetoothAirplaneModeListener;

    @Mock private Context mContext;
    @Mock private ContentResolver mContentResolver;
    @Mock private BluetoothManagerService mBluetoothManagerService;
    @Mock private BluetoothModeChangeHelper mHelper;
    @Mock private BluetoothNotificationManager mBluetoothNotificationManager;
    @Mock private PackageManager mPackageManager;
    @Mock private Resources mResources;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        when(mContext.getContentResolver()).thenReturn(mContentResolver);
        when(mHelper.getSettingsInt(BluetoothAirplaneModeListener.TOAST_COUNT))
                .thenReturn(BluetoothAirplaneModeListener.MAX_TOAST_COUNT);
        doNothing().when(mHelper).setSettingsInt(anyString(), anyInt());
        doNothing().when(mHelper).showToastMessage();
        doNothing().when(mHelper).onAirplaneModeChanged(any(BluetoothManagerService.class));

        mBluetoothAirplaneModeListener = new BluetoothAirplaneModeListener(
                mBluetoothManagerService, Looper.getMainLooper(), mContext,
                mBluetoothNotificationManager);
        mBluetoothAirplaneModeListener.start(mHelper);
    }

    @Test
    public void testIgnoreOnAirplanModeChange() {
        Assert.assertFalse(mBluetoothAirplaneModeListener.shouldSkipAirplaneModeChange());

        when(mHelper.isBluetoothOn()).thenReturn(true);
        Assert.assertFalse(mBluetoothAirplaneModeListener.shouldSkipAirplaneModeChange());

        when(mHelper.isMediaProfileConnected()).thenReturn(true);
        Assert.assertTrue(mBluetoothAirplaneModeListener.shouldSkipAirplaneModeChange());
    }

    @Test
    public void testIgnoreOnAirplanModeChangeApmEnhancement() {
        when(mHelper.isAirplaneModeOn()).thenReturn(true);
        when(mHelper.isBluetoothOn()).thenReturn(true);

        // When APM enhancement is disabled, BT remains on when connected to a media profile
        when(mHelper.getSettingsInt(APM_ENHANCEMENT)).thenReturn(0);
        when(mHelper.isMediaProfileConnected()).thenReturn(true);
        Assert.assertTrue(mBluetoothAirplaneModeListener.shouldSkipAirplaneModeChange());

        // When APM enhancement is disabled, BT turns off when not connected to a media profile
        when(mHelper.isMediaProfileConnected()).thenReturn(false);
        Assert.assertFalse(mBluetoothAirplaneModeListener.shouldSkipAirplaneModeChange());

        // When APM enhancement is enabled but not activated by toggling BT in APM,
        // BT remains on when connected to a media profile
        when(mHelper.getSettingsInt(APM_ENHANCEMENT)).thenReturn(1);
        when(mHelper.getSettingsSecureInt(APM_USER_TOGGLED_BLUETOOTH, UNUSED)).thenReturn(UNUSED);
        when(mHelper.isMediaProfileConnected()).thenReturn(true);
        Assert.assertTrue(mBluetoothAirplaneModeListener.shouldSkipAirplaneModeChange());

        // When APM enhancement is enabled but not activated by toggling BT in APM,
        // BT turns off when not connected to a media profile
        when(mHelper.isMediaProfileConnected()).thenReturn(false);
        Assert.assertFalse(mBluetoothAirplaneModeListener.shouldSkipAirplaneModeChange());

        // When APM enhancement is enabled but not activated by toggling BT in APM,
        // BT remains on when the default value for BT in APM is on
        when(mHelper.isBluetoothOnAPM()).thenReturn(true);
        Assert.assertTrue(mBluetoothAirplaneModeListener.shouldSkipAirplaneModeChange());

        // When APM enhancement is enabled but not activated by toggling BT in APM,
        // BT remains off when the default value for BT in APM is off
        when(mHelper.isBluetoothOnAPM()).thenReturn(false);
        Assert.assertFalse(mBluetoothAirplaneModeListener.shouldSkipAirplaneModeChange());

        // When APM enhancement is enabled and activated by toggling BT in APM,
        // BT remains on if user's last choice in APM was on
        when(mHelper.getSettingsSecureInt(APM_USER_TOGGLED_BLUETOOTH, UNUSED)).thenReturn(USED);
        when(mHelper.isBluetoothOnAPM()).thenReturn(true);
        Assert.assertTrue(mBluetoothAirplaneModeListener.shouldSkipAirplaneModeChange());

        // When APM enhancement is enabled and activated by toggling BT in APM,
        // BT turns off if user's last choice in APM was off
        when(mHelper.isBluetoothOnAPM()).thenReturn(false);
        Assert.assertFalse(mBluetoothAirplaneModeListener.shouldSkipAirplaneModeChange());

        // When APM enhancement is enabled and activated by toggling BT in APM,
        // BT turns off if user's last choice in APM was off even when connected to a media profile
        when(mHelper.isMediaProfileConnected()).thenReturn(true);
        Assert.assertFalse(mBluetoothAirplaneModeListener.shouldSkipAirplaneModeChange());
    }

    @Test
    public void testHandleAirplaneModeChange_InvokeAirplaneModeChanged() {
        mBluetoothAirplaneModeListener.handleAirplaneModeChange();
        verify(mHelper).onAirplaneModeChanged(mBluetoothManagerService);
    }

    @Test
    public void testHandleAirplaneModeChange_NotInvokeAirplaneModeChanged_NotPopToast() {
        mBluetoothAirplaneModeListener.mToastCount = BluetoothAirplaneModeListener.MAX_TOAST_COUNT;
        when(mHelper.isBluetoothOn()).thenReturn(true);
        when(mHelper.isMediaProfileConnected()).thenReturn(true);
        when(mHelper.isAirplaneModeOn()).thenReturn(true);
        mBluetoothAirplaneModeListener.handleAirplaneModeChange();

        verify(mHelper).setSettingsInt(Settings.Global.BLUETOOTH_ON,
                BluetoothManagerService.BLUETOOTH_ON_AIRPLANE);
        verify(mHelper, times(0)).showToastMessage();
        verify(mHelper, times(0)).onAirplaneModeChanged(mBluetoothManagerService);
    }

    @Test
    public void testHandleAirplaneModeChange_NotInvokeAirplaneModeChanged_PopToast() {
        mBluetoothAirplaneModeListener.mToastCount = 0;
        when(mHelper.isBluetoothOn()).thenReturn(true);
        when(mHelper.isMediaProfileConnected()).thenReturn(true);
        when(mHelper.isAirplaneModeOn()).thenReturn(true);
        mBluetoothAirplaneModeListener.handleAirplaneModeChange();

        verify(mHelper).setSettingsInt(Settings.Global.BLUETOOTH_ON,
                BluetoothManagerService.BLUETOOTH_ON_AIRPLANE);
        verify(mHelper).showToastMessage();
        verify(mHelper, times(0)).onAirplaneModeChanged(mBluetoothManagerService);
    }

    private void setUpApmNotificationTests() throws Exception {
        when(mHelper.isBluetoothOn()).thenReturn(true);
        when(mHelper.isAirplaneModeOn()).thenReturn(true);
        when(mHelper.isBluetoothOnAPM()).thenReturn(true);
        when(mHelper.getSettingsInt(APM_ENHANCEMENT)).thenReturn(1);
        when(mHelper.getSettingsSecureInt(APM_USER_TOGGLED_BLUETOOTH, UNUSED)).thenReturn(USED);
        when(mHelper.getBluetoothPackageName()).thenReturn(PACKAGE_NAME);
        when(mContext.getPackageManager()).thenReturn(mPackageManager);
        when(mPackageManager.getResourcesForApplication(PACKAGE_NAME)).thenReturn(mResources);
    }

    @Test
    public void testHandleAirplaneModeChange_ShowBtAndWifiApmNotification() throws Exception {
        setUpApmNotificationTests();
        when(mHelper.getSettingsInt(Settings.Global.WIFI_ON)).thenReturn(1);
        when(mHelper.getSettingsSecureInt(WIFI_APM_STATE, 0)).thenReturn(1);
        when(mHelper.getSettingsSecureInt(APM_WIFI_BT_NOTIFICATION, NOTIFICATION_NOT_SHOWN))
                .thenReturn(NOTIFICATION_NOT_SHOWN);

        mBluetoothAirplaneModeListener.handleAirplaneModeChange();

        verify(mHelper).setSettingsInt(Settings.Global.BLUETOOTH_ON,
                BluetoothManagerService.BLUETOOTH_ON_AIRPLANE);
        verify(mBluetoothNotificationManager).sendApmNotification(any(), any());
        verify(mHelper).setSettingsSecureInt(APM_WIFI_BT_NOTIFICATION, NOTIFICATION_SHOWN);
    }

    @Test
    public void testHandleAirplaneModeChange_NotShowBtAndWifiApmNotification() throws Exception {
        setUpApmNotificationTests();
        when(mHelper.getSettingsInt(Settings.Global.WIFI_ON)).thenReturn(1);
        when(mHelper.getSettingsSecureInt(WIFI_APM_STATE, 0)).thenReturn(1);
        when(mHelper.getSettingsSecureInt(APM_WIFI_BT_NOTIFICATION, NOTIFICATION_NOT_SHOWN))
                .thenReturn(NOTIFICATION_SHOWN);

        mBluetoothAirplaneModeListener.handleAirplaneModeChange();

        verify(mHelper).setSettingsInt(Settings.Global.BLUETOOTH_ON,
                BluetoothManagerService.BLUETOOTH_ON_AIRPLANE);
        verify(mBluetoothNotificationManager, never()).sendApmNotification(any(), any());
        verify(mHelper, never()).setSettingsSecureInt(APM_WIFI_BT_NOTIFICATION, NOTIFICATION_SHOWN);
    }

    @Test
    public void testHandleAirplaneModeChange_ShowBtApmNotification() throws Exception {
        setUpApmNotificationTests();
        when(mHelper.getSettingsInt(Settings.Global.WIFI_ON)).thenReturn(1);
        when(mHelper.getSettingsSecureInt(WIFI_APM_STATE, 0)).thenReturn(0);
        when(mHelper.getSettingsSecureInt(APM_BT_NOTIFICATION, NOTIFICATION_NOT_SHOWN))
                .thenReturn(NOTIFICATION_NOT_SHOWN);

        mBluetoothAirplaneModeListener.handleAirplaneModeChange();

        verify(mHelper).setSettingsInt(Settings.Global.BLUETOOTH_ON,
                BluetoothManagerService.BLUETOOTH_ON_AIRPLANE);
        verify(mBluetoothNotificationManager).sendApmNotification(any(), any());
        verify(mHelper).setSettingsSecureInt(APM_BT_NOTIFICATION, NOTIFICATION_SHOWN);
    }

    @Test
    public void testHandleAirplaneModeChange_NotShowBtApmNotification() throws Exception {
        setUpApmNotificationTests();
        when(mHelper.getSettingsInt(Settings.Global.WIFI_ON)).thenReturn(1);
        when(mHelper.getSettingsSecureInt(WIFI_APM_STATE, 0)).thenReturn(0);
        when(mHelper.getSettingsSecureInt(APM_BT_NOTIFICATION, NOTIFICATION_NOT_SHOWN))
                .thenReturn(NOTIFICATION_SHOWN);

        mBluetoothAirplaneModeListener.handleAirplaneModeChange();

        verify(mHelper).setSettingsInt(Settings.Global.BLUETOOTH_ON,
                BluetoothManagerService.BLUETOOTH_ON_AIRPLANE);
        verify(mBluetoothNotificationManager, never()).sendApmNotification(any(), any());
        verify(mHelper, never()).setSettingsSecureInt(APM_BT_NOTIFICATION, NOTIFICATION_SHOWN);
    }

    @Test
    public void testIsPopToast_PopToast() {
        mBluetoothAirplaneModeListener.mToastCount = 0;
        Assert.assertTrue(mBluetoothAirplaneModeListener.shouldPopToast());
        verify(mHelper).setSettingsInt(BluetoothAirplaneModeListener.TOAST_COUNT, 1);
    }

    @Test
    public void testIsPopToast_NotPopToast() {
        mBluetoothAirplaneModeListener.mToastCount = BluetoothAirplaneModeListener.MAX_TOAST_COUNT;
        Assert.assertFalse(mBluetoothAirplaneModeListener.shouldPopToast());
        verify(mHelper, times(0)).setSettingsInt(anyString(), anyInt());
    }
}
