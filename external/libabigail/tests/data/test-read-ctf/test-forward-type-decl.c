/* Test a forward type declaration as a struct member
 * to exercise circular dependencies.
 * gcc -gctf -c test-forward-type-decl.c -o \
 *    test-forward-type-decl.o
 */
typedef struct page *page_t;

struct rb_node {
  struct rb_node *rb_left;
};

struct address_space;

struct page {
  struct address_space *mapping;
};

struct address_space {
  struct rb_node *rb_root;
  struct page *page;
};

struct address_space *addr;
