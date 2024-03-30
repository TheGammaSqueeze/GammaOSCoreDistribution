#include <sys/socket.h>
#include <sysexits.h>
#include <unistd.h>

#include <bpf/btf.h>
#include <bpf/libbpf.h>

#include <cstdlib>
#include <sstream>

constexpr int kERROR_BPF_OBJECT_OPEN = 1;
constexpr int kERROR_BTF_NOT_FOUND = 2;
constexpr int kERROR_LOAD_BTF = 3;
constexpr int kERROR_SEND_BTF_FD = 4;
constexpr int kERROR_BTF_TYPE_IDS = 5;

static int no_print(enum libbpf_print_level , const char *, va_list ) {
    return 0;
}

int sendBtfFd(int socket, int fd) {
    char buf[CMSG_SPACE(sizeof(fd))];

    struct msghdr msg;
    memset(&msg, 0, sizeof(msg));
    msg.msg_control = buf;
    msg.msg_controllen = sizeof(buf);

    struct cmsghdr *cmsg = CMSG_FIRSTHDR(&msg);
    cmsg->cmsg_level = SOL_SOCKET;
    cmsg->cmsg_type = SCM_RIGHTS;
    cmsg->cmsg_len = CMSG_LEN(sizeof(fd));

    *(int*)CMSG_DATA(cmsg) = fd;
    return sendmsg(socket, &msg, 0);
}

int main(int argc, char **argv) {
    int ret = 0, socketFd, pipeFd, btfFd;
    if (argc != 4) return EX_USAGE;

    socketFd = atoi(argv[1]);
    pipeFd = atoi(argv[2]);

    auto path(argv[3]);
    ret = libbpf_set_strict_mode(LIBBPF_STRICT_CLEAN_PTRS);
    if (ret) return EX_SOFTWARE;

    libbpf_set_print(no_print);

    struct bpf_object_open_opts opts = {
        .relaxed_maps = true,
        .sz = sizeof(struct bpf_object_open_opts),
    };
    struct bpf_object *obj = bpf_object__open_file(path, &opts);
    if (!obj) return kERROR_BPF_OBJECT_OPEN;

    struct btf *btf = bpf_object__btf(obj);
    if (!btf) return kERROR_BTF_NOT_FOUND;

    ret = btf__load_into_kernel(btf);
    if (ret) {
        if (errno != EINVAL) return kERROR_LOAD_BTF;
        // For BTF_KIND_FUNC, newer kernels can read the BTF_INFO_VLEN bits of
        // struct btf_type to distinguish static vs. global vs. extern
        // functions, but older kernels enforce that only the BTF_INFO_KIND bits
        // can be set. Retry with non-BTF_INFO_KIND bits zeroed out to handle
        // this case.
        for (unsigned int i = 1; i < btf__type_cnt(btf); ++i) {
            struct btf_type *bt = (struct btf_type *)btf__type_by_id(btf, i);
            if (btf_is_func(bt)) {
                bt->info = (BTF_INFO_KIND(bt->info)) << 24;
            }
        }
        if (btf__load_into_kernel(btf)) return kERROR_LOAD_BTF;
    }

    btfFd = btf__fd(btf);
    if (sendBtfFd(socketFd, btf__fd(btf))) return kERROR_SEND_BTF_FD;

    std::ostringstream oss;
    struct bpf_map *m;
    bpf_object__for_each_map(m, obj) {
        unsigned kTid, vTid;
        auto mapName = bpf_map__name(m);
        if (btf__get_map_kv_tids(btf, mapName, bpf_map__key_size(m),
                                 bpf_map__value_size(m), &kTid, &vTid))
            return kERROR_BTF_TYPE_IDS;
        oss << mapName << ' ' << kTid << ' ' << vTid << '\n';
    }
    write(pipeFd, oss.str().c_str(), oss.str().size());

    return EX_OK;
}
