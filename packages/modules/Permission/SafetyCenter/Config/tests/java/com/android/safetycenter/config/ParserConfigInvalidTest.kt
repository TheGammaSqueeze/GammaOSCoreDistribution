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

package com.android.safetycenter.config

import android.content.Context
import android.os.Build.VERSION_CODES.TIRAMISU
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.filters.SdkSuppress
import com.android.safetycenter.config.tests.R
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
@SdkSuppress(minSdkVersion = TIRAMISU, codeName = "Tiramisu")
class ParserConfigInvalidTest {
    private val context: Context = getApplicationContext()

    data class Params(
        private val testName: String,
        val configResourceId: Int,
        val errorMessage: String,
        val causeErrorMessage: String?
    ) {
        override fun toString() = testName
    }

    @Parameterized.Parameter lateinit var params: Params

    @Test
    fun invalidConfig_throws() {
        val inputStream = context.resources.openRawResource(params.configResourceId)

        val thrown =
            assertThrows(ParseException::class.java) {
                SafetyCenterConfigParser.parseXmlResource(inputStream, context.resources)
            }

        assertThat(thrown).hasMessageThat().isEqualTo(params.errorMessage)
        if (params.causeErrorMessage != null) {
            assertThat(thrown.cause).hasMessageThat().isEqualTo(params.causeErrorMessage)
        }
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun parameters() =
            arrayOf(
                Params(
                    "ConfigDynamicSafetySourceAllDisabledNoWork",
                    R.raw.config_dynamic_safety_source_all_disabled_no_work,
                    "Element dynamic-safety-source invalid",
                    "Required attribute titleForWork missing"),
                Params(
                    "ConfigDynamicSafetySourceAllHiddenWithSearchNoWork",
                    R.raw.config_dynamic_safety_source_all_hidden_with_search_no_work,
                    "Element dynamic-safety-source invalid",
                    "Required attribute titleForWork missing"),
                Params(
                    "ConfigDynamicSafetySourceAllNoWork",
                    R.raw.config_dynamic_safety_source_all_no_work,
                    "Element dynamic-safety-source invalid",
                    "Required attribute titleForWork missing"),
                Params(
                    "ConfigDynamicSafetySourceDisabledNoSummary",
                    R.raw.config_dynamic_safety_source_disabled_no_summary,
                    "Element dynamic-safety-source invalid",
                    "Required attribute summary missing"),
                Params(
                    "ConfigDynamicSafetySourceDisabledNoTitle",
                    R.raw.config_dynamic_safety_source_disabled_no_title,
                    "Element dynamic-safety-source invalid",
                    "Required attribute title missing"),
                Params(
                    "ConfigDynamicSafetySourceDuplicateKey",
                    R.raw.config_dynamic_safety_source_duplicate_key,
                    "Element safety-sources-config invalid",
                    "Duplicate id id among safety sources"),
                Params(
                    "ConfigDynamicSafetySourceHiddenWithSearchNoTitle",
                    R.raw.config_dynamic_safety_source_hidden_with_search_no_title,
                    "Element dynamic-safety-source invalid",
                    "Required attribute title missing"),
                Params(
                    "ConfigDynamicSafetySourceInvalidDisplay",
                    R.raw.config_dynamic_safety_source_invalid_display,
                    "Attribute value \"invalid\" in dynamic-safety-source.initialDisplayState " +
                        "invalid",
                    null),
                Params(
                    "ConfigDynamicSafetySourceInvalidProfile",
                    R.raw.config_dynamic_safety_source_invalid_profile,
                    "Attribute value \"invalid\" in dynamic-safety-source.profile invalid",
                    null),
                Params(
                    "ConfigDynamicSafetySourceNoId",
                    R.raw.config_dynamic_safety_source_no_id,
                    "Element dynamic-safety-source invalid",
                    "Required attribute id missing"),
                Params(
                    "ConfigDynamicSafetySourceNoIntent",
                    R.raw.config_dynamic_safety_source_no_intent,
                    "Element dynamic-safety-source invalid",
                    "Required attribute intentAction missing"),
                Params(
                    "ConfigDynamicSafetySourceNoPackage",
                    R.raw.config_dynamic_safety_source_no_package,
                    "Element dynamic-safety-source invalid",
                    "Required attribute packageName missing"),
                Params(
                    "ConfigDynamicSafetySourceNoProfile",
                    R.raw.config_dynamic_safety_source_no_profile,
                    "Element dynamic-safety-source invalid",
                    "Required attribute profile missing"),
                Params(
                    "ConfigDynamicSafetySourceNoSummary",
                    R.raw.config_dynamic_safety_source_no_summary,
                    "Element dynamic-safety-source invalid",
                    "Required attribute summary missing"),
                Params(
                    "ConfigDynamicSafetySourceNoTitle",
                    R.raw.config_dynamic_safety_source_no_title,
                    "Element dynamic-safety-source invalid",
                    "Required attribute title missing"),
                Params(
                    "ConfigDynamicSafetySourcePrimaryHiddenWithWork",
                    R.raw.config_dynamic_safety_source_primary_hidden_with_work,
                    "Element dynamic-safety-source invalid",
                    "Prohibited attribute titleForWork present"),
                Params(
                    "ConfigDynamicSafetySourcePrimaryWithWork",
                    R.raw.config_dynamic_safety_source_primary_with_work,
                    "Element dynamic-safety-source invalid",
                    "Prohibited attribute titleForWork present"),
                Params(
                    "ConfigFileCorrupted",
                    R.raw.config_file_corrupted,
                    "Exception while parsing the XML resource",
                    null),
                Params(
                    "ConfigIssueOnlySafetySourceDuplicateKey",
                    R.raw.config_issue_only_safety_source_duplicate_key,
                    "Element safety-sources-config invalid",
                    "Duplicate id id among safety sources"),
                Params(
                    "ConfigIssueOnlySafetySourceInvalidProfile",
                    R.raw.config_issue_only_safety_source_invalid_profile,
                    "Attribute value \"invalid\" in issue-only-safety-source.profile invalid",
                    null),
                Params(
                    "ConfigIssueOnlySafetySourceNoId",
                    R.raw.config_issue_only_safety_source_no_id,
                    "Element issue-only-safety-source invalid",
                    "Required attribute id missing"),
                Params(
                    "ConfigIssueOnlySafetySourceNoPackage",
                    R.raw.config_issue_only_safety_source_no_package,
                    "Element issue-only-safety-source invalid",
                    "Required attribute packageName missing"),
                Params(
                    "ConfigIssueOnlySafetySourceNoProfile",
                    R.raw.config_issue_only_safety_source_no_profile,
                    "Element issue-only-safety-source invalid",
                    "Required attribute profile missing"),
                Params(
                    "ConfigIssueOnlySafetySourceWithDisplay",
                    R.raw.config_issue_only_safety_source_with_display,
                    "Element issue-only-safety-source invalid",
                    "Prohibited attribute initialDisplayState present"),
                Params(
                    "ConfigIssueOnlySafetySourceWithIntent",
                    R.raw.config_issue_only_safety_source_with_intent,
                    "Element issue-only-safety-source invalid",
                    "Prohibited attribute intentAction present"),
                Params(
                    "ConfigIssueOnlySafetySourceWithSearch",
                    R.raw.config_issue_only_safety_source_with_search,
                    "Element issue-only-safety-source invalid",
                    "Prohibited attribute searchTerms present"),
                Params(
                    "ConfigIssueOnlySafetySourceWithSummary",
                    R.raw.config_issue_only_safety_source_with_summary,
                    "Element issue-only-safety-source invalid",
                    "Prohibited attribute summary present"),
                Params(
                    "ConfigIssueOnlySafetySourceWithTitle",
                    R.raw.config_issue_only_safety_source_with_title,
                    "Element issue-only-safety-source invalid",
                    "Prohibited attribute title present"),
                Params(
                    "ConfigIssueOnlySafetySourceWithWork",
                    R.raw.config_issue_only_safety_source_with_work,
                    "Element issue-only-safety-source invalid",
                    "Prohibited attribute titleForWork present"),
                Params(
                    "ConfigMixedSafetySourceDuplicateKey",
                    R.raw.config_mixed_safety_source_duplicate_key,
                    "Element safety-sources-config invalid",
                    "Duplicate id id among safety sources"),
                Params(
                    "ConfigSafetyCenterConfigMissing",
                    R.raw.config_safety_center_config_missing,
                    "Element safety-center-config missing",
                    null),
                Params(
                    "ConfigSafetySourcesConfigEmpty",
                    R.raw.config_safety_sources_config_empty,
                    "Element safety-sources-config invalid",
                    "No safety sources groups present"),
                Params(
                    "ConfigSafetySourcesConfigMissing",
                    R.raw.config_safety_sources_config_missing,
                    "Element safety-sources-config missing",
                    null),
                Params(
                    "ConfigSafetySourcesGroupDuplicateId",
                    R.raw.config_safety_sources_group_duplicate_id,
                    "Element safety-sources-config invalid",
                    "Duplicate id id among safety sources groups"),
                Params(
                    "ConfigSafetySourcesGroupEmpty",
                    R.raw.config_safety_sources_group_empty,
                    "Element safety-sources-group invalid",
                    "Safety sources group empty"),
                Params(
                    "ConfigSafetySourcesGroupInvalidIcon",
                    R.raw.config_safety_sources_group_invalid_icon,
                    "Attribute value \"invalid\" in safety-sources-group.statelessIconType invalid",
                    null),
                Params(
                    "ConfigSafetySourcesGroupNoId",
                    R.raw.config_safety_sources_group_no_id,
                    "Element safety-sources-group invalid",
                    "Required attribute id missing"),
                Params(
                    "ConfigSafetySourcesGroupNoTitle",
                    R.raw.config_safety_sources_group_no_title,
                    "Element safety-sources-group invalid",
                    "Required attribute title missing"),
                Params(
                    "ConfigStaticSafetySourceDuplicateKey",
                    R.raw.config_static_safety_source_duplicate_key,
                    "Element safety-sources-config invalid",
                    "Duplicate id id among safety sources"),
                Params(
                    "ConfigStaticSafetySourceInvalidProfile",
                    R.raw.config_static_safety_source_invalid_profile,
                    "Attribute value \"invalid\" in static-safety-source.profile invalid",
                    null),
                Params(
                    "ConfigStaticSafetySourceNoId",
                    R.raw.config_static_safety_source_no_id,
                    "Element static-safety-source invalid",
                    "Required attribute id missing"),
                Params(
                    "ConfigStaticSafetySourceNoIntent",
                    R.raw.config_static_safety_source_no_intent,
                    "Element static-safety-source invalid",
                    "Required attribute intentAction missing"),
                Params(
                    "ConfigStaticSafetySourceNoProfile",
                    R.raw.config_static_safety_source_no_profile,
                    "Element static-safety-source invalid",
                    "Required attribute profile missing"),
                Params(
                    "ConfigStaticSafetySourceNoTitle",
                    R.raw.config_static_safety_source_no_title,
                    "Element static-safety-source invalid",
                    "Required attribute title missing"),
                Params(
                    "ConfigStaticSafetySourceWithDisplay",
                    R.raw.config_static_safety_source_with_display,
                    "Element static-safety-source invalid",
                    "Prohibited attribute initialDisplayState present"),
                Params(
                    "ConfigStaticSafetySourceWithLogging",
                    R.raw.config_static_safety_source_with_logging,
                    "Element static-safety-source invalid",
                    "Prohibited attribute loggingAllowed present"),
                Params(
                    "ConfigStaticSafetySourceWithPackage",
                    R.raw.config_static_safety_source_with_package,
                    "Element static-safety-source invalid",
                    "Prohibited attribute packageName present"),
                Params(
                    "ConfigStaticSafetySourceWithPrimaryAndWork",
                    R.raw.config_static_safety_source_with_primary_and_work,
                    "Element static-safety-source invalid",
                    "Prohibited attribute titleForWork present"),
                Params(
                    "ConfigStaticSafetySourceWithRefresh",
                    R.raw.config_static_safety_source_with_refresh,
                    "Element static-safety-source invalid",
                    "Prohibited attribute refreshOnPageOpenAllowed present"),
                Params(
                    "ConfigStaticSafetySourceWithSeverity",
                    R.raw.config_static_safety_source_with_severity,
                    "Element static-safety-source invalid",
                    "Prohibited attribute maxSeverityLevel present"),
                Params(
                    "ConfigStringResourceNameInvalidEmpty",
                    R.raw.config_string_resource_name_empty,
                    "Resource name in safety-sources-group.title cannot be empty",
                    null),
                Params(
                    "ConfigStringResourceNameInvalidNoAt",
                    R.raw.config_string_resource_name_invalid_no_at,
                    "Resource name \"com.android.safetycenter.config.tests:string/reference\" in " +
                        "safety-sources-group.title does not start with @",
                    null),
                Params(
                    "ConfigStringResourceNameInvalidNoPackage",
                    R.raw.config_string_resource_name_invalid_no_package,
                    "Resource name \"@string/reference\" in safety-sources-group.title does not " +
                        "specify a package",
                    null),
                Params(
                    "ConfigStringResourceNameInvalidNoType",
                    R.raw.config_string_resource_name_invalid_no_type,
                    "Resource name \"@com.android.safetycenter.config.tests:reference\" in " +
                        "safety-sources-group.title does not specify a type",
                    null),
                Params(
                    "ConfigStringResourceNameInvalidWrongType",
                    R.raw.config_string_resource_name_invalid_wrong_type,
                    "Resource name \"@com.android.safetycenter.config.tests:raw/" +
                        "config_string_resource_name_invalid_wrong_type\" in " +
                        "safety-sources-group.title is not a string",
                    null),
                Params(
                    "ConfigStringResourceNameMissing",
                    R.raw.config_string_resource_name_missing,
                    "Resource name \"@com.android.safetycenter.config.tests:string/missing\" in " +
                        "safety-sources-group.title missing or invalid",
                    null))
    }
}
