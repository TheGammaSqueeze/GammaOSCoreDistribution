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

package com.android.compatibility.common.util;

import static android.telephony.TelephonyManager.CarrierPrivilegesCallback;

import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.PersistableBundle;
import android.telephony.CarrierConfigManager;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Log;

import java.security.MessageDigest;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Utility to execute a code block with carrier privileges.
 *
 * <p>The utility methods contained in this class will release carrier privileges once the specified
 * task is completed.
 *
 * <p>Example:
 * <pre>
 *   CarrierPrivilegeUtils.withCarrierPrivileges(c, subId, () -> telephonyManager.setFoo(bar));
 * </pre>
 *
 * @see {@link TelephonyManager#hasCarrierPrivileges()}
 */
public final class CarrierPrivilegeUtils {
    private static final String TAG = CarrierPrivilegeUtils.class.getSimpleName();

    private static class CarrierPrivilegeChangeMonitor implements AutoCloseable {
        private final CountDownLatch mLatch = new CountDownLatch(1);
        private final Context mContext;
        private final int mSubId;
        private final boolean mGain;
        private final boolean mIsShell;
        private final TelephonyManager mTelephonyManager;
        private final CarrierPrivilegesCallback mCarrierPrivilegesCallback;

        /**
         * Construct a {@link CarrierPrivilegesCallback} to monitor carrier privileges change.
         * @param c context
         * @param subId subscriptionId to listen to
         * @param gain true if wait to grant carrier privileges, false if wait to revoke
         * @param isShell true if the caller is Shell
         */
        CarrierPrivilegeChangeMonitor(Context c, int subId, boolean gain, boolean isShell) {
            mContext = c;
            mSubId = subId;
            mGain = gain;
            mIsShell = isShell;
            mTelephonyManager = mContext.getSystemService(
                    TelephonyManager.class).createForSubscriptionId(subId);
            Objects.requireNonNull(mTelephonyManager);

            mCarrierPrivilegesCallback = (privilegedPackageNames, privilegedUids) -> {
                if (mTelephonyManager.hasCarrierPrivileges() == mGain) {
                    mLatch.countDown();
                }
            };

            // Run with shell identify only when caller is not Shell to avoid overriding current
            // SHELL permissions
            if (mIsShell) {
                mTelephonyManager.registerCarrierPrivilegesCallback(
                        SubscriptionManager.getSlotIndex(subId),
                        mContext.getMainExecutor(),
                        mCarrierPrivilegesCallback);
            } else {
                runWithShellPermissionIdentity(() -> {
                    mTelephonyManager.registerCarrierPrivilegesCallback(
                            SubscriptionManager.getSlotIndex(subId),
                            mContext.getMainExecutor(),
                            mCarrierPrivilegesCallback);
                }, Manifest.permission.READ_PRIVILEGED_PHONE_STATE);
            }
        }

        @Override
        public void close() {
            if (mTelephonyManager == null) return;

            if (mIsShell) {
                mTelephonyManager.unregisterCarrierPrivilegesCallback(mCarrierPrivilegesCallback);
            } else {
                runWithShellPermissionIdentity(
                        () -> mTelephonyManager.unregisterCarrierPrivilegesCallback(
                                mCarrierPrivilegesCallback),
                        Manifest.permission.READ_PRIVILEGED_PHONE_STATE);
            }
        }

        public void waitForCarrierPrivilegeChanged() throws Exception {
            if (!mLatch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Failed to update carrier privileges");
            }
        }
    }

    private static boolean hasCarrierPrivileges(Context c, int subId) {
        // Synchronously check for carrier privileges. Checking certificates MAY be incorrect if
        // broadcasts are delayed.
        return c.getSystemService(TelephonyManager.class)
                .createForSubscriptionId(subId)
                .hasCarrierPrivileges();
    }

    private static String getCertHashForThisPackage(final Context c) throws Exception {
        final PackageInfo pkgInfo = c.getPackageManager()
                .getPackageInfo(c.getOpPackageName(), PackageManager.GET_SIGNATURES);
        final MessageDigest md = MessageDigest.getInstance("SHA-256");
        final byte[] certHash = md.digest(pkgInfo.signatures[0].toByteArray());
        return UiccUtil.bytesToHexString(certHash);
    }

    private static void changeCarrierPrivileges(Context c, int subId, boolean gain, boolean isShell)
            throws Exception {
        if (hasCarrierPrivileges(c, subId) == gain) {
            Log.w(TAG, "Carrier privileges already " + (gain ? "granted" : "revoked") + "; bug?");
            return;
        }

        final String certHash = getCertHashForThisPackage(c);
        final PersistableBundle carrierConfigs;

        if (gain) {
            carrierConfigs = new PersistableBundle();
            carrierConfigs.putStringArray(
                    CarrierConfigManager.KEY_CARRIER_CERTIFICATE_STRING_ARRAY,
                    new String[] {certHash});
        } else {
            carrierConfigs = null;
        }

        final CarrierConfigManager configManager = c.getSystemService(CarrierConfigManager.class);

        try (CarrierPrivilegeChangeMonitor monitor =
                     new CarrierPrivilegeChangeMonitor(c, subId, gain, isShell)) {
            // If the caller is the shell, it's dangerous to adopt shell permission identity for
            // the CarrierConfig override (as it will override the existing shell permissions).
            if (isShell) {
                configManager.overrideConfig(subId, carrierConfigs);
            } else {
                runWithShellPermissionIdentity(() -> {
                    configManager.overrideConfig(subId, carrierConfigs);
                }, android.Manifest.permission.MODIFY_PHONE_STATE);
            }

            monitor.waitForCarrierPrivilegeChanged();
        }
    }

    public static void withCarrierPrivileges(Context c, int subId, ThrowingRunnable action)
            throws Exception {
        try {
            changeCarrierPrivileges(c, subId, true /* gain */, false /* isShell */);
            action.run();
        } finally {
            changeCarrierPrivileges(c, subId, false /* lose */, false /* isShell */);
        }
    }

    /** Completes the provided action while assuming the caller is the Shell. */
    public static void withCarrierPrivilegesForShell(Context c, int subId, ThrowingRunnable action)
            throws Exception {
        try {
            changeCarrierPrivileges(c, subId, true /* gain */, true /* isShell */);
            action.run();
        } finally {
            changeCarrierPrivileges(c, subId, false /* lose */, true /* isShell */);
        }
    }

    public static <R> R withCarrierPrivileges(Context c, int subId, ThrowingSupplier<R> action)
            throws Exception {
        try {
            changeCarrierPrivileges(c, subId, true /* gain */, false /* isShell */);
            return action.get();
        } finally {
            changeCarrierPrivileges(c, subId, false /* lose */, false /* isShell */);
        }
    }
}
