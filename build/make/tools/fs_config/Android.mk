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

# One can override the default android_filesystem_config.h file by using TARGET_FS_CONFIG_GEN.
#   Set TARGET_FS_CONFIG_GEN to contain a list of intermediate format files
#   for generating the android_filesystem_config.h file.
#
# More information can be found in the README

ifneq ($(wildcard $(TARGET_DEVICE_DIR)/android_filesystem_config.h),)
$(error Using $(TARGET_DEVICE_DIR)/android_filesystem_config.h is deprecated, please use TARGET_FS_CONFIG_GEN instead)
endif

system_android_filesystem_config := system/core/libcutils/include/private/android_filesystem_config.h
system_capability_header := bionic/libc/kernel/uapi/linux/capability.h

# Use snapshots if exist
vendor_android_filesystem_config := $(strip \
  $(if $(filter-out current,$(BOARD_VNDK_VERSION)), \
    $(SOONG_VENDOR_$(BOARD_VNDK_VERSION)_SNAPSHOT_DIR)/include/$(system_android_filesystem_config)))
ifeq (,$(wildcard $(vendor_android_filesystem_config)))
vendor_android_filesystem_config := $(system_android_filesystem_config)
endif

vendor_capability_header := $(strip \
  $(if $(filter-out current,$(BOARD_VNDK_VERSION)), \
    $(SOONG_VENDOR_$(BOARD_VNDK_VERSION)_SNAPSHOT_DIR)/include/$(system_capability_header)))
ifeq (,$(wildcard $(vendor_capability_header)))
vendor_capability_header := $(system_capability_header)
endif

# List of supported vendor, oem, odm, vendor_dlkm, odm_dlkm, and system_dlkm Partitions
fs_config_generate_extra_partition_list := $(strip \
  $(if $(BOARD_USES_VENDORIMAGE)$(BOARD_VENDORIMAGE_FILE_SYSTEM_TYPE),vendor) \
  $(if $(BOARD_USES_OEMIMAGE)$(BOARD_OEMIMAGE_FILE_SYSTEM_TYPE),oem) \
  $(if $(BOARD_USES_ODMIMAGE)$(BOARD_ODMIMAGE_FILE_SYSTEM_TYPE),odm) \
  $(if $(BOARD_USES_VENDOR_DLKMIMAGE)$(BOARD_VENDOR_DLKMIMAGE_FILE_SYSTEM_TYPE),vendor_dlkm) \
  $(if $(BOARD_USES_ODM_DLKMIMAGE)$(BOARD_ODM_DLKMIMAGE_FILE_SYSTEM_TYPE),odm_dlkm) \
  $(if $(BOARD_USES_SYSTEM_DLKMIMAGE)$(BOARD_SYSTEM_DLKMIMAGE_FILE_SYSTEM_TYPE),system_dlkm) \
)

##################################
# Generate the <p>/etc/fs_config_dirs binary files for each partition.
# Add fs_config_dirs to PRODUCT_PACKAGES in the device make file to enable.
include $(CLEAR_VARS)

LOCAL_MODULE := fs_config_dirs
LOCAL_LICENSE_KINDS := SPDX-license-identifier-Apache-2.0
LOCAL_LICENSE_CONDITIONS := notice
LOCAL_NOTICE_FILE := build/soong/licenses/LICENSE
LOCAL_REQUIRED_MODULES := \
  fs_config_dirs_system \
  fs_config_dirs_system_ext \
  fs_config_dirs_product \
  fs_config_dirs_nonsystem
include $(BUILD_PHONY_PACKAGE)

##################################
# Generate the <p>/etc/fs_config_files binary files for each partition.
# Add fs_config_files to PRODUCT_PACKAGES in the device make file to enable.
include $(CLEAR_VARS)

LOCAL_MODULE := fs_config_files
LOCAL_LICENSE_KINDS := SPDX-license-identifier-Apache-2.0
LOCAL_LICENSE_CONDITIONS := notice
LOCAL_NOTICE_FILE := build/soong/licenses/LICENSE
LOCAL_REQUIRED_MODULES := \
  fs_config_files_system \
  fs_config_files_system_ext \
  fs_config_files_product \
  fs_config_files_nonsystem
include $(BUILD_PHONY_PACKAGE)

##################################
# Generate the system_ext/etc/fs_config_dirs binary file for the target if the
# system_ext partition is generated. Add fs_config_dirs or fs_config_dirs_system_ext
# to PRODUCT_PACKAGES in the device make file to enable.
include $(CLEAR_VARS)

LOCAL_MODULE := fs_config_dirs_system_ext
LOCAL_LICENSE_KINDS := SPDX-license-identifier-Apache-2.0
LOCAL_LICENSE_CONDITIONS := notice
LOCAL_NOTICE_FILE := build/soong/licenses/LICENSE
LOCAL_REQUIRED_MODULES := $(if $(BOARD_USES_SYSTEM_EXTIMAGE)$(BOARD_SYSTEM_EXTIMAGE_FILE_SYSTEM_TYPE),_fs_config_dirs_system_ext)
include $(BUILD_PHONY_PACKAGE)

##################################
# Generate the system_ext/etc/fs_config_files binary file for the target if the
# system_ext partition is generated. Add fs_config_files or fs_config_files_system_ext
# to PRODUCT_PACKAGES in the device make file to enable.
include $(CLEAR_VARS)

LOCAL_MODULE := fs_config_files_system_ext
LOCAL_LICENSE_KINDS := SPDX-license-identifier-Apache-2.0
LOCAL_LICENSE_CONDITIONS := notice
LOCAL_NOTICE_FILE := build/soong/licenses/LICENSE
LOCAL_REQUIRED_MODULES := $(if $(BOARD_USES_SYSTEM_EXTIMAGE)$(BOARD_SYSTEM_EXTIMAGE_FILE_SYSTEM_TYPE),_fs_config_files_system_ext)
include $(BUILD_PHONY_PACKAGE)

##################################
# Generate the product/etc/fs_config_dirs binary file for the target if the
# product partition is generated. Add fs_config_dirs or fs_config_dirs_product
# to PRODUCT_PACKAGES in the device make file to enable.
include $(CLEAR_VARS)

LOCAL_MODULE := fs_config_dirs_product
LOCAL_LICENSE_KINDS := SPDX-license-identifier-Apache-2.0
LOCAL_LICENSE_CONDITIONS := notice
LOCAL_NOTICE_FILE := build/soong/licenses/LICENSE
LOCAL_REQUIRED_MODULES := $(if $(BOARD_USES_PRODUCTIMAGE)$(BOARD_PRODUCTIMAGE_FILE_SYSTEM_TYPE),_fs_config_dirs_product)
include $(BUILD_PHONY_PACKAGE)

##################################
# Generate the product/etc/fs_config_files binary file for the target if the
# product partition is generated. Add fs_config_files or fs_config_files_product
# to PRODUCT_PACKAGES in the device make file to enable.
include $(CLEAR_VARS)

LOCAL_MODULE := fs_config_files_product
LOCAL_LICENSE_KINDS := SPDX-license-identifier-Apache-2.0
LOCAL_LICENSE_CONDITIONS := notice
LOCAL_NOTICE_FILE := build/soong/licenses/LICENSE
LOCAL_REQUIRED_MODULES := $(if $(BOARD_USES_PRODUCTIMAGE)$(BOARD_PRODUCTIMAGE_FILE_SYSTEM_TYPE),_fs_config_files_product)
include $(BUILD_PHONY_PACKAGE)

##################################
# Generate the <p>/etc/fs_config_dirs binary files for all enabled partitions
# excluding /system, /system_ext and /product. Add fs_config_dirs_nonsystem to
# PRODUCT_PACKAGES in the device make file to enable.
include $(CLEAR_VARS)

LOCAL_MODULE := fs_config_dirs_nonsystem
LOCAL_LICENSE_KINDS := SPDX-license-identifier-Apache-2.0
LOCAL_LICENSE_CONDITIONS := notice
LOCAL_NOTICE_FILE := build/soong/licenses/LICENSE
LOCAL_REQUIRED_MODULES := $(foreach t,$(fs_config_generate_extra_partition_list),_fs_config_dirs_$(t))
include $(BUILD_PHONY_PACKAGE)

##################################
# Generate the <p>/etc/fs_config_files binary files for all enabled partitions
# excluding /system, /system_ext and /product. Add fs_config_files_nonsystem to
# PRODUCT_PACKAGES in the device make file to enable.
include $(CLEAR_VARS)

LOCAL_MODULE := fs_config_files_nonsystem
LOCAL_LICENSE_KINDS := SPDX-license-identifier-Apache-2.0
LOCAL_LICENSE_CONDITIONS := notice
LOCAL_NOTICE_FILE := build/soong/licenses/LICENSE
LOCAL_REQUIRED_MODULES := $(foreach t,$(fs_config_generate_extra_partition_list),_fs_config_files_$(t))
include $(BUILD_PHONY_PACKAGE)

##################################
# Generate the system/etc/fs_config_dirs binary file for the target
# Add fs_config_dirs or fs_config_dirs_system to PRODUCT_PACKAGES in
# the device make file to enable
include $(CLEAR_VARS)

LOCAL_MODULE := fs_config_dirs_system
LOCAL_LICENSE_KINDS := SPDX-license-identifier-Apache-2.0
LOCAL_LICENSE_CONDITIONS := notice
LOCAL_NOTICE_FILE := build/soong/licenses/LICENSE
LOCAL_MODULE_CLASS := ETC
LOCAL_INSTALLED_MODULE_STEM := fs_config_dirs
include $(BUILD_SYSTEM)/base_rules.mk
$(LOCAL_BUILT_MODULE): PRIVATE_ANDROID_FS_HDR := $(system_android_filesystem_config)
$(LOCAL_BUILT_MODULE): PRIVATE_ANDROID_CAP_HDR := $(system_capability_header)
$(LOCAL_BUILT_MODULE): PRIVATE_PARTITION_LIST := $(fs_config_generate_extra_partition_list)
$(LOCAL_BUILT_MODULE): PRIVATE_TARGET_FS_CONFIG_GEN := $(TARGET_FS_CONFIG_GEN)
$(LOCAL_BUILT_MODULE): $(LOCAL_PATH)/fs_config_generator.py $(TARGET_FS_CONFIG_GEN) $(system_android_filesystem_config) $(system_capability_header)
	@mkdir -p $(dir $@)
	$< fsconfig \
	   --aid-header $(PRIVATE_ANDROID_FS_HDR) \
	   --capability-header $(PRIVATE_ANDROID_CAP_HDR) \
	   --partition system \
	   --all-partitions "$(subst $(space),$(comma),$(PRIVATE_PARTITION_LIST))" \
	   --dirs \
	   --out_file $@ \
	   $(or $(PRIVATE_TARGET_FS_CONFIG_GEN),/dev/null)

##################################
# Generate the system/etc/fs_config_files binary file for the target
# Add fs_config_files or fs_config_files_system to PRODUCT_PACKAGES in
# the device make file to enable
include $(CLEAR_VARS)

LOCAL_MODULE := fs_config_files_system
LOCAL_LICENSE_KINDS := SPDX-license-identifier-Apache-2.0
LOCAL_LICENSE_CONDITIONS := notice
LOCAL_NOTICE_FILE := build/soong/licenses/LICENSE
LOCAL_MODULE_CLASS := ETC
LOCAL_INSTALLED_MODULE_STEM := fs_config_files
include $(BUILD_SYSTEM)/base_rules.mk
$(LOCAL_BUILT_MODULE): PRIVATE_ANDROID_FS_HDR := $(system_android_filesystem_config)
$(LOCAL_BUILT_MODULE): PRIVATE_ANDROID_CAP_HDR := $(system_capability_header)
$(LOCAL_BUILT_MODULE): PRIVATE_PARTITION_LIST := $(fs_config_generate_extra_partition_list)
$(LOCAL_BUILT_MODULE): PRIVATE_TARGET_FS_CONFIG_GEN := $(TARGET_FS_CONFIG_GEN)
$(LOCAL_BUILT_MODULE): $(LOCAL_PATH)/fs_config_generator.py $(TARGET_FS_CONFIG_GEN) $(system_android_filesystem_config) $(system_capability_header)
	@mkdir -p $(dir $@)
	$< fsconfig \
	   --aid-header $(PRIVATE_ANDROID_FS_HDR) \
	   --capability-header $(PRIVATE_ANDROID_CAP_HDR) \
	   --partition system \
	   --all-partitions "$(subst $(space),$(comma),$(PRIVATE_PARTITION_LIST))" \
	   --files \
	   --out_file $@ \
	   $(or $(PRIVATE_TARGET_FS_CONFIG_GEN),/dev/null)

ifneq ($(filter vendor,$(fs_config_generate_extra_partition_list)),)
##################################
# Generate the vendor/etc/fs_config_dirs binary file for the target
# Add fs_config_dirs or fs_config_dirs_nonsystem to PRODUCT_PACKAGES
# in the device make file to enable
include $(CLEAR_VARS)

LOCAL_MODULE := _fs_config_dirs_vendor
LOCAL_LICENSE_KINDS := SPDX-license-identifier-Apache-2.0
LOCAL_LICENSE_CONDITIONS := notice
LOCAL_NOTICE_FILE := build/soong/licenses/LICENSE
LOCAL_MODULE_CLASS := ETC
LOCAL_INSTALLED_MODULE_STEM := fs_config_dirs
LOCAL_MODULE_PATH := $(TARGET_OUT_VENDOR)/etc
include $(BUILD_SYSTEM)/base_rules.mk
$(LOCAL_BUILT_MODULE): PRIVATE_ANDROID_FS_HDR := $(vendor_android_filesystem_config)
$(LOCAL_BUILT_MODULE): PRIVATE_ANDROID_CAP_HDR := $(vendor_capability_header)
$(LOCAL_BUILT_MODULE): PRIVATE_TARGET_FS_CONFIG_GEN := $(TARGET_FS_CONFIG_GEN)
$(LOCAL_BUILT_MODULE): $(LOCAL_PATH)/fs_config_generator.py $(TARGET_FS_CONFIG_GEN) $(vendor_android_filesystem_config) $(vendor_capability_header)
	@mkdir -p $(dir $@)
	$< fsconfig \
	   --aid-header $(PRIVATE_ANDROID_FS_HDR) \
	   --capability-header $(PRIVATE_ANDROID_CAP_HDR) \
	   --partition vendor \
	   --dirs \
	   --out_file $@ \
	   $(or $(PRIVATE_TARGET_FS_CONFIG_GEN),/dev/null)

##################################
# Generate the vendor/etc/fs_config_files binary file for the target
# Add fs_config_files or fs_config_files_nonsystem to PRODUCT_PACKAGES
# in the device make file to enable
include $(CLEAR_VARS)

LOCAL_MODULE := _fs_config_files_vendor
LOCAL_LICENSE_KINDS := SPDX-license-identifier-Apache-2.0
LOCAL_LICENSE_CONDITIONS := notice
LOCAL_NOTICE_FILE := build/soong/licenses/LICENSE
LOCAL_MODULE_CLASS := ETC
LOCAL_INSTALLED_MODULE_STEM := fs_config_files
LOCAL_MODULE_PATH := $(TARGET_OUT_VENDOR)/etc
include $(BUILD_SYSTEM)/base_rules.mk
$(LOCAL_BUILT_MODULE): PRIVATE_ANDROID_FS_HDR := $(vendor_android_filesystem_config)
$(LOCAL_BUILT_MODULE): PRIVATE_ANDROID_CAP_HDR := $(vendor_capability_header)
$(LOCAL_BUILT_MODULE): PRIVATE_TARGET_FS_CONFIG_GEN := $(TARGET_FS_CONFIG_GEN)
$(LOCAL_BUILT_MODULE): $(LOCAL_PATH)/fs_config_generator.py $(TARGET_FS_CONFIG_GEN) $(vendor_android_filesystem_config) $(vendor_capability_header)
	@mkdir -p $(dir $@)
	$< fsconfig \
	   --aid-header $(PRIVATE_ANDROID_FS_HDR) \
	   --capability-header $(PRIVATE_ANDROID_CAP_HDR) \
	   --partition vendor \
	   --files \
	   --out_file $@ \
	   $(or $(PRIVATE_TARGET_FS_CONFIG_GEN),/dev/null)

endif

ifneq ($(filter oem,$(fs_config_generate_extra_partition_list)),)
##################################
# Generate the oem/etc/fs_config_dirs binary file for the target
# Add fs_config_dirs or fs_config_dirs_nonsystem to PRODUCT_PACKAGES
# in the device make file to enable
include $(CLEAR_VARS)

LOCAL_MODULE := _fs_config_dirs_oem
LOCAL_LICENSE_KINDS := SPDX-license-identifier-Apache-2.0
LOCAL_LICENSE_CONDITIONS := notice
LOCAL_NOTICE_FILE := build/soong/licenses/LICENSE
LOCAL_MODULE_CLASS := ETC
LOCAL_INSTALLED_MODULE_STEM := fs_config_dirs
LOCAL_MODULE_PATH := $(TARGET_OUT_OEM)/etc
include $(BUILD_SYSTEM)/base_rules.mk
$(LOCAL_BUILT_MODULE): PRIVATE_ANDROID_FS_HDR := $(system_android_filesystem_config)
$(LOCAL_BUILT_MODULE): PRIVATE_ANDROID_CAP_HDR := $(system_capability_header)
$(LOCAL_BUILT_MODULE): PRIVATE_TARGET_FS_CONFIG_GEN := $(TARGET_FS_CONFIG_GEN)
$(LOCAL_BUILT_MODULE): $(LOCAL_PATH)/fs_config_generator.py $(TARGET_FS_CONFIG_GEN) $(system_android_filesystem_config) $(system_capability_header)
	@mkdir -p $(dir $@)
	$< fsconfig \
	   --aid-header $(PRIVATE_ANDROID_FS_HDR) \
	   --capability-header $(PRIVATE_ANDROID_CAP_HDR) \
	   --partition oem \
	   --dirs \
	   --out_file $@ \
	   $(or $(PRIVATE_TARGET_FS_CONFIG_GEN),/dev/null)

##################################
# Generate the oem/etc/fs_config_files binary file for the target
# Add fs_config_files or fs_config_files_nonsystem to PRODUCT_PACKAGES
# in the device make file to enable
include $(CLEAR_VARS)

LOCAL_MODULE := _fs_config_files_oem
LOCAL_LICENSE_KINDS := SPDX-license-identifier-Apache-2.0
LOCAL_LICENSE_CONDITIONS := notice
LOCAL_NOTICE_FILE := build/soong/licenses/LICENSE
LOCAL_MODULE_CLASS := ETC
LOCAL_INSTALLED_MODULE_STEM := fs_config_files
LOCAL_MODULE_PATH := $(TARGET_OUT_OEM)/etc
include $(BUILD_SYSTEM)/base_rules.mk
$(LOCAL_BUILT_MODULE): PRIVATE_ANDROID_FS_HDR := $(system_android_filesystem_config)
$(LOCAL_BUILT_MODULE): PRIVATE_ANDROID_CAP_HDR := $(system_capability_header)
$(LOCAL_BUILT_MODULE): PRIVATE_TARGET_FS_CONFIG_GEN := $(TARGET_FS_CONFIG_GEN)
$(LOCAL_BUILT_MODULE): $(LOCAL_PATH)/fs_config_generator.py $(TARGET_FS_CONFIG_GEN) $(system_android_filesystem_config) $(system_capability_header)
	@mkdir -p $(dir $@)
	$< fsconfig \
	   --aid-header $(PRIVATE_ANDROID_FS_HDR) \
	   --capability-header $(PRIVATE_ANDROID_CAP_HDR) \
	   --partition oem \
	   --files \
	   --out_file $@ \
	   $(or $(PRIVATE_TARGET_FS_CONFIG_GEN),/dev/null)

endif

ifneq ($(filter odm,$(fs_config_generate_extra_partition_list)),)
##################################
# Generate the odm/etc/fs_config_dirs binary file for the target
# Add fs_config_dirs or fs_config_dirs_nonsystem to PRODUCT_PACKAGES
# in the device make file to enable
include $(CLEAR_VARS)

LOCAL_MODULE := _fs_config_dirs_odm
LOCAL_LICENSE_KINDS := SPDX-license-identifier-Apache-2.0
LOCAL_LICENSE_CONDITIONS := notice
LOCAL_NOTICE_FILE := build/soong/licenses/LICENSE
LOCAL_MODULE_CLASS := ETC
LOCAL_INSTALLED_MODULE_STEM := fs_config_dirs
LOCAL_MODULE_PATH := $(TARGET_OUT_ODM)/etc
include $(BUILD_SYSTEM)/base_rules.mk
$(LOCAL_BUILT_MODULE): PRIVATE_ANDROID_FS_HDR := $(vendor_android_filesystem_config)
$(LOCAL_BUILT_MODULE): PRIVATE_ANDROID_CAP_HDR := $(vendor_capability_header)
$(LOCAL_BUILT_MODULE): PRIVATE_TARGET_FS_CONFIG_GEN := $(TARGET_FS_CONFIG_GEN)
$(LOCAL_BUILT_MODULE): $(LOCAL_PATH)/fs_config_generator.py $(TARGET_FS_CONFIG_GEN) $(vendor_android_filesystem_config) $(vendor_capability_header)
	@mkdir -p $(dir $@)
	$< fsconfig \
	   --aid-header $(PRIVATE_ANDROID_FS_HDR) \
	   --capability-header $(PRIVATE_ANDROID_CAP_HDR) \
	   --partition odm \
	   --dirs \
	   --out_file $@ \
	   $(or $(PRIVATE_TARGET_FS_CONFIG_GEN),/dev/null)

##################################
# Generate the odm/etc/fs_config_files binary file for the target
# Add fs_config_files or fs_config_files_nonsystem to PRODUCT_PACKAGES
# in the device make file to enable
include $(CLEAR_VARS)

LOCAL_MODULE := _fs_config_files_odm
LOCAL_LICENSE_KINDS := SPDX-license-identifier-Apache-2.0
LOCAL_LICENSE_CONDITIONS := notice
LOCAL_NOTICE_FILE := build/soong/licenses/LICENSE
LOCAL_MODULE_CLASS := ETC
LOCAL_INSTALLED_MODULE_STEM := fs_config_files
LOCAL_MODULE_PATH := $(TARGET_OUT_ODM)/etc
include $(BUILD_SYSTEM)/base_rules.mk
$(LOCAL_BUILT_MODULE): PRIVATE_ANDROID_FS_HDR := $(vendor_android_filesystem_config)
$(LOCAL_BUILT_MODULE): PRIVATE_ANDROID_CAP_HDR := $(vendor_capability_header)
$(LOCAL_BUILT_MODULE): PRIVATE_TARGET_FS_CONFIG_GEN := $(TARGET_FS_CONFIG_GEN)
$(LOCAL_BUILT_MODULE): $(LOCAL_PATH)/fs_config_generator.py $(TARGET_FS_CONFIG_GEN) $(vendor_android_filesystem_config) $(vendor_capability_header)
	@mkdir -p $(dir $@)
	$< fsconfig \
	   --aid-header $(PRIVATE_ANDROID_FS_HDR) \
	   --capability-header $(PRIVATE_ANDROID_CAP_HDR) \
	   --partition odm \
	   --files \
	   --out_file $@ \
	   $(or $(PRIVATE_TARGET_FS_CONFIG_GEN),/dev/null)

endif

ifneq ($(filter vendor_dlkm,$(fs_config_generate_extra_partition_list)),)
##################################
# Generate the vendor_dlkm/etc/fs_config_dirs binary file for the target
# Add fs_config_dirs or fs_config_dirs_nonsystem to PRODUCT_PACKAGES in
# the device make file to enable
include $(CLEAR_VARS)

LOCAL_MODULE := _fs_config_dirs_vendor_dlkm
LOCAL_LICENSE_KINDS := SPDX-license-identifier-Apache-2.0
LOCAL_LICENSE_CONDITIONS := notice
LOCAL_NOTICE_FILE := build/soong/licenses/LICENSE
LOCAL_MODULE_CLASS := ETC
LOCAL_INSTALLED_MODULE_STEM := fs_config_dirs
LOCAL_MODULE_PATH := $(TARGET_OUT_VENDOR_DLKM)/etc
include $(BUILD_SYSTEM)/base_rules.mk
$(LOCAL_BUILT_MODULE): PRIVATE_ANDROID_FS_HDR := $(vendor_android_filesystem_config)
$(LOCAL_BUILT_MODULE): PRIVATE_ANDROID_CAP_HDR := $(vendor_capability_header)
$(LOCAL_BUILT_MODULE): PRIVATE_TARGET_FS_CONFIG_GEN := $(TARGET_FS_CONFIG_GEN)
$(LOCAL_BUILT_MODULE): $(LOCAL_PATH)/fs_config_generator.py $(TARGET_FS_CONFIG_GEN) $(vendor_android_filesystem_config) $(vendor_capability_header)
	@mkdir -p $(dir $@)
	$< fsconfig \
	   --aid-header $(PRIVATE_ANDROID_FS_HDR) \
	   --capability-header $(PRIVATE_ANDROID_CAP_HDR) \
	   --partition vendor_dlkm \
	   --dirs \
	   --out_file $@ \
	   $(or $(PRIVATE_TARGET_FS_CONFIG_GEN),/dev/null)

##################################
# Generate the vendor_dlkm/etc/fs_config_files binary file for the target
# Add fs_config_files or fs_config_files_nonsystem to PRODUCT_PACKAGES in
# the device make file to enable
include $(CLEAR_VARS)

LOCAL_MODULE := _fs_config_files_vendor_dlkm
LOCAL_LICENSE_KINDS := SPDX-license-identifier-Apache-2.0
LOCAL_LICENSE_CONDITIONS := notice
LOCAL_NOTICE_FILE := build/soong/licenses/LICENSE
LOCAL_MODULE_CLASS := ETC
LOCAL_INSTALLED_MODULE_STEM := fs_config_files
LOCAL_MODULE_PATH := $(TARGET_OUT_VENDOR_DLKM)/etc
include $(BUILD_SYSTEM)/base_rules.mk
$(LOCAL_BUILT_MODULE): PRIVATE_ANDROID_FS_HDR := $(vendor_android_filesystem_config)
$(LOCAL_BUILT_MODULE): PRIVATE_ANDROID_CAP_HDR := $(vendor_capability_header)
$(LOCAL_BUILT_MODULE): PRIVATE_TARGET_FS_CONFIG_GEN := $(TARGET_FS_CONFIG_GEN)
$(LOCAL_BUILT_MODULE): $(LOCAL_PATH)/fs_config_generator.py $(TARGET_FS_CONFIG_GEN) $(vendor_android_filesystem_config) $(vendor_capability_header)
	@mkdir -p $(dir $@)
	$< fsconfig \
	   --aid-header $(PRIVATE_ANDROID_FS_HDR) \
	   --capability-header $(PRIVATE_ANDROID_CAP_HDR) \
	   --partition vendor_dlkm \
	   --files \
	   --out_file $@ \
	   $(or $(PRIVATE_TARGET_FS_CONFIG_GEN),/dev/null)

endif

ifneq ($(filter odm_dlkm,$(fs_config_generate_extra_partition_list)),)
##################################
# Generate the odm_dlkm/etc/fs_config_dirs binary file for the target
# Add fs_config_dirs or fs_config_dirs_nonsystem to PRODUCT_PACKAGES
# in the device make file to enable
include $(CLEAR_VARS)

LOCAL_MODULE := _fs_config_dirs_odm_dlkm
LOCAL_LICENSE_KINDS := SPDX-license-identifier-Apache-2.0
LOCAL_LICENSE_CONDITIONS := notice
LOCAL_NOTICE_FILE := build/soong/licenses/LICENSE
LOCAL_MODULE_CLASS := ETC
LOCAL_INSTALLED_MODULE_STEM := fs_config_dirs
LOCAL_MODULE_PATH := $(TARGET_OUT_ODM_DLKM)/etc
include $(BUILD_SYSTEM)/base_rules.mk
$(LOCAL_BUILT_MODULE): PRIVATE_ANDROID_FS_HDR := $(vendor_android_filesystem_config)
$(LOCAL_BUILT_MODULE): PRIVATE_ANDROID_CAP_HDR := $(vendor_capability_header)
$(LOCAL_BUILT_MODULE): PRIVATE_TARGET_FS_CONFIG_GEN := $(TARGET_FS_CONFIG_GEN)
$(LOCAL_BUILT_MODULE): $(LOCAL_PATH)/fs_config_generator.py $(TARGET_FS_CONFIG_GEN) $(vendor_android_filesystem_config) $(vendor_capability_header)
	@mkdir -p $(dir $@)
	$< fsconfig \
	   --aid-header $(PRIVATE_ANDROID_FS_HDR) \
	   --capability-header $(PRIVATE_ANDROID_CAP_HDR) \
	   --partition odm_dlkm \
	   --dirs \
	   --out_file $@ \
	   $(or $(PRIVATE_TARGET_FS_CONFIG_GEN),/dev/null)

##################################
# Generate the odm_dlkm/etc/fs_config_files binary file for the target
# Add fs_config_files or fs_config_files_nonsystem to PRODUCT_PACKAGES
# in the device make file to enable
include $(CLEAR_VARS)

LOCAL_MODULE := _fs_config_files_odm_dlkm
LOCAL_LICENSE_KINDS := SPDX-license-identifier-Apache-2.0
LOCAL_LICENSE_CONDITIONS := notice
LOCAL_NOTICE_FILE := build/soong/licenses/LICENSE
LOCAL_MODULE_CLASS := ETC
LOCAL_INSTALLED_MODULE_STEM := fs_config_files
LOCAL_MODULE_PATH := $(TARGET_OUT_ODM_DLKM)/etc
include $(BUILD_SYSTEM)/base_rules.mk
$(LOCAL_BUILT_MODULE): PRIVATE_ANDROID_FS_HDR := $(vendor_android_filesystem_config)
$(LOCAL_BUILT_MODULE): PRIVATE_ANDROID_CAP_HDR := $(vendor_capability_header)
$(LOCAL_BUILT_MODULE): PRIVATE_TARGET_FS_CONFIG_GEN := $(TARGET_FS_CONFIG_GEN)
$(LOCAL_BUILT_MODULE): $(LOCAL_PATH)/fs_config_generator.py $(TARGET_FS_CONFIG_GEN) $(vendor_android_filesystem_config) $(vendor_capability_header)
	@mkdir -p $(dir $@)
	$< fsconfig \
	   --aid-header $(PRIVATE_ANDROID_FS_HDR) \
	   --capability-header $(PRIVATE_ANDROID_CAP_HDR) \
	   --partition odm_dlkm \
	   --files \
	   --out_file $@ \
	   $(or $(PRIVATE_TARGET_FS_CONFIG_GEN),/dev/null)

endif

ifneq ($(filter system_dlkm,$(fs_config_generate_extra_partition_list)),)
##################################
# Generate the system_dlkm/etc/fs_config_dirs binary file for the target
# Add fs_config_dirs or fs_config_dirs_nonsystem to PRODUCT_PACKAGES
# in the device make file to enable
include $(CLEAR_VARS)

LOCAL_MODULE := _fs_config_dirs_system_dlkm
LOCAL_LICENSE_KINDS := SPDX-license-identifier-Apache-2.0
LOCAL_LICENSE_CONDITIONS := notice
LOCAL_NOTICE_FILE := build/soong/licenses/LICENSE
LOCAL_MODULE_CLASS := ETC
LOCAL_INSTALLED_MODULE_STEM := fs_config_dirs
LOCAL_MODULE_PATH := $(TARGET_OUT_SYSTEM_DLKM)/etc
include $(BUILD_SYSTEM)/base_rules.mk
$(LOCAL_BUILT_MODULE): PRIVATE_ANDROID_FS_HDR := $(vendor_android_filesystem_config)
$(LOCAL_BUILT_MODULE): PRIVATE_ANDROID_CAP_HDR := $(vendor_capability_header)
$(LOCAL_BUILT_MODULE): PRIVATE_TARGET_FS_CONFIG_GEN := $(TARGET_FS_CONFIG_GEN)
$(LOCAL_BUILT_MODULE): $(LOCAL_PATH)/fs_config_generator.py $(TARGET_FS_CONFIG_GEN) $(vendor_android_filesystem_config) $(vendor_capability_header)
	@mkdir -p $(dir $@)
	$< fsconfig \
	   --aid-header $(PRIVATE_ANDROID_FS_HDR) \
	   --capability-header $(PRIVATE_ANDROID_CAP_HDR) \
	   --partition system_dlkm \
	   --dirs \
	   --out_file $@ \
	   $(or $(PRIVATE_TARGET_FS_CONFIG_GEN),/dev/null)

##################################
# Generate the system_dlkm/etc/fs_config_files binary file for the target
# Add fs_config_files or fs_config_files_nonsystem to PRODUCT_PACKAGES
# in the device make file to enable
include $(CLEAR_VARS)

LOCAL_MODULE := _fs_config_files_system_dlkm
LOCAL_LICENSE_KINDS := SPDX-license-identifier-Apache-2.0
LOCAL_LICENSE_CONDITIONS := notice
LOCAL_NOTICE_FILE := build/soong/licenses/LICENSE
LOCAL_MODULE_CLASS := ETC
LOCAL_INSTALLED_MODULE_STEM := fs_config_files
LOCAL_MODULE_PATH := $(TARGET_OUT_SYSTEM_DLKM)/etc
include $(BUILD_SYSTEM)/base_rules.mk
$(LOCAL_BUILT_MODULE): PRIVATE_ANDROID_FS_HDR := $(vendor_android_filesystem_config)
$(LOCAL_BUILT_MODULE): PRIVATE_ANDROID_CAP_HDR := $(vendor_capability_header)
$(LOCAL_BUILT_MODULE): PRIVATE_TARGET_FS_CONFIG_GEN := $(TARGET_FS_CONFIG_GEN)
$(LOCAL_BUILT_MODULE): $(LOCAL_PATH)/fs_config_generator.py $(TARGET_FS_CONFIG_GEN) $(vendor_android_filesystem_config) $(vendor_capability_header)
	@mkdir -p $(dir $@)
	$< fsconfig \
	   --aid-header $(PRIVATE_ANDROID_FS_HDR) \
	   --capability-header $(PRIVATE_ANDROID_CAP_HDR) \
	   --partition system_dlkm \
	   --files \
	   --out_file $@ \
	   $(or $(PRIVATE_TARGET_FS_CONFIG_GEN),/dev/null)

endif

ifneq ($(BOARD_USES_PRODUCTIMAGE)$(BOARD_PRODUCTIMAGE_FILE_SYSTEM_TYPE),)
##################################
# Generate the product/etc/fs_config_dirs binary file for the target
# Add fs_config_dirs or fs_config_dirs_product to PRODUCT_PACKAGES in
# the device make file to enable
include $(CLEAR_VARS)

LOCAL_MODULE := _fs_config_dirs_product
LOCAL_LICENSE_KINDS := SPDX-license-identifier-Apache-2.0
LOCAL_LICENSE_CONDITIONS := notice
LOCAL_NOTICE_FILE := build/soong/licenses/LICENSE
LOCAL_MODULE_CLASS := ETC
LOCAL_INSTALLED_MODULE_STEM := fs_config_dirs
LOCAL_MODULE_PATH := $(TARGET_OUT_PRODUCT)/etc
include $(BUILD_SYSTEM)/base_rules.mk
$(LOCAL_BUILT_MODULE): PRIVATE_ANDROID_FS_HDR := $(system_android_filesystem_config)
$(LOCAL_BUILT_MODULE): PRIVATE_ANDROID_CAP_HDR := $(system_capability_header)
$(LOCAL_BUILT_MODULE): PRIVATE_TARGET_FS_CONFIG_GEN := $(TARGET_FS_CONFIG_GEN)
$(LOCAL_BUILT_MODULE): $(LOCAL_PATH)/fs_config_generator.py $(TARGET_FS_CONFIG_GEN) $(system_android_filesystem_config) $(system_capability_header)
	@mkdir -p $(dir $@)
	$< fsconfig \
	   --aid-header $(PRIVATE_ANDROID_FS_HDR) \
	   --capability-header $(PRIVATE_ANDROID_CAP_HDR) \
	   --partition product \
	   --dirs \
	   --out_file $@ \
	   $(or $(PRIVATE_TARGET_FS_CONFIG_GEN),/dev/null)

##################################
# Generate the product/etc/fs_config_files binary file for the target
# Add fs_config_files or fs_config_files_product to PRODUCT_PACKAGES in
# the device make file to enable
include $(CLEAR_VARS)

LOCAL_MODULE := _fs_config_files_product
LOCAL_LICENSE_KINDS := SPDX-license-identifier-Apache-2.0
LOCAL_LICENSE_CONDITIONS := notice
LOCAL_NOTICE_FILE := build/soong/licenses/LICENSE
LOCAL_MODULE_CLASS := ETC
LOCAL_INSTALLED_MODULE_STEM := fs_config_files
LOCAL_MODULE_PATH := $(TARGET_OUT_PRODUCT)/etc
include $(BUILD_SYSTEM)/base_rules.mk
$(LOCAL_BUILT_MODULE): PRIVATE_ANDROID_FS_HDR := $(system_android_filesystem_config)
$(LOCAL_BUILT_MODULE): PRIVATE_ANDROID_CAP_HDR := $(system_capability_header)
$(LOCAL_BUILT_MODULE): PRIVATE_TARGET_FS_CONFIG_GEN := $(TARGET_FS_CONFIG_GEN)
$(LOCAL_BUILT_MODULE): $(LOCAL_PATH)/fs_config_generator.py $(TARGET_FS_CONFIG_GEN) $(system_android_filesystem_config) $(system_capability_header)
	@mkdir -p $(dir $@)
	$< fsconfig \
	   --aid-header $(PRIVATE_ANDROID_FS_HDR) \
	   --capability-header $(PRIVATE_ANDROID_CAP_HDR) \
	   --partition product \
	   --files \
	   --out_file $@ \
	   $(or $(PRIVATE_TARGET_FS_CONFIG_GEN),/dev/null)
endif

ifneq ($(BOARD_USES_SYSTEM_EXTIMAGE)$(BOARD_SYSTEM_EXTIMAGE_FILE_SYSTEM_TYPE),)
##################################
# Generate the system_ext/etc/fs_config_dirs binary file for the target
# Add fs_config_dirs or fs_config_dirs_system_ext to PRODUCT_PACKAGES in
# the device make file to enable
include $(CLEAR_VARS)

LOCAL_MODULE := _fs_config_dirs_system_ext
LOCAL_LICENSE_KINDS := SPDX-license-identifier-Apache-2.0
LOCAL_LICENSE_CONDITIONS := notice
LOCAL_NOTICE_FILE := build/soong/licenses/LICENSE
LOCAL_MODULE_CLASS := ETC
LOCAL_INSTALLED_MODULE_STEM := fs_config_dirs
LOCAL_MODULE_PATH := $(TARGET_OUT_SYSTEM_EXT)/etc
include $(BUILD_SYSTEM)/base_rules.mk
$(LOCAL_BUILT_MODULE): PRIVATE_ANDROID_FS_HDR := $(system_android_filesystem_config)
$(LOCAL_BUILT_MODULE): PRIVATE_ANDROID_CAP_HDR := $(system_capability_header)
$(LOCAL_BUILT_MODULE): PRIVATE_TARGET_FS_CONFIG_GEN := $(TARGET_FS_CONFIG_GEN)
$(LOCAL_BUILT_MODULE): $(LOCAL_PATH)/fs_config_generator.py $(TARGET_FS_CONFIG_GEN) $(system_android_filesystem_config) $(system_capability_header)
	@mkdir -p $(dir $@)
	$< fsconfig \
	   --aid-header $(PRIVATE_ANDROID_FS_HDR) \
	   --capability-header $(PRIVATE_ANDROID_CAP_HDR) \
	   --partition system_ext \
	   --dirs \
	   --out_file $@ \
	   $(or $(PRIVATE_TARGET_FS_CONFIG_GEN),/dev/null)

##################################
# Generate the system_ext/etc/fs_config_files binary file for the target
# Add fs_config_files or fs_config_files_system_ext to PRODUCT_PACKAGES in
# the device make file to enable
include $(CLEAR_VARS)

LOCAL_MODULE := _fs_config_files_system_ext
LOCAL_LICENSE_KINDS := SPDX-license-identifier-Apache-2.0
LOCAL_LICENSE_CONDITIONS := notice
LOCAL_NOTICE_FILE := build/soong/licenses/LICENSE
LOCAL_MODULE_CLASS := ETC
LOCAL_INSTALLED_MODULE_STEM := fs_config_files
LOCAL_MODULE_PATH := $(TARGET_OUT_SYSTEM_EXT)/etc
include $(BUILD_SYSTEM)/base_rules.mk
$(LOCAL_BUILT_MODULE): PRIVATE_ANDROID_FS_HDR := $(system_android_filesystem_config)
$(LOCAL_BUILT_MODULE): PRIVATE_ANDROID_CAP_HDR := $(system_capability_header)
$(LOCAL_BUILT_MODULE): PRIVATE_TARGET_FS_CONFIG_GEN := $(TARGET_FS_CONFIG_GEN)
$(LOCAL_BUILT_MODULE): $(LOCAL_PATH)/fs_config_generator.py $(TARGET_FS_CONFIG_GEN) $(system_android_filesystem_config) $(system_capability_header)
	@mkdir -p $(dir $@)
	$< fsconfig \
	   --aid-header $(PRIVATE_ANDROID_FS_HDR) \
	   --capability-header $(PRIVATE_ANDROID_CAP_HDR) \
	   --partition system_ext \
	   --files \
	   --out_file $@ \
	   $(or $(PRIVATE_TARGET_FS_CONFIG_GEN),/dev/null)
endif

system_android_filesystem_config :=
system_capability_header :=
fs_config_generate_extra_partition_list :=
