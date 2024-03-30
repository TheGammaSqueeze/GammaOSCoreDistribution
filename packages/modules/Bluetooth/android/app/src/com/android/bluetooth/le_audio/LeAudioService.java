/*
 * Copyright 2020 HIMSA II K/S - www.himsa.com.
 * Represented by EHIMA - www.ehima.com
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

package com.android.bluetooth.le_audio;

import static android.Manifest.permission.BLUETOOTH_CONNECT;
import static android.bluetooth.IBluetoothLeAudio.LE_AUDIO_GROUP_ID_INVALID;

import static com.android.bluetooth.Utils.enforceBluetoothPrivilegedPermission;

import android.annotation.RequiresPermission;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothLeAudio;
import android.bluetooth.BluetoothLeAudioCodecConfig;
import android.bluetooth.BluetoothLeAudioCodecStatus;
import android.bluetooth.BluetoothLeAudioContentMetadata;
import android.bluetooth.BluetoothLeBroadcastMetadata;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothStatusCodes;
import android.bluetooth.BluetoothUuid;
import android.bluetooth.IBluetoothLeAudio;
import android.bluetooth.IBluetoothLeAudioCallback;
import android.bluetooth.IBluetoothLeBroadcastCallback;
import android.bluetooth.IBluetoothVolumeControl;
import android.content.AttributionSource;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.BluetoothProfileConnectionInfo;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Parcel;
import android.os.ParcelUuid;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.sysprop.BluetoothProperties;
import android.util.Log;
import android.util.Pair;

import com.android.bluetooth.Utils;
import com.android.bluetooth.btservice.AdapterService;
import com.android.bluetooth.btservice.ProfileService;
import com.android.bluetooth.btservice.ServiceFactory;
import com.android.bluetooth.btservice.storage.DatabaseManager;
import com.android.bluetooth.hfp.HeadsetService;
import com.android.bluetooth.mcp.McpService;
import com.android.bluetooth.tbs.TbsGatt;
import com.android.bluetooth.vc.VolumeControlService;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.modules.utils.SynchronousResultReceiver;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Provides Bluetooth LeAudio profile, as a service in the Bluetooth application.
 * @hide
 */
public class LeAudioService extends ProfileService {
    private static final boolean DBG = true;
    private static final String TAG = "LeAudioService";

    // Timeout for state machine thread join, to prevent potential ANR.
    private static final int SM_THREAD_JOIN_TIMEOUT_MS = 1000;

    // Upper limit of all LeAudio devices: Bonded or Connected
    private static final int MAX_LE_AUDIO_DEVICES = 10;
    private static LeAudioService sLeAudioService;

    /**
     * Indicates group audio support for none direction
     */
    private static final int AUDIO_DIRECTION_NONE = 0x00;

    /**
     * Indicates group audio support for output direction
     */
    private static final int AUDIO_DIRECTION_OUTPUT_BIT = 0x01;

    /**
     * Indicates group audio support for input direction
     */
    private static final int AUDIO_DIRECTION_INPUT_BIT = 0x02;

    private AdapterService mAdapterService;
    private DatabaseManager mDatabaseManager;
    private HandlerThread mStateMachinesThread;
    private volatile BluetoothDevice mActiveAudioOutDevice;
    private volatile BluetoothDevice mActiveAudioInDevice;
    private LeAudioCodecConfig mLeAudioCodecConfig;
    private final Object mGroupLock = new Object();
    ServiceFactory mServiceFactory = new ServiceFactory();

    LeAudioNativeInterface mLeAudioNativeInterface;
    boolean mLeAudioNativeIsInitialized = false;
    boolean mBluetoothEnabled = false;
    BluetoothDevice mHfpHandoverDevice = null;
    LeAudioBroadcasterNativeInterface mLeAudioBroadcasterNativeInterface = null;
    @VisibleForTesting
    AudioManager mAudioManager;
    LeAudioTmapGattServer mTmapGattServer;

    @VisibleForTesting
    McpService mMcpService;

    @VisibleForTesting
    VolumeControlService mVolumeControlService;

    @VisibleForTesting
    RemoteCallbackList<IBluetoothLeBroadcastCallback> mBroadcastCallbacks;

    @VisibleForTesting
    RemoteCallbackList<IBluetoothLeAudioCallback> mLeAudioCallbacks;

    private class LeAudioGroupDescriptor {
        LeAudioGroupDescriptor() {
            mIsConnected = false;
            mIsActive = false;
            mDirection = AUDIO_DIRECTION_NONE;
            mCodecStatus = null;
            mLostLeadDeviceWhileStreaming = null;
        }

        public Boolean mIsConnected;
        public Boolean mIsActive;
        public Integer mDirection;
        public BluetoothLeAudioCodecStatus mCodecStatus;
        /* This can be non empty only for the streaming time */
        BluetoothDevice mLostLeadDeviceWhileStreaming;
    }

    private static class LeAudioDeviceDescriptor {
        LeAudioDeviceDescriptor() {
            mStateMachine = null;
            mGroupId = LE_AUDIO_GROUP_ID_INVALID;
            mSinkAudioLocation = BluetoothLeAudio.AUDIO_LOCATION_INVALID;
            mDirection = AUDIO_DIRECTION_NONE;
        }

        public LeAudioStateMachine mStateMachine;
        public Integer mGroupId;
        public Integer mSinkAudioLocation;
        public Integer mDirection;
    }

    List<BluetoothLeAudioCodecConfig> mInputLocalCodecCapabilities = new ArrayList<>();
    List<BluetoothLeAudioCodecConfig> mOutputLocalCodecCapabilities = new ArrayList<>();

    @GuardedBy("mGroupLock")
    private final Map<Integer, LeAudioGroupDescriptor> mGroupDescriptors = new LinkedHashMap<>();
    private final Map<BluetoothDevice, LeAudioDeviceDescriptor> mDeviceDescriptors =
            new LinkedHashMap<>();

    private BroadcastReceiver mBondStateChangedReceiver;
    private BroadcastReceiver mConnectionStateChangedReceiver;
    private BroadcastReceiver mMuteStateChangedReceiver;
    private int mStoredRingerMode = -1;
    private Handler mHandler = new Handler(Looper.getMainLooper());
    private final AudioManagerAddAudioDeviceCallback mAudioManagerAddAudioDeviceCallback =
            new AudioManagerAddAudioDeviceCallback();
    private final AudioManagerRemoveAudioDeviceCallback mAudioManagerRemoveAudioDeviceCallback =
            new AudioManagerRemoveAudioDeviceCallback();

    private final Map<Integer, Integer> mBroadcastStateMap = new HashMap<>();
    private final Map<Integer, Boolean> mBroadcastsPlaybackMap = new HashMap<>();
    private final Map<Integer, BluetoothLeBroadcastMetadata> mBroadcastMetadataList =
            new HashMap<>();

    @Override
    protected IProfileServiceBinder initBinder() {
        return new BluetoothLeAudioBinder(this);
    }

    public static boolean isEnabled() {
        return BluetoothProperties.isProfileBapUnicastClientEnabled().orElse(false);
    }

    public static boolean isBroadcastEnabled() {
        return BluetoothProperties.isProfileBapBroadcastSourceEnabled().orElse(false);
    }

    @Override
    protected void create() {
        Log.i(TAG, "create()");
    }

    @Override
    protected boolean start() {
        Log.i(TAG, "start()");
        if (sLeAudioService != null) {
            throw new IllegalStateException("start() called twice");
        }

        mAdapterService = Objects.requireNonNull(AdapterService.getAdapterService(),
                "AdapterService cannot be null when LeAudioService starts");
        mLeAudioNativeInterface = Objects.requireNonNull(LeAudioNativeInterface.getInstance(),
                "LeAudioNativeInterface cannot be null when LeAudioService starts");
        mDatabaseManager = Objects.requireNonNull(mAdapterService.getDatabase(),
                "DatabaseManager cannot be null when LeAudioService starts");

        mAudioManager = getSystemService(AudioManager.class);
        Objects.requireNonNull(mAudioManager,
                "AudioManager cannot be null when LeAudioService starts");

        // Start handler thread for state machines
        mStateMachinesThread = new HandlerThread("LeAudioService.StateMachines");
        mStateMachinesThread.start();

        mBroadcastStateMap.clear();
        mBroadcastMetadataList.clear();
        mBroadcastsPlaybackMap.clear();

        synchronized (mGroupLock) {
            mDeviceDescriptors.clear();
            mGroupDescriptors.clear();
        }

        // Setup broadcast receivers
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        mBondStateChangedReceiver = new BondStateChangedReceiver();
        registerReceiver(mBondStateChangedReceiver, filter);
        filter = new IntentFilter();
        filter.addAction(BluetoothLeAudio.ACTION_LE_AUDIO_CONNECTION_STATE_CHANGED);
        mConnectionStateChangedReceiver = new ConnectionStateChangedReceiver();
        registerReceiver(mConnectionStateChangedReceiver, filter);
        filter = new IntentFilter();
        filter.addAction(AudioManager.RINGER_MODE_CHANGED_ACTION);
        mMuteStateChangedReceiver = new MuteStateChangedReceiver();
        registerReceiver(mMuteStateChangedReceiver, filter);

        mLeAudioCallbacks = new RemoteCallbackList<IBluetoothLeAudioCallback>();

        int tmapRoleMask =
                LeAudioTmapGattServer.TMAP_ROLE_FLAG_CG | LeAudioTmapGattServer.TMAP_ROLE_FLAG_UMS;

        // Initialize Broadcast native interface
        if ((mAdapterService.getSupportedProfilesBitMask()
                    & (1 << BluetoothProfile.LE_AUDIO_BROADCAST)) != 0) {
            Log.i(TAG, "Init Le Audio broadcaster");
            mBroadcastCallbacks = new RemoteCallbackList<IBluetoothLeBroadcastCallback>();
            mLeAudioBroadcasterNativeInterface = Objects.requireNonNull(
                    LeAudioBroadcasterNativeInterface.getInstance(),
                    "LeAudioBroadcasterNativeInterface cannot be null when LeAudioService starts");
            mLeAudioBroadcasterNativeInterface.init();
            tmapRoleMask |= LeAudioTmapGattServer.TMAP_ROLE_FLAG_BMS;
        } else {
            Log.w(TAG, "Le Audio Broadcasts not supported.");
        }

        // the role mask is fixed in Android
        if (mTmapGattServer != null) {
            throw new IllegalStateException("TMAP GATT server started before start() is called");
        }
        mTmapGattServer = LeAudioObjectsFactory.getInstance().getTmapGattServer(this);
        mTmapGattServer.start(tmapRoleMask);

        // Mark service as started
        setLeAudioService(this);

        // Setup codec config
        mLeAudioCodecConfig = new LeAudioCodecConfig(this);

        // Delay the call to init by posting it. This ensures TBS and MCS are fully initialized
        // before we start accepting connections
        mHandler.post(this::init);

        return true;
    }

    private void init() {
        LeAudioNativeInterface nativeInterface = mLeAudioNativeInterface;
        if (nativeInterface == null) {
            Log.w(TAG, "the service is stopped. ignore init()");
            return;
        }
        nativeInterface.init(mLeAudioCodecConfig.getCodecConfigOffloading());
    }

    @Override
    protected boolean stop() {
        Log.i(TAG, "stop()");
        if (sLeAudioService == null) {
            Log.w(TAG, "stop() called before start()");
            return true;
        }

        mHandler.removeCallbacks(this::init);
        setActiveDevice(null);

        if (mTmapGattServer == null) {
            Log.w(TAG, "TMAP GATT server should never be null before stop() is called");
        } else {
            mTmapGattServer.stop();
            mTmapGattServer = null;
        }

        //Don't wait for async call with INACTIVE group status, clean active
        //device for active group.
        synchronized (mGroupLock) {
            for (Map.Entry<Integer, LeAudioGroupDescriptor> entry : mGroupDescriptors.entrySet()) {
                LeAudioGroupDescriptor descriptor = entry.getValue();
                Integer group_id = entry.getKey();
                if (descriptor.mIsActive) {
                    descriptor.mIsActive = false;
                    updateActiveDevices(group_id, descriptor.mDirection, AUDIO_DIRECTION_NONE,
                            descriptor.mIsActive);
                    break;
                }
            }

            // Destroy state machines and stop handler thread
            for (LeAudioDeviceDescriptor descriptor : mDeviceDescriptors.values()) {
                LeAudioStateMachine sm = descriptor.mStateMachine;
                if (sm == null) {
                    continue;
                }
                sm.doQuit();
                sm.cleanup();
            }

            mDeviceDescriptors.clear();
            mGroupDescriptors.clear();
        }

        // Cleanup native interfaces
        mLeAudioNativeInterface.cleanup();
        mLeAudioNativeInterface = null;
        mLeAudioNativeIsInitialized = false;
        mBluetoothEnabled = false;
        mHfpHandoverDevice = null;

        mActiveAudioOutDevice = null;
        mActiveAudioInDevice = null;
        mLeAudioCodecConfig = null;

        // Set the service and BLE devices as inactive
        setLeAudioService(null);

        // Unregister broadcast receivers
        unregisterReceiver(mBondStateChangedReceiver);
        mBondStateChangedReceiver = null;
        unregisterReceiver(mConnectionStateChangedReceiver);
        mConnectionStateChangedReceiver = null;
        unregisterReceiver(mMuteStateChangedReceiver);
        mMuteStateChangedReceiver = null;


        if (mBroadcastCallbacks != null) {
            mBroadcastCallbacks.kill();
        }

        if (mLeAudioCallbacks != null) {
            mLeAudioCallbacks.kill();
        }

        mBroadcastStateMap.clear();
        mBroadcastsPlaybackMap.clear();
        mBroadcastMetadataList.clear();

        if (mLeAudioBroadcasterNativeInterface != null) {
            mLeAudioBroadcasterNativeInterface.cleanup();
            mLeAudioBroadcasterNativeInterface = null;
        }

        if (mStateMachinesThread != null) {
            try {
                mStateMachinesThread.quitSafely();
                mStateMachinesThread.join(SM_THREAD_JOIN_TIMEOUT_MS);
                mStateMachinesThread = null;
            } catch (InterruptedException e) {
                // Do not rethrow as we are shutting down anyway
            }
        }

        mAudioManager.unregisterAudioDeviceCallback(mAudioManagerAddAudioDeviceCallback);
        mAudioManager.unregisterAudioDeviceCallback(mAudioManagerRemoveAudioDeviceCallback);

        mAdapterService = null;
        mAudioManager = null;
        mMcpService = null;
        mVolumeControlService = null;

        return true;
    }

    @Override
    protected void cleanup() {
        Log.i(TAG, "cleanup()");
    }

    public static synchronized LeAudioService getLeAudioService() {
        if (sLeAudioService == null) {
            Log.w(TAG, "getLeAudioService(): service is NULL");
            return null;
        }
        if (!sLeAudioService.isAvailable()) {
            Log.w(TAG, "getLeAudioService(): service is not available");
            return null;
        }
        return sLeAudioService;
    }

    @VisibleForTesting
    static synchronized void setLeAudioService(LeAudioService instance) {
        if (DBG) {
            Log.d(TAG, "setLeAudioService(): set to: " + instance);
        }
        sLeAudioService = instance;
    }

    @VisibleForTesting
    int getAudioDeviceGroupVolume(int groupId) {
        if (mVolumeControlService == null) {
            mVolumeControlService = mServiceFactory.getVolumeControlService();
            if (mVolumeControlService == null) {
                Log.e(TAG, "Volume control service is not available");
                return IBluetoothVolumeControl.VOLUME_CONTROL_UNKNOWN_VOLUME;
            }
        }

        return mVolumeControlService.getAudioDeviceGroupVolume(groupId);
    }

    LeAudioDeviceDescriptor createDeviceDescriptor(BluetoothDevice device) {
        LeAudioDeviceDescriptor descriptor = mDeviceDescriptors.get(device);
        if (descriptor == null) {

            // Limit the maximum number of devices to avoid DoS attack
            if (mDeviceDescriptors.size() >= MAX_LE_AUDIO_DEVICES) {
                Log.e(TAG, "Maximum number of LeAudio state machines reached: "
                        + MAX_LE_AUDIO_DEVICES);
                return null;
            }

            mDeviceDescriptors.put(device, new LeAudioDeviceDescriptor());
            descriptor = mDeviceDescriptors.get(device);
            Log.d(TAG, "Created descriptor for device: " + device);
        } else {
            Log.w(TAG, "Device: " + device + ", already exists");
        }

        return descriptor;
    }

    public boolean connect(BluetoothDevice device) {
        if (DBG) {
            Log.d(TAG, "connect(): " + device);
        }

        if (getConnectionPolicy(device) == BluetoothProfile.CONNECTION_POLICY_FORBIDDEN) {
            Log.e(TAG, "Cannot connect to " + device + " : CONNECTION_POLICY_FORBIDDEN");
            return false;
        }
        ParcelUuid[] featureUuids = mAdapterService.getRemoteUuids(device);
        if (!Utils.arrayContains(featureUuids, BluetoothUuid.LE_AUDIO)) {
            Log.e(TAG, "Cannot connect to " + device + " : Remote does not have LE_AUDIO UUID");
            return false;
        }

        synchronized (mGroupLock) {
            if (createDeviceDescriptor(device) == null) {
                return false;
            }

            LeAudioStateMachine sm = getOrCreateStateMachine(device);
            if (sm == null) {
                Log.e(TAG, "Ignored connect request for " + device + " : no state machine");
                return false;
            }
            sm.sendMessage(LeAudioStateMachine.CONNECT);
        }

        return true;
    }

    /**
     * Disconnects LE Audio for the remote bluetooth device
     *
     * @param device is the device with which we would like to disconnect LE Audio
     * @return true if profile disconnected, false if device not connected over LE Audio
     */
    public boolean disconnect(BluetoothDevice device) {
        if (DBG) {
            Log.d(TAG, "disconnect(): " + device);
        }

        synchronized (mGroupLock) {
            LeAudioDeviceDescriptor descriptor = getDeviceDescriptor(device);
            if (descriptor == null) {
                Log.e(TAG, "disconnect: No valid descriptor for device: " + device);
                return false;
            }

            LeAudioStateMachine sm = descriptor.mStateMachine;
            if (sm == null) {
                Log.e(TAG, "Ignored disconnect request for " + device
                        + " : no state machine");
                return false;
            }
            sm.sendMessage(LeAudioStateMachine.DISCONNECT);
        }

        return true;
    }

    public List<BluetoothDevice> getConnectedDevices() {
        synchronized (mGroupLock) {
            List<BluetoothDevice> devices = new ArrayList<>();
            for (LeAudioDeviceDescriptor descriptor : mDeviceDescriptors.values()) {
                LeAudioStateMachine sm = descriptor.mStateMachine;
                if (sm != null && sm.isConnected()) {
                    devices.add(sm.getDevice());
                }
            }
            return devices;
        }
    }

    BluetoothDevice getConnectedGroupLeadDevice(int groupId) {
        BluetoothDevice device = null;

        if (mActiveAudioOutDevice != null
                && getGroupId(mActiveAudioOutDevice) == groupId) {
            device = mActiveAudioOutDevice;
        } else {
            device = getFirstDeviceFromGroup(groupId);
        }

        if (device == null) {
            return device;
        }

        LeAudioDeviceDescriptor descriptor = getDeviceDescriptor(device);
        if (descriptor == null) {
            Log.e(TAG, "getConnectedGroupLeadDevice: No valid descriptor for device: " + device);
            return null;
        }

        LeAudioStateMachine sm = descriptor.mStateMachine;
        if (sm != null && sm.getConnectionState() == BluetoothProfile.STATE_CONNECTED) {
            return device;
        }

        return null;
    }

    List<BluetoothDevice> getDevicesMatchingConnectionStates(int[] states) {
        ArrayList<BluetoothDevice> devices = new ArrayList<>();
        if (states == null) {
            return devices;
        }
        final BluetoothDevice[] bondedDevices = mAdapterService.getBondedDevices();
        if (bondedDevices == null) {
            return devices;
        }
        synchronized (mGroupLock) {
            for (BluetoothDevice device : bondedDevices) {
                final ParcelUuid[] featureUuids = device.getUuids();
                if (!Utils.arrayContains(featureUuids, BluetoothUuid.LE_AUDIO)) {
                    continue;
                }
                int connectionState = BluetoothProfile.STATE_DISCONNECTED;
                LeAudioDeviceDescriptor descriptor = getDeviceDescriptor(device);
                if (descriptor == null) {
                    Log.e(TAG, "getDevicesMatchingConnectionStates: "
                            + "No valid descriptor for device: " + device);
                    return null;
                }

                LeAudioStateMachine sm = descriptor.mStateMachine;
                if (sm != null) {
                    connectionState = sm.getConnectionState();
                }
                for (int state : states) {
                    if (connectionState == state) {
                        devices.add(device);
                        break;
                    }
                }
            }
            return devices;
        }
    }

    /**
     * Get the list of devices that have state machines.
     *
     * @return the list of devices that have state machines
     */
    @VisibleForTesting
    List<BluetoothDevice> getDevices() {
        List<BluetoothDevice> devices = new ArrayList<>();
        synchronized (mGroupLock) {
            for (LeAudioDeviceDescriptor descriptor : mDeviceDescriptors.values()) {
                if (descriptor.mStateMachine != null) {
                    devices.add(descriptor.mStateMachine.getDevice());
                }
            }
            return devices;
        }
    }

    /**
     * Get the current connection state of the profile
     *
     * @param device is the remote bluetooth device
     * @return {@link BluetoothProfile#STATE_DISCONNECTED} if this profile is disconnected,
     * {@link BluetoothProfile#STATE_CONNECTING} if this profile is being connected,
     * {@link BluetoothProfile#STATE_CONNECTED} if this profile is connected, or
     * {@link BluetoothProfile#STATE_DISCONNECTING} if this profile is being disconnected
     */
    public int getConnectionState(BluetoothDevice device) {
        synchronized (mGroupLock) {
            LeAudioDeviceDescriptor descriptor = getDeviceDescriptor(device);
            if (descriptor == null) {
                return BluetoothProfile.STATE_DISCONNECTED;
            }

            LeAudioStateMachine sm = descriptor.mStateMachine;
            if (sm == null) {
                return BluetoothProfile.STATE_DISCONNECTED;
            }
            return sm.getConnectionState();
        }
    }

    /**
     * Add device to the given group.
     * @param groupId group ID the device is being added to
     * @param device the active device
     * @return true on success, otherwise false
     */
    boolean groupAddNode(int groupId, BluetoothDevice device) {
        if (!mLeAudioNativeIsInitialized) {
            Log.e(TAG, "Le Audio not initialized properly.");
            return false;
        }
        return mLeAudioNativeInterface.groupAddNode(groupId, device);
    }

    /**
     * Remove device from a given group.
     * @param groupId group ID the device is being removed from
     * @param device the active device
     * @return true on success, otherwise false
     */
    boolean groupRemoveNode(int groupId, BluetoothDevice device) {
        if (!mLeAudioNativeIsInitialized) {
            Log.e(TAG, "Le Audio not initialized properly.");
            return false;
        }
        return mLeAudioNativeInterface.groupRemoveNode(groupId, device);
    }

    /**
     * Checks if given group exists.
     * @param group_id group Id to verify
     * @return true given group exists, otherwise false
     */
    public boolean isValidDeviceGroup(int groupId) {
        synchronized (mGroupLock) {
            return groupId != LE_AUDIO_GROUP_ID_INVALID && mGroupDescriptors.containsKey(groupId);
        }
    }

    /**
     * Get all the devices within a given group.
     * @param groupId group id to get devices
     * @return all devices within a given group or empty list
     */
    public List<BluetoothDevice> getGroupDevices(int groupId) {
        List<BluetoothDevice> result = new ArrayList<>();

        if (groupId == LE_AUDIO_GROUP_ID_INVALID) {
            return result;
        }

        synchronized (mGroupLock) {
            for (Map.Entry<BluetoothDevice, LeAudioDeviceDescriptor> entry
                    : mDeviceDescriptors.entrySet()) {
                if (entry.getValue().mGroupId == groupId) {
                    result.add(entry.getKey());
                }
            }
        }
        return result;
    }

    private Integer getActiveGroupId() {
        synchronized (mGroupLock) {
            for (Map.Entry<Integer, LeAudioGroupDescriptor> entry : mGroupDescriptors.entrySet()) {
                LeAudioGroupDescriptor descriptor = entry.getValue();
                if (descriptor.mIsActive) {
                    return entry.getKey();
                }
            }
        }
        return LE_AUDIO_GROUP_ID_INVALID;
    }

    /**
     * Creates LeAudio Broadcast instance.
     * @param metadata metadata buffer with TLVs
     */
    public void createBroadcast(BluetoothLeAudioContentMetadata metadata, byte[] broadcastCode) {
        if (mLeAudioBroadcasterNativeInterface == null) {
            Log.w(TAG, "Native interface not available.");
            return;
        }
        boolean isEncrypted = (broadcastCode != null) && (broadcastCode.length != 0);
        if (isEncrypted) {
            if ((broadcastCode.length > 16) || (broadcastCode.length < 4)) {
                Log.e(TAG, "Invalid broadcast code length. Should be from 4 to 16 octets long.");
                return;
            }
        }

        Log.i(TAG, "createBroadcast: isEncrypted=" + (isEncrypted ? "true" : "false"));
        mLeAudioBroadcasterNativeInterface.createBroadcast(metadata.getRawMetadata(),
                broadcastCode);
    }

    /**
     * Start LeAudio Broadcast instance.
     * @param broadcastId broadcast instance identifier
     */
    public void startBroadcast(int broadcastId) {
        if (mLeAudioBroadcasterNativeInterface == null) {
            Log.w(TAG, "Native interface not available.");
            return;
        }
        if (DBG) Log.d(TAG, "startBroadcast");
        mLeAudioBroadcasterNativeInterface.startBroadcast(broadcastId);
    }

    /**
     * Updates LeAudio Broadcast instance metadata.
     * @param broadcastId broadcast instance identifier
     * @param metadata metadata for the default Broadcast subgroup
     */
    public void updateBroadcast(int broadcastId, BluetoothLeAudioContentMetadata metadata) {
        if (mLeAudioBroadcasterNativeInterface == null) {
            Log.w(TAG, "Native interface not available.");
            return;
        }
        if (!mBroadcastStateMap.containsKey(broadcastId)) {
            notifyBroadcastUpdateFailed(broadcastId,
                    BluetoothStatusCodes.ERROR_LE_BROADCAST_INVALID_BROADCAST_ID);
            return;
        }

        if (DBG) Log.d(TAG, "updateBroadcast");
        mLeAudioBroadcasterNativeInterface.updateMetadata(broadcastId, metadata.getRawMetadata());
    }

    /**
     * Stop LeAudio Broadcast instance.
     * @param broadcastId broadcast instance identifier
     */
    public void stopBroadcast(Integer broadcastId) {
        if (mLeAudioBroadcasterNativeInterface == null) {
            Log.w(TAG, "Native interface not available.");
            return;
        }
        if (!mBroadcastStateMap.containsKey(broadcastId)) {
            notifyOnBroadcastStopFailed(
                    BluetoothStatusCodes.ERROR_LE_BROADCAST_INVALID_BROADCAST_ID);
            return;
        }

        if (DBG) Log.d(TAG, "stopBroadcast");
        mLeAudioBroadcasterNativeInterface.stopBroadcast(broadcastId);
    }

    /**
     * Destroy LeAudio Broadcast instance.
     * @param broadcastId broadcast instance identifier
     */
    public void destroyBroadcast(int broadcastId) {
        if (mLeAudioBroadcasterNativeInterface == null) {
            Log.w(TAG, "Native interface not available.");
            return;
        }
        if (!mBroadcastStateMap.containsKey(broadcastId)) {
            notifyOnBroadcastStopFailed(
                    BluetoothStatusCodes.ERROR_LE_BROADCAST_INVALID_BROADCAST_ID);
            return;
        }

        if (DBG) Log.d(TAG, "destroyBroadcast");
        mLeAudioBroadcasterNativeInterface.destroyBroadcast(broadcastId);
    }

    /**
     * Checks if Broadcast instance is playing.
     * @param broadcastId broadcast instance identifier
     * @return true if if broadcast is playing, false otherwise
     */
    public boolean isPlaying(int broadcastId) {
        return mBroadcastsPlaybackMap.getOrDefault(broadcastId, false);
    }

    /**
     * Get all broadcast metadata.
     * @return list of all know Broadcast metadata
     */
    public List<BluetoothLeBroadcastMetadata> getAllBroadcastMetadata() {
        return new ArrayList<BluetoothLeBroadcastMetadata>(mBroadcastMetadataList.values());
    }

    /**
     * Get the maximum number of supported simultaneous broadcasts.
     * @return number of supported simultaneous broadcasts
     */
    public int getMaximumNumberOfBroadcasts() {
        /* TODO: This is currently fixed to 1 */
        return 1;
    }

    private BluetoothDevice getFirstDeviceFromGroup(Integer groupId) {
        if (groupId == LE_AUDIO_GROUP_ID_INVALID) {
            return null;
        }
        synchronized (mGroupLock) {
            for (LeAudioDeviceDescriptor descriptor : mDeviceDescriptors.values()) {
                if (!descriptor.mGroupId.equals(groupId)) {
                    continue;
                }

                LeAudioStateMachine sm = descriptor.mStateMachine;
                if (sm == null || sm.getConnectionState() != BluetoothProfile.STATE_CONNECTED) {
                    continue;
                }
                return sm.getDevice();
            }
        }
        return null;
    }

    private boolean updateActiveInDevice(BluetoothDevice device, Integer groupId,
            Integer oldSupportedAudioDirections, Integer newSupportedAudioDirections) {
        boolean oldSupportedByDeviceInput = (oldSupportedAudioDirections
                & AUDIO_DIRECTION_INPUT_BIT) != 0;
        boolean newSupportedByDeviceInput = (newSupportedAudioDirections
                & AUDIO_DIRECTION_INPUT_BIT) != 0;

        /*
         * Do not update input if neither previous nor current device support input
         */
        if (!oldSupportedByDeviceInput && !newSupportedByDeviceInput) {
            Log.d(TAG, "updateActiveInDevice: Device does not support input.");
            return false;
        }

        if (device != null && mActiveAudioInDevice != null) {
            LeAudioDeviceDescriptor deviceDescriptor = getDeviceDescriptor(device);
            if (deviceDescriptor == null) {
                Log.e(TAG, "updateActiveInDevice: No valid descriptor for device: " + device);
                return false;
            }

            if (deviceDescriptor.mGroupId.equals(groupId)) {
                /* This is thes same group as aleady notified to the system.
                 * Therefore do not change the device we have connected to the group,
                 * unless, previous one is disconnected now
                 */
                if (mActiveAudioInDevice.isConnected()) {
                    device = mActiveAudioInDevice;
                }
            } else if (deviceDescriptor.mGroupId != LE_AUDIO_GROUP_ID_INVALID) {
                /* Mark old group as no active */
                LeAudioGroupDescriptor descriptor = getGroupDescriptor(deviceDescriptor.mGroupId);
                if (descriptor != null) {
                    descriptor.mIsActive = false;
                }
            }
        }

        BluetoothDevice previousInDevice = mActiveAudioInDevice;

        /*
         * Update input if:
         * - Device changed
         *     OR
         * - Device stops / starts supporting input
         */
        if (!Objects.equals(device, previousInDevice)
                || (oldSupportedByDeviceInput != newSupportedByDeviceInput)) {
            mActiveAudioInDevice = newSupportedByDeviceInput ? device : null;
            if (DBG) {
                Log.d(TAG, " handleBluetoothActiveDeviceChanged  previousInDevice: "
                        + previousInDevice + ", mActiveAudioInDevice" + mActiveAudioInDevice
                        + " isLeOutput: false");
            }

            return true;
        }
        Log.d(TAG, "updateActiveInDevice: Nothing to do.");
        return false;
    }

    private boolean updateActiveOutDevice(BluetoothDevice device, Integer groupId,
            Integer oldSupportedAudioDirections, Integer newSupportedAudioDirections) {
        boolean oldSupportedByDeviceOutput = (oldSupportedAudioDirections
                & AUDIO_DIRECTION_OUTPUT_BIT) != 0;
        boolean newSupportedByDeviceOutput = (newSupportedAudioDirections
                & AUDIO_DIRECTION_OUTPUT_BIT) != 0;

        /*
         * Do not update output if neither previous nor current device support output
         */
        if (!oldSupportedByDeviceOutput && !newSupportedByDeviceOutput) {
            Log.d(TAG, "updateActiveOutDevice: Device does not support output.");
            return false;
        }

        if (device != null && mActiveAudioOutDevice != null) {
            LeAudioDeviceDescriptor deviceDescriptor = getDeviceDescriptor(device);
            if (deviceDescriptor == null) {
                Log.e(TAG, "updateActiveOutDevice: No valid descriptor for device: " + device);
                return false;
            }

            if (deviceDescriptor.mGroupId.equals(groupId)) {
                /* This is the same group as already notified to the system.
                 * Therefore do not change the device we have connected to the group,
                 * unless, previous one is disconnected now
                 */
                if (mActiveAudioOutDevice.isConnected()) {
                    device = mActiveAudioOutDevice;
                }
            } else if (deviceDescriptor.mGroupId != LE_AUDIO_GROUP_ID_INVALID) {
                Log.i(TAG, " Switching active group from " + deviceDescriptor.mGroupId + " to "
                        + groupId);
                /* Mark old group as no active */
                LeAudioGroupDescriptor descriptor = getGroupDescriptor(deviceDescriptor.mGroupId);
                if (descriptor != null) {
                    descriptor.mIsActive = false;
                }
            }
        }

        BluetoothDevice previousOutDevice = mActiveAudioOutDevice;

        /*
         * Update output if:
         * - Device changed
         *     OR
         * - Device stops / starts supporting output
         */
        if (!Objects.equals(device, previousOutDevice)
                || (oldSupportedByDeviceOutput != newSupportedByDeviceOutput)) {
            mActiveAudioOutDevice = newSupportedByDeviceOutput ? device : null;
            if (DBG) {
                Log.d(TAG, " handleBluetoothActiveDeviceChanged previousOutDevice: "
                        + previousOutDevice + ", mActiveOutDevice: " + mActiveAudioOutDevice
                        + " isLeOutput: true");
            }
            return true;
        }
        Log.d(TAG, "updateActiveOutDevice: Nothing to do.");
        return false;
    }

    /**
     * Send broadcast intent about LeAudio active device.
     * This is called when AudioManager confirms, LeAudio device
     * is added or removed.
     */
    @VisibleForTesting
    void notifyActiveDeviceChanged() {
        Intent intent = new Intent(BluetoothLeAudio.ACTION_LE_AUDIO_ACTIVE_DEVICE_CHANGED);
        intent.putExtra(BluetoothDevice.EXTRA_DEVICE,
                mActiveAudioOutDevice != null ? mActiveAudioOutDevice : mActiveAudioInDevice);
        intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT
                | Intent.FLAG_RECEIVER_INCLUDE_BACKGROUND);
        sendBroadcast(intent, BLUETOOTH_CONNECT);
    }

    /* Notifications of audio device disconnection events. */
    private class AudioManagerRemoveAudioDeviceCallback extends AudioDeviceCallback {
        @Override
        public void onAudioDevicesRemoved(AudioDeviceInfo[] removedDevices) {
            if (mAudioManager == null) {
                Log.e(TAG, "Callback called when LeAudioService is stopped");
                return;
            }

            for (AudioDeviceInfo deviceInfo : removedDevices) {
                if (deviceInfo.getType() == AudioDeviceInfo.TYPE_BLE_HEADSET
                        || deviceInfo.getType() == AudioDeviceInfo.TYPE_BLE_SPEAKER) {
                    notifyActiveDeviceChanged();
                    if (DBG) {
                        Log.d(TAG, " onAudioDevicesRemoved: device type: " + deviceInfo.getType());
                    }
                    mAudioManager.unregisterAudioDeviceCallback(this);
                }
            }
        }
    }

    /* Notifications of audio device connection events. */
    private class AudioManagerAddAudioDeviceCallback extends AudioDeviceCallback {
        @Override
        public void onAudioDevicesAdded(AudioDeviceInfo[] addedDevices) {
            if (mAudioManager == null) {
                Log.e(TAG, "Callback called when LeAudioService is stopped");
                return;
            }

            for (AudioDeviceInfo deviceInfo : addedDevices) {
                if (deviceInfo.getType() == AudioDeviceInfo.TYPE_BLE_HEADSET
                        || deviceInfo.getType() == AudioDeviceInfo.TYPE_BLE_SPEAKER) {
                    notifyActiveDeviceChanged();
                    if (DBG) {
                        Log.d(TAG, " onAudioDevicesAdded: device type: " + deviceInfo.getType());
                    }
                    mAudioManager.unregisterAudioDeviceCallback(this);
                }
            }
        }
    }

    /**
     * Report the active devices change to the active device manager and the media framework.
     * @param groupId id of group which devices should be updated
     * @param newSupportedAudioDirections new supported audio directions for group of devices
     * @param oldSupportedAudioDirections old supported audio directions for group of devices
     * @param isActive if there is new active group
     * @return true if group is active after change false otherwise.
     */
    private boolean updateActiveDevices(Integer groupId, Integer oldSupportedAudioDirections,
            Integer newSupportedAudioDirections, boolean isActive) {
        BluetoothDevice device = null;
        BluetoothDevice previousActiveOutDevice = mActiveAudioOutDevice;
        BluetoothDevice previousActiveInDevice = mActiveAudioInDevice;

        if (isActive) {
            device = getFirstDeviceFromGroup(groupId);
        }

        boolean isNewActiveOutDevice = updateActiveOutDevice(device, groupId,
                oldSupportedAudioDirections, newSupportedAudioDirections);
        boolean isNewActiveInDevice = updateActiveInDevice(device, groupId,
                oldSupportedAudioDirections, newSupportedAudioDirections);

        if (DBG) {
            Log.d(TAG, " isNewActiveOutDevice: " + isNewActiveOutDevice + ", "
                    + mActiveAudioOutDevice + ", isNewActiveInDevice: " + isNewActiveInDevice
                    + ", " + mActiveAudioInDevice);
        }

        /* Active device changed, there is need to inform about new active LE Audio device */
        if (isNewActiveOutDevice || isNewActiveInDevice) {
            /* Register for new device connection/disconnection in Audio Manager */
            if (mActiveAudioOutDevice != null || mActiveAudioInDevice != null) {
                /* Register for any device connection in case if any of devices become connected */
                mAudioManager.registerAudioDeviceCallback(mAudioManagerAddAudioDeviceCallback,
                        mHandler);
            } else {
                /* Register for disconnection if active devices become non-active */
                mAudioManager.registerAudioDeviceCallback(mAudioManagerRemoveAudioDeviceCallback,
                        mHandler);
            }
        }

        if (isNewActiveOutDevice) {
            int volume = IBluetoothVolumeControl.VOLUME_CONTROL_UNKNOWN_VOLUME;

            if (mActiveAudioOutDevice != null) {
                volume = getAudioDeviceGroupVolume(groupId);
            }

            final boolean suppressNoisyIntent = (mActiveAudioOutDevice != null)
                    || (getConnectionState(previousActiveOutDevice)
                    == BluetoothProfile.STATE_CONNECTED);

            mAudioManager.handleBluetoothActiveDeviceChanged(mActiveAudioOutDevice,
                    previousActiveOutDevice, getLeAudioOutputProfile(suppressNoisyIntent, volume));
        }

        if (isNewActiveInDevice) {
            mAudioManager.handleBluetoothActiveDeviceChanged(mActiveAudioInDevice,
                    previousActiveInDevice, BluetoothProfileConnectionInfo.createLeAudioInfo(false,
                            false));
        }

        return mActiveAudioOutDevice != null;
    }

    /**
     * Set the active device group.
     */
    private void setActiveGroupWithDevice(BluetoothDevice device) {
        int groupId = LE_AUDIO_GROUP_ID_INVALID;

        if (device != null) {
            LeAudioDeviceDescriptor descriptor = getDeviceDescriptor(device);
            if (descriptor == null) {
                Log.e(TAG, "setActiveGroupWithDevice: No valid descriptor for device: " + device);
                return;
            }

            groupId = descriptor.mGroupId;
        }

        int currentlyActiveGroupId = getActiveGroupId();
        if (DBG) {
            Log.d(TAG, "setActiveGroupWithDevice = " + groupId
                    + ", currentlyActiveGroupId = " + currentlyActiveGroupId
                    + ", device: " + device);
        }

        if (groupId == currentlyActiveGroupId) {
            if (groupId != LE_AUDIO_GROUP_ID_INVALID) {
                Log.w(TAG, "group is already active: device=" + device + ", groupId = " + groupId);
            }
            return;
        }

        if (!mLeAudioNativeIsInitialized) {
            Log.e(TAG, "Le Audio not initialized properly.");
            return;
        }
        mLeAudioNativeInterface.groupSetActive(groupId);
        if (groupId == LE_AUDIO_GROUP_ID_INVALID) {
            /* Native will clear its states and send us group Inactive.
             * However we would like to notify audio framework that LeAudio is not
             * active anymore and does not want to get more audio data.
             */
            handleGroupTransitToInactive(currentlyActiveGroupId);
        }
    }

    /**
     * Set the active group represented by device.
     *
     * @param device the new active device
     * @return true on success, otherwise false
     */
    public boolean setActiveDevice(BluetoothDevice device) {
        /* Clear active group */
        if (device == null) {
            setActiveGroupWithDevice(device);
            return true;
        }
        if (getConnectionState(device) != BluetoothProfile.STATE_CONNECTED) {
            Log.e(TAG, "setActiveDevice(" + device + "): failed because group device is not "
                    + "connected");
            return false;
        }
        setActiveGroupWithDevice(device);
        return true;
    }

    /**
     * Get the active LE audio devices.
     *
     * Note: When LE audio group is active, one of the Bluetooth device address
     * which belongs to the group, represents the active LE audio group - it is called
     * Lead device.
     * Internally, this address is translated to LE audio group id.
     *
     * @return List of active group members. First element is a Lead device.
     */
    public List<BluetoothDevice> getActiveDevices() {
        if (DBG) {
            Log.d(TAG, "getActiveDevices");
        }
        ArrayList<BluetoothDevice> activeDevices = new ArrayList<>(2);
        activeDevices.add(null);
        activeDevices.add(null);

        int currentlyActiveGroupId = getActiveGroupId();
        if (currentlyActiveGroupId == LE_AUDIO_GROUP_ID_INVALID) {
            return activeDevices;
        }

        BluetoothDevice leadDevice = getConnectedGroupLeadDevice(currentlyActiveGroupId);
        activeDevices.set(0, leadDevice);

        int i = 1;
        for (BluetoothDevice dev : getGroupDevices(currentlyActiveGroupId)) {
            if (Objects.equals(dev, leadDevice)) {
                continue;
            }
            if (i == 1) {
                /* Already has a spot for first member */
                activeDevices.set(i++, dev);
            } else {
                /* Extend list with other members */
                activeDevices.add(dev);
            }
        }
        return activeDevices;
    }

    void connectSet(BluetoothDevice device) {
        LeAudioDeviceDescriptor descriptor = getDeviceDescriptor(device);
        if (descriptor == null) {
            Log.e(TAG, "connectSet: No valid descriptor for device: " + device);
            return;
        }
        if (descriptor.mGroupId == LE_AUDIO_GROUP_ID_INVALID) {
            return;
        }

        if (DBG) {
            Log.d(TAG, "connect() others from group id: " + descriptor.mGroupId);
        }

        Integer setGroupId = descriptor.mGroupId;

        for (Map.Entry<BluetoothDevice, LeAudioDeviceDescriptor> entry
                : mDeviceDescriptors.entrySet()) {
            BluetoothDevice storedDevice = entry.getKey();
            descriptor = entry.getValue();
            if (device.equals(storedDevice)) {
                continue;
            }

            if (!descriptor.mGroupId.equals(setGroupId)) {
                continue;
            }

            if (DBG) {
                Log.d(TAG, "connect(): " + storedDevice);
            }

            synchronized (mGroupLock) {
                LeAudioStateMachine sm = getOrCreateStateMachine(storedDevice);
                if (sm == null) {
                    Log.e(TAG, "Ignored connect request for " + storedDevice
                            + " : no state machine");
                    continue;
                }
                sm.sendMessage(LeAudioStateMachine.CONNECT);
            }
        }
    }

    BluetoothProfileConnectionInfo getLeAudioOutputProfile(boolean suppressNoisyIntent,
            int volume) {
        /* TODO - b/236618595 */
        Parcel parcel = Parcel.obtain();
        parcel.writeInt(BluetoothProfile.LE_AUDIO);
        parcel.writeBoolean(suppressNoisyIntent);
        parcel.writeInt(volume);
        parcel.writeBoolean(true /* isLeOutput */);
        parcel.setDataPosition(0);

        BluetoothProfileConnectionInfo profileInfo =
                BluetoothProfileConnectionInfo.CREATOR.createFromParcel(parcel);
        parcel.recycle();
        return profileInfo;
    }

    BluetoothProfileConnectionInfo getBroadcastProfile(boolean suppressNoisyIntent) {
        Parcel parcel = Parcel.obtain();
        parcel.writeInt(BluetoothProfile.LE_AUDIO_BROADCAST);
        parcel.writeBoolean(suppressNoisyIntent);
        parcel.writeInt(-1 /* mVolume */);
        parcel.writeBoolean(true /* mIsLeOutput */);
        parcel.setDataPosition(0);

        BluetoothProfileConnectionInfo profileInfo =
                BluetoothProfileConnectionInfo.CREATOR.createFromParcel(parcel);
        parcel.recycle();
        return profileInfo;
    }

    private void clearLostDevicesWhileStreaming(LeAudioGroupDescriptor descriptor) {
        synchronized (mGroupLock) {
            if (DBG) {
                Log.d(TAG, "Clearing lost dev: " + descriptor.mLostLeadDeviceWhileStreaming);
            }

            LeAudioDeviceDescriptor deviceDescriptor =
                    getDeviceDescriptor(descriptor.mLostLeadDeviceWhileStreaming);
            if (deviceDescriptor == null) {
                Log.e(TAG, "clearLostDevicesWhileStreaming: No valid descriptor for device: "
                        + descriptor.mLostLeadDeviceWhileStreaming);
                return;
            }

            LeAudioStateMachine sm = deviceDescriptor.mStateMachine;
            if (sm != null) {
                LeAudioStackEvent stackEvent =
                        new LeAudioStackEvent(
                                LeAudioStackEvent.EVENT_TYPE_CONNECTION_STATE_CHANGED);
                stackEvent.device = descriptor.mLostLeadDeviceWhileStreaming;
                stackEvent.valueInt1 = LeAudioStackEvent.CONNECTION_STATE_DISCONNECTED;
                sm.sendMessage(LeAudioStateMachine.STACK_EVENT, stackEvent);
            }
            descriptor.mLostLeadDeviceWhileStreaming = null;
        }
    }

    private void handleGroupTransitToActive(int groupId) {
        synchronized (mGroupLock) {
            LeAudioGroupDescriptor descriptor = getGroupDescriptor(groupId);
            if (descriptor == null || descriptor.mIsActive) {
                Log.e(TAG, "no descriptors for group: " + groupId + " or group already active");
                return;
            }

            descriptor.mIsActive = updateActiveDevices(groupId, AUDIO_DIRECTION_NONE,
                    descriptor.mDirection, true);

            if (descriptor.mIsActive) {
                notifyGroupStatusChanged(groupId, LeAudioStackEvent.GROUP_STATUS_ACTIVE);
            }
        }
    }

    private void handleGroupTransitToInactive(int groupId) {
        synchronized (mGroupLock) {
            LeAudioGroupDescriptor descriptor = getGroupDescriptor(groupId);
            if (descriptor == null || !descriptor.mIsActive) {
                Log.e(TAG, "no descriptors for group: " + groupId + " or group already inactive");
                return;
            }

            descriptor.mIsActive = false;
            updateActiveDevices(groupId, descriptor.mDirection, AUDIO_DIRECTION_NONE,
                    descriptor.mIsActive);
            /* Clear lost devices */
            if (DBG) Log.d(TAG, "Clear for group: " + groupId);
            clearLostDevicesWhileStreaming(descriptor);
            notifyGroupStatusChanged(groupId, LeAudioStackEvent.GROUP_STATUS_INACTIVE);
        }
    }

    @VisibleForTesting
    void handleGroupIdleDuringCall() {
        if (mHfpHandoverDevice == null) {
            if (DBG) {
                Log.d(TAG, "There is no HFP handover");
            }
            return;
        }
        HeadsetService headsetService = mServiceFactory.getHeadsetService();
        if (headsetService == null) {
            if (DBG) {
                Log.d(TAG, "There is no HFP service available");
            }
            return;
        }

        BluetoothDevice activeHfpDevice = headsetService.getActiveDevice();
        if (activeHfpDevice == null) {
            if (DBG) {
                Log.d(TAG, "Make " + mHfpHandoverDevice + " active again ");
            }
            headsetService.setActiveDevice(mHfpHandoverDevice);
        } else {
            if (DBG) {
                Log.d(TAG, "Connect audio to " + activeHfpDevice);
            }
            headsetService.connectAudio();
        }
        mHfpHandoverDevice = null;
    }

    // Suppressed since this is part of a local process
    @SuppressLint("AndroidFrameworkRequiresPermission")
    void messageFromNative(LeAudioStackEvent stackEvent) {
        Log.d(TAG, "Message from native: " + stackEvent);
        BluetoothDevice device = stackEvent.device;

        if (stackEvent.type == LeAudioStackEvent.EVENT_TYPE_CONNECTION_STATE_CHANGED) {
            // Some events require device state machine
            synchronized (mGroupLock) {
                LeAudioDeviceDescriptor deviceDescriptor = getDeviceDescriptor(device);
                if (deviceDescriptor == null) {
                    Log.e(TAG, "messageFromNative: No valid descriptor for device: " + device);
                    return;
                }

                LeAudioStateMachine sm = deviceDescriptor.mStateMachine;
                if (sm != null) {
                    /*
                     * To improve scenario when lead Le Audio device is disconnected for the
                     * streaming group, while there are still other devices streaming,
                     * LeAudioService will not notify audio framework or other users about
                     * Le Audio lead device disconnection. Instead we try to reconnect under
                     * the hood and keep using lead device as a audio device indetifier in
                     * the audio framework in order to not stop the stream.
                     */
                    int groupId = deviceDescriptor.mGroupId;
                    LeAudioGroupDescriptor descriptor = mGroupDescriptors.get(groupId);
                    switch (stackEvent.valueInt1) {
                        case LeAudioStackEvent.CONNECTION_STATE_DISCONNECTING:
                        case LeAudioStackEvent.CONNECTION_STATE_DISCONNECTED:
                            boolean disconnectDueToUnbond =
                                    (BluetoothDevice.BOND_NONE
                                            == mAdapterService.getBondState(device));
                            if (descriptor != null && (Objects.equals(device,
                                    mActiveAudioOutDevice)
                                    || Objects.equals(device, mActiveAudioInDevice))
                                    && (getConnectedPeerDevices(groupId).size() > 1)
                                    && !disconnectDueToUnbond) {

                                if (DBG) Log.d(TAG, "Adding to lost devices : " + device);
                                descriptor.mLostLeadDeviceWhileStreaming = device;
                                return;
                            }
                            break;
                        case LeAudioStackEvent.CONNECTION_STATE_CONNECTED:
                        case LeAudioStackEvent.CONNECTION_STATE_CONNECTING:
                            if (descriptor != null
                                    && Objects.equals(
                                            descriptor.mLostLeadDeviceWhileStreaming,
                                            device)) {
                                if (DBG) {
                                    Log.d(TAG, "Removing from lost devices : " + device);
                                }
                                descriptor.mLostLeadDeviceWhileStreaming = null;
                                /* Try to connect other devices from the group */
                                connectSet(device);
                            }
                            break;
                    }
                } else {
                    /* state machine does not exist yet */
                    switch (stackEvent.valueInt1) {
                        case LeAudioStackEvent.CONNECTION_STATE_CONNECTED:
                        case LeAudioStackEvent.CONNECTION_STATE_CONNECTING:
                            sm = getOrCreateStateMachine(device);
                            /* Incoming connection try to connect other devices from the group */
                            connectSet(device);
                            break;
                        default:
                            break;
                    }

                    if (sm == null) {
                        Log.e(TAG, "Cannot process stack event: no state machine: " + stackEvent);
                        return;
                    }
                }

                sm.sendMessage(LeAudioStateMachine.STACK_EVENT, stackEvent);
                return;
            }
        } else if (stackEvent.type == LeAudioStackEvent.EVENT_TYPE_GROUP_NODE_STATUS_CHANGED) {
            int groupId = stackEvent.valueInt1;
            int nodeStatus = stackEvent.valueInt2;

            Objects.requireNonNull(stackEvent.device,
                    "Device should never be null, event: " + stackEvent);

            switch (nodeStatus) {
                case LeAudioStackEvent.GROUP_NODE_ADDED:
                    handleGroupNodeAdded(device, groupId);
                    break;
                case LeAudioStackEvent.GROUP_NODE_REMOVED:
                    handleGroupNodeRemoved(device, groupId);
                    break;
                default:
                    break;
            }
        } else if (stackEvent.type
                == LeAudioStackEvent.EVENT_TYPE_AUDIO_LOCAL_CODEC_CONFIG_CAPA_CHANGED) {
            mInputLocalCodecCapabilities = stackEvent.valueCodecList1;
            mOutputLocalCodecCapabilities = stackEvent.valueCodecList2;
        } else if (stackEvent.type
                == LeAudioStackEvent.EVENT_TYPE_AUDIO_GROUP_CODEC_CONFIG_CHANGED) {
            int groupId = stackEvent.valueInt1;
            LeAudioGroupDescriptor descriptor = getGroupDescriptor(groupId);
            if (descriptor == null) {
                Log.e(TAG, " Group not found " + groupId);
                return;
            }

            BluetoothLeAudioCodecStatus status =
                    new BluetoothLeAudioCodecStatus(stackEvent.valueCodec1,
                            stackEvent.valueCodec2, mInputLocalCodecCapabilities,
                            mOutputLocalCodecCapabilities,
                            stackEvent.valueCodecList1,
                            stackEvent.valueCodecList2);

            if (DBG) {
                if (descriptor.mCodecStatus != null) {
                    Log.d(TAG, " Replacing codec status for group: " + groupId);
                } else {
                    Log.d(TAG, " New codec status for group: " + groupId);
                }
            }

            descriptor.mCodecStatus = status;
            notifyUnicastCodecConfigChanged(groupId, status);
        } else if (stackEvent.type == LeAudioStackEvent.EVENT_TYPE_AUDIO_CONF_CHANGED) {
            int direction = stackEvent.valueInt1;
            int groupId = stackEvent.valueInt2;
            int snk_audio_location = stackEvent.valueInt3;
            int src_audio_location = stackEvent.valueInt4;
            int available_contexts = stackEvent.valueInt5;

            synchronized (mGroupLock) {
                LeAudioGroupDescriptor descriptor = getGroupDescriptor(groupId);
                if (descriptor != null) {
                    if (descriptor.mIsActive) {
                        descriptor.mIsActive =
                                updateActiveDevices(groupId, descriptor.mDirection, direction,
                                descriptor.mIsActive);
                        if (!descriptor.mIsActive) {
                            notifyGroupStatusChanged(groupId,
                                    BluetoothLeAudio.GROUP_STATUS_INACTIVE);
                        }
                    }
                    descriptor.mDirection = direction;
                } else {
                    Log.e(TAG, "no descriptors for group: " + groupId);
                }
            }
        } else if (stackEvent.type == LeAudioStackEvent.EVENT_TYPE_SINK_AUDIO_LOCATION_AVAILABLE) {
            Objects.requireNonNull(stackEvent.device,
                    "Device should never be null, event: " + stackEvent);

            int sink_audio_location = stackEvent.valueInt1;

            LeAudioDeviceDescriptor descriptor = getDeviceDescriptor(device);
            if (descriptor == null) {
                Log.e(TAG, "messageFromNative: No valid descriptor for device: " + device);
                return;
            }

            descriptor.mSinkAudioLocation = sink_audio_location;

            if (DBG) {
                Log.i(TAG, "EVENT_TYPE_SINK_AUDIO_LOCATION_AVAILABLE:" + device
                        + " audio location:" + sink_audio_location);
            }
        } else if (stackEvent.type == LeAudioStackEvent.EVENT_TYPE_GROUP_STATUS_CHANGED) {
            int groupId = stackEvent.valueInt1;
            int groupStatus = stackEvent.valueInt2;

            switch (groupStatus) {
                case LeAudioStackEvent.GROUP_STATUS_ACTIVE: {
                    handleGroupTransitToActive(groupId);
                    break;
                }
                case LeAudioStackEvent.GROUP_STATUS_INACTIVE: {
                    handleGroupTransitToInactive(groupId);
                    break;
                }
                case LeAudioStackEvent.GROUP_STATUS_TURNED_IDLE_DURING_CALL: {
                    handleGroupIdleDuringCall();
                    break;
                }
                default:
                    break;
            }
        } else if (stackEvent.type == LeAudioStackEvent.EVENT_TYPE_BROADCAST_CREATED) {
            int broadcastId = stackEvent.valueInt1;
            boolean success = stackEvent.valueBool1;
            if (success) {
                Log.d(TAG, "Broadcast broadcastId: " + broadcastId + " created.");
                notifyBroadcastStarted(broadcastId, BluetoothStatusCodes.REASON_LOCAL_APP_REQUEST);

                // Start sending the actual stream
                startBroadcast(broadcastId);
            } else {
                // TODO: Improve reason reporting or extend the native stack event with reason code
                notifyBroadcastStartFailed(broadcastId, BluetoothStatusCodes.ERROR_UNKNOWN);
            }

        } else if (stackEvent.type == LeAudioStackEvent.EVENT_TYPE_BROADCAST_DESTROYED) {
            Integer broadcastId = stackEvent.valueInt1;

            // TODO: Improve reason reporting or extend the native stack event with reason code
            notifyOnBroadcastStopped(broadcastId, BluetoothStatusCodes.REASON_LOCAL_APP_REQUEST);

            mBroadcastsPlaybackMap.remove(broadcastId);
            mBroadcastStateMap.remove(broadcastId);
            mBroadcastMetadataList.remove(broadcastId);

        } else if (stackEvent.type == LeAudioStackEvent.EVENT_TYPE_BROADCAST_STATE) {
            int broadcastId = stackEvent.valueInt1;
            int state = stackEvent.valueInt2;

            /* Request broadcast details if not known yet */
            if (!mBroadcastStateMap.containsKey(broadcastId)) {
                mLeAudioBroadcasterNativeInterface.getBroadcastMetadata(broadcastId);
            }
            mBroadcastStateMap.put(broadcastId, state);

            if (state == LeAudioStackEvent.BROADCAST_STATE_STOPPED) {
                if (DBG) Log.d(TAG, "Broadcast broadcastId: " + broadcastId + " stopped.");

                // Playback stopped
                mBroadcastsPlaybackMap.put(broadcastId, false);
                notifyPlaybackStopped(broadcastId, BluetoothStatusCodes.REASON_LOCAL_APP_REQUEST);

                // Notify audio manager
                if (Collections.frequency(mBroadcastsPlaybackMap.values(), true) == 0) {
                    if (Objects.equals(device, mActiveAudioOutDevice)) {
                        BluetoothDevice previousDevice = mActiveAudioOutDevice;
                        mActiveAudioOutDevice = null;
                        mAudioManager.handleBluetoothActiveDeviceChanged(mActiveAudioOutDevice,
                                previousDevice,
                                getBroadcastProfile(true));
                    }
                }

                destroyBroadcast(broadcastId);

            } else if (state == LeAudioStackEvent.BROADCAST_STATE_CONFIGURING) {
                if (DBG) Log.d(TAG, "Broadcast broadcastId: " + broadcastId + " configuring.");

            } else if (state == LeAudioStackEvent.BROADCAST_STATE_PAUSED) {
                if (DBG) Log.d(TAG, "Broadcast broadcastId: " + broadcastId + " paused.");

                // Playback paused
                mBroadcastsPlaybackMap.put(broadcastId, false);
                notifyPlaybackStopped(broadcastId, BluetoothStatusCodes.REASON_LOCAL_STACK_REQUEST);

            } else if (state == LeAudioStackEvent.BROADCAST_STATE_STOPPING) {
                if (DBG) Log.d(TAG, "Broadcast broadcastId: " + broadcastId + " stopping.");

            } else if (state == LeAudioStackEvent.BROADCAST_STATE_STREAMING) {
                if (DBG) Log.d(TAG, "Broadcast broadcastId: " + broadcastId + " streaming.");

                // Stream resumed
                mBroadcastsPlaybackMap.put(broadcastId, true);
                notifyPlaybackStarted(broadcastId, BluetoothStatusCodes.REASON_LOCAL_STACK_REQUEST);

                // Notify audio manager
                if (Collections.frequency(mBroadcastsPlaybackMap.values(), true) == 1) {
                    if (!Objects.equals(device, mActiveAudioOutDevice)) {
                        BluetoothDevice previousDevice = mActiveAudioOutDevice;
                        mActiveAudioOutDevice = device;
                        mAudioManager.handleBluetoothActiveDeviceChanged(mActiveAudioOutDevice,
                                previousDevice,
                                getBroadcastProfile(false));
                    }
                }
            }
        } else if (stackEvent.type == LeAudioStackEvent.EVENT_TYPE_BROADCAST_METADATA_CHANGED) {
            int broadcastId = stackEvent.valueInt1;
            if (stackEvent.broadcastMetadata == null) {
                Log.e(TAG, "Missing Broadcast metadata for broadcastId: " + broadcastId);
            } else {
                mBroadcastMetadataList.put(broadcastId, stackEvent.broadcastMetadata);
                notifyBroadcastMetadataChanged(broadcastId, stackEvent.broadcastMetadata);
            }
        } else if (stackEvent.type == LeAudioStackEvent.EVENT_TYPE_NATIVE_INITIALIZED) {
            mLeAudioNativeIsInitialized = true;
            for (Map.Entry<ParcelUuid, Pair<Integer, Integer>> entry :
                    ContentControlIdKeeper.getUserCcidMap().entrySet()) {
                ParcelUuid userUuid = entry.getKey();
                Pair<Integer, Integer> ccidInformation = entry.getValue();
                setCcidInformation(userUuid, ccidInformation.first, ccidInformation.second);
            }
        }
    }

    private LeAudioStateMachine getOrCreateStateMachine(BluetoothDevice device) {
        if (device == null) {
            Log.e(TAG, "getOrCreateStateMachine failed: device cannot be null");
            return null;
        }

        LeAudioDeviceDescriptor descriptor = getDeviceDescriptor(device);
        if (descriptor == null) {
            Log.e(TAG, "getOrCreateStateMachine: No valid descriptor for device: " + device);
            return null;
        }

        LeAudioStateMachine sm = descriptor.mStateMachine;
        if (sm != null) {
            return sm;
        }

        if (DBG) {
            Log.d(TAG, "Creating a new state machine for " + device);
        }

        sm = LeAudioStateMachine.make(device, this,
                mLeAudioNativeInterface, mStateMachinesThread.getLooper());
        descriptor.mStateMachine = sm;
        return sm;
    }

    // Remove state machine if the bonding for a device is removed
    private class BondStateChangedReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(intent.getAction())) {
                return;
            }
            int state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE,
                    BluetoothDevice.ERROR);
            BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            Objects.requireNonNull(device, "ACTION_BOND_STATE_CHANGED with no EXTRA_DEVICE");
            bondStateChanged(device, state);
        }
    }

    /**
     * Process a change in the bonding state for a device.
     *
     * @param device the device whose bonding state has changed
     * @param bondState the new bond state for the device. Possible values are:
     * {@link BluetoothDevice#BOND_NONE},
     * {@link BluetoothDevice#BOND_BONDING},
     * {@link BluetoothDevice#BOND_BONDED}.
     */
    @VisibleForTesting
    void bondStateChanged(BluetoothDevice device, int bondState) {
        if (DBG) {
            Log.d(TAG, "Bond state changed for device: " + device + " state: " + bondState);
        }
        // Remove state machine if the bonding for a device is removed
        if (bondState != BluetoothDevice.BOND_NONE) {
            return;
        }

        synchronized (mGroupLock) {
            LeAudioDeviceDescriptor descriptor = getDeviceDescriptor(device);
            if (descriptor == null) {
                Log.e(TAG, "bondStateChanged: No valid descriptor for device: " + device);
                return;
            }

            if (descriptor.mGroupId != LE_AUDIO_GROUP_ID_INVALID) {
                /* In case device is still in the group, let's remove it */
                mLeAudioNativeInterface.groupRemoveNode(descriptor.mGroupId, device);
            }

            descriptor.mGroupId = LE_AUDIO_GROUP_ID_INVALID;
            descriptor.mSinkAudioLocation = BluetoothLeAudio.AUDIO_LOCATION_INVALID;
            descriptor.mDirection = AUDIO_DIRECTION_NONE;

            LeAudioStateMachine sm = descriptor.mStateMachine;
            if (sm == null) {
                return;
            }
            if (sm.getConnectionState() != BluetoothProfile.STATE_DISCONNECTED) {
                Log.w(TAG, "Device is not disconnected yet.");
                disconnect(device);
                return;
            }
            removeStateMachine(device);
            mDeviceDescriptors.remove(device);
        }
    }

    private void removeStateMachine(BluetoothDevice device) {
        synchronized (mGroupLock) {
            LeAudioDeviceDescriptor descriptor = getDeviceDescriptor(device);
            if (descriptor == null) {
                Log.e(TAG, "removeStateMachine: No valid descriptor for device: " + device);
                return;
            }

            LeAudioStateMachine sm = descriptor.mStateMachine;
            if (sm == null) {
                Log.w(TAG, "removeStateMachine: device " + device
                        + " does not have a state machine");
                return;
            }
            Log.i(TAG, "removeStateMachine: removing state machine for device: " + device);
            sm.doQuit();
            sm.cleanup();
            descriptor.mStateMachine = null;
        }
    }

    @VisibleForTesting
    List<BluetoothDevice> getConnectedPeerDevices(int groupId) {
        List<BluetoothDevice> result = new ArrayList<>();
        for (BluetoothDevice peerDevice : getConnectedDevices()) {
            if (getGroupId(peerDevice) == groupId) {
                result.add(peerDevice);
            }
        }
        return result;
    }

    @VisibleForTesting
    synchronized void connectionStateChanged(BluetoothDevice device, int fromState, int toState) {
        if ((device == null) || (fromState == toState)) {
            Log.e(TAG, "connectionStateChanged: unexpected invocation. device=" + device
                    + " fromState=" + fromState + " toState=" + toState);
            return;
        }

        LeAudioDeviceDescriptor deviceDescriptor = getDeviceDescriptor(device);
        if (deviceDescriptor == null) {
            Log.e(TAG, "connectionStateChanged: No valid descriptor for device: " + device);
            return;
        }

        if (toState == BluetoothProfile.STATE_CONNECTED) {
            if (deviceDescriptor.mGroupId == LE_AUDIO_GROUP_ID_INVALID
                    || getConnectedPeerDevices(deviceDescriptor.mGroupId).size() == 1) {
                // Log LE Audio connection event if we are the first device in a set
                // Or when the GroupId has not been found
                // MetricsLogger.logProfileConnectionEvent(
                //         BluetoothMetricsProto.ProfileId.LE_AUDIO);
            }

            LeAudioGroupDescriptor descriptor = getGroupDescriptor(deviceDescriptor.mGroupId);
            if (descriptor != null) {
                descriptor.mIsConnected = true;
            } else {
                Log.e(TAG, "no descriptors for group: " + deviceDescriptor.mGroupId);
            }
        }
        // Check if the device is disconnected - if unbond, remove the state machine
        if (toState == BluetoothProfile.STATE_DISCONNECTED) {
            int bondState = mAdapterService.getBondState(device);
            if (bondState == BluetoothDevice.BOND_NONE) {
                if (DBG) {
                    Log.d(TAG, device + " is unbond. Remove state machine");
                }
                removeStateMachine(device);
            }

            LeAudioGroupDescriptor descriptor = getGroupDescriptor(deviceDescriptor.mGroupId);
            if (descriptor == null) {
                Log.e(TAG, "no descriptors for group: " + deviceDescriptor.mGroupId);
                return;
            }

            List<BluetoothDevice> connectedDevices =
                    getConnectedPeerDevices(deviceDescriptor.mGroupId);
            /* Let's check if the last connected device is really connected */
            if (connectedDevices.size() == 1 && Objects.equals(
                    connectedDevices.get(0), descriptor.mLostLeadDeviceWhileStreaming)) {
                clearLostDevicesWhileStreaming(descriptor);
                return;
            }

            if (getConnectedPeerDevices(deviceDescriptor.mGroupId).isEmpty()) {
                descriptor.mIsConnected = false;
                if (descriptor.mIsActive) {
                    /* Notify Native layer */
                    setActiveDevice(null);
                    descriptor.mIsActive = false;
                    /* Update audio framework */
                    updateActiveDevices(deviceDescriptor.mGroupId,
                            descriptor.mDirection,
                            descriptor.mDirection,
                            descriptor.mIsActive);
                    return;
                }
            }

            if (descriptor.mIsActive) {
                updateActiveDevices(deviceDescriptor.mGroupId,
                        descriptor.mDirection,
                        descriptor.mDirection,
                        descriptor.mIsActive);
            }
        }
    }

    private class ConnectionStateChangedReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!BluetoothLeAudio.ACTION_LE_AUDIO_CONNECTION_STATE_CHANGED
                    .equals(intent.getAction())) {
                return;
            }
            BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            int toState = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1);
            int fromState = intent.getIntExtra(BluetoothProfile.EXTRA_PREVIOUS_STATE, -1);
            connectionStateChanged(device, fromState, toState);
        }
    }

    @VisibleForTesting
    synchronized boolean isSilentModeEnabled() {
        return mStoredRingerMode != AudioManager.RINGER_MODE_NORMAL;
    }

    private class MuteStateChangedReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!AudioManager.RINGER_MODE_CHANGED_ACTION.equals(intent.getAction())) {
                return;
            }

            final String action = intent.getAction();
            if (action.equals(AudioManager.RINGER_MODE_CHANGED_ACTION)) {
                if (!Utils.isPtsTestMode()) return;

                int ringerMode = intent.getIntExtra(AudioManager.EXTRA_RINGER_MODE, -1);

                if (ringerMode < 0 || ringerMode == mStoredRingerMode) return;

                mStoredRingerMode = ringerMode;
                int activeGroupId = getActiveGroupId();
                if (activeGroupId == LE_AUDIO_GROUP_ID_INVALID) return;

                VolumeControlService service = mServiceFactory.getVolumeControlService();
                if (service == null) return;

                if (isSilentModeEnabled()) {
                    service.muteGroup(activeGroupId);
                } else {
                    service.unmuteGroup(activeGroupId);
                }
            }
        }
    }

    /**
     * Check whether can connect to a peer device.
     * The check considers a number of factors during the evaluation.
     *
     * @param device the peer device to connect to
     * @return true if connection is allowed, otherwise false
     */
    public boolean okToConnect(BluetoothDevice device) {
        // Check if this is an incoming connection in Quiet mode.
        if (mAdapterService.isQuietModeEnabled()) {
            Log.e(TAG, "okToConnect: cannot connect to " + device + " : quiet mode enabled");
            return false;
        }
        // Check connectionPolicy and accept or reject the connection.
        int connectionPolicy = getConnectionPolicy(device);
        int bondState = mAdapterService.getBondState(device);
        // Allow this connection only if the device is bonded. Any attempt to connect while
        // bonding would potentially lead to an unauthorized connection.
        if (bondState != BluetoothDevice.BOND_BONDED) {
            Log.w(TAG, "okToConnect: return false, bondState=" + bondState);
            return false;
        } else if (connectionPolicy != BluetoothProfile.CONNECTION_POLICY_UNKNOWN
                && connectionPolicy != BluetoothProfile.CONNECTION_POLICY_ALLOWED) {
            // Otherwise, reject the connection if connectionPolicy is not valid.
            Log.w(TAG, "okToConnect: return false, connectionPolicy=" + connectionPolicy);
            return false;
        }
        return true;
    }

    /**
     * Get device audio location.
     * @param device LE Audio capable device
     * @return the sink audioi location that this device currently exposed
     */
    public int getAudioLocation(BluetoothDevice device) {
        if (device == null) {
            return BluetoothLeAudio.AUDIO_LOCATION_INVALID;
        }

        LeAudioDeviceDescriptor descriptor = getDeviceDescriptor(device);
        if (descriptor == null) {
            Log.e(TAG, "getAudioLocation: No valid descriptor for device: " + device);
            return BluetoothLeAudio.AUDIO_LOCATION_INVALID;
        }

        return descriptor.mSinkAudioLocation;
    }

    /**
     * Set In Call state
     * @param inCall True if device in call (any state), false otherwise.
     */
    public void setInCall(boolean inCall) {
        if (!mLeAudioNativeIsInitialized) {
            Log.e(TAG, "Le Audio not initialized properly.");
            return;
        }
        mLeAudioNativeInterface.setInCall(inCall);
    }

    /**
     * Set Inactive by HFP during handover
     */
    public void setInactiveForHfpHandover(BluetoothDevice hfpHandoverDevice) {
        if (!mLeAudioNativeIsInitialized) {
            Log.e(TAG, "Le Audio not initialized properly.");
            return;
        }
        if (getActiveGroupId() != LE_AUDIO_GROUP_ID_INVALID) {
            mHfpHandoverDevice = hfpHandoverDevice;
            setActiveDevice(null);
        }
    }

    /**
     * Set connection policy of the profile and connects it if connectionPolicy is
     * {@link BluetoothProfile#CONNECTION_POLICY_ALLOWED} or disconnects if connectionPolicy is
     * {@link BluetoothProfile#CONNECTION_POLICY_FORBIDDEN}
     *
     * <p> The device should already be paired.
     * Connection policy can be one of:
     * {@link BluetoothProfile#CONNECTION_POLICY_ALLOWED},
     * {@link BluetoothProfile#CONNECTION_POLICY_FORBIDDEN},
     * {@link BluetoothProfile#CONNECTION_POLICY_UNKNOWN}
     *
     * @param device the remote device
     * @param connectionPolicy is the connection policy to set to for this profile
     * @return true on success, otherwise false
     */
    @RequiresPermission(android.Manifest.permission.BLUETOOTH_PRIVILEGED)
    public boolean setConnectionPolicy(BluetoothDevice device, int connectionPolicy) {
        enforceCallingOrSelfPermission(BLUETOOTH_PRIVILEGED,
                "Need BLUETOOTH_PRIVILEGED permission");
        if (DBG) {
            Log.d(TAG, "Saved connectionPolicy " + device + " = " + connectionPolicy);
        }

        if (!mDatabaseManager.setProfileConnectionPolicy(device, BluetoothProfile.LE_AUDIO,
                connectionPolicy)) {
            return false;
        }
        if (connectionPolicy == BluetoothProfile.CONNECTION_POLICY_ALLOWED) {
            connect(device);
        } else if (connectionPolicy == BluetoothProfile.CONNECTION_POLICY_FORBIDDEN) {
            disconnect(device);
        }
        return true;
    }

    /**
     * Get the connection policy of the profile.
     *
     * <p> The connection policy can be any of:
     * {@link BluetoothProfile#CONNECTION_POLICY_ALLOWED},
     * {@link BluetoothProfile#CONNECTION_POLICY_FORBIDDEN},
     * {@link BluetoothProfile#CONNECTION_POLICY_UNKNOWN}
     *
     * @param device Bluetooth device
     * @return connection policy of the device
     * @hide
     */
    public int getConnectionPolicy(BluetoothDevice device) {
        return mDatabaseManager
                .getProfileConnectionPolicy(device, BluetoothProfile.LE_AUDIO);
    }

    /**
     * Get device group id. Devices with same group id belong to same group (i.e left and right
     * earbud)
     * @param device LE Audio capable device
     * @return group id that this device currently belongs to
     */
    public int getGroupId(BluetoothDevice device) {
        if (device == null) {
            return LE_AUDIO_GROUP_ID_INVALID;
        }

        synchronized (mGroupLock) {
            LeAudioDeviceDescriptor descriptor = getDeviceDescriptor(device);
            if (descriptor == null) {
                Log.e(TAG, "getGroupId: No valid descriptor for device: " + device);
                return LE_AUDIO_GROUP_ID_INVALID;
            }

            return descriptor.mGroupId;
        }
    }

    /**
     * Set the user application ccid along with used context type
     * @param userUuid user uuid
     * @param ccid content control id
     * @param contextType context type
     */
    public void setCcidInformation(ParcelUuid userUuid, int ccid, int contextType) {
        /* for the moment we care only for GMCS and GTBS */
        if (userUuid != BluetoothUuid.GENERIC_MEDIA_CONTROL
                && userUuid.getUuid() != TbsGatt.UUID_GTBS) {
            return;
        }
        if (!mLeAudioNativeIsInitialized) {
            Log.e(TAG, "Le Audio not initialized properly.");
            return;
        }
        mLeAudioNativeInterface.setCcidInformation(ccid, contextType);
    }

    /**
     * Set volume for streaming devices
     * @param volume volume to set
     */
    public void setVolume(int volume) {
        if (DBG) {
            Log.d(TAG, "SetVolume " + volume);
        }

        int currentlyActiveGroupId = getActiveGroupId();
        if (currentlyActiveGroupId == LE_AUDIO_GROUP_ID_INVALID) {
            Log.e(TAG, "There is no active group ");
            return;
        }

        if (mVolumeControlService == null) {
            mVolumeControlService = mServiceFactory.getVolumeControlService();
        }
        if (mVolumeControlService != null) {
            mVolumeControlService.setGroupVolume(currentlyActiveGroupId, volume);
        }
    }

    McpService getMcpService() {
        if (mMcpService != null) {
            return mMcpService;
        }

        mMcpService = mServiceFactory.getMcpService();
        return mMcpService;
    }

    /**
     * This function is called when the framework registers
     * a callback with the service for this first time.
     * This is used as an indication that Bluetooth has been enabled.
     * 
     * It is used to authorize all known LeAudio devices in the services
     * which requires that e.g. GMCS
     */
    @VisibleForTesting
    void handleBluetoothEnabled() {
        if (DBG) {
            Log.d(TAG, "handleBluetoothEnabled ");
        }

        mBluetoothEnabled = true;

        synchronized (mGroupLock) {
            if (mDeviceDescriptors.isEmpty()) {
                return;
            }
        }

        McpService mcpService = getMcpService();
        if (mcpService == null) {
            Log.e(TAG, "mcpService not available ");
            return;
        }

        synchronized (mGroupLock) {
            for (Map.Entry<BluetoothDevice, LeAudioDeviceDescriptor> entry
                    : mDeviceDescriptors.entrySet()) {
                if (entry.getValue().mGroupId == LE_AUDIO_GROUP_ID_INVALID) {
                    continue;
                }

                mcpService.setDeviceAuthorized(entry.getKey(), true);
            }
        }
    }

    private LeAudioGroupDescriptor getGroupDescriptor(int groupId) {
        synchronized (mGroupLock) {
            return mGroupDescriptors.get(groupId);
        }
    }

    private LeAudioDeviceDescriptor getDeviceDescriptor(BluetoothDevice device) {
        synchronized (mGroupLock) {
            return mDeviceDescriptors.get(device);
        }
    }

    private void handleGroupNodeAdded(BluetoothDevice device, int groupId) {
        synchronized (mGroupLock) {
            if (DBG) {
                Log.d(TAG, "Device " + device + " added to group " + groupId);
            }

            LeAudioDeviceDescriptor deviceDescriptor = getDeviceDescriptor(device);
            if (deviceDescriptor == null) {
                deviceDescriptor = createDeviceDescriptor(device);
                if (deviceDescriptor == null) {
                    Log.e(TAG, "handleGroupNodeAdded: Can't create descriptor for added from"
                            + " storage device: " + device);
                    return;
                }

                LeAudioStateMachine sm = getOrCreateStateMachine(device);
                if (getOrCreateStateMachine(device) == null) {
                    Log.e(TAG, "Can't get state machine for device: " + device);
                    return;
                }
            }
            deviceDescriptor.mGroupId = groupId;

            LeAudioGroupDescriptor descriptor = mGroupDescriptors.get(groupId);
            if (descriptor == null) {
                mGroupDescriptors.put(groupId, new LeAudioGroupDescriptor());
            }
            notifyGroupNodeAdded(device, groupId);
        }

        if (mBluetoothEnabled) {
            McpService mcpService = getMcpService();
            if (mcpService != null) {
                mcpService.setDeviceAuthorized(device, true);
            }
        }
    }

    private void notifyGroupNodeAdded(BluetoothDevice device, int groupId) {
        if (mVolumeControlService == null) {
            mVolumeControlService = mServiceFactory.getVolumeControlService();
        }
        if (mVolumeControlService != null) {
            mVolumeControlService.handleGroupNodeAdded(groupId, device);
        }

        if (mLeAudioCallbacks != null) {
            int n = mLeAudioCallbacks.beginBroadcast();
            for (int i = 0; i < n; i++) {
                try {
                    mLeAudioCallbacks.getBroadcastItem(i).onGroupNodeAdded(device, groupId);
                } catch (RemoteException e) {
                    continue;
                }
            }
            mLeAudioCallbacks.finishBroadcast();
        }
    }

    private void handleGroupNodeRemoved(BluetoothDevice device, int groupId) {
        if (DBG) {
            Log.d(TAG, "Removing device " + device + " grom group " + groupId);
        }

        synchronized (mGroupLock) {
            LeAudioGroupDescriptor groupDescriptor = getGroupDescriptor(groupId);
            if (DBG) {
                Log.d(TAG, "Lost lead device is " + groupDescriptor.mLostLeadDeviceWhileStreaming);
            }
            if (Objects.equals(device, groupDescriptor.mLostLeadDeviceWhileStreaming)) {
                clearLostDevicesWhileStreaming(groupDescriptor);
            }

            LeAudioDeviceDescriptor deviceDescriptor = getDeviceDescriptor(device);
            if (deviceDescriptor == null) {
                Log.e(TAG, "handleGroupNodeRemoved: No valid descriptor for device: " + device);
                return;
            }
            deviceDescriptor.mGroupId = LE_AUDIO_GROUP_ID_INVALID;

            boolean isGroupEmpty = true;

            for (LeAudioDeviceDescriptor descriptor : mDeviceDescriptors.values()) {
                if (descriptor.mGroupId == groupId) {
                    isGroupEmpty = false;
                    break;
                }
            }

            if (isGroupEmpty) {
                /* Device is currently an active device. Group needs to be inactivated before
                 * removing
                 */
                if (Objects.equals(device, mActiveAudioOutDevice)
                        || Objects.equals(device, mActiveAudioInDevice)) {
                    handleGroupTransitToInactive(groupId);
                }
                mGroupDescriptors.remove(groupId);
            }
            notifyGroupNodeRemoved(device, groupId);
        }

        McpService mcpService = getMcpService();
        if (mcpService != null) {
            mcpService.setDeviceAuthorized(device, false);
        }
    }

    private void notifyGroupNodeRemoved(BluetoothDevice device, int groupId) {
        if (mLeAudioCallbacks != null) {
            int n = mLeAudioCallbacks.beginBroadcast();
            for (int i = 0; i < n; i++) {
                try {
                    mLeAudioCallbacks.getBroadcastItem(i).onGroupNodeRemoved(device, groupId);
                } catch (RemoteException e) {
                    continue;
                }
            }
            mLeAudioCallbacks.finishBroadcast();
        }
    }

    private void notifyGroupStatusChanged(int groupId, int status) {
        if (mLeAudioCallbacks != null) {
            int n = mLeAudioCallbacks.beginBroadcast();
            for (int i = 0; i < n; i++) {
                try {
                    mLeAudioCallbacks.getBroadcastItem(i).onGroupStatusChanged(groupId, status);
                } catch (RemoteException e) {
                    continue;
                }
            }
            mLeAudioCallbacks.finishBroadcast();
        }
    }

    private void notifyUnicastCodecConfigChanged(int groupId, BluetoothLeAudioCodecStatus status) {
        if (mLeAudioCallbacks != null) {
            int n = mLeAudioCallbacks.beginBroadcast();
            for (int i = 0; i < n; i++) {
                try {
                    mLeAudioCallbacks.getBroadcastItem(i).onCodecConfigChanged(groupId, status);
                } catch (RemoteException e) {
                    continue;
                }
            }
            mLeAudioCallbacks.finishBroadcast();
        }
    }

    private void notifyBroadcastStarted(Integer broadcastId, int reason) {
        if (mBroadcastCallbacks != null) {
            int n = mBroadcastCallbacks.beginBroadcast();
            for (int i = 0; i < n; i++) {
                try {
                    mBroadcastCallbacks.getBroadcastItem(i).onBroadcastStarted(reason, broadcastId);
                } catch (RemoteException e) {
                    continue;
                }
            }
            mBroadcastCallbacks.finishBroadcast();
        }
    }

    private void notifyBroadcastStartFailed(Integer broadcastId, int reason) {
        if (mBroadcastCallbacks != null) {
            int n = mBroadcastCallbacks.beginBroadcast();
            for (int i = 0; i < n; i++) {
                try {
                    mBroadcastCallbacks.getBroadcastItem(i).onBroadcastStartFailed(reason);
                } catch (RemoteException e) {
                    continue;
                }
            }
            mBroadcastCallbacks.finishBroadcast();
        }
    }

    private void notifyOnBroadcastStopped(Integer broadcastId, int reason) {
        if (mBroadcastCallbacks != null) {
            int n = mBroadcastCallbacks.beginBroadcast();
            for (int i = 0; i < n; i++) {
                try {
                    mBroadcastCallbacks.getBroadcastItem(i).onBroadcastStopped(reason, broadcastId);
                } catch (RemoteException e) {
                    continue;
                }
            }
            mBroadcastCallbacks.finishBroadcast();
        }
    }

    private void notifyOnBroadcastStopFailed(int reason) {
        if (mBroadcastCallbacks != null) {
            int n = mBroadcastCallbacks.beginBroadcast();
            for (int i = 0; i < n; i++) {
                try {
                    mBroadcastCallbacks.getBroadcastItem(i).onBroadcastStopFailed(reason);
                } catch (RemoteException e) {
                    continue;
                }
            }
            mBroadcastCallbacks.finishBroadcast();
        }
    }

    private void notifyPlaybackStarted(Integer broadcastId, int reason) {
        if (mBroadcastCallbacks != null) {
            int n = mBroadcastCallbacks.beginBroadcast();
            for (int i = 0; i < n; i++) {
                try {
                    mBroadcastCallbacks.getBroadcastItem(i).onPlaybackStarted(reason, broadcastId);
                } catch (RemoteException e) {
                    continue;
                }
            }
            mBroadcastCallbacks.finishBroadcast();
        }
    }

    private void notifyPlaybackStopped(Integer broadcastId, int reason) {
        if (mBroadcastCallbacks != null) {
            int n = mBroadcastCallbacks.beginBroadcast();
            for (int i = 0; i < n; i++) {
                try {
                    mBroadcastCallbacks.getBroadcastItem(i).onPlaybackStopped(reason, broadcastId);
                } catch (RemoteException e) {
                    continue;
                }
            }
            mBroadcastCallbacks.finishBroadcast();
        }
    }

    private void notifyBroadcastUpdated(int broadcastId, int reason) {
        if (mBroadcastCallbacks != null) {
            int n = mBroadcastCallbacks.beginBroadcast();
            for (int i = 0; i < n; i++) {
                try {
                    mBroadcastCallbacks.getBroadcastItem(i).onBroadcastUpdated(reason, broadcastId);
                } catch (RemoteException e) {
                    continue;
                }
            }
            mBroadcastCallbacks.finishBroadcast();
        }
    }

    private void notifyBroadcastUpdateFailed(int broadcastId, int reason) {
        if (mBroadcastCallbacks != null) {
            int n = mBroadcastCallbacks.beginBroadcast();
            for (int i = 0; i < n; i++) {
                try {
                    mBroadcastCallbacks.getBroadcastItem(i)
                            .onBroadcastUpdateFailed(reason, broadcastId);
                } catch (RemoteException e) {
                    continue;
                }
            }
            mBroadcastCallbacks.finishBroadcast();
        }
    }

    private void notifyBroadcastMetadataChanged(int broadcastId,
            BluetoothLeBroadcastMetadata metadata) {
        if (mBroadcastCallbacks != null) {
            int n = mBroadcastCallbacks.beginBroadcast();
            for (int i = 0; i < n; i++) {
                try {
                    mBroadcastCallbacks.getBroadcastItem(i)
                            .onBroadcastMetadataChanged(broadcastId, metadata);
                } catch (RemoteException e) {
                    continue;
                }
            }
            mBroadcastCallbacks.finishBroadcast();
        }
    }

    /**
     * Gets the current codec status (configuration and capability).
     *
     * @param groupId the group id
     * @return the current codec status
     * @hide
     */
    public BluetoothLeAudioCodecStatus getCodecStatus(int groupId) {
        if (DBG) {
            Log.d(TAG, "getCodecStatus(" + groupId + ")");
        }
        LeAudioGroupDescriptor descriptor = getGroupDescriptor(groupId);
        if (descriptor != null) {
            return descriptor.mCodecStatus;
        }
        return null;
    }

    /**
     * Sets the codec configuration preference.
     *
     * @param groupId the group id
     * @param inputCodecConfig the input codec configuration preference
     * @param outputCodecConfig the output codec configuration preference
     * @hide
     */
    public void setCodecConfigPreference(int groupId,
            BluetoothLeAudioCodecConfig inputCodecConfig,
            BluetoothLeAudioCodecConfig outputCodecConfig) {
        if (DBG) {
            Log.d(TAG, "setCodecConfigPreference(" + groupId + "): "
                    + Objects.toString(inputCodecConfig)
                    + Objects.toString(outputCodecConfig));
        }
        LeAudioGroupDescriptor descriptor = getGroupDescriptor(groupId);
        if (descriptor == null) {
            Log.e(TAG, "setCodecConfigPreference: Invalid groupId, " + groupId);
            return;
        }

        if (inputCodecConfig == null || outputCodecConfig == null) {
            Log.e(TAG, "setCodecConfigPreference: Codec config can't be null");
            return;
        }

        /* We support different configuration for input and output but codec type
         * shall be same */
        if (inputCodecConfig.getCodecType() != outputCodecConfig.getCodecType()) {
            Log.e(TAG, "setCodecConfigPreference: Input codec type: "
                    + inputCodecConfig.getCodecType()
                    + "does not match output codec type: " + outputCodecConfig.getCodecType());
            return;
        }

        if (descriptor.mCodecStatus == null) {
            Log.e(TAG, "setCodecConfigPreference: Codec status is null");
            return;
        }

        if (!mLeAudioNativeIsInitialized) {
            Log.e(TAG, "Le Audio not initialized properly.");
            return;
        }

        mLeAudioNativeInterface.setCodecConfigPreference(
                groupId, inputCodecConfig, outputCodecConfig);
    }

    /**
     * Binder object: must be a static class or memory leak may occur
     */
    @VisibleForTesting
    static class BluetoothLeAudioBinder extends IBluetoothLeAudio.Stub
            implements IProfileServiceBinder {
        private LeAudioService mService;

        @RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
        private LeAudioService getService(AttributionSource source) {
            if (Utils.isInstrumentationTestMode()) {
                return mService;
            }
            if (!Utils.checkServiceAvailable(mService, TAG)
                    || !Utils.checkCallerIsSystemOrActiveOrManagedUser(mService, TAG)
                    || !Utils.checkConnectPermissionForDataDelivery(mService, source, TAG)) {
                return null;
            }
            return mService;
        }

        BluetoothLeAudioBinder(LeAudioService svc) {
            mService = svc;
        }

        @Override
        public void cleanup() {
            mService = null;
        }

        @Override
        public void connect(BluetoothDevice device, AttributionSource source,
                SynchronousResultReceiver receiver) {
            try {
                Objects.requireNonNull(device, "device cannot be null");
                Objects.requireNonNull(source, "source cannot be null");
                Objects.requireNonNull(receiver, "receiver cannot be null");

                LeAudioService service = getService(source);
                boolean result = false;
                if (service != null) {
                    result = service.connect(device);
                }
                receiver.send(result);
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }

        @Override
        public void disconnect(BluetoothDevice device, AttributionSource source,
                SynchronousResultReceiver receiver) {
            try {
                Objects.requireNonNull(device, "device cannot be null");
                Objects.requireNonNull(source, "source cannot be null");
                Objects.requireNonNull(receiver, "receiver cannot be null");

                LeAudioService service = getService(source);
                boolean result = false;
                if (service != null) {
                    result = service.disconnect(device);
                }
                receiver.send(result);
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }

        @Override
        public void getConnectedDevices(AttributionSource source,
                SynchronousResultReceiver receiver) {
            try {
                Objects.requireNonNull(source, "source cannot be null");
                Objects.requireNonNull(receiver, "receiver cannot be null");

                LeAudioService service = getService(source);
                List<BluetoothDevice> result = new ArrayList<>(0);
                if (service != null) {
                    result = service.getConnectedDevices();
                }
                receiver.send(result);
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }

        @Override
        public void getConnectedGroupLeadDevice(int groupId, AttributionSource source,
                SynchronousResultReceiver receiver) {
            try {
                Objects.requireNonNull(source, "source cannot be null");
                Objects.requireNonNull(receiver, "receiver cannot be null");

                LeAudioService service = getService(source);
                BluetoothDevice result = null;
                if (service != null) {
                    result = service.getConnectedGroupLeadDevice(groupId);
                }
                receiver.send(result);
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }

        @Override
        public void getDevicesMatchingConnectionStates(int[] states,
                AttributionSource source, SynchronousResultReceiver receiver) {
            try {
                Objects.requireNonNull(source, "source cannot be null");
                Objects.requireNonNull(receiver, "receiver cannot be null");

                LeAudioService service = getService(source);
                List<BluetoothDevice> result = new ArrayList<>(0);
                if (service != null) {
                    result = service.getDevicesMatchingConnectionStates(states);
                }
                receiver.send(result);
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }

        @Override
        public void getConnectionState(BluetoothDevice device, AttributionSource source,
                SynchronousResultReceiver receiver) {
            try {
                Objects.requireNonNull(device, "device cannot be null");
                Objects.requireNonNull(source, "source cannot be null");
                Objects.requireNonNull(receiver, "receiver cannot be null");

                LeAudioService service = getService(source);
                int result = BluetoothProfile.STATE_DISCONNECTED;
                if (service != null) {
                    result = service.getConnectionState(device);
                }
                receiver.send(result);
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }

        @Override
        public void setActiveDevice(BluetoothDevice device, AttributionSource source,
                SynchronousResultReceiver receiver) {
            try {
                Objects.requireNonNull(device, "device cannot be null");
                Objects.requireNonNull(source, "source cannot be null");
                Objects.requireNonNull(receiver, "receiver cannot be null");

                LeAudioService service = getService(source);
                boolean result = false;
                if (service != null) {
                    result = service.setActiveDevice(device);
                }
                receiver.send(result);
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }

        @Override
        public void getActiveDevices(AttributionSource source, SynchronousResultReceiver receiver) {
            try {
                Objects.requireNonNull(source, "source cannot be null");
                Objects.requireNonNull(receiver, "receiver cannot be null");

                LeAudioService service = getService(source);
                List<BluetoothDevice> result = new ArrayList<>();
                if (service != null) {
                    result = service.getActiveDevices();
                }
                receiver.send(result);
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }

        @Override
        public void getAudioLocation(BluetoothDevice device, AttributionSource source,
                SynchronousResultReceiver receiver) {
            try {
                Objects.requireNonNull(device, "device cannot be null");
                Objects.requireNonNull(source, "source cannot be null");
                Objects.requireNonNull(receiver, "receiver cannot be null");

                LeAudioService service = getService(source);
                int result = BluetoothLeAudio.AUDIO_LOCATION_INVALID;
                if (service != null) {
                    enforceBluetoothPrivilegedPermission(service);
                    result = service.getAudioLocation(device);
                }
                receiver.send(result);
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }

        @Override
        public void setConnectionPolicy(BluetoothDevice device, int connectionPolicy,
                AttributionSource source, SynchronousResultReceiver receiver) {
            Objects.requireNonNull(device, "device cannot be null");
            Objects.requireNonNull(source, "source cannot be null");
            Objects.requireNonNull(receiver, "receiver cannot be null");

            try {
                LeAudioService service = getService(source);
                boolean result = false;
                if (service != null) {
                    enforceBluetoothPrivilegedPermission(service);
                    result = service.setConnectionPolicy(device, connectionPolicy);
                }
                receiver.send(result);
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }

        @Override
        public void getConnectionPolicy(BluetoothDevice device, AttributionSource source,
                SynchronousResultReceiver receiver) {
            try {
                Objects.requireNonNull(device, "device cannot be null");
                Objects.requireNonNull(source, "source cannot be null");
                Objects.requireNonNull(receiver, "receiver cannot be null");

                LeAudioService service = getService(source);
                int result = BluetoothProfile.CONNECTION_POLICY_UNKNOWN;
                if (service == null) {
                    throw new IllegalStateException("service is null");
                }
                enforceBluetoothPrivilegedPermission(service);
                result = service.getConnectionPolicy(device);
                receiver.send(result);
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }

        @Override
        public void setCcidInformation(ParcelUuid userUuid, int ccid, int contextType,
                AttributionSource source,
                SynchronousResultReceiver receiver) {
            try {
                Objects.requireNonNull(userUuid, "userUuid cannot be null");
                Objects.requireNonNull(source, "source cannot be null");
                Objects.requireNonNull(receiver, "receiver cannot be null");

                LeAudioService service = getService(source);
                if (service == null) {
                    throw new IllegalStateException("service is null");
                }
                enforceBluetoothPrivilegedPermission(service);
                service.setCcidInformation(userUuid, ccid, contextType);
                receiver.send(null);
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }

        @Override
        public void getGroupId(BluetoothDevice device, AttributionSource source,
                SynchronousResultReceiver receiver) {
            try {
                Objects.requireNonNull(device, "device cannot be null");
                Objects.requireNonNull(source, "source cannot be null");
                Objects.requireNonNull(receiver, "receiver cannot be null");

                LeAudioService service = getService(source);
                int result = LE_AUDIO_GROUP_ID_INVALID;
                if (service == null) {
                    throw new IllegalStateException("service is null");
                }
                result = service.getGroupId(device);
                receiver.send(result);
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }

        @Override
        public void groupAddNode(int group_id, BluetoothDevice device,
                AttributionSource source, SynchronousResultReceiver receiver) {
            try {
                Objects.requireNonNull(device, "device cannot be null");
                Objects.requireNonNull(source, "source cannot be null");
                Objects.requireNonNull(receiver, "receiver cannot be null");

                LeAudioService service = getService(source);
                boolean result = false;
                if (service == null) {
                    throw new IllegalStateException("service is null");
                }
                enforceBluetoothPrivilegedPermission(service);
                result = service.groupAddNode(group_id, device);
                receiver.send(result);
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }

        @Override
        public void setInCall(boolean inCall, AttributionSource source,
                SynchronousResultReceiver receiver) {
            try {
                Objects.requireNonNull(source, "source cannot be null");
                Objects.requireNonNull(receiver, "receiver cannot be null");

                LeAudioService service = getService(source);
                if (service == null) {
                    throw new IllegalStateException("service is null");
                }
                enforceBluetoothPrivilegedPermission(service);
                service.setInCall(inCall);
                receiver.send(null);
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }

        @Override
        public void setInactiveForHfpHandover(BluetoothDevice hfpHandoverDevice,
                AttributionSource source,
                SynchronousResultReceiver receiver) {
            try {
                Objects.requireNonNull(source, "source cannot be null");
                Objects.requireNonNull(receiver, "receiver cannot be null");

                LeAudioService service = getService(source);
                if (service == null) {
                    throw new IllegalStateException("service is null");
                }
                enforceBluetoothPrivilegedPermission(service);
                service.setInactiveForHfpHandover(hfpHandoverDevice);
                receiver.send(null);
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }

        @Override
        public void groupRemoveNode(int groupId, BluetoothDevice device,
                AttributionSource source, SynchronousResultReceiver receiver) {
            try {
                Objects.requireNonNull(device, "device cannot be null");
                Objects.requireNonNull(source, "source cannot be null");
                Objects.requireNonNull(receiver, "receiver cannot be null");

                LeAudioService service = getService(source);
                boolean result = false;
                if (service == null) {
                    throw new IllegalStateException("service is null");
                }
                enforceBluetoothPrivilegedPermission(service);
                result = service.groupRemoveNode(groupId, device);
                receiver.send(result);
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }

        @Override
        public void setVolume(int volume, AttributionSource source,
                SynchronousResultReceiver receiver) {
            try {
                Objects.requireNonNull(source, "source cannot be null");
                Objects.requireNonNull(receiver, "receiver cannot be null");

                LeAudioService service = getService(source);
                if (service == null) {
                    throw new IllegalStateException("service is null");
                }
                enforceBluetoothPrivilegedPermission(service);
                service.setVolume(volume);
                receiver.send(null);
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }

        @Override
        public void registerCallback(IBluetoothLeAudioCallback callback,
                AttributionSource source, SynchronousResultReceiver receiver) {
            try {
                Objects.requireNonNull(callback, "callback cannot be null");
                Objects.requireNonNull(source, "source cannot be null");
                Objects.requireNonNull(receiver, "receiver cannot be null");

                LeAudioService service = getService(source);
                if ((service == null) || (service.mLeAudioCallbacks == null)) {
                    throw new IllegalStateException("Service is unavailable: " + service);
                }

                enforceBluetoothPrivilegedPermission(service);
                service.mLeAudioCallbacks.register(callback);
                if (!service.mBluetoothEnabled) {
                    service.handleBluetoothEnabled();
                }
                receiver.send(null);
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }

        @Override
        public void unregisterCallback(IBluetoothLeAudioCallback callback,
                AttributionSource source, SynchronousResultReceiver receiver) {
            try {
                Objects.requireNonNull(callback, "callback cannot be null");
                Objects.requireNonNull(source, "source cannot be null");
                Objects.requireNonNull(receiver, "receiver cannot be null");

                LeAudioService service = getService(source);
                if ((service == null) || (service.mLeAudioCallbacks == null)) {
                    throw new IllegalStateException("Service is unavailable");
                }

                enforceBluetoothPrivilegedPermission(service);

                service.mLeAudioCallbacks.unregister(callback);
                receiver.send(null);
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }

        @Override
        public void registerLeBroadcastCallback(IBluetoothLeBroadcastCallback callback,
                AttributionSource source, SynchronousResultReceiver receiver) {
            try {
                Objects.requireNonNull(callback, "callback cannot be null");
                Objects.requireNonNull(source, "source cannot be null");
                Objects.requireNonNull(receiver, "receiver cannot be null");

                LeAudioService service = getService(source);
                if ((service == null) || (service.mBroadcastCallbacks == null)) {
                    throw new IllegalStateException("Service is unavailable");
                }

                enforceBluetoothPrivilegedPermission(service);

                service.mBroadcastCallbacks.register(callback);
                receiver.send(null);
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }

        @Override
        public void unregisterLeBroadcastCallback(IBluetoothLeBroadcastCallback callback,
                AttributionSource source, SynchronousResultReceiver receiver) {
            try {
                LeAudioService service = getService(source);
                if ((service == null) || (service.mBroadcastCallbacks == null)) {
                    throw new IllegalStateException("Service is unavailable");
                }

                enforceBluetoothPrivilegedPermission(service);
                Objects.requireNonNull(callback, "callback cannot be null");
                Objects.requireNonNull(source, "source cannot be null");
                Objects.requireNonNull(receiver, "receiver cannot be null");

                service.mBroadcastCallbacks.unregister(callback);
                receiver.send(null);
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }

        @Override
        public void startBroadcast(BluetoothLeAudioContentMetadata contentMetadata,
                byte[] broadcastCode, AttributionSource source) {
            LeAudioService service = getService(source);
            if (service != null) {
                enforceBluetoothPrivilegedPermission(service);
                service.createBroadcast(contentMetadata, broadcastCode);
            }
        }

        @Override
        public void stopBroadcast(int broadcastId, AttributionSource source) {
            LeAudioService service = getService(source);
            if (service != null) {
                enforceBluetoothPrivilegedPermission(service);
                service.stopBroadcast(broadcastId);
            }
        }

        @Override
        public void updateBroadcast(int broadcastId,
                BluetoothLeAudioContentMetadata contentMetadata, AttributionSource source) {
            LeAudioService service = getService(source);
            if (service != null) {
                enforceBluetoothPrivilegedPermission(service);
                service.updateBroadcast(broadcastId, contentMetadata);
            }
        }

        @Override
        public void isPlaying(int broadcastId, AttributionSource source,
                SynchronousResultReceiver receiver) {
            try {
                boolean result = false;
                LeAudioService service = getService(source);
                if (service != null) {
                    enforceBluetoothPrivilegedPermission(service);
                    result = service.isPlaying(broadcastId);
                }
                receiver.send(result);
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }

        @Override
        public void getAllBroadcastMetadata(AttributionSource source,
                SynchronousResultReceiver receiver) {
            try {
                List<BluetoothLeBroadcastMetadata> result = new ArrayList<>();
                LeAudioService service = getService(source);
                if (service != null) {
                    enforceBluetoothPrivilegedPermission(service);
                    result = service.getAllBroadcastMetadata();
                }
                receiver.send(result);
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }

        @Override
        public void getMaximumNumberOfBroadcasts(AttributionSource source,
                SynchronousResultReceiver receiver) {
            try {
                int result = 0;
                LeAudioService service = getService(source);
                if (service != null) {
                    enforceBluetoothPrivilegedPermission(service);
                    result = service.getMaximumNumberOfBroadcasts();
                }
                receiver.send(result);
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }

        @Override
        public void getCodecStatus(int groupId,
                AttributionSource source, SynchronousResultReceiver receiver) {
            try {
                LeAudioService service = getService(source);
                BluetoothLeAudioCodecStatus codecStatus = null;
                if (service != null) {
                    enforceBluetoothPrivilegedPermission(service);
                    codecStatus = service.getCodecStatus(groupId);
                }
                receiver.send(codecStatus);
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }

        @Override
        public void setCodecConfigPreference(int groupId,
                BluetoothLeAudioCodecConfig inputCodecConfig,
                BluetoothLeAudioCodecConfig outputCodecConfig,
                AttributionSource source) {
            LeAudioService service = getService(source);
            if (service == null) {
                return;
            }

            enforceBluetoothPrivilegedPermission(service);
            service.setCodecConfigPreference(groupId, inputCodecConfig, outputCodecConfig);
        }
    }

    @Override
    public void dump(StringBuilder sb) {
        super.dump(sb);
        ProfileService.println(sb, "Active Groups information: ");
        ProfileService.println(sb, "  currentlyActiveGroupId: " + getActiveGroupId());
        ProfileService.println(sb, "  mActiveAudioOutDevice: " + mActiveAudioOutDevice);
        ProfileService.println(sb, "  mActiveAudioInDevice: " + mActiveAudioInDevice);
        ProfileService.println(sb, "  mHfpHandoverDevice:" + mHfpHandoverDevice);

        for (Map.Entry<BluetoothDevice, LeAudioDeviceDescriptor> entry
                : mDeviceDescriptors.entrySet()) {
            LeAudioDeviceDescriptor descriptor = entry.getValue();

            descriptor.mStateMachine.dump(sb);
            ProfileService.println(sb, "    mGroupId: " + descriptor.mGroupId);
            ProfileService.println(sb, "    mSinkAudioLocation: " + descriptor.mSinkAudioLocation);
            ProfileService.println(sb, "    mDirection: " + descriptor.mDirection);
        }

        for (Map.Entry<Integer, LeAudioGroupDescriptor> entry : mGroupDescriptors.entrySet()) {
            LeAudioGroupDescriptor descriptor = entry.getValue();
            Integer groupId = entry.getKey();
            ProfileService.println(sb, "  Group: " + groupId);
            ProfileService.println(sb, "    isActive: " + descriptor.mIsActive);
            ProfileService.println(sb, "    isConnected: " + descriptor.mIsConnected);
            ProfileService.println(sb, "    mDirection: " + descriptor.mDirection);
            ProfileService.println(sb, "    group lead: " + getConnectedGroupLeadDevice(groupId));
            ProfileService.println(sb, "    first device: " + getFirstDeviceFromGroup(groupId));
            ProfileService.println(sb, "    lost lead device: "
                    + descriptor.mLostLeadDeviceWhileStreaming);
        }
    }
}
