#!/bin/bash -eu

set -o pipefail

# Test that bp2build and Bazel can play nicely together

source "$(dirname "$0")/lib.sh"

readonly GENERATED_BUILD_FILE_NAME="BUILD.bazel"

function test_bp2build_null_build() {
  setup
  run_soong bp2build
  local output_mtime1=$(stat -c "%y" out/soong/bp2build_workspace_marker)

  run_soong bp2build
  local output_mtime2=$(stat -c "%y" out/soong/bp2build_workspace_marker)

  if [[ "$output_mtime1" != "$output_mtime2" ]]; then
    fail "Output bp2build marker file changed on null build"
  fi
}

test_bp2build_null_build

function test_bp2build_null_build_with_globs() {
  setup

  mkdir -p foo/bar
  cat > foo/bar/Android.bp <<'EOF'
filegroup {
    name: "globs",
    srcs: ["*.txt"],
  }
EOF
  touch foo/bar/a.txt foo/bar/b.txt

  run_soong bp2build
  local output_mtime1=$(stat -c "%y" out/soong/bp2build_workspace_marker)

  run_soong bp2build
  local output_mtime2=$(stat -c "%y" out/soong/bp2build_workspace_marker)

  if [[ "$output_mtime1" != "$output_mtime2" ]]; then
    fail "Output bp2build marker file changed on null build"
  fi
}

test_bp2build_null_build_with_globs

function test_bp2build_generates_all_buildfiles {
  setup
  create_mock_bazel

  mkdir -p foo/convertible_soong_module
  cat > foo/convertible_soong_module/Android.bp <<'EOF'
genrule {
    name: "the_answer",
    cmd: "echo '42' > $(out)",
    out: [
        "the_answer.txt",
    ],
    bazel_module: {
        bp2build_available: true,
    },
  }
EOF

  mkdir -p foo/unconvertible_soong_module
  cat > foo/unconvertible_soong_module/Android.bp <<'EOF'
genrule {
    name: "not_the_answer",
    cmd: "echo '43' > $(out)",
    out: [
        "not_the_answer.txt",
    ],
    bazel_module: {
        bp2build_available: false,
    },
  }
EOF

  run_soong bp2build

  if [[ ! -f "./out/soong/workspace/foo/convertible_soong_module/${GENERATED_BUILD_FILE_NAME}" ]]; then
    fail "./out/soong/workspace/foo/convertible_soong_module/${GENERATED_BUILD_FILE_NAME} was not generated"
  fi

  if [[ ! -f "./out/soong/workspace/foo/unconvertible_soong_module/${GENERATED_BUILD_FILE_NAME}" ]]; then
    fail "./out/soong/workspace/foo/unconvertible_soong_module/${GENERATED_BUILD_FILE_NAME} was not generated"
  fi

  if ! grep "the_answer" "./out/soong/workspace/foo/convertible_soong_module/${GENERATED_BUILD_FILE_NAME}"; then
    fail "missing BUILD target the_answer in convertible_soong_module/${GENERATED_BUILD_FILE_NAME}"
  fi

  if grep "not_the_answer" "./out/soong/workspace/foo/unconvertible_soong_module/${GENERATED_BUILD_FILE_NAME}"; then
    fail "found unexpected BUILD target not_the_answer in unconvertible_soong_module/${GENERATED_BUILD_FILE_NAME}"
  fi

  if ! grep "filegroup" "./out/soong/workspace/foo/unconvertible_soong_module/${GENERATED_BUILD_FILE_NAME}"; then
    fail "missing filegroup in unconvertible_soong_module/${GENERATED_BUILD_FILE_NAME}"
  fi

  # NOTE: We don't actually use the extra BUILD file for anything here
  run_bazel build --package_path=out/soong/workspace //foo/...

  local the_answer_file="bazel-out/android_target-fastbuild/bin/foo/convertible_soong_module/the_answer.txt"
  if [[ ! -f "${the_answer_file}" ]]; then
    fail "Expected '${the_answer_file}' to be generated, but was missing"
  fi
  if ! grep 42 "${the_answer_file}"; then
    fail "Expected to find 42 in '${the_answer_file}'"
  fi
}

test_bp2build_generates_all_buildfiles
