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
package android.input.cts

import android.graphics.PointF
import android.os.SystemClock
import android.view.Gravity
import android.view.InputDevice
import android.view.InputEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import androidx.test.platform.app.InstrumentationRegistry
import com.android.compatibility.common.util.PollingCheck
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

private fun getViewCenterOnScreen(v: View): PointF {
    val location = IntArray(2)
    v.getLocationOnScreen(location)
    val x = location[0].toFloat() + v.width / 2
    val y = location[1].toFloat() + v.height / 2
    return PointF(x, y)
}

@MediumTest
@RunWith(AndroidJUnit4::class)
class PointerCancelTest {
    @get:Rule
    val activityRule = ActivityScenarioRule<CaptureEventActivity>(CaptureEventActivity::class.java)
    private lateinit var activity: CaptureEventActivity
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private lateinit var verifier: EventVerifier
    @Before
    fun setUp() {
        activityRule.getScenario().onActivity {
            activity = it
        }
        PollingCheck.waitFor { activity.hasWindowFocus() }
        instrumentation.uiAutomation.syncInputTransactions()
        verifier = EventVerifier(activity::getInputEvent)
    }

    /**
     * Check that pointer cancel is received by the activity via injectInputEvent.
     */
    @Test
    fun testPointerCancelMotion() {
        val downTime = SystemClock.uptimeMillis()
        val pointerInDecorView = getViewCenterOnScreen(activity.window.decorView)

        // Start a valid touch stream
        sendEvent(downTime, MotionEvent.ACTION_DOWN, pointerInDecorView, true /*sync*/)
        verifier.assertReceivedDown()

        pointerInDecorView.offset(0f, 1f)
        sendEvent(downTime, MotionEvent.ACTION_MOVE, pointerInDecorView, true /*sync*/)
        verifier.assertReceivedMove()

        val secondPointer = PointF(pointerInDecorView.x + 1, pointerInDecorView.y + 1)
        sendPointersEvent(downTime, ACTION_POINTER_1_DOWN,
                arrayOf(pointerInDecorView, secondPointer),
                0 /*flags*/, true /*sync*/)
        verifier.assertReceivedPointerDown()

        sendPointersEvent(downTime, ACTION_POINTER_1_UP,
                arrayOf(pointerInDecorView, secondPointer),
                MotionEvent.FLAG_CANCELED, true /*sync*/)
        verifier.assertReceivedPointerCancel()

        sendEvent(downTime, MotionEvent.ACTION_UP, pointerInDecorView, true /*sync*/)
        verifier.assertReceivedUp()
    }

    @Test
    fun testPointerCancelForSplitTouch() {
        val view = addFloatingWindow()
        val pointerInFloating = getViewCenterOnScreen(view)
        val downTime = SystemClock.uptimeMillis()
        val pointerOutsideFloating = PointF(pointerInFloating.x + view.width / 2 + 1,
                pointerInFloating.y + view.height / 2 + 1)

        val eventsInFloating = LinkedBlockingQueue<InputEvent>()
        view.setOnTouchListener { v, event ->
            eventsInFloating.add(MotionEvent.obtain(event))
        }
        val verifierForFloating = EventVerifier { eventsInFloating.poll(5, TimeUnit.SECONDS) }

        // First finger down (floating window)
        sendEvent(downTime, MotionEvent.ACTION_DOWN, pointerInFloating, true /*sync*/)
        verifierForFloating.assertReceivedDown()

        // First finger move (floating window)
        pointerInFloating.offset(0f, 1f)
        sendEvent(downTime, MotionEvent.ACTION_MOVE, pointerInFloating, true /*sync*/)
        verifierForFloating.assertReceivedMove()

        // Second finger down (activity window)
        sendPointersEvent(downTime, ACTION_POINTER_1_DOWN,
                arrayOf(pointerInFloating, pointerOutsideFloating),
                0 /*flags*/, true /*sync*/)
        verifier.assertReceivedDown()
        verifierForFloating.assertReceivedMove()

        // ACTION_CANCEL with cancel flag (activity window)
        sendPointersEvent(downTime, ACTION_POINTER_1_UP,
                arrayOf(pointerInFloating, pointerOutsideFloating),
                MotionEvent.FLAG_CANCELED, true /*sync*/)
        verifier.assertReceivedCancel()
        verifierForFloating.assertReceivedMove()

        // First finger up (floating window)
        sendEvent(downTime, MotionEvent.ACTION_UP, pointerInFloating, true /*sync*/)
        verifierForFloating.assertReceivedUp()
    }

    private fun sendEvent(downTime: Long, action: Int, pt: PointF, sync: Boolean) {
        val eventTime = when (action) {
            MotionEvent.ACTION_DOWN -> downTime
            else -> SystemClock.uptimeMillis()
        }
        val event = MotionEvent.obtain(downTime, eventTime, action, pt.x, pt.y, 0 /*metaState*/)
        event.source = InputDevice.SOURCE_TOUCHSCREEN
        instrumentation.uiAutomation.injectInputEvent(event, sync)
    }

    private fun sendPointersEvent(
        downTime: Long,
        action: Int,
        pointers: Array<PointF>,
        flags: Int,
        sync: Boolean
    ) {
        val eventTime = SystemClock.uptimeMillis()
        val pointerCount = pointers.size
        val properties = arrayOfNulls<MotionEvent.PointerProperties>(pointerCount)
        val coords = arrayOfNulls<MotionEvent.PointerCoords>(pointerCount)

        for (i in 0 until pointerCount) {
            properties[i] = MotionEvent.PointerProperties()
            properties[i]!!.id = i
            properties[i]!!.toolType = MotionEvent.TOOL_TYPE_FINGER
            coords[i] = MotionEvent.PointerCoords()
            coords[i]!!.x = pointers[i].x
            coords[i]!!.y = pointers[i].y
        }

        val event = MotionEvent.obtain(downTime, eventTime, action, pointerCount,
                properties, coords, 0 /*metaState*/, 0 /*buttonState*/,
                0f /*xPrecision*/, 0f /*yPrecision*/, 0 /*deviceId*/, 0 /*edgeFlags*/,
                InputDevice.SOURCE_TOUCHSCREEN, flags)
        instrumentation.uiAutomation.injectInputEvent(event, sync)
    }

    private fun addFloatingWindow(): View {
        val view = View(instrumentation.targetContext)
        val layoutParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.TYPE_APPLICATION,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        or WindowManager.LayoutParams.FLAG_SPLIT_TOUCH)
        layoutParams.x = 0
        layoutParams.y = 0
        layoutParams.width = 100
        layoutParams.height = 100
        layoutParams.gravity = Gravity.LEFT or Gravity.CENTER_VERTICAL

        activity.runOnUiThread {
            view.setBackgroundColor(android.graphics.Color.RED)
            activity.windowManager.addView(view, layoutParams)
        }

        PollingCheck.waitFor {
            view.hasWindowFocus()
        }
        instrumentation.uiAutomation.syncInputTransactions()
        return view
    }

    inner class EventVerifier(val getInputEvent: () -> InputEvent?) {
        fun assertReceivedPointerCancel() {
            val event = getInputEvent() as MotionEvent
            assertEquals(MotionEvent.ACTION_POINTER_UP, event.actionMasked)
            assertEquals(MotionEvent.FLAG_CANCELED, event.flags and MotionEvent.FLAG_CANCELED)
        }

        fun assertReceivedCancel() {
            val event = getInputEvent() as MotionEvent
            assertEquals(MotionEvent.ACTION_CANCEL, event.actionMasked)
            assertEquals(MotionEvent.FLAG_CANCELED, event.flags and MotionEvent.FLAG_CANCELED)
        }

        fun assertReceivedDown() {
            val event = getInputEvent() as MotionEvent
            assertEquals(MotionEvent.ACTION_DOWN, event.actionMasked)
        }

        fun assertReceivedPointerDown() {
            val event = getInputEvent() as MotionEvent
            assertEquals(MotionEvent.ACTION_POINTER_DOWN, event.actionMasked)
        }

        fun assertReceivedMove() {
            val event = getInputEvent() as MotionEvent
            assertEquals(MotionEvent.ACTION_MOVE, event.actionMasked)
        }

        fun assertReceivedUp() {
            val event = getInputEvent() as MotionEvent
            assertEquals(MotionEvent.ACTION_UP, event.actionMasked)
        }
    }

    companion object {
        val ACTION_POINTER_1_DOWN = (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT) or
                MotionEvent.ACTION_POINTER_DOWN
        val ACTION_POINTER_1_UP = (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT) or
                MotionEvent.ACTION_POINTER_UP
    }
}
