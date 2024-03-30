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

package com.android.internal.net.ipsec.ike.shim;

import android.net.Network;
import android.net.ipsec.ike.exceptions.IkeException;

import com.android.modules.utils.build.SdkLevel;

import java.io.IOException;

/**
 * The base shim utilities class.
 *
 * <p>Subclasses (like ShimUtilsRAndS) are tied to specific Android SDK versions to provide SDK
 * specific behaviors.
 *
 * <p>Callers should call getInstance to get a correct version of ShimUtils object.
 */
public abstract class ShimUtils {
    private static final ShimUtils INSTANCE;

    static {
        if (SdkLevel.isAtLeastT()) {
            INSTANCE = new ShimUtilsMinT();
        } else {
            INSTANCE = new ShimUtilsRAndS();
        }
    }

    // Package protected constructor
    ShimUtils() {}

    /** Get a ShimUtils instance based on the platform version */
    public static ShimUtils getInstance() {
        return INSTANCE;
    }

    /**
     * Wrap the Exception as an IkeException or do nothing if the input is already an IkeException.
     */
    public abstract IkeException getWrappedIkeException(Exception exception);

    /** Create an Exception for a retransmission failure */
    public abstract Exception getRetransmissionFailedException(String errMsg);

    /** Create an IOException for a DNS lookup failure */
    public abstract IOException getDnsFailedException(String errMsg);

    /** Handle network loss on an IkeSessionStateMachine without mobility */
    public abstract void onUnderlyingNetworkDiedWithoutMobility(
            IIkeSessionStateMachineShim ikeSession, Network network);
}
