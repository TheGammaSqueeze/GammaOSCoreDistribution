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

package com.android.server.nearby.common.servicemonitor;

import static android.content.Context.BIND_AUTO_CREATE;
import static android.content.Context.BIND_NOT_FOREGROUND;

import android.annotation.Nullable;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.util.Log;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.util.Preconditions;
import com.android.server.nearby.common.servicemonitor.ServiceMonitor.BoundServiceInfo;
import com.android.server.nearby.common.servicemonitor.ServiceMonitor.ServiceChangedListener;

import java.io.PrintWriter;
import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * Implementation of ServiceMonitor. Keeping the implementation separate from the interface allows
 * us to store the generic relationship between the service provider and the service listener, while
 * hiding the generics from clients, simplifying the API.
 */
class ServiceMonitorImpl<TBoundServiceInfo extends BoundServiceInfo> implements ServiceMonitor,
        ServiceChangedListener {

    private static final String TAG = "ServiceMonitor";
    private static final boolean D = Log.isLoggable(TAG, Log.DEBUG);
    private static final long RETRY_DELAY_MS = 15 * 1000;

    // This is the same as Context.BIND_NOT_VISIBLE.
    private static final int BIND_NOT_VISIBLE = 0x40000000;

    final Context mContext;
    final Handler mHandler;
    final Executor mExecutor;
    final String mTag;
    final ServiceProvider<TBoundServiceInfo> mServiceProvider;
    final @Nullable ServiceListener<? super TBoundServiceInfo> mServiceListener;

    private final PackageWatcher mPackageWatcher = new PackageWatcher() {
        @Override
        public void onSomePackagesChanged() {
            onServiceChanged(false);
        }
    };

    @GuardedBy("this")
    private boolean mRegistered = false;
    @GuardedBy("this")
    private MyServiceConnection mServiceConnection = new MyServiceConnection(null);

    ServiceMonitorImpl(Context context, Handler handler, Executor executor, String tag,
            ServiceProvider<TBoundServiceInfo> serviceProvider,
            ServiceListener<? super TBoundServiceInfo> serviceListener) {
        mContext = context;
        mExecutor = executor;
        mHandler = handler;
        mTag = tag;
        mServiceProvider = serviceProvider;
        mServiceListener = serviceListener;
    }

    @Override
    public boolean checkServiceResolves() {
        return mServiceProvider.hasMatchingService();
    }

    @Override
    public synchronized void register() {
        Preconditions.checkState(!mRegistered);

        mRegistered = true;
        mPackageWatcher.register(mContext, /*externalStorage=*/ true, mHandler);
        mServiceProvider.register(this);

        onServiceChanged(false);
    }

    @Override
    public synchronized void unregister() {
        Preconditions.checkState(mRegistered);

        mServiceProvider.unregister();
        mPackageWatcher.unregister();
        mRegistered = false;

        onServiceChanged(false);
    }

    @Override
    public synchronized void onServiceChanged() {
        onServiceChanged(false);
    }

    @Override
    public synchronized void runOnBinder(BinderOperation operation) {
        MyServiceConnection serviceConnection = mServiceConnection;
        mHandler.post(() -> serviceConnection.runOnBinder(operation));
    }

    synchronized void onServiceChanged(boolean forceRebind) {
        TBoundServiceInfo newBoundServiceInfo;
        if (mRegistered) {
            newBoundServiceInfo = mServiceProvider.getServiceInfo();
        } else {
            newBoundServiceInfo = null;
        }

        if (forceRebind || !Objects.equals(mServiceConnection.getBoundServiceInfo(),
                newBoundServiceInfo)) {
            Log.i(TAG, "[" + mTag + "] chose new implementation " + newBoundServiceInfo);
            MyServiceConnection oldServiceConnection = mServiceConnection;
            MyServiceConnection newServiceConnection = new MyServiceConnection(newBoundServiceInfo);
            mServiceConnection = newServiceConnection;
            mHandler.post(() -> {
                oldServiceConnection.unbind();
                newServiceConnection.bind();
            });
        }
    }

    @Override
    public String toString() {
        MyServiceConnection serviceConnection;
        synchronized (this) {
            serviceConnection = mServiceConnection;
        }

        return serviceConnection.getBoundServiceInfo().toString();
    }

    @Override
    public void dump(PrintWriter pw) {
        MyServiceConnection serviceConnection;
        synchronized (this) {
            serviceConnection = mServiceConnection;
        }

        pw.println("target service=" + serviceConnection.getBoundServiceInfo());
        pw.println("connected=" + serviceConnection.isConnected());
    }

    // runs on the handler thread, and expects most of its methods to be called from that thread
    private class MyServiceConnection implements ServiceConnection {

        private final @Nullable TBoundServiceInfo mBoundServiceInfo;

        // volatile so that isConnected can be called from any thread easily
        private volatile @Nullable IBinder mBinder;
        private @Nullable Runnable mRebinder;

        MyServiceConnection(@Nullable TBoundServiceInfo boundServiceInfo) {
            mBoundServiceInfo = boundServiceInfo;
        }

        // may be called from any thread
        @Nullable TBoundServiceInfo getBoundServiceInfo() {
            return mBoundServiceInfo;
        }

        // may be called from any thread
        boolean isConnected() {
            return mBinder != null;
        }

        void bind() {
            Preconditions.checkState(Looper.myLooper() == mHandler.getLooper());

            if (mBoundServiceInfo == null) {
                return;
            }

            if (D) {
                Log.d(TAG, "[" + mTag + "] binding to " + mBoundServiceInfo);
            }

            Intent bindIntent = new Intent(mBoundServiceInfo.getAction())
                    .setComponent(mBoundServiceInfo.getComponentName());
            if (!mContext.bindService(bindIntent,
                    BIND_AUTO_CREATE | BIND_NOT_FOREGROUND | BIND_NOT_VISIBLE,
                    mExecutor, this)) {
                Log.e(TAG, "[" + mTag + "] unexpected bind failure - retrying later");
                mRebinder = this::bind;
                mHandler.postDelayed(mRebinder, RETRY_DELAY_MS);
            } else {
                mRebinder = null;
            }
        }

        void unbind() {
            Preconditions.checkState(Looper.myLooper() == mHandler.getLooper());

            if (mBoundServiceInfo == null) {
                return;
            }

            if (D) {
                Log.d(TAG, "[" + mTag + "] unbinding from " + mBoundServiceInfo);
            }

            if (mRebinder != null) {
                mHandler.removeCallbacks(mRebinder);
                mRebinder = null;
            } else {
                mContext.unbindService(this);
            }

            onServiceDisconnected(mBoundServiceInfo.getComponentName());
        }

        void runOnBinder(BinderOperation operation) {
            Preconditions.checkState(Looper.myLooper() == mHandler.getLooper());

            if (mBinder == null) {
                operation.onError();
                return;
            }

            try {
                operation.run(mBinder);
            } catch (RuntimeException | RemoteException e) {
                // binders may propagate some specific non-RemoteExceptions from the other side
                // through the binder as well - we cannot allow those to crash the system server
                Log.e(TAG, "[" + mTag + "] error running operation on " + mBoundServiceInfo, e);
                operation.onError();
            }
        }

        @Override
        public final void onServiceConnected(ComponentName component, IBinder binder) {
            Preconditions.checkState(Looper.myLooper() == mHandler.getLooper());
            Preconditions.checkState(mBinder == null);

            Log.i(TAG, "[" + mTag + "] connected to " + component.toShortString());

            mBinder = binder;

            if (mServiceListener != null) {
                try {
                    mServiceListener.onBind(binder, mBoundServiceInfo);
                } catch (RuntimeException | RemoteException e) {
                    // binders may propagate some specific non-RemoteExceptions from the other side
                    // through the binder as well - we cannot allow those to crash the system server
                    Log.e(TAG, "[" + mTag + "] error running operation on " + mBoundServiceInfo, e);
                }
            }
        }

        @Override
        public final void onServiceDisconnected(ComponentName component) {
            Preconditions.checkState(Looper.myLooper() == mHandler.getLooper());

            if (mBinder == null) {
                return;
            }

            Log.i(TAG, "[" + mTag + "] disconnected from " + mBoundServiceInfo);

            mBinder = null;
            if (mServiceListener != null) {
                mServiceListener.onUnbind();
            }
        }

        @Override
        public final void onBindingDied(ComponentName component) {
            Preconditions.checkState(Looper.myLooper() == mHandler.getLooper());

            Log.w(TAG, "[" + mTag + "] " + mBoundServiceInfo + " died");

            // introduce a small delay to prevent spamming binding over and over, since the likely
            // cause of a binding dying is some package event that may take time to recover from
            mHandler.postDelayed(() -> onServiceChanged(true), 500);
        }

        @Override
        public final void onNullBinding(ComponentName component) {
            Log.e(TAG, "[" + mTag + "] " + mBoundServiceInfo + " has null binding");
        }
    }
}
