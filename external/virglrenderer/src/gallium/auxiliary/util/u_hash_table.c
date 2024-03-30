/**************************************************************************
 *
 * Copyright 2008 VMware, Inc.
 * All Rights Reserved.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a
 * copy of this software and associated documentation files (the
 * "Software"), to deal in the Software without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sub license, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:
 *
 * The above copyright notice and this permission notice (including the
 * next paragraph) shall be included in all copies or substantial portions
 * of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS
 * OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT.
 * IN NO EVENT SHALL VMWARE AND/OR ITS SUPPLIERS BE LIABLE FOR
 * ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,
 * TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 *
 **************************************************************************/

/**
 * @file
 * General purpose hash table implementation.
 * 
 * Just uses the cso_hash for now, but it might be better switch to a linear 
 * probing hash table implementation at some point -- as it is said they have 
 * better lookup and cache performance and it appears to be possible to write 
 * a lock-free implementation of such hash tables . 
 * 
 * @author José Fonseca <jfonseca@vmware.com>
 */


#include "pipe/p_compiler.h"
#include "util/u_debug.h"

#include "cso_cache/cso_hash.h"

#include "util/u_memory.h"
#include "util/u_hash_table.h"

#define XXH_INLINE_ALL
#include "xxhash.h"

struct util_hash_table
{
   struct cso_hash *cso;   
   
   /** Hash function */
   unsigned (*hash)(void *key);
   
   /** Compare two keys */
   int (*compare)(void *key1, void *key2);
   
   /** free value */
   void (*destroy)(void *value);
};


struct util_hash_table_item
{
   void *key;
   void *value;
};


static inline struct util_hash_table_item *
util_hash_table_item(struct cso_hash_iter iter)
{
   return (struct util_hash_table_item *)cso_hash_iter_data(iter);
}


struct util_hash_table *
util_hash_table_create(unsigned (*hash)(void *key),
                       int (*compare)(void *key1, void *key2),
                       void (*destroy)(void *value))
{
   struct util_hash_table *ht;
   
   ht = MALLOC_STRUCT(util_hash_table);
   if(!ht)
      return NULL;
   
   ht->cso = cso_hash_create();
   if(!ht->cso) {
      FREE(ht);
      return NULL;
   }
   
   ht->hash = hash;
   ht->compare = compare;
   ht->destroy = destroy;
   
   return ht;
}


static inline struct cso_hash_iter
util_hash_table_find_iter(struct util_hash_table *ht,
                          void *key,
                          unsigned key_hash)
{
   struct cso_hash_iter iter;
   struct util_hash_table_item *item;
   
   iter = cso_hash_find(ht->cso, key_hash);
   while (!cso_hash_iter_is_null(iter)) {
      item = (struct util_hash_table_item *)cso_hash_iter_data(iter);
      if (!ht->compare(item->key, key))
         break;
      iter = cso_hash_iter_next(iter);
   }
   
   return iter;
}


static inline struct util_hash_table_item *
util_hash_table_find_item(struct util_hash_table *ht,
                          void *key,
                          unsigned key_hash)
{
   struct cso_hash_iter iter;
   struct util_hash_table_item *item;
   
   iter = cso_hash_find(ht->cso, key_hash);
   while (!cso_hash_iter_is_null(iter)) {
      item = (struct util_hash_table_item *)cso_hash_iter_data(iter);
      if (!ht->compare(item->key, key))
         return item;
      iter = cso_hash_iter_next(iter);
   }
   
   return NULL;
}


enum pipe_error
util_hash_table_set(struct util_hash_table *ht,
                    void *key,
                    void *value)
{
   unsigned key_hash;
   struct util_hash_table_item *item;
   struct cso_hash_iter iter;

   assert(ht);
   if (!ht)
      return PIPE_ERROR_BAD_INPUT;

   key_hash = ht->hash(key);

   item = util_hash_table_find_item(ht, key, key_hash);
   if(item) {
      ht->destroy(item->value);
      item->value = value;
      return PIPE_OK;
   }
   
   item = MALLOC_STRUCT(util_hash_table_item);
   if(!item)
      return PIPE_ERROR_OUT_OF_MEMORY;
   
   item->key = key;
   item->value = value;
   
   iter = cso_hash_insert(ht->cso, key_hash, item);
   if(cso_hash_iter_is_null(iter)) {
      FREE(item);
      return PIPE_ERROR_OUT_OF_MEMORY;
   }

   return PIPE_OK;
}


void *
util_hash_table_get(struct util_hash_table *ht,
                    void *key)
{
   unsigned key_hash;
   struct util_hash_table_item *item;

   assert(ht);
   if (!ht)
      return NULL;

   key_hash = ht->hash(key);

   item = util_hash_table_find_item(ht, key, key_hash);
   if(!item)
      return NULL;
   
   return item->value;
}


void
util_hash_table_remove(struct util_hash_table *ht,
                       void *key)
{
   unsigned key_hash;
   struct cso_hash_iter iter;
   struct util_hash_table_item *item;

   assert(ht);
   if (!ht)
      return;

   key_hash = ht->hash(key);

   iter = util_hash_table_find_iter(ht, key, key_hash);
   if(cso_hash_iter_is_null(iter))
      return;
   
   item = util_hash_table_item(iter);
   assert(item);
   ht->destroy(item->value);
   FREE(item);
   
   cso_hash_erase(ht->cso, iter);
}


void 
util_hash_table_clear(struct util_hash_table *ht)
{
   struct cso_hash_iter iter;
   struct util_hash_table_item *item;

   assert(ht);
   if (!ht)
      return;

   iter = cso_hash_first_node(ht->cso);
   while (!cso_hash_iter_is_null(iter)) {
      item = (struct util_hash_table_item *)cso_hash_take(ht->cso, cso_hash_iter_key(iter));
      ht->destroy(item->value);
      FREE(item);
      iter = cso_hash_first_node(ht->cso);
   }
}


enum pipe_error
util_hash_table_foreach(struct util_hash_table *ht,
                     enum pipe_error (*callback)
                        (void *key, void *value, void *data),
                     void *data)
{
   struct cso_hash_iter iter;
   struct util_hash_table_item *item;
   enum pipe_error result;

   assert(ht);
   if (!ht)
      return PIPE_ERROR_BAD_INPUT;

   iter = cso_hash_first_node(ht->cso);
   while (!cso_hash_iter_is_null(iter)) {
      item = (struct util_hash_table_item *)cso_hash_iter_data(iter);
      result = callback(item->key, item->value, data);
      if(result != PIPE_OK)
	 return result;
      iter = cso_hash_iter_next(iter);
   }

   return PIPE_OK;
}


void
util_hash_table_destroy(struct util_hash_table *ht)
{
   struct cso_hash_iter iter;
   struct util_hash_table_item *item;

   assert(ht);
   if (!ht)
      return;

   iter = cso_hash_first_node(ht->cso);
   while (!cso_hash_iter_is_null(iter)) {
      item = (struct util_hash_table_item *)cso_hash_iter_data(iter);
      ht->destroy(item->value);
      FREE(item);
      iter = cso_hash_iter_next(iter);
   }

   cso_hash_delete(ht->cso);
   
   FREE(ht);
}

static unsigned hash_func_pointer(void *key)
{
   return XXH32(&key, sizeof(key), 0);
}

static int compare_func_pointer(void *key1, void *key2)
{
   return key1 != key2;
}

static unsigned hash_func_u64(void *key)
{
   return XXH32(key, sizeof(uint64_t), 0);
}

static int compare_func_u64(void *key1, void *key2)
{
   return *(const uint64_t *)key1 != *(const uint64_t*)key2;
}

static bool util_hash_table_u64_uses_pointer(void)
{
   /* return true if we can store a uint64_t in a pointer */
   return sizeof(void *) >= sizeof(uint64_t);
}

struct util_hash_table_u64 *
util_hash_table_create_u64(void (*destroy)(void *value))
{
   if (util_hash_table_u64_uses_pointer()) {
      return (struct util_hash_table_u64 *)
         util_hash_table_create(hash_func_pointer,
                                compare_func_pointer,
                                destroy);
   }

   return (struct util_hash_table_u64 *)
      util_hash_table_create(hash_func_u64,
                             compare_func_u64,
                             destroy);
}

enum pipe_error
util_hash_table_set_u64(struct util_hash_table_u64 *ht_u64,
                        uint64_t key,
                        void *value)
{
   struct util_hash_table *ht = (struct util_hash_table *)ht_u64;
   uint64_t *real_key;
   enum pipe_error err;

   if (util_hash_table_u64_uses_pointer())
      return util_hash_table_set(ht, uintptr_to_pointer(key), value);

   real_key = MALLOC(sizeof(*real_key));
   if (!real_key)
      return PIPE_ERROR_OUT_OF_MEMORY;
   *real_key = key;

   err = util_hash_table_set(ht, real_key, value);
   if (err != PIPE_OK)
      FREE(real_key);

   return err;
}

void *
util_hash_table_get_u64(struct util_hash_table_u64 *ht_u64,
                        uint64_t key)
{
   struct util_hash_table *ht = (struct util_hash_table *)ht_u64;

   if (util_hash_table_u64_uses_pointer())
      return util_hash_table_get(ht, uintptr_to_pointer(key));

   return util_hash_table_get(ht, &key);
}

void
util_hash_table_remove_u64(struct util_hash_table_u64 *ht_u64,
                           uint64_t key)
{
   struct util_hash_table *ht = (struct util_hash_table *)ht_u64;
   unsigned key_hash;
   struct cso_hash_iter iter;
   struct util_hash_table_item *item;

   if (util_hash_table_u64_uses_pointer()) {
      util_hash_table_remove(ht, uintptr_to_pointer(key));
      return;
   }

   key_hash = ht->hash(&key);
   iter = util_hash_table_find_iter(ht, &key, key_hash);

   if (cso_hash_iter_is_null(iter))
      return;

   item = util_hash_table_item(iter);
   ht->destroy(item->value);
   FREE(item->key);
   FREE(item);

   cso_hash_erase(ht->cso, iter);
}

void
util_hash_table_destroy_u64(struct util_hash_table_u64 *ht_u64)
{
   struct util_hash_table *ht = (struct util_hash_table *)ht_u64;
   struct cso_hash_iter iter;
   struct util_hash_table_item *item;

   if (util_hash_table_u64_uses_pointer()) {
      util_hash_table_destroy(ht);
      return;
   }

   iter = cso_hash_first_node(ht->cso);
   while (!cso_hash_iter_is_null(iter)) {
      item = util_hash_table_item(iter);
      ht->destroy(item->value);
      FREE(item->key);
      FREE(item);
      iter = cso_hash_iter_next(iter);
   }

   cso_hash_delete(ht->cso);

   FREE(ht);
}
