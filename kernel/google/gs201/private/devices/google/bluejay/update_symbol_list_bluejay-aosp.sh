#!/bin/bash
# SPDX-License-Identifier: GPL-2.0
BUILD_AOSP_KERNEL=1 \
BUILD_STAGING_KERNEL=0 \
BUILD_SCRIPT="./build_bluejay.sh" \
DEVICE_KERNEL_BUILD_CONFIG=private/devices/google/bluejay/build.config.bluejay \
private/gs-google/update_symbol_list.sh "$@"
