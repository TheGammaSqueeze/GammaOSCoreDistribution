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

package com.android.server.wm.flicker.service.assertors

import com.android.server.wm.flicker.service.FlickerService
import com.android.server.wm.traces.common.tags.Transition
import java.io.FileNotFoundException

/**
 * Stores data for FASS assertions.
 */
data class AssertionData(
    val transitionType: Transition,
    val assertion: BaseAssertion,
    val category: String
) {
    companion object {
        /**
         * Returns the name of the assertors configuration file.
         */
        private const val CONFIG_FILE_NAME = "config.json"

        /**
         * Creates an assertion data based on it's fully-qualified class path [cls] and set
         * its category to [category]
         */
        fun fromString(
            transitionType: Transition,
            category: String,
            cls: String,
            args: List<String>
        ): AssertionData {
            val clsDescriptor = Class.forName(cls)

            val assertionObj = if (args.isEmpty()) {
                clsDescriptor.newInstance() as BaseAssertion
            } else {
                val ctor = clsDescriptor.constructors
                    .firstOrNull { it.parameterCount == args.size }
                    ?: error("Constructor not found")
                ctor.newInstance(*args.toTypedArray()) as BaseAssertion
            }

            return AssertionData(transitionType, assertionObj, category)
        }

        /**
         * Reads the assertions configuration for the configuration file.
         *
         * @param fileName the location of the configuration file
         * @return a list with assertors configuration
         *
         * @throws FileNotFoundException when there is no config file
         */
        @JvmOverloads
        fun readConfiguration(fileName: String = CONFIG_FILE_NAME): List<AssertionData> {
            val fileContent = FlickerService::class.java.classLoader.getResource(fileName)
                ?.readText(Charsets.UTF_8)
                ?: throw FileNotFoundException("A configuration file must exist!")
            return AssertionConfigParser.parseConfigFile(fileContent)
        }
    }
}
