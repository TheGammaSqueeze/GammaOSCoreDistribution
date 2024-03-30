/* Test declaring twice a function pinter a struct member
 * gcc -gctf -c test-functions-declaration.c -o \
 *    test-functions-declaration.o
 */
void
attribute_container_add_device(
          void (*fn1)(int, long))
{
}

void
attribute_container_device_trigger(
       void (*fn2)(int , long))
{

}
