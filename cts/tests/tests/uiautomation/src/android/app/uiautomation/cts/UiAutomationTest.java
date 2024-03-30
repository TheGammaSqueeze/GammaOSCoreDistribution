/*
 * Copyright (C) 2014 The Android Open Source Project
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

package android.app.uiautomation.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.Manifest;
import android.accessibility.cts.common.AccessibilityDumpOnFailureRule;
import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Process;
import android.os.SystemClock;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.Presubmit;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.FrameStats;
import android.view.KeyEvent;
import android.view.WindowAnimationFrameStats;
import android.view.WindowContentFrameStats;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.widget.ListView;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.concurrent.TimeoutException;

/**
 * Tests for the UiAutomation APIs.
 */
@RunWith(AndroidJUnit4.class)
public class UiAutomationTest {
    private static final long QUIET_TIME_TO_BE_CONSIDERED_IDLE_STATE = 1000;//ms

    private static final long TOTAL_TIME_TO_WAIT_FOR_IDLE_STATE = 1000 * 10;//ms

    // Used to enable/disable accessibility services
    private static final String COMPONENT_NAME_SEPARATOR = ":";
    private static final int TIMEOUT_FOR_SERVICE_ENABLE = 10000; // millis; 10s

    @Rule
    public final AccessibilityDumpOnFailureRule mDumpOnFailureRule =
            new AccessibilityDumpOnFailureRule();

    @Before
    public void setUp() throws Exception {
        UiAutomation uiAutomation = getInstrumentation().getUiAutomation();
        AccessibilityServiceInfo info = uiAutomation.getServiceInfo();
        info.flags |= AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
        uiAutomation.setServiceInfo(info);
        grantWriteSecureSettingsPermission(uiAutomation);
    }

    @AppModeFull
    @Test
    public void testAdoptAllShellPermissions() {
        final Context context = getInstrumentation().getContext();
        final ActivityManager activityManager = context.getSystemService(ActivityManager.class);
        final PackageManager packageManager = context.getPackageManager();

        // Try to access APIs guarded by a platform defined signature permissions
        assertThrows(SecurityException.class,
                () -> activityManager.getPackageImportance("foo.bar.baz"),
                "Should not be able to access APIs protected by a permission apps cannot get");
        assertThrows(SecurityException.class,
                () -> packageManager.grantRuntimePermission(context.getPackageName(),
                        Manifest.permission.ANSWER_PHONE_CALLS, Process.myUserHandle()),
                "Should not be able to access APIs protected by a permission apps cannot get");

        // Access APIs guarded by a platform defined signature permissions
        try {
            getInstrumentation().getUiAutomation().adoptShellPermissionIdentity();
            // Access APIs guarded by a platform defined signature permission
            activityManager.getPackageImportance("foo.bar.baz");

            // Grant ourselves a runtime permission (was granted at install)
            packageManager.grantRuntimePermission(context.getPackageName(),
                    Manifest.permission.ANSWER_PHONE_CALLS, Process.myUserHandle());
        } catch (SecurityException e) {
            fail("Should be able to access APIs protected by a permission apps cannot get");
        } finally {
            getInstrumentation().getUiAutomation().dropShellPermissionIdentity();
        }

        // Try to access APIs guarded by a platform defined signature permissions
        assertThrows(SecurityException.class,
                () -> activityManager.getPackageImportance("foo.bar.baz"),
                "Should not be able to access APIs protected by a permission apps cannot get");
        assertThrows(SecurityException.class,
                () -> packageManager.revokeRuntimePermission(context.getPackageName(),
                        Manifest.permission.ANSWER_PHONE_CALLS, Process.myUserHandle()),
                "Should not be able to access APIs protected by a permission apps cannot get");
    }

    @AppModeFull
    @Test
    public void testAdoptSomeShellPermissions() {
        final Context context = getInstrumentation().getContext();

        // Make sure we don't have any of the permissions
        assertSame(PackageManager.PERMISSION_DENIED, context.checkSelfPermission(
                Manifest.permission.BATTERY_STATS));
        assertSame(PackageManager.PERMISSION_DENIED, context.checkSelfPermission(
                Manifest.permission.PACKAGE_USAGE_STATS));

        try {
            // Adopt a permission
            getInstrumentation().getUiAutomation().adoptShellPermissionIdentity(
                    Manifest.permission.BATTERY_STATS);
            // Check one is granted and the other not
            assertSame(PackageManager.PERMISSION_GRANTED, context.checkSelfPermission(
                    Manifest.permission.BATTERY_STATS));
            assertSame(PackageManager.PERMISSION_DENIED, context.checkSelfPermission(
                    Manifest.permission.PACKAGE_USAGE_STATS));

            // Adopt all permissions
            getInstrumentation().getUiAutomation().adoptShellPermissionIdentity();
            // Check both permissions are granted
            assertSame(PackageManager.PERMISSION_GRANTED, context.checkSelfPermission(
                    Manifest.permission.BATTERY_STATS));
            assertSame(PackageManager.PERMISSION_GRANTED, context.checkSelfPermission(
                    Manifest.permission.PACKAGE_USAGE_STATS));

            // Adopt a permission
            getInstrumentation().getUiAutomation().adoptShellPermissionIdentity(
                    Manifest.permission.PACKAGE_USAGE_STATS);
            // Check one is granted and the other not
            assertSame(PackageManager.PERMISSION_DENIED, context.checkSelfPermission(
                    Manifest.permission.BATTERY_STATS));
            assertSame(PackageManager.PERMISSION_GRANTED, context.checkSelfPermission(
                    Manifest.permission.PACKAGE_USAGE_STATS));
        } finally {
            getInstrumentation().getUiAutomation().dropShellPermissionIdentity();
        }
    }

    @Test
    public void testWindowContentFrameStats() throws Exception {
        Activity activity = null;
        try {
            UiAutomation uiAutomation = getInstrumentation().getUiAutomation();

            // Start an activity.
            Intent intent = new Intent(getInstrumentation().getContext(),
                    UiAutomationTestFirstActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity = getInstrumentation().startActivitySync(intent);

            // Wait for things to settle.
            uiAutomation.waitForIdle(
                    QUIET_TIME_TO_BE_CONSIDERED_IDLE_STATE, TOTAL_TIME_TO_WAIT_FOR_IDLE_STATE);

            // Wait for Activity draw finish
            getInstrumentation().waitForIdleSync();

            // Find the application window.
            final int windowId = findAppWindowId(uiAutomation.getWindows(), activity);
            assertTrue(windowId >= 0);

            // Clear stats to be with a clean slate.
            assertTrue(uiAutomation.clearWindowContentFrameStats(windowId));

            // Find the list to scroll around.
            final ListView listView = (ListView) activity.findViewById(R.id.list_view);

            // Scroll a bit.
            scrollListView(uiAutomation, listView, listView.getAdapter().getCount() - 1);
            scrollListView(uiAutomation, listView, 0);

            // Get the frame stats.
            WindowContentFrameStats stats = uiAutomation.getWindowContentFrameStats(windowId);

            // Check the frame stats...

            // We should have something.
            assertNotNull(stats);

            // The refresh period is always positive.
            assertTrue(stats.getRefreshPeriodNano() > 0);

            // There is some frame data.
            final int frameCount = stats.getFrameCount();
            assertTrue(frameCount > 0);

            // The frames are ordered in ascending order.
            assertWindowContentTimestampsInAscendingOrder(stats);

            // The start and end times are based on first and last frame.
            assertEquals(stats.getStartTimeNano(), stats.getFramePresentedTimeNano(0));
            assertEquals(stats.getEndTimeNano(), stats.getFramePresentedTimeNano(frameCount - 1));
        } finally {
            // Clean up.
            if (activity != null) {
                activity.finish();
            }
        }
    }

    @Test
    public void testWindowContentFrameStatsNoAnimation() throws Exception {
        Activity activity = null;
        try {
            UiAutomation uiAutomation = getInstrumentation().getUiAutomation();

            // Start an activity.
            Intent intent = new Intent(getInstrumentation().getContext(),
                    UiAutomationTestFirstActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity = getInstrumentation().startActivitySync(intent);

            // Wait for things to settle.
            uiAutomation.waitForIdle(
                    QUIET_TIME_TO_BE_CONSIDERED_IDLE_STATE, TOTAL_TIME_TO_WAIT_FOR_IDLE_STATE);

            // Wait for Activity draw finish
            getInstrumentation().waitForIdleSync();

            // Find the application window.
            final int windowId = findAppWindowId(uiAutomation.getWindows(), activity);
            assertTrue(windowId >= 0);

            // Clear stats to be with a clean slate.
            assertTrue(uiAutomation.clearWindowContentFrameStats(windowId));

            // Get the frame stats.
            WindowContentFrameStats stats = uiAutomation.getWindowContentFrameStats(windowId);

            // Check the frame stats...

            // We should have something.
            assertNotNull(stats);

            // The refresh period is always positive.
            assertTrue(stats.getRefreshPeriodNano() > 0);

            // There is no data.
            assertTrue(stats.getFrameCount() == 0);

            // The start and end times are undefibed as we have no data.
            assertEquals(stats.getStartTimeNano(), FrameStats.UNDEFINED_TIME_NANO);
            assertEquals(stats.getEndTimeNano(), FrameStats.UNDEFINED_TIME_NANO);
        } finally {
            // Clean up.
            if (activity != null) {
                activity.finish();
            }
        }
    }

    @Presubmit
    @Test
    public void testWindowAnimationFrameStatsDoesntCrash() throws Exception {
        UiAutomation uiAutomation = getInstrumentation().getUiAutomation();

        // Get the frame stats. This just needs to not crash because these APIs are deprecated.
        uiAutomation.clearWindowAnimationFrameStats();
        WindowAnimationFrameStats stats = uiAutomation.getWindowAnimationFrameStats();
        assertEquals(0, stats.getFrameCount());
    }


    @Presubmit
    @Test
    public void testUsingUiAutomationAfterDestroy_shouldThrowException() {
        UiAutomation uiAutomation = getInstrumentation().getUiAutomation();
        uiAutomation.destroy();
        assertThrows(RuntimeException.class, () -> uiAutomation.getServiceInfo(),
                "Expected exception when using destroyed UiAutomation");
    }

    @AppModeFull
    @Test
    public void testDontSuppressAccessibility_canStartA11yService() throws Exception {
        turnAccessibilityOff();
        try {
            getInstrumentation()
                    .getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
            enableAccessibilityService();
            assertTrue(UiAutomationTestA11yService.sConnectedInstance.isConnected());
        } finally {
            turnAccessibilityOff();
        }
    }

    @AppModeFull
    @Test
    public void testServiceWithNoFlags_shutsDownA11yService() throws Exception {
        turnAccessibilityOff();
        try {
            UiAutomation uiAutomation = getInstrumentation()
                    .getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
            enableAccessibilityService();
            assertTrue(UiAutomationTestA11yService.sConnectedInstance.isConnected());
            uiAutomation.destroy();
            assertTrue(UiAutomationTestA11yService.sConnectedInstance.isConnected());
            getInstrumentation().getUiAutomation(); // Should suppress
            waitForAccessibilityServiceToUnbind();
        } finally {
            turnAccessibilityOff();
        }
    }

    @AppModeFull
    @Test
    public void testServiceWithDontUseAccessibilityFlag_shutsDownA11yService() throws Exception {
        turnAccessibilityOff();
        try {
            enableAccessibilityService();
            assertTrue(UiAutomationTestA11yService.sConnectedInstance.isConnected());
            getInstrumentation().getUiAutomation(
                    UiAutomation.FLAG_DONT_USE_ACCESSIBILITY); // Should suppress
            waitForAccessibilityServiceToUnbind();
        } finally {
            turnAccessibilityOff();
        }
    }

    @AppModeFull
    @Test
    public void testServiceSuppressingA11yServices_a11yServiceStartsWhenDestroyed()
            throws Exception {
        turnAccessibilityOff();
        try {
            UiAutomation uiAutomation = getInstrumentation()
                    .getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
            enableAccessibilityService();
            uiAutomation.destroy();

            assertA11yServiceSuppressedAndRestartsAfterUiAutomationDestroyed(0);
        } finally {
            turnAccessibilityOff();
        }
    }

    @AppModeFull
    @Test
    public void testServiceSuppressingA11yServices_a11yServiceStartsWhenDestroyedAndFlagChanged()
            throws Exception {
        turnAccessibilityOff();
        try {
            UiAutomation uiAutomation = getInstrumentation()
                    .getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
            enableAccessibilityService();
            uiAutomation.destroy();

            assertA11yServiceSuppressedAndRestartsAfterUiAutomationDestroyed(
                    UiAutomation.FLAG_DONT_USE_ACCESSIBILITY);
        } finally {
            turnAccessibilityOff();
        }
    }

    @AppModeFull
    @Test
    public void testServiceSuppressingA11yServices_a11yServiceStartsWhenFlagsChange()
            throws Exception {
        turnAccessibilityOff();
        try {
            getInstrumentation()
                    .getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
            enableAccessibilityService();
            getInstrumentation().getUiAutomation();
            // We verify above that the connection is broken here. Make sure we see a new one
            // after we change the flags
            waitForAccessibilityServiceToUnbind();
            getInstrumentation()
                    .getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
            waitForAccessibilityServiceToStart();
        } finally {
            turnAccessibilityOff();
        }
    }

    @AppModeFull
    @Test
    public void testCallingDisabledAccessibilityAPIsWithDontUseAccessibilityFlag_shouldThrowException()
            throws Exception {
        final UiAutomation uiAutomation = getInstrumentation()
                .getUiAutomation(UiAutomation.FLAG_DONT_USE_ACCESSIBILITY);
        final String failMsg =
                "Should not be able to access Accessibility APIs disabled by UiAutomation flag, "
                        + "FLAG_DONT_USE_ACCESSIBILITY";
        assertThrows(IllegalStateException.class,
                () -> uiAutomation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK),
                failMsg);
        assertThrows(IllegalStateException.class,
                () -> uiAutomation.findFocus(AccessibilityNodeInfo.FOCUS_INPUT), failMsg);
        assertThrows(IllegalStateException.class,
                () -> uiAutomation.getServiceInfo(), failMsg);
        assertThrows(IllegalStateException.class,
                () -> uiAutomation.setServiceInfo(new AccessibilityServiceInfo()), failMsg);
        assertThrows(IllegalStateException.class,
                () -> uiAutomation.findFocus(AccessibilityNodeInfo.FOCUS_INPUT), failMsg);
        assertThrows(IllegalStateException.class,
                () -> uiAutomation.getWindows(), failMsg);
        assertThrows(IllegalStateException.class,
                () -> uiAutomation.getWindowsOnAllDisplays(), failMsg);
        assertThrows(IllegalStateException.class,
                () -> uiAutomation.clearWindowContentFrameStats(-1), failMsg);
        assertThrows(IllegalStateException.class,
                () -> uiAutomation.getWindowContentFrameStats(-1), failMsg);
        assertThrows(IllegalStateException.class,
                () -> uiAutomation.getRootInActiveWindow(), failMsg);
        assertThrows(IllegalStateException.class,
                () -> uiAutomation.setOnAccessibilityEventListener(null), failMsg);
    }

    @AppModeFull
    @Test
    public void testCallingPublicAPIsWithDontUseAccessibilityFlag_shouldNotThrowException()
            throws Exception {
        final UiAutomation uiAutomation = getInstrumentation()
                .getUiAutomation(UiAutomation.FLAG_DONT_USE_ACCESSIBILITY);
        final KeyEvent event = new KeyEvent(0, 0, KeyEvent.ACTION_DOWN,
                KeyEvent.KEYCODE_BACK, 0);
        uiAutomation.injectInputEvent(event, true);
        uiAutomation.syncInputTransactions();
        uiAutomation.setRotation(UiAutomation.ROTATION_FREEZE_0);
        uiAutomation.takeScreenshot();
        uiAutomation.clearWindowAnimationFrameStats();
        uiAutomation.getWindowAnimationFrameStats();
        try {
            uiAutomation.adoptShellPermissionIdentity(Manifest.permission.BATTERY_STATS);
        } finally {
            uiAutomation.dropShellPermissionIdentity();
        }
    }

    private void scrollListView(UiAutomation uiAutomation, final ListView listView,
            final int position) throws TimeoutException {
        getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                listView.smoothScrollToPosition(position);
            }
        });
        Runnable emptyRunnable = new Runnable() {
            @Override
            public void run() {
            }
        };
        UiAutomation.AccessibilityEventFilter scrollFilter =
                new UiAutomation.AccessibilityEventFilter() {
                    @Override
                    public boolean accept(AccessibilityEvent accessibilityEvent) {
                        return accessibilityEvent.getEventType()
                                == AccessibilityEvent.TYPE_VIEW_SCROLLED;
                    }
                };
        uiAutomation.executeAndWaitForEvent(emptyRunnable, scrollFilter,
                TOTAL_TIME_TO_WAIT_FOR_IDLE_STATE);
        uiAutomation.waitForIdle(
                QUIET_TIME_TO_BE_CONSIDERED_IDLE_STATE, TOTAL_TIME_TO_WAIT_FOR_IDLE_STATE);
    }

    private void grantWriteSecureSettingsPermission(UiAutomation uiAutomation) {
        uiAutomation.grantRuntimePermission(getInstrumentation().getContext().getPackageName(),
                android.Manifest.permission.WRITE_SECURE_SETTINGS);
    }

    private void enableAccessibilityService() {
        Context context = getInstrumentation().getContext();
        AccessibilityManager manager =
                (AccessibilityManager) context.getSystemService(Context.ACCESSIBILITY_SERVICE);
        List<AccessibilityServiceInfo> serviceInfos =
                manager.getInstalledAccessibilityServiceList();
        for (int i = 0; i < serviceInfos.size(); i++) {
            AccessibilityServiceInfo serviceInfo = serviceInfos.get(i);
            if (context.getString(R.string.uiautomation_a11y_service_description)
                    .equals(serviceInfo.getDescription())) {
                ContentResolver cr = context.getContentResolver();
                String enabledServices = Settings.Secure.getString(cr,
                        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
                Settings.Secure.putString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                        enabledServices + COMPONENT_NAME_SEPARATOR + serviceInfo.getId());
                Settings.Secure.putInt(cr, Settings.Secure.ACCESSIBILITY_ENABLED, 1);
                waitForAccessibilityServiceToStart();
                return;
            }
        }
        throw new RuntimeException("Test accessibility service not found");
    }

    private void waitForAccessibilityServiceToStart() {
        long timeoutTimeMillis = SystemClock.uptimeMillis() + TIMEOUT_FOR_SERVICE_ENABLE;
        while (SystemClock.uptimeMillis() < timeoutTimeMillis) {
            synchronized (UiAutomationTestA11yService.sWaitObjectForConnectOrUnbind) {
                if (UiAutomationTestA11yService.sConnectedInstance != null) {
                    return;
                }
                try {
                    UiAutomationTestA11yService.sWaitObjectForConnectOrUnbind.wait(
                            timeoutTimeMillis - SystemClock.uptimeMillis());
                } catch (InterruptedException e) {
                    // Ignored; loop again
                }
            }
        }
        throw new RuntimeException("Test accessibility service not starting");
    }

    private void waitForAccessibilityServiceToUnbind() {
        long timeoutTimeMillis = SystemClock.uptimeMillis() + TIMEOUT_FOR_SERVICE_ENABLE;
        while (SystemClock.uptimeMillis() < timeoutTimeMillis) {
            synchronized (UiAutomationTestA11yService.sWaitObjectForConnectOrUnbind) {
                if (UiAutomationTestA11yService.sConnectedInstance == null) {
                    return;
                }
                try {
                    UiAutomationTestA11yService.sWaitObjectForConnectOrUnbind.wait(
                            timeoutTimeMillis - SystemClock.uptimeMillis());
                } catch (InterruptedException e) {
                    // Ignored; loop again
                }
            }
        }
        throw new RuntimeException("Test accessibility service doesn't unbind");
    }

    private void turnAccessibilityOff() {
        getInstrumentation().getUiAutomation().destroy();
        final Object waitLockForA11yOff = new Object();
        Context context = getInstrumentation().getContext();
        AccessibilityManager manager =
                (AccessibilityManager) context.getSystemService(Context.ACCESSIBILITY_SERVICE);
        manager.addAccessibilityStateChangeListener(
                new AccessibilityManager.AccessibilityStateChangeListener() {
                    @Override
                    public void onAccessibilityStateChanged(boolean b) {
                        synchronized (waitLockForA11yOff) {
                            waitLockForA11yOff.notifyAll();
                        }
                    }
                });
        ContentResolver cr = context.getContentResolver();
        Settings.Secure.putString(
                cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, null);
        long timeoutTimeMillis = SystemClock.uptimeMillis() + TIMEOUT_FOR_SERVICE_ENABLE;
        while (SystemClock.uptimeMillis() < timeoutTimeMillis) {
            synchronized (waitLockForA11yOff) {
                if (!manager.isEnabled()) {
                    return;
                }
                try {
                    waitLockForA11yOff.wait(timeoutTimeMillis - SystemClock.uptimeMillis());
                } catch (InterruptedException e) {
                    // Ignored; loop again
                }
            }
        }
        throw new RuntimeException("Unable to turn accessibility off");
    }

    private void assertWindowContentTimestampsInAscendingOrder(WindowContentFrameStats stats) {
        long lastDesiredPresentTimeNano = 0;
        long lastPreviousFramePresentTimeNano = 0;
        long lastFrameReadyTimeNano = 0;

        String statsDebugDump = stats.toString();
        for (int i = 0; i < stats.getFrameCount(); i++) {
            statsDebugDump += " [" + i + ":" +
            stats.getFramePostedTimeNano(i) + " " +
            stats.getFramePresentedTimeNano(i) + " " + stats.getFrameReadyTimeNano(i)  + "] ";
        }

        final int frameCount = stats.getFrameCount();
        for (int i = 0; i < frameCount; i++) {
            final long desiredPresentTimeNano = stats.getFramePostedTimeNano(i);
            final long previousFramePresentTimeNano = stats.getFramePresentedTimeNano(i);
            final long frameReadyTimeNano = stats.getFrameReadyTimeNano(i);

            if (desiredPresentTimeNano == FrameStats.UNDEFINED_TIME_NANO ||
                previousFramePresentTimeNano == FrameStats.UNDEFINED_TIME_NANO ||
                frameReadyTimeNano == FrameStats.UNDEFINED_TIME_NANO) {
                continue;
            }

            if (i > 0) {
                // WindowContentFrameStats#getFramePresentedTimeNano() returns the previous frame
                // presented time, so verify the actual presented timestamp is ahead of the
                // last frame's desired present time and frame ready time.

                // NOTE: actual present time maybe an estimate. If this test continues to be flaky,
                // we may need to add a margin like the one below.
                // previousFramePresentTimeNano += stats.getRefreshPeriodNano() / 2;
                assertTrue("Failed frame:" + i + statsDebugDump,
                        previousFramePresentTimeNano >= lastDesiredPresentTimeNano);
                assertTrue("Failed frame:" + i + statsDebugDump,
                        previousFramePresentTimeNano >= lastFrameReadyTimeNano);
            }
            assertTrue("Failed frame:" + i + statsDebugDump,
                    previousFramePresentTimeNano >= lastPreviousFramePresentTimeNano);

            lastDesiredPresentTimeNano = desiredPresentTimeNano;
            lastPreviousFramePresentTimeNano = previousFramePresentTimeNano;
            lastFrameReadyTimeNano = frameReadyTimeNano;
        }
    }

    // An actual version of assertThrows() was added in JUnit5
    private static <T extends Throwable> void assertThrows(Class<T> clazz, Runnable r,
            String message) {
        try {
            r.run();
        } catch (Exception expected) {
            assertTrue(clazz.isAssignableFrom(expected.getClass()));
            return;
        }
        fail(message);
    }

    private void assertA11yServiceSuppressedAndRestartsAfterUiAutomationDestroyed(int flag) {
        UiAutomation suppressingUiAutomation = getInstrumentation().getUiAutomation(flag);
        // We verify above that the connection is broken here. Make sure we see a new one
        // after we destroy it
        waitForAccessibilityServiceToUnbind();
        suppressingUiAutomation.destroy();
        waitForAccessibilityServiceToStart();
    }

    private int findAppWindowId(List<AccessibilityWindowInfo> windows, Activity activity) {
        final CharSequence activityTitle = getActivityTitle(getInstrumentation(), activity);
        final int windowCount = windows.size();
        for (int i = 0; i < windowCount; i++) {
            AccessibilityWindowInfo window = windows.get(i);
            if (window.getType() == AccessibilityWindowInfo.TYPE_APPLICATION
                    && TextUtils.equals(window.getTitle(), activityTitle)) {
                return window.getId();
            }
        }
        return -1;
    }

    private Instrumentation getInstrumentation() {
        return InstrumentationRegistry.getInstrumentation();
    }

    private static CharSequence getActivityTitle(
            Instrumentation instrumentation, Activity activity) {
        final StringBuilder titleBuilder = new StringBuilder();
        instrumentation.runOnMainSync(() -> titleBuilder.append(activity.getTitle()));
        return titleBuilder;
    }
}
