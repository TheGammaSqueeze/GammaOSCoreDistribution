// Copyright 2016 Google Inc. All rights reserved.
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

package cc

import (
	"path/filepath"
	"regexp"
	"strings"

	"github.com/google/blueprint/proptools"

	"android/soong/android"
	"android/soong/cc/config"
)

type TidyProperties struct {
	// whether to run clang-tidy over C-like sources.
	Tidy *bool

	// Extra flags to pass to clang-tidy
	Tidy_flags []string

	// Extra checks to enable or disable in clang-tidy
	Tidy_checks []string

	// Checks that should be treated as errors.
	Tidy_checks_as_errors []string
}

type tidyFeature struct {
	Properties TidyProperties
}

var quotedFlagRegexp, _ = regexp.Compile(`^-?-[^=]+=('|").*('|")$`)

// When passing flag -name=value, if user add quotes around 'value',
// the quotation marks will be preserved by NinjaAndShellEscapeList
// and the 'value' string with quotes won't work like the intended value.
// So here we report an error if -*='*' is found.
func checkNinjaAndShellEscapeList(ctx ModuleContext, prop string, slice []string) []string {
	for _, s := range slice {
		if quotedFlagRegexp.MatchString(s) {
			ctx.PropertyErrorf(prop, "Extra quotes in: %s", s)
		}
	}
	return proptools.NinjaAndShellEscapeList(slice)
}

func (tidy *tidyFeature) props() []interface{} {
	return []interface{}{&tidy.Properties}
}

func (tidy *tidyFeature) flags(ctx ModuleContext, flags Flags) Flags {
	CheckBadTidyFlags(ctx, "tidy_flags", tidy.Properties.Tidy_flags)
	CheckBadTidyChecks(ctx, "tidy_checks", tidy.Properties.Tidy_checks)

	// Check if tidy is explicitly disabled for this module
	if tidy.Properties.Tidy != nil && !*tidy.Properties.Tidy {
		return flags
	}

	// If not explicitly disabled, set flags.Tidy to generate .tidy rules.
	// Note that libraries and binaries will depend on .tidy files ONLY if
	// the global WITH_TIDY or module 'tidy' property is true.
	flags.Tidy = true

	// If explicitly enabled, by global default or local tidy property,
	// set flags.NeedTidyFiles to make this module depend on .tidy files.
	if ctx.Config().ClangTidy() || Bool(tidy.Properties.Tidy) {
		flags.NeedTidyFiles = true
	}

	// Add global WITH_TIDY_FLAGS and local tidy_flags.
	withTidyFlags := ctx.Config().Getenv("WITH_TIDY_FLAGS")
	if len(withTidyFlags) > 0 {
		flags.TidyFlags = append(flags.TidyFlags, withTidyFlags)
	}
	esc := checkNinjaAndShellEscapeList
	flags.TidyFlags = append(flags.TidyFlags, esc(ctx, "tidy_flags", tidy.Properties.Tidy_flags)...)
	// If TidyFlags does not contain -header-filter, add default header filter.
	// Find the substring because the flag could also appear as --header-filter=...
	// and with or without single or double quotes.
	if !android.SubstringInList(flags.TidyFlags, "-header-filter=") {
		defaultDirs := ctx.Config().Getenv("DEFAULT_TIDY_HEADER_DIRS")
		headerFilter := "-header-filter="
		if defaultDirs == "" {
			headerFilter += ctx.ModuleDir() + "/"
		} else {
			headerFilter += "\"(" + ctx.ModuleDir() + "/|" + defaultDirs + ")\""
		}
		flags.TidyFlags = append(flags.TidyFlags, headerFilter)
	}
	// Work around RBE bug in parsing clang-tidy flags, replace "--flag" with "-flag".
	// Some C/C++ modules added local tidy flags like --header-filter= and --extra-arg-before=.
	doubleDash := regexp.MustCompile("^('?)--(.*)$")
	for i, s := range flags.TidyFlags {
		flags.TidyFlags[i] = doubleDash.ReplaceAllString(s, "$1-$2")
	}

	// If clang-tidy is not enabled globally, add the -quiet flag.
	if !ctx.Config().ClangTidy() {
		flags.TidyFlags = append(flags.TidyFlags, "-quiet")
		flags.TidyFlags = append(flags.TidyFlags, "-extra-arg-before=-fno-caret-diagnostics")
	}

	extraArgFlags := []string{
		// We might be using the static analyzer through clang tidy.
		// https://bugs.llvm.org/show_bug.cgi?id=32914
		"-D__clang_analyzer__",

		// A recent change in clang-tidy (r328258) enabled destructor inlining, which
		// appears to cause a number of false positives. Until that's resolved, this turns
		// off the effects of r328258.
		// https://bugs.llvm.org/show_bug.cgi?id=37459
		"-Xclang", "-analyzer-config", "-Xclang", "c++-temp-dtor-inlining=false",
	}

	for _, f := range extraArgFlags {
		flags.TidyFlags = append(flags.TidyFlags, "-extra-arg-before="+f)
	}

	tidyChecks := "-checks="
	if checks := ctx.Config().TidyChecks(); len(checks) > 0 {
		tidyChecks += checks
	} else {
		tidyChecks += config.TidyChecksForDir(ctx.ModuleDir())
	}
	if len(tidy.Properties.Tidy_checks) > 0 {
		tidyChecks = tidyChecks + "," + strings.Join(esc(ctx, "tidy_checks",
			config.ClangRewriteTidyChecks(tidy.Properties.Tidy_checks)), ",")
	}
	if ctx.Windows() {
		// https://b.corp.google.com/issues/120614316
		// mingw32 has cert-dcl16-c warning in NO_ERROR,
		// which is used in many Android files.
		tidyChecks = tidyChecks + ",-cert-dcl16-c"
	}
	// https://b.corp.google.com/issues/153464409
	// many local projects enable cert-* checks, which
	// trigger bugprone-reserved-identifier.
	tidyChecks = tidyChecks + ",-bugprone-reserved-identifier*,-cert-dcl51-cpp,-cert-dcl37-c"
	// http://b/153757728
	tidyChecks = tidyChecks + ",-readability-qualified-auto"
	// http://b/155034563
	tidyChecks = tidyChecks + ",-bugprone-signed-char-misuse"
	// http://b/155034972
	tidyChecks = tidyChecks + ",-bugprone-branch-clone"
	// http://b/193716442
	tidyChecks = tidyChecks + ",-bugprone-implicit-widening-of-multiplication-result"
	// Too many existing functions trigger this rule, and fixing it requires large code
	// refactoring. The cost of maintaining this tidy rule outweighs the benefit it brings.
	tidyChecks = tidyChecks + ",-bugprone-easily-swappable-parameters"
	// http://b/216364337 - TODO: Follow-up after compiler update to
	// disable or fix individual instances.
	tidyChecks = tidyChecks + ",-cert-err33-c"
	flags.TidyFlags = append(flags.TidyFlags, tidyChecks)

	if ctx.Config().IsEnvTrue("WITH_TIDY") {
		// WITH_TIDY=1 enables clang-tidy globally. There could be many unexpected
		// warnings from new checks and many local tidy_checks_as_errors and
		// -warnings-as-errors can break a global build.
		// So allow all clang-tidy warnings.
		inserted := false
		for i, s := range flags.TidyFlags {
			if strings.Contains(s, "-warnings-as-errors=") {
				// clang-tidy accepts only one -warnings-as-errors
				// replace the old one
				re := regexp.MustCompile(`'?-?-warnings-as-errors=[^ ]* *`)
				newFlag := re.ReplaceAllString(s, "")
				if newFlag == "" {
					flags.TidyFlags[i] = "-warnings-as-errors=-*"
				} else {
					flags.TidyFlags[i] = newFlag + " -warnings-as-errors=-*"
				}
				inserted = true
				break
			}
		}
		if !inserted {
			flags.TidyFlags = append(flags.TidyFlags, "-warnings-as-errors=-*")
		}
	} else if len(tidy.Properties.Tidy_checks_as_errors) > 0 {
		tidyChecksAsErrors := "-warnings-as-errors=" + strings.Join(esc(ctx, "tidy_checks_as_errors", tidy.Properties.Tidy_checks_as_errors), ",")
		flags.TidyFlags = append(flags.TidyFlags, tidyChecksAsErrors)
	}
	return flags
}

func init() {
	android.RegisterSingletonType("tidy_phony_targets", TidyPhonySingleton)
}

// This TidyPhonySingleton generates both tidy-* and obj-* phony targets for C/C++ files.
func TidyPhonySingleton() android.Singleton {
	return &tidyPhonySingleton{}
}

type tidyPhonySingleton struct{}

// Given a final module, add its tidy/obj phony targets to tidy/objModulesInDirGroup.
func collectTidyObjModuleTargets(ctx android.SingletonContext, module android.Module,
	tidyModulesInDirGroup, objModulesInDirGroup map[string]map[string]android.Paths) {
	allObjFileGroups := make(map[string]android.Paths)     // variant group name => obj file Paths
	allTidyFileGroups := make(map[string]android.Paths)    // variant group name => tidy file Paths
	subsetObjFileGroups := make(map[string]android.Paths)  // subset group name => obj file Paths
	subsetTidyFileGroups := make(map[string]android.Paths) // subset group name => tidy file Paths

	// (1) Collect all obj/tidy files into OS-specific groups.
	ctx.VisitAllModuleVariants(module, func(variant android.Module) {
		if ctx.Config().KatiEnabled() && android.ShouldSkipAndroidMkProcessing(variant) {
			return
		}
		if m, ok := variant.(*Module); ok {
			osName := variant.Target().Os.Name
			addToOSGroup(osName, m.objFiles, allObjFileGroups, subsetObjFileGroups)
			addToOSGroup(osName, m.tidyFiles, allTidyFileGroups, subsetTidyFileGroups)
		}
	})

	// (2) Add an all-OS group, with "" or "subset" name, to include all os-specific phony targets.
	addAllOSGroup(ctx, module, allObjFileGroups, "", "obj")
	addAllOSGroup(ctx, module, allTidyFileGroups, "", "tidy")
	addAllOSGroup(ctx, module, subsetObjFileGroups, "subset", "obj")
	addAllOSGroup(ctx, module, subsetTidyFileGroups, "subset", "tidy")

	tidyTargetGroups := make(map[string]android.Path)
	objTargetGroups := make(map[string]android.Path)
	genObjTidyPhonyTargets(ctx, module, "obj", allObjFileGroups, objTargetGroups)
	genObjTidyPhonyTargets(ctx, module, "obj", subsetObjFileGroups, objTargetGroups)
	genObjTidyPhonyTargets(ctx, module, "tidy", allTidyFileGroups, tidyTargetGroups)
	genObjTidyPhonyTargets(ctx, module, "tidy", subsetTidyFileGroups, tidyTargetGroups)

	moduleDir := ctx.ModuleDir(module)
	appendToModulesInDirGroup(tidyTargetGroups, moduleDir, tidyModulesInDirGroup)
	appendToModulesInDirGroup(objTargetGroups, moduleDir, objModulesInDirGroup)
}

func (m *tidyPhonySingleton) GenerateBuildActions(ctx android.SingletonContext) {
	// For tidy-* directory phony targets, there are different variant groups.
	// tidyModulesInDirGroup[G][D] is for group G, directory D, with Paths
	// of all phony targets to be included into direct dependents of tidy-D_G.
	tidyModulesInDirGroup := make(map[string]map[string]android.Paths)
	// Also for obj-* directory phony targets.
	objModulesInDirGroup := make(map[string]map[string]android.Paths)

	// Collect tidy/obj targets from the 'final' modules.
	ctx.VisitAllModules(func(module android.Module) {
		if module == ctx.FinalModule(module) {
			collectTidyObjModuleTargets(ctx, module, tidyModulesInDirGroup, objModulesInDirGroup)
		}
	})

	suffix := ""
	if ctx.Config().KatiEnabled() {
		suffix = "-soong"
	}
	generateObjTidyPhonyTargets(ctx, suffix, "obj", objModulesInDirGroup)
	generateObjTidyPhonyTargets(ctx, suffix, "tidy", tidyModulesInDirGroup)
}

// The name for an obj/tidy module variant group phony target is Name_group-obj/tidy,
func objTidyModuleGroupName(module android.Module, group string, suffix string) string {
	if group == "" {
		return module.Name() + "-" + suffix
	}
	return module.Name() + "_" + group + "-" + suffix
}

// Generate obj-* or tidy-* phony targets.
func generateObjTidyPhonyTargets(ctx android.SingletonContext, suffix string, prefix string, objTidyModulesInDirGroup map[string]map[string]android.Paths) {
	// For each variant group, create a <prefix>-<directory>_group target that
	// depends on all subdirectories and modules in the directory.
	for group, modulesInDir := range objTidyModulesInDirGroup {
		groupSuffix := ""
		if group != "" {
			groupSuffix = "_" + group
		}
		mmTarget := func(dir string) string {
			return prefix + "-" + strings.Replace(filepath.Clean(dir), "/", "-", -1) + groupSuffix
		}
		dirs, topDirs := android.AddAncestors(ctx, modulesInDir, mmTarget)
		// Create a <prefix>-soong_group target that depends on all <prefix>-dir_group of top level dirs.
		var topDirPaths android.Paths
		for _, dir := range topDirs {
			topDirPaths = append(topDirPaths, android.PathForPhony(ctx, mmTarget(dir)))
		}
		ctx.Phony(prefix+suffix+groupSuffix, topDirPaths...)
		// Create a <prefix>-dir_group target that depends on all targets in modulesInDir[dir]
		for _, dir := range dirs {
			if dir != "." && dir != "" {
				ctx.Phony(mmTarget(dir), modulesInDir[dir]...)
			}
		}
	}
}

// Append (obj|tidy)TargetGroups[group] into (obj|tidy)ModulesInDirGroups[group][moduleDir].
func appendToModulesInDirGroup(targetGroups map[string]android.Path, moduleDir string, modulesInDirGroup map[string]map[string]android.Paths) {
	for group, phonyPath := range targetGroups {
		if _, found := modulesInDirGroup[group]; !found {
			modulesInDirGroup[group] = make(map[string]android.Paths)
		}
		modulesInDirGroup[group][moduleDir] = append(modulesInDirGroup[group][moduleDir], phonyPath)
	}
}

// Add given files to the OS group and subset group.
func addToOSGroup(osName string, files android.Paths, allGroups, subsetGroups map[string]android.Paths) {
	if len(files) > 0 {
		subsetName := osName + "_subset"
		allGroups[osName] = append(allGroups[osName], files...)
		// Now include only the first variant in the subsetGroups.
		// If clang and clang-tidy get faster, we might include more variants.
		if _, found := subsetGroups[subsetName]; !found {
			subsetGroups[subsetName] = files
		}
	}
}

// Add an all-OS group, with groupName, to include all os-specific phony targets.
func addAllOSGroup(ctx android.SingletonContext, module android.Module, phonyTargetGroups map[string]android.Paths, groupName string, objTidyName string) {
	if len(phonyTargetGroups) > 0 {
		var targets android.Paths
		for group, _ := range phonyTargetGroups {
			targets = append(targets, android.PathForPhony(ctx, objTidyModuleGroupName(module, group, objTidyName)))
		}
		phonyTargetGroups[groupName] = targets
	}
}

// Create one phony targets for each group and add them to the targetGroups.
func genObjTidyPhonyTargets(ctx android.SingletonContext, module android.Module, objTidyName string, fileGroups map[string]android.Paths, targetGroups map[string]android.Path) {
	for group, files := range fileGroups {
		groupName := objTidyModuleGroupName(module, group, objTidyName)
		ctx.Phony(groupName, files...)
		targetGroups[group] = android.PathForPhony(ctx, groupName)
	}
}
