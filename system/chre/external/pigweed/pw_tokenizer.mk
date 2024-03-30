#
# Makefile for Pigweed's tokenizer module
#

# Environment Checks
ifeq ($(ANDROID_BUILD_TOP),)
$(error "You should supply an ANDROID_BUILD_TOP environment variable \
         containing a path to the Android source tree. This is typically \
         provided by initializing the Android build environment.")
endif

# Location of various Pigweed modules
PIGWEED_DIR = $(ANDROID_BUILD_TOP)/external/pigweed

# Pigweed source files
COMMON_SRCS += $(PIGWEED_DIR)/pw_tokenizer/encode_args.cc
COMMON_SRCS += $(PIGWEED_DIR)/pw_tokenizer/tokenize_to_global_handler_with_payload.cc
COMMON_SRCS += $(PIGWEED_DIR)/pw_varint/varint.cc

# Pigweed include paths
COMMON_CFLAGS += -I$(PIGWEED_DIR)/pw_containers/public
COMMON_CFLAGS += -I$(PIGWEED_DIR)/pw_polyfill/public
COMMON_CFLAGS += -I$(PIGWEED_DIR)/pw_polyfill/standard_library_public
COMMON_CFLAGS += -I$(PIGWEED_DIR)/pw_preprocessor/public
COMMON_CFLAGS += -I$(PIGWEED_DIR)/pw_span/public
COMMON_CFLAGS += -I$(PIGWEED_DIR)/pw_span/public_overrides
COMMON_CFLAGS += -I$(PIGWEED_DIR)/pw_tokenizer/public
COMMON_CFLAGS += -I$(PIGWEED_DIR)/pw_varint/public/
