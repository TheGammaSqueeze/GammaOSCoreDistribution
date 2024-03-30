// Copyright 2018 Google Inc. All rights reserved.
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
	"path/filepath"
	"strings"

	"android/soong/android"
	"android/soong/dexpreopt"
)

type DexpreopterInterface interface {
	IsInstallable() bool // Structs that embed dexpreopter must implement this.
	dexpreoptDisabled(ctx android.BaseModuleContext) bool
	DexpreoptBuiltInstalledForApex() []dexpreopterInstall
	AndroidMkEntriesForApex() []android.AndroidMkEntries
}

type dexpreopterInstall struct {
	// A unique name to distinguish an output from others for the same java library module. Usually in
	// the form of `<arch>-<encoded-path>.odex/vdex/art`.
	name string

	// The name of the input java module.
	moduleName string

	// The path to the dexpreopt output on host.
	outputPathOnHost android.Path

	// The directory on the device for the output to install to.
	installDirOnDevice android.InstallPath

	// The basename (the last segment of the path) for the output to install as.
	installFileOnDevice string
}

// The full module name of the output in the makefile.
func (install *dexpreopterInstall) FullModuleName() string {
	return install.moduleName + install.SubModuleName()
}

// The sub-module name of the output in the makefile (the name excluding the java module name).
func (install *dexpreopterInstall) SubModuleName() string {
	return "-dexpreopt-" + install.name
}

// Returns Make entries for installing the file.
//
// This function uses a value receiver rather than a pointer receiver to ensure that the object is
// safe to use in `android.AndroidMkExtraEntriesFunc`.
func (install dexpreopterInstall) ToMakeEntries() android.AndroidMkEntries {
	return android.AndroidMkEntries{
		Class:      "ETC",
		SubName:    install.SubModuleName(),
		OutputFile: android.OptionalPathForPath(install.outputPathOnHost),
		ExtraEntries: []android.AndroidMkExtraEntriesFunc{
			func(ctx android.AndroidMkExtraEntriesContext, entries *android.AndroidMkEntries) {
				entries.SetString("LOCAL_MODULE_PATH", install.installDirOnDevice.String())
				entries.SetString("LOCAL_INSTALLED_MODULE_STEM", install.installFileOnDevice)
				entries.SetString("LOCAL_NOT_AVAILABLE_FOR_PLATFORM", "false")
			},
		},
	}
}

type dexpreopter struct {
	dexpreoptProperties DexpreoptProperties

	installPath         android.InstallPath
	uncompressedDex     bool
	isSDKLibrary        bool
	isApp               bool
	isTest              bool
	isPresignedPrebuilt bool
	preventInstall      bool

	manifestFile        android.Path
	statusFile          android.WritablePath
	enforceUsesLibs     bool
	classLoaderContexts dexpreopt.ClassLoaderContextMap

	// See the `dexpreopt` function for details.
	builtInstalled        string
	builtInstalledForApex []dexpreopterInstall

	// The config is used for two purposes:
	// - Passing dexpreopt information about libraries from Soong to Make. This is needed when
	//   a <uses-library> is defined in Android.bp, but used in Android.mk (see dex_preopt_config_merger.py).
	//   Note that dexpreopt.config might be needed even if dexpreopt is disabled for the library itself.
	// - Dexpreopt post-processing (using dexpreopt artifacts from a prebuilt system image to incrementally
	//   dexpreopt another partition).
	configPath android.WritablePath
}

type DexpreoptProperties struct {
	Dex_preopt struct {
		// If false, prevent dexpreopting.  Defaults to true.
		Enabled *bool

		// If true, generate an app image (.art file) for this module.
		App_image *bool

		// If true, use a checked-in profile to guide optimization.  Defaults to false unless
		// a matching profile is set or a profile is found in PRODUCT_DEX_PREOPT_PROFILE_DIR
		// that matches the name of this module, in which case it is defaulted to true.
		Profile_guided *bool

		// If set, provides the path to profile relative to the Android.bp file.  If not set,
		// defaults to searching for a file that matches the name of this module in the default
		// profile location set by PRODUCT_DEX_PREOPT_PROFILE_DIR, or empty if not found.
		Profile *string `android:"path"`
	}
}

func init() {
	dexpreopt.DexpreoptRunningInSoong = true
}

func isApexVariant(ctx android.BaseModuleContext) bool {
	apexInfo := ctx.Provider(android.ApexInfoProvider).(android.ApexInfo)
	return !apexInfo.IsForPlatform()
}

func forPrebuiltApex(ctx android.BaseModuleContext) bool {
	apexInfo := ctx.Provider(android.ApexInfoProvider).(android.ApexInfo)
	return apexInfo.ForPrebuiltApex
}

func moduleName(ctx android.BaseModuleContext) string {
	// Remove the "prebuilt_" prefix if the module is from a prebuilt because the prefix is not
	// expected by dexpreopter.
	return android.RemoveOptionalPrebuiltPrefix(ctx.ModuleName())
}

func (d *dexpreopter) dexpreoptDisabled(ctx android.BaseModuleContext) bool {
	if !ctx.Device() {
		return true
	}

	if d.isTest {
		return true
	}

	if !BoolDefault(d.dexpreoptProperties.Dex_preopt.Enabled, true) {
		return true
	}

	// If the module is from a prebuilt APEX, it shouldn't be installable, but it can still be
	// dexpreopted.
	if !ctx.Module().(DexpreopterInterface).IsInstallable() && !forPrebuiltApex(ctx) {
		return true
	}

	if !android.IsModulePreferred(ctx.Module()) {
		return true
	}

	global := dexpreopt.GetGlobalConfig(ctx)

	if global.DisablePreopt {
		return true
	}

	if inList(moduleName(ctx), global.DisablePreoptModules) {
		return true
	}

	isApexSystemServerJar := global.AllApexSystemServerJars(ctx).ContainsJar(moduleName(ctx))
	if isApexVariant(ctx) {
		// Don't preopt APEX variant module unless the module is an APEX system server jar and we are
		// building the entire system image.
		if !isApexSystemServerJar || ctx.Config().UnbundledBuild() {
			return true
		}
	} else {
		// Don't preopt the platform variant of an APEX system server jar to avoid conflicts.
		if isApexSystemServerJar {
			return true
		}
	}

	// TODO: contains no java code

	return false
}

func dexpreoptToolDepsMutator(ctx android.BottomUpMutatorContext) {
	if d, ok := ctx.Module().(DexpreopterInterface); !ok || d.dexpreoptDisabled(ctx) {
		return
	}
	dexpreopt.RegisterToolDeps(ctx)
}

func (d *dexpreopter) odexOnSystemOther(ctx android.ModuleContext, installPath android.InstallPath) bool {
	return dexpreopt.OdexOnSystemOtherByName(moduleName(ctx), android.InstallPathToOnDevicePath(ctx, installPath), dexpreopt.GetGlobalConfig(ctx))
}

// Returns the install path of the dex jar of a module.
//
// Do not rely on `ApexInfo.ApexVariationName` because it can be something like "apex1000", rather
// than the `name` in the path `/apex/<name>` as suggested in its comment.
//
// This function is on a best-effort basis. It cannot handle the case where an APEX jar is not a
// system server jar, which is fine because we currently only preopt system server jars for APEXes.
func (d *dexpreopter) getInstallPath(
	ctx android.ModuleContext, defaultInstallPath android.InstallPath) android.InstallPath {
	global := dexpreopt.GetGlobalConfig(ctx)
	if global.AllApexSystemServerJars(ctx).ContainsJar(moduleName(ctx)) {
		dexLocation := dexpreopt.GetSystemServerDexLocation(ctx, global, moduleName(ctx))
		return android.PathForModuleInPartitionInstall(ctx, "", strings.TrimPrefix(dexLocation, "/"))
	}
	if !d.dexpreoptDisabled(ctx) && isApexVariant(ctx) &&
		filepath.Base(defaultInstallPath.PartitionDir()) != "apex" {
		ctx.ModuleErrorf("unable to get the install path of the dex jar for dexpreopt")
	}
	return defaultInstallPath
}

func (d *dexpreopter) dexpreopt(ctx android.ModuleContext, dexJarFile android.WritablePath) {
	global := dexpreopt.GetGlobalConfig(ctx)

	// TODO(b/148690468): The check on d.installPath is to bail out in cases where
	// the dexpreopter struct hasn't been fully initialized before we're called,
	// e.g. in aar.go. This keeps the behaviour that dexpreopting is effectively
	// disabled, even if installable is true.
	if d.installPath.Base() == "." {
		return
	}

	dexLocation := android.InstallPathToOnDevicePath(ctx, d.installPath)

	providesUsesLib := moduleName(ctx)
	if ulib, ok := ctx.Module().(ProvidesUsesLib); ok {
		name := ulib.ProvidesUsesLib()
		if name != nil {
			providesUsesLib = *name
		}
	}

	// If it is test, make config files regardless of its dexpreopt setting.
	// The config files are required for apps defined in make which depend on the lib.
	if d.isTest && d.dexpreoptDisabled(ctx) {
		return
	}

	isSystemServerJar := global.AllSystemServerJars(ctx).ContainsJar(moduleName(ctx))

	bootImage := defaultBootImageConfig(ctx)
	if global.UseArtImage {
		bootImage = artBootImageConfig(ctx)
	}

	dexFiles, dexLocations := bcpForDexpreopt(ctx, global.PreoptWithUpdatableBcp)

	targets := ctx.MultiTargets()
	if len(targets) == 0 {
		// assume this is a java library, dexpreopt for all arches for now
		for _, target := range ctx.Config().Targets[android.Android] {
			if target.NativeBridge == android.NativeBridgeDisabled {
				targets = append(targets, target)
			}
		}
		if isSystemServerJar && !d.isSDKLibrary {
			// If the module is not an SDK library and it's a system server jar, only preopt the primary arch.
			targets = targets[:1]
		}
	}

	var archs []android.ArchType
	var images android.Paths
	var imagesDeps []android.OutputPaths
	for _, target := range targets {
		archs = append(archs, target.Arch.ArchType)
		variant := bootImage.getVariant(target)
		images = append(images, variant.imagePathOnHost)
		imagesDeps = append(imagesDeps, variant.imagesDeps)
	}
	// The image locations for all Android variants are identical.
	hostImageLocations, deviceImageLocations := bootImage.getAnyAndroidVariant().imageLocations()

	var profileClassListing android.OptionalPath
	var profileBootListing android.OptionalPath
	profileIsTextListing := false
	if BoolDefault(d.dexpreoptProperties.Dex_preopt.Profile_guided, true) {
		// If dex_preopt.profile_guided is not set, default it based on the existence of the
		// dexprepot.profile option or the profile class listing.
		if String(d.dexpreoptProperties.Dex_preopt.Profile) != "" {
			profileClassListing = android.OptionalPathForPath(
				android.PathForModuleSrc(ctx, String(d.dexpreoptProperties.Dex_preopt.Profile)))
			profileBootListing = android.ExistentPathForSource(ctx,
				ctx.ModuleDir(), String(d.dexpreoptProperties.Dex_preopt.Profile)+"-boot")
			profileIsTextListing = true
		} else if global.ProfileDir != "" {
			profileClassListing = android.ExistentPathForSource(ctx,
				global.ProfileDir, moduleName(ctx)+".prof")
		}
	}

	// Full dexpreopt config, used to create dexpreopt build rules.
	dexpreoptConfig := &dexpreopt.ModuleConfig{
		Name:            moduleName(ctx),
		DexLocation:     dexLocation,
		BuildPath:       android.PathForModuleOut(ctx, "dexpreopt", moduleName(ctx)+".jar").OutputPath,
		DexPath:         dexJarFile,
		ManifestPath:    android.OptionalPathForPath(d.manifestFile),
		UncompressedDex: d.uncompressedDex,
		HasApkLibraries: false,
		PreoptFlags:     nil,

		ProfileClassListing:  profileClassListing,
		ProfileIsTextListing: profileIsTextListing,
		ProfileBootListing:   profileBootListing,

		EnforceUsesLibrariesStatusFile: dexpreopt.UsesLibrariesStatusFile(ctx),
		EnforceUsesLibraries:           d.enforceUsesLibs,
		ProvidesUsesLibrary:            providesUsesLib,
		ClassLoaderContexts:            d.classLoaderContexts,

		Archs:                           archs,
		DexPreoptImagesDeps:             imagesDeps,
		DexPreoptImageLocationsOnHost:   hostImageLocations,
		DexPreoptImageLocationsOnDevice: deviceImageLocations,

		PreoptBootClassPathDexFiles:     dexFiles.Paths(),
		PreoptBootClassPathDexLocations: dexLocations,

		PreoptExtractedApk: false,

		NoCreateAppImage:    !BoolDefault(d.dexpreoptProperties.Dex_preopt.App_image, true),
		ForceCreateAppImage: BoolDefault(d.dexpreoptProperties.Dex_preopt.App_image, false),

		PresignedPrebuilt: d.isPresignedPrebuilt,
	}

	d.configPath = android.PathForModuleOut(ctx, "dexpreopt", "dexpreopt.config")
	dexpreopt.WriteModuleConfig(ctx, dexpreoptConfig, d.configPath)

	if d.dexpreoptDisabled(ctx) {
		return
	}

	globalSoong := dexpreopt.GetGlobalSoongConfig(ctx)

	dexpreoptRule, err := dexpreopt.GenerateDexpreoptRule(ctx, globalSoong, global, dexpreoptConfig)
	if err != nil {
		ctx.ModuleErrorf("error generating dexpreopt rule: %s", err.Error())
		return
	}

	dexpreoptRule.Build("dexpreopt", "dexpreopt")

	isApexSystemServerJar := global.AllApexSystemServerJars(ctx).ContainsJar(moduleName(ctx))

	for _, install := range dexpreoptRule.Installs() {
		// Remove the "/" prefix because the path should be relative to $ANDROID_PRODUCT_OUT.
		installDir := strings.TrimPrefix(filepath.Dir(install.To), "/")
		installBase := filepath.Base(install.To)
		arch := filepath.Base(installDir)
		installPath := android.PathForModuleInPartitionInstall(ctx, "", installDir)

		if isApexSystemServerJar {
			// APEX variants of java libraries are hidden from Make, so their dexpreopt
			// outputs need special handling. Currently, for APEX variants of java
			// libraries, only those in the system server classpath are handled here.
			// Preopting of boot classpath jars in the ART APEX are handled in
			// java/dexpreopt_bootjars.go, and other APEX jars are not preopted.
			// The installs will be handled by Make as sub-modules of the java library.
			d.builtInstalledForApex = append(d.builtInstalledForApex, dexpreopterInstall{
				name:                arch + "-" + installBase,
				moduleName:          moduleName(ctx),
				outputPathOnHost:    install.From,
				installDirOnDevice:  installPath,
				installFileOnDevice: installBase,
			})
		} else if !d.preventInstall {
			ctx.InstallFile(installPath, installBase, install.From)
		}
	}

	if !isApexSystemServerJar {
		d.builtInstalled = dexpreoptRule.Installs().String()
	}
}

func (d *dexpreopter) DexpreoptBuiltInstalledForApex() []dexpreopterInstall {
	return d.builtInstalledForApex
}

func (d *dexpreopter) AndroidMkEntriesForApex() []android.AndroidMkEntries {
	var entries []android.AndroidMkEntries
	for _, install := range d.builtInstalledForApex {
		entries = append(entries, install.ToMakeEntries())
	}
	return entries
}
