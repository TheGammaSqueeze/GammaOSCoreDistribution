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

package android.safetycenter.cts

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build.VERSION_CODES.TIRAMISU
import android.safetycenter.SafetyCenterIssue
import android.safetycenter.cts.testing.EqualsHashCodeToStringTester
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.ext.truth.os.ParcelableSubject.assertThat
import androidx.test.filters.SdkSuppress
import com.google.common.truth.Truth.assertThat
import kotlin.test.assertFailsWith
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = TIRAMISU, codeName = "Tiramisu")
class SafetyCenterIssueTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private val pendingIntent1 =
        PendingIntent.getActivity(context, 0, Intent("Fake Data"), PendingIntent.FLAG_IMMUTABLE)
    private val pendingIntent2 =
        PendingIntent.getActivity(
            context, 0, Intent("Fake Different Data"), PendingIntent.FLAG_IMMUTABLE)

    private val action1 =
        SafetyCenterIssue.Action.Builder("action_id_1", "an action", pendingIntent1)
            .setWillResolve(true)
            .setIsInFlight(true)
            .setSuccessMessage("a success message")
            .build()
    private val action2 =
        SafetyCenterIssue.Action.Builder("action_id_2", "another action", pendingIntent2)
            .setWillResolve(false)
            .setIsInFlight(false)
            .build()

    private val issue1 =
        SafetyCenterIssue.Builder("issue_id", "Everything's good", "Please acknowledge this")
            .setSubtitle("In the neighborhood")
            .setSeverityLevel(SafetyCenterIssue.ISSUE_SEVERITY_LEVEL_OK)
            .setDismissible(true)
            .setShouldConfirmDismissal(true)
            .setActions(listOf(action1))
            .build()

    private val issueWithRequiredFieldsOnly =
        SafetyCenterIssue.Builder("issue_id", "Everything's good", "Please acknowledge this")
            .setSeverityLevel(SafetyCenterIssue.ISSUE_SEVERITY_LEVEL_OK)
            .build()

    @Test
    fun getId_returnsId() {
        assertThat(SafetyCenterIssue.Builder(issue1).setId("an id").build().id).isEqualTo("an id")
        assertThat(SafetyCenterIssue.Builder(issue1).setId("another id").build().id)
            .isEqualTo("another id")
    }

    @Test
    fun getTitle_returnsTitle() {
        assertThat(SafetyCenterIssue.Builder(issue1).setTitle("a title").build().title)
            .isEqualTo("a title")
        assertThat(SafetyCenterIssue.Builder(issue1).setTitle("another title").build().title)
            .isEqualTo("another title")
    }

    @Test
    fun getSubtitle_returnsSubtitle() {
        assertThat(SafetyCenterIssue.Builder(issue1).setSubtitle("a subtitle").build().subtitle)
            .isEqualTo("a subtitle")
        assertThat(
                SafetyCenterIssue.Builder(issue1).setSubtitle("another subtitle").build().subtitle)
            .isEqualTo("another subtitle")
    }

    @Test
    fun getSummary_returnsSummary() {
        assertThat(SafetyCenterIssue.Builder(issue1).setSummary("a summary").build().summary)
            .isEqualTo("a summary")
        assertThat(SafetyCenterIssue.Builder(issue1).setSummary("another summary").build().summary)
            .isEqualTo("another summary")
    }

    @Test
    fun getSeverityLevel_returnsSeverityLevel() {
        assertThat(
                SafetyCenterIssue.Builder(issue1)
                    .setSeverityLevel(SafetyCenterIssue.ISSUE_SEVERITY_LEVEL_RECOMMENDATION)
                    .build()
                    .severityLevel)
            .isEqualTo(SafetyCenterIssue.ISSUE_SEVERITY_LEVEL_RECOMMENDATION)
        assertThat(
                SafetyCenterIssue.Builder(issue1)
                    .setSeverityLevel(SafetyCenterIssue.ISSUE_SEVERITY_LEVEL_CRITICAL_WARNING)
                    .build()
                    .severityLevel)
            .isEqualTo(SafetyCenterIssue.ISSUE_SEVERITY_LEVEL_CRITICAL_WARNING)
    }

    @Test
    fun isDismissible_returnsIsDismissible() {
        assertThat(SafetyCenterIssue.Builder(issue1).setDismissible(true).build().isDismissible)
            .isTrue()
        assertThat(SafetyCenterIssue.Builder(issue1).setDismissible(false).build().isDismissible)
            .isFalse()
    }

    @Test
    fun isDismissible_defaultsToTrue() {
        assertThat(issueWithRequiredFieldsOnly.isDismissible).isTrue()
    }

    @Test
    fun shouldConfirmDismissal_returnsShouldConfirmDismissal() {
        assertThat(
                SafetyCenterIssue.Builder(issue1)
                    .setShouldConfirmDismissal(true)
                    .build()
                    .shouldConfirmDismissal())
            .isTrue()
        assertThat(
                SafetyCenterIssue.Builder(issue1)
                    .setShouldConfirmDismissal(false)
                    .build()
                    .shouldConfirmDismissal())
            .isFalse()
    }

    @Test
    fun shouldConfirmDismissal_defaultsToTrue() {
        assertThat(issueWithRequiredFieldsOnly.shouldConfirmDismissal()).isTrue()
    }

    @Test
    fun getActions_returnsActions() {
        assertThat(
                SafetyCenterIssue.Builder(issue1)
                    .setActions(listOf(action1, action2))
                    .build()
                    .actions)
            .containsExactly(action1, action2)
            .inOrder()
        assertThat(SafetyCenterIssue.Builder(issue1).setActions(listOf(action2)).build().actions)
            .containsExactly(action2)
        assertThat(SafetyCenterIssue.Builder(issue1).setActions(listOf()).build().actions).isEmpty()
    }

    @Test
    fun getActions_mutationsAreNotAllowed() {
        val mutatedActions = issue1.actions

        assertFailsWith(UnsupportedOperationException::class) { mutatedActions.add(action2) }
    }

    @Test
    fun build_withInvalidIssueSeverityLevel_throwsIllegalArgumentException() {
        val exception =
            assertFailsWith(IllegalArgumentException::class) {
                SafetyCenterIssue.Builder(issue1).setSeverityLevel(-1)
            }

        assertThat(exception)
            .hasMessageThat()
            .isEqualTo("Unexpected IssueSeverityLevel for SafetyCenterIssue: -1")
    }

    @Test
    fun describeContents_returns0() {
        assertThat(issue1.describeContents()).isEqualTo(0)
        assertThat(issueWithRequiredFieldsOnly.describeContents()).isEqualTo(0)
    }

    @Test
    fun parcelRoundTrip_recreatesEqual() {
        assertThat(issue1).recreatesEqual(SafetyCenterIssue.CREATOR)
        assertThat(issueWithRequiredFieldsOnly).recreatesEqual(SafetyCenterIssue.CREATOR)
    }

    @Test
    fun equalsHashCodeToString_usingEqualsHashCodeToStringTester() {
        EqualsHashCodeToStringTester()
            .addEqualityGroup(issue1, SafetyCenterIssue.Builder(issue1).build())
            .addEqualityGroup(issueWithRequiredFieldsOnly)
            .addEqualityGroup(
                SafetyCenterIssue.Builder("an id", "a title", "Please acknowledge this")
                    .setSubtitle("In the neighborhood")
                    .setSeverityLevel(SafetyCenterIssue.ISSUE_SEVERITY_LEVEL_OK)
                    .setActions(listOf(action1))
                    .build(),
                SafetyCenterIssue.Builder("an id", "a title", "Please acknowledge this")
                    .setSubtitle("In the neighborhood")
                    .setSeverityLevel(SafetyCenterIssue.ISSUE_SEVERITY_LEVEL_OK)
                    .setActions(listOf(action1))
                    .build())
            .addEqualityGroup(SafetyCenterIssue.Builder(issue1).setId("a different id").build())
            .addEqualityGroup(
                SafetyCenterIssue.Builder(issue1).setTitle("a different title").build())
            .addEqualityGroup(
                SafetyCenterIssue.Builder(issue1).setSubtitle("a different subtitle").build())
            .addEqualityGroup(
                SafetyCenterIssue.Builder(issue1).setSummary("a different summary").build())
            .addEqualityGroup(
                SafetyCenterIssue.Builder(issue1)
                    .setSeverityLevel(SafetyCenterIssue.ISSUE_SEVERITY_LEVEL_CRITICAL_WARNING)
                    .build())
            .addEqualityGroup(SafetyCenterIssue.Builder(issue1).setDismissible(false).build())
            .addEqualityGroup(
                SafetyCenterIssue.Builder(issue1).setShouldConfirmDismissal(false).build())
            .addEqualityGroup(SafetyCenterIssue.Builder(issue1).setActions(listOf(action2)).build())
            .test()
    }

    @Test
    fun action_getId_returnsId() {
        assertThat(action1.id).isEqualTo("action_id_1")
        assertThat(action2.id).isEqualTo("action_id_2")
    }

    @Test
    fun action_getLabel_returnsLabel() {
        assertThat(action1.label).isEqualTo("an action")
        assertThat(action2.label).isEqualTo("another action")
    }

    @Test
    fun action_getPendingIntent_returnsPendingIntent() {
        assertThat(action1.pendingIntent).isEqualTo(pendingIntent1)
        assertThat(action2.pendingIntent).isEqualTo(pendingIntent2)
    }

    @Test
    fun action_willResolve_returnsWillResolve() {
        assertThat(action1.willResolve()).isTrue()
        assertThat(action2.willResolve()).isFalse()
    }

    @Test
    fun action_isInFlight_returnsIsInFlight() {
        assertThat(action1.isInFlight).isTrue()
        assertThat(action2.isInFlight).isFalse()
    }

    @Test
    fun action_getSuccessMessage_returnsSuccessMessage() {
        assertThat(action1.successMessage).isEqualTo("a success message")
        assertThat(action2.successMessage).isNull()
    }

    @Test
    fun action_describeContents_returns0() {
        assertThat(action1.describeContents()).isEqualTo(0)
        assertThat(action2.describeContents()).isEqualTo(0)
    }

    @Test
    fun action_parcelRoundTrip_recreatesEqual() {
        assertThat(action1).recreatesEqual(SafetyCenterIssue.Action.CREATOR)
        assertThat(action2).recreatesEqual(SafetyCenterIssue.Action.CREATOR)
    }

    @Test
    fun action_equalsHashCodeToString_usingEqualsHashCodeToStringTester() {
        EqualsHashCodeToStringTester()
            .addEqualityGroup(action1)
            .addEqualityGroup(action2)
            .addEqualityGroup(
                SafetyCenterIssue.Action.Builder("an_id", "a label", pendingIntent1)
                    .setWillResolve(true)
                    .setIsInFlight(true)
                    .setSuccessMessage("a success message")
                    .build(),
                SafetyCenterIssue.Action.Builder("an_id", "a label", pendingIntent1)
                    .setWillResolve(true)
                    .setIsInFlight(true)
                    .setSuccessMessage("a success message")
                    .build())
            .addEqualityGroup(
                SafetyCenterIssue.Action.Builder("an_id", "a label", pendingIntent1)
                    .setSuccessMessage("a success message")
                    .build())
            .addEqualityGroup(
                SafetyCenterIssue.Action.Builder("a_different_id", "a label", pendingIntent1)
                    .setSuccessMessage("a success message")
                    .build())
            .addEqualityGroup(
                SafetyCenterIssue.Action.Builder("an_id", "a different label", pendingIntent1)
                    .setSuccessMessage("a success message")
                    .build())
            .addEqualityGroup(
                SafetyCenterIssue.Action.Builder("an_id", "a label", pendingIntent2)
                    .setSuccessMessage("a success message")
                    .build())
            .addEqualityGroup(
                SafetyCenterIssue.Action.Builder("an_id", "a label", pendingIntent1)
                    .setWillResolve(true)
                    .setSuccessMessage("a success message")
                    .build())
            .addEqualityGroup(
                SafetyCenterIssue.Action.Builder("an_id", "a label", pendingIntent1)
                    .setIsInFlight(true)
                    .setSuccessMessage("a success message")
                    .build())
            .addEqualityGroup(
                SafetyCenterIssue.Action.Builder("an_id", "a label", pendingIntent1)
                    .setSuccessMessage("a different success message")
                    .build())
            .addEqualityGroup(
                SafetyCenterIssue.Action.Builder("an_id", "a label", pendingIntent1)
                    .setId("another_id")
                    .setLabel("another_label")
                    .setPendingIntent(pendingIntent2)
                    .build())
            .test()
    }
}
