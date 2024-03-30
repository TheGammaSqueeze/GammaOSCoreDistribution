/* SPDX-License-Identifier: MIT */
/*
 * Description: run various openat(2) tests
 *
 */
#include <errno.h>
#include <stdio.h>
#include <unistd.h>
#include <stdlib.h>
#include <string.h>
#include <fcntl.h>

#include "helpers.h"
#include "liburing.h"

static int test_openat2(struct io_uring *ring, const char *path, int dfd)
{
	struct io_uring_cqe *cqe;
	struct io_uring_sqe *sqe;
	struct open_how how;
	int ret;

	sqe = io_uring_get_sqe(ring);
	if (!sqe) {
		fprintf(stderr, "get sqe failed\n");
		goto err;
	}
	memset(&how, 0, sizeof(how));
	how.flags = O_RDONLY;
	io_uring_prep_openat2(sqe, dfd, path, &how);

	ret = io_uring_submit(ring);
	if (ret <= 0) {
		fprintf(stderr, "sqe submit failed: %d\n", ret);
		goto err;
	}

	ret = io_uring_wait_cqe(ring, &cqe);
	if (ret < 0) {
		fprintf(stderr, "wait completion %d\n", ret);
		goto err;
	}
	ret = cqe->res;
	io_uring_cqe_seen(ring, cqe);
	return ret;
err:
	return -1;
}

int main(int argc, char *argv[])
{
	struct io_uring ring;
	const char *path, *path_rel;
	int ret, do_unlink;

	ret = io_uring_queue_init(8, &ring, 0);
	if (ret) {
		fprintf(stderr, "ring setup failed\n");
		return 1;
	}

	if (argc > 1) {
		path = "/tmp/.open.close";
		path_rel = argv[1];
		do_unlink = 0;
	} else {
		path = "/tmp/.open.close";
		path_rel = ".open.close";
		do_unlink = 1;
	}

	t_create_file(path, 4096);

	if (do_unlink)
		t_create_file(path_rel, 4096);

	ret = test_openat2(&ring, path, -1);
	if (ret < 0) {
		if (ret == -EINVAL) {
			fprintf(stdout, "openat2 not supported, skipping\n");
			goto done;
		}
		fprintf(stderr, "test_openat2 absolute failed: %d\n", ret);
		goto err;
	}

	ret = test_openat2(&ring, path_rel, AT_FDCWD);
	if (ret < 0) {
		fprintf(stderr, "test_openat2 relative failed: %d\n", ret);
		goto err;
	}

done:
	unlink(path);
	if (do_unlink)
		unlink(path_rel);
	return 0;
err:
	unlink(path);
	if (do_unlink)
		unlink(path_rel);
	return 1;
}
