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
 * @bug 7123229
 * @summary (coll) EnumMap.containsValue(null) returns true
 * @author ngmr
 */
package test.java.util.EnumMap;

import java.util.EnumMap;
import java.util.Map;

import org.testng.Assert;
import org.testng.annotations.Test;

public class UniqueNullValue {
    static enum TestEnum { e00, e01 }

    @Test
    public void testUniqueNull() {
        Map<TestEnum, Integer> map = new EnumMap<>(TestEnum.class);

        map.put(TestEnum.e00, 0);
        Assert.assertTrue(map.containsValue(0));
        Assert.assertFalse(map.containsValue(null));

        map.put(TestEnum.e00, null);
        Assert.assertFalse(map.containsValue(0));
        Assert.assertTrue(map.containsValue(null));
    }
}