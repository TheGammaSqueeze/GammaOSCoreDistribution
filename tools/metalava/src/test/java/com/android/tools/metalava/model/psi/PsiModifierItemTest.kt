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
import com.android.tools.metalava.model.VisibilityLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PsiModifierItemTest {
    @Test
    fun `Kotlin implicit internal visibility inheritance`() {
        testCodebase(
            kotlin(
                """
                    open class Base {
                        internal open fun method(): Int = 1
                        internal open val property: Int = 2
                    }

                    class Inherited : Base() {
                        override fun method(): Int = 3
                        override val property = 4
                    }
                """
            )
        ) { codebase ->
            val inherited = codebase.assertClass("Inherited")
            val method = inherited.methods().first { it.name().startsWith("method") }
            val property = inherited.properties().single()

            assertEquals(VisibilityLevel.INTERNAL, method.modifiers.getVisibilityLevel())
            assertEquals(VisibilityLevel.INTERNAL, property.modifiers.getVisibilityLevel())
        }
    }

    @Test
    fun `Kotlin class visibility modifiers`() {
        testCodebase(
            kotlin(
                """
                    internal class Internal
                    public class Public
                    class DefaultPublic
                    abstract class Outer {
                        private class Private
                        protected class Protected
                    }
                """
            )
        ) { codebase ->
            assertTrue(codebase.assertClass("Internal").isInternal)
            assertTrue(codebase.assertClass("Public").isPublic)
            assertTrue(codebase.assertClass("DefaultPublic").isPublic)
            assertTrue(codebase.assertClass("Outer.Private").isPrivate)
            assertTrue(codebase.assertClass("Outer.Protected").isProtected)
        }
    }

    @Test
    fun `Kotlin class abstract and final modifiers`() {
        testCodebase(
            kotlin(
                """
                    abstract class Abstract
                    sealed class Sealed
                    open class Open
                    final class Final
                    class FinalDefault
                    interface Interface
                    annotation class Annotation
                """
            )
        ) { codebase ->
            codebase.assertClass("Abstract").modifiers.let {
                assertTrue(it.isAbstract())
                assertFalse(it.isSealed())
                assertFalse(it.isFinal())
            }

            codebase.assertClass("Sealed").modifiers.let {
                assertTrue(it.isAbstract())
                assertTrue(it.isSealed())
                assertFalse(it.isFinal())
            }

            codebase.assertClass("Open").modifiers.let {
                assertFalse(it.isAbstract())
                assertFalse(it.isFinal())
            }

            codebase.assertClass("Final").modifiers.let {
                assertFalse(it.isAbstract())
                assertTrue(it.isFinal())
            }

            codebase.assertClass("FinalDefault").modifiers.let {
                assertFalse(it.isAbstract())
                assertTrue(it.isFinal())
            }

            codebase.assertClass("Interface").modifiers.let {
                assertTrue(it.isAbstract())
                assertFalse(it.isFinal())
            }

            codebase.assertClass("Annotation").modifiers.let {
                assertTrue(it.isAbstract())
                assertFalse(it.isFinal())
            }
        }
    }

    @Test
    fun `Kotlin class type modifiers`() {
        testCodebase(
            kotlin(
                """
                    inline class Inline(val value: Int)
                    value class Value(val value: Int)
                    data class Data(val data: Int) {
                        companion object {
                            const val DATA = 0
                        }
                    }
                    fun interface FunInterface {
                        fun foo()
                    }
                """
            )
        ) { codebase ->
            assertTrue(codebase.assertClass("Inline").modifiers.isInline())
            assertTrue(codebase.assertClass("Value").modifiers.isValue())
            assertTrue(codebase.assertClass("Data").modifiers.isData())
            assertTrue(codebase.assertClass("Data.Companion").modifiers.isCompanion())
            assertTrue(codebase.assertClass("FunInterface").modifiers.isFunctional())
        }
    }

    @Test
    fun `Kotlin class static modifiers`() {
        testCodebase(
            kotlin(
                """
                    class TopLevel {
                        inner class Inner
                        class Nested
                        interface Interface
                        annotation class Annotation
                        object Object
                    }
                    object Object
                """
            )
        ) { codebase ->

            assertFalse(codebase.assertClass("TopLevel").modifiers.isStatic())
            assertFalse(codebase.assertClass("TopLevel.Inner").modifiers.isStatic())
            assertFalse(codebase.assertClass("Object").modifiers.isStatic())

            assertTrue(codebase.assertClass("TopLevel.Nested").modifiers.isStatic())
            assertTrue(codebase.assertClass("TopLevel.Interface").modifiers.isStatic())
            assertTrue(codebase.assertClass("TopLevel.Annotation").modifiers.isStatic())
            assertTrue(codebase.assertClass("TopLevel.Object").modifiers.isStatic())
        }
    }

    fun `Kotlin vararg parameters`() {
        testCodebase(
            kotlin(
                "Foo.kt",
                """
                    fun varArg(vararg parameter: Int) { TODO() }
                    fun nonVarArg(parameter: Int) { TODO() }
                """
            )
        ) { codebase ->
            val facade = codebase.assertClass("FooKt")
            val varArg = facade.methods().single { it.name() == "varArg" }.parameters().single()
            val nonVarArg =
                facade.methods().single { it.name() == "nonVarArg" }.parameters().single()

            assertTrue(varArg.modifiers.isVarArg())
            assertFalse(nonVarArg.modifiers.isVarArg())
        }
    }
}
