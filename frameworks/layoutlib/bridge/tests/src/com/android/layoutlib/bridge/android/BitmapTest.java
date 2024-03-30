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

package com.android.layoutlib.bridge.android;

import com.android.layoutlib.bridge.Bridge;
import com.android.layoutlib.bridge.intensive.RenderTestBase;
import com.android.ninepatch.NinePatch;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.Buffer;
import java.nio.ByteBuffer;

import static org.junit.Assert.assertNotNull;

public class BitmapTest extends RenderTestBase {
    @BeforeClass
    public static void setUp() {
        Bridge.prepareThread();
    }

    @Test
    public void testNinePatchChunk() throws IOException {
        InputStream compiled =
                getClass().getResourceAsStream("/com/android/layoutlib/testdata/compiled.9.png");
        Bitmap compiledBitmap = BitmapFactory.decodeStream(compiled, null, null);

        InputStream nonCompiled = getClass().getResourceAsStream(
                "/com/android/layoutlib/testdata/non_compiled.9.png");
        NinePatch ninePatch = NinePatch.load(nonCompiled, true, false);

        Assert.assertArrayEquals(compiledBitmap.getNinePatchChunk(), ninePatch.getChunk().getSerializedChunk());
    }

    @Test
    public void testNativeBitmap() {
        InputStream compiled =
                getClass().getResourceAsStream("/com/android/layoutlib/testdata/compiled.9.png");
        Bitmap compiledBitmap = BitmapFactory.decodeStream(compiled, null, null);
        assertNotNull(compiledBitmap);
        Buffer buffer = ByteBuffer.allocate(compiledBitmap.getByteCount());
        compiledBitmap.copyPixelsToBuffer(buffer);
        buffer.rewind();
        compiledBitmap.copyPixelsFromBuffer(buffer);
    }
}
