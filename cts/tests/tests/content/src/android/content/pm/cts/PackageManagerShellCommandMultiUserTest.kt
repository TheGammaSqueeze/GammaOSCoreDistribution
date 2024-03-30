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

package android.content.pm.cts

import android.Manifest
import android.app.UiAutomation
import android.content.Context
import android.content.Context.RECEIVER_EXPORTED
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.cts.PackageManagerShellCommandTest.FullyRemovedBroadcastReceiver
import android.content.pm.cts.util.AbandonAllPackageSessionsRule
import android.os.Handler
import android.os.HandlerThread
import android.platform.test.annotations.AppModeFull
import androidx.test.InstrumentationRegistry
import com.android.bedstead.harrier.BedsteadJUnit4
import com.android.bedstead.harrier.DeviceState
import com.android.bedstead.harrier.annotations.EnsureHasSecondaryUser
import com.android.bedstead.harrier.annotations.StringTestParameter
import com.android.bedstead.nene.permissions.CommonPermissions.INTERACT_ACROSS_USERS
import com.android.bedstead.nene.permissions.CommonPermissions.INTERACT_ACROSS_USERS_FULL
import com.android.bedstead.nene.users.UserReference
import com.android.compatibility.common.util.SystemUtil
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@EnsureHasSecondaryUser
@RunWith(BedsteadJUnit4::class)
@AppModeFull(reason = "Cannot query other apps if instant")
class PackageManagerShellCommandMultiUserTest {

    companion object {

        private const val TEST_APP_PACKAGE = PackageManagerShellCommandTest.TEST_APP_PACKAGE
        private const val TEST_HW5 = PackageManagerShellCommandTest.TEST_HW5

        @JvmField
        @ClassRule
        @Rule
        val deviceState = DeviceState()

        @JvmField
        @ClassRule
        var mAbandonSessionsRule = AbandonAllPackageSessionsRule()

        private val context: Context = InstrumentationRegistry.getContext()
        private val uiAutomation: UiAutomation =
            InstrumentationRegistry.getInstrumentation().getUiAutomation()

        private var backgroundThread = HandlerThread("PackageManagerShellCommandMultiUserTest")

        fun skipTheInstallType(installTypeString: String): Boolean {
            if (installTypeString == "install-incremental" &&
                !context.packageManager.hasSystemFeature(
                    PackageManager.FEATURE_INCREMENTAL_DELIVERY)) {
                return true
            }
            return false
        }
    }

    private lateinit var primaryUser: UserReference
    private lateinit var secondaryUser: UserReference

    @Before
    fun cacheUsers() {
        primaryUser = deviceState.primaryUser()
        secondaryUser = deviceState.secondaryUser()
    }

    private var mPackageVerifier: String? = null
    private var mStreamingVerificationTimeoutMs =
        PackageManagerShellCommandTest.DEFAULT_STREAMING_VERIFICATION_TIMEOUT_MS

    @Before
    fun setup() {
        uninstallPackageSilently(TEST_APP_PACKAGE)
        assertFalse(PackageManagerShellCommandTest.isAppInstalled(TEST_APP_PACKAGE))
        mPackageVerifier =
            SystemUtil.runShellCommand("settings get global verifier_verify_adb_installs")
        // Disable the package verifier for non-incremental installations to avoid the dialog
        // when installing an app.
        SystemUtil.runShellCommand("settings put global verifier_verify_adb_installs 0")
        mStreamingVerificationTimeoutMs = SystemUtil.runShellCommand(
            "settings get global streaming_verifier_timeout"
        )
            .toLongOrNull()
            ?: PackageManagerShellCommandTest.DEFAULT_STREAMING_VERIFICATION_TIMEOUT_MS
    }

    @After
    fun reset() {
        uninstallPackageSilently(TEST_APP_PACKAGE)
        assertFalse(PackageManagerShellCommandTest.isAppInstalled(TEST_APP_PACKAGE))
        assertEquals(null, PackageManagerShellCommandTest.getSplits(TEST_APP_PACKAGE))

        // Reset the global settings to their original values.
        SystemUtil.runShellCommand(
            "settings put global verifier_verify_adb_installs $mPackageVerifier"
        )

        // Set the test override to invalid.
        setSystemProperty("debug.pm.uses_sdk_library_default_cert_digest", "invalid")
        setSystemProperty("debug.pm.prune_unused_shared_libraries_delay", "invalid")
        setSystemProperty("debug.pm.adb_verifier_override_package", "invalid")
    }

    @Test
    fun testGetFirstInstallTime(
        @StringTestParameter(
            "install",
            "install-streaming",
            "install-incremental"
        ) installTypeString: String
    ) {
        if (skipTheInstallType(installTypeString)) {
            return
        }
        val startTimeMillisForPrimaryUser = System.currentTimeMillis()
        installPackageAsUser(TEST_HW5, primaryUser, installTypeString)
        assertTrue(isAppInstalledForUser(TEST_APP_PACKAGE, primaryUser))
        val origFirstInstallTimeForPrimaryUser =
            getFirstInstallTimeAsUser(TEST_APP_PACKAGE, primaryUser)
        // Validate the timestamp
        assertTrue(origFirstInstallTimeForPrimaryUser > 0)
        assertTrue(startTimeMillisForPrimaryUser < origFirstInstallTimeForPrimaryUser)
        assertTrue(System.currentTimeMillis() > origFirstInstallTimeForPrimaryUser)

        // Install again with replace and the firstInstallTime should remain the same
        installPackage(TEST_HW5, installTypeString)
        var firstInstallTimeForPrimaryUser =
            getFirstInstallTimeAsUser(TEST_APP_PACKAGE, primaryUser)
        assertEquals(origFirstInstallTimeForPrimaryUser, firstInstallTimeForPrimaryUser)

        // Start another user and install this test itself for that user
        var startTimeMillisForSecondaryUser = System.currentTimeMillis()
        installExistingPackageAsUser(context.packageName, secondaryUser)
        assertTrue(isAppInstalledForUser(context.packageName, secondaryUser))
        // Install test package with replace
        installPackageAsUser(TEST_HW5, secondaryUser, installTypeString)
        assertTrue(isAppInstalledForUser(TEST_APP_PACKAGE, secondaryUser))
        firstInstallTimeForPrimaryUser = getFirstInstallTimeAsUser(TEST_APP_PACKAGE, primaryUser)
        // firstInstallTime should remain unchanged for the current user
        assertEquals(origFirstInstallTimeForPrimaryUser, firstInstallTimeForPrimaryUser)
        var firstInstallTimeForSecondaryUser =
            getFirstInstallTimeAsUser(TEST_APP_PACKAGE, secondaryUser)
        // firstInstallTime for the other user should be different
        assertNotEquals(firstInstallTimeForPrimaryUser, firstInstallTimeForSecondaryUser)
        assertTrue(startTimeMillisForSecondaryUser < firstInstallTimeForSecondaryUser)
        assertTrue(System.currentTimeMillis() > firstInstallTimeForSecondaryUser)

        // Uninstall for the other user
        uninstallPackageAsUser(TEST_APP_PACKAGE, secondaryUser)
        assertFalse(isAppInstalledForUser(TEST_APP_PACKAGE, secondaryUser))
        // Install test package as an existing package
        startTimeMillisForSecondaryUser = System.currentTimeMillis()
        installExistingPackageAsUser(TEST_APP_PACKAGE, secondaryUser)
        assertTrue(isAppInstalledForUser(TEST_APP_PACKAGE, secondaryUser))
        firstInstallTimeForPrimaryUser = getFirstInstallTimeAsUser(TEST_APP_PACKAGE, primaryUser)
        // firstInstallTime still remains unchanged for the current user
        assertEquals(origFirstInstallTimeForPrimaryUser, firstInstallTimeForPrimaryUser)
        firstInstallTimeForSecondaryUser =
            getFirstInstallTimeAsUser(TEST_APP_PACKAGE, secondaryUser)
        // firstInstallTime for the other user should be different
        assertNotEquals(firstInstallTimeForPrimaryUser, firstInstallTimeForSecondaryUser)
        assertTrue(startTimeMillisForSecondaryUser < firstInstallTimeForSecondaryUser)
        assertTrue(System.currentTimeMillis() > firstInstallTimeForSecondaryUser)

        // Uninstall for all users
        uninstallPackageSilently(TEST_APP_PACKAGE)
        assertFalse(isAppInstalledForUser(TEST_APP_PACKAGE, primaryUser))
        assertFalse(isAppInstalledForUser(TEST_APP_PACKAGE, secondaryUser))
        // Reinstall for all users
        installPackage(TEST_HW5, installTypeString)
        assertTrue(isAppInstalledForUser(TEST_APP_PACKAGE, primaryUser))
        assertTrue(isAppInstalledForUser(TEST_APP_PACKAGE, secondaryUser))
        firstInstallTimeForPrimaryUser = getFirstInstallTimeAsUser(TEST_APP_PACKAGE, primaryUser)
        // First install time is now different because the package was fully uninstalled
        assertNotEquals(origFirstInstallTimeForPrimaryUser, firstInstallTimeForPrimaryUser)
        firstInstallTimeForSecondaryUser =
            getFirstInstallTimeAsUser(TEST_APP_PACKAGE, secondaryUser)
        // Same firstInstallTime because package was installed for both users at the same time
        assertEquals(firstInstallTimeForPrimaryUser, firstInstallTimeForSecondaryUser)
    }

    @Test
    fun testPackageFullyRemovedBroadcastAfterUninstall(
        @StringTestParameter(
            "install",
            "install-streaming",
            "install-incremental"
        ) installTypeString: String
    ) {
        if (skipTheInstallType(installTypeString)) {
            return
        }
        if (!backgroundThread.isAlive) {
            backgroundThread.start()
        }
        val backgroundHandler = Handler(backgroundThread.getLooper())
        installExistingPackageAsUser(context.packageName, secondaryUser)
        installPackage(TEST_HW5, installTypeString)
        assertTrue(isAppInstalledForUser(context.packageName, primaryUser))
        assertTrue(isAppInstalledForUser(context.packageName, secondaryUser))
        assertTrue(isAppInstalledForUser(TEST_APP_PACKAGE, primaryUser))
        assertTrue(isAppInstalledForUser(TEST_APP_PACKAGE, secondaryUser))
        val broadcastReceiverForPrimaryUser =
            FullyRemovedBroadcastReceiver(TEST_APP_PACKAGE, primaryUser.id())
        val broadcastReceiverForSecondaryUser =
            FullyRemovedBroadcastReceiver(TEST_APP_PACKAGE, secondaryUser.id())
        val intentFilter = IntentFilter()
        intentFilter.addAction(Intent.ACTION_PACKAGE_FULLY_REMOVED)
        intentFilter.addDataScheme("package")
        uiAutomation.adoptShellPermissionIdentity(
            Manifest.permission.INTERACT_ACROSS_USERS,
            Manifest.permission.INTERACT_ACROSS_USERS_FULL
        )
        val contextPrimaryUser = context.createContextAsUser(primaryUser.userHandle(), 0)
        val contextSecondaryUser = context.createContextAsUser(secondaryUser.userHandle(), 0)
        try {
            contextPrimaryUser.registerReceiver(
                broadcastReceiverForPrimaryUser,
                intentFilter,
                null,
                backgroundHandler,
                RECEIVER_EXPORTED
            )
            contextSecondaryUser.registerReceiver(
                broadcastReceiverForSecondaryUser,
                intentFilter,
                null,
                backgroundHandler,
                RECEIVER_EXPORTED
            )
        } finally {
            uiAutomation.dropShellPermissionIdentity()
        }
        // Verify that uninstall with "keep data" doesn't send the broadcast
        uninstallPackageWithKeepData(TEST_APP_PACKAGE, secondaryUser)
        broadcastReceiverForSecondaryUser.assertBroadcastNotReceived()
        installExistingPackageAsUser(TEST_APP_PACKAGE, secondaryUser)
        // Verify that uninstall on a specific user only sends the broadcast to the user
        uninstallPackageAsUser(TEST_APP_PACKAGE, secondaryUser)
        broadcastReceiverForSecondaryUser.assertBroadcastReceived()
        broadcastReceiverForPrimaryUser.assertBroadcastNotReceived()
        uninstallPackageSilently(TEST_APP_PACKAGE)
        broadcastReceiverForPrimaryUser.assertBroadcastReceived()
    }

    @Test
    fun testListPackageDefaultAllUsers(
        @StringTestParameter(
            "install",
            "install-streaming",
            "install-incremental"
        ) installTypeString: String
    ) {
        if (skipTheInstallType(installTypeString)) {
            return
        }
        installPackageAsUser(TEST_HW5, primaryUser, installTypeString)
        assertTrue(isAppInstalledForUser(TEST_APP_PACKAGE, primaryUser))
        assertFalse(isAppInstalledForUser(TEST_APP_PACKAGE, secondaryUser))
        var out = SystemUtil.runShellCommand(
                    "pm list packages -U --user ${primaryUser.id()} $TEST_APP_PACKAGE"
                ).replace("\n", "")
        assertTrue(out.split(":").last().split(",").size == 1)
        out = SystemUtil.runShellCommand(
                    "pm list packages -U --user ${secondaryUser.id()} $TEST_APP_PACKAGE"
                ).replace("\n", "")
        assertEquals("", out)
        out = SystemUtil.runShellCommand("pm list packages -U $TEST_APP_PACKAGE")
                .replace("\n", "")
        assertTrue(out.split(":").last().split(",").size == 1)
        installExistingPackageAsUser(TEST_APP_PACKAGE, secondaryUser)
        assertTrue(isAppInstalledForUser(TEST_APP_PACKAGE, primaryUser))
        assertTrue(isAppInstalledForUser(TEST_APP_PACKAGE, secondaryUser))
        out = SystemUtil.runShellCommand("pm list packages -U $TEST_APP_PACKAGE")
                .replace("\n", "")
        assertTrue(out.split(":").last().split(",").size == 2)
        out = SystemUtil.runShellCommand(
                    "pm list packages -U --user ${primaryUser.id()} $TEST_APP_PACKAGE"
                ).replace("\n", "")
        assertTrue(out.split(":").last().split(",").size == 1)
        out = SystemUtil.runShellCommand(
                    "pm list packages -U --user ${secondaryUser.id()} $TEST_APP_PACKAGE"
                ).replace("\n", "")
        assertTrue(out.split(":").last().split(",").size == 1)
    }

    private fun getFirstInstallTimeAsUser(packageName: String, user: UserReference) =
        context.createContextAsUser(user.userHandle(), 0)
            .packageManager
            .getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            .firstInstallTime

    private fun installPackage(baseName: String, installTypeString: String) {
        val file = File(PackageManagerShellCommandTest.createApkPath(baseName))
        assertThat(SystemUtil.runShellCommand("pm $installTypeString -t -g ${file.path}"))
            .isEqualTo("Success\n")
    }

    private fun installExistingPackageAsUser(packageName: String, user: UserReference) {
        val userId = user.id()
        assertThat(SystemUtil.runShellCommand("pm install-existing --user $userId $packageName"))
            .isEqualTo("Package $packageName installed for user: $userId\n")
    }

    private fun installPackageAsUser(
        baseName: String,
        user: UserReference,
        installTypeString: String
    ) {
        val file = File(PackageManagerShellCommandTest.createApkPath(baseName))
        assertThat(
            SystemUtil.runShellCommand(
                "pm $installTypeString -t -g --user ${user.id()} ${file.path}"
            )
        )
            .isEqualTo("Success\n")
    }

    private fun uninstallPackageAsUser(packageName: String, user: UserReference) =
        assertThat(SystemUtil.runShellCommand("pm uninstall --user ${user.id()} $packageName"))
            .isEqualTo("Success\n")

    private fun uninstallPackageWithKeepData(packageName: String, user: UserReference) =
        SystemUtil.runShellCommand("pm uninstall -k --user ${user.id()} $packageName")

    private fun uninstallPackageSilently(packageName: String) =
        SystemUtil.runShellCommand("pm uninstall $packageName")

    private fun isAppInstalledForUser(packageName: String, user: UserReference) =
        SystemUtil.runShellCommand("pm list packages --user ${user.id()} $packageName")
            .split("\\r?\\n".toRegex())
            .any { it == "package:$packageName" }

    private fun setSystemProperty(name: String, value: String) =
        assertThat(SystemUtil.runShellCommand("setprop $name $value"))
            .isEmpty()
}
