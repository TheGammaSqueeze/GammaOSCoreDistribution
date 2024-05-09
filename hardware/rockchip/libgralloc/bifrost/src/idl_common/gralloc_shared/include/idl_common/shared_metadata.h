/*
 * Copyright (C) 2020-2022 Arm Limited.
 *
 * Copyright 2016 The Android Open Source Project
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
#pragma once

#include <optional>
#include <vector>

#include "gralloctypes/Gralloc4.h"
#include "core/buffer.h"
#include "core/buffer_descriptor.h"
#include "helper_functions.h"
#include "4.x/mapper/mapper_hidl_header.h"

#include <aidl/arm/graphics/ChromaSiting.h>

namespace arm::mapper::common
{

using aidl::android::hardware::graphics::common::BlendMode;
using aidl::android::hardware::graphics::common::Cta861_3;
using aidl::android::hardware::graphics::common::Dataspace;
using aidl::android::hardware::graphics::common::ExtendableType;
using aidl::android::hardware::graphics::common::Rect;
using aidl::android::hardware::graphics::common::Smpte2086;
using android::hardware::hidl_vec;

#define GRALLOC_ARM_CHROMA_SITING_TYPE_NAME "arm.graphics.ChromaSiting"
const static ExtendableType ChromaSiting_CositedVertical{
	GRALLOC_ARM_CHROMA_SITING_TYPE_NAME, static_cast<int64_t>(aidl::arm::graphics::ChromaSiting::COSITED_VERTICAL)
};
const static ExtendableType ChromaSiting_CositedBoth{
	GRALLOC_ARM_CHROMA_SITING_TYPE_NAME, static_cast<int64_t>(aidl::arm::graphics::ChromaSiting::COSITED_BOTH)
};

void shared_metadata_init(void *memory, std::string_view name, Dataspace dataspace, const ExtendableType &chroma_siting);
size_t shared_metadata_size();

void get_name(const imported_handle *hnd, std::string *name);

void get_crop_rect(const imported_handle *hnd, std::optional<Rect> *crop);
android::status_t set_crop_rect(const imported_handle *hnd, const Rect &crop_rectangle);

void get_dataspace(const imported_handle *hnd, std::optional<Dataspace> *dataspace);
void set_dataspace(const imported_handle *hnd, const Dataspace &dataspace);

void get_chroma_siting(const imported_handle *hnd, std::optional<ExtendableType> *chroma_siting);
void set_chroma_siting(const imported_handle *hnd, const ExtendableType &chroma_siting);

void get_blend_mode(const imported_handle *hnd, std::optional<BlendMode> *blend_mode);
void set_blend_mode(const imported_handle *hnd, const BlendMode &blend_mode);

void get_smpte2086(const imported_handle *hnd, std::optional<Smpte2086> *smpte2086);
android::status_t set_smpte2086(const imported_handle *hnd, const std::optional<Smpte2086> &smpte2086);

void get_cta861_3(const imported_handle *hnd, std::optional<Cta861_3> *cta861_3);
android::status_t set_cta861_3(const imported_handle *hnd, const std::optional<Cta861_3> &cta861_3);

void get_smpte2094_40(const imported_handle *hnd, std::optional<std::vector<uint8_t>> *smpte2094_40);
android::status_t set_smpte2094_40(const imported_handle *hnd,
                                   const std::optional<std::vector<uint8_t>> &smpte2094_40);

void get_smpte2094_10(const imported_handle *hnd, std::optional<std::vector<uint8_t>> *smpte2094_10);
android::status_t set_smpte2094_10(const imported_handle *hnd,
                                   const std::optional<std::vector<uint8_t>> &smpte2094_10);

} // namespace arm::mapper::common
