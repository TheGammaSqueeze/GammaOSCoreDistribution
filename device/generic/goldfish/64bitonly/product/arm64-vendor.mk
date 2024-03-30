include device/generic/goldfish/arm64-kernel.mk

PRODUCT_PROPERTY_OVERRIDES += \
       vendor.rild.libpath=/vendor/lib64/libgoldfish-ril.so

PRODUCT_PROPERTY_OVERRIDES += \
    ro.crypto.dm_default_key.options_format.version=2

PRODUCT_SHIPPING_API_LEVEL := 33
PRODUCT_OTA_ENFORCE_VINTF_KERNEL_REQUIREMENTS := false
TARGET_USES_MKE2FS := true

# Note: the following lines need to stay at the beginning so that it can
# take priority  and override the rules it inherit from other mk files
# see copy file rules in core/Makefile
PRODUCT_COPY_FILES += \
    device/generic/goldfish/fstab.ranchu.arm:$(TARGET_COPY_OUT_VENDOR)/etc/fstab.ranchu \
    device/generic/goldfish/fstab.ranchu.arm:$(TARGET_COPY_OUT_VENDOR_RAMDISK)/first_stage_ramdisk/fstab.ranchu \
    device/generic/goldfish/data/etc/encryptionkey.img:encryptionkey.img \
    device/generic/goldfish/task_profiles.json:$(TARGET_COPY_OUT_VENDOR)/etc/task_profiles.json \
    $(EMULATOR_KERNEL_FILE):kernel-ranchu \
    device/generic/goldfish/data/etc/advancedFeatures.ini.arm:advancedFeatures.ini \
    device/generic/goldfish/camera/media_codecs_performance_c2_arm64.xml:$(TARGET_COPY_OUT_VENDOR)/etc/media_codecs_performance_c2.xml \

EMULATOR_VENDOR_NO_GNSS := true
