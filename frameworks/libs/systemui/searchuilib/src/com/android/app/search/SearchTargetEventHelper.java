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

import static com.android.app.search.SearchTargetExtras.isRichAnswer;

import android.app.search.SearchTarget;
import android.content.ComponentName;
import android.os.Process;

import androidx.annotation.Nullable;

/**
 * Helper class that defines helper methods for {@link android.app.search.SearchTargetEvent} to
 * define the contract between Launcher and AiAi for notifyEvent.
 */

public class SearchTargetEventHelper {

    public static final String PKG_NAME_AGSA = "com.google.android.googlequicksearchbox";

    /**
     * Generate web target id similar to AiAi targetId for logging search button tap and Launcher
     * sends raw query to AGA.
     * AiAi target id is of format "resultType:userId:packageName:extraInfo"
     *
     * @return string webTargetId
     * Example webTargetId for
     * web suggestion - WEB_SUGGEST:0:com.google.android.googlequicksearchbox:SUGGESTION
     */
    public static String generateWebTargetIdForRawQuery() {
        // For raw query, there is no search target, so we pass null.
        return generateWebTargetIdForLogging(null);
    }

    /**
     * Generate web target id similar to AiAi targetId for logging both 0-state and n-state.
     * AiAi target id is of format "resultType:userId:packageName:extraInfo"
     *
     * @return string webTargetId
     * Example webTargetId for
     * web suggestion - WEB_SUGGEST:0:com.google.android.googlequicksearchbox:SUGGESTION
     * rich answer - WEB_SUGGEST:0:com.google.android.googlequicksearchbox:RICH_ANSWER
     */
    public static String generateWebTargetIdForLogging(@Nullable SearchTarget webTarget) {
        StringBuilder webTargetId = new StringBuilder(
                "WEB_SUGGEST" + ":" + Process.myUserHandle().getIdentifier() + ":");
        if (webTarget == null) {
            webTargetId.append(PKG_NAME_AGSA + ":SUGGESTION");
            return webTargetId.toString();
        }
        webTargetId.append(webTarget.getPackageName());
        if (isRichAnswer(webTarget)) {
            webTargetId.append(":RICH_ANSWER");
        } else {
            webTargetId.append(":SUGGESTION");
        }
        return webTargetId.toString();
    }

    /**
     * Generate application target id similar to AiAi targetId for logging only 0-state.
     * For n-state, AiAi already populates the target id in right format.
     * AiAi target id is of format "resultType:userId:packageName:extraInfo"
     *
     * When the apps from AiAi's AppPredictionService are converted to {@link SearchTarget}, we need
     * to construct the targetId using componentName.
     *
     * @return string appTargetId
     * Example appTargetId for
     * maps - APPLICATION:0:com.google.android.apps.maps:com.google.android.maps.MapsActivity
     * clock - APPLICATION:0:com.google.android.deskclock:com.android.deskclock.DeskClock
     */
    public static String generateAppTargetIdForLogging(@Nullable ComponentName appComponentName) {
        StringBuilder appTargetId = new StringBuilder(
                "APPLICATION" + ":" + Process.myUserHandle().getIdentifier() + ":");
        if (appComponentName == null) return appTargetId.append(" : ").toString();
        return appTargetId + appComponentName.getPackageName() + ":"
                + appComponentName.getClassName();
    }

    /**
     * Generate gms play target id similar to AiAi targetId for logging only n-state.
     * AiAi target id is of format "resultType:userId:packageName:extraInfo"
     *
     * @return string playTargetId
     * Example playTargetId for Candy Crush
     * PLAY:0:com.king.candycrushsaga:Gms
     */
    public static String generatePlayTargetIdForLogging(String appPackage) {
        return "PLAY" + ":" + Process.myUserHandle().getIdentifier() + ":" + appPackage + ":Gms";
    }
}
