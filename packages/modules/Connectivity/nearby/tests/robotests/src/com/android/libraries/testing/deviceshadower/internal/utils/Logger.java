/*
 * Copyright 2021 The Android Open Source Project
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

package com.android.libraries.testing.deviceshadower.internal.utils;

import android.text.TextUtils;
import android.util.Log;

/**
 * Logger class to provide formatted log for Device Shadower.
 *
 * <p>Log is formatted as "[TAG] [Keyword1, Keyword2 ...] Log Message Body".</p>
 */
public class Logger {

    private static final String TAG = "DeviceShadower";

    private final String mTag;
    private final String mPrefix;

    public Logger(String tag, String... keywords) {
        mTag = tag;
        mPrefix = buildPrefix(keywords);
    }

    public static Logger create(String... keywords) {
        return new Logger(TAG, keywords);
    }

    private static String buildPrefix(String... keywords) {
        if (keywords.length == 0) {
            return "";
        }
        return String.format(" [%s] ", TextUtils.join(", ", keywords));
    }

    /**
     * @see Log#e(String, String)
     */
    public void e(String msg) {
        Log.e(mTag, format(msg));
    }

    /**
     * @see Log#e(String, String, Throwable)
     */
    public void e(String msg, Throwable throwable) {
        Log.e(mTag, format(msg), throwable);
    }

    /**
     * @see Log#d(String, String)
     */
    public void d(String msg) {
        Log.d(mTag, format(msg));
    }

    /**
     * @see Log#d(String, String, Throwable)
     */
    public void d(String msg, Throwable throwable) {
        Log.d(mTag, format(msg), throwable);
    }

    /**
     * @see Log#i(String, String)
     */
    public void i(String msg) {
        Log.i(mTag, format(msg));
    }

    /**
     * @see Log#i(String, String, Throwable)
     */
    public void i(String msg, Throwable throwable) {
        Log.i(mTag, format(msg), throwable);
    }

    /**
     * @see Log#v(String, String)
     */
    public void v(String msg) {
        Log.v(mTag, format(msg));
    }

    /**
     * @see Log#v(String, String, Throwable)
     */
    public void v(String msg, Throwable throwable) {
        Log.v(mTag, format(msg), throwable);
    }

    /**
     * @see Log#w(String, String)
     */
    public void w(String msg) {
        Log.w(mTag, format(msg));
    }

    /**
     * @see Log#w(String, Throwable)
     */
    public void w(Throwable throwable) {
        Log.w(mTag, null, throwable);
    }

    /**
     * @see Log#w(String, String, Throwable)
     */
    public void w(String msg, Throwable throwable) {
        Log.w(mTag, format(msg), throwable);
    }

    /**
     * @see Log#wtf(String, String)
     */
    public void wtf(String msg) {
        Log.wtf(mTag, format(msg));
    }

    /**
     * @see Log#wtf(String, String, Throwable)
     */
    public void wtf(String msg, Throwable throwable) {
        Log.wtf(mTag, format(msg), throwable);
    }

    /**
     * @see Log#isLoggable(String, int)
     */
    public boolean isLoggable(int level) {
        return Log.isLoggable(mTag, level);
    }

    /**
     * @see Log#println(int, String, String)
     */
    public int println(int priority, String msg) {
        return Log.println(priority, mTag, format(msg));
    }

    private String format(String msg) {
        return mPrefix + msg;
    }
}
