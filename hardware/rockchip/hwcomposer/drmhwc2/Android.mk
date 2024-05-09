#
# Copyright (C) 2018 Fuzhou Rockchip Electronics Co.Ltd.
#
# Modification based on code covered by the Apache License, Version 2.0 (the "License").
# You may not use this software except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS TO YOU ON AN "AS IS" BASIS
# AND ANY AND ALL WARRANTIES AND REPRESENTATIONS WITH RESPECT TO SUCH SOFTWARE, WHETHER EXPRESS,
# IMPLIED, STATUTORY OR OTHERWISE, INCLUDING WITHOUT LIMITATION, ANY IMPLIED WARRANTIES OF TITLE,
# NON-INFRINGEMENT, MERCHANTABILITY, SATISFACTROY QUALITY, ACCURACY OR FITNESS FOR A PARTICULAR
# PURPOSE ARE DISCLAIMED.
#
# IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
# SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
# GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
# THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
# ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
#
# Copyright (C) 2015 The Android Open Source Project
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

BOARD_USES_DRM_HWCOMPOSER2=false
BOARD_USES_DRM_HWCOMPOSER=false
# rk356x rk3588 rk3528 rk3562 use DrmHwc2
ifneq ($(filter rk356x rk3588 rk3528 rk3562, $(strip $(TARGET_BOARD_PLATFORM))), )
ifeq ($(strip $(BUILD_WITH_RK_EBOOK)),true)
        BOARD_USES_DRM_HWCOMPOSER2=false
else  # BUILD_WITH_RK_EBOOK
        BOARD_USES_DRM_HWCOMPOSER2=true
endif # BUILD_WITH_RK_EBOOK
else
        BOARD_USES_DRM_HWCOMPOSER2=false
endif

ifeq ($(strip $(BOARD_USES_DRM_HWCOMPOSER2)),true)

include $(CLEAR_VARS)

LOCAL_SHARED_LIBRARIES := \
  libcutils \
  libdrm \
  libhardware \
  liblog \
  libui \
  libutils \
  libsync_vendor \
  libtinyxml2 \
  libbaseparameter \
  librga

LOCAL_STATIC_LIBRARIES := \
  libdrmhwcutils

LOCAL_C_INCLUDES := \
  ${LOCAL_PATH}/include \
  external/libdrm \
  external/libdrm/include/drm \
  system/core \
  system/core/libsync/include \
  external/tinyxml2 \
  hardware/rockchip/libbaseparameter \
  hardware/rockchip/librga/include \
  hardware/rockchip/librga/im2d_api


LOCAL_SRC_FILES := \
  drmhwctwo.cpp \
  drm/drmconnector.cpp \
  drm/drmcrtc.cpp \
  drm/drmdevice.cpp \
  drm/drmencoder.cpp \
  drm/drmeventlistener.cpp \
  drm/drmmode.cpp \
  drm/drmplane.cpp \
  drm/drmproperty.cpp \
  drm/drmcompositorworker.cpp \
  resources/resourcemanager.cpp \
  resources/resourcescache.cpp \
  drm/vsyncworker.cpp \
  drm/invalidateworker.cpp \
  utils/autolock.cpp \
  rockchip/compositor/drmdisplaycomposition.cpp \
  rockchip/compositor/drmdisplaycompositor.cpp \
  rockchip/utils/drmdebug.cpp \
  rockchip/common/drmfence.cpp \
  rockchip/common/drmlayer.cpp \
  rockchip/common/drmtype.cpp \
  rockchip/common/drmgralloc.cpp \
  rockchip/common/drmbaseparameter.cpp \
  rockchip/platform/common/platformdrmgeneric.cpp \
  rockchip/platform/common/platform.cpp \
  rockchip/platform/rk3399/drmvop3399.cpp \
  rockchip/platform/rk356x/drmvop356x.cpp \
  rockchip/platform/rk3588/drmvop3588.cpp \
  rockchip/platform/rk3528/drmvop3528.cpp \
  rockchip/platform/rk3562/drmvop3562.cpp \
  rockchip/platform/rk3399/drmhwc3399.cpp \
  rockchip/platform/rk356x/drmhwc356x.cpp \
  rockchip/platform/rk3588/drmhwc3588.cpp \
  rockchip/platform/rk3528/drmhwc3528.cpp \
  rockchip/platform/rk3562/drmhwc3562.cpp \
  rockchip/common/drmbufferqueue.cpp \
  rockchip/common/drmbuffer.cpp \
  rockchip/common/hdr/drmhdrparser.cpp \
  rockchip/producer/drmvideoproducer.cpp \
  rockchip/producer/vpcontext.cpp


LOCAL_CPPFLAGS += \
  -DHWC2_USE_CPP11 \
  -DHWC2_INCLUDE_STRINGIFICATION \
  -DRK_DRM_GRALLOC \
  -DUSE_HWC2 \
  -DMALI_AFBC_GRALLOC \
  -Wno-unreachable-code-loop-increment \
  -DUSE_NO_ASPECT_RATIO \
  -fPIC

ifneq (1,$(strip $(shell expr $(PLATFORM_SDK_VERSION) \< 31)))
LOCAL_CFLAGS += -DANDROID_S
LOCAL_HEADER_LIBRARIES += \
  libhardware_rockchip_headers
endif


# API 30 -> Android 11.0
ifneq (1,$(strip $(shell expr $(PLATFORM_SDK_VERSION) \< 30)))
LOCAL_C_INCLUDES += \
    hardware/rockchip/hwcomposer/drmhwc2/include
LOCAL_CPPFLAGS += -DANDROID_R

# Gralloc config:
ifeq ($(TARGET_RK_GRALLOC_VERSION),4) # Gralloc 4.0
LOCAL_CPPFLAGS += -DUSE_GRALLOC_4=1
LOCAL_SHARED_LIBRARIES += \
    libhidlbase \
    libgralloctypes \
    android.hardware.graphics.mapper@4.0
LOCAL_SRC_FILES += \
    rockchip/common/drmgralloc4.cpp
LOCAL_HEADER_LIBRARIES += \
    libgralloc_headers
else
  LOCAL_CPPFLAGS += -DUSE_GRALLOC_0=1
endif # Gralloc 4.0

else  # Android 11
LOCAL_C_INCLUDES += \
  hardware/rockchip/hwcomposer/include
endif


# Mali config:
# API 29 -> Android 10.0
ifneq (1,$(strip $(shell expr $(PLATFORM_SDK_VERSION) \< 29)))
LOCAL_CPPFLAGS += -DANDROID_Q
ifneq (,$(filter mali-tDVx mali-G52, $(TARGET_BOARD_PLATFORM_GPU)))
LOCAL_C_INCLUDES += \
  hardware/rockchip/libgralloc/bifrost \
  hardware/rockchip/libgralloc/bifrost/src
endif

ifneq (,$(filter mali-t860 mali-t760, $(TARGET_BOARD_PLATFORM_GPU)))
LOCAL_C_INCLUDES += \
  hardware/rockchip/libgralloc/midgard
endif

ifneq (,$(filter mali400 mali450, $(TARGET_BOARD_PLATFORM_GPU)))
LOCAL_C_INCLUDES += \
  hardware/rockchip/libgralloc/utgard
endif

ifeq ($(strip $(TARGET_BOARD_PLATFORM)),rk3368)
LOCAL_C_INCLUDES += \
  system/core/libion/original-kernel-headers
endif
endif

# RK3528 config:
ifneq ($(filter rk3528, $(strip $(TARGET_BOARD_PLATFORM))),)
LOCAL_CPPFLAGS += -DRK3528=1
USE_HDR_PARSER=true
# Android 启用 HDR 功能
LOCAL_SHARED_LIBRARIES += \
	libhdr_params_parser
LOCAL_CPPFLAGS += \
	-DUSE_HDR_PARSER=1
# API 28/29 -> Android 9.0
ifeq (0,$(strip $(shell expr $(PLATFORM_SDK_VERSION) \> 29)))
LOCAL_CPPFLAGS += -DANDROID_P=1
LOCAL_C_INCLUDES += \
  hardware/rockchip/libgralloc/ \
  system/core/liblog/include/
endif
endif

# SR
# BOARD_USES_LIBSVEP=true
ifeq ($(strip $(BOARD_USES_LIBSVEP)),true)
BOARD_USES_LIBSR=true
endif

ifeq ($(strip $(BOARD_USES_LIBSR)),true)
# in order to adapter old version，need include two dir.
LOCAL_C_INCLUDES += \
  hardware/rockchip/libsvep/include \
  hardware/rockchip/libsvep/include/sr

LOCAL_SHARED_LIBRARIES += \
	libsvepsr \
	librknnrt-svep \
	libOpenCL

LOCAL_CFLAGS += \
	-DUSE_LIBSR=1

LOCAL_REQUIRED_MODULES += \
	HwcSvepEnv.xml
endif

# MEMC
# BOARD_USES_LIBSVEP_MEMC=true
ifeq ($(strip $(BOARD_USES_LIBSVEP_MEMC)),true)
LOCAL_C_INCLUDES += \
  hardware/rockchip/libsvep/include/memc

LOCAL_SHARED_LIBRARIES += \
	libsvepmemc \
	libOpenCL

LOCAL_CFLAGS += \
	-DUSE_LIBSVEP_MEMC=1

LOCAL_REQUIRED_MODULES += \
	HwcSvepMemcEnv.xml
endif

# BOARD_USES_LIBPQ=true
ifeq ($(strip $(BOARD_USES_LIBPQ)),true)
LOCAL_C_INCLUDES += \
  hardware/rockchip/libpq/include

LOCAL_SHARED_LIBRARIES += \
	libpq

LOCAL_CFLAGS += \
	-DUSE_LIBPQ=1
endif

# GKI compile is true
# BOARD_BUILD_GKI=true
ifeq ($(strip $(BOARD_BUILD_GKI)),true)
LOCAL_CFLAGS += \
	-DBOARD_BUILD_GKI=1
endif

# LOCAL_SANITIZE:=address

LOCAL_MODULE := hwcomposer.$(TARGET_BOARD_HARDWARE)
LOCAL_REQUIRED_MODULES += \
	HwComposerEnv.xml

# API 26 -> Android 8.0
ifeq (1,$(strip $(shell expr $(PLATFORM_SDK_VERSION) \>= 26)))
LOCAL_PROPRIETARY_MODULE := true
endif

LOCAL_MODULE_TAGS := optional
LOCAL_CFLAGS += \
  -Wno-unused-function \
  -Wno-unused-private-field \
  -Wno-unused-function \
  -Wno-unused-variable \
  -Wno-unused-parameter \
  -fPIC \
  -Wno-sign-compare

LOCAL_MODULE_RELATIVE_PATH := hw
LOCAL_MODULE_CLASS := SHARED_LIBRARIES
LOCAL_MODULE_SUFFIX := $(TARGET_SHLIB_SUFFIX)
include $(BUILD_SHARED_LIBRARY)

## copy configs/*.xml from etc to /vendor/etc/init/hw
include $(CLEAR_VARS)
LOCAL_MODULE := HwComposerEnv.xml
LOCAL_PROPRIETARY_MODULE := true
LOCAL_MODULE_CLASS := ETC
LOCAL_SRC_FILES := configs/HwComposerEnv.xml
include $(BUILD_PREBUILT)

ifeq ($(strip $(BOARD_USES_LIBSVEP)),true)
## copy configs/*.xml from etc to /vendor/etc/init/hw
include $(CLEAR_VARS)
LOCAL_MODULE := HwcSvepEnv.xml
LOCAL_PROPRIETARY_MODULE := true
LOCAL_MODULE_CLASS := ETC
LOCAL_SRC_FILES := configs/HwcSvepEnv.xml
include $(BUILD_PREBUILT)
endif


ifeq ($(strip $(BOARD_USES_LIBSVEP_MEMC)),true)
## copy configs/*.xml from etc to /vendor/etc/init/hw
include $(CLEAR_VARS)
LOCAL_MODULE := HwcSvepMemcEnv.xml
LOCAL_PROPRIETARY_MODULE := true
LOCAL_MODULE_CLASS := ETC
LOCAL_SRC_FILES := configs/HwcSvepMemcEnv.xml
include $(BUILD_PREBUILT)
endif

ifeq ($(strip $(USE_HDR_PARSER)),true)
# libhdr_params_parser
TARGET_VIVID_HDR_PARSER_LIB_PATH := rockchip/common/hdr/vivid
include $(CLEAR_VARS)
LOCAL_MODULE := libhdr_params_parser
LOCAL_MODULE_TAGS := optional
LOCAL_MODULE_CLASS := SHARED_LIBRARIES
LOCAL_MODULE_STEM := $(LOCAL_MODULE)
LOCAL_MODULE_SUFFIX := .so
LOCAL_VENDOR_MODULE := true
LOCAL_PROPRIETARY_MODULE := true
ifneq ($(strip $(TARGET_2ND_ARCH)), )
LOCAL_MULTILIB := both
LOCAL_SRC_FILES_$(TARGET_ARCH) := $(TARGET_VIVID_HDR_PARSER_LIB_PATH)/$(TARGET_ARCH)/libhdr_params_parser.so
LOCAL_SRC_FILES_$(TARGET_2ND_ARCH) := $(TARGET_VIVID_HDR_PARSER_LIB_PATH)/$(TARGET_2ND_ARCH)/libhdr_params_parser.so
else
LOCAL_SRC_FILES_$(TARGET_ARCH) := $(TARGET_VIVID_HDR_PARSER_LIB_PATH)/$(TARGET_ARCH)/libhdr_params_parser.so
endif
include $(BUILD_PREBUILT)
endif # USE_HDR_PARSER

endif # HWC2

ifeq ($(strip $(BOARD_USES_DRM_HWCOMPOSER2)),true)
include $(call all-makefiles-under,$(LOCAL_PATH))
endif
