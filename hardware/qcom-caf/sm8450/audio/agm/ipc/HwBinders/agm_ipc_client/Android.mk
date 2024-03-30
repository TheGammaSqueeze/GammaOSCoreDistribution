LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE := libagmclient
LOCAL_MODULE_OWNER := qti
LOCAL_VENDOR_MODULE := true

ifeq ($(strip $(AUDIO_FEATURE_ENABLED_AGM_HIDL)),true)
LOCAL_CFLAGS += -DAGM_HIDL_ENABLED
endif

LOCAL_SRC_FILES := \
    src/agm_client_wrapper.cpp\
    src/AGMCallback.cpp

LOCAL_SHARED_LIBRARIES := \
    libhidlbase \
    libutils \
    liblog \
    libcutils \
    libhardware \
    libbase \
    vendor.qti.hardware.AGMIPC@1.0

LOCAL_HEADER_LIBRARIES := libagm_headers

LOCAL_CFLAGS += -Wno-format

include $(BUILD_SHARED_LIBRARY)
