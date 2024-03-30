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
 * @bug 7126277
 * @run testng/othervm -Dtest.map.collisions.shortrun=true Collisions
 * @summary Ensure Maps behave well with lots of hashCode() collisions.
 */
package test.java.util.Map;

import java.util.BitSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.function.Supplier;

import org.testng.annotations.Test;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

public class Collisions extends MapWithCollisionsProviders {

    @Test(dataProvider = "mapsWithObjects")
    public void testIntegerIteration(String desc, Supplier<Map<IntKey, IntKey>> ms, IntKey val) {
        Map<IntKey, IntKey> map = ms.get();
        int mapSize = map.size();

        BitSet all = new BitSet(mapSize);
        for (Map.Entry<IntKey, IntKey> each : map.entrySet()) {
            assertFalse(all.get(each.getKey().getValue()), "Iteration: key already seen");
            all.set(each.getKey().getValue());
        }

        all.flip(0, mapSize);
        assertTrue(all.isEmpty(), "Iteration: some keys not visited");

        for (IntKey each : map.keySet()) {
            assertFalse(all.get(each.getValue()), "Iteration: key already seen");
            all.set(each.getValue());
        }

        all.flip(0, mapSize);
        assertTrue(all.isEmpty(), "Iteration: some keys not visited");

        int count = 0;
        for (IntKey each : map.values()) {
            count++;
        }

        assertEquals(map.size(), count,
                String.format("Iteration: value count matches size m%d != c%d", map.size(), count));
    }

    @Test(dataProvider = "mapsWithStrings")
    public void testStringIteration(String desc, Supplier<Map<String, String>> ms, String val) {
        Map<String, String> map = ms.get();
        int mapSize = map.size();

        BitSet all = new BitSet(mapSize);
        for (Map.Entry<String, String> each : map.entrySet()) {
            String key = each.getKey();
            boolean longKey = key.length() > 5;
            int index = key.hashCode() + (longKey ? mapSize / 2 : 0);
            assertFalse(all.get(index), "key already seen");
            all.set(index);
        }

        all.flip(0, mapSize);
        assertTrue(all.isEmpty(), "some keys not visited");

        for (String each : map.keySet()) {
            boolean longKey = each.length() > 5;
            int index = each.hashCode() + (longKey ? mapSize / 2 : 0);
            assertFalse(all.get(index), "key already seen");
            all.set(index);
        }

        all.flip(0, mapSize);
        assertTrue(all.isEmpty(), "some keys not visited");

        int count = 0;
        for (String each : map.values()) {
            count++;
        }

        assertEquals(map.size(), mapSize,
                String.format("value count matches size m%d != k%d", map.size(), mapSize));
    }

    @Test(dataProvider = "mapsWithObjectsAndStrings")
    public void testRemove(String desc, Supplier<Map<Object, Object>> ms, Object val) {
        Map<Object, Object> map = ms.get();
        Object[] keys = map.keySet().toArray();

        for (int i = 0; i < keys.length; i++) {
            Object each = keys[i];
            assertNotNull(map.remove(each),
                    String.format("remove: %s[%d]%s", desc, i, each));
        }

        assertTrue(map.size() == 0 && map.isEmpty(),
                String.format("remove: map empty. size=%d", map.size()));
    }

    @Test(dataProvider = "mapsWithObjectsAndStrings")
    public void testKeysIteratorRemove(String desc, Supplier<Map<Object, Object>> ms, Object val) {
        Map<Object, Object> map = ms.get();

        Iterator<Object> each = map.keySet().iterator();
        while (each.hasNext()) {
            Object t = each.next();
            each.remove();
            assertFalse(map.containsKey(t), String.format("not removed: %s", each));
        }

        assertTrue(map.size() == 0 && map.isEmpty(),
                String.format("remove: map empty. size=%d", map.size()));
    }

    @Test(dataProvider = "mapsWithObjectsAndStrings")
    public void testValuesIteratorRemove(String desc, Supplier<Map<Object, Object>> ms, Object val) {
        Map<Object, Object> map = ms.get();

        Iterator<Object> each = map.values().iterator();
        while (each.hasNext()) {
            Object t = each.next();
            each.remove();
            assertFalse(map.containsValue(t), String.format("not removed: %s", each));
        }

        assertTrue(map.size() == 0 && map.isEmpty(),
                String.format("remove: map empty. size=%d", map.size()));
    }

    @Test(dataProvider = "mapsWithObjectsAndStrings")
    public void testEntriesIteratorRemove(String desc, Supplier<Map<Object, Object>> ms, Object val) {
        Map<Object, Object> map = ms.get();

        Iterator<Map.Entry<Object, Object>> each = map.entrySet().iterator();
        while (each.hasNext()) {
            Map.Entry<Object, Object> t = each.next();
            Object key = t.getKey();
            Object value = t.getValue();
            each.remove();
            assertTrue((map instanceof IdentityHashMap) || !map.entrySet().contains(t),
                    String.format("not removed: %s", each));
            assertFalse(map.containsKey(key),
                    String.format("not removed: %s", each));
            assertFalse(map.containsValue(value),
                    String.format("not removed: %s", each));
        }

        assertTrue(map.size() == 0 && map.isEmpty(),
                String.format("remove: map empty. size=%d", map.size()));
    }

}