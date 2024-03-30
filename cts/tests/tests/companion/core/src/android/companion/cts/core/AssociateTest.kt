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

package android.companion.cts.core

import android.companion.AssociationRequest
import android.companion.cts.common.RecordingCallback
import android.companion.cts.common.RecordingCallback.OnAssociationPending
import android.companion.cts.common.SIMPLE_EXECUTOR
import android.platform.test.annotations.AppModeFull
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Test CDM APIs for requesting establishing new associations.
 *
 * Run: atest CtsCompanionDeviceManagerCoreTestCases:AssociateTest
 *
 * @see android.companion.CompanionDeviceManager.associate
 */
@AppModeFull(reason = "CompanionDeviceManager APIs are not available to the instant apps.")
@RunWith(AndroidJUnit4::class)
class AssociateTest : CoreTestBase() {

    @Test
    fun test_associate() {
        val request: AssociationRequest = AssociationRequest.Builder()
                .build()
        val callback = RecordingCallback()

        callback.assertInvokedByActions {
            cdm.associate(request, SIMPLE_EXECUTOR, callback)
        }
        // Check callback invocations: there should have been exactly 1 invocation of the
        // onAssociationPending() method.
        assertEquals(1, callback.invocations.size)
        assertIs<OnAssociationPending>(callback.invocations.first())
    }
}