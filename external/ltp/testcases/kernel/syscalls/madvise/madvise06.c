// SPDX-License-Identifier: GPL-2.0-or-later
/*
 * Copyright (c) 2016 Red Hat, Inc.
 */

/*\
 * [Description]
 *
 * Page fault occurs in spite that madvise(WILLNEED) system call is called
 * to prefetch the page. This issue is reproduced by running a program
 * which sequentially accesses to a shared memory and calls madvise(WILLNEED)
 * to the next page on a page fault.
 *
 * This bug is present in all RHEL7 versions. It looks like this was fixed in
 * mainline kernel > v3.15 by the following patch:
 *
 *  commit 55231e5c898c5c03c14194001e349f40f59bd300
 *  Author: Johannes Weiner <hannes@cmpxchg.org>
 *  Date:   Thu May 22 11:54:17 2014 -0700
 *
 *     mm: madvise: fix MADV_WILLNEED on shmem swapouts
 *
 * Two checks are performed, the first looks at how SwapCache
 * changes during madvise. When the pages are dirtied, about half
 * will be accounted for under Cached and the other half will be
 * moved into Swap. When madvise is run it will cause the pages
 * under Cached to also be moved to Swap while rotating the pages
 * already in Swap into SwapCached. So we expect that SwapCached has
 * roughly MEM_LIMIT bytes added to it, but for reliability the
 * PASS_THRESHOLD is much lower than that.
 *
 * Secondly we run madvise again, but only on the first
 * PASS_THRESHOLD bytes to ensure these are entirely in RAM. Then we
 * dirty these pages and check there were (almost) no page
 * faults. Two faults are allowed incase some tasklet or something
 * else unexpected, but irrelevant procedure, registers a fault to
 * our process.
 */

#include <errno.h>
#include <stdio.h>
#include <sys/mount.h>
#include <sys/sysinfo.h>
#include "tst_test.h"
#include "tst_cgroup.h"

#define CHUNK_SZ (400*1024*1024L)
#define MEM_LIMIT (CHUNK_SZ / 2)
#define MEMSW_LIMIT (2 * CHUNK_SZ)
#define PASS_THRESHOLD (CHUNK_SZ / 4)
#define PASS_THRESHOLD_KB (PASS_THRESHOLD / 1024)

static const struct tst_cgroup_group *cg;

static const char drop_caches_fname[] = "/proc/sys/vm/drop_caches";
static int pg_sz, stat_refresh_sup;

static long init_swap, init_swap_cached, init_cached;

static void check_path(const char *path)
{
	if (access(path, R_OK | W_OK))
		tst_brk(TCONF, "file needed: %s", path);
}

static void print_cgmem(const char *name)
{
	long ret;

	if (!SAFE_CGROUP_HAS(cg, name))
		return;

	SAFE_CGROUP_SCANF(cg, name, "%ld", &ret);
	tst_res(TINFO, "\t%s: %ld Kb", name, ret / 1024);
}

static void meminfo_diag(const char *point)
{
	if (stat_refresh_sup)
		SAFE_FILE_PRINTF("/proc/sys/vm/stat_refresh", "1");

	tst_res(TINFO, "%s", point);
	tst_res(TINFO, "\tSwap: %ld Kb",
		SAFE_READ_MEMINFO("SwapTotal:") - SAFE_READ_MEMINFO("SwapFree:") - init_swap);
	tst_res(TINFO, "\tSwapCached: %ld Kb",
		SAFE_READ_MEMINFO("SwapCached:") - init_swap_cached);
	tst_res(TINFO, "\tCached: %ld Kb",
		SAFE_READ_MEMINFO("Cached:") - init_cached);

	print_cgmem("memory.current");
	print_cgmem("memory.swap.current");
	print_cgmem("memory.kmem.usage_in_bytes");
}

static void setup(void)
{
	struct sysinfo sys_buf_start;

	pg_sz = getpagesize();

	tst_res(TINFO, "dropping caches");
	sync();
	SAFE_FILE_PRINTF(drop_caches_fname, "3");

	sysinfo(&sys_buf_start);
	if (sys_buf_start.freeram < 2 * CHUNK_SZ) {
		tst_brk(TCONF, "System RAM is too small (%li bytes needed)",
			2 * CHUNK_SZ);
	}
	if (sys_buf_start.freeswap < 2 * CHUNK_SZ) {
		tst_brk(TCONF, "System swap is too small (%li bytes needed)",
			2 * CHUNK_SZ);
	}

	check_path("/proc/self/oom_score_adj");
	SAFE_FILE_PRINTF("/proc/self/oom_score_adj", "%d", -1000);

	tst_cgroup_require("memory", NULL);
	cg = tst_cgroup_get_test_group();

	SAFE_CGROUP_PRINTF(cg, "memory.max", "%ld", MEM_LIMIT);
	if (SAFE_CGROUP_HAS(cg, "memory.swap.max"))
		SAFE_CGROUP_PRINTF(cg, "memory.swap.max", "%ld", MEMSW_LIMIT);

	if (SAFE_CGROUP_HAS(cg, "memory.swappiness")) {
		SAFE_CGROUP_PRINT(cg, "memory.swappiness", "60");
	} else {
		check_path("/proc/sys/vm/swappiness");
		SAFE_FILE_PRINTF("/proc/sys/vm/swappiness", "%d", 60);
	}

	SAFE_CGROUP_PRINTF(cg, "cgroup.procs", "%d", getpid());

	meminfo_diag("Initial meminfo, later values are relative to this (except memcg)");
	init_swap = SAFE_READ_MEMINFO("SwapTotal:") - SAFE_READ_MEMINFO("SwapFree:");
	init_swap_cached = SAFE_READ_MEMINFO("SwapCached:");
	init_cached = SAFE_READ_MEMINFO("Cached:");

	if (!access("/proc/sys/vm/stat_refresh", W_OK))
		stat_refresh_sup = 1;

	tst_res(TINFO, "mapping %ld Kb (%ld pages), limit %ld Kb, pass threshold %ld Kb",
		CHUNK_SZ / 1024, CHUNK_SZ / pg_sz, MEM_LIMIT / 1024, PASS_THRESHOLD_KB);
}

static void cleanup(void)
{
	tst_cgroup_cleanup();
}

static void dirty_pages(char *ptr, long size)
{
	long i;
	long pages = size / pg_sz;

	for (i = 0; i < pages; i++)
		ptr[i * pg_sz] = 'x';
}

static int get_page_fault_num(void)
{
	int pg;

	SAFE_FILE_SCANF("/proc/self/stat",
			"%*s %*s %*s %*s %*s %*s %*s %*s %*s %*s %*s %d",
			&pg);
	return pg;
}

static void test_advice_willneed(void)
{
	int loops = 50, res;
	char *target;
	long swapcached_start, swapcached;
	int page_fault_num_1, page_fault_num_2;

	meminfo_diag("Before mmap");
	tst_res(TINFO, "PageFault(before mmap): %d", get_page_fault_num());
	target = SAFE_MMAP(NULL, CHUNK_SZ, PROT_READ | PROT_WRITE,
			MAP_SHARED | MAP_ANONYMOUS,
			-1, 0);
	meminfo_diag("Before dirty");
	tst_res(TINFO, "PageFault(before dirty): %d", get_page_fault_num());
	dirty_pages(target, CHUNK_SZ);
	tst_res(TINFO, "PageFault(after dirty): %d", get_page_fault_num());

	meminfo_diag("Before madvise");
	SAFE_FILE_LINES_SCANF("/proc/meminfo", "SwapCached: %ld",
		&swapcached_start);

	TEST(madvise(target, MEM_LIMIT, MADV_WILLNEED));
	if (TST_RET == -1)
		tst_brk(TBROK | TTERRNO, "madvise failed");

	do {
		loops--;
		usleep(100000);
		if (stat_refresh_sup)
			SAFE_FILE_PRINTF("/proc/sys/vm/stat_refresh", "1");
		SAFE_FILE_LINES_SCANF("/proc/meminfo", "SwapCached: %ld",
			&swapcached);
	} while (swapcached < swapcached_start + PASS_THRESHOLD_KB && loops > 0);

	meminfo_diag("After madvise");
	res = swapcached > swapcached_start + PASS_THRESHOLD_KB;
	tst_res(res ? TPASS : TFAIL,
		"%s than %ld Kb were moved to the swap cache",
		res ? "more" : "less", PASS_THRESHOLD_KB);


	TEST(madvise(target, PASS_THRESHOLD, MADV_WILLNEED));
	if (TST_RET == -1)
		tst_brk(TBROK | TTERRNO, "madvise failed");

	page_fault_num_1 = get_page_fault_num();
	tst_res(TINFO, "PageFault(madvice / no mem access): %d",
			page_fault_num_1);
	dirty_pages(target, PASS_THRESHOLD);
	page_fault_num_2 = get_page_fault_num();
	tst_res(TINFO, "PageFault(madvice / mem access): %d",
			page_fault_num_2);
	meminfo_diag("After page access");

	res = page_fault_num_2 - page_fault_num_1;
	tst_res(res < 3 ? TPASS : TFAIL,
		"%d pages were faulted out of 2 max", res);

	SAFE_MUNMAP(target, CHUNK_SZ);
}

static struct tst_test test = {
	.test_all = test_advice_willneed,
	.setup = setup,
	.cleanup = cleanup,
	.min_kver = "3.10.0",
	.needs_tmpdir = 1,
	.needs_root = 1,
	.save_restore = (const char * const[]) {
		"?/proc/sys/vm/swappiness",
		NULL
	},
	.tags = (const struct tst_tag[]) {
		{"linux-git", "55231e5c898c"},
		{"linux-git", "8de15e920dc8"},
		{}
	}
};
