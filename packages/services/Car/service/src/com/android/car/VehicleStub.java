/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.car;

import android.annotation.Nullable;
import android.car.builtin.util.Slogf;
import android.hardware.automotive.vehicle.SubscribeOptions;
import android.os.RemoteException;
import android.os.ServiceSpecificException;

import com.android.car.hal.HalClientCallback;
import com.android.car.hal.HalPropConfig;
import com.android.car.hal.HalPropValue;
import com.android.car.hal.HalPropValueBuilder;

import java.io.FileDescriptor;
import java.util.ArrayList;

/**
 * VehicleStub represents an IVehicle service interface in either AIDL or legacy HIDL version. It
 * exposes common interface so that the client does not need to care about which version the
 * underlying IVehicle service is in.
 */
public abstract class VehicleStub {
    /**
     * SubscriptionClient represents a client that could subscribe/unsubscribe to properties.
     */
    public interface SubscriptionClient {
        /**
         * Subscribes to a property.
         *
         * @param options The list of subscribe options.
         * @throws RemoteException if the remote operation fails.
         * @throws ServiceSpecificException if VHAL returns service specific error.
         */
        void subscribe(SubscribeOptions[] options) throws RemoteException, ServiceSpecificException;

        /**
         * Unsubscribes from a property.
         *
         * @param prop The ID for the property to unsubscribe.
         * @throws RemoteException if the remote operation fails.
         * @throws ServiceSpecificException if VHAL returns service specific error.
         */
        void unsubscribe(int prop) throws RemoteException, ServiceSpecificException;
    }

    /**
     * Checks whether we are connected to AIDL VHAL: {@code true} or HIDL VHAL: {@code false}.
     */
    public abstract boolean isAidlVhal();

    /**
     * Creates a new VehicleStub to connect to Vehicle HAL.
     *
     * Create a new VehicleStub to connect to Vehicle HAL according to which backend (AIDL or HIDL)
     * is available. This function will throw {@link IllegalStateException} if no vehicle HAL is
     * available.
     *
     * @return a vehicle stub to connect to Vehicle HAL.
     */
    public static VehicleStub newVehicleStub() throws IllegalStateException {
        VehicleStub stub = new AidlVehicleStub();
        if (stub.isValid()) {
            return stub;
        }

        Slogf.i(CarLog.TAG_SERVICE, "No AIDL vehicle HAL found, fall back to HIDL version");

        stub = new HidlVehicleStub();

        if (!stub.isValid()) {
            throw new IllegalStateException("Vehicle HAL service is not available.");
        }

        return stub;
    }

    /**
     * Gets a HalPropValueBuilder that could be used to build a HalPropValue.
     *
     * @return a builder to build HalPropValue.
     */
    public abstract HalPropValueBuilder getHalPropValueBuilder();

    /**
     * Returns whether this vehicle stub is connecting to a valid vehicle HAL.
     *
     * @return Whether this vehicle stub is connecting to a valid vehicle HAL.
     */
    public abstract boolean isValid();

    /**
     * Gets the interface descriptor for the connecting vehicle HAL.
     *
     * @return the interface descriptor.
     * @throws IllegalStateException If unable to get the descriptor.
     */
    public abstract String getInterfaceDescriptor() throws IllegalStateException;

    /**
     * Registers a death recipient that would be called when vehicle HAL died.
     *
     * @param recipient A death recipient.
     * @throws IllegalStateException If unable to register the death recipient.
     */
    public abstract void linkToDeath(IVehicleDeathRecipient recipient) throws IllegalStateException;

    /**
     * Unlinks a previously linked death recipient.
     *
     * @param recipient A previously linked death recipient.
     */
    public abstract void unlinkToDeath(IVehicleDeathRecipient recipient);

    /**
     * Gets all property configs.
     *
     * @return All the property configs.
     * @throws RemoteException if the remote operation fails.
     * @throws ServiceSpecificException if VHAL returns service specific error.
     */
    public abstract HalPropConfig[] getAllPropConfigs()
            throws RemoteException, ServiceSpecificException;

    /**
     * Gets a new {@code SubscriptionClient} that could be used to subscribe/unsubscribe.
     *
     * @param callback A callback that could be used to receive events.
     * @return a {@code SubscriptionClient} that could be used to subscribe/unsubscribe.
     */
    public abstract SubscriptionClient newSubscriptionClient(HalClientCallback callback);

    /**
     * Gets a property.
     *
     * @param requestedPropValue The property to get.
     * @return The vehicle property value.
     * @throws RemoteException if the remote operation fails.
     * @throws ServiceSpecificException if VHAL returns service specific error.
     */
    @Nullable
    public abstract HalPropValue get(HalPropValue requestedPropValue)
            throws RemoteException, ServiceSpecificException;

    /**
     * Sets a property.
     *
     * @param propValue The property to set.
     * @throws RemoteException if the remote operation fails.
     * @throws ServiceSpecificException if VHAL returns service specific error.
     */
    public abstract void set(HalPropValue propValue)
            throws RemoteException, ServiceSpecificException;

    /**
     * Dump VHAL debug information.
     *
     * Additional arguments could also be provided through {@link args} to debug VHAL.
     *
     * @param fd The file descriptor to print output.
     * @param args Optional additional arguments for the debug command. Can be empty.
     * @throws RemoteException if the remote operation fails.
     * @throws ServiceSpecificException if VHAL returns service specific error.
     */
    public abstract void dump(FileDescriptor fd, ArrayList<String> args)
            throws RemoteException, ServiceSpecificException;
}
