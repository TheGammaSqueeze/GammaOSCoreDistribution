/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.car.hal;

import static android.os.SystemClock.elapsedRealtime;

import android.car.builtin.util.Slogf;
import android.hardware.automotive.vehicle.StatusCode;
import android.hardware.automotive.vehicle.SubscribeOptions;
import android.hardware.automotive.vehicle.VehiclePropError;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.os.ServiceSpecificException;

import com.android.car.CarLog;
import com.android.car.VehicleStub;
import com.android.car.VehicleStub.SubscriptionClient;
import com.android.car.internal.util.DebugUtils;
import com.android.internal.annotations.VisibleForTesting;

import java.lang.ref.WeakReference;
import java.util.ArrayList;

/**
 * Vehicle HAL client. Interacts directly with Vehicle HAL interface {@link IVehicle}. Contains
 * some logic for retriable properties, redirects Vehicle notifications into given looper thread.
 */
final class HalClient {

    @VisibleForTesting
    static final String TAG = CarLog.tagFor(HalClient.class);

    /**
     * If call to vehicle HAL returns StatusCode.TRY_AGAIN, than {@link HalClient} will retry to
     * invoke that method again for this amount of milliseconds.
     */
    private static final int WAIT_CAP_FOR_RETRIABLE_RESULT_MS = 2000;

    private static final int SLEEP_BETWEEN_RETRIABLE_INVOKES_MS = 50;

    private final VehicleStub mVehicle;
    private final SubscriptionClient mSubscriptionClient;
    private final VehicleCallback mInternalCallback;
    private final int mWaitCapMs;
    private final int mSleepMs;

    /**
     * Create HalClient object
     *
     * @param vehicle interface to the vehicle HAL
     * @param looper looper that will be used to propagate notifications from vehicle HAL
     * @param callback to propagate notifications from Vehicle HAL in the provided looper thread
     */
    HalClient(VehicleStub vehicle, Looper looper, HalClientCallback callback) {
        this(
                vehicle,
                looper,
                callback,
                WAIT_CAP_FOR_RETRIABLE_RESULT_MS,
                SLEEP_BETWEEN_RETRIABLE_INVOKES_MS);
    }

    @VisibleForTesting
    HalClient(VehicleStub vehicle, Looper looper, HalClientCallback callback, int waitCapMs,
            int sleepMs) {
        mVehicle = vehicle;
        Handler handler = new CallbackHandler(looper, callback);
        mInternalCallback = new VehicleCallback(handler);
        mSubscriptionClient = vehicle.newSubscriptionClient(mInternalCallback);
        mWaitCapMs = waitCapMs;
        mSleepMs = sleepMs;
    }

    HalClientCallback getInternalCallback() {
        return mInternalCallback;
    }

    HalPropConfig[] getAllPropConfigs()
            throws RemoteException, ServiceSpecificException {
        return mVehicle.getAllPropConfigs();
    }

    public void subscribe(SubscribeOptions... options)
            throws RemoteException, ServiceSpecificException {
        mSubscriptionClient.subscribe(options);
    }

    public void unsubscribe(int prop) throws RemoteException, ServiceSpecificException {
        mSubscriptionClient.unsubscribe(prop);
    }

    public void setValue(HalPropValue propValue)
            throws IllegalArgumentException, ServiceSpecificException {
        ObjectWrapper<String> errorMsgWrapper = new ObjectWrapper<>();
        errorMsgWrapper.object = new String();

        int status = invokeRetriable(() -> {
            try {
                mVehicle.set(propValue);
                errorMsgWrapper.object = new String();
                return StatusCode.OK;
            } catch (RemoteException e) {
                errorMsgWrapper.object = e.toString();
                return StatusCode.TRY_AGAIN;
            } catch (ServiceSpecificException e) {
                errorMsgWrapper.object = e.toString();
                return e.errorCode;
            }
        }, mWaitCapMs, mSleepMs);

        String errorMsg = errorMsgWrapper.object;

        if (StatusCode.INVALID_ARG == status) {
            throw new IllegalArgumentException(getValueErrorMessage("set", propValue, errorMsg));
        }

        if (StatusCode.OK != status) {
            throw new ServiceSpecificException(
                    status, getValueErrorMessage("set", propValue, errorMsg));
        }
    }

    public HalPropValueBuilder getHalPropValueBuilder() {
        return mVehicle.getHalPropValueBuilder();
    }

    private String getValueErrorMessage(String action, HalPropValue propValue, String errorMsg) {
        return "Failed to " + action + " value for: 0x"
                + Integer.toHexString(propValue.getPropId()) + ", areaId: 0x"
                + Integer.toHexString(propValue.getAreaId()) + ", error: " + errorMsg;
    }

    HalPropValue getValue(HalPropValue requestedPropValue)
            throws IllegalArgumentException, ServiceSpecificException {
        // Use a wrapper to create a final object passed to lambda.
        ObjectWrapper<ValueResult> resultWrapper = new ObjectWrapper<>();
        resultWrapper.object = new ValueResult();
        int status = invokeRetriable(() -> {
            resultWrapper.object = internalGet(requestedPropValue);
            return resultWrapper.object.status;
        }, mWaitCapMs, mSleepMs);

        ValueResult result = resultWrapper.object;

        if (StatusCode.INVALID_ARG == status) {
            throw new IllegalArgumentException(
                    getValueErrorMessage("get", requestedPropValue, result.errorMsg));
        }

        if (StatusCode.OK != status || result.propValue == null) {
            // If propValue is null and status is StatusCode.Ok, change the status to be
            // NOT_AVAILABLE.
            if (StatusCode.OK == status) {
                status = StatusCode.NOT_AVAILABLE;
            }
            throw new ServiceSpecificException(
                    status, getValueErrorMessage("get", requestedPropValue, result.errorMsg));
        }

        return result.propValue;
    }

    private ValueResult internalGet(HalPropValue requestedPropValue) {
        final ValueResult result = new ValueResult();
        try {
            result.propValue = mVehicle.get(requestedPropValue);
            result.status = StatusCode.OK;
            result.errorMsg = new String();
        } catch (ServiceSpecificException e) {
            result.status = e.errorCode;
            result.errorMsg = e.toString();
        } catch (RemoteException e) {
            result.status = StatusCode.TRY_AGAIN;
            result.errorMsg = e.toString();
        }

        return result;
    }

    interface RetriableCallback {
        /** Returns {@link StatusCode} */
        int action();
    }

    private static int invokeRetriable(RetriableCallback callback, long timeoutMs, long sleepMs) {
        int status = callback.action();
        long startTime = elapsedRealtime();
        while (StatusCode.TRY_AGAIN == status && (elapsedRealtime() - startTime) < timeoutMs) {
            Slogf.d(TAG, "Status before sleeping %d ms: %d", sleepMs, status);
            try {
                Thread.sleep(sleepMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                Slogf.w(TAG, "Thread was interrupted while waiting for vehicle HAL.", e);
                break;
            }

            status = callback.action();
            Slogf.d(TAG, "Status after waking up: %s", DebugUtils.constantToString(
                    StatusCode.class, status));
        }
        Slogf.d(TAG, "Returning status: %s", DebugUtils.constantToString(
                StatusCode.class, status));
        return status;
    }

    private static final class ObjectWrapper<T> {
        public T object;
    }

    private static final class ValueResult {
        public int status;
        public String errorMsg = new String();
        public HalPropValue propValue;
    }

    private static final class CallbackHandler extends Handler {
        private static final int MSG_ON_PROPERTY_EVENT = 1;
        private static final int MSG_ON_SET_ERROR = 2;

        private final WeakReference<HalClientCallback> mCallback;

        CallbackHandler(Looper looper, HalClientCallback callback) {
            super(looper);
            mCallback = new WeakReference<HalClientCallback>(callback);
        }

        @Override
        public void handleMessage(Message msg) {
            HalClientCallback callback = mCallback.get();
            if (callback == null) {
                Slogf.i(TAG, "handleMessage null callback");
                return;
            }

            switch (msg.what) {
                case MSG_ON_PROPERTY_EVENT:
                    callback.onPropertyEvent((ArrayList<HalPropValue>) msg.obj);
                    break;
                case MSG_ON_SET_ERROR:
                    callback.onPropertySetError((ArrayList<VehiclePropError>) msg.obj);
                    break;
                default:
                    Slogf.e(TAG, "Unexpected message: %d", msg.what);
            }
        }
    }

    @VisibleForTesting
    static final class VehicleCallback implements HalClientCallback {
        private final Handler mHandler;

        VehicleCallback(Handler handler) {
            mHandler = handler;
        }

        @Override
        public void onPropertyEvent(ArrayList<HalPropValue> propValues) {
            mHandler.sendMessage(Message.obtain(
                    mHandler, CallbackHandler.MSG_ON_PROPERTY_EVENT, propValues));
        }

        @Override
        public void onPropertySetError(ArrayList<VehiclePropError> errors) {
            mHandler.sendMessage(Message.obtain(
                        mHandler, CallbackHandler.MSG_ON_SET_ERROR, errors));
        }
    }
}
