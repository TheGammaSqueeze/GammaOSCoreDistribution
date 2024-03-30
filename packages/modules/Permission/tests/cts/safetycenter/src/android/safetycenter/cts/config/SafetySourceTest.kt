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

package android.safetycenter.cts.config

import android.content.res.Resources
import android.os.Build.VERSION_CODES.TIRAMISU
import android.safetycenter.config.SafetySource
import android.safetycenter.cts.testing.EqualsHashCodeToStringTester
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.ext.truth.os.ParcelableSubject.assertThat
import androidx.test.filters.SdkSuppress
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

/** CTS tests for [SafetySource]. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = TIRAMISU, codeName = "Tiramisu")
class SafetySourceTest {

    @Test
    fun getType_returnsType() {
        assertThat(DYNAMIC_BAREBONE.type).isEqualTo(SafetySource.SAFETY_SOURCE_TYPE_DYNAMIC)
        assertThat(DYNAMIC_ALL_OPTIONAL.type).isEqualTo(SafetySource.SAFETY_SOURCE_TYPE_DYNAMIC)
        assertThat(DYNAMIC_HIDDEN.type).isEqualTo(SafetySource.SAFETY_SOURCE_TYPE_DYNAMIC)
        assertThat(DYNAMIC_HIDDEN_WITH_SEARCH.type)
            .isEqualTo(SafetySource.SAFETY_SOURCE_TYPE_DYNAMIC)
        assertThat(DYNAMIC_DISABLED.type).isEqualTo(SafetySource.SAFETY_SOURCE_TYPE_DYNAMIC)
        assertThat(STATIC_BAREBONE.type).isEqualTo(SafetySource.SAFETY_SOURCE_TYPE_STATIC)
        assertThat(STATIC_ALL_OPTIONAL.type).isEqualTo(SafetySource.SAFETY_SOURCE_TYPE_STATIC)
        assertThat(ISSUE_ONLY_BAREBONE.type).isEqualTo(SafetySource.SAFETY_SOURCE_TYPE_ISSUE_ONLY)
        assertThat(ISSUE_ONLY_ALL_OPTIONAL.type)
            .isEqualTo(SafetySource.SAFETY_SOURCE_TYPE_ISSUE_ONLY)
    }

    @Test
    fun getId_returnsId() {
        assertThat(DYNAMIC_BAREBONE.id).isEqualTo(DYNAMIC_BAREBONE_ID)
        assertThat(DYNAMIC_ALL_OPTIONAL.id).isEqualTo(DYNAMIC_ALL_OPTIONAL_ID)
        assertThat(DYNAMIC_HIDDEN.id).isEqualTo(DYNAMIC_HIDDEN_ID)
        assertThat(DYNAMIC_HIDDEN_WITH_SEARCH.id).isEqualTo(DYNAMIC_HIDDEN_WITH_SEARCH_ID)
        assertThat(DYNAMIC_DISABLED.id).isEqualTo(DYNAMIC_DISABLED_ID)
        assertThat(STATIC_BAREBONE.id).isEqualTo(STATIC_BAREBONE_ID)
        assertThat(STATIC_ALL_OPTIONAL.id).isEqualTo(STATIC_ALL_OPTIONAL_ID)
        assertThat(ISSUE_ONLY_BAREBONE.id).isEqualTo(ISSUE_ONLY_BAREBONE_ID)
        assertThat(ISSUE_ONLY_ALL_OPTIONAL.id).isEqualTo(ISSUE_ONLY_ALL_OPTIONAL_ID)
    }

    @Test
    fun getPackageName_returnsPackageNameOrThrows() {
        assertThat(DYNAMIC_BAREBONE.packageName).isEqualTo(PACKAGE_NAME)
        assertThat(DYNAMIC_ALL_OPTIONAL.packageName).isEqualTo(PACKAGE_NAME)
        assertThat(DYNAMIC_HIDDEN.packageName).isEqualTo(PACKAGE_NAME)
        assertThat(DYNAMIC_HIDDEN_WITH_SEARCH.packageName).isEqualTo(PACKAGE_NAME)
        assertThat(DYNAMIC_DISABLED.packageName).isEqualTo(PACKAGE_NAME)
        assertThrows(UnsupportedOperationException::class.java) { STATIC_BAREBONE.packageName }
        assertThrows(UnsupportedOperationException::class.java) { STATIC_ALL_OPTIONAL.packageName }
        assertThat(ISSUE_ONLY_BAREBONE.packageName).isEqualTo(PACKAGE_NAME)
        assertThat(ISSUE_ONLY_ALL_OPTIONAL.packageName).isEqualTo(PACKAGE_NAME)
    }

    @Test
    fun getTitleResId_returnsTitleResIdOrThrows() {
        assertThat(DYNAMIC_BAREBONE.titleResId).isEqualTo(REFERENCE_RES_ID)
        assertThat(DYNAMIC_ALL_OPTIONAL.titleResId).isEqualTo(REFERENCE_RES_ID)
        assertThat(DYNAMIC_DISABLED.titleResId).isEqualTo(REFERENCE_RES_ID)
        assertThat(DYNAMIC_HIDDEN.titleResId).isEqualTo(Resources.ID_NULL)
        assertThat(DYNAMIC_HIDDEN_WITH_SEARCH.titleResId).isEqualTo(REFERENCE_RES_ID)
        assertThat(STATIC_BAREBONE.titleResId).isEqualTo(REFERENCE_RES_ID)
        assertThat(STATIC_ALL_OPTIONAL.titleResId).isEqualTo(REFERENCE_RES_ID)
        assertThrows(UnsupportedOperationException::class.java) { ISSUE_ONLY_BAREBONE.titleResId }
        assertThrows(UnsupportedOperationException::class.java) {
            ISSUE_ONLY_ALL_OPTIONAL.titleResId
        }
    }

    @Test
    fun getTitleForWorkResId_returnsTitleForWorkResIdOrThrows() {
        assertThrows(UnsupportedOperationException::class.java) {
            DYNAMIC_BAREBONE.titleForWorkResId
        }
        assertThat(DYNAMIC_ALL_OPTIONAL.titleForWorkResId).isEqualTo(REFERENCE_RES_ID)
        assertThrows(UnsupportedOperationException::class.java) {
            DYNAMIC_DISABLED.titleForWorkResId
        }
        assertThat(DYNAMIC_HIDDEN.titleForWorkResId).isEqualTo(Resources.ID_NULL)
        assertThat(DYNAMIC_HIDDEN_WITH_SEARCH.titleForWorkResId).isEqualTo(REFERENCE_RES_ID)
        assertThrows(UnsupportedOperationException::class.java) {
            STATIC_BAREBONE.titleForWorkResId
        }
        assertThat(STATIC_ALL_OPTIONAL.titleForWorkResId).isEqualTo(REFERENCE_RES_ID)
        assertThrows(UnsupportedOperationException::class.java) {
            ISSUE_ONLY_BAREBONE.titleForWorkResId
        }
        assertThrows(UnsupportedOperationException::class.java) {
            ISSUE_ONLY_ALL_OPTIONAL.titleForWorkResId
        }
    }

    @Test
    fun getSummaryResId_returnsSummaryResIdOrThrows() {
        assertThat(DYNAMIC_BAREBONE.summaryResId).isEqualTo(REFERENCE_RES_ID)
        assertThat(DYNAMIC_ALL_OPTIONAL.summaryResId).isEqualTo(REFERENCE_RES_ID)
        assertThat(DYNAMIC_DISABLED.summaryResId).isEqualTo(REFERENCE_RES_ID)
        assertThat(DYNAMIC_HIDDEN.summaryResId).isEqualTo(Resources.ID_NULL)
        assertThat(DYNAMIC_HIDDEN_WITH_SEARCH.summaryResId).isEqualTo(REFERENCE_RES_ID)
        assertThat(STATIC_BAREBONE.summaryResId).isEqualTo(Resources.ID_NULL)
        assertThat(STATIC_ALL_OPTIONAL.summaryResId).isEqualTo(REFERENCE_RES_ID)
        assertThrows(UnsupportedOperationException::class.java) { ISSUE_ONLY_BAREBONE.summaryResId }
        assertThrows(UnsupportedOperationException::class.java) {
            ISSUE_ONLY_ALL_OPTIONAL.summaryResId
        }
    }

    @Test
    fun getIntentAction_returnsIntentActionOrThrows() {
        assertThat(DYNAMIC_BAREBONE.intentAction).isEqualTo(INTENT_ACTION)
        assertThat(DYNAMIC_ALL_OPTIONAL.intentAction).isEqualTo(INTENT_ACTION)
        assertThat(DYNAMIC_DISABLED.intentAction).isNull()
        assertThat(DYNAMIC_HIDDEN.intentAction).isNull()
        assertThat(DYNAMIC_HIDDEN_WITH_SEARCH.intentAction).isEqualTo(INTENT_ACTION)
        assertThat(STATIC_BAREBONE.intentAction).isEqualTo(INTENT_ACTION)
        assertThat(STATIC_ALL_OPTIONAL.intentAction).isEqualTo(INTENT_ACTION)
        assertThrows(UnsupportedOperationException::class.java) { ISSUE_ONLY_BAREBONE.intentAction }
        assertThrows(UnsupportedOperationException::class.java) {
            ISSUE_ONLY_ALL_OPTIONAL.intentAction
        }
    }

    @Test
    fun getProfile_returnsProfile() {
        assertThat(DYNAMIC_BAREBONE.profile).isEqualTo(SafetySource.PROFILE_PRIMARY)
        assertThat(DYNAMIC_ALL_OPTIONAL.profile).isEqualTo(SafetySource.PROFILE_ALL)
        assertThat(DYNAMIC_DISABLED.profile).isEqualTo(SafetySource.PROFILE_PRIMARY)
        assertThat(DYNAMIC_HIDDEN.profile).isEqualTo(SafetySource.PROFILE_ALL)
        assertThat(DYNAMIC_HIDDEN_WITH_SEARCH.profile).isEqualTo(SafetySource.PROFILE_ALL)
        assertThat(STATIC_BAREBONE.profile).isEqualTo(SafetySource.PROFILE_PRIMARY)
        assertThat(STATIC_ALL_OPTIONAL.profile).isEqualTo(SafetySource.PROFILE_ALL)
        assertThat(ISSUE_ONLY_BAREBONE.profile).isEqualTo(SafetySource.PROFILE_PRIMARY)
        assertThat(ISSUE_ONLY_ALL_OPTIONAL.profile).isEqualTo(SafetySource.PROFILE_ALL)
    }

    @Test
    fun getInitialDisplayState_returnsInitialDisplayStateOrThrows() {
        assertThat(DYNAMIC_BAREBONE.initialDisplayState)
            .isEqualTo(SafetySource.INITIAL_DISPLAY_STATE_ENABLED)
        assertThat(DYNAMIC_ALL_OPTIONAL.initialDisplayState)
            .isEqualTo(SafetySource.INITIAL_DISPLAY_STATE_DISABLED)
        assertThat(DYNAMIC_DISABLED.initialDisplayState)
            .isEqualTo(SafetySource.INITIAL_DISPLAY_STATE_DISABLED)
        assertThat(DYNAMIC_HIDDEN.initialDisplayState)
            .isEqualTo(SafetySource.INITIAL_DISPLAY_STATE_HIDDEN)
        assertThat(DYNAMIC_HIDDEN_WITH_SEARCH.initialDisplayState)
            .isEqualTo(SafetySource.INITIAL_DISPLAY_STATE_HIDDEN)
        assertThrows(UnsupportedOperationException::class.java) {
            STATIC_BAREBONE.initialDisplayState
        }
        assertThrows(UnsupportedOperationException::class.java) {
            STATIC_ALL_OPTIONAL.initialDisplayState
        }
        assertThrows(UnsupportedOperationException::class.java) {
            ISSUE_ONLY_BAREBONE.initialDisplayState
        }
        assertThrows(UnsupportedOperationException::class.java) {
            ISSUE_ONLY_ALL_OPTIONAL.initialDisplayState
        }
    }

    @Test
    fun getMaxSeverityLevel_returnsMaxSeverityLevelOrThrows() {
        assertThat(DYNAMIC_BAREBONE.maxSeverityLevel).isEqualTo(Integer.MAX_VALUE)
        assertThat(DYNAMIC_ALL_OPTIONAL.maxSeverityLevel).isEqualTo(MAX_SEVERITY_LEVEL)
        assertThat(DYNAMIC_DISABLED.maxSeverityLevel).isEqualTo(Integer.MAX_VALUE)
        assertThat(DYNAMIC_HIDDEN.maxSeverityLevel).isEqualTo(Integer.MAX_VALUE)
        assertThat(DYNAMIC_HIDDEN_WITH_SEARCH.maxSeverityLevel).isEqualTo(Integer.MAX_VALUE)
        assertThrows(UnsupportedOperationException::class.java) { STATIC_BAREBONE.maxSeverityLevel }
        assertThrows(UnsupportedOperationException::class.java) {
            STATIC_ALL_OPTIONAL.maxSeverityLevel
        }
        assertThat(ISSUE_ONLY_BAREBONE.maxSeverityLevel).isEqualTo(Integer.MAX_VALUE)
        assertThat(ISSUE_ONLY_ALL_OPTIONAL.maxSeverityLevel).isEqualTo(MAX_SEVERITY_LEVEL)
    }

    @Test
    fun getSearchTermsResId_returnsSearchTermsResIdOrThrows() {
        assertThat(DYNAMIC_BAREBONE.searchTermsResId).isEqualTo(Resources.ID_NULL)
        assertThat(DYNAMIC_ALL_OPTIONAL.searchTermsResId).isEqualTo(REFERENCE_RES_ID)
        assertThat(DYNAMIC_DISABLED.searchTermsResId).isEqualTo(Resources.ID_NULL)
        assertThat(DYNAMIC_HIDDEN.searchTermsResId).isEqualTo(Resources.ID_NULL)
        assertThat(DYNAMIC_HIDDEN_WITH_SEARCH.searchTermsResId).isEqualTo(REFERENCE_RES_ID)
        assertThat(STATIC_BAREBONE.searchTermsResId).isEqualTo(Resources.ID_NULL)
        assertThat(STATIC_ALL_OPTIONAL.searchTermsResId).isEqualTo(REFERENCE_RES_ID)
        assertThrows(UnsupportedOperationException::class.java) {
            ISSUE_ONLY_BAREBONE.searchTermsResId
        }
        assertThrows(UnsupportedOperationException::class.java) {
            ISSUE_ONLY_ALL_OPTIONAL.searchTermsResId
        }
    }

    @Test
    fun isLoggingAllowed_returnsLoggingAllowedOrThrows() {
        assertThat(DYNAMIC_BAREBONE.isLoggingAllowed).isEqualTo(true)
        assertThat(DYNAMIC_ALL_OPTIONAL.isLoggingAllowed).isEqualTo(false)
        assertThat(DYNAMIC_DISABLED.isLoggingAllowed).isEqualTo(true)
        assertThat(DYNAMIC_HIDDEN.isLoggingAllowed).isEqualTo(true)
        assertThat(DYNAMIC_HIDDEN_WITH_SEARCH.isLoggingAllowed).isEqualTo(true)
        assertThrows(UnsupportedOperationException::class.java) { STATIC_BAREBONE.isLoggingAllowed }
        assertThrows(UnsupportedOperationException::class.java) {
            STATIC_ALL_OPTIONAL.isLoggingAllowed
        }
        assertThat(ISSUE_ONLY_BAREBONE.isLoggingAllowed).isEqualTo(true)
        assertThat(ISSUE_ONLY_ALL_OPTIONAL.isLoggingAllowed).isEqualTo(false)
    }

    @Test
    fun isRefreshOnPageOpenAllowed_returnsRefreshOnPageOpenAllowedOrThrows() {
        assertThat(DYNAMIC_BAREBONE.isRefreshOnPageOpenAllowed).isEqualTo(false)
        assertThat(DYNAMIC_ALL_OPTIONAL.isRefreshOnPageOpenAllowed).isEqualTo(true)
        assertThat(DYNAMIC_DISABLED.isRefreshOnPageOpenAllowed).isEqualTo(false)
        assertThat(DYNAMIC_HIDDEN.isRefreshOnPageOpenAllowed).isEqualTo(false)
        assertThat(DYNAMIC_HIDDEN_WITH_SEARCH.isRefreshOnPageOpenAllowed).isEqualTo(false)
        assertThrows(UnsupportedOperationException::class.java) {
            STATIC_BAREBONE.isRefreshOnPageOpenAllowed
        }
        assertThrows(UnsupportedOperationException::class.java) {
            STATIC_ALL_OPTIONAL.isRefreshOnPageOpenAllowed
        }
        assertThat(ISSUE_ONLY_BAREBONE.isRefreshOnPageOpenAllowed).isEqualTo(false)
        assertThat(ISSUE_ONLY_ALL_OPTIONAL.isRefreshOnPageOpenAllowed).isEqualTo(true)
    }

    @Test
    fun describeContents_returns0() {
        assertThat(DYNAMIC_BAREBONE.describeContents()).isEqualTo(0)
        assertThat(DYNAMIC_ALL_OPTIONAL.describeContents()).isEqualTo(0)
        assertThat(DYNAMIC_DISABLED.describeContents()).isEqualTo(0)
        assertThat(DYNAMIC_HIDDEN.describeContents()).isEqualTo(0)
        assertThat(DYNAMIC_HIDDEN_WITH_SEARCH.describeContents()).isEqualTo(0)
        assertThat(STATIC_BAREBONE.describeContents()).isEqualTo(0)
        assertThat(STATIC_ALL_OPTIONAL.describeContents()).isEqualTo(0)
        assertThat(ISSUE_ONLY_BAREBONE.describeContents()).isEqualTo(0)
        assertThat(ISSUE_ONLY_ALL_OPTIONAL.describeContents()).isEqualTo(0)
    }

    @Test
    fun parcelRoundTrip_recreatesEqual() {
        assertThat(DYNAMIC_BAREBONE).recreatesEqual(SafetySource.CREATOR)
        assertThat(DYNAMIC_ALL_OPTIONAL).recreatesEqual(SafetySource.CREATOR)
        assertThat(DYNAMIC_DISABLED).recreatesEqual(SafetySource.CREATOR)
        assertThat(DYNAMIC_HIDDEN).recreatesEqual(SafetySource.CREATOR)
        assertThat(DYNAMIC_HIDDEN_WITH_SEARCH).recreatesEqual(SafetySource.CREATOR)
        assertThat(STATIC_BAREBONE).recreatesEqual(SafetySource.CREATOR)
        assertThat(STATIC_ALL_OPTIONAL).recreatesEqual(SafetySource.CREATOR)
        assertThat(ISSUE_ONLY_BAREBONE).recreatesEqual(SafetySource.CREATOR)
        assertThat(ISSUE_ONLY_ALL_OPTIONAL).recreatesEqual(SafetySource.CREATOR)
    }

    @Test
    fun equalsHashCodeToString_usingEqualsHashCodeToStringTester() {
        EqualsHashCodeToStringTester()
            .addEqualityGroup(DYNAMIC_BAREBONE)
            .addEqualityGroup(
                DYNAMIC_ALL_OPTIONAL,
                SafetySource.Builder(SafetySource.SAFETY_SOURCE_TYPE_DYNAMIC)
                    .setId(DYNAMIC_ALL_OPTIONAL_ID)
                    .setPackageName(PACKAGE_NAME)
                    .setTitleResId(REFERENCE_RES_ID)
                    .setTitleForWorkResId(REFERENCE_RES_ID)
                    .setSummaryResId(REFERENCE_RES_ID)
                    .setIntentAction(INTENT_ACTION)
                    .setProfile(SafetySource.PROFILE_ALL)
                    .setInitialDisplayState(SafetySource.INITIAL_DISPLAY_STATE_DISABLED)
                    .setMaxSeverityLevel(MAX_SEVERITY_LEVEL)
                    .setSearchTermsResId(REFERENCE_RES_ID)
                    .setLoggingAllowed(false)
                    .setRefreshOnPageOpenAllowed(true)
                    .build())
            .addEqualityGroup(DYNAMIC_HIDDEN)
            .addEqualityGroup(DYNAMIC_HIDDEN_WITH_SEARCH)
            .addEqualityGroup(DYNAMIC_DISABLED)
            .addEqualityGroup(STATIC_BAREBONE)
            .addEqualityGroup(STATIC_ALL_OPTIONAL)
            .addEqualityGroup(ISSUE_ONLY_BAREBONE)
            .addEqualityGroup(ISSUE_ONLY_ALL_OPTIONAL)
            .addEqualityGroup(
                SafetySource.Builder(SafetySource.SAFETY_SOURCE_TYPE_DYNAMIC)
                    .setId("other")
                    .setPackageName(PACKAGE_NAME)
                    .setTitleResId(REFERENCE_RES_ID)
                    .setTitleForWorkResId(REFERENCE_RES_ID)
                    .setSummaryResId(REFERENCE_RES_ID)
                    .setIntentAction(INTENT_ACTION)
                    .setProfile(SafetySource.PROFILE_ALL)
                    .setInitialDisplayState(SafetySource.INITIAL_DISPLAY_STATE_DISABLED)
                    .setMaxSeverityLevel(MAX_SEVERITY_LEVEL)
                    .setSearchTermsResId(REFERENCE_RES_ID)
                    .setLoggingAllowed(false)
                    .setRefreshOnPageOpenAllowed(true)
                    .build())
            .addEqualityGroup(
                SafetySource.Builder(SafetySource.SAFETY_SOURCE_TYPE_DYNAMIC)
                    .setId(DYNAMIC_ALL_OPTIONAL_ID)
                    .setPackageName("other")
                    .setTitleResId(REFERENCE_RES_ID)
                    .setTitleForWorkResId(REFERENCE_RES_ID)
                    .setSummaryResId(REFERENCE_RES_ID)
                    .setIntentAction(INTENT_ACTION)
                    .setProfile(SafetySource.PROFILE_ALL)
                    .setInitialDisplayState(SafetySource.INITIAL_DISPLAY_STATE_DISABLED)
                    .setMaxSeverityLevel(MAX_SEVERITY_LEVEL)
                    .setSearchTermsResId(REFERENCE_RES_ID)
                    .setLoggingAllowed(false)
                    .setRefreshOnPageOpenAllowed(true)
                    .build())
            .addEqualityGroup(
                SafetySource.Builder(SafetySource.SAFETY_SOURCE_TYPE_DYNAMIC)
                    .setId(DYNAMIC_ALL_OPTIONAL_ID)
                    .setPackageName(PACKAGE_NAME)
                    .setTitleResId(-1)
                    .setTitleForWorkResId(REFERENCE_RES_ID)
                    .setSummaryResId(REFERENCE_RES_ID)
                    .setIntentAction(INTENT_ACTION)
                    .setProfile(SafetySource.PROFILE_ALL)
                    .setInitialDisplayState(SafetySource.INITIAL_DISPLAY_STATE_DISABLED)
                    .setMaxSeverityLevel(MAX_SEVERITY_LEVEL)
                    .setSearchTermsResId(REFERENCE_RES_ID)
                    .setLoggingAllowed(false)
                    .setRefreshOnPageOpenAllowed(true)
                    .build())
            .addEqualityGroup(
                SafetySource.Builder(SafetySource.SAFETY_SOURCE_TYPE_DYNAMIC)
                    .setId(DYNAMIC_ALL_OPTIONAL_ID)
                    .setPackageName(PACKAGE_NAME)
                    .setTitleResId(REFERENCE_RES_ID)
                    .setTitleForWorkResId(-1)
                    .setSummaryResId(REFERENCE_RES_ID)
                    .setIntentAction(INTENT_ACTION)
                    .setProfile(SafetySource.PROFILE_ALL)
                    .setInitialDisplayState(SafetySource.INITIAL_DISPLAY_STATE_DISABLED)
                    .setMaxSeverityLevel(MAX_SEVERITY_LEVEL)
                    .setSearchTermsResId(REFERENCE_RES_ID)
                    .setLoggingAllowed(false)
                    .setRefreshOnPageOpenAllowed(true)
                    .build())
            .addEqualityGroup(
                SafetySource.Builder(SafetySource.SAFETY_SOURCE_TYPE_DYNAMIC)
                    .setId(DYNAMIC_ALL_OPTIONAL_ID)
                    .setPackageName(PACKAGE_NAME)
                    .setTitleResId(REFERENCE_RES_ID)
                    .setTitleForWorkResId(REFERENCE_RES_ID)
                    .setSummaryResId(-1)
                    .setIntentAction(INTENT_ACTION)
                    .setProfile(SafetySource.PROFILE_ALL)
                    .setInitialDisplayState(SafetySource.INITIAL_DISPLAY_STATE_DISABLED)
                    .setMaxSeverityLevel(MAX_SEVERITY_LEVEL)
                    .setSearchTermsResId(REFERENCE_RES_ID)
                    .setLoggingAllowed(false)
                    .setRefreshOnPageOpenAllowed(true)
                    .build())
            .addEqualityGroup(
                SafetySource.Builder(SafetySource.SAFETY_SOURCE_TYPE_DYNAMIC)
                    .setId(DYNAMIC_ALL_OPTIONAL_ID)
                    .setPackageName(PACKAGE_NAME)
                    .setTitleResId(REFERENCE_RES_ID)
                    .setTitleForWorkResId(REFERENCE_RES_ID)
                    .setSummaryResId(REFERENCE_RES_ID)
                    .setIntentAction("other")
                    .setProfile(SafetySource.PROFILE_ALL)
                    .setInitialDisplayState(SafetySource.INITIAL_DISPLAY_STATE_DISABLED)
                    .setMaxSeverityLevel(MAX_SEVERITY_LEVEL)
                    .setSearchTermsResId(REFERENCE_RES_ID)
                    .setLoggingAllowed(false)
                    .setRefreshOnPageOpenAllowed(true)
                    .build())
            .addEqualityGroup(
                SafetySource.Builder(SafetySource.SAFETY_SOURCE_TYPE_DYNAMIC)
                    .setId(DYNAMIC_HIDDEN_ID)
                    .setPackageName(PACKAGE_NAME)
                    .setProfile(SafetySource.PROFILE_PRIMARY)
                    .setInitialDisplayState(SafetySource.INITIAL_DISPLAY_STATE_HIDDEN)
                    .build())
            .addEqualityGroup(
                SafetySource.Builder(SafetySource.SAFETY_SOURCE_TYPE_DYNAMIC)
                    .setId(DYNAMIC_ALL_OPTIONAL_ID)
                    .setPackageName(PACKAGE_NAME)
                    .setTitleResId(REFERENCE_RES_ID)
                    .setTitleForWorkResId(REFERENCE_RES_ID)
                    .setSummaryResId(REFERENCE_RES_ID)
                    .setIntentAction(INTENT_ACTION)
                    .setProfile(SafetySource.PROFILE_ALL)
                    .setInitialDisplayState(SafetySource.INITIAL_DISPLAY_STATE_ENABLED)
                    .setMaxSeverityLevel(MAX_SEVERITY_LEVEL)
                    .setSearchTermsResId(REFERENCE_RES_ID)
                    .setLoggingAllowed(false)
                    .setRefreshOnPageOpenAllowed(true)
                    .build())
            .addEqualityGroup(
                SafetySource.Builder(SafetySource.SAFETY_SOURCE_TYPE_DYNAMIC)
                    .setId(DYNAMIC_ALL_OPTIONAL_ID)
                    .setPackageName(PACKAGE_NAME)
                    .setTitleResId(REFERENCE_RES_ID)
                    .setTitleForWorkResId(REFERENCE_RES_ID)
                    .setSummaryResId(REFERENCE_RES_ID)
                    .setIntentAction(INTENT_ACTION)
                    .setProfile(SafetySource.PROFILE_ALL)
                    .setInitialDisplayState(SafetySource.INITIAL_DISPLAY_STATE_DISABLED)
                    .setMaxSeverityLevel(-1)
                    .setSearchTermsResId(REFERENCE_RES_ID)
                    .setLoggingAllowed(false)
                    .setRefreshOnPageOpenAllowed(true)
                    .build())
            .addEqualityGroup(
                SafetySource.Builder(SafetySource.SAFETY_SOURCE_TYPE_DYNAMIC)
                    .setId(DYNAMIC_ALL_OPTIONAL_ID)
                    .setPackageName(PACKAGE_NAME)
                    .setTitleResId(REFERENCE_RES_ID)
                    .setTitleForWorkResId(REFERENCE_RES_ID)
                    .setSummaryResId(REFERENCE_RES_ID)
                    .setIntentAction(INTENT_ACTION)
                    .setProfile(SafetySource.PROFILE_ALL)
                    .setInitialDisplayState(SafetySource.INITIAL_DISPLAY_STATE_DISABLED)
                    .setMaxSeverityLevel(MAX_SEVERITY_LEVEL)
                    .setSearchTermsResId(-1)
                    .setLoggingAllowed(false)
                    .setRefreshOnPageOpenAllowed(true)
                    .build())
            .addEqualityGroup(
                SafetySource.Builder(SafetySource.SAFETY_SOURCE_TYPE_DYNAMIC)
                    .setId(DYNAMIC_ALL_OPTIONAL_ID)
                    .setPackageName(PACKAGE_NAME)
                    .setTitleResId(REFERENCE_RES_ID)
                    .setTitleForWorkResId(REFERENCE_RES_ID)
                    .setSummaryResId(REFERENCE_RES_ID)
                    .setIntentAction(INTENT_ACTION)
                    .setProfile(SafetySource.PROFILE_ALL)
                    .setInitialDisplayState(SafetySource.INITIAL_DISPLAY_STATE_DISABLED)
                    .setMaxSeverityLevel(MAX_SEVERITY_LEVEL)
                    .setSearchTermsResId(REFERENCE_RES_ID)
                    .setLoggingAllowed(true)
                    .setRefreshOnPageOpenAllowed(true)
                    .build())
            .addEqualityGroup(
                SafetySource.Builder(SafetySource.SAFETY_SOURCE_TYPE_DYNAMIC)
                    .setId(DYNAMIC_ALL_OPTIONAL_ID)
                    .setPackageName(PACKAGE_NAME)
                    .setTitleResId(REFERENCE_RES_ID)
                    .setTitleForWorkResId(REFERENCE_RES_ID)
                    .setSummaryResId(REFERENCE_RES_ID)
                    .setIntentAction(INTENT_ACTION)
                    .setProfile(SafetySource.PROFILE_ALL)
                    .setInitialDisplayState(SafetySource.INITIAL_DISPLAY_STATE_DISABLED)
                    .setMaxSeverityLevel(MAX_SEVERITY_LEVEL)
                    .setSearchTermsResId(REFERENCE_RES_ID)
                    .setLoggingAllowed(false)
                    .setRefreshOnPageOpenAllowed(false)
                    .build())
            .test()
    }

    companion object {
        private const val PACKAGE_NAME = "package"
        private const val REFERENCE_RES_ID = 9999
        private const val INTENT_ACTION = "intent"
        private const val MAX_SEVERITY_LEVEL = 300

        private const val DYNAMIC_BAREBONE_ID = "dynamic_barebone"
        private const val DYNAMIC_ALL_OPTIONAL_ID = "dynamic_all_optional"
        private const val DYNAMIC_DISABLED_ID = "dynamic_disabled"
        private const val DYNAMIC_HIDDEN_ID = "dynamic_hidden"
        private const val DYNAMIC_HIDDEN_WITH_SEARCH_ID = "dynamic_hidden_with_search"
        private const val STATIC_BAREBONE_ID = "static_barebone"
        private const val STATIC_ALL_OPTIONAL_ID = "static_all_optional"
        private const val ISSUE_ONLY_BAREBONE_ID = "issue_only_barebone"
        private const val ISSUE_ONLY_ALL_OPTIONAL_ID = "issue_only_all_optional"

        internal val DYNAMIC_BAREBONE =
            SafetySource.Builder(SafetySource.SAFETY_SOURCE_TYPE_DYNAMIC)
                .setId(DYNAMIC_BAREBONE_ID)
                .setPackageName(PACKAGE_NAME)
                .setTitleResId(REFERENCE_RES_ID)
                .setSummaryResId(REFERENCE_RES_ID)
                .setIntentAction(INTENT_ACTION)
                .setProfile(SafetySource.PROFILE_PRIMARY)
                .build()

        private val DYNAMIC_ALL_OPTIONAL =
            SafetySource.Builder(SafetySource.SAFETY_SOURCE_TYPE_DYNAMIC)
                .setId(DYNAMIC_ALL_OPTIONAL_ID)
                .setPackageName(PACKAGE_NAME)
                .setTitleResId(REFERENCE_RES_ID)
                .setTitleForWorkResId(REFERENCE_RES_ID)
                .setSummaryResId(REFERENCE_RES_ID)
                .setIntentAction(INTENT_ACTION)
                .setProfile(SafetySource.PROFILE_ALL)
                .setInitialDisplayState(SafetySource.INITIAL_DISPLAY_STATE_DISABLED)
                .setMaxSeverityLevel(MAX_SEVERITY_LEVEL)
                .setSearchTermsResId(REFERENCE_RES_ID)
                .setLoggingAllowed(false)
                .setRefreshOnPageOpenAllowed(true)
                .build()

        private val DYNAMIC_DISABLED =
            SafetySource.Builder(SafetySource.SAFETY_SOURCE_TYPE_DYNAMIC)
                .setId(DYNAMIC_DISABLED_ID)
                .setPackageName(PACKAGE_NAME)
                .setTitleResId(REFERENCE_RES_ID)
                .setSummaryResId(REFERENCE_RES_ID)
                .setProfile(SafetySource.PROFILE_PRIMARY)
                .setInitialDisplayState(SafetySource.INITIAL_DISPLAY_STATE_DISABLED)
                .build()

        private val DYNAMIC_HIDDEN =
            SafetySource.Builder(SafetySource.SAFETY_SOURCE_TYPE_DYNAMIC)
                .setId(DYNAMIC_HIDDEN_ID)
                .setPackageName(PACKAGE_NAME)
                .setProfile(SafetySource.PROFILE_ALL)
                .setInitialDisplayState(SafetySource.INITIAL_DISPLAY_STATE_HIDDEN)
                .build()

        private val DYNAMIC_HIDDEN_WITH_SEARCH =
            SafetySource.Builder(SafetySource.SAFETY_SOURCE_TYPE_DYNAMIC)
                .setId(DYNAMIC_HIDDEN_WITH_SEARCH_ID)
                .setPackageName(PACKAGE_NAME)
                .setTitleResId(REFERENCE_RES_ID)
                .setTitleForWorkResId(REFERENCE_RES_ID)
                .setSummaryResId(REFERENCE_RES_ID)
                .setIntentAction(INTENT_ACTION)
                .setProfile(SafetySource.PROFILE_ALL)
                .setInitialDisplayState(SafetySource.INITIAL_DISPLAY_STATE_HIDDEN)
                .setSearchTermsResId(REFERENCE_RES_ID)
                .build()

        internal val STATIC_BAREBONE =
            SafetySource.Builder(SafetySource.SAFETY_SOURCE_TYPE_STATIC)
                .setId(STATIC_BAREBONE_ID)
                .setTitleResId(REFERENCE_RES_ID)
                .setIntentAction(INTENT_ACTION)
                .setProfile(SafetySource.PROFILE_PRIMARY)
                .build()

        private val STATIC_ALL_OPTIONAL =
            SafetySource.Builder(SafetySource.SAFETY_SOURCE_TYPE_STATIC)
                .setId(STATIC_ALL_OPTIONAL_ID)
                .setTitleResId(REFERENCE_RES_ID)
                .setTitleForWorkResId(REFERENCE_RES_ID)
                .setSummaryResId(REFERENCE_RES_ID)
                .setIntentAction(INTENT_ACTION)
                .setProfile(SafetySource.PROFILE_ALL)
                .setSearchTermsResId(REFERENCE_RES_ID)
                .build()

        internal val ISSUE_ONLY_BAREBONE =
            SafetySource.Builder(SafetySource.SAFETY_SOURCE_TYPE_ISSUE_ONLY)
                .setId(ISSUE_ONLY_BAREBONE_ID)
                .setPackageName(PACKAGE_NAME)
                .setProfile(SafetySource.PROFILE_PRIMARY)
                .build()

        private val ISSUE_ONLY_ALL_OPTIONAL =
            SafetySource.Builder(SafetySource.SAFETY_SOURCE_TYPE_ISSUE_ONLY)
                .setId(ISSUE_ONLY_ALL_OPTIONAL_ID)
                .setPackageName(PACKAGE_NAME)
                .setProfile(SafetySource.PROFILE_ALL)
                .setMaxSeverityLevel(MAX_SEVERITY_LEVEL)
                .setLoggingAllowed(false)
                .setRefreshOnPageOpenAllowed(true)
                .build()
    }
}
