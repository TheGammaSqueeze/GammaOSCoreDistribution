/*
 * fuse_media eBPF program
 *
 * Copyright (C) 2021 Google
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License version
 * 2 as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 */

#include <bpf_helpers.h>

#include <stdint.h>

#define __KERNEL__
#include <fuse_kernel.h>

#define bpf_printk(fmt, ...)                                       \
    ({                                                             \
        char ____fmt[] = fmt;                                      \
        bpf_trace_printk(____fmt, sizeof(____fmt), ##__VA_ARGS__); \
    })

DEFINE_BPF_PROG("fuse/media", AID_ROOT, AID_MEDIA_RW, fuse_media)
(struct fuse_args* fa) {
    switch (fa->opcode) {
        case FUSE_LOOKUP | FUSE_PREFILTER: {
            const char* name = fa->in_args[0].value;

            bpf_printk("LOOKUP: %lx %s", fa->nodeid, name);
            if (fa->nodeid == 1)
                return FUSE_BPF_USER_FILTER | FUSE_BPF_BACKING;
            else
                return FUSE_BPF_BACKING;
        }

            /* FUSE_FORGET */

        case FUSE_GETATTR | FUSE_PREFILTER: {
            const struct fuse_getattr_in* fgi = fa->in_args[0].value;

            bpf_printk("GETATTR: %d", fgi->fh);
            return FUSE_BPF_BACKING;
        }

        case FUSE_SETATTR | FUSE_PREFILTER: {
            const struct fuse_setattr_in* fsi = fa->in_args[0].value;

            bpf_printk("SETATTR: %d", fsi->fh);
            return FUSE_BPF_BACKING;
        }

            /* FUSE_READLINK */
            /* FUSE_SYMLINK */

        case FUSE_MKNOD | FUSE_PREFILTER: {
            const struct fuse_mknod_in* fmi = fa->in_args[0].value;
            const char* name = fa->in_args[1].value;

            bpf_printk("MKNOD: %s %x %x", name, fmi->rdev | fmi->mode, fmi->umask);
            return FUSE_BPF_BACKING;
        }

        case FUSE_MKDIR | FUSE_PREFILTER: {
            const struct fuse_mkdir_in* fmi = fa->in_args[0].value;
            const char* name = fa->in_args[1].value;

            bpf_printk("MKDIR: %s %x %x", name, fmi->mode, fmi->umask);
            return FUSE_BPF_BACKING;
        }

        case FUSE_UNLINK | FUSE_PREFILTER: {
            const char* name = fa->in_args[0].value;

            bpf_printk("UNLINK: %s", name);
            return FUSE_BPF_BACKING;
        }

        case FUSE_RMDIR | FUSE_PREFILTER: {
            const char* name = fa->in_args[0].value;

            bpf_printk("RMDIR: %s", name);
            return FUSE_BPF_BACKING;
        }

        case FUSE_RENAME | FUSE_PREFILTER: {
            const char* name_old = fa->in_args[1].value;
            const char* name_new = fa->in_args[2].value;

            bpf_printk("RENAME: %s to %s", name_old, name_new);
            return FUSE_BPF_BACKING;
        }

        case FUSE_LINK | FUSE_PREFILTER: {
            const struct fuse_link_in* fli = fa->in_args[0].value;
            const char* dst_name = fa->in_args[1].value;

            bpf_printk("LINK: %d %s", fli->oldnodeid, dst_name);
            return FUSE_BPF_BACKING;
        }

        case FUSE_OPEN | FUSE_PREFILTER: {
            bpf_printk("OPEN: %d", fa->nodeid);
            return FUSE_BPF_BACKING;
        }

        case FUSE_READ | FUSE_PREFILTER: {
            const struct fuse_read_in* fri = fa->in_args[0].value;

            bpf_printk("READ: fh: %lu, offset %lu, size %lu", fri->fh, fri->offset, fri->size);
            return FUSE_BPF_BACKING;
        }

        case FUSE_WRITE | FUSE_PREFILTER: {
            const struct fuse_write_in* fwi = fa->in_args[0].value;

            bpf_printk("WRITE: fh: %lu, offset %lu, size %lu", fwi->fh, fwi->offset, fwi->size);
            return FUSE_BPF_BACKING;
        }

            /* FUSE_STATFS */

        case FUSE_RELEASE | FUSE_PREFILTER: {
            const struct fuse_release_in* fri = fa->in_args[0].value;

            bpf_printk("RELEASE: %d", fri->fh);
            return FUSE_BPF_BACKING;
        }

            /* FUSE_FSYNC */

        case FUSE_SETXATTR | FUSE_PREFILTER: {
            const char* name = fa->in_args[1].value;

            bpf_printk("SETXATTR: %d %s", fa->nodeid, name);
            return FUSE_BPF_BACKING;
        }

        case FUSE_GETXATTR | FUSE_PREFILTER: {
            const char* name = fa->in_args[1].value;

            bpf_printk("GETXATTR: %d %s", fa->nodeid, name);
            return FUSE_BPF_BACKING;
        }

        case FUSE_LISTXATTR | FUSE_PREFILTER: {
            const char* name = fa->in_args[1].value;

            bpf_printk("LISTXATTR: %d %s", fa->nodeid, name);
            return FUSE_BPF_BACKING;
        }

            /* FUSE_REMOVEXATTR */

        case FUSE_FLUSH | FUSE_PREFILTER: {
            const struct fuse_flush_in* ffi = fa->in_args[0].value;

            bpf_printk("FLUSH: %d", ffi->fh);
            return FUSE_BPF_BACKING;
        }

            /* FUSE_INIT */

        case FUSE_OPENDIR | FUSE_PREFILTER: {
            bpf_printk("OPENDIR: %d", fa->nodeid);
            return FUSE_BPF_BACKING;
        }

        case FUSE_READDIR | FUSE_PREFILTER: {
            const struct fuse_read_in* fri = fa->in_args[0].value;
            bpf_printk("READDIR: fh: %lu", fri->fh, fri->offset);
            return FUSE_BPF_BACKING;
        }

        case FUSE_RELEASEDIR | FUSE_PREFILTER: {
            const struct fuse_release_in* fri = fa->in_args[0].value;

            bpf_printk("RELEASEDIR: %d", fri->fh);
            return FUSE_BPF_BACKING;
        }

            /* FUSE_FSYNCDIR */
            /* FUSE_GETLK */
            /* FUSE_SETLK */
            /* FUSE_SETLKW */

        case FUSE_ACCESS | FUSE_PREFILTER: {
            bpf_printk("ACCESS: %d", fa->nodeid);
            return FUSE_BPF_BACKING;
        }

        case FUSE_CREATE | FUSE_PREFILTER: {
            bpf_printk("CREATE: %s", fa->in_args[1].value);
            return FUSE_BPF_BACKING;
        }

            /* FUSE_INTERRUPT */
            /* FUSE_BMAP */
            /* FUSE_DESTROY */
            /* FUSE_IOCTL */
            /* FUSE_POLL */
            /* FUSE_NOTIFY_REPLY */
            /* FUSE_BATCH_FORGET */

        case FUSE_FALLOCATE | FUSE_PREFILTER: {
            const struct fuse_fallocate_in* ffa = fa->in_args[0].value;

            bpf_printk("FALLOCATE: %d %lu", ffa->fh, ffa->length);
            return FUSE_BPF_BACKING;
        }

            /* FUSE_READDIRPLUS */
            /* FUSE_RENAME2 */
            /* FUSE_LSEEK */
            /* FUSE_COPY_FILE_RANGE */
            /* CUSE_INIT */

        case FUSE_CANONICAL_PATH | FUSE_PREFILTER: {
            bpf_printk("CANONICAL_PATH: %d", fa->nodeid);
            return FUSE_BPF_BACKING;
        }

        default:
            if (fa->opcode & FUSE_PREFILTER)
                bpf_printk("Prefilter *** UNKNOWN *** opcode: %d", fa->opcode & FUSE_OPCODE_FILTER);
            else if (fa->opcode & FUSE_POSTFILTER)
                bpf_printk("Postfilter *** UNKNOWN *** opcode: %d",
                           fa->opcode & FUSE_OPCODE_FILTER);
            else
                bpf_printk("*** UNKNOWN *** opcode: %d", fa->opcode);
            return FUSE_BPF_BACKING;
    }
}

LICENSE("GPL");
