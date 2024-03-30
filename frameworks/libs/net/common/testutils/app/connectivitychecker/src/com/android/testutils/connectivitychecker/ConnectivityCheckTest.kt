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

package com.android.testutils.connectivitychecker

import android.content.pm.PackageManager.FEATURE_TELEPHONY
import android.content.pm.PackageManager.FEATURE_WIFI
import android.telephony.TelephonyManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.android.testutils.ConnectUtil
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertTrue
import kotlin.test.fail

@RunWith(AndroidJUnit4::class)
class ConnectivityCheckTest {
    val context by lazy { InstrumentationRegistry.getInstrumentation().context }
    val pm by lazy { context.packageManager }

    @Test
    fun testCheckDeviceSetup() {
        checkWifiSetup()
        checkTelephonySetup()
    }

    private fun checkWifiSetup() {
        if (!pm.hasSystemFeature(FEATURE_WIFI)) return
        ConnectUtil(context).ensureWifiConnected()
    }

    private fun checkTelephonySetup() {
        if (!pm.hasSystemFeature(FEATURE_TELEPHONY)) return
        val tm = context.getSystemService(TelephonyManager::class.java)
                ?: fail("Could not get telephony service")

        val commonError = "Check the test bench. To run the tests anyway for quick & dirty local " +
                "testing, you can use atest X -- " +
                "--test-arg com.android.testutils.ConnectivityCheckTargetPreparer:disable:true"
        // Do not use assertEquals: it outputs "expected X, was Y", which looks like a test failure
        if (tm.simState == TelephonyManager.SIM_STATE_ABSENT) {
            fail("The device has no SIM card inserted. " + commonError)
        } else if (tm.simState != TelephonyManager.SIM_STATE_READY) {
            fail("The device is not setup with a usable SIM card. Sim state was ${tm.simState}. " +
                    commonError)
        }
        assertTrue(tm.isDataConnectivityPossible,
            "The device is not setup with a SIM card that supports data connectivity. " +
                    commonError)
    }
}