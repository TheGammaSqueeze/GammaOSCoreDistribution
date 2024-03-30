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
package android.app.appsearch.cts.app;

import static android.Manifest.permission.READ_CALENDAR;
import static android.Manifest.permission.READ_CONTACTS;
import static android.Manifest.permission.READ_EXTERNAL_STORAGE;
import static android.Manifest.permission.READ_GLOBAL_APP_SEARCH_DATA;
import static android.Manifest.permission.READ_SMS;
import static android.app.appsearch.testutil.AppSearchTestUtils.checkIsBatchResultSuccess;

import static com.google.common.truth.Truth.assertThat;

import android.app.appsearch.AppSearchBatchResult;
import android.app.appsearch.AppSearchManager;
import android.app.appsearch.AppSearchSessionShim;
import android.app.appsearch.GenericDocument;
import android.app.appsearch.GetByDocumentIdRequest;
import android.app.appsearch.GetSchemaResponse;
import android.app.appsearch.GlobalSearchSessionShim;
import android.app.appsearch.PackageIdentifier;
import android.app.appsearch.PutDocumentsRequest;
import android.app.appsearch.ReportSystemUsageRequest;
import android.app.appsearch.ReportUsageRequest;
import android.app.appsearch.SearchResult;
import android.app.appsearch.SearchResultsShim;
import android.app.appsearch.SearchSpec;
import android.app.appsearch.SetSchemaRequest;
import android.app.appsearch.observer.DocumentChangeInfo;
import android.app.appsearch.observer.ObserverSpec;
import android.app.appsearch.testutil.AppSearchEmail;
import android.app.appsearch.testutil.AppSearchSessionShimImpl;
import android.app.appsearch.testutil.GlobalSearchSessionShimImpl;
import android.app.appsearch.testutil.TestObserverCallback;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.platform.test.annotations.AppModeFull;
import android.util.Log;

import androidx.test.core.app.ApplicationProvider;

import com.android.compatibility.common.util.SystemUtil;
import com.android.cts.appsearch.ICommandReceiver;

import com.google.common.collect.ImmutableSet;
import com.google.common.io.BaseEncoding;
import com.google.common.util.concurrent.MoreExecutors;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * This doesn't extend {@link android.app.appsearch.cts.app.GlobalSearchSessionCtsTestBase} since
 * these test cases can't be run in a non-platform environment.
 */
@AppModeFull(reason = "Can't bind to helper apps from instant mode")
public class GlobalSearchSessionPlatformCtsTest {

    private static final long TIMEOUT_BIND_SERVICE_SEC = 2;

    private static final String TAG = "GlobalSearchSessionPlatformCtsTest";

    private static final String PKG_A = "com.android.cts.appsearch.helper.a";

    // To generate, run `apksigner` on the build APK. e.g.
    //   ./apksigner verify --print-certs \
    //   ~/sc-dev/out/soong/.intermediates/cts/tests/appsearch/CtsAppSearchTestHelperA/\
    //   android_common/CtsAppSearchTestHelperA.apk`
    // to get the SHA-256 digest. All characters need to be uppercase.
    //
    // Note: May need to switch the "sdk_version" of the test app from "test_current" to "30" before
    // building the apk and running apksigner
    private static final byte[] PKG_A_CERT_SHA256 =
            BaseEncoding.base16()
                    .decode("A90B80BD307B71BB4029674C5C4FE18066994E352EAC933B7B68266210CAFB53");

    private static final String PKG_B = "com.android.cts.appsearch.helper.b";

    // To generate, run `apksigner` on the build APK. e.g.
    //   ./apksigner verify --print-certs \
    //   ~/sc-dev/out/soong/.intermediates/cts/tests/appsearch/CtsAppSearchTestHelperB/\
    //   android_common/CtsAppSearchTestHelperB.apk`
    // to get the SHA-256 digest. All characters need to be uppercase.
    //
    // Note: May need to switch the "sdk_version" of the test app from "test_current" to "30" before
    // building the apk and running apksigner
    private static final byte[] PKG_B_CERT_SHA256 =
            BaseEncoding.base16()
                    .decode("88C0B41A31943D13226C3F22A86A6B4F300315575A6BC533CBF16C4EF3CFAA37");

    private static final String HELPER_SERVICE =
            "com.android.cts.appsearch.helper.AppSearchTestService";

    private static final String TEXT = "foo";

    private static final String NAMESPACE_NAME = "namespace";

    private static final AppSearchEmail EMAIL_DOCUMENT =
            new AppSearchEmail.Builder(NAMESPACE_NAME, "id1")
                    .setFrom("from@example.com")
                    .setTo("to1@example.com", "to2@example.com")
                    .setSubject(TEXT)
                    .setBody("this is the body of the email")
                    .build();

    private static final String DB_NAME = "database";


    private GlobalSearchSessionShim mGlobalSearchSession;
    private AppSearchSessionShim mDb;

    private Context mContext;

    @Before
    public void setUp() throws Exception {
        mContext = ApplicationProvider.getApplicationContext();
        mDb =
                AppSearchSessionShimImpl.createSearchSessionAsync(
                                new AppSearchManager.SearchContext.Builder(DB_NAME).build())
                        .get();
        cleanup();
    }

    @After
    public void tearDown() throws Exception {
        // Cleanup whatever documents may still exist in these databases.
        cleanup();
    }

    private void cleanup() throws Exception {
        mDb.setSchema(new SetSchemaRequest.Builder().setForceOverride(true).build()).get();

        clearData(PKG_A, DB_NAME);
        clearData(PKG_B, DB_NAME);
    }

    @Test
    public void testNoPackageAccess_default() throws Exception {
        mDb.setSchema(new SetSchemaRequest.Builder().addSchemas(AppSearchEmail.SCHEMA).build())
                .get();
        checkIsBatchResultSuccess(
                mDb.put(
                        new PutDocumentsRequest.Builder()
                                .addGenericDocuments(EMAIL_DOCUMENT)
                                .build()));

        // No package has access by default
        assertPackageCannotAccess(PKG_A);
        assertPackageCannotAccess(PKG_B);
    }

    @Test
    public void testNoPackageAccess_wrongPackageName() throws Exception {
        mDb.setSchema(
                        new SetSchemaRequest.Builder()
                                .addSchemas(AppSearchEmail.SCHEMA)
                                .setSchemaTypeVisibilityForPackage(
                                        AppSearchEmail.SCHEMA_TYPE,
                                        /*visible=*/ true,
                                        new PackageIdentifier(
                                                "some.other.package", PKG_A_CERT_SHA256))
                                .build())
                .get();
        checkIsBatchResultSuccess(
                mDb.put(
                        new PutDocumentsRequest.Builder()
                                .addGenericDocuments(EMAIL_DOCUMENT)
                                .build()));

        assertPackageCannotAccess(PKG_A);
    }

    @Test
    public void testNoPackageAccess_wrongCertificate() throws Exception {
        mDb.setSchema(
                        new SetSchemaRequest.Builder()
                                .addSchemas(AppSearchEmail.SCHEMA)
                                .setSchemaTypeVisibilityForPackage(
                                        AppSearchEmail.SCHEMA_TYPE,
                                        /*visible=*/ true,
                                        new PackageIdentifier(PKG_A, new byte[] {10}))
                                .build())
                .get();
        checkIsBatchResultSuccess(
                mDb.put(
                        new PutDocumentsRequest.Builder()
                                .addGenericDocuments(EMAIL_DOCUMENT)
                                .build()));

        assertPackageCannotAccess(PKG_A);
    }

    @Test
    public void testAllowPackageAccess() throws Exception {
        mDb.setSchema(
                        new SetSchemaRequest.Builder()
                                .addSchemas(AppSearchEmail.SCHEMA)
                                .setSchemaTypeVisibilityForPackage(
                                        AppSearchEmail.SCHEMA_TYPE,
                                        /*visible=*/ true,
                                        new PackageIdentifier(PKG_A, PKG_A_CERT_SHA256))
                                .build())
                .get();
        checkIsBatchResultSuccess(
                mDb.put(
                        new PutDocumentsRequest.Builder()
                                .addGenericDocuments(EMAIL_DOCUMENT)
                                .build()));

        assertPackageCanAccess(EMAIL_DOCUMENT, PKG_A);
        assertPackageCannotAccess(PKG_B);
    }

    @Test
    public void testRemoveVisibilitySetting_noRemainingSettings() throws Exception {
        // Set schema and allow PKG_A to access.
        mDb.setSchema(
                new SetSchemaRequest.Builder()
                        .addSchemas(AppSearchEmail.SCHEMA)
                        .setSchemaTypeVisibilityForPackage(
                                AppSearchEmail.SCHEMA_TYPE,
                                /*visible=*/ true,
                                new PackageIdentifier(PKG_A, PKG_A_CERT_SHA256))
                        .build())
                .get();
        checkIsBatchResultSuccess(
                mDb.put(
                        new PutDocumentsRequest.Builder()
                                .addGenericDocuments(EMAIL_DOCUMENT)
                                .build()));

        // PKG_A can access.
        assertPackageCanAccess(EMAIL_DOCUMENT, PKG_A);
        assertPackageCannotAccess(PKG_B);

        // Remove the schema.
        mDb.setSchema(new SetSchemaRequest.Builder().setForceOverride(true).build()).get();

        // Add the schema back with default visibility setting.
        mDb.setSchema(new SetSchemaRequest.Builder()
                .addSchemas(AppSearchEmail.SCHEMA).build()).get();

        // No pcakage can access.
        assertPackageCannotAccess(PKG_A);
        assertPackageCannotAccess(PKG_B);
    }

    @Test
    public void testAllowMultiplePackageAccess() throws Exception {
        mDb.setSchema(
                        new SetSchemaRequest.Builder()
                                .addSchemas(AppSearchEmail.SCHEMA)
                                .setSchemaTypeVisibilityForPackage(
                                        AppSearchEmail.SCHEMA_TYPE,
                                        /*visible=*/ true,
                                        new PackageIdentifier(PKG_A, PKG_A_CERT_SHA256))
                                .setSchemaTypeVisibilityForPackage(
                                        AppSearchEmail.SCHEMA_TYPE,
                                        /*visible=*/ true,
                                        new PackageIdentifier(PKG_B, PKG_B_CERT_SHA256))
                                .build())
                .get();
        checkIsBatchResultSuccess(
                mDb.put(
                        new PutDocumentsRequest.Builder()
                                .addGenericDocuments(EMAIL_DOCUMENT)
                                .build()));

        assertPackageCanAccess(EMAIL_DOCUMENT, PKG_A);
        assertPackageCanAccess(EMAIL_DOCUMENT, PKG_B);
    }

    @Test
    public void testNoPackageAccess_revoked() throws Exception {
        mDb.setSchema(
                        new SetSchemaRequest.Builder()
                                .addSchemas(AppSearchEmail.SCHEMA)
                                .setSchemaTypeVisibilityForPackage(
                                        AppSearchEmail.SCHEMA_TYPE,
                                        /*visible=*/ true,
                                        new PackageIdentifier(PKG_A, PKG_A_CERT_SHA256))
                                .build())
                .get();
        checkIsBatchResultSuccess(
                mDb.put(
                        new PutDocumentsRequest.Builder()
                                .addGenericDocuments(EMAIL_DOCUMENT)
                                .build()));
        assertPackageCanAccess(EMAIL_DOCUMENT, PKG_A);

        // Set the schema again, but package access as false.
        mDb.setSchema(
                        new SetSchemaRequest.Builder()
                                .addSchemas(AppSearchEmail.SCHEMA)
                                .setSchemaTypeVisibilityForPackage(
                                        AppSearchEmail.SCHEMA_TYPE,
                                        /*visible=*/ false,
                                        new PackageIdentifier(PKG_A, PKG_A_CERT_SHA256))
                                .build())
                .get();
        checkIsBatchResultSuccess(
                mDb.put(
                        new PutDocumentsRequest.Builder()
                                .addGenericDocuments(EMAIL_DOCUMENT)
                                .build()));
        assertPackageCannotAccess(PKG_A);

        // Set the schema again, but with default (i.e. no) access
        mDb.setSchema(
                        new SetSchemaRequest.Builder()
                                .addSchemas(AppSearchEmail.SCHEMA)
                                .setSchemaTypeVisibilityForPackage(
                                        AppSearchEmail.SCHEMA_TYPE,
                                        /*visible=*/ false,
                                        new PackageIdentifier(PKG_A, PKG_A_CERT_SHA256))
                                .build())
                .get();
        checkIsBatchResultSuccess(
                mDb.put(
                        new PutDocumentsRequest.Builder()
                                .addGenericDocuments(EMAIL_DOCUMENT)
                                .build()));
        assertPackageCannotAccess(PKG_A);
    }

    @Test
    public void testAllowPermissionAccess() throws Exception {
        // index a global searchable document in pkg_A and set it needs READ_SMS to read it.
        indexGloballySearchableDocument(PKG_A, DB_NAME, NAMESPACE_NAME, "id1",
                ImmutableSet.of(ImmutableSet.of(SetSchemaRequest.READ_SMS)));

        SystemUtil.runWithShellPermissionIdentity(
                () -> {
                    mGlobalSearchSession =
                            GlobalSearchSessionShimImpl.createGlobalSearchSessionAsync(mContext)
                                    .get();

                    // Can get the document
                    AppSearchBatchResult<String, GenericDocument> result = mGlobalSearchSession
                            .getByDocumentIdAsync(PKG_A, "database",
                                    new GetByDocumentIdRequest.Builder("namespace")
                                            .addIds("id1")
                                            .build()).get();
                    assertThat(result.getSuccesses()).hasSize(1);
                },
                READ_SMS);
    }

    //TODO(b/202194495) add test for READ_HOME_APP_SEARCH_DATA and READ_ASSISTANT_APP_SEARCH_DATA
    // once they are available in Shell.
    @Test
    public void testRequireAllPermissionsOfAnyCombinationToAccess() throws Exception {
        // index a global searchable document in pkg_A and set it needs both READ_SMS and
        // READ_CALENDAR or READ_HOME_APP_SEARCH_DATA only or READ_ASSISTANT_APP_SEARCH_DATA
        // only to read it.
        indexGloballySearchableDocument(PKG_A, DB_NAME, NAMESPACE_NAME, "id1",
                ImmutableSet.of(
                        ImmutableSet.of(SetSchemaRequest.READ_SMS,
                                SetSchemaRequest.READ_CALENDAR),
                        ImmutableSet.of(SetSchemaRequest.READ_CONTACTS),
                        ImmutableSet.of(SetSchemaRequest.READ_EXTERNAL_STORAGE)));

        // Has READ_SMS only cannot access the document.
        SystemUtil.runWithShellPermissionIdentity(
                () -> {
                    mGlobalSearchSession =
                            GlobalSearchSessionShimImpl.createGlobalSearchSessionAsync(mContext)
                                    .get();

                    // Can get the document
                    AppSearchBatchResult<String, GenericDocument> result = mGlobalSearchSession
                            .getByDocumentIdAsync(PKG_A, "database",
                                    new GetByDocumentIdRequest.Builder("namespace")
                                            .addIds("id1")
                                            .build()).get();
                    assertThat(result.getSuccesses()).isEmpty();
                },
                READ_SMS);

        // Has READ_SMS and READ_CALENDAR can access the document.
        SystemUtil.runWithShellPermissionIdentity(
                () -> {
                    mGlobalSearchSession =
                            GlobalSearchSessionShimImpl.createGlobalSearchSessionAsync(mContext)
                                    .get();

                    // Can get the document
                    AppSearchBatchResult<String, GenericDocument> result = mGlobalSearchSession
                            .getByDocumentIdAsync(PKG_A, "database",
                                    new GetByDocumentIdRequest.Builder("namespace")
                                            .addIds("id1")
                                            .build()).get();
                    assertThat(result.getSuccesses()).hasSize(1);
                },
                READ_SMS, READ_CALENDAR);

        // Has READ_CONTACTS can access the document also.
        SystemUtil.runWithShellPermissionIdentity(
                () -> {
                    mGlobalSearchSession =
                            GlobalSearchSessionShimImpl.createGlobalSearchSessionAsync(mContext)
                                    .get();

                    // Can get the document
                    AppSearchBatchResult<String, GenericDocument> result = mGlobalSearchSession
                            .getByDocumentIdAsync(PKG_A, "database",
                                    new GetByDocumentIdRequest.Builder("namespace")
                                            .addIds("id1")
                                            .build()).get();
                    assertThat(result.getSuccesses()).hasSize(1);
                },
                READ_CONTACTS);

        // Has READ_EXTERNAL_STORAGE can access the document.
        SystemUtil.runWithShellPermissionIdentity(
                () -> {
                    mGlobalSearchSession =
                            GlobalSearchSessionShimImpl.createGlobalSearchSessionAsync(mContext)
                                    .get();

                    // Can get the document
                    AppSearchBatchResult<String, GenericDocument> result = mGlobalSearchSession
                            .getByDocumentIdAsync(PKG_A, "database",
                                    new GetByDocumentIdRequest.Builder("namespace")
                                            .addIds("id1")
                                            .build()).get();
                    assertThat(result.getSuccesses()).hasSize(1);
                },
                READ_EXTERNAL_STORAGE);
    }

    @Test
    public void testAllowPermissions_sameError() throws Exception {
        // Try to get document before we put them, this is not found error.
        mGlobalSearchSession =
                GlobalSearchSessionShimImpl.createGlobalSearchSessionAsync(mContext).get();
        AppSearchBatchResult<String, GenericDocument> nonExistentResult = mGlobalSearchSession
                .getByDocumentIdAsync(PKG_A, "database",
                        new GetByDocumentIdRequest.Builder("namespace")
                                .addIds("id1")
                                .build()).get();
        assertThat(nonExistentResult.isSuccess()).isFalse();
        assertThat(nonExistentResult.getSuccesses()).isEmpty();
        assertThat(nonExistentResult.getFailures()).containsKey("id1");

        // Index a global searchable document in pkg_A and set it needs READ_SMS to read it.
        indexGloballySearchableDocument(PKG_A, DB_NAME, NAMESPACE_NAME, "id1",
                ImmutableSet.of(ImmutableSet.of(SetSchemaRequest.READ_SMS)));

        // Try to get document w/o permission, this is unAuthority error.
        mGlobalSearchSession =
                GlobalSearchSessionShimImpl.createGlobalSearchSessionAsync(mContext).get();
        AppSearchBatchResult<String, GenericDocument> unAuthResult = mGlobalSearchSession
                .getByDocumentIdAsync(PKG_A, "database",
                        new GetByDocumentIdRequest.Builder("namespace")
                                .addIds("id1")
                                .build()).get();
        assertThat(unAuthResult.isSuccess()).isFalse();
        assertThat(unAuthResult.getSuccesses()).isEmpty();
        assertThat(unAuthResult.getFailures()).containsKey("id1");

        // The error messages must be same.
        assertThat(unAuthResult.getFailures().get("id1").getErrorMessage())
                .isEqualTo(nonExistentResult.getFailures().get("id1").getErrorMessage());
    }

    @Test
    public void testGlobalGetById_withAccess() throws Exception {
        indexGloballySearchableDocument(PKG_A, DB_NAME, NAMESPACE_NAME, "id1");

        SystemUtil.runWithShellPermissionIdentity(
                () -> {
                    mGlobalSearchSession =
                            GlobalSearchSessionShimImpl.createGlobalSearchSessionAsync(mContext)
                                    .get();

                    // Can get the document
                    AppSearchBatchResult<String, GenericDocument> result = mGlobalSearchSession
                            .getByDocumentIdAsync(PKG_A, DB_NAME,
                                    new GetByDocumentIdRequest.Builder(NAMESPACE_NAME)
                                            .addIds("id1")
                                            .build()).get();

                    assertThat(result.getSuccesses()).hasSize(1);

                    // Can't get non existent document
                    AppSearchBatchResult<String, GenericDocument> nonExistent = mGlobalSearchSession
                            .getByDocumentIdAsync(PKG_A, DB_NAME,
                                    new GetByDocumentIdRequest.Builder(NAMESPACE_NAME)
                                            .addIds("id2")
                                            .build()).get();

                    assertThat(nonExistent.isSuccess()).isFalse();
                    assertThat(nonExistent.getSuccesses()).hasSize(0);
                },
                READ_GLOBAL_APP_SEARCH_DATA);
    }

    @Test
    public void testGlobalGetById_withoutAccess() throws Exception {
        indexNotGloballySearchableDocument(PKG_A, DB_NAME, NAMESPACE_NAME, "id1");

        SystemUtil.runWithShellPermissionIdentity(
                () -> {
                    mGlobalSearchSession =
                            GlobalSearchSessionShimImpl.createGlobalSearchSessionAsync(mContext)
                                    .get();

                    // Can't get the document
                    AppSearchBatchResult<String, GenericDocument> result = mGlobalSearchSession
                            .getByDocumentIdAsync(PKG_A, DB_NAME,
                                    new GetByDocumentIdRequest.Builder(NAMESPACE_NAME)
                                            .addIds("id1")
                                            .build()).get();
                    assertThat(result.isSuccess()).isFalse();
                    assertThat(result.getSuccesses()).hasSize(0);
                    assertThat(result.getFailures()).containsKey("id1");
                },
                READ_GLOBAL_APP_SEARCH_DATA);
    }

    @Test
    public void testGlobalGetById_sameErrorMessages() throws Exception {
        AtomicReference<String> errorMessageNonExistent = new AtomicReference<>();
        AtomicReference<String> errorMessageUnauth = new AtomicReference<>();

        // Can't get the document because we haven't added it yet
        SystemUtil.runWithShellPermissionIdentity(
                () -> {
                    mGlobalSearchSession =
                            GlobalSearchSessionShimImpl.createGlobalSearchSessionAsync(mContext)
                                    .get();

                    AppSearchBatchResult<String, GenericDocument> nonExistentResult =
                            mGlobalSearchSession.getByDocumentIdAsync(PKG_A, DB_NAME,
                                    new GetByDocumentIdRequest.Builder(NAMESPACE_NAME)
                                            .addIds("id1")
                                            .build()).get();
                    assertThat(nonExistentResult.isSuccess()).isFalse();
                    assertThat(nonExistentResult.getSuccesses()).hasSize(0);
                    assertThat(nonExistentResult.getFailures()).containsKey("id1");
                    errorMessageNonExistent.set(
                            nonExistentResult.getFailures().get("id1").getErrorMessage());
                },
                READ_GLOBAL_APP_SEARCH_DATA);

        indexNotGloballySearchableDocument(PKG_A, DB_NAME, NAMESPACE_NAME, "id1");

        // Can't get the document because the document is not globally searchable
        SystemUtil.runWithShellPermissionIdentity(
                () -> {
                    mGlobalSearchSession =
                            GlobalSearchSessionShimImpl.createGlobalSearchSessionAsync(mContext)
                                    .get();

                    AppSearchBatchResult<String, GenericDocument> unAuthResult =
                            mGlobalSearchSession.getByDocumentIdAsync(PKG_A, DB_NAME,
                                    new GetByDocumentIdRequest.Builder(NAMESPACE_NAME)
                                            .addIds("id1")
                                            .build()).get();
                    assertThat(unAuthResult.isSuccess()).isFalse();
                    assertThat(unAuthResult.getSuccesses()).hasSize(0);
                    assertThat(unAuthResult.getFailures()).containsKey("id1");
                    errorMessageUnauth.set(
                            unAuthResult.getFailures().get("id1").getErrorMessage());
                },
                READ_GLOBAL_APP_SEARCH_DATA);

        // try adding a global doc here to make sure non-global querier can't get it
        // and same error message
        clearData(PKG_A, DB_NAME);
        indexGloballySearchableDocument(PKG_A, DB_NAME, NAMESPACE_NAME, "id1");

        // Can't get the document because we don't have global permissions
        mGlobalSearchSession =
                GlobalSearchSessionShimImpl.createGlobalSearchSessionAsync(mContext).get();

        AppSearchBatchResult<String, GenericDocument> noGlobalResult = mGlobalSearchSession
                .getByDocumentIdAsync(PKG_A, DB_NAME,
                        new GetByDocumentIdRequest.Builder(NAMESPACE_NAME)
                                .addIds("id1")
                                .build()).get();
        assertThat(noGlobalResult.isSuccess()).isFalse();
        assertThat(noGlobalResult.getSuccesses()).hasSize(0);
        assertThat(noGlobalResult.getFailures()).containsKey("id1");

        // compare error messages
        assertThat(errorMessageNonExistent.get()).isEqualTo(errorMessageUnauth.get());
        assertThat(errorMessageNonExistent.get())
                .isEqualTo(noGlobalResult.getFailures().get("id1").getErrorMessage());
    }

    @Test
    public void testGlobalSearch_withAccess() throws Exception {
        indexGloballySearchableDocument(PKG_A, DB_NAME, NAMESPACE_NAME, "id1");
        indexGloballySearchableDocument(PKG_B, DB_NAME, NAMESPACE_NAME, "id1");

        SystemUtil.runWithShellPermissionIdentity(
                () -> {
                    mGlobalSearchSession =
                            GlobalSearchSessionShimImpl.createGlobalSearchSessionAsync(mContext)
                                    .get();

                    SearchResultsShim searchResults =
                            mGlobalSearchSession.search(
                                    /*queryExpression=*/ "",
                                    new SearchSpec.Builder()
                                            .setTermMatch(SearchSpec.TERM_MATCH_EXACT_ONLY)
                                            .addFilterPackageNames(PKG_A, PKG_B)
                                            .build());
                    List<SearchResult> page = searchResults.getNextPage().get();
                    assertThat(page).hasSize(2);

                    Set<String> actualPackageNames =
                            ImmutableSet.of(
                                    page.get(0).getPackageName(), page.get(1).getPackageName());
                    assertThat(actualPackageNames).containsExactly(PKG_A, PKG_B);
                },
                READ_GLOBAL_APP_SEARCH_DATA);
    }

    @Test
    public void testGlobalSearch_withPartialAccess() throws Exception {
        indexGloballySearchableDocument(PKG_A, DB_NAME, NAMESPACE_NAME, "id1");
        indexNotGloballySearchableDocument(PKG_B, DB_NAME, NAMESPACE_NAME, "id1");

        SystemUtil.runWithShellPermissionIdentity(
                () -> {
                    mGlobalSearchSession =
                            GlobalSearchSessionShimImpl.createGlobalSearchSessionAsync(mContext)
                                    .get();

                    SearchResultsShim searchResults =
                            mGlobalSearchSession.search(
                                    /*queryExpression=*/ "",
                                    new SearchSpec.Builder()
                                            .setTermMatch(SearchSpec.TERM_MATCH_EXACT_ONLY)
                                            .addFilterPackageNames(PKG_A, PKG_B)
                                            .build());
                    List<SearchResult> page = searchResults.getNextPage().get();
                    assertThat(page).hasSize(1);

                    assertThat(page.get(0).getPackageName()).isEqualTo(PKG_A);
                },
                READ_GLOBAL_APP_SEARCH_DATA);
    }

    @Test
    public void testGlobalSearch_withPackageFilters() throws Exception {
        indexGloballySearchableDocument(PKG_A, DB_NAME, NAMESPACE_NAME, "id1");
        indexGloballySearchableDocument(PKG_B, DB_NAME, NAMESPACE_NAME, "id1");

        SystemUtil.runWithShellPermissionIdentity(
                () -> {
                    mGlobalSearchSession =
                            GlobalSearchSessionShimImpl.createGlobalSearchSessionAsync(mContext)
                                    .get();

                    SearchResultsShim searchResults =
                            mGlobalSearchSession.search(
                                    /*queryExpression=*/ "",
                                    new SearchSpec.Builder()
                                            .setTermMatch(SearchSpec.TERM_MATCH_EXACT_ONLY)
                                            .addFilterPackageNames(PKG_B)
                                            .build());
                    List<SearchResult> page = searchResults.getNextPage().get();
                    assertThat(page).hasSize(1);

                    assertThat(page.get(0).getPackageName()).isEqualTo(PKG_B);
                },
                READ_GLOBAL_APP_SEARCH_DATA);
    }

    @Test
    public void testGlobalSearch_withoutAccess() throws Exception {
        indexGloballySearchableDocument(PKG_A, DB_NAME, NAMESPACE_NAME, "id1");
        indexGloballySearchableDocument(PKG_B, DB_NAME, NAMESPACE_NAME, "id1");

        mGlobalSearchSession =
                GlobalSearchSessionShimImpl.createGlobalSearchSessionAsync(mContext).get();

        SearchResultsShim searchResults =
                mGlobalSearchSession.search(
                        /*queryExpression=*/ "",
                        new SearchSpec.Builder()
                                .setTermMatch(SearchSpec.TERM_MATCH_EXACT_ONLY)
                                .addFilterPackageNames(PKG_A, PKG_B)
                                .build());
        List<SearchResult> page = searchResults.getNextPage().get();
        assertThat(page).isEmpty();
    }

    @Test
    public void testGlobalGetSchema_packageAccess_defaultAccess() throws Exception {
        // 1. Create a schema in the test with default (no) access.
        mDb.setSchema(
                        new SetSchemaRequest.Builder()
                                .addSchemas(AppSearchEmail.SCHEMA)
                                .build())
                .get();

        // 2. Neither PKG_A nor PKG_B should be able to retrieve the schema.
        List<String> schemaStrings = getSchemaAsPackage(PKG_A);
        assertThat(schemaStrings).isNull();

        schemaStrings = getSchemaAsPackage(PKG_B);
        assertThat(schemaStrings).isNull();
    }

    @Test
    public void testGlobalGetSchema_packageAccess_singleAccess() throws Exception {
        // 1. Create a schema in the test with access granted to PKG_A, but not PKG_B.
        mDb.setSchema(
                        new SetSchemaRequest.Builder()
                                .addSchemas(AppSearchEmail.SCHEMA)
                                .setSchemaTypeVisibilityForPackage(
                                        AppSearchEmail.SCHEMA_TYPE,
                                        /*visible=*/ true,
                                        new PackageIdentifier(PKG_A, PKG_A_CERT_SHA256))
                                .build())
                .get();

        // 2. Only PKG_A should be able to retrieve the schema.
        List<String> schemaStrings = getSchemaAsPackage(PKG_A);
        assertThat(schemaStrings).containsExactly(AppSearchEmail.SCHEMA.toString());

        schemaStrings = getSchemaAsPackage(PKG_B);
        assertThat(schemaStrings).isNull();
    }

    @Test
    public void testGlobalGetSchema_packageAccess_multiAccess() throws Exception {
        // 1. Create a schema in the test with access granted to PKG_A and PKG_B.
        mDb.setSchema(
                        new SetSchemaRequest.Builder()
                                .addSchemas(AppSearchEmail.SCHEMA)
                                .setSchemaTypeVisibilityForPackage(
                                        AppSearchEmail.SCHEMA_TYPE,
                                        /*visible=*/ true,
                                        new PackageIdentifier(PKG_A, PKG_A_CERT_SHA256))
                                .setSchemaTypeVisibilityForPackage(
                                        AppSearchEmail.SCHEMA_TYPE,
                                        /*visible=*/ true,
                                        new PackageIdentifier(PKG_B, PKG_B_CERT_SHA256))
                                .build())
                .get();

        // 2. Both packages should be able to retrieve the schema.
        List<String> schemaStrings = getSchemaAsPackage(PKG_A);
        assertThat(schemaStrings).containsExactly(AppSearchEmail.SCHEMA.toString());

        schemaStrings = getSchemaAsPackage(PKG_B);
        assertThat(schemaStrings).containsExactly(AppSearchEmail.SCHEMA.toString());
    }

    @Test
    public void testGlobalGetSchema_packageAccess_revokeAccess() throws Exception {
        // 1. Create a schema in the test with access granted to PKG_A.
        mDb.setSchema(
                        new SetSchemaRequest.Builder()
                                .addSchemas(AppSearchEmail.SCHEMA)
                                .setSchemaTypeVisibilityForPackage(
                                        AppSearchEmail.SCHEMA_TYPE,
                                        /*visible=*/ true,
                                        new PackageIdentifier(PKG_A, PKG_A_CERT_SHA256))
                                .build())
                .get();

        // 2. Now revoke that access.
        mDb.setSchema(
                        new SetSchemaRequest.Builder()
                                .addSchemas(AppSearchEmail.SCHEMA)
                                .setSchemaTypeVisibilityForPackage(
                                        AppSearchEmail.SCHEMA_TYPE,
                                        /*visible=*/ false,
                                        new PackageIdentifier(PKG_A, PKG_A_CERT_SHA256))
                                .build())
                .get();

        // 3. PKG_A should NOT be able to retrieve the schema.
        List<String> schemaStrings = getSchemaAsPackage(PKG_A);
        assertThat(schemaStrings).isNull();
    }

    @Test
    public void testGlobalGetSchema_globalAccess_singleAccess() throws Exception {
        // 1. Index documents for PKG_A and PKG_B. This will set the schema for each with the
        // corresponding access set.
        indexGloballySearchableDocument(PKG_A, DB_NAME, NAMESPACE_NAME, "id1");
        indexNotGloballySearchableDocument(PKG_B, DB_NAME, NAMESPACE_NAME, "id1");

        SystemUtil.runWithShellPermissionIdentity(
                () -> {
                    mGlobalSearchSession =
                            GlobalSearchSessionShimImpl.createGlobalSearchSessionAsync(
                                mContext).get();

                    // 2. The schema for PKG_A should be retrievable, but PKG_B should not be.
                    GetSchemaResponse response =
                            mGlobalSearchSession.getSchema(PKG_A, DB_NAME).get();
                    assertThat(response.getSchemas()).hasSize(1);

                    response = mGlobalSearchSession.getSchema(PKG_B, DB_NAME).get();
                    assertThat(response.getSchemas()).isEmpty();
                },
                READ_GLOBAL_APP_SEARCH_DATA);
    }

    @Test
    public void testGlobalGetSchema_globalAccess_multiAccess() throws Exception {
        // 1. Index documents for PKG_A and PKG_B. This will set the schema for each with the
        // corresponding access set.
        indexGloballySearchableDocument(PKG_A, DB_NAME, NAMESPACE_NAME, "id1");
        indexGloballySearchableDocument(PKG_B, DB_NAME, NAMESPACE_NAME, "id1");

        SystemUtil.runWithShellPermissionIdentity(
                () -> {
                    mGlobalSearchSession =
                            GlobalSearchSessionShimImpl.createGlobalSearchSessionAsync(
                                mContext).get();

                    // 2. The schema for both PKG_A and PKG_B should be retrievable.
                    GetSchemaResponse response =
                            mGlobalSearchSession.getSchema(PKG_A, DB_NAME).get();
                    assertThat(response.getSchemas()).hasSize(1);

                    response = mGlobalSearchSession.getSchema(PKG_B, DB_NAME).get();
                    assertThat(response.getSchemas()).hasSize(1);
                },
                READ_GLOBAL_APP_SEARCH_DATA);
    }


    @Test
    public void testReportSystemUsage() throws Exception {
        // Insert schema
        mDb.setSchema(new SetSchemaRequest.Builder().addSchemas(AppSearchEmail.SCHEMA).build())
                .get();

        // Insert two docs
        GenericDocument document1 =
                new GenericDocument.Builder<>(NAMESPACE_NAME, "id1", AppSearchEmail.SCHEMA_TYPE)
                        .build();
        GenericDocument document2 =
                new GenericDocument.Builder<>(NAMESPACE_NAME, "id2", AppSearchEmail.SCHEMA_TYPE)
                        .build();
        mDb.put(new PutDocumentsRequest.Builder().addGenericDocuments(document1, document2).build())
                .get();

        // Report some usages. id1 has 2 app and 1 system usage, id2 has 1 app and 2 system usage.
        mDb.reportUsage(
                new ReportUsageRequest.Builder(NAMESPACE_NAME, "id1")
                        .setUsageTimestampMillis(10)
                        .build())
                .get();
        mDb.reportUsage(
                new ReportUsageRequest.Builder(NAMESPACE_NAME, "id1")
                        .setUsageTimestampMillis(20)
                        .build())
                .get();
        mDb.reportUsage(
                new ReportUsageRequest.Builder(NAMESPACE_NAME, "id2")
                        .setUsageTimestampMillis(100)
                        .build())
                .get();

        SystemUtil.runWithShellPermissionIdentity(() -> {
            mGlobalSearchSession =
                    GlobalSearchSessionShimImpl.createGlobalSearchSessionAsync(mContext).get();
            mGlobalSearchSession
                    .reportSystemUsage(
                            new ReportSystemUsageRequest.Builder(
                                    mContext.getPackageName(), DB_NAME, NAMESPACE_NAME, "id1")
                                    .setUsageTimestampMillis(1000)
                                    .build())
                    .get();
            mGlobalSearchSession
                    .reportSystemUsage(
                            new ReportSystemUsageRequest.Builder(
                                    mContext.getPackageName(), DB_NAME, NAMESPACE_NAME, "id2")
                                    .setUsageTimestampMillis(200)
                                    .build())
                    .get();
            mGlobalSearchSession
                    .reportSystemUsage(
                            new ReportSystemUsageRequest.Builder(
                                    mContext.getPackageName(), DB_NAME, NAMESPACE_NAME, "id2")
                                    .setUsageTimestampMillis(150)
                                    .build())
                    .get();
        }, READ_GLOBAL_APP_SEARCH_DATA);

        // Query the data
        mGlobalSearchSession =
                GlobalSearchSessionShimImpl.createGlobalSearchSessionAsync(mContext).get();

        // Sort by app usage count: id1 should win
        try (SearchResultsShim results = mDb.search(
                "",
                new SearchSpec.Builder()
                        .setTermMatch(SearchSpec.TERM_MATCH_EXACT_ONLY)
                        .setRankingStrategy(SearchSpec.RANKING_STRATEGY_USAGE_COUNT)
                        .build())) {
            List<SearchResult> page = results.getNextPage().get();
            assertThat(page).hasSize(2);
            assertThat(page.get(0).getGenericDocument().getId()).isEqualTo("id1");
            assertThat(page.get(1).getGenericDocument().getId()).isEqualTo("id2");
        }

        // Sort by app usage timestamp: id2 should win
        try (SearchResultsShim results = mDb.search(
                "",
                new SearchSpec.Builder()
                        .setTermMatch(SearchSpec.TERM_MATCH_EXACT_ONLY)
                        .setRankingStrategy(SearchSpec.RANKING_STRATEGY_USAGE_LAST_USED_TIMESTAMP)
                        .build())) {
            List<SearchResult> page = results.getNextPage().get();
            assertThat(page).hasSize(2);
            assertThat(page.get(0).getGenericDocument().getId()).isEqualTo("id2");
            assertThat(page.get(1).getGenericDocument().getId()).isEqualTo("id1");
        }

        // Sort by system usage count: id2 should win
        try (SearchResultsShim results = mDb.search(
                "",
                new SearchSpec.Builder()
                        .setTermMatch(SearchSpec.TERM_MATCH_EXACT_ONLY)
                        .setRankingStrategy(
                                SearchSpec.RANKING_STRATEGY_SYSTEM_USAGE_COUNT)
                        .build())) {
            List<SearchResult> page = results.getNextPage().get();
            assertThat(page).hasSize(2);
            assertThat(page.get(0).getGenericDocument().getId()).isEqualTo("id2");
            assertThat(page.get(1).getGenericDocument().getId()).isEqualTo("id1");
        }

        // Sort by system usage timestamp: id1 should win
        SearchSpec searchSpec = new SearchSpec.Builder()
                .setTermMatch(SearchSpec.TERM_MATCH_EXACT_ONLY)
                .setRankingStrategy(SearchSpec.RANKING_STRATEGY_SYSTEM_USAGE_LAST_USED_TIMESTAMP)
                .build();
        try (SearchResultsShim results = mDb.search("", searchSpec)) {
            List<SearchResult> page = results.getNextPage().get();
            assertThat(page).hasSize(2);
            assertThat(page.get(0).getGenericDocument().getId()).isEqualTo("id1");
            assertThat(page.get(1).getGenericDocument().getId()).isEqualTo("id2");
        }
    }

    @Test
    public void testRemoveObserver_otherPackagesNotRemoved() throws Exception {
        final String fakePackage = "com.android.appsearch.fake.package";
        TestObserverCallback observer = new TestObserverCallback();

        // Set up schema
        mGlobalSearchSession =
                GlobalSearchSessionShimImpl.createGlobalSearchSessionAsync(mContext).get();
        mDb.setSchema(new SetSchemaRequest.Builder()
                .addSchemas(AppSearchEmail.SCHEMA).build()).get();

        // Register this observer twice, on different packages.
        Executor executor = MoreExecutors.directExecutor();
        mGlobalSearchSession.registerObserverCallback(
                mContext.getPackageName(),
                new ObserverSpec.Builder().addFilterSchemas(AppSearchEmail.SCHEMA_TYPE).build(),
                executor,
                observer);
        mGlobalSearchSession.registerObserverCallback(
                /*observedPackage=*/fakePackage,
                new ObserverSpec.Builder().addFilterSchemas("Gift").build(),
                executor,
                observer);

        // Make sure everything is empty
        assertThat(observer.getSchemaChanges()).isEmpty();
        assertThat(observer.getDocumentChanges()).isEmpty();

        // Index some documents
        AppSearchEmail email1 = new AppSearchEmail.Builder(NAMESPACE_NAME, "id1").build();
        AppSearchEmail email2 =
                new AppSearchEmail.Builder(NAMESPACE_NAME, "id2").setBody("caterpillar").build();
        AppSearchEmail email3 =
                new AppSearchEmail.Builder(NAMESPACE_NAME, "id3").setBody("foo").build();

        checkIsBatchResultSuccess(
                mDb.put(new PutDocumentsRequest.Builder()
                        .addGenericDocuments(email1).build()));

        // Make sure the notifications were received.
        observer.waitForNotificationCount(1);

        assertThat(observer.getSchemaChanges()).isEmpty();
        assertThat(observer.getDocumentChanges()).containsExactly(
                new DocumentChangeInfo(
                        mContext.getPackageName(),
                        DB_NAME,
                        NAMESPACE_NAME,
                        AppSearchEmail.SCHEMA_TYPE,
                        ImmutableSet.of("id1")));
        observer.clear();

        // Unregister observer from com.example.package
        mGlobalSearchSession.unregisterObserverCallback("com.example.package", observer);

        // Index some more documents
        assertThat(observer.getDocumentChanges()).isEmpty();
        checkIsBatchResultSuccess(
                mDb.put(new PutDocumentsRequest.Builder().addGenericDocuments(email2).build()));

        // Make sure data was still received
        observer.waitForNotificationCount(1);
        assertThat(observer.getDocumentChanges()).containsExactly(
                new DocumentChangeInfo(
                        mContext.getPackageName(),
                        DB_NAME,
                        NAMESPACE_NAME,
                        AppSearchEmail.SCHEMA_TYPE,
                        ImmutableSet.of("id2")));
        observer.clear();

        // Unregister the final observer
        mGlobalSearchSession.unregisterObserverCallback(mContext.getPackageName(), observer);

        // Index some more documents
        assertThat(observer.getDocumentChanges()).isEmpty();
        checkIsBatchResultSuccess(
                mDb.put(new PutDocumentsRequest.Builder().addGenericDocuments(email3).build()));

        // Make sure there have been no further notifications
        assertThat(observer.getDocumentChanges()).isEmpty();
    }

    private List<String> getSchemaAsPackage(String pkg) throws Exception {
        GlobalSearchSessionPlatformCtsTest.TestServiceConnection serviceConnection =
                bindToHelperService(pkg);
        try {
            ICommandReceiver commandReceiver = serviceConnection.getCommandReceiver();
            return commandReceiver.globalGetSchema(mContext.getPackageName(), DB_NAME);
        } finally {
            serviceConnection.unbind();
        }
    }

    private void assertPackageCannotAccess(String pkg) throws Exception {
        GlobalSearchSessionPlatformCtsTest.TestServiceConnection serviceConnection =
                bindToHelperService(pkg);
        try {
            ICommandReceiver commandReceiver = serviceConnection.getCommandReceiver();
            List<String> results = commandReceiver.globalSearch(TEXT);
            assertThat(results).isEmpty();
        } finally {
            serviceConnection.unbind();
        }
    }

    private void assertPackageCanAccess(GenericDocument expectedDocument, String pkg)
            throws Exception {
        GlobalSearchSessionPlatformCtsTest.TestServiceConnection serviceConnection =
                bindToHelperService(pkg);
        try {
            ICommandReceiver commandReceiver = serviceConnection.getCommandReceiver();
            List<String> results = commandReceiver.globalSearch(TEXT);
            assertThat(results).containsExactly(expectedDocument.toString());
        } finally {
            serviceConnection.unbind();
        }
    }

    private void indexGloballySearchableDocument(String pkg, String databaseName, String namespace,
            String id) throws Exception {
        indexGloballySearchableDocument(pkg, databaseName, namespace, id, Collections.emptySet());
    }

    private void indexGloballySearchableDocument(String pkg, String databaseName, String namespace,
            String id, Set<Set<Integer>> visibleToPermissions) throws Exception {
        // binder won't accept Set or Integer, we need to convert to List<Bundle>.
        List<Bundle> permissionBundles = new ArrayList<>(visibleToPermissions.size());
        for (Set<Integer> allRequiredPermissions : visibleToPermissions) {
            Bundle permissionBundle = new Bundle();
            permissionBundle.putIntegerArrayList("permission",
                    new ArrayList<>(allRequiredPermissions));
            permissionBundles.add(permissionBundle);
        }
        GlobalSearchSessionPlatformCtsTest.TestServiceConnection serviceConnection =
                bindToHelperService(pkg);
        try {
            ICommandReceiver commandReceiver = serviceConnection.getCommandReceiver();
            assertThat(commandReceiver.indexGloballySearchableDocument(
                    databaseName, namespace, id, permissionBundles)).isTrue();
        } finally {
            serviceConnection.unbind();
        }
    }

    private void indexNotGloballySearchableDocument(
            String pkg, String databaseName, String namespace, String id) throws Exception {
        GlobalSearchSessionPlatformCtsTest.TestServiceConnection serviceConnection =
                bindToHelperService(pkg);
        try {
            ICommandReceiver commandReceiver = serviceConnection.getCommandReceiver();
            assertThat(commandReceiver
                    .indexNotGloballySearchableDocument(databaseName, namespace, id)).isTrue();
        } finally {
            serviceConnection.unbind();
        }
    }

    private void clearData(String pkg, String databaseName) throws Exception {
        GlobalSearchSessionPlatformCtsTest.TestServiceConnection serviceConnection =
                bindToHelperService(pkg);
        try {
            ICommandReceiver commandReceiver = serviceConnection.getCommandReceiver();
            assertThat(commandReceiver.clearData(databaseName)).isTrue();
        } finally {
            serviceConnection.unbind();
        }
    }

    private GlobalSearchSessionPlatformCtsTest.TestServiceConnection bindToHelperService(
            String pkg) {
        GlobalSearchSessionPlatformCtsTest.TestServiceConnection serviceConnection =
                new GlobalSearchSessionPlatformCtsTest.TestServiceConnection(mContext);
        Intent intent = new Intent().setComponent(new ComponentName(pkg, HELPER_SERVICE));
        mContext.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
        return serviceConnection;
    }

    private class TestServiceConnection implements ServiceConnection {
        private final Context mContext;
        private final BlockingQueue<IBinder> mBlockingQueue = new LinkedBlockingQueue<>();
        private ICommandReceiver mCommandReceiver;

        TestServiceConnection(Context context) {
            mContext = context;
        }

        public void onServiceConnected(ComponentName componentName, IBinder service) {
            Log.i(TAG, "Service got connected: " + componentName);
            mBlockingQueue.offer(service);
        }

        public void onServiceDisconnected(ComponentName componentName) {
            Log.e(TAG, "Service got disconnected: " + componentName);
        }

        private IBinder getService() throws Exception {
            IBinder service = mBlockingQueue.poll(TIMEOUT_BIND_SERVICE_SEC, TimeUnit.SECONDS);
            return service;
        }

        public ICommandReceiver getCommandReceiver() throws Exception {
            if (mCommandReceiver == null) {
                mCommandReceiver = ICommandReceiver.Stub.asInterface(getService());
            }
            return mCommandReceiver;
        }

        public void unbind() {
            mCommandReceiver = null;
            mContext.unbindService(this);
        }
    }
}
