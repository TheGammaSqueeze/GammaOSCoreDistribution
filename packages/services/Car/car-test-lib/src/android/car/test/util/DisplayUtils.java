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

package android.car.test.util;

import static android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY;
import static android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC;
import static android.view.Display.DEFAULT_DISPLAY;

import android.content.Context;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.ImageReader;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.Display;

import com.android.compatibility.common.util.TestUtils;

/**
 * Utilities needed when interacting with the display
 */
public class DisplayUtils {
    private static final int DISPLAY_ADDED_TIMEOUT_MS = 10_000;
    private static final String DISPLAY_NAME = "VirtualDisplayForCarTestLib";

    public static class VirtualDisplaySession implements AutoCloseable {
        private VirtualDisplay mVirtualDisplay;
        private ImageReader mReader;

        /**
         * Creates a virtual display having same size with default display and waits until it's
         * in display list. The density of the virtual display is based on
         * {@link DisplayMetrics#xdpi} so that the threshold of gesture detection is same as
         * the default display's.
         *
         * @param context
         * @param isPrivate if this display is a private display.
         * @return virtual display.
         *
         * @throws IllegalStateException if called from main thread.
         */
        public Display createDisplayWithDefaultDisplayMetricsAndWait(Context context,
                boolean isPrivate) {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                throw new IllegalStateException("Should not call from main thread");
            }

            if (mReader != null) {
                throw new IllegalStateException(
                        "Only one display can be created during this session.");
            }

            DisplayManager displayManager = context.getSystemService(DisplayManager.class);
            DisplayMetrics metrics = new DisplayMetrics();
            displayManager.getDisplay(DEFAULT_DISPLAY).getRealMetrics(metrics);

            mReader = ImageReader.newInstance(metrics.widthPixels, metrics.heightPixels,
                    PixelFormat.RGBA_8888, 1 /* maxImages */);

            Object waitObject = new Object();
            DisplayManager.DisplayListener listener = new DisplayManager.DisplayListener() {
                @Override
                public void onDisplayAdded(int i) {
                    synchronized (waitObject) {
                        waitObject.notifyAll();
                    }
                }

                @Override
                public void onDisplayRemoved(int i) {
                }

                @Override
                public void onDisplayChanged(int i) {
                }
            };

            displayManager.registerDisplayListener(listener, null);

            int flags = isPrivate ? 0
                    : (VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY | VIRTUAL_DISPLAY_FLAG_PUBLIC);

            mVirtualDisplay = displayManager.createVirtualDisplay(DISPLAY_NAME,
                    metrics.widthPixels, metrics.heightPixels, (int) metrics.xdpi,
                    mReader.getSurface(), flags);

            try {
                int theNewDisplayId = mVirtualDisplay.getDisplay().getDisplayId();
                TestUtils.waitOn(waitObject,
                        () -> displayManager.getDisplay(theNewDisplayId) != null,
                        DISPLAY_ADDED_TIMEOUT_MS,
                        String.format("wait for virtual display %d adding", theNewDisplayId));
            } finally {
                displayManager.unregisterDisplayListener(listener);
            }

            return mVirtualDisplay.getDisplay();
        }

        @Override
        public void close() {
            if (mVirtualDisplay != null) {
                mVirtualDisplay.release();
            }
            if (mReader != null) {
                mReader.close();
            }
        }
    }
}
