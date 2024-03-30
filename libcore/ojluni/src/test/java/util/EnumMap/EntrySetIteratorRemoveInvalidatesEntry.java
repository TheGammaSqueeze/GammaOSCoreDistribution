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
 * @bug 6312706
 * @summary Iterator.remove() from Map.entrySet().iterator() invalidates returned Entry.
 * @author Neil Richards <neil.richards@ngmr.net>, <neil_richards@uk.ibm.com>
 */
package test.java.util.EnumMap;

import java.util.EnumMap;
import java.util.Iterator;
import java.util.Map;

import org.testng.Assert;
import org.testng.annotations.Test;

public class EntrySetIteratorRemoveInvalidatesEntry {
    static enum TestEnum { e00, e01, e02 }

    @Test
    public void testInvalidatesEntry() throws Exception {
        final EnumMap<TestEnum, String> enumMap = new EnumMap<>(TestEnum.class);

        for (TestEnum e : TestEnum.values()) {
            enumMap.put(e, e.name());
        }

        Iterator<Map.Entry<TestEnum, String>> entrySetIterator =
                enumMap.entrySet().iterator();
        Map.Entry<TestEnum, String> entry = entrySetIterator.next();

        entrySetIterator.remove();

        try {
            entry.getKey();
            Assert.fail();
        } catch (Exception e) { }
    }
}