// SPDX-License-Identifier: GPL-2.0-or-later
/*
 * Copyright (c) 2015 Linux Test Project
 */

#ifndef __GETRANDOM_H__
#define __GETRANDOM_H__

#include "config.h"

#if HAVE_LINUX_RANDOM_H
#include <linux/random.h>
#endif

/*
 * Flags for getrandom(2)
 *
 * GRND_NONBLOCK	Don't block and return EAGAIN instead
 * GRND_RANDOM		Use the /dev/random pool instead of /dev/urandom
 */

#ifndef GRND_NONBLOCK
# define GRND_NONBLOCK	0x0001
#endif

#ifndef GRND_RANDOM
# define GRND_RANDOM	0x0002
#endif

#endif /* __GETRANDOM_H__ */
