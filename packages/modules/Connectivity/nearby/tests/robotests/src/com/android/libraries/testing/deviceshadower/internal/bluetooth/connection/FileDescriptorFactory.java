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

package com.android.libraries.testing.deviceshadower.internal.bluetooth.connection;

import java.io.FileDescriptor;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Factory which creates {@link FileDescriptor} given an MAC address. Each MAC address can have many
 * FileDescriptor but each FileDescriptor only maps to one MAC address.
 */
public class FileDescriptorFactory {

    private static FileDescriptorFactory sInstance = null;

    public static synchronized FileDescriptorFactory getInstance() {
        if (sInstance == null) {
            sInstance = new FileDescriptorFactory();
        }
        return sInstance;
    }

    public static synchronized void reset() {
        sInstance = null;
    }

    private final Map<FileDescriptor, String> mAddressMap;

    private FileDescriptorFactory() {
        mAddressMap = new ConcurrentHashMap<>();
    }

    public FileDescriptor createFileDescriptor(String address) {
        FileDescriptor fd = new FileDescriptor();
        mAddressMap.put(fd, address);
        return fd;
    }

    public String getAddress(FileDescriptor fd) {
        return mAddressMap.get(fd);
    }
}
