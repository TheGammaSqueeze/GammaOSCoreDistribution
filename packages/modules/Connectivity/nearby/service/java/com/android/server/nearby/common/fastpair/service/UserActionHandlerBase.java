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

package com.android.server.nearby.common.fastpair.service;

/** Handles intents to {@link com.android.server.nearby.fastpair.FastPairManager}. */
public class UserActionHandlerBase {
    public static final String PREFIX = "com.android.server.nearby.fastpair.";
    public static final String ACTION_PREFIX = "com.android.server.nearby:";

    public static final String EXTRA_ITEM_ID = PREFIX + "EXTRA_ITEM_ID";
    public static final String EXTRA_COMPANION_APP = ACTION_PREFIX + "EXTRA_COMPANION_APP";
    public static final String EXTRA_MAC_ADDRESS = PREFIX + "EXTRA_MAC_ADDRESS";

}

