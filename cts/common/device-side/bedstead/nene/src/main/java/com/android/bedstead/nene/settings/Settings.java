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

package com.android.bedstead.nene.settings;

/** APIs related to {@link android.provider.Settings}. */
public final class Settings {

    public static final Settings sInstance = new Settings();

    private Settings() {

    }

    /** APIs related to {@link android.provider.Settings.Secure}. */
    public SecureSettings secure() {
        return SecureSettings.sInstance;
    }

    /** APIs related to {@link android.provider.Settings.Global}. */
    public GlobalSettings global() {
        return GlobalSettings.sInstance;
    }

    /** APIs related to {@link android.provider.Settings.System}. */
    public SystemSettings system() {
        return SystemSettings.sInstance;
    }

}
