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

import android.os.Bundle
import com.google.android.mobly.snippet.event.EventCache
import com.google.android.mobly.snippet.event.SnippetEvent

/**
 * Posts an {@link SnippetEvent} to the event cache with data bundle [fill] by the given function.
 *
 * This is a helper function to make your client side codes more concise. Sample usage:
 * ```
 *   postSnippetEvent(callbackId, "onReceiverFound") {
 *     putLong("discoveryTimeMs", discoveryTimeMs)
 *     putBoolean("isKnown", isKnown)
 *   }
 * ```
 *
 * @param callbackId the callbackId passed to the {@link
 * com.google.android.mobly.snippet.rpc.AsyncRpc} method.
 * @param eventName the name of the event.
 * @param fill the function to fill the data bundle.
 */
fun postSnippetEvent(callbackId: String, eventName: String, fill: Bundle.() -> Unit) {
  val eventData = Bundle().apply(fill)
  val snippetEvent = SnippetEvent(callbackId, eventName).apply { data.putAll(eventData) }
  EventCache.getInstance().postEvent(snippetEvent)
}
