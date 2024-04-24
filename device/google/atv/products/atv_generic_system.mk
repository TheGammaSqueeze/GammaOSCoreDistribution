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

# Wrapped net utils for /vendor access.
PRODUCT_PACKAGES += netutils-wrapper-1.0

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
    libprotobuf-cpp-full

# These libraries are empty and have been combined into libhidlbase, but are still depended
# on by things off /system.
# TODO(b/135686713): remove these
PRODUCT_PACKAGES += \
    libhidltransport \
    libhwbinder

PRODUCT_PACKAGES_ENG += \
    avbctl \
    bootctl \
    tinyplay \
    tinycap \
    tinymix \
    tinypcminfo \
    update_engine_client

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
    device/google/atv/gammaos/rgp2xbox/rgp2xbox:system/bin/rgp2xbox \
    device/google/atv/gammaos/rgp2xbox/rgp2xbox.sh:system/bin/rgp2xbox.sh \
    device/google/atv/gammaos/rgp2xbox/setabxyvalue_default.sh:system/bin/setabxyvalue_default.sh \
    device/google/atv/gammaos/rgp2xbox/setabxyvalue_swapped.sh:system/bin/setabxyvalue_swapped.sh \
    device/google/atv/gammaos/rgp2xbox/setanalogaxisvalue_default.sh:system/bin/setanalogaxisvalue_default.sh \
    device/google/atv/gammaos/rgp2xbox/setanalogaxisvalue_swapped.sh:system/bin/setanalogaxisvalue_swapped.sh \
    device/google/atv/gammaos/rgp2xbox/setanalogsensitivity_15.sh:system/bin/setanalogsensitivity_15.sh \
    device/google/atv/gammaos/rgp2xbox/setanalogsensitivity_25.sh:system/bin/setanalogsensitivity_25.sh \
    device/google/atv/gammaos/rgp2xbox/setanalogsensitivity_50.sh:system/bin/setanalogsensitivity_50.sh \
    device/google/atv/gammaos/rgp2xbox/setanalogsensitivity_custom.sh:system/bin/setanalogsensitivity_custom.sh \
    device/google/atv/gammaos/rgp2xbox/setanalogsensitivity_default.sh:system/bin/setanalogsensitivity_default.sh \
    device/google/atv/gammaos/rgp2xbox/setclock_max.sh:system/bin/setclock_max.sh \
    device/google/atv/gammaos/rgp2xbox/setclock_powersave.sh:system/bin/setclock_powersave.sh \
    device/google/atv/gammaos/rgp2xbox/setclock_stock.sh:system/bin/setclock_stock.sh \
    device/google/atv/gammaos/rgp2xbox/setclockvalue_max.sh:system/bin/setclockvalue_max.sh \
    device/google/atv/gammaos/rgp2xbox/setclockvalue_powersave.sh:system/bin/setclockvalue_powersave.sh \
    device/google/atv/gammaos/rgp2xbox/setclockvalue_stock.sh:system/bin/setclockvalue_stock.sh \
    device/google/atv/gammaos/rgp2xbox/setdpadanalogtoggle_off.sh:system/bin/setdpadanalogtoggle_off.sh \
    device/google/atv/gammaos/rgp2xbox/setdpadanalogtoggle_on.sh:system/bin/setdpadanalogtoggle_on.sh \
    device/google/atv/gammaos/rgp2xbox/setfan_auto.sh:system/bin/setfan_auto.sh \
    device/google/atv/gammaos/rgp2xbox/setfan_cool.sh:system/bin/setfan_cool.sh \
    device/google/atv/gammaos/rgp2xbox/setfan_max.sh:system/bin/setfan_max.sh \
    device/google/atv/gammaos/rgp2xbox/setfan_off.sh:system/bin/setfan_off.sh \
    device/google/atv/gammaos/rgp2xbox/setfanvalue_auto.sh:system/bin/setfanvalue_auto.sh \
    device/google/atv/gammaos/rgp2xbox/setfanvalue_cool.sh:system/bin/setfanvalue_cool.sh \
    device/google/atv/gammaos/rgp2xbox/setfanvalue_max.sh:system/bin/setfanvalue_max.sh \
    device/google/atv/gammaos/rgp2xbox/setfanvalue_off.sh:system/bin/setfanvalue_off.sh \
    device/google/atv/gammaos/rgp2xbox/setrightanalogaxisvalue_default.sh:system/bin/setrightanalogaxisvalue_default.sh \
    device/google/atv/gammaos/rgp2xbox/setrightanalogaxisvalue_swapped.sh:system/bin/setrightanalogaxisvalue_swapped.sh \
    device/google/atv/gammaos/customization.sh:system/bin/customization.sh \
    device/google/atv/gammaos/magisk/magisk.apk:system/etc/magisk.apk \
    device/google/atv/gammaos/retroarch/RetroArch_aarch64.apk:system/etc/RetroArch_aarch64.apk \
    device/google/atv/gammaos/retroarch/retroarch64.tar.gz:system/etc/retroarch64.tar.gz \
    device/google/atv/gammaos/retroarch/retroarch64sdcard.tar.gz:system/etc/retroarch64sdcard.tar.gz \
    device/google/atv/gammaos/retroarch/retroarch64sdcard1-arc.tar.gz:system/etc/retroarch64sdcard1-arc.tar.gz \
    device/google/atv/gammaos/retroarch/retroarch64sdcard2.tar.gz:system/etc/retroarch64sdcard2.tar.gz \
    device/google/atv/gammaos/retroarch/roms.tar.gz:system/etc/roms.tar.gz \
    device/google/atv/gammaos/launcher/projectivylauncher_4.36.apk:system/etc/projectivylauncher_4.36.apk \
    device/google/atv/gammaos/hdmiaudio/silent.mp3:system/etc/silent.mp3 \
    device/google/atv/gammaos/hdmiaudio/hdmiaudio.sh:system/bin/hdmiaudio.sh \
    device/google/atv/gammaos/toast/Toast.apk:system/priv-app/toast/Toast.apk \

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
