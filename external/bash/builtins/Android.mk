LOCAL_PATH := $(call my-dir)

# ========================================================
# libbuiltins.a
# ========================================================
include $(CLEAR_VARS)

LOCAL_SRC_FILES:= \
	alias.c \
	bashgetopt.c \
	bind.c \
	break.c \
	builtin.c \
	builtins.c \
	caller.c \
	cd.c \
	colon.c \
	command.c \
	common.c \
	complete.c \
	declare.c \
	echo.c \
	enable.c \
	eval.c \
	evalfile.c \
	evalstring.c \
	exec.c \
	exit.c \
	fc.c \
	fg_bg.c \
	gen-helpfiles.c \
	getopt.c \
	getopts.c \
	hash.c \
	help.c \
	history.c \
	inlib.c \
	jobs.c \
	kill.c \
	let.c \
	mapfile.c \
	printf.c \
	psize.c \
	pushd.c \
	read.c \
	return.c \
	setattr.c \
	set.c \
	shift.c \
	shopt.c \
	source.c \
	suspend.c \
	test.c \
	times.c \
	tmpbuiltins.c \
	trap.c \
	type.c \
	ulimit.c \
	umask.c \
	wait.c

LOCAL_C_INCLUDES += \
	$(LOCAL_PATH)/../ \
	$(LOCAL_PATH)/../include \
	$(LOCAL_PATH)/../lib
LOCAL_CFLAGS += \
	-DHAVE_CONFIG_H \
	-DPROGRAM=\"bash\" \
	-DCONF_HOSTTYPE=\"arm\" \
	-DCONF_OSTYPE=\"android-eabi\" \
	-DCONF_MACHTYPE=\"arm-android-eabi\" \
	-DCONF_VENDOR=\"unknown\"
LOCAL_CFLAGS += \
	-Wno-implicit-function-declaration \
	-Wno-incompatible-pointer-types-discards-qualifiers \
	-Wno-knr-promoted-parameter \
	-Wno-parentheses \
	-Wno-sign-compare \
	-Wno-sometimes-uninitialized \
	-Wno-unused-function \
	-Wno-unused-parameter \
	-Wno-unused-variable

LOCAL_MODULE_TAGS := optional
LOCAL_MODULE := libbuiltins
include $(BUILD_STATIC_LIBRARY)

# ========================================================
include $(call all-makefiles-under,$(LOCAL_PATH))
