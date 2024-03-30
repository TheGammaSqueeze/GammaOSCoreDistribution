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

#include <errno.h>
#include <jni.h>
#include <sys/types.h>
#include <unistd.h>

#include <android/performance_hint.h>

static jstring toJString(JNIEnv *env, const char* c_str) {
    return env->NewStringUTF(c_str);
}

constexpr int64_t DEFAULT_TARGET_NS = 16666666L;

class SessionWrapper {
public:
    explicit SessionWrapper(APerformanceHintSession* session) : mSession(session) {}
    SessionWrapper(SessionWrapper&& other) : mSession(other.mSession) {
        other.mSession = nullptr;
    }
    ~SessionWrapper() {
        if (mSession) {
            APerformanceHint_closeSession(mSession);
        }
    }

    SessionWrapper(const SessionWrapper&) = delete;
    SessionWrapper& operator=(const SessionWrapper&) = delete;

    APerformanceHintSession* session() const { return mSession; }

private:
    APerformanceHintSession* mSession;
};

static SessionWrapper createSession(APerformanceHintManager* manager) {
    int32_t pid = getpid();
    return SessionWrapper(APerformanceHint_createSession(manager, &pid, 1u, DEFAULT_TARGET_NS));
}

static jstring nativeTestCreateHintSession(JNIEnv *env, jobject) {
    APerformanceHintManager* manager = APerformanceHint_getManager();
    if (!manager) return toJString(env, "null manager");
    SessionWrapper a = createSession(manager);
    SessionWrapper b = createSession(manager);
    if (a.session() == nullptr) {
        if (b.session() != nullptr) {
            return toJString(env, "b is not null");
        }
    } else if (b.session() == nullptr) {
        if (a.session() != nullptr) {
            return toJString(env, "a is not null");
        }
    } else if (a.session() == b.session()) {
        return toJString(env, "a and b matches");
    }
    return nullptr;
}

static jstring nativeTestGetPreferredUpdateRateNanos(JNIEnv *env, jobject) {
    APerformanceHintManager* manager = APerformanceHint_getManager();
    if (!manager) return toJString(env, "null manager");
    SessionWrapper wrapper = createSession(manager);
    if (wrapper.session() != nullptr) {
        bool positive = APerformanceHint_getPreferredUpdateRateNanos(manager) > 0;
        if (!positive)
          return toJString(env, "preferred rate is not positive");
    } else {
        if (APerformanceHint_getPreferredUpdateRateNanos(manager) != -1)
          return toJString(env, "preferred rate is not -1");
    }
    return nullptr;
}

static jstring nativeUpdateTargetWorkDuration(JNIEnv *env, jobject) {
    APerformanceHintManager* manager = APerformanceHint_getManager();
    if (!manager) return toJString(env, "null manager");
    SessionWrapper wrapper = createSession(manager);
    if (wrapper.session() == nullptr) return nullptr;

    int result = APerformanceHint_updateTargetWorkDuration(wrapper.session(), 100);
    if (result != 0) {
        return toJString(env, "updateTargetWorkDuration did not return 0");
    }
    return nullptr;
}

static jstring nativeUpdateTargetWorkDurationWithNegativeDuration(JNIEnv *env, jobject) {
    APerformanceHintManager* manager = APerformanceHint_getManager();
    if (!manager) return toJString(env, "null manager");
    SessionWrapper wrapper = createSession(manager);
    if (wrapper.session() == nullptr) return nullptr;

    int result = APerformanceHint_updateTargetWorkDuration(wrapper.session(), -1);
    if (result != EINVAL) {
        return toJString(env, "updateTargetWorkDuration did not return EINVAL");
    }
    return nullptr;
}

static jstring nativeReportActualWorkDuration(JNIEnv *env, jobject) {
    APerformanceHintManager* manager = APerformanceHint_getManager();
    if (!manager) return toJString(env, "null manager");
    SessionWrapper wrapper = createSession(manager);
    if (wrapper.session() == nullptr) return nullptr;

    int result = APerformanceHint_reportActualWorkDuration(wrapper.session(), 100);
    if (result != 0) {
        return toJString(env, "reportActualWorkDuration(100) did not return 0");
    }

    result = APerformanceHint_reportActualWorkDuration(wrapper.session(), 1);
    if (result != 0) {
        return toJString(env, "reportActualWorkDuration(1) did not return 0");
    }

    result = APerformanceHint_reportActualWorkDuration(wrapper.session(), 100);
    if (result != 0) {
        return toJString(env, "reportActualWorkDuration(100) did not return 0");
    }

    result = APerformanceHint_reportActualWorkDuration(wrapper.session(), 1000);
    if (result != 0) {
        return toJString(env, "reportActualWorkDuration(1000) did not return 0");
    }

    return nullptr;
}

static jstring nativeReportActualWorkDurationWithIllegalArgument(JNIEnv *env, jobject) {
    APerformanceHintManager* manager = APerformanceHint_getManager();
    if (!manager) return toJString(env, "null manager");
    SessionWrapper wrapper = createSession(manager);
    if (wrapper.session() == nullptr) return nullptr;

    int result = APerformanceHint_reportActualWorkDuration(wrapper.session(), -1);
    if (result != EINVAL) {
        return toJString(env, "reportActualWorkDuration did not return EINVAL");
    }
    return nullptr;
}


static JNINativeMethod gMethods[] = {
    {"nativeTestCreateHintSession", "()Ljava/lang/String;",
     (void*)nativeTestCreateHintSession},
    {"nativeTestGetPreferredUpdateRateNanos", "()Ljava/lang/String;",
     (void*)nativeTestGetPreferredUpdateRateNanos},
    {"nativeUpdateTargetWorkDuration", "()Ljava/lang/String;",
     (void*)nativeUpdateTargetWorkDuration},
    {"nativeUpdateTargetWorkDurationWithNegativeDuration", "()Ljava/lang/String;",
     (void*)nativeUpdateTargetWorkDurationWithNegativeDuration},
    {"nativeReportActualWorkDuration", "()Ljava/lang/String;",
     (void*)nativeReportActualWorkDuration},
    {"nativeReportActualWorkDurationWithIllegalArgument", "()Ljava/lang/String;",
     (void*)nativeReportActualWorkDurationWithIllegalArgument},
};

int register_android_os_cts_PerformanceHintManagerTest(JNIEnv *env) {
    jclass clazz = env->FindClass("android/os/cts/PerformanceHintManagerTest");

    return env->RegisterNatives(clazz, gMethods,
            sizeof(gMethods) / sizeof(JNINativeMethod));
}
