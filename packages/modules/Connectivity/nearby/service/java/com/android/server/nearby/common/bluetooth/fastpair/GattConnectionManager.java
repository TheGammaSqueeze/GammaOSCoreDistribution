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

import static com.android.server.nearby.common.bluetooth.fastpair.AesEcbSingleBlockEncryption.AES_BLOCK_LENGTH;
import static com.android.server.nearby.common.bluetooth.fastpair.BluetoothAddress.maskBluetoothAddress;
import static com.android.server.nearby.common.bluetooth.fastpair.FastPairDualConnection.logRetrySuccessEvent;

import static com.google.common.base.Preconditions.checkNotNull;

import android.content.Context;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.core.util.Consumer;

import com.android.server.nearby.common.bluetooth.BluetoothException;
import com.android.server.nearby.common.bluetooth.BluetoothGattException;
import com.android.server.nearby.common.bluetooth.BluetoothTimeoutException;
import com.android.server.nearby.common.bluetooth.fastpair.TimingLogger.ScopedTiming;
import com.android.server.nearby.common.bluetooth.gatt.BluetoothGattConnection;
import com.android.server.nearby.common.bluetooth.gatt.BluetoothGattHelper;
import com.android.server.nearby.common.bluetooth.gatt.BluetoothGattHelper.ConnectionOptions;
import com.android.server.nearby.common.bluetooth.testability.android.bluetooth.BluetoothAdapter;
import com.android.server.nearby.common.bluetooth.util.BluetoothOperationExecutor.BluetoothOperationTimeoutException;
import com.android.server.nearby.intdefs.FastPairEventIntDefs.ErrorCode;
import com.android.server.nearby.intdefs.NearbyEventIntDefs.EventCode;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Manager for working with Gatt connections.
 *
 * <p>This helper class allows for opening and closing GATT connections to a provided address.
 * Optionally, it can also support automatically reopening a connection in the case that it has been
 * closed when it's next needed through {@link Preferences#getAutomaticallyReconnectGattWhenNeeded}.
 */
// TODO(b/202524672): Add class unit test.
final class GattConnectionManager {

    private static final String TAG = GattConnectionManager.class.getSimpleName();

    private final Context mContext;
    private final Preferences mPreferences;
    private final EventLoggerWrapper mEventLogger;
    private final BluetoothAdapter mBluetoothAdapter;
    private final ToggleBluetoothTask mToggleBluetooth;
    private final String mAddress;
    private final TimingLogger mTimingLogger;
    private final boolean mSetMtu;
    @Nullable
    private final FastPairConnection.FastPairSignalChecker mFastPairSignalChecker;
    @Nullable
    private BluetoothGattConnection mGattConnection;
    private static boolean sTestMode = false;

    static void enableTestMode() {
        sTestMode = true;
    }

    GattConnectionManager(
            Context context,
            Preferences preferences,
            EventLoggerWrapper eventLogger,
            BluetoothAdapter bluetoothAdapter,
            ToggleBluetoothTask toggleBluetooth,
            String address,
            TimingLogger timingLogger,
            @Nullable FastPairConnection.FastPairSignalChecker fastPairSignalChecker,
            boolean setMtu) {
        this.mContext = context;
        this.mPreferences = preferences;
        this.mEventLogger = eventLogger;
        this.mBluetoothAdapter = bluetoothAdapter;
        this.mToggleBluetooth = toggleBluetooth;
        this.mAddress = address;
        this.mTimingLogger = timingLogger;
        this.mFastPairSignalChecker = fastPairSignalChecker;
        this.mSetMtu = setMtu;
    }

    /**
     * Gets a gatt connection to address. If this connection does not exist, it creates one.
     */
    BluetoothGattConnection getConnection()
            throws InterruptedException, ExecutionException, TimeoutException, BluetoothException {
        if (mGattConnection == null) {
            try {
                mGattConnection =
                        connect(mAddress, /* checkSignalWhenFail= */ false,
                                /* rescueFromError= */ null);
            } catch (SignalLostException | SignalRotatedException e) {
                // Impossible to happen here because we didn't do signal check.
                throw new ExecutionException("getConnection throws SignalLostException", e);
            }
        }
        return mGattConnection;
    }

    BluetoothGattConnection getConnectionWithSignalLostCheck(
            @Nullable Consumer<Integer> rescueFromError)
            throws InterruptedException, ExecutionException, TimeoutException, BluetoothException,
            SignalLostException, SignalRotatedException {
        if (mGattConnection == null) {
            mGattConnection = connect(mAddress, /* checkSignalWhenFail= */ true,
                    rescueFromError);
        }
        return mGattConnection;
    }

    /**
     * Closes the gatt connection when it is open.
     */
    void closeConnection() throws BluetoothException {
        if (mGattConnection != null) {
            try (ScopedTiming scopedTiming = new ScopedTiming(mTimingLogger, "Close GATT")) {
                mGattConnection.close();
                mGattConnection = null;
            }
        }
    }

    private BluetoothGattConnection connect(
            String address, boolean checkSignalWhenFail,
            @Nullable Consumer<Integer> rescueFromError)
            throws InterruptedException, ExecutionException, TimeoutException, BluetoothException,
            SignalLostException, SignalRotatedException {
        int i = 1;
        boolean isRecoverable = true;
        long startElapsedRealtime = SystemClock.elapsedRealtime();
        BluetoothException lastException = null;
        mEventLogger.setCurrentEvent(EventCode.GATT_CONNECT);
        while (isRecoverable) {
            try (ScopedTiming scopedTiming =
                    new ScopedTiming(mTimingLogger, "Connect GATT #" + i)) {
                Log.i(TAG, "Connecting to GATT server at " + maskBluetoothAddress(address));
                if (sTestMode) {
                    return null;
                }
                BluetoothGattConnection connection =
                        new BluetoothGattHelper(mContext, mBluetoothAdapter)
                                .connect(
                                        mBluetoothAdapter.getRemoteDevice(address),
                                        getConnectionOptions(startElapsedRealtime));
                connection.setOperationTimeout(
                        TimeUnit.SECONDS.toMillis(mPreferences.getGattOperationTimeoutSeconds()));
                if (mPreferences.getAutomaticallyReconnectGattWhenNeeded()) {
                    connection.addCloseListener(
                            () -> {
                                Log.i(TAG, "Gatt connection with " + maskBluetoothAddress(address)
                                        + " closed.");
                                mGattConnection = null;
                            });
                }
                mEventLogger.logCurrentEventSucceeded();
                if (lastException != null) {
                    logRetrySuccessEvent(EventCode.RECOVER_BY_RETRY_GATT, lastException,
                            mEventLogger);
                }
                return connection;
            } catch (BluetoothException e) {
                lastException = e;

                boolean ableToRetry;
                if (mPreferences.getGattConnectRetryTimeoutMillis() > 0) {
                    ableToRetry =
                            (SystemClock.elapsedRealtime() - startElapsedRealtime)
                                    < mPreferences.getGattConnectRetryTimeoutMillis();
                    Log.i(TAG, "Retry connecting GATT by timeout: " + ableToRetry);
                } else {
                    ableToRetry = i < mPreferences.getNumAttempts();
                }

                if (mPreferences.getRetryGattConnectionAndSecretHandshake()) {
                    if (isNoRetryError(mPreferences, e)) {
                        ableToRetry = false;
                    }

                    if (ableToRetry) {
                        if (rescueFromError != null) {
                            rescueFromError.accept(
                                    e instanceof BluetoothOperationTimeoutException
                                            ? ErrorCode.SUCCESS_RETRY_GATT_TIMEOUT
                                            : ErrorCode.SUCCESS_RETRY_GATT_ERROR);
                        }
                        if (mFastPairSignalChecker != null && checkSignalWhenFail) {
                            FastPairDualConnection
                                    .checkFastPairSignal(mFastPairSignalChecker, address, e);
                        }
                    }
                    isRecoverable = ableToRetry;
                    if (ableToRetry && mPreferences.getPairingRetryDelayMs() > 0) {
                        SystemClock.sleep(mPreferences.getPairingRetryDelayMs());
                    }
                } else {
                    isRecoverable =
                            ableToRetry
                                    && (e instanceof BluetoothOperationTimeoutException
                                    || e instanceof BluetoothTimeoutException
                                    || (e instanceof BluetoothGattException
                                    && ((BluetoothGattException) e).getGattErrorCode() == 133));
                }
                Log.w(TAG, "GATT connect attempt " + i + "of " + mPreferences.getNumAttempts()
                        + " failed, " + (isRecoverable ? "recovering" : "permanently"), e);
                if (isRecoverable) {
                    // If we're going to retry, log failure here. If we throw, an upper level will
                    // log it.
                    mToggleBluetooth.toggleBluetooth();
                    i++;
                    mEventLogger.logCurrentEventFailed(e);
                    mEventLogger.setCurrentEvent(EventCode.GATT_CONNECT);
                }
            }
        }
        throw checkNotNull(lastException);
    }

    static boolean isNoRetryError(Preferences preferences, BluetoothException e) {
        return e instanceof BluetoothGattException
                && preferences
                .getGattConnectionAndSecretHandshakeNoRetryGattError()
                .contains(((BluetoothGattException) e).getGattErrorCode());
    }

    @VisibleForTesting
    long getTimeoutMs(long spentTime) {
        long timeoutInMs;
        if (mPreferences.getRetryGattConnectionAndSecretHandshake()) {
            timeoutInMs =
                    spentTime < mPreferences.getGattConnectShortTimeoutRetryMaxSpentTimeMs()
                            ? mPreferences.getGattConnectShortTimeoutMs()
                            : mPreferences.getGattConnectLongTimeoutMs();
        } else {
            timeoutInMs = TimeUnit.SECONDS.toMillis(mPreferences.getGattConnectionTimeoutSeconds());
        }
        return timeoutInMs;
    }

    private ConnectionOptions getConnectionOptions(long startElapsedRealtime) {
        return createConnectionOptions(
                mSetMtu,
                getTimeoutMs(SystemClock.elapsedRealtime() - startElapsedRealtime));
    }

    public static ConnectionOptions createConnectionOptions(boolean setMtu, long timeoutInMs) {
        ConnectionOptions.Builder builder = ConnectionOptions.builder();
        if (setMtu) {
            // There are 3 overhead bytes added to BLE packets.
            builder.setMtu(
                    AES_BLOCK_LENGTH + EllipticCurveDiffieHellmanExchange.PUBLIC_KEY_LENGTH + 3);
        }
        builder.setConnectionTimeoutMillis(timeoutInMs);
        return builder.build();
    }

    @VisibleForTesting
    void setGattConnection(BluetoothGattConnection gattConnection) {
        this.mGattConnection = gattConnection;
    }
}
