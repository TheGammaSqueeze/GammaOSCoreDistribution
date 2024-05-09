HAVE_EXT_WIFI_KO_FILE := $(shell test -d $(TOPDIR)external/wifi_driver/ && echo yes)
ifeq ($(HAVE_EXT_WIFI_KO_FILE),yes)
EXT_WIFI_KO_FILES := $(shell find $(TOPDIR)external/wifi_driver -name "*.ko" -type f)
BOARD_VENDOR_KERNEL_MODULES += \
        $(foreach file, $(EXT_WIFI_KO_FILES), $(file))

# priority to use external/wifi_driver, delete the same ko in kernel wifi driver
EXT_WIFI_DRIVER := $(shell find $(TOPDIR)external/wifi_driver -name "*.ko" -type f | awk -F "wifi_driver/" '{print $$2}' | awk -F "/" '{print $$1}')
$(shell for line in $(EXT_WIFI_DRIVER); do rm $(TOPDIR)$(PRODUCT_KERNEL_PATH)/drivers/net/wireless/rockchip_wlan/$$line/*.ko > /dev/null 2>&1; done)

$(shell for line in $(EXT_WIFI_DRIVER); do rm $(TOPDIR)$(PRODUCT_KERNEL_PATH)/drivers/net/wireless/rockchip_wlan/rkwifi/$$line/*.ko > /dev/null 2>&1; done)

endif

WIFI_KO_FILES := $(shell find $(TOPDIR)$(PRODUCT_KERNEL_PATH)/drivers/net/wireless/rockchip_wlan -name "*.ko" -type f)
BOARD_VENDOR_KERNEL_MODULES += \
	$(foreach file, $(WIFI_KO_FILES), $(file))

WifiFirmwareFile := $(shell ls $(CUR_PATH)/wifi/firmware)
PRODUCT_COPY_FILES += \
        $(foreach file, $(WifiFirmwareFile), $(CUR_PATH)/wifi/firmware/$(file):$(TARGET_COPY_OUT_VENDOR)/etc/firmware/$(file))
