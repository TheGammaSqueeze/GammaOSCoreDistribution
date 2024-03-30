// test function declaration passing const volatile modifier.
long
foo(char* c, const volatile long l);

long
foo(char* c, const volatile long l)
{return *c + l;}

// test function declaration passing variable arguments.
void
bar(const int c, ...)
{}

void
baz(int c)
{c = 0;}

// test function declaration passing an enum type argument.
enum E {e0, e1};

void
bar2(enum E e)
{int c = e; ++c;}

// test function declaration passing a typedef argument.
typedef long long long_long;

long_long
baz2(int c)
{c = 0; return c;}

typedef const volatile unsigned long long useless_long_long;

static useless_long_long
this_should_not_be_seen_by_bidw()
{
  int i = 0;
  bar(0);
  baz2(i);
  return 0;
}
