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

package com.android.car.carlauncher;

import static android.car.settings.CarSettings.Secure.KEY_PACKAGES_DISABLED_ON_RESOURCE_OVERUSE;
import static android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
import static android.content.pm.PackageManager.MATCH_DISABLED_UNTIL_USED_COMPONENTS;

import static com.android.car.carlauncher.AppLauncherUtils.APP_TYPE_LAUNCHABLES;
import static com.android.car.carlauncher.AppLauncherUtils.APP_TYPE_MEDIA_SERVICES;
import static com.android.car.carlauncher.AppLauncherUtils.PACKAGES_DISABLED_ON_RESOURCE_OVERUSE_SEPARATOR;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.car.Car;
import android.car.content.pm.CarPackageManager;
import android.car.media.CarMediaManager;
import android.car.test.mocks.AbstractExtendedMockitoTestCase;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.os.UserHandle;
import android.provider.Settings;
import android.service.media.MediaBrowserService;
import android.util.ArraySet;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

@RunWith(AndroidJUnit4.class)
@SmallTest
public final class AppLauncherUtilsTest extends AbstractExtendedMockitoTestCase {
    private static final String TEST_DISABLED_APP_1 = "com.android.car.test.disabled1";
    private static final String TEST_DISABLED_APP_2 = "com.android.car.test.disabled2";
    private static final String TEST_ENABLED_APP = "com.android.car.test.enabled";
    private static final String TEST_VIDEO_APP = "com.android.car.test.video";

    private static final Predicate<ResolveInfo> MATCH_NO_APP = (resolveInfo) -> false;

    @Mock private Context mMockContext;
    @Mock private LauncherApps mMockLauncherApps;
    @Mock private PackageManager mMockPackageManager;

    private CarMediaManager mCarMediaManager;
    private CarPackageManager mCarPackageManager;
    private XmlPullParserFactory mParserFactory;

    @Before
    public void setUp() throws Exception {
        Car car = Car.createCar(mMockContext);
        mCarPackageManager = (CarPackageManager) car.getCarManager(Car.PACKAGE_SERVICE);
        mCarMediaManager = (CarMediaManager) car.getCarManager(Car.CAR_MEDIA_SERVICE);
        when(mMockContext.getPackageManager()).thenReturn(mMockPackageManager);

        mParserFactory = XmlPullParserFactory.newInstance();
        mParserFactory.setNamespaceAware(true);
    }

    @Test
    public void testGetLauncherAppsWithEnableAndLaunchDisabledApps() {
        mockSettingsStringCalls();
        mockPackageManagerQueries();

        AppLauncherUtils.LauncherAppsInfo launcherAppsInfo = AppLauncherUtils.getLauncherApps(
                mMockContext, /* appsToHide= */ new ArraySet<>(),
                /* customMediaComponents= */ new ArraySet<>(),
                /* appTypes= */ APP_TYPE_LAUNCHABLES + APP_TYPE_MEDIA_SERVICES,
                /* openMediaCenter= */ false, mMockLauncherApps, mCarPackageManager,
                mMockPackageManager, MATCH_NO_APP, mCarMediaManager);

        List<AppMetaData> appMetaData = launcherAppsInfo.getLaunchableComponentsList();

        // mMockLauncherApps is never stubbed, only services & disabled activities are expected.
        assertEquals(3, appMetaData.size());

        mockPmGetApplicationEnabledSetting(COMPONENT_ENABLED_STATE_ENABLED, TEST_DISABLED_APP_1,
                TEST_DISABLED_APP_2);

        launchAllApps(appMetaData);

        verify(mMockPackageManager).setApplicationEnabledSetting(
                eq(TEST_DISABLED_APP_1), eq(COMPONENT_ENABLED_STATE_ENABLED), eq(0));

        verify(mMockPackageManager).setApplicationEnabledSetting(
                eq(TEST_DISABLED_APP_2), eq(COMPONENT_ENABLED_STATE_ENABLED), eq(0));

        verify(mMockContext, times(2)).startActivity(any(), any());
    }

    @Test
    public void testGetLauncherAppsWithNotEnablingEnabledApps() {
        mockSettingsStringCalls();
        mockPackageManagerQueries();

        AppLauncherUtils.LauncherAppsInfo launcherAppsInfo = AppLauncherUtils.getLauncherApps(
                mMockContext, /* appsToHide= */ new ArraySet<>(),
                /* customMediaComponents= */ new ArraySet<>(),
                /* appTypes= */ APP_TYPE_LAUNCHABLES + APP_TYPE_MEDIA_SERVICES,
                /* openMediaCenter= */ false, mMockLauncherApps, mCarPackageManager,
                mMockPackageManager, MATCH_NO_APP, mCarMediaManager);

        List<AppMetaData> appMetaData = launcherAppsInfo.getLaunchableComponentsList();

        // mMockLauncherApps is never stubbed, only services & disabled activities are expected.
        assertEquals(3, appMetaData.size());

        mockPmGetApplicationEnabledSetting(COMPONENT_ENABLED_STATE_ENABLED, TEST_DISABLED_APP_1,
                TEST_DISABLED_APP_2);

        launchAllApps(appMetaData);

        verify(mMockPackageManager, never()).setApplicationEnabledSetting(
                eq(TEST_ENABLED_APP), anyInt(), eq(0));
    }

    @Test
    public void testGetLauncherAppsWithEnabledAndVideoApps() {
        mockSettingsStringCalls();
        mockPackageManagerQueriesForVideo();

        AppLauncherUtils.LauncherAppsInfo launcherAppsInfo = AppLauncherUtils.getLauncherApps(
                mMockContext, /* appsToHide= */ new ArraySet<>(),
                /* customMediaComponents= */ new ArraySet<>(),
                /* appTypes= */ APP_TYPE_LAUNCHABLES + APP_TYPE_MEDIA_SERVICES,
                /* openMediaCenter= */ false, mMockLauncherApps, mCarPackageManager,
                mMockPackageManager, new TestVideoAppPredicate(), mCarMediaManager);

        // mMockLauncherApps is never stubbed, only services & disabled activities are expected.
        List<AppMetaData> appMetaData = launcherAppsInfo.getLaunchableComponentsList();

        // TEST_VIDEO_APP should be filtered by TestVideoAppPredicate above.
        assertEquals(1, appMetaData.size());
        assertEquals(TEST_ENABLED_APP, appMetaData.get(0).getPackageName());
    }

    @Test
    public void getAutomotiveAppTypes() {
        // This test relies on test app's manifest & xml resources.
        Context testContext =
                InstrumentationRegistry.getInstrumentation().getContext();
        assertEquals(
                Arrays.asList("video", "media"),
                AppLauncherUtils.getAutomotiveAppTypes(
                        testContext.getPackageManager(),
                        testContext.getPackageName()));
    }

    @Test
    public void videoAppPredicate() {
        // This test relies on test app's manifest & xml resources.
        Context testContext =
                InstrumentationRegistry.getInstrumentation().getContext();
        Predicate<ResolveInfo> predicate =
                new AppLauncherUtils.VideoAppPredicate(testContext.getPackageManager());

        assertTrue(predicate.test(constructServiceResolveInfo(testContext.getPackageName())));
    }

    @Test
    public void invalidAutomotiveXml() {
        StringBuilder hugeInvalidXml = new StringBuilder("<automotiveApp>");
        for (int i = 0; i < 65; i++) {
            hugeInvalidXml.append("<uses name=\"video\"/>");
        }
        hugeInvalidXml.append("</automotiveApp>");

        String[] invalidXmls = {
                "NoTagsHere",
                // Unknown tag.
                "<foo/>",
                // Manifest tag not expected.
                "<automotiveApp><manifest/></automotiveApp>",
                // Uses tag has missing name attribute.
                "<automotiveApp><uses/></automotiveApp>",
                // Uses tag has empty name attribute.
                "<automotiveApp><uses name=\"\"/></automotiveApp>",
                // Uses tag nested inside uses tag.
                "<automotiveApp><uses name=\"video\"><uses name=\"media\"/></uses></automotiveApp>",
                // Too many uses tags
                hugeInvalidXml.toString()
        };

        for (String invalidXml : invalidXmls) {
            List<String> appTypes =
                    AppLauncherUtils.parseAutomotiveAppTypes(createPullParser(invalidXml));
            assertEquals(0, appTypes.size());
        }
    }

    private XmlPullParser createPullParser(String xmlText) {
        try {
            XmlPullParser parser = mParserFactory.newPullParser();
            parser.setInput(new StringReader(xmlText));
            return parser;
        } catch (XmlPullParserException e) {
            fail("Unexpected failure");
            return null;
        }
    }

    private void mockPackageManagerQueriesForVideo() {
        when(mMockPackageManager.queryIntentServices(any(), anyInt())).thenAnswer(args -> {
            Intent intent = args.getArgument(0);
            if (intent.getAction().equals(MediaBrowserService.SERVICE_INTERFACE)) {
                return Arrays.asList(
                        constructServiceResolveInfo(TEST_ENABLED_APP),
                        constructServiceResolveInfo(TEST_VIDEO_APP));
            }
            return new ArrayList<>();
        });
    }

    private void mockPackageManagerQueries() {
        when(mMockPackageManager.queryIntentServices(any(), anyInt())).thenAnswer(args -> {
            Intent intent = args.getArgument(0);
            if (intent.getAction().equals(MediaBrowserService.SERVICE_INTERFACE)) {
                return Collections.singletonList(constructServiceResolveInfo(TEST_ENABLED_APP));
            }
            return new ArrayList<>();
        });
        when(mMockPackageManager.queryIntentActivities(any(), any())).thenAnswer(args -> {
            Intent intent = args.getArgument(0);
            PackageManager.ResolveInfoFlags flags = args.getArgument(1);
            List<ResolveInfo> resolveInfoList = new ArrayList<>();
            if (intent.getAction().equals(Intent.ACTION_MAIN)) {
                if ((flags.getValue() & MATCH_DISABLED_UNTIL_USED_COMPONENTS) != 0) {
                    resolveInfoList.add(constructActivityResolveInfo(TEST_DISABLED_APP_1));
                    resolveInfoList.add(constructActivityResolveInfo(TEST_DISABLED_APP_2));
                }
                resolveInfoList.add(constructActivityResolveInfo(TEST_ENABLED_APP));
            }
            return resolveInfoList;
        });
    }

    private void mockPmGetApplicationEnabledSetting(int enabledState, String... packages) {
        for (String pkg : packages) {
            when(mMockPackageManager.getApplicationEnabledSetting(pkg)).thenReturn(enabledState);
        }
    }

    private void mockSettingsStringCalls() {
        when(mMockContext.createContextAsUser(any(UserHandle.class), anyInt()))
                .thenAnswer(args -> {
                    Context context = mock(Context.class);
                    ContentResolver contentResolver = mock(ContentResolver.class);
                    when(context.getContentResolver()).thenReturn(contentResolver);
                    return context;
                });

        doReturn(TEST_DISABLED_APP_1 + PACKAGES_DISABLED_ON_RESOURCE_OVERUSE_SEPARATOR
                + TEST_DISABLED_APP_2)
                .when(() -> Settings.Secure.getString(any(ContentResolver.class),
                eq(KEY_PACKAGES_DISABLED_ON_RESOURCE_OVERUSE)));
    }

    private void launchAllApps(List<AppMetaData> appMetaData) {
        for (AppMetaData meta : appMetaData) {
            Consumer<Context> launchCallback = meta.getLaunchCallback();
            launchCallback.accept(mMockContext);
        }
    }

    private static ResolveInfo constructActivityResolveInfo(String packageName) {
        ResolveInfo info = new ResolveInfo();
        info.activityInfo = new ActivityInfo();
        info.activityInfo.packageName = packageName;
        info.activityInfo.name = packageName + ".activity";
        info.activityInfo.applicationInfo = new ApplicationInfo();
        return info;
    }

    private static ResolveInfo constructServiceResolveInfo(String packageName) {
        ResolveInfo info = new ResolveInfo();
        info.serviceInfo = new ServiceInfo();
        info.serviceInfo.packageName = packageName;
        info.serviceInfo.name = packageName + ".service";
        info.serviceInfo.applicationInfo = new ApplicationInfo();
        return info;
    }

    /** Test sub-class of VideoAppPredicate that only matches TEST_VIDEO_APP package-name */
    static class TestVideoAppPredicate extends AppLauncherUtils.VideoAppPredicate {
        TestVideoAppPredicate() {
            super(/* packageManager= */ null);
        }

        @Override
        public boolean test(ResolveInfo resolveInfo) {
            return TEST_VIDEO_APP.equals(super.getPackageName(resolveInfo));
        }
    }
}
