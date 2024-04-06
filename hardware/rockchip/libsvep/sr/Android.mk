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
ifeq ($(strip $(BOARD_USES_LIBSVEP)),true)

TARGET_ANDROID_VERSION := 12.0
TARGET_SOC_PLATFORM := rk3588

# API 31 / 32 -> Android 12.0
ifeq (1,$(strip $(shell expr `expr $(PLATFORM_SDK_VERSION) \= 32)` \| `expr $(PLATFORM_SDK_VERSION) \= 31)`))
TARGET_ANDROID_VERSION := 12.0
endif

# API 28 -> Android 9.0
ifeq (1,$(strip $(shell expr $(PLATFORM_SDK_VERSION) \= 28)))
TARGET_ANDROID_VERSION := 9.0
endif


# RK3588
ifneq ($(filter rk3588, $(strip $(TARGET_BOARD_PLATFORM))), )
TARGET_SOC_PLATFORM := rk3588
endif

# RK356x
ifneq ($(filter rk356x, $(strip $(TARGET_BOARD_PLATFORM))), )
TARGET_SOC_PLATFORM := rk356x
endif
# SVEP lib
TARGET_SVEP_LIB_PATH := lib/$(TARGET_SOC_PLATFORM)/$(TARGET_ANDROID_VERSION)

include $(CLEAR_VARS)
LOCAL_MODULE := librknnrt-svep
LOCAL_MODULE_TAGS := optional
LOCAL_MODULE_CLASS := SHARED_LIBRARIES
LOCAL_MODULE_SUFFIX := .so
##LOCAL_VENDOR_MODULE := true
LOCAL_VENDOR_MODULE := true
LOCAL_PROPRIETARY_MODULE := true
LOCAL_SHARED_LIBRARIES := libc libdl liblog libm libstdc++ libz
ifneq ($(strip $(TARGET_2ND_ARCH)), )
LOCAL_MULTILIB := both
LOCAL_SRC_FILES_$(TARGET_ARCH) := $(TARGET_SVEP_LIB_PATH)/$(TARGET_ARCH)/librknnrt-svep.so
LOCAL_SRC_FILES_$(TARGET_2ND_ARCH) := $(TARGET_SVEP_LIB_PATH)/$(TARGET_2ND_ARCH)/librknnrt-svep.so
else
LOCAL_SRC_FILES_$(TARGET_ARCH) := $(TARGET_SVEP_LIB_PATH)/$(TARGET_ARCH)/librknnrt-svep.so
endif
include $(BUILD_PREBUILT)

include $(CLEAR_VARS)
LOCAL_MODULE := libsvepsr
LOCAL_MODULE_CLASS := SHARED_LIBRARIES
LOCAL_MODULE_SUFFIX := .so
LOCAL_SHARED_LIBRARIES := \
  libcutils \
  liblog \
  libui \
  libutils \
  libsync_vendor \
  librga \
  libOpenCL \
  librknnrt-svep \
  libhidlbase \
  libz \
	libhardware

# API 31 -> Android 12.0
ifeq (1,$(strip $(shell expr $(PLATFORM_SDK_VERSION) \> 30)))

LOCAL_SHARED_LIBRARIES += \
	libgralloctypes \
	android.hardware.graphics.mapper@4.0

endif

LOCAL_PROPRIETARY_MODULE := true

LOCAL_REQUIRED_MODULES := \
	SrOsd.ttf

ifneq ($(strip $(TARGET_2ND_ARCH)), )
LOCAL_MULTILIB := both
LOCAL_SRC_FILES_$(TARGET_ARCH) := $(TARGET_SVEP_LIB_PATH)/$(TARGET_ARCH)/libsvepsr.so
LOCAL_SRC_FILES_$(TARGET_2ND_ARCH) := $(TARGET_SVEP_LIB_PATH)/$(TARGET_2ND_ARCH)/libsvepsr.so
else
LOCAL_SRC_FILES_$(TARGET_ARCH) := $(TARGET_SVEP_LIB_PATH)/$(TARGET_ARCH)/libsvepsr.so
endif
LOCAL_MODULE_SUFFIX := .so
include $(BUILD_PREBUILT)

## copy RKNPU-AI-892x136-RGBA.bin from etc to /vendor/etc/
include $(CLEAR_VARS)
LOCAL_MODULE := SrOsd.ttf
LOCAL_PROPRIETARY_MODULE := true
LOCAL_MODULE_CLASS := ETC
LOCAL_SRC_FILES := res/SrOsd.ttf
include $(BUILD_PREBUILT)

endif
