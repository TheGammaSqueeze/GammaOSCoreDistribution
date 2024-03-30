/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.layoutlib.java.text;

import java.text.DateFormat;

/**
 * Provides alternate implementation to java.text.DateFormat.set24HourTimePref, which is present
 * as a
 * non-public method in the Android VM, but not present on the host VM. This is injected in the
 * layoutlib using {@link ReplaceMethodCallsAdapter}.
 */
public class DateFormat_Delegate {

    public static final void set24HourTimePref(Boolean is24Hour) {
        // ignore
    }
}
