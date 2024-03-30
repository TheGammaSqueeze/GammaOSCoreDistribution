#
# RPC Service Test Nanoapp Makefile
#
# Environment Checks ###########################################################
ifeq ($(CHRE_PREFIX),)
  ifneq ($(ANDROID_BUILD_TOP),)
    CHRE_PREFIX = $(ANDROID_BUILD_TOP)/system/chre
  else
    $(error "You must run 'lunch' to setup ANDROID_BUILD_TOP, or explicitly \
    define the CHRE_PREFIX environment variable to point to the CHRE root \
    directory.")
  endif
endif

# Nanoapp Configuration ########################################################

NANOAPP_NAME = rpc_service_test
NANOAPP_ID = 0x476f6f675400000b
NANOAPP_NAME_STRING = \"RPC\ Service\ Test\"
NANOAPP_VERSION = 0x00000001

NANOAPP_PATH = $(CHRE_PREFIX)/apps/test/common/rpc_service_test
TEST_SHARED_PATH = $(CHRE_PREFIX)/apps/test/common/shared

# TODO(b/210138227) Enable PW RPC by default once build system is fixed
NANOAPP_PW_RPC_ENABLED = false

# Source Code ##################################################################

COMMON_SRCS += $(NANOAPP_PATH)/src/rpc_service_manager.cc
COMMON_SRCS += $(NANOAPP_PATH)/src/rpc_service_test.cc

# Compiler Flags ###############################################################

# Defines
COMMON_CFLAGS += -DNANOAPP_MINIMUM_LOG_LEVEL=CHRE_LOG_LEVEL_INFO

# Includes
COMMON_CFLAGS += -I$(TEST_SHARED_PATH)/inc
COMMON_CFLAGS += -I$(NANOAPP_PATH)/inc

# PW RPC protos ################################################################

ifeq (NANOAPP_PW_RPC_ENABLED, true)
PW_RPC_SRCS = $(ANDROID_BUILD_TOP)/external/pigweed/pw_rpc/echo.proto
COMMON_CFLAGS += -DPW_RPC_SERVICE_ENABLED
endif

# Makefile Includes ############################################################

include $(CHRE_PREFIX)/build/nanoapp/app.mk
