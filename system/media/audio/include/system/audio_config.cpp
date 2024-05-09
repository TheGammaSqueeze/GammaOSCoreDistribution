#include "audio_util.h"

bool property_contains_hdmi(const char* key) {
    char value[PROPERTY_VALUE_MAX] = {};
    property_get(key, value, "");
    std::string valueStr = value;
    // Convert to lowercase for case-insensitive comparison
    std::transform(valueStr.begin(), valueStr.end(), valueStr.begin(),
                   [](unsigned char c) { return std::tolower(c); });

    return valueStr.find("hdmi") != std::string::npos;
}
