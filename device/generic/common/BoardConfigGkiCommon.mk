# Copyright (C) 2022 The Android Open Source Project
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
#

# Enable GKI 2.0 certification
BOARD_GKI_SIGNING_KEY_PATH := build/make/target/product/gsi/testkey_rsa2048.pem
BOARD_GKI_SIGNING_ALGORITHM := SHA256_RSA2048

# Enable chained vbmeta for boot and init_boot images
BOARD_AVB_ENABLE := true
BOARD_AVB_ROLLBACK_INDEX := $(PLATFORM_SECURITY_PATCH_TIMESTAMP)

BOARD_AVB_BOOT_KEY_PATH := external/avb/test/data/testkey_rsa4096.pem
BOARD_AVB_BOOT_ALGORITHM := SHA256_RSA4096
BOARD_AVB_BOOT_ROLLBACK_INDEX := $(PLATFORM_SECURITY_PATCH_TIMESTAMP)
BOARD_AVB_BOOT_ROLLBACK_INDEX_LOCATION := 2

BOARD_AVB_INIT_BOOT_KEY_PATH := external/avb/test/data/testkey_rsa4096.pem
BOARD_AVB_INIT_BOOT_ALGORITHM := SHA256_RSA4096
BOARD_AVB_INIT_BOOT_ROLLBACK_INDEX := $(PLATFORM_SECURITY_PATCH_TIMESTAMP)
BOARD_AVB_INIT_BOOT_ROLLBACK_INDEX_LOCATION := 3

# Sets boot SPL.
BOOT_SECURITY_PATCH = $(PLATFORM_SECURITY_PATCH)

# Boot image with kernel only (no ramdisk)
BOARD_BOOT_HEADER_VERSION := 4
BOARD_MKBOOTIMG_ARGS += --header_version $(BOARD_BOOT_HEADER_VERSION)
BOARD_USES_RECOVERY_AS_BOOT :=
TARGET_NO_KERNEL := false
BOARD_USES_GENERIC_KERNEL_IMAGE := true
BOARD_BUILD_GKI_BOOT_IMAGE_WITHOUT_RAMDISK ?= true

# No system image
BOARD_SYSTEMIMAGE_PARTITION_SIZE :=

# No vendor_boot
BOARD_MOVE_RECOVERY_RESOURCES_TO_VENDOR_BOOT :=

# No recovery
BOARD_EXCLUDE_KERNEL_FROM_RECOVERY_IMAGE :=
