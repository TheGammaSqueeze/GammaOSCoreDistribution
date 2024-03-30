#include <signal.h>
#include <string.h>
#include <unistd.h>
#include "pthread_impl.h"
#include "lock.h"

int pthread_sigqueue(pthread_t t, int sig, const union sigval value)
{
	siginfo_t si;
	int r;
	memset(&si, 0, sizeof si);
	si.si_signo = sig;
	si.si_code = SI_QUEUE;
	si.si_value = value;
	si.si_uid = getuid();
	si.si_pid = getpid();
	LOCK(t->killlock);
	r = t->tid ? -__syscall(SYS_rt_tgsigqueueinfo, si.si_pid, t->tid, sig, &si)
		: (sig+0U >= _NSIG ? EINVAL : 0);
	UNLOCK(t->killlock);
	return r;
}
