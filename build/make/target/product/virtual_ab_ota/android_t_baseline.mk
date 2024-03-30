#
# Copyright (C) 2022 The Android Open-Source Project
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

# This file enables baseline features, such as io_uring,
# userspace merge, etc. But sets compression method to none.
# This .mk file also removes snapuserd from vendor ramdisk,
# as T launching devices will have init_boot which has snapuserd
# in generic ramdisk.
# T launching devices should include this .mk file, and configure
# compression algorithm by setting
# PRODUCT_VIRTUAL_AB_COMPRESSION_METHOD to gz or brotli. Complete
# set of supported algorithms can be found in
# system/core/fs_mgr/libsnapshot/cow_writer.cpp

PRODUCT_VIRTUAL_AB_OTA := true

PRODUCT_VENDOR_PROPERTIES += ro.virtual_ab.enabled=true

PRODUCT_VENDOR_PROPERTIES += ro.virtual_ab.compression.enabled=true
PRODUCT_VENDOR_PROPERTIES += ro.virtual_ab.userspace.snapshots.enabled=true
PRODUCT_VENDOR_PROPERTIES += ro.virtual_ab.io_uring.enabled=true
PRODUCT_VENDOR_PROPERTIES += ro.virtual_ab.compression.xor.enabled=true

PRODUCT_VIRTUAL_AB_COMPRESSION := true
PRODUCT_VIRTUAL_AB_COMPRESSION_METHOD ?= none
PRODUCT_PACKAGES += \
    snapuserd \

# For dedicated recovery partitions, we need to include snapuserd
# For GKI devices, BOARD_USES_RECOVERY_AS_BOOT is empty, but
# so is BOARD_MOVE_RECOVERY_RESOURCES_TO_VENDOR_BOOT.
ifdef BUILDING_RECOVERY_IMAGE
ifneq ($(BOARD_USES_RECOVERY_AS_BOOT),true)
ifneq ($(BOARD_MOVE_RECOVERY_RESOURCES_TO_VENDOR_BOOT),true)
PRODUCT_PACKAGES += \
    snapuserd.recovery
endif
endif
endif

