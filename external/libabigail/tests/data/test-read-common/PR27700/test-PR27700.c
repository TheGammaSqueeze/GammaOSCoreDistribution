#include "include-dir/priv.h"

/* gcc -I. -gctf -gdwarf -c -o test-PR27700.o test-PR27700.c */

void
foo(enum foo* c __attribute__((unused)))
{
}
