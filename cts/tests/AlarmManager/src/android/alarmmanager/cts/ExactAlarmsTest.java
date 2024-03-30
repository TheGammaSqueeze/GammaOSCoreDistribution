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

package android.alarmmanager.cts;

import static android.alarmmanager.cts.AppStandbyTests.setTestAppStandbyBucket;
import static android.app.usage.UsageStatsManager.STANDBY_BUCKET_ACTIVE;
import static android.app.usage.UsageStatsManager.STANDBY_BUCKET_WORKING_SET;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;

import android.alarmmanager.alarmtestapp.cts.TestAlarmReceiver;
import android.alarmmanager.alarmtestapp.cts.TestAlarmScheduler;
import android.alarmmanager.alarmtestapp.cts.common.FgsTester;
import android.alarmmanager.alarmtestapp.cts.common.PermissionStateChangedReceiver;
import android.alarmmanager.alarmtestapp.cts.common.RequestReceiver;
import android.alarmmanager.util.AlarmManagerDeviceConfigHelper;
import android.alarmmanager.util.Utils;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.AppOpsManager;
import android.app.PendingIntent;
import android.app.compat.CompatChanges;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.PowerWhitelistManager;
import android.os.Process;
import android.os.SystemClock;
import android.os.UserHandle;
import android.platform.test.annotations.AppModeFull;
import android.provider.Settings;
import android.util.Log;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.AppOpsUtils;
import com.android.compatibility.common.util.AppStandbyUtils;
import com.android.compatibility.common.util.FeatureUtil;
import com.android.compatibility.common.util.ShellUtils;
import com.android.compatibility.common.util.SystemUtil;
import com.android.compatibility.common.util.TestUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.Description;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@AppModeFull
@RunWith(AndroidJUnit4.class)
public class ExactAlarmsTest {
    /**
     * TODO (b/182835530): Add more tests for the following:
     *
     * Pre-S apps can:
     * - use setAlarmClock freely -- no temp-allowlist
     * - use setExactAndAWI with 7 / hr quota with standby and temp-allowlist
     * - use setInexactAndAWI with 7 / hr quota with standby-bucket "ACTIVE" and temp-allowlist
     *
     * S+ apps with permission can:
     * - use setInexactAWI with low quota + standby and *no* temp-allowlist.
     */
    private static final String TAG = ExactAlarmsTest.class.getSimpleName();

    private static final String TEST_APP_30 = "android.alarmmanager.alarmtestapp.cts.sdk30";
    private static final String TEST_APP_WITH_SCHEDULE_EXACT_ALARM_32 =
            "android.alarmmanager.alarmtestapp.cts.user_permission_32";
    private static final String TEST_APP_WITH_USE_EXACT_ALARM_32 =
            "android.alarmmanager.alarmtestapp.cts.policy_permission_32";

    private static final int ALLOW_WHILE_IDLE_QUOTA = 5;
    private static final long ALLOW_WHILE_IDLE_WINDOW = 10_000;
    private static final int ALLOW_WHILE_IDLE_COMPAT_QUOTA = 3;

    /**
     * Waiting generously long for success because the system can sometimes be slow to
     * provide expected behavior.
     * A different and shorter duration should be used while waiting for no-failure, because
     * even if the system is slow to fail in some cases, it would still cause some
     * flakiness and get flagged for investigation.
     */
    private static final long DEFAULT_WAIT_FOR_SUCCESS = 30_000;

    private static final String TEST_APP_PACKAGE = "android.alarmmanager.alarmtestapp.cts";

    private static final Context sContext = InstrumentationRegistry.getTargetContext();
    private final AlarmManager mAlarmManager = sContext.getSystemService(AlarmManager.class);
    private final PowerWhitelistManager mWhitelistManager = sContext.getSystemService(
            PowerWhitelistManager.class);
    private final PackageManager mPackageManager = sContext.getPackageManager();
    private final ComponentName mPermissionChangeReceiver = new ComponentName(TEST_APP_PACKAGE,
            PermissionStateChangedReceiver.class.getName());
    private final ComponentName mPermissionChangeReceiver32 = new ComponentName(
            TEST_APP_WITH_SCHEDULE_EXACT_ALARM_32,
            PermissionStateChangedReceiver.class.getName());

    private final AlarmManagerDeviceConfigHelper mDeviceConfigHelper =
            new AlarmManagerDeviceConfigHelper();
    private final Random mIdGenerator = new Random(6789);

    @Rule
    public DumpLoggerRule mFailLoggerRule = new DumpLoggerRule(TAG) {
        @Override
        protected void failed(Throwable e, Description description) {
            super.failed(e, description);
            AlarmReceiver.dumpState();
        }
    };

    @Before
    public void updateAlarmManagerConstants() {
        mDeviceConfigHelper.with("min_futurity", 0L)
                .with("allow_while_idle_quota", ALLOW_WHILE_IDLE_QUOTA)
                .with("allow_while_idle_compat_quota", ALLOW_WHILE_IDLE_COMPAT_QUOTA)
                .with("allow_while_idle_window", ALLOW_WHILE_IDLE_WINDOW)
                .with("kill_on_schedule_exact_alarm_revoked", false)
                .commitAndAwaitPropagation();
    }

    @Before
    public void putDeviceToIdle() {
        SystemUtil.runShellCommandForNoOutput("dumpsys battery reset");
        SystemUtil.runShellCommand("cmd deviceidle force-idle deep");
    }

    @Before
    public void enableChanges() {
        Utils.enableChangeForSelf(AlarmManager.REQUIRE_EXACT_ALARM_PERMISSION);
        Utils.enableChangeForSelf(AlarmManager.ENABLE_USE_EXACT_ALARM);
    }

    @After
    public void resetChanges() {
        Utils.resetChange(AlarmManager.REQUIRE_EXACT_ALARM_PERMISSION, sContext.getOpPackageName());
        Utils.resetChange(AlarmManager.ENABLE_USE_EXACT_ALARM, sContext.getOpPackageName());
        Utils.resetChange(AlarmManager.SCHEDULE_EXACT_ALARM_DENIED_BY_DEFAULT, TEST_APP_PACKAGE);
    }

    @After
    public void removeFromWhitelists() {
        removeFromWhitelists(sContext.getOpPackageName());
        removeFromWhitelists(TEST_APP_PACKAGE);
    }

    private void removeFromWhitelists(String packageName) {
        SystemUtil.runWithShellPermissionIdentity(
                () -> mWhitelistManager.removeFromWhitelist(packageName));
        SystemUtil.runShellCommand("cmd deviceidle tempwhitelist -r " + packageName);
    }

    @After
    public void restoreBatteryState() {
        SystemUtil.runShellCommand("cmd deviceidle unforce");
        SystemUtil.runShellCommandForNoOutput("dumpsys battery reset");
    }

    @After
    public void restorePermissionReceiverState() {
        SystemUtil.runWithShellPermissionIdentity(
                () -> mPackageManager.setComponentEnabledSetting(mPermissionChangeReceiver,
                        PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
                        PackageManager.DONT_KILL_APP));
    }

    @After
    public void resetAppOps() throws IOException {
        AppOpsUtils.reset(TEST_APP_PACKAGE);
        AppOpsUtils.reset(TEST_APP_30);
    }

    @After
    public void restoreAlarmManagerConstants() throws IOException {
        mDeviceConfigHelper.restoreAll();
    }

    private void revokeAppOp(String packageName) {
        setAppOp(packageName, AppOpsManager.MODE_IGNORED);
    }

    static void setAppOp(String packageName, int mode) {
        final int uid = Utils.getPackageUid(packageName);
        AppOpsUtils.setUidMode(uid, AppOpsManager.OPSTR_SCHEDULE_EXACT_ALARM, mode);
    }

    private static PendingIntent getAlarmSender(int id, boolean quotaed) {
        final Intent alarmAction = new Intent(AlarmReceiver.ALARM_ACTION)
                .setClass(sContext, AlarmReceiver.class)
                .addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                .putExtra(AlarmReceiver.EXTRA_ALARM_ID, id)
                .putExtra(AlarmReceiver.EXTRA_QUOTAED, quotaed);
        return PendingIntent.getBroadcast(sContext, 0, alarmAction,
                PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private boolean getCanScheduleExactAlarmFromTestApp(String testAppName) throws Exception {
        final CountDownLatch resultLatch = new CountDownLatch(1);
        final AtomicBoolean apiResult = new AtomicBoolean(false);
        final AtomicInteger result = new AtomicInteger(-1);

        final Intent requestToTestApp = new Intent(
                RequestReceiver.ACTION_GET_CAN_SCHEDULE_EXACT_ALARM)
                .setClassName(testAppName, RequestReceiver.class.getName())
                .addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
        sContext.sendOrderedBroadcast(requestToTestApp, null, new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                result.set(getResultCode());
                final String resultStr = getResultData();
                apiResult.set(Boolean.parseBoolean(resultStr));
                resultLatch.countDown();
            }
        }, null, Activity.RESULT_CANCELED, null, null);

        assertTrue("Timed out waiting for response from helper app " + testAppName,
                resultLatch.await(10, TimeUnit.SECONDS));
        assertEquals(Activity.RESULT_OK, result.get());
        return apiResult.get();
    }

    @Test
    public void scheduleExactAlarmChangeDisabled() {
        assertFalse(CompatChanges.isChangeEnabled(
                AlarmManager.SCHEDULE_EXACT_ALARM_DENIED_BY_DEFAULT));
    }

    @Test
    public void defaultBehaviorWhenChangeDisabled() throws Exception {
        setAppOp(TEST_APP_PACKAGE, AppOpsManager.MODE_DEFAULT);
        assertTrue(getCanScheduleExactAlarmFromTestApp(TEST_APP_PACKAGE));

        mDeviceConfigHelper.with("exact_alarm_deny_list", TEST_APP_PACKAGE)
                .commitAndAwaitPropagation();
        assertFalse(getCanScheduleExactAlarmFromTestApp(TEST_APP_PACKAGE));
    }

    @Test
    public void defaultBehaviorWhenChangeEnabled() throws Exception {
        Utils.enableChange(AlarmManager.SCHEDULE_EXACT_ALARM_DENIED_BY_DEFAULT, TEST_APP_PACKAGE,
                sContext.getUserId());
        setAppOp(TEST_APP_PACKAGE, AppOpsManager.MODE_DEFAULT);
        assertFalse(getCanScheduleExactAlarmFromTestApp(TEST_APP_PACKAGE));
    }

    @Test
    public void noPermissionWhenIgnored() throws Exception {
        revokeAppOp(TEST_APP_PACKAGE);
        assertFalse(getCanScheduleExactAlarmFromTestApp(TEST_APP_PACKAGE));
    }

    @Test
    public void hasPermissionWhenAllowed() throws Exception {
        setAppOp(TEST_APP_PACKAGE, AppOpsManager.MODE_ALLOWED);
        assertTrue(getCanScheduleExactAlarmFromTestApp(TEST_APP_PACKAGE));

        // The deny list shouldn't matter in this case.
        mDeviceConfigHelper.with("exact_alarm_deny_list", TEST_APP_PACKAGE)
                .commitAndAwaitPropagation();
        assertTrue(getCanScheduleExactAlarmFromTestApp(TEST_APP_PACKAGE));
    }

    @Test
    // TODO (b/185181884): Remove once standby buckets can be reliably manipulated from tests.
    @Ignore("Cannot reliably test bucket manipulation yet")
    public void exactAlarmPermissionElevatesBucket() throws Exception {
        mDeviceConfigHelper.without("exact_alarm_deny_list").commitAndAwaitPropagation();

        setTestAppStandbyBucket("active");
        assertEquals(STANDBY_BUCKET_ACTIVE, AppStandbyUtils.getAppStandbyBucket(TEST_APP_PACKAGE));

        setTestAppStandbyBucket("frequent");
        assertEquals(STANDBY_BUCKET_WORKING_SET,
                AppStandbyUtils.getAppStandbyBucket(TEST_APP_PACKAGE));

        setTestAppStandbyBucket("rare");
        assertEquals(STANDBY_BUCKET_WORKING_SET,
                AppStandbyUtils.getAppStandbyBucket(TEST_APP_PACKAGE));
    }

    @Test
    public void canScheduleExactAlarmWithPolicyPermission() throws Exception {
        assertTrue(mAlarmManager.canScheduleExactAlarms());

        // The deny list shouldn't do anything.
        mDeviceConfigHelper.with("exact_alarm_deny_list", sContext.getOpPackageName())
                .commitAndAwaitPropagation();
        assertTrue(mAlarmManager.canScheduleExactAlarms());
    }

    @Test
    public void canScheduleExactAlarmWithPolicyPermissionSdk32() throws Exception {
        // Policy permission is not enabled at SDK 32.
        assertFalse(getCanScheduleExactAlarmFromTestApp(TEST_APP_WITH_USE_EXACT_ALARM_32));
    }

    @Test
    public void canScheduleExactAlarmWithUserPermissionSdk32() throws Exception {
        // Should be allowed by default.
        assertTrue(getCanScheduleExactAlarmFromTestApp(TEST_APP_WITH_SCHEDULE_EXACT_ALARM_32));

        mDeviceConfigHelper.with("exact_alarm_deny_list", TEST_APP_WITH_SCHEDULE_EXACT_ALARM_32)
                .commitAndAwaitPropagation();

        assertFalse("canScheduleExactAlarm returned true when app was in deny list",
                getCanScheduleExactAlarmFromTestApp(TEST_APP_WITH_SCHEDULE_EXACT_ALARM_32));
    }

    @Test
    public void canScheduleExactAlarmSdk30() throws Exception {
        revokeAppOp(TEST_APP_30);
        assertTrue(getCanScheduleExactAlarmFromTestApp(TEST_APP_30));
    }

    private static void assertSecurityExceptionFromTestApp(String requestAction, String testAppName)
            throws Exception {
        final CountDownLatch resultLatch = new CountDownLatch(1);
        final AtomicInteger result = new AtomicInteger(-1);

        final Intent requestToTestApp = new Intent(requestAction)
                .setClassName(testAppName, RequestReceiver.class.getName())
                .addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
        sContext.sendOrderedBroadcast(requestToTestApp, null, new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                result.set(getResultCode());
                resultLatch.countDown();
            }
        }, null, Activity.RESULT_CANCELED, null, null);

        assertTrue("Timed out waiting for response from helper app " + testAppName,
                resultLatch.await(10, TimeUnit.SECONDS));
        assertEquals("Security exception not reported", RequestReceiver.RESULT_SECURITY_EXCEPTION,
                result.get());
    }

    private void whitelistTestApp() {
        SystemUtil.runWithShellPermissionIdentity(
                () -> mWhitelistManager.addToWhitelist(sContext.getOpPackageName()));
    }

    private void setAlarmClockForFgs(long triggerRTC, String testAppName) throws Exception {
        final CountDownLatch resultLatch = new CountDownLatch(1);
        final AtomicInteger result = new AtomicInteger(-1);

        AlarmManager.AlarmClockInfo alarmInfo = new AlarmManager.AlarmClockInfo(triggerRTC, null);

        final Intent requestToTestApp = new Intent(TestAlarmScheduler.ACTION_SET_ALARM_CLOCK)
                .setClassName(testAppName, TestAlarmScheduler.class.getName())
                .putExtra(TestAlarmScheduler.EXTRA_ALARM_CLOCK_INFO, alarmInfo)
                .putExtra(TestAlarmScheduler.EXTRA_TEST_FGS, true)
                .addFlags(Intent.FLAG_RECEIVER_FOREGROUND);

        sContext.sendOrderedBroadcast(requestToTestApp, null, new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                result.set(getResultCode());
                resultLatch.countDown();
            }
        }, null, Activity.RESULT_CANCELED, null, null);

        assertTrue("Timed out waiting for response from helper app " + testAppName,
                resultLatch.await(10, TimeUnit.SECONDS));
        assertEquals(Activity.RESULT_OK, result.get());
    }

    @Test
    public void alarmClockAllowsFGS() throws Exception {
        setAppOp(TEST_APP_PACKAGE, AppOpsManager.MODE_ALLOWED);

        final long triggerRtc = System.currentTimeMillis() + 5_000;
        setAlarmClockForFgs(triggerRtc, TEST_APP_PACKAGE);

        final AtomicReference<String> resultHolder = new AtomicReference<>();
        final CountDownLatch alarmLatch = new CountDownLatch(1);

        final IntentFilter filter = new IntentFilter(TestAlarmReceiver.ACTION_REPORT_ALARM_EXPIRED);
        final BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Log.d(TAG, "Received response intent: " + intent);
                resultHolder.set(intent.getStringExtra(FgsTester.EXTRA_FGS_START_RESULT));
                alarmLatch.countDown();
            }
        };
        sContext.registerReceiver(receiver, filter);
        try {
            Thread.sleep(5_000);
            assertTrue("AlarmClock expiration not reported",
                    alarmLatch.await(30, TimeUnit.SECONDS));
            assertEquals("FGS result should be empty", "", resultHolder.get());
        } finally {
            sContext.unregisterReceiver(receiver);
        }
    }

    @Test
    public void setAlarmClockWithPermission() throws Exception {
        final long now = System.currentTimeMillis();
        final int numAlarms = 100;   // Number much higher than any quota.
        for (int i = 0; i < numAlarms; i++) {
            final int id = mIdGenerator.nextInt();
            final AlarmManager.AlarmClockInfo alarmClock = new AlarmManager.AlarmClockInfo(now,
                    null);
            mAlarmManager.setAlarmClock(alarmClock, getAlarmSender(id, false));
            assertTrue("Alarm " + id + " not received",
                    AlarmReceiver.waitForAlarm(id, DEFAULT_WAIT_FOR_SUCCESS));
        }
    }

    @Test
    public void setAlarmClockWithoutPermissionOrWhitelist() throws Exception {
        revokeAppOp(TEST_APP_PACKAGE);
        assertSecurityExceptionFromTestApp(RequestReceiver.ACTION_SET_ALARM_CLOCK,
                TEST_APP_PACKAGE);
    }

    @Test
    public void setExactAwiWithoutPermissionOrWhitelist() throws Exception {
        revokeAppOp(TEST_APP_PACKAGE);
        assertSecurityExceptionFromTestApp(RequestReceiver.ACTION_SET_EXACT_AND_AWI,
                TEST_APP_PACKAGE);
    }

    @Test
    public void setExactPiWithoutPermissionOrWhitelist() throws Exception {
        revokeAppOp(TEST_APP_PACKAGE);
        assertSecurityExceptionFromTestApp(RequestReceiver.ACTION_SET_EXACT_PI, TEST_APP_PACKAGE);
    }

    @Test
    public void setExactCallbackWithoutPermissionOrWhitelist() throws Exception {
        revokeAppOp(TEST_APP_PACKAGE);
        assertSecurityExceptionFromTestApp(RequestReceiver.ACTION_SET_EXACT_CALLBACK,
                TEST_APP_PACKAGE);
    }

    @Test
    public void setExactAwiWithPermissionAndWhitelist() throws Exception {
        whitelistTestApp();
        final long now = SystemClock.elapsedRealtime();
        // The user whitelist takes precedence, so the app should get unrestricted alarms.
        final int numAlarms = 100;   // Number much higher than any quota.
        for (int i = 0; i < numAlarms; i++) {
            final int id = mIdGenerator.nextInt();
            mAlarmManager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, now,
                    getAlarmSender(id, false));
            assertTrue("Alarm " + id + " not received",
                    AlarmReceiver.waitForAlarm(id, DEFAULT_WAIT_FOR_SUCCESS));
        }
    }

    private static void reclaimQuota(int quotaToReclaim) {
        final long eligibleAt = getNextEligibleTime(quotaToReclaim);
        long now;
        while ((now = SystemClock.elapsedRealtime()) < eligibleAt) {
            try {
                Thread.sleep(eligibleAt - now);
            } catch (InterruptedException e) {
                Log.e(TAG, "Thread interrupted while reclaiming quota!", e);
            }
        }
    }

    private static long getNextEligibleTime(int quotaToReclaim) {
        long t = AlarmReceiver.getNthLastAlarmTime(ALLOW_WHILE_IDLE_QUOTA - quotaToReclaim + 1);
        return t + ALLOW_WHILE_IDLE_WINDOW;
    }

    @Test
    @Ignore("Flaky on cuttlefish")  // TODO (b/171306433): Fix and re-enable
    public void setExactAwiWithPermissionWithoutWhitelist() throws Exception {
        reclaimQuota(ALLOW_WHILE_IDLE_QUOTA);

        int alarmId;
        for (int i = 0; i < ALLOW_WHILE_IDLE_QUOTA; i++) {
            final long trigger = SystemClock.elapsedRealtime() + 500;
            mAlarmManager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, trigger,
                    getAlarmSender(alarmId = mIdGenerator.nextInt(), true));
            Thread.sleep(500);
            assertTrue("Alarm " + alarmId + " not received",
                    AlarmReceiver.waitForAlarm(alarmId, DEFAULT_WAIT_FOR_SUCCESS));
        }
        long now = SystemClock.elapsedRealtime();
        final long nextTrigger = getNextEligibleTime(1);
        assertTrue("Not enough margin to test reliably", nextTrigger > now + 5000);

        mAlarmManager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, now,
                getAlarmSender(alarmId = mIdGenerator.nextInt(), true));
        assertFalse("Alarm received when no quota", AlarmReceiver.waitForAlarm(alarmId, 5000));

        now = SystemClock.elapsedRealtime();
        if (now < nextTrigger) {
            Thread.sleep(nextTrigger - now);
        }
        assertTrue("Alarm " + alarmId + " not received when back in quota",
                AlarmReceiver.waitForAlarm(alarmId, DEFAULT_WAIT_FOR_SUCCESS));
    }

    private static void assertTempWhitelistState(boolean whitelisted) {
        final String selfUid = String.valueOf(Process.myUid());
        SystemUtil.runShellCommand("cmd deviceidle tempwhitelist",
                output -> (output.contains(selfUid) == whitelisted));
    }

    @Test
    public void alarmClockGrantsWhitelist() throws Exception {
        // no device idle in auto
        assumeFalse(FeatureUtil.isAutomotive());

        final int id = mIdGenerator.nextInt();
        final AlarmManager.AlarmClockInfo alarmClock = new AlarmManager.AlarmClockInfo(
                System.currentTimeMillis() + 100, null);
        mAlarmManager.setAlarmClock(alarmClock, getAlarmSender(id, false));
        Thread.sleep(100);
        assertTrue("Alarm " + id + " not received", AlarmReceiver.waitForAlarm(id,
                DEFAULT_WAIT_FOR_SUCCESS));
        assertTempWhitelistState(true);
    }

    @Test
    public void exactAwiGrantsWhitelist() throws Exception {
        // no device idle in auto
        assumeFalse(FeatureUtil.isAutomotive());

        reclaimQuota(1);
        final int id = mIdGenerator.nextInt();
        mAlarmManager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + 100, getAlarmSender(id, true));
        Thread.sleep(100);
        assertTrue("Alarm " + id + " not received", AlarmReceiver.waitForAlarm(id,
                DEFAULT_WAIT_FOR_SUCCESS));
        assertTempWhitelistState(true);
    }

    @Test
    public void activityToRequestPermissionExists() {
        // TODO(b/188070398) Remove this when auto supports the ACTION_REQUEST_SCHEDULE_EXACT_ALARM
        assumeFalse(FeatureUtil.isAutomotive());

        final Intent request = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
        final PackageManager pm = sContext.getPackageManager();

        assertNotNull("No activity found for " + Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                pm.resolveActivity(request, 0));

        request.setData(Uri.fromParts("package", sContext.getOpPackageName(), null));

        assertNotNull("No app specific activity found for "
                + Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, pm.resolveActivity(request, 0));
    }

    /**
     * Check if a given UID is in the "can start FGS" allowlist.
     */
    private boolean checkThisAppTempAllowListed(int uid) {
        // The allowlist used internally is ActivityManagerService.mFgsStartTempAllowList. We
        // don't use the device-idle allowlist directly.

        // Run "dumpsys activity processes", and remove everything until "mFgsStartTempAllowList:".
        String output = ShellUtils.runShellCommand("dumpsys activity processes");
        output = output.replaceFirst("^.*? mFgsStartTempAllowList:$", "");

        final String uidStr = UserHandle.formatUid(uid);
        final String expected = "^\\s*" + uidStr + ":";
        for (String line : output.split("\n")) {
            if (line.matches(expected)) {
                return true;
            }
        }
        return false;
    }

    private void prepareTestAppForBroadcast(ComponentName receiver) {
        // Just send an explicit foreground broadcast to the test app to make sure
        // the app is out of force-stop.
        SystemUtil.runWithShellPermissionIdentity(
                () -> mPackageManager.setComponentEnabledSetting(receiver,
                        PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                        PackageManager.DONT_KILL_APP));
        Log.d(TAG, "Un-force-stoppping the test app");
        Intent i = new Intent("android.app.action.cts.ACTION_PING");
        i.setComponent(receiver);
        i.setFlags(Intent.FLAG_RECEIVER_FOREGROUND);
        sContext.sendBroadcast(i);
    }

    @Test
    public void scheduleExactAlarmPermissionStateChangedSentAppOp() throws Exception {
        // Revoke the permission, and remove it from the temp-allowlist.
        prepareTestAppForBroadcast(mPermissionChangeReceiver);
        Log.d(TAG, "Revoking the appop");
        revokeAppOp(TEST_APP_PACKAGE);
        removeFromWhitelists(TEST_APP_PACKAGE);

        final int uid = Utils.getPackageUid(TEST_APP_PACKAGE);
        TestUtils.waitUntil("Package still allowlisted",
                () -> !checkThisAppTempAllowListed(uid));

        Thread.sleep(1000); // Give the system a little time to settle down.

        final IntentFilter filter = new IntentFilter(
                PermissionStateChangedReceiver.ACTION_FGS_START_RESULT);
        final AtomicReference<String> resultHolder = new AtomicReference<>();
        final CountDownLatch latch = new CountDownLatch(1);
        final BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Log.d(TAG, "Received response intent: " + intent);
                resultHolder.set(intent.getStringExtra(
                        FgsTester.EXTRA_FGS_START_RESULT));
                latch.countDown();
            }
        };
        sContext.registerReceiver(receiver, filter);
        try {
            Log.d(TAG, "Granting the appop");
            setAppOp(TEST_APP_PACKAGE, AppOpsManager.MODE_ALLOWED);

            assertTrue("Didn't receive response",
                    latch.await(30, TimeUnit.SECONDS));
            assertEquals("Failure message should be empty", "", resultHolder.get());
        } finally {
            sContext.unregisterReceiver(receiver);
        }
    }

    @Test
    public void scheduleExactAlarmPermissionStateChangedSentDenyListSdk32() throws Exception {
        // App is targeting SDK 32, deny list will dictate the default grant state.
        prepareTestAppForBroadcast(mPermissionChangeReceiver32);

        // App op hasn't been touched, should be default.
        Log.d(TAG, "Putting in deny list");
        mDeviceConfigHelper.with("exact_alarm_deny_list", TEST_APP_WITH_SCHEDULE_EXACT_ALARM_32)
                .commitAndAwaitPropagation();
        removeFromWhitelists(TEST_APP_WITH_SCHEDULE_EXACT_ALARM_32);

        final int uid = Utils.getPackageUid(TEST_APP_WITH_SCHEDULE_EXACT_ALARM_32);
        TestUtils.waitUntil("Package still allowlisted",
                () -> !checkThisAppTempAllowListed(uid));

        final IntentFilter filter = new IntentFilter(
                PermissionStateChangedReceiver.ACTION_FGS_START_RESULT);
        final AtomicReference<String> resultHolder = new AtomicReference<>();
        final CountDownLatch latch = new CountDownLatch(1);
        final BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Log.d(TAG, "Received response intent: " + intent);
                resultHolder.set(intent.getStringExtra(
                        FgsTester.EXTRA_FGS_START_RESULT));
                latch.countDown();
            }
        };
        sContext.registerReceiver(receiver, filter);
        try {
            Log.d(TAG, "Removing from deny list");
            mDeviceConfigHelper.without("exact_alarm_deny_list").commitAndAwaitPropagation();

            assertTrue("Didn't receive response",
                    latch.await(30, TimeUnit.SECONDS));
            assertEquals("Failure message should be empty", "", resultHolder.get());
        } finally {
            sContext.unregisterReceiver(receiver);
        }
    }
}
