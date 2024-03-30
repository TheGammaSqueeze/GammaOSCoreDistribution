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

package android.virtualdevice.cts.common;

import android.annotation.Nullable;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

/**
 * Class for listening for data sent by an activity through {@link Activity#sendBroadcast(Intent)}.
 */
public class ActivityResultReceiver extends BroadcastReceiver {
    /** Action for the activity to send data. */
    public static final String ACTION_SEND_ACTIVITY_RESULT =
            "android.virtualdevice.cts.SEND_ACTIVITY_RESULT";

    /** Extra for sending the computed power spectrum at expected audio frequency. */
    public static final String EXTRA_POWER_SPECTRUM_AT_FREQUENCY = "powerSpectrumAtFrequency";

    /** Extra for sending the computed power spectrum off expected audio frequency. */
    public static final String EXTRA_POWER_SPECTRUM_NOT_FREQUENCY = "powerSpectrumNotFrequency";

    /** Extra for sending the value of recorded audio data. */
    public static final String EXTRA_LAST_RECORDED_NONZERO_VALUE = "lastRecordedNonZeroValue";

    public interface Callback {
        void onActivityResult(Intent data);
    }

    @Nullable
    private Callback mCallback;
    private final Context mContext;

    public ActivityResultReceiver(Context context) {
        mContext = context;
    }

    public void register(@Nullable Callback callback) {
        mCallback = callback;
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_SEND_ACTIVITY_RESULT);
        mContext.registerReceiver(/* receiver= */ this, filter);
    }

    public void unregister() {
        mCallback = null;
        mContext.unregisterReceiver(/* receiver= */ this);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (mCallback != null) {
            mCallback.onActivityResult(intent);
        }
    }
}
