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
package com.android.networkstack.apishim.api29;

import android.os.Build;

import androidx.annotation.RequiresApi;

import com.android.networkstack.apishim.common.ProcessShim;

/** Implementation of {@link ProcessShim} for API 29. */
@RequiresApi(Build.VERSION_CODES.Q)
public class ProcessShimImpl implements ProcessShim {

    /** Get a new instance of {@link ProcessShim}. */
    @RequiresApi(Build.VERSION_CODES.Q)
    public static ProcessShim newInstance() {
        return new ProcessShimImpl();
    }
}
