/*
 * Copyright (c) 2022, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#ifndef CPP_VHAL_CLIENT_INCLUDE_IVHALCLIENT_H_
#define CPP_VHAL_CLIENT_INCLUDE_IVHALCLIENT_H_

#include "IHalPropConfig.h"
#include "IHalPropValue.h"

#include <aidl/android/hardware/automotive/vehicle/StatusCode.h>
#include <aidl/android/hardware/automotive/vehicle/SubscribeOptions.h>
#include <android-base/result.h>

#include <VehicleUtils.h>

namespace android {
namespace frameworks {
namespace automotive {
namespace vhal {

struct HalPropError {
    int32_t propId;
    int32_t areaId;
    aidl::android::hardware::automotive::vehicle::StatusCode status;
};

// ISubscriptionCallback is a general interface to delivery property events caused by subscription.
class ISubscriptionCallback {
public:
    virtual ~ISubscriptionCallback() = default;
    /**
     * Called when new property events happen.
     */
    virtual void onPropertyEvent(const std::vector<std::unique_ptr<IHalPropValue>>& values) = 0;

    /**
     * Called when property set errors happen.
     */
    virtual void onPropertySetError(const std::vector<HalPropError>& errors) = 0;
};

// ISubscriptionCallback is a client that could be used to subscribe/unsubscribe.
class ISubscriptionClient {
public:
    virtual ~ISubscriptionClient() = default;

    virtual android::hardware::automotive::vehicle::VhalResult<void> subscribe(
            const std::vector<aidl::android::hardware::automotive::vehicle::SubscribeOptions>&
                    options) = 0;

    virtual android::hardware::automotive::vehicle::VhalResult<void> unsubscribe(
            const std::vector<int32_t>& propIds) = 0;
};

// IVhalClient is a thread-safe client for AIDL or HIDL VHAL backend.
class IVhalClient {
public:
    // Wait for VHAL service and create a client. Return nullptr if failed to connect to VHAL.
    static std::shared_ptr<IVhalClient> create();

    // Try to get the VHAL service and create a client. Return nullptr if failed to connect to VHAL.
    static std::shared_ptr<IVhalClient> tryCreate();

    // Try to create a client based on the AIDL VHAL service descriptor.
    static std::shared_ptr<IVhalClient> tryCreateAidlClient(const char* descriptor);

    // Try to create a client based on the HIDL VHAL service descriptor.
    static std::shared_ptr<IVhalClient> tryCreateHidlClient(const char* descriptor);

    // The default timeout for callbacks.
    constexpr static int64_t DEFAULT_TIMEOUT_IN_SEC = 10;

    virtual ~IVhalClient() = default;

    using GetValueCallbackFunc = std::function<void(
            android::hardware::automotive::vehicle::VhalResult<std::unique_ptr<IHalPropValue>>)>;
    using SetValueCallbackFunc =
            std::function<void(android::hardware::automotive::vehicle::VhalResult<void>)>;
    using OnBinderDiedCallbackFunc = std::function<void()>;

    /**
     * Check whether we are connected to AIDL VHAL backend.
     *
     * Returns {@code true} if we are connected to AIDL VHAL backend, {@code false} if we are
     * connected to HIDL backend.
     */
    virtual bool isAidlVhal() = 0;

    /**
     * Create a new {@code IHalpropValue}.
     *
     * @param propId The property ID.
     * @return The created {@code IHalPropValue}.
     */
    virtual std::unique_ptr<IHalPropValue> createHalPropValue(int32_t propId) = 0;

    /**
     * Create a new {@code IHalpropValue}.
     *
     * @param propId The property ID.
     * @param areaId The area ID for the property.
     * @return The created {@code IHalPropValue}.
     */
    virtual std::unique_ptr<IHalPropValue> createHalPropValue(int32_t propId, int32_t areaId) = 0;

    /**
     * Get a property value asynchronously.
     *
     * @param requestValue The value to request.
     * @param callback The callback that would be called when the result is ready. The callback
     *    would be called with an okay result with the got value inside on success. The callback
     *    would be called with an error result with error code as the returned status code on
     *    failure.
     */
    virtual void getValue(const IHalPropValue& requestValue,
                          std::shared_ptr<GetValueCallbackFunc> callback) = 0;

    /**
     * Get a property value synchronously.
     *
     * @param requestValue the value to request.
     * @return An okay result with the returned value on success or an error result with returned
     *    status code as error code. For AIDL backend, this would return TRY_AGAIN error on timeout.
     *    For HIDL backend, because HIDL backend is synchronous, timeout does not apply.
     */
    virtual android::hardware::automotive::vehicle::VhalResult<std::unique_ptr<IHalPropValue>>
    getValueSync(const IHalPropValue& requestValue);

    /**
     * Set a property value asynchronously.
     *
     * @param requestValue The value to set.
     * @param callback The callback that would be called when the request is processed. The callback
     *    would be called with an empty okay result on success. The callback would be called with
     *    an error result with error code as the returned status code on failure.
     */
    virtual void setValue(const IHalPropValue& requestValue,
                          std::shared_ptr<SetValueCallbackFunc> callback) = 0;

    /**
     * Set a property value synchronously.
     *
     * @param requestValue the value to set.
     * @return An empty okay result on success or an error result with returned status code as
     *    error code. For AIDL backend, this would return TRY_AGAIN error on timeout.
     *    For HIDL backend, because HIDL backend is synchronous, timeout does not apply.
     */
    virtual android::hardware::automotive::vehicle::VhalResult<void> setValueSync(
            const IHalPropValue& requestValue);

    /**
     * Add a callback that would be called when the binder connection to VHAL died.
     *
     * @param callback The callback that would be called when the binder died.
     * @return An okay result on success or an error on failure.
     */
    virtual android::hardware::automotive::vehicle::VhalResult<void> addOnBinderDiedCallback(
            std::shared_ptr<OnBinderDiedCallbackFunc> callback) = 0;

    /**
     * Remove a previously added OnBinderDied callback.
     *
     * @param callback The callback that would be removed.
     * @return An okay result on success, or an error if the callback is not added before.
     */
    virtual android::hardware::automotive::vehicle::VhalResult<void> removeOnBinderDiedCallback(
            std::shared_ptr<OnBinderDiedCallbackFunc> callback) = 0;

    /**
     * Get all the property configurations.
     *
     * @return An okay result that contains all property configs on success or an error on failure.
     */
    virtual android::hardware::automotive::vehicle::VhalResult<
            std::vector<std::unique_ptr<IHalPropConfig>>>
    getAllPropConfigs() = 0;

    /**
     * Get the configs for specified properties.
     *
     * @param propIds A list of property IDs to get configs for.
     * @return An okay result that contains property configs for specified properties on success or
     *    an error if failed to get any of the property configs.
     */
    virtual android::hardware::automotive::vehicle::VhalResult<
            std::vector<std::unique_ptr<IHalPropConfig>>>
    getPropConfigs(std::vector<int32_t> propIds) = 0;

    /**
     * Get a {@code ISubscriptionClient} that could be used to subscribe/unsubscribe to properties.
     *
     * @param callback The callback that would be called when property event happens.
     * @return A {@code ISubscriptionClient} used to subscribe/unsubscribe.
     */
    virtual std::unique_ptr<ISubscriptionClient> getSubscriptionClient(
            std::shared_ptr<ISubscriptionCallback> callback) = 0;
};

}  // namespace vhal
}  // namespace automotive
}  // namespace frameworks
}  // namespace android

#endif  // CPP_VHAL_CLIENT_INCLUDE_IVHALCLIENT_H_
