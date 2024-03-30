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

import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanSettings;
import android.os.ParcelUuid;

import java.util.List;

/**
 * Fake interface replacement for IBluetoothGatt
 * TODO(b/200231384): include >=N interface.
 */
public interface IBluetoothGatt {

    /* ONLY SDK 23 */
    void startScan(int appIf, boolean isServer, ScanSettings settings,
            List<ScanFilter> filters, List<?> scanStorages, String callPackage);

    /* ONLY SDK 21 */
    void startScan(int appIf, boolean isServer, ScanSettings settings,
            List<ScanFilter> filters, List<?> scanStorages);

    /* SINCE SDK 21 */
    void stopScan(int appIf, boolean isServer);

    /* SINCE SDK 21 */
    void startMultiAdvertising(
            int appIf, AdvertiseData advertiseData, AdvertiseData scanResponse,
            AdvertiseSettings settings);

    /* SINCE SDK 21 */
    void stopMultiAdvertising(int appIf);

    /* SINCE SDK 21 */
    void registerClient(ParcelUuid appId, IBluetoothGattCallback callback);

    /* SINCE SDK 21 */
    void unregisterClient(int clientIf);

    /* SINCE SDK 21 */
    void clientConnect(int clientIf, String address, boolean isDirect, int transport);

    /* SINCE SDK 21 */
    void clientDisconnect(int clientIf, String address);

    /* SINCE SDK 21 */
    void discoverServices(int clientIf, String address);

    /* SINCE SDK 21 */
    void readCharacteristic(int clientIf, String address, int srvcType,
            int srvcInstanceId, ParcelUuid srvcId, int charInstanceId, ParcelUuid charId,
            int authReq);

    /* SINCE SDK 21 */
    void writeCharacteristic(int clientIf, String address, int srvcType,
            int srvcInstanceId, ParcelUuid srvcId, int charInstanceId, ParcelUuid charId,
            int writeType, int authReq, byte[] value);

    /* SINCE SDK 21 */
    void readDescriptor(int clientIf, String address, int srvcType,
            int srvcInstanceId, ParcelUuid srvcId, int charInstanceId, ParcelUuid charId,
            int descrInstanceId, ParcelUuid descrUuid, int authReq);

    /* SINCE SDK 21 */
    void writeDescriptor(int clientIf, String address, int srvcType,
            int srvcInstanceId, ParcelUuid srvcId, int charInstanceId, ParcelUuid charId,
            int descrInstanceId, ParcelUuid descrId, int writeType, int authReq, byte[] value);

    /* SINCE SDK 21 */
    void registerForNotification(int clientIf, String address, int srvcType,
            int srvcInstanceId, ParcelUuid srvcId, int charInstanceId, ParcelUuid charId,
            boolean enable);

    /* SINCE SDK 21 */
    void registerServer(ParcelUuid appId, IBluetoothGattServerCallback callback);

    /* SINCE SDK 21 */
    void unregisterServer(int serverIf);

    /* SINCE SDK 21 */
    void serverConnect(int servertIf, String address, boolean isDirect, int transport);

    /* SINCE SDK 21 */
    void serverDisconnect(int serverIf, String address);

    /* SINCE SDK 21 */
    void beginServiceDeclaration(int serverIf, int srvcType, int srvcInstanceId, int minHandles,
            ParcelUuid srvcId, boolean advertisePreferred);

    /* SINCE SDK 21 */
    void addIncludedService(int serverIf, int srvcType, int srvcInstanceId, ParcelUuid srvcId);

    /* SINCE SDK 21 */
    void addCharacteristic(int serverIf, ParcelUuid charId, int properties, int permissions);

    /* SINCE SDK 21 */
    void addDescriptor(int serverIf, ParcelUuid descId, int permissions);

    /* SINCE SDK 21 */
    void endServiceDeclaration(int serverIf);

    /* SINCE SDK 21 */
    void removeService(int serverIf, int srvcType, int srvcInstanceId, ParcelUuid srvcId);

    /* SINCE SDK 21 */
    void clearServices(int serverIf);

    /* SINCE SDK 21 */
    void sendResponse(int serverIf, String address, int requestId,
            int status, int offset, byte[] value);

    /* SINCE SDK 21 */
    void sendNotification(int serverIf, String address, int srvcType,
            int srvcInstanceId, ParcelUuid srvcId, int charInstanceId, ParcelUuid charId,
            boolean confirm, byte[] value);

    /* SINCE SDK 21 */
    void configureMTU(int clientIf, String address, int mtu);

    /* SINCE SDK 21 */
    void connectionParameterUpdate(int clientIf, String address, int connectionPriority);

    void disconnectAll();

    List<BluetoothDevice> getDevicesMatchingConnectionStates(int[] states);
}
