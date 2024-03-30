/*
 * Copyright (C) 2018 The Android Open Source Project
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

import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.FieldItem
import com.android.tools.metalava.model.PropertyItem
import com.android.tools.metalava.model.TypeItem
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import org.jetbrains.kotlin.psi.KtPropertyAccessor
import org.jetbrains.uast.UClass

class PsiPropertyItem private constructor(
    override val codebase: PsiBasedCodebase,
    private val psiMethod: PsiMethod,
    private val containingClass: PsiClassItem,
    private val name: String,
    modifiers: PsiModifierItem,
    documentation: String,
    private val fieldType: PsiTypeItem,
    override val getter: PsiMethodItem,
    override val setter: PsiMethodItem?,
    override val constructorParameter: PsiParameterItem?,
    override val backingField: PsiFieldItem?
) :
    PsiItem(
        codebase = codebase,
        modifiers = modifiers,
        documentation = documentation,
        element = psiMethod
    ),
    PropertyItem {

    override fun type(): TypeItem = fieldType
    override fun name(): String = name
    override fun containingClass(): ClassItem = containingClass

    override fun isCloned(): Boolean {
        val psiClass = run {
            val p = containingClass().psi() as? PsiClass ?: return false
            if (p is UClass) {
                p.sourcePsi as? PsiClass ?: return false
            } else {
                p
            }
        }
        return psiMethod.containingClass != psiClass
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        return other is FieldItem && name == other.name() && containingClass == other.containingClass()
    }

    override fun hashCode(): Int {
        return name.hashCode()
    }

    override fun toString(): String = "field ${containingClass.fullName()}.${name()}"

    companion object {
        /**
         * Creates a new property item, given a [name], [type] and relationships to other items.
         *
         * Kotlin properties consist of up to four other declarations: Their accessor functions,
         * primary constructor parameter, and a backing field. These relationships are useful for
         * resolving documentation and exposing the model correctly in Kotlin stubs.
         *
         * Metalava currently requires all properties to have a [getter]. It does not currently
         * support private, `const val`, or [JvmField] properties. Mutable `var` properties usually
         * have a [setter], but properties with a private default setter may use direct field
         * access instead.
         *
         * Properties declared in the primary constructor of a class have an associated
         * [constructorParameter]. This relationship is important for resolving docs which may
         * exist on the constructor parameter.
         *
         * Most properties on classes without a custom getter have a [backingField] to hold their
         * value. This is private except for [JvmField] properties.
         */
        fun create(
            codebase: PsiBasedCodebase,
            containingClass: PsiClassItem,
            name: String,
            type: PsiTypeItem,
            getter: PsiMethodItem,
            setter: PsiMethodItem? = null,
            constructorParameter: PsiParameterItem? = null,
            backingField: PsiFieldItem? = null
        ): PsiPropertyItem {
            val psiMethod = getter.psiMethod
            val documentation = when (val sourcePsi = getter.sourcePsi) {
                is KtPropertyAccessor -> javadoc(sourcePsi.property)
                else -> javadoc(sourcePsi ?: psiMethod)
            }
            val modifiers = modifiers(codebase, psiMethod, documentation)
            val property = PsiPropertyItem(
                codebase = codebase,
                psiMethod = psiMethod,
                containingClass = containingClass,
                name = name,
                documentation = documentation,
                modifiers = modifiers,
                fieldType = type,
                getter = getter,
                setter = setter,
                constructorParameter = constructorParameter,
                backingField = backingField
            )
            getter.property = property
            setter?.property = property
            constructorParameter?.property = property
            backingField?.property = property
            property.modifiers.setOwner(property)
            return property
        }
    }
}
