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

package com.google.android.mobly.snippet.util

import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.mobly.snippet.event.EventSnippet
import com.google.android.mobly.snippet.util.Log
import com.google.common.truth.Truth.assertThat
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Robolectric tests for SnippetEventHelper.kt. */
@RunWith(RobolectricTestRunner::class)
class SnippetEventHelperTest {

  @Test
  fun testPostSnippetEvent_withDataBundle_writesEventCache() {
    val testCallbackId = "test_1234"
    val testEventName = "onTestEvent"
    val testBundleDataStrKey = "testStrKey"
    val testBundleDataStrValue = "testStrValue"
    val testBundleDataIntKey = "testIntKey"
    val testBundleDataIntValue = 777
    val eventSnippet = EventSnippet()
    Log.initLogTag(InstrumentationRegistry.getInstrumentation().context)

    postSnippetEvent(testCallbackId, testEventName) {
      putString(testBundleDataStrKey, testBundleDataStrValue)
      putInt(testBundleDataIntKey, testBundleDataIntValue)
    }

    val event = eventSnippet.eventWaitAndGet(testCallbackId, testEventName, null)
    assertThat(event.getJSONObject("data").toString())
      .isEqualTo(
        JSONObject()
          .put(testBundleDataIntKey, testBundleDataIntValue)
          .put(testBundleDataStrKey, testBundleDataStrValue)
          .toString()
      )
  }
}
