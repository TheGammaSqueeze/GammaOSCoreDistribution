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

#include "mapper/mapper_metadata.h"
#include "idl_common/shared_metadata.h"
#include "core/format_info.h"
#include "core/drm_utils.h"
#include "core/buffer_allocation.h"
#include "core/buffer.h"
#include "log.h"
#include "gralloctypes/Gralloc4.h"
#include <vector>

namespace arm
{
namespace mapper
{
namespace common
{

using aidl::android::hardware::graphics::common::BlendMode;
using aidl::android::hardware::graphics::common::Cta861_3;
using aidl::android::hardware::graphics::common::Dataspace;
using aidl::android::hardware::graphics::common::PlaneLayout;
using aidl::android::hardware::graphics::common::PlaneLayoutComponent;
using aidl::android::hardware::graphics::common::Rect;
using aidl::android::hardware::graphics::common::Smpte2086;
using aidl::android::hardware::graphics::common::StandardMetadataType;
using aidl::android::hardware::graphics::common::XyColor;
using aidl::arm::graphics::ArmMetadataType;

using MetadataType = android::hardware::graphics::mapper::V4_0::IMapper::MetadataType;

static int get_num_planes(const private_handle_t *hnd)
{
	return hnd->is_multi_plane() ? (hnd->plane_info[2].offset == 0 ? 2 : 3) : 1;
}

static std::vector<std::vector<PlaneLayoutComponent>> plane_layout_components_from_handle(const private_handle_t *hnd)
{
	/* Re-define the component constants to make the table easier to read. */
	const ExtendableType R = android::gralloc4::PlaneLayoutComponentType_R;
	const ExtendableType G = android::gralloc4::PlaneLayoutComponentType_G;
	const ExtendableType B = android::gralloc4::PlaneLayoutComponentType_B;
	const ExtendableType A = android::gralloc4::PlaneLayoutComponentType_A;
	const ExtendableType CB = android::gralloc4::PlaneLayoutComponentType_CB;
	const ExtendableType CR = android::gralloc4::PlaneLayoutComponentType_CR;
	const ExtendableType Y = android::gralloc4::PlaneLayoutComponentType_Y;
	const ExtendableType RAW = android::gralloc4::PlaneLayoutComponentType_RAW;

	struct table_entry
	{
		uint32_t drm_fourcc;
		std::vector<std::vector<PlaneLayoutComponent>> components;
	};

	/* clang-format off */
	static table_entry table[] = {
		/* 16 bit RGB(A) */
		{
			.drm_fourcc = DRM_FORMAT_RGB565,
			.components = { { { B, 0, 5 }, { G, 5, 6 }, { R, 11, 5 } } }
		},
		{
			.drm_fourcc = DRM_FORMAT_BGR565,
			.components = { { { R, 0, 5 }, { G, 5, 6 }, { B, 11, 5 } } }
		},
		/* 24 bit RGB(A) */
		{
			.drm_fourcc = DRM_FORMAT_BGR888,
			.components = { { { R, 0, 8 }, { G, 8, 8 }, { B, 16, 8 } } }
		},
		/* 32 bit RGB(A) */
		{
			.drm_fourcc = DRM_FORMAT_ARGB8888,
			.components = { { { B, 0, 8 }, { G, 8, 8 }, { R, 16, 8 }, { A, 24, 8 } } }
		},
		{
			.drm_fourcc = DRM_FORMAT_ABGR8888,
			.components = { { { R, 0, 8 }, { G, 8, 8 }, { B, 16, 8 }, { A, 24, 8 } } }
		},
		{
			.drm_fourcc = DRM_FORMAT_XBGR8888,
			.components = { { { R, 0, 8 }, { G, 8, 8 }, { B, 16, 8 } } }
		},
		{
			.drm_fourcc = DRM_FORMAT_ABGR2101010,
			.components = { { { R, 0, 10 }, { G, 10, 10 }, { B, 20, 10 }, { A, 30, 2 } } }
		},
		/* 64 bit RGB(A) */
		{
			.drm_fourcc = DRM_FORMAT_ABGR16161616F,
			.components = { { { R, 0, 16 }, { G, 16, 16 }, { B, 32, 16 }, { A, 48, 16 } } }
		},
		/* 10 bit packed RGBA */
		{
			.drm_fourcc = DRM_FORMAT_AXBXGXRX106106106106,
			.components = { { { R, 6, 10 }, { G, 22, 10 }, { B, 38, 10 }, { A, 54, 10 } } }
		},
		/* Single plane 8 bit YUV 4:2:2 */
		{
			.drm_fourcc = DRM_FORMAT_YUYV,
			.components = { { { Y, 0, 8 }, { CB, 8, 8 }, { Y, 16, 8 }, { CR, 24, 8 } } }
		},
		/* Single plane 10 bit YUV 4:4:4 */
		{
			.drm_fourcc = DRM_FORMAT_Y410,
			.components = { { { CB, 0, 10 }, { Y, 10, 10 }, { CR, 20, 10 }, { A, 30, 2 } } }
		},
		/* Single plane 10 bit YUV 4:2:2 */
		{
			.drm_fourcc = DRM_FORMAT_Y210,
			.components = { { { Y, 6, 10 }, { CB, 22, 10 }, { Y, 38, 10 }, { CR, 54, 10 } } }
		},
		/* Single plane 10 bit YUV 4:2:0 */
		{
			.drm_fourcc = DRM_FORMAT_Y0L2,
			.components = { {
				{ Y, 0, 10 }, { CB, 10, 10 }, { Y, 20, 10 }, { A, 30, 1 }, { A, 31, 1 },
				{ Y, 32, 10 }, { CR, 42, 10 }, { Y, 52, 10 }, { A, 62, 1 }, { A, 63, 1 }
			} }
		},
		/* Semi-planar 8 bit YUV 4:4:4 */
		{
			.drm_fourcc = DRM_FORMAT_NV24,
			.components = {
				{ { Y, 0, 8 } },
				{ { CB, 0, 8 }, { CR, 8, 8 } }
			}
		},
		/* Semi-planar 10 bit YUV 4:4:4 */
		{
			.drm_fourcc = DRM_FORMAT_NV30,
			.components = {
				{ { Y, 0, 10 } },
				{ { CB, 0, 10 }, { CR, 10, 10 } }
			}
		},
		/* Semi-planar 8 bit YUV 4:2:2 */
		{
			.drm_fourcc = DRM_FORMAT_NV16,
			.components = {
				{ { Y, 0, 8 } },
				{ { CB, 0, 8 }, { CR, 8, 8 } }
			}
		},
		/* Semi-planar 8 bit YUV 4:2:0 */
		{
			.drm_fourcc = DRM_FORMAT_NV12,
			.components = {
				{ { Y, 0, 8 } },
				{ { CB, 0, 8 }, { CR, 8, 8 } }
			}
		},
		{
			.drm_fourcc = DRM_FORMAT_NV21,
			.components = {
				{ { Y, 0, 8 } },
				{ { CR, 0, 8 }, { CB, 8, 8 } }
			}
		},
		/* Semi-planar 10 bit YUV 4:2:2 */
		{
			.drm_fourcc = DRM_FORMAT_P210,
			.components = {
				{ { Y, 6, 10 } },
				{ { CB, 6, 10 }, { CR, 22, 10 } }
			}
		},
		/* Semi-planar 10 bit YUV 4:2:0 */
		{
			.drm_fourcc = DRM_FORMAT_P010,
			.components = {
				{ { Y, 6, 10 } },
				{ { CB, 6, 10 }, { CR, 22, 10 } }
			}
		},
		/* Planar 8 bit YVU 4:2:0 */
		{
			.drm_fourcc = DRM_FORMAT_YVU420,
			.components = {
				{ { Y, 0, 8 } },
				{ { CR, 0, 8 } },
				{ { CB, 0, 8 } }
			}
		},
		/* Planar 8 bit YUV 4:2:0 */
		{
			.drm_fourcc = DRM_FORMAT_YUV420,
			.components = {
				{ { Y, 0, 8 } },
				{ { CB, 0, 8 } },
				{ { CR, 0, 8 } }
			}
		},
		/* Planar 8 bit YUV 4:4:4 */
		{
			.drm_fourcc = DRM_FORMAT_YUV444,
			.components = {
				{ { Y, 0, 8 } },
				{ { CB, 0, 8 } },
				{ { CR, 0, 8 } }
			}
		},
		/* AFBC Only FourCC */
		{.drm_fourcc = DRM_FORMAT_YUV420_8BIT, .components = { {} } },
		{.drm_fourcc = DRM_FORMAT_YUV420_10BIT, .components = { {} } },
		/* 8 Bit R Channel */
		{
			.drm_fourcc = DRM_FORMAT_R8,
			.components = { { {R, 0, 8} } },
		},
	};
	/* clang-format on */

	/* Special case for formats that can't be represented by a DRM fourcc */
	const auto internal_format = hnd->alloc_format;
	if (!internal_format.has_modifiers())
	{
		switch (internal_format.get_base())
		{
		case MALI_GRALLOC_FORMAT_INTERNAL_RAW10:
		case MALI_GRALLOC_FORMAT_INTERNAL_RAW12:
			std::vector<std::vector<PlaneLayoutComponent>> components = { { { RAW, 0, -1 } } };
			return components;
		}
	}

	const uint32_t drm_fourcc = drm_fourcc_from_handle(hnd);
	if (drm_fourcc != DRM_FORMAT_INVALID)
	{
		for (const auto &entry : table)
		{
			if (entry.drm_fourcc == drm_fourcc)
			{
				return entry.components;
			}
		}
	}

	MALI_GRALLOC_LOGW("Could not find component description for FourCC value %x", drm_fourcc);
	return std::vector<std::vector<PlaneLayoutComponent>>(0);
}

static android::status_t get_plane_layouts(const private_handle_t *handle, std::vector<PlaneLayout> *layouts)
{
	const int num_planes = get_num_planes(handle);
	const auto internal_format = handle->alloc_format;
	const format_info_t *format_info = internal_format.get_base_info();
	if (format_info == nullptr)
	{
		MALI_GRALLOC_LOGE("Invalid format in get_plane_layouts");
		return android::BAD_VALUE;
	}
	std::vector<std::vector<PlaneLayoutComponent>> components = plane_layout_components_from_handle(handle);
	layouts->reserve(num_planes);
	for (size_t plane_index = 0; plane_index < num_planes; ++plane_index)
	{
		int64_t plane_size;
		if (plane_index < num_planes - 1)
		{
			plane_size = handle->plane_info[plane_index + 1].offset;
		}
		else
		{
			int64_t layer_size = handle->size / handle->layer_count;
			plane_size = layer_size - handle->plane_info[plane_index].offset;
		}

		bool is_raw = false;
		switch (internal_format.get_base())
		{
		case MALI_GRALLOC_FORMAT_INTERNAL_RAW10:
		case MALI_GRALLOC_FORMAT_INTERNAL_RAW12:
			is_raw = true;
			break;
		}

		int64_t sample_increment_in_bits = 0;
		if (internal_format.has_modifiers() || !is_raw)
		{
			sample_increment_in_bits =
			    (internal_format.is_afbc()) ? format_info->bpp_afbc[plane_index] : format_info->bpp[plane_index];
		}

		PlaneLayout layout = { .offsetInBytes = handle->plane_info[plane_index].offset,
			                   .sampleIncrementInBits = sample_increment_in_bits,
			                   .strideInBytes = handle->plane_info[plane_index].byte_stride,
			                   .widthInSamples = handle->plane_info[plane_index].alloc_width,
			                   .heightInSamples = handle->plane_info[plane_index].alloc_height,
			                   .totalSizeInBytes = plane_size,
			                   .horizontalSubsampling = (plane_index == 0 ? 1 : format_info->hsub),
			                   .verticalSubsampling = (plane_index == 0 ? 1 : format_info->vsub),
			                   .components = components.size() > plane_index ? components[plane_index] :
			                                                                   std::vector<PlaneLayoutComponent>(0) };
		layouts->push_back(layout);
	}

	return android::OK;
}

static android::status_t get_plane_fds(const private_handle_t *hnd, std::vector<int64_t> *fds)
{
	const int num_planes = get_num_planes(hnd);

	fds->resize(num_planes, static_cast<int64_t>(hnd->share_fd));

	return android::OK;
}

/* Encode the number of fds as an int64_t followed by the int64_t fds themselves */
static android::status_t encodeArmPlaneFds(const std::vector<int64_t> &fds, hidl_vec<uint8_t> *output)
{
	int64_t n_fds = fds.size();

	output->resize((n_fds + 1) * sizeof(int64_t));

	memcpy(output->data(), &n_fds, sizeof(n_fds));
	memcpy(output->data() + sizeof(n_fds), fds.data(), sizeof(int64_t) * n_fds);

	return android::OK;
}

static bool isArmMetadataType(const MetadataType &metadataType)
{
	return metadataType.name == GRALLOC_ARM_METADATA_TYPE_NAME;
}

static ArmMetadataType getArmMetadataTypeValue(const MetadataType &metadataType)
{
	return static_cast<ArmMetadataType>(metadataType.value);
}

void get_metadata(const private_handle_t *handle, const IMapper::MetadataType &metadataType, IMapper::get_cb hidl_cb)
{
	/* This will hold the metadata that is returned. */
	hidl_vec<uint8_t> vec;

	if (android::gralloc4::isStandardMetadataType(metadataType))
	{
		android::status_t err = android::OK;

		switch (android::gralloc4::getStandardMetadataTypeValue(metadataType))
		{
		case StandardMetadataType::BUFFER_ID:
			err = android::gralloc4::encodeBufferId(handle->backing_store_id, &vec);
			break;
		case StandardMetadataType::NAME:
		{
			auto import = handle_cast<imported_handle>(handle);
			if (import == nullptr)
			{
				MALI_GRALLOC_LOGE("get() called on raw handle");
				err = android::BAD_VALUE;
				break;
			}

			std::string name;
			get_name(import, &name);
			err = android::gralloc4::encodeName(name, &vec);
			break;
		}
		case StandardMetadataType::WIDTH:
			err = android::gralloc4::encodeWidth(handle->width, &vec);
			break;
		case StandardMetadataType::HEIGHT:
			err = android::gralloc4::encodeHeight(handle->height, &vec);
			break;
		case StandardMetadataType::LAYER_COUNT:
			err = android::gralloc4::encodeLayerCount(handle->layer_count, &vec);
			break;
		case StandardMetadataType::PIXEL_FORMAT_REQUESTED:
			err = android::gralloc4::encodePixelFormatRequested(static_cast<PixelFormat>(handle->req_format), &vec);
			break;
		case StandardMetadataType::PIXEL_FORMAT_FOURCC:
			err = android::gralloc4::encodePixelFormatFourCC(drm_fourcc_from_handle(handle), &vec);
			break;
		case StandardMetadataType::PIXEL_FORMAT_MODIFIER:
			err = android::gralloc4::encodePixelFormatModifier(drm_modifier_from_handle(handle), &vec);
			break;
		case StandardMetadataType::USAGE:
			err = android::gralloc4::encodeUsage(handle->consumer_usage | handle->producer_usage, &vec);
			break;
		case StandardMetadataType::ALLOCATION_SIZE:
			err = android::gralloc4::encodeAllocationSize(handle->size, &vec);
			break;
		case StandardMetadataType::PROTECTED_CONTENT:
		{
			/* This is set to 1 if the buffer has protected content. */
			const int is_protected =
			    (((handle->consumer_usage | handle->producer_usage) & BufferUsage::PROTECTED) == 0) ? 0 : 1;
			err = android::gralloc4::encodeProtectedContent(is_protected, &vec);
			break;
		}
		case StandardMetadataType::COMPRESSION:
		{
			ExtendableType compression;
			const auto internal_format = handle->alloc_format;
			if (internal_format.is_afbc())
			{
				compression = Compression_AFBC;
			}
			else if (internal_format.is_afrc())
			{
				compression = Compression_AFRC;
			}
			else
			{
				compression = android::gralloc4::Compression_None;
			}
			err = android::gralloc4::encodeCompression(compression, &vec);
			break;
		}
		case StandardMetadataType::INTERLACED:
			err = android::gralloc4::encodeInterlaced(android::gralloc4::Interlaced_None, &vec);
			break;
		case StandardMetadataType::CHROMA_SITING:
		{
			auto import = handle_cast<imported_handle>(handle);
			if (import == nullptr)
			{
				MALI_GRALLOC_LOGE("get() called on raw handle");
				err = android::BAD_VALUE;
				break;
			}

			const auto *format_info = handle->alloc_format.get_base_info();
			if (format_info == nullptr)
			{
				err = android::BAD_VALUE;
				break;
			}

			std::optional<ExtendableType> chroma_siting;
			ExtendableType chroma_siting_default = android::gralloc4::ChromaSiting_None;
			if (format_info->is_yuv)
			{
				chroma_siting_default = android::gralloc4::ChromaSiting_Unknown;
			}

			get_chroma_siting(import, &chroma_siting);
			err = android::gralloc4::encodeChromaSiting(chroma_siting.value_or(chroma_siting_default), &vec);
			break;
		}
		case StandardMetadataType::PLANE_LAYOUTS:
		{
			std::vector<PlaneLayout> layouts;
			err = get_plane_layouts(handle, &layouts);
			if (!err)
			{
				err = android::gralloc4::encodePlaneLayouts(layouts, &vec);
			}
			break;
		}
		case StandardMetadataType::DATASPACE:
		{
			auto import = handle_cast<imported_handle>(handle);
			if (import == nullptr)
			{
				MALI_GRALLOC_LOGE("get() called on raw handle");
				err = android::BAD_VALUE;
				break;
			}

			std::optional<Dataspace> dataspace;
			get_dataspace(import, &dataspace);
			err = android::gralloc4::encodeDataspace(dataspace.value_or(Dataspace::UNKNOWN), &vec);
			break;
		}
		case StandardMetadataType::BLEND_MODE:
		{
			auto import = handle_cast<imported_handle>(handle);
			if (import == nullptr)
			{
				MALI_GRALLOC_LOGE("get() called on raw handle");
				err = android::BAD_VALUE;
				break;
			}

			std::optional<BlendMode> blend_mode;
			get_blend_mode(import, &blend_mode);
			err = android::gralloc4::encodeBlendMode(blend_mode.value_or(BlendMode::INVALID), &vec);
			break;
		}
		case StandardMetadataType::CROP:
		{
			auto import = handle_cast<imported_handle>(handle);
			if (import == nullptr)
			{
				MALI_GRALLOC_LOGE("get() called on raw handle");
				err = android::BAD_VALUE;
				break;
			}

			const int num_planes = get_num_planes(handle);
			std::vector<Rect> crops(num_planes);
			for (size_t plane_index = 0; plane_index < num_planes; ++plane_index)
			{
				/* Set the default crop rectangle. Android mandates that it must fit [0, 0, widthInSamples, heightInSamples]
				 * We always require using the requested width and height for the crop rectangle size.
				 * For planes > 0 the size might need to be scaled, but since we only use plane[0] for crop set it to the
				 * Android default of [0, 0, widthInSamples, heightInSamples] for other planes.
				 */
				Rect rect = { .top = 0,
					          .left = 0,
					          .right = static_cast<int32_t>(handle->plane_info[plane_index].alloc_width),
					          .bottom = static_cast<int32_t>(handle->plane_info[plane_index].alloc_height) };
				if (plane_index == 0)
				{
					std::optional<Rect> crop_rect;
					get_crop_rect(import, &crop_rect);
					if (crop_rect.has_value())
					{
						rect = crop_rect.value();
					}
					else
					{
						rect = { .top = 0, .left = 0, .right = handle->width, .bottom = handle->height };
					}
				}
				crops[plane_index] = rect;
			}
			err = android::gralloc4::encodeCrop(crops, &vec);
			break;
		}
		case StandardMetadataType::SMPTE2086:
		{
			auto import = handle_cast<imported_handle>(handle);
			if (import == nullptr)
			{
				MALI_GRALLOC_LOGE("get() called on raw handle");
				err = android::BAD_VALUE;
				break;
			}

			std::optional<Smpte2086> smpte2086;
			get_smpte2086(import, &smpte2086);
			err = android::gralloc4::encodeSmpte2086(smpte2086, &vec);
			break;
		}
		case StandardMetadataType::CTA861_3:
		{
			auto import = handle_cast<imported_handle>(handle);
			if (import == nullptr)
			{
				MALI_GRALLOC_LOGE("get() called on raw handle");
				err = android::BAD_VALUE;
				break;
			}

			std::optional<Cta861_3> cta861_3;
			get_cta861_3(import, &cta861_3);
			err = android::gralloc4::encodeCta861_3(cta861_3, &vec);
			break;
		}
		case StandardMetadataType::SMPTE2094_40:
		{
			auto import = handle_cast<imported_handle>(handle);
			if (import == nullptr)
			{
				MALI_GRALLOC_LOGE("get() called on raw handle");
				err = android::BAD_VALUE;
				break;
			}

			std::optional<std::vector<uint8_t>> smpte2094_40;
			get_smpte2094_40(import, &smpte2094_40);
			err = android::gralloc4::encodeSmpte2094_40(smpte2094_40, &vec);
			break;
		}
#if PLATFORM_SDK_VERSION >= 33
		case StandardMetadataType::SMPTE2094_10:
		{
			auto import = handle_cast<imported_handle>(handle);
			if (import == nullptr)
			{
				MALI_GRALLOC_LOGE("get() called on raw handle");
				err = android::BAD_VALUE;
				break;
			}

			std::optional<std::vector<uint8_t>> smpte2094_10;
			get_smpte2094_10(import, &smpte2094_10);
			err = android::gralloc4::encodeSmpte2094_10(smpte2094_10, &vec);
			break;
		}
#endif
		case StandardMetadataType::INVALID:
		default:
			err = android::BAD_VALUE;
		}
		hidl_cb((err) ? Error::UNSUPPORTED : Error::NONE, vec);
	}
	else if (isArmMetadataType(metadataType))
	{
		android::status_t err = android::OK;

		switch (getArmMetadataTypeValue(metadataType))
		{
		case ArmMetadataType::PLANE_FDS:
		{
			std::vector<int64_t> fds;

			err = get_plane_fds(handle, &fds);
			if (!err)
			{
				err = encodeArmPlaneFds(fds, &vec);
			}
			break;
		}
		default:
			err = android::BAD_VALUE;
		}
		hidl_cb((err) ? Error::UNSUPPORTED : Error::NONE, vec);
	}
	else
	{
		/* If known vendor type, return it */
		hidl_cb(Error::UNSUPPORTED, vec);
	}
}

Error set_metadata(const imported_handle *handle, const IMapper::MetadataType &metadataType,
                   const hidl_vec<uint8_t> &metadata)
{
	if (android::gralloc4::isStandardMetadataType(metadataType))
	{
		android::status_t err = android::OK;
		switch (android::gralloc4::getStandardMetadataTypeValue(metadataType))
		{
		case StandardMetadataType::DATASPACE:
		{
			Dataspace dataspace;
			err = android::gralloc4::decodeDataspace(metadata, &dataspace);
			// The new dataspace format is above 16 bits,
			if (((int32_t)dataspace) & 0xffff) {
				MALI_GRALLOC_LOGV("Found legacy dataspace=0x%x, converting it to v0...", dataspace);
				switch (((int32_t)dataspace) & 0xffff) {
					case HAL_DATASPACE_SRGB:
						dataspace = Dataspace::SRGB;
						break;
					case HAL_DATASPACE_JFIF:
						dataspace = Dataspace::JFIF;
						break;
					case HAL_DATASPACE_SRGB_LINEAR:
						dataspace = Dataspace::SRGB_LINEAR;
						break;
					case HAL_DATASPACE_BT601_625:
						dataspace = Dataspace::BT601_625;
						break;
					case HAL_DATASPACE_BT601_525:
						dataspace = Dataspace::BT601_525;
						break;
					case HAL_DATASPACE_BT709:
						dataspace = Dataspace::BT709;
						break;
					default:
						MALI_GRALLOC_LOGW("Unsupported legacy dataspace=0x%x", dataspace);
				}
			}
			if (!err && dataspace!= Dataspace::UNKNOWN)
			{
				set_dataspace(handle, dataspace);
			}
			break;
		}
		case StandardMetadataType::CHROMA_SITING:
		{
			const auto *format_info = handle->alloc_format.get_base_info();
			if (format_info == nullptr)
			{
				err = android::BAD_VALUE;
				break;
			}

			ExtendableType chroma_siting;
			err = android::gralloc4::decodeChromaSiting(metadata, &chroma_siting);
			if (!err)
			{
				if (format_info->is_yuv && (chroma_siting.name == GRALLOC4_STANDARD_CHROMA_SITING ||
				                            chroma_siting.name == GRALLOC_ARM_CHROMA_SITING_TYPE_NAME))
				{
					set_chroma_siting(handle, chroma_siting);
				}
				else
				{
					err = android::BAD_VALUE;
				}
			}
			break;
		}
		case StandardMetadataType::BLEND_MODE:
		{
			BlendMode blend_mode;
			err = android::gralloc4::decodeBlendMode(metadata, &blend_mode);
			if (!err)
			{
				set_blend_mode(handle, blend_mode);
			}
			break;
		}
		case StandardMetadataType::SMPTE2086:
		{
			std::optional<Smpte2086> smpte2086;
			err = android::gralloc4::decodeSmpte2086(metadata, &smpte2086);
			if (!err)
			{
				err = set_smpte2086(handle, smpte2086);
			}
			break;
		}
		case StandardMetadataType::CTA861_3:
		{
			std::optional<Cta861_3> cta861_3;
			err = android::gralloc4::decodeCta861_3(metadata, &cta861_3);
			if (!err)
			{
				err = set_cta861_3(handle, cta861_3);
			}
			break;
		}
		case StandardMetadataType::SMPTE2094_40:
		{
			std::optional<std::vector<uint8_t>> smpte2094_40;
			err = android::gralloc4::decodeSmpte2094_40(metadata, &smpte2094_40);
			if (!err)
			{
				err = set_smpte2094_40(handle, smpte2094_40);
			}
			break;
		}
#if PLATFORM_SDK_VERSION >= 33
		case StandardMetadataType::SMPTE2094_10:
		{
			std::optional<std::vector<uint8_t>> smpte2094_10;
			err = android::gralloc4::decodeSmpte2094_10(metadata, &smpte2094_10);
			if (!err)
			{
				err = set_smpte2094_10(handle, smpte2094_10);
			}
			break;
		}
#endif
		case StandardMetadataType::CROP:
		{
			std::vector<Rect> crops;
			err = android::gralloc4::decodeCrop(metadata, &crops);
			if (!err)
			{
				err = set_crop_rect(handle, crops[0]);
			}
			break;
		}
		/* The following meta data types cannot be changed after allocation. */
		case StandardMetadataType::BUFFER_ID:
		case StandardMetadataType::NAME:
		case StandardMetadataType::WIDTH:
		case StandardMetadataType::HEIGHT:
		case StandardMetadataType::LAYER_COUNT:
		case StandardMetadataType::PIXEL_FORMAT_REQUESTED:
		case StandardMetadataType::USAGE:
			return Error::BAD_VALUE;
		/* Changing other metadata types is unsupported. */
		case StandardMetadataType::PLANE_LAYOUTS:
		case StandardMetadataType::PIXEL_FORMAT_FOURCC:
		case StandardMetadataType::PIXEL_FORMAT_MODIFIER:
		case StandardMetadataType::ALLOCATION_SIZE:
		case StandardMetadataType::PROTECTED_CONTENT:
		case StandardMetadataType::COMPRESSION:
		case StandardMetadataType::INTERLACED:
		case StandardMetadataType::INVALID:
		default:
			return Error::UNSUPPORTED;
		}
		return ((err) ? Error::UNSUPPORTED : Error::NONE);
	}
	else
	{
		/* None of the vendor types support set. */
		return Error::UNSUPPORTED;
	}
}

void getFromBufferDescriptorInfo(IMapper::BufferDescriptorInfo const &description,
                                 IMapper::MetadataType const &metadataType,
                                 IMapper::getFromBufferDescriptorInfo_cb hidl_cb)
{
	/* This will hold the metadata that is returned. */
	hidl_vec<uint8_t> vec;

	buffer_descriptor_t descriptor;
	descriptor.width = description.width;
	descriptor.height = description.height;
	descriptor.layer_count = description.layerCount;
	descriptor.hal_format = static_cast<uint64_t>(description.format);
	descriptor.producer_usage = static_cast<uint64_t>(description.usage);
	descriptor.consumer_usage = descriptor.producer_usage;

	/* Check if it is possible to allocate a buffer for the given description */
	const int alloc_result = mali_gralloc_derive_format_and_size(&descriptor);
	if (alloc_result != 0)
	{
		MALI_GRALLOC_LOGV("Allocation for the given description will not succeed. error: %d", alloc_result);
		hidl_cb(Error::BAD_VALUE, vec);
		return;
	}
	/* Create buffer handle from the initialized descriptor without a backing store or shared metadata region.
	 * Used to share functionality with the normal metadata get function that can only use the allocated buffer handle
	 * and does not have the buffer descriptor available. */
	private_handle_t partial_handle(descriptor.size, descriptor.consumer_usage, descriptor.producer_usage, -1,
	                                descriptor.hal_format, descriptor.alloc_format, descriptor.width, descriptor.height,
	                                descriptor.layer_count, descriptor.plane_info, descriptor.pixel_stride);
	if (android::gralloc4::isStandardMetadataType(metadataType))
	{
		android::status_t err = android::OK;

		switch (android::gralloc4::getStandardMetadataTypeValue(metadataType))
		{
		case StandardMetadataType::NAME:
			err = android::gralloc4::encodeName(description.name, &vec);
			break;
		case StandardMetadataType::WIDTH:
			err = android::gralloc4::encodeWidth(description.width, &vec);
			break;
		case StandardMetadataType::HEIGHT:
			err = android::gralloc4::encodeHeight(description.height, &vec);
			break;
		case StandardMetadataType::LAYER_COUNT:
			err = android::gralloc4::encodeLayerCount(description.layerCount, &vec);
			break;
		case StandardMetadataType::PIXEL_FORMAT_REQUESTED:
			err = android::gralloc4::encodePixelFormatRequested(static_cast<PixelFormat>(description.format), &vec);
			break;
		case StandardMetadataType::USAGE:
			err = android::gralloc4::encodeUsage(description.usage, &vec);
			break;
		case StandardMetadataType::PIXEL_FORMAT_FOURCC:
			err = android::gralloc4::encodePixelFormatFourCC(drm_fourcc_from_handle(&partial_handle), &vec);
			break;
		case StandardMetadataType::PIXEL_FORMAT_MODIFIER:
			err = android::gralloc4::encodePixelFormatModifier(drm_modifier_from_handle(&partial_handle), &vec);
			break;
		case StandardMetadataType::ALLOCATION_SIZE:
			err = android::gralloc4::encodeAllocationSize(partial_handle.size, &vec);
			break;
		case StandardMetadataType::PROTECTED_CONTENT:
		{
			/* This is set to 1 if the buffer has protected content. */
			const int is_protected =
			    (((partial_handle.consumer_usage | partial_handle.producer_usage) & BufferUsage::PROTECTED)) ? 1 : 0;
			err = android::gralloc4::encodeProtectedContent(is_protected, &vec);
			break;
		}
		case StandardMetadataType::COMPRESSION:
		{
			ExtendableType compression;
			const auto internal_format = partial_handle.alloc_format;
			if (internal_format.is_afbc())
			{
				compression = Compression_AFBC;
			}
			else if (internal_format.is_afrc())
			{
				compression = Compression_AFRC;
			}
			else
			{
				compression = android::gralloc4::Compression_None;
			}
			err = android::gralloc4::encodeCompression(compression, &vec);
			break;
		}
		case StandardMetadataType::INTERLACED:
			err = android::gralloc4::encodeInterlaced(android::gralloc4::Interlaced_None, &vec);
			break;
		case StandardMetadataType::CHROMA_SITING:
		{
			const auto *format_info = partial_handle.alloc_format.get_base_info();
			if (format_info == nullptr)
			{
				err = android::BAD_VALUE;
				break;
			}

			ExtendableType chroma_siting = android::gralloc4::ChromaSiting_None;
			if (format_info->is_yuv)
			{
				chroma_siting = android::gralloc4::ChromaSiting_Unknown;
			}
			err = android::gralloc4::encodeChromaSiting(chroma_siting, &vec);
			break;
		}
		case StandardMetadataType::PLANE_LAYOUTS:
		{
			std::vector<PlaneLayout> layouts;
			err = get_plane_layouts(&partial_handle, &layouts);
			if (!err)
			{
				err = android::gralloc4::encodePlaneLayouts(layouts, &vec);
			}
			break;
		}
		case StandardMetadataType::DATASPACE:
		{
			android_dataspace_t dataspace;
			get_format_dataspace(partial_handle.alloc_format.get_base_info(),
			                     partial_handle.consumer_usage | partial_handle.producer_usage, partial_handle.width,
			                     partial_handle.height, &dataspace);
			err = android::gralloc4::encodeDataspace(static_cast<Dataspace>(dataspace), &vec);
			break;
		}
		case StandardMetadataType::BLEND_MODE:
			err = android::gralloc4::encodeBlendMode(BlendMode::INVALID, &vec);
			break;
		case StandardMetadataType::CROP:
		{
			const int num_planes = get_num_planes(&partial_handle);
			std::vector<Rect> crops(num_planes);
			for (size_t plane_index = 0; plane_index < num_planes; ++plane_index)
			{
				Rect rect = { .top = 0,
					          .left = 0,
					          .right = static_cast<int32_t>(partial_handle.plane_info[plane_index].alloc_width),
					          .bottom = static_cast<int32_t>(partial_handle.plane_info[plane_index].alloc_height) };
				if (plane_index == 0)
				{
					rect = { .top = 0, .left = 0, .right = partial_handle.width, .bottom = partial_handle.height };
				}
				crops[plane_index] = rect;
			}
			err = android::gralloc4::encodeCrop(crops, &vec);
			break;
		}
		case StandardMetadataType::SMPTE2086:
		{
			std::optional<Smpte2086> smpte2086{};
			err = android::gralloc4::encodeSmpte2086(smpte2086, &vec);
			break;
		}
		case StandardMetadataType::CTA861_3:
		{
			std::optional<Cta861_3> cta861_3{};
			err = android::gralloc4::encodeCta861_3(cta861_3, &vec);
			break;
		}
		case StandardMetadataType::SMPTE2094_40:
		{
			std::optional<std::vector<uint8_t>> smpte2094_40{};
			err = android::gralloc4::encodeSmpte2094_40(smpte2094_40, &vec);
			break;
		}
#if PLATFORM_SDK_VERSION >= 33
		case StandardMetadataType::SMPTE2094_10:
		{
			std::optional<std::vector<uint8_t>> smpte2094_10{};
			err = android::gralloc4::encodeSmpte2094_10(smpte2094_10, &vec);
			break;
		}
#endif
		case StandardMetadataType::BUFFER_ID:
		case StandardMetadataType::INVALID:
		default:
			err = android::BAD_VALUE;
		}
		hidl_cb((err) ? Error::UNSUPPORTED : Error::NONE, vec);
	}
	else
	{
		hidl_cb(Error::UNSUPPORTED, vec);
	}
}

} // namespace common
} // namespace mapper
} // namespace arm
