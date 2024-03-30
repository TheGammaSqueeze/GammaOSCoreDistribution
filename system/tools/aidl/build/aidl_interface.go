// Copyright (C) 2018 The Android Open Source Project
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
	"android/soong/phony"
	"android/soong/rust"

	"fmt"
	"path/filepath"
	"regexp"
	"sort"
	"strconv"
	"strings"

	"github.com/google/blueprint"
	"github.com/google/blueprint/proptools"
)

const (
	aidlInterfaceSuffix       = "_interface"
	aidlMetadataSingletonName = "aidl_metadata_json"
	aidlApiDir                = "aidl_api"
	aidlApiSuffix             = "-api"
	langCpp                   = "cpp"
	langJava                  = "java"
	langNdk                   = "ndk"
	langRust                  = "rust"
	// TODO(b/161456198) remove the NDK platform backend as the 'platform' variant of the NDK
	// backend serves the same purpose.
	langNdkPlatform = "ndk_platform"

	currentVersion = "current"
)

var (
	pctx = android.NewPackageContext("android/aidl")
)

func init() {
	pctx.Import("android/soong/android")
	pctx.HostBinToolVariable("aidlCmd", "aidl")
	pctx.HostBinToolVariable("aidlHashGen", "aidl_hash_gen")
	pctx.SourcePathVariable("aidlToJniCmd", "system/tools/aidl/build/aidl_to_jni.py")
	pctx.SourcePathVariable("aidlRustGlueCmd", "system/tools/aidl/build/aidl_rust_glue.py")
	android.RegisterModuleType("aidl_interface", aidlInterfaceFactory)
	android.PreArchMutators(registerPreArchMutators)
	android.PreArchBp2BuildMutators(registerPreArchMutators)
	android.PostDepsMutators(registerPostDepsMutators)
}

func registerPreArchMutators(ctx android.RegisterMutatorsContext) {
	ctx.BottomUp("addInterfaceDeps", addInterfaceDeps).Parallel()
	ctx.BottomUp("checkImports", checkImports).Parallel()
	ctx.TopDown("createAidlInterface", createAidlInterfaceMutator).Parallel()
}

func registerPostDepsMutators(ctx android.RegisterMutatorsContext) {
	ctx.BottomUp("checkAidlGeneratedModules", checkAidlGeneratedModules).Parallel()
}

func createAidlInterfaceMutator(mctx android.TopDownMutatorContext) {
	if g, ok := mctx.Module().(*aidlImplementationGenerator); ok {
		g.GenerateImplementation(mctx)
	}
}

// A marker struct for AIDL-generated library modules
type AidlGeneratedModuleProperties struct{}

func wrapLibraryFactory(factory func() android.Module) func() android.Module {
	return func() android.Module {
		m := factory()
		// put a marker struct for AIDL-generated modules
		m.AddProperties(&AidlGeneratedModuleProperties{})
		return m
	}
}

func isAidlGeneratedModule(module android.Module) bool {
	for _, props := range module.GetProperties() {
		// check if there's a marker struct
		if _, ok := props.(*AidlGeneratedModuleProperties); ok {
			return true
		}
	}
	return false
}

// AildVersionInfo keeps the *-source module for each (aidl_interface & lang) and the list of
// not-frozen versions (which shouldn't be used by other modules)
type AildVersionInfo struct {
	notFrozen []string
	sourceMap map[string]string
}

var AidlVersionInfoProvider = blueprint.NewMutatorProvider(AildVersionInfo{}, "checkAidlGeneratedModules")

// Merges `other` version info into this one.
// Returns the pair of mismatching versions when there's conflict. Otherwise returns nil.
// For example, when a module depends on 'foo-V2-ndk', the map contains an entry of (foo, foo-V2-ndk-source).
// Merging (foo, foo-V1-ndk-source) and (foo, foo-V2-ndk-source) will fail and returns
// {foo-V1-ndk-source, foo-V2-ndk-source}.
func (info *AildVersionInfo) merge(other AildVersionInfo) []string {
	info.notFrozen = append(info.notFrozen, other.notFrozen...)

	if other.sourceMap == nil {
		return nil
	}
	if info.sourceMap == nil {
		info.sourceMap = make(map[string]string)
	}
	for ifaceName, otherSourceName := range other.sourceMap {
		if sourceName, ok := info.sourceMap[ifaceName]; ok {
			if sourceName != otherSourceName {
				return []string{sourceName, otherSourceName}
			}
		} else {
			info.sourceMap[ifaceName] = otherSourceName
		}
	}
	return nil
}

func reportUsingNotFrozenError(ctx android.BaseModuleContext, notFrozen []string) {
	// TODO(b/154066686): Replace it with a common method instead of listing up module types.
	// Test libraries are exempted.
	if android.InList(ctx.ModuleType(), []string{"cc_test_library", "android_test", "cc_benchmark", "cc_test"}) {
		return
	}
	for _, name := range notFrozen {
		ctx.ModuleErrorf("%v is disallowed in release version because it is unstable, and its \"owner\" property is missing.",
			name)
	}
}

func reportMultipleVersionError(ctx android.BaseModuleContext, violators []string) {
	sort.Strings(violators)
	ctx.ModuleErrorf("depends on multiple versions of the same aidl_interface: %s", strings.Join(violators, ", "))
	ctx.WalkDeps(func(child android.Module, parent android.Module) bool {
		if android.InList(child.Name(), violators) {
			ctx.ModuleErrorf("Dependency path: %s", ctx.GetPathString(true))
			return false
		}
		return true
	})
}

func checkAidlGeneratedModules(mctx android.BottomUpMutatorContext) {
	switch mctx.Module().(type) {
	case *java.Library:
	case *cc.Module:
	case *rust.Module:
	case *aidlGenRule:
	default:
		return
	}
	if gen, ok := mctx.Module().(*aidlGenRule); ok {
		var notFrozen []string
		if gen.properties.NotFrozen {
			notFrozen = []string{strings.TrimSuffix(mctx.ModuleName(), "-source")}
		}
		mctx.SetProvider(AidlVersionInfoProvider, AildVersionInfo{
			notFrozen: notFrozen,
			sourceMap: map[string]string{
				gen.properties.BaseName + "-" + gen.properties.Lang: gen.Name(),
			},
		})
		return
	}
	// Collect/merge AildVersionInfos from direct dependencies
	var info AildVersionInfo
	mctx.VisitDirectDeps(func(dep android.Module) {
		if mctx.OtherModuleHasProvider(dep, AidlVersionInfoProvider) {
			otherInfo := mctx.OtherModuleProvider(dep, AidlVersionInfoProvider).(AildVersionInfo)
			if violators := info.merge(otherInfo); violators != nil {
				reportMultipleVersionError(mctx, violators)
			}
		}
	})
	if !isAidlGeneratedModule(mctx.Module()) && len(info.notFrozen) > 0 {
		reportUsingNotFrozenError(mctx, info.notFrozen)
	}
	if mctx.Failed() {
		return
	}
	if info.sourceMap != nil || len(info.notFrozen) > 0 {
		mctx.SetProvider(AidlVersionInfoProvider, info)
	}
}

func getPaths(ctx android.ModuleContext, rawSrcs []string, root string) (srcs android.Paths, imports []string) {
	// TODO(b/189288369): move this to android.PathsForModuleSrcSubDir(ctx, srcs, subdir)
	for _, src := range rawSrcs {
		if m, _ := android.SrcIsModuleWithTag(src); m != "" {
			srcs = append(srcs, android.PathsForModuleSrc(ctx, []string{src})...)
		} else {
			srcs = append(srcs, android.PathsWithModuleSrcSubDir(ctx, android.PathsForModuleSrc(ctx, []string{src}), root)...)
		}
	}

	if len(srcs) == 0 {
		ctx.PropertyErrorf("srcs", "No sources provided in %v", root)
	}

	// gather base directories from input .aidl files
	for _, src := range srcs {
		if src.Ext() != ".aidl" {
			// Silently ignore non-aidl files as some filegroups have both java and aidl files together
			continue
		}
		baseDir := strings.TrimSuffix(src.String(), src.Rel())
		baseDir = strings.TrimSuffix(baseDir, "/")
		if baseDir != "" && !android.InList(baseDir, imports) {
			imports = append(imports, baseDir)
		}
	}

	return srcs, imports
}

func isRelativePath(path string) bool {
	if path == "" {
		return true
	}
	return filepath.Clean(path) == path && path != ".." &&
		!strings.HasPrefix(path, "../") && !strings.HasPrefix(path, "/")
}

type CommonBackendProperties struct {
	// Whether to generate code in the corresponding backend.
	// Default: true
	Enabled        *bool
	Apex_available []string

	// The minimum version of the sdk that the compiled artifacts will run against
	// For native modules, the property needs to be set when a module is a part of mainline modules(APEX).
	// Forwarded to generated java/native module.
	Min_sdk_version *string
}

type CommonNativeBackendProperties struct {
	CommonBackendProperties
	// Whether to generate additional code for gathering information
	// about the transactions.
	// Default: false
	Gen_log *bool

	// VNDK properties for correspdoning backend.
	cc.VndkProperties
}

type DumpApiProperties struct {
	// Dumps without license header (assuming it is the first comment in .aidl file). Default: false
	No_license *bool
}

type aidlInterfaceProperties struct {
	// Vndk properties for C++/NDK libraries only (preferred to use backend-specific settings)
	cc.VndkProperties

	// How to interpret VNDK options. We only want one library in the VNDK (not multiple
	// versions, since this would be a waste of space/unclear, and ultimately we want all
	// code in a given release to be updated to use a specific version). By default, this
	// puts either the latest stable version of the library or, if there is no stable
	// version, the unstable version of the library in the VNDK. When using this field,
	// explicitly set it to one of the values in the 'versions' field to put that version
	// in the VNDK or set it to the next version (1 higher than this) to mean the version
	// that will be frozen in the next update.
	Vndk_use_version *string

	// Whether the library can be installed on the vendor image.
	Vendor_available *bool

	// Whether the library can be installed on the odm image.
	Odm_available *bool

	// Whether the library can be installed on the product image.
	Product_available *bool

	// Whether the library can be installed on the recovery image.
	Recovery_available *bool

	// Whether the library can be loaded multiple times into the same process
	Double_loadable *bool

	// Whether the library can be used on host
	Host_supported *bool

	// Whether tracing should be added to the interface.
	Gen_trace *bool

	// Top level directories for includes.
	// TODO(b/128940869): remove it if aidl_interface can depend on framework.aidl
	Include_dirs []string
	// Relative path for includes. By default assumes AIDL path is relative to current directory.
	Local_include_dir string

	// List of .aidl files which compose this interface.
	Srcs []string `android:"path"`

	// List of aidl_interface modules that this uses. If one of your AIDL interfaces uses an
	// interface or parcelable from another aidl_interface, you should put its name here.
	// It could be an aidl_interface solely or with version(such as -V1)
	Imports []string

	// Stability promise. Currently only supports "vintf".
	// If this is unset, this corresponds to an interface with stability within
	// this compilation context (so an interface loaded here can only be used
	// with things compiled together, e.g. on the system.img).
	// If this is set to "vintf", this corresponds to a stability promise: the
	// interface must be kept stable as long as it is used.
	Stability *string

	// Deprecated: Use `versions_with_info` instead. Don't use `versions` property directly.
	Versions []string

	// Previous API versions that are now frozen. The version that is last in
	// the list is considered as the most recent version.
	// The struct contains both version and imports information per a version.
	// Until versions property is removed, don't use `versions_with_info` directly.
	Versions_with_info []struct {
		Version string
		Imports []string
	}

	// Use aidlInterface.getVersions()
	VersionsInternal []string `blueprint:"mutated"`

	// The minimum version of the sdk that the compiled artifacts will run against
	// For native modules, the property needs to be set when a module is a part of mainline modules(APEX).
	// Forwarded to generated java/native module. This can be overridden by
	// backend.<name>.min_sdk_version.
	Min_sdk_version *string

	Backend struct {
		// Backend of the compiler generating code for Java clients.
		// When enabled, this creates a target called "<name>-java".
		Java struct {
			CommonBackendProperties
			// Set to the version of the sdk to compile against
			// Default: system_current
			Sdk_version *string
			// Whether to compile against platform APIs instead of
			// an SDK.
			Platform_apis *bool
			// Whether RPC features are enabled (requires API level 32)
			// TODO(b/175819535): enable this automatically?
			Gen_rpc *bool
			// Lint properties for generated java module
			java.LintProperties
		}
		// Backend of the compiler generating code for C++ clients using
		// libbinder (unstable C++ interface)
		// When enabled, this creates a target called "<name>-cpp".
		Cpp struct {
			CommonNativeBackendProperties
		}
		// Backend of the compiler generating code for C++ clients using libbinder_ndk
		// (stable C interface to system's libbinder) When enabled, this creates a target
		// called "<name>-V<ver>-ndk" (for both apps and platform) and
		// "<name>-V<ver>-ndk_platform" (for platform only).
		// TODO(b/161456198): remove the ndk_platform backend as the ndk backend can serve
		// the same purpose.
		Ndk struct {
			CommonNativeBackendProperties

			// Set to the version of the sdk to compile against, for the NDK
			// variant.
			// Default: current
			Sdk_version *string

			// If set to false, the ndk backend is exclusive to platform and is not
			// available to applications. Default is true (i.e. available to both
			// applications and platform).
			Apps_enabled *bool
		}
		// Backend of the compiler generating code for Rust clients.
		// When enabled, this creates a target called "<name>-rust".
		Rust struct {
			CommonBackendProperties
		}
	}

	// Marks that this interface does not need to be stable. When set to true, the build system
	// doesn't create the API dump and require it to be updated. Default is false.
	Unstable *bool

	// Optional flags to be passed to the AIDL compiler. e.g. "-Weverything"
	Flags []string

	// --dumpapi options
	Dumpapi DumpApiProperties
}

type aidlInterface struct {
	android.ModuleBase

	properties aidlInterfaceProperties

	computedTypes []string

	// list of module names that are created for this interface
	internalModuleNames []string

	// map for version to preprocessed.aidl file.
	// There's two additional alias for versions:
	// - ""(empty) is for ToT
	// - "latest" is for i.latestVersion()
	preprocessed map[string]android.WritablePath
}

func (i *aidlInterface) shouldGenerateJavaBackend() bool {
	// explicitly true if not specified to give early warning to devs
	return proptools.BoolDefault(i.properties.Backend.Java.Enabled, true)
}

func (i *aidlInterface) shouldGenerateCppBackend() bool {
	// explicitly true if not specified to give early warning to devs
	return proptools.BoolDefault(i.properties.Backend.Cpp.Enabled, true)
}

func (i *aidlInterface) shouldGenerateNdkBackend() bool {
	// explicitly true if not specified to give early warning to devs
	return proptools.BoolDefault(i.properties.Backend.Ndk.Enabled, true)
}

// Returns whether the ndk backend supports applications or not. Default is `true`. `false` is
// returned when `apps_enabled` is explicitly set to false or the interface is exclusive to vendor
// (i.e. `vendor: true`). Note that the ndk_platform backend (which will be removed in the future)
// is not affected by this. In other words, it is always exclusive for the platform, as its name
// clearly shows.
func (i *aidlInterface) shouldGenerateAppNdkBackend() bool {
	return i.shouldGenerateNdkBackend() &&
		proptools.BoolDefault(i.properties.Backend.Ndk.Apps_enabled, true) &&
		!i.SocSpecific()
}

func (i *aidlInterface) shouldGenerateRustBackend() bool {
	return i.properties.Backend.Rust.Enabled != nil && *i.properties.Backend.Rust.Enabled
}

func (i *aidlInterface) minSdkVersion(lang string) *string {
	var ver *string
	switch lang {
	case langCpp:
		ver = i.properties.Backend.Cpp.Min_sdk_version
	case langJava:
		ver = i.properties.Backend.Java.Min_sdk_version
	case langNdk, langNdkPlatform:
		ver = i.properties.Backend.Ndk.Min_sdk_version
	case langRust:
		ver = i.properties.Backend.Rust.Min_sdk_version
	default:
		panic(fmt.Errorf("unsupported language backend %q\n", lang))
	}
	if ver == nil {
		return i.properties.Min_sdk_version
	}
	return ver
}

// Dep to *-api module(aidlApi)
type apiDepTag struct {
	blueprint.BaseDependencyTag
	name string
}

type importInterfaceDepTag struct {
	blueprint.BaseDependencyTag
	anImport string
}

type interfaceDepTag struct {
	blueprint.BaseDependencyTag
}

var (
	// Dep from *-source (aidlGenRule) to *-api (aidlApi)
	apiDep = apiDepTag{name: "api"}
	// Dep from *-api (aidlApi) to *-api (aidlApi), representing imported interfaces
	importApiDep = apiDepTag{name: "imported-api"}
	// Dep to original *-interface (aidlInterface)
	interfaceDep = interfaceDepTag{}
)

func addImportedInterfaceDeps(ctx android.BottomUpMutatorContext, imports []string) {
	for _, anImport := range imports {
		name, _ := parseModuleWithVersion(anImport)
		ctx.AddDependency(ctx.Module(), importInterfaceDepTag{anImport: anImport}, name+aidlInterfaceSuffix)
	}
}

// Run custom "Deps" mutator between AIDL modules created at LoadHook stage.
// We can't use the "DepsMutator" for these dependencies because
// - We need to create library modules (cc/java/...) before "arch" mutator. Note that cc_library
//   should be mutated by os/image/arch mutators as well.
// - When creating library modules, we need to access the original interface and its imported
//   interfaces to determine which version to use. See aidlInterface.getImportWithVersion.
func addInterfaceDeps(mctx android.BottomUpMutatorContext) {
	switch i := mctx.Module().(type) {
	case *aidlInterface:
		// In fact this isn't necessary because soong checks dependencies on undefined modules.
		// But since aidl_interface overrides its name internally, this provides better error message.
		for _, anImportWithVersion := range i.properties.Imports {
			anImport, _ := parseModuleWithVersion(anImportWithVersion)
			if !mctx.OtherModuleExists(anImport + aidlInterfaceSuffix) {
				if !mctx.Config().AllowMissingDependencies() {
					mctx.PropertyErrorf("imports", "Import does not exist: "+anImport)
				}
			}
		}
		if mctx.Failed() {
			return
		}
		addImportedInterfaceDeps(mctx, i.properties.Imports)
	case *aidlImplementationGenerator:
		mctx.AddDependency(i, interfaceDep, i.properties.AidlInterfaceName+aidlInterfaceSuffix)
		addImportedInterfaceDeps(mctx, i.properties.Imports)
	case *rust.Module:
		for _, props := range i.GetProperties() {
			if sp, ok := props.(*aidlRustSourceProviderProperties); ok {
				mctx.AddDependency(i, interfaceDep, sp.AidlInterfaceName+aidlInterfaceSuffix)
				addImportedInterfaceDeps(mctx, sp.Imports)
				break
			}
		}
	case *aidlApi:
		mctx.AddDependency(i, interfaceDep, i.properties.BaseName+aidlInterfaceSuffix)
		addImportedInterfaceDeps(mctx, i.properties.Imports)
		for _, anImport := range i.properties.Imports {
			name, _ := parseModuleWithVersion(anImport)
			mctx.AddDependency(i, importApiDep, name+aidlApiSuffix)
		}
	case *aidlGenRule:
		mctx.AddDependency(i, interfaceDep, i.properties.BaseName+aidlInterfaceSuffix)
		addImportedInterfaceDeps(mctx, i.properties.Imports)
		if !proptools.Bool(i.properties.Unstable) {
			// for checkapi timestamps
			mctx.AddDependency(i, apiDep, i.properties.BaseName+aidlApiSuffix)
		}
	}
}

// checkImports checks if "import:" property is valid.
// In fact, this isn't necessary because Soong can check/report when we add a dependency to
// undefined/unknown module. But module names are very implementation specific and may not be easy
// to understand. For example, when foo (with java enabled) depends on bar (with java disabled), the
// error message would look like "foo-V2-java depends on unknown module `bar-V3-java`", which isn't
// clear that backend.java.enabled should be turned on.
func checkImports(mctx android.BottomUpMutatorContext) {
	if i, ok := mctx.Module().(*aidlInterface); ok {
		mctx.VisitDirectDeps(func(dep android.Module) {
			tag, ok := mctx.OtherModuleDependencyTag(dep).(importInterfaceDepTag)
			if !ok {
				return
			}
			other := dep.(*aidlInterface)
			anImport := other.ModuleBase.Name()
			anImportWithVersion := tag.anImport
			_, version := parseModuleWithVersion(tag.anImport)
			if version != "" {
				candidateVersions := concat(other.getVersions(), []string{other.nextVersion()})
				if !android.InList(version, candidateVersions) {
					mctx.PropertyErrorf("imports", "%q depends on %q version %q(%q), which doesn't exist. The version must be one of %q", i.ModuleBase.Name(), anImport, version, anImportWithVersion, candidateVersions)
				}
			}
			if i.shouldGenerateJavaBackend() && !other.shouldGenerateJavaBackend() {
				mctx.PropertyErrorf("backend.java.enabled",
					"Java backend not enabled in the imported AIDL interface %q", anImport)
			}

			if i.shouldGenerateCppBackend() && !other.shouldGenerateCppBackend() {
				mctx.PropertyErrorf("backend.cpp.enabled",
					"C++ backend not enabled in the imported AIDL interface %q", anImport)
			}

			if i.shouldGenerateNdkBackend() && !other.shouldGenerateNdkBackend() {
				mctx.PropertyErrorf("backend.ndk.enabled",
					"NDK backend not enabled in the imported AIDL interface %q", anImport)
			}

			if i.shouldGenerateRustBackend() && !other.shouldGenerateRustBackend() {
				mctx.PropertyErrorf("backend.rust.enabled",
					"Rust backend not enabled in the imported AIDL interface %q", anImport)
			}
		})
	}
}

func (i *aidlInterface) checkGenTrace(mctx android.LoadHookContext) {
	if !proptools.Bool(i.properties.Gen_trace) {
		return
	}
	if i.shouldGenerateJavaBackend() && !proptools.Bool(i.properties.Backend.Java.Platform_apis) {
		mctx.PropertyErrorf("gen_trace", "must be false when Java backend is enabled and platform_apis is false")
	}
}

func (i *aidlInterface) checkStability(mctx android.LoadHookContext) {
	if i.properties.Stability == nil {
		return
	}

	if proptools.Bool(i.properties.Unstable) {
		mctx.PropertyErrorf("stability", "must be empty when \"unstable\" is true")
	}

	// TODO(b/136027762): should we allow more types of stability (e.g. for APEX) or
	// should we switch this flag to be something like "vintf { enabled: true }"
	isVintf := "vintf" == proptools.String(i.properties.Stability)
	if !isVintf {
		mctx.PropertyErrorf("stability", "must be empty or \"vintf\"")
	}
}
func (i *aidlInterface) checkVersions(mctx android.LoadHookContext) {
	if len(i.properties.Versions) > 0 && len(i.properties.Versions_with_info) > 0 {
		mctx.ModuleErrorf("versions:%q and versions_with_info:%q cannot be used at the same time. Use versions_with_info instead of versions.", i.properties.Versions, i.properties.Versions_with_info)
	}

	if len(i.properties.Versions) > 0 {
		i.properties.VersionsInternal = make([]string, len(i.properties.Versions))
		copy(i.properties.VersionsInternal, i.properties.Versions)
	} else if len(i.properties.Versions_with_info) > 0 {
		i.properties.VersionsInternal = make([]string, len(i.properties.Versions_with_info))
		for idx, value := range i.properties.Versions_with_info {
			i.properties.VersionsInternal[idx] = value.Version
			for _, im := range value.Imports {
				if !hasVersionSuffix(im) {
					mctx.ModuleErrorf("imports in versions_with_info must specify its version, but %s. Add a version suffix(such as %s-V1).", im, im)
					return
				}
			}
		}
	}

	versions := make(map[string]bool)
	intVersions := make([]int, 0, len(i.getVersions()))
	for _, ver := range i.getVersions() {
		if _, dup := versions[ver]; dup {
			mctx.PropertyErrorf("versions", "duplicate found", ver)
			continue
		}
		versions[ver] = true
		n, err := strconv.Atoi(ver)
		if err != nil {
			mctx.PropertyErrorf("versions", "%q is not an integer", ver)
			continue
		}
		if n <= 0 {
			mctx.PropertyErrorf("versions", "should be > 0, but is %v", ver)
			continue
		}
		intVersions = append(intVersions, n)

	}
	if !mctx.Failed() && !sort.IntsAreSorted(intVersions) {
		mctx.PropertyErrorf("versions", "should be sorted, but is %v", i.getVersions())
	}
}
func (i *aidlInterface) checkVndkUseVersion(mctx android.LoadHookContext) {
	if i.properties.Vndk_use_version == nil {
		return
	}
	if !i.hasVersion() {
		mctx.PropertyErrorf("vndk_use_version", "This does not make sense when no 'versions' are specified.")

	}
	if *i.properties.Vndk_use_version == i.nextVersion() {
		return
	}
	for _, ver := range i.getVersions() {
		if *i.properties.Vndk_use_version == ver {
			return
		}
	}
	mctx.PropertyErrorf("vndk_use_version", "Specified version %q does not exist", *i.properties.Vndk_use_version)
}

func (i *aidlInterface) nextVersion() string {
	if proptools.Bool(i.properties.Unstable) {
		return ""
	}
	return nextVersion(i.getVersions())
}

func nextVersion(versions []string) string {
	if len(versions) == 0 {
		return "1"
	}
	ver := versions[len(versions)-1]
	i, err := strconv.Atoi(ver)
	if err != nil {
		panic(err)
	}
	return strconv.Itoa(i + 1)
}

func (i *aidlInterface) latestVersion() string {
	if !i.hasVersion() {
		return "0"
	}
	return i.getVersions()[len(i.getVersions())-1]
}

func (i *aidlInterface) hasVersion() bool {
	return len(i.getVersions()) > 0
}

func (i *aidlInterface) getVersions() []string {
	return i.properties.VersionsInternal
}

func hasVersionSuffix(moduleName string) bool {
	hasVersionSuffix, _ := regexp.MatchString("-V\\d+$", moduleName)
	return hasVersionSuffix
}

func parseModuleWithVersion(moduleName string) (string, string) {
	if hasVersionSuffix(moduleName) {
		versionIdx := strings.LastIndex(moduleName, "-V")
		if versionIdx == -1 {
			panic("-V must exist in this context")
		}
		return moduleName[:versionIdx], moduleName[versionIdx+len("-V"):]
	}
	return moduleName, ""
}

func trimVersionSuffixInList(moduleNames []string) []string {
	return wrapFunc("", moduleNames, "", func(moduleName string) string {
		moduleNameWithoutVersion, _ := parseModuleWithVersion(moduleName)
		return moduleNameWithoutVersion
	})
}

func aidlInterfaceHook(mctx android.LoadHookContext, i *aidlInterface) {
	if hasVersionSuffix(i.ModuleBase.Name()) {
		mctx.PropertyErrorf("name", "aidl_interface should not have '-V<number> suffix")
	}
	if !isRelativePath(i.properties.Local_include_dir) {
		mctx.PropertyErrorf("local_include_dir", "must be relative path: "+i.properties.Local_include_dir)
	}

	i.checkStability(mctx)
	i.checkVersions(mctx)
	i.checkVndkUseVersion(mctx)
	i.checkGenTrace(mctx)

	if mctx.Failed() {
		return
	}

	var libs []string

	unstable := proptools.Bool(i.properties.Unstable)

	if unstable {
		if i.hasVersion() {
			mctx.PropertyErrorf("versions", "cannot have versions for an unstable interface")
			return
		}
		if i.properties.Stability != nil {
			mctx.ModuleErrorf("unstable:true and stability:%q cannot happen at the same time", i.properties.Stability)
			return
		}
	}

	sdkIsFinal := !mctx.Config().DefaultAppTargetSdk(mctx).IsPreview()
	requireFrozenNoOwner := i.Owner() == "" && (sdkIsFinal || mctx.Config().IsEnvTrue("AIDL_FROZEN_REL"))
	requireFrozenWithOwner := i.Owner() != "" && android.InList(i.Owner(), strings.Fields(mctx.Config().Getenv("AIDL_FROZEN_OWNERS")))
	requireFrozenByOwner := requireFrozenNoOwner || requireFrozenWithOwner

	// Two different types of 'unstable' here
	// - 'unstable: true' meaning the module is never stable
	// - current unfrozen ToT version
	//
	// OEM branches may remove 'i.Owner()' here to apply the check to all interfaces, in
	// addition to core platform interfaces. Otherwise, we rely on vts_treble_vintf_vendor_test.
	requireFrozenVersion := !unstable && requireFrozenByOwner

	// surface error early, main check is via checkUnstableModuleMutator
	if requireFrozenVersion && !i.hasVersion() {
		mctx.PropertyErrorf("versions", "must be set (need to be frozen) when \"unstable\" is false, PLATFORM_VERSION_CODENAME is REL, and \"owner\" property is missing.")
	}

	versions := i.getVersions()
	nextVersion := i.nextVersion()
	shouldGenerateLangBackendMap := map[string]bool{
		langCpp:  i.shouldGenerateCppBackend(),
		langNdk:  i.shouldGenerateNdkBackend(),
		langJava: i.shouldGenerateJavaBackend(),
		langRust: i.shouldGenerateRustBackend()}

	// The ndk_platform backend is generated only when explicitly requested. This will
	// eventually be completely removed the devices in the long tail are gone.
	if mctx.DeviceConfig().GenerateAidlNdkPlatformBackend() {
		shouldGenerateLangBackendMap[langNdkPlatform] = i.shouldGenerateNdkBackend()
	}

	for lang, shouldGenerate := range shouldGenerateLangBackendMap {
		if !shouldGenerate {
			continue
		}
		libs = append(libs, addLibrary(mctx, i, nextVersion, lang, requireFrozenVersion))
		for _, version := range versions {
			libs = append(libs, addLibrary(mctx, i, version, lang, false))
		}
	}

	if unstable {
		apiDirRoot := filepath.Join(aidlApiDir, i.ModuleBase.Name())
		aidlDumps, _ := mctx.GlobWithDeps(filepath.Join(mctx.ModuleDir(), apiDirRoot, "**/*.aidl"), nil)
		if len(aidlDumps) != 0 {
			mctx.PropertyErrorf("unstable", "The interface is configured as unstable, "+
				"but API dumps exist under %q. Unstable interface cannot have dumps.", apiDirRoot)
		}
	} else {
		addApiModule(mctx, i)
	}

	if proptools.Bool(i.properties.VndkProperties.Vndk.Enabled) {
		if "vintf" != proptools.String(i.properties.Stability) {
			mctx.PropertyErrorf("stability", "must be \"vintf\" if the module is for VNDK.")
		}
	}

	// Reserve this module name for future use
	mctx.CreateModule(phony.PhonyFactory, &phonyProperties{
		Name: proptools.StringPtr(i.ModuleBase.Name()),
	})

	i.internalModuleNames = libs
}

func (i *aidlInterface) commonBackendProperties(lang string) CommonBackendProperties {
	switch lang {
	case langCpp:
		return i.properties.Backend.Cpp.CommonBackendProperties
	case langJava:
		return i.properties.Backend.Java.CommonBackendProperties
	case langNdk, langNdkPlatform:
		return i.properties.Backend.Ndk.CommonBackendProperties
	case langRust:
		return i.properties.Backend.Rust.CommonBackendProperties
	default:
		panic(fmt.Errorf("unsupported language backend %q\n", lang))
	}
}

func (i *aidlInterface) Name() string {
	return i.ModuleBase.Name() + aidlInterfaceSuffix
}

func (i *aidlInterface) GenerateAndroidBuildActions(ctx android.ModuleContext) {
	srcs, _ := getPaths(ctx, i.properties.Srcs, i.properties.Local_include_dir)
	for _, src := range srcs {
		computedType := strings.TrimSuffix(strings.ReplaceAll(src.Rel(), "/", "."), ".aidl")
		i.computedTypes = append(i.computedTypes, computedType)
	}

	i.preprocessed = make(map[string]android.WritablePath)
	// generate (len(versions) + 1) preprocessed.aidl files
	for _, version := range concat(i.getVersions(), []string{i.nextVersion()}) {
		i.preprocessed[version] = i.buildPreprocessed(ctx, version)
	}
	// helpful aliases
	if !proptools.Bool(i.properties.Unstable) {
		if i.hasVersion() {
			i.preprocessed["latest"] = i.preprocessed[i.latestVersion()]
		} else {
			// when we have no frozen versions yet, use "next version" as latest
			i.preprocessed["latest"] = i.preprocessed[i.nextVersion()]
		}
		i.preprocessed[""] = i.preprocessed[i.nextVersion()]
	}
}

func (i *aidlInterface) getImportsForVersion(version string) []string {
	// `Imports` is used when version == i.nextVersion() or`versions` is defined instead of `versions_with_info`
	importsSrc := i.properties.Imports
	for _, v := range i.properties.Versions_with_info {
		if v.Version == version {
			importsSrc = v.Imports
			break
		}
	}
	imports := make([]string, len(importsSrc))
	copy(imports, importsSrc)

	return imports
}

func (i *aidlInterface) getImports(version string) map[string]string {
	imports := make(map[string]string)
	imports_src := i.getImportsForVersion(version)

	useLatestStable := !proptools.Bool(i.properties.Unstable) && version != "" && version != i.nextVersion()
	for _, importString := range imports_src {
		name, targetVersion := parseModuleWithVersion(importString)
		if targetVersion == "" && useLatestStable {
			targetVersion = "latest"
		}
		imports[name] = targetVersion
	}
	return imports
}

// generate preprocessed.aidl which contains only types with evaluated constants.
// "imports" will use preprocessed.aidl with -p flag to avoid parsing the entire transitive list
// of dependencies.
func (i *aidlInterface) buildPreprocessed(ctx android.ModuleContext, version string) android.WritablePath {
	deps := getDeps(ctx, i.getImports(version))

	preprocessed := android.PathForModuleOut(ctx, version, "preprocessed.aidl")
	rb := android.NewRuleBuilder(pctx, ctx)
	srcs, root_dir := i.srcsForVersion(ctx, version)

	if len(srcs) == 0 {
		ctx.PropertyErrorf("srcs", "No sources for a previous version in %v. Was a version manually added to .bp file? This is added automatically by <module>-freeze-api.", root_dir)
	}

	paths, imports := getPaths(ctx, srcs, root_dir)

	preprocessCommand := rb.Command().BuiltTool("aidl").
		FlagWithOutput("--preprocess ", preprocessed).
		Flag("--structured")
	if i.properties.Stability != nil {
		preprocessCommand.FlagWithArg("--stability ", *i.properties.Stability)
	}
	preprocessCommand.FlagForEachInput("-p", deps.preprocessed)
	preprocessCommand.FlagForEachArg("-I", concat(imports, i.properties.Include_dirs))
	preprocessCommand.Inputs(paths)
	name := i.BaseModuleName()
	if version != "" {
		name += "/" + version
	}
	rb.Build("export_"+name, "export types for "+name)
	return preprocessed
}

func (i *aidlInterface) DepsMutator(ctx android.BottomUpMutatorContext) {
	ctx.AddReverseDependency(ctx.Module(), nil, aidlMetadataSingletonName)
}

func aidlInterfaceFactory() android.Module {
	i := &aidlInterface{}
	i.AddProperties(&i.properties)
	android.InitAndroidModule(i)
	android.AddLoadHook(i, func(ctx android.LoadHookContext) { aidlInterfaceHook(ctx, i) })
	return i
}
