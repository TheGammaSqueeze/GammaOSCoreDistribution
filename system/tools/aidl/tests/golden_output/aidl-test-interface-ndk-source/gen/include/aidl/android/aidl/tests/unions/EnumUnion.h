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
#include <aidl/android/aidl/tests/IntEnum.h>
#include <aidl/android/aidl/tests/LongEnum.h>
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
namespace unions {
class EnumUnion {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  enum class Tag : int32_t {
    intEnum = 0,
    longEnum = 1,
    deprecatedField __attribute__((deprecated("do not use this"))) = 2,
  };

  // Expose tag symbols for legacy code
  static const inline Tag intEnum = Tag::intEnum;
  static const inline Tag longEnum = Tag::longEnum;
  static const inline Tag __attribute__((deprecated("do not use this"))) deprecatedField = Tag::deprecatedField;

  template<typename _Tp>
  static constexpr bool _not_self = !std::is_same_v<std::remove_cv_t<std::remove_reference_t<_Tp>>, EnumUnion>;

  EnumUnion() : _value(std::in_place_index<static_cast<size_t>(intEnum)>, ::aidl::android::aidl::tests::IntEnum(::aidl::android::aidl::tests::IntEnum::FOO)) { }

  template <typename _Tp, typename = std::enable_if_t<_not_self<_Tp>>>
  // NOLINTNEXTLINE(google-explicit-constructor)
  constexpr EnumUnion(_Tp&& _arg)
      : _value(std::forward<_Tp>(_arg)) {}

  template <size_t _Np, typename... _Tp>
  constexpr explicit EnumUnion(std::in_place_index_t<_Np>, _Tp&&... _args)
      : _value(std::in_place_index<_Np>, std::forward<_Tp>(_args)...) {}

  template <Tag _tag, typename... _Tp>
  static EnumUnion make(_Tp&&... _args) {
    return EnumUnion(std::in_place_index<static_cast<size_t>(_tag)>, std::forward<_Tp>(_args)...);
  }

  template <Tag _tag, typename _Tp, typename... _Up>
  static EnumUnion make(std::initializer_list<_Tp> _il, _Up&&... _args) {
    return EnumUnion(std::in_place_index<static_cast<size_t>(_tag)>, std::move(_il), std::forward<_Up>(_args)...);
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

  inline bool operator!=(const EnumUnion& rhs) const {
    return _value != rhs._value;
  }
  inline bool operator<(const EnumUnion& rhs) const {
    return _value < rhs._value;
  }
  inline bool operator<=(const EnumUnion& rhs) const {
    return _value <= rhs._value;
  }
  inline bool operator==(const EnumUnion& rhs) const {
    return _value == rhs._value;
  }
  inline bool operator>(const EnumUnion& rhs) const {
    return _value > rhs._value;
  }
  inline bool operator>=(const EnumUnion& rhs) const {
    return _value >= rhs._value;
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_LOCAL;
  inline std::string toString() const {
    std::ostringstream os;
    os << "EnumUnion{";
    switch (getTag()) {
    case intEnum: os << "intEnum: " << ::android::internal::ToString(get<intEnum>()); break;
    case longEnum: os << "longEnum: " << ::android::internal::ToString(get<longEnum>()); break;
    #pragma clang diagnostic push
    #pragma clang diagnostic ignored "-Wdeprecated-declarations"
    case deprecatedField: os << "deprecatedField: " << ::android::internal::ToString(get<deprecatedField>()); break;
    #pragma clang diagnostic pop
    }
    os << "}";
    return os.str();
  }
private:
  std::variant<::aidl::android::aidl::tests::IntEnum, ::aidl::android::aidl::tests::LongEnum, int32_t> _value;
};
}  // namespace unions
}  // namespace tests
}  // namespace aidl
}  // namespace android
}  // namespace aidl
namespace aidl {
namespace android {
namespace aidl {
namespace tests {
namespace unions {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wdeprecated-declarations"
[[nodiscard]] static inline std::string toString(EnumUnion::Tag val) {
  switch(val) {
  case EnumUnion::Tag::intEnum:
    return "intEnum";
  case EnumUnion::Tag::longEnum:
    return "longEnum";
  case EnumUnion::Tag::deprecatedField:
    return "deprecatedField";
  default:
    return std::to_string(static_cast<int32_t>(val));
  }
}
#pragma clang diagnostic pop
}  // namespace unions
}  // namespace tests
}  // namespace aidl
}  // namespace android
}  // namespace aidl
namespace ndk {
namespace internal {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
#pragma clang diagnostic ignored "-Wdeprecated-declarations"
template <>
constexpr inline std::array<aidl::android::aidl::tests::unions::EnumUnion::Tag, 3> enum_values<aidl::android::aidl::tests::unions::EnumUnion::Tag> = {
  aidl::android::aidl::tests::unions::EnumUnion::Tag::intEnum,
  aidl::android::aidl::tests::unions::EnumUnion::Tag::longEnum,
  aidl::android::aidl::tests::unions::EnumUnion::Tag::deprecatedField,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
