// SPDX-License-Identifier: GPL-2.0-or-later
/*
 * Copyright (c) 2015 Red Hat, Inc.
 */

#ifndef __NAMESPACES_CONSTANTS_H__
#define __NAMESPACES_CONSTANTS_H__

#ifndef CLONE_NEWIPC
#  define CLONE_NEWIPC	0x08000000
#endif
#ifndef CLONE_NEWNS
#  define CLONE_NEWNS	0x00020000
#endif
#ifndef CLONE_NEWNET
#  define CLONE_NEWNET	0x40000000
#endif
#ifndef CLONE_NEWPID
#  define CLONE_NEWPID	0x20000000
#endif
#ifndef CLONE_NEWUSER
#  define CLONE_NEWUSER	0x10000000
#endif
#ifndef CLONE_NEWUTS
#  define CLONE_NEWUTS	0x04000000
#endif
#ifndef CLONE_NEWTIME
#  define CLONE_NEWTIME 0x00000080
#endif

#endif /* __NAMESPACES_CONSTANTS_H__ */
