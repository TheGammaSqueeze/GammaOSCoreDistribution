/*
 * Copyright (c) 2020 Rockchip Electronics Co., Ltd
 */

#pragma once

#include <aidl/android/hardware/power/BnPower.h>
#include <string>

namespace aidl {
namespace android {
namespace hardware {
namespace power {
namespace impl {
namespace rockchip {

using aidl::android::hardware::power::Boost;

enum class ClusterType {
    CPU,
    GPU,
    DDR,
    NPU,
};

class ClusterInfo {
public:
    ClusterInfo(const ClusterType type, const std::string& clust);

    std::string toString();
    void setMinFreq(const std::string& freq);
    void setMaxFreq(const std::string& freq);
    void setPerformance(bool on);
    void setPowerSave(bool on);
    void setInteractive();
    void setGov(const std::string& governor);
    ClusterType getType() {
        return _type;
    }
private:
    ClusterType _type;
    std::string _minFreqPath;
    std::string _maxFreqPath;
    std::string _govPath;
    std::string _minFreq;
    std::string _maxFreq;
    std::string _govDefault;
};

class Power : public BnPower {
    ndk::ScopedAStatus setMode(Mode type, bool enabled) override;
    ndk::ScopedAStatus isModeSupported(Mode type, bool* _aidl_return) override;
    ndk::ScopedAStatus setBoost(Boost type, int32_t durationMs) override;
    ndk::ScopedAStatus isBoostSupported(Boost type, bool* _aidl_return) override;
    ndk::ScopedAStatus createHintSession(int32_t tgid, int32_t uid,
                                         const std::vector<int32_t>& threadIds,
                                         int64_t durationNanos,
                                         std::shared_ptr<IPowerHintSession>* _aidl_return) override;
    ndk::ScopedAStatus getHintSessionPreferredRate(int64_t* outNanoseconds) override;

private:
    int64_t _boost_support_int = -1;
    int64_t _mode_support_int = -1;
    int8_t _boot_complete = -1;
    std::vector<ClusterInfo> clusterList;

    void getSupportedPlatform();
    void initPlatform();
    void interactive();
    void performanceBoost(bool on);
    void powerSave(bool on);
};

}  // namespace rockchip
}  // namespace impl
}  // namespace power
}  // namespace hardware
}  // namespace android
}  // namespace aidl
