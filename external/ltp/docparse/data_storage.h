// SPDX-License-Identifier: GPL-2.0-or-later
/*
 * Copyright (c) 2019 Cyril Hrubis <chrubis@suse.cz>
 */

#ifndef DATA_STORAGE_H__
#define DATA_STORAGE_H__

#include <stdarg.h>
#include <stdio.h>
#include <string.h>
#include <stdlib.h>

enum data_type {
	DATA_ARRAY,
	DATA_HASH,
	DATA_STRING,
};

struct data_node_array {
	enum data_type type;
	unsigned int array_len;
	unsigned int array_used;
	struct data_node *array[];
};

struct data_hash_elem {
	struct data_node *node;
	char *id;
};

struct data_node_hash {
	enum data_type type;
	unsigned int elems_len;
	unsigned int elems_used;
	struct data_hash_elem elems[];
};

struct data_node_string {
	enum data_type type;
	char val[];
};

struct data_node {
	union {
		enum data_type type;
		struct data_node_hash hash;
		struct data_node_array array;
		struct data_node_string string;
	};
};

static inline struct data_node *data_node_string(const char *string)
{
	size_t size = sizeof(struct data_node_string) + strlen(string) + 1;
	struct data_node *node = malloc(size);

	if (!node)
		return NULL;

	node->type = DATA_STRING;
	strcpy(node->string.val, string);

	return node;
}

#define MAX_ELEMS 100

static inline struct data_node *data_node_hash(void)
{
	size_t size = sizeof(struct data_node_hash)
	              + MAX_ELEMS * sizeof(struct data_hash_elem);
	struct data_node *node = malloc(size);

	if (!node)
		return NULL;

	node->type = DATA_HASH;
	node->hash.elems_len = MAX_ELEMS;
	node->hash.elems_used = 0;

	return node;
}

static inline struct data_node *data_node_array(void)
{
	size_t size = sizeof(struct data_node_array) +
	              + MAX_ELEMS * sizeof(struct data_node*);
	struct data_node *node = malloc(size);

	if (!node)
		return NULL;

	node->type = DATA_ARRAY;
	node->array.array_len = MAX_ELEMS;
	node->array.array_used = 0;

	return node;
}

static inline int data_node_hash_add(struct data_node *self, const char *id, struct data_node *payload)
{
	if (self->type != DATA_HASH)
		return 1;

	struct data_node_hash *hash = &self->hash;

	if (hash->elems_used == hash->elems_len)
		return 1;

	struct data_hash_elem *elem = &hash->elems[hash->elems_used++];

	elem->node = payload;
	elem->id = strdup(id);

	return 0;
}

static inline void data_node_free(struct data_node *self)
{
	unsigned int i;

	switch (self->type) {
	case DATA_STRING:
	break;
	case DATA_HASH:
		for (i = 0; i < self->hash.elems_used; i++) {
			data_node_free(self->hash.elems[i].node);
			free(self->hash.elems[i].id);
		}
	break;
	case DATA_ARRAY:
		for (i = 0; i < self->array.array_used; i++)
			data_node_free(self->array.array[i]);
	break;
	}

	free(self);
}

static inline int data_node_hash_del(struct data_node *self, const char *id)
{
	unsigned int i;
	struct data_node_hash *hash = &self->hash;

	for (i = 0; i < hash->elems_used; i++) {
		if (!strcmp(hash->elems[i].id, id))
			break;
	}

	if (i >= hash->elems_used)
		return 0;

	data_node_free(hash->elems[i].node);
	free(hash->elems[i].id);

	hash->elems[i] = hash->elems[--hash->elems_used];

	return 1;
}

static struct data_node *data_node_hash_get(struct data_node *self, const char *id)
{
	unsigned int i;
	struct data_node_hash *hash = &self->hash;

	for (i = 0; i < hash->elems_used; i++) {
		if (!strcmp(hash->elems[i].id, id))
			break;
	}

	if (i >= hash->elems_used)
		return NULL;

	return hash->elems[i].node;
}

static inline int data_node_array_add(struct data_node *self, struct data_node *payload)
{
	if (self->type != DATA_ARRAY)
		return 1;

	struct data_node_array *array = &self->array;

	if (array->array_used == array->array_len)
		return 1;

	array->array[array->array_used++] = payload;

	return 0;
}

static inline unsigned int data_node_array_len(struct data_node *self)
{
	if (self->type != DATA_ARRAY)
		return 0;

	return self->array.array_used;
}

static inline void data_print_padd(unsigned int i)
{
	while (i-- > 0)
		putchar(' ');
}

static inline void data_node_print_(struct data_node *self, unsigned int padd)
{
	unsigned int i;

	switch (self->type) {
	case DATA_STRING:
		data_print_padd(padd);
		printf("'%s'\n", self->string.val);
	break;
	case DATA_HASH:
		for (i = 0; i < self->hash.elems_used; i++) {
			data_print_padd(padd);
			printf("%s = {\n", self->hash.elems[i].id);
			data_node_print_(self->hash.elems[i].node, padd+1);
			data_print_padd(padd);
			printf("},\n");
		}
	break;
	case DATA_ARRAY:
		for (i = 0; i < self->array.array_used; i++) {
			data_print_padd(padd);
			printf("{\n");
			data_node_print_(self->array.array[i], padd+1);
			data_print_padd(padd);
			printf("},\n");
		}
	break;
	}
}

static inline void data_node_print(struct data_node *self)
{
	printf("{\n");
	data_node_print_(self, 1);
	printf("}\n");
}

static inline void data_fprintf(FILE *f, unsigned int padd, const char *fmt, ...)
                   __attribute__((format (printf, 3, 4)));

static inline void data_fprintf(FILE *f, unsigned int padd, const char *fmt, ...)
{
	va_list va;

	while (padd-- > 0)
		putc(' ', f);

	va_start(va, fmt);
	vfprintf(f, fmt, va);
	va_end(va);
}


static inline void data_fprintf_esc(FILE *f, unsigned int padd, const char *str)
{
	while (padd-- > 0)
		fputc(' ', f);

	fputc('"', f);

	while (*str) {
		switch (*str) {
		case '\\':
			fputs("\\\\", f);
			break;
		case '"':
			fputs("\\\"", f);
			break;
		case '\t':
			fputs("        ", f);
			break;
		default:
			/* RFC 8259 specify  chars before 0x20 as invalid */
			if (*str >= 0x20)
				putc(*str, f);
			else
				fprintf(stderr, "%s:%d: WARNING: invalid character for JSON: %x\n",
						__FILE__, __LINE__, *str);
			break;
		}
		str++;
	}

	fputc('"', f);
}

static inline void data_to_json_(struct data_node *self, FILE *f, unsigned int padd, int do_padd)
{
	unsigned int i;

	switch (self->type) {
	case DATA_STRING:
		padd = do_padd ? padd : 0;
		data_fprintf_esc(f, padd, self->string.val);
	break;
	case DATA_HASH:
		for (i = 0; i < self->hash.elems_used; i++) {
			data_fprintf(f, padd, "\"%s\": ", self->hash.elems[i].id);
			data_to_json_(self->hash.elems[i].node, f, padd+1, 0);
			if (i < self->hash.elems_used - 1)
				fprintf(f, ",\n");
			else
				fprintf(f, "\n");
		}
	break;
	case DATA_ARRAY:
		data_fprintf(f, do_padd ? padd : 0, "[\n");
		for (i = 0; i < self->array.array_used; i++) {
			data_to_json_(self->array.array[i], f, padd+1, 1);
			if (i < self->array.array_used - 1)
				fprintf(f, ",\n");
			else
				fprintf(f, "\n");
		}
		data_fprintf(f, padd, "]");
	break;
	}
}

static inline void data_to_json(struct data_node *self, FILE *f, unsigned int padd)
{
	fprintf(f, "{\n");
	data_to_json_(self, f, padd + 1, 1);
	data_fprintf(f, padd, "}");
}

#endif /* DATA_STORAGE_H__ */
