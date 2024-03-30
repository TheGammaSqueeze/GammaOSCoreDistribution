/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.server.nearby.common.locator;

import android.annotation.Nullable;
import android.content.Context;
import android.content.ContextWrapper;

/**
 * Wraps a Context and associates it with a Locator, optionally linking it with a parent locator.
 */
public class LocatorContextWrapper extends ContextWrapper implements LocatorContext {
    private final Locator mLocator;
    private final Context mContext;
    /** Constructs a context wrapper with a Locator linked to the passed locator. */
    public LocatorContextWrapper(Context context, @Nullable Locator parentLocator) {
        super(context);
        mContext = context;
        // Assigning under initialization object, but it's safe, since locator is used lazily.
        this.mLocator = new Locator(this, parentLocator);
    }

    /**
     * Constructs a context wrapper.
     *
     * <p>Uses the Locator associated with the passed context as the parent.
     */
    public LocatorContextWrapper(Context context) {
        this(context, Locator.findLocator(context));
    }

    /**
     * Get the context of the context wrapper.
     */
    public Context getContext() {
        return mContext;
    }

    @Override
    public Locator getLocator() {
        return mLocator;
    }
}
