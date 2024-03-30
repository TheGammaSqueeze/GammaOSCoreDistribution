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

package com.android.car.internal;

import static java.util.Objects.requireNonNull;

import android.annotation.Nullable;
import android.car.VehiclePropertyIds;
import android.car.hardware.CarPropertyValue;
import android.car.hardware.property.CarPropertyEvent;
import android.car.hardware.property.CarPropertyManager.CarPropertyEventCallback;
import android.util.ArrayMap;
import android.util.Log;
import android.util.SparseLongArray;

import com.android.internal.annotations.GuardedBy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Manages a group of {@link CarPropertyEventCallback} instances registered for the same {@link
 * #mPropertyId} at possibly different update rates.
 *
 * @hide
 */
public final class CarPropertyEventCallbackController {
    // Abbreviating TAG because class name is longer than the 23 character Log tag limit.
    private static final String TAG = "CPECallbackController";
    private static final boolean DBG = Log.isLoggable(TAG, Log.DEBUG);
    private static final float NANOSECOND_PER_SECOND = 1000 * 1000 * 1000;
    // Since this class is internal to CarPropertyManager, it shares the same lock to avoid
    // potential deadlock.
    private final Object mCarPropertyManagerLock;
    @GuardedBy("mCarPropertyManagerLock")
    private final Map<CarPropertyEventCallback, Float> mCarPropertyEventCallbackToUpdateRateHz =
            new ArrayMap<>();
    @GuardedBy("mCarPropertyManagerLock")
    private final Map<CarPropertyEventCallback, SparseLongArray>
            mCarPropertyEventCallbackToAreaIdToNextUpdateTimeNanos = new ArrayMap<>();
    private final int mPropertyId;
    private final RegistrationUpdateCallback mRegistrationUpdateCallback;
    @GuardedBy("mCarPropertyManagerLock")
    private Float mMaxUpdateRateHz = null;

    public CarPropertyEventCallbackController(int propertyId, Object carPropertyManagerLock,
            RegistrationUpdateCallback registrationUpdateCallback) {
        requireNonNull(registrationUpdateCallback);
        mPropertyId = propertyId;
        mCarPropertyManagerLock = carPropertyManagerLock;
        mRegistrationUpdateCallback = registrationUpdateCallback;
    }

    /**
     * Forward a successful {@link CarPropertyEvent} to the registered {@link
     * CarPropertyEventCallback} instances if the {@link CarPropertyEventCallback} instance's update
     * rate threshold is met.
     */
    public void forwardPropertyChanged(CarPropertyEvent carPropertyEvent) {
        requireNonNull(carPropertyEvent);
        List<CarPropertyEventCallback> carPropertyEventCallbacks = getCallbacksForCarPropertyEvent(
                carPropertyEvent);
        CarPropertyValue<?> carPropertyValue = carPropertyEvent.getCarPropertyValue();
        for (int i = 0; i < carPropertyEventCallbacks.size(); i++) {
            carPropertyEventCallbacks.get(i).onChangeEvent(carPropertyValue);
        }
    }

    /**
     * Forward an error {@link CarPropertyEvent} to the registered {@link CarPropertyEventCallback}
     * instances.
     */
    public void forwardErrorEvent(CarPropertyEvent carPropertyEvent) {
        requireNonNull(carPropertyEvent);
        CarPropertyValue<?> carPropertyValue = carPropertyEvent.getCarPropertyValue();
        if (DBG) {
            Log.d(TAG, "onErrorEvent for property: " + VehiclePropertyIds.toString(
                    carPropertyValue.getPropertyId()) + " areaId: " + carPropertyValue.getAreaId()
                    + " errorCode: " + carPropertyEvent.getErrorCode());
        }
        List<CarPropertyEventCallback> carPropertyEventCallbacks;
        synchronized (mCarPropertyManagerLock) {
            carPropertyEventCallbacks = new ArrayList<>(
                    mCarPropertyEventCallbackToUpdateRateHz.keySet());
        }
        for (int i = 0; i < carPropertyEventCallbacks.size(); i++) {
            carPropertyEventCallbacks.get(i).onErrorEvent(carPropertyValue.getPropertyId(),
                    carPropertyValue.getAreaId(), carPropertyEvent.getErrorCode());

        }
    }

    /**
     * Add given {@link CarPropertyEventCallback} to the list and update registration if necessary.
     *
     * @return true is registration was successful, otherwise false.
     */
    public boolean add(CarPropertyEventCallback carPropertyEventCallback, float updateRateHz) {
        requireNonNull(carPropertyEventCallback);
        Float previousUpdateRateHz;
        SparseLongArray previousAreaIdToNextUpdateTimeNanos;
        synchronized (mCarPropertyManagerLock) {
            previousUpdateRateHz = mCarPropertyEventCallbackToUpdateRateHz.put(
                    carPropertyEventCallback, updateRateHz);
            previousAreaIdToNextUpdateTimeNanos =
                    mCarPropertyEventCallbackToAreaIdToNextUpdateTimeNanos.put(
                            carPropertyEventCallback, new SparseLongArray());
        }
        boolean registerSuccessful = updateMaxUpdateRateHzAndRegistration();
        if (!registerSuccessful) {
            synchronized (mCarPropertyManagerLock) {
                if (previousUpdateRateHz != null && previousAreaIdToNextUpdateTimeNanos != null) {
                    mCarPropertyEventCallbackToUpdateRateHz.put(carPropertyEventCallback,
                            previousUpdateRateHz);
                    mCarPropertyEventCallbackToAreaIdToNextUpdateTimeNanos.put(
                            carPropertyEventCallback, previousAreaIdToNextUpdateTimeNanos);
                } else {
                    mCarPropertyEventCallbackToUpdateRateHz.remove(carPropertyEventCallback);
                    mCarPropertyEventCallbackToAreaIdToNextUpdateTimeNanos.remove(
                            carPropertyEventCallback);
                }
                mMaxUpdateRateHz = calculateMaxUpdateRateHzLocked();
            }
        }
        return registerSuccessful;
    }

    /**
     * Remove given {@link CarPropertyEventCallback} from the list and update registration if
     * necessary.
     *
     * @return true if all {@link CarPropertyEventCallback} instances are removed, otherwise false.
     */
    public boolean remove(CarPropertyEventCallback carPropertyEventCallback) {
        requireNonNull(carPropertyEventCallback);
        synchronized (mCarPropertyManagerLock) {
            mCarPropertyEventCallbackToUpdateRateHz.remove(carPropertyEventCallback);
            mCarPropertyEventCallbackToAreaIdToNextUpdateTimeNanos.remove(carPropertyEventCallback);
        }
        updateMaxUpdateRateHzAndRegistration();
        synchronized (mCarPropertyManagerLock) {
            return mCarPropertyEventCallbackToUpdateRateHz.isEmpty();
        }
    }


    private List<CarPropertyEventCallback> getCallbacksForCarPropertyEvent(
            CarPropertyEvent carPropertyEvent) {
        List<CarPropertyEventCallback> carPropertyEventCallbacks = new ArrayList<>();
        synchronized (mCarPropertyManagerLock) {
            for (CarPropertyEventCallback carPropertyEventCallback :
                    mCarPropertyEventCallbackToUpdateRateHz.keySet()) {
                if (shouldCallbackBeInvokedLocked(carPropertyEventCallback, carPropertyEvent)) {
                    carPropertyEventCallbacks.add(carPropertyEventCallback);
                }
            }
        }
        return carPropertyEventCallbacks;
    }

    @GuardedBy("mCarPropertyManagerLock")
    private boolean shouldCallbackBeInvokedLocked(CarPropertyEventCallback carPropertyEventCallback,
            CarPropertyEvent carPropertyEvent) {
        SparseLongArray areaIdToNextUpdateTimeNanos =
                mCarPropertyEventCallbackToAreaIdToNextUpdateTimeNanos.get(
                        carPropertyEventCallback);
        Float updateRateHz = mCarPropertyEventCallbackToUpdateRateHz.get(carPropertyEventCallback);
        if (areaIdToNextUpdateTimeNanos == null || updateRateHz == null) {
            CarPropertyValue<?> carPropertyValue = carPropertyEvent.getCarPropertyValue();
            Log.w(TAG, "callback was not found for property: " + VehiclePropertyIds.toString(
                    carPropertyValue.getPropertyId()) + " areaId: " + carPropertyValue.getAreaId()
                    + " timestampNanos: " + carPropertyValue.getTimestamp());
            return false;
        }

        CarPropertyValue<?> carPropertyValue = carPropertyEvent.getCarPropertyValue();
        long nextUpdateTimeNanos = areaIdToNextUpdateTimeNanos.get(carPropertyValue.getAreaId(),
                0L);

        if (carPropertyValue.getTimestamp() >= nextUpdateTimeNanos) {
            long updatePeriodNanos =
                    updateRateHz > 0 ? ((long) ((1.0 / updateRateHz) * NANOSECOND_PER_SECOND)) : 0;
            areaIdToNextUpdateTimeNanos.put(carPropertyValue.getAreaId(),
                    carPropertyValue.getTimestamp() + updatePeriodNanos);
            mCarPropertyEventCallbackToAreaIdToNextUpdateTimeNanos.put(carPropertyEventCallback,
                    areaIdToNextUpdateTimeNanos);
            return true;
        }

        if (DBG) {
            Log.d(TAG, "Dropping carPropertyEvent - propId: " + carPropertyValue.getPropertyId()
                    + " areaId: " + carPropertyValue.getAreaId() + "  because getTimestamp(): "
                    + carPropertyValue.getTimestamp() + " < nextUpdateTimeNanos: "
                    + nextUpdateTimeNanos);
        }
        return false;
    }

    private boolean updateMaxUpdateRateHzAndRegistration() {
        Float newMaxUpdateRateHz;
        synchronized (mCarPropertyManagerLock) {
            newMaxUpdateRateHz = calculateMaxUpdateRateHzLocked();
            if (Objects.equals(mMaxUpdateRateHz, newMaxUpdateRateHz)) {
                return true;
            }
            mMaxUpdateRateHz = newMaxUpdateRateHz;
        }
        if (newMaxUpdateRateHz == null) {
            mRegistrationUpdateCallback.unregister(mPropertyId);
            return true;
        }
        return mRegistrationUpdateCallback.register(mPropertyId, newMaxUpdateRateHz);
    }

    @GuardedBy("mCarPropertyManagerLock")
    @Nullable
    private Float calculateMaxUpdateRateHzLocked() {
        if (mCarPropertyEventCallbackToUpdateRateHz.isEmpty()) {
            return null;
        }
        return Collections.max(mCarPropertyEventCallbackToUpdateRateHz.values());
    }

    /**
     * Interface that receives updates to register or unregister property with {@link
     * com.android.car.CarPropertyService}.
     */
    public interface RegistrationUpdateCallback {
        /**
         * Called when {@code propertyId} registration needs to be updated.
         *
         * @return true is registration was successful, otherwise false.
         */
        boolean register(int propertyId, float updateRateHz);

        /**
         * Called when {@code propertyId} needs to be unregistered.
         */
        void unregister(int propertyId);
    }
}

