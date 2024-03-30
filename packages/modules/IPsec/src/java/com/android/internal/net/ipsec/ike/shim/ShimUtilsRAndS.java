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
import android.net.ipsec.ike.exceptions.IkeInternalException;
import android.net.ipsec.ike.exceptions.IkeNetworkLostException;

import java.io.IOException;

/** Shim utilities for SDK R, S and S-V2 */
public class ShimUtilsRAndS extends ShimUtils {
    // Package protected constructor for ShimUtils to access
    ShimUtilsRAndS() {
        super();
    }

    @Override
    public IkeException getWrappedIkeException(Exception exception) {
        if (exception instanceof IkeException) {
            return (IkeException) exception;
        }

        return new IkeInternalException(exception);
    }

    @Override
    public Exception getRetransmissionFailedException(String errMsg) {
        return new IOException(errMsg);
    }

    @Override
    public IOException getDnsFailedException(String errMsg) {
        return new IOException(errMsg);
    }

    @Override
    public void onUnderlyingNetworkDiedWithoutMobility(
            IIkeSessionStateMachineShim ikeSession, Network network) {
        ikeSession.onNonFatalError(new IkeNetworkLostException(network));
    }
}
