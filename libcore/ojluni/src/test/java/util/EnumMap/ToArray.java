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

/*
 * @test
 * @bug     6310858
 * @summary Tests for toArray
 * @author  Martin Buchholz
 */
package test.java.util.EnumMap;

import java.util.*;

import org.testng.Assert;
import org.testng.annotations.Test;

public class ToArray {
    enum Country { FRENCH, POLISH }

    @Test
    public void testToArray() throws Throwable {
        Map<Country, String> m = new EnumMap<Country, String>(Country.class);
        m.put(Country.FRENCH, "connection");
        m.put(Country.POLISH, "sausage");

        Object[] z = m.entrySet().toArray();
        Assert.assertEquals(z.getClass(), Object[].class);
        Assert.assertEquals(z.length, 2);

        Map.Entry[] x1 = new Map.Entry[3];
        x1[2] = m.entrySet().iterator().next();
        Map.Entry[] x2 = m.entrySet().toArray(x1);
        Assert.assertEquals(x1, x2);
        Assert.assertEquals(x2[0].getKey(), Country.FRENCH);
        Assert.assertEquals(x2[1].getKey(), Country.POLISH);
        Assert.assertEquals(x2[2], null);

        Map.Entry[] y1 = new Map.Entry[1];
        Map.Entry[] y2 = m.entrySet().toArray(y1);
        Assert.assertTrue(y1 != y2);
        Assert.assertEquals(y2.length, 2);
        Assert.assertEquals(y2[0].getKey(), Country.FRENCH);
        Assert.assertEquals(y2[1].getKey(), Country.POLISH);
    }
}