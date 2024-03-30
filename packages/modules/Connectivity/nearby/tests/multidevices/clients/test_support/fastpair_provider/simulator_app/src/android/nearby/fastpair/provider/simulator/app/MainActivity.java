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

package android.nearby.fastpair.provider.simulator.app;

import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static android.nearby.fastpair.provider.simulator.SimulatorStreamProtocol.Event.Code.BLUETOOTH_STATE_BOND;
import static android.nearby.fastpair.provider.simulator.SimulatorStreamProtocol.Event.Code.BLUETOOTH_STATE_CONNECTION;
import static android.nearby.fastpair.provider.simulator.SimulatorStreamProtocol.Event.Code.BLUETOOTH_STATE_SCAN_MODE;

import static com.google.common.io.BaseEncoding.base16;
import static com.google.common.io.BaseEncoding.base64;

import android.Manifest.permission;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.AdvertiseSettings;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.nearby.fastpair.provider.EventStreamProtocol.EventGroup;
import android.nearby.fastpair.provider.FastPairSimulator;
import android.nearby.fastpair.provider.FastPairSimulator.BatteryValue;
import android.nearby.fastpair.provider.FastPairSimulator.KeyInputCallback;
import android.nearby.fastpair.provider.FastPairSimulator.PasskeyEventCallback;
import android.nearby.fastpair.provider.bluetooth.BluetoothController;
import android.nearby.fastpair.provider.simulator.SimulatorStreamProtocol.Event;
import android.nearby.fastpair.provider.simulator.testing.RemoteDevice;
import android.nearby.fastpair.provider.simulator.testing.RemoteDevicesManager;
import android.nearby.fastpair.provider.simulator.testing.StreamIOHandlerFactory;
import android.nearby.fastpair.provider.utils.Logger;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.text.method.ScrollingMovementMethod;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.core.util.Consumer;

import com.google.common.base.Ascii;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.errorprone.annotations.FormatMethod;
import com.google.protobuf.ByteString;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executors;

import service.proto.Rpcs.AntiSpoofingKeyPair;
import service.proto.Rpcs.Device;
import service.proto.Rpcs.DeviceType;

/**
 * Simulates a Fast Pair device (e.g. a headset).
 *
 * <p>See README in this directory, and {http://go/fast-pair-spec}.
 */
@SuppressLint("SetTextI18n")
public class MainActivity extends Activity {
    public static final String TAG = "FastPairProviderSimulatorApp";
    private final Logger mLogger = new Logger(TAG);

    /** Device has a display and the ability to input Yes/No. */
    private static final int IO_CAPABILITY_IO = 1;

    /** Device only has a keyboard for entry but no display. */
    private static final int IO_CAPABILITY_IN = 2;

    /** Device has no Input or Output capability. */
    private static final int IO_CAPABILITY_NONE = 3;

    /** Device has a display and a full keyboard. */
    private static final int IO_CAPABILITY_KBDISP = 4;

    private static final String SHARED_PREFS_NAME =
            "android.nearby.fastpair.provider.simulator.app";
    private static final String EXTRA_MODEL_ID = "MODEL_ID";
    private static final String EXTRA_BLUETOOTH_ADDRESS = "BLUETOOTH_ADDRESS";
    private static final String EXTRA_TX_POWER_LEVEL = "TX_POWER_LEVEL";
    private static final String EXTRA_FIRMWARE_VERSION = "FIRMWARE_VERSION";
    private static final String EXTRA_SUPPORT_DYNAMIC_SIZE = "SUPPORT_DYNAMIC_SIZE";
    private static final String EXTRA_USE_RANDOM_SALT_FOR_ACCOUNT_KEY_ROTATION =
            "USE_RANDOM_SALT_FOR_ACCOUNT_KEY_ROTATION";
    private static final String EXTRA_REMOTE_DEVICE_ID = "REMOTE_DEVICE_ID";
    private static final String EXTRA_USE_NEW_GATT_CHARACTERISTICS_ID =
            "USE_NEW_GATT_CHARACTERISTICS_ID";
    public static final String EXTRA_REMOVE_ALL_DEVICES_DURING_PAIRING =
            "REMOVE_ALL_DEVICES_DURING_PAIRING";
    private static final String KEY_ACCOUNT_NAME = "ACCOUNT_NAME";
    private static final String[] PERMISSIONS =
            new String[]{permission.BLUETOOTH, permission.BLUETOOTH_ADMIN, permission.GET_ACCOUNTS};
    private static final int LIGHT_GREEN = 0xFFC8FFC8;
    private static final String ANTI_SPOOFING_KEY_LABEL = "Anti-spoofing key";

    private static final ImmutableMap<String, String> ANTI_SPOOFING_PRIVATE_KEY_MAP =
            new ImmutableMap.Builder<String, String>()
                    .put("361A2E", "/1rMqyJRGeOK6vkTNgM70xrytxdKg14mNQkITeusK20=")
                    .put("00000D", "03/MAmUPTGNsN+2iA/1xASXoPplDh3Ha5/lk2JgEBx4=")
                    .put("00000C", "Cbj9eCJrTdDgSYxLkqtfADQi86vIaMvxJsQ298sZYWE=")
                    // BLE only devices
                    .put("49426D", "I5QFOJW0WWFgKKZiwGchuseXsq/p9RN/aYtNsGEVGT0=")
                    .put("01E5CE", "FbHt8STpHJDd4zFQFjimh4Zt7IU94U28MOEIXgUEeCw=")
                    .put("8D13B9", "mv++LcJB1n0mbLNGWlXCv/8Gb6aldctrJC4/Ma/Q3Rg=")
                    .put("9AB0F6", "9eKQNwJUr5vCg0c8rtOXkJcWTAsBmmvEKSgXIqAd50Q=")
                    // Android Auto
                    .put("8E083D", "hGQeREDKM/H1834zWMmTIe0Ap4Zl5igThgE62OtdcKA=")
                    .buildOrThrow();

    private static final Uri REMOTE_DEVICE_INPUT_STREAM_URI =
            Uri.fromFile(new File("/data/local/nearby/tmp/read.pipe"));

    private static final Uri REMOTE_DEVICE_OUTPUT_STREAM_URI =
            Uri.fromFile(new File("/data/local/nearby/tmp/write.pipe"));

    private static final String MODEL_ID_DEFAULT = "00000C";

    private static final String MODEL_ID_APP_LAUNCH = "60EB56";

    private static final int MODEL_ID_LENGTH = 6;

    private BluetoothController mBluetoothController;
    private final BluetoothController.EventListener mEventListener =
            new BluetoothController.EventListener() {

                @Override
                public void onBondStateChanged(int bondState) {
                    sendEventToRemoteDevice(
                            Event.newBuilder().setCode(BLUETOOTH_STATE_BOND).setBondState(
                                    bondState));
                    updateStatusView();
                }

                @Override
                public void onConnectionStateChanged(int connectionState) {
                    sendEventToRemoteDevice(
                            Event.newBuilder()
                                    .setCode(BLUETOOTH_STATE_CONNECTION)
                                    .setConnectionState(connectionState));
                    updateStatusView();
                }

                @Override
                public void onScanModeChange(int mode) {
                    sendEventToRemoteDevice(
                            Event.newBuilder().setCode(BLUETOOTH_STATE_SCAN_MODE).setScanMode(
                                    mode));
                    updateStatusView();
                }

                @Override
                public void onA2DPSinkProfileConnected() {
                    reset();
                }
            };

    @Nullable
    private FastPairSimulator mFastPairSimulator;
    @Nullable
    private AlertDialog mInputPasskeyDialog;
    private Switch mFailSwitch;
    private Switch mAppLaunchSwitch;
    private Spinner mAdvOptionSpinner;
    private Spinner mEventStreamSpinner;
    private EventGroup mEventGroup;
    private SharedPreferences mSharedPreferences;
    private Spinner mModelIdSpinner;
    private final RemoteDevicesManager mRemoteDevicesManager = new RemoteDevicesManager();
    @Nullable
    private RemoteDeviceListener mInputStreamListener;
    @Nullable
    String mRemoteDeviceId;
    private final Map<String, Device> mModelsMap = new LinkedHashMap<>();
    private boolean mRemoveAllDevicesDuringPairing = true;

    void sendEventToRemoteDevice(Event.Builder eventBuilder) {
        if (mRemoteDeviceId == null) {
            return;
        }

        mLogger.log("Send data to output stream: %s", eventBuilder.getCode().getNumber());
        mRemoteDevicesManager.writeDataToRemoteDevice(
                mRemoteDeviceId,
                eventBuilder.build().toByteString(),
                FutureCallbackWrapper.createDefaultIOCallback(this));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        mSharedPreferences = getSharedPreferences(SHARED_PREFS_NAME, MODE_PRIVATE);

        mRemoveAllDevicesDuringPairing =
                getIntent().getBooleanExtra(EXTRA_REMOVE_ALL_DEVICES_DURING_PAIRING, true);

        mFailSwitch = findViewById(R.id.fail_switch);
        mFailSwitch.setOnCheckedChangeListener((CompoundButton buttonView, boolean isChecked) -> {
            if (mFastPairSimulator != null) {
                mFastPairSimulator.setShouldFailPairing(isChecked);
            }
        });

        mAppLaunchSwitch = findViewById(R.id.app_launch_switch);
        mAppLaunchSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> reset());

        mAdvOptionSpinner = findViewById(R.id.adv_option_spinner);
        mEventStreamSpinner = findViewById(R.id.event_stream_spinner);
        ArrayAdapter<CharSequence> advOptionAdapter =
                ArrayAdapter.createFromResource(
                        this, R.array.adv_options, android.R.layout.simple_spinner_item);
        ArrayAdapter<CharSequence> eventStreamAdapter =
                ArrayAdapter.createFromResource(
                        this, R.array.event_stream_options, android.R.layout.simple_spinner_item);
        advOptionAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mAdvOptionSpinner.setAdapter(advOptionAdapter);
        mEventStreamSpinner.setAdapter(eventStreamAdapter);
        mAdvOptionSpinner.setOnItemSelectedListener(new OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int position,
                    long id) {
                startAdvertisingBatteryInformationBasedOnOption(position);
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
            }
        });
        mEventStreamSpinner.setOnItemSelectedListener(new OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position,
                    long id) {
                switch (EventGroup.forNumber(position + 1)) {
                    case BLUETOOTH:
                        mEventGroup = EventGroup.BLUETOOTH;
                        break;
                    case LOGGING:
                        mEventGroup = EventGroup.LOGGING;
                        break;
                    case DEVICE:
                        mEventGroup = EventGroup.DEVICE;
                        break;
                    default:
                        // fall through
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        setupModelIdSpinner();
        setupRemoteDevices();
        if (checkPermissions(PERMISSIONS)) {
            mBluetoothController = new BluetoothController(this, mEventListener);
            mBluetoothController.registerBluetoothStateReceiver();
            mBluetoothController.enableBluetooth();
            mBluetoothController.connectA2DPSinkProfile();

            if (mSharedPreferences.getString(KEY_ACCOUNT_NAME, "").isEmpty()) {
                putFixedModelLocal();
                resetModelIdSpinner();
                reset();
            }
        } else {
            requestPermissions(PERMISSIONS, 0 /* requestCode */);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu, menu);
        menu.findItem(R.id.use_new_gatt_characteristics_id).setChecked(
                getFromIntentOrPrefs(
                        EXTRA_USE_NEW_GATT_CHARACTERISTICS_ID, /* defaultValue= */ false));
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.sign_out_menu_item) {
            recreate();
            return true;
        } else if (item.getItemId() == R.id.reset_account_keys_menu_item) {
            resetAccountKeys();
            return true;
        } else if (item.getItemId() == R.id.reset_device_name_menu_item) {
            resetDeviceName();
            return true;
        } else if (item.getItemId() == R.id.set_firmware_version) {
            setFirmware();
            return true;
        } else if (item.getItemId() == R.id.set_simulator_capability) {
            setSimulatorCapability();
            return true;
        } else if (item.getItemId() == R.id.use_new_gatt_characteristics_id) {
            if (!item.isChecked()) {
                item.setChecked(true);
                mSharedPreferences.edit()
                        .putBoolean(EXTRA_USE_NEW_GATT_CHARACTERISTICS_ID, true).apply();
            } else {
                item.setChecked(false);
                mSharedPreferences.edit()
                        .putBoolean(EXTRA_USE_NEW_GATT_CHARACTERISTICS_ID, false).apply();
            }
            reset();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void setFirmware() {
        View firmwareInputView =
                LayoutInflater.from(getApplicationContext()).inflate(R.layout.user_input_dialog,
                        null);
        EditText userInputDialogEditText = firmwareInputView.findViewById(R.id.userInputDialog);
        new AlertDialog.Builder(MainActivity.this)
                .setView(firmwareInputView)
                .setCancelable(false)
                .setPositiveButton(android.R.string.ok, (dialogBox, id) -> {
                    String input = userInputDialogEditText.getText().toString();
                    mSharedPreferences.edit().putString(EXTRA_FIRMWARE_VERSION,
                            input).apply();
                    reset();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .setTitle(R.string.firmware_dialog_title)
                .show();
    }

    private void setSimulatorCapability() {
        String[] capabilityKeys = new String[]{EXTRA_SUPPORT_DYNAMIC_SIZE};
        String[] capabilityNames = new String[]{"Dynamic Buffer Size"};
        // Default values.
        boolean[] capabilitySelected = new boolean[]{false};
        // Get from preferences if exist.
        for (int i = 0; i < capabilityKeys.length; i++) {
            capabilitySelected[i] =
                    mSharedPreferences.getBoolean(capabilityKeys[i], capabilitySelected[i]);
        }

        new AlertDialog.Builder(MainActivity.this)
                .setMultiChoiceItems(
                        capabilityNames,
                        capabilitySelected,
                        (dialog, which, isChecked) -> capabilitySelected[which] = isChecked)
                .setCancelable(false)
                .setPositiveButton(
                        android.R.string.ok,
                        (dialogBox, id) -> {
                            for (int i = 0; i < capabilityKeys.length; i++) {
                                mSharedPreferences
                                        .edit()
                                        .putBoolean(capabilityKeys[i], capabilitySelected[i])
                                        .apply();
                            }
                            setCapabilityToSimulator();
                        })
                .setNegativeButton(android.R.string.cancel, null)
                .setTitle("Simulator Capability")
                .show();
    }

    private void setCapabilityToSimulator() {
        if (mFastPairSimulator != null) {
            mFastPairSimulator.setDynamicBufferSize(
                    getFromIntentOrPrefs(EXTRA_SUPPORT_DYNAMIC_SIZE, false));
        }
    }

    private static String getModelIdString(long id) {
        String result = Ascii.toUpperCase(Long.toHexString(id));
        while (result.length() < MODEL_ID_LENGTH) {
            result = "0" + result;
        }
        return result;
    }

    private void putFixedModelLocal() {
        mModelsMap.put(
                "00000C",
                Device.newBuilder()
                        .setId(12)
                        .setAntiSpoofingKeyPair(AntiSpoofingKeyPair.newBuilder().build())
                        .setDeviceType(DeviceType.HEADPHONES)
                        .build());
    }

    private void setupModelIdSpinner() {
        mModelIdSpinner = findViewById(R.id.model_id_spinner);

        ArrayAdapter<String> modelIdAdapter =
                new ArrayAdapter<>(this, android.R.layout.simple_spinner_item);
        modelIdAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mModelIdSpinner.setAdapter(modelIdAdapter);
        resetModelIdSpinner();
        mModelIdSpinner.setOnItemSelectedListener(new OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position,
                    long id) {
                setModelId(mModelsMap.keySet().toArray(new String[0])[position]);
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
            }
        });
    }

    private void setupRemoteDevices() {
        if (Strings.isNullOrEmpty(getIntent().getStringExtra(EXTRA_REMOTE_DEVICE_ID))) {
            mLogger.log("Can't get remote device id");
            return;
        }
        mRemoteDeviceId = getIntent().getStringExtra(EXTRA_REMOTE_DEVICE_ID);
        mInputStreamListener = new RemoteDeviceListener(this);

        try {
            mRemoteDevicesManager.registerRemoteDevice(
                    mRemoteDeviceId,
                    new RemoteDevice(
                            mRemoteDeviceId,
                            StreamIOHandlerFactory.createStreamIOHandler(
                                    StreamIOHandlerFactory.Type.LOCAL_FILE,
                                    REMOTE_DEVICE_INPUT_STREAM_URI,
                                    REMOTE_DEVICE_OUTPUT_STREAM_URI),
                            mInputStreamListener));
        } catch (IOException e) {
            mLogger.log(e, "Failed to create stream IO handler");
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @UiThread
    private void resetModelIdSpinner() {
        ArrayAdapter adapter = (ArrayAdapter) mModelIdSpinner.getAdapter();
        if (adapter == null) {
            return;
        }

        adapter.clear();
        if (!mModelsMap.isEmpty()) {
            for (String modelId : mModelsMap.keySet()) {
                adapter.add(modelId + "-" + mModelsMap.get(modelId).getName());
            }
            mModelIdSpinner.setEnabled(true);
            int newPos = getPositionFromModelId(getModelId());
            if (newPos < 0) {
                String newModelId = mModelsMap.keySet().iterator().next();
                Toast.makeText(this,
                        "Can't find Model ID " + getModelId() + " from console, reset it to "
                                + newModelId, Toast.LENGTH_SHORT).show();
                setModelId(newModelId);
                newPos = 0;
            }
            mModelIdSpinner.setSelection(newPos, /* animate= */ false);
        } else {
            mModelIdSpinner.setEnabled(false);
        }
    }

    private String getModelId() {
        return getFromIntentOrPrefs(EXTRA_MODEL_ID, MODEL_ID_DEFAULT).toUpperCase(Locale.US);
    }

    private boolean setModelId(String modelId) {
        String validModelId = getValidModelId(modelId);
        if (TextUtils.isEmpty(validModelId)) {
            mLogger.log("Can't do setModelId because inputted modelId is invalid!");
            return false;
        }

        if (getModelId().equals(validModelId)) {
            return false;
        }
        mSharedPreferences.edit().putString(EXTRA_MODEL_ID, validModelId).apply();
        reset();
        return true;
    }

    @Nullable
    private static String getValidModelId(String modelId) {
        if (TextUtils.isEmpty(modelId) || modelId.length() < MODEL_ID_LENGTH) {
            return null;
        }

        return modelId.substring(0, MODEL_ID_LENGTH).toUpperCase(Locale.US);
    }

    private int getPositionFromModelId(String modelId) {
        int i = 0;
        for (String id : mModelsMap.keySet()) {
            if (id.equals(modelId)) {
                return i;
            }
            i++;
        }
        return -1;
    }

    private void resetAccountKeys() {
        if (mFastPairSimulator != null) {
            mFastPairSimulator.resetAccountKeys();
            mFastPairSimulator.startAdvertising();
        }
    }

    private void resetDeviceName() {
        if (mFastPairSimulator != null) {
            mFastPairSimulator.resetDeviceName();
        }
    }

    /** Called via activity_main.xml */
    public void onResetButtonClicked(View view) {
        reset();
    }

    /** Called via activity_main.xml */
    public void onSendEventStreamMessageButtonClicked(View view) {
        if (mFastPairSimulator != null) {
            mFastPairSimulator.sendEventStreamMessageToRfcommDevices(mEventGroup);
        }
    }

    void reset() {
        Button resetButton = findViewById(R.id.reset_button);
        if (mModelsMap.isEmpty() || !resetButton.isEnabled()) {
            return;
        }
        resetButton.setText("Resetting...");
        resetButton.setEnabled(false);
        mModelIdSpinner.setEnabled(false);
        mAppLaunchSwitch.setEnabled(false);

        if (mFastPairSimulator != null) {
            mFastPairSimulator.stopAdvertising();

            if (mBluetoothController.getRemoteDevice() != null) {
                if (mRemoveAllDevicesDuringPairing) {
                    mFastPairSimulator.removeBond(mBluetoothController.getRemoteDevice());
                }
                mBluetoothController.clearRemoteDevice();
            }
            // To be safe, also unpair from all phones (this covers the case where you kill +
            // relaunch the
            // simulator while paired).
            if (mRemoveAllDevicesDuringPairing) {
                mFastPairSimulator.disconnectAllBondedDevices();
            }
            // Sometimes a device will still be connected even though it's not bonded. :( Clear
            // that too.
            BluetoothProfile profileProxy = mBluetoothController.getA2DPSinkProfileProxy();
            for (BluetoothDevice device : profileProxy.getConnectedDevices()) {
                mFastPairSimulator.disconnect(profileProxy, device);
            }
        }
        updateStatusView();

        if (mFastPairSimulator != null) {
            mFastPairSimulator.destroy();
        }
        TextView textView = (TextView) findViewById(R.id.text_view);
        textView.setText("");
        textView.setMovementMethod(new ScrollingMovementMethod());

        String modelId = getModelId();

        String txPower = getFromIntentOrPrefs(EXTRA_TX_POWER_LEVEL, "HIGH");
        updateStringStatusView(R.id.tx_power_text_view, "TxPower", txPower);

        String bluetoothAddress = getFromIntentOrPrefs(EXTRA_BLUETOOTH_ADDRESS, "");

        String firmwareVersion = getFromIntentOrPrefs(EXTRA_FIRMWARE_VERSION, "1.1");
        try {
            Preconditions.checkArgument(base16().decode(bluetoothAddress).length == 6);
        } catch (IllegalArgumentException e) {
            mLogger.log("Invalid BLUETOOTH_ADDRESS extra (%s), using default.", bluetoothAddress);
            bluetoothAddress = null;
        }
        final String finalBluetoothAddress = bluetoothAddress;

        updateStringStatusView(
                R.id.anti_spoofing_private_key_text_view, ANTI_SPOOFING_KEY_LABEL, "Loading...");

        boolean useRandomSaltForAccountKeyRotation =
                getFromIntentOrPrefs(EXTRA_USE_RANDOM_SALT_FOR_ACCOUNT_KEY_ROTATION, false);

        Executors.newSingleThreadExecutor().execute(() -> {
            // Fetch the anti-spoofing key corresponding to this model ID (if it
            // exists).
            // The account must have Project Viewer permission for the project
            // that owns
            // the model ID (normally discoverer-test or discoverer-devices).
            byte[] antiSpoofingKey = getAntiSpoofingKey(modelId);
            String antiSpoofingKeyString;
            Device device = mModelsMap.get(modelId);
            if (antiSpoofingKey != null) {
                antiSpoofingKeyString = base64().encode(antiSpoofingKey);
            } else {
                if (mSharedPreferences.getString(KEY_ACCOUNT_NAME, "").isEmpty()) {
                    antiSpoofingKeyString = "Can't fetch, no account";
                } else {
                    if (device == null) {
                        antiSpoofingKeyString = String.format(Locale.US,
                                "Can't find model %s from console", modelId);
                    } else if (!device.hasAntiSpoofingKeyPair()) {
                        antiSpoofingKeyString = String.format(Locale.US,
                                "Can't find AntiSpoofingKeyPair for model %s", modelId);
                    } else if (device.getAntiSpoofingKeyPair().getPrivateKey().isEmpty()) {
                        antiSpoofingKeyString = String.format(Locale.US,
                                "Can't find privateKey for model %s", modelId);
                    } else {
                        antiSpoofingKeyString = "Unknown error";
                    }
                }
            }

            int desiredIoCapability = getIoCapabilityFromModelId(modelId);

            mBluetoothController.setIoCapability(
                    /*ioCapabilityClassic=*/ desiredIoCapability,
                    /*ioCapabilityBLE=*/ desiredIoCapability);

            runOnUiThread(() -> {
                updateStringStatusView(
                        R.id.anti_spoofing_private_key_text_view,
                        ANTI_SPOOFING_KEY_LABEL,
                        antiSpoofingKeyString);
                FastPairSimulator.Options option = FastPairSimulator.Options.builder(modelId)
                        .setAdvertisingModelId(
                                mAppLaunchSwitch.isChecked() ? MODEL_ID_APP_LAUNCH : modelId)
                        .setBluetoothAddress(finalBluetoothAddress)
                        .setTxPowerLevel(toTxPowerLevel(txPower))
                        .setAdvertisingChangedCallback(isAdvertising -> updateStatusView())
                        .setAntiSpoofingPrivateKey(antiSpoofingKey)
                        .setUseRandomSaltForAccountKeyRotation(useRandomSaltForAccountKeyRotation)
                        .setDataOnlyConnection(device != null && device.getDataOnlyConnection())
                        .setShowsPasskeyConfirmation(
                                device.getDeviceType().equals(DeviceType.ANDROID_AUTO))
                        .setRemoveAllDevicesDuringPairing(mRemoveAllDevicesDuringPairing)
                        .build();
                Logger textViewLogger = new Logger(FastPairSimulator.TAG) {

                    @FormatMethod
                    public void log(@Nullable Throwable exception, String message,
                            Object... objects) {
                        super.log(exception, message, objects);

                        String exceptionMessage = (exception == null) ? ""
                                : " - " + exception.getMessage();
                        final String finalMessage =
                                String.format(message, objects) + exceptionMessage;

                        textView.post(() -> {
                            String newText =
                                    textView.getText() + "\n\n" + finalMessage;
                            textView.setText(newText);
                        });
                    }
                };
                mFastPairSimulator =
                        new FastPairSimulator(this, option, textViewLogger);
                mFastPairSimulator.setFirmwareVersion(firmwareVersion);
                mFailSwitch.setChecked(
                        mFastPairSimulator.getShouldFailPairing());
                mAdvOptionSpinner.setSelection(0);
                setCapabilityToSimulator();

                updateStringStatusView(R.id.bluetooth_address_text_view,
                        "Bluetooth address",
                        mFastPairSimulator.getBluetoothAddress());

                updateStringStatusView(R.id.device_name_text_view,
                        "Device name",
                        mFastPairSimulator.getDeviceName());

                resetButton.setText("Reset");
                resetButton.setEnabled(true);
                mModelIdSpinner.setEnabled(true);
                mAppLaunchSwitch.setEnabled(true);
                mFastPairSimulator.setDeviceNameCallback(deviceName ->
                        updateStringStatusView(
                                R.id.device_name_text_view,
                                "Device name", deviceName));

                if (desiredIoCapability == IO_CAPABILITY_IN
                        || device.getDeviceType().equals(DeviceType.ANDROID_AUTO)) {
                    mFastPairSimulator.setPasskeyEventCallback(mPasskeyEventCallback);
                }
                if (mInputStreamListener != null) {
                    mInputStreamListener.setFastPairSimulator(mFastPairSimulator);
                }
            });
        });
    }

    private int getIoCapabilityFromModelId(String modelId) {
        Device device = mModelsMap.get(modelId);
        if (device == null) {
            return IO_CAPABILITY_NONE;
        } else {
            if (getAntiSpoofingKey(modelId) == null) {
                return IO_CAPABILITY_NONE;
            } else {
                switch (device.getDeviceType()) {
                    case INPUT_DEVICE:
                        return IO_CAPABILITY_IN;

                    case DEVICE_TYPE_UNSPECIFIED:
                        return IO_CAPABILITY_NONE;

                    // Treats wearable to IO_CAPABILITY_KBDISP for simulator because there seems
                    // no suitable
                    // type.
                    case WEARABLE:
                        return IO_CAPABILITY_KBDISP;

                    default:
                        return IO_CAPABILITY_IO;
                }
            }
        }
    }

    @Nullable
    ByteString getAccontKey() {
        if (mFastPairSimulator == null) {
            return null;
        }
        return mFastPairSimulator.getAccountKey();
    }

    @Nullable
    private byte[] getAntiSpoofingKey(String modelId) {
        Device device = mModelsMap.get(modelId);
        if (device != null
                && device.hasAntiSpoofingKeyPair()
                && !device.getAntiSpoofingKeyPair().getPrivateKey().isEmpty()) {
            return base64().decode(device.getAntiSpoofingKeyPair().getPrivateKey().toStringUtf8());
        } else if (ANTI_SPOOFING_PRIVATE_KEY_MAP.containsKey(modelId)) {
            return base64().decode(ANTI_SPOOFING_PRIVATE_KEY_MAP.get(modelId));
        } else {
            return null;
        }
    }

    private final PasskeyEventCallback mPasskeyEventCallback = new PasskeyEventCallback() {
        @Override
        public void onPasskeyRequested(KeyInputCallback keyInputCallback) {
            showInputPasskeyDialog(keyInputCallback);
        }

        @Override
        public void onPasskeyConfirmation(int passkey, Consumer<Boolean> isConfirmed) {
            showConfirmPasskeyDialog(passkey, isConfirmed);
        }

        @Override
        public void onRemotePasskeyReceived(int passkey) {
            if (mInputPasskeyDialog == null) {
                return;
            }

            EditText userInputDialogEditText = mInputPasskeyDialog.findViewById(
                    R.id.userInputDialog);
            if (userInputDialogEditText == null) {
                return;
            }

            userInputDialogEditText.setText(String.format("%d", passkey));
        }
    };

    private void showInputPasskeyDialog(KeyInputCallback keyInputCallback) {
        if (mInputPasskeyDialog == null) {
            View userInputView =
                    LayoutInflater.from(getApplicationContext()).inflate(R.layout.user_input_dialog,
                            null);
            EditText userInputDialogEditText = userInputView.findViewById(R.id.userInputDialog);
            userInputDialogEditText.setHint(R.string.passkey_input_hint);
            userInputDialogEditText.setInputType(InputType.TYPE_CLASS_NUMBER);
            mInputPasskeyDialog = new AlertDialog.Builder(MainActivity.this)
                    .setView(userInputView)
                    .setCancelable(false)
                    .setPositiveButton(
                            android.R.string.ok,
                            (DialogInterface dialogBox, int id) -> {
                                String input = userInputDialogEditText.getText().toString();
                                keyInputCallback.onKeyInput(Integer.parseInt(input));
                            })
                    .setNegativeButton(android.R.string.cancel, /* listener= */ null)
                    .setTitle(R.string.passkey_dialog_title)
                    .create();
        }
        if (!mInputPasskeyDialog.isShowing()) {
            mInputPasskeyDialog.show();
        }
    }

    private void showConfirmPasskeyDialog(int passkey, Consumer<Boolean> isConfirmed) {
        runOnUiThread(() -> new AlertDialog.Builder(MainActivity.this)
                .setCancelable(false)
                .setTitle(R.string.confirm_passkey)
                .setMessage(String.valueOf(passkey))
                .setPositiveButton(android.R.string.ok,
                        (d, w) -> isConfirmed.accept(true))
                .setNegativeButton(android.R.string.cancel,
                        (d, w) -> isConfirmed.accept(false))
                .create()
                .show());
    }

    @UiThread
    private void updateStringStatusView(int id, String name, String value) {
        ((TextView) findViewById(id)).setText(name + ": " + value);
    }

    @UiThread
    private void updateStatusView() {
        TextView remoteDeviceTextView = (TextView) findViewById(R.id.remote_device_text_view);
        remoteDeviceTextView.setBackgroundColor(
                mBluetoothController.getRemoteDevice() != null ? LIGHT_GREEN : Color.LTGRAY);
        String remoteDeviceString = mBluetoothController.getRemoteDeviceAsString();
        remoteDeviceTextView.setText("Remote device: " + remoteDeviceString);

        updateBooleanStatusView(
                R.id.is_advertising_text_view,
                "BLE advertising",
                mFastPairSimulator != null && mFastPairSimulator.isAdvertising());

        updateStringStatusView(
                R.id.scan_mode_text_view,
                "Mode",
                FastPairSimulator.scanModeToString(mBluetoothController.getScanMode()));

        boolean isPaired = mBluetoothController.isPaired();
        updateBooleanStatusView(R.id.is_paired_text_view, "Paired", isPaired);

        updateBooleanStatusView(
                R.id.is_connected_text_view, "Connected", mBluetoothController.isConnected());
    }

    @UiThread
    private void updateBooleanStatusView(int id, String name, boolean value) {
        TextView view = (TextView) findViewById(id);
        view.setBackgroundColor(value ? LIGHT_GREEN : Color.LTGRAY);
        view.setText(name + ": " + (value ? "Yes" : "No"));
    }

    private String getFromIntentOrPrefs(String key, String defaultValue) {
        Bundle extras = getIntent().getExtras();
        extras = extras != null ? extras : new Bundle();
        SharedPreferences prefs = getSharedPreferences(SHARED_PREFS_NAME, MODE_PRIVATE);
        String value = extras.getString(key, prefs.getString(key, defaultValue));
        if (value == null) {
            prefs.edit().remove(key).apply();
        } else {
            prefs.edit().putString(key, value).apply();
        }
        return value;
    }

    private boolean getFromIntentOrPrefs(String key, boolean defaultValue) {
        Bundle extras = getIntent().getExtras();
        extras = extras != null ? extras : new Bundle();
        SharedPreferences prefs = getSharedPreferences(SHARED_PREFS_NAME, MODE_PRIVATE);
        boolean value = extras.getBoolean(key, prefs.getBoolean(key, defaultValue));
        prefs.edit().putBoolean(key, value).apply();
        return value;
    }

    private static int toTxPowerLevel(String txPowerLevelString) {
        switch (txPowerLevelString.toUpperCase()) {
            case "3":
            case "HIGH":
                return AdvertiseSettings.ADVERTISE_TX_POWER_HIGH;
            case "2":
            case "MEDIUM":
                return AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM;
            case "1":
            case "LOW":
                return AdvertiseSettings.ADVERTISE_TX_POWER_LOW;
            case "0":
            case "ULTRA_LOW":
                return AdvertiseSettings.ADVERTISE_TX_POWER_ULTRA_LOW;
            default:
                throw new IllegalArgumentException(
                        "Unexpected TxPower="
                                + txPowerLevelString
                                + ", please provide HIGH, MEDIUM, LOW, or ULTRA_LOW.");
        }
    }

    private boolean checkPermissions(String[] permissions) {
        for (String permission : permissions) {
            if (checkSelfPermission(permission) != PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    @Override
    protected void onDestroy() {
        mRemoteDevicesManager.destroy();

        if (mFastPairSimulator != null) {
            mFastPairSimulator.destroy();
            mBluetoothController.unregisterBluetoothStateReceiver();
        }

        // Recover the IO capability.
        mBluetoothController.setIoCapability(
                /*ioCapabilityClassic=*/ IO_CAPABILITY_IO, /*ioCapabilityBLE=*/
                IO_CAPABILITY_KBDISP);

        super.onDestroy();
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        // Relaunch this activity.
        recreate();
    }

    void startAdvertisingBatteryInformationBasedOnOption(int option) {
        if (mFastPairSimulator == null) {
            return;
        }

        // Option 0 is "No battery info", it means simulator will not pack battery information when
        // advertising. For the others with battery info, since we are simulating the Presto's
        // behavior,
        // there will always be three battery values.
        switch (option) {
            case 0:
                // Option "0: No battery info"
                mFastPairSimulator.clearBatteryValues();
                break;
            case 1:
                // Option "1: Show L(⬆) + R(⬆) + C(⬆)"
                mFastPairSimulator.setSuppressBatteryNotification(false);
                mFastPairSimulator.setBatteryValues(new BatteryValue(true, 60),
                        new BatteryValue(true, 61),
                        new BatteryValue(true, 62));
                break;
            case 2:
                // Option "2: Show L + R + C(unknown)"
                mFastPairSimulator.setSuppressBatteryNotification(false);
                mFastPairSimulator.setBatteryValues(new BatteryValue(false, 70),
                        new BatteryValue(false, 71),
                        new BatteryValue(false, -1));
                break;
            case 3:
                // Option "3: Show L(low 10) + R(low 9) + C(low 25)"
                mFastPairSimulator.setSuppressBatteryNotification(false);
                mFastPairSimulator.setBatteryValues(new BatteryValue(false, 10),
                        new BatteryValue(false, 9),
                        new BatteryValue(false, 25));
                break;
            case 4:
                // Option "4: Suppress battery w/o level changes"
                // Just change the suppress bit and keep the battery values the same as before.
                mFastPairSimulator.setSuppressBatteryNotification(true);
                break;
            case 5:
                // Option "5: Suppress L(low 10) + R(11) + C"
                mFastPairSimulator.setSuppressBatteryNotification(true);
                mFastPairSimulator.setBatteryValues(new BatteryValue(false, 10),
                        new BatteryValue(false, 11),
                        new BatteryValue(false, 82));
                break;
            case 6:
                // Option "6: Suppress L(low ⬆) + R(low ⬆) + C(low 10)"
                mFastPairSimulator.setSuppressBatteryNotification(true);
                mFastPairSimulator.setBatteryValues(new BatteryValue(true, 10),
                        new BatteryValue(true, 9),
                        new BatteryValue(false, 10));
                break;
            case 7:
                // Option "7: Suppress L(low ⬆) + R(low ⬆) + C(low ⬆)"
                mFastPairSimulator.setSuppressBatteryNotification(true);
                mFastPairSimulator.setBatteryValues(new BatteryValue(true, 10),
                        new BatteryValue(true, 9),
                        new BatteryValue(true, 25));
                break;
            case 8:
                // Option "8: Show subsequent pairing notification"
                mFastPairSimulator.setSuppressSubsequentPairingNotification(false);
                break;
            case 9:
                // Option "9: Suppress subsequent pairing notification"
                mFastPairSimulator.setSuppressSubsequentPairingNotification(true);
                break;
            default:
                // Unknown option, do nothing.
                return;
        }

        mFastPairSimulator.startAdvertising();
    }
}
