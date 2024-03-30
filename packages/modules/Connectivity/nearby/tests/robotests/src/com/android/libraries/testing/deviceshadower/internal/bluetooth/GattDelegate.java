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

import android.annotation.Nullable;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.IBluetoothGattCallback;
import android.bluetooth.IBluetoothGattServerCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.os.Build;
import android.os.Build.VERSION;
import android.os.ParcelUuid;
import android.os.SystemClock;

import com.android.internal.annotations.VisibleForTesting;
import com.android.libraries.testing.deviceshadower.internal.DeviceShadowEnvironmentImpl;
import com.android.libraries.testing.deviceshadower.internal.DeviceletImpl;
import com.android.libraries.testing.deviceshadower.internal.utils.GattHelper;
import com.android.libraries.testing.deviceshadower.internal.utils.Logger;

import com.google.common.base.Preconditions;
import com.google.common.primitives.Bytes;

import org.robolectric.util.ReflectionHelpers;
import org.robolectric.util.ReflectionHelpers.ClassParameter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Delegate to operate gatt operations.
 */
public class GattDelegate {

    private static final int DEFAULT_RSSI = -50;
    private static final Logger LOGGER = Logger.create("GattDelegate");

    // chipset properties
    // use 2 as API 21 requires multi-advertisement support to use Le Advertising.
    private final int mMaxAdvertiseInstances = 2;
    private final AtomicBoolean mIsOffloadedFilteringSupported = new AtomicBoolean(false);
    private final String mAddress;
    private final AtomicInteger mCurrentClientIf = new AtomicInteger(0);
    private final AtomicInteger mCurrentServerIf = new AtomicInteger(0);
    private final AtomicBoolean mCurrentConnectionState = new AtomicBoolean(false);
    private final Map<ParcelUuid, Service> mServices = new HashMap<>();
    private final Map<Integer, IBluetoothGattCallback> mClientCallbacks;
    private final Map<Integer, IBluetoothGattServerCallback> mServerCallbacks;
    private final Map<Integer, Advertiser> mAdvertisers;
    private final Map<Integer, Scanner> mScanners;
    @Nullable
    private Request mLastRequest;
    private boolean mConnectable = true;

    /**
     * The parameters of a request, e.g. readCharacteristic(). Subclass for each request.
     *
     * @see #getLastRequest()
     */
    abstract static class Request {

        final int mSrvcType;
        final int mSrvcInstId;
        final ParcelUuid mSrvcId;
        final int mCharInstId;
        final ParcelUuid mCharId;

        Request(int srvcType, int srvcInstId, ParcelUuid srvcId, int charInstId,
                ParcelUuid charId) {
            this.mSrvcType = srvcType;
            this.mSrvcInstId = srvcInstId;
            this.mSrvcId = srvcId;
            this.mCharInstId = charInstId;
            this.mCharId = charId;
        }
    }

    /**
     * Corresponds to {@link android.bluetooth.IBluetoothGatt#readCharacteristic}.
     */
    static class ReadCharacteristicRequest extends Request {

        ReadCharacteristicRequest(
                int srvcType, int srvcInstId, ParcelUuid srvcId, int charInstId,
                ParcelUuid charId) {
            super(srvcType, srvcInstId, srvcId, charInstId, charId);
        }
    }

    /**
     * Corresponds to {@link android.bluetooth.IBluetoothGatt#readDescriptor}.
     */
    static class ReadDescriptorRequest extends Request {

        final int mDescrInstId;
        final ParcelUuid mDescrId;

        ReadDescriptorRequest(
                int srvcType,
                int srvcInstId,
                ParcelUuid srvcId,
                int charInstId,
                ParcelUuid charId,
                int descrInstId,
                ParcelUuid descrId) {
            super(srvcType, srvcInstId, srvcId, charInstId, charId);
            this.mDescrInstId = descrInstId;
            this.mDescrId = descrId;
        }
    }

    GattDelegate(String address) {
        this(
                address,
                new HashMap<>(),
                new HashMap<>(),
                new ConcurrentHashMap<>(),
                new ConcurrentHashMap<>());
    }

    @VisibleForTesting
    GattDelegate(
            String address,
            Map<Integer, IBluetoothGattCallback> clientCallbacks,
            Map<Integer, IBluetoothGattServerCallback> serverCallbacks,
            Map<Integer, Advertiser> advertisers,
            Map<Integer, Scanner> scanners) {
        this.mAddress = address;
        this.mClientCallbacks = clientCallbacks;
        this.mServerCallbacks = serverCallbacks;
        this.mAdvertisers = advertisers;
        this.mScanners = scanners;
    }

    public void setRefuseConnections(boolean refuse) {
        this.mConnectable = !refuse;
    }

    /**
     * Used to maintain state between the request (e.g. readCharacteristic()) and sendResponse().
     */
    @Nullable
    Request getLastRequest() {
        return mLastRequest;
    }

    /**
     * @see #getLastRequest()
     */
    void setLastRequest(@Nullable Request params) {
        mLastRequest = params;
    }

    public int getClientIf() {
        // TODO(b/200231384): support multiple client if.
        return mCurrentClientIf.get();
    }

    public int getServerIf() {
        // TODO(b/200231384): support multiple server if.
        return mCurrentServerIf.get();
    }

    public IBluetoothGattServerCallback getServerCallback(int serverIf) {
        return mServerCallbacks.get(serverIf);
    }

    public IBluetoothGattCallback getClientCallback(int clientIf) {
        return mClientCallbacks.get(clientIf);
    }

    public int registerServer(IBluetoothGattServerCallback callback) {
        mServerCallbacks.put(mCurrentServerIf.incrementAndGet(), callback);
        return getServerIf();
    }

    public int registerClient(IBluetoothGattCallback callback) {
        mClientCallbacks.put(mCurrentClientIf.incrementAndGet(), callback);
        LOGGER.d(String.format("Client registered on %s, clientIf: %d", mAddress, getClientIf()));
        return getClientIf();
    }

    public void unregisterClient(int clientIf) {
        mClientCallbacks.remove(clientIf);
        LOGGER.d(String.format("Client unregistered on %s, clientIf: %d", mAddress, clientIf));
    }

    public void unregisterServer(int serverIf) {
        mServerCallbacks.remove(serverIf);
    }

    public int getMaxAdvertiseInstances() {
        return mMaxAdvertiseInstances;
    }

    public boolean isOffloadedFilteringSupported() {
        return mIsOffloadedFilteringSupported.get();
    }

    public boolean connect(String address) {
        return mConnectable;
    }

    public boolean disconnect(String address) {
        return true;
    }

    public void clientConnectionStateChange(
            int state, int clientIf, boolean connected, String address) {
        if (connected != mCurrentConnectionState.get()) {
            mCurrentConnectionState.set(connected);
            IBluetoothGattCallback callback = getClientCallback(clientIf);
            if (callback != null) {
                callback.onClientConnectionState(state, clientIf, connected, address);
            }
        }
    }

    public void serverConnectionStateChange(
            int state, int serverIf, boolean connected, String address) {
        if (connected != mCurrentConnectionState.get()) {
            mCurrentConnectionState.set(connected);
            IBluetoothGattServerCallback callback = getServerCallback(serverIf);
            if (callback != null) {
                callback.onServerConnectionState(state, serverIf, connected, address);
            }
        }
    }

    public Service addService(ParcelUuid uuid) {
        Service srvc = new Service(uuid);
        mServices.put(uuid, srvc);
        return srvc;
    }

    public Collection<Service> getServices() {
        return mServices.values();
    }

    public Service getService(ParcelUuid uuid) {
        return mServices.get(uuid);
    }

    public void clientSetMtu(int clientIf, int mtu, String serverAddress) {
        IBluetoothGattCallback callback = getClientCallback(clientIf);
        if (callback != null && Build.VERSION.SDK_INT >= 21) {
            callback.onConfigureMTU(serverAddress, mtu, BluetoothGatt.GATT_SUCCESS);
        }
    }

    public void serverSetMtu(int serverIf, int mtu, String clientAddress) {
        IBluetoothGattServerCallback callback = getServerCallback(serverIf);
        if (callback != null && Build.VERSION.SDK_INT >= 22) {
            callback.onMtuChanged(clientAddress, mtu);
        }
    }

    public void startMultiAdvertising(
            int appIf,
            AdvertiseData advertiseData,
            AdvertiseData scanResponse,
            final AdvertiseSettings settings) {
        LOGGER.d(String.format("startMultiAdvertising(%d) on %s", appIf, mAddress));
        final Advertiser advertiser =
                new Advertiser(
                        appIf,
                        mAddress,
                        DeviceShadowEnvironmentImpl.getLocalBlueletImpl().mName,
                        txPowerFromFlag(settings.getTxPowerLevel()),
                        advertiseData,
                        scanResponse,
                        settings);
        mAdvertisers.put(appIf, advertiser);
        final IBluetoothGattCallback callback = mClientCallbacks.get(appIf);
        @SuppressWarnings("unused") // go/futurereturn-lsc
        Future<?> possiblyIgnoredError =
                DeviceShadowEnvironmentImpl.run(
                        mAddress,
                        () -> {
                            callback.onMultiAdvertiseCallback(
                                    BluetoothConstants.ADVERTISE_SUCCESS, true /* isStart */,
                                    settings);
                            return null;
                        });
    }

    /**
     * Returns TxPower in dBm as measured at the source.
     *
     * <p>Note that this will vary by device and the values are only roughly accurate. The
     * measurements were taken with a Nexus 6. Copied from the TxEddystone-UID app:
     * {https://github.com/google/eddystone/blob/master/eddystone-uid/tools/txeddystone-uid/TxEddystone-UID/app/src/main/java/com/google/sample/txeddystone_uid/MainActivity.java}
     */
    private static byte txPowerFromFlag(int txPowerFlag) {
        switch (txPowerFlag) {
            case AdvertiseSettings.ADVERTISE_TX_POWER_HIGH:
                return (byte) -16;
            case AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM:
                return (byte) -26;
            case AdvertiseSettings.ADVERTISE_TX_POWER_LOW:
                return (byte) -35;
            case AdvertiseSettings.ADVERTISE_TX_POWER_ULTRA_LOW:
                return (byte) -59;
            default:
                throw new IllegalStateException("Unknown TxPower level=" + txPowerFlag);
        }
    }

    public void stopMultiAdvertising(int appIf) {
        LOGGER.d(String.format("stopAdvertising(%d) on %s", appIf, mAddress));
        Advertiser advertiser = mAdvertisers.get(appIf);
        if (advertiser == null) {
            LOGGER.d(String.format("Advertising already stopped on %s, clientIf: %d", mAddress,
                    appIf));
            return;
        }
        mAdvertisers.remove(appIf);
        final IBluetoothGattCallback callback = mClientCallbacks.get(appIf);
        @SuppressWarnings("unused") // go/futurereturn-lsc
        Future<?> possiblyIgnoredError =
                DeviceShadowEnvironmentImpl.run(
                        mAddress,
                        () -> {
                            callback.onMultiAdvertiseCallback(
                                    BluetoothConstants.ADVERTISE_SUCCESS, false /* isStart */,
                                    null /* setting */);
                            return null;
                        });
    }

    public void startScan(final int appIf, ScanSettings settings, List<ScanFilter> filters) {
        LOGGER.d(String.format("startScan(%d) on %s", appIf, mAddress));
        if (filters == null) {
            filters = new ArrayList<>();
        }
        final Scanner scanner = new Scanner(appIf, settings, filters);
        mScanners.put(appIf, scanner);
        @SuppressWarnings("unused") // go/futurereturn-lsc
        Future<?> possiblyIgnoredError =
                DeviceShadowEnvironmentImpl.run(
                        mAddress,
                        () -> {
                            try {
                                scan(scanner);
                            } catch (InterruptedException e) {
                                LOGGER.e(
                                        String.format("Failed to scan on %s, clientIf: %d.",
                                                mAddress, scanner.mClientIf),
                                        e);
                            }
                            return null;
                        });
    }

    // TODO(b/200231384): support periodic scan with interval and scan window.
    private void scan(Scanner scanner) throws InterruptedException {
        // fetch existing advertisements
        List<DeviceletImpl> devicelets = DeviceShadowEnvironmentImpl.getDeviceletImpls();
        for (DeviceletImpl devicelet : devicelets) {
            BlueletImpl bluelet = devicelet.blueletImpl();
            if (bluelet.address.equals(mAddress)) {
                continue;
            }
            for (Advertiser advertiser : bluelet.getGattDelegate().mAdvertisers.values()) {
                if (VERSION.SDK_INT < 21) {
                    throw new UnsupportedOperationException(
                            String.format("API %d is not supported.", VERSION.SDK_INT));
                }

                byte[] advertiseData =
                        GattHelper.convertAdvertiseData(
                                advertiser.mAdvertiseData,
                                advertiser.mTxPowerLevel,
                                advertiser.mName,
                                advertiser.mSettings.isConnectable());
                byte[] scanResponse =
                        GattHelper.convertAdvertiseData(
                                advertiser.mScanResponse,
                                advertiser.mTxPowerLevel,
                                advertiser.mName,
                                advertiser.mSettings.isConnectable());

                ScanRecord scanRecord =
                        ReflectionHelpers.callStaticMethod(
                                ScanRecord.class,
                                "parseFromBytes",
                                ClassParameter.from(byte[].class,
                                        Bytes.concat(advertiseData, scanResponse)));
                ScanResult scanResult =
                        new ScanResult(
                                BluetoothAdapter.getDefaultAdapter()
                                        .getRemoteDevice(advertiser.mAddress),
                                scanRecord,
                                DEFAULT_RSSI,
                                SystemClock.elapsedRealtimeNanos());

                if (!matchFilters(scanResult, scanner.mFilters)) {
                    continue;
                }

                IBluetoothGattCallback callback = mClientCallbacks.get(scanner.mClientIf);
                if (callback == null) {
                    LOGGER.e(
                            String.format("Callback is null on %s, clientIf: %d", mAddress,
                                    scanner.mClientIf));
                    return;
                }
                callback.onScanResult(scanResult);
            }
        }
    }

    private boolean matchFilters(ScanResult scanResult, List<ScanFilter> filters) {
        for (ScanFilter filter : filters) {
            if (!filter.matches(scanResult)) {
                return false;
            }
        }
        return true;
    }

    public void stopScan(int appIf) {
        LOGGER.d(String.format("stopScan(%d) on %s", appIf, mAddress));
        Scanner scanner = mScanners.get(appIf);
        if (scanner == null) {
            LOGGER.d(
                    String.format("Scanning already stopped on %s, clientIf: %d", mAddress, appIf));
            return;
        }
        mScanners.remove(appIf);
    }

    static class Service {

        private Map<ParcelUuid, Characteristic> mCharacteristics = new HashMap<>();
        private ParcelUuid mUuid;

        Service(ParcelUuid uuid) {
            this.mUuid = uuid;
        }

        Characteristic getCharacteristic(ParcelUuid uuid) {
            return mCharacteristics.get(uuid);
        }

        Characteristic addCharacteristic(ParcelUuid uuid, int properties, int permissions) {
            Characteristic ch = new Characteristic(uuid, properties, permissions);
            mCharacteristics.put(uuid, ch);
            return ch;
        }

        Collection<Characteristic> getCharacteristics() {
            return mCharacteristics.values();
        }

        ParcelUuid getUuid() {
            return this.mUuid;
        }
    }

    static class Characteristic {

        private int mProperties;
        private ParcelUuid mUuid;
        private Map<ParcelUuid, Descriptor> mDescriptors = new HashMap<>();
        private Set<String> mNotifyClients = new HashSet<>();
        private byte[] mValue;

        Characteristic(ParcelUuid uuid, int properties, int permissions) {
            this.mProperties = properties;
            this.mUuid = uuid;
        }

        Descriptor getDescriptor(ParcelUuid uuid) {
            return mDescriptors.get(uuid);
        }

        Descriptor addDescriptor(ParcelUuid uuid, int permissions) {
            Descriptor desc = new Descriptor(uuid, permissions);
            mDescriptors.put(uuid, desc);
            return desc;
        }

        Collection<Descriptor> getDescriptors() {
            return mDescriptors.values();
        }

        void setValue(byte[] value) {
            this.mValue = value;
        }

        byte[] getValue() {
            return mValue;
        }

        ParcelUuid getUuid() {
            return mUuid;
        }

        int getProperties() {
            return mProperties;
        }

        void registerNotification(String client, int clientIf) {
            mNotifyClients.add(client);
        }

        Set<String> getNotifyClients() {
            return mNotifyClients;
        }
    }

    static class Descriptor {

        int mPermissions;
        ParcelUuid mUuid;
        byte[] mValue;

        Descriptor(ParcelUuid uuid, int permissions) {
            this.mUuid = uuid;
            this.mPermissions = permissions;
        }

        void setValue(byte[] value) {
            this.mValue = value;
        }

        byte[] getValue() {
            return mValue;
        }

        ParcelUuid getUuid() {
            return mUuid;
        }
    }

    @VisibleForTesting
    static class Advertiser {

        final int mClientIf;
        final String mAddress;
        final String mName;
        final int mTxPowerLevel;
        final AdvertiseData mAdvertiseData;
        @Nullable
        final AdvertiseData mScanResponse;
        final AdvertiseSettings mSettings;

        Advertiser(
                int clientIf,
                String address,
                String name,
                int txPowerLevel,
                AdvertiseData advertiseData,
                AdvertiseData scanResponse,
                AdvertiseSettings settings) {
            this.mClientIf = clientIf;
            this.mAddress = Preconditions.checkNotNull(address);
            this.mName = name;
            this.mTxPowerLevel = txPowerLevel;
            this.mAdvertiseData = Preconditions.checkNotNull(advertiseData);
            this.mScanResponse = scanResponse;
            this.mSettings = Preconditions.checkNotNull(settings);
        }
    }

    @VisibleForTesting
    static class Scanner {

        final int mClientIf;
        final ScanSettings mSettings;
        final List<ScanFilter> mFilters;

        Scanner(int clientIf, ScanSettings settings, List<ScanFilter> filters) {
            this.mClientIf = clientIf;
            this.mSettings = Preconditions.checkNotNull(settings);
            this.mFilters = Preconditions.checkNotNull(filters);
        }
    }
}
