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

package android.car.cts;

import static com.google.common.truth.Truth.assertWithMessage;

import android.cts.statsdatom.lib.ConfigUtils;
import android.cts.statsdatom.lib.DeviceUtils;
import android.cts.statsdatom.lib.ReportUtils;

import com.android.compatibility.common.util.PollingCheck;
import com.android.internal.os.StatsdConfigProto.StatsdConfig;
import com.android.os.AtomsProto.Atom;
import com.android.os.AtomsProto.CarWatchdogIoOveruseStats;
import com.android.os.AtomsProto.CarWatchdogIoOveruseStatsReported;
import com.android.os.AtomsProto.CarWatchdogKillStatsReported;
import com.android.os.StatsLog.EventMetricData;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RunWith(DeviceJUnit4ClassRunner.class)
public class CarWatchdogHostTest extends CarHostJUnit4TestCase {
    public static final String TAG = CarWatchdogHostTest.class.getSimpleName();

    /**
     * CarWatchdog app package.
     */
    protected static final String WATCHDOG_APP_PKG = "android.car.cts.watchdog.sharedapp";

    /**
     * Second CarWatchdog app package.
     */
    protected static final String WATCHDOG_APP_PKG_2 = "android.car.cts.watchdog.second.sharedapp";

    /**
     * CarWatchdog app shared user id.
     */
    protected static final String WATCHDOG_APP_SHARED_USER_ID =
            "shared:android.car.cts.uid.watchdog.sharedapp";

    /**
     * The class name of the main activity in the APK.
     */
    private static final String ACTIVITY_CLASS = APP_PKG + ".CarWatchdogTestActivity";

    /**
     * The command to start a custom performance collection with CarWatchdog.
     */
    private static final String START_CUSTOM_PERF_COLLECTION_CMD =
            "dumpsys android.automotive.watchdog.ICarWatchdog/default --start_perf --max_duration"
                    + " 600 --interval 1";

    /**
     * The command to stop a custom performance collection in CarWatchdog.
     */
    private static final String STOP_CUSTOM_PERF_COLLECTION_CMD =
            "dumpsys android.automotive.watchdog.ICarWatchdog/default --stop_perf > /dev/null";

    /**
     * The command to reset I/O overuse counters in the adb shell, which clears any previous
     * stats saved by watchdog.
     */
    private static final String RESET_RESOURCE_OVERUSE_CMD = String.format(
            "dumpsys android.automotive.watchdog.ICarWatchdog/default "
                    + "--reset_resource_overuse_stats %s,%s", APP_PKG, WATCHDOG_APP_SHARED_USER_ID);

    /**
     * The command to get I/O overuse foreground bytes threshold in the adb shell.
     */
    private static final String GET_IO_OVERUSE_FOREGROUNG_BYTES_CMD =
            "cmd car_service watchdog-io-get-3p-foreground-bytes";

    /**
     * The command to set I/O overuse foreground bytes threshold in the adb shell.
     */
    private static final String SET_IO_OVERUSE_FOREGROUNG_BYTES_CMD =
            "cmd car_service watchdog-io-set-3p-foreground-bytes";

    private static final String DEFINE_ENABLE_DISPLAY_POWER_POLICY_CMD =
            "cmd car_service define-power-policy cts_car_watchdog_enable_display "
                    + "--enable DISPLAY";

    private static final String DEFINE_DISABLE_DISPLAY_POWER_POLICY_CMD =
            "cmd car_service define-power-policy cts_car_watchdog_disable_display "
                    + "--disable DISPLAY";

    private static final String APPLY_ENABLE_DISPLAY_POWER_POLICY_CMD =
            "cmd car_service apply-power-policy cts_car_watchdog_enable_display";

    private static final String APPLY_DISABLE_DISPLAY_POWER_POLICY_CMD =
            "cmd car_service apply-power-policy cts_car_watchdog_disable_display";

    private static final String FAILED_CUSTOM_COLLECTION_MSG = "Cannot start a custom collection";

    private static final long FIFTY_MEGABYTES = 1024 * 1024 * 50;
    private static final long TWO_HUNDRED_MEGABYTES = 1024 * 1024 * 200;

    private static final int RECURRING_OVERUSE_COUNT = 3;

    private static final Pattern DUMP_PATTERN = Pattern.compile(
            "CarWatchdogTestActivity:\\s(.+)");

    private static final Pattern FOREGROUND_BYTES_PATTERN = Pattern.compile(
            "foregroundModeBytes = (\\d+)");

    private static final long START_CUSTOM_COLLECTION_TIMEOUT_MS = 30_000;
    private static final long WATCHDOG_ACTION_TIMEOUT_MS = 30_000;

    private boolean mDidModifyDateTime;
    private long mOriginalForegroundBytes;

    @Before
    public void dateSetUp() throws Exception {
        checkAndSetDate();
    }

    @After
    public void dateReset() throws Exception {
        checkAndResetDate();
    }

    @Before
    public void setUp() throws Exception {
        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());
        executeCommand(DEFINE_ENABLE_DISPLAY_POWER_POLICY_CMD);
        executeCommand(DEFINE_DISABLE_DISPLAY_POWER_POLICY_CMD);
        mOriginalForegroundBytes = parseForegroundBytesFromMessage(executeCommand(
                GET_IO_OVERUSE_FOREGROUNG_BYTES_CMD));
        executeCommand("%s %d", SET_IO_OVERUSE_FOREGROUNG_BYTES_CMD, TWO_HUNDRED_MEGABYTES);
        executeCommand("logcat -c");
        startCustomCollection();
        executeCommand(RESET_RESOURCE_OVERUSE_CMD);
    }

    @After
    public void tearDown() throws Exception {
        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());
        executeCommand(APPLY_ENABLE_DISPLAY_POWER_POLICY_CMD);
        // Enable the CTS packages by running the reset resource overuse command.
        executeCommand(RESET_RESOURCE_OVERUSE_CMD);
        executeCommand(STOP_CUSTOM_PERF_COLLECTION_CMD);
        executeCommand("%s %d", SET_IO_OVERUSE_FOREGROUNG_BYTES_CMD, mOriginalForegroundBytes);
    }

    @Test
    public void testIoOveruseKillAfterDisplayTurnOff() throws Exception {
        uploadStatsdConfig(APP_PKG);

        for (int i = 0; i < RECURRING_OVERUSE_COUNT; ++i) {
            overuseDiskIo(APP_PKG);
            verifyAtomIoOveruseStatsReported(APP_PKG, /* overuseTimes= */ i + 1);
            ReportUtils.clearReports(getDevice());
        }

        executeCommand(APPLY_DISABLE_DISPLAY_POWER_POLICY_CMD);

        verifyTestAppsKilled(APP_PKG);
        verifyAtomKillStatsReported(APP_PKG);
    }

    @Test
    public void testIoOveruseKillAfterDisplayTurnOffWithSharedUserIdApps() throws Exception {
        // Stats collection is based on uid. Packages with shared uids can be used interchangeably.
        uploadStatsdConfig(WATCHDOG_APP_PKG);

        for (int i = 0; i < RECURRING_OVERUSE_COUNT; i++) {
            overuseDiskIo(i % 2 == 0 ? WATCHDOG_APP_PKG : WATCHDOG_APP_PKG_2);
            verifyAtomIoOveruseStatsReported(i % 2 == 0 ? WATCHDOG_APP_PKG_2 : WATCHDOG_APP_PKG,
                    /* overuseTimes= */ i + 1);
            ReportUtils.clearReports(getDevice());
        }

        executeCommand(APPLY_DISABLE_DISPLAY_POWER_POLICY_CMD);

        verifyTestAppsKilled(WATCHDOG_APP_PKG, WATCHDOG_APP_PKG_2);
        verifyAtomKillStatsReported(WATCHDOG_APP_PKG);
    }

    private void uploadStatsdConfig(String packageName) throws Exception {
        StatsdConfig.Builder config = ConfigUtils.createConfigBuilder("AID_SYSTEM");
        ConfigUtils.addEventMetricForUidAtom(config,
                Atom.CAR_WATCHDOG_IO_OVERUSE_STATS_REPORTED_FIELD_NUMBER,
                /* uidInAttributionChain= */ false, packageName);
        ConfigUtils.addEventMetricForUidAtom(config,
                Atom.CAR_WATCHDOG_KILL_STATS_REPORTED_FIELD_NUMBER,
                /* uidInAttributionChain= */ false, packageName);
        ConfigUtils.uploadConfig(getDevice(), config);
    }

    private void verifyAtomIoOveruseStatsReported(String packageName, int overuseTimes)
            throws Exception {
        List<EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());
        assertWithMessage("Reported I/O overuse event metrics data").that(data).hasSize(1);

        CarWatchdogIoOveruseStatsReported atom =
                data.get(0).getAtom().getCarWatchdogIoOveruseStatsReported();

        int appUid = DeviceUtils.getAppUid(getDevice(), packageName);
        assertWithMessage("UID in atom from " + overuseTimes + " overuse").that(atom.getUid())
                .isEqualTo(appUid);
        assertWithMessage("Atom has I/O overuse stats from " + overuseTimes + " overuse")
                .that(atom.hasIoOveruseStats()).isTrue();
        verifyAtomIoOveruseStats(atom.getIoOveruseStats(), overuseTimes * TWO_HUNDRED_MEGABYTES,
                "I/O overuse stats atom from " + overuseTimes + " overuse");
    }

    private void verifyAtomKillStatsReported(String packageName)
            throws Exception {
        List<EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());
        assertWithMessage("Reported kill event metrics data").that(data).hasSize(1);

        CarWatchdogKillStatsReported atom =
                data.get(0).getAtom().getCarWatchdogKillStatsReported();

        int appUid = DeviceUtils.getAppUid(getDevice(), packageName);
        assertWithMessage("UID in kill stats").that(atom.getUid()).isEqualTo(appUid);
        assertWithMessage("Kill reason from kill stats").that(atom.getKillReason())
                .isEqualTo(CarWatchdogKillStatsReported.KillReason.KILLED_ON_IO_OVERUSE);
        assertWithMessage("System state from kill stats").that(atom.getSystemState())
                .isEqualTo(CarWatchdogKillStatsReported.SystemState.USER_NO_INTERACTION_MODE);
        assertWithMessage("Atom has I/O overuse stats from overuse kill")
                .that(atom.hasIoOveruseStats()).isTrue();
        verifyAtomIoOveruseStats(atom.getIoOveruseStats(),
                RECURRING_OVERUSE_COUNT * TWO_HUNDRED_MEGABYTES,
                "I/O overuse stats atom from overuse kill");
    }

    private void verifyAtomIoOveruseStats(CarWatchdogIoOveruseStats ioOveruseStats,
            long foregroundWrittenBytes, String statsType) {
        assertWithMessage(statsType + " has period").that(ioOveruseStats.hasPeriod()).isTrue();
        assertWithMessage("Period in " + statsType).that(ioOveruseStats.getPeriod())
                .isEqualTo(CarWatchdogIoOveruseStats.Period.DAILY);
        assertWithMessage(statsType + " has threshold").that(ioOveruseStats.hasThreshold())
                .isTrue();
        assertWithMessage("Foreground threshold bytes in " + statsType)
                .that(ioOveruseStats.getThreshold().getForegroundBytes())
                .isEqualTo(TWO_HUNDRED_MEGABYTES);
        assertWithMessage(statsType + " has written bytes").that(ioOveruseStats.hasWrittenBytes())
                .isTrue();
        // Watchdog daemon's polling/syncing interval and the disk I/O writes performed by the
        // device side app are asynchronous. So, the actual number of bytes written by the app might
        // be greater than the expected written bytes. Thus verify that the reported written bytes
        // are in the range of 50MiB.
        assertWithMessage("Foreground written bytes in " + statsType)
                .that(ioOveruseStats.getWrittenBytes().getForegroundBytes())
                .isAtLeast(foregroundWrittenBytes);
        assertWithMessage("Foreground written bytes in " + statsType)
                .that(ioOveruseStats.getWrittenBytes().getForegroundBytes())
                .isAtMost(foregroundWrittenBytes + FIFTY_MEGABYTES);
    }

    private void overuseDiskIo(String packageName) throws Exception {
        startMainActivity(packageName);

        long remainingBytes = readForegroundBytesFromActivityDump(packageName);

        sendBytesToKillApp(remainingBytes, packageName);

        remainingBytes = readForegroundBytesFromActivityDump(packageName);

        assertWithMessage("Application " + packageName + "'s remaining write bytes")
                .that(remainingBytes).isEqualTo(0);
    }

    private long readForegroundBytesFromActivityDump(String packageName) throws Exception {
        AtomicReference<String> notification = new AtomicReference<>();
        PollingCheck.check("No notification received in the activity dump",
                WATCHDOG_ACTION_TIMEOUT_MS,
                () -> {
                    String dump = fetchActivityDumpsys(packageName);
                    if (dump.startsWith("INFO") && dump.contains("--Notification--")) {
                        notification.set(dump);
                        return true;
                    }
                    return false;
                });

        return parseForegroundBytesFromMessage(notification.get());
    }

    private long parseForegroundBytesFromMessage(String message) throws IllegalArgumentException {
        Matcher m = FOREGROUND_BYTES_PATTERN.matcher(message);
        if (m.find()) {
            return Long.parseLong(m.group(1));
        }
        throw new IllegalArgumentException("Invalid message format: " + message);
    }

    private void verifyTestAppsKilled(String... packageNames) throws Exception {
        ArrayList<String> packages = new ArrayList<>(List.of(packageNames));
        try {
            PollingCheck.check("Failed to kill applications", WATCHDOG_ACTION_TIMEOUT_MS, () -> {
                for (int i = packages.size() - 1; i >= 0; i--) {
                    // Check activity dump for errors. Throws exception on error.
                    String packageName = packages.get(i);
                    fetchActivityDumpsys(packageName);
                    if (!isPackageRunning(packageName)) {
                        packages.remove(i);
                    }
                }
                return packages.isEmpty();
            });
        } catch (AssertionError e) {
            assertWithMessage("Failed to kill applications: %s", packages).fail();
        }
    }

    private String fetchActivityDumpsys(String packageName) throws Exception {
        String dump = executeCommand("dumpsys activity %s/%s", packageName, ACTIVITY_CLASS);
        Matcher m = DUMP_PATTERN.matcher(dump);
        if (!m.find()) {
            return "";
        }
        String message = Objects.requireNonNull(m.group(1)).trim();
        if (message.startsWith("ERROR")) {
            throw new Exception(message);
        }
        return message;
    }

    private void startMainActivity(String packageName) throws Exception {
        String result = executeCommand("pm clear %s", packageName);
        assertWithMessage("pm clear").that(result.trim()).isEqualTo("Success");

        executeCommand("am start -W -a android.intent.action.MAIN -n %s/%s", packageName,
                ACTIVITY_CLASS);

        assertWithMessage("Is %s running?", packageName).that(isPackageRunning(packageName))
                .isTrue();
    }

    private void sendBytesToKillApp(long remainingBytes, String appPkg) throws Exception {
        executeCommand(
                "am start -W -a android.intent.action.MAIN -n %s/%s --el bytes_to_kill %d",
                appPkg, ACTIVITY_CLASS, remainingBytes);
    }

    private void checkAndSetDate() throws Exception {
        // Get date in ISO-8601 format
        LocalDateTime now = LocalDateTime.parse(executeCommand("date +%%FT%%T").trim());
        if (now.getHour() < 23) {
            return;
        }
        executeCommand("date %s", now.minusHours(1));
        CLog.d(TAG, "DateTime changed from %s to %s", now, now.minusHours(1));
        mDidModifyDateTime = true;
    }

    private void checkAndResetDate() throws Exception {
        if (!mDidModifyDateTime) {
            return;
        }
        LocalDateTime now = LocalDateTime.parse(executeCommand("date +%%FT%%T").trim());
        executeCommand("date %s", now.plusHours(1));
        CLog.d(TAG, "DateTime changed from %s to %s", now, now.plusHours(1));
    }

    private void startCustomCollection() throws Exception {
        PollingCheck.check("Could not start the custom collection.",
                START_CUSTOM_COLLECTION_TIMEOUT_MS,
                () -> !executeCommand(START_CUSTOM_PERF_COLLECTION_CMD)
                        .contains(FAILED_CUSTOM_COLLECTION_MSG));
    }
}
