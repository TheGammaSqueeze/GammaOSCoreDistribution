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

#include <sys/stat.h>
#include <sys/types.h>
#include <unistd.h>

#include <fstream>
#include <iostream>
#include <optional>
#include <string>
#include <vector>

#include <android-base/file.h>
#include <android-base/result.h>
#include <image_aggregator.h>
#include <json/json.h>

#include "microdroid/metadata.h"

using android::base::Dirname;
using android::base::ErrnoError;
using android::base::Error;
using android::base::Result;
using android::base::unique_fd;
using android::microdroid::ApexPayload;
using android::microdroid::ApkPayload;
using android::microdroid::Metadata;
using android::microdroid::WriteMetadata;

using cuttlefish::AlignToPartitionSize;
using cuttlefish::CreateCompositeDisk;
using cuttlefish::kLinuxFilesystem;
using cuttlefish::MultipleImagePartition;

Result<uint32_t> GetFileSize(const std::string& path) {
    struct stat st;
    if (lstat(path.c_str(), &st) == -1) {
        return ErrnoError() << "Can't lstat " << path;
    }
    return static_cast<uint32_t>(st.st_size);
}

std::string RelativeTo(const std::string& path, const std::string& dirname) {
    bool is_absolute = !path.empty() && path[0] == '/';
    if (is_absolute || dirname == ".") {
        return path;
    } else {
        return dirname + "/" + path;
    }
}

// Returns `append` is appended to the end of filename preserving the extension.
std::string AppendFileName(const std::string& filename, const std::string& append) {
    size_t pos = filename.find_last_of('.');
    if (pos == std::string::npos) {
        return filename + append;
    } else {
        return filename.substr(0, pos) + append + filename.substr(pos);
    }
}

struct ApexConfig {
    std::string name; // the apex name
    std::string path; // the path to the apex file
                      // absolute or relative to the config file
};

struct ApkConfig {
    std::string name;
    std::string path;
    std::string idsig_path;
};

struct Config {
    std::string dirname; // config file's direname to resolve relative paths in the config

    std::vector<ApexConfig> apexes;
    std::optional<ApkConfig> apk;
    // This is a path in the guest side
    std::optional<std::string> payload_config_path;
};

#define DO(expr) \
    if (auto res = (expr); !res.ok()) return res.error()

Result<void> ParseJson(const Json::Value& value, std::string& s) {
    if (!value.isString()) {
        return Error() << "should be a string: " << value;
    }
    s = value.asString();
    return {};
}

template <typename T>
Result<void> ParseJson(const Json::Value& value, std::optional<T>& s) {
    if (value.isNull()) {
        s.reset();
        return {};
    }
    s.emplace();
    return ParseJson(value, *s);
}

template <typename T>
Result<void> ParseJson(const Json::Value& values, std::vector<T>& parsed) {
    for (const Json::Value& value : values) {
        T t;
        DO(ParseJson(value, t));
        parsed.push_back(std::move(t));
    }
    return {};
}

Result<void> ParseJson(const Json::Value& value, ApexConfig& apex_config) {
    DO(ParseJson(value["name"], apex_config.name));
    DO(ParseJson(value["path"], apex_config.path));
    return {};
}

Result<void> ParseJson(const Json::Value& value, ApkConfig& apk_config) {
    DO(ParseJson(value["name"], apk_config.name));
    DO(ParseJson(value["path"], apk_config.path));
    DO(ParseJson(value["idsig_path"], apk_config.idsig_path));
    return {};
}

Result<void> ParseJson(const Json::Value& value, Config& config) {
    DO(ParseJson(value["apexes"], config.apexes));
    DO(ParseJson(value["apk"], config.apk));
    DO(ParseJson(value["payload_config_path"], config.payload_config_path));
    return {};
}

Result<Config> LoadConfig(const std::string& config_file) {
    std::ifstream in(config_file);
    Json::CharReaderBuilder builder;
    Json::Value root;
    Json::String errs;
    if (!parseFromStream(builder, in, &root, &errs)) {
        return Error() << errs;
    }

    Config config;
    config.dirname = Dirname(config_file);
    DO(ParseJson(root, config));
    return config;
}

#undef DO

Result<void> MakeMetadata(const Config& config, const std::string& filename) {
    Metadata metadata;
    metadata.set_version(1);

    int apex_index = 0;
    for (const auto& apex_config : config.apexes) {
        auto* apex = metadata.add_apexes();
        apex->set_name(apex_config.name);
        apex->set_partition_name("microdroid-apex-" + std::to_string(apex_index++));
        apex->set_is_factory(true);
    }

    if (config.apk.has_value()) {
        auto* apk = metadata.mutable_apk();
        apk->set_name(config.apk->name);
        apk->set_payload_partition_name("microdroid-apk");
        apk->set_idsig_partition_name("microdroid-apk-idsig");
    }

    if (config.payload_config_path.has_value()) {
        *metadata.mutable_payload_config_path() = config.payload_config_path.value();
    }

    std::ofstream out(filename);
    return WriteMetadata(metadata, out);
}

// fill zeros to align |file_path|'s size to BLOCK_SIZE(4096) boundary.
// return true when the filler is needed.
Result<bool> ZeroFiller(const std::string& file_path, const std::string& filler_path) {
    auto file_size = GetFileSize(file_path);
    if (!file_size.ok()) {
        return file_size.error();
    }
    auto disk_size = AlignToPartitionSize(*file_size);
    if (disk_size <= *file_size) {
        return false;
    }
    unique_fd fd(TEMP_FAILURE_RETRY(open(filler_path.c_str(), O_CREAT | O_WRONLY | O_TRUNC, 0600)));
    if (fd.get() == -1) {
        return ErrnoError() << "open(" << filler_path << ") failed.";
    }
    if (ftruncate(fd.get(), disk_size - *file_size) == -1) {
        return ErrnoError() << "ftruncate(" << filler_path << ") failed.";
    }
    return true;
}

Result<void> MakePayload(const Config& config, const std::string& metadata_file,
                         const std::string& output_file) {
    std::vector<MultipleImagePartition> partitions;

    int filler_count = 0;
    auto add_partition = [&](auto partition_name, auto file_path) -> Result<void> {
        std::vector<std::string> image_files{file_path};

        std::string filler_path =
                AppendFileName(output_file, "-filler-" + std::to_string(filler_count++));
        if (auto ret = ZeroFiller(file_path, filler_path); !ret.ok()) {
            return ret.error();
        } else if (*ret) {
            image_files.push_back(filler_path);
        }
        partitions.push_back(MultipleImagePartition{
                .label = partition_name,
                .image_file_paths = image_files,
                .type = kLinuxFilesystem,
                .read_only = true,
        });
        return {};
    };

    // put metadata at the first partition
    partitions.push_back(MultipleImagePartition{
            .label = "payload-metadata",
            .image_file_paths = {metadata_file},
            .type = kLinuxFilesystem,
            .read_only = true,
    });
    // put apexes at the subsequent partitions
    for (size_t i = 0; i < config.apexes.size(); i++) {
        const auto& apex_config = config.apexes[i];
        std::string apex_path = RelativeTo(apex_config.path, config.dirname);
        if (auto ret = add_partition("microdroid-apex-" + std::to_string(i), apex_path);
            !ret.ok()) {
            return ret.error();
        }
    }
    // put apk and its idsig
    if (config.apk.has_value()) {
        std::string apk_path = RelativeTo(config.apk->path, config.dirname);
        if (auto ret = add_partition("microdroid-apk", apk_path); !ret.ok()) {
            return ret.error();
        }
        std::string idsig_path = RelativeTo(config.apk->idsig_path, config.dirname);
        if (auto ret = add_partition("microdroid-apk-idsig", idsig_path); !ret.ok()) {
            return ret.error();
        }
    }

    const std::string gpt_header = AppendFileName(output_file, "-header");
    const std::string gpt_footer = AppendFileName(output_file, "-footer");
    CreateCompositeDisk(partitions, gpt_header, gpt_footer, output_file);
    return {};
}

int main(int argc, char** argv) {
    if (argc < 3 || argc > 4) {
        std::cerr << "Usage: " << argv[0] << " [--metadata-only] <config> <output>\n";
        return 1;
    }
    int arg_index = 1;
    bool metadata_only = false;
    if (strcmp(argv[arg_index], "--metadata-only") == 0) {
        metadata_only = true;
        arg_index++;
    }

    auto config = LoadConfig(argv[arg_index++]);
    if (!config.ok()) {
        std::cerr << "bad config: " << config.error() << '\n';
        return 1;
    }

    const std::string output_file(argv[arg_index++]);
    const std::string metadata_file =
            metadata_only ? output_file : AppendFileName(output_file, "-metadata");

    if (const auto res = MakeMetadata(*config, metadata_file); !res.ok()) {
        std::cerr << res.error() << '\n';
        return 1;
    }
    if (metadata_only) {
        return 0;
    }
    if (const auto res = MakePayload(*config, metadata_file, output_file); !res.ok()) {
        std::cerr << res.error() << '\n';
        return 1;
    }

    return 0;
}