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
#include "xml_configuration.h"
#include <optional>
#include <dirent.h>
#include <sys/stat.h>

static const std::string xml_base_path = "/vendor/etc/gralloc";

static std::string gralloc_ip_to_string(mali_gralloc_ip ip)
{
	switch (ip)
	{
	case MALI_GRALLOC_IP_GPU:
		return "GPU";
	case MALI_GRALLOC_IP_DPU:
		return "DPU";
	case MALI_GRALLOC_IP_DPU_AEU:
		return "DPU_AEU";
	case MALI_GRALLOC_IP_VPU:
		return "VPU";
	case MALI_GRALLOC_IP_CAM:
		return "CAM";
	default:
		return "UNKNOWN";
	}
}

ip_capability::ip_capability(mali_gralloc_ip ip)
    : ip_capability{ ip, xml_base_path }
{
}

ip_capability::ip_capability(mali_gralloc_ip ip, const std::string &base_path)
    : m_ip(ip)
{
	auto caps = find_ip_capabilities(base_path);
	if (!caps.has_value())
	{
		MALI_GRALLOC_LOG(ERROR) << "Failed to read capabilities from " << base_path
		                        << "for IP: " << gralloc_ip_to_string(m_ip);
	}
	else
	{
		MALI_GRALLOC_LOG(INFO) << "Read capability file from " << caps->first << " for IP: " << gralloc_ip_to_string(m_ip);

		m_path = caps->first;
		m_caps.emplace(caps->second);
	}
}

capabilities_type::Ip ip_capability::convert_gralloc_ip_to_capabilities_type_ip(mali_gralloc_ip ip)
{
	switch (ip)
	{
	case MALI_GRALLOC_IP_GPU:
		return capabilities_type::Ip::GPU;
	case MALI_GRALLOC_IP_DPU:
		return capabilities_type::Ip::DPU;
	case MALI_GRALLOC_IP_DPU_AEU:
		return capabilities_type::Ip::DPU_AEU;
	case MALI_GRALLOC_IP_VPU:
		return capabilities_type::Ip::VPU;
	case MALI_GRALLOC_IP_CAM:
		return capabilities_type::Ip::CAM;
	default:
		return capabilities_type::Ip::UNKNOWN;
	};
}

static std::optional<std::string> get_file_extension(std::string_view file_name)
{
	size_t dot_index = file_name.rfind('.');
	if (dot_index == std::string::npos || dot_index == file_name.length() - 1)
	{
		return std::nullopt;
	}

	return std::string(file_name.substr(dot_index + 1));
}

static bool is_regular_file(const char *file_path)
{
	struct stat stat_info;
	if (stat(file_path, &stat_info) != 0)
	{
		MALI_GRALLOC_LOG(ERROR) << "Failed to stat file for capability reading: " << file_path << ", error: " << errno;
		return false;
	}
	return S_ISREG(stat_info.st_mode);
}

std::optional<std::pair<std::string, capabilities_type::IpCapabilities>> ip_capability::find_ip_capabilities(
    const std::string &base_path)
{
	const auto dir = opendir(base_path.c_str());
	if (dir == nullptr)
	{
		MALI_GRALLOC_LOG(ERROR) << "Failed to open capability directory: " << base_path << ", error: " << errno;
		return std::nullopt;
	}

	struct dirent *entry = nullptr;
	while ((entry = readdir(dir)) != nullptr)
	{
		std::string full_file_path = base_path + "/" + entry->d_name;
		/* If readdir does not query full file information, use stat to determine the type */
		if (entry->d_type == DT_UNKNOWN)
		{
			if (!is_regular_file(full_file_path.c_str()))
			{
				continue;
			}
		}
		/* Skip any non-regular file types */
		else if (entry->d_type != DT_REG)
		{
			continue;
		}

		auto extension = get_file_extension(entry->d_name);
		if (extension.value_or("") == "xml")
		{
			auto config_file = capabilities_type::readCapabilities(full_file_path.c_str());
			if (!config_file.has_value())
			{
				MALI_GRALLOC_LOG(ERROR) << "Failed to parse XML file "
					<< full_file_path.c_str() << ". Please check the syntax is correct";
				return std::nullopt;
			}
			else
			{
				auto caps = find_ip_capabilities_in_config(*config_file);
				if (caps.has_value())
				{
					return std::make_pair(full_file_path, *caps);
				}
			}
		}
	}

	return std::nullopt;
}

std::optional<capabilities_type::IpCapabilities> ip_capability::find_ip_capabilities_in_config(
    capabilities_type::Capabilities &caps)
{
	auto current_ip = convert_gralloc_ip_to_capabilities_type_ip(m_ip);
	if (current_ip == capabilities_type::Ip::UNKNOWN)
	{
		MALI_GRALLOC_LOG(ERROR) << "Failed to convert Gralloc IP to capabilities IP: " << m_ip;
		return std::nullopt;
	}

	auto &capabilities = caps.getIp_capabilities();
	for (auto &capability : capabilities)
	{
		auto &ip = capability.getIp();
		if (ip == current_ip)
		{
			return capability;
		}
	}

	return std::nullopt;
}

using xml_feature_t = capabilities_type::Name;

static constexpr bool xml_matches_feature(xml_feature_t xml_feature, feature_t feature)
{
	#define CHECK_FEATURE_MATCH(F) \
		case xml_feature_t::F: return feature == feature_t::F;

	switch (xml_feature)
	{
		EXPAND_FEATURES(CHECK_FEATURE_MATCH)
	default:
		return false;
	}
}

bool ip_capability::is_feature_supported(feature_t feature, permission_t permission)
{
	const std::string feature_name = feature_to_name(feature);

	bool feature_found = false;
	for (auto &xml_feature : m_caps->getFeature())
	{
		if (xml_matches_feature(xml_feature.getName(), feature))
		{
			feature_found = true;

			auto &xml_permission = xml_feature.getPermission();
			bool readable = false;
			bool writeable = false;
			switch (xml_permission)
			{
			case capabilities_type::Permission::RW:
				readable = true;
				writeable = true;
				break;
			case capabilities_type::Permission::RO:
				readable = true;
				break;
			case capabilities_type::Permission::WO:
				writeable = true;
				break;
			case capabilities_type::Permission::NO:
				break;
			default:
				MALI_GRALLOC_LOG(ERROR) << "Invalid capabilities from " << m_path;
			}
			switch (permission)
			{
			case permission_t::read:
				MALI_GRALLOC_LOG(INFO) << feature_name << ": getReadable(): " << readable;
				return readable;
			case permission_t::write:
				MALI_GRALLOC_LOG(INFO) << feature_name << ": getWritable(): " << writeable;
				return writeable;
			}
		}
	}

	if (!feature_found)
	{
		MALI_GRALLOC_LOG(ERROR) << "Feature " << feature_name << " not found in " << m_path;
	}

	return false;
}
