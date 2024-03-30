LOCAL_PATH := $(call my-dir)

ifneq ($(QCPATH),)
include $(CLEAR_VARS)

LOCAL_MODULE        := vendor.qti.hardware.AGMIPC@1.0-impl
LOCAL_MODULE_OWNER  := qti
LOCAL_VENDOR_MODULE := true

LOCAL_CFLAGS        += -v -Wall
LOCAL_C_INCLUDES    := $(call project-path-for,qcom-audio)/agm/ipc/HwBinders/agm_ipc_client/
LOCAL_SRC_FILES     := src/agm_server_wrapper.cpp

LOCAL_EXPORT_C_INCLUDE_DIRS := $(LOCAL_PATH)/inc

LOCAL_SHARED_LIBRARIES := \
    libhidlbase \
    libutils \
    liblog \
    libcutils \
    libhardware \
    libbase \
    libar-gsl \
    vendor.qti.hardware.AGMIPC@1.0 \
    libagm

include $(BUILD_SHARED_LIBRARY)
endif

ifeq ($(strip $(AUDIO_FEATURE_ENABLED_AGM_HIDL)),true)
  LOCAL_CFLAGS += -DAGM_HIDL_ENABLED
endif

ifneq ($(strip $(AUDIO_FEATURE_ENABLED_AGM_HIDL)),true)
include $(CLEAR_VARS)

LOCAL_MODULE               := vendor.qti.hardware.AGMIPC@1.0-service
LOCAL_INIT_RC              := vendor.qti.hardware.AGMIPC@1.0-service.rc
LOCAL_VENDOR_MODULE        := true
LOCAL_MODULE_RELATIVE_PATH := hw
LOCAL_MODULE_OWNER         := qti

LOCAL_C_INCLUDES           := $(call project-path-for,qcom-audio)/agm/ipc/HwBinders/agm_ipc_client/
LOCAL_SRC_FILES            := src/service.cpp

LOCAL_SHARED_LIBRARIES := \
    liblog \
    libcutils \
    libdl \
    libbase \
    libutils \
    libhardware \
    libhidlbase \
    vendor.qti.hardware.AGMIPC@1.0 \
    vendor.qti.hardware.AGMIPC@1.0-impl \
    libagm

LOCAL_HEADER_LIBRARIES := libagm_headers

include $(BUILD_EXECUTABLE)
endif
