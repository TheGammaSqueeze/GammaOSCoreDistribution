/* Test a array of pointer definition as struct member
 * gcc -gctf -c test-array-of-pointers.c -o test-array-of-pointer.o
 */
struct task {
 struct css_set *s;
};

struct state {
 struct cgroup *cg;
};

struct css_set {
 struct state *s0[4];
};

struct cgroup {
 struct state *s1[4];
};

struct task *t;
