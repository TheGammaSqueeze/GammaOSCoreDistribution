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

package android.os.cts;

import static com.android.compatibility.common.util.SystemUtil.runShellCommand;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;

import android.content.pm.PackageManager;
import android.os.SystemClock;
import android.platform.test.annotations.AppModeFull;
import android.util.Range;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.ThrowingRunnable;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;

@RunWith(AndroidJUnit4.class)
public class SystemClockSntpTest {
    // Mocked server response. Refer to SntpClientTest.
    private static final String MOCKED_NTP_RESPONSE =
            "240206ec"
                    + "00000165"
                    + "000000b2"
                    + "ddfd4729"
                    + "d9ca9446820a5000"
                    + "d9ca9451938a3771"
                    + "d9ca945194bd3fff"
                    + "d9ca945194bd4001";
    // The midpoint between d9ca945194bd3fff and d9ca945194bd4001, d9ca9451.94bd4000 represents
    // (decimal) 1444943313.581012726 seconds in the Unix epoch, which is
    // ~2015-10-15 21:08:33.581 UTC.
    private static long MOCKED_NTP_TIMESTAMP = 1444943313581L;
    private static long TEST_NTP_TIMEOUT_MILLIS = 300L;

    private SntpTestServer mServer;
    private Instant mSetupInstant;
    private long mSetupElapsedRealtimeMillis;

    private boolean isWatch() {
        return ApplicationProvider.getApplicationContext().getPackageManager()
                .hasSystemFeature(PackageManager.FEATURE_WATCH);
    }

    @Before
    public void setUp() {
        mSetupInstant = Instant.now();
        mSetupElapsedRealtimeMillis = SystemClock.elapsedRealtime();
    }

    @After
    public void tearDown() {
        // Restore NTP server configuration.
        executeShellCommand("cmd network_time_update_service set_server_config");
        // Clear any stored fake NTP time that may have been introduced by tests.
        executeShellCommand("cmd network_time_update_service clear_time");
        // Try to refresh the NTP time from a real server (this may fail to have any effect if the
        // real server is unreachable).
        executeShellCommand("cmd network_time_update_service force_refresh");

        // If the system clock has been set to a time before or significantly after mSetupInstant as
        // a result of running tests, make best efforts to restore it to close to what it was to
        // avoid interfering with later tests, e.g. the refresh above might have failed, and tests
        // might be leaving the system clock set to a time in history / in the future that could
        // cause root cert validity checks to fail. The system clock will have been left unchanged
        // by tests if NTP isn't being used as the primary time zone detection mechanism or if the
        // times used in tests were obviously invalid and rejected by the time detector.
        Instant currentSystemClockTime = Instant.now();
        if (currentSystemClockTime.isBefore(mSetupInstant)
                || currentSystemClockTime.isAfter(mSetupInstant.plus(Duration.ofHours(1)))) {
            // Adjust mSetupInstant for (approximately) time elapsed.
            Duration timeElapsed = Duration.ofMillis(
                    SystemClock.elapsedRealtime() - mSetupElapsedRealtimeMillis);
            Instant newNow = mSetupInstant.plus(timeElapsed);

            // Set the system clock directly as there is currently no way easy way to inject time
            // suggestions into the time_detector service from the commandline.
            executeShellCommand("cmd alarm set-time " + newNow.toEpochMilli());
        }
    }

    // b/260031002 - this test breaks with newer mainline modules on T due to permission issues with
    // the command line commands it uses. Platform changes are required to fix, so it has to be
    // suppressed in CTS for T.
    @Ignore
    @AppModeFull(reason = "Cannot bind socket in instant app mode")
    @Test
    public void testCurrentNetworkTimeClock() throws Exception {
        assumeFalse("network_time_update_service does not exist on Wear", isWatch());
        // Start a local SNTP test server. But does not setup a fake response.
        // So the server will not reply to any request.
        runWithShellPermissionIdentity(() -> mServer = new SntpTestServer());

        // Write test server address into settings.
        executeShellCommand(
                "cmd network_time_update_service set_server_config --hostname "
                        + mServer.getAddress().getHostAddress()
                        + " --port " + mServer.getPort()
                        + " --timeout_millis " + TEST_NTP_TIMEOUT_MILLIS);

        // Clear current NTP value and verify it throws exception.
        executeShellCommand("cmd network_time_update_service clear_time");

        // Verify the case where the device hasn't made an NTP request yet.
        assertThrows(DateTimeException.class, () -> SystemClock.currentNetworkTimeClock().millis());

        // Trigger NtpTrustedTime refresh with the new command.
        executeShellCommandAndAssertOutput(
                "cmd network_time_update_service force_refresh", "false");

        // Verify the returned clock throws since there is still no previous NTP fix.
        assertThrows(DateTimeException.class, () -> SystemClock.currentNetworkTimeClock().millis());

        // Setup fake responses (Refer to SntpClientTest). And trigger NTP refresh.
        mServer.setServerReply(HexEncoding.decode(MOCKED_NTP_RESPONSE));

        // After force_refresh, network_time_update_service should have associated
        // MOCKED_NTP_TIMESTAMP with an elapsedRealtime() value between
        // beforeRefreshElapsedMillis and afterRefreshElapsedMillis.
        final long beforeRefreshElapsedMillis = SystemClock.elapsedRealtime();
        executeShellCommandAndAssertOutput("cmd network_time_update_service force_refresh", "true");
        final long afterRefreshElapsedMillis = SystemClock.elapsedRealtime();

        // Request the current Unix epoch time. Assert value of SystemClock#currentNetworkTimeClock.
        assertCurrentNetworkTimeClockInBounds(MOCKED_NTP_TIMESTAMP, beforeRefreshElapsedMillis,
                afterRefreshElapsedMillis);

        // Simulate some time passing and verify that SystemClock returns an updated time
        // using the same NTP signal.
        final long PASSED_DURATION_MS = 100L;
        Thread.sleep(PASSED_DURATION_MS);

        // Request the current Unix epoch time again. Verify that SystemClock returns an
        // updated time using the same NTP signal.
        assertCurrentNetworkTimeClockInBounds(MOCKED_NTP_TIMESTAMP, beforeRefreshElapsedMillis,
                afterRefreshElapsedMillis);

        // Remove fake server response and trigger NTP refresh to simulate a failed refresh.
        mServer.setServerReply(null);
        executeShellCommandAndAssertOutput(
                "cmd network_time_update_service force_refresh", "false");

        // Verify that SystemClock still returns an updated time using the same NTP signal.
        assertCurrentNetworkTimeClockInBounds(MOCKED_NTP_TIMESTAMP, beforeRefreshElapsedMillis,
                afterRefreshElapsedMillis);
    }

    private static void executeShellCommand(String command) {
        executeShellCommandAndAssertOutput(command, null);
    }

    private static void executeShellCommandAndAssertOutput(
            String command, String expectedOutput) {
        final String trimmedResult = runShellCommand(command).trim();
        if (expectedOutput != null) {
            assertEquals(expectedOutput, trimmedResult);
        }
    }

    private static void runWithShellPermissionIdentity(ThrowingRunnable command)
            throws Exception {
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .adoptShellPermissionIdentity();
        try {
            command.run();
        } finally {
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .dropShellPermissionIdentity();
        }
    }

    /** Verify the given value is in range [lower, upper] */
    private static void assertInRange(String tag, long value, long lower, long upper) {
        final Range range = new Range(lower, upper);
        assertTrue(tag + ": " + value + " is not within range [" + lower + ", " + upper + "]",
                range.contains(value));
    }

    private static void assertCurrentNetworkTimeClockInBounds(long expectedTimestamp,
            long beforeRefreshElapsedMillis, long afterRefreshElapsedMillis) {
        final long beforeQueryElapsedMillis = SystemClock.elapsedRealtime();
        final long networkEpochMillis = SystemClock.currentNetworkTimeClock().millis();
        final long afterQueryElapsedMillis = SystemClock.elapsedRealtime();

        // Calculate the lower/upper bound base on the elapsed time of refreshing.
        final long lowerBoundNetworkEpochMillis =
                expectedTimestamp + (beforeQueryElapsedMillis - afterRefreshElapsedMillis);
        final long upperBoundNetworkEpochMillis =
                expectedTimestamp + (afterQueryElapsedMillis - beforeRefreshElapsedMillis);
        assertInRange("Network time", networkEpochMillis, lowerBoundNetworkEpochMillis,
                upperBoundNetworkEpochMillis);
    }
}
