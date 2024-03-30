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

#ifndef ART_RUNTIME_JNI_JAVA_VM_EXT_INL_H_
#define ART_RUNTIME_JNI_JAVA_VM_EXT_INL_H_

#include "java_vm_ext.h"

#include "read_barrier_config.h"
#include "thread-inl.h"

namespace art {

inline bool JavaVMExt::MayAccessWeakGlobals(Thread* self) const {
  DCHECK(self != nullptr);
  return kUseReadBarrier
      ? self->GetWeakRefAccessEnabled()
      : allow_accessing_weak_globals_.load(std::memory_order_seq_cst);
}

}  // namespace art

#endif  // ART_RUNTIME_JNI_JAVA_VM_EXT_INL_H_
