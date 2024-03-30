// Copyright 2019 The Android Open Source Project
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

package rust

import (
	"fmt"
	"strings"

	"github.com/google/blueprint"
	"github.com/google/blueprint/proptools"

	"android/soong/android"
	"android/soong/bloaty"
	"android/soong/cc"
	cc_config "android/soong/cc/config"
	"android/soong/fuzz"
	"android/soong/rust/config"
	"android/soong/snapshot"
)

var pctx = android.NewPackageContext("android/soong/rust")

func init() {
	// Only allow rust modules to be defined for certain projects

	android.AddNeverAllowRules(
		android.NeverAllow().
			NotIn(append(config.RustAllowedPaths, config.DownstreamRustAllowedPaths...)...).
			ModuleType(config.RustModuleTypes...))

	android.RegisterModuleType("rust_defaults", defaultsFactory)
	android.PreDepsMutators(func(ctx android.RegisterMutatorsContext) {
		ctx.BottomUp("rust_libraries", LibraryMutator).Parallel()
		ctx.BottomUp("rust_stdlinkage", LibstdMutator).Parallel()
		ctx.BottomUp("rust_begin", BeginMutator).Parallel()

	})
	android.PostDepsMutators(func(ctx android.RegisterMutatorsContext) {
		ctx.BottomUp("rust_sanitizers", rustSanitizerRuntimeMutator).Parallel()
	})
	pctx.Import("android/soong/rust/config")
	pctx.ImportAs("cc_config", "android/soong/cc/config")
}

type Flags struct {
	GlobalRustFlags []string // Flags that apply globally to rust
	GlobalLinkFlags []string // Flags that apply globally to linker
	RustFlags       []string // Flags that apply to rust
	LinkFlags       []string // Flags that apply to linker
	ClippyFlags     []string // Flags that apply to clippy-driver, during the linting
	RustdocFlags    []string // Flags that apply to rustdoc
	Toolchain       config.Toolchain
	Coverage        bool
	Clippy          bool
}

type BaseProperties struct {
	AndroidMkRlibs         []string
	AndroidMkDylibs        []string
	AndroidMkProcMacroLibs []string
	AndroidMkSharedLibs    []string
	AndroidMkStaticLibs    []string

	ImageVariationPrefix string `blueprint:"mutated"`
	VndkVersion          string `blueprint:"mutated"`
	SubName              string `blueprint:"mutated"`

	// SubName is used by CC for tracking image variants / SDK versions. RustSubName is used for Rust-specific
	// subnaming which shouldn't be visible to CC modules (such as the rlib stdlinkage subname). This should be
	// appended before SubName.
	RustSubName string `blueprint:"mutated"`

	// Set by imageMutator
	CoreVariantNeeded          bool     `blueprint:"mutated"`
	VendorRamdiskVariantNeeded bool     `blueprint:"mutated"`
	RamdiskVariantNeeded       bool     `blueprint:"mutated"`
	RecoveryVariantNeeded      bool     `blueprint:"mutated"`
	ExtraVariants              []string `blueprint:"mutated"`

	// Allows this module to use non-APEX version of libraries. Useful
	// for building binaries that are started before APEXes are activated.
	Bootstrap *bool

	// Used by vendor snapshot to record dependencies from snapshot modules.
	SnapshotSharedLibs []string `blueprint:"mutated"`
	SnapshotStaticLibs []string `blueprint:"mutated"`

	// Make this module available when building for ramdisk.
	// On device without a dedicated recovery partition, the module is only
	// available after switching root into
	// /first_stage_ramdisk. To expose the module before switching root, install
	// the recovery variant instead.
	Ramdisk_available *bool

	// Make this module available when building for vendor ramdisk.
	// On device without a dedicated recovery partition, the module is only
	// available after switching root into
	// /first_stage_ramdisk. To expose the module before switching root, install
	// the recovery variant instead
	Vendor_ramdisk_available *bool

	// Normally Soong uses the directory structure to decide which modules
	// should be included (framework) or excluded (non-framework) from the
	// different snapshots (vendor, recovery, etc.), but this property
	// allows a partner to exclude a module normally thought of as a
	// framework module from the vendor snapshot.
	Exclude_from_vendor_snapshot *bool

	// Normally Soong uses the directory structure to decide which modules
	// should be included (framework) or excluded (non-framework) from the
	// different snapshots (vendor, recovery, etc.), but this property
	// allows a partner to exclude a module normally thought of as a
	// framework module from the recovery snapshot.
	Exclude_from_recovery_snapshot *bool

	// Make this module available when building for recovery
	Recovery_available *bool

	// Minimum sdk version that the artifact should support when it runs as part of mainline modules(APEX).
	Min_sdk_version *string

	HideFromMake   bool `blueprint:"mutated"`
	PreventInstall bool `blueprint:"mutated"`

	Installable *bool
}

type Module struct {
	fuzz.FuzzModule

	VendorProperties cc.VendorProperties

	Properties BaseProperties

	hod      android.HostOrDeviceSupported
	multilib android.Multilib

	makeLinkType string

	afdo             *afdo
	compiler         compiler
	coverage         *coverage
	clippy           *clippy
	sanitize         *sanitize
	cachedToolchain  config.Toolchain
	sourceProvider   SourceProvider
	subAndroidMkOnce map[SubAndroidMkProvider]bool

	// Output file to be installed, may be stripped or unstripped.
	outputFile android.OptionalPath

	docTimestampFile android.OptionalPath

	hideApexVariantFromMake bool

	// For apex variants, this is set as apex.min_sdk_version
	apexSdkVersion android.ApiLevel
}

func (mod *Module) Header() bool {
	//TODO: If Rust libraries provide header variants, this needs to be updated.
	return false
}

func (mod *Module) SetPreventInstall() {
	mod.Properties.PreventInstall = true
}

func (mod *Module) SetHideFromMake() {
	mod.Properties.HideFromMake = true
}

func (mod *Module) HiddenFromMake() bool {
	return mod.Properties.HideFromMake
}

func (mod *Module) SanitizePropDefined() bool {
	// Because compiler is not set for some Rust modules where sanitize might be set, check that compiler is also not
	// nil since we need compiler to actually sanitize.
	return mod.sanitize != nil && mod.compiler != nil
}

func (mod *Module) IsPrebuilt() bool {
	if _, ok := mod.compiler.(*prebuiltLibraryDecorator); ok {
		return true
	}
	return false
}

func (mod *Module) OutputFiles(tag string) (android.Paths, error) {
	switch tag {
	case "":
		if mod.sourceProvider != nil && (mod.compiler == nil || mod.compiler.Disabled()) {
			return mod.sourceProvider.Srcs(), nil
		} else {
			if mod.OutputFile().Valid() {
				return android.Paths{mod.OutputFile().Path()}, nil
			}
			return android.Paths{}, nil
		}
	default:
		return nil, fmt.Errorf("unsupported module reference tag %q", tag)
	}
}

func (mod *Module) SelectedStl() string {
	return ""
}

func (mod *Module) NonCcVariants() bool {
	if mod.compiler != nil {
		if _, ok := mod.compiler.(libraryInterface); ok {
			return false
		}
	}
	panic(fmt.Errorf("NonCcVariants called on non-library module: %q", mod.BaseModuleName()))
}

func (mod *Module) Static() bool {
	if mod.compiler != nil {
		if library, ok := mod.compiler.(libraryInterface); ok {
			return library.static()
		}
	}
	return false
}

func (mod *Module) Shared() bool {
	if mod.compiler != nil {
		if library, ok := mod.compiler.(libraryInterface); ok {
			return library.shared()
		}
	}
	return false
}

func (mod *Module) Dylib() bool {
	if mod.compiler != nil {
		if library, ok := mod.compiler.(libraryInterface); ok {
			return library.dylib()
		}
	}
	return false
}

func (mod *Module) Rlib() bool {
	if mod.compiler != nil {
		if library, ok := mod.compiler.(libraryInterface); ok {
			return library.rlib()
		}
	}
	return false
}

func (mod *Module) Binary() bool {
	if binary, ok := mod.compiler.(binaryInterface); ok {
		return binary.binary()
	}
	return false
}

func (mod *Module) StaticExecutable() bool {
	if !mod.Binary() {
		return false
	}
	return mod.StaticallyLinked()
}

func (mod *Module) Object() bool {
	// Rust has no modules which produce only object files.
	return false
}

func (mod *Module) Toc() android.OptionalPath {
	if mod.compiler != nil {
		if lib, ok := mod.compiler.(libraryInterface); ok {
			return lib.toc()
		}
	}
	panic(fmt.Errorf("Toc() called on non-library module: %q", mod.BaseModuleName()))
}

func (mod *Module) UseSdk() bool {
	return false
}

func (mod *Module) RelativeInstallPath() string {
	if mod.compiler != nil {
		return mod.compiler.relativeInstallPath()
	}
	return ""
}

func (mod *Module) UseVndk() bool {
	return mod.Properties.VndkVersion != ""
}

func (mod *Module) Bootstrap() bool {
	return Bool(mod.Properties.Bootstrap)
}

func (mod *Module) MustUseVendorVariant() bool {
	return true
}

func (mod *Module) SubName() string {
	return mod.Properties.SubName
}

func (mod *Module) IsVndk() bool {
	// TODO(b/165791368)
	return false
}

func (mod *Module) IsVndkExt() bool {
	return false
}

func (mod *Module) IsVndkSp() bool {
	return false
}

func (mod *Module) IsVndkPrebuiltLibrary() bool {
	// Rust modules do not provide VNDK prebuilts
	return false
}

func (mod *Module) IsVendorPublicLibrary() bool {
	return mod.VendorProperties.IsVendorPublicLibrary
}

func (mod *Module) SdkAndPlatformVariantVisibleToMake() bool {
	// Rust modules to not provide Sdk variants
	return false
}

func (c *Module) IsVndkPrivate() bool {
	return false
}

func (c *Module) IsLlndk() bool {
	return false
}

func (c *Module) IsLlndkPublic() bool {
	return false
}

func (mod *Module) KernelHeadersDecorator() bool {
	return false
}

func (m *Module) NeedsLlndkVariants() bool {
	return false
}

func (m *Module) NeedsVendorPublicLibraryVariants() bool {
	return false
}

func (mod *Module) HasLlndkStubs() bool {
	return false
}

func (mod *Module) StubsVersion() string {
	panic(fmt.Errorf("StubsVersion called on non-versioned module: %q", mod.BaseModuleName()))
}

func (mod *Module) SdkVersion() string {
	return ""
}

func (mod *Module) AlwaysSdk() bool {
	return false
}

func (mod *Module) IsSdkVariant() bool {
	return false
}

func (mod *Module) SplitPerApiLevel() bool {
	return false
}

type Deps struct {
	Dylibs          []string
	Rlibs           []string
	Rustlibs        []string
	Stdlibs         []string
	ProcMacros      []string
	SharedLibs      []string
	StaticLibs      []string
	WholeStaticLibs []string
	HeaderLibs      []string

	// Used for data dependencies adjacent to tests
	DataLibs []string
	DataBins []string

	CrtBegin, CrtEnd []string
}

type PathDeps struct {
	DyLibs        RustLibraries
	RLibs         RustLibraries
	SharedLibs    android.Paths
	SharedLibDeps android.Paths
	StaticLibs    android.Paths
	ProcMacros    RustLibraries
	AfdoProfiles  android.Paths

	// depFlags and depLinkFlags are rustc and linker (clang) flags.
	depFlags     []string
	depLinkFlags []string

	// linkDirs are link paths passed via -L to rustc. linkObjects are objects passed directly to the linker.
	// Both of these are exported and propagate to dependencies.
	linkDirs    []string
	linkObjects []string

	// Used by bindgen modules which call clang
	depClangFlags         []string
	depIncludePaths       android.Paths
	depGeneratedHeaders   android.Paths
	depSystemIncludePaths android.Paths

	CrtBegin android.Paths
	CrtEnd   android.Paths

	// Paths to generated source files
	SrcDeps          android.Paths
	srcProviderFiles android.Paths
}

type RustLibraries []RustLibrary

type RustLibrary struct {
	Path      android.Path
	CrateName string
}

type compiler interface {
	initialize(ctx ModuleContext)
	compilerFlags(ctx ModuleContext, flags Flags) Flags
	cfgFlags(ctx ModuleContext, flags Flags) Flags
	featureFlags(ctx ModuleContext, flags Flags) Flags
	compilerProps() []interface{}
	compile(ctx ModuleContext, flags Flags, deps PathDeps) android.Path
	compilerDeps(ctx DepsContext, deps Deps) Deps
	crateName() string
	rustdoc(ctx ModuleContext, flags Flags, deps PathDeps) android.OptionalPath

	// Output directory in which source-generated code from dependencies is
	// copied. This is equivalent to Cargo's OUT_DIR variable.
	CargoOutDir() android.OptionalPath

	// CargoPkgVersion returns the value of the Cargo_pkg_version property.
	CargoPkgVersion() string

	// CargoEnvCompat returns whether Cargo environment variables should be used.
	CargoEnvCompat() bool

	inData() bool
	install(ctx ModuleContext)
	relativeInstallPath() string
	everInstallable() bool

	nativeCoverage() bool

	Disabled() bool
	SetDisabled()

	stdLinkage(ctx *depsContext) RustLinkage

	unstrippedOutputFilePath() android.Path
	strippedOutputFilePath() android.OptionalPath
}

type exportedFlagsProducer interface {
	exportLinkDirs(...string)
	exportLinkObjects(...string)
}

type flagExporter struct {
	linkDirs    []string
	linkObjects []string
}

func (flagExporter *flagExporter) exportLinkDirs(dirs ...string) {
	flagExporter.linkDirs = android.FirstUniqueStrings(append(flagExporter.linkDirs, dirs...))
}

func (flagExporter *flagExporter) exportLinkObjects(flags ...string) {
	flagExporter.linkObjects = android.FirstUniqueStrings(append(flagExporter.linkObjects, flags...))
}

func (flagExporter *flagExporter) setProvider(ctx ModuleContext) {
	ctx.SetProvider(FlagExporterInfoProvider, FlagExporterInfo{
		LinkDirs:    flagExporter.linkDirs,
		LinkObjects: flagExporter.linkObjects,
	})
}

var _ exportedFlagsProducer = (*flagExporter)(nil)

func NewFlagExporter() *flagExporter {
	return &flagExporter{}
}

type FlagExporterInfo struct {
	Flags       []string
	LinkDirs    []string // TODO: this should be android.Paths
	LinkObjects []string // TODO: this should be android.Paths
}

var FlagExporterInfoProvider = blueprint.NewProvider(FlagExporterInfo{})

func (mod *Module) isCoverageVariant() bool {
	return mod.coverage.Properties.IsCoverageVariant
}

var _ cc.Coverage = (*Module)(nil)

func (mod *Module) IsNativeCoverageNeeded(ctx android.BaseModuleContext) bool {
	return mod.coverage != nil && mod.coverage.Properties.NeedCoverageVariant
}

func (mod *Module) VndkVersion() string {
	return mod.Properties.VndkVersion
}

func (mod *Module) PreventInstall() bool {
	return mod.Properties.PreventInstall
}

func (mod *Module) MarkAsCoverageVariant(coverage bool) {
	mod.coverage.Properties.IsCoverageVariant = coverage
}

func (mod *Module) EnableCoverageIfNeeded() {
	mod.coverage.Properties.CoverageEnabled = mod.coverage.Properties.NeedCoverageBuild
}

func defaultsFactory() android.Module {
	return DefaultsFactory()
}

type Defaults struct {
	android.ModuleBase
	android.DefaultsModuleBase
}

func DefaultsFactory(props ...interface{}) android.Module {
	module := &Defaults{}

	module.AddProperties(props...)
	module.AddProperties(
		&BaseProperties{},
		&cc.AfdoProperties{},
		&cc.VendorProperties{},
		&BenchmarkProperties{},
		&BindgenProperties{},
		&BaseCompilerProperties{},
		&BinaryCompilerProperties{},
		&LibraryCompilerProperties{},
		&ProcMacroCompilerProperties{},
		&PrebuiltProperties{},
		&SourceProviderProperties{},
		&TestProperties{},
		&cc.CoverageProperties{},
		&cc.RustBindgenClangProperties{},
		&ClippyProperties{},
		&SanitizeProperties{},
	)

	android.InitDefaultsModule(module)
	return module
}

func (mod *Module) CrateName() string {
	return mod.compiler.crateName()
}

func (mod *Module) CcLibrary() bool {
	if mod.compiler != nil {
		if _, ok := mod.compiler.(libraryInterface); ok {
			return true
		}
	}
	return false
}

func (mod *Module) CcLibraryInterface() bool {
	if mod.compiler != nil {
		// use build{Static,Shared}() instead of {static,shared}() here because this might be called before
		// VariantIs{Static,Shared} is set.
		if lib, ok := mod.compiler.(libraryInterface); ok && (lib.buildShared() || lib.buildStatic()) {
			return true
		}
	}
	return false
}

func (mod *Module) UnstrippedOutputFile() android.Path {
	if mod.compiler != nil {
		return mod.compiler.unstrippedOutputFilePath()
	}
	return nil
}

func (mod *Module) IncludeDirs() android.Paths {
	if mod.compiler != nil {
		if library, ok := mod.compiler.(*libraryDecorator); ok {
			return library.includeDirs
		}
	}
	panic(fmt.Errorf("IncludeDirs called on non-library module: %q", mod.BaseModuleName()))
}

func (mod *Module) SetStatic() {
	if mod.compiler != nil {
		if library, ok := mod.compiler.(libraryInterface); ok {
			library.setStatic()
			return
		}
	}
	panic(fmt.Errorf("SetStatic called on non-library module: %q", mod.BaseModuleName()))
}

func (mod *Module) SetShared() {
	if mod.compiler != nil {
		if library, ok := mod.compiler.(libraryInterface); ok {
			library.setShared()
			return
		}
	}
	panic(fmt.Errorf("SetShared called on non-library module: %q", mod.BaseModuleName()))
}

func (mod *Module) BuildStaticVariant() bool {
	if mod.compiler != nil {
		if library, ok := mod.compiler.(libraryInterface); ok {
			return library.buildStatic()
		}
	}
	panic(fmt.Errorf("BuildStaticVariant called on non-library module: %q", mod.BaseModuleName()))
}

func (mod *Module) BuildSharedVariant() bool {
	if mod.compiler != nil {
		if library, ok := mod.compiler.(libraryInterface); ok {
			return library.buildShared()
		}
	}
	panic(fmt.Errorf("BuildSharedVariant called on non-library module: %q", mod.BaseModuleName()))
}

func (mod *Module) Module() android.Module {
	return mod
}

func (mod *Module) OutputFile() android.OptionalPath {
	return mod.outputFile
}

func (mod *Module) CoverageFiles() android.Paths {
	if mod.compiler != nil {
		return android.Paths{}
	}
	panic(fmt.Errorf("CoverageFiles called on non-library module: %q", mod.BaseModuleName()))
}

func (mod *Module) installable(apexInfo android.ApexInfo) bool {
	if !proptools.BoolDefault(mod.Installable(), mod.EverInstallable()) {
		return false
	}

	// The apex variant is not installable because it is included in the APEX and won't appear
	// in the system partition as a standalone file.
	if !apexInfo.IsForPlatform() {
		return false
	}

	return mod.OutputFile().Valid() && !mod.Properties.PreventInstall
}

func (ctx moduleContext) apexVariationName() string {
	return ctx.Provider(android.ApexInfoProvider).(android.ApexInfo).ApexVariationName
}

var _ cc.LinkableInterface = (*Module)(nil)

func (mod *Module) Init() android.Module {
	mod.AddProperties(&mod.Properties)
	mod.AddProperties(&mod.VendorProperties)

	if mod.afdo != nil {
		mod.AddProperties(mod.afdo.props()...)
	}
	if mod.compiler != nil {
		mod.AddProperties(mod.compiler.compilerProps()...)
	}
	if mod.coverage != nil {
		mod.AddProperties(mod.coverage.props()...)
	}
	if mod.clippy != nil {
		mod.AddProperties(mod.clippy.props()...)
	}
	if mod.sourceProvider != nil {
		mod.AddProperties(mod.sourceProvider.SourceProviderProps()...)
	}
	if mod.sanitize != nil {
		mod.AddProperties(mod.sanitize.props()...)
	}

	android.InitAndroidArchModule(mod, mod.hod, mod.multilib)
	android.InitApexModule(mod)

	android.InitDefaultableModule(mod)
	return mod
}

func newBaseModule(hod android.HostOrDeviceSupported, multilib android.Multilib) *Module {
	return &Module{
		hod:      hod,
		multilib: multilib,
	}
}
func newModule(hod android.HostOrDeviceSupported, multilib android.Multilib) *Module {
	module := newBaseModule(hod, multilib)
	module.afdo = &afdo{}
	module.coverage = &coverage{}
	module.clippy = &clippy{}
	module.sanitize = &sanitize{}
	return module
}

type ModuleContext interface {
	android.ModuleContext
	ModuleContextIntf
}

type BaseModuleContext interface {
	android.BaseModuleContext
	ModuleContextIntf
}

type DepsContext interface {
	android.BottomUpMutatorContext
	ModuleContextIntf
}

type ModuleContextIntf interface {
	RustModule() *Module
	toolchain() config.Toolchain
}

type depsContext struct {
	android.BottomUpMutatorContext
}

type moduleContext struct {
	android.ModuleContext
}

type baseModuleContext struct {
	android.BaseModuleContext
}

func (ctx *moduleContext) RustModule() *Module {
	return ctx.Module().(*Module)
}

func (ctx *moduleContext) toolchain() config.Toolchain {
	return ctx.RustModule().toolchain(ctx)
}

func (ctx *depsContext) RustModule() *Module {
	return ctx.Module().(*Module)
}

func (ctx *depsContext) toolchain() config.Toolchain {
	return ctx.RustModule().toolchain(ctx)
}

func (ctx *baseModuleContext) RustModule() *Module {
	return ctx.Module().(*Module)
}

func (ctx *baseModuleContext) toolchain() config.Toolchain {
	return ctx.RustModule().toolchain(ctx)
}

func (mod *Module) nativeCoverage() bool {
	// Bug: http://b/137883967 - native-bridge modules do not currently work with coverage
	if mod.Target().NativeBridge == android.NativeBridgeEnabled {
		return false
	}
	return mod.compiler != nil && mod.compiler.nativeCoverage()
}

func (mod *Module) EverInstallable() bool {
	return mod.compiler != nil &&
		// Check to see whether the module is actually ever installable.
		mod.compiler.everInstallable()
}

func (mod *Module) Installable() *bool {
	return mod.Properties.Installable
}

func (mod *Module) ProcMacro() bool {
	if pm, ok := mod.compiler.(procMacroInterface); ok {
		return pm.ProcMacro()
	}
	return false
}

func (mod *Module) toolchain(ctx android.BaseModuleContext) config.Toolchain {
	if mod.cachedToolchain == nil {
		mod.cachedToolchain = config.FindToolchain(ctx.Os(), ctx.Arch())
	}
	return mod.cachedToolchain
}

func (mod *Module) ccToolchain(ctx android.BaseModuleContext) cc_config.Toolchain {
	return cc_config.FindToolchain(ctx.Os(), ctx.Arch())
}

func (d *Defaults) GenerateAndroidBuildActions(ctx android.ModuleContext) {
}

func (mod *Module) GenerateAndroidBuildActions(actx android.ModuleContext) {
	ctx := &moduleContext{
		ModuleContext: actx,
	}

	apexInfo := actx.Provider(android.ApexInfoProvider).(android.ApexInfo)
	if !apexInfo.IsForPlatform() {
		mod.hideApexVariantFromMake = true
	}

	toolchain := mod.toolchain(ctx)
	mod.makeLinkType = cc.GetMakeLinkType(actx, mod)

	mod.Properties.SubName = cc.GetSubnameProperty(actx, mod)

	if !toolchain.Supported() {
		// This toolchain's unsupported, there's nothing to do for this mod.
		return
	}

	deps := mod.depsToPaths(ctx)
	flags := Flags{
		Toolchain: toolchain,
	}

	// Calculate rustc flags
	if mod.afdo != nil {
		flags, deps = mod.afdo.flags(ctx, flags, deps)
	}
	if mod.compiler != nil {
		flags = mod.compiler.compilerFlags(ctx, flags)
		flags = mod.compiler.cfgFlags(ctx, flags)
		flags = mod.compiler.featureFlags(ctx, flags)
	}
	if mod.coverage != nil {
		flags, deps = mod.coverage.flags(ctx, flags, deps)
	}
	if mod.clippy != nil {
		flags, deps = mod.clippy.flags(ctx, flags, deps)
	}
	if mod.sanitize != nil {
		flags, deps = mod.sanitize.flags(ctx, flags, deps)
	}

	// SourceProvider needs to call GenerateSource() before compiler calls
	// compile() so it can provide the source. A SourceProvider has
	// multiple variants (e.g. source, rlib, dylib). Only the "source"
	// variant is responsible for effectively generating the source. The
	// remaining variants relies on the "source" variant output.
	if mod.sourceProvider != nil {
		if mod.compiler.(libraryInterface).source() {
			mod.sourceProvider.GenerateSource(ctx, deps)
			mod.sourceProvider.setSubName(ctx.ModuleSubDir())
		} else {
			sourceMod := actx.GetDirectDepWithTag(mod.Name(), sourceDepTag)
			sourceLib := sourceMod.(*Module).compiler.(*libraryDecorator)
			mod.sourceProvider.setOutputFiles(sourceLib.sourceProvider.Srcs())
		}
	}

	if mod.compiler != nil && !mod.compiler.Disabled() {
		mod.compiler.initialize(ctx)
		outputFile := mod.compiler.compile(ctx, flags, deps)
		if ctx.Failed() {
			return
		}
		mod.outputFile = android.OptionalPathForPath(outputFile)
		bloaty.MeasureSizeForPaths(ctx, mod.compiler.strippedOutputFilePath(), android.OptionalPathForPath(mod.compiler.unstrippedOutputFilePath()))

		mod.docTimestampFile = mod.compiler.rustdoc(ctx, flags, deps)
		if mod.docTimestampFile.Valid() {
			ctx.CheckbuildFile(mod.docTimestampFile.Path())
		}

		// glob exported headers for snapshot, if BOARD_VNDK_VERSION is current or
		// RECOVERY_SNAPSHOT_VERSION is current.
		if lib, ok := mod.compiler.(snapshotLibraryInterface); ok {
			if cc.ShouldCollectHeadersForSnapshot(ctx, mod, apexInfo) {
				lib.collectHeadersForSnapshot(ctx, deps)
			}
		}

		apexInfo := actx.Provider(android.ApexInfoProvider).(android.ApexInfo)
		if !proptools.BoolDefault(mod.Installable(), mod.EverInstallable()) && !mod.ProcMacro() {
			// If the module has been specifically configure to not be installed then
			// hide from make as otherwise it will break when running inside make as the
			// output path to install will not be specified. Not all uninstallable
			// modules can be hidden from make as some are needed for resolving make
			// side dependencies. In particular, proc-macros need to be captured in the
			// host snapshot.
			mod.HideFromMake()
		} else if !mod.installable(apexInfo) {
			mod.SkipInstall()
		}

		// Still call install though, the installs will be stored as PackageSpecs to allow
		// using the outputs in a genrule.
		if mod.OutputFile().Valid() {
			mod.compiler.install(ctx)
			if ctx.Failed() {
				return
			}
		}

		ctx.Phony("rust", ctx.RustModule().OutputFile().Path())
	}
}

func (mod *Module) deps(ctx DepsContext) Deps {
	deps := Deps{}

	if mod.compiler != nil {
		deps = mod.compiler.compilerDeps(ctx, deps)
	}
	if mod.sourceProvider != nil {
		deps = mod.sourceProvider.SourceProviderDeps(ctx, deps)
	}

	if mod.coverage != nil {
		deps = mod.coverage.deps(ctx, deps)
	}

	if mod.sanitize != nil {
		deps = mod.sanitize.deps(ctx, deps)
	}

	deps.Rlibs = android.LastUniqueStrings(deps.Rlibs)
	deps.Dylibs = android.LastUniqueStrings(deps.Dylibs)
	deps.Rustlibs = android.LastUniqueStrings(deps.Rustlibs)
	deps.ProcMacros = android.LastUniqueStrings(deps.ProcMacros)
	deps.SharedLibs = android.LastUniqueStrings(deps.SharedLibs)
	deps.StaticLibs = android.LastUniqueStrings(deps.StaticLibs)
	deps.Stdlibs = android.LastUniqueStrings(deps.Stdlibs)
	deps.WholeStaticLibs = android.LastUniqueStrings(deps.WholeStaticLibs)
	return deps

}

type dependencyTag struct {
	blueprint.BaseDependencyTag
	name      string
	library   bool
	procMacro bool
	dynamic   bool
}

// InstallDepNeeded returns true for rlibs, dylibs, and proc macros so that they or their transitive
// dependencies (especially C/C++ shared libs) are installed as dependencies of a rust binary.
func (d dependencyTag) InstallDepNeeded() bool {
	return d.library || d.procMacro
}

var _ android.InstallNeededDependencyTag = dependencyTag{}

func (d dependencyTag) LicenseAnnotations() []android.LicenseAnnotation {
	if d.library && d.dynamic {
		return []android.LicenseAnnotation{android.LicenseAnnotationSharedDependency}
	}
	return nil
}

var _ android.LicenseAnnotationsDependencyTag = dependencyTag{}

var (
	customBindgenDepTag = dependencyTag{name: "customBindgenTag"}
	rlibDepTag          = dependencyTag{name: "rlibTag", library: true}
	dylibDepTag         = dependencyTag{name: "dylib", library: true, dynamic: true}
	procMacroDepTag     = dependencyTag{name: "procMacro", procMacro: true}
	testPerSrcDepTag    = dependencyTag{name: "rust_unit_tests"}
	sourceDepTag        = dependencyTag{name: "source"}
	dataLibDepTag       = dependencyTag{name: "data lib"}
	dataBinDepTag       = dependencyTag{name: "data bin"}
)

func IsDylibDepTag(depTag blueprint.DependencyTag) bool {
	tag, ok := depTag.(dependencyTag)
	return ok && tag == dylibDepTag
}

func IsRlibDepTag(depTag blueprint.DependencyTag) bool {
	tag, ok := depTag.(dependencyTag)
	return ok && tag == rlibDepTag
}

type autoDep struct {
	variation string
	depTag    dependencyTag
}

var (
	rlibVariation  = "rlib"
	dylibVariation = "dylib"
	rlibAutoDep    = autoDep{variation: rlibVariation, depTag: rlibDepTag}
	dylibAutoDep   = autoDep{variation: dylibVariation, depTag: dylibDepTag}
)

type autoDeppable interface {
	autoDep(ctx android.BottomUpMutatorContext) autoDep
}

func (mod *Module) begin(ctx BaseModuleContext) {
	if mod.coverage != nil {
		mod.coverage.begin(ctx)
	}
	if mod.sanitize != nil {
		mod.sanitize.begin(ctx)
	}
}

func (mod *Module) Prebuilt() *android.Prebuilt {
	if p, ok := mod.compiler.(rustPrebuilt); ok {
		return p.prebuilt()
	}
	return nil
}

func (mod *Module) depsToPaths(ctx android.ModuleContext) PathDeps {
	var depPaths PathDeps

	directRlibDeps := []*Module{}
	directDylibDeps := []*Module{}
	directProcMacroDeps := []*Module{}
	directSharedLibDeps := []cc.SharedLibraryInfo{}
	directStaticLibDeps := [](cc.LinkableInterface){}
	directSrcProvidersDeps := []*Module{}
	directSrcDeps := [](android.SourceFileProducer){}

	// For the dependency from platform to apex, use the latest stubs
	mod.apexSdkVersion = android.FutureApiLevel
	apexInfo := ctx.Provider(android.ApexInfoProvider).(android.ApexInfo)
	if !apexInfo.IsForPlatform() {
		mod.apexSdkVersion = apexInfo.MinSdkVersion
	}

	if android.InList("hwaddress", ctx.Config().SanitizeDevice()) {
		// In hwasan build, we override apexSdkVersion to the FutureApiLevel(10000)
		// so that even Q(29/Android10) apexes could use the dynamic unwinder by linking the newer stubs(e.g libc(R+)).
		// (b/144430859)
		mod.apexSdkVersion = android.FutureApiLevel
	}

	ctx.VisitDirectDeps(func(dep android.Module) {
		depName := ctx.OtherModuleName(dep)
		depTag := ctx.OtherModuleDependencyTag(dep)

		if rustDep, ok := dep.(*Module); ok && !rustDep.CcLibraryInterface() {
			//Handle Rust Modules
			makeLibName := cc.MakeLibName(ctx, mod, rustDep, depName+rustDep.Properties.RustSubName)

			switch depTag {
			case dylibDepTag:
				dylib, ok := rustDep.compiler.(libraryInterface)
				if !ok || !dylib.dylib() {
					ctx.ModuleErrorf("mod %q not an dylib library", depName)
					return
				}
				directDylibDeps = append(directDylibDeps, rustDep)
				mod.Properties.AndroidMkDylibs = append(mod.Properties.AndroidMkDylibs, makeLibName)
			case rlibDepTag:

				rlib, ok := rustDep.compiler.(libraryInterface)
				if !ok || !rlib.rlib() {
					ctx.ModuleErrorf("mod %q not an rlib library", makeLibName)
					return
				}
				directRlibDeps = append(directRlibDeps, rustDep)
				mod.Properties.AndroidMkRlibs = append(mod.Properties.AndroidMkRlibs, makeLibName)
			case procMacroDepTag:
				directProcMacroDeps = append(directProcMacroDeps, rustDep)
				mod.Properties.AndroidMkProcMacroLibs = append(mod.Properties.AndroidMkProcMacroLibs, makeLibName)
			}

			if android.IsSourceDepTagWithOutputTag(depTag, "") {
				// Since these deps are added in path_properties.go via AddDependencies, we need to ensure the correct
				// OS/Arch variant is used.
				var helper string
				if ctx.Host() {
					helper = "missing 'host_supported'?"
				} else {
					helper = "device module defined?"
				}

				if dep.Target().Os != ctx.Os() {
					ctx.ModuleErrorf("OS mismatch on dependency %q (%s)", dep.Name(), helper)
					return
				} else if dep.Target().Arch.ArchType != ctx.Arch().ArchType {
					ctx.ModuleErrorf("Arch mismatch on dependency %q (%s)", dep.Name(), helper)
					return
				}
				directSrcProvidersDeps = append(directSrcProvidersDeps, rustDep)
			}

			//Append the dependencies exportedDirs, except for proc-macros which target a different arch/OS
			if depTag != procMacroDepTag {
				exportedInfo := ctx.OtherModuleProvider(dep, FlagExporterInfoProvider).(FlagExporterInfo)
				depPaths.linkDirs = append(depPaths.linkDirs, exportedInfo.LinkDirs...)
				depPaths.depFlags = append(depPaths.depFlags, exportedInfo.Flags...)
				depPaths.linkObjects = append(depPaths.linkObjects, exportedInfo.LinkObjects...)
			}

			if depTag == dylibDepTag || depTag == rlibDepTag || depTag == procMacroDepTag {
				linkFile := rustDep.UnstrippedOutputFile()
				linkDir := linkPathFromFilePath(linkFile)
				if lib, ok := mod.compiler.(exportedFlagsProducer); ok {
					lib.exportLinkDirs(linkDir)
				}
			}

		} else if ccDep, ok := dep.(cc.LinkableInterface); ok {
			//Handle C dependencies
			makeLibName := cc.MakeLibName(ctx, mod, ccDep, depName)
			if _, ok := ccDep.(*Module); !ok {
				if ccDep.Module().Target().Os != ctx.Os() {
					ctx.ModuleErrorf("OS mismatch between %q and %q", ctx.ModuleName(), depName)
					return
				}
				if ccDep.Module().Target().Arch.ArchType != ctx.Arch().ArchType {
					ctx.ModuleErrorf("Arch mismatch between %q and %q", ctx.ModuleName(), depName)
					return
				}
			}
			linkObject := ccDep.OutputFile()
			linkPath := linkPathFromFilePath(linkObject.Path())

			if !linkObject.Valid() {
				ctx.ModuleErrorf("Invalid output file when adding dep %q to %q", depName, ctx.ModuleName())
			}

			exportDep := false
			switch {
			case cc.IsStaticDepTag(depTag):
				if cc.IsWholeStaticLib(depTag) {
					// rustc will bundle static libraries when they're passed with "-lstatic=<lib>". This will fail
					// if the library is not prefixed by "lib".
					if mod.Binary() {
						// Binaries may sometimes need to link whole static libraries that don't start with 'lib'.
						// Since binaries don't need to 'rebundle' these like libraries and only use these for the
						// final linkage, pass the args directly to the linker to handle these cases.
						depPaths.depLinkFlags = append(depPaths.depLinkFlags, []string{"-Wl,--whole-archive", linkObject.Path().String(), "-Wl,--no-whole-archive"}...)
					} else if libName, ok := libNameFromFilePath(linkObject.Path()); ok {
						depPaths.depFlags = append(depPaths.depFlags, "-lstatic="+libName)
					} else {
						ctx.ModuleErrorf("'%q' cannot be listed as a whole_static_library in Rust modules unless the output is prefixed by 'lib'", depName, ctx.ModuleName())
					}
				}

				// Add this to linkObjects to pass the library directly to the linker as well. This propagates
				// to dependencies to avoid having to redeclare static libraries for dependents of the dylib variant.
				depPaths.linkObjects = append(depPaths.linkObjects, linkObject.String())
				depPaths.linkDirs = append(depPaths.linkDirs, linkPath)

				exportedInfo := ctx.OtherModuleProvider(dep, cc.FlagExporterInfoProvider).(cc.FlagExporterInfo)
				depPaths.depIncludePaths = append(depPaths.depIncludePaths, exportedInfo.IncludeDirs...)
				depPaths.depSystemIncludePaths = append(depPaths.depSystemIncludePaths, exportedInfo.SystemIncludeDirs...)
				depPaths.depClangFlags = append(depPaths.depClangFlags, exportedInfo.Flags...)
				depPaths.depGeneratedHeaders = append(depPaths.depGeneratedHeaders, exportedInfo.GeneratedHeaders...)
				directStaticLibDeps = append(directStaticLibDeps, ccDep)

				// Record baseLibName for snapshots.
				mod.Properties.SnapshotStaticLibs = append(mod.Properties.SnapshotStaticLibs, cc.BaseLibName(depName))

				mod.Properties.AndroidMkStaticLibs = append(mod.Properties.AndroidMkStaticLibs, makeLibName)
			case cc.IsSharedDepTag(depTag):
				// For the shared lib dependencies, we may link to the stub variant
				// of the dependency depending on the context (e.g. if this
				// dependency crosses the APEX boundaries).
				sharedLibraryInfo, exportedInfo := cc.ChooseStubOrImpl(ctx, dep)

				// Re-get linkObject as ChooseStubOrImpl actually tells us which
				// object (either from stub or non-stub) to use.
				linkObject = android.OptionalPathForPath(sharedLibraryInfo.SharedLibrary)
				linkPath = linkPathFromFilePath(linkObject.Path())

				depPaths.linkDirs = append(depPaths.linkDirs, linkPath)
				depPaths.linkObjects = append(depPaths.linkObjects, linkObject.String())
				depPaths.depIncludePaths = append(depPaths.depIncludePaths, exportedInfo.IncludeDirs...)
				depPaths.depSystemIncludePaths = append(depPaths.depSystemIncludePaths, exportedInfo.SystemIncludeDirs...)
				depPaths.depClangFlags = append(depPaths.depClangFlags, exportedInfo.Flags...)
				depPaths.depGeneratedHeaders = append(depPaths.depGeneratedHeaders, exportedInfo.GeneratedHeaders...)
				directSharedLibDeps = append(directSharedLibDeps, sharedLibraryInfo)

				// Record baseLibName for snapshots.
				mod.Properties.SnapshotSharedLibs = append(mod.Properties.SnapshotSharedLibs, cc.BaseLibName(depName))

				mod.Properties.AndroidMkSharedLibs = append(mod.Properties.AndroidMkSharedLibs, makeLibName)
				exportDep = true
			case cc.IsHeaderDepTag(depTag):
				exportedInfo := ctx.OtherModuleProvider(dep, cc.FlagExporterInfoProvider).(cc.FlagExporterInfo)
				depPaths.depIncludePaths = append(depPaths.depIncludePaths, exportedInfo.IncludeDirs...)
				depPaths.depSystemIncludePaths = append(depPaths.depSystemIncludePaths, exportedInfo.SystemIncludeDirs...)
				depPaths.depGeneratedHeaders = append(depPaths.depGeneratedHeaders, exportedInfo.GeneratedHeaders...)
			case depTag == cc.CrtBeginDepTag:
				depPaths.CrtBegin = append(depPaths.CrtBegin, linkObject.Path())
			case depTag == cc.CrtEndDepTag:
				depPaths.CrtEnd = append(depPaths.CrtEnd, linkObject.Path())
			}

			// Make sure these dependencies are propagated
			if lib, ok := mod.compiler.(exportedFlagsProducer); ok && exportDep {
				lib.exportLinkDirs(linkPath)
				lib.exportLinkObjects(linkObject.String())
			}
		} else {
			switch {
			case depTag == cc.CrtBeginDepTag:
				depPaths.CrtBegin = append(depPaths.CrtBegin, android.OutputFileForModule(ctx, dep, ""))
			case depTag == cc.CrtEndDepTag:
				depPaths.CrtEnd = append(depPaths.CrtEnd, android.OutputFileForModule(ctx, dep, ""))
			}
		}

		if srcDep, ok := dep.(android.SourceFileProducer); ok {
			if android.IsSourceDepTagWithOutputTag(depTag, "") {
				// These are usually genrules which don't have per-target variants.
				directSrcDeps = append(directSrcDeps, srcDep)
			}
		}
	})

	var rlibDepFiles RustLibraries
	for _, dep := range directRlibDeps {
		rlibDepFiles = append(rlibDepFiles, RustLibrary{Path: dep.UnstrippedOutputFile(), CrateName: dep.CrateName()})
	}
	var dylibDepFiles RustLibraries
	for _, dep := range directDylibDeps {
		dylibDepFiles = append(dylibDepFiles, RustLibrary{Path: dep.UnstrippedOutputFile(), CrateName: dep.CrateName()})
	}
	var procMacroDepFiles RustLibraries
	for _, dep := range directProcMacroDeps {
		procMacroDepFiles = append(procMacroDepFiles, RustLibrary{Path: dep.UnstrippedOutputFile(), CrateName: dep.CrateName()})
	}

	var staticLibDepFiles android.Paths
	for _, dep := range directStaticLibDeps {
		staticLibDepFiles = append(staticLibDepFiles, dep.OutputFile().Path())
	}

	var sharedLibFiles android.Paths
	var sharedLibDepFiles android.Paths
	for _, dep := range directSharedLibDeps {
		sharedLibFiles = append(sharedLibFiles, dep.SharedLibrary)
		if dep.TableOfContents.Valid() {
			sharedLibDepFiles = append(sharedLibDepFiles, dep.TableOfContents.Path())
		} else {
			sharedLibDepFiles = append(sharedLibDepFiles, dep.SharedLibrary)
		}
	}

	var srcProviderDepFiles android.Paths
	for _, dep := range directSrcProvidersDeps {
		srcs, _ := dep.OutputFiles("")
		srcProviderDepFiles = append(srcProviderDepFiles, srcs...)
	}
	for _, dep := range directSrcDeps {
		srcs := dep.Srcs()
		srcProviderDepFiles = append(srcProviderDepFiles, srcs...)
	}

	depPaths.RLibs = append(depPaths.RLibs, rlibDepFiles...)
	depPaths.DyLibs = append(depPaths.DyLibs, dylibDepFiles...)
	depPaths.SharedLibs = append(depPaths.SharedLibs, sharedLibDepFiles...)
	depPaths.SharedLibDeps = append(depPaths.SharedLibDeps, sharedLibDepFiles...)
	depPaths.StaticLibs = append(depPaths.StaticLibs, staticLibDepFiles...)
	depPaths.ProcMacros = append(depPaths.ProcMacros, procMacroDepFiles...)
	depPaths.SrcDeps = append(depPaths.SrcDeps, srcProviderDepFiles...)

	// Dedup exported flags from dependencies
	depPaths.linkDirs = android.FirstUniqueStrings(depPaths.linkDirs)
	depPaths.linkObjects = android.FirstUniqueStrings(depPaths.linkObjects)
	depPaths.depFlags = android.FirstUniqueStrings(depPaths.depFlags)
	depPaths.depClangFlags = android.FirstUniqueStrings(depPaths.depClangFlags)
	depPaths.depIncludePaths = android.FirstUniquePaths(depPaths.depIncludePaths)
	depPaths.depSystemIncludePaths = android.FirstUniquePaths(depPaths.depSystemIncludePaths)

	return depPaths
}

func (mod *Module) InstallInData() bool {
	if mod.compiler == nil {
		return false
	}
	return mod.compiler.inData()
}

func (mod *Module) InstallInRamdisk() bool {
	return mod.InRamdisk()
}

func (mod *Module) InstallInVendorRamdisk() bool {
	return mod.InVendorRamdisk()
}

func (mod *Module) InstallInRecovery() bool {
	return mod.InRecovery()
}

func linkPathFromFilePath(filepath android.Path) string {
	return strings.Split(filepath.String(), filepath.Base())[0]
}

func (mod *Module) DepsMutator(actx android.BottomUpMutatorContext) {
	ctx := &depsContext{
		BottomUpMutatorContext: actx,
	}

	deps := mod.deps(ctx)
	var commonDepVariations []blueprint.Variation
	var snapshotInfo *cc.SnapshotInfo

	if ctx.Os() == android.Android {
		deps.SharedLibs, _ = cc.RewriteLibs(mod, &snapshotInfo, actx, ctx.Config(), deps.SharedLibs)
	}

	stdLinkage := "dylib-std"
	if mod.compiler.stdLinkage(ctx) == RlibLinkage {
		stdLinkage = "rlib-std"
	}

	rlibDepVariations := commonDepVariations

	if lib, ok := mod.compiler.(libraryInterface); !ok || !lib.sysroot() {
		rlibDepVariations = append(rlibDepVariations,
			blueprint.Variation{Mutator: "rust_stdlinkage", Variation: stdLinkage})
	}

	// rlibs
	rlibDepVariations = append(rlibDepVariations, blueprint.Variation{Mutator: "rust_libraries", Variation: rlibVariation})
	for _, lib := range deps.Rlibs {
		depTag := rlibDepTag
		lib = cc.RewriteSnapshotLib(lib, cc.GetSnapshot(mod, &snapshotInfo, actx).Rlibs)

		actx.AddVariationDependencies(rlibDepVariations, depTag, lib)
	}

	// dylibs
	actx.AddVariationDependencies(
		append(commonDepVariations, []blueprint.Variation{
			{Mutator: "rust_libraries", Variation: dylibVariation}}...),
		dylibDepTag, deps.Dylibs...)

	// rustlibs
	if deps.Rustlibs != nil && !mod.compiler.Disabled() {
		autoDep := mod.compiler.(autoDeppable).autoDep(ctx)
		for _, lib := range deps.Rustlibs {
			if autoDep.depTag == rlibDepTag {
				// Handle the rlib deptag case
				addRlibDependency(actx, lib, mod, snapshotInfo, rlibDepVariations)
			} else {
				// autoDep.depTag is a dylib depTag. Not all rustlibs may be available as a dylib however.
				// Check for the existence of the dylib deptag variant. Select it if available,
				// otherwise select the rlib variant.
				autoDepVariations := append(commonDepVariations,
					blueprint.Variation{Mutator: "rust_libraries", Variation: autoDep.variation})
				if actx.OtherModuleDependencyVariantExists(autoDepVariations, lib) {
					actx.AddVariationDependencies(autoDepVariations, autoDep.depTag, lib)
				} else {
					// If there's no dylib dependency available, try to add the rlib dependency instead.
					addRlibDependency(actx, lib, mod, snapshotInfo, rlibDepVariations)
				}
			}
		}
	}
	// stdlibs
	if deps.Stdlibs != nil {
		if mod.compiler.stdLinkage(ctx) == RlibLinkage {
			for _, lib := range deps.Stdlibs {
				depTag := rlibDepTag
				lib = cc.RewriteSnapshotLib(lib, cc.GetSnapshot(mod, &snapshotInfo, actx).Rlibs)

				actx.AddVariationDependencies(append(commonDepVariations, []blueprint.Variation{{Mutator: "rust_libraries", Variation: "rlib"}}...),
					depTag, lib)
			}
		} else {
			actx.AddVariationDependencies(
				append(commonDepVariations, blueprint.Variation{Mutator: "rust_libraries", Variation: "dylib"}),
				dylibDepTag, deps.Stdlibs...)
		}
	}

	for _, lib := range deps.SharedLibs {
		depTag := cc.SharedDepTag()
		name, version := cc.StubsLibNameAndVersion(lib)

		variations := []blueprint.Variation{
			{Mutator: "link", Variation: "shared"},
		}
		cc.AddSharedLibDependenciesWithVersions(ctx, mod, variations, depTag, name, version, false)
	}

	for _, lib := range deps.WholeStaticLibs {
		depTag := cc.StaticDepTag(true)
		lib = cc.RewriteSnapshotLib(lib, cc.GetSnapshot(mod, &snapshotInfo, actx).StaticLibs)

		actx.AddVariationDependencies([]blueprint.Variation{
			{Mutator: "link", Variation: "static"},
		}, depTag, lib)
	}

	for _, lib := range deps.StaticLibs {
		depTag := cc.StaticDepTag(false)
		lib = cc.RewriteSnapshotLib(lib, cc.GetSnapshot(mod, &snapshotInfo, actx).StaticLibs)

		actx.AddVariationDependencies([]blueprint.Variation{
			{Mutator: "link", Variation: "static"},
		}, depTag, lib)
	}

	actx.AddVariationDependencies(nil, cc.HeaderDepTag(), deps.HeaderLibs...)

	crtVariations := cc.GetCrtVariations(ctx, mod)
	for _, crt := range deps.CrtBegin {
		actx.AddVariationDependencies(crtVariations, cc.CrtBeginDepTag,
			cc.RewriteSnapshotLib(crt, cc.GetSnapshot(mod, &snapshotInfo, actx).Objects))
	}
	for _, crt := range deps.CrtEnd {
		actx.AddVariationDependencies(crtVariations, cc.CrtEndDepTag,
			cc.RewriteSnapshotLib(crt, cc.GetSnapshot(mod, &snapshotInfo, actx).Objects))
	}

	if mod.sourceProvider != nil {
		if bindgen, ok := mod.sourceProvider.(*bindgenDecorator); ok &&
			bindgen.Properties.Custom_bindgen != "" {
			actx.AddFarVariationDependencies(ctx.Config().BuildOSTarget.Variations(), customBindgenDepTag,
				bindgen.Properties.Custom_bindgen)
		}
	}

	actx.AddVariationDependencies([]blueprint.Variation{
		{Mutator: "link", Variation: "shared"},
	}, dataLibDepTag, deps.DataLibs...)

	actx.AddVariationDependencies(nil, dataBinDepTag, deps.DataBins...)

	// proc_macros are compiler plugins, and so we need the host arch variant as a dependendcy.
	actx.AddFarVariationDependencies(ctx.Config().BuildOSTarget.Variations(), procMacroDepTag, deps.ProcMacros...)
}

// addRlibDependency will add an rlib dependency, rewriting to the snapshot library if available.
func addRlibDependency(actx android.BottomUpMutatorContext, lib string, mod *Module, snapshotInfo *cc.SnapshotInfo, variations []blueprint.Variation) {
	lib = cc.RewriteSnapshotLib(lib, cc.GetSnapshot(mod, &snapshotInfo, actx).Rlibs)
	actx.AddVariationDependencies(variations, rlibDepTag, lib)
}

func BeginMutator(ctx android.BottomUpMutatorContext) {
	if mod, ok := ctx.Module().(*Module); ok && mod.Enabled() {
		mod.beginMutator(ctx)
	}
}

func (mod *Module) beginMutator(actx android.BottomUpMutatorContext) {
	ctx := &baseModuleContext{
		BaseModuleContext: actx,
	}

	mod.begin(ctx)
}

func (mod *Module) Name() string {
	name := mod.ModuleBase.Name()
	if p, ok := mod.compiler.(interface {
		Name(string) string
	}); ok {
		name = p.Name(name)
	}
	return name
}

func (mod *Module) disableClippy() {
	if mod.clippy != nil {
		mod.clippy.Properties.Clippy_lints = proptools.StringPtr("none")
	}
}

var _ android.HostToolProvider = (*Module)(nil)
var _ snapshot.RelativeInstallPath = (*Module)(nil)

func (mod *Module) HostToolPath() android.OptionalPath {
	if !mod.Host() {
		return android.OptionalPath{}
	}
	if binary, ok := mod.compiler.(*binaryDecorator); ok {
		return android.OptionalPathForPath(binary.baseCompiler.path)
	} else if pm, ok := mod.compiler.(*procMacroDecorator); ok {
		// Even though proc-macros aren't strictly "tools", since they target the compiler
		// and act as compiler plugins, we treat them similarly.
		return android.OptionalPathForPath(pm.baseCompiler.path)
	}
	return android.OptionalPath{}
}

var _ android.ApexModule = (*Module)(nil)

func (mod *Module) MinSdkVersion() string {
	return String(mod.Properties.Min_sdk_version)
}

// Implements android.ApexModule
func (mod *Module) ShouldSupportSdkVersion(ctx android.BaseModuleContext, sdkVersion android.ApiLevel) error {
	minSdkVersion := mod.MinSdkVersion()
	if minSdkVersion == "apex_inherit" {
		return nil
	}
	if minSdkVersion == "" {
		return fmt.Errorf("min_sdk_version is not specificed")
	}

	// Not using nativeApiLevelFromUser because the context here is not
	// necessarily a native context.
	ver, err := android.ApiLevelFromUser(ctx, minSdkVersion)
	if err != nil {
		return err
	}

	if ver.GreaterThan(sdkVersion) {
		return fmt.Errorf("newer SDK(%v)", ver)
	}
	return nil
}

// Implements android.ApexModule
func (mod *Module) DepIsInSameApex(ctx android.BaseModuleContext, dep android.Module) bool {
	depTag := ctx.OtherModuleDependencyTag(dep)

	if ccm, ok := dep.(*cc.Module); ok {
		if ccm.HasStubsVariants() {
			if cc.IsSharedDepTag(depTag) {
				// dynamic dep to a stubs lib crosses APEX boundary
				return false
			}
			if cc.IsRuntimeDepTag(depTag) {
				// runtime dep to a stubs lib also crosses APEX boundary
				return false
			}

			if cc.IsHeaderDepTag(depTag) {
				return false
			}
		}
		if mod.Static() && cc.IsSharedDepTag(depTag) {
			// shared_lib dependency from a static lib is considered as crossing
			// the APEX boundary because the dependency doesn't actually is
			// linked; the dependency is used only during the compilation phase.
			return false
		}
	}

	if depTag == procMacroDepTag {
		return false
	}

	return true
}

// Overrides ApexModule.IsInstallabeToApex()
func (mod *Module) IsInstallableToApex() bool {
	if mod.compiler != nil {
		if lib, ok := mod.compiler.(libraryInterface); ok && (lib.shared() || lib.dylib()) {
			return true
		}
		if _, ok := mod.compiler.(*binaryDecorator); ok {
			return true
		}
	}
	return false
}

// If a library file has a "lib" prefix, extract the library name without the prefix.
func libNameFromFilePath(filepath android.Path) (string, bool) {
	libName := strings.TrimSuffix(filepath.Base(), filepath.Ext())
	if strings.HasPrefix(libName, "lib") {
		libName = libName[3:]
		return libName, true
	}
	return "", false
}

var Bool = proptools.Bool
var BoolDefault = proptools.BoolDefault
var String = proptools.String
var StringPtr = proptools.StringPtr

var _ android.OutputFileProducer = (*Module)(nil)
