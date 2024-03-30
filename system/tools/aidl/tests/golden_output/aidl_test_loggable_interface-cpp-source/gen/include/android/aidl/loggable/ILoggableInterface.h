#pragma once

#include <android/aidl/loggable/Data.h>
#include <android/binder_to_string.h>
#include <binder/IBinder.h>
#include <binder/IInterface.h>
#include <binder/ParcelFileDescriptor.h>
#include <binder/Status.h>
#include <cstdint>
#include <functional>
#include <optional>
#include <utils/String16.h>
#include <utils/StrongPointer.h>
#include <utils/Trace.h>
#include <vector>

namespace android {
namespace aidl {
namespace loggable {
class ILoggableInterface : public ::android::IInterface {
public:
  DECLARE_META_INTERFACE(LoggableInterface)
  class ISub : public ::android::IInterface {
  public:
    DECLARE_META_INTERFACE(Sub)
    virtual ::android::binder::Status Log(int32_t value) = 0;
  };  // class ISub

  class ISubDefault : public ISub {
  public:
    ::android::IBinder* onAsBinder() override {
      return nullptr;
    }
    ::android::binder::Status Log(int32_t /*value*/) override {
      return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
    }
  };  // class ISubDefault
  class BpSub : public ::android::BpInterface<ISub> {
  public:
    explicit BpSub(const ::android::sp<::android::IBinder>& _aidl_impl);
    virtual ~BpSub() = default;
    ::android::binder::Status Log(int32_t value) override;
    struct TransactionLog {
      double duration_ms;
      std::string interface_name;
      std::string method_name;
      const void* proxy_address;
      const void* stub_address;
      std::vector<std::pair<std::string, std::string>> input_args;
      std::vector<std::pair<std::string, std::string>> output_args;
      std::string result;
      std::string exception_message;
      int32_t exception_code;
      int32_t transaction_error;
      int32_t service_specific_error_code;
    };
    static std::function<void(const TransactionLog&)> logFunc;
  };  // class BpSub
  class BnSub : public ::android::BnInterface<ISub> {
  public:
    static constexpr uint32_t TRANSACTION_Log = ::android::IBinder::FIRST_CALL_TRANSACTION + 0;
    explicit BnSub();
    ::android::status_t onTransact(uint32_t _aidl_code, const ::android::Parcel& _aidl_data, ::android::Parcel* _aidl_reply, uint32_t _aidl_flags) override;
    struct TransactionLog {
      double duration_ms;
      std::string interface_name;
      std::string method_name;
      const void* proxy_address;
      const void* stub_address;
      std::vector<std::pair<std::string, std::string>> input_args;
      std::vector<std::pair<std::string, std::string>> output_args;
      std::string result;
      std::string exception_message;
      int32_t exception_code;
      int32_t transaction_error;
      int32_t service_specific_error_code;
    };
    static std::function<void(const TransactionLog&)> logFunc;
  };  // class BnSub

  class ISubDelegator : public BnSub {
  public:
    explicit ISubDelegator(::android::sp<ISub> &impl) : _aidl_delegate(impl) {}

    ::android::binder::Status Log(int32_t value) override {
      return _aidl_delegate->Log(value);
    }
  private:
    ::android::sp<ISub> _aidl_delegate;
  };  // class ISubDelegator
  virtual ::android::binder::Status LogThis(bool boolValue, ::std::vector<bool>* boolArray, int8_t byteValue, ::std::vector<uint8_t>* byteArray, char16_t charValue, ::std::vector<char16_t>* charArray, int32_t intValue, ::std::vector<int32_t>* intArray, int64_t longValue, ::std::vector<int64_t>* longArray, float floatValue, ::std::vector<float>* floatArray, double doubleValue, ::std::vector<double>* doubleArray, const ::android::String16& stringValue, ::std::vector<::android::String16>* stringArray, ::std::vector<::android::String16>* listValue, const ::android::aidl::loggable::Data& dataValue, const ::android::sp<::android::IBinder>& binderValue, ::std::optional<::android::os::ParcelFileDescriptor>* pfdValue, ::std::vector<::android::os::ParcelFileDescriptor>* pfdArray, ::std::vector<::android::String16>* _aidl_return) = 0;
};  // class ILoggableInterface

class ILoggableInterfaceDefault : public ILoggableInterface {
public:
  ::android::IBinder* onAsBinder() override {
    return nullptr;
  }
  ::android::binder::Status LogThis(bool /*boolValue*/, ::std::vector<bool>* /*boolArray*/, int8_t /*byteValue*/, ::std::vector<uint8_t>* /*byteArray*/, char16_t /*charValue*/, ::std::vector<char16_t>* /*charArray*/, int32_t /*intValue*/, ::std::vector<int32_t>* /*intArray*/, int64_t /*longValue*/, ::std::vector<int64_t>* /*longArray*/, float /*floatValue*/, ::std::vector<float>* /*floatArray*/, double /*doubleValue*/, ::std::vector<double>* /*doubleArray*/, const ::android::String16& /*stringValue*/, ::std::vector<::android::String16>* /*stringArray*/, ::std::vector<::android::String16>* /*listValue*/, const ::android::aidl::loggable::Data& /*dataValue*/, const ::android::sp<::android::IBinder>& /*binderValue*/, ::std::optional<::android::os::ParcelFileDescriptor>* /*pfdValue*/, ::std::vector<::android::os::ParcelFileDescriptor>* /*pfdArray*/, ::std::vector<::android::String16>* /*_aidl_return*/) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
};  // class ILoggableInterfaceDefault
}  // namespace loggable
}  // namespace aidl
}  // namespace android
