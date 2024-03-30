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

package bp2build

import (
	"android/soong/android"
	"android/soong/apex"
	"android/soong/cc"
	"android/soong/java"
	"android/soong/sh"

	"testing"
)

func runApexTestCase(t *testing.T, tc bp2buildTestCase) {
	t.Helper()
	runBp2BuildTestCase(t, registerApexModuleTypes, tc)
}

func registerApexModuleTypes(ctx android.RegistrationContext) {
	// CC module types needed as they can be APEX dependencies
	cc.RegisterCCBuildComponents(ctx)

	ctx.RegisterModuleType("sh_binary", sh.ShBinaryFactory)
	ctx.RegisterModuleType("cc_binary", cc.BinaryFactory)
	ctx.RegisterModuleType("cc_library", cc.LibraryFactory)
	ctx.RegisterModuleType("apex_key", apex.ApexKeyFactory)
	ctx.RegisterModuleType("android_app_certificate", java.AndroidAppCertificateFactory)
	ctx.RegisterModuleType("filegroup", android.FileGroupFactory)
}

func TestApexBundleSimple(t *testing.T) {
	runApexTestCase(t, bp2buildTestCase{
		description:                "apex - example with all props",
		moduleTypeUnderTest:        "apex",
		moduleTypeUnderTestFactory: apex.BundleFactory,
		filesystem:                 map[string]string{},
		blueprint: `
apex_key {
	name: "com.android.apogee.key",
	public_key: "com.android.apogee.avbpubkey",
	private_key: "com.android.apogee.pem",
	bazel_module: { bp2build_available: false },
}

android_app_certificate {
	name: "com.android.apogee.certificate",
	certificate: "com.android.apogee",
	bazel_module: { bp2build_available: false },
}

cc_library {
	name: "native_shared_lib_1",
	bazel_module: { bp2build_available: false },
}

cc_library {
	name: "native_shared_lib_2",
	bazel_module: { bp2build_available: false },
}

// TODO(b/194878861): Add bp2build support for prebuilt_etc
cc_library {
	name: "pretend_prebuilt_1",
	bazel_module: { bp2build_available: false },
}

// TODO(b/194878861): Add bp2build support for prebuilt_etc
cc_library {
	name: "pretend_prebuilt_2",
	bazel_module: { bp2build_available: false },
}

filegroup {
	name: "com.android.apogee-file_contexts",
	srcs: [
			"com.android.apogee-file_contexts",
	],
	bazel_module: { bp2build_available: false },
}

cc_binary { name: "cc_binary_1", bazel_module: { bp2build_available: false } }
sh_binary { name: "sh_binary_2", bazel_module: { bp2build_available: false } }

apex {
	name: "com.android.apogee",
	manifest: "apogee_manifest.json",
	androidManifest: "ApogeeAndroidManifest.xml",
	file_contexts: "com.android.apogee-file_contexts",
	min_sdk_version: "29",
	key: "com.android.apogee.key",
	certificate: "com.android.apogee.certificate",
	updatable: false,
	installable: false,
	compressible: false,
	native_shared_libs: [
	    "native_shared_lib_1",
	    "native_shared_lib_2",
	],
	binaries: [
		"cc_binary_1",
		"sh_binary_2",
	],
	prebuilts: [
	    "pretend_prebuilt_1",
	    "pretend_prebuilt_2",
	],
}
`,
		expectedBazelTargets: []string{
			makeBazelTarget("apex", "com.android.apogee", attrNameToString{
				"android_manifest": `"ApogeeAndroidManifest.xml"`,
				"binaries": `[
        ":cc_binary_1",
        ":sh_binary_2",
    ]`,
				"certificate":     `":com.android.apogee.certificate"`,
				"file_contexts":   `":com.android.apogee-file_contexts"`,
				"installable":     "False",
				"key":             `":com.android.apogee.key"`,
				"manifest":        `"apogee_manifest.json"`,
				"min_sdk_version": `"29"`,
				"native_shared_libs_32": `[
        ":native_shared_lib_1",
        ":native_shared_lib_2",
    ]`,
				"native_shared_libs_64": `select({
        "//build/bazel/platforms/arch:arm64": [
            ":native_shared_lib_1",
            ":native_shared_lib_2",
        ],
        "//build/bazel/platforms/arch:x86_64": [
            ":native_shared_lib_1",
            ":native_shared_lib_2",
        ],
        "//conditions:default": [],
    })`,
				"prebuilts": `[
        ":pretend_prebuilt_1",
        ":pretend_prebuilt_2",
    ]`,
				"updatable":    "False",
				"compressible": "False",
			}),
		}})
}

func TestApexBundleCompileMultilibBoth(t *testing.T) {
	runApexTestCase(t, bp2buildTestCase{
		description:                "apex - example with compile_multilib=both",
		moduleTypeUnderTest:        "apex",
		moduleTypeUnderTestFactory: apex.BundleFactory,
		filesystem:                 map[string]string{},
		blueprint:                  createMultilibBlueprint("both"),
		expectedBazelTargets: []string{
			makeBazelTarget("apex", "com.android.apogee", attrNameToString{
				"native_shared_libs_32": `[
        ":native_shared_lib_1",
        ":native_shared_lib_3",
    ] + select({
        "//build/bazel/platforms/arch:arm": [":native_shared_lib_2"],
        "//build/bazel/platforms/arch:x86": [":native_shared_lib_2"],
        "//conditions:default": [],
    })`,
				"native_shared_libs_64": `select({
        "//build/bazel/platforms/arch:arm64": [
            ":native_shared_lib_1",
            ":native_shared_lib_4",
            ":native_shared_lib_2",
        ],
        "//build/bazel/platforms/arch:x86_64": [
            ":native_shared_lib_1",
            ":native_shared_lib_4",
            ":native_shared_lib_2",
        ],
        "//conditions:default": [],
    })`,
			}),
		}})
}

func TestApexBundleCompileMultilibFirst(t *testing.T) {
	runApexTestCase(t, bp2buildTestCase{
		description:                "apex - example with compile_multilib=first",
		moduleTypeUnderTest:        "apex",
		moduleTypeUnderTestFactory: apex.BundleFactory,
		filesystem:                 map[string]string{},
		blueprint:                  createMultilibBlueprint("first"),
		expectedBazelTargets: []string{
			makeBazelTarget("apex", "com.android.apogee", attrNameToString{
				"native_shared_libs_32": `select({
        "//build/bazel/platforms/arch:arm": [
            ":native_shared_lib_1",
            ":native_shared_lib_3",
            ":native_shared_lib_2",
        ],
        "//build/bazel/platforms/arch:x86": [
            ":native_shared_lib_1",
            ":native_shared_lib_3",
            ":native_shared_lib_2",
        ],
        "//conditions:default": [],
    })`,
				"native_shared_libs_64": `select({
        "//build/bazel/platforms/arch:arm64": [
            ":native_shared_lib_1",
            ":native_shared_lib_4",
            ":native_shared_lib_2",
        ],
        "//build/bazel/platforms/arch:x86_64": [
            ":native_shared_lib_1",
            ":native_shared_lib_4",
            ":native_shared_lib_2",
        ],
        "//conditions:default": [],
    })`,
			}),
		}})
}

func TestApexBundleCompileMultilib32(t *testing.T) {
	runApexTestCase(t, bp2buildTestCase{
		description:                "apex - example with compile_multilib=32",
		moduleTypeUnderTest:        "apex",
		moduleTypeUnderTestFactory: apex.BundleFactory,
		filesystem:                 map[string]string{},
		blueprint:                  createMultilibBlueprint("32"),
		expectedBazelTargets: []string{
			makeBazelTarget("apex", "com.android.apogee", attrNameToString{
				"native_shared_libs_32": `[
        ":native_shared_lib_1",
        ":native_shared_lib_3",
    ] + select({
        "//build/bazel/platforms/arch:arm": [":native_shared_lib_2"],
        "//build/bazel/platforms/arch:x86": [":native_shared_lib_2"],
        "//conditions:default": [],
    })`,
			}),
		}})
}

func TestApexBundleCompileMultilib64(t *testing.T) {
	runApexTestCase(t, bp2buildTestCase{
		description:                "apex - example with compile_multilib=64",
		moduleTypeUnderTest:        "apex",
		moduleTypeUnderTestFactory: apex.BundleFactory,
		filesystem:                 map[string]string{},
		blueprint:                  createMultilibBlueprint("64"),
		expectedBazelTargets: []string{
			makeBazelTarget("apex", "com.android.apogee", attrNameToString{
				"native_shared_libs_64": `select({
        "//build/bazel/platforms/arch:arm64": [
            ":native_shared_lib_1",
            ":native_shared_lib_4",
            ":native_shared_lib_2",
        ],
        "//build/bazel/platforms/arch:x86_64": [
            ":native_shared_lib_1",
            ":native_shared_lib_4",
            ":native_shared_lib_2",
        ],
        "//conditions:default": [],
    })`,
			}),
		}})
}

func TestApexBundleDefaultPropertyValues(t *testing.T) {
	runApexTestCase(t, bp2buildTestCase{
		description:                "apex - default property values",
		moduleTypeUnderTest:        "apex",
		moduleTypeUnderTestFactory: apex.BundleFactory,
		filesystem:                 map[string]string{},
		blueprint: `
apex {
	name: "com.android.apogee",
	manifest: "apogee_manifest.json",
}
`,
		expectedBazelTargets: []string{makeBazelTarget("apex", "com.android.apogee", attrNameToString{
			"manifest": `"apogee_manifest.json"`,
		}),
		}})
}

func TestApexBundleHasBazelModuleProps(t *testing.T) {
	runApexTestCase(t, bp2buildTestCase{
		description:                "apex - has bazel module props",
		moduleTypeUnderTest:        "apex",
		moduleTypeUnderTestFactory: apex.BundleFactory,
		filesystem:                 map[string]string{},
		blueprint: `
apex {
	name: "apogee",
	manifest: "manifest.json",
	bazel_module: { bp2build_available: true },
}
`,
		expectedBazelTargets: []string{makeBazelTarget("apex", "apogee", attrNameToString{
			"manifest": `"manifest.json"`,
		}),
		}})
}

func createMultilibBlueprint(compile_multilib string) string {
	return `
cc_library {
	name: "native_shared_lib_1",
	bazel_module: { bp2build_available: false },
}

cc_library {
	name: "native_shared_lib_2",
	bazel_module: { bp2build_available: false },
}

cc_library {
	name: "native_shared_lib_3",
	bazel_module: { bp2build_available: false },
}

cc_library {
	name: "native_shared_lib_4",
	bazel_module: { bp2build_available: false },
}

apex {
	name: "com.android.apogee",
	compile_multilib: "` + compile_multilib + `",
	multilib: {
		both: {
			native_shared_libs: [
				"native_shared_lib_1",
			],
		},
		first: {
			native_shared_libs: [
				"native_shared_lib_2",
			],
		},
		lib32: {
			native_shared_libs: [
				"native_shared_lib_3",
			],
		},
		lib64: {
			native_shared_libs: [
				"native_shared_lib_4",
			],
		},
	},
}`
}
