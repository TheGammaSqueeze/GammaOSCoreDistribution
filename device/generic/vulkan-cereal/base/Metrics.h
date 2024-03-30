// Copyright (C) 2021 The Android Open Source Project
// Copyright (C) 2021 Google Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
#pragma once

#include <inttypes.h>
#include <memory>
#include <variant>

// Library to log metrics.
namespace android {
namespace base {

// Events that can be logged.
struct MetricEventFreeze {};
struct MetricEventUnFreeze { int64_t frozen_ms; };
struct GfxstreamVkAbort {
    const char* file;
    const char* function;
    const char* msg;
    int line;
    int64_t abort_reason;
};

using MetricEventType =
    std::variant<std::monostate, MetricEventFreeze, MetricEventUnFreeze, GfxstreamVkAbort>;

class MetricsLogger {
public:
    // Log a MetricEventType.
    virtual void logMetricEvent(MetricEventType eventType) = 0;
    // Virtual destructor.
    virtual ~MetricsLogger() = default;

    // Callbacks to log events
    static void (*add_instant_event_callback)(int64_t event_code);
    static void (*add_instant_event_with_descriptor_callback)(
        int64_t event_code, int64_t descriptor);
    static void (*add_instant_event_with_metric_callback)(
        int64_t event_code, int64_t metric_value);
    // Crashpad will copy the strings, so these need only persist for the function call
    static void(*set_crash_annotation_callback)(const char* key, const char* value);
};

std::unique_ptr<MetricsLogger> CreateMetricsLogger();

}  // namespace base
}  // namespace android