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

package com.android.tv.settings.about;

import android.content.Context;
import android.util.AttributeSet;

import com.android.tv.settings.R;
import com.android.tv.settings.compat.TsDialogPreference;

/**
 * Implementation of {@link TsDialogPreference} to show the consumer information
 * {@link androidx.preference.Preference Preference}.
 */
public class ConsumerInformationDialogPreferenceCompat extends TsDialogPreference {
    public ConsumerInformationDialogPreferenceCompat(Context context, AttributeSet attrs,
            int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public ConsumerInformationDialogPreferenceCompat(Context context, AttributeSet attrs,
            int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public ConsumerInformationDialogPreferenceCompat(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ConsumerInformationDialogPreferenceCompat(Context context) {
        super(context);
    }

    @Override
    public CharSequence getDialogMessage() {
        return getContext().getString(R.string.consumer_information_message);
    }

    @Override
    public CharSequence getPositiveButtonText() {
        return getContext().getString(R.string.consumer_information_button_ok);
    }
}
