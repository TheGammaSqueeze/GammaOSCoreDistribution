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

package com.android.tools.metalava.model

import com.android.tools.metalava.java
import com.android.tools.metalava.kotlin
import com.android.tools.metalava.model.psi.PsiTypeItem
import com.android.tools.metalava.model.psi.testCodebase
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PsiTypeItemAssignabilityTest {
    @get:Rule
    val tmpFolder = TemporaryFolder()

    @Test
    fun `Assignability in PSI`() {
        val sourceFiles = arrayOf(
            // pass in the same class structure in kotlin and java.
            java(
                """
                package test.foo;
                import java.util.*;
                public class JavaSubject {
                    public Object obj;
                    public String string;
                    public int primitiveInt;
                    public Number number;
                    public Integer boxedInt;
                    public List<Integer> listOfInt;
                    public List<Number> listOfNumber;
                    public Map<Integer, String> mapOfIntToString;
                    public Map<Number, String> mapOfNumberToString;
                }
                """
            ),
            kotlin(
                """
                package test.foo;
                class KotlinSubject {
                    @JvmField
                    var obj: Any? = null
                    @JvmField
                    var string: String? = null
                    @JvmField
                    var primitiveInt = 0
                    @JvmField
                    var number: Number? = null
                    @JvmField
                    var boxedInt: Int? = null
                    @JvmField
                    var listOfInt: MutableList<Int>? = null
                    @JvmField
                    var listOfNumber: MutableList<Number>? = null
                    @JvmField
                    var mapOfIntToString: MutableMap<Int, String>? = null
                    @JvmField
                    var mapOfNumberToString: MutableMap<Number, String>? = null
                }
                """
            )
        )

        testCodebase(
            sources = sourceFiles
        ) { codebase ->
            val javaSubject = codebase.findClass("test.foo.JavaSubject")
                ?: error("Cannot find java subject")
            val kotlinSubject = codebase.findClass("test.foo.KotlinSubject")
                ?: error("Cannot find subject")
            val testSubjects = listOf(javaSubject, kotlinSubject)
            // helper method to check assignability between fields
            fun String.isAssignableFromWithoutUnboxing(
                otherField: String
            ): Boolean {
                val results = testSubjects.map { subject ->
                    val field1Type = checkNotNull(
                        subject.findField(this)?.type() as? PsiTypeItem
                    ) {
                        "cannot find $this in $subject"
                    }
                    val field2Type = checkNotNull(
                        subject.findField(otherField)?.type() as? PsiTypeItem
                    ) {
                        "cannot find $otherField in $subject"
                    }
                    field1Type.isAssignableFromWithoutUnboxing(field2Type)
                }
                check(results.toSet().size == 1) {
                    "isAssignable check for $this to $otherField returned inconsistent results " +
                        "between kotlin and java: $results"
                }
                return results.first()
            }

            assertThat(
                "string".isAssignableFromWithoutUnboxing("string")
            ).isTrue()
            assertThat(
                "obj".isAssignableFromWithoutUnboxing("string")
            ).isTrue()
            assertThat(
                "string".isAssignableFromWithoutUnboxing("obj")
            ).isFalse()
            assertThat(
                "primitiveInt".isAssignableFromWithoutUnboxing("number")
            ).isFalse()
            assertThat(
                "number".isAssignableFromWithoutUnboxing("primitiveInt")
            ).isTrue()
            assertThat(
                "boxedInt".isAssignableFromWithoutUnboxing("primitiveInt")
            ).isTrue()
            assertThat(
                "primitiveInt".isAssignableFromWithoutUnboxing("boxedInt")
            ).isFalse()
            assertThat(
                "number".isAssignableFromWithoutUnboxing("boxedInt")
            ).isTrue()
            assertThat(
                "boxedInt".isAssignableFromWithoutUnboxing("number")
            ).isFalse()
            assertThat(
                "listOfInt".isAssignableFromWithoutUnboxing("listOfInt")
            ).isTrue()
            assertThat(
                "listOfInt".isAssignableFromWithoutUnboxing("listOfNumber")
            ).isFalse()
            assertThat(
                "listOfNumber".isAssignableFromWithoutUnboxing("listOfInt")
            ).isFalse()
            assertThat(
                "mapOfNumberToString".isAssignableFromWithoutUnboxing("mapOfNumberToString")
            ).isTrue()
            assertThat(
                "mapOfNumberToString".isAssignableFromWithoutUnboxing("mapOfIntToString")
            ).isFalse()
            assertThat(
                "mapOfIntToString".isAssignableFromWithoutUnboxing("mapOfNumberToString")
            ).isFalse()
        }
    }
}
