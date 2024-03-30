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

# Car UI Portrait Emulator Target

# Exclude AAE Car System UI
DO_NOT_INCLUDE_AAE_CAR_SYSTEM_UI := true

# Exclude Car UI Reference Design
DO_NOT_INCLUDE_CAR_UI_REFERENCE_DESIGN := true

# Exclude Car Visual Overlay
DISABLE_CAR_PRODUCT_VISUAL_OVERLAY := true

# Use the common Car Overlay
PRODUCT_PACKAGE_OVERLAYS := device/generic/car/common/overlay

# Copy additional files
PRODUCT_COPY_FILES += \
    packages/services/Car/car_product/car_ui_portrait/car_ui_portrait.ini:config.ini \
    packages/services/Car/car_product/car_ui_portrait/bootanimation/bootanimation.zip:system/media/bootanimation.zip

$(call inherit-product, device/generic/car/emulator/aosp_car_emulator.mk)
$(call inherit-product, $(SRC_TARGET_DIR)/product/sdk_x86_64.mk)

PRODUCT_NAME := sdk_car_portrait_x86_64
PRODUCT_MODEL := CarUiPortrait on x86_64 emulator
PRODUCT_CHARACTERISTICS := automotive
PRODUCT_SDK_ADDON_NAME := sdk_car_portrait_x86_64

$(call inherit-product, packages/services/Car/car_product/car_ui_portrait/apps/car_ui_portrait_apps.mk)
$(call inherit-product, packages/services/Car/car_product/car_ui_portrait/rro/car_ui_portrait_rro.mk)

# Use the default goldfish audio implementation
EMULATOR_VENDOR_NO_SOUND := true