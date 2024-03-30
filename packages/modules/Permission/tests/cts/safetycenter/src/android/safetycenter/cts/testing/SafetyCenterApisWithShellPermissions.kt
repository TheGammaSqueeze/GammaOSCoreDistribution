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

package android.safetycenter.cts.testing

import android.Manifest.permission.MANAGE_SAFETY_CENTER
import android.Manifest.permission.READ_SAFETY_CENTER_STATUS
import android.Manifest.permission.SEND_SAFETY_CENTER_UPDATE
import android.safetycenter.SafetyCenterData
import android.safetycenter.SafetyCenterManager
import android.safetycenter.SafetyCenterManager.OnSafetyCenterDataChangedListener
import android.safetycenter.SafetyEvent
import android.safetycenter.SafetySourceData
import android.safetycenter.SafetySourceErrorDetails
import android.safetycenter.config.SafetyCenterConfig
import com.android.compatibility.common.util.SystemUtil.callWithShellPermissionIdentity
import java.util.concurrent.Executor

/**
 * Extension methods for [SafetyCenterManager] that delegate to the relevant implementation, but
 * making each call with the appropriate permission.
 */
object SafetyCenterApisWithShellPermissions {

    /**
     * Calls [SafetyCenterManager.isSafetyCenterEnabled] adopting Shell's
     * [READ_SAFETY_CENTER_STATUS] permission.
     */
    fun SafetyCenterManager.isSafetyCenterEnabledWithPermission(): Boolean =
        callWithShellPermissionIdentity({ isSafetyCenterEnabled }, READ_SAFETY_CENTER_STATUS)

    /**
     * Calls [SafetyCenterManager.setSafetySourceData] adopting Shell's [SEND_SAFETY_CENTER_UPDATE]
     * permission.
     */
    fun SafetyCenterManager.setSafetySourceDataWithPermission(
        safetySourceId: String,
        safetySourceData: SafetySourceData?,
        safetyEvent: SafetyEvent
    ) {
        callWithShellPermissionIdentity(
            { setSafetySourceData(safetySourceId, safetySourceData, safetyEvent) },
            SEND_SAFETY_CENTER_UPDATE)
    }

    /**
     * Calls [SafetyCenterManager.getSafetySourceData] adopting Shell's [SEND_SAFETY_CENTER_UPDATE]
     * permission.
     */
    fun SafetyCenterManager.getSafetySourceDataWithPermission(id: String): SafetySourceData? =
        callWithShellPermissionIdentity({ getSafetySourceData(id) }, SEND_SAFETY_CENTER_UPDATE)

    /**
     * Calls [SafetyCenterManager.reportSafetySourceError] adopting Shell's
     * [SEND_SAFETY_CENTER_UPDATE] permission.
     */
    fun SafetyCenterManager.reportSafetySourceErrorWithPermission(
        safetySourceId: String,
        safetySourceErrorDetails: SafetySourceErrorDetails
    ) {
        callWithShellPermissionIdentity(
            { reportSafetySourceError(safetySourceId, safetySourceErrorDetails) },
            SEND_SAFETY_CENTER_UPDATE)
    }

    /**
     * Calls [SafetyCenterManager.refreshSafetySources] adopting Shell's [MANAGE_SAFETY_CENTER]
     * permission.
     */
    fun SafetyCenterManager.refreshSafetySourcesWithPermission(refreshReason: Int) {
        callWithShellPermissionIdentity(
            { refreshSafetySources(refreshReason) }, MANAGE_SAFETY_CENTER)
    }

    /**
     * Calls [SafetyCenterManager.getSafetyCenterConfig] adopting Shell's [MANAGE_SAFETY_CENTER]
     * permission.
     */
    fun SafetyCenterManager.getSafetyCenterConfigWithPermission(): SafetyCenterConfig? =
        callWithShellPermissionIdentity(::getSafetyCenterConfig, MANAGE_SAFETY_CENTER)

    /**
     * Calls [SafetyCenterManager.getSafetyCenterData] adopting Shell's [MANAGE_SAFETY_CENTER]
     * permission.
     */
    fun SafetyCenterManager.getSafetyCenterDataWithPermission(): SafetyCenterData =
        callWithShellPermissionIdentity(::getSafetyCenterData, MANAGE_SAFETY_CENTER)

    /**
     * Calls [SafetyCenterManager.addOnSafetyCenterDataChangedListener] adopting Shell's
     * [MANAGE_SAFETY_CENTER] permission.
     */
    fun SafetyCenterManager.addOnSafetyCenterDataChangedListenerWithPermission(
        executor: Executor,
        listener: OnSafetyCenterDataChangedListener
    ) {
        callWithShellPermissionIdentity(
            { addOnSafetyCenterDataChangedListener(executor, listener) }, MANAGE_SAFETY_CENTER)
    }

    /**
     * Calls [SafetyCenterManager.removeOnSafetyCenterDataChangedListener] adopting Shell's
     * [MANAGE_SAFETY_CENTER] permission.
     */
    fun SafetyCenterManager.removeOnSafetyCenterDataChangedListenerWithPermission(
        listener: OnSafetyCenterDataChangedListener
    ) {
        callWithShellPermissionIdentity(
            { removeOnSafetyCenterDataChangedListener(listener) }, MANAGE_SAFETY_CENTER)
    }

    /**
     * Calls [SafetyCenterManager.dismissSafetyCenterIssue] adopting Shell's [MANAGE_SAFETY_CENTER]
     * permission.
     */
    fun SafetyCenterManager.dismissSafetyCenterIssueWithPermission(safetyCenterIssueId: String) {
        callWithShellPermissionIdentity(
            { dismissSafetyCenterIssue(safetyCenterIssueId) }, MANAGE_SAFETY_CENTER)
    }

    /**
     * Calls [SafetyCenterManager.executeSafetyCenterIssueAction] adopting Shell's
     * [MANAGE_SAFETY_CENTER] permission.
     */
    fun SafetyCenterManager.executeSafetyCenterIssueActionWithPermission(
        safetyCenterIssueId: String,
        safetyCenterIssueActionId: String
    ) {
        callWithShellPermissionIdentity(
            { executeSafetyCenterIssueAction(safetyCenterIssueId, safetyCenterIssueActionId) },
            MANAGE_SAFETY_CENTER)
    }

    /**
     * Calls [SafetyCenterManager.clearAllSafetySourceDataForTests] adopting Shell's
     * [MANAGE_SAFETY_CENTER] permission.
     */
    fun SafetyCenterManager.clearAllSafetySourceDataForTestsWithPermission() =
        callWithShellPermissionIdentity(
            { clearAllSafetySourceDataForTests() }, MANAGE_SAFETY_CENTER)

    /**
     * Calls [SafetyCenterManager.setSafetyCenterConfigForTests] adopting Shell's
     * [MANAGE_SAFETY_CENTER] permission.
     */
    fun SafetyCenterManager.setSafetyCenterConfigForTestsWithPermission(
        safetyCenterConfig: SafetyCenterConfig
    ) {
        callWithShellPermissionIdentity(
            { setSafetyCenterConfigForTests(safetyCenterConfig) }, MANAGE_SAFETY_CENTER)
    }

    /**
     * Calls [SafetyCenterManager.clearSafetyCenterConfigForTests] adopting Shell's
     * [MANAGE_SAFETY_CENTER] permission.
     */
    fun SafetyCenterManager.clearSafetyCenterConfigForTestsWithPermission() {
        callWithShellPermissionIdentity({ clearSafetyCenterConfigForTests() }, MANAGE_SAFETY_CENTER)
    }
}
