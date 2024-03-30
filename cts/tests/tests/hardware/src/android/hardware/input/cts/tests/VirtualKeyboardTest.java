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

import android.hardware.input.VirtualKeyEvent;
import android.hardware.input.VirtualKeyboard;
import android.view.InputDevice;
import android.view.KeyEvent;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class VirtualKeyboardTest extends VirtualDeviceTestCase {

    private static final String DEVICE_NAME = "CtsVirtualKeyboardTestDevice";
    private VirtualKeyboard mVirtualKeyboard;

    @Override
    void onSetUpVirtualInputDevice() {
        mVirtualKeyboard = mVirtualDevice.createVirtualKeyboard(mVirtualDisplay, DEVICE_NAME,
                /* vendorId= */ 1, /* productId= */ 1);
    }

    @Override
    void onTearDownVirtualInputDevice() {
        if (mVirtualKeyboard != null) {
            mVirtualKeyboard.close();
        }
    }

    @Test
    public void sendKeyEvent() {
        mVirtualKeyboard.sendKeyEvent(new VirtualKeyEvent.Builder()
                .setKeyCode(KeyEvent.KEYCODE_A)
                .setAction(VirtualKeyEvent.ACTION_DOWN).build());
        mVirtualKeyboard.sendKeyEvent(new VirtualKeyEvent.Builder()
                .setKeyCode(KeyEvent.KEYCODE_A)
                .setAction(VirtualKeyEvent.ACTION_UP).build());
        verifyEvents(Arrays.asList(new KeyEvent(
                        /* downTime= */ 0,
                        /* eventTime= */ 0,
                        KeyEvent.ACTION_DOWN,
                        KeyEvent.KEYCODE_A,
                        /* repeat= */ 0,
                        /* metaState= */ 0,
                        /* deviceId= */ 0,
                        /* scancode= */ 0,
                        /* flags= */ 0,
                        /* source= */ InputDevice.SOURCE_KEYBOARD),
                new KeyEvent(
                        /* downTime= */ 0,
                        /* eventTime= */ 0,
                        KeyEvent.ACTION_UP,
                        KeyEvent.KEYCODE_A,
                        /* repeat= */ 0,
                        /* metaState= */ 0,
                        /* deviceId= */ 0,
                        /* scancode= */ 0,
                        /* flags= */ 0,
                        /* source= */ InputDevice.SOURCE_KEYBOARD)));
    }
}
