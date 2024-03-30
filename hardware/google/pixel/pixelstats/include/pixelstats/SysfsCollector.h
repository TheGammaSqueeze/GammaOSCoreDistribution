/*
 * Copyright (C) 2018 The Android Open Source Project
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

#ifndef HARDWARE_GOOGLE_PIXEL_PIXELSTATS_SYSFSCOLLECTOR_H
#define HARDWARE_GOOGLE_PIXEL_PIXELSTATS_SYSFSCOLLECTOR_H

#include <aidl/android/frameworks/stats/IStats.h>
#include <hardware/google/pixel/pixelstats/pixelatoms.pb.h>

#include "BatteryEEPROMReporter.h"
#include "BatteryHealthReporter.h"
#include "MitigationStatsReporter.h"
#include "MmMetricsReporter.h"
#include "TempResidencyReporter.h"
#include "ThermalStatsReporter.h"

namespace android {
namespace hardware {
namespace google {
namespace pixel {

using aidl::android::frameworks::stats::IStats;
using android::hardware::google::pixel::PixelAtoms::VendorSlowIo;

class SysfsCollector {
  public:
    struct SysfsPaths {
        const char *const SlowioReadCntPath;
        const char *const SlowioWriteCntPath;
        const char *const SlowioUnmapCntPath;
        const char *const SlowioSyncCntPath;
        const char *const CycleCountBinsPath;
        const char *const ImpedancePath;
        const char *const CodecPath;
        const char *const Codec1Path;
        const char *const SpeechDspPath;
        const char *const BatteryCapacityCC;
        const char *const BatteryCapacityVFSOC;
        const char *const UFSLifetimeA;
        const char *const UFSLifetimeB;
        const char *const UFSLifetimeC;
        const char *const F2fsStatsPath;
        const char *const UserdataBlockProp;
        const char *const ZramMmStatPath;
        const char *const ZramBdStatPath;
        const char *const EEPROMPath;
        const char *const MitigationPath;
        const char *const SpeakerTemperaturePath;
        const char *const SpeakerExcursionPath;
        const char *const SpeakerHeartBeatPath;
        const std::vector<std::string> UFSErrStatsPath;
        const int BlockStatsLength;
        const char *const AmsRatePath;
        const std::vector<std::string> ThermalStatsPaths;
        const char *const CCARatePath;
        const char *const TempResidencyPath;
        const char *const LongIRQMetricsPath;
        const char *const ResumeLatencyMetricsPath;
        const char *const ModemPcieLinkStatsPath;
        const char *const WifiPcieLinkStatsPath;
    };

    SysfsCollector(const struct SysfsPaths &paths);
    void collect();

  private:
    bool ReadFileToInt(const std::string &path, int *val);
    bool ReadFileToInt(const char *path, int *val);
    void aggregatePer5Min();
    void logPerDay();
    void logPerHour();

    void logBatteryChargeCycles(const std::shared_ptr<IStats> &stats_client);
    void logBatteryHealth(const std::shared_ptr<IStats> &stats_client);
    void logBlockStatsReported(const std::shared_ptr<IStats> &stats_client);
    void logCodecFailed(const std::shared_ptr<IStats> &stats_client);
    void logCodec1Failed(const std::shared_ptr<IStats> &stats_client);
    void logSlowIO(const std::shared_ptr<IStats> &stats_client);
    void logSpeakerImpedance(const std::shared_ptr<IStats> &stats_client);
    void logSpeechDspStat(const std::shared_ptr<IStats> &stats_client);
    void logBatteryCapacity(const std::shared_ptr<IStats> &stats_client);
    void logUFSLifetime(const std::shared_ptr<IStats> &stats_client);
    void logUFSErrorStats(const std::shared_ptr<IStats> &stats_client);
    void logF2fsStats(const std::shared_ptr<IStats> &stats_client);
    void logF2fsCompressionInfo(const std::shared_ptr<IStats> &stats_client);
    void logF2fsGcSegmentInfo(const std::shared_ptr<IStats> &stats_client);
    void logZramStats(const std::shared_ptr<IStats> &stats_client);
    void logBootStats(const std::shared_ptr<IStats> &stats_client);
    void logBatteryEEPROM(const std::shared_ptr<IStats> &stats_client);
    void logSpeakerHealthStats(const std::shared_ptr<IStats> &stats_client);
    void logF2fsSmartIdleMaintEnabled(const std::shared_ptr<IStats> &stats_client);
    void logThermalStats(const std::shared_ptr<IStats> &stats_client);

    void reportSlowIoFromFile(const std::shared_ptr<IStats> &stats_client, const char *path,
                              const VendorSlowIo::IoOperation &operation_s);
    void logTempResidencyStats(const std::shared_ptr<IStats> &stats_client);
    void reportZramMmStat(const std::shared_ptr<IStats> &stats_client);
    void reportZramBdStat(const std::shared_ptr<IStats> &stats_client);
    int getReclaimedSegments(const std::string &mode);
    void logVendorAudioHardwareStats(const std::shared_ptr<IStats> &stats_client);
    void logVendorLongIRQStatsReported(const std::shared_ptr<IStats> &stats_client);
    void logVendorResumeLatencyStats(const std::shared_ptr<IStats> &stats_client);
    void logPcieLinkStats(const std::shared_ptr<IStats> &stats_client);

    const char *const kSlowioReadCntPath;
    const char *const kSlowioWriteCntPath;
    const char *const kSlowioUnmapCntPath;
    const char *const kSlowioSyncCntPath;
    const char *const kCycleCountBinsPath;
    const char *const kImpedancePath;
    const char *const kCodecPath;
    const char *const kCodec1Path;
    const char *const kSpeechDspPath;
    const char *const kBatteryCapacityCC;
    const char *const kBatteryCapacityVFSOC;
    const char *const kUFSLifetimeA;
    const char *const kUFSLifetimeB;
    const char *const kUFSLifetimeC;
    const char *const kF2fsStatsPath;
    const char *const kZramMmStatPath;
    const char *const kZramBdStatPath;
    const char *const kEEPROMPath;
    const char *const kPowerMitigationStatsPath;
    const char *const kSpeakerTemperaturePath;
    const char *const kSpeakerExcursionPath;
    const char *const kSpeakerHeartbeatPath;
    const std::vector<std::string> kUFSErrStatsPath;
    const int kBlockStatsLength;
    const char *const kAmsRatePath;
    const std::vector<std::string> kThermalStatsPaths;
    const char *const kCCARatePath;
    const char *const kTempResidencyPath;
    const char *const kLongIRQMetricsPath;
    const char *const kResumeLatencyMetricsPath;
    const char *const kModemPcieLinkStatsPath;
    const char *const kWifiPcieLinkStatsPath;

    BatteryEEPROMReporter battery_EEPROM_reporter_;
    MmMetricsReporter mm_metrics_reporter_;
    MitigationStatsReporter mitigation_stats_reporter_;
    ThermalStatsReporter thermal_stats_reporter_;
    BatteryHealthReporter battery_health_reporter_;
    TempResidencyReporter temp_residency_reporter_;
    // Proto messages are 1-indexed and VendorAtom field numbers start at 2, so
    // store everything in the values array at the index of the field number
    // -2.
    const int kVendorAtomOffset = 2;

    bool log_once_reported = false;
    int64_t prev_huge_pages_since_boot_ = -1;

    struct perf_metrics_data {
        int64_t softirq_count;
        int64_t irq_count;
        uint64_t resume_latency_sum_ms;
        int64_t resume_count;
        std::vector<int64_t> resume_latency_buckets;
        int bucket_cnt;
    };
    struct perf_metrics_data prev_data;
    const int kMaxResumeLatencyBuckets = 36;
};

}  // namespace pixel
}  // namespace google
}  // namespace hardware
}  // namespace android

#endif  // HARDWARE_GOOGLE_PIXEL_PIXELSTATS_SYSFSCOLLECTOR_H
