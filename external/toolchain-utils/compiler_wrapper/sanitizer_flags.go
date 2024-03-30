// Copyright 2019 The Chromium OS Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package main

import (
	"strings"
)

func processSanitizerFlags(builder *commandBuilder) {
	hasSanitizeFlags := false
	for _, arg := range builder.args {
		// TODO: This should probably be -fsanitize= to not match on
		// e.g. -fsanitize-blocklist
		if arg.fromUser {
			if strings.HasPrefix(arg.value, "-fsanitize") {
				hasSanitizeFlags = true
			}
		}
	}
	if hasSanitizeFlags {
		// Flags not supported by sanitizers (ASan etc.)
		unsupportedSanitizerFlags := map[string]bool{
			"-D_FORTIFY_SOURCE=1": true,
			"-D_FORTIFY_SOURCE=2": true,
			"-Wl,--no-undefined":  true,
			"-Wl,-z,defs":         true,
		}

		builder.transformArgs(func(arg builderArg) string {
			// TODO: This is a bug in the old wrapper to not filter
			// non user args for gcc. Fix this once we don't compare to the old wrapper anymore.
			if (builder.target.compilerType != gccType || arg.fromUser) &&
				unsupportedSanitizerFlags[arg.value] {
				return ""
			}
			return arg.value
		})
	}
}
