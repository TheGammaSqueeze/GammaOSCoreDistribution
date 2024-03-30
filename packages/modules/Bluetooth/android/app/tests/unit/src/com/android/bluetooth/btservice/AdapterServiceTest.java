/*
 * Copyright 2017 The Android Open Source Project
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

package com.android.bluetooth.btservice;

import static android.Manifest.permission.BLUETOOTH_SCAN;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import android.app.AlarmManager;
import android.app.admin.DevicePolicyManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.IBluetoothCallback;
import android.content.AttributionSource;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PermissionInfo;
import android.content.res.Resources;
import android.media.AudioManager;
import android.os.AsyncTask;
import android.os.BatteryStatsManager;
import android.os.Binder;
import android.os.Bundle;
import android.os.Looper;
import android.os.PowerManager;
import android.os.Process;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.os.UserManager;
import android.permission.PermissionCheckerManager;
import android.permission.PermissionManager;
import android.provider.Settings;
import android.sysprop.BluetoothProperties;
import android.test.mock.MockContentProvider;
import android.test.mock.MockContentResolver;
import android.util.Log;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.MediumTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.bluetooth.TestUtils;
import com.android.bluetooth.Utils;
import com.android.bluetooth.a2dp.A2dpService;
import com.android.bluetooth.a2dpsink.A2dpSinkService;
import com.android.bluetooth.avrcp.AvrcpTargetService;
import com.android.bluetooth.avrcpcontroller.AvrcpControllerService;
import com.android.bluetooth.bas.BatteryService;
import com.android.bluetooth.bass_client.BassClientService;
import com.android.bluetooth.csip.CsipSetCoordinatorService;
import com.android.bluetooth.gatt.GattService;
import com.android.bluetooth.hap.HapClientService;
import com.android.bluetooth.hearingaid.HearingAidService;
import com.android.bluetooth.hfp.HeadsetService;
import com.android.bluetooth.hfpclient.HeadsetClientService;
import com.android.bluetooth.hid.HidDeviceService;
import com.android.bluetooth.hid.HidHostService;
import com.android.bluetooth.le_audio.LeAudioService;
import com.android.bluetooth.map.BluetoothMapService;
import com.android.bluetooth.mapclient.MapClientService;
import com.android.bluetooth.mcp.McpService;
import com.android.bluetooth.opp.BluetoothOppService;
import com.android.bluetooth.pan.PanService;
import com.android.bluetooth.pbap.BluetoothPbapService;
import com.android.bluetooth.pbapclient.PbapClientService;
import com.android.bluetooth.sap.SapService;
import com.android.bluetooth.tbs.TbsService;
import com.android.bluetooth.vc.VolumeControlService;
import com.android.internal.app.IBatteryStats;

import libcore.util.HexEncoding;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class AdapterServiceTest {
    private static final String TAG = AdapterServiceTest.class.getSimpleName();
    private static final String TEST_BT_ADDR_1 = "00:11:22:33:44:55";
    private static final String TEST_BT_ADDR_2 = "00:11:22:33:44:66";

    private AdapterService mAdapterService;
    private AdapterService.AdapterServiceBinder mServiceBinder;

    private @Mock Context mMockContext;
    private @Mock ApplicationInfo mMockApplicationInfo;
    private @Mock AlarmManager mMockAlarmManager;
    private @Mock Resources mMockResources;
    private @Mock UserManager mMockUserManager;
    private @Mock DevicePolicyManager mMockDevicePolicyManager;
    private @Mock ProfileService mMockGattService;
    private @Mock ProfileService mMockService;
    private @Mock ProfileService mMockService2;
    private @Mock IBluetoothCallback mIBluetoothCallback;
    private @Mock Binder mBinder;
    private @Mock AudioManager mAudioManager;
    private @Mock android.app.Application mApplication;
    private @Mock MetricsLogger mMockMetricsLogger;

    // BatteryStatsManager is final and cannot be mocked with regular mockito, so just mock the
    // underlying binder calls.
    final BatteryStatsManager mBatteryStatsManager =
            new BatteryStatsManager(mock(IBatteryStats.class));

    private static final int CONTEXT_SWITCH_MS = 100;
    private static final int PROFILE_SERVICE_TOGGLE_TIME_MS = 200;
    private static final int GATT_START_TIME_MS = 500;
    private static final int ONE_SECOND_MS = 1000;
    private static final int NATIVE_INIT_MS = 8000;
    private static final int NATIVE_DISABLE_MS = 1000;

    private final AttributionSource mAttributionSource = new AttributionSource.Builder(
            Process.myUid()).build();

    private PowerManager mPowerManager;
    private PermissionCheckerManager mPermissionCheckerManager;
    private PermissionManager mPermissionManager;
    private PackageManager mMockPackageManager;
    private MockContentResolver mMockContentResolver;
    private HashMap<String, HashMap<String, String>> mAdapterConfig;
    private int mForegroundUserId;

    private void configureEnabledProfiles() {
        Log.e("AdapterServiceTest", "configureEnabledProfiles");
        Config.setProfileEnabled(PanService.class, true);
        Config.setProfileEnabled(BluetoothPbapService.class, true);
        Config.setProfileEnabled(GattService.class, true);

        Config.setProfileEnabled(A2dpService.class, false);
        Config.setProfileEnabled(A2dpSinkService.class, false);
        Config.setProfileEnabled(AvrcpTargetService.class, false);
        Config.setProfileEnabled(AvrcpControllerService.class, false);
        Config.setProfileEnabled(BassClientService.class, false);
        Config.setProfileEnabled(BatteryService.class, false);
        Config.setProfileEnabled(CsipSetCoordinatorService.class, false);
        Config.setProfileEnabled(HapClientService.class, false);
        Config.setProfileEnabled(HeadsetService.class, false);
        Config.setProfileEnabled(HeadsetClientService.class, false);
        Config.setProfileEnabled(HearingAidService.class, false);
        Config.setProfileEnabled(HidDeviceService.class, false);
        Config.setProfileEnabled(HidHostService.class, false);
        Config.setProfileEnabled(LeAudioService.class, false);
        Config.setProfileEnabled(TbsService.class, false);
        Config.setProfileEnabled(BluetoothMapService.class, false);
        Config.setProfileEnabled(MapClientService.class, false);
        Config.setProfileEnabled(McpService.class, false);
        Config.setProfileEnabled(BluetoothOppService.class, false);
        Config.setProfileEnabled(PbapClientService.class, false);
        Config.setProfileEnabled(SapService.class, false);
        Config.setProfileEnabled(VolumeControlService.class, false);
    }

    @BeforeClass
    public static void setupClass() {
        Log.e("AdapterServiceTest", "setupClass");
        // Bring native layer up and down to make sure config files are properly loaded
        if (Looper.myLooper() == null) {
            Looper.prepare();
        }
        Assert.assertNotNull(Looper.myLooper());
        AdapterService adapterService = new AdapterService();
        adapterService.initNative(false /* is_restricted */, false /* is_common_criteria_mode */,
                0 /* config_compare_result */, new String[0], false, "");
        adapterService.cleanupNative();
        HashMap<String, HashMap<String, String>> adapterConfig = TestUtils.readAdapterConfig();
        Assert.assertNotNull(adapterConfig);
        Assert.assertNotNull("metrics salt is null: " + adapterConfig.toString(),
                getMetricsSalt(adapterConfig));
    }

    @Before
    public void setUp() throws PackageManager.NameNotFoundException {
        Log.e("AdapterServiceTest", "setUp()");
        MockitoAnnotations.initMocks(this);
        if (Looper.myLooper() == null) {
            Looper.prepare();
        }
        Assert.assertNotNull(Looper.myLooper());

        // Dispatch all async work through instrumentation so we can wait until
        // it's drained below
        AsyncTask.setDefaultExecutor((r) -> {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(r);
        });
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .adoptShellPermissionIdentity();

        InstrumentationRegistry.getInstrumentation().runOnMainSync(
                () -> mAdapterService = new AdapterService());
        mServiceBinder = new AdapterService.AdapterServiceBinder(mAdapterService);
        mMockPackageManager = mock(PackageManager.class);
        when(mMockPackageManager.getPermissionInfo(any(), anyInt()))
                .thenReturn(new PermissionInfo());

        mMockContentResolver = new MockContentResolver(InstrumentationRegistry.getTargetContext());
        mMockContentResolver.addProvider(Settings.AUTHORITY, new MockContentProvider() {
            @Override
            public Bundle call(String method, String request, Bundle args) {
                return Bundle.EMPTY;
            }
        });

        mPowerManager = InstrumentationRegistry.getTargetContext()
                .getSystemService(PowerManager.class);
        mPermissionCheckerManager = InstrumentationRegistry.getTargetContext()
                .getSystemService(PermissionCheckerManager.class);

        mPermissionManager = InstrumentationRegistry.getTargetContext()
                .getSystemService(PermissionManager.class);

        when(mMockContext.getCacheDir()).thenReturn(InstrumentationRegistry.getTargetContext()
                .getCacheDir());
        when(mMockContext.getApplicationInfo()).thenReturn(mMockApplicationInfo);
        when(mMockContext.getContentResolver()).thenReturn(mMockContentResolver);
        when(mMockContext.getApplicationContext()).thenReturn(mMockContext);
        when(mMockContext.createContextAsUser(UserHandle.SYSTEM, /* flags= */ 0)).thenReturn(
                mMockContext);
        when(mMockContext.getResources()).thenReturn(mMockResources);
        when(mMockContext.getUserId()).thenReturn(Process.BLUETOOTH_UID);
        when(mMockContext.getPackageManager()).thenReturn(mMockPackageManager);
        when(mMockContext.getSystemService(Context.USER_SERVICE)).thenReturn(mMockUserManager);
        when(mMockContext.getSystemServiceName(UserManager.class)).thenReturn(Context.USER_SERVICE);
        when(mMockContext.getSystemService(Context.DEVICE_POLICY_SERVICE)).thenReturn(
                mMockDevicePolicyManager);
        when(mMockContext.getSystemServiceName(DevicePolicyManager.class))
                .thenReturn(Context.DEVICE_POLICY_SERVICE);
        when(mMockContext.getSystemService(Context.POWER_SERVICE)).thenReturn(mPowerManager);
        when(mMockContext.getSystemServiceName(PowerManager.class))
                .thenReturn(Context.POWER_SERVICE);
        when(mMockContext.getSystemServiceName(PermissionCheckerManager.class))
                .thenReturn(Context.PERMISSION_CHECKER_SERVICE);
        when(mMockContext.getSystemService(Context.PERMISSION_CHECKER_SERVICE))
                .thenReturn(mPermissionCheckerManager);
        when(mMockContext.getSystemServiceName(PermissionManager.class))
                .thenReturn(Context.PERMISSION_SERVICE);
        when(mMockContext.getSystemService(Context.PERMISSION_SERVICE))
                .thenReturn(mPermissionManager);
        when(mMockContext.getSystemService(Context.ALARM_SERVICE)).thenReturn(mMockAlarmManager);
        when(mMockContext.getSystemServiceName(AlarmManager.class))
                .thenReturn(Context.ALARM_SERVICE);
        when(mMockContext.getSystemService(Context.AUDIO_SERVICE)).thenReturn(mAudioManager);
        when(mMockContext.getSystemServiceName(AudioManager.class))
                .thenReturn(Context.AUDIO_SERVICE);
        when(mMockContext.getSystemService(Context.BATTERY_STATS_SERVICE))
                .thenReturn(mBatteryStatsManager);
        when(mMockContext.getSystemServiceName(BatteryStatsManager.class))
                .thenReturn(Context.BATTERY_STATS_SERVICE);
        when(mMockContext.getSharedPreferences(anyString(), anyInt()))
                .thenReturn(InstrumentationRegistry.getTargetContext()
                        .getSharedPreferences("AdapterServiceTestPrefs", Context.MODE_PRIVATE));

        when(mMockContext.getAttributionSource()).thenReturn(mAttributionSource);
        doAnswer(invocation -> {
            Object[] args = invocation.getArguments();
            return InstrumentationRegistry.getTargetContext().getDatabasePath((String) args[0]);
        }).when(mMockContext).getDatabasePath(anyString());

        // Sets the foreground user id to match that of the tests (restored in tearDown)
        mForegroundUserId = Utils.getForegroundUserId();
        int callingUid = Binder.getCallingUid();
        UserHandle callingUser = UserHandle.getUserHandleForUid(callingUid);
        Utils.setForegroundUserId(callingUser.getIdentifier());

        when(mMockDevicePolicyManager.isCommonCriteriaModeEnabled(any())).thenReturn(false);

        when(mIBluetoothCallback.asBinder()).thenReturn(mBinder);

        doReturn(Process.BLUETOOTH_UID).when(mMockPackageManager)
                .getPackageUidAsUser(any(), anyInt(), anyInt());

        when(mMockGattService.getName()).thenReturn("GattService");
        when(mMockService.getName()).thenReturn("Service1");
        when(mMockService2.getName()).thenReturn("Service2");

        when(mMockMetricsLogger.init(any())).thenReturn(true);
        when(mMockMetricsLogger.close()).thenReturn(true);

        configureEnabledProfiles();
        Config.init(mMockContext);

        mAdapterService.setMetricsLogger(mMockMetricsLogger);

        // Attach a context to the service for permission checks.
        mAdapterService.attach(mMockContext, null, null, null, mApplication, null);
        mAdapterService.onCreate();

        // Wait for any async events to drain
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();

        mServiceBinder.registerCallback(mIBluetoothCallback, mAttributionSource);

        mAdapterConfig = TestUtils.readAdapterConfig();
        Assert.assertNotNull(mAdapterConfig);
    }

    @After
    public void tearDown() {
        Log.e("AdapterServiceTest", "tearDown()");

        // Restores the foregroundUserId to the ID prior to the test setup
        Utils.setForegroundUserId(mForegroundUserId);

        mServiceBinder.unregisterCallback(mIBluetoothCallback, mAttributionSource);
        mAdapterService.cleanup();
    }

    @AfterClass
    public static void tearDownOnce() {
        AsyncTask.setDefaultExecutor(AsyncTask.SERIAL_EXECUTOR);
    }

    private void verifyStateChange(int prevState, int currState, int callNumber, int timeoutMs) {
        try {
            verify(mIBluetoothCallback, timeout(timeoutMs)
                    .times(callNumber)).onBluetoothStateChange(prevState, currState);
        } catch (Exception e) {
            // the mocked onBluetoothStateChange doesn't throw exceptions
        }
    }

    private void doEnable(int invocationNumber, boolean onlyGatt) {
        Log.e("AdapterServiceTest", "doEnable() start");
        Assert.assertFalse(mAdapterService.getState() == BluetoothAdapter.STATE_ON);

        int startServiceCalls;
        startServiceCalls = 2 * (onlyGatt ? 1 : 3); // Start and stop GATT + 2

        mAdapterService.enable(false);

        verifyStateChange(BluetoothAdapter.STATE_OFF, BluetoothAdapter.STATE_BLE_TURNING_ON,
                invocationNumber + 1, CONTEXT_SWITCH_MS);

        // Start GATT
        verify(mMockContext, timeout(GATT_START_TIME_MS).times(
                startServiceCalls * invocationNumber + 1)).startService(any());
        mAdapterService.addProfile(mMockGattService);
        mAdapterService.onProfileServiceStateChanged(mMockGattService, BluetoothAdapter.STATE_ON);

        verifyStateChange(BluetoothAdapter.STATE_BLE_TURNING_ON, BluetoothAdapter.STATE_BLE_ON,
                invocationNumber + 1, NATIVE_INIT_MS);

        mServiceBinder.onLeServiceUp(mAttributionSource);

        verifyStateChange(BluetoothAdapter.STATE_BLE_ON, BluetoothAdapter.STATE_TURNING_ON,
                invocationNumber + 1, CONTEXT_SWITCH_MS);

        if (!onlyGatt) {
            // Start Mock PBAP and PAN services
            verify(mMockContext, timeout(ONE_SECOND_MS).times(
                    startServiceCalls * invocationNumber + 3)).startService(any());

            mAdapterService.addProfile(mMockService);
            mAdapterService.addProfile(mMockService2);
            mAdapterService.onProfileServiceStateChanged(mMockService, BluetoothAdapter.STATE_ON);
            mAdapterService.onProfileServiceStateChanged(mMockService2, BluetoothAdapter.STATE_ON);
        }

        verifyStateChange(BluetoothAdapter.STATE_TURNING_ON, BluetoothAdapter.STATE_ON,
                invocationNumber + 1, PROFILE_SERVICE_TOGGLE_TIME_MS);

        verify(mMockContext, timeout(CONTEXT_SWITCH_MS).times(2 * invocationNumber + 2))
                .sendBroadcast(any(), eq(BLUETOOTH_SCAN),
                        any(Bundle.class));
        final int scanMode = mServiceBinder.getScanMode(mAttributionSource);
        Assert.assertTrue(scanMode == BluetoothAdapter.SCAN_MODE_CONNECTABLE
                || scanMode == BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE);
        Assert.assertTrue(mAdapterService.getState() == BluetoothAdapter.STATE_ON);

        Log.e("AdapterServiceTest", "doEnable() complete success");
    }

    private void doDisable(int invocationNumber, boolean onlyGatt) {
        Assert.assertTrue(mAdapterService.getState() == BluetoothAdapter.STATE_ON);

        int startServiceCalls;
        startServiceCalls = 2 * (onlyGatt ? 1 : 3); // Start and stop GATT + 2

        mAdapterService.disable();

        verifyStateChange(BluetoothAdapter.STATE_ON, BluetoothAdapter.STATE_TURNING_OFF,
                invocationNumber + 1, CONTEXT_SWITCH_MS);

        if (!onlyGatt) {
            // Stop PBAP and PAN services
            verify(mMockContext, timeout(ONE_SECOND_MS).times(
                    startServiceCalls * invocationNumber + 5)).startService(any());

            mAdapterService.onProfileServiceStateChanged(mMockService, BluetoothAdapter.STATE_OFF);
            mAdapterService.onProfileServiceStateChanged(mMockService2, BluetoothAdapter.STATE_OFF);
        }

        verifyStateChange(BluetoothAdapter.STATE_TURNING_OFF, BluetoothAdapter.STATE_BLE_ON,
                invocationNumber + 1, PROFILE_SERVICE_TOGGLE_TIME_MS);

        mServiceBinder.onBrEdrDown(mAttributionSource);

        verifyStateChange(BluetoothAdapter.STATE_BLE_ON, BluetoothAdapter.STATE_BLE_TURNING_OFF,
                invocationNumber + 1, CONTEXT_SWITCH_MS);

        // Stop GATT
        verify(mMockContext, timeout(ONE_SECOND_MS).times(
                startServiceCalls * invocationNumber + startServiceCalls)).startService(any());
        mAdapterService.onProfileServiceStateChanged(mMockGattService, BluetoothAdapter.STATE_OFF);

        verifyStateChange(BluetoothAdapter.STATE_BLE_TURNING_OFF, BluetoothAdapter.STATE_OFF,
                invocationNumber + 1, NATIVE_DISABLE_MS);

        Assert.assertFalse(mAdapterService.getState() == BluetoothAdapter.STATE_ON);
    }

    /**
     * Test: Turn Bluetooth on.
     * Check whether the AdapterService gets started.
     */
    @Ignore("b/228874625")
    @Test
    public void testEnable() {
        Log.e("AdapterServiceTest", "testEnable() start");
        doEnable(0, false);
        Log.e("AdapterServiceTest", "testEnable() end");
    }

    /**
     * Test: Turn Bluetooth on/off.
     * Check whether the AdapterService gets started and stopped.
     */
    @Test
    public void testEnableDisable() {
        doEnable(0, false);
        doDisable(0, false);
    }

    /**
     * Test: Turn Bluetooth on/off with only GATT supported.
     * Check whether the AdapterService gets started and stopped.
     */
    @Test
    public void testEnableDisableOnlyGatt() {
        Context mockContext = mock(Context.class);
        Resources mockResources = mock(Resources.class);

        when(mockContext.getApplicationInfo()).thenReturn(mMockApplicationInfo);
        when(mockContext.getContentResolver()).thenReturn(mMockContentResolver);
        when(mockContext.getApplicationContext()).thenReturn(mockContext);
        when(mockContext.getResources()).thenReturn(mockResources);
        when(mockContext.getUserId()).thenReturn(Process.BLUETOOTH_UID);
        when(mockContext.getPackageManager()).thenReturn(mMockPackageManager);
        when(mockContext.getSystemService(Context.USER_SERVICE)).thenReturn(mMockUserManager);
        when(mockContext.getSystemServiceName(UserManager.class))
                .thenReturn(Context.USER_SERVICE);
        when(mockContext.getSystemService(Context.POWER_SERVICE)).thenReturn(mPowerManager);
        when(mockContext.getSystemServiceName(PowerManager.class))
                .thenReturn(Context.POWER_SERVICE);
        when(mockContext.getSystemService(Context.ALARM_SERVICE)).thenReturn(mMockAlarmManager);
        when(mockContext.getSystemServiceName(AlarmManager.class))
                .thenReturn(Context.ALARM_SERVICE);

        // Config is set to PBAP, PAN and GATT by default. Turn off PAN and PBAP.
        Config.setProfileEnabled(PanService.class, false);
        Config.setProfileEnabled(BluetoothPbapService.class, false);

        Config.init(mockContext);
        doEnable(0, true);
        doDisable(0, true);
    }

    /**
     * Test: Don't start GATT
     * Check whether the AdapterService quits gracefully
     */
    @Test
    public void testGattStartTimeout() {
        Assert.assertFalse(mAdapterService.getState() == BluetoothAdapter.STATE_ON);

        mAdapterService.enable(false);

        verifyStateChange(BluetoothAdapter.STATE_OFF, BluetoothAdapter.STATE_BLE_TURNING_ON, 1,
                CONTEXT_SWITCH_MS);

        // Start GATT
        verify(mMockContext, timeout(GATT_START_TIME_MS).times(1)).startService(any());
        mAdapterService.addProfile(mMockGattService);

        verifyStateChange(BluetoothAdapter.STATE_BLE_TURNING_ON,
                BluetoothAdapter.STATE_BLE_TURNING_OFF, 1,
                AdapterState.BLE_START_TIMEOUT_DELAY + CONTEXT_SWITCH_MS);

        // Stop GATT
        verify(mMockContext, timeout(AdapterState.BLE_STOP_TIMEOUT_DELAY + CONTEXT_SWITCH_MS)
                .times(2)).startService(any());

        verifyStateChange(BluetoothAdapter.STATE_BLE_TURNING_OFF, BluetoothAdapter.STATE_OFF, 1,
                NATIVE_DISABLE_MS);

        Assert.assertFalse(mAdapterService.getState() == BluetoothAdapter.STATE_ON);
    }

    /**
     * Test: Don't stop GATT
     * Check whether the AdapterService quits gracefully
     */
    @Test
    public void testGattStopTimeout() {
        doEnable(0, false);
        Assert.assertTrue(mAdapterService.getState() == BluetoothAdapter.STATE_ON);

        mAdapterService.disable();

        verifyStateChange(BluetoothAdapter.STATE_ON, BluetoothAdapter.STATE_TURNING_OFF, 1,
                CONTEXT_SWITCH_MS);

        // Stop PBAP and PAN services
        verify(mMockContext, timeout(ONE_SECOND_MS).times(5)).startService(any());
        mAdapterService.onProfileServiceStateChanged(mMockService, BluetoothAdapter.STATE_OFF);
        mAdapterService.onProfileServiceStateChanged(mMockService2, BluetoothAdapter.STATE_OFF);

        verifyStateChange(BluetoothAdapter.STATE_TURNING_OFF, BluetoothAdapter.STATE_BLE_ON, 1,
                CONTEXT_SWITCH_MS);

        mServiceBinder.onBrEdrDown(mAttributionSource);

        verifyStateChange(BluetoothAdapter.STATE_BLE_ON, BluetoothAdapter.STATE_BLE_TURNING_OFF, 1,
                CONTEXT_SWITCH_MS);

        // Stop GATT
        verify(mMockContext, timeout(ONE_SECOND_MS).times(6)).startService(any());

        verifyStateChange(BluetoothAdapter.STATE_BLE_TURNING_OFF, BluetoothAdapter.STATE_OFF, 1,
                AdapterState.BLE_STOP_TIMEOUT_DELAY + NATIVE_DISABLE_MS);

        Assert.assertFalse(mAdapterService.getState() == BluetoothAdapter.STATE_ON);
    }

    /**
     * Test: Don't start a classic profile
     * Check whether the AdapterService quits gracefully
     */
    @Test
    public void testProfileStartTimeout() {
        Assert.assertFalse(mAdapterService.getState() == BluetoothAdapter.STATE_ON);

        mAdapterService.enable(false);

        verifyStateChange(BluetoothAdapter.STATE_OFF, BluetoothAdapter.STATE_BLE_TURNING_ON, 1,
                CONTEXT_SWITCH_MS);

        // Start GATT
        verify(mMockContext, timeout(GATT_START_TIME_MS).times(1)).startService(any());
        mAdapterService.addProfile(mMockGattService);
        mAdapterService.onProfileServiceStateChanged(mMockGattService, BluetoothAdapter.STATE_ON);

        verifyStateChange(BluetoothAdapter.STATE_BLE_TURNING_ON, BluetoothAdapter.STATE_BLE_ON, 1,
                NATIVE_INIT_MS);

        mServiceBinder.onLeServiceUp(mAttributionSource);

        verifyStateChange(BluetoothAdapter.STATE_BLE_ON, BluetoothAdapter.STATE_TURNING_ON, 1,
                CONTEXT_SWITCH_MS);

        // Register Mock PBAP and PAN services
        verify(mMockContext, timeout(ONE_SECOND_MS).times(3)).startService(any());
        mAdapterService.addProfile(mMockService);
        mAdapterService.addProfile(mMockService2);
        mAdapterService.onProfileServiceStateChanged(mMockService, BluetoothAdapter.STATE_ON);

        verifyStateChange(BluetoothAdapter.STATE_TURNING_ON, BluetoothAdapter.STATE_TURNING_OFF, 1,
                AdapterState.BREDR_START_TIMEOUT_DELAY + CONTEXT_SWITCH_MS);

        // Stop PBAP and PAN services
        verify(mMockContext, timeout(ONE_SECOND_MS).times(5)).startService(any());
        mAdapterService.onProfileServiceStateChanged(mMockService, BluetoothAdapter.STATE_OFF);

        verifyStateChange(BluetoothAdapter.STATE_TURNING_OFF, BluetoothAdapter.STATE_BLE_ON, 1,
                CONTEXT_SWITCH_MS);
    }

    /**
     * Test: Don't stop a classic profile
     * Check whether the AdapterService quits gracefully
     */
    @Test
    public void testProfileStopTimeout() {
        doEnable(0, false);

        Assert.assertTrue(mAdapterService.getState() == BluetoothAdapter.STATE_ON);

        mAdapterService.disable();

        verifyStateChange(BluetoothAdapter.STATE_ON, BluetoothAdapter.STATE_TURNING_OFF, 1,
                CONTEXT_SWITCH_MS);

        // Stop PBAP and PAN services
        verify(mMockContext, timeout(ONE_SECOND_MS).times(5)).startService(any());
        mAdapterService.onProfileServiceStateChanged(mMockService, BluetoothAdapter.STATE_OFF);

        verifyStateChange(BluetoothAdapter.STATE_TURNING_OFF,
                BluetoothAdapter.STATE_BLE_TURNING_OFF, 1,
                AdapterState.BREDR_STOP_TIMEOUT_DELAY + CONTEXT_SWITCH_MS);

        // Stop GATT
        verify(mMockContext, timeout(ONE_SECOND_MS).times(6)).startService(any());
        mAdapterService.onProfileServiceStateChanged(mMockGattService, BluetoothAdapter.STATE_OFF);

        verifyStateChange(BluetoothAdapter.STATE_BLE_TURNING_OFF, BluetoothAdapter.STATE_OFF, 1,
                AdapterState.BLE_STOP_TIMEOUT_DELAY + NATIVE_DISABLE_MS);

        Assert.assertFalse(mAdapterService.getState() == BluetoothAdapter.STATE_ON);
    }

    /**
     * Test: Toggle snoop logging setting
     * Check whether the AdapterService restarts fully
     */
    @Test
    public void testSnoopLoggingChange() {
        BluetoothProperties.snoop_log_mode_values snoopSetting =
                BluetoothProperties.snoop_log_mode()
                .orElse(BluetoothProperties.snoop_log_mode_values.EMPTY);
        BluetoothProperties.snoop_log_mode(BluetoothProperties.snoop_log_mode_values.DISABLED);
        doEnable(0, false);

        Assert.assertTrue(mAdapterService.getState() == BluetoothAdapter.STATE_ON);

        Assert.assertFalse(
                SystemProperties.get(AdapterService.BLUETOOTH_BTSNOOP_LOG_MODE_PROPERTY,
                        "full").equals("full"));

        BluetoothProperties.snoop_log_mode(BluetoothProperties.snoop_log_mode_values.FULL);

        mAdapterService.disable();

        verifyStateChange(BluetoothAdapter.STATE_ON, BluetoothAdapter.STATE_TURNING_OFF, 1,
                CONTEXT_SWITCH_MS);

        // Stop PBAP and PAN services
        verify(mMockContext, timeout(ONE_SECOND_MS).times(5)).startService(any());
        mAdapterService.onProfileServiceStateChanged(mMockService, BluetoothAdapter.STATE_OFF);
        mAdapterService.onProfileServiceStateChanged(mMockService2, BluetoothAdapter.STATE_OFF);

        verifyStateChange(BluetoothAdapter.STATE_TURNING_OFF, BluetoothAdapter.STATE_BLE_ON, 1,
                CONTEXT_SWITCH_MS);

        // Don't call onBrEdrDown().  The Adapter should turn itself off.

        verifyStateChange(BluetoothAdapter.STATE_BLE_ON, BluetoothAdapter.STATE_BLE_TURNING_OFF, 1,
                CONTEXT_SWITCH_MS);

        // Stop GATT
        verify(mMockContext, timeout(ONE_SECOND_MS).times(6)).startService(any());

        mAdapterService.onProfileServiceStateChanged(mMockGattService, BluetoothAdapter.STATE_OFF);

        verifyStateChange(BluetoothAdapter.STATE_BLE_TURNING_OFF, BluetoothAdapter.STATE_OFF, 1,
                NATIVE_DISABLE_MS);

        Assert.assertFalse(mAdapterService.getState() == BluetoothAdapter.STATE_ON);

        // Restore earlier setting
        BluetoothProperties.snoop_log_mode(snoopSetting);
    }


    /**
     * Test: Obfuscate a null Bluetooth
     * Check if returned value from {@link AdapterService#obfuscateAddress(BluetoothDevice)} is
     * an empty array when device address is null
     */
    @Test
    public void testObfuscateBluetoothAddress_NullAddress() {
        Assert.assertArrayEquals(mAdapterService.obfuscateAddress(null), new byte[0]);
    }

    /**
     * Test: Obfuscate Bluetooth address when Bluetooth is disabled
     * Check whether the returned value meets expectation
     */
    @Test
    public void testObfuscateBluetoothAddress_BluetoothDisabled() {
        Assert.assertFalse(mAdapterService.getState() == BluetoothAdapter.STATE_ON);
        byte[] metricsSalt = getMetricsSalt(mAdapterConfig);
        Assert.assertNotNull(metricsSalt);
        BluetoothDevice device = TestUtils.getTestDevice(BluetoothAdapter.getDefaultAdapter(), 0);
        byte[] obfuscatedAddress = mAdapterService.obfuscateAddress(device);
        Assert.assertTrue(obfuscatedAddress.length > 0);
        Assert.assertFalse(isByteArrayAllZero(obfuscatedAddress));
        Assert.assertArrayEquals(obfuscateInJava(metricsSalt, device), obfuscatedAddress);
    }

    /**
     * Test: Obfuscate Bluetooth address when Bluetooth is enabled
     * Check whether the returned value meets expectation
     */
    @Test
    public void testObfuscateBluetoothAddress_BluetoothEnabled() {
        Assert.assertFalse(mAdapterService.getState() == BluetoothAdapter.STATE_ON);
        doEnable(0, false);
        Assert.assertTrue(mAdapterService.getState() == BluetoothAdapter.STATE_ON);
        byte[] metricsSalt = getMetricsSalt(mAdapterConfig);
        Assert.assertNotNull(metricsSalt);
        BluetoothDevice device = TestUtils.getTestDevice(BluetoothAdapter.getDefaultAdapter(), 0);
        byte[] obfuscatedAddress = mAdapterService.obfuscateAddress(device);
        Assert.assertTrue(obfuscatedAddress.length > 0);
        Assert.assertFalse(isByteArrayAllZero(obfuscatedAddress));
        Assert.assertArrayEquals(obfuscateInJava(metricsSalt, device), obfuscatedAddress);
    }

    /**
     * Test: Check if obfuscated Bluetooth address stays the same after toggling Bluetooth
     */
    @Test
    public void testObfuscateBluetoothAddress_PersistentBetweenToggle() {
        Assert.assertFalse(mAdapterService.getState() == BluetoothAdapter.STATE_ON);
        byte[] metricsSalt = getMetricsSalt(mAdapterConfig);
        Assert.assertNotNull(metricsSalt);
        BluetoothDevice device = TestUtils.getTestDevice(BluetoothAdapter.getDefaultAdapter(), 0);
        byte[] obfuscatedAddress1 = mAdapterService.obfuscateAddress(device);
        Assert.assertTrue(obfuscatedAddress1.length > 0);
        Assert.assertFalse(isByteArrayAllZero(obfuscatedAddress1));
        Assert.assertArrayEquals(obfuscateInJava(metricsSalt, device),
                obfuscatedAddress1);
        // Enable
        doEnable(0, false);
        Assert.assertTrue(mAdapterService.getState() == BluetoothAdapter.STATE_ON);
        byte[] obfuscatedAddress3 = mAdapterService.obfuscateAddress(device);
        Assert.assertTrue(obfuscatedAddress3.length > 0);
        Assert.assertFalse(isByteArrayAllZero(obfuscatedAddress3));
        Assert.assertArrayEquals(obfuscatedAddress3,
                obfuscatedAddress1);
        // Disable
        doDisable(0, false);
        Assert.assertFalse(mAdapterService.getState() == BluetoothAdapter.STATE_ON);
        byte[] obfuscatedAddress4 = mAdapterService.obfuscateAddress(device);
        Assert.assertTrue(obfuscatedAddress4.length > 0);
        Assert.assertFalse(isByteArrayAllZero(obfuscatedAddress4));
        Assert.assertArrayEquals(obfuscatedAddress4,
                obfuscatedAddress1);
    }

    @Test
    public void testAddressConsolidation() {
        // Create device properties
        RemoteDevices remoteDevices = mAdapterService.getRemoteDevices();
        remoteDevices.addDeviceProperties(Utils.getBytesFromAddress((TEST_BT_ADDR_1)));
        String identityAddress = mAdapterService.getIdentityAddress(TEST_BT_ADDR_1);
        Assert.assertEquals(identityAddress, TEST_BT_ADDR_1);

        // Trigger address consolidate callback
        remoteDevices.addressConsolidateCallback(Utils.getBytesFromAddress(TEST_BT_ADDR_1),
                Utils.getBytesFromAddress(TEST_BT_ADDR_2));

        // Verify we can get correct identity address
        identityAddress = mAdapterService.getIdentityAddress(TEST_BT_ADDR_1);
        Assert.assertEquals(identityAddress, TEST_BT_ADDR_2);
    }

    public static byte[] getMetricsSalt(HashMap<String, HashMap<String, String>> adapterConfig) {
        HashMap<String, String> metricsSection = adapterConfig.get("Metrics");
        if (metricsSection == null) {
            Log.e(TAG, "Metrics section is null: " + adapterConfig.toString());
            return null;
        }
        String saltString = metricsSection.get("Salt256Bit");
        if (saltString == null) {
            Log.e(TAG, "Salt256Bit is null: " + metricsSection.toString());
            return null;
        }
        byte[] metricsSalt = HexEncoding.decode(saltString, false /* allowSingleChar */);
        if (metricsSalt.length != 32) {
            Log.e(TAG, "Salt length is not 32 bit, but is " + metricsSalt.length);
            return null;
        }
        return metricsSalt;
    }

    public static byte[] obfuscateInJava(byte[] key, BluetoothDevice device) {
        String algorithm = "HmacSHA256";
        try {
            Mac hmac256 = Mac.getInstance(algorithm);
            hmac256.init(new SecretKeySpec(key, algorithm));
            return hmac256.doFinal(Utils.getByteAddress(device));
        } catch (NoSuchAlgorithmException | IllegalStateException | InvalidKeyException exp) {
            exp.printStackTrace();
            return null;
        }
    }

    public static boolean isByteArrayAllZero(byte[] byteArray) {
        for (byte i : byteArray) {
            if (i != 0) {
                return false;
            }
        }
        return true;
    }

    /**
     * Test: Get id for null address
     * Check if returned value from {@link AdapterService#getMetricId(BluetoothDevice)} is
     * 0 when device address is null
     */
    @Test
    public void testGetMetricId_NullAddress() {
        Assert.assertEquals(mAdapterService.getMetricId(null), 0);
    }

    /**
     * Test: Get id when Bluetooth is disabled
     * Check whether the returned value meets expectation
     */
    @Test
    public void testGetMetricId_BluetoothDisabled() {
        Assert.assertFalse(mAdapterService.getState() == BluetoothAdapter.STATE_ON);
        BluetoothDevice device = TestUtils.getTestDevice(BluetoothAdapter.getDefaultAdapter(), 0);
        int id = mAdapterService.getMetricId(device);
        Assert.assertTrue(id > 0);
    }

    /**
     * Test: Get id when Bluetooth is enabled
     * Check whether the returned value meets expectation
     */
    @Test
    public void testGetMetricId_BluetoothEnabled() {
        Assert.assertFalse(mAdapterService.getState() == BluetoothAdapter.STATE_ON);
        doEnable(0, false);
        Assert.assertTrue(mAdapterService.getState() == BluetoothAdapter.STATE_ON);
        BluetoothDevice device = TestUtils.getTestDevice(BluetoothAdapter.getDefaultAdapter(), 0);
        int id = mAdapterService.getMetricId(device);
        Assert.assertTrue(id > 0);
    }

    /**
     * Test: Check if id gotten stays the same after toggling Bluetooth
     */
    @Test
    public void testGetMetricId_PersistentBetweenToggle() {
        Assert.assertFalse(mAdapterService.getState() == BluetoothAdapter.STATE_ON);
        BluetoothDevice device = TestUtils.getTestDevice(BluetoothAdapter.getDefaultAdapter(), 0);
        int id1 = mAdapterService.getMetricId(device);
        Assert.assertTrue(id1 > 0);

        // Enable
        doEnable(0, false);
        Assert.assertTrue(mAdapterService.getState() == BluetoothAdapter.STATE_ON);
        int id2 = mAdapterService.getMetricId(device);
        Assert.assertEquals(id2, id1);

        // Disable
        doDisable(0, false);
        Assert.assertFalse(mAdapterService.getState() == BluetoothAdapter.STATE_ON);
        int id3 = mAdapterService.getMetricId(device);
        Assert.assertEquals(id3, id1);
    }

    @Test
    public void testDump_doesNotCrash() {
        FileDescriptor fd = new FileDescriptor();
        PrintWriter writer = mock(PrintWriter.class);

        mAdapterService.dump(fd, writer, new String[]{});
        mAdapterService.dump(fd, writer, new String[]{"set-test-mode", "enabled"});
        mAdapterService.dump(fd, writer, new String[]{"--proto-bin"});
        mAdapterService.dump(fd, writer, new String[]{"random", "arguments"});
    }
}
