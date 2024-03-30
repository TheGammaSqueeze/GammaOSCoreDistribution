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

package android.nearby.fastpair.provider.utils;

import android.util.Log;

import androidx.annotation.Nullable;

import com.google.errorprone.annotations.FormatMethod;

/**
 * The base context for a logging statement.
 */
public class Logger {
    private final String mString;

    public Logger(String tag) {
        this.mString = tag;
    }

    @FormatMethod
    public void log(String message, Object... objects) {
        log(null, message, objects);
    }

    /** Logs to the console. */
    @FormatMethod
    public void log(@Nullable Throwable exception, String message, Object... objects) {
        if (exception == null) {
            Log.i(mString, String.format(message, objects));
        } else {
            Log.w(mString, String.format(message, objects));
            Log.w(mString, String.format("Cause: %s", exception));
        }
    }
}
