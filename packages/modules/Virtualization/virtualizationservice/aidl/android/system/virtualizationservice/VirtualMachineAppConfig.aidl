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

/** Configuration for running an App in a VM */
parcelable VirtualMachineAppConfig {
    /** Main APK */
    ParcelFileDescriptor apk;

    /** idsig for an APK */
    ParcelFileDescriptor idsig;

    /** Idsigs for the extra APKs. Must match with the extra_apks in the payload config. */
    List<ParcelFileDescriptor> extraIdsigs;

    /** instance.img that has per-instance data */
    ParcelFileDescriptor instanceImage;

    /** Path to a configuration in an APK. This is the actual configuration for a VM. */
    @utf8InCpp String configPath;

    enum DebugLevel {
        /** Not debuggable at all */
        NONE,
        /** Only the logs from app is shown */
        APP_ONLY,
        /**
         * Fully debuggable. All logs are shown, kernel messages are shown, and adb shell is
         * supported
         */
        FULL,
    }

    /** Debug level of the VM */
    DebugLevel debugLevel;

    /** Whether the VM should be a protected VM. */
    boolean protectedVm;

    /**
     * The amount of RAM to give the VM, in MiB. If this is 0 or negative then it will default to
     * the value in microdroid.json, if any, or the crosvm default.
     */
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
     * List of task profile names to apply for the VM
     */
    String[] taskProfiles;
}
