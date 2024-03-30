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
 * @bug     4952736
 * @summary Range static factory is broken in Regular and Jumbo enum set
 * @author  Josh Bloch
 */
package test.java.util.EnumSet;

import java.util.*;

import org.testng.Assert;
import org.testng.annotations.Test;

public class Range {

    @Test
    public void testRange() {
        test(Test33.class, Test33.T6, Test33.T2);
        test(Test127.class, Test127.T6, Test127.T2);
    }

    static <T extends Enum<T>> void test(Class<T> enumClass, T e0,T e1) {
        try {
            EnumSet<T> range = EnumSet.range(e0, e1);
            Assert.fail();
        } catch(IllegalArgumentException e) {
            return;
        }
        throw new RuntimeException(enumClass.toString());
    }

    public enum Test33 {
        T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16,
        T17, T18, T19, T20, T21, T22, T23, T24, T25, T26, T27, T28, T29, T30,
        T31, T32, T33
    }

    public enum Test127 {
        T0, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15,
        T16, T17, T18, T19, T20, T21, T22, T23, T24, T25, T26, T27, T28, T29,
        T30, T31, T32, T33, T34, T35, T36, T37, T38, T39, T40, T41, T42, T43,
        T44, T45, T46, T47, T48, T49, T50, T51, T52, T53, T54, T55, T56, T57,
        T58, T59, T60, T61, T62, T63, T64, T65, T66, T67, T68, T69, T70, T71,
        T72, T73, T74, T75, T76, T77, T78, T79, T80, T81, T82, T83, T84, T85,
        T86, T87, T88, T89, T90, T91, T92, T93, T94, T95, T96, T97, T98, T99,
        T100, T101, T102, T103, T104, T105, T106, T107, T108, T109, T110, T111,
        T112, T113, T114, T115, T116, T117, T118, T119, T120, T121, T122, T123,
        T124, T125, T126
    }
}