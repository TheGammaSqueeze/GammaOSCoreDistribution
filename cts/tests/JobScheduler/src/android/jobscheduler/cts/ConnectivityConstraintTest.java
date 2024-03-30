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
package android.jobscheduler.cts;

import static android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET;
import static android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED;
import static android.net.NetworkCapabilities.TRANSPORT_CELLULAR;

import android.annotation.TargetApi;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.wifi.WifiManager;
import android.platform.test.annotations.RequiresDevice;
import android.provider.Settings;
import android.util.Log;

import com.android.compatibility.common.util.AppStandbyUtils;
import com.android.compatibility.common.util.BatteryUtils;
import com.android.compatibility.common.util.SystemUtil;

/**
 * Schedules jobs with the {@link android.app.job.JobScheduler} that have network connectivity
 * constraints.
 * Requires manipulating the {@link android.net.wifi.WifiManager} to ensure an unmetered network.
 * Similarly, requires that the phone be connected to a wifi hotspot, or else the test will fail.
 */
@TargetApi(21)
@RequiresDevice // Emulators don't always have access to wifi/network
public class ConnectivityConstraintTest extends BaseJobSchedulerTest {
    private static final String TAG = "ConnectivityConstraintTest";

    /** Unique identifier for the job scheduled by this suite of tests. */
    public static final int CONNECTIVITY_JOB_ID = ConnectivityConstraintTest.class.hashCode();
    /** Wait this long before timing out the test. */
    private static final long DEFAULT_TIMEOUT_MILLIS = 30000L; // 30 seconds.

    private NetworkingHelper mNetworkingHelper;
    private WifiManager mWifiManager;
    private ConnectivityManager mCm;

    /** Whether the device running these tests supports WiFi. */
    private boolean mHasWifi;
    /** Whether the device running these tests supports telephony. */
    private boolean mHasTelephony;
    /** Track whether the restricted bucket was enabled in case we toggle it. */
    private String mInitialRestrictedBucketEnabled;

    private JobInfo.Builder mBuilder;

    private TestAppInterface mTestAppInterface;

    @Override
    public void setUp() throws Exception {
        super.setUp();

        mWifiManager = (WifiManager) getContext().getSystemService(Context.WIFI_SERVICE);
        mCm = (ConnectivityManager) getContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        mNetworkingHelper = new NetworkingHelper(getInstrumentation(), getContext());

        PackageManager packageManager = mContext.getPackageManager();
        mHasWifi = packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI);
        mHasTelephony = packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY);
        mBuilder = new JobInfo.Builder(CONNECTIVITY_JOB_ID, kJobServiceComponent);

        if (mHasWifi) {
            mNetworkingHelper.ensureSavedWifiNetwork();
        }
        mInitialRestrictedBucketEnabled = Settings.Global.getString(mContext.getContentResolver(),
                Settings.Global.ENABLE_RESTRICTED_BUCKET);
        setDataSaverEnabled(false);
        mNetworkingHelper.setAllNetworksEnabled(true);
        // Force the test app out of the never bucket.
        SystemUtil.runShellCommand("am set-standby-bucket "
                + TestAppInterface.TEST_APP_PACKAGE + " rare");
    }

    @Override
    public void tearDown() throws Exception {
        if (mTestAppInterface != null) {
            mTestAppInterface.cleanup();
        }
        mJobScheduler.cancel(CONNECTIVITY_JOB_ID);

        BatteryUtils.runDumpsysBatteryReset();

        // Restore initial restricted bucket setting.
        Settings.Global.putString(mContext.getContentResolver(),
                Settings.Global.ENABLE_RESTRICTED_BUCKET, mInitialRestrictedBucketEnabled);

        // Ensure that we leave WiFi in its previous state.
        mNetworkingHelper.tearDown();

        super.tearDown();
    }

    // --------------------------------------------------------------------------------------------
    // Positives - schedule jobs under conditions that require them to pass.
    // --------------------------------------------------------------------------------------------

    /**
     * Schedule a job that requires a WiFi connection, and assert that it executes when the device
     * is connected to WiFi. This will fail if a wifi connection is unavailable.
     */
    public void testUnmeteredConstraintExecutes_withWifi() throws Exception {
        if (!mHasWifi) {
            Log.d(TAG, "Skipping test that requires the device be WiFi enabled.");
            return;
        }
        setWifiMeteredState(false);

        kTestEnvironment.setExpectedExecutions(1);
        mJobScheduler.schedule(
                mBuilder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED)
                        .build());

        runSatisfiedJob(CONNECTIVITY_JOB_ID);

        assertTrue("Job with unmetered constraint did not fire on WiFi.",
                kTestEnvironment.awaitExecution());
    }

    /**
     * Schedule a job with a connectivity constraint, and ensure that it executes on WiFi.
     */
    public void testConnectivityConstraintExecutes_withWifi() throws Exception {
        if (!mHasWifi) {
            Log.d(TAG, "Skipping test that requires the device be WiFi enabled.");
            return;
        }
        setWifiMeteredState(false);

        kTestEnvironment.setExpectedExecutions(1);
        mJobScheduler.schedule(
                mBuilder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                        .build());

        runSatisfiedJob(CONNECTIVITY_JOB_ID);

        assertTrue("Job with connectivity constraint did not fire on WiFi.",
                kTestEnvironment.awaitExecution());
    }

    /**
     * Schedule a job with a generic connectivity constraint, and ensure that it executes on WiFi,
     * even with Data Saver on.
     */
    public void testConnectivityConstraintExecutes_withWifi_DataSaverOn() throws Exception {
        if (!mHasWifi) {
            Log.d(TAG, "Skipping test that requires the device be WiFi enabled.");
            return;
        }
        setWifiMeteredState(false);
        setDataSaverEnabled(true);

        kTestEnvironment.setExpectedExecutions(1);
        mJobScheduler.schedule(
                mBuilder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                        .build());

        runSatisfiedJob(CONNECTIVITY_JOB_ID);

        assertTrue("Job with connectivity constraint did not fire on unmetered WiFi.",
                kTestEnvironment.awaitExecution());
    }

    /**
     * Schedule a job with a generic connectivity constraint, and ensure that it executes
     * on a cellular data connection.
     */
    public void testConnectivityConstraintExecutes_withMobile() throws Exception {
        if (!checkDeviceSupportsMobileData()) {
            return;
        }
        disconnectWifiToConnectToMobile();

        kTestEnvironment.setExpectedExecutions(1);
        mJobScheduler.schedule(
                mBuilder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                        .build());

        runSatisfiedJob(CONNECTIVITY_JOB_ID);

        assertTrue("Job with connectivity constraint did not fire on mobile.",
                kTestEnvironment.awaitExecution());
    }

    /**
     * Schedule a job with a generic connectivity constraint, and ensure that it executes
     * on a metered wifi connection.
     */
    public void testConnectivityConstraintExecutes_withMeteredWifi() throws Exception {
        if (hasEthernetConnection()) {
            Log.d(TAG, "Skipping test since ethernet is connected.");
            return;
        }
        if (!mHasWifi) {
            return;
        }
        setWifiMeteredState(true);

        kTestEnvironment.setExpectedExecutions(1);
        mJobScheduler.schedule(
                mBuilder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).build());

        runSatisfiedJob(CONNECTIVITY_JOB_ID);

        assertTrue("Job with connectivity constraint did not fire on metered wifi.",
                kTestEnvironment.awaitExecution());
    }

    /**
     * Schedule a job with a generic connectivity constraint, and ensure that it isn't stopped when
     * the device transitions to WiFi.
     */
    public void testConnectivityConstraintExecutes_transitionNetworks() throws Exception {
        if (!mHasWifi) {
            Log.d(TAG, "Skipping test that requires the device be WiFi enabled.");
            return;
        }
        if (!checkDeviceSupportsMobileData()) {
            return;
        }
        disconnectWifiToConnectToMobile();

        kTestEnvironment.setExpectedExecutions(1);
        kTestEnvironment.setExpectedStopped();
        mJobScheduler.schedule(
                mBuilder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                        .build());

        runSatisfiedJob(CONNECTIVITY_JOB_ID);

        assertTrue("Job with connectivity constraint did not fire on mobile.",
                kTestEnvironment.awaitExecution());

        connectToWifi();
        assertFalse(
                "Job with connectivity constraint was stopped when network transitioned to WiFi.",
                kTestEnvironment.awaitStopped());
    }

    /**
     * Schedule a job with a metered connectivity constraint, and ensure that it executes
     * on a mobile data connection.
     */
    public void testConnectivityConstraintExecutes_metered_mobile() throws Exception {
        if (!checkDeviceSupportsMobileData()) {
            return;
        }
        disconnectWifiToConnectToMobile();

        kTestEnvironment.setExpectedExecutions(1);
        mJobScheduler.schedule(
                mBuilder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_METERED)
                        .build());

        runSatisfiedJob(CONNECTIVITY_JOB_ID);
        assertTrue("Job with metered connectivity constraint did not fire on mobile.",
                kTestEnvironment.awaitExecution());
    }

    /**
     * Schedule a job with a metered connectivity constraint, and ensure that it executes
     * on a mobile data connection.
     */
    public void testConnectivityConstraintExecutes_metered_Wifi() throws Exception {
        if (hasEthernetConnection()) {
            Log.d(TAG, "Skipping test since ethernet is connected.");
            return;
        }
        if (!mHasWifi) {
            return;
        }
        setWifiMeteredState(true);


        kTestEnvironment.setExpectedExecutions(1);
        mJobScheduler.schedule(
                mBuilder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_METERED).build());

        // Since we equate "metered" to "cellular", the job shouldn't start.
        runSatisfiedJob(CONNECTIVITY_JOB_ID);
        assertTrue("Job with metered connectivity constraint fired on a metered wifi network.",
                kTestEnvironment.awaitTimeout());
    }

    /**
     * Schedule a job with a cellular connectivity constraint, and ensure that it executes
     * on a mobile data connection and is not stopped when Data Saver is turned on because the app
     * is in the foreground.
     */
    public void testCellularConstraintExecutedAndStopped_Foreground() throws Exception {
        if (hasEthernetConnection()) {
            Log.d(TAG, "Skipping test since ethernet is connected.");
            return;
        }
        if (mHasWifi) {
            setWifiMeteredState(true);
        } else if (checkDeviceSupportsMobileData()) {
            disconnectWifiToConnectToMobile();
        } else {
            // No mobile or wifi.
            return;
        }

        mTestAppInterface = new TestAppInterface(mContext, CONNECTIVITY_JOB_ID);
        mTestAppInterface.startAndKeepTestActivity();
        toggleScreenOn(true);

        mTestAppInterface.scheduleJob(false,  JobInfo.NETWORK_TYPE_ANY, false);

        mTestAppInterface.runSatisfiedJob();
        assertTrue("Job with metered connectivity constraint did not fire on a metered network.",
                mTestAppInterface.awaitJobStart(30_000));

        setDataSaverEnabled(true);
        assertFalse(
                "Job with metered connectivity constraint for foreground app was stopped when"
                        + " Data Saver was turned on.",
                mTestAppInterface.awaitJobStop(30_000));
    }

    /**
     * Schedule an expedited job that requires a network connection, and verify that it runs even
     * when if an app is idle.
     */
    public void testExpeditedJobExecutes_IdleApp() throws Exception {
        if (!AppStandbyUtils.isAppStandbyEnabled()) {
            Log.d(TAG, "App standby not enabled");
            return;
        }
        // We're skipping this test because we can't make the ethernet connection metered.
        if (hasEthernetConnection()) {
            Log.d(TAG, "Skipping test since ethernet is connected.");
            return;
        }
        if (mHasWifi) {
            setWifiMeteredState(true);
        } else if (checkDeviceSupportsMobileData()) {
            disconnectWifiToConnectToMobile();
        } else {
            Log.d(TAG, "Skipping test that requires a metered network.");
            return;
        }

        Settings.Global.putString(mContext.getContentResolver(),
                Settings.Global.ENABLE_RESTRICTED_BUCKET, "1");
        mDeviceConfigStateHelper.set("qc_max_session_count_restricted", "0");
        SystemUtil.runShellCommand("am set-standby-bucket "
                + kJobServiceComponent.getPackageName() + " restricted");
        BatteryUtils.runDumpsysBatteryUnplug();

        kTestEnvironment.setExpectedExecutions(1);
        mJobScheduler.schedule(
                mBuilder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                        .setExpedited(true)
                        .build());
        runSatisfiedJob(CONNECTIVITY_JOB_ID);

        assertTrue("Expedited job requiring connectivity did not fire when app was idle.",
                kTestEnvironment.awaitExecution());
    }

    /**
     * Schedule an expedited job that requires a network connection, and verify that it runs even
     * when Battery Saver is on.
     */
    public void testExpeditedJobExecutes_BatterySaverOn() throws Exception {
        if (!BatteryUtils.isBatterySaverSupported()) {
            Log.d(TAG, "Skipping test that requires battery saver support");
            return;
        }
        if (mHasWifi) {
            setWifiMeteredState(true);
        } else if (checkDeviceSupportsMobileData()) {
            disconnectWifiToConnectToMobile();
        } else {
            Log.d(TAG, "Skipping test that requires a metered.");
            return;
        }

        BatteryUtils.runDumpsysBatteryUnplug();
        BatteryUtils.enableBatterySaver(true);

        kTestEnvironment.setExpectedExecutions(1);
        mJobScheduler.schedule(
                mBuilder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                        .setExpedited(true)
                        .build());
        runSatisfiedJob(CONNECTIVITY_JOB_ID);

        assertTrue(
                "Expedited job requiring connectivity did not fire with Battery Saver on.",
                kTestEnvironment.awaitExecution());
    }

    /**
     * Schedule an expedited job that requires a network connection, and verify that it runs even
     * when Data Saver is on and the device is not connected to WiFi.
     */
    public void testFgExpeditedJobBypassesDataSaver() throws Exception {
        if (hasEthernetConnection()) {
            Log.d(TAG, "Skipping test since ethernet is connected.");
            return;
        }
        if (mHasWifi) {
            setWifiMeteredState(true);
        } else if (checkDeviceSupportsMobileData()) {
            disconnectWifiToConnectToMobile();
        } else {
            Log.d(TAG, "Skipping test that requires a metered network.");
            return;
        }
        setDataSaverEnabled(true);

        mTestAppInterface = new TestAppInterface(mContext, CONNECTIVITY_JOB_ID);
        mTestAppInterface.startAndKeepTestActivity();

        mTestAppInterface.scheduleJob(false,  JobInfo.NETWORK_TYPE_ANY, true);
        mTestAppInterface.runSatisfiedJob();

        assertTrue(
                "FG expedited job requiring metered connectivity did not fire with Data Saver on.",
                mTestAppInterface.awaitJobStart(DEFAULT_TIMEOUT_MILLIS));
    }

    /**
     * Schedule an expedited job that requires a network connection, and verify that it runs even
     * when multiple firewalls are active.
     */
    public void testExpeditedJobBypassesSimultaneousFirewalls_noDataSaver() throws Exception {
        if (!BatteryUtils.isBatterySaverSupported()) {
            Log.d(TAG, "Skipping test that requires battery saver support");
            return;
        }
        if (mHasWifi) {
            setWifiMeteredState(true);
        } else if (checkDeviceSupportsMobileData()) {
            disconnectWifiToConnectToMobile();
        } else {
            Log.d(TAG, "Skipping test that requires a metered network.");
            return;
        }
        if (!AppStandbyUtils.isAppStandbyEnabled()) {
            Log.d(TAG, "App standby not enabled");
            return;
        }

        Settings.Global.putString(mContext.getContentResolver(),
                Settings.Global.ENABLE_RESTRICTED_BUCKET, "1");
        mDeviceConfigStateHelper.set("qc_max_session_count_restricted", "0");
        SystemUtil.runShellCommand("am set-standby-bucket "
                + kJobServiceComponent.getPackageName() + " restricted");
        BatteryUtils.runDumpsysBatteryUnplug();
        BatteryUtils.enableBatterySaver(true);
        setDataSaverEnabled(false);

        kTestEnvironment.setExpectedExecutions(1);
        mJobScheduler.schedule(
                mBuilder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                        .setExpedited(true)
                        .build());
        runSatisfiedJob(CONNECTIVITY_JOB_ID);

        assertTrue("Expedited job requiring connectivity did not fire with multiple firewalls.",
                kTestEnvironment.awaitExecution());
    }

    // --------------------------------------------------------------------------------------------
    // Positives & Negatives - schedule jobs under conditions that require that pass initially and
    // then fail with a constraint change.
    // --------------------------------------------------------------------------------------------

    /**
     * Schedule a job with a cellular connectivity constraint, and ensure that it executes
     * on a mobile data connection and is stopped when Data Saver is turned on.
     */
    public void testCellularConstraintExecutedAndStopped() throws Exception {
        if (!checkDeviceSupportsMobileData()) {
            return;
        }
        disconnectWifiToConnectToMobile();

        mTestAppInterface = new TestAppInterface(mContext, CONNECTIVITY_JOB_ID);

        mTestAppInterface.scheduleJob(false,  JobInfo.NETWORK_TYPE_CELLULAR, false);

        mTestAppInterface.runSatisfiedJob();
        assertTrue("Job with cellular constraint did not fire on mobile.",
                mTestAppInterface.awaitJobStart(DEFAULT_TIMEOUT_MILLIS));

        setDataSaverEnabled(true);
        assertTrue(
                "Job with cellular constraint was not stopped when Data Saver was turned on.",
                mTestAppInterface.awaitJobStop(DEFAULT_TIMEOUT_MILLIS));
    }

    public void testJobParametersNetwork() throws Exception {
        mNetworkingHelper.setAllNetworksEnabled(true);

        // Everything good.
        final NetworkRequest nr = new NetworkRequest.Builder()
                .addCapability(NET_CAPABILITY_INTERNET)
                .addCapability(NET_CAPABILITY_VALIDATED)
                .build();
        JobInfo ji = mBuilder.setRequiredNetwork(nr).build();

        kTestEnvironment.setExpectedExecutions(1);
        mJobScheduler.schedule(ji);
        runSatisfiedJob(CONNECTIVITY_JOB_ID);
        assertTrue("Job didn't fire immediately", kTestEnvironment.awaitExecution());

        JobParameters params = kTestEnvironment.getLastStartJobParameters();
        assertNotNull(params.getNetwork());
        final NetworkCapabilities capabilities =
                getContext().getSystemService(ConnectivityManager.class)
                        .getNetworkCapabilities(params.getNetwork());
        assertTrue(nr.canBeSatisfiedBy(capabilities));

        if (!hasEthernetConnection()) {
            // Deadline passed with no network satisfied.
            mNetworkingHelper.setAllNetworksEnabled(false);
            ji = mBuilder
                    .setRequiredNetwork(nr)
                    .setOverrideDeadline(0)
                    .build();

            kTestEnvironment.setExpectedExecutions(1);
            mJobScheduler.schedule(ji);
            runSatisfiedJob(CONNECTIVITY_JOB_ID);
            assertTrue("Job didn't fire immediately", kTestEnvironment.awaitExecution());

            params = kTestEnvironment.getLastStartJobParameters();
            assertNull(params.getNetwork());
        }

        // No network requested
        mNetworkingHelper.setAllNetworksEnabled(true);
        ji = mBuilder.setRequiredNetwork(null).build();
        kTestEnvironment.setExpectedExecutions(1);
        mJobScheduler.schedule(ji);
        runSatisfiedJob(CONNECTIVITY_JOB_ID);
        assertTrue("Job didn't fire immediately", kTestEnvironment.awaitExecution());

        params = kTestEnvironment.getLastStartJobParameters();
        assertNull(params.getNetwork());
    }

    // --------------------------------------------------------------------------------------------
    // Negatives - schedule jobs under conditions that require that they fail.
    // --------------------------------------------------------------------------------------------

    /**
     * Schedule a job that requires a WiFi connection, and assert that it fails when the device is
     * connected to a cellular provider.
     * This test assumes that if the device supports a mobile data connection, then this connection
     * will be available.
     */
    public void testUnmeteredConstraintFails_withMobile() throws Exception {
        if (!checkDeviceSupportsMobileData()) {
            return;
        }
        disconnectWifiToConnectToMobile();

        kTestEnvironment.setExpectedExecutions(0);
        mJobScheduler.schedule(
                mBuilder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED)
                        .build());
        runSatisfiedJob(CONNECTIVITY_JOB_ID);

        assertTrue("Job requiring unmetered connectivity still executed on mobile.",
                kTestEnvironment.awaitTimeout());
    }

    /**
     * Schedule a job that requires a metered connection, and verify that it does not run when
     * the device is not connected to WiFi and Data Saver is on.
     */
    public void testMeteredConstraintFails_withMobile_DataSaverOn() throws Exception {
        if (!checkDeviceSupportsMobileData()) {
            Log.d(TAG, "Skipping test that requires the device be mobile data enabled.");
            return;
        }
        disconnectWifiToConnectToMobile();
        setDataSaverEnabled(true);

        mTestAppInterface = new TestAppInterface(mContext, CONNECTIVITY_JOB_ID);

        mTestAppInterface.scheduleJob(false,  JobInfo.NETWORK_TYPE_CELLULAR, false);
        mTestAppInterface.runSatisfiedJob();

        assertFalse("Job requiring cellular connectivity executed with Data Saver on",
                mTestAppInterface.awaitJobStop(DEFAULT_TIMEOUT_MILLIS));
    }

    /**
     * Schedule a job that requires a metered connection, and verify that it does not run when
     * the device is not connected to WiFi and Data Saver is on.
     */
    public void testEJMeteredConstraintFails_withMobile_DataSaverOn() throws Exception {
        if (!checkDeviceSupportsMobileData()) {
            Log.d(TAG, "Skipping test that requires the device be mobile data enabled.");
            return;
        }
        disconnectWifiToConnectToMobile();
        setDataSaverEnabled(true);

        mTestAppInterface = new TestAppInterface(mContext, CONNECTIVITY_JOB_ID);

        mTestAppInterface.scheduleJob(false,  JobInfo.NETWORK_TYPE_CELLULAR, true);
        mTestAppInterface.runSatisfiedJob();

        assertFalse("BG expedited job requiring cellular connectivity executed with Data Saver on",
                mTestAppInterface.awaitJobStop(DEFAULT_TIMEOUT_MILLIS));
    }

    /**
     * Schedule a job that requires a metered connection, and verify that it does not run when
     * the device is connected to an unmetered WiFi provider.
     * This test assumes that if the device supports a mobile data connection, then this connection
     * will be available.
     */
    public void testMeteredConstraintFails_withWiFi() throws Exception {
        if (!mHasWifi) {
            Log.d(TAG, "Skipping test that requires the device be WiFi enabled.");
            return;
        }
        if (!checkDeviceSupportsMobileData()) {
            Log.d(TAG, "Skipping test that requires the device be mobile data enabled.");
            return;
        }
        setWifiMeteredState(false);

        kTestEnvironment.setExpectedExecutions(0);
        mJobScheduler.schedule(
                mBuilder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_METERED)
                        .build());
        runSatisfiedJob(CONNECTIVITY_JOB_ID);

        assertTrue("Job requiring metered connectivity still executed on WiFi.",
                kTestEnvironment.awaitTimeout());
    }

    /**
     * Schedule a job that requires an unmetered connection, and verify that it does not run when
     * the device is connected to a metered WiFi provider.
     */
    public void testUnmeteredConstraintFails_withMeteredWiFi() throws Exception {
        if (hasEthernetConnection()) {
            Log.d(TAG, "Skipping test since ethernet is connected.");
            return;
        }
        if (!mHasWifi) {
            Log.d(TAG, "Skipping test that requires the device be WiFi enabled.");
            return;
        }
        setWifiMeteredState(true);

        kTestEnvironment.setExpectedExecutions(0);
        mJobScheduler.schedule(
                mBuilder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED)
                        .build());
        runSatisfiedJob(CONNECTIVITY_JOB_ID);

        assertTrue("Job requiring unmetered connectivity still executed on metered WiFi.",
                kTestEnvironment.awaitTimeout());
    }

    /**
     * Schedule a job that requires a cellular connection, and verify that it does not run when
     * the device is connected to a WiFi provider.
     */
    public void testCellularConstraintFails_withWiFi() throws Exception {
        if (!mHasWifi) {
            Log.d(TAG, "Skipping test that requires the device be WiFi enabled.");
            return;
        }
        if (!checkDeviceSupportsMobileData()) {
            Log.d(TAG, "Skipping test that requires the device be mobile data enabled.");
            return;
        }
        setWifiMeteredState(false);

        kTestEnvironment.setExpectedExecutions(0);
        mJobScheduler.schedule(
                mBuilder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_CELLULAR).build());
        runSatisfiedJob(CONNECTIVITY_JOB_ID);

        assertTrue("Job requiring cellular connectivity still executed on WiFi.",
                kTestEnvironment.awaitTimeout());
    }

    /**
     * Schedule an expedited job that requires a network connection, and verify that it runs even
     * when Data Saver is on and the device is not connected to WiFi.
     */
    public void testBgExpeditedJobDoesNotBypassDataSaver() throws Exception {
        if (hasEthernetConnection()) {
            Log.d(TAG, "Skipping test since ethernet is connected.");
            return;
        }
        if (mHasWifi) {
            setWifiMeteredState(true);
        } else if (checkDeviceSupportsMobileData()) {
            disconnectWifiToConnectToMobile();
        } else {
            Log.d(TAG, "Skipping test that requires a metered network.");
            return;
        }
        setDataSaverEnabled(true);

        mTestAppInterface = new TestAppInterface(mContext, CONNECTIVITY_JOB_ID);

        mTestAppInterface.scheduleJob(false,  JobInfo.NETWORK_TYPE_ANY, true);
        mTestAppInterface.runSatisfiedJob();

        assertFalse("BG expedited job requiring connectivity fired with Data Saver on.",
                mTestAppInterface.awaitJobStart(DEFAULT_TIMEOUT_MILLIS));
    }

    /**
     * Schedule an expedited job that requires a network connection, and verify that it runs even
     * when multiple firewalls are active.
     */
    public void testExpeditedJobDoesNotBypassSimultaneousFirewalls_withDataSaver()
            throws Exception {
        if (!BatteryUtils.isBatterySaverSupported()) {
            Log.d(TAG, "Skipping test that requires battery saver support");
            return;
        }
        if (mHasWifi) {
            setWifiMeteredState(true);
        } else if (checkDeviceSupportsMobileData()) {
            disconnectWifiToConnectToMobile();
        } else {
            Log.d(TAG, "Skipping test that requires a metered network.");
            return;
        }
        if (!AppStandbyUtils.isAppStandbyEnabled()) {
            Log.d(TAG, "App standby not enabled");
            return;
        }

        Settings.Global.putString(mContext.getContentResolver(),
                Settings.Global.ENABLE_RESTRICTED_BUCKET, "1");
        mDeviceConfigStateHelper.set("qc_max_session_count_restricted", "0");
        SystemUtil.runShellCommand("am set-standby-bucket "
                + kJobServiceComponent.getPackageName() + " restricted");
        BatteryUtils.runDumpsysBatteryUnplug();
        BatteryUtils.enableBatterySaver(true);
        setDataSaverEnabled(true);

        mTestAppInterface = new TestAppInterface(mContext, CONNECTIVITY_JOB_ID);

        mTestAppInterface.scheduleJob(false,  JobInfo.NETWORK_TYPE_ANY, true);
        mTestAppInterface.runSatisfiedJob();

        assertFalse("Expedited job fired with multiple firewalls, including data saver.",
                mTestAppInterface.awaitJobStart(DEFAULT_TIMEOUT_MILLIS));
    }

    // --------------------------------------------------------------------------------------------
    // Utility methods
    // --------------------------------------------------------------------------------------------

    /**
     * Determine whether the device running these CTS tests should be subject to tests involving
     * mobile data.
     * @return True if this device will support a mobile data connection.
     */
    private boolean checkDeviceSupportsMobileData() {
        if (!mHasTelephony) {
            Log.d(TAG, "Skipping test that requires telephony features, not supported by this" +
                    " device");
            return false;
        }
        Network[] networks = mCm.getAllNetworks();
        for (Network network : networks) {
            if (mCm.getNetworkCapabilities(network)
                    .hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                return true;
            }
        }
        Log.d(TAG, "Skipping test that requires ConnectivityManager.TYPE_MOBILE");
        return false;
    }

    private boolean hasEthernetConnection() {
        return mNetworkingHelper.hasEthernetConnection();
    }

    private void setWifiMeteredState(boolean metered) throws Exception {
        mNetworkingHelper.setWifiMeteredState(metered);
    }

    /**
     * Ensure WiFi is enabled, and block until we've verified that we are in fact connected.
     */
    private void connectToWifi() throws Exception {
        mNetworkingHelper.setWifiState(true);
    }

    /**
     * Ensure WiFi is disabled, and block until we've verified that we are in fact disconnected.
     */
    private void disconnectFromWifi() throws Exception {
        mNetworkingHelper.setWifiState(false);
    }

    /**
     * Disconnect from WiFi in an attempt to connect to cellular data. Worth noting that this is
     * best effort - there are no public APIs to force connecting to cell data. We disable WiFi
     * and wait for a broadcast that we're connected to cell.
     * We will not call into this function if the device doesn't support telephony.
     * @see #mHasTelephony
     * @see #checkDeviceSupportsMobileData()
     */
    private void disconnectWifiToConnectToMobile() throws Exception {
        mNetworkingHelper.setAllNetworksEnabled(true);
        if (mHasWifi && mWifiManager.isWifiEnabled()) {
            NetworkRequest nr = new NetworkRequest.Builder().clearCapabilities().build();
            NetworkCapabilities nc = new NetworkCapabilities.Builder()
                    .addTransportType(TRANSPORT_CELLULAR)
                    .build();
            NetworkingHelper.NetworkTracker tracker =
                    new NetworkingHelper.NetworkTracker(nc, true, mCm);
            mCm.registerNetworkCallback(nr, tracker);

            disconnectFromWifi();

            assertTrue("Device must have access to a metered network for this test.",
                    tracker.waitForStateChange());

            mCm.unregisterNetworkCallback(tracker);
        }
    }

    /**
     * Ensures that restrict background data usage policy is turned off.
     * If the policy is on, it interferes with tests that relies on metered connection.
     */
    private void setDataSaverEnabled(boolean enabled) throws Exception {
        mNetworkingHelper.setDataSaverEnabled(enabled);
    }
}
