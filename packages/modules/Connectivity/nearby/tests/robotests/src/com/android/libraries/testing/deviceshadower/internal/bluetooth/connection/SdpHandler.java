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

import com.android.libraries.testing.deviceshadower.internal.utils.Logger;

import com.google.common.base.Preconditions;
import com.google.common.collect.Sets;

import java.io.FileDescriptor;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Encapsulates SDP operations including creating service record and allocating channel.
 * <p>Listen on port and connect on port are not supported. </p>
 */
public class SdpHandler {

    // intended to use "RfcommDelegate"
    private static final Logger LOGGER = Logger.create("RfcommDelegate");

    private final Object mLock;
    private final String mAddress;
    private final Map<UUID, ServiceRecord> mServiceRecords;
    private final Map<FileDescriptor, UUID> mFdUuidMap;
    private final Set<Integer> mAvailablePortPool;
    private final Set<Integer> mInUsePortPool;

    public SdpHandler(String address) {
        mLock = new Object();
        this.mAddress = address;
        mServiceRecords = new ConcurrentHashMap<>();
        mFdUuidMap = new ConcurrentHashMap<>();
        mAvailablePortPool = Sets.newConcurrentHashSet();
        mInUsePortPool = Sets.newConcurrentHashSet();
        // 1 to 30 are valid RFCOMM port
        for (int i = 1; i <= 30; i++) {
            mAvailablePortPool.add(i);
        }
    }

    public ServiceRecord createServiceRecord(UUID uuid, String serviceName) {
        Preconditions.checkNotNull(uuid);
        LOGGER.d(String.format("Address %s: createServiceRecord with uuid %s", mAddress, uuid));
        if (isUuidRegistered(uuid) || !checkChannelAvailability()) {
            return null;
        }
        synchronized (mLock) {
            // ensure uuid is not registered and there's available channel
            if (isUuidRegistered(uuid) || !checkChannelAvailability()) {
                return null;
            }
            Iterator<Integer> available = mAvailablePortPool.iterator();
            int port = available.next();
            mAvailablePortPool.remove(port);
            mInUsePortPool.add(port);
            ServiceRecord record = new ServiceRecord(mAddress, serviceName, port);
            mServiceRecords.put(uuid, record);
            mFdUuidMap.put(record.mServerSocketFd, uuid);
            PageScanHandler.getInstance().addServerSocket(record.mServerSocketFd);
            return record;
        }
    }

    public void removeServiceRecord(UUID uuid) {
        Preconditions.checkNotNull(uuid);
        LOGGER.d(String.format("Address %s: removeServiceRecord with uuid %s", mAddress, uuid));
        if (!isUuidRegistered(uuid)) {
            return;
        }
        synchronized (mLock) {
            if (!isUuidRegistered(uuid)) {
                return;
            }
            ServiceRecord record = mServiceRecords.get(uuid);
            mServiceRecords.remove(uuid);
            mInUsePortPool.remove(record.mPort);
            mAvailablePortPool.add(record.mPort);
            mFdUuidMap.remove(record.mServerSocketFd);
        }
    }

    public ServiceRecord lookupChannel(UUID uuid) {
        ServiceRecord record = mServiceRecords.get(uuid);
        if (record == null) {
            LOGGER.e(String.format("Address %s: uuid %s not registered.", mAddress, uuid));
        }
        return record;
    }

    public UUID getUuid(FileDescriptor serverSocketFd) {
        return mFdUuidMap.get(serverSocketFd);
    }

    private boolean isUuidRegistered(UUID uuid) {
        if (mServiceRecords.containsKey(uuid)) {
            LOGGER.d(String.format("Address %s: Uuid %s in use.", mAddress, uuid));
            return true;
        }
        LOGGER.d(String.format("Address %s: Uuid %s not registered.", mAddress, uuid));
        return false;
    }

    private boolean checkChannelAvailability() {
        if (mAvailablePortPool.isEmpty()) {
            LOGGER.e(String.format("Address %s: No available channel.", mAddress));
            return false;
        }
        return true;
    }

    static class ServiceRecord {

        final FileDescriptor mServerSocketFd;
        final String mServiceName;
        final int mPort;

        ServiceRecord(String address, String serviceName, int port) {
            mServerSocketFd = FileDescriptorFactory.getInstance().createFileDescriptor(address);
            this.mServiceName = serviceName;
            this.mPort = port;
        }
    }
}
