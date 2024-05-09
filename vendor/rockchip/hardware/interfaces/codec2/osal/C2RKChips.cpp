/*
 * Copyright 2022 Rockchip Electronics Co. LTD
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
 *
 */

#undef  ROCKCHIP_LOG_TAG
#define ROCKCHIP_LOG_TAG    "C2RKChips"

#include <stdio.h>
#include <unistd.h>
#include <fcntl.h>
#include <string.h>
#include <fcntl.h>

#include "C2RKChips.h"
#include "C2RKLog.h"


#define MAX_SOC_NAME_LENGTH 1024

static RKChipInfo *sChipInfo = NULL;

RKChipInfo* match(char *buf) {
    int size = sizeof(ChipList) / sizeof((ChipList)[0]);

    for (int i = 0; i < size; i++) {
        if (strstr(buf, (char*)ChipList[i].name)) {
            return (RKChipInfo*)&ChipList[i];
        }
    }

    return NULL;
}

RKChipInfo* readDeviceTree() {
    int fd = open("/proc/device-tree/compatible", O_RDONLY);
    if (fd < 0) {
        c2_err("open /proc/device-tree/compatible error");
        return NULL;
    }

    char name[MAX_SOC_NAME_LENGTH] = {0};
    char* ptr = NULL;
    RKChipInfo* infor = NULL;

    int length = read(fd, name, MAX_SOC_NAME_LENGTH - 1);
    if (length > 0) {
        /* replacing the termination character to space */
        for (ptr = name;; ptr = name) {
            ptr += strnlen(name, MAX_SOC_NAME_LENGTH);
            *ptr = ' ';
            if (ptr >= name + length - 1)
                break;
        }

        infor = match(name);
        if (infor == NULL) {
            c2_err("devices tree can not found match chip name: %s", name);
        }
    }

    close(fd);

    return infor;
}

RKChipInfo* readCpuInforNode() {
    int fd = open("/proc/cpuinfo", O_RDONLY);
    if (fd < 0) {
        c2_err("open /proc/cpuinfo error");
        return NULL;
    }

    char buffer[MAX_SOC_NAME_LENGTH] = {0};
    RKChipInfo* infor = NULL;

    int length = read(fd, buffer, MAX_SOC_NAME_LENGTH - 1);
    if (length > 0) {
        char* ptr = strstr(buffer, "Hardware");
        if (ptr != NULL) {
            char name[128];
            sscanf(ptr, "Hardware\t: Rockchip %30s", name);

            infor = match(name);
            if (infor == NULL) {
                c2_info("cpu node can not found match chip name: %s", name);
            }
        }
    }

    close(fd);

    return infor;
}

RKChipInfo* readEfuse() {
    const char* NODE = "/sys/bus/nvmem/devices/rockchip-efuse0/nvmem";
    int fd = open(NODE, O_RDONLY, 0);
    if (fd < 0) {
        c2_err("open %s error", NODE);
        return NULL;
    }

    const int length = 128;
    char buffer[length] = {0};
    int size = read(fd, buffer, length);
    if (size > 0) {
        c2_info("%s: %s", __FUNCTION__, buffer);
    }

    close(fd);

    // FIXME: efuse is error in my test
    return NULL;
}

RKChipInfo* getChipName() {
    if (!sChipInfo) {
        sChipInfo = readEfuse();
        if (sChipInfo != NULL) {
            return sChipInfo;
        }

        sChipInfo = readDeviceTree();
        if (sChipInfo !=NULL) {
            return sChipInfo;
        }

        sChipInfo = readCpuInforNode();
        if (sChipInfo != NULL) {
            return sChipInfo;
        }
    }

    return sChipInfo;
}
