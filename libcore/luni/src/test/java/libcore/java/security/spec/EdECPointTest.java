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

package libcore.java.security.spec;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.math.BigInteger;
import java.security.spec.EdECPoint;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class EdECPointTest {

    @Test
    public void testConstructor() {
        EdECPoint p = new EdECPoint(false, BigInteger.TEN);
        assertFalse(p.isXOdd());
        assertEquals(BigInteger.TEN, p.getY());

        p = new EdECPoint(true, BigInteger.valueOf(Long.MAX_VALUE));
        assertTrue(p.isXOdd());
        assertEquals(BigInteger.valueOf(Long.MAX_VALUE), p.getY());
    }
}
