LOCAL_PATH := $(call my-dir)

# ========================================================
# bash
# ========================================================
include $(CLEAR_VARS)

LOCAL_SRC_FILES:= \
	alias.c \
	array.c \
	arrayfunc.c \
	assoc.c \
	bashhist.c \
	bashline.c \
	bracecomp.c \
	braces.c \
	copy_cmd.c \
	dispose_cmd.c \
	error.c \
	eval.c \
	execute_cmd.c \
	expr.c \
	findcmd.c \
	flags.c \
	general.c \
	hashcmd.c \
	hashlib.c \
	input.c \
	jobs.c \
	list.c \
	locale.c \
	mailcheck.c \
	make_cmd.c \
	pathexp.c \
	pcomplete.c \
	pcomplib.c \
	print_cmd.c \
	redir.c \
	shell.c \
	sig.c \
	siglist.c \
	stringlib.c \
	subst.c \
	syntax.c \
	test.c \
	trap.c \
	unwind_prot.c \
	variables.c \
	version.c \
	xmalloc.c \
	y.tab.c

LOCAL_C_INCLUDES += \
	$(LOCAL_PATH) \
	$(LOCAL_PATH)/include \
	$(LOCAL_PATH)/lib
LOCAL_CFLAGS += \
	-DHAVE_CONFIG_H \
	-DPROGRAM=\"bash\" \
	-DPACKAGE=\"bash\" \
	-DCONF_HOSTTYPE=\"arm\" \
	-DCONF_OSTYPE=\"android-eabi\" \
	-DCONF_MACHTYPE=\"arm-android-eabi\" \
	-DCONF_VENDOR=\"unknown\" \
	-DLOCALEDIR=\"/data/locale\" \
	-DSHELL
LOCAL_SHARED_LIBRARIES += \
	libncurses
LOCAL_STATIC_LIBRARIES += \
	libbuiltins \
	libglob \
	libsh \
	libtilde \
	libhistory \
	libreadline
LOCAL_CFLAGS += \
	-Wno-char-subscripts \
	-Wno-format \
	-Wno-implicit-function-declaration \
	-Wno-incompatible-pointer-types \
	-Wno-incompatible-pointer-types-discards-qualifiers \
	-Wno-missing-field-initializers \
	-Wno-non-literal-null-conversion \
	-Wno-parentheses \
	-Wno-sign-compare \
	-Wno-sometimes-uninitialized \
	-Wno-switch \
	-Wno-unused-function \
	-Wno-unused-label \
	-Wno-unused-parameter \
	-Wno-unused-variable \
	-Wno-user-defined-warnings

LOCAL_MODULE := bash
LOCAL_MODULE_TAGS := optional

LOCAL_SYSTEM_EXT_MODULE := true

include $(BUILD_EXECUTABLE)

# ========================================================
# bash configs
# ========================================================
etc_files := $(wildcard $(LOCAL_PATH)/etc/*)

BASH_ETC := $(TARGET_OUT_SYSTEM_EXT_ETC)/$(LOCAL_MODULE)
BASH_CONFIGS := $(addprefix $(BASH_ETC)/,$(notdir $(etc_files)))
$(BASH_CONFIGS): $(BASH_ETC)/%: $(LOCAL_PATH)/etc/% | $(LOCAL_BUILT_MODULE)
	@echo "Install: $@ -> $(BASH_ETC)"
	@mkdir -p $(dir $@)
	$(hide) cp $< $@

ALL_DEFAULT_INSTALLED_MODULES += $(BASH_CONFIGS)

ALL_MODULES.$(LOCAL_MODULE).INSTALLED := \
    $(ALL_MODULES.$(LOCAL_MODULE).INSTALLED) $(BASH_CONFIGS)

# ========================================================
include $(call all-makefiles-under,$(LOCAL_PATH))
