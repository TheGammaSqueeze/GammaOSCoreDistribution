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

package com.android.permissioncontroller.permissionui.ui.handheld

import android.Manifest.permission.ACCESS_COARSE_LOCATION
import android.content.Intent
import android.permission.cts.PermissionUtils.grantPermission
import android.permission.cts.PermissionUtils.install
import android.permission.cts.PermissionUtils.revokePermission
import android.permission.cts.PermissionUtils.uninstallApp
import android.support.test.uiautomator.By
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.compatibility.common.util.SystemUtil.eventually
import com.android.compatibility.common.util.SystemUtil.getEventually
import com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity
import com.android.compatibility.common.util.UiAutomatorUtils.waitFindObject
import com.android.permissioncontroller.permissionui.getUsageCountsFromUi
import com.android.permissioncontroller.permissionui.wakeUpScreen
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Simple tests for {@link ManageStandardPermissionsFragment}
 */
@RunWith(AndroidJUnit4::class)
class ManageStandardPermissionsFragmentTest : BaseHandheldPermissionUiTest() {
    private val LOCATION_USER_APK =
        "/data/local/tmp/permissioncontroller/tests/permissionui/AppThatRequestsLocation.apk"
    private val ADDITIONAL_DEFINER_APK =
        "/data/local/tmp/permissioncontroller/tests/permissionui/" +
            "PermissionUiDefineAdditionalPermissionApp.apk"
    private val ADDITIONAL_USER_APK =
        "/data/local/tmp/permissioncontroller/tests/permissionui/" +
            "PermissionUiUseAdditionalPermissionApp.apk"
    private val LOCATION_USER_PKG = "android.permission.cts.appthatrequestpermission"
    private val ADDITIONAL_DEFINER_PKG =
        "com.android.permissioncontroller.tests.appthatdefinespermission"
    private val ADDITIONAL_USER_PKG =
        "com.android.permissioncontroller.tests.appthatrequestpermission"
    private val ADDITIONAL_PERMISSIONS_LABEL = "Additional permissions"
    private val ADDITIONAL_PERMISSIONS_SUMMARY = "more"

    private val locationGroupLabel = "Location"

    /**
     * Read the number of additional permissions from the Ui.
     *
     * @return number of additional permissions
     */
    private fun getAdditionalPermissionCount(): Int {
        waitFindObject(By.textContains(ADDITIONAL_PERMISSIONS_LABEL))

        val additionalPermissionsSummaryText = waitFindObject(By
            .textContains(ADDITIONAL_PERMISSIONS_SUMMARY)).getText()

        // Matches a single number out of the summary line, i.e. "...3..." -> "3"
        return getEventually {
            Regex("^[^\\d]*(\\d+)[^\\d]*\$")
                .find(additionalPermissionsSummaryText)!!.groupValues[1]
                .toInt()
        }
    }

    @Before
    fun setup() {
        wakeUpScreen()

        runWithShellPermissionIdentity {
            instrumentationContext.startActivity(Intent(Intent.ACTION_MANAGE_PERMISSIONS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }

        // Sleep before each test for 1 second for getUsageCountsFromUi to get the correct counts
        Thread.sleep(1000)
    }

    @Test
    fun groupSummaryGetsUpdatedWhenAppGetsInstalled() {
        val original = getUsageCountsFromUi(locationGroupLabel)

        install(LOCATION_USER_APK)
        eventually {
            val afterInstall = getUsageCountsFromUi(locationGroupLabel)
            assertThat(afterInstall.granted).isEqualTo(original.granted)
            assertThat(afterInstall.total).isEqualTo(original.total + 1)
        }
    }

    @Test
    fun groupSummaryGetsUpdatedWhenAppGetsUninstalled() {
        val original = getUsageCountsFromUi(locationGroupLabel)

        install(LOCATION_USER_APK)
        eventually {
            assertThat(getUsageCountsFromUi(locationGroupLabel)).isNotEqualTo(original)
        }

        uninstallApp(LOCATION_USER_PKG)
        eventually {
            assertThat(getUsageCountsFromUi(locationGroupLabel)).isEqualTo(original)
        }
    }

    @Test
    fun groupSummaryGetsUpdatedWhenPermissionGetsGranted() {
        val original = getUsageCountsFromUi(locationGroupLabel)

        install(LOCATION_USER_APK)
        eventually {
            assertThat(getUsageCountsFromUi(locationGroupLabel).total)
                .isEqualTo(original.total + 1)
        }

        grantPermission(LOCATION_USER_PKG, ACCESS_COARSE_LOCATION)
        eventually {
            assertThat(getUsageCountsFromUi(locationGroupLabel).granted)
                .isEqualTo(original.granted + 1)
        }
    }

    @Test
    fun groupSummaryGetsUpdatedWhenPermissionGetsRevoked() {
        val original = getUsageCountsFromUi(locationGroupLabel)

        install(LOCATION_USER_APK)
        grantPermission(LOCATION_USER_PKG, ACCESS_COARSE_LOCATION)
        eventually {
            assertThat(getUsageCountsFromUi(locationGroupLabel).total)
                .isNotEqualTo(original.total)
            assertThat(getUsageCountsFromUi(locationGroupLabel).granted)
                .isNotEqualTo(original.granted)
        }

        revokePermission(LOCATION_USER_PKG, ACCESS_COARSE_LOCATION)
        eventually {
            assertThat(getUsageCountsFromUi(locationGroupLabel).granted)
                .isEqualTo(original.granted)
        }
    }

    @Test
    fun additionalPermissionSummaryGetUpdateWhenAppGetsInstalled() {
        val additionalPermissionBefore = getAdditionalPermissionCount()

        install(ADDITIONAL_DEFINER_APK)
        install(ADDITIONAL_USER_APK)
        eventually {
            assertThat(getAdditionalPermissionCount())
                .isEqualTo(additionalPermissionBefore + 1)
        }
    }

    @Test
    fun additionalPermissionSummaryGetUpdateWhenUserGetsUninstalled() {
        val additionalPermissionBefore = getAdditionalPermissionCount()

        install(ADDITIONAL_DEFINER_APK)
        install(ADDITIONAL_USER_APK)
        eventually {
            assertThat(getAdditionalPermissionCount())
                .isNotEqualTo(additionalPermissionBefore)
        }

        uninstallApp(ADDITIONAL_USER_PKG)
        eventually {
            assertThat(getAdditionalPermissionCount()).isEqualTo(additionalPermissionBefore)
        }
    }

    @Test
    fun additionalPermissionSummaryGetUpdateWhenDefinerGetsUninstalled() {
        val additionalPermissionBefore = getAdditionalPermissionCount()

        install(ADDITIONAL_DEFINER_APK)
        install(ADDITIONAL_USER_APK)
        eventually {
            assertThat(getAdditionalPermissionCount())
                .isNotEqualTo(additionalPermissionBefore)
        }

        uninstallApp(ADDITIONAL_DEFINER_PKG)
        eventually {
            assertThat(getAdditionalPermissionCount()).isEqualTo(additionalPermissionBefore)
        }
    }

    @After
    fun tearDown() {
        uninstallApp(LOCATION_USER_PKG)
        uninstallApp(ADDITIONAL_DEFINER_PKG)
        uninstallApp(ADDITIONAL_USER_PKG)

        uiDevice.pressBack()
    }
}
