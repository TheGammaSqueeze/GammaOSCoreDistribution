/* SPDX-License-Identifier: GPL-2.0 */
#ifndef __EROFS_HASHMAP_H
#define __EROFS_HASHMAP_H

#ifdef __cplusplus
extern "C"
{
#endif

/* Copied from https://github.com/git/git.git */
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "flex-array.h"

/*
 * Generic implementation of hash-based key-value mappings.
 * See Documentation/technical/api-hashmap.txt.
 */

/* FNV-1 functions */
unsigned int strhash(const char *str);
unsigned int strihash(const char *str);
unsigned int memhash(const void *buf, size_t len);
unsigned int memihash(const void *buf, size_t len);

static inline unsigned int sha1hash(const unsigned char *sha1)
{
	/*
	 * Equivalent to 'return *(unsigned int *)sha1;', but safe on
	 * platforms that don't support unaligned reads.
	 */
	unsigned int hash;

	memcpy(&hash, sha1, sizeof(hash));
	return hash;
}

/* data structures */
struct hashmap_entry {
	struct hashmap_entry *next;
	unsigned int hash;
};

typedef int (*hashmap_cmp_fn)(const void *entry, const void *entry_or_key,
		const void *keydata);

struct hashmap {
	struct hashmap_entry **table;
	hashmap_cmp_fn cmpfn;
	unsigned int size, tablesize, grow_at, shrink_at;
};

struct hashmap_iter {
	struct hashmap *map;
	struct hashmap_entry *next;
	unsigned int tablepos;
};

/* hashmap functions */
void hashmap_init(struct hashmap *map, hashmap_cmp_fn equals_function,
		  size_t initial_size);
void hashmap_free(struct hashmap *map, int free_entries);

/* hashmap_entry functions */
static inline void hashmap_entry_init(void *entry, unsigned int hash)
{
	struct hashmap_entry *e = entry;

	e->hash = hash;
	e->next = NULL;
}

void *hashmap_get(const struct hashmap *map, const void *key, const void *keydata);
void *hashmap_get_next(const struct hashmap *map, const void *entry);
void hashmap_add(struct hashmap *map, void *entry);
void *hashmap_put(struct hashmap *map, void *entry);
void *hashmap_remove(struct hashmap *map, const void *key, const void *keydata);

static inline void *hashmap_get_from_hash(const struct hashmap *map,
					  unsigned int hash,
					  const void *keydata)
{
	struct hashmap_entry key;

	hashmap_entry_init(&key, hash);
	return hashmap_get(map, &key, keydata);
}

/* hashmap_iter functions */
void hashmap_iter_init(struct hashmap *map, struct hashmap_iter *iter);
void *hashmap_iter_next(struct hashmap_iter *iter);
static inline void *hashmap_iter_first(struct hashmap *map,
				       struct hashmap_iter *iter)
{
	hashmap_iter_init(map, iter);
	return hashmap_iter_next(iter);
}

/* string interning */
const void *memintern(const void *data, size_t len);
static inline const char *strintern(const char *string)
{
	return memintern(string, strlen(string));
}

#ifdef __cplusplus
}
#endif

#endif
