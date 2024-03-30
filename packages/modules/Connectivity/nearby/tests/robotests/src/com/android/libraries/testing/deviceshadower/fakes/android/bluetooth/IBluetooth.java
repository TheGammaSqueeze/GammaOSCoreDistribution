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

package android.bluetooth;

import android.content.AttributionSource;
import android.os.ParcelFileDescriptor;
import android.os.ParcelUuid;

/**
 * Fake interface replacement for hidden IBluetooth class
 */
public interface IBluetooth {

    // Bluetooth settings.
    String getAddress();

    String getName();

    boolean setName(String name);

    // Remote device properties.
    int getRemoteClass(BluetoothDevice device);

    String getRemoteName(BluetoothDevice device);

    int getRemoteType(BluetoothDevice device, AttributionSource attributionSource);

    ParcelUuid[] getRemoteUuids(BluetoothDevice device);

    boolean fetchRemoteUuids(BluetoothDevice device);

    // Bluetooth discovery.
    int getScanMode();

    boolean setScanMode(int mode, int duration);

    int getDiscoverableTimeout();

    boolean setDiscoverableTimeout(int timeout);

    boolean startDiscovery();

    boolean cancelDiscovery();

    boolean isDiscovering();

    // Adapter state.
    boolean isEnabled();

    int getState();

    boolean enable();

    boolean disable();

    // Rfcomm sockets.
    ParcelFileDescriptor connectSocket(BluetoothDevice device, int type, ParcelUuid uuid,
            int port, int flag);

    ParcelFileDescriptor createSocketChannel(int type, String serviceName, ParcelUuid uuid,
            int port, int flag);

    // BLE settings.
    /* SINCE SDK 21 */ boolean isMultiAdvertisementSupported();

    /* SINCE SDK 22 */ boolean isPeripheralModeSupported();

    /* SINCE SDK 21 */  boolean isOffloadedFilteringSupported();

    // Bonding (pairing).
    int getBondState(BluetoothDevice device, AttributionSource attributionSource);

    boolean createBond(BluetoothDevice device, int transport, OobData remoteP192Data,
            OobData remoteP256Data, AttributionSource attributionSource);

    boolean setPairingConfirmation(BluetoothDevice device, boolean accept,
            AttributionSource attributionSource);

    boolean setPasskey(BluetoothDevice device, int passkey);

    boolean cancelBondProcess(BluetoothDevice device);

    boolean removeBond(BluetoothDevice device);

    BluetoothDevice[] getBondedDevices();

    // Connecting to profiles.
    int getAdapterConnectionState();

    int getProfileConnectionState(int profile);

    // Access permissions
    int getPhonebookAccessPermission(BluetoothDevice device);

    boolean setPhonebookAccessPermission(BluetoothDevice device, int value);

    int getMessageAccessPermission(BluetoothDevice device);

    boolean setMessageAccessPermission(BluetoothDevice device, int value);

    int getSimAccessPermission(BluetoothDevice device);

    boolean setSimAccessPermission(BluetoothDevice device, int value);
}
