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

package com.android.server.wm.flicker.service

import android.device.collectors.BaseCollectionListener
import android.device.collectors.annotations.OptionClass
import com.android.server.wm.traces.common.errors.ErrorTrace
import com.google.common.annotations.VisibleForTesting

/**
 * A {@link FlickerCollectionListener} that captures FASS assertions metrics.
 *
 * <p>Do NOT throw exception anywhere in this class. We don't want to halt the test when metrics
 * collection fails.
 */
@OptionClass(alias = "fass-collector")
class FlickerCollectionListener : BaseCollectionListener<Boolean>() {
    private val collectionHelper: FlickerCollectionHelper = FlickerCollectionHelper()

    init {
        createHelperInstance(collectionHelper)
    }

    fun getErrorTrace(): ErrorTrace {
        return collectionHelper.errorTrace
    }

    @VisibleForTesting
    fun getMetrics(): Map<String, Int> {
        return collectionHelper.metrics
    }

    fun setTransitionClassName(className: String?) {
        this.collectionHelper.setTransitionClassName(className)
    }
}