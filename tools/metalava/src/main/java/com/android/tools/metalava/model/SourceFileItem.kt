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

package com.android.tools.metalava.model

import com.android.tools.metalava.model.visitors.ItemVisitor
import com.android.tools.metalava.model.visitors.TypeVisitor
import java.util.function.Predicate

/** Represents a Kotlin/Java source file */
interface SourceFileItem : Item {
    /** Top level classes contained in this file */
    fun classes(): Sequence<ClassItem>

    fun getHeaderComments(): String? = null

    fun getImportStatements(predicate: Predicate<Item>): Collection<Item> = emptyList()

    override fun parent(): PackageItem? = containingPackage()

    override fun containingClass(strict: Boolean): ClassItem? = null

    override fun type(): TypeItem? = null

    override fun accept(visitor: ItemVisitor) {
        if (visitor.skip(this)) return

        visitor.visitItem(this)
        visitor.visitSourceFile(this)

        classes().forEach { it.accept(visitor) }

        visitor.afterVisitSourceFile(this)
        visitor.afterVisitItem(this)
    }

    override fun acceptTypes(visitor: TypeVisitor) {
        if (visitor.skip(this)) return

        classes().forEach { it.acceptTypes(visitor) }
    }
}
