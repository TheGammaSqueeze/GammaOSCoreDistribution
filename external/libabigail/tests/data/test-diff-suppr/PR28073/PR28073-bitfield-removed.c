/*
 * Compile this with:
 * gcc -g -c PR28073-bitfield-removed.c
 */
#include <inttypes.h>

struct bigstruct {
  char name[128];
  uint8_t other;
};

void access_bigstruct(struct bigstruct *st __attribute__((unused)))
{
}
