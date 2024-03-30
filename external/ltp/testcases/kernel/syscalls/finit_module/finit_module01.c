// SPDX-License-Identifier: GPL-2.0-or-later
/*
 * Copyright (c) 2020 Viresh Kumar <viresh.kumar@linaro.org>
 */

/*\
 * [Description]
 *
 * Basic finit_module() tests.
 *
 * [Algorithm]
 *
 * Inserts a simple module after opening and mmaping the module file.
 */

#include <errno.h>
#include "lapi/init_module.h"
#include "tst_module.h"

#define MODULE_NAME	"finit_module.ko"

static int fd;

static char *mod_path;

static void setup(void)
{
	finit_module_supported_by_kernel();

	tst_module_exists(MODULE_NAME, &mod_path);

	fd = SAFE_OPEN(mod_path, O_RDONLY|O_CLOEXEC);
}

static void run(void)
{
	TST_EXP_PASS(finit_module(fd, "status=valid", 0));
	if (!TST_PASS)
		return;

	tst_module_unload(MODULE_NAME);
}

static void cleanup(void)
{
	SAFE_CLOSE(fd);
}

static struct tst_test test = {
	.test_all = run,
	.setup = setup,
	.cleanup = cleanup,
	.needs_root = 1,
};
