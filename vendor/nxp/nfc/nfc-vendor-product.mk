#
# Copyright (C) 2022 The LineageOS Project
#
# SPDX-License-Identifier: Apache-2.0
#

PRODUCT_SOONG_NAMESPACES += \
    vendor/nxp/nfc \
    vendor/nxp/secure_element

NXP_NFC_HARDWARE := PN81T
NXP_VENDOR_PATH := vendor/nxp

BOARD_VENDOR_SEPOLICY_DIRS += \
    $(NXP_VENDOR_PATH)/nfc/sepolicy

PRODUCT_COPY_FILES += \
    $(NXP_VENDOR_PATH)/nfc/halimpl/libnfc-nxp-$(NXP_NFC_HARDWARE)_example.conf:$(TARGET_COPY_OUT_VENDOR)/etc/libnfc-nxp.conf \
    $(NXP_VENDOR_PATH)/nfc/halimpl/libnfc-nxp_RF-$(NXP_NFC_HARDWARE)_example.conf:$(TARGET_COPY_OUT_VENDOR)/etc/libnfc-nxp_RF.conf \
    $(NXP_VENDOR_PATH)/nfc/vendor.nxp.nfc.rc:$(TARGET_COPY_OUT_VENDOR)/etc/init/vendor.nxp.nfc.rc \

PRODUCT_COPY_FILES += \
    frameworks/native/data/etc/android.hardware.nfc.hce.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.nfc.hce.xml \
    frameworks/native/data/etc/android.hardware.nfc.hcef.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.nfc.hcef.xml \
    frameworks/native/data/etc/android.hardware.nfc.uicc.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.nfc.uicc.xml \
    frameworks/native/data/etc/android.hardware.nfc.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.nfc.xml \
    frameworks/native/data/etc/android.hardware.se.omapi.uicc.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.se.omapi.uicc.xml \
    frameworks/native/data/etc/com.android.nfc_extras.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/com.android.nfc_extras.xml \
    frameworks/native/data/etc/com.nxp.mifare.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/com.nxp.mifare.xml

PRODUCT_PACKAGES += \
    android.hardware.nfc@1.2-service.pn8x
