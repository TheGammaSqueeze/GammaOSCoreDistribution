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

import android.net.LocalSocket;

import com.android.internal.annotations.VisibleForTesting;
import com.android.libraries.testing.deviceshadower.internal.DeviceShadowEnvironmentImpl;
import com.android.libraries.testing.deviceshadower.internal.bluetooth.BluetoothConstants;
import com.android.libraries.testing.deviceshadower.internal.bluetooth.connection.RfcommDelegate;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

import java.io.FileDescriptor;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Shadow implementation of a LocalSocket to make bluetooth connections function.
 */
@Implements(LocalSocket.class)
public class ShadowLocalSocket {

    private String mRemoteAddress;
    private FileDescriptor mFd;
    private FileDescriptor mAncillaryFd;

    public ShadowLocalSocket() {
    }

    public void __constructor__(FileDescriptor fd) {
        this.mFd = fd;
    }

    @Implementation
    public FileDescriptor[] getAncillaryFileDescriptors() throws IOException {
        return new FileDescriptor[]{mAncillaryFd};
    }

    @Implementation
    @SuppressWarnings("InputStreamSlowMultibyteRead")
    public InputStream getInputStream() throws IOException {
        final RfcommDelegate local = getLocalRfcommDelegate();
        return new InputStream() {
            @Override
            public int read() throws IOException {
                int res = local.read(mRemoteAddress, mFd);
                if (res == BluetoothConstants.SOCKET_CLOSE) {
                    throw new IOException("closed");
                }
                return res & 0xFF;
            }
        };
    }

    @Implementation
    public OutputStream getOutputStream() throws IOException {
        final RfcommDelegate local = getLocalRfcommDelegate();
        return new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                local.write(mRemoteAddress, mFd, b);
            }
        };
    }

    @Implementation
    public void setSoTimeout(int n) throws IOException {
        // Nothing
    }

    @Implementation
    public void shutdownInput() throws IOException {
        getLocalRfcommDelegate().shutdownInput(mRemoteAddress, mFd);
    }

    @Implementation
    public void shutdownOutput() throws IOException {
        if (mRemoteAddress == null) {
            return;
        }
        getLocalRfcommDelegate().shutdownOutput(mRemoteAddress, mFd);
    }

    void setAncillaryFd(FileDescriptor fd) {
        mAncillaryFd = fd;
    }

    void setRemoteAddress(String address) {
        mRemoteAddress = address;
    }

    @VisibleForTesting
    void setFileDescriptorForTest(FileDescriptor fd) {
        this.mFd = fd;
    }

    private RfcommDelegate getLocalRfcommDelegate() {
        return DeviceShadowEnvironmentImpl.getLocalBlueletImpl().getRfcommDelegate();
    }
}
