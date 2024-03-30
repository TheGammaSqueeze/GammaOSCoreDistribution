// SPDX-License-Identifier: GPL-2.0-or-later
/*
 * Copyright (c) International Business Machines  Corp., 2001
 *
 * Test case to check that kill() fails when passed a pid owned by another user.
 *
 * HISTORY
 *	07/2001 Ported by Wayne Boyer
 *
 *      26/02/2008 Renaud Lottiaux (Renaud.Lottiaux@kerlabs.com)
 *      - Fix wrong return value check on shmat system call (leading to
 *        segfault in case of error with this syscall).
 *      - Fix deletion of IPC memory segment. Segment was not correctly
 *        deleted due to the change of uid during the test.
 *
 * RESTRICTIONS
 *	This test must be run as root.
 */

#include <sys/wait.h>
#include <pwd.h>
#include <stdlib.h>
#include "tst_test.h"
#include "libnewipc.h"
#include "tst_safe_sysv_ipc.h"
#include "tst_safe_macros.h"

static uid_t nobody_uid, bin_uid;
static int *flag;
static int shm_id = -1;
static key_t shm_key;

static void wait_for_flag(int value)
{
	while (1) {
		if (*flag == value)
			break;
		else
			usleep(100);
	}
}

static void do_master_child(void)
{
	pid_t pid1;

	*flag = 0;
	pid1 = SAFE_FORK();
	if (pid1 == 0) {
		SAFE_SETREUID(nobody_uid, nobody_uid);
		*flag = 1;
		wait_for_flag(2);

		exit(0);
	}

	SAFE_SETREUID(bin_uid, bin_uid);
	wait_for_flag(1);
	TEST(kill(pid1, SIGKILL));

	*flag = 2;
	SAFE_WAITPID(pid1, NULL, 0);

	if (TST_RET == 0)
		tst_brk(TFAIL, "kill succeeded unexpectedly");

	if (TST_ERR == EPERM)
		tst_res(TPASS, "kill failed with EPERM");
	else
		tst_res(TFAIL | TTERRNO, "kill failed expected EPERM, but got");
}

static void verify_kill(void)
{
	pid_t pid;

	pid = SAFE_FORK();
	if (pid == 0) {
		do_master_child();
		exit(0);
	}

	tst_reap_children();
}

static void setup(void)
{
	struct passwd *pw;

	shm_key = GETIPCKEY();
	shm_id = SAFE_SHMGET(shm_key, getpagesize(), 0666 | IPC_CREAT);
	flag = SAFE_SHMAT(shm_id, 0, 0);

	pw = SAFE_GETPWNAM("nobody");
	nobody_uid = pw->pw_uid;

	pw = SAFE_GETPWNAM("bin");
	bin_uid = pw->pw_uid;
}

static void cleanup(void)
{
	if (shm_id != -1)
		SAFE_SHMCTL(shm_id, IPC_RMID, NULL);
}

static struct tst_test test = {
	.setup = setup,
	.cleanup = cleanup,
	.test_all = verify_kill,
	.needs_root = 1,
	.forks_child = 1,
};
