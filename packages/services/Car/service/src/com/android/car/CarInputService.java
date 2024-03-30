/*
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
package com.android.car;

import static android.car.CarOccupantZoneManager.DisplayTypeEnum;
import static android.car.user.CarUserManager.USER_LIFECYCLE_EVENT_TYPE_SWITCHING;

import static com.android.car.BuiltinPackageDependency.CAR_ACCESSIBILITY_SERVICE_CLASS;
import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.DUMP_INFO;
import static com.android.car.util.Utils.getContentResolverForUser;
import static com.android.car.util.Utils.isEventOfType;

import android.annotation.Nullable;
import android.annotation.UserIdInt;
import android.car.CarOccupantZoneManager;
import android.car.CarProjectionManager;
import android.car.builtin.input.InputManagerHelper;
import android.car.builtin.util.AssistUtilsHelper;
import android.car.builtin.util.AssistUtilsHelper.VoiceInteractionSessionShowCallbackHelper;
import android.car.builtin.util.Slogf;
import android.car.builtin.view.KeyEventHelper;
import android.car.input.CarInputManager;
import android.car.input.CustomInputEvent;
import android.car.input.ICarInput;
import android.car.input.ICarInputCallback;
import android.car.input.RotaryEvent;
import android.car.user.CarUserManager.UserLifecycleListener;
import android.car.user.UserLifecycleEventFilter;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.hardware.input.InputManager;
import android.net.Uri;
import android.os.Binder;
import android.os.Handler;
import android.os.UserHandle;
import android.provider.CallLog.Calls;
import android.provider.Settings;
import android.telecom.TelecomManager;
import android.text.TextUtils;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.ViewConfiguration;

import com.android.car.bluetooth.CarBluetoothService;
import com.android.car.hal.InputHalService;
import com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport;
import com.android.car.internal.common.UserHelperLite;
import com.android.car.internal.util.IndentingPrintWriter;
import com.android.car.user.CarUserService;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * CarInputService monitors and handles input event through vehicle HAL.
 */
public class CarInputService extends ICarInput.Stub
        implements CarServiceBase, InputHalService.InputListener {
    public static final String ENABLED_ACCESSIBILITY_SERVICES_SEPARATOR = ":";

    private static final int MAX_RETRIES_FOR_ENABLING_ACCESSIBILITY_SERVICES = 5;

    @VisibleForTesting
    static final String TAG = CarLog.TAG_INPUT;

    @VisibleForTesting
    static final String LONG_PRESS_TIMEOUT = "long_press_timeout";

    /** An interface to receive {@link KeyEvent}s as they occur. */
    public interface KeyEventListener {
        /** Called when a key event occurs. */
        void onKeyEvent(KeyEvent event);
    }

    private final class KeyPressTimer {
        private final Runnable mLongPressRunnable;
        private final Runnable mCallback = this::onTimerExpired;
        private final IntSupplier mLongPressDelaySupplier;

        @GuardedBy("CarInputService.this.mLock")
        private final Handler mHandler;
        @GuardedBy("CarInputService.this.mLock")
        private boolean mDown;
        @GuardedBy("CarInputService.this.mLock")
        private boolean mLongPress = false;

        KeyPressTimer(
                Handler handler, IntSupplier longPressDelaySupplier, Runnable longPressRunnable) {
            mHandler = handler;
            mLongPressRunnable = longPressRunnable;
            mLongPressDelaySupplier = longPressDelaySupplier;
        }

        /** Marks that a key was pressed, and starts the long-press timer. */
        void keyDown() {
            synchronized (mLock) {
                mDown = true;
                mLongPress = false;
                mHandler.removeCallbacks(mCallback);
                mHandler.postDelayed(mCallback, mLongPressDelaySupplier.getAsInt());
            }
        }

        /**
         * Marks that a key was released, and stops the long-press timer.
         * <p>
         * Returns true if the press was a long-press.
         */
        boolean keyUp() {
            synchronized (mLock) {
                mHandler.removeCallbacks(mCallback);
                mDown = false;
                return mLongPress;
            }
        }

        private void onTimerExpired() {
            synchronized (mLock) {
                // If the timer expires after key-up, don't retroactively make the press long.
                if (!mDown) {
                    return;
                }
                mLongPress = true;
            }
            mLongPressRunnable.run();
        }
    }

    private final VoiceInteractionSessionShowCallbackHelper mShowCallback =
            new VoiceInteractionSessionShowCallbackHelper() {
                @Override
                public void onFailed() {
                    Slogf.w(TAG, "Failed to show VoiceInteractionSession");
                }

                @Override
                public void onShown() {
                    Slogf.d(TAG, "VoiceInteractionSessionShowCallbackHelper onShown()");
                }
            };

    private final Context mContext;
    private final InputHalService mInputHalService;
    private final CarUserService mUserService;
    private final CarOccupantZoneService mCarOccupantZoneService;
    private final CarBluetoothService mCarBluetoothService;
    private final TelecomManager mTelecomManager;

    // The default handler for main-display input events. By default, injects the events into
    // the input queue via InputManager, but can be overridden for testing.
    private final KeyEventListener mMainDisplayHandler;
    // The supplier for the last-called number. By default, gets the number from the call log.
    // May be overridden for testing.
    private final Supplier<String> mLastCalledNumberSupplier;
    // The supplier for the system long-press delay, in milliseconds. By default, gets the value
    // from Settings.Secure for the current user, falling back to the system-wide default
    // long-press delay defined in ViewConfiguration. May be overridden for testing.
    private final IntSupplier mLongPressDelaySupplier;
    // ComponentName of the RotaryService.
    private final String mRotaryServiceComponentName;

    private final BooleanSupplier mShouldCallButtonEndOngoingCallSupplier;

    private final Object mLock = new Object();

    @GuardedBy("mLock")
    private CarProjectionManager.ProjectionKeyEventHandler mProjectionKeyEventHandler;

    @GuardedBy("mLock")
    private final BitSet mProjectionKeyEventsSubscribed = new BitSet();

    private final KeyPressTimer mVoiceKeyTimer;
    private final KeyPressTimer mCallKeyTimer;

    @GuardedBy("mLock")
    private KeyEventListener mInstrumentClusterKeyListener;

    private final InputCaptureClientController mCaptureController;

    private final UserLifecycleListener mUserLifecycleListener = event -> {
        if (!isEventOfType(TAG, event, USER_LIFECYCLE_EVENT_TYPE_SWITCHING)) {
            return;
        }
        Slogf.d(TAG, "CarInputService.onEvent(%s)", event);

        updateCarAccessibilityServicesSettings(event.getUserId());
    };

    private static int getViewLongPressDelay(Context context) {
        return Settings.Secure.getInt(
                getContentResolverForUser(context, UserHandle.CURRENT.getIdentifier()),
                LONG_PRESS_TIMEOUT,
                ViewConfiguration.getLongPressTimeout());
    }

    public CarInputService(Context context, InputHalService inputHalService,
            CarUserService userService, CarOccupantZoneService occupantZoneService,
            CarBluetoothService bluetoothService) {
        this(context, inputHalService, userService, occupantZoneService, bluetoothService,
                new Handler(CarServiceUtils.getCommonHandlerThread().getLooper()),
                context.getSystemService(TelecomManager.class),
                event -> InputManagerHelper.injectInputEvent(
                        context.getSystemService(InputManager.class), event),
                () -> Calls.getLastOutgoingCall(context),
                () -> getViewLongPressDelay(context),
                () -> context.getResources().getBoolean(R.bool.config_callButtonEndsOngoingCall),
                new InputCaptureClientController(context));
    }

    @VisibleForTesting
    CarInputService(Context context, InputHalService inputHalService, CarUserService userService,
            CarOccupantZoneService occupantZoneService, CarBluetoothService bluetoothService,
            Handler handler, TelecomManager telecomManager, KeyEventListener mainDisplayHandler,
            Supplier<String> lastCalledNumberSupplier, IntSupplier longPressDelaySupplier,
            BooleanSupplier shouldCallButtonEndOngoingCallSupplier,
            InputCaptureClientController captureController) {
        mContext = context;
        mCaptureController = captureController;
        mInputHalService = inputHalService;
        mUserService = userService;
        mCarOccupantZoneService = occupantZoneService;
        mCarBluetoothService = bluetoothService;
        mTelecomManager = telecomManager;
        mMainDisplayHandler = mainDisplayHandler;
        mLastCalledNumberSupplier = lastCalledNumberSupplier;
        mLongPressDelaySupplier = longPressDelaySupplier;

        mVoiceKeyTimer =
                new KeyPressTimer(
                        handler, longPressDelaySupplier, this::handleVoiceAssistLongPress);
        mCallKeyTimer =
                new KeyPressTimer(handler, longPressDelaySupplier, this::handleCallLongPress);

        mRotaryServiceComponentName = mContext.getString(R.string.rotaryService);
        mShouldCallButtonEndOngoingCallSupplier = shouldCallButtonEndOngoingCallSupplier;
    }

    /**
     * Set projection key event listener. If null, unregister listener.
     */
    public void setProjectionKeyEventHandler(
            @Nullable CarProjectionManager.ProjectionKeyEventHandler listener,
            @Nullable BitSet events) {
        synchronized (mLock) {
            mProjectionKeyEventHandler = listener;
            mProjectionKeyEventsSubscribed.clear();
            if (events != null) {
                mProjectionKeyEventsSubscribed.or(events);
            }
        }
    }

    /**
     * Sets the instrument cluster key event listener.
     */
    public void setInstrumentClusterKeyListener(KeyEventListener listener) {
        synchronized (mLock) {
            mInstrumentClusterKeyListener = listener;
        }
    }

    @Override
    public void init() {
        if (!mInputHalService.isKeyInputSupported()) {
            Slogf.w(TAG, "Hal does not support key input.");
            return;
        }
        Slogf.d(TAG, "Hal supports key input.");
        mInputHalService.setInputListener(this);
        UserLifecycleEventFilter userSwitchingEventFilter = new UserLifecycleEventFilter.Builder()
                .addEventType(USER_LIFECYCLE_EVENT_TYPE_SWITCHING).build();
        mUserService.addUserLifecycleListener(userSwitchingEventFilter, mUserLifecycleListener);
    }

    @Override
    public void release() {
        synchronized (mLock) {
            mProjectionKeyEventHandler = null;
            mProjectionKeyEventsSubscribed.clear();
            mInstrumentClusterKeyListener = null;
        }
        mUserService.removeUserLifecycleListener(mUserLifecycleListener);
    }

    @Override
    public void onKeyEvent(KeyEvent event, @DisplayTypeEnum int targetDisplayType) {
        // Special case key code that have special "long press" handling for automotive
        switch (event.getKeyCode()) {
            case KeyEvent.KEYCODE_VOICE_ASSIST:
                handleVoiceAssistKey(event);
                return;
            case KeyEvent.KEYCODE_CALL:
                handleCallKey(event);
                return;
            default:
                break;
        }

        assignDisplayId(event, targetDisplayType);

        // Allow specifically targeted keys to be routed to the cluster
        if (targetDisplayType == CarOccupantZoneManager.DISPLAY_TYPE_INSTRUMENT_CLUSTER
                && handleInstrumentClusterKey(event)) {
            return;
        }
        if (mCaptureController.onKeyEvent(targetDisplayType, event)) {
            return;
        }
        mMainDisplayHandler.onKeyEvent(event);
    }

    private void assignDisplayId(KeyEvent event, @DisplayTypeEnum int targetDisplayType) {
        // Setting display id for driver user id (currently MAIN and CLUSTER display types are
        // linked to driver user only)
        int newDisplayId = mCarOccupantZoneService.getDisplayIdForDriver(targetDisplayType);

        // Display id is overridden even if already set.
        KeyEventHelper.setDisplayId(event, newDisplayId);
    }

    @Override
    public void onRotaryEvent(RotaryEvent event, @DisplayTypeEnum int targetDisplay) {
        if (!mCaptureController.onRotaryEvent(targetDisplay, event)) {
            List<KeyEvent> keyEvents = rotaryEventToKeyEvents(event);
            for (KeyEvent keyEvent : keyEvents) {
                onKeyEvent(keyEvent, targetDisplay);
            }
        }
    }

    @Override
    public void onCustomInputEvent(CustomInputEvent event) {
        if (!mCaptureController.onCustomInputEvent(event)) {
            Slogf.w(TAG, "Failed to propagate (%s)", event);
            return;
        }
        Slogf.d(TAG, "Succeed injecting (%s)", event);
    }

    private static List<KeyEvent> rotaryEventToKeyEvents(RotaryEvent event) {
        int numClicks = event.getNumberOfClicks();
        int numEvents = numClicks * 2; // up / down per each click
        boolean clockwise = event.isClockwise();
        int keyCode;
        switch (event.getInputType()) {
            case CarInputManager.INPUT_TYPE_ROTARY_NAVIGATION:
                keyCode = clockwise
                        ? KeyEvent.KEYCODE_NAVIGATE_NEXT
                        : KeyEvent.KEYCODE_NAVIGATE_PREVIOUS;
                break;
            case CarInputManager.INPUT_TYPE_ROTARY_VOLUME:
                keyCode = clockwise
                        ? KeyEvent.KEYCODE_VOLUME_UP
                        : KeyEvent.KEYCODE_VOLUME_DOWN;
                break;
            default:
                Slogf.e(TAG, "Unknown rotary input type: %d", event.getInputType());
                return Collections.EMPTY_LIST;
        }
        ArrayList<KeyEvent> keyEvents = new ArrayList<>(numEvents);
        for (int i = 0; i < numClicks; i++) {
            long uptime = event.getUptimeMillisForClick(i);
            KeyEvent downEvent = createKeyEvent(/* down= */ true, uptime, uptime, keyCode);
            KeyEvent upEvent = createKeyEvent(/* down= */ false, uptime, uptime, keyCode);
            keyEvents.add(downEvent);
            keyEvents.add(upEvent);
        }
        return keyEvents;
    }

    private static KeyEvent createKeyEvent(boolean down, long downTime, long eventTime,
            int keyCode) {
        return new KeyEvent(
                downTime,
                eventTime,
                /* action= */ down ? KeyEvent.ACTION_DOWN : KeyEvent.ACTION_UP,
                keyCode,
                /* repeat= */ 0,
                /* metaState= */ 0,
                /* deviceId= */ 0,
                /* scancode= */ 0,
                /* flags= */ 0,
                InputDevice.SOURCE_CLASS_BUTTON);
    }

    @Override
    public int requestInputEventCapture(ICarInputCallback callback,
            @DisplayTypeEnum int targetDisplayType,
            int[] inputTypes, int requestFlags) {
        return mCaptureController.requestInputEventCapture(callback, targetDisplayType, inputTypes,
                requestFlags);
    }

    @Override
    public void releaseInputEventCapture(ICarInputCallback callback,
            @DisplayTypeEnum int targetDisplayType) {
        mCaptureController.releaseInputEventCapture(callback, targetDisplayType);
    }

    /**
     * Injects the {@link KeyEvent} passed as parameter against Car Input API.
     * <p>
     * The event's display id will be overridden accordingly to the display type (it will be
     * retrieved from {@link CarOccupantZoneService}).
     *
     * @param event             the event to inject
     * @param targetDisplayType the display type associated with the event
     * @throws SecurityException when caller doesn't have INJECT_EVENTS permission granted
     */
    @Override
    public void injectKeyEvent(KeyEvent event, @DisplayTypeEnum int targetDisplayType) {
        // Permission check
        if (PackageManager.PERMISSION_GRANTED != mContext.checkCallingOrSelfPermission(
                android.Manifest.permission.INJECT_EVENTS)) {
            throw new SecurityException("Injecting KeyEvent requires INJECT_EVENTS permission");
        }

        long token = Binder.clearCallingIdentity();
        try {
            // Redirect event to onKeyEvent
            onKeyEvent(event, targetDisplayType);
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    private void handleVoiceAssistKey(KeyEvent event) {
        int action = event.getAction();
        if (action == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
            mVoiceKeyTimer.keyDown();
            dispatchProjectionKeyEvent(CarProjectionManager.KEY_EVENT_VOICE_SEARCH_KEY_DOWN);
        } else if (action == KeyEvent.ACTION_UP) {
            if (mVoiceKeyTimer.keyUp()) {
                // Long press already handled by handleVoiceAssistLongPress(), nothing more to do.
                // Hand it off to projection, if it's interested, otherwise we're done.
                dispatchProjectionKeyEvent(
                        CarProjectionManager.KEY_EVENT_VOICE_SEARCH_LONG_PRESS_KEY_UP);
                return;
            }

            if (dispatchProjectionKeyEvent(
                    CarProjectionManager.KEY_EVENT_VOICE_SEARCH_SHORT_PRESS_KEY_UP)) {
                return;
            }

            launchDefaultVoiceAssistantHandler();
        }
    }

    private void handleVoiceAssistLongPress() {
        // If projection wants this event, let it take it.
        if (dispatchProjectionKeyEvent(
                CarProjectionManager.KEY_EVENT_VOICE_SEARCH_LONG_PRESS_KEY_DOWN)) {
            return;
        }
        // Otherwise, try to launch voice recognition on a BT device.
        if (launchBluetoothVoiceRecognition()) {
            return;
        }
        // Finally, fallback to the default voice assist handling.
        launchDefaultVoiceAssistantHandler();
    }

    private void handleCallKey(KeyEvent event) {
        int action = event.getAction();
        if (action == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
            mCallKeyTimer.keyDown();
            dispatchProjectionKeyEvent(CarProjectionManager.KEY_EVENT_CALL_KEY_DOWN);
        } else if (action == KeyEvent.ACTION_UP) {
            if (mCallKeyTimer.keyUp()) {
                // Long press already handled by handleCallLongPress(), nothing more to do.
                // Hand it off to projection, if it's interested, otherwise we're done.
                dispatchProjectionKeyEvent(CarProjectionManager.KEY_EVENT_CALL_LONG_PRESS_KEY_UP);
                return;
            }

            if (acceptCallIfRinging()) {
                // Ringing call answered, nothing more to do.
                return;
            }

            if (mShouldCallButtonEndOngoingCallSupplier.getAsBoolean() && endCall()) {
                // On-going call ended, nothing more to do.
                return;
            }

            if (dispatchProjectionKeyEvent(
                    CarProjectionManager.KEY_EVENT_CALL_SHORT_PRESS_KEY_UP)) {
                return;
            }

            launchDialerHandler();
        }
    }

    private void handleCallLongPress() {
        // Long-press answers call if ringing, same as short-press.
        if (acceptCallIfRinging()) {
            return;
        }

        if (mShouldCallButtonEndOngoingCallSupplier.getAsBoolean() && endCall()) {
            return;
        }

        if (dispatchProjectionKeyEvent(CarProjectionManager.KEY_EVENT_CALL_LONG_PRESS_KEY_DOWN)) {
            return;
        }

        dialLastCallHandler();
    }

    private boolean dispatchProjectionKeyEvent(@CarProjectionManager.KeyEventNum int event) {
        CarProjectionManager.ProjectionKeyEventHandler projectionKeyEventHandler;
        synchronized (mLock) {
            projectionKeyEventHandler = mProjectionKeyEventHandler;
            if (projectionKeyEventHandler == null || !mProjectionKeyEventsSubscribed.get(event)) {
                // No event handler, or event handler doesn't want this event - we're done.
                return false;
            }
        }

        projectionKeyEventHandler.onKeyEvent(event);
        return true;
    }

    private void launchDialerHandler() {
        Slogf.i(TAG, "call key, launch dialer intent");
        Intent dialerIntent = new Intent(Intent.ACTION_DIAL);
        mContext.startActivityAsUser(dialerIntent, UserHandle.CURRENT);
    }

    private void dialLastCallHandler() {
        Slogf.i(TAG, "call key, dialing last call");

        String lastNumber = mLastCalledNumberSupplier.get();
        if (!TextUtils.isEmpty(lastNumber)) {
            Intent callLastNumberIntent = new Intent(Intent.ACTION_CALL)
                    .setData(Uri.fromParts("tel", lastNumber, /* fragment= */ null))
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            mContext.startActivityAsUser(callLastNumberIntent, UserHandle.CURRENT);
        }
    }

    private boolean acceptCallIfRinging() {
        if (mTelecomManager != null && mTelecomManager.isRinging()) {
            Slogf.i(TAG, "call key while ringing. Answer the call!");
            mTelecomManager.acceptRingingCall();
            return true;
        }
        return false;
    }

    private boolean endCall() {
        if (mTelecomManager != null && mTelecomManager.isInCall()) {
            Slogf.i(TAG, "End the call!");
            mTelecomManager.endCall();
            return true;
        }
        return false;
    }

    private boolean isBluetoothVoiceRecognitionEnabled() {
        Resources res = mContext.getResources();
        return res.getBoolean(R.bool.enableLongPressBluetoothVoiceRecognition);
    }

    private boolean launchBluetoothVoiceRecognition() {
        if (isBluetoothVoiceRecognitionEnabled()) {
            Slogf.d(TAG, "Attempting to start Bluetooth Voice Recognition.");
            return mCarBluetoothService.startBluetoothVoiceRecognition();
        }
        Slogf.d(TAG, "Unable to start Bluetooth Voice Recognition, it is not enabled.");
        return false;
    }

    private void launchDefaultVoiceAssistantHandler() {
        Slogf.d(TAG, "voice key, invoke AssistUtilsHelper");

        if (!AssistUtilsHelper.showPushToTalkSessionForActiveService(mContext, mShowCallback)) {
            Slogf.w(TAG, "Unable to retrieve assist component for current user");
        }
    }

    /**
     * @return false if the KeyEvent isn't consumed because there is no
     * InstrumentClusterKeyListener.
     */
    private boolean handleInstrumentClusterKey(KeyEvent event) {
        KeyEventListener listener = null;
        synchronized (mLock) {
            listener = mInstrumentClusterKeyListener;
        }
        if (listener == null) {
            return false;
        }
        listener.onKeyEvent(event);
        return true;
    }

    private List<String> getAccessibilityServicesToBeEnabled() {
        String carSafetyAccessibilityServiceComponentName =
                BuiltinPackageDependency.getComponentName(CAR_ACCESSIBILITY_SERVICE_CLASS);
        ArrayList<String> accessibilityServicesToBeEnabled = new ArrayList<>();
        accessibilityServicesToBeEnabled.add(carSafetyAccessibilityServiceComponentName);
        if (!TextUtils.isEmpty(mRotaryServiceComponentName)) {
            accessibilityServicesToBeEnabled.add(mRotaryServiceComponentName);
        }
        return accessibilityServicesToBeEnabled;
    }

    private static List<String> createServiceListFromSettingsString(
            String accessibilityServicesString) {
        return TextUtils.isEmpty(accessibilityServicesString)
                ? new ArrayList<>()
                : Arrays.asList(accessibilityServicesString.split(
                        ENABLED_ACCESSIBILITY_SERVICES_SEPARATOR));
    }

    @Override
    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    public void dump(IndentingPrintWriter writer) {
        writer.println("*Input Service*");
        writer.println("Long-press delay: " + mLongPressDelaySupplier.getAsInt() + "ms");
        writer.println("Call button ends ongoing call: "
                + mShouldCallButtonEndOngoingCallSupplier.getAsBoolean());
        mCaptureController.dump(writer);
    }

    private void updateCarAccessibilityServicesSettings(@UserIdInt int userId) {
        if (UserHelperLite.isHeadlessSystemUser(userId)) {
            return;
        }
        List<String> accessibilityServicesToBeEnabled = getAccessibilityServicesToBeEnabled();
        ContentResolver contentResolverForUser = getContentResolverForUser(mContext, userId);
        List<String> alreadyEnabledServices = createServiceListFromSettingsString(
                Settings.Secure.getString(contentResolverForUser,
                        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES));

        int retry = 0;
        while (!alreadyEnabledServices.containsAll(accessibilityServicesToBeEnabled)
                && retry <= MAX_RETRIES_FOR_ENABLING_ACCESSIBILITY_SERVICES) {
            ArrayList<String> enabledServicesList = new ArrayList<>(alreadyEnabledServices);
            int numAccessibilityServicesToBeEnabled = accessibilityServicesToBeEnabled.size();
            for (int i = 0; i < numAccessibilityServicesToBeEnabled; i++) {
                String serviceToBeEnabled = accessibilityServicesToBeEnabled.get(i);
                if (!enabledServicesList.contains(serviceToBeEnabled)) {
                    enabledServicesList.add(serviceToBeEnabled);
                }
            }
            Settings.Secure.putString(contentResolverForUser,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                    String.join(ENABLED_ACCESSIBILITY_SERVICES_SEPARATOR, enabledServicesList));
            // Read again to account for any race condition with other parts of the code that might
            // be enabling other accessibility services.
            alreadyEnabledServices = createServiceListFromSettingsString(
                    Settings.Secure.getString(contentResolverForUser,
                            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES));
            retry++;
        }
        if (!alreadyEnabledServices.containsAll(accessibilityServicesToBeEnabled)) {
            Slogf.e(TAG, "Failed to enable accessibility services");
        }

        Settings.Secure.putString(contentResolverForUser, Settings.Secure.ACCESSIBILITY_ENABLED,
                "1");
    }
}
