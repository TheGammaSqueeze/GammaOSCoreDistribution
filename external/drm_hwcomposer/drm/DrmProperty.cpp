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

// NOLINTNEXTLINE(cppcoreguidelines-macro-usage)
#define LOG_TAG "hwc-drm-property"

#include "DrmProperty.h"

#include <xf86drmMode.h>

#include <cerrno>
#include <cstdint>
#include <string>

#include "DrmDevice.h"
#include "utils/log.h"

namespace android {

DrmProperty::DrmPropertyEnum::DrmPropertyEnum(drm_mode_property_enum *e)
    : value_(e->value), name_(e->name) {
}

DrmProperty::DrmProperty(uint32_t obj_id, drmModePropertyPtr p,
                         uint64_t value) {
  Init(obj_id, p, value);
}

void DrmProperty::Init(uint32_t obj_id, drmModePropertyPtr p, uint64_t value) {
  obj_id_ = obj_id;
  id_ = p->prop_id;
  flags_ = p->flags;
  name_ = p->name;
  value_ = value;

  for (int i = 0; i < p->count_values; ++i)
    values_.emplace_back(p->values[i]);

  for (int i = 0; i < p->count_enums; ++i)
    enums_.emplace_back(DrmPropertyEnum(&p->enums[i]));

  for (int i = 0; i < p->count_blobs; ++i)
    blob_ids_.emplace_back(p->blob_ids[i]);

  if (flags_ & DRM_MODE_PROP_RANGE)
    type_ = DRM_PROPERTY_TYPE_INT;
  else if (flags_ & DRM_MODE_PROP_ENUM)
    type_ = DRM_PROPERTY_TYPE_ENUM;
  else if (flags_ & DRM_MODE_PROP_OBJECT)
    type_ = DRM_PROPERTY_TYPE_OBJECT;
  else if (flags_ & DRM_MODE_PROP_BLOB)
    type_ = DRM_PROPERTY_TYPE_BLOB;
  else if (flags_ & DRM_MODE_PROP_BITMASK)
    type_ = DRM_PROPERTY_TYPE_BITMASK;
}

uint32_t DrmProperty::id() const {
  return id_;
}

std::string DrmProperty::name() const {
  return name_;
}

std::tuple<int, uint64_t> DrmProperty::value() const {
  if (type_ == DRM_PROPERTY_TYPE_BLOB)
    return std::make_tuple(0, value_);

  if (values_.empty())
    return std::make_tuple(-ENOENT, 0);

  switch (type_) {
    case DRM_PROPERTY_TYPE_INT:
      return std::make_tuple(0, value_);

    case DRM_PROPERTY_TYPE_ENUM:
      if (value_ >= enums_.size())
        return std::make_tuple(-ENOENT, 0);

      return std::make_tuple(0, enums_[value_].value_);

    case DRM_PROPERTY_TYPE_OBJECT:
      return std::make_tuple(0, value_);

    case DRM_PROPERTY_TYPE_BITMASK:
    default:
      return std::make_tuple(-EINVAL, 0);
  }
}

bool DrmProperty::is_immutable() const {
  return id_ && (flags_ & DRM_MODE_PROP_IMMUTABLE);
}

bool DrmProperty::is_range() const {
  return id_ && (flags_ & DRM_MODE_PROP_RANGE);
}

std::tuple<int, uint64_t> DrmProperty::range_min() const {
  if (!is_range())
    return std::make_tuple(-EINVAL, 0);
  if (values_.empty())
    return std::make_tuple(-ENOENT, 0);

  return std::make_tuple(0, values_[0]);
}

std::tuple<int, uint64_t> DrmProperty::range_max() const {
  if (!is_range())
    return std::make_tuple(-EINVAL, 0);
  if (values_.size() < 2)
    return std::make_tuple(-ENOENT, 0);

  return std::make_tuple(0, values_[1]);
}

std::tuple<uint64_t, int> DrmProperty::GetEnumValueWithName(
    const std::string &name) const {
  for (const auto &it : enums_) {
    if (it.name_ == name) {
      return std::make_tuple(it.value_, 0);
    }
  }

  return std::make_tuple(UINT64_MAX, -EINVAL);
}

auto DrmProperty::AtomicSet(drmModeAtomicReq &pset, uint64_t value) const
    -> bool {
  if (id_ == 0) {
    ALOGE("AtomicSet() is called on non-initialized property!");
    return false;
  }
  if (drmModeAtomicAddProperty(&pset, obj_id_, id_, value) < 0) {
    ALOGE("Failed to add obj_id: %u, prop_id: %u (%s) to pset", obj_id_, id_,
          name_.c_str());
    return false;
  }
  return true;
}

}  // namespace android
