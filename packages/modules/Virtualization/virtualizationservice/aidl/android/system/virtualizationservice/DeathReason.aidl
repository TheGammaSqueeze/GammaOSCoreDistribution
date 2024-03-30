/*
 * Copyright 2022 The Android Open Source Project
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

/**
 * The reason why a VM died.
 */
@Backing(type="int")
enum DeathReason {
    /** There was an error waiting for the VM. */
    INFRASTRUCTURE_ERROR = 0,
    /** The VM was killed. */
    KILLED = 1,
    /** The VM died for an unknown reason. */
    UNKNOWN = 2,
    /** The VM requested to shut down. */
    SHUTDOWN = 3,
    /** crosvm had an error starting the VM. */
    ERROR = 4,
    /** The VM requested to reboot, possibly as the result of a kernel panic. */
    REBOOT = 5,
    /** The VM or crosvm crashed. */
    CRASH = 6,
    /** The pVM firmware failed to verify the VM because the public key doesn't match. */
    PVM_FIRMWARE_PUBLIC_KEY_MISMATCH = 7,
    /** The pVM firmware failed to verify the VM because the instance image changed. */
    PVM_FIRMWARE_INSTANCE_IMAGE_CHANGED = 8,
    /** The bootloader failed to verify the VM because the public key doesn't match. */
    BOOTLOADER_PUBLIC_KEY_MISMATCH = 9,
    /** The bootloader failed to verify the VM because the instance image changed. */
    BOOTLOADER_INSTANCE_IMAGE_CHANGED = 10,
}
