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
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.storage.StorageManager;
import android.os.storage.VolumeRecord;
import android.text.TextUtils;
import android.util.Log;

import com.android.tv.settings.library.ManagerUtil;
import com.android.tv.settings.library.PreferenceCompat;
import com.android.tv.settings.library.UIUpdateCallback;
import com.android.tv.settings.library.data.PreferenceControllerState;
import com.android.tv.settings.library.util.AbstractPreferenceController;
import com.android.tv.settings.library.util.ResourcesUtil;

import java.util.List;

/** State to handle missing storage screen. */
public class MissingStorageState extends PreferenceControllerState {
    private static final String TAG = "MissingStorageState";

    private static final String KEY_FORGET = "forget";
    private static final String INTENT_CONFIRMATION = "android.settings.ui.CONFIRM";
    private static final String EXTRA_GUIDANCE_TITLE = "guidancetitle";
    private static final String EXTRA_GUIDANCE_SUBTITLE = "guidanceSubtitle";

    private static final int REQUEST_FORGET_PRIVATE = 1;

    private String mFsUuid;
    private StorageManager mStorageManager;
    PreferenceCompat mForgetPreferenceCompat;

    public static void prepareArgs(Bundle b, String fsUuid) {
        b.putString(VolumeRecord.EXTRA_FS_UUID, fsUuid);
    }

    public MissingStorageState(Context context,
            UIUpdateCallback callback) {
        super(context, callback);
    }

    @Override
    public void onCreate(Bundle extras) {
        super.onCreate(extras);
        mFsUuid = extras.getString(VolumeRecord.EXTRA_FS_UUID);
        mStorageManager = mContext.getSystemService(StorageManager.class);
        mStorageManager.registerListener(new StorageEventListener());
        mForgetPreferenceCompat = mPreferenceCompatManager.getOrCreatePrefCompat(KEY_FORGET);
    }

    @Override
    public boolean onPreferenceTreeClick(String[] key, boolean status) {
        if (KEY_FORGET.equals(key[0])) {
            Intent i = new Intent(INTENT_CONFIRMATION);
            i.putExtra(EXTRA_GUIDANCE_TITLE, ResourcesUtil.getString(
                    mContext, "storage_wizard_forget_confirm_title"));
            i.putExtra(EXTRA_GUIDANCE_SUBTITLE, ResourcesUtil.getString(
                    mContext, "storage_wizard_forget_confirm_description"));
            ((Activity) mContext).startActivityForResult(i,
                    ManagerUtil.calculateCompoundCode(
                            getStateIdentifier(), REQUEST_FORGET_PRIVATE
                    ));
            return true;
        }
        return super.onPreferenceTreeClick(key, status);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_FORGET_PRIVATE:
                if (resultCode == Activity.RESULT_OK) {
                    mContext.getSystemService(StorageManager.class).forgetVolume(mFsUuid);
                }
                break;
            default:
                // no-op
        }
    }

    @Override
    public int getStateIdentifier() {
        return ManagerUtil.STATE_MISSING_STORAGE;
    }

    @Override
    protected List<AbstractPreferenceController> onCreatePreferenceControllers(Context context) {
        return null;
    }

    private class StorageEventListener extends android.os.storage.StorageEventListener {

        @Override
        public void onVolumeForgotten(String fsUuid) {
            if (!TextUtils.equals(fsUuid, mFsUuid)) {
                return;
            }
            if (mStorageManager.findRecordByUuid(fsUuid) == null) {
                mUIUpdateCallback.notifyNavigateBackward(getStateIdentifier());
                Log.i(TAG, "FsUuid " + mFsUuid + " vanished while resumed");
            }
        }
    }
}
