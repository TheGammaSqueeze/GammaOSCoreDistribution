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

package com.android.server.nearby.common.servicemonitor;

import android.os.Handler;
import android.os.HandlerThread;

import com.android.modules.utils.HandlerExecutor;

import java.util.concurrent.Executor;

/**
 * Thread for asynchronous event processing. This thread is configured as
 * {@link android.os.Process#THREAD_PRIORITY_FOREGROUND}, which means more CPU
 * resources will be dedicated to it, and it will be treated like "a user
 * interface that the user is interacting with."
 * <p>
 * This thread is best suited for tasks that the user is actively waiting for,
 * or for tasks that the user expects to be executed immediately.
 *
 */
public final class ForegroundThread extends HandlerThread {
    private static ForegroundThread sInstance;
    private static Handler sHandler;
    private static HandlerExecutor sHandlerExecutor;

    private ForegroundThread() {
        super("nearbyfg", android.os.Process.THREAD_PRIORITY_FOREGROUND);
    }

    private static void ensureThreadLocked() {
        if (sInstance == null) {
            sInstance = new ForegroundThread();
            sInstance.start();
            sHandler = new Handler(sInstance.getLooper());
            sHandlerExecutor = new HandlerExecutor(sHandler);
        }
    }

    /** Get ForegroundThread singleton instance. */
    public static ForegroundThread get() {
        synchronized (ForegroundThread.class) {
            ensureThreadLocked();
            return sInstance;
        }
    }

    /** Get ForegroundThread singleton handler. */
    public static Handler getHandler() {
        synchronized (ForegroundThread.class) {
            ensureThreadLocked();
            return sHandler;
        }
    }

    /** Get ForegroundThread singleton executor. */
    public static Executor getExecutor() {
        synchronized (ForegroundThread.class) {
            ensureThreadLocked();
            return sHandlerExecutor;
        }
    }
}
