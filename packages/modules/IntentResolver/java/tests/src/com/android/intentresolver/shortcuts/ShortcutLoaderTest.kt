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

package com.android.intentresolver.shortcuts

import android.app.prediction.AppPredictor
import android.content.ComponentName
import android.content.Context
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.PackageManager.ApplicationInfoFlags
import android.content.pm.ShortcutManager
import android.os.UserHandle
import android.os.UserManager
import androidx.test.filters.SmallTest
import com.android.intentresolver.any
import com.android.intentresolver.argumentCaptor
import com.android.intentresolver.capture
import com.android.intentresolver.chooser.DisplayResolveInfo
import com.android.intentresolver.createAppTarget
import com.android.intentresolver.createShareShortcutInfo
import com.android.intentresolver.createShortcutInfo
import com.android.intentresolver.mock
import com.android.intentresolver.whenever
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.anyInt
import org.mockito.Mockito.atLeastOnce
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import java.util.concurrent.Executor
import java.util.function.Consumer

@SmallTest
class ShortcutLoaderTest {
    private val appInfo = ApplicationInfo().apply {
        enabled = true
        flags = 0
    }
    private val pm = mock<PackageManager> {
        whenever(getApplicationInfo(any(), any<ApplicationInfoFlags>())).thenReturn(appInfo)
    }
    val userManager = mock<UserManager> {
        whenever(isUserRunning(any<UserHandle>())).thenReturn(true)
        whenever(isUserUnlocked(any<UserHandle>())).thenReturn(true)
        whenever(isQuietModeEnabled(any<UserHandle>())).thenReturn(false)
    }
    private val context = mock<Context> {
        whenever(packageManager).thenReturn(pm)
        whenever(createContextAsUser(any(), anyInt())).thenReturn(this)
        whenever(getSystemService(Context.USER_SERVICE)).thenReturn(userManager)
    }
    private val executor = ImmediateExecutor()
    private val intentFilter = mock<IntentFilter>()
    private val appPredictor = mock<ShortcutLoader.AppPredictorProxy>()
    private val callback = mock<Consumer<ShortcutLoader.Result>>()

    @Test
    fun test_queryShortcuts_result_consistency_with_AppPredictor() {
        val componentName = ComponentName("pkg", "Class")
        val appTarget = mock<DisplayResolveInfo> {
            whenever(resolvedComponentName).thenReturn(componentName)
        }
        val appTargets = arrayOf(appTarget)
        val testSubject = ShortcutLoader(
            context,
            appPredictor,
            UserHandle.of(0),
            true,
            intentFilter,
            executor,
            executor,
            callback
        )

        testSubject.queryShortcuts(appTargets)

        val matchingShortcutInfo = createShortcutInfo("id-0", componentName, 1)
        val matchingAppTarget = createAppTarget(matchingShortcutInfo)
        val shortcuts = listOf(
            matchingAppTarget,
            // an AppTarget that does not belong to any resolved application; should be ignored
            createAppTarget(
                createShortcutInfo("id-1", ComponentName("mismatching.pkg", "Class"), 1)
            )
        )
        val appPredictorCallbackCaptor = argumentCaptor<AppPredictor.Callback>()
        verify(appPredictor, atLeastOnce())
            .registerPredictionUpdates(any(), capture(appPredictorCallbackCaptor))
        appPredictorCallbackCaptor.value.onTargetsAvailable(shortcuts)

        val resultCaptor = argumentCaptor<ShortcutLoader.Result>()
        verify(callback, times(1)).accept(capture(resultCaptor))

        val result = resultCaptor.value
        assertTrue("An app predictor result is expected", result.isFromAppPredictor)
        assertArrayEquals("Wrong input app targets in the result", appTargets, result.appTargets)
        assertEquals("Wrong shortcut count", 1, result.shortcutsByApp.size)
        assertEquals("Wrong app target", appTarget, result.shortcutsByApp[0].appTarget)
        for (shortcut in result.shortcutsByApp[0].shortcuts) {
            assertEquals(
                "Wrong AppTarget in the cache",
                matchingAppTarget,
                result.directShareAppTargetCache[shortcut]
            )
            assertEquals(
                "Wrong ShortcutInfo in the cache",
                matchingShortcutInfo,
                result.directShareShortcutInfoCache[shortcut]
            )
        }
    }

    @Test
    fun test_queryShortcuts_result_consistency_with_ShortcutManager() {
        val componentName = ComponentName("pkg", "Class")
        val appTarget = mock<DisplayResolveInfo> {
            whenever(resolvedComponentName).thenReturn(componentName)
        }
        val appTargets = arrayOf(appTarget)
        val matchingShortcutInfo = createShortcutInfo("id-0", componentName, 1)
        val shortcutManagerResult = listOf(
            ShortcutManager.ShareShortcutInfo(matchingShortcutInfo, componentName),
            // mismatching shortcut
            createShareShortcutInfo("id-1", ComponentName("mismatching.pkg", "Class"), 1)
        )
        val shortcutManager = mock<ShortcutManager> {
            whenever(getShareTargets(intentFilter)).thenReturn(shortcutManagerResult)
        }
        whenever(context.getSystemService(Context.SHORTCUT_SERVICE)).thenReturn(shortcutManager)
        val testSubject = ShortcutLoader(
            context,
            null,
            UserHandle.of(0),
            true,
            intentFilter,
            executor,
            executor,
            callback
        )

        testSubject.queryShortcuts(appTargets)

        val resultCaptor = argumentCaptor<ShortcutLoader.Result>()
        verify(callback, times(1)).accept(capture(resultCaptor))

        val result = resultCaptor.value
        assertFalse("An ShortcutManager result is expected", result.isFromAppPredictor)
        assertArrayEquals("Wrong input app targets in the result", appTargets, result.appTargets)
        assertEquals("Wrong shortcut count", 1, result.shortcutsByApp.size)
        assertEquals("Wrong app target", appTarget, result.shortcutsByApp[0].appTarget)
        for (shortcut in result.shortcutsByApp[0].shortcuts) {
            assertTrue(
                "AppTargets are not expected the cache of a ShortcutManager result",
                result.directShareAppTargetCache.isEmpty()
            )
            assertEquals(
                "Wrong ShortcutInfo in the cache",
                matchingShortcutInfo,
                result.directShareShortcutInfoCache[shortcut]
            )
        }
    }

    @Test
    fun test_queryShortcuts_falls_back_to_ShortcutManager_on_empty_reply() {
        val componentName = ComponentName("pkg", "Class")
        val appTarget = mock<DisplayResolveInfo> {
            whenever(resolvedComponentName).thenReturn(componentName)
        }
        val appTargets = arrayOf(appTarget)
        val matchingShortcutInfo = createShortcutInfo("id-0", componentName, 1)
        val shortcutManagerResult = listOf(
            ShortcutManager.ShareShortcutInfo(matchingShortcutInfo, componentName),
            // mismatching shortcut
            createShareShortcutInfo("id-1", ComponentName("mismatching.pkg", "Class"), 1)
        )
        val shortcutManager = mock<ShortcutManager> {
            whenever(getShareTargets(intentFilter)).thenReturn(shortcutManagerResult)
        }
        whenever(context.getSystemService(Context.SHORTCUT_SERVICE)).thenReturn(shortcutManager)
        val testSubject = ShortcutLoader(
            context,
            appPredictor,
            UserHandle.of(0),
            true,
            intentFilter,
            executor,
            executor,
            callback
        )

        testSubject.queryShortcuts(appTargets)

        verify(appPredictor, times(1)).requestPredictionUpdate()
        val appPredictorCallbackCaptor = argumentCaptor<AppPredictor.Callback>()
        verify(appPredictor, times(1))
            .registerPredictionUpdates(any(), capture(appPredictorCallbackCaptor))
        appPredictorCallbackCaptor.value.onTargetsAvailable(emptyList())

        val resultCaptor = argumentCaptor<ShortcutLoader.Result>()
        verify(callback, times(1)).accept(capture(resultCaptor))

        val result = resultCaptor.value
        assertFalse("An ShortcutManager result is expected", result.isFromAppPredictor)
        assertArrayEquals("Wrong input app targets in the result", appTargets, result.appTargets)
        assertEquals("Wrong shortcut count", 1, result.shortcutsByApp.size)
        assertEquals("Wrong app target", appTarget, result.shortcutsByApp[0].appTarget)
        for (shortcut in result.shortcutsByApp[0].shortcuts) {
            assertTrue(
                "AppTargets are not expected the cache of a ShortcutManager result",
                result.directShareAppTargetCache.isEmpty()
            )
            assertEquals(
                "Wrong ShortcutInfo in the cache",
                matchingShortcutInfo,
                result.directShareShortcutInfoCache[shortcut]
            )
        }
    }

    @Test
    fun test_queryShortcuts_do_not_call_services_for_not_running_work_profile() {
        testDisabledWorkProfileDoNotCallSystem(isUserRunning = false)
    }

    @Test
    fun test_queryShortcuts_do_not_call_services_for_locked_work_profile() {
        testDisabledWorkProfileDoNotCallSystem(isUserUnlocked = false)
    }

    @Test
    fun test_queryShortcuts_do_not_call_services_if_quite_mode_is_enabled_for_work_profile() {
        testDisabledWorkProfileDoNotCallSystem(isQuietModeEnabled = true)
    }

    @Test
    fun test_queryShortcuts_call_services_for_not_running_main_profile() {
        testAlwaysCallSystemForMainProfile(isUserRunning = false)
    }

    @Test
    fun test_queryShortcuts_call_services_for_locked_main_profile() {
        testAlwaysCallSystemForMainProfile(isUserUnlocked = false)
    }

    @Test
    fun test_queryShortcuts_call_services_if_quite_mode_is_enabled_for_main_profile() {
        testAlwaysCallSystemForMainProfile(isQuietModeEnabled = true)
    }

    private fun testDisabledWorkProfileDoNotCallSystem(
        isUserRunning: Boolean = true,
        isUserUnlocked: Boolean = true,
        isQuietModeEnabled: Boolean = false
    ) {
        val userHandle = UserHandle.of(10)
        with(userManager) {
            whenever(isUserRunning(userHandle)).thenReturn(isUserRunning)
            whenever(isUserUnlocked(userHandle)).thenReturn(isUserUnlocked)
            whenever(isQuietModeEnabled(userHandle)).thenReturn(isQuietModeEnabled)
        }
        whenever(context.getSystemService(Context.USER_SERVICE)).thenReturn(userManager);
        val appPredictor = mock<ShortcutLoader.AppPredictorProxy>()
        val callback = mock<Consumer<ShortcutLoader.Result>>()
        val testSubject = ShortcutLoader(
            context,
            appPredictor,
            userHandle,
            false,
            intentFilter,
            executor,
            executor,
            callback
        )

        testSubject.queryShortcuts(arrayOf<DisplayResolveInfo>(mock()))

        verify(appPredictor, never()).requestPredictionUpdate()
    }

    private fun testAlwaysCallSystemForMainProfile(
        isUserRunning: Boolean = true,
        isUserUnlocked: Boolean = true,
        isQuietModeEnabled: Boolean = false
    ) {
        val userHandle = UserHandle.of(10)
        with(userManager) {
            whenever(isUserRunning(userHandle)).thenReturn(isUserRunning)
            whenever(isUserUnlocked(userHandle)).thenReturn(isUserUnlocked)
            whenever(isQuietModeEnabled(userHandle)).thenReturn(isQuietModeEnabled)
        }
        whenever(context.getSystemService(Context.USER_SERVICE)).thenReturn(userManager);
        val appPredictor = mock<ShortcutLoader.AppPredictorProxy>()
        val callback = mock<Consumer<ShortcutLoader.Result>>()
        val testSubject = ShortcutLoader(
            context,
            appPredictor,
            userHandle,
            true,
            intentFilter,
            executor,
            executor,
            callback
        )

        testSubject.queryShortcuts(arrayOf<DisplayResolveInfo>(mock()))

        verify(appPredictor, times(1)).requestPredictionUpdate()
    }
}

private class ImmediateExecutor : Executor {
    override fun execute(r: Runnable) {
        r.run()
    }
}
