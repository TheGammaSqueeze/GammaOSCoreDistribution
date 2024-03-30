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

package com.android.car.carlauncher;

import android.app.Activity;

import com.android.wm.shell.ShellTaskOrganizer;
import com.android.wm.shell.common.SyncTransactionQueue;

import java.util.concurrent.Executor;

/**
 * A Semi-controlled {@link CarTaskView} is where the apps are meant to stay temporarily. It always
 * works when a {@link LaunchRootCarTaskView} has been set up.
 *
 * It serves these use-cases:
 * <ul>
 *     <li>Should be used when the apps that are meant to be in it can be started from anywhere
 *     in the system. i.e. when the host app has no control over their launching.</li>
 *     <li>Suitable for apps like Assistant or Setup-Wizard.</li>
 * </ul>
 */
final class SemiControlledCarTaskView extends CarTaskView {
    private final Executor mCallbackExecutor;
    private final SemiControlledCarTaskViewCallbacks mCallbacks;

    public SemiControlledCarTaskView(Activity context,
            ShellTaskOrganizer organizer,
            SyncTransactionQueue syncQueue,
            Executor callbackExecutor,
            SemiControlledCarTaskViewCallbacks callbacks) {
        super(context, organizer, syncQueue);
        mCallbacks = callbacks;
        mCallbackExecutor = callbackExecutor;
        mCallbackExecutor.execute(() -> mCallbacks.onTaskViewCreated(this));
    }

    @Override
    protected void onCarTaskViewInitialized() {
        super.onCarTaskViewInitialized();
        mCallbackExecutor.execute(() -> mCallbacks.onTaskViewReady());
    }

    /**
     * @return the underlying {@link SemiControlledCarTaskViewCallbacks}.
     */
    SemiControlledCarTaskViewCallbacks getCallbacks() {
        return mCallbacks;
    }
}
