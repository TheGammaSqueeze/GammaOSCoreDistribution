/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.bluetooth.btservice;

import android.annotation.RequiresPermission;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothA2dp;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHapClient;
import android.bluetooth.BluetoothHeadset;
import android.bluetooth.BluetoothHearingAid;
import android.bluetooth.BluetoothLeAudio;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothSinkAudioPolicy;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import com.android.bluetooth.a2dp.A2dpService;
import com.android.bluetooth.btservice.storage.DatabaseManager;
import com.android.bluetooth.hearingaid.HearingAidService;
import com.android.bluetooth.hfp.HeadsetService;
import com.android.bluetooth.le_audio.LeAudioService;
import com.android.internal.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The active device manager is responsible for keeping track of the
 * connected A2DP/HFP/AVRCP/HearingAid/LE audio devices and select which device is
 * active (for each profile).
 * The active device manager selects a fallback device when the currently active device
 * is disconnected, and it selects BT devices that are lastly activated one.
 *
 * Current policy (subject to change):
 * 1) If the maximum number of connected devices is one, the manager doesn't
 *    do anything. Each profile is responsible for automatically selecting
 *    the connected device as active. Only if the maximum number of connected
 *    devices is more than one, the rules below will apply.
 * 2) The selected A2DP active device is the one used for AVRCP as well.
 * 3) The HFP active device might be different from the A2DP active device.
 * 4) The Active Device Manager always listens for ACTION_ACTIVE_DEVICE_CHANGED
 *    broadcasts for each profile:
 *    - BluetoothA2dp.ACTION_ACTIVE_DEVICE_CHANGED for A2DP
 *    - BluetoothHeadset.ACTION_ACTIVE_DEVICE_CHANGED for HFP
 *    - BluetoothHearingAid.ACTION_ACTIVE_DEVICE_CHANGED for HearingAid
 *    - BluetoothLeAudio.ACTION_LE_AUDIO_ACTIVE_DEVICE_CHANGED for LE audio
 *    If such broadcast is received (e.g., triggered indirectly by user
 *    action on the UI), the device in the received broadcast is marked
 *    as the current active device for that profile.
 * 5) If there is a HearingAid active device, then A2DP, HFP and LE audio active devices
 *    must be set to null (i.e., A2DP, HFP and LE audio cannot have active devices).
 *    The reason is that A2DP, HFP or LE audio cannot be used together with HearingAid.
 * 6) If there are no connected devices (e.g., during startup, or after all
 *    devices have been disconnected, the active device per profile
 *    (A2DP/HFP/HearingAid/LE audio) is selected as follows:
 * 6.1) The last connected HearingAid device is selected as active.
 *      If there is an active A2DP, HFP or LE audio device, those must be set to null.
 * 6.2) The last connected A2DP, HFP or LE audio device is selected as active.
 *      However, if there is an active HearingAid device, then the
 *      A2DP, HFP, or LE audio active device is not set (must remain null).
 * 7) If the currently active device (per profile) is disconnected, the
 *    Active Device Manager just marks that the profile has no active device,
 *    and the lastly activated BT device that is still connected would be selected.
 * 8) If there is already an active device, and the corresponding
 *    ACTION_ACTIVE_DEVICE_CHANGED broadcast is received, the device
 *    contained in the broadcast is marked as active. However, if
 *    the contained device is null, the corresponding profile is marked
 *    as having no active device.
 * 9) If a wired audio device is connected, the audio output is switched
 *    by the Audio Framework itself to that device. We detect this here,
 *    and the active device for each profile (A2DP/HFP/HearingAid/LE audio) is set
 *    to null to reflect the output device state change. However, if the
 *    wired audio device is disconnected, we don't do anything explicit
 *    and apply the default behavior instead:
 * 9.1) If the wired headset is still the selected output device (i.e. the
 *      active device is set to null), the Phone itself will become the output
 *      device (i.e., the active device will remain null). If music was
 *      playing, it will stop.
 * 9.2) If one of the Bluetooth devices is the selected active device
 *      (e.g., by the user in the UI), disconnecting the wired audio device
 *      will have no impact. E.g., music will continue streaming over the
 *      active Bluetooth device.
 */
class ActiveDeviceManager {
    private static final boolean DBG = true;
    private static final String TAG = "BluetoothActiveDeviceManager";

    // Message types for the handler
    private static final int MESSAGE_ADAPTER_ACTION_STATE_CHANGED = 1;
    private static final int MESSAGE_A2DP_ACTION_CONNECTION_STATE_CHANGED = 2;
    private static final int MESSAGE_A2DP_ACTION_ACTIVE_DEVICE_CHANGED = 3;
    private static final int MESSAGE_HFP_ACTION_CONNECTION_STATE_CHANGED = 4;
    private static final int MESSAGE_HFP_ACTION_ACTIVE_DEVICE_CHANGED = 5;
    private static final int MESSAGE_HEARING_AID_ACTION_CONNECTION_STATE_CHANGED = 6;
    private static final int MESSAGE_HEARING_AID_ACTION_ACTIVE_DEVICE_CHANGED = 7;
    private static final int MESSAGE_LE_AUDIO_ACTION_CONNECTION_STATE_CHANGED = 8;
    private static final int MESSAGE_LE_AUDIO_ACTION_ACTIVE_DEVICE_CHANGED = 9;
    private static final int MESSAGE_HAP_ACTION_CONNECTION_STATE_CHANGED = 10;
    private static final int MESSAGE_HAP_ACTION_ACTIVE_DEVICE_CHANGED = 11;

    private final AdapterService mAdapterService;
    private final ServiceFactory mFactory;
    private HandlerThread mHandlerThread = null;
    private Handler mHandler = null;
    private final AudioManager mAudioManager;
    private final AudioManagerAudioDeviceCallback mAudioManagerAudioDeviceCallback;

    private final List<BluetoothDevice> mA2dpConnectedDevices = new ArrayList<>();
    private final List<BluetoothDevice> mHfpConnectedDevices = new ArrayList<>();
    private final List<BluetoothDevice> mHearingAidConnectedDevices = new ArrayList<>();
    private final List<BluetoothDevice> mLeAudioConnectedDevices = new ArrayList<>();
    private final List<BluetoothDevice> mLeHearingAidConnectedDevices = new ArrayList<>();
    private List<BluetoothDevice> mPendingLeHearingAidActiveDevice = new ArrayList<>();
    private BluetoothDevice mA2dpActiveDevice = null;
    private BluetoothDevice mHfpActiveDevice = null;
    private BluetoothDevice mHearingAidActiveDevice = null;
    private BluetoothDevice mLeAudioActiveDevice = null;
    private BluetoothDevice mLeHearingAidActiveDevice = null;

    // Broadcast receiver for all changes
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action == null) {
                Log.e(TAG, "Received intent with null action");
                return;
            }
            switch (action) {
                case BluetoothAdapter.ACTION_STATE_CHANGED:
                    mHandler.obtainMessage(MESSAGE_ADAPTER_ACTION_STATE_CHANGED,
                            intent).sendToTarget();
                    break;
                case BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED:
                    mHandler.obtainMessage(MESSAGE_A2DP_ACTION_CONNECTION_STATE_CHANGED,
                            intent).sendToTarget();
                    break;
                case BluetoothA2dp.ACTION_ACTIVE_DEVICE_CHANGED:
                    mHandler.obtainMessage(MESSAGE_A2DP_ACTION_ACTIVE_DEVICE_CHANGED,
                            intent).sendToTarget();
                    break;
                case BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED:
                    mHandler.obtainMessage(MESSAGE_HFP_ACTION_CONNECTION_STATE_CHANGED,
                            intent).sendToTarget();
                    break;
                case BluetoothHeadset.ACTION_ACTIVE_DEVICE_CHANGED:
                    mHandler.obtainMessage(MESSAGE_HFP_ACTION_ACTIVE_DEVICE_CHANGED,
                            intent).sendToTarget();
                    break;
                case BluetoothHearingAid.ACTION_CONNECTION_STATE_CHANGED:
                    mHandler.obtainMessage(MESSAGE_HEARING_AID_ACTION_CONNECTION_STATE_CHANGED,
                            intent).sendToTarget();
                    break;
                case BluetoothHearingAid.ACTION_ACTIVE_DEVICE_CHANGED:
                    mHandler.obtainMessage(MESSAGE_HEARING_AID_ACTION_ACTIVE_DEVICE_CHANGED,
                            intent).sendToTarget();
                    break;
                case BluetoothLeAudio.ACTION_LE_AUDIO_CONNECTION_STATE_CHANGED:
                    mHandler.obtainMessage(MESSAGE_LE_AUDIO_ACTION_CONNECTION_STATE_CHANGED,
                            intent).sendToTarget();
                    break;
                case BluetoothLeAudio.ACTION_LE_AUDIO_ACTIVE_DEVICE_CHANGED:
                    mHandler.obtainMessage(MESSAGE_LE_AUDIO_ACTION_ACTIVE_DEVICE_CHANGED,
                            intent).sendToTarget();
                    break;
                case BluetoothHapClient.ACTION_HAP_CONNECTION_STATE_CHANGED:
                    mHandler.obtainMessage(MESSAGE_HAP_ACTION_CONNECTION_STATE_CHANGED,
                            intent).sendToTarget();
                    break;
                case BluetoothHapClient.ACTION_HAP_DEVICE_AVAILABLE:
                    mHandler.obtainMessage(MESSAGE_HAP_ACTION_ACTIVE_DEVICE_CHANGED,
                            intent).sendToTarget();
                    break;
                default:
                    Log.e(TAG, "Received unexpected intent, action=" + action);
                    break;
            }
        }
    };

    class ActiveDeviceManagerHandler extends Handler {
        ActiveDeviceManagerHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MESSAGE_ADAPTER_ACTION_STATE_CHANGED: {
                    Intent intent = (Intent) msg.obj;
                    int newState = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1);
                    if (DBG) {
                        Log.d(TAG, "handleMessage(MESSAGE_ADAPTER_ACTION_STATE_CHANGED): newState="
                                + newState);
                    }
                    if (newState == BluetoothAdapter.STATE_ON) {
                        resetState();
                    }
                }
                break;

                case MESSAGE_A2DP_ACTION_CONNECTION_STATE_CHANGED: {
                    Intent intent = (Intent) msg.obj;
                    BluetoothDevice device =
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    int prevState = intent.getIntExtra(BluetoothProfile.EXTRA_PREVIOUS_STATE, -1);
                    int nextState = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1);
                    if (prevState == nextState) {
                        // Nothing has changed
                        break;
                    }
                    if (nextState == BluetoothProfile.STATE_CONNECTED) {
                        // Device connected
                        if (DBG) {
                            Log.d(TAG,
                                    "handleMessage(MESSAGE_A2DP_ACTION_CONNECTION_STATE_CHANGED): "
                                    + "device " + device + " connected");
                        }
                        if (mA2dpConnectedDevices.contains(device)) {
                            break;      // The device is already connected
                        }
                        mA2dpConnectedDevices.add(device);
                        if (mHearingAidActiveDevice == null && mLeHearingAidActiveDevice == null) {
                            // New connected device: select it as active
                            setA2dpActiveDevice(device);
                            setLeAudioActiveDevice(null);
                        }
                        break;
                    }
                    if (prevState == BluetoothProfile.STATE_CONNECTED) {
                        // Device disconnected
                        if (DBG) {
                            Log.d(TAG,
                                    "handleMessage(MESSAGE_A2DP_ACTION_CONNECTION_STATE_CHANGED): "
                                    + "device " + device + " disconnected");
                        }
                        mA2dpConnectedDevices.remove(device);
                        if (Objects.equals(mA2dpActiveDevice, device)) {
                            if (mA2dpConnectedDevices.isEmpty()) {
                                setA2dpActiveDevice(null);
                            }
                            setFallbackDeviceActive();
                        }
                    }
                }
                break;

                case MESSAGE_A2DP_ACTION_ACTIVE_DEVICE_CHANGED: {
                    Intent intent = (Intent) msg.obj;
                    BluetoothDevice device =
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    if (DBG) {
                        Log.d(TAG, "handleMessage(MESSAGE_A2DP_ACTION_ACTIVE_DEVICE_CHANGED): "
                                + "device= " + device);
                    }
                    if (device != null && !Objects.equals(mA2dpActiveDevice, device)) {
                        setHearingAidActiveDevice(null);
                        setLeAudioActiveDevice(null);
                    }
                    if (mHfpConnectedDevices.contains(device)) {
                        setHfpActiveDevice(device);
                    }
                    // Just assign locally the new value
                    mA2dpActiveDevice = device;
                }
                break;

                case MESSAGE_HFP_ACTION_CONNECTION_STATE_CHANGED: {
                    Intent intent = (Intent) msg.obj;
                    BluetoothDevice device =
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    int prevState = intent.getIntExtra(BluetoothProfile.EXTRA_PREVIOUS_STATE, -1);
                    int nextState = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1);
                    if (prevState == nextState) {
                        // Nothing has changed
                        break;
                    }
                    if (nextState == BluetoothProfile.STATE_CONNECTED) {
                        // Device connected
                        if (DBG) {
                            Log.d(TAG,
                                    "handleMessage(MESSAGE_HFP_ACTION_CONNECTION_STATE_CHANGED): "
                                    + "device " + device + " connected");
                        }
                        if (mHfpConnectedDevices.contains(device)) {
                            break;      // The device is already connected
                        }
                        mHfpConnectedDevices.add(device);
                        if (mHearingAidActiveDevice == null && mLeHearingAidActiveDevice == null) {
                            // New connected device: select it as active
                            setHfpActiveDevice(device);
                            setLeAudioActiveDevice(null);
                        }
                        break;
                    }
                    if (prevState == BluetoothProfile.STATE_CONNECTED) {
                        // Device disconnected
                        if (DBG) {
                            Log.d(TAG,
                                    "handleMessage(MESSAGE_HFP_ACTION_CONNECTION_STATE_CHANGED): "
                                    + "device " + device + " disconnected");
                        }
                        mHfpConnectedDevices.remove(device);
                        if (Objects.equals(mHfpActiveDevice, device)) {
                            if (mHfpConnectedDevices.isEmpty()) {
                                setHfpActiveDevice(null);
                            }
                            setFallbackDeviceActive();
                        }
                    }
                }
                break;

                case MESSAGE_HFP_ACTION_ACTIVE_DEVICE_CHANGED: {
                    Intent intent = (Intent) msg.obj;
                    BluetoothDevice device =
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    if (DBG) {
                        Log.d(TAG, "handleMessage(MESSAGE_HFP_ACTION_ACTIVE_DEVICE_CHANGED): "
                                + "device= " + device);
                    }
                    if (device != null && !Objects.equals(mHfpActiveDevice, device)) {
                        setHearingAidActiveDevice(null);
                        setLeAudioActiveDevice(null);
                    }
                    if (mA2dpConnectedDevices.contains(device)) {
                        setA2dpActiveDevice(device);
                    }
                    // Just assign locally the new value
                    mHfpActiveDevice = device;
                }
                break;

                case MESSAGE_HEARING_AID_ACTION_CONNECTION_STATE_CHANGED: {
                    Intent intent = (Intent) msg.obj;
                    BluetoothDevice device =
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    int prevState = intent.getIntExtra(BluetoothProfile.EXTRA_PREVIOUS_STATE, -1);
                    int nextState = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1);
                    if (prevState == nextState) {
                        // Nothing has changed
                        break;
                    }
                    if (nextState == BluetoothProfile.STATE_CONNECTED) {
                        // Device connected
                        if (DBG) {
                            Log.d(TAG, "handleMessage(MESSAGE_HEARING_AID_ACTION_CONNECTION_STATE"
                                    + "_CHANGED): device " + device + " connected");
                        }
                        if (mHearingAidConnectedDevices.contains(device)) {
                            break;      // The device is already connected
                        }
                        mHearingAidConnectedDevices.add(device);
                        // New connected device: select it as active
                        setHearingAidActiveDevice(device);
                        setA2dpActiveDevice(null);
                        setHfpActiveDevice(null);
                        setLeAudioActiveDevice(null);
                        break;
                    }
                    if (prevState == BluetoothProfile.STATE_CONNECTED) {
                        // Device disconnected
                        if (DBG) {
                            Log.d(TAG, "handleMessage(MESSAGE_HEARING_AID_ACTION_CONNECTION_STATE"
                                    + "_CHANGED): device " + device + " disconnected");
                        }
                        mHearingAidConnectedDevices.remove(device);
                        if (Objects.equals(mHearingAidActiveDevice, device)) {
                            if (mHearingAidConnectedDevices.isEmpty()) {
                                setHearingAidActiveDevice(null);
                            }
                            setFallbackDeviceActive();
                        }
                    }
                }
                break;

                case MESSAGE_HEARING_AID_ACTION_ACTIVE_DEVICE_CHANGED: {
                    Intent intent = (Intent) msg.obj;
                    BluetoothDevice device =
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    if (DBG) {
                        Log.d(TAG, "handleMessage(MESSAGE_HA_ACTION_ACTIVE_DEVICE_CHANGED): "
                                + "device= " + device);
                    }
                    // Just assign locally the new value
                    mHearingAidActiveDevice = device;
                    if (device != null) {
                        setA2dpActiveDevice(null);
                        setHfpActiveDevice(null);
                        setLeAudioActiveDevice(null);
                    }
                }
                break;

                case MESSAGE_LE_AUDIO_ACTION_CONNECTION_STATE_CHANGED: {
                    Intent intent = (Intent) msg.obj;
                    BluetoothDevice device =
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    int prevState = intent.getIntExtra(BluetoothProfile.EXTRA_PREVIOUS_STATE, -1);
                    int nextState = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1);
                    if (prevState == nextState) {
                        // Nothing has changed
                        break;
                    }
                    if (nextState == BluetoothProfile.STATE_CONNECTED) {
                        // Device connected
                        if (DBG) {
                            Log.d(TAG, "handleMessage(MESSAGE_LE_AUDIO_ACTION_CONNECTION_STATE"
                                    + "_CHANGED): device " + device + " connected");
                        }
                        if (mLeAudioConnectedDevices.contains(device)) {
                            break;      // The device is already connected
                        }
                        mLeAudioConnectedDevices.add(device);
                        if (mHearingAidActiveDevice == null && mLeHearingAidActiveDevice == null
                                && mPendingLeHearingAidActiveDevice.isEmpty()) {
                            // New connected device: select it as active
                            setLeAudioActiveDevice(device);
                            setA2dpActiveDevice(null);
                            setHfpActiveDevice(null);
                        } else if (mPendingLeHearingAidActiveDevice.contains(device)) {
                            setLeHearingAidActiveDevice(device);
                            setHearingAidActiveDevice(null);
                            setA2dpActiveDevice(null);
                            setHfpActiveDevice(null);
                        }
                        break;
                    }
                    if (prevState == BluetoothProfile.STATE_CONNECTED) {
                        // Device disconnected
                        if (DBG) {
                            Log.d(TAG, "handleMessage(MESSAGE_LE_AUDIO_ACTION_CONNECTION_STATE"
                                    + "_CHANGED): device " + device + " disconnected");
                        }
                        mLeAudioConnectedDevices.remove(device);
                        mLeHearingAidConnectedDevices.remove(device);
                        if (Objects.equals(mLeAudioActiveDevice, device)) {
                            if (mLeAudioConnectedDevices.isEmpty()) {
                                setLeAudioActiveDevice(null);
                            }
                            setFallbackDeviceActive();
                        }
                    }
                }
                break;

                case MESSAGE_LE_AUDIO_ACTION_ACTIVE_DEVICE_CHANGED: {
                    Intent intent = (Intent) msg.obj;
                    BluetoothDevice device =
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    if (device != null && !mLeAudioConnectedDevices.contains(device)) {
                        mLeAudioConnectedDevices.add(device);
                    }
                    if (DBG) {
                        Log.d(TAG, "handleMessage(MESSAGE_LE_AUDIO_ACTION_ACTIVE_DEVICE_CHANGED): "
                                + "device= " + device);
                    }
                    // Just assign locally the new value
                    if (device != null && !Objects.equals(mLeAudioActiveDevice, device)) {
                        setA2dpActiveDevice(null);
                        setHfpActiveDevice(null);
                        setHearingAidActiveDevice(null);
                    }
                    mLeAudioActiveDevice = device;
                }
                break;

                case MESSAGE_HAP_ACTION_CONNECTION_STATE_CHANGED: {
                    Intent intent = (Intent) msg.obj;
                    BluetoothDevice device =
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    int prevState = intent.getIntExtra(BluetoothProfile.EXTRA_PREVIOUS_STATE, -1);
                    int nextState = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1);
                    if (prevState == nextState) {
                        // Nothing has changed
                        break;
                    }
                    if (nextState == BluetoothProfile.STATE_CONNECTED) {
                        // Device connected
                        if (DBG) {
                            Log.d(TAG, "handleMessage(MESSAGE_HAP_ACTION_CONNECTION_STATE"
                                    + "_CHANGED): device " + device + " connected");
                        }
                        if (mLeHearingAidConnectedDevices.contains(device)) {
                            break;      // The device is already connected
                        }
                        mLeHearingAidConnectedDevices.add(device);
                        if (!mLeAudioConnectedDevices.contains(device)) {
                            mPendingLeHearingAidActiveDevice.add(device);
                        } else if (Objects.equals(mLeAudioActiveDevice, device)) {
                            mLeHearingAidActiveDevice = device;
                        } else {
                            // New connected device: select it as active
                            setLeHearingAidActiveDevice(device);
                            setHearingAidActiveDevice(null);
                            setA2dpActiveDevice(null);
                            setHfpActiveDevice(null);
                        }
                        break;
                    }
                    if (prevState == BluetoothProfile.STATE_CONNECTED) {
                        // Device disconnected
                        if (DBG) {
                            Log.d(TAG, "handleMessage(MESSAGE_HAP_ACTION_CONNECTION_STATE"
                                    + "_CHANGED): device " + device + " disconnected");
                        }
                        mLeHearingAidConnectedDevices.remove(device);
                        mPendingLeHearingAidActiveDevice.remove(device);
                        if (Objects.equals(mLeHearingAidActiveDevice, device)) {
                            mLeHearingAidActiveDevice = null;
                        }
                    }
                }
                break;

                case MESSAGE_HAP_ACTION_ACTIVE_DEVICE_CHANGED: {
                    Intent intent = (Intent) msg.obj;
                    BluetoothDevice device =
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    if (device != null && !mLeHearingAidConnectedDevices.contains(device)) {
                        mLeHearingAidConnectedDevices.add(device);
                    }
                    if (DBG) {
                        Log.d(TAG, "handleMessage(MESSAGE_HAP_ACTION_ACTIVE_DEVICE_CHANGED): "
                                + "device= " + device);
                    }
                    // Just assign locally the new value
                    if (device != null && !Objects.equals(mLeHearingAidActiveDevice, device)) {
                        setA2dpActiveDevice(null);
                        setHfpActiveDevice(null);
                        setHearingAidActiveDevice(null);
                    }
                    mLeHearingAidActiveDevice = mLeAudioActiveDevice = device;
                }
                break;
            }
        }
    }

    /** Notifications of audio device connection and disconnection events. */
    @SuppressLint("AndroidFrameworkRequiresPermission")
    private class AudioManagerAudioDeviceCallback extends AudioDeviceCallback {
        private boolean isWiredAudioHeadset(AudioDeviceInfo deviceInfo) {
            switch (deviceInfo.getType()) {
                case AudioDeviceInfo.TYPE_WIRED_HEADSET:
                case AudioDeviceInfo.TYPE_WIRED_HEADPHONES:
                case AudioDeviceInfo.TYPE_USB_HEADSET:
                    return true;
                default:
                    break;
            }
            return false;
        }

        @Override
        public void onAudioDevicesAdded(AudioDeviceInfo[] addedDevices) {
            if (DBG) {
                Log.d(TAG, "onAudioDevicesAdded");
            }
            boolean hasAddedWiredDevice = false;
            for (AudioDeviceInfo deviceInfo : addedDevices) {
                if (DBG) {
                    Log.d(TAG, "Audio device added: " + deviceInfo.getProductName() + " type: "
                            + deviceInfo.getType());
                }
                if (isWiredAudioHeadset(deviceInfo)) {
                    hasAddedWiredDevice = true;
                    break;
                }
            }
            if (hasAddedWiredDevice) {
                wiredAudioDeviceConnected();
            }
        }

        @Override
        public void onAudioDevicesRemoved(AudioDeviceInfo[] removedDevices) {
        }
    }

    ActiveDeviceManager(AdapterService service, ServiceFactory factory) {
        mAdapterService = service;
        mFactory = factory;
        mAudioManager = service.getSystemService(AudioManager.class);
        mAudioManagerAudioDeviceCallback = new AudioManagerAudioDeviceCallback();
    }

    void start() {
        if (DBG) {
            Log.d(TAG, "start()");
        }

        mHandlerThread = new HandlerThread("BluetoothActiveDeviceManager");
        mHandlerThread.start();
        mHandler = new ActiveDeviceManagerHandler(mHandlerThread.getLooper());

        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        filter.addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED);
        filter.addAction(BluetoothA2dp.ACTION_ACTIVE_DEVICE_CHANGED);
        filter.addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED);
        filter.addAction(BluetoothHeadset.ACTION_ACTIVE_DEVICE_CHANGED);
        filter.addAction(BluetoothHearingAid.ACTION_CONNECTION_STATE_CHANGED);
        filter.addAction(BluetoothHearingAid.ACTION_ACTIVE_DEVICE_CHANGED);
        filter.addAction(BluetoothLeAudio.ACTION_LE_AUDIO_CONNECTION_STATE_CHANGED);
        filter.addAction(BluetoothLeAudio.ACTION_LE_AUDIO_ACTIVE_DEVICE_CHANGED);
        filter.addAction(BluetoothHapClient.ACTION_HAP_CONNECTION_STATE_CHANGED);
        filter.addAction(BluetoothHapClient.ACTION_HAP_DEVICE_AVAILABLE);
        mAdapterService.registerReceiver(mReceiver, filter);

        mAudioManager.registerAudioDeviceCallback(mAudioManagerAudioDeviceCallback, mHandler);
    }

    void cleanup() {
        if (DBG) {
            Log.d(TAG, "cleanup()");
        }

        mAudioManager.unregisterAudioDeviceCallback(mAudioManagerAudioDeviceCallback);
        mAdapterService.unregisterReceiver(mReceiver);
        if (mHandlerThread != null) {
            mHandlerThread.quit();
            mHandlerThread = null;
        }
        resetState();
    }

    /**
     * Get the {@link Looper} for the handler thread. This is used in testing and helper
     * objects
     *
     * @return {@link Looper} for the handler thread
     */
    @VisibleForTesting
    public Looper getHandlerLooper() {
        if (mHandlerThread == null) {
            return null;
        }
        return mHandlerThread.getLooper();
    }

    private void setA2dpActiveDevice(BluetoothDevice device) {
        if (DBG) {
            Log.d(TAG, "setA2dpActiveDevice(" + device + ")");
        }
        final A2dpService a2dpService = mFactory.getA2dpService();
        if (a2dpService == null) {
            return;
        }
        if (!a2dpService.setActiveDevice(device)) {
            return;
        }
        mA2dpActiveDevice = device;
    }

    @RequiresPermission(android.Manifest.permission.MODIFY_PHONE_STATE)
    private void setHfpActiveDevice(BluetoothDevice device) {
        if (DBG) {
            Log.d(TAG, "setHfpActiveDevice(" + device + ")");
        }
        final HeadsetService headsetService = mFactory.getHeadsetService();
        if (headsetService == null) {
            return;
        }
        BluetoothSinkAudioPolicy audioPolicy = headsetService.getHfpCallAudioPolicy(device);
        if (audioPolicy == null || audioPolicy.getActiveDevicePolicyAfterConnection()
                != BluetoothSinkAudioPolicy.POLICY_NOT_ALLOWED) {
            if (!headsetService.setActiveDevice(device)) {
                return;
            }
            mHfpActiveDevice = device;
        }
    }

    private void setHearingAidActiveDevice(BluetoothDevice device) {
        if (DBG) {
            Log.d(TAG, "setHearingAidActiveDevice(" + device + ")");
        }
        final HearingAidService hearingAidService = mFactory.getHearingAidService();
        if (hearingAidService == null) {
            return;
        }
        if (!hearingAidService.setActiveDevice(device)) {
            return;
        }
        mHearingAidActiveDevice = device;
    }

    private void setLeAudioActiveDevice(BluetoothDevice device) {
        if (DBG) {
            Log.d(TAG, "setLeAudioActiveDevice(" + device + ")");
        }
        final LeAudioService leAudioService = mFactory.getLeAudioService();
        if (leAudioService == null) {
            return;
        }
        if (!leAudioService.setActiveDevice(device)) {
            return;
        }
        mLeAudioActiveDevice = device;
        if (device == null) {
            mLeHearingAidActiveDevice = null;
            mPendingLeHearingAidActiveDevice.remove(device);
        }
    }

    private void setLeHearingAidActiveDevice(BluetoothDevice device) {
        if (!Objects.equals(mLeAudioActiveDevice, device)) {
            setLeAudioActiveDevice(device);
        }
        if (Objects.equals(mLeAudioActiveDevice, device)) {
            // setLeAudioActiveDevice succeed
            mLeHearingAidActiveDevice = device;
            mPendingLeHearingAidActiveDevice.remove(device);
        }
    }

    private void setFallbackDeviceActive() {
        if (DBG) {
            Log.d(TAG, "setFallbackDeviceActive");
        }
        DatabaseManager dbManager = mAdapterService.getDatabase();
        if (dbManager == null) {
            return;
        }
        List<BluetoothDevice> connectedHearingAidDevices = new ArrayList<>();
        if (!mHearingAidConnectedDevices.isEmpty()) {
            connectedHearingAidDevices.addAll(mHearingAidConnectedDevices);
        }
        if (!mLeHearingAidConnectedDevices.isEmpty()) {
            connectedHearingAidDevices.addAll(mLeHearingAidConnectedDevices);
        }
        if (!connectedHearingAidDevices.isEmpty()) {
            BluetoothDevice device =
                    dbManager.getMostRecentlyConnectedDevicesInList(connectedHearingAidDevices);
            if (device != null) {
                if (mHearingAidConnectedDevices.contains(device)) {
                    if (DBG) {
                        Log.d(TAG, "set hearing aid device active: " + device);
                    }
                    setHearingAidActiveDevice(device);
                    setA2dpActiveDevice(null);
                    setHfpActiveDevice(null);
                    setLeAudioActiveDevice(null);
                } else {
                    if (DBG) {
                        Log.d(TAG, "set LE hearing aid device active: " + device);
                    }
                    setLeHearingAidActiveDevice(device);
                    setHearingAidActiveDevice(null);
                    setA2dpActiveDevice(null);
                    setHfpActiveDevice(null);
                }
                return;
            }
        }

        A2dpService a2dpService = mFactory.getA2dpService();
        BluetoothDevice a2dpFallbackDevice = null;
        if (a2dpService != null) {
            a2dpFallbackDevice = a2dpService.getFallbackDevice();
        }

        HeadsetService headsetService = mFactory.getHeadsetService();
        BluetoothDevice headsetFallbackDevice = null;
        if (headsetService != null) {
            headsetFallbackDevice = headsetService.getFallbackDevice();
        }

        List<BluetoothDevice> connectedDevices = new ArrayList<>();
        connectedDevices.addAll(mLeAudioConnectedDevices);
        switch (mAudioManager.getMode()) {
            case AudioManager.MODE_NORMAL:
                if (a2dpFallbackDevice != null) {
                    connectedDevices.add(a2dpFallbackDevice);
                }
                break;
            case AudioManager.MODE_RINGTONE:
                if (headsetFallbackDevice != null && headsetService.isInbandRingingEnabled()) {
                    connectedDevices.add(headsetFallbackDevice);
                }
                break;
            default:
                if (headsetFallbackDevice != null) {
                    connectedDevices.add(headsetFallbackDevice);
                }
        }
        BluetoothDevice device = dbManager.getMostRecentlyConnectedDevicesInList(connectedDevices);
        if (device != null) {
            if (mAudioManager.getMode() == AudioManager.MODE_NORMAL) {
                if (Objects.equals(a2dpFallbackDevice, device)) {
                    if (DBG) {
                        Log.d(TAG, "set A2DP device active: " + device);
                    }
                    setA2dpActiveDevice(device);
                    if (headsetFallbackDevice != null) {
                        setHfpActiveDevice(device);
                        setLeAudioActiveDevice(null);
                    }
                } else {
                    if (DBG) {
                        Log.d(TAG, "set LE audio device active: " + device);
                    }
                    setLeAudioActiveDevice(device);
                    setA2dpActiveDevice(null);
                    setHfpActiveDevice(null);
                }
            } else {
                if (Objects.equals(headsetFallbackDevice, device)) {
                    if (DBG) {
                        Log.d(TAG, "set HFP device active: " + device);
                    }
                    setHfpActiveDevice(device);
                    if (a2dpFallbackDevice != null) {
                        setA2dpActiveDevice(a2dpFallbackDevice);
                        setLeAudioActiveDevice(null);
                    }
                } else {
                    if (DBG) {
                        Log.d(TAG, "set LE audio device active: " + device);
                    }
                    setLeAudioActiveDevice(device);
                    setA2dpActiveDevice(null);
                    setHfpActiveDevice(null);
                }
            }
        }
    }

    private void resetState() {
        mA2dpConnectedDevices.clear();
        mA2dpActiveDevice = null;

        mHfpConnectedDevices.clear();
        mHfpActiveDevice = null;

        mHearingAidConnectedDevices.clear();
        mHearingAidActiveDevice = null;

        mLeAudioConnectedDevices.clear();
        mLeAudioActiveDevice = null;

        mLeHearingAidConnectedDevices.clear();
        mLeHearingAidActiveDevice = null;
        mPendingLeHearingAidActiveDevice.clear();
    }

    @VisibleForTesting
    BroadcastReceiver getBroadcastReceiver() {
        return mReceiver;
    }

    @VisibleForTesting
    BluetoothDevice getA2dpActiveDevice() {
        return mA2dpActiveDevice;
    }

    @VisibleForTesting
    BluetoothDevice getHfpActiveDevice() {
        return mHfpActiveDevice;
    }

    @VisibleForTesting
    BluetoothDevice getHearingAidActiveDevice() {
        return mHearingAidActiveDevice;
    }

    @VisibleForTesting
    BluetoothDevice getLeAudioActiveDevice() {
        return mLeAudioActiveDevice;
    }

    /**
     * Called when a wired audio device is connected.
     * It might be called multiple times each time a wired audio device is connected.
     */
    @VisibleForTesting
    @RequiresPermission(android.Manifest.permission.MODIFY_PHONE_STATE)
    void wiredAudioDeviceConnected() {
        if (DBG) {
            Log.d(TAG, "wiredAudioDeviceConnected");
        }
        setA2dpActiveDevice(null);
        setHfpActiveDevice(null);
        setHearingAidActiveDevice(null);
        setLeAudioActiveDevice(null);
    }
}
