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

package android.os.cts

import android.app.Activity
import android.app.ActivityManager
import android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_TOP_SLEEPING
import android.app.Instrumentation
import android.app.UiAutomation
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.Process
import android.provider.DeviceConfig
import android.support.test.uiautomator.By
import android.support.test.uiautomator.BySelector
import android.support.test.uiautomator.UiDevice
import android.support.test.uiautomator.UiObject2
import android.support.test.uiautomator.UiScrollable
import android.support.test.uiautomator.UiSelector
import android.support.test.uiautomator.Until
import android.util.Log
import androidx.test.InstrumentationRegistry
import com.android.compatibility.common.util.ExceptionUtils.wrappingExceptions
import com.android.compatibility.common.util.FeatureUtil
import com.android.compatibility.common.util.LogcatInspector
import com.android.compatibility.common.util.SystemUtil.eventually
import com.android.compatibility.common.util.SystemUtil.runShellCommandOrThrow
import com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity
import com.android.compatibility.common.util.ThrowingSupplier
import com.android.compatibility.common.util.UiAutomatorUtils
import com.android.compatibility.common.util.UiDumpUtils
import com.android.compatibility.common.util.click
import com.android.compatibility.common.util.depthFirstSearch
import com.android.compatibility.common.util.textAsString
import org.hamcrest.Matcher
import org.hamcrest.Matchers
import org.junit.Assert
import org.junit.Assert.assertThat
import org.junit.Assert.assertTrue
import java.io.InputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

private const val BROADCAST_TIMEOUT_MS = 60000L

const val HIBERNATION_BOOT_RECEIVER_CLASS_NAME =
    "com.android.permissioncontroller.hibernation.HibernationOnBootReceiver"
const val ACTION_SET_UP_HIBERNATION =
    "com.android.permissioncontroller.action.SET_UP_HIBERNATION"

const val SYSUI_PKG_NAME = "com.android.systemui"
const val NOTIF_LIST_ID = "com.android.systemui:id/notification_stack_scroller"
const val NOTIF_LIST_ID_AUTOMOTIVE = "com.android.systemui:id/notifications"
const val CLEAR_ALL_BUTTON_ID = "dismiss_text"
// Time to find a notification. Unlikely, but in cases with a lot of notifications, it may take
// time to find the notification we're looking for
const val NOTIF_FIND_TIMEOUT = 20000L
const val VIEW_WAIT_TIMEOUT = 3000L

const val CMD_EXPAND_NOTIFICATIONS = "cmd statusbar expand-notifications"
const val CMD_COLLAPSE = "cmd statusbar collapse"

const val APK_PATH_S_APP = "/data/local/tmp/cts/os/CtsAutoRevokeSApp.apk"
const val APK_PACKAGE_NAME_S_APP = "android.os.cts.autorevokesapp"
const val APK_PATH_R_APP = "/data/local/tmp/cts/os/CtsAutoRevokeRApp.apk"
const val APK_PACKAGE_NAME_R_APP = "android.os.cts.autorevokerapp"
const val APK_PATH_Q_APP = "/data/local/tmp/cts/os/CtsAutoRevokeQApp.apk"
const val APK_PACKAGE_NAME_Q_APP = "android.os.cts.autorevokeqapp"

fun runBootCompleteReceiver(context: Context, testTag: String) {
    val pkgManager = context.packageManager
    val permissionControllerPkg = pkgManager.permissionControllerPackageName
    var permissionControllerSetupIntent = Intent(ACTION_SET_UP_HIBERNATION).apply {
        setPackage(permissionControllerPkg)
        setFlags(Intent.FLAG_RECEIVER_FOREGROUND)
    }
    val receivers = pkgManager.queryBroadcastReceivers(
        permissionControllerSetupIntent, /* flags= */ 0)
    if (receivers.size == 0) {
        // May be on an older, pre-built PermissionController. In this case, try sending directly.
        permissionControllerSetupIntent = Intent().apply {
            setPackage(permissionControllerPkg)
            setClassName(permissionControllerPkg, HIBERNATION_BOOT_RECEIVER_CLASS_NAME)
            setFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        }
    }
    val countdownLatch = CountDownLatch(1)
    Log.d(testTag, "Sending boot complete broadcast directly to $permissionControllerPkg")
    context.sendOrderedBroadcast(
        permissionControllerSetupIntent,
        /* receiverPermission= */ null,
        object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                countdownLatch.countDown()
                Log.d(testTag, "Broadcast received by $permissionControllerPkg")
            }
        },
        Handler.createAsync(Looper.getMainLooper()),
        Activity.RESULT_OK,
        /* initialData= */ null,
        /* initialExtras= */ null)
    assertTrue("Timed out while waiting for boot receiver broadcast to be received",
        countdownLatch.await(BROADCAST_TIMEOUT_MS, TimeUnit.MILLISECONDS))
}

fun runAppHibernationJob(context: Context, tag: String) {
    val logcat = Logcat()

    // Sometimes first run observes stale package data
    // so run twice to prevent that
    repeat(2) {
        val mark = logcat.mark(tag)
        eventually {
            runShellCommandOrThrow("cmd jobscheduler run -u " +
                "${Process.myUserHandle().identifier} -f " +
                "${context.packageManager.permissionControllerPackageName} 2")
        }
        logcat.assertLogcatContainsInOrder("*:*", 30_000,
            mark,
            "onStartJob",
            "Done auto-revoke for user")
    }
}

inline fun withApp(
    apk: String,
    packageName: String,
    action: () -> Unit
) {
    installApk(apk)
    try {
        // Try to reduce flakiness caused by new package update not propagating in time
        Thread.sleep(1000)
        action()
    } finally {
        uninstallApp(packageName)
    }
}

inline fun withAppNoUninstallAssertion(
    apk: String,
    packageName: String,
    action: () -> Unit
) {
    installApk(apk)
    try {
        // Try to reduce flakiness caused by new package update not propagating in time
        Thread.sleep(1000)
        action()
    } finally {
        uninstallAppWithoutAssertion(packageName)
    }
}

inline fun <T> withDeviceConfig(
    namespace: String,
    name: String,
    value: String,
    action: () -> T
): T {
    val oldValue = runWithShellPermissionIdentity(ThrowingSupplier {
        DeviceConfig.getProperty(namespace, name)
    })
    try {
        runWithShellPermissionIdentity {
            DeviceConfig.setProperty(namespace, name, value, false /* makeDefault */)
        }
        return action()
    } finally {
        runWithShellPermissionIdentity {
            DeviceConfig.setProperty(namespace, name, oldValue, false /* makeDefault */)
        }
    }
}

inline fun <T> withUnusedThresholdMs(threshold: Long, action: () -> T): T {
    return withDeviceConfig(
        DeviceConfig.NAMESPACE_PERMISSIONS, "auto_revoke_unused_threshold_millis2",
        threshold.toString(), action)
}

fun awaitAppState(pkg: String, stateMatcher: Matcher<Int>) {
    val context: Context = InstrumentationRegistry.getTargetContext()
    eventually {
        runWithShellPermissionIdentity {
            val packageImportance = context
                .getSystemService(ActivityManager::class.java)!!
                .getPackageImportance(pkg)
            assertThat(packageImportance, stateMatcher)
        }
    }
}

fun startApp(packageName: String) {
    val context = InstrumentationRegistry.getTargetContext()
    val intent = context.packageManager.getLaunchIntentForPackage(packageName)
    context.startActivity(intent)
    awaitAppState(packageName, Matchers.lessThanOrEqualTo(IMPORTANCE_TOP_SLEEPING))
    waitForIdle()
}

fun goHome() {
    runShellCommandOrThrow("input keyevent KEYCODE_HOME")
}

/**
 * Open the "unused apps" notification which is sent after the hibernation job.
 */
fun openUnusedAppsNotification() {
    val notifSelector = By.textContains("unused app")
    if (hasFeatureWatch()) {
        val uiAutomation = InstrumentationRegistry.getInstrumentation().uiAutomation
        expandNotificationsWatch(UiAutomatorUtils.getUiDevice())
        waitFindObject(uiAutomation, notifSelector).click()
        // In wear os, notification has one additional button to open it
        waitFindObject(uiAutomation, By.text("Open")).click()
    } else {
        runShellCommandOrThrow(CMD_EXPAND_NOTIFICATIONS)
        waitFindNotification(notifSelector, NOTIF_FIND_TIMEOUT).click()
    }
}

fun hasFeatureWatch(): Boolean {
    return InstrumentationRegistry.getTargetContext().packageManager.hasSystemFeature(
        PackageManager.FEATURE_WATCH)
}

fun hasFeatureTV(): Boolean {
    return InstrumentationRegistry.getTargetContext().packageManager.hasSystemFeature(
            PackageManager.FEATURE_LEANBACK) ||
            InstrumentationRegistry.getTargetContext().packageManager.hasSystemFeature(
                    PackageManager.FEATURE_TELEVISION)
}

private fun expandNotificationsWatch(uiDevice: UiDevice) {
    with(uiDevice) {
        wakeUp()
        // Swipe up from bottom to reveal notifications
        val x = displayWidth / 2
        swipe(x, displayHeight, x, 0, 1)
    }
}

/**
 * Reset to the top of the notifications list.
 */
private fun resetNotifications(notificationList: UiScrollable) {
    runShellCommandOrThrow(CMD_COLLAPSE)
    notificationList.waitUntilGone(VIEW_WAIT_TIMEOUT)
    runShellCommandOrThrow(CMD_EXPAND_NOTIFICATIONS)
}

private fun waitFindNotification(selector: BySelector, timeoutMs: Long):
    UiObject2 {
    var view: UiObject2? = null
    val start = System.currentTimeMillis()
    val uiDevice = UiAutomatorUtils.getUiDevice()

    var isAtEnd = false
    var wasScrolledUpAlready = false
    while (view == null && start + timeoutMs > System.currentTimeMillis()) {
        view = uiDevice.wait(Until.findObject(selector), VIEW_WAIT_TIMEOUT)
        if (view == null) {
            val notificationListId = if (FeatureUtil.isAutomotive()) {
                NOTIF_LIST_ID_AUTOMOTIVE
            } else {
                NOTIF_LIST_ID
            }
            val notificationList = UiScrollable(UiSelector().resourceId(notificationListId))
            wrappingExceptions({ cause: Throwable? -> UiDumpUtils.wrapWithUiDump(cause) }) {
                Assert.assertTrue("Notification list view not found",
                    notificationList.waitForExists(VIEW_WAIT_TIMEOUT))
            }
            if (isAtEnd) {
                if (wasScrolledUpAlready) {
                    break
                }
                resetNotifications(notificationList)
                isAtEnd = false
                wasScrolledUpAlready = true
            } else {
                notificationList.scrollForward()
                isAtEnd = uiDevice.hasObject(By.res(SYSUI_PKG_NAME, CLEAR_ALL_BUTTON_ID))
            }
        }
    }
    wrappingExceptions({ cause: Throwable? -> UiDumpUtils.wrapWithUiDump(cause) }) {
        Assert.assertNotNull("View not found after waiting for " + timeoutMs + "ms: " + selector,
            view)
    }
    return view!!
}

fun waitFindObject(uiAutomation: UiAutomation, selector: BySelector): UiObject2 {
    try {
        return UiAutomatorUtils.waitFindObject(selector)
    } catch (e: RuntimeException) {
        val ui = uiAutomation.rootInActiveWindow

        val title = ui.depthFirstSearch { node ->
            node.viewIdResourceName?.contains("alertTitle") == true
        }
        val okCloseButton = ui.depthFirstSearch { node ->
            (node.textAsString?.equals("OK", ignoreCase = true) ?: false)  ||
                (node.textAsString?.equals("Close app", ignoreCase = true) ?: false)
        }
        val titleString = title?.text?.toString()
        if (okCloseButton != null &&
            titleString != null &&
            (titleString == "Android System" ||
                titleString.endsWith("keeps stopping"))) {
            // Auto dismiss occasional system dialogs to prevent interfering with the test
            android.util.Log.w(AutoRevokeTest.LOG_TAG, "Ignoring exception", e)
            okCloseButton.click()
            return UiAutomatorUtils.waitFindObject(selector)
        } else {
            throw e
        }
    }
}

class Logcat() : LogcatInspector() {
    override fun executeShellCommand(command: String?): InputStream {
        val instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation()
        return ParcelFileDescriptor.AutoCloseInputStream(
            instrumentation.uiAutomation.executeShellCommand(command))
    }
}
