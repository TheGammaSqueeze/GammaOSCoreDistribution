/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.google.android.car.kitchensink;

import android.annotation.Nullable;
import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.security.AttestedKeyPair;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.IndentingPrintWriter;
import android.util.Log;

import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * {@code KitchenSink}'s own {@code cmd} implementation.
 *
 * <p>Usage: {$code adb shell dumpsys activity
 * com.google.android.car.kitchensink/.KitchenSinkActivity cmd <CMD>}
 *
 * <p><p>Note</p>: you must launch {@code KitchenSink} first. Example: {@code
 * adb shell am start com.google.android.car.kitchensink/.KitchenSinkActivity}
 */
final class KitchenSinkShellCommand {

    private static final String TAG = "KitchenSinkCmd";

    private static final String CMD_HELP = "help";
    private static final String CMD_GET_DELEGATED_SCOPES = "get-delegated-scopes";
    private static final String CMD_IS_UNINSTALL_BLOCKED = "is-uninstall-blocked";
    private static final String CMD_SET_UNINSTALL_BLOCKED = "set-uninstall-blocked";
    private static final String CMD_GENERATE_DEVICE_ATTESTATION_KEY_PAIR =
            "generate-device-attestation-key-pair";

    private final Context mContext;
    private final @Nullable DevicePolicyManager mDpm;
    private final IndentingPrintWriter mWriter;
    private final String[] mArgs;

    @Nullable // dynamically created on post() method
    private Handler mHandler;

    private int mNextArgIndex;

    KitchenSinkShellCommand(Context context, PrintWriter writer, String[] args) {
        mContext = context;
        mDpm = context.getSystemService(DevicePolicyManager.class);
        mWriter = new IndentingPrintWriter(writer);
        mArgs = args;
    }

    void run() {
        if (mArgs.length == 0) {
            showHelp("Error: must pass an argument");
            return;
        }
        String cmd = mArgs[0];
        switch (cmd) {
            case CMD_HELP:
                showHelp("KitchenSink Command-Line Interface");
                break;
            case CMD_GET_DELEGATED_SCOPES:
                getDelegatedScopes();
                break;
            case CMD_IS_UNINSTALL_BLOCKED:
                isUninstallBlocked();
                break;
            case CMD_SET_UNINSTALL_BLOCKED:
                setUninstallBlocked();
                break;
            case CMD_GENERATE_DEVICE_ATTESTATION_KEY_PAIR:
                generateDeviceAttestationKeyPair();
                break;
            default:
                showHelp("Invalid command: %s", cmd);
        }
    }

    private void showHelp(String headerMessage, Object... headerArgs) {
        if (headerMessage != null) {
            mWriter.printf(headerMessage, headerArgs);
            mWriter.print(". ");
        }
        mWriter.println("Available commands:\n");

        mWriter.increaseIndent();
        showCommandHelp("Shows this help message.",
                CMD_HELP);
        showCommandHelp("Lists delegated scopes set by the device admin.",
                CMD_GET_DELEGATED_SCOPES);
        showCommandHelp("Checks whether uninstalling the given app is blocked.",
                CMD_IS_UNINSTALL_BLOCKED, "<PKG>");
        showCommandHelp("Blocks / unblocks uninstalling the given app.",
                CMD_SET_UNINSTALL_BLOCKED, "<PKG>", "<true|false>");
        showCommandHelp("Generates a device attestation key.",
                CMD_GENERATE_DEVICE_ATTESTATION_KEY_PAIR, "<ALIAS>", "[FLAGS]");
        mWriter.decreaseIndent();
    }

    private void showCommandHelp(String description, String cmd, String... args) {
        mWriter.printf("%s", cmd);
        if (args != null) {
            for (String arg : args) {
                mWriter.printf(" %s", arg);
            }
        }
        mWriter.println(":");
        mWriter.increaseIndent();
        mWriter.printf("%s\n\n", description);
        mWriter.decreaseIndent();
    }

    private void getDelegatedScopes() {
        if (!supportDevicePolicyManagement()) return;

        List<String> scopes = mDpm.getDelegatedScopes(/* admin= */ null, mContext.getPackageName());
        printCollection("delegated scope", scopes);
    }

    private void isUninstallBlocked() {
        if (!supportDevicePolicyManagement()) return;

        String packageName = getNextArg();
        boolean isIt = mDpm.isUninstallBlocked(/* admin= */ null, packageName);
        mWriter.println(isIt);
    }

    private void setUninstallBlocked() {
        if (!supportDevicePolicyManagement()) return;

        String packageName = getNextArg();
        boolean blocked = getNextBooleanArg();

        Log.i(TAG, "Calling dpm.setUninstallBlocked(" + packageName + ", " + blocked + ")");
        mDpm.setUninstallBlocked(/* admin= */ null, packageName, blocked);
    }

    private void generateDeviceAttestationKeyPair() {
        if (!supportDevicePolicyManagement()) return;

        String alias = getNextArg();
        int flags = getNextOptionalIntArg(/* defaultValue= */ 0);
        // Cannot call dpm.generateKeyPair() on main thread
        warnAboutAsyncCall();
        post(() -> handleDeviceAttestationKeyPair(alias, flags));
    }

    private void handleDeviceAttestationKeyPair(String alias, int flags) {
        KeyGenParameterSpec keySpec = buildRsaKeySpecWithKeyAttestation(alias);
        String algorithm = "RSA";
        Log.i(TAG, "calling dpm.generateKeyPair(alg=" + algorithm + ", spec=" + keySpec
                + ", flags=" + flags + ")");
        AttestedKeyPair kp = mDpm.generateKeyPair(/* admin= */ null, algorithm, keySpec, flags);
        Log.i(TAG, "key: " + kp);
    }

    private void warnAboutAsyncCall() {
        mWriter.printf("Command will be executed asynchronally; use `adb logcat %s *:s` for result"
                + "\n", TAG);
    }

    private void post(Runnable r) {
        if (mHandler == null) {
            HandlerThread handlerThread = new HandlerThread("KitchenSinkShellCommandThread");
            Log.i(TAG, "Starting " + handlerThread);
            handlerThread.start();
            mHandler = new Handler(handlerThread.getLooper());
        }
        Log.d(TAG, "posting runnable");
        mHandler.post(r);
    }

    private boolean supportDevicePolicyManagement() {
        if (mDpm == null) {
            mWriter.println("Device Policy Management not supported by device");
            return false;
        }
        return true;
    }

    private String getNextArgAndIncrementCounter() {
        return mArgs[++mNextArgIndex];
    }

    private String getNextArg() {
        try {
            return getNextArgAndIncrementCounter();
        } catch (Exception e) {
            mWriter.println("Error: missing argument");
            mWriter.flush();
            throw new IllegalArgumentException(
                    "Missing argument. Args=" + Arrays.toString(mArgs));
        }
    }

    private int getNextOptionalIntArg(int defaultValue) {
        try {
            return Integer.parseInt(getNextArgAndIncrementCounter());
        } catch (Exception e) {
            Log.d(TAG, "Exception getting optional arg: " + e);
            return defaultValue;
        }
    }

    private boolean getNextBooleanArg() {
        String arg = getNextArg();
        return Boolean.parseBoolean(arg);
    }

    private void printCollection(String nameOnSingular, Collection<String> collection) {
        if (collection.isEmpty()) {
            mWriter.printf("No %ss\n", nameOnSingular);
            return;
        }
        int size = collection.size();
        mWriter.printf("%d %s%s:\n", size, nameOnSingular, size == 1 ? "" : "s");
        collection.forEach((s) -> mWriter.printf("  %s\n", s));
    }

    // Copied from CTS' KeyGenerationUtils
    private static KeyGenParameterSpec buildRsaKeySpecWithKeyAttestation(String alias) {
        return new KeyGenParameterSpec.Builder(alias,
                KeyProperties.PURPOSE_SIGN | KeyProperties.PURPOSE_VERIFY)
                        .setKeySize(2048)
                        .setDigests(KeyProperties.DIGEST_SHA256)
                        .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PSS,
                                KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
                        .setIsStrongBoxBacked(false)
                        .setAttestationChallenge(new byte[] {
                                'a', 'b', 'c'
                        })
                        .build();
    }
}
