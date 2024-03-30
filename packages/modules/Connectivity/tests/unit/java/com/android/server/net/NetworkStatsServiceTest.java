/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.server.net;

import static android.Manifest.permission.READ_NETWORK_USAGE_HISTORY;
import static android.Manifest.permission.UPDATE_DEVICE_STATS;
import static android.app.usage.NetworkStatsManager.PREFIX_DEV;
import static android.content.Intent.ACTION_UID_REMOVED;
import static android.content.Intent.EXTRA_UID;
import static android.content.pm.PackageManager.PERMISSION_DENIED;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static android.net.ConnectivityManager.TYPE_MOBILE;
import static android.net.ConnectivityManager.TYPE_WIFI;
import static android.net.NetworkIdentity.OEM_PAID;
import static android.net.NetworkIdentity.OEM_PRIVATE;
import static android.net.NetworkStack.PERMISSION_MAINLINE_NETWORK_STACK;
import static android.net.NetworkStats.DEFAULT_NETWORK_ALL;
import static android.net.NetworkStats.DEFAULT_NETWORK_NO;
import static android.net.NetworkStats.DEFAULT_NETWORK_YES;
import static android.net.NetworkStats.IFACE_ALL;
import static android.net.NetworkStats.INTERFACES_ALL;
import static android.net.NetworkStats.METERED_ALL;
import static android.net.NetworkStats.METERED_NO;
import static android.net.NetworkStats.METERED_YES;
import static android.net.NetworkStats.ROAMING_ALL;
import static android.net.NetworkStats.ROAMING_NO;
import static android.net.NetworkStats.ROAMING_YES;
import static android.net.NetworkStats.SET_ALL;
import static android.net.NetworkStats.SET_DEFAULT;
import static android.net.NetworkStats.SET_FOREGROUND;
import static android.net.NetworkStats.TAG_ALL;
import static android.net.NetworkStats.TAG_NONE;
import static android.net.NetworkStats.UID_ALL;
import static android.net.NetworkStatsHistory.FIELD_ALL;
import static android.net.NetworkTemplate.MATCH_MOBILE_WILDCARD;
import static android.net.NetworkTemplate.NETWORK_TYPE_ALL;
import static android.net.NetworkTemplate.OEM_MANAGED_NO;
import static android.net.NetworkTemplate.OEM_MANAGED_YES;
import static android.net.NetworkTemplate.buildTemplateMobileAll;
import static android.net.NetworkTemplate.buildTemplateMobileWithRatType;
import static android.net.NetworkTemplate.buildTemplateWifi;
import static android.net.NetworkTemplate.buildTemplateWifiWildcard;
import static android.net.TrafficStats.MB_IN_BYTES;
import static android.net.TrafficStats.UID_REMOVED;
import static android.net.TrafficStats.UID_TETHERING;
import static android.net.netstats.NetworkStatsDataMigrationUtils.PREFIX_UID;
import static android.net.netstats.NetworkStatsDataMigrationUtils.PREFIX_UID_TAG;
import static android.net.netstats.NetworkStatsDataMigrationUtils.PREFIX_XT;
import static android.text.format.DateUtils.DAY_IN_MILLIS;
import static android.text.format.DateUtils.HOUR_IN_MILLIS;
import static android.text.format.DateUtils.MINUTE_IN_MILLIS;
import static android.text.format.DateUtils.WEEK_IN_MILLIS;

import static com.android.net.module.util.NetworkStatsUtils.SUBSCRIBER_ID_MATCH_RULE_EXACT;
import static com.android.server.net.NetworkStatsService.ACTION_NETWORK_STATS_POLL;
import static com.android.server.net.NetworkStatsService.NETSTATS_IMPORT_ATTEMPTS_COUNTER_NAME;
import static com.android.server.net.NetworkStatsService.NETSTATS_IMPORT_FALLBACKS_COUNTER_NAME;
import static com.android.server.net.NetworkStatsService.NETSTATS_IMPORT_SUCCESSES_COUNTER_NAME;
import static com.android.testutils.DevSdkIgnoreRuleKt.SC_V2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.annotation.NonNull;
import android.app.AlarmManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.net.ConnectivityResources;
import android.net.DataUsageRequest;
import android.net.INetd;
import android.net.INetworkStatsSession;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkIdentity;
import android.net.NetworkStateSnapshot;
import android.net.NetworkStats;
import android.net.NetworkStatsCollection;
import android.net.NetworkStatsHistory;
import android.net.NetworkTemplate;
import android.net.TelephonyNetworkSpecifier;
import android.net.TetherStatsParcel;
import android.net.TetheringManager;
import android.net.UnderlyingNetworkInfo;
import android.net.netstats.provider.INetworkStatsProviderCallback;
import android.net.wifi.WifiInfo;
import android.os.DropBoxManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.SimpleClock;
import android.provider.Settings;
import android.system.ErrnoException;
import android.telephony.TelephonyManager;
import android.util.ArrayMap;

import androidx.annotation.Nullable;
import androidx.test.InstrumentationRegistry;
import androidx.test.filters.SmallTest;

import com.android.connectivity.resources.R;
import com.android.internal.util.FileRotator;
import com.android.internal.util.test.BroadcastInterceptingContext;
import com.android.net.module.util.IBpfMap;
import com.android.net.module.util.LocationPermissionChecker;
import com.android.net.module.util.Struct.U32;
import com.android.net.module.util.Struct.U8;
import com.android.server.net.NetworkStatsService.AlertObserver;
import com.android.server.net.NetworkStatsService.NetworkStatsSettings;
import com.android.server.net.NetworkStatsService.NetworkStatsSettings.Config;
import com.android.testutils.DevSdkIgnoreRule;
import com.android.testutils.DevSdkIgnoreRunner;
import com.android.testutils.HandlerUtils;
import com.android.testutils.TestBpfMap;
import com.android.testutils.TestableNetworkStatsProviderBinder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

import libcore.testing.io.TestIoUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Tests for {@link NetworkStatsService}.
 *
 * TODO: This test used to be really brittle because it used Easymock - it uses Mockito now, but
 * still uses the Easymock structure, which could be simplified.
 */
@RunWith(DevSdkIgnoreRunner.class)
@SmallTest
// NetworkStatsService is not updatable before T, so tests do not need to be backwards compatible
@DevSdkIgnoreRule.IgnoreUpTo(SC_V2)
public class NetworkStatsServiceTest extends NetworkStatsBaseTest {
    private static final String TAG = "NetworkStatsServiceTest";

    private static final long TEST_START = 1194220800000L;

    private static final String IMSI_1 = "310004";
    private static final String IMSI_2 = "310260";
    private static final String TEST_WIFI_NETWORK_KEY = "WifiNetworkKey";

    private static NetworkTemplate sTemplateWifi = buildTemplateWifi(TEST_WIFI_NETWORK_KEY);
    private static NetworkTemplate sTemplateCarrierWifi1 =
            buildTemplateWifi(NetworkTemplate.WIFI_NETWORKID_ALL, IMSI_1);
    private static NetworkTemplate sTemplateImsi1 = buildTemplateMobileAll(IMSI_1);
    private static NetworkTemplate sTemplateImsi2 = buildTemplateMobileAll(IMSI_2);

    private static final Network WIFI_NETWORK =  new Network(100);
    private static final Network MOBILE_NETWORK =  new Network(101);
    private static final Network VPN_NETWORK = new Network(102);

    private static final Network[] NETWORKS_WIFI = new Network[]{ WIFI_NETWORK };
    private static final Network[] NETWORKS_MOBILE = new Network[]{ MOBILE_NETWORK };

    private static final long WAIT_TIMEOUT = 2 * 1000;  // 2 secs
    private static final int INVALID_TYPE = -1;

    private long mElapsedRealtime;

    private File mStatsDir;
    private File mLegacyStatsDir;
    private MockContext mServiceContext;
    private @Mock TelephonyManager mTelephonyManager;
    private static @Mock WifiInfo sWifiInfo;
    private @Mock INetd mNetd;
    private @Mock TetheringManager mTetheringManager;
    private @Mock NetworkStatsFactory mStatsFactory;
    private @Mock NetworkStatsSettings mSettings;
    private @Mock IBinder mUsageCallbackBinder;
    private TestableUsageCallback mUsageCallback;
    private @Mock AlarmManager mAlarmManager;
    @Mock
    private NetworkStatsSubscriptionsMonitor mNetworkStatsSubscriptionsMonitor;
    private @Mock BpfInterfaceMapUpdater mBpfInterfaceMapUpdater;
    private HandlerThread mHandlerThread;
    @Mock
    private LocationPermissionChecker mLocationPermissionChecker;
    private TestBpfMap<U32, U8> mUidCounterSetMap = spy(new TestBpfMap<>(U32.class, U8.class));

    private TestBpfMap<CookieTagMapKey, CookieTagMapValue> mCookieTagMap = new TestBpfMap<>(
            CookieTagMapKey.class, CookieTagMapValue.class);
    private TestBpfMap<StatsMapKey, StatsMapValue> mStatsMapA = new TestBpfMap<>(StatsMapKey.class,
            StatsMapValue.class);
    private TestBpfMap<StatsMapKey, StatsMapValue> mStatsMapB = new TestBpfMap<>(StatsMapKey.class,
            StatsMapValue.class);
    private TestBpfMap<UidStatsMapKey, StatsMapValue> mAppUidStatsMap = new TestBpfMap<>(
            UidStatsMapKey.class, StatsMapValue.class);

    private NetworkStatsService mService;
    private INetworkStatsSession mSession;
    private AlertObserver mAlertObserver;
    private ContentObserver mContentObserver;
    private Handler mHandler;
    private TetheringManager.TetheringEventCallback mTetheringEventCallback;
    private Map<String, NetworkStatsCollection> mPlatformNetworkStatsCollection =
            new ArrayMap<String, NetworkStatsCollection>();
    private boolean mStoreFilesInApexData = false;
    private int mImportLegacyTargetAttempts = 0;
    private @Mock PersistentInt mImportLegacyAttemptsCounter;
    private @Mock PersistentInt mImportLegacySuccessesCounter;
    private @Mock PersistentInt mImportLegacyFallbacksCounter;
    private @Mock Resources mResources;
    private Boolean mIsDebuggable;

    private class MockContext extends BroadcastInterceptingContext {
        private final Context mBaseContext;

        MockContext(Context base) {
            super(base);
            mBaseContext = base;
        }

        @Override
        public Object getSystemService(String name) {
            if (Context.TELEPHONY_SERVICE.equals(name)) return mTelephonyManager;
            if (Context.TETHERING_SERVICE.equals(name)) return mTetheringManager;
            return mBaseContext.getSystemService(name);
        }

        @Override
        public void enforceCallingOrSelfPermission(String permission, @Nullable String message) {
            if (checkCallingOrSelfPermission(permission) != PERMISSION_GRANTED) {
                throw new SecurityException("Test does not have mocked permission " + permission);
            }
        }

        @Override
        public int checkCallingOrSelfPermission(String permission) {
            switch (permission) {
                case PERMISSION_MAINLINE_NETWORK_STACK:
                case READ_NETWORK_USAGE_HISTORY:
                case UPDATE_DEVICE_STATS:
                    return PERMISSION_GRANTED;
                default:
                    return PERMISSION_DENIED;
            }

        }
    }

    private final Clock mClock = new SimpleClock(ZoneOffset.UTC) {
        @Override
        public long millis() {
            return currentTimeMillis();
        }
    };

    @NonNull
    private static TetherStatsParcel buildTetherStatsParcel(String iface, long rxBytes,
            long rxPackets, long txBytes, long txPackets, int ifIndex) {
        TetherStatsParcel parcel = new TetherStatsParcel();
        parcel.iface = iface;
        parcel.rxBytes = rxBytes;
        parcel.rxPackets = rxPackets;
        parcel.txBytes = txBytes;
        parcel.txPackets = txPackets;
        parcel.ifIndex = ifIndex;
        return parcel;
    }

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        // Setup mock resources.
        final Context mockResContext = mock(Context.class);
        doReturn(mResources).when(mockResContext).getResources();
        ConnectivityResources.setResourcesContextForTest(mockResContext);

        final Context context = InstrumentationRegistry.getContext();
        mServiceContext = new MockContext(context);
        when(mLocationPermissionChecker.checkCallersLocationPermission(
                any(), any(), anyInt(), anyBoolean(), any())).thenReturn(true);
        when(sWifiInfo.getNetworkKey()).thenReturn(TEST_WIFI_NETWORK_KEY);
        mStatsDir = TestIoUtils.createTemporaryDirectory(getClass().getSimpleName());
        mLegacyStatsDir = TestIoUtils.createTemporaryDirectory(
                getClass().getSimpleName() + "-legacy");

        PowerManager powerManager = (PowerManager) mServiceContext.getSystemService(
                Context.POWER_SERVICE);
        PowerManager.WakeLock wakeLock =
                powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);

        mHandlerThread = new HandlerThread("HandlerThread");
        final NetworkStatsService.Dependencies deps = makeDependencies();
        mService = new NetworkStatsService(mServiceContext, mNetd, mAlarmManager, wakeLock,
                mClock, mSettings, mStatsFactory, new NetworkStatsObservers(), deps);

        mElapsedRealtime = 0L;

        expectDefaultSettings();
        expectNetworkStatsUidDetail(buildEmptyStats());
        expectSystemReady();
        mService.systemReady();
        // Verify that system ready fetches realtime stats
        verify(mStatsFactory).readNetworkStatsDetail(UID_ALL, INTERFACES_ALL, TAG_ALL);
        // Wait for posting onChange() event to handler thread and verify that when system ready,
        // start monitoring data usage per RAT type because the settings value is mock as false
        // by default in expectSettings().
        waitForIdle();
        verify(mNetworkStatsSubscriptionsMonitor).start();
        reset(mNetworkStatsSubscriptionsMonitor);

        doReturn(TelephonyManager.CARRIER_PRIVILEGE_STATUS_HAS_ACCESS).when(mTelephonyManager)
                .checkCarrierPrivilegesForPackageAnyPhone(anyString());

        mSession = mService.openSession();
        assertNotNull("openSession() failed", mSession);

        // Catch AlertObserver during systemReady().
        final ArgumentCaptor<AlertObserver> alertObserver =
                ArgumentCaptor.forClass(AlertObserver.class);
        verify(mNetd).registerUnsolicitedEventListener(alertObserver.capture());
        mAlertObserver = alertObserver.getValue();

        // Catch TetheringEventCallback during systemReady().
        ArgumentCaptor<TetheringManager.TetheringEventCallback> tetheringEventCbCaptor =
                ArgumentCaptor.forClass(TetheringManager.TetheringEventCallback.class);
        verify(mTetheringManager).registerTetheringEventCallback(
                any(), tetheringEventCbCaptor.capture());
        mTetheringEventCallback = tetheringEventCbCaptor.getValue();

        mUsageCallback = new TestableUsageCallback(mUsageCallbackBinder);
    }

    @NonNull
    private NetworkStatsService.Dependencies makeDependencies() {
        return new NetworkStatsService.Dependencies() {
            @Override
            public File getLegacyStatsDir() {
                return mLegacyStatsDir;
            }

            @Override
            public File getOrCreateStatsDir() {
                return mStatsDir;
            }

            @Override
            public boolean getStoreFilesInApexData() {
                return mStoreFilesInApexData;
            }

            @Override
            public int getImportLegacyTargetAttempts() {
                return mImportLegacyTargetAttempts;
            }

            @Override
            public PersistentInt createPersistentCounter(@androidx.annotation.NonNull Path dir,
                    @androidx.annotation.NonNull String name) throws IOException {
                switch (name) {
                    case NETSTATS_IMPORT_ATTEMPTS_COUNTER_NAME:
                        return mImportLegacyAttemptsCounter;
                    case NETSTATS_IMPORT_SUCCESSES_COUNTER_NAME:
                        return mImportLegacySuccessesCounter;
                    case NETSTATS_IMPORT_FALLBACKS_COUNTER_NAME:
                        return mImportLegacyFallbacksCounter;
                    default:
                        throw new IllegalArgumentException("Unknown counter name: " + name);
                }
            }

            @Override
            public NetworkStatsCollection readPlatformCollection(
                    @NonNull String prefix, long bucketDuration) {
                return mPlatformNetworkStatsCollection.get(prefix);
            }

            @Override
            public HandlerThread makeHandlerThread() {
                return mHandlerThread;
            }

            @Override
            public NetworkStatsSubscriptionsMonitor makeSubscriptionsMonitor(
                    @NonNull Context context, @NonNull Executor executor,
                    @NonNull NetworkStatsService service) {

                return mNetworkStatsSubscriptionsMonitor;
            }

            @Override
            public ContentObserver makeContentObserver(Handler handler,
                    NetworkStatsSettings settings, NetworkStatsSubscriptionsMonitor monitor) {
                mHandler = handler;
                return mContentObserver = super.makeContentObserver(handler, settings, monitor);
            }

            @Override
            public LocationPermissionChecker makeLocationPermissionChecker(final Context context) {
                return mLocationPermissionChecker;
            }

            @Override
            public BpfInterfaceMapUpdater makeBpfInterfaceMapUpdater(
                    @NonNull Context ctx, @NonNull Handler handler) {
                return mBpfInterfaceMapUpdater;
            }

            @Override
            public IBpfMap<U32, U8> getUidCounterSetMap() {
                return mUidCounterSetMap;
            }

            @Override
            public IBpfMap<CookieTagMapKey, CookieTagMapValue> getCookieTagMap() {
                return mCookieTagMap;
            }

            @Override
            public IBpfMap<StatsMapKey, StatsMapValue> getStatsMapA() {
                return mStatsMapA;
            }

            @Override
            public IBpfMap<StatsMapKey, StatsMapValue> getStatsMapB() {
                return mStatsMapB;
            }

            @Override
            public IBpfMap<UidStatsMapKey, StatsMapValue> getAppUidStatsMap() {
                return mAppUidStatsMap;
            }

            @Override
            public boolean isDebuggable() {
                return mIsDebuggable == Boolean.TRUE;
            }
        };
    }

    @After
    public void tearDown() throws Exception {
        mServiceContext = null;
        mStatsDir = null;

        mNetd = null;
        mSettings = null;

        mSession.close();
        mService = null;

        mHandlerThread.quitSafely();
    }

    private void initWifiStats(NetworkStateSnapshot snapshot) throws Exception {
        // pretend that wifi network comes online; service should ask about full
        // network state, and poll any existing interfaces before updating.
        expectDefaultSettings();
        NetworkStateSnapshot[] states = new NetworkStateSnapshot[] {snapshot};
        expectNetworkStatsSummary(buildEmptyStats());
        expectNetworkStatsUidDetail(buildEmptyStats());

        mService.notifyNetworkStatus(NETWORKS_WIFI, states, getActiveIface(states),
                new UnderlyingNetworkInfo[0]);
    }

    private void incrementWifiStats(long durationMillis, String iface,
            long rxb, long rxp, long txb, long txp) throws Exception {
        incrementCurrentTime(durationMillis);
        expectDefaultSettings();
        expectNetworkStatsSummary(new NetworkStats(getElapsedRealtime(), 1)
                .insertEntry(iface, rxb, rxp, txb, txp));
        expectNetworkStatsUidDetail(buildEmptyStats());
        forcePollAndWaitForIdle();
    }

    @Test
    public void testNetworkStatsCarrierWifi() throws Exception {
        initWifiStats(buildWifiState(true, TEST_IFACE, IMSI_1));
        // verify service has empty history for carrier merged wifi and non-carrier wifi
        assertNetworkTotal(sTemplateCarrierWifi1, 0L, 0L, 0L, 0L, 0);
        assertNetworkTotal(sTemplateWifi, 0L, 0L, 0L, 0L, 0);

        // modify some number on wifi, and trigger poll event
        incrementWifiStats(HOUR_IN_MILLIS, TEST_IFACE, 1024L, 1L, 2048L, 2L);

        // verify service recorded history
        assertNetworkTotal(sTemplateCarrierWifi1, 1024L, 1L, 2048L, 2L, 0);

        // verify service recorded history for wifi with WiFi Network Key filter
        assertNetworkTotal(sTemplateWifi,  1024L, 1L, 2048L, 2L, 0);


        // and bump forward again, with counters going higher. this is
        // important, since polling should correctly subtract last snapshot.
        incrementWifiStats(DAY_IN_MILLIS, TEST_IFACE, 4096L, 4L, 8192L, 8L);

        // verify service recorded history
        assertNetworkTotal(sTemplateCarrierWifi1, 4096L, 4L, 8192L, 8L, 0);
        // verify service recorded history for wifi with WiFi Network Key filter
        assertNetworkTotal(sTemplateWifi, 4096L, 4L, 8192L, 8L, 0);
    }

    @Test
    public void testNetworkStatsNonCarrierWifi() throws Exception {
        initWifiStats(buildWifiState());

        // verify service has empty history for wifi
        assertNetworkTotal(sTemplateWifi, 0L, 0L, 0L, 0L, 0);
        // verify service has empty history for carrier merged wifi
        assertNetworkTotal(sTemplateCarrierWifi1, 0L, 0L, 0L, 0L, 0);

        // modify some number on wifi, and trigger poll event
        incrementWifiStats(HOUR_IN_MILLIS, TEST_IFACE, 1024L, 1L, 2048L, 2L);

        // verify service recorded history
        assertNetworkTotal(sTemplateWifi, 1024L, 1L, 2048L, 2L, 0);
        // verify service has empty history for carrier wifi since current network is non carrier
        // wifi
        assertNetworkTotal(sTemplateCarrierWifi1, 0L, 0L, 0L, 0L, 0);

        // and bump forward again, with counters going higher. this is
        // important, since polling should correctly subtract last snapshot.
        incrementWifiStats(DAY_IN_MILLIS, TEST_IFACE, 4096L, 4L, 8192L, 8L);

        // verify service recorded history
        assertNetworkTotal(sTemplateWifi, 4096L, 4L, 8192L, 8L, 0);
        // verify service has empty history for carrier wifi since current network is non carrier
        // wifi
        assertNetworkTotal(sTemplateCarrierWifi1, 0L, 0L, 0L, 0L, 0);
    }

    @Test
    public void testStatsRebootPersist() throws Exception {
        assertStatsFilesExist(false);

        // pretend that wifi network comes online; service should ask about full
        // network state, and poll any existing interfaces before updating.
        expectDefaultSettings();
        NetworkStateSnapshot[] states = new NetworkStateSnapshot[] {buildWifiState()};
        expectNetworkStatsSummary(buildEmptyStats());
        expectNetworkStatsUidDetail(buildEmptyStats());

        mService.notifyNetworkStatus(NETWORKS_WIFI, states, getActiveIface(states),
                new UnderlyingNetworkInfo[0]);

        // verify service has empty history for wifi
        assertNetworkTotal(sTemplateWifi, 0L, 0L, 0L, 0L, 0);


        // modify some number on wifi, and trigger poll event
        incrementCurrentTime(HOUR_IN_MILLIS);
        expectDefaultSettings();
        expectNetworkStatsSummary(new NetworkStats(getElapsedRealtime(), 1)
                .insertEntry(TEST_IFACE, 1024L, 8L, 2048L, 16L));
        expectNetworkStatsUidDetail(new NetworkStats(getElapsedRealtime(), 2)
                .insertEntry(TEST_IFACE, UID_RED, SET_DEFAULT, TAG_NONE, 512L, 4L, 256L, 2L, 0L)
                .insertEntry(TEST_IFACE, UID_RED, SET_DEFAULT, 0xFAAD, 256L, 2L, 128L, 1L, 0L)
                .insertEntry(TEST_IFACE, UID_RED, SET_FOREGROUND, TAG_NONE, 512L, 4L, 256L, 2L, 0L)
                .insertEntry(TEST_IFACE, UID_RED, SET_FOREGROUND, 0xFAAD, 256L, 2L, 128L, 1L, 0L)
                .insertEntry(TEST_IFACE, UID_BLUE, SET_DEFAULT, TAG_NONE, 128L, 1L, 128L, 1L, 0L));
        mService.noteUidForeground(UID_RED, false);
        verify(mUidCounterSetMap, never()).deleteEntry(any());
        mService.incrementOperationCount(UID_RED, 0xFAAD, 4);
        mService.noteUidForeground(UID_RED, true);
        verify(mUidCounterSetMap).updateEntry(
                eq(new U32(UID_RED)), eq(new U8((short) SET_FOREGROUND)));
        mService.incrementOperationCount(UID_RED, 0xFAAD, 6);

        forcePollAndWaitForIdle();

        // verify service recorded history
        assertNetworkTotal(sTemplateWifi, 1024L, 8L, 2048L, 16L, 0);
        assertUidTotal(sTemplateWifi, UID_RED, 1024L, 8L, 512L, 4L, 10);
        assertUidTotal(sTemplateWifi, UID_RED, SET_DEFAULT, METERED_NO, ROAMING_NO,
                DEFAULT_NETWORK_YES, 512L, 4L, 256L, 2L, 4);
        assertUidTotal(sTemplateWifi, UID_RED, SET_FOREGROUND, METERED_NO, ROAMING_NO,
                DEFAULT_NETWORK_YES, 512L, 4L, 256L, 2L, 6);
        assertUidTotal(sTemplateWifi, UID_BLUE, 128L, 1L, 128L, 1L, 0);


        // graceful shutdown system, which should trigger persist of stats, and
        // clear any values in memory.
        expectDefaultSettings();
        mServiceContext.sendBroadcast(new Intent(Intent.ACTION_SHUTDOWN));
        assertStatsFilesExist(true);

        // boot through serviceReady() again
        expectDefaultSettings();
        expectNetworkStatsUidDetail(buildEmptyStats());
        expectSystemReady();

        mService.systemReady();

        // after systemReady(), we should have historical stats loaded again
        assertNetworkTotal(sTemplateWifi, 1024L, 8L, 2048L, 16L, 0);
        assertUidTotal(sTemplateWifi, UID_RED, 1024L, 8L, 512L, 4L, 10);
        assertUidTotal(sTemplateWifi, UID_RED, SET_DEFAULT, METERED_NO, ROAMING_NO,
                DEFAULT_NETWORK_YES, 512L, 4L, 256L, 2L, 4);
        assertUidTotal(sTemplateWifi, UID_RED, SET_FOREGROUND, METERED_NO, ROAMING_NO,
                DEFAULT_NETWORK_YES, 512L, 4L, 256L, 2L, 6);
        assertUidTotal(sTemplateWifi, UID_BLUE, 128L, 1L, 128L, 1L, 0);

    }

    // TODO: simulate reboot to test bucket resize
    @Test
    @Ignore
    public void testStatsBucketResize() throws Exception {
        NetworkStatsHistory history = null;

        assertStatsFilesExist(false);

        // pretend that wifi network comes online; service should ask about full
        // network state, and poll any existing interfaces before updating.
        expectSettings(0L, HOUR_IN_MILLIS, WEEK_IN_MILLIS);
        NetworkStateSnapshot[] states = new NetworkStateSnapshot[] {buildWifiState()};
        expectNetworkStatsSummary(buildEmptyStats());
        expectNetworkStatsUidDetail(buildEmptyStats());

        mService.notifyNetworkStatus(NETWORKS_WIFI, states, getActiveIface(states),
                new UnderlyingNetworkInfo[0]);

        // modify some number on wifi, and trigger poll event
        incrementCurrentTime(2 * HOUR_IN_MILLIS);
        expectSettings(0L, HOUR_IN_MILLIS, WEEK_IN_MILLIS);
        expectNetworkStatsSummary(new NetworkStats(getElapsedRealtime(), 1)
                .insertEntry(TEST_IFACE, 512L, 4L, 512L, 4L));
        expectNetworkStatsUidDetail(buildEmptyStats());
        forcePollAndWaitForIdle();

        // verify service recorded history
        history = mSession.getHistoryForNetwork(sTemplateWifi, FIELD_ALL);
        assertValues(history, Long.MIN_VALUE, Long.MAX_VALUE, 512L, 4L, 512L, 4L, 0);
        assertEquals(HOUR_IN_MILLIS, history.getBucketDuration());
        assertEquals(2, history.size());


        // now change bucket duration setting and trigger another poll with
        // exact same values, which should resize existing buckets.
        expectSettings(0L, 30 * MINUTE_IN_MILLIS, WEEK_IN_MILLIS);
        expectNetworkStatsSummary(buildEmptyStats());
        expectNetworkStatsUidDetail(buildEmptyStats());
        forcePollAndWaitForIdle();

        // verify identical stats, but spread across 4 buckets now
        history = mSession.getHistoryForNetwork(sTemplateWifi, FIELD_ALL);
        assertValues(history, Long.MIN_VALUE, Long.MAX_VALUE, 512L, 4L, 512L, 4L, 0);
        assertEquals(30 * MINUTE_IN_MILLIS, history.getBucketDuration());
        assertEquals(4, history.size());

    }

    @Test
    public void testUidStatsAcrossNetworks() throws Exception {
        // pretend first mobile network comes online
        expectDefaultSettings();
        NetworkStateSnapshot[] states = new NetworkStateSnapshot[] {buildMobileState(IMSI_1)};
        expectNetworkStatsSummary(buildEmptyStats());
        expectNetworkStatsUidDetail(buildEmptyStats());

        mService.notifyNetworkStatus(NETWORKS_MOBILE, states, getActiveIface(states),
                new UnderlyingNetworkInfo[0]);

        // create some traffic on first network
        incrementCurrentTime(HOUR_IN_MILLIS);
        expectDefaultSettings();
        expectNetworkStatsSummary(new NetworkStats(getElapsedRealtime(), 1)
                .insertEntry(TEST_IFACE, 2048L, 16L, 512L, 4L));
        expectNetworkStatsUidDetail(new NetworkStats(getElapsedRealtime(), 3)
                .insertEntry(TEST_IFACE, UID_RED, SET_DEFAULT, TAG_NONE, 1536L, 12L, 512L, 4L, 0L)
                .insertEntry(TEST_IFACE, UID_RED, SET_DEFAULT, 0xF00D, 512L, 4L, 512L, 4L, 0L)
                .insertEntry(TEST_IFACE, UID_BLUE, SET_DEFAULT, TAG_NONE, 512L, 4L, 0L, 0L, 0L));
        mService.incrementOperationCount(UID_RED, 0xF00D, 10);

        forcePollAndWaitForIdle();

        // verify service recorded history
        assertNetworkTotal(sTemplateImsi1, 2048L, 16L, 512L, 4L, 0);
        assertNetworkTotal(sTemplateWifi, 0L, 0L, 0L, 0L, 0);
        assertUidTotal(sTemplateImsi1, UID_RED, 1536L, 12L, 512L, 4L, 10);
        assertUidTotal(sTemplateImsi1, UID_BLUE, 512L, 4L, 0L, 0L, 0);


        // now switch networks; this also tests that we're okay with interfaces
        // disappearing, to verify we don't count backwards.
        incrementCurrentTime(HOUR_IN_MILLIS);
        expectDefaultSettings();
        states = new NetworkStateSnapshot[] {buildMobileState(IMSI_2)};
        expectNetworkStatsSummary(new NetworkStats(getElapsedRealtime(), 1)
                .insertEntry(TEST_IFACE, 2048L, 16L, 512L, 4L));
        expectNetworkStatsUidDetail(new NetworkStats(getElapsedRealtime(), 3)
                .insertEntry(TEST_IFACE, UID_RED, SET_DEFAULT, TAG_NONE, 1536L, 12L, 512L, 4L, 0L)
                .insertEntry(TEST_IFACE, UID_RED, SET_DEFAULT, 0xF00D, 512L, 4L, 512L, 4L, 0L)
                .insertEntry(TEST_IFACE, UID_BLUE, SET_DEFAULT, TAG_NONE, 512L, 4L, 0L, 0L, 0L));

        mService.notifyNetworkStatus(NETWORKS_MOBILE, states, getActiveIface(states),
                new UnderlyingNetworkInfo[0]);
        forcePollAndWaitForIdle();


        // create traffic on second network
        incrementCurrentTime(HOUR_IN_MILLIS);
        expectDefaultSettings();
        expectNetworkStatsSummary(new NetworkStats(getElapsedRealtime(), 1)
                .insertEntry(TEST_IFACE, 2176L, 17L, 1536L, 12L));
        expectNetworkStatsUidDetail(new NetworkStats(getElapsedRealtime(), 1)
                .insertEntry(TEST_IFACE, UID_RED, SET_DEFAULT, TAG_NONE, 1536L, 12L, 512L, 4L, 0L)
                .insertEntry(TEST_IFACE, UID_RED, SET_DEFAULT, 0xF00D, 512L, 4L, 512L, 4L, 0L)
                .insertEntry(TEST_IFACE, UID_BLUE, SET_DEFAULT, TAG_NONE, 640L, 5L, 1024L, 8L, 0L)
                .insertEntry(TEST_IFACE, UID_BLUE, SET_DEFAULT, 0xFAAD, 128L, 1L, 1024L, 8L, 0L));
        mService.incrementOperationCount(UID_BLUE, 0xFAAD, 10);

        forcePollAndWaitForIdle();

        // verify original history still intact
        assertNetworkTotal(sTemplateImsi1, 2048L, 16L, 512L, 4L, 0);
        assertUidTotal(sTemplateImsi1, UID_RED, 1536L, 12L, 512L, 4L, 10);
        assertUidTotal(sTemplateImsi1, UID_BLUE, 512L, 4L, 0L, 0L, 0);

        // and verify new history also recorded under different template, which
        // verifies that we didn't cross the streams.
        assertNetworkTotal(sTemplateImsi2, 128L, 1L, 1024L, 8L, 0);
        assertNetworkTotal(sTemplateWifi, 0L, 0L, 0L, 0L, 0);
        assertUidTotal(sTemplateImsi2, UID_BLUE, 128L, 1L, 1024L, 8L, 10);

    }

    @Test
    public void testUidRemovedIsMoved() throws Exception {
        // pretend that network comes online
        expectDefaultSettings();
        NetworkStateSnapshot[] states = new NetworkStateSnapshot[] {buildWifiState()};
        expectNetworkStatsSummary(buildEmptyStats());
        expectNetworkStatsUidDetail(buildEmptyStats());

        mService.notifyNetworkStatus(NETWORKS_WIFI, states, getActiveIface(states),
                new UnderlyingNetworkInfo[0]);

        // create some traffic
        incrementCurrentTime(HOUR_IN_MILLIS);
        expectDefaultSettings();
        expectNetworkStatsSummary(new NetworkStats(getElapsedRealtime(), 1)
                .insertEntry(TEST_IFACE, 4128L, 258L, 544L, 34L));
        expectNetworkStatsUidDetail(new NetworkStats(getElapsedRealtime(), 1)
                .insertEntry(TEST_IFACE, UID_RED, SET_DEFAULT, TAG_NONE, 16L, 1L, 16L, 1L, 0L)
                .insertEntry(TEST_IFACE, UID_RED, SET_DEFAULT, 0xFAAD, 16L, 1L, 16L, 1L, 0L)
                .insertEntry(TEST_IFACE, UID_BLUE, SET_DEFAULT, TAG_NONE,
                        4096L, 258L, 512L, 32L, 0L)
                .insertEntry(TEST_IFACE, UID_GREEN, SET_DEFAULT, TAG_NONE, 16L, 1L, 16L, 1L, 0L));
        mService.incrementOperationCount(UID_RED, 0xFAAD, 10);

        forcePollAndWaitForIdle();

        // verify service recorded history
        assertNetworkTotal(sTemplateWifi, 4128L, 258L, 544L, 34L, 0);
        assertUidTotal(sTemplateWifi, UID_RED, 16L, 1L, 16L, 1L, 10);
        assertUidTotal(sTemplateWifi, UID_BLUE, 4096L, 258L, 512L, 32L, 0);
        assertUidTotal(sTemplateWifi, UID_GREEN, 16L, 1L, 16L, 1L, 0);


        // now pretend two UIDs are uninstalled, which should migrate stats to
        // special "removed" bucket.
        expectDefaultSettings();
        expectNetworkStatsSummary(new NetworkStats(getElapsedRealtime(), 1)
                .insertEntry(TEST_IFACE, 4128L, 258L, 544L, 34L));
        expectNetworkStatsUidDetail(new NetworkStats(getElapsedRealtime(), 1)
                .insertEntry(TEST_IFACE, UID_RED, SET_DEFAULT, TAG_NONE, 16L, 1L, 16L, 1L, 0L)
                .insertEntry(TEST_IFACE, UID_RED, SET_DEFAULT, 0xFAAD, 16L, 1L, 16L, 1L, 0L)
                .insertEntry(TEST_IFACE, UID_BLUE, SET_DEFAULT, TAG_NONE,
                        4096L, 258L, 512L, 32L, 0L)
                .insertEntry(TEST_IFACE, UID_GREEN, SET_DEFAULT, TAG_NONE, 16L, 1L, 16L, 1L, 0L));
        final Intent intent = new Intent(ACTION_UID_REMOVED);
        intent.putExtra(EXTRA_UID, UID_BLUE);
        mServiceContext.sendBroadcast(intent);
        intent.putExtra(EXTRA_UID, UID_RED);
        mServiceContext.sendBroadcast(intent);

        // existing uid and total should remain unchanged; but removed UID
        // should be gone completely.
        assertNetworkTotal(sTemplateWifi, 4128L, 258L, 544L, 34L, 0);
        assertUidTotal(sTemplateWifi, UID_RED, 0L, 0L, 0L, 0L, 0);
        assertUidTotal(sTemplateWifi, UID_BLUE, 0L, 0L, 0L, 0L, 0);
        assertUidTotal(sTemplateWifi, UID_GREEN, 16L, 1L, 16L, 1L, 0);
        assertUidTotal(sTemplateWifi, UID_REMOVED, 4112L, 259L, 528L, 33L, 10);

    }

    @Test
    public void testMobileStatsByRatType() throws Exception {
        final NetworkTemplate template3g =
                buildTemplateMobileWithRatType(null, TelephonyManager.NETWORK_TYPE_UMTS,
                METERED_YES);
        final NetworkTemplate template4g =
                buildTemplateMobileWithRatType(null, TelephonyManager.NETWORK_TYPE_LTE,
                METERED_YES);
        final NetworkTemplate template5g =
                buildTemplateMobileWithRatType(null, TelephonyManager.NETWORK_TYPE_NR,
                METERED_YES);
        final NetworkStateSnapshot[] states =
                new NetworkStateSnapshot[]{buildMobileState(IMSI_1)};

        // 3G network comes online.
        expectNetworkStatsSummary(buildEmptyStats());
        expectNetworkStatsUidDetail(buildEmptyStats());

        setMobileRatTypeAndWaitForIdle(TelephonyManager.NETWORK_TYPE_UMTS);
        mService.notifyNetworkStatus(NETWORKS_MOBILE, states, getActiveIface(states),
                new UnderlyingNetworkInfo[0]);

        // Create some traffic.
        incrementCurrentTime(MINUTE_IN_MILLIS);
        expectNetworkStatsUidDetail(new NetworkStats(getElapsedRealtime(), 1)
                .addEntry(new NetworkStats.Entry(TEST_IFACE, UID_RED, SET_DEFAULT, TAG_NONE,
                        12L, 18L, 14L, 1L, 0L)));
        forcePollAndWaitForIdle();

        // Verify 3g templates gets stats.
        assertUidTotal(sTemplateImsi1, UID_RED, 12L, 18L, 14L, 1L, 0);
        assertUidTotal(template3g, UID_RED, 12L, 18L, 14L, 1L, 0);
        assertUidTotal(template4g, UID_RED, 0L, 0L, 0L, 0L, 0);
        assertUidTotal(template5g, UID_RED, 0L, 0L, 0L, 0L, 0);

        // 4G network comes online.
        incrementCurrentTime(MINUTE_IN_MILLIS);
        setMobileRatTypeAndWaitForIdle(TelephonyManager.NETWORK_TYPE_LTE);
        expectNetworkStatsUidDetail(new NetworkStats(getElapsedRealtime(), 1)
                // Append more traffic on existing 3g stats entry.
                .addEntry(new NetworkStats.Entry(TEST_IFACE, UID_RED, SET_DEFAULT, TAG_NONE,
                        16L, 22L, 17L, 2L, 0L))
                // Add entry that is new on 4g.
                .addEntry(new NetworkStats.Entry(TEST_IFACE, UID_RED, SET_FOREGROUND, TAG_NONE,
                        33L, 27L, 8L, 10L, 1L)));
        forcePollAndWaitForIdle();

        // Verify ALL_MOBILE template gets all. 3g template counters do not increase.
        assertUidTotal(sTemplateImsi1, UID_RED, 49L, 49L, 25L, 12L, 1);
        assertUidTotal(template3g, UID_RED, 12L, 18L, 14L, 1L, 0);
        // Verify 4g template counts appended stats on existing entry and newly created entry.
        assertUidTotal(template4g, UID_RED, 4L + 33L, 4L + 27L, 3L + 8L, 1L + 10L, 1);
        // Verify 5g template doesn't get anything since no traffic is generated on 5g.
        assertUidTotal(template5g, UID_RED, 0L, 0L, 0L, 0L, 0);

        // 5g network comes online.
        incrementCurrentTime(MINUTE_IN_MILLIS);
        setMobileRatTypeAndWaitForIdle(TelephonyManager.NETWORK_TYPE_NR);
        expectNetworkStatsUidDetail(new NetworkStats(getElapsedRealtime(), 1)
                // Existing stats remains.
                .addEntry(new NetworkStats.Entry(TEST_IFACE, UID_RED, SET_DEFAULT, TAG_NONE,
                        16L, 22L, 17L, 2L, 0L))
                .addEntry(new NetworkStats.Entry(TEST_IFACE, UID_RED, SET_FOREGROUND, TAG_NONE,
                        33L, 27L, 8L, 10L, 1L))
                // Add some traffic on 5g.
                .addEntry(new NetworkStats.Entry(TEST_IFACE, UID_RED, SET_DEFAULT, TAG_NONE,
                5L, 13L, 31L, 9L, 2L)));
        forcePollAndWaitForIdle();

        // Verify ALL_MOBILE template gets all.
        assertUidTotal(sTemplateImsi1, UID_RED, 54L, 62L, 56L, 21L, 3);
        // 3g/4g template counters do not increase.
        assertUidTotal(template3g, UID_RED, 12L, 18L, 14L, 1L, 0);
        assertUidTotal(template4g, UID_RED, 4L + 33L, 4L + 27L, 3L + 8L, 1L + 10L, 1);
        // Verify 5g template gets the 5g count.
        assertUidTotal(template5g, UID_RED, 5L, 13L, 31L, 9L, 2);
    }

    @Test
    public void testMobileStatsMeteredness() throws Exception {
        // Create metered 5g template.
        final NetworkTemplate templateMetered5g =
                buildTemplateMobileWithRatType(null, TelephonyManager.NETWORK_TYPE_NR,
                METERED_YES);
        // Create non-metered 5g template
        final NetworkTemplate templateNonMetered5g =
                buildTemplateMobileWithRatType(null, TelephonyManager.NETWORK_TYPE_NR, METERED_NO);

        expectDefaultSettings();
        expectNetworkStatsSummary(buildEmptyStats());
        expectNetworkStatsUidDetail(buildEmptyStats());

        // Pretend that 5g mobile network comes online
        final NetworkStateSnapshot[] mobileStates =
                new NetworkStateSnapshot[] {buildMobileState(IMSI_1), buildMobileState(TEST_IFACE2,
                IMSI_1, true /* isTemporarilyNotMetered */, false /* isRoaming */)};
        setMobileRatTypeAndWaitForIdle(TelephonyManager.NETWORK_TYPE_NR);
        mService.notifyNetworkStatus(NETWORKS_MOBILE, mobileStates,
                getActiveIface(mobileStates), new UnderlyingNetworkInfo[0]);

        // Create some traffic
        // Note that all traffic from NetworkManagementService is tagged as METERED_NO, ROAMING_NO
        // and DEFAULT_NETWORK_YES, because these three properties aren't tracked at that layer.
        // They are layered on top by inspecting the iface properties.
        incrementCurrentTime(HOUR_IN_MILLIS);
        expectNetworkStatsUidDetail(new NetworkStats(getElapsedRealtime(), 1)
                .insertEntry(TEST_IFACE, UID_RED, SET_DEFAULT, TAG_NONE, METERED_YES, ROAMING_NO,
                        DEFAULT_NETWORK_YES, 128L, 2L, 128L, 2L, 0L)
                .insertEntry(TEST_IFACE2, UID_RED, SET_DEFAULT, TAG_NONE, METERED_YES, ROAMING_NO,
                        DEFAULT_NETWORK_YES, 256, 3L, 128L, 5L, 0L));
        forcePollAndWaitForIdle();

        // Verify service recorded history.
        assertUidTotal(templateMetered5g, UID_RED, 128L, 2L, 128L, 2L, 0);
        assertUidTotal(templateNonMetered5g, UID_RED, 256, 3L, 128L, 5L, 0);
    }

    @Test
    public void testMobileStatsOemManaged() throws Exception {
        final NetworkTemplate templateOemPaid = new NetworkTemplate(MATCH_MOBILE_WILDCARD,
                /*subscriberId=*/null, /*matchSubscriberIds=*/null,
                /*matchWifiNetworkKeys=*/new String[0], METERED_ALL, ROAMING_ALL,
                DEFAULT_NETWORK_ALL, NETWORK_TYPE_ALL, OEM_PAID, SUBSCRIBER_ID_MATCH_RULE_EXACT);

        final NetworkTemplate templateOemPrivate = new NetworkTemplate(MATCH_MOBILE_WILDCARD,
                /*subscriberId=*/null, /*matchSubscriberIds=*/null,
                /*matchWifiNetworkKeys=*/new String[0], METERED_ALL, ROAMING_ALL,
                DEFAULT_NETWORK_ALL, NETWORK_TYPE_ALL, OEM_PRIVATE, SUBSCRIBER_ID_MATCH_RULE_EXACT);

        final NetworkTemplate templateOemAll = new NetworkTemplate(MATCH_MOBILE_WILDCARD,
                /*subscriberId=*/null, /*matchSubscriberIds=*/null,
                /*matchWifiNetworkKeys=*/new String[0], METERED_ALL, ROAMING_ALL,
                DEFAULT_NETWORK_ALL, NETWORK_TYPE_ALL, OEM_PAID | OEM_PRIVATE,
                SUBSCRIBER_ID_MATCH_RULE_EXACT);

        final NetworkTemplate templateOemYes = new NetworkTemplate(MATCH_MOBILE_WILDCARD,
                /*subscriberId=*/null, /*matchSubscriberIds=*/null,
                /*matchWifiNetworkKeys=*/new String[0], METERED_ALL, ROAMING_ALL,
                DEFAULT_NETWORK_ALL, NETWORK_TYPE_ALL, OEM_MANAGED_YES,
                SUBSCRIBER_ID_MATCH_RULE_EXACT);

        final NetworkTemplate templateOemNone = new NetworkTemplate(MATCH_MOBILE_WILDCARD,
                /*subscriberId=*/null, /*matchSubscriberIds=*/null,
                /*matchWifiNetworkKeys=*/new String[0], METERED_ALL, ROAMING_ALL,
                DEFAULT_NETWORK_ALL, NETWORK_TYPE_ALL, OEM_MANAGED_NO,
                SUBSCRIBER_ID_MATCH_RULE_EXACT);

        // OEM_PAID network comes online.
        NetworkStateSnapshot[] states = new NetworkStateSnapshot[]{
                buildOemManagedMobileState(IMSI_1, false,
                new int[]{NetworkCapabilities.NET_CAPABILITY_OEM_PAID})};
        expectNetworkStatsSummary(buildEmptyStats());
        expectNetworkStatsUidDetail(buildEmptyStats());
        mService.notifyNetworkStatus(NETWORKS_MOBILE, states, getActiveIface(states),
                new UnderlyingNetworkInfo[0]);

        // Create some traffic.
        incrementCurrentTime(MINUTE_IN_MILLIS);
        expectNetworkStatsUidDetail(new NetworkStats(getElapsedRealtime(), 1)
                .addEntry(new NetworkStats.Entry(TEST_IFACE, UID_RED, SET_DEFAULT, TAG_NONE,
                        36L, 41L, 24L, 96L, 0L)));
        forcePollAndWaitForIdle();

        // OEM_PRIVATE network comes online.
        states = new NetworkStateSnapshot[]{buildOemManagedMobileState(IMSI_1, false,
                new int[]{NetworkCapabilities.NET_CAPABILITY_OEM_PRIVATE})};
        expectNetworkStatsSummary(buildEmptyStats());
        expectNetworkStatsUidDetail(buildEmptyStats());
        mService.notifyNetworkStatus(NETWORKS_MOBILE, states, getActiveIface(states),
                new UnderlyingNetworkInfo[0]);

        // Create some traffic.
        incrementCurrentTime(MINUTE_IN_MILLIS);
        expectNetworkStatsUidDetail(new NetworkStats(getElapsedRealtime(), 1)
                .addEntry(new NetworkStats.Entry(TEST_IFACE, UID_RED, SET_DEFAULT, TAG_NONE,
                        49L, 71L, 72L, 48L, 0L)));
        forcePollAndWaitForIdle();

        // OEM_PAID + OEM_PRIVATE network comes online.
        states = new NetworkStateSnapshot[]{buildOemManagedMobileState(IMSI_1, false,
                new int[]{NetworkCapabilities.NET_CAPABILITY_OEM_PRIVATE,
                          NetworkCapabilities.NET_CAPABILITY_OEM_PAID})};
        expectNetworkStatsSummary(buildEmptyStats());
        expectNetworkStatsUidDetail(buildEmptyStats());
        mService.notifyNetworkStatus(NETWORKS_MOBILE, states, getActiveIface(states),
                new UnderlyingNetworkInfo[0]);

        // Create some traffic.
        incrementCurrentTime(MINUTE_IN_MILLIS);
        expectNetworkStatsUidDetail(new NetworkStats(getElapsedRealtime(), 1)
                .addEntry(new NetworkStats.Entry(TEST_IFACE, UID_RED, SET_DEFAULT, TAG_NONE,
                        57L, 86L, 83L, 93L, 0L)));
        forcePollAndWaitForIdle();

        // OEM_NONE network comes online.
        states = new NetworkStateSnapshot[]{buildOemManagedMobileState(IMSI_1, false, new int[]{})};
        expectNetworkStatsSummary(buildEmptyStats());
        expectNetworkStatsUidDetail(buildEmptyStats());
        mService.notifyNetworkStatus(NETWORKS_MOBILE, states, getActiveIface(states),
                new UnderlyingNetworkInfo[0]);

        // Create some traffic.
        incrementCurrentTime(MINUTE_IN_MILLIS);
        expectNetworkStatsUidDetail(new NetworkStats(getElapsedRealtime(), 1)
                .addEntry(new NetworkStats.Entry(TEST_IFACE, UID_RED, SET_DEFAULT, TAG_NONE,
                        29L, 73L, 34L, 31L, 0L)));
        forcePollAndWaitForIdle();

        // Verify OEM_PAID template gets only relevant stats.
        assertUidTotal(templateOemPaid, UID_RED, 36L, 41L, 24L, 96L, 0);

        // Verify OEM_PRIVATE template gets only relevant stats.
        assertUidTotal(templateOemPrivate, UID_RED, 49L, 71L, 72L, 48L, 0);

        // Verify OEM_PAID + OEM_PRIVATE template gets only relevant stats.
        assertUidTotal(templateOemAll, UID_RED, 57L, 86L, 83L, 93L, 0);

        // Verify OEM_NONE sees only non-OEM managed stats.
        assertUidTotal(templateOemNone, UID_RED, 29L, 73L, 34L, 31L, 0);

        // Verify OEM_MANAGED_YES sees all OEM managed stats.
        assertUidTotal(templateOemYes, UID_RED,
                36L + 49L + 57L,
                41L + 71L + 86L,
                24L + 72L + 83L,
                96L + 48L + 93L, 0);

        // Verify ALL_MOBILE template gets both OEM managed and non-OEM managed stats.
        assertUidTotal(sTemplateImsi1, UID_RED,
                36L + 49L + 57L + 29L,
                41L + 71L + 86L + 73L,
                24L + 72L + 83L + 34L,
                96L + 48L + 93L + 31L, 0);
    }

    // TODO: support per IMSI state
    private void setMobileRatTypeAndWaitForIdle(int ratType) {
        when(mNetworkStatsSubscriptionsMonitor.getRatTypeForSubscriberId(anyString()))
                .thenReturn(ratType);
        mService.handleOnCollapsedRatTypeChanged();
        HandlerUtils.waitForIdle(mHandlerThread, WAIT_TIMEOUT);
    }

    @Test
    public void testSummaryForAllUid() throws Exception {
        // pretend that network comes online
        expectDefaultSettings();
        NetworkStateSnapshot[] states = new NetworkStateSnapshot[] {buildWifiState()};
        expectNetworkStatsSummary(buildEmptyStats());
        expectNetworkStatsUidDetail(buildEmptyStats());

        mService.notifyNetworkStatus(NETWORKS_WIFI, states, getActiveIface(states),
                new UnderlyingNetworkInfo[0]);

        // create some traffic for two apps
        incrementCurrentTime(HOUR_IN_MILLIS);
        expectDefaultSettings();
        expectNetworkStatsSummary(buildEmptyStats());
        expectNetworkStatsUidDetail(new NetworkStats(getElapsedRealtime(), 1)
                .insertEntry(TEST_IFACE, UID_RED, SET_DEFAULT, TAG_NONE, 50L, 5L, 50L, 5L, 0L)
                .insertEntry(TEST_IFACE, UID_RED, SET_DEFAULT, 0xF00D, 10L, 1L, 10L, 1L, 0L)
                .insertEntry(TEST_IFACE, UID_BLUE, SET_DEFAULT, TAG_NONE, 1024L, 8L, 512L, 4L, 0L));
        mService.incrementOperationCount(UID_RED, 0xF00D, 1);

        forcePollAndWaitForIdle();

        // verify service recorded history
        assertUidTotal(sTemplateWifi, UID_RED, 50L, 5L, 50L, 5L, 1);
        assertUidTotal(sTemplateWifi, UID_BLUE, 1024L, 8L, 512L, 4L, 0);


        // now create more traffic in next hour, but only for one app
        incrementCurrentTime(HOUR_IN_MILLIS);
        expectDefaultSettings();
        expectNetworkStatsSummary(buildEmptyStats());
        expectNetworkStatsUidDetail(new NetworkStats(getElapsedRealtime(), 1)
                .insertEntry(TEST_IFACE, UID_RED, SET_DEFAULT, TAG_NONE, 50L, 5L, 50L, 5L, 0L)
                .insertEntry(TEST_IFACE, UID_RED, SET_DEFAULT, 0xF00D, 10L, 1L, 10L, 1L, 0L)
                .insertEntry(TEST_IFACE, UID_BLUE, SET_DEFAULT, TAG_NONE,
                        2048L, 16L, 1024L, 8L, 0L));
        forcePollAndWaitForIdle();

        // first verify entire history present
        NetworkStats stats = mSession.getSummaryForAllUid(
                sTemplateWifi, Long.MIN_VALUE, Long.MAX_VALUE, true);
        assertEquals(3, stats.size());
        assertValues(stats, IFACE_ALL, UID_RED, SET_DEFAULT, TAG_NONE, METERED_NO, ROAMING_NO,
                DEFAULT_NETWORK_YES, 50L, 5L, 50L, 5L, 1);
        assertValues(stats, IFACE_ALL, UID_RED, SET_DEFAULT, 0xF00D, METERED_NO, ROAMING_NO,
                DEFAULT_NETWORK_YES, 10L, 1L, 10L, 1L, 1);
        assertValues(stats, IFACE_ALL, UID_BLUE, SET_DEFAULT, TAG_NONE, METERED_NO, ROAMING_NO,
                DEFAULT_NETWORK_YES, 2048L, 16L, 1024L, 8L, 0);

        // now verify that recent history only contains one uid
        final long currentTime = currentTimeMillis();
        stats = mSession.getSummaryForAllUid(
                sTemplateWifi, currentTime - HOUR_IN_MILLIS, currentTime, true);
        assertEquals(1, stats.size());
        assertValues(stats, IFACE_ALL, UID_BLUE, SET_DEFAULT, TAG_NONE, METERED_NO, ROAMING_NO,
                DEFAULT_NETWORK_YES, 1024L, 8L, 512L, 4L, 0);
    }

    @Test
    public void testGetLatestSummary() throws Exception {
        // Pretend that network comes online.
        expectDefaultSettings();
        NetworkStateSnapshot[] states = new NetworkStateSnapshot[]{buildWifiState()};
        expectNetworkStatsSummary(buildEmptyStats());
        expectNetworkStatsUidDetail(buildEmptyStats());

        mService.notifyNetworkStatus(NETWORKS_WIFI, states, getActiveIface(states),
                new UnderlyingNetworkInfo[0]);

        // Increase arbitrary time which does not align to the bucket edge, create some traffic.
        incrementCurrentTime(1751000L);
        NetworkStats.Entry entry = new NetworkStats.Entry(
                TEST_IFACE, UID_ALL, SET_DEFAULT, TAG_NONE, 50L, 5L, 51L, 1L, 3L);
        expectNetworkStatsSummary(new NetworkStats(getElapsedRealtime(), 1).insertEntry(entry));
        expectNetworkStatsUidDetail(buildEmptyStats());
        forcePollAndWaitForIdle();

        // Verify the mocked stats is returned by querying with the range of the latest bucket.
        final ZonedDateTime end =
                ZonedDateTime.ofInstant(mClock.instant(), ZoneId.systemDefault());
        final ZonedDateTime start = end.truncatedTo(ChronoUnit.HOURS);
        NetworkStats stats = mSession.getSummaryForNetwork(buildTemplateWifi(TEST_WIFI_NETWORK_KEY),
                start.toInstant().toEpochMilli(), end.toInstant().toEpochMilli());
        assertEquals(1, stats.size());
        assertValues(stats, IFACE_ALL, UID_ALL, SET_ALL, TAG_NONE, METERED_ALL, ROAMING_ALL,
                DEFAULT_NETWORK_ALL, 50L, 5L, 51L, 1L, 3L);

        // For getHistoryIntervalForNetwork, only includes buckets that atomically occur in
        // the inclusive time range, instead of including the latest bucket. This behavior is
        // already documented publicly, refer to {@link NetworkStatsManager#queryDetails}.
    }

    @Test
    public void testUidStatsForTransport() throws Exception {
        // pretend that network comes online
        expectDefaultSettings();
        NetworkStateSnapshot[] states = new NetworkStateSnapshot[] {buildWifiState()};
        expectNetworkStatsSummary(buildEmptyStats());
        expectNetworkStatsUidDetail(buildEmptyStats());

        mService.notifyNetworkStatus(NETWORKS_WIFI, states, getActiveIface(states),
                new UnderlyingNetworkInfo[0]);

        NetworkStats.Entry entry1 = new NetworkStats.Entry(
                TEST_IFACE, UID_RED, SET_DEFAULT, TAG_NONE, 50L, 5L, 50L, 5L, 0L);
        NetworkStats.Entry entry2 = new NetworkStats.Entry(
                TEST_IFACE, UID_RED, SET_DEFAULT, 0xF00D, 50L, 5L, 50L, 5L, 0L);
        NetworkStats.Entry entry3 = new NetworkStats.Entry(
                TEST_IFACE, UID_BLUE, SET_DEFAULT, 0xBEEF, 1024L, 8L, 512L, 4L, 0L);

        incrementCurrentTime(HOUR_IN_MILLIS);
        expectDefaultSettings();
        expectNetworkStatsSummary(buildEmptyStats());
        expectNetworkStatsUidDetail(new NetworkStats(getElapsedRealtime(), 3)
                .insertEntry(entry1)
                .insertEntry(entry2)
                .insertEntry(entry3));
        mService.incrementOperationCount(UID_RED, 0xF00D, 1);

        NetworkStats stats = mService.getUidStatsForTransport(NetworkCapabilities.TRANSPORT_WIFI);

        assertEquals(3, stats.size());
        entry1.operations = 1;
        entry1.iface = null;
        assertEquals(entry1, stats.getValues(0, null));
        entry2.operations = 1;
        entry2.iface = null;
        assertEquals(entry2, stats.getValues(1, null));
        entry3.iface = null;
        assertEquals(entry3, stats.getValues(2, null));
    }

    @Test
    public void testForegroundBackground() throws Exception {
        // pretend that network comes online
        expectDefaultSettings();
        NetworkStateSnapshot[] states = new NetworkStateSnapshot[] {buildWifiState()};
        expectNetworkStatsSummary(buildEmptyStats());
        expectNetworkStatsUidDetail(buildEmptyStats());

        mService.notifyNetworkStatus(NETWORKS_WIFI, states, getActiveIface(states),
                new UnderlyingNetworkInfo[0]);

        // create some initial traffic
        incrementCurrentTime(HOUR_IN_MILLIS);
        expectDefaultSettings();
        expectNetworkStatsSummary(buildEmptyStats());
        expectNetworkStatsUidDetail(new NetworkStats(getElapsedRealtime(), 1)
                .insertEntry(TEST_IFACE, UID_RED, SET_DEFAULT, TAG_NONE, 128L, 2L, 128L, 2L, 0L)
                .insertEntry(TEST_IFACE, UID_RED, SET_DEFAULT, 0xF00D, 64L, 1L, 64L, 1L, 0L));
        mService.incrementOperationCount(UID_RED, 0xF00D, 1);

        forcePollAndWaitForIdle();

        // verify service recorded history
        assertUidTotal(sTemplateWifi, UID_RED, 128L, 2L, 128L, 2L, 1);


        // now switch to foreground
        incrementCurrentTime(HOUR_IN_MILLIS);
        expectDefaultSettings();
        expectNetworkStatsSummary(buildEmptyStats());
        expectNetworkStatsUidDetail(new NetworkStats(getElapsedRealtime(), 1)
                .insertEntry(TEST_IFACE, UID_RED, SET_DEFAULT, TAG_NONE, 128L, 2L, 128L, 2L, 0L)
                .insertEntry(TEST_IFACE, UID_RED, SET_DEFAULT, 0xF00D, 64L, 1L, 64L, 1L, 0L)
                .insertEntry(TEST_IFACE, UID_RED, SET_FOREGROUND, TAG_NONE, 32L, 2L, 32L, 2L, 0L)
                .insertEntry(TEST_IFACE, UID_RED, SET_FOREGROUND, 0xFAAD, 1L, 1L, 1L, 1L, 0L));
        mService.noteUidForeground(UID_RED, true);
        verify(mUidCounterSetMap).updateEntry(
                eq(new U32(UID_RED)), eq(new U8((short) SET_FOREGROUND)));
        mService.incrementOperationCount(UID_RED, 0xFAAD, 1);

        forcePollAndWaitForIdle();

        // test that we combined correctly
        assertUidTotal(sTemplateWifi, UID_RED, 160L, 4L, 160L, 4L, 2);

        // verify entire history present
        final NetworkStats stats = mSession.getSummaryForAllUid(
                sTemplateWifi, Long.MIN_VALUE, Long.MAX_VALUE, true);
        assertEquals(4, stats.size());
        assertValues(stats, IFACE_ALL, UID_RED, SET_DEFAULT, TAG_NONE, METERED_NO, ROAMING_NO,
                DEFAULT_NETWORK_YES, 128L, 2L, 128L, 2L, 1);
        assertValues(stats, IFACE_ALL, UID_RED, SET_DEFAULT, 0xF00D, METERED_NO, ROAMING_NO,
                DEFAULT_NETWORK_YES, 64L, 1L, 64L, 1L, 1);
        assertValues(stats, IFACE_ALL, UID_RED, SET_FOREGROUND, TAG_NONE, METERED_NO, ROAMING_NO,
                DEFAULT_NETWORK_YES, 32L, 2L, 32L, 2L, 1);
        assertValues(stats, IFACE_ALL, UID_RED, SET_FOREGROUND, 0xFAAD, METERED_NO, ROAMING_NO,
                DEFAULT_NETWORK_YES, 1L, 1L, 1L, 1L, 1);
    }

    @Test
    public void testMetered() throws Exception {
        // pretend that network comes online
        expectDefaultSettings();
        NetworkStateSnapshot[] states =
                new NetworkStateSnapshot[] {buildWifiState(true /* isMetered */, TEST_IFACE)};
        expectNetworkStatsSummary(buildEmptyStats());
        expectNetworkStatsUidDetail(buildEmptyStats());

        mService.notifyNetworkStatus(NETWORKS_WIFI, states, getActiveIface(states),
                new UnderlyingNetworkInfo[0]);

        // create some initial traffic
        incrementCurrentTime(HOUR_IN_MILLIS);
        expectDefaultSettings();
        expectNetworkStatsSummary(buildEmptyStats());
        // Note that all traffic from NetworkManagementService is tagged as METERED_NO, ROAMING_NO
        // and DEFAULT_NETWORK_YES, because these three properties aren't tracked at that layer.
        // We layer them on top by inspecting the iface properties.
        expectNetworkStatsUidDetail(new NetworkStats(getElapsedRealtime(), 1)
                .insertEntry(TEST_IFACE, UID_RED, SET_DEFAULT, TAG_NONE, METERED_NO, ROAMING_NO,
                        DEFAULT_NETWORK_YES, 128L, 2L, 128L, 2L, 0L)
                .insertEntry(TEST_IFACE, UID_RED, SET_DEFAULT, 0xF00D, METERED_NO, ROAMING_NO,
                        DEFAULT_NETWORK_YES, 64L, 1L, 64L, 1L, 0L));
        mService.incrementOperationCount(UID_RED, 0xF00D, 1);

        forcePollAndWaitForIdle();

        // verify service recorded history
        assertUidTotal(sTemplateWifi, UID_RED, 128L, 2L, 128L, 2L, 1);
        // verify entire history present
        final NetworkStats stats = mSession.getSummaryForAllUid(
                sTemplateWifi, Long.MIN_VALUE, Long.MAX_VALUE, true);
        assertEquals(2, stats.size());
        assertValues(stats, IFACE_ALL, UID_RED, SET_DEFAULT, TAG_NONE, METERED_YES, ROAMING_NO,
                DEFAULT_NETWORK_YES,  128L, 2L, 128L, 2L, 1);
        assertValues(stats, IFACE_ALL, UID_RED, SET_DEFAULT, 0xF00D, METERED_YES, ROAMING_NO,
                DEFAULT_NETWORK_YES, 64L, 1L, 64L, 1L, 1);
    }

    @Test
    public void testRoaming() throws Exception {
        // pretend that network comes online
        expectDefaultSettings();
        NetworkStateSnapshot[] states =
            new NetworkStateSnapshot[] {buildMobileState(TEST_IFACE, IMSI_1,
            false /* isTemporarilyNotMetered */, true /* isRoaming */)};
        expectNetworkStatsSummary(buildEmptyStats());
        expectNetworkStatsUidDetail(buildEmptyStats());

        mService.notifyNetworkStatus(NETWORKS_MOBILE, states, getActiveIface(states),
                new UnderlyingNetworkInfo[0]);

        // Create some traffic
        incrementCurrentTime(HOUR_IN_MILLIS);
        expectDefaultSettings();
        expectNetworkStatsSummary(buildEmptyStats());
        // Note that all traffic from NetworkManagementService is tagged as METERED_NO and
        // ROAMING_NO, because metered and roaming isn't tracked at that layer. We layer it
        // on top by inspecting the iface properties.
        expectNetworkStatsUidDetail(new NetworkStats(getElapsedRealtime(), 1)
                .insertEntry(TEST_IFACE, UID_RED, SET_DEFAULT, TAG_NONE, METERED_ALL, ROAMING_NO,
                        DEFAULT_NETWORK_YES,  128L, 2L, 128L, 2L, 0L)
                .insertEntry(TEST_IFACE, UID_RED, SET_DEFAULT, 0xF00D, METERED_ALL, ROAMING_NO,
                        DEFAULT_NETWORK_YES, 64L, 1L, 64L, 1L, 0L));
        forcePollAndWaitForIdle();

        // verify service recorded history
        assertUidTotal(sTemplateImsi1, UID_RED, 128L, 2L, 128L, 2L, 0);

        // verify entire history present
        final NetworkStats stats = mSession.getSummaryForAllUid(
                sTemplateImsi1, Long.MIN_VALUE, Long.MAX_VALUE, true);
        assertEquals(2, stats.size());
        assertValues(stats, IFACE_ALL, UID_RED, SET_DEFAULT, TAG_NONE, METERED_ALL, ROAMING_YES,
                DEFAULT_NETWORK_YES, 128L, 2L, 128L, 2L, 0);
        assertValues(stats, IFACE_ALL, UID_RED, SET_DEFAULT, 0xF00D, METERED_ALL, ROAMING_YES,
                DEFAULT_NETWORK_YES, 64L, 1L, 64L, 1L, 0);
    }

    @Test
    public void testTethering() throws Exception {
        // pretend first mobile network comes online
        expectDefaultSettings();
        final NetworkStateSnapshot[] states =
                new NetworkStateSnapshot[]{buildMobileState(IMSI_1)};
        expectNetworkStatsSummary(buildEmptyStats());
        expectNetworkStatsUidDetail(buildEmptyStats());

        mService.notifyNetworkStatus(NETWORKS_MOBILE, states, getActiveIface(states),
                new UnderlyingNetworkInfo[0]);

        // create some tethering traffic
        incrementCurrentTime(HOUR_IN_MILLIS);
        expectDefaultSettings();

        // Register custom provider and retrieve callback.
        final TestableNetworkStatsProviderBinder provider =
                new TestableNetworkStatsProviderBinder();
        final INetworkStatsProviderCallback cb =
                mService.registerNetworkStatsProvider("TEST-TETHERING-OFFLOAD", provider);
        assertNotNull(cb);
        final long now = getElapsedRealtime();

        // Traffic seen by kernel counters (includes software tethering).
        final NetworkStats swIfaceStats = new NetworkStats(now, 1)
                .insertEntry(TEST_IFACE, 1536L, 12L, 384L, 3L);
        // Hardware tethering traffic, not seen by kernel counters.
        final NetworkStats tetherHwIfaceStats = new NetworkStats(now, 1)
                .insertEntry(new NetworkStats.Entry(TEST_IFACE, UID_ALL, SET_DEFAULT,
                        TAG_NONE, METERED_YES, ROAMING_NO, DEFAULT_NETWORK_YES,
                        512L, 4L, 128L, 1L, 0L));
        final NetworkStats tetherHwUidStats = new NetworkStats(now, 1)
                .insertEntry(new NetworkStats.Entry(TEST_IFACE, UID_TETHERING, SET_DEFAULT,
                        TAG_NONE, METERED_YES, ROAMING_NO, DEFAULT_NETWORK_YES,
                        512L, 4L, 128L, 1L, 0L));
        cb.notifyStatsUpdated(0 /* unused */, tetherHwIfaceStats, tetherHwUidStats);

        // Fake some traffic done by apps on the device (as opposed to tethering), and record it
        // into UID stats (as opposed to iface stats).
        final NetworkStats localUidStats = new NetworkStats(now, 1)
                .insertEntry(TEST_IFACE, UID_RED, SET_DEFAULT, TAG_NONE, 128L, 2L, 128L, 2L, 0L);
        // Software per-uid tethering traffic.
        final TetherStatsParcel[] tetherStatsParcels =
                {buildTetherStatsParcel(TEST_IFACE, 1408L, 10L, 256L, 1L, 0)};

        expectNetworkStatsSummary(swIfaceStats);
        expectNetworkStatsUidDetail(localUidStats, tetherStatsParcels);
        forcePollAndWaitForIdle();

        // verify service recorded history
        assertNetworkTotal(sTemplateImsi1, 2048L, 16L, 512L, 4L, 0);
        assertUidTotal(sTemplateImsi1, UID_RED, 128L, 2L, 128L, 2L, 0);
        assertUidTotal(sTemplateImsi1, UID_TETHERING, 1920L, 14L, 384L, 2L, 0);
    }

    @Test
    public void testRegisterUsageCallback() throws Exception {
        // pretend that wifi network comes online; service should ask about full
        // network state, and poll any existing interfaces before updating.
        expectDefaultSettings();
        NetworkStateSnapshot[] states = new NetworkStateSnapshot[] {buildWifiState()};
        expectNetworkStatsSummary(buildEmptyStats());
        expectNetworkStatsUidDetail(buildEmptyStats());

        mService.notifyNetworkStatus(NETWORKS_WIFI, states, getActiveIface(states),
                new UnderlyingNetworkInfo[0]);

        // verify service has empty history for wifi
        assertNetworkTotal(sTemplateWifi, 0L, 0L, 0L, 0L, 0);
        long thresholdInBytes = 1L;  // very small; should be overriden by framework
        DataUsageRequest inputRequest = new DataUsageRequest(
                DataUsageRequest.REQUEST_ID_UNSET, sTemplateWifi, thresholdInBytes);

        // Force poll
        expectDefaultSettings();
        expectNetworkStatsSummary(buildEmptyStats());
        expectNetworkStatsUidDetail(buildEmptyStats());

        // Register and verify request and that binder was called
        DataUsageRequest request = mService.registerUsageCallback(
                mServiceContext.getOpPackageName(), inputRequest, mUsageCallback);
        assertTrue(request.requestId > 0);
        assertTrue(Objects.equals(sTemplateWifi, request.template));
        long minThresholdInBytes = 2 * 1024 * 1024; // 2 MB
        assertEquals(minThresholdInBytes, request.thresholdInBytes);

        HandlerUtils.waitForIdle(mHandlerThread, WAIT_TIMEOUT);

        // Make sure that the caller binder gets connected
        verify(mUsageCallbackBinder).linkToDeath(any(IBinder.DeathRecipient.class), anyInt());

        // modify some number on wifi, and trigger poll event
        // not enough traffic to call data usage callback
        incrementCurrentTime(HOUR_IN_MILLIS);
        expectDefaultSettings();
        expectNetworkStatsSummary(new NetworkStats(getElapsedRealtime(), 1)
                .insertEntry(TEST_IFACE, 1024L, 1L, 2048L, 2L));
        expectNetworkStatsUidDetail(buildEmptyStats());
        forcePollAndWaitForIdle();

        // verify service recorded history
        assertNetworkTotal(sTemplateWifi, 1024L, 1L, 2048L, 2L, 0);

        // make sure callback has not being called
        mUsageCallback.assertNoCallback();

        // and bump forward again, with counters going higher. this is
        // important, since it will trigger the data usage callback
        incrementCurrentTime(DAY_IN_MILLIS);
        expectDefaultSettings();
        expectNetworkStatsSummary(new NetworkStats(getElapsedRealtime(), 1)
                .insertEntry(TEST_IFACE, 4096000L, 4L, 8192000L, 8L));
        expectNetworkStatsUidDetail(buildEmptyStats());
        forcePollAndWaitForIdle();

        // verify service recorded history
        assertNetworkTotal(sTemplateWifi, 4096000L, 4L, 8192000L, 8L, 0);


        // Wait for the caller to invoke expectOnThresholdReached.
        mUsageCallback.expectOnThresholdReached(request);

        // Allow binder to disconnect
        when(mUsageCallbackBinder.unlinkToDeath(any(IBinder.DeathRecipient.class), anyInt()))
                .thenReturn(true);

        // Unregister request
        mService.unregisterUsageRequest(request);

        // Wait for the caller to invoke expectOnCallbackReleased.
        mUsageCallback.expectOnCallbackReleased(request);

        // Make sure that the caller binder gets disconnected
        verify(mUsageCallbackBinder).unlinkToDeath(any(IBinder.DeathRecipient.class), anyInt());
    }

    @Test
    public void testUnregisterUsageCallback_unknown_noop() throws Exception {
        String callingPackage = "the.calling.package";
        long thresholdInBytes = 10 * 1024 * 1024;  // 10 MB
        DataUsageRequest unknownRequest = new DataUsageRequest(
                2 /* requestId */, sTemplateImsi1, thresholdInBytes);

        mService.unregisterUsageRequest(unknownRequest);
    }

    @Test
    public void testStatsProviderUpdateStats() throws Exception {
        // Pretend that network comes online.
        expectDefaultSettings();
        final NetworkStateSnapshot[] states =
                new NetworkStateSnapshot[]{buildWifiState(true /* isMetered */, TEST_IFACE)};
        expectNetworkStatsSummary(buildEmptyStats());
        expectNetworkStatsUidDetail(buildEmptyStats());

        // Register custom provider and retrieve callback.
        final TestableNetworkStatsProviderBinder provider =
                new TestableNetworkStatsProviderBinder();
        final INetworkStatsProviderCallback cb =
                mService.registerNetworkStatsProvider("TEST", provider);
        assertNotNull(cb);

        mService.notifyNetworkStatus(NETWORKS_WIFI, states, getActiveIface(states),
                new UnderlyingNetworkInfo[0]);

        // Verifies that one requestStatsUpdate will be called during iface update.
        provider.expectOnRequestStatsUpdate(0 /* unused */);

        // Create some initial traffic and report to the service.
        incrementCurrentTime(HOUR_IN_MILLIS);
        final NetworkStats expectedStats = new NetworkStats(0L, 1)
                .addEntry(new NetworkStats.Entry(TEST_IFACE, UID_RED, SET_DEFAULT,
                        TAG_NONE, METERED_YES, ROAMING_NO, DEFAULT_NETWORK_YES,
                        128L, 2L, 128L, 2L, 1L))
                .addEntry(new NetworkStats.Entry(TEST_IFACE, UID_RED, SET_DEFAULT,
                        0xF00D, METERED_YES, ROAMING_NO, DEFAULT_NETWORK_YES,
                        64L, 1L, 64L, 1L, 1L));
        cb.notifyStatsUpdated(0 /* unused */, expectedStats, expectedStats);

        // Make another empty mutable stats object. This is necessary since the new NetworkStats
        // object will be used to compare with the old one in NetworkStatsRecoder, two of them
        // cannot be the same object.
        expectNetworkStatsUidDetail(buildEmptyStats());

        forcePollAndWaitForIdle();

        // Verifies that one requestStatsUpdate and setAlert will be called during polling.
        provider.expectOnRequestStatsUpdate(0 /* unused */);
        provider.expectOnSetAlert(MB_IN_BYTES);

        // Verifies that service recorded history, does not verify uid tag part.
        assertUidTotal(sTemplateWifi, UID_RED, 128L, 2L, 128L, 2L, 1);

        // Verifies that onStatsUpdated updates the stats accordingly.
        final NetworkStats stats = mSession.getSummaryForAllUid(
                sTemplateWifi, Long.MIN_VALUE, Long.MAX_VALUE, true);
        assertEquals(2, stats.size());
        assertValues(stats, IFACE_ALL, UID_RED, SET_DEFAULT, TAG_NONE, METERED_YES, ROAMING_NO,
                DEFAULT_NETWORK_YES, 128L, 2L, 128L, 2L, 1L);
        assertValues(stats, IFACE_ALL, UID_RED, SET_DEFAULT, 0xF00D, METERED_YES, ROAMING_NO,
                DEFAULT_NETWORK_YES, 64L, 1L, 64L, 1L, 1L);

        // Verifies that unregister the callback will remove the provider from service.
        cb.unregister();
        forcePollAndWaitForIdle();
        provider.assertNoCallback();
    }

    @Test
    public void testDualVilteProviderStats() throws Exception {
        // Pretend that network comes online.
        expectDefaultSettings();
        final int subId1 = 1;
        final int subId2 = 2;
        final NetworkStateSnapshot[] states = new NetworkStateSnapshot[]{
                buildImsState(IMSI_1, subId1, TEST_IFACE),
                buildImsState(IMSI_2, subId2, TEST_IFACE2)};
        expectNetworkStatsSummary(buildEmptyStats());
        expectNetworkStatsUidDetail(buildEmptyStats());

        // Register custom provider and retrieve callback.
        final TestableNetworkStatsProviderBinder provider =
                new TestableNetworkStatsProviderBinder();
        final INetworkStatsProviderCallback cb =
                mService.registerNetworkStatsProvider("TEST", provider);
        assertNotNull(cb);

        mService.notifyNetworkStatus(NETWORKS_MOBILE, states, getActiveIface(states),
                new UnderlyingNetworkInfo[0]);

        // Verifies that one requestStatsUpdate will be called during iface update.
        provider.expectOnRequestStatsUpdate(0 /* unused */);

        // Create some initial traffic and report to the service.
        incrementCurrentTime(HOUR_IN_MILLIS);
        final String vtIface1 = NetworkStats.IFACE_VT + subId1;
        final String vtIface2 = NetworkStats.IFACE_VT + subId2;
        final NetworkStats expectedStats = new NetworkStats(0L, 1)
                .addEntry(new NetworkStats.Entry(vtIface1, UID_RED, SET_DEFAULT,
                        TAG_NONE, METERED_YES, ROAMING_NO, DEFAULT_NETWORK_YES,
                        128L, 2L, 128L, 2L, 1L))
                .addEntry(new NetworkStats.Entry(vtIface2, UID_RED, SET_DEFAULT,
                        TAG_NONE, METERED_YES, ROAMING_NO, DEFAULT_NETWORK_YES,
                        64L, 1L, 64L, 1L, 1L));
        cb.notifyStatsUpdated(0 /* unused */, expectedStats, expectedStats);

        // Make another empty mutable stats object. This is necessary since the new NetworkStats
        // object will be used to compare with the old one in NetworkStatsRecoder, two of them
        // cannot be the same object.
        expectNetworkStatsUidDetail(buildEmptyStats());

        forcePollAndWaitForIdle();

        // Verifies that one requestStatsUpdate and setAlert will be called during polling.
        provider.expectOnRequestStatsUpdate(0 /* unused */);
        provider.expectOnSetAlert(MB_IN_BYTES);

        // Verifies that service recorded history, does not verify uid tag part.
        assertUidTotal(sTemplateImsi1, UID_RED, 128L, 2L, 128L, 2L, 1);

        // Verifies that onStatsUpdated updates the stats accordingly.
        NetworkStats stats = mSession.getSummaryForAllUid(
                sTemplateImsi1, Long.MIN_VALUE, Long.MAX_VALUE, true);
        assertEquals(1, stats.size());
        assertValues(stats, IFACE_ALL, UID_RED, SET_DEFAULT, TAG_NONE, METERED_YES, ROAMING_NO,
                DEFAULT_NETWORK_YES, 128L, 2L, 128L, 2L, 1L);

        stats = mSession.getSummaryForAllUid(
                sTemplateImsi2, Long.MIN_VALUE, Long.MAX_VALUE, true);
        assertEquals(1, stats.size());
        assertValues(stats, IFACE_ALL, UID_RED, SET_DEFAULT, TAG_NONE, METERED_YES, ROAMING_NO,
                DEFAULT_NETWORK_YES, 64L, 1L, 64L, 1L, 1L);

        // Verifies that unregister the callback will remove the provider from service.
        cb.unregister();
        forcePollAndWaitForIdle();
        provider.assertNoCallback();
    }

    @Test
    public void testStatsProviderSetAlert() throws Exception {
        // Pretend that network comes online.
        expectDefaultSettings();
        NetworkStateSnapshot[] states =
                new NetworkStateSnapshot[]{buildWifiState(true /* isMetered */, TEST_IFACE)};
        mService.notifyNetworkStatus(NETWORKS_WIFI, states, getActiveIface(states),
                new UnderlyingNetworkInfo[0]);

        // Register custom provider and retrieve callback.
        final TestableNetworkStatsProviderBinder provider =
                new TestableNetworkStatsProviderBinder();
        final INetworkStatsProviderCallback cb =
                mService.registerNetworkStatsProvider("TEST", provider);
        assertNotNull(cb);

        // Simulates alert quota of the provider has been reached.
        cb.notifyAlertReached();
        HandlerUtils.waitForIdle(mHandlerThread, WAIT_TIMEOUT);

        // Verifies that polling is triggered by alert reached.
        provider.expectOnRequestStatsUpdate(0 /* unused */);
        // Verifies that global alert will be re-armed.
        provider.expectOnSetAlert(MB_IN_BYTES);
    }

    private void setCombineSubtypeEnabled(boolean enable) {
        when(mSettings.getCombineSubtypeEnabled()).thenReturn(enable);
        mHandler.post(() -> mContentObserver.onChange(false, Settings.Global
                    .getUriFor(Settings.Global.NETSTATS_COMBINE_SUBTYPE_ENABLED)));
        waitForIdle();
        if (enable) {
            verify(mNetworkStatsSubscriptionsMonitor).stop();
        } else {
            verify(mNetworkStatsSubscriptionsMonitor).start();
        }
    }

    @Test
    public void testDynamicWatchForNetworkRatTypeChanges() throws Exception {
        // Build 3G template, type unknown template to get stats while network type is unknown
        // and type all template to get the sum of all network type stats.
        final NetworkTemplate template3g =
                buildTemplateMobileWithRatType(null, TelephonyManager.NETWORK_TYPE_UMTS,
                METERED_YES);
        final NetworkTemplate templateUnknown =
                buildTemplateMobileWithRatType(null, TelephonyManager.NETWORK_TYPE_UNKNOWN,
                METERED_YES);
        final NetworkTemplate templateAll =
                buildTemplateMobileWithRatType(null, NETWORK_TYPE_ALL, METERED_YES);
        final NetworkStateSnapshot[] states =
                new NetworkStateSnapshot[]{buildMobileState(IMSI_1)};

        expectNetworkStatsSummary(buildEmptyStats());
        expectNetworkStatsUidDetail(buildEmptyStats());

        // 3G network comes online.
        setMobileRatTypeAndWaitForIdle(TelephonyManager.NETWORK_TYPE_UMTS);
        mService.notifyNetworkStatus(NETWORKS_MOBILE, states, getActiveIface(states),
                new UnderlyingNetworkInfo[0]);

        // Create some traffic.
        incrementCurrentTime(MINUTE_IN_MILLIS);
        expectNetworkStatsUidDetail(new NetworkStats(getElapsedRealtime(), 1)
                .addEntry(new NetworkStats.Entry(TEST_IFACE, UID_RED, SET_DEFAULT, TAG_NONE,
                        12L, 18L, 14L, 1L, 0L)));
        forcePollAndWaitForIdle();

        // Since CombineSubtypeEnabled is false by default in unit test, the generated traffic
        // will be split by RAT type. Verify 3G templates gets stats, while template with unknown
        // RAT type gets nothing, and template with NETWORK_TYPE_ALL gets all stats.
        assertUidTotal(template3g, UID_RED, 12L, 18L, 14L, 1L, 0);
        assertUidTotal(templateUnknown, UID_RED, 0L, 0L, 0L, 0L, 0);
        assertUidTotal(templateAll, UID_RED, 12L, 18L, 14L, 1L, 0);

        // Stop monitoring data usage per RAT type changes NetworkStatsService records data
        // to {@link TelephonyManager#NETWORK_TYPE_UNKNOWN}.
        setCombineSubtypeEnabled(true);

        // Call handleOnCollapsedRatTypeChanged manually to simulate the callback fired
        // when stopping monitor, this is needed by NetworkStatsService to trigger
        // handleNotifyNetworkStatus.
        mService.handleOnCollapsedRatTypeChanged();
        HandlerUtils.waitForIdle(mHandlerThread, WAIT_TIMEOUT);
        // Create some traffic.
        incrementCurrentTime(MINUTE_IN_MILLIS);
        // Append more traffic on existing snapshot.
        expectNetworkStatsUidDetail(new NetworkStats(getElapsedRealtime(), 1)
                .addEntry(new NetworkStats.Entry(TEST_IFACE, UID_RED, SET_DEFAULT, TAG_NONE,
                        12L + 4L, 18L + 4L, 14L + 3L, 1L + 1L, 0L))
                .addEntry(new NetworkStats.Entry(TEST_IFACE, UID_RED, SET_FOREGROUND, TAG_NONE,
                        35L, 29L, 7L, 11L, 1L)));
        forcePollAndWaitForIdle();

        // Verify 3G counters do not increase, while template with unknown RAT type gets new
        // traffic and template with NETWORK_TYPE_ALL gets all stats.
        assertUidTotal(template3g, UID_RED, 12L, 18L, 14L, 1L, 0);
        assertUidTotal(templateUnknown, UID_RED, 4L + 35L, 4L + 29L, 3L + 7L, 1L + 11L, 1);
        assertUidTotal(templateAll, UID_RED, 16L + 35L, 22L + 29L, 17L + 7L, 2L + 11L, 1);

        // Start monitoring data usage per RAT type changes and NetworkStatsService records data
        // by a granular subtype representative of the actual subtype
        setCombineSubtypeEnabled(false);

        mService.handleOnCollapsedRatTypeChanged();
        HandlerUtils.waitForIdle(mHandlerThread, WAIT_TIMEOUT);
        // Create some traffic.
        incrementCurrentTime(MINUTE_IN_MILLIS);
        // Append more traffic on existing snapshot.
        expectNetworkStatsUidDetail(new NetworkStats(getElapsedRealtime(), 1)
                .addEntry(new NetworkStats.Entry(TEST_IFACE, UID_RED, SET_DEFAULT, TAG_NONE,
                        22L, 26L, 19L, 5L, 0L))
                .addEntry(new NetworkStats.Entry(TEST_IFACE, UID_RED, SET_FOREGROUND, TAG_NONE,
                        35L, 29L, 7L, 11L, 1L)));
        forcePollAndWaitForIdle();

        // Verify traffic is split by RAT type, no increase on template with unknown RAT type
        // and template with NETWORK_TYPE_ALL gets all stats.
        assertUidTotal(template3g, UID_RED, 6L + 12L , 4L + 18L, 2L + 14L, 3L + 1L, 0);
        assertUidTotal(templateUnknown, UID_RED, 4L + 35L, 4L + 29L, 3L + 7L, 1L + 11L, 1);
        assertUidTotal(templateAll, UID_RED, 22L + 35L, 26L + 29L, 19L + 7L, 5L + 11L, 1);
    }

    @Test
    public void testOperationCount_nonDefault_traffic() throws Exception {
        // Pretend mobile network comes online, but wifi is the default network.
        expectDefaultSettings();
        NetworkStateSnapshot[] states = new NetworkStateSnapshot[]{
                buildWifiState(true /*isMetered*/, TEST_IFACE2), buildMobileState(IMSI_1)};
        expectNetworkStatsUidDetail(buildEmptyStats());
        mService.notifyNetworkStatus(NETWORKS_WIFI, states, getActiveIface(states),
                new UnderlyingNetworkInfo[0]);

        // Create some traffic on mobile network.
        incrementCurrentTime(HOUR_IN_MILLIS);
        expectNetworkStatsUidDetail(new NetworkStats(getElapsedRealtime(), 4)
                .insertEntry(TEST_IFACE, UID_RED, SET_DEFAULT, TAG_NONE, METERED_NO, ROAMING_NO,
                        DEFAULT_NETWORK_NO, 2L, 1L, 3L, 4L, 0L)
                .insertEntry(TEST_IFACE, UID_RED, SET_DEFAULT, TAG_NONE, METERED_NO, ROAMING_NO,
                        DEFAULT_NETWORK_YES, 1L, 3L, 2L, 1L, 0L)
                .insertEntry(TEST_IFACE, UID_RED, SET_DEFAULT, 0xF00D, 5L, 4L, 1L, 4L, 0L));
        // Increment operation count, which must have a specific tag.
        mService.incrementOperationCount(UID_RED, 0xF00D, 2);
        forcePollAndWaitForIdle();

        // Verify mobile summary is not changed by the operation count.
        final NetworkTemplate templateMobile =
                buildTemplateMobileWithRatType(null, NETWORK_TYPE_ALL, METERED_YES);
        final NetworkStats statsMobile = mSession.getSummaryForAllUid(
                templateMobile, Long.MIN_VALUE, Long.MAX_VALUE, true);
        assertValues(statsMobile, IFACE_ALL, UID_RED, SET_ALL, TAG_NONE, METERED_ALL, ROAMING_ALL,
                DEFAULT_NETWORK_ALL, 3L, 4L, 5L, 5L, 0);
        assertValues(statsMobile, IFACE_ALL, UID_RED, SET_ALL, 0xF00D, METERED_ALL, ROAMING_ALL,
                DEFAULT_NETWORK_ALL, 5L, 4L, 1L, 4L, 0);

        // Verify the operation count is blamed onto the default network.
        // TODO: Blame onto the default network is not very reasonable. Consider blame onto the
        //  network that generates the traffic.
        final NetworkTemplate templateWifi = buildTemplateWifiWildcard();
        final NetworkStats statsWifi = mSession.getSummaryForAllUid(
                templateWifi, Long.MIN_VALUE, Long.MAX_VALUE, true);
        assertValues(statsWifi, IFACE_ALL, UID_RED, SET_ALL, 0xF00D, METERED_ALL, ROAMING_ALL,
                DEFAULT_NETWORK_ALL, 0L, 0L, 0L, 0L, 2);
    }

    @Test
    public void testTetheringEventCallback_onUpstreamChanged() throws Exception {
        // Register custom provider and retrieve callback.
        final TestableNetworkStatsProviderBinder provider =
                new TestableNetworkStatsProviderBinder();
        final INetworkStatsProviderCallback cb =
                mService.registerNetworkStatsProvider("TEST-TETHERING-OFFLOAD", provider);
        assertNotNull(cb);
        provider.assertNoCallback();

        // Post upstream changed event, verify the service will pull for stats.
        mTetheringEventCallback.onUpstreamChanged(WIFI_NETWORK);
        provider.expectOnRequestStatsUpdate(0 /* unused */);
    }

    /**
     * Verify the service will throw exceptions if the template is location sensitive but
     * the permission is not granted.
     */
    @Test
    public void testEnforceTemplateLocationPermission() throws Exception {
        when(mLocationPermissionChecker.checkCallersLocationPermission(
                any(), any(), anyInt(), anyBoolean(), any())).thenReturn(false);
        initWifiStats(buildWifiState(true, TEST_IFACE, IMSI_1));
        assertThrows(SecurityException.class, () ->
                assertNetworkTotal(sTemplateWifi, 0L, 0L, 0L, 0L, 0));
        // Templates w/o wifi network keys can query stats as usual.
        assertNetworkTotal(sTemplateCarrierWifi1, 0L, 0L, 0L, 0L, 0);
        assertNetworkTotal(sTemplateImsi1, 0L, 0L, 0L, 0L, 0);

        when(mLocationPermissionChecker.checkCallersLocationPermission(
                any(), any(), anyInt(), anyBoolean(), any())).thenReturn(true);
        assertNetworkTotal(sTemplateCarrierWifi1, 0L, 0L, 0L, 0L, 0);
        assertNetworkTotal(sTemplateWifi, 0L, 0L, 0L, 0L, 0);
        assertNetworkTotal(sTemplateImsi1, 0L, 0L, 0L, 0L, 0);
    }

    /**
     * Verify the service will perform data migration process can be controlled by the device flag.
     */
    @Test
    public void testDataMigration() throws Exception {
        assertStatsFilesExist(false);
        expectDefaultSettings();

        NetworkStateSnapshot[] states = new NetworkStateSnapshot[] {buildWifiState()};

        mService.notifyNetworkStatus(NETWORKS_WIFI, states, getActiveIface(states),
                new UnderlyingNetworkInfo[0]);

        // modify some number on wifi, and trigger poll event
        incrementCurrentTime(HOUR_IN_MILLIS);
        // expectDefaultSettings();
        expectNetworkStatsSummary(new NetworkStats(getElapsedRealtime(), 1)
                .insertEntry(TEST_IFACE, 1024L, 8L, 2048L, 16L));
        expectNetworkStatsUidDetail(new NetworkStats(getElapsedRealtime(), 2)
                .insertEntry(TEST_IFACE, UID_RED, SET_DEFAULT, TAG_NONE, 512L, 4L, 256L, 2L, 0L)
                .insertEntry(TEST_IFACE, UID_RED, SET_DEFAULT, 0xFAAD, 256L, 2L, 128L, 1L, 0L)
                .insertEntry(TEST_IFACE, UID_RED, SET_FOREGROUND, TAG_NONE, 512L, 4L, 256L, 2L, 0L)
                .insertEntry(TEST_IFACE, UID_RED, SET_FOREGROUND, 0xFAAD, 256L, 2L, 128L, 1L, 0L)
                .insertEntry(TEST_IFACE, UID_BLUE, SET_DEFAULT, TAG_NONE, 128L, 1L, 128L, 1L, 0L));

        mService.noteUidForeground(UID_RED, false);
        verify(mUidCounterSetMap, never()).deleteEntry(any());
        mService.incrementOperationCount(UID_RED, 0xFAAD, 4);
        mService.noteUidForeground(UID_RED, true);
        verify(mUidCounterSetMap).updateEntry(
                eq(new U32(UID_RED)), eq(new U8((short) SET_FOREGROUND)));
        mService.incrementOperationCount(UID_RED, 0xFAAD, 6);

        forcePollAndWaitForIdle();
        // Simulate shutdown to force persisting data
        mServiceContext.sendBroadcast(new Intent(Intent.ACTION_SHUTDOWN));
        assertStatsFilesExist(true);

        // Move the files to the legacy directory to simulate an import from old data
        for (File f : mStatsDir.listFiles()) {
            Files.move(f.toPath(), mLegacyStatsDir.toPath().resolve(f.getName()));
        }
        assertStatsFilesExist(false);

        // Fetch the stats from the legacy files and set platform stats collection to be identical
        mPlatformNetworkStatsCollection.put(PREFIX_DEV,
                getLegacyCollection(PREFIX_DEV, false /* includeTags */));
        mPlatformNetworkStatsCollection.put(PREFIX_XT,
                getLegacyCollection(PREFIX_XT, false /* includeTags */));
        mPlatformNetworkStatsCollection.put(PREFIX_UID,
                getLegacyCollection(PREFIX_UID, false /* includeTags */));
        mPlatformNetworkStatsCollection.put(PREFIX_UID_TAG,
                getLegacyCollection(PREFIX_UID_TAG, true /* includeTags */));

        // Mock zero usage and boot through serviceReady(), verify there is no imported data.
        expectDefaultSettings();
        expectNetworkStatsUidDetail(buildEmptyStats());
        expectSystemReady();
        mService.systemReady();
        assertStatsFilesExist(false);

        // Set the flag and reboot, verify the imported data is not there until next boot.
        mStoreFilesInApexData = true;
        mImportLegacyTargetAttempts = 3;
        mServiceContext.sendBroadcast(new Intent(Intent.ACTION_SHUTDOWN));
        assertStatsFilesExist(false);

        // Boot through systemReady() again.
        expectDefaultSettings();
        expectNetworkStatsUidDetail(buildEmptyStats());
        expectSystemReady();
        mService.systemReady();

        // After systemReady(), the service should have historical stats loaded again.
        // Thus, verify
        //  1. The stats are absorbed by the recorder.
        //  2. The imported data are persisted.
        //  3. The attempts count is set to target attempts count to indicate a successful
        //     migration.
        assertNetworkTotal(sTemplateWifi, 1024L, 8L, 2048L, 16L, 0);
        assertStatsFilesExist(true);
        verify(mImportLegacyAttemptsCounter).set(3);
        verify(mImportLegacySuccessesCounter).set(1);

        // TODO: Verify upgrading with Exception won't damege original data and
        //  will decrease the retry counter by 1.
    }

    @Test
    public void testDataMigration_differentFromFallback() throws Exception {
        assertStatsFilesExist(false);
        expectDefaultSettings();

        NetworkStateSnapshot[] states = new NetworkStateSnapshot[]{buildWifiState()};

        mService.notifyNetworkStatus(NETWORKS_WIFI, states, getActiveIface(states),
                new UnderlyingNetworkInfo[0]);

        // modify some number on wifi, and trigger poll event
        incrementCurrentTime(HOUR_IN_MILLIS);
        expectNetworkStatsSummary(new NetworkStats(getElapsedRealtime(), 1)
                .insertEntry(TEST_IFACE, 1024L, 8L, 2048L, 16L));
        expectNetworkStatsUidDetail(new NetworkStats(getElapsedRealtime(), 1)
                .insertEntry(TEST_IFACE, UID_BLUE, SET_DEFAULT, TAG_NONE, 128L, 1L, 128L, 1L, 0L));
        forcePollAndWaitForIdle();
        // Simulate shutdown to force persisting data
        mServiceContext.sendBroadcast(new Intent(Intent.ACTION_SHUTDOWN));
        assertStatsFilesExist(true);

        // Move the files to the legacy directory to simulate an import from old data
        for (File f : mStatsDir.listFiles()) {
            Files.move(f.toPath(), mLegacyStatsDir.toPath().resolve(f.getName()));
        }
        assertStatsFilesExist(false);

        // Prepare some unexpected data.
        final NetworkIdentity testWifiIdent = new NetworkIdentity.Builder().setType(TYPE_WIFI)
                .setWifiNetworkKey(TEST_WIFI_NETWORK_KEY).build();
        final NetworkStatsCollection.Key unexpectedUidAllkey = new NetworkStatsCollection.Key(
                Set.of(testWifiIdent), UID_ALL, SET_DEFAULT, 0);
        final NetworkStatsCollection.Key unexpectedUidBluekey = new NetworkStatsCollection.Key(
                Set.of(testWifiIdent), UID_BLUE, SET_DEFAULT, 0);
        final NetworkStatsHistory unexpectedHistory = new NetworkStatsHistory
                .Builder(965L /* bucketDuration */, 1)
                .addEntry(new NetworkStatsHistory.Entry(TEST_START, 3L, 55L, 4L, 31L, 10L, 5L))
                .build();

        // Simulate the platform stats collection somehow is different from what is read from
        // the fallback method. The service should read them as is. This usually happens when an
        // OEM has changed the implementation of NetworkStatsDataMigrationUtils inside the platform.
        final NetworkStatsCollection summaryCollection =
                getLegacyCollection(PREFIX_XT, false /* includeTags */);
        summaryCollection.recordHistory(unexpectedUidAllkey, unexpectedHistory);
        final NetworkStatsCollection uidCollection =
                getLegacyCollection(PREFIX_UID, false /* includeTags */);
        uidCollection.recordHistory(unexpectedUidBluekey, unexpectedHistory);
        mPlatformNetworkStatsCollection.put(PREFIX_DEV, summaryCollection);
        mPlatformNetworkStatsCollection.put(PREFIX_XT, summaryCollection);
        mPlatformNetworkStatsCollection.put(PREFIX_UID, uidCollection);
        mPlatformNetworkStatsCollection.put(PREFIX_UID_TAG,
                getLegacyCollection(PREFIX_UID_TAG, true /* includeTags */));

        // Mock zero usage and boot through serviceReady(), verify there is no imported data.
        expectDefaultSettings();
        expectNetworkStatsUidDetail(buildEmptyStats());
        expectSystemReady();
        mService.systemReady();
        assertStatsFilesExist(false);

        // Set the flag and reboot, verify the imported data is not there until next boot.
        mStoreFilesInApexData = true;
        mImportLegacyTargetAttempts = 3;
        mServiceContext.sendBroadcast(new Intent(Intent.ACTION_SHUTDOWN));
        assertStatsFilesExist(false);

        // Boot through systemReady() again.
        expectDefaultSettings();
        expectNetworkStatsUidDetail(buildEmptyStats());
        expectSystemReady();
        mService.systemReady();

        // Verify the result read from public API matches the result returned from the importer.
        assertNetworkTotal(sTemplateWifi, 1024L + 55L, 8L + 4L, 2048L + 31L, 16L + 10L, 0 + 5);
        assertUidTotal(sTemplateWifi, UID_BLUE,
                128L + 55L, 1L + 4L, 128L + 31L, 1L + 10L, 0 + 5);
        assertStatsFilesExist(true);
        verify(mImportLegacyAttemptsCounter).set(3);
        verify(mImportLegacySuccessesCounter).set(1);
    }

    @Test
    public void testShouldRunComparison() {
        for (Boolean isDebuggable : Set.of(Boolean.TRUE, Boolean.FALSE)) {
            mIsDebuggable = isDebuggable;
            // Verify return false regardless of the device is debuggable.
            doReturn(0).when(mResources)
                    .getInteger(R.integer.config_netstats_validate_import);
            assertShouldRunComparison(false, isDebuggable);
            // Verify return true regardless of the device is debuggable.
            doReturn(1).when(mResources)
                    .getInteger(R.integer.config_netstats_validate_import);
            assertShouldRunComparison(true, isDebuggable);
            // Verify return true iff the device is debuggable.
            for (int testValue : Set.of(-1, 2)) {
                doReturn(testValue).when(mResources)
                        .getInteger(R.integer.config_netstats_validate_import);
                assertShouldRunComparison(isDebuggable, isDebuggable);
            }
        }
    }

    private void assertShouldRunComparison(boolean expected, boolean isDebuggable) {
        assertEquals("shouldRunComparison (debuggable=" + isDebuggable + "): ",
                expected, mService.shouldRunComparison());
    }

    private NetworkStatsRecorder makeTestRecorder(File directory, String prefix, Config config,
            boolean includeTags, boolean wipeOnError) {
        final NetworkStats.NonMonotonicObserver observer =
                mock(NetworkStats.NonMonotonicObserver.class);
        final DropBoxManager dropBox = mock(DropBoxManager.class);
        return new NetworkStatsRecorder(new FileRotator(
                directory, prefix, config.rotateAgeMillis, config.deleteAgeMillis),
                observer, dropBox, prefix, config.bucketDuration, includeTags, wipeOnError);
    }

    private NetworkStatsCollection getLegacyCollection(String prefix, boolean includeTags) {
        final NetworkStatsRecorder recorder = makeTestRecorder(mLegacyStatsDir, prefix,
                mSettings.getDevConfig(), includeTags, false);
        return recorder.getOrLoadCompleteLocked();
    }

    private void assertNetworkTotal(NetworkTemplate template, long rxBytes, long rxPackets,
            long txBytes, long txPackets, int operations) throws Exception {
        assertNetworkTotal(template, Long.MIN_VALUE, Long.MAX_VALUE, rxBytes, rxPackets, txBytes,
                txPackets, operations);
    }

    private void assertNetworkTotal(NetworkTemplate template, long start, long end, long rxBytes,
            long rxPackets, long txBytes, long txPackets, int operations) throws Exception {
        // verify history API
        final NetworkStatsHistory history =
                mSession.getHistoryIntervalForNetwork(template, FIELD_ALL, start, end);
        assertValues(history, start, end, rxBytes, rxPackets, txBytes, txPackets, operations);

        // verify summary API
        final NetworkStats stats = mSession.getSummaryForNetwork(template, start, end);
        assertValues(stats, IFACE_ALL, UID_ALL, SET_ALL, TAG_NONE, METERED_ALL, ROAMING_ALL,
                DEFAULT_NETWORK_ALL,  rxBytes, rxPackets, txBytes, txPackets, operations);
    }

    private void assertUidTotal(NetworkTemplate template, int uid, long rxBytes, long rxPackets,
            long txBytes, long txPackets, int operations) throws Exception {
        assertUidTotal(template, uid, SET_ALL, METERED_ALL, ROAMING_ALL, DEFAULT_NETWORK_ALL,
                rxBytes, rxPackets, txBytes, txPackets, operations);
    }

    private void assertUidTotal(NetworkTemplate template, int uid, int set, int metered,
            int roaming, int defaultNetwork, long rxBytes, long rxPackets, long txBytes,
            long txPackets, int operations) throws Exception {
        // verify history API
        final NetworkStatsHistory history = mSession.getHistoryForUid(
                template, uid, set, TAG_NONE, FIELD_ALL);
        assertValues(history, Long.MIN_VALUE, Long.MAX_VALUE, rxBytes, rxPackets, txBytes,
                txPackets, operations);

        // verify summary API
        final NetworkStats stats = mSession.getSummaryForAllUid(
                template, Long.MIN_VALUE, Long.MAX_VALUE, false);
        assertValues(stats, IFACE_ALL, uid, set, TAG_NONE, metered, roaming, defaultNetwork,
                rxBytes, rxPackets, txBytes, txPackets, operations);
    }

    private void expectSystemReady() throws Exception {
        expectNetworkStatsSummary(buildEmptyStats());
    }

    private String getActiveIface(NetworkStateSnapshot... states) throws Exception {
        if (states == null || states.length == 0 || states[0].getLinkProperties() == null) {
            return null;
        }
        return states[0].getLinkProperties().getInterfaceName();
    }

    // TODO: These expect* methods are used to have NetworkStatsService returns the given stats
    //       instead of expecting anything. Therefore, these methods should be renamed properly.
    private void expectNetworkStatsSummary(NetworkStats summary) throws Exception {
        expectNetworkStatsSummaryDev(summary.clone());
        expectNetworkStatsSummaryXt(summary.clone());
    }

    private void expectNetworkStatsSummaryDev(NetworkStats summary) throws Exception {
        when(mStatsFactory.readNetworkStatsSummaryDev()).thenReturn(summary);
    }

    private void expectNetworkStatsSummaryXt(NetworkStats summary) throws Exception {
        when(mStatsFactory.readNetworkStatsSummaryXt()).thenReturn(summary);
    }

    private void expectNetworkStatsUidDetail(NetworkStats detail) throws Exception {
        final TetherStatsParcel[] tetherStatsParcels = {};
        expectNetworkStatsUidDetail(detail, tetherStatsParcels);
    }

    private void expectNetworkStatsUidDetail(NetworkStats detail,
            TetherStatsParcel[] tetherStatsParcels) throws Exception {
        when(mStatsFactory.readNetworkStatsDetail(UID_ALL, INTERFACES_ALL, TAG_ALL))
                .thenReturn(detail);

        // also include tethering details, since they are folded into UID
        when(mNetd.tetherGetStats()).thenReturn(tetherStatsParcels);
    }

    private void expectDefaultSettings() throws Exception {
        expectSettings(0L, HOUR_IN_MILLIS, WEEK_IN_MILLIS);
    }

    private void expectSettings(long persistBytes, long bucketDuration, long deleteAge)
            throws Exception {
        when(mSettings.getPollInterval()).thenReturn(HOUR_IN_MILLIS);
        when(mSettings.getPollDelay()).thenReturn(0L);
        when(mSettings.getSampleEnabled()).thenReturn(true);
        when(mSettings.getCombineSubtypeEnabled()).thenReturn(false);

        final Config config = new Config(bucketDuration, deleteAge, deleteAge);
        when(mSettings.getDevConfig()).thenReturn(config);
        when(mSettings.getXtConfig()).thenReturn(config);
        when(mSettings.getUidConfig()).thenReturn(config);
        when(mSettings.getUidTagConfig()).thenReturn(config);

        when(mSettings.getGlobalAlertBytes(anyLong())).thenReturn(MB_IN_BYTES);
        when(mSettings.getDevPersistBytes(anyLong())).thenReturn(MB_IN_BYTES);
        when(mSettings.getXtPersistBytes(anyLong())).thenReturn(MB_IN_BYTES);
        when(mSettings.getUidPersistBytes(anyLong())).thenReturn(MB_IN_BYTES);
        when(mSettings.getUidTagPersistBytes(anyLong())).thenReturn(MB_IN_BYTES);
    }

    private void assertStatsFilesExist(boolean exist) {
        if (exist) {
            assertTrue(mStatsDir.list().length > 0);
        } else {
            assertTrue(mStatsDir.list().length == 0);
        }
    }

    private static void assertValues(NetworkStatsHistory stats, long start, long end, long rxBytes,
            long rxPackets, long txBytes, long txPackets, int operations) {
        final NetworkStatsHistory.Entry entry = stats.getValues(start, end, null);
        assertEquals("unexpected rxBytes", rxBytes, entry.rxBytes);
        assertEquals("unexpected rxPackets", rxPackets, entry.rxPackets);
        assertEquals("unexpected txBytes", txBytes, entry.txBytes);
        assertEquals("unexpected txPackets", txPackets, entry.txPackets);
        assertEquals("unexpected operations", operations, entry.operations);
    }

    private static NetworkStateSnapshot buildWifiState() {
        return buildWifiState(false, TEST_IFACE, null);
    }

    private static NetworkStateSnapshot buildWifiState(boolean isMetered, @NonNull String iface) {
        return buildWifiState(isMetered, iface, null);
    }

    private static NetworkStateSnapshot buildWifiState(boolean isMetered, @NonNull String iface,
            String subscriberId) {
        final LinkProperties prop = new LinkProperties();
        prop.setInterfaceName(iface);
        final NetworkCapabilities capabilities = new NetworkCapabilities();
        capabilities.setCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED, !isMetered);
        capabilities.setCapability(NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING, true);
        capabilities.addTransportType(NetworkCapabilities.TRANSPORT_WIFI);
        capabilities.setTransportInfo(sWifiInfo);
        return new NetworkStateSnapshot(WIFI_NETWORK, capabilities, prop, subscriberId, TYPE_WIFI);
    }

    private static NetworkStateSnapshot buildMobileState(String subscriberId) {
        return buildMobileState(TEST_IFACE, subscriberId, false /* isTemporarilyNotMetered */,
                false /* isRoaming */);
    }

    private static NetworkStateSnapshot buildMobileState(String iface, String subscriberId,
            boolean isTemporarilyNotMetered, boolean isRoaming) {
        final LinkProperties prop = new LinkProperties();
        prop.setInterfaceName(iface);
        final NetworkCapabilities capabilities = new NetworkCapabilities();

        if (isTemporarilyNotMetered) {
            capabilities.addCapability(
                    NetworkCapabilities.NET_CAPABILITY_TEMPORARILY_NOT_METERED);
        }
        capabilities.setCapability(NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING, !isRoaming);
        capabilities.addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR);
        return new NetworkStateSnapshot(
                MOBILE_NETWORK, capabilities, prop, subscriberId, TYPE_MOBILE);
    }

    private NetworkStats buildEmptyStats() {
        return new NetworkStats(getElapsedRealtime(), 0);
    }

    private static NetworkStateSnapshot buildOemManagedMobileState(
            String subscriberId, boolean isRoaming, int[] oemNetCapabilities) {
        final LinkProperties prop = new LinkProperties();
        prop.setInterfaceName(TEST_IFACE);
        final NetworkCapabilities capabilities = new NetworkCapabilities();
        capabilities.setCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED, false);
        capabilities.setCapability(NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING, !isRoaming);
        for (int nc : oemNetCapabilities) {
            capabilities.setCapability(nc, true);
        }
        capabilities.addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR);
        return new NetworkStateSnapshot(MOBILE_NETWORK, capabilities, prop, subscriberId,
                TYPE_MOBILE);
    }

    private static NetworkStateSnapshot buildImsState(
            String subscriberId, int subId, String ifaceName) {
        final LinkProperties prop = new LinkProperties();
        prop.setInterfaceName(ifaceName);
        final NetworkCapabilities capabilities = new NetworkCapabilities();
        capabilities.setCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED, true);
        capabilities.setCapability(NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING, true);
        capabilities.setCapability(NetworkCapabilities.NET_CAPABILITY_IMS, true);
        capabilities.addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR);
        capabilities.setNetworkSpecifier(new TelephonyNetworkSpecifier(subId));
        return new NetworkStateSnapshot(
                MOBILE_NETWORK, capabilities, prop, subscriberId, TYPE_MOBILE);
    }

    private long getElapsedRealtime() {
        return mElapsedRealtime;
    }

    private long startTimeMillis() {
        return TEST_START;
    }

    private long currentTimeMillis() {
        return startTimeMillis() + mElapsedRealtime;
    }

    private void incrementCurrentTime(long duration) {
        mElapsedRealtime += duration;
    }

    private void forcePollAndWaitForIdle() {
        mServiceContext.sendBroadcast(new Intent(ACTION_NETWORK_STATS_POLL));
        waitForIdle();
    }

    private void waitForIdle() {
        HandlerUtils.waitForIdle(mHandlerThread, WAIT_TIMEOUT);
    }

    private boolean cookieTagMapContainsUid(int uid) throws ErrnoException {
        final AtomicBoolean found = new AtomicBoolean();
        mCookieTagMap.forEach((k, v) -> {
            if (v.uid == uid) {
                found.set(true);
            }
        });
        return found.get();
    }

    private static <K extends StatsMapKey, V extends StatsMapValue> boolean statsMapContainsUid(
            TestBpfMap<K, V> map, int uid) throws ErrnoException {
        final AtomicBoolean found = new AtomicBoolean();
        map.forEach((k, v) -> {
            if (k.uid == uid) {
                found.set(true);
            }
        });
        return found.get();
    }

    private void initBpfMapsWithTagData(int uid) throws ErrnoException {
        // key needs to be unique, use some offset from uid.
        mCookieTagMap.insertEntry(new CookieTagMapKey(1000 + uid), new CookieTagMapValue(uid, 1));
        mCookieTagMap.insertEntry(new CookieTagMapKey(2000 + uid), new CookieTagMapValue(uid, 2));

        mStatsMapA.insertEntry(new StatsMapKey(uid, 1, 0, 10), new StatsMapValue(5, 5000, 3, 3000));
        mStatsMapA.insertEntry(new StatsMapKey(uid, 2, 0, 10), new StatsMapValue(5, 5000, 3, 3000));

        mStatsMapB.insertEntry(new StatsMapKey(uid, 1, 0, 10), new StatsMapValue(0, 0, 0, 0));

        mAppUidStatsMap.insertEntry(new UidStatsMapKey(uid), new StatsMapValue(10, 10000, 6, 6000));

        mUidCounterSetMap.insertEntry(new U32(uid), new U8((short) 1));

        assertTrue(cookieTagMapContainsUid(uid));
        assertTrue(statsMapContainsUid(mStatsMapA, uid));
        assertTrue(statsMapContainsUid(mStatsMapB, uid));
        assertTrue(mAppUidStatsMap.containsKey(new UidStatsMapKey(uid)));
        assertTrue(mUidCounterSetMap.containsKey(new U32(uid)));
    }

    @Test
    public void testRemovingUidRemovesTagDataForUid() throws ErrnoException {
        initBpfMapsWithTagData(UID_BLUE);
        initBpfMapsWithTagData(UID_RED);

        final Intent intent = new Intent(ACTION_UID_REMOVED);
        intent.putExtra(EXTRA_UID, UID_BLUE);
        mServiceContext.sendBroadcast(intent);

        // assert that all UID_BLUE related tag data has been removed from the maps.
        assertFalse(cookieTagMapContainsUid(UID_BLUE));
        assertFalse(statsMapContainsUid(mStatsMapA, UID_BLUE));
        assertFalse(statsMapContainsUid(mStatsMapB, UID_BLUE));
        assertFalse(mAppUidStatsMap.containsKey(new UidStatsMapKey(UID_BLUE)));
        assertFalse(mUidCounterSetMap.containsKey(new U32(UID_BLUE)));

        // assert that UID_RED related tag data is still in the maps.
        assertTrue(cookieTagMapContainsUid(UID_RED));
        assertTrue(statsMapContainsUid(mStatsMapA, UID_RED));
        assertTrue(statsMapContainsUid(mStatsMapB, UID_RED));
        assertTrue(mAppUidStatsMap.containsKey(new UidStatsMapKey(UID_RED)));
        assertTrue(mUidCounterSetMap.containsKey(new U32(UID_RED)));
    }
}
