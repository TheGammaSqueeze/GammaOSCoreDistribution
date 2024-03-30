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

package com.android.cts.localeconfig;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import android.app.LocaleConfig;
import android.content.Context;
import android.os.LocaleList;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Tests for {@link android.app.LocaleConfig} API(s).
 *
 * Build/Install/Run: atest LocaleConfigTest
 */
@RunWith(AndroidJUnit4.class)
public class LocaleConfigTest {
    private static final String NOTAG_PACKAGE_NAME = "com.android.cts.nolocaleconfigtag";
    private static final String MALFORMED_INPUT_PACKAGE_NAME = "com.android.cts.malformedinput";
    private static final List<String> EXPECT_LOCALES = Arrays.asList(
            new String[]{"en-US", "zh-TW", "pt", "fr", "zh-Hans-SG"});

    @Test
    public void testGetLocaleList() throws Exception {
        Context context = InstrumentationRegistry.getTargetContext();
        LocaleConfig localeConfig = new LocaleConfig(context);

        assertEquals(EXPECT_LOCALES.stream()
                .sorted()
                .collect(Collectors.toList()),
                new ArrayList<String>(Arrays.asList(
                        localeConfig.getSupportedLocales().toLanguageTags().split(","))).stream()
                .sorted()
                .collect(Collectors.toList()));

        assertEquals(LocaleConfig.STATUS_SUCCESS, localeConfig.getStatus());
    }

    @Test
    public void testNoLocaleConfigTag() throws Exception {
        Context context = InstrumentationRegistry.getTargetContext();
        Context appContext = context.createPackageContext(NOTAG_PACKAGE_NAME, 0);
        LocaleConfig localeConfig = new LocaleConfig(appContext);
        LocaleList localeList = localeConfig.getSupportedLocales();

        assertNull(localeList);

        assertEquals(LocaleConfig.STATUS_NOT_SPECIFIED, localeConfig.getStatus());
    }

    @Test
    public void testLocaleConfigMalformedInput() throws Exception {
        Context context = InstrumentationRegistry.getTargetContext();
        Context appContext = context.createPackageContext(MALFORMED_INPUT_PACKAGE_NAME, 0);
        LocaleConfig localeConfig = new LocaleConfig(appContext);
        LocaleList localeList = localeConfig.getSupportedLocales();

        assertNull(localeList);

        assertEquals(LocaleConfig.STATUS_PARSING_FAILED, localeConfig.getStatus());
    }
}

