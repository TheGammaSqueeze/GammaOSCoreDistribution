/*
 * Copyright (C) 2022 The Android Open Source Project
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

#include <fstream>

// Class to store CompOsArtifactsCheck related metrics.
// These are flushed to a file kOdsignMetricsFile and consumed by
// System Server (in class OdsignStatsLogger) & sent to statsd.
class StatsReporter {
  public:
    // Keep sync with EarlyBootCompOsArtifactsCheckReported
    // definition in proto_logging/stats/atoms.proto.
    struct CompOsArtifactsCheckRecord {
        bool current_artifacts_ok = false;
        bool comp_os_pending_artifacts_exists = false;
        bool use_comp_os_generated_artifacts = false;
    };

    // The report is flushed (from buffer) into a file by the destructor.
    ~StatsReporter();

    // Get pointer to comp_os_artifacts_check_record, caller can then modify it.
    // Note: pointer remains valid for the lifetime of this StatsReporter.
    CompOsArtifactsCheckRecord* GetComposArtifactsCheckRecord();

  private:
    // Temporary buffer which stores the metrics.
    std::unique_ptr<CompOsArtifactsCheckRecord> comp_os_artifacts_check_record_;
};
