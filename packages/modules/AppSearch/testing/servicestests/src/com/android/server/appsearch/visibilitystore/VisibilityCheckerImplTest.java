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

package com.android.server.appsearch.visibilitystore;

import static android.Manifest.permission.READ_ASSISTANT_APP_SEARCH_DATA;
import static android.Manifest.permission.READ_CALENDAR;
import static android.Manifest.permission.READ_CONTACTS;
import static android.Manifest.permission.READ_EXTERNAL_STORAGE;
import static android.Manifest.permission.READ_GLOBAL_APP_SEARCH_DATA;
import static android.Manifest.permission.READ_HOME_APP_SEARCH_DATA;
import static android.Manifest.permission.READ_SMS;
import static android.content.pm.PackageManager.PERMISSION_DENIED;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import android.annotation.NonNull;
import android.app.UiAutomation;
import android.app.appsearch.PackageIdentifier;
import android.app.appsearch.SetSchemaRequest;
import android.app.appsearch.VisibilityDocument;
import android.content.AttributionSource;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.pm.PackageManager;
import android.os.UserHandle;
import android.util.ArrayMap;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.server.appsearch.external.localstorage.AppSearchImpl;
import com.android.server.appsearch.external.localstorage.OptimizeStrategy;
import com.android.server.appsearch.external.localstorage.UnlimitedLimitConfig;
import com.android.server.appsearch.external.localstorage.util.PrefixUtil;
import com.android.server.appsearch.external.localstorage.visibilitystore.VisibilityStore;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mockito;

import java.util.Map;

public class VisibilityCheckerImplTest {
    /**
     * Always trigger optimize in this class. OptimizeStrategy will be tested in its own test class.
     */
    private static final OptimizeStrategy ALWAYS_OPTIMIZE = optimizeInfo -> true;

    @Rule public TemporaryFolder mTemporaryFolder = new TemporaryFolder();
    private final Map<UserHandle, PackageManager> mMockPackageManagers = new ArrayMap<>();
    private Context mContext;
    private VisibilityCheckerImpl mVisibilityChecker;
    private VisibilityStore mVisibilityStore;
    private AttributionSource mAttributionSource;
    private UiAutomation mUiAutomation;

    @Before
    public void setUp() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        mAttributionSource = context.getAttributionSource();
        mContext = new ContextWrapper(context) {
            @Override
            public Context createContextAsUser(UserHandle user, int flags) {
                return new ContextWrapper(super.createContextAsUser(user, flags)) {
                    @Override
                    public PackageManager getPackageManager() {
                        return getMockPackageManager(user);
                    }
                };
            }

            @Override
            public PackageManager getPackageManager() {
                return createContextAsUser(getUser(), /*flags=*/ 0).getPackageManager();
            }
        };
        mUiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();

        mVisibilityChecker = new VisibilityCheckerImpl(mContext);
        // Give ourselves global query permissions
        AppSearchImpl appSearchImpl = AppSearchImpl.create(
                mTemporaryFolder.newFolder(),
                new UnlimitedLimitConfig(),
                /*initStatsBuilder=*/ null,
                ALWAYS_OPTIMIZE,
                mVisibilityChecker);
        mVisibilityStore = new VisibilityStore(appSearchImpl);
    }

    @Test
    public void testDoesCallerHaveSystemAccess() {
        PackageManager mockPackageManager = getMockPackageManager(mContext.getUser());
        when(mockPackageManager
                .checkPermission(READ_GLOBAL_APP_SEARCH_DATA, mContext.getPackageName()))
                .thenReturn(PERMISSION_GRANTED);
        assertThat(mVisibilityChecker.doesCallerHaveSystemAccess(mContext.getPackageName()))
                .isTrue();

        when(mockPackageManager
                .checkPermission(READ_GLOBAL_APP_SEARCH_DATA, mContext.getPackageName()))
                .thenReturn(PERMISSION_DENIED);
        assertThat(mVisibilityChecker.doesCallerHaveSystemAccess(mContext.getPackageName()))
                .isFalse();
    }

    @Test
    public void testSetVisibility_displayedBySystem() throws Exception {
        // Create two VisibilityDocument that are not displayed by system.
        VisibilityDocument
                visibilityDocument1 = new VisibilityDocument.Builder(/*id=*/"prefix/Schema1")
                .setNotDisplayedBySystem(true).build();
        VisibilityDocument
                visibilityDocument2 = new VisibilityDocument.Builder(/*id=*/"prefix/Schema2")
                .setNotDisplayedBySystem(true).build();
        mVisibilityStore.setVisibility(
                ImmutableList.of(visibilityDocument1, visibilityDocument2));

        assertThat(mVisibilityChecker.isSchemaSearchableByCaller(
                new FrameworkCallerAccess(mAttributionSource,
                        /*callerHasSystemAccess=*/ true),
                "package",
                "prefix/Schema1",
                mVisibilityStore))
                .isFalse();
        assertThat(mVisibilityChecker.isSchemaSearchableByCaller(
                new FrameworkCallerAccess(mAttributionSource,
                        /*callerHasSystemAccess=*/ true),
                "package",
                "prefix/Schema2",
                mVisibilityStore))
                .isFalse();

        // Rewrite Visibility Document 1 to let it accessible to the system.
        visibilityDocument1 = new VisibilityDocument.Builder(/*id=*/"prefix/Schema1").build();
        mVisibilityStore.setVisibility(
                ImmutableList.of(visibilityDocument1, visibilityDocument2));
        assertThat(mVisibilityChecker.isSchemaSearchableByCaller(
                new FrameworkCallerAccess(mAttributionSource,
                        /*callerHasSystemAccess=*/ true),
                "package",
                "prefix/Schema1",
                mVisibilityStore))
                .isTrue();
        assertThat(mVisibilityChecker.isSchemaSearchableByCaller(
                new FrameworkCallerAccess(mAttributionSource,
                        /*callerHasSystemAccess=*/ true),
                "package",
                "prefix/Schema2",
                mVisibilityStore))
                .isFalse();
    }

    @Test
    public void testSetVisibility_visibleToPackages() throws Exception {
        // Values for a "foo" client
        String packageNameFoo = "packageFoo";
        byte[] sha256CertFoo = new byte[32];
        int uidFoo = 1;

        // Values for a "bar" client
        String packageNameBar = "packageBar";
        byte[] sha256CertBar = new byte[32];
        int uidBar = 2;

        // Can't be the same value as uidFoo nor uidBar
        int uidNotFooOrBar = 3;

        // Grant package access
        VisibilityDocument
                visibilityDocument1 = new VisibilityDocument.Builder(/*id=*/"prefix/SchemaFoo")
                .addVisibleToPackage(new PackageIdentifier(packageNameFoo, sha256CertFoo)).build();
        VisibilityDocument
                visibilityDocument2 = new VisibilityDocument.Builder(/*id=*/"prefix/SchemaBar")
                .addVisibleToPackage(new PackageIdentifier(packageNameBar, sha256CertBar)).build();
        mVisibilityStore.setVisibility(
                ImmutableList.of(visibilityDocument1, visibilityDocument2));

        // Should fail if PackageManager doesn't see that it has the proper certificate
        PackageManager mockPackageManager = getMockPackageManager(mContext.getUser());
        when(mockPackageManager.getPackageUid(eq(packageNameFoo), /*flags=*/ anyInt()))
                .thenReturn(uidFoo);
        when(mockPackageManager.hasSigningCertificate(
                packageNameFoo, sha256CertFoo, PackageManager.CERT_INPUT_SHA256))
                .thenReturn(false);
        assertThat(mVisibilityChecker.isSchemaSearchableByCaller(
                new FrameworkCallerAccess(new AttributionSource.Builder(uidFoo)
                        .setPackageName(packageNameFoo).build(),
                        /*callerHasSystemAccess=*/ false),
                "package",
                "prefix/SchemaFoo",
                mVisibilityStore))
                .isFalse();

        // Should fail if PackageManager doesn't think the package belongs to the uid
        when(mockPackageManager.getPackageUid(eq(packageNameFoo), /*flags=*/ anyInt()))
                .thenReturn(uidNotFooOrBar);
        when(mockPackageManager.hasSigningCertificate(
                packageNameFoo, sha256CertFoo, PackageManager.CERT_INPUT_SHA256))
                .thenReturn(true);
        assertThat(mVisibilityChecker.isSchemaSearchableByCaller(
                new FrameworkCallerAccess(new AttributionSource.Builder(uidFoo)
                        .setPackageName(packageNameFoo).build(),
                        /*callerHasSystemAccess=*/ false),
                "package",
                "prefix/SchemaFoo",
                mVisibilityStore))
                .isFalse();

        // But if uid and certificate match, then we should have access
        when(mockPackageManager.getPackageUid(eq(packageNameFoo), /*flags=*/ anyInt()))
                .thenReturn(uidFoo);
        when(mockPackageManager.hasSigningCertificate(
                packageNameFoo, sha256CertFoo, PackageManager.CERT_INPUT_SHA256))
                .thenReturn(true);
        assertThat(mVisibilityChecker.isSchemaSearchableByCaller(
                new FrameworkCallerAccess(new AttributionSource.Builder(uidFoo)
                        .setPackageName(packageNameFoo).build(),
                        /*callerHasSystemAccess=*/ false),
                "package",
                "prefix/SchemaFoo",
                mVisibilityStore))
                .isTrue();

        when(mockPackageManager.getPackageUid(eq(packageNameBar), /*flags=*/ anyInt()))
                .thenReturn(uidBar);
        when(mockPackageManager.hasSigningCertificate(
                packageNameBar, sha256CertBar, PackageManager.CERT_INPUT_SHA256))
                .thenReturn(true);
        assertThat(mVisibilityChecker.isSchemaSearchableByCaller(
                new FrameworkCallerAccess(new AttributionSource.Builder(uidBar)
                        .setPackageName(packageNameBar).build(),
                        /*callerHasSystemAccess=*/ false),
                "package",
                "prefix/SchemaBar",
                mVisibilityStore))
                .isTrue();

        // Save default document and, then we shouldn't have access
        visibilityDocument1 = new VisibilityDocument.Builder(/*id=*/"prefix/SchemaFoo").build();
        visibilityDocument2 = new VisibilityDocument.Builder(/*id=*/"prefix/SchemaBar").build();
        mVisibilityStore.setVisibility(ImmutableList.of(visibilityDocument1, visibilityDocument2));
        assertThat(mVisibilityChecker.isSchemaSearchableByCaller(
                new FrameworkCallerAccess(new AttributionSource.Builder(uidFoo)
                        .setPackageName(packageNameFoo).build(),
                        /*callerHasSystemAccess=*/ false),
                "package",
                "prefix/SchemaFoo",
                mVisibilityStore))
                .isFalse();
        assertThat(mVisibilityChecker.isSchemaSearchableByCaller(
                new FrameworkCallerAccess(new AttributionSource.Builder(uidBar)
                        .setPackageName(packageNameBar).build(),
                        /*callerHasSystemAccess=*/ false),
                "package",
                "prefix/SchemaBar",
                mVisibilityStore))
                .isFalse();
    }

    @Test
    public void testIsSchemaSearchableByCaller_packageAccessibilityHandlesNameNotFoundException()
            throws Exception {
        // Values for a "foo" client
        String packageNameFoo = "packageFoo";
        byte[] sha256CertFoo = new byte[] {10};

        // Pretend we can't find the Foo package.
        PackageManager mockPackageManager = getMockPackageManager(mContext.getUser());
        when(mockPackageManager.getPackageUid(eq(packageNameFoo), /*flags=*/ anyInt()))
                .thenThrow(new PackageManager.NameNotFoundException());

        VisibilityDocument
                visibilityDocument1 = new VisibilityDocument.Builder(/*id=*/"prefix/SchemaFoo")
                .addVisibleToPackage(new PackageIdentifier(packageNameFoo, sha256CertFoo)).build();
        // Grant package access
        mVisibilityStore.setVisibility(ImmutableList.of(visibilityDocument1));

        // If we can't verify the Foo package that has access, assume it doesn't have access.
        assertThat(mVisibilityChecker.isSchemaSearchableByCaller(
                new FrameworkCallerAccess(mAttributionSource,
                        /*callerHasSystemAccess=*/ false),
                "package",
                "prefix/SchemaFoo",
                mVisibilityStore))
                .isFalse();
    }

    @Test
    public void testEmptyPrefix() throws Exception {
        // Values for a "foo" client
        String packageNameFoo = "packageFoo";
        byte[] sha256CertFoo = new byte[] {10};

        VisibilityDocument
                visibilityDocument = new VisibilityDocument.Builder(/*id=*/"$/Schema")
                .addVisibleToPackage(new PackageIdentifier(packageNameFoo, sha256CertFoo)).build();
        mVisibilityStore.setVisibility(ImmutableList.of(visibilityDocument));

        // is accessible for caller who has system access.
        assertThat(mVisibilityChecker.isSchemaSearchableByCaller(
                new FrameworkCallerAccess(mAttributionSource,
                        /*callerHasSystemAccess=*/ true),
                /*packageName=*/ "",
                "$/Schema",
                mVisibilityStore)).isTrue();

        PackageManager mockPackageManager = getMockPackageManager(mContext.getUser());
        when(mockPackageManager.getPackageUid(eq(packageNameFoo), /*flags=*/ anyInt()))
                .thenReturn(mAttributionSource.getUid());
        when(mockPackageManager.hasSigningCertificate(
                packageNameFoo, sha256CertFoo, PackageManager.CERT_INPUT_SHA256))
                .thenReturn(true);
        // is accessible for caller who in the allow package list.
        assertThat(mVisibilityChecker.isSchemaSearchableByCaller(
                new FrameworkCallerAccess(mAttributionSource,
                        /*callerHasSystemAccess=*/ false),
                /*packageName=*/ "",
                "$/Schema",
                mVisibilityStore)).isTrue();
    }

    @Test
    public void testSetSchema_defaultPlatformVisible() throws Exception {
        String prefix = PrefixUtil.createPrefix("package", "database");
        VisibilityDocument visibilityDocument = new VisibilityDocument.Builder(
                /*id=*/prefix + "Schema").build();
        mVisibilityStore.setVisibility(ImmutableList.of(visibilityDocument));
        assertThat(mVisibilityChecker.isSchemaSearchableByCaller(
                new FrameworkCallerAccess(mAttributionSource,
                        /*callerHasSystemAccess=*/ true),
                "package",
                prefix + "Schema",
                mVisibilityStore))
                .isTrue();
    }

    @Test
    public void testSetSchema_platformHidden() throws Exception {
        String prefix = PrefixUtil.createPrefix("package", "database");
        VisibilityDocument visibilityDocument = new VisibilityDocument.Builder(
                /*id=*/prefix + "Schema")
                .setNotDisplayedBySystem(true).build();
        mVisibilityStore.setVisibility(ImmutableList.of(visibilityDocument));

        assertThat(mVisibilityChecker.isSchemaSearchableByCaller(
                new FrameworkCallerAccess(mAttributionSource,
                        /*callerHasSystemAccess=*/ true),
                "package",
                prefix + "Schema",
                mVisibilityStore))
                .isFalse();
    }

    @Test
    public void testSetSchema_defaultNotVisibleToPackages() throws Exception {
        String prefix = PrefixUtil.createPrefix("package", "database");
        VisibilityDocument visibilityDocument = new VisibilityDocument.Builder(
                /*id=*/prefix + "Schema").build();
        mVisibilityStore.setVisibility(ImmutableList.of(visibilityDocument));

        assertThat(mVisibilityChecker.isSchemaSearchableByCaller(
                new FrameworkCallerAccess(mAttributionSource,
                        /*callerHasSystemAccess=*/ false),
                        "package",
                        prefix + "Schema",
                        mVisibilityStore))
                .isFalse();
    }

    @Test
    public void testSetSchema_visibleToPackages() throws Exception {
        // Values for a "foo" client
        String packageNameFoo = "packageFoo";
        byte[] sha256CertFoo = new byte[] {10};

        // Make sure foo package will pass package manager checks.
        PackageManager mockPackageManager = getMockPackageManager(mContext.getUser());
        when(mockPackageManager.getPackageUid(eq(packageNameFoo), /*flags=*/ anyInt()))
                .thenReturn(mAttributionSource.getUid());
        when(mockPackageManager.hasSigningCertificate(
                packageNameFoo, sha256CertFoo, PackageManager.CERT_INPUT_SHA256))
                .thenReturn(true);

        String prefix = PrefixUtil.createPrefix("package", "database");

        VisibilityDocument visibilityDocument = new VisibilityDocument.Builder(
                /*id=*/prefix + "Schema")
                .addVisibleToPackage(new PackageIdentifier(packageNameFoo, sha256CertFoo)).build();
        mVisibilityStore.setVisibility(ImmutableList.of(visibilityDocument));

        assertThat(mVisibilityChecker.isSchemaSearchableByCaller(
                new FrameworkCallerAccess(mAttributionSource,
                        /*callerHasSystemAccess=*/ false),
                "package",
                prefix + "Schema",
                mVisibilityStore))
                .isTrue();
    }

    @Test
    public void testSetSchema_visibleToPermissions() throws Exception {
        String prefix = PrefixUtil.createPrefix("package", "database");

        // Create a VDoc that require READ_SMS permission only.
        VisibilityDocument visibilityDocument = new VisibilityDocument.Builder(
                /*id=*/prefix + "Schema")
                .setVisibleToPermissions(ImmutableSet.of(
                        ImmutableSet.of(SetSchemaRequest.READ_SMS)))
                .build();
        mVisibilityStore.setVisibility(ImmutableList.of(visibilityDocument));

        // Grant the READ_SMS permission, we should able to access.
        mUiAutomation.adoptShellPermissionIdentity(READ_SMS);
        try {
            assertThat(mVisibilityChecker.isSchemaSearchableByCaller(
                    new FrameworkCallerAccess(mAttributionSource,
                            /*callerHasSystemAccess=*/ false),
                    "package",
                    prefix + "Schema",
                    mVisibilityStore))
                    .isTrue();
        } finally {
            mUiAutomation.dropShellPermissionIdentity();
        }
        // Drop the READ_SMS permission, it becomes invisible.
        assertThat(mVisibilityChecker.isSchemaSearchableByCaller(
                new FrameworkCallerAccess(mAttributionSource,
                        /*callerHasSystemAccess=*/ false),
                "package",
                prefix + "Schema",
                mVisibilityStore))
                .isFalse();
    }

    //TODO(b/202194495) add test for READ_HOME_APP_SEARCH_DATA and READ_ASSISTANT_APP_SEARCH_DATA
    // once they are available in Shell.
    @Test
    public void testSetSchema_visibleToPermissions_anyCombinations() throws Exception {
        String prefix = PrefixUtil.createPrefix("package", "database");

        // Create a VDoc that require the querier to hold both READ_SMS and READ_CALENDAR, or
        // READ_CONTACTS only or READ_EXTERNAL_STORAGE only.
        VisibilityDocument visibilityDocument = new VisibilityDocument.Builder(
                /*id=*/prefix + "Schema")
                .setVisibleToPermissions(ImmutableSet.of(
                        ImmutableSet.of(SetSchemaRequest.READ_SMS, SetSchemaRequest.READ_CALENDAR),
                        ImmutableSet.of(SetSchemaRequest.READ_CONTACTS),
                        ImmutableSet.of(SetSchemaRequest.READ_EXTERNAL_STORAGE)))
                .build();
        mVisibilityStore.setVisibility(ImmutableList.of(visibilityDocument));

        // Grant the READ_SMS and READ_CALENDAR permission, we should able to access.
        mUiAutomation.adoptShellPermissionIdentity(READ_SMS, READ_CALENDAR);
        try {
            assertThat(mVisibilityChecker.isSchemaSearchableByCaller(
                    new FrameworkCallerAccess(mAttributionSource,
                            /*callerHasSystemAccess=*/ false),
                    "package",
                    prefix + "Schema",
                    mVisibilityStore))
                    .isTrue();
        } finally {
            mUiAutomation.dropShellPermissionIdentity();
        }

        // Grant the READ_SMS only, it shouldn't have access.
        mUiAutomation.adoptShellPermissionIdentity(READ_SMS);
        try {
            assertThat(mVisibilityChecker.isSchemaSearchableByCaller(
                    new FrameworkCallerAccess(mAttributionSource,
                            /*callerHasSystemAccess=*/ false),
                    "package",
                    prefix + "Schema",
                    mVisibilityStore))
                    .isFalse();
        } finally {
            mUiAutomation.dropShellPermissionIdentity();
        }

        // Grant the READ_SMS and READ_CALENDAR permission, we should able to access.
        mUiAutomation.adoptShellPermissionIdentity(READ_SMS, READ_CALENDAR);
        try {
            assertThat(mVisibilityChecker.isSchemaSearchableByCaller(
                    new FrameworkCallerAccess(mAttributionSource,
                            /*callerHasSystemAccess=*/ false),
                    "package",
                    prefix + "Schema",
                    mVisibilityStore))
                    .isTrue();
        } finally {
            mUiAutomation.dropShellPermissionIdentity();
        }

        // Grant the READ_CONTACTS permission, we should able to access.
        mUiAutomation.adoptShellPermissionIdentity(READ_CONTACTS);
        try {
            assertThat(mVisibilityChecker.isSchemaSearchableByCaller(
                    new FrameworkCallerAccess(mAttributionSource,
                            /*callerHasSystemAccess=*/ false),
                    "package",
                    prefix + "Schema",
                    mVisibilityStore))
                    .isTrue();
        } finally {
            mUiAutomation.dropShellPermissionIdentity();
        }

        // Grant the READ_EXTERNAL_STORAGE permission, we should able to access.
        mUiAutomation.adoptShellPermissionIdentity(READ_EXTERNAL_STORAGE);
        try {
            assertThat(mVisibilityChecker.isSchemaSearchableByCaller(
                    new FrameworkCallerAccess(mAttributionSource,
                            /*callerHasSystemAccess=*/ false),
                    "package",
                    prefix + "Schema",
                    mVisibilityStore))
                    .isTrue();
        } finally {
            mUiAutomation.dropShellPermissionIdentity();
        }

        // Drop permissions, it becomes invisible.
        assertThat(mVisibilityChecker.isSchemaSearchableByCaller(
                new FrameworkCallerAccess(mAttributionSource,
                        /*callerHasSystemAccess=*/ false),
                "package",
                prefix + "Schema",
                mVisibilityStore))
                .isFalse();
    }

    @Test
    public void testSetSchema_defaultNotVisibleToPermissions() throws Exception {
        String prefix = PrefixUtil.createPrefix("package", "database");

        // Create a VDoc with default setting.
        VisibilityDocument visibilityDocument = new VisibilityDocument.Builder(
                /*id=*/prefix + "Schema").build();
        mVisibilityStore.setVisibility(ImmutableList.of(visibilityDocument));

        // Give all supported permissions to the caller, it still cannot get the access.
        mUiAutomation.adoptShellPermissionIdentity(READ_SMS, READ_CALENDAR, READ_CONTACTS,
                READ_EXTERNAL_STORAGE, READ_HOME_APP_SEARCH_DATA, READ_ASSISTANT_APP_SEARCH_DATA);
        try {
            assertThat(mVisibilityChecker.isSchemaSearchableByCaller(
                    new FrameworkCallerAccess(mAttributionSource,
                            /*callerHasSystemAccess=*/ false),
                    "package",
                    prefix + "Schema",
                    mVisibilityStore))
                    .isFalse();
        } finally {
            mUiAutomation.dropShellPermissionIdentity();
        }
    }

    @Test
    public void testSetSchema_visibleToPermissions_needAllPermissions() throws Exception {
        String prefix = PrefixUtil.createPrefix("package", "database");

        // Create a VDoc which requires both READ_SMS and READ_CALENDAR
        VisibilityDocument visibilityDocument = new VisibilityDocument.Builder(
                /*id=*/prefix + "Schema")
                .setVisibleToPermissions(ImmutableSet.of(
                        ImmutableSet.of(SetSchemaRequest.READ_SMS, SetSchemaRequest.READ_CALENDAR)))
                        .build();
        mVisibilityStore.setVisibility(ImmutableList.of(visibilityDocument));

        mUiAutomation.adoptShellPermissionIdentity(READ_SMS);
        try {
            // Only has READ_SMS won't have access.
            assertThat(mVisibilityChecker.isSchemaSearchableByCaller(
                    new FrameworkCallerAccess(mAttributionSource,
                            /*callerHasSystemAccess=*/ false),
                    "package",
                    prefix + "Schema",
                    mVisibilityStore))
                    .isFalse();
        } finally {
            mUiAutomation.dropShellPermissionIdentity();
        }

        mUiAutomation.adoptShellPermissionIdentity(READ_SMS, READ_CALENDAR);
        try {
            // has READ_SMS and READ_CALENDAR will have access.
            assertThat(mVisibilityChecker.isSchemaSearchableByCaller(
                    new FrameworkCallerAccess(mAttributionSource,
                            /*callerHasSystemAccess=*/ false),
                    "package",
                    prefix + "Schema",
                    mVisibilityStore))
                    .isTrue();
        } finally {
            mUiAutomation.dropShellPermissionIdentity();
        }
    }

    @NonNull
    private PackageManager getMockPackageManager(@NonNull UserHandle user) {
        PackageManager pm = mMockPackageManagers.get(user);
        if (pm == null) {
            pm = Mockito.mock(PackageManager.class);
            mMockPackageManagers.put(user, pm);
        }
        return pm;
    }
}
