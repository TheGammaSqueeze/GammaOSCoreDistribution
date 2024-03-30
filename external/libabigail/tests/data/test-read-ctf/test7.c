#include "test7.h"

void first_type_constructor(struct first_type *ft)
{
  ft->member0 = 0;
  ft->member1 = 0;
  ft->ctor = first_type_constructor;
}
