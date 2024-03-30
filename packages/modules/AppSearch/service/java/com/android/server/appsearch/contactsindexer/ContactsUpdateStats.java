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

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.app.appsearch.AppSearchResult;
import android.util.ArraySet;

import com.android.server.appsearch.stats.AppSearchStatsLog;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Set;

/**
 * The class to hold stats for DeltaUpdate or FullUpdate.
 *
 * <p>This will be used to populate
 * {@link AppSearchStatsLog#CONTACTS_INDEXER_UPDATE_STATS_REPORTED}.
 *
 * <p>This class is not thread-safe.
 *
 * @hide
 */
public class ContactsUpdateStats {
    @IntDef(
            value = {
                    UNKNOWN_UPDATE_TYPE,
                    DELTA_UPDATE,
                    FULL_UPDATE,
            })
    @Retention(RetentionPolicy.SOURCE)
    public @interface UpdateType {
    }

    public static final int UNKNOWN_UPDATE_TYPE =
            AppSearchStatsLog.CONTACTS_INDEXER_UPDATE_STATS_REPORTED__UPDATE_TYPE__UNKNOWN;
    /** Incremental update reacting to CP2 change notifications. */
    public static final int DELTA_UPDATE =
            AppSearchStatsLog.CONTACTS_INDEXER_UPDATE_STATS_REPORTED__UPDATE_TYPE__DELTA;
    /** Complete update to bring AppSearch in sync with CP2. */
    public static final int FULL_UPDATE =
            AppSearchStatsLog.CONTACTS_INDEXER_UPDATE_STATS_REPORTED__UPDATE_TYPE__FULL;

    @UpdateType
    int mUpdateType = UNKNOWN_UPDATE_TYPE;
    // Status for updates.
    // In case of success, we will just have one success status stored.
    // In case of Error,  we store the unique error codes during the update.
    @AppSearchResult.ResultCode
    Set<Integer> mUpdateStatuses = new ArraySet<>();
    // Status for deletions.
    // In case of success, we will just have one success status stored.
    // In case of Error,  we store the unique error codes during the deletion.
    @AppSearchResult.ResultCode
    Set<Integer> mDeleteStatuses = new ArraySet<>();

    // Start time in millis for update and delete.
    long mUpdateAndDeleteStartTimeMillis;


    //
    // Update for both old and new contacts(a.k.a insertion).
    //
    // # of old and new contacts failed to be updated.
    int mContactsUpdateFailedCount;
    // # of old and new contacts succeeds to be updated.
    int mContactsUpdateSucceededCount;
    // # of contacts update skipped due to NO significant change during the update.
    int mContactsUpdateSkippedCount;
    // Total # of old and new contacts to be updated.
    // It should equal to
    // mContactsUpdateFailedCount + mContactsUpdateSucceededCount + mContactsUpdateSkippedCount
    int mTotalContactsToBeUpdated;
    // Among the succeeded and failed contacts updates, how many of them are for the new contacts
    // currently NOT available in AppSearch.
    int mNewContactsToBeUpdated;

    //
    // Deletion for old documents.
    //
    // # of old contacts failed to be deleted.
    int mContactsDeleteFailedCount;
    // # of old contacts succeeds to be deleted.
    int mContactsDeleteSucceededCount;
    // Total # of old contacts to be deleted. It should equal to
    // mContactsDeleteFailedCount + mContactsDeleteSucceededCount
    int mTotalContactsToBeDeleted;

    public void clear() {
        mUpdateType = UNKNOWN_UPDATE_TYPE;
        mUpdateStatuses.clear();
        mDeleteStatuses.clear();
        mUpdateAndDeleteStartTimeMillis = 0;
        // Update for old and new contacts
        mContactsUpdateFailedCount = 0;
        mContactsUpdateSucceededCount = 0;
        mContactsUpdateSkippedCount = 0;
        mNewContactsToBeUpdated = 0;
        mTotalContactsToBeUpdated = 0;
        // delete for old contacts
        mContactsDeleteFailedCount = 0;
        mContactsDeleteSucceededCount = 0;
        mTotalContactsToBeDeleted = 0;
    }

    @NonNull
    public String toString() {
        return "UpdateType: " + mUpdateType
                + ", UpdateStatus: " + mUpdateStatuses.toString()
                + ", DeleteStatus: " + mDeleteStatuses.toString()
                + ", UpdateAndDeleteStartTimeMillis: " + mUpdateAndDeleteStartTimeMillis
                + ", ContactsUpdateFailedCount: " + mContactsUpdateFailedCount
                + ", ContactsUpdateSucceededCount: " + mContactsUpdateSucceededCount
                + ", NewContactsToBeUpdated: " + mNewContactsToBeUpdated
                + ", ContactsUpdateSkippedCount: " + mContactsUpdateSkippedCount
                + ", TotalContactsToBeUpdated: " + mTotalContactsToBeUpdated
                + ", ContactsDeleteFailedCount: " + mContactsDeleteFailedCount
                + ", ContactsDeleteSucceededCount: " + mContactsDeleteSucceededCount
                + ", TotalContactsToBeDeleted: " + mTotalContactsToBeDeleted;
    }
}