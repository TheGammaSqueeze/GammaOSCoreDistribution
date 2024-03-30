// Copyright (C) 2021 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

#include <gtest/gtest.h>

#include "src/StatsLogProcessor.h"
#include "tests/statsd_test_util.h"

namespace android {
namespace os {
namespace statsd {

#ifdef __ANDROID__

using namespace std;

class KllMetricE2eTest : public ::testing::Test {
protected:
    void SetUp() override {
        key = ConfigKey(123, 987);
        bucketStartTimeNs = getElapsedRealtimeNs();
        bucketSizeNs = TimeUnitToBucketSizeInMillis(TEN_MINUTES) * 1000000LL;
        whatMatcher = CreateScreenBrightnessChangedAtomMatcher();
        metric = createKllMetric("ScreenBrightness", whatMatcher, /*valueField=*/1,
                                 /*condition=*/nullopt);

        config.add_allowed_log_source("AID_ROOT");

        *config.add_atom_matcher() = whatMatcher;
        *config.add_kll_metric() = metric;

        events.push_back(CreateScreenBrightnessChangedEvent(bucketStartTimeNs + 5 * NS_PER_SEC, 5));
        events.push_back(
                CreateScreenBrightnessChangedEvent(bucketStartTimeNs + 15 * NS_PER_SEC, 15));
        events.push_back(
                CreateScreenBrightnessChangedEvent(bucketStartTimeNs + 25 * NS_PER_SEC, 40));
    }

    ConfigKey key;
    uint64_t bucketStartTimeNs;
    uint64_t bucketSizeNs;
    AtomMatcher whatMatcher;
    KllMetric metric;
    StatsdConfig config;
    vector<unique_ptr<LogEvent>> events;
};

TEST_F(KllMetricE2eTest, TestSimpleMetric) {
    const sp<StatsLogProcessor> processor =
            CreateStatsLogProcessor(bucketStartTimeNs, bucketStartTimeNs, config, key);

    for (auto& event : events) {
        processor->OnLogEvent(event.get());
    }

    uint64_t dumpTimeNs = bucketStartTimeNs + bucketSizeNs;
    ConfigMetricsReportList reports;
    vector<uint8_t> buffer;
    processor->onDumpReport(key, dumpTimeNs, true, true, ADB_DUMP, FAST, &buffer);
    EXPECT_TRUE(reports.ParseFromArray(&buffer[0], buffer.size()));
    backfillDimensionPath(&reports);
    backfillStringInReport(&reports);
    backfillStartEndTimestamp(&reports);
    ASSERT_EQ(reports.reports_size(), 1);

    ConfigMetricsReport report = reports.reports(0);
    ASSERT_EQ(report.metrics_size(), 1);
    StatsLogReport metricReport = report.metrics(0);
    EXPECT_EQ(metricReport.metric_id(), metric.id());
    EXPECT_TRUE(metricReport.has_kll_metrics());
    ASSERT_EQ(metricReport.kll_metrics().data_size(), 1);
    KllMetricData data = metricReport.kll_metrics().data(0);
    ASSERT_EQ(data.bucket_info_size(), 1);
    KllBucketInfo bucket = data.bucket_info(0);
    EXPECT_EQ(bucket.start_bucket_elapsed_nanos(), bucketStartTimeNs);
    EXPECT_EQ(bucket.end_bucket_elapsed_nanos(), bucketStartTimeNs + bucketSizeNs);
    EXPECT_EQ(bucket.sketches_size(), 1);
    EXPECT_EQ(metricReport.kll_metrics().skipped_size(), 0);
}

TEST_F(KllMetricE2eTest, TestInitWithKllFieldPositionALL) {
    // Create config.
    StatsdConfig config;
    config.add_allowed_log_source("AID_ROOT");  // LogEvent defaults to UID of root.

    AtomMatcher testAtomReportedMatcher =
            CreateSimpleAtomMatcher("TestAtomReportedMatcher", util::TEST_ATOM_REPORTED);
    *config.add_atom_matcher() = testAtomReportedMatcher;

    // Create kll metric.
    int64_t metricId = 123456;
    KllMetric* kllMetric = config.add_kll_metric();
    kllMetric->set_id(metricId);
    kllMetric->set_bucket(TimeUnit::FIVE_MINUTES);
    kllMetric->set_what(testAtomReportedMatcher.id());
    *kllMetric->mutable_kll_field() = CreateRepeatedDimensions(
            util::TEST_ATOM_REPORTED, {9 /*repeated_int_field*/}, {Position::ALL});

    // Initialize StatsLogProcessor.
    const uint64_t bucketStartTimeNs = 10000000000;  // 0:10
    int uid = 12345;
    int64_t cfgId = 98765;
    ConfigKey cfgKey(uid, cfgId);
    sp<StatsLogProcessor> processor =
            CreateStatsLogProcessor(bucketStartTimeNs, bucketStartTimeNs, config, cfgKey);

    // Config initialization fails.
    ASSERT_EQ(0, processor->mMetricsManagers.size());
}

#else
GTEST_LOG_(INFO) << "This test does nothing.\n";
#endif

}  // namespace statsd
}  // namespace os
}  // namespace android
