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

package com.android.systemui.car.statusicon.ui;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.content.res.Resources;
import android.testing.AndroidTestingRunner;
import android.view.View;

import androidx.test.filters.SmallTest;

import com.android.systemui.SysuiTestCase;
import com.android.systemui.broadcast.BroadcastDispatcher;
import com.android.systemui.car.CarServiceProvider;
import com.android.systemui.car.CarSystemUiTest;
import com.android.systemui.statusbar.policy.ConfigurationController;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;

import java.util.Map;

@CarSystemUiTest
@RunWith(AndroidTestingRunner.class)
@SmallTest
public class QuickControlsEntryPointsControllerTest extends SysuiTestCase {
    private QuickControlsEntryPointsController mQuickControlsEntryPointsController;

    @Mock
    private Resources mResources;
    @Mock
    private CarServiceProvider mCarServiceProvider;
    @Mock
    private BroadcastDispatcher mBroadcastDispatcher;
    @Mock
    private ConfigurationController mConfigurationController;
    @Mock
    private Map mIconControllerCreators;

    @Before
    public void setUp() {
        mQuickControlsEntryPointsController = new QuickControlsEntryPointsController(
                mContext,
                mContext.getOrCreateTestableResources().getResources(),
                mCarServiceProvider,
                mBroadcastDispatcher,
                mConfigurationController,
                mIconControllerCreators);
    }

    @Test
    public void getClassNameOfSelectedView_getsSelectedView() {
        String selectedClassName = "selectedClassName";
        View selectedView = mock(View.class);
        when(selectedView.isSelected()).thenReturn(true);
        Map<String, View> statusIconViewClassMap = Map.of("className", mock(View.class),
                selectedClassName, selectedView);
        mQuickControlsEntryPointsController.setStatusIconViewClassMap(statusIconViewClassMap);

        String resultClassName = mQuickControlsEntryPointsController.getClassNameOfSelectedView();

        assertThat(resultClassName).isEqualTo(selectedClassName);
    }
}
