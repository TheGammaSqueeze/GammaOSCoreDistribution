/*
 *
 * Copyright 2023 Rockchip Electronics S.LSI Co. LTD
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
 
#include <string.h>
#include <stdlib.h>
#include <unistd.h>
#include <termio.h>
#include <stdio.h>
#include <sys/ioctl.h>
#include <errno.h>
#include <fcntl.h>
#include <cutils/properties.h>
#include <sys/utsname.h>
#include <cutils/list.h>
#include <cutils/log.h>
#include <cutils/sockets.h>
#include <cutils/iosched_policy.h>

#include <dirent.h>
#include <sys/stat.h>
#include <regex.h>
#include <sys/cdefs.h>
#include <stddef.h>

#include "vendor_storage.h"

//#define LOG_TAG "Vendor_storage"

#define EMMC_IDB_PART_OFFSET            64
#define EMMC_SYS_PART_OFFSET            8064
#define EMMC_BOOT_PART_SIZE             1024
#define EMMC_VENDOR_PART_START          (1024 * 7)
#define EMMC_VENDOR_PART_SIZE           128
#define EMMC_VENDOR_PART_NUM            4
#define EMMC_VENDOR_TAG                 0x524B5644

struct vendor_info {
	uint32	tag;
	uint32	version;
	uint16	next_index;
	uint16	item_num;
	uint16	free_offset;
	uint16	free_size;
	struct	vendor_item item[126]; /* 126 * 8*/
	uint8	data[EMMC_VENDOR_PART_SIZE * 512 - 1024 - 8];
	uint32	hash;
	uint32	version2;
};

static struct vendor_info *g_vendor;
#define RK_MMC_MAX_DEVICES	3
#define EMMC_MAX_PATH_LENGTH	32
#define EMMC_DEV_PATH "/dev/block/mmcblk"
char emmc_path[EMMC_MAX_PATH_LENGTH];

#define ALIGN(x, a)		__ALIGN_KERNEL((x), (a))
#define __ALIGN_KERNEL(x, a)		__ALIGN_KERNEL_MASK(x, (typeof(x))(a) - 1)
#define __ALIGN_KERNEL_MASK(x, mask)	(((x) + (mask)) & ~(mask))

static int emmc_vendor_ops(uint8 *buffer, uint32 addr, uint32 n_sec, int write)
{
	int ret;
	uint32 f_pos =  addr * 512;
	FILE *emmc_device;

	emmc_device = fopen(emmc_path, "rb+");
	if(emmc_device == NULL) {
		return -EIO;
	}
	fseek(emmc_device,f_pos,SEEK_SET);

	if (write)
		ret = fwrite(buffer, n_sec << 9, 1, emmc_device);
	else
		ret = fread(buffer, n_sec << 9, 1, emmc_device);
	fclose(emmc_device);
	if (ret != 1)
		return -EIO;
	return 0;
}

static int emmc_vendor_storage_init(void)
{
	uint32 i, max_ver, max_index;
	uint8 *p_buf;
	char temp[EMMC_MAX_PATH_LENGTH];
	FILE *emmc_device;

	for (i = 0; i < RK_MMC_MAX_DEVICES; i++) {
		memset(emmc_path,0,sizeof(emmc_path));
		strncpy(emmc_path, EMMC_DEV_PATH, EMMC_MAX_PATH_LENGTH);
		snprintf(temp, EMMC_MAX_PATH_LENGTH, "%dboot0", i);
		strncat(emmc_path, temp, EMMC_MAX_PATH_LENGTH);
		emmc_device = fopen(emmc_path, "r");
		if(emmc_device != NULL) {
			fclose(emmc_device);
			memset(emmc_path,0,sizeof(emmc_path));
			strncpy(emmc_path, EMMC_DEV_PATH, EMMC_MAX_PATH_LENGTH);
			snprintf(temp, EMMC_MAX_PATH_LENGTH, "%d", i);
			strncat(emmc_path, temp, EMMC_MAX_PATH_LENGTH);
			emmc_device = fopen(emmc_path, "r");
			if(emmc_device != NULL) {
				fclose(emmc_device);
				break;
			}
		}
	}
	if (emmc_device == NULL)
		return -ENODEV;

	max_ver = 0;
	max_index = 0;
	for (i = 0; i < EMMC_VENDOR_PART_NUM; i++) {
		/* read first 512 bytes */
		p_buf = (uint8 *)g_vendor;
		if (emmc_vendor_ops(p_buf, EMMC_VENDOR_PART_START +
				 EMMC_VENDOR_PART_SIZE * i, 1, 0))
			goto error_exit;
		/* read last 512 bytes */
		p_buf += (EMMC_VENDOR_PART_SIZE - 1) << 9;
		if (emmc_vendor_ops(p_buf, EMMC_VENDOR_PART_START +
				 EMMC_VENDOR_PART_SIZE * (i + 1) - 1,
				 1, 0))
			goto error_exit;

		if (g_vendor->tag == EMMC_VENDOR_TAG &&
		    g_vendor->version2 == g_vendor->version) {
			if (max_ver < g_vendor->version) {
				max_index = i;
				max_ver = g_vendor->version;
			}
		}
	}
	if (max_ver) {
		if (emmc_vendor_ops((uint8 *)g_vendor, EMMC_VENDOR_PART_START +
				EMMC_VENDOR_PART_SIZE * max_index,
				EMMC_VENDOR_PART_SIZE, 0))
			goto error_exit;
	} else {
		memset((void *)g_vendor, 0, sizeof(*g_vendor));
		g_vendor->version = 1;
		g_vendor->tag = EMMC_VENDOR_TAG;
		g_vendor->version2 = g_vendor->version;
		g_vendor->free_offset = 0;
		g_vendor->free_size = sizeof(g_vendor->data);
		emmc_vendor_ops((uint8 *)g_vendor, EMMC_VENDOR_PART_START,
					EMMC_VENDOR_PART_SIZE, 1);
	}
	return 0;
error_exit:
	return -EIO;
}

int emmc_vendor_read(uint32 id, void *pbuf, uint32 size)
{
	uint32 i;
	if (!g_vendor)
		return -ENOMEM;

	for (i = 0; i < g_vendor->item_num; i++) {
		if (g_vendor->item[i].id == id) {
			if (size > g_vendor->item[i].size)
				size = g_vendor->item[i].size;
			memcpy(pbuf,
			       &g_vendor->data[g_vendor->item[i].offset],
			       size);
			return size;
		}
	}
	return (-1);
}

int emmc_vendor_write(uint32 id, void *pbuf, uint32 size)
{
	uint32 i, j, next_index, align_size, alloc_size, item_num;
	uint32 offset, next_size;
	uint8 *p_data;
	int ret = 0;
	struct vendor_item *item;
	struct vendor_item *next_item;

	if (!g_vendor)
		return -ENOMEM;

	p_data = g_vendor->data;
	item_num = g_vendor->item_num;
	align_size = ALIGN(size, 0x40); /* align to 64 bytes*/
	next_index = g_vendor->next_index;
	for (i = 0; i < item_num; i++) {
		item = &g_vendor->item[i];
		if (item->id == id) {
			alloc_size = ALIGN(item->size, 0x40);
			if (size > alloc_size) {
				if (g_vendor->free_size < align_size) {
					ret = -EINVAL;
					goto exit;
				}
				offset = item->offset;
				for (j = i; j < item_num - 1; j++) {
					item = &g_vendor->item[j];
					next_item = &g_vendor->item[j + 1];
					item->id = next_item->id;
					item->size = next_item->size;
					item->offset = offset;
					next_size = ALIGN(next_item->size,
							  0x40);
					memcpy(&p_data[offset],
					       &p_data[next_item->offset],
					       next_size);
					offset += next_size;
				}
				item = &g_vendor->item[j];
				item->id = id;
				item->offset = offset;
				item->size = size;
				memcpy(&p_data[item->offset], pbuf, size);
				g_vendor->free_offset = offset + align_size;
				g_vendor->free_size -= (align_size -
							alloc_size);
			} else {
				memcpy(&p_data[item->offset],
				       pbuf,
				       size);
				g_vendor->item[i].size = size;
			}
			g_vendor->version++;
			g_vendor->version2 = g_vendor->version;
			g_vendor->next_index++;
			if (g_vendor->next_index >= EMMC_VENDOR_PART_NUM)
				g_vendor->next_index = 0;
			emmc_vendor_ops((uint8 *)g_vendor, EMMC_VENDOR_PART_START +
					EMMC_VENDOR_PART_SIZE * next_index,
					EMMC_VENDOR_PART_SIZE, 1);
			goto exit;
		}
	}

	if (g_vendor->free_size >= align_size) {
		item = &g_vendor->item[g_vendor->item_num];
		item->id = id;
		item->offset = g_vendor->free_offset;
		item->size = size;
		g_vendor->free_offset += align_size;
		g_vendor->free_size -= align_size;
		memcpy(&g_vendor->data[item->offset], pbuf, size);
		g_vendor->item_num++;
		g_vendor->version++;
		g_vendor->version2 = g_vendor->version;
		g_vendor->next_index++;
		if (g_vendor->next_index >= EMMC_VENDOR_PART_NUM)
			g_vendor->next_index = 0;
		emmc_vendor_ops((uint8 *)g_vendor, EMMC_VENDOR_PART_START +
				EMMC_VENDOR_PART_SIZE * next_index,
				EMMC_VENDOR_PART_SIZE, 1);
		goto exit;
	}
	ret = -1;
exit:
	return ret;
}

int vendor_storage_init(void)
{
	int ret;

	g_vendor = malloc(sizeof(*g_vendor));
	if (!g_vendor)
		return -ENOMEM;
	ret = emmc_vendor_storage_init();	
	if (!ret) {
	} else {
          	SLOGE("vendor_storage_init failed ret=%d\n",ret);
		free(g_vendor);
		g_vendor = NULL;
	}

	return 0;
}

