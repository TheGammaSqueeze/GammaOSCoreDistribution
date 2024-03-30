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

package com.android.net.module.util

import androidx.test.filters.SmallTest
import androidx.test.runner.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertFailsWith

@RunWith(AndroidJUnit4::class)
@SmallTest
class PerUidCounterTest {
    private val UID_A = 1000
    private val UID_B = 1001

    @Test
    fun testCounterMaximum() {
        assertFailsWith<IllegalArgumentException> {
            PerUidCounter(-1)
        }
        assertFailsWith<IllegalArgumentException> {
            PerUidCounter(0)
        }

        val largeMaxCounter = PerUidCounter(Integer.MAX_VALUE)
        largeMaxCounter.incrementCountOrThrow(UID_A, Integer.MAX_VALUE)
        assertFailsWith<IllegalStateException> {
            largeMaxCounter.incrementCountOrThrow(UID_A)
        }
    }

    @Test
    fun testIncrementCountOrThrow() {
        val counter = PerUidCounter(3)

        // Verify the increment count cannot be zero.
        assertFailsWith<IllegalArgumentException> {
            counter.incrementCountOrThrow(UID_A, 0)
        }

        // Verify the counters work independently.
        counter.incrementCountOrThrow(UID_A)
        counter.incrementCountOrThrow(UID_B, 2)
        counter.incrementCountOrThrow(UID_B)
        counter.incrementCountOrThrow(UID_A)
        counter.incrementCountOrThrow(UID_A)
        assertFailsWith<IllegalStateException> {
            counter.incrementCountOrThrow(UID_A)
        }
        assertFailsWith<IllegalStateException> {
            counter.incrementCountOrThrow(UID_B)
        }

        // Verify exception can be triggered again.
        assertFailsWith<IllegalStateException> {
            counter.incrementCountOrThrow(UID_A)
        }
        assertFailsWith<IllegalStateException> {
            counter.incrementCountOrThrow(UID_A, 3)
        }
    }

    @Test
    fun testDecrementCountOrThrow() {
        val counter = PerUidCounter(3)

        // Verify the decrement count cannot be zero.
        assertFailsWith<IllegalArgumentException> {
            counter.decrementCountOrThrow(UID_A, 0)
        }

        // Verify the count cannot go below zero.
        assertFailsWith<IllegalStateException> {
            counter.decrementCountOrThrow(UID_A)
        }
        assertFailsWith<IllegalStateException> {
            counter.decrementCountOrThrow(UID_A, 5)
        }
        assertFailsWith<IllegalStateException> {
            counter.decrementCountOrThrow(UID_A, Integer.MAX_VALUE)
        }

        // Verify the counters work independently.
        counter.incrementCountOrThrow(UID_A)
        counter.incrementCountOrThrow(UID_B)
        assertFailsWith<IllegalStateException> {
            counter.decrementCountOrThrow(UID_A, 3)
        }
        counter.decrementCountOrThrow(UID_A)
        assertFailsWith<IllegalStateException> {
            counter.decrementCountOrThrow(UID_A)
        }
    }
}