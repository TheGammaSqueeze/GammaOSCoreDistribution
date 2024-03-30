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
 * @bug 8139233
 * @summary ensure entry set's iterator doesn't have side effects on the entry set
 * @run testng EntrySetIterator
 */
package test.java.util.Map;

import java.util.*;
import org.testng.annotations.Test;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.assertEquals;

public class EntrySetIterator {
    @Test
    public void testEntrySetIterator() {
        Map<String, String> map = Map.of("a", "1", "b", "2", "c", "3");
        Set<Map.Entry<String, String>> entrySet = map.entrySet();
        Iterator<Map.Entry<String, String>> iterator = entrySet.iterator();

        assertTrue(iterator.hasNext());

        // copying implicitly iterates an iterator
        Set<Map.Entry<String, String>> copy1 = new HashSet<>(entrySet);
        Set<Map.Entry<String, String>> copy2 = new HashSet<>(entrySet);

        assertEquals(copy2, copy1);
        assertTrue(iterator.hasNext());
    }
}