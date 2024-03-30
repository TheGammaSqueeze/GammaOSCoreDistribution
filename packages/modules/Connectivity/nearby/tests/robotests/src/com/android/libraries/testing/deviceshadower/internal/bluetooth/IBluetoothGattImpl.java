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

package com.android.libraries.testing.deviceshadower.internal.bluetooth;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.IBluetoothGatt;
import android.bluetooth.IBluetoothGattCallback;
import android.bluetooth.IBluetoothGattServerCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanSettings;
import android.os.ParcelUuid;

import com.android.internal.annotations.VisibleForTesting;
import com.android.libraries.testing.deviceshadower.internal.DeviceShadowEnvironmentImpl;
import com.android.libraries.testing.deviceshadower.internal.bluetooth.GattDelegate.ReadCharacteristicRequest;
import com.android.libraries.testing.deviceshadower.internal.bluetooth.GattDelegate.ReadDescriptorRequest;
import com.android.libraries.testing.deviceshadower.internal.bluetooth.GattDelegate.Request;
import com.android.libraries.testing.deviceshadower.internal.common.NamedRunnable;
import com.android.libraries.testing.deviceshadower.internal.utils.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * Implementation of IBluetoothGatt.
 */
public class IBluetoothGattImpl implements IBluetoothGatt {

    private static final Logger LOGGER = Logger.create("IBluetoothGattImpl");
    private GattDelegate.Service mCurrentService;
    private GattDelegate.Characteristic mCurrentCharacteristic;

    @Override
    public void startScan(
            int appIf,
            boolean isServer,
            ScanSettings settings,
            List<ScanFilter> filters,
            List<?> scanStorages,
            String callingPackage) {
        localGattDelegate().startScan(appIf, settings, filters);
    }

    @Override
    public void startScan(
            int appIf,
            boolean isServer,
            ScanSettings settings,
            List<ScanFilter> filters,
            List<?> scanStorages) {
        startScan(appIf, isServer, settings, filters, scanStorages, "" /* callingPackage */);
    }

    @Override
    public void stopScan(int appIf, boolean isServer) {
        localGattDelegate().stopScan(appIf);
    }

    @Override
    public void startMultiAdvertising(
            int appIf,
            AdvertiseData advertiseData,
            AdvertiseData scanResponse,
            AdvertiseSettings settings) {
        localGattDelegate().startMultiAdvertising(appIf, advertiseData, scanResponse, settings);
    }

    @Override
    public void stopMultiAdvertising(int appIf) {
        localGattDelegate().stopMultiAdvertising(appIf);
    }

    @Override
    @SuppressWarnings("FutureReturnValueIgnored")
    public void registerClient(ParcelUuid appId, final IBluetoothGattCallback callback) {
        final int clientIf = localGattDelegate().registerClient(callback);
        NamedRunnable onClientRegistered =
                NamedRunnable.create(
                        "ClientGatt.onClientRegistered=" + clientIf,
                        () -> {
                            callback.onClientRegistered(BluetoothGatt.GATT_SUCCESS, clientIf);
                        });

        DeviceShadowEnvironmentImpl.runOnService(localAddress(), onClientRegistered);
    }

    @Override
    public void unregisterClient(int clientIf) {
        localGattDelegate().unregisterClient(clientIf);
    }

    @Override
    @SuppressWarnings("FutureReturnValueIgnored")
    public void clientConnect(
            final int clientIf, final String serverAddress, boolean isDirect, int transport) {
        // TODO(b/200231384): implement auto connect.
        String clientAddress = localAddress();
        int serverIf = remoteGattDelegate(serverAddress).getServerIf();
        boolean success = remoteGattDelegate(serverAddress).connect(clientAddress);
        if (!success) {
            LOGGER.i(String.format("clientConnect failed: %s connect %s", serverAddress,
                    clientAddress));
            return;
        }

        DeviceShadowEnvironmentImpl.runOnService(
                clientAddress,
                newClientConnectionStateChangeRunnable(clientIf, true, serverAddress));

        DeviceShadowEnvironmentImpl.runOnService(
                serverAddress,
                newServerConnectionStateChangeRunnable(serverIf, true, clientAddress));
    }

    @Override
    @SuppressWarnings("FutureReturnValueIgnored")
    public void clientDisconnect(final int clientIf, final String serverAddress) {
        final String clientAddress = localAddress();
        remoteGattDelegate(serverAddress).disconnect(clientAddress);
        int serverIf = remoteGattDelegate(serverAddress).getServerIf();


        DeviceShadowEnvironmentImpl.runOnService(
                clientAddress,
                newClientConnectionStateChangeRunnable(clientIf, false, serverAddress));

        DeviceShadowEnvironmentImpl.runOnService(
                serverAddress,
                newServerConnectionStateChangeRunnable(serverIf, false, clientAddress));
    }

    @Override
    public void discoverServices(int clientIf, String serverAddress) {
        final IBluetoothGattCallback callback = localGattDelegate().getClientCallback(clientIf);
        if (callback == null) {
            return;
        }
        for (GattDelegate.Service service : remoteGattDelegate(serverAddress).getServices()) {
            callback.onGetService(serverAddress, 0 /*srvcType*/, 0 /*srvcInstId*/,
                    service.getUuid());

            for (GattDelegate.Characteristic characteristic : service.getCharacteristics()) {
                callback.onGetCharacteristic(
                        serverAddress,
                        0 /*srvcType*/,
                        0 /*srvcInstId*/,
                        service.getUuid(),
                        0 /*charInstId*/,
                        characteristic.getUuid(),
                        characteristic.getProperties());
                for (GattDelegate.Descriptor descriptor : characteristic.getDescriptors()) {
                    callback.onGetDescriptor(
                            serverAddress,
                            0 /*srvcType*/,
                            0 /*srvcInstId*/,
                            service.getUuid(),
                            0 /*charInstId*/,
                            characteristic.getUuid(),
                            0 /*descrInstId*/,
                            descriptor.getUuid());
                }
            }
        }

        callback.onSearchComplete(serverAddress, BluetoothGatt.GATT_SUCCESS);
    }

    @Override
    @SuppressWarnings("FutureReturnValueIgnored")
    public void readCharacteristic(
            final int clientIf,
            final String serverAddress,
            final int srvcType,
            final int srvcInstId,
            final ParcelUuid srvcId,
            final int charInstId,
            final ParcelUuid charId,
            final int authReq) {
        // TODO(b/200231384): implement authReq.
        final String clientAddress = localAddress();
        localGattDelegate()
                .setLastRequest(
                        new ReadCharacteristicRequest(srvcType, srvcInstId, srvcId, charInstId,
                                charId));

        NamedRunnable serverOnCharacteristicReadRequest =
                NamedRunnable.create(
                        "ServerGatt.onCharacteristicReadRequest",
                        () -> {
                            int serverIf = localGattDelegate().getServerIf();
                            IBluetoothGattServerCallback callback =
                                    localGattDelegate().getServerCallback(serverIf);
                            if (callback != null) {
                                callback.onCharacteristicReadRequest(
                                        clientAddress,
                                        0 /*transId*/,
                                        0 /*offset*/,
                                        false /*isLong*/,
                                        0 /*srvcType*/,
                                        srvcInstId,
                                        srvcId,
                                        charInstId,
                                        charId);
                            }
                        });

        DeviceShadowEnvironmentImpl.runOnService(serverAddress, serverOnCharacteristicReadRequest);
    }

    @Override
    @SuppressWarnings("FutureReturnValueIgnored")
    public void writeCharacteristic(
            final int clientIf,
            final String serverAddress,
            final int srvcType,
            final int srvcInstId,
            final ParcelUuid srvcId,
            final int charInstId,
            final ParcelUuid charId,
            final int writeType,
            final int authReq,
            final byte[] value) {
        // TODO(b/200231384): implement write with response needed.
        remoteGattDelegate(serverAddress).getService(srvcId).getCharacteristic(charId)
                .setValue(value);
        final String clientAddress = localAddress();

        NamedRunnable clientOnCharacteristicWrite =
                NamedRunnable.create(
                        "ClientGatt.onCharacteristicWrite",
                        () -> {
                            IBluetoothGattCallback callback = localGattDelegate().getClientCallback(
                                    clientIf);
                            if (callback != null) {
                                callback.onCharacteristicWrite(
                                        serverAddress,
                                        BluetoothGatt.GATT_SUCCESS,
                                        0 /*srvcType*/,
                                        srvcInstId,
                                        srvcId,
                                        charInstId,
                                        charId);
                            }
                        });

        NamedRunnable onCharacteristicWriteRequest =
                NamedRunnable.create(
                        "ServerGatt.onCharacteristicWriteRequest",
                        () -> {
                            int serverIf = localGattDelegate().getServerIf();
                            IBluetoothGattServerCallback callback =
                                    localGattDelegate().getServerCallback(serverIf);
                            if (callback != null) {
                                callback.onCharacteristicWriteRequest(
                                        clientAddress,
                                        0 /*transId*/,
                                        0 /*offset*/,
                                        value.length,
                                        false /*isPrep*/,
                                        false /*needRsp*/,
                                        0 /*srvcType*/,
                                        srvcInstId,
                                        srvcId,
                                        charInstId,
                                        charId,
                                        value);
                            }
                        });

        DeviceShadowEnvironmentImpl.runOnService(clientAddress, clientOnCharacteristicWrite);

        DeviceShadowEnvironmentImpl.runOnService(serverAddress, onCharacteristicWriteRequest);
    }

    @Override
    @SuppressWarnings("FutureReturnValueIgnored")
    public void readDescriptor(
            final int clientIf,
            final String serverAddress,
            final int srvcType,
            final int srvcInstId,
            final ParcelUuid srvcId,
            final int charInstId,
            final ParcelUuid charId,
            final int descrInstId,
            final ParcelUuid descrId,
            final int authReq) {
        final String clientAddress = localAddress();
        localGattDelegate()
                .setLastRequest(
                        new ReadDescriptorRequest(
                                srvcType, srvcInstId, srvcId, charInstId, charId, descrInstId,
                                descrId));

        NamedRunnable serverOnDescriptorReadRequest =
                NamedRunnable.create(
                        "ServerGatt.onDescriptorReadRequest",
                        () -> {
                            int serverIf = localGattDelegate().getServerIf();
                            IBluetoothGattServerCallback callback =
                                    localGattDelegate().getServerCallback(serverIf);
                            if (callback != null) {
                                callback.onDescriptorReadRequest(
                                        clientAddress,
                                        0 /*transId*/,
                                        0 /*offset*/,
                                        false /*isLong*/,
                                        0 /*srvcType*/,
                                        srvcInstId,
                                        srvcId,
                                        charInstId,
                                        charId,
                                        descrId);
                            }
                        });

        DeviceShadowEnvironmentImpl.runOnService(serverAddress, serverOnDescriptorReadRequest);
    }

    @Override
    @SuppressWarnings("FutureReturnValueIgnored")
    public void writeDescriptor(
            final int clientIf,
            final String serverAddress,
            final int srvcType,
            final int srvcInstId,
            final ParcelUuid srvcId,
            final int charInstId,
            final ParcelUuid charId,
            final int descrInstId,
            final ParcelUuid descrId,
            final int writeType,
            final int authReq,
            final byte[] value) {
        // TODO(b/200231384): implement write with response needed.
        remoteGattDelegate(serverAddress)
                .getService(srvcId)
                .getCharacteristic(charId)
                .getDescriptor(descrId)
                .setValue(value);
        final String clientAddress = localAddress();

        NamedRunnable serverOnDescriptorWriteRequest =
                NamedRunnable.create(
                        "ServerGatt.onDescriptorWriteRequest",
                        () -> {
                            int serverIf = localGattDelegate().getServerIf();
                            IBluetoothGattServerCallback callback =
                                    localGattDelegate().getServerCallback(serverIf);
                            if (callback != null) {
                                callback.onDescriptorWriteRequest(
                                        clientAddress,
                                        0 /*transId*/,
                                        0 /*offset*/,
                                        value.length,
                                        false /*isPrep*/,
                                        false /*needRsp*/,
                                        0 /*srvcType*/,
                                        srvcInstId,
                                        srvcId,
                                        charInstId,
                                        charId,
                                        descrId,
                                        value);
                            }
                        });

        NamedRunnable clientOnDescriptorWrite =
                NamedRunnable.create(
                        "ClientGatt.onDescriptorWrite",
                        () -> {
                            IBluetoothGattCallback callback = localGattDelegate().getClientCallback(
                                    clientIf);
                            if (callback != null) {
                                callback.onDescriptorWrite(
                                        serverAddress,
                                        BluetoothGatt.GATT_SUCCESS,
                                        0 /*srvcType*/,
                                        srvcInstId,
                                        srvcId,
                                        charInstId,
                                        charId,
                                        descrInstId,
                                        descrId);
                            }
                        });

        DeviceShadowEnvironmentImpl.runOnService(serverAddress, serverOnDescriptorWriteRequest);

        DeviceShadowEnvironmentImpl.runOnService(clientAddress, clientOnDescriptorWrite);
    }

    @Override
    public void registerForNotification(
            int clientIf,
            String remoteAddress,
            int srvcType,
            int srvcInstId,
            ParcelUuid srvcId,
            int charInstId,
            ParcelUuid charId,
            boolean enable) {
        remoteGattDelegate(remoteAddress)
                .getService(srvcId)
                .getCharacteristic(charId)
                .registerNotification(localAddress(), clientIf);
    }

    @Override
    @SuppressWarnings("FutureReturnValueIgnored")
    public void registerServer(ParcelUuid appId, final IBluetoothGattServerCallback callback) {
        // TODO(b/200231384): support multiple serverIf.
        final int serverIf = localGattDelegate().registerServer(callback);
        NamedRunnable serverOnRegistered =
                NamedRunnable.create(
                        "ServerGatt.onServerRegistered",
                        () -> {
                            callback.onServerRegistered(BluetoothGatt.GATT_SUCCESS, serverIf);
                        });

        DeviceShadowEnvironmentImpl.runOnService(localAddress(), serverOnRegistered);
    }

    @Override
    public void unregisterServer(int serverIf) {
        localGattDelegate().unregisterServer(serverIf);
    }

    @Override
    @SuppressWarnings("FutureReturnValueIgnored")
    public void serverConnect(
            final int serverIf, final String clientAddress, boolean isDirect, int transport) {
        // TODO(b/200231384): implement isDirect and transport.
        boolean success = localGattDelegate().connect(clientAddress);
        final String serverAddress = localAddress();
        if (!success) {
            return;
        }
        int clientIf = remoteGattDelegate(clientAddress).getClientIf();

        DeviceShadowEnvironmentImpl.runOnService(
                serverAddress,
                newServerConnectionStateChangeRunnable(serverIf, true, clientAddress));

        DeviceShadowEnvironmentImpl.runOnService(
                clientAddress,
                newClientConnectionStateChangeRunnable(clientIf, true, serverAddress));
    }

    @Override
    @SuppressWarnings("FutureReturnValueIgnored")
    public void serverDisconnect(final int serverIf, final String clientAddress) {
        localGattDelegate().disconnect(clientAddress);
        String serverAddress = localAddress();
        int clientIf = remoteGattDelegate(clientAddress).getClientIf();

        DeviceShadowEnvironmentImpl.runOnService(
                serverAddress,
                newServerConnectionStateChangeRunnable(serverIf, false, clientAddress));

        DeviceShadowEnvironmentImpl.runOnService(
                clientAddress,
                newClientConnectionStateChangeRunnable(clientIf, false, serverAddress));
    }

    @Override
    public void beginServiceDeclaration(
            int serverIf,
            int srvcType,
            int srvcInstId,
            int minHandles,
            ParcelUuid srvcId,
            boolean advertisePreferred) {
        // TODO(b/200231384): support different service type, instanceId, advertisePreferred.
        mCurrentService = localGattDelegate().addService(srvcId);
    }

    @Override
    public void addIncludedService(int serverIf, int srvcType, int srvcInstId, ParcelUuid srvcId) {
        // TODO(b/200231384): implement this.
    }

    @Override
    public void addCharacteristic(int serverIf, ParcelUuid charId, int properties,
            int permissions) {
        mCurrentCharacteristic = mCurrentService.addCharacteristic(charId, properties, permissions);
    }

    @Override
    public void addDescriptor(int serverIf, ParcelUuid descId, int permissions) {
        mCurrentCharacteristic.addDescriptor(descId, permissions);
    }

    @Override
    public void endServiceDeclaration(int serverIf) {
        // TODO(b/200231384): choose correct srvc type and inst id.
        IBluetoothGattServerCallback callback = localGattDelegate().getServerCallback(serverIf);
        if (callback != null) {
            callback.onServiceAdded(
                    BluetoothGatt.GATT_SUCCESS, 0 /*srvcType*/, 0 /*srvcInstId*/,
                    mCurrentService.getUuid());
        }
        mCurrentService = null;
    }

    @Override
    public void removeService(int serverIf, int srvcType, int srvcInstId, ParcelUuid srvcId) {
        // TODO(b/200231384): implement remove service.
        // localGattDelegate().removeService(srvcId);
    }

    @Override
    public void clearServices(int serverIf) {
        // TODO(b/200231384): support multiple serverIf.
        // localGattDelegate().clearService();
    }

    @Override
    @SuppressWarnings("FutureReturnValueIgnored")
    public void sendResponse(
            int serverIf, String clientAddress, int requestId, int status, int offset,
            byte[] value) {
        // TODO(b/200231384): implement more operations.
        String serverAddress = localAddress();

        DeviceShadowEnvironmentImpl.runOnService(
                clientAddress,
                NamedRunnable.create(
                        "ClientGatt.receiveResponse",
                        () -> {
                            IBluetoothGattCallback callback =
                                    localGattDelegate().getClientCallback(
                                            localGattDelegate().getClientIf());
                            if (callback != null) {
                                Request request = localGattDelegate().getLastRequest();
                                localGattDelegate().setLastRequest(null);
                                if (request != null) {
                                    if (request instanceof ReadCharacteristicRequest) {
                                        callback.onCharacteristicRead(
                                                serverAddress,
                                                status,
                                                request.mSrvcType,
                                                request.mSrvcInstId,
                                                request.mSrvcId,
                                                request.mCharInstId,
                                                request.mCharId,
                                                value);
                                    } else if (request instanceof ReadDescriptorRequest) {
                                        ReadDescriptorRequest readDescriptorRequest =
                                                (ReadDescriptorRequest) request;
                                        callback.onDescriptorRead(
                                                serverAddress,
                                                status,
                                                readDescriptorRequest.mSrvcType,
                                                readDescriptorRequest.mSrvcInstId,
                                                readDescriptorRequest.mSrvcId,
                                                readDescriptorRequest.mCharInstId,
                                                readDescriptorRequest.mCharId,
                                                readDescriptorRequest.mDescrInstId,
                                                readDescriptorRequest.mDescrId,
                                                value);
                                    }
                                }
                            }
                        }));
    }

    @Override
    @SuppressWarnings("FutureReturnValueIgnored")
    public void sendNotification(
            final int serverIf,
            final String address,
            final int srvcType,
            final int srvcInstId,
            final ParcelUuid srvcId,
            final int charInstId,
            final ParcelUuid charId,
            boolean confirm,
            final byte[] value) {
        GattDelegate.Characteristic characteristic =
                localGattDelegate().getService(srvcId).getCharacteristic(charId);
        characteristic.setValue(value);
        final String serverAddress = localAddress();
        for (final String clientAddress : characteristic.getNotifyClients()) {
            NamedRunnable clientOnNotify =
                    NamedRunnable.create(
                            "ClientGatt.onNotify",
                            () -> {
                                int clientIf = localGattDelegate().getClientIf();
                                IBluetoothGattCallback callback =
                                        localGattDelegate().getClientCallback(clientIf);
                                if (callback != null) {
                                    callback.onNotify(
                                            serverAddress, srvcType, srvcInstId, srvcId, charInstId,
                                            charId, value);
                                }
                            });

            DeviceShadowEnvironmentImpl.runOnService(clientAddress, clientOnNotify);
        }

        NamedRunnable serverOnNotificationSent =
                NamedRunnable.create(
                        "ServerGatt.onNotificationSent",
                        () -> {
                            IBluetoothGattServerCallback callback =
                                    localGattDelegate().getServerCallback(serverIf);
                            if (callback != null) {
                                callback.onNotificationSent(address, BluetoothGatt.GATT_SUCCESS);
                            }
                        });

        DeviceShadowEnvironmentImpl.runOnService(serverAddress, serverOnNotificationSent);
    }

    @Override
    @SuppressWarnings("FutureReturnValueIgnored")
    public void configureMTU(int clientIf, String address, int mtu) {
        final String clientAddress = localAddress();

        NamedRunnable clientSetMtu =
                NamedRunnable.create(
                        "ClientGatt.setMtu",
                        () -> {
                            localGattDelegate().clientSetMtu(clientIf, mtu, address);
                        });
        NamedRunnable serverSetMtu =
                NamedRunnable.create(
                        "ServerGatt.setMtu",
                        () -> {
                            int serverIf = localGattDelegate().getServerIf();
                            localGattDelegate().serverSetMtu(serverIf, mtu, clientAddress);
                        });

        DeviceShadowEnvironmentImpl.runOnService(clientAddress, clientSetMtu);

        DeviceShadowEnvironmentImpl.runOnService(address, serverSetMtu);
    }

    @Override
    public void connectionParameterUpdate(int clientIf, String address, int connectionPriority) {
        // TODO(b/200231384): Implement.
    }

    @Override
    public void disconnectAll() {
    }

    @Override
    public List<BluetoothDevice> getDevicesMatchingConnectionStates(int[] states) {
        return new ArrayList<>();
    }

    @VisibleForTesting
    static GattDelegate remoteGattDelegate(String address) {
        return DeviceShadowEnvironmentImpl.getBlueletImpl(address).getGattDelegate();
    }

    private static GattDelegate localGattDelegate() {
        return DeviceShadowEnvironmentImpl.getLocalBlueletImpl().getGattDelegate();
    }

    private static String localAddress() {
        return DeviceShadowEnvironmentImpl.getLocalBlueletImpl().address;
    }

    private static NamedRunnable newClientConnectionStateChangeRunnable(
            final int clientIf, final boolean isConnected, final String serverAddress) {
        return NamedRunnable.create(
                "ClientGatt.clientConnectionStateChange",
                () -> {
                    localGattDelegate()
                            .clientConnectionStateChange(
                                    BluetoothGatt.GATT_SUCCESS, clientIf, isConnected,
                                    serverAddress);
                });
    }

    private static NamedRunnable newServerConnectionStateChangeRunnable(
            final int serverIf, final boolean isConnected, final String clientAddress) {
        return NamedRunnable.create(
                "ServerGatt.serverConnectionStateChange",
                () -> {
                    localGattDelegate()
                            .serverConnectionStateChange(
                                    BluetoothGatt.GATT_SUCCESS, serverIf, isConnected,
                                    clientAddress);
                });
    }
}
