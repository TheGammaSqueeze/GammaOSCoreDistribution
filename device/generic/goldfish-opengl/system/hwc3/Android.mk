#
# Copyright 2022 The Android Open-Source Project
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
#

LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE := android.hardware.graphics.composer3-service.ranchu

LOCAL_LICENSE_KINDS := SPDX-license-identifier-Apache-2.0
LOCAL_LICENSE_CONDITIONS := notice
LOCAL_NOTICE_FILE := $(LOCAL_PATH)/../../LICENSE

LOCAL_MODULE_TAGS := optional
LOCAL_MODULE_RELATIVE_PATH := hw
LOCAL_VENDOR_MODULE := true

LOCAL_SHARED_LIBRARIES := \
    android.hardware.graphics.composer@2.1-resources \
    android.hardware.graphics.composer@2.2-resources \
    android.hardware.graphics.composer3-V1-ndk \
    android.hardware.graphics.mapper@2.0 \
    android.hardware.graphics.mapper@4.0 \
    libbase \
    libbinder \
    libbinder_ndk \
    libEGL \
    libcutils \
    libcuttlefish_device_config \
    libcuttlefish_device_config_proto \
    libcuttlefish_utils \
    libcuttlefish_fs \
    libdrm \
    libgralloctypes \
    libhardware \
    libhidlbase \
    libjsoncpp \
    libjpeg \
    liblog \
    libsync \
    libui \
    libutils \
    libutils \
    libOpenglSystemCommon \
    lib_renderControl_enc \
    libui

LOCAL_STATIC_LIBRARIES := \
    libaidlcommonsupport \
    libyuv_static

LOCAL_C_INCLUDES := \
    device/generic/goldfish-opengl/host/include/libOpenglRender \
    device/generic/goldfish-opengl/android-emu \
    device/generic/goldfish-opengl/shared/OpenglCodecCommon \
    device/generic/goldfish-opengl/system/OpenglSystemCommon \
    device/generic/goldfish-opengl/system/include \
    device/generic/goldfish-opengl/system/renderControl_enc \
    external/libdrm \
    external/minigbm/cros_gralloc \
    system/core/libsync \
    system/core/libsync/include \

LOCAL_SRC_FILES := \
    ClientFrameComposer.cpp \
    Common.cpp \
    Composer.cpp \
    ComposerClient.cpp \
    ComposerResources.cpp \
    Device.cpp \
    Display.cpp \
    DisplayConfig.cpp \
    DisplayFinder.cpp \
    Drm.cpp \
    DrmPresenter.cpp \
    Gralloc.cpp \
    GuestFrameComposer.cpp \
    HostFrameComposer.cpp \
    HostUtils.cpp \
    Layer.cpp \
    Main.cpp \
    NoOpFrameComposer.cpp \
    VsyncThread.cpp \

LOCAL_VINTF_FRAGMENTS := hwc3.xml
LOCAL_INIT_RC := hwc3.rc

include $(BUILD_EXECUTABLE)

