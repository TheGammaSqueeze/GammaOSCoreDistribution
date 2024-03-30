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

package android.app.sdksandbox;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.pm.ApplicationInfo;
import android.content.res.AssetManager;
import android.content.res.Resources;

/**
 * A wrapper context that is used by a {@link SandboxedSdkProvider}.
 *
 * @hide
 */
public class SandboxedSdkContext extends ContextWrapper {

    private final Context mBaseContext;
    private final Resources mResources;
    private final AssetManager mAssets;

    public SandboxedSdkContext(@NonNull Context baseContext, @NonNull ApplicationInfo info) {
        super(baseContext);
        mBaseContext = baseContext;
        Resources resources = null;
        try {
            resources = mBaseContext.getPackageManager().getResourcesForApplication(info);
        } catch (Exception ignored) {
        }

        if (resources != null) {
            mResources = resources;
            mAssets = resources.getAssets();
        } else {
            mResources = null;
            mAssets = null;
        }
    }

    @Override
    @Nullable
    public Resources getResources() {
        return mResources;
    }

    @Override
    @Nullable
    public AssetManager getAssets() {
        return mAssets;
    }
}
