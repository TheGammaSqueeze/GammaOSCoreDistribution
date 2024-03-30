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

package com.android.systemui.car.decor;

import android.view.View;

import androidx.annotation.UiThread;

import com.android.systemui.dagger.SysUISingleton;
import com.android.systemui.dagger.qualifiers.Main;
import com.android.systemui.plugins.statusbar.StatusBarStateController;
import com.android.systemui.shade.ShadeExpansionStateManager;
import com.android.systemui.statusbar.events.PrivacyDotViewController;
import com.android.systemui.statusbar.events.SystemStatusAnimationScheduler;
import com.android.systemui.statusbar.phone.StatusBarContentInsetsProvider;
import com.android.systemui.statusbar.policy.ConfigurationController;

import org.jetbrains.annotations.NotNull;

import java.util.concurrent.Executor;

import javax.inject.Inject;

/**
 * Subclass of {@link PrivacyDotViewController}.
 * This class is used to avoid showing the privacy dot.
 */
@SysUISingleton
public class CarPrivacyChipViewController extends PrivacyDotViewController {

    @Inject
    public CarPrivacyChipViewController(
            @NotNull @Main Executor mainExecutor,
            @NotNull StatusBarStateController stateController,
            @NotNull ConfigurationController configurationController,
            @NotNull StatusBarContentInsetsProvider contentInsetsProvider,
            @NotNull SystemStatusAnimationScheduler animationScheduler,
            ShadeExpansionStateManager shadeExpansionStateManager) {
        super(mainExecutor, stateController, configurationController, contentInsetsProvider,
                animationScheduler, shadeExpansionStateManager);
    }

    @Override
    @UiThread
    public void showDotView(View dot, boolean animate) {
        // Do nothing.
    }

    @Override
    @UiThread
    public void hideDotView(View dot, boolean animate) {
        // Do nothing.
    }
}
