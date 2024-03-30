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

package android.app.cts;

import static android.app.cts.ActivityManagerFgsBgStartTest.PACKAGE_NAME_APP1;
import static android.app.cts.ActivityManagerFgsBgStartTest.PACKAGE_NAME_APP2;
import static android.app.cts.ActivityManagerFgsBgStartTest.WAITFOR_MSEC;
import static android.app.stubs.LocalForegroundService.ACTION_START_FGS_RESULT;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;

import static org.junit.Assert.assertThrows;

import android.app.BroadcastOptions;
import android.app.Instrumentation;
import android.app.cts.android.app.cts.tools.WaitForBroadcast;
import android.app.stubs.CommandReceiver;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerExemptionManager;

import androidx.test.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.compatibility.common.util.SystemUtil;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class BroadcastOptionsTest {

    /**
     * Creates a clone of BroadcastOptions, using toBundle().
     */
    private BroadcastOptions cloneViaBundle(BroadcastOptions bo) {
        final Bundle b = bo.toBundle();

        // If toBundle() returns null, that means the BroadcastOptions was the default values.
        return b == null ? BroadcastOptions.makeBasic() : new BroadcastOptions(b);
    }

    private void assertBroadcastOptionTemporaryAppAllowList(
            BroadcastOptions bo,
            long expectedDuration,
            int expectedAllowListType,
            int expectedReasonCode,
            String expectedReason) {
        assertEquals(expectedAllowListType, bo.getTemporaryAppAllowlistType());
        assertEquals(expectedDuration, bo.getTemporaryAppAllowlistDuration());
        assertEquals(expectedReasonCode, bo.getTemporaryAppAllowlistReasonCode());
        assertEquals(expectedReason, bo.getTemporaryAppAllowlistReason());

        // Clone the BO and check it too.
        BroadcastOptions cloned = cloneViaBundle(bo);
        assertEquals(expectedAllowListType, cloned.getTemporaryAppAllowlistType());
        assertEquals(expectedDuration, cloned.getTemporaryAppAllowlistDuration());
        assertEquals(expectedReasonCode, cloned.getTemporaryAppAllowlistReasonCode());
        assertEquals(expectedReason, cloned.getTemporaryAppAllowlistReason());
    }

    private void assertBroadcastOption_noTemporaryAppAllowList(BroadcastOptions bo) {
        assertBroadcastOptionTemporaryAppAllowList(bo,
                /* duration= */ 0,
                PowerExemptionManager.TEMPORARY_ALLOW_LIST_TYPE_NONE,
                PowerExemptionManager.REASON_UNKNOWN,
                /* reason= */ null);
    }

    @Test
    public void testTemporaryAppAllowlistBroadcastOptions_defaultValues() {
        BroadcastOptions bo;

        bo = BroadcastOptions.makeBasic();
        Bundle bundle = bo.toBundle();

        // Only background activity launch key is set.
        assertEquals(1, bundle.size());
        // TODO: Use BroadcastOptions.KEY_PENDING_INTENT_BACKGROUND_ACTIVITY_ALLOWED instead.
        assertTrue(bundle.containsKey("android.pendingIntent.backgroundActivityAllowed"));

        // Check the default values about temp-allowlist.
        assertBroadcastOption_noTemporaryAppAllowList(bo);
    }

    @Test
    public void testSetTemporaryAppWhitelistDuration_legacyApi() {
        BroadcastOptions bo;

        bo = BroadcastOptions.makeBasic();

        bo.setTemporaryAppWhitelistDuration(10);

        assertBroadcastOptionTemporaryAppAllowList(bo,
                /* duration= */ 10,
                PowerExemptionManager.TEMPORARY_ALLOW_LIST_TYPE_FOREGROUND_SERVICE_ALLOWED,
                PowerExemptionManager.REASON_UNKNOWN,
                /* reason= */ null);

        // Clear the temp-allowlist.
        bo.setTemporaryAppWhitelistDuration(0);

        // Check the default values about temp-allowlist.
        assertBroadcastOption_noTemporaryAppAllowList(bo);
    }

    @Test
    public void testSetTemporaryAppWhitelistDuration() {
        BroadcastOptions bo;

        bo = BroadcastOptions.makeBasic();

        bo.setTemporaryAppAllowlist(10,
                PowerExemptionManager.TEMPORARY_ALLOW_LIST_TYPE_FOREGROUND_SERVICE_NOT_ALLOWED,
                PowerExemptionManager.REASON_GEOFENCING,
                null);

        assertBroadcastOptionTemporaryAppAllowList(bo,
                /* duration= */ 10,
                PowerExemptionManager.TEMPORARY_ALLOW_LIST_TYPE_FOREGROUND_SERVICE_NOT_ALLOWED,
                PowerExemptionManager.REASON_GEOFENCING,
                /* reason= */ null);

        // Setting duration 0 will clear the previous call.
        bo.setTemporaryAppAllowlist(0,
                PowerExemptionManager.TEMPORARY_ALLOW_LIST_TYPE_FOREGROUND_SERVICE_NOT_ALLOWED,
                PowerExemptionManager.REASON_ACTIVITY_RECOGNITION,
                "reason");
        assertBroadcastOption_noTemporaryAppAllowList(bo);

        // Set again.
        bo.setTemporaryAppAllowlist(20,
                PowerExemptionManager.TEMPORARY_ALLOW_LIST_TYPE_FOREGROUND_SERVICE_ALLOWED,
                PowerExemptionManager.REASON_GEOFENCING,
                "reason");

        assertBroadcastOptionTemporaryAppAllowList(bo,
                /* duration= */ 20,
                PowerExemptionManager.TEMPORARY_ALLOW_LIST_TYPE_FOREGROUND_SERVICE_ALLOWED,
                PowerExemptionManager.REASON_GEOFENCING,
                /* reason= */ "reason");

        // Set to NONE will clear the previous call too.
        bo.setTemporaryAppAllowlist(10,
                PowerExemptionManager.TEMPORARY_ALLOW_LIST_TYPE_NONE,
                PowerExemptionManager.REASON_ACTIVITY_RECOGNITION,
                "reason");

        assertBroadcastOption_noTemporaryAppAllowList(bo);
    }

    @Test
    public void testMaxManifestReceiverApiLevel() {
        final BroadcastOptions bo = BroadcastOptions.makeBasic();
        // No MaxManifestReceiverApiLevel set, the default value should be CUR_DEVELOPMENT.
        assertEquals(Build.VERSION_CODES.CUR_DEVELOPMENT, bo.getMaxManifestReceiverApiLevel());

        // Set MaxManifestReceiverApiLevel to P.
        bo.setMaxManifestReceiverApiLevel(Build.VERSION_CODES.P);
        assertEquals(Build.VERSION_CODES.P, bo.getMaxManifestReceiverApiLevel());

        // Clone the BroadcastOptions and check it too.
        final BroadcastOptions cloned = cloneViaBundle(bo);
        assertEquals(Build.VERSION_CODES.P, bo.getMaxManifestReceiverApiLevel());
    }

    @Test
    public void testGetSetPendingIntentBackgroundActivityLaunchAllowed() {
        BroadcastOptions options = BroadcastOptions.makeBasic();
        options.setPendingIntentBackgroundActivityLaunchAllowed(true);
        assertTrue(options.isPendingIntentBackgroundActivityLaunchAllowed());
        options.setPendingIntentBackgroundActivityLaunchAllowed(false);
        assertFalse(options.isPendingIntentBackgroundActivityLaunchAllowed());
    }

    private void assertBroadcastSuccess(BroadcastOptions options) {
        final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        final WaitForBroadcast waiter = new WaitForBroadcast(instrumentation.getTargetContext());
        waiter.prepare(ACTION_START_FGS_RESULT);
        CommandReceiver.sendCommandWithBroadcastOptions(instrumentation.getContext(),
                CommandReceiver.COMMAND_START_FOREGROUND_SERVICE,
                PACKAGE_NAME_APP1, PACKAGE_NAME_APP2, 0, null,
                options.toBundle());
        waiter.doWait(WAITFOR_MSEC);
    }

    private void assertBroadcastFailure(BroadcastOptions options) {
        final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        final WaitForBroadcast waiter = new WaitForBroadcast(instrumentation.getTargetContext());
        waiter.prepare(ACTION_START_FGS_RESULT);
        CommandReceiver.sendCommandWithBroadcastOptions(instrumentation.getContext(),
                CommandReceiver.COMMAND_START_FOREGROUND_SERVICE,
                PACKAGE_NAME_APP1, PACKAGE_NAME_APP2, 0, null,
                options.toBundle());
        assertThrows(Exception.class, () -> waiter.doWait(WAITFOR_MSEC));
    }

    @Test
    public void testRequireCompatChange_simple() {
        SystemUtil.runWithShellPermissionIdentity(() -> {
            final int uid = android.os.Process.myUid();
            final BroadcastOptions options = BroadcastOptions.makeBasic();

            // Default passes
            assertTrue(options.testRequireCompatChange(uid));
            assertTrue(cloneViaBundle(options).testRequireCompatChange(uid));

            // Verify both enabled and disabled
            options.setRequireCompatChange(BroadcastOptions.CHANGE_ALWAYS_ENABLED, true);
            assertTrue(options.testRequireCompatChange(uid));
            assertTrue(cloneViaBundle(options).testRequireCompatChange(uid));
            options.setRequireCompatChange(BroadcastOptions.CHANGE_ALWAYS_ENABLED, false);
            assertFalse(options.testRequireCompatChange(uid));
            assertFalse(cloneViaBundle(options).testRequireCompatChange(uid));

            // And back to default passes
            options.clearRequireCompatChange();
            assertTrue(options.testRequireCompatChange(uid));
            assertTrue(cloneViaBundle(options).testRequireCompatChange(uid));
        });
    }

    @Test
    public void testRequireCompatChange_enabled_success() {
        final BroadcastOptions options = BroadcastOptions.makeBasic();
        options.setRequireCompatChange(BroadcastOptions.CHANGE_ALWAYS_ENABLED, true);
        assertBroadcastSuccess(options);
    }

    @Test
    public void testRequireCompatChange_enabled_failure() {
        final BroadcastOptions options = BroadcastOptions.makeBasic();
        options.setRequireCompatChange(BroadcastOptions.CHANGE_ALWAYS_DISABLED, true);
        assertBroadcastFailure(options);
    }

    @Test
    public void testRequireCompatChange_disabled_success() {
        final BroadcastOptions options = BroadcastOptions.makeBasic();
        options.setRequireCompatChange(BroadcastOptions.CHANGE_ALWAYS_DISABLED, false);
        assertBroadcastSuccess(options);
    }

    @Test
    public void testRequireCompatChange_disabled_failure() {
        final BroadcastOptions options = BroadcastOptions.makeBasic();
        options.setRequireCompatChange(BroadcastOptions.CHANGE_ALWAYS_ENABLED, false);
        assertBroadcastFailure(options);
    }
}
