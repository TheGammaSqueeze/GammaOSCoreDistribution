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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;

import androidx.test.filters.SdkSuppress;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.server.nearby.common.locator.LocatorContextWrapper;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;


public class FastPairManagerTest {
    private FastPairManager mFastPairManager;
    @Mock private Context mContext;
    private LocatorContextWrapper mLocatorContextWrapper;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);

        mLocatorContextWrapper = new LocatorContextWrapper(mContext);
        mFastPairManager = new FastPairManager(mLocatorContextWrapper);
        when(mContext.getContentResolver()).thenReturn(
                InstrumentationRegistry.getInstrumentation().getContext().getContentResolver());
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void testFastPairInit() {
        mFastPairManager.initiate();

        verify(mContext, times(1)).registerReceiver(any(), any());
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void testFastPairCleanUp() {
        mFastPairManager.cleanUp();

        verify(mContext, times(1)).unregisterReceiver(any());
    }
}
