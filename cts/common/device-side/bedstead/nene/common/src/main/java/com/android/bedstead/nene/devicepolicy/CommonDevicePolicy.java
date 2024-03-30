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

package com.android.bedstead.nene.devicepolicy;

/** Device Policy helper methods common to host and device. */
public class CommonDevicePolicy {
    CommonDevicePolicy() {

    }

    /** See {@code DevicePolicyManager#DELEGATION_CERT_INSTALL}. */
    public static final String DELEGATION_CERT_INSTALL = "delegation-cert-install";

    /** See {@code DevicePolicyManager#DELEGATION_APP_RESTRICTIONS}. */
    public static final String DELEGATION_APP_RESTRICTIONS = "delegation-app-restrictions";

    /** See {@code DevicePolicyManager#DELEGATION_BLOCK_UNINSTALL}. */
    public static final String DELEGATION_BLOCK_UNINSTALL = "delegation-block-uninstall";

    /** See {@code DevicePolicyManager#DELEGATION_PERMISSION_GRANT}. */
    public static final String DELEGATION_PERMISSION_GRANT = "delegation-permission-grant";

    /** See {@code DevicePolicyManager#DELEGATION_PACKAGE_ACCESS}. */
    public static final String DELEGATION_PACKAGE_ACCESS = "delegation-package-access";

    /** See {@code DevicePolicyManager#DELEGATION_ENABLE_SYSTEM_APP}. */
    public static final String DELEGATION_ENABLE_SYSTEM_APP = "delegation-enable-system-app";

    /** See {@code DevicePolicyManager#DELEGATION_INSTALL_EXISTING_PACKAGE}. */
    public static final String DELEGATION_INSTALL_EXISTING_PACKAGE =
            "delegation-install-existing-package";

    /** See {@code DevicePolicyManager#DELEGATION_KEEP_UNINSTALLED_PACKAGES}. */
    public static final String DELEGATION_KEEP_UNINSTALLED_PACKAGES =
            "delegation-keep-uninstalled-packages";

    /** See {@code DevicePolicyManager#DELEGATION_NETWORK_LOGGING}. */
    public static final String DELEGATION_NETWORK_LOGGING = "delegation-network-logging";

    /** See {@code DevicePolicyManager#DELEGATION_CERT_SELECTION}. */
    public static final String DELEGATION_CERT_SELECTION = "delegation-cert-selection";

    /** See {@code DevicePolicyManager#DELEGATION_SECURITY_LOGGING}. */
    public static final String DELEGATION_SECURITY_LOGGING = "delegation-security-logging";
}
