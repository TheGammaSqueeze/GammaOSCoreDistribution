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

package com.android.compos;

/** {@hide} */
interface ICompOsService {
    /**
     * Initializes system properties. ART expects interesting properties that have to be passed from
     * Android. The API client should call this method once with all desired properties, since once
     * the call completes, the service is considered initialized and cannot be re-initialized again.
     *
     * <p>If the initialization failed, Microdroid may already have some properties set. It is up to
     * the service to reject further calls by the client.
     *
     * <p>The service may reject unrecognized names, but it does not interpret values.
     */
    void initializeSystemProperties(in String[] names, in String[] values);

    /**
     * What type of compilation to perform.
     */
    @Backing(type="int")
    enum CompilationMode {
        /** Compile artifacts required by the current set of APEXes for use on reboot. */
        NORMAL_COMPILE = 0,
        /** Compile a full set of artifacts for test purposes. */
        TEST_COMPILE = 1,
    }

    /**
     * Run odrefresh in the VM context.
     *
     * The execution is based on the VM's APEX mounts, files on Android's /system (by accessing
     * through systemDirFd over AuthFS), and *CLASSPATH derived in the VM, to generate the same
     * odrefresh output artifacts to the output directory (through outputDirFd).
     *
     * @param compilationMode The type of compilation to be performed
     * @param systemDirFd An fd referring to /system
     * @param outputDirFd An fd referring to the output directory, ART_APEX_DATA
     * @param stagingDirFd An fd referring to the staging directory, e.g. ART_APEX_DATA/staging
     * @param targetDirName The sub-directory of the output directory to which artifacts are to be
     *                      written (e.g. dalvik-cache)
     * @param zygoteArch The zygote architecture (ro.zygote)
     * @param systemServerCompilerFilter The compiler filter used to compile system server
     * @return odrefresh exit code
     */
    byte odrefresh(CompilationMode compilation_mode, int systemDirFd, int outputDirFd,
            int stagingDirFd, String targetDirName, String zygoteArch,
            String systemServerCompilerFilter);

    /**
     * Returns the current VM's signing key, as an Ed25519 public key
     * (https://datatracker.ietf.org/doc/html/rfc8032#section-5.1.5).
     */
    byte[] getPublicKey();
}
