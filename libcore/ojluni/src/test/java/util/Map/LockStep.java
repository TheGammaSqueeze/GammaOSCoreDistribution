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
 * @bug 6612102
 * @summary Test Map implementations for mutual compatibility
 * @key randomness
 */
package test.java.util.Map;

import java.util.Collections;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;

import org.testng.annotations.Test;
import org.testng.Assert;

/**
 * Based on the strange scenario required to reproduce
 * (coll) IdentityHashMap.iterator().remove() might decrement size twice
 *
 * It would be good to add more "Lockstep-style" tests to this file.
 */
public class LockStep {
    void mapsEqual(Map m1, Map m2) {
        Assert.assertEquals(m1, m2);
        Assert.assertEquals(m2, m1);
        Assert.assertEquals(m1.size(), m2.size());
        Assert.assertEquals(m1.isEmpty(), m2.isEmpty());
        Assert.assertEquals(m1.keySet(), m2.keySet());
        Assert.assertEquals(m2.keySet(), m1.keySet());
    }

    void mapsEqual(List<Map> maps) {
        Map first = maps.get(0);
        for (Map map : maps)
            mapsEqual(first, map);
    }

    void put(List<Map> maps, Object key, Object val) {
        for (Map map : maps)
            map.put(key, val);
        mapsEqual(maps);
    }

    void removeLastTwo(List<Map> maps) {
        Map first = maps.get(0);
        int size = first.size();
        Iterator fit = first.keySet().iterator();
        for (int j = 0; j < size - 2; j++)
            fit.next();
        Object x1 = fit.next();
        Object x2 = fit.next();

        for (Map map : maps) {
            Iterator it = map.keySet().iterator();
            while (it.hasNext()) {
                Object x = it.next();
                if (x == x1 || x == x2)
                    it.remove();
            }
        }
        mapsEqual(maps);
    }

    @Test
    public void testLockStep() throws Throwable {
        final int iterations = 100;
        final Random r = new Random();

        for (int i = 0; i < iterations; i++) {
            List<Map> maps = List.of(
                    new IdentityHashMap(11),
                    new HashMap(16),
                    new LinkedHashMap(16),
                    new WeakHashMap(16),
                    new Hashtable(16),
                    new TreeMap(),
                    new ConcurrentHashMap(16),
                    new ConcurrentSkipListMap(),
                    Collections.checkedMap(new HashMap(16), Integer.class, Integer.class),
                    Collections.checkedSortedMap(new TreeMap(), Integer.class, Integer.class),
                    Collections.checkedNavigableMap(new TreeMap(), Integer.class, Integer.class),
                    Collections.synchronizedMap(new HashMap(16)),
                    Collections.synchronizedSortedMap(new TreeMap()),
                    Collections.synchronizedNavigableMap(new TreeMap()));

            for (int j = 0; j < 10; j++)
                put(maps, r.nextInt(100), r.nextInt(100));
            removeLastTwo(maps);
        }
    }
}