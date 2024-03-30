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

package com.android.permissioncontroller.permissionui.ui

import android.content.Intent
import android.permission.cts.PermissionUtils.install
import android.permission.cts.PermissionUtils.uninstallApp
import android.support.test.uiautomator.By
import com.android.compatibility.common.util.SystemUtil.eventually
import com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity
import com.android.compatibility.common.util.UiAutomatorUtils.waitFindObject
import com.android.compatibility.common.util.UiAutomatorUtils.waitFindObjectOrNull
import com.android.permissioncontroller.permissionui.wakeUpScreen
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.Test

/**
 * Superclass of all tests for {@link PermissionAppsFragmentTest}.
 *
 * <p>Leave abstract to prevent the test runner from trying to run it
 *
 * Currently, none of the tests that extend [PermissionAppsFragmentTest] run on TV.
 * TODO(b/178576541): Adapt and run on TV.
 */
abstract class PermissionAppsFragmentTest(
    val userApk: String,
    val userPkg: String,
    val perm: String,
    val definerApk: String? = null,
    val definerPkg: String? = null
) : BasePermissionUiTest() {

    @Before
    fun assumeNotTelevision() = assumeFalse(isTelevision)

    @Before
    fun wakeScreenUp() {
        wakeUpScreen()
    }

    @Before
    fun installDefinerApk() {
        if (definerApk != null) {
            install(definerApk)
        }
    }

    @Before
    fun startManagePermissionAppsActivity() {
        runWithShellPermissionIdentity {
            instrumentationContext.startActivity(Intent(Intent.ACTION_MANAGE_PERMISSION_APPS)
                .apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    putExtra(Intent.EXTRA_PERMISSION_NAME, perm)
                })
        }
    }

    @Test
    fun appAppearsWhenInstalled() {
        assertNull(waitFindObjectOrNull(By.text(userPkg)))

        install(userApk)
        eventually {
            waitFindObject(By.text(userPkg))
        }
    }

    @Test
    fun appDisappearsWhenUninstalled() {
        assertNull(waitFindObjectOrNull(By.text(userPkg)))

        install(userApk)
        eventually {
            waitFindObject(By.text(userPkg))
        }

        uninstallApp(userPkg)
        eventually {
            assertNull(waitFindObjectOrNull(By.text(userPkg)))
        }
    }

    @After
    fun tearDown() {
        if (definerPkg != null) {
            uninstallApp(definerPkg)
        }
        uninstallApp(userPkg)

        uiDevice.pressBack()
        uiDevice.pressHome()
    }
}
