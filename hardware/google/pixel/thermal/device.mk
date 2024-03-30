# Thermal HAL
SOONG_CONFIG_NAMESPACES += thermal_hal_feature
SOONG_CONFIG_thermal_hal_feature += \
    pid \

SOONG_CONFIG_thermal_hal_feature_pid ?= apply_1_0

PRODUCT_PACKAGES += \
    android.hardware.thermal@2.0-service.pixel

# Thermal utils
PRODUCT_PACKAGES += \
    thermal_symlinks

ifneq (,$(filter eng, $(TARGET_BUILD_VARIANT)))
PRODUCT_PACKAGES += \
    thermal_logd
endif

BOARD_SEPOLICY_DIRS += hardware/google/pixel-sepolicy/thermal
