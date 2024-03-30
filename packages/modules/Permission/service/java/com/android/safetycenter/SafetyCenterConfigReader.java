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

package com.android.safetycenter;

import static android.os.Build.VERSION_CODES.TIRAMISU;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.StringRes;
import android.content.Context;
import android.content.res.Resources;
import android.safetycenter.config.SafetyCenterConfig;
import android.util.Log;

import androidx.annotation.RequiresApi;

import com.android.safetycenter.config.ParseException;
import com.android.safetycenter.config.SafetyCenterConfigParser;
import com.android.safetycenter.resources.SafetyCenterResourcesContext;

import java.io.InputStream;

/**
 * A class that reads the {@link SafetyCenterConfig} from the associated {@link
 * SafetyCenterResourcesContext}.
 */
@RequiresApi(TIRAMISU)
final class SafetyCenterConfigReader {

    private static final String TAG = "SafetyCenterConfigReade";

    private final Object mSafetyCenterConfigLock = new Object();
    @NonNull private final SafetyCenterResourcesContext mSafetyCenterResourcesContext;

    @Nullable private SafetyCenterConfig mSafetyCenterConfig;

    /**
     * Creates a {@link SafetyCenterConfigReader} from a {@link Context} object by wrapping it into
     * a {@link SafetyCenterResourcesContext}.
     */
    SafetyCenterConfigReader(@NonNull Context context) {
        mSafetyCenterResourcesContext = new SafetyCenterResourcesContext(context);
    }

    /**
     * Returns the {@link SafetyCenterConfig} read by {@link #loadSafetyCenterConfig()}.
     *
     * <p>Returns {@code null} if {@link #loadSafetyCenterConfig()} was never called or if there was
     * an issue when reading the {@link SafetyCenterConfig}.
     */
    @Nullable
    SafetyCenterConfig getSafetyCenterConfig() {
        if (mSafetyCenterConfig == null) {
            synchronized (mSafetyCenterConfigLock) {
                return mSafetyCenterConfig;
            }
        }
        return mSafetyCenterConfig;
    }

    /**
     * Returns a {@link String} resource from the given {@code stringId}, using the {@link
     * SafetyCenterResourcesContext}.
     *
     * <p>Returns {@code null} if the resources cannot be accessed or if {@code stringId} is equal
     * to {@link Resources#ID_NULL}. Otherwise, throws a {@link Resources.NotFoundException} if the
     * {@code stringId} is invalid.
     */
    @Nullable
    String readStringResource(@StringRes int stringId) {
        if (stringId == Resources.ID_NULL) {
            return null;
        }

        Resources resources = mSafetyCenterResourcesContext.getResources();
        if (resources == null) {
            return null;
        }

        return resources.getString(stringId);
    }

    /**
     * Loads the {@link SafetyCenterConfig} for it to be available when calling {@link
     * #getSafetyCenterConfig()}.
     */
    void loadSafetyCenterConfig() {
        synchronized (mSafetyCenterConfigLock) {
            mSafetyCenterConfig = readSafetyCenterConfig();
        }
    }

    @Nullable
    private SafetyCenterConfig readSafetyCenterConfig() {
        InputStream in = mSafetyCenterResourcesContext.getSafetyCenterConfig();
        if (in == null) {
            Log.e(TAG, "Cannot get safety center config file");
            return null;
        }

        Resources resources = mSafetyCenterResourcesContext.getResources();
        if (resources == null) {
            Log.e(TAG, "Cannot get safety center resources");
            return null;
        }

        try {
            SafetyCenterConfig safetyCenterConfig =
                    SafetyCenterConfigParser.parseXmlResource(in, resources);
            Log.i(TAG, "SafetyCenterConfig read successfully");
            return safetyCenterConfig;
        } catch (ParseException e) {
            Log.e(TAG, "Cannot read SafetyCenterConfig", e);
            return null;
        }
    }
}
