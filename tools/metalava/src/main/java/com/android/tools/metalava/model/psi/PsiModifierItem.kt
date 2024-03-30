/*
 * Copyright (C) 2017 The Android Open Source Project
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

import com.android.tools.metalava.ANDROIDX_VISIBLE_FOR_TESTING
import com.android.tools.metalava.ANDROID_DEPRECATED_FOR_SDK
import com.android.tools.metalava.ATTR_ALLOW_IN
import com.android.tools.metalava.ATTR_OTHERWISE
import com.android.tools.metalava.model.AnnotationItem
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.DefaultModifierList
import com.android.tools.metalava.model.ModifierList
import com.android.tools.metalava.model.MutableModifierList
import com.android.tools.metalava.options
import com.intellij.psi.PsiDocCommentOwner
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiModifierList
import com.intellij.psi.PsiModifierListOwner
import com.intellij.psi.PsiPrimitiveType
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.impl.light.LightModifierList
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable
import org.jetbrains.kotlin.asJava.elements.KtLightModifierList
import org.jetbrains.kotlin.asJava.elements.KtLightNullabilityAnnotation
import org.jetbrains.kotlin.descriptors.DeclarationDescriptorWithVisibility
import org.jetbrains.kotlin.descriptors.EffectiveVisibility
import org.jetbrains.kotlin.descriptors.effectiveVisibility
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtAnnotated
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtModifierList
import org.jetbrains.kotlin.psi.KtModifierListOwner
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtPropertyAccessor
import org.jetbrains.kotlin.psi.psiUtil.hasFunModifier
import org.jetbrains.kotlin.psi.psiUtil.visibilityModifier
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.uast.UAnnotated
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UVariable

class PsiModifierItem(
    codebase: Codebase,
    flags: Int = PACKAGE_PRIVATE,
    annotations: MutableList<AnnotationItem>? = null
) : DefaultModifierList(codebase, flags, annotations), ModifierList, MutableModifierList {
    companion object {
        fun create(
            codebase: PsiBasedCodebase,
            element: PsiModifierListOwner,
            documentation: String?
        ): PsiModifierItem {
            val modifiers =
                if (element is UAnnotated) {
                    create(codebase, element, element)
                } else {
                    create(codebase, element)
                }
            if (documentation?.contains("@deprecated") == true ||
                // Check for @Deprecated annotation
                ((element as? PsiDocCommentOwner)?.isDeprecated == true) ||
                // Check for @Deprecated on sourcePsi
                isDeprecatedFromSourcePsi(element)
            ) {
                modifiers.setDeprecated(true)
            }

            return modifiers
        }

        private fun isDeprecatedFromSourcePsi(element: PsiModifierListOwner): Boolean {
            return ((element as? UElement)?.sourcePsi as? KtAnnotated)?.annotationEntries?.any {
                it.shortName?.toString() == "Deprecated"
            } ?: false
        }

        private fun computeFlag(
            codebase: PsiBasedCodebase,
            element: PsiModifierListOwner,
            modifierList: PsiModifierList
        ): Int {
            var flags = 0
            if (modifierList.hasModifierProperty(PsiModifier.STATIC)) {
                flags = flags or STATIC
            }
            if (modifierList.hasModifierProperty(PsiModifier.ABSTRACT)) {
                flags = flags or ABSTRACT
            }
            if (modifierList.hasModifierProperty(PsiModifier.FINAL)) {
                flags = flags or FINAL
            }
            if (modifierList.hasModifierProperty(PsiModifier.NATIVE)) {
                flags = flags or NATIVE
            }
            if (modifierList.hasModifierProperty(PsiModifier.SYNCHRONIZED)) {
                flags = flags or SYNCHRONIZED
            }
            if (modifierList.hasModifierProperty(PsiModifier.STRICTFP)) {
                flags = flags or STRICT_FP
            }
            if (modifierList.hasModifierProperty(PsiModifier.TRANSIENT)) {
                flags = flags or TRANSIENT
            }
            if (modifierList.hasModifierProperty(PsiModifier.VOLATILE)) {
                flags = flags or VOLATILE
            }
            if (modifierList.hasModifierProperty(PsiModifier.DEFAULT)) {
                flags = flags or DEFAULT
            }

            // Look for special Kotlin keywords
            var ktModifierList: KtModifierList? = null
            val sourcePsi = (element as? UElement)?.sourcePsi
            if (modifierList is KtLightModifierList<*>) {
                ktModifierList = modifierList.kotlinOrigin
            } else if (modifierList is LightModifierList && element is UMethod) {
                if (sourcePsi is KtModifierListOwner) {
                    ktModifierList = sourcePsi.modifierList
                }
            }
            var visibilityFlags = when {
                modifierList.hasModifierProperty(PsiModifier.PUBLIC) -> PUBLIC
                modifierList.hasModifierProperty(PsiModifier.PROTECTED) -> PROTECTED
                modifierList.hasModifierProperty(PsiModifier.PRIVATE) -> PRIVATE
                ktModifierList != null -> when {
                    ktModifierList.hasModifier(KtTokens.PRIVATE_KEYWORD) -> PRIVATE
                    ktModifierList.hasModifier(KtTokens.PROTECTED_KEYWORD) -> PROTECTED
                    ktModifierList.hasModifier(KtTokens.INTERNAL_KEYWORD) -> INTERNAL
                    else -> PUBLIC
                }
                else -> PACKAGE_PRIVATE
            }
            if (ktModifierList != null) {
                if (ktModifierList.hasModifier(KtTokens.INTERNAL_KEYWORD)) {
                    // Reset visibilityFlags to INTERNAL if the internal modifier is explicitly
                    // present on the element
                    visibilityFlags = INTERNAL
                } else if (
                    ktModifierList.hasModifier(KtTokens.OVERRIDE_KEYWORD) &&
                    ktModifierList.visibilityModifier() == null &&
                    sourcePsi is KtElement
                ) {
                    // Reset visibilityFlags to INTERNAL if the element has no explicit visibility
                    // modifier, but overrides an internal declaration. Adapted from
                    // org.jetbrains.kotlin.asJava.classes.UltraLightMembersCreator.isInternal
                    val descriptor = codebase.bindingContext(sourcePsi)
                        .get(BindingContext.DECLARATION_TO_DESCRIPTOR, sourcePsi)

                    if (descriptor is DeclarationDescriptorWithVisibility) {
                        val effectiveVisibility =
                            descriptor.visibility.effectiveVisibility(descriptor, false)

                        if (effectiveVisibility == EffectiveVisibility.Internal) {
                            visibilityFlags = INTERNAL
                        }
                    }
                }
                if (ktModifierList.hasModifier(KtTokens.VARARG_KEYWORD)) {
                    flags = flags or VARARG
                }
                if (ktModifierList.hasModifier(KtTokens.SEALED_KEYWORD)) {
                    flags = flags or SEALED
                }
                if (ktModifierList.hasModifier(KtTokens.INFIX_KEYWORD)) {
                    flags = flags or INFIX
                }
                if (ktModifierList.hasModifier(KtTokens.CONST_KEYWORD)) {
                    flags = flags or CONST
                }
                if (ktModifierList.hasModifier(KtTokens.OPERATOR_KEYWORD)) {
                    flags = flags or OPERATOR
                }
                if (ktModifierList.hasModifier(KtTokens.INLINE_KEYWORD)) {
                    flags = flags or INLINE

                    // Workaround for b/117565118:
                    val func = sourcePsi as? KtNamedFunction
                    if (func != null &&
                        (func.typeParameterList?.text ?: "").contains("reified") &&
                        !ktModifierList.hasModifier(KtTokens.PRIVATE_KEYWORD) &&
                        !ktModifierList.hasModifier(KtTokens.INTERNAL_KEYWORD)
                    ) {
                        // Switch back from private to public
                        visibilityFlags = PUBLIC
                    }
                }
                if (ktModifierList.hasModifier(KtTokens.VALUE_KEYWORD)) {
                    flags = flags or VALUE
                }
                if (ktModifierList.hasModifier(KtTokens.SUSPEND_KEYWORD)) {
                    flags = flags or SUSPEND
                }
                if (ktModifierList.hasModifier(KtTokens.COMPANION_KEYWORD)) {
                    flags = flags or COMPANION
                }
                if (ktModifierList.hasFunModifier()) {
                    flags = flags or FUN
                }
                if (ktModifierList.hasModifier(KtTokens.DATA_KEYWORD)) {
                    flags = flags or DATA
                }
            }
            // Methods that are property accessors inherit visibility from the source element
            if (element is UMethod && (element.sourceElement is KtPropertyAccessor)) {
                val sourceElement = element.sourceElement
                if (sourceElement is KtModifierListOwner) {
                    val sourceModifierList = sourceElement.modifierList
                    if (sourceModifierList != null) {
                        if (sourceModifierList.hasModifier(KtTokens.INTERNAL_KEYWORD)) {
                            visibilityFlags = INTERNAL
                        }
                    }
                }
            }

            // Merge in the visibility flags.
            flags = flags or visibilityFlags

            return flags
        }

        private fun create(codebase: PsiBasedCodebase, element: PsiModifierListOwner): PsiModifierItem {
            val modifierList = element.modifierList ?: return PsiModifierItem(codebase)
            var flags = computeFlag(codebase, element, modifierList)

            val psiAnnotations = modifierList.annotations
            return if (psiAnnotations.isEmpty()) {
                PsiModifierItem(codebase, flags)
            } else {
                val annotations: MutableList<AnnotationItem> =
                    // psi sometimes returns duplicate annotations, using distinct() to counter that.
                    psiAnnotations.distinct().map {
                        val qualifiedName = it.qualifiedName
                        // Consider also supporting com.android.internal.annotations.VisibleForTesting?
                        if (qualifiedName == ANDROIDX_VISIBLE_FOR_TESTING) {
                            val otherwise = it.findAttributeValue(ATTR_OTHERWISE)
                            val ref = when {
                                otherwise is PsiReferenceExpression -> otherwise.referenceName ?: ""
                                otherwise != null -> otherwise.text
                                else -> ""
                            }
                            flags = getVisibilityFlag(ref, flags)
                        }

                        PsiAnnotationItem.create(codebase, it, qualifiedName)
                    }.filter { !it.isDeprecatedForSdk() }.toMutableList()
                PsiModifierItem(codebase, flags, annotations)
            }
        }

        private fun create(
            codebase: PsiBasedCodebase,
            element: PsiModifierListOwner,
            annotated: UAnnotated
        ): PsiModifierItem {
            val modifierList = element.modifierList ?: return PsiModifierItem(codebase)
            val uAnnotations = annotated.uAnnotations

            var flags = computeFlag(codebase, element, modifierList)

            return if (uAnnotations.isEmpty()) {
                val psiAnnotations = modifierList.annotations
                if (psiAnnotations.isNotEmpty()) {
                    val annotations: MutableList<AnnotationItem> =
                        psiAnnotations.map { PsiAnnotationItem.create(codebase, it) }.toMutableList()
                    PsiModifierItem(codebase, flags, annotations)
                } else {
                    PsiModifierItem(codebase, flags)
                }
            } else {
                val isPrimitiveVariable = element is UVariable && element.type is PsiPrimitiveType

                val annotations: MutableList<AnnotationItem> = uAnnotations
                    // Uast sometimes puts nullability annotations on primitives!?
                    .filter {
                        !isPrimitiveVariable ||
                            it.qualifiedName == null ||
                            !it.isKotlinNullabilityAnnotation
                    }
                    .map {

                        val qualifiedName = it.qualifiedName
                        if (qualifiedName == ANDROIDX_VISIBLE_FOR_TESTING) {
                            val otherwise = it.findAttributeValue(ATTR_OTHERWISE)
                            val ref = when {
                                otherwise is PsiReferenceExpression -> otherwise.referenceName ?: ""
                                otherwise != null -> otherwise.asSourceString()
                                else -> ""
                            }
                            flags = getVisibilityFlag(ref, flags)
                        }

                        UAnnotationItem.create(codebase, it, qualifiedName)
                    }.filter { !it.isDeprecatedForSdk() }.toMutableList()

                if (!isPrimitiveVariable) {
                    val psiAnnotations = modifierList.annotations
                    if (psiAnnotations.isNotEmpty() && annotations.none { it.isNullnessAnnotation() }) {
                        val ktNullAnnotation = psiAnnotations.firstOrNull { it is KtLightNullabilityAnnotation<*> }
                        ktNullAnnotation?.let {
                            annotations.add(PsiAnnotationItem.create(codebase, it))
                        }
                    }
                }

                PsiModifierItem(codebase, flags, annotations)
            }
        }

        /** Returns whether this is a `@DeprecatedForSdk` annotation **that should be skipped**. */
        private fun AnnotationItem.isDeprecatedForSdk(): Boolean {
            if (originalName != ANDROID_DEPRECATED_FOR_SDK) {
                return false
            }

            val allowIn = findAttribute(ATTR_ALLOW_IN) ?: return false

            for (api in allowIn.leafValues()) {
                val annotationName = api.value() as? String ?: continue
                if (options.showAnnotations.matchesAnnotationName(annotationName)) {
                    return true
                }
            }

            return false
        }

        private val NOT_NULL = NotNull::class.qualifiedName
        private val NULLABLE = Nullable::class.qualifiedName

        private val UAnnotation.isKotlinNullabilityAnnotation: Boolean
            get() = qualifiedName == NOT_NULL || qualifiedName == NULLABLE

        /** Modifies the modifier flags based on the VisibleForTesting otherwise constants */
        private fun getVisibilityFlag(ref: String, flags: Int): Int {
            val visibilityFlags = if (ref.endsWith("PROTECTED")) {
                PROTECTED
            } else if (ref.endsWith("PACKAGE_PRIVATE")) {
                PACKAGE_PRIVATE
            } else if (ref.endsWith("PRIVATE") || ref.endsWith("NONE")) {
                PRIVATE
            } else {
                flags and VISIBILITY_MASK
            }

            return (flags and VISIBILITY_MASK.inv()) or visibilityFlags
        }

        fun create(codebase: PsiBasedCodebase, original: PsiModifierItem): PsiModifierItem {
            val originalAnnotations = original.annotations ?: return PsiModifierItem(codebase, original.flags)
            val copy: MutableList<AnnotationItem> = ArrayList(originalAnnotations.size)
            originalAnnotations.mapTo(copy) { item ->
                when (item) {
                    is PsiAnnotationItem -> PsiAnnotationItem.create(codebase, item)
                    is UAnnotationItem -> UAnnotationItem.create(codebase, item)
                    else -> {
                        throw Exception("Unexpected annotation type ${item::class.qualifiedName}")
                    }
                }
            }
            return PsiModifierItem(codebase, original.flags, copy)
        }
    }
}
