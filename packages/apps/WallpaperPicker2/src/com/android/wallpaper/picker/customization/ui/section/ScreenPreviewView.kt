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
 *
 */

package com.android.wallpaper.picker.customization.ui.section

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewConfiguration
import com.android.wallpaper.picker.SectionView
import kotlin.math.pow
import kotlin.math.sqrt

class ScreenPreviewView(
    context: Context,
    attrs: AttributeSet?,
) :
    SectionView(
        context,
        attrs,
    ) {

    private var downX = 0f
    private var downY = 0f

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            downX = event.x
            downY = event.y
        }

        // We want to intercept clicks so the Carousel MotionLayout child doesn't prevent users from
        // clicking on the screen preview.
        if (isClick(event, downX, downY)) {
            return performClick()
        }

        return super.onInterceptTouchEvent(event)
    }

    companion object {
        private fun isClick(event: MotionEvent, downX: Float, downY: Float): Boolean {
            return when {
                // It's not a click if the event is not an UP action (though it may become one
                // later, when/if an UP is received).
                event.actionMasked != MotionEvent.ACTION_UP -> false
                // It's not a click if too much time has passed between the down and the current
                // event.
                gestureElapsedTime(event) > ViewConfiguration.getTapTimeout() -> false
                // It's not a click if the touch traveled too far.
                distanceMoved(event, downX, downY) > ViewConfiguration.getTouchSlop() -> false
                // Otherwise, this is a click!
                else -> true
            }
        }

        /**
         * Returns the distance that the pointer traveled in the touch gesture the given event is
         * part of.
         */
        private fun distanceMoved(event: MotionEvent, downX: Float, downY: Float): Float {
            val deltaX = event.x - downX
            val deltaY = event.y - downY
            return sqrt(deltaX.pow(2) + deltaY.pow(2))
        }

        /**
         * Returns the elapsed time since the touch gesture the given event is part of has begun.
         */
        private fun gestureElapsedTime(event: MotionEvent): Long {
            return event.eventTime - event.downTime
        }
    }
}
