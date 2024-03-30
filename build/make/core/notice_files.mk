###########################################################
## Track NOTICE files
###########################################################
$(call record-module-type,NOTICE_FILE)

ifneq ($(LOCAL_NOTICE_FILE),)
notice_file:=$(strip $(LOCAL_NOTICE_FILE))
else
notice_file:=$(strip $(wildcard $(LOCAL_PATH)/LICENSE $(LOCAL_PATH)/LICENCE $(LOCAL_PATH)/NOTICE))
endif

ifneq (,$(strip $(LOCAL_LICENSE_PACKAGE_NAME)))
license_package_name:=$(strip $(LOCAL_LICENSE_PACKAGE_NAME))
endif

ifneq (,$(strip $(LOCAL_LICENSE_INSTALL_MAP)))
install_map:=$(strip $(LOCAL_LICENSE_INSTALL_MAP))
else
install_map:=
endif

ifneq (,$(strip $(LOCAL_LICENSE_KINDS)))
license_kinds:=$(strip $(LOCAL_LICENSE_KINDS))
else
license_kinds:=legacy_by_exception_only
endif

ifneq (,$(strip $(LOCAL_LICENSE_CONDITIONS)))
license_conditions:=$(strip $(LOCAL_LICENSE_CONDITIONS))
else
license_conditions:=by_exception_only
endif

ifeq ($(LOCAL_MODULE_CLASS),GYP)
  # We ignore NOTICE files for modules of type GYP.
  notice_file :=
endif

ifeq ($(LOCAL_MODULE_CLASS),FAKE)
  # We ignore NOTICE files for modules of type FAKE.
  notice_file :=
endif

# Soong generates stub libraries that don't need NOTICE files
ifdef LOCAL_NO_NOTICE_FILE
  ifneq ($(LOCAL_MODULE_MAKEFILE),$(SOONG_ANDROID_MK))
    $(call pretty-error,LOCAL_NO_NOTICE_FILE should not be used by Android.mk files)
  endif
  notice_file :=
endif

ifeq ($(LOCAL_MODULE_CLASS),NOTICE_FILES)
# If this is a NOTICE-only module, we don't include base_rule.mk,
# so my_prefix is not set at this point.
ifeq ($(LOCAL_IS_HOST_MODULE),true)
  my_prefix := HOST_
  LOCAL_HOST_PREFIX :=
else
  my_prefix := TARGET_
endif
endif

installed_notice_file :=

is_container:=$(strip $(LOCAL_MODULE_IS_CONTAINER))
ifeq (,$(is_container))
ifneq (,$(strip $(filter %.zip %.tar %.tgz %.tar.gz %.apk %.img %.srcszip %.apex, $(LOCAL_BUILT_MODULE))))
is_container:=true
else
is_container:=false
endif
else ifneq (,$(strip $(filter-out true false,$(is_container))))
$(error Unrecognized value '$(is_container)' for LOCAL_MODULE_IS_CONTAINER)
endif

ifeq (true,$(is_container))
# Include shared libraries' notices for "container" types, but not for binaries etc.
notice_deps := \
    $(strip \
        $(foreach d, \
            $(LOCAL_REQUIRED_MODULES) \
            $(LOCAL_STATIC_LIBRARIES) \
            $(LOCAL_WHOLE_STATIC_LIBRARIES) \
            $(LOCAL_SHARED_LIBRARIES) \
            $(LOCAL_DYLIB_LIBRARIES) \
            $(LOCAL_RLIB_LIBRARIES) \
            $(LOCAL_PROC_MACRO_LIBRARIES) \
            $(LOCAL_HEADER_LIBRARIES) \
            $(LOCAL_STATIC_JAVA_LIBRARIES) \
            $(LOCAL_JAVA_LIBRARIES) \
            $(LOCAL_JNI_SHARED_LIBRARIES) \
            ,$(subst :,_,$(d)):static \
        ) \
    )
else
notice_deps := \
    $(strip \
        $(foreach d, \
            $(LOCAL_REQUIRED_MODULES) \
            $(LOCAL_STATIC_LIBRARIES) \
            $(LOCAL_WHOLE_STATIC_LIBRARIES) \
            $(LOCAL_RLIB_LIBRARIES) \
            $(LOCAL_PROC_MACRO_LIBRARIES) \
            $(LOCAL_HEADER_LIBRARIES) \
            $(LOCAL_STATIC_JAVA_LIBRARIES) \
            ,$(subst :,_,$(d)):static \
        )$(foreach d, \
            $(LOCAL_SHARED_LIBRARIES) \
            $(LOCAL_DYLIB_LIBRARIES) \
            $(LOCAL_JAVA_LIBRARIES) \
            $(LOCAL_JNI_SHARED_LIBRARIES) \
            ,$(subst :,_,$(d)):dynamic \
        ) \
    )
endif
ifeq ($(LOCAL_IS_HOST_MODULE),true)
notice_deps := $(strip $(notice_deps) $(foreach d,$(LOCAL_HOST_REQUIRED_MODULES),$(subst :,_,$(d)):static))
else
notice_deps := $(strip $(notice_deps) $(foreach d,$(LOCAL_TARGET_REQUIRED_MODULES),$(subst :,_,$(d)):static))
endif

local_path := $(LOCAL_PATH)


module_license_metadata :=

ifdef my_register_name
  module_license_metadata := $(call local-intermediates-dir)/$(my_register_name).meta_lic

  $(foreach target,$(ALL_MODULES.$(my_register_name).BUILT) $(ALL_MODULES.$(my_register_name).INSTALLED) $(my_test_data) $(my_test_config),\
    $(eval ALL_TARGETS.$(target).META_LIC := $(module_license_metadata)))

  ALL_MODULES.$(my_register_name).META_LIC := $(strip $(ALL_MODULES.$(my_register_name).META_LIC) $(module_license_metadata))

  ifdef LOCAL_SOONG_LICENSE_METADATA
    # Soong modules have already produced a license metadata file, copy it to where Make expects it.
    $(eval $(call copy-one-file, $(LOCAL_SOONG_LICENSE_METADATA), $(module_license_metadata)))
  else
    # Make modules don't have enough information to produce a license metadata rule until after fix-notice-deps
    # has been called, store the necessary information until later.
    ALL_MODULES.$(my_register_name).DELAYED_META_LIC := $(strip $(ALL_MODULES.$(my_register_name).DELAYED_META_LIC) $(module_license_metadata))
    ALL_MODULES.$(my_register_name).LICENSE_PACKAGE_NAME := $(strip $(license_package_name))
    ALL_MODULES.$(my_register_name).MODULE_TYPE := $(strip $(ALL_MODULES.$(my_register_name).MODULE_TYPE) $(LOCAL_MODULE_TYPE))
    ALL_MODULES.$(my_register_name).MODULE_CLASS := $(strip $(ALL_MODULES.$(my_register_name).MODULE_CLASS) $(LOCAL_MODULE_CLASS))
    ALL_MODULES.$(my_register_name).LICENSE_KINDS := $(ALL_MODULES.$(my_register_name).LICENSE_KINDS) $(license_kinds)
    ALL_MODULES.$(my_register_name).LICENSE_CONDITIONS := $(ALL_MODULES.$(my_register_name).LICENSE_CONDITIONS) $(license_conditions)
    ALL_MODULES.$(my_register_name).LICENSE_INSTALL_MAP := $(ALL_MODULES.$(my_register_name).LICENSE_INSTALL_MAP) $(install_map)
    ALL_MODULES.$(my_register_name).NOTICE_DEPS := $(ALL_MODULES.$(my_register_name).NOTICE_DEPS) $(notice_deps)
    ALL_MODULES.$(my_register_name).IS_CONTAINER := $(strip $(filter-out false,$(ALL_MODULES.$(my_register_name).IS_CONTAINER) $(is_container)))
    ALL_MODULES.$(my_register_name).PATH := $(strip $(ALL_MODULES.$(my_register_name).PATH) $(local_path))
  endif
endif

ifdef notice_file

ifdef my_register_name
ALL_MODULES.$(my_register_name).NOTICES := $(ALL_MODULES.$(my_register_name).NOTICES) $(notice_file)
endif

endif  # notice_file
