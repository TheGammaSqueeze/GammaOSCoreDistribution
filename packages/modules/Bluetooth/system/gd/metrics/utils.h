#pragma once
#include <string>
#include <utility>
#include <vector>
#include "os/metrics.h"
namespace bluetooth {
namespace metrics {
bool GetBootId(std::string* boot_id);
int GetArgumentTypeFromList(
    std::vector<std::pair<os::ArgumentType, int>>& argument_list, os::ArgumentType argumentType);
    os::LeConnectionType GetLeConnectionTypeFromCID(int fixed_cid);
}  // namespace metrics
}
