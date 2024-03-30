#pragma once

#include <binder/IInterface.h>
#include <android/aidl/versioned/tests/IFooInterface.h>

namespace android {
namespace aidl {
namespace versioned {
namespace tests {
class BnFooInterface : public ::android::BnInterface<IFooInterface> {
public:
  static constexpr uint32_t TRANSACTION_originalApi = ::android::IBinder::FIRST_CALL_TRANSACTION + 0;
  static constexpr uint32_t TRANSACTION_acceptUnionAndReturnString = ::android::IBinder::FIRST_CALL_TRANSACTION + 1;
  static constexpr uint32_t TRANSACTION_ignoreParcelablesAndRepeatInt = ::android::IBinder::FIRST_CALL_TRANSACTION + 2;
  static constexpr uint32_t TRANSACTION_returnsLengthOfFooArray = ::android::IBinder::FIRST_CALL_TRANSACTION + 3;
  static constexpr uint32_t TRANSACTION_getInterfaceVersion = ::android::IBinder::FIRST_CALL_TRANSACTION + 16777214;
  static constexpr uint32_t TRANSACTION_getInterfaceHash = ::android::IBinder::FIRST_CALL_TRANSACTION + 16777213;
  explicit BnFooInterface();
  ::android::status_t onTransact(uint32_t _aidl_code, const ::android::Parcel& _aidl_data, ::android::Parcel* _aidl_reply, uint32_t _aidl_flags) override;
  int32_t getInterfaceVersion();
  std::string getInterfaceHash();
};  // class BnFooInterface

class IFooInterfaceDelegator : public BnFooInterface {
public:
  explicit IFooInterfaceDelegator(::android::sp<IFooInterface> &impl) : _aidl_delegate(impl) {}

  ::android::binder::Status originalApi() override {
    return _aidl_delegate->originalApi();
  }
  ::android::binder::Status acceptUnionAndReturnString(const ::android::aidl::versioned::tests::BazUnion& u, ::std::string* _aidl_return) override {
    return _aidl_delegate->acceptUnionAndReturnString(u, _aidl_return);
  }
  ::android::binder::Status ignoreParcelablesAndRepeatInt(const ::android::aidl::versioned::tests::Foo& inFoo, ::android::aidl::versioned::tests::Foo* inoutFoo, ::android::aidl::versioned::tests::Foo* outFoo, int32_t value, int32_t* _aidl_return) override {
    return _aidl_delegate->ignoreParcelablesAndRepeatInt(inFoo, inoutFoo, outFoo, value, _aidl_return);
  }
  ::android::binder::Status returnsLengthOfFooArray(const ::std::vector<::android::aidl::versioned::tests::Foo>& foos, int32_t* _aidl_return) override {
    return _aidl_delegate->returnsLengthOfFooArray(foos, _aidl_return);
  }
  int32_t getInterfaceVersion() override {
    int32_t _delegator_ver = BnFooInterface::getInterfaceVersion();
    int32_t _impl_ver = _aidl_delegate->getInterfaceVersion();
    return _delegator_ver < _impl_ver ? _delegator_ver : _impl_ver;
  }
  std::string getInterfaceHash() override {
    return _aidl_delegate->getInterfaceHash();
  }
private:
  ::android::sp<IFooInterface> _aidl_delegate;
};  // class IFooInterfaceDelegator
}  // namespace tests
}  // namespace versioned
}  // namespace aidl
}  // namespace android
