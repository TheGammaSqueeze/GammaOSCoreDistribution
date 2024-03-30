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
package com.android.server.adservices;

import android.content.Context;
import android.util.Log;

import com.android.server.SystemService;

/**
 * @hide
 */
public class AdServicesManagerService {
    private static final String TAG = "AdServicesManagerService";

    private final Context mContext;

    private AdServicesManagerService(Context context) {
        mContext = context;
    }

    /** @hide */
    public static class Lifecycle extends SystemService {
        /** @hide */
        public Lifecycle(Context context) {
            super(context);
        }

        /** @hide */
        @Override
        public void onStart() {
            AdServicesManagerService service =
                    new AdServicesManagerService(getContext());
            Log.i(TAG, "AdServicesManagerService started!");
        }
    }
}
