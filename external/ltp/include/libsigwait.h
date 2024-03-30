// SPDX-License-Identifier: GPL-2.0-or-later
/*
 * Copyright (c) 2020 Linaro Limited. All rights reserved.
 * Author: Viresh Kumar <viresh.kumar@linaro.org>
 */

#ifndef SIGWAIT_H__
#define SIGWAIT_H__

#include "tst_test.h"
#include "tst_timer.h"
#include <signal.h>

/* swi: sigwaitinfo() */
typedef int (*swi_func) (const sigset_t * set, siginfo_t * info,
			 void * timeout);
typedef void (*test_func) (swi_func, int, enum tst_ts_type type);

struct sigwait_test_desc {
	test_func tf;
	int signo;
};

void test_empty_set(swi_func sigwaitinfo, int signo,
		    enum tst_ts_type type LTP_ATTRIBUTE_UNUSED);
void test_timeout(swi_func sigwaitinfo, int signo, enum tst_ts_type type);
void test_unmasked_matching(swi_func sigwaitinfo, int signo,
			    enum tst_ts_type type LTP_ATTRIBUTE_UNUSED);
void test_unmasked_matching_noinfo(swi_func sigwaitinfo, int signo,
				   enum tst_ts_type type LTP_ATTRIBUTE_UNUSED);
void test_masked_matching(swi_func sigwaitinfo, int signo,
			  enum tst_ts_type type LTP_ATTRIBUTE_UNUSED);
void test_masked_matching_rt(swi_func sigwaitinfo, int signo,
			     enum tst_ts_type type LTP_ATTRIBUTE_UNUSED);
void test_masked_matching_noinfo(swi_func sigwaitinfo, int signo,
				 enum tst_ts_type type LTP_ATTRIBUTE_UNUSED);
void test_bad_address(swi_func sigwaitinfo, int signo,
		      enum tst_ts_type type LTP_ATTRIBUTE_UNUSED);
void test_bad_address2(swi_func sigwaitinfo, int signo LTP_ATTRIBUTE_UNUSED,
		       enum tst_ts_type type LTP_ATTRIBUTE_UNUSED);
void test_bad_address3(swi_func sigwaitinfo, int signo LTP_ATTRIBUTE_UNUSED,
		       enum tst_ts_type type LTP_ATTRIBUTE_UNUSED);
void sigwait_setup(void);
#endif /* SIGWAIT_H__ */
