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

package bpf

import (
	"fmt"
	"io"
	"strings"

	"android/soong/android"

	"github.com/google/blueprint"
	"github.com/google/blueprint/proptools"
)

func init() {
	registerBpfBuildComponents(android.InitRegistrationContext)
	pctx.Import("android/soong/cc/config")
}

var (
	pctx = android.NewPackageContext("android/soong/bpf")

	ccRule = pctx.AndroidRemoteStaticRule("ccRule", android.RemoteRuleSupports{Goma: true},
		blueprint.RuleParams{
			Depfile:     "${out}.d",
			Deps:        blueprint.DepsGCC,
			Command:     "$ccCmd --target=bpf -c $cFlags -MD -MF ${out}.d -o $out $in",
			CommandDeps: []string{"$ccCmd"},
		},
		"ccCmd", "cFlags")

	stripRule = pctx.AndroidStaticRule("stripRule",
		blueprint.RuleParams{
			Command: `$stripCmd --strip-unneeded --remove-section=.rel.BTF ` +
				`--remove-section=.rel.BTF.ext --remove-section=.BTF.ext $in -o $out`,
			CommandDeps: []string{"$stripCmd"},
		},
		"stripCmd")
)

func registerBpfBuildComponents(ctx android.RegistrationContext) {
	ctx.RegisterModuleType("bpf", BpfFactory)
}

var PrepareForTestWithBpf = android.FixtureRegisterWithContext(registerBpfBuildComponents)

// BpfModule interface is used by the apex package to gather information from a bpf module.
type BpfModule interface {
	android.Module

	OutputFiles(tag string) (android.Paths, error)

	// Returns the sub install directory if the bpf module is included by apex.
	SubDir() string
}

type BpfProperties struct {
	Srcs         []string `android:"path"`
	Cflags       []string
	Include_dirs []string
	Sub_dir      string
	// If set to true, generate BTF debug info for maps & programs
	Btf    *bool
	Vendor *bool

	VendorInternal bool `blueprint:"mutated"`
}

type bpf struct {
	android.ModuleBase

	properties BpfProperties

	objs android.Paths
}

var _ android.ImageInterface = (*bpf)(nil)

func (bpf *bpf) ImageMutatorBegin(ctx android.BaseModuleContext) {}

func (bpf *bpf) CoreVariantNeeded(ctx android.BaseModuleContext) bool {
	return !proptools.Bool(bpf.properties.Vendor)
}

func (bpf *bpf) RamdiskVariantNeeded(ctx android.BaseModuleContext) bool {
	return false
}

func (bpf *bpf) VendorRamdiskVariantNeeded(ctx android.BaseModuleContext) bool {
	return false
}

func (bpf *bpf) DebugRamdiskVariantNeeded(ctx android.BaseModuleContext) bool {
	return false
}

func (bpf *bpf) RecoveryVariantNeeded(ctx android.BaseModuleContext) bool {
	return false
}

func (bpf *bpf) ExtraImageVariations(ctx android.BaseModuleContext) []string {
	if proptools.Bool(bpf.properties.Vendor) {
		return []string{"vendor"}
	}
	return nil
}

func (bpf *bpf) SetImageVariation(ctx android.BaseModuleContext, variation string, module android.Module) {
	bpf.properties.VendorInternal = variation == "vendor"
}

func (bpf *bpf) GenerateAndroidBuildActions(ctx android.ModuleContext) {
	cflags := []string{
		"-nostdlibinc",

		// Make paths in deps files relative
		"-no-canonical-prefixes",

		"-O2",
		"-isystem bionic/libc/include",
		"-isystem bionic/libc/kernel/uapi",
		// The architecture doesn't matter here, but asm/types.h is included by linux/types.h.
		"-isystem bionic/libc/kernel/uapi/asm-arm64",
		"-isystem bionic/libc/kernel/android/uapi",
		"-I       frameworks/libs/net/common/native/bpf_headers/include/bpf",
		// TODO(b/149785767): only give access to specific file with AID_* constants
		"-I       system/core/libcutils/include",
		"-I " + ctx.ModuleDir(),
	}

	for _, dir := range android.PathsForSource(ctx, bpf.properties.Include_dirs) {
		cflags = append(cflags, "-I "+dir.String())
	}

	cflags = append(cflags, bpf.properties.Cflags...)

	if proptools.Bool(bpf.properties.Btf) {
		cflags = append(cflags, "-g")
	}

	srcs := android.PathsForModuleSrc(ctx, bpf.properties.Srcs)

	for _, src := range srcs {
		obj := android.ObjPathWithExt(ctx, "unstripped", src, "o")

		ctx.Build(pctx, android.BuildParams{
			Rule:   ccRule,
			Input:  src,
			Output: obj,
			Args: map[string]string{
				"cFlags": strings.Join(cflags, " "),
				"ccCmd":  "${config.ClangBin}/clang",
			},
		})

		if proptools.Bool(bpf.properties.Btf) {
			objStripped := android.ObjPathWithExt(ctx, "", src, "o")
			ctx.Build(pctx, android.BuildParams{
				Rule:   stripRule,
				Input:  obj,
				Output: objStripped,
				Args: map[string]string{
					"stripCmd": "${config.ClangBin}/llvm-strip",
				},
			})
			bpf.objs = append(bpf.objs, objStripped.WithoutRel())
		} else {
			bpf.objs = append(bpf.objs, obj.WithoutRel())
		}

	}
}

func (bpf *bpf) AndroidMk() android.AndroidMkData {
	return android.AndroidMkData{
		Custom: func(w io.Writer, name, prefix, moduleDir string, data android.AndroidMkData) {
			var names []string
			fmt.Fprintln(w)
			fmt.Fprintln(w, "LOCAL_PATH :=", moduleDir)
			fmt.Fprintln(w)
			var localModulePath string
			if bpf.properties.VendorInternal {
				localModulePath = "LOCAL_MODULE_PATH := $(TARGET_OUT_VENDOR_ETC)/bpf"
			} else {
				localModulePath = "LOCAL_MODULE_PATH := $(TARGET_OUT_ETC)/bpf"
			}
			if len(bpf.properties.Sub_dir) > 0 {
				localModulePath += "/" + bpf.properties.Sub_dir
			}
			for _, obj := range bpf.objs {
				objName := name + "_" + obj.Base()
				names = append(names, objName)
				fmt.Fprintln(w, "include $(CLEAR_VARS)")
				fmt.Fprintln(w, "LOCAL_MODULE := ", objName)
				data.Entries.WriteLicenseVariables(w)
				fmt.Fprintln(w, "LOCAL_PREBUILT_MODULE_FILE :=", obj.String())
				fmt.Fprintln(w, "LOCAL_MODULE_STEM :=", obj.Base())
				fmt.Fprintln(w, "LOCAL_MODULE_CLASS := ETC")
				fmt.Fprintln(w, localModulePath)
				fmt.Fprintln(w, "include $(BUILD_PREBUILT)")
				fmt.Fprintln(w)
			}
			fmt.Fprintln(w, "include $(CLEAR_VARS)")
			fmt.Fprintln(w, "LOCAL_MODULE := ", name)
			data.Entries.WriteLicenseVariables(w)
			fmt.Fprintln(w, "LOCAL_REQUIRED_MODULES :=", strings.Join(names, " "))
			fmt.Fprintln(w, "include $(BUILD_PHONY_PACKAGE)")
		},
	}
}

// Implements OutputFileFileProducer interface so that the obj output can be used in the data property
// of other modules.
func (bpf *bpf) OutputFiles(tag string) (android.Paths, error) {
	switch tag {
	case "":
		return bpf.objs, nil
	default:
		return nil, fmt.Errorf("unsupported module reference tag %q", tag)
	}
}

func (bpf *bpf) SubDir() string {
	return bpf.properties.Sub_dir
}

var _ android.OutputFileProducer = (*bpf)(nil)

func BpfFactory() android.Module {
	module := &bpf{}

	module.AddProperties(&module.properties)

	android.InitAndroidArchModule(module, android.DeviceSupported, android.MultilibCommon)
	return module
}
