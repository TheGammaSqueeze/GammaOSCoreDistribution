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

package android.mediav2.cts;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertTrue;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class MediaFormatUnitTest {
    static final int PER_TEST_TIMEOUT_MS = 10000;

    static {
        System.loadLibrary("ctsmediav2utils_jni");
    }

    @Rule
    public Timeout timeout = new Timeout(PER_TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);

    @Test
    public void testMediaFormatNativeInt32() {
        assertTrue(nativeTestMediaFormatInt32());
    }

    private native boolean nativeTestMediaFormatInt32();

    @Test
    public void testMediaFormatNativeInt64() {
        assertTrue(nativeTestMediaFormatInt64());
    }

    private native boolean nativeTestMediaFormatInt64();

    @Test
    public void testMediaFormatNativeFloat() {
        assertTrue(nativeTestMediaFormatFloat());
    }

    private native boolean nativeTestMediaFormatFloat();

    @Test
    public void testMediaFormatNativeDouble() {
        assertTrue(nativeTestMediaFormatDouble());
    }

    private native boolean nativeTestMediaFormatDouble();

    @Test
    public void testMediaFormatNativeSize() {
        assertTrue(nativeTestMediaFormatSize());
    }

    private native boolean nativeTestMediaFormatSize();

    @Test
    public void testMediaFormatNativeString() {
        assertTrue(nativeTestMediaFormatString());
    }

    private native boolean nativeTestMediaFormatString();

    @Test
    public void testMediaFormatNativeRect() {
        assertTrue(nativeTestMediaFormatRect());
    }

    private native boolean nativeTestMediaFormatRect();

    @Test
    public void testMediaFormatNativeBuffer() {
        assertTrue(nativeTestMediaFormatBuffer());
    }

    private native boolean nativeTestMediaFormatBuffer();

    @Test
    public void testMediaFormatNativeAll() {
        assertTrue(nativeTestMediaFormatAll());
    }

    private native boolean nativeTestMediaFormatAll();
}
