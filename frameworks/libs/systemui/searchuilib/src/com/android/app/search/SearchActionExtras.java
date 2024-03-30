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

import android.app.search.SearchAction;

/**
 * Helper class that defines key string value for {@link SearchAction#getExtras()}
 */
public class SearchActionExtras {
    public static final String BUNDLE_EXTRA_HIDE_SUBTITLE = "hide_subtitle";
    public static final String BUNDLE_EXTRA_HIDE_ICON = "hide_icon";
    public static final String BUNDLE_EXTRA_ALLOW_PINNING = "allow_pinning";
    public static final String BUNDLE_EXTRA_BADGE_WITH_PACKAGE = "badge_with_package";
    public static final String BUNDLE_EXTRA_PRIMARY_ICON_FROM_TITLE = "primary_icon_from_title";
    public static final String BUNDLE_EXTRA_IS_SEARCH_IN_APP = "is_search_in_app";
    public static final String BUNDLE_EXTRA_BADGE_WITH_COMPONENT_NAME = "badge_with_component_name";
    public static final String BUNDLE_EXTRA_ICON_CACHE_KEY = "icon_cache_key";
    public static final String BUNDLE_EXTRA_ICON_TOKEN_INTEGER = "icon_integer";
    public static final String BUNDLE_EXTRA_SHOULD_START = "should_start";
    public static final String BUNDLE_EXTRA_SHOULD_START_FOR_RESULT = "should_start_for_result";
    public static final String BUNDLE_EXTRA_SUGGESTION_ACTION_TEXT = "suggestion_action_text";
    public static final String BUNDLE_EXTRA_SUGGESTION_ACTION_RPC = "suggestion_action_rpc";
    public static final String BUNDLE_EXTRA_SKIP_LOGGING_IN_TARGET_HANDLER =
            "skip_logging_in_target_handler";
}
