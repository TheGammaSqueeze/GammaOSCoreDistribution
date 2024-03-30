"""
Copyright (C) 2021 The Android Open Source Project

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
"""

# Constants for cc_* rules.
# To use, load the constants struct:
#
#   load("//build/bazel/rules:cc_constants.bzl", "constants")
# Supported hdr extensions in Soong. Keep this consistent with hdrExts in build/soong/cc/snapshot_utils.go
_HDR_EXTS = ["h", "hh", "hpp", "hxx", "h++", "inl", "inc", "ipp", "h.generic"]
_C_SRC_EXTS = ["c"]
_CPP_SRC_EXTS = ["cc", "cpp"]
_AS_SRC_EXTS = ["S"]
_SRC_EXTS = _C_SRC_EXTS + _CPP_SRC_EXTS + _AS_SRC_EXTS
_ALL_EXTS = _SRC_EXTS + _HDR_EXTS
_HDR_EXTS_WITH_DOT = ["." + ext for ext in _HDR_EXTS]
_SRC_EXTS_WITH_DOT = ["." + ext for ext in _SRC_EXTS]
_ALL_EXTS_WITH_DOT = ["." + ext for ext in _ALL_EXTS]

# These are root-relative.
_GLOBAL_INCLUDE_DIRS_COPTS_ONLY_USED_FOR_SOONG_COMPATIBILITY_DO_NOT_ADD_MORE = [
    "/",
]
constants = struct(
    hdr_exts = _HDR_EXTS,
    c_src_exts = _C_SRC_EXTS,
    cpp_src_exts = _CPP_SRC_EXTS,
    as_src_exts = _AS_SRC_EXTS,
    src_exts = _SRC_EXTS,
    all_exts = _ALL_EXTS,
    hdr_dot_exts = _HDR_EXTS_WITH_DOT,
    src_dot_exts = _SRC_EXTS_WITH_DOT,
    all_dot_exts = _ALL_EXTS_WITH_DOT,
)
