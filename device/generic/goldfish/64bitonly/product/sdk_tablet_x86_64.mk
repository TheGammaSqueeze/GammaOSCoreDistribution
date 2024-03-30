#
# Copyright (C) 2021 The Android Open Source Project
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
QEMU_USE_SYSTEM_EXT_PARTITIONS := true
PRODUCT_USE_DYNAMIC_PARTITIONS := true
EMULATOR_DISABLE_RADIO := true

PRODUCT_COPY_FILES += \
    device/generic/goldfish/data/etc/advancedFeatures.ini.tablet:advancedFeatures.ini \
    device/generic/goldfish/data/etc/config.ini.nexus7tab:config.ini

PRODUCT_CHARACTERISTICS := tablet,nosdcard

#
# All components inherited here go to system image
#
$(call inherit-product, $(SRC_TARGET_DIR)/product/core_64_bit.mk)
$(call inherit-product, $(SRC_TARGET_DIR)/product/generic_no_telephony.mk)

# Enable mainline checking for excat this product name
ifeq (sdk_tablet_x86_64,$(TARGET_PRODUCT))
PRODUCT_ENFORCE_ARTIFACT_PATH_REQUIREMENTS := relaxed
endif

#
# All components inherited here go to product image
#

PRODUCT_SDK_ADDON_SYS_IMG_SOURCE_PROP := \
    development/sys-img/images_x86_64_source.prop_template

#
# All components inherited here go to vendor image
#
$(call inherit-product, device/generic/goldfish/64bitonly/product/x86_64-vendor.mk)
$(call inherit-product, device/generic/goldfish/64bitonly/product/emulator64_vendor.mk)
$(call inherit-product, device/generic/goldfish/emulator64_x86_64/device.mk)


# Overrides
PRODUCT_BRAND := Android
PRODUCT_NAME := sdk_tablet_x86_64
PRODUCT_DEVICE := emulator64_x86_64
PRODUCT_MODEL := Android SDK built for x86_64
