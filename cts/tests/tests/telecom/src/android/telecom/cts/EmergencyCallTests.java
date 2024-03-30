/*
 * Copyright (C) 2019 The Android Open Source Project
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

package android.telecom.cts;

import android.net.Uri;
import android.os.Bundle;
import android.provider.CallLog;
import android.telecom.Call;
import android.telecom.Connection;
import android.telecom.DisconnectCause;
import android.telecom.PhoneAccount;

import androidx.test.platform.app.InstrumentationRegistry;

import java.util.ArrayList;

public class EmergencyCallTests extends BaseTelecomTestWithMockServices {

    // mirrors constant in PhoneAccountRegistrar called MAX_PHONE_ACCOUNT_REGISTRATIONS
    public static final int MAX_PHONE_ACCOUNT_REGISTRATIONS = 10;

    @Override
    public void setUp() throws Exception {
        // Sets up this package as default dialer in super.
        super.setUp();
        NewOutgoingCallBroadcastReceiver.reset();
        if (!mShouldTestTelecom) return;
        setupConnectionService(null, FLAG_REGISTER | FLAG_ENABLE);
        setupForEmergencyCalling(TEST_EMERGENCY_NUMBER);
    }

    /**
     * Tests a scenario where an emergency call could fail due to the presence of invalid
     * {@link PhoneAccount} data.
     * The seed and quantity for {@link TestUtils#generateRandomPhoneAccounts(long, int, String,
     * String)} is chosen to represent a set of phone accounts which is known in AOSP to cause a
     * failure placing an emergency call.  {@code 52L} was chosen as a random seed and {@code 50}
     * was chosen as the set size for {@link PhoneAccount}s as these were observed in repeated test
     * invocations to induce the failure method.
     */
    public void testEmergencyCallFailureDueToInvalidPhoneAccounts() throws Exception {
        if (!mShouldTestTelecom) return;

        // needed in order to call mTelecomManager.getPhoneAccountsForPackage()
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .adoptShellPermissionIdentity("android.permission.READ_PRIVILEGED_PHONE_STATE");

        // determine the number of phone accounts already registered to this package.
        int phoneAccountsRegisteredAlready = mTelecomManager.getPhoneAccountsForPackage().size();

        // now, determine the number of accounts remaining
        int numberOfAccountsThatCanBeRegistered =
                MAX_PHONE_ACCOUNT_REGISTRATIONS - phoneAccountsRegisteredAlready;

        // create the remaining phone accounts allowed
        ArrayList<PhoneAccount> accounts = TestUtils.generateRandomPhoneAccounts(52L,
                numberOfAccountsThatCanBeRegistered,
                TestUtils.PACKAGE, TestUtils.COMPONENT);

        try {
            // register the phone accounts
            accounts.stream().forEach(a -> mTelecomManager.registerPhoneAccount(a));
            // assert all were registered successfully
            assertTrue(mTelecomManager.getPhoneAccountsForPackage().size()
                    >= MAX_PHONE_ACCOUNT_REGISTRATIONS);

            // The existing start emergency call test is impacted if there is a failure due to
            // excess phone accounts being present.
            testStartEmergencyCall();
        } finally {
            accounts.stream().forEach(d -> mTelecomManager.unregisterPhoneAccount(
                    d.getAccountHandle()));
            // cleanup permission that was added
            InstrumentationRegistry.getInstrumentation()
                    .getUiAutomation().dropShellPermissionIdentity();
        }
    }

    /**
     * Place an outgoing emergency call and ensure it is started successfully.
     */
    public void testStartEmergencyCall() throws Exception {
        if (!mShouldTestTelecom) return;
        Connection conn = placeAndVerifyEmergencyCall(true /*supportsHold*/);
        Call eCall = getInCallService().getLastCall();
        assertCallState(eCall, Call.STATE_DIALING);

        assertIsInCall(true);
        assertIsInManagedCall(true);
        conn.setActive();
        conn.setDisconnected(new DisconnectCause(DisconnectCause.LOCAL));
        conn.destroy();
        assertConnectionState(conn, Connection.STATE_DISCONNECTED);
        assertIsInCall(false);
    }

    /**
     * Place an outgoing emergency call and ensure any incoming call is rejected automatically and
     * logged in call log as a new missed call.
     *
     * Note: PSAPs have requirements that active emergency calls can not be put on hold, so if for
     * some reason an incoming emergency callback happens while on another emergency call, that call
     * will automatically be rejected as well.
     */
    public void testOngoingEmergencyCallAndReceiveIncomingCall() throws Exception {
        if (!mShouldTestTelecom) return;

        Connection eConnection = placeAndVerifyEmergencyCall(true /*supportsHold*/);
        assertIsInCall(true);
        assertIsInManagedCall(true);
        Call eCall = getInCallService().getLastCall();
        assertCallState(eCall, Call.STATE_DIALING);
        eConnection.setActive();
        assertCallState(eCall, Call.STATE_ACTIVE);

        Uri normalCallNumber = createRandomTestNumber();
        addAndVerifyNewFailedIncomingCall(normalCallNumber, null);
        assertCallState(eCall, Call.STATE_ACTIVE);

        // Notify as missed instead of rejected, since the user did not explicitly reject.
        verifyCallLogging(normalCallNumber, CallLog.Calls.MISSED_TYPE);
    }

    /**
     * Receive an incoming ringing call and place an emergency call. The ringing call should be
     * rejected and logged as a new missed call.
     */
    public void testIncomingRingingCallAndPlaceEmergencyCall() throws Exception {
        if (!mShouldTestTelecom) return;

        Uri normalCallNumber = createRandomTestNumber();
        addAndVerifyNewIncomingCall(normalCallNumber, null);
        MockConnection incomingConnection = verifyConnectionForIncomingCall();
        // Ensure destroy happens after emergency call is placed to prevent unbind -> rebind.
        incomingConnection.disableAutoDestroy();
        Call incomingCall = getInCallService().getLastCall();
        assertCallState(incomingCall, Call.STATE_RINGING);

        // Do not support holding incoming call for emergency call.
        Connection eConnection = placeAndVerifyEmergencyCall(false /*supportsHold*/);
        Call eCall = getInCallService().getLastCall();
        assertCallState(eCall, Call.STATE_DIALING);

        incomingConnection.destroy();
        assertConnectionState(incomingConnection, Connection.STATE_DISCONNECTED);
        assertCallState(incomingCall, Call.STATE_DISCONNECTED);

        eConnection.setActive();
        assertCallState(eCall, Call.STATE_ACTIVE);

        // Notify as missed instead of rejected, since the user did not explicitly reject.
        verifyCallLogging(normalCallNumber, CallLog.Calls.MISSED_TYPE);
    }

    /**
     * While on an outgoing call, receive an incoming ringing call and then place an emergency call.
     * The other two calls should stay active while the ringing call should be rejected and logged
     * as a new missed call.
     */
    public void testActiveCallAndIncomingRingingCallAndPlaceEmergencyCall() throws Exception {
        if (!mShouldTestTelecom) return;

        Uri normalOutgoingCallNumber = createRandomTestNumber();
        Bundle extras = new Bundle();
        extras.putParcelable(TestUtils.EXTRA_PHONE_NUMBER, normalOutgoingCallNumber);
        placeAndVerifyCall(extras);
        Connection outgoingConnection = verifyConnectionForOutgoingCall();
        Call outgoingCall = getInCallService().getLastCall();
        outgoingConnection.setActive();
        assertCallState(outgoingCall, Call.STATE_ACTIVE);

        Uri normalIncomingCallNumber = createRandomTestNumber();
        addAndVerifyNewIncomingCall(normalIncomingCallNumber, null);
        MockConnection incomingConnection = verifyConnectionForIncomingCall();
        // Ensure destroy happens after emergency call is placed to prevent unbind -> rebind.
        incomingConnection.disableAutoDestroy();
        Call incomingCall = getInCallService().getLastCall();
        assertCallState(incomingCall, Call.STATE_RINGING);

        // Do not support holding incoming call for emergency call.
        Connection eConnection = placeAndVerifyEmergencyCall(false /*supportsHold*/);
        Call eCall = getInCallService().getLastCall();
        assertCallState(eCall, Call.STATE_DIALING);

        incomingConnection.destroy();
        assertConnectionState(incomingConnection, Connection.STATE_DISCONNECTED);
        assertCallState(incomingCall, Call.STATE_DISCONNECTED);

        eConnection.setActive();
        assertCallState(eCall, Call.STATE_ACTIVE);

        // Notify as missed instead of rejected, since the user did not explicitly reject.
        verifyCallLogging(normalIncomingCallNumber, CallLog.Calls.MISSED_TYPE);
    }
}
