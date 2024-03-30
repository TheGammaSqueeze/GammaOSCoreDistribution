#pragma once

#include "aidl/android/aidl/tests/nested/INestedService.h"

#include <android/binder_ibinder.h>
#include <cassert>

#ifndef __BIONIC__
#ifndef __assert2
#define __assert2(a,b,c,d) ((void)0)
#endif
#endif

namespace aidl {
namespace android {
namespace aidl {
namespace tests {
namespace nested {
class BnNestedService : public ::ndk::BnCInterface<INestedService> {
public:
  BnNestedService();
  virtual ~BnNestedService();
protected:
  ::ndk::SpAIBinder createBinder() override;
private:
};
class INestedServiceDelegator : public BnNestedService {
public:
  explicit INestedServiceDelegator(const std::shared_ptr<INestedService> &impl) : _impl(impl) {
  }

  ::ndk::ScopedAStatus flipStatus(const ::aidl::android::aidl::tests::nested::ParcelableWithNested& in_p, ::aidl::android::aidl::tests::nested::INestedService::Result* _aidl_return) override {
    return _impl->flipStatus(in_p, _aidl_return);
  }
  ::ndk::ScopedAStatus flipStatusWithCallback(::aidl::android::aidl::tests::nested::ParcelableWithNested::Status in_status, const std::shared_ptr<::aidl::android::aidl::tests::nested::INestedService::ICallback>& in_cb) override {
    return _impl->flipStatusWithCallback(in_status, in_cb);
  }
protected:
private:
  std::shared_ptr<INestedService> _impl;
};

}  // namespace nested
}  // namespace tests
}  // namespace aidl
}  // namespace android
}  // namespace aidl
