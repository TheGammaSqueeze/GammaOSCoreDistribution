/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.intentresolver

import android.content.Context
import android.content.Intent
import android.content.IntentSender
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.intentresolver.chooser.TargetInfo
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import java.util.function.Consumer
import org.junit.Assert.assertEquals

@RunWith(AndroidJUnit4::class)
class ChooserRefinementManagerTest {
    @Test
    fun testMaybeHandleSelection() {
        val intentSender = mock<IntentSender>()
        val refinementManager = ChooserRefinementManager(
            mock<Context>(),
            intentSender,
            Consumer<TargetInfo>{},
            Runnable{})

        val intents = listOf(Intent(Intent.ACTION_VIEW), Intent(Intent.ACTION_EDIT))
        val targetInfo = mock<TargetInfo>{
            whenever(allSourceIntents).thenReturn(intents)
        }

        refinementManager.maybeHandleSelection(targetInfo)

        val intentCaptor = ArgumentCaptor.forClass(Intent::class.java)
        Mockito.verify(intentSender).sendIntent(
            any(), eq(0), intentCaptor.capture(), eq(null), eq(null))

        val intent = intentCaptor.value
        assertEquals(intents[0], intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java))

        val alternates =
            intent.getParcelableArrayExtra(Intent.EXTRA_ALTERNATE_INTENTS, Intent::class.java)
        assertEquals(1, alternates?.size)
        assertEquals(intents[1], alternates?.get(0))
    }
}
