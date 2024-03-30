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
package com.android.internal.net.ipsec.ike;

import android.content.Context;
import android.os.Looper;

import com.android.internal.net.eap.EapAuthenticator;
import com.android.internal.net.ipsec.ike.utils.RandomnessFactory;

/** IkeContext contains all context information of an IKE Session */
public class IkeContext implements EapAuthenticator.EapContext {
    private final Looper mLooper;
    private final Context mContext;
    private final RandomnessFactory mRandomFactory;

    /** Constructor for IkeContext */
    public IkeContext(Looper looper, Context context, RandomnessFactory randomFactory) {
        mLooper = looper;
        mContext = context;
        mRandomFactory = randomFactory;
    }

    /** Gets the Looper */
    @Override
    public Looper getLooper() {
        return mLooper;
    }

    /** Gets the Context */
    @Override
    public Context getContext() {
        return mContext;
    }

    /** Gets the RandomnessFactory which will control if the IKE Session is in test mode */
    @Override
    public RandomnessFactory getRandomnessFactory() {
        return mRandomFactory;
    }
}
