/*
 * Copyright (C) 2016 The Android Open Source Project
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

#include "method_type-inl.h"

#include "class-alloc-inl.h"
#include "class_root-inl.h"
#include "method_handles.h"
#include "obj_ptr-inl.h"
#include "object_array-alloc-inl.h"
#include "object_array-inl.h"

namespace art {
namespace mirror {

namespace {

ObjPtr<ObjectArray<Class>> AllocatePTypesArray(Thread* self, int count)
    REQUIRES_SHARED(Locks::mutator_lock_) {
  ObjPtr<Class> class_array_type = GetClassRoot<mirror::ObjectArray<mirror::Class>>();
  return ObjectArray<Class>::Alloc(self, class_array_type, count);
}

}  // namespace

ObjPtr<MethodType> MethodType::Create(Thread* const self,
                                      Handle<Class> return_type,
                                      Handle<ObjectArray<Class>> parameter_types) {
  StackHandleScope<1> hs(self);
  Handle<MethodType> mt(
      hs.NewHandle(ObjPtr<MethodType>::DownCast(GetClassRoot<MethodType>()->AllocObject(self))));

  if (mt == nullptr) {
    self->AssertPendingOOMException();
    return nullptr;
  }

  // We're initializing a newly allocated object, so we do not need to record that under
  // a transaction. If the transaction is aborted, the whole object shall be unreachable.
  mt->SetFieldObject</*kTransactionActive=*/ false, /*kCheckTransaction=*/ false>(
      FormOffset(), nullptr);
  mt->SetFieldObject</*kTransactionActive=*/ false, /*kCheckTransaction=*/ false>(
      MethodDescriptorOffset(), nullptr);
  mt->SetFieldObject</*kTransactionActive=*/ false, /*kCheckTransaction=*/ false>(
      RTypeOffset(), return_type.Get());
  mt->SetFieldObject</*kTransactionActive=*/ false, /*kCheckTransaction=*/ false>(
      PTypesOffset(), parameter_types.Get());
  mt->SetFieldObject</*kTransactionActive=*/ false, /*kCheckTransaction=*/ false>(
      WrapAltOffset(), nullptr);

  return mt.Get();
}

ObjPtr<MethodType> MethodType::CloneWithoutLeadingParameter(Thread* const self,
                                                            ObjPtr<MethodType> method_type) {
  StackHandleScope<3> hs(self);
  Handle<ObjectArray<Class>> src_ptypes = hs.NewHandle(method_type->GetPTypes());
  Handle<Class> dst_rtype = hs.NewHandle(method_type->GetRType());
  const int32_t dst_ptypes_count = method_type->GetNumberOfPTypes() - 1;
  Handle<ObjectArray<Class>> dst_ptypes = hs.NewHandle(AllocatePTypesArray(self, dst_ptypes_count));
  if (dst_ptypes.IsNull()) {
    return nullptr;
  }
  for (int32_t i = 0; i < dst_ptypes_count; ++i) {
    dst_ptypes->Set(i, src_ptypes->Get(i + 1));
  }
  return Create(self, dst_rtype, dst_ptypes);
}

ObjPtr<MethodType> MethodType::CollectTrailingArguments(Thread* self,
                                                        ObjPtr<MethodType> method_type,
                                                        ObjPtr<Class> collector_array_class,
                                                        int32_t start_index) {
  int32_t ptypes_length = method_type->GetNumberOfPTypes();
  if (start_index > ptypes_length) {
    return method_type;
  }

  StackHandleScope<4> hs(self);
  Handle<Class> collector_class = hs.NewHandle(collector_array_class);
  Handle<Class> dst_rtype = hs.NewHandle(method_type->GetRType());
  Handle<ObjectArray<Class>> src_ptypes = hs.NewHandle(method_type->GetPTypes());
  Handle<ObjectArray<Class>> dst_ptypes = hs.NewHandle(AllocatePTypesArray(self, start_index + 1));
  if (dst_ptypes.IsNull()) {
    return nullptr;
  }
  for (int32_t i = 0; i < start_index; ++i) {
    dst_ptypes->Set(i, src_ptypes->Get(i));
  }
  dst_ptypes->Set(start_index, collector_class.Get());
  return Create(self, dst_rtype, dst_ptypes);
}

size_t MethodType::NumberOfVRegs() {
  const ObjPtr<ObjectArray<Class>> p_types = GetPTypes();
  const int32_t p_types_length = p_types->GetLength();

  // Initialize |num_vregs| with number of parameters and only increment it for
  // types requiring a second vreg.
  size_t num_vregs = static_cast<size_t>(p_types_length);
  for (int32_t i = 0; i < p_types_length; ++i) {
    ObjPtr<Class> klass = p_types->GetWithoutChecks(i);
    if (klass->IsPrimitiveLong() || klass->IsPrimitiveDouble()) {
      ++num_vregs;
    }
  }
  return num_vregs;
}

bool MethodType::IsExactMatch(ObjPtr<MethodType> target) {
  const ObjPtr<ObjectArray<Class>> p_types = GetPTypes();
  const int32_t params_length = p_types->GetLength();

  const ObjPtr<ObjectArray<Class>> target_p_types = target->GetPTypes();
  if (params_length != target_p_types->GetLength()) {
    return false;
  }
  for (int32_t i = 0; i < params_length; ++i) {
    if (p_types->GetWithoutChecks(i) != target_p_types->GetWithoutChecks(i)) {
      return false;
    }
  }
  return GetRType() == target->GetRType();
}

bool MethodType::IsConvertible(ObjPtr<MethodType> target) {
  const ObjPtr<ObjectArray<Class>> p_types = GetPTypes();
  const int32_t params_length = p_types->GetLength();

  const ObjPtr<ObjectArray<Class>> target_p_types = target->GetPTypes();
  if (params_length != target_p_types->GetLength()) {
    return false;
  }

  // Perform return check before invoking method handle otherwise side
  // effects from the invocation may be observable before
  // WrongMethodTypeException is raised.
  if (!IsReturnTypeConvertible(target->GetRType(), GetRType())) {
    return false;
  }

  for (int32_t i = 0; i < params_length; ++i) {
    if (!IsParameterTypeConvertible(p_types->GetWithoutChecks(i),
                                    target_p_types->GetWithoutChecks(i))) {
      return false;
    }
  }
  return true;
}

static bool IsParameterInPlaceConvertible(ObjPtr<Class> from, ObjPtr<Class> to)
    REQUIRES_SHARED(Locks::mutator_lock_) {
  if (from == to) {
    return true;
  }

  if (from->IsPrimitive() != to->IsPrimitive()) {
    return false;  // No in-place conversion from place conversion for box/unboxing.
  }

  if (from->IsPrimitive()) {
    // `from` and `to` are both primitives. The supported in-place conversions use a 32-bit
    // interpreter representation and are a subset of permitted conversions for MethodHandles.
    // Conversions are documented in JLS 11 S5.1.2 "Widening Primitive Conversion".
    Primitive::Type src = from->GetPrimitiveType();
    Primitive::Type dst = to->GetPrimitiveType();
    switch (src) {
      case Primitive::Type::kPrimByte:
        return dst == Primitive::Type::kPrimShort || dst == Primitive::Type::kPrimInt;
      case Primitive::Type::kPrimChar:
        FALLTHROUGH_INTENDED;
      case Primitive::Type::kPrimShort:
        return dst == Primitive::Type::kPrimInt;
      default:
        return false;
    }
  }

  // `from` and `to` are both references, apply an assignability check.
  return to->IsAssignableFrom(from);
}

bool MethodType::IsInPlaceConvertible(ObjPtr<MethodType> target) {
  const ObjPtr<ObjectArray<Class>> ptypes = GetPTypes();
  const ObjPtr<ObjectArray<Class>> target_ptypes = target->GetPTypes();
  const int32_t ptypes_length = ptypes->GetLength();
  if (ptypes_length != target_ptypes->GetLength()) {
    return false;
  }

  for (int32_t i = 0; i < ptypes_length; ++i) {
    if (!IsParameterInPlaceConvertible(ptypes->GetWithoutChecks(i),
                                       target_ptypes->GetWithoutChecks(i))) {
      return false;
    }
  }

  return GetRType()->IsPrimitiveVoid() ||
         IsParameterInPlaceConvertible(target->GetRType(), GetRType());
}

std::string MethodType::PrettyDescriptor() {
  std::ostringstream ss;
  ss << "(";

  const ObjPtr<ObjectArray<Class>> p_types = GetPTypes();
  const int32_t params_length = p_types->GetLength();
  for (int32_t i = 0; i < params_length; ++i) {
    ss << p_types->GetWithoutChecks(i)->PrettyDescriptor();
    if (i != (params_length - 1)) {
      ss << ", ";
    }
  }

  ss << ")";
  ss << GetRType()->PrettyDescriptor();

  return ss.str();
}

}  // namespace mirror
}  // namespace art
