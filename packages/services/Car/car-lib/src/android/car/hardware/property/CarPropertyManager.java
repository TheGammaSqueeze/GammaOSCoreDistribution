/*
 * Copyright (C) 2018 The Android Open Source Project
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

package android.car.hardware.property;

import static com.android.car.internal.property.CarPropertyHelper.SYNC_OP_LIMIT_TRY_AGAIN;

import static java.lang.Integer.toHexString;
import static java.util.Objects.requireNonNull;

import android.annotation.FloatRange;
import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.car.Car;
import android.car.CarManagerBase;
import android.car.VehicleAreaType;
import android.car.VehiclePropertyIds;
import android.car.annotation.AddedInOrBefore;
import android.car.hardware.CarPropertyConfig;
import android.car.hardware.CarPropertyValue;
import android.os.Build;
import android.os.Handler;
import android.os.RemoteException;
import android.os.ServiceSpecificException;
import android.os.SystemClock;
import android.util.ArraySet;
import android.util.Log;
import android.util.SparseArray;

import com.android.car.internal.CarPropertyEventCallbackController;
import com.android.car.internal.SingleMessageHandler;
import com.android.internal.annotations.GuardedBy;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * Provides an application interface for interacting with the Vehicle specific properties.
 * For details about the individual properties, see the descriptions in
 * hardware/interfaces/automotive/vehicle/types.hal
 */
public class CarPropertyManager extends CarManagerBase {
    private static final boolean DBG = false;
    private static final String TAG = "CarPropertyManager";
    private static final int MSG_GENERIC_EVENT = 0;
    private static final int SYNC_OP_RETRY_SLEEP_IN_MS = 10;
    private static final int SYNC_OP_RETRY_MAX_COUNT = 10;

    private final SingleMessageHandler<CarPropertyEvent> mHandler;
    private final ICarProperty mService;
    private final int mAppTargetSdk;

    private final CarPropertyEventListenerToService mCarPropertyEventToService =
            new CarPropertyEventListenerToService(this);

    // This lock is shared with all CarPropertyEventCallbackController instances to prevent
    // potential deadlock.
    private final Object mLock = new Object();
    @GuardedBy("mLock")
    private final SparseArray<CarPropertyEventCallbackController>
            mPropertyIdToCarPropertyEventCallbackController = new SparseArray<>();

    private final CarPropertyEventCallbackController.RegistrationUpdateCallback
            mRegistrationUpdateCallback =
            new CarPropertyEventCallbackController.RegistrationUpdateCallback() {
                @Override
                public boolean register(int propertyId, float updateRateHz) {
                    try {
                        mService.registerListener(propertyId, updateRateHz,
                                mCarPropertyEventToService);
                    } catch (RemoteException e) {
                        handleRemoteExceptionFromCarService(e);
                        return false;
                    }
                    return true;
                }

                @Override
                public void unregister(int propertyId) {
                    try {
                        mService.unregisterListener(propertyId, mCarPropertyEventToService);
                    } catch (RemoteException e) {
                        handleRemoteExceptionFromCarService(e);
                    }
                }
            };

    /**
     * Application registers {@link CarPropertyEventCallback} object to receive updates and changes
     * to subscribed Vehicle specific properties.
     */
    public interface CarPropertyEventCallback {
        /**
         * Called when a property is updated
         * @param value Property that has been updated.
         */
        @AddedInOrBefore(majorVersion = 33)
        void onChangeEvent(CarPropertyValue value);

        /**
         * Called when an error is detected when setting a property.
         *
         * @param propId Property ID which is detected an error.
         * @param zone Zone which is detected an error.
         *
         * @see CarPropertyEventCallback#onErrorEvent(int, int, int)
         */
        @AddedInOrBefore(majorVersion = 33)
        void onErrorEvent(int propId, int zone);

        /**
         * Called when an error is detected when setting a property.
         *
         * <p>Clients which changed the property value in the areaId most recently will receive
         * this callback. If multiple clients set a property for the same area id simultaneously,
         * which one takes precedence is undefined. Typically, the last set operation
         * (in the order that they are issued to car's ECU) overrides the previous set operations.
         * The delivered error reflects the error happened in the last set operation.
         *
         * @param propId Property ID which is detected an error.
         * @param areaId AreaId which is detected an error.
         * @param errorCode Error code is raised in the car.
         */
        @AddedInOrBefore(majorVersion = 33)
        default void onErrorEvent(int propId, int areaId, @CarSetPropertyErrorCode int errorCode) {
            if (DBG) {
                Log.d(TAG, "onErrorEvent propertyId: 0x" + toHexString(propId) + " areaId:0x"
                        + toHexString(areaId) + " ErrorCode: " + errorCode);
            }
            onErrorEvent(propId, areaId);
        }
    }

    /** Read ONCHANGE sensors. */
    @AddedInOrBefore(majorVersion = 33)
    public static final float SENSOR_RATE_ONCHANGE = 0f;
    /** Read sensors at the rate of  1 hertz */
    @AddedInOrBefore(majorVersion = 33)
    public static final float SENSOR_RATE_NORMAL = 1f;
    /** Read sensors at the rate of 5 hertz */
    @AddedInOrBefore(majorVersion = 33)
    public static final float SENSOR_RATE_UI = 5f;
    /** Read sensors at the rate of 10 hertz */
    @AddedInOrBefore(majorVersion = 33)
    public static final float SENSOR_RATE_FAST = 10f;
    /** Read sensors at the rate of 100 hertz */
    @AddedInOrBefore(majorVersion = 33)
    public static final float SENSOR_RATE_FASTEST = 100f;



    /**
     * Status to indicate that set operation failed. Try it again.
     */
    @AddedInOrBefore(majorVersion = 33)
    public static final int CAR_SET_PROPERTY_ERROR_CODE_TRY_AGAIN = 1;

    /**
     * Status to indicate that set operation failed because of an invalid argument.
     */
    @AddedInOrBefore(majorVersion = 33)
    public static final int CAR_SET_PROPERTY_ERROR_CODE_INVALID_ARG = 2;

    /**
     * Status to indicate that set operation failed because the property is not available.
     */
    @AddedInOrBefore(majorVersion = 33)
    public static final int CAR_SET_PROPERTY_ERROR_CODE_PROPERTY_NOT_AVAILABLE = 3;

    /**
     * Status to indicate that set operation failed because car denied access to the property.
     */
    @AddedInOrBefore(majorVersion = 33)
    public static final int CAR_SET_PROPERTY_ERROR_CODE_ACCESS_DENIED = 4;

    /**
     * Status to indicate that set operation failed because of an general error in cars.
     */
    @AddedInOrBefore(majorVersion = 33)
    public static final int CAR_SET_PROPERTY_ERROR_CODE_UNKNOWN = 5;

    /** @hide */
    @IntDef(prefix = {"CAR_SET_PROPERTY_ERROR_CODE_"}, value = {
            CAR_SET_PROPERTY_ERROR_CODE_TRY_AGAIN,
            CAR_SET_PROPERTY_ERROR_CODE_INVALID_ARG,
            CAR_SET_PROPERTY_ERROR_CODE_PROPERTY_NOT_AVAILABLE,
            CAR_SET_PROPERTY_ERROR_CODE_ACCESS_DENIED,
            CAR_SET_PROPERTY_ERROR_CODE_UNKNOWN,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface CarSetPropertyErrorCode {}

    /**
     * Get an instance of the CarPropertyManager.
     *
     * Should not be obtained directly by clients, use {@link Car#getCarManager(String)} instead.
     * @param car Car instance
     * @param service ICarProperty instance
     * @hide
     */
    public CarPropertyManager(Car car, @NonNull ICarProperty service) {
        super(car);
        mService = service;
        mAppTargetSdk = getContext().getApplicationInfo().targetSdkVersion;

        Handler eventHandler = getEventHandler();
        if (eventHandler == null) {
            mHandler = null;
            return;
        }
        mHandler = new SingleMessageHandler<CarPropertyEvent>(eventHandler.getLooper(),
            MSG_GENERIC_EVENT) {
            @Override
            protected void handleEvent(CarPropertyEvent carPropertyEvent) {
                CarPropertyEventCallbackController carPropertyEventCallbackController;
                synchronized (mLock) {
                    carPropertyEventCallbackController =
                            mPropertyIdToCarPropertyEventCallbackController.get(
                                    carPropertyEvent.getCarPropertyValue().getPropertyId());
                }
                if (carPropertyEventCallbackController == null) {
                    return;
                }
                switch (carPropertyEvent.getEventType()) {
                    case CarPropertyEvent.PROPERTY_EVENT_PROPERTY_CHANGE:
                        carPropertyEventCallbackController.forwardPropertyChanged(carPropertyEvent);
                        break;
                    case CarPropertyEvent.PROPERTY_EVENT_ERROR:
                        carPropertyEventCallbackController.forwardErrorEvent(carPropertyEvent);
                        break;
                    default:
                        throw new IllegalArgumentException();
                }
            }
        };
    }

    /**
     * Register {@link CarPropertyEventCallback} to get property updates. Multiple callbacks
     * can be registered for a single property or the same callback can be used for different
     * properties. If the same callback is registered again for the same property, it will be
     * updated to new updateRateHz.
     * <p>Rate could be one of the following:
     * <ul>
     *   <li>{@link CarPropertyManager#SENSOR_RATE_ONCHANGE}</li>
     *   <li>{@link CarPropertyManager#SENSOR_RATE_NORMAL}</li>
     *   <li>{@link CarPropertyManager#SENSOR_RATE_UI}</li>
     *   <li>{@link CarPropertyManager#SENSOR_RATE_FAST}</li>
     *   <li>{@link CarPropertyManager#SENSOR_RATE_FASTEST}</li>
     * </ul>
     * <p>
     * <b>Note:</b>Rate has no effect if the property has one of the following change modes:
     * <ul>
     *   <li>{@link CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_STATIC}</li>
     *   <li>{@link CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE}</li>
     * </ul>
     * <b>Note:</b>If listener registers for updates for a
     * {@link CarPropertyConfig#VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE} property, it will receive the
     * property's current value upon registration.
     * See {@link CarPropertyConfig#getChangeMode()} for details.
     * If updateRateHz is higher than {@link CarPropertyConfig#getMaxSampleRate()}, it will be
     * registered with max sample updateRateHz.
     * If updateRateHz is lower than {@link CarPropertyConfig#getMinSampleRate()}, it will be
     * registered with min sample updateRateHz.
     *
     * @param carPropertyEventCallback CarPropertyEventCallback to be registered.
     * @param propertyId               PropertyId to subscribe
     * @param updateRateHz             how fast the property events are delivered in Hz.
     * @return true if the listener is successfully registered.
     * @throws SecurityException if missing the appropriate permission.
     */
    @AddedInOrBefore(majorVersion = 33)
    public boolean registerCallback(@NonNull CarPropertyEventCallback carPropertyEventCallback,
            int propertyId, @FloatRange(from = 0.0, to = 100.0) float updateRateHz) {
        requireNonNull(carPropertyEventCallback);
        CarPropertyConfig<?> carPropertyConfig = getCarPropertyConfig(propertyId);
        if (carPropertyConfig == null) {
            Log.e(TAG, "registerListener:  propId is not in carPropertyConfig list:  "
                    + VehiclePropertyIds.toString(propertyId));
            return false;
        }
        if (carPropertyConfig.getChangeMode()
                != CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_CONTINUOUS) {
            updateRateHz = SENSOR_RATE_ONCHANGE;
        } else if (updateRateHz > carPropertyConfig.getMaxSampleRate()) {
            updateRateHz = carPropertyConfig.getMaxSampleRate();
        } else if (updateRateHz < carPropertyConfig.getMinSampleRate()) {
            updateRateHz = carPropertyConfig.getMinSampleRate();
        }
        CarPropertyEventCallbackController carPropertyEventCallbackController;
        synchronized (mLock) {
            carPropertyEventCallbackController =
                    mPropertyIdToCarPropertyEventCallbackController.get(propertyId);
            if (carPropertyEventCallbackController == null) {
                carPropertyEventCallbackController = new CarPropertyEventCallbackController(
                        propertyId, mLock, mRegistrationUpdateCallback);
                mPropertyIdToCarPropertyEventCallbackController.put(propertyId,
                        carPropertyEventCallbackController);
            }
        }
        return carPropertyEventCallbackController.add(carPropertyEventCallback, updateRateHz);
    }

    private static class CarPropertyEventListenerToService extends ICarPropertyEventListener.Stub {
        private final WeakReference<CarPropertyManager> mCarPropertyManager;

        CarPropertyEventListenerToService(CarPropertyManager carPropertyManager) {
            mCarPropertyManager = new WeakReference<>(carPropertyManager);
        }

        @Override
        public void onEvent(List<CarPropertyEvent> carPropertyEvents) throws RemoteException {
            CarPropertyManager carPropertyManager = mCarPropertyManager.get();
            if (carPropertyManager != null) {
                carPropertyManager.handleEvents(carPropertyEvents);
            }
        }
    }

    private void handleEvents(List<CarPropertyEvent> carPropertyEvents) {
        if (mHandler != null) {
            mHandler.sendEvents(carPropertyEvents);
        }
    }

    /**
     * Stop getting property updates for the given {@link CarPropertyEventCallback}. If there are
     * multiple registrations for this {@link CarPropertyEventCallback}, all listening will be
     * stopped.
     */
    @AddedInOrBefore(majorVersion = 33)
    public void unregisterCallback(@NonNull CarPropertyEventCallback carPropertyEventCallback) {
        requireNonNull(carPropertyEventCallback);
        int[] propertyIds;
        synchronized (mLock) {
            propertyIds = new int[mPropertyIdToCarPropertyEventCallbackController.size()];
            for (int i = 0; i < mPropertyIdToCarPropertyEventCallbackController.size(); i++) {
                propertyIds[i] = mPropertyIdToCarPropertyEventCallbackController.keyAt(i);
            }
        }
        for (int propertyId : propertyIds) {
            unregisterCallback(carPropertyEventCallback, propertyId);
        }
    }

    /**
     * Stop getting update for {@code propertyId} to the given {@link CarPropertyEventCallback}. If
     * the same {@link CarPropertyEventCallback} is used for other properties, those subscriptions
     * will not be affected.
     */
    @AddedInOrBefore(majorVersion = 33)
    public void unregisterCallback(@NonNull CarPropertyEventCallback carPropertyEventCallback,
            int propertyId) {
        requireNonNull(carPropertyEventCallback);
        CarPropertyEventCallbackController carPropertyEventCallbackController;
        synchronized (mLock) {
            carPropertyEventCallbackController =
                    mPropertyIdToCarPropertyEventCallbackController.get(propertyId);
        }
        if (carPropertyEventCallbackController == null) {
            return;
        }
        synchronized (mLock) {
            boolean allCallbacksRemoved = carPropertyEventCallbackController.remove(
                    carPropertyEventCallback);
            if (allCallbacksRemoved) {
                mPropertyIdToCarPropertyEventCallbackController.remove(propertyId);
            }
        }
    }

    /**
     * @return List of properties implemented by this car that the application may access.
     */
    @NonNull
    @AddedInOrBefore(majorVersion = 33)
    public List<CarPropertyConfig> getPropertyList() {
        List<CarPropertyConfig> configs;
        try {
            configs = mService.getPropertyList();
        } catch (RemoteException e) {
            Log.e(TAG, "getPropertyList exception ", e);
            return handleRemoteExceptionFromCarService(e, new ArrayList<CarPropertyConfig>());
        }
        return configs;
    }

    /**
     * @param propertyIds property ID list
     * @return List of properties implemented by this car in given property ID list that application
     *          may access.
     */
    @NonNull
    @AddedInOrBefore(majorVersion = 33)
    public List<CarPropertyConfig> getPropertyList(@NonNull ArraySet<Integer> propertyIds) {
        int[] propIds = new int[propertyIds.size()];
        int idx = 0;
        for (int propId : propertyIds) {
            checkSupportedProperty(propId);
            propIds[idx++] = propId;
        }
        List<CarPropertyConfig> configs;
        try {
            configs = mService.getPropertyConfigList(propIds);
        } catch (RemoteException e) {
            Log.e(TAG, "getPropertyList exception ", e);
            return handleRemoteExceptionFromCarService(e, new ArrayList<CarPropertyConfig>());
        }
        return configs;
    }

    /**
     * Get CarPropertyConfig by property Id.
     *
     * @param propId Property ID
     * @return {@link CarPropertyConfig} for the selected property.
     * Null if the property is not available.
     */
    @Nullable
    @AddedInOrBefore(majorVersion = 33)
    public CarPropertyConfig<?> getCarPropertyConfig(int propId) {
        checkSupportedProperty(propId);
        List<CarPropertyConfig> configs;
        try {
            configs = mService.getPropertyConfigList(new int[] {propId});
        } catch (RemoteException e) {
            Log.e(TAG, "getPropertyList exception ", e);
            return handleRemoteExceptionFromCarService(e, null);
        }
        return configs.size() == 0 ? null : configs.get(0);
    }

    /**
     * Returns areaId contains the seletcted area for the property.
     *
     * @param propId Property ID
     * @param area Area enum such as Enums in {@link android.car.VehicleAreaSeat}.
     * @throws IllegalArgumentException if the property is not available in the vehicle for
     * the selected area.
     * @return AreaId contains the selected area for the property.
     */
    @AddedInOrBefore(majorVersion = 33)
    public int getAreaId(int propId, int area) {
        checkSupportedProperty(propId);

        CarPropertyConfig<?> propConfig = getCarPropertyConfig(propId);
        if (propConfig == null) {
            throw new IllegalArgumentException("The property propId: 0x" + toHexString(propId)
                    + " is not available");
        }
        // For the global property, areaId is 0
        if (propConfig.isGlobalProperty()) {
            return VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL;
        }
        for (int areaId : propConfig.getAreaIds()) {
            if ((area & areaId) == area) {
                return areaId;
            }
        }

        throw new IllegalArgumentException("The property propId: 0x" + toHexString(propId)
                + " is not available at the area: 0x" + toHexString(area));
    }

    /**
     * Return read permission string for given property ID.
     *
     * @param propId Property ID to query
     * @return String Permission needed to read this property.  NULL if propId not available.
     * @hide
     */
    @Nullable
    @AddedInOrBefore(majorVersion = 33)
    public String getReadPermission(int propId) {
        if (DBG) {
            Log.d(TAG, "getReadPermission, propId: 0x" + toHexString(propId));
        }
        checkSupportedProperty(propId);
        try {
            return mService.getReadPermission(propId);
        } catch (RemoteException e) {
            return handleRemoteExceptionFromCarService(e, "");
        }
    }

    /**
     * Return write permission string for given property ID.
     *
     * @param propId Property ID to query
     * @return String Permission needed to write this property.  NULL if propId not available.
     * @hide
     */
    @Nullable
    @AddedInOrBefore(majorVersion = 33)
    public String getWritePermission(int propId) {
        if (DBG) {
            Log.d(TAG, "getWritePermission, propId: 0x" + toHexString(propId));
        }
        checkSupportedProperty(propId);
        try {
            return mService.getWritePermission(propId);
        } catch (RemoteException e) {
            return handleRemoteExceptionFromCarService(e, "");
        }
    }


    /**
     * Check whether a given property is available or disabled based on the car's current state.
     * @param propId Property Id
     * @param area AreaId of property
     * @return true if STATUS_AVAILABLE, false otherwise (eg STATUS_UNAVAILABLE)
     */
    @AddedInOrBefore(majorVersion = 33)
    public boolean isPropertyAvailable(int propId, int area) {
        checkSupportedProperty(propId);
        try {
            CarPropertyValue propValue = runSyncOperation(() -> {
                return mService.getProperty(propId, area);
            });
            return (propValue != null)
                    && (propValue.getStatus() == CarPropertyValue.STATUS_AVAILABLE);
        } catch (RemoteException e) {
            return handleRemoteExceptionFromCarService(e, false);
        } catch (ServiceSpecificException e) {
            Log.e(TAG, "unable to get property, error: " + e);
            return false;
        }
    }

    /**
     * Returns value of a bool property
     *
     * <p> This method may take couple seconds to complete, so it needs to be called from an
     * non-main thread.
     *
     * <p> Clients that declare a {@link android.content.pm.ApplicationInfo#targetSdkVersion} equal
     * or later than {@link Build.VERSION_CODES#R} will receive the following exceptions when
     * request is failed.
     * <ul>
     *     <li>{@link CarInternalErrorException}
     *     <li>{@link PropertyAccessDeniedSecurityException}
     *     <li>{@link PropertyNotAvailableAndRetryException}
     *     <li>{@link PropertyNotAvailableException}
     *     <li>{@link IllegalArgumentException}
     * </ul>
     * <p> Clients that declare a {@link android.content.pm.ApplicationInfo#targetSdkVersion}
     * earlier than {@link Build.VERSION_CODES#R} will receive the following exceptions if the call
     * fails.
     * <ul>
     *     <li>{@link IllegalStateException} when there is an error detected in cars.
     *     <li>{@link IllegalArgumentException} when the property in the areaId is not supplied.
     * </ul>
     *
     * @param prop Property ID to get
     * @param area Area of the property to get
     *
     * @throws {@link CarInternalErrorException} when there is an error detected in cars.
     * @throws {@link PropertyAccessDeniedSecurityException} when cars denied the access of the
     * property.
     * @throws {@link PropertyNotAvailableAndRetryException} when the property is temporarily
     * not available and likely that retrying will be successful.
     * @throws {@link PropertyNotAvailableException} when the property is temporarily not available.
     * @throws {@link IllegalArgumentException} when the property in the areaId is not supplied.
     *
     * @return value of a bool property, {@code false} if can not get value from cars.
     */
    @AddedInOrBefore(majorVersion = 33)
    public boolean getBooleanProperty(int prop, int area) {
        checkSupportedProperty(prop);
        CarPropertyValue<Boolean> carProp = getProperty(Boolean.class, prop, area);
        return handleNullAndPropertyStatus(carProp, area, false);
    }

    /**
     * Returns value of a float property
     *
     * <p> This method may take couple seconds to complete, so it needs to be called from an
     * non-main thread.
     *
     * <p> This method has the same exception behavior as {@link #getBooleanProperty(int, int)}.
     *
     * @param prop Property ID to get
     * @param area Area of the property to get
     *
     * @throws {@link CarInternalErrorException} when there is an error detected in cars.
     * @throws {@link PropertyAccessDeniedSecurityException} when cars denied the access of the
     * property.
     * @throws {@link PropertyNotAvailableAndRetryException} when the property is temporarily
     * not available and likely that retrying will be successful.
     * @throws {@link PropertyNotAvailableException} when the property is temporarily not available.
     * @throws {@link IllegalArgumentException} when the property in the areaId is not supplied.
     *
     * @return value of a float property, 0 if can not get value from the cars.
     */
    @AddedInOrBefore(majorVersion = 33)
    public float getFloatProperty(int prop, int area) {
        checkSupportedProperty(prop);
        CarPropertyValue<Float> carProp = getProperty(Float.class, prop, area);
        return handleNullAndPropertyStatus(carProp, area, 0f);
    }

    /**
     * Returns value of an integer property
     *
     * <p> This method may take couple seconds to complete, so it needs to be called form an
     * non-main thread.
     *
     * <p> This method has the same exception behavior as {@link #getBooleanProperty(int, int)}.
     *
     * @param prop Property ID to get
     * @param area Zone of the property to get
     *
     * @throws {@link CarInternalErrorException} when there is an error detected in cars.
     * @throws {@link PropertyAccessDeniedSecurityException} when cars denied the access of the
     * property.
     * @throws {@link PropertyNotAvailableAndRetryException} when the property is temporarily
     * not available and likely that retrying will be successful.
     * @throws {@link PropertyNotAvailableException} when the property is temporarily not available.
     * @throws {@link IllegalArgumentException} when the property in the areaId is not supplied.
     *
     * @return value of an integer property, 0 if can not get the value from cars.
     */
    @AddedInOrBefore(majorVersion = 33)
    public int getIntProperty(int prop, int area) {
        checkSupportedProperty(prop);
        CarPropertyValue<Integer> carProp = getProperty(Integer.class, prop, area);
        return handleNullAndPropertyStatus(carProp, area, 0);
    }

    /**
     * Returns value of an integer array property
     *
     * <p> This method may take couple seconds to complete, so it needs to be called from an
     * non-main thread.
     *
     * <p> This method has the same exception behavior as {@link #getBooleanProperty(int, int)}.
     *
     * @param prop Property ID to get
     * @param area Zone of the property to get
     *
     * @throws {@link CarInternalErrorException} when there is an error detected in cars.
     * @throws {@link PropertyAccessDeniedSecurityException} when cars denied the access of the
     * property.
     * @throws {@link PropertyNotAvailableAndRetryException} when the property is temporarily
     * not available and likely that retrying will be successful.
     * @throws {@link PropertyNotAvailableException} when the property is temporarily not available.
     * @throws {@link IllegalArgumentException} when the property in the areaId is not supplied.
     *
     * @return value of an integer array property, an empty integer array if can not get the value
     * from cars.
     */
    @NonNull
    @AddedInOrBefore(majorVersion = 33)
    public int[] getIntArrayProperty(int prop, int area) {
        checkSupportedProperty(prop);
        CarPropertyValue<Integer[]> carProp = getProperty(Integer[].class, prop, area);
        Integer[] res = handleNullAndPropertyStatus(carProp, area, new Integer[0]);
        return toIntArray(res);
    }

    private static int[] toIntArray(Integer[] input) {
        int len = input.length;
        int[] arr = new int[len];
        for (int i = 0; i < len; i++) {
            arr[i] = input[i];
        }
        return arr;
    }

    private <T> T handleNullAndPropertyStatus(CarPropertyValue<T> propertyValue, int areaId,
            T defaultValue) {

        if (propertyValue == null) {
            return defaultValue;
        }

        // Keeps the same behavior as android R.
        if (mAppTargetSdk < Build.VERSION_CODES.S) {
            return propertyValue.getStatus() == CarPropertyValue.STATUS_AVAILABLE
                    ? propertyValue.getValue() : defaultValue;
        }

        // throws new exceptions in android S.
        switch (propertyValue.getStatus()) {
            case CarPropertyValue.STATUS_ERROR:
                throw new CarInternalErrorException(propertyValue.getPropertyId(), areaId);
            case CarPropertyValue.STATUS_UNAVAILABLE:
                throw new PropertyNotAvailableException(propertyValue.getPropertyId(), areaId);
            default:
                return propertyValue.getValue();
        }
    }

    private static <V> V runSyncOperation(Callable<V> c)
            throws RemoteException, ServiceSpecificException {
        int retryCount = 0;
        while (retryCount < SYNC_OP_RETRY_MAX_COUNT) {
            retryCount++;
            try {
                return c.call();
            } catch (ServiceSpecificException e) {
                if (e.errorCode != SYNC_OP_LIMIT_TRY_AGAIN) {
                    throw e;
                }
                // If car service don't have enough binder thread to handle this request. Sleep for
                // 10ms and try again.
                Log.d(TAG, "too many sync request, sleeping for " + SYNC_OP_RETRY_SLEEP_IN_MS
                        + " ms before retry");
                SystemClock.sleep(SYNC_OP_RETRY_SLEEP_IN_MS);
                continue;
            } catch (RuntimeException | RemoteException e) {
                throw e;
            } catch (Exception e) {
                Log.e(TAG, "catching unexpected exception for getProperty/setProperty", e);
                return null;
            }
        }
        throw new ServiceSpecificException(VehicleHalStatusCode.STATUS_INTERNAL_ERROR,
                "failed to call car service sync operations after " + retryCount + " retries");
    }

    /**
     * Return CarPropertyValue
     *
     * <p> This method may take couple seconds to complete, so it needs to be called from an
     * non-main thread.
     *
     * <p> Clients that declare a {@link android.content.pm.ApplicationInfo#targetSdkVersion} equal
     * or later than {@link Build.VERSION_CODES#R} will receive the following exceptions when
     * request is failed.
     * <ul>
     *     <li>{@link CarInternalErrorException}
     *     <li>{@link PropertyAccessDeniedSecurityException}
     *     <li>{@link PropertyNotAvailableAndRetryException}
     *     <li>{@link PropertyNotAvailableException}
     *     <li>{@link IllegalArgumentException}
     * </ul>
     * <p> Clients that declare a {@link android.content.pm.ApplicationInfo#targetSdkVersion}
     * earlier than {@link Build.VERSION_CODES#R} will receive the following exceptions when request
     * is failed.
     * <ul>
     *     <li>{@link IllegalStateException} when there is an error detected in cars.
     *     <li>{@link IllegalArgumentException} when the property in the areaId is not supplied.
     * </ul>
     *
     * @param clazz The class object for the CarPropertyValue
     * @param propId Property ID to get
     * @param areaId Zone of the property to get
     *
     * @throws {@link CarInternalErrorException} when there is an error detected in cars.
     * @throws {@link PropertyAccessDeniedSecurityException} when cars denied the access of the
     * property.
     * @throws {@link PropertyNotAvailableAndRetryException} when the property is temporarily
     * not available and likely that retrying will be successful.
     * @throws {@link PropertyNotAvailableException} when the property is temporarily not available.
     * @throws {@link IllegalArgumentException} when the property in the areaId is not supplied.
     *
     * @return CarPropertyValue. Null if property's id is invalid.
     */
    @SuppressWarnings("unchecked")
    @Nullable
    @AddedInOrBefore(majorVersion = 33)
    public <E> CarPropertyValue<E> getProperty(@NonNull Class<E> clazz, int propId, int areaId) {
        if (DBG) {
            Log.d(TAG, "getProperty, propId: 0x" + toHexString(propId)
                    + ", areaId: 0x" + toHexString(areaId) + ", class: " + clazz);
        }

        checkSupportedProperty(propId);

        try {
            CarPropertyValue<E> propVal = mService.getProperty(propId, areaId);
            if (propVal != null && propVal.getValue() != null) {
                Class<?> actualClass = propVal.getValue().getClass();
                if (actualClass != clazz) {
                    throw new IllegalArgumentException("Invalid property type. " + "Expected: "
                            + clazz + ", but was: " + actualClass);
                }
            }
            return propVal;
        } catch (RemoteException e) {
            return handleRemoteExceptionFromCarService(e, null);
        } catch (ServiceSpecificException e) {
            // For pre R apps, throws the old exceptions.
            if (mAppTargetSdk < Build.VERSION_CODES.R) {
                if (e.errorCode == VehicleHalStatusCode.STATUS_TRY_AGAIN) {
                    return null;
                } else {
                    throw new IllegalStateException(String.format("Failed to get property: 0x%x, "
                            + "areaId: 0x%x", propId, areaId));
                }
            }
            return handleCarServiceSpecificException(e, propId, areaId, null);
        }
    }

    /**
     * Query CarPropertyValue with property id and areaId.
     *
     * <p> This method may take couple seconds to complete, so it needs to be called from an
     * non-main thread.
     *
     * <p> Clients that declare a {@link android.content.pm.ApplicationInfo#targetSdkVersion} equal
     * or later than {@link Build.VERSION_CODES#R} will receive the following exceptions when
     * request is failed.
     * <ul>
     *     <li>{@link CarInternalErrorException}
     *     <li>{@link PropertyAccessDeniedSecurityException}
     *     <li>{@link PropertyNotAvailableAndRetryException}
     *     <li>{@link PropertyNotAvailableException}
     *     <li>{@link IllegalArgumentException}
     * </ul>
     * <p> Clients that declare a {@link android.content.pm.ApplicationInfo#targetSdkVersion}
     * earlier than {@link Build.VERSION_CODES#R} will receive the following exceptions when request
     * is failed.
     * <ul>
     *     <li>{@link IllegalStateException} when there is an error detected in cars.
     *     <li>{@link IllegalArgumentException} when the property in the areaId is not supplied.
     * </ul>
     *
     * @param propId Property Id
     * @param areaId areaId
     * @param <E> Value type of the property
     *
     * @throws {@link CarInternalErrorException} when there is an error detected in cars.
     * @throws {@link PropertyAccessDeniedSecurityException} when cars denied the access of the
     * property.
     * @throws {@link PropertyNotAvailableAndRetryException} when the property is temporarily
     * not available and likely that retrying will be successful.
     * @throws {@link PropertyNotAvailableException} when the property is temporarily not available.
     * @throws {@link IllegalArgumentException} when the property in the areaId is not supplied.
     *
     * @return CarPropertyValue. Null if property's id is invalid.
     */
    @Nullable
    @AddedInOrBefore(majorVersion = 33)
    public <E> CarPropertyValue<E> getProperty(int propId, int areaId) {
        checkSupportedProperty(propId);

        try {
            CarPropertyValue<E> propVal = runSyncOperation(() -> {
                return mService.getProperty(propId, areaId);
            });
            return propVal;
        } catch (RemoteException e) {
            return handleRemoteExceptionFromCarService(e, null);
        } catch (ServiceSpecificException e) {
            if (mAppTargetSdk < Build.VERSION_CODES.R) {
                if (e.errorCode == VehicleHalStatusCode.STATUS_TRY_AGAIN) {
                    return null;
                } else {
                    throw new IllegalStateException(String.format("Failed to get property: 0x%x, "
                            + "areaId: 0x%x", propId, areaId));
                }
            }
            return handleCarServiceSpecificException(e, propId, areaId, null);
        }
    }

    /**
     * Set value of car property by areaId.
     *
     * <p>If multiple clients set a property for the same area id simultaneously, which one takes
     * precedence is undefined. Typically, the last set operation (in the order that they are issued
     * to the car's ECU) overrides the previous set operations.
     *
     * <p> This method may take couple seconds to complete, so it needs to be called form an
     * non-main thread.
     *
     * <p> Clients that declare a {@link android.content.pm.ApplicationInfo#targetSdkVersion} equal
     * or later than {@link Build.VERSION_CODES#R} will receive the following exceptions when
     * request is failed.
     * <ul>
     *     <li>{@link CarInternalErrorException}
     *     <li>{@link PropertyAccessDeniedSecurityException}
     *     <li>{@link PropertyNotAvailableAndRetryException}
     *     <li>{@link PropertyNotAvailableException}
     *     <li>{@link IllegalArgumentException}
     * </ul>
     * <p> Clients that declare a {@link android.content.pm.ApplicationInfo#targetSdkVersion}
     * earlier than {@link Build.VERSION_CODES#R} will receive the following exceptions when request
     * is failed.
     * <ul>
     *     <li>{@link RuntimeException} when the property is temporarily not available.
     *     <li>{@link IllegalStateException} when there is an error detected in cars.
     *     <li>{@link IllegalArgumentException} when the property in the areaId is not supplied
     * </ul>
     *
     * @param clazz The class object for the CarPropertyValue
     * @param propId Property ID
     * @param areaId areaId
     * @param val Value of CarPropertyValue
     * @param <E> data type of the given property, for example property that was
     * defined as {@code VEHICLE_VALUE_TYPE_INT32} in vehicle HAL could be accessed using
     * {@code Integer.class}.
     *
     * @throws {@link CarInternalErrorException} when there is an error detected in cars.
     * @throws {@link PropertyAccessDeniedSecurityException} when cars denied the access of the
     * property.
     * @throws {@link PropertyNotAvailableException} when the property is temporarily not available.
     * @throws {@link PropertyNotAvailableAndRetryException} when the property is temporarily
     * not available and likely that retrying will be successful.
     * @throws {@link IllegalStateException} when get an unexpected error code.
     * @throws {@link IllegalArgumentException} when the property in the areaId is not supplied.
     */
    @AddedInOrBefore(majorVersion = 33)
    public <E> void setProperty(@NonNull Class<E> clazz, int propId, int areaId, @NonNull E val) {
        if (DBG) {
            Log.d(TAG, "setProperty, propId: 0x" + toHexString(propId)
                    + ", areaId: 0x" + toHexString(areaId) + ", class: " + clazz + ", val: " + val);
        }
        checkSupportedProperty(propId);
        try {
            runSyncOperation(() -> {
                mService.setProperty(new CarPropertyValue<>(propId, areaId, val),
                        mCarPropertyEventToService);
                return null;
            });
        } catch (RemoteException e) {
            handleRemoteExceptionFromCarService(e);
            return;
        } catch (ServiceSpecificException e) {
            if (mAppTargetSdk < Build.VERSION_CODES.R) {
                if (e.errorCode == VehicleHalStatusCode.STATUS_TRY_AGAIN) {
                    throw new RuntimeException(String.format("Failed to set property: 0x%x, "
                            + "areaId: 0x%x", propId, areaId));
                } else {
                    throw new IllegalStateException(String.format("Failed to set property: 0x%x, "
                            + "areaId: 0x%x", propId, areaId));
                }
            }
            handleCarServiceSpecificException(e, propId, areaId, null);
        }
    }

    /**
     * Modifies a property.  If the property modification doesn't occur, an error event shall be
     * generated and propagated back to the application.
     *
     * <p> This method may take couple seconds to complete, so it needs to be called from an
     * non-main thread.
     *
     * @param prop Property ID to modify
     * @param areaId AreaId to apply the modification.
     * @param val Value to set
     */
    @AddedInOrBefore(majorVersion = 33)
    public void setBooleanProperty(int prop, int areaId, boolean val) {
        setProperty(Boolean.class, prop, areaId, val);
    }

    /**
     * Set float value of property
     *
     * <p> This method may take couple seconds to complete, so it needs to be called from an
     * non-main thread.
     *
     * @param prop Property ID to modify
     * @param areaId AreaId to apply the modification
     * @param val Value to set
     */
    @AddedInOrBefore(majorVersion = 33)
    public void setFloatProperty(int prop, int areaId, float val) {
        setProperty(Float.class, prop, areaId, val);
    }

    /**
     * Set int value of property
     *
     * <p> This method may take couple seconds to complete, so it needs to be called from an
     * non-main thread.
     *
     * @param prop Property ID to modify
     * @param areaId AreaId to apply the modification
     * @param val Value to set
     */
    @AddedInOrBefore(majorVersion = 33)
    public void setIntProperty(int prop, int areaId, int val) {
        setProperty(Integer.class, prop, areaId, val);
    }

    // Handles ServiceSpecificException in CarService for R and later version.
    private <T> T handleCarServiceSpecificException(
            ServiceSpecificException e, int propId, int areaId, T returnValue) {
        // We are not passing the error message down, so log it here.
        Log.w(TAG, "received ServiceSpecificException: " + e);
        int errorCode = e.errorCode;
        switch (errorCode) {
            case VehicleHalStatusCode.STATUS_NOT_AVAILABLE:
                throw new PropertyNotAvailableException(propId, areaId);
            case VehicleHalStatusCode.STATUS_TRY_AGAIN:
                throw new PropertyNotAvailableAndRetryException(propId, areaId);
            case VehicleHalStatusCode.STATUS_ACCESS_DENIED:
                throw new PropertyAccessDeniedSecurityException(propId, areaId);
            case VehicleHalStatusCode.STATUS_INTERNAL_ERROR:
                throw new CarInternalErrorException(propId, areaId);
            default:
                Log.e(TAG, "Invalid errorCode: " + errorCode + " in CarService");
        }
        return returnValue;
    }

    /**
     * Checks if the given property can be exposed to by this manager.
     *
     * <p>For example, properties related to user management should only be manipulated by
     * {@code UserHalService}.
     *
     * @param propId property to be checked
     *
     * @throws IllegalArgumentException if the property is not supported.
     */
    private void checkSupportedProperty(int propId) {
        switch (propId) {
            case VehiclePropertyIds.INITIAL_USER_INFO:
            case VehiclePropertyIds.SWITCH_USER:
            case VehiclePropertyIds.CREATE_USER:
            case VehiclePropertyIds.REMOVE_USER:
            case VehiclePropertyIds.USER_IDENTIFICATION_ASSOCIATION:
                throw new IllegalArgumentException("Unsupported property: "
                        + VehiclePropertyIds.toString(propId) + " (" + propId + ")");
        }
    }

    /** @hide */
    @Override
    @AddedInOrBefore(majorVersion = 33)
    public void onCarDisconnected() {
        synchronized (mLock) {
            mPropertyIdToCarPropertyEventCallbackController.clear();
        }
    }
}
