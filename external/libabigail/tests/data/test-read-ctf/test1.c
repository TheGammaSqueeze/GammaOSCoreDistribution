enum opaque_enum;
struct opaque_struct;

typedef enum opaque_enum opaque_enum;
typedef struct opaque_struct opaque_struct;

void
fn(opaque_struct *, opaque_enum *e);

enum opaque_enum
{
  e0,
  e1
};

struct opaque_struct
{
  opaque_enum m0;
};

void
fn(opaque_struct * s, opaque_enum *e)
{
  s->m0 = *e;
}
