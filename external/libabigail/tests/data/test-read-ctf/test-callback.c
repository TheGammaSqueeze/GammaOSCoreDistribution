/* Test a simple callback as a struct member
 * gcc -gctf -c test-callback.c -o test-callback.o
 */
struct test {
   void (*fn1)(int, long);
};

void f2(int a, long b)
{
}

void assign()
{
   struct test *tt;
   tt->fn1 = f2;
}
