#
# Nanoapp/CHRE NanoPB Makefile
#
# Include this file in your nanoapp Makefile to produce pb.c and pb.h (or
# $NANOPB_EXTENSION.c and $NANOPB_EXTENSION.h if $NANOPB_EXTENSION is defined)
# for .proto files specified in the NANOPB_SRCS variable. The produced pb.c or
# $NANOPB_EXTENSION.c files are automatically added to the COMMON_SRCS variable
# for the nanoapp build.
#
# The NANOPB_OPTIONS variable can be used to supply an .options file to use when
# generating code for all .proto files. Alternatively, if an .options file has
# the same name as a .proto file in NANOPB_SRCS, it'll be automatically picked
# up when generating code **only** for that .proto file.
#
# NANOPB_FLAGS can be used to supply additional command line arguments to the
# nanopb compiler. Note that this is global and applies to all protobuf
# generated source.
#
# NANOPB_INCLUDES may optionally be used to automatically add one or more
# include path prefixes for C/C++ source and .proto files. For example, if the
# file myprefix/proto/foo.proto is added to NANOPB_SRCS, but you'd like to use
# #include "proto/foo.pb.h" in your source (rather than myprefix/proto/foo.pb.h)
# and/or import "proto/foo.proto" in your .proto files, then set NANOPB_INCLUDES
# to myprefix.

# Environment Checks ###########################################################

ifneq ($(NANOPB_SRCS),)
ifeq ($(NANOPB_PREFIX),)
$(error "NANOPB_SRCS is non-empty. You must supply a NANOPB_PREFIX environment \
         variable containing a path to the nanopb project. Example: \
         export NANOPB_PREFIX=$$HOME/path/to/nanopb/nanopb-c")
endif
endif

ifeq ($(PROTOC),)
PROTOC=protoc
endif

# Generated Source Files #######################################################

NANOPB_GEN_PATH = $(OUT)/nanopb_gen

ifeq ($(NANOPB_EXTENSION),)
NANOPB_EXTENSION = pb
else
NANOPB_GENERATOR_FLAGS = --extension=.$(NANOPB_EXTENSION)
endif

NANOPB_GEN_SRCS += $(patsubst %.proto, \
                              $(NANOPB_GEN_PATH)/%.$(NANOPB_EXTENSION).c, \
                              $(NANOPB_SRCS))

ifneq ($(NANOPB_GEN_SRCS),)
COMMON_CFLAGS += -I$(NANOPB_PREFIX)
COMMON_CFLAGS += -I$(NANOPB_GEN_PATH)
COMMON_CFLAGS += $(addprefix -I$(NANOPB_GEN_PATH)/, $(NANOPB_INCLUDES))

ifneq ($(NANOPB_INCLUDE_LIBRARY),false)
COMMON_SRCS += $(NANOPB_PREFIX)/pb_common.c
COMMON_SRCS += $(NANOPB_PREFIX)/pb_decode.c
COMMON_SRCS += $(NANOPB_PREFIX)/pb_encode.c
endif

endif

# NanoPB Compiler Flags ########################################################

ifneq ($(NANOPB_GEN_SRCS),)
ifneq ($(NANOPB_INCLUDE_LIBRARY),false)
COMMON_CFLAGS += -DPB_NO_PACKED_STRUCTS=1
endif
endif

# NanoPB Generator Setup #######################################################

NANOPB_GENERATOR_SRCS = $(NANOPB_PREFIX)/generator/proto/nanopb_pb2.py
NANOPB_GENERATOR_SRCS += $(NANOPB_PREFIX)/generator/proto/plugin_pb2.py

$(NANOPB_GENERATOR_SRCS):
	cd $(NANOPB_PREFIX)/generator/proto && make

ifneq ($(NANOPB_OPTIONS),)
NANOPB_OPTIONS_FLAG = --options-file=$(NANOPB_OPTIONS)
else
NANOPB_OPTIONS_FLAG =
endif

NANOPB_FLAGS += $(addprefix --proto_path=, $(abspath $(NANOPB_INCLUDES)))

# Generate NanoPB Sources ######################################################

COMMON_SRCS += $(NANOPB_GEN_SRCS)

NANOPB_PROTOC = $(NANOPB_PREFIX)/generator/protoc-gen-nanopb

$(NANOPB_GEN_PATH)/%.$(NANOPB_EXTENSION).c \
        $(NANOPB_GEN_PATH)/%.$(NANOPB_EXTENSION).h: %.proto \
                                                    %.options \
                                                    $(NANOPB_GENERATOR_SRCS)
	@echo " [NANOPB] $<"
	$(V)mkdir -p $(dir $@)
	$(V)$(PROTOC) --plugin=protoc-gen-nanopb=$(NANOPB_PROTOC) \
	  --proto_path=$(abspath $(dir $<)) \
	  $(NANOPB_FLAGS) \
	  --nanopb_out="$(NANOPB_GENERATOR_FLAGS) --options-file=$(basename $<).options:$(dir $@)" \
	  $(abspath $<)

$(NANOPB_GEN_PATH)/%.$(NANOPB_EXTENSION).c \
        $(NANOPB_GEN_PATH)/%.$(NANOPB_EXTENSION).h: %.proto \
                                                    $(NANOPB_OPTIONS) \
                                                    $(NANOPB_GENERATOR_SRCS)
	@echo " [NANOPB] $<"
	$(V)mkdir -p $(dir $@)
	$(V)$(PROTOC) --plugin=protoc-gen-nanopb=$(NANOPB_PROTOC) \
	  --proto_path=$(abspath $(dir $<)) \
	  $(NANOPB_FLAGS) \
	  --nanopb_out="$(NANOPB_GENERATOR_FLAGS) $(NANOPB_OPTIONS_FLAG):$(dir $@)" \
	  $(abspath $<)
