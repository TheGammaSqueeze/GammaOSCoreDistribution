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

#define LOG_TAG "VtsHalAutomotiveVehicle"

#include <IVhalClient.h>
#include <VehicleHalTypes.h>
#include <VehicleUtils.h>
#include <aidl/Gtest.h>
#include <aidl/Vintf.h>
#include <aidl/android/hardware/automotive/vehicle/IVehicle.h>
#include <android-base/stringprintf.h>
#include <android-base/thread_annotations.h>
#include <android/binder_process.h>
#include <gtest/gtest.h>
#include <hidl/GtestPrinter.h>
#include <hidl/ServiceManagement.h>
#include <inttypes.h>
#include <utils/Log.h>
#include <utils/SystemClock.h>

#include <chrono>
#include <mutex>
#include <unordered_map>
#include <unordered_set>
#include <vector>

using ::aidl::android::hardware::automotive::vehicle::IVehicle;
using ::aidl::android::hardware::automotive::vehicle::StatusCode;
using ::aidl::android::hardware::automotive::vehicle::SubscribeOptions;
using ::aidl::android::hardware::automotive::vehicle::VehicleArea;
using ::aidl::android::hardware::automotive::vehicle::VehicleProperty;
using ::aidl::android::hardware::automotive::vehicle::VehiclePropertyAccess;
using ::aidl::android::hardware::automotive::vehicle::VehiclePropertyType;
using ::android::getAidlHalInstanceNames;
using ::android::base::ScopedLockAssertion;
using ::android::base::StringPrintf;
using ::android::frameworks::automotive::vhal::HalPropError;
using ::android::frameworks::automotive::vhal::IHalPropConfig;
using ::android::frameworks::automotive::vhal::IHalPropValue;
using ::android::frameworks::automotive::vhal::ISubscriptionCallback;
using ::android::frameworks::automotive::vhal::IVhalClient;
using ::android::hardware::getAllHalInstanceNames;
using ::android::hardware::Sanitize;
using ::android::hardware::automotive::vehicle::toInt;

constexpr int32_t kInvalidProp = 0x31600207;

struct ServiceDescriptor {
    std::string name;
    bool isAidlService;
};

class VtsVehicleCallback final : public ISubscriptionCallback {
  private:
    std::mutex mLock;
    std::unordered_map<int32_t, size_t> mEventsCount GUARDED_BY(mLock);
    std::unordered_map<int32_t, std::vector<int64_t>> mEventTimestamps GUARDED_BY(mLock);
    std::condition_variable mEventCond;

  public:
    void onPropertyEvent(const std::vector<std::unique_ptr<IHalPropValue>>& values) override {
        {
            std::lock_guard<std::mutex> lockGuard(mLock);
            for (auto& value : values) {
                int32_t propId = value->getPropId();
                mEventsCount[propId] += 1;
                mEventTimestamps[propId].push_back(value->getTimestamp());
            }
        }
        mEventCond.notify_one();
    }

    void onPropertySetError([[maybe_unused]] const std::vector<HalPropError>& errors) override {
        // Do nothing.
    }

    template <class Rep, class Period>
    bool waitForExpectedEvents(int32_t propId, size_t expectedEvents,
                               const std::chrono::duration<Rep, Period>& timeout) {
        std::unique_lock<std::mutex> uniqueLock(mLock);
        return mEventCond.wait_for(uniqueLock, timeout, [this, propId, expectedEvents] {
            ScopedLockAssertion lockAssertion(mLock);
            return mEventsCount[propId] >= expectedEvents;
        });
    }

    std::vector<int64_t> getEventTimestamps(int32_t propId) {
        {
            std::lock_guard<std::mutex> lockGuard(mLock);
            return mEventTimestamps[propId];
        }
    }

    void reset() {
        std::lock_guard<std::mutex> lockGuard(mLock);
        mEventsCount.clear();
    }
};

class VtsHalAutomotiveVehicleTargetTest : public testing::TestWithParam<ServiceDescriptor> {
  public:
    virtual void SetUp() override {
        auto descriptor = GetParam();
        if (descriptor.isAidlService) {
            mVhalClient = IVhalClient::tryCreateAidlClient(descriptor.name.c_str());
        } else {
            mVhalClient = IVhalClient::tryCreateHidlClient(descriptor.name.c_str());
        }

        ASSERT_NE(mVhalClient, nullptr) << "Failed to connect to VHAL";

        mCallback = std::make_shared<VtsVehicleCallback>();
    }

    static bool isBooleanGlobalProp(int32_t property) {
        return (property & toInt(VehiclePropertyType::MASK)) ==
                       toInt(VehiclePropertyType::BOOLEAN) &&
               (property & toInt(VehicleArea::MASK)) == toInt(VehicleArea::GLOBAL);
    }

  protected:
    std::shared_ptr<IVhalClient> mVhalClient;
    std::shared_ptr<VtsVehicleCallback> mCallback;
};

TEST_P(VtsHalAutomotiveVehicleTargetTest, useAidlBackend) {
    if (!mVhalClient->isAidlVhal()) {
        GTEST_SKIP() << "AIDL backend is not available, HIDL backend is used instead";
    }
}

TEST_P(VtsHalAutomotiveVehicleTargetTest, useHidlBackend) {
    if (mVhalClient->isAidlVhal()) {
        GTEST_SKIP() << "AIDL backend is available, HIDL backend is not used";
    }
}

// Test getAllPropConfig() returns at least 4 property configs.
TEST_P(VtsHalAutomotiveVehicleTargetTest, getAllPropConfigs) {
    ALOGD("VtsHalAutomotiveVehicleTargetTest::getAllPropConfigs");

    auto result = mVhalClient->getAllPropConfigs();

    ASSERT_TRUE(result.ok()) << "Failed to get all property configs, error: "
                             << result.error().message();
    ASSERT_GE(result.value().size(), 4u) << StringPrintf(
            "Expect to get at least 4 property configs, got %zu", result.value().size());
}

// Test getPropConfigs() can query all properties listed in CDD.
TEST_P(VtsHalAutomotiveVehicleTargetTest, getRequiredPropConfigs) {
    ALOGD("VtsHalAutomotiveVehicleTargetTest::getRequiredPropConfigs");

    // Check the properties listed in CDD
    std::vector<int32_t> properties = {
            toInt(VehicleProperty::GEAR_SELECTION), toInt(VehicleProperty::NIGHT_MODE),
            toInt(VehicleProperty::PARKING_BRAKE_ON), toInt(VehicleProperty::PERF_VEHICLE_SPEED)};

    auto result = mVhalClient->getPropConfigs(properties);

    ASSERT_TRUE(result.ok()) << "Failed to get required property config, error: "
                             << result.error().message();
    ASSERT_EQ(result.value().size(), 4u)
            << StringPrintf("Expect to get exactly 4 configs, got %zu", result.value().size());
}

// Test getPropConfig() with an invalid propertyId returns an error code.
TEST_P(VtsHalAutomotiveVehicleTargetTest, getPropConfigsWithInvalidProp) {
    ALOGD("VtsHalAutomotiveVehicleTargetTest::getPropConfigsWithInvalidProp");

    auto result = mVhalClient->getPropConfigs({kInvalidProp});

    ASSERT_FALSE(result.ok()) << StringPrintf(
            "Expect failure to get prop configs for invalid prop: %" PRId32, kInvalidProp);
    ASSERT_NE(result.error().message(), "") << "Expect error message not to be empty";
}

// Test get() return current value for properties.
TEST_P(VtsHalAutomotiveVehicleTargetTest, get) {
    ALOGD("VtsHalAutomotiveVehicleTargetTest::get");

    int32_t propId = toInt(VehicleProperty::PERF_VEHICLE_SPEED);
    auto result = mVhalClient->getValueSync(*mVhalClient->createHalPropValue(propId));

    ASSERT_TRUE(result.ok()) << StringPrintf("Failed to get value for property: %" PRId32
                                             ", error: %s",
                                             propId, result.error().message().c_str());
    ASSERT_NE(result.value(), nullptr) << "Result value must not be null";
}

// Test get() with an invalid propertyId return an error codes.
TEST_P(VtsHalAutomotiveVehicleTargetTest, getInvalidProp) {
    ALOGD("VtsHalAutomotiveVehicleTargetTest::getInvalidProp");

    auto result = mVhalClient->getValueSync(*mVhalClient->createHalPropValue(kInvalidProp));

    ASSERT_FALSE(result.ok()) << StringPrintf(
            "Expect failure to get property for invalid prop: %" PRId32, kInvalidProp);
}

// Test set() on read_write properties.
TEST_P(VtsHalAutomotiveVehicleTargetTest, setProp) {
    ALOGD("VtsHalAutomotiveVehicleTargetTest::setProp");

    // skip hvac related properties
    std::unordered_set<int32_t> hvacProps = {toInt(VehicleProperty::HVAC_DEFROSTER),
                                             toInt(VehicleProperty::HVAC_AC_ON),
                                             toInt(VehicleProperty::HVAC_MAX_AC_ON),
                                             toInt(VehicleProperty::HVAC_MAX_DEFROST_ON),
                                             toInt(VehicleProperty::HVAC_RECIRC_ON),
                                             toInt(VehicleProperty::HVAC_DUAL_ON),
                                             toInt(VehicleProperty::HVAC_AUTO_ON),
                                             toInt(VehicleProperty::HVAC_POWER_ON),
                                             toInt(VehicleProperty::HVAC_AUTO_RECIRC_ON),
                                             toInt(VehicleProperty::HVAC_ELECTRIC_DEFROSTER_ON)};
    auto result = mVhalClient->getAllPropConfigs();
    ASSERT_TRUE(result.ok());

    for (const auto& cfgPtr : result.value()) {
        const IHalPropConfig& cfg = *cfgPtr;
        int32_t propId = cfg.getPropId();
        // test on boolean and writable property
        if (cfg.getAccess() == toInt(VehiclePropertyAccess::READ_WRITE) &&
            isBooleanGlobalProp(propId) && !hvacProps.count(propId)) {
            auto propToGet = mVhalClient->createHalPropValue(propId);
            auto getValueResult = mVhalClient->getValueSync(*propToGet);

            ASSERT_TRUE(getValueResult.ok())
                    << StringPrintf("Failed to get value for property: %" PRId32 ", error: %s",
                                    propId, getValueResult.error().message().c_str());
            ASSERT_NE(getValueResult.value(), nullptr)
                    << StringPrintf("Result value must not be null for property: %" PRId32, propId);

            const IHalPropValue& value = *getValueResult.value();
            size_t intValueSize = value.getInt32Values().size();
            ASSERT_EQ(intValueSize, 1u) << StringPrintf(
                    "Expect exactly 1 int value for boolean property: %" PRId32 ", got %zu", propId,
                    intValueSize);

            int setValue = value.getInt32Values()[0] == 1 ? 0 : 1;
            auto propToSet = mVhalClient->createHalPropValue(propId);
            propToSet->setInt32Values({setValue});
            auto setValueResult = mVhalClient->setValueSync(*propToSet);

            ASSERT_TRUE(setValueResult.ok())
                    << StringPrintf("Failed to set value for property: %" PRId32 ", error: %s",
                                    propId, setValueResult.error().message().c_str());

            // check set success
            getValueResult = mVhalClient->getValueSync(*propToGet);
            ASSERT_TRUE(getValueResult.ok())
                    << StringPrintf("Failed to get value for property: %" PRId32 ", error: %s",
                                    propId, getValueResult.error().message().c_str());
            ASSERT_NE(getValueResult.value(), nullptr)
                    << StringPrintf("Result value must not be null for property: %" PRId32, propId);
            ASSERT_EQ(getValueResult.value()->getInt32Values(), std::vector<int32_t>({setValue}))
                    << StringPrintf("Boolean value not updated after set for property: %" PRId32,
                                    propId);
        }
    }
}

// Test set() on an read_only property.
TEST_P(VtsHalAutomotiveVehicleTargetTest, setNotWritableProp) {
    ALOGD("VtsHalAutomotiveVehicleTargetTest::setNotWritableProp");

    int32_t propId = toInt(VehicleProperty::PERF_VEHICLE_SPEED);
    auto getValueResult = mVhalClient->getValueSync(*mVhalClient->createHalPropValue(propId));
    ASSERT_TRUE(getValueResult.ok())
            << StringPrintf("Failed to get value for property: %" PRId32 ", error: %s", propId,
                            getValueResult.error().message().c_str());

    auto setValueResult = mVhalClient->setValueSync(*getValueResult.value());

    ASSERT_FALSE(setValueResult.ok()) << "Expect set a read-only value to fail";
    ASSERT_EQ(setValueResult.error().code(), StatusCode::ACCESS_DENIED);
}

// Test subscribe() and unsubscribe().
TEST_P(VtsHalAutomotiveVehicleTargetTest, subscribeAndUnsubscribe) {
    ALOGD("VtsHalAutomotiveVehicleTargetTest::subscribeAndUnsubscribe");

    int32_t propId = toInt(VehicleProperty::PERF_VEHICLE_SPEED);

    auto propConfigsResult = mVhalClient->getPropConfigs({propId});

    ASSERT_TRUE(propConfigsResult.ok()) << "Failed to get property config for PERF_VEHICLE_SPEED: "
                                        << "error: " << propConfigsResult.error().message();
    ASSERT_EQ(propConfigsResult.value().size(), 1u)
            << "Expect to return 1 config for PERF_VEHICLE_SPEED";
    auto& propConfig = propConfigsResult.value()[0];
    float minSampleRate = propConfig->getMinSampleRate();
    float maxSampleRate = propConfig->getMaxSampleRate();

    if (minSampleRate < 1) {
        GTEST_SKIP() << "Sample rate for vehicle speed < 1 times/sec, skip test since it would "
                        "take too long";
    }

    auto client = mVhalClient->getSubscriptionClient(mCallback);
    ASSERT_NE(client, nullptr) << "Failed to get subscription client";

    auto result = client->subscribe({{.propId = propId, .sampleRate = minSampleRate}});

    ASSERT_TRUE(result.ok()) << StringPrintf("Failed to subscribe to property: %" PRId32
                                             ", error: %s",
                                             propId, result.error().message().c_str());

    if (mVhalClient->isAidlVhal()) {
        // Skip checking timestamp for HIDL because the behavior for sample rate and timestamp is
        // only specified clearly for AIDL.

        // Timeout is 2 seconds, which gives a 1 second buffer.
        ASSERT_TRUE(mCallback->waitForExpectedEvents(propId, std::floor(minSampleRate),
                                                     std::chrono::seconds(2)))
                << "Didn't get enough events for subscribing to minSampleRate";
    }

    result = client->subscribe({{.propId = propId, .sampleRate = maxSampleRate}});

    ASSERT_TRUE(result.ok()) << StringPrintf("Failed to subscribe to property: %" PRId32
                                             ", error: %s",
                                             propId, result.error().message().c_str());

    if (mVhalClient->isAidlVhal()) {
        ASSERT_TRUE(mCallback->waitForExpectedEvents(propId, std::floor(maxSampleRate),
                                                     std::chrono::seconds(2)))
                << "Didn't get enough events for subscribing to maxSampleRate";

        std::unordered_set<int64_t> timestamps;
        // Event event should have a different timestamp.
        for (const int64_t& eventTimestamp : mCallback->getEventTimestamps(propId)) {
            ASSERT_TRUE(timestamps.find(eventTimestamp) == timestamps.end())
                    << "two events for the same property must not have the same timestamp";
            timestamps.insert(eventTimestamp);
        }
    }

    result = client->unsubscribe({propId});
    ASSERT_TRUE(result.ok()) << StringPrintf("Failed to unsubscribe to property: %" PRId32
                                             ", error: %s",
                                             propId, result.error().message().c_str());

    mCallback->reset();
    ASSERT_FALSE(mCallback->waitForExpectedEvents(propId, 10, std::chrono::seconds(1)))
            << "Expect not to get events after unsubscription";
}

// Test subscribe() with an invalid property.
TEST_P(VtsHalAutomotiveVehicleTargetTest, subscribeInvalidProp) {
    ALOGD("VtsHalAutomotiveVehicleTargetTest::subscribeInvalidProp");

    std::vector<SubscribeOptions> options = {
            SubscribeOptions{.propId = kInvalidProp, .sampleRate = 10.0}};

    auto client = mVhalClient->getSubscriptionClient(mCallback);
    ASSERT_NE(client, nullptr) << "Failed to get subscription client";

    auto result = client->subscribe(options);

    ASSERT_FALSE(result.ok()) << StringPrintf("Expect subscribing to property: %" PRId32 " to fail",
                                              kInvalidProp);
}

// Test the timestamp returned in GetValues results is the timestamp when the value is retrieved.
TEST_P(VtsHalAutomotiveVehicleTargetTest, testGetValuesTimestampAIDL) {
    if (!mVhalClient->isAidlVhal()) {
        GTEST_SKIP() << "Skip checking timestamp for HIDL because the behavior is only specified "
                        "for AIDL";
    }

    int32_t propId = toInt(VehicleProperty::PARKING_BRAKE_ON);
    auto prop = mVhalClient->createHalPropValue(propId);

    auto result = mVhalClient->getValueSync(*prop);

    ASSERT_TRUE(result.ok()) << StringPrintf("Failed to get value for property: %" PRId32
                                             ", error: %s",
                                             propId, result.error().message().c_str());
    ASSERT_NE(result.value(), nullptr) << "Result value must not be null";
    ASSERT_EQ(result.value()->getInt32Values().size(), 1u) << "Result must contain 1 int value";

    bool parkBrakeOnValue1 = (result.value()->getInt32Values()[0] == 1);
    int64_t timestampValue1 = result.value()->getTimestamp();

    result = mVhalClient->getValueSync(*prop);

    ASSERT_TRUE(result.ok()) << StringPrintf("Failed to get value for property: %" PRId32
                                             ", error: %s",
                                             propId, result.error().message().c_str());
    ASSERT_NE(result.value(), nullptr) << "Result value must not be null";
    ASSERT_EQ(result.value()->getInt32Values().size(), 1u) << "Result must contain 1 int value";

    bool parkBarkeOnValue2 = (result.value()->getInt32Values()[0] == 1);
    int64_t timestampValue2 = result.value()->getTimestamp();

    if (parkBarkeOnValue2 == parkBrakeOnValue1) {
        ASSERT_EQ(timestampValue2, timestampValue1)
                << "getValue result must contain a timestamp updated when the value was updated, if"
                   "the value does not change, expect the same timestamp";
    } else {
        ASSERT_GT(timestampValue2, timestampValue1)
                << "getValue result must contain a timestamp updated when the value was updated, if"
                   "the value changes, expect the newer value has a larger timestamp";
    }
}

std::vector<ServiceDescriptor> getDescriptors() {
    std::vector<ServiceDescriptor> descriptors;
    for (std::string name : getAidlHalInstanceNames(IVehicle::descriptor)) {
        descriptors.push_back({
                .name = name,
                .isAidlService = true,
        });
    }
    for (std::string name : getAllHalInstanceNames(IVehicle::descriptor)) {
        descriptors.push_back({
                .name = name,
                .isAidlService = false,
        });
    }
    return descriptors;
}

GTEST_ALLOW_UNINSTANTIATED_PARAMETERIZED_TEST(VtsHalAutomotiveVehicleTargetTest);

INSTANTIATE_TEST_SUITE_P(PerInstance, VtsHalAutomotiveVehicleTargetTest,
                         testing::ValuesIn(getDescriptors()),
                         [](const testing::TestParamInfo<ServiceDescriptor>& info) {
                             std::string name = "";
                             if (info.param.isAidlService) {
                                 name += "aidl_";
                             } else {
                                 name += "hidl_";
                             }
                             name += info.param.name;
                             return Sanitize(name);
                         });

int main(int argc, char** argv) {
    ::testing::InitGoogleTest(&argc, argv);
    ABinderProcess_setThreadPoolMaxThreadCount(1);
    return RUN_ALL_TESTS();
}
