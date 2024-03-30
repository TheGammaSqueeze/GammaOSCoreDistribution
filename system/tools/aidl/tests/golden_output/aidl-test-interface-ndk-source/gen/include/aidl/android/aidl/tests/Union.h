#pragma once

#include <array>
#include <cassert>
#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <type_traits>
#include <utility>
#include <variant>
#include <vector>
#include <android/binder_enums.h>
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#include <aidl/android/aidl/tests/ByteEnum.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

#ifndef __BIONIC__
#define __assert2(a,b,c,d) ((void)0)
#endif

namespace aidl {
namespace android {
namespace aidl {
namespace tests {
class Union {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  enum class Tag : int32_t {
    ns = 0,
    n = 1,
    m = 2,
    s = 3,
    ibinder = 4,
    ss = 5,
    be = 6,
  };

  // Expose tag symbols for legacy code
  static const inline Tag ns = Tag::ns;
  static const inline Tag n = Tag::n;
  static const inline Tag m = Tag::m;
  static const inline Tag s = Tag::s;
  static const inline Tag ibinder = Tag::ibinder;
  static const inline Tag ss = Tag::ss;
  static const inline Tag be = Tag::be;

  template<typename _Tp>
  static constexpr bool _not_self = !std::is_same_v<std::remove_cv_t<std::remove_reference_t<_Tp>>, Union>;

  Union() : _value(std::in_place_index<static_cast<size_t>(ns)>, std::vector<int32_t>({})) { }

  template <typename _Tp, typename = std::enable_if_t<_not_self<_Tp>>>
  // NOLINTNEXTLINE(google-explicit-constructor)
  constexpr Union(_Tp&& _arg)
      : _value(std::forward<_Tp>(_arg)) {}

  template <size_t _Np, typename... _Tp>
  constexpr explicit Union(std::in_place_index_t<_Np>, _Tp&&... _args)
      : _value(std::in_place_index<_Np>, std::forward<_Tp>(_args)...) {}

  template <Tag _tag, typename... _Tp>
  static Union make(_Tp&&... _args) {
    return Union(std::in_place_index<static_cast<size_t>(_tag)>, std::forward<_Tp>(_args)...);
  }

  template <Tag _tag, typename _Tp, typename... _Up>
  static Union make(std::initializer_list<_Tp> _il, _Up&&... _args) {
    return Union(std::in_place_index<static_cast<size_t>(_tag)>, std::move(_il), std::forward<_Up>(_args)...);
  }

  Tag getTag() const {
    return static_cast<Tag>(_value.index());
  }

  template <Tag _tag>
  const auto& get() const {
    if (getTag() != _tag) { __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "bad access: a wrong tag"); }
    return std::get<static_cast<size_t>(_tag)>(_value);
  }

  template <Tag _tag>
  auto& get() {
    if (getTag() != _tag) { __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "bad access: a wrong tag"); }
    return std::get<static_cast<size_t>(_tag)>(_value);
  }

  template <Tag _tag, typename... _Tp>
  void set(_Tp&&... _args) {
    _value.emplace<static_cast<size_t>(_tag)>(std::forward<_Tp>(_args)...);
  }

  binder_status_t readFromParcel(const AParcel* _parcel);
  binder_status_t writeToParcel(AParcel* _parcel) const;

  inline bool operator!=(const Union& rhs) const {
    return _value != rhs._value;
  }
  inline bool operator<(const Union& rhs) const {
    return _value < rhs._value;
  }
  inline bool operator<=(const Union& rhs) const {
    return _value <= rhs._value;
  }
  inline bool operator==(const Union& rhs) const {
    return _value == rhs._value;
  }
  inline bool operator>(const Union& rhs) const {
    return _value > rhs._value;
  }
  inline bool operator>=(const Union& rhs) const {
    return _value >= rhs._value;
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_LOCAL;
  static const char* S1;
  inline std::string toString() const {
    std::ostringstream os;
    os << "Union{";
    switch (getTag()) {
    case ns: os << "ns: " << ::android::internal::ToString(get<ns>()); break;
    case n: os << "n: " << ::android::internal::ToString(get<n>()); break;
    case m: os << "m: " << ::android::internal::ToString(get<m>()); break;
    case s: os << "s: " << ::android::internal::ToString(get<s>()); break;
    case ibinder: os << "ibinder: " << ::android::internal::ToString(get<ibinder>()); break;
    case ss: os << "ss: " << ::android::internal::ToString(get<ss>()); break;
    case be: os << "be: " << ::android::internal::ToString(get<be>()); break;
    }
    os << "}";
    return os.str();
  }
private:
  std::variant<std::vector<int32_t>, int32_t, int32_t, std::string, ::ndk::SpAIBinder, std::vector<std::string>, ::aidl::android::aidl::tests::ByteEnum> _value;
};
}  // namespace tests
}  // namespace aidl
}  // namespace android
}  // namespace aidl
namespace aidl {
namespace android {
namespace aidl {
namespace tests {
[[nodiscard]] static inline std::string toString(Union::Tag val) {
  switch(val) {
  case Union::Tag::ns:
    return "ns";
  case Union::Tag::n:
    return "n";
  case Union::Tag::m:
    return "m";
  case Union::Tag::s:
    return "s";
  case Union::Tag::ibinder:
    return "ibinder";
  case Union::Tag::ss:
    return "ss";
  case Union::Tag::be:
    return "be";
  default:
    return std::to_string(static_cast<int32_t>(val));
  }
}
}  // namespace tests
}  // namespace aidl
}  // namespace android
}  // namespace aidl
namespace ndk {
namespace internal {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template <>
constexpr inline std::array<aidl::android::aidl::tests::Union::Tag, 7> enum_values<aidl::android::aidl::tests::Union::Tag> = {
  aidl::android::aidl::tests::Union::Tag::ns,
  aidl::android::aidl::tests::Union::Tag::n,
  aidl::android::aidl::tests::Union::Tag::m,
  aidl::android::aidl::tests::Union::Tag::s,
  aidl::android::aidl::tests::Union::Tag::ibinder,
  aidl::android::aidl::tests::Union::Tag::ss,
  aidl::android::aidl::tests::Union::Tag::be,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
