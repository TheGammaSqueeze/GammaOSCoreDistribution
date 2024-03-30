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

package com.android.car.hal.test;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static junit.framework.Assert.fail;

import static java.lang.Integer.toHexString;

import android.hardware.automotive.vehicle.GetValueRequest;
import android.hardware.automotive.vehicle.GetValueRequests;
import android.hardware.automotive.vehicle.GetValueResult;
import android.hardware.automotive.vehicle.GetValueResults;
import android.hardware.automotive.vehicle.IVehicle;
import android.hardware.automotive.vehicle.IVehicleCallback;
import android.hardware.automotive.vehicle.SetValueRequest;
import android.hardware.automotive.vehicle.SetValueRequests;
import android.hardware.automotive.vehicle.SetValueResult;
import android.hardware.automotive.vehicle.SetValueResults;
import android.hardware.automotive.vehicle.StatusCode;
import android.hardware.automotive.vehicle.SubscribeOptions;
import android.hardware.automotive.vehicle.VehiclePropConfig;
import android.hardware.automotive.vehicle.VehiclePropConfigs;
import android.hardware.automotive.vehicle.VehiclePropError;
import android.hardware.automotive.vehicle.VehiclePropErrors;
import android.hardware.automotive.vehicle.VehiclePropValue;
import android.hardware.automotive.vehicle.VehiclePropValues;
import android.hardware.automotive.vehicle.VehiclePropertyAccess;
import android.os.RemoteException;
import android.os.ServiceSpecificException;
import android.os.SystemClock;
import android.util.SparseArray;

import com.android.internal.annotations.GuardedBy;

import java.util.ArrayList;
import java.util.List;

public class AidlMockedVehicleHal extends IVehicle.Stub {

    /**
    * Interface for handler of each property.
    */
    public interface VehicleHalPropertyHandler {
        default void onPropertySet(VehiclePropValue value) {}
        default VehiclePropValue onPropertyGet(VehiclePropValue value) {
            return null;
        }
        default void onPropertySubscribe(int property, float sampleRate) {}
        default void onPropertyUnsubscribe(int property) {}

        VehicleHalPropertyHandler NOP = new VehicleHalPropertyHandler() {};
    }

    private final Object mLock = new Object();
    @GuardedBy("mLock")
    private final SparseArray<VehicleHalPropertyHandler> mPropertyHandlerMap = new SparseArray<>();
    @GuardedBy("mLock")
    private final SparseArray<VehiclePropConfig> mConfigs = new SparseArray<>();
    @GuardedBy("mLock")
    private final SparseArray<List<IVehicleCallback>> mSubscribers = new SparseArray<>();

    public void addProperties(VehiclePropConfig... configs) {
        for (VehiclePropConfig config : configs) {
            addProperty(config, new DefaultPropertyHandler(config, /* initialValue= */ null));
        }
    }

    public void addProperty(VehiclePropConfig config, VehicleHalPropertyHandler handler) {
        synchronized (mLock) {
            mPropertyHandlerMap.put(config.prop, handler);
            mConfigs.put(config.prop, config);
        }
    }

    public void addStaticProperty(VehiclePropConfig config, VehiclePropValue value) {
        addProperty(config, new StaticPropertyHandler(value));
    }

    public boolean waitForSubscriber(int propId, long timeoutMillis) {
        long startTime = SystemClock.elapsedRealtime();
        try {
            synchronized (mLock) {
                while (mSubscribers.get(propId) == null) {
                    long waitMillis = startTime - SystemClock.elapsedRealtime() + timeoutMillis;
                    if (waitMillis < 0) break;
                    mLock.wait(waitMillis);
                }

                return mSubscribers.get(propId) != null;
            }
        } catch (InterruptedException e) {
            return false;
        }
    }

    public void injectEvent(VehiclePropValue value, boolean setProperty) {
        synchronized (mLock) {
            List<IVehicleCallback> callbacks = mSubscribers.get(value.prop);
            assertWithMessage("Injecting event failed for property: " + value.prop
                    + ". No listeners found").that(callbacks).isNotNull();

            if (setProperty) {
                // Update property if requested
                VehicleHalPropertyHandler handler = mPropertyHandlerMap.get(value.prop);
                if (handler != null) {
                    handler.onPropertySet(value);
                }
            }

            for (int i = 0; i < callbacks.size(); i++) {
                IVehicleCallback callback = callbacks.get(i);
                try {
                    VehiclePropValues propValues = new VehiclePropValues();
                    propValues.payloads = new VehiclePropValue[1];
                    propValues.payloads[0] = value;
                    callback.onPropertyEvent(propValues, /* sharedMemoryCount= */ 0);
                } catch (RemoteException e) {
                    e.printStackTrace();
                    fail("Remote exception while injecting events.");
                }
            }
        }
    }

    public void injectEvent(VehiclePropValue value) {
        injectEvent(value, /* setProperty= */ false);
    }

    public void injectError(int errorCode, int propertyId, int areaId) {
        synchronized (mLock) {
            List<IVehicleCallback> callbacks = mSubscribers.get(propertyId);
            assertWithMessage("Injecting error failed for property: " + propertyId
                    + ". No listeners found").that(callbacks).isNotNull();
            for (int i = 0; i < callbacks.size(); i++) {
                IVehicleCallback callback = callbacks.get(i);
                try {
                    VehiclePropError error = new VehiclePropError();
                    error.propId = propertyId;
                    error.areaId = areaId;
                    error.errorCode = errorCode;
                    VehiclePropErrors propErrors = new VehiclePropErrors();
                    propErrors.payloads = new VehiclePropError[] {error};
                    callback.onPropertySetError(propErrors);
                } catch (RemoteException e) {
                    e.printStackTrace();
                    fail("Remote exception while injecting errors.");
                }
            }
        }
    }

    @Override
    public VehiclePropConfigs getAllPropConfigs() throws RemoteException {
        synchronized (mLock) {
            VehiclePropConfigs propConfigs = new VehiclePropConfigs();
            propConfigs.payloads = new VehiclePropConfig[mConfigs.size()];
            for (int i = 0; i < mConfigs.size(); i++) {
                // Make a copy of the config.
                propConfigs.payloads[i] = AidlVehiclePropConfigBuilder.newBuilder(
                        mConfigs.valueAt(i)).build();
            }
            return propConfigs;
        }
    }

    @Override
    public VehiclePropConfigs getPropConfigs(int[] props) throws RemoteException {
        synchronized (mLock) {
            int count = 0;
            for (int prop : props) {
                if (mConfigs.contains(prop)) {
                    count++;
                }
            }

            VehiclePropConfigs propConfigs = new VehiclePropConfigs();
            propConfigs.payloads = new VehiclePropConfig[count];

            int i = 0;
            for (int prop : props) {
                if (mConfigs.contains(prop)) {
                    // Make a copy of the config.
                    propConfigs.payloads[i] = AidlVehiclePropConfigBuilder.newBuilder(
                            mConfigs.get(prop)).build();
                    i++;
                }
            }

            return propConfigs;
        }
    }

    @Override
    public void getValues(IVehicleCallback callback, GetValueRequests requests)
            throws RemoteException {
        synchronized (mLock) {
            assertWithMessage("AidlMockedVehicleHal does not support large parcelable").that(
                    requests.sharedMemoryFd).isNull();
            GetValueResults results = new GetValueResults();
            results.payloads = new GetValueResult[requests.payloads.length];

            for (int i = 0; i < requests.payloads.length; i++) {
                GetValueRequest request = requests.payloads[i];
                GetValueResult result = new GetValueResult();
                result.requestId = request.requestId;
                VehiclePropValue requestedPropValue = request.prop;
                VehicleHalPropertyHandler handler = mPropertyHandlerMap.get(
                        requestedPropValue.prop);
                if (handler == null) {
                    result.status = StatusCode.INVALID_ARG;
                } else {
                    try {
                        VehiclePropValue prop = handler.onPropertyGet(requestedPropValue);
                        result.status = StatusCode.OK;
                        if (prop == null) {
                            result.prop = null;
                        } else {
                            // Make a copy of prop.
                            result.prop = AidlVehiclePropValueBuilder.newBuilder(prop).build();
                        }
                    } catch (ServiceSpecificException e) {
                        result.status = e.errorCode;
                    }
                }
                results.payloads[i] = result;
            }

            callback.onGetValues(results);
        }
    }

    @Override
    public void setValues(IVehicleCallback callback, SetValueRequests requests)
            throws RemoteException {
        synchronized (mLock) {
            assertWithMessage("AidlMockedVehicleHal does not support large parcelable").that(
                    requests.sharedMemoryFd).isNull();
            SetValueResults results = new SetValueResults();
            results.payloads = new SetValueResult[requests.payloads.length];

            for (int i = 0; i < requests.payloads.length; i++) {
                SetValueRequest request = requests.payloads[i];
                SetValueResult result = new SetValueResult();
                result.requestId = request.requestId;
                VehiclePropValue requestedPropValue = request.value;
                VehicleHalPropertyHandler handler = mPropertyHandlerMap.get(
                        requestedPropValue.prop);
                if (handler == null) {
                    result.status = StatusCode.INVALID_ARG;
                } else {
                    try {
                        handler.onPropertySet(requestedPropValue);
                        result.status = StatusCode.OK;
                    } catch (ServiceSpecificException e) {
                        result.status = e.errorCode;
                    }
                }
                results.payloads[i] = result;
            }

            callback.onSetValues(results);
        }
    }

    @Override
    public void subscribe(IVehicleCallback callback, SubscribeOptions[] options,
            int maxSharedMemoryFileCount) throws RemoteException {
        synchronized (mLock) {
            for (SubscribeOptions opt : options) {
                VehicleHalPropertyHandler handler = mPropertyHandlerMap.get(opt.propId);
                if (handler == null) {
                    throw new ServiceSpecificException(StatusCode.INVALID_ARG,
                            "no registered handler");
                }

                handler.onPropertySubscribe(opt.propId, opt.sampleRate);
                List<IVehicleCallback> subscribers = mSubscribers.get(opt.propId);
                if (subscribers == null) {
                    subscribers = new ArrayList<>();
                    mSubscribers.put(opt.propId, subscribers);
                    mLock.notifyAll();
                } else {
                    for (int i = 0; i < subscribers.size(); i++) {
                        IVehicleCallback s = subscribers.get(i);
                        if (callback.asBinder() == s.asBinder()) {
                            // Remove callback that was registered previously for this property
                            subscribers.remove(callback);
                            break;
                        }
                    }
                }
                subscribers.add(callback);
            }
        }
    }

    @Override
    public void unsubscribe(IVehicleCallback callback, int[] propIds)
            throws RemoteException {
        synchronized (mLock) {
            for (int propId : propIds) {
                VehicleHalPropertyHandler handler = mPropertyHandlerMap.get(propId);
                if (handler == null) {
                    throw new ServiceSpecificException(StatusCode.INVALID_ARG,
                            "no registered handler");
                }

                handler.onPropertyUnsubscribe(propId);
                List<IVehicleCallback> subscribers = mSubscribers.get(propId);
                if (subscribers != null) {
                    subscribers.remove(callback);
                    if (subscribers.size() == 0) {
                        mSubscribers.remove(propId);
                    }
                }
            }
        }
    }

    @Override
    public void returnSharedMemory(IVehicleCallback callback, long sharedMemoryId)
            throws RemoteException {
        // Do nothing.
    }

    @Override
    public String getInterfaceHash() {
        return IVehicle.HASH;
    }

    @Override
    public int getInterfaceVersion() {
        return IVehicle.VERSION;
    }

    public static class FailingPropertyHandler implements VehicleHalPropertyHandler {
        @Override
        public void onPropertySet(VehiclePropValue value) {
            fail("Unexpected onPropertySet call");
        }

        @Override
        public VehiclePropValue onPropertyGet(VehiclePropValue value) {
            fail("Unexpected onPropertyGet call");
            return null;
        }

        @Override
        public void onPropertySubscribe(int property, float sampleRate) {
            fail("Unexpected onPropertySubscribe call");
        }

        @Override
        public void onPropertyUnsubscribe(int property) {
            fail("Unexpected onPropertyUnsubscribe call");
        }
    }

    public static class StaticPropertyHandler extends FailingPropertyHandler {
        private final Object mLock = new Object();
        @GuardedBy("mLock")
        private final VehiclePropValue mValue;

        public StaticPropertyHandler(VehiclePropValue value) {
            synchronized (mLock) {
                mValue = value;
            }
        }

        @Override
        public synchronized VehiclePropValue onPropertyGet(VehiclePropValue value) {
            synchronized (mLock) {
                return mValue;
            }
        }
    }

    public static class ErrorCodeHandler extends FailingPropertyHandler {
        private final Object mLock = new Object();
        @GuardedBy("mLock")
        private int mStatus;

        public void setStatus(int status) {
            synchronized (mLock) {
                mStatus = status;
            }
        }
        @Override
        public synchronized VehiclePropValue onPropertyGet(VehiclePropValue value) {
            synchronized (mLock) {
                throw new ServiceSpecificException(mStatus);
            }
        }
        @Override
        public void onPropertySet(VehiclePropValue value) {
            synchronized (mLock) {
                throw new ServiceSpecificException(mStatus);
            }
        }
    }

    public static class DefaultPropertyHandler implements VehicleHalPropertyHandler {
        private final VehiclePropConfig mConfig;

        private final Object mLock = new Object();
        @GuardedBy("mLock")
        private VehiclePropValue mValue;
        @GuardedBy("mLock")
        private boolean mSubscribed;

        public DefaultPropertyHandler(VehiclePropConfig config, VehiclePropValue initialValue) {
            mConfig = config;
            mValue = initialValue;
        }

        @Override
        public void onPropertySet(VehiclePropValue value) {
            assertThat(mConfig.prop).isEqualTo(value.prop);
            assertThat(mConfig.access & VehiclePropertyAccess.WRITE).isEqualTo(
                    VehiclePropertyAccess.WRITE);

            synchronized (mLock) {
                mValue = value;
            }
        }

        @Override
        public VehiclePropValue onPropertyGet(VehiclePropValue value) {
            assertThat(mConfig.prop).isEqualTo(value.prop);
            assertThat(mConfig.access & VehiclePropertyAccess.READ).isEqualTo(
                    VehiclePropertyAccess.READ);
            synchronized (mLock) {
                return mValue;
            }
        }

        @Override
        public void onPropertySubscribe(int property, float sampleRate) {
            assertThat(mConfig.prop).isEqualTo(property);
            synchronized (mLock) {
                mSubscribed = true;
            }
        }

        @Override
        public void onPropertyUnsubscribe(int property) {
            assertThat(mConfig.prop).isEqualTo(property);

            synchronized (mLock) {
                if (!mSubscribed) {
                    throw new IllegalArgumentException("Property was not subscribed 0x"
                            + toHexString(property));
                }
                mSubscribed = false;
            }
        }
    }

}
