#
# Copyright (C) 2020 The Android Open Source Project
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

# This makefile is the basis of a generic system image for a TV device.
$(call inherit-product, device/google/atv/products/atv_system.mk)
$(call inherit-product, $(SRC_TARGET_DIR)/product/languages_default.mk)
# Add adb keys to debuggable AOSP builds (if they exist)
$(call inherit-product-if-exists, vendor/google/security/adb/vendor_key.mk)

# Enable updating of APEXes
$(call inherit-product, $(SRC_TARGET_DIR)/product/updatable_apex.mk)

PRODUCT_PACKAGES += \
    DocumentsUI \
    com.android.mtp \
    MtpService \
    android.server.tvproviderstub \

PRODUCT_STATIC_LIBRARIES += androidx.appcompat_appcompat

# Wrapped net utils for /vendor access.
PRODUCT_PACKAGES += netutils-wrapper-1.0 \

# Bluetooth Audio (System-side HAL, sysbta)
PRODUCT_PACKAGES += \
    audio.sysbta.default \
    android.hardware.bluetooth.audio-service-system \

# system_other support
#PRODUCT_PACKAGES += \
#    cppreopts.sh \
#    otapreopt_script

# System libraries commonly depended on by things on the system_ext or product partitions.
# These lists will be pruned periodically.
PRODUCT_PACKAGES += \
    android.hardware.wifi@1.0 \
    libaudio-resampler \
    libaudiohal \
    libdrm \
    liblogwrap \
    liblz4 \
    libminui \
    libnl \
    libprotobuf-cpp-full \

# These libraries are empty and have been combined into libhidlbase, but are still depended
# on by things off /system.
# TODO(b/135686713): remove these
PRODUCT_PACKAGES += \
    libhidltransport \
    libhwbinder \

PRODUCT_PACKAGES_ENG += \
    avbctl \
    bootctl \
    tinyplay \
    tinycap \
    tinymix \
    tinypcminfo \
    update_engine_client \

PRODUCT_HOST_PACKAGES += \
    tinyplay

# Enable configurable audio policy
PRODUCT_PACKAGES += \
    libaudiopolicyengineconfigurable \
    libpolicy-subsystem

# Include all zygote init scripts. "ro.zygote" will select one of them.
PRODUCT_COPY_FILES += \
    system/core/rootdir/init.zygote32.rc:system/etc/init/hw/init.zygote32.rc \
    system/core/rootdir/init.zygote64.rc:system/etc/init/hw/init.zygote64.rc \
    system/core/rootdir/init.zygote64_32.rc:system/etc/init/hw/init.zygote64_32.rc

# GammaOS Customizations

PRODUCT_COPY_FILES += \
    device/google/atv/bluetooth/audio/config/sysbta_audio_policy_configuration.xml:system/etc/sysbta_audio_policy_configuration.xml \
    device/google/atv/bluetooth/audio/config/sysbta_audio_policy_configuration_7_0.xml:system/etc/sysbta_audio_policy_configuration_7_0.xml \
    device/google/atv/gammaos/utils/xz:system/bin/xz \
    device/google/atv/gammaos/customization.sh:system/bin/customization.sh \
    device/google/atv/gammaos/magisk/magisk.apk:system/etc/magisk.apk \
    device/google/atv/gammaos/magisk/magisk.tar.gz:system/etc/magisk.tar.gz \
    device/google/atv/gammaos/retroarch/RetroArch_aarch64.apk:system/etc/RetroArch_aarch64.apk \
    device/google/atv/gammaos/retroarch/retroarch.tar.xz:system/etc/retroarch.tar.xz \
    device/google/atv/gammaos/retroarch/retroarch64sdcard1-arc.tar.gz:system/etc/retroarch64sdcard1-arc.tar.gz \
    device/google/atv/gammaos/retroarch/roms.tar.xz:system/etc/roms.tar.xz \
    device/google/atv/gammaos/hdmiaudio/silent.mp3:system/etc/silent.mp3 \
    device/google/atv/gammaos/hdmiaudio/hdmiaudio.sh:system/bin/hdmiaudio.sh \
    device/google/atv/gammaos/setup.sh:system/bin/setup.sh \
    device/google/atv/gammaos/launcher/MiXplorer_v6.64.3-API29_B23090720.apk:system/etc/MiXplorer_v6.64.3-API29_B23090720.apk \
    device/google/atv/gammaos/launcher/AuroraStore_4.6.2.apk:system/etc/AuroraStore_4.6.2.apk \
    device/google/atv/gammaos/launcher/aurorastore.tar.gz:system/etc/aurorastore.tar.gz \
    device/google/atv/gammaos/toast/gammaos-displayloading.apk:system/etc/gammaos-displayloading.apk \
    device/google/atv/gammaos/daijisho/daijisho_408.tar.gz:system/etc/daijisho_408.tar.gz \
    device/google/atv/gammaos/daijisho/daijisho.tar.xz:system/etc/daijisho.tar.xz \
    device/google/atv/gammaos/toast/Toast.apk:system/etc/Toast.apk \
    device/google/atv/gammaos/emulators/drastic.tar.gz:system/etc/drastic.tar.gz \
    device/google/atv/gammaos/emulators/drastic_r2.6.0.4a.apk:system/etc/drastic_r2.6.0.4a.apk \
    device/google/atv/gammaos/emulators/mupen64plusae.tar.gz:system/etc/mupen64plusae.tar.gz \
    device/google/atv/gammaos/emulators/mupen64plusae_3.0.335.apk:system/etc/mupen64plusae_3.0.335.apk \
    device/google/atv/gammaos/emulators/ppsspp.tar.xz:system/etc/ppsspp.tar.xz \
    device/google/atv/gammaos/emulators/ppsspp_1.18.1.apk:system/etc/ppsspp_1.18.1.apk \
    device/google/atv/gammaos/emulators/flycast-release.apk:system/etc/flycast-release.apk \
    device/google/atv/gammaos/emulators/flycast.tar.xz:system/etc/flycast.tar.xz \
    device/google/atv/gammaos/launcher/gboard.tar.gz:system/etc/gboard.tar.gz \

# Enable dynamic partition size
PRODUCT_USE_DYNAMIC_PARTITION_SIZE := true

PRODUCT_ENFORCE_RRO_TARGETS := *

PRODUCT_NAME := atv_generic_system
PRODUCT_BRAND := generic

# Define /system partition-specific product properties to identify that /system
# partition is atv_generic_system.
PRODUCT_SYSTEM_NAME := atv_generic
PRODUCT_SYSTEM_BRAND := Android
PRODUCT_SYSTEM_MANUFACTURER := Android
PRODUCT_SYSTEM_MODEL := atv_generic
PRODUCT_SYSTEM_DEVICE := generic

_base_mk_whitelist :=

_my_whitelist := $(_base_mk_whitelist)

# For mainline, system.img should be mounted at /, so we include ROOT here.
_my_paths := \
  $(TARGET_COPY_OUT_ROOT)/ \
  $(TARGET_COPY_OUT_SYSTEM)/ \

$(call require-artifacts-in-path, $(_my_paths), $(_my_whitelist))
