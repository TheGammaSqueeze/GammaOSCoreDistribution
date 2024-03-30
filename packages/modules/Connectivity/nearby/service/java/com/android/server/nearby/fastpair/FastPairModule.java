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

package com.android.server.nearby.fastpair;

import android.content.Context;

import com.android.server.nearby.common.eventloop.EventLoop;
import com.android.server.nearby.common.locator.Locator;
import com.android.server.nearby.common.locator.Module;
import com.android.server.nearby.fastpair.cache.FastPairCacheManager;
import com.android.server.nearby.fastpair.footprint.FootprintsDeviceManager;
import com.android.server.nearby.fastpair.halfsheet.FastPairHalfSheetManager;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

/**
 * Module that associates all of the fast pair related singleton class
 */
public class FastPairModule extends Module {
    /**
     * Initiate the class that needs to be singleton.
     */
    @Override
    public void configure(Context context, Class<?> type, Locator locator) {
        if (type.equals(FastPairCacheManager.class)) {
            locator.bind(FastPairCacheManager.class, new FastPairCacheManager(context));
        } else if (type.equals(FootprintsDeviceManager.class)) {
            locator.bind(FootprintsDeviceManager.class, new FootprintsDeviceManager());
        } else if (type.equals(EventLoop.class)) {
            locator.bind(EventLoop.class, EventLoop.newInstance("NearbyFastPair"));
        } else if (type.equals(FastPairController.class)) {
            locator.bind(FastPairController.class, new FastPairController(context));
        } else if (type.equals(FastPairCacheManager.class)) {
            locator.bind(FastPairCacheManager.class, new FastPairCacheManager(context));
        } else if (type.equals(FastPairHalfSheetManager.class)) {
            locator.bind(FastPairHalfSheetManager.class, new FastPairHalfSheetManager(context));
        } else if (type.equals(FastPairAdvHandler.class)) {
            locator.bind(FastPairAdvHandler.class, new FastPairAdvHandler(context));
        } else if (type.equals(Clock.class)) {
            locator.bind(Clock.class, new Clock() {
                @Override
                public ZoneId getZone() {
                    return null;
                }

                @Override
                public Clock withZone(ZoneId zone) {
                    return null;
                }

                @Override
                public Instant instant() {
                    return null;
                }
            });
        }

    }

    /**
     * Clean up the singleton classes.
     */
    @Override
    public void destroy(Context context, Class<?> type, Object instance) {
        super.destroy(context, type, instance);
    }
}
