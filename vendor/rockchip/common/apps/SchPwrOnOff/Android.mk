LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := optional
LOCAL_SRC_FILES := $(call all-subdir-java-files)
LOCAL_PACKAGE_NAME := SchPwrOnOff
LOCAL_CERTIFICATE := platform
LOCAL_MODULE_PATH   := $(PRODUCT_OUT)/system_ext/priv-app
LOCAL_PROGUARD_ENABLED := disabled
LOCAL_PRIVILEGED_MODULE := true

LOCAL_PRIVATE_PLATFORM_APIS := true

include $(BUILD_PACKAGE)
