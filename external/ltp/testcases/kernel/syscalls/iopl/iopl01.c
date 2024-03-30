// SPDX-License-Identifier: GPL-2.0-or-later
/*
 *  Copyright (c) Linux Test Project, 2020
 *  Copyright (c) Wipro Technologies Ltd, 2002
 */

/*
 * This is a basic test for iopl(2) system call.
 *
 * Test the system call for possible privelege levels.
 * As the privelge level for a normal process is 0, start by
 * setting/changing the level to 0.
 *
 * Author: Subhab Biswas <subhabrata.biswas@wipro.com>
 */

#include <errno.h>
#include <unistd.h>

#include "tst_test.h"

#if defined __i386__ || defined(__x86_64__)
#include <sys/io.h>

static void verify_iopl(void)
{
	int total_level = 4;
	int level;

	for (level = 0; level < total_level; ++level) {

		TEST(iopl(level));

		if (TST_RET == -1) {
			tst_res(TFAIL | TTERRNO, "iopl() failed for level %d, "
					"errno=%d : %s", level,
					TST_ERR, tst_strerrno(TST_ERR));
		} else {
			tst_res(TPASS, "iopl() passed for level %d, "
					"returned %ld", level, TST_RET);
		}
	}
}

static void setup(void)
{
	/* iopl() is restricted under kernel lockdown. */
	if (tst_lockdown_enabled())
		tst_brk(TCONF, "Kernel is locked down, skip this test");
}

static void cleanup(void)
{
	/*
	 * back to I/O privilege for normal process.
	 */
	if (iopl(0) == -1)
		tst_res(TWARN, "iopl() cleanup failed");
}

static struct tst_test test = {
	.test_all = verify_iopl,
	.needs_root = 1,
	.setup = setup,
	.cleanup = cleanup,
};

#else
TST_TEST_TCONF("LSB v1.3 does not specify iopl() for this architecture. (only for i386 or x86_64)");
#endif /* __i386_, __x86_64__*/
