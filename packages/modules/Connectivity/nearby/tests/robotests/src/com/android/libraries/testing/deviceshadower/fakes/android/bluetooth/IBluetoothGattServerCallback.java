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

import android.os.ParcelUuid;

/**
 * Fake interface of internal IBluetoothGattServerCallback.
 */
public interface IBluetoothGattServerCallback {

    /* SINCE SDK 21 */
    void onServerRegistered(int status, int serverIf);

    /* SINCE SDK 21 */
    void onScanResult(String address, int rssi, byte[] advData);

    /* SINCE SDK 21 */
    void onServerConnectionState(int status, int serverIf, boolean connected, String address);

    /* SINCE SDK 21 */
    void onServiceAdded(int status, int srvcType, int srvcInstId, ParcelUuid srvcId);

    /* SINCE SDK 21 */
    void onCharacteristicReadRequest(String address, int transId, int offset, boolean isLong,
            int srvcType, int srvcInstId, ParcelUuid srvcId, int charInstId, ParcelUuid charId);

    /* SINCE SDK 21 */
    void onDescriptorReadRequest(String address, int transId, int offset, boolean isLong,
            int srvcType, int srvcInstId, ParcelUuid srvcId,
            int charInstId, ParcelUuid charId, ParcelUuid descrId);

    /* SINCE SDK 21 */
    void onCharacteristicWriteRequest(String address, int transId, int offset, int length,
            boolean isPrep, boolean needRsp, int srvcType, int srvcInstId, ParcelUuid srvcId,
            int charInstId, ParcelUuid charId, byte[] value);

    /* SINCE SDK 21 */
    void onDescriptorWriteRequest(String address, int transId, int offset, int length,
            boolean isPrep, boolean needRsp, int srvcType, int srvcInstId, ParcelUuid srvcId,
            int charInstId, ParcelUuid charId, ParcelUuid descrId, byte[] value);

    /* SINCE SDK 21 */
    void onExecuteWrite(String address, int transId, boolean execWrite);

    /* SINCE SDK 21 */
    void onNotificationSent(String address, int status);

    /* SINCE SDK 22 */
    void onMtuChanged(String address, int mtu);

}
