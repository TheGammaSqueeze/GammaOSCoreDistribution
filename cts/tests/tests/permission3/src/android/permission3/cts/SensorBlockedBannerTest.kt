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

import android.content.Intent
import android.hardware.SensorPrivacyManager
import android.hardware.SensorPrivacyManager.Sensors.CAMERA
import android.hardware.SensorPrivacyManager.Sensors.MICROPHONE
import android.location.LocationManager
import android.Manifest.permission_group.CAMERA as CAMERA_PERMISSION_GROUP
import android.Manifest.permission_group.LOCATION as LOCATION_PERMISSION_GROUP
import android.Manifest.permission_group.MICROPHONE as MICROPHONE_PERMISSION_GROUP
import android.os.Build
import android.provider.DeviceConfig
import android.support.test.uiautomator.By
import androidx.test.filters.SdkSuppress
import com.android.compatibility.common.util.SystemUtil.callWithShellPermissionIdentity
import com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity
import java.util.regex.Pattern
import org.junit.After
import org.junit.Assume
import org.junit.Before
import org.junit.Test

/**
 * Banner card display tests on sensors being blocked
 */
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.S)
class SensorBlockedBannerTest : BaseUsePermissionTest() {
    companion object {
        const val LOCATION = -1
        const val WARNING_BANNER_ENABLED = "warning_banner_enabled"
        const val DELAY_MILLIS = 3000L
    }

    val sensorPrivacyManager = context.getSystemService(SensorPrivacyManager::class.java)!!
    val locationManager = context.getSystemService(LocationManager::class.java)!!
    private val originalEnabledValue = callWithShellPermissionIdentity {
        DeviceConfig.getString(DeviceConfig.NAMESPACE_PRIVACY,
                WARNING_BANNER_ENABLED, false.toString())
    }

    private val sensorToPermissionGroup = mapOf(CAMERA to CAMERA_PERMISSION_GROUP,
            MICROPHONE to MICROPHONE_PERMISSION_GROUP,
            LOCATION to LOCATION_PERMISSION_GROUP)

    private val permToTitle = mapOf(CAMERA to "blocked_camera_title",
            MICROPHONE to "blocked_microphone_title",
            LOCATION to "blocked_location_title")

    @Before
    fun setup() {
        Assume.assumeFalse(isTv)
        Assume.assumeFalse(isWatch)
        // TODO(b/203784852) Auto will eventually support the blocked sensor banner, but there won't
        // be support in T or below
        Assume.assumeFalse(isAutomotive)
        installPackage(APP_APK_PATH_31)
        runWithShellPermissionIdentity {
            DeviceConfig.setProperty(DeviceConfig.NAMESPACE_PRIVACY,
                WARNING_BANNER_ENABLED, true.toString(), false)
        }
    }

    @After
    fun restoreWarningBannerState() {
        runWithShellPermissionIdentity {
            DeviceConfig.setProperty(DeviceConfig.NAMESPACE_PRIVACY,
                    WARNING_BANNER_ENABLED, originalEnabledValue, false)
        }
    }

    private fun navigateAndTest(sensor: Int) {
        val permissionGroup = sensorToPermissionGroup.getOrDefault(sensor, "Break")
        val intent = Intent(Intent.ACTION_MANAGE_PERMISSION_APPS)
                .putExtra(Intent.EXTRA_PERMISSION_GROUP_NAME, permissionGroup)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runWithShellPermissionIdentity {
            context.startActivity(intent)
        }
        val bannerTitle = permToTitle.getOrDefault(sensor, "Break")
        waitFindObject(By.text(getPermissionControllerString(bannerTitle)))
        pressBack()
    }

    private fun runSensorTest(sensor: Int) {
        val blocked = isSensorPrivacyEnabled(sensor)
        if (!blocked) {
            setSensor(sensor, true)
        }
        navigateAndTest(sensor)
        if (!blocked) {
            setSensor(sensor, false)
        }
    }

    @Test
    fun testCameraCardDisplayed() {
        Assume.assumeTrue(sensorPrivacyManager.supportsSensorToggle(CAMERA))
        runSensorTest(CAMERA)
    }

    @Test
    fun testMicCardDisplayed() {
        Assume.assumeTrue(sensorPrivacyManager.supportsSensorToggle(MICROPHONE))
        runSensorTest(MICROPHONE)
    }

    @Test
    fun testLocationCardDisplayed() {
        runSensorTest(LOCATION)
    }

    private fun setSensor(sensor: Int, enable: Boolean) {
        if (sensor == LOCATION) {
            runWithShellPermissionIdentity {
                locationManager.setLocationEnabledForUser(!enable,
                    android.os.Process.myUserHandle())
                if (enable) {
                    try {
                        val closePattern = Pattern.compile("close", Pattern.CASE_INSENSITIVE)
                        waitFindObjectOrNull(By.text(closePattern), DELAY_MILLIS)?.click()
                    } catch (e: Exception) {
                        // Do nothing, warning didn't show up so test can proceed
                    }
                }
            }
        } else {
            runWithShellPermissionIdentity {
                sensorPrivacyManager.setSensorPrivacy(SensorPrivacyManager.Sources.OTHER,
                    sensor, enable)
            }
        }
    }

    private fun isSensorPrivacyEnabled(sensor: Int): Boolean {
        return if (sensor == LOCATION) {
            callWithShellPermissionIdentity {
                !locationManager.isLocationEnabled()
            }
        } else {
            callWithShellPermissionIdentity {
                sensorPrivacyManager.isSensorPrivacyEnabled(sensor)
            }
        }
    }
}
