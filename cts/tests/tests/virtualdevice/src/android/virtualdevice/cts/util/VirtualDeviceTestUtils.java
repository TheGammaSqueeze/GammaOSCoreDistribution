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

package android.virtualdevice.cts.util;

import android.app.ActivityOptions;
import android.hardware.display.VirtualDisplay;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcel;
import android.os.ResultReceiver;

/**
 * Test utilities for Virtual Device tests.
 */
public final class VirtualDeviceTestUtils {

    public static ResultReceiver createResultReceiver(OnReceiveResultListener listener) {
        ResultReceiver receiver = new ResultReceiver(new Handler(Looper.getMainLooper())) {
            @Override
            protected void onReceiveResult(int resultCode, Bundle resultData) {
                listener.onReceiveResult(resultCode, resultData);
            }
        };
        // Erase the subclass to make the given result receiver safe to include inside Bundles
        // (See b/177985835).
        Parcel parcel = Parcel.obtain();
        receiver.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        receiver = ResultReceiver.CREATOR.createFromParcel(parcel);
        parcel.recycle();
        return receiver;
    }

    /**
     * Interface mimicking {@link ResultReceiver}, allowing it to be mocked.
     */
    public interface OnReceiveResultListener {
        void onReceiveResult(int resultCode, Bundle resultData);
    }

    public static Bundle createActivityOptions(VirtualDisplay virtualDisplay) {
        return ActivityOptions.makeBasic()
                .setLaunchDisplayId(virtualDisplay.getDisplay().getDisplayId())
                .toBundle();
    }

    private VirtualDeviceTestUtils() {}
}
