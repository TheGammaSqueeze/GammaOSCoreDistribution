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

package com.android.server.nearby.fastpair.testing;

import com.android.server.nearby.common.locator.LocatorContextWrapper;
import com.android.server.nearby.fastpair.cache.DiscoveryItem;

import service.proto.Cache;

public class FakeDiscoveryItems {
    public static final String DEFAULT_MAC_ADDRESS = "00:11:22:33:44:55";
    public static final long DEFAULT_TIMESTAMP = 1000000000L;
    public static final String DEFAULT_DESCRIPITON = "description";
    public static final String TRIGGER_ID = "trigger.id";
    private static final String FAST_PAIR_ID = "id";
    private static final int RSSI = -80;
    private static final int TX_POWER = -10;
    public static DiscoveryItem newFastPairDiscoveryItem(LocatorContextWrapper contextWrapper) {
        return new DiscoveryItem(contextWrapper, newFastPairDeviceStoredItem());
    }

    public static Cache.StoredDiscoveryItem newFastPairDeviceStoredItem() {
        return newFastPairDeviceStoredItem(TRIGGER_ID);
    }

    public static Cache.StoredDiscoveryItem newFastPairDeviceStoredItem(String triggerId) {
        Cache.StoredDiscoveryItem.Builder item = Cache.StoredDiscoveryItem.newBuilder();
        item.setState(Cache.StoredDiscoveryItem.State.STATE_ENABLED);
        item.setId(FAST_PAIR_ID);
        item.setDescription(DEFAULT_DESCRIPITON);
        item.setTriggerId(triggerId);
        item.setMacAddress(DEFAULT_MAC_ADDRESS);
        item.setFirstObservationTimestampMillis(DEFAULT_TIMESTAMP);
        item.setLastObservationTimestampMillis(DEFAULT_TIMESTAMP);
        item.setRssi(RSSI);
        item.setTxPower(TX_POWER);
        return item.build();
    }

}
