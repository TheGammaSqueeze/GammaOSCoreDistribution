/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.app.search;

import android.app.search.Query;

/**
 * Utility class used to define implicit contract between aiai and launcher regarding
 * what constant string key should be used to pass sub session information inside
 * the {@link Query} object.
 *
 * This decorated query object is passed to aiai using two method calls:
 * <ul>
 *     <ol>android.app.search.SearchSession.query()</ol>
 *     <ol>android.app.search.SearchSession.notifyEvent()</ol>
 * </ul>
 */
public class QueryExtras {

    // Can be either 1 (ALLAPPS) or 2 (QSB)
    public static final String EXTRAS_KEY_ENTRY = "entry";

    // This value overrides the timeout that is defined inside {@link SearchContext#getTimeout}
    public static final String EXTRAS_KEY_TIMEOUT_OVERRIDE = "timeout";

    // Used to know which target is deleted.
    public static final String EXTRAS_BUNDLE_DELETED_TARGET_ID = "deleted_target_id";
}
