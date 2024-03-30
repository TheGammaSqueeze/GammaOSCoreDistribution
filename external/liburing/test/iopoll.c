/* SPDX-License-Identifier: MIT */
/*
 * Description: basic read/write tests with polled IO
 */
#include <errno.h>
#include <stdio.h>
#include <unistd.h>
#include <stdlib.h>
#include <string.h>
#include <fcntl.h>
#include <sys/types.h>
#include <sys/poll.h>
#include <sys/eventfd.h>
#include <sys/resource.h>
#include "helpers.h"
#include "liburing.h"
#include "../src/syscall.h"

#define FILE_SIZE	(128 * 1024)
#define BS		4096
#define BUFFERS		(FILE_SIZE / BS)

static struct iovec *vecs;
static int no_buf_select;
static int no_iopoll;

static int provide_buffers(struct io_uring *ring)
{
	struct io_uring_sqe *sqe;
	struct io_uring_cqe *cqe;
	int ret, i;

	for (i = 0; i < BUFFERS; i++) {
		sqe = io_uring_get_sqe(ring);
		io_uring_prep_provide_buffers(sqe, vecs[i].iov_base,
						vecs[i].iov_len, 1, 1, i);
	}

	ret = io_uring_submit(ring);
	if (ret != BUFFERS) {
		fprintf(stderr, "submit: %d\n", ret);
		return 1;
	}

	for (i = 0; i < BUFFERS; i++) {
		ret = io_uring_wait_cqe(ring, &cqe);
		if (cqe->res < 0) {
			fprintf(stderr, "cqe->res=%d\n", cqe->res);
			return 1;
		}
		io_uring_cqe_seen(ring, cqe);
	}

	return 0;
}

static int __test_io(const char *file, struct io_uring *ring, int write, int sqthread,
		     int fixed, int buf_select)
{
	struct io_uring_sqe *sqe;
	struct io_uring_cqe *cqe;
	int open_flags;
	int i, fd, ret;
	off_t offset;

	if (buf_select && write)
		write = 0;
	if (buf_select && fixed)
		fixed = 0;

	if (buf_select && provide_buffers(ring))
		return 1;

	if (write)
		open_flags = O_WRONLY;
	else
		open_flags = O_RDONLY;
	open_flags |= O_DIRECT;

	fd = open(file, open_flags);
	if (fd < 0) {
		perror("file open");
		goto err;
	}

	if (fixed) {
		ret = io_uring_register_buffers(ring, vecs, BUFFERS);
		if (ret) {
			fprintf(stderr, "buffer reg failed: %d\n", ret);
			goto err;
		}
	}
	if (sqthread) {
		ret = io_uring_register_files(ring, &fd, 1);
		if (ret) {
			fprintf(stderr, "file reg failed: %d\n", ret);
			goto err;
		}
	}

	offset = 0;
	for (i = 0; i < BUFFERS; i++) {
		sqe = io_uring_get_sqe(ring);
		if (!sqe) {
			fprintf(stderr, "sqe get failed\n");
			goto err;
		}
		offset = BS * (rand() % BUFFERS);
		if (write) {
			int do_fixed = fixed;
			int use_fd = fd;

			if (sqthread)
				use_fd = 0;
			if (fixed && (i & 1))
				do_fixed = 0;
			if (do_fixed) {
				io_uring_prep_write_fixed(sqe, use_fd, vecs[i].iov_base,
								vecs[i].iov_len,
								offset, i);
			} else {
				io_uring_prep_writev(sqe, use_fd, &vecs[i], 1,
								offset);
			}
		} else {
			int do_fixed = fixed;
			int use_fd = fd;

			if (sqthread)
				use_fd = 0;
			if (fixed && (i & 1))
				do_fixed = 0;
			if (do_fixed) {
				io_uring_prep_read_fixed(sqe, use_fd, vecs[i].iov_base,
								vecs[i].iov_len,
								offset, i);
			} else {
				io_uring_prep_readv(sqe, use_fd, &vecs[i], 1,
								offset);
			}

		}
		if (sqthread)
			sqe->flags |= IOSQE_FIXED_FILE;
		if (buf_select) {
			sqe->flags |= IOSQE_BUFFER_SELECT;
			sqe->buf_group = buf_select;
			sqe->user_data = i;
		}
	}

	ret = io_uring_submit(ring);
	if (ret != BUFFERS) {
		fprintf(stderr, "submit got %d, wanted %d\n", ret, BUFFERS);
		goto err;
	}

	for (i = 0; i < BUFFERS; i++) {
		ret = io_uring_wait_cqe(ring, &cqe);
		if (ret) {
			fprintf(stderr, "wait_cqe=%d\n", ret);
			goto err;
		} else if (cqe->res == -EOPNOTSUPP) {
			fprintf(stdout, "File/device/fs doesn't support polled IO\n");
			no_iopoll = 1;
			goto out;
		} else if (cqe->res != BS) {
			fprintf(stderr, "cqe res %d, wanted %d\n", cqe->res, BS);
			goto err;
		}
		io_uring_cqe_seen(ring, cqe);
	}

	if (fixed) {
		ret = io_uring_unregister_buffers(ring);
		if (ret) {
			fprintf(stderr, "buffer unreg failed: %d\n", ret);
			goto err;
		}
	}
	if (sqthread) {
		ret = io_uring_unregister_files(ring);
		if (ret) {
			fprintf(stderr, "file unreg failed: %d\n", ret);
			goto err;
		}
	}

out:
	close(fd);
	return 0;
err:
	if (fd != -1)
		close(fd);
	return 1;
}

extern int __io_uring_flush_sq(struct io_uring *ring);

/*
 * if we are polling io_uring_submit needs to always enter the
 * kernel to fetch events
 */
static int test_io_uring_submit_enters(const char *file)
{
	struct io_uring ring;
	int fd, i, ret, ring_flags, open_flags;
	unsigned head;
	struct io_uring_cqe *cqe;

	if (no_iopoll)
		return 0;

	ring_flags = IORING_SETUP_IOPOLL;
	ret = io_uring_queue_init(64, &ring, ring_flags);
	if (ret) {
		fprintf(stderr, "ring create failed: %d\n", ret);
		return 1;
	}

	open_flags = O_WRONLY | O_DIRECT;
	fd = open(file, open_flags);
	if (fd < 0) {
		perror("file open");
		goto err;
	}

	for (i = 0; i < BUFFERS; i++) {
		struct io_uring_sqe *sqe;
		off_t offset = BS * (rand() % BUFFERS);

		sqe = io_uring_get_sqe(&ring);
		io_uring_prep_writev(sqe, fd, &vecs[i], 1, offset);
		sqe->user_data = 1;
	}

	/* submit manually to avoid adding IORING_ENTER_GETEVENTS */
	ret = __sys_io_uring_enter(ring.ring_fd, __io_uring_flush_sq(&ring), 0,
						0, NULL);
	if (ret < 0)
		goto err;

	for (i = 0; i < 500; i++) {
		ret = io_uring_submit(&ring);
		if (ret != 0) {
			fprintf(stderr, "still had %d sqes to submit, this is unexpected", ret);
			goto err;
		}

		io_uring_for_each_cqe(&ring, head, cqe) {
			/* runs after test_io so should not have happened */
			if (cqe->res == -EOPNOTSUPP) {
				fprintf(stdout, "File/device/fs doesn't support polled IO\n");
				goto err;
			}
			goto ok;
		}
		usleep(10000);
	}
err:
	ret = 1;
	if (fd != -1)
		close(fd);

ok:
	io_uring_queue_exit(&ring);
	return ret;
}

static int test_io(const char *file, int write, int sqthread, int fixed,
		   int buf_select)
{
	struct io_uring ring;
	int ret, ring_flags;

	if (no_iopoll)
		return 0;

	ring_flags = IORING_SETUP_IOPOLL;
	if (sqthread) {
		static int warned;

		if (geteuid()) {
			if (!warned)
				fprintf(stdout, "SQPOLL requires root, skipping\n");
			warned = 1;
			return 0;
		}
	}

	ret = io_uring_queue_init(64, &ring, ring_flags);
	if (ret) {
		fprintf(stderr, "ring create failed: %d\n", ret);
		return 1;
	}

	ret = __test_io(file, &ring, write, sqthread, fixed, buf_select);

	io_uring_queue_exit(&ring);
	return ret;
}

static int probe_buf_select(void)
{
	struct io_uring_probe *p;
	struct io_uring ring;
	int ret;

	ret = io_uring_queue_init(1, &ring, 0);
	if (ret) {
		fprintf(stderr, "ring create failed: %d\n", ret);
		return 1;
	}

	p = io_uring_get_probe_ring(&ring);
	if (!p || !io_uring_opcode_supported(p, IORING_OP_PROVIDE_BUFFERS)) {
		no_buf_select = 1;
		fprintf(stdout, "Buffer select not supported, skipping\n");
		return 0;
	}
	free(p);
	return 0;
}

int main(int argc, char *argv[])
{
	int i, ret, nr;
	char *fname;

	if (probe_buf_select())
		return 1;

	if (argc > 1) {
		fname = argv[1];
	} else {
		fname = ".iopoll-rw";
		t_create_file(fname, FILE_SIZE);
	}

	vecs = t_create_buffers(BUFFERS, BS);

	nr = 16;
	if (no_buf_select)
		nr = 8;
	for (i = 0; i < nr; i++) {
		int v1, v2, v3, v4;

		v1 = (i & 1) != 0;
		v2 = (i & 2) != 0;
		v3 = (i & 4) != 0;
		v4 = (i & 8) != 0;
		ret = test_io(fname, v1, v2, v3, v4);
		if (ret) {
			fprintf(stderr, "test_io failed %d/%d/%d/%d\n", v1, v2, v3, v4);
			goto err;
		}
		if (no_iopoll)
			break;
	}

	ret = test_io_uring_submit_enters(fname);
	if (ret) {
	    fprintf(stderr, "test_io_uring_submit_enters failed\n");
	    goto err;
	}

	if (fname != argv[1])
		unlink(fname);
	return 0;
err:
	if (fname != argv[1])
		unlink(fname);
	return 1;
}
