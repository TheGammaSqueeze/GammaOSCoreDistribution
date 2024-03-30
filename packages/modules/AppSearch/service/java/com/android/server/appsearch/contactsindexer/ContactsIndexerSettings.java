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

import android.annotation.NonNull;
import android.os.PersistableBundle;
import android.util.AtomicFile;

import com.android.internal.annotations.VisibleForTesting;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Objects;

/**
 * Contacts indexer settings backed by a PersistableBundle.
 *
 * Holds settings such as the last time when a full update or delta update was performed.
 *
 * <p>This class is NOT thread safe (similar to {@link PersistableBundle} which it wraps).
 *
 * @hide
 */
public class ContactsIndexerSettings {

    private static final String TAG = "ContactsIndexerSettings";

    /*package*/ static final String SETTINGS_FILE_NAME = "contacts_indexer_settings.pb";

    /*package*/ static final String LAST_FULL_UPDATE_TIMESTAMP_KEY =
            "last_full_update_timestamp_millis";
    /*package*/ static final String LAST_DELTA_UPDATE_TIMESTAMP_KEY =
            "last_delta_update_timestamp_millis";
    /*package*/ static final String LAST_DELTA_DELETE_TIMESTAMP_KEY =
            "last_delta_delete_timestamp_millis";

    private final File mFile;
    private PersistableBundle mBundle = new PersistableBundle();

    public ContactsIndexerSettings(@NonNull File baseDir) {
        Objects.requireNonNull(baseDir);
        mFile = new File(baseDir, SETTINGS_FILE_NAME);
    }

    public void load() throws IOException {
        mBundle = readBundle(mFile);
    }

    public void persist() throws IOException {
        writeBundle(mFile, mBundle);
    }

    public long getLastFullUpdateTimestampMillis() {
        return mBundle.getLong(LAST_FULL_UPDATE_TIMESTAMP_KEY);
    }

    public void setLastFullUpdateTimestampMillis(long timestampMillis) {
        mBundle.putLong(LAST_FULL_UPDATE_TIMESTAMP_KEY, timestampMillis);
    }

    public long getLastDeltaUpdateTimestampMillis() {
        return mBundle.getLong(LAST_DELTA_UPDATE_TIMESTAMP_KEY);
    }

    public void setLastDeltaUpdateTimestampMillis(long timestampMillis) {
        mBundle.putLong(LAST_DELTA_UPDATE_TIMESTAMP_KEY, timestampMillis);
    }

    public long getLastDeltaDeleteTimestampMillis() {
        return mBundle.getLong(LAST_DELTA_DELETE_TIMESTAMP_KEY);
    }

    public void setLastDeltaDeleteTimestampMillis(long timestampMillis) {
        mBundle.putLong(LAST_DELTA_DELETE_TIMESTAMP_KEY, timestampMillis);
    }

    /** Resets all the settings to default values. */
    public void reset() {
        setLastDeltaDeleteTimestampMillis(0);
        setLastDeltaUpdateTimestampMillis(0);
        setLastFullUpdateTimestampMillis(0);
    }

    @VisibleForTesting
    @NonNull
    /*package*/ static PersistableBundle readBundle(@NonNull File src) throws
            IOException {
        AtomicFile atomicFile = new AtomicFile(src);
        try (FileInputStream fis = atomicFile.openRead()) {
            return PersistableBundle.readFromStream(fis);
        }
    }

    @VisibleForTesting
    /*package*/ static void writeBundle(@NonNull File dest, @NonNull PersistableBundle bundle)
            throws IOException {
        AtomicFile atomicFile = new AtomicFile(dest);
        FileOutputStream fos = null;
        try {
            fos = atomicFile.startWrite();
            bundle.writeToStream(fos);
            atomicFile.finishWrite(fos);
        } catch (IOException e ) {
            if (fos != null) {
                atomicFile.failWrite(fos);
            }
            throw e;
        }
    }
}
