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

package com.android.tv.settings.library.inputmethod;

import android.annotation.DrawableRes;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;

import com.android.tv.settings.library.ManagerUtil;
import com.android.tv.settings.library.PreferenceCompat;
import com.android.tv.settings.library.UIUpdateCallback;
import com.android.tv.settings.library.data.PreferenceControllerState;
import com.android.tv.settings.library.settingslib.InputMethodAndSubtypeUtilCompat;
import com.android.tv.settings.library.settingslib.InputMethodSettingValuesWrapper;
import com.android.tv.settings.library.util.AbstractPreferenceController;
import com.android.tv.settings.library.util.ResourcesUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * State to handle the business logic for AvailableVirtualKeyboardFragment.
 */
public class AvailableVirtualKeyboadState extends PreferenceControllerState
        implements InputMethodPreferenceController.OnSavePreferenceListener {
    private InputMethodSettingValuesWrapper mInputMethodSettingValues;
    private InputMethodManager mImm;
    private DevicePolicyManager mDpm;

    public AvailableVirtualKeyboadState(Context context,
            UIUpdateCallback callback) {
        super(context, callback);
    }

    @Override
    public void onResume() {
        super.onResume();
        mInputMethodSettingValues.refreshAllInputMethodAndSubtypes();
        updateInputMethodPreferenceViews();
    }


    @Override
    public void onCreate(Bundle extras) {
        super.onCreate(extras);
        mUIUpdateCallback.notifyUpdateScreenTitle(getStateIdentifier(),
                ResourcesUtil.getString(mContext, "available_virtual_keyboard_category"));
        mInputMethodSettingValues = InputMethodSettingValuesWrapper.getInstance(mContext);
        mImm = mContext.getSystemService(InputMethodManager.class);
        mDpm = mContext.getSystemService(DevicePolicyManager.class);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        // no-op
    }

    private void updateInputMethodPreferenceViews() {
        mInputMethodSettingValues.refreshAllInputMethodAndSubtypes();
        // Clear existing "InputMethodPreference"s
        List<String> permittedList = mDpm.getPermittedInputMethodsForCurrentUser();
        final PackageManager packageManager = mContext.getPackageManager();
        final List<InputMethodInfo> imis = mInputMethodSettingValues.getInputMethodList();
        final int numImis = (imis == null ? 0 : imis.size());
        List<PreferenceCompat> prefCompats = new ArrayList<>();
        mPreferenceControllers.clear();
        for (int i = 0; i < numImis; ++i) {
            final InputMethodInfo imi = imis.get(i);
            final boolean isAllowedByOrganization = permittedList == null
                    || permittedList.contains(imi.getPackageName());
            final InputMethodPreferenceController prefController =
                    new InputMethodPreferenceController(
                            mContext, mUIUpdateCallback, getStateIdentifier(),
                            mPreferenceCompatManager, imi, true,
                            isAllowedByOrganization, this);
            mPreferenceControllers.add(prefController);
            prefController.init();
            prefController.getPrefCompat().setIcon(getInputMethodIcon(packageManager, imi));
            prefController.notifyChange();
            prefCompats.add(prefController.getPrefCompat());
        }
        mUIUpdateCallback.notifyUpdateAll(getStateIdentifier(), prefCompats);
    }

    @Nullable
    private static Drawable loadDrawable(@NonNull final PackageManager packageManager,
            @NonNull final String packageName, @DrawableRes final int resId,
            @NonNull final ApplicationInfo applicationInfo) {
        if (resId == 0) {
            return null;
        }
        try {
            return packageManager.getDrawable(packageName, resId, applicationInfo);
        } catch (Exception e) {
            return null;
        }
    }

    @NonNull
    private static Drawable getInputMethodIcon(@NonNull final PackageManager packageManager,
            @NonNull final InputMethodInfo imi) {
        final ServiceInfo si = imi.getServiceInfo();
        final ApplicationInfo ai = si != null ? si.applicationInfo : null;
        final String packageName = imi.getPackageName();
        if (si == null || ai == null || packageName == null) {
            return new ColorDrawable(Color.TRANSPARENT);
        }
        // We do not use ServiceInfo#loadLogo() and ServiceInfo#loadIcon here since those methods
        // internally have some fallback rules, which we want to do manually.
        Drawable drawable = loadDrawable(packageManager, packageName, si.logo, ai);
        if (drawable != null) {
            return drawable;
        }
        drawable = loadDrawable(packageManager, packageName, si.icon, ai);
        if (drawable != null) {
            return drawable;
        }
        // We do not use ApplicationInfo#loadLogo() and ApplicationInfo#loadIcon here since those
        // methods internally have some fallback rules, which we want to do manually.
        drawable = loadDrawable(packageManager, packageName, ai.logo, ai);
        if (drawable != null) {
            return drawable;
        }
        drawable = loadDrawable(packageManager, packageName, ai.icon, ai);
        if (drawable != null) {
            return drawable;
        }
        return new ColorDrawable(Color.TRANSPARENT);
    }

    @Override
    public int getStateIdentifier() {
        return ManagerUtil.STATE_AVAILABLE_KEYBOARD;
    }

    @Override
    protected List<AbstractPreferenceController> onCreatePreferenceControllers(Context context) {
        return null;
    }

    @Override
    public void onSaveInputMethodPreference(InputMethodPreferenceController pref) {
        final boolean hasHardwareKeyboard = mContext.getResources().getConfiguration().keyboard
                == Configuration.KEYBOARD_QWERTY;
        InputMethodAndSubtypeUtilCompat.saveInputMethodSubtypeList(mContext,
                mPreferenceCompatManager,
                mContext.getContentResolver(), mImm.getInputMethodList(), hasHardwareKeyboard);
        // Update input method settings and preference list.
        mInputMethodSettingValues.refreshAllInputMethodAndSubtypes();
        updateInputMethodPreferenceViews();
    }
}
