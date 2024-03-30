/*
 * Copyright (C) 2017 The Android Open Source Project
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
 * See the License for the specic language governing permissions and
 * limitations under the License.
 */

#define ATRACE_TAG (ATRACE_TAG_POWER | ATRACE_TAG_HAL)
#define LOG_TAG "libperfmgr"

#include "perfmgr/HintManager.h"

#include <android-base/file.h>
#include <android-base/logging.h>
#include <android-base/properties.h>
#include <android-base/stringprintf.h>
#include <inttypes.h>
#include <json/reader.h>
#include <json/value.h>
#include <utils/Trace.h>

#include <algorithm>
#include <set>

#include "perfmgr/FileNode.h"
#include "perfmgr/PropertyNode.h"

namespace android {
namespace perfmgr {

namespace {
constexpr std::chrono::milliseconds kMilliSecondZero = std::chrono::milliseconds(0);
constexpr std::chrono::steady_clock::time_point kTimePointMax =
        std::chrono::steady_clock::time_point::max();
}  // namespace

constexpr char kPowerHalTruncateProp[] = "vendor.powerhal.truncate";
constexpr std::string_view kConfigDebugPathProperty("vendor.powerhal.config.debug");
constexpr std::string_view kConfigProperty("vendor.powerhal.config");
constexpr std::string_view kConfigDefaultFileName("powerhint.json");

bool HintManager::ValidateHint(const std::string& hint_type) const {
    if (nm_.get() == nullptr) {
        LOG(ERROR) << "NodeLooperThread not present";
        return false;
    }
    return IsHintSupported(hint_type);
}

bool HintManager::IsHintSupported(const std::string& hint_type) const {
    if (actions_.find(hint_type) == actions_.end()) {
        LOG(INFO) << "Hint type not present in actions: " << hint_type;
        return false;
    }
    return true;
}

bool HintManager::IsHintEnabled(const std::string &hint_type) const {
    std::lock_guard<std::mutex> lock(actions_.at(hint_type).hint_lock);
    return actions_.at(hint_type).mask_requesters.empty();
}

bool HintManager::InitHintStatus(const std::unique_ptr<HintManager> &hm) {
    if (hm.get() == nullptr) {
        return false;
    }
    for (auto &a : hm->actions_) {
        // timeout_ms equaling kMilliSecondZero means forever until cancelling.
        // As a result, if there's one NodeAction has timeout_ms of 0, we will store
        // 0 instead of max. Also node actions could be empty, set to 0 in that case.
        std::chrono::milliseconds timeout = kMilliSecondZero;
        if (a.second.node_actions.size()) {
            auto [min, max] =
                    std::minmax_element(a.second.node_actions.begin(), a.second.node_actions.end(),
                                        [](const auto act1, const auto act2) {
                                            return act1.timeout_ms < act2.timeout_ms;
                                        });
            timeout = min->timeout_ms == kMilliSecondZero ? kMilliSecondZero : max->timeout_ms;
        }
        a.second.status.reset(new HintStatus(timeout));
    }
    return true;
}

void HintManager::DoHintStatus(const std::string &hint_type, std::chrono::milliseconds timeout_ms) {
    std::lock_guard<std::mutex> lock(actions_.at(hint_type).hint_lock);
    actions_.at(hint_type).status->stats.count.fetch_add(1);
    auto now = std::chrono::steady_clock::now();
    ATRACE_INT(hint_type.c_str(), (timeout_ms == kMilliSecondZero) ? std::numeric_limits<int>::max()
                                                                   : timeout_ms.count());
    if (now > actions_.at(hint_type).status->end_time) {
        actions_.at(hint_type).status->stats.duration_ms.fetch_add(
                std::chrono::duration_cast<std::chrono::milliseconds>(
                        actions_.at(hint_type).status->end_time -
                        actions_.at(hint_type).status->start_time)
                        .count());
        actions_.at(hint_type).status->start_time = now;
    }
    actions_.at(hint_type).status->end_time =
            (timeout_ms == kMilliSecondZero) ? kTimePointMax : now + timeout_ms;
}

void HintManager::EndHintStatus(const std::string &hint_type) {
    std::lock_guard<std::mutex> lock(actions_.at(hint_type).hint_lock);
    // Update HintStats if the hint ends earlier than expected end_time
    auto now = std::chrono::steady_clock::now();
    ATRACE_INT(hint_type.c_str(), 0);
    if (now < actions_.at(hint_type).status->end_time) {
        actions_.at(hint_type).status->stats.duration_ms.fetch_add(
                std::chrono::duration_cast<std::chrono::milliseconds>(
                        now - actions_.at(hint_type).status->start_time)
                        .count());
        actions_.at(hint_type).status->end_time = now;
    }
}

void HintManager::DoHintAction(const std::string &hint_type) {
    for (auto &action : actions_.at(hint_type).hint_actions) {
        if (!action.enable_property.empty() &&
            !android::base::GetBoolProperty(action.enable_property, true)) {
            // Disabled action based on its control property
            continue;
        }
        switch (action.type) {
            case HintActionType::DoHint:
                DoHint(action.value);
                break;
            case HintActionType::EndHint:
                EndHint(action.value);
                break;
            case HintActionType::MaskHint:
                if (actions_.find(action.value) == actions_.end()) {
                    LOG(ERROR) << "Failed to find " << action.value << " action";
                } else {
                    std::lock_guard<std::mutex> lock(actions_.at(hint_type).hint_lock);
                    actions_.at(action.value).mask_requesters.insert(hint_type);
                }
                break;
            default:
                // should not reach here
                LOG(ERROR) << "Invalid "
                           << static_cast<std::underlying_type<HintActionType>::type>(action.type)
                           << " type";
        }
    }
}

void HintManager::EndHintAction(const std::string &hint_type) {
    for (auto &action : actions_.at(hint_type).hint_actions) {
        if (action.type == HintActionType::MaskHint &&
            actions_.find(action.value) != actions_.end()) {
            std::lock_guard<std::mutex> lock(actions_.at(hint_type).hint_lock);
            actions_.at(action.value).mask_requesters.erase(hint_type);
        }
    }
}

bool HintManager::DoHint(const std::string& hint_type) {
    LOG(VERBOSE) << "Do Powerhint: " << hint_type;
    if (!ValidateHint(hint_type) || !IsHintEnabled(hint_type) ||
        !nm_->Request(actions_.at(hint_type).node_actions, hint_type)) {
        return false;
    }
    DoHintStatus(hint_type, actions_.at(hint_type).status->max_timeout);
    DoHintAction(hint_type);
    return true;
}

bool HintManager::DoHint(const std::string& hint_type,
                         std::chrono::milliseconds timeout_ms_override) {
    LOG(VERBOSE) << "Do Powerhint: " << hint_type << " for "
                 << timeout_ms_override.count() << "ms";
    if (!ValidateHint(hint_type) || !IsHintEnabled(hint_type)) {
        return false;
    }
    std::vector<NodeAction> actions_override = actions_.at(hint_type).node_actions;
    for (auto& action : actions_override) {
        action.timeout_ms = timeout_ms_override;
    }
    if (!nm_->Request(actions_override, hint_type)) {
        return false;
    }
    DoHintStatus(hint_type, timeout_ms_override);
    DoHintAction(hint_type);
    return true;
}

bool HintManager::EndHint(const std::string& hint_type) {
    LOG(VERBOSE) << "End Powerhint: " << hint_type;
    if (!ValidateHint(hint_type) || !nm_->Cancel(actions_.at(hint_type).node_actions, hint_type)) {
        return false;
    }
    EndHintStatus(hint_type);
    EndHintAction(hint_type);
    return true;
}

bool HintManager::IsRunning() const {
    return (nm_.get() == nullptr) ? false : nm_->isRunning();
}

std::vector<std::string> HintManager::GetHints() const {
    std::vector<std::string> hints;
    for (auto const& action : actions_) {
        hints.push_back(action.first);
    }
    return hints;
}

HintStats HintManager::GetHintStats(const std::string &hint_type) const {
    HintStats hint_stats;
    if (ValidateHint(hint_type)) {
        std::lock_guard<std::mutex> lock(actions_.at(hint_type).hint_lock);
        hint_stats.count =
                actions_.at(hint_type).status->stats.count.load(std::memory_order_relaxed);
        hint_stats.duration_ms =
                actions_.at(hint_type).status->stats.duration_ms.load(std::memory_order_relaxed);
    }
    return hint_stats;
}

void HintManager::DumpToFd(int fd) {
    std::string header("========== Begin perfmgr nodes ==========\n");
    if (!android::base::WriteStringToFd(header, fd)) {
        LOG(ERROR) << "Failed to dump fd: " << fd;
    }
    nm_->DumpToFd(fd);
    std::string footer("==========  End perfmgr nodes  ==========\n");
    if (!android::base::WriteStringToFd(footer, fd)) {
        LOG(ERROR) << "Failed to dump fd: " << fd;
    }
    header = "========== Begin perfmgr stats ==========\n"
             "Hint Name\t"
             "Counts\t"
             "Duration\n";
    if (!android::base::WriteStringToFd(header, fd)) {
        LOG(ERROR) << "Failed to dump fd: " << fd;
    }
    std::string hint_stats_string;
    std::vector<std::string> keys(GetHints());
    std::sort(keys.begin(), keys.end());
    for (const auto &ordered_key : keys) {
        HintStats hint_stats(GetHintStats(ordered_key));
        hint_stats_string +=
                android::base::StringPrintf("%s\t%" PRIu32 "\t%" PRIu64 "\n", ordered_key.c_str(),
                                            hint_stats.count, hint_stats.duration_ms);
    }
    if (!android::base::WriteStringToFd(hint_stats_string, fd)) {
        LOG(ERROR) << "Failed to dump fd: " << fd;
    }
    footer = "==========  End perfmgr stats  ==========\n";
    if (!android::base::WriteStringToFd(footer, fd)) {
        LOG(ERROR) << "Failed to dump fd: " << fd;
    }

    // Dump current ADPF profile
    if (GetAdpfProfile()) {
        header = "========== Begin current adpf profile ==========\n";
        if (!android::base::WriteStringToFd(header, fd)) {
            LOG(ERROR) << "Failed to dump fd: " << fd;
        }
        GetAdpfProfile()->dumpToFd(fd);
        footer = "==========  End current adpf profile  ==========\n";
        if (!android::base::WriteStringToFd(footer, fd)) {
            LOG(ERROR) << "Failed to dump fd: " << fd;
        }
    }
    fsync(fd);
}

bool HintManager::Start() {
    return nm_->Start();
}

std::shared_ptr<HintManager> HintManager::mInstance = nullptr;

std::shared_ptr<HintManager> HintManager::Reload(bool start) {
    std::string config_path = "/vendor/etc/";
    if (android::base::GetBoolProperty(kConfigDebugPathProperty.data(), false)) {
        config_path = "/data/vendor/etc/";
        LOG(WARNING) << "Pixel Power HAL AIDL Service is using debug config from: " << config_path;
    }
    config_path.append(
            android::base::GetProperty(kConfigProperty.data(), kConfigDefaultFileName.data()));

    LOG(INFO) << "Pixel Power HAL AIDL Service with Extension is starting with config: "
              << config_path;
    // Reload and start the HintManager
    mInstance = HintManager::GetFromJSON(config_path, start);
    if (!mInstance) {
        LOG(FATAL) << "Invalid config: " << config_path;
    }
    return mInstance;
}

std::shared_ptr<HintManager> HintManager::GetInstance() {
    if (!mInstance) {
        mInstance = HintManager::Reload(false);
    }
    return mInstance;
}

std::unique_ptr<HintManager> HintManager::GetFromJSON(
    const std::string& config_path, bool start) {
    std::string json_doc;

    if (!android::base::ReadFileToString(config_path, &json_doc)) {
        LOG(ERROR) << "Failed to read JSON config from " << config_path;
        return nullptr;
    }

    std::vector<std::unique_ptr<Node>> nodes = ParseNodes(json_doc);
    if (nodes.empty()) {
        LOG(ERROR) << "Failed to parse Nodes section from " << config_path;
        return nullptr;
    }
    std::vector<std::shared_ptr<AdpfConfig>> adpfs = HintManager::ParseAdpfConfigs(json_doc);
    if (adpfs.empty()) {
        LOG(INFO) << "No AdpfConfig section in the " << config_path;
    }

    std::unordered_map<std::string, Hint> actions = HintManager::ParseActions(json_doc, nodes);

    if (actions.empty()) {
        LOG(ERROR) << "Failed to parse Actions section from " << config_path;
        return nullptr;
    }

    sp<NodeLooperThread> nm = new NodeLooperThread(std::move(nodes));
    std::unique_ptr<HintManager> hm = std::make_unique<HintManager>(std::move(nm), actions, adpfs);

    if (!HintManager::InitHintStatus(hm)) {
        LOG(ERROR) << "Failed to initialize hint status";
        return nullptr;
    }

    LOG(INFO) << "Initialized HintManager from JSON config: " << config_path;

    if (start) {
        hm->Start();
    }
    return hm;
}

std::vector<std::unique_ptr<Node>> HintManager::ParseNodes(
    const std::string& json_doc) {
    // function starts
    std::vector<std::unique_ptr<Node>> nodes_parsed;
    std::set<std::string> nodes_name_parsed;
    std::set<std::string> nodes_path_parsed;
    Json::Value root;
    Json::CharReaderBuilder builder;
    std::unique_ptr<Json::CharReader> reader(builder.newCharReader());
    std::string errorMessage;

    if (!reader->parse(&*json_doc.begin(), &*json_doc.end(), &root, &errorMessage)) {
        LOG(ERROR) << "Failed to parse JSON config: " << errorMessage;
        return nodes_parsed;
    }

    Json::Value nodes = root["Nodes"];
    for (Json::Value::ArrayIndex i = 0; i < nodes.size(); ++i) {
        std::string name = nodes[i]["Name"].asString();
        LOG(VERBOSE) << "Node[" << i << "]'s Name: " << name;
        if (name.empty()) {
            LOG(ERROR) << "Failed to read "
                       << "Node[" << i << "]'s Name";
            nodes_parsed.clear();
            return nodes_parsed;
        }

        auto result = nodes_name_parsed.insert(name);
        if (!result.second) {
            LOG(ERROR) << "Duplicate Node[" << i << "]'s Name";
            nodes_parsed.clear();
            return nodes_parsed;
        }

        std::string path = nodes[i]["Path"].asString();
        LOG(VERBOSE) << "Node[" << i << "]'s Path: " << path;
        if (path.empty()) {
            LOG(ERROR) << "Failed to read "
                       << "Node[" << i << "]'s Path";
            nodes_parsed.clear();
            return nodes_parsed;
        }

        result = nodes_path_parsed.insert(path);
        if (!result.second) {
            LOG(ERROR) << "Duplicate Node[" << i << "]'s Path";
            nodes_parsed.clear();
            return nodes_parsed;
        }

        bool is_file = true;
        std::string node_type = nodes[i]["Type"].asString();
        LOG(VERBOSE) << "Node[" << i << "]'s Type: " << node_type;
        if (node_type.empty()) {
            LOG(VERBOSE) << "Failed to read "
                         << "Node[" << i << "]'s Type, set to 'File' as default";
        } else if (node_type == "File") {
            is_file = true;
        } else if (node_type == "Property") {
            is_file = false;
        } else {
            LOG(ERROR) << "Invalid Node[" << i
                       << "]'s Type: only File and Property supported.";
            nodes_parsed.clear();
            return nodes_parsed;
        }

        std::vector<RequestGroup> values_parsed;
        std::set<std::string> values_set_parsed;
        Json::Value values = nodes[i]["Values"];
        for (Json::Value::ArrayIndex j = 0; j < values.size(); ++j) {
            std::string value = values[j].asString();
            LOG(VERBOSE) << "Node[" << i << "]'s Value[" << j << "]: " << value;
            auto result = values_set_parsed.insert(value);
            if (!result.second) {
                LOG(ERROR) << "Duplicate value parsed in Node[" << i
                           << "]'s Value[" << j << "]";
                nodes_parsed.clear();
                return nodes_parsed;
            }
            if (is_file && value.empty()) {
                LOG(ERROR) << "Failed to read Node[" << i << "]'s Value[" << j
                           << "]";
                nodes_parsed.clear();
                return nodes_parsed;
            }
            values_parsed.emplace_back(value);
        }
        if (values_parsed.size() < 1) {
            LOG(ERROR) << "Failed to read Node[" << i << "]'s Values";
            nodes_parsed.clear();
            return nodes_parsed;
        }

        Json::UInt64 default_index = values_parsed.size() - 1;
        if (nodes[i]["DefaultIndex"].empty() ||
            !nodes[i]["DefaultIndex"].isUInt64()) {
            LOG(INFO) << "Failed to read Node[" << i
                      << "]'s DefaultIndex, set to last index: "
                      << default_index;
        } else {
            default_index = nodes[i]["DefaultIndex"].asUInt64();
        }
        if (default_index > values_parsed.size() - 1) {
            default_index = values_parsed.size() - 1;
            LOG(ERROR) << "Node[" << i
                       << "]'s DefaultIndex out of bound, max value index: "
                       << default_index;
            nodes_parsed.clear();
            return nodes_parsed;
        }
        LOG(VERBOSE) << "Node[" << i << "]'s DefaultIndex: " << default_index;

        bool reset = false;
        if (nodes[i]["ResetOnInit"].empty() ||
            !nodes[i]["ResetOnInit"].isBool()) {
            LOG(INFO) << "Failed to read Node[" << i
                      << "]'s ResetOnInit, set to 'false'";
        } else {
            reset = nodes[i]["ResetOnInit"].asBool();
        }
        LOG(VERBOSE) << "Node[" << i << "]'s ResetOnInit: " << std::boolalpha
                     << reset << std::noboolalpha;

        if (is_file) {
            bool truncate = android::base::GetBoolProperty(kPowerHalTruncateProp, true);
            if (nodes[i]["Truncate"].empty() || !nodes[i]["Truncate"].isBool()) {
                LOG(INFO) << "Failed to read Node[" << i << "]'s Truncate, set to 'true'";
            } else {
                truncate = nodes[i]["Truncate"].asBool();
            }
            LOG(VERBOSE) << "Node[" << i << "]'s Truncate: " << std::boolalpha << truncate
                         << std::noboolalpha;

            bool hold_fd = false;
            if (nodes[i]["HoldFd"].empty() || !nodes[i]["HoldFd"].isBool()) {
                LOG(INFO) << "Failed to read Node[" << i
                          << "]'s HoldFd, set to 'false'";
            } else {
                hold_fd = nodes[i]["HoldFd"].asBool();
            }
            LOG(VERBOSE) << "Node[" << i << "]'s HoldFd: " << std::boolalpha
                         << hold_fd << std::noboolalpha;

            nodes_parsed.emplace_back(std::make_unique<FileNode>(
                    name, path, values_parsed, static_cast<std::size_t>(default_index), reset,
                    truncate, hold_fd));
        } else {
            nodes_parsed.emplace_back(std::make_unique<PropertyNode>(
                name, path, values_parsed,
                static_cast<std::size_t>(default_index), reset));
        }
    }
    LOG(INFO) << nodes_parsed.size() << " Nodes parsed successfully";
    return nodes_parsed;
}

std::unordered_map<std::string, Hint> HintManager::ParseActions(
        const std::string &json_doc, const std::vector<std::unique_ptr<Node>> &nodes) {
    // function starts
    std::unordered_map<std::string, Hint> actions_parsed;
    Json::Value root;
    Json::CharReaderBuilder builder;
    std::unique_ptr<Json::CharReader> reader(builder.newCharReader());
    std::string errorMessage;

    if (!reader->parse(&*json_doc.begin(), &*json_doc.end(), &root, &errorMessage)) {
        LOG(ERROR) << "Failed to parse JSON config";
        return actions_parsed;
    }

    Json::Value actions = root["Actions"];
    std::size_t total_parsed = 0;

    std::map<std::string, std::size_t> nodes_index;
    for (std::size_t i = 0; i < nodes.size(); ++i) {
        nodes_index[nodes[i]->GetName()] = i;
    }

    for (Json::Value::ArrayIndex i = 0; i < actions.size(); ++i) {
        const std::string& hint_type = actions[i]["PowerHint"].asString();
        LOG(VERBOSE) << "Action[" << i << "]'s PowerHint: " << hint_type;
        if (hint_type.empty()) {
            LOG(ERROR) << "Failed to read "
                       << "Action[" << i << "]'s PowerHint";
            actions_parsed.clear();
            return actions_parsed;
        }

        HintActionType action_type = HintActionType::Node;
        std::string type_string = actions[i]["Type"].asString();
        std::string enable_property = actions[i]["EnableProperty"].asString();
        LOG(VERBOSE) << "Action[" << i << "]'s Type: " << type_string;
        if (type_string.empty()) {
            LOG(VERBOSE) << "Failed to read "
                         << "Action[" << i << "]'s Type, set to 'Node' as default";
        } else if (type_string == "DoHint") {
            action_type = HintActionType::DoHint;
        } else if (type_string == "EndHint") {
            action_type = HintActionType::EndHint;
        } else if (type_string == "MaskHint") {
            action_type = HintActionType::MaskHint;
        } else {
            LOG(ERROR) << "Invalid Action[" << i << "]'s Type: " << type_string;
            actions_parsed.clear();
            return actions_parsed;
        }
        if (action_type == HintActionType::Node) {
            std::string node_name = actions[i]["Node"].asString();
            LOG(VERBOSE) << "Action[" << i << "]'s Node: " << node_name;
            std::size_t node_index;

            if (nodes_index.find(node_name) == nodes_index.end()) {
                LOG(ERROR) << "Failed to find "
                           << "Action[" << i << "]'s Node from Nodes section: [" << node_name
                           << "]";
                actions_parsed.clear();
                return actions_parsed;
            }
            node_index = nodes_index[node_name];

            std::string value_name = actions[i]["Value"].asString();
            LOG(VERBOSE) << "Action[" << i << "]'s Value: " << value_name;
            std::size_t value_index = 0;

            if (!nodes[node_index]->GetValueIndex(value_name, &value_index)) {
                LOG(ERROR) << "Failed to read Action[" << i << "]'s Value";
                LOG(ERROR) << "Action[" << i << "]'s Value " << value_name
                           << " is not defined in Node[" << node_name << "]";
                actions_parsed.clear();
                return actions_parsed;
            }
            LOG(VERBOSE) << "Action[" << i << "]'s ValueIndex: " << value_index;

            Json::UInt64 duration = 0;
            if (actions[i]["Duration"].empty() || !actions[i]["Duration"].isUInt64()) {
                LOG(ERROR) << "Failed to read Action[" << i << "]'s Duration";
                actions_parsed.clear();
                return actions_parsed;
            } else {
                duration = actions[i]["Duration"].asUInt64();
            }
            LOG(VERBOSE) << "Action[" << i << "]'s Duration: " << duration;

            for (const auto &action : actions_parsed[hint_type].node_actions) {
                if (action.node_index == node_index) {
                    LOG(ERROR)
                        << "Action[" << i
                        << "]'s NodeIndex is duplicated with another Action";
                    actions_parsed.clear();
                    return actions_parsed;
                }
            }
            actions_parsed[hint_type].node_actions.emplace_back(
                    node_index, value_index, std::chrono::milliseconds(duration), enable_property);

        } else {
            const std::string &hint_value = actions[i]["Value"].asString();
            LOG(VERBOSE) << "Action[" << i << "]'s Value: " << hint_value;
            if (hint_value.empty()) {
                LOG(ERROR) << "Failed to read "
                           << "Action[" << i << "]'s Value";
                actions_parsed.clear();
                return actions_parsed;
            }
            actions_parsed[hint_type].hint_actions.emplace_back(action_type, hint_value,
                                                                enable_property);
        }

        ++total_parsed;
    }

    LOG(INFO) << total_parsed << " actions parsed successfully";

    for (const auto& action : actions_parsed) {
        LOG(INFO) << "PowerHint " << action.first << " has " << action.second.node_actions.size()
                  << " node actions"
                  << ", and " << action.second.hint_actions.size() << " hint actions parsed";
    }

    return actions_parsed;
}

std::vector<std::shared_ptr<AdpfConfig>> HintManager::ParseAdpfConfigs(
        const std::string &json_doc) {
    // function starts
    bool pidOn;
    double pidPOver;
    double pidPUnder;
    double pidI;
    double pidDOver;
    double pidDUnder;
    int64_t pidIInit;
    int64_t pidIHighLimit;
    int64_t pidILowLimit;
    bool adpfUclamp;
    uint32_t uclampMinInit;
    uint32_t uclampMinHighLimit;
    uint32_t uclampMinLowLimit;
    uint64_t samplingWindowP;
    uint64_t samplingWindowI;
    uint64_t samplingWindowD;
    double staleTimeFactor;
    uint64_t reportingRate;
    bool earlyBoostOn;
    double earlyBoostTimeFactor;
    double targetTimeFactor;
    std::vector<std::shared_ptr<AdpfConfig>> adpfs_parsed;
    std::set<std::string> name_parsed;
    Json::Value root;
    Json::CharReaderBuilder builder;
    std::unique_ptr<Json::CharReader> reader(builder.newCharReader());
    std::string errorMessage;
    if (!reader->parse(&*json_doc.begin(), &*json_doc.end(), &root, &errorMessage)) {
        LOG(ERROR) << "Failed to parse JSON config: " << errorMessage;
        return adpfs_parsed;
    }
    Json::Value adpfs = root["AdpfConfig"];
    for (Json::Value::ArrayIndex i = 0; i < adpfs.size(); ++i) {
        std::string name = adpfs[i]["Name"].asString();
        LOG(VERBOSE) << "AdpfConfig[" << i << "]'s Name: " << name;
        if (name.empty()) {
            LOG(ERROR) << "Failed to read "
                       << "AdpfConfig[" << i << "]'s Name";
            adpfs_parsed.clear();
            return adpfs_parsed;
        }
        auto result = name_parsed.insert(name);
        if (!result.second) {
            LOG(ERROR) << "Duplicate AdpfConfig[" << i << "]'s Name";
            adpfs_parsed.clear();
            return adpfs_parsed;
        }

        if (adpfs[i]["PID_On"].empty() || !adpfs[i]["PID_On"].isBool()) {
            LOG(ERROR) << "Failed to read AdpfConfig[" << name << "][PID_On]'s Values";
            adpfs_parsed.clear();
            return adpfs_parsed;
        }
        pidOn = adpfs[i]["PID_On"].asBool();

        if (adpfs[i]["PID_Po"].empty() || !adpfs[i]["PID_Po"].isDouble()) {
            LOG(ERROR) << "Failed to read AdpfConfig[" << name << "][PID_Po]'s Values";
            adpfs_parsed.clear();
            return adpfs_parsed;
        }
        pidPOver = adpfs[i]["PID_Po"].asDouble();

        if (adpfs[i]["PID_Pu"].empty() || !adpfs[i]["PID_Pu"].isDouble()) {
            LOG(ERROR) << "Failed to read AdpfConfig[" << name << "][PID_Pu]'s Values";
            adpfs_parsed.clear();
            return adpfs_parsed;
        }
        pidPUnder = adpfs[i]["PID_Pu"].asDouble();

        if (adpfs[i]["PID_I"].empty() || !adpfs[i]["PID_I"].isDouble()) {
            LOG(ERROR) << "Failed to read AdpfConfig[" << name << "][PID_I]'s Values";
            adpfs_parsed.clear();
            return adpfs_parsed;
        }
        pidI = adpfs[i]["PID_I"].asDouble();

        if (adpfs[i]["PID_I_Init"].empty() || !adpfs[i]["PID_I_Init"].isInt64()) {
            LOG(ERROR) << "Failed to read AdpfConfig[" << name << "][PID_I_Init]'s Values";
            adpfs_parsed.clear();
            return adpfs_parsed;
        }
        pidIInit = adpfs[i]["PID_I_Init"].asInt64();

        if (adpfs[i]["PID_I_High"].empty() || !adpfs[i]["PID_I_High"].isInt64()) {
            LOG(ERROR) << "Failed to read AdpfConfig[" << name << "][PID_I_High]'s Values";
            adpfs_parsed.clear();
            return adpfs_parsed;
        }
        pidIHighLimit = adpfs[i]["PID_I_High"].asInt64();

        if (adpfs[i]["PID_I_Low"].empty() || !adpfs[i]["PID_I_Low"].isInt64()) {
            LOG(ERROR) << "Failed to read AdpfConfig[" << name << "][PID_I_Low]'s Values";
            adpfs_parsed.clear();
            return adpfs_parsed;
        }
        pidILowLimit = adpfs[i]["PID_I_Low"].asInt64();

        if (adpfs[i]["PID_Do"].empty() || !adpfs[i]["PID_Do"].isDouble()) {
            LOG(ERROR) << "Failed to read AdpfConfig[" << name << "][PID_Do]'s Values";
            adpfs_parsed.clear();
            return adpfs_parsed;
        }
        pidDOver = adpfs[i]["PID_Do"].asDouble();

        if (adpfs[i]["PID_Du"].empty() || !adpfs[i]["PID_Du"].isDouble()) {
            LOG(ERROR) << "Failed to read AdpfConfig[" << name << "][PID_Du]'s Values";
            adpfs_parsed.clear();
            return adpfs_parsed;
        }
        pidDUnder = adpfs[i]["PID_Du"].asDouble();

        if (adpfs[i]["UclampMin_On"].empty() || !adpfs[i]["UclampMin_On"].isBool()) {
            LOG(ERROR) << "Failed to read AdpfConfig[" << name << "][UclampMin_On]'s Values";
            adpfs_parsed.clear();
            return adpfs_parsed;
        }
        adpfUclamp = adpfs[i]["UclampMin_On"].asBool();

        if (adpfs[i]["UclampMin_Init"].empty() || !adpfs[i]["UclampMin_Init"].isInt()) {
            LOG(ERROR) << "Failed to read AdpfConfig[" << name << "][UclampMin_Init]'s Values";
            adpfs_parsed.clear();
            return adpfs_parsed;
        }
        uclampMinInit = adpfs[i]["UclampMin_Init"].asInt();

        if (adpfs[i]["UclampMin_High"].empty() || !adpfs[i]["UclampMin_High"].isUInt()) {
            LOG(ERROR) << "Failed to read AdpfConfig[" << name << "][UclampMin_High]'s Values";
            adpfs_parsed.clear();
            return adpfs_parsed;
        }
        uclampMinHighLimit = adpfs[i]["UclampMin_High"].asUInt();

        if (adpfs[i]["UclampMin_Low"].empty() || !adpfs[i]["UclampMin_Low"].isUInt()) {
            LOG(ERROR) << "Failed to read AdpfConfig[" << name << "][UclampMin_Low]'s Values";
            adpfs_parsed.clear();
            return adpfs_parsed;
        }
        uclampMinLowLimit = adpfs[i]["UclampMin_Low"].asUInt();

        if (adpfs[i]["SamplingWindow_P"].empty() || !adpfs[i]["SamplingWindow_P"].isUInt64()) {
            LOG(ERROR) << "Failed to read AdpfConfig[" << name << "][SamplingWindow_P]'s Values";
            adpfs_parsed.clear();
            return adpfs_parsed;
        }
        samplingWindowP = adpfs[i]["SamplingWindow_P"].asUInt64();

        if (adpfs[i]["SamplingWindow_I"].empty() || !adpfs[i]["SamplingWindow_I"].isUInt64()) {
            LOG(ERROR) << "Failed to read AdpfConfig[" << name << "][SamplingWindow_I]'s Values";
            adpfs_parsed.clear();
            return adpfs_parsed;
        }
        samplingWindowI = adpfs[i]["SamplingWindow_I"].asUInt64();

        if (adpfs[i]["SamplingWindow_D"].empty() || !adpfs[i]["SamplingWindow_D"].isUInt64()) {
            LOG(ERROR) << "Failed to read AdpfConfig[" << name << "][SamplingWindow_D]'s Values";
            adpfs_parsed.clear();
            return adpfs_parsed;
        }
        samplingWindowD = adpfs[i]["SamplingWindow_D"].asUInt64();

        if (adpfs[i]["StaleTimeFactor"].empty() || !adpfs[i]["StaleTimeFactor"].isUInt64()) {
            LOG(ERROR) << "Failed to read AdpfConfig[" << name << "][StaleTimeFactor]'s Values";
            adpfs_parsed.clear();
            return adpfs_parsed;
        }
        staleTimeFactor = adpfs[i]["StaleTimeFactor"].asDouble();

        if (adpfs[i]["ReportingRateLimitNs"].empty() ||
            !adpfs[i]["ReportingRateLimitNs"].isInt64()) {
            LOG(ERROR) << "Failed to read AdpfConfig[" << name
                       << "][ReportingRateLimitNs]'s Values";
            adpfs_parsed.clear();
            return adpfs_parsed;
        }
        reportingRate = adpfs[i]["ReportingRateLimitNs"].asInt64();

        if (adpfs[i]["EarlyBoost_On"].empty() || !adpfs[i]["EarlyBoost_On"].isBool()) {
            LOG(ERROR) << "Failed to read AdpfConfig[" << name << "][EarlyBoost_On]'s Values";
            adpfs_parsed.clear();
            return adpfs_parsed;
        }
        earlyBoostOn = adpfs[i]["EarlyBoost_On"].asBool();

        if (adpfs[i]["EarlyBoost_TimeFactor"].empty() ||
            !adpfs[i]["EarlyBoost_TimeFactor"].isDouble()) {
            LOG(ERROR) << "Failed to read AdpfConfig[" << name
                       << "][EarlyBoost_TimeFactor]'s Values";
            adpfs_parsed.clear();
            return adpfs_parsed;
        }
        earlyBoostTimeFactor = adpfs[i]["EarlyBoost_TimeFactor"].asDouble();

        if (adpfs[i]["TargetTimeFactor"].empty() || !adpfs[i]["TargetTimeFactor"].isDouble()) {
            LOG(ERROR) << "Failed to read AdpfConfig[" << name << "][TargetTimeFactor]'s Values";
            adpfs_parsed.clear();
            return adpfs_parsed;
        }
        targetTimeFactor = adpfs[i]["TargetTimeFactor"].asDouble();

        adpfs_parsed.emplace_back(std::make_shared<AdpfConfig>(
                name, pidOn, pidPOver, pidPUnder, pidI, pidIInit, pidIHighLimit, pidILowLimit,
                pidDOver, pidDUnder, adpfUclamp, uclampMinInit, uclampMinHighLimit,
                uclampMinLowLimit, samplingWindowP, samplingWindowI, samplingWindowD, reportingRate,
                earlyBoostOn, earlyBoostTimeFactor, targetTimeFactor, staleTimeFactor));
    }
    LOG(INFO) << adpfs_parsed.size() << " AdpfConfigs parsed successfully";
    return adpfs_parsed;
}

std::shared_ptr<AdpfConfig> HintManager::GetAdpfProfile() const {
    if (adpfs_.empty())
        return nullptr;
    return adpfs_[adpf_index_];
}

bool HintManager::SetAdpfProfile(const std::string &profile_name) {
    for (std::size_t i = 0; i < adpfs_.size(); ++i) {
        if (adpfs_[i]->mName == profile_name) {
            adpf_index_ = i;
            return true;
        }
    }
    return false;
}

}  // namespace perfmgr
}  // namespace android
