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
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class PsiPropertyItemTest {
    @Test
    fun `primary constructor properties have constructor parameters`() {
        testCodebase(kotlin("class Foo(val myVal: Int)")) { codebase ->
            val myVal = codebase.assertClass("Foo").properties().single()

            assertNotNull(myVal.constructorParameter)
            assertSame(myVal, myVal.constructorParameter?.property)
        }
    }

    @Test
    fun `properties have getters`() {
        testCodebase(
            kotlin(
                """
                    class Foo {
                        val myVal: Int = 0
                        var myVar: Int = 0
                    }
                """
            )
        ) { codebase ->
            val properties = codebase.assertClass("Foo").properties()
            val myVal = properties.single { it.name() == "myVal" }
            val myVar = properties.single { it.name() == "myVar" }

            assertNotNull(myVal.getter)
            assertNotNull(myVar.getter)

            assertEquals("getMyVal", myVal.getter?.name())
            assertEquals("getMyVar", myVar.getter?.name())

            assertSame(myVal, myVal.getter?.property)
            assertSame(myVar, myVar.getter?.property)
        }
    }

    @Test
    fun `var properties have setters`() {
        testCodebase(kotlin("class Foo { var myVar: Int = 0 }")) { codebase ->
            val myVar = codebase.assertClass("Foo").properties().single()

            assertNotNull(myVar.setter)
            assertEquals("setMyVar", myVar.setter?.name())
            assertSame(myVar, myVar.setter?.property)
        }
    }

    @Test
    fun `setter visibility`() {
        testCodebase(
            kotlin(
                """
                    class Foo {
                        var internalSet: Int = 0
                            internal set

                        var privateSet: Int = 0
                            private set

                        var privateCustomSet: Int = 0
                            private set(value) { field = value + 1 }
                """
            )
        ) { codebase ->
            val properties = codebase.assertClass("Foo").properties()
            val internalSet = properties.single { it.name() == "internalSet" }
            val privateSet = properties.single { it.name() == "privateSet" }
            val privateCustomSet = properties.single { it.name() == "privateCustomSet" }

            assertTrue(internalSet.isPublic)
            assertTrue(internalSet.getter!!.isPublic)
            assertTrue(internalSet.setter!!.isInternal)

            assertTrue(privateSet.isPublic)
            assertTrue(privateSet.getter!!.isPublic)
            assertNull(privateSet.setter) // Private setter is replaced with direct field access

            assertTrue(privateCustomSet.isPublic)
            assertTrue(privateCustomSet.getter!!.isPublic)
            assertTrue(privateCustomSet.setter!!.isPrivate)
        }
    }

    @Test
    fun `properties have backing fields`() {
        testCodebase(
            kotlin(
                """
                    class Foo(val withField: Int) {
                        val withoutField: Int
                            get() = 0
                    }
                """
            )
        ) { codebase ->
            val properties = codebase.assertClass("Foo").properties()
            val withField = properties.single { it.name() == "withField" }
            val withoutField = properties.single { it.name() == "withoutField" }

            assertNull(withoutField.backingField)

            assertNotNull(withField.backingField)
            assertEquals("withField", withField.backingField?.name())
            assertSame(withField, withField.backingField?.property)
        }
    }

    @Test
    fun `properties have documentation`() {
        testCodebase(
            kotlin(
                """
                    class Foo(/** parameter doc */ val parameter: Int) {
                        /** body doc */
                        var body: Int = 0

                        /** accessors property doc */
                        var accessors: Int
                            /** getter doc */
                            get() = field + 1
                            /** setter doc */
                            set(value) = { field = value - 1 }
                    }
                """
            )
        ) { codebase ->
            val properties = codebase.assertClass("Foo").properties()
            val parameter = properties.single { it.name() == "parameter" }
            val body = properties.single { it.name() == "body" }
            val accessors = properties.single { it.name() == "accessors" }

            assertContains(parameter.documentation, "parameter doc")
            assertContains(body.documentation, "body doc")
            assertContains(accessors.documentation, "accessors property doc")
            assertContains(accessors.getter?.documentation.orEmpty(), "getter doc")
            assertContains(accessors.setter?.documentation.orEmpty(), "setter doc")
        }
    }
}
