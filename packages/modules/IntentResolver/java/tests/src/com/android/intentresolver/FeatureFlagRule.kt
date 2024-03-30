/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.intentresolver

import com.android.systemui.flags.BooleanFlag
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * Ignores tests annotated with [RequireFeatureFlags] which flag requirements does not
 * meet in the active flag set.
 * @param flags active flag set
 */
internal class FeatureFlagRule(flags: Map<BooleanFlag, Boolean>) : TestRule {
    private val flags = flags.entries.fold(HashMap<String, Boolean>()) { map, (key, value) ->
        map.apply {
            put(key.name, value)
        }
    }
    private val skippingStatement = object : Statement() {
        override fun evaluate() = Unit
    }

    override fun apply(base: Statement, description: Description): Statement {
        val annotation = description.annotations.firstOrNull {
                it is RequireFeatureFlags
            } as? RequireFeatureFlags
            ?: return base

        if (annotation.flags.size != annotation.values.size) {
            error("${description.className}#${description.methodName}: inconsistent number of" +
                    " flags and values in $annotation")
        }
        for (i in annotation.flags.indices) {
            val flag = annotation.flags[i]
            val value = annotation.values[i]
            if (flags.getOrDefault(flag, !value) != value) return skippingStatement
        }
        return base
    }
}
