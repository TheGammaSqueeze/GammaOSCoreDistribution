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

package com.android.tools.metalava

import com.android.tools.metalava.model.AnnotationTarget
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.ConstructorItem
import com.android.tools.metalava.model.FieldItem
import com.android.tools.metalava.model.Item
import com.android.tools.metalava.model.MethodItem
import com.android.tools.metalava.model.ModifierList
import com.android.tools.metalava.model.PackageItem
import com.android.tools.metalava.model.PropertyItem
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.TypeParameterList
import com.android.tools.metalava.model.visitors.ApiVisitor
import java.io.PrintWriter
import java.util.function.Predicate

class SignatureWriter(
    private val writer: PrintWriter,
    filterEmit: Predicate<Item>,
    filterReference: Predicate<Item>,
    private val preFiltered: Boolean,
    var emitHeader: EmitFileHeader = options.includeSignatureFormatVersionNonRemoved
) : ApiVisitor(
    visitConstructorsAsMethods = false,
    nestInnerClasses = false,
    inlineInheritedFields = true,
    methodComparator = MethodItem.comparator,
    fieldComparator = FieldItem.comparator,
    filterEmit = filterEmit,
    filterReference = filterReference,
    showUnannotated = options.showUnannotated
) {
    init {
        if (emitHeader == EmitFileHeader.ALWAYS) {
            writer.print(options.outputFormat.header())
            emitHeader = EmitFileHeader.NEVER
        }
    }

    fun write(text: String) {
        if (emitHeader == EmitFileHeader.IF_NONEMPTY_FILE) {
            if (options.includeSignatureFormatVersion) {
                writer.print(options.outputFormat.header())
            }
            emitHeader = EmitFileHeader.NEVER
        }
        writer.print(text)
    }

    override fun visitPackage(pkg: PackageItem) {
        write("package ")
        writeModifiers(pkg)
        write("${pkg.qualifiedName()} {\n\n")
    }

    override fun afterVisitPackage(pkg: PackageItem) {
        write("}\n\n")
    }

    override fun visitConstructor(constructor: ConstructorItem) {
        write("    ctor ")
        writeModifiers(constructor)
        // Note - we don't write out the type parameter list (constructor.typeParameterList()) in signature files!
        // writeTypeParameterList(constructor.typeParameterList(), addSpace = true)
        write(constructor.containingClass().fullName())
        writeParameterList(constructor)
        writeThrowsList(constructor)
        write(";\n")
    }

    override fun visitField(field: FieldItem) {
        val name = if (field.isEnumConstant()) "enum_constant" else "field"
        write("    ")
        write(name)
        write(" ")
        writeModifiers(field)
        writeType(field, field.type())
        write(" ")
        write(field.name())
        field.writeValueWithSemicolon(writer, allowDefaultValue = false, requireInitialValue = false)
        write("\n")
    }

    override fun visitProperty(property: PropertyItem) {
        write("    property ")
        writeModifiers(property)
        writeType(property, property.type())
        write(" ")
        write(property.name())
        write(";\n")
    }

    override fun visitMethod(method: MethodItem) {
        write("    method ")
        writeModifiers(method)
        writeTypeParameterList(method.typeParameterList(), addSpace = true)

        writeType(method, method.returnType())
        write(" ")
        write(method.name())
        writeParameterList(method)
        writeThrowsList(method)

        if (method.containingClass().isAnnotationType()) {
            val default = method.defaultValue()
            if (default.isNotEmpty()) {
                write(" default ")
                write(default)
            }
        }

        write(";\n")
    }

    override fun visitClass(cls: ClassItem) {
        write("  ")

        writeModifiers(cls)

        if (cls.isAnnotationType()) {
            write("@interface")
        } else if (cls.isInterface()) {
            write("interface")
        } else if (cls.isEnum()) {
            write("enum")
        } else {
            write("class")
        }
        write(" ")
        write(cls.fullName())
        writeTypeParameterList(cls.typeParameterList(), addSpace = false)
        writeSuperClassStatement(cls)
        writeInterfaceList(cls)

        write(" {\n")
    }

    override fun afterVisitClass(cls: ClassItem) {
        write("  }\n\n")
    }

    private fun writeModifiers(item: Item) {
        ModifierList.write(
            writer = writer,
            modifiers = item.modifiers,
            item = item,
            target = AnnotationTarget.SIGNATURE_FILE,
            includeDeprecated = true,
            skipNullnessAnnotations = options.outputKotlinStyleNulls,
            omitCommonPackages = true
        )
    }

    private fun writeSuperClassStatement(cls: ClassItem) {
        if (cls.isEnum() || cls.isAnnotationType()) {
            return
        }

        val superClass = if (preFiltered)
            cls.superClassType()
        else cls.filteredSuperClassType(filterReference)
        if (superClass != null && !superClass.isJavaLangObject()) {
            val superClassString =
                superClass.toTypeString(
                    kotlinStyleNulls = false,
                    context = superClass.asClass(),
                    filter = filterReference
                )
            write(" extends ")
            write(superClassString)
        }
    }

    private fun writeInterfaceList(cls: ClassItem) {
        if (cls.isAnnotationType()) {
            return
        }
        val isInterface = cls.isInterface()

        val interfaces = if (preFiltered)
            cls.interfaceTypes().asSequence()
        else cls.filteredInterfaceTypes(filterReference).asSequence()

        if (interfaces.any()) {
            val label =
                if (isInterface) {
                    val superInterface = cls.filteredSuperclass(filterReference)
                    if (superInterface != null && !superInterface.isJavaLangObject()) {
                        // For interfaces we've already listed "extends <super interface>"; we don't
                        // want to repeat "extends " here
                        ""
                    } else {
                        " extends"
                    }
                } else {
                    " implements"
                }
            write(label)
            interfaces.sortedWith(TypeItem.comparator).forEach { item ->
                write(" ")
                write(
                    item.toTypeString(
                        kotlinStyleNulls = false,
                        context = item.asClass(),
                        filter = filterReference
                    )
                )
            }
        }
    }

    private fun writeTypeParameterList(typeList: TypeParameterList, addSpace: Boolean) {
        val typeListString = typeList.toString()
        if (typeListString.isNotEmpty()) {
            write(typeListString)
            if (addSpace) {
                write(" ")
            }
        }
    }

    private fun writeParameterList(method: MethodItem) {
        write("(")
        method.parameters().asSequence().forEachIndexed { i, parameter ->
            if (i > 0) {
                write(", ")
            }
            if (parameter.hasDefaultValue() &&
                options.outputDefaultValues &&
                options.outputFormat.conciseDefaultValues
            ) {
                // Concise representation of a parameter with a default
                write("optional ")
            }
            writeModifiers(parameter)
            writeType(parameter, parameter.type())
            val name = parameter.publicName()
            if (name != null) {
                write(" ")
                write(name)
            }
            if (parameter.isDefaultValueKnown() &&
                options.outputDefaultValues &&
                !options.outputFormat.conciseDefaultValues
            ) {
                write(" = ")
                val defaultValue = parameter.defaultValue()
                if (defaultValue != null) {
                    write(defaultValue)
                } else {
                    // null is a valid default value!
                    write("null")
                }
            }
        }
        write(")")
    }

    private fun writeType(
        item: Item,
        type: TypeItem?,
        outputKotlinStyleNulls: Boolean = options.outputKotlinStyleNulls
    ) {
        type ?: return

        var typeString = type.toTypeString(
            outerAnnotations = false,
            innerAnnotations = true,
            erased = false,
            kotlinStyleNulls = outputKotlinStyleNulls,
            context = item,
            filter = filterReference
        )

        // Strip java.lang. prefix
        typeString = TypeItem.shortenTypes(typeString)

        write(typeString)
    }

    private fun writeThrowsList(method: MethodItem) {
        val throws = when {
            preFiltered -> method.throwsTypes().asSequence()
            else -> method.filteredThrowsTypes(filterReference).asSequence()
        }
        if (throws.any()) {
            write(" throws ")
            throws.asSequence().sortedWith(ClassItem.fullNameComparator).forEachIndexed { i, type ->
                if (i > 0) {
                    write(", ")
                }
                write(type.qualifiedName())
            }
        }
    }
}

enum class EmitFileHeader {
    ALWAYS,
    NEVER,
    IF_NONEMPTY_FILE
}
