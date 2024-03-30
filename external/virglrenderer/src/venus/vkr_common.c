/*
 * Copyright 2021 Google LLC
 * SPDX-License-Identifier: MIT
 */

#include "vkr_common.h"

#include <stdarg.h>
#include <stdio.h>

#include "vkr_context.h"
#include "vkr_cs.h"

void
vkr_log(const char *fmt, ...)
{
   const char prefix[] = "vkr: ";
   char line[1024];
   size_t len;
   va_list va;
   int ret;

   len = ARRAY_SIZE(prefix) - 1;
   memcpy(line, prefix, len);

   va_start(va, fmt);
   ret = vsnprintf(line + len, ARRAY_SIZE(line) - len, fmt, va);
   va_end(va);

   if (ret < 0) {
      const char log_error[] = "log error";
      memcpy(line + len, log_error, ARRAY_SIZE(log_error) - 1);
      len += ARRAY_SIZE(log_error) - 1;
   } else if ((size_t)ret < ARRAY_SIZE(line) - len) {
      len += ret;
   } else {
      len = ARRAY_SIZE(line) - 1;
   }

   /* make room for newline */
   if (len + 1 >= ARRAY_SIZE(line))
      len--;

   line[len++] = '\n';
   line[len] = '\0';

   virgl_log(line);
}

void
object_array_fini(struct object_array *arr)
{
   if (!arr->objects_stolen) {
      for (uint32_t i = 0; i < arr->count; i++)
         free(arr->objects[i]);
   }

   free(arr->objects);
   free(arr->handle_storage);
}

bool
object_array_init(struct vkr_context *ctx,
                  struct object_array *arr,
                  uint32_t count,
                  VkObjectType obj_type,
                  size_t obj_size,
                  size_t handle_size,
                  const void *obj_id_handles)
{
   arr->count = count;

   arr->objects = malloc(sizeof(*arr->objects) * count);
   if (!arr->objects)
      return false;

   arr->handle_storage = malloc(handle_size * count);
   if (!arr->handle_storage) {
      free(arr->objects);
      return false;
   }

   arr->objects_stolen = false;
   for (uint32_t i = 0; i < count; i++) {
      const void *obj_id_handle = (const char *)obj_id_handles + handle_size * i;
      struct vkr_object *obj =
         vkr_context_alloc_object(ctx, obj_size, obj_type, obj_id_handle);
      if (!obj) {
         arr->count = i;
         object_array_fini(arr);
         return false;
      }

      arr->objects[i] = obj;
   }

   return arr;
}
