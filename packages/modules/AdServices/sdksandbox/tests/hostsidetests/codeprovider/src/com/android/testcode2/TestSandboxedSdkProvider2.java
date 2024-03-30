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

package com.android.testcode2;

import android.app.sdksandbox.SandboxedSdkContext;
import android.app.sdksandbox.SandboxedSdkProvider;
import android.content.Context;
import android.os.Bundle;
import android.view.View;

import java.util.concurrent.Executor;

public class TestSandboxedSdkProvider2 extends SandboxedSdkProvider {

    @Override
    public void initSdk(SandboxedSdkContext context, Bundle params,
            Executor executor, InitSdkCallback callback) {
        callback.onInitSdkFinished(null);
    }

    @Override
    public View getView(Context windowContext, Bundle params) {
        return null;
    }

    @Override
    public void onExtraDataReceived(Bundle extraData) {
    }
}
