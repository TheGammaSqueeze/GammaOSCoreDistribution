/*
 * Copyright (C) 2020-2021 Arm Limited.
 * SPDX-License-Identifier: Apache-2.0
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

package arm.graphics;

/**
 * Used by IAllocator/IMapper (gralloc) to describe Arm compression strategies
 * For details query the buffer's DRM Modifier using the metadata type
 * aidl::android::hardware::graphics::common::StandardMetadataType::PIXEL_FORMAT_MODIFIER
 */
@VintfStability
@Backing(type="long")
enum Compression {
    /**
     * Arm Framebuffer Compression
     */
    AFBC = 0,
    /**
     * Arm Fixed Rate Compression
     */
    AFRC = 1,
}
