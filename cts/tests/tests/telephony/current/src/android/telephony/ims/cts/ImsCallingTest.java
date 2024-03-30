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

package android.telephony.ims.cts;

import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertTrue;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import android.app.Instrumentation;
import android.app.UiAutomation;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.telecom.Call;
import android.telecom.PhoneAccount;
import android.telecom.TelecomManager;
import android.telephony.CarrierConfigManager;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telephony.cts.InCallServiceStateValidator;
import android.telephony.cts.InCallServiceStateValidator.InCallServiceCallbacks;
import android.telephony.cts.TelephonyUtils;
import android.telephony.ims.feature.ImsFeature;
import android.telephony.ims.feature.MmTelFeature;
import android.telephony.ims.stub.ImsFeatureConfiguration;
import android.telephony.ims.stub.ImsRegistrationImplBase;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.ShellIdentityUtils;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * CTS tests for ImsCall .
 */
@RunWith(AndroidJUnit4.class)
public class ImsCallingTest {

    private static ImsServiceConnector sServiceConnector;

    private static final String LOG_TAG = "CtsImsCallingTest";
    private static final String PACKAGE = "android.telephony.ims.cts";
    private static final String PACKAGE_CTS_DIALER = "android.telephony.cts";
    private static final String COMMAND_SET_DEFAULT_DIALER = "telecom set-default-dialer ";
    private static final String COMMAND_GET_DEFAULT_DIALER = "telecom get-default-dialer";

    public static final int WAIT_FOR_SERVICE_TO_UNBOUND = 40000;
    public static final int WAIT_FOR_CONDITION = 3000;
    public static final int WAIT_FOR_CALL_STATE = 10000;
    public static final int WAIT_FOR_CALL_DISCONNECT = 1000;
    public static final int WAIT_FOR_CALL_CONNECT = 5000;
    public static final int WAIT_FOR_CALL_STATE_HOLD = 1000;
    public static final int WAIT_FOR_CALL_STATE_RESUME = 1000;
    public static final int WAIT_FOR_CALL_STATE_ACTIVE = 15000;
    public static final int LATCH_WAIT = 0;
    public static final int LATCH_INCALL_SERVICE_BOUND = 1;
    public static final int LATCH_INCALL_SERVICE_UNBOUND = 2;
    public static final int LATCH_IS_ON_CALL_ADDED = 3;
    public static final int LATCH_IS_ON_CALL_REMOVED = 4;
    public static final int LATCH_IS_CALL_DIALING = 5;
    public static final int LATCH_IS_CALL_ACTIVE = 6;
    public static final int LATCH_IS_CALL_DISCONNECTING = 7;
    public static final int LATCH_IS_CALL_DISCONNECTED = 8;
    public static final int LATCH_IS_CALL_RINGING = 9;
    public static final int LATCH_IS_CALL_HOLDING = 10;
    public static final int LATCH_MAX = 11;

    private static boolean sIsBound = false;
    private static int sCounter = 5553639;
    private static int sTestSlot = 0;
    private static int sTestSub = SubscriptionManager.INVALID_SUBSCRIPTION_ID;
    private static long sPreviousOptInStatus = 0;
    private static long sPreviousEn4GMode = 0;
    private static String sPreviousDefaultDialer;

    private static CarrierConfigReceiver sReceiver;
    private static SubscriptionManager sSubcriptionManager;

    private int mParticipantCount = 0;
    private final Object mLock = new Object();
    private InCallServiceCallbacks mServiceCallBack;
    private Context mContext;
    private ConcurrentHashMap<String, Call> mCalls = new ConcurrentHashMap<String, Call>();
    private String mCurrentCallId = null;
    private Call mCall1 = null;
    private Call mCall2 = null;
    private TestImsCallSessionImpl mCallSession1 = null;
    private TestImsCallSessionImpl mCallSession2 = null;

    private static final CountDownLatch[] sLatches = new CountDownLatch[LATCH_MAX];
    static {
        for (int i = 0; i < LATCH_MAX; i++) {
            sLatches[i] = new CountDownLatch(1);
        }
    }

    public boolean callingTestLatchCountdown(int latchIndex, int waitMs) {
        boolean complete = false;
        try {
            CountDownLatch latch;
            synchronized (mLock) {
                latch = sLatches[latchIndex];
            }
            complete = latch.await(waitMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
             //complete == false
        }
        synchronized (mLock) {
            sLatches[latchIndex] = new CountDownLatch(1);
        }
        return complete;
    }

    public void countDownLatch(int latchIndex) {
        synchronized (mLock) {
            sLatches[latchIndex].countDown();
        }
    }

    private abstract static class BaseReceiver extends BroadcastReceiver {
        protected CountDownLatch mLatch = new CountDownLatch(1);

        void clearQueue() {
            mLatch = new CountDownLatch(1);
        }

        void waitForChanged() throws Exception {
            mLatch.await(5000, TimeUnit.MILLISECONDS);
        }
    }

    private static class CarrierConfigReceiver extends BaseReceiver {
        private final int mSubId;

        CarrierConfigReceiver(int subId) {
            mSubId = subId;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            if (CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED.equals(intent.getAction())) {
                int subId = intent.getIntExtra(CarrierConfigManager.EXTRA_SUBSCRIPTION_INDEX, -1);
                if (mSubId == subId) {
                    mLatch.countDown();
                }
            }
        }
    }

    public interface Condition {
        Object expected();
        Object actual();
    }

    void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (Exception e) {
            Log.d(LOG_TAG, "InterruptedException");
        }
    }

    void waitUntilConditionIsTrueOrTimeout(Condition condition, long timeout,
            String description) {
        final long start = System.currentTimeMillis();
        while (!Objects.equals(condition.expected(), condition.actual())
                && System.currentTimeMillis() - start < timeout) {
            sleep(50);
        }
        assertEquals(description, condition.expected(), condition.actual());
    }

    @BeforeClass
    public static void beforeAllTests() throws Exception {
        if (!ImsUtils.shouldTestImsCall()) {
            return;
        }

        TelephonyManager tm = (TelephonyManager) getContext()
                .getSystemService(Context.TELEPHONY_SERVICE);
        sTestSub = ImsUtils.getPreferredActiveSubId();
        sTestSlot = SubscriptionManager.getSlotIndex(sTestSub);
        if (tm.getSimState(sTestSlot) != TelephonyManager.SIM_STATE_READY) {
            return;
        }

        sServiceConnector = new ImsServiceConnector(InstrumentationRegistry.getInstrumentation());
        // Remove all live ImsServices until after these tests are done
        sServiceConnector.clearAllActiveImsServices(sTestSlot);

        sReceiver = new CarrierConfigReceiver(sTestSub);
        IntentFilter filter = new IntentFilter(CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED);
        // ACTION_CARRIER_CONFIG_CHANGED is sticky, so we will get a callback right away.
        InstrumentationRegistry.getInstrumentation().getContext()
                .registerReceiver(sReceiver, filter);

        UiAutomation ui = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        try {
            ui.adoptShellPermissionIdentity();
            // Get the default dialer and save it to restore after test ends.
            sPreviousDefaultDialer = getDefaultDialer(InstrumentationRegistry.getInstrumentation());
            // Set dialer as "android.telephony.cts"
            setDefaultDialer(InstrumentationRegistry.getInstrumentation(), PACKAGE_CTS_DIALER);

            sSubcriptionManager = InstrumentationRegistry.getInstrumentation()
                    .getContext().getSystemService(SubscriptionManager.class);
            // Get the default Subscription values and save it to restore after test ends.
            sPreviousOptInStatus = sSubcriptionManager.getLongSubscriptionProperty(sTestSub,
                        SubscriptionManager.VOIMS_OPT_IN_STATUS, 0, getContext());
            sPreviousEn4GMode = sSubcriptionManager.getLongSubscriptionProperty(sTestSub,
                        SubscriptionManager.ENHANCED_4G_MODE_ENABLED, 0, getContext());
            // Set the new Sunbscription values
            sSubcriptionManager.setSubscriptionProperty(sTestSub,
                    SubscriptionManager.VOIMS_OPT_IN_STATUS, String.valueOf(1));
            sSubcriptionManager.setSubscriptionProperty(sTestSub,
                    SubscriptionManager.ENHANCED_4G_MODE_ENABLED, String.valueOf(1));

            //Override the carrier configurartions
            CarrierConfigManager configurationManager = InstrumentationRegistry.getInstrumentation()
                    .getContext().getSystemService(CarrierConfigManager.class);
            PersistableBundle bundle = new PersistableBundle(1);
            bundle.putBoolean(CarrierConfigManager.KEY_CARRIER_VOLTE_AVAILABLE_BOOL, true);
            bundle.putBoolean(CarrierConfigManager.KEY_ENHANCED_4G_LTE_ON_BY_DEFAULT_BOOL, true);
            bundle.putBoolean(CarrierConfigManager.KEY_EDITABLE_ENHANCED_4G_LTE_BOOL, false);
            bundle.putBoolean(CarrierConfigManager.KEY_CARRIER_VOLTE_TTY_SUPPORTED_BOOL, true);
            bundle.putBoolean(CarrierConfigManager.KEY_CARRIER_IMS_GBA_REQUIRED_BOOL , false);

            sReceiver.clearQueue();
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(configurationManager,
                    (m) -> m.overrideConfig(sTestSub, bundle));
        } finally {
            ui.dropShellPermissionIdentity();
        }
        sReceiver.waitForChanged();
    }

    @AfterClass
    public static void afterAllTests() throws Exception {
        if (!ImsUtils.shouldTestImsCall()) {
            return;
        }

        UiAutomation ui = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        try {
            ui.adoptShellPermissionIdentity();
            // Set the default Sunbscription values.
            sSubcriptionManager.setSubscriptionProperty(sTestSub,
                    SubscriptionManager.VOIMS_OPT_IN_STATUS, String.valueOf(sPreviousOptInStatus));
            sSubcriptionManager.setSubscriptionProperty(sTestSub,
                    SubscriptionManager.ENHANCED_4G_MODE_ENABLED, String.valueOf(
                    sPreviousEn4GMode));
            // Set default dialer
            setDefaultDialer(InstrumentationRegistry.getInstrumentation(), sPreviousDefaultDialer);

            // Restore all ImsService configurations that existed before the test.
            if (sServiceConnector != null && sIsBound) {
                sServiceConnector.disconnectServices();
                sIsBound = false;
            }
            sServiceConnector = null;
            overrideCarrierConfig(null);

            if (sReceiver != null) {
                InstrumentationRegistry.getInstrumentation().getContext()
                        .unregisterReceiver(sReceiver);
                sReceiver = null;
            }
        } finally {
            ui.dropShellPermissionIdentity();
        }
    }

    @Before
    public void beforeTest() throws Exception {
        if (!ImsUtils.shouldTestImsCall()) {
            return;
        }
        TelephonyManager tm = (TelephonyManager) InstrumentationRegistry.getInstrumentation()
                .getContext().getSystemService(Context.TELEPHONY_SERVICE);
        if (tm.getSimState(sTestSlot) != TelephonyManager.SIM_STATE_READY) {
            fail("This test requires that there is a SIM in the device!");
        }
        // Correctness check: ensure that the subscription hasn't changed between tests.
        int[] subs = SubscriptionManager.getSubId(sTestSlot);

        if (subs == null) {
            fail("This test requires there is an active subscription in slot " + sTestSlot);
        }
        boolean isFound = false;
        for (int sub : subs) {
            isFound |= (sTestSub == sub);
        }
        if (!isFound) {
            fail("Invalid state found: the test subscription in slot " + sTestSlot + " changed "
                    + "during this test.");
        }
    }

    public void bindImsService() throws Exception  {
        // Connect to the ImsService with the MmTel feature.
        assertTrue(sServiceConnector.connectCarrierImsService(new ImsFeatureConfiguration.Builder()
                .addFeature(sTestSlot, ImsFeature.FEATURE_MMTEL)
                .build()));
        sIsBound = true;
        // The MmTelFeature is created when the ImsService is bound. If it wasn't created, then the
        // Framework did not call it.
        sServiceConnector.getCarrierService().waitForLatchCountdown(
                TestImsService.LATCH_CREATE_MMTEL);
        assertNotNull("ImsService created, but ImsService#createMmTelFeature was not called!",
                sServiceConnector.getCarrierService().getMmTelFeature());

        sServiceConnector.getCarrierService().waitForLatchCountdown(
                TestImsService.LATCH_MMTEL_CAP_SET);

        MmTelFeature.MmTelCapabilities capabilities = new MmTelFeature.MmTelCapabilities(
                MmTelFeature.MmTelCapabilities.CAPABILITY_TYPE_VOICE);
        // Set Registered and VoLTE capable
        sServiceConnector.getCarrierService().getImsService().getRegistrationForSubscription(
                sTestSlot, sTestSub).onRegistered(ImsRegistrationImplBase.REGISTRATION_TECH_LTE);
        sServiceConnector.getCarrierService().getMmTelFeature().setCapabilities(capabilities);
        sServiceConnector.getCarrierService().getMmTelFeature()
                .notifyCapabilitiesStatusChanged(capabilities);

        // Wait a second for the notifyCapabilitiesStatusChanged indication to be processed on the
        // main telephony thread - currently no better way of knowing that telephony has processed
        // this command. SmsManager#isImsSmsSupported() is @hide and must be updated to use new API.
        Thread.sleep(3000);
    }

    @After
    public void afterTest() throws Exception {
        if (!ImsUtils.shouldTestImsCall()) {
            return;
        }

        if (!mCalls.isEmpty() && (mCurrentCallId != null)) {
            Call call = mCalls.get(mCurrentCallId);
            call.disconnect();
        }

        //Set the untracked CountDownLatches which are reseted in ServiceCallBack
        for (int i = 0; i < LATCH_MAX; i++) {
            sLatches[i] = new CountDownLatch(1);
        }

        if (sServiceConnector != null && sIsBound) {
            TestImsService imsService = sServiceConnector.getCarrierService();
            sServiceConnector.disconnectCarrierImsService();
            sIsBound = false;
            imsService.waitForExecutorFinish();
        }
    }

    @Test
    public void testOutGoingCall() throws Exception {
        if (!ImsUtils.shouldTestImsCall()) {
            return;
        }

        bindImsService();
        mServiceCallBack = new ServiceCallBack();
        InCallServiceStateValidator.setCallbacks(mServiceCallBack);

        TelecomManager telecomManager = (TelecomManager) InstrumentationRegistry
                .getInstrumentation().getContext().getSystemService(Context.TELECOM_SERVICE);

        final Uri imsUri = Uri.fromParts(PhoneAccount.SCHEME_TEL, String.valueOf(++sCounter), null);
        Bundle extras = new Bundle();

        // Place outgoing call
        telecomManager.placeCall(imsUri, extras);
        assertTrue(callingTestLatchCountdown(LATCH_IS_ON_CALL_ADDED, WAIT_FOR_CALL_STATE));

        Call call = getCall(mCurrentCallId);
        assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_DIALING, WAIT_FOR_CALL_STATE));

        TestImsCallSessionImpl callSession = sServiceConnector.getCarrierService().getMmTelFeature()
                .getImsCallsession();

        isCallActive(call, callSession);

        callingTestLatchCountdown(LATCH_WAIT, WAIT_FOR_CALL_DISCONNECT);
        call.disconnect();

        assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_DISCONNECTING, WAIT_FOR_CALL_STATE));
        isCallDisconnected(call, callSession);
        assertTrue(callingTestLatchCountdown(LATCH_IS_ON_CALL_REMOVED, WAIT_FOR_CALL_STATE));
        waitForUnboundService();
    }

    @Test
    public void testOutGoingCallStartFailed() throws Exception {
        if (!ImsUtils.shouldTestImsCall()) {
            return;
        }

        bindImsService();
        mServiceCallBack = new ServiceCallBack();
        InCallServiceStateValidator.setCallbacks(mServiceCallBack);

        TelecomManager telecomManager = (TelecomManager) InstrumentationRegistry
                .getInstrumentation().getContext().getSystemService(Context.TELECOM_SERVICE);

        final Uri imsUri = Uri.fromParts(PhoneAccount.SCHEME_TEL, String.valueOf(++sCounter), null);
        Bundle extras = new Bundle();

        // Place outgoing call
        telecomManager.placeCall(imsUri, extras);
        assertTrue(callingTestLatchCountdown(LATCH_IS_ON_CALL_ADDED, WAIT_FOR_CALL_STATE));

        Call call = getCall(mCurrentCallId);

        waitUntilConditionIsTrueOrTimeout(
                new Condition() {
                    @Override
                    public Object expected() {
                        return true;
                    }

                    @Override
                    public Object actual() {
                        TestMmTelFeature mmtelfeatue = sServiceConnector.getCarrierService()
                                .getMmTelFeature();
                        return (mmtelfeatue.isCallSessionCreated()) ? true : false;
                    }
                }, WAIT_FOR_CONDITION, "CallSession Created");

        TestImsCallSessionImpl callSession = sServiceConnector.getCarrierService().getMmTelFeature()
                .getImsCallsession();
        assertNotNull("Unable to get callSession, its null", callSession);
        callSession.addTestType(TestImsCallSessionImpl.TEST_TYPE_MO_FAILED);

        assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_DIALING, WAIT_FOR_CALL_STATE));
        isCallDisconnected(call, callSession);
        assertTrue(callingTestLatchCountdown(LATCH_IS_ON_CALL_REMOVED, WAIT_FOR_CALL_STATE));
        waitForUnboundService();
    }

    @Test
    public void testIncomingCall() throws Exception {
        if (!ImsUtils.shouldTestImsCall()) {
            return;
        }
        bindImsService();
        mServiceCallBack = new ServiceCallBack();
        InCallServiceStateValidator.setCallbacks(mServiceCallBack);

        Bundle extras = new Bundle();
        sServiceConnector.getCarrierService().getMmTelFeature().onIncomingCallReceived(extras);
        assertTrue(callingTestLatchCountdown(LATCH_IS_ON_CALL_ADDED, WAIT_FOR_CALL_STATE));

        Call call = getCall(mCurrentCallId);
        if (call.getDetails().getState() == Call.STATE_RINGING) {
            callingTestLatchCountdown(LATCH_WAIT, 5000);
            call.answer(0);
        }

        TestImsCallSessionImpl callSession = sServiceConnector.getCarrierService().getMmTelFeature()
                .getImsCallsession();

        isCallActive(call, callSession);

        callingTestLatchCountdown(LATCH_WAIT, WAIT_FOR_CALL_DISCONNECT);
        callSession.terminateIncomingCall();

        isCallDisconnected(call, callSession);
        assertTrue(callingTestLatchCountdown(LATCH_IS_ON_CALL_REMOVED, WAIT_FOR_CALL_STATE));
        waitForUnboundService();
    }

    @Test
    public void testOutGoingCallForExecutor() throws Exception {
        if (!ImsUtils.shouldTestImsCall()) {
            return;
        }

        sServiceConnector.setExecutorTestType(true);
        bindImsService();

        mServiceCallBack = new ServiceCallBack();
        InCallServiceStateValidator.setCallbacks(mServiceCallBack);

        TelecomManager telecomManager = (TelecomManager) InstrumentationRegistry
                .getInstrumentation().getContext().getSystemService(Context.TELECOM_SERVICE);

        final Uri imsUri = Uri.fromParts(PhoneAccount.SCHEME_TEL, String.valueOf(++sCounter), null);
        Bundle extras = new Bundle();

        // Place outgoing call
        telecomManager.placeCall(imsUri, extras);
        assertTrue(callingTestLatchCountdown(LATCH_IS_ON_CALL_ADDED, WAIT_FOR_CALL_STATE));

        Call call = getCall(mCurrentCallId);
        assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_DIALING, WAIT_FOR_CALL_STATE));

        TestImsCallSessionImpl callSession = sServiceConnector.getCarrierService().getMmTelFeature()
                .getImsCallsession();

        isCallActive(call, callSession);

        callingTestLatchCountdown(LATCH_WAIT, WAIT_FOR_CALL_DISCONNECT);
        call.disconnect();

        assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_DISCONNECTING, WAIT_FOR_CALL_STATE));
        isCallDisconnected(call, callSession);
        assertTrue(callingTestLatchCountdown(LATCH_IS_ON_CALL_REMOVED, WAIT_FOR_CALL_STATE));
        waitForUnboundService();
    }

    @Test
    public void testOutGoingCallHoldResume() throws Exception {
        if (!ImsUtils.shouldTestImsCall()) {
            return;
        }

        bindImsService();
        mServiceCallBack = new ServiceCallBack();
        InCallServiceStateValidator.setCallbacks(mServiceCallBack);

        TelecomManager telecomManager = (TelecomManager) InstrumentationRegistry
                .getInstrumentation().getContext().getSystemService(Context.TELECOM_SERVICE);

        final Uri imsUri = Uri.fromParts(PhoneAccount.SCHEME_TEL, String.valueOf(++sCounter), null);
        Bundle extras = new Bundle();

        // Place outgoing call
        telecomManager.placeCall(imsUri, extras);
        assertTrue(callingTestLatchCountdown(LATCH_IS_ON_CALL_ADDED, WAIT_FOR_CALL_STATE));

        Call call = getCall(mCurrentCallId);
        assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_DIALING, WAIT_FOR_CALL_STATE));

        TestImsCallSessionImpl callSession = sServiceConnector.getCarrierService().getMmTelFeature()
                .getImsCallsession();

        isCallActive(call, callSession);
        callingTestLatchCountdown(LATCH_WAIT, WAIT_FOR_CALL_STATE_HOLD);
        // Put on hold
        call.hold();
        assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_HOLDING, WAIT_FOR_CALL_STATE));

        callingTestLatchCountdown(LATCH_WAIT, WAIT_FOR_CALL_STATE_RESUME);
        // Put on resume
        call.unhold();
        isCallActive(call, callSession);

        callingTestLatchCountdown(LATCH_WAIT, WAIT_FOR_CALL_DISCONNECT);
        call.disconnect();

        assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_DISCONNECTING, WAIT_FOR_CALL_STATE));
        isCallDisconnected(call, callSession);
        assertTrue(callingTestLatchCountdown(LATCH_IS_ON_CALL_REMOVED, WAIT_FOR_CALL_STATE));
        waitForUnboundService();
    }

    @Test
    public void testOutGoingCallHoldFailure() throws Exception {
        if (!ImsUtils.shouldTestImsCall()) {
            return;
        }

        bindImsService();
        mServiceCallBack = new ServiceCallBack();
        InCallServiceStateValidator.setCallbacks(mServiceCallBack);

        TelecomManager telecomManager = (TelecomManager) InstrumentationRegistry
                .getInstrumentation().getContext().getSystemService(Context.TELECOM_SERVICE);

        final Uri imsUri = Uri.fromParts(PhoneAccount.SCHEME_TEL, String.valueOf(++sCounter), null);
        Bundle extras = new Bundle();

        // Place outgoing call
        telecomManager.placeCall(imsUri, extras);
        assertTrue(callingTestLatchCountdown(LATCH_IS_ON_CALL_ADDED, WAIT_FOR_CALL_STATE));

        Call call = getCall(mCurrentCallId);
        assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_DIALING, WAIT_FOR_CALL_STATE));

        TestImsCallSessionImpl callSession = sServiceConnector.getCarrierService().getMmTelFeature()
                .getImsCallsession();

        isCallActive(call, callSession);
        callSession.addTestType(TestImsCallSessionImpl.TEST_TYPE_HOLD_FAILED);

        callingTestLatchCountdown(LATCH_WAIT, WAIT_FOR_CALL_STATE_HOLD);
        call.hold();
        assertTrue("call is not in Active State", (call.getDetails().getState()
                == call.STATE_ACTIVE));

        callingTestLatchCountdown(LATCH_WAIT, WAIT_FOR_CALL_DISCONNECT);
        call.disconnect();

        assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_DISCONNECTING, WAIT_FOR_CALL_STATE));
        isCallDisconnected(call, callSession);
        assertTrue(callingTestLatchCountdown(LATCH_IS_ON_CALL_REMOVED, WAIT_FOR_CALL_STATE));
        waitForUnboundService();
    }


    @Test
    public void testOutGoingCallResumeFailure() throws Exception {
        if (!ImsUtils.shouldTestImsCall()) {
            return;
        }

        bindImsService();
        mServiceCallBack = new ServiceCallBack();
        InCallServiceStateValidator.setCallbacks(mServiceCallBack);

        TelecomManager telecomManager = (TelecomManager) InstrumentationRegistry
                .getInstrumentation().getContext().getSystemService(Context.TELECOM_SERVICE);

        final Uri imsUri = Uri.fromParts(PhoneAccount.SCHEME_TEL, String.valueOf(++sCounter), null);
        Bundle extras = new Bundle();

        // Place outgoing call
        telecomManager.placeCall(imsUri, extras);
        assertTrue(callingTestLatchCountdown(LATCH_IS_ON_CALL_ADDED, WAIT_FOR_CALL_STATE));

        Call call = getCall(mCurrentCallId);
        assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_DIALING, WAIT_FOR_CALL_STATE));

        TestImsCallSessionImpl callSession = sServiceConnector.getCarrierService().getMmTelFeature()
                .getImsCallsession();

        isCallActive(call, callSession);

        callingTestLatchCountdown(LATCH_WAIT, WAIT_FOR_CALL_STATE_HOLD);
        // Put on hold
        call.hold();
        assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_HOLDING, WAIT_FOR_CALL_STATE));

        callSession.addTestType(TestImsCallSessionImpl.TEST_TYPE_RESUME_FAILED);
        callingTestLatchCountdown(LATCH_WAIT, WAIT_FOR_CALL_STATE_RESUME);
        call.unhold();
        callingTestLatchCountdown(LATCH_WAIT, WAIT_FOR_CALL_STATE_HOLD);
        assertTrue("Call is not in Hold State", (call.getDetails().getState()
                == call.STATE_HOLDING));


        callingTestLatchCountdown(LATCH_WAIT, WAIT_FOR_CALL_DISCONNECT);
        call.disconnect();

        assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_DISCONNECTING, WAIT_FOR_CALL_STATE));
        isCallDisconnected(call, callSession);
        assertTrue(callingTestLatchCountdown(LATCH_IS_ON_CALL_REMOVED, WAIT_FOR_CALL_STATE));
        waitForUnboundService();
    }

    @Test
    public void testOutGoingIncomingMultiCall() throws Exception {
        if (!ImsUtils.shouldTestImsCall()) {
            return;
        }

        bindImsService();
        mServiceCallBack = new ServiceCallBack();
        InCallServiceStateValidator.setCallbacks(mServiceCallBack);

        TelecomManager telecomManager = (TelecomManager) InstrumentationRegistry
                .getInstrumentation().getContext().getSystemService(Context.TELECOM_SERVICE);

        final Uri imsUri = Uri.fromParts(PhoneAccount.SCHEME_TEL, String.valueOf(++sCounter), null);
        Bundle extras = new Bundle();

        // Place outgoing call
        telecomManager.placeCall(imsUri, extras);
        assertTrue(callingTestLatchCountdown(LATCH_IS_ON_CALL_ADDED, WAIT_FOR_CALL_STATE));

        Call moCall = getCall(mCurrentCallId);
        assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_DIALING, WAIT_FOR_CALL_STATE));

        TestImsCallSessionImpl moCallSession = sServiceConnector.getCarrierService()
                .getMmTelFeature().getImsCallsession();
        isCallActive(moCall, moCallSession);
        assertTrue("Call is not in Active State", (moCall.getDetails().getState()
                == Call.STATE_ACTIVE));

        extras.putBoolean("android.telephony.ims.feature.extra.IS_USSD", false);
        extras.putBoolean("android.telephony.ims.feature.extra.IS_UNKNOWN_CALL", false);
        extras.putString("android:imsCallID",  String.valueOf(++sCounter));
        extras.putLong("android:phone_id", 123456);
        sServiceConnector.getCarrierService().getMmTelFeature().onIncomingCallReceived(extras);
        assertTrue(callingTestLatchCountdown(LATCH_IS_ON_CALL_ADDED, WAIT_FOR_CALL_STATE));

        Call mtCall = null;
        if (mCurrentCallId != null) {
            mtCall = getCall(mCurrentCallId);
            if (mtCall.getDetails().getState() == Call.STATE_RINGING) {
                callingTestLatchCountdown(LATCH_WAIT, WAIT_FOR_CALL_CONNECT);
                mtCall.answer(0);
            }
        }

        assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_HOLDING, WAIT_FOR_CALL_STATE));
        TestImsCallSessionImpl mtCallSession = sServiceConnector.getCarrierService()
                .getMmTelFeature().getImsCallsession();
        isCallActive(mtCall, mtCallSession);
        assertTrue("Call is not in Active State", (mtCall.getDetails().getState()
                == Call.STATE_ACTIVE));

        callingTestLatchCountdown(LATCH_WAIT, WAIT_FOR_CALL_DISCONNECT);
        mtCall.disconnect();
        assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_DISCONNECTING, WAIT_FOR_CALL_STATE));
        isCallDisconnected(mtCall, mtCallSession);
        assertTrue(callingTestLatchCountdown(LATCH_IS_ON_CALL_REMOVED, WAIT_FOR_CALL_STATE));

        isCallActive(moCall, moCallSession);
        assertTrue("Call is not in Active State", (moCall.getDetails().getState()
                == Call.STATE_ACTIVE));

        moCall.disconnect();
        assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_DISCONNECTING, WAIT_FOR_CALL_STATE));
        isCallDisconnected(moCall, moCallSession);
        assertTrue(callingTestLatchCountdown(LATCH_IS_ON_CALL_REMOVED, WAIT_FOR_CALL_STATE));

        waitForUnboundService();
    }

    @Test
    public void testOutGoingIncomingMultiCallAcceptTerminate() throws Exception {
        if (!ImsUtils.shouldTestImsCall()) {
            return;
        }

        bindImsService();
        mServiceCallBack = new ServiceCallBack();
        InCallServiceStateValidator.setCallbacks(mServiceCallBack);

        TelecomManager telecomManager = (TelecomManager) InstrumentationRegistry
                .getInstrumentation().getContext().getSystemService(Context.TELECOM_SERVICE);

        final Uri imsUri = Uri.fromParts(PhoneAccount.SCHEME_TEL, String.valueOf(++sCounter), null);
        Bundle extras = new Bundle();

        // Place outgoing call
        telecomManager.placeCall(imsUri, extras);
        assertTrue(callingTestLatchCountdown(LATCH_IS_ON_CALL_ADDED, WAIT_FOR_CALL_STATE));

        Call moCall = getCall(mCurrentCallId);
        assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_DIALING, WAIT_FOR_CALL_STATE));

        TestImsCallSessionImpl moCallSession = sServiceConnector.getCarrierService()
                .getMmTelFeature().getImsCallsession();
        isCallActive(moCall, moCallSession);
        assertTrue("Call is not in Active State", (moCall.getDetails().getState()
                == Call.STATE_ACTIVE));

        extras.putBoolean("android.telephony.ims.feature.extra.IS_USSD", false);
        extras.putBoolean("android.telephony.ims.feature.extra.IS_UNKNOWN_CALL", false);
        extras.putString("android:imsCallID",  String.valueOf(++sCounter));
        extras.putLong("android:phone_id", 123456);
        sServiceConnector.getCarrierService().getMmTelFeature().onIncomingCallReceived(extras);
        assertTrue(callingTestLatchCountdown(LATCH_IS_ON_CALL_ADDED, WAIT_FOR_CALL_STATE));
        TestImsCallSessionImpl mtCallSession = sServiceConnector.getCarrierService()
                .getMmTelFeature().getImsCallsession();
        // do not generate an auto hold response here, need to simulate a timing issue.
        moCallSession.addTestType(TestImsCallSessionImpl.TEST_TYPE_HOLD_NO_RESPONSE);

        Call mtCall = null;
        if (mCurrentCallId != null) {
            mtCall = getCall(mCurrentCallId);
            if (mtCall.getDetails().getState() == Call.STATE_RINGING) {
                callingTestLatchCountdown(LATCH_WAIT, WAIT_FOR_CALL_CONNECT);
                mtCall.answer(0);
            }
        }

        callingTestLatchCountdown(LATCH_WAIT, WAIT_FOR_CALL_CONNECT);
        // simulate user hanging up the MT call at the same time as accept.
        mtCallSession.terminateIncomingCall();
        isCallDisconnected(mtCall, mtCallSession);
        assertTrue(callingTestLatchCountdown(LATCH_IS_ON_CALL_REMOVED, WAIT_FOR_CALL_STATE));

        // then send hold response, which should be reversed, since MT call was disconnected.
        moCallSession.sendHoldResponse();

        // MO call should move back to active.
        isCallActive(moCall, moCallSession);
        assertTrue("Call is not in Active State", (moCall.getDetails().getState()
                == Call.STATE_ACTIVE));

        moCall.disconnect();
        assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_DISCONNECTING, WAIT_FOR_CALL_STATE));
        isCallDisconnected(moCall, moCallSession);
        assertTrue(callingTestLatchCountdown(LATCH_IS_ON_CALL_REMOVED, WAIT_FOR_CALL_STATE));

        waitForUnboundService();
    }

    @Test
    public void testOutGoingCallSwap() throws Exception {
        if (!ImsUtils.shouldTestImsCall()) {
            return;
        }

        bindImsService();
        mServiceCallBack = new ServiceCallBack();
        InCallServiceStateValidator.setCallbacks(mServiceCallBack);
        addOutgoingCalls();

        // Swap the call
        callingTestLatchCountdown(LATCH_WAIT, WAIT_FOR_CALL_STATE_RESUME);
        mCall1.unhold();
        assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_HOLDING, WAIT_FOR_CALL_STATE));
        assertTrue("Call is not in Hold State", (mCall2.getDetails().getState()
                == Call.STATE_HOLDING));
        isCallActive(mCall1, mCallSession1);

        // After successful call swap disconnect the call
        callingTestLatchCountdown(LATCH_WAIT, WAIT_FOR_CALL_DISCONNECT);
        mCall1.disconnect();
        assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_DISCONNECTING, WAIT_FOR_CALL_STATE));
        isCallDisconnected(mCall1, mCallSession1);
        assertTrue(callingTestLatchCountdown(LATCH_IS_ON_CALL_REMOVED, WAIT_FOR_CALL_STATE));

        //Wait till second call is in active state
        waitUntilConditionIsTrueOrTimeout(
                new Condition() {
                    @Override
                    public Object expected() {
                        return true;
                    }

                    @Override
                    public Object actual() {
                        return (mCall2.getDetails().getState() == Call.STATE_ACTIVE)
                                ? true : false;
                    }
                }, WAIT_FOR_CALL_STATE_ACTIVE, "Call in Active State");

        isCallActive(mCall2, mCallSession2);
        mCall2.disconnect();
        assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_DISCONNECTING, WAIT_FOR_CALL_STATE));
        isCallDisconnected(mCall2, mCallSession2);
        assertTrue(callingTestLatchCountdown(LATCH_IS_ON_CALL_REMOVED, WAIT_FOR_CALL_STATE));

        resetCallSessionObjects();
        waitForUnboundService();
    }

    @Test
    public void testOutGoingCallSwapFail() throws Exception {
        if (!ImsUtils.shouldTestImsCall()) {
            return;
        }

        bindImsService();
        mServiceCallBack = new ServiceCallBack();
        InCallServiceStateValidator.setCallbacks(mServiceCallBack);
        addOutgoingCalls();

        mCallSession1.addTestType(TestImsCallSessionImpl.TEST_TYPE_RESUME_FAILED);
        // Swap the call
        callingTestLatchCountdown(LATCH_WAIT, WAIT_FOR_CALL_STATE_RESUME);
        mCall1.unhold();
        assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_HOLDING, WAIT_FOR_CALL_STATE));
        assertTrue("Call is not in Hold State", (mCall1.getDetails().getState()
                == Call.STATE_HOLDING));

        // Wait till second call is in active state
        waitUntilConditionIsTrueOrTimeout(
                new Condition() {
                    @Override
                    public Object expected() {
                        return true;
                    }

                    @Override
                    public Object actual() {
                        return (mCall2.getDetails().getState() == Call.STATE_ACTIVE)
                                ? true : false;
                    }
                }, WAIT_FOR_CALL_STATE_ACTIVE, "Call in Active State");

        isCallActive(mCall2, mCallSession2);
        mCallSession1.removeTestType(TestImsCallSessionImpl.TEST_TYPE_RESUME_FAILED);
        mCall2.disconnect();
        assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_DISCONNECTING, WAIT_FOR_CALL_STATE));
        isCallDisconnected(mCall2, mCallSession2);
        assertTrue(callingTestLatchCountdown(LATCH_IS_ON_CALL_REMOVED, WAIT_FOR_CALL_STATE));

        // Wait till second call is in active state
        isCallActive(mCall1, mCallSession1);
        mCall1.disconnect();
        assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_DISCONNECTING, WAIT_FOR_CALL_STATE));
        isCallDisconnected(mCall1, mCallSession1);
        assertTrue(callingTestLatchCountdown(LATCH_IS_ON_CALL_REMOVED, WAIT_FOR_CALL_STATE));

        resetCallSessionObjects();
        waitForUnboundService();
    }

    @Test
    public void testConferenceCall() throws Exception {
        if (!ImsUtils.shouldTestImsCall()) {
            return;
        }
        Log.i(LOG_TAG, "testConference ");
        bindImsService();
        mServiceCallBack = new ServiceCallBack();
        InCallServiceStateValidator.setCallbacks(mServiceCallBack);
        addOutgoingCalls();
        addConferenceCall(mCall1, mCall2);

        assertTrue(callingTestLatchCountdown(LATCH_IS_ON_CALL_ADDED, WAIT_FOR_CALL_STATE));
        assertTrue("Conference call is not added", mServiceCallBack.getService()
                .getConferenceCallCount() > 0);

        Call conferenceCall = mServiceCallBack.getService().getLastConferenceCall();
        assertNotNull("Unable to add conference call, its null", conferenceCall);

        ConferenceHelper confHelper = sServiceConnector.getCarrierService().getMmTelFeature()
                .getConferenceHelper();

        TestImsCallSessionImpl confcallSession = confHelper.getConferenceSession();
        assertTrue("Conference call is not Active", confcallSession.isInCall());

        //Verify mCall1 and mCall2 disconnected after conference Merge success
        assertParticiapantDisconnected(mCall1);
        assertParticiapantDisconnected(mCall2);

        //Verify conference participant connections are connected.
        assertParticiapantAddedToConference(2);

        //Disconnect the conference call.
        callingTestLatchCountdown(LATCH_WAIT, WAIT_FOR_CALL_DISCONNECT);
        conferenceCall.disconnect();

        //Verify conference participant connections are disconnected.
        assertParticiapantAddedToConference(0);
        isCallDisconnected(conferenceCall, confcallSession);
        assertTrue(callingTestLatchCountdown(LATCH_IS_ON_CALL_REMOVED, WAIT_FOR_CALL_STATE));
        resetCallSessionObjects();
        waitForUnboundService();
    }

    @Test
    public void testConferenceCallFailure() throws Exception {
        if (!ImsUtils.shouldTestImsCall()) {
            return;
        }
        Log.i(LOG_TAG, "testConferenceCallFailure ");
        bindImsService();
        mServiceCallBack = new ServiceCallBack();
        InCallServiceStateValidator.setCallbacks(mServiceCallBack);
        addOutgoingCalls();
        mCallSession2.addTestType(TestImsCallSessionImpl.TEST_TYPE_CONFERENCE_FAILED);
        addConferenceCall(mCall1, mCall2);

        callingTestLatchCountdown(LATCH_WAIT, WAIT_FOR_CALL_CONNECT);
        //Verify foreground call is in Active state after merge failed.
        assertTrue("Call is not in Active State", (mCall2.getDetails().getState()
                == Call.STATE_ACTIVE));
        //Verify background call is in Hold state after merge failed.
        assertTrue("Call is not in Holding State", (mCall1.getDetails().getState()
                == Call.STATE_HOLDING));

        callingTestLatchCountdown(LATCH_WAIT, WAIT_FOR_CALL_DISCONNECT);
        mCall2.disconnect();
        assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_DISCONNECTING, WAIT_FOR_CALL_STATE));
        isCallDisconnected(mCall2, mCallSession2);
        assertTrue(callingTestLatchCountdown(LATCH_IS_ON_CALL_REMOVED, WAIT_FOR_CALL_STATE));

        //Wait till background call is in active state
        isCallActive(mCall1, mCallSession1);
        mCall1.disconnect();
        assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_DISCONNECTING, WAIT_FOR_CALL_STATE));
        isCallDisconnected(mCall1, mCallSession1);
        assertTrue(callingTestLatchCountdown(LATCH_IS_ON_CALL_REMOVED, WAIT_FOR_CALL_STATE));

        resetCallSessionObjects();
        waitForUnboundService();
    }

    void addConferenceCall(Call call1, Call call2) {
        InCallServiceStateValidator inCallService = mServiceCallBack.getService();
        int currentConfCallCount = 0;
        if (inCallService != null) {
            currentConfCallCount = inCallService.getConferenceCallCount();
        }

        // Verify that the calls have each other on their conferenceable list before proceeding
        List<Call> callConfList = new ArrayList<>();
        callConfList.add(call2);
        assertCallConferenceableList(call1, callConfList);

        callConfList.clear();
        callConfList.add(call1);
        assertCallConferenceableList(call2, callConfList);

        call2.conference(call1);
    }

    void assertCallConferenceableList(final Call call, final List<Call> conferenceableList) {
        waitUntilConditionIsTrueOrTimeout(
                new Condition() {
                    @Override
                    public Object expected() {
                        return conferenceableList;
                    }

                    @Override
                    public Object actual() {
                        return call.getConferenceableCalls();
                    }
                }, WAIT_FOR_CONDITION,
                        "Call: " + call + " does not have the correct conferenceable call list."
        );
    }

    private void assertParticiapantDisconnected(Call call) {
        waitUntilConditionIsTrueOrTimeout(
                new Condition() {
                    @Override
                    public Object expected() {
                        return true;
                    }

                    @Override
                    public Object actual() {
                        return ((call.getState() == Call.STATE_DISCONNECTED)) ? true : false;
                    }
                }, WAIT_FOR_CALL_DISCONNECT, "Call Disconnected");
    }

    private void assertParticiapantAddedToConference(int count) {
        waitUntilConditionIsTrueOrTimeout(
                new Condition() {
                    @Override
                    public Object expected() {
                        return true;
                    }

                    @Override
                    public Object actual() {
                        return (mParticipantCount == count) ? true : false;
                    }
                }, WAIT_FOR_CALL_CONNECT, "Call Added");
    }

    private void addOutgoingCalls() throws Exception {
        TelecomManager telecomManager = (TelecomManager) InstrumentationRegistry
                .getInstrumentation().getContext().getSystemService(Context.TELECOM_SERVICE);

        // Place first outgoing call
        final Uri imsUri1 = Uri.fromParts(PhoneAccount.SCHEME_TEL, String.valueOf(++sCounter),
                null);
        Bundle extras1 = new Bundle();

        telecomManager.placeCall(imsUri1, extras1);
        assertTrue(callingTestLatchCountdown(LATCH_IS_ON_CALL_ADDED, WAIT_FOR_CALL_STATE));

        mCall1 = getCall(mCurrentCallId);
        assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_DIALING, WAIT_FOR_CALL_STATE));
        mCallSession1 = sServiceConnector.getCarrierService().getMmTelFeature().getImsCallsession();
        isCallActive(mCall1, mCallSession1);
        assertTrue("Call is not in Active State", (mCall1.getDetails().getState()
                == Call.STATE_ACTIVE));

        // Place second outgoing call
        final Uri imsUri2 = Uri.fromParts(PhoneAccount.SCHEME_TEL, String.valueOf(++sCounter),
                null);
        Bundle extras2 = new Bundle();

        telecomManager.placeCall(imsUri2, extras2);
        assertTrue(callingTestLatchCountdown(LATCH_IS_ON_CALL_ADDED, WAIT_FOR_CALL_STATE));

        mCall2 = getCall(mCurrentCallId);
        assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_DIALING, WAIT_FOR_CALL_STATE));
        assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_HOLDING, WAIT_FOR_CALL_STATE));
        assertTrue("Call is not in Hold State", (mCall1.getDetails().getState()
                == Call.STATE_HOLDING));

        //Wait till the object of TestImsCallSessionImpl for second call created.
        waitUntilConditionIsTrueOrTimeout(
                new Condition() {
                    @Override
                    public Object expected() {
                        return true;
                    }

                    @Override
                    public Object actual() {
                        TestMmTelFeature mmtelfeatue = sServiceConnector.getCarrierService()
                                .getMmTelFeature();
                        return (mmtelfeatue.getImsCallsession() != mCallSession1) ? true : false;
                    }
                }, WAIT_FOR_CONDITION, "CallSession Created");

        mCallSession2 = sServiceConnector.getCarrierService().getMmTelFeature().getImsCallsession();
        isCallActive(mCall2, mCallSession2);
        assertTrue("Call is not in Active State", (mCall2.getDetails().getState()
                == Call.STATE_ACTIVE));
    }

    private void resetCallSessionObjects() {
        mCall1 = mCall2 = null;
        mCallSession1 = mCallSession2 = null;
        ConferenceHelper confHelper = sServiceConnector.getCarrierService().getMmTelFeature()
                .getConferenceHelper();
        if (confHelper != null) {
            confHelper.clearSessions();
        }
    }

    public void waitForUnboundService() {
        waitUntilConditionIsTrueOrTimeout(
                new Condition() {
                    @Override
                    public Object expected() {
                        return true;
                    }

                    @Override
                    public Object actual() {
                        InCallServiceStateValidator inCallService = mServiceCallBack.getService();
                        return (inCallService.isServiceUnBound()) ? true : false;
                    }
                }, WAIT_FOR_SERVICE_TO_UNBOUND, "Service Unbound");
    }

    public void isCallActive(Call call, TestImsCallSessionImpl callsession) {
        assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_ACTIVE, WAIT_FOR_CALL_STATE));
        assertNotNull("Unable to get callSession, its null", callsession);

        waitUntilConditionIsTrueOrTimeout(
                new Condition() {
                    @Override
                    public Object expected() {
                        return true;
                    }

                    @Override
                    public Object actual() {
                        return (callsession.isInCall()) ? true : false;
                    }
                }, WAIT_FOR_CONDITION, "Call Active");
    }

    public void isCallDisconnected(Call call, TestImsCallSessionImpl callsession) {
        assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_DISCONNECTED, WAIT_FOR_CALL_STATE));
        assertNotNull("Unable to get callSession, its null", callsession);

        waitUntilConditionIsTrueOrTimeout(
                new Condition() {
                    @Override
                    public Object expected() {
                        return true;
                    }

                    @Override
                    public Object actual() {
                        return (callsession.isInTerminated()) ? true : false;
                    }
                }, WAIT_FOR_CONDITION, "Call Disconnected");
    }

    private void setCallID(String callid) {
        assertNotNull("Call Id is set to null", callid);
        mCurrentCallId = callid;
    }

    public void addCall(Call call) {
        String callid = getCallId(call);
        setCallID(callid);
        synchronized (mCalls) {
            mCalls.put(callid, call);
        }
    }

    public String getCallId(Call call) {
        String str = call.toString();
        String[] arrofstr = str.split(",", 3);
        int index = arrofstr[0].indexOf(":");
        String callId = arrofstr[0].substring(index + 1);
        return callId;
    }

    public Call getCall(String callId) {
        synchronized (mCalls) {
            if (mCalls.isEmpty()) {
                return null;
            }

            for (Map.Entry<String, Call> entry : mCalls.entrySet()) {
                if (entry.getKey().equals(callId)) {
                    Call call = entry.getValue();
                    assertNotNull("Call is not added, its null", call);
                    return call;
                }
            }
        }
        return null;
    }

    private void removeCall(Call call) {
        if (mCalls.isEmpty()) {
            return;
        }

        String callid = getCallId(call);
        Map.Entry<String, Call>[] entries = mCalls.entrySet().toArray(new Map.Entry[mCalls.size()]);
        for (Map.Entry<String, Call> entry : entries) {
            if (entry.getKey().equals(callid)) {
                mCalls.remove(entry.getKey());
                mCurrentCallId = null;
            }
        }
    }

    class ServiceCallBack extends InCallServiceCallbacks {

        @Override
        public void onCallAdded(Call call, int numCalls) {
            Log.i(LOG_TAG, "onCallAdded, Call: " + call + ", Num Calls: " + numCalls);
            addCall(call);
            countDownLatch(LATCH_IS_ON_CALL_ADDED);
        }

        @Override
        public void onCallRemoved(Call call, int numCalls) {
            Log.i(LOG_TAG, "onCallRemoved, Call: " + call + ", Num Calls: " + numCalls);
            removeCall(call);
            countDownLatch(LATCH_IS_ON_CALL_REMOVED);
        }

        @Override
        public void onCallStateChanged(Call call, int state) {
            Log.i(LOG_TAG, "onCallStateChanged " + state + "Call: " + call);

            switch(state) {
                case Call.STATE_DIALING : {
                    countDownLatch(LATCH_IS_CALL_DIALING);
                    break;
                }
                case Call.STATE_ACTIVE : {
                    countDownLatch(LATCH_IS_CALL_ACTIVE);
                    break;
                }
                case Call.STATE_DISCONNECTING : {
                    countDownLatch(LATCH_IS_CALL_DISCONNECTING);
                    break;
                }
                case Call.STATE_DISCONNECTED : {
                    countDownLatch(LATCH_IS_CALL_DISCONNECTED);
                    break;
                }
                case Call.STATE_RINGING : {
                    countDownLatch(LATCH_IS_CALL_RINGING);
                    break;
                }
                case Call.STATE_HOLDING : {
                    countDownLatch(LATCH_IS_CALL_HOLDING);
                    break;
                }
                default:
                    break;
            }
        }

        @Override
        public void onChildrenChanged(Call call, List<Call> children) {
            if (call.getDetails().hasProperty(Call.Details.PROPERTY_CONFERENCE)) {
                mParticipantCount = children.size();
            }
        }
    }

    private static Context getContext() {
        return InstrumentationRegistry.getInstrumentation().getContext();
    }

    private static String setDefaultDialer(Instrumentation instrumentation, String packageName)
            throws Exception {
        String str =  TelephonyUtils.executeShellCommand(instrumentation, COMMAND_SET_DEFAULT_DIALER
                + packageName);
        return str;
    }

    private static String getDefaultDialer(Instrumentation instrumentation) throws Exception {
        String str = TelephonyUtils.executeShellCommand(instrumentation,
                COMMAND_GET_DEFAULT_DIALER);
        return str;
    }

    private static void overrideCarrierConfig(PersistableBundle bundle) throws Exception {
        CarrierConfigManager carrierConfigManager = InstrumentationRegistry.getInstrumentation()
                .getContext().getSystemService(CarrierConfigManager.class);
        sReceiver.clearQueue();
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(carrierConfigManager,
                (m) -> m.overrideConfig(sTestSub, bundle));
        sReceiver.waitForChanged();
    }

}
