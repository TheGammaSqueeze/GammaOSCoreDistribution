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

import static com.android.server.nearby.common.bluetooth.fastpair.BluetoothAddress.maskBluetoothAddress;
import static com.android.server.nearby.common.bluetooth.fastpair.BluetoothUuids.get16BitUuid;
import static com.android.server.nearby.common.bluetooth.fastpair.BluetoothUuids.to128BitUuid;
import static com.android.server.nearby.common.bluetooth.fastpair.Bytes.toBytes;
import static com.android.server.nearby.common.bluetooth.fastpair.Bytes.toShorts;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Verify.verifyNotNull;
import static com.google.common.io.BaseEncoding.base16;
import static com.google.common.primitives.Bytes.concat;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.ParcelUuid;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.GuardedBy;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import com.android.server.nearby.common.bluetooth.BluetoothException;
import com.android.server.nearby.common.bluetooth.BluetoothGattException;
import com.android.server.nearby.common.bluetooth.BluetoothTimeoutException;
import com.android.server.nearby.common.bluetooth.fastpair.BluetoothAudioPairer.KeyBasedPairingInfo;
import com.android.server.nearby.common.bluetooth.fastpair.Constants.FastPairService;
import com.android.server.nearby.common.bluetooth.fastpair.Constants.FastPairService.AccountKeyCharacteristic;
import com.android.server.nearby.common.bluetooth.fastpair.Constants.FastPairService.AdditionalDataCharacteristic.AdditionalDataType;
import com.android.server.nearby.common.bluetooth.fastpair.Constants.FastPairService.FirmwareVersionCharacteristic;
import com.android.server.nearby.common.bluetooth.fastpair.Constants.FastPairService.KeyBasedPairingCharacteristic.KeyBasedPairingRequestFlag;
import com.android.server.nearby.common.bluetooth.fastpair.Constants.FastPairService.NameCharacteristic;
import com.android.server.nearby.common.bluetooth.fastpair.Constants.TransportDiscoveryService;
import com.android.server.nearby.common.bluetooth.fastpair.HandshakeHandler.ActionOverBle;
import com.android.server.nearby.common.bluetooth.fastpair.HandshakeHandler.HandshakeException;
import com.android.server.nearby.common.bluetooth.fastpair.HandshakeHandler.HandshakeMessage;
import com.android.server.nearby.common.bluetooth.fastpair.HandshakeHandler.KeyBasedPairingRequest;
import com.android.server.nearby.common.bluetooth.fastpair.Ltv.ParseException;
import com.android.server.nearby.common.bluetooth.fastpair.TimingLogger.ScopedTiming;
import com.android.server.nearby.common.bluetooth.gatt.BluetoothGattConnection;
import com.android.server.nearby.common.bluetooth.gatt.BluetoothGattConnection.ChangeObserver;
import com.android.server.nearby.common.bluetooth.testability.android.bluetooth.BluetoothAdapter;
import com.android.server.nearby.common.bluetooth.util.BluetoothOperationExecutor.BluetoothOperationTimeoutException;
import com.android.server.nearby.common.locator.Locator;
import com.android.server.nearby.fastpair.FastPairController;
import com.android.server.nearby.intdefs.FastPairEventIntDefs.BrEdrHandoverErrorCode;
import com.android.server.nearby.intdefs.FastPairEventIntDefs.ConnectErrorCode;
import com.android.server.nearby.intdefs.FastPairEventIntDefs.CreateBondErrorCode;
import com.android.server.nearby.intdefs.FastPairEventIntDefs.ErrorCode;
import com.android.server.nearby.intdefs.NearbyEventIntDefs.EventCode;

import com.google.common.base.Ascii;
import com.google.common.base.Preconditions;
import com.google.common.primitives.Shorts;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.nio.ByteOrder;
import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Supports Fast Pair pairing with certain Bluetooth headphones, Auto, etc.
 *
 * <p>Based on https://developers.google.com/nearby/fast-pair/spec, the pairing is constructed by
 * both BLE and BREDR connections. Example state transitions for Fast Pair 2, ie a pairing key is
 * included in the request (note: timeouts and retries are governed by flags, may change):
 *
 * <pre>
 * {@code
 *   Connect GATT
 *     A) Success -> Handshake
 *     B) Failure (3s timeout) -> Retry 2x -> end
 *
 *   Handshake
 *     A) Generate a shared secret with the headset (either using anti-spoofing key or account key)
 *       1) Account key is used directly as the key
 *       2) Anti-spoofing key is used by combining out private key with the headset's public and
 *          sending our public to the headset to combine with their private to generate a shared
 *          key. Sending our public key to headset takes ~3s.
 *     B) Write an encrypted packet to the headset containing their BLE address for verification
 *        that both sides have the same key (headset decodes this packet and checks it against their
 *        own address) (~250ms).
 *     C) Receive a response from the headset containing their public address (~250ms).
 *
 *   Discovery (for devices < Oreo)
 *     A) Success -> Create Bond
 *     B) Failure (10s timeout) -> Sleep 1s, Retry 3x -> end
 *
 *   Connect to device
 *     A) If already bonded
 *       1) Attempt directly connecting to supported profiles (A2DP, etc)
 *         a) Success -> Write Account Key
 *         b) Failure (15s timeout, usually fails within a ~2s) -> Remove bond (~1s) -> Create bond
 *     B) If not already bonded
 *       1) Create bond
 *         a) Success -> Connect profile
 *         b) Failure (15s timeout) -> Retry 2x -> end
 *       2) Connect profile
 *         a) Success -> Write account key
 *         b) Failure -> Retry -> end
 *
 *   Write account key
 *     A) Callback that pairing succeeded
 *     B) Disconnect GATT
 *     C) Reconnect GATT for secure connection
 *     D) Write account key (~3s)
 * }
 * </pre>
 *
 * The performance profiling result by {@link TimingLogger}:
 *
 * <pre>
 *   FastPairDualConnection [Exclusive time] / [Total time] ([Timestamp])
 *     Connect GATT #1 3054ms (0)
 *     Handshake 32ms / 740ms (3054)
 *       Generate key via ECDH 10ms (3054)
 *       Add salt 1ms (3067)
 *       Encrypt request 3ms (3068)
 *       Write data to GATT 692ms (3097)
 *       Wait response from GATT 0ms (3789)
 *       Decrypt response 2ms (3789)
 *     Get BR/EDR handover information via SDP 1ms (3795)
 *     Pair device #1 6ms / 4887ms (3805)
 *       Create bond 3965ms / 4881ms (3809)
 *         Exchange passkey 587ms / 915ms (7124)
 *           Encrypt passkey 6ms (7694)
 *           Send passkey to remote 290ms (7700)
 *           Wait for remote passkey 0ms (7993)
 *           Decrypt passkey 18ms (7994)
 *           Confirm the pairing: true 14ms (8025)
 *         Close BondedReceiver 1ms (8688)
 *     Connect: A2DP 19ms / 370ms (8701)
 *       Wait connection 348ms / 349ms (8720)
 *         Close ConnectedReceiver 1ms (9068)
 *       Close profile: A2DP 2ms (9069)
 *     Write account key 2ms / 789ms (9163)
 *       Encrypt key 0ms (9164)
 *       Write key via GATT #1 777ms / 783ms (9164)
 *         Close GATT 6ms (9941)
 *       Start CloudSyncing 2ms (9947)
 *       Broadcast Validator 2ms (9949)
 *   FastPairDualConnection end, 9952ms
 * </pre>
 */
// TODO(b/203441105): break down FastPairDualConnection into smaller classes.
public class FastPairDualConnection extends FastPairConnection {

    private static final String TAG = FastPairDualConnection.class.getSimpleName();

    @VisibleForTesting
    static final int GATT_ERROR_CODE_FAST_PAIR_SIGNAL_LOST = 10000;
    @VisibleForTesting
    static final int GATT_ERROR_CODE_FAST_PAIR_ADDRESS_ROTATED = 20000;
    @VisibleForTesting
    static final int GATT_ERROR_CODE_USER_RETRY = 30000;
    @VisibleForTesting
    static final int GATT_ERROR_CODE_PAIR_WITH_SAME_MODEL_ID_COUNT = 40000;
    @VisibleForTesting
    static final int GATT_ERROR_CODE_TIMEOUT = 1000;

    @Nullable
    private static String sInitialConnectionFirmwareVersion;
    private static final byte[] REQUESTED_SERVICES_LTV =
            new Ltv(
                    TransportDiscoveryService.SERVICE_UUIDS_16_BIT_LIST_TYPE,
                    toBytes(
                            ByteOrder.LITTLE_ENDIAN,
                            Constants.A2DP_SINK_SERVICE_UUID,
                            Constants.HANDS_FREE_SERVICE_UUID,
                            Constants.HEADSET_SERVICE_UUID))
                    .getBytes();
    private static final byte[] TDS_CONTROL_POINT_REQUEST =
            concat(
                    new byte[]{
                            TransportDiscoveryService.ControlPointCharacteristic
                                    .ACTIVATE_TRANSPORT_OP_CODE,
                            TransportDiscoveryService.BLUETOOTH_SIG_ORGANIZATION_ID
                    },
                    REQUESTED_SERVICES_LTV);

    private static boolean sTestMode = false;

    static void enableTestMode() {
        sTestMode = true;
    }

    /**
     * Operation Result Code.
     */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(
            value = {
                    ResultCode.UNKNOWN,
                    ResultCode.SUCCESS,
                    ResultCode.OP_CODE_NOT_SUPPORTED,
                    ResultCode.INVALID_PARAMETER,
                    ResultCode.UNSUPPORTED_ORGANIZATION_ID,
                    ResultCode.OPERATION_FAILED,
            })

    public @interface ResultCode {

        int UNKNOWN = (byte) 0xFF;
        int SUCCESS = (byte) 0x00;
        int OP_CODE_NOT_SUPPORTED = (byte) 0x01;
        int INVALID_PARAMETER = (byte) 0x02;
        int UNSUPPORTED_ORGANIZATION_ID = (byte) 0x03;
        int OPERATION_FAILED = (byte) 0x04;
    }


    private static @ResultCode int fromTdsControlPointIndication(byte[] response) {
        return response == null || response.length < 2 ? ResultCode.UNKNOWN : from(response[1]);
    }

    private static @ResultCode int from(byte byteValue) {
        switch (byteValue) {
            case ResultCode.UNKNOWN:
            case ResultCode.SUCCESS:
            case ResultCode.OP_CODE_NOT_SUPPORTED:
            case ResultCode.INVALID_PARAMETER:
            case ResultCode.UNSUPPORTED_ORGANIZATION_ID:
            case ResultCode.OPERATION_FAILED:
                return byteValue;
            default:
                return ResultCode.UNKNOWN;
        }
    }

    private static class BrEdrHandoverInformation {

        private final byte[] mBluetoothAddress;
        private final short[] mProfiles;

        private BrEdrHandoverInformation(byte[] bluetoothAddress, short[] profiles) {
            this.mBluetoothAddress = bluetoothAddress;

            // For now, since we only connect to one profile, prefer A2DP Sink over headset/HFP.
            // TODO(b/37167120): Connect to more than one profile.
            Set<Short> profileSet = new HashSet<>(Shorts.asList(profiles));
            if (profileSet.contains(Constants.A2DP_SINK_SERVICE_UUID)) {
                profileSet.remove(Constants.HEADSET_SERVICE_UUID);
                profileSet.remove(Constants.HANDS_FREE_SERVICE_UUID);
            }
            this.mProfiles = Shorts.toArray(profileSet);
        }

        @Override
        public String toString() {
            return "BrEdrHandoverInformation{"
                    + maskBluetoothAddress(BluetoothAddress.encode(mBluetoothAddress))
                    + ", profiles="
                    + (mProfiles.length > 0 ? Shorts.join(",", mProfiles) : "(none)")
                    + "}";
        }
    }

    private final Context mContext;
    private final Preferences mPreferences;
    private final EventLoggerWrapper mEventLogger;
    private final BluetoothAdapter mBluetoothAdapter =
            checkNotNull(BluetoothAdapter.getDefaultAdapter());
    private String mBleAddress;

    private final TimingLogger mTimingLogger;
    private GattConnectionManager mGattConnectionManager;
    private boolean mProviderInitiatesBonding;
    private @Nullable
    byte[] mPairingSecret;
    private @Nullable
    byte[] mPairingKey;
    @Nullable
    private String mPublicAddress;
    @VisibleForTesting
    @Nullable
    FastPairHistoryFinder mPairedHistoryFinder;
    @Nullable
    private String mProviderDeviceName = null;
    private boolean mNeedUpdateProviderName = false;
    @Nullable
    DeviceNameReceiver mDeviceNameReceiver;
    @Nullable
    private HandshakeHandler mHandshakeHandlerForTest;
    @Nullable
    private Runnable mBeforeDirectlyConnectProfileFromCacheForTest;

    public FastPairDualConnection(
            Context context,
            String bleAddress,
            Preferences preferences,
            @Nullable EventLogger eventLogger) {
        this(context, bleAddress, preferences, eventLogger,
                new TimingLogger("FastPairDualConnection", preferences));
    }

    @VisibleForTesting
    FastPairDualConnection(
            Context context,
            String bleAddress,
            Preferences preferences,
            @Nullable EventLogger eventLogger,
            TimingLogger timingLogger) {
        this.mContext = context;
        this.mPreferences = preferences;
        this.mEventLogger = new EventLoggerWrapper(eventLogger);
        this.mBleAddress = bleAddress;
        this.mTimingLogger = timingLogger;
    }

    /**
     * Unpairs with headphones. Synchronous: Blocks until unpaired. Throws on any error.
     */
    @WorkerThread
    public void unpair(BluetoothDevice device)
            throws ReflectionException, InterruptedException, ExecutionException, TimeoutException,
            PairingException {
        if (mPreferences.getExtraLoggingInformation() != null) {
            mEventLogger
                    .bind(mContext, device.getAddress(), mPreferences.getExtraLoggingInformation());
        }
        new BluetoothAudioPairer(
                mContext,
                device,
                mPreferences,
                mEventLogger,
                /* keyBasedPairingInfo= */ null,
                /* passkeyConfirmationHandler= */ null,
                mTimingLogger)
                .unpair();
        if (mEventLogger.isBound()) {
            mEventLogger.unbind(mContext);
        }
    }

    /**
     * Sets the fast pair history for identifying the provider which has paired (without being
     * forgotten) with the primary account on the device, i.e. the history is not limited on this
     * phone, can be on other phones with the same account. If they have already paired, Fast Pair
     * should not generate new account key and default personalized name for it after initial pair.
     */
    @WorkerThread
    public void setFastPairHistory(List<FastPairHistoryItem> fastPairHistoryItem) {
        Log.i(TAG, "Paired history has been set.");
        this.mPairedHistoryFinder = new FastPairHistoryFinder(fastPairHistoryItem);
    }

    /**
     * Update the provider device name when we take provider default name and account based name
     * into consideration.
     */
    public void setProviderDeviceName(String deviceName) {
        Log.i(TAG, "Update provider device name = " + deviceName);
        mProviderDeviceName = deviceName;
        mNeedUpdateProviderName = true;
    }

    /**
     * Gets the device name from the Provider (via GATT notify).
     */
    @Nullable
    public String getProviderDeviceName() {
        if (mDeviceNameReceiver == null) {
            Log.i(TAG, "getProviderDeviceName failed, deviceNameReceiver == null.");
            return null;
        }
        if (mPairingSecret == null) {
            Log.i(TAG, "getProviderDeviceName failed, pairingSecret == null.");
            return null;
        }
        String deviceName = mDeviceNameReceiver.getParsedResult(mPairingSecret);
        Log.i(TAG, "getProviderDeviceName = " + deviceName);

        return deviceName;
    }

    /**
     * Get the existing account key of the provider, this API can be called after handshake.
     *
     * @return the existing account key if the provider has paired with the account before.
     * Otherwise, return null, i.e. it is a real initial pairing.
     */
    @WorkerThread
    @Nullable
    public byte[] getExistingAccountKey() {
        return mPairedHistoryFinder == null ? null : mPairedHistoryFinder.getExistingAccountKey();
    }

    /**
     * Pairs with headphones. Synchronous: Blocks until paired and connected. Throws on any error.
     *
     * @return the secret key for the user's account, if written.
     */
    @WorkerThread
    @Nullable
    public SharedSecret pair()
            throws BluetoothException, InterruptedException, ReflectionException, TimeoutException,
            ExecutionException, PairingException {
        try {
            return pair(/*key=*/ null);
        } catch (GeneralSecurityException e) {
            throw new RuntimeException("Should never happen, no security key!", e);
        }
    }

    /**
     * Pairs with headphones. Synchronous: Blocks until paired and connected. Throws on any error.
     *
     * @param key can be in two different formats. If it is 16 bytes long, then it is an AES account
     * key. Otherwise, it's a public key generated by {@link EllipticCurveDiffieHellmanExchange}.
     * See go/fast-pair-2-spec for how each of these keys are used.
     * @return the secret key for the user's account, if written
     */
    @WorkerThread
    @Nullable
    public SharedSecret pair(@Nullable byte[] key)
            throws BluetoothException, InterruptedException, ReflectionException, TimeoutException,
            ExecutionException, PairingException, GeneralSecurityException {
        mPairingKey = key;
        if (key != null) {
            Log.i(TAG, "Starting to pair " + maskBluetoothAddress(mBleAddress) + ": key["
                    + key.length + "], " + mPreferences);
        } else {
            Log.i(TAG, "Pairing " + maskBluetoothAddress(mBleAddress) + ": " + mPreferences);
        }
        if (mPreferences.getExtraLoggingInformation() != null) {
            this.mEventLogger.bind(
                    mContext, mBleAddress, mPreferences.getExtraLoggingInformation());
        }
        // Provider never initiates if key is null (Fast Pair 1.0).
        if (key != null && mPreferences.getProviderInitiatesBondingIfSupported()) {
            // Provider can't initiate if we can't get our own public address, so check.
            this.mEventLogger.setCurrentEvent(EventCode.GET_LOCAL_PUBLIC_ADDRESS);
            if (BluetoothAddress.getPublicAddress(mContext) != null) {
                this.mEventLogger.logCurrentEventSucceeded();
                mProviderInitiatesBonding = true;
            } else {
                this.mEventLogger
                        .logCurrentEventFailed(new IllegalStateException("null bluetooth_address"));
                Log.e(TAG,
                        "Want provider to initiate bonding, but cannot access Bluetooth public "
                                + "address. Falling back to initiating bonding ourselves.");
            }
        }

        // User might be pairing with a bonded device. In this case, we just connect profile
        // directly and finish pairing.
        if (directConnectProfileWithCachedAddress()) {
            callbackOnPaired();
            mTimingLogger.dump();
            if (mEventLogger.isBound()) {
                mEventLogger.unbind(mContext);
            }
            return null;
        }

        // Lazily initialize a new connection manager for each pairing request.
        initGattConnectionManager();
        boolean isSecretHandshakeCompleted = true;

        try {
            if (key != null && key.length > 0) {
                // GATT_CONNECTION_AND_SECRET_HANDSHAKE start.
                mEventLogger.setCurrentEvent(EventCode.GATT_CONNECTION_AND_SECRET_HANDSHAKE);
                isSecretHandshakeCompleted = false;
                Exception lastException = null;
                boolean lastExceptionFromHandshake = false;
                long startTime = SystemClock.elapsedRealtime();
                // We communicate over this connection twice for Key-based Pairing: once before
                // bonding begins, and once during (to transfer the passkey). Empirically, keeping
                // it alive throughout is far more reliable than disconnecting and reconnecting for
                // each step. The while loop is for retry of GATT connection and handshake only.
                do {
                    boolean isHandshaking = false;
                    try (BluetoothGattConnection connection =
                            mGattConnectionManager
                                    .getConnectionWithSignalLostCheck(mRescueFromError)) {
                        mEventLogger.setCurrentEvent(EventCode.SECRET_HANDSHAKE);
                        if (lastException != null && !lastExceptionFromHandshake) {
                            logRetrySuccessEvent(EventCode.RECOVER_BY_RETRY_GATT, lastException,
                                    mEventLogger);
                            lastException = null;
                        }
                        try (ScopedTiming scopedTiming = new ScopedTiming(mTimingLogger,
                                "Handshake")) {
                            isHandshaking = true;
                            handshakeForKeyBasedPairing(key);
                            // After handshake, Fast Pair has the public address of the provider, so
                            // we can check if it has paired with the account.
                            if (mPublicAddress != null && mPairedHistoryFinder != null) {
                                if (mPairedHistoryFinder.isInPairedHistory(mPublicAddress)) {
                                    Log.i(TAG, "The provider is found in paired history.");
                                } else {
                                    Log.i(TAG, "The provider is not found in paired history.");
                                }
                            }
                        }
                        isHandshaking = false;
                        // SECRET_HANDSHAKE end.
                        mEventLogger.logCurrentEventSucceeded();
                        isSecretHandshakeCompleted = true;
                        if (mPrepareCreateBondCallback != null) {
                            mPrepareCreateBondCallback.run();
                        }
                        if (lastException != null && lastExceptionFromHandshake) {
                            logRetrySuccessEvent(EventCode.RECOVER_BY_RETRY_HANDSHAKE_RECONNECT,
                                    lastException, mEventLogger);
                        }
                        logManualRetryCounts(/* success= */ true);
                        // GATT_CONNECTION_AND_SECRET_HANDSHAKE end.
                        mEventLogger.logCurrentEventSucceeded();
                        return pair(mPreferences.getEnableBrEdrHandover());
                    } catch (SignalLostException e) {
                        long spentTime = SystemClock.elapsedRealtime() - startTime;
                        if (spentTime > mPreferences.getAddressRotateRetryMaxSpentTimeMs()) {
                            Log.w(TAG, "Signal lost but already spend too much time " + spentTime
                                    + "ms");
                            throw e;
                        }

                        logCurrentEventFailedBySignalLost(e);
                        lastException = (Exception) e.getCause();
                        lastExceptionFromHandshake = isHandshaking;
                        if (mRescueFromError != null && isHandshaking) {
                            mRescueFromError.accept(ErrorCode.SUCCESS_SECRET_HANDSHAKE_RECONNECT);
                        }
                        Log.i(TAG, "Signal lost, retry");
                        // In case we meet some GATT error which is not recoverable and fail very
                        // quick.
                        SystemClock.sleep(mPreferences.getPairingRetryDelayMs());
                    } catch (SignalRotatedException e) {
                        long spentTime = SystemClock.elapsedRealtime() - startTime;
                        if (spentTime > mPreferences.getAddressRotateRetryMaxSpentTimeMs()) {
                            Log.w(TAG, "Address rotated but already spend too much time "
                                    + spentTime + "ms");
                            throw e;
                        }

                        logCurrentEventFailedBySignalRotated(e);
                        setBleAddress(e.getNewAddress());
                        lastException = (Exception) e.getCause();
                        lastExceptionFromHandshake = isHandshaking;
                        if (mRescueFromError != null) {
                            mRescueFromError.accept(ErrorCode.SUCCESS_ADDRESS_ROTATE);
                        }
                        Log.i(TAG, "Address rotated, retry");
                    } catch (HandshakeException e) {
                        long spentTime = SystemClock.elapsedRealtime() - startTime;
                        if (spentTime > mPreferences
                                .getSecretHandshakeRetryGattConnectionMaxSpentTimeMs()) {
                            Log.w(TAG, "Secret handshake failed but already spend too much time "
                                    + spentTime + "ms");
                            throw e.getOriginalException();
                        }
                        if (mEventLogger.isCurrentEvent()) {
                            mEventLogger.logCurrentEventFailed(e.getOriginalException());
                        }
                        initGattConnectionManager();
                        lastException = e.getOriginalException();
                        lastExceptionFromHandshake = true;
                        if (mRescueFromError != null) {
                            mRescueFromError.accept(ErrorCode.SUCCESS_SECRET_HANDSHAKE_RECONNECT);
                        }
                        Log.i(TAG, "Handshake failed, retry GATT connection");
                    }
                } while (mPreferences.getRetryGattConnectionAndSecretHandshake());
            }
            if (mPrepareCreateBondCallback != null) {
                mPrepareCreateBondCallback.run();
            }
            return pair(mPreferences.getEnableBrEdrHandover());
        } catch (SignalLostException e) {
            logCurrentEventFailedBySignalLost(e);
            // GATT_CONNECTION_AND_SECRET_HANDSHAKE end.
            if (!isSecretHandshakeCompleted) {
                logManualRetryCounts(/* success= */ false);
                logCurrentEventFailedBySignalLost(e);
            }
            throw e;
        } catch (SignalRotatedException e) {
            logCurrentEventFailedBySignalRotated(e);
            // GATT_CONNECTION_AND_SECRET_HANDSHAKE end.
            if (!isSecretHandshakeCompleted) {
                logManualRetryCounts(/* success= */ false);
                logCurrentEventFailedBySignalRotated(e);
            }
            throw e;
        } catch (BluetoothException
                | InterruptedException
                | ReflectionException
                | TimeoutException
                | ExecutionException
                | PairingException
                | GeneralSecurityException e) {
            if (mEventLogger.isCurrentEvent()) {
                mEventLogger.logCurrentEventFailed(e);
            }
            // GATT_CONNECTION_AND_SECRET_HANDSHAKE end.
            if (!isSecretHandshakeCompleted) {
                logManualRetryCounts(/* success= */ false);
                if (mEventLogger.isCurrentEvent()) {
                    mEventLogger.logCurrentEventFailed(e);
                }
            }
            throw e;
        } finally {
            mTimingLogger.dump();
            if (mEventLogger.isBound()) {
                mEventLogger.unbind(mContext);
            }
        }
    }

    private boolean directConnectProfileWithCachedAddress() throws ReflectionException {
        if (TextUtils.isEmpty(mPreferences.getCachedDeviceAddress())
                || !mPreferences.getDirectConnectProfileIfModelIdInCache()
                || mPreferences.getSkipConnectingProfiles()) {
            return false;
        }
        Log.i(TAG, "Try to direct connect profile with cached address "
                + maskBluetoothAddress(mPreferences.getCachedDeviceAddress()));
        mEventLogger.setCurrentEvent(EventCode.DIRECTLY_CONNECT_PROFILE_WITH_CACHED_ADDRESS);
        BluetoothDevice device =
                mBluetoothAdapter.getRemoteDevice(mPreferences.getCachedDeviceAddress()).unwrap();
        AtomicBoolean interruptConnection = new AtomicBoolean(false);
        BroadcastReceiver receiver =
                new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        if (intent == null
                                || !BluetoothDevice.ACTION_PAIRING_REQUEST
                                .equals(intent.getAction())) {
                            return;
                        }
                        BluetoothDevice pairingDevice = intent
                                .getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                        if (pairingDevice == null || !device.getAddress()
                                .equals(pairingDevice.getAddress())) {
                            return;
                        }
                        abortBroadcast();
                        // Should be the clear link key case, make it fail directly to go back to
                        // initial pairing process.
                        pairingDevice.setPairingConfirmation(/* confirm= */ false);
                        Log.w(TAG, "Get pairing request broadcast for device "
                                + maskBluetoothAddress(device.getAddress())
                                + " while try to direct connect profile with cached address, reject"
                                + " and to go back to initial pairing process");
                        interruptConnection.set(true);
                    }
                };
        mContext.registerReceiver(receiver,
                new IntentFilter(BluetoothDevice.ACTION_PAIRING_REQUEST));
        try (ScopedTiming scopedTiming =
                new ScopedTiming(mTimingLogger,
                        "Connect to profile with cached address directly")) {
            if (mBeforeDirectlyConnectProfileFromCacheForTest != null) {
                mBeforeDirectlyConnectProfileFromCacheForTest.run();
            }
            attemptConnectProfiles(
                    new BluetoothAudioPairer(
                            mContext,
                            device,
                            mPreferences,
                            mEventLogger,
                            /* keyBasedPairingInfo= */ null,
                            /* passkeyConfirmationHandler= */ null,
                            mTimingLogger),
                    maskBluetoothAddress(device),
                    getSupportedProfiles(device),
                    /* numConnectionAttempts= */ 1,
                    /* enablePairingBehavior= */ false,
                    interruptConnection);
            Log.i(TAG,
                    "Directly connected to " + maskBluetoothAddress(device)
                            + "with cached address.");
            mEventLogger.logCurrentEventSucceeded();
            mEventLogger.setDevice(device);
            logPairWithPossibleCachedAddress(device.getAddress());
            return true;
        } catch (PairingException e) {
            if (interruptConnection.get()) {
                Log.w(TAG, "Fail to connected to " + maskBluetoothAddress(device)
                        + " with cached address due to link key is cleared.", e);
                mEventLogger.logCurrentEventFailed(
                        new ConnectException(ConnectErrorCode.LINK_KEY_CLEARED,
                                "Link key is cleared"));
            } else {
                Log.w(TAG, "Fail to connected to " + maskBluetoothAddress(device)
                        + " with cached address.", e);
                mEventLogger.logCurrentEventFailed(e);
            }
            return false;
        } finally {
            mContext.unregisterReceiver(receiver);
        }
    }

    /**
     * Logs for user retry, check go/fastpairquality21q3 for more details.
     */
    private void logManualRetryCounts(boolean success) {
        if (!mPreferences.getLogUserManualRetry()) {
            return;
        }

        // We don't want to be the final event on analytics.
        if (!mEventLogger.isCurrentEvent()) {
            return;
        }

        mEventLogger.setCurrentEvent(EventCode.GATT_HANDSHAKE_MANUAL_RETRY_ATTEMPTS);
        if (mPreferences.getPairFailureCounts() <= 0 && success) {
            mEventLogger.logCurrentEventSucceeded();
        } else {
            int errorCode = mPreferences.getPairFailureCounts();
            if (errorCode > 99) {
                errorCode = 99;
            }
            errorCode += success ? 0 : 100;
            // To not conflict with current error codes.
            errorCode += GATT_ERROR_CODE_USER_RETRY;
            mEventLogger.logCurrentEventFailed(
                    new BluetoothGattException("Error for manual retry", errorCode));
        }
    }

    static void logRetrySuccessEvent(
            @EventCode int eventCode,
            @Nullable Exception recoverFromException,
            EventLoggerWrapper eventLogger) {
        if (recoverFromException == null) {
            return;
        }
        eventLogger.setCurrentEvent(eventCode);
        eventLogger.logCurrentEventFailed(recoverFromException);
    }

    private void initGattConnectionManager() {
        mGattConnectionManager =
                new GattConnectionManager(
                        mContext,
                        mPreferences,
                        mEventLogger,
                        mBluetoothAdapter,
                        this::toggleBluetooth,
                        mBleAddress,
                        mTimingLogger,
                        mFastPairSignalChecker,
                        isPairingWithAntiSpoofingPublicKey());
    }

    private void logCurrentEventFailedBySignalRotated(SignalRotatedException e) {
        if (!mEventLogger.isCurrentEvent()) {
            return;
        }

        Log.w(TAG, "BLE Address for pairing device might rotated!");
        mEventLogger.logCurrentEventFailed(
                new BluetoothGattException(
                        "BLE Address for pairing device might rotated",
                        appendMoreErrorCode(GATT_ERROR_CODE_FAST_PAIR_ADDRESS_ROTATED,
                                e.getCause()),
                        e));
    }

    private void logCurrentEventFailedBySignalLost(SignalLostException e) {
        if (!mEventLogger.isCurrentEvent()) {
            return;
        }

        Log.w(TAG, "BLE signal for pairing device might lost!");
        mEventLogger.logCurrentEventFailed(
                new BluetoothGattException(
                        "BLE signal for pairing device might lost",
                        appendMoreErrorCode(GATT_ERROR_CODE_FAST_PAIR_SIGNAL_LOST, e.getCause()),
                        e));
    }

    @VisibleForTesting
    static int appendMoreErrorCode(int masterErrorCode, @Nullable Throwable cause) {
        if (cause instanceof BluetoothGattException) {
            return masterErrorCode + ((BluetoothGattException) cause).getGattErrorCode();
        } else if (cause instanceof TimeoutException
                || cause instanceof BluetoothTimeoutException
                || cause instanceof BluetoothOperationTimeoutException) {
            return masterErrorCode + GATT_ERROR_CODE_TIMEOUT;
        } else {
            return masterErrorCode;
        }
    }

    private void setBleAddress(String newAddress) {
        if (TextUtils.isEmpty(newAddress) || Ascii.equalsIgnoreCase(newAddress, mBleAddress)) {
            return;
        }

        mBleAddress = newAddress;

        // Recreates a GattConnectionManager with the new address for establishing a new GATT
        // connection later.
        initGattConnectionManager();

        mEventLogger.setDevice(mBluetoothAdapter.getRemoteDevice(mBleAddress).unwrap());
    }

    /**
     * Gets the public address of the headset used in the connection. Before the handshake, this
     * could be null.
     */
    @Nullable
    public String getPublicAddress() {
        return mPublicAddress;
    }

    /**
     * Pairs with a Bluetooth device. In general, this process goes through the following steps:
     *
     * <ol>
     *   <li>Get BrEdr handover information if requested
     *   <li>Discover the device (on Android N and lower to work around a bug)
     *   <li>Connect to the device
     *       <ul>
     *         <li>Attempt a direct connection to a supported profile if we're already bonded
     *         <li>Create a new bond with the not bonded device and then connect to a supported
     *             profile
     *       </ul>
     *   <li>Write the account secret
     * </ol>
     *
     * <p>Blocks until paired. May take 10+ seconds, so run on a background thread.
     */
    @Nullable
    private SharedSecret pair(boolean enableBrEdrHandover)
            throws BluetoothException, InterruptedException, ReflectionException, TimeoutException,
            ExecutionException, PairingException, GeneralSecurityException {
        BrEdrHandoverInformation brEdrHandoverInformation = null;
        if (enableBrEdrHandover) {
            try (ScopedTiming scopedTiming =
                    new ScopedTiming(mTimingLogger, "Get BR/EDR handover information via GATT")) {
                brEdrHandoverInformation =
                        getBrEdrHandoverInformation(mGattConnectionManager.getConnection());
            } catch (BluetoothException | TdsException e) {
                Log.w(TAG,
                        "Couldn't get BR/EDR Handover info via TDS. Trying direct connect.", e);
                mEventLogger.logCurrentEventFailed(e);
            }
        }

        if (brEdrHandoverInformation == null) {
            // Pair directly to the BLE address. Works if the BLE and Bluetooth Classic addresses
            // are the same, or if we can do BLE cross-key transport.
            brEdrHandoverInformation =
                    new BrEdrHandoverInformation(
                            BluetoothAddress
                                    .decode(mPublicAddress != null ? mPublicAddress : mBleAddress),
                            attemptGetBluetoothClassicProfiles(
                                    mBluetoothAdapter.getRemoteDevice(mBleAddress).unwrap(),
                                    mPreferences.getNumSdpAttempts()));
        }

        BluetoothDevice device =
                mBluetoothAdapter.getRemoteDevice(brEdrHandoverInformation.mBluetoothAddress)
                        .unwrap();
        callbackOnGetAddress(device.getAddress());
        mEventLogger.setDevice(device);

        Log.i(TAG, "Pairing with " + brEdrHandoverInformation);
        KeyBasedPairingInfo keyBasedPairingInfo =
                mPairingSecret == null
                        ? null
                        : new KeyBasedPairingInfo(
                                mPairingSecret, mGattConnectionManager, mProviderInitiatesBonding);

        BluetoothAudioPairer pairer =
                new BluetoothAudioPairer(
                        mContext,
                        device,
                        mPreferences,
                        mEventLogger,
                        keyBasedPairingInfo,
                        mPasskeyConfirmationHandler,
                        mTimingLogger);

        logPairWithPossibleCachedAddress(device.getAddress());
        logPairWithModelIdInCacheAndDiscoveryFailForCachedAddress(device);

        // In the case where we are already bonded, we should first just try connecting to supported
        // profiles. If successful, then this will be much faster than recreating the bond like we
        // normally do and we can finish early. It is also more reliable than tearing down the bond
        // and recreating it.
        try {
            if (!sTestMode) {
                attemptDirectConnectionIfBonded(device, pairer);
            }
            callbackOnPaired();
            return maybeWriteAccountKey(device);
        } catch (PairingException e) {
            Log.i(TAG, "Failed to directly connect to supported profiles: " + e.getMessage());
            // Catches exception when we fail to connect support profile. And makes the flow to go
            // through step to write account key when device is bonded.
            if (mPreferences.getEnablePairFlowShowUiWithoutProfileConnection()
                    && device.getBondState() == BluetoothDevice.BOND_BONDED) {
                if (mPreferences.getSkipConnectingProfiles()
                        && !mPreferences.getCheckBondStateWhenSkipConnectingProfiles()) {
                    Log.i(TAG, "For notCheckBondStateWhenSkipConnectingProfiles case should do "
                            + "re-bond");
                } else {
                    Log.i(TAG, "Fail to connect profile when device is bonded, still call back on"
                            + "pair callback to show ui");
                    callbackOnPaired();
                    return maybeWriteAccountKey(device);
                }
            }
        }

        if (mPreferences.getMoreEventLogForQuality()) {
            switch (device.getBondState()) {
                case BOND_BONDED:
                    mEventLogger.setCurrentEvent(EventCode.BEFORE_CREATE_BOND_BONDED);
                    break;
                case BOND_BONDING:
                    mEventLogger.setCurrentEvent(EventCode.BEFORE_CREATE_BOND_BONDING);
                    break;
                case BOND_NONE:
                default:
                    mEventLogger.setCurrentEvent(EventCode.BEFORE_CREATE_BOND);
            }
        }

        for (int i = 1; i <= mPreferences.getNumCreateBondAttempts(); i++) {
            try (ScopedTiming scopedTiming = new ScopedTiming(mTimingLogger, "Pair device #" + i)) {
                pairer.pair();
                if (mPreferences.getMoreEventLogForQuality()) {
                    // For EventCode.BEFORE_CREATE_BOND
                    mEventLogger.logCurrentEventSucceeded();
                }
                break;
            } catch (Exception e) {
                mEventLogger.logCurrentEventFailed(e);
                if (mPasskeyIsGotten) {
                    Log.w(TAG,
                            "createBond() failed because of " + e.getMessage()
                                    + " after getting the passkey. Skip retry.");
                    if (mPreferences.getMoreEventLogForQuality()) {
                        // For EventCode.BEFORE_CREATE_BOND
                        mEventLogger.logCurrentEventFailed(
                                new CreateBondException(
                                        CreateBondErrorCode.FAILED_BUT_ALREADY_RECEIVE_PASS_KEY,
                                        0,
                                        "Already get the passkey"));
                    }
                    break;
                }
                Log.e(TAG,
                        "removeBond() or createBond() failed, attempt " + i + " of " + mPreferences
                                .getNumCreateBondAttempts() + ". Bond state "
                                + device.getBondState(), e);
                if (i < mPreferences.getNumCreateBondAttempts()) {
                    toggleBluetooth();

                    // We've seen 3 createBond() failures within 100ms (!). And then success again
                    // later (even without turning on/off bluetooth). So create some minimum break
                    // time.
                    Log.i(TAG, "Sleeping 1 sec after createBond() failure.");
                    SystemClock.sleep(1000);
                } else if (mPreferences.getMoreEventLogForQuality()) {
                    // For EventCode.BEFORE_CREATE_BOND
                    mEventLogger.logCurrentEventFailed(e);
                }
            }
        }
        boolean deviceCreateBondFailWithNullSecret = false;
        if (!pairer.isPaired()) {
            if (mPairingSecret != null) {
                // Bonding could fail for a few different reasons here. It could be an error, an
                // attacker may have tried to bond, or the device may not be up to spec.
                throw new PairingException("createBond() failed, exiting connection process.");
            } else if (mPreferences.getSkipConnectingProfiles()) {
                throw new PairingException(
                        "createBond() failed and skipping connecting to a profile.");
            } else {
                // When bond creation has failed, connecting a profile will still work most of the
                // time for Fast Pair 1.0 devices (ie, pairing secret is null), so continue on with
                // the spec anyways and attempt to connect supported profiles.
                Log.w(TAG, "createBond() failed, will try connecting profiles anyway.");
                deviceCreateBondFailWithNullSecret = true;
            }
        } else if (mPreferences.getEnablePairFlowShowUiWithoutProfileConnection()) {
            Log.i(TAG, "new flow to call on paired callback for ui when pairing step is finished");
            callbackOnPaired();
        }

        if (!mPreferences.getSkipConnectingProfiles()) {
            if (mPreferences.getWaitForUuidsAfterBonding()
                    && brEdrHandoverInformation.mProfiles.length == 0) {
                short[] supportedProfiles = getCachedUuids(device);
                if (supportedProfiles.length == 0
                        && mPreferences.getNumSdpAttemptsAfterBonded() > 0) {
                    Log.i(TAG, "Found no supported profiles in UUID cache, manually trigger SDP.");
                    attemptGetBluetoothClassicProfiles(device,
                            mPreferences.getNumSdpAttemptsAfterBonded());
                }
                brEdrHandoverInformation =
                        new BrEdrHandoverInformation(
                                brEdrHandoverInformation.mBluetoothAddress, supportedProfiles);
            }
            short[] profiles = brEdrHandoverInformation.mProfiles;
            if (profiles.length == 0) {
                profiles = Constants.getSupportedProfiles();
                Log.w(TAG,
                        "Attempting to connect constants profiles, " + Arrays.toString(profiles));
            } else {
                Log.i(TAG, "Attempting to connect device profiles, " + Arrays.toString(profiles));
            }

            try {
                attemptConnectProfiles(
                        pairer,
                        maskBluetoothAddress(device),
                        profiles,
                        mPreferences.getNumConnectAttempts(),
                        /* enablePairingBehavior= */ false);
            } catch (PairingException e) {
                // For new pair flow to show ui, we already show success ui when finishing the
                // createBond step. So we should catch the exception from connecting profile to
                // avoid showing fail ui for user.
                if (mPreferences.getEnablePairFlowShowUiWithoutProfileConnection()
                        && !deviceCreateBondFailWithNullSecret) {
                    Log.i(TAG, "Fail to connect profile when device is bonded");
                } else {
                    throw e;
                }
            }
        }
        if (!mPreferences.getEnablePairFlowShowUiWithoutProfileConnection()) {
            Log.i(TAG, "original flow to call on paired callback for ui");
            callbackOnPaired();
        } else if (deviceCreateBondFailWithNullSecret) {
            // This paired callback is called for device which create bond fail with null secret
            // such as FastPair 1.0 device when directly connecting to any supported profile.
            Log.i(TAG, "call on paired callback for ui for device with null secret without bonded "
                    + "state");
            callbackOnPaired();
        }
        if (mPreferences.getEnableFirmwareVersionCharacteristic()
                && validateBluetoothGattCharacteristic(
                mGattConnectionManager.getConnection(), FirmwareVersionCharacteristic.ID)) {
            try {
                sInitialConnectionFirmwareVersion = readFirmwareVersion();
            } catch (BluetoothException e) {
                Log.i(TAG, "Fast Pair: head phone does not support firmware read", e);
            }
        }

        // Catch exception when writing account key or name fail to avoid showing pairing failure
        // notice for user. Because device is already paired successfully based on paring step.
        SharedSecret secret = null;
        try {
            secret = maybeWriteAccountKey(device);
        } catch (InterruptedException
                | ExecutionException
                | TimeoutException
                | NoSuchAlgorithmException
                | BluetoothException e) {
            Log.w(TAG, "Fast Pair: Got exception when writing account key or name to provider", e);
        }

        return secret;
    }

    private void logPairWithPossibleCachedAddress(String brEdrAddressForBonding) {
        if (TextUtils.isEmpty(mPreferences.getPossibleCachedDeviceAddress())
                || !mPreferences.getLogPairWithCachedModelId()) {
            return;
        }
        mEventLogger.setCurrentEvent(EventCode.PAIR_WITH_CACHED_MODEL_ID);
        if (Ascii.equalsIgnoreCase(
                mPreferences.getPossibleCachedDeviceAddress(), brEdrAddressForBonding)) {
            mEventLogger.logCurrentEventSucceeded();
            Log.i(TAG, "Repair with possible cached device "
                    + maskBluetoothAddress(mPreferences.getPossibleCachedDeviceAddress()));
        } else {
            mEventLogger.logCurrentEventFailed(
                    new PairingException("Pairing with 2nd device with same model ID"));
            Log.i(TAG, "Pair with a new device " + maskBluetoothAddress(brEdrAddressForBonding)
                    + " with model ID in cache "
                    + maskBluetoothAddress(mPreferences.getPossibleCachedDeviceAddress()));
        }
    }

    /**
     * Logs two type of events. First, why cachedAddress mechanism doesn't work if it's repair with
     * bonded device case. Second, if it's not the case, log how many devices with the same model Id
     * is already paired.
     */
    private void logPairWithModelIdInCacheAndDiscoveryFailForCachedAddress(BluetoothDevice device) {
        if (!mPreferences.getLogPairWithCachedModelId()) {
            return;
        }

        if (device.getBondState() == BOND_BONDED) {
            if (mPreferences.getSameModelIdPairedDeviceCount() <= 0) {
                Log.i(TAG, "Device is bonded but we don't have this model Id in cache.");
            } else if (TextUtils.isEmpty(mPreferences.getCachedDeviceAddress())
                    && mPreferences.getDirectConnectProfileIfModelIdInCache()
                    && !mPreferences.getSkipConnectingProfiles()) {
                // Pair with bonded device case. Log why the cached address is not found.
                mEventLogger.setCurrentEvent(
                        EventCode.DIRECTLY_CONNECT_PROFILE_WITH_CACHED_ADDRESS);
                mEventLogger.logCurrentEventFailed(
                        mPreferences.getIsDeviceFinishCheckAddressFromCache()
                                ? new ConnectException(ConnectErrorCode.FAIL_TO_DISCOVERY,
                                "Failed to discovery")
                                : new ConnectException(
                                        ConnectErrorCode.DISCOVERY_NOT_FINISHED,
                                        "Discovery not finished"));
                Log.i(TAG, "Failed to get cached address due to "
                        + (mPreferences.getIsDeviceFinishCheckAddressFromCache()
                        ? "Failed to discovery"
                        : "Discovery not finished"));
            }
        } else if (device.getBondState() == BOND_NONE) {
            // Pair with new device case, log how many devices with the same model id is in FastPair
            // cache already.
            mEventLogger.setCurrentEvent(EventCode.PAIR_WITH_NEW_MODEL);
            if (mPreferences.getSameModelIdPairedDeviceCount() <= 0) {
                mEventLogger.logCurrentEventSucceeded();
            } else {
                mEventLogger.logCurrentEventFailed(
                        new BluetoothGattException(
                                "Already have this model ID in cache",
                                GATT_ERROR_CODE_PAIR_WITH_SAME_MODEL_ID_COUNT
                                        + mPreferences.getSameModelIdPairedDeviceCount()));
            }
            Log.i(TAG, "This device already has " + mPreferences.getSameModelIdPairedDeviceCount()
                    + " peripheral with the same model Id");
        }
    }

    /**
     * Attempts to directly connect to any supported profile if we're already bonded, this will save
     * time over tearing down the bond and recreating it.
     */
    private void attemptDirectConnectionIfBonded(BluetoothDevice device,
            BluetoothAudioPairer pairer)
            throws PairingException {
        if (mPreferences.getSkipConnectingProfiles()) {
            if (mPreferences.getCheckBondStateWhenSkipConnectingProfiles()
                    && device.getBondState() == BluetoothDevice.BOND_BONDED) {
                Log.i(TAG, "Skipping connecting to profiles by preferences.");
                return;
            }
            throw new PairingException(
                    "Skipping connecting to profiles, no direct connection possible.");
        } else if (!mPreferences.getAttemptDirectConnectionWhenPreviouslyBonded()
                || device.getBondState() != BluetoothDevice.BOND_BONDED) {
            throw new PairingException(
                    "Not previously bonded skipping direct connection, %s", device.getBondState());
        }
        short[] supportedProfiles = getSupportedProfiles(device);
        mEventLogger.setCurrentEvent(EventCode.DIRECTLY_CONNECTED_TO_PROFILE);
        try (ScopedTiming scopedTiming =
                new ScopedTiming(mTimingLogger, "Connect to profile directly")) {
            attemptConnectProfiles(
                    pairer,
                    maskBluetoothAddress(device),
                    supportedProfiles,
                    mPreferences.getEnablePairFlowShowUiWithoutProfileConnection()
                            ? mPreferences.getNumConnectAttempts()
                            : 1,
                    mPreferences.getEnablePairingWhileDirectlyConnecting());
            Log.i(TAG, "Directly connected to " + maskBluetoothAddress(device));
            mEventLogger.logCurrentEventSucceeded();
        } catch (PairingException e) {
            mEventLogger.logCurrentEventFailed(e);
            // Rethrow e so that the exception bubbles up and we continue the normal pairing
            // process.
            throw e;
        }
    }

    @VisibleForTesting
    void attemptConnectProfiles(
            BluetoothAudioPairer pairer,
            String deviceMaskedBluetoothAddress,
            short[] profiles,
            int numConnectionAttempts,
            boolean enablePairingBehavior)
            throws PairingException {
        attemptConnectProfiles(
                pairer,
                deviceMaskedBluetoothAddress,
                profiles,
                numConnectionAttempts,
                enablePairingBehavior,
                new AtomicBoolean(false));
    }

    private void attemptConnectProfiles(
            BluetoothAudioPairer pairer,
            String deviceMaskedBluetoothAddress,
            short[] profiles,
            int numConnectionAttempts,
            boolean enablePairingBehavior,
            AtomicBoolean interruptConnection)
            throws PairingException {
        if (mPreferences.getMoreEventLogForQuality()) {
            mEventLogger.setCurrentEvent(EventCode.BEFORE_CONNECT_PROFILE);
        }
        Exception lastException = null;
        for (short profile : profiles) {
            if (interruptConnection.get()) {
                Log.w(TAG, "attemptConnectProfiles interrupted");
                break;
            }
            if (!mPreferences.isSupportedProfile(profile)) {
                Log.w(TAG, "Ignoring unsupported profile=" + profile);
                continue;
            }
            for (int i = 1; i <= numConnectionAttempts; i++) {
                if (interruptConnection.get()) {
                    Log.w(TAG, "attemptConnectProfiles interrupted");
                    break;
                }
                mEventLogger.setCurrentEvent(EventCode.CONNECT_PROFILE);
                mEventLogger.setCurrentProfile(profile);
                try {
                    pairer.connect(profile, enablePairingBehavior);
                    mEventLogger.logCurrentEventSucceeded();
                    if (mPreferences.getMoreEventLogForQuality()) {
                        // For EventCode.BEFORE_CONNECT_PROFILE
                        mEventLogger.logCurrentEventSucceeded();
                    }
                    // If successful, we're done.
                    // TODO(b/37167120): Connect to more than one profile.
                    return;
                } catch (InterruptedException
                        | ReflectionException
                        | TimeoutException
                        | ExecutionException
                        | ConnectException e) {
                    Log.w(TAG,
                            "Error connecting to profile=" + profile
                                    + " for device=" + deviceMaskedBluetoothAddress
                                    + " (attempt " + i + " of " + mPreferences
                                    .getNumConnectAttempts(), e);
                    mEventLogger.logCurrentEventFailed(e);
                    lastException = e;
                }
            }
        }
        if (mPreferences.getMoreEventLogForQuality()) {
            // For EventCode.BEFORE_CONNECT_PROFILE
            if (lastException != null) {
                mEventLogger.logCurrentEventFailed(lastException);
            } else {
                mEventLogger.logCurrentEventSucceeded();
            }
        }
        throw new PairingException(
                "Unable to connect to any profiles in: %s", Arrays.toString(profiles));
    }

    /**
     * Checks whether or not an account key should be written to the device and writes it if so.
     * This is called after handle notifying the pairedCallback that we've finished pairing, because
     * at this point the headset is ready to use.
     */
    @Nullable
    private SharedSecret maybeWriteAccountKey(BluetoothDevice device)
            throws InterruptedException, ExecutionException, TimeoutException,
            NoSuchAlgorithmException,
            BluetoothException {
        if (!sTestMode) {
            Locator.get(mContext, FastPairController.class).setShouldUpload(false);
        }
        if (!shouldWriteAccountKey()) {
            // For FastPair 2.0, here should be a subsequent pairing case.
            return null;
        }

        // Check if it should be a subsequent pairing but go through initial pairing. If there is an
        // existed paired history found, use the same account key instead of creating a new one.
        byte[] accountKey =
                mPairedHistoryFinder == null ? null : mPairedHistoryFinder.getExistingAccountKey();
        if (accountKey == null) {
            // It is a real initial pairing, generate a new account key for the headset.
            try (ScopedTiming scopedTiming1 =
                    new ScopedTiming(mTimingLogger, "Write account key")) {
                accountKey = doWriteAccountKey(createAccountKey(), device.getAddress());
                if (accountKey == null) {
                    // Without writing account key back to provider, close the connection.
                    mGattConnectionManager.closeConnection();
                    return null;
                }
                if (!mPreferences.getIsRetroactivePairing()) {
                    try (ScopedTiming scopedTiming2 = new ScopedTiming(mTimingLogger,
                            "Start CloudSyncing")) {
                        // Start to sync to the footprint
                        Locator.get(mContext, FastPairController.class).setShouldUpload(true);
                        //mContext.startService(createCloudSyncingIntent(accountKey));
                    } catch (SecurityException e) {
                        Log.w(TAG, "Error adding device.", e);
                    }
                }
            }
        } else if (shouldWriteAccountKeyForExistingCase(accountKey)) {
            // There is an existing account key, but go through initial pairing, and still write the
            // existing account key.
            doWriteAccountKey(accountKey, device.getAddress());
        }

        // When finish writing account key in initial pairing, write new device name back to
        // provider.
        UUID characteristicUuid = NameCharacteristic.getId(mGattConnectionManager.getConnection());
        if (mPreferences.getEnableNamingCharacteristic()
                && mNeedUpdateProviderName
                && validateBluetoothGattCharacteristic(
                mGattConnectionManager.getConnection(), characteristicUuid)) {
            try (ScopedTiming scopedTiming = new ScopedTiming(mTimingLogger,
                    "WriteNameToProvider")) {
                writeNameToProvider(this.mProviderDeviceName, device.getAddress());
            }
        }

        // When finish writing account key and name back to provider, close the connection.
        mGattConnectionManager.closeConnection();
        return SharedSecret.create(accountKey, device.getAddress());
    }

    private boolean shouldWriteAccountKey() {
        return isWritingAccountKeyEnabled() && isPairingWithAntiSpoofingPublicKey();
    }

    private boolean isWritingAccountKeyEnabled() {
        return mPreferences.getNumWriteAccountKeyAttempts() > 0;
    }

    private boolean isPairingWithAntiSpoofingPublicKey() {
        return isPairingWithAntiSpoofingPublicKey(mPairingKey);
    }

    private boolean isPairingWithAntiSpoofingPublicKey(@Nullable byte[] key) {
        return key != null && key.length == EllipticCurveDiffieHellmanExchange.PUBLIC_KEY_LENGTH;
    }

    /**
     * Creates and writes an account key to the provided mac address.
     */
    @Nullable
    private byte[] doWriteAccountKey(byte[] accountKey, String macAddress)
            throws InterruptedException, ExecutionException, TimeoutException, BluetoothException {
        byte[] localPairingSecret = mPairingSecret;
        if (localPairingSecret == null) {
            Log.w(TAG, "Pairing secret was null, account key couldn't be encrypted or written.");
            return null;
        }
        if (!mPreferences.getSkipDisconnectingGattBeforeWritingAccountKey()) {
            try (ScopedTiming scopedTiming = new ScopedTiming(mTimingLogger,
                    "Close GATT and sleep")) {
                // Make a new connection instead of reusing gattConnection, because this is
                // post-pairing and we need an encrypted connection.
                mGattConnectionManager.closeConnection();
                // Sleep before re-connecting to gatt, for writing account key, could increase
                // stability.
                Thread.sleep(mPreferences.getWriteAccountKeySleepMillis());
            }
        }

        byte[] encryptedKey;
        try (ScopedTiming scopedTiming = new ScopedTiming(mTimingLogger, "Encrypt key")) {
            encryptedKey = AesEcbSingleBlockEncryption.encrypt(localPairingSecret, accountKey);
        } catch (GeneralSecurityException e) {
            Log.w("Failed to encrypt key.", e);
            return null;
        }

        for (int i = 1; i <= mPreferences.getNumWriteAccountKeyAttempts(); i++) {
            mEventLogger.setCurrentEvent(EventCode.WRITE_ACCOUNT_KEY);
            try (ScopedTiming scopedTiming = new ScopedTiming(mTimingLogger,
                    "Write key via GATT #" + i)) {
                writeAccountKey(encryptedKey, macAddress);
                mEventLogger.logCurrentEventSucceeded();
                return accountKey;
            } catch (BluetoothException e) {
                Log.w("Error writing account key attempt " + i + " of " + mPreferences
                        .getNumWriteAccountKeyAttempts(), e);
                mEventLogger.logCurrentEventFailed(e);
                // Retry with a while for stability.
                Thread.sleep(mPreferences.getWriteAccountKeySleepMillis());
            }
        }
        return null;
    }

    private byte[] createAccountKey() throws NoSuchAlgorithmException {
        return AccountKeyGenerator.createAccountKey();
    }

    @VisibleForTesting
    boolean shouldWriteAccountKeyForExistingCase(byte[] existingAccountKey) {
        if (!mPreferences.getKeepSameAccountKeyWrite()) {
            Log.i(TAG,
                    "The provider has already paired with the account, skip writing account key.");
            return false;
        }
        if (existingAccountKey[0] != AccountKeyCharacteristic.TYPE) {
            Log.i(TAG,
                    "The provider has already paired with the account, but accountKey[0] != 0x04."
                            + " Forget the device from the account and re-try");

            return false;
        }
        Log.i(TAG, "The provider has already paired with the account, still write the same account "
                + "key.");
        return true;
    }

    /**
     * Performs a key-based pairing request handshake to authenticate and get the remote device's
     * public address.
     *
     * @param key is described in {@link #pair(byte[])}
     */
    @VisibleForTesting
    SharedSecret handshakeForKeyBasedPairing(byte[] key)
            throws InterruptedException, ExecutionException, TimeoutException, BluetoothException,
            GeneralSecurityException, PairingException {
        // We may also initialize gattConnectionManager of prepareForHandshake() that will be used
        // in registerNotificationForNamePacket(), so we need to call it here.
        HandshakeHandler handshakeHandler = prepareForHandshake();
        KeyBasedPairingRequest.Builder keyBasedPairingRequestBuilder =
                new KeyBasedPairingRequest.Builder()
                        .setVerificationData(BluetoothAddress.decode(mBleAddress));
        if (mProviderInitiatesBonding) {
            keyBasedPairingRequestBuilder
                    .addFlag(KeyBasedPairingRequestFlag.PROVIDER_INITIATES_BONDING);
        }
        // Seeker only request provider device name in initial pairing.
        if (mPreferences.getEnableNamingCharacteristic() && isPairingWithAntiSpoofingPublicKey(
                key)) {
            keyBasedPairingRequestBuilder.addFlag(KeyBasedPairingRequestFlag.REQUEST_DEVICE_NAME);
            // Register listener to receive name characteristic response from provider.
            registerNotificationForNamePacket();
        }
        if (mPreferences.getIsRetroactivePairing()) {
            keyBasedPairingRequestBuilder
                    .addFlag(KeyBasedPairingRequestFlag.REQUEST_RETROACTIVE_PAIR);
            keyBasedPairingRequestBuilder.setSeekerPublicAddress(
                    Preconditions.checkNotNull(BluetoothAddress.getPublicAddress(mContext)));
        }

        return performHandshakeWithRetryAndSignalLostCheck(
                handshakeHandler, key, keyBasedPairingRequestBuilder.build(), /* withRetry= */
                true);
    }

    /**
     * Performs an action-over-BLE request handshake for authentication, i.e. to identify the shared
     * secret. The given key should be the account key.
     */
    private SharedSecret handshakeForActionOverBle(byte[] key,
            @AdditionalDataType int additionalDataType)
            throws InterruptedException, ExecutionException, TimeoutException, BluetoothException,
            GeneralSecurityException, PairingException {
        HandshakeHandler handshakeHandler = prepareForHandshake();
        return performHandshakeWithRetryAndSignalLostCheck(
                handshakeHandler,
                key,
                new ActionOverBle.Builder()
                        .setVerificationData(BluetoothAddress.decode(mBleAddress))
                        .setAdditionalDataType(additionalDataType)
                        .build(),
                /* withRetry= */ false);
    }

    private HandshakeHandler prepareForHandshake() {
        if (mGattConnectionManager == null) {
            mGattConnectionManager =
                    new GattConnectionManager(
                            mContext,
                            mPreferences,
                            mEventLogger,
                            mBluetoothAdapter,
                            this::toggleBluetooth,
                            mBleAddress,
                            mTimingLogger,
                            mFastPairSignalChecker,
                            isPairingWithAntiSpoofingPublicKey());
        }
        if (mHandshakeHandlerForTest != null) {
            Log.w(TAG, "Use handshakeHandlerForTest!");
            return verifyNotNull(mHandshakeHandlerForTest);
        }
        return new HandshakeHandler(
                mGattConnectionManager, mBleAddress, mPreferences, mEventLogger,
                mFastPairSignalChecker);
    }

    @VisibleForTesting
    void setHandshakeHandlerForTest(@Nullable HandshakeHandler handshakeHandlerForTest) {
        this.mHandshakeHandlerForTest = handshakeHandlerForTest;
    }

    private SharedSecret performHandshakeWithRetryAndSignalLostCheck(
            HandshakeHandler handshakeHandler,
            byte[] key,
            HandshakeMessage handshakeMessage,
            boolean withRetry)
            throws GeneralSecurityException, ExecutionException, BluetoothException,
            InterruptedException, TimeoutException, PairingException {
        SharedSecret handshakeResult =
                withRetry
                        ? handshakeHandler.doHandshakeWithRetryAndSignalLostCheck(
                        key, handshakeMessage, mRescueFromError)
                        : handshakeHandler.doHandshake(key, handshakeMessage);
        // TODO: Try to remove these two global variables, publicAddress and pairingSecret.
        mPublicAddress = handshakeResult.getAddress();
        mPairingSecret = handshakeResult.getKey();
        return handshakeResult;
    }

    private void toggleBluetooth()
            throws InterruptedException, ExecutionException, TimeoutException {
        if (!mPreferences.getToggleBluetoothOnFailure()) {
            return;
        }

        Log.i(TAG, "Turning Bluetooth off.");
        mEventLogger.setCurrentEvent(EventCode.DISABLE_BLUETOOTH);
        mBluetoothAdapter.unwrap().disable();
        disableBle(mBluetoothAdapter.unwrap());
        try {
            waitForBluetoothState(android.bluetooth.BluetoothAdapter.STATE_OFF);
            mEventLogger.logCurrentEventSucceeded();
        } catch (TimeoutException e) {
            mEventLogger.logCurrentEventFailed(e);
            // Soldier on despite failing to turn off Bluetooth. We can't control whether other
            // clients (even inside GCore) kept it enabled in BLE-only mode.
            Log.w(TAG, "Bluetooth still on. BluetoothAdapter state="
                    + getBleState(mBluetoothAdapter.unwrap()), e);
        }

        // Note: Intentionally don't re-enable BLE-only mode, because we don't know which app
        // enabled it. The client app should listen to Bluetooth events and enable as necessary
        // (because the user can toggle at any time; e.g. via Airplane mode).
        Log.i(TAG, "Turning Bluetooth on.");
        mEventLogger.setCurrentEvent(EventCode.ENABLE_BLUETOOTH);
        mBluetoothAdapter.unwrap().enable();
        waitForBluetoothState(android.bluetooth.BluetoothAdapter.STATE_ON);
        mEventLogger.logCurrentEventSucceeded();
    }

    private void waitForBluetoothState(int state)
            throws TimeoutException, ExecutionException, InterruptedException {
        waitForBluetoothStateUsingPolling(state);
    }

    private void waitForBluetoothStateUsingPolling(int state) throws TimeoutException {
        // There's a bug where we (pretty often!) never get the broadcast for STATE_ON or STATE_OFF.
        // So poll instead.
        long start = SystemClock.elapsedRealtime();
        long timeoutMillis = mPreferences.getBluetoothToggleTimeoutSeconds() * 1000L;
        while (SystemClock.elapsedRealtime() - start < timeoutMillis) {
            if (state == getBleState(mBluetoothAdapter.unwrap())) {
                break;
            }
            SystemClock.sleep(mPreferences.getBluetoothStatePollingMillis());
        }

        if (state != getBleState(mBluetoothAdapter.unwrap())) {
            throw new TimeoutException(
                    String.format(
                            Locale.getDefault(),
                            "Timed out waiting for state %d, current state is %d",
                            state,
                            getBleState(mBluetoothAdapter.unwrap())));
        }
    }

    private BrEdrHandoverInformation getBrEdrHandoverInformation(BluetoothGattConnection connection)
            throws BluetoothException, TdsException, InterruptedException, ExecutionException,
            TimeoutException {
        Log.i(TAG, "Connecting GATT server to BLE address=" + maskBluetoothAddress(mBleAddress));
        Log.i(TAG, "Telling device to become discoverable");
        mEventLogger.setCurrentEvent(EventCode.BR_EDR_HANDOVER_WRITE_CONTROL_POINT_REQUEST);
        ChangeObserver changeObserver =
                connection.enableNotification(
                        TransportDiscoveryService.ID,
                        TransportDiscoveryService.ControlPointCharacteristic.ID);
        connection.writeCharacteristic(
                TransportDiscoveryService.ID,
                TransportDiscoveryService.ControlPointCharacteristic.ID,
                TDS_CONTROL_POINT_REQUEST);

        byte[] response =
                changeObserver.waitForUpdate(
                        TimeUnit.SECONDS.toMillis(mPreferences.getGattOperationTimeoutSeconds()));
        @ResultCode int resultCode = fromTdsControlPointIndication(response);
        if (resultCode != ResultCode.SUCCESS) {
            throw new TdsException(
                    BrEdrHandoverErrorCode.CONTROL_POINT_RESULT_CODE_NOT_SUCCESS,
                    "TDS Control Point result code (%s) was not success in response %s",
                    resultCode,
                    base16().lowerCase().encode(response));
        }
        mEventLogger.logCurrentEventSucceeded();
        return new BrEdrHandoverInformation(
                getAddressFromBrEdrConnection(connection),
                getProfilesFromBrEdrConnection(connection));
    }

    private byte[] getAddressFromBrEdrConnection(BluetoothGattConnection connection)
            throws BluetoothException, TdsException {
        Log.i(TAG, "Getting Bluetooth MAC");
        mEventLogger.setCurrentEvent(EventCode.BR_EDR_HANDOVER_READ_BLUETOOTH_MAC);
        byte[] brHandoverData =
                connection.readCharacteristic(
                        TransportDiscoveryService.ID,
                        to128BitUuid(mPreferences.getBrHandoverDataCharacteristicId()));
        if (brHandoverData == null || brHandoverData.length < 7) {
            throw new TdsException(
                    BrEdrHandoverErrorCode.BLUETOOTH_MAC_INVALID,
                    "Bluetooth MAC not contained in BR handover data: %s",
                    brHandoverData != null ? base16().lowerCase().encode(brHandoverData)
                            : "(none)");
        }
        byte[] bluetoothAddress =
                new Bytes.Value(Arrays.copyOfRange(brHandoverData, 1, 7), ByteOrder.LITTLE_ENDIAN)
                        .getBytes(ByteOrder.BIG_ENDIAN);
        mEventLogger.logCurrentEventSucceeded();
        return bluetoothAddress;
    }

    private short[] getProfilesFromBrEdrConnection(BluetoothGattConnection connection) {
        mEventLogger.setCurrentEvent(EventCode.BR_EDR_HANDOVER_READ_TRANSPORT_BLOCK);
        try {
            byte[] transportBlock =
                    connection.readDescriptor(
                            TransportDiscoveryService.ID,
                            to128BitUuid(mPreferences.getBluetoothSigDataCharacteristicId()),
                            to128BitUuid(mPreferences.getBrTransportBlockDataDescriptorId()));
            Log.i(TAG, "Got transport block: " + base16().lowerCase().encode(transportBlock));
            short[] profiles = getSupportedProfiles(transportBlock);
            mEventLogger.logCurrentEventSucceeded();
            return profiles;
        } catch (BluetoothException | TdsException | ParseException e) {
            Log.w(TAG, "Failed to get supported profiles from transport block.", e);
            mEventLogger.logCurrentEventFailed(e);
        }
        return new short[0];
    }

    @VisibleForTesting
    boolean writeNameToProvider(@Nullable String deviceName, @Nullable String address)
            throws InterruptedException, TimeoutException, ExecutionException {
        if (deviceName == null || address == null) {
            Log.i(TAG, "writeNameToProvider fail because provider name or address is null.");
            return false;
        }
        if (mPairingSecret == null) {
            Log.i(TAG, "writeNameToProvider fail because no pairingSecret.");
            return false;
        }
        byte[] encryptedDeviceNamePacket;
        try (ScopedTiming scopedTiming = new ScopedTiming(mTimingLogger, "Encode device name")) {
            encryptedDeviceNamePacket =
                    NamingEncoder.encodeNamingPacket(mPairingSecret, deviceName);
        } catch (GeneralSecurityException e) {
            Log.w(TAG, "Failed to encrypt device name.", e);
            return false;
        }

        for (int i = 1; i <= mPreferences.getNumWriteAccountKeyAttempts(); i++) {
            mEventLogger.setCurrentEvent(EventCode.WRITE_DEVICE_NAME);
            try {
                writeDeviceName(encryptedDeviceNamePacket, address);
                mEventLogger.logCurrentEventSucceeded();
                return true;
            } catch (BluetoothException e) {
                Log.w(TAG, "Error writing name attempt " + i + " of "
                        + mPreferences.getNumWriteAccountKeyAttempts());
                mEventLogger.logCurrentEventFailed(e);
                // Reuses the existing preference because the same usage.
                Thread.sleep(mPreferences.getWriteAccountKeySleepMillis());
            }
        }
        return false;
    }

    private void writeAccountKey(byte[] encryptedAccountKey, String address)
            throws BluetoothException, InterruptedException, ExecutionException, TimeoutException {
        Log.i(TAG, "Writing account key to address=" + maskBluetoothAddress(address));
        BluetoothGattConnection connection = mGattConnectionManager.getConnection();
        connection.setOperationTimeout(
                TimeUnit.SECONDS.toMillis(mPreferences.getGattOperationTimeoutSeconds()));
        UUID characteristicUuid = AccountKeyCharacteristic.getId(connection);
        connection.writeCharacteristic(FastPairService.ID, characteristicUuid, encryptedAccountKey);
        Log.i(TAG,
                "Finished writing encrypted account key=" + base16().encode(encryptedAccountKey));
    }

    private void writeDeviceName(byte[] naming, String address)
            throws BluetoothException, InterruptedException, ExecutionException, TimeoutException {
        Log.i(TAG, "Writing new device name to address=" + maskBluetoothAddress(address));
        BluetoothGattConnection connection = mGattConnectionManager.getConnection();
        connection.setOperationTimeout(
                TimeUnit.SECONDS.toMillis(mPreferences.getGattOperationTimeoutSeconds()));
        UUID characteristicUuid = NameCharacteristic.getId(connection);
        connection.writeCharacteristic(FastPairService.ID, characteristicUuid, naming);
        Log.i(TAG, "Finished writing new device name=" + base16().encode(naming));
    }

    /**
     * Reads firmware version after write account key to provider since simulator is more stable to
     * read firmware version in initial gatt connection. This function will also read firmware when
     * detect bloomfilter. Need to verify this after real device come out. TODO(b/130592473)
     */
    @Nullable
    public String readFirmwareVersion()
            throws BluetoothException, InterruptedException, ExecutionException, TimeoutException {
        if (!TextUtils.isEmpty(sInitialConnectionFirmwareVersion)) {
            String result = sInitialConnectionFirmwareVersion;
            sInitialConnectionFirmwareVersion = null;
            return result;
        }
        if (mGattConnectionManager == null) {
            mGattConnectionManager =
                    new GattConnectionManager(
                            mContext,
                            mPreferences,
                            mEventLogger,
                            mBluetoothAdapter,
                            this::toggleBluetooth,
                            mBleAddress,
                            mTimingLogger,
                            mFastPairSignalChecker,
                            /* setMtu= */ true);
            mGattConnectionManager.closeConnection();
        }
        if (sTestMode) {
            return null;
        }
        BluetoothGattConnection connection = mGattConnectionManager.getConnection();
        connection.setOperationTimeout(
                TimeUnit.SECONDS.toMillis(mPreferences.getGattOperationTimeoutSeconds()));

        try {
            String firmwareVersion =
                    new String(
                            connection.readCharacteristic(
                                    FastPairService.ID,
                                    to128BitUuid(
                                            mPreferences.getFirmwareVersionCharacteristicId())));
            Log.i(TAG, "FastPair: Got the firmware info version number = " + firmwareVersion);
            mGattConnectionManager.closeConnection();
            return firmwareVersion;
        } catch (BluetoothException e) {
            Log.i(TAG, "FastPair: can't read firmware characteristic.", e);
            mGattConnectionManager.closeConnection();
            return null;
        }
    }

    @VisibleForTesting
    @Nullable
    String getInitialConnectionFirmware() {
        return sInitialConnectionFirmwareVersion;
    }

    private void registerNotificationForNamePacket()
            throws BluetoothException, InterruptedException, ExecutionException, TimeoutException {
        Log.i(TAG,
                "register for the device name response from address=" + maskBluetoothAddress(
                        mBleAddress));

        BluetoothGattConnection gattConnection = mGattConnectionManager.getConnection();
        gattConnection.setOperationTimeout(
                TimeUnit.SECONDS.toMillis(mPreferences.getGattOperationTimeoutSeconds()));
        try {
            mDeviceNameReceiver = new DeviceNameReceiver(gattConnection);
        } catch (BluetoothException e) {
            Log.i(TAG, "Can't register for device name response, no naming characteristic.");
            return;
        }
    }

    private short[] getSupportedProfiles(BluetoothDevice device) {
        short[] supportedProfiles = getCachedUuids(device);
        if (supportedProfiles.length == 0 && mPreferences.getNumSdpAttemptsAfterBonded() > 0) {
            supportedProfiles =
                    attemptGetBluetoothClassicProfiles(device,
                            mPreferences.getNumSdpAttemptsAfterBonded());
        }
        if (supportedProfiles.length == 0) {
            supportedProfiles = Constants.getSupportedProfiles();
            Log.w(TAG, "Attempting to connect constants profiles, "
                    + Arrays.toString(supportedProfiles));
        } else {
            Log.i(TAG,
                    "Attempting to connect device profiles, " + Arrays.toString(supportedProfiles));
        }
        return supportedProfiles;
    }

    private static short[] getSupportedProfiles(byte[] transportBlock)
            throws TdsException, ParseException {
        if (transportBlock == null || transportBlock.length < 4) {
            throw new TdsException(
                    BrEdrHandoverErrorCode.TRANSPORT_BLOCK_INVALID,
                    "Transport Block null or too short: %s",
                    base16().lowerCase().encode(transportBlock));
        }
        int transportDataLength = transportBlock[2];
        if (transportBlock.length < 3 + transportDataLength) {
            throw new TdsException(
                    BrEdrHandoverErrorCode.TRANSPORT_BLOCK_INVALID,
                    "Transport Block has wrong length byte: %s",
                    base16().lowerCase().encode(transportBlock));
        }
        byte[] transportData = Arrays.copyOfRange(transportBlock, 3, 3 + transportDataLength);
        for (Ltv ltv : Ltv.parse(transportData)) {
            int uuidLength = uuidLength(ltv.mType);
            // We currently only support a single list of 2-byte UUIDs.
            // TODO(b/37539535): Support multiple lists, and longer (32-bit, 128-bit) IDs?
            if (uuidLength == 2) {
                return toShorts(ByteOrder.LITTLE_ENDIAN, ltv.mValue);
            }
        }
        return new short[0];
    }

    /**
     * Returns 0 if the type is not one of the UUID list types; otherwise returns length in bytes.
     */
    private static int uuidLength(byte dataType) {
        switch (dataType) {
            case TransportDiscoveryService.SERVICE_UUIDS_16_BIT_LIST_TYPE:
                return 2;
            case TransportDiscoveryService.SERVICE_UUIDS_32_BIT_LIST_TYPE:
                return 4;
            case TransportDiscoveryService.SERVICE_UUIDS_128_BIT_LIST_TYPE:
                return 16;
            default:
                return 0;
        }
    }

    private short[] attemptGetBluetoothClassicProfiles(BluetoothDevice device, int numSdpAttempts) {
        // The docs say that if fetchUuidsWithSdp() has an error or "takes a long time", we get an
        // intent containing only the stuff in the cache (i.e. nothing). Retry a few times.
        short[] supportedProfiles = null;
        for (int i = 1; i <= numSdpAttempts; i++) {
            mEventLogger.setCurrentEvent(EventCode.GET_PROFILES_VIA_SDP);
            try (ScopedTiming scopedTiming =
                    new ScopedTiming(mTimingLogger,
                            "Get BR/EDR handover information via SDP #" + i)) {
                supportedProfiles = getSupportedProfilesViaBluetoothClassic(device);
            } catch (ExecutionException | InterruptedException | TimeoutException e) {
                // Ignores and retries if needed.
            }
            if (supportedProfiles != null && supportedProfiles.length != 0) {
                mEventLogger.logCurrentEventSucceeded();
                break;
            } else {
                mEventLogger.logCurrentEventFailed(new TimeoutException());
                Log.w(TAG, "SDP returned no UUIDs from " + maskBluetoothAddress(device.getAddress())
                        + ", assuming timeout (attempt " + i + " of " + numSdpAttempts + ").");
            }
        }
        return (supportedProfiles == null) ? new short[0] : supportedProfiles;
    }

    private short[] getSupportedProfilesViaBluetoothClassic(BluetoothDevice device)
            throws ExecutionException, InterruptedException, TimeoutException {
        Log.i(TAG, "Getting supported profiles via SDP (Bluetooth Classic) for "
                + maskBluetoothAddress(device.getAddress()));
        try (DeviceIntentReceiver supportedProfilesReceiver =
                DeviceIntentReceiver.oneShotReceiver(
                        mContext, mPreferences, device, BluetoothDevice.ACTION_UUID)) {
            device.fetchUuidsWithSdp();
            supportedProfilesReceiver.await(mPreferences.getSdpTimeoutSeconds(), TimeUnit.SECONDS);
        }
        return getCachedUuids(device);
    }

    private static short[] getCachedUuids(BluetoothDevice device) {
        ParcelUuid[] parcelUuids = device.getUuids();
        Log.i(TAG, "Got supported UUIDs: " + Arrays.toString(parcelUuids));
        if (parcelUuids == null) {
            // The OS can return null.
            parcelUuids = new ParcelUuid[0];
        }

        List<Short> shortUuids = new ArrayList<>(parcelUuids.length);
        for (ParcelUuid parcelUuid : parcelUuids) {
            UUID uuid = parcelUuid.getUuid();
            if (BluetoothUuids.is16BitUuid(uuid)) {
                shortUuids.add(get16BitUuid(uuid));
            }
        }
        return Shorts.toArray(shortUuids);
    }

    private void callbackOnPaired() {
        if (mPairedCallback != null) {
            mPairedCallback.onPaired(mPublicAddress != null ? mPublicAddress : mBleAddress);
        }
    }

    private void callbackOnGetAddress(String address) {
        if (mOnGetBluetoothAddressCallback != null) {
            mOnGetBluetoothAddressCallback.onGetBluetoothAddress(address);
        }
    }

    private boolean validateBluetoothGattCharacteristic(
            BluetoothGattConnection connection, UUID characteristicUUID) {
        try (ScopedTiming scopedTiming =
                new ScopedTiming(mTimingLogger, "Get service characteristic list")) {
            List<BluetoothGattCharacteristic> serviceCharacteristicList =
                    connection.getService(FastPairService.ID).getCharacteristics();
            for (BluetoothGattCharacteristic characteristic : serviceCharacteristicList) {
                if (characteristicUUID.equals(characteristic.getUuid())) {
                    Log.i(TAG, "characteristic is exists, uuid = " + characteristicUUID);
                    return true;
                }
            }
        } catch (BluetoothException e) {
            Log.w(TAG, "Can't get service characteristic list.", e);
        }
        Log.i(TAG, "can't find characteristic, uuid = " + characteristicUUID);
        return false;
    }

    // This method is only for testing to make test method block until get name response or time
    // out.
    /**
     * Set name response countdown latch.
     */
    public void setNameResponseCountDownLatch(CountDownLatch countDownLatch) {
        if (mDeviceNameReceiver != null) {
            mDeviceNameReceiver.setCountDown(countDownLatch);
            Log.v(TAG, "set up nameResponseCountDown");
        }
    }

    private static int getBleState(android.bluetooth.BluetoothAdapter bluetoothAdapter) {
        // Can't use the public isLeEnabled() API, because it returns false for
        // STATE_BLE_TURNING_(ON|OFF). So if we assume false == STATE_OFF, that can be
        // very wrong.
        return getLeState(bluetoothAdapter);
    }

    private static int getLeState(android.bluetooth.BluetoothAdapter adapter) {
        try {
            return (Integer) Reflect.on(adapter).withMethod("getLeState").get();
        } catch (ReflectionException e) {
            Log.i(TAG, "Can't call getLeState", e);
        }
        return adapter.getState();
    }

    private static void disableBle(android.bluetooth.BluetoothAdapter adapter) {
        adapter.disableBLE();
    }

    /**
     * Handle the searching of Fast Pair history. Since there is only one public address using
     * during Fast Pair connection, {@link #isInPairedHistory(String)} only needs to be called once,
     * then the result is kept, and call {@link #getExistingAccountKey()} to get the result.
     */
    @VisibleForTesting
    static final class FastPairHistoryFinder {

        private @Nullable
        byte[] mExistingAccountKey;
        @Nullable
        private final List<FastPairHistoryItem> mHistoryItems;

        FastPairHistoryFinder(List<FastPairHistoryItem> historyItems) {
            this.mHistoryItems = historyItems;
        }

        @WorkerThread
        @VisibleForTesting
        boolean isInPairedHistory(String publicAddress) {
            if (mHistoryItems == null || mHistoryItems.isEmpty()) {
                return false;
            }
            for (FastPairHistoryItem item : mHistoryItems) {
                if (item.isMatched(BluetoothAddress.decode(publicAddress))) {
                    mExistingAccountKey = item.accountKey().toByteArray();
                    return true;
                }
            }
            return false;
        }

        // This function should be called after isInPairedHistory(). Or it will just return null.
        @WorkerThread
        @VisibleForTesting
        @Nullable
        byte[] getExistingAccountKey() {
            return mExistingAccountKey;
        }
    }

    private static final class DeviceNameReceiver {

        @GuardedBy("this")
        private @Nullable
        byte[] mEncryptedResponse;

        @GuardedBy("this")
        @Nullable
        private String mDecryptedDeviceName;

        @Nullable
        private CountDownLatch mResponseCountDown;

        DeviceNameReceiver(BluetoothGattConnection gattConnection) throws BluetoothException {
            UUID characteristicUuid = NameCharacteristic.getId(gattConnection);
            ChangeObserver observer =
                    gattConnection.enableNotification(FastPairService.ID, characteristicUuid);
            observer.setListener(
                    (byte[] value) -> {
                        synchronized (DeviceNameReceiver.this) {
                            Log.i(TAG, "DeviceNameReceiver: device name response size = "
                                    + value.length);
                            // We don't decrypt it here because we may not finish handshaking and
                            // the pairing
                            // secret is not available.
                            mEncryptedResponse = value;
                        }
                        // For testing to know we get the device name from provider.
                        if (mResponseCountDown != null) {
                            mResponseCountDown.countDown();
                            Log.v(TAG, "Finish nameResponseCountDown.");
                        }
                    });
        }

        void setCountDown(CountDownLatch countDownLatch) {
            this.mResponseCountDown = countDownLatch;
        }

        synchronized @Nullable String getParsedResult(byte[] secret) {
            if (mDecryptedDeviceName != null) {
                return mDecryptedDeviceName;
            }
            if (mEncryptedResponse == null) {
                Log.i(TAG, "DeviceNameReceiver: no device name sent from the Provider.");
                return null;
            }
            try {
                mDecryptedDeviceName = NamingEncoder.decodeNamingPacket(secret, mEncryptedResponse);
                Log.i(TAG, "DeviceNameReceiver: decrypted provider's name from naming response, "
                        + "name = " + mDecryptedDeviceName);
            } catch (GeneralSecurityException e) {
                Log.w(TAG, "DeviceNameReceiver: fail to parse the NameCharacteristic from provider"
                        + ".", e);
                return null;
            }
            return mDecryptedDeviceName;
        }
    }

    static void checkFastPairSignal(
            FastPairSignalChecker fastPairSignalChecker,
            String currentAddress,
            Exception originalException)
            throws SignalLostException, SignalRotatedException {
        String newAddress = fastPairSignalChecker.getValidAddressForModelId(currentAddress);
        if (TextUtils.isEmpty(newAddress)) {
            throw new SignalLostException("Signal lost", originalException);
        } else if (!Ascii.equalsIgnoreCase(currentAddress, newAddress)) {
            throw new SignalRotatedException("Address rotated", newAddress, originalException);
        }
    }

    @VisibleForTesting
    public Preferences getPreferences() {
        return mPreferences;
    }
}
