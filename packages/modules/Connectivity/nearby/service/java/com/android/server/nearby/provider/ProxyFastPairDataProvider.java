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

package com.android.server.nearby.provider;

import android.annotation.Nullable;
import android.content.Context;
import android.nearby.aidl.FastPairAccountDevicesMetadataRequestParcel;
import android.nearby.aidl.FastPairAccountKeyDeviceMetadataParcel;
import android.nearby.aidl.FastPairAntispoofKeyDeviceMetadataParcel;
import android.nearby.aidl.FastPairAntispoofKeyDeviceMetadataRequestParcel;
import android.nearby.aidl.FastPairEligibleAccountParcel;
import android.nearby.aidl.FastPairEligibleAccountsRequestParcel;
import android.nearby.aidl.FastPairManageAccountDeviceRequestParcel;
import android.nearby.aidl.FastPairManageAccountRequestParcel;
import android.nearby.aidl.IFastPairAccountDevicesMetadataCallback;
import android.nearby.aidl.IFastPairAntispoofKeyDeviceMetadataCallback;
import android.nearby.aidl.IFastPairDataProvider;
import android.nearby.aidl.IFastPairEligibleAccountsCallback;
import android.nearby.aidl.IFastPairManageAccountCallback;
import android.nearby.aidl.IFastPairManageAccountDeviceCallback;
import android.os.IBinder;
import android.os.RemoteException;

import androidx.annotation.WorkerThread;

import com.android.server.nearby.common.servicemonitor.CurrentUserServiceProvider;
import com.android.server.nearby.common.servicemonitor.CurrentUserServiceProvider.BoundServiceInfo;
import com.android.server.nearby.common.servicemonitor.ServiceMonitor;
import com.android.server.nearby.common.servicemonitor.ServiceMonitor.ServiceListener;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Proxy for IFastPairDataProvider implementations.
 */
public class ProxyFastPairDataProvider implements ServiceListener<BoundServiceInfo> {

    private static final int TIME_OUT_MILLIS = 10000;

    /**
     * Creates and registers this proxy. If no suitable service is available for the proxy, returns
     * null.
     */
    @Nullable
    public static ProxyFastPairDataProvider create(Context context, String action) {
        ProxyFastPairDataProvider proxy = new ProxyFastPairDataProvider(context, action);
        if (proxy.checkServiceResolves()) {
            return proxy;
        } else {
            return null;
        }
    }

    private final ServiceMonitor mServiceMonitor;

    private ProxyFastPairDataProvider(Context context, String action) {
        // safe to use direct executor since our locks are not acquired in a code path invoked by
        // our owning provider

        mServiceMonitor = ServiceMonitor.create(context, "FAST_PAIR_DATA_PROVIDER",
                CurrentUserServiceProvider.create(context, action), this);
    }

    private boolean checkServiceResolves() {
        return mServiceMonitor.checkServiceResolves();
    }

    /**
     * User service watch to connect to actually services implemented by OEMs.
     */
    public void register() {
        mServiceMonitor.register();
    }

    // Fast Pair Data Provider doesn't maintain a long running state.
    // Therefore, it doesn't need setup at bind time.
    @Override
    public void onBind(IBinder binder, BoundServiceInfo boundServiceInfo) throws RemoteException {
    }

    // Fast Pair Data Provider doesn't maintain a long running state.
    // Therefore, it doesn't need tear down at unbind time.
    @Override
    public void onUnbind() {
    }

    /**
     * Invokes system api loadFastPairEligibleAccounts.
     *
     * @return an array of acccounts and their opt in status.
     */
    @WorkerThread
    @Nullable
    public FastPairEligibleAccountParcel[] loadFastPairEligibleAccounts(
            FastPairEligibleAccountsRequestParcel requestParcel) {
        final CountDownLatch waitForCompletionLatch = new CountDownLatch(1);
        final AtomicReference<FastPairEligibleAccountParcel[]> response = new AtomicReference<>();
        mServiceMonitor.runOnBinder(new ServiceMonitor.BinderOperation() {
            @Override
            public void run(IBinder binder) throws RemoteException {
                IFastPairDataProvider provider = IFastPairDataProvider.Stub.asInterface(binder);
                IFastPairEligibleAccountsCallback callback =
                        new IFastPairEligibleAccountsCallback.Stub() {
                            public void onFastPairEligibleAccountsReceived(
                                    FastPairEligibleAccountParcel[] accountParcels) {
                                response.set(accountParcels);
                                waitForCompletionLatch.countDown();
                            }

                            public void onError(int code, String message) {
                                waitForCompletionLatch.countDown();
                            }
                        };
                provider.loadFastPairEligibleAccounts(requestParcel, callback);
            }

            @Override
            public void onError() {
                waitForCompletionLatch.countDown();
            }
        });
        try {
            waitForCompletionLatch.await(TIME_OUT_MILLIS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            // skip.
        }
        return response.get();
    }

    /**
     * Invokes system api manageFastPairAccount to opt in account, or opt out account.
     */
    @WorkerThread
    public void manageFastPairAccount(FastPairManageAccountRequestParcel requestParcel) {
        final CountDownLatch waitForCompletionLatch = new CountDownLatch(1);
        mServiceMonitor.runOnBinder(new ServiceMonitor.BinderOperation() {
            @Override
            public void run(IBinder binder) throws RemoteException {
                IFastPairDataProvider provider = IFastPairDataProvider.Stub.asInterface(binder);
                IFastPairManageAccountCallback callback =
                        new IFastPairManageAccountCallback.Stub() {
                            public void onSuccess() {
                                waitForCompletionLatch.countDown();
                            }

                            public void onError(int code, String message) {
                                waitForCompletionLatch.countDown();
                            }
                        };
                provider.manageFastPairAccount(requestParcel, callback);
            }

            @Override
            public void onError() {
                waitForCompletionLatch.countDown();
            }
        });
        try {
            waitForCompletionLatch.await(TIME_OUT_MILLIS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            // skip.
        }
        return;
    }

    /**
     * Invokes system api manageFastPairAccountDevice to add or remove a device from a Fast Pair
     * account.
     */
    @WorkerThread
    public void manageFastPairAccountDevice(
            FastPairManageAccountDeviceRequestParcel requestParcel) {
        final CountDownLatch waitForCompletionLatch = new CountDownLatch(1);
        mServiceMonitor.runOnBinder(new ServiceMonitor.BinderOperation() {
            @Override
            public void run(IBinder binder) throws RemoteException {
                IFastPairDataProvider provider = IFastPairDataProvider.Stub.asInterface(binder);
                IFastPairManageAccountDeviceCallback callback =
                        new IFastPairManageAccountDeviceCallback.Stub() {
                            public void onSuccess() {
                                waitForCompletionLatch.countDown();
                            }

                            public void onError(int code, String message) {
                                waitForCompletionLatch.countDown();
                            }
                        };
                provider.manageFastPairAccountDevice(requestParcel, callback);
            }

            @Override
            public void onError() {
                waitForCompletionLatch.countDown();
            }
        });
        try {
            waitForCompletionLatch.await(TIME_OUT_MILLIS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            // skip.
        }
        return;
    }

    /**
     * Invokes system api loadFastPairAntispoofKeyDeviceMetadata.
     *
     * @return the Fast Pair AntispoofKeyDeviceMetadata of a given device.
     */
    @WorkerThread
    @Nullable
    FastPairAntispoofKeyDeviceMetadataParcel loadFastPairAntispoofKeyDeviceMetadata(
            FastPairAntispoofKeyDeviceMetadataRequestParcel requestParcel) {
        final CountDownLatch waitForCompletionLatch = new CountDownLatch(1);
        final AtomicReference<FastPairAntispoofKeyDeviceMetadataParcel> response =
                new AtomicReference<>();
        mServiceMonitor.runOnBinder(new ServiceMonitor.BinderOperation() {
            @Override
            public void run(IBinder binder) throws RemoteException {
                IFastPairDataProvider provider = IFastPairDataProvider.Stub.asInterface(binder);
                IFastPairAntispoofKeyDeviceMetadataCallback callback =
                        new IFastPairAntispoofKeyDeviceMetadataCallback.Stub() {
                            public void onFastPairAntispoofKeyDeviceMetadataReceived(
                                    FastPairAntispoofKeyDeviceMetadataParcel metadata) {
                                response.set(metadata);
                                waitForCompletionLatch.countDown();
                            }

                            public void onError(int code, String message) {
                                waitForCompletionLatch.countDown();
                            }
                        };
                provider.loadFastPairAntispoofKeyDeviceMetadata(requestParcel, callback);
            }

            @Override
            public void onError() {
                waitForCompletionLatch.countDown();
            }
        });
        try {
            waitForCompletionLatch.await(TIME_OUT_MILLIS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            // skip.
        }
        return response.get();
    }

    /**
     * Invokes loadFastPairAccountDevicesMetadata.
     *
     * @return the metadata of Fast Pair devices that are associated with a given account.
     */
    @WorkerThread
    @Nullable
    FastPairAccountKeyDeviceMetadataParcel[] loadFastPairAccountDevicesMetadata(
            FastPairAccountDevicesMetadataRequestParcel requestParcel) {
        final CountDownLatch waitForCompletionLatch = new CountDownLatch(1);
        final AtomicReference<FastPairAccountKeyDeviceMetadataParcel[]> response =
                new AtomicReference<>();
        mServiceMonitor.runOnBinder(new ServiceMonitor.BinderOperation() {
            @Override
            public void run(IBinder binder) throws RemoteException {
                IFastPairDataProvider provider = IFastPairDataProvider.Stub.asInterface(binder);
                IFastPairAccountDevicesMetadataCallback callback =
                        new IFastPairAccountDevicesMetadataCallback.Stub() {
                            public void onFastPairAccountDevicesMetadataReceived(
                                    FastPairAccountKeyDeviceMetadataParcel[] metadatas) {
                                response.set(metadatas);
                                waitForCompletionLatch.countDown();
                            }

                            public void onError(int code, String message) {
                                waitForCompletionLatch.countDown();
                            }
                        };
                provider.loadFastPairAccountDevicesMetadata(requestParcel, callback);
            }

            @Override
            public void onError() {
                waitForCompletionLatch.countDown();
            }
        });
        try {
            waitForCompletionLatch.await(TIME_OUT_MILLIS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            // skip.
        }
        return response.get();
    }
}
