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
import android.content.Context
import android.content.Intent
import android.os.Build.VERSION_CODES.TIRAMISU
import android.safetycenter.SafetyCenterData
import android.safetycenter.SafetyCenterEntry
import android.safetycenter.SafetyCenterEntryGroup
import android.safetycenter.SafetyCenterEntryOrGroup
import android.safetycenter.SafetyCenterIssue
import android.safetycenter.SafetyCenterStaticEntry
import android.safetycenter.SafetyCenterStaticEntryGroup
import android.safetycenter.SafetyCenterStatus
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
class SafetyCenterDataTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private val pendingIntent =
        PendingIntent.getActivity(context, 0, Intent("Fake Data"), PendingIntent.FLAG_IMMUTABLE)

    private val status1 =
        SafetyCenterStatus.Builder("This is my title", "This is my summary")
            .setSeverityLevel(SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_RECOMMENDATION)
            .build()
    private val status2 =
        SafetyCenterStatus.Builder("This is also my title", "This is also my summary")
            .setSeverityLevel(SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_OK)
            .build()

    private val issue1 =
        SafetyCenterIssue.Builder("iSsUe_iD_oNe", "An issue title", "An issue summary")
            .setSeverityLevel(SafetyCenterIssue.ISSUE_SEVERITY_LEVEL_OK)
            .build()
    private val issue2 =
        SafetyCenterIssue.Builder("iSsUe_iD_tWo", "Another issue title", "Another issue summary")
            .setSeverityLevel(SafetyCenterIssue.ISSUE_SEVERITY_LEVEL_RECOMMENDATION)
            .build()

    private val entry1 =
        SafetyCenterEntry.Builder("eNtRy_iD_OnE", "An entry title")
            .setPendingIntent(pendingIntent)
            .setSeverityLevel(SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_OK)
            .build()
    private val entry2 =
        SafetyCenterEntry.Builder("eNtRy_iD_TwO", "Another entry title")
            .setPendingIntent(pendingIntent)
            .setSeverityLevel(SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_RECOMMENDATION)
            .build()

    private val entryGroup1 =
        SafetyCenterEntryGroup.Builder("eNtRy_gRoUp_iD", "An entry group title")
            .setSeverityLevel(SafetyCenterEntry.ENTRY_SEVERITY_LEVEL_RECOMMENDATION)
            .setEntries(listOf(entry2))
            .build()

    private val entryOrGroup1 = SafetyCenterEntryOrGroup(entry1)
    private val entryOrGroup2 = SafetyCenterEntryOrGroup(entryGroup1)

    private val staticEntry1 =
        SafetyCenterStaticEntry.Builder("A static entry title")
            .setSummary("A static entry summary")
            .setPendingIntent(pendingIntent)
            .build()
    private val staticEntry2 =
        SafetyCenterStaticEntry.Builder("Another static entry title")
            .setSummary("Another static entry summary")
            .setPendingIntent(pendingIntent)
            .build()

    private val staticEntryGroup1 =
        SafetyCenterStaticEntryGroup("A static entry group title", listOf(staticEntry1))
    private val staticEntryGroup2 =
        SafetyCenterStaticEntryGroup("Another static entry group title", listOf(staticEntry2))

    private val data1 =
        SafetyCenterData(status1, listOf(issue1), listOf(entryOrGroup1), listOf(staticEntryGroup1))
    private val data2 =
        SafetyCenterData(status2, listOf(issue2), listOf(entryOrGroup2), listOf(staticEntryGroup2))

    @Test
    fun getStatus_returnsStatus() {
        assertThat(data1.status).isEqualTo(status1)
        assertThat(data2.status).isEqualTo(status2)
    }

    @Test
    fun getIssues_returnsIssues() {
        assertThat(data1.issues).containsExactly(issue1)
        assertThat(data2.issues).containsExactly(issue2)
    }

    @Test
    fun getIssues_mutationsAreNotAllowed() {
        val mutatedIssues = data1.issues

        assertFailsWith(UnsupportedOperationException::class) { mutatedIssues.add(issue2) }
    }

    @Test
    fun getEntriesOrGroups_returnsEntriesOrGroups() {
        assertThat(data1.entriesOrGroups).containsExactly(entryOrGroup1)
        assertThat(data2.entriesOrGroups).containsExactly(entryOrGroup2)
    }

    @Test
    fun getEntriesOrGroups_mutationsAreNotAllowed() {
        val mutatedEntriesOrGroups = data1.entriesOrGroups

        assertFailsWith(UnsupportedOperationException::class) {
            mutatedEntriesOrGroups.add(entryOrGroup2)
        }
    }

    @Test
    fun getStaticEntryGroups_returnsStaticEntryGroups() {
        assertThat(data1.staticEntryGroups).containsExactly(staticEntryGroup1)
        assertThat(data2.staticEntryGroups).containsExactly(staticEntryGroup2)
    }

    @Test
    fun getStaticEntryGroups_mutationsAreNotAllowed() {
        val mutatedStaticEntryGroups = data1.staticEntryGroups

        assertFailsWith(UnsupportedOperationException::class) {
            mutatedStaticEntryGroups.add(staticEntryGroup2)
        }
    }

    @Test
    fun describeContents_returns0() {
        assertThat(data1.describeContents()).isEqualTo(0)
        assertThat(data2.describeContents()).isEqualTo(0)
    }

    @Test
    fun parcelRoundTrip_recreatesEqual() {
        assertThat(data1).recreatesEqual(SafetyCenterData.CREATOR)
        assertThat(data2).recreatesEqual(SafetyCenterData.CREATOR)
    }

    @Test
    fun equalsHashCodeToString_usingEqualsHashCodeToStringTester() {
        EqualsHashCodeToStringTester()
            .addEqualityGroup(
                data1,
                SafetyCenterData(
                    status1, listOf(issue1), listOf(entryOrGroup1), listOf(staticEntryGroup1)))
            .addEqualityGroup(
                data2,
                SafetyCenterData(
                    status2, listOf(issue2), listOf(entryOrGroup2), listOf(staticEntryGroup2)))
            .addEqualityGroup(
                SafetyCenterData(status1, listOf(), listOf(), listOf()),
                SafetyCenterData(status1, listOf(), listOf(), listOf()))
            .addEqualityGroup(
                SafetyCenterData(
                    status2, listOf(issue1), listOf(entryOrGroup1), listOf(staticEntryGroup1)))
            .addEqualityGroup(
                SafetyCenterData(
                    status1, listOf(issue2), listOf(entryOrGroup1), listOf(staticEntryGroup1)))
            .addEqualityGroup(
                SafetyCenterData(
                    status1, listOf(issue1), listOf(entryOrGroup2), listOf(staticEntryGroup1)))
            .addEqualityGroup(
                SafetyCenterData(
                    status1, listOf(issue1), listOf(entryOrGroup1), listOf(staticEntryGroup2)))
            .test()
    }
}
