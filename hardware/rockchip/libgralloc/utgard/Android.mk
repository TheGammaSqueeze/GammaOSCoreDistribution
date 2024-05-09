# 
# Copyright (C) 2010 ARM Limited. All rights reserved.
# 
# Copyright (C) 2008 The Android Open Source Project
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

# HAL module implemenation, not prelinked and stored in
# hw/<OVERLAY_HARDWARE_MODULE_ID>.<ro.product.board>.so
include $(CLEAR_VARS)
LOCAL_PRELINK_MODULE := false

LOCAL_PROPRIETARY_MODULE := true

LOCAL_MODULE_RELATIVE_PATH := hw

MALI_DDK_TEST_PATH := hardware/arm/

LOCAL_MODULE := gralloc.$(TARGET_BOARD_HARDWARE)
#LOCAL_MODULE_TAGS := optional

LOCAL_HEADER_LIBRARIES += \
	libhardware_rockchip_headers

# Mali-200/300/400MP DDK
SHARED_MEM_LIBS := \
	libion \
	libhardware \
	libdmabufheap

LOCAL_SHARED_LIBRARIES := liblog libcutils libGLESv1_CM $(SHARED_MEM_LIBS)

LOCAL_C_INCLUDES := system/core/include/

LOCAL_CFLAGS := -DLOG_TAG=\"gralloc\"

MAJOR_VERSION := "RK_GRAPHICS_VER=commit-id:$(shell cd $(LOCAL_PATH) && git log  -1 --oneline | awk '{print $$1}')"
LOCAL_CPPFLAGS += -DRK_GRAPHICS_VER=\"$(MAJOR_VERSION)\"

LOCAL_SRC_FILES := \
	gralloc_module.cpp \
	alloc_device.cpp \
	core/formats.cpp \
	core/format_info.cpp \
	core/buffer_allocation.cpp \
	allocator/dmabufheap/dmabufheap.cpp \
	allocator/shared_memory/shared_memory.cpp

LOCAL_CPP_STD := c++17

include $(BUILD_SHARED_LIBRARY)
