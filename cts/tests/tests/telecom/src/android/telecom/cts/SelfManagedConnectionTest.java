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

package android.telecom.cts;

import android.app.ActivityManager;
import android.app.UiAutomation;
import android.app.role.RoleManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.Configuration;
import android.graphics.Color;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.IBinder;
import android.os.UserHandle;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telecom.cts.carmodetestapp.CtsCarModeInCallServiceControl;
import android.telecom.cts.carmodetestapp.ICtsCarModeInCallServiceControl;
import android.telecom.cts.carmodetestappselfmanaged.CtsCarModeInCallServiceControlSelfManaged;
import android.telecom.cts.carmodetestapptwo.CtsCarModeInCallServiceControlTwo;
import android.telecom.cts.thirdptydialer.CtsThirdPtyDialerInCallServiceControl;
import android.telecom.cts.thirdptydialertwo.CtsThirdPtyDialerInCallServiceControlTwo;
import android.telecom.cts.thirdptyincallservice.CtsThirdPartyInCallServiceControl;
import android.telecom.cts.thirdptyincallservice.ICtsThirdPartyInCallServiceControl;
import android.util.Log;

import com.android.compatibility.common.util.FeatureUtil;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

public class SelfManagedConnectionTest extends BaseTelecomTestWithMockServices {
    private static final String TAG = "SelfManagedConnectionTest";
    private static final long TIMEOUT = 3000L;
    private static final String THIRD_PTY_CONTROL =
            CtsThirdPartyInCallServiceControl.CONTROL_INTERFACE_ACTION;
    private static final String CAR_MODE_CONTROL =
            "android.telecom.cts.carmodetestapp.ACTION_CAR_MODE_CONTROL";
    private static final ComponentName NON_UI_INCALLSERVICE = ComponentName.createRelative(
            CtsThirdPartyInCallServiceControl.class.getPackage().getName(),
            CtsThirdPartyInCallServiceControl.class.getName());
    // Default dialer that not support self-managed calls
    private static final String DEFAULT_DIALER_PKG_1 = CtsThirdPtyDialerInCallServiceControl.class
            .getPackage().getName();
    private static final ComponentName DEFAULT_DIALER_INCALLSERVICE_1 = ComponentName
            .createRelative(DEFAULT_DIALER_PKG_1,
                    CtsThirdPtyDialerInCallServiceControl.class.getName());
    // Default dialerhat support self-managed calls
    private static final String DEFAULT_DIALER_PKG_2 = CtsThirdPtyDialerInCallServiceControlTwo
            .class.getPackage().getName();
    private static final ComponentName DEFAULT_DIALER_INCALLSERVICE_2 = ComponentName
            .createRelative(DEFAULT_DIALER_PKG_2,
                    CtsThirdPtyDialerInCallServiceControlTwo.class.getName());
    private static final String CAR_DIALER_PKG_1 = CtsCarModeInCallServiceControl.class
            .getPackage().getName();
    private static final ComponentName CAR_DIALER_1 = ComponentName.createRelative(
            CAR_DIALER_PKG_1, CtsCarModeInCallServiceControl.class.getName());
    private static final String CAR_DIALER_PKG_2 = CtsCarModeInCallServiceControlTwo.class
            .getPackage().getName();
    private static final String CAR_SELF_MANAGED_PKG =
            CtsCarModeInCallServiceControlSelfManaged.class
                    .getPackage().getName();
    private static final ComponentName CAR_DIALER_2 = ComponentName.createRelative(
            CAR_DIALER_PKG_2, CtsCarModeInCallServiceControlTwo.class.getName());
    private static final ComponentName CAR_SELF_MANAGED_COMPONENT = ComponentName.createRelative(
            CAR_SELF_MANAGED_PKG, CtsCarModeInCallServiceControlSelfManaged.class.getName());

    private Uri TEST_ADDRESS = Uri.fromParts("tel", "6505551213", null);

    private static final PhoneAccountHandle TEST_CAR_SELF_MANAGED_HANDLE =
            new PhoneAccountHandle(
                    new ComponentName(CAR_SELF_MANAGED_PKG, TestUtils.SELF_MANAGED_COMPONENT),
                    TestUtils.SELF_MANAGED_ACCOUNT_ID_1);

    private static final PhoneAccount TEST_SELF_MANAGED_PHONE_ACCOUNT = PhoneAccount.builder(
                    TEST_CAR_SELF_MANAGED_HANDLE, TestUtils.SELF_MANAGED_ACCOUNT_LABEL)
            .setAddress(Uri.parse("sip:test@test.com"))
            .setSubscriptionAddress(Uri.parse("sip:test@test.com"))
            .setCapabilities(PhoneAccount.CAPABILITY_SELF_MANAGED
                    | PhoneAccount.CAPABILITY_SUPPORTS_VIDEO_CALLING
                    | PhoneAccount.CAPABILITY_VIDEO_CALLING)
            .setHighlightColor(Color.BLUE)
            .setShortDescription(TestUtils.SELF_MANAGED_ACCOUNT_LABEL)
            .addSupportedUriScheme(PhoneAccount.SCHEME_TEL)
            .addSupportedUriScheme(PhoneAccount.SCHEME_SIP)
            .setExtras(TestUtils.SELF_MANAGED_ACCOUNT_1_EXTRAS)
            .build();

    private RoleManager mRoleManager;
    private String mDefaultDialer;
    private UiAutomation mUiAutomation;
    private ICtsCarModeInCallServiceControl mCarModeIncallServiceControlOne;
    private ICtsCarModeInCallServiceControl mCarModeIncallServiceControlTwo;
    private ICtsCarModeInCallServiceControl mCarModeIncallServiceControlSelfManaged;

    private class TestServiceConnection implements ServiceConnection {
        private IBinder mService;
        private CountDownLatch mLatch = new CountDownLatch(1);
        private boolean mIsConnected;

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            Log.i(TAG, "Service Connected: " + componentName);
            mService = service;
            mIsConnected = true;
            mLatch.countDown();
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mService = null;
        }

        public IBinder getService() {
            return mService;
        }

        public boolean waitBind() {
            try {
                mLatch.await(TIMEOUT, TimeUnit.MILLISECONDS);
                return mIsConnected;
            } catch (InterruptedException e) {
                return false;
            }
        }
    }

    @Override
    protected void setUp() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }
        super.setUp();
        NewOutgoingCallBroadcastReceiver.reset();
        mContext = getInstrumentation().getContext();
        mUiAutomation = getInstrumentation().getUiAutomation();
        if (mShouldTestTelecom) {
            mRoleManager = mContext.getSystemService(RoleManager.class);
            setupConnectionService(null, FLAG_ENABLE | FLAG_REGISTER);
            mTelecomManager.registerPhoneAccount(TestUtils.TEST_SELF_MANAGED_PHONE_ACCOUNT_4);
            mDefaultDialer = getDefaultDialer();
        }
    }

    @Override
    protected void tearDown() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }
        super.tearDown();

        disableAndVerifyCarMode(mCarModeIncallServiceControlOne, Configuration.UI_MODE_TYPE_NORMAL);
        disableAndVerifyCarMode(mCarModeIncallServiceControlTwo, Configuration.UI_MODE_TYPE_NORMAL);

        disconnectAllCallsAndVerify(mCarModeIncallServiceControlOne);
        disconnectAllCallsAndVerify(mCarModeIncallServiceControlTwo);

        CtsSelfManagedConnectionService connectionService =
                CtsSelfManagedConnectionService.getConnectionService();
        if (connectionService != null) {
            connectionService.tearDown();
            mTelecomManager.unregisterPhoneAccount(TestUtils.TEST_SELF_MANAGED_HANDLE_4);
            assertTrue(setDefaultDialer(mDefaultDialer));
        }
    }

    /**
     * Test bind to non-UI in call services that support self-managed connections
     */
    public void testBindToSupportNonUiInCallService() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }
        TestServiceConnection controlConn = setUpControl(THIRD_PTY_CONTROL,
                NON_UI_INCALLSERVICE);
        ICtsThirdPartyInCallServiceControl control = ICtsThirdPartyInCallServiceControl.Stub
                .asInterface(controlConn.getService());
        control.resetLatchForServiceBound(true /* bind */);

        mUiAutomation.adoptShellPermissionIdentity("android.permission.CONTROL_INCALL_EXPERIENCE");
        SelfManagedConnection connection = placeAndVerifySelfManagedCall();
        control.checkBindStatus(true /* bindStatus */);
        assertTrue(control.checkBindStatus(true /* bindStatus */));
        connection.waitOnInCallServiceTrackingChanged();
        assertTrue(connection.isTracked());
        mUiAutomation.dropShellPermissionIdentity();

        connection.disconnectAndDestroy();
        assertIsInCall(false);
        mContext.unbindService(controlConn);
    }

    /**
     * Test bind to default dialer that support self-managed connections when device is not in car
     * mode
     */
    public void testBindToSupportDefaultDialerNoCarMode() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }
        TestServiceConnection controlConn = setUpControl(THIRD_PTY_CONTROL,
                DEFAULT_DIALER_INCALLSERVICE_2);
        ICtsThirdPartyInCallServiceControl control = ICtsThirdPartyInCallServiceControl.Stub
                .asInterface(controlConn.getService());
        TestUtils.setDefaultDialer(getInstrumentation(), DEFAULT_DIALER_PKG_2);
        control.resetLatchForServiceBound(true /* bind */);

        SelfManagedConnection connection = placeAndVerifySelfManagedCall();
        assertTrue(control.checkBindStatus(true /* bindStatus */));

        connection.waitOnInCallServiceTrackingChanged();
        assertTrue(connection.isAlternativeUiShowing());
        mUiAutomation.dropShellPermissionIdentity();

        connection.disconnectAndDestroy();
        assertIsInCall(false);
        mContext.unbindService(controlConn);
    }

    /**
     * Test not bind to default dialer that not support self-managed connections when device is not
     * in car mode
     */
    public void testNoBindToUnsupportDefaultDialerNoCarMode() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }
        TestServiceConnection controlConn = setUpControl(THIRD_PTY_CONTROL,
                DEFAULT_DIALER_INCALLSERVICE_1);
        ICtsThirdPartyInCallServiceControl control = ICtsThirdPartyInCallServiceControl.Stub
                .asInterface(controlConn.getService());
        assertTrue(setDefaultDialer(DEFAULT_DIALER_PKG_1));
        control.resetLatchForServiceBound(true /* bind */);

        SelfManagedConnection connection = placeAndVerifySelfManagedCall();
        assertFalse(control.checkBindStatus(true /* bindStatus */));

        connection.disconnectAndDestroy();
        assertIsInCall(false);
        mContext.unbindService(controlConn);
    }

    public void testEnterCarMode() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }
        // carMode is not supported in Wear OS
        if (FeatureUtil.isWatch()) {
            return;
        }
        TestServiceConnection controlConn = setUpControl(CAR_MODE_CONTROL,
                CAR_DIALER_1);
        mCarModeIncallServiceControlOne = ICtsCarModeInCallServiceControl.Stub
                .asInterface(controlConn.getService());
        mCarModeIncallServiceControlOne.reset();

        SelfManagedConnection connection = placeAndVerifySelfManagedCall();
        mUiAutomation.adoptShellPermissionIdentity(
                "android.permission.ENTER_CAR_MODE_PRIORITIZED",
                "android.permission.CONTROL_INCALL_EXPERIENCE");
        mCarModeIncallServiceControlOne.enableCarMode(1000);
        assertTrue(mCarModeIncallServiceControlOne.checkBindStatus(true /* bindStatus */));
        mCarModeIncallServiceControlOne.disableCarMode();
        mUiAutomation.dropShellPermissionIdentity();

        connection.disconnectAndDestroy();
        assertIsInCall(false);
        mContext.unbindService(controlConn);
    }

    /**
     * Test {@link TelecomManager#getOwnSelfManagedPhoneAccounts} works on packages with only the
     * {@link android.Manifest.permission#MANAGE_OWN_CALLS} permission.
     */
    public void testTelecomManagerGetSelfManagedPhoneAccountsForPackage() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }
        // bind to CarModeTestAppSelfManaged which only has the
        // {@link android.Manifest.permission#MANAGE_OWN_CALLS} permission
        TestServiceConnection control = setUpControl(CAR_MODE_CONTROL, CAR_SELF_MANAGED_COMPONENT);

        mCarModeIncallServiceControlSelfManaged =
                ICtsCarModeInCallServiceControl.Stub
                        .asInterface(control.getService());

        mCarModeIncallServiceControlSelfManaged.reset();

        // register a self-managed phone account
        mCarModeIncallServiceControlSelfManaged.registerPhoneAccount(
                TEST_SELF_MANAGED_PHONE_ACCOUNT);

        List<PhoneAccountHandle> pah =
                mCarModeIncallServiceControlSelfManaged.getOwnSelfManagedPhoneAccounts();

        // assert that we can get all the self-managed phone accounts registered to
        // CarModeTestAppSelfManaged
        assertEquals(1, pah.size());
        assertTrue(pah.contains(TEST_CAR_SELF_MANAGED_HANDLE));

        mCarModeIncallServiceControlSelfManaged.unregisterPhoneAccount(
                TEST_CAR_SELF_MANAGED_HANDLE);

        // unbind to CarModeTestAppSelfManaged
        mContext.unbindService(control);
    }

    public void testChangeCarModeApp() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }
        // carMode is not supported in Wear OS
        if (FeatureUtil.isWatch()) {
            return;
        }
        TestServiceConnection controlConn1 = setUpControl(CAR_MODE_CONTROL, CAR_DIALER_1);
        TestServiceConnection controlConn2 = setUpControl(CAR_MODE_CONTROL, CAR_DIALER_2);
        mCarModeIncallServiceControlOne = ICtsCarModeInCallServiceControl.Stub
                .asInterface(controlConn1.getService());
        mCarModeIncallServiceControlTwo = ICtsCarModeInCallServiceControl.Stub
                .asInterface(controlConn2.getService());
        mCarModeIncallServiceControlOne.reset();
        mCarModeIncallServiceControlTwo.reset();

        mUiAutomation.adoptShellPermissionIdentity(
                "android.permission.ENTER_CAR_MODE_PRIORITIZED",
                "android.permission.CONTROL_INCALL_EXPERIENCE");
        mCarModeIncallServiceControlOne.enableCarMode(999);

        SelfManagedConnection connection = placeAndVerifySelfManagedCall();
        assertTrue(mCarModeIncallServiceControlOne.checkBindStatus(true /* bindStatus */));
        mCarModeIncallServiceControlTwo.enableCarMode(1000);
        assertTrue(mCarModeIncallServiceControlOne.checkBindStatus(false /* bindStatus */));
        assertTrue(mCarModeIncallServiceControlTwo.checkBindStatus(true /* bindStatus */));

        mCarModeIncallServiceControlOne.disableCarMode();
        mCarModeIncallServiceControlTwo.disableCarMode();

        connection.disconnectAndDestroy();
        assertIsInCall(false);
        mUiAutomation.dropShellPermissionIdentity();
        mContext.unbindService(controlConn1);
        mContext.unbindService(controlConn2);
    }

    public void testExitCarMode() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }
        // carMode is not supported in Wear OS
        if (FeatureUtil.isWatch()) {
            return;
        }
        TestServiceConnection controlConn = setUpControl(CAR_MODE_CONTROL, CAR_DIALER_1);
        mCarModeIncallServiceControlOne = ICtsCarModeInCallServiceControl.Stub
                .asInterface(controlConn.getService());
        mCarModeIncallServiceControlOne.reset();

        mUiAutomation.adoptShellPermissionIdentity(
                "android.permission.ENTER_CAR_MODE_PRIORITIZED",
                "android.permission.CONTROL_INCALL_EXPERIENCE");
        mCarModeIncallServiceControlOne.enableCarMode(1000);

        SelfManagedConnection connection = placeAndVerifySelfManagedCall();
        assertTrue(mCarModeIncallServiceControlOne.checkBindStatus(true /* bindStatus */));
        mCarModeIncallServiceControlOne.disableCarMode();
        assertTrue(mCarModeIncallServiceControlOne.checkBindStatus(false /* bindStatus */));
        mUiAutomation.dropShellPermissionIdentity();

        connection.disconnectAndDestroy();
        assertIsInCall(false);
        mContext.unbindService(controlConn);
    }

    private TestServiceConnection setUpControl(String action, ComponentName componentName) {
        Intent bindIntent = new Intent(action);
        bindIntent.setComponent(componentName);

        TestServiceConnection serviceConnection = new TestServiceConnection();
        mContext.bindService(bindIntent, serviceConnection, Context.BIND_AUTO_CREATE);
        if (!serviceConnection.waitBind()) {
            fail("fail bind to service");
        }
        return serviceConnection;
    }

    private boolean setDefaultDialer(String packageName) {
        mUiAutomation.adoptShellPermissionIdentity();
        try {
            CompletableFuture<Boolean> future = new CompletableFuture<>();
            Consumer<Boolean> callback = successful -> {
                future.complete(successful);
            };

            mRoleManager.addRoleHolderAsUser(RoleManager.ROLE_DIALER, packageName, 0,
                    UserHandle.of(ActivityManager.getCurrentUser()), AsyncTask.THREAD_POOL_EXECUTOR,
                    callback);
            return future.get(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            e.printStackTrace();
            return false;
        } finally {
            mUiAutomation.dropShellPermissionIdentity();
        }
    }

    private String getDefaultDialer() {
        mUiAutomation.adoptShellPermissionIdentity();
        String result = mRoleManager.getRoleHolders(RoleManager.ROLE_DIALER).get(0);
        mUiAutomation.dropShellPermissionIdentity();
        return result;
    }

    private SelfManagedConnection placeAndVerifySelfManagedCall() {
        TestUtils.addIncomingCall(getInstrumentation(), mTelecomManager,
                TestUtils.TEST_SELF_MANAGED_HANDLE_4, TEST_ADDRESS);
        if (!CtsSelfManagedConnectionService.waitForBinding()) {
            fail("Could not bind to Self-Managed ConnectionService");
        }
        return TestUtils.waitForAndGetConnection(TEST_ADDRESS);
    }
}
