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

import static com.android.app.search.LayoutType.SMALL_ICON_HORIZONTAL_TEXT;
import static com.android.app.search.SearchActionExtras.BUNDLE_EXTRA_HIDE_ICON;
import static com.android.app.search.SearchActionExtras.BUNDLE_EXTRA_HIDE_SUBTITLE;
import static com.android.app.search.SearchTargetExtras.BUNDLE_EXTRA_CLASS;
import static com.android.app.search.SearchTargetExtras.BUNDLE_EXTRA_SUBTITLE_OVERRIDE;
import static com.android.app.search.SearchTargetExtras.BUNDLE_EXTRA_SUPPORT_QUERY_BUILDER;
import static com.android.app.search.SearchTargetExtras.EXTRAS_RECENT_BLOCK_TARGET;

import android.app.search.SearchAction;
import android.app.search.SearchTarget;
import android.content.pm.ShortcutInfo;
import android.os.Bundle;

public class SearchTargetConverter {
    /**
     * Generate a searchTarget that uses {@link LayoutType#SMALL_ICON_HORIZONTAL_TEXT} from a
     * searchTarget where original layout type may not have been SMALL_ICON_HORIZONTAL_TEXT. Only
     * possible if the given SearchTarget contains a searchAction or shortcutInfo, otherwise the
     * original searchTarget will be returned.
     */
    public static SearchTarget convertLayoutTypeToSmallIconHorizontalText(
            SearchTarget searchTarget) {
        SearchAction searchTargetAction = searchTarget.getSearchAction();
        ShortcutInfo shortcutInfo = searchTarget.getShortcutInfo();
        int resultType = searchTarget.getResultType();
        String subtitle = "";

        Bundle searchTargetBundle = searchTarget.getExtras();
        searchTargetBundle.putString(BUNDLE_EXTRA_CLASS,
                searchTargetBundle.getString(BUNDLE_EXTRA_CLASS));
        searchTargetBundle.putBoolean(BUNDLE_EXTRA_SUPPORT_QUERY_BUILDER, true);
        searchTargetBundle.putBoolean(BUNDLE_EXTRA_HIDE_SUBTITLE, false);
        searchTargetBundle.putString(BUNDLE_EXTRA_SUBTITLE_OVERRIDE, subtitle);
        searchTargetBundle.putBoolean(BUNDLE_EXTRA_HIDE_ICON, false);
        searchTargetBundle.putBoolean(EXTRAS_RECENT_BLOCK_TARGET, true);

        SearchTarget.Builder builder = new SearchTarget.Builder(resultType,
                SMALL_ICON_HORIZONTAL_TEXT, searchTarget.getId())
                .setPackageName(searchTarget.getPackageName())
                .setExtras(searchTargetBundle)
                .setUserHandle(searchTarget.getUserHandle());
        if (searchTargetAction != null) {
            builder.setSearchAction(searchTargetAction);
        } else if (shortcutInfo != null) {
            builder.setShortcutInfo(shortcutInfo);
        } else {
            return searchTarget;
        }
        return builder.build();
    }
}
