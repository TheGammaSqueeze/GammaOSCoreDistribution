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

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.DeviceConfig
import android.provider.DeviceConfig.NAMESPACE_PRIVACY
import android.support.test.uiautomator.By
import androidx.test.filters.SdkSuppress
import com.android.compatibility.common.util.SystemUtil
import com.android.modules.utils.build.SdkLevel
import org.junit.After
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import java.util.regex.Pattern

private const val APP_LABEL_1 = "CtsMicAccess"
private const val APP_LABEL_2 = "CtsMicAccess2"
private const val INTENT_ACTION_1 = "test.action.USE_MIC"
private const val INTENT_ACTION_2 = "test.action.USE_MIC_2"
private const val PERMISSION_CONTROLLER_PACKAGE_ID_PREFIX = "com.android.permissioncontroller:id/"
private const val HISTORY_PREFERENCE_ICON = "permission_history_icon"
private const val HISTORY_PREFERENCE_TIME = "permission_history_time"
private const val SHOW_SYSTEM = "Show system"
private const val SHOW_7_DAYS = "Show 7 days"
private const val SHOW_24_HOURS = "Show 24 hours"
private const val MORE_OPTIONS = "More options"
private const val DASHBOARD_7_DAYS_DESCRIPTION_REGEX = "^.*7.*days$"
private const val DASHBOARD_TIME_DESCRIPTION_REGEX = "^[0-2]?[0-9]:[0-5][0-9].*"
private const val PRIV_DASH_7_DAY_ENABLED = "privacy_dashboard_7_day_toggle"
private const val REFRESH = "Refresh"

@SdkSuppress(minSdkVersion = Build.VERSION_CODES.S)
class PermissionHistoryTest : BasePermissionHubTest() {
    private val micLabel = packageManager.getPermissionGroupInfo(
            Manifest.permission_group.MICROPHONE, 0).loadLabel(packageManager).toString()
    private var was7DayToggleEnabled = false

    // Permission history is not available on TV devices.
    @Before
    fun assumeNotTv() = assumeFalse(isTv)

    // Permission history is not available on Auto devices running S or below.
    @Before
    fun assumeNotAutoBelowT() {
        assumeFalse(isAutomotive && !SdkLevel.isAtLeastT())
    }

    @Before
    fun installApps() {
        uninstallPackage(APP_PACKAGE_NAME, requireSuccess = false)
        uninstallPackage(APP2_PACKAGE_NAME, requireSuccess = false)
        installPackage(APP_APK_PATH, grantRuntimePermissions = true)
        installPackage(APP2_APK_PATH, grantRuntimePermissions = true)
    }

    @After
    fun uninstallApps() {
        uninstallPackage(APP_PACKAGE_NAME, requireSuccess = false)
        uninstallPackage(APP2_PACKAGE_NAME, requireSuccess = false)
    }

    @Before
    fun setUpTest() {
        // Wear does not currently support the permission dashboard
        assumeFalse(isWatch)

        SystemUtil.runWithShellPermissionIdentity {
            was7DayToggleEnabled = DeviceConfig.getBoolean(NAMESPACE_PRIVACY,
                    PRIV_DASH_7_DAY_ENABLED, false)
            DeviceConfig.setProperty(NAMESPACE_PRIVACY,
                    PRIV_DASH_7_DAY_ENABLED, true.toString(), false)
        }
    }

    @After
    fun tearDownTest() {
        SystemUtil.runWithShellPermissionIdentity {
            DeviceConfig.setProperty(NAMESPACE_PRIVACY,
                    PRIV_DASH_7_DAY_ENABLED, was7DayToggleEnabled.toString(), false)
        }
    }

    @Test
    fun testMicrophoneAccessShowsUpOnPrivacyDashboard() {
        openMicrophoneApp(INTENT_ACTION_1)
        waitFindObject(By.textContains(APP_LABEL_1))

        openPermissionDashboard()

        SystemUtil.eventually {
            try {
                waitFindObject(By.hasChild(By.textContains("Microphone"))
                        .hasChild(By.textStartsWith("Used by")))
                        .click()
                waitFindObject(By.textContains(micLabel))
                waitFindObject(By.textContains(APP_LABEL_1))
            } catch (e: Exception) {
                // Sometimes the dashboard was in the state from previous failed tests.
                // Clicking the refresh button to get the most recent access.
                waitFindObject(By.descContains(REFRESH)).click()
                throw e
            }
        }

        pressBack()
        pressBack()
    }

    @Test
    @Ignore
    fun testToggleSystemApps() {
        // I had some hard time mocking a system app.
        // Hence here I am only testing if the toggle is there.
        // Will comeback and add the system app for testing if we
        // need the line coverage for this. - theianchen@
        openMicrophoneApp(INTENT_ACTION_1)
        waitFindObject(By.textContains(APP_LABEL_1))

        openMicrophoneTimeline()
        // Auto doesn't show the "Show system" action when it is disabled. If a system app ends up
        // being installed for this test, then the Auto logic should be tested too.
        if (!isAutomotive) {
            SystemUtil.eventually {
                try {
                    val menuView = waitFindObject(By.descContains(MORE_OPTIONS))
                    menuView.click()
                    waitFindObject(By.text(SHOW_SYSTEM))
                } catch (e: Exception) {
                    // Sometimes the dashboard was in the state from previous failed tests.
                    // Clicking the refresh button to get the most recent access.
                    waitFindObject(By.descContains(REFRESH)).click()
                    throw e
                }
            }
        }

        pressBack()
        pressBack()
    }

    @Test
    fun testToggleFrom24HoursTo7Days() {
        // Auto doesn't support the 7 day view
        assumeFalse(isAutomotive)

        openMicrophoneApp(INTENT_ACTION_1)
        waitFindObject(By.textContains(APP_LABEL_1))

        openPermissionDashboard()
        waitFindObject(By.descContains(MORE_OPTIONS)).click()
        try {
            waitFindObject(By.text(SHOW_7_DAYS)).click()
        } catch (exception: RuntimeException) {
            // If privacy dashboard was set to 7d instead of 24h,
            // it will not be able to find the "Show 7 days" option.
            // This block is to toggle it back to 24h if that happens.
            waitFindObject(By.text(SHOW_24_HOURS)).click()
            waitFindObject(By.descContains(MORE_OPTIONS)).click()
            waitFindObject(By.text(SHOW_7_DAYS)).click()
        }

        SystemUtil.eventually {
            try {
                waitFindObject(By.hasChild(By.textContains("Microphone"))
                        .hasChild(By.textStartsWith("Used by")))
            } catch (e: Exception) {
                // Sometimes the dashboard was in the state from previous failed tests.
                // Clicking the refresh button to get the most recent access.
                waitFindObject(By.descContains(REFRESH)).click()
                throw e
            }
        }

        waitFindObject(By.text(Pattern.compile(DASHBOARD_7_DAYS_DESCRIPTION_REGEX, Pattern.DOTALL)))

        pressBack()
    }

    @Test
    @Ignore
    fun testToggleFrom24HoursTo7DaysInTimeline() {
        // Auto doesn't support the 7 day view
        assumeFalse(isAutomotive)

        openMicrophoneApp(INTENT_ACTION_1)
        waitFindObject(By.textContains(APP_LABEL_1))

        openMicrophoneTimeline()
        waitFindObject(By.descContains(MORE_OPTIONS)).click()
        try {
            waitFindObject(By.text(SHOW_7_DAYS)).click()
        } catch (exception: RuntimeException) {
            // If privacy dashboard was set to 7d instead of 24h,
            // it will not be able to find the "Show 7 days" option.
            // This block is to toggle it back to 24h if that happens.
            waitFindObject(By.text(SHOW_24_HOURS)).click()
            waitFindObject(By.descContains(MORE_OPTIONS)).click()
            waitFindObject(By.text(SHOW_7_DAYS)).click()
        }

        waitFindObject(By.descContains(micLabel))
        waitFindObject(By.textContains(APP_LABEL_1))
        waitFindObject(By.text(Pattern.compile(DASHBOARD_7_DAYS_DESCRIPTION_REGEX, Pattern.DOTALL)))

        pressBack()
    }

    @Test
    @Ignore
    fun testMicrophoneTimelineWithOneApp() {
        openMicrophoneApp(INTENT_ACTION_1)
        waitFindObject(By.textContains(APP_LABEL_1))

        openMicrophoneTimeline()
        waitFindObject(By.textContains(micLabel))
        waitFindObject(By.textContains(APP_LABEL_1))
        if (isAutomotive) {
            // Automotive views don't have the same ids as phones - find an example of the time
            // usage instead. Specify the package name to avoid matching with the system UI time.
            waitFindObject(
                By.text(Pattern.compile(DASHBOARD_TIME_DESCRIPTION_REGEX, Pattern.DOTALL))
                    .pkg(context.packageManager.permissionControllerPackageName))
        } else {
            waitFindObject(By.res(
                    PERMISSION_CONTROLLER_PACKAGE_ID_PREFIX + HISTORY_PREFERENCE_ICON))
            waitFindObject(By.res(
                    PERMISSION_CONTROLLER_PACKAGE_ID_PREFIX + HISTORY_PREFERENCE_TIME))
        }
        pressBack()
    }

    @Test
    @Ignore
    fun testCameraTimelineWithMultipleApps() {
        openMicrophoneApp(INTENT_ACTION_1)
        waitFindObject(By.textContains(APP_LABEL_1))

        openMicrophoneApp(INTENT_ACTION_2)
        waitFindObject(By.textContains(APP_LABEL_2))

        openMicrophoneTimeline()
        waitFindObject(By.textContains(micLabel))
        waitFindObject(By.textContains(APP_LABEL_1))
        waitFindObject(By.textContains(APP_LABEL_2))
        pressBack()
    }

    private fun openMicrophoneApp(intentAction: String) {
        context.startActivity(Intent(intentAction).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    private fun openPermissionDashboard() {
        SystemUtil.runWithShellPermissionIdentity {
            context.startActivity(Intent(Intent.ACTION_REVIEW_PERMISSION_USAGE).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }
    }

    companion object {
        const val APP_APK_PATH = "$APK_DIRECTORY/CtsAccessMicrophoneApp.apk"
        const val APP_PACKAGE_NAME = "android.permission3.cts.accessmicrophoneapp"
        const val APP2_APK_PATH = "$APK_DIRECTORY/CtsAccessMicrophoneApp2.apk"
        const val APP2_PACKAGE_NAME = "android.permission3.cts.accessmicrophoneapp2"
    }
}
