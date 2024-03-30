/*
 * Copyright 2021 Google LLC
 * SPDX-License-Identifier: MIT
 */

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>
#include <stdlib.h>

#include "os/os_misc.h"
#include "virglrenderer.h"
#include "virglrenderer_hw.h"

int
LLVMFuzzerTestOneInput(const uint8_t *data, size_t size);

struct fuzz_renderer {
   bool initialized;
};

static void
fuzz_atexit_callback(void)
{
   virgl_renderer_cleanup(NULL);
}

static void
fuzz_debug_callback(UNUSED const char *fmt, UNUSED va_list ap)
{
   /* no logging */
}

static struct fuzz_renderer *
fuzz_renderer_get(void)
{
   static struct fuzz_renderer renderer;
   if (renderer.initialized)
      return &renderer;

   int ret =
      virgl_renderer_init(NULL, VIRGL_RENDERER_VENUS | VIRGL_RENDERER_NO_VIRGL, NULL);
   if (ret)
      abort();

   virgl_set_debug_callback(fuzz_debug_callback);

   atexit(fuzz_atexit_callback);

   renderer.initialized = true;
   return &renderer;
}

static uint32_t
fuzz_context_create(UNUSED struct fuzz_renderer *renderer)
{
   const uint32_t ctx_id = 1;
   const char name[] = "virgl_venus_fuzzer";
   int ret = virgl_renderer_context_create_with_flags(ctx_id, VIRGL_RENDERER_CAPSET_VENUS,
                                                      sizeof(name), name);
   if (ret)
      abort();

   return ctx_id;
}

static void
fuzz_context_destroy(UNUSED struct fuzz_renderer *renderer, uint32_t ctx_id)
{
   virgl_renderer_context_destroy(ctx_id);
}

static void
fuzz_context_submit(UNUSED struct fuzz_renderer *renderer,
                    uint32_t ctx_id,
                    const uint8_t *data,
                    size_t size)
{
   virgl_renderer_submit_cmd((void *)data, ctx_id, size / 4);
}

int
LLVMFuzzerTestOneInput(const uint8_t *data, size_t size)
{
   struct fuzz_renderer *renderer = fuzz_renderer_get();

   const uint32_t ctx_id = fuzz_context_create(renderer);
   fuzz_context_submit(renderer, ctx_id, data, size);
   fuzz_context_destroy(renderer, ctx_id);

   return 0;
}
