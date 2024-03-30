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

package com.android.tv.settings.library.device.storage;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.storage.DiskInfo;
import android.os.storage.StorageManager;
import android.os.storage.VolumeInfo;
import android.text.TextUtils;
import android.text.format.Formatter;
import android.util.Log;

import com.android.tv.settings.library.ManagerUtil;
import com.android.tv.settings.library.PreferenceCompat;
import com.android.tv.settings.library.UIUpdateCallback;
import com.android.tv.settings.library.data.PreferenceControllerState;
import com.android.tv.settings.library.device.apps.AppsState;
import com.android.tv.settings.library.settingslib.StorageMeasurement;
import com.android.tv.settings.library.util.AbstractPreferenceController;
import com.android.tv.settings.library.util.ResourcesUtil;

import java.io.File;
import java.util.HashMap;
import java.util.List;

/** State to handle storage screen. */
public class StorageState extends PreferenceControllerState {
    private static final String TAG = "StorageFragment";
    private static final String EXTRA_MIGRATE_HERE =
            "com.android.tv.settings.device.storage.MigrateStorageActivity.MIGRATE_HERE";
    private static final String INTENT_ACTION_FORMAT_AS_PRIVATE =
            "com.android.tv.settings.device.storage.FormatActivity.formatAsPrivate";
    private static final String INTENT_ACTION_FORMAT_AS_PUBLIC =
            "com.android.tv.settings.device.storage.FormatActivity.formatAsPublic";
    private static final String EXTRA_VOLUME_DESC = "UnmountActivity.volumeDesc";
    private static final String INTENT_NEW_STORAGE = "com.android.tv.settings.action.NEW_STORAGE";
    private static final String INTENT_UNMOUNT = "com.android.tv.settings.action.UNMOUNT_STORAGE";
    private static final String INTENT_CONFIRMATION = "android.settings.ui.CONFIRM";
    private static final String EXTRA_GUIDANCE_TITLE = "guidancetitle";
    private static final String EXTRA_GUIDANCE_SUBTITLE = "guidanceSubtitle";
    private static final String KEY_MIGRATE = "migrate";
    private static final String KEY_EJECT = "eject";
    private static final String KEY_ERASE = "erase";
    private static final String KEY_APPS_USAGE = "apps_usage";
    private static final String KEY_DCIM_USAGE = "dcim_usage";
    private static final String KEY_MUSIC_USAGE = "music_usage";
    private static final String KEY_DOWNLOADS_USAGE = "downloads_usage";
    private static final String KEY_CACHE_USAGE = "cache_usage";
    private static final String KEY_MISC_USAGE = "misc_usage";
    private static final String KEY_AVAILABLE = "available";

    private static final int REQUEST_CLEAR_CACHE = 1;
    private static final long SIZE_CALCULATING = -1;

    private StorageManager mStorageManager;
    private PackageManager mPackageManager;

    private VolumeInfo mVolumeInfo;

    private StorageMeasurement mMeasure;
    private final StorageMeasurement.MeasurementReceiver mMeasurementReceiver =
            new MeasurementReceiver();
    private final StorageEventListener mStorageEventListener = new StorageEventListener();

    private PreferenceCompat mMigratePref;
    private PreferenceCompat mEjectPref;
    private PreferenceCompat mErasePref;
    private PreferenceCompat mAppsUsagePref;
    private PreferenceCompat mDcimUsagePref;
    private PreferenceCompat mMusicUsagePref;
    private PreferenceCompat mDownloadsUsagePref;
    private PreferenceCompat mCacheUsagePref;
    private PreferenceCompat mMiscUsagePref;
    private PreferenceCompat mAvailablePref;
    private Bundle mExtras;

    public StorageState(Context context,
            UIUpdateCallback callback) {
        super(context, callback);
    }

    public static void prepareArgs(Bundle bundle, VolumeInfo volumeInfo) {
        bundle.putString(VolumeInfo.EXTRA_VOLUME_ID, volumeInfo.getId());
    }

    @Override
    public void onCreate(Bundle extras) {
        mStorageManager = mContext.getSystemService(StorageManager.class);
        mPackageManager = mContext.getPackageManager();

        mVolumeInfo = mStorageManager.findVolumeById(
                extras.getString(VolumeInfo.EXTRA_VOLUME_ID));
        mExtras = extras;
        super.onCreate(extras);
        mUIUpdateCallback.notifyUpdateScreenTitle(
                getStateIdentifier(), mStorageManager.getBestVolumeDescription(mVolumeInfo));
        mMigratePref = mPreferenceCompatManager.getOrCreatePrefCompat(KEY_MIGRATE);
        mEjectPref = mPreferenceCompatManager.getOrCreatePrefCompat(KEY_EJECT);
        mErasePref = mPreferenceCompatManager.getOrCreatePrefCompat(KEY_ERASE);
        mAppsUsagePref = mPreferenceCompatManager.getOrCreatePrefCompat(KEY_APPS_USAGE);
        mDcimUsagePref = mPreferenceCompatManager.getOrCreatePrefCompat(KEY_DCIM_USAGE);
        mMusicUsagePref = mPreferenceCompatManager.getOrCreatePrefCompat(KEY_MUSIC_USAGE);
        mDownloadsUsagePref = mPreferenceCompatManager.getOrCreatePrefCompat(KEY_DOWNLOADS_USAGE);
        mCacheUsagePref = mPreferenceCompatManager.getOrCreatePrefCompat(KEY_CACHE_USAGE);
        mMiscUsagePref = mPreferenceCompatManager.getOrCreatePrefCompat(KEY_MISC_USAGE);
        mAvailablePref = mPreferenceCompatManager.getOrCreatePrefCompat(KEY_AVAILABLE);
    }

    @Override
    public void onStart() {
        super.onStart();
        mStorageManager.registerListener(mStorageEventListener);
        startMeasurement();
    }

    @Override
    public void onResume() {
        super.onResume();
        mVolumeInfo = mStorageManager.findVolumeById(mExtras.getString(VolumeInfo.EXTRA_VOLUME_ID));
        if (mVolumeInfo == null || !mVolumeInfo.isMountedReadable()) {
            mUIUpdateCallback.notifyNavigateBackward(getStateIdentifier());
        } else {
            refresh();
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        mStorageManager.unregisterListener(mStorageEventListener);
        stopMeasurement();
    }

    private static Intent getFormatAsPublicIntent(Context context, String diskId) {
        final Intent i = new Intent(INTENT_ACTION_FORMAT_AS_PUBLIC);
        i.putExtra(DiskInfo.EXTRA_DISK_ID, diskId);
        return i;
    }

    private static Intent getFormatAsPrivateIntent(Context context, String diskId) {
        final Intent i = new Intent(INTENT_ACTION_FORMAT_AS_PRIVATE);
        i.putExtra(DiskInfo.EXTRA_DISK_ID, diskId);
        return i;
    }

    private static Intent getUnmountIntent(Context context, String volumeId, String volumeDesc) {
        final Intent i = new Intent(INTENT_UNMOUNT);
        i.putExtra(VolumeInfo.EXTRA_VOLUME_ID, volumeId);
        i.putExtra(EXTRA_VOLUME_DESC, volumeDesc);
        return i;
    }

    private void refresh() {
        boolean showMigrate = false;
        final VolumeInfo currentExternal = mPackageManager.getPrimaryStorageCurrentVolume();
        // currentExternal will be null if the drive is not mounted. Don't offer the option to
        // migrate if so.
        if (currentExternal != null
                && !TextUtils.equals(currentExternal.getId(), mVolumeInfo.getId())) {
            final List<VolumeInfo> candidates =
                    mPackageManager.getPrimaryStorageCandidateVolumes();
            for (final VolumeInfo candidate : candidates) {
                if (TextUtils.equals(candidate.getId(), mVolumeInfo.getId())) {
                    showMigrate = true;
                    break;
                }
            }
        }

        mMigratePref.setVisible(showMigrate);
        Intent intent = new Intent(INTENT_NEW_STORAGE)
                .putExtra(VolumeInfo.EXTRA_VOLUME_ID, mVolumeInfo.getId())
                .putExtra(EXTRA_MIGRATE_HERE, true);
        mMigratePref.setIntent(intent);

        final String description = mStorageManager.getBestVolumeDescription(mVolumeInfo);

        final boolean privateInternal = VolumeInfo.ID_PRIVATE_INTERNAL.equals(mVolumeInfo.getId());
        final boolean isPrivate = mVolumeInfo.getType() == VolumeInfo.TYPE_PRIVATE;

        mEjectPref.setVisible(!privateInternal);
        mEjectPref.setIntent(getUnmountIntent(mContext, mVolumeInfo.getId(), description));
        mErasePref.setVisible(!privateInternal);
        if (isPrivate) {
            mErasePref.setIntent(getFormatAsPublicIntent(mContext, mVolumeInfo.getDiskId()));
            mErasePref.setTitle(ResourcesUtil.getString(mContext, "storage_format_as_public"));
        } else {
            mErasePref.setIntent(getFormatAsPrivateIntent(mContext, mVolumeInfo.getDiskId()));
            mErasePref.setTitle(ResourcesUtil.getString(mContext, "storage_format_as_private"));
        }

        mAppsUsagePref.setVisible(isPrivate);
        mAppsUsagePref.setNextState(ManagerUtil.STATE_APPS);
        Bundle appUsageExtras = new Bundle();
        AppsState.prepareArgs(appUsageExtras, mVolumeInfo.fsUuid, description);
        mAppsUsagePref.setExtras(appUsageExtras);
        mDcimUsagePref.setVisible(isPrivate);
        mMusicUsagePref.setVisible(isPrivate);
        mDownloadsUsagePref.setVisible(isPrivate);
        mCacheUsagePref.setVisible(isPrivate);
        mUIUpdateCallback.notifyUpdate(getStateIdentifier(), mMigratePref);
        mUIUpdateCallback.notifyUpdate(getStateIdentifier(), mEjectPref);
        mUIUpdateCallback.notifyUpdate(getStateIdentifier(), mErasePref);
        mUIUpdateCallback.notifyUpdate(getStateIdentifier(), mAppsUsagePref);
        mUIUpdateCallback.notifyUpdate(getStateIdentifier(), mDcimUsagePref);
        mUIUpdateCallback.notifyUpdate(getStateIdentifier(), mMusicUsagePref);
        mUIUpdateCallback.notifyUpdate(getStateIdentifier(), mDownloadsUsagePref);
        mUIUpdateCallback.notifyUpdate(getStateIdentifier(), mCacheUsagePref);
    }

    @Override
    public boolean onPreferenceTreeClick(String[] key, boolean status) {
        if (KEY_CACHE_USAGE.equals(key[0])) {
            Intent i = new Intent(INTENT_CONFIRMATION);
            i.putExtra(EXTRA_GUIDANCE_TITLE, ResourcesUtil.getString(
                    mContext, "device_storage_clear_cache_title"));
            i.putExtra(EXTRA_GUIDANCE_SUBTITLE, ResourcesUtil.getString(
                    mContext, "device_storage_clear_cache_message"));
            ((Activity) mContext).startActivityForResult(i,
                    ManagerUtil.calculateCompoundCode(
                            getStateIdentifier(), REQUEST_CLEAR_CACHE
                    ));
            return true;
        }
        return super.onPreferenceTreeClick(key, status);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_CLEAR_CACHE:
                if (resultCode == Activity.RESULT_OK) {
                    final PackageManager pm = mContext.getPackageManager();
                    final List<PackageInfo> infos = pm.getInstalledPackages(0);
                    for (PackageInfo info : infos) {
                        pm.deleteApplicationCacheFiles(info.packageName, null);
                    }
                }
                break;
            default:
                // no-op
        }
    }

    private void startMeasurement() {
        if (mVolumeInfo != null && mVolumeInfo.isMountedReadable()) {
            final VolumeInfo sharedVolume = mStorageManager.findEmulatedForPrivate(mVolumeInfo);
            mMeasure = new StorageMeasurement(mContext, mVolumeInfo, sharedVolume);
            mMeasure.setReceiver(mMeasurementReceiver);
            mMeasure.forceMeasure();
        }
    }

    private void updateDetails(StorageMeasurement.MeasurementDetails details) {
        final int currentUser = ActivityManager.getCurrentUser();
        final long dcimSize = totalValues(details.mediaSize.get(currentUser),
                Environment.DIRECTORY_DCIM,
                Environment.DIRECTORY_MOVIES, Environment.DIRECTORY_PICTURES);

        final long musicSize = totalValues(details.mediaSize.get(currentUser),
                Environment.DIRECTORY_MUSIC,
                Environment.DIRECTORY_ALARMS, Environment.DIRECTORY_NOTIFICATIONS,
                Environment.DIRECTORY_RINGTONES, Environment.DIRECTORY_PODCASTS);

        final long downloadsSize = totalValues(details.mediaSize.get(currentUser),
                Environment.DIRECTORY_DOWNLOADS);

        mAvailablePref.setSummary(formatSize(mContext,
                Math.max(0L, details.availSize - cachePartitionSize())));
        mAppsUsagePref.setSummary(formatSize(mContext,
                details.appsSize.get(currentUser)));
        mDcimUsagePref.setSummary(formatSize(mContext, dcimSize));
        mMusicUsagePref.setSummary(formatSize(mContext, musicSize));
        mDownloadsUsagePref.setSummary(formatSize(mContext, downloadsSize));
        mCacheUsagePref.setSummary(formatSize(mContext, details.cacheSize));
        mMiscUsagePref.setSummary(formatSize(mContext, details.miscSize.get(currentUser)));
        mUIUpdateCallback.notifyUpdate(getStateIdentifier(), mAvailablePref);
        mUIUpdateCallback.notifyUpdate(getStateIdentifier(), mAppsUsagePref);
        mUIUpdateCallback.notifyUpdate(getStateIdentifier(), mDcimUsagePref);
        mUIUpdateCallback.notifyUpdate(getStateIdentifier(), mMusicUsagePref);
        mUIUpdateCallback.notifyUpdate(getStateIdentifier(), mDownloadsUsagePref);
        mUIUpdateCallback.notifyUpdate(getStateIdentifier(), mCacheUsagePref);
        mUIUpdateCallback.notifyUpdate(getStateIdentifier(), mMiscUsagePref);
    }

    public static String formatSize(Context context, long size) {
        return (size == SIZE_CALCULATING)
                ? ResourcesUtil.getString(context, "storage_calculating_size")
                : Formatter.formatShortFileSize(context, size);
    }

    private static long cachePartitionSize() {
        File cache = new File("/cache");
        try {
            return cache.getUsableSpace();
        } catch (SecurityException e) {
            Log.w(TAG, "Cannot determine cache partition size.", e);
            return 0;
        }
    }

    private static long totalValues(HashMap<String, Long> map, String... keys) {
        long total = 0;
        if (map != null) {
            for (String key : keys) {
                if (map.containsKey(key)) {
                    total += map.get(key);
                }
            }
        } else {
            Log.w(TAG,
                    "MeasurementDetails mediaSize array does not have key for current user " +
                            ActivityManager.getCurrentUser());
        }
        return total;
    }

    private void stopMeasurement() {
        if (mMeasure != null) {
            mMeasure.onDestroy();
        }
    }

    @Override
    public int getStateIdentifier() {
        return ManagerUtil.STATE_STORAGE;
    }

    @Override
    protected List<AbstractPreferenceController> onCreatePreferenceControllers(Context context) {
        return null;
    }

    private class MeasurementReceiver implements StorageMeasurement.MeasurementReceiver {

        @Override
        public void onDetailsChanged(StorageMeasurement.MeasurementDetails details) {
            updateDetails(details);
        }
    }

    private class StorageEventListener extends android.os.storage.StorageEventListener {
        @Override
        public void onVolumeStateChanged(VolumeInfo vol, int oldState, int newState) {
            mVolumeInfo = vol;
            if (mVolumeInfo.isMountedReadable()) {
                refresh();
            } else {
                mUIUpdateCallback.notifyNavigateBackward(getStateIdentifier());
            }
        }
    }
}
