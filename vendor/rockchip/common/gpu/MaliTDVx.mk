ifeq ($(strip $(TARGET_BOARD_PLATFORM_GPU)), mali-tDVx)

# libs of libGLES_mali.so are installed in ./Android.mk
PRODUCT_PACKAGES += \
    libGLES_mali \
    vulkan.$(TARGET_BOARD_PLATFORM)

ifeq ($(strip $(BOARD_BUILD_GKI)), true)
BOARD_VENDOR_KERNEL_MODULES += \
	vendor/rockchip/common/gpu/MaliTDVx/lib/modules/mali_kbase.ko
endif
endif
