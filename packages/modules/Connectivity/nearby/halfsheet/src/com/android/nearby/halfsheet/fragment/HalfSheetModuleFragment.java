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
package com.android.nearby.halfsheet.fragment;

import static com.android.nearby.halfsheet.HalfSheetActivity.TAG;
import static com.android.nearby.halfsheet.fragment.HalfSheetModuleFragment.HalfSheetFragmentState.NOT_STARTED;

import android.os.Bundle;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;


/** Base class for all of the half sheet fragment. */
public abstract class HalfSheetModuleFragment extends Fragment {

    static final int TEXT_ANIMATION_DURATION_MILLISECONDS = 200;

    HalfSheetFragmentState mFragmentState = NOT_STARTED;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    /** UI states of the half-sheet fragment. */
    public enum HalfSheetFragmentState {
        NOT_STARTED, // Initial status
        FOUND_DEVICE, // When a device is found found from Nearby scan service
        PAIRING, // When user taps 'Connect' and Fast Pair stars pairing process
        PAIRED_LAUNCHABLE, // When pair successfully
        // and we found a launchable companion app installed
        PAIRED_UNLAUNCHABLE, // When pair successfully
        // but we cannot find a companion app to launch it
        FAILED, // When paring was failed
        FINISHED // When the activity is about to end finished.
    }

    /**
     * Returns the {@link HalfSheetFragmentState} to the parent activity.
     *
     * <p>Overrides this method if the fragment's state needs to be preserved in the parent
     * activity.
     */
    public HalfSheetFragmentState getFragmentState() {
        return mFragmentState;
    }

    void setState(HalfSheetFragmentState state) {
        Log.v(TAG, "Settings state from " + mFragmentState + " to " + state);
        mFragmentState = state;
    }

    /**
     * Populate data to UI widgets according to the latest {@link HalfSheetFragmentState}.
     */
    abstract void invalidateState();
}
