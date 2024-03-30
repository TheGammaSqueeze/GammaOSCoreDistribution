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

import static android.Manifest.permission.READ_DEVICE_CONFIG;
import static android.Manifest.permission.WRITE_DEVICE_CONFIG;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

import android.app.UiAutomation;
import android.app.appsearch.AppSearchManager;
import android.app.appsearch.AppSearchResult;
import android.app.appsearch.AppSearchSessionShim;
import android.app.appsearch.GlobalSearchSessionShim;
import android.app.appsearch.SearchSpec;
import android.app.appsearch.SetSchemaRequest;
import android.app.appsearch.observer.DocumentChangeInfo;
import android.app.appsearch.observer.ObserverCallback;
import android.app.appsearch.observer.ObserverSpec;
import android.app.appsearch.observer.SchemaChangeInfo;
import android.app.appsearch.testutil.AppSearchSessionShimImpl;
import android.app.appsearch.testutil.GlobalSearchSessionShimImpl;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.os.CancellationSignal;
import android.os.PersistableBundle;
import android.provider.ContactsContract;
import android.provider.DeviceConfig;
import android.test.ProviderTestCase2;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.server.appsearch.FrameworkAppSearchConfig;
import com.android.server.appsearch.contactsindexer.appsearchtypes.Person;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.annotation.Nullable;

// TODO(b/203605504) this is a junit3 test(ProviderTestCase2) but we run it with junit4 to use
//  some utilities like temporary folder. Right now I can't make ProviderTestRule work so we
//  stick to ProviderTestCase2 for now.
@RunWith(AndroidJUnit4.class)
public class ContactsIndexerUserInstanceTest extends ProviderTestCase2<FakeContactsProvider> {
    @Rule
    public TemporaryFolder mTemporaryFolder = new TemporaryFolder();

    private final ExecutorService mSingleThreadedExecutor = Executors.newSingleThreadExecutor();
    private ContextWrapper mContextWrapper;
    private File mContactsDir;
    private File mSettingsFile;
    private SearchSpec mSpecForQueryAllContacts;
    private ContactsIndexerUserInstance mInstance;
    private ContactsUpdateStats mUpdateStats;
    private ContactsIndexerConfig mConfigForTest = new TestContactsIndexerConfig();

    public ContactsIndexerUserInstanceTest() {
        super(FakeContactsProvider.class, FakeContactsProvider.AUTHORITY);
    }

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();

        // Setup the file path to the persisted data
        mContactsDir = new File(mTemporaryFolder.newFolder(), "appsearch/contacts");
        mSettingsFile = new File(mContactsDir, ContactsIndexerSettings.SETTINGS_FILE_NAME);
        mContextWrapper = new ContextWrapper(ApplicationProvider.getApplicationContext());
        mContextWrapper.setContentResolver(getMockContentResolver());
        mContext = mContextWrapper;
        mSpecForQueryAllContacts = new SearchSpec.Builder().addFilterSchemas(
                Person.SCHEMA_TYPE).addProjection(Person.SCHEMA_TYPE,
                Arrays.asList(Person.PERSON_PROPERTY_NAME))
                .setResultCountPerPage(100)
                .build();

        mInstance = ContactsIndexerUserInstance.createInstance(mContext, mContactsDir,
                mConfigForTest, mSingleThreadedExecutor);
        mUpdateStats = new ContactsUpdateStats();
    }

    @Override
    @After
    public void tearDown() throws Exception {
        // Wipe the data in AppSearchHelper.DATABASE_NAME.
        AppSearchManager.SearchContext searchContext =
                new AppSearchManager.SearchContext.Builder(AppSearchHelper.DATABASE_NAME).build();
        AppSearchSessionShim db = AppSearchSessionShimImpl.createSearchSessionAsync(
                searchContext).get();
        SetSchemaRequest setSchemaRequest = new SetSchemaRequest.Builder()
                .setForceOverride(true).build();
        db.setSchemaAsync(setSchemaRequest).get();
        super.tearDown();
    }

    @Test
    public void testHandleMultipleNotifications_onlyOneDeltaUpdateCanBeScheduledAndRun()
            throws Exception {
        try {
            long dataQueryDelayMs = 5000;
            getProvider().setDataQueryDelayMs(dataQueryDelayMs);
            BlockingQueue<Runnable> queue = new LinkedBlockingQueue<>();
            ThreadPoolExecutor singleThreadedExecutor =
                    new ThreadPoolExecutor(/*corePoolSize=*/1, /*maximumPoolSize=*/
                            1, /*KeepAliveTime=*/ 0L, TimeUnit.MILLISECONDS, queue);
            ContactsIndexerUserInstance instance = ContactsIndexerUserInstance.createInstance(
                    mContext, mContactsDir,
                    mConfigForTest, singleThreadedExecutor);

            int numOfNotifications = 20;
            for (int i = 0; i < numOfNotifications / 2; ++i) {
                int docCount = 2;
                // Insert contacts to trigger delta update.
                ContentResolver resolver = mContext.getContentResolver();
                ContentValues dummyValues = new ContentValues();
                for (int j = 0; j < docCount; j++) {
                    resolver.insert(ContactsContract.Contacts.CONTENT_URI, dummyValues);
                }
                instance.handleDeltaUpdate();
            }
            // Sleep here so the active delta update can be run for some time. While
            // notifications come before and afterwards.
            Thread.sleep(1500);
            long totalTaskAfterFirstDeltaUpdate = singleThreadedExecutor.getTaskCount();
            for (int i = 0; i < numOfNotifications / 2; ++i) {
                int docCount = 2;
                // Insert contacts to trigger delta update.
                ContentResolver resolver = mContext.getContentResolver();
                ContentValues dummyValues = new ContentValues();
                for (int j = 0; j < docCount; j++) {
                    resolver.insert(ContactsContract.Contacts.CONTENT_URI, dummyValues);
                }
                instance.handleDeltaUpdate();
            }

            // The total task count will be increased if there is another delta update scheduled.
            assertThat(singleThreadedExecutor.getTaskCount()).isEqualTo(
                    totalTaskAfterFirstDeltaUpdate);
        } finally {
            getProvider().setDataQueryDelayMs(0);
        }
    }

    @Test
    public void testHandleNotificationDuringUpdate_oneAdditionalUpdateWillBeRun()
            throws Exception {
        try {
            long dataQueryDelayMs = 5000;
            getProvider().setDataQueryDelayMs(dataQueryDelayMs);
            BlockingQueue<Runnable> queue = new LinkedBlockingQueue<>();
            ThreadPoolExecutor singleThreadedExecutor =
                    new ThreadPoolExecutor(/*corePoolSize=*/1, /*maximumPoolSize=*/
                            1, /*KeepAliveTime=*/ 0L, TimeUnit.MILLISECONDS, queue);
            ContactsIndexerUserInstance instance = ContactsIndexerUserInstance.createInstance(
                    mContext, mContactsDir,
                    mConfigForTest, singleThreadedExecutor);
            int docCount = 10;
            // Insert contacts to trigger delta update.
            ContentResolver resolver = mContext.getContentResolver();
            ContentValues dummyValues = new ContentValues();

            for (int j = 0; j < docCount; j++) {
                resolver.insert(ContactsContract.Contacts.CONTENT_URI, dummyValues);
            }
            instance.handleDeltaUpdate();
            // Sleep here so the active delta update can be run for some time to make sure
            // notification come during an update.
            Thread.sleep(1500);
            long totalTaskAfterFirstDeltaUpdate = singleThreadedExecutor.getTaskCount();
            // Insert contacts to trigger delta update.
            for (int j = 0; j < docCount; j++) {
                resolver.insert(ContactsContract.Contacts.CONTENT_URI, dummyValues);
            }
            instance.handleDeltaUpdate();

            //The 2nd update won't be scheduled right away.
            assertThat(singleThreadedExecutor.getTaskCount()).isEqualTo(
                    totalTaskAfterFirstDeltaUpdate);

            // To make sure the 1st update has been finished.
            Thread.sleep(dataQueryDelayMs);

            // This means additional task has been run by the 1st delta update to handle
            // the change notification.
            assertThat(singleThreadedExecutor.getActiveCount()).isEqualTo(1);
        } finally {
            getProvider().setDataQueryDelayMs(0);
        }
    }

    @Test
    public void testCreateInstance_dataDirectoryCreatedAsynchronously() throws Exception {
        File dataDir = new File(mTemporaryFolder.newFolder(), "contacts");
        boolean isDataDirectoryCreatedSynchronously = mSingleThreadedExecutor.submit(() -> {
            ContactsIndexerUserInstance unused =
                    ContactsIndexerUserInstance.createInstance(mContext, dataDir, mConfigForTest,
                            mSingleThreadedExecutor);
            // Data directory shouldn't have been created synchronously in createInstance()
            return dataDir.exists();
        }).get();
        assertFalse(isDataDirectoryCreatedSynchronously);
        boolean isDataDirectoryCreatedAsynchronously = mSingleThreadedExecutor.submit(
                dataDir::exists).get();
        assertTrue(isDataDirectoryCreatedAsynchronously);
    }

    @Test
    public void testStart_initialRun_schedulesFullUpdateJob() throws Exception {
        JobScheduler mockJobScheduler = mock(JobScheduler.class);
        mContextWrapper.setJobScheduler(mockJobScheduler);
        ContactsIndexerUserInstance instance = ContactsIndexerUserInstance.createInstance(
                mContext,
                mContactsDir, mConfigForTest, mSingleThreadedExecutor);

        int docCount = 100;
        CountDownLatch latch = new CountDownLatch(docCount);
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
        // Insert contacts to trigger delta update.
        ContentResolver resolver = mContext.getContentResolver();
        ContentValues dummyValues = new ContentValues();
        for (int i = 0; i < docCount; i++) {
            resolver.insert(ContactsContract.Contacts.CONTENT_URI, dummyValues);
        }

        instance.startAsync();

        // Wait for all async tasks to complete
        latch.await(30L, TimeUnit.SECONDS);

        ArgumentCaptor<JobInfo> jobInfoArgumentCaptor = ArgumentCaptor.forClass(JobInfo.class);
        verify(mockJobScheduler).schedule(jobInfoArgumentCaptor.capture());
        JobInfo fullUpdateJob = jobInfoArgumentCaptor.getValue();
        assertThat(fullUpdateJob.isRequireBatteryNotLow()).isTrue();
        assertThat(fullUpdateJob.isRequireDeviceIdle()).isTrue();
        assertThat(fullUpdateJob.isPersisted()).isTrue();
        assertThat(fullUpdateJob.isPeriodic()).isFalse();
    }

    @Test
    public void testStart_subsequentRun_doesNotScheduleFullUpdateJob() throws Exception {
        executeAndWaitForCompletion(
                mInstance.doFullUpdateInternalAsync(new CancellationSignal(), mUpdateStats),
                mSingleThreadedExecutor);

        JobScheduler mockJobScheduler = mock(JobScheduler.class);
        mContextWrapper.setJobScheduler(mockJobScheduler);
        ContactsIndexerUserInstance instance = ContactsIndexerUserInstance.createInstance(
                mContext, mContactsDir, mConfigForTest, mSingleThreadedExecutor);

        int docCount = 100;
        CountDownLatch latch = new CountDownLatch(docCount);
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
        // Insert contacts to trigger delta update.
        ContentResolver resolver = mContext.getContentResolver();
        ContentValues dummyValues = new ContentValues();
        for (int i = 0; i < docCount; i++) {
            resolver.insert(ContactsContract.Contacts.CONTENT_URI, dummyValues);
        }

        instance.startAsync();

        // Wait for all async tasks to complete
        mSingleThreadedExecutor.submit(() -> {
        }).get();

        verifyZeroInteractions(mockJobScheduler);
    }

    @Test
    public void testFullUpdate() throws Exception {
        ContentResolver resolver = mContext.getContentResolver();
        ContentValues dummyValues = new ContentValues();
        for (int i = 0; i < 500; i++) {
            resolver.insert(ContactsContract.Contacts.CONTENT_URI, dummyValues);
        }

        executeAndWaitForCompletion(
                mInstance.doFullUpdateInternalAsync(new CancellationSignal(), mUpdateStats),
                mSingleThreadedExecutor);

        AppSearchHelper searchHelper = AppSearchHelper.createAppSearchHelper(mContext,
                mSingleThreadedExecutor);
        List<String> contactIds = searchHelper.getAllContactIdsAsync().get();
        assertThat(contactIds.size()).isEqualTo(500);
    }

    @Test
    public void testDeltaUpdate_insertedContacts() throws Exception {
        long timeBeforeDeltaChangeNotification = System.currentTimeMillis();
        // Insert contacts to trigger delta update.
        ContentResolver resolver = mContext.getContentResolver();
        ContentValues dummyValues = new ContentValues();
        for (int i = 0; i < 250; i++) {
            resolver.insert(ContactsContract.Contacts.CONTENT_URI, dummyValues);
        }

        executeAndWaitForCompletion(mInstance.doDeltaUpdateAsync(/*indexingLimit=*/ -1,
                mUpdateStats),
                mSingleThreadedExecutor);

        AppSearchHelper searchHelper = AppSearchHelper.createAppSearchHelper(mContext,
                mSingleThreadedExecutor);
        List<String> contactIds = searchHelper.getAllContactIdsAsync().get();
        assertThat(contactIds.size()).isEqualTo(250);

        PersistableBundle settingsBundle = ContactsIndexerSettings.readBundle(mSettingsFile);
        assertThat(settingsBundle.getLong(ContactsIndexerSettings.LAST_DELTA_UPDATE_TIMESTAMP_KEY))
                .isAtLeast(timeBeforeDeltaChangeNotification);
        // check stats
        assertThat(mUpdateStats.mUpdateType).isEqualTo(ContactsUpdateStats.DELTA_UPDATE);
        assertThat(mUpdateStats.mUpdateStatuses).hasSize(1);
        assertThat(mUpdateStats.mUpdateStatuses).containsExactly(AppSearchResult.RESULT_OK);
        assertThat(mUpdateStats.mDeleteStatuses).hasSize(1);
        assertThat(mUpdateStats.mDeleteStatuses).containsExactly(AppSearchResult.RESULT_OK);
        assertThat(mUpdateStats.mContactsUpdateFailedCount).isEqualTo(0);
        // NOT_FOUND does not count as error.
        assertThat(mUpdateStats.mContactsDeleteFailedCount).isEqualTo(0);
        assertThat(mUpdateStats.mNewContactsToBeUpdated).isEqualTo(250);
        assertThat(mUpdateStats.mContactsUpdateSkippedCount).isEqualTo(0);
        assertThat(mUpdateStats.mTotalContactsToBeUpdated).isEqualTo(250);
        assertThat(mUpdateStats.mContactsUpdateSucceededCount).isEqualTo(250);
        assertThat(mUpdateStats.mTotalContactsToBeDeleted).isEqualTo(0);
        assertThat(mUpdateStats.mContactsDeleteSucceededCount).isEqualTo(0);
    }

    @Test
    public void testDeltaUpdateWithLimit_fewerContactsIndexed() throws Exception {
        // Insert contacts to trigger delta update.
        ContentResolver resolver = mContext.getContentResolver();
        ContentValues dummyValues = new ContentValues();
        for (int i = 0; i < 250; i++) {
            resolver.insert(ContactsContract.Contacts.CONTENT_URI, dummyValues);
        }

        executeAndWaitForCompletion(mInstance.doDeltaUpdateAsync(/*indexingLimit=*/ 100,
                mUpdateStats),
                mSingleThreadedExecutor);

        AppSearchHelper searchHelper = AppSearchHelper.createAppSearchHelper(mContext,
                mSingleThreadedExecutor);
        List<String> contactIds = searchHelper.getAllContactIdsAsync().get();
        assertThat(contactIds.size()).isEqualTo(100);
    }

    @Test
    public void testDeltaUpdate_deletedContacts() throws Exception {
        long timeBeforeDeltaChangeNotification = System.currentTimeMillis();
        // Insert contacts to trigger delta update.
        ContentResolver resolver = mContext.getContentResolver();
        ContentValues dummyValues = new ContentValues();
        for (int i = 0; i < 10; i++) {
            resolver.insert(ContactsContract.Contacts.CONTENT_URI, dummyValues);
        }

        executeAndWaitForCompletion(mInstance.doDeltaUpdateAsync(/*indexingLimit=*/ -1,
                mUpdateStats),
                mSingleThreadedExecutor);

        // Delete a few contacts to trigger delta update.
        resolver.delete(ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, 2),
                /*extras=*/ null);
        resolver.delete(ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, 3),
                /*extras=*/ null);
        resolver.delete(ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, 5),
                /*extras=*/ null);
        resolver.delete(ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, 7),
                /*extras=*/ null);

        executeAndWaitForCompletion(mInstance.doDeltaUpdateAsync(/*indexingLimit=*/ -1,
                mUpdateStats),
                mSingleThreadedExecutor);

        AppSearchHelper searchHelper = AppSearchHelper.createAppSearchHelper(mContext,
                mSingleThreadedExecutor);
        List<String> contactIds = searchHelper.getAllContactIdsAsync().get();
        assertThat(contactIds.size()).isEqualTo(6);
        assertThat(contactIds).containsNoneOf("2", "3", "5", "7");

        PersistableBundle settingsBundle = ContactsIndexerSettings.readBundle(mSettingsFile);
        assertThat(settingsBundle.getLong(ContactsIndexerSettings.LAST_DELTA_UPDATE_TIMESTAMP_KEY))
                .isAtLeast(timeBeforeDeltaChangeNotification);
        // check stats
        assertThat(mUpdateStats.mUpdateType).isEqualTo(ContactsUpdateStats.DELTA_UPDATE);
        assertThat(mUpdateStats.mUpdateStatuses).hasSize(1);
        assertThat(mUpdateStats.mUpdateStatuses).containsExactly(AppSearchResult.RESULT_OK);
        assertThat(mUpdateStats.mDeleteStatuses).hasSize(1);
        assertThat(mUpdateStats.mDeleteStatuses).containsExactly(AppSearchResult.RESULT_OK);
        assertThat(mUpdateStats.mContactsUpdateFailedCount).isEqualTo(0);
        assertThat(mUpdateStats.mContactsDeleteFailedCount).isEqualTo(0);
        assertThat(mUpdateStats.mNewContactsToBeUpdated).isEqualTo(10);
        assertThat(mUpdateStats.mContactsUpdateSkippedCount).isEqualTo(0);
        assertThat(mUpdateStats.mTotalContactsToBeUpdated).isEqualTo(10);
        assertThat(mUpdateStats.mContactsUpdateSucceededCount).isEqualTo(10);
        assertThat(mUpdateStats.mTotalContactsToBeDeleted).isEqualTo(4);
        assertThat(mUpdateStats.mContactsDeleteSucceededCount).isEqualTo(4);
    }

    @Test
    public void testDeltaUpdate_insertedAndDeletedContacts() throws Exception {
        long timeBeforeDeltaChangeNotification = System.currentTimeMillis();
        // Insert contacts to trigger delta update.
        ContentResolver resolver = mContext.getContentResolver();
        ContentValues dummyValues = new ContentValues();
        for (int i = 0; i < 10; i++) {
            resolver.insert(ContactsContract.Contacts.CONTENT_URI, dummyValues);
        }

        // Delete a few contacts to trigger delta update.
        resolver.delete(ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, 2),
                /*extras=*/ null);
        resolver.delete(ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, 3),
                /*extras=*/ null);
        resolver.delete(ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, 5),
                /*extras=*/ null);
        resolver.delete(ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, 7),
                /*extras=*/ null);

        mUpdateStats.clear();
        executeAndWaitForCompletion(mInstance.doDeltaUpdateAsync(/*indexingLimit=*/ -1,
                mUpdateStats),
                mSingleThreadedExecutor);

        AppSearchHelper searchHelper = AppSearchHelper.createAppSearchHelper(mContext,
                mSingleThreadedExecutor);
        List<String> contactIds = searchHelper.getAllContactIdsAsync().get();
        assertThat(contactIds.size()).isEqualTo(6);
        assertThat(contactIds).containsNoneOf("2", "3", "5", "7");

        PersistableBundle settingsBundle = ContactsIndexerSettings.readBundle(mSettingsFile);
        assertThat(settingsBundle.getLong(ContactsIndexerSettings.LAST_DELTA_UPDATE_TIMESTAMP_KEY))
                .isAtLeast(timeBeforeDeltaChangeNotification);
        assertThat(settingsBundle.getLong(ContactsIndexerSettings.LAST_DELTA_DELETE_TIMESTAMP_KEY))
                .isAtLeast(timeBeforeDeltaChangeNotification);
        // check stats
        assertThat(mUpdateStats.mUpdateType).isEqualTo(ContactsUpdateStats.DELTA_UPDATE);
        assertThat(mUpdateStats.mUpdateStatuses).hasSize(1);
        assertThat(mUpdateStats.mUpdateStatuses).containsExactly(AppSearchResult.RESULT_OK);
        assertThat(mUpdateStats.mDeleteStatuses).hasSize(1);
        assertThat(mUpdateStats.mDeleteStatuses).containsExactly(AppSearchResult.RESULT_OK);
        assertThat(mUpdateStats.mContactsUpdateFailedCount).isEqualTo(0);
        // 4 contacts deleted in CP2, but we don't have those in AppSearch. So we will get
        // NOT_FOUND. We don't treat the NOT_FOUND as failures, so the status code is still OK.
        assertThat(mUpdateStats.mContactsDeleteFailedCount).isEqualTo(4);
        assertThat(mUpdateStats.mNewContactsToBeUpdated).isEqualTo(6);
        assertThat(mUpdateStats.mContactsUpdateSkippedCount).isEqualTo(0);
        assertThat(mUpdateStats.mTotalContactsToBeUpdated).isEqualTo(6);
        assertThat(mUpdateStats.mContactsUpdateSucceededCount).isEqualTo(6);
        assertThat(mUpdateStats.mTotalContactsToBeDeleted).isEqualTo(4);
        assertThat(mUpdateStats.mContactsDeleteSucceededCount).isEqualTo(0);
    }

    @Test
    public void testDeltaUpdate_insertedAndDeletedContacts_withDeletionSucceed() throws Exception {
        ContentResolver resolver = mContext.getContentResolver();
        ContentValues dummyValues = new ContentValues();
        // Index 10 documents before testing.
        for (int i = 0; i < 10; i++) {
            resolver.insert(ContactsContract.Contacts.CONTENT_URI, dummyValues);
        }
        executeAndWaitForCompletion(
                mInstance.doFullUpdateInternalAsync(new CancellationSignal(), mUpdateStats),
                mSingleThreadedExecutor);

        long timeBeforeDeltaChangeNotification = System.currentTimeMillis();
        // Insert additional 5 contacts to trigger delta update.
        for (int i = 0; i < 5; i++) {
            resolver.insert(ContactsContract.Contacts.CONTENT_URI, dummyValues);
        }
        // Delete a few contacts to trigger delta update.
        resolver.delete(ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, 2),
                /*extras=*/ null);
        resolver.delete(ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, 3),
                /*extras=*/ null);
        resolver.delete(ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, 5),
                /*extras=*/ null);
        resolver.delete(ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, 7),
                /*extras=*/ null);

        mUpdateStats.clear();
        executeAndWaitForCompletion(mInstance.doDeltaUpdateAsync(/*indexingLimit=*/ -1,
                mUpdateStats),
                mSingleThreadedExecutor);

        AppSearchHelper searchHelper = AppSearchHelper.createAppSearchHelper(mContext,
                mSingleThreadedExecutor);
        List<String> contactIds = searchHelper.getAllContactIdsAsync().get();
        assertThat(contactIds.size()).isEqualTo(11);
        assertThat(contactIds).containsNoneOf("2", "3", "5", "7");

        PersistableBundle settingsBundle = ContactsIndexerSettings.readBundle(mSettingsFile);
        assertThat(settingsBundle.getLong(ContactsIndexerSettings.LAST_DELTA_UPDATE_TIMESTAMP_KEY))
                .isAtLeast(timeBeforeDeltaChangeNotification);
        assertThat(settingsBundle.getLong(ContactsIndexerSettings.LAST_DELTA_DELETE_TIMESTAMP_KEY))
                .isAtLeast(timeBeforeDeltaChangeNotification);
        // check stats
        assertThat(mUpdateStats.mUpdateType).isEqualTo(ContactsUpdateStats.DELTA_UPDATE);
        assertThat(mUpdateStats.mUpdateStatuses).hasSize(1);
        assertThat(mUpdateStats.mUpdateStatuses).containsExactly(AppSearchResult.RESULT_OK);
        assertThat(mUpdateStats.mDeleteStatuses).hasSize(1);
        assertThat(mUpdateStats.mDeleteStatuses).containsExactly(AppSearchResult.RESULT_OK);
        assertThat(mUpdateStats.mContactsUpdateFailedCount).isEqualTo(0);
        // NOT_FOUND does not count as error.
        assertThat(mUpdateStats.mContactsDeleteFailedCount).isEqualTo(0);
        assertThat(mUpdateStats.mNewContactsToBeUpdated).isEqualTo(5);
        assertThat(mUpdateStats.mContactsUpdateSkippedCount).isEqualTo(0);
        assertThat(mUpdateStats.mTotalContactsToBeUpdated).isEqualTo(5);
        assertThat(mUpdateStats.mContactsUpdateSucceededCount).isEqualTo(5);
        assertThat(mUpdateStats.mTotalContactsToBeDeleted).isEqualTo(4);
        assertThat(mUpdateStats.mContactsDeleteSucceededCount).isEqualTo(4);
    }

    // TODO(b/243542728) This tests whether a full update job will be run to prune the person
    //  corpus when AppSearch reaches its max document limit. So we do want to change the device
    //  config on the system to make the max document limit 100. And after we try to index 250
    //  documents, the full update can be triggered. Disable this test for now as the asked
    //  permissions are always denied.
    @Ignore
    @Test
    public void testDeltaUpdate_outOfSpaceError_fullUpdateScheduled() throws Exception {
        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        int maxDocumentCountBeforeTest = -1;
        try {
            uiAutomation.adoptShellPermissionIdentity(READ_DEVICE_CONFIG, WRITE_DEVICE_CONFIG);
            maxDocumentCountBeforeTest = FrameworkAppSearchConfig.getInstance(
                    mSingleThreadedExecutor).getCachedLimitConfigMaxDocumentCount();
            int totalContactCount = 250;
            int maxDocumentCount = 100;
            // Override the configs in AppSearch. This is hard to be mocked since we are not testing
            // AppSearch here.
            DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                    FrameworkAppSearchConfig.KEY_LIMIT_CONFIG_MAX_DOCUMENT_COUNT,
                    String.valueOf(maxDocumentCount), false);
            // Cancel any existing jobs.
            ContactsIndexerMaintenanceService.cancelFullUpdateJob(mContext, mContext.getUserId());

            JobScheduler mockJobScheduler = mock(JobScheduler.class);
            mContextWrapper.setJobScheduler(mockJobScheduler);

            // We are trying to index 250 contacts, but our max documentCount is 100. So we would
            // index 100 contacts, reach the limit, and trigger a full update.
            CountDownLatch latch = new CountDownLatch(maxDocumentCount);
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

            long timeBeforeDeltaChangeNotification = System.currentTimeMillis();
            // Insert contacts to trigger delta update.
            ContentResolver resolver = mContext.getContentResolver();
            ContentValues dummyValues = new ContentValues();
            for (int i = 0; i < totalContactCount; i++) {
                resolver.insert(ContactsContract.Contacts.CONTENT_URI, dummyValues);
            }

            executeAndWaitForCompletion(
                    mInstance.doDeltaUpdateAsync(ContactsProviderUtil.UPDATE_LIMIT_NONE,
                            mUpdateStats),
                    mSingleThreadedExecutor);
            latch.await(30L, TimeUnit.SECONDS);

            // Verify the full update job is scheduled due to out_of_space.
            verify(mockJobScheduler).schedule(any());
            PersistableBundle settingsBundle = ContactsIndexerSettings.readBundle(mSettingsFile);
            assertThat(
                    settingsBundle.getLong(ContactsIndexerSettings.LAST_DELTA_UPDATE_TIMESTAMP_KEY))
                    .isAtLeast(timeBeforeDeltaChangeNotification);
        } finally {
            if (maxDocumentCountBeforeTest > 0) {
                DeviceConfig.setProperty(DeviceConfig.NAMESPACE_APPSEARCH,
                        FrameworkAppSearchConfig.KEY_LIMIT_CONFIG_MAX_DOCUMENT_COUNT,
                        String.valueOf(maxDocumentCountBeforeTest), false);
            }
            uiAutomation.dropShellPermissionIdentity();
        }
    }

    @Test
    public void testDeltaUpdate_notTriggered_afterCompatibleSchemaChange() throws Exception {
        long timeAtBeginning = System.currentTimeMillis();

        // Configure the timestamps to non-zero on disk.
        PersistableBundle settingsBundle = new PersistableBundle();
        settingsBundle.putLong(ContactsIndexerSettings.LAST_FULL_UPDATE_TIMESTAMP_KEY,
                timeAtBeginning);
        settingsBundle.putLong(ContactsIndexerSettings.LAST_DELTA_UPDATE_TIMESTAMP_KEY,
                timeAtBeginning);
        settingsBundle.putLong(ContactsIndexerSettings.LAST_DELTA_DELETE_TIMESTAMP_KEY,
                timeAtBeginning);
        mSettingsFile.getParentFile().mkdirs();
        mSettingsFile.createNewFile();
        ContactsIndexerSettings.writeBundle(mSettingsFile, settingsBundle);
        // Preset a compatible schema.
        AppSearchManager.SearchContext searchContext =
                new AppSearchManager.SearchContext.Builder(AppSearchHelper.DATABASE_NAME).build();
        AppSearchSessionShim db = AppSearchSessionShimImpl.createSearchSessionAsync(
                searchContext).get();
        SetSchemaRequest setSchemaRequest = new SetSchemaRequest.Builder()
                .addSchemas(TestUtils.CONTACT_POINT_SCHEMA_WITH_APP_IDS_OPTIONAL, Person.SCHEMA)
                .setForceOverride(true).build();
        db.setSchemaAsync(setSchemaRequest).get();

        // Since the current schema is compatible, this won't trigger any delta update and
        // schedule a full update job.
        JobScheduler mockJobScheduler = mock(JobScheduler.class);
        mContextWrapper.setJobScheduler(mockJobScheduler);
        mInstance = ContactsIndexerUserInstance.createInstance(mContext, mContactsDir,
                mConfigForTest, mSingleThreadedExecutor);
        mInstance.startAsync();

        verifyZeroInteractions(mockJobScheduler);
    }

    @Test
    public void testDeltaUpdate_triggered_afterIncompatibleSchemaChange() throws Exception {
        long timeBeforeDeltaChangeNotification = System.currentTimeMillis();

        // Configure the timestamps to non-zero on disk.
        PersistableBundle settingsBundle = new PersistableBundle();
        settingsBundle.putLong(ContactsIndexerSettings.LAST_FULL_UPDATE_TIMESTAMP_KEY,
                timeBeforeDeltaChangeNotification);
        settingsBundle.putLong(ContactsIndexerSettings.LAST_DELTA_UPDATE_TIMESTAMP_KEY,
                timeBeforeDeltaChangeNotification);
        settingsBundle.putLong(ContactsIndexerSettings.LAST_DELTA_DELETE_TIMESTAMP_KEY,
                timeBeforeDeltaChangeNotification);
        mSettingsFile.getParentFile().mkdirs();
        mSettingsFile.createNewFile();
        ContactsIndexerSettings.writeBundle(mSettingsFile, settingsBundle);
        // Insert contacts
        int docCount = 250;
        ContentResolver resolver = mContext.getContentResolver();
        ContentValues dummyValues = new ContentValues();
        for (int i = 0; i < docCount; i++) {
            resolver.insert(ContactsContract.Contacts.CONTENT_URI, dummyValues);
        }
        // Preset an incompatible schema.
        AppSearchManager.SearchContext searchContext =
                new AppSearchManager.SearchContext.Builder(AppSearchHelper.DATABASE_NAME).build();
        AppSearchSessionShim db = AppSearchSessionShimImpl.createSearchSessionAsync(
                searchContext).get();
        SetSchemaRequest setSchemaRequest = new SetSchemaRequest.Builder()
                .addSchemas(TestUtils.CONTACT_POINT_SCHEMA_WITH_LABEL_REPEATED, Person.SCHEMA)
                .setForceOverride(true).build();
        db.setSchemaAsync(setSchemaRequest).get();
        // Setup a latch
        CountDownLatch latch = new CountDownLatch(docCount);
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

        // Since the current schema is incompatible, this will trigger two setSchemas, and run do
        // doCp2SyncFirstRun again.
        JobScheduler mockJobScheduler = mock(JobScheduler.class);
        mContextWrapper.setJobScheduler(mockJobScheduler);
        mInstance = ContactsIndexerUserInstance.createInstance(mContext, mContactsDir,
                mConfigForTest, mSingleThreadedExecutor);
        mInstance.startAsync();
        latch.await(30L, TimeUnit.SECONDS);

        verify(mockJobScheduler).schedule(any());
    }

    /**
     * Executes given {@link CompletionStage} on the {@code executor} and waits for its completion.
     *
     * <p>There are 2 steps in this implementation. The first step is to execute the stage on the
     * executor, and wait for its execution. The second step is to wait for the completion of the
     * stage itself.
     */
    private <T> T executeAndWaitForCompletion(CompletionStage<T> stage, ExecutorService executor)
            throws Exception {
        AtomicReference<CompletableFuture<T>> future = new AtomicReference<>(
                CompletableFuture.completedFuture(null));
        executor.submit(() -> {
            // Chain the given stage inside the runnable task so that it executes on the executor.
            CompletableFuture<T> chainedFuture = future.get().thenCompose(x -> stage);
            future.set(chainedFuture);
        }).get();
        // Wait for the task to complete on the executor, and wait for the stage to complete also.
        return future.get().get();
    }

    static final class ContextWrapper extends android.content.ContextWrapper {

        @Nullable ContentResolver mResolver;
        @Nullable JobScheduler mScheduler;

        public ContextWrapper(Context base) {
            super(base);
        }

        @Override
        public Context getApplicationContext() {
            return this;
        }

        @Override
        public ContentResolver getContentResolver() {
            if (mResolver != null) {
                return mResolver;
            }
            return getBaseContext().getContentResolver();
        }

        @Override
        @Nullable
        public Object getSystemService(String name) {
            if (mScheduler != null && Context.JOB_SCHEDULER_SERVICE.equals(name)) {
                return mScheduler;
            }
            return getBaseContext().getSystemService(name);
        }

        public void setContentResolver(ContentResolver resolver) {
            mResolver = resolver;
        }

        public void setJobScheduler(JobScheduler scheduler) {
            mScheduler = scheduler;
        }
    }
}
