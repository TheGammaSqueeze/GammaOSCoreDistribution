/*
 * Copyright (C) 2021 The Android Open Source Project
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

#include <mini_keyctl_utils.h>

bool LoadKeyFromStdin(key_serial_t keyring_id, const char* keyname);
void LoadKeyFromFile(key_serial_t keyring_id, const char* keyname, const std::string& path);
void LoadKeyFromVerifiedPartitions(key_serial_t keyring_id);
