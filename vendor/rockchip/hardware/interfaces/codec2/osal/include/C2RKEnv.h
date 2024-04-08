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

#ifndef ANDROID_C2_RK_ENV_H__
#define ANDROID_C2_RK_ENV_H__

bool Rockchip_C2_GetEnvU32(const char *name, uint32_t *value, uint32_t default_value);
bool Rockchip_C2_GetEnvStr(const char *name, char *value, char *default_value);
bool Rockchip_C2_SetEnvU32(const char *name, uint32_t value);
bool Rockchip_C2_SetEnvStr(const char *name, char *value);

#endif  // ANDROID_C2_RK_ENV_H__

