//
// Copyright (C) 2021 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//

package rustprebuilts

import (
	"path"
	"strings"

	"github.com/google/blueprint/proptools"

	"android/soong/android"
	"android/soong/rust"
	"android/soong/rust/config"
)

// This module is used to generate the rust host stdlib prebuilts
// When RUST_PREBUILTS_VERSION is set, the library will generated
// from the given Rust version.
func init() {
	android.RegisterModuleType("rust_stdlib_prebuilt_host",
		rustHostPrebuiltSysrootLibraryFactory)
}

func getRustPrebuiltVersion(ctx android.LoadHookContext) string {
	return ctx.AConfig().GetenvWithDefault("RUST_PREBUILTS_VERSION", config.RustDefaultVersion)
}

func getRustLibDir(ctx android.LoadHookContext) string {
	rustDir := getRustPrebuiltVersion(ctx)
	return path.Join(rustDir, "lib", "rustlib")
}

// getPrebuilt returns the module relative Rust library path and the suffix hash.
func getPrebuilt(ctx android.LoadHookContext, dir, lib, extension string) (string, string) {
	globPath := path.Join(ctx.ModuleDir(), dir, lib) + "-*" + extension
	libMatches := ctx.Glob(globPath, nil)

	if len(libMatches) != 1 {
		ctx.ModuleErrorf("Unexpected number of matches for prebuilt libraries at path %q, found %d matches", globPath, len(libMatches))
		return "", ""
	}

	// Collect the suffix by trimming the extension from the Base, then removing the library name and hyphen.
	suffix := strings.TrimSuffix(libMatches[0].Base(), extension)[len(lib)+1:]

	// Get the relative path from the match by trimming out the module directory.
	relPath := strings.TrimPrefix(libMatches[0].String(), ctx.ModuleDir()+"/")

	return relPath, suffix
}

func rustHostPrebuiltSysrootLibrary(ctx android.LoadHookContext) {
	rustDir := getRustLibDir(ctx)
	name := android.RemoveOptionalPrebuiltPrefix(ctx.ModuleName())

	type props struct {
		Target struct {
			Linux_glibc_x86_64 struct {
				Suffix *string
				Dylib  struct {
					Srcs []string
				}
				Rlib struct {
					Srcs []string
				}
				Link_dirs []string
				Enabled   *bool
			}
			Linux_glibc_x86 struct {
				Suffix *string
				Dylib  struct {
					Srcs []string
				}
				Rlib struct {
					Srcs []string
				}
				Link_dirs []string
				Enabled   *bool
			}
			Darwin_x86_64 struct {
				Suffix *string
				Dylib  struct {
					Srcs []string
				}
				Rlib struct {
					Srcs []string
				}
				Link_dirs []string
				Enabled   *bool
			}
		}
		Enabled *bool
	}

	p := &props{}
	p.Enabled = proptools.BoolPtr(false)

	if ctx.Config().BuildOS == android.Linux {
		// The suffixes are the same between the dylib and the rlib,
		// so we only need to collect this value once for each target.
		linux64Dir := path.Join("linux-x86", rustDir, "x86_64-unknown-linux-gnu", "lib")
		linux64Dylib, linux64Suffix := getPrebuilt(ctx, linux64Dir, name, ".so")
		linux64Rlib, _ := getPrebuilt(ctx, linux64Dir, name, ".rlib")

		linux32Dir := path.Join("linux-x86", rustDir, "i686-unknown-linux-gnu", "lib")
		linux32Rlib, _ := getPrebuilt(ctx, linux32Dir, name, ".rlib")
		linux32Dylib, linux32Suffix := getPrebuilt(ctx, linux32Dir, name, ".so")

		p.Target.Linux_glibc_x86_64.Suffix = proptools.StringPtr(linux64Suffix)
		p.Target.Linux_glibc_x86_64.Dylib.Srcs = []string{linux64Dylib}
		p.Target.Linux_glibc_x86_64.Rlib.Srcs = []string{linux64Rlib}
		p.Target.Linux_glibc_x86_64.Link_dirs = []string{linux64Dir}
		p.Target.Linux_glibc_x86_64.Enabled = proptools.BoolPtr(true)

		p.Target.Linux_glibc_x86.Suffix = proptools.StringPtr(linux32Suffix)
		p.Target.Linux_glibc_x86.Dylib.Srcs = []string{linux32Dylib}
		p.Target.Linux_glibc_x86.Rlib.Srcs = []string{linux32Rlib}
		p.Target.Linux_glibc_x86.Link_dirs = []string{linux32Dir}
		p.Target.Linux_glibc_x86.Enabled = proptools.BoolPtr(true)

	} else if ctx.Config().BuildOS == android.Darwin {
		darwinDir := path.Join("darwin-x86", rustDir, "x86_64-apple-darwin", "lib")
		darwinDylib, darwinSuffix := getPrebuilt(ctx, darwinDir, name, ".dylib")
		darwinRlib, _ := getPrebuilt(ctx, darwinDir, name, ".rlib")

		p.Target.Darwin_x86_64.Suffix = proptools.StringPtr(darwinSuffix)
		p.Target.Darwin_x86_64.Dylib.Srcs = []string{darwinDylib}
		p.Target.Darwin_x86_64.Rlib.Srcs = []string{darwinRlib}
		p.Target.Darwin_x86_64.Link_dirs = []string{darwinDir}
		p.Target.Darwin_x86_64.Enabled = proptools.BoolPtr(true)
	}

	ctx.AppendProperties(p)
}

func rustHostPrebuiltSysrootLibraryFactory() android.Module {
	module, _ := rust.NewPrebuiltLibrary(android.HostSupported)
	android.AddLoadHook(module, rustHostPrebuiltSysrootLibrary)
	return module.Init()
}
