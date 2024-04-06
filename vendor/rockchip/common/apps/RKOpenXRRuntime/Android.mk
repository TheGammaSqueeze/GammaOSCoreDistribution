LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE := RKOpenXRRuntime
LOCAL_MODULE_CLASS := APPS
LOCAL_MODULE_TAGS := optional
LOCAL_BUILT_MODULE_STEM := package.apk
LOCAL_MODULE_SUFFIX := $(COMMON_ANDROID_PACKAGE_SUFFIX)
LOCAL_PRIVILEGED_MODULE := true
LOCAL_CERTIFICATE := platform
LOCAL_SRC_FILES := $(LOCAL_MODULE).apk
ifeq ($(strip $(TARGET_ARCH)), arm)
    LOCAL_PREBUILT_JNI_LIBS := \
        lib/arm/libc++_shared.so \
        lib/arm/libopenxr_monado.openxr.so
else ifeq ($(strip $(TARGET_ARCH)), arm64)
    LOCAL_PREBUILT_JNI_LIBS := \
        lib/arm64/libc++_shared.so \
        lib/arm64/libopenxr_monado.openxr.so
endif
LOCAL_SHARED_LIBRARIES := \
    openxr_monado.openxr
include $(BUILD_PREBUILT)
