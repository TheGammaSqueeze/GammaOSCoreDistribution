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

package com.android.bedstead.nene.logging;

import com.android.bedstead.nene.TestApis;

import java.util.function.Function;

/**
 * Logger used for tests and test infrastructure.
 *
 * <p>By default, logs will be dropped silently.
 *
 * <p>To change this, pass the LOGGING instrumentation argument.
 *
 * <p>E.g. {@code -- --instrumentation-arg LOGGING ADB} to log to ADB.
 */
public interface Logger extends CommonLogger {

    /**
     * The key to use to pass in the type of logger to use.
     *
     * <p>Defaults to silently dropping logs.
     */
    String LOGGER_KEY = "LOGGER";

    Function<Object, Logger> LOG_CONSTRUCTOR =
            TestApis.instrumentation().arguments().getString(LOGGER_KEY, "")
                    .equals(AdbLogger.KEY) ? AdbLogger::new : (object) -> VoidLogger.sInstance;

    /** Create a {@link Logger} for the given object. */
    static Logger forInstance(Object object) {
        return LOG_CONSTRUCTOR.apply(object);
    }
}
