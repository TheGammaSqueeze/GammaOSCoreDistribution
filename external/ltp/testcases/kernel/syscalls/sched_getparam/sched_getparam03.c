// SPDX-License-Identifier: GPL-2.0-or-later
/*
 * Copyright (c) Wipro Technologies Ltd, 2002.  All Rights Reserved.
 */

/*\
 * [Description]
 *
 * Verify that:
 *
 * - sched_getparam(2) returns -1 and sets errno to ESRCH if the
 * process with specified pid could not be found
 * - sched_getparam(2) returns -1 and sets errno to EINVAL if
 * the parameter pid is an invalid value (-1)
 * - sched_getparam(2) returns -1 and sets errno to EINVAL if the
 * parameter p is an invalid address
 */

#include <errno.h>
#include <sched.h>
#include "tst_test.h"

static struct sched_param param;
static pid_t unused_pid;
static pid_t zero_pid;
static pid_t inval_pid = -1;

static struct test_case_t {
	char *desc;
	pid_t *pid;
	struct sched_param *p;
	int exp_errno;
} test_cases[] = {
	{"sched_getparam() with non-existing pid",
	 &unused_pid, &param, ESRCH},
	{"sched_getparam() with invalid pid",
	 &inval_pid, &param, EINVAL},
	{"sched_getparam() with invalid address for param",
	 &zero_pid, NULL, EINVAL},
};

static void verify_sched_getparam(unsigned int n)
{
	struct test_case_t *tc = &test_cases[n];

	TST_EXP_FAIL(sched_getparam(*(tc->pid), tc->p), tc->exp_errno, "%s", tc->desc);
}

static void setup(void)
{
	unused_pid = tst_get_unused_pid();
}

static struct tst_test test = {
	.setup = setup,
	.tcnt = ARRAY_SIZE(test_cases),
	.test = verify_sched_getparam,
};
