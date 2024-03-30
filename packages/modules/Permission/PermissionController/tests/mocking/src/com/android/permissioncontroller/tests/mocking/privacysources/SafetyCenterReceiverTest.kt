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

package com.android.permissioncontroller.tests.mocking.privacysources

import android.content.Intent
import android.content.Intent.ACTION_BOOT_COMPLETED
import android.os.Build
import android.safetycenter.SafetyCenterManager
import android.safetycenter.SafetyCenterManager.ACTION_REFRESH_SAFETY_SOURCES
import android.safetycenter.SafetyCenterManager.ACTION_SAFETY_CENTER_ENABLED_CHANGED
import android.safetycenter.SafetyCenterManager.EXTRA_REFRESH_SAFETY_SOURCE_IDS
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.android.dx.mockito.inline.extended.ExtendedMockito
import com.android.permissioncontroller.PermissionControllerApplication
import com.android.permissioncontroller.privacysources.PrivacySource
import com.android.permissioncontroller.privacysources.SafetyCenterReceiver
import com.android.permissioncontroller.privacysources.SafetyCenterReceiver.RefreshEvent.EVENT_DEVICE_REBOOTED
import com.android.permissioncontroller.privacysources.SafetyCenterReceiver.RefreshEvent.EVENT_REFRESH_REQUESTED
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runBlockingTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.`when` as whenever
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyZeroInteractions
import org.mockito.MockitoAnnotations
import org.mockito.MockitoSession
import org.mockito.quality.Strictness

/**
 * Unit tests for [SafetyCenterReceiver]
 */
@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.TIRAMISU, codeName = "Tiramisu")
class SafetyCenterReceiverTest {

    companion object {
        private const val TEST_PRIVACY_SOURCE_ID = "test_privacy_source_id"
        private const val TEST_PRIVACY_SOURCE_ID_2 = "test_privacy_source_id_2"

        val application = Mockito.mock(PermissionControllerApplication::class.java)
    }

    private val testCoroutineDispatcher: TestCoroutineDispatcher = TestCoroutineDispatcher()

    @Mock
    lateinit var mockSafetyCenterManager: SafetyCenterManager
    @Mock
    lateinit var mockPrivacySource: PrivacySource
    @Mock
    lateinit var mockPrivacySource2: PrivacySource

    private lateinit var mockitoSession: MockitoSession
    private lateinit var safetyCenterReceiver: SafetyCenterReceiver

    private fun privacySourceMap() = mapOf(
        TEST_PRIVACY_SOURCE_ID to mockPrivacySource,
        TEST_PRIVACY_SOURCE_ID_2 to mockPrivacySource2
    )

    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)

        mockitoSession = ExtendedMockito.mockitoSession()
            .mockStatic(PermissionControllerApplication::class.java)
            .strictness(Strictness.LENIENT).startMocking()

        whenever(PermissionControllerApplication.get()).thenReturn(application)
        whenever(application.applicationContext).thenReturn(application)
        whenever(application.getSystemService(SafetyCenterManager::class.java))
            .thenReturn(mockSafetyCenterManager)
        whenever(mockSafetyCenterManager.isSafetyCenterEnabled).thenReturn(true)

        safetyCenterReceiver = SafetyCenterReceiver(::privacySourceMap, testCoroutineDispatcher)

        Dispatchers.setMain(testCoroutineDispatcher)
    }

    @After
    fun cleanup() {
        Dispatchers.resetMain()
        testCoroutineDispatcher.cleanupTestCoroutines()

        mockitoSession.finishMocking()
    }

    @Test
    fun onReceive_actionSafetyCenterEnabledChanged() = runBlockingTest {
        safetyCenterReceiver.onReceive(application, Intent(ACTION_SAFETY_CENTER_ENABLED_CHANGED))

        verify(mockPrivacySource).safetyCenterEnabledChanged(true)
        verify(mockPrivacySource2).safetyCenterEnabledChanged(true)
    }

    @Test
    fun onReceive_actionSafetyCenterEnabledChanged_safetyCenterDisabled() = runBlockingTest {
        whenever(mockSafetyCenterManager.isSafetyCenterEnabled).thenReturn(false)

        safetyCenterReceiver.onReceive(application, Intent(ACTION_SAFETY_CENTER_ENABLED_CHANGED))
        advanceUntilIdle()

        verify(mockPrivacySource).safetyCenterEnabledChanged(false)
        verify(mockPrivacySource2).safetyCenterEnabledChanged(false)
    }

    @Test
    fun onReceive_actionBootCompleted() = runBlockingTest {
        val intent = Intent(ACTION_BOOT_COMPLETED)

        safetyCenterReceiver.onReceive(application, intent)
        advanceUntilIdle()

        verify(mockPrivacySource)
            .rescanAndPushSafetyCenterData(application, intent, EVENT_DEVICE_REBOOTED)
        verify(mockPrivacySource2)
            .rescanAndPushSafetyCenterData(application, intent, EVENT_DEVICE_REBOOTED)
    }

    @Test
    fun onReceive_actionBootCompleted_safetyCenterDisabled() = runBlockingTest {
        whenever(mockSafetyCenterManager.isSafetyCenterEnabled).thenReturn(false)
        val intent = Intent(ACTION_BOOT_COMPLETED)

        safetyCenterReceiver.onReceive(application, intent)
        advanceUntilIdle()

        verifyZeroInteractions(mockPrivacySource)
        verifyZeroInteractions(mockPrivacySource2)
    }

    @Test
    fun onReceive_actionRefreshSafetySources() = runBlockingTest {
        val intent = Intent(ACTION_REFRESH_SAFETY_SOURCES)
        intent.putExtra(EXTRA_REFRESH_SAFETY_SOURCE_IDS, arrayOf(TEST_PRIVACY_SOURCE_ID))

        safetyCenterReceiver.onReceive(application, intent)
        advanceUntilIdle()

        verify(mockPrivacySource)
            .rescanAndPushSafetyCenterData(application, intent, EVENT_REFRESH_REQUESTED)
        verifyZeroInteractions(mockPrivacySource2)
    }

    @Test
    fun onReceive_actionRefreshSafetySources_noSourcesSpecified() = runBlockingTest {
        val intent = Intent(ACTION_REFRESH_SAFETY_SOURCES)

        safetyCenterReceiver.onReceive(application, intent)
        advanceUntilIdle()

        verifyZeroInteractions(mockPrivacySource)
        verifyZeroInteractions(mockPrivacySource2)
    }

    @Test
    fun onReceive_actionRefreshSafetySources_safetyCenterDisabled() = runBlockingTest {
        whenever(mockSafetyCenterManager.isSafetyCenterEnabled).thenReturn(false)
        val intent = Intent(ACTION_REFRESH_SAFETY_SOURCES)

        safetyCenterReceiver.onReceive(application, intent)
        advanceUntilIdle()

        verifyZeroInteractions(mockPrivacySource)
        verifyZeroInteractions(mockPrivacySource2)
    }
}
