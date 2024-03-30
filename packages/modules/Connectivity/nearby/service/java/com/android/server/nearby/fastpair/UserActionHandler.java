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

package com.android.server.nearby.fastpair;

import com.android.server.nearby.common.fastpair.service.UserActionHandlerBase;

/**
 * User action handler class.
 */
public class UserActionHandler extends UserActionHandlerBase {

    public static final String EXTRA_DISCOVERY_ITEM = PREFIX + "EXTRA_DISCOVERY_ITEM";
    public static final String EXTRA_FAST_PAIR_SECRET = PREFIX + "EXTRA_FAST_PAIR_SECRET";
    public static final String ACTION_FAST_PAIR = ACTION_PREFIX + "ACTION_FAST_PAIR";
    public static final String EXTRA_PRIVATE_BLE_ADDRESS =
            ACTION_PREFIX + "EXTRA_PRIVATE_BLE_ADDRESS";
}
