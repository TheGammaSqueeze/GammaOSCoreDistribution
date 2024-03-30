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

package com.android.server.appsearch;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.os.Environment;
import android.os.UserHandle;
import android.util.Log;

import com.android.server.SystemService;
import com.android.server.appsearch.contactsindexer.ContactsIndexerConfig;
import com.android.server.appsearch.contactsindexer.FrameworkContactsIndexerConfig;
import com.android.server.appsearch.contactsindexer.ContactsIndexerManagerService;

import java.io.File;

public class AppSearchModule {
    private static final String TAG = "AppSearchModule";

    /**
     * Returns AppSearch directory in the credential encrypted system directory for the given user.
     *
     * <p>This folder should only be accessed after unlock.
     */
    public static File getAppSearchDir(@NonNull UserHandle userHandle) {
        // Duplicates the implementation of Environment#getDataSystemCeDirectory
        // TODO(b/191059409): Unhide Environment#getDataSystemCeDirectory and switch to it.
        File systemCeDir = new File(Environment.getDataDirectory(), "system_ce");
        File systemCeUserDir = new File(systemCeDir, String.valueOf(userHandle.getIdentifier()));
        return new File(systemCeUserDir, "appsearch");
    }

    public static final class Lifecycle extends SystemService {
        private AppSearchManagerService mAppSearchManagerService;
        @Nullable
        private ContactsIndexerManagerService mContactsIndexerManagerService;

        public Lifecycle(Context context) {
            super(context);
        }

        @Override
        public void onStart() {
            mAppSearchManagerService = new AppSearchManagerService(getContext());

            try {
                mAppSearchManagerService.onStart();
            } catch (Exception e) {
                Log.e(TAG, "Failed to start AppSearch service", e);
                // If AppSearch service fails to start, skip starting ContactsIndexer service
                // since it indexes CP2 contacts into AppSearch builtin:Person corpus
                return;
            }

            // It is safe to check DeviceConfig here, since SettingsProvider, which DeviceConfig
            // uses, starts before AppSearch.
            ContactsIndexerConfig contactsIndexerConfig = new FrameworkContactsIndexerConfig();
            if (contactsIndexerConfig.isContactsIndexerEnabled()) {
                mContactsIndexerManagerService = new ContactsIndexerManagerService(getContext(),
                        contactsIndexerConfig);
                try {
                    mContactsIndexerManagerService.onStart();
                } catch (Throwable t) {
                    Log.e(TAG, "Failed to start ContactsIndexer service", t);
                    // Release the Contacts Indexer instance as it won't be started until the next
                    // system_server restart on a device reboot.
                    mContactsIndexerManagerService = null;
                }
            } else {
                Log.i(TAG, "ContactsIndexer service is disabled.");
            }
        }

        @Override
        public void onBootPhase(int phase) {
            mAppSearchManagerService.onBootPhase(phase);
        }

        @Override
        public void onUserUnlocking(@NonNull TargetUser user) {
            mAppSearchManagerService.onUserUnlocking(user);
            if (mContactsIndexerManagerService != null) {
                mContactsIndexerManagerService.onUserUnlocking(user);
            }
        }

        @Override
        public void onUserStopping(@NonNull TargetUser user) {
            mAppSearchManagerService.onUserStopping(user);
            if (mContactsIndexerManagerService != null) {
                mContactsIndexerManagerService.onUserStopping(user);
            }
        }
    }
}
