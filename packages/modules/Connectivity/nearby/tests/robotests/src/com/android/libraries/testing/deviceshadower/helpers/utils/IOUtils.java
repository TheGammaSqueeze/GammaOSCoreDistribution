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

package com.android.libraries.testing.deviceshadower.helpers.utils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;

/**
 * Utils for IO methods.
 */
public class IOUtils {

    /**
     * Write num of bytes to be sent and payload through OutputStream.
     */
    public static void write(OutputStream os, byte[] data) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(4 + data.length).putInt(data.length).put(data);
        os.write(buffer.array());
    }

    /**
     * Read num of bytes to be read, and payload through InputStream.
     *
     * @return payload received.
     */
    public static byte[] read(InputStream is) throws IOException {
        byte[] size = new byte[4];
        is.read(size, 0, 4 /* bytes of int type */);

        byte[] data = new byte[ByteBuffer.wrap(size).getInt()];
        is.read(data);
        return data;
    }

}
