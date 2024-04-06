/*
 * Copyright (C) 2020, 2022 ARM Limited. All rights reserved.
 *
 * Copyright (C) 2008 The Android Open Source Project
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

#include <inttypes.h>
#include <string>
#include <unordered_map>

#include "gralloc/formats.h"

// Helper macros for generating all available features
#define EXPAND_FEATURES(V) \
	V(FORMAT_R10G10B10A2) \
	V(FORMAT_R16G16B16A16_FLOAT) \
	V(YUV_BL_8) \
	V(YUV_BL_10) \
	V(AFBC_16X16) \
	V(AFBC_32X8) \
	V(AFBC_64X4) \
	V(AFBC_BLOCK_SPLIT) \
	V(AFBC_TILED_HEADERS) \
	V(AFBC_DOUBLE_BODY) \
	V(AFBC_WRITE_NON_SPARSE) \
	V(AFBC_YUV) \
	V(AFBC_FORMAT_R16G16B16A16_FLOAT) \
	V(AFRC_ROT_LAYOUT) \
	V(AFRC_SCAN_LAYOUT) \
	V(DISABLED)
#define FEATURE(F) F,

/*
 * @brief Enum containing all the available features.
 */
enum class feature_t
{
	EXPAND_FEATURES(FEATURE)
	UNKNOWN
};

#undef FEATURE

/*
 * @brief Gets the name / string representation of a feature enum value.
 *
 * @param[in] name The feature enum value.
 *
 * @return the name corresponding to the enum value.
 */
inline feature_t name_to_feature(const std::string &name)
{
	#define MAP_ITEM(F) {#F, feature_t::F},
	static std::unordered_map<std::string, feature_t> name_to_feature_map =
	{
		EXPAND_FEATURES(MAP_ITEM)
	};
	#undef MAP_ITEM

	auto mapping = name_to_feature_map.find(name);
	if(mapping != name_to_feature_map.end())
	{
		return mapping->second;
	}

	return feature_t::UNKNOWN;
}

/*
 * @brief Gets a feature enum value by its name.
 *
 * @param[in] name The name of the feature.
 *
 * @return the corresponding feature enum value if a feature with the
 *         requested name could be found, feature_t::UNKNOWN otherwise.
 */
inline std::string feature_to_name(feature_t feature)
{
	#define MAP_ITEM(F) {feature_t::F, #F},
	static std::unordered_map<feature_t, std::string> feature_to_name_map =
	{
		EXPAND_FEATURES(MAP_ITEM)
	};
	#undef MAP_ITEM

	auto mapping = feature_to_name_map.find(feature);
	if(mapping != feature_to_name_map.end())
	{
		return mapping->second;
	}

	return "UNKNOWN";
}

class producers_t;
class consumers_t;

bool ip_support_feature(mali_gralloc_ip producers, mali_gralloc_ip consumers, feature_t feature);

/**
 * @brief Class that represents a set of IPs (CPU, GPU, DPU, VPU).
 *
 * This class represents a set of IPs. It provides a type safe alternative to using mali_gralloc_ip
 * directly. See in particular the derived types producer_t and consumer_t.
 * Using these types provides type safety, as it is not possible to accidentally exchange
 * consumers and producers. It also makes the code more readable.
 */
class ip_t
{
public:
	/**
	 * @brief Check whether a feature is supported by all provided producers and consumers.
	 *
	 * @param producers A set of producers.
	 * @param consumers A set of consumers.
	 * @param feature The feature.
	 * @return Whether the feature @p feature is supported by all of @p producers and @p consumers.
	 *   If @p producers or @p consumers are empty, then they are ignored.
	 *   For example, if @p producers is empty then this function checks whether @p feature is
	 *   supported by all consumers only. If @p producers and @p consumers are both empty, this
	 *   function returns unconditionally @c true. Similarly, producers and consumers that are
	 *   not present (see ip_t::present for a definition of "present") are also ignored.
	 */
	static bool support(producers_t producers, consumers_t consumers, feature_t feature);

	/**
	 * @brief Check whether the provided IPs are present in the system.
	 *
	 * @param ips The IP set to check.
	 * @return Whether all the IPs in @p ips are present in the system. An IP is considered present
	 *   when the Gralloc configuration files explicitly provide the capabilities for that IP.
	 */
	static bool present(ip_t ips);

	ip_t() = default;

	ip_t(mali_gralloc_ip ip)
	    : m_value(ip)
	{
	}

	bool empty() const
	{
		return m_value == 0;
	}

	bool contains(mali_gralloc_ip ip) const
	{
		return (ip & m_value);
	}

	void add(mali_gralloc_ip ip)
	{
		m_value |= ip;
	}

	void remove(mali_gralloc_ip ip)
	{
		m_value &= ~ip;
	}

	mali_gralloc_ip get() const
	{
		return m_value;
	}

private:
	mali_gralloc_ip m_value = MALI_GRALLOC_IP_NONE;
};

/**
 * @brief Set of producers.
 */
class producers_t : public ip_t
{
public:
	using ip_t::ip_t;

	bool support(feature_t feature) const
	{
		return ip_support_feature(get(), MALI_GRALLOC_IP_NONE, feature);
	}
};

/**
 * @brief Set of consumers.
 */
class consumers_t : public ip_t
{
public:
	using ip_t::ip_t;

	bool support(feature_t feature) const
	{
		return ip_support_feature(MALI_GRALLOC_IP_NONE, get(), feature);
	}
};

inline bool ip_t::support(producers_t producers, consumers_t consumers, feature_t feature)
{
	return ip_support_feature(producers.get(), consumers.get(), feature);
}

inline bool ip_t::present(ip_t ips)
{
	for (mali_gralloc_ip ip = 1; ip <= ips.get() && ip != 0; ip <<= 1)
	{
		/* The call to ip_support_feature() returns true iff:
		 * - ip is not found in the configuration files
		 * - ip is explictly marked as disabled in the configuration files for both read/write
		 */
		if (ips.contains(ip) && ip_support_feature(ip, ip, feature_t::DISABLED))
		{
			return false;
		}
	}
	return true;
}
