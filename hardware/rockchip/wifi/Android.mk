ifeq ($(BOARD_WLAN_DEVICE), auto)
	include $(call all-subdir-makefiles)
endif
