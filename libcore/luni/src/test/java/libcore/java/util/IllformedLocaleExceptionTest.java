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

package libcore.java.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.IllformedLocaleException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class IllformedLocaleExceptionTest {

    @Test
    public void testConstructor() {
        IllformedLocaleException exception = new IllformedLocaleException();
        assertNull(exception.getMessage());
        assertEquals(-1, exception.getErrorIndex());
    }

    @Test
    public void testGetErrorIndex() {
        IllformedLocaleException exception = new IllformedLocaleException("message", 6);
        assertEquals(6, exception.getErrorIndex());
    }
}
