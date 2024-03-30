/* Test a ADT where a struct member is a pointer to
 * itself.
 * gcc -gctf -c test-callback.c -o test-callback.o
 */

struct rb_node_b {
  struct rb_node_b *this;
  int a;
};

struct rb_node_b n1, n2;
