/*
 * Copyright (C) 2022 ARM Limited. All rights reserved.
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

#include <string>
#include <string_view>

#include "gralloc/formats.h"
#include "capabilities.h"
#include "capabilities_type.h"

/*
 * @brief class for handling access to a capabilities xml file.
 */
class ip_capability
{
public:
	enum class permission_t
	{
		read,
		write
	};

	/**
	 * @brief Construct a new capability for specific IP and use a predefined
	 *        default path to look for capability files
	 *
	 * @param ip The Gralloc IP for which the capabilities will be loaded.
	 */
	ip_capability(mali_gralloc_ip ip);

	/**
	 * @brief Construct a new capability for specific IP
	 *
	 * @param ip The Gralloc IP for which the capabilities will be loaded.
	 * @param base_path The directory to use for capability file lookup.
	 *
	 * Note: The @a base_path will not be iterated recursively.
	 */
	ip_capability(mali_gralloc_ip ip, const std::string &base_path);

	/*
	 * @brief Check if a feature is supported by the ip.
	 *
	 * @param feature Feature.
	 * @param perm    Requested permission for the feature.
	 *
	 * @return true if the feature is supported with the given permission,
	 *         false otherwise.
	 */
	bool is_feature_supported(feature_t feature, permission_t permission);

	mali_gralloc_ip get_ip()
	{
		return m_ip;
	}

	const char *get_path()
	{
		return m_path.c_str();
	}

	bool caps_have_value()
	{
		return m_caps.has_value();
	}

private:
	capabilities_type::Ip convert_gralloc_ip_to_capabilities_type_ip(mali_gralloc_ip ip);

	std::optional<std::pair<std::string, capabilities_type::IpCapabilities>> find_ip_capabilities(const std::string &base_path);
	std::optional<capabilities_type::IpCapabilities> find_ip_capabilities_in_config(capabilities_type::Capabilities &caps);

	mali_gralloc_ip m_ip;
	std::string m_path;
	std::optional<capabilities_type::IpCapabilities> m_caps;
};
