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

#include "RecordFilter.h"

#include <gtest/gtest.h>

#include <memory>

#include "event_attr.h"
#include "event_type.h"
#include "record.h"

using namespace simpleperf;

class RecordFilterTest : public ::testing::Test {
 public:
  RecordFilterTest() : filter(thread_tree) {}

 protected:
  void SetUp() override {
    const EventType* event_type = FindEventTypeByName("cpu-clock");
    attr = CreateDefaultPerfEventAttr(*event_type);
    record.reset(new SampleRecord(attr, 0, 0, 0, 0, 0, 0, 0, {}, {}, {}, 0));
  }

  SampleRecord* GetRecord(uint32_t pid, uint32_t tid) {
    record->tid_data.pid = pid;
    record->tid_data.tid = tid;
    return record.get();
  }

  bool SetFilterData(const std::string& data) {
    TemporaryFile tmpfile;
    return android::base::WriteStringToFd(data, tmpfile.fd) && filter.SetFilterFile(tmpfile.path);
  }

  ThreadTree thread_tree;
  perf_event_attr attr;
  RecordFilter filter;
  std::unique_ptr<SampleRecord> record;
};

TEST_F(RecordFilterTest, no_filter) {
  ASSERT_TRUE(filter.Check(GetRecord(0, 0)));
}

TEST_F(RecordFilterTest, exclude_pid) {
  filter.AddPids({1}, true);
  ASSERT_FALSE(filter.Check(GetRecord(1, 1)));
  ASSERT_TRUE(filter.Check(GetRecord(2, 2)));
}

TEST_F(RecordFilterTest, exclude_tid) {
  filter.AddTids({1}, true);
  ASSERT_FALSE(filter.Check(GetRecord(1, 1)));
  ASSERT_TRUE(filter.Check(GetRecord(1, 2)));
}

TEST_F(RecordFilterTest, exclude_process_name_regex) {
  filter.AddProcessNameRegex("processA", true);
  thread_tree.SetThreadName(1, 1, "processA1");
  thread_tree.SetThreadName(2, 2, "processB1");
  ASSERT_FALSE(filter.Check(GetRecord(1, 1)));
  ASSERT_TRUE(filter.Check(GetRecord(2, 2)));
}

TEST_F(RecordFilterTest, exclude_thread_name_regex) {
  filter.AddThreadNameRegex("threadA", true);
  thread_tree.SetThreadName(1, 1, "processA_threadA");
  thread_tree.SetThreadName(1, 2, "processA_threadB");
  ASSERT_FALSE(filter.Check(GetRecord(1, 1)));
  ASSERT_TRUE(filter.Check(GetRecord(1, 2)));
}

TEST_F(RecordFilterTest, exclude_uid) {
  pid_t pid = getpid();
  std::optional<uint32_t> uid = GetProcessUid(pid);
  ASSERT_TRUE(uid.has_value());
  filter.AddUids({uid.value()}, true);
  ASSERT_FALSE(filter.Check(GetRecord(pid, pid)));
  uint32_t pid_not_exist = UINT32_MAX;
  ASSERT_TRUE(filter.Check(GetRecord(pid_not_exist, pid_not_exist)));
}

TEST_F(RecordFilterTest, include_pid) {
  filter.AddPids({1}, false);
  ASSERT_TRUE(filter.Check(GetRecord(1, 1)));
  ASSERT_FALSE(filter.Check(GetRecord(2, 2)));
}

TEST_F(RecordFilterTest, include_tid) {
  filter.AddTids({1}, false);
  ASSERT_TRUE(filter.Check(GetRecord(1, 1)));
  ASSERT_FALSE(filter.Check(GetRecord(1, 2)));
}

TEST_F(RecordFilterTest, include_process_name_regex) {
  filter.AddProcessNameRegex("processA", false);
  thread_tree.SetThreadName(1, 1, "processA1");
  thread_tree.SetThreadName(2, 2, "processB1");
  ASSERT_TRUE(filter.Check(GetRecord(1, 1)));
  ASSERT_FALSE(filter.Check(GetRecord(2, 2)));
}

TEST_F(RecordFilterTest, include_thread_name_regex) {
  filter.AddThreadNameRegex("threadA", false);
  thread_tree.SetThreadName(1, 1, "processA_threadA");
  thread_tree.SetThreadName(1, 2, "processA_threadB");
  ASSERT_TRUE(filter.Check(GetRecord(1, 1)));
  ASSERT_FALSE(filter.Check(GetRecord(1, 2)));
}

TEST_F(RecordFilterTest, include_uid) {
  pid_t pid = getpid();
  std::optional<uint32_t> uid = GetProcessUid(pid);
  ASSERT_TRUE(uid.has_value());
  filter.AddUids({uid.value()}, false);
  ASSERT_TRUE(filter.Check(GetRecord(pid, pid)));
  uint32_t pid_not_exist = UINT32_MAX;
  ASSERT_FALSE(filter.Check(GetRecord(pid_not_exist, pid_not_exist)));
}

TEST_F(RecordFilterTest, global_time_filter) {
  ASSERT_TRUE(
      SetFilterData("GLOBAL_BEGIN 1000\n"
                    "GLOBAL_END 2000\n"
                    "GLOBAL_BEGIN 3000\n"
                    "GLOBAL_END 4000"));
  SampleRecord* r = GetRecord(1, 1);
  r->time_data.time = 0;
  ASSERT_FALSE(filter.Check(r));
  r->time_data.time = 999;
  ASSERT_FALSE(filter.Check(r));
  r->time_data.time = 1000;
  ASSERT_TRUE(filter.Check(r));
  r->time_data.time = 1001;
  ASSERT_TRUE(filter.Check(r));
  r->time_data.time = 1999;
  ASSERT_TRUE(filter.Check(r));
  r->time_data.time = 2000;
  ASSERT_FALSE(filter.Check(r));
  r->time_data.time = 2001;
  ASSERT_FALSE(filter.Check(r));
  r->time_data.time = 3000;
  ASSERT_TRUE(filter.Check(r));
  r->time_data.time = 4000;
  ASSERT_FALSE(filter.Check(r));
}

TEST_F(RecordFilterTest, process_time_filter) {
  ASSERT_TRUE(
      SetFilterData("PROCESS_BEGIN 1 1000\n"
                    "PROCESS_END 1 2000"));
  SampleRecord* r = GetRecord(1, 1);
  r->time_data.time = 0;
  ASSERT_FALSE(filter.Check(r));
  r->time_data.time = 999;
  ASSERT_FALSE(filter.Check(r));
  r->time_data.time = 1000;
  ASSERT_TRUE(filter.Check(r));
  r->time_data.time = 1001;
  ASSERT_TRUE(filter.Check(r));
  r->time_data.time = 1999;
  ASSERT_TRUE(filter.Check(r));
  r->time_data.time = 2000;
  ASSERT_FALSE(filter.Check(r));
  // When process time filters are used, not mentioned processes should be filtered.
  r->tid_data.pid = 2;
  r->time_data.time = 1000;
  ASSERT_FALSE(filter.Check(r));
}

TEST_F(RecordFilterTest, thread_time_filter) {
  ASSERT_TRUE(
      SetFilterData("THREAD_BEGIN 1 1000\n"
                    "THREAD_END 1 2000"));
  SampleRecord* r = GetRecord(1, 1);
  r->time_data.time = 0;
  ASSERT_FALSE(filter.Check(r));
  r->time_data.time = 999;
  ASSERT_FALSE(filter.Check(r));
  r->time_data.time = 1000;
  ASSERT_TRUE(filter.Check(r));
  r->time_data.time = 1001;
  ASSERT_TRUE(filter.Check(r));
  r->time_data.time = 1999;
  ASSERT_TRUE(filter.Check(r));
  r->time_data.time = 2000;
  ASSERT_FALSE(filter.Check(r));
  // When thread time filters are used, not mentioned threads should be filtered.
  r->tid_data.tid = 2;
  r->time_data.time = 1000;
  ASSERT_FALSE(filter.Check(r));
}

TEST_F(RecordFilterTest, clock_in_time_filter) {
  // If there is no filter data, any clock is fine.
  ASSERT_TRUE(filter.CheckClock("monotonic"));
  ASSERT_TRUE(filter.CheckClock("perf"));
  // If there is no clock command, monotonic clock is used.
  ASSERT_TRUE(SetFilterData(""));
  ASSERT_TRUE(filter.CheckClock("monotonic"));
  ASSERT_FALSE(filter.CheckClock("perf"));
  // If there is a clock command, use that clock.
  ASSERT_TRUE(SetFilterData("CLOCK realtime"));
  ASSERT_TRUE(filter.CheckClock("realtime"));
  ASSERT_FALSE(filter.CheckClock("monotonic"));
}

TEST_F(RecordFilterTest, error_in_time_filter) {
  // no timestamp error
  ASSERT_FALSE(SetFilterData("GLOBAL_BEGIN"));
  // time range error
  ASSERT_FALSE(
      SetFilterData("GLOBAL_BEGIN 1000\n"
                    "GLOBAL_END 999"));
  // time range error
  ASSERT_FALSE(
      SetFilterData("GLOBAL_BEGIN 1000\n"
                    "GLOBAL_END 1000"));
  // no timestamp error
  ASSERT_FALSE(SetFilterData("PROCESS_BEGIN 1"));
  // time range error
  ASSERT_FALSE(
      SetFilterData("PROCESS_BEGIN 1 1000\n"
                    "PROCESS_END 1 999"));
  // no timestamp error
  ASSERT_FALSE(SetFilterData("THREAD_BEGIN 1"));
  // time range error
  ASSERT_FALSE(
      SetFilterData("THREAD_BEGIN 1 1000\n"
                    "THREAD_END 1 999"));
}

namespace {

class ParseRecordFilterCommand : public Command {
 public:
  ParseRecordFilterCommand(RecordFilter& filter) : Command("", "", ""), filter_(filter) {}

  bool Run(const std::vector<std::string>& args) override {
    const auto option_formats = GetRecordFilterOptionFormats(true);
    OptionValueMap options;
    std::vector<std::pair<OptionName, OptionValue>> ordered_options;

    if (!PreprocessOptions(args, option_formats, &options, &ordered_options, nullptr)) {
      return false;
    }
    filter_.Clear();
    return filter_.ParseOptions(options);
  }

 private:
  RecordFilter& filter_;
};

}  // namespace

TEST_F(RecordFilterTest, parse_options) {
  ParseRecordFilterCommand filter_cmd(filter);

  for (bool exclude : {true, false}) {
    std::string prefix = exclude ? "--exclude-" : "--include-";

    ASSERT_TRUE(filter_cmd.Run({prefix + "pid", "1,2", prefix + "pid", "3"}));
    ASSERT_EQ(filter.GetCondition(exclude).pids, std::set<pid_t>({1, 2, 3}));
    ASSERT_TRUE(filter_cmd.Run({prefix + "tid", "1,2", prefix + "tid", "3"}));
    ASSERT_EQ(filter.GetCondition(exclude).tids, std::set<pid_t>({1, 2, 3}));

    ASSERT_TRUE(
        filter_cmd.Run({prefix + "process-name", "processA", prefix + "process-name", "processB"}));
    auto& process_regs = filter.GetCondition(exclude).process_name_regs;
    ASSERT_EQ(process_regs.size(), 2);
    ASSERT_TRUE(std::regex_match("processA", process_regs[0]));
    ASSERT_TRUE(std::regex_match("processB", process_regs[1]));

    ASSERT_TRUE(
        filter_cmd.Run({prefix + "thread-name", "threadA", prefix + "thread-name", "threadB"}));
    auto& thread_regs = filter.GetCondition(exclude).thread_name_regs;
    ASSERT_EQ(thread_regs.size(), 2);
    ASSERT_TRUE(std::regex_match("threadA", thread_regs[0]));
    ASSERT_TRUE(std::regex_match("threadB", thread_regs[1]));

    ASSERT_TRUE(filter_cmd.Run({prefix + "uid", "1,2", prefix + "uid", "3"}));
    ASSERT_EQ(filter.GetCondition(exclude).uids, std::set<uint32_t>({1, 2, 3}));
  }
}
