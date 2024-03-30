/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.sts.common;

import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.io.InputStream;

/** Unit tests for {@link MallocDebug}. */
@RunWith(DeviceJUnit4ClassRunner.class)
public class MallocDebugTest extends BaseHostJUnit4Test {
    private static String logcatWithErrors = null;
    private static String logcatWithoutErrors = null;

    @BeforeClass
    public static void setUpClass() throws IOException {
        try (InputStream is1 =
                        MallocDebugTest.class
                                .getClassLoader()
                                .getResourceAsStream("malloc_debug_logcat.txt");
                InputStream is2 =
                        MallocDebugTest.class.getClassLoader().getResourceAsStream("logcat.txt")) {
            logcatWithErrors = new String(is1.readAllBytes());
            logcatWithoutErrors = new String(is2.readAllBytes());
        }
    }

    @Test(expected = Test.None.class /* no exception expected */)
    public void testMallocDebugNoErrors() throws Exception {
        MallocDebug.assertNoMallocDebugErrors(logcatWithoutErrors);
    }

    @Test(expected = AssertionError.class)
    public void testMallocDebugWithErrors() throws Exception {
        MallocDebug.assertNoMallocDebugErrors(logcatWithErrors);
    }
}
