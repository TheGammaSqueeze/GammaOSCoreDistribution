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
 * @bug 8000955
 * @summary Map.Entry implementations need to comply with Map.Entry.hashCode() defined behaviour.
 * @author ngmr
 */
package test.java.util.Map;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;

import org.junit.Assert;
import org.testng.annotations.Test;

public class EntryHashCode {
    private static final int TEST_SIZE = 100;

    static final Object[][] entryData = {
            new Object[TEST_SIZE],
            new Object[TEST_SIZE]
    };

    @SuppressWarnings("unchecked")
    static final Map<Object,Object>[] maps = (Map<Object,Object>[])new Map[] {
            new HashMap<>(),
            new Hashtable<>(),
            new IdentityHashMap<>(),
            new LinkedHashMap<>(),
            new TreeMap<>(),
            new WeakHashMap<>(),
            new ConcurrentHashMap<>(),
            new ConcurrentSkipListMap<>()
    };

    static {
        for (int i = 0; i < entryData[0].length; i++) {
            // key objects need to be Comparable for use in TreeMap
            entryData[0][i] = new Comparable<Object>() {
                public int compareTo(Object o) {
                    return (hashCode() - o.hashCode());
                }
            };
            entryData[1][i] = new Object();
        }
    }

    private static void addTestData(Map<Object,Object> map) {
        for (int i = 0; i < entryData[0].length; i++) {
            map.put(entryData[0][i], entryData[1][i]);
        }
    }

    @Test
    public void testEntryHashCode() throws Exception {
        Exception failure = null;
        for (Map<Object,Object> map: maps) {
            addTestData(map);

            try {
                for (Map.Entry<Object,Object> e: map.entrySet()) {
                    Object key = e.getKey();
                    Object value = e.getValue();
                    int expectedEntryHashCode =
                            (Objects.hashCode(key) ^ Objects.hashCode(value));

                    Assert.assertEquals(e.hashCode(), expectedEntryHashCode);
                }
            } catch (Exception e) {
                if (failure == null) {
                    failure = e;
                } else {
                    failure.addSuppressed(e);
                }
            } finally {
                map.clear();
            }
        }
        if (failure != null) {
            Assert.fail();;
        }
    }
}