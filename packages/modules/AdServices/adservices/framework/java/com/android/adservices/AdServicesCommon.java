/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.adservices;

/** Common constants for AdServices
 *
 * @hide
 */
public class AdServicesCommon {
    private AdServicesCommon() {}

    /** The service APK name. */
    public static final String ADSERVICES_PACKAGE = "com.android.adservices.apk";

    /** Intent action to discover the Topics servce in the APK. */
    public static final String ACTION_TOPICS_SERVICE = "android.adservices.TOPICS_SERVICE";
}
