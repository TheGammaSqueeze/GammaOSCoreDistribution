/* gcc -std=c89 -gctf -g -mtune=generic -march=x86-64 -c \
 *     -o test-PR26568-2.o test-PR26568-2.c */

union A {
  struct {
    int x;
  };
  struct {
    long y;
  };
};

void fun(union A * a) {
  a->x = 0;
  a->y = 0x0102030405060708ULL;
}
