// SPDX-License-Identifier: GPL-2.0-or-later
/*
 * Copyright (c) 2020 CTERA Networks. All Rights Reserved.
 *
 * Started by Amir Goldstein <amir73il@gmail.com>
 */

/*\
 * [Description]
 * Check fanotify directory entry modification events, events on child and
 * on self with group init flags:
 *
 * - FAN_REPORT_DFID_NAME (dir fid + name)
 * - FAN_REPORT_DIR_FID   (dir fid)
 * - FAN_REPORT_DIR_FID | FAN_REPORT_FID   (dir fid + child fid)
 * - FAN_REPORT_DFID_NAME | FAN_REPORT_FID (dir fid + name + child fid)
 */

#define _GNU_SOURCE
#include "config.h"

#include <stdio.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <errno.h>
#include <string.h>
#include <sys/mount.h>
#include <sys/syscall.h>
#include "tst_test.h"

#ifdef HAVE_SYS_FANOTIFY_H
#include "fanotify.h"

#define EVENT_MAX 20

/* Size of the event structure, not including file handle */
#define EVENT_SIZE (sizeof(struct fanotify_event_metadata) + \
		    sizeof(struct fanotify_event_info_fid))
/* Tripple events buffer size to account for file handles and names */
#define EVENT_BUF_LEN (EVENT_MAX * EVENT_SIZE * 3)


#define BUF_SIZE 256

#ifdef HAVE_NAME_TO_HANDLE_AT
struct event_t {
	unsigned long long mask;
	struct fanotify_fid_t *fid;
	struct fanotify_fid_t *child_fid;
	char name[BUF_SIZE];
};

static char fname1[BUF_SIZE + 11], fname2[BUF_SIZE + 11];
static char dname1[BUF_SIZE], dname2[BUF_SIZE];
static int fd_notify;

static struct event_t event_set[EVENT_MAX];

static char event_buf[EVENT_BUF_LEN];

#define DIR_NAME1 "test_dir1"
#define DIR_NAME2 "test_dir2"
#define FILE_NAME1 "test_file1"
#define FILE_NAME2 "test_file2"
#define MOUNT_PATH "fs_mnt"

static struct test_case_t {
	const char *tname;
	struct fanotify_group_type group;
	struct fanotify_mark_type mark;
	unsigned long mask;
	struct fanotify_mark_type sub_mark;
	unsigned long sub_mask;
} test_cases[] = {
	{
		"FAN_REPORT_DFID_NAME monitor filesystem for create/delete/move/open/close",
		INIT_FANOTIFY_GROUP_TYPE(REPORT_DFID_NAME),
		INIT_FANOTIFY_MARK_TYPE(FILESYSTEM),
		FAN_CREATE | FAN_DELETE | FAN_MOVE | FAN_DELETE_SELF | FAN_MOVE_SELF | FAN_ONDIR,
		/* Mount watch for events possible on children */
		INIT_FANOTIFY_MARK_TYPE(MOUNT),
		FAN_OPEN | FAN_CLOSE | FAN_ONDIR,
	},
	{
		"FAN_REPORT_DFID_NAME monitor directories for create/delete/move/open/close",
		INIT_FANOTIFY_GROUP_TYPE(REPORT_DFID_NAME),
		INIT_FANOTIFY_MARK_TYPE(INODE),
		FAN_CREATE | FAN_DELETE | FAN_MOVE | FAN_ONDIR,
		/* Watches for self events on subdir and events on subdir's children */
		INIT_FANOTIFY_MARK_TYPE(INODE),
		FAN_CREATE | FAN_DELETE | FAN_MOVE | FAN_DELETE_SELF | FAN_MOVE_SELF | FAN_ONDIR |
		FAN_OPEN | FAN_CLOSE | FAN_EVENT_ON_CHILD,
	},
	{
		"FAN_REPORT_DIR_FID monitor filesystem for create/delete/move/open/close",
		INIT_FANOTIFY_GROUP_TYPE(REPORT_DIR_FID),
		INIT_FANOTIFY_MARK_TYPE(FILESYSTEM),
		FAN_CREATE | FAN_DELETE | FAN_MOVE | FAN_DELETE_SELF | FAN_MOVE_SELF | FAN_ONDIR,
		/* Mount watch for events possible on children */
		INIT_FANOTIFY_MARK_TYPE(MOUNT),
		FAN_OPEN | FAN_CLOSE | FAN_ONDIR,
	},
	{
		"FAN_REPORT_DIR_FID monitor directories for create/delete/move/open/close",
		INIT_FANOTIFY_GROUP_TYPE(REPORT_DIR_FID),
		INIT_FANOTIFY_MARK_TYPE(INODE),
		FAN_CREATE | FAN_DELETE | FAN_MOVE | FAN_ONDIR,
		/* Watches for self events on subdir and events on subdir's children */
		INIT_FANOTIFY_MARK_TYPE(INODE),
		FAN_CREATE | FAN_DELETE | FAN_MOVE | FAN_DELETE_SELF | FAN_MOVE_SELF | FAN_ONDIR |
		FAN_OPEN | FAN_CLOSE | FAN_EVENT_ON_CHILD,
	},
	{
		"FAN_REPORT_DFID_FID monitor filesystem for create/delete/move/open/close",
		INIT_FANOTIFY_GROUP_TYPE(REPORT_DFID_FID),
		INIT_FANOTIFY_MARK_TYPE(FILESYSTEM),
		FAN_CREATE | FAN_DELETE | FAN_MOVE | FAN_DELETE_SELF | FAN_MOVE_SELF | FAN_ONDIR,
		/* Mount watch for events possible on children */
		INIT_FANOTIFY_MARK_TYPE(MOUNT),
		FAN_OPEN | FAN_CLOSE | FAN_ONDIR,
	},
	{
		"FAN_REPORT_DFID_FID monitor directories for create/delete/move/open/close",
		INIT_FANOTIFY_GROUP_TYPE(REPORT_DFID_FID),
		INIT_FANOTIFY_MARK_TYPE(INODE),
		FAN_CREATE | FAN_DELETE | FAN_MOVE | FAN_ONDIR,
		/* Watches for self events on subdir and events on subdir's children */
		INIT_FANOTIFY_MARK_TYPE(INODE),
		FAN_CREATE | FAN_DELETE | FAN_MOVE | FAN_DELETE_SELF | FAN_MOVE_SELF | FAN_ONDIR |
		FAN_OPEN | FAN_CLOSE | FAN_EVENT_ON_CHILD,
	},
	{
		"FAN_REPORT_DFID_NAME_FID monitor filesystem for create/delete/move/open/close",
		INIT_FANOTIFY_GROUP_TYPE(REPORT_DFID_NAME_FID),
		INIT_FANOTIFY_MARK_TYPE(FILESYSTEM),
		FAN_CREATE | FAN_DELETE | FAN_MOVE | FAN_DELETE_SELF | FAN_MOVE_SELF | FAN_ONDIR,
		/* Mount watch for events possible on children */
		INIT_FANOTIFY_MARK_TYPE(MOUNT),
		FAN_OPEN | FAN_CLOSE | FAN_ONDIR,
	},
	{
		"FAN_REPORT_DFID_NAME_FID monitor directories for create/delete/move/open/close",
		INIT_FANOTIFY_GROUP_TYPE(REPORT_DFID_NAME_FID),
		INIT_FANOTIFY_MARK_TYPE(INODE),
		FAN_CREATE | FAN_DELETE | FAN_MOVE | FAN_ONDIR,
		/* Watches for self events on subdir and events on subdir's children */
		INIT_FANOTIFY_MARK_TYPE(INODE),
		FAN_CREATE | FAN_DELETE | FAN_MOVE | FAN_DELETE_SELF | FAN_MOVE_SELF | FAN_ONDIR |
		FAN_OPEN | FAN_CLOSE | FAN_EVENT_ON_CHILD,
	},
};

static void do_test(unsigned int number)
{
	int fd, dirfd, len = 0, i = 0, test_num = 0, tst_count = 0;
	struct test_case_t *tc = &test_cases[number];
	struct fanotify_group_type *group = &tc->group;
	struct fanotify_mark_type *mark = &tc->mark;
	struct fanotify_mark_type *sub_mark = &tc->sub_mark;
	struct fanotify_fid_t root_fid, dir_fid, file_fid;

	tst_res(TINFO, "Test #%d: %s", number, tc->tname);

	fd_notify = SAFE_FANOTIFY_INIT(group->flag, 0);

	/*
	 * Watch dir modify events with name in filesystem/dir
	 */
	SAFE_FANOTIFY_MARK(fd_notify, FAN_MARK_ADD | mark->flag, tc->mask,
			   AT_FDCWD, MOUNT_PATH);

	/* Save the mount root fid */
	fanotify_save_fid(MOUNT_PATH, &root_fid);

	/*
	 * Create subdir and watch open events "on children" with name.
	 * Make it a mount root.
	 */
	SAFE_MKDIR(dname1, 0755);
	SAFE_MOUNT(dname1, dname1, "none", MS_BIND, NULL);

	/* Save the subdir fid */
	fanotify_save_fid(dname1, &dir_fid);

	if (tc->sub_mask)
		SAFE_FANOTIFY_MARK(fd_notify, FAN_MARK_ADD | sub_mark->flag,
				   tc->sub_mask, AT_FDCWD, dname1);

	event_set[tst_count].mask = FAN_CREATE | FAN_ONDIR;
	event_set[tst_count].fid = &root_fid;
	event_set[tst_count].child_fid = NULL;
	strcpy(event_set[tst_count].name, DIR_NAME1);
	tst_count++;

	/* Generate modify events "on child" */
	fd = SAFE_CREAT(fname1, 0755);

	/* Save the file fid */
	fanotify_save_fid(fname1, &file_fid);

	SAFE_WRITE(1, fd, "1", 1);
	SAFE_RENAME(fname1, fname2);

	SAFE_CLOSE(fd);

	/* Generate delete events with fname2 */
	SAFE_UNLINK(fname2);

	/* Read events on files in subdir */
	len += SAFE_READ(0, fd_notify, event_buf + len, EVENT_BUF_LEN - len);

	/*
	 * FAN_CREATE|FAN_DELETE|FAN_MOVE events with the same name are merged.
	 */
	event_set[tst_count].mask = FAN_CREATE | FAN_MOVED_FROM;
	event_set[tst_count].fid = &dir_fid;
	event_set[tst_count].child_fid = NULL;
	strcpy(event_set[tst_count].name, FILE_NAME1);
	tst_count++;
	/*
	 * Event on non-dir child with the same name may be merged with the
	 * directory entry modification events above, unless FAN_REPORT_FID is
	 * set and child fid is reported. If FAN_REPORT_FID is set but
	 * FAN_REPORT_NAME is not set, then FAN_CREATE above is merged with
	 * FAN_DELETE below and FAN_OPEN will be merged with FAN_CLOSE.
	 */
	if (group->flag & FAN_REPORT_NAME) {
		event_set[tst_count].mask = FAN_OPEN;
		event_set[tst_count].fid = &dir_fid;
		event_set[tst_count].child_fid = &file_fid;
		strcpy(event_set[tst_count].name, FILE_NAME1);
		tst_count++;
	}

	event_set[tst_count].mask = FAN_DELETE | FAN_MOVED_TO;
	event_set[tst_count].fid = &dir_fid;
	event_set[tst_count].child_fid = NULL;
	strcpy(event_set[tst_count].name, FILE_NAME2);
	tst_count++;
	/*
	 * When not reporting name, open of FILE_NAME1 is merged
	 * with close of FILE_NAME2.
	 */
	if (!(group->flag & FAN_REPORT_NAME)) {
		event_set[tst_count].mask = FAN_OPEN | FAN_CLOSE_WRITE;
		event_set[tst_count].fid = &dir_fid;
		event_set[tst_count].child_fid = &file_fid;
		strcpy(event_set[tst_count].name, "");
		tst_count++;
	}
	/*
	 * Directory watch does not get self events on children.
	 * Filesystem watch gets self event w/o name info if FAN_REPORT_FID
	 * is set.
	 */
	if (mark->flag == FAN_MARK_FILESYSTEM && (group->flag & FAN_REPORT_FID)) {
		event_set[tst_count].mask = FAN_DELETE_SELF | FAN_MOVE_SELF;
		event_set[tst_count].fid = &file_fid;
		event_set[tst_count].child_fid = NULL;
		strcpy(event_set[tst_count].name, "");
		tst_count++;
	}
	/*
	 * When reporting name, close of FILE_NAME2 is not merged with
	 * open of FILE_NAME1 and it is received after the merged self
	 * events.
	 */
	if (group->flag & FAN_REPORT_NAME) {
		event_set[tst_count].mask = FAN_CLOSE_WRITE;
		event_set[tst_count].fid = &dir_fid;
		event_set[tst_count].child_fid = &file_fid;
		strcpy(event_set[tst_count].name, FILE_NAME2);
		tst_count++;
	}

	dirfd = SAFE_OPEN(dname1, O_RDONLY | O_DIRECTORY);
	SAFE_CLOSE(dirfd);

	SAFE_UMOUNT(dname1);

	/*
	 * Directory watch gets open/close events on itself and on its subdirs.
	 * Filesystem watch gets open/close event on all directories with name ".".
	 */
	event_set[tst_count].mask = FAN_OPEN | FAN_CLOSE_NOWRITE | FAN_ONDIR;
	event_set[tst_count].fid = &dir_fid;
	event_set[tst_count].child_fid = NULL;
	strcpy(event_set[tst_count].name, ".");
	tst_count++;
	/*
	 * Directory watch gets self event on itself and filesystem watch gets
	 * self event on all directories with name ".".
	 */
	event_set[tst_count].mask = FAN_DELETE_SELF | FAN_MOVE_SELF | FAN_ONDIR;
	event_set[tst_count].fid = &dir_fid;
	event_set[tst_count].child_fid = NULL;
	strcpy(event_set[tst_count].name, ".");
	tst_count++;

	SAFE_RENAME(dname1, dname2);
	SAFE_RMDIR(dname2);

	/* Read more events on dirs */
	len += SAFE_READ(0, fd_notify, event_buf + len, EVENT_BUF_LEN - len);

	event_set[tst_count].mask = FAN_MOVED_FROM | FAN_ONDIR;
	event_set[tst_count].fid = &root_fid;
	event_set[tst_count].child_fid = NULL;
	strcpy(event_set[tst_count].name, DIR_NAME1);
	tst_count++;
	event_set[tst_count].mask = FAN_DELETE | FAN_MOVED_TO | FAN_ONDIR;
	event_set[tst_count].fid = &root_fid;
	event_set[tst_count].child_fid = NULL;
	strcpy(event_set[tst_count].name, DIR_NAME2);
	tst_count++;
	/* Expect no more events */
	event_set[tst_count].mask = 0;

	/*
	 * Cleanup the marks
	 */
	SAFE_CLOSE(fd_notify);
	fd_notify = -1;

	while (i < len) {
		struct event_t *expected = &event_set[test_num];
		struct fanotify_event_metadata *event;
		struct fanotify_event_info_fid *event_fid;
		struct fanotify_event_info_fid *child_fid;
		struct fanotify_fid_t *expected_fid = expected->fid;
		struct fanotify_fid_t *expected_child_fid = expected->child_fid;
		struct file_handle *file_handle;
		unsigned int fhlen;
		const char *filename;
		int namelen, info_type, mask_match, info_id = 0;

		event = (struct fanotify_event_metadata *)&event_buf[i];
		event_fid = (struct fanotify_event_info_fid *)(event + 1);
		file_handle = (struct file_handle *)event_fid->handle;
		fhlen = file_handle->handle_bytes;
		filename = (char *)file_handle->f_handle + fhlen;
		child_fid = (void *)((char *)event_fid + event_fid->hdr.len);
		namelen = (char *)child_fid - (char *)filename;
		/* End of event_fid could have name, zero padding, both or none */
		if (namelen > 0) {
			namelen = strlen(filename);
		} else {
			filename = "";
			namelen = 0;
		}
		/* Is there a child fid after first fid record? */
		if (((char *)child_fid - (char *)event) >= event->event_len)
			child_fid = NULL;

		if (!(group->flag & FAN_REPORT_FID))
			expected_child_fid = NULL;

		if (!(group->flag & FAN_REPORT_NAME))
			expected->name[0] = 0;

		if (expected->name[0]) {
			info_type = FAN_EVENT_INFO_TYPE_DFID_NAME;
		} else if (expected->mask & FAN_ONDIR) {
			info_type = FAN_EVENT_INFO_TYPE_DFID;
		} else if (expected->mask & (FAN_DELETE_SELF | FAN_MOVE_SELF)) {
			/* Self event on non-dir has only child fid */
			info_type = FAN_EVENT_INFO_TYPE_FID;
		} else {
			info_type = FAN_EVENT_INFO_TYPE_DFID;
		}

		/*
		 * Event may contain more than the expected mask, but it must
		 * have all the bits in expected mask.
		 * Expected event on dir must not get event on non dir and the
		 * other way around.
		 */
		mask_match = ((event->mask & expected->mask) &&
			      !(expected->mask & ~event->mask) &&
			      !((event->mask ^ expected->mask) & FAN_ONDIR));

check_match:
		if (test_num >= tst_count) {
			tst_res(TFAIL,
				"got unnecessary event: mask=%llx "
				"pid=%u fd=%d name='%s' "
				"len=%d info_type=%d info_len=%d fh_len=%d",
				(unsigned long long)event->mask,
				(unsigned)event->pid, event->fd, filename,
				event->event_len, event_fid->hdr.info_type,
				event_fid->hdr.len, fhlen);
		} else if (!fhlen || namelen < 0) {
			tst_res(TFAIL,
				"got event without fid: mask=%llx pid=%u fd=%d, "
				"len=%d info_type=%d info_len=%d fh_len=%d",
				(unsigned long long)event->mask,
				(unsigned)event->pid, event->fd,
				event->event_len, event_fid->hdr.info_type,
				event_fid->hdr.len, fhlen);
		} else if (!mask_match) {
			tst_res(TFAIL,
				"got event: mask=%llx (expected %llx) "
				"pid=%u fd=%d name='%s' "
				"len=%d info_type=%d info_len=%d fh_len=%d",
				(unsigned long long)event->mask, expected->mask,
				(unsigned)event->pid, event->fd, filename,
				event->event_len, event_fid->hdr.info_type,
				event_fid->hdr.len, fhlen);
		} else if (info_type != event_fid->hdr.info_type) {
			tst_res(TFAIL,
				"got event: mask=%llx pid=%u fd=%d, "
				"len=%d info_type=%d expected(%d) info_len=%d fh_len=%d",
				(unsigned long long)event->mask,
				(unsigned)event->pid, event->fd,
				event->event_len, event_fid->hdr.info_type,
				info_type, event_fid->hdr.len, fhlen);
		} else if (fhlen != expected_fid->handle.handle_bytes) {
			tst_res(TFAIL,
				"got event: mask=%llx pid=%u fd=%d name='%s' "
				"len=%d info_type=%d info_len=%d fh_len=%d expected(%d)"
				"fh_type=%d",
				(unsigned long long)event->mask,
				(unsigned)event->pid, event->fd, filename,
				event->event_len, info_type,
				event_fid->hdr.len, fhlen,
				expected_fid->handle.handle_bytes,
				file_handle->handle_type);
		} else if (file_handle->handle_type !=
			   expected_fid->handle.handle_type) {
			tst_res(TFAIL,
				"got event: mask=%llx pid=%u fd=%d name='%s' "
				"len=%d info_type=%d info_len=%d fh_len=%d "
				"fh_type=%d expected(%x)",
				(unsigned long long)event->mask,
				(unsigned)event->pid, event->fd, filename,
				event->event_len, info_type,
				event_fid->hdr.len, fhlen,
				file_handle->handle_type,
				expected_fid->handle.handle_type);
		} else if (memcmp(file_handle->f_handle,
				  expected_fid->handle.f_handle, fhlen)) {
			tst_res(TFAIL,
				"got event: mask=%llx pid=%u fd=%d name='%s' "
				"len=%d info_type=%d info_len=%d fh_len=%d "
				"fh_type=%d unexpected file handle (%x...)",
				(unsigned long long)event->mask,
				(unsigned)event->pid, event->fd, filename,
				event->event_len, info_type,
				event_fid->hdr.len, fhlen,
				file_handle->handle_type,
				*(int *)(file_handle->f_handle));
		} else if (memcmp(&event_fid->fsid, &expected_fid->fsid,
				  sizeof(event_fid->fsid)) != 0) {
			tst_res(TFAIL,
				"got event: mask=%llx pid=%u fd=%d name='%s' "
				"len=%d info_type=%d info_len=%d fh_len=%d "
				"fsid=%x.%x (expected %x.%x)",
				(unsigned long long)event->mask,
				(unsigned)event->pid, event->fd, filename,
				event->event_len, info_type,
				event_fid->hdr.len, fhlen,
				FSID_VAL_MEMBER(event_fid->fsid, 0),
				FSID_VAL_MEMBER(event_fid->fsid, 1),
				expected_fid->fsid.val[0],
				expected_fid->fsid.val[1]);
		} else if (strcmp(expected->name, filename)) {
			tst_res(TFAIL,
				"got event: mask=%llx "
				"pid=%u fd=%d name='%s' expected('%s') "
				"len=%d info_type=%d info_len=%d fh_len=%d",
				(unsigned long long)event->mask,
				(unsigned)event->pid, event->fd,
				filename, expected->name,
				event->event_len, event_fid->hdr.info_type,
				event_fid->hdr.len, fhlen);
		} else if (event->pid != getpid()) {
			tst_res(TFAIL,
				"got event: mask=%llx pid=%u "
				"(expected %u) fd=%d name='%s' "
				"len=%d info_type=%d info_len=%d fh_len=%d",
				(unsigned long long)event->mask,
				(unsigned)event->pid,
				(unsigned)getpid(),
				event->fd, filename,
				event->event_len, event_fid->hdr.info_type,
				event_fid->hdr.len, fhlen);
		} else if (!!child_fid != !!expected_child_fid) {
			tst_res(TFAIL,
				"got event: mask=%llx "
				"pid=%u fd=%d name='%s' num_info=%d (expected %d) "
				"len=%d info_type=%d info_len=%d fh_len=%d",
				(unsigned long long)event->mask,
				(unsigned)event->pid, event->fd,
				filename, 1 + !!child_fid, 1 + !!expected_child_fid,
				event->event_len, event_fid->hdr.info_type,
				event_fid->hdr.len, fhlen);
		} else if (child_fid) {
			tst_res(TINFO,
				"got event #%d: info #%d: info_type=%d info_len=%d fh_len=%d",
				test_num, info_id, event_fid->hdr.info_type,
				event_fid->hdr.len, fhlen);

			/* Recheck event_fid match with child_fid */
			event_fid = child_fid;
			expected_fid = expected->child_fid;
			info_id = 1;
			info_type = FAN_EVENT_INFO_TYPE_FID;
			file_handle = (struct file_handle *)event_fid->handle;
			fhlen = file_handle->handle_bytes;
			child_fid = NULL;
			expected_child_fid = NULL;
			goto check_match;
		} else {
			tst_res(TPASS,
				"got event #%d: mask=%llx pid=%u fd=%d name='%s' "
				"len=%d; info #%d: info_type=%d info_len=%d fh_len=%d",
				test_num, (unsigned long long)event->mask,
				(unsigned)event->pid, event->fd, filename,
				event->event_len, info_id, event_fid->hdr.info_type,
				event_fid->hdr.len, fhlen);
		}

		if (test_num < tst_count)
			test_num++;

		if (mask_match) {
			/* In case of merged event match next expected mask */
			event->mask &= ~expected->mask | FAN_ONDIR;
			if (event->mask & ~FAN_ONDIR)
				continue;
		}

		i += event->event_len;
		if (event->fd > 0)
			SAFE_CLOSE(event->fd);
	}

	for (; test_num < tst_count; test_num++) {
		tst_res(TFAIL, "didn't get event: mask=%llx, name='%s'",
			 event_set[test_num].mask, event_set[test_num].name);

	}
}

static void setup(void)
{
	REQUIRE_FANOTIFY_INIT_FLAGS_SUPPORTED_ON_FS(FAN_REPORT_DIR_FID, MOUNT_PATH);

	sprintf(dname1, "%s/%s", MOUNT_PATH, DIR_NAME1);
	sprintf(dname2, "%s/%s", MOUNT_PATH, DIR_NAME2);
	sprintf(fname1, "%s/%s", dname1, FILE_NAME1);
	sprintf(fname2, "%s/%s", dname1, FILE_NAME2);
}

static void cleanup(void)
{
	if (fd_notify > 0)
		SAFE_CLOSE(fd_notify);
}

static struct tst_test test = {
	.test = do_test,
	.tcnt = ARRAY_SIZE(test_cases),
	.setup = setup,
	.cleanup = cleanup,
	.mount_device = 1,
	.mntpoint = MOUNT_PATH,
	.all_filesystems = 1,
	.needs_root = 1
};

#else
	TST_TEST_TCONF("system does not have required name_to_handle_at() support");
#endif
#else
	TST_TEST_TCONF("system doesn't have required fanotify support");
#endif
