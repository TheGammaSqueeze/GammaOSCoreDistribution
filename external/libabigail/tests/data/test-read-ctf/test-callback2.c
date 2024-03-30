/* Test a simple callback as a struct member
 * that takes a pointer to a struct parent as
 * argument
 * gcc -gctf -c test-callback2.c -o test-callback2.o
 */
struct s0
{
  int (*mem_fun)(struct s0 *);
};

struct s0 *s0;
