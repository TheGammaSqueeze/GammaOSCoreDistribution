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

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.provider.ContactsContract;
import android.test.ProviderTestCase2;

import org.junit.After;
import org.junit.Before;

import java.util.ArrayList;
import java.util.List;

// TODO(b/203605504) this is a junit3 test but we should use junit4. Right now I can't make
//  ProviderTestRule work so we stick to ProviderTestCase2 for now.
public class ContactsProviderUtilTest extends ProviderTestCase2<FakeContactsProvider> {
    public ContactsProviderUtilTest() {
        super(FakeContactsProvider.class, FakeContactsProvider.AUTHORITY);
    }

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        mContext = mock(Context.class);
        doReturn(getMockContentResolver()).when(mContext).getContentResolver();
    }

    @Override
    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    public void testGetUpdatedContactIds_getAll() throws Exception {
        ContentResolver resolver = mContext.getContentResolver();
        ContentValues dummyValues = new ContentValues();
        List<String> expectedIds = new ArrayList<>();
        for (int i = 0; i < 50; i ++) {
            resolver.insert(ContactsContract.Contacts.CONTENT_URI, dummyValues);
            expectedIds.add(String.valueOf(i));
            // Sleep for 2ms to ensure that each contact gets a distinct update timestamp
            Thread.sleep(2);
        }

        List<String> ids = new ArrayList<>();
        long lastUpdatedTime = ContactsProviderUtil.getUpdatedContactIds(mContext,
                /*sinceFilter=*/ 0, ContactsProviderUtil.UPDATE_LIMIT_NONE, ids, /*stats=*/ null);

        assertThat(lastUpdatedTime).isEqualTo(
                getProvider().getMostRecentContactUpdateTimestampMillis());
        // TODO(b/228239000): make this assertion based on last-updated-ts instead of contact ID
        assertThat(ids).isEqualTo(expectedIds);
    }

    public void testGetUpdatedContactIds_getNone() {
        ContentResolver resolver = mContext.getContentResolver();
        ContentValues dummyValues = new ContentValues();
        for (int i = 0; i < 50; i ++) {
            resolver.insert(ContactsContract.Contacts.CONTENT_URI, dummyValues);
        }

        List<String> ids = new ArrayList<>();
        long lastUpdatedTime = ContactsProviderUtil.getUpdatedContactIds(mContext,
                /*sinceFilter=*/ getProvider().getMostRecentContactUpdateTimestampMillis(),
                ContactsProviderUtil.UPDATE_LIMIT_NONE, ids, /*stats=*/ null);

        assertThat(lastUpdatedTime).isEqualTo(
                getProvider().getMostRecentContactUpdateTimestampMillis());
        assertThat(ids).isEmpty();
    }

    public void testGetUpdatedContactIds() {
        ContentResolver resolver = mContext.getContentResolver();
        ContentValues dummyValues = new ContentValues();
        for (int i = 0; i < 50; i++) {
            resolver.insert(ContactsContract.Contacts.CONTENT_URI, dummyValues);
        }
        long firstUpdateTimestamp = getProvider().getMostRecentContactUpdateTimestampMillis();
        List<String> expectedIds = new ArrayList<>();
        for (int i = 50; i < 100; i++) {
            resolver.insert(ContactsContract.Contacts.CONTENT_URI, dummyValues);
            expectedIds.add(String.valueOf(i));
        }

        List<String> ids = new ArrayList<>();
        long lastUpdatedTime = ContactsProviderUtil.getUpdatedContactIds(mContext,
                /*sinceFilter=*/ firstUpdateTimestamp, ContactsProviderUtil.UPDATE_LIMIT_NONE,
                ids, /*stats=*/ null);

        assertThat(lastUpdatedTime).isEqualTo(
                getProvider().getMostRecentContactUpdateTimestampMillis());
        assertThat(ids).isEqualTo(expectedIds);
    }

    public void testGetDeletedContactIds_getAll() {
        ContentResolver resolver = mContext.getContentResolver();
        ContentValues dummyValues = new ContentValues();
        for (int i = 0; i < 50; i++) {
            resolver.insert(ContactsContract.Contacts.CONTENT_URI, dummyValues);
        }
        List<String> expectedIds = new ArrayList<>();
        for (int i = 5; i < 50; i += 5) {
            resolver.delete(ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, i),
                    /*extras=*/ null);
            expectedIds.add(String.valueOf(i));
        }

        List<String> ids = new ArrayList<>();
        long lastDeleteTime = ContactsProviderUtil.getDeletedContactIds(mContext,
                /*sinceFilter=*/ 0, ids, /*stats=*/ null);

        assertThat(lastDeleteTime).isEqualTo(
                getProvider().getMostRecentDeletedContactTimestampMillis());
        assertThat(ids).isEqualTo(expectedIds);
    }

    public void testGetDeletedContactIds_getNone() {
        ContentResolver resolver = mContext.getContentResolver();
        ContentValues dummyValues = new ContentValues();
        for (int i = 0; i < 50; i ++) {
            resolver.insert(ContactsContract.Contacts.CONTENT_URI, dummyValues);
        }
        for (int i = 5; i < 50; i += 5) {
            resolver.delete(ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, i),
                    /*extras=*/ null);
        }

        List<String> ids = new ArrayList<>();
        long lastDeleteTime = ContactsProviderUtil.getDeletedContactIds(mContext,
                /*sinceFilter=*/ getProvider().getMostRecentDeletedContactTimestampMillis(),
                ids, /*stats=*/ null);

        assertThat(lastDeleteTime).isEqualTo(
                getProvider().getMostRecentDeletedContactTimestampMillis());
        assertThat(ids).isEmpty();
    }

    public void testGetDeletedContactIds() {
        ContentResolver resolver = mContext.getContentResolver();
        ContentValues dummyValues = new ContentValues();
        for (int i = 0; i < 50; i++) {
            resolver.insert(ContactsContract.Contacts.CONTENT_URI, dummyValues);
        }
        for (int i = 5; i < 50; i += 5) {
            resolver.delete(ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, i),
                    /*extras=*/ null);
        }
        long firstDeleteTimestamp = getProvider().getMostRecentDeletedContactTimestampMillis();
        List<String> expectedIds = new ArrayList<>();
        for (int i = 7; i < 50; i += 7) {
            resolver.delete(ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, i),
                    /*extras=*/ null);
            expectedIds.add(String.valueOf(i));
        }

        List<String> ids = new ArrayList<>();
        long lastDeleteTime = ContactsProviderUtil.getDeletedContactIds(mContext,
                /*sinceFilter=*/ firstDeleteTimestamp, ids, /*stats=*/ null);

        assertThat(lastDeleteTime).isEqualTo(
                getProvider().getMostRecentDeletedContactTimestampMillis());
        assertThat(ids).isEqualTo(expectedIds);
    }
}
