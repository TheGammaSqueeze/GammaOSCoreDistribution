/*
 * gcc -c -std=c89 -mtune=generic -march=x86-64 crti.c
 *
 * NOTE: linking with _old_ crti.o exposes _init and _fini as
 *       global symbols, the newer versions don't.
 *
 * 0000000000000000 g     F .init  0000000000000000 .hidden _init
 * 0000000000000000 g     F .fini  0000000000000000 .hidden _fini
 *
 * So this is a dummy c-runtime object.
 *
 */

void __attribute__((visibility("default")))
_init(void)
{
}

void __attribute__((visibility("default")))
_fini(void)
{
}
