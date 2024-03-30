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

import android.util.Log
import com.android.server.wm.flicker.FLICKER_TAG
import com.android.server.wm.traces.common.tags.Transition
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

object AssertionConfigParser {
    private const val ASSERTORS_KEY = "assertors"
    private const val CLASS_KEY = "class"
    private const val ARGS_KEY = "args"
    private const val TRANSITION_KEY = "transition"
    private const val ASSERTIONS_KEY = "assertions"

    internal const val PRESUBMIT_KEY = "presubmit"
    internal const val POSTSUBMIT_KEY = "postsubmit"
    internal const val FLAKY_KEY = "flaky"

    /**
     * Parses assertor config JSON file. The format expected is:
     * <pre>
     * {
     *  "assertors": [
     *   {
     *      "transition": "ROTATION",
     *      "assertions": {
     *         "presubmit": [
     *              "navBarWindowIsVisible"
     *              "navBarLayerIsVisible",
     *              "navBarLayerRotatesAndScales"
     *          ],
     *          "postsubmit": [ ],
     *          "flaky": [
     *              "entireScreenCovered"
     *          ]
     *      }
     *   }
     *  ]
     * }
     * </pre>
     *
     * @param config string containing a json file
     * @return a list of [AssertionData] assertions
     */
    @JvmStatic
    fun parseConfigFile(config: String): List<AssertionData> {
        val assertorsConfig = mutableListOf<AssertionData>()
        val jsonArray = JSONObject(config).getJSONArray(ASSERTORS_KEY)

        for (i in 0 until jsonArray.length()) {
            val jsonObject = jsonArray.getJSONObject(i)
            val jsonAssertions = jsonObject.getJSONObject(ASSERTIONS_KEY)
            val transitionType = Transition.valueOf(jsonObject.getString(TRANSITION_KEY))
            val presubmit = parseAssertionArray(
                jsonAssertions.getJSONArray(PRESUBMIT_KEY), transitionType, PRESUBMIT_KEY)
            val postsubmit = parseAssertionArray(
                jsonAssertions.getJSONArray(POSTSUBMIT_KEY), transitionType, POSTSUBMIT_KEY)
            val flaky = parseAssertionArray(
                jsonAssertions.getJSONArray(FLAKY_KEY), transitionType, FLAKY_KEY)
            val assertionsList = presubmit + postsubmit + flaky

            assertorsConfig.addAll(assertionsList)
        }

        return assertorsConfig
    }

    /**
     * Splits an assertions JSONArray into an array of [AssertionData].
     *
     * @param assertionsArray a [JSONArray] with assertion names
     * @param transitionType type of transition connected to this assertion
     * @param category the category of the assertion (presubmit/postsubmit/flaky)
     * @return an array of assertion details
     */
    @JvmStatic
    private fun parseAssertionArray(
        assertionsArray: JSONArray,
        transitionType: Transition,
        category: String
    ): List<AssertionData> {
        val assertions = mutableListOf<AssertionData>()
        try {
            for (i in 0 until assertionsArray.length()) {
                val assertionObj = assertionsArray.getJSONObject(i)
                val assertionClass = assertionObj.getString(CLASS_KEY)
                val args = mutableListOf<String>()
                if (assertionObj.has(ARGS_KEY)) {
                    val assertionArgsArray = assertionObj.getJSONArray(ARGS_KEY)
                    for (j in 0 until assertionArgsArray.length()) {
                        val arg = assertionArgsArray.getString(j)
                        args.add(arg)
                    }
                }
                Log.v(FLICKER_TAG, "Creating assertion for class $assertionClass")
                assertions.add(
                    AssertionData.fromString(transitionType, category, assertionClass, args))
            }
        } catch (e: JSONException) {
            throw RuntimeException(e)
        }

        return assertions
    }
}