#include <stdlib.h>

typedef int integer;
typedef unsigned char character;
struct first_type;

void
first_type_constructor(struct first_type *ft);

typedef void (*constructor)();

struct first_type
{
  integer member0;
  character member1;
  constructor ctor;
};

struct second_type
{
  integer member0;
  character member1;
  constructor ctor;
};
