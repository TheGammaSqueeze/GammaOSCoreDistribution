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

package com.android.libraries.testing.deviceshadower.shadows.common;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;

import com.android.libraries.testing.deviceshadower.internal.DeviceShadowEnvironmentImpl;
import com.android.libraries.testing.deviceshadower.internal.DeviceletImpl;
import com.android.libraries.testing.deviceshadower.internal.common.BroadcastManager;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.RealObject;
import org.robolectric.shadows.ShadowContextImpl;

import javax.annotation.Nullable;

/**
 * Extends {@link ShadowContextImpl} to achieve automatic method redirection to correct virtual
 * device.
 *
 * <p>Supports:
 * <li>Broadcasting</li>
 * Includes send regular, regular sticky, ordered broadcast, and register/unregister receiver.
 * </p>
 */
@Implements(className = "android.app.ContextImpl")
public class DeviceShadowContextImpl extends ShadowContextImpl {

    private static final String TAG = "DeviceShadowContextImpl";

    @RealObject
    private Context mContextImpl;

    @Override
    @Implementation
    @Nullable
    public Intent registerReceiver(BroadcastReceiver receiver, IntentFilter filter) {
        if (receiver == null) {
            return null;
        }
        BroadcastManager manager = getLocalBroadcastManager();
        if (manager == null) {
            Log.w(TAG, "Receiver registered before any devices added: " + receiver);
            return null;
        }
        return manager.registerReceiver(
                receiver, filter, null /* permission */, null /* handler */, mContextImpl);
    }

    @Override
    @Implementation
    @Nullable
    public Intent registerReceiver(BroadcastReceiver receiver, IntentFilter filter,
            @Nullable String broadcastPermission, @Nullable Handler scheduler) {
        return getLocalBroadcastManager().registerReceiver(
                receiver, filter, broadcastPermission, scheduler, mContextImpl);
    }

    @Override
    @Implementation
    public void unregisterReceiver(BroadcastReceiver broadcastReceiver) {
        getLocalBroadcastManager().unregisterReceiver(broadcastReceiver);
    }

    @Override
    @Implementation
    public void sendBroadcast(Intent intent) {
        getLocalBroadcastManager().sendBroadcast(intent, null /* permission */);
    }

    @Override
    @Implementation
    public void sendBroadcast(Intent intent, @Nullable String receiverPermission) {
        getLocalBroadcastManager().sendBroadcast(intent, receiverPermission);
    }

    @Override
    @Implementation
    public void sendOrderedBroadcast(Intent intent, @Nullable String receiverPermission) {
        getLocalBroadcastManager().sendOrderedBroadcast(intent, receiverPermission);
    }

    @Override
    @Implementation
    public void sendOrderedBroadcast(Intent intent, @Nullable String receiverPermission,
            @Nullable BroadcastReceiver resultReceiver, @Nullable Handler scheduler,
            int initialCode, @Nullable String initialData, @Nullable Bundle initialExtras) {
        getLocalBroadcastManager().sendOrderedBroadcast(intent, receiverPermission, resultReceiver,
                scheduler, initialCode, initialData, initialExtras, mContextImpl);
    }

    @Override
    @Implementation
    public void sendStickyBroadcast(Intent intent) {
        getLocalBroadcastManager().sendStickyBroadcast(intent);
    }

    private BroadcastManager getLocalBroadcastManager() {
        DeviceletImpl devicelet = DeviceShadowEnvironmentImpl.getLocalDeviceletImpl();
        if (devicelet == null) {
            return null;
        }
        return devicelet.getBroadcastManager();
    }
}
