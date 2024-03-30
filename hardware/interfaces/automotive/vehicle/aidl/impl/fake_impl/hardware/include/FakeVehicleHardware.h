/*
 * Copyright (C) 2021 The Android Open Source Project
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

#ifndef android_hardware_automotive_vehicle_aidl_impl_fake_impl_hardware_include_FakeVehicleHardware_H_
#define android_hardware_automotive_vehicle_aidl_impl_fake_impl_hardware_include_FakeVehicleHardware_H_

#include <ConcurrentQueue.h>
#include <DefaultConfig.h>
#include <FakeObd2Frame.h>
#include <FakeUserHal.h>
#include <IVehicleHardware.h>
#include <RecurrentTimer.h>
#include <VehicleHalTypes.h>
#include <VehiclePropertyStore.h>
#include <android-base/parseint.h>
#include <android-base/result.h>
#include <android-base/stringprintf.h>
#include <android-base/thread_annotations.h>

#include <map>
#include <memory>
#include <mutex>
#include <vector>

namespace android {
namespace hardware {
namespace automotive {
namespace vehicle {
namespace fake {

class FakeVehicleHardware : public IVehicleHardware {
  public:
    using ValueResultType = VhalResult<VehiclePropValuePool::RecyclableType>;

    FakeVehicleHardware();

    explicit FakeVehicleHardware(std::unique_ptr<VehiclePropValuePool> valuePool);

    ~FakeVehicleHardware();

    // Get all the property configs.
    std::vector<aidl::android::hardware::automotive::vehicle::VehiclePropConfig>
    getAllPropertyConfigs() const override;

    // Set property values asynchronously. Server could return before the property set requests
    // are sent to vehicle bus or before property set confirmation is received. The callback is
    // safe to be called after the function returns and is safe to be called in a different thread.
    aidl::android::hardware::automotive::vehicle::StatusCode setValues(
            std::shared_ptr<const SetValuesCallback> callback,
            const std::vector<aidl::android::hardware::automotive::vehicle::SetValueRequest>&
                    requests) override;

    // Get property values asynchronously. Server could return before the property values are ready.
    // The callback is safe to be called after the function returns and is safe to be called in a
    // different thread.
    aidl::android::hardware::automotive::vehicle::StatusCode getValues(
            std::shared_ptr<const GetValuesCallback> callback,
            const std::vector<aidl::android::hardware::automotive::vehicle::GetValueRequest>&
                    requests) const override;

    // Dump debug information in the server.
    DumpResult dump(const std::vector<std::string>& options) override;

    // Check whether the system is healthy, return {@code StatusCode::OK} for healthy.
    aidl::android::hardware::automotive::vehicle::StatusCode checkHealth() override;

    // Register a callback that would be called when there is a property change event from vehicle.
    void registerOnPropertyChangeEvent(
            std::unique_ptr<const PropertyChangeCallback> callback) override;

    // Register a callback that would be called when there is a property set error event from
    // vehicle.
    void registerOnPropertySetErrorEvent(
            std::unique_ptr<const PropertySetErrorCallback> callback) override;

    // Update the sample rate for the [propId, areaId] pair.
    aidl::android::hardware::automotive::vehicle::StatusCode updateSampleRate(
            int32_t propId, int32_t areaId, float sampleRate) override;

  protected:
    // mValuePool is also used in mServerSidePropStore.
    const std::shared_ptr<VehiclePropValuePool> mValuePool;
    const std::shared_ptr<VehiclePropertyStore> mServerSidePropStore;

    ValueResultType getValue(
            const aidl::android::hardware::automotive::vehicle::VehiclePropValue& value) const;

    VhalResult<void> setValue(
            const aidl::android::hardware::automotive::vehicle::VehiclePropValue& value);

  private:
    // Expose private methods to unit test.
    friend class FakeVehicleHardwareTestHelper;

    template <class CallbackType, class RequestType>
    struct RequestWithCallback {
        RequestType request;
        std::shared_ptr<const CallbackType> callback;
    };

    template <class CallbackType, class RequestType>
    class PendingRequestHandler {
      public:
        PendingRequestHandler(FakeVehicleHardware* hardware);

        void addRequest(RequestType request, std::shared_ptr<const CallbackType> callback);

        void stop();

      private:
        FakeVehicleHardware* mHardware;
        std::thread mThread;
        ConcurrentQueue<RequestWithCallback<CallbackType, RequestType>> mRequests;

        void handleRequestsOnce();
    };

    const std::unique_ptr<obd2frame::FakeObd2Frame> mFakeObd2Frame;
    const std::unique_ptr<FakeUserHal> mFakeUserHal;
    // RecurrentTimer is thread-safe.
    std::unique_ptr<RecurrentTimer> mRecurrentTimer;
    std::mutex mLock;
    std::unique_ptr<const PropertyChangeCallback> mOnPropertyChangeCallback GUARDED_BY(mLock);
    std::unique_ptr<const PropertySetErrorCallback> mOnPropertySetErrorCallback GUARDED_BY(mLock);
    std::unordered_map<PropIdAreaId, std::shared_ptr<RecurrentTimer::Callback>, PropIdAreaIdHash>
            mRecurrentActions GUARDED_BY(mLock);
    // PendingRequestHandler is thread-safe.
    mutable PendingRequestHandler<GetValuesCallback,
                                  aidl::android::hardware::automotive::vehicle::GetValueRequest>
            mPendingGetValueRequests;
    mutable PendingRequestHandler<SetValuesCallback,
                                  aidl::android::hardware::automotive::vehicle::SetValueRequest>
            mPendingSetValueRequests;

    void init();
    // Stores the initial value to property store.
    void storePropInitialValue(const defaultconfig::ConfigDeclaration& config);
    // The callback that would be called when a vehicle property value change happens.
    void onValueChangeCallback(
            const aidl::android::hardware::automotive::vehicle::VehiclePropValue& value);
    // If property "persist.vendor.vhal_init_value_override" is set to true, override the properties
    // using config files in 'overrideDir'.
    void maybeOverrideProperties(const char* overrideDir);
    // Override the properties using config files in 'overrideDir'.
    void overrideProperties(const char* overrideDir);

    VhalResult<void> maybeSetSpecialValue(
            const aidl::android::hardware::automotive::vehicle::VehiclePropValue& value,
            bool* isSpecialValue);
    ValueResultType maybeGetSpecialValue(
            const aidl::android::hardware::automotive::vehicle::VehiclePropValue& value,
            bool* isSpecialValue) const;
    VhalResult<void> setApPowerStateReport(
            const aidl::android::hardware::automotive::vehicle::VehiclePropValue& value);
    VehiclePropValuePool::RecyclableType createApPowerStateReq(
            aidl::android::hardware::automotive::vehicle::VehicleApPowerStateReq state);
    VhalResult<void> setUserHalProp(
            const aidl::android::hardware::automotive::vehicle::VehiclePropValue& value);
    ValueResultType getUserHalProp(
            const aidl::android::hardware::automotive::vehicle::VehiclePropValue& value) const;
    ValueResultType getEchoReverseBytes(
            const aidl::android::hardware::automotive::vehicle::VehiclePropValue& value) const;
    bool isHvacPropAndHvacNotAvailable(int32_t propId);

    std::string dumpAllProperties();
    std::string dumpOnePropertyByConfig(
            int rowNumber,
            const aidl::android::hardware::automotive::vehicle::VehiclePropConfig& config);
    std::string dumpOnePropertyById(int32_t propId, int32_t areaId);
    std::string dumpHelp();
    std::string dumpListProperties();
    std::string dumpSpecificProperty(const std::vector<std::string>& options);
    std::string dumpSetProperties(const std::vector<std::string>& options);

    template <typename T>
    android::base::Result<T> safelyParseInt(int index, const std::string& s) {
        T out;
        if (!::android::base::ParseInt(s, &out)) {
            return android::base::Error() << android::base::StringPrintf(
                           "non-integer argument at index %d: %s\n", index, s.c_str());
        }
        return out;
    }
    android::base::Result<float> safelyParseFloat(int index, const std::string& s);
    std::vector<std::string> getOptionValues(const std::vector<std::string>& options,
                                             size_t* index);
    android::base::Result<aidl::android::hardware::automotive::vehicle::VehiclePropValue>
    parseSetPropOptions(const std::vector<std::string>& options);
    android::base::Result<std::vector<uint8_t>> parseHexString(const std::string& s);

    android::base::Result<void> checkArgumentsSize(const std::vector<std::string>& options,
                                                   size_t minSize);
    aidl::android::hardware::automotive::vehicle::GetValueResult handleGetValueRequest(
            const aidl::android::hardware::automotive::vehicle::GetValueRequest& request);
    aidl::android::hardware::automotive::vehicle::SetValueResult handleSetValueRequest(
            const aidl::android::hardware::automotive::vehicle::SetValueRequest& request);
};

}  // namespace fake
}  // namespace vehicle
}  // namespace automotive
}  // namespace hardware
}  // namespace android

#endif  // android_hardware_automotive_vehicle_aidl_impl_fake_impl_hardware_include_FakeVehicleHardware_H_
