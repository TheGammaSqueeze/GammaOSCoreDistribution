/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.hardware.devicestate.cts;

import static android.hardware.devicestate.DeviceStateManager.MAXIMUM_DEVICE_STATE;
import static android.hardware.devicestate.DeviceStateManager.MINIMUM_DEVICE_STATE;
import static android.server.wm.DeviceStateUtils.assertValidState;
import static android.server.wm.DeviceStateUtils.runWithControlDeviceStatePermission;
import static android.view.Display.DEFAULT_DISPLAY;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import android.content.Context;
import android.content.res.Resources;
import android.hardware.devicestate.DeviceStateManager;
import android.hardware.devicestate.DeviceStateRequest;
import android.server.wm.jetpack.utils.ExtensionUtil;
import android.server.wm.jetpack.utils.Version;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.PollingCheck;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executor;

/** CTS tests for {@link DeviceStateManager} API(s). */
@RunWith(AndroidJUnit4.class)
public class DeviceStateManagerTests extends DeviceStateManagerTestBase {

    public static final int TIMEOUT = 2000;

    private static final int INVALID_DEVICE_STATE = -1;

    /** Vendor extension version. Some API behaviors are only available in newer version. */
    private static final Version WM_EXTENSION_VERSION = ExtensionUtil.getExtensionVersion();

    /**
     * Tests that {@link DeviceStateManager#getSupportedStates()} returns at least one state and
     * that none of the returned states are in the range
     * [{@link #MINIMUM_DEVICE_STATE}, {@link #MAXIMUM_DEVICE_STATE}].
     */
    @Test
    public void testValidSupportedStates() throws Exception {
        final int[] supportedStates = getDeviceStateManager().getSupportedStates();
        assertTrue(supportedStates.length > 0);

        for (int i = 0; i < supportedStates.length; i++) {
            final int state = supportedStates[i];
            assertValidState(state);
        }
    }

    /**
     * Tests that calling {@link DeviceStateManager#requestState(DeviceStateRequest, Executor,
     * DeviceStateRequest.Callback)} is successful and results in a registered callback being
     * triggered with a value equal to the requested state.
     */
    @Test
    public void testRequestAllSupportedStates() throws Throwable {
        final ArgumentCaptor<Integer> intAgumentCaptor = ArgumentCaptor.forClass(Integer.class);
        final DeviceStateManager.DeviceStateCallback callback
                = mock(DeviceStateManager.DeviceStateCallback.class);
        final DeviceStateManager manager = getDeviceStateManager();
        manager.registerCallback(Runnable::run, callback);

        final int[] supportedStates = manager.getSupportedStates();
        for (int i = 0; i < supportedStates.length; i++) {
            final DeviceStateRequest request
                    = DeviceStateRequest.newBuilder(supportedStates[i]).build();

            runWithRequestActive(request, false, () -> {
                verify(callback, atLeastOnce()).onStateChanged(intAgumentCaptor.capture());
                assertEquals(intAgumentCaptor.getValue().intValue(), request.getState());
            });
        }
    }

    @Test
    public void testRequestBaseState() throws Throwable {
        assumeExtensionVersionAtLeast2();
        final ArgumentCaptor<Integer> intAgumentCaptor = ArgumentCaptor.forClass(Integer.class);
        final DeviceStateManager.DeviceStateCallback callback =
                mock(DeviceStateManager.DeviceStateCallback.class);
        final DeviceStateManager manager = getDeviceStateManager();

        manager.registerCallback(Runnable::run, callback);

        DeviceStateRequest request = DeviceStateRequest.newBuilder(0).build();
        runWithRequestActive(request, true, () -> {
            verify(callback, atLeastOnce()).onStateChanged(intAgumentCaptor.capture());
            assertEquals(intAgumentCaptor.getValue().intValue(), request.getState());
        });
    }

    /**
     * Tests that calling {@link DeviceStateManager#requestState(DeviceStateRequest, Executor,
     * DeviceStateRequest.Callback)} throws an {@link java.lang.IllegalArgumentException} if
     * supplied with a state above {@link MAXIMUM_DEVICE_STATE}.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testRequestStateTooLarge() throws Throwable {
        final DeviceStateManager manager = getDeviceStateManager();
        final DeviceStateRequest request
                = DeviceStateRequest.newBuilder(MAXIMUM_DEVICE_STATE + 1).build();
        runWithControlDeviceStatePermission(() -> manager.requestState(request, null, null));
    }

    /**
     * Tests that calling {@link DeviceStateManager#requestState(DeviceStateRequest, Executor,
     * DeviceStateRequest.Callback)} throws an {@link java.lang.IllegalArgumentException} if
     * supplied with a state below {@link MINIMUM_DEVICE_STATE}.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testRequestStateTooSmall() throws Throwable {
        final DeviceStateManager manager = getDeviceStateManager();
        final DeviceStateRequest request
                = DeviceStateRequest.newBuilder(MINIMUM_DEVICE_STATE - 1).build();
        runWithControlDeviceStatePermission(() -> manager.requestState(request, null, null));
    }

    /**
     * Tests that calling {@link DeviceStateManager#requestState(DeviceStateRequest, Executor,
     * DeviceStateRequest.Callback)} is not successful and results in a failure to change the
     * state of the device due to the state requested not being available for apps to request.
     */
    @Test
    public void testRequestStateFailsAsTopApp_ifStateNotDefinedAsAvailableForAppsToRequest()
            throws IllegalArgumentException {
        final DeviceStateManager manager = getDeviceStateManager();
        final int[] supportedStates = manager.getSupportedStates();
        // We want to verify that the app can change device state
        // So we only attempt if there are more than 1 possible state.
        assumeTrue(supportedStates.length > 1);
        Set<Integer> statesAvailableToRequest = getAvailableStatesToRequest(
                InstrumentationRegistry.getInstrumentation().getTargetContext(), supportedStates);
        // checks that not every state is available for an app to request
        assumeTrue(statesAvailableToRequest.size() < supportedStates.length);

        Set<Integer> availableDeviceStates = generateDeviceStateSet(supportedStates);

        final StateTrackingCallback callback = new StateTrackingCallback();
        manager.registerCallback(Runnable::run, callback);
        PollingCheck.waitFor(TIMEOUT, () -> callback.mCurrentState != INVALID_DEVICE_STATE);
        final TestActivitySession<DeviceStateTestActivity> activitySession =
                createManagedTestActivitySession();

        activitySession.launchTestActivityOnDisplaySync(
                DeviceStateTestActivity.class,
                DEFAULT_DISPLAY
        );

        DeviceStateTestActivity activity = activitySession.getActivity();

        Set<Integer> possibleStates = possibleStates(false /* shouldSucceed */,
                availableDeviceStates,
                statesAvailableToRequest);
        int nextState = calculateDifferentState(callback.mCurrentState, possibleStates);
        // checks that we were able to find a valid state to request.
        assumeTrue(nextState != INVALID_DEVICE_STATE);

        activity.requestDeviceStateChange(nextState);

        assertTrue(activity.requestStateFailed);
    }

    /**
     * Tests that calling {@link DeviceStateManager#requestState(DeviceStateRequest, Executor,
     * DeviceStateRequest.Callback)} is successful and results in a registered callback being
     * triggered with a value equal to the requested state.
     */
    @Test
    public void testRequestStateSucceedsAsTopApp_ifStateDefinedAsAvailableForAppsToRequest()
            throws Throwable {
        final DeviceStateManager manager = getDeviceStateManager();
        final int[] supportedStates = manager.getSupportedStates();

        // We want to verify that the app can change device state
        // So we only attempt if there are more than 1 possible state.
        assumeTrue(supportedStates.length > 1);
        Set<Integer> statesAvailableToRequest = getAvailableStatesToRequest(
                InstrumentationRegistry.getInstrumentation().getTargetContext(), supportedStates);
        assumeTrue(statesAvailableToRequest.size() > 0);

        Set<Integer> availableDeviceStates = generateDeviceStateSet(supportedStates);

        final StateTrackingCallback callback = new StateTrackingCallback();
        manager.registerCallback(Runnable::run, callback);
        PollingCheck.waitFor(TIMEOUT, () -> callback.mCurrentState != INVALID_DEVICE_STATE);
        final TestActivitySession<DeviceStateTestActivity> activitySession =
                createManagedTestActivitySession();

        activitySession.launchTestActivityOnDisplaySync(
                DeviceStateTestActivity.class,
                DEFAULT_DISPLAY
        );

        DeviceStateTestActivity activity = activitySession.getActivity();

        Set<Integer> possibleStates = possibleStates(true /* shouldSucceed */,
                availableDeviceStates,
                statesAvailableToRequest);
        int nextState = calculateDifferentState(callback.mCurrentState, possibleStates);
        // checks that we were able to find a valid state to request.
        assumeTrue(nextState != INVALID_DEVICE_STATE);

        runWithControlDeviceStatePermission(() -> activity.requestDeviceStateChange(nextState));

        PollingCheck.waitFor(TIMEOUT, () -> callback.mCurrentState == nextState);

        assertEquals(nextState, callback.mCurrentState);
        assertFalse(activity.requestStateFailed);

        manager.cancelStateRequest(); // reset device state after successful request
    }

    /**
     * Tests that calling {@link DeviceStateManager#requestState} is unsuccessful and results in a
     * failure to update the state of the device as expected since the activity is backgrounded.
     */
    @Test
    public void testRequestStateFailsAsBackgroundApp() throws IllegalArgumentException {
        final DeviceStateManager manager = getDeviceStateManager();
        final int[] supportedStates = manager.getSupportedStates();
        // We want to verify that the app can change device state
        // So we only attempt if there are more than 1 possible state.
        assumeTrue(supportedStates.length > 1);
        Set<Integer> statesAvailableToRequest = getAvailableStatesToRequest(
                InstrumentationRegistry.getInstrumentation().getTargetContext(), supportedStates);
        assumeTrue(statesAvailableToRequest.size() > 0);

        Set<Integer> availableDeviceStates = generateDeviceStateSet(supportedStates);

        final StateTrackingCallback callback = new StateTrackingCallback();
        manager.registerCallback(Runnable::run, callback);
        PollingCheck.waitFor(TIMEOUT, () -> callback.mCurrentState != INVALID_DEVICE_STATE);

        final TestActivitySession<DeviceStateTestActivity> activitySession =
                createManagedTestActivitySession();
        activitySession.launchTestActivityOnDisplaySync(
                DeviceStateTestActivity.class,
                DEFAULT_DISPLAY
        );

        DeviceStateTestActivity activity = activitySession.getActivity();
        assertFalse(activity.requestStateFailed);

        launchHomeActivity(); // places our test activity in the background

        Set<Integer> possibleStates = possibleStates(true /* shouldSucceed */,
                availableDeviceStates,
                statesAvailableToRequest);
        int nextState = calculateDifferentState(callback.mCurrentState, possibleStates);
        // checks that we were able to find a valid state to request.
        assumeTrue(nextState != INVALID_DEVICE_STATE);

        activity.requestDeviceStateChange(nextState);

        assertTrue(activity.requestStateFailed);
    }

    /**
     * Tests that calling {@link DeviceStateManager#cancelStateRequest} is successful and results
     * in a registered callback being triggered with a value equal to the base state.
     */
    @Test
    public void testCancelStateRequestFromNewActivity() throws Throwable {
        final DeviceStateManager manager = getDeviceStateManager();
        final int[] supportedStates = manager.getSupportedStates();
        // We want to verify that the app can change device state
        // So we only attempt if there are more than 1 possible state.
        assumeTrue(supportedStates.length > 1);
        Set<Integer> statesAvailableToRequest = getAvailableStatesToRequest(
                InstrumentationRegistry.getInstrumentation().getTargetContext(), supportedStates);
        assumeFalse(statesAvailableToRequest.isEmpty());

        Set<Integer> availableDeviceStates = generateDeviceStateSet(supportedStates);

        final StateTrackingCallback callback = new StateTrackingCallback();
        manager.registerCallback(Runnable::run, callback);
        PollingCheck.waitFor(TIMEOUT, () -> callback.mCurrentState != INVALID_DEVICE_STATE);
        final TestActivitySession<DeviceStateTestActivity> activitySession =
                createManagedTestActivitySession();

        activitySession.launchTestActivityOnDisplaySync(
                DeviceStateTestActivity.class,
                DEFAULT_DISPLAY
        );

        final DeviceStateTestActivity activity = activitySession.getActivity();

        int originalState = callback.mCurrentState;

        Set<Integer> possibleStates = possibleStates(true /* shouldSucceed */,
                availableDeviceStates,
                statesAvailableToRequest);
        int nextState = calculateDifferentState(callback.mCurrentState, possibleStates);
        // checks that we were able to find a valid state to request.
        assumeTrue(nextState != INVALID_DEVICE_STATE);

        runWithControlDeviceStatePermission(() -> activity.requestDeviceStateChange(nextState));

        PollingCheck.waitFor(TIMEOUT, () -> callback.mCurrentState == nextState);

        assertEquals(nextState, callback.mCurrentState);
        assertFalse(activity.requestStateFailed);

        activity.finish();

        final TestActivitySession<DeviceStateTestActivity> secondActivitySession =
                createManagedTestActivitySession();
        secondActivitySession.launchTestActivityOnDisplaySync(
                DeviceStateTestActivity.class,
                DEFAULT_DISPLAY
        );
        // verify that the overridden state is still active after finishing
        // and launching the second activity.
        assertEquals(nextState, callback.mCurrentState);

        final DeviceStateTestActivity activity2 = secondActivitySession.getActivity();
        activity2.cancelOverriddenState();

        PollingCheck.waitFor(TIMEOUT, () -> callback.mCurrentState == originalState);

        assertEquals(originalState, callback.mCurrentState);
    }


    /**
     * Reads in the states that are available to be requested by apps from the configuration file
     * and returns a set of all valid states that are read in.
     *
     * @param context The context used to get the configuration values from {@link Resources}
     * @param supportedStates The device states that are supported on that device.
     * @return {@link Set} of valid device states that are read in.
     */
    private static Set<Integer> getAvailableStatesToRequest(Context context,
            int[] supportedStates) {
        Set<Integer> availableStatesToRequest = new HashSet<>();
        String[] availableStateIdentifiers = context.getResources().getStringArray(
                Resources.getSystem().getIdentifier("config_deviceStatesAvailableForAppRequests",
                        "array",
                        "android"));
        for (String identifier : availableStateIdentifiers) {
            int stateIdentifier = context.getResources()
                    .getIdentifier(identifier, "integer", "android");
            int state = context.getResources().getInteger(stateIdentifier);
            if (isValidState(state, supportedStates)) {
                availableStatesToRequest.add(context.getResources().getInteger(stateIdentifier));
            }
        }
        return availableStatesToRequest;
    }

    private static boolean isValidState(int state, int[] supportedStates) {
        for (int i = 0; i < supportedStates.length; i++) {
            if (state == supportedStates[i]) {
                return true;
            }
        }
        return false;
    }

    /**
     * Generates a set of possible device states based on a {@link Set} of valid device states,
     * {@code supportedDeviceStates}, and the set of device states available to be requested
     * {@code availableStatesToRequest}, as well as if the request should succeed or not, given by
     * {@code shouldSucceed}.
     *
     * If {@code shouldSucceed} is {@code true}, we only return device states that are available,
     * and if it is {@code false}, we only return non available device states.
     *
     * @param availableStatesToRequest The states that are available to be requested from an app
     * @param shouldSucceed Should the request succeed or not, to determine what states we return
     * @param supportedDeviceStates All states supported on the device.
     * {@throws} an {@link IllegalArgumentException} if {@code availableStatesToRequest} includes
     * non-valid device states.
     */
    private static Set<Integer> possibleStates(boolean shouldSucceed,
            Set<Integer> supportedDeviceStates,
            Set<Integer> availableStatesToRequest) {

        if (!supportedDeviceStates.containsAll(availableStatesToRequest)) {
            throw new IllegalArgumentException("Available states include invalid device states");
        }

        Set<Integer> availableStates = new HashSet<>(supportedDeviceStates);

        if (shouldSucceed) {
            availableStates.retainAll(availableStatesToRequest);
        } else {
            availableStates.removeAll(availableStatesToRequest);
        }

        return availableStates;
    }

    /**
     * Determines what state we should request that isn't the current state, and is included
     * in {@code possibleStates}. If there is no state that fits these requirements, we return
     * {@link INVALID_DEVICE_STATE}.
     *
     * @param currentState The current state of the device
     * @param possibleStates States that we can request
     */
    private static int calculateDifferentState(int currentState, Set<Integer> possibleStates) {
        if (possibleStates.isEmpty()) {
            return INVALID_DEVICE_STATE;
        }
        if (possibleStates.size() == 1 && possibleStates.contains(currentState)) {
            return INVALID_DEVICE_STATE;
        }
        for (int state: possibleStates) {
            if (state != currentState) {
                return state;
            }
        }
        return INVALID_DEVICE_STATE;
    }

    /**
     * Creates a {@link Set} of values that are in the {@code states} array.
     *
     * Used to create a {@link Set} from the available device states that {@link DeviceStateManager}
     * returns as an array.
     *
     * @param states Device states that are supported on the device
     */
    private static Set<Integer> generateDeviceStateSet(int[] states) {
        Set<Integer> supportedStates = new HashSet<>();
        for (int i = 0; i < states.length; i++) {
            supportedStates.add(states[i]);
        }
        return supportedStates;
    }

    /**
     * Tests that calling {@link DeviceStateManager#requestState()} throws a
     * {@link java.lang.SecurityException} without the
     * {@link android.Manifest.permission.CONTROL_DEVICE_STATE} permission held.
     */
    @Test(expected = SecurityException.class)
    public void testRequestStateWithoutPermission() {
        final DeviceStateManager manager = getDeviceStateManager();
        final int[] states = manager.getSupportedStates();
        final DeviceStateRequest request = DeviceStateRequest.newBuilder(states[0]).build();
        manager.requestState(request, null, null);
    }

    /**
     * Tests that calling {@link DeviceStateManager#cancelStateRequest} throws a
     * {@link java.lang.SecurityException} without the
     * {@link android.Manifest.permission.CONTROL_DEVICE_STATE} permission held.
     */
    @Test(expected = SecurityException.class)
    public void testCancelOverrideRequestWithoutPermission() throws Throwable {
        final DeviceStateManager manager = getDeviceStateManager();
        final int[] states = manager.getSupportedStates();
        final DeviceStateRequest request = DeviceStateRequest.newBuilder(states[0]).build();
        runWithRequestActive(request, false, manager::cancelStateRequest);
    }

    /**
     * Tests that callbacks added with {@link DeviceStateManager#registerDeviceStateCallback()} are
     * supplied with an initial callback that contains the state at the time of registration.
     */
    @Test
    public void testRegisterCallbackSuppliesInitialValue() throws InterruptedException {
        final ArgumentCaptor<int[]> intArrayAgumentCaptor = ArgumentCaptor.forClass(int[].class);
        final ArgumentCaptor<Integer> intAgumentCaptor = ArgumentCaptor.forClass(Integer.class);

        final DeviceStateManager.DeviceStateCallback callback
                = mock(DeviceStateManager.DeviceStateCallback.class);
        final DeviceStateManager manager = getDeviceStateManager();
        manager.registerCallback(Runnable::run, callback);

        verify(callback, timeout(CALLBACK_TIMEOUT_MS)).onStateChanged(intAgumentCaptor.capture());
        assertValidState(intAgumentCaptor.getValue().intValue());

        verify(callback, timeout(CALLBACK_TIMEOUT_MS))
                .onBaseStateChanged(intAgumentCaptor.capture());
        assertValidState(intAgumentCaptor.getValue().intValue());

        verify(callback, timeout(CALLBACK_TIMEOUT_MS))
                .onSupportedStatesChanged(intArrayAgumentCaptor.capture());
        final int[] supportedStates = intArrayAgumentCaptor.getValue();
        assertTrue(supportedStates.length > 0);
        for (int i = 0; i < supportedStates.length; i++) {
            final int state = supportedStates[i];
            assertValidState(state);
        }
    }

    /** For API changes that are introduced together with WM Extensions version 2. */
    private static void assumeExtensionVersionAtLeast2() {
        // TODO(b/232476698) Remove in the next Android release.
        assumeTrue(WM_EXTENSION_VERSION.getMajor() >= 2);
    }

    private class StateTrackingCallback implements  DeviceStateManager.DeviceStateCallback {
        private int mCurrentState = - 1;

        @Override
        public void onStateChanged(int state) {
            mCurrentState = state;
        }
    }
}
