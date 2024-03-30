/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.time.cts.host;


import static android.app.time.cts.shell.DeviceConfigKeys.LocationTimeZoneManager.KEY_LTZP_EVENT_FILTERING_AGE_THRESHOLD_MILLIS;
import static android.app.time.cts.shell.DeviceConfigKeys.NAMESPACE_SYSTEM_TIME;
import static android.app.time.cts.shell.DeviceConfigShellHelper.SYNC_DISABLED_MODE_UNTIL_REBOOT;
import static android.app.time.cts.shell.FakeTimeZoneProviderAppShellHelper.FAKE_TZPS_APP_APK;
import static android.app.time.cts.shell.FakeTimeZoneProviderAppShellHelper.FAKE_TZPS_APP_PACKAGE;
import static android.app.time.cts.shell.FakeTimeZoneProviderAppShellHelper.PROVIDER_STATE_CERTAIN;
import static android.app.time.cts.shell.FakeTimeZoneProviderAppShellHelper.PROVIDER_STATE_DISABLED;
import static android.app.time.cts.shell.FakeTimeZoneProviderAppShellHelper.PROVIDER_STATE_INITIALIZING;
import static android.app.time.cts.shell.FakeTimeZoneProviderAppShellHelper.PROVIDER_STATE_UNCERTAIN;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.app.time.ControllerStateEnum;
import android.app.time.LocationTimeZoneManagerServiceStateProto;
import android.app.time.TimeZoneProviderStateEnum;
import android.app.time.TimeZoneProviderStateProto;
import android.app.time.cts.shell.DeviceConfigShellHelper;
import android.app.time.cts.shell.DeviceShellCommandExecutor;
import android.app.time.cts.shell.FakeTimeZoneProviderAppShellHelper;
import android.app.time.cts.shell.FakeTimeZoneProviderAppShellHelper.FakeTimeZoneProviderShellHelper;
import android.app.time.cts.shell.LocationShellHelper;
import android.app.time.cts.shell.LocationTimeZoneManagerShellHelper;
import android.app.time.cts.shell.TimeZoneDetectorShellHelper;
import android.app.time.cts.shell.host.HostShellCommandExecutor;

import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;

import com.google.protobuf.Parser;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

/**
 * Host-side CTS tests for the location time zone manager service. There are plenty of unit tests
 * for individual components but manufacturers don't have to run them. This test is intended to
 * provide confidence that the specific device configuration is likely to work as it should, i.e.
 * this tests the actual location_time_zone_manager service on a given device.
 *
 * <p>Because there are a large set of possibilities, this test has to handle them all:
 * <ul>
 *     <li>location_time_zone_manager service disabled</li>
 *     <li>location_time_zone_manager service enabled, but no LTZPs configured</li>
 *     <li>location_time_zone_manager service enabled, with LTZPs configured</li>
 * </ul>
 */
@RunWith(DeviceJUnit4ClassRunner.class)
public class LocationTimeZoneManagerHostTest extends BaseHostJUnit4Test {

    private static final String NON_EXISTENT_TZPS_APP_PACKAGE = "foobar";

    private boolean mOriginalLocationEnabled;
    private boolean mOriginalAutoDetectionEnabled;
    private boolean mOriginalGeoDetectionEnabled;
    private TimeZoneDetectorShellHelper mTimeZoneDetectorShellHelper;
    private LocationTimeZoneManagerShellHelper mLocationTimeZoneManagerShellHelper;
    private DeviceConfigShellHelper mDeviceConfigShellHelper;
    private DeviceConfigShellHelper.PreTestState mDeviceConfigPreTestState;
    private LocationShellHelper mLocationShellHelper;
    private FakeTimeZoneProviderShellHelper mPrimaryFakeTimeZoneProviderShellHelper;
    private FakeTimeZoneProviderShellHelper mSecondaryFakeTimeZoneProviderShellHelper;

    @Before
    public void setUp() throws Exception {
        DeviceShellCommandExecutor shellCommandExecutor = new HostShellCommandExecutor(getDevice());
        mLocationTimeZoneManagerShellHelper =
                new LocationTimeZoneManagerShellHelper(shellCommandExecutor);

        // Confirm the service being tested is present. It can be turned off permanently in config,
        // in which case there's nothing about it to test.
        mLocationTimeZoneManagerShellHelper.assumeLocationTimeZoneManagerIsPresent();

        // Install the app that hosts the fake providers.
        // Installations are tracked in BaseHostJUnit4Test and uninstalled automatically.
        installPackage(FAKE_TZPS_APP_APK);

        mTimeZoneDetectorShellHelper = new TimeZoneDetectorShellHelper(shellCommandExecutor);
        mLocationShellHelper = new LocationShellHelper(shellCommandExecutor);
        mDeviceConfigShellHelper = new DeviceConfigShellHelper(shellCommandExecutor);

        // Stop device_config updates for the duration of the test.
        mDeviceConfigPreTestState = mDeviceConfigShellHelper.setSyncModeForTest(
                SYNC_DISABLED_MODE_UNTIL_REBOOT, NAMESPACE_SYSTEM_TIME);

        // These original values try to record the raw value of the settings before the test ran:
        // they may be ignored by the location_time_zone_manager service when they have no meaning.
        // Unfortunately, we cannot tell if the value returned is the result of setting defaults or
        // real values, which means we may not return things exactly as they were. To do better
        // would require looking at raw settings values and use internal knowledge of settings keys.
        mOriginalAutoDetectionEnabled = mTimeZoneDetectorShellHelper.isAutoDetectionEnabled();
        mOriginalGeoDetectionEnabled = mTimeZoneDetectorShellHelper.isGeoDetectionEnabled();

        mLocationTimeZoneManagerShellHelper.stop();

        // Make sure location is enabled, otherwise the geo detection feature cannot operate.
        mOriginalLocationEnabled = mLocationShellHelper.isLocationEnabledForCurrentUser();
        if (!mOriginalLocationEnabled) {
            mLocationShellHelper.setLocationEnabledForCurrentUser(true);
        }

        // Restart the location_time_zone_manager with a do-nothing test config; some settings
        // values cannot be set when the service knows that the settings won't be used. Devices
        // can be encountered with the location_time_zone_manager enabled but with no providers
        // installed. Starting the service with a valid-looking test provider config means we know
        // settings changes will be accepted regardless of the real config.
        String testPrimaryLocationTimeZoneProviderPackageName = NON_EXISTENT_TZPS_APP_PACKAGE;
        String testSecondaryLocationTimeZoneProviderPackageName = null;
        mLocationTimeZoneManagerShellHelper.startWithTestProviders(
                testPrimaryLocationTimeZoneProviderPackageName,
                testSecondaryLocationTimeZoneProviderPackageName,
                false /* recordProviderStates */);

        // Begin all tests with auto detection turned off.
        if (mOriginalAutoDetectionEnabled) {
            mTimeZoneDetectorShellHelper.setAutoDetectionEnabled(false);
        }

        // We set the device settings so that location detection will be used.
        if (!mOriginalGeoDetectionEnabled) {
            mTimeZoneDetectorShellHelper.setGeoDetectionEnabled(true);
        }

        // All tests begin with the location_time_zone_manager stopped so that fake providers can be
        // configured.
        mLocationTimeZoneManagerShellHelper.stop();

        // Make sure the fake provider APK install started above has completed before tests try to
        // use the fake providers.
        FakeTimeZoneProviderAppShellHelper fakeTimeZoneProviderAppShellHelper =
                new FakeTimeZoneProviderAppShellHelper(shellCommandExecutor);
        // Delay until the fake TZPS app can be found.
        fakeTimeZoneProviderAppShellHelper.waitForInstallation();
        mPrimaryFakeTimeZoneProviderShellHelper =
                fakeTimeZoneProviderAppShellHelper.getPrimaryLocationProviderHelper();
        mSecondaryFakeTimeZoneProviderShellHelper =
                fakeTimeZoneProviderAppShellHelper.getSecondaryLocationProviderHelper();
    }

    @After
    public void tearDown() throws Exception {
        if (!mLocationTimeZoneManagerShellHelper.isLocationTimeZoneManagerPresent()) {
            // Nothing to tear down.
            return;
        }

        // Restart the location_time_zone_manager with a test config so that the device can be set
        // back to the starting state regardless of how the test left things.
        mLocationTimeZoneManagerShellHelper.stop();
        String testPrimaryLocationTimeZoneProviderPackageName = NON_EXISTENT_TZPS_APP_PACKAGE;
        String testSecondaryLocationTimeZoneProviderPackageName = null;
        mLocationTimeZoneManagerShellHelper.startWithTestProviders(
                testPrimaryLocationTimeZoneProviderPackageName,
                testSecondaryLocationTimeZoneProviderPackageName,
                false /* recordProviderStates */);

        if (mTimeZoneDetectorShellHelper.isGeoDetectionEnabled() != mOriginalGeoDetectionEnabled) {
            mTimeZoneDetectorShellHelper.setGeoDetectionEnabled(mOriginalGeoDetectionEnabled);
        }

        if (mTimeZoneDetectorShellHelper.isAutoDetectionEnabled()
                != mOriginalAutoDetectionEnabled) {
            mTimeZoneDetectorShellHelper.setAutoDetectionEnabled(mOriginalAutoDetectionEnabled);
        }

        // Everything else can be reset without worrying about the providers.
        mLocationTimeZoneManagerShellHelper.stop();

        mLocationShellHelper.setLocationEnabledForCurrentUser(mOriginalLocationEnabled);
        mDeviceConfigShellHelper.restoreDeviceConfigStateForTest(mDeviceConfigPreTestState);

        // Attempt to start the service without test providers. It may not start if there are no
        // providers configured, but that is ok.
        mLocationTimeZoneManagerShellHelper.start();
    }

    /** Tests what happens when there's only a primary provider and it makes a suggestion. */
    @Test
    public void testOnlyPrimary_suggestionMade() throws Exception {
        String testPrimaryLocationTimeZoneProviderPackageName = FAKE_TZPS_APP_PACKAGE;
        String testSecondaryLocationTimeZoneProviderPackageName = null;
        mLocationTimeZoneManagerShellHelper.startWithTestProviders(
                testPrimaryLocationTimeZoneProviderPackageName,
                testSecondaryLocationTimeZoneProviderPackageName,
                true /* recordProviderStates */);

        // Turn on auto detection, which should activate the location time zone algorithm.
        mTimeZoneDetectorShellHelper.setAutoDetectionEnabled(true);

        mPrimaryFakeTimeZoneProviderShellHelper.assertCreated();
        mSecondaryFakeTimeZoneProviderShellHelper.assertNotCreated();

        {
            LocationTimeZoneManagerServiceStateProto serviceState = dumpServiceState();
            assertControllerStateHistory(serviceState,
                    ControllerStateEnum.CONTROLLER_STATE_PROVIDERS_INITIALIZING,
                    ControllerStateEnum.CONTROLLER_STATE_STOPPED,
                    ControllerStateEnum.CONTROLLER_STATE_INITIALIZING);
            assertNoLastSuggestion(serviceState);
            assertProviderStates(serviceState.getPrimaryProviderStatesList(),
                    TimeZoneProviderStateEnum.TIME_ZONE_PROVIDER_STATE_DISABLED,
                    TimeZoneProviderStateEnum.TIME_ZONE_PROVIDER_STATE_INITIALIZING);
            mPrimaryFakeTimeZoneProviderShellHelper.assertCurrentState(PROVIDER_STATE_INITIALIZING);

            assertProviderStates(serviceState.getSecondaryProviderStatesList(),
                    TimeZoneProviderStateEnum.TIME_ZONE_PROVIDER_STATE_DISABLED);
        }
        mLocationTimeZoneManagerShellHelper.clearRecordedProviderStates();

        mPrimaryFakeTimeZoneProviderShellHelper.reportSuccess("Europe/London");

        {
            LocationTimeZoneManagerServiceStateProto serviceState = dumpServiceState();
            assertControllerStateHistory(serviceState,
                    ControllerStateEnum.CONTROLLER_STATE_CERTAIN);
            assertLastSuggestion(serviceState, "Europe/London");
            assertProviderStates(serviceState.getPrimaryProviderStatesList(),
                    TimeZoneProviderStateEnum.TIME_ZONE_PROVIDER_STATE_CERTAIN);
            mPrimaryFakeTimeZoneProviderShellHelper.assertCurrentState(PROVIDER_STATE_CERTAIN);

            assertProviderStates(serviceState.getSecondaryProviderStatesList());
        }
    }

    /**
     * Demonstrates that duplicate equivalent reports made by location time zone providers within
     * a threshold time are ignored. It focuses on a single LTZP setup (primary only); the behavior
     * for the secondary is assumed to be identical.
     */
    @Test
    public void test_dupeSuggestionsMade_rateLimited() throws Exception {
        // Set the rate setting sufficiently high that rate limiting will definitely take place.
        mDeviceConfigShellHelper.put(NAMESPACE_SYSTEM_TIME,
                KEY_LTZP_EVENT_FILTERING_AGE_THRESHOLD_MILLIS,
                Long.toString(Duration.ofMinutes(10).toMillis()));

        String testPrimaryLocationTimeZoneProviderPackageName = FAKE_TZPS_APP_PACKAGE;
        String testSecondaryLocationTimeZoneProviderPackageName = null;
        mLocationTimeZoneManagerShellHelper.startWithTestProviders(
                testPrimaryLocationTimeZoneProviderPackageName,
                testSecondaryLocationTimeZoneProviderPackageName,
                true /* recordProviderStates */);
        // Turn on auto detection, which should activate the location time zone algorithm.
        mTimeZoneDetectorShellHelper.setAutoDetectionEnabled(true);

        mPrimaryFakeTimeZoneProviderShellHelper.assertCreated();
        mSecondaryFakeTimeZoneProviderShellHelper.assertNotCreated();

        mLocationTimeZoneManagerShellHelper.clearRecordedProviderStates();

        // Report a new time zone.
        mPrimaryFakeTimeZoneProviderShellHelper.reportSuccess("Europe/London");
        assertPrimaryReportedCertain();
        mLocationTimeZoneManagerShellHelper.clearRecordedProviderStates();

        // Duplicate time zone suggestion.
        mPrimaryFakeTimeZoneProviderShellHelper.reportSuccess("Europe/London");
        assertPrimaryMadeNoReport();
        mLocationTimeZoneManagerShellHelper.clearRecordedProviderStates();

        // Report a new time zone.
        mPrimaryFakeTimeZoneProviderShellHelper.reportSuccess("Europe/Paris");
        assertPrimaryReportedCertain();
        mLocationTimeZoneManagerShellHelper.clearRecordedProviderStates();

        // Duplicate time zone suggestion.
        mPrimaryFakeTimeZoneProviderShellHelper.reportSuccess("Europe/Paris");
        assertPrimaryMadeNoReport();
        mLocationTimeZoneManagerShellHelper.clearRecordedProviderStates();

        // Report uncertain.
        mPrimaryFakeTimeZoneProviderShellHelper.reportUncertain();
        assertPrimaryReportedUncertain();
        mLocationTimeZoneManagerShellHelper.clearRecordedProviderStates();

        // Duplicate uncertain report.
        mPrimaryFakeTimeZoneProviderShellHelper.reportUncertain();
        assertPrimaryMadeNoReport();
        mLocationTimeZoneManagerShellHelper.clearRecordedProviderStates();

        // Report a new time zone.
        mPrimaryFakeTimeZoneProviderShellHelper.reportSuccess("Europe/Paris");
        assertPrimaryReportedCertain();
        mLocationTimeZoneManagerShellHelper.clearRecordedProviderStates();
    }

    /**
     * Demonstrates that duplicate equivalent reports made by location time zone providers above
     * a threshold time are not filtered. It focuses on a single LTZP setup (primary only); the
     * behavior for the secondary is assumed to be identical.
     */
    @Test
    public void test_dupeSuggestionsMade_notRateLimited() throws Exception {
        // Set the rate sufficiently low that rate limiting will not take place.
        mDeviceConfigShellHelper.put(NAMESPACE_SYSTEM_TIME,
                KEY_LTZP_EVENT_FILTERING_AGE_THRESHOLD_MILLIS,
                "0");

        String testPrimaryLocationTimeZoneProviderPackageName = FAKE_TZPS_APP_PACKAGE;
        String testSecondaryLocationTimeZoneProviderPackageName = null;
        mLocationTimeZoneManagerShellHelper.startWithTestProviders(
                testPrimaryLocationTimeZoneProviderPackageName,
                testSecondaryLocationTimeZoneProviderPackageName,
                true /* recordProviderStates */);
        // Turn on auto detection, which should activate the location time zone algorithm.
        mTimeZoneDetectorShellHelper.setAutoDetectionEnabled(true);
        mPrimaryFakeTimeZoneProviderShellHelper.assertCreated();
        mSecondaryFakeTimeZoneProviderShellHelper.assertNotCreated();

        mLocationTimeZoneManagerShellHelper.clearRecordedProviderStates();

        // Report a new time zone.
        mPrimaryFakeTimeZoneProviderShellHelper.reportSuccess("Europe/London");
        assertPrimaryReportedCertain();
        mLocationTimeZoneManagerShellHelper.clearRecordedProviderStates();

        // Duplicate time zone suggestion.
        mPrimaryFakeTimeZoneProviderShellHelper.reportSuccess("Europe/London");
        assertPrimaryReportedCertain();
        mLocationTimeZoneManagerShellHelper.clearRecordedProviderStates();

        // Report uncertain.
        mPrimaryFakeTimeZoneProviderShellHelper.reportUncertain();
        assertPrimaryReportedUncertain();
        mLocationTimeZoneManagerShellHelper.clearRecordedProviderStates();

        // Duplicate uncertain report.
        mPrimaryFakeTimeZoneProviderShellHelper.reportUncertain();
        assertPrimaryReportedUncertain();
        mLocationTimeZoneManagerShellHelper.clearRecordedProviderStates();
    }

    private void assertPrimaryReportedCertain() throws Exception {
        LocationTimeZoneManagerServiceStateProto serviceState = dumpServiceState();
        assertProviderStates(serviceState.getPrimaryProviderStatesList(),
                TimeZoneProviderStateEnum.TIME_ZONE_PROVIDER_STATE_CERTAIN);
    }

    private void assertPrimaryMadeNoReport() throws Exception {
        LocationTimeZoneManagerServiceStateProto serviceState = dumpServiceState();
        assertProviderStates(serviceState.getPrimaryProviderStatesList());
    }

    private void assertPrimaryReportedUncertain() throws Exception {
        LocationTimeZoneManagerServiceStateProto serviceState = dumpServiceState();
        assertProviderStates(serviceState.getPrimaryProviderStatesList(),
                TimeZoneProviderStateEnum.TIME_ZONE_PROVIDER_STATE_UNCERTAIN);
    }

    /** Tests what happens when there's only a secondary provider and it makes a suggestion. */
    @Test
    public void testOnlySecondary_suggestionMade() throws Exception {
        String testPrimaryLocationTimeZoneProviderPackageName = null;
        String testSecondaryLocationTimeZoneProviderPackageName = FAKE_TZPS_APP_PACKAGE;
        mLocationTimeZoneManagerShellHelper.startWithTestProviders(
                testPrimaryLocationTimeZoneProviderPackageName,
                testSecondaryLocationTimeZoneProviderPackageName,
                true /* recordProviderStates */);
        // Turn on auto detection, which should activate the location time zone algorithm.
        mTimeZoneDetectorShellHelper.setAutoDetectionEnabled(true);
        mPrimaryFakeTimeZoneProviderShellHelper.assertNotCreated();
        mSecondaryFakeTimeZoneProviderShellHelper.assertCreated();

        {
            LocationTimeZoneManagerServiceStateProto serviceState = dumpServiceState();
            assertControllerStateHistory(serviceState,
                    ControllerStateEnum.CONTROLLER_STATE_PROVIDERS_INITIALIZING,
                    ControllerStateEnum.CONTROLLER_STATE_STOPPED,
                    ControllerStateEnum.CONTROLLER_STATE_INITIALIZING);
            assertNoLastSuggestion(serviceState);
            assertProviderStates(serviceState.getPrimaryProviderStatesList(),
                    TimeZoneProviderStateEnum.TIME_ZONE_PROVIDER_STATE_DISABLED,
                    TimeZoneProviderStateEnum.TIME_ZONE_PROVIDER_STATE_INITIALIZING,
                    TimeZoneProviderStateEnum.TIME_ZONE_PROVIDER_STATE_PERM_FAILED);

            assertProviderStates(serviceState.getSecondaryProviderStatesList(),
                    TimeZoneProviderStateEnum.TIME_ZONE_PROVIDER_STATE_DISABLED,
                    TimeZoneProviderStateEnum.TIME_ZONE_PROVIDER_STATE_INITIALIZING);
        }
        mLocationTimeZoneManagerShellHelper.clearRecordedProviderStates();

        mSecondaryFakeTimeZoneProviderShellHelper.reportSuccess("Europe/London");

        {
            LocationTimeZoneManagerServiceStateProto serviceState = dumpServiceState();
            assertControllerStateHistory(serviceState,
                    ControllerStateEnum.CONTROLLER_STATE_CERTAIN);
            assertLastSuggestion(serviceState, "Europe/London");
            assertProviderStates(serviceState.getPrimaryProviderStatesList());

            assertProviderStates(serviceState.getSecondaryProviderStatesList(),
                    TimeZoneProviderStateEnum.TIME_ZONE_PROVIDER_STATE_CERTAIN);
            mSecondaryFakeTimeZoneProviderShellHelper.assertCurrentState(PROVIDER_STATE_CERTAIN);
        }
    }

    /**
     * Tests what happens when there's both a primary and a secondary provider, the primary starts
     * by being uncertain, the secondary makes a suggestion, then the primary makes a suggestion.
     */
    @Test
    public void testPrimaryAndSecondary() throws Exception {
        String testPrimaryLocationTimeZoneProviderPackageName = FAKE_TZPS_APP_PACKAGE;
        String testSecondaryLocationTimeZoneProviderPackageName = FAKE_TZPS_APP_PACKAGE;
        mLocationTimeZoneManagerShellHelper.startWithTestProviders(
                testPrimaryLocationTimeZoneProviderPackageName,
                testSecondaryLocationTimeZoneProviderPackageName,
                true /* recordProviderStates*/);
        // Turn on auto detection, which should activate the location time zone algorithm.
        mTimeZoneDetectorShellHelper.setAutoDetectionEnabled(true);
        mPrimaryFakeTimeZoneProviderShellHelper.assertCreated();
        mSecondaryFakeTimeZoneProviderShellHelper.assertCreated();

        {
            LocationTimeZoneManagerServiceStateProto serviceState = dumpServiceState();
            assertControllerStateHistory(serviceState,
                    ControllerStateEnum.CONTROLLER_STATE_PROVIDERS_INITIALIZING,
                    ControllerStateEnum.CONTROLLER_STATE_STOPPED,
                    ControllerStateEnum.CONTROLLER_STATE_INITIALIZING);
            assertNoLastSuggestion(serviceState);
            assertProviderStates(serviceState.getPrimaryProviderStatesList(),
                    TimeZoneProviderStateEnum.TIME_ZONE_PROVIDER_STATE_DISABLED,
                    TimeZoneProviderStateEnum.TIME_ZONE_PROVIDER_STATE_INITIALIZING);
            mPrimaryFakeTimeZoneProviderShellHelper.assertCurrentState(PROVIDER_STATE_INITIALIZING);

            assertProviderStates(serviceState.getSecondaryProviderStatesList(),
                    TimeZoneProviderStateEnum.TIME_ZONE_PROVIDER_STATE_DISABLED);
            mSecondaryFakeTimeZoneProviderShellHelper.assertCurrentState(PROVIDER_STATE_DISABLED);
        }
        mLocationTimeZoneManagerShellHelper.clearRecordedProviderStates();

        // Make the primary report being uncertain. This should cause the secondary to be started.
        mPrimaryFakeTimeZoneProviderShellHelper.reportUncertain();

        {
            LocationTimeZoneManagerServiceStateProto serviceState = dumpServiceState();
            assertControllerStateHistory(serviceState);
            assertNoLastSuggestion(serviceState);
            assertProviderStates(serviceState.getPrimaryProviderStatesList(),
                    TimeZoneProviderStateEnum.TIME_ZONE_PROVIDER_STATE_UNCERTAIN);
            mPrimaryFakeTimeZoneProviderShellHelper.assertCurrentState(PROVIDER_STATE_UNCERTAIN);

            assertProviderStates(serviceState.getSecondaryProviderStatesList(),
                    TimeZoneProviderStateEnum.TIME_ZONE_PROVIDER_STATE_INITIALIZING);
            mSecondaryFakeTimeZoneProviderShellHelper.assertCurrentState(
                    PROVIDER_STATE_INITIALIZING);
        }
        mLocationTimeZoneManagerShellHelper.clearRecordedProviderStates();

        // Make the secondary report being certain.
        mSecondaryFakeTimeZoneProviderShellHelper.reportSuccess("Europe/London");

        {
            LocationTimeZoneManagerServiceStateProto serviceState = dumpServiceState();
            assertControllerStateHistory(serviceState,
                    ControllerStateEnum.CONTROLLER_STATE_CERTAIN);
            assertLastSuggestion(serviceState, "Europe/London");
            assertProviderStates(serviceState.getPrimaryProviderStatesList());
            mPrimaryFakeTimeZoneProviderShellHelper.assertCurrentState(PROVIDER_STATE_UNCERTAIN);

            assertProviderStates(serviceState.getSecondaryProviderStatesList(),
                    TimeZoneProviderStateEnum.TIME_ZONE_PROVIDER_STATE_CERTAIN);
            mSecondaryFakeTimeZoneProviderShellHelper.assertCurrentState(PROVIDER_STATE_CERTAIN);
        }
        mLocationTimeZoneManagerShellHelper.clearRecordedProviderStates();

        // Make the primary report being certain.
        mPrimaryFakeTimeZoneProviderShellHelper.reportSuccess("Europe/Paris");

        {
            LocationTimeZoneManagerServiceStateProto serviceState = dumpServiceState();
            assertControllerStateHistory(serviceState);
            assertLastSuggestion(serviceState, "Europe/Paris");
            assertProviderStates(serviceState.getPrimaryProviderStatesList(),
                    TimeZoneProviderStateEnum.TIME_ZONE_PROVIDER_STATE_CERTAIN);
            mPrimaryFakeTimeZoneProviderShellHelper.assertCurrentState(PROVIDER_STATE_CERTAIN);

            assertProviderStates(serviceState.getSecondaryProviderStatesList(),
                    TimeZoneProviderStateEnum.TIME_ZONE_PROVIDER_STATE_DISABLED);
            mSecondaryFakeTimeZoneProviderShellHelper.assertCurrentState(PROVIDER_STATE_DISABLED);
        }
    }

    private static void assertControllerStateHistory(
            LocationTimeZoneManagerServiceStateProto serviceState,
            ControllerStateEnum... expectedStates) {
        List<ControllerStateEnum> expectedStatesList = Arrays.asList(expectedStates);
        List<ControllerStateEnum> actualStates = serviceState.getControllerStatesList();
        assertEquals(expectedStatesList, actualStates);
    }

    private static void assertNoLastSuggestion(
            LocationTimeZoneManagerServiceStateProto serviceState) {
        if (serviceState.hasLastSuggestion()) {
            fail("Expected no last suggestion, but found:" + serviceState.getLastSuggestion());
        }
    }

    private static void assertLastSuggestion(LocationTimeZoneManagerServiceStateProto serviceState,
            String... expectedTimeZones) {
        assertFalse(expectedTimeZones == null || expectedTimeZones.length == 0);
        assertTrue(serviceState.hasLastSuggestion());
        List<String> expectedTimeZonesList = Arrays.asList(expectedTimeZones);
        List<String> actualTimeZonesList = serviceState.getLastSuggestion().getZoneIdsList();
        assertEquals(expectedTimeZonesList, actualTimeZonesList);
    }

    private static void assertProviderStates(List<TimeZoneProviderStateProto> actualStates,
            TimeZoneProviderStateEnum... expectedStates) {
        List<TimeZoneProviderStateEnum> expectedStatesList = Arrays.asList(expectedStates);
        assertEquals("Expected states: " + expectedStatesList + ", but was " + actualStates,
                expectedStatesList.size(), actualStates.size());
        for (int i = 0; i < expectedStatesList.size(); i++) {
            assertEquals("Expected states: " + expectedStatesList + ", but was " + actualStates,
                    expectedStates[i], actualStates.get(i).getState());
        }
    }

    private LocationTimeZoneManagerServiceStateProto dumpServiceState() throws Exception {
        byte[] protoBytes = mLocationTimeZoneManagerShellHelper.dumpState();
        Parser<LocationTimeZoneManagerServiceStateProto> parser =
                LocationTimeZoneManagerServiceStateProto.parser();
        return parser.parseFrom(protoBytes);
    }
}
