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
package com.android.customization.model.grid;

import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;

/** The class to store status of the grid fragment view. */
public class GridOptionViewModel extends ViewModel {
    private static final String SELECTED_OPTION_KEY = "selected_option";
    private static final String BOTTOM_ACTION_BAR_VISIBLE_KEY = "bottom_action_bar_visible";

    private SavedStateHandle mState;

    public GridOptionViewModel(SavedStateHandle savedStateHandle) {
        mState = savedStateHandle;
    }

    /** Gets selected {@link GridOption} from {@link SavedStateHandle} */
    public GridOption getSelectedOption() {
        return mState.get(SELECTED_OPTION_KEY);
    }

    /** Sets selected {@link GridOption} to {@link SavedStateHandle} */
    public void setSelectedOption(GridOption selectedOption) {
        mState.set(SELECTED_OPTION_KEY, selectedOption);
    }

    /** Gets bottom action bar visible from {@link SavedStateHandle} */
    public boolean getBottomActionBarVisible() {
        return mState.contains(BOTTOM_ACTION_BAR_VISIBLE_KEY)
                ? mState.get(BOTTOM_ACTION_BAR_VISIBLE_KEY)
                : false;
    }

    /** Sets bottom action bar visible to {@link SavedStateHandle} */
    public void setBottomActionBarVisible(boolean bottomActionBarVisible) {
        mState.set(BOTTOM_ACTION_BAR_VISIBLE_KEY, bottomActionBarVisible);
    }
}
