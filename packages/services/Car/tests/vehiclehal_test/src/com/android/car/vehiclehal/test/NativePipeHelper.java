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
package com.android.car.vehiclehal.test;

import android.os.NativeHandle;
import android.os.ParcelFileDescriptor;

import java.io.IOException;

// A helper class to create a native pipe used in debug functions.
final class NativePipeHelper {
    // The maximum size of the output buffer during the test.
    private static final int BUFFER_SIZE = 10_240;

    private ParcelFileDescriptor mWriter;
    private ParcelFileDescriptor.AutoCloseInputStream mReadStream;

    public void create() throws IOException {
        ParcelFileDescriptor[] pipe;
        pipe = ParcelFileDescriptor.createPipe();
        ParcelFileDescriptor reader = new ParcelFileDescriptor(pipe[0]);
        mReadStream = new ParcelFileDescriptor.AutoCloseInputStream(reader);
        mWriter = new ParcelFileDescriptor(pipe[1]);
    }

    public NativeHandle getNativeHandle() {
        return new NativeHandle(mWriter.getFileDescriptor(), false);
    }

    public String getOutput() throws IOException {
        if (mReadStream.available() == 0) {
            return "";
        }
        byte[] buffer = new byte[BUFFER_SIZE];
        int size = mReadStream.read(buffer);
        return new String(buffer, 0, size);
    }

    public void close() throws IOException {
        mWriter.close();
        mReadStream.close();
    }
}
