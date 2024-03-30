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

package android.appenumeration.cts;

import static android.Manifest.permission.SET_PREFERRED_APPLICATIONS;
import static android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN;
import static android.appenumeration.cts.Constants.ACTION_AWAIT_LAUNCHER_APPS_CALLBACK;
import static android.appenumeration.cts.Constants.ACTION_AWAIT_LAUNCHER_APPS_SESSION_CALLBACK;
import static android.appenumeration.cts.Constants.ACTION_BIND_SERVICE;
import static android.appenumeration.cts.Constants.ACTION_CAN_PACKAGE_QUERY;
import static android.appenumeration.cts.Constants.ACTION_CHECK_PACKAGE;
import static android.appenumeration.cts.Constants.ACTION_CHECK_SIGNATURES;
import static android.appenumeration.cts.Constants.ACTION_CHECK_URI_PERMISSION;
import static android.appenumeration.cts.Constants.ACTION_GET_ALL_PACKAGE_INSTALLER_SESSIONS;
import static android.appenumeration.cts.Constants.ACTION_GET_ALL_SESSIONS;
import static android.appenumeration.cts.Constants.ACTION_GET_INSTALLED_ACCESSIBILITYSERVICES_PACKAGES;
import static android.appenumeration.cts.Constants.ACTION_GET_INSTALLED_APPWIDGET_PROVIDERS;
import static android.appenumeration.cts.Constants.ACTION_GET_INSTALLED_PACKAGES;
import static android.appenumeration.cts.Constants.ACTION_GET_NAMES_FOR_UIDS;
import static android.appenumeration.cts.Constants.ACTION_GET_NAME_FOR_UID;
import static android.appenumeration.cts.Constants.ACTION_GET_PACKAGES_FOR_UID;
import static android.appenumeration.cts.Constants.ACTION_GET_PACKAGE_INFO;
import static android.appenumeration.cts.Constants.ACTION_GET_PREFERRED_ACTIVITIES;
import static android.appenumeration.cts.Constants.ACTION_GET_SESSION_INFO;
import static android.appenumeration.cts.Constants.ACTION_GET_SHAREDLIBRARY_DEPENDENT_PACKAGES;
import static android.appenumeration.cts.Constants.ACTION_GET_STAGED_SESSIONS;
import static android.appenumeration.cts.Constants.ACTION_GET_SYNCADAPTER_PACKAGES_FOR_AUTHORITY;
import static android.appenumeration.cts.Constants.ACTION_GET_SYNCADAPTER_TYPES;
import static android.appenumeration.cts.Constants.ACTION_GRANT_URI_PERMISSION;
import static android.appenumeration.cts.Constants.ACTION_HAS_SIGNING_CERTIFICATE;
import static android.appenumeration.cts.Constants.ACTION_JUST_FINISH;
import static android.appenumeration.cts.Constants.ACTION_LAUNCHER_APPS_GET_SUSPENDED_PACKAGE_LAUNCHER_EXTRAS;
import static android.appenumeration.cts.Constants.ACTION_LAUNCHER_APPS_IS_ACTIVITY_ENABLED;
import static android.appenumeration.cts.Constants.ACTION_LAUNCHER_APPS_SHOULD_HIDE_FROM_SUGGESTIONS;
import static android.appenumeration.cts.Constants.ACTION_MANIFEST_ACTIVITY;
import static android.appenumeration.cts.Constants.ACTION_MANIFEST_PROVIDER;
import static android.appenumeration.cts.Constants.ACTION_MANIFEST_SERVICE;
import static android.appenumeration.cts.Constants.ACTION_PENDING_INTENT_GET_ACTIVITY;
import static android.appenumeration.cts.Constants.ACTION_PENDING_INTENT_GET_CREATOR_PACKAGE;
import static android.appenumeration.cts.Constants.ACTION_QUERY_ACTIVITIES;
import static android.appenumeration.cts.Constants.ACTION_QUERY_PROVIDERS;
import static android.appenumeration.cts.Constants.ACTION_QUERY_RESOLVER;
import static android.appenumeration.cts.Constants.ACTION_QUERY_SERVICES;
import static android.appenumeration.cts.Constants.ACTION_REQUEST_SYNC_AND_AWAIT_STATUS;
import static android.appenumeration.cts.Constants.ACTION_REVOKE_URI_PERMISSION;
import static android.appenumeration.cts.Constants.ACTION_SET_INSTALLER_PACKAGE_NAME;
import static android.appenumeration.cts.Constants.ACTION_START_DIRECTLY;
import static android.appenumeration.cts.Constants.ACTION_START_FOR_RESULT;
import static android.appenumeration.cts.Constants.ACTION_TAKE_PERSISTABLE_URI_PERMISSION;
import static android.appenumeration.cts.Constants.ACTIVITY_CLASS_DUMMY_ACTIVITY;
import static android.appenumeration.cts.Constants.ACTIVITY_CLASS_TEST;
import static android.appenumeration.cts.Constants.CALLBACK_EVENT_INVALID;
import static android.appenumeration.cts.Constants.CALLBACK_EVENT_PACKAGES_SUSPENDED;
import static android.appenumeration.cts.Constants.CALLBACK_EVENT_PACKAGES_UNSUSPENDED;
import static android.appenumeration.cts.Constants.CALLBACK_EVENT_PACKAGE_ADDED;
import static android.appenumeration.cts.Constants.CALLBACK_EVENT_PACKAGE_CHANGED;
import static android.appenumeration.cts.Constants.CALLBACK_EVENT_PACKAGE_REMOVED;
import static android.appenumeration.cts.Constants.EXTRA_ACCOUNT;
import static android.appenumeration.cts.Constants.EXTRA_AUTHORITY;
import static android.appenumeration.cts.Constants.EXTRA_CERT;
import static android.appenumeration.cts.Constants.EXTRA_DATA;
import static android.appenumeration.cts.Constants.EXTRA_ERROR;
import static android.appenumeration.cts.Constants.EXTRA_FLAGS;
import static android.appenumeration.cts.Constants.EXTRA_ID;
import static android.appenumeration.cts.Constants.EXTRA_PENDING_INTENT;
import static android.appenumeration.cts.Constants.EXTRA_REMOTE_CALLBACK;
import static android.appenumeration.cts.Constants.EXTRA_REMOTE_READY_CALLBACK;
import static android.appenumeration.cts.Constants.QUERIES_ACTIVITY_ACTION;
import static android.appenumeration.cts.Constants.QUERIES_NOTHING;
import static android.appenumeration.cts.Constants.QUERIES_NOTHING_PERM;
import static android.appenumeration.cts.Constants.QUERIES_NOTHING_PROVIDER;
import static android.appenumeration.cts.Constants.QUERIES_NOTHING_Q;
import static android.appenumeration.cts.Constants.QUERIES_NOTHING_RECEIVES_NON_PERSISTABLE_URI;
import static android.appenumeration.cts.Constants.QUERIES_NOTHING_RECEIVES_NON_PERSISTABLE_URI_APK;
import static android.appenumeration.cts.Constants.QUERIES_NOTHING_RECEIVES_PERM_URI;
import static android.appenumeration.cts.Constants.QUERIES_NOTHING_RECEIVES_PERSISTABLE_URI;
import static android.appenumeration.cts.Constants.QUERIES_NOTHING_RECEIVES_PERSISTABLE_URI_APK;
import static android.appenumeration.cts.Constants.QUERIES_NOTHING_RECEIVES_URI;
import static android.appenumeration.cts.Constants.QUERIES_NOTHING_SEES_INSTALLER;
import static android.appenumeration.cts.Constants.QUERIES_NOTHING_SEES_INSTALLER_APK;
import static android.appenumeration.cts.Constants.QUERIES_NOTHING_SHARED_USER;
import static android.appenumeration.cts.Constants.QUERIES_NOTHING_USES_LIBRARY;
import static android.appenumeration.cts.Constants.QUERIES_NOTHING_USES_OPTIONAL_LIBRARY;
import static android.appenumeration.cts.Constants.QUERIES_PACKAGE;
import static android.appenumeration.cts.Constants.QUERIES_PACKAGE_PROVIDER;
import static android.appenumeration.cts.Constants.QUERIES_PROVIDER_ACTION;
import static android.appenumeration.cts.Constants.QUERIES_PROVIDER_AUTH;
import static android.appenumeration.cts.Constants.QUERIES_SERVICE_ACTION;
import static android.appenumeration.cts.Constants.QUERIES_UNEXPORTED_ACTIVITY_ACTION;
import static android.appenumeration.cts.Constants.QUERIES_UNEXPORTED_PROVIDER_ACTION;
import static android.appenumeration.cts.Constants.QUERIES_UNEXPORTED_PROVIDER_AUTH;
import static android.appenumeration.cts.Constants.QUERIES_UNEXPORTED_SERVICE_ACTION;
import static android.appenumeration.cts.Constants.QUERIES_WILDCARD_ACTION;
import static android.appenumeration.cts.Constants.QUERIES_WILDCARD_BROWSABLE;
import static android.appenumeration.cts.Constants.QUERIES_WILDCARD_BROWSER;
import static android.appenumeration.cts.Constants.QUERIES_WILDCARD_CONTACTS;
import static android.appenumeration.cts.Constants.QUERIES_WILDCARD_EDITOR;
import static android.appenumeration.cts.Constants.QUERIES_WILDCARD_SHARE;
import static android.appenumeration.cts.Constants.QUERIES_WILDCARD_WEB;
import static android.appenumeration.cts.Constants.TARGET_APPWIDGETPROVIDER;
import static android.appenumeration.cts.Constants.TARGET_APPWIDGETPROVIDER_SHARED_USER;
import static android.appenumeration.cts.Constants.TARGET_BROWSER;
import static android.appenumeration.cts.Constants.TARGET_BROWSER_WILDCARD;
import static android.appenumeration.cts.Constants.TARGET_CONTACTS;
import static android.appenumeration.cts.Constants.TARGET_EDITOR;
import static android.appenumeration.cts.Constants.TARGET_FILTERS;
import static android.appenumeration.cts.Constants.TARGET_FILTERS_APK;
import static android.appenumeration.cts.Constants.TARGET_FORCEQUERYABLE;
import static android.appenumeration.cts.Constants.TARGET_FORCEQUERYABLE_NORMAL;
import static android.appenumeration.cts.Constants.TARGET_NO_API;
import static android.appenumeration.cts.Constants.TARGET_PREFERRED_ACTIVITY;
import static android.appenumeration.cts.Constants.TARGET_PREFIX_WILDCARD_WEB;
import static android.appenumeration.cts.Constants.TARGET_SHARE;
import static android.appenumeration.cts.Constants.TARGET_SHARED_LIBRARY_PACKAGE;
import static android.appenumeration.cts.Constants.TARGET_SHARED_USER;
import static android.appenumeration.cts.Constants.TARGET_STUB;
import static android.appenumeration.cts.Constants.TARGET_STUB_APK;
import static android.appenumeration.cts.Constants.TARGET_SYNCADAPTER;
import static android.appenumeration.cts.Constants.TARGET_SYNCADAPTER_SHARED_USER;
import static android.appenumeration.cts.Constants.TARGET_WEB;
import static android.content.Intent.EXTRA_PACKAGES;
import static android.content.Intent.EXTRA_UID;
import static android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES;
import static android.content.pm.PackageManager.MATCH_SYSTEM_ONLY;
import static android.content.pm.PackageManager.SIGNATURE_MATCH;
import static android.content.pm.PackageManager.SIGNATURE_UNKNOWN_PACKAGE;
import static android.os.Process.INVALID_UID;
import static android.os.Process.ROOT_UID;

import static com.android.compatibility.common.util.ShellUtils.runShellCommand;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.arrayContainingInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.emptyArray;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItemInArray;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.Manifest;
import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.ActivityOptions;
import android.app.PendingIntent;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SyncAdapterType;
import android.content.pm.LauncherApps;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageInstaller.SessionInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Bundle;
import android.os.ConditionVariable;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Parcelable;
import android.os.Process;
import android.os.RemoteCallback;

import androidx.annotation.Nullable;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.AmUtils;
import com.android.compatibility.common.util.SystemUtil;
import com.android.cts.install.lib.Install;
import com.android.cts.install.lib.InstallUtils;
import com.android.cts.install.lib.LocalIntentSender;
import com.android.cts.install.lib.TestApp;

import org.hamcrest.core.IsNull;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(AndroidJUnit4.class)
public class AppEnumerationTests {
    private static Handler sResponseHandler;
    private static HandlerThread sResponseThread;

    private static boolean sGlobalFeatureEnabled;

    private static PackageManager sPm;
    private static AccountManager sAccountManager;

    // The shared library for getting dependent packages
    private static final String TEST_SHARED_LIB_NAME = "android.test.runner";
    private static final String TEST_NONEXISTENT_PACKAGE_NAME_1 = "com.android.cts.nonexistent1";
    private static final String TEST_NONEXISTENT_PACKAGE_NAME_2 = "com.android.cts.nonexistent2";

    private static final Account ACCOUNT_SYNCADAPTER = new Account(
            TARGET_SYNCADAPTER, "android.appenumeration.account.type");
    private static final Account ACCOUNT_SYNCADAPTER_SHARED_USER = new Account(
            TARGET_SYNCADAPTER_SHARED_USER, "android.appenumeration.shareduid.account.type");

    @Rule
    public TestName name = new TestName();

    @BeforeClass
    public static void setup() {
        final String deviceConfigResponse =
                SystemUtil.runShellCommand(
                        "device_config get package_manager_service "
                                + "package_query_filtering_enabled")
                        .trim();
        if ("null".equalsIgnoreCase(deviceConfigResponse) || deviceConfigResponse.isEmpty()) {
            sGlobalFeatureEnabled = true;
        } else {
            sGlobalFeatureEnabled = Boolean.parseBoolean(deviceConfigResponse);
        }
        System.out.println("Feature enabled: " + sGlobalFeatureEnabled);
        if (!sGlobalFeatureEnabled) return;

        sResponseThread = new HandlerThread("response");
        sResponseThread.start();
        sResponseHandler = new Handler(sResponseThread.getLooper());

        sPm = InstrumentationRegistry.getInstrumentation().getContext().getPackageManager();
        sAccountManager = AccountManager.get(
                InstrumentationRegistry.getInstrumentation().getContext());

        assertThat(sAccountManager.addAccountExplicitly(ACCOUNT_SYNCADAPTER,
                null /* password */, null /* userdata */), is(true));
        assertThat(sAccountManager.addAccountExplicitly(ACCOUNT_SYNCADAPTER_SHARED_USER,
                null /* password */, null /* userdata */), is(true));
    }

    @AfterClass
    public static void tearDown() {
        if (!sGlobalFeatureEnabled) return;
        sResponseThread.quit();

        assertThat(sAccountManager.removeAccountExplicitly(ACCOUNT_SYNCADAPTER),
                is(true));
        assertThat(sAccountManager.removeAccountExplicitly(ACCOUNT_SYNCADAPTER_SHARED_USER),
                is(true));
    }

    @Test
    public void systemPackagesQueryable_notEnabled() throws Exception {
        final Resources resources = Resources.getSystem();
        assertFalse(
                "config_forceSystemPackagesQueryable must not be true.",
                resources.getBoolean(resources.getIdentifier(
                        "config_forceSystemPackagesQueryable", "bool", "android")));

        // now let's assert that that the actual set of system apps is limited
        assertThat("Not all system apps should be visible.",
                getInstalledPackages(QUERIES_NOTHING_PERM, MATCH_SYSTEM_ONLY).length,
                greaterThan(getInstalledPackages(QUERIES_NOTHING, MATCH_SYSTEM_ONLY).length));
    }

    @Test
    public void all_canSeeForceQueryable() throws Exception {
        assertVisible(QUERIES_NOTHING, TARGET_FORCEQUERYABLE);
        assertVisible(QUERIES_ACTIVITY_ACTION, TARGET_FORCEQUERYABLE);
        assertVisible(QUERIES_SERVICE_ACTION, TARGET_FORCEQUERYABLE);
        assertVisible(QUERIES_PROVIDER_AUTH, TARGET_FORCEQUERYABLE);
        assertVisible(QUERIES_PACKAGE, TARGET_FORCEQUERYABLE);
    }

    @Test
    public void all_cannotSeeForceQueryableInstalledNormally() throws Exception {
        assertNotVisible(QUERIES_NOTHING, TARGET_FORCEQUERYABLE_NORMAL);
        assertNotVisible(QUERIES_ACTIVITY_ACTION, TARGET_FORCEQUERYABLE_NORMAL);
        assertNotVisible(QUERIES_SERVICE_ACTION, TARGET_FORCEQUERYABLE_NORMAL);
        assertNotVisible(QUERIES_PROVIDER_AUTH, TARGET_FORCEQUERYABLE_NORMAL);
        assertNotVisible(QUERIES_PACKAGE, TARGET_FORCEQUERYABLE_NORMAL);
    }

    @Test
    public void startExplicitly_canStartNonVisible() throws Exception {
        assertNotVisible(QUERIES_NOTHING, TARGET_FILTERS);
        startExplicitIntentViaComponent(QUERIES_NOTHING, TARGET_FILTERS);
        startExplicitIntentViaPackageName(QUERIES_NOTHING, TARGET_FILTERS);
    }

    @Test
    public void startExplicitly_canStartVisible() throws Exception {
        assertVisible(QUERIES_ACTIVITY_ACTION, TARGET_FILTERS);
        startExplicitIntentViaComponent(QUERIES_ACTIVITY_ACTION, TARGET_FILTERS);
        startExplicitIntentViaPackageName(QUERIES_ACTIVITY_ACTION, TARGET_FILTERS);
    }

    @Test
    public void startImplicitly_canStartNonVisible() throws Exception {
        assertNotVisible(QUERIES_NOTHING, TARGET_FILTERS);
        startImplicitIntent(QUERIES_NOTHING);
    }

    @Test
    public void startActivityWithNoPermissionUri_canSeeProvider() throws Exception {
        assertNotVisible(QUERIES_NOTHING_RECEIVES_URI, QUERIES_NOTHING_PERM);

        // send with uri but no grant flags; shouldn't be visible
        startExplicitActivityWithIntent(QUERIES_NOTHING_PERM, QUERIES_NOTHING_RECEIVES_URI,
                new Intent(ACTION_JUST_FINISH)
                        .setData(Uri.parse("content://" + QUERIES_NOTHING_PERM + "/test")));
        assertNotVisible(QUERIES_NOTHING_RECEIVES_URI, QUERIES_NOTHING_PERM);

        // send again with uri bug grant flags now set; should be visible
        startExplicitActivityWithIntent(QUERIES_NOTHING_PERM, QUERIES_NOTHING_RECEIVES_URI,
                new Intent(ACTION_JUST_FINISH)
                        .setData(Uri.parse("content://" + QUERIES_NOTHING_PERM + "/test"))
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));
        assertVisible(QUERIES_NOTHING_RECEIVES_URI, QUERIES_NOTHING_PERM);
    }

    @Test
    public void startActivityWithUri_canSeePermissionProtectedProvider() throws Exception {
        assertNotVisible(QUERIES_NOTHING_RECEIVES_PERM_URI, QUERIES_NOTHING_PERM);

        // send with uri but no grant flags; shouldn't be visible
        startExplicitActivityWithIntent(QUERIES_NOTHING_PERM, QUERIES_NOTHING_RECEIVES_PERM_URI,
                new Intent(ACTION_JUST_FINISH)
                        .setData(Uri.parse("content://" + QUERIES_NOTHING_PERM + "2/test")));
        assertNotVisible(QUERIES_NOTHING_RECEIVES_PERM_URI, QUERIES_NOTHING_PERM);

        // send again with uri bug grant flags now set; should be visible
        startExplicitActivityWithIntent(QUERIES_NOTHING_PERM, QUERIES_NOTHING_RECEIVES_PERM_URI,
                new Intent(ACTION_JUST_FINISH)
                        .setData(Uri.parse("content://" + QUERIES_NOTHING_PERM + "2/test"))
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));
        assertVisible(QUERIES_NOTHING_RECEIVES_PERM_URI, QUERIES_NOTHING_PERM);
    }

    @Test
    public void startActivityWithUriGrant_cannotSeeProviderAfterUpdated() throws Exception {
        assertNotVisible(QUERIES_NOTHING_RECEIVES_NON_PERSISTABLE_URI, QUERIES_NOTHING_PERM);

        // send with uri grant flags; should be visible
        startExplicitActivityWithIntent(QUERIES_NOTHING_PERM,
                QUERIES_NOTHING_RECEIVES_NON_PERSISTABLE_URI,
                new Intent(ACTION_JUST_FINISH)
                        .setData(Uri.parse("content://" + QUERIES_NOTHING_PERM + "3/test"))
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));
        assertVisible(QUERIES_NOTHING_RECEIVES_NON_PERSISTABLE_URI, QUERIES_NOTHING_PERM);

        // update the package; shouldn't be visible
        runShellCommand("pm install " + QUERIES_NOTHING_RECEIVES_NON_PERSISTABLE_URI_APK);
        // Wait until the updating is done
        AmUtils.waitForBroadcastIdle();
        assertNotVisible(QUERIES_NOTHING_RECEIVES_NON_PERSISTABLE_URI, QUERIES_NOTHING_PERM);
    }

    @Test
    public void startActivityWithPersistableUriGrant_canSeeProviderAfterUpdated() throws Exception {
        assertNotVisible(QUERIES_NOTHING_RECEIVES_PERSISTABLE_URI, QUERIES_NOTHING_PERM);

        // send with persistable uri grant flags; should be visible
        startExplicitActivityWithIntent(QUERIES_NOTHING_PERM,
                QUERIES_NOTHING_RECEIVES_PERSISTABLE_URI,
                new Intent(ACTION_TAKE_PERSISTABLE_URI_PERMISSION)
                        .setData(Uri.parse("content://" + QUERIES_NOTHING_PERM + "3/test"))
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION));
        assertVisible(QUERIES_NOTHING_RECEIVES_PERSISTABLE_URI, QUERIES_NOTHING_PERM);

        // update the package; should be still visible
        runShellCommand("pm install " + QUERIES_NOTHING_RECEIVES_PERSISTABLE_URI_APK);
        // Wait until the updating is done
        AmUtils.waitForBroadcastIdle();
        assertVisible(QUERIES_NOTHING_RECEIVES_PERSISTABLE_URI, QUERIES_NOTHING_PERM);
    }

    private void startExplicitActivityWithIntent(
            String sourcePackageName, String targetPackageName, Intent intent) throws Exception {
        sendCommandBlocking(sourcePackageName, targetPackageName,
                intent.setClassName(targetPackageName, ACTIVITY_CLASS_TEST),
                ACTION_START_DIRECTLY);
    }

    @Test
    public void queriesNothing_cannotSeeNonForceQueryable() throws Exception {
        assertNotVisible(QUERIES_NOTHING, TARGET_NO_API);
        assertNotVisible(QUERIES_NOTHING, TARGET_FILTERS);
    }

    @Test
    public void queriesNothingTargetsQ_canSeeAll() throws Exception {
        assertVisible(QUERIES_NOTHING_Q, TARGET_FORCEQUERYABLE);
        assertVisible(QUERIES_NOTHING_Q, TARGET_NO_API);
        assertVisible(QUERIES_NOTHING_Q, TARGET_FILTERS);
    }

    @Test
    public void queriesNothingHasPermission_canSeeAll() throws Exception {
        assertVisible(QUERIES_NOTHING_PERM, TARGET_FORCEQUERYABLE);
        assertVisible(QUERIES_NOTHING_PERM, TARGET_NO_API);
        assertVisible(QUERIES_NOTHING_PERM, TARGET_FILTERS);
    }

    @Test
    public void queriesNothing_cannotSeeFilters() throws Exception {
        assertNotQueryable(QUERIES_NOTHING, TARGET_FILTERS,
                ACTION_MANIFEST_ACTIVITY, this::queryIntentActivities);
        assertNotQueryable(QUERIES_NOTHING, TARGET_FILTERS,
                ACTION_MANIFEST_SERVICE, this::queryIntentServices);
        assertNotQueryable(QUERIES_NOTHING, TARGET_FILTERS,
                ACTION_MANIFEST_PROVIDER, this::queryIntentProviders);
    }

    @Test
    public void queriesActivityAction_canSeeFilters() throws Exception {
        assertQueryable(QUERIES_ACTIVITY_ACTION, TARGET_FILTERS,
                ACTION_MANIFEST_ACTIVITY, this::queryIntentActivities);
        assertQueryable(QUERIES_SERVICE_ACTION, TARGET_FILTERS,
                ACTION_MANIFEST_SERVICE, this::queryIntentServices);
        assertQueryable(QUERIES_PROVIDER_AUTH, TARGET_FILTERS,
                ACTION_MANIFEST_PROVIDER, this::queryIntentProviders);
        assertQueryable(QUERIES_PROVIDER_ACTION, TARGET_FILTERS,
                ACTION_MANIFEST_PROVIDER, this::queryIntentProviders);
    }

    @Test
    public void queriesNothingHasPermission_canSeeFilters() throws Exception {
        assertQueryable(QUERIES_NOTHING_PERM, TARGET_FILTERS,
                ACTION_MANIFEST_ACTIVITY, this::queryIntentActivities);
        assertQueryable(QUERIES_NOTHING_PERM, TARGET_FILTERS,
                ACTION_MANIFEST_SERVICE, this::queryIntentServices);
        assertQueryable(QUERIES_NOTHING_PERM, TARGET_FILTERS,
                ACTION_MANIFEST_PROVIDER, this::queryIntentProviders);
    }

    @Test
    public void queriesSomething_cannotSeeNoApi() throws Exception {
        assertNotVisible(QUERIES_ACTIVITY_ACTION, TARGET_NO_API);
        assertNotVisible(QUERIES_SERVICE_ACTION, TARGET_NO_API);
        assertNotVisible(QUERIES_PROVIDER_AUTH, TARGET_NO_API);
        assertNotVisible(QUERIES_PROVIDER_ACTION, TARGET_NO_API);
    }

    @Test
    public void queriesActivityAction_canSeeTarget() throws Exception {
        assertVisible(QUERIES_ACTIVITY_ACTION, TARGET_FILTERS);
    }

    @Test
    public void queriesServiceAction_canSeeTarget() throws Exception {
        assertVisible(QUERIES_SERVICE_ACTION, TARGET_FILTERS);
    }

    @Test
    public void queriesWildcardAction_canSeeTargets() throws Exception {
        assertVisible(QUERIES_WILDCARD_ACTION, TARGET_FILTERS);
    }

    @Test
    public void queriesProviderAuthority_canSeeTarget() throws Exception {
        assertVisible(QUERIES_PROVIDER_AUTH, TARGET_FILTERS);
    }

    @Test
    public void queriesProviderAction_canSeeTarget() throws Exception {
        assertVisible(QUERIES_PROVIDER_ACTION, TARGET_FILTERS);
    }

    @Test
    public void queriesActivityAction_cannotSeeUnexportedTarget() throws Exception {
        assertNotVisible(QUERIES_UNEXPORTED_ACTIVITY_ACTION, TARGET_FILTERS);
    }

    @Test
    public void queriesServiceAction_cannotSeeUnexportedTarget() throws Exception {
        assertNotVisible(QUERIES_UNEXPORTED_SERVICE_ACTION, TARGET_FILTERS);
    }

    @Test
    public void queriesProviderAuthority_cannotSeeUnexportedTarget() throws Exception {
        assertNotVisible(QUERIES_UNEXPORTED_PROVIDER_AUTH, TARGET_FILTERS);
    }

    @Test
    public void queriesProviderAction_cannotSeeUnexportedTarget() throws Exception {
        assertNotVisible(QUERIES_UNEXPORTED_PROVIDER_ACTION, TARGET_FILTERS);
    }

    @Test
    public void queriesPackage_canSeeTarget() throws Exception {
        assertVisible(QUERIES_PACKAGE, TARGET_NO_API);
    }

    @Test
    public void queriesNothing_canSeeInstaller() throws Exception {
        runShellCommand("pm uninstall " + QUERIES_NOTHING_SEES_INSTALLER);
        runShellCommand("pm install"
                + " -i " + TARGET_NO_API
                + " --pkg " + QUERIES_NOTHING_SEES_INSTALLER
                + " " + QUERIES_NOTHING_SEES_INSTALLER_APK);
        try {
            assertVisible(QUERIES_NOTHING_SEES_INSTALLER, TARGET_NO_API);
        } finally {
            runShellCommand("pm uninstall " + QUERIES_NOTHING_SEES_INSTALLER);
        }
    }


    @Test
    public void whenStarted_canSeeCaller() throws Exception {
        // let's first make sure that the target cannot see the caller.
        assertNotVisible(QUERIES_NOTHING, QUERIES_NOTHING_PERM);
        // now let's start the target and make sure that it can see the caller as part of that call
        PackageInfo packageInfo = startForResult(QUERIES_NOTHING_PERM, QUERIES_NOTHING);
        assertThat(packageInfo, IsNull.notNullValue());
        assertThat(packageInfo.packageName, is(QUERIES_NOTHING_PERM));
        // and finally let's re-run the last check to make sure that the target can still see the
        // caller
        assertVisible(QUERIES_NOTHING, QUERIES_NOTHING_PERM);
    }

    @Test
    public void whenStartedViaIntentSender_canSeeCaller() throws Exception {
        // let's first make sure that the target cannot see the caller.
        assertNotVisible(QUERIES_NOTHING, QUERIES_NOTHING_Q);
        // now let's start the target via pending intent and make sure that it can see the caller
        // as part of that call
        PackageInfo packageInfo = startSenderForResult(QUERIES_NOTHING_Q, QUERIES_NOTHING);
        assertThat(packageInfo, IsNull.notNullValue());
        assertThat(packageInfo.packageName, is(QUERIES_NOTHING_Q));
        // and finally let's re-run the last check to make sure that the target can still see the
        // caller
        assertVisible(QUERIES_NOTHING, QUERIES_NOTHING_Q);
    }

    @Test
    public void queriesNothing_cannotSeeLibraryPackage() throws Exception {
        assertNotVisible(QUERIES_NOTHING, TARGET_SHARED_LIBRARY_PACKAGE);
    }

    @Test
    public void queriesNothingUsesLibrary_canSeeLibraryPackage() throws Exception {
        assertVisible(QUERIES_NOTHING_USES_LIBRARY, TARGET_SHARED_LIBRARY_PACKAGE);
    }

    @Test
    public void queriesNothing_cannotSeeOptionalLibraryPackage() throws Exception {
        assertNotVisible(QUERIES_NOTHING, TARGET_SHARED_LIBRARY_PACKAGE);
    }

    @Test
    public void queriesNothingUsesOptionalLibrary_canSeeLibraryPackage() throws Exception {
        assertVisible(QUERIES_NOTHING_USES_OPTIONAL_LIBRARY, TARGET_SHARED_LIBRARY_PACKAGE);
    }

    @Test
    public void queriesNothing_getPackagesForUid_consistentVisibility()
            throws Exception {
        final int targetSharedUid = sPm.getPackageUid(TARGET_SHARED_USER, /* flags */ 0);
        final int targetUid = sPm.getPackageUid(TARGET_FILTERS, /* flags */ 0);
        Assert.assertNull(getPackagesForUid(QUERIES_NOTHING, targetSharedUid));
        Assert.assertNull(getPackagesForUid(QUERIES_NOTHING, targetUid));
    }

    @Test
    public void queriesNothingHasPermission_getPackagesForUid_consistentVisibility()
            throws Exception {
        final int targetSharedUid = sPm.getPackageUid(TARGET_SHARED_USER, /* flags */ 0);
        final int targetUid = sPm.getPackageUid(TARGET_FILTERS, /* flags */ 0);
        Assert.assertNotNull(getPackagesForUid(QUERIES_NOTHING_PERM, targetSharedUid));
        Assert.assertNotNull(getPackagesForUid(QUERIES_NOTHING_PERM, targetUid));
    }

    @Test
    public void queriesNothing_getNameForUid_consistentVisibility()
            throws Exception {
        final int targetSharedUid = sPm.getPackageUid(TARGET_SHARED_USER, /* flags */ 0);
        final int targetUid = sPm.getPackageUid(TARGET_FILTERS, /* flags */ 0);
        Assert.assertNull(getNameForUid(QUERIES_NOTHING, targetSharedUid));
        Assert.assertNull(getNameForUid(QUERIES_NOTHING, targetUid));
    }

    @Test
    public void queriesNothingHasPermission_getNameForUid_consistentVisibility()
            throws Exception {
        final int targetSharedUid = sPm.getPackageUid(TARGET_SHARED_USER, /* flags */ 0);
        final int targetUid = sPm.getPackageUid(TARGET_FILTERS, /* flags */ 0);
        Assert.assertNotNull(getNameForUid(QUERIES_NOTHING_PERM, targetSharedUid));
        Assert.assertNotNull(getNameForUid(QUERIES_NOTHING_PERM, targetUid));
    }

    @Test
    public void queriesNothing_getNamesForUids_consistentVisibility()
            throws Exception {
        final int targetSharedUid = sPm.getPackageUid(TARGET_SHARED_USER, /* flags */ 0);
        final int targetUid = sPm.getPackageUid(TARGET_FILTERS, /* flags */ 0);
        Assert.assertNull(getNamesForUids(QUERIES_NOTHING, targetSharedUid)[0]);
        Assert.assertNull(getNamesForUids(QUERIES_NOTHING, targetUid)[0]);
    }

    @Test
    public void queriesNothingHasPermission_getNamesForUids_consistentVisibility()
            throws Exception {
        final int targetSharedUid = sPm.getPackageUid(TARGET_SHARED_USER, /* flags */ 0);
        final int targetUid = sPm.getPackageUid(TARGET_FILTERS, /* flags */ 0);
        Assert.assertNotNull(getNamesForUids(QUERIES_NOTHING_PERM, targetSharedUid)[0]);
        Assert.assertNotNull(getNamesForUids(QUERIES_NOTHING_PERM, targetUid)[0]);
    }

    @Test
    public void queriesNothing_checkSignatures_consistentVisibility()
            throws Exception {
        final int targetSharedUid = sPm.getPackageUid(TARGET_SHARED_USER, /* flags */ 0);
        final int targetUid = sPm.getPackageUid(TARGET_FILTERS, /* flags */ 0);
        Assert.assertEquals(SIGNATURE_UNKNOWN_PACKAGE,
                checkSignatures(QUERIES_NOTHING, targetSharedUid));
        Assert.assertEquals(SIGNATURE_UNKNOWN_PACKAGE,
                checkSignatures(QUERIES_NOTHING, targetUid));
    }

    @Test
    public void queriesNothingHasPermission_checkSignatures_consistentVisibility()
            throws Exception {
        final int targetSharedUid = sPm.getPackageUid(TARGET_SHARED_USER, /* flags */ 0);
        final int targetUid = sPm.getPackageUid(TARGET_FILTERS, /* flags */ 0);
        Assert.assertEquals(SIGNATURE_MATCH,
                checkSignatures(QUERIES_NOTHING_PERM, targetSharedUid));
        Assert.assertEquals(SIGNATURE_MATCH, checkSignatures(QUERIES_NOTHING_PERM, targetUid));
    }

    @Test
    public void queriesNothing_hasSigningCertificate_consistentVisibility() throws Exception {
        final PackageInfo targetSharedUidInfo = sPm.getPackageInfo(TARGET_SHARED_USER,
                GET_SIGNING_CERTIFICATES);
        final PackageInfo targetUidInfo = sPm.getPackageInfo(TARGET_FILTERS,
                GET_SIGNING_CERTIFICATES);
        final byte[] targetSharedCert = convertSignaturesToCertificates(
                targetSharedUidInfo.signingInfo.getApkContentsSigners()).get(0).getEncoded();
        final byte[] targetCert = convertSignaturesToCertificates(
                targetUidInfo.signingInfo.getApkContentsSigners()).get(0).getEncoded();

        Assert.assertFalse(
                hasSigningCertificate(QUERIES_NOTHING, targetSharedUidInfo.applicationInfo.uid,
                        targetSharedCert));
        Assert.assertFalse(
                hasSigningCertificate(QUERIES_NOTHING, targetUidInfo.applicationInfo.uid,
                        targetCert));
    }

    @Test
    public void queriesNothingHasPermission_hasSigningCertificate_consistentVisibility()
            throws Exception {
        final PackageInfo targetSharedUidInfo = sPm.getPackageInfo(TARGET_SHARED_USER,
                GET_SIGNING_CERTIFICATES);
        final PackageInfo targetUidInfo = sPm.getPackageInfo(TARGET_FILTERS,
                GET_SIGNING_CERTIFICATES);
        final byte[] targetSharedCert = convertSignaturesToCertificates(
                targetSharedUidInfo.signingInfo.getApkContentsSigners()).get(0).getEncoded();
        final byte[] targetCert = convertSignaturesToCertificates(
                targetUidInfo.signingInfo.getApkContentsSigners()).get(0).getEncoded();

        Assert.assertTrue(
                hasSigningCertificate(QUERIES_NOTHING_PERM, targetSharedUidInfo.applicationInfo.uid,
                        targetSharedCert));
        Assert.assertTrue(
                hasSigningCertificate(QUERIES_NOTHING_PERM, targetUidInfo.applicationInfo.uid,
                        targetCert));
    }

    @Test
    public void sharedUserMember_canSeeOtherMember() throws Exception {
        assertVisible(QUERIES_NOTHING_SHARED_USER, TARGET_SHARED_USER);
    }

    @Test
    public void queriesPackage_canSeeAllSharedUserMembers() throws Exception {
        // explicitly queries target via manifest
        assertVisible(QUERIES_PACKAGE, TARGET_SHARED_USER);
        // implicitly granted visibility to other member of shared user
        assertVisible(QUERIES_PACKAGE, QUERIES_NOTHING_SHARED_USER);
    }

    @Test
    public void queriesWildcardContacts() throws Exception {
        assertNotVisible(QUERIES_NOTHING, TARGET_CONTACTS);
        assertVisible(QUERIES_WILDCARD_CONTACTS, TARGET_CONTACTS);
    }

    @Test
    public void queriesWildcardWeb() throws Exception {
        assertNotVisible(QUERIES_NOTHING, TARGET_WEB);
        assertVisible(QUERIES_WILDCARD_BROWSABLE, TARGET_WEB);
        assertVisible(QUERIES_WILDCARD_WEB, TARGET_WEB);
    }

    @Test
    public void queriesWildcardBrowser() throws Exception {
        assertNotVisible(QUERIES_NOTHING, TARGET_BROWSER);
        assertNotVisible(QUERIES_WILDCARD_BROWSER, TARGET_WEB);
        assertVisible(QUERIES_WILDCARD_BROWSER, TARGET_BROWSER);
        assertVisible(QUERIES_WILDCARD_BROWSER, TARGET_BROWSER_WILDCARD);
    }

    @Test
    public void queriesWildcardWeb_canSeePrefixWildcardWeb() throws Exception {
        assertNotVisible(QUERIES_NOTHING, TARGET_PREFIX_WILDCARD_WEB);
        assertVisible(QUERIES_WILDCARD_BROWSABLE, TARGET_PREFIX_WILDCARD_WEB);
        assertVisible(QUERIES_WILDCARD_WEB, TARGET_PREFIX_WILDCARD_WEB);
    }

    @Test
    public void queriesWildcardBrowser_cannotseePrefixWildcardWeb() throws Exception {
        assertNotVisible(QUERIES_NOTHING, TARGET_PREFIX_WILDCARD_WEB);
        assertNotVisible(QUERIES_WILDCARD_BROWSER, TARGET_PREFIX_WILDCARD_WEB);
    }

    @Test
    public void queriesWildcardEditor() throws Exception {
        assertNotVisible(QUERIES_NOTHING, TARGET_EDITOR);
        assertVisible(QUERIES_WILDCARD_EDITOR, TARGET_EDITOR);
    }

    @Test
    public void queriesWildcardShareSheet() throws Exception {
        assertNotVisible(QUERIES_NOTHING, TARGET_SHARE);
        assertVisible(QUERIES_WILDCARD_SHARE, TARGET_SHARE);
    }

    @Test
    public void queriesNothing_cannotSeeA11yService() throws Exception {
        if (!sGlobalFeatureEnabled) return;
        final String[] result = getInstalledAccessibilityServices(QUERIES_NOTHING);
        assertThat(result, not(hasItemInArray(TARGET_FILTERS)));
    }

    @Test
    public void queriesNothingHasPermission_canSeeA11yService() throws Exception {
        if (!sGlobalFeatureEnabled) return;
        final String[] result = getInstalledAccessibilityServices(QUERIES_NOTHING_PERM);
        assertThat(QUERIES_NOTHING_PERM + " should be able to see " + TARGET_FILTERS,
                result, hasItemInArray(TARGET_FILTERS));
    }

    private void assertVisible(String sourcePackageName, String targetPackageName)
            throws Exception {
        if (!sGlobalFeatureEnabled) return;
        Assert.assertNotNull(sourcePackageName + " should be able to see " + targetPackageName,
                getPackageInfo(sourcePackageName, targetPackageName));
    }

    @Test
    public void broadcastAdded_notVisibleDoesNotReceive() throws Exception {
        final Result result = sendCommand(QUERIES_NOTHING, TARGET_FILTERS,
                /* targetUid */ INVALID_UID, /* intentExtra */ null,
                Constants.ACTION_AWAIT_PACKAGE_ADDED, /* waitForReady */ true);
        runShellCommand("pm install " + TARGET_FILTERS_APK);
        try {
            result.await();
            fail();
        } catch (MissingBroadcastException e) {
            // hooray
        }
    }

    @Test
    public void broadcastAdded_visibleReceives() throws Exception {
        final Result result = sendCommand(QUERIES_ACTIVITY_ACTION, TARGET_FILTERS,
                /* targetUid */ INVALID_UID, /* intentExtra */ null,
                Constants.ACTION_AWAIT_PACKAGE_ADDED, /* waitForReady */ true);
        runShellCommand("pm install " + TARGET_FILTERS_APK);
        try {
            Assert.assertEquals(TARGET_FILTERS,
                    Uri.parse(result.await().getString(EXTRA_DATA)).getSchemeSpecificPart());
        } catch (MissingBroadcastException e) {
            fail();
        }
    }

    @Test
    public void reinstallTarget_broadcastRemoved_notVisibleDoesNotReceive() throws Exception {
        final Result result = sendCommand(QUERIES_NOTHING, TARGET_FILTERS,
                /* targetUid */ INVALID_UID, /* intentExtra */ null,
                Constants.ACTION_AWAIT_PACKAGE_REMOVED, /* waitForReady */ true);
        runShellCommand("pm install " + TARGET_FILTERS_APK);
        try {
            result.await();
            fail();
        } catch (MissingBroadcastException e) {
            // hooray
        }
    }

    @Test
    public void reinstallTarget_broadcastRemoved_visibleReceives() throws Exception {
        final Result result = sendCommand(QUERIES_ACTIVITY_ACTION, TARGET_FILTERS,
                /* targetUid */ INVALID_UID, /* intentExtra */ null,
                Constants.ACTION_AWAIT_PACKAGE_REMOVED, /* waitForReady */ true);
        runShellCommand("pm install " + TARGET_FILTERS_APK);
        try {
            Assert.assertEquals(TARGET_FILTERS,
                    Uri.parse(result.await().getString(EXTRA_DATA)).getSchemeSpecificPart());
        } catch (MissingBroadcastException e) {
            fail();
        }
    }

    @Test
    public void uninstallTarget_broadcastRemoved_notVisibleDoesNotReceive() throws Exception {
        ensurePackageIsInstalled(TARGET_STUB, TARGET_STUB_APK);
        final Result result = sendCommand(QUERIES_NOTHING, TARGET_STUB,
                /* targetUid */ INVALID_UID, /* intentExtra */ null,
                Constants.ACTION_AWAIT_PACKAGE_REMOVED, /* waitForReady */ true);
        runShellCommand("pm uninstall " + TARGET_STUB);
        try {
            result.await();
            fail();
        } catch (MissingBroadcastException e) {
            // hooray
        }
    }

    @Test
    public void uninstallTarget_broadcastRemoved_visibleReceives() throws Exception {
        ensurePackageIsInstalled(TARGET_STUB, TARGET_STUB_APK);
        final Result result = sendCommand(QUERIES_NOTHING_PERM, TARGET_STUB,
                /* targetUid */ INVALID_UID, /* intentExtra */ null,
                Constants.ACTION_AWAIT_PACKAGE_REMOVED, /* waitForReady */ true);
        runShellCommand("pm uninstall " + TARGET_STUB);
        try {
            Assert.assertEquals(TARGET_STUB,
                    Uri.parse(result.await().getString(EXTRA_DATA)).getSchemeSpecificPart());
        } catch (MissingBroadcastException e) {
            fail();
        }
    }

    @Test
    public void uninstallTarget_broadcastFullyRemoved_notVisibleDoesNotReceive() throws Exception {
        ensurePackageIsInstalled(TARGET_STUB, TARGET_STUB_APK);
        final Result result = sendCommand(QUERIES_NOTHING, TARGET_STUB,
                /* targetUid */ INVALID_UID, /* intentExtra */ null,
                Constants.ACTION_AWAIT_PACKAGE_FULLY_REMOVED, /* waitForReady */ true);
        runShellCommand("pm uninstall " + TARGET_STUB);
        try {
            result.await();
            fail();
        } catch (MissingBroadcastException e) {
            // hooray
        }
    }

    @Test
    public void uninstallTarget_broadcastFullyRemoved_visibleReceives() throws Exception {
        ensurePackageIsInstalled(TARGET_STUB, TARGET_STUB_APK);
        final Result result = sendCommand(QUERIES_NOTHING_PERM, TARGET_STUB,
                /* targetUid */ INVALID_UID, /* intentExtra */ null,
                Constants.ACTION_AWAIT_PACKAGE_FULLY_REMOVED, /* waitForReady */ true);
        runShellCommand("pm uninstall " + TARGET_STUB);
        try {
            Assert.assertEquals(TARGET_STUB,
                    Uri.parse(result.await().getString(EXTRA_DATA)).getSchemeSpecificPart());
        } catch (MissingBroadcastException e) {
            fail();
        }
    }

    @Test
    public void clearTargetData_broadcastDataCleared_notVisibleDoesNotReceive() throws Exception {
        ensurePackageIsInstalled(TARGET_STUB, TARGET_STUB_APK);
        final Result result = sendCommand(QUERIES_NOTHING, TARGET_STUB,
                /* targetUid */ INVALID_UID, /* intentExtra */ null,
                Constants.ACTION_AWAIT_PACKAGE_DATA_CLEARED, /* waitForReady */ true);
        runShellCommand("pm clear --user cur " + TARGET_STUB);
        try {
            result.await();
            fail();
        } catch (MissingBroadcastException e) {
            // hooray
        }
    }

    @Test
    public void clearTargetData_broadcastDataCleared_visibleReceives() throws Exception {
        ensurePackageIsInstalled(TARGET_STUB, TARGET_STUB_APK);
        final Result result = sendCommand(QUERIES_NOTHING_PERM, TARGET_STUB,
                /* targetUid */ INVALID_UID, /* intentExtra */ null,
                Constants.ACTION_AWAIT_PACKAGE_DATA_CLEARED, /* waitForReady */ true);
        runShellCommand("pm clear --user cur " + TARGET_STUB);
        try {
            Assert.assertEquals(TARGET_STUB,
                    Uri.parse(result.await().getString(EXTRA_DATA)).getSchemeSpecificPart());
        } catch (MissingBroadcastException e) {
            fail();
        }
    }

    @Test
    public void broadcastSuspended_visibleReceives() throws Exception {
        assertBroadcastSuspendedVisible(QUERIES_PACKAGE,
                Arrays.asList(TARGET_NO_API, TARGET_SYNCADAPTER),
                Arrays.asList(TARGET_NO_API, TARGET_SYNCADAPTER));
    }

    @Test
    public void broadcastSuspended_notVisibleDoesNotReceive() throws Exception {
        assertBroadcastSuspendedVisible(QUERIES_NOTHING,
                Arrays.asList(),
                Arrays.asList(TARGET_NO_API, TARGET_SYNCADAPTER));
    }

    @Test
    public void broadcastSuspended_visibleReceivesAndNotVisibleDoesNotReceive() throws Exception {
        assertBroadcastSuspendedVisible(QUERIES_ACTIVITY_ACTION,
                Arrays.asList(TARGET_FILTERS),
                Arrays.asList(TARGET_NO_API, TARGET_FILTERS));
    }

    @Test
    public void launcherAppsCallback_added_notVisibleNotReceives() throws Exception {
        ensurePackageIsNotInstalled(TARGET_STUB);
        final Result result = sendCommandAndWaitForLauncherAppsCallback(QUERIES_NOTHING,
                CALLBACK_EVENT_PACKAGE_ADDED);

        runShellCommand("pm install " + TARGET_STUB_APK);
        final Bundle response = result.await();

        assertThat(response.getInt(EXTRA_FLAGS), equalTo(CALLBACK_EVENT_INVALID));
        assertThat(response.getStringArray(EXTRA_PACKAGES), emptyArray());
    }

    @Test
    public void launcherAppsCallback_added_visibleReceives() throws Exception {
        ensurePackageIsNotInstalled(TARGET_STUB);
        final Result result = sendCommandAndWaitForLauncherAppsCallback(QUERIES_NOTHING_PERM,
                CALLBACK_EVENT_PACKAGE_ADDED);

        runShellCommand("pm install " + TARGET_STUB_APK);
        final Bundle response = result.await();

        assertThat(response.getInt(EXTRA_FLAGS), equalTo(CALLBACK_EVENT_PACKAGE_ADDED));
        assertThat(response.getStringArray(EXTRA_PACKAGES),
                arrayContainingInAnyOrder(new String[]{TARGET_STUB}));
    }

    @Test
    public void launcherAppsCallback_removed_notVisibleNotReceives() throws Exception {
        ensurePackageIsInstalled(TARGET_STUB, TARGET_STUB_APK);
        final Result result = sendCommandAndWaitForLauncherAppsCallback(QUERIES_NOTHING,
                CALLBACK_EVENT_PACKAGE_REMOVED);

        runShellCommand("pm uninstall " + TARGET_STUB);
        final Bundle response = result.await();

        assertThat(response.getInt(EXTRA_FLAGS), equalTo(CALLBACK_EVENT_INVALID));
        assertThat(response.getStringArray(EXTRA_PACKAGES), emptyArray());
    }

    @Test
    public void launcherAppsCallback_removed_visibleReceives() throws Exception {
        ensurePackageIsInstalled(TARGET_STUB, TARGET_STUB_APK);
        final Result result = sendCommandAndWaitForLauncherAppsCallback(QUERIES_NOTHING_PERM,
                CALLBACK_EVENT_PACKAGE_REMOVED);

        runShellCommand("pm uninstall " + TARGET_STUB);
        final Bundle response = result.await();

        assertThat(response.getInt(EXTRA_FLAGS), equalTo(CALLBACK_EVENT_PACKAGE_REMOVED));
        assertThat(response.getStringArray(EXTRA_PACKAGES),
                arrayContainingInAnyOrder(new String[]{TARGET_STUB}));
    }

    @Test
    public void launcherAppsCallback_changed_notVisibleNotReceives() throws Exception {
        final Result result = sendCommandAndWaitForLauncherAppsCallback(QUERIES_NOTHING,
                CALLBACK_EVENT_PACKAGE_CHANGED);

        runShellCommand("pm install " + TARGET_FILTERS_APK);
        final Bundle response = result.await();

        assertThat(response.getInt(EXTRA_FLAGS), equalTo(CALLBACK_EVENT_INVALID));
        assertThat(response.getStringArray(EXTRA_PACKAGES), emptyArray());
    }

    @Test
    public void launcherAppsCallback_changed_visibleReceives() throws Exception {
        final Result result = sendCommandAndWaitForLauncherAppsCallback(QUERIES_NOTHING_PERM,
                CALLBACK_EVENT_PACKAGE_CHANGED);

        runShellCommand("pm install " + TARGET_FILTERS_APK);
        final Bundle response = result.await();

        assertThat(response.getInt(EXTRA_FLAGS), equalTo(CALLBACK_EVENT_PACKAGE_CHANGED));
        assertThat(response.getStringArray(EXTRA_PACKAGES),
                arrayContainingInAnyOrder(new String[]{TARGET_FILTERS}));
    }

    @Test
    public void launcherAppsCallback_suspended_notVisibleNotReceives() throws Exception {
        final Result result = sendCommandAndWaitForLauncherAppsCallback(QUERIES_NOTHING,
                CALLBACK_EVENT_PACKAGES_SUSPENDED);

        try {
            setPackagesSuspended(/* suspend */ true,
                    Arrays.asList(TARGET_NO_API, TARGET_FILTERS));
            final Bundle response = result.await();

            assertThat(response.getInt(EXTRA_FLAGS), equalTo(CALLBACK_EVENT_INVALID));
            assertThat(response.getStringArray(EXTRA_PACKAGES), emptyArray());
        } finally {
            setPackagesSuspended(/* suspend */ false,
                    Arrays.asList(TARGET_NO_API, TARGET_FILTERS));
        }
    }

    @Test
    public void launcherAppsCallback_suspended_visibleReceives() throws Exception {
        final Result result = sendCommandAndWaitForLauncherAppsCallback(QUERIES_ACTIVITY_ACTION,
                CALLBACK_EVENT_PACKAGES_SUSPENDED);

        try {
            setPackagesSuspended(/* suspend */ true,
                    Arrays.asList(TARGET_NO_API, TARGET_FILTERS));
            final Bundle response = result.await();

            assertThat(response.getInt(EXTRA_FLAGS), equalTo(CALLBACK_EVENT_PACKAGES_SUSPENDED));
            assertThat(response.getStringArray(EXTRA_PACKAGES),
                    arrayContainingInAnyOrder(new String[]{TARGET_FILTERS}));
        } finally {
            setPackagesSuspended(/* suspend */ false,
                    Arrays.asList(TARGET_NO_API, TARGET_FILTERS));
        }
    }

    @Test
    public void launcherAppsCallback_unsuspended_notVisibleNotReceives() throws Exception {
        final Result result = sendCommandAndWaitForLauncherAppsCallback(QUERIES_NOTHING,
                CALLBACK_EVENT_PACKAGES_UNSUSPENDED);

        setPackagesSuspended(/* suspend */ false, Arrays.asList(TARGET_NO_API, TARGET_FILTERS));
        final Bundle response = result.await();

        assertThat(response.getInt(EXTRA_FLAGS), equalTo(CALLBACK_EVENT_INVALID));
        assertThat(response.getStringArray(EXTRA_PACKAGES), emptyArray());
    }

    @Test
    public void launcherAppsCallback_unsuspended_visibleReceives() throws Exception {
        setPackagesSuspended(/* suspend */ true, Arrays.asList(TARGET_NO_API, TARGET_FILTERS));

        final Result result = sendCommandAndWaitForLauncherAppsCallback(QUERIES_ACTIVITY_ACTION,
                CALLBACK_EVENT_PACKAGES_UNSUSPENDED);

        setPackagesSuspended(/* suspend */ false, Arrays.asList(TARGET_NO_API, TARGET_FILTERS));
        final Bundle response = result.await();

        assertThat(response.getInt(EXTRA_FLAGS), equalTo(CALLBACK_EVENT_PACKAGES_UNSUSPENDED));
        assertThat(response.getStringArray(EXTRA_PACKAGES),
                arrayContainingInAnyOrder(new String[]{TARGET_FILTERS}));
    }

    @Test
    public void launcherAppsSessionCallback_queriesNothing_cannotSeeSession() throws Exception {
        try {
            adoptShellPermissions();
            final int sessionId = Install.single(TestApp.A1).setPackageName(TestApp.A)
                    .createSession();
            final Result result = sendCommandAndWaitForLauncherAppsSessionCallback(
                    QUERIES_NOTHING, sessionId);
            commitSession(sessionId);
            final Bundle response = result.await();
            assertThat(response.getInt(EXTRA_ID), equalTo(SessionInfo.INVALID_ID));
        } finally {
            runShellCommand("pm uninstall " + TestApp.A);
            dropShellPermissions();
        }
    }

    @Test
    public void launcherAppsSessionCallback_queriesNothingHasPermission_canSeeSession()
            throws Exception {
        try {
            adoptShellPermissions();
            final int sessionId = Install.single(TestApp.A1).setPackageName(TestApp.A)
                    .createSession();
            final Result result = sendCommandAndWaitForLauncherAppsSessionCallback(
                    QUERIES_NOTHING_PERM, sessionId);
            commitSession(sessionId);
            final Bundle response = result.await();
            assertThat(response.getInt(EXTRA_ID), equalTo(sessionId));
        } finally {
            runShellCommand("pm uninstall " + TestApp.A);
            dropShellPermissions();
        }
    }

    @Test
    public void launcherAppsSessionCallback_queriesPackage_canSeeSession()
            throws Exception {
        try {
            adoptShellPermissions();
            final int sessionId = Install.single(TestApp.A1).setPackageName(TestApp.A)
                    .createSession();
            final Result result = sendCommandAndWaitForLauncherAppsSessionCallback(
                    QUERIES_PACKAGE, sessionId);
            commitSession(sessionId);
            final Bundle response = result.await();
            assertThat(response.getInt(EXTRA_ID), equalTo(sessionId));
        } finally {
            runShellCommand("pm uninstall " + TestApp.A);
            dropShellPermissions();
        }
    }

    @Test
    public void launcherAppsSessionCallback_queriesNothingTargetsQ_canSeeSession()
            throws Exception {
        try {
            adoptShellPermissions();
            final int sessionId = Install.single(TestApp.A1).setPackageName(TestApp.A)
                    .createSession();
            final Result result = sendCommandAndWaitForLauncherAppsSessionCallback(
                    QUERIES_NOTHING_Q, sessionId);
            commitSession(sessionId);
            final Bundle response = result.await();
            assertThat(response.getInt(EXTRA_ID), equalTo(sessionId));
        } finally {
            runShellCommand("pm uninstall " + TestApp.A);
            dropShellPermissions();
        }
    }

    @Test
    public void launcherAppsSessionCallback_sessionOwner_canSeeSession() throws Exception {
        try {
            adoptShellPermissions();
            final CountDownLatch countDownLatch = new CountDownLatch(1);
            final int expectedSessionId = Install.single(TestApp.A1).setPackageName(TestApp.A)
                    .createSession();
            final Context context = InstrumentationRegistry
                    .getInstrumentation()
                    .getContext();
            final LauncherApps launcherApps = context.getSystemService(LauncherApps.class);
            final PackageInstaller.SessionCallback
                    sessionCallback = new PackageInstaller.SessionCallback() {

                @Override
                public void onCreated(int sessionId) {
                    // No-op
                }

                @Override
                public void onBadgingChanged(int sessionId) {
                    // No-op
                }

                @Override
                public void onActiveChanged(int sessionId, boolean active) {
                    // No-op
                }

                @Override
                public void onProgressChanged(int sessionId, float progress) {
                    // No-op
                }

                @Override
                public void onFinished(int sessionId, boolean success) {
                    if (sessionId != expectedSessionId) {
                        return;
                    }

                    launcherApps.unregisterPackageInstallerSessionCallback(this);
                    countDownLatch.countDown();
                }
            };

            launcherApps.registerPackageInstallerSessionCallback(context.getMainExecutor(),
                    sessionCallback);

            commitSession(expectedSessionId);
            assertTrue(countDownLatch.await(5, TimeUnit.SECONDS));
        } finally {
            runShellCommand("pm uninstall " + TestApp.A);
            dropShellPermissions();
        }
    }

    private Result sendCommandAndWaitForLauncherAppsSessionCallback(String sourcePackageName,
            int expectedSessionId) throws Exception {
        final Bundle extra = new Bundle();
        extra.putInt(EXTRA_ID, expectedSessionId);
        final Result result = sendCommand(sourcePackageName, /* targetPackageName */ null,
                /* targetUid */ INVALID_UID, extra, ACTION_AWAIT_LAUNCHER_APPS_SESSION_CALLBACK,
                /* waitForReady */ true);
        return result;
    }

    @Test
    public void launcherApps_getAllPkgInstallerSessions_queriesNothing_cannotSeeSessions()
            throws Exception {
        try {
            adoptShellPermissions();
            final int sessionId = Install.single(TestApp.A1).setPackageName(TestApp.A)
                    .createSession();
            final Integer[] sessionIds = getSessionInfos(ACTION_GET_ALL_PACKAGE_INSTALLER_SESSIONS,
                    QUERIES_NOTHING, SessionInfo.INVALID_ID);
            assertSessionNotVisible(sessionIds, sessionId);
        } finally {
            cleanUpSessions();
            dropShellPermissions();
        }
    }

    @Test
    public void launcherApps_getAllPkgInstallerSessions_queriesNothingHasPermission_canSeeSessions()
            throws Exception {
        try {
            adoptShellPermissions();
            final int sessionId = Install.single(TestApp.A1).setPackageName(TestApp.A)
                    .createSession();
            final Integer[] sessionIds = getSessionInfos(ACTION_GET_ALL_PACKAGE_INSTALLER_SESSIONS,
                    QUERIES_NOTHING_PERM, SessionInfo.INVALID_ID);
            assertSessionVisible(sessionIds, sessionId);
        } finally {
            cleanUpSessions();
            dropShellPermissions();
        }
    }

    @Test
    public void launcherApps_getAllPkgInstallerSessions_queriesPackage_canSeeSessions()
            throws Exception {
        try {
            adoptShellPermissions();
            final int sessionId = Install.single(TestApp.A1).setPackageName(TestApp.A)
                    .createSession();
            final Integer[] sessionIds = getSessionInfos(ACTION_GET_ALL_PACKAGE_INSTALLER_SESSIONS,
                    QUERIES_PACKAGE, SessionInfo.INVALID_ID);
            assertSessionVisible(sessionIds, sessionId);
        } finally {
            cleanUpSessions();
            dropShellPermissions();
        }
    }

    @Test
    public void launcherApps_getAllPkgInstallerSessions_queriesNothingTargetsQ_canSeeSessions()
            throws Exception {
        try {
            adoptShellPermissions();
            final int sessionId = Install.single(TestApp.A1).setPackageName(TestApp.A)
                    .createSession();
            final Integer[] sessionIds = getSessionInfos(ACTION_GET_ALL_PACKAGE_INSTALLER_SESSIONS,
                    QUERIES_NOTHING_Q, SessionInfo.INVALID_ID);
            assertSessionVisible(sessionIds, sessionId);
        } finally {
            cleanUpSessions();
            dropShellPermissions();
        }
    }

    @Test
    public void launcherApps_getAllPkgInstallerSessions_sessionOwner_canSeeSessions()
            throws Exception {
        try {
            adoptShellPermissions();
            final int sessionId = Install.single(TestApp.A1).setPackageName(TestApp.A)
                    .createSession();
            final LauncherApps launcherApps = InstrumentationRegistry
                    .getInstrumentation()
                    .getContext()
                    .getSystemService(LauncherApps.class);
            final Integer[] sessionIds = launcherApps.getAllPackageInstallerSessions().stream()
                    .map(i -> i.getSessionId())
                    .distinct()
                    .toArray(Integer[]::new);
            assertSessionVisible(sessionIds, sessionId);
        } finally {
            cleanUpSessions();
            dropShellPermissions();
        }
    }

    @Test
    public void packageInstaller_getSessionInfo_queriesNothing_cannotSeeSession()
            throws Exception {
        try {
            adoptShellPermissions();
            final int sessionId = Install.single(TestApp.A1).setPackageName(TestApp.A)
                    .createSession();
            final Integer[] sessionIds = getSessionInfos(ACTION_GET_SESSION_INFO,
                    QUERIES_NOTHING, sessionId);
            assertSessionNotVisible(sessionIds, sessionId);
        } finally {
            cleanUpSessions();
            dropShellPermissions();
        }
    }

    @Test
    public void packageInstaller_getSessionInfo_queriesNothingHasPermission_canSeeSession()
            throws Exception {
        try {
            adoptShellPermissions();
            final int sessionId = Install.single(TestApp.A1).setPackageName(TestApp.A)
                    .createSession();
            final Integer[] sessionIds = getSessionInfos(ACTION_GET_SESSION_INFO,
                    QUERIES_NOTHING_PERM, sessionId);
            assertSessionVisible(sessionIds, sessionId);
        } finally {
            cleanUpSessions();
            dropShellPermissions();
        }
    }

    @Test
    public void packageInstaller_getSessionInfo_queriesPackage_canSeeSession()
            throws Exception {
        try {
            adoptShellPermissions();
            final int sessionId = Install.single(TestApp.A1).setPackageName(TestApp.A)
                    .createSession();
            final Integer[] sessionIds = getSessionInfos(ACTION_GET_SESSION_INFO,
                    QUERIES_PACKAGE, sessionId);
            assertSessionVisible(sessionIds, sessionId);
        } finally {
            cleanUpSessions();
            dropShellPermissions();
        }
    }

    @Test
    public void packageInstaller_getSessionInfo_queriesNothingTargetsQ_canSeeSession()
            throws Exception {
        try {
            adoptShellPermissions();
            final int sessionId = Install.single(TestApp.A1).setPackageName(TestApp.A)
                    .createSession();
            final Integer[] sessionIds = getSessionInfos(ACTION_GET_SESSION_INFO,
                    QUERIES_NOTHING_Q, sessionId);
            assertSessionVisible(sessionIds, sessionId);
        } finally {
            cleanUpSessions();
            dropShellPermissions();
        }
    }

    @Test
    public void packageInstaller_getSessionInfo_sessionOwner_canSeeSession()
            throws Exception {
        try {
            adoptShellPermissions();
            final int sessionId = Install.single(TestApp.A1).setPackageName(TestApp.A)
                    .createSession();
            final PackageInstaller installer = InstrumentationRegistry
                    .getInstrumentation()
                    .getContext()
                    .getPackageManager()
                    .getPackageInstaller();
            final SessionInfo info = installer.getSessionInfo(sessionId);
            assertThat(info, IsNull.notNullValue());
        } finally {
            cleanUpSessions();
            dropShellPermissions();
        }
    }

    @Test
    public void packageInstaller_getStagedSessions_queriesNothing_cannotSeeSession()
            throws Exception {
        try {
            adoptShellPermissions();
            final int sessionId = Install.single(TestApp.A1).setPackageName(TestApp.A).setStaged()
                    .createSession();
            final Integer[] sessionIds = getSessionInfos(ACTION_GET_STAGED_SESSIONS,
                    QUERIES_NOTHING, sessionId);
            assertSessionNotVisible(sessionIds, sessionId);
        } finally {
            cleanUpSessions();
            dropShellPermissions();
        }
    }

    @Test
    public void packageInstaller_getStagedSessions_queriesNothingHasPermission_canSeeSession()
            throws Exception {
        try {
            adoptShellPermissions();
            final int sessionId = Install.single(TestApp.A1).setPackageName(TestApp.A).setStaged()
                    .createSession();
            final Integer[] sessionIds = getSessionInfos(ACTION_GET_STAGED_SESSIONS,
                    QUERIES_NOTHING_PERM, sessionId);
            assertSessionVisible(sessionIds, sessionId);
        } finally {
            cleanUpSessions();
            dropShellPermissions();
        }
    }

    @Test
    public void packageInstaller_getStagedSessions_queriesPackage_canSeeSession()
            throws Exception {
        try {
            adoptShellPermissions();
            final int sessionId = Install.single(TestApp.A1).setPackageName(TestApp.A).setStaged()
                    .createSession();
            final Integer[] sessionIds = getSessionInfos(ACTION_GET_STAGED_SESSIONS,
                    QUERIES_PACKAGE, sessionId);
            assertSessionVisible(sessionIds, sessionId);
        } finally {
            cleanUpSessions();
            dropShellPermissions();
        }
    }

    @Test
    public void packageInstaller_getStagedSessions_queriesNothingTargetsQ_canSeeSession()
            throws Exception {
        try {
            adoptShellPermissions();
            final int sessionId = Install.single(TestApp.A1).setPackageName(TestApp.A).setStaged()
                    .createSession();
            final Integer[] sessionIds = getSessionInfos(ACTION_GET_STAGED_SESSIONS,
                    QUERIES_NOTHING_Q, sessionId);
            assertSessionVisible(sessionIds, sessionId);
        } finally {
            cleanUpSessions();
            dropShellPermissions();
        }
    }

    @Test
    public void packageInstaller_getStagedSessions_sessionOwner_canSeeSession()
            throws Exception {
        try {
            adoptShellPermissions();
            final int sessionId = Install.single(TestApp.A1).setPackageName(TestApp.A).setStaged()
                    .createSession();
            final PackageInstaller installer = InstrumentationRegistry
                    .getInstrumentation()
                    .getContext()
                    .getPackageManager()
                    .getPackageInstaller();
            final Integer[] sessionIds = installer.getStagedSessions().stream()
                    .map(i -> i.getSessionId())
                    .distinct()
                    .toArray(Integer[]::new);
            assertSessionVisible(sessionIds, sessionId);
        } finally {
            cleanUpSessions();
            dropShellPermissions();
        }
    }

    @Test
    public void packageInstaller_getAllSessions_queriesNothing_cannotSeeSession()
            throws Exception {
        try {
            adoptShellPermissions();
            final int sessionId = Install.single(TestApp.A1).setPackageName(TestApp.A)
                    .createSession();
            final Integer[] sessionIds = getSessionInfos(ACTION_GET_ALL_SESSIONS,
                    QUERIES_NOTHING, sessionId);
            assertSessionNotVisible(sessionIds, sessionId);
        } finally {
            cleanUpSessions();
            dropShellPermissions();
        }
    }

    @Test
    public void packageInstaller_getAllSessions_queriesNothingHasPermission_canSeeSession()
            throws Exception {
        try {
            adoptShellPermissions();
            final int sessionId = Install.single(TestApp.A1).setPackageName(TestApp.A)
                    .createSession();
            final Integer[] sessionIds = getSessionInfos(ACTION_GET_ALL_SESSIONS,
                    QUERIES_NOTHING_PERM, sessionId);
            assertSessionVisible(sessionIds, sessionId);
        } finally {
            cleanUpSessions();
            dropShellPermissions();
        }
    }

    @Test
    public void packageInstaller_getAllSessions_queriesPackage_canSeeSession()
            throws Exception {
        try {
            adoptShellPermissions();
            final int sessionId = Install.single(TestApp.A1).setPackageName(TestApp.A)
                    .createSession();
            final Integer[] sessionIds = getSessionInfos(ACTION_GET_ALL_SESSIONS,
                    QUERIES_PACKAGE, sessionId);
            assertSessionVisible(sessionIds, sessionId);
        } finally {
            cleanUpSessions();
            dropShellPermissions();
        }
    }

    @Test
    public void packageInstaller_getAllSessions_queriesNothingTargetsQ_canSeeSession()
            throws Exception {
        try {
            adoptShellPermissions();
            final int sessionId = Install.single(TestApp.A1).setPackageName(TestApp.A)
                    .createSession();
            final Integer[] sessionIds = getSessionInfos(ACTION_GET_ALL_SESSIONS,
                    QUERIES_NOTHING_Q, sessionId);
            assertSessionVisible(sessionIds, sessionId);
        } finally {
            cleanUpSessions();
            dropShellPermissions();
        }
    }

    @Test
    public void packageInstaller_getAllSessions_sessionOwner_canSeeSession()
            throws Exception {
        try {
            adoptShellPermissions();
            final int sessionId = Install.single(TestApp.A1).setPackageName(TestApp.A)
                    .createSession();
            final PackageInstaller installer = InstrumentationRegistry
                    .getInstrumentation()
                    .getContext()
                    .getPackageManager()
                    .getPackageInstaller();
            final Integer[] sessionIds = installer.getAllSessions().stream()
                    .map(i -> i.getSessionId())
                    .distinct()
                    .toArray(Integer[]::new);
            assertSessionVisible(sessionIds, sessionId);
        } finally {
            cleanUpSessions();
            dropShellPermissions();
        }
    }

    private Integer[] getSessionInfos(String action, String sourcePackageName, int sessionId)
            throws Exception {
        final Bundle extraData = new Bundle();
        extraData.putInt(EXTRA_ID, sessionId);
        final Bundle response = sendCommandBlocking(sourcePackageName, /* targetPackageName */ null,
                extraData, action);
        final List<Parcelable> parcelables = response.getParcelableArrayList(
                Intent.EXTRA_RETURN_RESULT);
        return parcelables.stream()
                .map(i -> (i == null ? SessionInfo.INVALID_ID : ((SessionInfo) i).getSessionId()))
                .distinct()
                .toArray(Integer[]::new);
    }

    private void assertSessionVisible(Integer[] sessionIds, int sessionId) {
        if (!sGlobalFeatureEnabled) {
            return;
        }
        assertThat(sessionIds, hasItemInArray(sessionId));
    }

    private void assertSessionNotVisible(Integer[] sessionIds, int sessionId) {
        if (!sGlobalFeatureEnabled) {
            return;
        }
        assertThat(sessionIds, not(hasItemInArray(sessionId)));
    }

    private static void commitSession(int sessionId) throws Exception {
        final PackageInstaller.Session session =
                InstallUtils.openPackageInstallerSession(sessionId);
        final LocalIntentSender sender = new LocalIntentSender();
        session.commit(sender.getIntentSender());
        InstallUtils.assertStatusSuccess(sender.getResult());
    }

    private void adoptShellPermissions() {
        InstrumentationRegistry
                .getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.INSTALL_PACKAGES);
    }

    private void dropShellPermissions() {
        InstrumentationRegistry
                .getInstrumentation()
                .getUiAutomation()
                .dropShellPermissionIdentity();
    }

    private void cleanUpSessions() {
        InstallUtils.getPackageInstaller().getMySessions().forEach(info -> {
            try {
                InstallUtils.getPackageInstaller().abandonSession(info.getSessionId());
            } catch (Exception ignore) {
            }
        });
    }

    @Test
    public void queriesResolver_grantsVisibilityToProvider() throws Exception {
        assertNotVisible(QUERIES_NOTHING_PROVIDER, QUERIES_NOTHING_PERM);

        String[] result = sendCommandBlocking(
                QUERIES_NOTHING_PERM, QUERIES_NOTHING_PROVIDER, null, ACTION_QUERY_RESOLVER)
                .getStringArray(Intent.EXTRA_RETURN_RESULT);
        Arrays.sort(result);
        assertThat(QUERIES_NOTHING_PERM + " not visible to " + QUERIES_NOTHING_PROVIDER
                        + " during resolver interaction",
                Arrays.binarySearch(result, QUERIES_NOTHING_PERM),
                greaterThanOrEqualTo(0));

        assertVisible(QUERIES_NOTHING_PROVIDER, QUERIES_NOTHING_PERM);
    }

    @Test
    public void bindService_consistentVisibility() throws Exception {
        // Ensure package visibility isn't impacted by optimization or cached result.
        // Target service shouldn't be visible to app without query permission even if
        // another app with query permission is binding it.
        assertServiceVisible(QUERIES_NOTHING_PERM, TARGET_FILTERS);
        assertServiceNotVisible(QUERIES_NOTHING, TARGET_FILTERS);
    }

    @Test
    public void queriesPackage_getSyncAdapterTypes_canSeeSyncadapterTarget() throws Exception {
        assertVisible(QUERIES_PACKAGE, TARGET_SYNCADAPTER, this::getSyncAdapterTypes);
    }

    @Test
    public void queriesNothing_getSyncAdapterTypes_cannotSeeSyncadapterTarget() throws Exception {
        assertNotVisible(QUERIES_NOTHING, TARGET_SYNCADAPTER, this::getSyncAdapterTypes);
        assertNotVisible(QUERIES_NOTHING, TARGET_SYNCADAPTER_SHARED_USER,
                this::getSyncAdapterTypes);
    }

    @Test
    public void queriesNothingSharedUser_getSyncAdapterTypes_canSeeSyncadapterSharedUserTarget()
            throws Exception {
        assertVisible(QUERIES_NOTHING_SHARED_USER, TARGET_SYNCADAPTER_SHARED_USER,
                this::getSyncAdapterTypes);
    }

    @Test
    public void queriesPackage_getSyncAdapterPackages_canSeeSyncadapterTarget()
            throws Exception {
        assertVisible(QUERIES_PACKAGE, TARGET_SYNCADAPTER,
                this::getSyncAdapterPackagesForAuthorityAsUser);
    }

    @Test
    public void queriesNothing_getSyncAdapterPackages_cannotSeeSyncadapterTarget()
            throws Exception {
        assertNotVisible(QUERIES_NOTHING, TARGET_SYNCADAPTER,
                this::getSyncAdapterPackagesForAuthorityAsUser);
        assertNotVisible(QUERIES_NOTHING, TARGET_SYNCADAPTER_SHARED_USER,
                this::getSyncAdapterPackagesForAuthorityAsUser);
    }

    @Test
    public void queriesPackage_requestSync_canSeeSyncadapterTarget()
            throws Exception {
        assertThat(requestSyncAndAwaitStatus(QUERIES_PACKAGE,
                        ACCOUNT_SYNCADAPTER, TARGET_SYNCADAPTER),
                is(true));
    }

    @Test
    public void queriesNothingSharedUser_requestSync_canSeeSyncadapterSharedUserTarget()
            throws Exception {
        assertThat(requestSyncAndAwaitStatus(QUERIES_NOTHING_SHARED_USER,
                        ACCOUNT_SYNCADAPTER_SHARED_USER, TARGET_SYNCADAPTER_SHARED_USER),
                is(true));
    }

    @Test
    public void queriesNothing_requestSync_cannotSeeSyncadapterTarget() {
        assertThrows(MissingCallbackException.class,
                () -> requestSyncAndAwaitStatus(QUERIES_NOTHING,
                        ACCOUNT_SYNCADAPTER, TARGET_SYNCADAPTER));
        assertThrows(MissingCallbackException.class,
                () -> requestSyncAndAwaitStatus(QUERIES_NOTHING,
                        ACCOUNT_SYNCADAPTER_SHARED_USER, TARGET_SYNCADAPTER_SHARED_USER));
    }

    @Test
    public void queriesNothingSharedUser_getSyncAdapterPackages_canSeeSyncadapterSharedUserTarget()
            throws Exception {
        assertVisible(QUERIES_NOTHING_SHARED_USER, TARGET_SYNCADAPTER_SHARED_USER,
                this::getSyncAdapterPackagesForAuthorityAsUser);
    }

    @Test
    public void launcherAppsIsActivityEnabled_queriesActivityAction_canSeeActivity()
            throws Exception {
        final ComponentName targetFilters = ComponentName.createRelative(TARGET_FILTERS,
                ACTIVITY_CLASS_DUMMY_ACTIVITY);
        assertThat(QUERIES_ACTIVITY_ACTION + " should be able to see " + targetFilters,
                launcherAppsIsActivityEnabled(QUERIES_ACTIVITY_ACTION, targetFilters),
                is(true));
    }

    @Test
    public void launcherAppsIsActivityEnabled_queriesNothing_cannotSeeActivity()
            throws Exception {
        final ComponentName targetFilters = ComponentName.createRelative(TARGET_FILTERS,
                ACTIVITY_CLASS_DUMMY_ACTIVITY);
        assertThat(QUERIES_ACTIVITY_ACTION + " should not be able to see " + targetFilters,
                launcherAppsIsActivityEnabled(QUERIES_NOTHING, targetFilters),
                is(false));
    }

    @Test
    public void launcherAppsGetSuspendedPackageLauncherExtras_queriesNothingHasPerm_canGetExtras()
            throws Exception {
        try {
            setPackagesSuspendedWithLauncherExtras(/* suspend */ true,
                    Arrays.asList(TARGET_NO_API), /* extras */ true);
            Assert.assertNotNull(launcherAppsGetSuspendedPackageLauncherExtras(QUERIES_NOTHING_PERM,
                            TARGET_NO_API));
        } finally {
            setPackagesSuspended(/* suspend */ false, Arrays.asList(TARGET_NO_API));
        }
    }

    @Test
    public void launcherAppsGetSuspendedPackageLauncherExtras_queriesNothing_cannotGetExtras()
            throws Exception {
        try {
            setPackagesSuspendedWithLauncherExtras(/* suspend */ true,
                    Arrays.asList(TARGET_NO_API), /* extras */ true);
            Assert.assertNull(launcherAppsGetSuspendedPackageLauncherExtras(QUERIES_NOTHING,
                    TARGET_NO_API));
        } finally {
            setPackagesSuspended(/* suspend */ false, Arrays.asList(TARGET_NO_API));
        }
    }

    @Test
    public void launcherAppsShouldHideFromSuggestions_queriesPackage_canSeeNoApi()
            throws Exception {
        setDistractingPackageRestrictions(new String[]{TARGET_NO_API},
                PackageManager.RESTRICTION_HIDE_FROM_SUGGESTIONS);

        try {
            final boolean hideFromSuggestions = shouldHideFromSuggestions(
                    QUERIES_PACKAGE, TARGET_NO_API);
            assertThat(hideFromSuggestions, is(true));
        } finally {
            setDistractingPackageRestrictions(new String[]{TARGET_NO_API},
                    PackageManager.RESTRICTION_NONE);
        }
    }

    @Test
    public void launcherAppsShouldHideFromSuggestions_queriesNothing_cannotSeeNoApi()
            throws Exception {
        setDistractingPackageRestrictions(new String[]{TARGET_NO_API},
                PackageManager.RESTRICTION_HIDE_FROM_SUGGESTIONS);

        try {
            final boolean hideFromSuggestions = shouldHideFromSuggestions(
                    QUERIES_NOTHING, TARGET_NO_API);
            assertThat(hideFromSuggestions, is(false));
        } finally {
            setDistractingPackageRestrictions(new String[]{TARGET_NO_API},
                    PackageManager.RESTRICTION_NONE);
        }
    }

    @Test
    public void queriesPackage_canSeeAppWidgetProviderTarget() throws Exception {
        assumeTrue(InstrumentationRegistry.getInstrumentation().getContext().getPackageManager()
                .hasSystemFeature(PackageManager.FEATURE_APP_WIDGETS));

        assertVisible(QUERIES_PACKAGE, TARGET_APPWIDGETPROVIDER,
                this::getInstalledAppWidgetProviders);
    }

    @Test
    public void queriesNothing_cannotSeeAppWidgetProviderTarget() throws Exception {
        assumeTrue(InstrumentationRegistry.getInstrumentation().getContext().getPackageManager()
                .hasSystemFeature(PackageManager.FEATURE_APP_WIDGETS));

        assertNotVisible(QUERIES_NOTHING, TARGET_APPWIDGETPROVIDER,
                this::getInstalledAppWidgetProviders);
        assertNotVisible(QUERIES_NOTHING, TARGET_APPWIDGETPROVIDER_SHARED_USER,
                this::getInstalledAppWidgetProviders);
    }

    @Test
    public void queriesNothingSharedUser_canSeeAppWidgetProviderSharedUserTarget()
            throws Exception {
        assumeTrue(InstrumentationRegistry.getInstrumentation().getContext().getPackageManager()
                .hasSystemFeature(PackageManager.FEATURE_APP_WIDGETS));

        assertVisible(QUERIES_NOTHING_SHARED_USER, TARGET_APPWIDGETPROVIDER_SHARED_USER,
                this::getInstalledAppWidgetProviders);
    }

    @Test
    public void queriesNothing_cannotSeeSharedLibraryDependentPackages() throws Exception {
        assertNotVisible(QUERIES_NOTHING, TARGET_NO_API, this::getSharedLibraryDependentPackages);
        assertNotVisible(QUERIES_NOTHING, TARGET_FILTERS, this::getSharedLibraryDependentPackages);
        assertNotVisible(QUERIES_NOTHING, TARGET_SHARED_USER,
                this::getSharedLibraryDependentPackages);

    }

    @Test
    public void queriesPackage_canSeeSharedLibraryDependentPackages() throws Exception {
        assertVisible(QUERIES_PACKAGE, TARGET_NO_API, this::getSharedLibraryDependentPackages);
        assertVisible(QUERIES_PACKAGE, TARGET_SHARED_USER, this::getSharedLibraryDependentPackages);
    }

    @Test
    public void queriesNothingSharedUser_canSeeSharedUserInSharedLibraryDependentPackages()
            throws Exception {
        assertVisible(QUERIES_NOTHING_SHARED_USER, TARGET_SHARED_USER,
                this::getSharedLibraryDependentPackages);
    }

    @Test
    public void queriesNothing_cannotSeePreferredActivityTarget() throws Exception {
        addPreferredActivity();
        try {
            assertNotVisible(QUERIES_NOTHING, TARGET_PREFERRED_ACTIVITY,
                    this::getPreferredActivities);
        } finally {
            clearPreferredActivity();
        }
    }

    @Test
    public void queriesPackage_canSeePreferredActivityTarget() throws Exception {
        addPreferredActivity();
        try {
            assertVisible(QUERIES_PACKAGE, TARGET_PREFERRED_ACTIVITY,
                    this::getPreferredActivities);
        } finally {
            clearPreferredActivity();
        }
    }

    @Test
    public void queriesNothing_setInstallerPackageName_targetIsNoApi_throwsException() {
        final Exception ex = assertThrows(IllegalArgumentException.class,
                () -> setInstallerPackageName(QUERIES_NOTHING, TARGET_NO_API, QUERIES_NOTHING));
        assertThat(ex.getMessage(), containsString(TARGET_NO_API));
    }

    @Test
    public void queriesNothing_setInstallerPackageName_installerIsNoApi_throwsException() {
        final Exception ex = assertThrows(IllegalArgumentException.class,
                () -> setInstallerPackageName(QUERIES_NOTHING, QUERIES_NOTHING, TARGET_NO_API));
        assertThat(ex.getMessage(), containsString(TARGET_NO_API));
    }

    @Test
    public void queriesPackageHasProvider_checkUriPermission_canSeeNoApi() throws Exception {
        final int permissionResult = checkUriPermission(QUERIES_PACKAGE_PROVIDER, TARGET_NO_API);
        assertThat(permissionResult, is(PackageManager.PERMISSION_GRANTED));
    }

    @Test
    public void queriesPackageHasProvider_checkUriPermission_cannotSeeFilters() throws Exception {
        final int permissionResult = checkUriPermission(QUERIES_PACKAGE_PROVIDER, TARGET_FILTERS);
        assertThat(permissionResult, is(PackageManager.PERMISSION_DENIED));
    }

    @Test
    public void queriesPackageHasProvider_grantUriPermission_canSeeNoApi() throws Exception {
        try {
            grantUriPermission(QUERIES_PACKAGE_PROVIDER, TARGET_NO_API);
            assertThat(InstrumentationRegistry.getInstrumentation().getContext()
                    .checkUriPermission(
                            Uri.parse("content://" + QUERIES_PACKAGE_PROVIDER),
                            0 /* pid */,
                            sPm.getPackageUid(TARGET_NO_API, 0 /* flags */),
                            Intent.FLAG_GRANT_READ_URI_PERMISSION),
                    is(PackageManager.PERMISSION_GRANTED));
        } finally {
            revokeUriPermission(QUERIES_PACKAGE_PROVIDER);
        }
    }

    @Test
    public void queriesPackageHasProvider_grantUriPermission_cannotSeeFilters() throws Exception {
        try {
            grantUriPermission(QUERIES_PACKAGE_PROVIDER, TARGET_FILTERS);
            assertThat(InstrumentationRegistry.getInstrumentation().getContext()
                            .checkUriPermission(
                                    Uri.parse("content://" + QUERIES_PACKAGE_PROVIDER),
                                    0 /* pid */,
                                    sPm.getPackageUid(TARGET_FILTERS, 0 /* flags */),
                                    Intent.FLAG_GRANT_READ_URI_PERMISSION),
                    is(PackageManager.PERMISSION_DENIED));
        } finally {
            revokeUriPermission(QUERIES_PACKAGE_PROVIDER);
        }
    }

    @Test
    public void canPackageQuery_queriesActivityAction_canSeeFilters() throws Exception {
        assertThat(sPm.canPackageQuery(QUERIES_ACTIVITY_ACTION, TARGET_FILTERS),
                is(true));
    }

    @Test
    public void canPackageQuery_queriesNothing_cannotSeeFilters() throws Exception {
        assertThat(sPm.canPackageQuery(QUERIES_NOTHING, TARGET_FILTERS),
                is(false));
    }

    @Test
    public void canPackageQuery_withNonexistentPackages() {
        assertThrows(PackageManager.NameNotFoundException.class,
                () -> sPm.canPackageQuery(
                        TEST_NONEXISTENT_PACKAGE_NAME_1, TEST_NONEXISTENT_PACKAGE_NAME_2));
        assertThrows(PackageManager.NameNotFoundException.class,
                () -> sPm.canPackageQuery(
                        QUERIES_NOTHING_PERM, TEST_NONEXISTENT_PACKAGE_NAME_2));
        assertThrows(PackageManager.NameNotFoundException.class,
                () -> sPm.canPackageQuery(
                        TEST_NONEXISTENT_PACKAGE_NAME_1, TARGET_FILTERS));
    }

    @Test
    public void canPackageQuery_callerHasNoPackageVisibility() {
        assertThrows(PackageManager.NameNotFoundException.class,
                () -> canPackageQuery(
                        QUERIES_NOTHING, QUERIES_ACTIVITY_ACTION, TARGET_FILTERS));
        assertThrows(PackageManager.NameNotFoundException.class,
                () -> canPackageQuery(
                        QUERIES_NOTHING_SHARED_USER, QUERIES_PACKAGE, TARGET_SHARED_USER));
    }

    @Test
    public void canPackageQuery_cannotDetectPackageExistence() {
        ensurePackageIsNotInstalled(TARGET_STUB);
        final Exception ex1 = assertThrows(PackageManager.NameNotFoundException.class,
                () -> canPackageQuery(QUERIES_NOTHING, TARGET_STUB, ""));
        final StringWriter stackTrace1 = new StringWriter();
        ex1.printStackTrace(new PrintWriter(stackTrace1));

        ensurePackageIsInstalled(TARGET_STUB, TARGET_STUB_APK);

        final Exception ex2 = assertThrows(PackageManager.NameNotFoundException.class,
                () -> canPackageQuery(QUERIES_NOTHING, TARGET_STUB, ""));
        final StringWriter stackTrace2 = new StringWriter();
        ex1.printStackTrace(new PrintWriter(stackTrace2));

        assertThat(ex1.getMessage(), is(ex2.getMessage()));
        assertThat(stackTrace1.toString(), is(stackTrace2.toString()));
    }

    @Test
    public void checkPackage_queriesNothing_validateFailed() {
        // Using ROOT_UID here to pass the check in #verifyAndGetBypass, this is intended by design.
        assertThrows(SecurityException.class,
                () -> checkPackage(QUERIES_NOTHING, TARGET_FILTERS, ROOT_UID));
    }

    @Test
    public void checkPackage_queriesNothing_targetIsNotExisting_validateFailed() {
        // Using ROOT_UID here to pass the check in #verifyAndGetBypass, this is intended by design.
        assertThrows(SecurityException.class,
                () -> checkPackage(QUERIES_NOTHING, TEST_NONEXISTENT_PACKAGE_NAME_1, ROOT_UID));
    }

    @Test
    public void checkPackage_queriesNothingHasPerm_validateSuccessful() throws Exception {
        // Using ROOT_UID here to pass the check in #verifyAndGetBypass, this is intended by design.
        assertThat(checkPackage(QUERIES_NOTHING_PERM, TARGET_FILTERS, ROOT_UID), is(true));
    }

    @Test
    public void checkPackage_queriesNothingHasPerm_targetIsNotExisting_validateFailed()
            throws Exception {
        // Using ROOT_UID here to pass the check in #verifyAndGetBypass, this is intended by design.
        assertThrows(SecurityException.class,
                () -> checkPackage(QUERIES_NOTHING_PERM, TEST_NONEXISTENT_PACKAGE_NAME_1,
                        ROOT_UID));
    }

    @Test
    public void pendingIntent_getCreatorPackage_queriesPackage_canSeeNoApi()
            throws Exception {
        final PendingIntent pendingIntent = getPendingIntentActivity(TARGET_NO_API);
        assertThat(getPendingIntentCreatorPackage(QUERIES_PACKAGE, pendingIntent),
                is(TARGET_NO_API));
    }

    @Test
    public void pendingIntent_getCreatorPackage_queriesNothing_cannotSeeNoApi()
            throws Exception {
        final PendingIntent pendingIntent = getPendingIntentActivity(TARGET_NO_API);
        assertThat(getPendingIntentCreatorPackage(QUERIES_NOTHING, pendingIntent),
                is(emptyOrNullString()));
    }

    @Test
    public void makeUidVisible_throwsException() throws Exception {
        final int recipientUid = sPm.getPackageUid(
                QUERIES_NOTHING, PackageManager.PackageInfoFlags.of(0));
        final int visibleUid = sPm.getPackageUid(
                TARGET_NO_API, PackageManager.PackageInfoFlags.of(0));
        assertThrows(SecurityException.class,
                () -> sPm.makeUidVisible(recipientUid, visibleUid));
    }

    @Test
    public void makeUidVisible_queriesNothing_canSeeStub() throws Exception {
        ensurePackageIsInstalled(TARGET_STUB, TARGET_STUB_APK);
        try {
            assertNotVisible(QUERIES_NOTHING, TARGET_STUB);

            final int recipientUid = sPm.getPackageUid(
                    QUERIES_NOTHING, PackageManager.PackageInfoFlags.of(0));
            final int visibleUid = sPm.getPackageUid(
                    TARGET_STUB, PackageManager.PackageInfoFlags.of(0));
            SystemUtil.runWithShellPermissionIdentity(
                    () -> sPm.makeUidVisible(recipientUid, visibleUid),
                            Manifest.permission.MAKE_UID_VISIBLE);

            assertVisible(QUERIES_NOTHING, TARGET_STUB);
        } finally {
            ensurePackageIsNotInstalled(TARGET_STUB);
        }
    }

    private void assertNotVisible(String sourcePackageName, String targetPackageName)
            throws Exception {
        if (!sGlobalFeatureEnabled) return;
        try {
            getPackageInfo(sourcePackageName, targetPackageName);
            fail(sourcePackageName + " should not be able to see " + targetPackageName);
        } catch (PackageManager.NameNotFoundException ignored) {
        }
    }

    private void assertServiceVisible(String sourcePackageName, String targetPackageName)
            throws Exception {
        if (!sGlobalFeatureEnabled) return;
        assertTrue(bindService(sourcePackageName, targetPackageName));
    }

    private void assertServiceNotVisible(String sourcePackageName, String targetPackageName)
            throws Exception {
        if (!sGlobalFeatureEnabled) return;
        assertFalse(bindService(sourcePackageName, targetPackageName));
    }

    interface ThrowingBiFunction<T, U, R> {
        R apply(T arg1, U arg2) throws Exception;
    }

    interface ThrowingFunction<T, R> {
        R apply(T arg1) throws Exception;
    }

    private void assertNotQueryable(String sourcePackageName, String targetPackageName,
            String intentAction, ThrowingBiFunction<String, Intent, String[]> commandMethod)
            throws Exception {
        if (!sGlobalFeatureEnabled) return;
        Intent intent = new Intent(intentAction);
        String[] queryablePackageNames = commandMethod.apply(sourcePackageName, intent);
        for (String packageName : queryablePackageNames) {
            if (packageName.contentEquals(targetPackageName)) {
                fail(sourcePackageName + " should not be able to query " + targetPackageName +
                        " via " + intentAction);
            }
        }
    }

    private void assertQueryable(String sourcePackageName, String targetPackageName,
            String intentAction, ThrowingBiFunction<String, Intent, String[]> commandMethod)
            throws Exception {
        if (!sGlobalFeatureEnabled) return;
        Intent intent = new Intent(intentAction);
        String[] queryablePackageNames = commandMethod.apply(sourcePackageName, intent);
        for (String packageName : queryablePackageNames) {
            if (packageName.contentEquals(targetPackageName)) {
                return;
            }
        }
        fail(sourcePackageName + " should be able to query " + targetPackageName + " via "
                + intentAction);
    }

    private void assertVisible(String sourcePackageName, String targetPackageName,
            ThrowingFunction<String, String[]> commandMethod) throws Exception {
        if (!sGlobalFeatureEnabled) return;
        final String[] packageNames = commandMethod.apply(sourcePackageName);
        assertThat("The list of package names should not be null",
                packageNames, notNullValue());
        assertThat(sourcePackageName + " should be able to see " + targetPackageName,
                packageNames, hasItemInArray(targetPackageName));
    }

    private void assertNotVisible(String sourcePackageName, String targetPackageName,
            ThrowingFunction<String, String[]> commandMethod) throws Exception {
        if (!sGlobalFeatureEnabled) return;
        final String[] packageNames = commandMethod.apply(sourcePackageName);
        assertThat("The list of package names should not be null",
                packageNames, notNullValue());
        assertThat(sourcePackageName + " should not be able to see " + targetPackageName,
                packageNames, not(hasItemInArray(targetPackageName)));
    }

    private void assertVisible(String sourcePackageName, String targetPackageName,
            ThrowingBiFunction<String, String, String[]> commandMethod) throws Exception {
        if (!sGlobalFeatureEnabled) return;
        final String[] packageNames = commandMethod.apply(sourcePackageName, targetPackageName);
        assertThat(sourcePackageName + " should be able to see " + targetPackageName,
                packageNames, hasItemInArray(targetPackageName));
    }

    private void assertNotVisible(String sourcePackageName, String targetPackageName,
            ThrowingBiFunction<String, String, String[]> commandMethod) throws Exception {
        if (!sGlobalFeatureEnabled) return;
        final String[] packageNames = commandMethod.apply(sourcePackageName, targetPackageName);
        assertThat(sourcePackageName + " should not be able to see " + targetPackageName,
                packageNames, not(hasItemInArray(targetPackageName)));
    }

    private void assertBroadcastSuspendedVisible(String sourcePackageName,
            List<String> expectedVisiblePackages, List<String> packagesToSuspend)
            throws Exception {
        final Bundle extras = new Bundle();
        extras.putStringArray(EXTRA_PACKAGES, packagesToSuspend.toArray(new String[] {}));
        final Result result = sendCommand(sourcePackageName, /* targetPackageName */ null,
                /* targetUid */ INVALID_UID, extras, Constants.ACTION_AWAIT_PACKAGES_SUSPENDED,
                /* waitForReady */ true);
        try {
            setPackagesSuspended(true, packagesToSuspend);
            final String[] suspendedPackages = result.await().getStringArray(EXTRA_PACKAGES);
            assertThat(suspendedPackages, arrayContainingInAnyOrder(
                    expectedVisiblePackages.toArray()));
        } finally {
            setPackagesSuspended(false, packagesToSuspend);
        }
    }

    private String[] getInstalledAccessibilityServices (String sourcePackageName)
            throws Exception {
        final Bundle response = sendCommandBlocking(sourcePackageName, null /*targetPackageName*/,
                null /*queryIntent*/, ACTION_GET_INSTALLED_ACCESSIBILITYSERVICES_PACKAGES);
        return response.getStringArray(Intent.EXTRA_RETURN_RESULT);
    }

    private PackageInfo getPackageInfo(String sourcePackageName, String targetPackageName)
            throws Exception {
        Bundle response = sendCommandBlocking(sourcePackageName, targetPackageName,
                null /*queryIntent*/, ACTION_GET_PACKAGE_INFO);
        return response.getParcelable(Intent.EXTRA_RETURN_RESULT);
    }

    private String[] getPackagesForUid(String sourcePackageName, int targetUid)
            throws Exception {
        final Bundle response = sendCommandBlocking(sourcePackageName, targetUid,
                /* intentExtra */ null, ACTION_GET_PACKAGES_FOR_UID);
        return response.getStringArray(Intent.EXTRA_RETURN_RESULT);
    }

    private String getNameForUid(String sourcePackageName, int targetUid) throws Exception {
        final Bundle response = sendCommandBlocking(sourcePackageName, targetUid,
                /* intentExtra */ null, ACTION_GET_NAME_FOR_UID);
        return response.getString(Intent.EXTRA_RETURN_RESULT);
    }

    private String[] getNamesForUids(String sourcePackageName, int targetUid) throws Exception {
        final Bundle response = sendCommandBlocking(sourcePackageName, targetUid,
                /* intentExtra */ null, ACTION_GET_NAMES_FOR_UIDS);
        return response.getStringArray(Intent.EXTRA_RETURN_RESULT);
    }

    private int checkSignatures(String sourcePackageName, int targetUid) throws Exception {
        final Bundle response = sendCommandBlocking(sourcePackageName, targetUid,
                /* intentExtra */ null, ACTION_CHECK_SIGNATURES);
        return response.getInt(Intent.EXTRA_RETURN_RESULT);
    }

    private boolean hasSigningCertificate(String sourcePackageName, int targetUid, byte[] cert)
            throws Exception {
        final Bundle extra = new Bundle();
        extra.putByteArray(EXTRA_CERT, cert);
        final Bundle response = sendCommandBlocking(sourcePackageName, targetUid, extra,
                ACTION_HAS_SIGNING_CERTIFICATE);
        return response.getBoolean(Intent.EXTRA_RETURN_RESULT);
    }

    private List<Certificate> convertSignaturesToCertificates(Signature[] signatures)
            throws Exception {
        final CertificateFactory cf = CertificateFactory.getInstance("X.509");
        ArrayList<Certificate> certs = new ArrayList<>(signatures.length);
        for (Signature signature : signatures) {
            final InputStream is = new ByteArrayInputStream(signature.toByteArray());
            final X509Certificate cert = (X509Certificate) cf.generateCertificate(is);
            certs.add(cert);
        }
        return certs;
    }

    private boolean checkPackage(String sourcePackageName, String targetPackageName, int targetUid)
            throws Exception {
        final Bundle extra = new Bundle();
        extra.putInt(EXTRA_UID, targetUid);
        final Bundle response = sendCommandBlocking(sourcePackageName, targetPackageName, extra,
                ACTION_CHECK_PACKAGE);
        return response.getBoolean(Intent.EXTRA_RETURN_RESULT);
    }

    private PackageInfo startForResult(String sourcePackageName, String targetPackageName)
            throws Exception {
        Bundle response = sendCommandBlocking(sourcePackageName, targetPackageName,
                null /*queryIntent*/, ACTION_START_FOR_RESULT);
        return response.getParcelable(Intent.EXTRA_RETURN_RESULT);
    }

    private PackageInfo startSenderForResult(String sourcePackageName, String targetPackageName)
            throws Exception {
        PendingIntent pendingIntent = PendingIntent.getActivity(
                InstrumentationRegistry.getInstrumentation().getContext(), 100,
                new Intent("android.appenumeration.cts.action.SEND_RESULT").setComponent(
                        new ComponentName(targetPackageName,
                                "android.appenumeration.cts.TestActivity")),
                PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE);

        Bundle response = sendCommandBlocking(sourcePackageName, targetPackageName,
                pendingIntent /*queryIntent*/, Constants.ACTION_START_SENDER_FOR_RESULT);
        return response.getParcelable(Intent.EXTRA_RETURN_RESULT);
    }


    private String[] queryIntentActivities(String sourcePackageName, Intent queryIntent)
            throws Exception {
        Bundle response =
                sendCommandBlocking(sourcePackageName, null, queryIntent, ACTION_QUERY_ACTIVITIES);
        return response.getStringArray(Intent.EXTRA_RETURN_RESULT);
    }

    private String[] queryIntentServices(String sourcePackageName, Intent queryIntent)
            throws Exception {
        Bundle response = sendCommandBlocking(sourcePackageName, null, queryIntent,
                ACTION_QUERY_SERVICES);
        return response.getStringArray(Intent.EXTRA_RETURN_RESULT);
    }

    private String[] queryIntentProviders(String sourcePackageName, Intent queryIntent)
            throws Exception {
        Bundle response = sendCommandBlocking(sourcePackageName, null, queryIntent,
                ACTION_QUERY_PROVIDERS);
        return response.getStringArray(Intent.EXTRA_RETURN_RESULT);
    }

    private String[] getInstalledPackages(String sourcePackageNames, int flags) throws Exception {
        Bundle response = sendCommandBlocking(sourcePackageNames, null,
                new Intent().putExtra(EXTRA_FLAGS, flags), ACTION_GET_INSTALLED_PACKAGES);
        return response.getStringArray(Intent.EXTRA_RETURN_RESULT);
    }

    private void startExplicitIntentViaComponent(String sourcePackage, String targetPackage)
            throws Exception {
        sendCommandBlocking(sourcePackage, targetPackage,
                new Intent().setComponent(new ComponentName(targetPackage,
                        ACTIVITY_CLASS_DUMMY_ACTIVITY)),
                ACTION_START_DIRECTLY);
    }
    private void startExplicitIntentViaPackageName(String sourcePackage, String targetPackage)
            throws Exception {
        sendCommandBlocking(sourcePackage, targetPackage,
                new Intent().setPackage(targetPackage),
                ACTION_START_DIRECTLY);
    }

    private void startImplicitIntent(String sourcePackage) throws Exception {
        sendCommandBlocking(sourcePackage, TARGET_FILTERS, new Intent(ACTION_MANIFEST_ACTIVITY),
                ACTION_START_DIRECTLY);
    }

    private boolean bindService(String sourcePackageName, String targetPackageName)
            throws Exception {
        final Bundle response = sendCommandBlocking(sourcePackageName, targetPackageName,
                /* intentExtra */ null, ACTION_BIND_SERVICE);
        return response.getBoolean(Intent.EXTRA_RETURN_RESULT);
    }

    private String[] getSyncAdapterTypes(String sourcePackageName) throws Exception {
        final Bundle response = sendCommandBlocking(sourcePackageName, /* targetPackageName */ null,
                /* intentExtra */ null, ACTION_GET_SYNCADAPTER_TYPES);
        final List<Parcelable> parcelables = response.getParcelableArrayList(
                Intent.EXTRA_RETURN_RESULT);
        return parcelables.stream()
                .map(parcelable -> ((SyncAdapterType) parcelable).getPackageName())
                .distinct()
                .toArray(String[]::new);
    }

    private String[] getInstalledAppWidgetProviders(String sourcePackageName) throws Exception {
        final Bundle response = sendCommandBlocking(sourcePackageName, /* targetPackageName */ null,
                /* intentExtra */ null, ACTION_GET_INSTALLED_APPWIDGET_PROVIDERS);
        final List<Parcelable> parcelables = response.getParcelableArrayList(
                Intent.EXTRA_RETURN_RESULT);
        return parcelables.stream()
                .map(parcelable -> ((AppWidgetProviderInfo) parcelable).provider.getPackageName())
                .distinct()
                .toArray(String[]::new);
    }

    private String[] getSyncAdapterPackagesForAuthorityAsUser(String sourcePackageName,
            String targetPackageName) throws Exception {
        final Bundle extraData = new Bundle();
        extraData.putString(EXTRA_AUTHORITY, targetPackageName + ".authority");
        extraData.putInt(Intent.EXTRA_USER, Process.myUserHandle().getIdentifier());
        final Bundle response = sendCommandBlocking(sourcePackageName, /* targetPackageName */ null,
                extraData, ACTION_GET_SYNCADAPTER_PACKAGES_FOR_AUTHORITY);
        return response.getStringArray(Intent.EXTRA_PACKAGES);
    }

    private boolean requestSyncAndAwaitStatus(String sourcePackageName, Account account,
            String targetPackageName) throws Exception {
        final Bundle extraData = new Bundle();
        extraData.putParcelable(EXTRA_ACCOUNT, account);
        extraData.putString(EXTRA_AUTHORITY, targetPackageName + ".authority");
        final Bundle response = sendCommandBlocking(sourcePackageName, /* targetPackageName */ null,
                extraData, ACTION_REQUEST_SYNC_AND_AWAIT_STATUS);
        return response.getBoolean(Intent.EXTRA_RETURN_RESULT);
    }

    private void setPackagesSuspended(boolean suspend, List<String> packages) {
        setPackagesSuspendedWithLauncherExtras(suspend, packages, /* persistableBundle */ false);
    }

    private void setPackagesSuspendedWithLauncherExtras(boolean suspend, List<String> packages,
            boolean extras) {
        final StringBuilder cmd = new StringBuilder("pm ");
        if (suspend) {
            cmd.append("suspend");
        } else {
            cmd.append("unsuspend");
        }
        cmd.append(" --user cur");
        if (extras) {
            cmd.append(" --les foo bar");
        }
        packages.stream().forEach(p -> cmd.append(" ").append(p));
        runShellCommand(cmd.toString());
    }

    private boolean launcherAppsIsActivityEnabled(String sourcePackageName,
            ComponentName componentName) throws Exception {
        final Bundle extraData = new Bundle();
        extraData.putString(Intent.EXTRA_COMPONENT_NAME, componentName.flattenToString());
        final Bundle response = sendCommandBlocking(sourcePackageName, /* targetPackageName */ null,
                extraData, ACTION_LAUNCHER_APPS_IS_ACTIVITY_ENABLED);
        return response.getBoolean(Intent.EXTRA_RETURN_RESULT);
    }

    private Bundle launcherAppsGetSuspendedPackageLauncherExtras(String sourcePackageName,
            String targetPackageName) throws Exception {
        final Bundle response = sendCommandBlocking(sourcePackageName, targetPackageName,
                /* extraData */ null, ACTION_LAUNCHER_APPS_GET_SUSPENDED_PACKAGE_LAUNCHER_EXTRAS);
        return response.getBundle(Intent.EXTRA_RETURN_RESULT);
    }

    private String[] getSharedLibraryDependentPackages(String sourcePackageName) throws Exception {
        final Bundle extraData = new Bundle();
        final Bundle response = sendCommandBlocking(sourcePackageName, TEST_SHARED_LIB_NAME,
                extraData, ACTION_GET_SHAREDLIBRARY_DEPENDENT_PACKAGES);
        return response.getStringArray(Intent.EXTRA_PACKAGES);
    }

    private String[] getPreferredActivities(String sourcePackageName) throws Exception {
        final Bundle extraData = new Bundle();
        final Bundle response = sendCommandBlocking(sourcePackageName, null /* targetPackageName */,
                extraData, ACTION_GET_PREFERRED_ACTIVITIES);
        return response.getStringArray(Intent.EXTRA_PACKAGES);
    }

    private void setInstallerPackageName(String sourcePackageName, String targetPackageName,
            String installerPackageName) throws Exception {
        final Bundle extraData = new Bundle();
        extraData.putString(Intent.EXTRA_INSTALLER_PACKAGE_NAME, installerPackageName);
        sendCommandBlocking(sourcePackageName, targetPackageName,
                extraData, ACTION_SET_INSTALLER_PACKAGE_NAME);
    }

    private void setDistractingPackageRestrictions(String[] packagesToRestrict,
            int distractionFlags) throws Exception {
        final String[] failed = SystemUtil.callWithShellPermissionIdentity(
                () -> sPm.setDistractingPackageRestrictions(packagesToRestrict, distractionFlags));
        assertThat(failed, emptyArray());
    }

    private boolean shouldHideFromSuggestions(String sourcePackageName, String targetPackageName)
            throws Exception {
        final Bundle extraData = new Bundle();
        extraData.putInt(Intent.EXTRA_USER, Process.myUserHandle().getIdentifier());
        final Bundle response = sendCommandBlocking(sourcePackageName, targetPackageName, extraData,
                ACTION_LAUNCHER_APPS_SHOULD_HIDE_FROM_SUGGESTIONS);
        return response.getBoolean(Intent.EXTRA_RETURN_RESULT);
    }

    private int checkUriPermission(String sourcePackageName, String targetPackageName)
            throws Exception {
        final int targetUid = sPm.getPackageUid(targetPackageName, /* flags */ 0);
        final Bundle extraData = new Bundle();
        extraData.putString(EXTRA_AUTHORITY, sourcePackageName);
        final Result result = sendCommand(sourcePackageName, targetPackageName, targetUid,
                extraData, ACTION_CHECK_URI_PERMISSION, /* waitForReady */ false);
        final Bundle response = result.await();
        return response.getInt(Intent.EXTRA_RETURN_RESULT);
    }

    private void grantUriPermission(String providerPackageName, String targetPackageName)
            throws Exception {
        final Bundle extraData = new Bundle();
        extraData.putString(EXTRA_AUTHORITY, providerPackageName);
        sendCommandBlocking(providerPackageName, targetPackageName, extraData,
                ACTION_GRANT_URI_PERMISSION);
    }

    private void revokeUriPermission(String providerPackageName) throws Exception {
        final Bundle extraData = new Bundle();
        extraData.putString(EXTRA_AUTHORITY, providerPackageName);
        sendCommandBlocking(providerPackageName, null /* targetPackageName */, extraData,
                ACTION_REVOKE_URI_PERMISSION);
    }

    private boolean canPackageQuery(String callerPackageName, String sourcePackageName,
            String targetPackageName) throws Exception {
        final Bundle extraData = new Bundle();
        extraData.putString(Intent.EXTRA_PACKAGE_NAME, targetPackageName);
        final Bundle response = sendCommandBlocking(callerPackageName, sourcePackageName,
                extraData, ACTION_CAN_PACKAGE_QUERY);
        return response.getBoolean(Intent.EXTRA_RETURN_RESULT);
    }

    private PendingIntent getPendingIntentActivity(String sourcePackageName) throws Exception  {
        final Bundle bundle = sendCommandBlocking(sourcePackageName, null /* targetPackageName */,
                null /* intentExtra */, ACTION_PENDING_INTENT_GET_ACTIVITY);
        return bundle.getParcelable(EXTRA_PENDING_INTENT);
    }

    private String getPendingIntentCreatorPackage(String sourcePackageName,
            PendingIntent pendingIntent) throws Exception  {
        final Bundle bundle = sendCommandBlocking(sourcePackageName, null /* targetPackageName */,
                pendingIntent, ACTION_PENDING_INTENT_GET_CREATOR_PACKAGE);
        return bundle.getString(Intent.EXTRA_PACKAGE_NAME);
    }

    interface Result {
        Bundle await() throws Exception;
    }

    private Result sendCommand(String sourcePackageName, @Nullable String targetPackageName,
            int targetUid, @Nullable Parcelable intentExtra, String action, boolean waitForReady)
            throws Exception {
        final Intent intent = new Intent(action)
                .setComponent(new ComponentName(sourcePackageName, ACTIVITY_CLASS_TEST))
                // data uri unique to each activity start to ensure actual launch and not just
                // redisplay
                .setData(Uri.parse("test://" + UUID.randomUUID().toString()))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NEW_DOCUMENT);
        if (targetPackageName != null) {
            intent.putExtra(Intent.EXTRA_PACKAGE_NAME, targetPackageName);
        }
        if (targetUid > INVALID_UID) {
            intent.putExtra(Intent.EXTRA_UID, targetUid);
        }
        if (intentExtra != null) {
            if (intentExtra instanceof Intent) {
                intent.putExtra(Intent.EXTRA_INTENT, intentExtra);
            } else if (intentExtra instanceof PendingIntent) {
                intent.putExtra(EXTRA_PENDING_INTENT, intentExtra);
            } else if (intentExtra instanceof Bundle) {
                intent.putExtra(EXTRA_DATA, intentExtra);
            }
        }

        final ConditionVariable latch = new ConditionVariable();
        final AtomicReference<Bundle> resultReference = new AtomicReference<>();
        final RemoteCallback callback = new RemoteCallback(
                bundle -> {
                    resultReference.set(bundle);
                    latch.open();
                },
                sResponseHandler);
        intent.putExtra(EXTRA_REMOTE_CALLBACK, callback);
        if (waitForReady) {
            AmUtils.waitForBroadcastIdle();
            startAndWaitForCommandReady(intent);
        } else {
            final ActivityOptions options = ActivityOptions.makeBasic();
            options.setLaunchWindowingMode(WINDOWING_MODE_FULLSCREEN);
            InstrumentationRegistry.getInstrumentation().getContext().startActivity(
                    intent, options.toBundle());
        }
        return () -> {
            if (!latch.block(TimeUnit.SECONDS.toMillis(10))) {
                throw new TimeoutException(
                        "Latch timed out while awiating a response from " + sourcePackageName);
            }
            final Bundle bundle = resultReference.get();
            if (bundle != null && bundle.containsKey(EXTRA_ERROR)) {
                throw (Exception) Objects.requireNonNull(bundle.getSerializable(EXTRA_ERROR));
            }
            return bundle;
        };
    }

    private void startAndWaitForCommandReady(Intent intent) throws Exception {
        final ConditionVariable latchForReady = new ConditionVariable();
        final RemoteCallback readyCallback = new RemoteCallback(bundle -> latchForReady.open(),
                sResponseHandler);
        intent.putExtra(EXTRA_REMOTE_READY_CALLBACK, readyCallback);
        InstrumentationRegistry.getInstrumentation().getContext().startActivity(intent);
        if (!latchForReady.block(TimeUnit.SECONDS.toMillis(10))) {
            throw new TimeoutException(
                    "Latch timed out while awiating a response from command " + intent.getAction());
        }
    }

    private Bundle sendCommandBlocking(String sourcePackageName, @Nullable String targetPackageName,
            @Nullable Parcelable intentExtra, String action)
            throws Exception {
        final Result result = sendCommand(sourcePackageName, targetPackageName,
                /* targetUid */ INVALID_UID, intentExtra, action, /* waitForReady */ false);
        return result.await();
    }

    private Bundle sendCommandBlocking(String sourcePackageName, int targetUid,
            @Nullable Parcelable intentExtra, String action)
            throws Exception {
        final Result result = sendCommand(sourcePackageName, /* targetPackageName */ null,
                targetUid, intentExtra, action, /* waitForReady */ false);
        return result.await();
    }

    private Result sendCommandAndWaitForLauncherAppsCallback(String sourcePackageName,
            int expectedEventCode) throws Exception {
        final Bundle extra = new Bundle();
        extra.putInt(EXTRA_FLAGS, expectedEventCode);
        final Result result = sendCommand(sourcePackageName, /* targetPackageName */ null,
                /* targetUid */ INVALID_UID, extra, ACTION_AWAIT_LAUNCHER_APPS_CALLBACK,
                /* waitForReady */ true);
        return result;
    }

    private void ensurePackageIsInstalled(String packageName, String apkPath) {
        runShellCommand("pm install -R " + apkPath);
        PackageInfo info = null;
        try {
            info = sPm.getPackageInfo(packageName, /* flags */ 0);
        } catch (PackageManager.NameNotFoundException e) {
            // Ignore
        }
        Assert.assertNotNull(packageName + " should be installed", info);
    }

    private void ensurePackageIsNotInstalled(String packageName) {
        runShellCommand("pm uninstall " + packageName);
        PackageInfo info = null;
        try {
            info = sPm.getPackageInfo(packageName, /* flags */ 0);
        } catch (PackageManager.NameNotFoundException e) {
            // Expected
        }
        Assert.assertNull(packageName + " shouldn't be installed", info);
    }

    private void addPreferredActivity() {
        final IntentFilter filter = new IntentFilter(
                "android.intent.action.APP_ENUMERATION_PREFERRED_ACTIVITY");
        final ComponentName[] candidates = {new ComponentName(TARGET_PREFERRED_ACTIVITY,
                ACTIVITY_CLASS_DUMMY_ACTIVITY)};
        SystemUtil.runWithShellPermissionIdentity(() -> {
            sPm.addPreferredActivity(filter, IntentFilter.MATCH_ADJUSTMENT_NORMAL,
                    candidates, candidates[0]);
        }, SET_PREFERRED_APPLICATIONS);
    }

    private void clearPreferredActivity() {
        SystemUtil.runWithShellPermissionIdentity(() -> {
            sPm.clearPackagePreferredActivities(TARGET_PREFERRED_ACTIVITY);
        }, SET_PREFERRED_APPLICATIONS);
    }
}
