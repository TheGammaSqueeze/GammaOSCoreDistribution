ifeq ($(strip $(TARGET_BOARD_PLATFORM_GPU)), mali-G52)

# libs of libGLES_mali.so are installed in ./Android.mk
PRODUCT_PACKAGES += \
        libGLES_mali \
        libgpudataproducer \
        vulkan.$(TARGET_BOARD_PLATFORM)

ifeq ($(strip $(BOARD_BUILD_GKI)), true)
BOARD_VENDOR_KERNEL_MODULES += \
	vendor/rockchip/common/gpu/MaliG52/lib/modules/bifrost_kbase.ko
endif
endif
