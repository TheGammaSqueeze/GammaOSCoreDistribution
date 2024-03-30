/*
 * Copyright (C) 2015 The Android Open Source Project
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

#ifndef ANDROID_DRM_PROPERTY_H_
#define ANDROID_DRM_PROPERTY_H_

#include <xf86drmMode.h>

#include <cstdint>
#include <map>
#include <string>
#include <vector>

namespace android {

enum DrmPropertyType {
  DRM_PROPERTY_TYPE_INT,
  DRM_PROPERTY_TYPE_ENUM,
  DRM_PROPERTY_TYPE_OBJECT,
  DRM_PROPERTY_TYPE_BLOB,
  DRM_PROPERTY_TYPE_BITMASK,
  DRM_PROPERTY_TYPE_INVALID,
};

class DrmProperty {
 public:
  DrmProperty() = default;
  DrmProperty(uint32_t obj_id, drmModePropertyPtr p, uint64_t value);
  DrmProperty(const DrmProperty &) = delete;
  DrmProperty &operator=(const DrmProperty &) = delete;

  auto Init(uint32_t obj_id, drmModePropertyPtr p, uint64_t value) -> void;
  std::tuple<uint64_t, int> GetEnumValueWithName(const std::string &name) const;

  uint32_t id() const;
  std::string name() const;

  std::tuple<int, uint64_t> value() const;
  bool is_immutable() const;

  bool is_range() const;
  std::tuple<int, uint64_t> range_min() const;
  std::tuple<int, uint64_t> range_max() const;

  [[nodiscard]] auto AtomicSet(drmModeAtomicReq &pset, uint64_t value) const
      -> bool;

  template <class E>
  auto AddEnumToMap(const std::string &name, E key, std::map<E, uint64_t> &map)
      -> bool;

  explicit operator bool() const {
    return id_ != 0;
  }

 private:
  class DrmPropertyEnum {
   public:
    explicit DrmPropertyEnum(drm_mode_property_enum *e);
    ~DrmPropertyEnum() = default;

    uint64_t value_;
    std::string name_;
  };

  uint32_t obj_id_ = 0;
  uint32_t id_ = 0;

  DrmPropertyType type_ = DRM_PROPERTY_TYPE_INVALID;
  uint32_t flags_ = 0;
  std::string name_;
  uint64_t value_ = 0;

  std::vector<uint64_t> values_;
  std::vector<DrmPropertyEnum> enums_;
  std::vector<uint32_t> blob_ids_;
};

template <class E>
auto DrmProperty::AddEnumToMap(const std::string &name, E key,
                               std::map<E, uint64_t> &map) -> bool {
  uint64_t enum_value = UINT64_MAX;
  int err = 0;
  std::tie(enum_value, err) = GetEnumValueWithName(name);
  if (err == 0) {
    map[key] = enum_value;
    return true;
  }

  return false;
}

}  // namespace android

#endif  // ANDROID_DRM_PROPERTY_H_
