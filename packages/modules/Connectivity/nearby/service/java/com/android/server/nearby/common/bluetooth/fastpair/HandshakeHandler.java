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
import static com.android.server.nearby.common.bluetooth.fastpair.AesEcbSingleBlockEncryption.decrypt;
import static com.android.server.nearby.common.bluetooth.fastpair.AesEcbSingleBlockEncryption.encrypt;
import static com.android.server.nearby.common.bluetooth.fastpair.BluetoothAddress.maskBluetoothAddress;
import static com.android.server.nearby.common.bluetooth.fastpair.Constants.BLUETOOTH_ADDRESS_LENGTH;
import static com.android.server.nearby.common.bluetooth.fastpair.Constants.FastPairService.KeyBasedPairingCharacteristic.ActionOverBleFlag.ADDITIONAL_DATA_CHARACTERISTIC;
import static com.android.server.nearby.common.bluetooth.fastpair.Constants.FastPairService.KeyBasedPairingCharacteristic.ActionOverBleFlag.DEVICE_ACTION;
import static com.android.server.nearby.common.bluetooth.fastpair.Constants.FastPairService.KeyBasedPairingCharacteristic.Request.ADDITIONAL_DATA_TYPE_INDEX;
import static com.android.server.nearby.common.bluetooth.fastpair.Constants.FastPairService.KeyBasedPairingCharacteristic.Request.EVENT_ADDITIONAL_DATA_INDEX;
import static com.android.server.nearby.common.bluetooth.fastpair.Constants.FastPairService.KeyBasedPairingCharacteristic.Request.EVENT_ADDITIONAL_DATA_LENGTH_INDEX;
import static com.android.server.nearby.common.bluetooth.fastpair.Constants.FastPairService.KeyBasedPairingCharacteristic.Request.EVENT_CODE_INDEX;
import static com.android.server.nearby.common.bluetooth.fastpair.Constants.FastPairService.KeyBasedPairingCharacteristic.Request.EVENT_GROUP_INDEX;
import static com.android.server.nearby.common.bluetooth.fastpair.Constants.FastPairService.KeyBasedPairingCharacteristic.Request.FLAGS_INDEX;
import static com.android.server.nearby.common.bluetooth.fastpair.Constants.FastPairService.KeyBasedPairingCharacteristic.Request.SEEKER_PUBLIC_ADDRESS_INDEX;
import static com.android.server.nearby.common.bluetooth.fastpair.Constants.FastPairService.KeyBasedPairingCharacteristic.Request.TYPE_ACTION_OVER_BLE;
import static com.android.server.nearby.common.bluetooth.fastpair.Constants.FastPairService.KeyBasedPairingCharacteristic.Request.TYPE_INDEX;
import static com.android.server.nearby.common.bluetooth.fastpair.Constants.FastPairService.KeyBasedPairingCharacteristic.Request.TYPE_KEY_BASED_PAIRING_REQUEST;
import static com.android.server.nearby.common.bluetooth.fastpair.Constants.FastPairService.KeyBasedPairingCharacteristic.Request.VERIFICATION_DATA_INDEX;
import static com.android.server.nearby.common.bluetooth.fastpair.Constants.FastPairService.KeyBasedPairingCharacteristic.Request.VERIFICATION_DATA_LENGTH;
import static com.android.server.nearby.common.bluetooth.fastpair.FastPairDualConnection.logRetrySuccessEvent;
import static com.android.server.nearby.common.bluetooth.fastpair.GattConnectionManager.isNoRetryError;

import static com.google.common.base.Verify.verifyNotNull;
import static com.google.common.io.BaseEncoding.base16;
import static com.google.common.primitives.Bytes.concat;

import static java.util.concurrent.TimeUnit.SECONDS;

import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.core.util.Consumer;

import com.android.server.nearby.common.bluetooth.BluetoothException;
import com.android.server.nearby.common.bluetooth.BluetoothTimeoutException;
import com.android.server.nearby.common.bluetooth.fastpair.Constants.FastPairService;
import com.android.server.nearby.common.bluetooth.fastpair.Constants.FastPairService.AdditionalDataCharacteristic.AdditionalDataType;
import com.android.server.nearby.common.bluetooth.fastpair.Constants.FastPairService.KeyBasedPairingCharacteristic;
import com.android.server.nearby.common.bluetooth.fastpair.Constants.FastPairService.KeyBasedPairingCharacteristic.ActionOverBleFlag;
import com.android.server.nearby.common.bluetooth.fastpair.Constants.FastPairService.KeyBasedPairingCharacteristic.KeyBasedPairingRequestFlag;
import com.android.server.nearby.common.bluetooth.fastpair.Constants.FastPairService.KeyBasedPairingCharacteristic.Request;
import com.android.server.nearby.common.bluetooth.fastpair.FastPairConnection.SharedSecret;
import com.android.server.nearby.common.bluetooth.gatt.BluetoothGattConnection;
import com.android.server.nearby.common.bluetooth.gatt.BluetoothGattConnection.ChangeObserver;
import com.android.server.nearby.common.bluetooth.util.BluetoothOperationExecutor.BluetoothOperationTimeoutException;
import com.android.server.nearby.intdefs.FastPairEventIntDefs.ErrorCode;
import com.android.server.nearby.intdefs.NearbyEventIntDefs.EventCode;

import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

/**
 * Handles the handshake step of Fast Pair, the Provider's public address and the shared secret will
 * be disclosed during this step. It is the first step of all key-based operations, e.g. key-based
 * pairing and action over BLE.
 *
 * @see <a href="https://developers.google.com/nearby/fast-pair/spec#procedure">
 *     Fastpair Spec Procedure</a>
 */
public class HandshakeHandler {

    private static final String TAG = HandshakeHandler.class.getSimpleName();
    private final GattConnectionManager mGattConnectionManager;
    private final String mProviderBleAddress;
    private final Preferences mPreferences;
    private final EventLoggerWrapper mEventLogger;
    @Nullable
    private final FastPairConnection.FastPairSignalChecker mFastPairSignalChecker;

    /**
     * Keeps the keys used during handshaking, generated by {@link #createKey(byte[])}.
     */
    private static final class Keys {

        private final byte[] mSharedSecret;
        private final byte[] mPublicKey;

        private Keys(byte[] sharedSecret, byte[] publicKey) {
            this.mSharedSecret = sharedSecret;
            this.mPublicKey = publicKey;
        }
    }

    public HandshakeHandler(
            GattConnectionManager gattConnectionManager,
            String bleAddress,
            Preferences preferences,
            EventLoggerWrapper eventLogger,
            @Nullable FastPairConnection.FastPairSignalChecker fastPairSignalChecker) {
        this.mGattConnectionManager = gattConnectionManager;
        this.mProviderBleAddress = bleAddress;
        this.mPreferences = preferences;
        this.mEventLogger = eventLogger;
        this.mFastPairSignalChecker = fastPairSignalChecker;
    }

    /**
     * Performs a handshake to authenticate and get the remote device's public address. Returns the
     * AES-128 key as the shared secret for this pairing session.
     */
    public SharedSecret doHandshake(byte[] key, HandshakeMessage message)
            throws GeneralSecurityException, InterruptedException, ExecutionException,
            TimeoutException, BluetoothException, PairingException {
        Keys keys = createKey(key);
        Log.i(TAG,
                "Handshake " + maskBluetoothAddress(mProviderBleAddress) + ", flags "
                        + message.mFlags);
        byte[] handshakeResponse =
                processGattCommunication(
                        createPacket(keys, message),
                        SECONDS.toMillis(mPreferences.getGattOperationTimeoutSeconds()));
        String providerPublicAddress = decodeResponse(keys.mSharedSecret, handshakeResponse);

        return SharedSecret.create(keys.mSharedSecret, providerPublicAddress);
    }

    /**
     * Performs a handshake to authenticate and get the remote device's public address. Returns the
     * AES-128 key as the shared secret for this pairing session. Will retry and also performs
     * FastPair signal check if fails.
     */
    public SharedSecret doHandshakeWithRetryAndSignalLostCheck(
            byte[] key, HandshakeMessage message, @Nullable Consumer<Integer> rescueFromError)
            throws GeneralSecurityException, InterruptedException, ExecutionException,
            TimeoutException, BluetoothException, PairingException {
        Keys keys = createKey(key);
        Log.i(TAG,
                "Handshake " + maskBluetoothAddress(mProviderBleAddress) + ", flags "
                        + message.mFlags);
        int retryCount = 0;
        byte[] handshakeResponse = null;
        long startTime = SystemClock.elapsedRealtime();
        BluetoothException lastException = null;
        do {
            try {
                mEventLogger.setCurrentEvent(EventCode.SECRET_HANDSHAKE_GATT_COMMUNICATION);
                handshakeResponse =
                        processGattCommunication(
                                createPacket(keys, message),
                                getTimeoutMs(SystemClock.elapsedRealtime() - startTime));
                mEventLogger.logCurrentEventSucceeded();
                if (lastException != null) {
                    logRetrySuccessEvent(EventCode.RECOVER_BY_RETRY_HANDSHAKE, lastException,
                            mEventLogger);
                }
            } catch (BluetoothException e) {
                lastException = e;
                long spentTime = SystemClock.elapsedRealtime() - startTime;
                Log.w(TAG, "Secret handshake failed, address="
                        + maskBluetoothAddress(mProviderBleAddress)
                        + ", spent time=" + spentTime + "ms, retryCount=" + retryCount);
                mEventLogger.logCurrentEventFailed(e);

                if (!mPreferences.getRetryGattConnectionAndSecretHandshake()) {
                    throw e;
                }

                if (spentTime > mPreferences.getSecretHandshakeLongTimeoutRetryMaxSpentTimeMs()) {
                    Log.w(TAG, "Spent too long time for handshake, timeInMs=" + spentTime);
                    throw e;
                }
                if (isNoRetryError(mPreferences, e)) {
                    throw e;
                }

                if (mFastPairSignalChecker != null) {
                    FastPairDualConnection
                            .checkFastPairSignal(mFastPairSignalChecker, mProviderBleAddress, e);
                }
                retryCount++;
                if (retryCount > mPreferences.getSecretHandshakeRetryAttempts()
                        || ((e instanceof BluetoothOperationTimeoutException)
                        && !mPreferences.getRetrySecretHandshakeTimeout())) {
                    throw new HandshakeException("Fail on handshake!", e);
                }
                if (rescueFromError != null) {
                    rescueFromError.accept(
                            (e instanceof BluetoothTimeoutException
                                    || e instanceof BluetoothOperationTimeoutException)
                                    ? ErrorCode.SUCCESS_RETRY_SECRET_HANDSHAKE_TIMEOUT
                                    : ErrorCode.SUCCESS_RETRY_SECRET_HANDSHAKE_ERROR);
                }
            }
        } while (mPreferences.getRetryGattConnectionAndSecretHandshake()
                && handshakeResponse == null);
        if (retryCount > 0) {
            Log.i(TAG, "Secret handshake failed but restored by retry, retry count=" + retryCount);
        }
        String providerPublicAddress =
                decodeResponse(keys.mSharedSecret, verifyNotNull(handshakeResponse));

        return SharedSecret.create(keys.mSharedSecret, providerPublicAddress);
    }

    @VisibleForTesting
    long getTimeoutMs(long spentTime) {
        if (!mPreferences.getRetryGattConnectionAndSecretHandshake()) {
            return SECONDS.toMillis(mPreferences.getGattOperationTimeoutSeconds());
        } else {
            return spentTime < mPreferences.getSecretHandshakeShortTimeoutRetryMaxSpentTimeMs()
                    ? mPreferences.getSecretHandshakeShortTimeoutMs()
                    : mPreferences.getSecretHandshakeLongTimeoutMs();
        }
    }

    /**
     * If the given key is an ecc-256 public key (currently, we are using secp256r1), the shared
     * secret is generated by ECDH; if the input key is AES-128 key (should be the account key),
     * then it is the shared secret.
     */
    private Keys createKey(byte[] key) throws GeneralSecurityException {
        if (key.length == EllipticCurveDiffieHellmanExchange.PUBLIC_KEY_LENGTH) {
            EllipticCurveDiffieHellmanExchange exchange = EllipticCurveDiffieHellmanExchange
                    .create();
            byte[] publicKey = exchange.getPublicKey();
            if (publicKey != null) {
                Log.i(TAG, "Handshake " + maskBluetoothAddress(mProviderBleAddress)
                        + ", generates key by ECDH.");
            } else {
                throw new GeneralSecurityException("Failed to do ECDH.");
            }
            return new Keys(exchange.generateSecret(key), publicKey);
        } else if (key.length == AesEcbSingleBlockEncryption.KEY_LENGTH) {
            Log.i(TAG, "Handshake " + maskBluetoothAddress(mProviderBleAddress)
                    + ", using the given secret.");
            return new Keys(key, new byte[0]);
        } else {
            throw new GeneralSecurityException("Key length is not correct: " + key.length);
        }
    }

    private static byte[] createPacket(Keys keys, HandshakeMessage message)
            throws GeneralSecurityException {
        byte[] encryptedMessage = encrypt(keys.mSharedSecret, message.getBytes());
        return concat(encryptedMessage, keys.mPublicKey);
    }

    private byte[] processGattCommunication(byte[] packet, long gattOperationTimeoutMS)
            throws BluetoothException, InterruptedException, ExecutionException, TimeoutException {
        BluetoothGattConnection gattConnection = mGattConnectionManager.getConnection();
        gattConnection.setOperationTimeout(gattOperationTimeoutMS);
        UUID characteristicUuid = KeyBasedPairingCharacteristic.getId(gattConnection);
        ChangeObserver changeObserver =
                gattConnection.enableNotification(FastPairService.ID, characteristicUuid);

        Log.i(TAG,
                "Writing handshake packet to address=" + maskBluetoothAddress(mProviderBleAddress));
        gattConnection.writeCharacteristic(FastPairService.ID, characteristicUuid, packet);
        Log.i(TAG, "Waiting handshake packet from address=" + maskBluetoothAddress(
                mProviderBleAddress));
        return changeObserver.waitForUpdate(gattOperationTimeoutMS);
    }

    private String decodeResponse(byte[] sharedSecret, byte[] response)
            throws PairingException, GeneralSecurityException {
        if (response.length != AES_BLOCK_LENGTH) {
            throw new PairingException(
                    "Handshake failed because of incorrect response: " + base16().encode(response));
        }
        // 1 byte type, 6 bytes public address, remainder random salt.
        byte[] decryptedResponse = decrypt(sharedSecret, response);
        if (decryptedResponse[0] != KeyBasedPairingCharacteristic.Response.TYPE) {
            throw new PairingException(
                    "Handshake response type incorrect: " + decryptedResponse[0]);
        }
        String address = BluetoothAddress.encode(Arrays.copyOfRange(decryptedResponse, 1, 7));
        Log.i(TAG, "Handshake success with public " + maskBluetoothAddress(address) + ", ble "
                + maskBluetoothAddress(mProviderBleAddress));
        return address;
    }

    /**
     * The base class for handshake message that contains the common data: message type, flags and
     * verification data.
     */
    abstract static class HandshakeMessage {

        final byte mType;
        final byte mFlags;
        private final byte[] mVerificationData;

        HandshakeMessage(Builder<?> builder) {
            this.mType = builder.mType;
            this.mVerificationData = builder.mVerificationData;
            this.mFlags = builder.mFlags;
        }

        abstract static class Builder<T extends Builder<T>> {

            byte mType;
            byte mFlags;
            private byte[] mVerificationData;

            abstract T getThis();

            T setVerificationData(byte[] verificationData) {
                if (verificationData.length != BLUETOOTH_ADDRESS_LENGTH) {
                    throw new IllegalArgumentException(
                            "Incorrect verification data length: " + verificationData.length + ".");
                }
                this.mVerificationData = verificationData;
                return getThis();
            }
        }

        /**
         * Constructs the base handshake message according to the format of Fast Pair spec.
         */
        byte[] constructBaseBytes() {
            byte[] rawMessage = new byte[Request.SIZE];
            new SecureRandom().nextBytes(rawMessage);
            rawMessage[TYPE_INDEX] = mType;
            rawMessage[FLAGS_INDEX] = mFlags;

            System.arraycopy(
                    mVerificationData,
                    /* srcPos= */ 0,
                    rawMessage,
                    VERIFICATION_DATA_INDEX,
                    VERIFICATION_DATA_LENGTH);
            return rawMessage;
        }

        /**
         * Returns the raw handshake message.
         */
        abstract byte[] getBytes();
    }

    /**
     * Extends {@link HandshakeMessage} and contains the required data for key-based pairing
     * request.
     */
    public static class KeyBasedPairingRequest extends HandshakeMessage {

        @Nullable
        private final byte[] mSeekerPublicAddress;

        private KeyBasedPairingRequest(Builder builder) {
            super(builder);
            this.mSeekerPublicAddress = builder.mSeekerPublicAddress;
        }

        @Override
        byte[] getBytes() {
            byte[] rawMessage = constructBaseBytes();
            if (mSeekerPublicAddress != null) {
                System.arraycopy(
                        mSeekerPublicAddress,
                        /* srcPos= */ 0,
                        rawMessage,
                        SEEKER_PUBLIC_ADDRESS_INDEX,
                        BLUETOOTH_ADDRESS_LENGTH);
            }
            Log.i(TAG,
                    "Handshake Message: type (" + rawMessage[TYPE_INDEX] + "), flag ("
                            + rawMessage[FLAGS_INDEX] + ").");
            return rawMessage;
        }

        /**
         * Builder class for key-based pairing request.
         */
        public static class Builder extends HandshakeMessage.Builder<Builder> {

            @Nullable
            private byte[] mSeekerPublicAddress;

            /**
             * Adds flags without changing other flags.
             */
            public Builder addFlag(@KeyBasedPairingRequestFlag int flag) {
                this.mFlags |= (byte) flag;
                return this;
            }

            /**
             * Set seeker's public address.
             */
            public Builder setSeekerPublicAddress(byte[] seekerPublicAddress) {
                this.mSeekerPublicAddress = seekerPublicAddress;
                return this;
            }

            /**
             * Buulds KeyBasedPairigRequest.
             */
            public KeyBasedPairingRequest build() {
                mType = TYPE_KEY_BASED_PAIRING_REQUEST;
                return new KeyBasedPairingRequest(this);
            }

            @Override
            Builder getThis() {
                return this;
            }
        }
    }

    /**
     * Extends {@link HandshakeMessage} and contains the required data for action over BLE request.
     */
    public static class ActionOverBle extends HandshakeMessage {

        private final byte mEventGroup;
        private final byte mEventCode;
        @Nullable
        private final byte[] mEventData;
        private final byte mAdditionalDataType;

        private ActionOverBle(Builder builder) {
            super(builder);
            this.mEventGroup = builder.mEventGroup;
            this.mEventCode = builder.mEventCode;
            this.mEventData = builder.mEventData;
            this.mAdditionalDataType = builder.mAdditionalDataType;
        }

        @Override
        byte[] getBytes() {
            byte[] rawMessage = constructBaseBytes();
            StringBuilder stringBuilder =
                    new StringBuilder(
                            String.format(
                                    "type (%02X), flag (%02X)", rawMessage[TYPE_INDEX],
                                    rawMessage[FLAGS_INDEX]));
            if ((mFlags & (byte) DEVICE_ACTION) != 0) {
                rawMessage[EVENT_GROUP_INDEX] = mEventGroup;
                rawMessage[EVENT_CODE_INDEX] = mEventCode;

                if (mEventData != null) {
                    rawMessage[EVENT_ADDITIONAL_DATA_LENGTH_INDEX] = (byte) mEventData.length;
                    System.arraycopy(
                            mEventData,
                            /* srcPos= */ 0,
                            rawMessage,
                            EVENT_ADDITIONAL_DATA_INDEX,
                            mEventData.length);
                } else {
                    rawMessage[EVENT_ADDITIONAL_DATA_LENGTH_INDEX] = (byte) 0;
                }
                stringBuilder.append(
                        String.format(
                                ", group(%02X), code(%02X), length(%02X)",
                                rawMessage[EVENT_GROUP_INDEX],
                                rawMessage[EVENT_CODE_INDEX],
                                rawMessage[EVENT_ADDITIONAL_DATA_LENGTH_INDEX]));
            }
            if ((mFlags & (byte) ADDITIONAL_DATA_CHARACTERISTIC) != 0) {
                rawMessage[ADDITIONAL_DATA_TYPE_INDEX] = mAdditionalDataType;
                stringBuilder.append(
                        String.format(", data id(%02X)", rawMessage[ADDITIONAL_DATA_TYPE_INDEX]));
            }
            Log.i(TAG, "Handshake Message: " + stringBuilder);
            return rawMessage;
        }

        /**
         * Builder class for action over BLE request.
         */
        public static class Builder extends HandshakeMessage.Builder<Builder> {

            private byte mEventGroup;
            private byte mEventCode;
            @Nullable
            private byte[] mEventData;
            private byte mAdditionalDataType;

            // Adds a flag to this handshake message. This can be called repeatedly for adding
            // different preference.

            /**
             * Adds flag without changing other flags.
             */
            public Builder addFlag(@ActionOverBleFlag int flag) {
                this.mFlags |= (byte) flag;
                return this;
            }

            /**
             * Set event group and event code.
             */
            public Builder setEvent(int eventGroup, int eventCode) {
                this.mFlags |= (byte) DEVICE_ACTION;
                this.mEventGroup = (byte) (eventGroup & 0xFF);
                this.mEventCode = (byte) (eventCode & 0xFF);
                return this;
            }

            /**
             * Set event additional data.
             */
            public Builder setEventAdditionalData(byte[] data) {
                this.mEventData = data;
                return this;
            }

            /**
             * Set event additional data type.
             */
            public Builder setAdditionalDataType(@AdditionalDataType int additionalDataType) {
                this.mFlags |= (byte) ADDITIONAL_DATA_CHARACTERISTIC;
                this.mAdditionalDataType = (byte) additionalDataType;
                return this;
            }

            @Override
            Builder getThis() {
                return this;
            }

            ActionOverBle build() {
                mType = TYPE_ACTION_OVER_BLE;
                return new ActionOverBle(this);
            }
        }
    }

    /**
     * Exception for handshake failure.
     */
    public static class HandshakeException extends PairingException {

        private final BluetoothException mOriginalException;

        @VisibleForTesting
        HandshakeException(String format, BluetoothException e) {
            super(format);
            mOriginalException = e;
        }

        public BluetoothException getOriginalException() {
            return mOriginalException;
        }
    }
}
