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
 * @bug 7014637
 * @summary EnumSet's iterator.remove() can be resilient to set's modification.
 * @author Neil Richards <neil.richards@ngmr.net>, <neil_richards@uk.ibm.com>
 */
package test.java.util.EnumSet;

import java.util.EnumSet;
import java.util.Iterator;
import java.util.Set;

import org.testng.Assert;
import org.testng.annotations.Test;

public class SmallEnumIteratorRemoveResilience {
    // enum with less than 64 values
    private enum SmallEnum { e0, e1, e2 }

    @Test
    public void testSmallEnumRemoveResilience() {
        final Set<SmallEnum> set = EnumSet.noneOf(SmallEnum.class);

        set.add(SmallEnum.e0);
        set.add(SmallEnum.e1);

        final Iterator<SmallEnum> iterator = set.iterator();

        int size = set.size();
        SmallEnum element = iterator.next();

        iterator.remove();
        checkSetAfterRemoval(set, size, element);

        size = set.size();
        element = iterator.next();

        set.remove(element);
        checkSetAfterRemoval(set, size, element);

        // The Java API declares that the behaviour here - to call
        // iterator.remove() after the underlying collection has been
        // modified - is "unspecified".
        // However, in the case of iterators for EnumSet, it is easy to
        // implement their remove() operation such that the set is
        // unmodified if it is called for an element that has already been
        // removed from the set - this being the naturally "resilient"
        // behaviour.
        iterator.remove();
        checkSetAfterRemoval(set, size, element);
    }

    private static void checkSetAfterRemoval(final Set<SmallEnum> set,
            final int origSize, final SmallEnum removedElement) {
        Assert.assertEquals(set.size(), (origSize - 1));
        Assert.assertFalse(set.contains(removedElement));
    }
}