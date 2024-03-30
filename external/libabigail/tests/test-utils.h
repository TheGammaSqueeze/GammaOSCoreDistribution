// SPDX-License-Identifier: Apache-2.0 WITH LLVM-exception
// -*- Mode: C++ -*-
//
// Copyright (C) 2013-2020 Red Hat, Inc.

#ifndef __TEST_UTILS_H__
#define __TEST_UTILS_H__

#include "config.h"
#include <string>

#define BRIGHT_YELLOW_COLOR "\e[1;33m"
#define BRIGHT_RED_COLOR "\e[1;31m"
#define DEFAULT_TERMINAL_COLOR "\033[0m"

#define TEST_FAILURE_COLOR BRIGHT_RED_COLOR
#define TEST_SUCCESS_COLOR BRIGHT_YELLOW_COLOR

namespace abigail
{
namespace tests
{

const char* get_src_dir();
const char* get_build_dir();
void
emit_test_status_and_update_counters(bool test_passed,
				     const std::string& test_cmd,
				     unsigned& passed_count,
				     unsigned& failed_count,
				     unsigned& total_count);
void
emit_test_summary(unsigned total_count,
		  unsigned passed_count,
		  unsigned failed_count);
}//end namespace tests
}//end namespace abigail
#endif //__TEST_UTILS_H__
