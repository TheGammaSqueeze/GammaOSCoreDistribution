// Copyright 2014 Google Inc. All rights reserved.
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

package bootstrap

import (
	"bufio"
	"fmt"
	"io"
	"io/ioutil"
	"os"
	"path/filepath"
	"runtime"
	"runtime/debug"
	"runtime/pprof"
	"runtime/trace"

	"github.com/google/blueprint"
)

type Args struct {
	ModuleListFile string
	OutFile        string

	EmptyNinjaFile bool

	NoGC       bool
	Cpuprofile string
	Memprofile string
	TraceFile  string
}

// Returns the list of dependencies the emitted Ninja files has. These can be
// written to the .d file for the output so that it is correctly rebuilt when
// needed in case Blueprint is itself invoked from Ninja
func RunBlueprint(args Args, stopBefore StopBefore, ctx *blueprint.Context, config interface{}) []string {
	runtime.GOMAXPROCS(runtime.NumCPU())

	if args.NoGC {
		debug.SetGCPercent(-1)
	}

	if args.Cpuprofile != "" {
		f, err := os.Create(joinPath(ctx.SrcDir(), args.Cpuprofile))
		if err != nil {
			fatalf("error opening cpuprofile: %s", err)
		}
		pprof.StartCPUProfile(f)
		defer f.Close()
		defer pprof.StopCPUProfile()
	}

	if args.TraceFile != "" {
		f, err := os.Create(joinPath(ctx.SrcDir(), args.TraceFile))
		if err != nil {
			fatalf("error opening trace: %s", err)
		}
		trace.Start(f)
		defer f.Close()
		defer trace.Stop()
	}

	srcDir := "."

	ninjaDeps := make([]string, 0)

	if args.ModuleListFile != "" {
		ctx.SetModuleListFile(args.ModuleListFile)
		ninjaDeps = append(ninjaDeps, args.ModuleListFile)
	} else {
		fatalf("-l <moduleListFile> is required and must be nonempty")
	}
	ctx.BeginEvent("list_modules")
	filesToParse, err := ctx.ListModulePaths(srcDir)
	ctx.EndEvent("list_modules")
	if err != nil {
		fatalf("could not enumerate files: %v\n", err.Error())
	}

	ctx.RegisterBottomUpMutator("bootstrap_plugin_deps", pluginDeps)
	ctx.RegisterModuleType("bootstrap_go_package", newGoPackageModuleFactory())
	ctx.RegisterModuleType("blueprint_go_binary", newGoBinaryModuleFactory())
	ctx.RegisterSingletonType("bootstrap", newSingletonFactory())
	blueprint.RegisterPackageIncludesModuleType(ctx)

	ctx.BeginEvent("parse_bp")
	blueprintFiles, errs := ctx.ParseFileList(".", filesToParse, config)
	if len(errs) > 0 {
		fatalErrors(errs)
	}
	ctx.EndEvent("parse_bp")

	// Add extra ninja file dependencies
	ninjaDeps = append(ninjaDeps, blueprintFiles...)

	extraDeps, errs := ctx.ResolveDependencies(config)
	if len(errs) > 0 {
		fatalErrors(errs)
	}
	ninjaDeps = append(ninjaDeps, extraDeps...)

	if stopBefore == StopBeforePrepareBuildActions {
		return ninjaDeps
	}

	extraDeps, errs = ctx.PrepareBuildActions(config)
	if len(errs) > 0 {
		fatalErrors(errs)
	}
	ninjaDeps = append(ninjaDeps, extraDeps...)

	if stopBefore == StopBeforeWriteNinja {
		return ninjaDeps
	}

	const outFilePermissions = 0666
	var out io.StringWriter
	var f *os.File
	var buf *bufio.Writer

	ctx.BeginEvent("write_files")
	defer ctx.EndEvent("write_files")
	if args.EmptyNinjaFile {
		if err := ioutil.WriteFile(joinPath(ctx.SrcDir(), args.OutFile), []byte(nil), outFilePermissions); err != nil {
			fatalf("error writing empty Ninja file: %s", err)
		}
	}

	if !args.EmptyNinjaFile {
		f, err = os.OpenFile(joinPath(ctx.SrcDir(), args.OutFile), os.O_WRONLY|os.O_CREATE|os.O_TRUNC, outFilePermissions)
		if err != nil {
			fatalf("error opening Ninja file: %s", err)
		}
		buf = bufio.NewWriterSize(f, 16*1024*1024)
		out = buf
	} else {
		out = ioutil.Discard.(io.StringWriter)
	}

	err = ctx.WriteBuildFile(out)
	if err != nil {
		fatalf("error writing Ninja file contents: %s", err)
	}

	if buf != nil {
		err = buf.Flush()
		if err != nil {
			fatalf("error flushing Ninja file contents: %s", err)
		}
	}

	if f != nil {
		err = f.Close()
		if err != nil {
			fatalf("error closing Ninja file: %s", err)
		}
	}

	if args.Memprofile != "" {
		f, err := os.Create(joinPath(ctx.SrcDir(), args.Memprofile))
		if err != nil {
			fatalf("error opening memprofile: %s", err)
		}
		defer f.Close()
		pprof.WriteHeapProfile(f)
	}

	return ninjaDeps
}

func fatalf(format string, args ...interface{}) {
	fmt.Printf(format, args...)
	fmt.Print("\n")
	os.Exit(1)
}

func fatalErrors(errs []error) {
	red := "\x1b[31m"
	unred := "\x1b[0m"

	for _, err := range errs {
		switch err := err.(type) {
		case *blueprint.BlueprintError,
			*blueprint.ModuleError,
			*blueprint.PropertyError:
			fmt.Printf("%serror:%s %s\n", red, unred, err.Error())
		default:
			fmt.Printf("%sinternal error:%s %s\n", red, unred, err)
		}
	}
	os.Exit(1)
}

func joinPath(base, path string) string {
	if filepath.IsAbs(path) {
		return path
	}
	return filepath.Join(base, path)
}
