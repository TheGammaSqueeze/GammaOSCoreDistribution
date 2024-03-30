/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.nearby.fastpair.provider.bluetooth;

import android.annotation.TargetApi;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;

import androidx.annotation.Nullable;

import com.android.server.nearby.common.bluetooth.BluetoothConsts;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;

/** Configuration of a GATT server. */
@TargetApi(18)
public class BluetoothGattServerConfig {
    private final Map<UUID, ServiceConfig> mServiceConfigs = new HashMap<UUID, ServiceConfig>();

    @Nullable
    private BluetoothGattServerHelper.Listener mServerlistener = null;

    public BluetoothGattServerConfig addService(UUID uuid, ServiceConfig serviceConfig) {
        mServiceConfigs.put(uuid, serviceConfig);
        return this;
    }

    public BluetoothGattServerConfig setServerConnectionListener(
            BluetoothGattServerHelper.Listener listener) {
        mServerlistener = listener;
        return this;
    }

    @Nullable
    public BluetoothGattServerHelper.Listener getServerListener() {
        return mServerlistener;
    }

    /**
     * Adds a service and a characteristic to indicate that the server has dynamic services.
     * This is a workaround for b/21587710.
     * TODO(lingjunl): remove them when b/21587710 is fixed.
     */
    public BluetoothGattServerConfig addSelfDefinedDynamicService() {
        ServiceConfig serviceConfig = new ServiceConfig().addCharacteristic(
                new BluetoothGattServlet() {
                    @Override
                    public BluetoothGattCharacteristic getCharacteristic() {
                        return new BluetoothGattCharacteristic(
                                BluetoothConsts.SERVICE_DYNAMIC_CHARACTERISTIC,
                                BluetoothGattCharacteristic.PROPERTY_READ,
                                BluetoothGattCharacteristic.PERMISSION_READ);
                    }
                });
        return addService(BluetoothConsts.SERVICE_DYNAMIC_SERVICE, serviceConfig);
    }

    public List<BluetoothGattService> getBluetoothGattServices() {
        List<BluetoothGattService> result = new ArrayList<BluetoothGattService>();
        for (Entry<UUID, ServiceConfig> serviceEntry : mServiceConfigs.entrySet()) {
            UUID serviceUuid = serviceEntry.getKey();
            ServiceConfig serviceConfig = serviceEntry.getValue();
            if (serviceUuid == null || serviceConfig == null) {
                // This is not supposed to happen
                throw new IllegalStateException();
            }
            BluetoothGattService gattService = new BluetoothGattService(serviceUuid,
                    BluetoothGattService.SERVICE_TYPE_PRIMARY);
            for (Entry<BluetoothGattCharacteristic, BluetoothGattServlet> servletEntry :
                    serviceConfig.getServlets().entrySet()) {
                BluetoothGattCharacteristic characteristic = servletEntry.getKey();
                if (characteristic == null) {
                    // This is not supposed to happen
                    throw new IllegalStateException();
                }
                gattService.addCharacteristic(characteristic);
            }
            result.add(gattService);
        }
        return result;
    }

    public List<UUID> getAdvertisedUuids() {
        List<UUID> result = new ArrayList<UUID>();
        for (Entry<UUID, ServiceConfig> serviceEntry : mServiceConfigs.entrySet()) {
            UUID serviceUuid = serviceEntry.getKey();
            ServiceConfig serviceConfig = serviceEntry.getValue();
            if (serviceUuid == null || serviceConfig == null) {
                // This is not supposed to happen
                throw new IllegalStateException();
            }
            if (serviceConfig.isAdvertised()) {
                result.add(serviceUuid);
            }
        }
        return result;
    }

    public Map<BluetoothGattCharacteristic, BluetoothGattServlet> getServlets() {
        Map<BluetoothGattCharacteristic, BluetoothGattServlet> result =
                new HashMap<BluetoothGattCharacteristic, BluetoothGattServlet>();
        for (ServiceConfig serviceConfig : mServiceConfigs.values()) {
            result.putAll(serviceConfig.getServlets());
        }
        return result;
    }

    /** Configuration of a GATT service. */
    public static class ServiceConfig {
        private final Map<BluetoothGattCharacteristic, BluetoothGattServlet> mServlets =
                new HashMap<BluetoothGattCharacteristic, BluetoothGattServlet>();
        private boolean mAdvertise = false;

        public ServiceConfig addCharacteristic(BluetoothGattServlet servlet) {
            mServlets.put(servlet.getCharacteristic(), servlet);
            return this;
        }

        public ServiceConfig setAdvertise(boolean advertise) {
            mAdvertise = advertise;
            return this;
        }

        public Map<BluetoothGattCharacteristic, BluetoothGattServlet> getServlets() {
            return mServlets;
        }

        public boolean isAdvertised() {
            return mAdvertise;
        }
    }
}
