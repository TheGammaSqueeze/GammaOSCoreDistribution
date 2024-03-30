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
 * @bug 8008785
 * @summary Ensure toArray() implementations return correct results.
 * @author Mike Duigou
 */
package test.java.util.Map;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;

import org.testng.annotations.Test;
import org.testng.Assert;

public class ToArray {

    /**
     * Number of elements per map.
     */
    private static final int TEST_SIZE = 5000;

    @Test
    public void testToArray() throws Throwable {
        Map<Integer, Long>[] maps = (Map<Integer, Long>[]) new Map[]{
                new HashMap<>(),
                new Hashtable<>(),
                new IdentityHashMap<>(),
                new LinkedHashMap<>(),
                new TreeMap<>(),
                new WeakHashMap<>(),
                new ConcurrentHashMap<>(),
                new ConcurrentSkipListMap<>()
        };

        // for each map type.
        for (Map<Integer, Long> map : maps) {
            testMap(map);
        }
    }

    private static final Integer[] KEYS = new Integer[TEST_SIZE];

    private static final Long[] VALUES = new Long[TEST_SIZE];

    static {
        for (int each = 0; each < TEST_SIZE; each++) {
            KEYS[each]   = Integer.valueOf(each);
            VALUES[each] = Long.valueOf(each + TEST_SIZE);
        }
    }


    private static void testMap(Map<Integer, Long> map) {

        // Fill the map
        for (int each = 0; each < TEST_SIZE; each++) {
            map.put(KEYS[each], VALUES[each]);
        }

        // check the keys
        Object[] keys = map.keySet().toArray();
        Arrays.sort(keys);

        for(int each = 0; each < TEST_SIZE; each++) {
            Assert.assertTrue( keys[each] == KEYS[each]);
        }

        // check the values
        Object[] values = map.values().toArray();
        Arrays.sort(values);

        for(int each = 0; each < TEST_SIZE; each++) {
            Assert.assertTrue( values[each] == VALUES[each]);
        }

        // check the entries
        Map.Entry<Integer,Long>[] entries = map.entrySet().toArray(new Map.Entry[TEST_SIZE]);
        Arrays.sort( entries,new Comparator<Map.Entry<Integer,Long>>() {
            public int compare(Map.Entry<Integer,Long> o1, Map.Entry<Integer,Long> o2) {
                return o1.getKey().compareTo(o2.getKey());
            }});

        for(int each = 0; each < TEST_SIZE; each++) {
            Assert.assertTrue( entries[each].getKey() == KEYS[each] && entries[each].getValue() == VALUES[each]);
        }
    }
}