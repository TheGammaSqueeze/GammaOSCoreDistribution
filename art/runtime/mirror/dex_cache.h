/*
 * Copyright (C) 2011 The Android Open Source Project
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

#ifndef ART_RUNTIME_MIRROR_DEX_CACHE_H_
#define ART_RUNTIME_MIRROR_DEX_CACHE_H_

#include "array.h"
#include "base/array_ref.h"
#include "base/bit_utils.h"
#include "base/locks.h"
#include "dex/dex_file_types.h"
#include "gc_root.h"  // Note: must not use -inl here to avoid circular dependency.
#include "object.h"
#include "object_array.h"

namespace art {

namespace linker {
class ImageWriter;
}  // namespace linker

class ArtField;
class ArtMethod;
struct DexCacheOffsets;
class DexFile;
union JValue;
class LinearAlloc;
class ReflectiveValueVisitor;
class Thread;

namespace mirror {

class CallSite;
class Class;
class ClassLoader;
class MethodType;
class String;

template <typename T> struct PACKED(8) DexCachePair {
  GcRoot<T> object;
  uint32_t index;
  // The array is initially [ {0,0}, {0,0}, {0,0} ... ]
  // We maintain the invariant that once a dex cache entry is populated,
  // the pointer is always non-0
  // Any given entry would thus be:
  // {non-0, non-0} OR {0,0}
  //
  // It's generally sufficiently enough then to check if the
  // lookup index matches the stored index (for a >0 lookup index)
  // because if it's true the pointer is also non-null.
  //
  // For the 0th entry which is a special case, the value is either
  // {0,0} (initial state) or {non-0, 0} which indicates
  // that a valid object is stored at that index for a dex section id of 0.
  //
  // As an optimization, we want to avoid branching on the object pointer since
  // it's always non-null if the id branch succeeds (except for the 0th id).
  // Set the initial state for the 0th entry to be {0,1} which is guaranteed to fail
  // the lookup id == stored id branch.
  DexCachePair(ObjPtr<T> object, uint32_t index);
  DexCachePair() : index(0) {}
  DexCachePair(const DexCachePair<T>&) = default;
  DexCachePair& operator=(const DexCachePair<T>&) = default;

  static void Initialize(std::atomic<DexCachePair<T>>* dex_cache);

  static uint32_t InvalidIndexForSlot(uint32_t slot) {
    // Since the cache size is a power of two, 0 will always map to slot 0.
    // Use 1 for slot 0 and 0 for all other slots.
    return (slot == 0) ? 1u : 0u;
  }

  T* GetObjectForIndex(uint32_t idx) REQUIRES_SHARED(Locks::mutator_lock_);
};

template <typename T> struct PACKED(2 * __SIZEOF_POINTER__) NativeDexCachePair {
  T* object;
  size_t index;
  // This is similar to DexCachePair except that we're storing a native pointer
  // instead of a GC root. See DexCachePair for the details.
  NativeDexCachePair(T* object, uint32_t index)
      : object(object),
        index(index) {}
  NativeDexCachePair() : object(nullptr), index(0u) { }
  NativeDexCachePair(const NativeDexCachePair<T>&) = default;
  NativeDexCachePair& operator=(const NativeDexCachePair<T>&) = default;

  static void Initialize(std::atomic<NativeDexCachePair<T>>* dex_cache);

  static uint32_t InvalidIndexForSlot(uint32_t slot) {
    // Since the cache size is a power of two, 0 will always map to slot 0.
    // Use 1 for slot 0 and 0 for all other slots.
    return (slot == 0) ? 1u : 0u;
  }

  T* GetObjectForIndex(uint32_t idx) REQUIRES_SHARED(Locks::mutator_lock_) {
    if (idx != index) {
      return nullptr;
    }
    DCHECK(object != nullptr);
    return object;
  }
};

using TypeDexCachePair = DexCachePair<Class>;
using TypeDexCacheType = std::atomic<TypeDexCachePair>;

using StringDexCachePair = DexCachePair<String>;
using StringDexCacheType = std::atomic<StringDexCachePair>;

using FieldDexCachePair = NativeDexCachePair<ArtField>;
using FieldDexCacheType = std::atomic<FieldDexCachePair>;

using MethodDexCachePair = NativeDexCachePair<ArtMethod>;
using MethodDexCacheType = std::atomic<MethodDexCachePair>;

using MethodTypeDexCachePair = DexCachePair<MethodType>;
using MethodTypeDexCacheType = std::atomic<MethodTypeDexCachePair>;

// C++ mirror of java.lang.DexCache.
class MANAGED DexCache final : public Object {
 public:
  MIRROR_CLASS("Ljava/lang/DexCache;");

  // Size of java.lang.DexCache.class.
  static uint32_t ClassSize(PointerSize pointer_size);

  // Size of type dex cache. Needs to be a power of 2 for entrypoint assumptions to hold.
  static constexpr size_t kDexCacheTypeCacheSize = 1024;
  static_assert(IsPowerOfTwo(kDexCacheTypeCacheSize),
                "Type dex cache size is not a power of 2.");

  // Size of string dex cache. Needs to be a power of 2 for entrypoint assumptions to hold.
  static constexpr size_t kDexCacheStringCacheSize = 1024;
  static_assert(IsPowerOfTwo(kDexCacheStringCacheSize),
                "String dex cache size is not a power of 2.");

  // Size of field dex cache. Needs to be a power of 2 for entrypoint assumptions to hold.
  static constexpr size_t kDexCacheFieldCacheSize = 1024;
  static_assert(IsPowerOfTwo(kDexCacheFieldCacheSize),
                "Field dex cache size is not a power of 2.");

  // Size of method dex cache. Needs to be a power of 2 for entrypoint assumptions to hold.
  static constexpr size_t kDexCacheMethodCacheSize = 1024;
  static_assert(IsPowerOfTwo(kDexCacheMethodCacheSize),
                "Method dex cache size is not a power of 2.");

  // Size of method type dex cache. Needs to be a power of 2 for entrypoint assumptions
  // to hold.
  static constexpr size_t kDexCacheMethodTypeCacheSize = 1024;
  static_assert(IsPowerOfTwo(kDexCacheMethodTypeCacheSize),
                "MethodType dex cache size is not a power of 2.");

  static constexpr size_t StaticTypeSize() {
    return kDexCacheTypeCacheSize;
  }

  static constexpr size_t StaticStringSize() {
    return kDexCacheStringCacheSize;
  }

  static constexpr size_t StaticArtFieldSize() {
    return kDexCacheFieldCacheSize;
  }

  static constexpr size_t StaticMethodSize() {
    return kDexCacheMethodCacheSize;
  }

  static constexpr size_t StaticMethodTypeSize() {
    return kDexCacheMethodTypeCacheSize;
  }

  // Size of an instance of java.lang.DexCache not including referenced values.
  static constexpr uint32_t InstanceSize() {
    return sizeof(DexCache);
  }

  void Initialize(const DexFile* dex_file, ObjPtr<ClassLoader> class_loader)
      REQUIRES_SHARED(Locks::mutator_lock_)
      REQUIRES(Locks::dex_lock_);

  // Zero all array references.
  // WARNING: This does not free the memory since it is in LinearAlloc.
  void ResetNativeArrays() REQUIRES_SHARED(Locks::mutator_lock_);

  template<VerifyObjectFlags kVerifyFlags = kDefaultVerifyFlags,
           ReadBarrierOption kReadBarrierOption = kWithReadBarrier>
  ObjPtr<String> GetLocation() REQUIRES_SHARED(Locks::mutator_lock_);

  static constexpr MemberOffset StringsOffset() {
    return OFFSET_OF_OBJECT_MEMBER(DexCache, strings_);
  }

  static constexpr MemberOffset PreResolvedStringsOffset() {
    return OFFSET_OF_OBJECT_MEMBER(DexCache, preresolved_strings_);
  }

  static constexpr MemberOffset ResolvedTypesOffset() {
    return OFFSET_OF_OBJECT_MEMBER(DexCache, resolved_types_);
  }

  static constexpr MemberOffset ResolvedFieldsOffset() {
    return OFFSET_OF_OBJECT_MEMBER(DexCache, resolved_fields_);
  }

  static constexpr MemberOffset ResolvedMethodsOffset() {
    return OFFSET_OF_OBJECT_MEMBER(DexCache, resolved_methods_);
  }

  static constexpr MemberOffset ResolvedMethodTypesOffset() {
    return OFFSET_OF_OBJECT_MEMBER(DexCache, resolved_method_types_);
  }

  static constexpr MemberOffset ResolvedCallSitesOffset() {
    return OFFSET_OF_OBJECT_MEMBER(DexCache, resolved_call_sites_);
  }

  static constexpr MemberOffset NumStringsOffset() {
    return OFFSET_OF_OBJECT_MEMBER(DexCache, num_strings_);
  }

  static constexpr MemberOffset NumPreResolvedStringsOffset() {
    return OFFSET_OF_OBJECT_MEMBER(DexCache, num_preresolved_strings_);
  }

  static constexpr MemberOffset NumResolvedTypesOffset() {
    return OFFSET_OF_OBJECT_MEMBER(DexCache, num_resolved_types_);
  }

  static constexpr MemberOffset NumResolvedFieldsOffset() {
    return OFFSET_OF_OBJECT_MEMBER(DexCache, num_resolved_fields_);
  }

  static constexpr MemberOffset NumResolvedMethodsOffset() {
    return OFFSET_OF_OBJECT_MEMBER(DexCache, num_resolved_methods_);
  }

  static constexpr MemberOffset NumResolvedMethodTypesOffset() {
    return OFFSET_OF_OBJECT_MEMBER(DexCache, num_resolved_method_types_);
  }

  static constexpr MemberOffset NumResolvedCallSitesOffset() {
    return OFFSET_OF_OBJECT_MEMBER(DexCache, num_resolved_call_sites_);
  }

  static constexpr size_t PreResolvedStringsAlignment() {
    return alignof(GcRoot<mirror::String>);
  }

  String* GetResolvedString(dex::StringIndex string_idx) ALWAYS_INLINE
      REQUIRES_SHARED(Locks::mutator_lock_);

  void SetResolvedString(dex::StringIndex string_idx, ObjPtr<mirror::String> resolved) ALWAYS_INLINE
      REQUIRES_SHARED(Locks::mutator_lock_);

  // Clear a string for a string_idx, used to undo string intern transactions to make sure
  // the string isn't kept live.
  void ClearString(dex::StringIndex string_idx) REQUIRES_SHARED(Locks::mutator_lock_);

  Class* GetResolvedType(dex::TypeIndex type_idx) REQUIRES_SHARED(Locks::mutator_lock_);

  void SetResolvedType(dex::TypeIndex type_idx, ObjPtr<Class> resolved)
      REQUIRES_SHARED(Locks::mutator_lock_);

  void ClearResolvedType(dex::TypeIndex type_idx) REQUIRES_SHARED(Locks::mutator_lock_);

  ALWAYS_INLINE ArtMethod* GetResolvedMethod(uint32_t method_idx)
      REQUIRES_SHARED(Locks::mutator_lock_);

  ALWAYS_INLINE void SetResolvedMethod(uint32_t method_idx, ArtMethod* resolved)
      REQUIRES_SHARED(Locks::mutator_lock_);

  ALWAYS_INLINE ArtField* GetResolvedField(uint32_t idx)
      REQUIRES_SHARED(Locks::mutator_lock_);

  ALWAYS_INLINE void SetResolvedField(uint32_t idx, ArtField* field)
      REQUIRES_SHARED(Locks::mutator_lock_);

  MethodType* GetResolvedMethodType(dex::ProtoIndex proto_idx) REQUIRES_SHARED(Locks::mutator_lock_);

  void SetResolvedMethodType(dex::ProtoIndex proto_idx, MethodType* resolved)
      REQUIRES_SHARED(Locks::mutator_lock_);

  // Clear a method type for proto_idx, used to undo method type resolution
  // in aborted transactions to make sure the method type isn't kept live.
  void ClearMethodType(dex::ProtoIndex proto_idx) REQUIRES_SHARED(Locks::mutator_lock_);

  CallSite* GetResolvedCallSite(uint32_t call_site_idx) REQUIRES_SHARED(Locks::mutator_lock_);

  // Attempts to bind |call_site_idx| to the call site |resolved|. The
  // caller must use the return value in place of |resolved|. This is
  // because multiple threads can invoke the bootstrap method each
  // producing a call site, but the method handle invocation on the
  // call site must be on a common agreed value.
  ObjPtr<CallSite> SetResolvedCallSite(uint32_t call_site_idx, ObjPtr<CallSite> resolved)
      REQUIRES_SHARED(Locks::mutator_lock_) WARN_UNUSED;

  template<VerifyObjectFlags kVerifyFlags = kDefaultVerifyFlags>
  StringDexCacheType* GetStrings() ALWAYS_INLINE REQUIRES_SHARED(Locks::mutator_lock_) {
    return GetFieldPtr64<StringDexCacheType*, kVerifyFlags>(StringsOffset());
  }

  void SetStrings(StringDexCacheType* strings) ALWAYS_INLINE REQUIRES_SHARED(Locks::mutator_lock_) {
    SetFieldPtr<false>(StringsOffset(), strings);
  }

  template<VerifyObjectFlags kVerifyFlags = kDefaultVerifyFlags>
  TypeDexCacheType* GetResolvedTypes() ALWAYS_INLINE REQUIRES_SHARED(Locks::mutator_lock_) {
    return GetFieldPtr<TypeDexCacheType*, kVerifyFlags>(ResolvedTypesOffset());
  }

  void SetResolvedTypes(TypeDexCacheType* resolved_types)
      ALWAYS_INLINE
      REQUIRES_SHARED(Locks::mutator_lock_) {
    SetFieldPtr<false>(ResolvedTypesOffset(), resolved_types);
  }

  MethodDexCacheType* GetResolvedMethods() ALWAYS_INLINE REQUIRES_SHARED(Locks::mutator_lock_) {
    return GetFieldPtr<MethodDexCacheType*>(ResolvedMethodsOffset());
  }

  void SetResolvedMethods(MethodDexCacheType* resolved_methods)
      ALWAYS_INLINE
      REQUIRES_SHARED(Locks::mutator_lock_) {
    SetFieldPtr<false>(ResolvedMethodsOffset(), resolved_methods);
  }

  FieldDexCacheType* GetResolvedFields() ALWAYS_INLINE REQUIRES_SHARED(Locks::mutator_lock_) {
    return GetFieldPtr<FieldDexCacheType*>(ResolvedFieldsOffset());
  }

  void SetResolvedFields(FieldDexCacheType* resolved_fields)
      ALWAYS_INLINE
      REQUIRES_SHARED(Locks::mutator_lock_) {
    SetFieldPtr<false>(ResolvedFieldsOffset(), resolved_fields);
  }

  template<VerifyObjectFlags kVerifyFlags = kDefaultVerifyFlags>
  MethodTypeDexCacheType* GetResolvedMethodTypes()
      ALWAYS_INLINE REQUIRES_SHARED(Locks::mutator_lock_) {
    return GetFieldPtr64<MethodTypeDexCacheType*, kVerifyFlags>(ResolvedMethodTypesOffset());
  }

  void SetResolvedMethodTypes(MethodTypeDexCacheType* resolved_method_types)
      ALWAYS_INLINE
      REQUIRES_SHARED(Locks::mutator_lock_) {
    SetFieldPtr<false>(ResolvedMethodTypesOffset(), resolved_method_types);
  }

  template<VerifyObjectFlags kVerifyFlags = kDefaultVerifyFlags>
  GcRoot<CallSite>* GetResolvedCallSites()
      ALWAYS_INLINE
      REQUIRES_SHARED(Locks::mutator_lock_) {
    return GetFieldPtr<GcRoot<CallSite>*, kVerifyFlags>(ResolvedCallSitesOffset());
  }

  void SetResolvedCallSites(GcRoot<CallSite>* resolved_call_sites)
      ALWAYS_INLINE
      REQUIRES_SHARED(Locks::mutator_lock_) {
    SetFieldPtr<false>(ResolvedCallSitesOffset(), resolved_call_sites);
  }

  template<VerifyObjectFlags kVerifyFlags = kDefaultVerifyFlags>
  size_t NumStrings() REQUIRES_SHARED(Locks::mutator_lock_) {
    return GetField32<kVerifyFlags>(NumStringsOffset());
  }

  template<VerifyObjectFlags kVerifyFlags = kDefaultVerifyFlags>
  size_t NumPreResolvedStrings() REQUIRES_SHARED(Locks::mutator_lock_) {
    return GetField32<kVerifyFlags>(NumPreResolvedStringsOffset());
  }

  template<VerifyObjectFlags kVerifyFlags = kDefaultVerifyFlags>
  size_t NumResolvedTypes() REQUIRES_SHARED(Locks::mutator_lock_) {
    return GetField32<kVerifyFlags>(NumResolvedTypesOffset());
  }

  template<VerifyObjectFlags kVerifyFlags = kDefaultVerifyFlags>
  size_t NumResolvedMethods() REQUIRES_SHARED(Locks::mutator_lock_) {
    return GetField32<kVerifyFlags>(NumResolvedMethodsOffset());
  }

  template<VerifyObjectFlags kVerifyFlags = kDefaultVerifyFlags>
  size_t NumResolvedFields() REQUIRES_SHARED(Locks::mutator_lock_) {
    return GetField32<kVerifyFlags>(NumResolvedFieldsOffset());
  }

  template<VerifyObjectFlags kVerifyFlags = kDefaultVerifyFlags>
  size_t NumResolvedMethodTypes() REQUIRES_SHARED(Locks::mutator_lock_) {
    return GetField32<kVerifyFlags>(NumResolvedMethodTypesOffset());
  }

  template<VerifyObjectFlags kVerifyFlags = kDefaultVerifyFlags>
  size_t NumResolvedCallSites() REQUIRES_SHARED(Locks::mutator_lock_) {
    return GetField32<kVerifyFlags>(NumResolvedCallSitesOffset());
  }

  const DexFile* GetDexFile() ALWAYS_INLINE REQUIRES_SHARED(Locks::mutator_lock_) {
    return GetFieldPtr<const DexFile*>(OFFSET_OF_OBJECT_MEMBER(DexCache, dex_file_));
  }

  void SetDexFile(const DexFile* dex_file) REQUIRES_SHARED(Locks::mutator_lock_) {
    SetFieldPtr<false>(OFFSET_OF_OBJECT_MEMBER(DexCache, dex_file_), dex_file);
  }

  void SetLocation(ObjPtr<String> location) REQUIRES_SHARED(Locks::mutator_lock_);

  template <typename T>
  static NativeDexCachePair<T> GetNativePair(std::atomic<NativeDexCachePair<T>>* pair_array,
                                             size_t idx);

  template <typename T>
  static void SetNativePair(std::atomic<NativeDexCachePair<T>>* pair_array,
                            size_t idx,
                            NativeDexCachePair<T> pair);

  static size_t PreResolvedStringsSize(size_t num_strings) {
    return sizeof(GcRoot<mirror::String>) * num_strings;
  }

  uint32_t StringSlotIndex(dex::StringIndex string_idx) REQUIRES_SHARED(Locks::mutator_lock_);
  uint32_t TypeSlotIndex(dex::TypeIndex type_idx) REQUIRES_SHARED(Locks::mutator_lock_);
  uint32_t FieldSlotIndex(uint32_t field_idx) REQUIRES_SHARED(Locks::mutator_lock_);
  uint32_t MethodSlotIndex(uint32_t method_idx) REQUIRES_SHARED(Locks::mutator_lock_);
  uint32_t MethodTypeSlotIndex(dex::ProtoIndex proto_idx) REQUIRES_SHARED(Locks::mutator_lock_);

  void VisitReflectiveTargets(ReflectiveValueVisitor* visitor) REQUIRES(Locks::mutator_lock_);

  void SetClassLoader(ObjPtr<ClassLoader> class_loader) REQUIRES_SHARED(Locks::mutator_lock_);

  ObjPtr<ClassLoader> GetClassLoader() REQUIRES_SHARED(Locks::mutator_lock_);

 private:
  // Allocate new array in linear alloc and save it in the given fields.
  template<typename T, size_t kMaxCacheSize>
  T* AllocArray(MemberOffset obj_offset, MemberOffset num_offset, size_t num)
     REQUIRES_SHARED(Locks::mutator_lock_);

  // Visit instance fields of the dex cache as well as its associated arrays.
  template <bool kVisitNativeRoots,
            VerifyObjectFlags kVerifyFlags = kDefaultVerifyFlags,
            ReadBarrierOption kReadBarrierOption = kWithReadBarrier,
            typename Visitor>
  void VisitReferences(ObjPtr<Class> klass, const Visitor& visitor)
      REQUIRES_SHARED(Locks::mutator_lock_) REQUIRES(Locks::heap_bitmap_lock_);

  HeapReference<ClassLoader> class_loader_;
  HeapReference<String> location_;

  uint64_t dex_file_;                // const DexFile*
  uint64_t preresolved_strings_;     // GcRoot<mirror::String*> array with num_preresolved_strings
                                     // elements.
  uint64_t resolved_call_sites_;     // GcRoot<CallSite>* array with num_resolved_call_sites_
                                     // elements.
  uint64_t resolved_fields_;         // std::atomic<FieldDexCachePair>*, array with
                                     // num_resolved_fields_ elements.
  uint64_t resolved_method_types_;   // std::atomic<MethodTypeDexCachePair>* array with
                                     // num_resolved_method_types_ elements.
  uint64_t resolved_methods_;        // ArtMethod*, array with num_resolved_methods_ elements.
  uint64_t resolved_types_;          // TypeDexCacheType*, array with num_resolved_types_ elements.
  uint64_t strings_;                 // std::atomic<StringDexCachePair>*, array with num_strings_
                                     // elements.

  uint32_t num_preresolved_strings_;    // Number of elements in the preresolved_strings_ array.
  uint32_t num_resolved_call_sites_;    // Number of elements in the call_sites_ array.
  uint32_t num_resolved_fields_;        // Number of elements in the resolved_fields_ array.
  uint32_t num_resolved_method_types_;  // Number of elements in the resolved_method_types_ array.
  uint32_t num_resolved_methods_;       // Number of elements in the resolved_methods_ array.
  uint32_t num_resolved_types_;         // Number of elements in the resolved_types_ array.
  uint32_t num_strings_;                // Number of elements in the strings_ array.

  friend struct art::DexCacheOffsets;  // for verifying offset information
  friend class linker::ImageWriter;
  friend class Object;  // For VisitReferences
  DISALLOW_IMPLICIT_CONSTRUCTORS(DexCache);
};

}  // namespace mirror
}  // namespace art

#endif  // ART_RUNTIME_MIRROR_DEX_CACHE_H_
