// Copyright (C) 2021 The Android Open Source Project
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

package aidl

import (
	"android/soong/android"
	"android/soong/cc"
	"android/soong/java"
	"android/soong/rust"

	"path/filepath"
	"strings"

	"github.com/google/blueprint/proptools"
)

func addLibrary(mctx android.LoadHookContext, i *aidlInterface, version string, lang string, notFrozen bool) string {
	if lang == langJava {
		return addJavaLibrary(mctx, i, version, notFrozen)
	} else if lang == langRust {
		return addRustLibrary(mctx, i, version, notFrozen)
	}
	return addCppLibrary(mctx, i, version, lang, notFrozen)
}

func addCppLibrary(mctx android.LoadHookContext, i *aidlInterface, version string, lang string, notFrozen bool) string {
	cppSourceGen := i.versionedName(version) + "-" + lang + "-source"
	cppModuleGen := i.versionedName(version) + "-" + lang

	srcs, aidlRoot := i.srcsForVersion(mctx, version)
	if len(srcs) == 0 {
		// This can happen when the version is about to be frozen; the version
		// directory is created but API dump hasn't been copied there.
		// Don't create a library for the yet-to-be-frozen version.
		return ""
	}

	var overrideVndkProperties cc.VndkProperties

	if !i.isModuleForVndk(version) {
		// We only want the VNDK to include the latest interface. For interfaces in
		// development, they will be frozen, so we put their latest version in the
		// VNDK. For interfaces which are already frozen, we put their latest version
		// in the VNDK, and when that version is frozen, the version in the VNDK can
		// be updated. Otherwise, we remove this library from the VNDK, to avoid adding
		// multiple versions of the same library to the VNDK.
		overrideVndkProperties.Vndk.Enabled = proptools.BoolPtr(false)
		overrideVndkProperties.Vndk.Support_system_process = proptools.BoolPtr(false)
	}

	var commonProperties *CommonNativeBackendProperties
	if lang == langCpp {
		commonProperties = &i.properties.Backend.Cpp.CommonNativeBackendProperties
	} else if lang == langNdk || lang == langNdkPlatform {
		commonProperties = &i.properties.Backend.Ndk.CommonNativeBackendProperties
	}

	genLog := proptools.Bool(commonProperties.Gen_log)
	genTrace := proptools.Bool(i.properties.Gen_trace)

	mctx.CreateModule(aidlGenFactory, &nameProperties{
		Name: proptools.StringPtr(cppSourceGen),
	}, &aidlGenProperties{
		Srcs:            srcs,
		AidlRoot:        aidlRoot,
		Imports:         i.getImportsForVersion(version),
		Stability:       i.properties.Stability,
		Min_sdk_version: i.minSdkVersion(lang),
		Lang:            lang,
		BaseName:        i.ModuleBase.Name(),
		GenLog:          genLog,
		Version:         i.versionForInitVersionCompat(version),
		GenTrace:        genTrace,
		Unstable:        i.properties.Unstable,
		NotFrozen:       notFrozen,
		Flags:           i.flagsForAidlGenRule(version),
	})

	importExportDependencies := []string{}
	var sharedLibDependency []string
	var headerLibs []string
	var sdkVersion *string
	var stl *string
	var cpp_std *string
	var hostSupported *bool
	var addCflags []string
	targetProp := ccTargetProperties{
		Darwin: darwinProperties{Enabled: proptools.BoolPtr(false)},
	}

	if lang == langCpp {
		importExportDependencies = append(importExportDependencies, "libbinder", "libutils")
		if genTrace {
			sharedLibDependency = append(sharedLibDependency, "libcutils")
		}
		hostSupported = i.properties.Host_supported
	} else if lang == langNdk || lang == langNdkPlatform {
		importExportDependencies = append(importExportDependencies, "libbinder_ndk")
		nonAppProps := imageProperties{
			Cflags: []string{"-DBINDER_STABILITY_SUPPORT"},
		}
		if genTrace {
			sharedLibDependency = append(sharedLibDependency, "libandroid")
			nonAppProps.Exclude_shared_libs = []string{"libandroid"}
			nonAppProps.Header_libs = []string{"libandroid_aidltrace"}
			nonAppProps.Shared_libs = []string{"libcutils"}
		}
		targetProp.Platform = nonAppProps
		targetProp.Vendor = nonAppProps
		targetProp.Product = nonAppProps
		hostSupported = i.properties.Host_supported
		if lang == langNdk && i.shouldGenerateAppNdkBackend() {
			sdkVersion = i.properties.Backend.Ndk.Sdk_version
			if sdkVersion == nil {
				sdkVersion = proptools.StringPtr("current")
			}

			// Don't worry! This maps to libc++.so for the platform variant.
			stl = proptools.StringPtr("c++_shared")
		}
	} else {
		panic("Unrecognized language: " + lang)
	}

	vendorAvailable := i.properties.Vendor_available
	odmAvailable := i.properties.Odm_available
	productAvailable := i.properties.Product_available
	recoveryAvailable := i.properties.Recovery_available
	if lang == langCpp {
		// Vendor and product modules cannot use the libbinder (cpp) backend of AIDL in a
		// way that is stable. So, in order to prevent accidental usage of these library by
		// vendor and product forcibly disabling this version of the library.
		//
		// It may be the case in the future that we will want to enable this (if some generic
		// helper should be used by both libbinder vendor things using /dev/vndbinder as well
		// as those things using /dev/binder + libbinder_ndk to talk to stable interfaces).
		if "vintf" == proptools.String(i.properties.Stability) {
			overrideVndkProperties.Vndk.Private = proptools.BoolPtr(true)
		}
		// As libbinder is not available for the product processes, we must not create
		// product variant for the aidl_interface
		productAvailable = nil
	}

	mctx.CreateModule(aidlImplementationGeneratorFactory, &nameProperties{
		Name: proptools.StringPtr(cppModuleGen + "-generator"),
	}, &aidlImplementationGeneratorProperties{
		Lang:              lang,
		AidlInterfaceName: i.ModuleBase.Name(),
		Version:           version,
		Imports:           i.getImportsForVersion(version),
		ModuleProperties: []interface{}{
			&ccProperties{
				Name:                      proptools.StringPtr(cppModuleGen),
				Vendor_available:          vendorAvailable,
				Odm_available:             odmAvailable,
				Product_available:         productAvailable,
				Recovery_available:        recoveryAvailable,
				Host_supported:            hostSupported,
				Defaults:                  []string{"aidl-cpp-module-defaults"},
				Double_loadable:           i.properties.Double_loadable,
				Generated_sources:         []string{cppSourceGen},
				Generated_headers:         []string{cppSourceGen},
				Export_generated_headers:  []string{cppSourceGen},
				Shared_libs:               append(importExportDependencies, sharedLibDependency...),
				Header_libs:               headerLibs,
				Export_shared_lib_headers: importExportDependencies,
				Sdk_version:               sdkVersion,
				Stl:                       stl,
				Cpp_std:                   cpp_std,
				Cflags:                    append(addCflags, "-Wextra", "-Wall", "-Werror", "-Wextra-semi"),
				Apex_available:            commonProperties.Apex_available,
				Min_sdk_version:           i.minSdkVersion(lang),
				Target:                    targetProp,
				Tidy:                      proptools.BoolPtr(true),
				// Do the tidy check only for the generated headers
				Tidy_flags:            []string{"--header-filter=" + android.PathForOutput(mctx).String() + ".*"},
				Tidy_checks_as_errors: []string{"*"},
			}, &i.properties.VndkProperties,
			&commonProperties.VndkProperties,
			&overrideVndkProperties,
		},
	})

	return cppModuleGen
}

func addJavaLibrary(mctx android.LoadHookContext, i *aidlInterface, version string, notFrozen bool) string {
	javaSourceGen := i.versionedName(version) + "-java-source"
	javaModuleGen := i.versionedName(version) + "-java"
	srcs, aidlRoot := i.srcsForVersion(mctx, version)
	if len(srcs) == 0 {
		// This can happen when the version is about to be frozen; the version
		// directory is created but API dump hasn't been copied there.
		// Don't create a library for the yet-to-be-frozen version.
		return ""
	}
	minSdkVersion := i.minSdkVersion(langJava)
	sdkVersion := i.properties.Backend.Java.Sdk_version
	if !proptools.Bool(i.properties.Backend.Java.Platform_apis) && sdkVersion == nil {
		// platform apis requires no default
		sdkVersion = proptools.StringPtr("system_current")
	}
	// use sdkVersion if minSdkVersion is not set
	if sdkVersion != nil && minSdkVersion == nil {
		minSdkVersion = proptools.StringPtr(android.SdkSpecFrom(mctx, *sdkVersion).ApiLevel.String())
	}

	mctx.CreateModule(aidlGenFactory, &nameProperties{
		Name: proptools.StringPtr(javaSourceGen),
	}, &aidlGenProperties{
		Srcs:            srcs,
		AidlRoot:        aidlRoot,
		Imports:         i.getImportsForVersion(version),
		Stability:       i.properties.Stability,
		Min_sdk_version: minSdkVersion,
		Platform_apis:   proptools.Bool(i.properties.Backend.Java.Platform_apis),
		Lang:            langJava,
		BaseName:        i.ModuleBase.Name(),
		Version:         version,
		GenRpc:          proptools.Bool(i.properties.Backend.Java.Gen_rpc),
		GenTrace:        proptools.Bool(i.properties.Gen_trace),
		Unstable:        i.properties.Unstable,
		NotFrozen:       notFrozen,
		Flags:           i.flagsForAidlGenRule(version),
	})

	mctx.CreateModule(aidlImplementationGeneratorFactory, &nameProperties{
		Name: proptools.StringPtr(javaModuleGen + "-generator"),
	}, &aidlImplementationGeneratorProperties{
		Lang:              langJava,
		AidlInterfaceName: i.ModuleBase.Name(),
		Version:           version,
		Imports:           i.getImportsForVersion(version),
		ModuleProperties: []interface{}{&javaProperties{
			Name:            proptools.StringPtr(javaModuleGen),
			Installable:     proptools.BoolPtr(true),
			Defaults:        []string{"aidl-java-module-defaults"},
			Sdk_version:     sdkVersion,
			Platform_apis:   i.properties.Backend.Java.Platform_apis,
			Srcs:            []string{":" + javaSourceGen},
			Apex_available:  i.properties.Backend.Java.Apex_available,
			Min_sdk_version: i.minSdkVersion(langJava),
		}, &i.properties.Backend.Java.LintProperties},
	})

	return javaModuleGen
}

func addRustLibrary(mctx android.LoadHookContext, i *aidlInterface, version string, notFrozen bool) string {
	rustSourceGen := i.versionedName(version) + "-rust-source"
	rustModuleGen := i.versionedName(version) + "-rust"
	srcs, aidlRoot := i.srcsForVersion(mctx, version)
	if len(srcs) == 0 {
		// This can happen when the version is about to be frozen; the version
		// directory is created but API dump hasn't been copied there.
		// Don't create a library for the yet-to-be-frozen version.
		return ""
	}

	mctx.CreateModule(aidlGenFactory, &nameProperties{
		Name: proptools.StringPtr(rustSourceGen),
	}, &aidlGenProperties{
		Srcs:            srcs,
		AidlRoot:        aidlRoot,
		Imports:         i.getImportsForVersion(version),
		Stability:       i.properties.Stability,
		Min_sdk_version: i.minSdkVersion(langRust),
		Lang:            langRust,
		BaseName:        i.ModuleBase.Name(),
		Version:         i.versionForInitVersionCompat(version),
		Unstable:        i.properties.Unstable,
		NotFrozen:       notFrozen,
		Flags:           i.flagsForAidlGenRule(version),
	})

	versionedRustName := fixRustName(i.versionedName(version))
	rustCrateName := fixRustName(i.ModuleBase.Name())

	mctx.CreateModule(wrapLibraryFactory(aidlRustLibraryFactory), &rustProperties{
		Name:             proptools.StringPtr(rustModuleGen),
		Crate_name:       rustCrateName,
		Stem:             proptools.StringPtr("lib" + versionedRustName),
		Defaults:         []string{"aidl-rust-module-defaults"},
		Host_supported:   i.properties.Host_supported,
		Vendor_available: i.properties.Vendor_available,
		Apex_available:   i.properties.Backend.Rust.Apex_available,
		Min_sdk_version:  i.minSdkVersion(langRust),
		Target:           rustTargetProperties{Darwin: darwinProperties{Enabled: proptools.BoolPtr(false)}},
	}, &rust.SourceProviderProperties{
		Source_stem: proptools.StringPtr(versionedRustName),
	}, &aidlRustSourceProviderProperties{
		SourceGen:         rustSourceGen,
		Imports:           i.getImportsForVersion(version),
		Version:           version,
		AidlInterfaceName: i.ModuleBase.Name(),
	})

	return rustModuleGen
}

// This function returns module name with version. Assume that there is foo of which latest version is 2
// Version -> Module name
// "1"->foo-V1
// "2"->foo-V2
// "3"->foo-V3
// And assume that there is 'bar' which is an 'unstable' interface.
// ""->bar
func (i *aidlInterface) versionedName(version string) string {
	name := i.ModuleBase.Name()
	if version == "" {
		return name
	}
	return name + "-V" + version
}

func (i *aidlInterface) srcsForVersion(mctx android.EarlyModuleContext, version string) (srcs []string, aidlRoot string) {
	if version == i.nextVersion() {
		return i.properties.Srcs, i.properties.Local_include_dir
	} else {
		aidlRoot = filepath.Join(aidlApiDir, i.ModuleBase.Name(), version)
		full_paths, err := mctx.GlobWithDeps(filepath.Join(mctx.ModuleDir(), aidlRoot, "**/*.aidl"), nil)
		if err != nil {
			panic(err)
		}
		for _, path := range full_paths {
			// Here, we need path local to the module
			srcs = append(srcs, strings.TrimPrefix(path, mctx.ModuleDir()+"/"))
		}
		return srcs, aidlRoot
	}
}

// For certain backend, avoid a difference between the initial version of a versioned
// interface and an unversioned interface. This ensures that prebuilts can't prevent
// an interface from switching from unversioned to versioned.
func (i *aidlInterface) versionForInitVersionCompat(version string) string {
	if !i.hasVersion() {
		return ""
	}
	return version
}

func (i *aidlInterface) flagsForAidlGenRule(version string) (flags []string) {
	flags = append(flags, i.properties.Flags...)
	// For ToT, turn on "-Weverything" (enable all warnings)
	if version == i.nextVersion() {
		flags = append(flags, "-Weverything -Wno-missing-permission-annotation")
	}
	return
}

func (i *aidlInterface) isModuleForVndk(version string) bool {
	if i.properties.Vndk_use_version != nil {
		if !i.hasVersion() {
			panic("does not make sense, vndk_use_version specififed")
		}
		// Will be exactly one of the version numbers
		return version == *i.properties.Vndk_use_version
	}

	// For an interface with no versions, this is the ToT interface.
	if !i.hasVersion() {
		return version == i.nextVersion()
	}

	return version == i.latestVersion()
}

// importing aidl_interface's version  | imported aidl_interface | imported aidl_interface's version
// --------------------------------------------------------------------------------------------------
// whatever                            | unstable                | unstable version
// ToT version(including unstable)     | whatever                | ToT version(unstable if unstable)
// otherwise                           | whatever                | the latest stable version
// In the case that import specifies the version which it wants to use, use that version.
func (i *aidlInterface) getImportWithVersion(version string, anImport string, other *aidlInterface) string {
	if hasVersionSuffix(anImport) {
		return anImport
	}
	if proptools.Bool(other.properties.Unstable) {
		return anImport
	}
	if version == i.nextVersion() || !other.hasVersion() {
		return other.versionedName(other.nextVersion())
	}
	return other.versionedName(other.latestVersion())
}

// Assuming that the context module has deps to its original aidl_interface and imported
// aidl_interface modules with interfaceDepTag and importInterfaceDepTag, returns the list of
// imported interfaces with versions.
func getImportsWithVersion(ctx android.BaseMutatorContext, interfaceName, version string) []string {
	i := ctx.GetDirectDepWithTag(interfaceName+aidlInterfaceSuffix, interfaceDep).(*aidlInterface)
	var imports []string
	ctx.VisitDirectDeps(func(dep android.Module) {
		if tag, ok := ctx.OtherModuleDependencyTag(dep).(importInterfaceDepTag); ok {
			other := dep.(*aidlInterface)
			imports = append(imports, i.getImportWithVersion(version, tag.anImport, other))
		}
	})
	return imports
}

func aidlImplementationGeneratorFactory() android.Module {
	g := &aidlImplementationGenerator{}
	g.AddProperties(&g.properties)
	android.InitAndroidModule(g)
	return g
}

type aidlImplementationGenerator struct {
	android.ModuleBase
	properties aidlImplementationGeneratorProperties
}

type aidlImplementationGeneratorProperties struct {
	Lang              string
	AidlInterfaceName string
	Version           string
	Imports           []string
	ModuleProperties  []interface{}
}

func (g *aidlImplementationGenerator) DepsMutator(ctx android.BottomUpMutatorContext) {
}

func (g *aidlImplementationGenerator) GenerateAndroidBuildActions(ctx android.ModuleContext) {
}

func (g *aidlImplementationGenerator) GenerateImplementation(ctx android.TopDownMutatorContext) {
	imports := wrap("", getImportsWithVersion(ctx, g.properties.AidlInterfaceName, g.properties.Version), "-"+g.properties.Lang)
	if g.properties.Lang == langJava {
		if p, ok := g.properties.ModuleProperties[0].(*javaProperties); ok {
			p.Static_libs = imports
		}
		ctx.CreateModule(wrapLibraryFactory(java.LibraryFactory), g.properties.ModuleProperties...)
	} else {
		if p, ok := g.properties.ModuleProperties[0].(*ccProperties); ok {
			p.Shared_libs = append(p.Shared_libs, imports...)
			p.Export_shared_lib_headers = append(p.Export_shared_lib_headers, imports...)
		}
		ctx.CreateModule(wrapLibraryFactory(cc.LibraryFactory), g.properties.ModuleProperties...)
	}
}
