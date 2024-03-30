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

package com.android.server.appsearch.visibilitystore;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.AttributionSource;

import com.android.server.appsearch.external.localstorage.visibilitystore.CallerAccess;

import java.util.Objects;

/**
 * Contains attributes of an API caller relevant to its access via visibility store.
 *
 * @hide
 */
public class FrameworkCallerAccess extends CallerAccess {
    private final AttributionSource mAttributionSource;
    private final boolean mCallerHasSystemAccess;

    /**
     * Constructs a new {@link CallerAccess}.
     *
     * @param callerAttributionSource The permission identity of the caller
     * @param callerHasSystemAccess Whether {@code callingPackageName} has access to schema types
     *     marked visible to system via {@link
     *     android.app.appsearch.SetSchemaRequest.Builder#setSchemaTypeDisplayedBySystem}.
     */
    public FrameworkCallerAccess(
            @NonNull AttributionSource callerAttributionSource,boolean callerHasSystemAccess) {
        super(callerAttributionSource.getPackageName());
        mAttributionSource = callerAttributionSource;
        mCallerHasSystemAccess = callerHasSystemAccess;
    }

    /** Returns the permission identity {@link AttributionSource} of the caller. */
    @NonNull
    public AttributionSource getCallingAttributionSource() {
        return mAttributionSource;
    }

    /**
     * Returns whether {@code callingPackageName} has access to schema types marked visible to
     * system via {@link
     * android.app.appsearch.SetSchemaRequest.Builder#setSchemaTypeDisplayedBySystem}.
     */
    public boolean doesCallerHaveSystemAccess() {
        return mCallerHasSystemAccess;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (!(o instanceof FrameworkCallerAccess)) return false;
        FrameworkCallerAccess that = (FrameworkCallerAccess) o;
        return super.equals(o)
                && mCallerHasSystemAccess == that.mCallerHasSystemAccess
                && Objects.equals(mAttributionSource, that.mAttributionSource);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), mAttributionSource, mCallerHasSystemAccess);
    }
}
