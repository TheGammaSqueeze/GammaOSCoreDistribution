/*
 * Copyright (C) 2017 The Android Open Source Project
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

#define LOG_TAG "OperationsUtils"

#include "OperationsUtils.h"

#include <android-base/logging.h>

#include <vector>

#include "nnapi/Validation.h"

namespace android::nn {

bool SameShape(const Shape& in1, const Shape& in2) {
    if (in1.type != in2.type || in1.dimensions.size() != in2.dimensions.size()) {
        return false;
    }
    for (size_t i = 0; i < in1.dimensions.size(); i++) {
        if (in1.dimensions[i] != in2.dimensions[i]) {
            return false;
        }
    }
    return true;
}

bool SetShape(const Shape& in, Shape* out) {
    if (in.type != out->type) {
        return false;
    }
    out->dimensions = in.dimensions;
    return true;
}

uint32_t getNumberOfElements(const Shape& shape) {
    uint32_t count = 1;
    for (size_t i = 0; i < shape.dimensions.size(); i++) {
        count *= shape.dimensions[i];
    }
    return count;
}

uint32_t getNumberOfElements(const Shape& shape, size_t firstAxisInclusive,
                             size_t lastAxisExclusive) {
    CHECK_LE(0u, firstAxisInclusive);
    CHECK_LE(firstAxisInclusive, lastAxisExclusive);
    CHECK_LE(lastAxisExclusive, shape.dimensions.size());
    uint32_t count = 1;
    for (size_t i = firstAxisInclusive; i < lastAxisExclusive; i++) {
        count *= shape.dimensions[i];
    }
    return count;
}

uint32_t getNumberOfDimensions(const Shape& shape) {
    return shape.dimensions.size();
}

uint32_t getSizeOfDimension(const Shape& shape, uint32_t dimensionIdx) {
    CHECK(0 <= dimensionIdx && dimensionIdx < shape.dimensions.size());
    return shape.dimensions[dimensionIdx];
}

uint32_t hasKnownRank(const Shape& shape) {
    return !shape.dimensions.empty();
}

}  // namespace android::nn
