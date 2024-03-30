LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_SRC_FILES := $(call all-c-files-under, ncurses/tty)
LOCAL_SRC_FILES += $(call all-c-files-under, ncurses/base)
LOCAL_SRC_FILES := $(filter-out ncurses/base/lib_driver.c ncurses/base/sigaction.c, $(LOCAL_SRC_FILES))
LOCAL_SRC_FILES += $(call all-c-files-under, ncurses/tinfo)
LOCAL_SRC_FILES := $(filter-out ncurses/tinfo/doalloc.c ncurses/tinfo/make_keys.c ncurses/tinfo/tinfo_driver.c, $(LOCAL_SRC_FILES))

LOCAL_SRC_FILES += \
	ncurses/codes.c \
	ncurses/comp_captab.c \
	ncurses/comp_userdefs.c \
	ncurses/expanded.c \
	ncurses/fallback.c \
	ncurses/lib_gen.c \
	ncurses/lib_keyname.c \
	ncurses/names.c \
	ncurses/unctrl.c \
	ncurses/trace/lib_trace.c \
	ncurses/trace/varargs.c \
	ncurses/trace/visbuf.c

LOCAL_CFLAGS := \
	-DHAVE_CONFIG_H \
	-U_XOPEN_SOURCE \
	-D_XOPEN_SOURCE=500 \
	-U_POSIX_C_SOURCE  \
	-D_POSIX_C_SOURCE=199506L \
	-DNDEBUG

LOCAL_CFLAGS += \
	-Wno-unused-parameter

LOCAL_C_INCLUDES := \
	$(LOCAL_PATH) \
	$(LOCAL_PATH)/include \
	$(LOCAL_PATH)/ncurses

LOCAL_MODULE_TAGS := optional
LOCAL_MODULE := libncurses

LOCAL_SYSTEM_EXT_MODULE := true

include $(BUILD_SHARED_LIBRARY)

# Copy only basic terminal type definitions as ncurses-base in debian does by default.
# https://salsa.debian.org/debian/ncurses/-/blob/master/debian/rules#L179
TERMINFO_FILES := \
	a/ansi c/cons25 c/cons25-debian c/cygwin d/dumb h/hurd l/linux \
	m/mach m/mach-bold m/mach-color m/mach-gnu m/mach-gnu-color p/pcansi \
	r/rxvt r/rxvt-basic \
	s/screen s/screen-256color s/screen-256color-bce s/screen-bce \
	s/screen-s s/screen-w s/screen.xterm-256color s/sun t/tmux \
	t/tmux-256color v/vt100 v/vt102 v/vt220 v/vt52 w/wsvt25 w/wsvt25m \
	x/xterm x/xterm-256color x/xterm-color x/xterm-mono \
	x/xterm-r5 x/xterm-r6 x/xterm-vt220 x/xterm-xfree86 E/Eterm \
	E/Eterm-color

TERMINFO_SOURCE := $(LOCAL_PATH)/lib/terminfo
TERMINFO_TARGET := $(TARGET_OUT_SYSTEM_EXT_ETC)/terminfo
$(TERMINFO_TARGET): $(ACP)
	@echo "copy terminfo to /etc/"
	@mkdir -p $@
	@$(foreach TERMINFO_FILE,$(TERMINFO_FILES), \
		mkdir -p $@/$(dir $(TERMINFO_FILE)); \
		$(ACP) $(TERMINFO_SOURCE)/$(TERMINFO_FILE) $@/$(TERMINFO_FILE); \
	)
ALL_DEFAULT_INSTALLED_MODULES += $(TERMINFO_TARGET)
