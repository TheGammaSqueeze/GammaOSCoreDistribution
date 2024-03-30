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

package com.android.server.wifi;

import android.app.ActivityOptions;
import android.content.Intent;
import android.net.wifi.WifiContext;
import android.net.wifi.WifiManager;
import android.os.UserHandle;
import android.util.ArraySet;
import android.util.Log;
import android.util.SparseArray;
import android.view.Display;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.android.modules.utils.build.SdkLevel;

import java.util.Set;

import javax.annotation.concurrent.ThreadSafe;

/**
 * Class to manage launching dialogs via WifiDialog and returning the user reply.
 * All methods run on the main Wi-Fi thread runner except those annotated with @AnyThread, which can
 * run on any thread.
 */
public class WifiDialogManager {
    private static final String TAG = "WifiDialogManager";
    @VisibleForTesting
    static final String WIFI_DIALOG_ACTIVITY_CLASSNAME =
            "com.android.wifi.dialog.WifiDialogActivity";

    private boolean mVerboseLoggingEnabled;

    private int mNextDialogId = 0;
    private final Set<Integer> mActiveDialogIds = new ArraySet<>();
    private final @NonNull SparseArray<DialogHandleInternal> mActiveDialogHandles =
            new SparseArray<>();

    private final @NonNull WifiContext mContext;
    private final @NonNull WifiThreadRunner mWifiThreadRunner;

    /**
     * Constructs a WifiDialogManager
     *
     * @param context          Main Wi-Fi context.
     * @param wifiThreadRunner Main Wi-Fi thread runner.
     */
    public WifiDialogManager(
            @NonNull WifiContext context,
            @NonNull WifiThreadRunner wifiThreadRunner) {
        mContext = context;
        mWifiThreadRunner = wifiThreadRunner;
    }

    /**
     * Enables verbose logging.
     */
    public void enableVerboseLogging(boolean enabled) {
        mVerboseLoggingEnabled = enabled;
    }

    private int getNextDialogId() {
        if (mActiveDialogIds.isEmpty() || mNextDialogId == WifiManager.INVALID_DIALOG_ID) {
            mNextDialogId = 0;
        }
        return mNextDialogId++;
    }

    private @Nullable Intent getBaseLaunchIntent(@WifiManager.DialogType int dialogType) {
        Intent intent = new Intent(WifiManager.ACTION_LAUNCH_DIALOG)
                .putExtra(WifiManager.EXTRA_DIALOG_TYPE, dialogType)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        String wifiDialogApkPkgName = mContext.getWifiDialogApkPkgName();
        if (wifiDialogApkPkgName == null) {
            Log.w(TAG, "Could not get WifiDialog APK package name!");
            return null;
        }
        intent.setClassName(wifiDialogApkPkgName, WIFI_DIALOG_ACTIVITY_CLASSNAME);
        return intent;
    }

    private @Nullable Intent getDismissIntent(int dialogId) {
        Intent intent = new Intent(WifiManager.ACTION_DISMISS_DIALOG);
        intent.putExtra(WifiManager.EXTRA_DIALOG_ID, dialogId);
        String wifiDialogApkPkgName = mContext.getWifiDialogApkPkgName();
        if (wifiDialogApkPkgName == null) {
            Log.w(TAG, "Could not get WifiDialog APK package name!");
            return null;
        }
        intent.setClassName(wifiDialogApkPkgName, WIFI_DIALOG_ACTIVITY_CLASSNAME);
        return intent;
    }

    /**
     * Handle for launching and dismissing a dialog from any thread.
     */
    @ThreadSafe
    public class DialogHandle {
        DialogHandleInternal mInternalHandle;
        private DialogHandle(DialogHandleInternal internalHandle) {
            mInternalHandle = internalHandle;
        }

        /**
         * Launches the dialog.
         */
        @AnyThread
        public void launchDialog() {
            mWifiThreadRunner.post(() -> mInternalHandle.launchDialog(0));
        }

        /**
         * Launches the dialog with a timeout before it is auto-cancelled.
         * @param timeoutMs timeout in milliseconds before the dialog is auto-cancelled. A value <=0
         *                  indicates no timeout.
         */
        @AnyThread
        public void launchDialog(long timeoutMs) {
            mWifiThreadRunner.post(() -> mInternalHandle.launchDialog(timeoutMs));

        }

        /**
         * Dismisses the dialog. Dialogs will automatically be dismissed once the user replies, but
         * this method may be used to dismiss unanswered dialogs that are no longer needed.
         */
        @AnyThread
        public void dismissDialog() {
            mWifiThreadRunner.post(() -> mInternalHandle.dismissDialog());
        }
    }

    /**
     * Internal handle for launching and dismissing a dialog on the main Wi-Fi thread runner.
     * @see {@link DialogHandle}
     */
    private class DialogHandleInternal {
        private int mDialogId = WifiManager.INVALID_DIALOG_ID;
        private final @NonNull Intent mIntent;
        private Runnable mTimeoutRunnable;
        private final int mDisplayId;

        DialogHandleInternal(@NonNull Intent intent, int displayId)
                throws IllegalArgumentException {
            if (intent == null) {
                throw new IllegalArgumentException("Intent cannot be null!");
            }
            mDisplayId = displayId;
            mIntent = intent;
        }

        /**
         * @see {@link DialogHandle#launchDialog(long)}
         */
        void launchDialog(long timeoutMs) {
            if (mDialogId != WifiManager.INVALID_DIALOG_ID) {
                // Dialog is already active, ignore.
                return;
            }
            registerDialog();
            mIntent.putExtra(WifiManager.EXTRA_DIALOG_ID, mDialogId);
            boolean launched = false;
            if (SdkLevel.isAtLeastT() && mDisplayId != Display.DEFAULT_DISPLAY) {
                try {
                    mContext.startActivityAsUser(mIntent,
                            ActivityOptions.makeBasic().setLaunchDisplayId(mDisplayId).toBundle(),
                            UserHandle.CURRENT);
                    launched = true;
                } catch (Exception e) {
                    Log.e(TAG, "Error startActivityAsUser - " + e);
                }
            }
            if (!launched) {
                mContext.startActivityAsUser(mIntent, UserHandle.CURRENT);
            }
            if (mVerboseLoggingEnabled) {
                Log.v(TAG, "Launching dialog with id=" + mDialogId);
            }
            if (timeoutMs > 0) {
                mTimeoutRunnable = () -> onTimeout();
                mWifiThreadRunner.postDelayed(mTimeoutRunnable, timeoutMs);
            }
        }

        /**
         * Callback to run when the dialog times out.
         */
        void onTimeout() {
            dismissDialog();
        }

        /**
         * @see {@link DialogHandle#dismissDialog()}
         */
        void dismissDialog() {
            if (mDialogId == WifiManager.INVALID_DIALOG_ID) {
                // Dialog is not active, ignore.
                return;
            }
            Intent dismissIntent = getDismissIntent(mDialogId);
            if (dismissIntent == null) {
                Log.e(TAG, "Could not create intent for dismissing dialog with id: "
                        + mDialogId);
                return;
            }
            mContext.startActivityAsUser(dismissIntent, UserHandle.CURRENT);
            if (mVerboseLoggingEnabled) {
                Log.v(TAG, "Dismissing dialog with id=" + mDialogId);
            }
            unregisterDialog();
        }

        /**
         * Assigns a dialog id to the dialog and registers it as an active dialog.
         */
        void registerDialog() {
            if (mDialogId != WifiManager.INVALID_DIALOG_ID) {
                // Already registered.
                return;
            }
            mDialogId = getNextDialogId();
            mActiveDialogIds.add(mDialogId);
            mActiveDialogHandles.put(mDialogId, this);
            if (mVerboseLoggingEnabled) {
                Log.v(TAG, "Registered dialog with id=" + mDialogId);
            }
        }

        /**
         * Unregisters the dialog as an active dialog and removes its dialog id.
         * This should be called after a dialog is replied to or dismissed.
         */
        void unregisterDialog() {
            if (mDialogId == WifiManager.INVALID_DIALOG_ID) {
                // Already unregistered.
                return;
            }
            if (mTimeoutRunnable != null) {
                mWifiThreadRunner.removeCallbacks(mTimeoutRunnable);
            }
            mTimeoutRunnable = null;
            mActiveDialogIds.remove(mDialogId);
            mActiveDialogHandles.remove(mDialogId);
            mDialogId = WifiManager.INVALID_DIALOG_ID;
            if (mVerboseLoggingEnabled) {
                Log.v(TAG, "Unregistered dialog with id=" + mDialogId);
            }
        }
    }

    private class SimpleDialogHandle extends DialogHandleInternal {
        private @NonNull SimpleDialogCallback mCallback;
        private @NonNull WifiThreadRunner mCallbackThreadRunner;

        SimpleDialogHandle(
                final String title,
                final String message,
                final String messageUrl,
                final int messageUrlStart,
                final int messageUrlEnd,
                final String positiveButtonText,
                final String negativeButtonText,
                final String neutralButtonText,
                @NonNull SimpleDialogCallback callback,
                @NonNull WifiThreadRunner callbackThreadRunner) throws IllegalArgumentException {
            super(getBaseLaunchIntent(WifiManager.DIALOG_TYPE_SIMPLE)
                    .putExtra(WifiManager.EXTRA_DIALOG_TITLE, title)
                    .putExtra(WifiManager.EXTRA_DIALOG_MESSAGE, message)
                    .putExtra(WifiManager.EXTRA_DIALOG_MESSAGE_URL, messageUrl)
                    .putExtra(WifiManager.EXTRA_DIALOG_MESSAGE_URL_START, messageUrlStart)
                    .putExtra(WifiManager.EXTRA_DIALOG_MESSAGE_URL_END, messageUrlEnd)
                    .putExtra(WifiManager.EXTRA_DIALOG_POSITIVE_BUTTON_TEXT, positiveButtonText)
                    .putExtra(WifiManager.EXTRA_DIALOG_NEGATIVE_BUTTON_TEXT, negativeButtonText)
                    .putExtra(WifiManager.EXTRA_DIALOG_NEUTRAL_BUTTON_TEXT, neutralButtonText),
                    Display.DEFAULT_DISPLAY);
            if (messageUrl != null) {
                if (message == null) {
                    throw new IllegalArgumentException("Cannot set span for null message!");
                }
                if (messageUrlStart < 0) {
                    throw new IllegalArgumentException("Span start cannot be less than 0!");
                }
                if (messageUrlEnd > message.length()) {
                    throw new IllegalArgumentException("Span end index " + messageUrlEnd
                            + " cannot be greater than message length " + message.length() + "!");
                }
            }
            if (callback == null) {
                throw new IllegalArgumentException("Callback cannot be null!");
            }
            if (callbackThreadRunner == null) {
                throw new IllegalArgumentException("Callback thread runner cannot be null!");
            }
            mCallback = callback;
            mCallbackThreadRunner = callbackThreadRunner;
        }

        void notifyOnPositiveButtonClicked() {
            mCallbackThreadRunner.post(() -> mCallback.onPositiveButtonClicked());
            unregisterDialog();
        }

        void notifyOnNegativeButtonClicked() {
            mCallbackThreadRunner.post(() -> mCallback.onNegativeButtonClicked());
            unregisterDialog();
        }

        void notifyOnNeutralButtonClicked() {
            mCallbackThreadRunner.post(() -> mCallback.onNeutralButtonClicked());
            unregisterDialog();
        }

        void notifyOnCancelled() {
            mCallbackThreadRunner.post(() -> mCallback.onCancelled());
            unregisterDialog();
        }

        @Override
        void onTimeout() {
            dismissDialog();
            notifyOnCancelled();
        }
    }

    /**
     * Callback for receiving simple dialog responses.
     */
    public interface SimpleDialogCallback {
        /**
         * The positive button was clicked.
         */
        void onPositiveButtonClicked();

        /**
         * The negative button was clicked.
         */
        void onNegativeButtonClicked();

        /**
         * The neutral button was clicked.
         */
        void onNeutralButtonClicked();

        /**
         * The dialog was cancelled (back button or home button or timeout).
         */
        void onCancelled();
    }

    /**
     * Creates a simple dialog with optional title, message, and positive/negative/neutral buttons.
     *
     * @param title                Title of the dialog.
     * @param message              Message of the dialog.
     * @param positiveButtonText   Text of the positive button or {@code null} for no button.
     * @param negativeButtonText   Text of the negative button or {@code null} for no button.
     * @param neutralButtonText    Text of the neutral button or {@code null} for no button.
     * @param callback             Callback to receive the dialog response.
     * @param callbackThreadRunner WifiThreadRunner to run the callback on.
     * @return DialogHandle        Handle for the dialog, or {@code null} if no dialog could
     *                             be created.
     */
    @AnyThread
    @Nullable
    public DialogHandle createSimpleDialog(
            @Nullable String title,
            @Nullable String message,
            @Nullable String positiveButtonText,
            @Nullable String negativeButtonText,
            @Nullable String neutralButtonText,
            @NonNull SimpleDialogCallback callback,
            @NonNull WifiThreadRunner callbackThreadRunner) {
        try {
            return new DialogHandle(
                    new SimpleDialogHandle(
                            title,
                            message,
                            null /* messageUrl */,
                            0 /* messageUrlStart */,
                            0 /* messageUrlEnd */,
                            positiveButtonText,
                            negativeButtonText,
                            neutralButtonText,
                            callback,
                            callbackThreadRunner)
            );
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Could not create DialogHandle for simple dialog: " + e);
            return null;
        }
    }

    /**
     * Creates a simple dialog with a URL embedded in the message.
     *
     * @param title                Title of the dialog.
     * @param message              Message of the dialog.
     * @param messageUrl           URL to embed in the message. If non-null, then message must also
     *                             be non-null.
     * @param messageUrlStart      Start index (inclusive) of the URL in the message. Must be
     *                             non-negative.
     * @param messageUrlEnd        End index (exclusive) of the URL in the message. Must be less
     *                             than the length of message.
     * @param positiveButtonText   Text of the positive button or {@code null} for no button.
     * @param negativeButtonText   Text of the negative button or {@code null} for no button.
     * @param neutralButtonText    Text of the neutral button or {@code null} for no button.
     * @param callback             Callback to receive the dialog response.
     * @param callbackThreadRunner WifiThreadRunner to run the callback on.
     * @return DialogHandle        Handle for the dialog, or {@code null} if no dialog could
     *                             be created.
     */
    @AnyThread
    @Nullable
    public DialogHandle createSimpleDialogWithUrl(
            @Nullable String title,
            @Nullable String message,
            @Nullable String messageUrl,
            int messageUrlStart,
            int messageUrlEnd,
            @Nullable String positiveButtonText,
            @Nullable String negativeButtonText,
            @Nullable String neutralButtonText,
            @NonNull SimpleDialogCallback callback,
            @NonNull WifiThreadRunner callbackThreadRunner) {
        try {
            return new DialogHandle(
                    new SimpleDialogHandle(
                            title,
                            message,
                            messageUrl,
                            messageUrlStart,
                            messageUrlEnd,
                            positiveButtonText,
                            negativeButtonText,
                            neutralButtonText,
                            callback,
                            callbackThreadRunner)
            );
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Could not create DialogHandle for simple dialog: " + e);
            return null;
        }
    }

    /**
     * Returns the reply to a simple dialog to the callback of matching dialogId.
     * @param dialogId id of the replying dialog.
     * @param reply    reply of the dialog.
     */
    public void replyToSimpleDialog(int dialogId, @WifiManager.DialogReply int reply) {
        if (mVerboseLoggingEnabled) {
            Log.i(TAG, "Response received for simple dialog. id=" + dialogId + " reply=" + reply);
        }
        DialogHandleInternal internalHandle = mActiveDialogHandles.get(dialogId);
        if (internalHandle == null) {
            if (mVerboseLoggingEnabled) {
                Log.w(TAG, "No matching dialog handle for simple dialog id=" + dialogId);
            }
            return;
        }
        if (!(internalHandle instanceof SimpleDialogHandle)) {
            if (mVerboseLoggingEnabled) {
                Log.w(TAG, "Dialog handle with id " + dialogId + " is not for a simple dialog.");
            }
            return;
        }
        switch (reply) {
            case WifiManager.DIALOG_REPLY_POSITIVE:
                ((SimpleDialogHandle) internalHandle).notifyOnPositiveButtonClicked();
                break;
            case WifiManager.DIALOG_REPLY_NEGATIVE:
                ((SimpleDialogHandle) internalHandle).notifyOnNegativeButtonClicked();
                break;
            case WifiManager.DIALOG_REPLY_NEUTRAL:
                ((SimpleDialogHandle) internalHandle).notifyOnNeutralButtonClicked();
                break;
            case WifiManager.DIALOG_REPLY_CANCELLED:
                ((SimpleDialogHandle) internalHandle).notifyOnCancelled();
                break;
            default:
                if (mVerboseLoggingEnabled) {
                    Log.w(TAG, "Received invalid reply=" + reply);
                }
        }
    }

    private class P2pInvitationReceivedDialogHandle extends DialogHandleInternal {
        private @NonNull P2pInvitationReceivedDialogCallback mCallback;
        private @NonNull WifiThreadRunner mCallbackThreadRunner;

        P2pInvitationReceivedDialogHandle(
                final @NonNull String deviceName,
                final boolean isPinRequested,
                @Nullable String displayPin,
                int displayId,
                @NonNull P2pInvitationReceivedDialogCallback callback,
                @NonNull WifiThreadRunner callbackThreadRunner) throws IllegalArgumentException {
            super(getBaseLaunchIntent(WifiManager.DIALOG_TYPE_P2P_INVITATION_RECEIVED)
                    .putExtra(WifiManager.EXTRA_P2P_DEVICE_NAME, deviceName)
                    .putExtra(WifiManager.EXTRA_P2P_PIN_REQUESTED, isPinRequested)
                    .putExtra(WifiManager.EXTRA_P2P_DISPLAY_PIN, displayPin), displayId);
            if (deviceName == null) {
                throw new IllegalArgumentException("Device name cannot be null!");
            }
            if (callback == null) {
                throw new IllegalArgumentException("Callback cannot be null!");
            }
            if (callbackThreadRunner == null) {
                throw new IllegalArgumentException("Callback thread runner cannot be null!");
            }
            mCallback = callback;
            mCallbackThreadRunner = callbackThreadRunner;
        }

        void notifyOnAccepted(@Nullable String optionalPin) {
            mCallbackThreadRunner.post(() -> mCallback.onAccepted(optionalPin));
            unregisterDialog();
        }

        void notifyOnDeclined() {
            mCallbackThreadRunner.post(() -> mCallback.onDeclined());
            unregisterDialog();
        }

        @Override
        void onTimeout() {
            dismissDialog();
            notifyOnDeclined();
        }
    }

    /**
     * Callback for receiving P2P Invitation Received dialog responses.
     */
    public interface P2pInvitationReceivedDialogCallback {
        /**
         * Invitation was accepted.
         *
         * @param optionalPin Optional PIN if a PIN was requested, or {@code null} otherwise.
         */
        void onAccepted(@Nullable String optionalPin);

        /**
         * Invitation was declined or cancelled (back button or home button or timeout).
         */
        void onDeclined();
    }

    /**
     * Creates a P2P Invitation Received dialog.
     *
     * @param deviceName           Name of the device sending the invitation.
     * @param isPinRequested       True if a PIN was requested and a PIN input UI should be shown.
     * @param displayPin           Display PIN, or {@code null} if no PIN should be displayed
     * @param displayId            The ID of the Display on which to place the dialog
     *                             (Display.DEFAULT_DISPLAY
     *                             refers to the default display)
     * @param callback             Callback to receive the dialog response.
     * @param callbackThreadRunner WifiThreadRunner to run the callback on.
     * @return DialogHandle        Handle for the dialog, or {@code null} if no dialog could
     *                             be created.
     */
    @AnyThread
    public DialogHandle createP2pInvitationReceivedDialog(
            @NonNull String deviceName,
            boolean isPinRequested,
            @Nullable String displayPin,
            int displayId,
            @NonNull P2pInvitationReceivedDialogCallback callback,
            @NonNull WifiThreadRunner callbackThreadRunner) {
        try {
            return new DialogHandle(
                    new P2pInvitationReceivedDialogHandle(
                            deviceName,
                            isPinRequested,
                            displayPin,
                            displayId,
                            callback,
                            callbackThreadRunner)
            );
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Could not create DialogHandle for P2P Invitation Received dialog: " + e);
            return null;
        }
    }

    /**
     * Returns the reply to a P2P Invitation Received dialog to the callback of matching dialogId.
     * Note: Must be invoked only from the main Wi-Fi thread.
     *
     * @param dialogId    id of the replying dialog.
     * @param accepted    Whether the invitation was accepted.
     * @param optionalPin PIN of the reply, or {@code null} if none was supplied.
     */
    public void replyToP2pInvitationReceivedDialog(
            int dialogId,
            boolean accepted,
            @Nullable String optionalPin) {
        if (mVerboseLoggingEnabled) {
            Log.i(TAG, "Response received for P2P Invitation Received dialog."
                    + " id=" + dialogId
                    + " accepted=" + accepted
                    + " pin=" + optionalPin);
        }
        DialogHandleInternal internalHandle = mActiveDialogHandles.get(dialogId);
        if (internalHandle == null) {
            if (mVerboseLoggingEnabled) {
                Log.w(TAG, "No matching dialog handle for P2P Invitation Received dialog"
                        + " id=" + dialogId);
            }
            return;
        }
        if (!(internalHandle instanceof P2pInvitationReceivedDialogHandle)) {
            if (mVerboseLoggingEnabled) {
                Log.w(TAG, "Dialog handle with id " + dialogId
                        + " is not for a P2P Invitation Received dialog.");
            }
            return;
        }
        if (accepted) {
            ((P2pInvitationReceivedDialogHandle) internalHandle).notifyOnAccepted(optionalPin);
        } else {
            ((P2pInvitationReceivedDialogHandle) internalHandle).notifyOnDeclined();
        }
    }

    private class P2pInvitationSentDialogHandle extends DialogHandleInternal {
        P2pInvitationSentDialogHandle(
                final @NonNull String deviceName,
                final @NonNull String displayPin,
                int displayId) throws IllegalArgumentException {
            super(getBaseLaunchIntent(WifiManager.DIALOG_TYPE_P2P_INVITATION_SENT)
                    .putExtra(WifiManager.EXTRA_P2P_DEVICE_NAME, deviceName)
                    .putExtra(WifiManager.EXTRA_P2P_DISPLAY_PIN, displayPin),
                    displayId);
            if (deviceName == null) {
                throw new IllegalArgumentException("Device name cannot be null!");
            }
            if (displayPin == null) {
                throw new IllegalArgumentException("Display PIN cannot be null!");
            }
        }
    }

    /**
     * Creates a P2P Invitation Sent dialog.
     *
     * @param deviceName           Name of the device the invitation was sent to.
     * @param displayPin           display PIN
     * @param displayId            display ID
     * @return DialogHandle        Handle for the dialog, or {@code null} if no dialog could
     *                             be created.
     */
    @AnyThread
    public DialogHandle createP2pInvitationSentDialog(
            @NonNull String deviceName,
            @Nullable String displayPin,
            int displayId) {
        try {
            return new DialogHandle(new P2pInvitationSentDialogHandle(deviceName, displayPin,
                    displayId));
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Could not create DialogHandle for P2P Invitation Sent dialog: " + e);
            return null;
        }
    }
}
