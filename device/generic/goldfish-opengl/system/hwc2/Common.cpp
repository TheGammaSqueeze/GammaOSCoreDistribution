/*
 * Copyright 2021 The Android Open Source Project
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

#include "Common.h"

#include <android-base/properties.h>

bool IsCuttlefish() {
  return android::base::GetProperty("ro.product.board", "") == "cutf";
}

bool IsCuttlefishFoldable() {
  return IsCuttlefish() &&
         android::base::GetProperty("ro.product.name", "").find("foldable") !=
             std::string::npos;
}

bool IsNoOpMode() {
  return android::base::GetProperty("ro.vendor.hwcomposer.mode", "") == "noop";
}

bool IsClientCompositionMode() {
  return android::base::GetProperty("ro.vendor.hwcomposer.mode", "") == "client";
}
