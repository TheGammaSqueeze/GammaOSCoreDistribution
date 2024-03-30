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

#define LOG_TAG "TrafficControllerJni"

#include "TrafficController.h"

#include <bpf_shared.h>
#include <jni.h>
#include <log/log.h>
#include <nativehelper/JNIHelp.h>
#include <nativehelper/ScopedUtfChars.h>
#include <nativehelper/ScopedPrimitiveArray.h>
#include <netjniutils/netjniutils.h>
#include <net/if.h>
#include <vector>


using android::net::TrafficController;
using android::netdutils::Status;

using UidOwnerMatchType::PENALTY_BOX_MATCH;
using UidOwnerMatchType::HAPPY_BOX_MATCH;

static android::net::TrafficController mTc;

namespace android {

static void native_init(JNIEnv* env, jobject clazz) {
  Status status = mTc.start();
   if (!isOk(status)) {
    ALOGE("%s failed, error code = %d", __func__, status.code());
  }
}

static jint native_addNaughtyApp(JNIEnv* env, jobject clazz, jint uid) {
  const uint32_t appUids = static_cast<uint32_t>(abs(uid));
  Status status = mTc.updateUidOwnerMap(appUids, PENALTY_BOX_MATCH,
      TrafficController::IptOp::IptOpInsert);
  if (!isOk(status)) {
    ALOGE("%s failed, error code = %d", __func__, status.code());
  }
  return (jint)status.code();
}

static jint native_removeNaughtyApp(JNIEnv* env, jobject clazz, jint uid) {
  const uint32_t appUids = static_cast<uint32_t>(abs(uid));
  Status status = mTc.updateUidOwnerMap(appUids, PENALTY_BOX_MATCH,
      TrafficController::IptOp::IptOpDelete);
  if (!isOk(status)) {
    ALOGE("%s failed, error code = %d", __func__, status.code());
  }
  return (jint)status.code();
}

static jint native_addNiceApp(JNIEnv* env, jobject clazz, jint uid) {
  const uint32_t appUids = static_cast<uint32_t>(abs(uid));
  Status status = mTc.updateUidOwnerMap(appUids, HAPPY_BOX_MATCH,
      TrafficController::IptOp::IptOpInsert);
  if (!isOk(status)) {
    ALOGE("%s failed, error code = %d", __func__, status.code());
  }
  return (jint)status.code();
}

static jint native_removeNiceApp(JNIEnv* env, jobject clazz, jint uid) {
  const uint32_t appUids = static_cast<uint32_t>(abs(uid));
  Status status = mTc.updateUidOwnerMap(appUids, HAPPY_BOX_MATCH,
      TrafficController::IptOp::IptOpDelete);
  if (!isOk(status)) {
    ALOGD("%s failed, error code = %d", __func__, status.code());
  }
  return (jint)status.code();
}

static jint native_setChildChain(JNIEnv* env, jobject clazz, jint childChain, jboolean enable) {
  auto chain = static_cast<ChildChain>(childChain);
  int res = mTc.toggleUidOwnerMap(chain, enable);
  if (res) {
    ALOGE("%s failed, error code = %d", __func__, res);
  }
  return (jint)res;
}

static jint native_replaceUidChain(JNIEnv* env, jobject clazz, jstring name, jboolean isAllowlist,
                                jintArray jUids) {
    const ScopedUtfChars chainNameUtf8(env, name);
    if (chainNameUtf8.c_str() == nullptr) {
        return -EINVAL;
    }
    const std::string chainName(chainNameUtf8.c_str());

    ScopedIntArrayRO uids(env, jUids);
    if (uids.get() == nullptr) {
        return -EINVAL;
    }

    size_t size = uids.size();
    static_assert(sizeof(*(uids.get())) == sizeof(int32_t));
    std::vector<int32_t> data ((int32_t *)&uids[0], (int32_t*)&uids[size]);
    int res = mTc.replaceUidOwnerMap(chainName, isAllowlist, data);
    if (res) {
      ALOGE("%s failed, error code = %d", __func__, res);
    }
    return (jint)res;
}

static jint native_setUidRule(JNIEnv* env, jobject clazz, jint childChain, jint uid,
                          jint firewallRule) {
    auto chain = static_cast<ChildChain>(childChain);
    auto rule = static_cast<FirewallRule>(firewallRule);
    FirewallType fType = mTc.getFirewallType(chain);

    int res = mTc.changeUidOwnerRule(chain, uid, rule, fType);
    if (res) {
      ALOGE("%s failed, error code = %d", __func__, res);
    }
    return (jint)res;
}

static jint native_addUidInterfaceRules(JNIEnv* env, jobject clazz, jstring ifName,
                                    jintArray jUids) {
    // Null ifName is a wildcard to allow apps to receive packets on all interfaces and ifIndex is
    // set to 0.
    int ifIndex;
    if (ifName != nullptr) {
        const ScopedUtfChars ifNameUtf8(env, ifName);
        const std::string interfaceName(ifNameUtf8.c_str());
        ifIndex = if_nametoindex(interfaceName.c_str());
    } else {
        ifIndex = 0;
    }

    ScopedIntArrayRO uids(env, jUids);
    if (uids.get() == nullptr) {
        return -EINVAL;
    }

    size_t size = uids.size();
    static_assert(sizeof(*(uids.get())) == sizeof(int32_t));
    std::vector<int32_t> data ((int32_t *)&uids[0], (int32_t*)&uids[size]);
    Status status = mTc.addUidInterfaceRules(ifIndex, data);
    if (!isOk(status)) {
        ALOGE("%s failed, error code = %d", __func__, status.code());
    }
    return (jint)status.code();
}

static jint native_removeUidInterfaceRules(JNIEnv* env, jobject clazz, jintArray jUids) {
    ScopedIntArrayRO uids(env, jUids);
    if (uids.get() == nullptr) {
        return -EINVAL;
    }

    size_t size = uids.size();
    static_assert(sizeof(*(uids.get())) == sizeof(int32_t));
    std::vector<int32_t> data ((int32_t *)&uids[0], (int32_t*)&uids[size]);
    Status status = mTc.removeUidInterfaceRules(data);
    if (!isOk(status)) {
        ALOGE("%s failed, error code = %d", __func__, status.code());
    }
    return (jint)status.code();
}

static jint native_swapActiveStatsMap(JNIEnv* env, jobject clazz) {
    Status status = mTc.swapActiveStatsMap();
    if (!isOk(status)) {
        ALOGD("%s failed, error code = %d", __func__, status.code());
    }
    return (jint)status.code();
}

static void native_setPermissionForUids(JNIEnv* env, jobject clazz, jint permission,
                                      jintArray jUids) {
    ScopedIntArrayRO uids(env, jUids);
    if (uids.get() == nullptr) return;

    size_t size = uids.size();
    static_assert(sizeof(*(uids.get())) == sizeof(uid_t));
    std::vector<uid_t> data ((uid_t *)&uids[0], (uid_t*)&uids[size]);
    mTc.setPermissionForUids(permission, data);
}

static void native_dump(JNIEnv* env, jobject clazz, jobject javaFd, jboolean verbose) {
    int fd = netjniutils::GetNativeFileDescriptor(env, javaFd);
    if (fd < 0) {
        jniThrowExceptionFmt(env, "java/io/IOException", "Invalid file descriptor");
        return;
    }
    mTc.dump(fd, verbose);
}

/*
 * JNI registration.
 */
// clang-format off
static const JNINativeMethod gMethods[] = {
    /* name, signature, funcPtr */
    {"native_init", "()V",
    (void*)native_init},
    {"native_addNaughtyApp", "(I)I",
    (void*)native_addNaughtyApp},
    {"native_removeNaughtyApp", "(I)I",
    (void*)native_removeNaughtyApp},
    {"native_addNiceApp", "(I)I",
    (void*)native_addNiceApp},
    {"native_removeNiceApp", "(I)I",
    (void*)native_removeNiceApp},
    {"native_setChildChain", "(IZ)I",
    (void*)native_setChildChain},
    {"native_replaceUidChain", "(Ljava/lang/String;Z[I)I",
    (void*)native_replaceUidChain},
    {"native_setUidRule", "(III)I",
    (void*)native_setUidRule},
    {"native_addUidInterfaceRules", "(Ljava/lang/String;[I)I",
    (void*)native_addUidInterfaceRules},
    {"native_removeUidInterfaceRules", "([I)I",
    (void*)native_removeUidInterfaceRules},
    {"native_swapActiveStatsMap", "()I",
    (void*)native_swapActiveStatsMap},
    {"native_setPermissionForUids", "(I[I)V",
    (void*)native_setPermissionForUids},
    {"native_dump", "(Ljava/io/FileDescriptor;Z)V",
    (void*)native_dump},
};
// clang-format on

int register_com_android_server_BpfNetMaps(JNIEnv* env) {
    return jniRegisterNativeMethods(env,
    "com/android/server/BpfNetMaps",
    gMethods, NELEM(gMethods));
}

}; // namespace android
