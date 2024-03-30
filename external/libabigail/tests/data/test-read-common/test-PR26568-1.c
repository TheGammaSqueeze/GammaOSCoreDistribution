/* gcc -std=c89 -gctf -g -mtune=generic -march=x86-64 -c \
 *     -o test-PR26568-1.o test-PR26568-1.c */

struct A {
  union {
    struct {
      int x;
    };
    struct {
      long y;
    };
  };
};

void fun(struct A * a) {
  a->x = 0;
  a->y = 0x0102030405060708ULL;
}
