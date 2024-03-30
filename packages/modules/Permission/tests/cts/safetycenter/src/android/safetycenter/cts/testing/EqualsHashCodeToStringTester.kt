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

import com.google.common.base.Equivalence
import com.google.common.testing.EqualsTester
import com.google.common.testing.EquivalenceTester

/**
 * A class similar to [EqualsTester] that also checks that the [Object.hashCode] and
 * [Object.toString] implementations are consistent with equality groups.
 *
 * Note: this class assumes that [Object.hashCode] does not create a collision for equality groups,
 * however this can be disabled by setting [hashCodeCanCollide] to `true`.
 */
class EqualsHashCodeToStringTester(private val hashCodeCanCollide: Boolean = false) {
    private val equalsTester = EqualsTester()
    private val toStringTester = EquivalenceTester.of(TO_STRING_EQUIVALENCE)
    private val hashCodeTester = EquivalenceTester.of(HASH_CODE_EQUIVALENCE)

    fun addEqualityGroup(vararg groups: Any): EqualsHashCodeToStringTester {
        equalsTester.addEqualityGroup(*groups)
        toStringTester.addEquivalenceGroupAsArray(groups)
        if (!hashCodeCanCollide) {
            hashCodeTester.addEquivalenceGroupAsArray(groups)
        }
        return this
    }

    private fun EquivalenceTester<Any>.addEquivalenceGroupAsArray(input: Array<out Any>) {
        when (val size = input.size) {
            0 -> return
            1 -> addEquivalenceGroup(input[0])
            else -> addEquivalenceGroup(input[0], *input.copyOfRange(1, size))
        }
    }

    fun test() {
        equalsTester.testEquals()
        toStringTester.test()
        if (!hashCodeCanCollide) {
            hashCodeTester.test()
        }
    }

    companion object {

        /**
         * An [Equivalence] that considers two instances of a class equivalent iff [Object.toString]
         * return the same value.
         */
        private val TO_STRING_EQUIVALENCE =
            object : Equivalence<Any>() {

                override fun doEquivalent(a: Any, b: Any): Boolean {
                    return a.toString() == b.toString()
                }

                override fun doHash(o: Any): Int {
                    return o.toString().hashCode()
                }
            }

        /**
         * An [Equivalence] that considers two instances of a class equivalent iff [Object.hashCode]
         * return the same value.
         */
        private val HASH_CODE_EQUIVALENCE =
            object : Equivalence<Any>() {

                override fun doEquivalent(a: Any, b: Any): Boolean {
                    return a.hashCode() == b.hashCode()
                }

                override fun doHash(o: Any): Int {
                    return o.hashCode()
                }
            }
    }
}
