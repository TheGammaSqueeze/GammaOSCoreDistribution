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

package android.localemanager.cts.app;

import static android.localemanager.cts.util.LocaleConstants.EXTRA_QUERY_LOCALES;
import static android.localemanager.cts.util.LocaleConstants.EXTRA_SET_LOCALES;
import static android.localemanager.cts.util.LocaleConstants.TEST_APP_CONFIG_CHANGED_INFO_PROVIDER_ACTION;
import static android.localemanager.cts.util.LocaleConstants.TEST_APP_CREATION_INFO_PROVIDER_ACTION;
import static android.localemanager.cts.util.LocaleConstants.TEST_APP_PACKAGE;
import static android.localemanager.cts.util.LocaleUtils.constructResultIntent;

import android.app.Activity;
import android.app.LocaleManager;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.LocaleList;

import androidx.annotation.Nullable;

/**
 * This app is used as an external package to test system api
 * {@link LocaleManager#setApplicationLocales(String, LocaleList)}
 *
 * <p> This activity is invoked by the {@link LocaleManagerTests} for below reasons:
 * <ul>
 * <li> To keep the app in the foreground/background.
 * <li> To query locales in the running activity on app restart.
 * </ul>
 */
public class MainActivity extends Activity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        if (intent != null && intent.hasExtra(EXTRA_QUERY_LOCALES)) {
            // This intent extra is sent by app for restarting the app.
            // Upon app restart, we want to check that the correct locales are received.
            // So fetch the locales and send them to the calling test for verification.
            LocaleManager localeManager = getSystemService(LocaleManager.class);
            LocaleList locales = localeManager.getApplicationLocales();

            // Send back the response with package name of this app and the locales fetched
            // in current activity to the calling test.
            sendBroadcast(constructResultIntent(TEST_APP_CREATION_INFO_PROVIDER_ACTION,
                    TEST_APP_PACKAGE, locales));
            finish();
        } else if (intent != null && intent.hasExtra(EXTRA_SET_LOCALES)) {
            // The invoking test directed us to set our application locales to the specified value
            LocaleManager localeManager = getSystemService(LocaleManager.class);
            localeManager.setApplicationLocales(LocaleList.forLanguageTags(
                    intent.getStringExtra(EXTRA_SET_LOCALES)));
            finish();
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        LocaleList locales = newConfig.getLocales();

        // Send back the received locales to the test for correctness assertion
        sendBroadcast(constructResultIntent(TEST_APP_CONFIG_CHANGED_INFO_PROVIDER_ACTION,
                TEST_APP_PACKAGE, locales));
        finish();
    }
}
