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

#include "UwbJniUtil.h"

#include <nativehelper/JNIHelp.h>
#include <nativehelper/ScopedLocalRef.h>

#include "JniLog.h"
#include "UwbJniInternal.h"

/*******************************************************************************
**
** Function:        JNI_OnLoad
**
** Description:     Register all JNI functions with Java Virtual Machine.
**                  jvm: Java Virtual Machine.
**                  reserved: Not used.
**
** Returns:         JNI version.
**
*******************************************************************************/
jint JNI_OnLoad(JavaVM *jvm, void *) {
  JNI_TRACE_I("%s: enter", __func__);
  JNIEnv *env = NULL;

  JNI_TRACE_I("UWB Service: loading uci JNI");

  // Check JNI version
  if (jvm->GetEnv((void **)&env, JNI_VERSION_1_6))
    return JNI_ERR;

  if (android::register_com_android_uwb_dhimpl_UwbNativeManager(env) == -1)
    return JNI_ERR;
  /*if (android::register_com_android_uwb_dhimpl_UwbRfTestNativeManager(env) ==
      -1)
    return JNI_ERR;*/

  JNI_TRACE_I("%s: exit", __func__);
  return JNI_VERSION_1_6;
}

/*******************************************************************************
**
** Function:        uwb_jni_cache_jclass
**
** Description:     This API invoked during JNI initialization to register
**                  Required class and corresponding Global refference will be
**                  used during sending Ranging ntf to upper layer.
**
** Returns:         Status code.
**
*******************************************************************************/
int uwb_jni_cache_jclass(JNIEnv *env, const char *className,
                         jclass *cachedJclass) {
  jclass cls = env->FindClass(className);
  if (cls == NULL) {
    JNI_TRACE_E("%s: find class error", __func__);
    return -1;
  }

  *cachedJclass = static_cast<jclass>(env->NewGlobalRef(cls));
  if (*cachedJclass == NULL) {
    JNI_TRACE_E("%s: global ref error", __func__);
    return -1;
  }
  return 0;
}