/*
 * Copyright (C) 2022 Arm Limited. All rights reserved.
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

#include "internal_format.h"
#include "format_info.h"

internal_format_t internal_format_t::from_private(mali_gralloc_android_format private_format)
{
	/* Clean the sentinel bit as it has no purpose after this point. */
	auto fmt = (static_cast<mali_gralloc_internal_format>(private_format) &
	            ~static_cast<mali_gralloc_internal_format>(MALI_GRALLOC_INTFMT_SENTINEL));
	return internal_format_t(fmt);
}

mali_gralloc_android_format internal_format_t::get_base() const
{
	return mali_gralloc_format_get_base(m_format);
}

const format_info_t *internal_format_t::get_base_info() const
{
	return get_format_info(get_base());
}

const format_info_t &internal_format_t::base_info() const
{
	auto *ret = get_base_info();
	CHECK(ret != nullptr) << "Attempted access to base info for invalid format";
	return *ret;
}
