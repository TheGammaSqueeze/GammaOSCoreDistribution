# Copyright (C) 2011 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

LOCAL_PATH := $(call my-dir)

# Make the HAL library
# ============================================================
include $(CLEAR_VARS)

LOCAL_C_INCLUDES += \
    external/libnl/include \
    $(call include-path-for, libhardware_legacy)/hardware_legacy \
    external/wpa_supplicant_8/src/drivers

LOCAL_HEADER_LIBRARIES := libutils_headers liblog_headers
LOCAL_SRC_FILES := wifi_hal.cpp

LOCAL_MODULE := libwifi-hal-auto
LOCAL_PROPRIETARY_MODULE := true

include $(BUILD_STATIC_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE := libwifi-hal-package
LOCAL_MODULE_OWNER := google
LOCAL_MODULE_TAGS := optional
LOCAL_REQUIRED_MODULES := \
    libwifi-hal-bcm \
    libwifi-hal-rtk \
    libwifi-hal-bes \
    libwifi-hal-aic
ifeq ($(strip $(TARGET_BOARD_PLATFORM_PRODUCT)), car)
LOCAL_REQUIRED_MODULES += libwifi-hal-qcom
endif
include $(BUILD_PHONY_PACKAGE)
