/*
 * Copyright 2022 The Android Open Source Project
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

package android.media.cts;

import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;

/**
 * Contains arguments passed to the tests.
 */
public final class TestArgs {
    private final static String TAG = "TestArgs";
    private static final String CODEC_PREFIX_KEY = "codec-prefix";
    private static final String CODEC_PREFIX;
    private static final String MEDIA_TYPE_PREFIX_KEY = "media-type-prefix";
    private static final String MEDIA_TYPE_PREFIX;

    static {
        android.os.Bundle args = InstrumentationRegistry.getArguments();
        CODEC_PREFIX = args.getString(CODEC_PREFIX_KEY);
        MEDIA_TYPE_PREFIX = args.getString(MEDIA_TYPE_PREFIX_KEY);
    }

    public static boolean shouldSkipMediaType(String mediaType) {
        if (MEDIA_TYPE_PREFIX != null && !mediaType.startsWith(MEDIA_TYPE_PREFIX)) {
            Log.d(TAG, "Skipping tests for mediaType: " + mediaType);
            return true;
        }
        return false;
    }

    public static boolean shouldSkipCodec(String name) {
        if (CODEC_PREFIX != null && !name.startsWith(CODEC_PREFIX)) {
            Log.d(TAG, "Skipping tests for codec: " + name);
            return true;
        }
        return false;
    }
}
