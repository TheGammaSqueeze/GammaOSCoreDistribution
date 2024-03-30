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

import static com.android.app.search.LayoutType.EMPTY_DIVIDER;
import static com.android.app.search.LayoutType.SECTION_HEADER;
import static com.android.app.search.ResultType.NO_FULFILLMENT;

import android.app.search.SearchTarget;
import android.os.Bundle;
import android.os.Process;
import android.os.UserHandle;

public class SearchTargetGenerator {
    private static final UserHandle USERHANDLE = Process.myUserHandle();

    public static SearchTarget EMPTY_DIVIDER_TARGET =
            new SearchTarget.Builder(NO_FULFILLMENT, EMPTY_DIVIDER, "divider")
                    .setPackageName("") /* required but not used*/
                    .setUserHandle(USERHANDLE) /* required */
                    .setExtras(new Bundle())
                    .build();

    public static SearchTarget SECTION_HEADER_TARGET =
            new SearchTarget.Builder(NO_FULFILLMENT, SECTION_HEADER, "section_header")
                    .setPackageName("") /* required but not used*/
                    .setUserHandle(USERHANDLE) /* required */
                    .setExtras(new Bundle())
                    .build();
}
