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
package android.util.cts;

import static com.google.common.truth.Truth.assertWithMessage;

import android.util.Dumpable;

import org.junit.Test;

import java.io.PrintWriter;

public final class DumpableTest {

    @Test
    public void testGetDumpableName() {
        Dumpable dumpable = new BondJamesBond();
        assertWithMessage("dumpable (%s) name", dumpable).that(dumpable.getDumpableName())
                .isEqualTo("android.util.cts.DumpableTest$BondJamesBond");
    }

    private static final class BondJamesBond implements Dumpable {

        @Override
        public void dump(PrintWriter writer, String[] args) {
            throw new UnsupportedOperationException("Shaken, not stirred");
        }
    }
}
