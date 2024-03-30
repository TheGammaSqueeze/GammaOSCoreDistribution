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

package android.permission3.cts

import android.app.Activity
import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Intent
import android.location.LocationManager
import android.os.Build
import android.provider.DeviceConfig
import android.support.test.uiautomator.By
import androidx.test.filters.SdkSuppress
import com.android.compatibility.common.util.AppOpsUtils.setOpMode
import com.android.compatibility.common.util.CtsDownstreamingTest
import com.android.compatibility.common.util.SystemUtil.callWithShellPermissionIdentity
import com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * Tests permission attribution for location providers.
 */
// Tests converted to GTS since these are GMS requirements not CDD.
// These will be moved to GTS in U.
@CtsDownstreamingTest
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.TIRAMISU)
class PermissionAttributionTest : BasePermissionHubTest() {
    private val micLabel = packageManager.getPermissionGroupInfo(
        android.Manifest.permission_group.MICROPHONE, 0).loadLabel(packageManager).toString()
    val locationManager = context.getSystemService(LocationManager::class.java)!!
    private var wasEnabled = false

    @Before
    fun installAppLocationProviderAndAllowMockLocation() {
        installPackage(APP_APK_PATH, grantRuntimePermissions = true)
        // The package name of a mock location provider is the caller adding it, so we have to let
        // the test app add itself.
        setOpMode(APP_PACKAGE_NAME, AppOpsManager.OPSTR_MOCK_LOCATION, AppOpsManager.MODE_ALLOWED)
    }

    @Before
    fun setup() {
        // Allow ourselves to reliably remove the test location provider.
        setOpMode(
            context.packageName, AppOpsManager.OPSTR_MOCK_LOCATION, AppOpsManager.MODE_ALLOWED
        )
        wasEnabled = setSubattributionEnabledStateIfNeeded(true)
    }

    @After
    fun teardown() {
        locationManager.removeTestProvider(APP_PACKAGE_NAME)
        if (!wasEnabled) {
            setSubattributionEnabledStateIfNeeded(false)
        }
    }

    @Test
    fun testLocationProviderAttributionForMicrophone() {
        enableAppAsLocationProvider()
        useMicrophone()
        openMicrophoneTimeline()

        waitFindObject(By.textContains(micLabel))
        waitFindObject(By.textContains(APP_LABEL))
        waitFindObject(By.textContains(ATTRIBUTION_LABEL))
    }

    private fun enableAppAsLocationProvider() {
        // Add the test app as location provider.
        val future = startActivityForFuture(
            Intent().apply {
                component = ComponentName(
                    APP_PACKAGE_NAME, "$APP_PACKAGE_NAME.AddLocationProviderActivity"
                )
            }
        )
        val result = future.get(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
        assertEquals(Activity.RESULT_OK, result.resultCode)
        assertTrue(
            callWithShellPermissionIdentity {
                locationManager.isProviderPackage(APP_PACKAGE_NAME)
            }
        )
    }

    private fun useMicrophone() {
        val future = startActivityForFuture(
            Intent().apply {
                component = ComponentName(
                    APP_PACKAGE_NAME, "$APP_PACKAGE_NAME.UseMicrophoneActivity"
                )
            }
        )
        val result = future.get(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
        assertEquals(Activity.RESULT_OK, result.resultCode)
    }

    private fun setSubattributionEnabledStateIfNeeded(shouldBeEnabled: Boolean): Boolean {
        var currentlyEnabled = false
        runWithShellPermissionIdentity {
            currentlyEnabled = DeviceConfig.getBoolean(DeviceConfig.NAMESPACE_PRIVACY,
                FLAG_SUBATTRIBUTION, false)
            if (currentlyEnabled != shouldBeEnabled) {
                DeviceConfig.setProperty(DeviceConfig.NAMESPACE_PRIVACY, FLAG_SUBATTRIBUTION,
                    shouldBeEnabled.toString(), false)
            }
        }
        return currentlyEnabled
    }

    companion object {
        const val APP_APK_PATH = "$APK_DIRECTORY/CtsAccessMicrophoneAppLocationProvider.apk"
        const val APP_PACKAGE_NAME = "android.permission3.cts.accessmicrophoneapplocationprovider"
        const val APP_LABEL = "LocationProviderWithMicApp"
        const val ATTRIBUTION_LABEL = "Attribution Label"
        const val FLAG_SUBATTRIBUTION = "permissions_hub_subattribution_enabled"
    }
}
