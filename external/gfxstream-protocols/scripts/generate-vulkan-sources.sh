#!/bin/sh

# Copyright 2022 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

PROJECT_ROOT=$(pwd)

WHICH=which
if [[ "$OSTYPE" == "msys" ]]; then
    WHICH=where
fi

# Detect clang-format

if ! $WHICH clang-format > /dev/null; then
    echo "Failed to find clang-format." 1>&2
    exit 1
fi

# Generate Vulkan headers
VULKAN_HEADERS_ROOT=$PROJECT_ROOT/include/vulkan
rm -rf $VULKAN_HEADERS_ROOT && mkdir -p $VULKAN_HEADERS_ROOT
if [ $? -ne 0 ]; then
    echo "Failed to clear the old Vulkan headers." 1>&2
    exit 1
fi

cd registry/vulkan/xml && make GENOPTS="-removeExtensions VK_GOOGLE_gfxstream" GENERATED=$VULKAN_HEADERS_ROOT
if [ $? -ne 0 ]; then
    echo "Failed to generate Vulkan headers." 1>&2
    exit 1
fi

cd $PROJECT_ROOT

AOSP_DIR=$(pwd)/../../
VK_CEREAL_GUEST_DIR=$AOSP_DIR/device/generic/goldfish-opengl
VK_CEREAL_HOST_DIR=$AOSP_DIR/device/generic/vulkan-cereal
export VK_CEREAL_GUEST_ENCODER_DIR=$VK_CEREAL_GUEST_DIR/system/vulkan_enc
export VK_CEREAL_GUEST_HAL_DIR=$VK_CEREAL_GUEST_DIR/system/vulkan
export VK_CEREAL_HOST_DECODER_DIR=$VK_CEREAL_HOST_DIR/stream-servers/vulkan
export VK_CEREAL_HOST_INCLUDE_DIR=$VK_CEREAL_HOST_DIR/stream-servers
export VK_CEREAL_HOST_SCRIPTS_DIR=$VK_CEREAL_HOST_DIR/scripts
export VK_CEREAL_BASELIB_PREFIX=aemu/base
export VK_CEREAL_BASELIB_LINKNAME=aemu-base.headers
export VK_CEREAL_VK_HEADER_TARGET=gfxstream_vulkan_headers

VK_CEREAL_OUTPUT_DIR=$VK_CEREAL_HOST_DECODER_DIR/cereal
if [ -d "$VK_CEREAL_GUEST_DIR" ]; then
    mkdir -p $VK_CEREAL_GUEST_ENCODER_DIR
    mkdir -p $VK_CEREAL_GUEST_HAL_DIR
fi
if [ -d "$VK_CEREAL_HOST_DIR" ]; then
    mkdir -p $VK_CEREAL_HOST_DECODER_DIR
    mkdir -p $VK_CEREAL_OUTPUT_DIR
fi

VULKAN_REGISTRY_DIR=$AOSP_DIR/external/gfxstream-protocols/registry/vulkan
VULKAN_REGISTRY_XML_DIR=$VULKAN_REGISTRY_DIR/xml
VULKAN_REGISTRY_SCRIPTS_DIR=$VULKAN_REGISTRY_DIR/scripts

python3 $VULKAN_REGISTRY_SCRIPTS_DIR/genvk.py -registry $VULKAN_REGISTRY_XML_DIR/vk.xml cereal -o $VK_CEREAL_OUTPUT_DIR


# Generate VK_ANDROID_native_buffer specific Vulkan definitions.
if [ -d $VK_CEREAL_HOST_DECODER_DIR ]; then
    OUT_DIR=$VK_CEREAL_HOST_DECODER_DIR
    OUT_FILE_BASENAME="vk_android_native_buffer.h"

    python3 registry/vulkan/scripts/genvk.py -registry registry/vulkan/xml/vk.xml -o $OUT_DIR \
        $OUT_FILE_BASENAME

    if [ $? -ne 0 ]; then
        echo "Failed to generate vk_android_native_buffer.h" 1>&2
        exit 1
    fi
    if ! clang-format -i $OUT_DIR/$OUT_FILE_BASENAME; then
        echo "Failed to reformat vk_android_native_buffer.h" 1>&2
        exit 1
    fi
fi

# Generate gfxstream specific Vulkan definitions.
for OUT_DIR in $VK_CEREAL_HOST_DECODER_DIR $VK_CEREAL_GUEST_ENCODER_DIR; do
    if [ -d "$OUT_DIR" ]; then
        OUT_FILE_BASENAME=vulkan_gfxstream.h
        python3 registry/vulkan/scripts/genvk.py -registry registry/vulkan/xml/vk.xml -o $OUT_DIR \
            $OUT_FILE_BASENAME

        if [ $? -ne 0 ]; then
            echo "Failed to generate gfxstream specific vulkan headers." 1>&2
            exit 1
        fi
        if ! clang-format -i $OUT_DIR/$OUT_FILE_BASENAME; then
            echo "Failed to reformat gfxstream specific vulkan headers." 1>&2
            exit 1
        fi
    fi
done
