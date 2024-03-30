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

package src.com.android.server.nearby.fastpair;

import static com.google.common.truth.Truth.assertThat;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.filters.SdkSuppress;

import com.android.server.nearby.common.eventloop.EventLoop;
import com.android.server.nearby.common.locator.Locator;
import com.android.server.nearby.fastpair.FastPairAdvHandler;
import com.android.server.nearby.fastpair.FastPairModule;
import com.android.server.nearby.fastpair.cache.FastPairCacheManager;
import com.android.server.nearby.fastpair.footprint.FootprintsDeviceManager;
import com.android.server.nearby.fastpair.halfsheet.FastPairHalfSheetManager;

import org.junit.Before;
import org.junit.Test;
import org.mockito.MockitoAnnotations;

import java.time.Clock;

import src.com.android.server.nearby.fastpair.testing.MockingLocator;

public class ModuleTest {
    private Locator mLocator;


    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mLocator = MockingLocator.withMocksOnly(ApplicationProvider.getApplicationContext());
        mLocator.bind(new FastPairModule());
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void genericConstructor() {
        assertThat(mLocator.get(FastPairCacheManager.class)).isNotNull();
        assertThat(mLocator.get(FootprintsDeviceManager.class)).isNotNull();
        assertThat(mLocator.get(EventLoop.class)).isNotNull();
        assertThat(mLocator.get(FastPairHalfSheetManager.class)).isNotNull();
        assertThat(mLocator.get(FastPairAdvHandler.class)).isNotNull();
        assertThat(mLocator.get(Clock.class)).isNotNull();
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void genericDestroy() {
        mLocator.destroy();
    }
}
