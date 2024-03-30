/*
 * Copyright (C) 2020 Google LLC. All rights reserved.
 *
 * Copyright (C) 2008 The Android Open Source Project
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

#ifndef GRALLOC_VENDOR_INTERFACE_H
#define GRALLOC_VENDOR_INTERFACE_H

#include <cutils/native_handle.h>
#include <sys/types.h>
#include <cstdint>
#include <system/graphics-base-v1.0.h>
#include <android/hardware/graphics/mapper/4.0/IMapper.h>


namespace android::hardware::graphics::allocator::priv {

struct Descriptor;
Descriptor *createDescriptor();
void deleteDescriptor(Descriptor *descriptor);

void setProducerUsage(Descriptor &descriptor, uint64_t usage);
void setConsumerUsage(Descriptor &descriptor, uint64_t usage);
void setPlaneCount(Descriptor &descriptor, int count);
void setPlane(Descriptor &descriptor, int index, int fd, size_t size, off_t offset, int stride_byte);
void setWidth(Descriptor &descriptor, int width);
void setHeight(Descriptor &descriptor, int height);
void setStridePixel(Descriptor &descriptor, int stride_pixel);
void setFormat(Descriptor &descriptor, int format);

buffer_handle_t createNativeHandle(const Descriptor &descriptor);

int freeImportedHandle(void *handle);

}  // namespace android::hardware::graphics::allocator::priv

#endif
