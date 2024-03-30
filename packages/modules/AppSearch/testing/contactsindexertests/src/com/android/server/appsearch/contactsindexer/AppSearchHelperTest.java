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

import static com.google.common.truth.Truth.assertThat;

import android.app.appsearch.AppSearchManager;
import android.app.appsearch.AppSearchResult;
import android.app.appsearch.AppSearchSession;
import android.app.appsearch.AppSearchSessionShim;
import android.app.appsearch.GetSchemaResponse;
import android.app.appsearch.SetSchemaRequest;
import android.app.appsearch.testutil.AppSearchSessionShimImpl;
import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.android.server.appsearch.contactsindexer.appsearchtypes.ContactPoint;
import com.android.server.appsearch.contactsindexer.appsearchtypes.Person;

import com.google.common.collect.ImmutableSet;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

// Since AppSearchHelper mainly just calls AppSearch's api to index/remove files, we shouldn't
// worry too much about it since AppSearch has good test coverage. Here just add some simple checks.
public class AppSearchHelperTest {

    private final Executor mSingleThreadedExecutor = Executors.newSingleThreadExecutor();

    private Context mContext;
    private AppSearchHelper mAppSearchHelper;
    private ContactsUpdateStats mUpdateStats;

    @Before
    public void setUp() throws Exception {
        mContext = ApplicationProvider.getApplicationContext();
        mAppSearchHelper = AppSearchHelper.createAppSearchHelper(mContext, mSingleThreadedExecutor);
        mUpdateStats = new ContactsUpdateStats();
    }

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
    }

    @Test
    public void testAppSearchHelper_permissionIsSetCorrectlyForPerson() throws Exception {
        // TODO(b/203605504) We can create AppSearchHelper in the test itself so make things more
        //  clear.
        AppSearchSession session = mAppSearchHelper.getSession();
        CompletableFuture<AppSearchResult<GetSchemaResponse>> responseFuture =
                new CompletableFuture<>();

        // TODO(b/203605504) Considering using AppSearchShim, which is our test utility that
        //  glues AppSearchSession to the Future API
        session.getSchema(mSingleThreadedExecutor, responseFuture::complete);

        AppSearchResult<GetSchemaResponse> result = responseFuture.get();
        assertThat(result.isSuccess()).isTrue();
        GetSchemaResponse response = result.getResultValue();
        assertThat(response.getRequiredPermissionsForSchemaTypeVisibility()).hasSize(2);
        assertThat(response.getRequiredPermissionsForSchemaTypeVisibility()).containsKey(
                ContactPoint.SCHEMA_TYPE);
        assertThat(response.getRequiredPermissionsForSchemaTypeVisibility()).containsEntry(
                Person.SCHEMA_TYPE,
                ImmutableSet.of(ImmutableSet.of(SetSchemaRequest.READ_CONTACTS)));
    }

    @Test
    public void testIndexContacts() throws Exception {
        mAppSearchHelper.indexContactsAsync(generatePersonData(50), mUpdateStats).get();

        List<String> appsearchIds = mAppSearchHelper.getAllContactIdsAsync().get();
        assertThat(appsearchIds.size()).isEqualTo(50);
    }

    @Test
    public void testIndexContacts_clearAfterIndex() throws Exception {
        List<Person> contacts = generatePersonData(50);

        CompletableFuture<Void> indexContactsFuture = mAppSearchHelper.indexContactsAsync(contacts,
                mUpdateStats);
        contacts.clear();
        indexContactsFuture.get();

        List<String> appsearchIds = mAppSearchHelper.getAllContactIdsAsync().get();
        assertThat(appsearchIds.size()).isEqualTo(50);
    }

    @Test
    public void testAppSearchHelper_removeContacts() throws Exception {
        mAppSearchHelper.indexContactsAsync(generatePersonData(50), mUpdateStats).get();
        List<String> indexedIds = mAppSearchHelper.getAllContactIdsAsync().get();

        List<String> deletedIds = new ArrayList<>();
        for (int i = 0; i < 50; i += 5) {
            deletedIds.add(String.valueOf(i));
        }
        mAppSearchHelper.removeContactsByIdAsync(deletedIds, mUpdateStats).get();

        assertThat(indexedIds.size()).isEqualTo(50);
        List<String> appsearchIds = mAppSearchHelper.getAllContactIdsAsync().get();
        assertThat(appsearchIds).containsNoneIn(deletedIds);
    }

    @Test
    public void testCreateAppSearchHelper_compatibleSchemaChange() throws Exception {
        AppSearchHelper appSearchHelper = AppSearchHelper.createAppSearchHelper(mContext,
                mSingleThreadedExecutor);

        assertThat(appSearchHelper).isNotNull();
        assertThat(appSearchHelper.isDataLikelyWipedDuringInitAsync().get()).isFalse();
    }

    @Test
    public void testCreateAppSearchHelper_compatibleSchemaChange2() throws Exception {
        AppSearchManager.SearchContext searchContext =
                new AppSearchManager.SearchContext.Builder(AppSearchHelper.DATABASE_NAME).build();
        AppSearchSessionShim db = AppSearchSessionShimImpl.createSearchSessionAsync(
                searchContext).get();
        SetSchemaRequest setSchemaRequest = new SetSchemaRequest.Builder()
                .addSchemas(TestUtils.CONTACT_POINT_SCHEMA_WITH_APP_IDS_OPTIONAL)
                .setForceOverride(true).build();
        db.setSchemaAsync(setSchemaRequest).get();

        // APP_IDS changed from optional to repeated, which is a compatible change.
        AppSearchHelper appSearchHelper =
                AppSearchHelper.createAppSearchHelper(mContext, mSingleThreadedExecutor);

        assertThat(appSearchHelper).isNotNull();
        assertThat(appSearchHelper.isDataLikelyWipedDuringInitAsync().get()).isFalse();
    }

    @Test
    public void testCreateAppSearchHelper_incompatibleSchemaChange() throws Exception {
        AppSearchManager.SearchContext searchContext =
                new AppSearchManager.SearchContext.Builder(AppSearchHelper.DATABASE_NAME).build();
        AppSearchSessionShim db = AppSearchSessionShimImpl.createSearchSessionAsync(
                searchContext).get();
        SetSchemaRequest setSchemaRequest = new SetSchemaRequest.Builder()
                .addSchemas(TestUtils.CONTACT_POINT_SCHEMA_WITH_LABEL_REPEATED)
                .setForceOverride(true).build();
        db.setSchemaAsync(setSchemaRequest).get();

        // LABEL changed from repeated to optional, which is an incompatible change.
        AppSearchHelper appSearchHelper =
                AppSearchHelper.createAppSearchHelper(mContext, mSingleThreadedExecutor);

        assertThat(appSearchHelper).isNotNull();
        assertThat(appSearchHelper.isDataLikelyWipedDuringInitAsync().get()).isTrue();
    }

    @Test
    public void testGetAllContactIds() throws Exception {
        indexContactsInBatchesAsync(generatePersonData(200)).get();

        List<String> appSearchContactIds = mAppSearchHelper.getAllContactIdsAsync().get();

        assertThat(appSearchContactIds.size()).isEqualTo(200);
    }

    private CompletableFuture<Void> indexContactsInBatchesAsync(List<Person> contacts) {
        CompletableFuture<Void> indexContactsInBatchesFuture =
                CompletableFuture.completedFuture(null);
        int startIndex = 0;
        while (startIndex < contacts.size()) {
            int batchEndIndex = Math.min(
                    startIndex + ContactsIndexerImpl.NUM_UPDATED_CONTACTS_PER_BATCH_FOR_APPSEARCH,
                    contacts.size());
            List<Person> batchedContacts = contacts.subList(startIndex, batchEndIndex);
            indexContactsInBatchesFuture = indexContactsInBatchesFuture
                    .thenCompose(x -> mAppSearchHelper.indexContactsAsync(batchedContacts,
                            mUpdateStats));
            startIndex = batchEndIndex;
        }
        return indexContactsInBatchesFuture;
    }

    List<Person> generatePersonData(int numContacts) {
        List<Person> personList = new ArrayList<>();
        for (int i = 0; i < numContacts; i++) {
            personList.add(
                    new Person.Builder(/*namespace=*/ "", String.valueOf(i), "name" + i).build());
        }
        return personList;
    }
}