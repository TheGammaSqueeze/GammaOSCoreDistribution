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

package com.android.intentresolver;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.os.UserHandle;

import com.android.intentresolver.AbstractMultiProfilePagerAdapter.CrossProfileIntentsChecker;
import com.android.intentresolver.AbstractMultiProfilePagerAdapter.MyUserIdProvider;
import com.android.intentresolver.chooser.TargetInfo;
import com.android.intentresolver.flags.FeatureFlagRepository;
import com.android.intentresolver.shortcuts.ShortcutLoader;

import java.util.function.Consumer;
import java.util.function.Function;

import kotlin.jvm.functions.Function2;

/**
 * Singleton providing overrides to be applied by any {@code IChooserWrapper} used in testing.
 * We cannot directly mock the activity created since instrumentation creates it, so instead we use
 * this singleton to modify behavior.
 */
public class ChooserActivityOverrideData {
    private static ChooserActivityOverrideData sInstance = null;

    public static ChooserActivityOverrideData getInstance() {
        if (sInstance == null) {
            sInstance = new ChooserActivityOverrideData();
        }
        return sInstance;
    }

    @SuppressWarnings("Since15")
    public Function<PackageManager, PackageManager> createPackageManager;
    public Function<TargetInfo, Boolean> onSafelyStartCallback;
    public Function2<UserHandle, Consumer<ShortcutLoader.Result>, ShortcutLoader>
            shortcutLoaderFactory = (userHandle, callback) -> null;
    public ChooserActivity.ChooserListController resolverListController;
    public ChooserActivity.ChooserListController workResolverListController;
    public Boolean isVoiceInteraction;
    public boolean isImageType;
    public Cursor resolverCursor;
    public boolean resolverForceException;
    public Bitmap previewThumbnail;
    public ChooserActivityLogger chooserActivityLogger;
    public int alternateProfileSetting;
    public Resources resources;
    public UserHandle workProfileUserHandle;
    public boolean hasCrossProfileIntents;
    public boolean isQuietModeEnabled;
    public Integer myUserId;
    public WorkProfileAvailabilityManager mWorkProfileAvailability;
    public MyUserIdProvider mMyUserIdProvider;
    public CrossProfileIntentsChecker mCrossProfileIntentsChecker;
    public PackageManager packageManager;
    public FeatureFlagRepository featureFlagRepository;

    public void reset() {
        onSafelyStartCallback = null;
        isVoiceInteraction = null;
        createPackageManager = null;
        previewThumbnail = null;
        isImageType = false;
        resolverCursor = null;
        resolverForceException = false;
        resolverListController = mock(ChooserActivity.ChooserListController.class);
        workResolverListController = mock(ChooserActivity.ChooserListController.class);
        chooserActivityLogger = mock(ChooserActivityLogger.class);
        alternateProfileSetting = 0;
        resources = null;
        workProfileUserHandle = null;
        hasCrossProfileIntents = true;
        isQuietModeEnabled = false;
        myUserId = null;
        packageManager = null;
        mWorkProfileAvailability = new WorkProfileAvailabilityManager(null, null, null) {
            @Override
            public boolean isQuietModeEnabled() {
                return isQuietModeEnabled;
            }

            @Override
            public boolean isWorkProfileUserUnlocked() {
                return true;
            }

            @Override
            public void requestQuietModeEnabled(boolean enabled) {
                isQuietModeEnabled = enabled;
            }

            @Override
            public void markWorkProfileEnabledBroadcastReceived() {}

            @Override
            public boolean isWaitingToEnableWorkProfile() {
                return false;
            }
        };
        shortcutLoaderFactory = ((userHandle, resultConsumer) -> null);

        mMyUserIdProvider = new MyUserIdProvider() {
            @Override
            public int getMyUserId() {
                return myUserId != null ? myUserId : UserHandle.myUserId();
            }
        };

        mCrossProfileIntentsChecker = mock(CrossProfileIntentsChecker.class);
        when(mCrossProfileIntentsChecker.hasCrossProfileIntents(any(), anyInt(), anyInt()))
                .thenAnswer(invocation -> hasCrossProfileIntents);
        featureFlagRepository = null;
    }

    private ChooserActivityOverrideData() {}
}

