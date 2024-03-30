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

package com.android.server.wm.traces.common.service

import com.android.server.wm.traces.common.errors.ErrorTrace
import com.android.server.wm.traces.common.layers.LayersTrace
import com.android.server.wm.traces.common.tags.Tag
import com.android.server.wm.traces.common.windowmanager.WindowManagerTrace

/**
 * Interface for the WM Flicker Service Assertor component.
 */
interface ITransitionAssertor {
    /**
     * Analyzes a [WindowManagerTrace] and/or a [LayersTrace] trace to detect flickers.
     *
     * @param tag Tag for the transition
     * @param wmTrace Window Manager trace
     * @param layersTrace Surface Flinger trace
     * @return An error trace
     */
    fun analyze(tag: Tag, wmTrace: WindowManagerTrace, layersTrace: LayersTrace): ErrorTrace
}
