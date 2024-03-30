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
package com.android.customization.model.clock.custom;

import com.android.customization.model.CustomizationManager;

import com.google.common.collect.Lists;

/**
 * {@link CustomizationManager} for clock faces.
 */
public class ClockCustomManager implements CustomizationManager<ClockOption> {
    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public void apply(ClockOption option, Callback callback) {
        // TODO(/b241966062) execute applying the clock when user selects a clock
    }

    @Override
    public void fetchOptions(OptionsFetchedListener<ClockOption> callback, boolean reload) {
        // TODO(/b241966062) fetch the real clock metadata from the ClockRegistry
        callback.onOptionsLoaded(
                Lists.newArrayList(new ClockOption(), new ClockOption(), new ClockOption(),
                        new ClockOption(), new ClockOption()));
    }
}
