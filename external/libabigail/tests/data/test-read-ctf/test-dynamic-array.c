// test dynamic arrays definitions
// information was detected f field.

struct S
{
  char *a;
  char b[0];
  char c[];
};

void use_struct_s(struct S *)
{
}
