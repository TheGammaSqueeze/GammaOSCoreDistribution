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

import android.Manifest.permission.POST_NOTIFICATIONS
import android.Manifest.permission.RECORD_AUDIO
import android.app.ActivityManager
import android.app.ActivityOptions
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.PackageManager.FLAG_PERMISSION_REVIEW_REQUIRED
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.os.Build
import android.os.Process
import android.os.UserHandle
import android.provider.Settings
import android.support.test.uiautomator.By
import androidx.test.filters.SdkSuppress
import com.android.compatibility.common.util.SystemUtil
import com.android.compatibility.common.util.SystemUtil.callWithShellPermissionIdentity
import com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.Assume.assumeFalse
import java.util.concurrent.CountDownLatch

const val EXTRA_DELETE_CHANNELS_ON_CLOSE = "extra_delete_channels_on_close"
const val EXTRA_CREATE_CHANNELS = "extra_create"
const val EXTRA_CREATE_CHANNELS_DELAYED = "extra_create_delayed"
const val EXTRA_REQUEST_OTHER_PERMISSIONS = "extra_request_permissions"
const val EXTRA_REQUEST_NOTIF_PERMISSION = "extra_request_notif_permission"
const val EXTRA_REQUEST_PERMISSIONS_DELAYED = "extra_request_permissions_delayed"
const val EXTRA_START_SECOND_ACTIVITY = "extra_start_second_activity"
const val EXTRA_START_SECOND_APP = "extra_start_second_app"
const val ACTIVITY_NAME = "CreateNotificationChannelsActivity"
const val ACTIVITY_LABEL = "CreateNotif"
const val SECOND_ACTIVITY_LABEL = "EmptyActivity"
const val ALLOW = "to send you"
const val INTENT_ACTION = "usepermission.createchannels.MAIN"
const val BROADCAST_ACTION = "usepermission.createchannels.BROADCAST"
const val NOTIFICATION_PERMISSION_ENABLED = "notification_permission_enabled"
const val EXPECTED_TIMEOUT_MS = 2000L

@SdkSuppress(minSdkVersion = Build.VERSION_CODES.TIRAMISU, codeName = "Tiramisu")
class NotificationPermissionTest : BaseUsePermissionTest() {

    private val cr = callWithShellPermissionIdentity {
        context.createContextAsUser(UserHandle.SYSTEM, 0).contentResolver
    }
    private var previousEnableState = -1
    private var countDown: CountDownLatch = CountDownLatch(1)
    private var allowedGroups = listOf<String>()
    private val receiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            allowedGroups = intent?.getStringArrayListExtra(
                PackageManager.EXTRA_REQUEST_PERMISSIONS_RESULTS) ?: emptyList()
            countDown.countDown()
        }
    }

    @Before
    fun setLatchAndEnablePermission() {
        // b/220968160: Notification permission is not enabled on TV devices.
        assumeFalse(isTv)
        runWithShellPermissionIdentity {
            previousEnableState = Settings.Secure.getInt(cr, NOTIFICATION_PERMISSION_ENABLED, 0)
            Settings.Secure.putInt(cr, NOTIFICATION_PERMISSION_ENABLED, 1)
        }
        countDown = CountDownLatch(1)
        allowedGroups = listOf()
        context.registerReceiver(receiver, IntentFilter(BROADCAST_ACTION))
    }

    @After
    fun resetPermissionAndRemoveReceiver() {
        if (previousEnableState >= 0) {
            runWithShellPermissionIdentity {
                Settings.Secure.putInt(cr, NOTIFICATION_PERMISSION_ENABLED, previousEnableState)
            }
            context.unregisterReceiver(receiver)
        }
    }

    @Test
    fun notificationPermissionAddedForLegacyApp() {
        installPackage(APP_APK_PATH_CREATE_NOTIFICATION_CHANNELS_31, expectSuccess = true)
        runWithShellPermissionIdentity {
            Assert.assertTrue("SDK < 32 apps should have POST_NOTIFICATIONS added implicitly",
                context.packageManager.getPackageInfo(APP_PACKAGE_NAME,
                    PackageManager.GET_PERMISSIONS).requestedPermissions
                    .contains(POST_NOTIFICATIONS))
        }
    }

    @Test
    fun notificationPermissionIsNotImplicitlyAddedTo33Apps() {
        installPackage(APP_APK_PATH_LATEST_NONE, expectSuccess = true)
        runWithShellPermissionIdentity {
            val requestedPerms = context.packageManager.getPackageInfo(APP_PACKAGE_NAME,
                    PackageManager.GET_PERMISSIONS).requestedPermissions
            Assert.assertTrue("SDK >= 33 apps should NOT have POST_NOTIFICATIONS added implicitly",
                    requestedPerms == null || !requestedPerms.contains(POST_NOTIFICATIONS))
        }
    }

    @Test
    fun notificationPromptShowsForLegacyAppAfterCreatingNotificationChannels() {
        installPackage(APP_APK_PATH_CREATE_NOTIFICATION_CHANNELS_31, expectSuccess = true)
        launchApp()
        clickPermissionRequestAllowButton()
    }

    @Test
    fun notificationPromptShowsForLegacyAppWithNotificationChannelsOnStart() {
        installPackage(APP_APK_PATH_CREATE_NOTIFICATION_CHANNELS_31, expectSuccess = true)
        // create channels, then leave the app
        launchApp()
        killTestApp()
        launchApp()
        waitFindObject(By.textContains(ALLOW))
        clickPermissionRequestAllowButton()
    }

    @Test
    fun notificationPromptDoesNotShowForLegacyAppWithNoNotificationChannels_onLaunch() {
        installPackage(APP_APK_PATH_CREATE_NOTIFICATION_CHANNELS_31, expectSuccess = true)
        launchApp(createChannels = false)
        assertDialogNotShowing()
    }
    @Test
    fun notificationPromptDoesNotShowForNonLauncherIntentCategoryLaunches_onChannelCreate() {
        installPackage(APP_APK_PATH_CREATE_NOTIFICATION_CHANNELS_31, expectSuccess = true)
        launchApp(launcherCategory = false)
        assertDialogNotShowing()
    }

    @Test
    fun notificationPromptDoesNotShowForNonLauncherIntentCategoryLaunches_onLaunch() {
        installPackage(APP_APK_PATH_CREATE_NOTIFICATION_CHANNELS_31, expectSuccess = true)
        // create channels, then leave the app
        launchApp()
        killTestApp()
        launchApp(launcherCategory = false)
        assertDialogNotShowing()
    }

    @Test
    fun notificationPromptDoesNotShowForNonMainIntentActionLaunches_onLaunch() {
        installPackage(APP_APK_PATH_CREATE_NOTIFICATION_CHANNELS_31, expectSuccess = true)
        // create channels, then leave the app
        launchApp()
        killTestApp()
        launchApp(mainIntent = false)
        assertDialogNotShowing()
    }

    @Test
    fun notificationPromptDoesNotShowForNonMainIntentActionLaunches_onChannelCreate() {
        installPackage(APP_APK_PATH_CREATE_NOTIFICATION_CHANNELS_31, expectSuccess = true)
        launchApp(mainIntent = false)
        assertDialogNotShowing()
    }

    @Test
    fun notificationPromptShowsIfActivityOptionSet() {
        installPackage(APP_APK_PATH_CREATE_NOTIFICATION_CHANNELS_31, expectSuccess = true)
        // create channels, then leave the app
        launchApp()
        killTestApp()
        launchApp(mainIntent = false, isEligibleForPromptOption = true)
        clickPermissionRequestAllowButton()
    }

    @Test
    fun notificationPromptShownForSubsequentStartsIfTaskStartWasLauncher() {
        installPackage(APP_APK_PATH_CREATE_NOTIFICATION_CHANNELS_31, expectSuccess = true)
        launchApp(startSecondActivity = true)
        waitFindObject(By.res(ALLOW_BUTTON))
        pressBack()
        clickPermissionRequestAllowButton()
    }

    @Test
    fun notificationPromptNotShownForSubsequentStartsIfTaskStartWasNotLauncher() {
        installPackage(APP_APK_PATH_CREATE_NOTIFICATION_CHANNELS_31, expectSuccess = true)
        launchApp(mainIntent = false, startSecondActivity = true)
        assertDialogNotShowing()
    }

    @Test
    fun notificationPromptShownForChannelCreateInSecondActivityIfTaskStartWasLauncher() {
        installPackage(APP_APK_PATH_CREATE_NOTIFICATION_CHANNELS_31, expectSuccess = true)
        launchApp(startSecondActivity = true, createChannels = false)
        clickPermissionRequestAllowButton()
    }

    @Test
    fun notificationPromptNotShownForChannelCreateInSecondActivityIfTaskStartWasntLauncher() {
        installPackage(APP_APK_PATH_CREATE_NOTIFICATION_CHANNELS_31, expectSuccess = true)
        launchApp(mainIntent = false, startSecondActivity = true, createChannels = false)
        assertDialogNotShowing()
    }

    @Test
    fun notificationPromptNotShownForSubsequentStartsIfSubsequentIsDifferentPkg() {
        installPackage(APP_APK_PATH_CREATE_NOTIFICATION_CHANNELS_31, expectSuccess = true)
        installPackage(APP_APK_PATH_OTHER_APP, expectSuccess = true)
        // perform a launcher start, then start a secondary app
        launchApp(startSecondaryAppAndCreateChannelsAfterSecondStart = true)
        try {
            // Watch does not have app bar
            if (!isWatch) {
                waitFindObject(By.textContains(SECOND_ACTIVITY_LABEL))
            }
            assertDialogNotShowing()
        } finally {
            uninstallPackage(OTHER_APP_PACKAGE_NAME)
        }
    }

    @Test
    fun notificationGrantedOnLegacyGrant() {
        installPackage(APP_APK_PATH_CREATE_NOTIFICATION_CHANNELS_31, expectSuccess = true)
        launchApp()
        clickPermissionRequestAllowButton()
        assertAppPermissionGrantedState(POST_NOTIFICATIONS, granted = true)
    }

    @Test
    fun nonSystemServerPackageCannotShowPromptForOtherPackage() {
        installPackage(APP_APK_PATH_CREATE_NOTIFICATION_CHANNELS_31, expectSuccess = true)
        runWithShellPermissionIdentity {
            val grantPermission = Intent(PackageManager.ACTION_REQUEST_PERMISSIONS_FOR_OTHER)
            grantPermission.putExtra(Intent.EXTRA_PACKAGE_NAME, APP_PACKAGE_NAME)
            grantPermission.putExtra(PackageManager.EXTRA_REQUEST_PERMISSIONS_NAMES,
                arrayOf(POST_NOTIFICATIONS))
            grantPermission.setPackage(context.packageManager.permissionControllerPackageName)
            grantPermission.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(grantPermission)
        }
        try {
            clickPermissionRequestAllowButton(timeoutMillis = EXPECTED_TIMEOUT_MS)
            Assert.fail("Expected not to find permission request dialog")
        } catch (expected: RuntimeException) {
            // Do nothing
        }
    }

    @Test
    fun mergeAppPermissionRequestIntoNotificationAndVerifyResult() {
        installPackage(APP_APK_PATH_CREATE_NOTIFICATION_CHANNELS_31, expectSuccess = true)
        launchApp(requestPermissionsDelayed = true)
        clickPermissionRequestAllowButton()
        assertAppPermissionGrantedState(POST_NOTIFICATIONS, granted = true)
        clickPermissionRequestAllowForegroundButton()
        assertAppPermissionGrantedState(RECORD_AUDIO, granted = true)
        countDown.await()
        // Result should contain only the microphone request
        Assert.assertEquals(listOf(RECORD_AUDIO), allowedGroups)
    }

    @Test
    fun mergeNotificationRequestIntoAppPermissionRequestAndVerifyResult() {
        installPackage(APP_APK_PATH_CREATE_NOTIFICATION_CHANNELS_31, expectSuccess = true)
        launchApp(createChannels = false, createChannelsDelayed = true, requestPermissions = true)
        clickPermissionRequestAllowForegroundButton()
        assertAppPermissionGrantedState(RECORD_AUDIO, granted = true)
        clickPermissionRequestAllowButton()
        assertAppPermissionGrantedState(POST_NOTIFICATIONS, granted = true)
        countDown.await()
        // Result should contain only the microphone request
        Assert.assertEquals(listOf(RECORD_AUDIO), allowedGroups)
    }

    // Enable this test once droidfood code is removed
    @Test
    fun newlyInstalledLegacyAppsDontHaveReviewRequired() {
        installPackage(APP_APK_PATH_CREATE_NOTIFICATION_CHANNELS_31, expectSuccess = true)
        runWithShellPermissionIdentity {
            Assert.assertEquals("expect REVIEW_REQUIRED to not be set", 0, context.packageManager
                .getPermissionFlags(POST_NOTIFICATIONS, APP_PACKAGE_NAME, Process.myUserHandle())
                and FLAG_PERMISSION_REVIEW_REQUIRED)
        }
    }

    @Test
    fun newlyInstalledTAppsDontHaveReviewRequired() {
        installPackage(APP_APK_PATH_CREATE_NOTIFICATION_CHANNELS_33, expectSuccess = true)
        runWithShellPermissionIdentity {
            Assert.assertEquals("expect REVIEW_REQUIRED to not be set", 0, context.packageManager
                .getPermissionFlags(POST_NOTIFICATIONS, APP_PACKAGE_NAME, Process.myUserHandle())
                and FLAG_PERMISSION_REVIEW_REQUIRED)
        }
    }

    @Test
    fun legacyAppCannotExplicitlyRequestNotifications() {
        installPackage(APP_APK_PATH_CREATE_NOTIFICATION_CHANNELS_31, expectSuccess = true)
        launchApp(createChannels = false, requestNotificationPermission = true)
        try {
            clickPermissionRequestAllowButton(timeoutMillis = EXPECTED_TIMEOUT_MS)
            Assert.fail("Expected not to find permission request dialog")
        } catch (expected: RuntimeException) {
            // Do nothing
        }
    }

    private fun assertAppPermissionGrantedState(permission: String, granted: Boolean) {
        SystemUtil.eventually {
            runWithShellPermissionIdentity {
                Assert.assertEquals("Expected $permission to be granted", context.packageManager
                        .checkPermission(permission, APP_PACKAGE_NAME), PERMISSION_GRANTED)
            }
        }
    }

    private fun launchApp(
        createChannels: Boolean = true,
        createChannelsDelayed: Boolean = false,
        requestNotificationPermission: Boolean = false,
        requestPermissions: Boolean = false,
        requestPermissionsDelayed: Boolean = false,
        launcherCategory: Boolean = true,
        mainIntent: Boolean = true,
        isEligibleForPromptOption: Boolean = false,
        startSecondActivity: Boolean = false,
        startSecondaryAppAndCreateChannelsAfterSecondStart: Boolean = false
    ) {
        val intent = if (mainIntent && launcherCategory) {
            packageManager.getLaunchIntentForPackage(APP_PACKAGE_NAME)!!
        } else if (mainIntent) {
            Intent(Intent.ACTION_MAIN)
        } else {
            Intent(INTENT_ACTION)
        }

        intent.`package` = APP_PACKAGE_NAME
        intent.putExtra(EXTRA_CREATE_CHANNELS, createChannels)
        if (!createChannels) {
            intent.putExtra(EXTRA_CREATE_CHANNELS_DELAYED, createChannelsDelayed)
        }
        intent.putExtra(EXTRA_REQUEST_OTHER_PERMISSIONS, requestPermissions)
        if (!requestPermissions) {
            intent.putExtra(EXTRA_REQUEST_PERMISSIONS_DELAYED, requestPermissionsDelayed)
        }
        intent.putExtra(EXTRA_REQUEST_NOTIF_PERMISSION, requestNotificationPermission)
        intent.putExtra(EXTRA_START_SECOND_ACTIVITY, startSecondActivity)
        intent.putExtra(EXTRA_START_SECOND_APP, startSecondaryAppAndCreateChannelsAfterSecondStart)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK

        val options = ActivityOptions.makeBasic()
        options.isEligibleForLegacyPermissionPrompt = isEligibleForPromptOption
        context.startActivity(intent, options.toBundle())

        // Watch does not have app bar
        if (!isWatch) {
            waitFindObject(By.textContains(ACTIVITY_LABEL))
        }
        waitForIdle()
    }

    private fun killTestApp() {
        pressBack()
        pressBack()
        runWithShellPermissionIdentity {
            val am = context.getSystemService(ActivityManager::class.java)!!
            am.forceStopPackage(APP_PACKAGE_NAME)
        }
        waitForIdle()
    }

    private fun assertDialogNotShowing(timeoutMillis: Long = EXPECTED_TIMEOUT_MS) {
        try {
            clickPermissionRequestAllowButton(timeoutMillis)
            Assert.fail("Expected not to find permission request dialog")
        } catch (expected: RuntimeException) {
            // Do nothing
        }
    }
}
