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

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Resources
import android.graphics.drawable.Icon
import android.service.chooser.ChooserAction
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.android.intentresolver.flags.FeatureFlagRepository
import com.android.intentresolver.flags.Flags
import com.google.common.collect.ImmutableList
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.function.Consumer

@RunWith(AndroidJUnit4::class)
class ChooserActionFactoryTest {
    private val context = InstrumentationRegistry.getInstrumentation().getContext()

    private val logger = mock<ChooserActivityLogger>()
    private val flags = mock<FeatureFlagRepository>()
    private val actionLabel = "Action label"
    private val testAction = "com.android.intentresolver.testaction"
    private val countdown = CountDownLatch(1)
    private val testReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // Just doing at most a single countdown per test.
            countdown.countDown()
        }
    }
    private object resultConsumer : Consumer<Int> {
        var latestReturn = Integer.MIN_VALUE

        override fun accept(resultCode: Int) {
            latestReturn = resultCode
        }

    }

    @Before
    fun setup() {
        whenever(flags.isEnabled(Flags.SHARESHEET_RESELECTION_ACTION)).thenReturn(true)
        context.registerReceiver(testReceiver, IntentFilter(testAction))
    }

    @After
    fun teardown() {
        context.unregisterReceiver(testReceiver)
    }

    @Test
    fun testCreateCustomActions() {
        val factory = createFactory()

        val customActions = factory.createCustomActions()

        assertThat(customActions.size).isEqualTo(1)
        assertThat(customActions[0].label).isEqualTo(actionLabel)

        // click it
        customActions[0].onClicked.run()

        Mockito.verify(logger).logCustomActionSelected(eq(0))
        assertEquals(Activity.RESULT_OK, resultConsumer.latestReturn)
        // Verify the pendingintent has been called
        countdown.await(500, TimeUnit.MILLISECONDS)
    }

    @Test
    fun testNoModifyShareAction() {
        val factory = createFactory(includeModifyShare = false)

        assertThat(factory.modifyShareAction).isNull()
    }

    @Test
    fun testNoModifyShareAction_flagDisabled() {
        whenever(flags.isEnabled(Flags.SHARESHEET_RESELECTION_ACTION)).thenReturn(false)
        val factory = createFactory(includeModifyShare = true)

        assertThat(factory.modifyShareAction).isNull()
    }

    @Test
    fun testModifyShareAction() {
        val factory = createFactory(includeModifyShare = true)

        factory.modifyShareAction!!.run()

        Mockito.verify(logger).logActionSelected(
            eq(ChooserActivityLogger.SELECTION_TYPE_MODIFY_SHARE))
        assertEquals(Activity.RESULT_OK, resultConsumer.latestReturn)
        // Verify the pendingintent has been called
        countdown.await(500, TimeUnit.MILLISECONDS)
    }

    private fun createFactory(includeModifyShare: Boolean = false): ChooserActionFactory {
        val testPendingIntent = PendingIntent.getActivity(context, 0, Intent(testAction),0)
        val targetIntent = Intent()
        val action = ChooserAction.Builder(
            Icon.createWithResource("", Resources.ID_NULL),
            actionLabel,
            testPendingIntent
        ).build()
        val chooserRequest = mock<ChooserRequestParameters>()
        whenever(chooserRequest.targetIntent).thenReturn(targetIntent)
        whenever(chooserRequest.chooserActions).thenReturn(ImmutableList.of(action))

        if (includeModifyShare) {
            whenever(chooserRequest.modifyShareAction).thenReturn(testPendingIntent)
        }

        return ChooserActionFactory(
            context,
            chooserRequest,
            flags,
            mock<ChooserIntegratedDeviceComponents>(),
            logger,
            Consumer<Boolean>{},
            Callable<View?>{null},
            mock<ChooserActionFactory.ActionActivityStarter>(),
            resultConsumer)
    }
}