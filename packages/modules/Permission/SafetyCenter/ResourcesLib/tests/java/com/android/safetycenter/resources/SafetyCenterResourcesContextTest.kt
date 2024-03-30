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

package com.android.safetycenter.resources

import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SafetyCenterResourcesContextTest {
    private val context: Context = getApplicationContext()

    @Test
    fun validDataWithValidInputs() {
        val safetyCenterResourcesContext = SafetyCenterResourcesContext(
            context,
            RESOURCES_APK_ACTION,
            null,
            CONFIG_NAME,
            0
        )
        assertThat(safetyCenterResourcesContext.resourcesApkPkgName).isEqualTo(
            RESOURCES_APK_PKG_NAME
        )
        val configContent =
            safetyCenterResourcesContext.safetyCenterConfig?.bufferedReader().use { it?.readText() }
        assertThat(configContent).isEqualTo(CONFIG_CONTENT)
        assertNotNull(safetyCenterResourcesContext.assets)
        assertNotNull(safetyCenterResourcesContext.resources)
        assertNotNull(safetyCenterResourcesContext.theme)
    }

    @Test
    fun nullDataWithWrongAction() {
        val safetyCenterResourcesContext = SafetyCenterResourcesContext(
            context,
            "wrong",
            null,
            CONFIG_NAME,
            0
        )
        assertNull(safetyCenterResourcesContext.resourcesApkPkgName)
        assertNull(safetyCenterResourcesContext.safetyCenterConfig)
        assertNull(safetyCenterResourcesContext.assets)
        assertNull(safetyCenterResourcesContext.resources)
        assertNull(safetyCenterResourcesContext.theme)
    }

    @Test
    fun nullDataWithWrongPath() {
        val safetyCenterResourcesContext = SafetyCenterResourcesContext(
            context,
            RESOURCES_APK_ACTION,
            "/apex/com.android.permission",
            CONFIG_NAME,
            0
        )
        assertNull(safetyCenterResourcesContext.resourcesApkPkgName)
        assertNull(safetyCenterResourcesContext.safetyCenterConfig)
        assertNull(safetyCenterResourcesContext.assets)
        assertNull(safetyCenterResourcesContext.resources)
        assertNull(safetyCenterResourcesContext.theme)
    }

    @Test
    fun nullDataWithWrongFlag() {
        val safetyCenterResourcesContext = SafetyCenterResourcesContext(
            context,
            RESOURCES_APK_ACTION,
            null,
            CONFIG_NAME,
            PackageManager.MATCH_SYSTEM_ONLY
        )
        assertNull(safetyCenterResourcesContext.resourcesApkPkgName)
        assertNull(safetyCenterResourcesContext.safetyCenterConfig)
        assertNull(safetyCenterResourcesContext.assets)
        assertNull(safetyCenterResourcesContext.resources)
        assertNull(safetyCenterResourcesContext.theme)
    }

    @Test
    fun nullConfigWithWrongConfigName() {
        val safetyCenterResourcesContext = SafetyCenterResourcesContext(
            context,
            RESOURCES_APK_ACTION,
            null,
            "wrong",
            0
        )
        assertNotNull(safetyCenterResourcesContext.resourcesApkPkgName)
        assertNull(safetyCenterResourcesContext.safetyCenterConfig)
        assertNotNull(safetyCenterResourcesContext.assets)
        assertNotNull(safetyCenterResourcesContext.resources)
        assertNotNull(safetyCenterResourcesContext.theme)
    }

    companion object {
        const val RESOURCES_APK_ACTION =
            "com.android.safetycenter.tests.intent.action.SAFETY_CENTER_TEST_RESOURCES_APK"
        const val RESOURCES_APK_PKG_NAME =
            "com.android.safetycenter.tests.config.safetycenterresourceslibtestresources"
        const val CONFIG_NAME = "test"
        const val CONFIG_CONTENT = "TEST"
    }
}