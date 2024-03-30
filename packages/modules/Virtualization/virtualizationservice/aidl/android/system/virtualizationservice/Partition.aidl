/*
 * Copyright 2021 The Android Open Source Project
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
package android.system.virtualizationservice;

/** A partition to be assembled into a composite image. */
parcelable Partition {
    /** A label for the partition. */
    @utf8InCpp String label;

    /** The backing file descriptor of the partition image. */
    ParcelFileDescriptor image;

    /** Whether the partition should be writable by the VM. */
    boolean writable;
}
