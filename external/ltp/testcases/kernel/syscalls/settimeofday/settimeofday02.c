// SPDX-License-Identifier: GPL-2.0-or-later
/*
 * Copyright (c) International Business Machines  Corp., 2001
 * Ported to LTP 07/2001 John George
 * Testcase to check that settimeofday() sets errnos correctly.
 */

#include <stdio.h>
#include <sys/time.h>
#include <errno.h>
#include "tst_capability.h"
#include "tst_test.h"
#include "lapi/syscalls.h"

static struct tcase {
	struct timeval tv;
	int exp_errno;
	char *message;
} tcases[] = {
	{{-1, 0}, EINVAL, "tv.tv_sec is negative"},
	{{0, -1}, EINVAL, "tv.tv_usec is outside the range [0..999,999]"},
	{{100, 100}, EPERM, "calling process without CAP_SYS_TIME capability"},
};

static void verify_settimeofday(unsigned int n)
{
	struct tcase *tc = &tcases[n];

	tst_res(TINFO, "%s", tc->message);
	TEST(settimeofday(&tc->tv, NULL));
	if (TST_RET != -1) {
		tst_res(TFAIL, "settimeofday() succeeded unexpectedly");
		return;
	}

	if (TST_ERR != tc->exp_errno)
		tst_res(TFAIL | TTERRNO, "Expected %s got ", tst_strerrno(tc->exp_errno));
	else
		tst_res(TPASS | TTERRNO, "Received expected errno");
}

static struct tst_test test = {
	.test = verify_settimeofday,
	.tcnt = ARRAY_SIZE(tcases),
	.caps = (struct tst_cap []) {
		TST_CAP(TST_CAP_DROP, CAP_SYS_TIME),
		{}
	},
};
