#
# Copyright (C) 2021 The Android Open-Source Project
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

$(call inherit-product, packages/services/Car/car_product/car_ui_portrait/rro/car-ui-customizations/product.mk)
$(call inherit-product, packages/services/Car/car_product/car_ui_portrait/rro/car-ui-toolbar-customizations/product.mk)

# All RROs to be included in car_ui_portrait builds.
PRODUCT_PACKAGES += \
    CarEvsCameraPreviewAppRRO \
    CarUiPortraitDialerRRO \
    CarUiPortraitSettingsRRO \
    CarUiPortraitMediaRRO \
    CarUiPortraitMediaCommonRRO \
    CarUiPortraitLauncherRRO \
    CarUiPortraitNotificationRRO \
    CarUiPortraitCarServiceRRO \
    CarUiPortraitFrameworkResRRO \
    CarUiPortraitFrameworkResRROTest \
    CarUiPortraitLauncherMediaRRO \
    CarUiPortraitLauncherAppsRRO

ifneq ($(INCLUDE_SEAHAWK_ONLY_RROS),)
PRODUCT_PACKAGES += \
    CarUiPortraitSettingsProviderRRO
endif

# Set necessary framework configs for SUW to run at boot.
ifneq ($(filter $(TARGET_PRODUCT), gcar_ui_portrait_suw),)
PRODUCT_PACKAGES += \
    CarUiPortraitSettingsProviderEmuRRO
endif

PRODUCT_PROPERTY_OVERRIDES += \
    ro.boot.vendor.overlay.theme=com.android.car.carlauncher.caruiportrait.rro;com.android.car.dialer.caruiportrait.rro;com.google.android.car.evs.caruiportrait.rro;com.android.car.carlauncher.apps.caruiportrait.rro;com.android.car.caruiportrait.rro;com.android.car.carlauncher.media.caruiportrait.rro;com.android.car.media.common.caruiportrait.rro;com.android.car.media.caruiportrait.rro;com.android.car.notification.caruiportrait.rro;com.android.providers.settings.caruiportrait.emu.rro;com.android.providers.settings.caruiportrait.rro;com.android.car.settings.caruiportrait.rro
