/*
 * Copyright (C) 2021 Rockchip Electronics Co. LTD
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

#include <stdio.h>
#include <stdlib.h>
#include <errno.h>
#include <string.h>

#include <sys/system_properties.h>

#include "C2RKLog.h"
#include "C2RKEnv.h"

bool Rockchip_C2_GetEnvU32(const char *name, uint32_t *value, uint32_t default_value)
{
    bool ret = true;
    char prop[PROP_VALUE_MAX + 1];
    int len = __system_property_get(name, prop);
    if (len > 0) {
        char *endptr;
        int base = (prop[0] == '0' && prop[1] == 'x') ? (16) : (10);
        errno = 0;
        *value = strtoul(prop, &endptr, base);
        if (errno || (prop == endptr)) {
            errno = 0;
            *value = default_value;
        }
    } else {
        *value = default_value;
    }

    return ret;
}

bool Rockchip_C2_GetEnvStr(const char *name, char *value, char *default_value)
{
    bool ret = true;
    if (value != NULL) {
        int len = __system_property_get(name, value);
        if (len <= 0 && default_value != NULL) {
            strcpy(value, default_value);
        }
    } else {
        c2_err("get env string failed, value is null");
        ret = false;
        goto EXIT;
    }

EXIT:
    return ret;
}

bool Rockchip_C2_SetEnvU32(const char *name, uint32_t value)
{
    bool ret = true;
    char buf[PROP_VALUE_MAX + 1];
    snprintf(buf, sizeof(buf), "%u", value);
    int len = __system_property_set(name, buf);
    if (len <= 0) {
        c2_err("property set failed!");
        ret = false;
        goto EXIT;
    }
EXIT:
    return ret;
}

bool Rockchip_C2_SetEnvStr(const char *name, char *value)
{
    bool ret = true;
    int len = __system_property_set(name, value);
    if (len <= 0) {
        c2_err("property set failed!");
        ret = false;
        goto EXIT;
    }

EXIT:
    return ret;
}