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

package android.hardware.input.cts.tests;

import static org.junit.Assert.assertEquals;

import android.graphics.PointF;
import android.hardware.input.VirtualMouse;
import android.hardware.input.VirtualMouseButtonEvent;
import android.hardware.input.VirtualMouseRelativeEvent;
import android.hardware.input.VirtualMouseScrollEvent;
import android.view.InputDevice;
import android.view.MotionEvent;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class VirtualMouseTest extends VirtualDeviceTestCase {

    private static final String DEVICE_NAME = "CtsVirtualMouseTestDevice";

    private static final float EPSILON = 0.001f;

    private VirtualMouse mVirtualMouse;

    @Override
    void onSetUpVirtualInputDevice() {
        mVirtualMouse = mVirtualDevice.createVirtualMouse(mVirtualDisplay, DEVICE_NAME,
                /* vendorId= */ 1, /* productId= */ 1);
    }

    @Override
    void onTearDownVirtualInputDevice() {
        if (mVirtualMouse != null) {
            mVirtualMouse.close();
        }
    }

    @Test
    public void sendButtonEvent() {
        final PointF startPosition = mVirtualMouse.getCursorPosition();
        mVirtualMouse.sendButtonEvent(new VirtualMouseButtonEvent.Builder()
                .setAction(VirtualMouseButtonEvent.ACTION_BUTTON_PRESS)
                .setButtonCode(VirtualMouseButtonEvent.BUTTON_PRIMARY)
                .build());
        mVirtualMouse.sendButtonEvent(new VirtualMouseButtonEvent.Builder()
                .setAction(VirtualMouseButtonEvent.ACTION_BUTTON_RELEASE)
                .setButtonCode(VirtualMouseButtonEvent.BUTTON_PRIMARY)
                .build());
        final MotionEvent buttonPressEvent = createMotionEvent(MotionEvent.ACTION_BUTTON_PRESS,
                startPosition.x, startPosition.y, /* relativeX= */ 0f, /* relativeY= */ 0f,
                /* vScroll= */ 0f, /* hScroll= */ 0f, MotionEvent.BUTTON_PRIMARY,
                /* pressure= */ 1.0f);
        buttonPressEvent.setActionButton(MotionEvent.BUTTON_PRIMARY);
        final MotionEvent buttonReleaseEvent = createMotionEvent(MotionEvent.ACTION_BUTTON_RELEASE,
                startPosition.x, startPosition.y, /* relativeX= */ 0f, /* relativeY= */ 0f,
                /* vScroll= */ 0f, /* hScroll= */ 0f, /* buttonState= */ 0, /* pressure= */ 0.0f);
        buttonReleaseEvent.setActionButton(MotionEvent.BUTTON_PRIMARY);
        verifyEvents(Arrays.asList(
                createMotionEvent(MotionEvent.ACTION_DOWN, startPosition.x, startPosition.y,
                        /* relativeX= */ 0f, /* relativeY= */ 0f, /* vScroll= */ 0f,
                        /* hScroll= */ 0f, MotionEvent.BUTTON_PRIMARY, /* pressure= */ 1.0f),
                buttonPressEvent,
                buttonReleaseEvent,
                createMotionEvent(MotionEvent.ACTION_UP, startPosition.x, startPosition.y,
                        /* relativeX= */ 0f, /* relativeY= */ 0f, /* vScroll= */ 0f,
                        /* hScroll= */ 0f, /* buttonState= */ 0, /* pressure= */ 0.0f),
                createMotionEvent(MotionEvent.ACTION_HOVER_ENTER, startPosition.x,
                        startPosition.y, /* relativeX= */ 0f, /* relativeY= */ 0f,
                        /* vScroll= */ 0f, /* hScroll= */ 0f, /* buttonState= */ 0,
                        /* pressure= */ 0.0f)));
    }

    @Test
    public void sendRelativeEvent() {
        final PointF startPosition = mVirtualMouse.getCursorPosition();
        final float relativeChangeX = 25f;
        final float relativeChangeY = 35f;
        mVirtualMouse.sendRelativeEvent(new VirtualMouseRelativeEvent.Builder()
                .setRelativeY(relativeChangeY)
                .setRelativeX(relativeChangeX)
                .build());
        final float firstStopPositionX = startPosition.x + relativeChangeX;
        final float firstStopPositionY = startPosition.y + relativeChangeY;
        verifyEvents(Arrays.asList(
                createMotionEvent(MotionEvent.ACTION_HOVER_ENTER, firstStopPositionX,
                        firstStopPositionY, relativeChangeX, relativeChangeY, /* vScroll= */ 0f,
                        /* hScroll= */ 0f, /* buttonState= */ 0, /* pressure= */ 0.0f),
                createMotionEvent(MotionEvent.ACTION_HOVER_MOVE, firstStopPositionX,
                        firstStopPositionY, relativeChangeX, relativeChangeY, /* vScroll= */ 0f,
                        /* hScroll= */ 0f, /* buttonState= */ 0, /* pressure= */ 0.0f)));
        final PointF cursorPosition1 = mVirtualMouse.getCursorPosition();
        assertEquals("getCursorPosition() should return the updated x position",
                firstStopPositionX, cursorPosition1.x, EPSILON);
        assertEquals("getCursorPosition() should return the updated y position",
                firstStopPositionY, cursorPosition1.y, EPSILON);

        final float secondStopPositionX = firstStopPositionX - relativeChangeX;
        final float secondStopPositionY = firstStopPositionY - relativeChangeY;
        mVirtualMouse.sendRelativeEvent(new VirtualMouseRelativeEvent.Builder()
                .setRelativeY(-relativeChangeY)
                .setRelativeX(-relativeChangeX)
                .build());

        // b/263272128: Consume hover enter, if received, to make the test permissive against new
        // and existing behaviors.
        maybeConsumeHoverEnter();

        verifyEvents(Arrays.asList(
                createMotionEvent(MotionEvent.ACTION_HOVER_MOVE, secondStopPositionX,
                        secondStopPositionY, -relativeChangeX,
                        -relativeChangeY, /* vScroll= */ 0f,
                        /* hScroll= */ 0f, /* buttonState= */ 0, /* pressure= */ 0.0f)));
        final PointF cursorPosition2 = mVirtualMouse.getCursorPosition();
        assertEquals("getCursorPosition() should return the updated x position",
                secondStopPositionX, cursorPosition2.x, EPSILON);
        assertEquals("getCursorPosition() should return the updated y position",
                secondStopPositionY, cursorPosition2.y, EPSILON);
    }

    @Test
    public void sendScrollEvent() {
        final PointF startPosition = mVirtualMouse.getCursorPosition();
        final float moveX = 0f;
        final float moveY = 1f;
        mVirtualMouse.sendScrollEvent(new VirtualMouseScrollEvent.Builder()
                .setYAxisMovement(moveY)
                .setXAxisMovement(moveX)
                .build());
        verifyEvents(Arrays.asList(
                createMotionEvent(MotionEvent.ACTION_HOVER_ENTER, startPosition.x,
                        startPosition.y, /* relativeX= */ 0f, /* relativeY= */ 0f,
                        /* vScroll= */ 0f, /* hScroll= */ 0f, /* buttonState= */ 0,
                        /* pressure= */ 0f),
                createMotionEvent(MotionEvent.ACTION_HOVER_MOVE, startPosition.x,
                        startPosition.y, /* relativeX= */ 0f, /* relativeY= */ 0f,
                        /* vScroll= */ 0f, /* hScroll= */ 0f, /* buttonState= */ 0,
                        /* pressure= */ 0f),
                createMotionEvent(MotionEvent.ACTION_SCROLL, startPosition.x,
                        startPosition.y, /* relativeX= */ 0f, /* relativeY= */ 0f,
                        /* vScroll= */ 1f, /* hScroll= */ 0f, /* buttonState= */ 0,
                        /* pressure= */ 0f)));
    }

    @Test
    public void testStartingCursorPosition() {
        // The virtual display is 100x100px, running from [0,99]. Half of this is 49.5, and
        // we assume the pointer for a new display begins at the center.
        final PointF startPosition = new PointF((DISPLAY_WIDTH - 1) / 2f,
                (DISPLAY_HEIGHT - 1) / 2f);
        // Trigger a position update without moving the cursor off the starting position.
        mVirtualMouse.sendButtonEvent(new VirtualMouseButtonEvent.Builder()
                .setAction(VirtualMouseButtonEvent.ACTION_BUTTON_PRESS)
                .setButtonCode(VirtualMouseButtonEvent.BUTTON_PRIMARY)
                .build());
        final MotionEvent buttonPressEvent = createMotionEvent(MotionEvent.ACTION_BUTTON_PRESS,
                startPosition.x, startPosition.y, /* relativeX= */ 0f, /* relativeY= */ 0f,
                /* vScroll= */ 0f, /* hScroll= */ 0f, MotionEvent.BUTTON_PRIMARY,
                /* pressure= */ 1.0f);
        buttonPressEvent.setActionButton(MotionEvent.BUTTON_PRIMARY);
        verifyEvents(Arrays.asList(
                createMotionEvent(MotionEvent.ACTION_DOWN, startPosition.x, startPosition.y,
                        /* relativeX= */ 0f, /* relativeY= */ 0f, /* vScroll= */ 0f,
                        /* hScroll= */ 0f, MotionEvent.BUTTON_PRIMARY, /* pressure= */ 1.0f),
                buttonPressEvent));

        final PointF position = mVirtualMouse.getCursorPosition();

        assertEquals("Cursor position x differs", startPosition.x, position.x, EPSILON);
        assertEquals("Cursor position y differs", startPosition.y, position.y, EPSILON);
    }

    private MotionEvent createMotionEvent(int action, float x, float y, float relativeX,
            float relativeY, float vScroll, float hScroll, int buttonState, float pressure) {
        final MotionEvent.PointerProperties pointerProperties = new MotionEvent.PointerProperties();
        pointerProperties.toolType = MotionEvent.TOOL_TYPE_MOUSE;
        final MotionEvent.PointerCoords pointerCoords = new MotionEvent.PointerCoords();
        pointerCoords.setAxisValue(MotionEvent.AXIS_X, x);
        pointerCoords.setAxisValue(MotionEvent.AXIS_Y, y);
        pointerCoords.setAxisValue(MotionEvent.AXIS_RELATIVE_X, relativeX);
        pointerCoords.setAxisValue(MotionEvent.AXIS_RELATIVE_Y, relativeY);
        pointerCoords.setAxisValue(MotionEvent.AXIS_PRESSURE, pressure);
        pointerCoords.setAxisValue(MotionEvent.AXIS_VSCROLL, vScroll);
        pointerCoords.setAxisValue(MotionEvent.AXIS_HSCROLL, hScroll);
        return MotionEvent.obtain(
                /* downTime= */ 0,
                /* eventTime= */ 0,
                action,
                /* pointerCount= */ 1,
                new MotionEvent.PointerProperties[]{pointerProperties},
                new MotionEvent.PointerCoords[]{pointerCoords},
                /* metaState= */ 0,
                buttonState,
                /* xPrecision= */ 1f,
                /* yPrecision= */ 1f,
                /* deviceId= */ 0,
                /* edgeFlags= */ 0,
                InputDevice.SOURCE_MOUSE,
                /* flags= */ 0);
    }
}
