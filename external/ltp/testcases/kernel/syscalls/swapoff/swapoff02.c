// SPDX-License-Identifier: GPL-2.0-or-later
/*
 * Copyright (c) Wipro Technologies Ltd, 2002.  All Rights Reserved.
 */

/*\
 * [Description]
 *
 * This test case checks whether swapoff(2) system call  returns
 *  1. EINVAL when the path does not exist
 *  2. ENOENT when the path exists but is invalid
 *  3. EPERM when user is not a superuser
 */

#include <errno.h>
#include <pwd.h>
#include "tst_test.h"
#include "lapi/syscalls.h"
#include "libswap.h"

static int setup01(void);
static void cleanup01(void);

static uid_t nobody_uid;

static struct tcase {
	char *err_desc;
	int exp_errno;
	char *exp_errval;
	char *path;
	int (*setup)(void);
	void (*cleanup)(void);
} tcases[] = {
	{"path does not exist", ENOENT, "ENOENT", "./doesnotexist", NULL, NULL},
	{"Invalid file", EINVAL, "EINVAL", "./swapfile01", NULL, NULL},
	{"Permission denied", EPERM, "EPERM", "./swapfile01", setup01, cleanup01}
};

static void verify_swapoff(unsigned int i)
{
	struct tcase *tc = tcases + i;
	if (tc->setup)
		tc->setup();

	TEST(tst_syscall(__NR_swapoff, tc->path));

	if (tc->cleanup)
		tc->cleanup();

	if (TST_RET == -1 && (TST_ERR == tc->exp_errno)) {
		tst_res(TPASS, "swapoff(2) expected failure;"
			" Got errno - %s : %s",
			tc->exp_errval, tc->err_desc);
	} else {
		tst_res(TFAIL, "swapoff(2) failed to produce"
			" expected error; %d, errno"
			": %s and got %d",
			tc->exp_errno, tc->exp_errval, TST_ERR);

		if ((TST_RET == 0) && (i == 2)) {
			if (tst_syscall(__NR_swapon, "./swapfile01", 0) != 0)
				tst_brk(TBROK | TERRNO, " Failed to turn on swap file");
		}
	}
}

static int setup01(void)
{
	SAFE_SETEUID(nobody_uid);
	return 0;
}

static void cleanup01(void)
{
	SAFE_SETEUID(0);
}

static void setup(void)
{
	struct passwd *nobody;

	nobody = SAFE_GETPWNAM("nobody");
	nobody_uid = nobody->pw_uid;

	is_swap_supported("./tstswap");

	if (!tst_fs_has_free(".", 1, TST_KB))
		tst_brk(TBROK, "Insufficient disk space to create swap file");

	if (tst_fill_file("./swapfile01", 0x00, 1024, 1))
		tst_brk(TBROK, "Failed to create swapfile");
}

static struct tst_test test = {
	.needs_root = 1,
	.needs_tmpdir = 1,
	.test = verify_swapoff,
	.tcnt = ARRAY_SIZE(tcases),
	.setup = setup
};
