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

package com.android.networkstack.apishim.api33;

import android.net.Network;
import android.net.NetworkRequest;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.android.modules.utils.build.SdkLevel;
import com.android.networkstack.apishim.common.NsdShim;
import com.android.networkstack.apishim.common.UnsupportedApiLevelException;

import java.util.concurrent.Executor;

/**
 * Implementation of {@link com.android.networkstack.apishim.common.NsdShim}.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
public class NsdShimImpl extends com.android.networkstack.apishim.api31.NsdShimImpl {

    /**
     * Get a new instance of {@link NsdShim}.
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    public static NsdShim newInstance() {
        if (SdkLevel.isAtLeastT()) {
            return new NsdShimImpl();
        } else {
            return new com.android.networkstack.apishim.api31.NsdShimImpl();
        }
    }

    @Nullable
    @Override
    public Network getNetwork(@NonNull NsdServiceInfo serviceInfo) {
        return serviceInfo.getNetwork();
    }

    @Override
    public void setNetwork(@NonNull NsdServiceInfo serviceInfo, @Nullable Network network) {
        serviceInfo.setNetwork(network);
    }

    @Override
    public void registerService(@NonNull NsdManager nsdManager, @NonNull NsdServiceInfo serviceInfo,
            int protocolType, @NonNull Executor executor,
            @NonNull NsdManager.RegistrationListener listener) {
        nsdManager.registerService(serviceInfo, protocolType, executor, listener);
    }

    @Override
    public void discoverServices(@NonNull NsdManager nsdManager, @NonNull String serviceType,
            int protocolType, @Nullable Network network,
            @NonNull Executor executor, @NonNull NsdManager.DiscoveryListener listener)
            throws UnsupportedApiLevelException {
        nsdManager.discoverServices(serviceType, protocolType, network, executor, listener);
    }

    @Override
    public void discoverServices(@NonNull NsdManager nsdManager, @NonNull String serviceType,
            int protocolType, @Nullable NetworkRequest request,
            @NonNull Executor executor, @NonNull NsdManager.DiscoveryListener listener)
            throws UnsupportedApiLevelException {
        nsdManager.discoverServices(serviceType, protocolType, request, executor, listener);
    }

    @Override
    public void resolveService(@NonNull NsdManager nsdManager, @NonNull NsdServiceInfo serviceInfo,
            @NonNull Executor executor, @NonNull NsdManager.ResolveListener resolveListener)
            throws UnsupportedApiLevelException {
        nsdManager.resolveService(serviceInfo, executor, resolveListener);
    }
}
