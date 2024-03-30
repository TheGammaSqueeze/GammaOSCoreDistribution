// SPDX-License-Identifier: GPL-2.0
/*
 * Copyright (c) 2018 Andrew Lutomirski
 * Copyright (C) 2020 SUSE LLC <mdoucha@suse.cz>
 *
 * CVE-2018-1000199
 *
 * Test error handling when ptrace(POKEUSER) modified x86 debug registers even
 * when the call returned error.
 *
 * When the bug was present we could create breakpoint in the kernel code,
 * which shoudn't be possible at all. The original CVE caused a kernel crash by
 * setting a breakpoint on do_debug kernel function which, when triggered,
 * caused an infinite loop. However we do not have to crash the kernel in order
 * to assert if kernel has been fixed or not.
 *
 * On newer kernels all we have to do is to try to set a breakpoint, on any
 * kernel address, then read it back and check if the value has been set or
 * not.
 *
 * The original fix to the CVE however disabled a breakpoint on address change
 * and the check was deffered to write dr7 that enabled the breakpoint again.
 * So on older kernels we have to write to dr7 which should fail instead.
 *
 * Kernel crash partially fixed in:
 *
 *  commit f67b15037a7a50c57f72e69a6d59941ad90a0f0f
 *  Author: Linus Torvalds <torvalds@linux-foundation.org>
 *  Date:   Mon Mar 26 15:39:07 2018 -1000
 *
 *  perf/hwbp: Simplify the perf-hwbp code, fix documentation
 *
 * On Centos7, this is also a regression test for
 * commit 27747f8bc355 ("perf/x86/hw_breakpoints: Fix check for kernel-space breakpoints").
 */

#include <stdlib.h>
#include <stdio.h>
#include <stddef.h>
#include <sys/ptrace.h>
#include <sys/user.h>
#include <signal.h>
#include "tst_test.h"
#include "tst_safe_stdio.h"

#if defined(__i386__) || defined(__x86_64__)

static pid_t child_pid;

#if defined(__x86_64__)
# define KERN_ADDR_MIN 0xffff800000000000
# define KERN_ADDR_MAX 0xffffffffffffffff
# define KERN_ADDR_BITS 64
#elif defined(__i386__)
# define KERN_ADDR_MIN 0xc0000000
# define KERN_ADDR_MAX 0xffffffff
# define KERN_ADDR_BITS 32
#endif

static int deffered_check;

static struct tst_kern_exv kvers[] = {
	{"RHEL8", "4.18.0-49"},
	{NULL, NULL},
};

static void setup(void)
{
	/*
	 * When running in compat mode we can't pass 64 address to ptrace so we
	 * have to skip the test.
	 */
	if (tst_kernel_bits() != KERN_ADDR_BITS)
		tst_brk(TCONF, "Cannot pass 64bit kernel address in compat mode");


	/*
	 * The original fix for the kernel haven't rejected the kernel address
	 * right away when breakpoint was modified from userspace it was
	 * disabled instead and the EINVAL was returned when dr7 was written to
	 * enable it again. On RHEL8, it has introduced the right fix since
	 * 4.18.0-49.
	 */
	if (tst_kvercmp2(4, 19, 0, kvers) < 0)
		deffered_check = 1;
}

static void child_main(void)
{
	raise(SIGSTOP);
	exit(0);
}

static void ptrace_try_kern_addr(unsigned long kern_addr)
{
	int status;
	unsigned long addr;

	tst_res(TINFO, "Trying address 0x%lx", kern_addr);

	child_pid = SAFE_FORK();

	if (!child_pid)
		child_main();

	if (SAFE_WAITPID(child_pid, &status, WUNTRACED) != child_pid)
		tst_brk(TBROK, "Received event from unexpected PID");

	SAFE_PTRACE(PTRACE_ATTACH, child_pid, NULL, NULL);
	SAFE_PTRACE(PTRACE_POKEUSER, child_pid,
		(void *)offsetof(struct user, u_debugreg[0]), (void *)1);
	SAFE_PTRACE(PTRACE_POKEUSER, child_pid,
		(void *)offsetof(struct user, u_debugreg[7]), (void *)1);

	TEST(ptrace(PTRACE_POKEUSER, child_pid,
		(void *)offsetof(struct user, u_debugreg[0]),
		(void *)kern_addr));

	if (deffered_check) {
		TEST(ptrace(PTRACE_POKEUSER, child_pid,
			(void *)offsetof(struct user, u_debugreg[7]), (void *)1));
	}

	if (TST_RET != -1) {
		tst_res(TFAIL, "ptrace() breakpoint with kernel addr succeeded");
	} else {
		if (TST_ERR == EINVAL) {
			tst_res(TPASS | TTERRNO,
				"ptrace() breakpoint with kernel addr failed");
		} else {
			tst_res(TFAIL | TTERRNO,
				"ptrace() breakpoint on kernel addr should return EINVAL, got");
		}
	}

	addr = ptrace(PTRACE_PEEKUSER, child_pid,
	              (void*)offsetof(struct user, u_debugreg[0]), NULL);

	if (!deffered_check && addr == kern_addr)
		tst_res(TFAIL, "Was able to set breakpoint on kernel addr");

	SAFE_PTRACE(PTRACE_DETACH, child_pid, NULL, NULL);
	SAFE_KILL(child_pid, SIGCONT);
	child_pid = 0;
	tst_reap_children();
}

static void run(void)
{
	ptrace_try_kern_addr(KERN_ADDR_MIN);
	ptrace_try_kern_addr(KERN_ADDR_MAX);
	ptrace_try_kern_addr(KERN_ADDR_MIN + (KERN_ADDR_MAX - KERN_ADDR_MIN)/2);
}

static void cleanup(void)
{
	/* Main process terminated by tst_brk() with child still paused */
	if (child_pid)
		SAFE_KILL(child_pid, SIGKILL);
}

static struct tst_test test = {
	.test_all = run,
	.setup = setup,
	.cleanup = cleanup,
	.forks_child = 1,
	.tags = (const struct tst_tag[]) {
		{"linux-git", "f67b15037a7a"},
		{"CVE", "2018-1000199"},
		{"linux-git", "27747f8bc355"},
		{}
	}
};
#else
TST_TEST_TCONF("This test is only supported on x86 systems");
#endif
