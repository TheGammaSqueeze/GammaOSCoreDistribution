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

#define LOG_TAG "VehicleObjectPool"

#include <VehicleObjectPool.h>

#include <VehicleUtils.h>

#include <assert.h>
#include <utils/Log.h>

namespace android {
namespace hardware {
namespace automotive {
namespace vehicle {

using ::aidl::android::hardware::automotive::vehicle::RawPropValues;
using ::aidl::android::hardware::automotive::vehicle::VehicleProperty;
using ::aidl::android::hardware::automotive::vehicle::VehiclePropertyType;
using ::aidl::android::hardware::automotive::vehicle::VehiclePropValue;

VehiclePropValuePool::RecyclableType VehiclePropValuePool::obtain(VehiclePropertyType type) {
    if (isComplexType(type)) {
        return obtain(type, 0);
    }
    return obtain(type, 1);
}

VehiclePropValuePool::RecyclableType VehiclePropValuePool::obtain(VehiclePropertyType type,
                                                                  size_t vectorSize) {
    if (isSingleValueType(type)) {
        vectorSize = 1;
    } else if (isComplexType(type)) {
        vectorSize = 0;
    }
    return isDisposable(type, vectorSize) ? obtainDisposable(type, vectorSize)
                                          : obtainRecyclable(type, vectorSize);
}

VehiclePropValuePool::RecyclableType VehiclePropValuePool::obtain(const VehiclePropValue& src) {
    int propId = src.prop;
    VehiclePropertyType type = getPropType(propId);
    size_t vectorSize = getVehicleRawValueVectorSize(src.value, type);
    if (vectorSize == 0 && !isComplexType(type)) {
        ALOGW("empty vehicle prop value, contains no content");
        ALOGW("empty vehicle prop value, contains no content, prop: %d", propId);
        // Return any empty VehiclePropValue.
        return RecyclableType{new VehiclePropValue{}, mDisposableDeleter};
    }

    auto dest = obtain(type, vectorSize);

    dest->prop = propId;
    dest->areaId = src.areaId;
    dest->status = src.status;
    dest->timestamp = src.timestamp;
    copyVehicleRawValue(&dest->value, src.value);

    return dest;
}

VehiclePropValuePool::RecyclableType VehiclePropValuePool::obtainInt32(int32_t value) {
    auto val = obtain(VehiclePropertyType::INT32);
    val->value.int32Values[0] = value;
    return val;
}

VehiclePropValuePool::RecyclableType VehiclePropValuePool::obtainInt64(int64_t value) {
    auto val = obtain(VehiclePropertyType::INT64);
    val->value.int64Values[0] = value;
    return val;
}

VehiclePropValuePool::RecyclableType VehiclePropValuePool::obtainFloat(float value) {
    auto val = obtain(VehiclePropertyType::FLOAT);
    val->value.floatValues[0] = value;
    return val;
}

VehiclePropValuePool::RecyclableType VehiclePropValuePool::obtainString(const char* cstr) {
    auto val = obtain(VehiclePropertyType::STRING);
    val->value.stringValue = cstr;
    return val;
}

VehiclePropValuePool::RecyclableType VehiclePropValuePool::obtainComplex() {
    return obtain(VehiclePropertyType::MIXED);
}

VehiclePropValuePool::RecyclableType VehiclePropValuePool::obtainRecyclable(
        VehiclePropertyType type, size_t vectorSize) {
    std::scoped_lock<std::mutex> lock(mLock);
    assert(vectorSize > 0);

    // VehiclePropertyType is not overlapping with vectorSize.
    int32_t key = static_cast<int32_t>(type) | static_cast<int32_t>(vectorSize);
    auto it = mValueTypePools.find(key);

    if (it == mValueTypePools.end()) {
        auto newPool(std::make_unique<InternalPool>(type, vectorSize, mMaxPoolObjectsSize,
                                                    getVehiclePropValueSize));
        it = mValueTypePools.emplace(key, std::move(newPool)).first;
    }
    return it->second->obtain();
}

VehiclePropValuePool::RecyclableType VehiclePropValuePool::obtainBoolean(bool value) {
    return obtainInt32(value);
}

VehiclePropValuePool::RecyclableType VehiclePropValuePool::obtainDisposable(
        VehiclePropertyType valueType, size_t vectorSize) const {
    return RecyclableType{createVehiclePropValueVec(valueType, vectorSize).release(),
                          mDisposableDeleter};
}

void VehiclePropValuePool::InternalPool::recycle(VehiclePropValue* o) {
    if (o == nullptr) {
        ALOGE("Attempt to recycle nullptr");
        return;
    }

    if (!check(&o->value)) {
        ALOGE("Discarding value for prop 0x%x because it contains "
              "data that is not consistent with this pool. "
              "Expected type: %d, vector size: %zu",
              o->prop, toInt(mPropType), mVectorSize);
        delete o;
    } else {
        ObjectPool<VehiclePropValue>::recycle(o);
    }
}

bool VehiclePropValuePool::InternalPool::check(RawPropValues* v) {
    return check(&v->int32Values, (VehiclePropertyType::INT32 == mPropType ||
                                   VehiclePropertyType::INT32_VEC == mPropType ||
                                   VehiclePropertyType::BOOLEAN == mPropType)) &&
           check(&v->floatValues, (VehiclePropertyType::FLOAT == mPropType ||
                                   VehiclePropertyType::FLOAT_VEC == mPropType)) &&
           check(&v->int64Values, (VehiclePropertyType::INT64 == mPropType ||
                                   VehiclePropertyType::INT64_VEC == mPropType)) &&
           check(&v->byteValues, VehiclePropertyType::BYTES == mPropType) &&
           v->stringValue.size() == 0;
}

VehiclePropValue* VehiclePropValuePool::InternalPool::createObject() {
    return createVehiclePropValueVec(mPropType, mVectorSize).release();
}

}  // namespace vehicle
}  // namespace automotive
}  // namespace hardware
}  // namespace android
