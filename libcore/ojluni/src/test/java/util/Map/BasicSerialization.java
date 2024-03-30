

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
 * @bug 8011200
 * @run testng BasicSerialization
 * @summary Ensure Maps can be serialized and deserialized.
 * @author Mike Duigou
 */
package test.java.util.Map;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ByteArrayInputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;

import org.testng.annotations.Test;
import org.testng.annotations.DataProvider;
import static org.testng.Assert.fail;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertSame;

public class BasicSerialization {

    enum IntegerEnum {

        e0, e1, e2, e3, e4, e5, e6, e7, e8, e9,
        e10, e11, e12, e13, e14, e15, e16, e17, e18, e19,
        e20, e21, e22, e23, e24, e25, e26, e27, e28, e29,
        e30, e31, e32, e33, e34, e35, e36, e37, e38, e39,
        e40, e41, e42, e43, e44, e45, e46, e47, e48, e49,
        e50, e51, e52, e53, e54, e55, e56, e57, e58, e59,
        e60, e61, e62, e63, e64, e65, e66, e67, e68, e69,
        e70, e71, e72, e73, e74, e75, e76, e77, e78, e79,
        e80, e81, e82, e83, e84, e85, e86, e87, e88, e89,
        e90, e91, e92, e93, e94, e95, e96, e97, e98, e99,
        EXTRA_KEY;
        public static final int SIZE = values().length;
    };
    private static final int TEST_SIZE = IntegerEnum.SIZE - 1;
    /**
     * Realized keys ensure that there is always a hard ref to all test objects.
     */
    private static final IntegerEnum[] KEYS = new IntegerEnum[TEST_SIZE];
    /**
     * Realized values ensure that there is always a hard ref to all test
     * objects.
     */
    private static final String[] VALUES = new String[TEST_SIZE];

    static {
        IntegerEnum[] keys = IntegerEnum.values();
        for (int each = 0; each < TEST_SIZE; each++) {
            KEYS[each] = keys[each];
            VALUES[each] = keys[each].name();
        }
    }
    private static final IntegerEnum EXTRA_KEY = IntegerEnum.EXTRA_KEY;
    private static final String EXTRA_VALUE = IntegerEnum.EXTRA_KEY.name();

    public static <K, V> Map<K, V> mapClone(Map<K, V> map) {
        Method cloneMethod;

        try {
            cloneMethod = map.getClass().getMethod("clone", new Class[]{});
        } catch (NoSuchMethodException | SecurityException all) {
            cloneMethod = null;
        }

        if (null != cloneMethod) {
            try {
                Map<K, V> result = (Map<K, V>)cloneMethod.invoke(map, new Object[]{});
                return result;
            } catch (Exception all) {
                fail("clone() failed " + map.getClass().getSimpleName(), all);
                return null;
            }
        } else {
            Constructor<? extends Map> copyConstructor;
            try {
                copyConstructor = (Constructor<? extends Map>)map.getClass().getConstructor(new Class[]{Map.class});

                Map<K, V> result = (Map<K, V>)copyConstructor.newInstance(new Object[]{map});

                return result;
            } catch (Exception all) {
                return serialClone(map);
            }
        }
    }

    @Test(dataProvider = "Map<IntegerEnum,String>")
    public void testSerialization(String description, Map<IntegerEnum, String> map) {
        Object foo = new Object();

        Map<IntegerEnum, String> clone = mapClone(map);
        Map<IntegerEnum, String> serialClone = serialClone(map);

        assertEquals(map, map, description + ":should equal self");
        assertEquals(clone, map, description + ":should equal clone");
        assertEquals(map, clone, description + ": should equal original map");
        assertEquals(serialClone, map, description + ": should equal deserialized clone");
        assertEquals(map, serialClone, description + ": should equal original map");
        assertEquals(serialClone, clone, description + ": deserialized clone should equal clone");
        assertEquals(clone, serialClone, description + ": clone should equal deserialized clone");

        assertFalse(map.containsKey(EXTRA_KEY), description + ":unexpected key");
        assertFalse(clone.containsKey(EXTRA_KEY), description + ":unexpected key");
        assertFalse(serialClone.containsKey(EXTRA_KEY), description + ":unexpected key");
        map.put(EXTRA_KEY, EXTRA_VALUE);
        clone.put(EXTRA_KEY, EXTRA_VALUE);
        serialClone.put(EXTRA_KEY, EXTRA_VALUE);
        assertTrue(map.containsKey(EXTRA_KEY), description + ":missing key");
        assertTrue(clone.containsKey(EXTRA_KEY), description + ":missing key");
        assertTrue(serialClone.containsKey(EXTRA_KEY), description + ":missing key");
        assertSame(map.get(EXTRA_KEY), EXTRA_VALUE, description + ":wrong value");
        assertSame(clone.get(EXTRA_KEY), EXTRA_VALUE, description + ":wrong value");
        assertSame(serialClone.get(EXTRA_KEY), EXTRA_VALUE, description + ":wrong value");

        assertEquals(map, map, description + ":should equal self");
        assertEquals(clone, map, description + ":should equal clone");
        assertEquals(map, clone, description + ": should equal i map");
        assertEquals(serialClone, map, description + ": should equal deserialized clone");
        assertEquals(map, serialClone, description + ": should equal original map");
        assertEquals(serialClone, clone, description + ": deserialized clone should equal clone");
        assertEquals(clone, serialClone, description + ": clone should equal deserialized clone");
    }

    static byte[] serializedForm(Object obj) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            new ObjectOutputStream(baos).writeObject(obj);
            return baos.toByteArray();
        } catch (IOException e) {
            fail("Unexpected Exception", e);
            return null;
        }
    }

    static Object readObject(byte[] bytes) throws IOException, ClassNotFoundException {
        InputStream is = new ByteArrayInputStream(bytes);
        return new ObjectInputStream(is).readObject();
    }

    @SuppressWarnings("unchecked")
    static <T> T serialClone(T obj) {
        try {
            return (T)readObject(serializedForm(obj));
        } catch (IOException | ClassNotFoundException e) {
            fail("Unexpected Exception", e);
            return null;
        }
    }

    @DataProvider(name = "Map<IntegerEnum,String>", parallel = true)
    private static Iterator<Object[]> makeMaps() {
        return Arrays.asList(
                // empty
                new Object[]{"HashMap", new HashMap()},
                new Object[]{"LinkedHashMap", new LinkedHashMap()},
                new Object[]{"Collections.checkedMap(HashMap)", Collections.checkedMap(new HashMap(), IntegerEnum.class, String.class)},
                new Object[]{"Collections.synchronizedMap(HashMap)", Collections.synchronizedMap(new HashMap())},
                // null hostile
                new Object[]{"EnumMap", new EnumMap(IntegerEnum.class)},
                new Object[]{"Hashtable", new Hashtable()},
                new Object[]{"TreeMap", new TreeMap()},
                new Object[]{"ConcurrentHashMap", new ConcurrentHashMap()},
                new Object[]{"ConcurrentSkipListMap", new ConcurrentSkipListMap()},
                new Object[]{"Collections.checkedMap(ConcurrentHashMap)", Collections.checkedMap(new ConcurrentHashMap(), IntegerEnum.class, String.class)},
                new Object[]{"Collections.synchronizedMap(EnumMap)", Collections.synchronizedMap(new EnumMap(IntegerEnum.class))},
                // filled
                new Object[]{"HashMap", fillMap(new HashMap())},
                new Object[]{"LinkedHashMap", fillMap(new LinkedHashMap())},
                new Object[]{"Collections.checkedMap(HashMap)", Collections.checkedMap(fillMap(new HashMap()), IntegerEnum.class, String.class)},
                new Object[]{"Collections.synchronizedMap(HashMap)", Collections.synchronizedMap(fillMap(new HashMap()))},
                // null hostile
                new Object[]{"EnumMap", fillMap(new EnumMap(IntegerEnum.class))},
                new Object[]{"Hashtable", fillMap(new Hashtable())},
                new Object[]{"TreeMap", fillMap(new TreeMap())},
                new Object[]{"ConcurrentHashMap", fillMap(new ConcurrentHashMap())},
                new Object[]{"ConcurrentSkipListMap", fillMap(new ConcurrentSkipListMap())},
                new Object[]{"Collections.checkedMap(ConcurrentHashMap)", Collections.checkedMap(fillMap(new ConcurrentHashMap()), IntegerEnum.class, String.class)},
                new Object[]{"Collections.synchronizedMap(EnumMap)", Collections.synchronizedMap(fillMap(new EnumMap(IntegerEnum.class)))}).iterator();
    }

    private static Map<IntegerEnum, String> fillMap(Map<IntegerEnum, String> result) {
        for (int each = 0; each < TEST_SIZE; each++) {
            result.put(KEYS[each], VALUES[each]);
        }

        return result;
    }
}