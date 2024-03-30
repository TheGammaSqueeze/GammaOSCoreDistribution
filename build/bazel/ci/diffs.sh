#!/bin/bash -eu
# checks the diff between legacy Soong built artifacts and their counterparts
# built with bazel/mixed build
export TARGET_PRODUCT=aosp_arm64
export TARGET_BUILD_VARIANT=userdebug

build/soong/soong_ui.bash \
  --build-mode \
  --all-modules \
  --dir="$(pwd)" \
  bp2build
tools/bazel build --config=bp2build //build/bazel/scripts/difftool:collect_zip
tools/bazel build --config=bp2build //build/bazel/scripts/difftool:difftool_zip

# the following 2 arrays must be of the same size
MODULES=(
  libnativehelper
)
OUTPUTS=(
  JNIHelp.o
)
PATH_FILTERS=(
  "linux_glibc_x86_shared/\|linux_x86-fastbuild"
  "linux_glibc_x86_64_shared/\|linux_x86_64-fastbuild"
  "android_arm64[-_]"
#  "android_arm[-_]" TODO(usta) investigate why there is a diff for this
)
readonly AOSP_ROOT="$(readlink -f "$(dirname "$0")"/../../..)"
#TODO(usta): absolute path isn't compatible with collect.py and ninja
readonly LEGACY_OUTPUT_SEARCH_TREE="out/soong/.intermediates/libnativehelper"
readonly MIXED_OUTPUT_SEARCH_TREE="out/bazel/output/execroot/__main__/bazel-out"
readonly NINJA_FILE="$AOSP_ROOT/out/combined-$TARGET_PRODUCT.ninja"
# python is expected in PATH but used only to start a zipped python archive,
# which bundles its own interpreter. We could also simply use `tools/bazel run`
# instead however that sets the working directly differently and collect.py
# won't work because it expects paths relative to $OUT_DIR
# TODO(usta) make collect.py work with absolute paths and maybe consider
# using `tools/bazel run` on the `py_binary` target directly instead of using
# the python_zip_file filegroup's output
readonly stub_python=python3
readonly LEGACY_COLLECTION="$AOSP_ROOT/out/diff_metadata/legacy"
readonly MIXED_COLLECTION="$AOSP_ROOT/out/diff_metadata/mixed"
mkdir -p "$LEGACY_COLLECTION"
mkdir -p "$MIXED_COLLECTION"

function findIn() {
  result=$(find "$1" -name "$3" | grep "$2")
  count=$(echo "$result" | wc -l)
  if [ "$count" != 1 ]; then
    printf "multiple files found instead of exactly ONE:\n%s\n" "$result" 1>&2
    exit 1
  fi
  echo "$result"
}

for ((i = 0; i < ${#MODULES[@]}; i++)); do
  MODULE=${MODULES[$i]}
  echo "Building $MODULE for comparison"
  build/soong/soong_ui.bash --make-mode "$MODULE"
  $stub_python "bazel-bin/build/bazel/scripts/difftool/collect.zip" \
    "$NINJA_FILE" "$LEGACY_COLLECTION"
  build/soong/soong_ui.bash \
    --make-mode \
    USE_BAZEL_ANALYSIS=1 \
    BAZEL_STARTUP_ARGS="--max_idle_secs=5" \
    BAZEL_BUILD_ARGS="--color=no --curses=no --noshow_progress" \
    "$MODULE"
  $stub_python "bazel-bin/build/bazel/scripts/difftool/collect.zip" \
      "$NINJA_FILE" "$MIXED_COLLECTION"
  OUTPUT=${OUTPUTS[$i]}
  for ((j = 0; j < ${#PATH_FILTERS[@]}; j++)); do
    PATH_FILTER=${PATH_FILTERS[$j]}
    LEGACY_OUTPUT=$(findIn "$LEGACY_OUTPUT_SEARCH_TREE" "$PATH_FILTER" "$OUTPUT")
    MIXED_OUTPUT=$(findIn "$MIXED_OUTPUT_SEARCH_TREE" "$PATH_FILTER" "$OUTPUT")

    LEGACY_COLLECTION_DIR=$(dirname "$LEGACY_COLLECTION/$LEGACY_OUTPUT")
    mkdir -p "$LEGACY_COLLECTION_DIR"
    cp "$LEGACY_OUTPUT" "$LEGACY_COLLECTION_DIR"
    MIXED_COLLECTION_DIR=$(dirname "$MIXED_COLLECTION/$MIXED_OUTPUT")
    mkdir -p "$MIXED_COLLECTION_DIR"
    cp "$MIXED_OUTPUT" "$MIXED_COLLECTION_DIR"

    $stub_python "bazel-bin/build/bazel/scripts/difftool/difftool.zip" \
      --level=SEVERE -v "$LEGACY_COLLECTION" "$MIXED_COLLECTION" \
      -l="$LEGACY_OUTPUT" -r="$MIXED_OUTPUT"
  done
done

