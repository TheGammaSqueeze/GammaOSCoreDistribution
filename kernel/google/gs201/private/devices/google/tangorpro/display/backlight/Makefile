M ?= $(shell pwd)

KBUILD_OPTIONS += CONFIG_BACKLIGHT_RT4539=m

EXTRA_CFLAGS += -DDYNAMIC_DEBUG_MODULE=1
EXTRA_CFLAGS += -I$(KERNEL_SRC)/drivers/video/backlight
EXTRA_CFLAGS += -Werror

modules modules_install clean:
	$(MAKE) -C $(KERNEL_SRC) M=$(M) W=1 $(KBUILD_OPTIONS) EXTRA_CFLAGS="$(EXTRA_CFLAGS)" $(@)
