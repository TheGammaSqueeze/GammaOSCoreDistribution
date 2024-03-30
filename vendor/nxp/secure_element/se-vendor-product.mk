#
# Copyright (C) 2022 The LineageOS Project
#
# SPDX-License-Identifier: Apache-2.0
#

NXP_VENDOR_PATH := vendor/nxp

BOARD_VENDOR_SEPOLICY_DIRS += \
    $(NXP_VENDOR_PATH)/secure_element/sepolicy

PRODUCT_COPY_FILES += \
    $(NXP_VENDOR_PATH)/secure_element/libese-spi/p73/libese-nxp-P73.conf:$(TARGET_COPY_OUT_VENDOR)/etc/libese-nxp.conf \

PRODUCT_PACKAGES += \
    android.hardware.secure_element@1.2-service.p73

ifneq ($(PRODUCT_USES_ESE),false)
PRODUCT_COPY_FILES += \
    frameworks/native/data/etc/android.hardware.nfc.ese.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.nfc.ese.xml \
    frameworks/native/data/etc/android.hardware.se.omapi.ese.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.se.omapi.ese.xml

PRODUCT_PACKAGES += \
    android.hardware.secure_element@1.2-service.p73.xml
endif
