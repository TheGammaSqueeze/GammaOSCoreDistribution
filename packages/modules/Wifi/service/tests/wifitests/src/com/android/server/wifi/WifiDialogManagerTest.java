/*
 * Copyright 2022 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.ActivityOptions;
import android.content.ComponentName;
import android.content.Intent;
import android.net.wifi.WifiContext;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.UserHandle;
import android.view.Display;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.filters.SmallTest;

import com.android.modules.utils.build.SdkLevel;
import com.android.server.wifi.WifiDialogManager.DialogHandle;
import com.android.server.wifi.WifiDialogManager.P2pInvitationReceivedDialogCallback;
import com.android.server.wifi.WifiDialogManager.SimpleDialogCallback;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Unit tests for {@link WifiDialogManager}.
 */
@SmallTest
public class WifiDialogManagerTest extends WifiBaseTest {
    private static final int TIMEOUT_MILLIS = 30_000;
    private static final String TEST_TITLE = "Title";
    private static final String TEST_MESSAGE = "Message";
    private static final String TEST_POSITIVE_BUTTON_TEXT = "Yes";
    private static final String TEST_NEGATIVE_BUTTON_TEXT = "No";
    private static final String TEST_NEUTRAL_BUTTON_TEXT = "Maybe";
    private static final String TEST_DEVICE_NAME = "TEST_DEVICE_NAME";
    private static final String WIFI_DIALOG_APK_PKG_NAME = "WifiDialogApkPkgName";

    @Mock WifiContext mWifiContext;
    @Mock WifiThreadRunner mWifiThreadRunner;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        when(mWifiContext.getWifiDialogApkPkgName()).thenReturn(WIFI_DIALOG_APK_PKG_NAME);
        doThrow(SecurityException.class).when(mWifiContext).startActivityAsUser(any(), any(),
                any());
    }

    private void dispatchMockWifiThreadRunner(WifiThreadRunner wifiThreadRunner) {
        ArgumentCaptor<Runnable> runnableArgumentCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(wifiThreadRunner, atLeastOnce()).post(runnableArgumentCaptor.capture());
        runnableArgumentCaptor.getValue().run();
    }

    /**
     * Helper method to synchronously call {@link DialogHandle#launchDialog(long)}.
     * @param dialogHandle     Dialog handle to call on.
     * @param timeoutMs        Timeout for {@link DialogHandle#launchDialog(long)}.
     * @param wifiThreadRunner Main Wi-Fi thread runner of the WifiDialogManager.
     */
    private void launchDialogSynchronous(
            @NonNull DialogHandle dialogHandle,
            long timeoutMs,
            @NonNull WifiThreadRunner wifiThreadRunner) {
        dialogHandle.launchDialog(timeoutMs);
        ArgumentCaptor<Runnable> launchRunnableArgumentCaptor =
                ArgumentCaptor.forClass(Runnable.class);
        verify(wifiThreadRunner, atLeastOnce()).post(launchRunnableArgumentCaptor.capture());
        launchRunnableArgumentCaptor.getValue().run();
    }

    /**
     * Helper method to synchronously call {@link DialogHandle#dismissDialog()}.
     * @param dialogHandle     Dialog handle to call on.
     * @param wifiThreadRunner Main Wi-Fi thread runner of the WifiDialogManager.
     */
    private void dismissDialogSynchronous(
            @NonNull DialogHandle dialogHandle,
            @NonNull WifiThreadRunner wifiThreadRunner) {
        dialogHandle.dismissDialog();
        ArgumentCaptor<Runnable> dismissRunnableArgumentCaptor =
                ArgumentCaptor.forClass(Runnable.class);
        verify(wifiThreadRunner, atLeastOnce()).post(dismissRunnableArgumentCaptor.capture());
        dismissRunnableArgumentCaptor.getValue().run();
    }

    /**
     * Helper method to verify startActivityAsUser was called a given amount of times and return the
     * last Intent that was sent.
     */
    @NonNull
    private Intent verifyStartActivityAsUser(
            int times,
            @NonNull WifiContext wifiContext) {
        ArgumentCaptor<Intent> intentArgumentCaptor = ArgumentCaptor.forClass(Intent.class);
        verify(wifiContext, times(times))
                .startActivityAsUser(intentArgumentCaptor.capture(), eq(UserHandle.CURRENT));
        return intentArgumentCaptor.getValue();
    }

    /**
     * Helper method to verify display-specific startActivityAsUser was called a given amount of
     * times and return the last Intent that was sent.
     */
    @NonNull
    private Intent verifyStartActivityAsUser(
            int times,
            int displayId,
            @NonNull WifiContext wifiContext) {
        ArgumentCaptor<Intent> intentArgumentCaptor = ArgumentCaptor.forClass(Intent.class);
        ArgumentCaptor<Bundle> bundleArgumentCaptor = ArgumentCaptor.forClass(Bundle.class);
        verify(wifiContext, times(times)).startActivityAsUser(intentArgumentCaptor.capture(),
                bundleArgumentCaptor.capture(), eq(UserHandle.CURRENT));
        assertEquals(ActivityOptions.makeBasic().setLaunchDisplayId(
                        displayId).toBundle().toString(),
                bundleArgumentCaptor.getValue().toString()); // since can't compare Bundles
        return intentArgumentCaptor.getValue();
    }

    /**
     * Helper method to verify the contents of a dismiss Intent
     */
    private void verifyDismissIntent(@NonNull Intent dismissIntent) {
        assertThat(dismissIntent.getAction()).isEqualTo(WifiManager.ACTION_DISMISS_DIALOG);
        ComponentName component = dismissIntent.getComponent();
        assertThat(component.getPackageName()).isEqualTo(WIFI_DIALOG_APK_PKG_NAME);
        assertThat(component.getClassName())
                .isEqualTo(WifiDialogManager.WIFI_DIALOG_ACTIVITY_CLASSNAME);
        assertThat(dismissIntent.hasExtra(WifiManager.EXTRA_DIALOG_ID)).isTrue();
        int dialogId = dismissIntent.getIntExtra(WifiManager.EXTRA_DIALOG_ID,
                WifiManager.INVALID_DIALOG_ID);
        assertThat(dialogId).isNotEqualTo(WifiManager.INVALID_DIALOG_ID);
    }

    /**
     * Helper method to verify the contents of a launch Intent for a simple dialog.
     * @return dialog id of the Intent.
     */
    private int verifySimpleDialogLaunchIntent(
            @NonNull Intent launchIntent,
            @Nullable String expectedTitle,
            @Nullable String expectedMessage,
            @Nullable String expectedPositiveButtonText,
            @Nullable String expectedNegativeButtonText,
            @Nullable String expectedNeutralButtonText) {
        assertThat(launchIntent.getAction()).isEqualTo(WifiManager.ACTION_LAUNCH_DIALOG);
        ComponentName component = launchIntent.getComponent();
        assertThat(component.getPackageName()).isEqualTo(WIFI_DIALOG_APK_PKG_NAME);
        assertThat(component.getClassName())
                .isEqualTo(WifiDialogManager.WIFI_DIALOG_ACTIVITY_CLASSNAME);
        assertThat(launchIntent.hasExtra(WifiManager.EXTRA_DIALOG_ID)).isTrue();
        int dialogId = launchIntent.getIntExtra(WifiManager.EXTRA_DIALOG_ID,
                WifiManager.INVALID_DIALOG_ID);
        assertThat(dialogId).isNotEqualTo(WifiManager.INVALID_DIALOG_ID);
        assertThat(launchIntent.hasExtra(WifiManager.EXTRA_DIALOG_TYPE)).isTrue();
        assertThat(launchIntent.getIntExtra(WifiManager.EXTRA_DIALOG_TYPE,
                WifiManager.DIALOG_TYPE_UNKNOWN))
                .isEqualTo(WifiManager.DIALOG_TYPE_SIMPLE);
        assertThat(launchIntent.hasExtra(WifiManager.EXTRA_DIALOG_TITLE)).isTrue();
        assertThat(launchIntent.getStringExtra(WifiManager.EXTRA_DIALOG_TITLE))
                .isEqualTo(expectedTitle);
        assertThat(launchIntent.hasExtra(WifiManager.EXTRA_DIALOG_MESSAGE)).isTrue();
        assertThat(launchIntent.getStringExtra(WifiManager.EXTRA_DIALOG_MESSAGE))
                .isEqualTo(expectedMessage);
        assertThat(launchIntent.hasExtra(WifiManager.EXTRA_DIALOG_POSITIVE_BUTTON_TEXT)).isTrue();
        assertThat(launchIntent.getStringExtra(WifiManager.EXTRA_DIALOG_POSITIVE_BUTTON_TEXT))
                .isEqualTo(expectedPositiveButtonText);
        assertThat(launchIntent.hasExtra(WifiManager.EXTRA_DIALOG_NEGATIVE_BUTTON_TEXT)).isTrue();
        assertThat(launchIntent.getStringExtra(WifiManager.EXTRA_DIALOG_NEGATIVE_BUTTON_TEXT))
                .isEqualTo(expectedNegativeButtonText);
        assertThat(launchIntent.hasExtra(WifiManager.EXTRA_DIALOG_NEUTRAL_BUTTON_TEXT)).isTrue();
        assertThat(launchIntent.getStringExtra(WifiManager.EXTRA_DIALOG_NEUTRAL_BUTTON_TEXT))
                .isEqualTo(expectedNeutralButtonText);
        return dialogId;
    }

    /**
     * Verifies that launching a simple dialog will result in the correct callback methods invoked
     * when a response is received.
     */
    @Test
    public void testSimpleDialog_launchAndResponse_notifiesCallback() {
        SimpleDialogCallback callback = mock(SimpleDialogCallback.class);
        WifiThreadRunner callbackThreadRunner = mock(WifiThreadRunner.class);
        WifiDialogManager wifiDialogManager =
                new WifiDialogManager(mWifiContext, mWifiThreadRunner);

        // Positive
        DialogHandle dialogHandle = wifiDialogManager.createSimpleDialog(TEST_TITLE, TEST_MESSAGE,
                TEST_POSITIVE_BUTTON_TEXT, TEST_NEGATIVE_BUTTON_TEXT, TEST_NEUTRAL_BUTTON_TEXT,
                callback, callbackThreadRunner);
        launchDialogSynchronous(dialogHandle, 0, mWifiThreadRunner);
        Intent intent = verifyStartActivityAsUser(1, mWifiContext);
        int dialogId = verifySimpleDialogLaunchIntent(intent, TEST_TITLE, TEST_MESSAGE,
                TEST_POSITIVE_BUTTON_TEXT, TEST_NEGATIVE_BUTTON_TEXT, TEST_NEUTRAL_BUTTON_TEXT);
        wifiDialogManager.replyToSimpleDialog(dialogId, WifiManager.DIALOG_REPLY_POSITIVE);
        dispatchMockWifiThreadRunner(callbackThreadRunner);
        verify(callback, times(1)).onPositiveButtonClicked();
        verify(callback, times(0)).onNegativeButtonClicked();
        verify(callback, times(0)).onNeutralButtonClicked();
        verify(callback, times(0)).onCancelled();

        // Positive again -- callback should be removed from callback list, so a second notification
        // should be ignored.
        wifiDialogManager.replyToSimpleDialog(dialogId, WifiManager.DIALOG_REPLY_POSITIVE);
        verify(callback, times(1)).onPositiveButtonClicked();
        verify(callback, times(0)).onNegativeButtonClicked();
        verify(callback, times(0)).onNeutralButtonClicked();
        verify(callback, times(0)).onCancelled();

        // Negative
        dialogHandle = wifiDialogManager.createSimpleDialog(TEST_TITLE, TEST_MESSAGE,
                TEST_POSITIVE_BUTTON_TEXT, TEST_NEGATIVE_BUTTON_TEXT, TEST_NEUTRAL_BUTTON_TEXT,
                callback, callbackThreadRunner);
        launchDialogSynchronous(dialogHandle, 0, mWifiThreadRunner);
        intent = verifyStartActivityAsUser(2, mWifiContext);
        dialogId = verifySimpleDialogLaunchIntent(intent, TEST_TITLE, TEST_MESSAGE,
                TEST_POSITIVE_BUTTON_TEXT, TEST_NEGATIVE_BUTTON_TEXT, TEST_NEUTRAL_BUTTON_TEXT);
        wifiDialogManager.replyToSimpleDialog(dialogId, WifiManager.DIALOG_REPLY_NEGATIVE);
        dispatchMockWifiThreadRunner(callbackThreadRunner);
        verify(callback, times(1)).onPositiveButtonClicked();
        verify(callback, times(1)).onNegativeButtonClicked();
        verify(callback, times(0)).onNeutralButtonClicked();
        verify(callback, times(0)).onCancelled();

        // Neutral
        dialogHandle = wifiDialogManager.createSimpleDialog(
                TEST_TITLE, TEST_MESSAGE, TEST_POSITIVE_BUTTON_TEXT, TEST_NEGATIVE_BUTTON_TEXT,
                TEST_NEUTRAL_BUTTON_TEXT, callback, callbackThreadRunner);
        launchDialogSynchronous(dialogHandle, 0, mWifiThreadRunner);
        intent = verifyStartActivityAsUser(3, mWifiContext);
        dialogId = verifySimpleDialogLaunchIntent(intent, TEST_TITLE, TEST_MESSAGE,
                TEST_POSITIVE_BUTTON_TEXT, TEST_NEGATIVE_BUTTON_TEXT, TEST_NEUTRAL_BUTTON_TEXT);
        wifiDialogManager.replyToSimpleDialog(dialogId, WifiManager.DIALOG_REPLY_NEUTRAL);
        dispatchMockWifiThreadRunner(callbackThreadRunner);
        verify(callback, times(1)).onPositiveButtonClicked();
        verify(callback, times(1)).onNegativeButtonClicked();
        verify(callback, times(1)).onNeutralButtonClicked();
        verify(callback, times(0)).onCancelled();

        // Cancelled
        dialogHandle = wifiDialogManager.createSimpleDialog(TEST_TITLE, TEST_MESSAGE,
                TEST_POSITIVE_BUTTON_TEXT, TEST_NEGATIVE_BUTTON_TEXT, TEST_NEUTRAL_BUTTON_TEXT,
                callback, callbackThreadRunner);
        launchDialogSynchronous(dialogHandle, 0, mWifiThreadRunner);
        intent = verifyStartActivityAsUser(4, mWifiContext);
        dialogId = verifySimpleDialogLaunchIntent(intent, TEST_TITLE, TEST_MESSAGE,
                TEST_POSITIVE_BUTTON_TEXT, TEST_NEGATIVE_BUTTON_TEXT, TEST_NEUTRAL_BUTTON_TEXT);
        wifiDialogManager.replyToSimpleDialog(dialogId, WifiManager.DIALOG_REPLY_CANCELLED);
        dispatchMockWifiThreadRunner(callbackThreadRunner);
        verify(callback, times(1)).onPositiveButtonClicked();
        verify(callback, times(1)).onNegativeButtonClicked();
        verify(callback, times(1)).onNeutralButtonClicked();
        verify(callback, times(1)).onCancelled();
    }

    /**
     * Verifies that launching a simple dialog and dismissing it will send a dismiss intent and
     * prevent future replies to the original dialog id from notifying the callback.
     */
    @Test
    public void testSimpleDialog_launchAndDismiss_dismissesDialog() {
        SimpleDialogCallback callback = mock(SimpleDialogCallback.class);
        WifiThreadRunner callbackThreadRunner = mock(WifiThreadRunner.class);
        WifiDialogManager wifiDialogManager =
                new WifiDialogManager(mWifiContext, mWifiThreadRunner);

        // Launch and dismiss dialog.
        DialogHandle dialogHandle = wifiDialogManager.createSimpleDialog(TEST_TITLE, TEST_MESSAGE,
                TEST_POSITIVE_BUTTON_TEXT, TEST_NEGATIVE_BUTTON_TEXT, TEST_NEUTRAL_BUTTON_TEXT,
                callback, callbackThreadRunner);
        launchDialogSynchronous(dialogHandle, 0, mWifiThreadRunner);
        Intent intent = verifyStartActivityAsUser(1, mWifiContext);
        int dialogId = verifySimpleDialogLaunchIntent(intent, TEST_TITLE, TEST_MESSAGE,
                TEST_POSITIVE_BUTTON_TEXT, TEST_NEGATIVE_BUTTON_TEXT, TEST_NEUTRAL_BUTTON_TEXT);
        dismissDialogSynchronous(dialogHandle, mWifiThreadRunner);
        intent = verifyStartActivityAsUser(2, mWifiContext);
        verifyDismissIntent(intent);

        // A reply to the same dialog id should not trigger callback
        wifiDialogManager.replyToSimpleDialog(dialogId, WifiManager.DIALOG_REPLY_POSITIVE);
        verify(callbackThreadRunner, never()).post(any());
        verify(callback, times(0)).onPositiveButtonClicked();

        // Another call to dismiss should not send another dismiss intent.
        dismissDialogSynchronous(dialogHandle, mWifiThreadRunner);
        verifyStartActivityAsUser(2, mWifiContext);

        // Launch dialog again
        dialogHandle = wifiDialogManager.createSimpleDialog(TEST_TITLE, TEST_MESSAGE,
                TEST_POSITIVE_BUTTON_TEXT, TEST_NEGATIVE_BUTTON_TEXT, TEST_NEUTRAL_BUTTON_TEXT,
                callback, callbackThreadRunner);
        launchDialogSynchronous(dialogHandle, 0, mWifiThreadRunner);
        intent = verifyStartActivityAsUser(3, mWifiContext);
        dialogId = verifySimpleDialogLaunchIntent(intent, TEST_TITLE, TEST_MESSAGE,
                TEST_POSITIVE_BUTTON_TEXT, TEST_NEGATIVE_BUTTON_TEXT, TEST_NEUTRAL_BUTTON_TEXT);

        // Callback should receive replies to the corresponding dialogId now.
        wifiDialogManager.replyToSimpleDialog(dialogId, WifiManager.DIALOG_REPLY_POSITIVE);
        dispatchMockWifiThreadRunner(callbackThreadRunner);
        verify(callback, times(1)).onPositiveButtonClicked();
    }

    /**
     * Verifies the right callback is notified for a response to a simple dialog.
     */
    @Test
    public void testSimpleDialog_multipleDialogs_responseMatchedToCorrectCallback() {
        WifiThreadRunner callbackThreadRunner = mock(WifiThreadRunner.class);
        WifiDialogManager wifiDialogManager =
                new WifiDialogManager(mWifiContext, mWifiThreadRunner);

        // Launch Dialog1
        SimpleDialogCallback callback1 = mock(SimpleDialogCallback.class);
        DialogHandle dialogHandle1 = wifiDialogManager.createSimpleDialog(TEST_TITLE, TEST_MESSAGE,
                TEST_POSITIVE_BUTTON_TEXT, TEST_NEGATIVE_BUTTON_TEXT, TEST_NEUTRAL_BUTTON_TEXT,
                callback1, callbackThreadRunner);
        launchDialogSynchronous(dialogHandle1, 0, mWifiThreadRunner);
        Intent intent = verifyStartActivityAsUser(1, mWifiContext);
        int dialogId1 = verifySimpleDialogLaunchIntent(intent, TEST_TITLE, TEST_MESSAGE,
                TEST_POSITIVE_BUTTON_TEXT, TEST_NEGATIVE_BUTTON_TEXT, TEST_NEUTRAL_BUTTON_TEXT);

        // Launch Dialog2
        SimpleDialogCallback callback2 = mock(SimpleDialogCallback.class);
        DialogHandle dialogHandle2 = wifiDialogManager.createSimpleDialog(TEST_TITLE, TEST_MESSAGE,
                TEST_POSITIVE_BUTTON_TEXT, TEST_NEGATIVE_BUTTON_TEXT, TEST_NEUTRAL_BUTTON_TEXT,
                callback2, callbackThreadRunner);
        launchDialogSynchronous(dialogHandle2, 0, mWifiThreadRunner);
        intent = verifyStartActivityAsUser(2, mWifiContext);
        int dialogId2 = verifySimpleDialogLaunchIntent(intent, TEST_TITLE, TEST_MESSAGE,
                TEST_POSITIVE_BUTTON_TEXT, TEST_NEGATIVE_BUTTON_TEXT, TEST_NEUTRAL_BUTTON_TEXT);

        // callback1 notified
        wifiDialogManager.replyToSimpleDialog(dialogId1, WifiManager.DIALOG_REPLY_POSITIVE);
        dispatchMockWifiThreadRunner(callbackThreadRunner);
        verify(callback1, times(1)).onPositiveButtonClicked();
        verify(callback2, times(0)).onPositiveButtonClicked();

        // callback2 notified
        wifiDialogManager.replyToSimpleDialog(dialogId2, WifiManager.DIALOG_REPLY_POSITIVE);
        dispatchMockWifiThreadRunner(callbackThreadRunner);
        verify(callback1, times(1)).onPositiveButtonClicked();
        verify(callback2, times(1)).onPositiveButtonClicked();
    }

    /**
     * Helper method to verify the contents of a launch Intent for a P2P Invitation Received dialog.
     * @return dialog id of the Intent.
     */
    private int verifyP2pInvitationReceivedDialogLaunchIntent(
            @NonNull Intent launchIntent,
            String expectedDeviceName,
            boolean expectedIsPinRequested,
            @Nullable String expectedDisplayPin) {
        assertThat(launchIntent.getAction()).isEqualTo(WifiManager.ACTION_LAUNCH_DIALOG);
        ComponentName component = launchIntent.getComponent();
        assertThat(component.getPackageName()).isEqualTo(WIFI_DIALOG_APK_PKG_NAME);
        assertThat(component.getClassName())
                .isEqualTo(WifiDialogManager.WIFI_DIALOG_ACTIVITY_CLASSNAME);
        assertThat(launchIntent.hasExtra(WifiManager.EXTRA_DIALOG_ID)).isTrue();
        int dialogId = launchIntent.getIntExtra(WifiManager.EXTRA_DIALOG_ID, -1);
        assertThat(dialogId).isNotEqualTo(-1);
        assertThat(launchIntent.hasExtra(WifiManager.EXTRA_DIALOG_TYPE)).isTrue();
        assertThat(launchIntent.getIntExtra(WifiManager.EXTRA_DIALOG_TYPE,
                WifiManager.DIALOG_TYPE_UNKNOWN))
                .isEqualTo(WifiManager.DIALOG_TYPE_P2P_INVITATION_RECEIVED);
        assertThat(launchIntent.hasExtra(WifiManager.EXTRA_P2P_DEVICE_NAME)).isTrue();
        assertThat(launchIntent.getStringExtra(WifiManager.EXTRA_P2P_DEVICE_NAME))
                .isEqualTo(expectedDeviceName);
        assertThat(launchIntent.hasExtra(WifiManager.EXTRA_P2P_PIN_REQUESTED)).isTrue();
        assertThat(launchIntent.getBooleanExtra(WifiManager.EXTRA_P2P_PIN_REQUESTED, false))
                .isEqualTo(expectedIsPinRequested);
        assertThat(launchIntent.hasExtra(WifiManager.EXTRA_P2P_DISPLAY_PIN)).isTrue();
        assertThat(launchIntent.getStringExtra(WifiManager.EXTRA_P2P_DISPLAY_PIN))
                .isEqualTo(expectedDisplayPin);
        return dialogId;
    }

    /**
     * Verifies that launching a P2P Invitation Received dialog with a callback will result in the
     * correct callback methods invoked when a response is received.
     */
    @Test
    public void testP2pInvitationReceivedDialog_launchAndResponse_notifiesCallback() {
        P2pInvitationReceivedDialogCallback callback =
                mock(P2pInvitationReceivedDialogCallback.class);
        WifiThreadRunner callbackThreadRunner = mock(WifiThreadRunner.class);
        WifiDialogManager wifiDialogManager =
                new WifiDialogManager(mWifiContext, mWifiThreadRunner);

        // Accept without PIN
        DialogHandle dialogHandle = wifiDialogManager.createP2pInvitationReceivedDialog(
                TEST_DEVICE_NAME, false, null, Display.DEFAULT_DISPLAY,
                callback, callbackThreadRunner);
        launchDialogSynchronous(dialogHandle, 0, mWifiThreadRunner);
        Intent intent = verifyStartActivityAsUser(1, mWifiContext);
        int dialogId = verifyP2pInvitationReceivedDialogLaunchIntent(intent,
                TEST_DEVICE_NAME, false, null);
        wifiDialogManager.replyToP2pInvitationReceivedDialog(dialogId, true, null);
        dispatchMockWifiThreadRunner(callbackThreadRunner);
        verify(callback, times(1)).onAccepted(null);

        // Callback should be removed from callback list, so a second notification should be ignored
        wifiDialogManager.replyToP2pInvitationReceivedDialog(dialogId, true, "012345");
        verify(callback, times(0)).onAccepted("012345");

        // Accept with PIN
        dialogHandle = wifiDialogManager.createP2pInvitationReceivedDialog(
                TEST_DEVICE_NAME, true, null, Display.DEFAULT_DISPLAY,
                callback, callbackThreadRunner);
        launchDialogSynchronous(dialogHandle, 0, mWifiThreadRunner);
        intent = verifyStartActivityAsUser(2, mWifiContext);
        dialogId = verifyP2pInvitationReceivedDialogLaunchIntent(intent,
                TEST_DEVICE_NAME, true, null);
        wifiDialogManager.replyToP2pInvitationReceivedDialog(dialogId, true, "012345");
        dispatchMockWifiThreadRunner(callbackThreadRunner);
        verify(callback, times(1)).onAccepted("012345");

        // Accept with PIN but PIN was not requested
        dialogHandle = wifiDialogManager.createP2pInvitationReceivedDialog(
                TEST_DEVICE_NAME, false, null, 123, callback, callbackThreadRunner);
        launchDialogSynchronous(dialogHandle, 0, mWifiThreadRunner);
        if (SdkLevel.isAtLeastT()) {
            verifyStartActivityAsUser(1, 123, mWifiContext);
        }
        intent = verifyStartActivityAsUser(3, mWifiContext);
        dialogId = verifyP2pInvitationReceivedDialogLaunchIntent(intent,
                TEST_DEVICE_NAME, false, null);
        wifiDialogManager.replyToP2pInvitationReceivedDialog(dialogId, true, "012345");
        dispatchMockWifiThreadRunner(callbackThreadRunner);
        verify(callback, times(2)).onAccepted("012345");

        // Accept without PIN but PIN was requested
        dialogHandle = wifiDialogManager.createP2pInvitationReceivedDialog(
                TEST_DEVICE_NAME, true, null, Display.DEFAULT_DISPLAY,
                callback, callbackThreadRunner);
        launchDialogSynchronous(dialogHandle, 0, mWifiThreadRunner);
        intent = verifyStartActivityAsUser(4, mWifiContext);
        dialogId = verifyP2pInvitationReceivedDialogLaunchIntent(intent,
                TEST_DEVICE_NAME, true, null);
        wifiDialogManager.replyToP2pInvitationReceivedDialog(dialogId, true, null);
        dispatchMockWifiThreadRunner(callbackThreadRunner);
        verify(callback, times(2)).onAccepted(null);

        // Decline without PIN
        dialogHandle = wifiDialogManager.createP2pInvitationReceivedDialog(
                TEST_DEVICE_NAME, false, null, Display.DEFAULT_DISPLAY,
                callback, callbackThreadRunner);
        launchDialogSynchronous(dialogHandle, 0, mWifiThreadRunner);
        intent = verifyStartActivityAsUser(5, mWifiContext);
        dialogId = verifyP2pInvitationReceivedDialogLaunchIntent(intent,
                TEST_DEVICE_NAME, false, null);
        wifiDialogManager.replyToP2pInvitationReceivedDialog(dialogId, false, null);
        dispatchMockWifiThreadRunner(callbackThreadRunner);
        verify(callback, times(1)).onDeclined();

        // Decline with PIN
        dialogHandle = wifiDialogManager.createP2pInvitationReceivedDialog(
                TEST_DEVICE_NAME, true, null, Display.DEFAULT_DISPLAY,
                callback, callbackThreadRunner);
        launchDialogSynchronous(dialogHandle, 0, mWifiThreadRunner);
        intent = verifyStartActivityAsUser(6, mWifiContext);
        dialogId = verifyP2pInvitationReceivedDialogLaunchIntent(intent,
                TEST_DEVICE_NAME, true, null);
        wifiDialogManager.replyToP2pInvitationReceivedDialog(dialogId, false, "012345");
        dispatchMockWifiThreadRunner(callbackThreadRunner);
        verify(callback, times(2)).onDeclined();

        // Decline with PIN but PIN was not requested
        dialogHandle = wifiDialogManager.createP2pInvitationReceivedDialog(
                TEST_DEVICE_NAME, false, null, Display.DEFAULT_DISPLAY,
                callback, callbackThreadRunner);
        launchDialogSynchronous(dialogHandle, 0, mWifiThreadRunner);
        intent = verifyStartActivityAsUser(7, mWifiContext);
        dialogId = verifyP2pInvitationReceivedDialogLaunchIntent(intent,
                TEST_DEVICE_NAME, false, null);
        wifiDialogManager.replyToP2pInvitationReceivedDialog(dialogId, false, "012345");
        dispatchMockWifiThreadRunner(callbackThreadRunner);
        verify(callback, times(3)).onDeclined();

        // Decline without PIN but PIN was requested
        dialogHandle = wifiDialogManager.createP2pInvitationReceivedDialog(
                TEST_DEVICE_NAME, true, null, Display.DEFAULT_DISPLAY,
                callback, callbackThreadRunner);
        launchDialogSynchronous(dialogHandle, 0, mWifiThreadRunner);
        intent = verifyStartActivityAsUser(8, mWifiContext);
        dialogId = verifyP2pInvitationReceivedDialogLaunchIntent(intent,
                TEST_DEVICE_NAME, true, null);
        wifiDialogManager.replyToP2pInvitationReceivedDialog(dialogId, false, null);
        dispatchMockWifiThreadRunner(callbackThreadRunner);
        verify(callback, times(4)).onDeclined();
    }

    /**
     * Verifies that launching a simple dialog and dismissing it will send a dismiss intent and
     * prevent future replies to the original dialog id from notifying the callback.
     */
    @Test
    public void testP2pInvitationReceivedDialog_launchAndDismiss_dismissesDialog() {
        P2pInvitationReceivedDialogCallback callback =
                mock(P2pInvitationReceivedDialogCallback.class);
        WifiThreadRunner callbackThreadRunner = mock(WifiThreadRunner.class);
        WifiDialogManager wifiDialogManager =
                new WifiDialogManager(mWifiContext, mWifiThreadRunner);

        // Launch and dismiss dialog.
        DialogHandle dialogHandle = wifiDialogManager.createP2pInvitationReceivedDialog(
                TEST_DEVICE_NAME, false, null, Display.DEFAULT_DISPLAY,
                callback, callbackThreadRunner);
        launchDialogSynchronous(dialogHandle, 0, mWifiThreadRunner);
        Intent intent = verifyStartActivityAsUser(1, mWifiContext);
        int dialogId = verifyP2pInvitationReceivedDialogLaunchIntent(intent,
                TEST_DEVICE_NAME, false, null);
        dismissDialogSynchronous(dialogHandle, mWifiThreadRunner);
        intent = verifyStartActivityAsUser(2, mWifiContext);
        verifyDismissIntent(intent);

        // A reply to the same dialog id should not trigger callback
        wifiDialogManager.replyToP2pInvitationReceivedDialog(dialogId, true, null);
        verify(callbackThreadRunner, never()).post(any());
        verify(callback, times(0)).onAccepted(null);

        // Another call to dismiss should not send another dismiss intent.
        dismissDialogSynchronous(dialogHandle, mWifiThreadRunner);
        verifyStartActivityAsUser(2, mWifiContext);

        // Launch dialog again
        dialogHandle = wifiDialogManager.createP2pInvitationReceivedDialog(
                TEST_DEVICE_NAME, false, null, Display.DEFAULT_DISPLAY,
                callback, callbackThreadRunner);
        launchDialogSynchronous(dialogHandle, 0, mWifiThreadRunner);
        intent = verifyStartActivityAsUser(3, mWifiContext);
        dialogId = verifyP2pInvitationReceivedDialogLaunchIntent(intent,
                TEST_DEVICE_NAME, false, null);

        // Callback should receive replies to the corresponding dialogId now.
        wifiDialogManager.replyToP2pInvitationReceivedDialog(dialogId, true, null);
        dispatchMockWifiThreadRunner(callbackThreadRunner);
        verify(callback, times(1)).onAccepted(null);
    }

    /**
     * Verifies the right callback is notified for a response to a P2P Invitation Received dialog.
     */
    @Test
    public void testP2pInvitationReceivedDialog_multipleDialogs_responseMatchedToCorrectCallback() {
        WifiDialogManager wifiDialogManager =
                new WifiDialogManager(mWifiContext, mWifiThreadRunner);

        // Launch Dialog1
        P2pInvitationReceivedDialogCallback callback1 =
                mock(P2pInvitationReceivedDialogCallback.class);
        WifiThreadRunner callbackThreadRunner = mock(WifiThreadRunner.class);
        DialogHandle dialogHandle1 = wifiDialogManager.createP2pInvitationReceivedDialog(
                TEST_DEVICE_NAME, false, null, Display.DEFAULT_DISPLAY,
                callback1, callbackThreadRunner);
        launchDialogSynchronous(dialogHandle1, 0, mWifiThreadRunner);
        Intent intent1 = verifyStartActivityAsUser(1, mWifiContext);
        int dialogId1 = verifyP2pInvitationReceivedDialogLaunchIntent(intent1,
                TEST_DEVICE_NAME, false, null);

        // Launch Dialog2
        P2pInvitationReceivedDialogCallback callback2 =
                mock(P2pInvitationReceivedDialogCallback.class);
        DialogHandle dialogHandle2 = wifiDialogManager.createP2pInvitationReceivedDialog(
                TEST_DEVICE_NAME, false, null, Display.DEFAULT_DISPLAY,
                callback2, callbackThreadRunner);
        launchDialogSynchronous(dialogHandle2, 0, mWifiThreadRunner);
        Intent intent2 = verifyStartActivityAsUser(2, mWifiContext);
        int dialogId2 = verifyP2pInvitationReceivedDialogLaunchIntent(intent2,
                TEST_DEVICE_NAME, false, null);

        // callback1 notified
        wifiDialogManager.replyToP2pInvitationReceivedDialog(dialogId1, true, null);
        dispatchMockWifiThreadRunner(callbackThreadRunner);
        verify(callback1, times(1)).onAccepted(null);
        verify(callback2, times(0)).onAccepted(null);

        // callback2 notified
        wifiDialogManager.replyToP2pInvitationReceivedDialog(dialogId2, true, null);
        dispatchMockWifiThreadRunner(callbackThreadRunner);
        verify(callback1, times(1)).onAccepted(null);
        verify(callback2, times(1)).onAccepted(null);
    }

    /**
     * Verifies that a P2P Invitation Received dialog is cancelled after the specified timeout
     */
    @Test
    public void testP2pInvitationReceivedDialog_timeout_cancelsDialog() {
        WifiDialogManager wifiDialogManager =
                new WifiDialogManager(mWifiContext, mWifiThreadRunner);

        // Launch Dialog without timeout.
        P2pInvitationReceivedDialogCallback callback =
                mock(P2pInvitationReceivedDialogCallback.class);
        WifiThreadRunner callbackThreadRunner = mock(WifiThreadRunner.class);
        DialogHandle dialogHandle = wifiDialogManager.createP2pInvitationReceivedDialog(
                TEST_DEVICE_NAME, false, null, Display.DEFAULT_DISPLAY,
                callback, callbackThreadRunner);
        launchDialogSynchronous(dialogHandle, 0, mWifiThreadRunner);
        Intent intent = verifyStartActivityAsUser(1, mWifiContext);
        int dialogId = verifyP2pInvitationReceivedDialogLaunchIntent(intent,
                TEST_DEVICE_NAME, false, null);

        // Verify cancel runnable wasn't posted.
        verify(mWifiThreadRunner, never()).postDelayed(any(Runnable.class), anyInt());

        // Launch Dialog with timeout
        callback = mock(P2pInvitationReceivedDialogCallback.class);
        dialogHandle = wifiDialogManager.createP2pInvitationReceivedDialog(
                TEST_DEVICE_NAME, false, null, Display.DEFAULT_DISPLAY,
                callback, callbackThreadRunner);
        launchDialogSynchronous(dialogHandle, TIMEOUT_MILLIS, mWifiThreadRunner);
        intent = verifyStartActivityAsUser(2, mWifiContext);
        dialogId = verifyP2pInvitationReceivedDialogLaunchIntent(intent,
                TEST_DEVICE_NAME, false, null);

        // Verify the timeout runnable was posted and run it.
        ArgumentCaptor<Runnable> runnableArgumentCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(mWifiThreadRunner, times(1))
                .postDelayed(runnableArgumentCaptor.capture(), eq((long) TIMEOUT_MILLIS));
        runnableArgumentCaptor.getValue().run();

        // Verify that a dismiss Intent was sent and the callback was declined.
        intent = verifyStartActivityAsUser(3, mWifiContext);
        assertThat(intent.getAction()).isEqualTo(WifiManager.ACTION_DISMISS_DIALOG);
        ComponentName component = intent.getComponent();
        assertThat(component.getPackageName()).isEqualTo(WIFI_DIALOG_APK_PKG_NAME);
        assertThat(component.getClassName())
                .isEqualTo(WifiDialogManager.WIFI_DIALOG_ACTIVITY_CLASSNAME);
        assertThat(intent.hasExtra(WifiManager.EXTRA_DIALOG_ID)).isTrue();
        assertThat(intent.getIntExtra(WifiManager.EXTRA_DIALOG_ID, -1)).isEqualTo(dialogId);
        dispatchMockWifiThreadRunner(callbackThreadRunner);
        verify(callback).onDeclined();

        // Launch Dialog with timeout
        callback = mock(P2pInvitationReceivedDialogCallback.class);
        dialogHandle = wifiDialogManager.createP2pInvitationReceivedDialog(
                TEST_DEVICE_NAME, false, null, Display.DEFAULT_DISPLAY,
                callback, callbackThreadRunner);
        launchDialogSynchronous(dialogHandle, TIMEOUT_MILLIS, mWifiThreadRunner);
        intent = verifyStartActivityAsUser(4, mWifiContext);
        dialogId = verifyP2pInvitationReceivedDialogLaunchIntent(intent,
                TEST_DEVICE_NAME, false, null);
        // Reply before the timeout is over
        wifiDialogManager.replyToP2pInvitationReceivedDialog(dialogId, true, null);
        dispatchMockWifiThreadRunner(callbackThreadRunner);

        // Verify callback was replied to, and the cancel runnable was posted but then removed.
        verify(callback).onAccepted(null);
        verify(callback, never()).onDeclined();
        verify(mWifiThreadRunner, times(2))
                .postDelayed(runnableArgumentCaptor.capture(), eq((long) TIMEOUT_MILLIS));
        verify(mWifiThreadRunner).removeCallbacks(runnableArgumentCaptor.getValue());
    }
}
