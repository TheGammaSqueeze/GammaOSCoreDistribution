/*
 * Copyright 2022 The Android Open Source Project
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

package com.google.snippet.uwb;

import android.app.UiAutomation;
import android.content.Context;
import android.uwb.UwbManager;

import androidx.test.platform.app.InstrumentationRegistry;

import com.google.android.mobly.snippet.Snippet;
import com.google.android.mobly.snippet.rpc.Rpc;
import com.google.android.mobly.snippet.util.Log;

import java.lang.reflect.Method;

/** Snippet class exposing Android APIs for Uwb. */
public class UwbManagerSnippet implements Snippet {
    private static class UwbManagerSnippetException extends Exception {
        private static final long serialVersionUID = 1;

        UwbManagerSnippetException(String msg) {
            super(msg);
        }

        UwbManagerSnippetException(String msg, Throwable err) {
            super(msg, err);
        }
    }
    private final UwbManager mUwbManager;
    private final Context mContext;

    public UwbManagerSnippet() throws Throwable {
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        mUwbManager = mContext.getSystemService(UwbManager.class);
        adoptShellPermission();
    }

    /** Get the UWB state. */
    @Rpc(description = "Get Uwb state")
    public boolean isUwbEnabled() {
        return mUwbManager.isUwbEnabled();
    }

    /** Get the UWB state. */
    @Rpc(description = "Set Uwb state")
    public void setUwbEnabled(boolean enabled) {
        mUwbManager.setUwbEnabled(enabled);
    }

    @Override
    public void shutdown() {}

    private void adoptShellPermission() throws Throwable {
        UiAutomation uia = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        uia.adoptShellPermissionIdentity();
        try {
            Class<?> cls = Class.forName("android.app.UiAutomation");
            Method destroyMethod = cls.getDeclaredMethod("destroy");
            destroyMethod.invoke(uia);
        } catch (ReflectiveOperationException e) {
            throw new UwbManagerSnippetException("Failed to cleaup Ui Automation", e);
        }
    }
}
