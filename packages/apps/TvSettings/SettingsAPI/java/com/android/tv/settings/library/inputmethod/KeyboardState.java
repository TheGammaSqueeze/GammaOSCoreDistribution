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

import static com.android.tv.settings.library.ManagerUtil.KEY_KEYBOARD_SETTINGS;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.UserHandle;
import android.text.TextUtils;
import android.util.ArraySet;
import android.view.inputmethod.InputMethodInfo;

import com.android.tv.settings.library.ManagerUtil;
import com.android.tv.settings.library.PreferenceCompat;
import com.android.tv.settings.library.UIUpdateCallback;
import com.android.tv.settings.library.data.PreferenceControllerState;
import com.android.tv.settings.library.overlay.FlavorUtils;
import com.android.tv.settings.library.settingslib.AutofillHelper;
import com.android.tv.settings.library.settingslib.DefaultAppInfo;
import com.android.tv.settings.library.settingslib.InputMethodHelper;
import com.android.tv.settings.library.util.AbstractPreferenceController;
import com.android.tv.settings.library.util.ResourcesUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * State to handle the business logic for KeyboardFragment.
 */
public class KeyboardState extends PreferenceControllerState {
    private static final String KEY_KEYBOARD_CATEGORY = "keyboardCategory";

    private static final String KEY_CURRENT_KEYBOARD = "currentKeyboard";

    private static final String KEY_AUTOFILL_CATEGORY = "autofillCategory";

    static final String KEY_CURRENT_AUTOFILL = "currentAutofill";

    private PreferenceCompat mKeyboardCategory;
    private PreferenceCompat mCurrentKeyboard;
    private PreferenceCompat mAutofillCategory;
    private PreferenceCompat mCurrentAutofill;
    private PreferenceCompat mKeyboardSettings;
    private PackageManager mPm;

    public KeyboardState(Context context,
            UIUpdateCallback callback) {
        super(context, callback);
    }


    @Override
    public void onAttach() {
        super.onAttach();
        mPm = mContext.getPackageManager();
    }

    @Override
    public void onCreate(Bundle extras) {
        super.onCreate(extras);
        mKeyboardCategory = mPreferenceCompatManager.getOrCreatePrefCompat(KEY_KEYBOARD_CATEGORY);
        mCurrentKeyboard = mPreferenceCompatManager.getOrCreatePrefCompat(KEY_CURRENT_KEYBOARD);
        mAutofillCategory = mPreferenceCompatManager.getOrCreatePrefCompat(KEY_AUTOFILL_CATEGORY);
        mCurrentAutofill = mPreferenceCompatManager.getOrCreatePrefCompat(KEY_CURRENT_AUTOFILL);
        mKeyboardSettings = mPreferenceCompatManager.getOrCreatePrefCompat(KEY_KEYBOARD_SETTINGS);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        // no-op
    }

    @Override
    public void onResume() {
        super.onResume();
        updateUi();
    }

    void updateUi() {
        updateAutofill();
        updateKeyboards();
    }

    private void updateKeyboards() {
        updateCurrentKeyboardPreference();
        updateKeyboardsSettings();
    }

    /**
     * Update autofill related preferences.
     */
    private void updateAutofill() {
        List<DefaultAppInfo> candidates = getAutofillCandidates();
        if (candidates.isEmpty()) {
            // No need to show keyboard category and autofill category.
            // Keyboard only preference screen:
            mKeyboardCategory.setVisible(false);
            mAutofillCategory.setVisible(false);
            mUIUpdateCallback.notifyUpdateScreenTitle(
                    getStateIdentifier(),
                    ResourcesUtil.getString(mContext, "system_keyboard"));
        } else {
            // Show both keyboard category and autofill category in keyboard & autofill screen.
            mKeyboardCategory.setVisible(true);
            mAutofillCategory.setVisible(true);
            updateCurrentAutofillPreference(candidates);
            updateAutofillSettings(candidates);
            mUIUpdateCallback.notifyUpdateScreenTitle(
                    getStateIdentifier(),
                    ResourcesUtil.getString(mContext, "system_keyboard_autofill"));
        }
        mUIUpdateCallback.notifyUpdate(getStateIdentifier(), mKeyboardCategory);
        mUIUpdateCallback.notifyUpdate(getStateIdentifier(), mAutofillCategory);
    }

    private List<DefaultAppInfo> getAutofillCandidates() {
        return AutofillHelper.getAutofillCandidates(mContext,
                mPm, UserHandle.myUserId());
    }

    private void updateCurrentAutofillPreference(List<DefaultAppInfo> candidates) {

        DefaultAppInfo app = AutofillHelper.getCurrentAutofill(mContext, candidates);

        CharSequence summary = app == null ? ResourcesUtil.getString(mContext, "autofill_none")
                : app.loadLabel();
        mCurrentAutofill.setSummary(summary.toString());
        mUIUpdateCallback.notifyUpdate(getStateIdentifier(), mCurrentAutofill);
    }

    private void updateAutofillSettings(List<DefaultAppInfo> candidates) {
        for (final DefaultAppInfo info : candidates) {
            final Intent settingsIntent = AutofillHelper.getAutofillSettingsIntent(mContext,
                    mPm, info);
            if (settingsIntent == null) {
                continue;
            }

            PreferenceCompat preferenceCompat = mPreferenceCompatManager.getOrCreatePrefCompat(
                    new String[]{KEY_AUTOFILL_CATEGORY, info.getKey()});
            preferenceCompat.setTitle(ResourcesUtil.getString(mContext,
                    "title_settings", info.loadLabel()));
            preferenceCompat.setIntent(settingsIntent);
            mCurrentAutofill.addChildPrefCompat(preferenceCompat);
        }
        mUIUpdateCallback.notifyUpdate(getStateIdentifier(), mCurrentAutofill);
    }

    private void updateKeyboardsSettings() {
        final PackageManager packageManager = mContext.getPackageManager();
        List<InputMethodInfo> enabledInputMethodInfos = InputMethodHelper
                .getEnabledSystemInputMethodList(mContext);

        final Set<String> enabledInputMethodKeys = new ArraySet<>(enabledInputMethodInfos.size());
        // Add per-IME settings
        for (final InputMethodInfo info : enabledInputMethodInfos) {
            final String uri = InputMethodHelper.getInputMethodsSettingsUri(mContext, info);
            final Intent settingsIntent = InputMethodHelper.getInputMethodSettingsIntent(info);
            if (uri == null && settingsIntent == null) {
                continue;
            }

            boolean useSlice = FlavorUtils.isTwoPanel(mContext) && uri != null;
            PreferenceCompat prefCompat = new PreferenceCompat(new String[]{
                    KEY_KEYBOARD_SETTINGS, info.getId()});
            prefCompat.setTitle(ResourcesUtil.getString(mContext, "title_settings",
                    info.loadLabel(packageManager)));
            if (useSlice) {
                prefCompat.setHasSlice(true);
                prefCompat.setSliceUri(uri);
                prefCompat.setIntent(null);
            } else {
                prefCompat.setHasSlice(false);
                prefCompat.setSliceUri(null);
                prefCompat.setIntent(settingsIntent);
            }
            mKeyboardSettings.addChildPrefCompat(prefCompat);
        }
        mUIUpdateCallback.notifyUpdate(getStateIdentifier(), mKeyboardSettings);
    }

    private void updateCurrentKeyboardPreference() {
        final PackageManager packageManager = mContext.getPackageManager();
        List<InputMethodInfo> enabledInputMethodInfos = InputMethodHelper
                .getEnabledSystemInputMethodList(mContext);
        final List<CharSequence> entries = new ArrayList<>(enabledInputMethodInfos.size());
        final List<CharSequence> values = new ArrayList<>(enabledInputMethodInfos.size());

        int defaultIndex = 0;
        final String defaultId = InputMethodHelper.getDefaultInputMethodId(mContext);

        for (final InputMethodInfo info : enabledInputMethodInfos) {
            entries.add(info.loadLabel(packageManager));
            final String id = info.getId();
            values.add(id);
            if (TextUtils.equals(id, defaultId)) {
                defaultIndex = values.size() - 1;
            }
        }

        mCurrentKeyboard.setEntries(entries.toArray(new CharSequence[entries.size()]));
        mCurrentKeyboard.setEntryValues(values.toArray(new CharSequence[values.size()]));
        if (entries.size() > 0) {
            mCurrentKeyboard.setValueIndex(defaultIndex);
        }
        mUIUpdateCallback.notifyUpdate(getStateIdentifier(), mCurrentKeyboard);
    }

    @Override
    public boolean onPreferenceChange(String[] key, Object newValue) {
        if (keyEquals(key, mCurrentKeyboard.getKey())) {
            InputMethodHelper.setDefaultInputMethodId(mContext, (String) newValue);
            return true;
        }
        return false;
    }

    @Override
    public int getStateIdentifier() {
        return ManagerUtil.STATE_KEYBOARD;
    }

    @Override
    protected List<AbstractPreferenceController> onCreatePreferenceControllers(Context context) {
        return null;
    }
}
