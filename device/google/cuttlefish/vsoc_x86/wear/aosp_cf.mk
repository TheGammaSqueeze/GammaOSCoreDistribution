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
$(call inherit-product, device/google/cuttlefish/shared/wear/aosp_system.mk)

# Allowed for wearables, but not installed to /system by default
PRODUCT_PACKAGES += \
    cameraserver \

# Cuttlefish uses A/B with system_b preopt, so we must install these
PRODUCT_PACKAGES += \
    cppreopts.sh \
    otapreopt_script \

# Hacks to boot with basic AOSP system apps
PRODUCT_PACKAGES += \
    Contacts \
    Launcher3QuickStep \
    Provision \
    Settings \
    StorageManager \
    SystemUI \

PRODUCT_COPY_FILES += \
    frameworks/native/data/etc/android.software.app_widgets.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.software.app_widgets.xml \

#
# All components inherited here go to system_ext image
#
$(call inherit-product, device/google/cuttlefish/shared/wear/aosp_system_ext.mk)

#
# All components inherited here go to product image
#
$(call inherit-product, device/google/cuttlefish/shared/wear/aosp_product.mk)

#
# All components inherited here go to vendor image
#
$(call inherit-product, device/google/cuttlefish/shared/wear/aosp_vendor.mk)
$(call inherit-product, device/google/cuttlefish/shared/wear/device_vendor.mk)
PRODUCT_ENFORCE_MAC80211_HWSIM := false

#
# Special settings for the target
#
$(call inherit-product, device/google/cuttlefish/vsoc_x86_64/kernel.mk)
$(call inherit-product, device/google/cuttlefish/vsoc_x86_64/bootloader.mk)

# Exclude features that are not available on AOSP devices.
PRODUCT_COPY_FILES += \
    frameworks/native/data/etc/aosp_excluded_hardware.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/aosp_excluded_hardware.xml \

PRODUCT_NAME := aosp_cf_x86_wear
PRODUCT_DEVICE := vsoc_x86
PRODUCT_MANUFACTURER := Google
PRODUCT_MODEL := Cuttlefish x86 wearable

PRODUCT_VENDOR_PROPERTIES += \
    ro.soc.manufacturer=$(PRODUCT_MANUFACTURER) \
    ro.soc.model=$(PRODUCT_DEVICE)
