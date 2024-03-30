LOCAL_PATH:= $(call my-dir)

LOCAL_CFLAGS += -Wall -Werror

#--------------------------------------------
#          Build bt_bundle LIB
#--------------------------------------------
include $(CLEAR_VARS)

LOCAL_SRC_FILES := \
    bt_base.c \
    bt_bundle.c

LOCAL_CFLAGS += -O2 -fvisibility=hidden

LOCAL_SHARED_LIBRARIES := \
    libcutils \
    liblog \
    libdl

LOCAL_C_INCLUDES += $(TOP)/system/media/audio/include

LOCAL_HEADER_LIBRARIES := \
    libspf-headers \
    libarosal_headers

LOCAL_MODULE_TAGS := optional
LOCAL_MODULE := lib_bt_bundle
LOCAL_MODULE_OWNER := qti
LOCAL_VENDOR_MODULE := true
include $(BUILD_SHARED_LIBRARY)

#--------------------------------------------
#          Build bt_aptx LIB
#--------------------------------------------
include $(CLEAR_VARS)

LOCAL_SRC_FILES := \
    bt_base.c \
    bt_aptx.c

LOCAL_CFLAGS += -O2 -fvisibility=hidden

LOCAL_SHARED_LIBRARIES := \
    libcutils \
    liblog \
    libdl

LOCAL_C_INCLUDES += $(TOP)/system/media/audio/include

LOCAL_HEADER_LIBRARIES := \
    libspf-headers \
    libarosal_headers

LOCAL_MODULE_TAGS := optional
LOCAL_MODULE := lib_bt_aptx
LOCAL_MODULE_OWNER := qti
LOCAL_VENDOR_MODULE := true
include $(BUILD_SHARED_LIBRARY)

#--------------------------------------------
#          Build bt_ble LIB
#--------------------------------------------
include $(CLEAR_VARS)

LOCAL_SRC_FILES := \
    bt_base.c \
    bt_ble.c

LOCAL_CFLAGS += -O2 -fvisibility=hidden

LOCAL_SHARED_LIBRARIES := \
    libcutils \
    liblog \
    libdl

LOCAL_C_INCLUDES += $(TOP)/system/media/audio/include

LOCAL_HEADER_LIBRARIES := \
    libspf-headers \
    libarosal_headers

LOCAL_MODULE_TAGS := optional
LOCAL_MODULE := lib_bt_ble
LOCAL_MODULE_OWNER := qti
LOCAL_VENDOR_MODULE := true
include $(BUILD_SHARED_LIBRARY)
