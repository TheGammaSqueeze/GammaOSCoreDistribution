/*
 * Copyright 2021 The Android Open Source Project
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

package com.android.server.nearby.common.bluetooth.fastpair;

import static android.bluetooth.BluetoothDevice.BOND_BONDED;
import static android.bluetooth.BluetoothDevice.BOND_BONDING;
import static android.bluetooth.BluetoothDevice.BOND_NONE;
import static android.bluetooth.BluetoothDevice.ERROR;
import static android.bluetooth.BluetoothProfile.A2DP;
import static android.bluetooth.BluetoothProfile.HEADSET;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;

import static com.android.server.nearby.common.bluetooth.fastpair.BluetoothAddress.maskBluetoothAddress;

import static java.util.concurrent.Executors.newSingleThreadExecutor;

import android.Manifest.permission;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Parcelable;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;
import androidx.core.content.ContextCompat;

import com.android.server.nearby.common.bluetooth.BluetoothException;
import com.android.server.nearby.common.bluetooth.fastpair.Constants.FastPairService;
import com.android.server.nearby.common.bluetooth.fastpair.Constants.FastPairService.PasskeyCharacteristic;
import com.android.server.nearby.common.bluetooth.fastpair.Constants.Profile;
import com.android.server.nearby.common.bluetooth.fastpair.TimingLogger.ScopedTiming;
import com.android.server.nearby.common.bluetooth.gatt.BluetoothGattConnection;
import com.android.server.nearby.common.bluetooth.gatt.BluetoothGattConnection.ChangeObserver;
import com.android.server.nearby.intdefs.FastPairEventIntDefs.ConnectErrorCode;
import com.android.server.nearby.intdefs.FastPairEventIntDefs.CreateBondErrorCode;
import com.android.server.nearby.intdefs.NearbyEventIntDefs.EventCode;

import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.SettableFuture;

import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Pairs to Bluetooth audio devices.
 */
public class BluetoothAudioPairer {

    private static final String TAG = BluetoothAudioPairer.class.getSimpleName();

    /**
     * Hidden, see {@link BluetoothDevice}.
     */
    // TODO(b/202549655): remove Hidden usage.
    private static final String EXTRA_REASON = "android.bluetooth.device.extra.REASON";

    /**
     * Hidden, see {@link BluetoothDevice}.
     */
    // TODO(b/202549655): remove Hidden usage.
    private static final int PAIRING_VARIANT_CONSENT = 3;

    /**
     * Hidden, see {@link BluetoothDevice}.
     */
    // TODO(b/202549655): remove Hidden usage.
    public static final int PAIRING_VARIANT_DISPLAY_PASSKEY = 4;

    private static final int DISCOVERY_STATE_CHANGE_TIMEOUT_MS = 3000;

    private final Context mContext;
    private final Preferences mPreferences;
    private final EventLoggerWrapper mEventLogger;
    private final BluetoothDevice mDevice;
    @Nullable
    private final KeyBasedPairingInfo mKeyBasedPairingInfo;
    @Nullable
    private final PasskeyConfirmationHandler mPasskeyConfirmationHandler;
    private final TimingLogger mTimingLogger;

    private static boolean sTestMode = false;

    static void enableTestMode() {
        sTestMode = true;
    }

    static class KeyBasedPairingInfo {

        private final byte[] mSecret;
        private final GattConnectionManager mGattConnectionManager;
        private final boolean mProviderInitiatesBonding;

        /**
         * @param secret The secret negotiated during the initial BLE handshake for Key-based
         * Pairing. See {@link FastPairConnection#handshake}.
         * @param gattConnectionManager A manager that knows how to get and create Gatt connections
         * to the remote device.
         */
        KeyBasedPairingInfo(
                byte[] secret,
                GattConnectionManager gattConnectionManager,
                boolean providerInitiatesBonding) {
            this.mSecret = secret;
            this.mGattConnectionManager = gattConnectionManager;
            this.mProviderInitiatesBonding = providerInitiatesBonding;
        }
    }

    public BluetoothAudioPairer(
            Context context,
            BluetoothDevice device,
            Preferences preferences,
            EventLoggerWrapper eventLogger,
            @Nullable KeyBasedPairingInfo keyBasedPairingInfo,
            @Nullable PasskeyConfirmationHandler passkeyConfirmationHandler,
            TimingLogger timingLogger)
            throws PairingException {
        this.mContext = context;
        this.mDevice = device;
        this.mPreferences = preferences;
        this.mEventLogger = eventLogger;
        this.mKeyBasedPairingInfo = keyBasedPairingInfo;
        this.mPasskeyConfirmationHandler = passkeyConfirmationHandler;
        this.mTimingLogger = timingLogger;

        // TODO(b/203455314): follow up with the following comments.
        // The OS should give the user some UI to choose if they want to allow access, but there
        // seems to be a bug where if we don't reject access, it's auto-granted in some cases
        // (Plantronics headset gets contacts access when pairing with my Taimen via Bluetooth
        // Settings, without me seeing any UI about it). b/64066631
        //
        // If that OS bug doesn't get fixed, we can flip these flags to force-reject the
        // permissions.
        if (preferences.getRejectPhonebookAccess() && (sTestMode ? false :
                !device.setPhonebookAccessPermission(BluetoothDevice.ACCESS_REJECTED))) {
            throw new PairingException("Failed to deny contacts (phonebook) access.");
        }
        if (preferences.getRejectMessageAccess()
                && (sTestMode ? false :
                !device.setMessageAccessPermission(BluetoothDevice.ACCESS_REJECTED))) {
            throw new PairingException("Failed to deny message access.");
        }
        if (preferences.getRejectSimAccess()
                && (sTestMode ? false :
                !device.setSimAccessPermission(BluetoothDevice.ACCESS_REJECTED))) {
            throw new PairingException("Failed to deny SIM access.");
        }
    }

    boolean isPaired() {
        return (sTestMode ? false : mDevice.getBondState() == BOND_BONDED);
    }

    /**
     * Unpairs from the device. Throws an exception if any error occurs.
     */
    @WorkerThread
    void unpair()
            throws InterruptedException, ExecutionException, TimeoutException, PairingException {
        int bondState =  sTestMode ? BOND_NONE : mDevice.getBondState();
        try (UnbondedReceiver unbondedReceiver = new UnbondedReceiver();
                ScopedTiming scopedTiming = new ScopedTiming(mTimingLogger,
                        "Unpair for state: " + bondState)) {
            // We'll only get a state change broadcast if we're actually unbonding (method returns
            // true).
            if (bondState == BluetoothDevice.BOND_BONDED) {
                mEventLogger.setCurrentEvent(EventCode.REMOVE_BOND);
                Log.i(TAG,  "removeBond with " + maskBluetoothAddress(mDevice));
                mDevice.removeBond();
                unbondedReceiver.await(
                        mPreferences.getRemoveBondTimeoutSeconds(), TimeUnit.SECONDS);
            } else if (bondState == BluetoothDevice.BOND_BONDING) {
                mEventLogger.setCurrentEvent(EventCode.CANCEL_BOND);
                Log.i(TAG,  "cancelBondProcess with " + maskBluetoothAddress(mDevice));
                mDevice.cancelBondProcess();
                unbondedReceiver.await(
                        mPreferences.getRemoveBondTimeoutSeconds(), TimeUnit.SECONDS);
            } else {
                // The OS may have beaten us in a race, unbonding before we called the method. So if
                // we're (somehow) in the desired state then we're happy, if not then bail.
                if (bondState != BluetoothDevice.BOND_NONE) {
                    throw new PairingException("returned false, state=%s", bondState);
                }
            }
        }

        // This seems to improve the probability that createBond will succeed after removeBond.
        SystemClock.sleep(mPreferences.getRemoveBondSleepMillis());
        mEventLogger.logCurrentEventSucceeded();
    }

    /**
     * Pairs with the device. Throws an exception if any error occurs.
     */
    @WorkerThread
    void pair()
            throws InterruptedException, ExecutionException, TimeoutException, PairingException {
        // Unpair first, because if we have a bond, but the other device has forgotten its bond,
        // it can send us a pairing request that we're not ready for (which can pop up a dialog).
        // Or, if we're in the middle of a (too-long) bonding attempt, we want to cancel.
        unpair();

        mEventLogger.setCurrentEvent(EventCode.CREATE_BOND);
        try (BondedReceiver bondedReceiver = new BondedReceiver();
                ScopedTiming scopedTiming = new ScopedTiming(mTimingLogger, "Create bond")) {
            // If the provider's initiating the bond, we do nothing but wait for broadcasts.
            if (mKeyBasedPairingInfo == null || !mKeyBasedPairingInfo.mProviderInitiatesBonding) {
                if (!sTestMode) {
                    Log.i(TAG, "createBond with " + maskBluetoothAddress(mDevice) + ", type="
                        + mDevice.getType());
                    if (mPreferences.getSpecifyCreateBondTransportType()) {
                        mDevice.createBond(mPreferences.getCreateBondTransportType());
                    } else {
                        mDevice.createBond();
                    }
                }
            }
            try {
                bondedReceiver.await(mPreferences.getCreateBondTimeoutSeconds(), TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                Log.w(TAG, "bondedReceiver time out after " + mPreferences
                        .getCreateBondTimeoutSeconds() + " seconds");
                if (mPreferences.getIgnoreUuidTimeoutAfterBonded() && isPaired()) {
                    Log.w(TAG, "Created bond but never received UUIDs, attempting to continue.");
                } else {
                    // Rethrow e to cause the pairing to fail and be retried if necessary.
                    throw e;
                }
            }
        }
        mEventLogger.logCurrentEventSucceeded();
    }

    /**
     * Connects to the given profile. Throws an exception if any error occurs.
     *
     * <p>If remote device clears the link key, the BOND_BONDED state would transit to BOND_BONDING
     * (and go through the pairing process again) when directly connecting the profile. By enabling
     * enablePairingBehavior, we provide both pairing and connecting behaviors at the same time. See
     * b/145699390 for more details.
     */
    // Suppression for possible null from ImmutableMap#get. See go/lsc-get-nullable
    @SuppressWarnings("nullness:argument")
    @WorkerThread
    public void connect(short profileUuid, boolean enablePairingBehavior)
            throws InterruptedException, ReflectionException, TimeoutException, ExecutionException,
            ConnectException {
        if (!mPreferences.isSupportedProfile(profileUuid)) {
            throw new ConnectException(
                    ConnectErrorCode.UNSUPPORTED_PROFILE, "Unsupported profile=%s", profileUuid);
        }
        Profile profile = Constants.PROFILES.get(profileUuid);
        Log.i(TAG,
                "Connecting to profile=" + profile + " on device=" + maskBluetoothAddress(mDevice));
        try (BondedReceiver bondedReceiver = enablePairingBehavior ? new BondedReceiver() : null;
                ScopedTiming scopedTiming = new ScopedTiming(mTimingLogger,
                        "Connect: " + profile)) {
            connectByProfileProxy(profile);
        }
    }

    private void connectByProfileProxy(Profile profile)
            throws ReflectionException, InterruptedException, ExecutionException, TimeoutException,
            ConnectException {
        try (BluetoothProfileWrapper autoClosingProxy = new BluetoothProfileWrapper(profile);
                ConnectedReceiver connectedReceiver = new ConnectedReceiver(profile)) {
            BluetoothProfile proxy = autoClosingProxy.mProxy;

            // Try to connect via reflection
            Log.v(TAG, "Connect to proxy=" + proxy);

            if (!sTestMode) {
                if (!(Boolean) Reflect.on(proxy).withMethod("connect", BluetoothDevice.class)
                        .get(mDevice)) {
                    // If we're already connecting, connect() may return false. :/
                    Log.w(TAG, "connect returned false, expected if connecting, state="
                            + proxy.getConnectionState(mDevice));
                }
            }

            // If we're already connected, the OS may not send the connection state broadcast, so
            // return immediately for that case.
            if (!sTestMode) {
                if (proxy.getConnectionState(mDevice) == BluetoothProfile.STATE_CONNECTED) {
                    Log.v(TAG, "connectByProfileProxy: already connected to device="
                            + maskBluetoothAddress(mDevice));
                    return;
                }
            }

            try (ScopedTiming scopedTiming = new ScopedTiming(mTimingLogger, "Wait connection")) {
                // Wait for connecting to succeed or fail (via event or timeout).
                connectedReceiver
                        .await(mPreferences.getCreateBondTimeoutSeconds(), TimeUnit.SECONDS);
            }
        }
    }

    private class BluetoothProfileWrapper implements AutoCloseable {

        // incompatible types in assignment.
        @SuppressWarnings("nullness:assignment")
        private final BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        private final Profile mProfile;
        private final BluetoothProfile mProxy;

        /**
         * Blocks until we get the proxy. Throws on error.
         */
        private BluetoothProfileWrapper(Profile profile)
                throws InterruptedException, ExecutionException, TimeoutException,
                ConnectException {
            this.mProfile = profile;
            mProxy = getProfileProxy(profile);
        }

        @Override
        public void close() {
            try (ScopedTiming scopedTiming =
                    new ScopedTiming(mTimingLogger, "Close profile: " + mProfile)) {
                if (!sTestMode) {
                    mBluetoothAdapter.closeProfileProxy(mProfile.type, mProxy);
                }
            }
        }

        private BluetoothProfile getProfileProxy(BluetoothProfileWrapper this, Profile profile)
                throws InterruptedException, ExecutionException, TimeoutException,
                ConnectException {
            if (profile.type != A2DP && profile.type != HEADSET) {
                throw new IllegalArgumentException("Unsupported profile type=" + profile.type);
            }

            SettableFuture<BluetoothProfile> proxyFuture = SettableFuture.create();
            BluetoothProfile.ServiceListener listener =
                    new BluetoothProfile.ServiceListener() {
                        @UiThread
                        @Override
                        public void onServiceConnected(int profileType, BluetoothProfile proxy) {
                            proxyFuture.set(proxy);
                        }

                        @Override
                        public void onServiceDisconnected(int profileType) {
                            Log.v(TAG, "proxy disconnected for profile=" + profile);
                        }
                    };

            if (!mBluetoothAdapter.getProfileProxy(mContext, listener, profile.type)) {
                throw new ConnectException(
                        ConnectErrorCode.GET_PROFILE_PROXY_FAILED,
                        "getProfileProxy failed immediately");
            }

            return proxyFuture.get(mPreferences.getProxyTimeoutSeconds(), TimeUnit.SECONDS);
        }
    }

    private class UnbondedReceiver extends DeviceIntentReceiver {

        private UnbondedReceiver() {
            super(mContext, mPreferences, mDevice, BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        }

        @Override
        protected void onReceiveDeviceIntent(Intent intent) throws Exception {
            if (mDevice.getBondState() == BOND_NONE) {
                try (ScopedTiming scopedTiming = new ScopedTiming(mTimingLogger,
                        "Close UnbondedReceiver")) {
                    close();
                }
            }
        }
    }

    /**
     * Receiver that closes after bonding has completed.
     */
    class BondedReceiver extends DeviceIntentReceiver {

        private boolean mReceivedUuids = false;
        private boolean mReceivedPasskey = false;

        private BondedReceiver() {
            super(
                    mContext,
                    mPreferences,
                    mDevice,
                    BluetoothDevice.ACTION_PAIRING_REQUEST,
                    BluetoothDevice.ACTION_BOND_STATE_CHANGED,
                    BluetoothDevice.ACTION_UUID);
        }

        // switching on a possibly-null value (intent.getAction())
        // incompatible types in argument.
        @SuppressWarnings({"nullness:switching.nullable", "nullness:argument"})
        @Override
        protected void onReceiveDeviceIntent(Intent intent)
                throws PairingException, InterruptedException, ExecutionException, TimeoutException,
                BluetoothException, GeneralSecurityException {
            switch (intent.getAction()) {
                case BluetoothDevice.ACTION_PAIRING_REQUEST:
                    int variant = intent.getIntExtra(BluetoothDevice.EXTRA_PAIRING_VARIANT, ERROR);
                    int passkey = intent.getIntExtra(BluetoothDevice.EXTRA_PAIRING_KEY, ERROR);
                    handlePairingRequest(variant, passkey);
                    break;
                case BluetoothDevice.ACTION_BOND_STATE_CHANGED:
                    // Use the state in the intent, not device.getBondState(), to avoid a race where
                    // we log the wrong failure reason during a rapid transition.
                    int bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, ERROR);
                    int reason = intent.getIntExtra(EXTRA_REASON, ERROR);
                    handleBondStateChanged(bondState, reason);
                    break;
                case BluetoothDevice.ACTION_UUID:
                    // According to eisenbach@ and pavlin@, there's always a UUID broadcast when
                    // pairing (it can happen either before or after the transition to BONDED).
                    if (mPreferences.getWaitForUuidsAfterBonding()) {
                        Parcelable[] uuids = intent
                                .getParcelableArrayExtra(BluetoothDevice.EXTRA_UUID);
                        handleUuids(uuids);
                    }
                    break;
                default:
                    break;
            }
        }

        private void handlePairingRequest(int variant, int passkey) {
            Log.i(TAG, "Pairing request, variant=" + variant + ", passkey=" + (passkey == ERROR
                    ? "(none)" : String.valueOf(passkey)));
            if (mPreferences.getMoreEventLogForQuality()) {
                mEventLogger.setCurrentEvent(EventCode.HANDLE_PAIRING_REQUEST);
            }

            if (mPreferences.getSupportHidDevice() && variant == PAIRING_VARIANT_DISPLAY_PASSKEY) {
                mReceivedPasskey = true;
                extendAwaitSecond(
                        mPreferences.getHidCreateBondTimeoutSeconds()
                                - mPreferences.getCreateBondTimeoutSeconds());
                triggerDiscoverStateChange();
                if (mPreferences.getMoreEventLogForQuality()) {
                    mEventLogger.logCurrentEventSucceeded();
                }
                return;

            } else {
                // Prevent Bluetooth Settings from getting the pairing request and showing its own
                // UI.
                abortBroadcast();

                if (variant == PAIRING_VARIANT_CONSENT
                        && mKeyBasedPairingInfo == null // Fast Pair 1.0 device
                        && mPreferences.getAcceptConsentForFastPairOne()) {
                    // Previously, if Bluetooth decided to use the Just Works variant (e.g. Fast
                    // Pair 1.0), we don't get a pairing request broadcast at all.
                    // However, after CVE-2019-2225, Bluetooth will decide to ask consent from
                    // users. Details:
                    // https://source.android.com/security/bulletin/2019-12-01#system
                    // Since we've certified the Fast Pair 1.0 devices, and user taps to pair it
                    // (with the device's image), we could help user to accept the consent.
                    if (!sTestMode) {
                        mDevice.setPairingConfirmation(true);
                    }
                    if (mPreferences.getMoreEventLogForQuality()) {
                        mEventLogger.logCurrentEventSucceeded();
                    }
                    return;
                } else if (variant != BluetoothDevice.PAIRING_VARIANT_PASSKEY_CONFIRMATION) {
                    if (!sTestMode) {
                        mDevice.setPairingConfirmation(false);
                    }
                    if (mPreferences.getMoreEventLogForQuality()) {
                        mEventLogger.logCurrentEventFailed(
                                new CreateBondException(
                                        CreateBondErrorCode.INCORRECT_VARIANT, 0,
                                        "Incorrect variant for FastPair"));
                    }
                    return;
                }
                mReceivedPasskey = true;

                if (mKeyBasedPairingInfo == null) {
                    if (mPreferences.getAcceptPasskey()) {
                        // Must be the simulator using FP 1.0 (no Key-based Pairing). Real
                        // headphones using FP 1.0 use Just Works instead (and maybe we should
                        // disable this flag for them).
                        if (!sTestMode) {
                            mDevice.setPairingConfirmation(true);
                        }
                    }
                    if (mPreferences.getMoreEventLogForQuality()) {
                        if (!sTestMode) {
                            mEventLogger.logCurrentEventSucceeded();
                        }
                    }
                    return;
                }
            }

            if (mPreferences.getMoreEventLogForQuality()) {
                mEventLogger.logCurrentEventSucceeded();
            }

            newSingleThreadExecutor()
                    .execute(
                            () -> {
                                try (ScopedTiming scopedTiming1 =
                                        new ScopedTiming(mTimingLogger, "Exchange passkey")) {
                                    mEventLogger.setCurrentEvent(EventCode.PASSKEY_EXCHANGE);

                                    // We already check above, but the static analyzer's not
                                    // convinced without this.
                                    Preconditions.checkNotNull(mKeyBasedPairingInfo);
                                    BluetoothGattConnection connection =
                                            mKeyBasedPairingInfo.mGattConnectionManager
                                                    .getConnection();
                                    UUID characteristicUuid =
                                            PasskeyCharacteristic.getId(connection);
                                    ChangeObserver remotePasskeyObserver =
                                            connection.enableNotification(FastPairService.ID,
                                                    characteristicUuid);
                                    Log.i(TAG, "Sending local passkey.");
                                    byte[] encryptedData;
                                    try (ScopedTiming scopedTiming2 =
                                            new ScopedTiming(mTimingLogger, "Encrypt passkey")) {
                                        encryptedData =
                                                PasskeyCharacteristic.encrypt(
                                                        PasskeyCharacteristic.Type.SEEKER,
                                                        mKeyBasedPairingInfo.mSecret, passkey);
                                    }
                                    try (ScopedTiming scopedTiming3 =
                                            new ScopedTiming(mTimingLogger,
                                                    "Send passkey to remote")) {
                                        connection.writeCharacteristic(
                                                FastPairService.ID, characteristicUuid,
                                                encryptedData);
                                    }
                                    Log.i(TAG, "Waiting for remote passkey.");
                                    byte[] encryptedRemotePasskey;
                                    try (ScopedTiming scopedTiming4 =
                                            new ScopedTiming(mTimingLogger,
                                                    "Wait for remote passkey")) {
                                        encryptedRemotePasskey =
                                                remotePasskeyObserver.waitForUpdate(
                                                        TimeUnit.SECONDS.toMillis(mPreferences
                                                                .getGattOperationTimeoutSeconds()));
                                    }
                                    int remotePasskey;
                                    try (ScopedTiming scopedTiming5 =
                                            new ScopedTiming(mTimingLogger, "Decrypt passkey")) {
                                        remotePasskey =
                                                PasskeyCharacteristic.decrypt(
                                                        PasskeyCharacteristic.Type.PROVIDER,
                                                        mKeyBasedPairingInfo.mSecret,
                                                        encryptedRemotePasskey);
                                    }

                                    // We log success if we made it through with no exceptions.
                                    // If the passkey was wrong, pairing will fail and we'll log
                                    // BOND_BROKEN with reason = AUTH_FAILED.
                                    mEventLogger.logCurrentEventSucceeded();

                                    boolean isPasskeyCorrect = passkey == remotePasskey;
                                    if (isPasskeyCorrect) {
                                        Log.i(TAG, "Passkey correct.");
                                    } else {
                                        Log.e(TAG, "Passkey incorrect, local= " + passkey
                                                + ", remote=" + remotePasskey);
                                    }

                                    // Don't estimate the {@code ScopedTiming} because the
                                    // passkey confirmation is done by UI.
                                    if (isPasskeyCorrect
                                            && mPreferences.getHandlePasskeyConfirmationByUi()
                                            && mPasskeyConfirmationHandler != null) {
                                        Log.i(TAG, "Callback the passkey to UI for confirmation.");
                                        mPasskeyConfirmationHandler
                                                .onPasskeyConfirmation(mDevice, passkey);
                                    } else {
                                        try (ScopedTiming scopedTiming6 =
                                                new ScopedTiming(
                                                        mTimingLogger, "Confirm the pairing: "
                                                        + isPasskeyCorrect)) {
                                            mDevice.setPairingConfirmation(isPasskeyCorrect);
                                        }
                                    }
                                } catch (BluetoothException
                                        | GeneralSecurityException
                                        | InterruptedException
                                        | ExecutionException
                                        | TimeoutException e) {
                                    mEventLogger.logCurrentEventFailed(e);
                                    closeWithError(e);
                                }
                            });
        }

        /**
         * Workaround to let Settings popup a pairing dialog instead of notification. When pairing
         * request intent passed to Settings, it'll check several conditions to decide that it
         * should show a dialog or a notification. One of those conditions is to check if the device
         * is in discovery mode recently, which can be fulfilled by calling {@link
         * BluetoothAdapter#startDiscovery()}. This method aims to fulfill the condition, and block
         * the pairing broadcast for at most
         * {@link BluetoothAudioPairer#DISCOVERY_STATE_CHANGE_TIMEOUT_MS}
         * to make sure that we fulfill the condition first and successful.
         */
        // dereference of possibly-null reference bluetoothAdapter
        @SuppressWarnings("nullness:dereference.of.nullable")
        private void triggerDiscoverStateChange() {
            BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

            if (bluetoothAdapter.isDiscovering()) {
                return;
            }

            HandlerThread backgroundThread = new HandlerThread("TriggerDiscoverStateChangeThread");
            backgroundThread.start();

            AtomicBoolean result = new AtomicBoolean(false);
            SimpleBroadcastReceiver receiver =
                    new SimpleBroadcastReceiver(
                            mContext,
                            mPreferences,
                            new Handler(backgroundThread.getLooper()),
                            BluetoothAdapter.ACTION_DISCOVERY_STARTED,
                            BluetoothAdapter.ACTION_DISCOVERY_FINISHED) {

                        @Override
                        protected void onReceive(Intent intent) throws Exception {
                            result.set(true);
                            close();
                        }
                    };

            Log.i(TAG, "triggerDiscoverStateChange call startDiscovery.");
            // Uses startDiscovery to trigger Settings show pairing dialog instead of notification.
            if (!sTestMode) {
                bluetoothAdapter.startDiscovery();
                bluetoothAdapter.cancelDiscovery();
            }
            try {
                receiver.await(DISCOVERY_STATE_CHANGE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            } catch (InterruptedException | ExecutionException | TimeoutException e) {
                Log.w(TAG, "triggerDiscoverStateChange failed!");
            }

            backgroundThread.quitSafely();
            try {
                backgroundThread.join();
            } catch (InterruptedException e) {
                Log.i(TAG, "triggerDiscoverStateChange backgroundThread.join meet exception!", e);
            }

            if (result.get()) {
                Log.i(TAG, "triggerDiscoverStateChange successful.");
            }
        }

        private void handleBondStateChanged(int bondState, int reason)
                throws PairingException, InterruptedException, ExecutionException,
                TimeoutException {
            Log.i(TAG, "Bond state changed to " + bondState + ", reason=" + reason);
            switch (bondState) {
                case BOND_BONDED:
                    if (mKeyBasedPairingInfo != null && !mReceivedPasskey) {
                        // The device bonded with Just Works, although we did the Key-based Pairing
                        // GATT handshake and agreed on a pairing secret. It might be a Person In
                        // The Middle Attack!
                        try (ScopedTiming scopedTiming =
                                new ScopedTiming(mTimingLogger,
                                        "Close BondedReceiver: POSSIBLE_MITM")) {
                            closeWithError(
                                    new CreateBondException(
                                            CreateBondErrorCode.POSSIBLE_MITM,
                                            reason,
                                            "Unexpectedly bonded without a passkey. It might be a "
                                                    + "Person In The Middle Attack! Unbonding!"));
                        }
                        unpair();
                    } else if (!mPreferences.getWaitForUuidsAfterBonding()
                            || (mPreferences.getReceiveUuidsAndBondedEventBeforeClose()
                            && mReceivedUuids)) {
                        try (ScopedTiming scopedTiming =
                                new ScopedTiming(mTimingLogger, "Close BondedReceiver")) {
                            close();
                        }
                    }
                    break;
                case BOND_NONE:
                    throw new CreateBondException(
                            CreateBondErrorCode.BOND_BROKEN, reason, "Bond broken, reason=%d",
                            reason);
                case BOND_BONDING:
                default:
                    break;
            }
        }

        private void handleUuids(Parcelable[] uuids) {
            Log.i(TAG, "Got UUIDs for " + maskBluetoothAddress(mDevice) + ": "
                    + Arrays.toString(uuids));
            mReceivedUuids = true;
            if (!mPreferences.getReceiveUuidsAndBondedEventBeforeClose() || isPaired()) {
                try (ScopedTiming scopedTiming = new ScopedTiming(mTimingLogger,
                        "Close BondedReceiver")) {
                    close();
                }
            }
        }
    }

    private class ConnectedReceiver extends DeviceIntentReceiver {

        private ConnectedReceiver(Profile profile) throws ConnectException {
            super(mContext, mPreferences, mDevice, profile.connectionStateAction);
        }

        @Override
        public void onReceiveDeviceIntent(Intent intent) throws PairingException {
            int state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, ERROR);
            Log.i(TAG, "Connection state changed to " + state);
            switch (state) {
                case BluetoothAdapter.STATE_CONNECTED:
                    try (ScopedTiming scopedTiming =
                            new ScopedTiming(mTimingLogger, "Close ConnectedReceiver")) {
                        close();
                    }
                    break;
                case BluetoothAdapter.STATE_DISCONNECTED:
                    throw new ConnectException(ConnectErrorCode.DISCONNECTED, "Disconnected");
                case BluetoothAdapter.STATE_CONNECTING:
                case BluetoothAdapter.STATE_DISCONNECTING:
                default:
                    break;
            }
        }
    }

    private boolean hasPermission(String permission) {
        return ContextCompat.checkSelfPermission(mContext, permission) == PERMISSION_GRANTED;
    }

    public BluetoothDevice getDevice() {
        return mDevice;
    }
}
