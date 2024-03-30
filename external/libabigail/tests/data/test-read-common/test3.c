/*
 * Test file for creating multiple alias for a symbol
 *
 * NOTE: linking with _old_ crti.o exposes _init and _fini as
 *       global symbols, the newer versions don't.
 *
 * 0000000000000000 g     F .init  0000000000000000 .hidden _init
 * 0000000000000000 g     F .fini  0000000000000000 .hidden _fini
 *
 * This test is looking for those symbols which are not experted.
 * So it's linked with dummy crti.o to avoid false positives.
 *
 * gcc -std=c89 -shared -gctf -g -mtune=generic -march=x86-64 -fPIC \
 *     -nostartfiles -Wl,-soname=test3.so.1 -o test3.so test3.c crti.o
 *
 */

void __foo(void);
void foo(void) __attribute__((weak, alias("__foo")));
void foo__(void) __attribute__((weak, alias("__foo")));
void __foo__(void) __attribute__((alias("__foo")));

void __foo(void)
{

}
