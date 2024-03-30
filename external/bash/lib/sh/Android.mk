LOCAL_PATH := $(call my-dir)

# ========================================================
# libsh.a
# ========================================================
include $(CLEAR_VARS)

LOCAL_SRC_FILES:= \
	casemod.c \
	clktck.c \
	clock.c \
	dprintf.c \
	eaccess.c \
	fmtullong.c \
	fmtulong.c \
	fmtumax.c \
	fnxform.c \
	fpurge.c \
	getcwd.c \
	getenv.c \
	gettimeofday.c \
	inet_aton.c \
	input_avail.c \
	itos.c \
	mailstat.c \
	makepath.c \
	mbscasecmp.c \
	mbschr.c \
	mbscmp.c \
	mktime.c \
	netconn.c \
	netopen.c \
	oslib.c \
	pathcanon.c \
	pathphys.c \
	random.c \
	rename.c \
	setlinebuf.c \
	shmatch.c \
	shmbchar.c \
	shquote.c \
	shtty.c \
	snprintf.c \
	spell.c \
	strcasecmp.c \
	strcasestr.c \
	strchrnul.c \
	strdup.c \
	strerror.c \
	strftime.c \
	stringlist.c \
	stringvec.c \
	strnlen.c \
	strpbrk.c \
	strstr.c \
	strtod.c \
	strtoimax.c \
	strtol.c \
	strtoll.c \
	strtoul.c \
	strtoull.c \
	strtoumax.c \
	strtrans.c \
	times.c \
	timeval.c \
	tmpfile.c \
	uconvert.c \
	ufuncs.c \
	unicode.c \
	utf8.c \
	vprint.c \
	wcsdup.c \
	wcsnwidth.c \
	wcswidth.c \
	winsize.c \
	zcatfd.c \
	zgetline.c \
	zmapfd.c \
	zread.c \
	zwrite.c

LOCAL_C_INCLUDES += \
	$(LOCAL_PATH)/../.. \
	$(LOCAL_PATH)/../../include \
	$(LOCAL_PATH)/../../lib \
	external/clearsilver \
	external/clearsilver/util/regex

LOCAL_CFLAGS += \
	-DHAVE_CONFIG_H

LOCAL_CFLAGS += \
	-Wno-missing-braces \
	-Wno-parentheses \
	-Wno-sign-compare \
	-Wno-unused-function \
	-Wno-unused-parameter \
	-Wno-unused-variable

LOCAL_MODULE := libsh

LOCAL_MODULE_TAGS := optional

include $(BUILD_STATIC_LIBRARY)

# ========================================================
include $(call all-makefiles-under,$(LOCAL_PATH))
