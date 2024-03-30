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

package android.server.wm.jetpack.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import android.os.IBinder;

import androidx.annotation.NonNull;
import androidx.window.sidecar.SidecarDeviceState;
import androidx.window.sidecar.SidecarInterface.SidecarCallback;
import androidx.window.sidecar.SidecarWindowLayoutInfo;

import java.util.concurrent.CountDownLatch;

public class SidecarCallbackCounter implements SidecarCallback {

    private final IBinder mWindowToken;
    private final int mExpectedCallbackCount;
    private final CountDownLatch mCountDownLatch;

    public SidecarCallbackCounter(IBinder windowToken, int expectedCallbackCount) {
        mWindowToken = windowToken;
        mExpectedCallbackCount = expectedCallbackCount;
        mCountDownLatch = new CountDownLatch(mExpectedCallbackCount);
    }

    @Override
    public void onDeviceStateChanged(@NonNull SidecarDeviceState sidecarDeviceState) {
    }

    @Override
    public void onWindowLayoutChanged(@NonNull IBinder iBinder,
            @NonNull SidecarWindowLayoutInfo sidecarWindowLayoutInfo) {
        assertEquals(iBinder, mWindowToken);
        assertNotNull(sidecarWindowLayoutInfo);
        if (mCountDownLatch.getCount() == 0) {
            fail("onWindowLayoutChanged callback count has exceeded expected amount of: "
                    + mExpectedCallbackCount);
        }
        mCountDownLatch.countDown();
    }

    public void assertZeroCount() {
        assertEquals("Callback count should be zero", 0, mCountDownLatch.getCount());
    }

}
