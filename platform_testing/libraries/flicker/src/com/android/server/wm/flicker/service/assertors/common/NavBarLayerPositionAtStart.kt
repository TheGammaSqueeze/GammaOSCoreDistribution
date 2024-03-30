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

package com.android.server.wm.flicker.service.assertors.common

import com.android.server.wm.flicker.helpers.WindowUtils
import com.android.server.wm.flicker.service.assertors.BaseAssertion
import com.android.server.wm.flicker.traces.layers.LayersTraceSubject
import com.android.server.wm.flicker.traces.windowmanager.WindowManagerTraceSubject
import com.android.server.wm.traces.common.FlickerComponentName
import com.android.server.wm.traces.common.tags.Tag

/**
 * Checks if the [FlickerComponentName.NAV_BAR] layer is placed at the correct position at the
 * start of the transition
 */
class NavBarLayerPositionAtStart : BaseAssertion() {
    /** {@inheritDoc} */
    override fun doEvaluate(
        tag: Tag,
        wmSubject: WindowManagerTraceSubject,
        layerSubject: LayersTraceSubject
    ) {
        val firstLayersSubject = layerSubject.first()
        val display = firstLayersSubject.entry.displays.minByOrNull { it.id }
            ?: throw RuntimeException("There is no display!")
        firstLayersSubject.visibleRegion(FlickerComponentName.NAV_BAR)
            .coversExactly(WindowUtils.getNavigationBarPosition(display))
    }
}