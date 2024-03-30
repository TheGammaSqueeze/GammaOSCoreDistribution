
#include <stdio.h>

char* test_pointer = NULL;
int test_array[10] = {0};
volatile short test_volatile = 1;
float test_float = 0.0;

struct {
  unsigned int status0 : 1;
  unsigned int status1 : 1;
} status;


struct S
{
  int m0;
};

const struct S test_const;
long* restrict test_restrict;

int
foo_1(struct S* s)
{
  return s->m0;
}

int main()
{

}
