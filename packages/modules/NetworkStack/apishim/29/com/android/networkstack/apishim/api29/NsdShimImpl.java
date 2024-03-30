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

package com.android.networkstack.apishim.api29;

import android.net.Network;
import android.net.NetworkRequest;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.android.networkstack.apishim.common.NsdShim;
import com.android.networkstack.apishim.common.UnsupportedApiLevelException;

import java.util.concurrent.Executor;

/**
 * Implementation of {@link NsdShim}.
 */
@RequiresApi(Build.VERSION_CODES.Q)
public class NsdShimImpl implements NsdShim {

    /**
     * Get a new instance of {@link NsdShim}.
     */
    public static NsdShim newInstance() {
        return new NsdShimImpl();
    }

    @Nullable
    @Override
    public Network getNetwork(@NonNull NsdServiceInfo serviceInfo) {
        // NsdServiceInfo has no Network before T
        return null;
    }

    @Override
    public void setNetwork(@NonNull NsdServiceInfo serviceInfo, @Nullable Network network) {
        // No-op: NsdServiceInfo has no Network before T
    }

    @Override
    public void registerService(@NonNull NsdManager nsdManager, @NonNull NsdServiceInfo serviceInfo,
            int protocolType, @NonNull Executor executor,
            @NonNull NsdManager.RegistrationListener listener) throws UnsupportedApiLevelException {
        throw new UnsupportedApiLevelException("Register with an executor is only supported on T+");
    }

    @Override
    public void discoverServices(@NonNull NsdManager nsdManager, @NonNull String serviceType,
            int protocolType, @Nullable Network network,
            @NonNull Executor executor, @NonNull NsdManager.DiscoveryListener listener)
            throws UnsupportedApiLevelException {
        throw new UnsupportedApiLevelException("Discover on network is only supported on T+");
    }

    @Override
    public void discoverServices(@NonNull NsdManager nsdManager, @NonNull String serviceType,
            int protocolType, @Nullable NetworkRequest request,
            @NonNull Executor executor, @NonNull NsdManager.DiscoveryListener listener)
            throws UnsupportedApiLevelException {
        throw new UnsupportedApiLevelException(
                "Discover with NetworkRequest is only supported on T+");
    }

    @Override
    public void resolveService(@NonNull NsdManager nsdManager, @NonNull NsdServiceInfo serviceInfo,
            @NonNull Executor executor, @NonNull NsdManager.ResolveListener resolveListener)
            throws UnsupportedApiLevelException {
        throw new UnsupportedApiLevelException("Resolve with executor is only supported on T+");
    }
}
