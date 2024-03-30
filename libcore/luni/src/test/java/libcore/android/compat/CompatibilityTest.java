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

package libcore.android.compat;

import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import android.compat.Compatibility;
import android.compat.Compatibility.BehaviorChangeDelegate;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class CompatibilityTest {

    @Test
    public void testBehaviorChangeDelegate() {
        long changeId = 1L;
        BehaviorChangeDelegate defaultDelegate = new BehaviorChangeDelegate() {};

        // default implementation should not throw exception
        defaultDelegate.onChangeReported(changeId);
        assertTrue(defaultDelegate.isChangeEnabled(changeId));
    }

    @Test
    public void testReportUnconditionalChange() {
        long changeId = 1L;
        BehaviorChangeDelegate delegate = mock(BehaviorChangeDelegate.class);
        Compatibility.setBehaviorChangeDelegate(delegate);

        Compatibility.reportUnconditionalChange(changeId);
        verify(delegate).onChangeReported(changeId);
    }

    @Test
    public void testClearBehaviorChangeDelegate() {
        long changeId = 1L;
        BehaviorChangeDelegate delegate = mock(BehaviorChangeDelegate.class);
        Compatibility.setBehaviorChangeDelegate(delegate);

        Compatibility.clearBehaviorChangeDelegate();
        Compatibility.reportUnconditionalChange(changeId);
        verify(delegate, never()).onChangeReported(anyLong());
    }
}
