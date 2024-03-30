PRODUCT_COPY_FILES += \
      hardware/google/pixel/common/init.pixel.rc:$(TARGET_COPY_OUT_VENDOR)/etc/init/init.pixel.rc

BOARD_VENDOR_SEPOLICY_DIRS += hardware/google/pixel-sepolicy/common/vendor
SYSTEM_EXT_PRIVATE_SEPOLICY_DIRS += hardware/google/pixel-sepolicy/common/system_ext

# Write flags to the vendor space in /misc partition.
PRODUCT_PACKAGES += \
    misc_writer

# Enable atrace hal and tools for pixel devices
PRODUCT_PACKAGES += \
    android.hardware.atrace@1.0-service.pixel \
    dmabuf_dump

# fastbootd
PRODUCT_PACKAGES += \
    fastbootd

# Common ramdump file type.
BOARD_VENDOR_SEPOLICY_DIRS += hardware/google/pixel-sepolicy/ramdump/common

# Pixel Experience

ifneq (,$(filter userdebug eng, $(TARGET_BUILD_VARIANT)))
ifeq (,$(filter aosp_%,$(TARGET_PRODUCT)))
PRODUCT_PACKAGES_DEBUG += wifi_diagnostic
BOARD_VENDOR_SEPOLICY_DIRS += hardware/google/pixel-sepolicy/wifi_diagnostic
endif
endif

PRODUCT_PACKAGES_DEBUG += wifi_sniffer
BOARD_VENDOR_SEPOLICY_DIRS += hardware/google/pixel-sepolicy/wifi_sniffer

PRODUCT_PACKAGES_DEBUG += wifi_perf_diag
BOARD_VENDOR_SEPOLICY_DIRS += hardware/google/pixel-sepolicy/wifi_perf_diag

# Enable whole-program R8 Java optimizations for SystemUI and system_server,
# but also allow explicit overriding for testing and development.
SYSTEM_OPTIMIZE_JAVA ?= true
SYSTEMUI_OPTIMIZE_JAVA ?= true
