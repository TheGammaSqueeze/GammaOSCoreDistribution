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

package com.android.server.appsearch.contactsindexer;

import static android.Manifest.permission.RECEIVE_BOOT_COMPLETED;

import static com.android.server.appsearch.contactsindexer.ContactsIndexerMaintenanceService.MIN_INDEXER_JOB_ID;

import static com.google.common.truth.Truth.assertThat;

import android.app.UiAutomation;
import android.app.appsearch.AppSearchManager;
import android.app.appsearch.AppSearchSessionShim;
import android.app.appsearch.GlobalSearchSessionShim;
import android.app.appsearch.SetSchemaRequest;
import android.app.appsearch.observer.DocumentChangeInfo;
import android.app.appsearch.observer.ObserverCallback;
import android.app.appsearch.observer.ObserverSpec;
import android.app.appsearch.observer.SchemaChangeInfo;
import android.app.appsearch.testutil.AppSearchSessionShimImpl;
import android.app.appsearch.testutil.GlobalSearchSessionShimImpl;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.pm.UserInfo;
import android.os.UserHandle;
import android.provider.ContactsContract;
import android.test.ProviderTestCase2;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import static android.Manifest.permission.INTERACT_ACROSS_USERS_FULL;
import static android.Manifest.permission.READ_DEVICE_CONFIG;

import com.android.compatibility.common.util.SystemUtil;
import com.android.server.SystemService;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public class ContactsIndexerManagerServiceTest extends ProviderTestCase2<FakeContactsProvider> {

    private static final String TAG = "ContactsIndexerManagerServiceTest";

    private final ExecutorService mSingleThreadedExecutor = Executors.newSingleThreadExecutor();

    private ContactsIndexerManagerService mContactsIndexerManagerService;
    private UiAutomation mUiAutomation;

    public ContactsIndexerManagerServiceTest() {
        super(FakeContactsProvider.class, FakeContactsProvider.AUTHORITY);
    }

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        Context context = ApplicationProvider.getApplicationContext();
        mUiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        mContext = new ContextWrapper(context) {

            @Override
            public Context getApplicationContext() {
                return this;
            }

            @Override
            public Context createContextAsUser(@NonNull UserHandle user, int flags) {
                return this;
            }

            @Override
            public ContentResolver getContentResolver() {
                return getMockContentResolver();
            }
        };
        // INTERACT_ACROSS_USERS_FULL: needed when we do registerReceiverForAllUsers for getting
        // package change notifications.
        mUiAutomation.adoptShellPermissionIdentity(INTERACT_ACROSS_USERS_FULL);
        mContactsIndexerManagerService = new ContactsIndexerManagerService(mContext,
                new TestContactsIndexerConfig());
        mContactsIndexerManagerService.onStart();
    }

    @Override
    @After
    public void tearDown() throws Exception {
        // Wipe the data in AppSearchHelper.DATABASE_NAME.
        AppSearchSessionShim db = AppSearchSessionShimImpl.createSearchSessionAsync(mContext,
                new AppSearchManager.SearchContext.Builder(AppSearchHelper.DATABASE_NAME).build(),
                mSingleThreadedExecutor).get();
        db.setSchemaAsync(new SetSchemaRequest.Builder().setForceOverride(true).build()).get();
        mUiAutomation.dropShellPermissionIdentity();
        super.tearDown();
    }

    @Test
    public void testCP2Clear_runsFullUpdate() throws Exception {
        int userId = mContext.getUser().getIdentifier();

        // Populate fake CP2 with 100 contacts.
        ContentResolver resolver = mContext.getContentResolver();
        ContentValues values = new ContentValues();
        for (int i = 0; i < 100; i++) {
            resolver.insert(ContactsContract.Contacts.CONTENT_URI, values);
        }

        // Contacts indexer schedules a full-update job for bootstrapping from CP2,
        // and JobScheduler API requires BOOT_COMPLETED permission for persisting the job.
        mUiAutomation.adoptShellPermissionIdentity(RECEIVE_BOOT_COMPLETED);
        try {
            CountDownLatch bootstrapLatch = countDownAppSearchDocumentChanges(100);
            UserInfo userInfo = new UserInfo(mContext.getUser().getIdentifier(),
                    /*name=*/ "default", /*flags=*/ 0);
            mContactsIndexerManagerService.onUserUnlocking(new SystemService.TargetUser(userInfo));
            bootstrapLatch.await(30L, TimeUnit.SECONDS);
        } finally {
            mUiAutomation.dropShellPermissionIdentity();
        }

        // Clear fake CP2.
        for (int i = 0; i < 100; i++) {
            resolver.delete(ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, i),
                    /*extras=*/ null);
        }
        CountDownLatch fullUpdateLatch = countDownAppSearchDocumentChanges(100);
        SystemUtil.runShellCommand("pm clear --user " + userId + " com.android.providers.contacts");
        // Wait for full-update to run and delete all 100 contacts.
        fullUpdateLatch.await(30L, TimeUnit.SECONDS);
        // Verify that a periodic full-update job is scheduled still.
        assertThat(getJobState(MIN_INDEXER_JOB_ID + userId)).contains("waiting");
    }

    /**
     * Returns a latch to count down the given number of document changes in the Person corpus.
     *
     * <p>The latch counts down to 0, and can be used to wait until the expected number of document
     * changes have occurred.
     */
    @NonNull
    private CountDownLatch countDownAppSearchDocumentChanges(int numChanges) throws Exception {
        CountDownLatch latch = new CountDownLatch(numChanges);
        GlobalSearchSessionShim shim =
                GlobalSearchSessionShimImpl.createGlobalSearchSessionAsync(mContext).get();
        ObserverCallback callback = new ObserverCallback() {
            @Override
            public void onSchemaChanged(SchemaChangeInfo changeInfo) {
                // Do nothing
            }

            @Override
            public void onDocumentChanged(DocumentChangeInfo changeInfo) {
                for (int i = 0; i < changeInfo.getChangedDocumentIds().size(); i++) {
                    latch.countDown();
                }
            }
        };
        shim.registerObserverCallback(mContext.getPackageName(),
                new ObserverSpec.Builder().addFilterSchemas("builtin:Person").build(),
                mSingleThreadedExecutor,
                callback);
        return latch;
    }

    /**
     * Returns the current state of a job, which may be "pending", "active", "ready", or "waiting".
     *
     * <p>See "adb shell cmd jobscheduler -h" for more details.
     */
    @NonNull
    private String getJobState(int jobId) throws Exception {
        return SystemUtil.runShellCommand(mUiAutomation,
                "cmd jobscheduler get-job-state android " + jobId).trim();
    }
}
