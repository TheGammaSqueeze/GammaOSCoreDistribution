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

package com.android.libraries.testing.deviceshadower.shadows.bluetooth;

import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.net.LocalSocket;
import android.os.ParcelFileDescriptor;

import com.android.internal.annotations.VisibleForTesting;
import com.android.libraries.testing.deviceshadower.internal.DeviceShadowEnvironmentImpl;
import com.android.libraries.testing.deviceshadower.internal.bluetooth.connection.RfcommDelegate;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.RealObject;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.util.ReflectionHelpers;
import org.robolectric.util.ReflectionHelpers.ClassParameter;

import java.io.FileDescriptor;
import java.io.IOException;

/**
 * Placeholder for BluetoothServerSocket updates
 */
@Implements(BluetoothServerSocket.class)
public class ShadowBluetoothServerSocket {

    @RealObject
    BluetoothServerSocket mRealServerSocket;

    public ShadowBluetoothServerSocket() {
    }

    @Implementation
    public BluetoothSocket accept(int timeout) throws IOException {
        FileDescriptor serverSocketFd = getServerSocketFileDescriptor();
        if (serverSocketFd == null) {
            throw new IOException("socket is closed.");
        }
        RfcommDelegate local = getLocalRfcommDelegate();
        local.checkInterrupt();
        FileDescriptor clientFd = local.processNextConnectionRequest(serverSocketFd);
        // configure the LocalSocket of the BluetoothServerSocket
        BluetoothSocket internalSocket = ReflectionHelpers.getField(mRealServerSocket, "mSocket");
        ShadowLocalSocket internalLocalSocket = getLocalSocketShadow(internalSocket);
        internalLocalSocket.setAncillaryFd(local.getServerFd(clientFd));

        // call original method
        BluetoothSocket socket = Shadow.directlyOn(mRealServerSocket, BluetoothServerSocket.class,
                "accept", ClassParameter.from(int.class, timeout));

        // setup local socket of the returned BluetoothSocket
        String remoteAddress = socket.getRemoteDevice().getAddress();
        ShadowLocalSocket shadowLocalSocket = getLocalSocketShadow(socket);
        shadowLocalSocket.setRemoteAddress(remoteAddress);
        // init connection to client
        local.initiateConnectToClient(clientFd, getPort());
        local.waitForConnectionEstablished(clientFd);
        return socket;
    }

    @Implementation
    public void close() throws IOException {
        getLocalRfcommDelegate().closeServerSocket(getServerSocketFileDescriptor());
        Shadow.directlyOn(mRealServerSocket, BluetoothServerSocket.class, "close");
    }

    @VisibleForTesting
    FileDescriptor getServerSocketFileDescriptor() {
        BluetoothSocket socket = ReflectionHelpers.getField(mRealServerSocket, "mSocket");
        ParcelFileDescriptor pfd = ReflectionHelpers.getField(socket, "mPfd");
        if (pfd == null) {
            return null;
        }
        return pfd.getFileDescriptor();
    }

    @VisibleForTesting
    int getPort() {
        BluetoothSocket socket = ReflectionHelpers.getField(mRealServerSocket, "mSocket");
        return ReflectionHelpers.getField(socket, "mPort");
    }

    private ShadowLocalSocket getLocalSocketShadow(BluetoothSocket socket) {
        LocalSocket localSocket = ReflectionHelpers.getField(socket, "mSocket");
        return (ShadowLocalSocket) Shadow.extract(localSocket);
    }

    private RfcommDelegate getLocalRfcommDelegate() {
        return DeviceShadowEnvironmentImpl.getLocalBlueletImpl().getRfcommDelegate();
    }
}
