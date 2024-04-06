ifeq ($(strip $(TARGET_BOARD_PLATFORM_GPU)), mali-G610)
# libs of libGLES_mali.so are installed in ./Android.mk
PRODUCT_PACKAGES += \
        libGLES_mali \
        vulkan.$(TARGET_BOARD_PLATFORM) \
        libgpudataproducer

PRODUCT_COPY_FILES += \
	vendor/rockchip/common/gpu/MaliG610/firmware/mali_csffw.bin:$(TARGET_COPY_OUT_VENDOR)/etc/firmware/mali_csffw.bin

ifeq ($(strip $(BOARD_BUILD_GKI)), true)
BOARD_VENDOR_KERNEL_MODULES += \
        vendor/rockchip/common/gpu/MaliG610/lib/modules/bifrost_kbase.ko
endif
endif
