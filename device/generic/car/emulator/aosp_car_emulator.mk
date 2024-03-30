#
# Copyright (C) 2019 The Android Open Source Project
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

# Enable Setup Wizard. This overrides the setting in emulator_vendor.mk
PRODUCT_SYSTEM_EXT_PROPERTIES += \
    ro.setupwizard.mode?=OPTIONAL

ifeq (,$(ENABLE_REAR_VIEW_CAMERA_SAMPLE))
ENABLE_REAR_VIEW_CAMERA_SAMPLE:=true
endif

PRODUCT_PACKAGE_OVERLAYS := device/generic/car/emulator/overlay

$(call inherit-product, device/generic/car/common/car.mk)
# This overrides device/generic/car/common/car.mk
$(call inherit-product, device/generic/car/emulator/audio/car_emulator_audio.mk)
$(call inherit-product, device/generic/car/emulator/rotary/car_rotary.mk)
# Enables USB related passthrough
$(call inherit-product, device/generic/car/emulator/usbpt/car_usbpt.mk)

TARGET_PRODUCT_PROP := device/generic/car/emulator/usbpt/bluetooth/bluetooth.prop

ifeq (true,$(BUILD_EMULATOR_CLUSTER_DISPLAY))
PRODUCT_COPY_FILES += \
    device/generic/car/emulator/cluster/display_settings.xml:system/etc/display_settings.xml \

ifeq ($(EMULATOR_MULTIDISPLAY_HW_CONFIG),)
PRODUCT_PRODUCT_PROPERTIES += \
    hwservicemanager.external.displays=1,400,600,120,0 \
    persist.service.bootanim.displays=8140900251843329
else
ifneq ($(EMULATOR_MULTIDISPLAY_BOOTANIM_CONFIG),)
$(warning Setting displays to $(EMULATOR_MULTIDISPLAY_HW_CONFIG) and bootanims to $(EMULATOR_MULTIDISPLAY_BOOTANIM_CONFIG))
    PRODUCT_PRODUCT_PROPERTIES += \
        hwservicemanager.external.displays=$(EMULATOR_MULTIDISPLAY_HW_CONFIG) \
        persist.service.bootanim.displays=$(EMULATOR_MULTIDISPLAY_BOOTANIM_CONFIG)
else #  EMULATOR_MULTIDISPLAY_BOOTANIM_CONFIG
$(error EMULATOR_MULTIDISPLAY_BOOTANIM_CONFIG has to be defined when EMULATOR_MULTIDISPLAY_BOOTANIM_CONFIG is defined)
endif # EMULATOR_MULTIDISPLAY_BOOTANIM_CONFIG
endif # EMULATOR_HW_MULTIDISPLAY_CONFIG

ifeq (true,$(ENABLE_CLUSTER_OS_DOUBLE))
PRODUCT_PACKAGES += CarServiceOverlayEmulatorOsDouble
else
PRODUCT_PACKAGES += CarServiceOverlayEmulator
endif  # ENABLE_CLUSTER_OS_DOUBLE
endif  # BUILD_EMULATOR_CLUSTER_DISPLAY

PRODUCT_PACKAGES += CarServiceOverlayEmulatorMedia

PRODUCT_PRODUCT_PROPERTIES += \
    ro.carwatchdog.vhal_healthcheck.interval=10 \
    ro.carwatchdog.client_healthcheck.interval=20 \
