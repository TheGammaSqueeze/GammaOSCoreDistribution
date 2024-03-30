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

package com.android.tools.metalava.model.psi

import com.android.tools.metalava.kotlin
import org.junit.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame

class PsiMethodItemTest {
    @Test
    fun `property accessors have properties`() {
        testCodebase(kotlin("class Foo { var bar: Int = 0 }")) { codebase ->
            val classItem = codebase.assertClass("Foo")
            val getter = classItem.methods().single { it.name() == "getBar" }
            val setter = classItem.methods().single { it.name() == "setBar" }

            assertNotNull(getter.property)
            assertNotNull(setter.property)

            assertSame(getter.property, setter.property)
            assertSame(getter, getter.property?.getter)
            assertSame(setter, setter.property?.setter)
        }
    }

    @Test
    fun `destructuring functions do not have a property relationship`() {
        testCodebase(kotlin("data class Foo(val bar: Int)")) { codebase ->
            val classItem = codebase.assertClass("Foo")
            val component1 = classItem.methods().single { it.name() == "component1" }

            assertNull(component1.property)
        }
    }
}
