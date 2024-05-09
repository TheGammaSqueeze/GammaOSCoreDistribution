# Copyright (C) 2020-2022 Arm Limited.
# SPDX-License-Identifier: Apache-2.0
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# Configuration that should be included by BoardConfig.mk to configure necessary Soong namespaces.

#
# Software behaviour defines
#

# The following defines are used to override default behaviour of which heap is selected for allocations.
# The default is to pick system heap.

# When enabled, allocations for display buffers will use physically contiguous memory.
GRALLOC_USE_CONTIGUOUS_DISPLAY_MEMORY?=0

# When enabled, forces format to BGRA_8888 for FB usage when HWC is in use
GRALLOC_HWC_FORCE_BGRA_8888?=0

# When enabled, disables AFBC for FB usage when HWC is in use
GRALLOC_HWC_FB_DISABLE_AFBC?=0

# When enabled, buffers will never be allocated with AFBC
GRALLOC_ARM_NO_EXTERNAL_AFBC?=0

# For hikey960 use contiguous memory for framebuffer allocations.
ifeq ($(TARGET_PRODUCT), hikey960)
GRALLOC_USE_CONTIGUOUS_DISPLAY_MEMORY=1
endif


# Setup configuration in Soong namespace
SOONG_CONFIG_NAMESPACES += arm_gralloc
SOONG_CONFIG_arm_gralloc := \
	gralloc_use_contiguous_display_memory \
	gralloc_hwc_force_bgra_8888 \
	gralloc_hwc_fb_disable_afbc \
	gralloc_arm_no_external_afbc \
	gralloc_target_product \

SOONG_CONFIG_arm_gralloc_gralloc_use_contiguous_display_memory := $(GRALLOC_USE_CONTIGUOUS_DISPLAY_MEMORY)
SOONG_CONFIG_arm_gralloc_gralloc_hwc_force_bgra_8888 := $(GRALLOC_HWC_FORCE_BGRA_8888)

SOONG_CONFIG_arm_gralloc_gralloc_hwc_fb_disable_afbc := $(GRALLOC_HWC_FB_DISABLE_AFBC)
SOONG_CONFIG_arm_gralloc_gralloc_arm_no_external_afbc := $(GRALLOC_ARM_NO_EXTERNAL_AFBC)
SOONG_CONFIG_arm_gralloc_gralloc_target_product := $(TARGET_PRODUCT)

# Retrieve the directory of Gralloc module
LOCAL_MODULE_MAKEFILE := $(lastword $(MAKEFILE_LIST)))
GRALLOC_TOP_DIR := $(strip $(patsubst %/,%,$(dir $(LOCAL_MODULE_MAKEFILE))))

# Add the system properties for Gralloc
TARGET_VENDOR_PROP += $(GRALLOC_TOP_DIR)/arm.gralloc.usage.prop
TARGET_VENDOR_PROP += $(GRALLOC_TOP_DIR)/arm.egl.config.prop
