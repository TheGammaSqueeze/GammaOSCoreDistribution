// SPDX-License-Identifier: GPL-2.0-or-later
/*
 * Copyright (c) 2017 Cyril Hrubis <chrubis@suse.cz>
 */

#include <stdio.h>
#include <errno.h>
#include <stdlib.h>
#include <sys/mount.h>
#include <sys/wait.h>
#include <sys/quota.h>

#define TST_NO_DEFAULT_MAIN
#include "tst_test.h"
#include "tst_fs.h"

static const char *const fs_type_whitelist[] = {
	"ext2",
	"ext3",
	"ext4",
	"xfs",
	"btrfs",
	"vfat",
	"exfat",
	"ntfs",
	"tmpfs",
	NULL
};

static const char *fs_types[ARRAY_SIZE(fs_type_whitelist)];

static int has_mkfs(const char *fs_type)
{
	char buf[128];
	int ret;

	if (strstr(fs_type, "tmpfs")) {
		tst_res(TINFO, "mkfs is not needed for tmpfs");
		return 1;
	}

	sprintf(buf, "mkfs.%s >/dev/null 2>&1", fs_type);

	ret = tst_system(buf);

	if (WEXITSTATUS(ret) == 127) {
		tst_res(TINFO, "mkfs.%s does not exist", fs_type);
		return 0;
	}

	tst_res(TINFO, "mkfs.%s does exist", fs_type);
	return 1;
}

int tst_fs_in_skiplist(const char *fs_type, const char *const *skiplist)
{
	unsigned int i;

	if (!skiplist)
		return 0;

	for (i = 0; skiplist[i]; i++) {
		if (!strcmp(fs_type, skiplist[i]))
			return 1;
	}

	return 0;
}

static enum tst_fs_impl has_kernel_support(const char *fs_type)
{
	static int fuse_supported = -1;
	const char *tmpdir = getenv("TMPDIR");
	char buf[128];
	char template[PATH_MAX];
	int ret;

	if (!tmpdir)
		tmpdir = "/tmp";

	snprintf(template, sizeof(template), "%s/mountXXXXXX", tmpdir);
	if (!mkdtemp(template))
		tst_brk(TBROK | TERRNO , "mkdtemp(%s) failed", template);

	ret = mount("/dev/zero", template, fs_type, 0, NULL);
	if ((ret && errno != ENODEV) || !ret) {
		if (!ret)
			tst_umount(template);
		tst_res(TINFO, "Kernel supports %s", fs_type);
		SAFE_RMDIR(template);
		return TST_FS_KERNEL;
	}

	SAFE_RMDIR(template);

	/* Is FUSE supported by kernel? */
	if (fuse_supported == -1) {
		ret = open("/dev/fuse", O_RDWR);
		if (ret < 0) {
			fuse_supported = 0;
		} else {
			fuse_supported = 1;
			SAFE_CLOSE(ret);
		}
	}

	if (!fuse_supported)
		return TST_FS_UNSUPPORTED;

	/* Is FUSE implementation installed? */
	sprintf(buf, "mount.%s >/dev/null 2>&1", fs_type);

	ret = tst_system(buf);
	if (WEXITSTATUS(ret) == 127) {
		tst_res(TINFO, "Filesystem %s is not supported", fs_type);
		return TST_FS_UNSUPPORTED;
	}

	tst_res(TINFO, "FUSE does support %s", fs_type);
	return TST_FS_FUSE;
}

enum tst_fs_impl tst_fs_is_supported(const char *fs_type)
{
	enum tst_fs_impl ret;

	ret = has_kernel_support(fs_type);
	if (!ret)
		return TST_FS_UNSUPPORTED;

	if (has_mkfs(fs_type))
		return ret;

	return TST_FS_UNSUPPORTED;
}

const char **tst_get_supported_fs_types(const char *const *skiplist)
{
	unsigned int i, j = 0;
	int skip_fuse;
	enum tst_fs_impl sup;

	skip_fuse = tst_fs_in_skiplist("fuse", skiplist);

	for (i = 0; fs_type_whitelist[i]; i++) {
		if (tst_fs_in_skiplist(fs_type_whitelist[i], skiplist)) {
			tst_res(TINFO, "Skipping %s as requested by the test",
				fs_type_whitelist[i]);
			continue;
		}

		sup = tst_fs_is_supported(fs_type_whitelist[i]);

		if (skip_fuse && sup == TST_FS_FUSE) {
			tst_res(TINFO,
				"Skipping FUSE based %s as requested by the test",
				fs_type_whitelist[i]);
			continue;
		}

		if (sup)
			fs_types[j++] = fs_type_whitelist[i];
	}

	return fs_types;
}

int tst_check_quota_support(const char *device, int format, char *quotafile)
{
	TEST(quotactl(QCMD(Q_QUOTAON, USRQUOTA), device, format, quotafile));

	/* Not supported */
	if (TST_RET == -1 && TST_ERR == ESRCH)
		return 0;

	/* Broken */
	if (TST_RET)
		return -1;

	quotactl(QCMD(Q_QUOTAOFF, USRQUOTA), device, 0, 0);
	return 1;
}

void tst_require_quota_support_(const char *file, const int lineno,
	const char *device, int format, char *quotafile)
{
	int status = tst_check_quota_support(device, format, quotafile);

	if (!status) {
		tst_brk_(file, lineno, TCONF,
			"Kernel or device does not support FS quotas");
	}

	if (status < 0)
		tst_brk_(file, lineno, TBROK|TTERRNO, "FS quotas are broken");
}
