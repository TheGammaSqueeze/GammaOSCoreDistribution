// Copyright 2021 Google Inc. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package java

import (
	"sort"
	"strings"
	"testing"

	"android/soong/android"

	"github.com/google/blueprint"
)

func intPtr(v int) *int {
	return &v
}

func TestPrebuiltApis_SystemModulesCreation(t *testing.T) {
	result := android.GroupFixturePreparers(
		prepareForJavaTest,
		FixtureWithPrebuiltApis(map[string][]string{
			"31":      {},
			"32":      {},
			"current": {},
		}),
	).RunTest(t)

	sdkSystemModules := []string{}
	result.VisitAllModules(func(module blueprint.Module) {
		name := android.RemoveOptionalPrebuiltPrefix(module.Name())
		if strings.HasPrefix(name, "sdk_") && strings.HasSuffix(name, "_system_modules") {
			sdkSystemModules = append(sdkSystemModules, name)
		}
	})
	sort.Strings(sdkSystemModules)
	expected := []string{
		// 31 only has public system modules.
		"sdk_public_31_system_modules",

		// 32 and current both have public and module-lib system modules.
		"sdk_public_32_system_modules",
		"sdk_module-lib_32_system_modules",
		"sdk_public_current_system_modules",
		"sdk_module-lib_current_system_modules",
	}
	sort.Strings(expected)
	android.AssertArrayString(t, "sdk system modules", expected, sdkSystemModules)
}

func TestPrebuiltApis_WithExtensions(t *testing.T) {
	runTestWithBaseExtensionLevel := func(v int) (foo_input string, bar_input string) {
		result := android.GroupFixturePreparers(
			prepareForJavaTest,
			android.FixtureModifyProductVariables(func(variables android.FixtureProductVariables) {
				variables.Platform_base_sdk_extension_version = intPtr(v)
			}),
			FixtureWithPrebuiltApisAndExtensions(map[string][]string{
				"31":      {"foo"},
				"32":      {"foo", "bar"},
				"current": {"foo", "bar"},
			}, map[string][]string{
				"1": {"foo"},
				"2": {"foo", "bar"},
			}),
		).RunTest(t)
		foo_input = result.ModuleForTests("foo.api.public.latest", "").Rule("generator").Implicits[0].String()
		bar_input = result.ModuleForTests("bar.api.public.latest", "").Rule("generator").Implicits[0].String()
		return
	}
	// Here, the base extension level is 1, so extension level 2 is the latest
	foo_input, bar_input := runTestWithBaseExtensionLevel(1)
	android.AssertStringEquals(t, "Expected latest = extension level 2", "prebuilts/sdk/extensions/2/public/api/foo.txt", foo_input)
	android.AssertStringEquals(t, "Expected latest = extension level 2", "prebuilts/sdk/extensions/2/public/api/bar.txt", bar_input)

	// Here, the base extension level is 2, so 2 is not later than 32.
	foo_input, bar_input = runTestWithBaseExtensionLevel(2)
	android.AssertStringEquals(t, "Expected latest = api level 32", "prebuilts/sdk/32/public/api/foo.txt", foo_input)
	android.AssertStringEquals(t, "Expected latest = api level 32", "prebuilts/sdk/32/public/api/bar.txt", bar_input)
}
