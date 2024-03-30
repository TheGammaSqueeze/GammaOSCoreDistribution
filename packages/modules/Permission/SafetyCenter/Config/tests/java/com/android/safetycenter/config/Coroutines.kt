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

package com.android.safetycenter.config

import android.util.Log
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.time.Duration

/**
 * A class that facilitates interacting with coroutines.
 * TODO(b/228823159) Consolidate with other Coroutines helper functions
 */
object Coroutines {

    /** Behaves in the same way as [runBlocking], but with a timeout. */
    fun <T> runBlockingWithTimeout(timeout: Duration = TIMEOUT_LONG, block: suspend () -> T) =
        runBlocking {
            withTimeout(timeout.toMillis()) { block() }
        }

    /** Check a condition using coroutines with a timeout. */
    fun waitForWithTimeout(
        timeout: Duration = TIMEOUT_LONG,
        checkPeriod: Duration = CHECK_PERIOD,
        condition: () -> Boolean
    ) {
        runBlockingWithTimeout(timeout) { waitFor(checkPeriod, condition) }
    }

    /** Check a condition using coroutines. */
    suspend fun waitFor(checkPeriod: Duration = CHECK_PERIOD, condition: () -> Boolean) {
        val conditionMet = condition()
        if (conditionMet) {
            return
        }
        delay(checkPeriod.toMillis())
        return waitFor(checkPeriod, condition)
    }

    /** Retries a test until no assertions or exceptions are thrown or a timeout occurs. */
    fun waitForTestToPass(test: () -> Unit) {
        waitForWithTimeout {
            try {
                test()
                true
            } catch (ex: Throwable) {
                Log.w(TAG, "Encountered test failure, retrying until timeout: $ex")
                false
            }
        }
    }

    /** A medium period, to be used for conditions that are expected to change. */
    private val TAG = "Coroutines"

    /** A medium period, to be used for conditions that are expected to change. */
    private val CHECK_PERIOD = Duration.ofMillis(250)

    /** A long timeout, to be used for actions that are expected to complete. */
    private val TIMEOUT_LONG: Duration = Duration.ofSeconds(5)
}