/*****************************************************************
//Copyright 2017 Google Inc. All Rights Reserved.

****************************************************************/

#ifndef _UWB_JNI_SCOPEDJNIENV_H_
#define _UWB_JNI_SCOPEDJNIENV_H_

#include <jni.h>

class ScopedJniEnv {
public:
  ScopedJniEnv(JavaVM *jvm) : jvm_(jvm), env_(NULL), is_attached_(false) {
    // We don't make any assumptions about the state of the current thread, and
    // we want to leave it in the state we received it with respect to the
    // JavaVm. So we only attach and detach when needed, and we always delete
    // local references.
    jint error =
        jvm_->GetEnv(reinterpret_cast<void **>(&env_), JNI_VERSION_1_2);
    if (error != JNI_OK) {
      jvm_->AttachCurrentThread(&env_, NULL);
      is_attached_ = true;
    }
    if (env_ != NULL) {
      env_->PushLocalFrame(0);
    }
  }

  virtual ~ScopedJniEnv() {
    if (env_ != NULL) {
      env_->PopLocalFrame(NULL);
      if (is_attached_) {
        // A return value indicating possible errors is available here.
        (void)jvm_->DetachCurrentThread();
      }
    }
  }

  operator JNIEnv *() { return env_; }

  JNIEnv *operator->() { return env_; }

  bool isValid() const { return env_ != NULL; }

private:
#if __cplusplus >= 201103L

  ScopedJniEnv(const ScopedJniEnv &) = delete;

  void operator=(const ScopedJniEnv &) = delete;

#else
  ScopedJniEnv(const ScopedJniEnv &);
  void operator=(const ScopedJniEnv &);
#endif

  JavaVM *jvm_;
  JNIEnv *env_;
  bool is_attached_;
};

#endif
