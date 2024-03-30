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

package android.safetycenter.cts.testing

import java.time.Duration
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

/** A class that facilitates interacting with coroutines. */
object Coroutines {

    /** Behaves in the same way as [runBlocking], but with a timeout. */
    fun <T> runBlockingWithTimeout(timeout: Duration = TIMEOUT_LONG, block: suspend () -> T) =
        runBlocking {
            withTimeout(timeout.toMillis()) { block() }
        }

    /** A long timeout, to be used for actions that are expected to complete. */
    val TIMEOUT_LONG: Duration = Duration.ofSeconds(5)

    /** A short timeout, to be used for actions that are expected not to complete. */
    val TIMEOUT_SHORT: Duration = Duration.ofSeconds(1)
}
