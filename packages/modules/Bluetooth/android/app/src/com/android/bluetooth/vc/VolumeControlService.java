/*
 * Copyright 2021 HIMSA II K/S - www.himsa.com.
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

package com.android.bluetooth.vc;

import static android.Manifest.permission.BLUETOOTH_CONNECT;

import static com.android.bluetooth.Utils.enforceBluetoothPrivilegedPermission;

import android.annotation.RequiresPermission;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothUuid;
import android.bluetooth.BluetoothVolumeControl;
import android.bluetooth.IBluetoothCsipSetCoordinator;
import android.bluetooth.IBluetoothLeAudio;
import android.bluetooth.IBluetoothVolumeControl;
import android.bluetooth.IBluetoothVolumeControlCallback;
import android.content.AttributionSource;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.os.HandlerThread;
import android.os.ParcelUuid;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.sysprop.BluetoothProperties;
import android.util.Log;

import com.android.bluetooth.Utils;
import com.android.bluetooth.btservice.AdapterService;
import com.android.bluetooth.btservice.ProfileService;
import com.android.bluetooth.btservice.ServiceFactory;
import com.android.bluetooth.btservice.storage.DatabaseManager;
import com.android.bluetooth.csip.CsipSetCoordinatorService;
import com.android.bluetooth.le_audio.LeAudioService;
import com.android.internal.annotations.VisibleForTesting;
import com.android.modules.utils.SynchronousResultReceiver;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class VolumeControlService extends ProfileService {
    private static final boolean DBG = false;
    private static final String TAG = "VolumeControlService";

    // Timeout for state machine thread join, to prevent potential ANR.
    private static final int SM_THREAD_JOIN_TIMEOUT_MS = 1000;

    // Upper limit of all VolumeControl devices: Bonded or Connected
    private static final int MAX_VC_STATE_MACHINES = 10;
    private static final int LE_AUDIO_MAX_VOL = 255;
    private static final int LE_AUDIO_MIN_VOL = 0;

    private static VolumeControlService sVolumeControlService;

    private AdapterService mAdapterService;
    private DatabaseManager mDatabaseManager;
    private HandlerThread mStateMachinesThread;
    private BluetoothDevice mPreviousAudioDevice;

    @VisibleForTesting
    RemoteCallbackList<IBluetoothVolumeControlCallback> mCallbacks;

    @VisibleForTesting
    static class VolumeControlOffsetDescriptor {
        Map<Integer, Descriptor> mVolumeOffsets;

        private class Descriptor {
            Descriptor() {
                mValue = 0;
                mLocation = 0;
                mDescription = null;
            }
            int mValue;
            int mLocation;
            String mDescription;
        };

        VolumeControlOffsetDescriptor() {
            mVolumeOffsets = new HashMap<>();
        }

        int size() {
            return mVolumeOffsets.size();
        }

        void add(int id) {
            Descriptor d = mVolumeOffsets.get(id);
            if (d == null) {
                mVolumeOffsets.put(id, new Descriptor());
            }
        }

        boolean setValue(int id, int value) {
            Descriptor d = mVolumeOffsets.get(id);
            if (d == null) {
                return false;
            }
            d.mValue = value;
            return true;
        }

        int getValue(int id) {
            Descriptor d = mVolumeOffsets.get(id);
            if (d == null) {
                return 0;
            }
            return d.mValue;
        }

        boolean setDescription(int id, String desc) {
            Descriptor d = mVolumeOffsets.get(id);
            if (d == null) {
                return false;
            }
            d.mDescription = desc;
            return true;
        }

        String getDescription(int id) {
            Descriptor d = mVolumeOffsets.get(id);
            if (d == null) {
                return null;
            }
            return d.mDescription;
        }

        boolean setLocation(int id, int location) {
            Descriptor d = mVolumeOffsets.get(id);
            if (d == null) {
                return false;
            }
            d.mLocation = location;
            return true;
        }

        int getLocation(int id) {
            Descriptor d = mVolumeOffsets.get(id);
            if (d == null) {
                return 0;
            }
            return d.mLocation;
        }

        void remove(int id) {
            mVolumeOffsets.remove(id);
        }

        void clear() {
            mVolumeOffsets.clear();
        }

        void dump(StringBuilder sb) {
            for (Map.Entry<Integer, Descriptor> entry : mVolumeOffsets.entrySet()) {
                Descriptor descriptor = entry.getValue();
                Integer id = entry.getKey();
                ProfileService.println(sb, "        Id: " + id);
                ProfileService.println(sb, "        value: " + descriptor.mValue);
                ProfileService.println(sb, "        location: " + descriptor.mLocation);
                ProfileService.println(sb, "        description: " + descriptor.mDescription);
            }
        }
    }

    @VisibleForTesting
    VolumeControlNativeInterface mVolumeControlNativeInterface;
    @VisibleForTesting
    AudioManager mAudioManager;

    private final Map<BluetoothDevice, VolumeControlStateMachine> mStateMachines = new HashMap<>();
    private final Map<BluetoothDevice, VolumeControlOffsetDescriptor> mAudioOffsets =
                                                                            new HashMap<>();
    private final Map<Integer, Integer> mGroupVolumeCache = new HashMap<>();

    private BroadcastReceiver mBondStateChangedReceiver;
    private BroadcastReceiver mConnectionStateChangedReceiver;

    @VisibleForTesting
    ServiceFactory mFactory = new ServiceFactory();

    public static boolean isEnabled() {
        return BluetoothProperties.isProfileVcpControllerEnabled().orElse(false);
    }

    @Override
    protected IProfileServiceBinder initBinder() {
        return new BluetoothVolumeControlBinder(this);
    }

    @Override
    protected void create() {
        if (DBG) {
            Log.d(TAG, "create()");
        }
    }

    @Override
    protected boolean start() {
        if (DBG) {
            Log.d(TAG, "start()");
        }
        if (sVolumeControlService != null) {
            throw new IllegalStateException("start() called twice");
        }

        // Get AdapterService, VolumeControlNativeInterface, DatabaseManager, AudioManager.
        // None of them can be null.
        mAdapterService = Objects.requireNonNull(AdapterService.getAdapterService(),
                "AdapterService cannot be null when VolumeControlService starts");
        mDatabaseManager = Objects.requireNonNull(mAdapterService.getDatabase(),
                "DatabaseManager cannot be null when VolumeControlService starts");
        mVolumeControlNativeInterface = Objects.requireNonNull(
                VolumeControlNativeInterface.getInstance(),
                "VolumeControlNativeInterface cannot be null when VolumeControlService starts");
        mAudioManager =  getSystemService(AudioManager.class);
        Objects.requireNonNull(mAudioManager,
                "AudioManager cannot be null when VolumeControlService starts");

        // Start handler thread for state machines
        mStateMachines.clear();
        mStateMachinesThread = new HandlerThread("VolumeControlService.StateMachines");
        mStateMachinesThread.start();

        // Setup broadcast receivers
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        mBondStateChangedReceiver = new BondStateChangedReceiver();
        registerReceiver(mBondStateChangedReceiver, filter);
        filter = new IntentFilter();
        filter.addAction(BluetoothVolumeControl.ACTION_CONNECTION_STATE_CHANGED);
        mConnectionStateChangedReceiver = new ConnectionStateChangedReceiver();
        registerReceiver(mConnectionStateChangedReceiver, filter);

        mAudioOffsets.clear();
        mGroupVolumeCache.clear();
        mCallbacks = new RemoteCallbackList<IBluetoothVolumeControlCallback>();

        // Mark service as started
        setVolumeControlService(this);

        // Initialize native interface
        mVolumeControlNativeInterface.init();

        return true;
    }

    @Override
    protected boolean stop() {
        if (DBG) {
            Log.d(TAG, "stop()");
        }
        if (sVolumeControlService == null) {
            Log.w(TAG, "stop() called before start()");
            return true;
        }

        // Mark service as stopped
        setVolumeControlService(null);

        // Unregister broadcast receivers
        unregisterReceiver(mBondStateChangedReceiver);
        mBondStateChangedReceiver = null;
        unregisterReceiver(mConnectionStateChangedReceiver);
        mConnectionStateChangedReceiver = null;

        // Destroy state machines and stop handler thread
        synchronized (mStateMachines) {
            for (VolumeControlStateMachine sm : mStateMachines.values()) {
                sm.doQuit();
                sm.cleanup();
            }
            mStateMachines.clear();
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

        // Cleanup native interface
        mVolumeControlNativeInterface.cleanup();
        mVolumeControlNativeInterface = null;

        mAudioOffsets.clear();
        mGroupVolumeCache.clear();

        // Clear AdapterService, VolumeControlNativeInterface
        mAudioManager = null;
        mVolumeControlNativeInterface = null;
        mAdapterService = null;

        if (mCallbacks != null) {
            mCallbacks.kill();
        }

        return true;
    }

    @Override
    protected void cleanup() {
        if (DBG) {
            Log.d(TAG, "cleanup()");
        }
    }

    /**
     * Get the VolumeControlService instance
     * @return VolumeControlService instance
     */
    public static synchronized VolumeControlService getVolumeControlService() {
        if (sVolumeControlService == null) {
            Log.w(TAG, "getVolumeControlService(): service is NULL");
            return null;
        }

        if (!sVolumeControlService.isAvailable()) {
            Log.w(TAG, "getVolumeControlService(): service is not available");
            return null;
        }
        return sVolumeControlService;
    }

    @VisibleForTesting
    static synchronized void setVolumeControlService(VolumeControlService instance) {
        if (DBG) {
            Log.d(TAG, "setVolumeControlService(): set to: " + instance);
        }
        sVolumeControlService = instance;
    }

    @RequiresPermission(android.Manifest.permission.BLUETOOTH_PRIVILEGED)
    public boolean connect(BluetoothDevice device) {
        enforceCallingOrSelfPermission(BLUETOOTH_PRIVILEGED,
                "Need BLUETOOTH_PRIVILEGED permission");
        if (DBG) {
            Log.d(TAG, "connect(): " + device);
        }
        if (device == null) {
            return false;
        }

        if (getConnectionPolicy(device) == BluetoothProfile.CONNECTION_POLICY_FORBIDDEN) {
            return false;
        }
        ParcelUuid[] featureUuids = mAdapterService.getRemoteUuids(device);
        if (!Utils.arrayContains(featureUuids, BluetoothUuid.VOLUME_CONTROL)) {
            Log.e(TAG, "Cannot connect to " + device
                    + " : Remote does not have Volume Control UUID");
            return false;
        }


        synchronized (mStateMachines) {
            VolumeControlStateMachine smConnect = getOrCreateStateMachine(device);
            if (smConnect == null) {
                Log.e(TAG, "Cannot connect to " + device + " : no state machine");
            }
            smConnect.sendMessage(VolumeControlStateMachine.CONNECT);
        }

        return true;
    }

    @RequiresPermission(android.Manifest.permission.BLUETOOTH_PRIVILEGED)
    public boolean disconnect(BluetoothDevice device) {
        enforceCallingOrSelfPermission(BLUETOOTH_PRIVILEGED,
                "Need BLUETOOTH_PRIVILEGED permission");
        if (DBG) {
            Log.d(TAG, "disconnect(): " + device);
        }
        if (device == null) {
            return false;
        }
        synchronized (mStateMachines) {
            VolumeControlStateMachine sm = getOrCreateStateMachine(device);
            if (sm != null) {
                sm.sendMessage(VolumeControlStateMachine.DISCONNECT);
            }
        }

        return true;
    }

    @RequiresPermission(android.Manifest.permission.BLUETOOTH_PRIVILEGED)
    public List<BluetoothDevice> getConnectedDevices() {
        enforceCallingOrSelfPermission(BLUETOOTH_PRIVILEGED,
                "Need BLUETOOTH_PRIVILEGED permission");
        synchronized (mStateMachines) {
            List<BluetoothDevice> devices = new ArrayList<>();
            for (VolumeControlStateMachine sm : mStateMachines.values()) {
                if (sm.isConnected()) {
                    devices.add(sm.getDevice());
                }
            }
            return devices;
        }
    }

    /**
     * Check whether can connect to a peer device.
     * The check considers a number of factors during the evaluation.
     *
     * @param device the peer device to connect to
     * @return true if connection is allowed, otherwise false
     */
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
    public boolean okToConnect(BluetoothDevice device) {
        /* Make sure device is valid */
        if (device == null) {
            Log.e(TAG, "okToConnect: Invalid device");
            return false;
        }
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

    @RequiresPermission(android.Manifest.permission.BLUETOOTH_PRIVILEGED)
    List<BluetoothDevice> getDevicesMatchingConnectionStates(int[] states) {
        enforceCallingOrSelfPermission(BLUETOOTH_PRIVILEGED,
                "Need BLUETOOTH_PRIVILEGED permission");
        ArrayList<BluetoothDevice> devices = new ArrayList<>();
        if (states == null) {
            return devices;
        }
        final BluetoothDevice[] bondedDevices = mAdapterService.getBondedDevices();
        if (bondedDevices == null) {
            return devices;
        }
        synchronized (mStateMachines) {
            for (BluetoothDevice device : bondedDevices) {
                final ParcelUuid[] featureUuids = device.getUuids();
                if (!Utils.arrayContains(featureUuids, BluetoothUuid.VOLUME_CONTROL)) {
                    continue;
                }
                int connectionState = BluetoothProfile.STATE_DISCONNECTED;
                VolumeControlStateMachine sm = mStateMachines.get(device);
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
        synchronized (mStateMachines) {
            for (VolumeControlStateMachine sm : mStateMachines.values()) {
                devices.add(sm.getDevice());
            }
            return devices;
        }
    }

    @RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
    public int getConnectionState(BluetoothDevice device) {
        enforceCallingOrSelfPermission(BLUETOOTH_CONNECT,
                "Need BLUETOOTH_CONNECT permission");
        synchronized (mStateMachines) {
            VolumeControlStateMachine sm = mStateMachines.get(device);
            if (sm == null) {
                return BluetoothProfile.STATE_DISCONNECTED;
            }
            return sm.getConnectionState();
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
        mDatabaseManager.setProfileConnectionPolicy(device, BluetoothProfile.VOLUME_CONTROL,
                        connectionPolicy);
        if (connectionPolicy == BluetoothProfile.CONNECTION_POLICY_ALLOWED) {
            connect(device);
        } else if (connectionPolicy == BluetoothProfile.CONNECTION_POLICY_FORBIDDEN) {
            disconnect(device);
        }
        return true;
    }

    @RequiresPermission(android.Manifest.permission.BLUETOOTH_PRIVILEGED)
    public int getConnectionPolicy(BluetoothDevice device) {
        enforceCallingOrSelfPermission(BLUETOOTH_PRIVILEGED,
                "Need BLUETOOTH_PRIVILEGED permission");
        return mDatabaseManager.getProfileConnectionPolicy(device, BluetoothProfile.VOLUME_CONTROL);
    }

    boolean isVolumeOffsetAvailable(BluetoothDevice device) {
        VolumeControlOffsetDescriptor offsets = mAudioOffsets.get(device);
        if (offsets == null) {
            Log.i(TAG, " There is no offset service for device: " + device);
            return false;
        }
        Log.i(TAG, " Offset service available for device: " + device);
        return true;
    }

    void setVolumeOffset(BluetoothDevice device, int volumeOffset) {
        VolumeControlOffsetDescriptor offsets = mAudioOffsets.get(device);
        if (offsets == null) {
            Log.e(TAG, " There is no offset service for device: " + device);
            return;
        }

        /* Use first offset always */
        int value = offsets.getValue(1);
        if (value == volumeOffset) {
            /* Nothing to do - offset already applied */
            return;
        }

        mVolumeControlNativeInterface.setExtAudioOutVolumeOffset(device, 1, volumeOffset);
    }

    /**
     * {@hide}
     */
    public void setGroupVolume(int groupId, int volume) {
        if (volume < 0) {
            Log.w(TAG, "Tried to set invalid volume " + volume + ". Ignored.");
            return;
        }

        mGroupVolumeCache.put(groupId, volume);
        mVolumeControlNativeInterface.setGroupVolume(groupId, volume);
    }

    /**
     * {@hide}
     * @param groupId
     */
    public int getGroupVolume(int groupId) {
        return mGroupVolumeCache.getOrDefault(groupId,
                        IBluetoothVolumeControl.VOLUME_CONTROL_UNKNOWN_VOLUME);
    }

    /**
     * {@hide}
     */
    public void mute(BluetoothDevice device) {
        mVolumeControlNativeInterface.mute(device);
    }

    /**
     * {@hide}
     */
    public void muteGroup(int groupId) {
        mVolumeControlNativeInterface.muteGroup(groupId);
    }

    /**
     * {@hide}
     */
    public void unmute(BluetoothDevice device) {
        mVolumeControlNativeInterface.unmute(device);
    }

    /**
     * {@hide}
     */
    public void unmuteGroup(int groupId) {
        mVolumeControlNativeInterface.unmuteGroup(groupId);
    }

    /**
     * {@hide}
     */
    public void handleGroupNodeAdded(int groupId, BluetoothDevice device) {
        // Ignore disconnected device, its volume will be set once it connects
        synchronized (mStateMachines) {
            VolumeControlStateMachine sm = mStateMachines.get(device);
            if (sm == null) {
                return;
            }
            if (sm.getConnectionState() != BluetoothProfile.STATE_CONNECTED) {
                return;
            }
        }

        // If group volume has already changed, the new group member should set it
        Integer groupVolume = mGroupVolumeCache.getOrDefault(groupId,
                IBluetoothVolumeControl.VOLUME_CONTROL_UNKNOWN_VOLUME);
        if (groupVolume != IBluetoothVolumeControl.VOLUME_CONTROL_UNKNOWN_VOLUME) {
            // Correct the volume level only if device was already reported as connected.
            boolean can_change_volume = false;
            synchronized (mStateMachines) {
                VolumeControlStateMachine sm = mStateMachines.get(device);
                if (sm != null) {
                    can_change_volume =
                            (sm.getConnectionState() == BluetoothProfile.STATE_CONNECTED);
                }
            }
            if (can_change_volume) {
                Log.i(TAG, "Setting value:" + groupVolume + " to " + device);
                mVolumeControlNativeInterface.setVolume(device, groupVolume);
            }
        }
    }

    void handleVolumeControlChanged(BluetoothDevice device, int groupId,
                                    int volume, boolean mute, boolean isAutonomous) {

        if (isAutonomous && device != null) {
            Log.e(TAG, "We expect only group notification for autonomous updates");
            return;
        }

        if (groupId == IBluetoothLeAudio.LE_AUDIO_GROUP_ID_INVALID) {
            LeAudioService leAudioService = mFactory.getLeAudioService();
            if (leAudioService == null) {
                Log.e(TAG, "leAudioService not available");
                return;
            }
            groupId = leAudioService.getGroupId(device);
        }

        if (groupId == IBluetoothLeAudio.LE_AUDIO_GROUP_ID_INVALID) {
            Log.e(TAG, "Device not a part of the group");
            return;
        }

        int groupVolume = getGroupVolume(groupId);

        if (!isAutonomous) {
            /* If the change is triggered by Android device, the stream is already changed.
             * However it might be called with isAutonomous, one the first read of after
             * reconnection. Make sure device has group volume. Also it might happen that
             * remote side send us wrong value - lets check it.
             */

            if (groupVolume == volume) {
                Log.i(TAG, " Volume:" + volume + " confirmed by remote side.");
                return;
            }

            if (device != null && groupVolume
                            != IBluetoothVolumeControl.VOLUME_CONTROL_UNKNOWN_VOLUME) {
                // Correct the volume level only if device was already reported as connected.
                boolean can_change_volume = false;
                synchronized (mStateMachines) {
                    VolumeControlStateMachine sm = mStateMachines.get(device);
                    if (sm != null) {
                        can_change_volume =
                                (sm.getConnectionState() == BluetoothProfile.STATE_CONNECTED);
                    }
                }
                if (can_change_volume) {
                    Log.i(TAG, "Setting value:" + groupVolume + " to " + device);
                    mVolumeControlNativeInterface.setVolume(device, groupVolume);
                }
            } else {
                Log.e(TAG, "Volume changed did not succeed. Volume: " + volume
                                + " expected volume: " + groupVolume);
            }
        } else {
            // TODO: Handle the other arguments: mute.

            /* Received group notification for autonomous change. Update cache and audio system. */
            mGroupVolumeCache.put(groupId, volume);

            int streamType = getBluetoothContextualVolumeStream();
            mAudioManager.setStreamVolume(streamType, getDeviceVolume(streamType, volume),
                    AudioManager.FLAG_SHOW_UI | AudioManager.FLAG_BLUETOOTH_ABS_VOLUME);
        }
    }

    /**
     * {@hide}
     */
    public int getAudioDeviceGroupVolume(int groupId) {
        int volume = getGroupVolume(groupId);
        if (volume == IBluetoothVolumeControl.VOLUME_CONTROL_UNKNOWN_VOLUME) return -1;
        return getDeviceVolume(getBluetoothContextualVolumeStream(), volume);
    }

    int getDeviceVolume(int streamType, int bleVolume) {
        int deviceMaxVolume = mAudioManager.getStreamMaxVolume(streamType);

        // TODO: Investigate what happens in classic BT when BT volume is changed to zero.
        double deviceVolume = (double) (bleVolume * deviceMaxVolume) / LE_AUDIO_MAX_VOL;
        return (int) Math.round(deviceVolume);
    }

    // Copied from AudioService.getBluetoothContextualVolumeStream() and modified it.
    int getBluetoothContextualVolumeStream() {
        int mode = mAudioManager.getMode();
        switch (mode) {
            case AudioManager.MODE_IN_COMMUNICATION:
            case AudioManager.MODE_IN_CALL:
                return AudioManager.STREAM_VOICE_CALL;
            case AudioManager.MODE_NORMAL:
            default:
                // other conditions will influence the stream type choice, read on...
                break;
        }
        return AudioManager.STREAM_MUSIC;
    }

    void handleDeviceAvailable(BluetoothDevice device, int numberOfExternalOutputs) {
        if (numberOfExternalOutputs == 0) {
            Log.i(TAG, "Volume offset not available");
            return;
        }

        VolumeControlOffsetDescriptor offsets = mAudioOffsets.get(device);
        if (offsets == null) {
            offsets = new VolumeControlOffsetDescriptor();
            mAudioOffsets.put(device, offsets);
        } else if (offsets.size() != numberOfExternalOutputs) {
            Log.i(TAG, "Number of offset changed: ");
            offsets.clear();
        }

        /* Stack delivers us number of audio outputs.
         * Offset ids a countinous from 1 to number_of_ext_outputs*/
        for (int i = 1; i <= numberOfExternalOutputs; i++) {
            offsets.add(i);
            mVolumeControlNativeInterface.getExtAudioOutVolumeOffset(device, i);
            mVolumeControlNativeInterface.getExtAudioOutDescription(device, i);
        }
    }

    void handleDeviceExtAudioOffsetChanged(BluetoothDevice device, int id, int value) {
        if (DBG) {
            Log.d(TAG, " device: " + device + " offset_id: " +  id + " value: " + value);
        }
        VolumeControlOffsetDescriptor offsets = mAudioOffsets.get(device);
        if (offsets == null) {
            Log.e(TAG, " Offsets not found for device: " + device);
            return;
        }
        offsets.setValue(id, value);

        if (mCallbacks == null) {
            return;
        }

        int n = mCallbacks.beginBroadcast();
        for (int i = 0; i < n; i++) {
            try {
                mCallbacks.getBroadcastItem(i).onVolumeOffsetChanged(device, value);
            } catch (RemoteException e) {
                continue;
            }
        }
        mCallbacks.finishBroadcast();
    }

    void handleDeviceExtAudioLocationChanged(BluetoothDevice device, int id, int location) {
        if (DBG) {
            Log.d(TAG, " device: " + device + " offset_id: "
                    + id + " location: " + location);
        }

        VolumeControlOffsetDescriptor offsets = mAudioOffsets.get(device);
        if (offsets == null) {
            Log.e(TAG, " Offsets not found for device: " + device);
            return;
        }
        offsets.setLocation(id, location);
    }

    void handleDeviceExtAudioDescriptionChanged(BluetoothDevice device, int id,
                                                String description) {
        if (DBG) {
            Log.d(TAG, " device: " + device + " offset_id: "
                    + id + " description: " + description);
        }

        VolumeControlOffsetDescriptor offsets = mAudioOffsets.get(device);
        if (offsets == null) {
            Log.e(TAG, " Offsets not found for device: " + device);
            return;
        }
        offsets.setDescription(id, description);
    }

    void messageFromNative(VolumeControlStackEvent stackEvent) {

        if (stackEvent.type == VolumeControlStackEvent.EVENT_TYPE_VOLUME_STATE_CHANGED) {
            handleVolumeControlChanged(stackEvent.device, stackEvent.valueInt1,
                                       stackEvent.valueInt2, stackEvent.valueBool1,
                                       stackEvent.valueBool2);
          return;
        }

        Objects.requireNonNull(stackEvent.device,
                "Device should never be null, event: " + stackEvent);

        Intent intent = null;

        if (intent != null) {
            intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT
                    | Intent.FLAG_RECEIVER_INCLUDE_BACKGROUND);
            sendBroadcast(intent, BLUETOOTH_CONNECT);
            return;
        }

        BluetoothDevice device = stackEvent.device;
        if (stackEvent.type == VolumeControlStackEvent.EVENT_TYPE_DEVICE_AVAILABLE) {
            handleDeviceAvailable(device, stackEvent.valueInt1);
            return;
        }

        if (stackEvent.type
                == VolumeControlStackEvent.EVENT_TYPE_EXT_AUDIO_OUT_VOL_OFFSET_CHANGED) {
            handleDeviceExtAudioOffsetChanged(device, stackEvent.valueInt1, stackEvent.valueInt2);
            return;
        }

        if (stackEvent.type
                == VolumeControlStackEvent.EVENT_TYPE_EXT_AUDIO_OUT_LOCATION_CHANGED) {
            handleDeviceExtAudioLocationChanged(device, stackEvent.valueInt1,
                                                    stackEvent.valueInt2);
            return;
        }

        if (stackEvent.type
                == VolumeControlStackEvent.EVENT_TYPE_EXT_AUDIO_OUT_DESCRIPTION_CHANGED) {
            handleDeviceExtAudioDescriptionChanged(device, stackEvent.valueInt1,
                                                    stackEvent.valueString1);
            return;
        }

        synchronized (mStateMachines) {
            VolumeControlStateMachine sm = mStateMachines.get(device);
            if (sm == null) {
                if (stackEvent.type
                        == VolumeControlStackEvent.EVENT_TYPE_CONNECTION_STATE_CHANGED) {
                    switch (stackEvent.valueInt1) {
                        case VolumeControlStackEvent.CONNECTION_STATE_CONNECTED:
                        case VolumeControlStackEvent.CONNECTION_STATE_CONNECTING:
                            sm = getOrCreateStateMachine(device);
                            break;
                        default:
                            break;
                    }
                }
            }
            if (sm == null) {
                Log.e(TAG, "Cannot process stack event: no state machine: " + stackEvent);
                return;
            }
            sm.sendMessage(VolumeControlStateMachine.STACK_EVENT, stackEvent);
        }
    }

    private VolumeControlStateMachine getOrCreateStateMachine(BluetoothDevice device) {
        if (device == null) {
            Log.e(TAG, "getOrCreateStateMachine failed: device cannot be null");
            return null;
        }
        synchronized (mStateMachines) {
            VolumeControlStateMachine sm = mStateMachines.get(device);
            if (sm != null) {
                return sm;
            }
            // Limit the maximum number of state machines to avoid DoS attack
            if (mStateMachines.size() >= MAX_VC_STATE_MACHINES) {
                Log.e(TAG, "Maximum number of VolumeControl state machines reached: "
                        + MAX_VC_STATE_MACHINES);
                return null;
            }
            if (DBG) {
                Log.d(TAG, "Creating a new state machine for " + device);
            }
            sm = VolumeControlStateMachine.make(device, this,
                    mVolumeControlNativeInterface, mStateMachinesThread.getLooper());
            mStateMachines.put(device, sm);
            return sm;
        }
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

        synchronized (mStateMachines) {
            VolumeControlStateMachine sm = mStateMachines.get(device);
            if (sm == null) {
                return;
            }
            if (sm.getConnectionState() != BluetoothProfile.STATE_DISCONNECTED) {
                Log.i(TAG, "Disconnecting device because it was unbonded.");
                disconnect(device);
                return;
            }
            removeStateMachine(device);
        }
    }

    private void removeStateMachine(BluetoothDevice device) {
        synchronized (mStateMachines) {
            VolumeControlStateMachine sm = mStateMachines.get(device);
            if (sm == null) {
                Log.w(TAG, "removeStateMachine: device " + device
                        + " does not have a state machine");
                return;
            }
            Log.i(TAG, "removeStateMachine: removing state machine for device: " + device);
            sm.doQuit();
            sm.cleanup();
            mStateMachines.remove(device);
        }
    }

    @VisibleForTesting
    synchronized void connectionStateChanged(BluetoothDevice device, int fromState,
                                             int toState) {
        if ((device == null) || (fromState == toState)) {
            Log.e(TAG, "connectionStateChanged: unexpected invocation. device=" + device
                    + " fromState=" + fromState + " toState=" + toState);
            return;
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
        } else if (toState == BluetoothProfile.STATE_CONNECTED) {
            // Restore the group volume if it was changed while the device was not yet connected.
            CsipSetCoordinatorService csipClient = mFactory.getCsipSetCoordinatorService();
            Integer groupId = csipClient.getGroupId(device, BluetoothUuid.CAP);
            if (groupId != IBluetoothCsipSetCoordinator.CSIS_GROUP_ID_INVALID) {
                Integer groupVolume = mGroupVolumeCache.getOrDefault(groupId,
                        IBluetoothVolumeControl.VOLUME_CONTROL_UNKNOWN_VOLUME);
                if (groupVolume != IBluetoothVolumeControl.VOLUME_CONTROL_UNKNOWN_VOLUME) {
                    mVolumeControlNativeInterface.setVolume(device, groupVolume);
                }
            }
        }
    }

    private class ConnectionStateChangedReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!BluetoothVolumeControl.ACTION_CONNECTION_STATE_CHANGED.equals(intent.getAction())) {
                return;
            }
            BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            int toState = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1);
            int fromState = intent.getIntExtra(BluetoothProfile.EXTRA_PREVIOUS_STATE, -1);
            connectionStateChanged(device, fromState, toState);
        }
    }

    /**
     * Binder object: must be a static class or memory leak may occur
     */
    @VisibleForTesting
    static class BluetoothVolumeControlBinder extends IBluetoothVolumeControl.Stub
            implements IProfileServiceBinder {
        @VisibleForTesting
        boolean mIsTesting = false;
        private VolumeControlService mService;

        @RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
        private VolumeControlService getService(AttributionSource source) {
            if (mIsTesting) {
                return mService;
            }
            if (!Utils.checkServiceAvailable(mService, TAG)
                    || !Utils.checkCallerIsSystemOrActiveOrManagedUser(mService, TAG)
                    || !Utils.checkConnectPermissionForDataDelivery(mService, source, TAG)) {
                return null;
            }
            return mService;
        }

        BluetoothVolumeControlBinder(VolumeControlService svc) {
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

                VolumeControlService service = getService(source);
                boolean defaultValue = false;
                if (service != null) {
                    defaultValue = service.connect(device);
                }
                receiver.send(defaultValue);
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

                VolumeControlService service = getService(source);
                boolean defaultValue = false;
                if (service != null) {
                    defaultValue = service.disconnect(device);
                }
                receiver.send(defaultValue);
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

                VolumeControlService service = getService(source);
                List<BluetoothDevice> defaultValue = new ArrayList<>();
                if (service != null) {
                    enforceBluetoothPrivilegedPermission(service);
                    defaultValue = service.getConnectedDevices();
                }
                receiver.send(defaultValue);
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

                VolumeControlService service = getService(source);
                List<BluetoothDevice> defaultValue = new ArrayList<>();
                if (service != null) {
                    defaultValue = service.getDevicesMatchingConnectionStates(states);
                }
                receiver.send(defaultValue);
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

                VolumeControlService service = getService(source);
                int defaultValue = BluetoothProfile.STATE_DISCONNECTED;
                if (service != null) {
                    defaultValue = service.getConnectionState(device);
                }
                receiver.send(defaultValue);
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }

        @Override
        public void setConnectionPolicy(BluetoothDevice device, int connectionPolicy,
                AttributionSource source, SynchronousResultReceiver receiver) {
            try {
                Objects.requireNonNull(device, "device cannot be null");
                Objects.requireNonNull(source, "source cannot be null");
                Objects.requireNonNull(receiver, "receiver cannot be null");

                VolumeControlService service = getService(source);
                boolean defaultValue = false;
                if (service != null) {
                    defaultValue = service.setConnectionPolicy(device, connectionPolicy);
                }
                receiver.send(defaultValue);
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

                VolumeControlService service = getService(source);
                int defaultValue = BluetoothProfile.CONNECTION_POLICY_UNKNOWN;
                if (service != null) {
                    defaultValue = service.getConnectionPolicy(device);
                }
                receiver.send(defaultValue);
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }

        @Override
        public void isVolumeOffsetAvailable(BluetoothDevice device,
                AttributionSource source, SynchronousResultReceiver receiver) {
            try {
                Objects.requireNonNull(device, "device cannot be null");
                Objects.requireNonNull(source, "source cannot be null");
                Objects.requireNonNull(receiver, "receiver cannot be null");

                boolean defaultValue = false;
                VolumeControlService service = getService(source);
                if (service != null) {
                    defaultValue = service.isVolumeOffsetAvailable(device);
                }
                receiver.send(defaultValue);
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }

        @Override
        public void setVolumeOffset(BluetoothDevice device, int volumeOffset,
                AttributionSource source, SynchronousResultReceiver receiver) {
            try {
                Objects.requireNonNull(device, "device cannot be null");
                Objects.requireNonNull(source, "source cannot be null");
                Objects.requireNonNull(receiver, "receiver cannot be null");

                VolumeControlService service = getService(source);
                if (service != null) {
                    service.setVolumeOffset(device, volumeOffset);
                }
                receiver.send(null);
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }

        @Override
        public void setGroupVolume(int groupId, int volume, AttributionSource source,
                SynchronousResultReceiver receiver) {
            try {
                Objects.requireNonNull(source, "source cannot be null");
                Objects.requireNonNull(receiver, "receiver cannot be null");

                VolumeControlService service = getService(source);
                if (service != null) {
                    service.setGroupVolume(groupId, volume);
                }
                receiver.send(null);
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }

        @Override
        public void getGroupVolume(int groupId, AttributionSource source,
                SynchronousResultReceiver receiver) {
            try {
                Objects.requireNonNull(source, "source cannot be null");
                Objects.requireNonNull(receiver, "receiver cannot be null");

                int groupVolume = 0;
                VolumeControlService service = getService(source);
                if (service != null) {
                    groupVolume = service.getGroupVolume(groupId);
                }
                receiver.send(groupVolume);
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }


        @Override
        public void mute(BluetoothDevice device,  AttributionSource source,
                SynchronousResultReceiver receiver) {
            try {
                Objects.requireNonNull(device, "device cannot be null");
                Objects.requireNonNull(source, "source cannot be null");
                Objects.requireNonNull(receiver, "receiver cannot be null");

                VolumeControlService service = getService(source);
                if (service != null) {
                    service.mute(device);
                }
                receiver.send(null);
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }

        @Override
        public void muteGroup(int groupId, AttributionSource source,
                SynchronousResultReceiver receiver) {
            try {
                Objects.requireNonNull(source, "source cannot be null");
                Objects.requireNonNull(receiver, "receiver cannot be null");

                VolumeControlService service = getService(source);
                if (service != null) {
                    service.muteGroup(groupId);
                }
                receiver.send(null);
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }

        @Override
        public void unmute(BluetoothDevice device,  AttributionSource source,
                SynchronousResultReceiver receiver) {
            try {
                Objects.requireNonNull(device, "device cannot be null");
                Objects.requireNonNull(source, "source cannot be null");
                Objects.requireNonNull(receiver, "receiver cannot be null");

                VolumeControlService service = getService(source);
                if (service != null) {
                    service.unmute(device);
                }
                receiver.send(null);
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }

        @Override
        public void unmuteGroup(int groupId,  AttributionSource source,
                SynchronousResultReceiver receiver) {
            try {
                Objects.requireNonNull(source, "source cannot be null");
                Objects.requireNonNull(receiver, "receiver cannot be null");

                VolumeControlService service = getService(source);
                if (service != null) {
                    service.unmuteGroup(groupId);
                }
                receiver.send(null);
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }

        @Override
        public void registerCallback(IBluetoothVolumeControlCallback callback,
                AttributionSource source, SynchronousResultReceiver receiver) {
            try {
                Objects.requireNonNull(callback, "callback cannot be null");
                Objects.requireNonNull(source, "source cannot be null");
                Objects.requireNonNull(receiver, "receiver cannot be null");

                VolumeControlService service = getService(source);
                if (service == null) {
                    throw new IllegalStateException("Service is unavailable");
                }

                enforceBluetoothPrivilegedPermission(service);

                service.mCallbacks.register(callback);
                receiver.send(null);
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }

        @Override
        public void unregisterCallback(IBluetoothVolumeControlCallback callback,
                AttributionSource source, SynchronousResultReceiver receiver) {
            try {
                Objects.requireNonNull(callback, "callback cannot be null");
                Objects.requireNonNull(source, "source cannot be null");
                Objects.requireNonNull(receiver, "receiver cannot be null");

                VolumeControlService service = getService(source);
                if (service == null) {
                    throw new IllegalStateException("Service is unavailable");
                }

                enforceBluetoothPrivilegedPermission(service);

                service.mCallbacks.unregister(callback);
                receiver.send(null);
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }
    }

    @Override
    public void dump(StringBuilder sb) {
        super.dump(sb);
        for (VolumeControlStateMachine sm : mStateMachines.values()) {
            sm.dump(sb);
        }

        for (Map.Entry<BluetoothDevice, VolumeControlOffsetDescriptor> entry :
                                                            mAudioOffsets.entrySet()) {
            VolumeControlOffsetDescriptor descriptor = entry.getValue();
            BluetoothDevice device = entry.getKey();
            ProfileService.println(sb, "    Device: " + device);
            ProfileService.println(sb, "    Volume offset cnt: " + descriptor.size());
            descriptor.dump(sb);
        }
        for (Map.Entry<Integer, Integer> entry : mGroupVolumeCache.entrySet()) {
            ProfileService.println(sb, "    GroupId: " + entry.getKey() + " volume: "
                            + entry.getValue());
        }
    }
}
