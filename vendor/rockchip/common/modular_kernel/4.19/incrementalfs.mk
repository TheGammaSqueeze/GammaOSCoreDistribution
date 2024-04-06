ifneq (,$(filter 4.19, $(PRODUCT_KERNEL_VERSION)))
# Enable Incremental on the device via kernel driver
BOARD_VENDOR_KERNEL_MODULES += \
    vendor/rockchip/common/modular_kernel/4.19/$(TARGET_BUILD_VARIANT)/incrementalfs.ko

PRODUCT_PROPERTY_OVERRIDES += \
    ro.incremental.enable=module:/vendor/lib/modules/incrementalfs.ko

PRODUCT_KERNEL_CONFIG += disable_incfs.config
else
PRODUCT_PROPERTY_OVERRIDES += \
    ro.incremental.enable=yes
endif

PRODUCT_COPY_FILES += \
    vendor/rockchip/common/gms/features/android.software.incremental_delivery.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.software.incremental_delivery.xml