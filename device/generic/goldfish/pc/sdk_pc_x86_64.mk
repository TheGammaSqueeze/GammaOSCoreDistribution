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

PRODUCT_COPY_FILES += device/generic/goldfish/pc/config.ini.pc:config.ini

$(call inherit-product, $(SRC_TARGET_DIR)/product/sdk_x86_64.mk)

PRODUCT_NAME := sdk_pc_x86_64
PRODUCT_DEVICE := emulator64_x86_64
PRODUCT_BRAND := Android
PRODUCT_MODEL := PC on x86_64 emulator
PRODUCT_PACKAGE_OVERLAYS := device/generic/goldfish/pc/overlay
PRODUCT_COPY_FILES += \
    device/generic/goldfish/pc/data/etc/pc.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/pc.xml
PRODUCT_SDK_ADDON_SYS_IMG_SOURCE_PROP := device/generic/goldfish/pc/images_source.prop_template
