/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.server.wifi;

import static android.content.Intent.ACTION_SCREEN_OFF;
import static android.content.Intent.ACTION_SCREEN_ON;

import static com.android.server.wifi.ActiveModeManager.ROLE_CLIENT_PRIMARY;
import static com.android.server.wifi.ActiveModeManager.ROLE_CLIENT_SCAN_ONLY;
import static com.android.server.wifi.ActiveModeManager.ROLE_CLIENT_SECONDARY_LONG_LIVED;
import static com.android.server.wifi.ActiveModeManager.ROLE_CLIENT_SECONDARY_TRANSIENT;
import static com.android.server.wifi.ClientModeImpl.WIFI_WORK_SOURCE;
import static com.android.server.wifi.WifiConfigurationTestUtil.generateWifiConfig;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyList;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.anyObject;
import static org.mockito.Mockito.anySet;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.validateMockitoUsage;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import android.app.AlarmManager;
import android.app.test.MockAnswerUtil.AnswerWithArguments;
import android.app.test.TestAlarmManager;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.MacAddress;
import android.net.wifi.IPnoScanResultsCallback;
import android.net.wifi.ScanResult;
import android.net.wifi.ScanResult.InformationElement;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiContext;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiNetworkSuggestion;
import android.net.wifi.WifiScanner;
import android.net.wifi.WifiScanner.PnoScanListener;
import android.net.wifi.WifiScanner.PnoSettings;
import android.net.wifi.WifiScanner.ScanData;
import android.net.wifi.WifiScanner.ScanListener;
import android.net.wifi.WifiScanner.ScanSettings;
import android.net.wifi.WifiSsid;
import android.net.wifi.hotspot2.PasspointConfiguration;
import android.net.wifi.util.ScanResultUtil;
import android.os.Handler;
import android.os.IBinder;
import android.os.IPowerManager;
import android.os.IThermalService;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.os.Process;
import android.os.SystemClock;
import android.os.WorkSource;
import android.os.test.TestLooper;
import android.util.ArraySet;
import android.util.LocalLog;

import androidx.test.filters.SmallTest;

import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.modules.utils.build.SdkLevel;
import com.android.server.wifi.ActiveModeWarden.ExternalClientModeManagerRequestListener;
import com.android.server.wifi.hotspot2.PasspointManager;
import com.android.server.wifi.proto.nano.WifiMetricsProto;
import com.android.server.wifi.util.LruConnectionTracker;
import com.android.wifi.resources.R;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatcher;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

/**
 * Unit tests for {@link com.android.server.wifi.WifiConnectivityManager}.
 */
@SmallTest
public class WifiConnectivityManagerTest extends WifiBaseTest {
    /**
     * Called before each test
     */
    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mResources = new MockResources();
        setUpResources(mResources);
        mAlarmManager = new TestAlarmManager();
        mContext = mockContext();
        mLocalLog = new LocalLog(512);
        setupMockForClientModeManager(mPrimaryClientModeManager);
        mWifiConfigManager = mockWifiConfigManager();
        mWifiInfo = getWifiInfo();
        mScanData = mockScanData();
        mWifiScanner = mockWifiScanner();
        mWifiConnectivityHelper = mockWifiConnectivityHelper();
        mWifiNS = mockWifiNetworkSelector();
        mLooper = new TestLooper(mClock::getElapsedSinceBootMillis);
        mTestHandler = new TestHandler(mLooper.getLooper());
        when(mContext.getSystemService(WifiScanner.class)).thenReturn(mWifiScanner);
        when(mWifiNetworkSuggestionsManager.retrieveHiddenNetworkList(anyBoolean()))
                .thenReturn(new ArrayList<>());
        when(mWifiNetworkSuggestionsManager.getAllApprovedNetworkSuggestions())
                .thenReturn(new HashSet<>());
        when(mPasspointManager.getProviderConfigs(anyInt(), anyBoolean()))
                .thenReturn(new ArrayList<>());
        mPowerManagerService = mock(IPowerManager.class);
        PowerManager powerManager =
                new PowerManager(mContext, mPowerManagerService, mock(IThermalService.class),
                        new Handler());
        when(mContext.getSystemService(PowerManager.class)).thenReturn(powerManager);
        when(powerManager.isInteractive()).thenReturn(false);
        when(mPrimaryClientModeManager.getRole()).thenReturn(ActiveModeManager.ROLE_CLIENT_PRIMARY);
        when(mPrimaryClientModeManager.syncRequestConnectionInfo()).thenReturn(mWifiInfo);
        when(mActiveModeWarden.getPrimaryClientModeManager()).thenReturn(mPrimaryClientModeManager);
        doAnswer(new AnswerWithArguments() {
            public void answer(ExternalClientModeManagerRequestListener listener,
                    WorkSource requestorWs, String ssid, String bssid) {
                listener.onAnswer(mPrimaryClientModeManager);
            }
        }).when(mActiveModeWarden).requestSecondaryTransientClientModeManager(
                any(), eq(ActiveModeWarden.INTERNAL_REQUESTOR_WS), any(), any());
        doAnswer(new AnswerWithArguments() {
            public void answer(ExternalClientModeManagerRequestListener listener,
                    WorkSource requestorWs, String ssid, String bssid) {
                listener.onAnswer(mPrimaryClientModeManager);
            }
        }).when(mActiveModeWarden).requestSecondaryLongLivedClientModeManager(
                any(), any(), any(), any());

        mWifiConnectivityManager = createConnectivityManager();
        mWifiConnectivityManager.setTrustedConnectionAllowed(true);
        mWifiConnectivityManager.enableVerboseLogging(true);
        setWifiEnabled(true);
        when(mClock.getElapsedSinceBootMillis()).thenReturn(CURRENT_SYSTEM_TIME_MS);
        when(mWifiLastResortWatchdog.shouldIgnoreBssidUpdate(anyString())).thenReturn(false);
        mLruConnectionTracker = new LruConnectionTracker(100, mContext);
        Comparator<WifiConfiguration> comparator =
                Comparator.comparingInt(mLruConnectionTracker::getAgeIndexOfNetwork);
        when(mWifiConfigManager.getScanListComparator()).thenReturn(comparator);

        // Need to mock WifiInjector since some code used in WifiConnectivityManager calls
        // WifiInjector.getInstance().
        mSession = ExtendedMockito.mockitoSession()
                .strictness(Strictness.LENIENT)
                .mockStatic(WifiInjector.class, withSettings().lenient())
                .startMocking();
        WifiInjector wifiInjector = mock(WifiInjector.class);
        when(wifiInjector.getActiveModeWarden()).thenReturn(mActiveModeWarden);
        when(wifiInjector.getWifiGlobals()).thenReturn(mWifiGlobals);
        lenient().when(WifiInjector.getInstance()).thenReturn(wifiInjector);
    }

    private void setUpResources(MockResources resources) {
        resources.setBoolean(
                R.bool.config_wifi_framework_enable_associated_network_selection, true);
        resources.setInteger(
                R.integer.config_wifi_framework_wifi_score_good_rssi_threshold_24GHz, -60);
        resources.setInteger(
                R.integer.config_wifiFrameworkMinPacketPerSecondActiveTraffic, 16);
        resources.setIntArray(
                R.array.config_wifiConnectedScanIntervalScheduleSec,
                VALID_CONNECTED_SINGLE_SCAN_SCHEDULE_SEC);
        resources.setIntArray(
                R.array.config_wifiDisconnectedScanIntervalScheduleSec,
                VALID_DISCONNECTED_SINGLE_SCAN_SCHEDULE_SEC);
        resources.setIntArray(R.array.config_wifiConnectedScanType,
                VALID_CONNECTED_SINGLE_SCAN_TYPE);
        resources.setIntArray(R.array.config_wifiDisconnectedScanType,
                VALID_DISCONNECTED_SINGLE_SCAN_TYPE);
        resources.setIntArray(
                R.array.config_wifiSingleSavedNetworkConnectedScanIntervalScheduleSec,
                SCHEDULE_EMPTY_SEC);
        resources.setInteger(
                R.integer.config_wifiHighMovementNetworkSelectionOptimizationScanDelayMs,
                HIGH_MVMT_SCAN_DELAY_MS);
        resources.setInteger(
                R.integer.config_wifiHighMovementNetworkSelectionOptimizationRssiDelta,
                HIGH_MVMT_RSSI_DELTA);
        resources.setInteger(R.integer.config_wifiInitialPartialScanChannelCacheAgeMins,
                CHANNEL_CACHE_AGE_MINS);
        resources.setInteger(R.integer.config_wifiMovingPnoScanIntervalMillis,
                MOVING_PNO_SCAN_INTERVAL_MILLIS);
        resources.setInteger(R.integer.config_wifiStationaryPnoScanIntervalMillis,
                STATIONARY_PNO_SCAN_INTERVAL_MILLIS);
        resources.setInteger(R.integer.config_wifiPnoScanLowRssiNetworkRetryStartDelaySec,
                LOW_RSSI_NETWORK_RETRY_START_DELAY_SEC);
        resources.setInteger(R.integer.config_wifiPnoScanLowRssiNetworkRetryMaxDelaySec,
                LOW_RSSI_NETWORK_RETRY_MAX_DELAY_SEC);
        resources.setBoolean(R.bool.config_wifiEnable6ghzPscScanning, true);
        resources.setBoolean(R.bool.config_wifiUseHalApiToDisableFwRoaming, true);
    }

    /**
     * Called after each test
     */
    @After
    public void cleanup() {
        verify(mScoringParams, atLeast(0)).getEntryRssi(anyInt());
        verifyNoMoreInteractions(mScoringParams);
        validateMockitoUsage();
        if (mSession != null) {
            mSession.finishMocking();
        }
    }

    private WifiContext mContext;
    private TestAlarmManager mAlarmManager;
    private TestLooper mLooper;
    private TestHandler mTestHandler;
    private WifiConnectivityManager mWifiConnectivityManager;
    private WifiNetworkSelector mWifiNS;
    private WifiScanner mWifiScanner;
    private WifiConnectivityHelper mWifiConnectivityHelper;
    private ScanData mScanData;
    private WifiConfigManager mWifiConfigManager;
    private WifiInfo mWifiInfo;
    private LocalLog mLocalLog;
    private LruConnectionTracker mLruConnectionTracker;
    @Mock private Clock mClock;
    @Mock private WifiLastResortWatchdog mWifiLastResortWatchdog;
    @Mock private OpenNetworkNotifier mOpenNetworkNotifier;
    @Mock private WifiMetrics mWifiMetrics;
    @Mock private WifiNetworkSuggestionsManager mWifiNetworkSuggestionsManager;
    @Mock private WifiBlocklistMonitor mWifiBlocklistMonitor;
    @Mock private WifiChannelUtilization mWifiChannelUtilization;
    @Mock private ScoringParams mScoringParams;
    @Mock private WifiScoreCard mWifiScoreCard;
    @Mock private PasspointManager mPasspointManager;
    @Mock private FrameworkFacade mFacade;
    @Mock private MultiInternetManager mMultiInternetManager;
    @Mock private WifiScoreCard.PerNetwork mPerNetwork;
    @Mock private WifiScoreCard.PerNetwork mPerNetwork1;
    @Mock private PasspointConfiguration mPasspointConfiguration;
    @Mock private WifiConfiguration mSuggestionConfig;
    @Mock private WifiNetworkSuggestion mWifiNetworkSuggestion;
    @Mock private IPowerManager mPowerManagerService;
    @Mock private DeviceConfigFacade mDeviceConfigFacade;
    @Mock private ActiveModeWarden mActiveModeWarden;
    @Mock private ConcreteClientModeManager mPrimaryClientModeManager;
    @Mock private ConcreteClientModeManager mSecondaryClientModeManager;
    @Mock private WifiGlobals mWifiGlobals;
    @Mock private ExternalPnoScanRequestManager mExternalPnoScanRequestManager;
    @Mock WifiCandidates.Candidate mCandidate1;
    @Mock WifiCandidates.Candidate mCandidate2;
    private WifiConfiguration mCandidateWifiConfig1;
    private WifiConfiguration mCandidateWifiConfig2;
    private List<WifiCandidates.Candidate> mCandidateList;
    @Captor ArgumentCaptor<String> mCandidateBssidCaptor;
    @Captor ArgumentCaptor<WifiConfigManager.OnNetworkUpdateListener>
            mNetworkUpdateListenerCaptor;
    @Captor ArgumentCaptor<WifiNetworkSuggestionsManager.OnSuggestionUpdateListener>
            mSuggestionUpdateListenerCaptor;
    @Captor ArgumentCaptor<ActiveModeWarden.ModeChangeCallback> mModeChangeCallbackCaptor;
    @Captor ArgumentCaptor<BroadcastReceiver> mBroadcastReceiverCaptor;
    @Captor ArgumentCaptor<MultiInternetManager.ConnectionStatusListener>
            mMultiInternetConnectionStatusListenerCaptor;
    private MockitoSession mSession;
    private MockResources mResources;

    private static final int CANDIDATE_NETWORK_ID = 0;
    private static final int CANDIDATE_NETWORK_ID_2 = 2;
    private static final String CANDIDATE_SSID = "\"AnSsid\"";
    private static final String CANDIDATE_BSSID = "6c:f3:7f:ae:8c:f3";
    private static final String CANDIDATE_BSSID_2 = "6c:f3:7f:ae:8d:f3";
    private static final String INVALID_SCAN_RESULT_BSSID = "6c:f3:7f:ae:8c:f4";
    private static final int TEST_FREQUENCY = 2420;
    private static final long CURRENT_SYSTEM_TIME_MS = 1000;
    private static final int MAX_BSSID_BLOCKLIST_SIZE = 16;

    // Scan schedule and corresponding scan types
    private static final int[] VALID_CONNECTED_SINGLE_SCAN_SCHEDULE_SEC = {10, 30, 50};
    private static final int[] VALID_CONNECTED_SINGLE_SAVED_NETWORK_SCHEDULE_SEC = {15, 35, 55};
    private static final int[] VALID_DISCONNECTED_SINGLE_SCAN_SCHEDULE_SEC = {25, 40, 60};
    private static final int[] VALID_CONNECTED_SINGLE_SCAN_TYPE = {1, 0, 0};
    private static final int[] VALID_CONNECTED_SINGLE_SAVED_NETWORK_TYPE = {2, 0, 1};
    private static final int[] VALID_DISCONNECTED_SINGLE_SCAN_TYPE = {2, 1, 1};
    private static final int[] VALID_EXTERNAL_SINGLE_SCAN_SCHEDULE_SEC = {40, 80};
    private static final int[] VALID_EXTERNAL_SINGLE_SCAN_TYPE = {1, 0};

    private static final int[] SCHEDULE_EMPTY_SEC = {};
    private static final int[] INVALID_SCHEDULE_NEGATIVE_VALUES_SEC = {10, -10, 20};
    private static final int[] INVALID_SCHEDULE_ZERO_VALUES_SEC = {10, 0, 20};
    private static final int MAX_SCAN_INTERVAL_IN_SCHEDULE_SEC = 60;
    private static final int[] DEFAULT_SINGLE_SCAN_SCHEDULE_SEC = {20, 40, 80, 160};
    private static final int[] DEFAULT_SINGLE_SCAN_TYPE = {2, 2, 2, 2};
    private static final int MAX_SCAN_INTERVAL_IN_DEFAULT_SCHEDULE_SEC = 160;
    private static final int TEST_FREQUENCY_1 = 2412;
    private static final int TEST_FREQUENCY_2 = 5180;
    private static final int TEST_FREQUENCY_3 = 5240;
    private static final int TEST_CURRENT_CONNECTED_FREQUENCY = 2427;
    private static final int HIGH_MVMT_SCAN_DELAY_MS = 10000;
    private static final int HIGH_MVMT_RSSI_DELTA = 10;
    private static final String TEST_FQDN = "FQDN";
    private static final String TEST_SSID = "SSID";
    private static final int TEMP_BSSID_BLOCK_DURATION_MS = 10 * 1000; // 10 seconds
    private static final int TEST_CONNECTED_NETWORK_ID = 55;
    private static final String TEST_CONNECTED_BSSID = "6c:f3:7f:ae:8c:f1";
    private static final int CHANNEL_CACHE_AGE_MINS = 14400;
    private static final int MOVING_PNO_SCAN_INTERVAL_MILLIS = 20_000;
    private static final int STATIONARY_PNO_SCAN_INTERVAL_MILLIS = 60_000;
    private static final int POWER_SAVE_SCAN_INTERVAL_MULTIPLIER = 2;
    private static final int LOW_RSSI_NETWORK_RETRY_START_DELAY_SEC = 20;
    private static final int LOW_RSSI_NETWORK_RETRY_MAX_DELAY_SEC = 80;
    private static final int SCAN_TRIGGER_TIMES = 7;
    private static final long NETWORK_CHANGE_TRIGGER_PNO_THROTTLE_MS = 3000; // 3 seconds
    private static final int TEST_FREQUENCY_2G = 2412;
    private static final int TEST_FREQUENCY_5G = 5262;

    /**
    * A test Handler that stores one single incoming Message with delayed time internally, to be
    * able to manually triggered by calling {@link #timeAdvance}. Only one delayed message can be
    * scheduled at a time. The scheduled delayed message intervals are recorded and returned by
    * {@link #getIntervals}. The intervals are cleared by calling {@link #reset}.
    */
    private class TestHandler extends Handler {
        private ArrayList<Long> mIntervals = new ArrayList<>();
        private Message mMessage;

        TestHandler(Looper looper) {
            super(looper);
        }

        public List<Long> getIntervals() {
            return mIntervals;
        }

        public void reset() {
            mIntervals.clear();
        }

        public void timeAdvance() {
            if (mMessage != null) {
                // Dispatch the message without waiting.
                super.dispatchMessage(mMessage);
            }
        }

        @Override
        public boolean sendMessageAtTime(Message msg, long uptimeMillis) {
            // uptimeMillis is an absolute time obtained as SystemClock.uptimeMillis() + delay
            // in Handler and can't be replaced with customized clock.
            // if custom clock is given, recalculate the time with regards to it
            long delayMs = uptimeMillis - SystemClock.uptimeMillis();
            if (delayMs > 0) {
                mIntervals.add(delayMs);
                mMessage = msg;
            }
            uptimeMillis = delayMs + mClock.getElapsedSinceBootMillis();
            // Message is still queued to super, so it doesn't get filtered out and rely on the
            // timeAdvance() to dispatch. timeAdvance() can force time to advance and send the
            // message immediately. If it is not called not the message can still be dispatched
            // at the time the message is scheduled.
            return super.sendMessageAtTime(msg, uptimeMillis);
        }
    }

    WifiContext mockContext() {
        WifiContext context = mock(WifiContext.class);

        when(context.getResources()).thenReturn(mResources);
        when(context.getSystemService(AlarmManager.class)).thenReturn(
                mAlarmManager.getAlarmManager());
        when(context.getPackageManager()).thenReturn(mock(PackageManager.class));

        return context;
    }

    ScanData mockScanData() {
        ScanData scanData = mock(ScanData.class);

        when(scanData.getScannedBandsInternal()).thenReturn(WifiScanner.WIFI_BAND_ALL);

        return scanData;
    }

    WifiScanner mockWifiScanner() {
        WifiScanner scanner = mock(WifiScanner.class);
        ArgumentCaptor<ScanListener> allSingleScanListenerCaptor =
                ArgumentCaptor.forClass(ScanListener.class);

        doNothing().when(scanner).registerScanListener(
                any(), allSingleScanListenerCaptor.capture());

        ScanData[] scanDatas = new ScanData[1];
        scanDatas[0] = mScanData;

        // do a synchronous answer for the ScanListener callbacks
        doAnswer(new AnswerWithArguments() {
            public void answer(ScanSettings settings, ScanListener listener,
                    WorkSource workSource) throws Exception {
                listener.onResults(scanDatas);
            }}).when(scanner).startBackgroundScan(anyObject(), anyObject(), anyObject());

        doAnswer(new AnswerWithArguments() {
            public void answer(ScanSettings settings, Executor executor, ScanListener listener,
                    WorkSource workSource) throws Exception {
                listener.onResults(scanDatas);
                // WCM processes scan results received via onFullResult (even though they're the
                // same as onResult for single scans).
                if (mScanData != null && mScanData.getResults() != null) {
                    for (int i = 0; i < mScanData.getResults().length; i++) {
                        allSingleScanListenerCaptor.getValue().onFullResult(
                                mScanData.getResults()[i]);
                    }
                }
                allSingleScanListenerCaptor.getValue().onResults(scanDatas);
            }}).when(scanner).startScan(anyObject(), anyObject(), anyObject(), anyObject());

        // This unfortunately needs to be a somewhat valid scan result, otherwise
        // |ScanDetailUtil.toScanDetail| raises exceptions.
        final ScanResult[] scanResults = new ScanResult[1];
        scanResults[0] = new ScanResult(WifiSsid.fromUtf8Text(CANDIDATE_SSID),
                CANDIDATE_SSID, CANDIDATE_BSSID, 1245, 0, "some caps",
                -78, 2450, 1025, 22, 33, 20, 0, 0, true);
        scanResults[0].informationElements = new InformationElement[1];
        scanResults[0].informationElements[0] = new InformationElement();
        scanResults[0].informationElements[0].id = InformationElement.EID_SSID;
        scanResults[0].informationElements[0].bytes =
            CANDIDATE_SSID.getBytes(StandardCharsets.UTF_8);

        doAnswer(new AnswerWithArguments() {
            public void answer(ScanSettings settings, PnoSettings pnoSettings,
                    Executor executor, PnoScanListener listener) throws Exception {
                listener.onPnoNetworkFound(scanResults);
            }}).when(scanner).startDisconnectedPnoScan(
                    anyObject(), anyObject(), anyObject(), anyObject());

        return scanner;
    }

    WifiConnectivityHelper mockWifiConnectivityHelper() {
        WifiConnectivityHelper connectivityHelper = mock(WifiConnectivityHelper.class);

        when(connectivityHelper.isFirmwareRoamingSupported()).thenReturn(false);
        when(connectivityHelper.getMaxNumBlocklistBssid()).thenReturn(MAX_BSSID_BLOCKLIST_SIZE);

        return connectivityHelper;
    }

    private void setupMockForClientModeManager(ConcreteClientModeManager cmm) {
        when(cmm.getRole()).thenReturn(ActiveModeManager.ROLE_CLIENT_PRIMARY);
        when(cmm.isConnected()).thenReturn(false);
        when(cmm.isDisconnected()).thenReturn(true);
        when(cmm.isSupplicantTransientState()).thenReturn(false);
        when(cmm.enableRoaming(anyBoolean())).thenReturn(true);
    }

    WifiNetworkSelector mockWifiNetworkSelector() {
        WifiNetworkSelector ns = mock(WifiNetworkSelector.class);

        WifiConfiguration candidate = generateWifiConfig(
                0, CANDIDATE_NETWORK_ID, CANDIDATE_SSID, false, true, null, null,
                WifiConfigurationTestUtil.SECURITY_NONE);
        candidate.BSSID = ClientModeImpl.SUPPLICANT_BSSID_ANY;
        ScanResult candidateScanResult = new ScanResult();
        candidateScanResult.SSID = CANDIDATE_SSID;
        candidateScanResult.BSSID = CANDIDATE_BSSID;
        candidate.getNetworkSelectionStatus().setCandidate(candidateScanResult);
        mCandidateWifiConfig1 = candidate;
        mCandidateWifiConfig2 = new WifiConfiguration(candidate);
        mCandidateWifiConfig2.networkId = CANDIDATE_NETWORK_ID_2;

        when(mWifiConfigManager.getConfiguredNetwork(CANDIDATE_NETWORK_ID)).thenReturn(candidate);
        MacAddress macAddress = MacAddress.fromString(CANDIDATE_BSSID);
        ScanResultMatchInfo matchInfo = mock(ScanResultMatchInfo.class);
        // Assume that this test use the default security params.
        when(matchInfo.getDefaultSecurityParams()).thenReturn(candidate.getDefaultSecurityParams());
        WifiCandidates.Key key = new WifiCandidates.Key(matchInfo,
                macAddress, 0);
        when(mCandidate1.getKey()).thenReturn(key);
        when(mCandidate1.getScanRssi()).thenReturn(-40);
        when(mCandidate1.getFrequency()).thenReturn(TEST_FREQUENCY);
        when(mCandidate2.getKey()).thenReturn(key);
        when(mCandidate2.getScanRssi()).thenReturn(-60);
        mCandidateList = new ArrayList<WifiCandidates.Candidate>();
        mCandidateList.add(mCandidate1);
        when(ns.getCandidatesFromScan(any(), any(), any(), anyBoolean(), anyBoolean(),
                anyBoolean(), any(), anyBoolean())).thenReturn(mCandidateList);
        when(ns.selectNetwork(any()))
                .then(new AnswerWithArguments() {
                    public WifiConfiguration answer(List<WifiCandidates.Candidate> candidateList) {
                        if (candidateList == null || candidateList.size() == 0) {
                            return null;
                        }
                        return candidate;
                    }
                });
        return ns;
    }

    WifiInfo getWifiInfo() {
        WifiInfo wifiInfo = new WifiInfo();

        wifiInfo.setNetworkId(WifiConfiguration.INVALID_NETWORK_ID);
        wifiInfo.setBSSID(null);
        wifiInfo.setSupplicantState(SupplicantState.DISCONNECTED);

        return wifiInfo;
    }

    WifiConfigManager mockWifiConfigManager() {
        WifiConfigManager wifiConfigManager = mock(WifiConfigManager.class);
        WifiConfiguration config = WifiConfigurationTestUtil.createOpenNetwork();
        config.getNetworkSelectionStatus().setHasEverConnected(true);
        List<WifiConfiguration> networkList = new ArrayList<>();
        networkList.add(config);
        when(wifiConfigManager.getConfiguredNetwork(anyInt())).thenReturn(null);
        when(wifiConfigManager.getSavedNetworks(anyInt())).thenReturn(networkList);

        return wifiConfigManager;
    }

    WifiConnectivityManager createConnectivityManager() {
        WifiConnectivityManager wCm = new WifiConnectivityManager(mContext, mScoringParams,
                mWifiConfigManager, mWifiNetworkSuggestionsManager,
                mWifiNS, mWifiConnectivityHelper,
                mWifiLastResortWatchdog, mOpenNetworkNotifier,
                mWifiMetrics, mTestHandler, mClock,
                mLocalLog, mWifiScoreCard, mWifiBlocklistMonitor, mWifiChannelUtilization,
                mPasspointManager, mMultiInternetManager, mDeviceConfigFacade, mActiveModeWarden,
                mFacade, mWifiGlobals, mExternalPnoScanRequestManager);
        mLooper.dispatchAll();
        verify(mActiveModeWarden, atLeastOnce()).registerModeChangeCallback(
                mModeChangeCallbackCaptor.capture());
        verify(mContext, atLeastOnce()).registerReceiver(
                mBroadcastReceiverCaptor.capture(), any(), any(), any());
        verify(mWifiConfigManager, atLeastOnce()).addOnNetworkUpdateListener(
                mNetworkUpdateListenerCaptor.capture());
        verify(mWifiNetworkSuggestionsManager, atLeastOnce()).addOnSuggestionUpdateListener(
                mSuggestionUpdateListenerCaptor.capture());
        verify(mMultiInternetManager, atLeastOnce()).setConnectionStatusListener(
                mMultiInternetConnectionStatusListenerCaptor.capture());
        return wCm;
    }

    void setWifiStateConnected() {
        setWifiStateConnected(TEST_CONNECTED_NETWORK_ID, TEST_CONNECTED_BSSID);
    }

    void setWifiStateConnected(int networkId, String bssid) {
        // Prep for setting WiFi to connected state
        WifiConfiguration connectedWifiConfiguration = new WifiConfiguration();
        connectedWifiConfiguration.networkId = networkId;
        when(mPrimaryClientModeManager.getConnectedWifiConfiguration())
                .thenReturn(connectedWifiConfiguration);
        when(mPrimaryClientModeManager.getConnectedBssid())
                .thenReturn(bssid);

        mWifiConnectivityManager.handleConnectionStateChanged(
                mPrimaryClientModeManager,
                WifiConnectivityManager.WIFI_STATE_CONNECTED);
    }

    /**
     * Verify that a primary CMM changing role to secondary transient (MBB) will not trigger cleanup
     * that's meant to be done when wifi is disabled.
     */
    @Test
    public void testPrimaryToSecondaryTransientDoesNotDisableWifi() {
        ConcreteClientModeManager cmm = mock(ConcreteClientModeManager.class);
        when(cmm.getPreviousRole()).thenReturn(ROLE_CLIENT_PRIMARY);
        when(cmm.getRole()).thenReturn(ROLE_CLIENT_SECONDARY_TRANSIENT);
        when(mActiveModeWarden.getInternetConnectivityClientModeManagers()).thenReturn(
                Collections.EMPTY_LIST);
        mModeChangeCallbackCaptor.getValue().onActiveModeManagerRoleChanged(cmm);
        verify(mWifiConfigManager, never()).removeAllEphemeralOrPasspointConfiguredNetworks();
    }

    /**
     * Verify that the primary CMM switching to scan only mode will trigger cleanup code.
     */
    @Test
    public void testPrimaryToScanOnlyWillDisableWifi() {
        ConcreteClientModeManager cmm = mock(ConcreteClientModeManager.class);
        when(cmm.getPreviousRole()).thenReturn(ROLE_CLIENT_PRIMARY);
        when(cmm.getRole()).thenReturn(ROLE_CLIENT_SCAN_ONLY);
        when(mActiveModeWarden.getInternetConnectivityClientModeManagers()).thenReturn(
                Collections.EMPTY_LIST);
        mModeChangeCallbackCaptor.getValue().onActiveModeManagerRoleChanged(cmm);
        verify(mWifiConfigManager).removeAllEphemeralOrPasspointConfiguredNetworks();
    }

    /**
     * Don't connect to the candidate network if we're already connected to that network on the
     * primary ClientModeManager.
     */
    @Test
    public void alreadyConnectedOnPrimaryCmm_dontConnectAgain() {
        when(mWifiConnectivityHelper.isFirmwareRoamingSupported()).thenReturn(true);
        // Set screen to on
        setScreenState(true);

        WifiConfiguration config = new WifiConfiguration();
        config.networkId = CANDIDATE_NETWORK_ID;
        when(mPrimaryClientModeManager.getConnectingWifiConfiguration()).thenReturn(config);

        // Set WiFi to disconnected state
        mWifiConnectivityManager.handleConnectionStateChanged(
                mPrimaryClientModeManager,
                WifiConnectivityManager.WIFI_STATE_DISCONNECTED);

        verify(mActiveModeWarden, never()).requestSecondaryTransientClientModeManager(
                any(), any(), any(), any());
        verify(mPrimaryClientModeManager, never()).startConnectToNetwork(
                anyInt(), anyInt(), any());
    }

    /** Connect using the primary ClientModeManager if it's not connected to anything */
    @Test
    public void disconnectedOnPrimaryCmm_connectUsingPrimaryCmm() {
        when(mWifiConnectivityHelper.isFirmwareRoamingSupported()).thenReturn(true);
        // Set screen to on
        setScreenState(true);

        when(mPrimaryClientModeManager.getConnectedWifiConfiguration()).thenReturn(null);
        when(mPrimaryClientModeManager.getConnectingWifiConfiguration()).thenReturn(null);

        // Set WiFi to disconnected state
        mWifiConnectivityManager.handleConnectionStateChanged(
                mPrimaryClientModeManager,
                WifiConnectivityManager.WIFI_STATE_DISCONNECTED);

        verify(mPrimaryClientModeManager).startConnectToNetwork(
                CANDIDATE_NETWORK_ID, Process.WIFI_UID, "any");
        verify(mPrimaryClientModeManager).enableRoaming(true);
        verify(mActiveModeWarden).stopAllClientModeManagersInRole(ROLE_CLIENT_SECONDARY_TRANSIENT);
        verify(mActiveModeWarden, never()).requestSecondaryTransientClientModeManager(
                any(), any(), any(), any());
    }

    /** Don't crash if allocated a null ClientModeManager. */
    @Test
    public void requestSecondaryTransientCmm_gotNullCmm() {
        doAnswer(new AnswerWithArguments() {
            public void answer(ExternalClientModeManagerRequestListener listener,
                    WorkSource requestorWs, String ssid, String bssid) {
                listener.onAnswer(null);
            }
        }).when(mActiveModeWarden).requestSecondaryTransientClientModeManager(
                any(), eq(ActiveModeWarden.INTERNAL_REQUESTOR_WS), any(), any());

        // primary CMM already connected
        WifiConfiguration config2 = new WifiConfiguration();
        config2.networkId = CANDIDATE_NETWORK_ID_2;
        when(mPrimaryClientModeManager.getConnectedWifiConfiguration())
                .thenReturn(config2);

        // Set screen to on
        setScreenState(true);

        // Set WiFi to disconnected state
        mWifiConnectivityManager.handleConnectionStateChanged(
                mPrimaryClientModeManager,
                WifiConnectivityManager.WIFI_STATE_DISCONNECTED);

        verify(mActiveModeWarden).requestSecondaryTransientClientModeManager(
                any(),
                eq(ActiveModeWarden.INTERNAL_REQUESTOR_WS),
                eq(CANDIDATE_SSID),
                eq(CANDIDATE_BSSID));
        verify(mPrimaryClientModeManager, never()).startConnectToNetwork(
                anyInt(), anyInt(), any());
    }

    /**
     * Don't attempt to connect again if the allocated ClientModeManager is already connected to
     * the desired network.
     */
    @Test
    public void requestSecondaryTransientCmm_gotAlreadyConnectedCmm() {
        when(mWifiConnectivityHelper.isFirmwareRoamingSupported()).thenReturn(true);

        WifiConfiguration config = new WifiConfiguration();
        config.networkId = CANDIDATE_NETWORK_ID;
        ClientModeManager alreadyConnectedCmm = mock(ClientModeManager.class);
        when(alreadyConnectedCmm.getConnectingWifiConfiguration()).thenReturn(config);

        doAnswer(new AnswerWithArguments() {
            public void answer(ExternalClientModeManagerRequestListener listener,
                    WorkSource requestorWs, String ssid, String bssid) {
                listener.onAnswer(alreadyConnectedCmm);
            }
        }).when(mActiveModeWarden).requestSecondaryTransientClientModeManager(
                any(), eq(ActiveModeWarden.INTERNAL_REQUESTOR_WS), any(), any());

        // primary CMM already connected
        WifiConfiguration config2 = new WifiConfiguration();
        config2.networkId = CANDIDATE_NETWORK_ID_2;
        when(mPrimaryClientModeManager.getConnectedWifiConfiguration())
                .thenReturn(config2);

        // Set screen to on
        setScreenState(true);

        // Set WiFi to disconnected state
        mWifiConnectivityManager.handleConnectionStateChanged(
                mPrimaryClientModeManager,
                WifiConnectivityManager.WIFI_STATE_DISCONNECTED);

        verify(mActiveModeWarden).requestSecondaryTransientClientModeManager(
                any(),
                eq(ActiveModeWarden.INTERNAL_REQUESTOR_WS),
                eq(CANDIDATE_SSID),
                eq(null));

        // already connected, don't connect again
        verify(alreadyConnectedCmm, never()).startConnectToNetwork(
                anyInt(), anyInt(), any());
    }

    /**
     * Verify MBB full flow.
     */
    @Test
    public void connectWhenConnected_UsingMbb() {
        when(mWifiConnectivityHelper.isFirmwareRoamingSupported()).thenReturn(true);

        ClientModeManager mbbCmm = mock(ClientModeManager.class);
        doAnswer(new AnswerWithArguments() {
            public void answer(ExternalClientModeManagerRequestListener listener,
                    WorkSource requestorWs, String ssid, String bssid) {
                listener.onAnswer(mbbCmm);
            }
        }).when(mActiveModeWarden).requestSecondaryTransientClientModeManager(
                any(), eq(ActiveModeWarden.INTERNAL_REQUESTOR_WS), any(), any());

        // primary CMM already connected
        when(mPrimaryClientModeManager.getConnectedWifiConfiguration())
                .thenReturn(mCandidateWifiConfig2);

        // Set screen to on
        setScreenState(true);

        // Set WiFi to connected state
        mWifiConnectivityManager.handleConnectionStateChanged(
                mPrimaryClientModeManager,
                WifiConnectivityManager.WIFI_STATE_CONNECTED);

        // Request secondary STA and connect using it.
        verify(mActiveModeWarden).requestSecondaryTransientClientModeManager(
                any(),
                eq(ActiveModeWarden.INTERNAL_REQUESTOR_WS),
                eq(CANDIDATE_SSID),
                eq(null));
        verify(mbbCmm).startConnectToNetwork(eq(CANDIDATE_NETWORK_ID), anyInt(), any());
    }

    /**
     * Fallback to single STA behavior when both networks have MAC randomization disabled.
     */
    @Test
    public void connectWhenConnected_UsingBbmIfBothNetworksHaveMacRandomizationDisabled() {
        when(mWifiConnectivityHelper.isFirmwareRoamingSupported()).thenReturn(true);

        ClientModeManager mbbCmm = mock(ClientModeManager.class);
        doAnswer(new AnswerWithArguments() {
            public void answer(ExternalClientModeManagerRequestListener listener,
                    WorkSource requestorWs, String ssid, String bssid) {
                listener.onAnswer(mbbCmm);
            }
        }).when(mActiveModeWarden).requestSecondaryTransientClientModeManager(
                any(), eq(ActiveModeWarden.INTERNAL_REQUESTOR_WS), any(), any());

        // Turn off MAC randomization on both networks.
        mCandidateWifiConfig1.macRandomizationSetting = WifiConfiguration.RANDOMIZATION_NONE;
        mCandidateWifiConfig2.macRandomizationSetting = WifiConfiguration.RANDOMIZATION_NONE;

        // primary CMM already connected
        when(mPrimaryClientModeManager.getConnectedWifiConfiguration())
                .thenReturn(mCandidateWifiConfig2);

        // Set screen to on
        setScreenState(true);

        // Set WiFi to connected state
        mWifiConnectivityManager.handleConnectionStateChanged(
                mPrimaryClientModeManager,
                WifiConnectivityManager.WIFI_STATE_CONNECTED);

        // Don't request secondary STA, fallback to primary STA.
        verify(mActiveModeWarden, never()).requestSecondaryTransientClientModeManager(
                any(), any(), any(), any());
        verify(mbbCmm, never()).startConnectToNetwork(anyInt(), anyInt(), any());
        verify(mPrimaryClientModeManager).startConnectToNetwork(
                eq(CANDIDATE_NETWORK_ID), anyInt(), any());
        verify(mPrimaryClientModeManager).enableRoaming(true);
    }

    /**
     * Setup all the mocks for the positive case, individual negative test cases below override
     * specific params.
     */
    private void setupMocksForSecondaryLongLivedTests() {
        when(mWifiConnectivityHelper.isFirmwareRoamingSupported()).thenReturn(true);
        when(mCandidate1.isOemPaid()).thenReturn(true);
        when(mCandidate1.isOemPrivate()).thenReturn(true);
        mCandidateWifiConfig1.oemPaid = true;
        mCandidateWifiConfig1.oemPrivate = true;
        when(mWifiNS.selectNetwork(argThat(
                candidates -> (candidates != null && candidates.size() == 1
                        && (candidates.get(0).isOemPaid() || candidates.get(0).isOemPrivate()))
        ))).thenReturn(mCandidateWifiConfig1);
        when(mActiveModeWarden.isStaStaConcurrencySupportedForRestrictedConnections())
                .thenReturn(true);
        when(mActiveModeWarden.canRequestMoreClientModeManagersInRole(
                any(), eq(ROLE_CLIENT_SECONDARY_LONG_LIVED), eq(false))).thenReturn(true);
        doAnswer(new AnswerWithArguments() {
            public void answer(ExternalClientModeManagerRequestListener listener,
                    WorkSource requestorWs, String ssid, String bssid) {
                listener.onAnswer(mSecondaryClientModeManager);
            }
        }).when(mActiveModeWarden).requestSecondaryLongLivedClientModeManager(
                any(), any(), any(), any());
        when(mSecondaryClientModeManager.getRole()).thenReturn(ROLE_CLIENT_SECONDARY_LONG_LIVED);
    }

    @Test
    public void secondaryLongLived_noOemPaidOrOemPrivateConnectionAllowed() {
        setupMocksForSecondaryLongLivedTests();

        // Set screen to on
        setScreenState(true);

        // OEM paid/OEM private connection disallowed.
        mWifiConnectivityManager.setOemPaidConnectionAllowed(false, null);
        mWifiConnectivityManager.setOemPrivateConnectionAllowed(false, null);

        // Set WiFi to disconnected state
        mWifiConnectivityManager.handleConnectionStateChanged(
                mPrimaryClientModeManager,
                WifiConnectivityManager.WIFI_STATE_DISCONNECTED);

        verify(mPrimaryClientModeManager).startConnectToNetwork(
                CANDIDATE_NETWORK_ID, Process.WIFI_UID, "any");
        verify(mActiveModeWarden, never()).requestSecondaryLongLivedClientModeManager(
                any(), any(), any(), any());
    }

    @Test
    public void secondaryLongLived_oemPaidConnectionAllowedWithOemPrivateCandidate() {
        setupMocksForSecondaryLongLivedTests();

        // Set screen to on
        setScreenState(true);

        // OEM paid connection allowed.
        mWifiConnectivityManager.setOemPaidConnectionAllowed(true, new WorkSource());

        // Mark the candidate oem private only
        when(mCandidate1.isOemPaid()).thenReturn(false);
        when(mCandidate1.isOemPrivate()).thenReturn(true);
        mCandidateWifiConfig1.oemPaid = false;
        mCandidateWifiConfig1.oemPrivate = true;

        // Set WiFi to disconnected state
        mWifiConnectivityManager.handleConnectionStateChanged(
                mPrimaryClientModeManager,
                WifiConnectivityManager.WIFI_STATE_DISCONNECTED);

        verify(mPrimaryClientModeManager).startConnectToNetwork(
                CANDIDATE_NETWORK_ID, Process.WIFI_UID, "any");
        verify(mActiveModeWarden, never()).requestSecondaryLongLivedClientModeManager(
                any(), any(), any(), any());
    }

    @Test
    public void secondaryLongLived_oemPrivateConnectionAllowedWithOemPaidCandidate() {
        setupMocksForSecondaryLongLivedTests();

        // Set screen to on
        setScreenState(true);

        // OEM private connection allowed.
        mWifiConnectivityManager.setOemPrivateConnectionAllowed(true, new WorkSource());

        // Mark the candidate oem paid only
        when(mCandidate1.isOemPaid()).thenReturn(true);
        when(mCandidate1.isOemPrivate()).thenReturn(false);
        mCandidateWifiConfig1.oemPaid = true;
        mCandidateWifiConfig1.oemPrivate = false;

        // Set WiFi to disconnected state
        mWifiConnectivityManager.handleConnectionStateChanged(
                mPrimaryClientModeManager,
                WifiConnectivityManager.WIFI_STATE_DISCONNECTED);

        verify(mPrimaryClientModeManager).startConnectToNetwork(
                CANDIDATE_NETWORK_ID, Process.WIFI_UID, "any");
        verify(mActiveModeWarden, never()).requestSecondaryLongLivedClientModeManager(
                any(), any(), any(), any());
    }

    @Test
    public void secondaryLongLived_noSecondaryStaSupport() {
        setupMocksForSecondaryLongLivedTests();

        // Set screen to on
        setScreenState(true);

        // OEM paid connection allowed.
        mWifiConnectivityManager.setOemPaidConnectionAllowed(true, new WorkSource());

        // STA + STA is not supported.
        when(mActiveModeWarden.isStaStaConcurrencySupportedForRestrictedConnections())
                .thenReturn(false);
        when(mActiveModeWarden.isStaStaConcurrencySupportedForMultiInternet())
                .thenReturn(false);

        // Set WiFi to disconnected state
        mWifiConnectivityManager.handleConnectionStateChanged(
                mPrimaryClientModeManager,
                WifiConnectivityManager.WIFI_STATE_DISCONNECTED);

        verify(mPrimaryClientModeManager).startConnectToNetwork(
                CANDIDATE_NETWORK_ID, Process.WIFI_UID, "any");
        verify(mActiveModeWarden, never()).requestSecondaryLongLivedClientModeManager(
                any(), any(), any(), any());
    }

    @Test
    public void secondaryLongLived_noSecondaryCandidateSelected() {
        setupMocksForSecondaryLongLivedTests();

        // Set screen to on
        setScreenState(true);

        // OEM paid connection allowed.
        mWifiConnectivityManager.setOemPaidConnectionAllowed(true, new WorkSource());

        // Network selection does not select a secondary candidate.
        when(mWifiNS.selectNetwork(argThat(
                candidates -> (candidates != null && candidates.size() == 1
                        && (candidates.get(0).isOemPaid() || candidates.get(0).isOemPrivate()))
        ))).thenReturn(null) // first for secondary returns null.
                .thenReturn(mCandidateWifiConfig1); // second for primary returns something.

        // Set WiFi to disconnected state
        mWifiConnectivityManager.handleConnectionStateChanged(
                mPrimaryClientModeManager,
                WifiConnectivityManager.WIFI_STATE_DISCONNECTED);

        verify(mPrimaryClientModeManager).startConnectToNetwork(
                CANDIDATE_NETWORK_ID, Process.WIFI_UID, "any");
        verify(mActiveModeWarden, never()).requestSecondaryLongLivedClientModeManager(
                any(), any(), any(), any());
    }

    @Test
    public void secondaryLongLived_secondaryStaRequestReturnsNull() {
        setupMocksForSecondaryLongLivedTests();

        // Set screen to on
        setScreenState(true);

        // OEM paid connection allowed.
        mWifiConnectivityManager.setOemPaidConnectionAllowed(true, new WorkSource());

        // STA + STA is supported, but secondary STA request returns null
        doAnswer(new AnswerWithArguments() {
            public void answer(ExternalClientModeManagerRequestListener listener,
                    WorkSource requestorWs, String ssid, String bssid) {
                listener.onAnswer(null);
            }
        }).when(mActiveModeWarden).requestSecondaryLongLivedClientModeManager(
                any(), any(), any(), any());

        // Set WiFi to disconnected state
        mWifiConnectivityManager.handleConnectionStateChanged(
                mPrimaryClientModeManager,
                WifiConnectivityManager.WIFI_STATE_DISCONNECTED);

        // No connection triggered (even on primary since wifi is off).
        verify(mPrimaryClientModeManager, never()).startConnectToNetwork(
                CANDIDATE_NETWORK_ID, Process.WIFI_UID, "any");
        verify(mActiveModeWarden).requestSecondaryLongLivedClientModeManager(
                any(), any(), any(), any());
    }

    @Test
    public void secondaryLongLived_secondaryStaRequestReturnsPrimary() {
        setupMocksForSecondaryLongLivedTests();

        // Set screen to on
        setScreenState(true);

        // OEM paid connection allowed.
        mWifiConnectivityManager.setOemPaidConnectionAllowed(true, new WorkSource());

        // STA + STA is supported, but secondary STA request returns the primary
        doAnswer(new AnswerWithArguments() {
            public void answer(ExternalClientModeManagerRequestListener listener,
                    WorkSource requestorWs, String ssid, String bssid) {
                listener.onAnswer(mPrimaryClientModeManager);
            }
        }).when(mActiveModeWarden).requestSecondaryLongLivedClientModeManager(
                any(), any(), any(), any());

        // Set WiFi to disconnected state
        mWifiConnectivityManager.handleConnectionStateChanged(
                mPrimaryClientModeManager,
                WifiConnectivityManager.WIFI_STATE_DISCONNECTED);

        // connection triggered on primary
        verify(mPrimaryClientModeManager).startConnectToNetwork(
                CANDIDATE_NETWORK_ID, Process.WIFI_UID, "any");
        verify(mActiveModeWarden).requestSecondaryLongLivedClientModeManager(
                any(), any(), any(), any());
    }

    @Test
    public void secondaryLongLived_secondaryStaRequestSucceedsWithOemPaidConnectionAllowed() {
        setupMocksForSecondaryLongLivedTests();

        // Set screen to on
        setScreenState(true);

        // OEM paid connection allowed.
        mWifiConnectivityManager.setOemPaidConnectionAllowed(true, new WorkSource());

        // Mark the candidate oem paid only
        when(mCandidate1.isOemPaid()).thenReturn(true);
        when(mCandidate1.isOemPrivate()).thenReturn(false);
        mCandidateWifiConfig1.oemPaid = true;
        mCandidateWifiConfig1.oemPrivate = false;

        // Set WiFi to disconnected state
        mWifiConnectivityManager.handleConnectionStateChanged(
                mPrimaryClientModeManager,
                WifiConnectivityManager.WIFI_STATE_DISCONNECTED);

        // connection triggered on secondary
        verify(mPrimaryClientModeManager, never()).startConnectToNetwork(
                CANDIDATE_NETWORK_ID, Process.WIFI_UID, "any");
        verify(mSecondaryClientModeManager).startConnectToNetwork(
                CANDIDATE_NETWORK_ID, Process.WIFI_UID, "any");
        verify(mActiveModeWarden).requestSecondaryLongLivedClientModeManager(
                any(), any(), any(), any());

        // Simulate connection failing on the secondary
        clearInvocations(mSecondaryClientModeManager, mPrimaryClientModeManager, mWifiNS);
        WifiConfiguration config = WifiConfigurationTestUtil.createPskNetwork(CANDIDATE_SSID);
        mWifiConnectivityManager.handleConnectionAttemptEnded(
                mSecondaryClientModeManager,
                WifiMetrics.ConnectionEvent.FAILURE_ASSOCIATION_REJECTION,
                WifiMetricsProto.ConnectionEvent.FAILURE_REASON_UNKNOWN, CANDIDATE_BSSID,
                config);
        // verify connection is never restarted when a connection on the secondary STA fails.
        verify(mWifiNS, never()).selectNetwork(any());
        verify(mSecondaryClientModeManager, never()).startConnectToNetwork(
                anyInt(), anyInt(), any());
        verify(mPrimaryClientModeManager, never()).startConnectToNetwork(
                anyInt(), anyInt(), any());
    }

    @Test
    public void secondaryLongLived_secondaryStaRequestSucceedsWithOemPrivateConnectionAllowed() {
        setupMocksForSecondaryLongLivedTests();

        // Set screen to on
        setScreenState(true);

        // OEM paid connection allowed.
        mWifiConnectivityManager.setOemPrivateConnectionAllowed(true, new WorkSource());

        // Mark the candidate oem private only
        when(mCandidate1.isOemPaid()).thenReturn(false);
        when(mCandidate1.isOemPrivate()).thenReturn(true);
        mCandidateWifiConfig1.oemPaid = false;
        mCandidateWifiConfig1.oemPrivate = true;

        // Set WiFi to disconnected state
        mWifiConnectivityManager.handleConnectionStateChanged(
                mPrimaryClientModeManager,
                WifiConnectivityManager.WIFI_STATE_DISCONNECTED);

        // connection triggered on secondary
        verify(mPrimaryClientModeManager, never()).startConnectToNetwork(
                CANDIDATE_NETWORK_ID, Process.WIFI_UID, "any");
        verify(mSecondaryClientModeManager).startConnectToNetwork(
                CANDIDATE_NETWORK_ID, Process.WIFI_UID, "any");
        verify(mActiveModeWarden).requestSecondaryLongLivedClientModeManager(
                any(), any(), any(), any());
    }

    @Test
    public void secondaryLongLived_secondaryStaRequestSucceedsAlongWithPrimary() {
        setupMocksForSecondaryLongLivedTests();

        // 2 candidates - 1 oem paid, other regular.
        // Mark the first candidate oem private only
        when(mCandidate1.isOemPaid()).thenReturn(false);
        when(mCandidate1.isOemPrivate()).thenReturn(true);
        mCandidateWifiConfig1.oemPaid = false;
        mCandidateWifiConfig1.oemPrivate = true;

        // Add the second regular candidate.
        mCandidateList.add(mCandidate2);

        // Set screen to on
        setScreenState(true);

        // OEM paid connection allowed.
        mWifiConnectivityManager.setOemPrivateConnectionAllowed(true, new WorkSource());

        // Network selection setup for primary.
        when(mWifiNS.selectNetwork(argThat(
                candidates -> (candidates != null && candidates.size() == 1
                        // not oem paid or oem private.
                        && !(candidates.get(0).isOemPaid() || candidates.get(0).isOemPrivate()))
        ))).thenReturn(mCandidateWifiConfig2);

        // Set WiFi to disconnected state
        mWifiConnectivityManager.handleConnectionStateChanged(
                mPrimaryClientModeManager,
                WifiConnectivityManager.WIFI_STATE_DISCONNECTED);

        // connection triggered on primary & secondary
        verify(mPrimaryClientModeManager).startConnectToNetwork(
                CANDIDATE_NETWORK_ID_2, Process.WIFI_UID, "any");
        verify(mSecondaryClientModeManager).startConnectToNetwork(
                CANDIDATE_NETWORK_ID, Process.WIFI_UID, "any");
        verify(mActiveModeWarden).requestSecondaryLongLivedClientModeManager(
                any(), any(), any(), any());
    }

    /**
     * Verify that when the secondary is already connecting to the selected secondary network,
     * we only connect the primary STA.
     */
    @Test
    public void secondaryLongLived_secondaryStaRequestSucceedsWhenSecondaryAlreadyConnecting() {
        setupMocksForSecondaryLongLivedTests();

        // 2 candidates - 1 oem paid, other regular.
        // Mark the first candidate oem private only
        when(mCandidate1.isOemPaid()).thenReturn(false);
        when(mCandidate1.isOemPrivate()).thenReturn(true);
        mCandidateWifiConfig1.oemPaid = false;
        mCandidateWifiConfig1.oemPrivate = true;

        // mock secondary STA to already connecting to the target OEM private network
        when(mSecondaryClientModeManager.getConnectingWifiConfiguration()).thenReturn(
                mCandidateWifiConfig1);

        // Add the second regular candidate.
        mCandidateList.add(mCandidate2);

        // Set screen to on
        setScreenState(true);

        // OEM paid connection allowed.
        mWifiConnectivityManager.setOemPrivateConnectionAllowed(true, new WorkSource());

        // Network selection setup for primary.
        when(mWifiNS.selectNetwork(argThat(
                candidates -> (candidates != null && candidates.size() == 1
                        // not oem paid or oem private.
                        && !(candidates.get(0).isOemPaid() || candidates.get(0).isOemPrivate()))
        ))).thenReturn(mCandidateWifiConfig2);

        // Set WiFi to disconnected state
        mWifiConnectivityManager.handleConnectionStateChanged(
                mPrimaryClientModeManager,
                WifiConnectivityManager.WIFI_STATE_DISCONNECTED);

        // connection triggered on only on primary to CANDIDATE_NETWORK_ID_2.
        verify(mPrimaryClientModeManager).startConnectToNetwork(
                CANDIDATE_NETWORK_ID_2, Process.WIFI_UID, "any");
        verify(mSecondaryClientModeManager, never()).startConnectToNetwork(
                CANDIDATE_NETWORK_ID, Process.WIFI_UID, "any");
        verify(mActiveModeWarden).requestSecondaryLongLivedClientModeManager(
                any(), any(), eq(CANDIDATE_SSID), any());
    }

    /**
     * Create scan data with different bands of 2G and 5G.
     */
    private ScanData createScanDataWithDifferentBands() {
        // Create 4 scan results.
        ScanData[] scanDatas =
                ScanTestUtil.createScanDatas(new int[][]{{5150, 5175, 2412, 2400}}, new int[]{0});
        // WCM barfs if the scan result does not have an IE.
        return scanDatas[0];
    }

    /**
     * Setup all the mocks for the positive case, individual negative test cases below override
     * specific params.
     */
    private void setupMocksForMultiInternetTests() {
        mScanData = createScanDataWithDifferentBands();
        when(mWifiConnectivityHelper.isFirmwareRoamingSupported()).thenReturn(true);
        when(mActiveModeWarden.isStaStaConcurrencySupportedForMultiInternet()).thenReturn(true);
        when(mActiveModeWarden.getPrimaryClientModeManagerNullable())
                .thenReturn(mPrimaryClientModeManager);
        when(mCandidate1.isOemPaid()).thenReturn(false);
        when(mCandidate1.isOemPrivate()).thenReturn(false);
        ScanResultMatchInfo matchInfo = mock(ScanResultMatchInfo.class);
        when(matchInfo.getDefaultSecurityParams()).thenReturn(
                mCandidateWifiConfig1.getDefaultSecurityParams());
        WifiCandidates.Key key = new WifiCandidates.Key(matchInfo,
                MacAddress.fromString(CANDIDATE_BSSID), 0);
        when(mCandidate1.getKey()).thenReturn(key);
        when(mCandidate1.getScanRssi()).thenReturn(-40);
        when(mCandidate1.getFrequency()).thenReturn(TEST_FREQUENCY);
        when(mCandidate1.getKey()).thenReturn(key);

        mCandidateWifiConfig1.oemPaid = false;
        mCandidateWifiConfig1.oemPrivate = false;
        mCandidateWifiConfig1.ephemeral = true;
        mCandidateWifiConfig1.dbsSecondaryInternet = true;
        when(mWifiNS.selectNetwork(argThat(
                candidates -> (candidates != null)), eq(false))).thenReturn(mCandidateWifiConfig1);
        when(mActiveModeWarden.isStaStaConcurrencySupportedForRestrictedConnections())
                .thenReturn(true);
        when(mActiveModeWarden.canRequestMoreClientModeManagersInRole(
                any(), eq(ROLE_CLIENT_SECONDARY_LONG_LIVED), eq(false))).thenReturn(true);
        doAnswer(new AnswerWithArguments() {
            public void answer(ExternalClientModeManagerRequestListener listener,
                    WorkSource requestorWs, String ssid, String bssid) {
                listener.onAnswer(mSecondaryClientModeManager);
            }
        }).when(mActiveModeWarden).requestSecondaryLongLivedClientModeManager(
                any(), any(), any(), any());
        when(mSecondaryClientModeManager.getRole()).thenReturn(ROLE_CLIENT_SECONDARY_LONG_LIVED);
    }

    @Test
    public void multiInternetSecondaryConnectionRequestSucceedsWithDbsApOnly() {
        setupMocksForMultiInternetTests();
        when(mMultiInternetManager.isStaConcurrencyForMultiInternetMultiApAllowed())
                .thenReturn(false);

        // Set screen to on
        setScreenState(true);

        when(mCandidate1.isSecondaryInternet()).thenReturn(true);
        mCandidateWifiConfig1.ephemeral = true;
        mCandidateWifiConfig1.dbsSecondaryInternet = true;

        // Set up the scan candidates
        MacAddress macAddress = MacAddress.fromString(CANDIDATE_BSSID);
        ScanResult result1 = new ScanResult(WifiSsid.fromUtf8Text(CANDIDATE_SSID),
                TEST_SSID, TEST_CONNECTED_BSSID, 1245, 0, "some caps", -78, 2450,
                1025, 22, 33, 20, 0, 0, true);
        ScanResultMatchInfo matchInfo1 = ScanResultMatchInfo.fromScanResult(result1);
        WifiCandidates.Key key = new WifiCandidates.Key(matchInfo1, macAddress,
                TEST_CONNECTED_NETWORK_ID,
                WifiConfiguration.SECURITY_TYPE_OPEN);
        WifiCandidates.Candidate otherCandidate = mock(WifiCandidates.Candidate.class);
        when(otherCandidate.getKey()).thenReturn(key);
        List<WifiCandidates.Candidate> candidateList = new ArrayList<>();
        candidateList.add(mCandidate1);
        candidateList.add(otherCandidate);
        when(mWifiNS.getCandidatesFromScan(any(), any(), any(), anyBoolean(), anyBoolean(),
                anyBoolean(), any(), anyBoolean())).thenReturn(candidateList);

        // Set WiFi to disconnected state to trigger scan
        mWifiConnectivityManager.handleConnectionStateChanged(
                mPrimaryClientModeManager,
                WifiConnectivityManager.WIFI_STATE_DISCONNECTED);
        mLooper.dispatchAll();
        // Verify a connection starting
        verify(mWifiNS).selectNetwork((List<WifiCandidates.Candidate>)
                argThat(new WifiCandidatesListSizeMatcher(2)));
        verify(mPrimaryClientModeManager).startConnectToNetwork(anyInt(), anyInt(), any());

        WifiInfo info1 = getWifiInfo();
        info1.setNetworkId(TEST_CONNECTED_NETWORK_ID);
        info1.setSSID(WifiSsid.fromUtf8Text(TEST_SSID));
        info1.setBSSID(TEST_CONNECTED_BSSID);
        info1.setFrequency(TEST_FREQUENCY_5G);
        info1.setCurrentSecurityType(WifiConfiguration.SECURITY_TYPE_OPEN);
        when(mPrimaryClientModeManager.isConnected()).thenReturn(true);
        when(mPrimaryClientModeManager.isDisconnected()).thenReturn(false);
        when(mPrimaryClientModeManager.syncRequestConnectionInfo()).thenReturn(info1);
        when(mMultiInternetManager.hasPendingConnectionRequests()).thenReturn(true);
        WorkSource testWorkSource = new WorkSource();
        // Set the connection pending status
        mMultiInternetConnectionStatusListenerCaptor.getValue().onStatusChange(
                MultiInternetManager.MULTI_INTERNET_STATE_CONNECTION_REQUESTED,
                testWorkSource);
        mMultiInternetConnectionStatusListenerCaptor.getValue().onStartScan(testWorkSource);
        verify(mSecondaryClientModeManager, times(2)).startConnectToNetwork(
                CANDIDATE_NETWORK_ID, Process.WIFI_UID, CANDIDATE_BSSID);
        verify(mSecondaryClientModeManager, times(2)).enableRoaming(false);
        verify(mActiveModeWarden, times(2)).requestSecondaryLongLivedClientModeManager(
                any(), any(), any(), any());

        // Simulate connection failing on the secondary
        clearInvocations(mSecondaryClientModeManager, mPrimaryClientModeManager, mWifiNS);
        mWifiConnectivityManager.handleConnectionAttemptEnded(
                mSecondaryClientModeManager,
                WifiMetrics.ConnectionEvent.FAILURE_ASSOCIATION_REJECTION,
                WifiMetricsProto.ConnectionEvent.FAILURE_REASON_UNKNOWN, CANDIDATE_BSSID,
                WifiConfigurationTestUtil.createPskNetwork(CANDIDATE_SSID));
        // verify connection is never restarted when a connection on the secondary STA fails.
        verify(mWifiNS, never()).selectNetwork(any());
        verify(mSecondaryClientModeManager, never()).startConnectToNetwork(
                anyInt(), anyInt(), any());
        verify(mPrimaryClientModeManager, never()).startConnectToNetwork(
                anyInt(), anyInt(), any());
    }

    @Test
    public void multiInternetSecondaryConnectionRequestSucceedsWithMultiApAllowed() {
        setupMocksForMultiInternetTests();
        when(mMultiInternetManager.isStaConcurrencyForMultiInternetMultiApAllowed())
                .thenReturn(true);

        // Set screen to on
        setScreenState(true);

        when(mCandidate1.isSecondaryInternet()).thenReturn(true);
        mCandidateWifiConfig1.ephemeral = true;
        mCandidateWifiConfig1.dbsSecondaryInternet = true;

        // Set up the scan candidates
        MacAddress macAddress = MacAddress.fromString(CANDIDATE_BSSID);
        WifiCandidates.Key key = new WifiCandidates.Key(mock(ScanResultMatchInfo.class),
                macAddress, 0, WifiConfiguration.SECURITY_TYPE_OPEN);
        WifiCandidates.Candidate otherCandidate = mock(WifiCandidates.Candidate.class);
        when(otherCandidate.getKey()).thenReturn(key);
        List<WifiCandidates.Candidate> candidateList = new ArrayList<>();
        candidateList.add(mCandidate1);
        candidateList.add(otherCandidate);
        when(mWifiNS.getCandidatesFromScan(any(), any(), any(), anyBoolean(), anyBoolean(),
                anyBoolean(), any(), anyBoolean())).thenReturn(candidateList);

        // Set WiFi to disconnected state to trigger scan
        mWifiConnectivityManager.handleConnectionStateChanged(
                mPrimaryClientModeManager,
                WifiConnectivityManager.WIFI_STATE_DISCONNECTED);
        mLooper.dispatchAll();
        // Verify a connection starting
        verify(mWifiNS).selectNetwork((List<WifiCandidates.Candidate>)
                argThat(new WifiCandidatesListSizeMatcher(2)));
        verify(mPrimaryClientModeManager).startConnectToNetwork(anyInt(), anyInt(), any());

        WifiInfo info1 = getWifiInfo();
        info1.setFrequency(TEST_FREQUENCY_5G);
        when(mPrimaryClientModeManager.isConnected()).thenReturn(true);
        when(mPrimaryClientModeManager.isDisconnected()).thenReturn(false);
        when(mPrimaryClientModeManager.syncRequestConnectionInfo()).thenReturn(info1);
        when(mMultiInternetManager.hasPendingConnectionRequests()).thenReturn(true);
        WorkSource testWorkSource = new WorkSource();
        // Set the connection pending status
        mMultiInternetConnectionStatusListenerCaptor.getValue().onStatusChange(
                MultiInternetManager.MULTI_INTERNET_STATE_CONNECTION_REQUESTED,
                testWorkSource);
        mMultiInternetConnectionStatusListenerCaptor.getValue().onStartScan(testWorkSource);
        verify(mSecondaryClientModeManager, times(2)).startConnectToNetwork(
                CANDIDATE_NETWORK_ID, Process.WIFI_UID, CANDIDATE_BSSID);
        verify(mSecondaryClientModeManager, times(2)).enableRoaming(false);
        verify(mActiveModeWarden, times(2)).requestSecondaryLongLivedClientModeManager(
                any(), any(), any(), any());

        // Simulate connection failing on the secondary
        clearInvocations(mSecondaryClientModeManager, mPrimaryClientModeManager, mWifiNS);
        mWifiConnectivityManager.handleConnectionAttemptEnded(
                mSecondaryClientModeManager,
                WifiMetrics.ConnectionEvent.FAILURE_ASSOCIATION_REJECTION,
                WifiMetricsProto.ConnectionEvent.FAILURE_REASON_UNKNOWN, CANDIDATE_BSSID,
                WifiConfigurationTestUtil.createPskNetwork(CANDIDATE_SSID));
        // verify connection is never restarted when a connection on the secondary STA fails.
        verify(mWifiNS, never()).selectNetwork(any());
        verify(mSecondaryClientModeManager, never()).startConnectToNetwork(
                anyInt(), anyInt(), any());
        verify(mPrimaryClientModeManager, never()).startConnectToNetwork(
                anyInt(), anyInt(), any());
    }

    /**
     *  Wifi enters disconnected state while screen is on.
     *
     * Expected behavior: WifiConnectivityManager calls
     * ClientModeManager.startConnectToNetwork() with the
     * expected candidate network ID and BSSID.
     */
    @Test
    public void enterWifiDisconnectedStateWhenScreenOn() {
        // Set screen to on
        setScreenState(true);

        // Set WiFi to disconnected state
        mWifiConnectivityManager.handleConnectionStateChanged(
                mPrimaryClientModeManager,
                WifiConnectivityManager.WIFI_STATE_DISCONNECTED);

        verify(mPrimaryClientModeManager).startConnectToNetwork(
                CANDIDATE_NETWORK_ID, Process.WIFI_UID, CANDIDATE_BSSID);
    }

    /**
     *  Wifi enters connected state while screen is on.
     *
     * Expected behavior: WifiConnectivityManager calls
     * ClientModeManager.startConnectToNetwork() with the
     * expected candidate network ID and BSSID.
     */
    @Test
    public void enterWifiConnectedStateWhenScreenOn() {
        // Set screen to on
        setScreenState(true);

        // Set WiFi to connected state
        setWifiStateConnected();
        mWifiConnectivityManager.handleConnectionStateChanged(
                mPrimaryClientModeManager,
                WifiConnectivityManager.WIFI_STATE_CONNECTED);

        verify(mPrimaryClientModeManager).startConnectToNetwork(
                CANDIDATE_NETWORK_ID, Process.WIFI_UID, CANDIDATE_BSSID);
    }

    /**
     *  Screen turned on while WiFi in disconnected state.
     *
     * Expected behavior: WifiConnectivityManager calls
     * ClientModeManager.startConnectToNetwork() with the
     * expected candidate network ID and BSSID.
     */
    @Test
    public void turnScreenOnWhenWifiInDisconnectedState() {
        // Set WiFi to disconnected state
        mWifiConnectivityManager.handleConnectionStateChanged(
                mPrimaryClientModeManager,
                WifiConnectivityManager.WIFI_STATE_DISCONNECTED);

        // Set screen to on
        setScreenState(true);

        verify(mPrimaryClientModeManager, atLeastOnce()).startConnectToNetwork(
                CANDIDATE_NETWORK_ID, Process.WIFI_UID, CANDIDATE_BSSID);
    }

    /**
     *  Screen turned on while WiFi in connected state.
     *
     * Expected behavior: WifiConnectivityManager calls
     * ClientModeManager.startConnectToNetwork() with the
     * expected candidate network ID and BSSID.
     */
    @Test
    public void turnScreenOnWhenWifiInConnectedState() {
        // Set WiFi to connected state
        setWifiStateConnected();

        // Set screen to on
        setScreenState(true);

        verify(mPrimaryClientModeManager, atLeastOnce()).startConnectToNetwork(
                CANDIDATE_NETWORK_ID, Process.WIFI_UID, CANDIDATE_BSSID);
    }

    /**
     *  Screen turned on while WiFi in connected state but
     *  auto roaming is disabled.
     *
     * Expected behavior: WifiConnectivityManager doesn't invoke
     * ClientModeManager.startConnectToNetwork() because roaming
     * is turned off.
     */
    @Test
    public void turnScreenOnWhenWifiInConnectedStateRoamingDisabled() {
        // Turn off auto roaming
        mResources.setBoolean(
                R.bool.config_wifi_framework_enable_associated_network_selection, false);
        mWifiConnectivityManager = createConnectivityManager();
        mWifiConnectivityManager.setTrustedConnectionAllowed(true);

        // Set WiFi to connected state
        setWifiStateConnected();

        // Set screen to on
        setScreenState(true);

        verify(mPrimaryClientModeManager, times(0)).startConnectToNetwork(
                CANDIDATE_NETWORK_ID, Process.WIFI_UID, CANDIDATE_BSSID);
    }

    /**
     * Multiple back to back connection attempts within the rate interval should be rate limited.
     *
     * Expected behavior: WifiConnectivityManager calls ClientModeManager.startConnectToNetwork()
     * with the expected candidate network ID and BSSID for only the expected number of times within
     * the given interval.
     */
    @Test
    public void connectionAttemptRateLimitedWhenScreenOff() {
        int maxAttemptRate = WifiConnectivityManager.MAX_CONNECTION_ATTEMPTS_RATE;
        int timeInterval = WifiConnectivityManager.MAX_CONNECTION_ATTEMPTS_TIME_INTERVAL_MS;
        int numAttempts = 0;
        int connectionAttemptIntervals = timeInterval / maxAttemptRate;

        setScreenState(false);

        // First attempt the max rate number of connections within the rate interval.
        long currentTimeStamp = 0;
        for (int attempt = 0; attempt < maxAttemptRate; attempt++) {
            currentTimeStamp += connectionAttemptIntervals;
            when(mClock.getElapsedSinceBootMillis()).thenReturn(currentTimeStamp);
            // Set WiFi to disconnected state to trigger PNO scan
            mWifiConnectivityManager.handleConnectionStateChanged(
                    mPrimaryClientModeManager,
                    WifiConnectivityManager.WIFI_STATE_DISCONNECTED);
            numAttempts++;
        }
        // Now trigger another connection attempt before the rate interval, this should be
        // skipped because we've crossed rate limit.
        when(mClock.getElapsedSinceBootMillis()).thenReturn(currentTimeStamp);
        // Set WiFi to disconnected state to trigger PNO scan
        mWifiConnectivityManager.handleConnectionStateChanged(
                mPrimaryClientModeManager,
                WifiConnectivityManager.WIFI_STATE_DISCONNECTED);

        // Verify that we attempt to connect upto the rate.
        verify(mPrimaryClientModeManager, times(numAttempts)).startConnectToNetwork(
                CANDIDATE_NETWORK_ID, Process.WIFI_UID, CANDIDATE_BSSID);
    }

    /**
     * Multiple back to back connection attempts outside the rate interval should not be rate
     * limited.
     *
     * Expected behavior: WifiConnectivityManager calls ClientModeManager.startConnectToNetwork()
     * with the expected candidate network ID and BSSID for only the expected number of times within
     * the given interval.
     */
    @Test
    public void connectionAttemptNotRateLimitedWhenScreenOff() {
        int maxAttemptRate = WifiConnectivityManager.MAX_CONNECTION_ATTEMPTS_RATE;
        int timeInterval = WifiConnectivityManager.MAX_CONNECTION_ATTEMPTS_TIME_INTERVAL_MS;
        int numAttempts = 0;
        int connectionAttemptIntervals = timeInterval / maxAttemptRate;

        setScreenState(false);

        // First attempt the max rate number of connections within the rate interval.
        long currentTimeStamp = 0;
        for (int attempt = 0; attempt < maxAttemptRate; attempt++) {
            currentTimeStamp += connectionAttemptIntervals;
            when(mClock.getElapsedSinceBootMillis()).thenReturn(currentTimeStamp);
            // Set WiFi to disconnected state to trigger PNO scan
            mWifiConnectivityManager.handleConnectionStateChanged(
                    mPrimaryClientModeManager,
                    WifiConnectivityManager.WIFI_STATE_DISCONNECTED);
            numAttempts++;
        }
        // Now trigger another connection attempt after the rate interval, this should not be
        // skipped because we should've evicted the older attempt.
        when(mClock.getElapsedSinceBootMillis()).thenReturn(
                currentTimeStamp + connectionAttemptIntervals * 2);
        // Set WiFi to disconnected state to trigger PNO scan
        mWifiConnectivityManager.handleConnectionStateChanged(
                mPrimaryClientModeManager,
                WifiConnectivityManager.WIFI_STATE_DISCONNECTED);
        numAttempts++;

        // Verify that all the connection attempts went through
        verify(mPrimaryClientModeManager, times(numAttempts)).startConnectToNetwork(
                CANDIDATE_NETWORK_ID, Process.WIFI_UID, CANDIDATE_BSSID);
    }

    /**
     * Multiple back to back connection attempts after a force connectivity scan should not be rate
     * limited.
     *
     * Expected behavior: WifiConnectivityManager calls ClientModeManager.startConnectToNetwork()
     * with the expected candidate network ID and BSSID for only the expected number of times within
     * the given interval.
     */
    @Test
    public void connectionAttemptNotRateLimitedWhenScreenOffForceConnectivityScan() {
        int maxAttemptRate = WifiConnectivityManager.MAX_CONNECTION_ATTEMPTS_RATE;
        int timeInterval = WifiConnectivityManager.MAX_CONNECTION_ATTEMPTS_TIME_INTERVAL_MS;
        int numAttempts = 0;
        int connectionAttemptIntervals = timeInterval / maxAttemptRate;

        setScreenState(false);

        // First attempt the max rate number of connections within the rate interval.
        long currentTimeStamp = 0;
        for (int attempt = 0; attempt < maxAttemptRate; attempt++) {
            currentTimeStamp += connectionAttemptIntervals;
            when(mClock.getElapsedSinceBootMillis()).thenReturn(currentTimeStamp);
            // Set WiFi to disconnected state to trigger PNO scan
            mWifiConnectivityManager.handleConnectionStateChanged(
                    mPrimaryClientModeManager,
                    WifiConnectivityManager.WIFI_STATE_DISCONNECTED);
            numAttempts++;
        }

        mWifiConnectivityManager.forceConnectivityScan(new WorkSource());

        for (int attempt = 0; attempt < maxAttemptRate; attempt++) {
            currentTimeStamp += connectionAttemptIntervals;
            when(mClock.getElapsedSinceBootMillis()).thenReturn(currentTimeStamp);
            // Set WiFi to disconnected state to trigger PNO scan
            mWifiConnectivityManager.handleConnectionStateChanged(
                    mPrimaryClientModeManager,
                    WifiConnectivityManager.WIFI_STATE_DISCONNECTED);
            numAttempts++;
        }

        // Verify that all the connection attempts went through
        verify(mPrimaryClientModeManager, times(numAttempts)).startConnectToNetwork(
                CANDIDATE_NETWORK_ID, Process.WIFI_UID, CANDIDATE_BSSID);
    }

    /**
     * Multiple back to back connection attempts after a user selection should not be rate limited.
     *
     * Expected behavior: WifiConnectivityManager calls ClientModeManager.startConnectToNetwork()
     * with the expected candidate network ID and BSSID for only the expected number of times within
     * the given interval.
     */
    @Test
    public void connectionAttemptNotRateLimitedWhenScreenOffAfterUserSelection() {
        int maxAttemptRate = WifiConnectivityManager.MAX_CONNECTION_ATTEMPTS_RATE;
        int timeInterval = WifiConnectivityManager.MAX_CONNECTION_ATTEMPTS_TIME_INTERVAL_MS;
        int numAttempts = 0;
        int connectionAttemptIntervals = timeInterval / maxAttemptRate;

        setScreenState(false);

        // First attempt the max rate number of connections within the rate interval.
        long currentTimeStamp = 0;
        for (int attempt = 0; attempt < maxAttemptRate; attempt++) {
            currentTimeStamp += connectionAttemptIntervals;
            when(mClock.getElapsedSinceBootMillis()).thenReturn(currentTimeStamp);
            // Set WiFi to disconnected state to trigger PNO scan
            mWifiConnectivityManager.handleConnectionStateChanged(
                    mPrimaryClientModeManager,
                    WifiConnectivityManager.WIFI_STATE_DISCONNECTED);
            numAttempts++;
        }

        mWifiConnectivityManager.prepareForForcedConnection(CANDIDATE_NETWORK_ID);

        for (int attempt = 0; attempt < maxAttemptRate; attempt++) {
            currentTimeStamp += connectionAttemptIntervals;
            when(mClock.getElapsedSinceBootMillis()).thenReturn(currentTimeStamp);
            // Set WiFi to disconnected state to trigger PNO scan
            mWifiConnectivityManager.handleConnectionStateChanged(
                    mPrimaryClientModeManager,
                    WifiConnectivityManager.WIFI_STATE_DISCONNECTED);
            numAttempts++;
        }

        // Verify that all the connection attempts went through
        verify(mPrimaryClientModeManager, times(numAttempts)).startConnectToNetwork(
                CANDIDATE_NETWORK_ID, Process.WIFI_UID, CANDIDATE_BSSID);
    }

    /**
     * Multiple back to back connection attempts after a wifi toggle should not be rate limited.
     *
     * Expected behavior: WifiConnectivityManager calls ClientModeManager.startConnectToNetwork()
     * with the expected candidate network ID and BSSID for only the expected number of times within
     * the given interval.
     */
    @Test
    public void connectionAttemptNotRateLimitedWhenScreenOffAfterWifiToggle() {
        int maxAttemptRate = WifiConnectivityManager.MAX_CONNECTION_ATTEMPTS_RATE;
        int timeInterval = WifiConnectivityManager.MAX_CONNECTION_ATTEMPTS_TIME_INTERVAL_MS;
        int numAttempts = 0;
        int connectionAttemptIntervals = timeInterval / maxAttemptRate;

        setScreenState(false);

        // First attempt the max rate number of connections within the rate interval.
        long currentTimeStamp = 0;
        for (int attempt = 0; attempt < maxAttemptRate; attempt++) {
            currentTimeStamp += connectionAttemptIntervals;
            when(mClock.getElapsedSinceBootMillis()).thenReturn(currentTimeStamp);
            // Set WiFi to disconnected state to trigger PNO scan
            mWifiConnectivityManager.handleConnectionStateChanged(
                    mPrimaryClientModeManager,
                    WifiConnectivityManager.WIFI_STATE_DISCONNECTED);
            numAttempts++;
        }

        setWifiEnabled(false);
        setWifiEnabled(true);

        for (int attempt = 0; attempt < maxAttemptRate; attempt++) {
            currentTimeStamp += connectionAttemptIntervals;
            when(mClock.getElapsedSinceBootMillis()).thenReturn(currentTimeStamp);
            // Set WiFi to disconnected state to trigger PNO scan
            mWifiConnectivityManager.handleConnectionStateChanged(
                    mPrimaryClientModeManager,
                    WifiConnectivityManager.WIFI_STATE_DISCONNECTED);
            numAttempts++;
        }

        // Verify that all the connection attempts went through
        verify(mPrimaryClientModeManager, times(numAttempts)).startConnectToNetwork(
                CANDIDATE_NETWORK_ID, Process.WIFI_UID, CANDIDATE_BSSID);
    }

    /**
     *  PNO retry for low RSSI networks.
     *
     * Expected behavior: WifiConnectivityManager doubles the low RSSI
     * network retry delay value after QNS skips the PNO scan results
     * because of their low RSSI values and reaches max after three scans
     */
    @Test
    public void pnoRetryForLowRssiNetwork() {
        when(mWifiNS.selectNetwork(any())).thenReturn(null);

        // Set screen to off
        setScreenState(false);

        // Set WiFi to disconnected state to trigger PNO scan
        mWifiConnectivityManager.handleConnectionStateChanged(
                mPrimaryClientModeManager,
                WifiConnectivityManager.WIFI_STATE_DISCONNECTED);

        verify(mWifiMetrics).noteFirstNetworkSelectionAfterBoot(false);

        // Get the retry delay value after QNS didn't select a
        // network candidate from the PNO scan results.
        int lowRssiNetworkRetryDelayAfterOnePnoMs = mWifiConnectivityManager
                .getLowRssiNetworkRetryDelay();

        assertEquals(LOW_RSSI_NETWORK_RETRY_START_DELAY_SEC * 2000,
                lowRssiNetworkRetryDelayAfterOnePnoMs);

        // Set WiFi to disconnected state to trigger two more PNO scans
        for (int i = 0; i < 2; i++) {
            mWifiConnectivityManager.handleConnectionStateChanged(
                    mPrimaryClientModeManager,
                    WifiConnectivityManager.WIFI_STATE_DISCONNECTED);
        }
        int lowRssiNetworkRetryDelayAfterThreePnoMs = mWifiConnectivityManager
                .getLowRssiNetworkRetryDelay();
        assertEquals(LOW_RSSI_NETWORK_RETRY_MAX_DELAY_SEC * 1000,
                lowRssiNetworkRetryDelayAfterThreePnoMs);
    }

    /**
     * Ensure that the watchdog bite increments the "Pno bad" metric.
     *
     * Expected behavior: WifiConnectivityManager detects that the PNO scan failed to find
     * a candidate while watchdog single scan did.
     */
    @Test
    public void watchdogBitePnoBadIncrementsMetrics() {
        // Set screen to off
        setScreenState(false);

        // Set WiFi to disconnected state to trigger PNO scan
        mWifiConnectivityManager.handleConnectionStateChanged(
                mPrimaryClientModeManager,
                WifiConnectivityManager.WIFI_STATE_DISCONNECTED);

        // Now fire the watchdog alarm and verify the metrics were incremented.
        mAlarmManager.dispatch(WifiConnectivityManager.WATCHDOG_TIMER_TAG);
        mLooper.dispatchAll();

        verify(mWifiMetrics).incrementNumConnectivityWatchdogPnoBad();
        verify(mWifiMetrics, never()).incrementNumConnectivityWatchdogPnoGood();
    }

    /**
     * Ensure that the watchdog bite increments the "Pno good" metric.
     *
     * Expected behavior: WifiConnectivityManager detects that the PNO scan failed to find
     * a candidate which was the same with watchdog single scan.
     */
    @Test
    public void watchdogBitePnoGoodIncrementsMetrics() {
        // Qns returns no candidate after watchdog single scan.
        when(mWifiNS.selectNetwork(any())).thenReturn(null);

        // Set screen to off
        setScreenState(false);

        // Set WiFi to disconnected state to trigger PNO scan
        mWifiConnectivityManager.handleConnectionStateChanged(
                mPrimaryClientModeManager,
                WifiConnectivityManager.WIFI_STATE_DISCONNECTED);

        // Now fire the watchdog alarm and verify the metrics were incremented.
        mAlarmManager.dispatch(WifiConnectivityManager.WATCHDOG_TIMER_TAG);
        mLooper.dispatchAll();

        verify(mWifiMetrics).incrementNumConnectivityWatchdogPnoGood();
        verify(mWifiMetrics, never()).incrementNumConnectivityWatchdogPnoBad();
    }

    @Test
    public void testNetworkConnectionCancelWatchdogTimer() {
        // Set screen to off
        setScreenState(false);

        // Set WiFi to disconnected state to trigger PNO scan
        mWifiConnectivityManager.handleConnectionStateChanged(
                mPrimaryClientModeManager,
                WifiConnectivityManager.WIFI_STATE_DISCONNECTED);

        // Verify the watchdog alarm has been set
        assertTrue(mAlarmManager.isPending(WifiConnectivityManager.WATCHDOG_TIMER_TAG));

        // Set WiFi to connected
        setWifiStateConnected();

        // Verify the watchdog alarm has been canceled
        assertFalse(mAlarmManager.isPending(WifiConnectivityManager.WATCHDOG_TIMER_TAG));
    }

    /**
     * Verify that 2 scans that are sufficiently far apart are required to initiate a connection
     * when the high mobility scanning optimization is enabled.
     */
    @Test
    public void testHighMovementNetworkSelection() {
        when(mClock.getElapsedSinceBootMillis()).thenReturn(0L);
        // Enable high movement optimization
        mResources.setBoolean(R.bool.config_wifiHighMovementNetworkSelectionOptimizationEnabled,
                true);
        mWifiConnectivityManager.setDeviceMobilityState(
                WifiManager.DEVICE_MOBILITY_STATE_HIGH_MVMT);

        // Set WiFi to disconnected state to trigger scan
        mWifiConnectivityManager.handleConnectionStateChanged(
                mPrimaryClientModeManager,
                WifiConnectivityManager.WIFI_STATE_DISCONNECTED);
        mLooper.dispatchAll();

        // Verify there is no connection due to currently having no cached candidates.
        verify(mPrimaryClientModeManager, never()).startConnectToNetwork(
                CANDIDATE_NETWORK_ID, Process.WIFI_UID, CANDIDATE_BSSID);

        // Move time forward but do not cross HIGH_MVMT_SCAN_DELAY_MS yet.
        when(mClock.getElapsedSinceBootMillis()).thenReturn(HIGH_MVMT_SCAN_DELAY_MS - 1L);
        // Set WiFi to disconnected state to trigger scan
        mWifiConnectivityManager.handleConnectionStateChanged(
                mPrimaryClientModeManager,
                WifiConnectivityManager.WIFI_STATE_DISCONNECTED);
        mLooper.dispatchAll();

        // Verify we still don't connect because not enough time have passed since the candidates
        // were cached.
        verify(mPrimaryClientModeManager, never()).startConnectToNetwork(
                CANDIDATE_NETWORK_ID, Process.WIFI_UID, CANDIDATE_BSSID);

        // Move time past HIGH_MVMT_SCAN_DELAY_MS.
        when(mClock.getElapsedSinceBootMillis()).thenReturn((long) HIGH_MVMT_SCAN_DELAY_MS);
        // Set WiFi to disconnected state to trigger scan
        mWifiConnectivityManager.handleConnectionStateChanged(
                mPrimaryClientModeManager,
                WifiConnectivityManager.WIFI_STATE_DISCONNECTED);
        mLooper.dispatchAll();

        // Verify a candidate if found this time.
        verify(mPrimaryClientModeManager).startConnectToNetwork(
                CANDIDATE_NETWORK_ID, Process.WIFI_UID, CANDIDATE_BSSID);
        verify(mWifiMetrics, times(2)).incrementNumHighMovementConnectionSkipped();
        verify(mWifiMetrics).incrementNumHighMovementConnectionStarted();
    }

    /**
     * Verify that the device is initiating partial scans to verify AP stability in the high
     * movement mobility state.
     */
    @Test
    public void testHighMovementTriggerPartialScan() {
        when(mClock.getElapsedSinceBootMillis()).thenReturn(0L);
        // Enable high movement optimization
        mResources.setBoolean(R.bool.config_wifiHighMovementNetworkSelectionOptimizationEnabled,
                true);
        mWifiConnectivityManager.setDeviceMobilityState(
                WifiManager.DEVICE_MOBILITY_STATE_HIGH_MVMT);

        // Set WiFi to disconnected state to trigger scan
        mWifiConnectivityManager.handleConnectionStateChanged(
                mPrimaryClientModeManager,
                WifiConnectivityManager.WIFI_STATE_DISCONNECTED);
        mLooper.dispatchAll();
        // Verify there is no connection due to currently having no cached candidates.
        verify(mPrimaryClientModeManager, never()).startConnectToNetwork(
                CANDIDATE_NETWORK_ID, Process.WIFI_UID, CANDIDATE_BSSID);

        // Move time forward and verify that a delayed partial scan is scheduled.
        when(mClock.getElapsedSinceBootMillis()).thenReturn(HIGH_MVMT_SCAN_DELAY_MS + 1L);
        mAlarmManager.dispatch(WifiConnectivityManager.DELAYED_PARTIAL_SCAN_TIMER_TAG);
        mLooper.dispatchAll();

        verify(mWifiScanner).startScan((ScanSettings) argThat(new WifiPartialScanSettingMatcher()),
                any(), any(), any());
    }

    private class WifiPartialScanSettingMatcher implements ArgumentMatcher<ScanSettings> {
        @Override
        public boolean matches(ScanSettings scanSettings) {
            return scanSettings.band == WifiScanner.WIFI_BAND_UNSPECIFIED
                    && scanSettings.channels[0].frequency == TEST_FREQUENCY;
        }
    }

    /**
     * Verify that when there are we obtain more than one valid candidates from scan results and
     * network connection fails, connection is immediately retried on the remaining candidates.
     */
    @Test
    public void testRetryConnectionOnFailure() {
        // Setup WifiNetworkSelector to return 2 valid candidates from scan results
        MacAddress macAddress = MacAddress.fromString(CANDIDATE_BSSID_2);
        WifiCandidates.Key key = new WifiCandidates.Key(mock(ScanResultMatchInfo.class),
                macAddress, 0, WifiConfiguration.SECURITY_TYPE_OPEN);
        WifiCandidates.Candidate otherCandidate = mock(WifiCandidates.Candidate.class);
        when(otherCandidate.getKey()).thenReturn(key);
        List<WifiCandidates.Candidate> candidateList = new ArrayList<>();
        candidateList.add(mCandidate1);
        candidateList.add(otherCandidate);
        when(mWifiNS.getCandidatesFromScan(any(), any(), any(), anyBoolean(), anyBoolean(),
                anyBoolean(), any(), anyBoolean())).thenReturn(candidateList);

        // Set WiFi to disconnected state to trigger scan
        mWifiConnectivityManager.handleConnectionStateChanged(
                mPrimaryClientModeManager,
                WifiConnectivityManager.WIFI_STATE_DISCONNECTED);
        mLooper.dispatchAll();
        // Verify a connection starting
        verify(mWifiNS).selectNetwork((List<WifiCandidates.Candidate>)
                argThat(new WifiCandidatesListSizeMatcher(2)));
        verify(mPrimaryClientModeManager).startConnectToNetwork(anyInt(), anyInt(), any());

        // Simulate the connection failing
        WifiConfiguration config = WifiConfigurationTestUtil.createPskNetwork(CANDIDATE_SSID);
        mWifiConnectivityManager.handleConnectionAttemptEnded(
                mPrimaryClientModeManager,
                WifiMetrics.ConnectionEvent.FAILURE_ASSOCIATION_REJECTION,
                WifiMetricsProto.ConnectionEvent.FAILURE_REASON_UNKNOWN, CANDIDATE_BSSID,
                config);
        // Verify the failed BSSID is added to blocklist
        verify(mWifiBlocklistMonitor).blockBssidForDurationMs(eq(CANDIDATE_BSSID),
                eq(config), anyLong(), anyInt(), anyInt());
        // Verify another connection starting
        verify(mWifiNS).selectNetwork((List<WifiCandidates.Candidate>)
                argThat(new WifiCandidatesListSizeMatcher(1)));
        verify(mPrimaryClientModeManager, times(2)).startConnectToNetwork(
                anyInt(), anyInt(), any());

        // Simulate the second connection also failing
        mWifiConnectivityManager.handleConnectionAttemptEnded(
                mPrimaryClientModeManager,
                WifiMetrics.ConnectionEvent.FAILURE_ASSOCIATION_REJECTION,
                WifiMetricsProto.ConnectionEvent.FAILURE_REASON_UNKNOWN, CANDIDATE_BSSID_2,
                config);
        // Verify there are no more connections
        verify(mWifiNS).selectNetwork((List<WifiCandidates.Candidate>)
                argThat(new WifiCandidatesListSizeMatcher(0)));
        verify(mPrimaryClientModeManager, times(2)).startConnectToNetwork(
                anyInt(), anyInt(), any());
    }

    @Test
    public void testRetryConnectionEapFailureIgnoreSameNetwork() {
        // Setup WifiNetworkSelector to return 2 valid candidates with the same
        // ScanResultMatchInfo so they are the same network, but different BSSID.
        ScanResultMatchInfo matchInfo = ScanResultMatchInfo.fromWifiConfiguration(
                mCandidateWifiConfig1);
        WifiCandidates.Key key = new WifiCandidates.Key(matchInfo,
                MacAddress.fromString(CANDIDATE_BSSID), 0);
        WifiCandidates.Key key2 = new WifiCandidates.Key(matchInfo,
                MacAddress.fromString(CANDIDATE_BSSID_2), 0);
        WifiCandidates.Candidate candidate1 = mock(WifiCandidates.Candidate.class);
        when(candidate1.getKey()).thenReturn(key);
        WifiCandidates.Candidate candidate2 = mock(WifiCandidates.Candidate.class);
        when(candidate2.getKey()).thenReturn(key2);
        List<WifiCandidates.Candidate> candidateList = new ArrayList<>();
        candidateList.add(candidate1);
        candidateList.add(candidate2);
        when(mWifiNS.getCandidatesFromScan(any(), any(), any(), anyBoolean(), anyBoolean(),
                anyBoolean(), any(), anyBoolean())).thenReturn(candidateList);

        // Set WiFi to disconnected state to trigger scan
        mWifiConnectivityManager.handleConnectionStateChanged(
                mPrimaryClientModeManager,
                WifiConnectivityManager.WIFI_STATE_DISCONNECTED);
        mLooper.dispatchAll();
        // Verify a connection starting
        verify(mWifiNS).selectNetwork((List<WifiCandidates.Candidate>)
                argThat(new WifiCandidatesListSizeMatcher(2)));
        verify(mPrimaryClientModeManager).startConnectToNetwork(anyInt(), anyInt(), any());

        // Simulate the connection failing
        mWifiConnectivityManager.handleConnectionAttemptEnded(
                mPrimaryClientModeManager,
                WifiMetrics.ConnectionEvent.FAILURE_AUTHENTICATION_FAILURE,
                WifiMetricsProto.ConnectionEvent.AUTH_FAILURE_EAP_FAILURE, CANDIDATE_BSSID,
                mCandidateWifiConfig1);
        mLooper.dispatchAll();
        // verify no there is no retry.
        verify(mPrimaryClientModeManager).startConnectToNetwork(anyInt(), anyInt(), any());
    }

    private class WifiCandidatesListSizeMatcher implements
            ArgumentMatcher<List<WifiCandidates.Candidate>> {
        int mSize;
        WifiCandidatesListSizeMatcher(int size) {
            mSize = size;
        }
        @Override
        public boolean matches(List<WifiCandidates.Candidate> candidateList) {
            return candidateList.size() == mSize;
        }
    }

    /**
     * Verify that the cached candidates become cleared after a period of time.
     */
    @Test
    public void testRetryConnectionOnFailureCacheTimeout() {
        // Setup WifiNetworkSelector to return 2 valid candidates from scan results
        when(mClock.getElapsedSinceBootMillis()).thenReturn(0L);
        MacAddress macAddress = MacAddress.fromString(CANDIDATE_BSSID_2);
        WifiCandidates.Key key = new WifiCandidates.Key(mock(ScanResultMatchInfo.class),
                macAddress, 0, WifiConfiguration.SECURITY_TYPE_OPEN);
        WifiCandidates.Candidate otherCandidate = mock(WifiCandidates.Candidate.class);
        when(otherCandidate.getKey()).thenReturn(key);
        List<WifiCandidates.Candidate> candidateList = new ArrayList<>();
        candidateList.add(mCandidate1);
        candidateList.add(otherCandidate);
        when(mWifiNS.getCandidatesFromScan(any(), any(), any(), anyBoolean(), anyBoolean(),
                anyBoolean(), any(), anyBoolean())).thenReturn(candidateList);

        // Set WiFi to disconnected state to trigger scan
        mWifiConnectivityManager.handleConnectionStateChanged(
                mPrimaryClientModeManager,
                WifiConnectivityManager.WIFI_STATE_DISCONNECTED);
        mLooper.dispatchAll();
        // Verify a connection starting
        verify(mWifiNS).selectNetwork((List<WifiCandidates.Candidate>)
                argThat(new WifiCandidatesListSizeMatcher(2)));
        verify(mPrimaryClientModeManager).startConnectToNetwork(anyInt(), anyInt(), any());

        // Simulate the connection failing after the cache timeout period.
        when(mClock.getElapsedSinceBootMillis()).thenReturn(TEMP_BSSID_BLOCK_DURATION_MS + 1L);
        WifiConfiguration config = WifiConfigurationTestUtil.createPskNetwork(CANDIDATE_SSID);
        mWifiConnectivityManager.handleConnectionAttemptEnded(
                mPrimaryClientModeManager,
                WifiMetrics.ConnectionEvent.FAILURE_ASSOCIATION_REJECTION,
                WifiMetricsProto.ConnectionEvent.FAILURE_REASON_UNKNOWN, CANDIDATE_BSSID,
                config);
        // verify there are no additional connections.
        verify(mPrimaryClientModeManager).startConnectToNetwork(anyInt(), anyInt(), any());
    }

    /**
     * Verify that when cached candidates get cleared there will no longer be retries after a
     * connection failure.
     */
    @Test
    public void testNoRetryConnectionOnFailureAfterCacheCleared() {
        // Setup WifiNetworkSelector to return 2 valid candidates from scan results
        MacAddress macAddress = MacAddress.fromString(CANDIDATE_BSSID_2);
        WifiCandidates.Key key = new WifiCandidates.Key(mock(ScanResultMatchInfo.class),
                macAddress, 0, WifiConfiguration.SECURITY_TYPE_OPEN);
        WifiCandidates.Candidate otherCandidate = mock(WifiCandidates.Candidate.class);
        when(otherCandidate.getKey()).thenReturn(key);
        List<WifiCandidates.Candidate> candidateList = new ArrayList<>();
        candidateList.add(mCandidate1);
        candidateList.add(otherCandidate);
        when(mWifiNS.getCandidatesFromScan(any(), any(), any(), anyBoolean(), anyBoolean(),
                anyBoolean(), any(), anyBoolean())).thenReturn(candidateList);

        // Set WiFi to disconnected state to trigger scan
        mWifiConnectivityManager.handleConnectionStateChanged(
                mPrimaryClientModeManager,
                WifiConnectivityManager.WIFI_STATE_DISCONNECTED);
        mLooper.dispatchAll();
        // Verify a connection starting
        verify(mWifiNS).selectNetwork((List<WifiCandidates.Candidate>)
                argThat(new WifiCandidatesListSizeMatcher(2)));
        verify(mPrimaryClientModeManager).startConnectToNetwork(anyInt(), anyInt(), any());

        // now clear the cached candidates
        mWifiConnectivityManager.clearCachedCandidates();

        // Simulate the connection failing
        WifiConfiguration config = WifiConfigurationTestUtil.createPskNetwork(CANDIDATE_SSID);
        mWifiConnectivityManager.handleConnectionAttemptEnded(
                mPrimaryClientModeManager,
                WifiMetrics.ConnectionEvent.FAILURE_ASSOCIATION_REJECTION,
                WifiMetricsProto.ConnectionEvent.FAILURE_REASON_UNKNOWN, CANDIDATE_BSSID,
                config);

        // Verify there no re-attempt to connect
        verify(mPrimaryClientModeManager).startConnectToNetwork(anyInt(), anyInt(), any());
    }

    /**
     * Verify that the cached candidates that become disabled are not selected for connection.
     */
    @Test
    public void testRetryConnectionIgnoresDisabledNetworks() {
        // Setup WifiNetworkSelector to return 2 valid candidates from scan results
        int testOtherNetworkNetworkId = 123;
        MacAddress macAddress = MacAddress.fromString(CANDIDATE_BSSID_2);
        WifiCandidates.Key key = new WifiCandidates.Key(mock(ScanResultMatchInfo.class),
                macAddress, testOtherNetworkNetworkId, WifiConfiguration.SECURITY_TYPE_OPEN);
        WifiCandidates.Candidate otherCandidate = mock(WifiCandidates.Candidate.class);
        when(otherCandidate.getKey()).thenReturn(key);
        List<WifiCandidates.Candidate> candidateList = new ArrayList<>();
        candidateList.add(mCandidate1);
        candidateList.add(otherCandidate);
        when(mWifiNS.getCandidatesFromScan(any(), any(), any(), anyBoolean(), anyBoolean(),
                anyBoolean(), any(), anyBoolean())).thenReturn(candidateList);

        // Set WiFi to disconnected state to trigger scan
        mWifiConnectivityManager.handleConnectionStateChanged(
                mPrimaryClientModeManager,
                WifiConnectivityManager.WIFI_STATE_DISCONNECTED);
        mLooper.dispatchAll();
        // Verify a connection starting
        verify(mWifiNS).selectNetwork((List<WifiCandidates.Candidate>)
                argThat(new WifiCandidatesListSizeMatcher(2)));
        verify(mPrimaryClientModeManager).startConnectToNetwork(anyInt(), anyInt(), any());

        // make sure the configuration for otherCandidate is disabled, and verify there is no
        // connection attempt after the disconnect happens.
        when(otherCandidate.getNetworkConfigId()).thenReturn(testOtherNetworkNetworkId);
        WifiConfiguration candidateOtherConfig = WifiConfigurationTestUtil.createOpenNetwork();
        candidateOtherConfig.getNetworkSelectionStatus().setNetworkSelectionStatus(
                WifiConfiguration.NetworkSelectionStatus.NETWORK_SELECTION_PERMANENTLY_DISABLED);
        when(mWifiConfigManager.getConfiguredNetwork(testOtherNetworkNetworkId))
                .thenReturn(candidateOtherConfig);

        // Simulate the connection failing
        WifiConfiguration config = WifiConfigurationTestUtil.createPskNetwork(CANDIDATE_SSID);
        mWifiConnectivityManager.handleConnectionAttemptEnded(
                mPrimaryClientModeManager,
                WifiMetrics.ConnectionEvent.FAILURE_ASSOCIATION_REJECTION,
                WifiMetricsProto.ConnectionEvent.FAILURE_REASON_UNKNOWN, CANDIDATE_BSSID,
                config);

        // Verify no more connections since there are 0 valid candidates remaining.
        verify(mWifiNS).selectNetwork((List<WifiCandidates.Candidate>)
                argThat(new WifiCandidatesListSizeMatcher(0)));
        verify(mPrimaryClientModeManager).startConnectToNetwork(anyInt(), anyInt(), any());
    }

    /**
     * Verify that in the high movement mobility state, when the RSSI delta of a BSSID from
     * 2 consecutive scans becomes greater than a threshold, the candidate get ignored from
     * network selection.
     */
    @Test
    public void testHighMovementRssiFilter() {
        when(mClock.getElapsedSinceBootMillis()).thenReturn(0L);
        // Enable high movement optimization
        mResources.setBoolean(R.bool.config_wifiHighMovementNetworkSelectionOptimizationEnabled,
                true);
        mWifiConnectivityManager.setDeviceMobilityState(
                WifiManager.DEVICE_MOBILITY_STATE_HIGH_MVMT);

        // Set WiFi to disconnected state to trigger scan
        mWifiConnectivityManager.handleConnectionStateChanged(
                mPrimaryClientModeManager,
                WifiConnectivityManager.WIFI_STATE_DISCONNECTED);
        mLooper.dispatchAll();

        // Verify there is no connection due to currently having no cached candidates.
        verify(mPrimaryClientModeManager, never()).startConnectToNetwork(
                CANDIDATE_NETWORK_ID, Process.WIFI_UID, CANDIDATE_BSSID);

        // Move time past HIGH_MVMT_SCAN_DELAY_MS.
        when(mClock.getElapsedSinceBootMillis()).thenReturn((long) HIGH_MVMT_SCAN_DELAY_MS);

        // Mock the current Candidate to have RSSI over the filter threshold
        mCandidateList.clear();
        mCandidateList.add(mCandidate2);

        // Set WiFi to disconnected state to trigger scan
        mWifiConnectivityManager.handleConnectionStateChanged(
                mPrimaryClientModeManager,
                WifiConnectivityManager.WIFI_STATE_DISCONNECTED);
        mLooper.dispatchAll();

        // Verify connect is not started.
        verify(mPrimaryClientModeManager, never()).startConnectToNetwork(
                CANDIDATE_NETWORK_ID, Process.WIFI_UID, CANDIDATE_BSSID);
        verify(mWifiMetrics, times(2)).incrementNumHighMovementConnectionSkipped();
    }

    /**
     * {@link OpenNetworkNotifier} handles scan results on network selection.
     *
     * Expected behavior: ONA handles scan results
     */
    @Test
    public void wifiDisconnected_noCandidateInSelect_openNetworkNotifierScanResultsHandled() {
        // no connection candidate selected
        when(mWifiNS.selectNetwork(any())).thenReturn(null);

        List<ScanDetail> expectedOpenNetworks = new ArrayList<>();
        expectedOpenNetworks.add(
                new ScanDetail(
                        new ScanResult(WifiSsid.fromUtf8Text(CANDIDATE_SSID),
                                CANDIDATE_SSID, CANDIDATE_BSSID, 1245, 0, "some caps", -78, 2450,
                                1025, 22, 33, 20, 0, 0, true)));

        when(mWifiNS.getFilteredScanDetailsForOpenUnsavedNetworks())
                .thenReturn(expectedOpenNetworks);

        // Set WiFi to disconnected state to trigger PNO scan
        mWifiConnectivityManager.handleConnectionStateChanged(
                mPrimaryClientModeManager,
                WifiConnectivityManager.WIFI_STATE_DISCONNECTED);

        verify(mOpenNetworkNotifier).handleScanResults(expectedOpenNetworks);
    }

    /**
     * {@link OpenNetworkNotifier} handles scan results on network selection.
     *
     * Expected behavior: ONA handles scan results
     */
    @Test
    public void wifiDisconnected_noCandidatesInScan_openNetworkNotifierScanResultsHandled() {
        // no connection candidates from scan.
        when(mWifiNS.getCandidatesFromScan(any(), any(), any(), anyBoolean(), anyBoolean(),
                anyBoolean(), any(), anyBoolean())).thenReturn(null);

        List<ScanDetail> expectedOpenNetworks = new ArrayList<>();
        expectedOpenNetworks.add(
                new ScanDetail(
                        new ScanResult(WifiSsid.fromUtf8Text(CANDIDATE_SSID),
                                CANDIDATE_SSID, CANDIDATE_BSSID, 1245, 0, "some caps", -78, 2450,
                                1025, 22, 33, 20, 0, 0, true)));

        when(mWifiNS.getFilteredScanDetailsForOpenUnsavedNetworks())
                .thenReturn(expectedOpenNetworks);

        // Set WiFi to disconnected state to trigger PNO scan
        mWifiConnectivityManager.handleConnectionStateChanged(
                mPrimaryClientModeManager,
                WifiConnectivityManager.WIFI_STATE_DISCONNECTED);

        verify(mOpenNetworkNotifier).handleScanResults(expectedOpenNetworks);
    }

    /**
     * When wifi is connected, {@link OpenNetworkNotifier} handles the Wi-Fi connected behavior.
     *
     * Expected behavior: ONA handles connected behavior
     */
    @Test
    public void wifiConnected_openNetworkNotifierHandlesConnection() {
        // Set WiFi to connected state
        mWifiInfo.setSSID(WifiSsid.fromUtf8Text(CANDIDATE_SSID));
        WifiConfiguration config = WifiConfigurationTestUtil.createPskNetwork(CANDIDATE_SSID);
        mWifiConnectivityManager.handleConnectionAttemptEnded(
                mPrimaryClientModeManager,
                WifiMetrics.ConnectionEvent.FAILURE_NONE,
                WifiMetricsProto.ConnectionEvent.FAILURE_REASON_UNKNOWN, CANDIDATE_BSSID, config);
        verify(mOpenNetworkNotifier).handleWifiConnected(CANDIDATE_SSID);
    }

    /**
     * When wifi is connected, {@link OpenNetworkNotifier} handles connection state
     * change.
     *
     * Expected behavior: ONA does not clear pending notification.
     */
    @Test
    public void wifiDisconnected_openNetworkNotifierDoesNotClearPendingNotification() {
        // Set WiFi to disconnected state
        mWifiConnectivityManager.handleConnectionStateChanged(
                mPrimaryClientModeManager,
                WifiConnectivityManager.WIFI_STATE_DISCONNECTED);

        verify(mOpenNetworkNotifier, never()).clearPendingNotification(anyBoolean());
    }

    /**
     * When a Wi-Fi connection attempt ends, {@link OpenNetworkNotifier} handles the connection
     * failure. A failure code that is not {@link WifiMetrics.ConnectionEvent#FAILURE_NONE}
     * represents a connection failure.
     *
     * Expected behavior: ONA handles connection failure.
     */
    @Test
    public void wifiConnectionEndsWithFailure_openNetworkNotifierHandlesConnectionFailure() {
        WifiConfiguration config = WifiConfigurationTestUtil.createPskNetwork(CANDIDATE_SSID);
        mWifiConnectivityManager.handleConnectionAttemptEnded(
                mPrimaryClientModeManager,
                WifiMetrics.ConnectionEvent.FAILURE_CONNECT_NETWORK_FAILED,
                WifiMetricsProto.ConnectionEvent.FAILURE_REASON_UNKNOWN, CANDIDATE_BSSID,
                config);

        verify(mOpenNetworkNotifier).handleConnectionFailure();
    }

    /**
     * When a Wi-Fi connection attempt ends, {@link OpenNetworkNotifier} does not handle connection
     * failure after a successful connection. {@link WifiMetrics.ConnectionEvent#FAILURE_NONE}
     * represents a successful connection.
     *
     * Expected behavior: ONA does nothing.
     */
    @Test
    public void wifiConnectionEndsWithSuccess_openNetworkNotifierDoesNotHandleConnectionFailure() {
        WifiConfiguration config = WifiConfigurationTestUtil.createPskNetwork(CANDIDATE_SSID);
        mWifiConnectivityManager.handleConnectionAttemptEnded(
                mPrimaryClientModeManager,
                WifiMetrics.ConnectionEvent.FAILURE_NONE,
                WifiMetricsProto.ConnectionEvent.FAILURE_REASON_UNKNOWN, CANDIDATE_BSSID, config);

        verify(mOpenNetworkNotifier, never()).handleConnectionFailure();
    }

    /**
     * When Wi-Fi is disabled, clear the pending notification and reset notification repeat delay.
     *
     * Expected behavior: clear pending notification and reset notification repeat delay
     * */
    @Test
    public void openNetworkNotifierClearsPendingNotificationOnWifiDisabled() {
        setWifiEnabled(false);

        verify(mOpenNetworkNotifier).clearPendingNotification(true /* resetRepeatDelay */);
    }

    /**
     * Verify that the ONA controller tracks screen state changes.
     */
    @Test
    public void openNetworkNotifierTracksScreenStateChanges() {
        // Screen state change at bootup.
        verify(mOpenNetworkNotifier).handleScreenStateChanged(false);

        setScreenState(false);

        verify(mOpenNetworkNotifier, times(2)).handleScreenStateChanged(false);

        setScreenState(true);

        verify(mOpenNetworkNotifier).handleScreenStateChanged(true);
    }

    /**
     * Verify that the initial fast scan schedules the scan timer just like regular scans.
     */
    @Test
    public void testInitialFastScanSchedulesMoreScans() {
        // Enable the fast initial scan feature
        mResources.setBoolean(R.bool.config_wifiEnablePartialInitialScan, true);
        // return 2 available frequencies
        when(mWifiScoreCard.lookupNetwork(anyString())).thenReturn(mPerNetwork);
        when(mPerNetwork.getFrequencies(anyLong())).thenReturn(new ArrayList<>(
                Arrays.asList(TEST_FREQUENCY_1, TEST_FREQUENCY_2)));

        long currentTimeStamp = CURRENT_SYSTEM_TIME_MS;
        when(mClock.getElapsedSinceBootMillis()).thenReturn(currentTimeStamp);
        mWifiConnectivityManager.setTrustedConnectionAllowed(true);

        // set screen off and wifi disconnected
        setScreenState(false);
        mWifiConnectivityManager.handleConnectionStateChanged(mPrimaryClientModeManager,
                WifiConnectivityManager.WIFI_STATE_DISCONNECTED);

        List<Long> intervals = triggerPeriodicScansAndGetIntervals(0 /* triggerTimes */,
                () -> {
                    // Set screen to ON to start a fast initial scan
                    setScreenState(true);
                }, currentTimeStamp);
        verifyScanTimesAndIntervals(1 /* scanTimes */, intervals,
                VALID_DISCONNECTED_SINGLE_SCAN_SCHEDULE_SEC,
                VALID_DISCONNECTED_SINGLE_SCAN_TYPE);

        // Verify the initial scan state is awaiting for response
        assertEquals(WifiConnectivityManager.INITIAL_SCAN_STATE_AWAITING_RESPONSE,
                mWifiConnectivityManager.getInitialScanState());
        verify(mWifiMetrics).incrementInitialPartialScanCount();
    }

    /**
     * Verify that if configuration for single scan schedule is empty, default
     * schedule is being used.
     */
    @Test
    public void checkPeriodicScanIntervalWhenDisconnectedWithEmptySchedule() throws Exception {
        mResources.setIntArray(R.array.config_wifiDisconnectedScanIntervalScheduleSec,
                SCHEDULE_EMPTY_SEC);
        mResources.setIntArray(R.array.config_wifiDisconnectedScanType, SCHEDULE_EMPTY_SEC);

        checkWorkingWithDefaultSchedule();
    }

    /**
     * Verify that if configuration for single scan schedule has zero values, default
     * schedule is being used.
     */
    @Test
    public void checkPeriodicScanIntervalWhenDisconnectedWithZeroValuesSchedule() {
        mResources.setIntArray(
                R.array.config_wifiDisconnectedScanIntervalScheduleSec,
                INVALID_SCHEDULE_ZERO_VALUES_SEC);
        mResources.setIntArray(R.array.config_wifiDisconnectedScanType,
                INVALID_SCHEDULE_NEGATIVE_VALUES_SEC);

        checkWorkingWithDefaultSchedule();
    }

    /**
     * Verify that if configuration for single scan schedule has negative values, default
     * schedule is being used.
     */
    @Test
    public void checkPeriodicScanIntervalWhenDisconnectedWithNegativeValuesSchedule() {
        mResources.setIntArray(
                R.array.config_wifiDisconnectedScanIntervalScheduleSec,
                INVALID_SCHEDULE_NEGATIVE_VALUES_SEC);
        mResources.setIntArray(R.array.config_wifiDisconnectedScanType,
                INVALID_SCHEDULE_NEGATIVE_VALUES_SEC);

        checkWorkingWithDefaultSchedule();
    }

    /**
     * Verify that when power save mode in on, the periodic scan interval is increased.
     */
    @Test
    public void checkPeriodicScanIntervalWhenDisconnectAndPowerSaveModeOn() throws Exception {
        mResources.setIntArray(
                R.array.config_wifiDisconnectedScanIntervalScheduleSec,
                INVALID_SCHEDULE_ZERO_VALUES_SEC);
        mResources.setIntArray(R.array.config_wifiDisconnectedScanType, SCHEDULE_EMPTY_SEC);

        when(mDeviceConfigFacade.isWifiBatterySaverEnabled()).thenReturn(true);
        when(mPowerManagerService.isPowerSaveMode()).thenReturn(true);
        checkWorkingWithDefaultScheduleWithMultiplier(POWER_SAVE_SCAN_INTERVAL_MULTIPLIER);
    }

    private void checkWorkingWithDefaultSchedule() {
        checkWorkingWithDefaultScheduleWithMultiplier(1 /* multiplier */);
    }

    /**
     * Get the value at index, or the last value if index is out of bound.
     * @param schedule Int array of schedule.
     * @param index Array index.
     */
    private int getByIndexOrLast(int[] schedule, int index) {
        return index < schedule.length ? schedule[index] : schedule[schedule.length - 1];
    }

    private void verifyScanTimesAndIntervals(int scanTimes, List<Long> intervals,
            int[] intervalSchedule, int[] scheduleScanType) {
        // Verify the scans actually happened for expected times, one scan for state change and
        // each for scan timer triggered.
        verify(mWifiScanner, times(scanTimes)).startScan(anyObject(), anyObject(), anyObject(),
                anyObject());

        // Verify scans are happening using the expected scan type.
        Map<Integer, Integer> scanTypeToTimesMap = new HashMap<>();
        for (int i = 0; i < scanTimes; i++) {
            int expected = getByIndexOrLast(scheduleScanType, i);
            scanTypeToTimesMap.put(expected, 1 + scanTypeToTimesMap.getOrDefault(expected, 0));
        }
        for (Map.Entry<Integer, Integer> entry : scanTypeToTimesMap.entrySet()) {
            verify(mWifiScanner, times(entry.getValue())).startScan(
                    argThat(new ArgumentMatcher<ScanSettings>() {
                        @Override
                        public boolean matches(ScanSettings scanSettings) {
                            return scanSettings.type == entry.getKey();
                        }
                    }), any(), any(), any());
        }

        // Verify the scan intervals are same as expected interval schedule.
        for (int i = 0; i < intervals.size(); i++) {
            long expected = (long) (getByIndexOrLast(intervalSchedule, i) * 1000);
            // TestHandler#sendMessageAtTime is not perfectly mocked and uses
            // SystemClock.uptimeMillis() to generate |intervals|. This sometimes results in error
            // margins of ~1ms and cause flaky test failures.
            assertTrue("Interval " + i + " not in 1ms error margin",
                    Math.abs(expected - intervals.get(i).longValue()) < 2);
        }
    }

    private void verifyScanTimesAndFirstInterval(int scanTimes, List<Long> intervals,
            int expectedInterval) {
        // Verify the scans actually happened for expected times, one scan for state change and
        // each for scan timer triggered.
        verify(mWifiScanner, times(scanTimes)).startScan(anyObject(), anyObject(), anyObject(),
                anyObject());

        // The actual interval should be same as scheduled.
        assertEquals(expectedInterval * 1000, intervals.get(0).longValue());
    }

    /**
     * Trigger the Wifi periodic scan and get scan intervals, after setting the Wifi state.
     * @param triggerTimes The times to trigger the scheduled periodic scan. If it's 0 then don't
     *              trigger the scheduled periodic scan just return the interval.
     * @param setStateCallback The callback function to be called to set the Wifi state.
     * @param startTime The simulated scan start time.
     */
    private List<Long> triggerPeriodicScansAndGetIntervals(int triggerTimes,
            Runnable setStateCallback, long startTime) {
        when(mClock.getElapsedSinceBootMillis()).thenReturn(startTime);
        // Call the Wifi state callback to set the specified Wifi State to test the scan intervals.
        setStateCallback.run();

        for (int i = 0; i < triggerTimes; i++) {
            // Mock the advanced time as when the scan timer supposed to fire
            when(mClock.getElapsedSinceBootMillis()).thenReturn(startTime
                    + mTestHandler.getIntervals().stream().mapToLong(Long::longValue).sum());
            // Now advance the test handler and fire the periodic scan timer
            mTestHandler.timeAdvance();
        }

        /* Verify the number of intervals recorded for periodic scans is (times + 1):
         * One initial interval by scan scheduled in setStateCallback.
         * One interval by each scan triggered.
         */
        assertEquals(triggerTimes + 1, mTestHandler.getIntervals().size());

        return mTestHandler.getIntervals();
    }

    private void checkWorkingWithDefaultScheduleWithMultiplier(float multiplier) {
        long currentTimeStamp = CURRENT_SYSTEM_TIME_MS;
        when(mClock.getElapsedSinceBootMillis()).thenReturn(currentTimeStamp);

        mWifiConnectivityManager = createConnectivityManager();
        mWifiConnectivityManager.setTrustedConnectionAllowed(true);
        setWifiEnabled(true);

        // Set screen to ON
        setScreenState(true);

        List<Long> intervals = triggerPeriodicScansAndGetIntervals(SCAN_TRIGGER_TIMES,
                () -> {
                    mWifiConnectivityManager.handleConnectionStateChanged(
                            mPrimaryClientModeManager,
                            WifiConnectivityManager.WIFI_STATE_DISCONNECTED);
                }, currentTimeStamp);

        verifyScanTimesAndIntervals(SCAN_TRIGGER_TIMES + 1, intervals,
                Arrays.stream(DEFAULT_SINGLE_SCAN_SCHEDULE_SEC).map(i -> (int) (i * multiplier))
                .toArray(), DEFAULT_SINGLE_SCAN_TYPE);
    }

    /**
     *  Verify that scan interval for screen on and wifi disconnected scenario
     *  is in the exponential backoff fashion.
     *
     * Expected behavior: WifiConnectivityManager doubles periodic
     * scan interval.
     */
    @Test
    public void checkPeriodicScanIntervalWhenDisconnected() {
        long currentTimeStamp = CURRENT_SYSTEM_TIME_MS;
        when(mClock.getElapsedSinceBootMillis()).thenReturn(currentTimeStamp);

        // Set screen to ON
        setScreenState(true);

        // Wait for max periodic scan interval so that any impact triggered
        // by screen state change can settle
        currentTimeStamp += MAX_SCAN_INTERVAL_IN_SCHEDULE_SEC * 1000;
        when(mClock.getElapsedSinceBootMillis()).thenReturn(currentTimeStamp);

        List<Long> intervals = triggerPeriodicScansAndGetIntervals(SCAN_TRIGGER_TIMES,
                () -> {
                    mWifiConnectivityManager.handleConnectionStateChanged(
                            mPrimaryClientModeManager,
                            WifiConnectivityManager.WIFI_STATE_DISCONNECTED);
                }, currentTimeStamp);

        verifyScanTimesAndIntervals(SCAN_TRIGGER_TIMES + 1, intervals,
                VALID_DISCONNECTED_SINGLE_SCAN_SCHEDULE_SEC, VALID_DISCONNECTED_SINGLE_SCAN_TYPE);
    }

    @Test
    public void checkSetExternalPeriodicScanInterval() {
        assumeTrue(SdkLevel.isAtLeastT());
        long currentTimeStamp = CURRENT_SYSTEM_TIME_MS;
        when(mClock.getElapsedSinceBootMillis()).thenReturn(currentTimeStamp);

        mWifiConnectivityManager.setExternalScreenOnScanSchedule(
                VALID_EXTERNAL_SINGLE_SCAN_SCHEDULE_SEC, VALID_EXTERNAL_SINGLE_SCAN_TYPE);
        // Set screen to ON
        setScreenState(true);

        // Wait for max periodic scan interval so that any impact triggered
        // by screen state change can settle
        currentTimeStamp += MAX_SCAN_INTERVAL_IN_SCHEDULE_SEC * 1000;
        when(mClock.getElapsedSinceBootMillis()).thenReturn(currentTimeStamp);

        List<Long> intervals = triggerPeriodicScansAndGetIntervals(SCAN_TRIGGER_TIMES,
                () -> {
                    mWifiConnectivityManager.handleConnectionStateChanged(
                            mPrimaryClientModeManager,
                            WifiConnectivityManager.WIFI_STATE_DISCONNECTED);
                }, currentTimeStamp);

        verifyScanTimesAndIntervals(SCAN_TRIGGER_TIMES + 1, intervals,
                VALID_EXTERNAL_SINGLE_SCAN_SCHEDULE_SEC, VALID_EXTERNAL_SINGLE_SCAN_TYPE);
    }

    @Test
    public void testSetOneShotScreenOnConnectivityScanDelayMillis() {
        assumeTrue(SdkLevel.isAtLeastT());
        int scanDelayMs = 12345;
        mWifiConnectivityManager.setOneShotScreenOnConnectivityScanDelayMillis(scanDelayMs);

        // Toggle screen to ON
        assertEquals(0, mTestHandler.getIntervals().size());
        setScreenState(false);
        setScreenState(true);
        assertEquals(1, mTestHandler.getIntervals().size());
        assertTrue("Delay is not in 1ms error margin",
                Math.abs(scanDelayMs - mTestHandler.getIntervals().get(0).longValue()) < 2);

        // Toggle again and there should be no more delayed scan
        setScreenState(false);
        setScreenState(true);
        assertEquals(1, mTestHandler.getIntervals().size());

        // set the scan delay and verify again
        scanDelayMs = 23455;
        mWifiConnectivityManager.setOneShotScreenOnConnectivityScanDelayMillis(scanDelayMs);
        setScreenState(false);
        setScreenState(true);
        assertEquals(2, mTestHandler.getIntervals().size());
        assertTrue("Delay is not in 1ms error margin",
                Math.abs(scanDelayMs - mTestHandler.getIntervals().get(1).longValue()) < 2);
    }

    /**
     *  Verify that scan interval for screen on and wifi connected scenario
     *  is in the exponential backoff fashion.
     *
     * Expected behavior: WifiConnectivityManager doubles periodic
     * scan interval.
     */
    @Test
    public void checkPeriodicScanIntervalWhenConnected() {
        long currentTimeStamp = CURRENT_SYSTEM_TIME_MS;
        when(mClock.getElapsedSinceBootMillis()).thenReturn(currentTimeStamp);

        if (SdkLevel.isAtLeastT()) {
            // verify that setting the external scan schedule and then setting to null again should
            // result in no-op, and not affect the scan schedule at all.
            mWifiConnectivityManager.setExternalScreenOnScanSchedule(
                    VALID_EXTERNAL_SINGLE_SCAN_SCHEDULE_SEC, VALID_EXTERNAL_SINGLE_SCAN_TYPE);
            mWifiConnectivityManager.setExternalScreenOnScanSchedule(
                    null, null);
        }

        // Set screen to ON
        setScreenState(true);

        // Wait for max scanning interval so that any impact triggered
        // by screen state change can settle
        currentTimeStamp += MAX_SCAN_INTERVAL_IN_SCHEDULE_SEC * 1000;
        List<Long> intervals = triggerPeriodicScansAndGetIntervals(SCAN_TRIGGER_TIMES,
                () -> {
                    // Set WiFi to connected state to trigger periodic scan
                    setWifiStateConnected();
                }, currentTimeStamp);
        verifyScanTimesAndIntervals(SCAN_TRIGGER_TIMES + 1, intervals,
                VALID_CONNECTED_SINGLE_SCAN_SCHEDULE_SEC, VALID_CONNECTED_SINGLE_SCAN_TYPE);
    }

    /**
     *  Verify that scan interval for screen on and wifi is connected to the only network known to
     *  the device.
     */
    @Test
    public void checkPeriodicScanIntervalWhenConnectedAndOnlySingleNetwork() {
        long currentTimeStamp = CURRENT_SYSTEM_TIME_MS;
        when(mClock.getElapsedSinceBootMillis()).thenReturn(currentTimeStamp);
        mResources.setIntArray(
                R.array.config_wifiSingleSavedNetworkConnectedScanIntervalScheduleSec,
                VALID_CONNECTED_SINGLE_SAVED_NETWORK_SCHEDULE_SEC);
        mResources.setIntArray(
                R.array.config_wifiSingleSavedNetworkConnectedScanType,
                VALID_CONNECTED_SINGLE_SAVED_NETWORK_TYPE);
        WifiConfiguration wifiConfiguration = new WifiConfiguration();
        wifiConfiguration.networkId = TEST_CONNECTED_NETWORK_ID;
        List<WifiConfiguration> wifiConfigurationList = new ArrayList<WifiConfiguration>();
        wifiConfigurationList.add(wifiConfiguration);
        when(mWifiConfigManager.getSavedNetworks(anyInt())).thenReturn(wifiConfigurationList);

        // Set screen to ON
        setScreenState(true);
        // Wait for max scanning interval so that any impact triggered
        // by screen state change can settle
        currentTimeStamp += MAX_SCAN_INTERVAL_IN_SCHEDULE_SEC * 1000;
        List<Long> intervals = triggerPeriodicScansAndGetIntervals(SCAN_TRIGGER_TIMES,
                () -> {
                    // Set WiFi to connected state to trigger periodic scan
                    setWifiStateConnected();
                }, currentTimeStamp);
        verifyScanTimesAndIntervals(SCAN_TRIGGER_TIMES + 1, intervals,
                VALID_CONNECTED_SINGLE_SAVED_NETWORK_SCHEDULE_SEC,
                VALID_CONNECTED_SINGLE_SAVED_NETWORK_TYPE);
    }

    /**
     * When screen on and single saved network schedule is set
     * If we have multiple saved networks, the regular connected state scan schedule is used
     */
    @Test
    public void checkScanScheduleForMultipleSavedNetwork() {
        long currentTimeStamp = CURRENT_SYSTEM_TIME_MS;
        when(mClock.getElapsedSinceBootMillis()).thenReturn(currentTimeStamp);

        // Set screen to ON
        setScreenState(true);

        // Wait for max scanning interval so that any impact triggered
        // by screen state change can settle
        currentTimeStamp += MAX_SCAN_INTERVAL_IN_SCHEDULE_SEC * 1000;
        when(mClock.getElapsedSinceBootMillis()).thenReturn(currentTimeStamp);

        mResources.setIntArray(
                R.array.config_wifiSingleSavedNetworkConnectedScanIntervalScheduleSec,
                VALID_CONNECTED_SINGLE_SAVED_NETWORK_SCHEDULE_SEC);

        WifiConfiguration wifiConfiguration1 = new WifiConfiguration();
        WifiConfiguration wifiConfiguration2 = new WifiConfiguration();
        wifiConfiguration1.status = WifiConfiguration.Status.CURRENT;
        List<WifiConfiguration> wifiConfigurationList = new ArrayList<WifiConfiguration>();
        wifiConfigurationList.add(wifiConfiguration1);
        wifiConfigurationList.add(wifiConfiguration2);
        when(mWifiConfigManager.getSavedNetworks(anyInt())).thenReturn(wifiConfigurationList);

        // Set firmware roaming to enabled
        when(mWifiConnectivityHelper.isFirmwareRoamingSupported()).thenReturn(true);

        List<Long> intervals = triggerPeriodicScansAndGetIntervals(0 /* triggerTimes */,
                () -> {
                    // Set WiFi to connected state to trigger periodic scan
                    mWifiConnectivityManager.handleConnectionStateChanged(
                            mPrimaryClientModeManager,
                            WifiConnectivityManager.WIFI_STATE_CONNECTED);
                }, currentTimeStamp);
        verifyScanTimesAndFirstInterval(1 /* scanTimes */, intervals,
                VALID_CONNECTED_SINGLE_SCAN_SCHEDULE_SEC[0]);
    }

    /**
     * When screen on and single saved network schedule is set
     * If we have a single saved network (connected network),
     * no passpoint or suggestion networks.
     * the single-saved-network connected state scan schedule is used
     */
    @Test
    public void checkScanScheduleForSingleSavedNetworkConnected() {
        long currentTimeStamp = CURRENT_SYSTEM_TIME_MS;
        when(mClock.getElapsedSinceBootMillis()).thenReturn(currentTimeStamp);

        // Set screen to ON
        setScreenState(true);

        // Wait for max scanning interval so that any impact triggered
        // by screen state change can settle
        currentTimeStamp += MAX_SCAN_INTERVAL_IN_SCHEDULE_SEC * 1000;
        when(mClock.getElapsedSinceBootMillis()).thenReturn(currentTimeStamp);

        mResources.setIntArray(
                R.array.config_wifiSingleSavedNetworkConnectedScanIntervalScheduleSec,
                VALID_CONNECTED_SINGLE_SAVED_NETWORK_SCHEDULE_SEC);

        WifiConfiguration wifiConfiguration = new WifiConfiguration();
        wifiConfiguration.networkId = TEST_CONNECTED_NETWORK_ID;
        List<WifiConfiguration> wifiConfigurationList = new ArrayList<WifiConfiguration>();
        wifiConfigurationList.add(wifiConfiguration);
        when(mWifiConfigManager.getSavedNetworks(anyInt())).thenReturn(wifiConfigurationList);

        // Set firmware roaming to enabled
        when(mWifiConnectivityHelper.isFirmwareRoamingSupported()).thenReturn(true);

        List<Long> intervals = triggerPeriodicScansAndGetIntervals(1 /* triggerTimes */,
                () -> {
                    // Set WiFi to connected state to trigger periodic scan
                    setWifiStateConnected();
                }, currentTimeStamp);
        verifyScanTimesAndFirstInterval(2 /* scanTimes */, intervals,
                VALID_CONNECTED_SINGLE_SAVED_NETWORK_SCHEDULE_SEC[0]);
    }

    /**
     * When screen on and single saved network schedule is set
     * If we have a single saved network (not connected network),
     * no passpoint or suggestion networks.
     * the regular connected state scan schedule is used
     */
    @Test
    public void checkScanScheduleForSingleSavedNetwork() {
        int testSavedNetworkId = TEST_CONNECTED_NETWORK_ID + 1;
        long currentTimeStamp = CURRENT_SYSTEM_TIME_MS;
        when(mClock.getElapsedSinceBootMillis()).thenReturn(currentTimeStamp);

        // Set screen to ON
        setScreenState(true);

        // Wait for max scanning interval so that any impact triggered
        // by screen state change can settle
        currentTimeStamp += MAX_SCAN_INTERVAL_IN_SCHEDULE_SEC * 1000;
        when(mClock.getElapsedSinceBootMillis()).thenReturn(currentTimeStamp);

        mResources.setIntArray(
                R.array.config_wifiSingleSavedNetworkConnectedScanIntervalScheduleSec,
                VALID_CONNECTED_SINGLE_SAVED_NETWORK_SCHEDULE_SEC);

        // Set firmware roaming to enabled
        when(mWifiConnectivityHelper.isFirmwareRoamingSupported()).thenReturn(true);

        WifiConfiguration wifiConfiguration = new WifiConfiguration();
        wifiConfiguration.status = WifiConfiguration.Status.ENABLED;
        wifiConfiguration.networkId = testSavedNetworkId;
        List<WifiConfiguration> wifiConfigurationList = new ArrayList<WifiConfiguration>();
        wifiConfigurationList.add(wifiConfiguration);
        when(mWifiConfigManager.getSavedNetworks(anyInt())).thenReturn(wifiConfigurationList);

        List<Long> intervals = triggerPeriodicScansAndGetIntervals(1 /* triggerTimes */,
                () -> {
                    // Set WiFi to connected state to trigger periodic scan
                    setWifiStateConnected();
                }, currentTimeStamp);
        verifyScanTimesAndFirstInterval(2 /* scanTimes */, intervals,
                VALID_CONNECTED_SINGLE_SCAN_SCHEDULE_SEC[0]);
    }

    /**
     * When screen on and single saved network schedule is set
     * If we have a single passpoint network (connected network),
     * and no saved or suggestion networks the single-saved-network
     * connected state scan schedule is used.
     */
    @Test
    public void checkScanScheduleForSinglePasspointNetworkConnected() {
        long currentTimeStamp = CURRENT_SYSTEM_TIME_MS;
        when(mClock.getElapsedSinceBootMillis()).thenReturn(currentTimeStamp);

        // Set screen to ON
        setScreenState(true);

        // Wait for max scanning interval so that any impact triggered
        // by screen state change can settle
        currentTimeStamp += MAX_SCAN_INTERVAL_IN_SCHEDULE_SEC * 1000;
        when(mClock.getElapsedSinceBootMillis()).thenReturn(currentTimeStamp);

        mResources.setIntArray(
                R.array.config_wifiSingleSavedNetworkConnectedScanIntervalScheduleSec,
                VALID_CONNECTED_SINGLE_SAVED_NETWORK_SCHEDULE_SEC);

        // Prepare for a single passpoint network
        WifiConfiguration config = new WifiConfiguration();
        config.networkId = TEST_CONNECTED_NETWORK_ID;
        String passpointKey = "PASSPOINT_KEY";
        when(mWifiConfigManager.getConfiguredNetwork(passpointKey)).thenReturn(config);
        List<PasspointConfiguration> passpointNetworks = new ArrayList<PasspointConfiguration>();
        passpointNetworks.add(mPasspointConfiguration);
        when(mPasspointConfiguration.getUniqueId()).thenReturn(passpointKey);
        when(mPasspointManager.getProviderConfigs(anyInt(), anyBoolean()))
                .thenReturn(passpointNetworks);

        // Prepare for no saved networks
        when(mWifiConfigManager.getSavedNetworks(anyInt())).thenReturn(new ArrayList<>());

        // Set firmware roaming to enabled
        when(mWifiConnectivityHelper.isFirmwareRoamingSupported()).thenReturn(true);

        List<Long> intervals = triggerPeriodicScansAndGetIntervals(1 /* triggerTimes */,
                () -> {
                    // Set WiFi to connected state to trigger periodic scan
                    setWifiStateConnected();
                }, currentTimeStamp);
        verifyScanTimesAndFirstInterval(2 /* scanTimes */, intervals,
                VALID_CONNECTED_SINGLE_SAVED_NETWORK_SCHEDULE_SEC[0]);
    }

    /**
     * When screen on and single saved network schedule is set
     * If we have a single suggestion network (connected network),
     * and no saved network or passpoint networks the single-saved-network
     * connected state scan schedule is used
     */
    @Test
    public void checkScanScheduleForSingleSuggestionsNetworkConnected() {
        long currentTimeStamp = CURRENT_SYSTEM_TIME_MS;
        when(mClock.getElapsedSinceBootMillis()).thenReturn(currentTimeStamp);

        // Set screen to ON
        setScreenState(true);

        // Wait for max scanning interval so that any impact triggered
        // by screen state change can settle
        currentTimeStamp += MAX_SCAN_INTERVAL_IN_SCHEDULE_SEC * 1000;
        when(mClock.getElapsedSinceBootMillis()).thenReturn(currentTimeStamp);

        mResources.setIntArray(
                R.array.config_wifiSingleSavedNetworkConnectedScanIntervalScheduleSec,
                VALID_CONNECTED_SINGLE_SAVED_NETWORK_SCHEDULE_SEC);

        // Prepare for a single suggestions network
        WifiConfiguration config = new WifiConfiguration();
        config.networkId = TEST_CONNECTED_NETWORK_ID;
        String networkKey = "NETWORK_KEY";
        when(mWifiConfigManager.getConfiguredNetwork(networkKey)).thenReturn(config);
        when(mSuggestionConfig.getProfileKey()).thenReturn(networkKey);
        when(mWifiNetworkSuggestion.getWifiConfiguration()).thenReturn(mSuggestionConfig);
        Set<WifiNetworkSuggestion> suggestionNetworks = new HashSet<WifiNetworkSuggestion>();
        suggestionNetworks.add(mWifiNetworkSuggestion);
        when(mWifiNetworkSuggestionsManager.getAllApprovedNetworkSuggestions())
                .thenReturn(suggestionNetworks);

        // Prepare for no saved networks
        when(mWifiConfigManager.getSavedNetworks(anyInt())).thenReturn(new ArrayList<>());

        // Set firmware roaming to enabled
        when(mWifiConnectivityHelper.isFirmwareRoamingSupported()).thenReturn(true);

        List<Long> intervals = triggerPeriodicScansAndGetIntervals(1 /* triggerTimes */,
                () -> {
                    // Set WiFi to connected state to trigger periodic scan
                    setWifiStateConnected();
                }, currentTimeStamp);
        verifyScanTimesAndFirstInterval(2 /* scanTimes */, intervals,
                VALID_CONNECTED_SINGLE_SAVED_NETWORK_SCHEDULE_SEC[0]);
    }

    /**
     * When screen on and single saved network schedule is set
     * If we have a single suggestion network (connected network),
     * and saved network/passpoint networks the regular
     * connected state scan schedule is used
     */
    @Test
    public void checkScanScheduleForSavedPasspointSuggestionNetworkConnected() {
        long currentTimeStamp = CURRENT_SYSTEM_TIME_MS;
        when(mClock.getElapsedSinceBootMillis()).thenReturn(currentTimeStamp);

        // Set screen to ON
        setScreenState(true);

        // Wait for max scanning interval so that any impact triggered
        // by screen state change can settle
        currentTimeStamp += MAX_SCAN_INTERVAL_IN_SCHEDULE_SEC * 1000;
        when(mClock.getElapsedSinceBootMillis()).thenReturn(currentTimeStamp);

        mResources.setIntArray(
                R.array.config_wifiSingleSavedNetworkConnectedScanIntervalScheduleSec,
                VALID_CONNECTED_SINGLE_SAVED_NETWORK_SCHEDULE_SEC);

        // Prepare for a single suggestions network
        WifiConfiguration config = new WifiConfiguration();
        config.networkId = TEST_CONNECTED_NETWORK_ID;
        String networkKey = "NETWORK_KEY";
        when(mWifiConfigManager.getConfiguredNetwork(networkKey)).thenReturn(config);
        when(mSuggestionConfig.getProfileKey()).thenReturn(networkKey);
        when(mWifiNetworkSuggestion.getWifiConfiguration()).thenReturn(mSuggestionConfig);
        Set<WifiNetworkSuggestion> suggestionNetworks = new HashSet<WifiNetworkSuggestion>();
        suggestionNetworks.add(mWifiNetworkSuggestion);
        when(mWifiNetworkSuggestionsManager.getAllApprovedNetworkSuggestions())
                .thenReturn(suggestionNetworks);

        // Prepare for a single passpoint network
        WifiConfiguration passpointConfig = new WifiConfiguration();
        String passpointKey = "PASSPOINT_KEY";
        when(mWifiConfigManager.getConfiguredNetwork(passpointKey)).thenReturn(passpointConfig);
        List<PasspointConfiguration> passpointNetworks = new ArrayList<PasspointConfiguration>();
        passpointNetworks.add(mPasspointConfiguration);
        when(mPasspointConfiguration.getUniqueId()).thenReturn(passpointKey);
        when(mPasspointManager.getProviderConfigs(anyInt(), anyBoolean()))
                .thenReturn(passpointNetworks);

        // Set firmware roaming to enabled
        when(mWifiConnectivityHelper.isFirmwareRoamingSupported()).thenReturn(true);

        List<Long> intervals = triggerPeriodicScansAndGetIntervals(1 /* triggerTimes */,
                () -> {
                    // Set WiFi to connected state to trigger periodic scan
                    setWifiStateConnected();
                }, currentTimeStamp);
        verifyScanTimesAndFirstInterval(2 /* scanTimes */, intervals,
                VALID_CONNECTED_SINGLE_SCAN_SCHEDULE_SEC[0]);
    }

    /**
     * Remove network will trigger update scan and meet single network requirement.
     * Verify before disconnect finished, will not trigger single network scan schedule.
     */
    @Test
    public void checkScanScheduleForCurrentConnectedNetworkIsNull() {
        long currentTimeStamp = CURRENT_SYSTEM_TIME_MS;
        when(mClock.getElapsedSinceBootMillis()).thenReturn(currentTimeStamp);

        // Set screen to ON
        setScreenState(true);

        // Wait for max scanning interval so that any impact triggered
        // by screen state change can settle
        currentTimeStamp += MAX_SCAN_INTERVAL_IN_SCHEDULE_SEC * 1000;
        when(mClock.getElapsedSinceBootMillis()).thenReturn(currentTimeStamp);

        mResources.setIntArray(
                R.array.config_wifiSingleSavedNetworkConnectedScanIntervalScheduleSec,
                VALID_CONNECTED_SINGLE_SAVED_NETWORK_SCHEDULE_SEC);

        // Set firmware roaming to enabled
        when(mWifiConnectivityHelper.isFirmwareRoamingSupported()).thenReturn(true);

        // Set up single saved network
        WifiConfiguration wifiConfiguration = new WifiConfiguration();
        wifiConfiguration.networkId = TEST_CONNECTED_NETWORK_ID;
        List<WifiConfiguration> wifiConfigurationList = new ArrayList<WifiConfiguration>();
        wifiConfigurationList.add(wifiConfiguration);
        when(mWifiConfigManager.getSavedNetworks(anyInt())).thenReturn(wifiConfigurationList);

        // Set WiFi to connected state to trigger periodic scan
        setWifiStateConnected();
        mTestHandler.reset();

        List<Long> intervals = triggerPeriodicScansAndGetIntervals(1 /* triggerTimes */,
                () -> {
                    // Simulate remove network, disconnect not finished.
                    when(mPrimaryClientModeManager.getConnectedWifiConfiguration())
                            .thenReturn(null);
                    mNetworkUpdateListenerCaptor.getValue().onNetworkRemoved(null);
                }, currentTimeStamp);
        verifyScanTimesAndFirstInterval(2 /* scanTimes */, intervals,
                VALID_CONNECTED_SINGLE_SCAN_SCHEDULE_SEC[0]);
    }

    /**
     *  When screen on trigger a disconnected state change event then a connected state
     *  change event back to back to verify that the minium scan interval is enforced.
     *
     * Expected behavior: WifiConnectivityManager start the second periodic single
     * scan after the first one by first interval in connected scanning schedule.
     */
    @Test
    public void checkMinimumPeriodicScanIntervalWhenScreenOnAndConnected() {
        long currentTimeStamp = CURRENT_SYSTEM_TIME_MS;
        when(mClock.getElapsedSinceBootMillis()).thenReturn(currentTimeStamp);

        // Set screen to ON
        setScreenState(true);

        // Wait for max scanning interval in schedule so that any impact triggered
        // by screen state change can settle
        currentTimeStamp += MAX_SCAN_INTERVAL_IN_SCHEDULE_SEC * 1000;
        long scanForDisconnectedTimeStamp = currentTimeStamp;
        when(mClock.getElapsedSinceBootMillis()).thenReturn(currentTimeStamp);

        // Set WiFi to disconnected state which triggers a scan immediately
        mWifiConnectivityManager.handleConnectionStateChanged(
                mPrimaryClientModeManager,
                WifiConnectivityManager.WIFI_STATE_DISCONNECTED);
        verify(mWifiScanner, times(1)).startScan(
                anyObject(), anyObject(), anyObject(), anyObject());

        // Set up time stamp for when entering CONNECTED state
        currentTimeStamp += 2000;
        when(mClock.getElapsedSinceBootMillis()).thenReturn(currentTimeStamp);
        mTestHandler.reset();

        List<Long> intervals = triggerPeriodicScansAndGetIntervals(1 /* triggerTimes */,
                () -> {
                    // Set WiFi to connected state to trigger periodic scan
                    setWifiStateConnected();
                }, currentTimeStamp);
        intervals.set(0, intervals.get(0) + 2000);
        verifyScanTimesAndFirstInterval(2 /* scanTimes */, intervals,
                VALID_CONNECTED_SINGLE_SCAN_SCHEDULE_SEC[0]);
    }

    /**
     *  When screen on trigger a connected state change event then a disconnected state
     *  change event back to back to verify that a scan is fired immediately for the
     *  disconnected state change event.
     *
     * Expected behavior: WifiConnectivityManager directly starts the periodic immediately
     * for the disconnected state change event. The second scan for disconnected state is
     * via alarm timer.
     */
    @Test
    public void scanImmediatelyWhenScreenOnAndDisconnected() {
        long currentTimeStamp = CURRENT_SYSTEM_TIME_MS;
        when(mClock.getElapsedSinceBootMillis()).thenReturn(currentTimeStamp);

        // Set screen to ON
        setScreenState(true);

        // Wait for maximum scanning interval in schedule so that any impact triggered
        // by screen state change can settle
        currentTimeStamp += MAX_SCAN_INTERVAL_IN_SCHEDULE_SEC * 1000;
        long scanForConnectedTimeStamp = currentTimeStamp;
        when(mClock.getElapsedSinceBootMillis()).thenReturn(currentTimeStamp);

        // Set WiFi to connected state to trigger the periodic scan
        setWifiStateConnected();

        verify(mWifiScanner, times(1)).startScan(
                anyObject(), anyObject(), anyObject(), anyObject());

        // Set up the time stamp for when entering DISCONNECTED state
        currentTimeStamp += 2000;
        long enteringDisconnectedStateTimeStamp = currentTimeStamp;
        when(mClock.getElapsedSinceBootMillis()).thenReturn(currentTimeStamp);
        mTestHandler.reset();

        List<Long> intervals = triggerPeriodicScansAndGetIntervals(0 /* triggerTimes */,
                () -> {
                    // Set WiFi to disconnected state to trigger its periodic scan
                    mWifiConnectivityManager.handleConnectionStateChanged(
                            mPrimaryClientModeManager,
                            WifiConnectivityManager.WIFI_STATE_DISCONNECTED);
                }, currentTimeStamp);
        verifyScanTimesAndFirstInterval(2 /* scanTimes */, intervals,
                VALID_DISCONNECTED_SINGLE_SCAN_SCHEDULE_SEC[0]);
    }

    /**
     *  When screen on trigger a connection state change event and a forced connectivity
     *  scan event back to back to verify that the minimum scan interval is not applied
     *  in this scenario.
     *
     * Expected behavior: WifiConnectivityManager starts the second periodic single
     * scan immediately.
     */
    @Test
    public void checkMinimumPeriodicScanIntervalNotEnforced() {
        long currentTimeStamp = CURRENT_SYSTEM_TIME_MS;
        when(mClock.getElapsedSinceBootMillis()).thenReturn(currentTimeStamp);

        // Set screen to ON
        setScreenState(true);

        // Wait for maximum interval in scanning schedule so that any impact triggered
        // by screen state change can settle
        currentTimeStamp += MAX_SCAN_INTERVAL_IN_SCHEDULE_SEC * 1000;
        long firstScanTimeStamp = currentTimeStamp;
        when(mClock.getElapsedSinceBootMillis()).thenReturn(currentTimeStamp);

        // Set WiFi to connected state to trigger the periodic scan
        setWifiStateConnected();

        // Set the second scan attempt time stamp
        currentTimeStamp += 2000;
        when(mClock.getElapsedSinceBootMillis()).thenReturn(currentTimeStamp);

        // Allow untrusted networks so WifiConnectivityManager starts a periodic scan
        // immediately.
        mWifiConnectivityManager.setUntrustedConnectionAllowed(true);

        // Get the second periodic scan actual time stamp. Note, this scan is not
        // started from the AlarmManager.
        long secondScanTimeStamp = mWifiConnectivityManager.getLastPeriodicSingleScanTimeStamp();

        // Verify that the second scan is fired immediately
        assertEquals(secondScanTimeStamp, currentTimeStamp);
    }

    /**
     * Verify that we perform full band scan in the following two cases
     * 1) Current RSSI is low, no active stream, network is insufficient
     * 2) Current RSSI is high, no active stream, and a long time since last network selection
     * 3) Current RSSI is high, no active stream, and a short time since last network selection,
     *  internet status is not acceptable
     *
     * Expected behavior: WifiConnectivityManager does full band scan in both cases
     */
    @Test
    public void verifyFullBandScanWhenConnected() {
        mResources.setInteger(
                R.integer.config_wifiConnectedHighRssiScanMinimumWindowSizeSec, 600);

        // Verify case 1
        when(mWifiNS.isNetworkSufficient(eq(mWifiInfo))).thenReturn(false);
        when(mWifiNS.hasActiveStream(eq(mWifiInfo))).thenReturn(false);
        when(mWifiNS.hasSufficientLinkQuality(eq(mWifiInfo))).thenReturn(false);
        when(mWifiNS.hasInternetOrExpectNoInternet(eq(mWifiInfo))).thenReturn(true);

        final List<Integer> channelList = new ArrayList<>();
        channelList.add(TEST_FREQUENCY_1);
        channelList.add(TEST_FREQUENCY_2);
        channelList.add(TEST_FREQUENCY_3);
        WifiConfiguration configuration = WifiConfigurationTestUtil.createOpenNetwork();
        configuration.networkId = TEST_CONNECTED_NETWORK_ID;
        when(mWifiConfigManager.getConfiguredNetwork(TEST_CONNECTED_NETWORK_ID))
                .thenReturn(configuration);
        when(mPrimaryClientModeManager.getConnectedWifiConfiguration())
                .thenReturn(configuration);
        when(mWifiScoreCard.lookupNetwork(configuration.SSID)).thenReturn(mPerNetwork);
        when(mPerNetwork.getFrequencies(anyLong())).thenReturn(new ArrayList<>());

        doAnswer(new AnswerWithArguments() {
            public void answer(ScanSettings settings, ScanListener listener,
                    WorkSource workSource) throws Exception {
                assertEquals(settings.band, WifiScanner.WIFI_BAND_ALL);
                assertNull(settings.channels);
            }}).when(mWifiScanner).startScan(anyObject(), anyObject(), anyObject());

        when(mClock.getElapsedSinceBootMillis()).thenReturn(0L);
        // Set screen to ON
        setScreenState(true);

        // Set WiFi to connected state to trigger periodic scan
        mWifiConnectivityManager.handleConnectionStateChanged(
                mPrimaryClientModeManager,
                WifiConnectivityManager.WIFI_STATE_CONNECTED);

        verify(mWifiScanner).startScan(anyObject(), anyObject(), anyObject(), anyObject());

        // Verify case 2
        when(mWifiNS.isNetworkSufficient(eq(mWifiInfo))).thenReturn(true);
        when(mWifiNS.hasActiveStream(eq(mWifiInfo))).thenReturn(false);
        when(mWifiNS.hasSufficientLinkQuality(eq(mWifiInfo))).thenReturn(true);
        when(mWifiNS.hasInternetOrExpectNoInternet(eq(mWifiInfo))).thenReturn(true);
        when(mClock.getElapsedSinceBootMillis()).thenReturn(600_000L + 1L);
        setScreenState(true);
        // Set WiFi to connected state to trigger periodic scan
        mWifiConnectivityManager.handleConnectionStateChanged(
                mPrimaryClientModeManager,
                WifiConnectivityManager.WIFI_STATE_CONNECTED);
        verify(mWifiScanner, times(2)).startScan(anyObject(), anyObject(), anyObject(),
                anyObject());

        // Verify case 3
        when(mWifiNS.isNetworkSufficient(eq(mWifiInfo))).thenReturn(false);
        when(mWifiNS.hasActiveStream(eq(mWifiInfo))).thenReturn(false);
        when(mWifiNS.hasSufficientLinkQuality(eq(mWifiInfo))).thenReturn(true);
        when(mWifiNS.hasInternetOrExpectNoInternet(eq(mWifiInfo))).thenReturn(false);
        when(mClock.getElapsedSinceBootMillis()).thenReturn(0L);
        setScreenState(true);
        // Set WiFi to connected state to trigger periodic scan
        mWifiConnectivityManager.handleConnectionStateChanged(
                mPrimaryClientModeManager,
                WifiConnectivityManager.WIFI_STATE_CONNECTED);
        verify(mWifiScanner, times(2)).startScan(anyObject(), anyObject(), anyObject(),
                anyObject());
    }

    /**
     * Verify that we perform partial scan when the current RSSI is low,
     * Tx/Rx success rates are high, and when the currently connected network is present
     * in scan cache in WifiConfigManager.
     * WifiConnectivityManager does partial scan only when firmware roaming is not supported.
     *
     * Expected behavior: WifiConnectivityManager does partial scan.
     */
    @Test
    public void checkPartialScanRequestedWithLowRssiAndActiveStreamWithoutFwRoaming() {
        when(mWifiNS.isNetworkSufficient(eq(mWifiInfo))).thenReturn(false);
        when(mWifiNS.hasActiveStream(eq(mWifiInfo))).thenReturn(true);
        when(mWifiNS.hasSufficientLinkQuality(eq(mWifiInfo))).thenReturn(false);
        when(mWifiNS.hasInternetOrExpectNoInternet(eq(mWifiInfo))).thenReturn(true);

        mResources.setInteger(
                R.integer.config_wifi_framework_associated_partial_scan_max_num_active_channels,
                10);

        WifiConfiguration configuration = WifiConfigurationTestUtil.createOpenNetwork();
        configuration.networkId = TEST_CONNECTED_NETWORK_ID;
        when(mWifiConfigManager.getConfiguredNetwork(TEST_CONNECTED_NETWORK_ID))
                .thenReturn(configuration);
        List<Integer> channelList = linkScoreCardFreqsToNetwork(configuration).get(0);
        when(mPrimaryClientModeManager.getConnectedWifiConfiguration())
                .thenReturn(configuration);

        when(mWifiConnectivityHelper.isFirmwareRoamingSupported()).thenReturn(false);

        doAnswer(new AnswerWithArguments() {
            public void answer(ScanSettings settings, Executor executor, ScanListener listener,
                    WorkSource workSource) throws Exception {
                assertEquals(settings.band, WifiScanner.WIFI_BAND_UNSPECIFIED);
                assertEquals(settings.channels.length, channelList.size());
                if (SdkLevel.isAtLeastS()) {
                    assertEquals("Should never force enable RNR for partial scans",
                            WifiScanner.WIFI_RNR_NOT_NEEDED, settings.getRnrSetting());
                    assertFalse("PSC should be disabled for partial scans",
                            settings.is6GhzPscOnlyEnabled());
                }
                for (int chanIdx = 0; chanIdx < settings.channels.length; chanIdx++) {
                    assertTrue(channelList.contains(settings.channels[chanIdx].frequency));
                }
            }}).when(mWifiScanner).startScan(anyObject(), anyObject(), anyObject(), anyObject());

        // Set screen to ON
        setScreenState(true);

        // Set WiFi to connected state to trigger periodic scan
        mWifiConnectivityManager.handleConnectionStateChanged(
                mPrimaryClientModeManager,
                WifiConnectivityManager.WIFI_STATE_CONNECTED);

        verify(mWifiScanner).startScan(anyObject(), anyObject(), anyObject(), anyObject());
    }

    /**
     * Verify that we perform partial scan when the current RSSI is high,
     * Tx/Rx success rates are low, and when the currently connected network is present
     * in scan cache in WifiConfigManager.
     * WifiConnectivityManager does partial scan only when firmware roaming is not supported.
     *
     * Expected behavior: WifiConnectivityManager does partial scan.
     */
    @Test
    public void checkPartialSCanRequestedWithHighRssiNoActiveStreamWithoutFwRoaming() {
        when(mWifiNS.isNetworkSufficient(eq(mWifiInfo))).thenReturn(false);
        when(mWifiNS.hasActiveStream(eq(mWifiInfo))).thenReturn(false);
        when(mWifiNS.hasSufficientLinkQuality(eq(mWifiInfo))).thenReturn(true);
        when(mWifiNS.hasInternetOrExpectNoInternet(eq(mWifiInfo))).thenReturn(true);

        mResources.setInteger(
                R.integer.config_wifi_framework_associated_partial_scan_max_num_active_channels,
                10);

        WifiConfiguration configuration = WifiConfigurationTestUtil.createOpenNetwork();
        configuration.networkId = TEST_CONNECTED_NETWORK_ID;
        when(mWifiConfigManager.getConfiguredNetwork(TEST_CONNECTED_NETWORK_ID))
                .thenReturn(configuration);
        List<Integer> channelList = linkScoreCardFreqsToNetwork(configuration).get(0);

        when(mPrimaryClientModeManager.getConnectedWifiConfiguration())
                .thenReturn(configuration);
        when(mWifiConnectivityHelper.isFirmwareRoamingSupported()).thenReturn(false);

        doAnswer(new AnswerWithArguments() {
            public void answer(ScanSettings settings, Executor executor, ScanListener listener,
                               WorkSource workSource) throws Exception {
                assertEquals(settings.band, WifiScanner.WIFI_BAND_UNSPECIFIED);
                assertEquals(settings.channels.length, channelList.size());
                for (int chanIdx = 0; chanIdx < settings.channels.length; chanIdx++) {
                    assertTrue(channelList.contains(settings.channels[chanIdx].frequency));
                }
            }}).when(mWifiScanner).startScan(anyObject(), anyObject(), anyObject(), anyObject());

        // Set screen to ON
        setScreenState(true);

        // Set WiFi to connected state to trigger periodic scan
        mWifiConnectivityManager.handleConnectionStateChanged(
                mPrimaryClientModeManager,
                WifiConnectivityManager.WIFI_STATE_CONNECTED);

        verify(mWifiScanner).startScan(anyObject(), anyObject(), anyObject(), anyObject());
    }


    /**
     * Verify that we fall back to full band scan when the currently connected network's tx/rx
     * success rate is high, RSSI is also high but the currently connected network
     * is not present in scan cache in WifiConfigManager.
     * This is simulated by returning an empty hashset in |makeChannelList|.
     *
     * Expected behavior: WifiConnectivityManager does full band scan.
     */
    @Test
    public void checkSingleScanSettingsWhenConnectedWithHighDataRateNotInCache() {
        when(mWifiNS.isNetworkSufficient(eq(mWifiInfo))).thenReturn(true);
        when(mWifiNS.hasActiveStream(eq(mWifiInfo))).thenReturn(true);
        when(mWifiNS.hasSufficientLinkQuality(eq(mWifiInfo))).thenReturn(true);
        when(mWifiNS.hasInternetOrExpectNoInternet(eq(mWifiInfo))).thenReturn(true);

        WifiConfiguration configuration = WifiConfigurationTestUtil.createOpenNetwork();
        configuration.networkId = TEST_CONNECTED_NETWORK_ID;
        when(mWifiConfigManager.getConfiguredNetwork(TEST_CONNECTED_NETWORK_ID))
                .thenReturn(configuration);
        List<Integer> channelList = linkScoreCardFreqsToNetwork(configuration).get(0);

        when(mPrimaryClientModeManager.getConnectedWifiConfiguration())
                .thenReturn(new WifiConfiguration());

        doAnswer(new AnswerWithArguments() {
            public void answer(ScanSettings settings, Executor executor, ScanListener listener,
                    WorkSource workSource) throws Exception {
                assertNull(settings.channels);
                if (SdkLevel.isAtLeastS()) {
                    assertEquals(WifiScanner.WIFI_BAND_24_5_WITH_DFS_6_GHZ, settings.band);
                    assertEquals("RNR should be enabled for full scans",
                            WifiScanner.WIFI_RNR_ENABLED, settings.getRnrSetting());
                    assertTrue("PSC should be enabled for full scans",
                            settings.is6GhzPscOnlyEnabled());
                } else {
                    assertEquals(WifiScanner.WIFI_BAND_ALL, settings.band);
                }
            }}).when(mWifiScanner).startScan(anyObject(), anyObject(), anyObject(), anyObject());

        // Set screen to ON
        setScreenState(true);

        // Set WiFi to connected state to trigger periodic scan
        mWifiConnectivityManager.handleConnectionStateChanged(
                mPrimaryClientModeManager,
                WifiConnectivityManager.WIFI_STATE_CONNECTED);

        verify(mWifiScanner).startScan(anyObject(), anyObject(), anyObject(), anyObject());
    }

    /**
     *  Verify that we retry connectivity scan up to MAX_SCAN_RESTART_ALLOWED times
     *  when Wifi somehow gets into a bad state and fails to scan.
     *
     * Expected behavior: WifiConnectivityManager schedules connectivity scan
     * MAX_SCAN_RESTART_ALLOWED times.
     */
    @Test
    public void checkMaximumScanRetry() {
        // Set screen to ON
        setScreenState(true);

        doAnswer(new AnswerWithArguments() {
            public void answer(ScanSettings settings, Executor executor, ScanListener listener,
                    WorkSource workSource) throws Exception {
                listener.onFailure(-1, "ScanFailure");
            }}).when(mWifiScanner).startScan(anyObject(), anyObject(), anyObject(), anyObject());

        // Set WiFi to disconnected state to trigger the single scan based periodic scan
        mWifiConnectivityManager.handleConnectionStateChanged(
                mPrimaryClientModeManager,
                WifiConnectivityManager.WIFI_STATE_DISCONNECTED);

        // Fire the alarm timer 2x timers
        for (int i = 0; i < (WifiConnectivityManager.MAX_SCAN_RESTART_ALLOWED * 2); i++) {
            mAlarmManager.dispatch(WifiConnectivityManager.RESTART_SINGLE_SCAN_TIMER_TAG);
            mLooper.dispatchAll();
        }

        // Verify that the connectivity scan has been retried for MAX_SCAN_RESTART_ALLOWED
        // times. Note, WifiScanner.startScan() is invoked MAX_SCAN_RESTART_ALLOWED + 1 times.
        // The very first scan is the initial one, and the other MAX_SCAN_RESTART_ALLOWED
        // are the retrial ones.
        verify(mWifiScanner, times(WifiConnectivityManager.MAX_SCAN_RESTART_ALLOWED + 1)).startScan(
                anyObject(), anyObject(), anyObject(), anyObject());
    }

    /**
     * Verify that a successful scan result resets scan retry counter
     *
     * Steps
     * 1. Trigger a scan that fails
     * 2. Let the retry succeed
     * 3. Trigger a scan again and have it and all subsequent retries fail
     * 4. Verify that there are MAX_SCAN_RESTART_ALLOWED + 3 startScan calls. (2 are from the
     * original scans, and MAX_SCAN_RESTART_ALLOWED + 1 from retries)
     */
    @Test
    public void verifyScanFailureCountIsResetAfterOnResult() {
        // Setup WifiScanner to fail
        doAnswer(new AnswerWithArguments() {
            public void answer(ScanSettings settings, Executor executor, ScanListener listener,
                    WorkSource workSource) throws Exception {
                listener.onFailure(-1, "ScanFailure");
            }}).when(mWifiScanner).startScan(anyObject(), anyObject(), anyObject(), anyObject());

        mWifiConnectivityManager.forceConnectivityScan(null);
        // make the retry succeed
        doAnswer(new AnswerWithArguments() {
            public void answer(ScanSettings settings, Executor executor, ScanListener listener,
                    WorkSource workSource) throws Exception {
                listener.onResults(null);
            }}).when(mWifiScanner).startScan(anyObject(), anyObject(), anyObject(), anyObject());
        mAlarmManager.dispatch(WifiConnectivityManager.RESTART_SINGLE_SCAN_TIMER_TAG);
        mLooper.dispatchAll();

        // Verify that startScan is called once for the original scan, plus once for the retry.
        // The successful retry should have now cleared the restart count
        verify(mWifiScanner, times(2)).startScan(
                anyObject(), anyObject(), anyObject(), anyObject());

        // Now force a new scan and verify we retry MAX_SCAN_RESTART_ALLOWED times
        doAnswer(new AnswerWithArguments() {
            public void answer(ScanSettings settings, Executor executor, ScanListener listener,
                    WorkSource workSource) throws Exception {
                listener.onFailure(-1, "ScanFailure");
            }}).when(mWifiScanner).startScan(anyObject(), anyObject(), anyObject(), anyObject());
        mWifiConnectivityManager.forceConnectivityScan(null);
        // Fire the alarm timer 2x timers
        for (int i = 0; i < (WifiConnectivityManager.MAX_SCAN_RESTART_ALLOWED * 2); i++) {
            mAlarmManager.dispatch(WifiConnectivityManager.RESTART_SINGLE_SCAN_TIMER_TAG);
            mLooper.dispatchAll();
        }

        // Verify that the connectivity scan has been retried for MAX_SCAN_RESTART_ALLOWED + 3
        // times. Note, WifiScanner.startScan() is invoked 2 times by the first part of this test,
        // and additionally MAX_SCAN_RESTART_ALLOWED + 1 times from forceConnectivityScan and
        // subsequent retries.
        verify(mWifiScanner, times(WifiConnectivityManager.MAX_SCAN_RESTART_ALLOWED + 3)).startScan(
                anyObject(), anyObject(), anyObject(), anyObject());
    }

    /**
     * Listen to scan results not requested by WifiConnectivityManager and
     * act on them.
     *
     * Expected behavior: WifiConnectivityManager calls
     * ClientModeManager.startConnectToNetwork() with the
     * expected candidate network ID and BSSID.
     */
    @Test
    public void listenToAllSingleScanResults() {
        ScanSettings settings = new ScanSettings();
        ScanListener scanListener = mock(ScanListener.class);

        // Request a single scan outside of WifiConnectivityManager.
        mWifiScanner.startScan(settings, mock(Executor.class), scanListener, WIFI_WORK_SOURCE);

        // Verify that WCM receives the scan results and initiates a connection
        // to the network.
        verify(mPrimaryClientModeManager).startConnectToNetwork(
                CANDIDATE_NETWORK_ID, Process.WIFI_UID, CANDIDATE_BSSID);
    }

    /**
     *  Verify that a forced connectivity scan waits for full band scan
     *  results.
     *
     * Expected behavior: WifiConnectivityManager doesn't invoke
     * ClientModeManager.startConnectToNetwork() when full band scan
     * results are not available.
     */
    @Test
    public void waitForFullBandScanResults() {
        // Set WiFi to connected state.
        setWifiStateConnected();

        // Set up as partial scan results.
        when(mScanData.getScannedBandsInternal()).thenReturn(WifiScanner.WIFI_BAND_5_GHZ);

        // Force a connectivity scan which enables WifiConnectivityManager
        // to wait for full band scan results.
        mWifiConnectivityManager.forceConnectivityScan(WIFI_WORK_SOURCE);

        // No roaming because no full band scan results.
        verify(mPrimaryClientModeManager, times(0)).startConnectToNetwork(
                CANDIDATE_NETWORK_ID, Process.WIFI_UID, CANDIDATE_BSSID);

        // Set up as full band scan results.
        when(mScanData.getScannedBandsInternal()).thenReturn(WifiScanner.WIFI_BAND_ALL);

        // Force a connectivity scan which enables WifiConnectivityManager
        // to wait for full band scan results.
        mWifiConnectivityManager.forceConnectivityScan(WIFI_WORK_SOURCE);

        // Roaming attempt because full band scan results are available.
        verify(mPrimaryClientModeManager).startConnectToNetwork(
                CANDIDATE_NETWORK_ID, Process.WIFI_UID, CANDIDATE_BSSID);
    }

    /**
     * Verify when new scanResults are available, UserDisabledList will be updated.
     */
    @Test
    public void verifyUserDisabledListUpdated() {
        mResources.setBoolean(
                R.bool.config_wifi_framework_use_single_radio_chain_scan_results_network_selection,
                true);
        verify(mWifiConfigManager, never()).updateUserDisabledList(anyList());
        Set<String> updateNetworks = new HashSet<>();
        mScanData = createScanDataWithDifferentRadioChainInfos();
        int i = 0;
        for (ScanResult scanResult : mScanData.getResults()) {
            scanResult.SSID = TEST_SSID + i;
            updateNetworks.add(ScanResultUtil.createQuotedSsid(scanResult.SSID));
            i++;
        }
        updateNetworks.add(TEST_FQDN);
        mScanData.getResults()[0].setFlag(ScanResult.FLAG_PASSPOINT_NETWORK);
        HashMap<String, Map<Integer, List<ScanResult>>> passpointNetworks = new HashMap<>();
        passpointNetworks.put(TEST_FQDN, new HashMap<>());
        when(mPasspointManager.getAllMatchingPasspointProfilesForScanResults(any()))
                .thenReturn(passpointNetworks);

        mWifiConnectivityManager.forceConnectivityScan(WIFI_WORK_SOURCE);
        ArgumentCaptor<ArrayList<String>> listArgumentCaptor =
                ArgumentCaptor.forClass(ArrayList.class);
        verify(mWifiConfigManager).updateUserDisabledList(listArgumentCaptor.capture());
        assertEquals(updateNetworks, new HashSet<>(listArgumentCaptor.getValue()));
    }

    /**
     * Verify that after receiving scan results, we attempt to clear expired recent failure reasons.
     */
    @Test
    public void verifyClearExpiredRecentFailureStatusAfterScan() {
        // mWifiScanner is mocked to directly return scan results when a scan is triggered.
        mWifiConnectivityManager.forceConnectivityScan(WIFI_WORK_SOURCE);
        verify(mWifiConfigManager).cleanupExpiredRecentFailureReasons();
    }

    /**
     *  Verify that a blocklisted BSSID becomes available only after
     *  BSSID_BLOCKLIST_EXPIRE_TIME_MS.
     */
    @Test
    public void verifyBlocklistRefreshedAfterScanResults() {
        WifiConfiguration disabledConfig = WifiConfigurationTestUtil.createPskNetwork();
        List<ScanDetail> mockScanDetails = new ArrayList<>();
        mockScanDetails.add(mock(ScanDetail.class));
        when(mWifiBlocklistMonitor.tryEnablingBlockedBssids(any())).thenReturn(mockScanDetails);
        when(mWifiConfigManager.getSavedNetworkForScanDetail(any())).thenReturn(
                disabledConfig);

        when(mWifiConnectivityHelper.isFirmwareRoamingSupported()).thenReturn(true);

        InOrder inOrder = inOrder(mWifiBlocklistMonitor, mWifiConfigManager);
        // Force a connectivity scan
        inOrder.verify(mWifiBlocklistMonitor, never())
                .updateAndGetBssidBlocklistForSsids(anySet());
        mWifiConnectivityManager.forceConnectivityScan(WIFI_WORK_SOURCE);

        inOrder.verify(mWifiBlocklistMonitor).tryEnablingBlockedBssids(any());
        inOrder.verify(mWifiConfigManager).updateNetworkSelectionStatus(disabledConfig.networkId,
                WifiConfiguration.NetworkSelectionStatus.DISABLED_NONE);
        inOrder.verify(mWifiBlocklistMonitor).updateAndGetBssidBlocklistForSsids(anySet());
    }

    /**
     *  Verify blocklists and ephemeral networks are cleared from WifiConfigManager when exiting
     *  Wifi client mode. And if requires, ANQP cache is also flushed.
     */
    @Test
    public void clearEnableTemporarilyDisabledNetworksWhenExitingWifiClientMode() {
        when(mWifiConnectivityHelper.isFirmwareRoamingSupported()).thenReturn(true);
        when(mWifiGlobals.flushAnqpCacheOnWifiToggleOffEvent()).thenReturn(true);
        // Exit Wifi client mode.
        setWifiEnabled(false);

        // Verify the blocklists is cleared again.
        verify(mWifiConfigManager).enableTemporaryDisabledNetworks();
        verify(mWifiConfigManager).stopRestrictingAutoJoinToSubscriptionId();
        verify(mWifiConfigManager).removeAllEphemeralOrPasspointConfiguredNetworks();
        verify(mWifiConfigManager).clearUserTemporarilyDisabledList();

        // Verify ANQP cache is flushed.
        verify(mPasspointManager).clearAnqpRequestsAndFlushCache();
        // Verify WifiNetworkSelector is informed of the disable.
        verify(mWifiNS).resetOnDisable();
    }

    /**
     * Verifies that the ANQP cache is not flushed when the configuration does not permit it.
     */
    @Test
    public void testAnqpFlushCacheSkippedIfNotConfigured() {
        when(mWifiConnectivityHelper.isFirmwareRoamingSupported()).thenReturn(true);
        when(mWifiGlobals.flushAnqpCacheOnWifiToggleOffEvent()).thenReturn(false);
        // Exit Wifi client mode.
        setWifiEnabled(false);

        // Verify ANQP cache is not flushed.
        verify(mPasspointManager, never()).clearAnqpRequestsAndFlushCache();
    }

    /**
     *  Verify that BSSID blocklist gets cleared when preparing for a forced connection
     *  initiated by user/app.
     */
    @Test
    public void clearBssidBlocklistWhenPreparingForForcedConnection() {
        when(mWifiConnectivityHelper.isFirmwareRoamingSupported()).thenReturn(true);
        // Prepare for a forced connection attempt.
        WifiConfiguration currentNetwork = generateWifiConfig(
                0, CANDIDATE_NETWORK_ID, CANDIDATE_SSID, false, true, null, null,
                WifiConfigurationTestUtil.SECURITY_NONE);
        when(mWifiConfigManager.getConfiguredNetwork(anyInt())).thenReturn(currentNetwork);
        mWifiConnectivityManager.prepareForForcedConnection(1);
        verify(mWifiBlocklistMonitor).clearBssidBlocklistForSsid(CANDIDATE_SSID);
    }

    /**
     * When WifiConnectivityManager is on and Wifi client mode is enabled, framework
     * queries firmware via WifiConnectivityHelper to check if firmware roaming is
     * supported and its capability.
     *
     * Expected behavior: WifiConnectivityManager#setWifiEnabled calls into
     * WifiConnectivityHelper#getFirmwareRoamingInfo
     */
    @Test
    public void verifyGetFirmwareRoamingInfoIsCalledWhenEnableWiFiAndWcmOn() {
        // WifiConnectivityManager is on by default
        setWifiEnabled(true);
        verify(mWifiConnectivityHelper).getFirmwareRoamingInfo();
    }

    /**
     * When WifiConnectivityManager is off,  verify that framework does not
     * query firmware via WifiConnectivityHelper to check if firmware roaming is
     * supported and its capability when enabling Wifi client mode.
     *
     * Expected behavior: WifiConnectivityManager#setWifiEnabled does not call into
     * WifiConnectivityHelper#getFirmwareRoamingInfo
     */
    @Test
    public void verifyGetFirmwareRoamingInfoIsNotCalledWhenEnableWiFiAndWcmOff() {
        reset(mWifiConnectivityHelper);
        mWifiConnectivityManager.setAutoJoinEnabledExternal(false);
        setWifiEnabled(true);
        verify(mWifiConnectivityHelper, times(0)).getFirmwareRoamingInfo();
    }

    /*
     * Firmware supports controlled roaming.
     * Connect to a network which doesn't have a config specified BSSID.
     *
     * Expected behavior: WifiConnectivityManager calls
     * ClientModeManager.startConnectToNetwork() with the
     * expected candidate network ID, and the BSSID value should be
     * 'any' since firmware controls the roaming.
     */
    @Test
    public void useAnyBssidToConnectWhenFirmwareRoamingOnAndConfigHasNoBssidSpecified() {
        // Firmware controls roaming
        when(mWifiConnectivityHelper.isFirmwareRoamingSupported()).thenReturn(true);

        // Set screen to on
        setScreenState(true);

        // Set WiFi to disconnected state
        mWifiConnectivityManager.handleConnectionStateChanged(
                mPrimaryClientModeManager,
                WifiConnectivityManager.WIFI_STATE_DISCONNECTED);

        verify(mPrimaryClientModeManager).startConnectToNetwork(
                CANDIDATE_NETWORK_ID, Process.WIFI_UID, ClientModeImpl.SUPPLICANT_BSSID_ANY);
    }

    /*
     * Firmware supports controlled roaming.
     * Connect to a network which has a config specified BSSID.
     *
     * Expected behavior: WifiConnectivityManager calls
     * ClientModeManager.startConnectToNetwork() with the
     * expected candidate network ID, and the BSSID value should be
     * the config specified one.
     */
    @Test
    public void useConfigSpecifiedBssidToConnectWhenFirmwareRoamingOn() {
        // Firmware controls roaming
        when(mWifiConnectivityHelper.isFirmwareRoamingSupported()).thenReturn(true);

        // Set up the candidate configuration such that it has a BSSID specified.
        WifiConfiguration candidate = generateWifiConfig(
                0, CANDIDATE_NETWORK_ID, CANDIDATE_SSID, false, true, null, null,
                WifiConfigurationTestUtil.SECURITY_NONE);
        candidate.BSSID = CANDIDATE_BSSID; // config specified
        ScanResult candidateScanResult = new ScanResult();
        candidateScanResult.SSID = CANDIDATE_SSID;
        candidateScanResult.BSSID = CANDIDATE_BSSID;
        candidate.getNetworkSelectionStatus().setCandidate(candidateScanResult);
        when(mWifiNS.selectNetwork(any())).thenReturn(candidate);

        // Set screen to on
        setScreenState(true);

        // Set WiFi to disconnected state
        mWifiConnectivityManager.handleConnectionStateChanged(
                mPrimaryClientModeManager,
                WifiConnectivityManager.WIFI_STATE_DISCONNECTED);

        verify(mPrimaryClientModeManager).startConnectToNetwork(
                CANDIDATE_NETWORK_ID, Process.WIFI_UID, CANDIDATE_BSSID);
        verify(mPrimaryClientModeManager).enableRoaming(false);

        verify(mWifiMetrics).noteFirstNetworkSelectionAfterBoot(true);
    }

    /*
     * Firmware does not support controlled roaming.
     * Connect to a network which doesn't have a config specified BSSID.
     *
     * Expected behavior: WifiConnectivityManager calls
     * ClientModeManager.startConnectToNetwork() with the expected candidate network ID,
     * and the BSSID value should be the candidate scan result specified.
     */
    @Test
    public void useScanResultBssidToConnectWhenFirmwareRoamingOffAndConfigHasNoBssidSpecified() {
        // Set screen to on
        setScreenState(true);

        // Set WiFi to disconnected state
        mWifiConnectivityManager.handleConnectionStateChanged(
                mPrimaryClientModeManager,
                WifiConnectivityManager.WIFI_STATE_DISCONNECTED);

        verify(mPrimaryClientModeManager).startConnectToNetwork(
                CANDIDATE_NETWORK_ID, Process.WIFI_UID, CANDIDATE_BSSID);
    }

    /*
     * Firmware does not support controlled roaming.
     * Connect to a network which has a config specified BSSID.
     *
     * Expected behavior: WifiConnectivityManager calls
     * ClientModeManager.startConnectToNetwork() with the expected candidate network ID,
     * and the BSSID value should be the config specified one.
     */
    @Test
    public void useConfigSpecifiedBssidToConnectionWhenFirmwareRoamingOff() {
        // Set up the candidate configuration such that it has a BSSID specified.
        WifiConfiguration candidate = generateWifiConfig(
                0, CANDIDATE_NETWORK_ID, CANDIDATE_SSID, false, true, null, null,
                WifiConfigurationTestUtil.SECURITY_NONE);
        candidate.BSSID = CANDIDATE_BSSID; // config specified
        ScanResult candidateScanResult = new ScanResult();
        candidateScanResult.SSID = CANDIDATE_SSID;
        candidateScanResult.BSSID = CANDIDATE_BSSID;
        candidate.getNetworkSelectionStatus().setCandidate(candidateScanResult);
        when(mWifiNS.selectNetwork(any())).thenReturn(candidate);

        // Set screen to on
        setScreenState(true);

        // Set WiFi to disconnected state
        mWifiConnectivityManager.handleConnectionStateChanged(
                mPrimaryClientModeManager,
                WifiConnectivityManager.WIFI_STATE_DISCONNECTED);

        verify(mPrimaryClientModeManager).startConnectToNetwork(
                CANDIDATE_NETWORK_ID, Process.WIFI_UID, CANDIDATE_BSSID);
    }

    /**
     * Firmware does not support controlled roaming.
     * WiFi in connected state, framework triggers roaming.
     *
     * Expected behavior: WifiConnectivityManager invokes
     * ClientModeManager.startRoamToNetwork().
     */
    @Test
    public void frameworkInitiatedRoaming() {
        // Set WiFi to connected state
        setWifiStateConnected(CANDIDATE_NETWORK_ID, CANDIDATE_BSSID_2);

        // Set screen to on
        setScreenState(true);

        verify(mPrimaryClientModeManager).startRoamToNetwork(eq(CANDIDATE_NETWORK_ID),
                mCandidateBssidCaptor.capture());
        assertEquals(mCandidateBssidCaptor.getValue(), CANDIDATE_BSSID);
        verify(mPrimaryClientModeManager, never()).startConnectToNetwork(
                anyInt(), anyInt(), anyObject());
    }

    /**
     * Firmware supports controlled roaming.
     * WiFi in connected state, framework does not trigger roaming
     * as it's handed off to the firmware.
     *
     * Expected behavior: WifiConnectivityManager doesn't invoke
     * ClientModeManager.startRoamToNetwork().
     */
    @Test
    public void noFrameworkRoamingIfConnectedAndFirmwareRoamingSupported() {
        // Set WiFi to connected state
        setWifiStateConnected(CANDIDATE_NETWORK_ID, CANDIDATE_BSSID_2);

        // Firmware controls roaming
        when(mWifiConnectivityHelper.isFirmwareRoamingSupported()).thenReturn(true);

        // Set screen to on
        setScreenState(true);

        verify(mPrimaryClientModeManager, never()).startRoamToNetwork(anyInt(), anyObject());
        verify(mPrimaryClientModeManager, never()).startConnectToNetwork(
                anyInt(), anyInt(), anyObject());
    }

    /*
     * Wifi in disconnected state. Drop the connection attempt if the recommended
     * network configuration has a BSSID specified but the scan result BSSID doesn't
     * match it.
     *
     * Expected behavior: WifiConnectivityManager doesn't invoke
     * ClientModeManager.startConnectToNetwork().
     */
    @Test
    public void dropConnectAttemptIfConfigSpecifiedBssidDifferentFromScanResultBssid() {
        // Set up the candidate configuration such that it has a BSSID specified.
        WifiConfiguration candidate = generateWifiConfig(
                0, CANDIDATE_NETWORK_ID, CANDIDATE_SSID, false, true, null, null,
                WifiConfigurationTestUtil.SECURITY_NONE);
        candidate.BSSID = CANDIDATE_BSSID; // config specified
        ScanResult candidateScanResult = new ScanResult();
        candidateScanResult.SSID = CANDIDATE_SSID;
        // Set up the scan result BSSID to be different from the config specified one.
        candidateScanResult.BSSID = INVALID_SCAN_RESULT_BSSID;
        candidate.getNetworkSelectionStatus().setCandidate(candidateScanResult);
        when(mWifiNS.selectNetwork(any())).thenReturn(candidate);

        // Set screen to on
        setScreenState(true);

        // Set WiFi to disconnected state
        mWifiConnectivityManager.handleConnectionStateChanged(
                mPrimaryClientModeManager,
                WifiConnectivityManager.WIFI_STATE_DISCONNECTED);

        verify(mPrimaryClientModeManager, times(0)).startConnectToNetwork(
                CANDIDATE_NETWORK_ID, Process.WIFI_UID, CANDIDATE_BSSID);
    }

    /*
     * Wifi in connected state. Drop the roaming attempt if the recommended
     * network configuration has a BSSID specified but the scan result BSSID doesn't
     * match it.
     *
     * Expected behavior: WifiConnectivityManager doesn't invoke
     * ClientModeManager.startRoamToNetwork().
     */
    @Test
    public void dropRoamingAttemptIfConfigSpecifiedBssidDifferentFromScanResultBssid() {
        // Mock the currently connected network which has the same networkID and
        // SSID as the one to be selected.
        WifiConfiguration currentNetwork = generateWifiConfig(
                TEST_CONNECTED_NETWORK_ID, 0, CANDIDATE_SSID, false, true, null, null,
                WifiConfigurationTestUtil.SECURITY_NONE);
        when(mWifiConfigManager.getConfiguredNetwork(anyInt())).thenReturn(currentNetwork);

        // Set up the candidate configuration such that it has a BSSID specified.
        WifiConfiguration candidate = generateWifiConfig(
                TEST_CONNECTED_NETWORK_ID, 0, CANDIDATE_SSID, false, true, null, null,
                WifiConfigurationTestUtil.SECURITY_NONE);
        candidate.BSSID = CANDIDATE_BSSID; // config specified
        ScanResult candidateScanResult = new ScanResult();
        candidateScanResult.SSID = CANDIDATE_SSID;
        // Set up the scan result BSSID to be different from the config specified one.
        candidateScanResult.BSSID = INVALID_SCAN_RESULT_BSSID;
        candidate.getNetworkSelectionStatus().setCandidate(candidateScanResult);
        when(mWifiNS.selectNetwork(any())).thenReturn(candidate);

        // Set WiFi to connected state
        setWifiStateConnected();

        // Set screen to on
        setScreenState(true);

        verify(mPrimaryClientModeManager, times(0)).startRoamToNetwork(anyInt(), anyObject());
    }

    /**
     *  Dump local log buffer.
     *
     * Expected behavior: Logs dumped from WifiConnectivityManager.dump()
     * contain the message we put in mLocalLog.
     */
    @Test
    public void dumpLocalLog() {
        final String localLogMessage = "This is a message from the test";
        mLocalLog.log(localLogMessage);

        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        mWifiConnectivityManager.dump(new FileDescriptor(), pw, new String[]{});
        assertTrue(sw.toString().contains(localLogMessage));
    }

    /**
     *  Dump ONA controller.
     *
     * Expected behavior: {@link OpenNetworkNotifier#dump(FileDescriptor, PrintWriter,
     * String[])} is invoked.
     */
    @Test
    public void dumpNotificationController() {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        mWifiConnectivityManager.dump(new FileDescriptor(), pw, new String[]{});

        verify(mOpenNetworkNotifier).dump(any(), any(), any());
    }

    /**
     * Create scan data with different radio chain infos:
     * First scan result has null radio chain info (No DBS support).
     * Second scan result has empty radio chain info (No DBS support).
     * Third scan result has 1 radio chain info (DBS scan).
     * Fourth scan result has 2 radio chain info (non-DBS scan).
     */
    private ScanData createScanDataWithDifferentRadioChainInfos() {
        // Create 4 scan results.
        ScanData[] scanDatas =
                ScanTestUtil.createScanDatas(new int[][]{{5150, 5175, 2412, 2400}}, new int[]{0});
        // WCM barfs if the scan result does not have an IE.
        scanDatas[0].getResults()[0].informationElements = new InformationElement[0];
        scanDatas[0].getResults()[1].informationElements = new InformationElement[0];
        scanDatas[0].getResults()[2].informationElements = new InformationElement[0];
        scanDatas[0].getResults()[3].informationElements = new InformationElement[0];
        scanDatas[0].getResults()[0].radioChainInfos = null;
        scanDatas[0].getResults()[1].radioChainInfos = new ScanResult.RadioChainInfo[0];
        scanDatas[0].getResults()[2].radioChainInfos = new ScanResult.RadioChainInfo[1];
        scanDatas[0].getResults()[3].radioChainInfos = new ScanResult.RadioChainInfo[2];

        return scanDatas[0];
    }

    /**
     * If |config_wifi_framework_use_single_radio_chain_scan_results_network_selection| flag is
     * false, WifiConnectivityManager should filter scan results which contain scans from a single
     * radio chain (i.e DBS scan).
     * Note:
     * a) ScanResult with no radio chain indicates a lack of DBS support on the device.
     * b) ScanResult with 2 radio chain info indicates a scan done using both the radio chains
     * on a DBS supported device.
     *
     * Expected behavior: WifiConnectivityManager invokes
     * {@link WifiNetworkSelector#getCandidatesFromScan(List, Set, List, boolean, boolean, Set, boolean)}
     * boolean, boolean, boolean)} after filtering out the scan results obtained via DBS scan.
     */
    @Test
    public void filterScanResultsWithOneRadioChainInfoForNetworkSelectionIfConfigDisabled() {
        mResources.setBoolean(
                R.bool.config_wifi_framework_use_single_radio_chain_scan_results_network_selection,
                false);
        when(mWifiNS.selectNetwork(any())).thenReturn(null);
        mWifiConnectivityManager = createConnectivityManager();

        mScanData = createScanDataWithDifferentRadioChainInfos();

        // Capture scan details which were sent to network selector.
        final List<ScanDetail> capturedScanDetails = new ArrayList<>();
        doAnswer(new AnswerWithArguments() {
            public List<WifiCandidates.Candidate> answer(
                    List<ScanDetail> scanDetails, Set<String> bssidBlocklist,
                    List<WifiNetworkSelector.ClientModeManagerState> cmmStates,
                    boolean untrustedNetworkAllowed,
                    boolean oemPaidNetworkAllowed, boolean oemPrivateNetworkAllowed,
                    Set<Integer> restrictedNetworkAllowedUids, boolean multiInternetNetworkAllowed)
                    throws Exception {
                capturedScanDetails.addAll(scanDetails);
                return null;
            }}).when(mWifiNS).getCandidatesFromScan(
                    any(), any(), any(), anyBoolean(), eq(true), eq(false), any(), eq(false));

        mWifiConnectivityManager.setTrustedConnectionAllowed(true);
        mWifiConnectivityManager.setOemPaidConnectionAllowed(true, new WorkSource());
        // Set WiFi to disconnected state with screen on which triggers a scan immediately.
        setWifiEnabled(true);
        setScreenState(true);
        mWifiConnectivityManager.handleConnectionStateChanged(
                mPrimaryClientModeManager,
                WifiConnectivityManager.WIFI_STATE_DISCONNECTED);

        // We should have filtered out the 3rd scan result.
        assertEquals(3, capturedScanDetails.size());
        List<ScanResult> capturedScanResults =
                capturedScanDetails.stream().map(ScanDetail::getScanResult)
                        .collect(Collectors.toList());

        assertEquals(3, capturedScanResults.size());
        assertTrue(capturedScanResults.contains(mScanData.getResults()[0]));
        assertTrue(capturedScanResults.contains(mScanData.getResults()[1]));
        assertFalse(capturedScanResults.contains(mScanData.getResults()[2]));
        assertTrue(capturedScanResults.contains(mScanData.getResults()[3]));
    }

    /**
     * If |config_wifi_framework_use_single_radio_chain_scan_results_network_selection| flag is
     * true, WifiConnectivityManager should not filter scan results which contain scans from a
     * single radio chain (i.e DBS scan).
     * Note:
     * a) ScanResult with no radio chain indicates a lack of DBS support on the device.
     * b) ScanResult with 2 radio chain info indicates a scan done using both the radio chains
     * on a DBS supported device.
     *
     * Expected behavior: WifiConnectivityManager invokes
     * {@link WifiNetworkSelector#selectNetwork(List)}
     * after filtering out the scan results obtained via DBS scan.
     */
    @Test
    public void dontFilterScanResultsWithOneRadioChainInfoForNetworkSelectionIfConfigEnabled() {
        mResources.setBoolean(
                R.bool.config_wifi_framework_use_single_radio_chain_scan_results_network_selection,
                true);
        when(mWifiNS.selectNetwork(any())).thenReturn(null);
        mWifiConnectivityManager = createConnectivityManager();

        mScanData = createScanDataWithDifferentRadioChainInfos();

        // Capture scan details which were sent to network selector.
        final List<ScanDetail> capturedScanDetails = new ArrayList<>();
        doAnswer(new AnswerWithArguments() {
            public List<WifiCandidates.Candidate> answer(
                    List<ScanDetail> scanDetails, Set<String> bssidBlocklist,
                    List<WifiNetworkSelector.ClientModeManagerState> cmmStates,
                    boolean untrustedNetworkAllowed,
                    boolean oemPaidNetworkAllowed, boolean oemPrivateNetworkAllowed,
                    Set<Integer> restrictedNetworkAllowedUids, boolean multiInternetNetworkAllowed)
                    throws Exception {
                capturedScanDetails.addAll(scanDetails);
                return null;
            }}).when(mWifiNS).getCandidatesFromScan(
                any(), any(), any(), anyBoolean(), eq(false), eq(true), any(), eq(false));

        mWifiConnectivityManager.setTrustedConnectionAllowed(true);
        mWifiConnectivityManager.setOemPrivateConnectionAllowed(true, new WorkSource());
        // Set WiFi to disconnected state with screen on which triggers a scan immediately.
        setWifiEnabled(true);
        setScreenState(true);
        mWifiConnectivityManager.handleConnectionStateChanged(
                mPrimaryClientModeManager,
                WifiConnectivityManager.WIFI_STATE_DISCONNECTED);

        // We should not filter any of the scan results.
        assertEquals(4, capturedScanDetails.size());
        List<ScanResult> capturedScanResults =
                capturedScanDetails.stream().map(ScanDetail::getScanResult)
                        .collect(Collectors.toList());

        assertEquals(4, capturedScanResults.size());
        assertTrue(capturedScanResults.contains(mScanData.getResults()[0]));
        assertTrue(capturedScanResults.contains(mScanData.getResults()[1]));
        assertTrue(capturedScanResults.contains(mScanData.getResults()[2]));
        assertTrue(capturedScanResults.contains(mScanData.getResults()[3]));
    }

    /**
     * Verify the various auto join enable/disable sequences when auto join is disabled externally.
     *
     * Expected behavior: Autojoin is turned on as a long as there is
     *  - Auto join is enabled externally
     *    And
     *  - No specific network request being processed.
     *    And
     *    - Pending generic Network request for trusted wifi connection.
     *      OR
     *    - Pending generic Network request for untrused wifi connection.
     */
    @Test
    public void verifyEnableAndDisableAutoJoinWhenExternalAutoJoinIsDisabled() {
        mWifiConnectivityManager = createConnectivityManager();

        // set wifi on & disconnected to trigger pno scans when auto-join is enabled.
        setWifiEnabled(true);
        mWifiConnectivityManager.handleConnectionStateChanged(
                mPrimaryClientModeManager,
                WifiConnectivityManager.WIFI_STATE_DISCONNECTED);

        // Disable externally.
        mWifiConnectivityManager.setAutoJoinEnabledExternal(false);

        // Enable trusted connection. This should NOT trigger a pno scan for auto-join.
        mWifiConnectivityManager.setTrustedConnectionAllowed(true);
        verify(mWifiScanner, never()).startDisconnectedPnoScan(any(), any(), any(), any());

        // End of processing a specific request. This should NOT trigger a new pno scan for
        // auto-join.
        mWifiConnectivityManager.setSpecificNetworkRequestInProgress(false);
        verify(mWifiScanner, never()).startDisconnectedPnoScan(any(), any(), any(), any());

        // Enable untrusted connection. This should NOT trigger a pno scan for auto-join.
        mWifiConnectivityManager.setUntrustedConnectionAllowed(true);
        verify(mWifiScanner, never()).startDisconnectedPnoScan(any(), any(), any(), any());
    }

    /**
     * Verify the various auto join enable/disable sequences when auto join is enabled externally.
     *
     * Expected behavior: Autojoin is turned on as a long as there is
     *  - Auto join is enabled externally
     *    And
     *  - No specific network request being processed.
     *    And
     *    - Pending generic Network request for trusted wifi connection.
     *      OR
     *    - Pending generic Network request for untrused wifi connection.
     */
    @Test
    public void verifyEnableAndDisableAutoJoin() {
        mWifiConnectivityManager = createConnectivityManager();

        // set wifi on & disconnected to trigger pno scans when auto-join is enabled.
        setWifiEnabled(true);
        mWifiConnectivityManager.handleConnectionStateChanged(
                mPrimaryClientModeManager,
                WifiConnectivityManager.WIFI_STATE_DISCONNECTED);

        // Enable trusted connection. This should trigger a pno scan for auto-join.
        mWifiConnectivityManager.setTrustedConnectionAllowed(true);
        verify(mWifiScanner).startDisconnectedPnoScan(any(), any(), any(), any());

        // Start of processing a specific request. This should stop any pno scan for auto-join.
        mWifiConnectivityManager.setSpecificNetworkRequestInProgress(true);
        verify(mWifiScanner).stopPnoScan(any());

        // End of processing a specific request. This should now trigger a new pno scan for
        // auto-join.
        mWifiConnectivityManager.setSpecificNetworkRequestInProgress(false);
        verify(mWifiScanner, times(2)).startDisconnectedPnoScan(any(), any(), any(), any());

        // Disable trusted connection. This should stop any pno scan for auto-join.
        mWifiConnectivityManager.setTrustedConnectionAllowed(false);
        verify(mWifiScanner, times(2)).stopPnoScan(any());

        // Enable untrusted connection. This should trigger a pno scan for auto-join.
        mWifiConnectivityManager.setUntrustedConnectionAllowed(true);
        verify(mWifiScanner, times(3)).startDisconnectedPnoScan(any(), any(), any(), any());
    }

    /**
     * Verify that the increased PNO interval is used when power save is on.
     */
    @Test
    public void testPnoIntervalPowerSaveEnabled() throws Exception {
        when(mDeviceConfigFacade.isWifiBatterySaverEnabled()).thenReturn(true);
        when(mPowerManagerService.isPowerSaveMode()).thenReturn(true);
        verifyPnoScanWithInterval(
                MOVING_PNO_SCAN_INTERVAL_MILLIS * POWER_SAVE_SCAN_INTERVAL_MULTIPLIER);
    }

    /**
     * Verify that the normal PNO interval is used when power save is off.
     */
    @Test
    public void testPnoIntervalPowerSaveDisabled() throws Exception {
        when(mDeviceConfigFacade.isWifiBatterySaverEnabled()).thenReturn(true);
        when(mPowerManagerService.isPowerSaveMode()).thenReturn(false);
        verifyPnoScanWithInterval(MOVING_PNO_SCAN_INTERVAL_MILLIS);
    }

    /**
     * Verify that the normal PNO interval is used when the power save feature is disabled.
     */
    @Test
    public void testPnoIntervalPowerSaveEnabled_FeatureDisabled() throws Exception {
        when(mDeviceConfigFacade.isWifiBatterySaverEnabled()).thenReturn(false);
        when(mPowerManagerService.isPowerSaveMode()).thenReturn(true);
        verifyPnoScanWithInterval(MOVING_PNO_SCAN_INTERVAL_MILLIS);
    }


    /**
     * Verify PNO scan is started with the given scan interval.
     */
    private void verifyPnoScanWithInterval(int interval) throws Exception {
        setWifiEnabled(true);
        // starts a PNO scan
        mWifiConnectivityManager.handleConnectionStateChanged(
                mPrimaryClientModeManager,
                WifiConnectivityManager.WIFI_STATE_DISCONNECTED);
        mWifiConnectivityManager.setTrustedConnectionAllowed(true);

        ArgumentCaptor<ScanSettings> scanSettingsCaptor = ArgumentCaptor.forClass(
                ScanSettings.class);
        InOrder inOrder = inOrder(mWifiScanner);

        inOrder.verify(mWifiScanner).startDisconnectedPnoScan(
                scanSettingsCaptor.capture(), any(), any(), any());
        assertEquals(interval, scanSettingsCaptor.getValue().periodInMs);
    }

    /**
     * Change device mobility state in the middle of a PNO scan. PNO scan should stop, then restart
     * with the updated scan period.
     */
    @Test
    public void changeDeviceMobilityStateDuringScan() {
        setWifiEnabled(true);

        // starts a PNO scan
        mWifiConnectivityManager.handleConnectionStateChanged(
                mPrimaryClientModeManager,
                WifiConnectivityManager.WIFI_STATE_DISCONNECTED);
        mWifiConnectivityManager.setTrustedConnectionAllowed(true);

        ArgumentCaptor<ScanSettings> scanSettingsCaptor = ArgumentCaptor.forClass(
                ScanSettings.class);
        InOrder inOrder = inOrder(mWifiScanner);

        inOrder.verify(mWifiScanner).startDisconnectedPnoScan(
                scanSettingsCaptor.capture(), any(), any(), any());
        assertEquals(scanSettingsCaptor.getValue().periodInMs, MOVING_PNO_SCAN_INTERVAL_MILLIS);

        // initial connectivity state uses moving PNO scan interval, now set it to stationary
        mWifiConnectivityManager.setDeviceMobilityState(
                WifiManager.DEVICE_MOBILITY_STATE_STATIONARY);

        inOrder.verify(mWifiScanner).stopPnoScan(any());
        inOrder.verify(mWifiScanner).startDisconnectedPnoScan(
                scanSettingsCaptor.capture(), any(), any(), any());
        assertEquals(scanSettingsCaptor.getValue().periodInMs, STATIONARY_PNO_SCAN_INTERVAL_MILLIS);
        verify(mScoringParams, times(2)).getEntryRssi(ScanResult.BAND_6_GHZ_START_FREQ_MHZ);
        verify(mScoringParams, times(2)).getEntryRssi(ScanResult.BAND_5_GHZ_START_FREQ_MHZ);
        verify(mScoringParams, times(2)).getEntryRssi(ScanResult.BAND_24_GHZ_START_FREQ_MHZ);
    }

    /**
     * Change device mobility state in the middle of a PNO scan, but it is changed to another
     * mobility state with the same scan period. Original PNO scan should continue.
     */
    @Test
    public void changeDeviceMobilityStateDuringScanWithSameScanPeriod() {
        setWifiEnabled(true);

        // starts a PNO scan
        mWifiConnectivityManager.handleConnectionStateChanged(
                mPrimaryClientModeManager,
                WifiConnectivityManager.WIFI_STATE_DISCONNECTED);
        mWifiConnectivityManager.setTrustedConnectionAllowed(true);

        ArgumentCaptor<ScanSettings> scanSettingsCaptor = ArgumentCaptor.forClass(
                ScanSettings.class);
        InOrder inOrder = inOrder(mWifiScanner);
        inOrder.verify(mWifiScanner, never()).stopPnoScan(any());
        inOrder.verify(mWifiScanner).startDisconnectedPnoScan(
                scanSettingsCaptor.capture(), any(), any(), any());
        assertEquals(scanSettingsCaptor.getValue().periodInMs, MOVING_PNO_SCAN_INTERVAL_MILLIS);

        mWifiConnectivityManager.setDeviceMobilityState(
                WifiManager.DEVICE_MOBILITY_STATE_LOW_MVMT);

        inOrder.verifyNoMoreInteractions();
    }

    /**
     * Device is already connected, setting device mobility state should do nothing since no PNO
     * scans are running. Then, when PNO scan is started afterwards, should use the new scan period.
     */
    @Test
    public void setDeviceMobilityStateBeforePnoScan() {
        // ensure no PNO scan running
        setWifiEnabled(true);
        setWifiStateConnected();

        // initial connectivity state uses moving PNO scan interval, now set it to stationary
        mWifiConnectivityManager.setDeviceMobilityState(
                WifiManager.DEVICE_MOBILITY_STATE_STATIONARY);

        // no scans should start or stop because no PNO scan is running
        verify(mWifiScanner, never()).startDisconnectedPnoScan(any(), any(), any(), any());
        verify(mWifiScanner, never()).stopPnoScan(any());

        // starts a PNO scan
        mWifiConnectivityManager.handleConnectionStateChanged(
                mPrimaryClientModeManager,
                WifiConnectivityManager.WIFI_STATE_DISCONNECTED);
        mWifiConnectivityManager.setTrustedConnectionAllowed(true);

        ArgumentCaptor<ScanSettings> scanSettingsCaptor = ArgumentCaptor.forClass(
                ScanSettings.class);

        verify(mWifiScanner).startDisconnectedPnoScan(
                scanSettingsCaptor.capture(), any(), any(), any());
        // check that now the PNO scan uses the stationary interval, even though it was set before
        // the PNO scan started
        assertEquals(scanSettingsCaptor.getValue().periodInMs, STATIONARY_PNO_SCAN_INTERVAL_MILLIS);
    }

    /**
     * Tests the metrics collection of PNO scans through changes to device mobility state and
     * starting and stopping of PNO scans.
     */
    @Test
    public void deviceMobilityStateMetricsChangeStateAndStopStart() {
        InOrder inOrder = inOrder(mWifiMetrics);

        mWifiConnectivityManager = createConnectivityManager();
        setWifiEnabled(true);

        // change mobility state while no PNO scans running
        mWifiConnectivityManager.setDeviceMobilityState(
                WifiManager.DEVICE_MOBILITY_STATE_LOW_MVMT);
        inOrder.verify(mWifiMetrics).enterDeviceMobilityState(
                WifiManager.DEVICE_MOBILITY_STATE_LOW_MVMT);

        // starts a PNO scan
        mWifiConnectivityManager.handleConnectionStateChanged(
                mPrimaryClientModeManager,
                WifiConnectivityManager.WIFI_STATE_DISCONNECTED);
        mWifiConnectivityManager.setTrustedConnectionAllowed(true);
        inOrder.verify(mWifiMetrics).logPnoScanStart();

        // change to High Movement, which has the same scan interval as Low Movement
        mWifiConnectivityManager.setDeviceMobilityState(
                WifiManager.DEVICE_MOBILITY_STATE_HIGH_MVMT);
        inOrder.verify(mWifiMetrics).logPnoScanStop();
        inOrder.verify(mWifiMetrics).enterDeviceMobilityState(
                WifiManager.DEVICE_MOBILITY_STATE_HIGH_MVMT);
        inOrder.verify(mWifiMetrics).logPnoScanStart();

        // change to Stationary, which has a different scan interval from High Movement
        mWifiConnectivityManager.setDeviceMobilityState(
                WifiManager.DEVICE_MOBILITY_STATE_STATIONARY);
        inOrder.verify(mWifiMetrics).logPnoScanStop();
        inOrder.verify(mWifiMetrics).enterDeviceMobilityState(
                WifiManager.DEVICE_MOBILITY_STATE_STATIONARY);
        inOrder.verify(mWifiMetrics).logPnoScanStart();

        // stops PNO scan
        mWifiConnectivityManager.setTrustedConnectionAllowed(false);
        inOrder.verify(mWifiMetrics).logPnoScanStop();

        // change mobility state while no PNO scans running
        mWifiConnectivityManager.setDeviceMobilityState(
                WifiManager.DEVICE_MOBILITY_STATE_HIGH_MVMT);
        inOrder.verify(mWifiMetrics).enterDeviceMobilityState(
                WifiManager.DEVICE_MOBILITY_STATE_HIGH_MVMT);

        inOrder.verifyNoMoreInteractions();
    }

    /**
     *  Verify that WifiChannelUtilization is updated
     */
    @Test
    public void verifyWifiChannelUtilizationRefreshedAfterScanResults() {
        WifiLinkLayerStats llstats = new WifiLinkLayerStats();
        when(mPrimaryClientModeManager.getWifiLinkLayerStats()).thenReturn(llstats);

        // Force a connectivity scan
        mWifiConnectivityManager.forceConnectivityScan(WIFI_WORK_SOURCE);

        verify(mWifiChannelUtilization).refreshChannelStatsAndChannelUtilization(
                llstats, WifiChannelUtilization.UNKNOWN_FREQ);
    }

    /**
     *  Verify that WifiChannelUtilization is initialized properly
     */
    @Test
    public void verifyWifiChannelUtilizationInitAfterWifiToggle() {
        verify(mWifiChannelUtilization, times(1)).init(null);
        WifiLinkLayerStats llstats = new WifiLinkLayerStats();
        when(mPrimaryClientModeManager.getWifiLinkLayerStats()).thenReturn(llstats);

        setWifiEnabled(false);
        setWifiEnabled(true);
        verify(mWifiChannelUtilization, times(1)).init(llstats);
    }

    /**
     *  Verify that WifiChannelUtilization sets mobility state correctly
     */
    @Test
    public void verifyWifiChannelUtilizationSetMobilityState() {
        WifiLinkLayerStats llstats = new WifiLinkLayerStats();
        when(mPrimaryClientModeManager.getWifiLinkLayerStats()).thenReturn(llstats);

        mWifiConnectivityManager.setDeviceMobilityState(
                WifiManager.DEVICE_MOBILITY_STATE_HIGH_MVMT);
        verify(mWifiChannelUtilization).setDeviceMobilityState(
                WifiManager.DEVICE_MOBILITY_STATE_HIGH_MVMT);
        mWifiConnectivityManager.setDeviceMobilityState(
                WifiManager.DEVICE_MOBILITY_STATE_STATIONARY);
        verify(mWifiChannelUtilization).setDeviceMobilityState(
                WifiManager.DEVICE_MOBILITY_STATE_STATIONARY);
    }

    /**
     *  Verify that WifiChannelUtilization is updated
     */
    @Test
    public void verifyForceConnectivityScan() {
        // Auto-join enabled
        mWifiConnectivityManager.setAutoJoinEnabledExternal(true);
        mWifiConnectivityManager.forceConnectivityScan(WIFI_WORK_SOURCE);
        verify(mWifiScanner).startScan(any(), any(), any(), any());

        // Auto-join disabled, no new scans
        mWifiConnectivityManager.setAutoJoinEnabledExternal(false);
        mWifiConnectivityManager.forceConnectivityScan(WIFI_WORK_SOURCE);
        verify(mWifiScanner, times(1)).startScan(any(), any(), any(), any());

        // Wifi disabled, no new scans
        setWifiEnabled(false);
        mWifiConnectivityManager.forceConnectivityScan(WIFI_WORK_SOURCE);
        verify(mWifiScanner, times(1)).startScan(any(), any(), any(), any());
    }

    @Test
    public void testSetAndClearExternalPnoScanRequest() {
        int testUid = 123;
        String testPackage = "TestPackage";
        IBinder binder = mock(IBinder.class);
        IPnoScanResultsCallback callback = mock(IPnoScanResultsCallback.class);
        List<WifiSsid> requestedSsids = Arrays.asList(
                WifiSsid.fromString("\"TEST_SSID_1\""),
                WifiSsid.fromString("\"TEST_SSID_2\""));
        int[] frequencies = new int[] {TEST_FREQUENCY};
        mWifiConnectivityManager.setExternalPnoScanRequest(testUid, testPackage, binder, callback,
                requestedSsids, frequencies);
        verify(mExternalPnoScanRequestManager).setRequest(testUid, testPackage, binder, callback,
                requestedSsids, frequencies);
        mWifiConnectivityManager.clearExternalPnoScanRequest(testUid);
        verify(mExternalPnoScanRequestManager).removeRequest(testUid);
    }

    /**
     * When location is disabled external PNO SSIDs should not get scanned.
     */
    @Test
    public void testExternalPnoScanRequest_gatedBylocationMode() {
        when(mWifiScoreCard.lookupNetwork(any())).thenReturn(mock(WifiScoreCard.PerNetwork.class));
        mResources.setBoolean(R.bool.config_wifiPnoFrequencyCullingEnabled, true);
        mWifiConnectivityManager.setLocationModeEnabled(false);
        // mock saved networks list to be empty
        when(mWifiConfigManager.getSavedNetworks(anyInt())).thenReturn(Collections.EMPTY_LIST);


        // Mock a couple external requested PNO SSIDs
        Set<String> requestedSsids = new ArraySet<>();
        requestedSsids.add("\"Test_SSID_1\"");
        requestedSsids.add("\"Test_SSID_2\"");
        when(mExternalPnoScanRequestManager.getExternalPnoScanSsids()).thenReturn(requestedSsids);
        Set<Integer> frequencies = new ArraySet<>();
        frequencies.add(TEST_FREQUENCY);
        when(mExternalPnoScanRequestManager.getExternalPnoScanFrequencies())
                .thenReturn(frequencies);

        assertEquals(Collections.EMPTY_LIST, mWifiConnectivityManager.retrievePnoNetworkList());

        // turn location mode on and now PNO scan should include the requested SSIDs
        mWifiConnectivityManager.setLocationModeEnabled(true);
        List<WifiScanner.PnoSettings.PnoNetwork> pnoNetworks =
                mWifiConnectivityManager.retrievePnoNetworkList();
        assertEquals(2, pnoNetworks.size());
        assertEquals("\"Test_SSID_1\"", pnoNetworks.get(0).ssid);
        assertEquals("\"Test_SSID_2\"", pnoNetworks.get(1).ssid);
        assertArrayEquals(new int[] {TEST_FREQUENCY}, pnoNetworks.get(0).frequencies);
        assertArrayEquals(new int[] {TEST_FREQUENCY}, pnoNetworks.get(1).frequencies);
    }

    /**
     * Test external requested PNO SSIDs get handled properly when there are existing saved networks
     * with same SSID.
     */
    @Test
    public void testExternalPnoScanRequest_withSavedNetworks() {
        mWifiConnectivityManager.setLocationModeEnabled(true);
        // Create and add 3 networks.
        WifiConfiguration network1 = WifiConfigurationTestUtil.createPasspointNetwork();
        network1.ephemeral = true;
        network1.getNetworkSelectionStatus().setHasEverConnected(false);
        WifiConfiguration network2 = WifiConfigurationTestUtil.createPskNetwork();
        network2.getNetworkSelectionStatus().setHasEverConnected(true);
        WifiConfiguration network3 = WifiConfigurationTestUtil.createPskNetwork();
        List<WifiConfiguration> networkList = new ArrayList<>();
        networkList.add(network1);
        networkList.add(network2);
        networkList.add(network3);
        mLruConnectionTracker.addNetwork(network3);
        mLruConnectionTracker.addNetwork(network2);
        mLruConnectionTracker.addNetwork(network1);
        when(mWifiConfigManager.getSavedNetworks(anyInt())).thenReturn(networkList);

        // Mock a couple external requested PNO SSIDs. network3.SSID is in both saved networks
        // and external requested networks.
        Set<String> requestedSsids = new ArraySet<>();
        requestedSsids.add("\"Test_SSID_1\"");
        requestedSsids.add(network2.SSID);
        when(mExternalPnoScanRequestManager.getExternalPnoScanSsids()).thenReturn(requestedSsids);

        List<WifiScanner.PnoSettings.PnoNetwork> pnoNetworks =
                mWifiConnectivityManager.retrievePnoNetworkList();
        // There should be 3 SSIDs in total: network1, network2, and Test_SSID_1.
        // network1 should be included in PNO even if it's never connected because it's ephemeral.
        // network3 should not get included because it's saved and never connected before.
        assertEquals(3, pnoNetworks.size());
        // Verify the order. Test_SSID_1 and network2 should be in the front because they are
        // requested by an external app. Verify network2.SSID only appears once.
        assertEquals("\"Test_SSID_1\"", pnoNetworks.get(0).ssid);
        assertEquals(network2.SSID, pnoNetworks.get(1).ssid);
        assertEquals(network1.SSID, pnoNetworks.get(2).ssid);
    }

    @Test
    public void testExternalPnoScanRequest_reportResults() {
        setWifiEnabled(true);
        mWifiConnectivityManager.setLocationModeEnabled(true);

        when(mClock.getElapsedSinceBootMillis()).thenReturn(0L);
        // starts a PNO scan
        mWifiConnectivityManager.handleConnectionStateChanged(
                mPrimaryClientModeManager,
                WifiConnectivityManager.WIFI_STATE_DISCONNECTED);
        mWifiConnectivityManager.setTrustedConnectionAllowed(true);

        InOrder inOrder = inOrder(mWifiScanner, mExternalPnoScanRequestManager);

        inOrder.verify(mWifiScanner).startDisconnectedPnoScan(any(), any(), any(), any());
        inOrder.verify(mExternalPnoScanRequestManager).onPnoNetworkFound(any());
    }

    /**
     * Verify no network is network selection disabled, auto-join disabled using.
     * {@link WifiConnectivityManager#retrievePnoNetworkList()}.
     */
    @Test
    public void testRetrievePnoList() {
        // Create and add 3 networks.
        WifiConfiguration network1 = WifiConfigurationTestUtil.createEapNetwork();
        WifiConfiguration network2 = WifiConfigurationTestUtil.createPskNetwork();
        WifiConfiguration network3 = WifiConfigurationTestUtil.createOpenHiddenNetwork();
        network1.getNetworkSelectionStatus().setHasEverConnected(true);
        network2.getNetworkSelectionStatus().setHasEverConnected(true);
        network3.getNetworkSelectionStatus().setHasEverConnected(true);

        List<WifiConfiguration> networkList = new ArrayList<>();
        networkList.add(network1);
        networkList.add(network2);
        networkList.add(network3);
        mLruConnectionTracker.addNetwork(network3);
        mLruConnectionTracker.addNetwork(network2);
        mLruConnectionTracker.addNetwork(network1);
        when(mWifiConfigManager.getSavedNetworks(anyInt())).thenReturn(networkList);
        // Retrieve the Pno network list & verify.
        List<WifiScanner.PnoSettings.PnoNetwork> pnoNetworks =
                mWifiConnectivityManager.retrievePnoNetworkList();
        verify(mWifiNetworkSuggestionsManager).getAllScanOptimizationSuggestionNetworks();
        assertEquals(3, pnoNetworks.size());
        assertEquals(network1.SSID, pnoNetworks.get(0).ssid);
        assertEquals(network2.SSID, pnoNetworks.get(1).ssid);
        assertEquals(network3.SSID, pnoNetworks.get(2).ssid);

        // Now permanently disable |network3|. This should remove network 3 from the list.
        network3.getNetworkSelectionStatus().setNetworkSelectionStatus(
                WifiConfiguration.NetworkSelectionStatus.NETWORK_SELECTION_TEMPORARY_DISABLED);

        // Retrieve the Pno network list & verify.
        pnoNetworks = mWifiConnectivityManager.retrievePnoNetworkList();
        assertEquals(2, pnoNetworks.size());
        assertEquals(network1.SSID, pnoNetworks.get(0).ssid);
        assertEquals(network2.SSID, pnoNetworks.get(1).ssid);

        // Now set network1 autojoin disabled. This should remove network 1 from the list.
        network1.allowAutojoin = false;
        // Retrieve the Pno network list & verify.
        pnoNetworks = mWifiConnectivityManager.retrievePnoNetworkList();
        assertEquals(1, pnoNetworks.size());
        assertEquals(network2.SSID, pnoNetworks.get(0).ssid);

        // Now set network2 to be temporarily disabled by the user. This should remove network 2
        // from the list.
        when(mWifiConfigManager.isNetworkTemporarilyDisabledByUser(network2.SSID)).thenReturn(true);
        pnoNetworks = mWifiConnectivityManager.retrievePnoNetworkList();
        assertEquals(0, pnoNetworks.size());
    }

    /**
     * Verifies frequencies are populated correctly for pno networks.
     * {@link WifiConnectivityManager#retrievePnoNetworkList()}.
     */
    @Test
    public void testRetrievePnoListFrequencies() {
        // Create 2 networks.
        WifiConfiguration network1 = WifiConfigurationTestUtil.createEapNetwork();
        WifiConfiguration network2 = WifiConfigurationTestUtil.createPskNetwork();
        network1.getNetworkSelectionStatus().setHasEverConnected(true);
        network2.getNetworkSelectionStatus().setHasEverConnected(true);
        List<WifiConfiguration> networkList = new ArrayList<>();
        networkList.add(network1);
        networkList.add(network2);
        mLruConnectionTracker.addNetwork(network2);
        mLruConnectionTracker.addNetwork(network1);
        when(mWifiConfigManager.getSavedNetworks(anyInt())).thenReturn(networkList);
        // Retrieve the Pno network list and verify.
        // Frequencies should be empty since no scan results have been received yet.
        List<WifiScanner.PnoSettings.PnoNetwork> pnoNetworks =
                mWifiConnectivityManager.retrievePnoNetworkList();
        assertEquals(2, pnoNetworks.size());
        assertEquals(network1.SSID, pnoNetworks.get(0).ssid);
        assertEquals(network2.SSID, pnoNetworks.get(1).ssid);
        assertEquals("frequencies should be empty", 0, pnoNetworks.get(0).frequencies.length);
        assertEquals("frequencies should be empty", 0, pnoNetworks.get(1).frequencies.length);

        //Set up wifiScoreCard to get frequency.
        List<Integer> channelList = Arrays
                .asList(TEST_FREQUENCY_1, TEST_FREQUENCY_2, TEST_FREQUENCY_3);
        when(mWifiScoreCard.lookupNetwork(network1.SSID)).thenReturn(mPerNetwork);
        when(mWifiScoreCard.lookupNetwork(network2.SSID)).thenReturn(mPerNetwork1);
        when(mPerNetwork.getFrequencies(anyLong())).thenReturn(channelList);
        when(mPerNetwork1.getFrequencies(anyLong())).thenReturn(new ArrayList<>());

        //Set config_wifiPnoFrequencyCullingEnabled false, should ignore get frequency.
        mResources.setBoolean(R.bool.config_wifiPnoFrequencyCullingEnabled, false);
        pnoNetworks = mWifiConnectivityManager.retrievePnoNetworkList();
        assertEquals(2, pnoNetworks.size());
        assertEquals(network1.SSID, pnoNetworks.get(0).ssid);
        assertEquals(network2.SSID, pnoNetworks.get(1).ssid);
        assertEquals("frequencies should be empty", 0, pnoNetworks.get(0).frequencies.length);
        assertEquals("frequencies should be empty", 0, pnoNetworks.get(1).frequencies.length);

        // Set config_wifiPnoFrequencyCullingEnabled false, should get the right frequency.
        mResources.setBoolean(R.bool.config_wifiPnoFrequencyCullingEnabled, true);
        pnoNetworks = mWifiConnectivityManager.retrievePnoNetworkList();
        assertEquals(2, pnoNetworks.size());
        assertEquals(network1.SSID, pnoNetworks.get(0).ssid);
        assertEquals(network2.SSID, pnoNetworks.get(1).ssid);
        assertEquals(3, pnoNetworks.get(0).frequencies.length);
        Arrays.sort(pnoNetworks.get(0).frequencies);
        assertEquals(TEST_FREQUENCY_1, pnoNetworks.get(0).frequencies[0]);
        assertEquals(TEST_FREQUENCY_2, pnoNetworks.get(0).frequencies[1]);
        assertEquals(TEST_FREQUENCY_3, pnoNetworks.get(0).frequencies[2]);
        assertEquals("frequencies should be empty", 0, pnoNetworks.get(1).frequencies.length);
    }


    /**
     * Verifies the ordering of network list generated using
     * {@link WifiConnectivityManager#retrievePnoNetworkList()}.
     */
    @Test
    public void testRetrievePnoListOrder() {
        //Create 4 networks.
        WifiConfiguration network1 = WifiConfigurationTestUtil.createEapNetwork();
        WifiConfiguration network2 = WifiConfigurationTestUtil.createPskNetwork();
        WifiConfiguration network3 = WifiConfigurationTestUtil.createOpenHiddenNetwork();
        WifiConfiguration network4 = WifiConfigurationTestUtil.createPskNetwork();

        // mark all networks except network4 as connected before
        network1.getNetworkSelectionStatus().setHasEverConnected(true);
        network2.getNetworkSelectionStatus().setHasEverConnected(true);
        network3.getNetworkSelectionStatus().setHasEverConnected(true);

        mLruConnectionTracker.addNetwork(network1);
        mLruConnectionTracker.addNetwork(network2);
        mLruConnectionTracker.addNetwork(network3);
        mLruConnectionTracker.addNetwork(network4);
        List<WifiConfiguration> networkList = new ArrayList<>();
        networkList.add(network1);
        networkList.add(network2);
        networkList.add(network3);
        when(mWifiConfigManager.getSavedNetworks(anyInt())).thenReturn(networkList);
        List<WifiScanner.PnoSettings.PnoNetwork> pnoNetworks =
                mWifiConnectivityManager.retrievePnoNetworkList();

        // Verify correct order of networks. Note that network4 should not appear for PNO scan
        // since it had not been connected before.
        assertEquals(3, pnoNetworks.size());
        assertEquals(network3.SSID, pnoNetworks.get(0).ssid);
        assertEquals(network2.SSID, pnoNetworks.get(1).ssid);
        assertEquals(network1.SSID, pnoNetworks.get(2).ssid);
    }

    private List<List<Integer>> linkScoreCardFreqsToNetwork(WifiConfiguration... configs) {
        List<List<Integer>> results = new ArrayList<>();
        int i = 0;
        for (WifiConfiguration config : configs) {
            List<Integer> channelList = Arrays.asList(TEST_FREQUENCY_1 + i, TEST_FREQUENCY_2 + i,
                    TEST_FREQUENCY_3 + i);
            WifiScoreCard.PerNetwork perNetwork = mock(WifiScoreCard.PerNetwork.class);
            when(mWifiScoreCard.lookupNetwork(config.SSID)).thenReturn(perNetwork);
            when(perNetwork.getFrequencies(anyLong())).thenReturn(channelList);
            results.add(channelList);
            i++;
        }
        return results;
    }

    /**
     * Verify that the length of frequency set will not exceed the provided max value
     */
    @Test
    public void testFetchChannelSetForPartialScanMaxCount() {
        WifiConfiguration configuration1 = WifiConfigurationTestUtil.createOpenNetwork();
        WifiConfiguration configuration2 = WifiConfigurationTestUtil.createOpenNetwork();
        configuration1.getNetworkSelectionStatus().setHasEverConnected(true);
        configuration2.getNetworkSelectionStatus().setHasEverConnected(true);
        when(mWifiConfigManager.getSavedNetworks(anyInt()))
                .thenReturn(Arrays.asList(configuration1, configuration2));

        List<List<Integer>> freqs = linkScoreCardFreqsToNetwork(configuration1, configuration2);

        mLruConnectionTracker.addNetwork(configuration2);
        mLruConnectionTracker.addNetwork(configuration1);

        assertEquals(new HashSet<>(freqs.get(0)), mWifiConnectivityManager
                .fetchChannelSetForPartialScan(3, CHANNEL_CACHE_AGE_MINS));
    }

    /**
     * Verifies the creation of channel list using
     * {@link WifiConnectivityManager#fetchChannelSetForNetworkForPartialScan(int)}.
     */
    @Test
    public void testFetchChannelSetForNetwork() {
        WifiConfiguration configuration = WifiConfigurationTestUtil.createOpenNetwork();
        configuration.networkId = TEST_CONNECTED_NETWORK_ID;
        when(mWifiConfigManager.getConfiguredNetwork(TEST_CONNECTED_NETWORK_ID))
                .thenReturn(configuration);
        List<List<Integer>> freqs = linkScoreCardFreqsToNetwork(configuration);

        assertEquals(new HashSet<>(freqs.get(0)), mWifiConnectivityManager
                .fetchChannelSetForNetworkForPartialScan(configuration.networkId));
    }

    /**
     * Verifies the creation of channel list using
     * {@link WifiConnectivityManager#fetchChannelSetForNetworkForPartialScan(int)} and
     * ensures that the frequenecy of the currently connected network is in the returned
     * channel set.
     */
    @Test
    public void testFetchChannelSetForNetworkIncludeCurrentNetwork() {
        WifiConfiguration configuration = WifiConfigurationTestUtil.createOpenNetwork();
        configuration.networkId = TEST_CONNECTED_NETWORK_ID;
        when(mWifiConfigManager.getConfiguredNetwork(TEST_CONNECTED_NETWORK_ID))
                .thenReturn(configuration);
        linkScoreCardFreqsToNetwork(configuration);

        mWifiInfo.setFrequency(TEST_CURRENT_CONNECTED_FREQUENCY);

        // Currently connected network frequency 2427 is not in the TEST_FREQ_LIST
        Set<Integer> freqs = mWifiConnectivityManager.fetchChannelSetForNetworkForPartialScan(
                configuration.networkId);

        assertTrue(freqs.contains(2427));
    }

    /**
     * Verifies the creation of channel list using
     * {@link WifiConnectivityManager#fetchChannelSetForNetworkForPartialScan(int)} and
     * ensures that the list size does not exceed the max configured for the device.
     */
    @Test
    public void testFetchChannelSetForNetworkIsLimitedToConfiguredSize() {
        // Need to recreate the WifiConfigManager instance for this test to modify the config
        // value which is read only in the constructor.
        int maxListSize = 2;
        mResources.setInteger(
                R.integer.config_wifi_framework_associated_partial_scan_max_num_active_channels,
                maxListSize);

        WifiConfiguration configuration = WifiConfigurationTestUtil.createOpenNetwork();
        configuration.networkId = TEST_CONNECTED_NETWORK_ID;
        when(mWifiConfigManager.getConfiguredNetwork(TEST_CONNECTED_NETWORK_ID))
                .thenReturn(configuration);
        List<List<Integer>> freqs = linkScoreCardFreqsToNetwork(configuration);
        // Ensure that the fetched list size is limited.
        Set<Integer> results = mWifiConnectivityManager.fetchChannelSetForNetworkForPartialScan(
                configuration.networkId);
        assertEquals(maxListSize, results.size());
        assertFalse(results.contains(freqs.get(0).get(2)));
    }

    @Test
    public void restartPnoScanForNetworkChanges() {
        setWifiEnabled(true);

        when(mClock.getElapsedSinceBootMillis()).thenReturn(0L);
        // starts a PNO scan
        mWifiConnectivityManager.handleConnectionStateChanged(
                mPrimaryClientModeManager,
                WifiConnectivityManager.WIFI_STATE_DISCONNECTED);
        mWifiConnectivityManager.setTrustedConnectionAllowed(true);

        InOrder inOrder = inOrder(mWifiScanner);

        inOrder.verify(mWifiScanner).startDisconnectedPnoScan(any(), any(), any(), any());

        // Add or update suggestions.
        mSuggestionUpdateListenerCaptor.getValue().onSuggestionsAddedOrUpdated(
                Arrays.asList(mWifiNetworkSuggestion));
        // Add saved network
        mNetworkUpdateListenerCaptor.getValue().onNetworkAdded(new WifiConfiguration());
        // Ensure that we don't immediately restarted PNO.
        inOrder.verify(mWifiScanner, never()).stopPnoScan(any());
        inOrder.verify(mWifiScanner, never()).startDisconnectedPnoScan(any(), any(), any(), any());

        // Verify there is only 1 delayed scan scheduled
        assertEquals(1, mTestHandler.getIntervals().size());
        assertEquals(NETWORK_CHANGE_TRIGGER_PNO_THROTTLE_MS,
                (long) mTestHandler.getIntervals().get(0));
        when(mClock.getElapsedSinceBootMillis()).thenReturn(mTestHandler.getIntervals().get(0));
        // Now advance the test handler and fire the periodic scan timer
        mTestHandler.timeAdvance();

        // Ensure that we restarted PNO.
        inOrder.verify(mWifiScanner).stopPnoScan(any());
        inOrder.verify(mWifiScanner).startDisconnectedPnoScan(any(), any(), any(), any());
    }

    @Test
    public void includeSecondaryStaWhenPresentInGetCandidatesFromScan() {
        // Set screen to on
        setScreenState(true);

        ConcreteClientModeManager primaryCmm = mock(ConcreteClientModeManager.class);
        WifiInfo wifiInfo1 = mock(WifiInfo.class);
        when(primaryCmm.getInterfaceName()).thenReturn("wlan0");
        when(primaryCmm.getRole()).thenReturn(ROLE_CLIENT_PRIMARY);
        when(primaryCmm.isConnected()).thenReturn(false);
        when(primaryCmm.isDisconnected()).thenReturn(true);
        when(primaryCmm.syncRequestConnectionInfo()).thenReturn(wifiInfo1);

        ConcreteClientModeManager secondaryCmm = mock(ConcreteClientModeManager.class);
        WifiInfo wifiInfo2 = mock(WifiInfo.class);
        when(secondaryCmm.getInterfaceName()).thenReturn("wlan1");
        when(secondaryCmm.getRole()).thenReturn(ROLE_CLIENT_SECONDARY_LONG_LIVED);
        when(secondaryCmm.isConnected()).thenReturn(false);
        when(secondaryCmm.isDisconnected()).thenReturn(true);
        when(secondaryCmm.syncRequestConnectionInfo()).thenReturn(wifiInfo2);

        when(mActiveModeWarden.getInternetConnectivityClientModeManagers())
                .thenReturn(Arrays.asList(primaryCmm, secondaryCmm));

        // Set WiFi to disconnected state to trigger scan
        mWifiConnectivityManager.handleConnectionStateChanged(
                primaryCmm,
                WifiConnectivityManager.WIFI_STATE_DISCONNECTED);

        List<WifiNetworkSelector.ClientModeManagerState> expectedCmmStates =
                Arrays.asList(new WifiNetworkSelector.ClientModeManagerState(
                                "wlan0", false, true, wifiInfo1),
                        new WifiNetworkSelector.ClientModeManagerState(
                                "wlan1", false, true, wifiInfo2));
        verify(mWifiNS).getCandidatesFromScan(any(), any(),
                eq(expectedCmmStates), anyBoolean(), anyBoolean(), anyBoolean(), any(),
                anyBoolean());
    }

    @Test
    public void includeSecondaryStaWhenNotPresentButAvailableInGetCandidatesFromScan() {
        // Set screen to on
        setScreenState(true);
        // set OEM paid connection allowed.
        WorkSource oemPaidWs = new WorkSource();
        mWifiConnectivityManager.setOemPaidConnectionAllowed(true, oemPaidWs);

        ConcreteClientModeManager primaryCmm = mock(ConcreteClientModeManager.class);
        WifiInfo wifiInfo1 = mock(WifiInfo.class);
        when(primaryCmm.getInterfaceName()).thenReturn("wlan0");
        when(primaryCmm.getRole()).thenReturn(ROLE_CLIENT_PRIMARY);
        when(primaryCmm.isConnected()).thenReturn(false);
        when(primaryCmm.isDisconnected()).thenReturn(true);
        when(primaryCmm.syncRequestConnectionInfo()).thenReturn(wifiInfo1);

        when(mActiveModeWarden.getInternetConnectivityClientModeManagers())
                .thenReturn(Arrays.asList(primaryCmm));
        // Second STA creation is allowed.
        when(mActiveModeWarden.canRequestMoreClientModeManagersInRole(
                eq(oemPaidWs), eq(ROLE_CLIENT_SECONDARY_LONG_LIVED), eq(false))).thenReturn(true);

        // Set WiFi to disconnected state to trigger scan
        mWifiConnectivityManager.handleConnectionStateChanged(
                primaryCmm,
                WifiConnectivityManager.WIFI_STATE_DISCONNECTED);

        List<WifiNetworkSelector.ClientModeManagerState> expectedCmmStates =
                Arrays.asList(new WifiNetworkSelector.ClientModeManagerState(
                        "wlan0", false, true, wifiInfo1),
                new WifiNetworkSelector.ClientModeManagerState(
                        "unknown", false, true, new WifiInfo()));
        verify(mWifiNS).getCandidatesFromScan(any(), any(),
                eq(expectedCmmStates), anyBoolean(), anyBoolean(), anyBoolean(), any(),
                anyBoolean());
    }

    private void setWifiEnabled(boolean enable) {
        ActiveModeWarden.ModeChangeCallback modeChangeCallback =
                mModeChangeCallbackCaptor.getValue();
        assertNotNull(modeChangeCallback);
        if (enable) {
            when(mActiveModeWarden.getInternetConnectivityClientModeManagers())
                    .thenReturn(Arrays.asList(mPrimaryClientModeManager));
            modeChangeCallback.onActiveModeManagerAdded(mPrimaryClientModeManager);
        } else {
            when(mActiveModeWarden.getInternetConnectivityClientModeManagers())
                    .thenReturn(Arrays.asList());
            modeChangeCallback.onActiveModeManagerRemoved(mPrimaryClientModeManager);
        }
    }

    private void setScreenState(boolean screenOn) {
        BroadcastReceiver broadcastReceiver = mBroadcastReceiverCaptor.getValue();
        assertNotNull(broadcastReceiver);
        Intent intent = new Intent(screenOn  ? ACTION_SCREEN_ON : ACTION_SCREEN_OFF);
        broadcastReceiver.onReceive(mContext, intent);
    }
}
