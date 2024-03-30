/*
 * Copyright (C) 2019 The Android Open Source Project
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

#include <android-base/file.h>
#include <android-base/test_utils.h>
#include <gtest/gtest.h>

#include "cmd_inject_impl.h"
#include "command.h"
#include "get_test_data.h"
#include "test_util.h"
#include "utils.h"

using namespace simpleperf;

static std::unique_ptr<Command> InjectCmd() {
  return CreateCommandInstance("inject");
}

static bool RunInjectCmd(std::vector<std::string>&& args) {
  bool has_input = std::find(args.begin(), args.end(), "-i") != args.end();
  if (!has_input) {
    args.insert(args.end(), {"-i", GetTestData(PERF_DATA_ETM_TEST_LOOP)});
  }
  args.insert(args.end(), {"--symdir", GetTestDataDir() + "etm"});
  return InjectCmd()->Run(args);
}

static bool RunInjectCmd(std::vector<std::string>&& args, std::string* output) {
  TemporaryFile tmpfile;
  close(tmpfile.release());
  args.insert(args.end(), {"-o", tmpfile.path});
  if (!RunInjectCmd(std::move(args))) {
    return false;
  }
  if (output != nullptr) {
    return android::base::ReadFileToString(tmpfile.path, output);
  }
  return true;
}

static void CheckMatchingExpectedData(std::string& data) {
  std::string expected_data;
  ASSERT_TRUE(android::base::ReadFileToString(
      GetTestData(std::string("etm") + OS_PATH_SEPARATOR + "perf_inject.data"), &expected_data));
  data.erase(std::remove(data.begin(), data.end(), '\r'), data.end());
  ASSERT_EQ(data, expected_data);
}

TEST(cmd_inject, smoke) {
  std::string data;
  ASSERT_TRUE(RunInjectCmd({}, &data));
  // Test that we can find instr range in etm_test_loop binary.
  ASSERT_NE(data.find("etm_test_loop"), std::string::npos);
  CheckMatchingExpectedData(data);
}

TEST(cmd_inject, binary_option) {
  // Test that data for etm_test_loop is generated when selected by --binary.
  std::string data;
  ASSERT_TRUE(RunInjectCmd({"--binary", "etm_test_loop"}, &data));
  ASSERT_NE(data.find("etm_test_loop"), std::string::npos);

  // Test that data for etm_test_loop is generated when selected by regex.
  ASSERT_TRUE(RunInjectCmd({"--binary", "etm_t.*_loop"}, &data));
  ASSERT_NE(data.find("etm_test_loop"), std::string::npos);

  // Test that data for etm_test_loop isn't generated when not selected by --binary.
  ASSERT_TRUE(RunInjectCmd({"--binary", "no_etm_test_loop"}, &data));
  ASSERT_EQ(data.find("etm_test_loop"), std::string::npos);

  // Test that data for etm_test_loop isn't generated when not selected by regex.
  ASSERT_TRUE(RunInjectCmd({"--binary", "no_etm_test_.*"}, &data));
  ASSERT_EQ(data.find("etm_test_loop"), std::string::npos);
}

TEST(cmd_inject, exclude_perf_option) {
  ASSERT_TRUE(RunInjectCmd({"--exclude-perf"}, nullptr));
}

TEST(cmd_inject, output_option) {
  TemporaryFile tmpfile;
  close(tmpfile.release());
  ASSERT_TRUE(RunInjectCmd({"--output", "autofdo", "-o", tmpfile.path}));
  ASSERT_TRUE(RunInjectCmd({"--output", "branch-list", "-o", tmpfile.path}));
  std::string autofdo_data;
  ASSERT_TRUE(RunInjectCmd({"-i", tmpfile.path, "--output", "autofdo"}, &autofdo_data));
  CheckMatchingExpectedData(autofdo_data);
}

TEST(cmd_inject, branch_to_proto_string) {
  std::vector<bool> branch;
  for (size_t i = 0; i < 100; i++) {
    branch.push_back(i % 2 == 0);
    std::string s = BranchToProtoString(branch);
    for (size_t j = 0; j <= i; j++) {
      bool b = s[j >> 3] & (1 << (j & 7));
      ASSERT_EQ(b, branch[j]);
    }
    std::vector<bool> branch2 = ProtoStringToBranch(s, branch.size());
    ASSERT_EQ(branch, branch2);
  }
}

TEST(cmd_inject, skip_empty_output_file) {
  TemporaryFile tmpfile;
  close(tmpfile.release());
  ASSERT_TRUE(RunInjectCmd(
      {"--binary", "not_exist_binary", "--output", "branch-list", "-o", tmpfile.path}));
  // The empty output file should not be produced.
  ASSERT_FALSE(IsRegularFile(tmpfile.path));
  tmpfile.DoNotRemove();
}

TEST(cmd_inject, inject_kernel_data) {
  const std::string recording_file =
      GetTestData(std::string("etm") + OS_PATH_SEPARATOR + "perf_kernel.data");

  // Inject directly to autofdo format.
  TemporaryFile tmpfile;
  close(tmpfile.release());
  ASSERT_TRUE(RunInjectCmd({"-i", recording_file, "-o", tmpfile.path}));
  std::string autofdo_output;
  ASSERT_TRUE(android::base::ReadFileToString(tmpfile.path, &autofdo_output));
  ASSERT_NE(autofdo_output.find("rq_stats.ko"), std::string::npos);

  // Inject through etm branch list.
  TemporaryFile tmpfile2;
  close(tmpfile2.release());
  ASSERT_TRUE(RunInjectCmd({"-i", recording_file, "-o", tmpfile.path, "--output", "branch-list"}));
  ASSERT_TRUE(RunInjectCmd({"-i", tmpfile.path, "-o", tmpfile2.path}));
  std::string output;
  ASSERT_TRUE(android::base::ReadFileToString(tmpfile2.path, &output));
  ASSERT_EQ(output, autofdo_output);
}

TEST(cmd_inject, unformatted_trace) {
  std::string data;
  std::string perf_with_unformatted_trace =
      GetTestData(std::string("etm") + OS_PATH_SEPARATOR + "perf_with_unformatted_trace.data");
  ASSERT_TRUE(RunInjectCmd({"-i", perf_with_unformatted_trace}, &data));
  // Test that we can find instr range in etm_test_loop binary.
  ASSERT_NE(data.find("etm_test_loop"), std::string::npos);
  CheckMatchingExpectedData(data);
}

TEST(cmd_inject, multiple_input_files) {
  std::string data;
  std::string perf_data = GetTestData(std::string("etm") + OS_PATH_SEPARATOR + "perf.data");
  std::string perf_with_unformatted_trace =
      GetTestData(std::string("etm") + OS_PATH_SEPARATOR + "perf_with_unformatted_trace.data");

  // Test input files separated by comma.
  ASSERT_TRUE(RunInjectCmd({"-i", perf_with_unformatted_trace + "," + perf_data}, &data));
  ASSERT_NE(data.find("106c->1074:200"), std::string::npos);

  // Test input files from different -i options.
  ASSERT_TRUE(RunInjectCmd({"-i", perf_with_unformatted_trace, "-i", perf_data}, &data));
  ASSERT_NE(data.find("106c->1074:200"), std::string::npos);

  // Test input files provided by input_file_list.
  TemporaryFile tmpfile;
  std::string input_file_list = perf_data + "\n" + perf_with_unformatted_trace + "\n";
  ASSERT_TRUE(android::base::WriteStringToFd(input_file_list, tmpfile.fd));
  close(tmpfile.release());
  ASSERT_TRUE(RunInjectCmd({"-i", std::string("@") + tmpfile.path}, &data));
  ASSERT_NE(data.find("106c->1074:200"), std::string::npos);
}

TEST(cmd_inject, merge_branch_list_files) {
  TemporaryFile tmpfile;
  close(tmpfile.release());
  ASSERT_TRUE(RunInjectCmd({"--output", "branch-list", "-o", tmpfile.path}));
  TemporaryFile tmpfile2;
  close(tmpfile2.release());
  ASSERT_TRUE(RunInjectCmd({"-i", std::string(tmpfile.path) + "," + tmpfile.path, "--output",
                            "branch-list", "-o", tmpfile2.path}));
  std::string autofdo_data;
  ASSERT_TRUE(RunInjectCmd({"-i", tmpfile2.path, "--output", "autofdo"}, &autofdo_data));
  ASSERT_NE(autofdo_data.find("106c->1074:200"), std::string::npos);
}

TEST(cmd_inject, report_warning_when_overflow) {
  CapturedStderr capture;
  capture.Start();
  std::vector<std::unique_ptr<TemporaryFile>> branch_list_files;
  std::vector<std::unique_ptr<TemporaryFile>> input_files;

  branch_list_files.emplace_back(new TemporaryFile);
  close(branch_list_files.back()->release());
  ASSERT_TRUE(RunInjectCmd({"--output", "branch-list", "-o", branch_list_files.back()->path}));
  for (size_t i = 1; i <= 7; i++) {
    // Create input file list, repeating branch list file for 1000 times.
    std::string s;
    for (size_t j = 0; j < 1000; j++) {
      s += std::string(branch_list_files.back()->path) + "\n";
    }
    input_files.emplace_back(new TemporaryFile);
    ASSERT_TRUE(android::base::WriteStringToFd(s, input_files.back()->fd));
    close(input_files.back()->release());

    // Merge branch list files.
    branch_list_files.emplace_back(new TemporaryFile);
    close(branch_list_files.back()->release());
    ASSERT_TRUE(
        RunInjectCmd({"--output", "branch-list", "-i", std::string("@") + input_files.back()->path,
                      "-o", branch_list_files.back()->path}));
  }
  capture.Stop();
  const std::string WARNING_MSG = "Branch count overflow happened.";
  ASSERT_NE(capture.str().find(WARNING_MSG), std::string::npos);

  // Warning also happens when converting branch lists to AutoFDO format.
  capture.Reset();
  capture.Start();
  std::string autofdo_data;
  ASSERT_TRUE(RunInjectCmd({"-i", branch_list_files.back()->path}, &autofdo_data));
  capture.Stop();
  ASSERT_NE(capture.str().find(WARNING_MSG), std::string::npos);
  ASSERT_NE(autofdo_data.find("106c->1074:18446744073709551615"), std::string::npos);
}
