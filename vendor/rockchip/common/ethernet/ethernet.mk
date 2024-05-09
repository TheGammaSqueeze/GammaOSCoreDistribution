ETH_KO_FILES := $(shell find $(TOPDIR)$(PRODUCT_KERNEL_PATH)/drivers/net/ethernet -name "*.ko" -type f)

BOARD_VENDOR_KERNEL_MODULES += \
    $(foreach file, $(ETH_KO_FILES), $(file))
