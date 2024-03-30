/*
 * Copyright 2008, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.bluetooth;

import android.app.PendingIntent;
import android.bluetooth.IBluetoothActivityEnergyInfoListener;
import android.bluetooth.IBluetoothCallback;
import android.bluetooth.IBluetoothConnectionCallback;
import android.bluetooth.IBluetoothMetadataListener;
import android.bluetooth.IBluetoothOobDataCallback;
import android.bluetooth.IBluetoothSocketManager;
import android.bluetooth.IBluetoothStateChangeCallback;
import android.bluetooth.BluetoothActivityEnergyInfo;
import android.bluetooth.BluetoothSinkAudioPolicy;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.IncomingRfcommSocketInfo;
import android.bluetooth.OobData;
import android.content.AttributionSource;
import android.os.ParcelUuid;
import android.os.ParcelFileDescriptor;
import android.os.ResultReceiver;

import com.android.modules.utils.SynchronousResultReceiver;

/**
 * System private API for talking with the Bluetooth service.
 *
 * {@hide}
 */
interface IBluetooth
{
    @JavaPassthrough(annotation="@android.annotation.RequiresNoPermission")
    oneway void getState(in SynchronousResultReceiver receiver);

    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)")
    oneway void enable(boolean quietMode, in AttributionSource attributionSource, in SynchronousResultReceiver receiver);
    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)")
    oneway void disable(in AttributionSource attributionSource, in SynchronousResultReceiver receiver);

    @UnsupportedAppUsage
    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(allOf={android.Manifest.permission.BLUETOOTH_CONNECT,android.Manifest.permission.LOCAL_MAC_ADDRESS})")
    String getAddress();
    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(allOf={android.Manifest.permission.BLUETOOTH_CONNECT,android.Manifest.permission.LOCAL_MAC_ADDRESS})")
    oneway void getAddressWithAttribution(in AttributionSource attributionSource, in SynchronousResultReceiver receiver);
    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)")
    oneway void getUuids(in AttributionSource attributionSource, in SynchronousResultReceiver receiver);
    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)")
    oneway void setName(in String name, in AttributionSource attributionSource, in SynchronousResultReceiver receiver);
    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)")
    oneway void getIdentityAddress(in String address, in SynchronousResultReceiver receiver);
    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)")
    oneway void getName(in AttributionSource attributionSource, in SynchronousResultReceiver receiver);
    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_ADVERTISE)")
    oneway void getNameLengthForAdvertise(in AttributionSource attributionSource, in SynchronousResultReceiver receiver);
    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)")
    oneway void getBluetoothClass(in AttributionSource attributionSource, in SynchronousResultReceiver receiver);
    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(allOf={android.Manifest.permission.BLUETOOTH_CONNECT,android.Manifest.permission.BLUETOOTH_PRIVILEGED})")
    oneway void setBluetoothClass(in BluetoothClass bluetoothClass, in AttributionSource attributionSource, in SynchronousResultReceiver receiver);

    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)")
    oneway void getIoCapability(in AttributionSource attributionSource, in SynchronousResultReceiver receiver);
    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(allOf={android.Manifest.permission.BLUETOOTH_CONNECT,android.Manifest.permission.BLUETOOTH_PRIVILEGED})")
    oneway void setIoCapability(int capability, in AttributionSource attributionSource, in SynchronousResultReceiver receiver);
    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)")
    oneway void getLeIoCapability(in AttributionSource attributionSource, in SynchronousResultReceiver receiver);
    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(allOf={android.Manifest.permission.BLUETOOTH_CONNECT,android.Manifest.permission.BLUETOOTH_PRIVILEGED})")
    oneway void setLeIoCapability(int capability, in AttributionSource attributionSource, in SynchronousResultReceiver receiver);

    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_SCAN)")
    oneway void getScanMode(in AttributionSource attributionSource, in SynchronousResultReceiver receiver);
    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_SCAN)")
    oneway void setScanMode(int mode, in AttributionSource attributionSource, in SynchronousResultReceiver receiver);

    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_SCAN)")
    oneway void getDiscoverableTimeout(in AttributionSource attributionSource, in SynchronousResultReceiver receiver);
    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_SCAN)")
    oneway void setDiscoverableTimeout(long timeout, in AttributionSource attributionSource, in SynchronousResultReceiver receiver);

    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_SCAN)")
    oneway void startDiscovery(in AttributionSource attributionSource, in SynchronousResultReceiver receiver);
    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_SCAN)")
    oneway void cancelDiscovery(in AttributionSource attributionSource, in SynchronousResultReceiver receiver);
    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_SCAN)")
    oneway void isDiscovering(in AttributionSource attributionSource, in SynchronousResultReceiver receiver);
    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(allOf={android.Manifest.permission.BLUETOOTH_CONNECT,android.Manifest.permission.BLUETOOTH_PRIVILEGED})")
    oneway void getDiscoveryEndMillis(in AttributionSource attributionSource, in SynchronousResultReceiver receiver);

    @JavaPassthrough(annotation="@android.annotation.RequiresNoPermission")
    oneway void getAdapterConnectionState(in SynchronousResultReceiver receiver);
    @JavaPassthrough(annotation="@android.annotation.RequiresNoPermission")
    oneway void getProfileConnectionState(int profile, in SynchronousResultReceiver receiver);

    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)")
    oneway void getBondedDevices(in AttributionSource attributionSource, in SynchronousResultReceiver receiver);
    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)")
    oneway void createBond(in BluetoothDevice device, in int transport, in OobData p192Data, in OobData p256Data, in AttributionSource attributionSource, in SynchronousResultReceiver receiver);
    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)")
    oneway void cancelBondProcess(in BluetoothDevice device, in AttributionSource attributionSource, in SynchronousResultReceiver receiver);
    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)")
    oneway void removeBond(in BluetoothDevice device, in AttributionSource attributionSource, in SynchronousResultReceiver receiver);
    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)")
    oneway void getBondState(in BluetoothDevice device, in AttributionSource attributionSource, in SynchronousResultReceiver receiver);
    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)")
    oneway void isBondingInitiatedLocally(in BluetoothDevice device, in AttributionSource attributionSource, in SynchronousResultReceiver receiver);
    @JavaPassthrough(annotation="@android.annotation.RequiresNoPermission")
    oneway void getSupportedProfiles(in AttributionSource attributionSource, in SynchronousResultReceiver receiver);
    @UnsupportedAppUsage
    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)")
    int getConnectionState(in BluetoothDevice device);
    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)")
    oneway void getConnectionStateWithAttribution(in BluetoothDevice device, in AttributionSource attributionSource, in SynchronousResultReceiver receiver);
    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)")
    oneway void getRemoteName(in BluetoothDevice device, in AttributionSource attributionSource, in SynchronousResultReceiver receiver);
    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)")
    oneway void getRemoteType(in BluetoothDevice device, in AttributionSource attributionSource, in SynchronousResultReceiver receiver);
    @UnsupportedAppUsage
    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)")
    String getRemoteAlias(in BluetoothDevice device);
    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)")
    oneway void getRemoteAliasWithAttribution(in BluetoothDevice device, in AttributionSource attributionSource, in SynchronousResultReceiver receiver);
    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)")
    oneway void setRemoteAlias(in BluetoothDevice device, in String name, in AttributionSource attributionSource, in SynchronousResultReceiver receiver);
    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)")
    oneway void getRemoteClass(in BluetoothDevice device, in AttributionSource attributionSource, in SynchronousResultReceiver receiver);
    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)")
    oneway void getRemoteUuids(in BluetoothDevice device, in AttributionSource attributionSource, in SynchronousResultReceiver receiver);
    @UnsupportedAppUsage
    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)")
    boolean fetchRemoteUuids(in BluetoothDevice device);
    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)")
    oneway void fetchRemoteUuidsWithAttribution(in BluetoothDevice device, in int transport, in AttributionSource attributionSource, in SynchronousResultReceiver receiver);
    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)")
    oneway void sdpSearch(in BluetoothDevice device, in ParcelUuid uuid, in AttributionSource attributionSource, in SynchronousResultReceiver receiver);
    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)")
    oneway void getBatteryLevel(in BluetoothDevice device, in AttributionSource attributionSource, in SynchronousResultReceiver receiver);
    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)")
    oneway void getMaxConnectedAudioDevices(in AttributionSource attributionSource, in SynchronousResultReceiver receiver);

    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)")
    oneway void setPin(in BluetoothDevice device, boolean accept, int len, in byte[] pinCode, in AttributionSource attributionSource, in SynchronousResultReceiver receiver);
    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)")
    oneway void setPasskey(in BluetoothDevice device, boolean accept, int len, in byte[] passkey, in AttributionSource attributionSource, in SynchronousResultReceiver receiver);
    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(allOf={android.Manifest.permission.BLUETOOTH_CONNECT,android.Manifest.permission.BLUETOOTH_PRIVILEGED})")
    oneway void setPairingConfirmation(in BluetoothDevice device, boolean accept, in AttributionSource attributionSource, in SynchronousResultReceiver receiver);

    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)")
    oneway void getPhonebookAccessPermission(in BluetoothDevice device, in AttributionSource attributionSource, in SynchronousResultReceiver receiver);
    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(allOf={android.Manifest.permission.BLUETOOTH_CONNECT,android.Manifest.permission.BLUETOOTH_PRIVILEGED})")
    oneway void setSilenceMode(in BluetoothDevice device, boolean silence, in AttributionSource attributionSource, in SynchronousResultReceiver receiver);
    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(allOf={android.Manifest.permission.BLUETOOTH_CONNECT,android.Manifest.permission.BLUETOOTH_PRIVILEGED})")
    oneway void getSilenceMode(in BluetoothDevice device, in AttributionSource attributionSource, in SynchronousResultReceiver receiver);
    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(allOf={android.Manifest.permission.BLUETOOTH_CONNECT,android.Manifest.permission.BLUETOOTH_PRIVILEGED})")
    oneway void setPhonebookAccessPermission(in BluetoothDevice device, int value, in AttributionSource attributionSource, in SynchronousResultReceiver receiver);
    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)")
    oneway void getMessageAccessPermission(in BluetoothDevice device, in AttributionSource attributionSource, in SynchronousResultReceiver receiver);
    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(allOf={android.Manifest.permission.BLUETOOTH_CONNECT,android.Manifest.permission.BLUETOOTH_PRIVILEGED})")
    oneway void setMessageAccessPermission(in BluetoothDevice device, int value, in AttributionSource attributionSource, in SynchronousResultReceiver receiver);
    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)")
    oneway void getSimAccessPermission(in BluetoothDevice device, in AttributionSource attributionSource, in SynchronousResultReceiver receiver);
    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(allOf={android.Manifest.permission.BLUETOOTH_CONNECT,android.Manifest.permission.BLUETOOTH_PRIVILEGED})")
    oneway void setSimAccessPermission(in BluetoothDevice device, int value, in AttributionSource attributionSource, in SynchronousResultReceiver receiver);

    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(allOf={android.Manifest.permission.BLUETOOTH_CONNECT,android.Manifest.permission.BLUETOOTH_PRIVILEGED})")
    oneway void registerCallback(in IBluetoothCallback callback, in AttributionSource attributionSource, in SynchronousResultReceiver receiver);
    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(allOf={android.Manifest.permission.BLUETOOTH_CONNECT,android.Manifest.permission.BLUETOOTH_PRIVILEGED})")
    oneway void unregisterCallback(in IBluetoothCallback callback, in AttributionSource attributionSource, in SynchronousResultReceiver receiver);

    // For Socket
    @JavaPassthrough(annotation="@android.annotation.RequiresNoPermission")
    IBluetoothSocketManager getSocketManager();

    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(allOf={android.Manifest.permission.BLUETOOTH_CONNECT,android.Manifest.permission.BLUETOOTH_PRIVILEGED})")
    oneway void factoryReset(in AttributionSource attributionSource, in SynchronousResultReceiver receiver);

    @JavaPassthrough(annotation="@android.annotation.RequiresNoPermission")
    oneway void isMultiAdvertisementSupported(in SynchronousResultReceiver receiver);
    @JavaPassthrough(annotation="@android.annotation.RequiresNoPermission")
    oneway void isOffloadedFilteringSupported(in SynchronousResultReceiver receiver);
    @JavaPassthrough(annotation="@android.annotation.RequiresNoPermission")
    oneway void isOffloadedScanBatchingSupported(in SynchronousResultReceiver receiver);
    @JavaPassthrough(annotation="@android.annotation.RequiresNoPermission")
    oneway void isActivityAndEnergyReportingSupported(in SynchronousResultReceiver receiver);
    @JavaPassthrough(annotation="@android.annotation.RequiresNoPermission")
    oneway void isLe2MPhySupported(in SynchronousResultReceiver receiver);
    @JavaPassthrough(annotation="@android.annotation.RequiresNoPermission")
    oneway void isLeCodedPhySupported(in SynchronousResultReceiver receiver);
    @JavaPassthrough(annotation="@android.annotation.RequiresNoPermission")
    oneway void isLeExtendedAdvertisingSupported(in SynchronousResultReceiver receiver);
    @JavaPassthrough(annotation="@android.annotation.RequiresNoPermission")
    oneway void isLePeriodicAdvertisingSupported(in SynchronousResultReceiver receiver);
    @JavaPassthrough(annotation="@android.annotation.RequiresNoPermission")
    oneway void isLeAudioSupported(in SynchronousResultReceiver receiver);
    @JavaPassthrough(annotation="@android.annotation.RequiresNoPermission")
    oneway void isLeAudioBroadcastSourceSupported(in SynchronousResultReceiver receiver);
    @JavaPassthrough(annotation="@android.annotation.RequiresNoPermission")
    oneway void isLeAudioBroadcastAssistantSupported(in SynchronousResultReceiver receiver);
    @JavaPassthrough(annotation="@android.annotation.RequiresNoPermission")
    oneway void getLeMaximumAdvertisingDataLength(in SynchronousResultReceiver receiver);

    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(allOf={android.Manifest.permission.BLUETOOTH_CONNECT,android.Manifest.permission.BLUETOOTH_PRIVILEGED})")
    oneway void reportActivityInfo(in AttributionSource attributionSource, in SynchronousResultReceiver receiver);

    // For Metadata
    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(allOf={android.Manifest.permission.BLUETOOTH_CONNECT,android.Manifest.permission.BLUETOOTH_PRIVILEGED})")
    oneway void registerMetadataListener(in IBluetoothMetadataListener listener, in BluetoothDevice device, in AttributionSource attributionSource, in SynchronousResultReceiver receiver);
    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(allOf={android.Manifest.permission.BLUETOOTH_CONNECT,android.Manifest.permission.BLUETOOTH_PRIVILEGED})")
    oneway void unregisterMetadataListener(in BluetoothDevice device, in AttributionSource attributionSource, in SynchronousResultReceiver receiver);
    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(allOf={android.Manifest.permission.BLUETOOTH_CONNECT,android.Manifest.permission.BLUETOOTH_PRIVILEGED})")
    oneway void setMetadata(in BluetoothDevice device, in int key, in byte[] value, in AttributionSource attributionSource, in SynchronousResultReceiver receiver);
    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(allOf={android.Manifest.permission.BLUETOOTH_CONNECT,android.Manifest.permission.BLUETOOTH_PRIVILEGED})")
    oneway void getMetadata(in BluetoothDevice device, in int key, in AttributionSource attributionSource, in SynchronousResultReceiver receiver);

    /**
     * Requests the controller activity info asynchronously.
     * The implementor is expected to reply with the
     * {@link android.bluetooth.BluetoothActivityEnergyInfo} object placed into the Bundle with the
     * key {@link android.os.BatteryStats#RESULT_RECEIVER_CONTROLLER_KEY}.
     * The result code is ignored.
     */
    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(allOf={android.Manifest.permission.BLUETOOTH_CONNECT,android.Manifest.permission.BLUETOOTH_PRIVILEGED})")
    oneway void requestActivityInfo(in IBluetoothActivityEnergyInfoListener listener, in AttributionSource attributionSource);

    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(allOf={android.Manifest.permission.BLUETOOTH_CONNECT,android.Manifest.permission.BLUETOOTH_PRIVILEGED})")
    oneway void onLeServiceUp(in AttributionSource attributionSource, in SynchronousResultReceiver receiver);
    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(allOf={android.Manifest.permission.BLUETOOTH_CONNECT,android.Manifest.permission.BLUETOOTH_PRIVILEGED})")
    oneway void onBrEdrDown(in AttributionSource attributionSource, in SynchronousResultReceiver receiver);

    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(allOf={android.Manifest.permission.BLUETOOTH_CONNECT,android.Manifest.permission.BLUETOOTH_PRIVILEGED,android.Manifest.permission.MODIFY_PHONE_STATE})")
    oneway void connectAllEnabledProfiles(in BluetoothDevice device, in AttributionSource attributionSource, in SynchronousResultReceiver receiver);
    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(allOf={android.Manifest.permission.BLUETOOTH_CONNECT,android.Manifest.permission.BLUETOOTH_PRIVILEGED})")
    oneway void disconnectAllEnabledProfiles(in BluetoothDevice device, in AttributionSource attributionSource, in SynchronousResultReceiver receiver);

    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(allOf={android.Manifest.permission.BLUETOOTH_CONNECT,android.Manifest.permission.BLUETOOTH_PRIVILEGED,android.Manifest.permission.MODIFY_PHONE_STATE})")
    oneway void setActiveDevice(in BluetoothDevice device, in int profiles, in AttributionSource attributionSource, in SynchronousResultReceiver receiver);
    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_PRIVILEGED)")
    oneway void getActiveDevices(in int profile, in AttributionSource attributionSource, in SynchronousResultReceiver receiver);
    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)")
    oneway void getMostRecentlyConnectedDevices(in AttributionSource attributionSource, in SynchronousResultReceiver receiver);
    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(allOf={android.Manifest.permission.BLUETOOTH_CONNECT,android.Manifest.permission.BLUETOOTH_PRIVILEGED,android.Manifest.permission.MODIFY_PHONE_STATE})")
    oneway void removeActiveDevice(in int profiles, in AttributionSource attributionSource, in SynchronousResultReceiver receiver);

    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(allOf={android.Manifest.permission.BLUETOOTH_CONNECT,android.Manifest.permission.BLUETOOTH_PRIVILEGED})")
    oneway void registerBluetoothConnectionCallback(in IBluetoothConnectionCallback callback, in AttributionSource attributionSource, in SynchronousResultReceiver receiver);
    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(allOf={android.Manifest.permission.BLUETOOTH_CONNECT,android.Manifest.permission.BLUETOOTH_PRIVILEGED})")
    oneway void unregisterBluetoothConnectionCallback(in IBluetoothConnectionCallback callback, in AttributionSource attributionSource, in SynchronousResultReceiver receiver);

    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(allOf={android.Manifest.permission.BLUETOOTH_CONNECT,android.Manifest.permission.BLUETOOTH_PRIVILEGED})")
    oneway void canBondWithoutDialog(in BluetoothDevice device, in AttributionSource attributionSource, in SynchronousResultReceiver receiver);
    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(allOf={android.Manifest.permission.BLUETOOTH_CONNECT,android.Manifest.permission.BLUETOOTH_PRIVILEGED})")
    oneway void generateLocalOobData(in int transport, IBluetoothOobDataCallback callback, in AttributionSource attributionSource, in SynchronousResultReceiver receiver);

    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(allOf={android.Manifest.permission.BLUETOOTH_CONNECT,android.Manifest.permission.BLUETOOTH_PRIVILEGED})")
    oneway void allowLowLatencyAudio(in boolean allowed, in BluetoothDevice device, in SynchronousResultReceiver receiver);

    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)")
    void isRequestAudioPolicyAsSinkSupported(in BluetoothDevice device, in AttributionSource attributionSource, in SynchronousResultReceiver receiver);
    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)")
    void requestAudioPolicyAsSink(in BluetoothDevice device, in BluetoothSinkAudioPolicy policies, in AttributionSource attributionSource, in SynchronousResultReceiver receiver);
    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)")
    void getRequestedAudioPolicyAsSink(in BluetoothDevice device, in AttributionSource attributionSource, in SynchronousResultReceiver receiver);

    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)")
    oneway void startRfcommListener(String name, in ParcelUuid uuid, in PendingIntent intent, in AttributionSource attributionSource, in SynchronousResultReceiver receiver);
    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)")
    oneway void stopRfcommListener(in ParcelUuid uuid, in AttributionSource attributionSource, in SynchronousResultReceiver receiver);
    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)")
    oneway void retrievePendingSocketForServiceRecord(in ParcelUuid uuid, in AttributionSource attributionSource, in SynchronousResultReceiver receiver);

    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(allOf={android.Manifest.permission.BLUETOOTH_CONNECT,android.Manifest.permission.BLUETOOTH_PRIVILEGED})")
    oneway void setForegroundUserId(in int userId, in AttributionSource attributionSource);
}
