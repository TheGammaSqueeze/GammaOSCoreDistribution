LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE := vendor.qti.hardware.pal@1.0-impl
LOCAL_MODULE_OWNER := qti
LOCAL_VENDOR_MODULE := true
LOCAL_CFLAGS += -v
LOCAL_CFLAGS += \
    -Wno-unused-parameter \
    -Wno-unused-variable \
    -Wno-format \
    -Wno-missing-field-initializers \
    -Wno-unused-function

LOCAL_SRC_FILES := \
    src/pal_server_wrapper.cpp

LOCAL_EXPORT_C_INCLUDE_DIRS := $(LOCAL_PATH)/inc

ifeq ($(QCPATH),)
LOCAL_C_INCLUDES := \
    $(LOCAL_PATH)/../../..
endif

LOCAL_SHARED_LIBRARIES := \
    libhidlbase \
    libhidltransport \
    libutils \
    liblog \
    libcutils \
    libhardware \
    libbase \
    vendor.qti.hardware.pal@1.0 \
    libar-pal

include $(BUILD_SHARED_LIBRARY)

