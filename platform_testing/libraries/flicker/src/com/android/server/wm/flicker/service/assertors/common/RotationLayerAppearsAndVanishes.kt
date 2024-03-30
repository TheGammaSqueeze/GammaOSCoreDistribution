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

import com.android.server.wm.flicker.service.assertors.BaseAssertion
import com.android.server.wm.flicker.traces.layers.LayersTraceSubject
import com.android.server.wm.flicker.traces.windowmanager.WindowManagerTraceSubject
import com.android.server.wm.traces.common.FlickerComponentName
import com.android.server.wm.traces.common.tags.Tag

/**
 * Checks that the [FlickerComponentName.ROTATION] layer appears during the transition,
 * doesn't flicker, and disappears before the transition is complete.
 */
class RotationLayerAppearsAndVanishes : BaseAssertion() {
    /** {@inheritDoc} */
    override fun doEvaluate(
        tag: Tag,
        wmSubject: WindowManagerTraceSubject,
        layerSubject: LayersTraceSubject
    ) {
        val window = wmSubject.first().wmState.topVisibleAppWindow
        val appComponent = FlickerComponentName.unflattenFromString(window)
        layerSubject.isVisible(appComponent)
            .then()
            .isVisible(FlickerComponentName.ROTATION)
            .then()
            .isVisible(appComponent)
            .isInvisible(FlickerComponentName.ROTATION)
            .forAllEntries()
    }
}