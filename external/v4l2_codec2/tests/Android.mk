ifneq ($(filter external/v4l2_codec2,$(PRODUCT_SOONG_NAMESPACES)),)
include $(call all-subdir-makefiles)
endif