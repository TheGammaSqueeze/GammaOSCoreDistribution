// SPDX-License-Identifier: Apache-2.0 WITH LLVM-exception
// -*- Mode: C++ -*-
//
// Copyright (C) 2013-2020 Red Hat, Inc.

#include <iostream>
#include "test-utils.h"

using std::string;

namespace abigail
{
namespace tests
{

/// Returns the absolute path to the source directory.
///
/// \return the absolute path tho the source directory.
const char*
get_src_dir()
{
#ifndef ABIGAIL_SRC_DIR
#error the macro ABIGAIL_SRC_DIR must be set at compile time
#endif

  static __thread const char* s(ABIGAIL_SRC_DIR);
  return s;
}

/// Returns the absolute path to the build directory.
///
/// \return the absolute path the build directory.
const char*
get_build_dir()
{
#ifndef ABIGAIL_BUILD_DIR
#error the macro ABIGAIL_BUILD_DIR must be set at compile time
#endif

  static __thread const char* s(ABIGAIL_BUILD_DIR);
  return s;
}

/// Emit test status on the standard output.
///
/// This function also increments passed, failed and total test
/// numbers accordingly.
///
/// @param test_passed indicated if the test succeeded or not.
///
/// @param test_cmd the test command that was executed.  If the test
/// failed, the exact command is displayed.
///
/// @param passed_count the number of passed tests.  This is going to
/// be incremented if the test passes.
///
/// @param failed_count the number of failed tests.  This is going to
/// be incremented if the test fails.
///
/// @param total_count the total number of tests.  This is going to be
/// incremented.
void
emit_test_status_and_update_counters(bool test_passed,
				     const std::string& test_cmd,
				     unsigned& passed_count,
				     unsigned& failed_count,
				     unsigned& total_count)
{
  if (test_passed)
    passed_count++;
  else
    {
      std::cout << TEST_FAILURE_COLOR
		<< "Test Failed: "
		<< DEFAULT_TERMINAL_COLOR
		<< test_cmd
		<< std::endl;
      failed_count++;
    }
  total_count++;
}

/// Emit the summary of the test.
///
/// @param total_count the total number of tests executed.
///
/// @param passed_count the number of tests that succeeded.
///
/// @param failed_count the number of tests that failed.
void
emit_test_summary(unsigned total_count,
		  unsigned passed_count,
		  unsigned failed_count)
{
  if (failed_count)
    std::cout << TEST_FAILURE_COLOR << "FAILURE!";
  else
    std::cout << TEST_SUCCESS_COLOR << "SUCCESS!";
  std::cout << DEFAULT_TERMINAL_COLOR << "\n";

  std::cout << "Total number of tests executed: " << total_count
	    << " Number of tests PASSED: " << passed_count
	    << ", Number of tests FAILED: " << failed_count
	    << ".\n";
}
}//end namespace tests
}//end namespace abigail
