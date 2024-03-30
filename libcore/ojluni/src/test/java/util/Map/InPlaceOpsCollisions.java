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
 * @bug 8005698
 * @run testng/othervm -Dtest.map.collisions.shortrun=true InPlaceOpsCollisions
 * @summary Ensure overrides of in-place operations in Maps behave well with lots of collisions.
 */
package test.java.util.Map;

import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

import org.testng.annotations.Test;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

public class InPlaceOpsCollisions extends MapWithCollisionsProviders {

    @Test(dataProvider = "mapsWithObjectsAndStrings")
    public void testPutIfAbsent(String desc, Supplier<Map<Object, Object>> ms, Object val) {
        Map<Object, Object> map = ms.get();
        Object[] keys = map.keySet().toArray();
        Object retVal;
        removeOddKeys(map, keys);
        for (int i = 0; i < keys.length; i++) {
            retVal = map.putIfAbsent(keys[i], val);
            if (i % 2 == 0) { // even: not absent, not put

                assertEquals(retVal, keys[i],
                        String.format("putIfAbsent: (%s[%d]) retVal", desc, i));
                assertEquals(keys[i], map.get(keys[i]),
                        String.format("putIfAbsent: get(%s[%d])", desc, i));
                assertTrue(map.containsValue(keys[i]),
                        String.format("putIfAbsent: containsValue(%s[%d])", desc, i));
            } else { // odd: absent, was put
                assertNull(retVal,
                        String.format("putIfAbsent: (%s[%d]) retVal", desc, i));
                assertEquals(val, map.get(keys[i]),
                        String.format("putIfAbsent: get(%s[%d])", desc, i));
                assertFalse(map.containsValue(keys[i]),
                        String.format("putIfAbsent: !containsValue(%s[%d])", desc, i));
            }
            assertTrue(map.containsKey(keys[i]),
                    String.format("insertion: containsKey(%s[%d])", desc, i));
        }
        assertEquals(map.size(), keys.length,
                String.format("map expected size m%d != k%d", map.size(), keys.length));
    }

    @Test(dataProvider = "mapsWithObjectsAndStrings")
    public void testRemoveMapping(String desc, Supplier<Map<Object, Object>> ms, Object val) {
        Map<Object, Object> map = ms.get();
        Object[] keys = map.keySet().toArray();
        boolean removed;
        int removes = 0;
        remapOddKeys(map, keys, val);
        for (int i = 0; i < keys.length; i++) {
            removed = map.remove(keys[i], keys[i]);
            if (i % 2 == 0) { // even: original mapping, should be removed
                assertTrue(removed,
                        String.format("removeMapping: retVal(%s[%d])", desc, i));
                assertNull(map.get(keys[i]),
                        String.format("removeMapping: get(%s[%d])", desc, i));
                assertFalse(map.containsKey(keys[i]),
                        String.format("removeMapping: !containsKey(%s[%d])", desc, i));
                assertFalse(map.containsValue(keys[i]),
                        String.format("removeMapping: !containsValue(%s[%d])", desc, i));
                removes++;
            } else { // odd: new mapping, not removed
                assertFalse(removed,
                        String.format("removeMapping: retVal(%s[%d])", desc, i));
                assertEquals(val, map.get(keys[i]),
                        String.format("removeMapping: get(%s[%d])", desc, i));
                assertTrue(map.containsKey(keys[i]),
                        String.format("removeMapping: containsKey(%s[%d])", desc, i));
                assertTrue(map.containsValue(val),
                        String.format("removeMapping: containsValue(%s[%d])", desc, i));
            }
        }
        assertEquals(map.size(), keys.length - removes,
                String.format("map expected size m%d != k%d", map.size(), keys.length - removes));
    }

    @Test(dataProvider = "mapsWithObjectsAndStrings")
    public void testReplaceOldValue(String desc, Supplier<Map<Object, Object>> ms, Object val) {
        // remap odds to val
        // call replace to replace for val, for all keys
        // check that all keys map to value from keys array
        Map<Object, Object> map = ms.get();
        Object[] keys = map.keySet().toArray();
        boolean replaced;
        remapOddKeys(map, keys, val);

        for (int i = 0; i < keys.length; i++) {
            replaced = map.replace(keys[i], val, keys[i]);
            if (i % 2 == 0) { // even: original mapping, should not be replaced
                assertFalse(replaced,
                        String.format("replaceOldValue: retVal(%s[%d])", desc, i));
            } else { // odd: new mapping, should be replaced
                assertTrue(replaced,
                        String.format("replaceOldValue: get(%s[%d])", desc, i));
            }
            assertEquals(keys[i], map.get(keys[i]),
                    String.format("replaceOldValue: get(%s[%d])", desc, i));
            assertTrue(map.containsKey(keys[i]),
                    String.format("replaceOldValue: containsKey(%s[%d])", desc, i));
            assertTrue(map.containsValue(keys[i]),
                    String.format("replaceOldValue: containsValue(%s[%d])", desc, i));
        }
        assertFalse(map.containsValue(val),
                String.format("replaceOldValue: !containsValue(%s[%s])", desc, val));
        assertEquals(map.size(), keys.length,
                String.format("map expected size m%d != k%d", map.size(), keys.length));
    }

    @Test(dataProvider = "mapsWithObjectsAndStrings")
    public void testReplaceIfMapped(String desc, Supplier<Map<Object, Object>> ms, Object val) {
        // remove odd keys
        // call replace for all keys[]
        // odd keys should remain absent, even keys should be mapped to EXTRA, no value from keys[] should be in map
        Map<Object, Object> map = ms.get();
        Object[] keys = map.keySet().toArray();
        int expectedSize1 = 0;
        removeOddKeys(map, keys);
        int expectedSize2 = map.size();

        for (int i = 0; i < keys.length; i++) {
            Object retVal = map.replace(keys[i], val);
            if (i % 2 == 0) { // even: still in map, should be replaced
                assertEquals(retVal, keys[i],
                        String.format("replaceIfMapped: retVal(%s[%d])", desc, i));
                assertEquals(val, map.get(keys[i]),
                        String.format("replaceIfMapped: get(%s[%d])", desc, i));
                assertTrue(map.containsKey(keys[i]),
                        String.format("replaceIfMapped: containsKey(%s[%d])", desc, i));
                expectedSize1++;
            } else { // odd: was removed, should not be replaced
                assertNull(retVal,
                        String.format("replaceIfMapped: retVal(%s[%d])", desc, i));
                assertNull(map.get(keys[i]),
                        String.format("replaceIfMapped: get(%s[%d])", desc, i));
                assertFalse(map.containsKey(keys[i]),
                        String.format("replaceIfMapped: containsKey(%s[%d])", desc, i));
            }
            assertFalse(map.containsValue(keys[i]),
                    String.format("replaceIfMapped: !containsValue(%s[%d])", desc, i));
        }
        assertTrue(map.containsValue(val),
                String.format("replaceIfMapped: containsValue(%s[%s])", desc, val));
        assertEquals(map.size(), expectedSize1,
                String.format("map expected size#1 m%d != k%d", map.size(), expectedSize1));
        assertEquals(map.size(), expectedSize2,
                String.format("map expected size#2 m%d != k%d", map.size(), expectedSize2));

    }

    private static <T> void testComputeIfAbsent(Map<T, T> map, String desc, T[] keys,
            Function<T, T> mappingFunction) {
        // remove a third of the keys
        // call computeIfAbsent for all keys, func returns EXTRA
        // check that removed keys now -> EXTRA, other keys -> original val
        T expectedVal = mappingFunction.apply(keys[0]);
        T retVal;
        int expectedSize = 0;
        removeThirdKeys(map, keys);
        for (int i = 0; i < keys.length; i++) {
            retVal = map.computeIfAbsent(keys[i], mappingFunction);
            if (i % 3 != 2) { // key present, not computed
                assertEquals(retVal, keys[i],
                        String.format("computeIfAbsent: (%s[%d]) retVal", desc, i));
                assertEquals(keys[i], map.get(keys[i]),
                        String.format("computeIfAbsent: get(%s[%d])", desc, i));
                assertTrue(map.containsValue(keys[i]),
                        String.format("computeIfAbsent: containsValue(%s[%d])", desc, i));
                assertTrue(map.containsKey(keys[i]),
                        String.format("insertion: containsKey(%s[%d])", desc, i));
                expectedSize++;
            } else { // key absent, computed unless function return null
                assertEquals(retVal, expectedVal,
                        String.format("computeIfAbsent: (%s[%d]) retVal", desc, i));
                assertEquals(expectedVal, map.get(keys[i]),
                        String.format("computeIfAbsent: get(%s[%d])", desc, i));
                assertFalse(map.containsValue(keys[i]),
                        String.format("computeIfAbsent: !containsValue(%s[%d])", desc, i));
                // mapping should not be added if function returns null
                assertTrue(map.containsKey(keys[i]) != (expectedVal == null),
                        String.format("insertion: containsKey(%s[%d])", desc, i));
                if (expectedVal != null) {
                    expectedSize++;
                }
            }
        }
        if (expectedVal != null) {
            assertTrue(map.containsValue(expectedVal),
                    String.format("computeIfAbsent: containsValue(%s[%s])", desc, expectedVal));
        }
        assertEquals(map.size(), expectedSize,
                String.format("map expected size m%d != k%d", map.size(), expectedSize));
    }

    @Test(dataProvider = "mapsWithObjectsAndStrings")
    public void testComputeIfAbsentNonNull(String desc, Supplier<Map<Object, Object>> ms, Object val) {
        Map<Object, Object> map = ms.get();
        Object[] keys = map.keySet().toArray();
        testComputeIfAbsent(map, desc, keys, (k) -> val);
    }

    @Test(dataProvider = "mapsWithObjectsAndStrings")
    public void testComputeIfAbsentNull(String desc, Supplier<Map<Object, Object>> ms, Object val) {
        Map<Object, Object> map = ms.get();
        Object[] keys = map.keySet().toArray();
        testComputeIfAbsent(map, desc, keys, (k) -> null);
    }

    private static <T> void testComputeIfPresent(Map<T, T> map, String desc, T[] keys,
            BiFunction<T, T, T> mappingFunction) {
        // remove a third of the keys
        // call testComputeIfPresent for all keys[]
        // removed keys should remain absent, even keys should be mapped to $RESULT
        // no value from keys[] should be in map
        T funcResult = mappingFunction.apply(keys[0], keys[0]);
        int expectedSize1 = 0;
        removeThirdKeys(map, keys);

        for (int i = 0; i < keys.length; i++) {
            T retVal = map.computeIfPresent(keys[i], mappingFunction);
            if (i % 3 != 2) { // key present
                if (funcResult == null) { // was removed
                    assertFalse(map.containsKey(keys[i]),
                            String.format("replaceIfMapped: containsKey(%s[%d])", desc, i));
                } else { // value was replaced
                    assertTrue(map.containsKey(keys[i]),
                            String.format("replaceIfMapped: containsKey(%s[%d])", desc, i));
                    expectedSize1++;
                }
                assertEquals(retVal, funcResult,
                        String.format("computeIfPresent: retVal(%s[%s])", desc, i));
                assertEquals(funcResult, map.get(keys[i]),
                        String.format("replaceIfMapped: get(%s[%d])", desc, i));

            } else { // odd: was removed, should not be replaced
                assertNull(retVal,
                        String.format("replaceIfMapped: retVal(%s[%d])", desc, i));
                assertNull(map.get(keys[i]),
                        String.format("replaceIfMapped: get(%s[%d])", desc, i));
                assertFalse(map.containsKey(keys[i]),
                        String.format("replaceIfMapped: containsKey(%s[%d])", desc, i));
            }
            assertFalse(map.containsValue(keys[i]),
                    String.format("replaceIfMapped: !containsValue(%s[%d])", desc, i));
        }
        assertEquals(map.size(), expectedSize1,
                String.format("map expected size#1 m%d != k%d", map.size(), expectedSize1));
    }

    @Test(dataProvider = "mapsWithObjectsAndStrings")
    public void testComputeIfPresentNonNull(String desc, Supplier<Map<Object, Object>> ms, Object val) {
        Map<Object, Object> map = ms.get();
        Object[] keys = map.keySet().toArray();
        testComputeIfPresent(map, desc, keys, (k, v) -> val);
    }

    @Test(dataProvider = "mapsWithObjectsAndStrings")
    public void testComputeIfPresentNull(String desc, Supplier<Map<Object, Object>> ms, Object val) {
        Map<Object, Object> map = ms.get();
        Object[] keys = map.keySet().toArray();
        testComputeIfPresent(map, desc, keys, (k, v) -> null);
    }

    @Test(dataProvider = "hashMapsWithObjects")
    public void testComputeNonNull(String desc, Supplier<Map<IntKey, IntKey>> ms, IntKey val) {
        // remove a third of the keys
        // call compute() for all keys[]
        // all keys should be present: removed keys -> EXTRA, others to k-1
        Map<IntKey, IntKey> map = ms.get();
        IntKey[] keys = map.keySet().stream().sorted().toArray(IntKey[]::new);
        BiFunction<IntKey, IntKey, IntKey> mappingFunction = (k, v) -> {
            if (v == null) {
                return val;
            } else {
                return keys[k.getValue() - 1];
            }
        };
        removeThirdKeys(map, keys);
        for (int i = 1; i < keys.length; i++) {
            IntKey retVal = map.compute(keys[i], mappingFunction);
            if (i % 3 != 2) { // key present, should be mapped to k-1
                assertEquals(retVal, keys[i - 1],
                        String.format("compute: retVal(%s[%d])", desc, i));
                assertEquals(keys[i - 1], map.get(keys[i]),
                        String.format("compute: get(%s[%d])", desc, i));
            } else { // odd: was removed, should be replaced with EXTRA
                assertEquals(retVal, val,
                        String.format("compute: retVal(%s[%d])", desc, i));
                assertEquals(val, map.get(keys[i]),
                        String.format("compute: get(%s[%d])", desc, i));
            }
            assertTrue(map.containsKey(keys[i]),
                    String.format("compute: containsKey(%s[%d])", desc, i));
        }
        assertEquals(map.size(), keys.length,
                String.format("map expected size#1 m%d != k%d", map.size(), keys.length));
        assertTrue(map.containsValue(val),
                String.format("compute: containsValue(%s[%s])", desc, val));
        assertFalse(map.containsValue(null),
                String.format("compute: !containsValue(%s,[null])", desc));
    }

    @Test(dataProvider = "mapsWithObjectsAndStrings")
    public void testComputeNull(String desc, Supplier<Map<Object, Object>> ms, Object val) {
        // remove a third of the keys
        // call compute() for all keys[]
        // removed keys should -> EXTRA
        // for other keys: func returns null, should have no mapping
        Map<Object, Object> map = ms.get();
        Object[] keys = map.keySet().toArray();
        BiFunction<Object, Object, Object> mappingFunction = (k, v) -> {
            // if absent/null -> EXTRA
            // if present -> null
            if (v == null) {
                return val;
            } else {
                return null;
            }
        };
        int expectedSize = 0;
        removeThirdKeys(map, keys);
        for (int i = 0; i < keys.length; i++) {
            Object retVal = map.compute(keys[i], mappingFunction);
            if (i % 3 != 2) { // key present, func returned null, should be absent from map
                assertNull(retVal,
                        String.format("compute: retVal(%s[%d])", desc, i));
                assertNull(map.get(keys[i]),
                        String.format("compute: get(%s[%d])", desc, i));
                assertFalse(map.containsKey(keys[i]),
                        String.format("compute: containsKey(%s[%d])", desc, i));
                assertFalse(map.containsValue(keys[i]),
                        String.format("compute: containsValue(%s[%s])", desc, i));
            } else { // odd: was removed, should now be mapped to EXTRA
                assertEquals(retVal, val,
                        String.format("compute: retVal(%s[%d])", desc, i));
                assertEquals(val, map.get(keys[i]),
                        String.format("compute: get(%s[%d])", desc, i));
                assertTrue(map.containsKey(keys[i]),
                        String.format("compute: containsKey(%s[%d])", desc, i));
                expectedSize++;
            }
        }
        assertTrue(map.containsValue(val),
                String.format("compute: containsValue(%s[%s])", desc, val));
        assertEquals(map.size(), expectedSize,
                String.format("map expected size#1 m%d != k%d", map.size(), expectedSize));
    }

    @Test(dataProvider = "hashMapsWithObjects")
    public void testMergeNonNull(String desc, Supplier<Map<IntKey, IntKey>> ms, IntKey val) {
        // remove a third of the keys
        // call merge() for all keys[]
        // all keys should be present: removed keys now -> EXTRA, other keys -> k-1
        Map<IntKey, IntKey> map = ms.get();
        IntKey[] keys = map.keySet().stream().sorted().toArray(IntKey[]::new);

        // Map to preceding key
        BiFunction<IntKey, IntKey, IntKey> mappingFunction
                = (k, v) -> keys[k.getValue() - 1];
        removeThirdKeys(map, keys);
        for (int i = 1; i < keys.length; i++) {
            IntKey retVal = map.merge(keys[i], val, mappingFunction);
            if (i % 3 != 2) { // key present, should be mapped to k-1
                assertEquals(retVal, keys[i - 1],
                        String.format("compute: retVal(%s[%d])", desc, i));
                assertEquals(keys[i - 1], map.get(keys[i]),
                        String.format("compute: get(%s[%d])", desc, i));
            } else { // odd: was removed, should be replaced with EXTRA
                assertEquals(retVal, val,
                        String.format("compute: retVal(%s[%d])", desc, i));
                assertEquals(val, map.get(keys[i]),
                        String.format("compute: get(%s[%d])", desc, i));
            }
            assertTrue(map.containsKey(keys[i]),
                    String.format("compute: containsKey(%s[%d])", desc, i));
        }

        assertEquals(map.size(), keys.length,
                String.format("map expected size#1 m%d != k%d", map.size(), keys.length));
        assertTrue(map.containsValue(val),
                String.format("compute: containsValue(%s[%s])", desc, val));
        assertFalse(map.containsValue(null),
                String.format("compute: !containsValue(%s,[null])", desc));
    }

    @Test(dataProvider = "mapsWithObjectsAndStrings")
    public void testMergeNull(String desc, Supplier<Map<Object, Object>> ms, Object val) {
        // remove a third of the keys
        // call merge() for all keys[]
        // result: removed keys -> EXTRA, other keys absent

        Map<Object, Object> map = ms.get();
        Object[] keys = map.keySet().toArray();
        BiFunction<Object, Object, Object> mappingFunction = (k, v) -> null;
        int expectedSize = 0;
        removeThirdKeys(map, keys);
        for (int i = 0; i < keys.length; i++) {
            Object retVal = map.merge(keys[i], val, mappingFunction);
            if (i % 3 != 2) { // key present, func returned null, should be absent from map
                assertNull(retVal,
                        String.format("compute: retVal(%s[%d])", desc, i));
                assertNull(map.get(keys[i]),
                        String.format("compute: get(%s[%d])", desc, i));
                assertFalse(map.containsKey(keys[i]),
                        String.format("compute: containsKey(%s[%d])", desc, i));
            } else { // odd: was removed, should now be mapped to EXTRA
                assertEquals(retVal, val,
                        String.format("compute: retVal(%s[%d])", desc, i));
                assertEquals(val, map.get(keys[i]),
                        String.format("compute: get(%s[%d])", desc, i));
                assertTrue(map.containsKey(keys[i]),
                        String.format("compute: containsKey(%s[%d])", desc, i));
                expectedSize++;
            }
            assertFalse(map.containsValue(keys[i]),
                    String.format("compute: containsValue(%s[%s])", desc, i));
        }
        assertTrue(map.containsValue(val),
                String.format("compute: containsValue(%s[%s])", desc, val));
        assertEquals(map.size(), expectedSize,
                String.format("map expected size#1 m%d != k%d", map.size(), expectedSize));
    }

    /*
     * Remove half of the keys
     */
    private static <T> void removeOddKeys(Map<T, T> map, /*String keys_desc, */ T[] keys) {
        int removes = 0;
        for (int i = 0; i < keys.length; i++) {
            if (i % 2 != 0) {
                map.remove(keys[i]);
                removes++;
            }
        }
        assertEquals(map.size(), keys.length - removes,
                String.format("map expected size m%d != k%d", map.size(), keys.length - removes));
    }

    /*
     * Remove every third key
     * This will hopefully leave some removed keys in TreeBins for, e.g., computeIfAbsent
     * w/ a func that returns null.
     *
     * TODO: consider using this in other tests (and maybe adding a remapThirdKeys)
     */
    private static <T> void removeThirdKeys(Map<T, T> map, /*String keys_desc, */ T[] keys) {
        int removes = 0;
        for (int i = 0; i < keys.length; i++) {
            if (i % 3 == 2) {
                map.remove(keys[i]);
                removes++;
            }
        }
        assertEquals(map.size(), keys.length - removes,
                String.format("map expected size m%d != k%d", map.size(), keys.length - removes));
    }

    /*
     * Re-map the odd-numbered keys to map to the EXTRA value
     */
    private static <T> void remapOddKeys(Map<T, T> map, T[] keys, T val) {
        for (int i = 0; i < keys.length; i++) {
            if (i % 2 != 0) {
                map.put(keys[i], val);
            }
        }
    }

}