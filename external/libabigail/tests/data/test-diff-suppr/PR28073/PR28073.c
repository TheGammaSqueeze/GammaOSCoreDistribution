/*
 * Compile this twice:
 * gcc -g -c -DBEFORE -o PR28073.before.o PR28073.c
 * gcc -g -c -o PR28073.after.o PR28073.c
 */

#include <inttypes.h>

struct bigstruct {
  char name[128];
  uint8_t bitfield0:1
  #ifndef BEFORE
  ,bitfield1:1
    #endif
    ;
  uint8_t other;
};

void access_bigstruct(struct bigstruct *st)
{
  #ifndef BEFORE
  st->bitfield1 = 1;
  #endif
}
