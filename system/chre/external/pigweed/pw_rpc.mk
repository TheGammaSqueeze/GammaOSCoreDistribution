#
# Makefile for Pigweed's RPC module
#
# NOTE: In order to use this, you *must* have the following:
# - Installed mypy-protobuf and protoc
# - nanopb-c git repo checked out
#

ifneq ($(PW_RPC_SRCS),)

# Environment Checks ###########################################################

# Location of various Pigweed modules
PIGWEED_DIR = $(ANDROID_BUILD_TOP)/external/pigweed
CHRE_UTIL_DIR = $(ANDROID_BUILD_TOP)/system/chre/util
PIGWEED_CHRE_UTIL_DIR = $(CHRE_UTIL_DIR)/pigweed

ifeq ($(NANOPB_PREFIX),)
$(error "PW_RPC_SRCS is non-empty. You must supply a NANOPB_PREFIX environment \
         variable containing a path to the nanopb project. Example: \
         export NANOPB_PREFIX=$$HOME/path/to/nanopb/nanopb-c")
endif

ifeq ($(PROTOC),)
PROTOC=protoc
endif

PW_RPC_GEN_PATH = $(OUT)/pw_rpc_gen

# Create proto used for header generation ######################################

PW_RPC_PROTO_GENERATOR = $(PIGWEED_DIR)/pw_protobuf_compiler/py/pw_protobuf_compiler/generate_protos.py
PW_RPC_GENERATOR_PROTO_SRCS = $(PIGWEED_DIR)/pw_rpc/internal/packet.proto
PW_RPC_GENERATOR_COMPILED_PROTO = $(PW_RPC_GEN_PATH)/py/pw_rpc/internal/packet_pb2.py

# Modifies PYTHONPATH so that python can see all of pigweed's modules used by
# their protoc plugins
PW_RPC_GENERATOR_CMD = PYTHONPATH=$$PYTHONPATH:$(PW_RPC_GEN_PATH)/py:$\
  $(PIGWEED_DIR)/pw_status/py:$(PIGWEED_DIR)/pw_protobuf/py:$\
  $(PIGWEED_DIR)/pw_protobuf_compiler/py python3

$(PW_RPC_GENERATOR_COMPILED_PROTO): $(PW_RPC_GENERATOR_PROTO_SRCS)
	@echo " [PW_RPC] $<"
	$(V)mkdir -p $(PW_RPC_GEN_PATH)/py/
	$(V)cp -R $(PIGWEED_DIR)/pw_rpc/py/pw_rpc $(PW_RPC_GEN_PATH)/py/
	$(V)$(PW_RPC_GENERATOR_CMD) $(PW_RPC_PROTO_GENERATOR) --out-dir=$(PW_RPC_GEN_PATH)/py/pw_rpc/internal \
	  --compile-dir=$(dir $<) --sources $(PW_RPC_GENERATOR_PROTO_SRCS) \
	  --language python
	$(V)$(PW_RPC_GENERATOR_CMD) $(PW_RPC_PROTO_GENERATOR) --out-dir=$(PW_RPC_GEN_PATH)/$(dir $<) \
	  --plugin-path=$(PIGWEED_DIR)/pw_protobuf/py/pw_protobuf/plugin.py \
	  --compile-dir=$(dir $<) --sources $(PW_RPC_GENERATOR_PROTO_SRCS) \
	  --language pwpb

# Generated PW RPC Files #######################################################

PW_RPC_GEN_SRCS = $(patsubst %.proto, \
                             $(PW_RPC_GEN_PATH)/%.pb.c, \
                             $(PW_RPC_SRCS))

# Include to-be-generated files
COMMON_CFLAGS += -I$(PW_RPC_GEN_PATH)
COMMON_CFLAGS += -I$(PW_RPC_GEN_PATH)/$(PIGWEED_DIR)
COMMON_CFLAGS += $(addprefix -I$(PW_RPC_GEN_PATH)/, $(PW_RPC_INCLUDES))

COMMON_SRCS += $(PW_RPC_GEN_SRCS)

# PW RPC library ###############################################################

# Pigweed RPC include paths
COMMON_CFLAGS += -I$(PIGWEED_DIR)/pw_assert/assert_lite_public_overrides
COMMON_CFLAGS += -I$(PIGWEED_DIR)/pw_assert/public
COMMON_CFLAGS += -I$(PIGWEED_DIR)/pw_assert_log/public
COMMON_CFLAGS += -I$(PIGWEED_DIR)/pw_assert_log/public_overrides
COMMON_CFLAGS += -I$(PIGWEED_DIR)/pw_bytes/public
COMMON_CFLAGS += -I$(PIGWEED_DIR)/pw_containers/public
COMMON_CFLAGS += -I$(PIGWEED_DIR)/pw_function/public
COMMON_CFLAGS += -I$(PIGWEED_DIR)/pw_log/public
COMMON_CFLAGS += -I$(PIGWEED_DIR)/pw_log_null/public
COMMON_CFLAGS += -I$(PIGWEED_DIR)/pw_log_null/public_overrides
COMMON_CFLAGS += -I$(PIGWEED_DIR)/pw_polyfill/public
COMMON_CFLAGS += -I$(PIGWEED_DIR)/pw_polyfill/public_overrides
COMMON_CFLAGS += -I$(PIGWEED_DIR)/pw_polyfill/standard_library_public
COMMON_CFLAGS += -I$(PIGWEED_DIR)/pw_preprocessor/public
COMMON_CFLAGS += -I$(PIGWEED_DIR)/pw_protobuf/public
COMMON_CFLAGS += -I$(PIGWEED_DIR)/pw_result/public
COMMON_CFLAGS += -I$(PIGWEED_DIR)/pw_rpc/
COMMON_CFLAGS += -I$(PIGWEED_DIR)/pw_rpc/nanopb/public
COMMON_CFLAGS += -I$(PIGWEED_DIR)/pw_rpc/public
COMMON_CFLAGS += -I$(PIGWEED_DIR)/pw_rpc/raw/public
COMMON_CFLAGS += -I$(PIGWEED_DIR)/pw_span/public
COMMON_CFLAGS += -I$(PIGWEED_DIR)/pw_span/public_overrides
COMMON_CFLAGS += -I$(PIGWEED_DIR)/pw_status/public
COMMON_CFLAGS += -I$(PIGWEED_DIR)/pw_stream/public
COMMON_CFLAGS += -I$(PIGWEED_DIR)/pw_sync/public
COMMON_CFLAGS += -I$(PIGWEED_DIR)/pw_varint/public

# Pigweed RPC sources
COMMON_SRCS += $(PIGWEED_DIR)/pw_assert_log/assert_log.cc
COMMON_SRCS += $(PIGWEED_DIR)/pw_containers/intrusive_list.cc
COMMON_SRCS += $(PIGWEED_DIR)/pw_protobuf/decoder.cc
COMMON_SRCS += $(PIGWEED_DIR)/pw_protobuf/encoder.cc
COMMON_SRCS += $(PIGWEED_DIR)/pw_rpc/call.cc
COMMON_SRCS += $(PIGWEED_DIR)/pw_rpc/channel.cc
COMMON_SRCS += $(PIGWEED_DIR)/pw_rpc/client.cc
COMMON_SRCS += $(PIGWEED_DIR)/pw_rpc/client_call.cc
COMMON_SRCS += $(PIGWEED_DIR)/pw_rpc/client_server.cc
COMMON_SRCS += $(PIGWEED_DIR)/pw_rpc/endpoint.cc
COMMON_SRCS += $(PIGWEED_DIR)/pw_rpc/packet.cc
COMMON_SRCS += $(PIGWEED_DIR)/pw_rpc/server.cc
COMMON_SRCS += $(PIGWEED_DIR)/pw_rpc/server_call.cc
COMMON_SRCS += $(PIGWEED_DIR)/pw_rpc/service.cc
COMMON_SRCS += $(PIGWEED_DIR)/pw_rpc/nanopb/common.cc
COMMON_SRCS += $(PIGWEED_DIR)/pw_rpc/nanopb/method.cc
COMMON_SRCS += $(PIGWEED_DIR)/pw_rpc/nanopb/server_reader_writer.cc
COMMON_SRCS += $(PIGWEED_DIR)/pw_stream/memory_stream.cc
COMMON_SRCS += $(PIGWEED_DIR)/pw_varint/varint.cc

# NanoPB header includes
COMMON_CFLAGS += -I$(NANOPB_PREFIX)

# NanoPB sources
COMMON_SRCS += $(NANOPB_PREFIX)/pb_common.c
COMMON_SRCS += $(NANOPB_PREFIX)/pb_decode.c
COMMON_SRCS += $(NANOPB_PREFIX)/pb_encode.c

# Add CHRE Pigweed util sources since nanoapps should always use these
COMMON_SRCS += $(PIGWEED_CHRE_UTIL_DIR)/chre_channel_output.cc
COMMON_SRCS += $(CHRE_UTIL_DIR)/nanoapp/callbacks.cc

# Generate PW RPC headers ######################################################

$(PW_RPC_GEN_PATH)/%.pb.c \
        $(PW_RPC_GEN_PATH)/%.pb.h \
        $(PW_RPC_GEN_PATH)/%.rpc.pb.h \
        $(PW_RPC_GEN_PATH)/%.raw_rpc.pb.h: %.proto \
                                           %.options \
                                           $(NANOPB_GENERATOR_SRCS) \
                                           $(PW_RPC_GENERATOR_COMPILED_PROTO)
	@echo " [PW_RPC] $<"
	$(V)$(PW_RPC_GENERATOR_CMD) $(PW_RPC_PROTO_GENERATOR) \
	  --plugin-path=$(NANOPB_PROTOC) \
	  --out-dir=$(PW_RPC_GEN_PATH)/$(dir $<) --compile-dir=$(dir $<) --language nanopb \
	  --sources $<
	$(V)$(PW_RPC_GENERATOR_CMD) $(PW_RPC_PROTO_GENERATOR) \
	  --plugin-path=$(PIGWEED_DIR)/pw_rpc/py/pw_rpc/plugin_nanopb.py \
	  --out-dir=$(PW_RPC_GEN_PATH)/$(dir $<) --compile-dir=$(dir $<) --language nanopb_rpc \
	  --sources $<
	$(V)$(PW_RPC_GENERATOR_CMD) $(PW_RPC_PROTO_GENERATOR) \
	  --plugin-path=$(PIGWEED_DIR)/pw_rpc/py/pw_rpc/plugin_raw.py \
	  --out-dir=$(PW_RPC_GEN_PATH)/$(dir $<) --compile-dir=$(dir $<) --language raw_rpc \
	  --sources $<

endif