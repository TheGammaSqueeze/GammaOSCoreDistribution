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

import android.system.virtualizationservice.DiskImage;

/** Raw configuration for running a VM. */
parcelable VirtualMachineRawConfig {
    /** The kernel image, if any. */
    @nullable ParcelFileDescriptor kernel;

    /** The initial ramdisk for the kernel, if any. */
    @nullable ParcelFileDescriptor initrd;

    /**
     * Parameters to pass to the kernel. As far as the VMM and boot protocol are concerned this is
     * just a string, but typically it will contain multiple parameters separated by spaces.
     */
    @nullable @utf8InCpp String params;

    /**
     * The bootloader to use. If this is supplied then the kernel and initrd must not be supplied;
     * the bootloader is instead responsibly for loading the kernel from one of the disks.
     */
    @nullable ParcelFileDescriptor bootloader;

    /** Disk images to be made available to the VM. */
    DiskImage[] disks;

    /** Whether the VM should be a protected VM. */
    boolean protectedVm;

    /** The amount of RAM to give the VM, in MiB. 0 or negative to use the default. */
    int memoryMib;

    /**
     * Number of vCPUs in the VM. Defaults to 1.
     */
    int numCpus = 1;

    /**
     * Comma-separated list of CPUs or CPU ranges to run vCPUs on (e.g. 0,1-3,5), or
     * colon-separated list of assignments of vCPU to host CPU assignments (e.g. 0=0:1=1:2=2).
     * Default is no mask which means a vCPU can run on any host CPU.
     */
    @nullable String cpuAffinity;

    /**
     * A version or range of versions of the virtual platform that this config is compatible with.
     * The format follows SemVer.
     */
    @utf8InCpp String platformVersion;

    /**
     * List of task profile names to apply for the VM
     */
    String[] taskProfiles;
}
