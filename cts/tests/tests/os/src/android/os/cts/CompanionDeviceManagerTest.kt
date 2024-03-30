/*
 * Copyright (C) 2019 The Android Open Source Project
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

import android.app.Instrumentation
import android.companion.CompanionDeviceManager
import android.content.pm.PackageManager
import android.content.pm.PackageManager.FEATURE_AUTOMOTIVE
import android.content.pm.PackageManager.FEATURE_COMPANION_DEVICE_SETUP
import android.content.pm.PackageManager.FEATURE_LEANBACK
import android.net.MacAddress
import android.os.Binder
import android.os.Bundle
import android.os.Parcelable
import android.os.UserHandle
import android.platform.test.annotations.AppModeFull
import android.util.Size
import android.util.SizeF
import android.util.SparseArray
import androidx.test.InstrumentationRegistry
import androidx.test.runner.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import com.android.compatibility.common.util.MatcherUtils.hasIdThat
import com.android.compatibility.common.util.SystemUtil.getEventually
import com.android.compatibility.common.util.SystemUtil.runShellCommand
import com.android.compatibility.common.util.SystemUtil.runShellCommandOrThrow
import com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity
import com.android.compatibility.common.util.ThrowingSupplier
import com.android.compatibility.common.util.UiAutomatorUtils.waitFindObject
import com.android.compatibility.common.util.click
import org.hamcrest.CoreMatchers.containsString
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThat
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import java.io.Serializable

/**
 * Test for [CompanionDeviceManager]
 */
@RunWith(AndroidJUnit4::class)
class CompanionDeviceManagerTest {
    companion object {
        const val COMPANION_APPROVE_WIFI_CONNECTIONS =
                "android.permission.COMPANION_APPROVE_WIFI_CONNECTIONS"
        const val DUMMY_MAC_ADDRESS = "00:00:00:00:00:10"
        const val MANAGE_COMPANION_DEVICES = "android.permission.MANAGE_COMPANION_DEVICES"
        const val SHELL_PACKAGE_NAME = "com.android.shell"
        const val TEST_APP_PACKAGE_NAME = "android.os.cts.companiontestapp"
        const val TEST_APP_APK_LOCATION = "/data/local/tmp/cts/os/CtsCompanionTestApp.apk"
        const val CDM_UI_PACKAGE_NAME = "com.android.companiondevicemanager"
        const val RECYCLER_VIEW_CLASS = "androidx.recyclerview.widget.RecyclerView"

        val DEVICE_LIST_ITEM_SELECTOR: BySelector = By.res(CDM_UI_PACKAGE_NAME, "list_item_device")
        val DEVICE_LIST_SELECTOR: BySelector = By.pkg(CDM_UI_PACKAGE_NAME)
                .clazz(RECYCLER_VIEW_CLASS)
                .hasChild(DEVICE_LIST_ITEM_SELECTOR)
    }

    private val instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation()
    private val uiDevice = UiDevice.getInstance(instrumentation)
    private val context = instrumentation.targetContext
    private val userId = context.userId
    private val packageName = context.packageName!!

    private val pm: PackageManager by lazy { context.packageManager }
    private val hasFeatureCompanionDeviceSetup: Boolean by lazy {
        pm.hasSystemFeature(FEATURE_COMPANION_DEVICE_SETUP)
    }

    private val cdm: CompanionDeviceManager by lazy {
        context.getSystemService(CompanionDeviceManager::class.java)
    }
    private val isAuto: Boolean by lazy { pm.hasSystemFeature(FEATURE_AUTOMOTIVE) }
    private val isTV: Boolean by lazy { pm.hasSystemFeature(FEATURE_LEANBACK) }

    @Before
    fun assumeHasFeature() {
        assumeTrue(hasFeatureCompanionDeviceSetup)
    }

    @After
    fun cleanUp() {
        // If the devices does not have the feature or is an Auto, the test didn't run, and the
        // clean up is not needed (will actually crash if the feature is missing).
        // See assumeHasFeature @Before method.
        if (!hasFeatureCompanionDeviceSetup || isAuto) return

        // Remove associations
        val associations = getAssociatedDevices(TEST_APP_PACKAGE_NAME)
        for (address in associations) {
            runShellCommandOrThrow(
                    "cmd companiondevice disassociate $userId $TEST_APP_PACKAGE_NAME $address")
        }

        // Uninstall test app
        uninstallAppWithoutAssertion(TEST_APP_PACKAGE_NAME)
    }

    @AppModeFull(reason = "Companion API for non-instant apps only")
    @Test
    fun testIsDeviceAssociated() {
        assertFalse(isCdmAssociated(DUMMY_MAC_ADDRESS, packageName, MANAGE_COMPANION_DEVICES))
        assertFalse(isShellAssociated(DUMMY_MAC_ADDRESS, packageName))

        try {
            runShellCommand(
                    "cmd companiondevice associate $userId $packageName $DUMMY_MAC_ADDRESS")
            assertTrue(isCdmAssociated(DUMMY_MAC_ADDRESS, packageName, MANAGE_COMPANION_DEVICES))
            assertTrue(isShellAssociated(DUMMY_MAC_ADDRESS, packageName))
        } finally {
            runShellCommand(
                    "cmd companiondevice disassociate $userId $packageName $DUMMY_MAC_ADDRESS")
        }
    }

    @AppModeFull(reason = "Companion API for non-instant apps only")
    @Test
    fun testIsDeviceAssociatedWithCompanionApproveWifiConnectionsPermission() {
        assertTrue(isCdmAssociated(
            DUMMY_MAC_ADDRESS, SHELL_PACKAGE_NAME, MANAGE_COMPANION_DEVICES,
            COMPANION_APPROVE_WIFI_CONNECTIONS))
        assertFalse(isShellAssociated(DUMMY_MAC_ADDRESS, SHELL_PACKAGE_NAME))
    }

    @AppModeFull(reason = "Companion API for non-instant apps only")
    @Test
    fun testDump() {
        try {
            runShellCommand(
                    "cmd companiondevice associate $userId $packageName $DUMMY_MAC_ADDRESS")
            val output = runShellCommand("dumpsys companiondevice")
            assertThat(output, containsString(packageName))
            assertThat(output, containsString(DUMMY_MAC_ADDRESS))
        } finally {
            runShellCommand(
                    "cmd companiondevice disassociate $userId $packageName $DUMMY_MAC_ADDRESS")
        }
    }

    @AppModeFull(reason = "Companion API for non-instant apps only")
    @Test
    @Ignore("b/212535524")
    fun testRequestNotifications() {
        // Skip this test for Android TV due to NotificationAccessConfirmationActivity only exists
        // in Settings but not in TvSettings for Android TV devices (b/199224565).
        assumeFalse(isTV)

        installApk("--user $userId $TEST_APP_APK_LOCATION")
        startApp(TEST_APP_PACKAGE_NAME)

        uiDevice.waitAndFind(By.desc("name filter")).text = ""
        uiDevice.waitForIdle()

        click("Associate")

        uiDevice.wait(Until.findObject(DEVICE_LIST_SELECTOR), 20_000)
                ?.findObject(DEVICE_LIST_ITEM_SELECTOR)
                ?.click()
                ?: throw AssertionError("Empty device list")

        waitForIdle()

        val deviceForNotifications = getEventually({
            click("Request Notifications")
            waitFindNode(hasIdThat(containsString("button1")),
                    failMsg = "The Request Notifications dialog is not showing up",
                    timeoutMs = 5_000)
                    .assertNotNull { "Request Notifications is not implemented" }
        }, 60_000)

        deviceForNotifications!!.click()

        waitForIdle()
    }

    private fun getAssociatedDevices(
        pkg: String,
        user: UserHandle = android.os.Process.myUserHandle()
    ): List<String> {
        return runShellCommandOrThrow("cmd companiondevice list ${user.identifier}")
                .lines()
                .filter { it.startsWith(pkg) }
                .map { it.substringAfterLast(" ") }
    }

    private fun isShellAssociated(macAddress: String, packageName: String): Boolean {
        return runShellCommand("cmd companiondevice list $userId")
                .lines()
                .any {
                    packageName in it && macAddress in it
                }
    }

    private fun isCdmAssociated(
        macAddress: String,
        packageName: String,
        vararg permissions: String
    ): Boolean {
        return runWithShellPermissionIdentity(ThrowingSupplier {
            cdm.isDeviceAssociatedForWifiConnection(packageName,
                    MacAddress.fromString(macAddress), context.user)
        }, *permissions)
    }
}

private fun UiDevice.waitAndFind(selector: BySelector): UiObject2 =
        wait(Until.findObject(selector), 1000)

private fun click(label: String) {
    waitFindObject(byTextIgnoreCase(label)).click()
    waitForIdle()
}

operator fun Bundle.set(key: String, value: Any?) {
    if (value is Array<*> && value.isArrayOf<Parcelable>()) {
        putParcelableArray(key, value as Array<Parcelable>)
        return
    }
    if (value is Array<*> && value.isArrayOf<CharSequence>()) {
        putCharSequenceArray(key, value as Array<CharSequence>)
        return
    }
    when (value) {
        is Byte -> putByte(key, value)
        is Char -> putChar(key, value)
        is Short -> putShort(key, value)
        is Float -> putFloat(key, value)
        is CharSequence -> putCharSequence(key, value)
        is Parcelable -> putParcelable(key, value)
        is Size -> putSize(key, value)
        is SizeF -> putSizeF(key, value)
        is ArrayList<*> -> putParcelableArrayList(key, value as ArrayList<Parcelable>)
        is SparseArray<*> -> putSparseParcelableArray(key, value as SparseArray<Parcelable>)
        is Serializable -> putSerializable(key, value)
        is ByteArray -> putByteArray(key, value)
        is ShortArray -> putShortArray(key, value)
        is CharArray -> putCharArray(key, value)
        is FloatArray -> putFloatArray(key, value)
        is Bundle -> putBundle(key, value)
        is Binder -> putBinder(key, value)
        else -> throw IllegalArgumentException("" + value)
    }
}