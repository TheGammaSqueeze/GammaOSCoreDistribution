#
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

#
# TODO (b/212486689): The minimum system stuff for build pass.
#
$(call inherit-product, $(SRC_TARGET_DIR)/product/core_64_bit.mk)
$(call inherit-product, $(SRC_TARGET_DIR)/product/runtime_libart.mk)

#
# Build GKI boot images
#
include device/generic/common/gki_common.mk

PRODUCT_COPY_FILES += \
    kernel/prebuilts/5.10/x86_64/kernel-5.10:kernel-5.10 \
    kernel/prebuilts/5.15/x86_64/kernel-5.15:kernel-5.15 \

$(call dist-for-goals,dist_files,kernel/prebuilts/5.10/x86_64/prebuilt-info.txt:kernel/5.10/prebuilt-info.txt)
$(call dist-for-goals,dist_files,kernel/prebuilts/5.15/x86_64/prebuilt-info.txt:kernel/5.15/prebuilt-info.txt)

ifneq (,$(filter userdebug eng,$(TARGET_BUILD_VARIANT)))

PRODUCT_COPY_FILES += \
    kernel/prebuilts/5.10/x86_64/kernel-5.10-allsyms:kernel-5.10-allsyms \
    kernel/prebuilts/5.15/x86_64/kernel-5.15-allsyms:kernel-5.15-allsyms \

$(call dist-for-goals,dist_files,kernel/prebuilts/5.10/x86_64/prebuilt-info.txt:kernel/5.10-debug/prebuilt-info.txt)
$(call dist-for-goals,dist_files,kernel/prebuilts/5.15/x86_64/prebuilt-info.txt:kernel/5.15-debug/prebuilt-info.txt)

endif


PRODUCT_NAME := gki_x86_64
PRODUCT_DEVICE := gki_x86_64
PRODUCT_BRAND := Android
PRODUCT_MODEL := GKI on x86_64
