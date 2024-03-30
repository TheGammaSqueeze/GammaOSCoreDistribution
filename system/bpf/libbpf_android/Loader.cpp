/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#define LOG_TAG "LibBpfLoader"

#include <errno.h>
#include <linux/bpf.h>
#include <linux/elf.h>
#include <log/log.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sysexits.h>
#include <sys/stat.h>
#include <sys/utsname.h>
#include <sys/wait.h>
#include <unistd.h>

// This is BpfLoader v0.19
#define BPFLOADER_VERSION_MAJOR 0u
#define BPFLOADER_VERSION_MINOR 19u
#define BPFLOADER_VERSION ((BPFLOADER_VERSION_MAJOR << 16) | BPFLOADER_VERSION_MINOR)

#include "bpf/BpfUtils.h"
#include "bpf/bpf_map_def.h"
#include "include/libbpf_android.h"

#include <bpf/bpf.h>

#include <cstdlib>
#include <fstream>
#include <iostream>
#include <optional>
#include <string>
#include <unordered_map>
#include <vector>

#include <android-base/cmsg.h>
#include <android-base/file.h>
#include <android-base/strings.h>
#include <android-base/unique_fd.h>

#define BPF_FS_PATH "/sys/fs/bpf/"

// Size of the BPF log buffer for verifier logging
#define BPF_LOAD_LOG_SZ 0xfffff

// Unspecified attach type is 0 which is BPF_CGROUP_INET_INGRESS.
#define BPF_ATTACH_TYPE_UNSPEC BPF_CGROUP_INET_INGRESS

using android::base::StartsWith;
using android::base::unique_fd;
using std::ifstream;
using std::ios;
using std::optional;
using std::string;
using std::vector;

namespace android {
namespace bpf {

constexpr const char* lookupSelinuxContext(const domain d, const char* const unspecified = "") {
    switch (d) {
        case domain::unspecified:   return unspecified;
        case domain::platform:      return "fs_bpf";
        case domain::tethering:     return "fs_bpf_tethering";
        case domain::net_private:   return "fs_bpf_net_private";
        case domain::net_shared:    return "fs_bpf_net_shared";
        case domain::netd_readonly: return "fs_bpf_netd_readonly";
        case domain::netd_shared:   return "fs_bpf_netd_shared";
        case domain::vendor:        return "fs_bpf_vendor";
        default:                    return "(unrecognized)";
    }
}

domain getDomainFromSelinuxContext(const char s[BPF_SELINUX_CONTEXT_CHAR_ARRAY_SIZE]) {
    for (domain d : AllDomains) {
        // Not sure how to enforce this at compile time, so abort() bpfloader at boot instead
        if (strlen(lookupSelinuxContext(d)) >= BPF_SELINUX_CONTEXT_CHAR_ARRAY_SIZE) abort();
        if (!strncmp(s, lookupSelinuxContext(d), BPF_SELINUX_CONTEXT_CHAR_ARRAY_SIZE)) return d;
    }
    ALOGW("ignoring unrecognized selinux_context '%32s'", s);
    // We should return 'unrecognized' here, however: returning unspecified will
    // result in the system simply using the default context, which in turn
    // will allow future expansion by adding more restrictive selinux types.
    // Older bpfloader will simply ignore that, and use the less restrictive default.
    // This does mean you CANNOT later add a *less* restrictive type than the default.
    //
    // Note: we cannot just abort() here as this might be a mainline module shipped optional update
    return domain::unspecified;
}

constexpr const char* lookupPinSubdir(const domain d, const char* const unspecified = "") {
    switch (d) {
        case domain::unspecified:   return unspecified;
        case domain::platform:      return "/";
        case domain::tethering:     return "tethering/";
        case domain::net_private:   return "net_private/";
        case domain::net_shared:    return "net_shared/";
        case domain::netd_readonly: return "netd_readonly/";
        case domain::netd_shared:   return "netd_shared/";
        case domain::vendor:        return "vendor/";
        default:                    return "(unrecognized)";
    }
};

domain getDomainFromPinSubdir(const char s[BPF_PIN_SUBDIR_CHAR_ARRAY_SIZE]) {
    for (domain d : AllDomains) {
        // Not sure how to enforce this at compile time, so abort() bpfloader at boot instead
        if (strlen(lookupPinSubdir(d)) >= BPF_PIN_SUBDIR_CHAR_ARRAY_SIZE) abort();
        if (!strncmp(s, lookupPinSubdir(d), BPF_PIN_SUBDIR_CHAR_ARRAY_SIZE)) return d;
    }
    ALOGE("unrecognized pin_subdir '%32s'", s);
    // pin_subdir affects the object's full pathname,
    // and thus using the default would change the location and thus our code's ability to find it,
    // hence this seems worth treating as a true error condition.
    //
    // Note: we cannot just abort() here as this might be a mainline module shipped optional update
    // However, our callers will treat this as an error, and stop loading the specific .o,
    // which will fail bpfloader if the .o is marked critical.
    return domain::unrecognized;
}

static string pathToFilename(const string& path, bool noext = false) {
    vector<string> spath = android::base::Split(path, "/");
    string ret = spath.back();

    if (noext) {
        size_t lastindex = ret.find_last_of('.');
        return ret.substr(0, lastindex);
    }
    return ret;
}

typedef struct {
    const char* name;
    enum bpf_prog_type type;
    enum bpf_attach_type expected_attach_type;
} sectionType;

/*
 * Map section name prefixes to program types, the section name will be:
 *   SECTION(<prefix>/<name-of-program>)
 * For example:
 *   SECTION("tracepoint/sched_switch_func") where sched_switch_funcs
 * is the name of the program, and tracepoint is the type.
 *
 * However, be aware that you should not be directly using the SECTION() macro.
 * Instead use the DEFINE_(BPF|XDP)_(PROG|MAP)... & LICENSE/CRITICAL macros.
 */
sectionType sectionNameTypes[] = {
        {"bind4/", BPF_PROG_TYPE_CGROUP_SOCK_ADDR, BPF_CGROUP_INET4_BIND},
        {"bind6/", BPF_PROG_TYPE_CGROUP_SOCK_ADDR, BPF_CGROUP_INET6_BIND},
        {"cgroupskb/", BPF_PROG_TYPE_CGROUP_SKB, BPF_ATTACH_TYPE_UNSPEC},
        {"cgroupsock/", BPF_PROG_TYPE_CGROUP_SOCK, BPF_ATTACH_TYPE_UNSPEC},
        {"kprobe/", BPF_PROG_TYPE_KPROBE, BPF_ATTACH_TYPE_UNSPEC},
        {"perf_event/", BPF_PROG_TYPE_PERF_EVENT, BPF_ATTACH_TYPE_UNSPEC},
        {"schedact/", BPF_PROG_TYPE_SCHED_ACT, BPF_ATTACH_TYPE_UNSPEC},
        {"schedcls/", BPF_PROG_TYPE_SCHED_CLS, BPF_ATTACH_TYPE_UNSPEC},
        {"skfilter/", BPF_PROG_TYPE_SOCKET_FILTER, BPF_ATTACH_TYPE_UNSPEC},
        {"tracepoint/", BPF_PROG_TYPE_TRACEPOINT, BPF_ATTACH_TYPE_UNSPEC},
        {"xdp/", BPF_PROG_TYPE_XDP, BPF_ATTACH_TYPE_UNSPEC},
};

typedef struct {
    enum bpf_prog_type type;
    enum bpf_attach_type expected_attach_type;
    string name;
    vector<char> data;
    vector<char> rel_data;
    optional<struct bpf_prog_def> prog_def;

    unique_fd prog_fd; /* fd after loading */
} codeSection;

static int readElfHeader(ifstream& elfFile, Elf64_Ehdr* eh) {
    elfFile.seekg(0);
    if (elfFile.fail()) return -1;

    if (!elfFile.read((char*)eh, sizeof(*eh))) return -1;

    return 0;
}

/* Reads all section header tables into an Shdr array */
static int readSectionHeadersAll(ifstream& elfFile, vector<Elf64_Shdr>& shTable) {
    Elf64_Ehdr eh;
    int ret = 0;

    ret = readElfHeader(elfFile, &eh);
    if (ret) return ret;

    elfFile.seekg(eh.e_shoff);
    if (elfFile.fail()) return -1;

    /* Read shdr table entries */
    shTable.resize(eh.e_shnum);

    if (!elfFile.read((char*)shTable.data(), (eh.e_shnum * eh.e_shentsize))) return -ENOMEM;

    return 0;
}

/* Read a section by its index - for ex to get sec hdr strtab blob */
static int readSectionByIdx(ifstream& elfFile, int id, vector<char>& sec) {
    vector<Elf64_Shdr> shTable;
    int ret = readSectionHeadersAll(elfFile, shTable);
    if (ret) return ret;

    elfFile.seekg(shTable[id].sh_offset);
    if (elfFile.fail()) return -1;

    sec.resize(shTable[id].sh_size);
    if (!elfFile.read(sec.data(), shTable[id].sh_size)) return -1;

    return 0;
}

/* Read whole section header string table */
static int readSectionHeaderStrtab(ifstream& elfFile, vector<char>& strtab) {
    Elf64_Ehdr eh;
    int ret = readElfHeader(elfFile, &eh);
    if (ret) return ret;

    ret = readSectionByIdx(elfFile, eh.e_shstrndx, strtab);
    if (ret) return ret;

    return 0;
}

/* Get name from offset in strtab */
static int getSymName(ifstream& elfFile, int nameOff, string& name) {
    int ret;
    vector<char> secStrTab;

    ret = readSectionHeaderStrtab(elfFile, secStrTab);
    if (ret) return ret;

    if (nameOff >= (int)secStrTab.size()) return -1;

    name = string((char*)secStrTab.data() + nameOff);
    return 0;
}

/* Reads a full section by name - example to get the GPL license */
static int readSectionByName(const char* name, ifstream& elfFile, vector<char>& data) {
    vector<char> secStrTab;
    vector<Elf64_Shdr> shTable;
    int ret;

    ret = readSectionHeadersAll(elfFile, shTable);
    if (ret) return ret;

    ret = readSectionHeaderStrtab(elfFile, secStrTab);
    if (ret) return ret;

    for (int i = 0; i < (int)shTable.size(); i++) {
        char* secname = secStrTab.data() + shTable[i].sh_name;
        if (!secname) continue;

        if (!strcmp(secname, name)) {
            vector<char> dataTmp;
            dataTmp.resize(shTable[i].sh_size);

            elfFile.seekg(shTable[i].sh_offset);
            if (elfFile.fail()) return -1;

            if (!elfFile.read((char*)dataTmp.data(), shTable[i].sh_size)) return -1;

            data = dataTmp;
            return 0;
        }
    }
    return -2;
}

unsigned int readSectionUint(const char* name, ifstream& elfFile, unsigned int defVal) {
    vector<char> theBytes;
    int ret = readSectionByName(name, elfFile, theBytes);
    if (ret) {
        ALOGD("Couldn't find section %s (defaulting to %u [0x%x]).", name, defVal, defVal);
        return defVal;
    } else if (theBytes.size() < sizeof(unsigned int)) {
        ALOGE("Section %s too short (defaulting to %u [0x%x]).", name, defVal, defVal);
        return defVal;
    } else {
        // decode first 4 bytes as LE32 uint, there will likely be more bytes due to alignment.
        unsigned int value = static_cast<unsigned char>(theBytes[3]);
        value <<= 8;
        value += static_cast<unsigned char>(theBytes[2]);
        value <<= 8;
        value += static_cast<unsigned char>(theBytes[1]);
        value <<= 8;
        value += static_cast<unsigned char>(theBytes[0]);
        ALOGI("Section %s value is %u [0x%x]", name, value, value);
        return value;
    }
}

static int readSectionByType(ifstream& elfFile, int type, vector<char>& data) {
    int ret;
    vector<Elf64_Shdr> shTable;

    ret = readSectionHeadersAll(elfFile, shTable);
    if (ret) return ret;

    for (int i = 0; i < (int)shTable.size(); i++) {
        if ((int)shTable[i].sh_type != type) continue;

        vector<char> dataTmp;
        dataTmp.resize(shTable[i].sh_size);

        elfFile.seekg(shTable[i].sh_offset);
        if (elfFile.fail()) return -1;

        if (!elfFile.read((char*)dataTmp.data(), shTable[i].sh_size)) return -1;

        data = dataTmp;
        return 0;
    }
    return -2;
}

static bool symCompare(Elf64_Sym a, Elf64_Sym b) {
    return (a.st_value < b.st_value);
}

static int readSymTab(ifstream& elfFile, int sort, vector<Elf64_Sym>& data) {
    int ret, numElems;
    Elf64_Sym* buf;
    vector<char> secData;

    ret = readSectionByType(elfFile, SHT_SYMTAB, secData);
    if (ret) return ret;

    buf = (Elf64_Sym*)secData.data();
    numElems = (secData.size() / sizeof(Elf64_Sym));
    data.assign(buf, buf + numElems);

    if (sort) std::sort(data.begin(), data.end(), symCompare);
    return 0;
}

static enum bpf_prog_type getSectionType(string& name) {
    for (auto& snt : sectionNameTypes)
        if (StartsWith(name, snt.name)) return snt.type;

    // TODO Remove this code when fuse-bpf is upstream and this BPF_PROG_TYPE_FUSE is fixed
    if (StartsWith(name, "fuse/")) {
        int result = BPF_PROG_TYPE_UNSPEC;
        ifstream("/sys/fs/fuse/bpf_prog_type_fuse") >> result;
        return static_cast<bpf_prog_type>(result);
    }

    return BPF_PROG_TYPE_UNSPEC;
}

static enum bpf_attach_type getExpectedAttachType(string& name) {
    for (auto& snt : sectionNameTypes)
        if (StartsWith(name, snt.name)) return snt.expected_attach_type;
    return BPF_ATTACH_TYPE_UNSPEC;
}

static string getSectionName(enum bpf_prog_type type)
{
    for (auto& snt : sectionNameTypes)
        if (snt.type == type)
            return string(snt.name);

    return "UNKNOWN SECTION NAME " + std::to_string(type);
}

static int readProgDefs(ifstream& elfFile, vector<struct bpf_prog_def>& pd,
                        size_t sizeOfBpfProgDef) {
    vector<char> pdData;
    int ret = readSectionByName("progs", elfFile, pdData);
    // Older file formats do not require a 'progs' section at all.
    // (We should probably figure out whether this is behaviour which is safe to remove now.)
    if (ret == -2) return 0;
    if (ret) return ret;

    if (pdData.size() % sizeOfBpfProgDef) {
        ALOGE("readProgDefs failed due to improper sized progs section, %zu %% %zu != 0",
              pdData.size(), sizeOfBpfProgDef);
        return -1;
    };

    int progCount = pdData.size() / sizeOfBpfProgDef;
    pd.resize(progCount);
    size_t trimmedSize = std::min(sizeOfBpfProgDef, sizeof(struct bpf_prog_def));

    const char* dataPtr = pdData.data();
    for (auto& p : pd) {
        // First we zero initialize
        memset(&p, 0, sizeof(p));
        // Then we set non-zero defaults
        p.bpfloader_max_ver = DEFAULT_BPFLOADER_MAX_VER;  // v1.0
        // Then we copy over the structure prefix from the ELF file.
        memcpy(&p, dataPtr, trimmedSize);
        // Move to next struct in the ELF file
        dataPtr += sizeOfBpfProgDef;
    }
    return 0;
}

static int getSectionSymNames(ifstream& elfFile, const string& sectionName, vector<string>& names,
                              optional<unsigned> symbolType = std::nullopt) {
    int ret;
    string name;
    vector<Elf64_Sym> symtab;
    vector<Elf64_Shdr> shTable;

    ret = readSymTab(elfFile, 1 /* sort */, symtab);
    if (ret) return ret;

    /* Get index of section */
    ret = readSectionHeadersAll(elfFile, shTable);
    if (ret) return ret;

    int sec_idx = -1;
    for (int i = 0; i < (int)shTable.size(); i++) {
        ret = getSymName(elfFile, shTable[i].sh_name, name);
        if (ret) return ret;

        if (!name.compare(sectionName)) {
            sec_idx = i;
            break;
        }
    }

    /* No section found with matching name*/
    if (sec_idx == -1) {
        ALOGW("No %s section could be found in elf object", sectionName.c_str());
        return -1;
    }

    for (int i = 0; i < (int)symtab.size(); i++) {
        if (symbolType.has_value() && ELF_ST_TYPE(symtab[i].st_info) != symbolType) continue;

        if (symtab[i].st_shndx == sec_idx) {
            string s;
            ret = getSymName(elfFile, symtab[i].st_name, s);
            if (ret) return ret;
            names.push_back(s);
        }
    }

    return 0;
}

static bool IsAllowed(bpf_prog_type type, const bpf_prog_type* allowed, size_t numAllowed) {
    if (allowed == nullptr) return true;

    for (size_t i = 0; i < numAllowed; i++) {
        if (type == allowed[i]) return true;
    }

    return false;
}

/* Read a section by its index - for ex to get sec hdr strtab blob */
static int readCodeSections(ifstream& elfFile, vector<codeSection>& cs, size_t sizeOfBpfProgDef,
                            const bpf_prog_type* allowed, size_t numAllowed) {
    vector<Elf64_Shdr> shTable;
    int entries, ret = 0;

    ret = readSectionHeadersAll(elfFile, shTable);
    if (ret) return ret;
    entries = shTable.size();

    vector<struct bpf_prog_def> pd;
    ret = readProgDefs(elfFile, pd, sizeOfBpfProgDef);
    if (ret) return ret;
    vector<string> progDefNames;
    ret = getSectionSymNames(elfFile, "progs", progDefNames);
    if (!pd.empty() && ret) return ret;

    for (int i = 0; i < entries; i++) {
        string name;
        codeSection cs_temp;
        cs_temp.type = BPF_PROG_TYPE_UNSPEC;

        ret = getSymName(elfFile, shTable[i].sh_name, name);
        if (ret) return ret;

        enum bpf_prog_type ptype = getSectionType(name);

        if (ptype == BPF_PROG_TYPE_UNSPEC) continue;

        if (!IsAllowed(ptype, allowed, numAllowed)) {
            ALOGE("Program type %s not permitted here", getSectionName(ptype).c_str());
            return -1;
        }

        // This must be done before '/' is replaced with '_'.
        cs_temp.expected_attach_type = getExpectedAttachType(name);

        string oldName = name;

        // convert all slashes to underscores
        std::replace(name.begin(), name.end(), '/', '_');

        cs_temp.type = ptype;
        cs_temp.name = name;

        ret = readSectionByIdx(elfFile, i, cs_temp.data);
        if (ret) return ret;
        ALOGD("Loaded code section %d (%s)", i, name.c_str());

        vector<string> csSymNames;
        ret = getSectionSymNames(elfFile, oldName, csSymNames, STT_FUNC);
        if (ret || !csSymNames.size()) return ret;
        for (size_t i = 0; i < progDefNames.size(); ++i) {
            if (!progDefNames[i].compare(csSymNames[0] + "_def")) {
                cs_temp.prog_def = pd[i];
                break;
            }
        }

        /* Check for rel section */
        if (cs_temp.data.size() > 0 && i < entries) {
            ret = getSymName(elfFile, shTable[i + 1].sh_name, name);
            if (ret) return ret;

            if (name == (".rel" + oldName)) {
                ret = readSectionByIdx(elfFile, i + 1, cs_temp.rel_data);
                if (ret) return ret;
                ALOGD("Loaded relo section %d (%s)", i, name.c_str());
            }
        }

        if (cs_temp.data.size() > 0) {
            cs.push_back(std::move(cs_temp));
            ALOGD("Adding section %d to cs list", i);
        }
    }
    return 0;
}

static int getSymNameByIdx(ifstream& elfFile, int index, string& name) {
    vector<Elf64_Sym> symtab;
    int ret = 0;

    ret = readSymTab(elfFile, 0 /* !sort */, symtab);
    if (ret) return ret;

    if (index >= (int)symtab.size()) return -1;

    return getSymName(elfFile, symtab[index].st_name, name);
}

static bool waitpidTimeout(pid_t pid, int timeoutMs) {
    // Add SIGCHLD to the signal set.
    sigset_t child_mask, original_mask;
    sigemptyset(&child_mask);
    sigaddset(&child_mask, SIGCHLD);
    if (sigprocmask(SIG_BLOCK, &child_mask, &original_mask) == -1) return false;

    // Wait for a SIGCHLD notification.
    errno = 0;
    timespec ts = {0, timeoutMs * 1000000};
    int wait_result = TEMP_FAILURE_RETRY(sigtimedwait(&child_mask, nullptr, &ts));

    // Restore the original signal set.
    sigprocmask(SIG_SETMASK, &original_mask, nullptr);

    if (wait_result == -1) return false;

    int status;
    return TEMP_FAILURE_RETRY(waitpid(pid, &status, WNOHANG)) == pid;
}

static std::optional<unique_fd> getMapBtfInfo(const char* elfPath,
                         std::unordered_map<string, std::pair<uint32_t, uint32_t>> &btfTypeIds) {
    unique_fd bpfloaderSocket, btfloaderSocket;
    if (!android::base::Socketpair(AF_UNIX, SOCK_DGRAM | SOCK_NONBLOCK, 0, &bpfloaderSocket,
                                   &btfloaderSocket)) {
        return {};
    }

    unique_fd pipeRead, pipeWrite;
    if (!android::base::Pipe(&pipeRead, &pipeWrite, O_NONBLOCK)) {
        return {};
    }

    pid_t pid = fork();
    if (pid < 0) return {};
    if (!pid) {
        bpfloaderSocket.reset();
        pipeRead.reset();
        auto socketFdStr = std::to_string(btfloaderSocket.release());
        auto pipeFdStr = std::to_string(pipeWrite.release());

        if (execl("/system/bin/btfloader", "/system/bin/btfloader", socketFdStr.c_str(),
                  pipeFdStr.c_str(), elfPath, NULL) == -1) {
            ALOGW("exec btfloader failed with errno %d (%s)", errno, strerror(errno));
            exit(EX_UNAVAILABLE);
        }
    }
    btfloaderSocket.reset();
    pipeWrite.reset();
    if (!waitpidTimeout(pid, 100)) {
        kill(pid, SIGKILL);
        return {};
    }

    unique_fd btfFd;
    if (android::base::ReceiveFileDescriptors(bpfloaderSocket, nullptr, 0, &btfFd)) return {};

    std::string btfTypeIdStr;
    if (!android::base::ReadFdToString(pipeRead, &btfTypeIdStr)) return {};
    if (!btfFd.ok()) return {};

    const auto mapTypeIdLines = android::base::Split(btfTypeIdStr, "\n");
    for (const auto &line : mapTypeIdLines) {
        const auto vec = android::base::Split(line, " ");
        // Splitting on newline will give us one empty line
        if (vec.size() != 3) continue;
        const int kTid = atoi(vec[1].c_str());
        const int vTid = atoi(vec[2].c_str());
        if (!kTid || !vTid) return {};
        btfTypeIds[vec[0]] = std::make_pair(kTid, vTid);
    }
    return btfFd;
}

static bool mapMatchesExpectations(unique_fd& fd, string& mapName, struct bpf_map_def& mapDef,
                                   enum bpf_map_type type) {
    // bpfGetFd... family of functions require at minimum a 4.14 kernel,
    // so on 4.9 kernels just pretend the map matches our expectations.
    // This isn't really a problem as we only really support 4.14+ anyway...
    // Additionally we'll get almost equivalent test coverage on newer devices/kernels.
    // This is because the primary failure mode we're trying to detect here
    // is either a source code misconfiguration (which is likely kernel independent)
    // or a newly introduced kernel feature/bug (which is unlikely to get backported to 4.9).
    if (!isAtLeastKernelVersion(4, 14, 0)) return true;

    // These asserts should *never* trigger, if one of them somehow does,
    // it probably means a bpf .o file has been changed/replaced at runtime
    // and bpfloader was manually rerun (normally it should only run *once*
    // early during the boot process).
    // Another possibility is that something is misconfigured in the code:
    // most likely a shared map is declared twice differently.
    // But such a change should never be checked into the source tree...
    int fd_type = bpfGetFdMapType(fd);
    int fd_key_size = bpfGetFdKeySize(fd);
    int fd_value_size = bpfGetFdValueSize(fd);
    int fd_max_entries = bpfGetFdMaxEntries(fd);
    int fd_map_flags = bpfGetFdMapFlags(fd);

    // DEVMAPs are readonly from the bpf program side's point of view, as such
    // the kernel in kernel/bpf/devmap.c dev_map_init_map() will set the flag
    int desired_map_flags = (int)mapDef.map_flags;
    if (type == BPF_MAP_TYPE_DEVMAP || type == BPF_MAP_TYPE_DEVMAP_HASH)
        desired_map_flags |= BPF_F_RDONLY_PROG;

    // If anything doesn't match, just close the fd - it's of no use anyway.
    if (fd_type != type) fd.reset();
    if (fd_key_size != (int)mapDef.key_size) fd.reset();
    if (fd_value_size != (int)mapDef.value_size) fd.reset();
    if (fd_max_entries != (int)mapDef.max_entries) fd.reset();
    if (fd_map_flags != desired_map_flags) fd.reset();

    if (fd.ok()) return true;

    ALOGE("bpf map name %s mismatch: desired/found: "
          "type:%d/%d key:%u/%d value:%u/%d entries:%u/%d flags:%u/%d",
          mapName.c_str(), type, fd_type, mapDef.key_size, fd_key_size, mapDef.value_size,
          fd_value_size, mapDef.max_entries, fd_max_entries, desired_map_flags, fd_map_flags);
    return false;
}

static int createMaps(const char* elfPath, ifstream& elfFile, vector<unique_fd>& mapFds,
                      const char* prefix, const unsigned long long allowedDomainBitmask,
                      const size_t sizeOfBpfMapDef) {
    int ret;
    vector<char> mdData, btfData;
    vector<struct bpf_map_def> md;
    vector<string> mapNames;
    std::unordered_map<string, std::pair<uint32_t, uint32_t>> btfTypeIdMap;
    string fname = pathToFilename(string(elfPath), true);

    ret = readSectionByName("maps", elfFile, mdData);
    if (ret == -2) return 0;  // no maps to read
    if (ret) return ret;

    if (mdData.size() % sizeOfBpfMapDef) {
        ALOGE("createMaps failed due to improper sized maps section, %zu %% %zu != 0",
              mdData.size(), sizeOfBpfMapDef);
        return -1;
    };

    int mapCount = mdData.size() / sizeOfBpfMapDef;
    md.resize(mapCount);
    size_t trimmedSize = std::min(sizeOfBpfMapDef, sizeof(struct bpf_map_def));

    const char* dataPtr = mdData.data();
    for (auto& m : md) {
        // First we zero initialize
        memset(&m, 0, sizeof(m));
        // Then we set non-zero defaults
        m.bpfloader_max_ver = DEFAULT_BPFLOADER_MAX_VER;  // v1.0
        m.max_kver = 0xFFFFFFFFu;                         // matches KVER_INF from bpf_helpers.h
        // Then we copy over the structure prefix from the ELF file.
        memcpy(&m, dataPtr, trimmedSize);
        // Move to next struct in the ELF file
        dataPtr += sizeOfBpfMapDef;
    }

    ret = getSectionSymNames(elfFile, "maps", mapNames);
    if (ret) return ret;

    unsigned btfMinBpfLoaderVer = readSectionUint("btf_min_bpfloader_ver", elfFile, 0);
    unsigned btfMinKernelVer = readSectionUint("btf_min_kernel_ver", elfFile, 0);
    unsigned kvers = kernelVersion();

    std::optional<unique_fd> btfFd;
    if ((BPFLOADER_VERSION >= btfMinBpfLoaderVer) && (kvers >= btfMinKernelVer) &&
        (!readSectionByName(".BTF", elfFile, btfData))) {
        btfFd = getMapBtfInfo(elfPath, btfTypeIdMap);
    }

    for (int i = 0; i < (int)mapNames.size(); i++) {
        if (BPFLOADER_VERSION < md[i].bpfloader_min_ver) {
            ALOGI("skipping map %s which requires bpfloader min ver 0x%05x", mapNames[i].c_str(),
                  md[i].bpfloader_min_ver);
            mapFds.push_back(unique_fd());
            continue;
        }

        if (BPFLOADER_VERSION >= md[i].bpfloader_max_ver) {
            ALOGI("skipping map %s which requires bpfloader max ver 0x%05x", mapNames[i].c_str(),
                  md[i].bpfloader_max_ver);
            mapFds.push_back(unique_fd());
            continue;
        }

        if (kvers < md[i].min_kver) {
            ALOGI("skipping map %s which requires kernel version 0x%x >= 0x%x",
                  mapNames[i].c_str(), kvers, md[i].min_kver);
            mapFds.push_back(unique_fd());
            continue;
        }

        if (kvers >= md[i].max_kver) {
            ALOGI("skipping map %s which requires kernel version 0x%x < 0x%x",
                  mapNames[i].c_str(), kvers, md[i].max_kver);
            mapFds.push_back(unique_fd());
            continue;
        }

        enum bpf_map_type type = md[i].type;
        if (type == BPF_MAP_TYPE_DEVMAP && !isAtLeastKernelVersion(4, 14, 0)) {
            // On Linux Kernels older than 4.14 this map type doesn't exist, but it can kind
            // of be approximated: ARRAY has the same userspace api, though it is not usable
            // by the same ebpf programs.  However, that's okay because the bpf_redirect_map()
            // helper doesn't exist on 4.9 anyway (so the bpf program would fail to load,
            // and thus needs to be tagged as 4.14+ either way), so there's nothing useful you
            // could do with a DEVMAP anyway (that isn't already provided by an ARRAY)...
            // Hence using an ARRAY instead of a DEVMAP simply makes life easier for userspace.
            type = BPF_MAP_TYPE_ARRAY;
        }
        if (type == BPF_MAP_TYPE_DEVMAP_HASH && !isAtLeastKernelVersion(5, 4, 0)) {
            // On Linux Kernels older than 5.4 this map type doesn't exist, but it can kind
            // of be approximated: HASH has the same userspace visible api.
            // However it cannot be used by ebpf programs in the same way.
            // Since bpf_redirect_map() only requires 4.14, a program using a DEVMAP_HASH map
            // would fail to load (due to trying to redirect to a HASH instead of DEVMAP_HASH).
            // One must thus tag any BPF_MAP_TYPE_DEVMAP_HASH + bpf_redirect_map() using
            // programs as being 5.4+...
            type = BPF_MAP_TYPE_HASH;
        }

        domain selinux_context = getDomainFromSelinuxContext(md[i].selinux_context);
        if (specified(selinux_context)) {
            if (!inDomainBitmask(selinux_context, allowedDomainBitmask)) {
                ALOGE("map %s has invalid selinux_context of %d (allowed bitmask 0x%llx)",
                      mapNames[i].c_str(), selinux_context, allowedDomainBitmask);
                return -EINVAL;
            }
            ALOGI("map %s selinux_context [%32s] -> %d -> '%s' (%s)", mapNames[i].c_str(),
                  md[i].selinux_context, selinux_context, lookupSelinuxContext(selinux_context),
                  lookupPinSubdir(selinux_context));
        }

        domain pin_subdir = getDomainFromPinSubdir(md[i].pin_subdir);
        if (unrecognized(pin_subdir)) return -ENOTDIR;
        if (specified(pin_subdir)) {
            if (!inDomainBitmask(pin_subdir, allowedDomainBitmask)) {
                ALOGE("map %s has invalid pin_subdir of %d (allowed bitmask 0x%llx)",
                      mapNames[i].c_str(), pin_subdir, allowedDomainBitmask);
                return -EINVAL;
            }
            ALOGI("map %s pin_subdir [%32s] -> %d -> '%s'", mapNames[i].c_str(), md[i].pin_subdir,
                  pin_subdir, lookupPinSubdir(pin_subdir));
        }

        // Format of pin location is /sys/fs/bpf/<pin_subdir|prefix>map_<filename>_<mapname>
        // except that maps shared across .o's have empty <filename>
        // Note: <filename> refers to the extension-less basename of the .o file.
        string mapPinLoc = string(BPF_FS_PATH) + lookupPinSubdir(pin_subdir, prefix) + "map_" +
                           (md[i].shared ? "" : fname) + "_" + mapNames[i];
        bool reuse = false;
        unique_fd fd;
        int saved_errno;

        if (access(mapPinLoc.c_str(), F_OK) == 0) {
            fd.reset(bpf_obj_get(mapPinLoc.c_str()));
            saved_errno = errno;
            ALOGD("bpf_create_map reusing map %s, ret: %d", mapNames[i].c_str(), fd.get());
            reuse = true;
        } else {
            struct bpf_create_map_attr attr = {
                .name = mapNames[i].c_str(),
                .map_type = type,
                .map_flags = md[i].map_flags,
                .key_size = md[i].key_size,
                .value_size = md[i].value_size,
                .max_entries = md[i].max_entries,
            };
            if (btfFd.has_value() && btfTypeIdMap.find(mapNames[i]) != btfTypeIdMap.end()) {
                attr.btf_fd = btfFd->get();
                attr.btf_key_type_id = btfTypeIdMap.at(mapNames[i]).first;
                attr.btf_value_type_id = btfTypeIdMap.at(mapNames[i]).second;
            }
            fd.reset(bcc_create_map_xattr(&attr, true));
            saved_errno = errno;
            ALOGD("bpf_create_map name %s, ret: %d", mapNames[i].c_str(), fd.get());
        }

        if (!fd.ok()) return -saved_errno;

        // When reusing a pinned map, we need to check the map type/sizes/etc match, but for
        // safety (since reuse code path is rare) run these checks even if we just created it.
        // We assume failure is due to pinned map mismatch, hence the 'NOT UNIQUE' return code.
        if (!mapMatchesExpectations(fd, mapNames[i], md[i], type)) return -ENOTUNIQ;

        if (!reuse) {
            if (specified(selinux_context)) {
                string createLoc = string(BPF_FS_PATH) + lookupPinSubdir(selinux_context) +
                                   "tmp_map_" + fname + "_" + mapNames[i];
                ret = bpf_obj_pin(fd, createLoc.c_str());
                if (ret) {
                    int err = errno;
                    ALOGE("create %s -> %d [%d:%s]", createLoc.c_str(), ret, err, strerror(err));
                    return -err;
                }
                ret = rename(createLoc.c_str(), mapPinLoc.c_str());
                if (ret) {
                    int err = errno;
                    ALOGE("rename %s %s -> %d [%d:%s]", createLoc.c_str(), mapPinLoc.c_str(), ret,
                          err, strerror(err));
                    return -err;
                }
            } else {
                ret = bpf_obj_pin(fd, mapPinLoc.c_str());
                if (ret) return -errno;
            }
            ret = chown(mapPinLoc.c_str(), (uid_t)md[i].uid, (gid_t)md[i].gid);
            if (ret) {
                int err = errno;
                ALOGE("chown(%s, %u, %u) = %d [%d:%s]", mapPinLoc.c_str(), md[i].uid, md[i].gid,
                      ret, err, strerror(err));
                return -err;
            }
            ret = chmod(mapPinLoc.c_str(), md[i].mode);
            if (ret) {
                int err = errno;
                ALOGE("chmod(%s, 0%o) = %d [%d:%s]", mapPinLoc.c_str(), md[i].mode, ret, err,
                      strerror(err));
                return -err;
            }
        }

        struct bpf_map_info map_info = {};
        __u32 map_info_len = sizeof(map_info);
        int rv = bpf_obj_get_info_by_fd(fd, &map_info, &map_info_len);
        if (rv) {
            ALOGE("bpf_obj_get_info_by_fd failed, ret: %d [%d]", rv, errno);
        } else {
            ALOGI("map %s id %d", mapPinLoc.c_str(), map_info.id);
        }

        mapFds.push_back(std::move(fd));
    }

    return ret;
}

/* For debugging, dump all instructions */
static void dumpIns(char* ins, int size) {
    for (int row = 0; row < size / 8; row++) {
        ALOGE("%d: ", row);
        for (int j = 0; j < 8; j++) {
            ALOGE("%3x ", ins[(row * 8) + j]);
        }
        ALOGE("\n");
    }
}

/* For debugging, dump all code sections from cs list */
static void dumpAllCs(vector<codeSection>& cs) {
    for (int i = 0; i < (int)cs.size(); i++) {
        ALOGE("Dumping cs %d, name %s", int(i), cs[i].name.c_str());
        dumpIns((char*)cs[i].data.data(), cs[i].data.size());
        ALOGE("-----------");
    }
}

static void applyRelo(void* insnsPtr, Elf64_Addr offset, int fd) {
    int insnIndex;
    struct bpf_insn *insn, *insns;

    insns = (struct bpf_insn*)(insnsPtr);

    insnIndex = offset / sizeof(struct bpf_insn);
    insn = &insns[insnIndex];

    ALOGD(
        "applying relo to instruction at byte offset: %d, \
	       insn offset %d , insn %lx\n",
        (int)offset, (int)insnIndex, *(unsigned long*)insn);

    if (insn->code != (BPF_LD | BPF_IMM | BPF_DW)) {
        ALOGE("Dumping all instructions till ins %d", insnIndex);
        ALOGE("invalid relo for insn %d: code 0x%x", insnIndex, insn->code);
        dumpIns((char*)insnsPtr, (insnIndex + 3) * 8);
        return;
    }

    insn->imm = fd;
    insn->src_reg = BPF_PSEUDO_MAP_FD;
}

static void applyMapRelo(ifstream& elfFile, vector<unique_fd> &mapFds, vector<codeSection>& cs) {
    vector<string> mapNames;

    int ret = getSectionSymNames(elfFile, "maps", mapNames);
    if (ret) return;

    for (int k = 0; k != (int)cs.size(); k++) {
        Elf64_Rel* rel = (Elf64_Rel*)(cs[k].rel_data.data());
        int n_rel = cs[k].rel_data.size() / sizeof(*rel);

        for (int i = 0; i < n_rel; i++) {
            int symIndex = ELF64_R_SYM(rel[i].r_info);
            string symName;

            ret = getSymNameByIdx(elfFile, symIndex, symName);
            if (ret) return;

            /* Find the map fd and apply relo */
            for (int j = 0; j < (int)mapNames.size(); j++) {
                if (!mapNames[j].compare(symName)) {
                    applyRelo(cs[k].data.data(), rel[i].r_offset, mapFds[j]);
                    break;
                }
            }
        }
    }
}

static int loadCodeSections(const char* elfPath, vector<codeSection>& cs, const string& license,
                            const char* prefix, const unsigned long long allowedDomainBitmask) {
    unsigned kvers = kernelVersion();
    int ret, fd;

    if (!kvers) return -1;

    string fname = pathToFilename(string(elfPath), true);

    for (int i = 0; i < (int)cs.size(); i++) {
        string name = cs[i].name;
        unsigned bpfMinVer = DEFAULT_BPFLOADER_MIN_VER;  // v0.0
        unsigned bpfMaxVer = DEFAULT_BPFLOADER_MAX_VER;  // v1.0
        domain selinux_context = domain::unspecified;
        domain pin_subdir = domain::unspecified;

        if (cs[i].prog_def.has_value()) {
            unsigned min_kver = cs[i].prog_def->min_kver;
            unsigned max_kver = cs[i].prog_def->max_kver;
            ALOGD("cs[%d].name:%s min_kver:%x .max_kver:%x (kvers:%x)", i, name.c_str(), min_kver,
                  max_kver, kvers);
            if (kvers < min_kver) continue;
            if (kvers >= max_kver) continue;

            bpfMinVer = cs[i].prog_def->bpfloader_min_ver;
            bpfMaxVer = cs[i].prog_def->bpfloader_max_ver;
            selinux_context = getDomainFromSelinuxContext(cs[i].prog_def->selinux_context);
            pin_subdir = getDomainFromPinSubdir(cs[i].prog_def->pin_subdir);
            // Note: make sure to only check for unrecognized *after* verifying bpfloader
            // version limits include this bpfloader's version.
        }

        ALOGD("cs[%d].name:%s requires bpfloader version [0x%05x,0x%05x)", i, name.c_str(),
              bpfMinVer, bpfMaxVer);
        if (BPFLOADER_VERSION < bpfMinVer) continue;
        if (BPFLOADER_VERSION >= bpfMaxVer) continue;
        if (unrecognized(pin_subdir)) return -ENOTDIR;

        if (specified(selinux_context)) {
            if (!inDomainBitmask(selinux_context, allowedDomainBitmask)) {
                ALOGE("prog %s has invalid selinux_context of %d (allowed bitmask 0x%llx)",
                      name.c_str(), selinux_context, allowedDomainBitmask);
                return -EINVAL;
            }
            ALOGI("prog %s selinux_context [%32s] -> %d -> '%s' (%s)", name.c_str(),
                  cs[i].prog_def->selinux_context, selinux_context,
                  lookupSelinuxContext(selinux_context), lookupPinSubdir(selinux_context));
        }

        if (specified(pin_subdir)) {
            if (!inDomainBitmask(pin_subdir, allowedDomainBitmask)) {
                ALOGE("prog %s has invalid pin_subdir of %d (allowed bitmask 0x%llx)", name.c_str(),
                      pin_subdir, allowedDomainBitmask);
                return -EINVAL;
            }
            ALOGI("prog %s pin_subdir [%32s] -> %d -> '%s'", name.c_str(),
                  cs[i].prog_def->pin_subdir, pin_subdir, lookupPinSubdir(pin_subdir));
        }

        // strip any potential $foo suffix
        // this can be used to provide duplicate programs
        // conditionally loaded based on running kernel version
        name = name.substr(0, name.find_last_of('$'));

        bool reuse = false;
        // Format of pin location is
        // /sys/fs/bpf/<prefix>prog_<filename>_<mapname>
        string progPinLoc = string(BPF_FS_PATH) + lookupPinSubdir(pin_subdir, prefix) + "prog_" +
                            fname + '_' + string(name);
        if (access(progPinLoc.c_str(), F_OK) == 0) {
            fd = retrieveProgram(progPinLoc.c_str());
            ALOGD("New bpf prog load reusing prog %s, ret: %d (%s)", progPinLoc.c_str(), fd,
                  (fd < 0 ? std::strerror(errno) : "no error"));
            reuse = true;
        } else {
            vector<char> log_buf(BPF_LOAD_LOG_SZ, 0);

            struct bpf_load_program_attr attr = {
                .prog_type = cs[i].type,
                .name = name.c_str(),
                .insns = (struct bpf_insn*)cs[i].data.data(),
                .license = license.c_str(),
                .log_level = 0,
                .expected_attach_type = cs[i].expected_attach_type,
            };

            fd = bcc_prog_load_xattr(&attr, cs[i].data.size(), log_buf.data(), log_buf.size(),
                    true);

            ALOGD("bpf_prog_load lib call for %s (%s) returned fd: %d (%s)", elfPath,
                  cs[i].name.c_str(), fd, (fd < 0 ? std::strerror(errno) : "no error"));

            if (fd < 0) {
                vector<string> lines = android::base::Split(log_buf.data(), "\n");

                ALOGW("bpf_prog_load - BEGIN log_buf contents:");
                for (const auto& line : lines) ALOGW("%s", line.c_str());
                ALOGW("bpf_prog_load - END log_buf contents.");

                if (cs[i].prog_def->optional) {
                    ALOGW("failed program is marked optional - continuing...");
                    continue;
                }
                ALOGE("non-optional program failed to load.");
            }
        }

        if (fd < 0) return fd;
        if (fd == 0) return -EINVAL;

        if (!reuse) {
            if (specified(selinux_context)) {
                string createLoc = string(BPF_FS_PATH) + lookupPinSubdir(selinux_context) +
                                   "tmp_prog_" + fname + '_' + string(name);
                ret = bpf_obj_pin(fd, createLoc.c_str());
                if (ret) {
                    int err = errno;
                    ALOGE("create %s -> %d [%d:%s]", createLoc.c_str(), ret, err, strerror(err));
                    return -err;
                }
                ret = rename(createLoc.c_str(), progPinLoc.c_str());
                if (ret) {
                    int err = errno;
                    ALOGE("rename %s %s -> %d [%d:%s]", createLoc.c_str(), progPinLoc.c_str(), ret,
                          err, strerror(err));
                    return -err;
                }
            } else {
                ret = bpf_obj_pin(fd, progPinLoc.c_str());
                if (ret) {
                    int err = errno;
                    ALOGE("create %s -> %d [%d:%s]", progPinLoc.c_str(), ret, err, strerror(err));
                    return -err;
                }
            }
            if (chmod(progPinLoc.c_str(), 0440)) {
                int err = errno;
                ALOGE("chmod %s 0440 -> [%d:%s]", progPinLoc.c_str(), err, strerror(err));
                return -err;
            }
            if (cs[i].prog_def.has_value()) {
                if (chown(progPinLoc.c_str(), (uid_t)cs[i].prog_def->uid,
                          (gid_t)cs[i].prog_def->gid)) {
                    int err = errno;
                    ALOGE("chown %s %d %d -> [%d:%s]", progPinLoc.c_str(), cs[i].prog_def->uid,
                          cs[i].prog_def->gid, err, strerror(err));
                    return -err;
                }
            }
        }

        struct bpf_prog_info prog_info = {};
        __u32 prog_info_len = sizeof(prog_info);
        int rv = bpf_obj_get_info_by_fd(fd, &prog_info, &prog_info_len);
        if (rv) {
            ALOGE("bpf_obj_get_info_by_fd failed, ret: %d [%d]", rv, errno);
        } else {
            ALOGI("prog %s id %d", progPinLoc.c_str(), prog_info.id);
        }

        cs[i].prog_fd.reset(fd);
    }

    return 0;
}

int loadProg(const char* elfPath, bool* isCritical, const char* prefix,
             const unsigned long long allowedDomainBitmask, const bpf_prog_type* allowed,
             size_t numAllowed) {
    vector<char> license;
    vector<char> critical;
    vector<codeSection> cs;
    vector<unique_fd> mapFds;
    int ret;

    if (!isCritical) return -1;
    *isCritical = false;

    ifstream elfFile(elfPath, ios::in | ios::binary);
    if (!elfFile.is_open()) return -1;

    ret = readSectionByName("critical", elfFile, critical);
    *isCritical = !ret;

    ret = readSectionByName("license", elfFile, license);
    if (ret) {
        ALOGE("Couldn't find license in %s", elfPath);
        return ret;
    } else {
        ALOGD("Loading %s%s ELF object %s with license %s",
              *isCritical ? "critical for " : "optional", *isCritical ? (char*)critical.data() : "",
              elfPath, (char*)license.data());
    }

    // the following default values are for bpfloader V0.0 format which does not include them
    unsigned int bpfLoaderMinVer =
            readSectionUint("bpfloader_min_ver", elfFile, DEFAULT_BPFLOADER_MIN_VER);
    unsigned int bpfLoaderMaxVer =
            readSectionUint("bpfloader_max_ver", elfFile, DEFAULT_BPFLOADER_MAX_VER);
    size_t sizeOfBpfMapDef =
            readSectionUint("size_of_bpf_map_def", elfFile, DEFAULT_SIZEOF_BPF_MAP_DEF);
    size_t sizeOfBpfProgDef =
            readSectionUint("size_of_bpf_prog_def", elfFile, DEFAULT_SIZEOF_BPF_PROG_DEF);

    // inclusive lower bound check
    if (BPFLOADER_VERSION < bpfLoaderMinVer) {
        ALOGI("BpfLoader version 0x%05x ignoring ELF object %s with min ver 0x%05x",
              BPFLOADER_VERSION, elfPath, bpfLoaderMinVer);
        return 0;
    }

    // exclusive upper bound check
    if (BPFLOADER_VERSION >= bpfLoaderMaxVer) {
        ALOGI("BpfLoader version 0x%05x ignoring ELF object %s with max ver 0x%05x",
              BPFLOADER_VERSION, elfPath, bpfLoaderMaxVer);
        return 0;
    }

    ALOGI("BpfLoader version 0x%05x processing ELF object %s with ver [0x%05x,0x%05x)",
          BPFLOADER_VERSION, elfPath, bpfLoaderMinVer, bpfLoaderMaxVer);

    if (sizeOfBpfMapDef < DEFAULT_SIZEOF_BPF_MAP_DEF) {
        ALOGE("sizeof(bpf_map_def) of %zu is too small (< %d)", sizeOfBpfMapDef,
              DEFAULT_SIZEOF_BPF_MAP_DEF);
        return -1;
    }

    if (sizeOfBpfProgDef < DEFAULT_SIZEOF_BPF_PROG_DEF) {
        ALOGE("sizeof(bpf_prog_def) of %zu is too small (< %d)", sizeOfBpfProgDef,
              DEFAULT_SIZEOF_BPF_PROG_DEF);
        return -1;
    }

    ret = readCodeSections(elfFile, cs, sizeOfBpfProgDef, allowed, numAllowed);
    if (ret) {
        ALOGE("Couldn't read all code sections in %s", elfPath);
        return ret;
    }

    /* Just for future debugging */
    if (0) dumpAllCs(cs);

    ret = createMaps(elfPath, elfFile, mapFds, prefix, allowedDomainBitmask, sizeOfBpfMapDef);
    if (ret) {
        ALOGE("Failed to create maps: (ret=%d) in %s", ret, elfPath);
        return ret;
    }

    for (int i = 0; i < (int)mapFds.size(); i++)
        ALOGD("map_fd found at %d is %d in %s", i, mapFds[i].get(), elfPath);

    applyMapRelo(elfFile, mapFds, cs);

    ret = loadCodeSections(elfPath, cs, string(license.data()), prefix, allowedDomainBitmask);
    if (ret) ALOGE("Failed to load programs, loadCodeSections ret=%d", ret);

    return ret;
}

}  // namespace bpf
}  // namespace android
