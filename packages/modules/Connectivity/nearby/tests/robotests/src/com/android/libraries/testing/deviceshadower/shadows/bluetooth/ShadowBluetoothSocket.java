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

import android.bluetooth.BluetoothSocket;
import android.os.ParcelFileDescriptor;

import com.android.libraries.testing.deviceshadower.internal.DeviceShadowEnvironmentImpl;
import com.android.libraries.testing.deviceshadower.internal.bluetooth.connection.RfcommDelegate;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.RealObject;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.util.ReflectionHelpers;

import java.io.FileDescriptor;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Shadow implementation of a Bluetooth Socket
 */
@Implements(BluetoothSocket.class)
public class ShadowBluetoothSocket {

    @RealObject
    BluetoothSocket mRealSocket;

    public ShadowBluetoothSocket() {
    }

    @Implementation
    public void connect() throws IOException {
        Shadow.directlyOn(mRealSocket, BluetoothSocket.class, "connect");

        boolean isEncrypted = ReflectionHelpers.getField(mRealSocket, "mEncrypt");
        FileDescriptor localFd =
                ((ParcelFileDescriptor) ReflectionHelpers.getField(mRealSocket,
                        "mPfd")).getFileDescriptor();
        RfcommDelegate local = DeviceShadowEnvironmentImpl.getLocalBlueletImpl()
                .getRfcommDelegate();
        String remoteAddress = mRealSocket.getRemoteDevice().getAddress();
        local.finishPendingConnection(remoteAddress, localFd, isEncrypted);

        ShadowLocalSocket shadowLocalSocket = getLocalSocketShadow();
        shadowLocalSocket.setRemoteAddress(remoteAddress);
    }

    @Implementation
    public InputStream getInputStream() throws IOException {
        ShadowLocalSocket socket = getLocalSocketShadow();
        return socket.getInputStream();
    }

    @Implementation
    public OutputStream getOutputStream() throws IOException {
        ShadowLocalSocket socket = getLocalSocketShadow();
        return socket.getOutputStream();
    }

    private ShadowLocalSocket getLocalSocketShadow() throws IOException {
        try {
            return (ShadowLocalSocket) Shadow.extract(
                    ReflectionHelpers.getField(mRealSocket, "mSocket"));
        } catch (NullPointerException e) {
            throw new IOException(e);
        }
    }
}
