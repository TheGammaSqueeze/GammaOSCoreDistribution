struct b0
{
  long long m0;
  char m1;
};

struct b1
{
  double m0;
  char m1;
};

struct s0;

typedef int integer;
typedef unsigned char byte;
typedef integer (*mem_fun)(struct s0 *);

struct s0
{

  struct b0 b0;
  struct b1 b1;

  integer m0;
  byte m1;
  mem_fun f;
};

integer
fun(struct s0 *s0)
{
  s0->f = fun;
  return s0->m0 + s0->m1;
}
