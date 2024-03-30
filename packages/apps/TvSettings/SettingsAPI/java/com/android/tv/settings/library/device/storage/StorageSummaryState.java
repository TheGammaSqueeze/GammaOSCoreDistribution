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

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.storage.DiskInfo;
import android.os.storage.StorageManager;
import android.os.storage.VolumeInfo;
import android.os.storage.VolumeRecord;
import android.util.ArraySet;
import android.util.Log;

import com.android.tv.settings.library.ManagerUtil;
import com.android.tv.settings.library.PreferenceCompat;
import com.android.tv.settings.library.UIUpdateCallback;
import com.android.tv.settings.library.data.PreferenceControllerState;
import com.android.tv.settings.library.util.AbstractPreferenceController;
import com.android.tv.settings.library.util.ResourcesUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** State to handle storage summary screen. */
public class StorageSummaryState extends PreferenceControllerState {
    private static final String TAG = "StorageSummaryState";
    private static final String KEY_DEVICE_CATEGORY = "device_storage";
    private static final String KEY_REMOVABLE_CATEGORY = "removable_storage";

    private static final int REFRESH_DELAY_MILLIS = 500;

    private StorageManager mStorageManager;
    private PreferenceCompat mRemovableCategory;
    private PreferenceCompat mDeviceCategory;
    private final StorageSummaryState.StorageEventListener
            mStorageEventListener = new StorageSummaryState.StorageEventListener();

    private final Handler mHandler = new Handler();
    private final Runnable mRefreshRunnable = new Runnable() {
        @Override
        public void run() {
            refresh();
        }
    };

    public StorageSummaryState(Context context,
            UIUpdateCallback callback) {
        super(context, callback);
    }

    @Override
    public void onCreate(Bundle extras) {
        super.onCreate(extras);
        mStorageManager = mContext.getSystemService(StorageManager.class);
        mRemovableCategory = mPreferenceCompatManager.getOrCreatePrefCompat(KEY_REMOVABLE_CATEGORY);
        mDeviceCategory = mPreferenceCompatManager.getOrCreatePrefCompat(KEY_DEVICE_CATEGORY);
        mRemovableCategory.setVisible(false);
    }


    @Override
    public void onStart() {
        super.onStart();
        mStorageManager.registerListener(mStorageEventListener);
    }

    @Override
    public void onResume() {
        super.onResume();
        mHandler.removeCallbacks(mRefreshRunnable);
        // Delay to allow entrance animations to complete
        mHandler.postDelayed(mRefreshRunnable, REFRESH_DELAY_MILLIS);
    }

    @Override
    public void onPause() {
        super.onPause();
        mHandler.removeCallbacks(mRefreshRunnable);
    }

    @Override
    public void onStop() {
        super.onStop();
        mStorageManager.unregisterListener(mStorageEventListener);
    }

    private void refresh() {
        final List<VolumeInfo> volumes = mStorageManager.getVolumes();
        volumes.sort(VolumeInfo.getDescriptionComparator());

        final List<VolumeInfo> privateVolumes = new ArrayList<>(volumes.size());
        final List<VolumeInfo> publicVolumes = new ArrayList<>(volumes.size());

        // Find mounted volumes
        for (final VolumeInfo vol : volumes) {
            if (vol.getType() == VolumeInfo.TYPE_PRIVATE) {
                privateVolumes.add(vol);
            } else if (vol.getType() == VolumeInfo.TYPE_PUBLIC) {
                publicVolumes.add(vol);
            } else {
                Log.d(TAG, "Skipping volume " + vol.toString());
            }
        }

        // Find missing private filesystems
        final List<VolumeRecord> volumeRecords = mStorageManager.getVolumeRecords();
        final List<VolumeRecord> privateMissingVolumes = new ArrayList<>(volumeRecords.size());

        for (final VolumeRecord record : volumeRecords) {
            if (record.getType() == VolumeInfo.TYPE_PRIVATE
                    && mStorageManager.findVolumeByUuid(record.getFsUuid()) == null) {
                privateMissingVolumes.add(record);
            }
        }

        // Find unreadable disks
        final List<DiskInfo> disks = mStorageManager.getDisks();
        final List<DiskInfo> unsupportedDisks = new ArrayList<>(disks.size());
        for (final DiskInfo disk : disks) {
            if (disk.volumeCount == 0 && disk.size > 0) {
                unsupportedDisks.add(disk);
            }
        }

        final Set<String> touchedDeviceKeys =
                new ArraySet<>(privateVolumes.size() + privateMissingVolumes.size());

        mDeviceCategory.clearChildPrefCompats();
        mRemovableCategory.clearChildPrefCompats();
        for (final VolumeInfo volumeInfo : privateVolumes) {
            final String key = makeKeyForVolPref(volumeInfo);
            touchedDeviceKeys.add(key);
            PreferenceCompat preferenceCompat = mPreferenceCompatManager.getOrCreatePrefCompat(
                    new String[]{KEY_DEVICE_CATEGORY, key});
            refreshForVolPref(mContext, mStorageManager, volumeInfo, preferenceCompat);
            mDeviceCategory.addChildPrefCompat(preferenceCompat);
        }

        for (final VolumeRecord volumeRecord : privateMissingVolumes) {
            final String key = makeKeyForMissingPref(volumeRecord);
            touchedDeviceKeys.add(key);
            PreferenceCompat preferenceCompat = mPreferenceCompatManager.getOrCreatePrefCompat(
                    new String[]{KEY_DEVICE_CATEGORY, key});
            refreshForMissingPref(mContext, volumeRecord, preferenceCompat);
            mDeviceCategory.addChildPrefCompat(preferenceCompat);
        }


        final int publicCount = publicVolumes.size() + unsupportedDisks.size();
        final Set<String> touchedRemovableKeys = new ArraySet<>(publicCount);
        // Only show section if there are public/unknown volumes present
        mRemovableCategory.setVisible(publicCount > 0);

        for (final VolumeInfo volumeInfo : publicVolumes) {
            final String key = makeKeyForVolPref(volumeInfo);
            touchedRemovableKeys.add(key);
            PreferenceCompat preferenceCompat = mPreferenceCompatManager.getOrCreatePrefCompat(
                    new String[]{KEY_REMOVABLE_CATEGORY, key});
            refreshForVolPref(mContext, mStorageManager, volumeInfo, preferenceCompat);
            mRemovableCategory.addChildPrefCompat(preferenceCompat);
        }
        for (final DiskInfo diskInfo : unsupportedDisks) {
            final String key = makeKeyForUnsupportedDiskPref(diskInfo);
            touchedRemovableKeys.add(key);
            PreferenceCompat preferenceCompat = mPreferenceCompatManager.getOrCreatePrefCompat(
                    new String[]{KEY_REMOVABLE_CATEGORY, key});
            refreshForUnsupportedDiskPref(mContext, diskInfo, preferenceCompat);
            mRemovableCategory.addChildPrefCompat(preferenceCompat);
        }
        mUIUpdateCallback.notifyUpdate(getStateIdentifier(), mDeviceCategory);
        mUIUpdateCallback.notifyUpdate(getStateIdentifier(), mRemovableCategory);
    }

    private void refreshForVolPref(Context context, StorageManager storageManager,
            VolumeInfo volumeInfo, PreferenceCompat preferenceCompat) {
        final String description = storageManager
                .getBestVolumeDescription(volumeInfo);
        preferenceCompat.setTitle(description);
        if (volumeInfo.isMountedReadable()) {
            preferenceCompat.setSummary(getSizeString(context, volumeInfo));
        } else {
            preferenceCompat.setSummary(ResourcesUtil.getString(
                    context, "storage_unmount_success, description"));
        }
        Bundle b = new Bundle();
        StorageState.prepareArgs(b, volumeInfo);
        preferenceCompat.setExtras(b);
        preferenceCompat.setNextState(ManagerUtil.STATE_STORAGE);
    }

    private void refreshForUnsupportedDiskPref(Context context, DiskInfo info,
            PreferenceCompat preferenceCompat) {
        preferenceCompat.setTitle(info.getDescription());
    }

    private void refreshForMissingPref(Context context, VolumeRecord volumeRecord,
            PreferenceCompat preferenceCompat) {
        preferenceCompat.setTitle(volumeRecord.getNickname());
        preferenceCompat.setSummary(ResourcesUtil.getString(mContext, "storage_not_connected"));
        Bundle b = new Bundle();
        MissingStorageState.prepareArgs(b, volumeRecord.getFsUuid());
        preferenceCompat.setExtras(b);
        preferenceCompat.setNextState(ManagerUtil.STATE_MISSING_STORAGE);
    }

    private static String getSizeString(Context context, VolumeInfo vol) {
        final File path = vol.getPath();
        if (vol.isMountedReadable() && path != null) {
            return String.format(ResourcesUtil.getString(context, "storage_size"),
                    StorageState.formatSize(context, path.getTotalSpace()));
        } else {
            return null;
        }
    }

    private static String makeKeyForVolPref(VolumeInfo volumeInfo) {
        return "VolPref:" + volumeInfo.getId();
    }

    private static String makeKeyForUnsupportedDiskPref(DiskInfo info) {
        return "UnsupportedPref:" + info.getId();
    }

    private static String makeKeyForMissingPref(VolumeRecord volumeRecord) {
        return "MissingPref:" + volumeRecord.getFsUuid();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {

    }

    @Override
    public int getStateIdentifier() {
        return ManagerUtil.STATE_STORAGE_SUMMARY;
    }

    private class StorageEventListener extends android.os.storage.StorageEventListener {
        @Override
        public void onStorageStateChanged(String path, String oldState, String newState) {
            refresh();
        }

        @Override
        public void onVolumeStateChanged(VolumeInfo vol, int oldState, int newState) {
            refresh();
        }

        @Override
        public void onVolumeRecordChanged(VolumeRecord rec) {
            refresh();
        }

        @Override
        public void onVolumeForgotten(String fsUuid) {
            refresh();
        }

        @Override
        public void onDiskScanned(DiskInfo disk, int volumeCount) {
            refresh();
        }

        @Override
        public void onDiskDestroyed(DiskInfo disk) {
            refresh();
        }

    }

    @Override
    protected List<AbstractPreferenceController> onCreatePreferenceControllers(Context context) {
        return null;
    }
}
