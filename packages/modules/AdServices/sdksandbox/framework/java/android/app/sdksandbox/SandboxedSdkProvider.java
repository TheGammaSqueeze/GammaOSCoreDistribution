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

package android.app.sdksandbox;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.os.Bundle;
import android.view.View;

import java.util.concurrent.Executor;

/**
 * Encapsulates API between sdk sandbox and sdk loaded into it.
 * @hide
 */
public abstract class SandboxedSdkProvider {

    /**
     * Initializes sdk.
     */
    public abstract void initSdk(
            @NonNull SandboxedSdkContext sandboxedSdkContext, @NonNull Bundle params,
            @NonNull Executor executor, @NonNull InitSdkCallback callback);

    /**
     * Returns view that will be used for remote rendering.
     */
    @NonNull
    public abstract View getView(Context windowContext, @NonNull Bundle params);

    /**
     * Called when extra data sent from the app is received by code.
     */
    public abstract void onExtraDataReceived(@NonNull Bundle extraData);

    /**
     * Callback for {@link #initSdk}.
     */
    public interface InitSdkCallback {
        /**
         * Called when sdk is successfully initialized.
         */
        void onInitSdkFinished(@NonNull Bundle extraParams);

        /**
         * Called when sdk fails to initialize.
         */
        void onInitSdkError(@Nullable String errorMessage);
    }
}
