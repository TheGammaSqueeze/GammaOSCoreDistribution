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


import android.annotation.Nullable;
import android.app.Activity;
import android.content.Intent;

/**
 * An empty activity to allow this CTS test to get foreground status, for things like accessing
 * clipboard data.
 */
public class EmptyActivity extends Activity {

    public interface Callback {
        void onActivityResult(int requestCode, int resultCode, Intent data);
    }

    @Nullable private Callback mCallback;

    public void setCallback(@Nullable Callback callback) {
        mCallback = callback;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (mCallback != null) {
            mCallback.onActivityResult(requestCode, resultCode, data);
        }
    }
}
