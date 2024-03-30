/*
 * Copyright (C) 2012 The Android Open Source Project
 * Copyright 2018-2020 NXP.
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
#pragma once
#include <jni.h>
#include <pthread.h>
#include <semaphore.h>
#include <sys/queue.h>

#define JNI_NULL 0

struct uwb_jni_native_data {
  /* Our VM */
  JavaVM *vm;
  jobject manager;
  jclass mRangeDataClass;
  jclass rangingTwoWayMeasuresClass;
  jclass mRangeTdoaMeasuresClass;
  jclass periodicTxDataClass;
  jclass perRxDataClass;
  jclass uwbLoopBackDataClass;
  jclass multicastUpdateListDataClass;
};

jint JNI_OnLoad(JavaVM *jvm, void *reserved);

int uwb_jni_cache_jclass(JNIEnv *env, const char *clsname,
                         jclass *cached_jclass);

namespace android {
int register_com_android_uwb_dhimpl_UwbNativeManager(JNIEnv *env);
int register_com_android_uwb_dhimpl_NxpUwbNativeManager(JNIEnv *env);
int register_com_android_uwb_dhimpl_UwbRfTestNativeManager(JNIEnv *env);
} // namespace android