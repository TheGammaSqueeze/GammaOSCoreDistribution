// gcc -shared -gctf -gdwarf-3 -mtune=generic -march=x86-64 -std=c99 -fPIC -o test4.so test4.c

char *
cpy (char * restrict s1, const char * restrict s2, unsigned int n)
{
  char *t1 = s1;
  const char *t2 = s2;
  while(n-- > 0)
    *t1++ = *t2++;
  return s1;
}
