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

import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.ScanResult;
import android.os.ParcelUuid;

/**
 * Fake interface replacement for IBluetoothGattCallback
 * TODO(b/200231384): include >=N interface.
 */
public interface IBluetoothGattCallback {

    /* SINCE SDK 21 */
    void onClientRegistered(int status, int clientIf);

    /* SINCE SDK 21 */
    void onClientConnectionState(int status, int clientIf, boolean connected, String address);

    /* ONLY SDK 19 */
    void onScanResult(String address, int rssi, byte[] advData);

    /* SINCE SDK 21 */
    void onScanResult(ScanResult scanResult);

    /* SINCE SDK 21 */
    void onGetService(String address, int srvcType, int srvcInstId, ParcelUuid srvcUuid);

    /* SINCE SDK 21 */
    void onGetIncludedService(String address, int srvcType, int srvcInstId,
            ParcelUuid srvcUuid, int inclSrvcType,
            int inclSrvcInstId, ParcelUuid inclSrvcUuid);

    /* SINCE SDK 21 */
    void onGetCharacteristic(String address, int srvcType,
            int srvcInstId, ParcelUuid srvcUuid,
            int charInstId, ParcelUuid charUuid,
            int charProps);

    /* SINCE SDK 21 */
    void onGetDescriptor(String address, int srvcType,
            int srvcInstId, ParcelUuid srvcUuid,
            int charInstId, ParcelUuid charUuid,
            int descrInstId, ParcelUuid descrUuid);

    /* SINCE SDK 21 */
    void onSearchComplete(String address, int status);

    /* SINCE SDK 21 */
    void onCharacteristicRead(String address, int status, int srvcType,
            int srvcInstId, ParcelUuid srvcUuid,
            int charInstId, ParcelUuid charUuid,
            byte[] value);

    /* SINCE SDK 21 */
    void onCharacteristicWrite(String address, int status, int srvcType,
            int srvcInstId, ParcelUuid srvcUuid,
            int charInstId, ParcelUuid charUuid);

    /* SINCE SDK 21 */
    void onExecuteWrite(String address, int status);

    /* SINCE SDK 21 */
    void onDescriptorRead(String address, int status, int srvcType,
            int srvcInstId, ParcelUuid srvcUuid,
            int charInstId, ParcelUuid charUuid,
            int descrInstId, ParcelUuid descrUuid,
            byte[] value);

    /* SINCE SDK 21 */
    void onDescriptorWrite(String address, int status, int srvcType,
            int srvcInstId, ParcelUuid srvcUuid,
            int charInstId, ParcelUuid charUuid,
            int descrInstId, ParcelUuid descrUuid);

    /* SINCE SDK 21 */
    void onNotify(String address, int srvcType,
            int srvcInstId, ParcelUuid srvcUuid,
            int charInstId, ParcelUuid charUuid,
            byte[] value);

    /* SINCE SDK 21 */
    void onReadRemoteRssi(String address, int rssi, int status);

    /* SDK 21 */
    void onMultiAdvertiseCallback(int status, boolean isStart,
            AdvertiseSettings advertiseSettings);

    /* SDK 21 */
    void onConfigureMTU(String address, int mtu, int status);
}
