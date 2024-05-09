PRODUCT_PACKAGES += \
        libpq \
        rkaipq820_sd_model_rknn140.rknn \
        rkaipq820_sr_model0_rknn140.rknn \
        rkaipq820_sr_model1_rknn140.rknn \
        pq_init

# SELinux配置
BOARD_SEPOLICY_DIRS += hardware/rockchip/libpq/pq_init/sepolicy
