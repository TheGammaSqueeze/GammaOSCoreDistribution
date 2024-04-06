LOCAL_PATH:= $(call my-dir)

include $(CLEAR_VARS)

ifeq (1, $(strip $(shell expr $(PLATFORM_SDK_VERSION) \<= 27)))
LOCAL_CPPFLAGS += -DWFD_HDCP_SUPPORT
endif

#LOCAL_CPPFLAGS += -DLINUX_DRM_SUPPORT
LOCAL_CPPFLAGS += -DMS_CURSOR_SUPPORT -DPLATFORM_SDK_VERSION=$(PLATFORM_SDK_VERSION) -DPLATFORM_VERSION=$(PLATFORM_VERSION)
#LOCAL_LDFLAGS += -fsanitize=undefined

LOCAL_SRC_FILES:= \
        LinearRegression.cpp       \
        RTPSink.cpp                \
        TunnelRenderer.cpp         \
        WifiDisplaySink.cpp        \
        ANetworkSession.cpp        \
        ParsedMessage.cpp          \
        wfdsink_jni.cpp                    

#LOCAL_SRC_FILES +=     \
#        HdmiEdid.cpp       \

LOCAL_C_INCLUDES:= \
        $(TOP)/frameworks/av/media/libstagefright \
        $(TOP)/frameworks/native/include/media/openmax \
        $(TOP)/frameworks/native/include/media/hardware \
        $(TOP)/frameworks/base/core/jni/include  \

ifeq (1, $(strip $(shell expr $(PLATFORM_VERSION) \<= 12)))
LOCAL_C_INCLUDES += \
	$(TOP)/frameworks/av/media/libstagefright/mpeg2ts
else
LOCAL_C_INCLUDES += \
	$(TOP)/frameworks/av/media/libstagefright/mpeg2ts/include/mpeg2ts
endif

LOCAL_SHARED_LIBRARIES:= \
        libbinder                       \
        libcutils                       \
        liblog                          \
        libgui                          \
        libmedia                        \
        libstagefright                  \
        libstagefright_foundation       \
        libui                           \
        libutils                        \
        libandroid_runtime              \
        libnativehelper                 \

#LOCAL_C_INCLUDES: += \
#        $(TOP)/external/libdrm

#LOCAL_SHARED_LIBRARIES +=   \
#        libdrm

#LOCAL_CFLAGS += -Wno-multichar -Werror -Wall
ifeq (1, $(strip $(shell expr $(PLATFORM_SDK_VERSION) \>= 24)))
LOCAL_CLANG := true
LOCAL_SANITIZE := signed-integer-overflow
endif

LOCAL_MODULE:= libwfdsink_jni

LOCAL_MODULE_TAGS:= optional

include $(BUILD_SHARED_LIBRARY)

