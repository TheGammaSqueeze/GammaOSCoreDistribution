/*
 * Copyright (c) 2000, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/*
 * @test
 * @bug 4245809
 * @summary Basic test for LinkedHashSet.  (Based on SetBash)
 */
package test.java.util.LinkedHashSet;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Random;
import java.util.Set;

import org.testng.Assert;
import org.testng.annotations.Test;

public class Basic {
    static Random rnd = new Random(666);

    @Test
    public void testBasic() {
        int numItr =  500;
        int setSize = 500;

        for (int i=0; i<numItr; i++) {
            Set s1 = new LinkedHashSet();
            AddRandoms(s1, setSize);

            Set s2 = new LinkedHashSet();
            AddRandoms(s2, setSize);

            Set intersection = clone(s1);
            intersection.retainAll(s2);
            Set diff1 = clone(s1); diff1.removeAll(s2);
            Set diff2 = clone(s2); diff2.removeAll(s1);
            Set union = clone(s1); union.addAll(s2);

            Assert.assertFalse(diff1.removeAll(diff2));
            Assert.assertFalse(diff1.removeAll(intersection));
            Assert.assertFalse(diff2.removeAll(diff1));
            Assert.assertFalse(diff2.removeAll(intersection));
            Assert.assertFalse(intersection.removeAll(diff1));
            Assert.assertFalse(intersection.removeAll(diff1));

            intersection.addAll(diff1); intersection.addAll(diff2);
            Assert.assertTrue(intersection.equals(union));

            Assert.assertEquals(new LinkedHashSet(union).hashCode(), union.hashCode());

            Iterator e = union.iterator();
            while (e.hasNext())
                Assert.assertTrue(intersection.remove(e.next()));
            Assert.assertTrue(intersection.isEmpty());

            e = union.iterator();
            while (e.hasNext()) {
                Object o = e.next();
                Assert.assertTrue(union.contains(o));
                e.remove();
                Assert.assertFalse(union.contains(o));
            }
            Assert.assertTrue(union.isEmpty());

            s1.clear();
            Assert.assertTrue(s1.isEmpty());
        }
    }

    static Set clone(Set s) {
        Set clone;
        int method = rnd.nextInt(3);
        clone = (method==0 ? (Set) ((LinkedHashSet)s).clone() :
                (method==1 ? new LinkedHashSet(Arrays.asList(s.toArray())) :
                        serClone(s)));
        Assert.assertTrue(s.equals(clone));
        Assert.assertTrue(s.containsAll(clone));
        Assert.assertTrue(clone.containsAll(s));
        return clone;
    }

    private static Set serClone(Set m) {
        Set result = null;
        try {
            // Serialize
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ObjectOutputStream out = new ObjectOutputStream(bos);
            out.writeObject(m);
            out.flush();

            // Deserialize
            ByteArrayInputStream bis = new ByteArrayInputStream(bos.toByteArray());
            out.close();
            ObjectInputStream in = new ObjectInputStream(bis);
            result = (Set)in.readObject();
            in.close();
        } catch (Exception e) {
            Assert.fail();
        }
        return result;
    }

    static void AddRandoms(Set s, int n) {
        for (int i = 0; i < n; i++) {
            Integer e = rnd.nextInt(n);

            int preSize = s.size();
            boolean prePresent = s.contains(e);
            boolean added = s.add(e);
            Assert.assertTrue(s.contains(e));
            Assert.assertFalse(added == prePresent);
            int postSize = s.size();
            Assert.assertFalse(added && preSize == postSize);
            Assert.assertFalse(!added && preSize != postSize);
        }
    }
}