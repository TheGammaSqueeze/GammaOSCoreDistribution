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

TARGET_CPU_ABI := x86_64
TARGET_ARCH := x86_64
TARGET_ARCH_VARIANT := x86_64

TARGET_2ND_CPU_ABI := x86
TARGET_2ND_ARCH := x86
TARGET_2ND_ARCH_VARIANT := x86_64

include device/generic/common/BoardConfigGkiCommon.mk

BOARD_KERNEL-5.4_BOOTIMAGE_PARTITION_SIZE := 67108864
BOARD_KERNEL-5.10_BOOTIMAGE_PARTITION_SIZE := 67108864
BOARD_KERNEL-5.10-ALLSYMS_BOOTIMAGE_PARTITION_SIZE := 67108864
BOARD_KERNEL-5.15_BOOTIMAGE_PARTITION_SIZE := 67108864
BOARD_KERNEL-5.15-ALLSYMS_BOOTIMAGE_PARTITION_SIZE := 67108864

# Boot images
BOARD_KERNEL_BINARIES := \
    kernel-5.10 \
    kernel-5.15 \

ifneq (,$(filter userdebug eng,$(TARGET_BUILD_VARIANT)))
BOARD_KERNEL_BINARIES += \
    kernel-5.10-allsyms \
    kernel-5.15-allsyms \

endif
