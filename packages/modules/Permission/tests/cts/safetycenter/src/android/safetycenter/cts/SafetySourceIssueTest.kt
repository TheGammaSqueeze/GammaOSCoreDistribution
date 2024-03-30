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

package android.safetycenter.cts

import android.app.PendingIntent
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.content.Context
import android.content.Intent
import android.os.Build.VERSION_CODES.TIRAMISU
import android.safetycenter.SafetySourceData.SEVERITY_LEVEL_CRITICAL_WARNING
import android.safetycenter.SafetySourceData.SEVERITY_LEVEL_INFORMATION
import android.safetycenter.SafetySourceData.SEVERITY_LEVEL_UNSPECIFIED
import android.safetycenter.SafetySourceIssue
import android.safetycenter.SafetySourceIssue.Action
import android.safetycenter.SafetySourceIssue.ISSUE_CATEGORY_ACCOUNT
import android.safetycenter.SafetySourceIssue.ISSUE_CATEGORY_DEVICE
import android.safetycenter.SafetySourceIssue.ISSUE_CATEGORY_GENERAL
import android.safetycenter.cts.testing.EqualsHashCodeToStringTester
import android.safetycenter.cts.testing.Generic
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.ext.truth.os.ParcelableSubject.assertThat
import androidx.test.filters.SdkSuppress
import com.google.common.truth.Truth.assertThat
import kotlin.test.assertFailsWith
import org.junit.Test
import org.junit.runner.RunWith

/** CTS tests for [SafetySourceIssue]. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = TIRAMISU, codeName = "Tiramisu")
class SafetySourceIssueTest {
    private val context: Context = getApplicationContext()

    private val pendingIntent1: PendingIntent =
        PendingIntent.getActivity(context, 0, Intent("PendingIntent 1"), FLAG_IMMUTABLE)
    private val action1 = Action.Builder("action_id_1", "Action label 1", pendingIntent1).build()
    private val pendingIntent2: PendingIntent =
        PendingIntent.getActivity(context, 0, Intent("PendingIntent 2"), FLAG_IMMUTABLE)
    private val action2 = Action.Builder("action_id_2", "Action label 2", pendingIntent2).build()
    private val action3 = Action.Builder("action_id_3", "Action label 3", pendingIntent1).build()
    private val pendingIntentService =
        PendingIntent.getService(context, 0, Intent("PendingIntent service"), FLAG_IMMUTABLE)

    @Test
    fun action_getId_returnsId() {
        val action = Action.Builder("action_id", "Action label", pendingIntent1).build()

        assertThat(action.id).isEqualTo("action_id")
    }

    @Test
    fun action_getLabel_returnsLabel() {
        val action = Action.Builder("action_id", "Action label", pendingIntent1).build()

        assertThat(action.label).isEqualTo("Action label")
    }

    @Test
    fun action_willResolve_withDefaultBuilder_returnsFalse() {
        val action = Action.Builder("action_id", "Action label", pendingIntent1).build()

        assertThat(action.willResolve()).isFalse()
    }

    @Test
    fun action_willResolve_whenSetExplicitly_returnsWillResolve() {
        val action =
            Action.Builder("action_id", "Action label", pendingIntent1).setWillResolve(true).build()

        assertThat(action.willResolve()).isTrue()
    }

    @Test
    fun action_getPendingIntent_returnsPendingIntent() {
        val action = Action.Builder("action_id", "Action label", pendingIntent1).build()

        assertThat(action.pendingIntent).isEqualTo(pendingIntent1)
    }

    @Test
    fun action_getSuccessMessage_withDefaultBuilder_returnsNull() {
        val action = Action.Builder("action_id", "Action label", pendingIntent1).build()

        assertThat(action.successMessage).isNull()
    }

    @Test
    fun action_getSuccessMessage_whenSetExplicitly_returnsSuccessMessage() {
        val action =
            Action.Builder("action_id", "Action label", pendingIntent1)
                .setSuccessMessage("Action successfully completed")
                .build()

        assertThat(action.successMessage).isEqualTo("Action successfully completed")
    }

    @Test
    fun action_build_withNullId_throwsNullPointerException() {
        assertFailsWith(NullPointerException::class) {
            Action.Builder(Generic.asNull(), "Action label", pendingIntent1)
        }
    }

    @Test
    fun action_build_withNullLabel_throwsNullPointerException() {
        assertFailsWith(NullPointerException::class) {
            Action.Builder("action_id", Generic.asNull(), pendingIntent1)
        }
    }

    @Test
    fun action_build_withNullPendingIntent_throwsNullPointerException() {
        assertFailsWith(NullPointerException::class) {
            Action.Builder("action_id", "Action label", Generic.asNull())
        }
    }

    @Test
    fun action_describeContents_returns0() {
        val action = Action.Builder("action_id", "Action label", pendingIntent1).build()

        assertThat(action.describeContents()).isEqualTo(0)
    }

    @Test
    fun action_parcelRoundTrip_recreatesEqual() {
        val action =
            Action.Builder("action_id", "Action label", pendingIntent1)
                .setSuccessMessage("Action successfully completed")
                .build()

        assertThat(action).recreatesEqual(Action.CREATOR)
    }

    @Test
    fun action_equalsHashCodeToString_usingEqualsHashCodeToStringTester() {
        EqualsHashCodeToStringTester()
            .addEqualityGroup(
                Action.Builder("action_id", "Action label", pendingIntent1).build(),
                Action.Builder("action_id", "Action label", pendingIntent1).build(),
                Action.Builder("action_id", "Action label", pendingIntent1)
                    .setWillResolve(false)
                    .build())
            .addEqualityGroup(
                Action.Builder("action_id", "Action label", pendingIntent1)
                    .setSuccessMessage("Action successfully completed")
                    .build())
            .addEqualityGroup(
                Action.Builder("action_id", "Other action label", pendingIntent1).build())
            .addEqualityGroup(
                Action.Builder("other_action_id", "Action label", pendingIntent1).build())
            .addEqualityGroup(
                Action.Builder("action_id", "Action label", pendingIntent1)
                    .setWillResolve(true)
                    .build())
            .addEqualityGroup(
                Action.Builder(
                        "action_id",
                        "Action label",
                        PendingIntent.getActivity(
                            context, 0, Intent("Other action PendingIntent"), FLAG_IMMUTABLE))
                    .build())
            .addEqualityGroup(
                Action.Builder("action_id", "Action label", pendingIntent1)
                    .setSuccessMessage("Other action successfully completed")
                    .build())
            .test()
    }

    @Test
    fun getId_returnsId() {
        val safetySourceIssue =
            SafetySourceIssue.Builder(
                    "Issue id",
                    "Issue title",
                    "Issue summary",
                    SEVERITY_LEVEL_INFORMATION,
                    "issue_type_id")
                .addAction(action1)
                .build()

        assertThat(safetySourceIssue.id).isEqualTo("Issue id")
    }

    @Test
    fun getTitle_returnsTitle() {
        val safetySourceIssue =
            SafetySourceIssue.Builder(
                    "Issue id",
                    "Issue title",
                    "Issue summary",
                    SEVERITY_LEVEL_INFORMATION,
                    "issue_type_id")
                .addAction(action1)
                .build()

        assertThat(safetySourceIssue.title).isEqualTo("Issue title")
    }

    @Test
    fun getSubtitle_withDefaultBuilder_returnsNull() {
        val safetySourceIssue =
            SafetySourceIssue.Builder(
                    "Issue id",
                    "Issue title",
                    "Issue summary",
                    SEVERITY_LEVEL_INFORMATION,
                    "issue_type_id")
                .addAction(action1)
                .build()

        assertThat(safetySourceIssue.subtitle).isNull()
    }

    @Test
    fun getSubtitle_whenSetExplicitly_returnsSubtitle() {
        val safetySourceIssue =
            SafetySourceIssue.Builder(
                    "Issue id",
                    "Issue title",
                    "Issue summary",
                    SEVERITY_LEVEL_INFORMATION,
                    "issue_type_id")
                .setSubtitle("Issue subtitle")
                .addAction(action1)
                .build()

        assertThat(safetySourceIssue.subtitle).isEqualTo("Issue subtitle")
    }

    @Test
    fun getSummary_returnsSummary() {
        val safetySourceIssue =
            SafetySourceIssue.Builder(
                    "Issue id",
                    "Issue title",
                    "Issue summary",
                    SEVERITY_LEVEL_INFORMATION,
                    "issue_type_id")
                .addAction(action1)
                .build()

        assertThat(safetySourceIssue.summary).isEqualTo("Issue summary")
    }

    @Test
    fun getSeverityLevel_returnsSeverityLevel() {
        val safetySourceIssue =
            SafetySourceIssue.Builder(
                    "Issue id",
                    "Issue title",
                    "Issue summary",
                    SEVERITY_LEVEL_INFORMATION,
                    "issue_type_id")
                .addAction(action1)
                .build()

        assertThat(safetySourceIssue.severityLevel).isEqualTo(SEVERITY_LEVEL_INFORMATION)
    }

    @Test
    fun getIssueCategory_withDefaultBuilder_returnsGeneralCategory() {
        val safetySourceIssue =
            SafetySourceIssue.Builder(
                    "Issue id",
                    "Issue title",
                    "Issue summary",
                    SEVERITY_LEVEL_INFORMATION,
                    "issue_type_id")
                .addAction(action1)
                .build()

        assertThat(safetySourceIssue.issueCategory).isEqualTo(ISSUE_CATEGORY_GENERAL)
    }

    @Test
    fun getIssueCategory_whenSetExplicitly_returnsIssueCategory() {
        val safetySourceIssue =
            SafetySourceIssue.Builder(
                    "Issue id",
                    "Issue title",
                    "Issue summary",
                    SEVERITY_LEVEL_INFORMATION,
                    "issue_type_id")
                .addAction(action1)
                .setIssueCategory(ISSUE_CATEGORY_DEVICE)
                .build()

        assertThat(safetySourceIssue.issueCategory).isEqualTo(ISSUE_CATEGORY_DEVICE)
    }

    @Test
    fun getActions_returnsActions() {
        val safetySourceIssue =
            SafetySourceIssue.Builder(
                    "Issue id",
                    "Issue title",
                    "Issue summary",
                    SEVERITY_LEVEL_INFORMATION,
                    "issue_type_id")
                .addAction(action1)
                .addAction(action2)
                .build()

        assertThat(safetySourceIssue.actions).containsExactly(action1, action2).inOrder()
    }

    @Test
    fun clearActions_removesAllActions() {
        val safetySourceIssue =
            SafetySourceIssue.Builder(
                    "Issue id",
                    "Issue title",
                    "Issue summary",
                    SEVERITY_LEVEL_INFORMATION,
                    "issue_type_id")
                .addAction(action1)
                .addAction(action2)
                .clearActions()
                .addAction(action3)
                .build()

        assertThat(safetySourceIssue.actions).containsExactly(action3)
    }

    @Test
    fun getOnDismissPendingIntent_withDefaultBuilder_returnsNull() {
        val safetySourceIssue =
            SafetySourceIssue.Builder(
                    "Issue id",
                    "Issue title",
                    "Issue summary",
                    SEVERITY_LEVEL_INFORMATION,
                    "issue_type_id")
                .addAction(action1)
                .build()

        assertThat(safetySourceIssue.onDismissPendingIntent).isNull()
    }

    @Test
    fun getOnDismissPendingIntent_whenSetExplicitly_returnsOnDismissPendingIntent() {
        val safetySourceIssue =
            SafetySourceIssue.Builder(
                    "Issue id",
                    "Issue title",
                    "Issue summary",
                    SEVERITY_LEVEL_INFORMATION,
                    "issue_type_id")
                .addAction(action1)
                .setOnDismissPendingIntent(pendingIntentService)
                .build()

        assertThat(safetySourceIssue.onDismissPendingIntent).isEqualTo(pendingIntentService)
    }

    @Test
    fun getIssueTypeId_returnsIssueTypeId() {
        val safetySourceIssue =
            SafetySourceIssue.Builder(
                    "Issue id",
                    "Issue title",
                    "Issue summary",
                    SEVERITY_LEVEL_INFORMATION,
                    "issue_type_id")
                .addAction(action1)
                .build()

        assertThat(safetySourceIssue.issueTypeId).isEqualTo("issue_type_id")
    }

    @Test
    fun build_withNullId_throwsNullPointerException() {
        assertFailsWith(NullPointerException::class) {
            SafetySourceIssue.Builder(
                Generic.asNull(),
                "Issue title",
                "Issue summary",
                SEVERITY_LEVEL_INFORMATION,
                "issue_type_id")
        }
    }

    @Test
    fun build_withNullTitle_throwsNullPointerException() {
        assertFailsWith(NullPointerException::class) {
            SafetySourceIssue.Builder(
                "Issue id",
                Generic.asNull(),
                "Issue summary",
                SEVERITY_LEVEL_INFORMATION,
                "issue_type_id")
        }
    }

    @Test
    fun build_withNullSummary_throwsNullPointerException() {
        assertFailsWith(NullPointerException::class) {
            SafetySourceIssue.Builder(
                "Issue id",
                "Issue title",
                Generic.asNull(),
                SEVERITY_LEVEL_INFORMATION,
                "issue_type_id")
        }
    }

    @Test
    fun build_withUnspecifiedSeverityLevel_throwsIllegalArgumentException() {
        val exception =
            assertFailsWith(IllegalArgumentException::class) {
                SafetySourceIssue.Builder(
                    "Issue id",
                    "Issue title",
                    "Issue summary",
                    SEVERITY_LEVEL_UNSPECIFIED,
                    "issue_type_id")
            }
        assertThat(exception)
            .hasMessageThat()
            .isEqualTo("SeverityLevel for SafetySourceIssue must not be SEVERITY_LEVEL_UNSPECIFIED")
    }

    @Test
    fun build_withInvalidSeverityLevel_throwsIllegalArgumentException() {
        val exception =
            assertFailsWith(IllegalArgumentException::class) {
                SafetySourceIssue.Builder(
                    "Issue id", "Issue title", "Issue summary", -1, "issue_type_id")
            }
        assertThat(exception)
            .hasMessageThat()
            .isEqualTo("Unexpected SeverityLevel for SafetySourceIssue: -1")
    }

    @Test
    fun build_withNullIssueTypeId_throwsNullPointerException() {
        assertFailsWith(NullPointerException::class) {
            SafetySourceIssue.Builder(
                "Issue id",
                "Issue title",
                "Issue summary",
                SEVERITY_LEVEL_INFORMATION,
                Generic.asNull())
        }
    }

    @Test
    fun build_withInvalidIssueCategory_throwsIllegalArgumentException() {
        val builder =
            SafetySourceIssue.Builder(
                "Issue id",
                "Issue title",
                "Issue summary",
                SEVERITY_LEVEL_INFORMATION,
                "issue_type_id")
        val exception =
            assertFailsWith(IllegalArgumentException::class) { builder.setIssueCategory(-1) }
        assertThat(exception)
            .hasMessageThat()
            .isEqualTo("Unexpected IssueCategory for SafetySourceIssue: -1")
    }

    @Test
    fun build_withInvalidOnDismissPendingIntent_throwsIllegalArgumentException() {
        val builder =
            SafetySourceIssue.Builder(
                "Issue id",
                "Issue title",
                "Issue summary",
                SEVERITY_LEVEL_INFORMATION,
                "issue_type_id")
        val exception =
            assertFailsWith(IllegalArgumentException::class) {
                builder.setOnDismissPendingIntent(
                    PendingIntent.getActivity(
                        context,
                        /* requestCode = */ 0,
                        Intent("PendingIntent activity"),
                        FLAG_IMMUTABLE))
            }
        assertThat(exception)
            .hasMessageThat()
            .isEqualTo("Safety source issue on dismiss pending intent must not start an activity")
    }

    @Test
    fun build_withNoActions_throwsIllegalArgumentException() {
        val safetySourceIssueBuilder =
            SafetySourceIssue.Builder(
                "Issue id",
                "Issue title",
                "Issue summary",
                SEVERITY_LEVEL_INFORMATION,
                "issue_type_id")

        val exception =
            assertFailsWith(IllegalArgumentException::class) { safetySourceIssueBuilder.build() }
        assertThat(exception)
            .hasMessageThat()
            .isEqualTo("Safety source issue must contain at least 1 action")
    }

    @Test
    fun build_withMoreThanTwoActions_throwsIllegalArgumentException() {
        val safetySourceIssueBuilder =
            SafetySourceIssue.Builder(
                    "Issue id",
                    "Issue title",
                    "Issue summary",
                    SEVERITY_LEVEL_INFORMATION,
                    "issue_type_id")
                .addAction(action1)
                .addAction(action2)
                .addAction(action3)

        val exception =
            assertFailsWith(IllegalArgumentException::class) { safetySourceIssueBuilder.build() }
        assertThat(exception)
            .hasMessageThat()
            .isEqualTo("Safety source issue must not contain more than 2 actions")
    }

    @Test
    fun describeContents_returns0() {
        val safetySourceIssue =
            SafetySourceIssue.Builder(
                    "Issue id",
                    "Issue title",
                    "Issue summary",
                    SEVERITY_LEVEL_INFORMATION,
                    "issue_type_id")
                .setSubtitle("Issue subtitle")
                .setIssueCategory(ISSUE_CATEGORY_ACCOUNT)
                .addAction(action1)
                .addAction(action2)
                .setOnDismissPendingIntent(pendingIntentService)
                .build()

        assertThat(safetySourceIssue.describeContents()).isEqualTo(0)
    }

    @Test
    fun parcelRoundTrip_recreatesEqual() {
        val safetySourceIssue =
            SafetySourceIssue.Builder(
                    "Issue id",
                    "Issue title",
                    "Issue summary",
                    SEVERITY_LEVEL_INFORMATION,
                    "issue_type_id")
                .setSubtitle("Issue subtitle")
                .setIssueCategory(ISSUE_CATEGORY_ACCOUNT)
                .addAction(action1)
                .addAction(action2)
                .setOnDismissPendingIntent(pendingIntentService)
                .build()

        assertThat(safetySourceIssue).recreatesEqual(SafetySourceIssue.CREATOR)
    }

    @Test
    fun equalsHashCodeToString_usingEqualsHashCodeToStringTester() {
        EqualsHashCodeToStringTester()
            .addEqualityGroup(
                SafetySourceIssue.Builder(
                        "Issue id",
                        "Issue title",
                        "Issue summary",
                        SEVERITY_LEVEL_INFORMATION,
                        "issue_type_id")
                    .setSubtitle("Issue subtitle")
                    .setIssueCategory(ISSUE_CATEGORY_ACCOUNT)
                    .addAction(action1)
                    .addAction(action2)
                    .setOnDismissPendingIntent(pendingIntentService)
                    .build(),
                SafetySourceIssue.Builder(
                        "Issue id",
                        "Issue title",
                        "Issue summary",
                        SEVERITY_LEVEL_INFORMATION,
                        "issue_type_id")
                    .setSubtitle("Issue subtitle")
                    .setIssueCategory(ISSUE_CATEGORY_ACCOUNT)
                    .addAction(action1)
                    .addAction(action2)
                    .setOnDismissPendingIntent(pendingIntentService)
                    .build())
            .addEqualityGroup(
                SafetySourceIssue.Builder(
                        "Issue id",
                        "Issue title",
                        "Issue summary",
                        SEVERITY_LEVEL_INFORMATION,
                        "issue_type_id")
                    .addAction(action1)
                    .build())
            .addEqualityGroup(
                SafetySourceIssue.Builder(
                        "Other issue id",
                        "Issue title",
                        "Issue summary",
                        SEVERITY_LEVEL_INFORMATION,
                        "issue_type_id")
                    .addAction(action1)
                    .build())
            .addEqualityGroup(
                SafetySourceIssue.Builder(
                        "Issue id",
                        "Other issue title",
                        "Issue summary",
                        SEVERITY_LEVEL_INFORMATION,
                        "issue_type_id")
                    .addAction(action1)
                    .build())
            .addEqualityGroup(
                SafetySourceIssue.Builder(
                        "Issue id",
                        "Issue title",
                        "Different issue summary",
                        SEVERITY_LEVEL_INFORMATION,
                        "issue_type_id")
                    .addAction(action1)
                    .build())
            .addEqualityGroup(
                SafetySourceIssue.Builder(
                        "Issue id",
                        "Issue title",
                        "Issue summary",
                        SEVERITY_LEVEL_CRITICAL_WARNING,
                        "issue_type_id")
                    .addAction(action1)
                    .build())
            .addEqualityGroup(
                SafetySourceIssue.Builder(
                        "Issue id",
                        "Issue title",
                        "Issue summary",
                        SEVERITY_LEVEL_INFORMATION,
                        "other_issue_type_id")
                    .addAction(action1)
                    .build())
            .addEqualityGroup(
                SafetySourceIssue.Builder(
                        "Issue id",
                        "Issue title",
                        "Issue summary",
                        SEVERITY_LEVEL_INFORMATION,
                        "issue_type_id")
                    .addAction(action2)
                    .build())
            .addEqualityGroup(
                SafetySourceIssue.Builder(
                        "Issue id",
                        "Issue title",
                        "Issue summary",
                        SEVERITY_LEVEL_INFORMATION,
                        "issue_type_id")
                    .setSubtitle("Other issue subtitle")
                    .setIssueCategory(ISSUE_CATEGORY_ACCOUNT)
                    .addAction(action1)
                    .addAction(action2)
                    .setOnDismissPendingIntent(pendingIntentService)
                    .build())
            .addEqualityGroup(
                SafetySourceIssue.Builder(
                        "Issue id",
                        "Issue title",
                        "Issue summary",
                        SEVERITY_LEVEL_INFORMATION,
                        "issue_type_id")
                    .setSubtitle("Other issue subtitle")
                    .setIssueCategory(ISSUE_CATEGORY_DEVICE)
                    .addAction(action1)
                    .addAction(action2)
                    .setOnDismissPendingIntent(pendingIntentService)
                    .build())
            .addEqualityGroup(
                SafetySourceIssue.Builder(
                        "Issue id",
                        "Issue title",
                        "Issue summary",
                        SEVERITY_LEVEL_INFORMATION,
                        "issue_type_id")
                    .setSubtitle("Other issue subtitle")
                    .setIssueCategory(ISSUE_CATEGORY_DEVICE)
                    .addAction(action2)
                    .addAction(action1)
                    .setOnDismissPendingIntent(pendingIntentService)
                    .build())
            .addEqualityGroup(
                SafetySourceIssue.Builder(
                        "Issue id",
                        "Issue title",
                        "Issue summary",
                        SEVERITY_LEVEL_INFORMATION,
                        "issue_type_id")
                    .setSubtitle("Other issue subtitle")
                    .setIssueCategory(ISSUE_CATEGORY_DEVICE)
                    .addAction(action2)
                    .addAction(action1)
                    .setOnDismissPendingIntent(
                        PendingIntent.getService(
                            context, 0, Intent("Other PendingIntent service"), FLAG_IMMUTABLE))
                    .build())
            .test()
    }
}
