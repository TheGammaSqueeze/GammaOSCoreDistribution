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
# All components inherited here go to system image
#
$(call inherit-product, $(SRC_TARGET_DIR)/product/core_64_bit_only.mk)
$(call inherit-product, $(SRC_TARGET_DIR)/product/generic_system.mk)

# FIXME: generic_system.mk sets 'PRODUCT_ENFORCE_RRO_TARGETS := *'
#        but this breaks phone_car. So undo it here.
PRODUCT_ENFORCE_RRO_TARGETS := frameworks-res

# FIXME: Disable mainline path checks
PRODUCT_ENFORCE_ARTIFACT_PATH_REQUIREMENTS := false

#
# All components inherited here go to system_ext image
#
$(call inherit-product, $(SRC_TARGET_DIR)/product/base_system_ext.mk)

#
# All components inherited here go to product image
#
$(call inherit-product, $(SRC_TARGET_DIR)/product/aosp_product.mk)

#
# All components inherited here go to vendor image
#
LOCAL_DISABLE_OMX := true
$(call inherit-product, device/google/cuttlefish/shared/auto/device_vendor.mk)

# TODO(b/205788876) remove this when openwrt has an image for arm.
PRODUCT_ENFORCE_MAC80211_HWSIM := false

#
# Special settings for the target
#
$(call inherit-product, device/google/cuttlefish/vsoc_arm64/kernel.mk)
$(call inherit-product, device/google/cuttlefish/vsoc_arm64/bootloader.mk)

# Exclude features that are not available on AOSP devices.
PRODUCT_COPY_FILES += \
    frameworks/native/data/etc/aosp_excluded_hardware.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/aosp_excluded_hardware.xml

PRODUCT_NAME := aosp_cf_arm64_auto
PRODUCT_DEVICE := vsoc_arm64_only
PRODUCT_MANUFACTURER := Google
PRODUCT_MODEL := Cuttlefish arm64 auto

PRODUCT_VENDOR_PROPERTIES += \
    ro.soc.manufacturer=$(PRODUCT_MANUFACTURER) \
    ro.soc.model=$(PRODUCT_DEVICE)
