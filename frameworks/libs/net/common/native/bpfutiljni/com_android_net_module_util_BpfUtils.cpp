/*
 * Copyright (C) 2022 The Android Open Source Project
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

#include <android-base/unique_fd.h>
#include <errno.h>
#include <fcntl.h>
#include <jni.h>
#include <nativehelper/JNIHelp.h>
#include <nativehelper/ScopedLocalRef.h>
#include <nativehelper/scoped_utf_chars.h>
#include <string.h>
#include <sys/socket.h>

#include "BpfSyscallWrappers.h"

namespace android {

using base::unique_fd;

// If attach fails throw error and return false.
static jboolean com_android_net_module_util_BpfUtil_attachProgramToCgroup(JNIEnv *env,
        jobject clazz, jint type, jstring bpfProgPath, jstring cgroupPath, jint flags) {

    ScopedUtfChars dirPath(env, cgroupPath);
    unique_fd cg_fd(open(dirPath.c_str(), O_DIRECTORY | O_RDONLY | O_CLOEXEC));
    if (cg_fd == -1) {
        jniThrowExceptionFmt(env, "java/io/IOException",
                             "Failed to open the cgroup directory %s: %s",
                             dirPath.c_str(), strerror(errno));
        return false;
    }

    ScopedUtfChars bpfProg(env, bpfProgPath);
    unique_fd bpf_fd(bpf::retrieveProgram(bpfProg.c_str()));
    if (bpf_fd == -1) {
        jniThrowExceptionFmt(env, "java/io/IOException",
                             "Failed to retrieve bpf program from %s: %s",
                             bpfProg.c_str(), strerror(errno));
        return false;
    }
    if (bpf::attachProgram((bpf_attach_type) type, bpf_fd, cg_fd, flags)) {
        jniThrowExceptionFmt(env, "java/io/IOException",
                             "Failed to attach bpf program %s to %s: %s",
                             bpfProg.c_str(), dirPath.c_str(), strerror(errno));
        return false;
    }
    return true;
}

// If detach fails throw error and return false.
static jboolean com_android_net_module_util_BpfUtil_detachProgramFromCgroup(JNIEnv *env,
        jobject clazz, jint type, jstring cgroupPath) {

    ScopedUtfChars dirPath(env, cgroupPath);
    unique_fd cg_fd(open(dirPath.c_str(), O_DIRECTORY | O_RDONLY | O_CLOEXEC));
    if (cg_fd == -1) {
        jniThrowExceptionFmt(env, "java/io/IOException",
                             "Failed to open the cgroup directory %s: %s",
                             dirPath.c_str(), strerror(errno));
        return false;
    }

    if (bpf::detachProgram((bpf_attach_type) type, cg_fd)) {
        jniThrowExceptionFmt(env, "Failed to detach bpf program from %s: %s",
                dirPath.c_str(), strerror(errno));
        return false;
    }
    return true;
}

// If detach single program fails throw error and return false.
static jboolean com_android_net_module_util_BpfUtil_detachSingleProgramFromCgroup(JNIEnv *env,
        jobject clazz, jint type, jstring bpfProgPath, jstring cgroupPath) {

    ScopedUtfChars dirPath(env, cgroupPath);
    unique_fd cg_fd(open(dirPath.c_str(), O_DIRECTORY | O_RDONLY | O_CLOEXEC));
    if (cg_fd == -1) {
        jniThrowExceptionFmt(env, "java/io/IOException",
                             "Failed to open the cgroup directory %s: %s",
                             dirPath.c_str(), strerror(errno));
        return false;
    }

    ScopedUtfChars bpfProg(env, bpfProgPath);
    unique_fd bpf_fd(bpf::retrieveProgram(bpfProg.c_str()));
    if (bpf_fd == -1) {
        jniThrowExceptionFmt(env, "java/io/IOException",
                             "Failed to retrieve bpf program from %s: %s",
                             bpfProg.c_str(), strerror(errno));
        return false;
    }
    if (bpf::detachSingleProgram((bpf_attach_type) type, bpf_fd, cg_fd)) {
        jniThrowExceptionFmt(env, "Failed to detach bpf program %s from %s: %s",
                bpfProg.c_str(), dirPath.c_str(), strerror(errno));
        return false;
    }
    return true;
}

/*
 * JNI registration.
 */
static const JNINativeMethod gMethods[] = {
    /* name, signature, funcPtr */
    { "native_attachProgramToCgroup", "(ILjava/lang/String;Ljava/lang/String;I)Z",
        (void*) com_android_net_module_util_BpfUtil_attachProgramToCgroup },
    { "native_detachProgramFromCgroup", "(ILjava/lang/String;)Z",
        (void*) com_android_net_module_util_BpfUtil_detachProgramFromCgroup },
    { "native_detachSingleProgramFromCgroup", "(ILjava/lang/String;Ljava/lang/String;)Z",
        (void*) com_android_net_module_util_BpfUtil_detachSingleProgramFromCgroup },
};

int register_com_android_net_module_util_BpfUtils(JNIEnv* env, char const* class_name) {
    return jniRegisterNativeMethods(env,
            class_name,
            gMethods, NELEM(gMethods));
}

}; // namespace android
