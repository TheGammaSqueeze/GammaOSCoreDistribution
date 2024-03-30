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

package android.nearby.fastpair.provider;

import static android.bluetooth.BluetoothAdapter.EXTRA_STATE;
import static android.bluetooth.BluetoothAdapter.SCAN_MODE_CONNECTABLE;
import static android.bluetooth.BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE;
import static android.bluetooth.BluetoothAdapter.SCAN_MODE_NONE;
import static android.bluetooth.BluetoothAdapter.STATE_OFF;
import static android.bluetooth.BluetoothAdapter.STATE_ON;
import static android.bluetooth.BluetoothDevice.ERROR;
import static android.bluetooth.BluetoothGattCharacteristic.PERMISSION_READ;
import static android.bluetooth.BluetoothGattCharacteristic.PERMISSION_WRITE;
import static android.bluetooth.BluetoothGattCharacteristic.PROPERTY_INDICATE;
import static android.bluetooth.BluetoothGattCharacteristic.PROPERTY_NOTIFY;
import static android.bluetooth.BluetoothGattCharacteristic.PROPERTY_READ;
import static android.bluetooth.BluetoothGattCharacteristic.PROPERTY_WRITE;
import static android.nearby.fastpair.provider.bluetooth.BluetoothManager.wrap;
import static android.nearby.fastpair.provider.bluetooth.RfcommServer.State.CONNECTED;

import static com.android.server.nearby.common.bluetooth.fastpair.AesEcbSingleBlockEncryption.AES_BLOCK_LENGTH;
import static com.android.server.nearby.common.bluetooth.fastpair.AesEcbSingleBlockEncryption.encrypt;
import static com.android.server.nearby.common.bluetooth.fastpair.Bytes.toBytes;
import static com.android.server.nearby.common.bluetooth.fastpair.Constants.A2DP_SINK_SERVICE_UUID;
import static com.android.server.nearby.common.bluetooth.fastpair.Constants.TransportDiscoveryService.BLUETOOTH_SIG_ORGANIZATION_ID;
import static com.android.server.nearby.common.bluetooth.fastpair.EllipticCurveDiffieHellmanExchange.PUBLIC_KEY_LENGTH;
import static com.android.server.nearby.common.bluetooth.fastpair.MessageStreamHmacEncoder.SECTION_NONCE_LENGTH;

import static com.google.common.io.BaseEncoding.base16;
import static com.google.common.primitives.Bytes.concat;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass.Device.Major;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.AdvertiseSettings;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.nearby.fastpair.provider.EventStreamProtocol.AcknowledgementEventCode;
import android.nearby.fastpair.provider.EventStreamProtocol.DeviceActionEventCode;
import android.nearby.fastpair.provider.EventStreamProtocol.DeviceCapabilitySyncEventCode;
import android.nearby.fastpair.provider.EventStreamProtocol.DeviceConfigurationEventCode;
import android.nearby.fastpair.provider.EventStreamProtocol.DeviceEventCode;
import android.nearby.fastpair.provider.EventStreamProtocol.EventGroup;
import android.nearby.fastpair.provider.bluetooth.BluetoothGattServerConfig;
import android.nearby.fastpair.provider.bluetooth.BluetoothGattServerConfig.ServiceConfig;
import android.nearby.fastpair.provider.bluetooth.BluetoothGattServerConnection;
import android.nearby.fastpair.provider.bluetooth.BluetoothGattServerConnection.Notifier;
import android.nearby.fastpair.provider.bluetooth.BluetoothGattServerHelper;
import android.nearby.fastpair.provider.bluetooth.BluetoothGattServlet;
import android.nearby.fastpair.provider.bluetooth.RfcommServer;
import android.nearby.fastpair.provider.crypto.Crypto;
import android.nearby.fastpair.provider.crypto.E2eeCalculator;
import android.nearby.fastpair.provider.utils.Logger;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.TextUtils;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.core.util.Consumer;

import com.android.server.nearby.common.bloomfilter.BloomFilter;
import com.android.server.nearby.common.bloomfilter.FastPairBloomFilterHasher;
import com.android.server.nearby.common.bluetooth.BluetoothException;
import com.android.server.nearby.common.bluetooth.BluetoothGattException;
import com.android.server.nearby.common.bluetooth.fastpair.AesEcbSingleBlockEncryption;
import com.android.server.nearby.common.bluetooth.fastpair.BluetoothAddress;
import com.android.server.nearby.common.bluetooth.fastpair.Bytes.Value;
import com.android.server.nearby.common.bluetooth.fastpair.Constants;
import com.android.server.nearby.common.bluetooth.fastpair.Constants.FastPairService;
import com.android.server.nearby.common.bluetooth.fastpair.Constants.FastPairService.AccountKeyCharacteristic;
import com.android.server.nearby.common.bluetooth.fastpair.Constants.FastPairService.BeaconActionsCharacteristic;
import com.android.server.nearby.common.bluetooth.fastpair.Constants.FastPairService.BeaconActionsCharacteristic.BeaconActionType;
import com.android.server.nearby.common.bluetooth.fastpair.Constants.FastPairService.FirmwareVersionCharacteristic;
import com.android.server.nearby.common.bluetooth.fastpair.Constants.FastPairService.KeyBasedPairingCharacteristic;
import com.android.server.nearby.common.bluetooth.fastpair.Constants.FastPairService.NameCharacteristic;
import com.android.server.nearby.common.bluetooth.fastpair.Constants.FastPairService.PasskeyCharacteristic;
import com.android.server.nearby.common.bluetooth.fastpair.Constants.TransportDiscoveryService;
import com.android.server.nearby.common.bluetooth.fastpair.Constants.TransportDiscoveryService.BrHandoverDataCharacteristic;
import com.android.server.nearby.common.bluetooth.fastpair.Constants.TransportDiscoveryService.ControlPointCharacteristic;
import com.android.server.nearby.common.bluetooth.fastpair.EllipticCurveDiffieHellmanExchange;
import com.android.server.nearby.common.bluetooth.fastpair.Ltv;
import com.android.server.nearby.common.bluetooth.fastpair.MessageStreamHmacEncoder;
import com.android.server.nearby.common.bluetooth.fastpair.NamingEncoder;

import com.google.common.base.Ascii;
import com.google.common.primitives.Bytes;
import com.google.protobuf.ByteString;

import java.lang.reflect.Method;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Simulates a Fast Pair device (e.g. a headset).
 *
 * <p>Note: There are two deviations from the spec:
 *
 * <ul>
 *   <li>Instead of using the public address when in pairing mode (discoverable), it always uses the
 *       random private address (RPA), because that's how stock Android works. To work around this,
 *       it implements the BR/EDR Handover profile (which is no longer part of the Fast Pair spec)
 *       when simulating a keyless device (i.e. Fast Pair 1.0), which allows the phone to ask for
 *       the public address. When there is an anti-spoofing key, i.e. Fast Pair 2.0, the public
 *       address is delivered via the Key-based Pairing handshake. b/79374759 tracks fixing this.
 *   <li>The simulator always identifies its device capabilities as Keyboard/Display, even when
 *       simulating a keyless (Fast Pair 1.0) device that should identify as NoInput/NoOutput.
 *       b/79377125 tracks fixing this.
 * </ul>
 *
 * @see {http://go/fast-pair-2-spec}
 */
public class FastPairSimulator {
    public static final String TAG = "FastPairSimulator";
    private final Logger mLogger;

    private static final int BECOME_DISCOVERABLE_TIMEOUT_SEC = 3;

    private static final int SCAN_MODE_REFRESH_SEC = 30;

    /**
     * Headphones. Generated by
     * http://bluetooth-pentest.narod.ru/software/bluetooth_class_of_device-service_generator.html
     */
    private static final Value CLASS_OF_DEVICE =
            new Value(base16().decode("200418"), ByteOrder.BIG_ENDIAN);

    private static final byte[] SUPPORTED_SERVICES_LTV = new Ltv(
            TransportDiscoveryService.SERVICE_UUIDS_16_BIT_LIST_TYPE,
            toBytes(ByteOrder.LITTLE_ENDIAN, A2DP_SINK_SERVICE_UUID)
    ).getBytes();
    private static final byte[] TDS_CONTROL_POINT_RESPONSE_PARAMETER =
            Bytes.concat(new byte[]{BLUETOOTH_SIG_ORGANIZATION_ID}, SUPPORTED_SERVICES_LTV);

    private static final String SIMULATOR_FAKE_BLE_ADDRESS = "11:22:33:44:55:66";

    private static final long ADVERTISING_REFRESH_DELAY_1_MIN = TimeUnit.MINUTES.toMillis(1);

    /**
     * The size of account key filter in bytes is (1.2*n + 3), n represents the size of account key,
     * see https://developers.google.com/nearby/fast-pair/spec#advertising_when_not_discoverable.
     * However we'd like to advertise something else, so we could only afford 8 account keys.
     *
     * <ul>
     *   <li>BLE flags: 3 bytes
     *   <li>TxPower: 3 bytes
     *   <li>FastPair: max 25 bytes
     *       <ul>
     *         <li>FastPair service data: 4 bytes
     *         <li>Flags: 1 byte
     *         <li>Account key filter: max 14 bytes (1 byte: length + type, 13 bytes: max 8 account
     *             keys)
     *         <li>Salt: 2 bytes
     *         <li>Battery: 4 bytes
     *       </ul>
     * </ul>
     */
    private String mDeviceFirmwareVersion = "1.1.0";

    private byte[] mSessionNonce;

    private boolean mUseLogFullEvent = true;

    private enum ResultCode {
        SUCCESS((byte) 0x00),
        OP_CODE_NOT_SUPPORTED((byte) 0x01),
        INVALID_PARAMETER((byte) 0x02),
        UNSUPPORTED_ORGANIZATION_ID((byte) 0x03),
        OPERATION_FAILED((byte) 0x04);

        private final byte mByteValue;

        ResultCode(byte byteValue) {
            this.mByteValue = byteValue;
        }
    }

    private enum TransportState {
        OFF((byte) 0x00),
        ON((byte) 0x01),
        TEMPORARILY_UNAVAILABLE((byte) 0x10);

        private final byte mByteValue;

        TransportState(byte byteValue) {
            this.mByteValue = byteValue;
        }
    }

    private final Context mContext;
    private final Options mOptions;
    private final Handler mUiThreadHandler = new Handler(Looper.getMainLooper());
    // No thread pool: Only used in test app (outside gmscore) and in javatests/.../gmscore/.
    private final ScheduledExecutorService mExecutor =
            Executors.newSingleThreadScheduledExecutor(); // exempt
    private final BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (mShouldFailPairing) {
                mLogger.log("Pairing disabled by test app switch");
                return;
            }
            if (mIsDestroyed) {
                // Sometimes this receiver does not successfully unregister in destroy()
                // which causes events to occur after the simulator is stopped, so ignore
                // those events.
                mLogger.log("Intent received after simulator destroyed, ignoring");
                return;
            }
            BluetoothDevice device = intent.getParcelableExtra(
                    BluetoothDevice.EXTRA_DEVICE);
            switch (intent.getAction()) {
                case BluetoothAdapter.ACTION_SCAN_MODE_CHANGED:
                    if (isDiscoverable()) {
                        mIsDiscoverableLatch.countDown();
                    }
                    break;
                case BluetoothDevice.ACTION_PAIRING_REQUEST:
                    int variant = intent.getIntExtra(BluetoothDevice.EXTRA_PAIRING_VARIANT,
                            ERROR);
                    int key = intent.getIntExtra(BluetoothDevice.EXTRA_PAIRING_KEY, ERROR);
                    mLogger.log(
                            "Pairing request, variant=%d, key=%s", variant,
                            key == ERROR ? "(none)" : key);

                    // Prevent Bluetooth Settings from getting the pairing request.
                    abortBroadcast();

                    mPairingDevice = device;
                    if (mSecret == null) {
                        // We haven't done the handshake over GATT to agree on the shared
                        // secret. For now, just accept anyway (so we can still simulate
                        // old 1.0 model IDs).
                        mLogger.log("No handshake, auto-accepting anyway.");
                        setPasskeyConfirmation(true);
                    } else if (variant
                            == BluetoothDevice.PAIRING_VARIANT_PASSKEY_CONFIRMATION) {
                        // Store the passkey. And check it, since there's a race (see
                        // method for why). Usually this check is a no-op and we'll get
                        // the passkey later over GATT.
                        mLocalPasskey = key;
                        checkPasskey();
                    } else if (variant == BluetoothDevice.PAIRING_VARIANT_DISPLAY_PASSKEY) {
                        if (mPasskeyEventCallback != null) {
                            mPasskeyEventCallback.onPasskeyRequested(
                                    FastPairSimulator.this::enterPassKey);
                        } else {
                            mLogger.log("passkeyEventCallback is not set!");
                            enterPassKey(key);
                        }
                    } else if (variant == BluetoothDevice.PAIRING_VARIANT_CONSENT) {
                        setPasskeyConfirmation(true);

                    } else if (variant == BluetoothDevice.PAIRING_VARIANT_PIN) {
                        if (mPasskeyEventCallback != null) {
                            mPasskeyEventCallback.onPasskeyRequested(
                                    (int pin) -> {
                                        byte[] newPin = convertPinToBytes(
                                                String.format(Locale.ENGLISH, "%d", pin));
                                        mPairingDevice.setPin(newPin);
                                    });
                        }
                    } else {
                        // Reject the pairing request if it's not using the Numeric
                        // Comparison (aka Passkey Confirmation) method.
                        setPasskeyConfirmation(false);
                    }
                    break;
                case BluetoothDevice.ACTION_BOND_STATE_CHANGED:
                    int bondState =
                            intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE,
                                    BluetoothDevice.BOND_NONE);
                    mLogger.log("Bond state to %s changed to %d", device, bondState);
                    switch (bondState) {
                        case BluetoothDevice.BOND_BONDING:
                            // If we've started bonding, we shouldn't be advertising.
                            mAdvertiser.stopAdvertising();
                            // Not discoverable anymore, but still connectable.
                            setScanMode(BluetoothAdapter.SCAN_MODE_CONNECTABLE);
                            break;
                        case BluetoothDevice.BOND_BONDED:
                            // Once bonded, advertise the account keys.
                            mAdvertiser.startAdvertising(accountKeysServiceData());
                            setScanMode(BluetoothAdapter.SCAN_MODE_CONNECTABLE);

                            // If it is subsequent pair, we need to add paired device here.
                            if (mIsSubsequentPair
                                    && mSecret != null
                                    && mSecret.length == AES_BLOCK_LENGTH) {
                                addAccountKey(mSecret, mPairingDevice);
                            }
                            break;
                        case BluetoothDevice.BOND_NONE:
                            // If the bonding process fails, we should be advertising again.
                            mAdvertiser.startAdvertising(getServiceData());
                            break;
                        default:
                            break;
                    }
                    break;
                case BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED:
                    mLogger.log(
                            "Connection state to %s changed to %d",
                            device,
                            intent.getIntExtra(
                                    BluetoothAdapter.EXTRA_CONNECTION_STATE,
                                    BluetoothAdapter.STATE_DISCONNECTED));
                    break;
                case BluetoothAdapter.ACTION_STATE_CHANGED:
                    int state = intent.getIntExtra(EXTRA_STATE, -1);
                    mLogger.log("Bluetooth adapter state=%s", state);
                    switch (state) {
                        case STATE_ON:
                            startRfcommServer();
                            break;
                        case STATE_OFF:
                            stopRfcommServer();
                            break;
                        default: // fall out
                    }
                    break;
                default:
                    mLogger.log(new IllegalArgumentException(intent.toString()),
                            "Received unexpected intent");
                    break;
            }
        }
    };

    @Nullable
    private byte[] convertPinToBytes(@Nullable String pin) {
        if (TextUtils.isEmpty(pin)) {
            return null;
        }
        byte[] pinBytes;
        pinBytes = pin.getBytes(StandardCharsets.UTF_8);
        if (pinBytes.length <= 0 || pinBytes.length > 16) {
            return null;
        }
        return pinBytes;
    }

    private final NotifiableGattServlet mPasskeyServlet =
            new NotifiableGattServlet() {
                @Override
                // Simulating deprecated API {@code PasskeyCharacteristic.ID} for testing.
                @SuppressWarnings("deprecation")
                public BluetoothGattCharacteristic getBaseCharacteristic() {
                    return new BluetoothGattCharacteristic(
                            PasskeyCharacteristic.CUSTOM_128_BIT_UUID,
                            PROPERTY_WRITE | PROPERTY_INDICATE,
                            PERMISSION_WRITE);
                }

                @Override
                public void write(
                        BluetoothGattServerConnection connection, int offset, byte[] value) {
                    mLogger.log("Got value from passkey servlet: %s", base16().encode(value));
                    if (mSecret == null) {
                        mLogger.log("Ignoring write to passkey characteristic, no pairing secret.");
                        return;
                    }

                    try {
                        mRemotePasskey = PasskeyCharacteristic.decrypt(
                                PasskeyCharacteristic.Type.SEEKER, mSecret, value);
                        if (mPasskeyEventCallback != null) {
                            mPasskeyEventCallback.onRemotePasskeyReceived(mRemotePasskey);
                        }
                        checkPasskey();
                    } catch (GeneralSecurityException e) {
                        mLogger.log(
                                "Decrypting passkey value %s failed using key %s",
                                base16().encode(value), base16().encode(mSecret));
                    }
                }
            };

    private final NotifiableGattServlet mDeviceNameServlet =
            new NotifiableGattServlet() {
                @Override
                // Simulating deprecated API {@code NameCharacteristic.ID} for testing.
                @SuppressWarnings("deprecation")
                BluetoothGattCharacteristic getBaseCharacteristic() {
                    return new BluetoothGattCharacteristic(
                            NameCharacteristic.CUSTOM_128_BIT_UUID,
                            PROPERTY_WRITE | PROPERTY_INDICATE,
                            PERMISSION_WRITE);
                }

                @Override
                public void write(
                        BluetoothGattServerConnection connection, int offset, byte[] value) {
                    mLogger.log("Got value from device naming servlet: %s", base16().encode(value));
                    if (mSecret == null) {
                        mLogger.log("Ignoring write to name characteristic, no pairing secret.");
                        return;
                    }
                    // Parse the device name from seeker to write name into provider.
                    mLogger.log("Got name byte array size = %d", value.length);
                    try {
                        String decryptedDeviceName =
                                NamingEncoder.decodeNamingPacket(mSecret, value);
                        if (decryptedDeviceName != null) {
                            setDeviceName(decryptedDeviceName.getBytes(StandardCharsets.UTF_8));
                            mLogger.log("write device name = %s", decryptedDeviceName);
                        }
                    } catch (GeneralSecurityException e) {
                        mLogger.log(e, "Failed to decrypt device name.");
                    }
                    // For testing to make sure we get the new provider name from simulator.
                    if (mWriteNameCountDown != null) {
                        mLogger.log("finish count down latch to write device name.");
                        mWriteNameCountDown.countDown();
                    }
                }
            };

    private Value mBluetoothAddress;
    private final FastPairAdvertiser mAdvertiser;
    private final Map<String, BluetoothGattServerHelper> mBluetoothGattServerHelpers =
            new HashMap<>();
    private CountDownLatch mIsDiscoverableLatch = new CountDownLatch(1);
    private ScheduledFuture<?> mRevertDiscoverableFuture;
    private boolean mShouldFailPairing = false;
    private boolean mIsDestroyed = false;
    private boolean mIsAdvertising;
    @Nullable
    private String mBleAddress;
    private BluetoothDevice mPairingDevice;
    private int mLocalPasskey;
    private int mRemotePasskey;
    @Nullable
    private byte[] mSecret;
    @Nullable
    private byte[] mAccountKey; // The latest account key added.
    // The first account key added. Eddystone treats that account as the owner of the device.
    @Nullable
    private byte[] mOwnerAccountKey;
    @Nullable
    private PasskeyConfirmationCallback mPasskeyConfirmationCallback;
    @Nullable
    private DeviceNameCallback mDeviceNameCallback;
    @Nullable
    private PasskeyEventCallback mPasskeyEventCallback;
    private final List<BatteryValue> mBatteryValues;
    private boolean mSuppressBatteryNotification = false;
    private boolean mSuppressSubsequentPairingNotification = false;
    HandshakeRequest mHandshakeRequest;
    @Nullable
    private CountDownLatch mWriteNameCountDown;
    private final RfcommServer mRfcommServer = new RfcommServer();
    private boolean mSupportDynamicBufferSize = false;
    private NotifiableGattServlet mBeaconActionsServlet;
    private final FastPairSimulatorDatabase mFastPairSimulatorDatabase;
    private boolean mIsSubsequentPair = false;

    /** Sets the flag for failing paring for debug purpose. */
    public void setShouldFailPairing(boolean shouldFailPairing) {
        this.mShouldFailPairing = shouldFailPairing;
    }

    /** Gets the flag for failing paring for debug purpose. */
    public boolean getShouldFailPairing() {
        return mShouldFailPairing;
    }

    /** Clear the battery values, then no battery information is packed when advertising. */
    public void clearBatteryValues() {
        mBatteryValues.clear();
    }

    /** Sets the battery items which will be included in the advertisement packet. */
    public void setBatteryValues(BatteryValue... batteryValues) {
        this.mBatteryValues.clear();
        Collections.addAll(this.mBatteryValues, batteryValues);
    }

    /** Sets whether the battery advertisement packet is within suppress type or not. */
    public void setSuppressBatteryNotification(boolean suppressBatteryNotification) {
        this.mSuppressBatteryNotification = suppressBatteryNotification;
    }

    /** Sets whether the account key data is within suppress type or not. */
    public void setSuppressSubsequentPairingNotification(boolean isSuppress) {
        mSuppressSubsequentPairingNotification = isSuppress;
    }

    /** Calls this to start advertising after some values are changed. */
    public void startAdvertising() {
        mAdvertiser.startAdvertising(getServiceData());
    }

    /** Send Event Message on to rfcomm connected devices. */
    public void sendEventStreamMessageToRfcommDevices(EventGroup eventGroup) {
        // Send fake log when event code is logging and type is not using Log_Full event.
        if (eventGroup == EventGroup.LOGGING && !mUseLogFullEvent) {
            mRfcommServer.sendFakeEventStreamLoggingMessage(
                    getDeviceName()
                            + " "
                            + getBleAddress()
                            + " send log at "
                            + new SimpleDateFormat("HH:mm:ss:SSS", Locale.US)
                            .format(Calendar.getInstance().getTime()));
        } else {
            mRfcommServer.sendFakeEventStreamMessage(eventGroup);
        }
    }

    public void setUseLogFullEvent(boolean useLogFullEvent) {
        this.mUseLogFullEvent = useLogFullEvent;
    }

    /** An optional way to get advertising status updates. */
    public interface AdvertisingChangedCallback {
        /**
         * Called when we change our BLE advertisement.
         *
         * @param isAdvertising the advertising status.
         */
        void onAdvertisingChanged(boolean isAdvertising);
    }

    /** A way for tests to get callbacks when passkey confirmation is invoked. */
    public interface PasskeyConfirmationCallback {
        void onPasskeyConfirmation(boolean confirm);
    }

    /** A way for simulator UI update to get callback when device name is changed. */
    public interface DeviceNameCallback {
        void onNameChanged(String deviceName);
    }

    /**
     * Callback when there comes a passkey input request from BT service, or receiving remote
     * device's passkey.
     */
    public interface PasskeyEventCallback {
        void onPasskeyRequested(KeyInputCallback keyInputCallback);

        void onRemotePasskeyReceived(int passkey);

        default void onPasskeyConfirmation(int passkey, Consumer<Boolean> isConfirmed) {
        }
    }

    /** Options for the simulator. */
    public static class Options {
        private final String mModelId;

        // TODO(b/143117318):Remove this when app-launch type has its own anti-spoofing key.
        private final String mAdvertisingModelId;

        @Nullable
        private final String mBluetoothAddress;

        @Nullable
        private final String mBleAddress;

        private final boolean mDataOnlyConnection;

        private final int mTxPowerLevel;

        private final boolean mEnableNameCharacteristic;

        private final AdvertisingChangedCallback mAdvertisingChangedCallback;

        private final boolean mIncludeTransportDataDescriptor;

        @Nullable
        private final byte[] mAntiSpoofingPrivateKey;

        private final boolean mUseRandomSaltForAccountKeyRotation;

        private final boolean mBecomeDiscoverable;

        private final boolean mShowsPasskeyConfirmation;

        private final boolean mEnableBeaconActionsCharacteristic;

        private final boolean mRemoveAllDevicesDuringPairing;

        @Nullable
        private final ByteString mEddystoneIdentityKey;

        private Options(
                String modelId,
                String advertisingModelId,
                @Nullable String bluetoothAddress,
                @Nullable String bleAddress,
                boolean dataOnlyConnection,
                int txPowerLevel,
                boolean enableNameCharacteristic,
                AdvertisingChangedCallback advertisingChangedCallback,
                boolean includeTransportDataDescriptor,
                @Nullable byte[] antiSpoofingPrivateKey,
                boolean useRandomSaltForAccountKeyRotation,
                boolean becomeDiscoverable,
                boolean showsPasskeyConfirmation,
                boolean enableBeaconActionsCharacteristic,
                boolean removeAllDevicesDuringPairing,
                @Nullable ByteString eddystoneIdentityKey) {
            this.mModelId = modelId;
            this.mAdvertisingModelId = advertisingModelId;
            this.mBluetoothAddress = bluetoothAddress;
            this.mBleAddress = bleAddress;
            this.mDataOnlyConnection = dataOnlyConnection;
            this.mTxPowerLevel = txPowerLevel;
            this.mEnableNameCharacteristic = enableNameCharacteristic;
            this.mAdvertisingChangedCallback = advertisingChangedCallback;
            this.mIncludeTransportDataDescriptor = includeTransportDataDescriptor;
            this.mAntiSpoofingPrivateKey = antiSpoofingPrivateKey;
            this.mUseRandomSaltForAccountKeyRotation = useRandomSaltForAccountKeyRotation;
            this.mBecomeDiscoverable = becomeDiscoverable;
            this.mShowsPasskeyConfirmation = showsPasskeyConfirmation;
            this.mEnableBeaconActionsCharacteristic = enableBeaconActionsCharacteristic;
            this.mRemoveAllDevicesDuringPairing = removeAllDevicesDuringPairing;
            this.mEddystoneIdentityKey = eddystoneIdentityKey;
        }

        public String getModelId() {
            return mModelId;
        }

        // TODO(b/143117318):Remove this when app-launch type has its own anti-spoofing key.
        public String getAdvertisingModelId() {
            return mAdvertisingModelId;
        }

        @Nullable
        public String getBluetoothAddress() {
            return mBluetoothAddress;
        }

        @Nullable
        public String getBleAddress() {
            return mBleAddress;
        }

        public boolean getDataOnlyConnection() {
            return mDataOnlyConnection;
        }

        public int getTxPowerLevel() {
            return mTxPowerLevel;
        }

        public boolean getEnableNameCharacteristic() {
            return mEnableNameCharacteristic;
        }

        public AdvertisingChangedCallback getAdvertisingChangedCallback() {
            return mAdvertisingChangedCallback;
        }

        public boolean getIncludeTransportDataDescriptor() {
            return mIncludeTransportDataDescriptor;
        }

        @Nullable
        public byte[] getAntiSpoofingPrivateKey() {
            return mAntiSpoofingPrivateKey;
        }

        public boolean getUseRandomSaltForAccountKeyRotation() {
            return mUseRandomSaltForAccountKeyRotation;
        }

        public boolean getBecomeDiscoverable() {
            return mBecomeDiscoverable;
        }

        public boolean getShowsPasskeyConfirmation() {
            return mShowsPasskeyConfirmation;
        }

        public boolean getEnableBeaconActionsCharacteristic() {
            return mEnableBeaconActionsCharacteristic;
        }

        public boolean getRemoveAllDevicesDuringPairing() {
            return mRemoveAllDevicesDuringPairing;
        }

        @Nullable
        public ByteString getEddystoneIdentityKey() {
            return mEddystoneIdentityKey;
        }

        /** Converts an instance to a builder. */
        public Builder toBuilder() {
            return new Options.Builder(this);
        }

        /** Constructs a builder. */
        public static Builder builder() {
            return new Options.Builder();
        }

        /** @param modelId Must be a 3-byte hex string. */
        public static Builder builder(String modelId) {
            return new Options.Builder()
                    .setModelId(Ascii.toUpperCase(modelId))
                    .setAdvertisingModelId(Ascii.toUpperCase(modelId))
                    .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                    .setAdvertisingChangedCallback(isAdvertising -> {
                    })
                    .setIncludeTransportDataDescriptor(true)
                    .setUseRandomSaltForAccountKeyRotation(false)
                    .setEnableNameCharacteristic(true)
                    .setDataOnlyConnection(false)
                    .setBecomeDiscoverable(true)
                    .setShowsPasskeyConfirmation(false)
                    .setEnableBeaconActionsCharacteristic(true)
                    .setRemoveAllDevicesDuringPairing(true);
        }

        /** A builder for {@link Options}. */
        public static class Builder {

            private String mModelId;

            // TODO(b/143117318):Remove this when app-launch type has its own anti-spoofing key.
            private String mAdvertisingModelId;

            @Nullable
            private String mBluetoothAddress;

            @Nullable
            private String mBleAddress;

            private boolean mDataOnlyConnection;

            private int mTxPowerLevel;

            private boolean mEnableNameCharacteristic;

            private AdvertisingChangedCallback mAdvertisingChangedCallback;

            private boolean mIncludeTransportDataDescriptor;

            @Nullable
            private byte[] mAntiSpoofingPrivateKey;

            private boolean mUseRandomSaltForAccountKeyRotation;

            private boolean mBecomeDiscoverable;

            private boolean mShowsPasskeyConfirmation;

            private boolean mEnableBeaconActionsCharacteristic;

            private boolean mRemoveAllDevicesDuringPairing;

            @Nullable
            private ByteString mEddystoneIdentityKey;

            private Builder() {
            }

            private Builder(Options option) {
                this.mModelId = option.mModelId;
                this.mAdvertisingModelId = option.mAdvertisingModelId;
                this.mBluetoothAddress = option.mBluetoothAddress;
                this.mBleAddress = option.mBleAddress;
                this.mDataOnlyConnection = option.mDataOnlyConnection;
                this.mTxPowerLevel = option.mTxPowerLevel;
                this.mEnableNameCharacteristic = option.mEnableNameCharacteristic;
                this.mAdvertisingChangedCallback = option.mAdvertisingChangedCallback;
                this.mIncludeTransportDataDescriptor = option.mIncludeTransportDataDescriptor;
                this.mAntiSpoofingPrivateKey = option.mAntiSpoofingPrivateKey;
                this.mUseRandomSaltForAccountKeyRotation =
                        option.mUseRandomSaltForAccountKeyRotation;
                this.mBecomeDiscoverable = option.mBecomeDiscoverable;
                this.mShowsPasskeyConfirmation = option.mShowsPasskeyConfirmation;
                this.mEnableBeaconActionsCharacteristic = option.mEnableBeaconActionsCharacteristic;
                this.mRemoveAllDevicesDuringPairing = option.mRemoveAllDevicesDuringPairing;
                this.mEddystoneIdentityKey = option.mEddystoneIdentityKey;
            }

            /**
             * Must be one of the {@code ADVERTISE_TX_POWER_*} levels in {@link AdvertiseSettings}.
             * Default is HIGH.
             */
            public Builder setTxPowerLevel(int txPowerLevel) {
                this.mTxPowerLevel = txPowerLevel;
                return this;
            }

            /**
             * Must be a 6-byte hex string (optionally with colons).
             * Default is this device's BT MAC.
             */
            public Builder setBluetoothAddress(@Nullable String bluetoothAddress) {
                this.mBluetoothAddress = bluetoothAddress;
                return this;
            }

            public Builder setBleAddress(@Nullable String bleAddress) {
                this.mBleAddress = bleAddress;
                return this;
            }

            /** A boolean to decide if enable name characteristic as simulator characteristic. */
            public Builder setEnableNameCharacteristic(boolean enable) {
                this.mEnableNameCharacteristic = enable;
                return this;
            }

            /** @see AdvertisingChangedCallback */
            public Builder setAdvertisingChangedCallback(
                    AdvertisingChangedCallback advertisingChangedCallback) {
                this.mAdvertisingChangedCallback = advertisingChangedCallback;
                return this;
            }

            public Builder setDataOnlyConnection(boolean dataOnlyConnection) {
                this.mDataOnlyConnection = dataOnlyConnection;
                return this;
            }

            /**
             * Set whether to include the Transport Data descriptor, which has the list of supported
             * profiles. This is required by the spec, but if we can't get it, we recover gracefully
             * by assuming support for one of {A2DP, Headset}. Default is true.
             */
            public Builder setIncludeTransportDataDescriptor(
                    boolean includeTransportDataDescriptor) {
                this.mIncludeTransportDataDescriptor = includeTransportDataDescriptor;
                return this;
            }

            public Builder setAntiSpoofingPrivateKey(@Nullable byte[] antiSpoofingPrivateKey) {
                this.mAntiSpoofingPrivateKey = antiSpoofingPrivateKey;
                return this;
            }

            public Builder setUseRandomSaltForAccountKeyRotation(
                    boolean useRandomSaltForAccountKeyRotation) {
                this.mUseRandomSaltForAccountKeyRotation = useRandomSaltForAccountKeyRotation;
                return this;
            }

            // TODO(b/143117318):Remove this when app-launch type has its own anti-spoofing key.
            public Builder setAdvertisingModelId(String modelId) {
                this.mAdvertisingModelId = modelId;
                return this;
            }

            public Builder setBecomeDiscoverable(boolean becomeDiscoverable) {
                this.mBecomeDiscoverable = becomeDiscoverable;
                return this;
            }

            public Builder setShowsPasskeyConfirmation(boolean showsPasskeyConfirmation) {
                this.mShowsPasskeyConfirmation = showsPasskeyConfirmation;
                return this;
            }

            public Builder setEnableBeaconActionsCharacteristic(
                    boolean enableBeaconActionsCharacteristic) {
                this.mEnableBeaconActionsCharacteristic = enableBeaconActionsCharacteristic;
                return this;
            }

            public Builder setRemoveAllDevicesDuringPairing(boolean removeAllDevicesDuringPairing) {
                this.mRemoveAllDevicesDuringPairing = removeAllDevicesDuringPairing;
                return this;
            }

            /**
             * Non-public because this is required to create a builder. See
             * {@link Options#builder}.
             */
            public Builder setModelId(String modelId) {
                this.mModelId = modelId;
                return this;
            }

            public Builder setEddystoneIdentityKey(@Nullable ByteString eddystoneIdentityKey) {
                this.mEddystoneIdentityKey = eddystoneIdentityKey;
                return this;
            }

            // Custom builder in order to normalize properties. go/autovalue/builders-howto
            public Options build() {
                return new Options(
                        Ascii.toUpperCase(mModelId),
                        Ascii.toUpperCase(mAdvertisingModelId),
                        mBluetoothAddress,
                        mBleAddress,
                        mDataOnlyConnection,
                        mTxPowerLevel,
                        mEnableNameCharacteristic,
                        mAdvertisingChangedCallback,
                        mIncludeTransportDataDescriptor,
                        mAntiSpoofingPrivateKey,
                        mUseRandomSaltForAccountKeyRotation,
                        mBecomeDiscoverable,
                        mShowsPasskeyConfirmation,
                        mEnableBeaconActionsCharacteristic,
                        mRemoveAllDevicesDuringPairing,
                        mEddystoneIdentityKey);
            }
        }
    }

    public FastPairSimulator(Context context, Options options) {
        this(context, options, new Logger(TAG));
    }

    public FastPairSimulator(Context context, Options options, Logger logger) {
        this.mContext = context;
        this.mOptions = options;
        this.mLogger = logger;

        this.mBatteryValues = new ArrayList<>();

        String bluetoothAddress =
                !TextUtils.isEmpty(options.getBluetoothAddress())
                        ? options.getBluetoothAddress()
                        : Settings.Secure.getString(context.getContentResolver(),
                                "bluetooth_address");
        if (bluetoothAddress == null && VERSION.SDK_INT >= VERSION_CODES.O) {
            // Requires a modified Android O build for access to bluetoothAdapter.getAddress().
            // See http://google3/java/com/google/location/nearby/apps/fastpair/simulator/README.md.
            bluetoothAddress = mBluetoothAdapter.getAddress();
        }
        this.mBluetoothAddress =
                new Value(BluetoothAddress.decode(bluetoothAddress), ByteOrder.BIG_ENDIAN);
        this.mBleAddress = options.getBleAddress();
        this.mAdvertiser = new OreoFastPairAdvertiser(this);

        mFastPairSimulatorDatabase = new FastPairSimulatorDatabase(context);

        byte[] deviceName = getDeviceNameInBytes();
        mLogger.log(
                "Provider default device name is %s",
                deviceName != null ? new String(deviceName, StandardCharsets.UTF_8) : null);

        if (mOptions.getDataOnlyConnection()) {
            // To get BLE address, we need to start advertising first, and then
            // {@code#setBleAddress} will be called with BLE address.
            mAdvertiser.startAdvertising(modelIdServiceData(/* forAdvertising= */ true));
        } else {
            // Make this so that the simulator doesn't start automatically.
            // This is tricky since the simulator is used in our integ tests as well.
            start(mBleAddress != null ? mBleAddress : bluetoothAddress);
        }
    }

    public void start(String address) {
        IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED);
        filter.addAction(BluetoothDevice.ACTION_PAIRING_REQUEST);
        filter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        filter.addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED);
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        mContext.registerReceiver(mBroadcastReceiver, filter);

        BluetoothManager bluetoothManager = mContext.getSystemService(BluetoothManager.class);
        BluetoothGattServerHelper bluetoothGattServerHelper =
                new BluetoothGattServerHelper(mContext, wrap(bluetoothManager));
        mBluetoothGattServerHelpers.put(address, bluetoothGattServerHelper);

        if (mOptions.getBecomeDiscoverable()) {
            try {
                becomeDiscoverable();
            } catch (InterruptedException | TimeoutException e) {
                mLogger.log(e, "Error becoming discoverable");
            }
        }

        mAdvertiser.startAdvertising(modelIdServiceData(/* forAdvertising= */ true));
        startGattServer(bluetoothGattServerHelper);
        startRfcommServer();
        scheduleAdvertisingRefresh();
    }

    /**
     * Regenerate service data on a fixed interval.
     * This causes the bloom filter to be refreshed and a different salt to be used for rotation.
     */
    @SuppressWarnings("FutureReturnValueIgnored")
    private void scheduleAdvertisingRefresh() {
        mExecutor.scheduleAtFixedRate(() -> {
            if (mIsAdvertising) {
                mAdvertiser.startAdvertising(getServiceData());
            }
        }, ADVERTISING_REFRESH_DELAY_1_MIN, ADVERTISING_REFRESH_DELAY_1_MIN, TimeUnit.MILLISECONDS);
    }

    public void destroy() {
        try {
            mLogger.log("Destroying simulator");
            mIsDestroyed = true;
            mContext.unregisterReceiver(mBroadcastReceiver);
            mAdvertiser.stopAdvertising();
            for (BluetoothGattServerHelper helper : mBluetoothGattServerHelpers.values()) {
                helper.close();
            }
            stopRfcommServer();
            mDeviceNameCallback = null;
            mExecutor.shutdownNow();
        } catch (IllegalArgumentException ignored) {
            // Happens if you haven't given us permissions yet, so we didn't register the receiver.
        }
    }

    public boolean isDestroyed() {
        return mIsDestroyed;
    }

    @Nullable
    public String getBluetoothAddress() {
        return BluetoothAddress.encode(mBluetoothAddress.getBytes(ByteOrder.BIG_ENDIAN));
    }

    public boolean isAdvertising() {
        return mIsAdvertising;
    }

    public void setIsAdvertising(boolean isAdvertising) {
        if (this.mIsAdvertising != isAdvertising) {
            this.mIsAdvertising = isAdvertising;
            mOptions.getAdvertisingChangedCallback().onAdvertisingChanged(isAdvertising);
        }
    }

    public void stopAdvertising() {
        mAdvertiser.stopAdvertising();
    }

    public void setBleAddress(String bleAddress) {
        this.mBleAddress = bleAddress;
        if (mOptions.getDataOnlyConnection()) {
            mBluetoothAddress = new Value(BluetoothAddress.decode(bleAddress),
                    ByteOrder.BIG_ENDIAN);
            start(bleAddress);
        }
        // When BLE address changes, needs to send BLE address to the client again.
        sendDeviceBleAddress(bleAddress);

        // If we are advertising something other than the model id (e.g. the bloom filter), restart
        // the advertisement so that it is updated with the new address.
        if (isAdvertising() && !isDiscoverable()) {
            mAdvertiser.startAdvertising(getServiceData());
        }
    }

    @Nullable
    public String getBleAddress() {
        return mBleAddress;
    }

    // This method is only for testing to make test block until write name success or time out.
    @VisibleForTesting
    public void setCountDownLatchToWriteName(CountDownLatch countDownLatch) {
        mLogger.log("Set up count down latch to write device name.");
        mWriteNameCountDown = countDownLatch;
    }

    public boolean areBeaconActionsNotificationsEnabled() {
        return mBeaconActionsServlet.areNotificationsEnabled();
    }

    private abstract class NotifiableGattServlet extends BluetoothGattServlet {
        private final Map<BluetoothGattServerConnection, Notifier> mConnections = new HashMap<>();

        abstract BluetoothGattCharacteristic getBaseCharacteristic();

        @Override
        public BluetoothGattCharacteristic getCharacteristic() {
            // Enabling indication requires the Client Characteristic Configuration descriptor.
            BluetoothGattCharacteristic characteristic = getBaseCharacteristic();
            characteristic.addDescriptor(
                    new BluetoothGattDescriptor(
                            Constants.CLIENT_CHARACTERISTIC_CONFIGURATION_DESCRIPTOR_UUID,
                            BluetoothGattDescriptor.PERMISSION_READ
                                    | BluetoothGattDescriptor.PERMISSION_WRITE));
            return characteristic;
        }

        @Override
        public void enableNotification(BluetoothGattServerConnection connection, Notifier notifier)
                throws BluetoothGattException {
            mLogger.log("Registering notifier for %s", getCharacteristic());
            mConnections.put(connection, notifier);
        }

        @Override
        public void disableNotification(BluetoothGattServerConnection connection, Notifier notifier)
                throws BluetoothGattException {
            mLogger.log("Removing notifier for %s", getCharacteristic());
            mConnections.remove(connection);
        }

        boolean areNotificationsEnabled() {
            return !mConnections.isEmpty();
        }

        void sendNotification(byte[] data) {
            if (mConnections.isEmpty()) {
                mLogger.log("Not sending notify as no notifier registered");
                return;
            }
            // Needs to be on a separate thread to avoid deadlocking and timing out (waits for a
            // callback from OS, which happens on the main thread).
            mExecutor.execute(
                    () -> {
                        for (Map.Entry<BluetoothGattServerConnection, Notifier> entry :
                                mConnections.entrySet()) {
                            try {
                                mLogger.log("Sending notify %s to %s",
                                        getCharacteristic(),
                                        entry.getKey().getDevice().getAddress());
                                entry.getValue().notify(data);
                            } catch (BluetoothException e) {
                                mLogger.log(
                                        e,
                                        "Failed to notify (indicate) result of %s to %s",
                                        getCharacteristic(),
                                        entry.getKey().getDevice().getAddress());
                            }
                        }
                    });
        }
    }

    private void startRfcommServer() {
        mRfcommServer.setRequestHandler(this::handleRfcommServerRequest);
        mRfcommServer.setStateMonitor(state -> {
            mLogger.log("RfcommServer is in %s state", state);
            if (CONNECTED.equals(state)) {
                sendModelId();
                sendDeviceBleAddress(mBleAddress);
                sendFirmwareVersion();
                sendSessionNonce();
            }
        });
        mRfcommServer.start();
    }

    private void handleRfcommServerRequest(int eventGroup, int eventCode, byte[] data) {
        switch (eventGroup) {
            case EventGroup.DEVICE_VALUE:
                if (data == null) {
                    break;
                }

                String deviceValue = base16().encode(data);
                if (eventCode == DeviceEventCode.DEVICE_CAPABILITY_VALUE) {
                    mLogger.log("Received phone capability: %s", deviceValue);
                } else if (eventCode == DeviceEventCode.PLATFORM_TYPE_VALUE) {
                    mLogger.log("Received platform type: %s", deviceValue);
                }
                break;
            case EventGroup.DEVICE_ACTION_VALUE:
                if (eventCode == DeviceActionEventCode.DEVICE_ACTION_RING_VALUE) {
                    mLogger.log("receive device action with ring value, data = %d",
                            data[0]);
                    sendDeviceRingActionResponse();
                    // Simulate notifying the seeker that the ringing has stopped due
                    // to user interaction (such as tapping the bud).
                    mUiThreadHandler.postDelayed(this::sendDeviceRingStoppedAction,
                            5000);
                }
                break;
            case EventGroup.DEVICE_CONFIGURATION_VALUE:
                if (eventCode == DeviceConfigurationEventCode.CONFIGURATION_BUFFER_SIZE_VALUE) {
                    mLogger.log(
                            "receive device action with buffer size value, data = %s",
                            base16().encode(data));
                    sendSetBufferActionResponse(data);
                }
                break;
            case EventGroup.DEVICE_CAPABILITY_SYNC_VALUE:
                if (eventCode == DeviceCapabilitySyncEventCode.REQUEST_CAPABILITY_UPDATE_VALUE) {
                    mLogger.log("receive device capability update request.");
                    sendCapabilitySync();
                }
                break;
            default: // fall out
                break;
        }
    }

    private void stopRfcommServer() {
        mRfcommServer.stop();
        mRfcommServer.setRequestHandler(null);
        mRfcommServer.setStateMonitor(null);
    }

    private void sendModelId() {
        mLogger.log("Send model ID to the client");
        mRfcommServer.send(
                EventGroup.DEVICE_VALUE,
                DeviceEventCode.DEVICE_MODEL_ID_VALUE,
                modelIdServiceData(/* forAdvertising= */ false));
    }

    private void sendDeviceBleAddress(String bleAddress) {
        mLogger.log("Send BLE address (%s) to the client", bleAddress);
        if (bleAddress != null) {
            mRfcommServer.send(
                    EventGroup.DEVICE_VALUE,
                    DeviceEventCode.DEVICE_BLE_ADDRESS_VALUE,
                    BluetoothAddress.decode(bleAddress));
        }
    }

    private void sendFirmwareVersion() {
        mLogger.log("Send Firmware Version (%s) to the client", mDeviceFirmwareVersion);
        mRfcommServer.send(
                EventGroup.DEVICE_VALUE,
                DeviceEventCode.FIRMWARE_VERSION_VALUE,
                mDeviceFirmwareVersion.getBytes());
    }

    private void sendSessionNonce() {
        mLogger.log("Send SessionNonce (%s) to the client", mDeviceFirmwareVersion);
        SecureRandom secureRandom = new SecureRandom();
        mSessionNonce = new byte[SECTION_NONCE_LENGTH];
        secureRandom.nextBytes(mSessionNonce);
        mRfcommServer.send(
                EventGroup.DEVICE_VALUE, DeviceEventCode.SECTION_NONCE_VALUE, mSessionNonce);
    }

    private void sendDeviceRingActionResponse() {
        mLogger.log("Send device ring action response to the client");
        mRfcommServer.send(
                EventGroup.ACKNOWLEDGEMENT_VALUE,
                AcknowledgementEventCode.ACKNOWLEDGEMENT_ACK_VALUE,
                new byte[]{
                        EventGroup.DEVICE_ACTION_VALUE,
                        DeviceActionEventCode.DEVICE_ACTION_RING_VALUE
                });
    }

    private void sendSetBufferActionResponse(byte[] data) {
        boolean hmacPassed = false;
        for (ByteString accountKey : getAccountKeys()) {
            try {
                if (MessageStreamHmacEncoder.verifyHmac(
                        accountKey.toByteArray(), mSessionNonce, data)) {
                    hmacPassed = true;
                    mLogger.log("Buffer size data matches account key %s",
                            base16().encode(accountKey.toByteArray()));
                    break;
                }
            } catch (GeneralSecurityException e) {
                // Ignore.
            }
        }
        if (hmacPassed) {
            mLogger.log("Send buffer size action response %s to the client", base16().encode(data));
            mRfcommServer.send(
                    EventGroup.ACKNOWLEDGEMENT_VALUE,
                    AcknowledgementEventCode.ACKNOWLEDGEMENT_ACK_VALUE,
                    new byte[]{
                            EventGroup.DEVICE_CONFIGURATION_VALUE,
                            DeviceConfigurationEventCode.CONFIGURATION_BUFFER_SIZE_VALUE,
                            data[0],
                            data[1],
                            data[2]
                    });
        } else {
            mLogger.log("No matched account key for sendSetBufferActionResponse");
        }
    }

    private void sendCapabilitySync() {
        mLogger.log("Send capability sync to the client");
        if (mSupportDynamicBufferSize) {
            mLogger.log("Send dynamic buffer size range to the client");
            mRfcommServer.send(
                    EventGroup.DEVICE_CAPABILITY_SYNC_VALUE,
                    DeviceCapabilitySyncEventCode.CONFIGURABLE_BUFFER_SIZE_RANGE_VALUE,
                    new byte[]{
                            0x00, 0x01, (byte) 0xf4, 0x00, 0x64, 0x00, (byte) 0xc8,
                            0x01, 0x00, (byte) 0xff, 0x00, 0x01, 0x00, (byte) 0x88,
                            0x02, 0x01, (byte) 0xff, 0x01, 0x01, 0x01, (byte) 0x88,
                            0x03, 0x02, (byte) 0xff, 0x02, 0x01, 0x02, (byte) 0x88,
                            0x04, 0x03, (byte) 0xff, 0x03, 0x01, 0x03, (byte) 0x88
                    });
        }
    }

    private void sendDeviceRingStoppedAction() {
        mLogger.log("Sending device ring stopped action to the client");
        mRfcommServer.send(
                EventGroup.DEVICE_ACTION_VALUE,
                DeviceActionEventCode.DEVICE_ACTION_RING_VALUE,
                // Additional data for stopping ringing on all components.
                new byte[]{0x00});
    }

    private void startGattServer(BluetoothGattServerHelper helper) {
        BluetoothGattServlet tdsControlPointServlet =
                new NotifiableGattServlet() {
                    @Override
                    public BluetoothGattCharacteristic getBaseCharacteristic() {
                        return new BluetoothGattCharacteristic(ControlPointCharacteristic.ID,
                                PROPERTY_WRITE | PROPERTY_INDICATE, PERMISSION_WRITE);
                    }

                    @Override
                    public void write(
                            BluetoothGattServerConnection connection, int offset, byte[] value)
                            throws BluetoothGattException {
                        mLogger.log("Requested TDS Control Point write, value=%s",
                                base16().encode(value));

                        ResultCode resultCode = checkTdsControlPointRequest(value);
                        if (resultCode == ResultCode.SUCCESS) {
                            try {
                                becomeDiscoverable();
                            } catch (TimeoutException | InterruptedException e) {
                                mLogger.log(e, "Failed to become discoverable");
                                resultCode = ResultCode.OPERATION_FAILED;
                            }
                        }

                        mLogger.log("Request complete, resultCode=%s", resultCode);

                        mLogger.log("Sending TDS Control Point response indication");
                        sendNotification(
                                Bytes.concat(
                                        new byte[]{
                                                getTdsControlPointOpCode(value),
                                                resultCode.mByteValue,
                                        },
                                        resultCode == ResultCode.SUCCESS
                                                ? TDS_CONTROL_POINT_RESPONSE_PARAMETER
                                                : new byte[0]));
                    }
                };

        BluetoothGattServlet brHandoverDataServlet =
                new BluetoothGattServlet() {

                    @Override
                    public BluetoothGattCharacteristic getCharacteristic() {
                        return new BluetoothGattCharacteristic(BrHandoverDataCharacteristic.ID,
                                PROPERTY_READ, PERMISSION_READ);
                    }

                    @Override
                    public byte[] read(BluetoothGattServerConnection connection, int offset) {
                        return Bytes.concat(
                                new byte[]{BrHandoverDataCharacteristic.BR_EDR_FEATURES},
                                mBluetoothAddress.getBytes(ByteOrder.LITTLE_ENDIAN),
                                CLASS_OF_DEVICE.getBytes(ByteOrder.LITTLE_ENDIAN));
                    }
                };

        BluetoothGattServlet bluetoothSigServlet =
                new BluetoothGattServlet() {

                    @Override
                    public BluetoothGattCharacteristic getCharacteristic() {
                        BluetoothGattCharacteristic characteristic =
                                new BluetoothGattCharacteristic(
                                        TransportDiscoveryService.BluetoothSigDataCharacteristic.ID,
                                        0 /* no properties */,
                                        0 /* no permissions */);

                        if (mOptions.getIncludeTransportDataDescriptor()) {
                            characteristic.addDescriptor(
                                    new BluetoothGattDescriptor(
                                            TransportDiscoveryService.BluetoothSigDataCharacteristic
                                                    .BrTransportBlockDataDescriptor.ID,
                                            BluetoothGattDescriptor.PERMISSION_READ));
                        }
                        return characteristic;
                    }

                    @Override
                    public byte[] readDescriptor(
                            BluetoothGattServerConnection connection,
                            BluetoothGattDescriptor descriptor,
                            int offset)
                            throws BluetoothGattException {
                        return transportDiscoveryData();
                    }
                };

        BluetoothGattServlet accountKeyServlet =
                new BluetoothGattServlet() {
                    @Override
                    // Simulating deprecated API {@code AccountKeyCharacteristic.ID} for testing.
                    @SuppressWarnings("deprecation")
                    public BluetoothGattCharacteristic getCharacteristic() {
                        return new BluetoothGattCharacteristic(
                                AccountKeyCharacteristic.CUSTOM_128_BIT_UUID,
                                PROPERTY_WRITE,
                                PERMISSION_WRITE);
                    }

                    @Override
                    public void write(
                            BluetoothGattServerConnection connection, int offset, byte[] value) {
                        mLogger.log("Got value from account key servlet: %s",
                                base16().encode(value));
                        try {
                            addAccountKey(AesEcbSingleBlockEncryption.decrypt(mSecret, value),
                                    mPairingDevice);
                        } catch (GeneralSecurityException e) {
                            mLogger.log(e, "Failed to decrypt account key.");
                        }
                        mUiThreadHandler.post(
                                () -> mAdvertiser.startAdvertising(accountKeysServiceData()));
                    }
                };

        BluetoothGattServlet firmwareVersionServlet =
                new BluetoothGattServlet() {
                    @Override
                    public BluetoothGattCharacteristic getCharacteristic() {
                        return new BluetoothGattCharacteristic(
                                FirmwareVersionCharacteristic.ID, PROPERTY_READ, PERMISSION_READ);
                    }

                    @Override
                    public byte[] read(BluetoothGattServerConnection connection, int offset) {
                        return mDeviceFirmwareVersion.getBytes();
                    }
                };

        BluetoothGattServlet keyBasedPairingServlet =
                new NotifiableGattServlet() {
                    @Override
                    // Simulating deprecated API {@code KeyBasedPairingCharacteristic.ID} for
                    // testing.
                    @SuppressWarnings("deprecation")
                    public BluetoothGattCharacteristic getBaseCharacteristic() {
                        return new BluetoothGattCharacteristic(
                                KeyBasedPairingCharacteristic.CUSTOM_128_BIT_UUID,
                                PROPERTY_WRITE | PROPERTY_INDICATE,
                                PERMISSION_WRITE);
                    }

                    @Override
                    public void write(
                            BluetoothGattServerConnection connection, int offset, byte[] value) {
                        mLogger.log("Requesting key based pairing handshake, value=%s",
                                base16().encode(value));

                        mSecret = null;
                        byte[] seekerPublicAddress = null;
                        if (value.length == AES_BLOCK_LENGTH) {

                            for (ByteString key : getAccountKeys()) {
                                byte[] candidateSecret = key.toByteArray();
                                try {
                                    seekerPublicAddress = handshake(candidateSecret, value);
                                    mSecret = candidateSecret;
                                    mIsSubsequentPair = true;
                                    break;
                                } catch (GeneralSecurityException e) {
                                    mLogger.log(e, "Failed to decrypt with %s",
                                            base16().encode(candidateSecret));
                                }
                            }
                        } else if (value.length == AES_BLOCK_LENGTH + PUBLIC_KEY_LENGTH
                                && mOptions.getAntiSpoofingPrivateKey() != null) {
                            try {
                                byte[] encryptedRequest = Arrays.copyOf(value, AES_BLOCK_LENGTH);
                                byte[] receivedPublicKey =
                                        Arrays.copyOfRange(value, AES_BLOCK_LENGTH, value.length);
                                byte[] candidateSecret =
                                        EllipticCurveDiffieHellmanExchange.create(
                                                        mOptions.getAntiSpoofingPrivateKey())
                                                .generateSecret(receivedPublicKey);
                                seekerPublicAddress = handshake(candidateSecret, encryptedRequest);
                                mSecret = candidateSecret;
                            } catch (Exception e) {
                                mLogger.log(
                                        e,
                                        "Failed to decrypt with anti-spoofing private key %s",
                                        base16().encode(mOptions.getAntiSpoofingPrivateKey()));
                            }
                        } else {
                            mLogger.log("Packet length invalid, %d", value.length);
                            return;
                        }

                        if (mSecret == null) {
                            mLogger.log("Couldn't find a usable key to decrypt with.");
                            return;
                        }

                        mLogger.log("Found valid decryption key, %s", base16().encode(mSecret));
                        byte[] salt = new byte[9];
                        new Random().nextBytes(salt);
                        try {
                            byte[] data = concat(
                                    new byte[]{KeyBasedPairingCharacteristic.Response.TYPE},
                                    mBluetoothAddress.getBytes(ByteOrder.BIG_ENDIAN), salt);
                            byte[] encryptedAddress = encrypt(mSecret, data);
                            mLogger.log(
                                    "Sending handshake response %s with size %d",
                                    base16().encode(encryptedAddress), encryptedAddress.length);
                            sendNotification(encryptedAddress);

                            // Notify seeker for NameCharacteristic to get provider device name
                            // when seeker request device name flag is true.
                            if (mOptions.getEnableNameCharacteristic()
                                    && mHandshakeRequest.requestDeviceName()) {
                                byte[] encryptedResponse =
                                        getDeviceNameInBytes() != null ? createEncryptedDeviceName()
                                                : new byte[0];
                                mLogger.log(
                                        "Sending device name response %s with size %d",
                                        base16().encode(encryptedResponse),
                                        encryptedResponse.length);
                                mDeviceNameServlet.sendNotification(encryptedResponse);
                            }

                            // Disconnects the current connection to allow the following pairing
                            // request. Needs to be on a separate thread to avoid deadlocking and
                            // timing out (waits for a callback from OS, which happens on this
                            // thread).
                            //
                            // Note: The spec does not require you to disconnect from other
                            // devices at this point.
                            // If headphones support multiple simultaneous connections, they
                            // should stay connected. But Android fails to pair with the new
                            // device if we don't first disconnect from any other device.
                            mLogger.log("Skip remove bond, value=%s",
                                    mOptions.getRemoveAllDevicesDuringPairing());
                            if (mOptions.getRemoveAllDevicesDuringPairing()
                                    && mHandshakeRequest.getType()
                                    == HandshakeRequest.Type.KEY_BASED_PAIRING_REQUEST
                                    && !mHandshakeRequest.requestRetroactivePair()) {
                                mExecutor.execute(() -> disconnectAllBondedDevices());
                            }

                            if (mHandshakeRequest.getType()
                                    == HandshakeRequest.Type.KEY_BASED_PAIRING_REQUEST
                                    && mHandshakeRequest.requestProviderInitialBonding()) {
                                // Run on executor to ensure it doesn't happen until after the
                                // notify (which tells the remote device what address to expect).
                                String seekerPublicAddressString =
                                        BluetoothAddress.encode(seekerPublicAddress);
                                mExecutor.execute(() -> {
                                    mLogger.log("Sending pairing request to %s",
                                            seekerPublicAddressString);
                                    mBluetoothAdapter.getRemoteDevice(
                                            seekerPublicAddressString).createBond();
                                });
                            }
                        } catch (GeneralSecurityException e) {
                            mLogger.log(e, "Failed to notify of static mac address");
                        }
                    }

                    @Nullable
                    private byte[] handshake(byte[] key, byte[] encryptedPairingRequest)
                            throws GeneralSecurityException {
                        mHandshakeRequest = new HandshakeRequest(key, encryptedPairingRequest);

                        byte[] decryptedAddress = mHandshakeRequest.getVerificationData();
                        if (mBleAddress != null
                                && Arrays.equals(decryptedAddress,
                                BluetoothAddress.decode(mBleAddress))
                                || Arrays.equals(decryptedAddress,
                                mBluetoothAddress.getBytes(ByteOrder.BIG_ENDIAN))) {
                            mLogger.log("Address matches: %s", base16().encode(decryptedAddress));
                        } else {
                            throw new GeneralSecurityException(
                                    "Address (BLE or BR/EDR) is not correct: "
                                            + base16().encode(decryptedAddress)
                                            + ", "
                                            + mBleAddress
                                            + ", "
                                            + getBluetoothAddress());
                        }

                        switch (mHandshakeRequest.getType()) {
                            case KEY_BASED_PAIRING_REQUEST:
                                return handleKeyBasedPairingRequest(mHandshakeRequest);
                            case ACTION_OVER_BLE:
                                return handleActionOverBleRequest(mHandshakeRequest);
                            case UNKNOWN:
                                // continue to throw the exception;
                        }
                        throw new GeneralSecurityException(
                                "Type is not correct: " + mHandshakeRequest.getType());
                    }

                    @Nullable
                    private byte[] handleKeyBasedPairingRequest(HandshakeRequest handshakeRequest)
                            throws GeneralSecurityException {
                        if (handshakeRequest.requestDiscoverable()) {
                            mLogger.log("Requested discoverability");
                            setScanMode(BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE);
                        }

                        mLogger.log(
                                "KeyBasedPairing: initialBonding=%s, requestDeviceName=%s, "
                                        + "retroactivePair=%s",
                                handshakeRequest.requestProviderInitialBonding(),
                                handshakeRequest.requestDeviceName(),
                                handshakeRequest.requestRetroactivePair());

                        byte[] seekerPublicAddress = null;
                        if (handshakeRequest.requestProviderInitialBonding()
                                || handshakeRequest.requestRetroactivePair()) {
                            seekerPublicAddress = handshakeRequest.getSeekerPublicAddress();
                            mLogger.log(
                                    "Seeker sends BR/EDR address %s to provider",
                                    BluetoothAddress.encode(seekerPublicAddress));
                        }

                        if (handshakeRequest.requestRetroactivePair()) {
                            if (mBluetoothAdapter.getRemoteDevice(
                                    seekerPublicAddress).getBondState()
                                    != BluetoothDevice.BOND_BONDED) {
                                throw new GeneralSecurityException(
                                        "Address (BR/EDR) is not bonded: "
                                                + BluetoothAddress.encode(seekerPublicAddress));
                            }
                        }

                        return seekerPublicAddress;
                    }

                    @Nullable
                    private byte[] handleActionOverBleRequest(HandshakeRequest handshakeRequest) {
                        // TODO(wollohchou): implement action over ble request.
                        if (handshakeRequest.requestDeviceAction()) {
                            mLogger.log("Requesting action over BLE, device action");
                        } else if (handshakeRequest.requestFollowedByAdditionalData()) {
                            mLogger.log(
                                    "Requesting action over BLE, followed by additional data, "
                                            + "type:%s",
                                    handshakeRequest.getAdditionalDataType());
                        } else {
                            mLogger.log("Requesting action over BLE");
                        }
                        return null;
                    }

                    /**
                     * @return The encrypted device name from provider for seeker to use.
                     */
                    private byte[] createEncryptedDeviceName() throws GeneralSecurityException {
                        byte[] deviceName = getDeviceNameInBytes();
                        String providerName = new String(deviceName, StandardCharsets.UTF_8);
                        mLogger.log(
                                "Sending handshake response for device name %s with size %d",
                                providerName, deviceName.length);
                        return NamingEncoder.encodeNamingPacket(mSecret, providerName);
                    }
                };

        mBeaconActionsServlet =
                new NotifiableGattServlet() {
                    private static final int GATT_ERROR_UNAUTHENTICATED = 0x80;
                    private static final int GATT_ERROR_INVALID_VALUE = 0x81;
                    private static final int NONCE_LENGTH = 8;
                    private static final int ONE_TIME_AUTH_KEY_OFFSET = 2;
                    private static final int ONE_TIME_AUTH_KEY_LENGTH = 8;
                    private static final int IDENTITY_KEY_LENGTH = 32;
                    private static final byte TRANSMISSION_POWER = 0;

                    private final SecureRandom mRandom = new SecureRandom();
                    private final MessageDigest mSha256;
                    @Nullable
                    private byte[] mLastNonce;
                    @Nullable
                    private ByteString mIdentityKey = mOptions.getEddystoneIdentityKey();

                    {
                        try {
                            mSha256 = MessageDigest.getInstance("SHA-256");
                            mSha256.reset();
                        } catch (NoSuchAlgorithmException e) {
                            throw new IllegalStateException(
                                    "System missing SHA-256 implementation.", e);
                        }
                    }

                    @Override
                    // Simulating deprecated API {@code BeaconActionsCharacteristic.ID} for testing.
                    @SuppressWarnings("deprecation")
                    public BluetoothGattCharacteristic getBaseCharacteristic() {
                        return new BluetoothGattCharacteristic(
                                BeaconActionsCharacteristic.CUSTOM_128_BIT_UUID,
                                PROPERTY_READ | PROPERTY_WRITE | PROPERTY_NOTIFY,
                                PERMISSION_READ | PERMISSION_WRITE);
                    }

                    @Override
                    public byte[] read(BluetoothGattServerConnection connection, int offset) {
                        mLastNonce = new byte[NONCE_LENGTH];
                        mRandom.nextBytes(mLastNonce);
                        return mLastNonce;
                    }

                    @Override
                    public void write(
                            BluetoothGattServerConnection connection, int offset, byte[] value)
                            throws BluetoothGattException {
                        mLogger.log("Got value from beacon actions servlet: %s",
                                base16().encode(value));
                        if (value.length == 0) {
                            mLogger.log("Packet length invalid, %d", value.length);
                            throw new BluetoothGattException("Packet length invalid",
                                    GATT_ERROR_INVALID_VALUE);
                        }
                        switch (value[0]) {
                            case BeaconActionType.READ_BEACON_PARAMETERS:
                                handleReadBeaconParameters(value);
                                break;
                            case BeaconActionType.READ_PROVISIONING_STATE:
                                handleReadProvisioningState(value);
                                break;
                            case BeaconActionType.SET_EPHEMERAL_IDENTITY_KEY:
                                handleSetEphemeralIdentityKey(value);
                                break;
                            case BeaconActionType.CLEAR_EPHEMERAL_IDENTITY_KEY:
                            case BeaconActionType.READ_EPHEMERAL_IDENTITY_KEY:
                            case BeaconActionType.RING:
                            case BeaconActionType.READ_RINGING_STATE:
                                throw new BluetoothGattException(
                                        "Unimplemented beacon action",
                                        BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED);
                            default:
                                throw new BluetoothGattException(
                                        "Unknown beacon action",
                                        BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED);
                        }
                    }

                    private boolean verifyAccountKeyToken(byte[] value, boolean ownerOnly)
                            throws BluetoothGattException {
                        if (value.length < ONE_TIME_AUTH_KEY_LENGTH + ONE_TIME_AUTH_KEY_OFFSET) {
                            mLogger.log("Packet length invalid, %d", value.length);
                            throw new BluetoothGattException(
                                    "Packet length invalid", GATT_ERROR_INVALID_VALUE);
                        }
                        byte[] hashedAccountKey =
                                Arrays.copyOfRange(
                                        value,
                                        ONE_TIME_AUTH_KEY_OFFSET,
                                        ONE_TIME_AUTH_KEY_LENGTH + ONE_TIME_AUTH_KEY_OFFSET);
                        if (mLastNonce == null) {
                            throw new BluetoothGattException(
                                    "Nonce wasn't set", GATT_ERROR_UNAUTHENTICATED);
                        }
                        if (ownerOnly) {
                            ByteString accountKey = getOwnerAccountKey();
                            if (accountKey != null) {
                                mSha256.update(accountKey.toByteArray());
                                mSha256.update(mLastNonce);
                                return Arrays.equals(
                                        hashedAccountKey,
                                        Arrays.copyOf(mSha256.digest(), ONE_TIME_AUTH_KEY_LENGTH));
                            }
                        } else {
                            Set<ByteString> accountKeys = getAccountKeys();
                            for (ByteString accountKey : accountKeys) {
                                mSha256.update(accountKey.toByteArray());
                                mSha256.update(mLastNonce);
                                if (Arrays.equals(
                                        hashedAccountKey,
                                        Arrays.copyOf(mSha256.digest(),
                                                ONE_TIME_AUTH_KEY_LENGTH))) {
                                    return true;
                                }
                            }
                        }
                        return false;
                    }

                    private int getBeaconClock() {
                        return (int) TimeUnit.MILLISECONDS.toSeconds(SystemClock.elapsedRealtime());
                    }

                    private ByteString fromBytes(byte... bytes) {
                        return ByteString.copyFrom(bytes);
                    }

                    private byte[] intToByteArray(int value) {
                        byte[] data = new byte[4];
                        data[3] = (byte) value;
                        data[2] = (byte) (value >>> 8);
                        data[1] = (byte) (value >>> 16);
                        data[0] = (byte) (value >>> 24);
                        return data;
                    }

                    private void handleReadBeaconParameters(byte[] value)
                            throws BluetoothGattException {
                        if (!verifyAccountKeyToken(value, /* ownerOnly= */ false)) {
                            throw new BluetoothGattException(
                                    "failed to authenticate account key",
                                    GATT_ERROR_UNAUTHENTICATED);
                        }
                        sendNotification(
                                fromBytes(
                                        (byte) BeaconActionType.READ_BEACON_PARAMETERS,
                                        (byte) 5 /* data length */,
                                        TRANSMISSION_POWER)
                                        .concat(ByteString.copyFrom(
                                                intToByteArray(getBeaconClock())))
                                        .toByteArray());
                    }

                    private void handleReadProvisioningState(byte[] value)
                            throws BluetoothGattException {
                        if (!verifyAccountKeyToken(value, /* ownerOnly= */ false)) {
                            throw new BluetoothGattException(
                                    "failed to authenticate account key",
                                    GATT_ERROR_UNAUTHENTICATED);
                        }
                        byte flags = 0;
                        if (verifyAccountKeyToken(value, /* ownerOnly= */ true)) {
                            flags |= (byte) (1 << 1);
                        }
                        if (mIdentityKey == null) {
                            sendNotification(
                                    fromBytes(
                                            (byte) BeaconActionType.READ_PROVISIONING_STATE,
                                            (byte) 1 /* data length */,
                                            flags)
                                            .toByteArray());
                        } else {
                            flags |= (byte) 1;
                            sendNotification(
                                    fromBytes(
                                            (byte) BeaconActionType.READ_PROVISIONING_STATE,
                                            (byte) 21 /* data length */,
                                            flags)
                                            .concat(
                                                    E2eeCalculator.computeE2eeEid(
                                                            mIdentityKey, /* exponent= */ 10,
                                                            getBeaconClock()))
                                            .toByteArray());
                        }
                    }

                    private void handleSetEphemeralIdentityKey(byte[] value)
                            throws BluetoothGattException {
                        if (!verifyAccountKeyToken(value, /* ownerOnly= */ true)) {
                            throw new BluetoothGattException(
                                    "failed to authenticate owner account key",
                                    GATT_ERROR_UNAUTHENTICATED);
                        }
                        if (value.length
                                != ONE_TIME_AUTH_KEY_LENGTH + ONE_TIME_AUTH_KEY_OFFSET
                                + IDENTITY_KEY_LENGTH) {
                            mLogger.log("Packet length invalid, %d", value.length);
                            throw new BluetoothGattException("Packet length invalid",
                                    GATT_ERROR_INVALID_VALUE);
                        }
                        if (mIdentityKey != null) {
                            throw new BluetoothGattException(
                                    "Device is already provisioned as Eddystone",
                                    GATT_ERROR_UNAUTHENTICATED);
                        }
                        mIdentityKey = Crypto.aesEcbNoPaddingDecrypt(
                                ByteString.copyFrom(mOwnerAccountKey),
                                ByteString.copyFrom(value)
                                        .substring(ONE_TIME_AUTH_KEY_LENGTH
                                                + ONE_TIME_AUTH_KEY_OFFSET));
                    }
                };

        ServiceConfig fastPairServiceConfig =
                new ServiceConfig()
                        .addCharacteristic(accountKeyServlet)
                        .addCharacteristic(keyBasedPairingServlet)
                        .addCharacteristic(mPasskeyServlet)
                        .addCharacteristic(firmwareVersionServlet);
        if (mOptions.getEnableBeaconActionsCharacteristic()) {
            fastPairServiceConfig.addCharacteristic(mBeaconActionsServlet);
        }

        BluetoothGattServerConfig config =
                new BluetoothGattServerConfig()
                        .addService(
                                TransportDiscoveryService.ID,
                                new ServiceConfig()
                                        .addCharacteristic(tdsControlPointServlet)
                                        .addCharacteristic(brHandoverDataServlet)
                                        .addCharacteristic(bluetoothSigServlet))
                        .addService(
                                FastPairService.ID,
                                mOptions.getEnableNameCharacteristic()
                                        ? fastPairServiceConfig.addCharacteristic(
                                        mDeviceNameServlet)
                                        : fastPairServiceConfig);

        mLogger.log(
                "Starting GATT server, support name characteristic %b",
                mOptions.getEnableNameCharacteristic());
        try {
            helper.open(config);
        } catch (BluetoothException e) {
            mLogger.log(e, "Error starting GATT server");
        }
    }

    /** Callback for passkey/pin input. */
    public interface KeyInputCallback {
        void onKeyInput(int key);
    }

    public void enterPassKey(int passkey) {
        mLogger.log("enterPassKey called with passkey %d.", passkey);
        mPairingDevice.setPairingConfirmation(true);
    }

    private void checkPasskey() {
        // There's a race between the PAIRING_REQUEST broadcast from the OS giving us the local
        // passkey, and the remote passkey received over GATT. Skip the check until we have both.
        if (mLocalPasskey == 0 || mRemotePasskey == 0) {
            mLogger.log(
                    "Skipping passkey check, missing local (%s) or remote (%s).",
                    mLocalPasskey, mRemotePasskey);
            return;
        }

        // Regardless of whether it matches, send our (encrypted) passkey to the seeker.
        sendPasskeyToRemoteDevice(mLocalPasskey);

        mLogger.log("Checking localPasskey %s == remotePasskey %s", mLocalPasskey, mRemotePasskey);
        boolean passkeysMatched = mLocalPasskey == mRemotePasskey;
        if (mOptions.getShowsPasskeyConfirmation() && passkeysMatched
                && mPasskeyEventCallback != null) {
            mLogger.log("callbacks the UI for passkey confirmation.");
            mPasskeyEventCallback.onPasskeyConfirmation(mLocalPasskey,
                    this::setPasskeyConfirmation);
        } else {
            setPasskeyConfirmation(passkeysMatched);
        }
    }

    private void sendPasskeyToRemoteDevice(int passkey) {
        try {
            mPasskeyServlet.sendNotification(
                    PasskeyCharacteristic.encrypt(
                            PasskeyCharacteristic.Type.PROVIDER, mSecret, passkey));
        } catch (GeneralSecurityException e) {
            mLogger.log(e, "Failed to encrypt passkey response.");
        }
    }

    public void setFirmwareVersion(String versionNumber) {
        mDeviceFirmwareVersion = versionNumber;
    }

    public void setDynamicBufferSize(boolean support) {
        if (mSupportDynamicBufferSize != support) {
            mSupportDynamicBufferSize = support;
            sendCapabilitySync();
        }
    }

    @VisibleForTesting
    void setPasskeyConfirmationCallback(PasskeyConfirmationCallback callback) {
        this.mPasskeyConfirmationCallback = callback;
    }

    public void setDeviceNameCallback(DeviceNameCallback callback) {
        this.mDeviceNameCallback = callback;
    }

    public void setPasskeyEventCallback(PasskeyEventCallback passkeyEventCallback) {
        this.mPasskeyEventCallback = passkeyEventCallback;
    }

    private void setPasskeyConfirmation(boolean confirm) {
        mPairingDevice.setPairingConfirmation(confirm);
        if (mPasskeyConfirmationCallback != null) {
            mPasskeyConfirmationCallback.onPasskeyConfirmation(confirm);
        }
        mLocalPasskey = 0;
        mRemotePasskey = 0;
    }

    private void becomeDiscoverable() throws InterruptedException, TimeoutException {
        setDiscoverable(true);
    }

    public void cancelDiscovery() throws InterruptedException, TimeoutException {
        setDiscoverable(false);
    }

    private void setDiscoverable(boolean discoverable)
            throws InterruptedException, TimeoutException {
        mIsDiscoverableLatch = new CountDownLatch(1);
        setScanMode(discoverable ? SCAN_MODE_CONNECTABLE_DISCOVERABLE : SCAN_MODE_CONNECTABLE);
        // If we're already discoverable, count down the latch right away. Otherwise,
        // we'll get a broadcast when we successfully become discoverable.
        if (isDiscoverable()) {
            mIsDiscoverableLatch.countDown();
        }
        if (mIsDiscoverableLatch.await(BECOME_DISCOVERABLE_TIMEOUT_SEC, TimeUnit.SECONDS)) {
            mLogger.log("Successfully became switched discoverable mode %s", discoverable);
        } else {
            throw new TimeoutException();
        }
    }

    private void setScanMode(int scanMode) {
        if (mRevertDiscoverableFuture != null) {
            mRevertDiscoverableFuture.cancel(false /* may interrupt if running */);
        }

        mLogger.log("Setting scan mode to %s", scanModeToString(scanMode));
        try {
            Method method = mBluetoothAdapter.getClass().getMethod("setScanMode", Integer.TYPE);
            method.invoke(mBluetoothAdapter, scanMode);

            if (scanMode == SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
                mRevertDiscoverableFuture =
                        mExecutor.schedule(() -> setScanMode(SCAN_MODE_CONNECTABLE),
                                SCAN_MODE_REFRESH_SEC, TimeUnit.SECONDS);
            }
        } catch (Exception e) {
            mLogger.log(e, "Error setting scan mode to %d", scanMode);
        }
    }

    public static String scanModeToString(int scanMode) {
        switch (scanMode) {
            case SCAN_MODE_CONNECTABLE_DISCOVERABLE:
                return "DISCOVERABLE";
            case SCAN_MODE_CONNECTABLE:
                return "CONNECTABLE";
            case SCAN_MODE_NONE:
                return "NOT CONNECTABLE";
            default:
                return "UNKNOWN(" + scanMode + ")";
        }
    }

    private ResultCode checkTdsControlPointRequest(byte[] request) {
        if (request.length < 2) {
            mLogger.log(
                    new IllegalArgumentException(), "Expected length >= 2 for %s",
                    base16().encode(request));
            return ResultCode.INVALID_PARAMETER;
        }
        byte opCode = getTdsControlPointOpCode(request);
        if (opCode != ControlPointCharacteristic.ACTIVATE_TRANSPORT_OP_CODE) {
            mLogger.log(
                    new IllegalArgumentException(),
                    "Expected Activate Transport op code (0x01), got %d",
                    opCode);
            return ResultCode.OP_CODE_NOT_SUPPORTED;
        }
        if (request[1] != BLUETOOTH_SIG_ORGANIZATION_ID) {
            mLogger.log(
                    new IllegalArgumentException(),
                    "Expected Bluetooth SIG organization ID (0x01), got %d",
                    request[1]);
            return ResultCode.UNSUPPORTED_ORGANIZATION_ID;
        }
        return ResultCode.SUCCESS;
    }

    private static byte getTdsControlPointOpCode(byte[] request) {
        return request.length < 1 ? 0x00 : request[0];
    }

    private boolean isDiscoverable() {
        return mBluetoothAdapter.getScanMode() == SCAN_MODE_CONNECTABLE_DISCOVERABLE;
    }

    private byte[] modelIdServiceData(boolean forAdvertising) {
        // Note: This used to be little-endian but is now big-endian. See b/78229467 for details.
        byte[] modelIdPacket =
                base16().decode(
                        forAdvertising ? mOptions.getAdvertisingModelId() : mOptions.getModelId());
        if (!mBatteryValues.isEmpty()) {
            // If we are going to advertise battery values with the packet, then switch to the
            // non-3-byte model ID format.
            modelIdPacket = concat(new byte[]{0b00000110}, modelIdPacket);
        }
        return modelIdPacket;
    }

    private byte[] accountKeysServiceData() {
        try {
            return concat(new byte[]{0x00}, generateBloomFilterFields());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Unable to build bloom filter.", e);
        }
    }

    private byte[] transportDiscoveryData() {
        byte[] transportData = SUPPORTED_SERVICES_LTV;
        return Bytes.concat(
                new byte[]{BLUETOOTH_SIG_ORGANIZATION_ID},
                new byte[]{tdsFlags(isDiscoverable() ? TransportState.ON : TransportState.OFF)},
                new byte[]{(byte) transportData.length},
                transportData);
    }

    private byte[] generateBloomFilterFields() throws NoSuchAlgorithmException {
        Set<ByteString> accountKeys = getAccountKeys();
        if (accountKeys.isEmpty()) {
            return new byte[0];
        }
        BloomFilter bloomFilter =
                new BloomFilter(
                        new byte[(int) (1.2 * accountKeys.size()) + 3],
                        new FastPairBloomFilterHasher());
        String address = mBleAddress == null ? SIMULATOR_FAKE_BLE_ADDRESS : mBleAddress;

        // Simulator supports Central Address Resolution characteristic, so when paired, the BLE
        // address in Seeker will be resolved to BR/EDR address. This caused Seeker fails on
        // checking the bloom filter due to different address is used for salting. In order to
        // let battery values notification be shown on paired device, we use random salt to
        // workaround it.
        boolean advertisingBatteryValues = !mBatteryValues.isEmpty();
        byte[] salt;
        if (mOptions.getUseRandomSaltForAccountKeyRotation() || advertisingBatteryValues) {
            salt = new byte[1];
            new SecureRandom().nextBytes(salt);
            mLogger.log("Using random salt %s for bloom filter", base16().encode(salt));
        } else {
            salt = BluetoothAddress.decode(address);
            mLogger.log("Using address %s for bloom filter", address);
        }

        // To prevent tampering, account filter shall be slightly modified to include battery data
        // when the battery values are included in the advertisement. Normally, when building the
        // account filter, a value V is produce by combining the account key with a salt. Instead,
        // when battery values are also being advertised, it be constructed as follows:
        // - the first 16 bytes are account key.
        // - the next bytes are the salt.
        // - the remaining bytes are the battery data.
        byte[] saltAndBatteryData =
                advertisingBatteryValues ? concat(salt, generateBatteryData()) : salt;

        for (ByteString accountKey : accountKeys) {
            bloomFilter.add(concat(accountKey.toByteArray(), saltAndBatteryData));
        }
        byte[] packet = generateAccountKeyData(bloomFilter);
        return mOptions.getUseRandomSaltForAccountKeyRotation() || advertisingBatteryValues
                // Create a header with length 1 and type 1 for a random salt.
                ? concat(packet, createField((byte) 0x11, salt))
                // Exclude the salt from the packet, BLE address will be assumed by the client.
                : packet;
    }

    /**
     * Creates a new field for the packet.
     *
     * The header is formatted 0xLLLLTTTT where LLLL is the
     * length of the field and TTTT is the type (0 for bloom filter, 1 for salt).
     */
    private byte[] createField(byte header, byte[] value) {
        return concat(new byte[]{header}, value);
    }

    public int getTxPower() {
        return mOptions.getTxPowerLevel();
    }

    @Nullable
    byte[] getServiceData() {
        byte[] packet =
                isDiscoverable()
                        ? modelIdServiceData(/* forAdvertising= */ true)
                        : !getAccountKeys().isEmpty() ? accountKeysServiceData() : null;
        return addBatteryValues(packet);
    }

    @Nullable
    private byte[] addBatteryValues(byte[] packet) {
        if (mBatteryValues.isEmpty() || packet == null) {
            return packet;
        }

        return concat(packet, generateBatteryData());
    }

    private byte[] generateBatteryData() {
        // Byte 0: Battery length and type, first 4 bits are the number of battery values, second
        // 4 are the type.
        // Byte 1 - length: Battery values, the first bit is charging status, the remaining bits are
        // the actual value between 0 and 100, or -1 for unknown.
        byte[] batteryData = new byte[mBatteryValues.size() + 1];
        batteryData[0] = (byte) (mBatteryValues.size() << 4
                | (mSuppressBatteryNotification ? 0b0100 : 0b0011));

        int batteryValueIndex = 1;
        for (BatteryValue batteryValue : mBatteryValues) {
            batteryData[batteryValueIndex++] =
                    (byte)
                            ((batteryValue.mCharging ? 0b10000000 : 0b00000000)
                                    | (0b01111111 & batteryValue.mLevel));
        }

        return batteryData;
    }

    private byte[] generateAccountKeyData(BloomFilter bloomFilter) {
        // Byte 0: length and type, first 4 bits are the length of bloom filter, second 4 are the
        // type which indicating the subsequent pairing notification is suppressed or not.
        // The following bytes are the data of bloom filter.
        byte[] filterBytes = bloomFilter.asBytes();
        byte lengthAndType = (byte) (filterBytes.length << 4
                | (mSuppressSubsequentPairingNotification ? 0b0010 : 0b0000));
        mLogger.log(
                "Generate bloom filter with suppress subsequent pairing notification:%b",
                mSuppressSubsequentPairingNotification);
        return createField(lengthAndType, filterBytes);
    }

    /** Disconnects all bonded devices. */
    public void disconnectAllBondedDevices() {
        for (BluetoothDevice device : mBluetoothAdapter.getBondedDevices()) {
            if (device.getBluetoothClass().getMajorDeviceClass() == Major.PHONE) {
                removeBond(device);
            }
        }
    }

    public void disconnect(BluetoothProfile profile, BluetoothDevice device) {
        device.disconnect();
    }

    public void removeBond(BluetoothDevice device) {
        device.removeBond();
    }

    public void resetAccountKeys() {
        mFastPairSimulatorDatabase.setAccountKeys(new HashSet<>());
        mFastPairSimulatorDatabase.setFastPairSeekerDevices(new HashSet<>());
        mAccountKey = null;
        mOwnerAccountKey = null;
        mLogger.log("Remove all account keys");
    }

    public void addAccountKey(byte[] key) {
        addAccountKey(key, /* device= */ null);
    }

    private void addAccountKey(byte[] key, @Nullable BluetoothDevice device) {
        mAccountKey = key;
        if (mOwnerAccountKey == null) {
            mOwnerAccountKey = key;
        }

        mFastPairSimulatorDatabase.addAccountKey(key);
        mFastPairSimulatorDatabase.addFastPairSeekerDevice(device, key);
        mLogger.log("Add account key: key=%s, device=%s", base16().encode(key), device);
    }

    private Set<ByteString> getAccountKeys() {
        return mFastPairSimulatorDatabase.getAccountKeys();
    }

    /** Get the latest account key. */
    @Nullable
    public ByteString getAccountKey() {
        if (mAccountKey == null) {
            return null;
        }
        return ByteString.copyFrom(mAccountKey);
    }

    /** Get the owner account key (the first account key registered). */
    @Nullable
    public ByteString getOwnerAccountKey() {
        if (mOwnerAccountKey == null) {
            return null;
        }
        return ByteString.copyFrom(mOwnerAccountKey);
    }

    public void resetDeviceName() {
        mFastPairSimulatorDatabase.setLocalDeviceName(null);
        // Trigger simulator to update device name text view.
        if (mDeviceNameCallback != null) {
            mDeviceNameCallback.onNameChanged(getDeviceName());
        }
    }

    // This method is used in test case with default name in provider.
    public void setDeviceName(String deviceName) {
        setDeviceName(deviceName.getBytes(StandardCharsets.UTF_8));
    }

    private void setDeviceName(@Nullable byte[] deviceName) {
        mFastPairSimulatorDatabase.setLocalDeviceName(deviceName);

        mLogger.log("Save device name : %s", getDeviceName());
        // Trigger simulator to update device name text view.
        if (mDeviceNameCallback != null) {
            mDeviceNameCallback.onNameChanged(getDeviceName());
        }
    }

    @Nullable
    private byte[] getDeviceNameInBytes() {
        return mFastPairSimulatorDatabase.getLocalDeviceName();
    }

    @Nullable
    public String getDeviceName() {
        String providerDeviceName =
                getDeviceNameInBytes() != null
                        ? new String(getDeviceNameInBytes(), StandardCharsets.UTF_8)
                        : null;
        mLogger.log("get device name = %s", providerDeviceName);
        return providerDeviceName;
    }

    /**
     * Bit index: Description - Value
     *
     * <ul>
     *   <li>0-1: Role - 0b10 (Provider only)
     *   <li>2: Transport Data Incomplete: 0 (false)
     *   <li>3-4: Transport State (0b00: Off, 0b01: On, 0b10: Temporarily Unavailable)
     *   <li>5-7: Reserved for future use
     * </ul>
     */
    private static byte tdsFlags(TransportState transportState) {
        return (byte) (0b00000010 & (transportState.mByteValue << 3));
    }

    /** Detailed information about battery value. */
    public static class BatteryValue {
        boolean mCharging;

        // The range is 0 ~ 100, and -1 represents the battery level is unknown.
        int mLevel;

        public BatteryValue(boolean charging, int level) {
            this.mCharging = charging;
            this.mLevel = level;
        }
    }
}
