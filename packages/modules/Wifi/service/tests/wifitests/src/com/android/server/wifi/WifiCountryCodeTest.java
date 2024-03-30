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

import static com.android.server.wifi.ActiveModeManager.ROLE_CLIENT_PRIMARY;
import static com.android.server.wifi.ActiveModeManager.ROLE_CLIENT_SECONDARY_LONG_LIVED;
import static com.android.server.wifi.WifiSettingsConfigStore.WIFI_DEFAULT_COUNTRY_CODE;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.doAnswer;

import android.app.test.MockAnswerUtil.AnswerWithArguments;
import android.content.Context;
import android.net.wifi.WifiInfo;
import android.telephony.TelephonyManager;

import androidx.test.filters.SmallTest;

import com.android.wifi.resources.R;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Unit tests for {@link com.android.server.wifi.WifiCountryCode}.
 */
@SmallTest
public class WifiCountryCodeTest extends WifiBaseTest {

    private static final String TAG = "WifiCountryCodeTest";
    private static final String TEST_COUNTRY_CODE = "JP";
    private String mDefaultCountryCode = "US";
    private String mTelephonyCountryCode = "JP";
    private String mWorldModeCountryCode = "00";
    private boolean mRevertCountryCodeOnCellularLoss = true;
    // Default assume true since it was a design before R
    private boolean mDriverSupportedNl80211RegChangedEvent = false;
    private boolean mForcedSoftApRestateWhenCountryCodeChanged = false;
    @Mock Context mContext;
    MockResources mResources = new MockResources();
    @Mock TelephonyManager mTelephonyManager;
    @Mock ActiveModeWarden mActiveModeWarden;
    @Mock ConcreteClientModeManager mClientModeManager;
    @Mock SoftApManager mSoftApManager;
    @Mock ClientModeImplMonitor mClientModeImplMonitor;
    @Mock WifiNative mWifiNative;
    @Mock WifiSettingsConfigStore mSettingsConfigStore;
    @Mock WifiInfo mWifiInfo;
    @Mock WifiCountryCode.ChangeListener mExternalChangeListener;
    @Mock SoftApModeConfiguration mSoftApModeConfiguration;
    private WifiCountryCode mWifiCountryCode;
    private List<ClientModeManager> mClientManagerList;

    @Captor
    private ArgumentCaptor<ActiveModeWarden.ModeChangeCallback> mModeChangeCallbackCaptor;
    @Captor
    private ArgumentCaptor<ClientModeImplListener> mClientModeImplListenerCaptor;
    @Captor
    private ArgumentCaptor<WifiCountryCode.ChangeListener> mChangeListenerCaptor;
    @Captor
    private ArgumentCaptor<String> mSetCountryCodeCaptor;

    /**
     * Setup test.
     */
    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        mClientManagerList = new ArrayList<>();
        mClientManagerList.add(mClientModeManager);
        when(mActiveModeWarden.getInternetConnectivityClientModeManagers())
                .thenReturn(mClientManagerList);
        when(mClientModeManager.getRole()).thenReturn(ROLE_CLIENT_PRIMARY);
        when(mClientModeManager.setCountryCode(anyString())).thenReturn(true);
        when(mClientModeManager.isConnected()).thenReturn(true);
        when(mClientModeManager.syncRequestConnectionInfo()).thenReturn(mWifiInfo);
        when(mWifiInfo.getSuccessfulTxPacketsPerSecond()).thenReturn(10.0);
        when(mWifiInfo.getSuccessfulRxPacketsPerSecond()).thenReturn(5.0);
        when(mContext.getSystemService(Context.TELEPHONY_SERVICE))
                .thenReturn(mTelephonyManager);

        doAnswer(new AnswerWithArguments() {
            public void answer(WifiSettingsConfigStore.Key<String> key, Object countryCode) {
                when(mSettingsConfigStore.get(WIFI_DEFAULT_COUNTRY_CODE))
                        .thenReturn((String) countryCode);
            }
        }).when(mSettingsConfigStore).put(eq(WIFI_DEFAULT_COUNTRY_CODE), any(String.class));

        when(mSettingsConfigStore.get(WIFI_DEFAULT_COUNTRY_CODE)).thenReturn(mDefaultCountryCode);

        createWifiCountryCode();
    }

    private void createWifiCountryCode() {
        mResources.setBoolean(R.bool.config_wifi_revert_country_code_on_cellular_loss,
                mRevertCountryCodeOnCellularLoss);
        mResources.setBoolean(R.bool.config_wifiDriverSupportedNl80211RegChangedEvent,
                mDriverSupportedNl80211RegChangedEvent);
        mResources.setBoolean(R.bool.config_wifiForcedSoftApRestartWhenCountryCodeChanged,
                mForcedSoftApRestateWhenCountryCodeChanged);
        mResources.setString(R.string.config_wifiDriverWorldModeCountryCode, mWorldModeCountryCode);
        doAnswer((invocation) -> {
            mChangeListenerCaptor.getValue()
                    .onSetCountryCodeSucceeded(mSetCountryCodeCaptor.getValue());
            if (mDriverSupportedNl80211RegChangedEvent) {
                mChangeListenerCaptor.getValue()
                        .onDriverCountryCodeChanged(mSetCountryCodeCaptor.getValue());
            }
            return true;
        }).when(mClientModeManager).setCountryCode(
                    mSetCountryCodeCaptor.capture());
        when(mContext.getResources()).thenReturn(mResources);
        mWifiCountryCode = new WifiCountryCode(
                mContext,
                mActiveModeWarden,
                mClientModeImplMonitor,
                mWifiNative,
                mSettingsConfigStore);
        verify(mActiveModeWarden, atLeastOnce()).registerModeChangeCallback(
                    mModeChangeCallbackCaptor.capture());
        verify(mClientModeImplMonitor, atLeastOnce()).registerListener(
                mClientModeImplListenerCaptor.capture());
        verify(mWifiNative, atLeastOnce()).registerCountryCodeEventListener(
                mChangeListenerCaptor.capture());
    }

    /**
     * Test if we do not receive country code from Telephony.
     * @throws Exception
     */
    @Test
    public void useDefaultCountryCode() throws Exception {
        // Supplicant started.
        mModeChangeCallbackCaptor.getValue().onActiveModeManagerAdded(mClientModeManager);
        // Wifi get L2 connected.
        mClientModeImplListenerCaptor.getValue().onConnectionStart(mClientModeManager);
        verify(mClientModeManager).setCountryCode(anyString());
        assertEquals(mDefaultCountryCode, mWifiCountryCode.getCurrentDriverCountryCode());
    }

    /**
     * Test that we read the country code from telephony at bootup.
     * @throws Exception
     */
    @Test
    public void useTelephonyCountryCodeOnBootup() throws Exception {
        when(mTelephonyManager.getNetworkCountryIso()).thenReturn(mTelephonyCountryCode);
        // Supplicant started.
        mModeChangeCallbackCaptor.getValue().onActiveModeManagerAdded(mClientModeManager);
        // Wifi get L2 connected.
        mClientModeImplListenerCaptor.getValue().onConnectionStart(mClientModeManager);
        verify(mClientModeManager).setCountryCode(anyString());
        assertEquals(mTelephonyCountryCode, mWifiCountryCode.getCurrentDriverCountryCode());
    }

    /**
     * Test if we receive country code from Telephony before supplicant starts.
     * @throws Exception
     */
    @Test
    public void useTelephonyCountryCodeOnChange() throws Exception {
        mWifiCountryCode.setTelephonyCountryCodeAndUpdate(mTelephonyCountryCode);
        assertEquals(null, mWifiCountryCode.getCurrentDriverCountryCode());
        // Supplicant started.
        mModeChangeCallbackCaptor.getValue().onActiveModeManagerAdded(mClientModeManager);
        // Wifi get L2 connected.
        mClientModeImplListenerCaptor.getValue().onConnectionStart(mClientModeManager);
        verify(mClientModeManager).setCountryCode(anyString());
        assertEquals(mTelephonyCountryCode, mWifiCountryCode.getCurrentDriverCountryCode());
    }

    /**
     * Test if we receive country code from Telephony after supplicant starts.
     * @throws Exception
     */
    @Test
    public void telephonyCountryCodeChangeAfterSupplicantStarts() throws Exception {
        // Start in scan only mode.
        mModeChangeCallbackCaptor.getValue().onActiveModeManagerAdded(mClientModeManager);
        assertEquals(mDefaultCountryCode, mWifiCountryCode.getCurrentDriverCountryCode());

        // Supplicant starts.
        when(mClientModeManager.getRole()).thenReturn(ROLE_CLIENT_PRIMARY);
        mModeChangeCallbackCaptor.getValue().onActiveModeManagerRoleChanged(mClientModeManager);
        assertEquals(mDefaultCountryCode, mWifiCountryCode.getCurrentDriverCountryCode());

        // Telephony country code arrives.
        mWifiCountryCode.setTelephonyCountryCodeAndUpdate(mTelephonyCountryCode);
        // Wifi get L2 connected.
        mClientModeImplListenerCaptor.getValue().onConnectionStart(mClientModeManager);

        verify(mClientModeManager, times(3)).setCountryCode(anyString());
        assertEquals(mTelephonyCountryCode, mWifiCountryCode.getCurrentDriverCountryCode());
    }


    /**
     * Test if we receive country code from Telephony after supplicant stop.
     * @throws Exception
     */
    @Test
    public void telephonyCountryCodeChangeAfterSupplicantStop() throws Exception {
        // Start in scan only mode.
        mModeChangeCallbackCaptor.getValue().onActiveModeManagerAdded(mClientModeManager);
        assertEquals(mDefaultCountryCode, mWifiCountryCode.getCurrentDriverCountryCode());

        // Supplicant starts.
        when(mClientModeManager.getRole()).thenReturn(ROLE_CLIENT_PRIMARY);
        mModeChangeCallbackCaptor.getValue().onActiveModeManagerRoleChanged(mClientModeManager);
        assertEquals(mDefaultCountryCode, mWifiCountryCode.getCurrentDriverCountryCode());

        // Telephony country code arrives.
        mWifiCountryCode.setTelephonyCountryCodeAndUpdate(mTelephonyCountryCode);
        // Wifi get L2 connected.
        mClientModeImplListenerCaptor.getValue().onConnectionStart(mClientModeManager);

        verify(mClientModeManager, times(3)).setCountryCode(anyString());
        assertEquals(mTelephonyCountryCode, mWifiCountryCode.getCurrentDriverCountryCode());

        // Remove mode manager.
        mModeChangeCallbackCaptor.getValue().onActiveModeManagerRemoved(mClientModeManager);

        // Send Telephony country code again - should be ignored.
        mWifiCountryCode.setTelephonyCountryCodeAndUpdate(mTelephonyCountryCode);
        verify(mClientModeManager, times(3)).setCountryCode(anyString());
        assertEquals(mTelephonyCountryCode, mWifiCountryCode.getCountryCode());

        // Now try removing the mode manager again - should not crash.
        mModeChangeCallbackCaptor.getValue().onActiveModeManagerRemoved(mClientModeManager);
    }

    /**
     * Test if we receive country code from Telephony after we get L2 connected.
     * @throws Exception
     */
    @Test
    public void telephonyCountryCodeChangeAfterL2Connected() throws Exception {
        mWifiCountryCode.setDefaultCountryCode("00");
        // Supplicant starts.
        mModeChangeCallbackCaptor.getValue().onActiveModeManagerAdded(mClientModeManager);
        // Wifi get L2 connected.
        mClientModeImplListenerCaptor.getValue().onConnectionStart(mClientModeManager);

        // Wifi traffic is high
        when(mWifiInfo.getSuccessfulTxPacketsPerSecond()).thenReturn(20.0);
        // Telephony country code arrives.
        mWifiCountryCode.setTelephonyCountryCodeAndUpdate(mTelephonyCountryCode);
        // Telephony country code won't be applied at this time.
        assertEquals("00", mWifiCountryCode.getCurrentDriverCountryCode());
        // Wifi is not forced to disconnect
        verify(mClientModeManager, times(0)).disconnect();

        // Wifi traffic is low
        when(mWifiInfo.getSuccessfulTxPacketsPerSecond()).thenReturn(10.0);
        // Telephony country code arrives for multiple times
        for (int i = 0; i < 3; i++) {
            mWifiCountryCode.setTelephonyCountryCodeAndUpdate(mTelephonyCountryCode);
        }
        // Telephony country code still won't be applied.
        assertEquals("00", mWifiCountryCode.getCurrentDriverCountryCode());
        // Wifi is forced to disconnect
        verify(mClientModeManager, times(1)).disconnect();

        mClientModeImplListenerCaptor.getValue().onConnectionEnd(mClientModeManager);
        // Telephony country is applied after supplicant is ready.
        verify(mClientModeManager, times(2)).setCountryCode(anyString());
        assertEquals(mTelephonyCountryCode, mWifiCountryCode.getCurrentDriverCountryCode());
    }

    /**
     * Test if we receive country code from Telephony after we get L2 connected on 2 STA interfaces.
     * @throws Exception
     */
    @Test
    public void telephonyCountryCodeChangeAfterL2ConnectedOnTwoClientModeManager()
            throws Exception {
        // Primary CMM
        mModeChangeCallbackCaptor.getValue().onActiveModeManagerAdded(mClientModeManager);
        // Wifi get L2 connected on the primary.
        mClientModeImplListenerCaptor.getValue().onConnectionStart(mClientModeManager);
        // Telephony country code arrives.
        mWifiCountryCode.setTelephonyCountryCodeAndUpdate(mTelephonyCountryCode);
        // Telephony country code won't be applied at this time.
        assertEquals(mDefaultCountryCode, mWifiCountryCode.getCurrentDriverCountryCode());

        // Create secondary CMM
        ConcreteClientModeManager secondaryClientModeManager =
                mock(ConcreteClientModeManager.class);
        when(mClientModeManager.getRole()).thenReturn(ROLE_CLIENT_SECONDARY_LONG_LIVED);
        mModeChangeCallbackCaptor.getValue().onActiveModeManagerAdded(secondaryClientModeManager);
        // Wifi get L2 connected on the secondary.
        mClientModeImplListenerCaptor.getValue().onConnectionStart(secondaryClientModeManager);

        // Telephony country code still not applied.
        assertEquals(mDefaultCountryCode, mWifiCountryCode.getCurrentDriverCountryCode());

        // Disconnection on primary
        mClientModeImplListenerCaptor.getValue().onConnectionEnd(mClientModeManager);

        // Telephony country code still not applied.
        assertEquals(mDefaultCountryCode, mWifiCountryCode.getCurrentDriverCountryCode());

        // Disconnection on secondary
        mClientModeImplListenerCaptor.getValue().onConnectionEnd(secondaryClientModeManager);

        // Telephony coutry is applied after both of them are disconnected.
        verify(mClientModeManager, times(2)).setCountryCode(anyString());
        assertEquals(mTelephonyCountryCode, mWifiCountryCode.getCurrentDriverCountryCode());
    }

    /**
     * Test if we can reset to the default country code when phone is out of service, when
     * |config_wifi_revert_country_code_on_cellular_loss| is set to true;
     * Telephony service calls |setCountryCode| with an empty string when phone is out of service.
     * In this case we should fall back to the default country code.
     * @throws Exception
     */
    @Test
    public void resetCountryCodeWhenOutOfService() throws Exception {
        assertEquals(mDefaultCountryCode, mWifiCountryCode.getCountryCode());
        mWifiCountryCode.setTelephonyCountryCodeAndUpdate(mTelephonyCountryCode);
        assertEquals(mTelephonyCountryCode, mWifiCountryCode.getCountryCode());
        // Out of service.
        mWifiCountryCode.setTelephonyCountryCodeAndUpdate("");
        assertEquals(mDefaultCountryCode, mWifiCountryCode.getCountryCode());
    }

    /**
     * Test if we can keep using the last known country code when phone is out of service, when
     * |config_wifi_revert_country_code_on_cellular_loss| is set to false;
     * Telephony service calls |setCountryCode| with an empty string when phone is out of service.
     * In this case we should keep using the last known country code.
     * @throws Exception
     */
    @Test
    public void doNotResetCountryCodeWhenOutOfService() throws Exception {
        // Refresh mWifiCountryCode with |config_wifi_revert_country_code_on_cellular_loss|
        // setting to false.
        mRevertCountryCodeOnCellularLoss = false;
        createWifiCountryCode();

        assertEquals(mDefaultCountryCode, mWifiCountryCode.getCountryCode());
        mWifiCountryCode.setTelephonyCountryCodeAndUpdate(mTelephonyCountryCode);
        assertEquals(mTelephonyCountryCode, mWifiCountryCode.getCountryCode());
        // Out of service.
        mWifiCountryCode.setTelephonyCountryCodeAndUpdate("");
        assertEquals(mTelephonyCountryCode, mWifiCountryCode.getCountryCode());
    }

    /**
     * Tests that we always use the US locale for converting the provided country code regardless
     * of the system locale set.
     */
    @Test
    public void useUSLocaleForConversionToUpperCase() {
        String oemCountryCodeLower = "us";
        String oemCountryCodeUpper = "US";
        String telephonyCountryCodeLower = "il";
        String telephonyCountryCodeUpper = "IL";

        mDefaultCountryCode = oemCountryCodeLower;
        createWifiCountryCode();

        // Set the default locale to "tr" (Non US).
        Locale.setDefault(new Locale("tr"));

        // Trigger a country code change using the OEM country code.
        mModeChangeCallbackCaptor.getValue().onActiveModeManagerAdded(mClientModeManager);
        verify(mClientModeManager).setCountryCode(oemCountryCodeUpper);

        // Now trigger a country code change using the telephony country code.
        mWifiCountryCode.setTelephonyCountryCodeAndUpdate(telephonyCountryCodeLower);
        verify(mClientModeManager).setCountryCode(telephonyCountryCodeUpper);
    }
    /**
     * Verifies that dump() does not fail
     */
    @Test
    public void dumpDoesNotFail() {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);

        mRevertCountryCodeOnCellularLoss = false;
        createWifiCountryCode();

        mWifiCountryCode.dump(null, pw, null);
        String dumpCountryCodeStr = sw.toString();

        assertTrue(dumpCountryCodeStr.contains("mTelephonyCountryCode"));
        assertTrue(dumpCountryCodeStr.contains("DefaultCountryCode(system property)"));
        assertTrue(dumpCountryCodeStr.contains("DefaultCountryCode(config store)"));
        assertTrue(dumpCountryCodeStr.contains("mTelephonyCountryTimestamp"));
        assertTrue(dumpCountryCodeStr.contains("mReadyTimestamp"));
        assertTrue(dumpCountryCodeStr.contains("mReady"));
        assertTrue(dumpCountryCodeStr.contains("mDriverCountryCode"));
        assertTrue(dumpCountryCodeStr.contains("mDriverCountryCodeUpdatedTimestamp"));

    }

    /**
     * Test set Default country code
     * @throws Exception
     */
    @Test
    public void setDefaultCountryCode() throws Exception {
        // Supplicant started, it will update default country code (US) to driver
        mModeChangeCallbackCaptor.getValue().onActiveModeManagerAdded(mClientModeManager);
        verify(mClientModeManager).setCountryCode(eq(mDefaultCountryCode));
        // Remove mode manager.
        mModeChangeCallbackCaptor.getValue().onActiveModeManagerRemoved(mClientModeManager);
        // Update default country code (JP) to driver
        mWifiCountryCode.setDefaultCountryCode(TEST_COUNTRY_CODE);
        // Supplicant started again, it will update new default country code (JP) to driver
        mModeChangeCallbackCaptor.getValue().onActiveModeManagerAdded(mClientModeManager);
        verify(mClientModeManager).setCountryCode(eq(TEST_COUNTRY_CODE));
        assertEquals(TEST_COUNTRY_CODE, mWifiCountryCode.getCurrentDriverCountryCode());
        verify(mSettingsConfigStore).put(eq(WIFI_DEFAULT_COUNTRY_CODE), eq(TEST_COUNTRY_CODE));
        assertEquals(TEST_COUNTRY_CODE, mSettingsConfigStore.get(WIFI_DEFAULT_COUNTRY_CODE));
    }

    /**
     * Test set Default country code
     * @throws Exception
     */
    @Test
    public void testDefaultCountryCodeNotUsedWhenDriverCountryCodeExist() throws Exception {
        // Supplicant started, it will update default country code (US) to driver
        mModeChangeCallbackCaptor.getValue().onActiveModeManagerAdded(mClientModeManager);
        // The driver country code is mDefaultCountryCode now.
        verify(mClientModeManager).setCountryCode(eq(mDefaultCountryCode));
        // Update default country code (JP) to driver
        mWifiCountryCode.setDefaultCountryCode(TEST_COUNTRY_CODE);
        // It still use the last driver country code when default country code changed
        verify(mClientModeManager, times(2)).setCountryCode(eq(mDefaultCountryCode));
        assertEquals(mDefaultCountryCode, mWifiCountryCode.getCurrentDriverCountryCode());
        // But default country code updated to new country code - JP
        verify(mSettingsConfigStore).put(eq(WIFI_DEFAULT_COUNTRY_CODE), eq(TEST_COUNTRY_CODE));
        assertEquals(TEST_COUNTRY_CODE, mSettingsConfigStore.get(WIFI_DEFAULT_COUNTRY_CODE));
    }

    /**
     * Test is valid country code
     * @throws Exception
     */
    @Test
    public void testValidCountryCode() throws Exception {
        assertEquals(WifiCountryCode.isValid(null), false);
        assertEquals(WifiCountryCode.isValid("JPUS"), false);
        assertEquals(WifiCountryCode.isValid("JP"), true);
        assertEquals(WifiCountryCode.isValid("00"), true);
        assertEquals(WifiCountryCode.isValid("0U"), true);
    }

    /**
     * Test driver country code is null when there is no active mode.
     */
    @Test
    public void testDriverCountryCodeIsNullWhenNoModeActive() throws Exception {
        // Supplicant started, it will update default country code (US) to driver
        mModeChangeCallbackCaptor.getValue().onActiveModeManagerAdded(mClientModeManager);
        assertEquals(mWifiCountryCode.getCurrentDriverCountryCode(), mDefaultCountryCode);
        // Remove mode manager.
        mModeChangeCallbackCaptor.getValue().onActiveModeManagerRemoved(mClientModeManager);
        assertNull(mWifiCountryCode.getCurrentDriverCountryCode());
    }

    /**
     * Test driver country code updated correctly
     * when config_wifiDriverSupportedNl80211RegChangedEvent is true.
     */
    @Test
    public void testDriverCountryCodeUpdateWhenOverlayisTrue() throws Exception {
        mDriverSupportedNl80211RegChangedEvent = true;
        createWifiCountryCode();
        // Supplicant started.
        mModeChangeCallbackCaptor.getValue().onActiveModeManagerAdded(mClientModeManager);
        // Wifi get L2 connected.
        mClientModeImplListenerCaptor.getValue().onConnectionStart(mClientModeManager);
        verify(mClientModeManager).setCountryCode(anyString());
        assertEquals(mDefaultCountryCode, mWifiCountryCode.getCurrentDriverCountryCode());
        verify(mWifiNative, never()).countryCodeChanged(any());
    }

    @Test
    public void testCountryCodeChangedWillNotifyExternalListener()
            throws Exception {
        // External caller register the listener
        mWifiCountryCode.registerListener(mExternalChangeListener);
        // Supplicant started.
        mModeChangeCallbackCaptor.getValue().onActiveModeManagerAdded(mClientModeManager);
        // Wifi get L2 connected.
        mClientModeImplListenerCaptor.getValue().onConnectionStart(mClientModeManager);
        verify(mClientModeManager).setCountryCode(anyString());
        verify(mWifiNative).countryCodeChanged(mDefaultCountryCode);
        assertEquals(mDefaultCountryCode, mWifiCountryCode.getCurrentDriverCountryCode());
        verify(mExternalChangeListener).onDriverCountryCodeChanged(mDefaultCountryCode);
    }

    @Test
    public void testNotifyExternalListenerWhenOverlayisTrueButCountryCodeSameAsLastActiveOne()
            throws Exception {
        mDriverSupportedNl80211RegChangedEvent = true;
        createWifiCountryCode();
        // External caller register the listener
        mWifiCountryCode.registerListener(mExternalChangeListener);
        // Supplicant started.
        mModeChangeCallbackCaptor.getValue().onActiveModeManagerAdded(mClientModeManager);
        // Wifi get L2 connected.
        mClientModeImplListenerCaptor.getValue().onConnectionStart(mClientModeManager);
        verify(mClientModeManager).setCountryCode(mDefaultCountryCode);
        assertEquals(mDefaultCountryCode, mWifiCountryCode.getCurrentDriverCountryCode());
        verify(mExternalChangeListener).onDriverCountryCodeChanged(mDefaultCountryCode);
        // First time it should not trigger since last active country code is null.
        verify(mWifiNative, never()).countryCodeChanged(any());
        // Remove and add client mode manager again.
        mModeChangeCallbackCaptor.getValue().onActiveModeManagerRemoved(mClientModeManager);
        assertNull(mWifiCountryCode.getCurrentDriverCountryCode());
        mModeChangeCallbackCaptor.getValue().onActiveModeManagerAdded(mClientModeManager);
        verify(mClientModeManager, times(2)).setCountryCode(mDefaultCountryCode);
        // Second time it would notify the wificond since it is same as last active country code
        verify(mWifiNative).countryCodeChanged(mDefaultCountryCode);
        assertEquals(mDefaultCountryCode, mWifiCountryCode.getCurrentDriverCountryCode());
        verify(mExternalChangeListener, times(2)).onDriverCountryCodeChanged(mDefaultCountryCode);
    }

    @Test
    public void testSetTelephonyCountryCodeAndUpdateWithEmptyCCReturnFalseWhenDefaultSIMCCExist()
            throws Exception {
        when(mTelephonyManager.getNetworkCountryIso()).thenReturn(mTelephonyCountryCode);
        assertFalse(mWifiCountryCode.setTelephonyCountryCodeAndUpdate(""));
    }

    @Test
    public void testClientModeManagerAndSoftApManagerDoesntImpactEachOther()
            throws Exception {
        // Supplicant started.
        mModeChangeCallbackCaptor.getValue().onActiveModeManagerAdded(mClientModeManager);
        mChangeListenerCaptor.getValue().onSetCountryCodeSucceeded(mDefaultCountryCode);
        verify(mClientModeManager).setCountryCode(anyString());

        // SoftApManager activated, it shouldn't impact to client mode, the times keep 1.
        when(mSoftApManager.getRole()).thenReturn(ActiveModeManager.ROLE_SOFTAP_TETHERED);
        when(mSoftApManager.updateCountryCode(anyString())).thenReturn(true);
        mModeChangeCallbackCaptor.getValue().onActiveModeManagerAdded(mSoftApManager);
        verify(mClientModeManager).setCountryCode(anyString());
        // Verify the SoftAp enable shouldn't trigger the update CC event.
        verify(mSoftApManager, never()).updateCountryCode(anyString());

        // Remove and add client mode manager again.
        mModeChangeCallbackCaptor.getValue().onActiveModeManagerRemoved(mClientModeManager);
        mModeChangeCallbackCaptor.getValue().onActiveModeManagerAdded(mClientModeManager);
        // Verify the SoftApManager doesn't impact when client mode changed
        verify(mSoftApManager, never()).updateCountryCode(anyString());
        verify(mClientModeManager, times(2)).setCountryCode(anyString());

        // Override the mClientModeManager.setCountryCode mock in setUp, do not update driver
        // country code, so both client mode manager and ap mode manager will update country code.
        doAnswer((invocation) -> {
            return true;
        }).when(mClientModeManager).setCountryCode(mSetCountryCodeCaptor.capture());
        // Test telephony CC changed, check both of client mode and softap mode update the CC.
        mWifiCountryCode.setTelephonyCountryCodeAndUpdate(mTelephonyCountryCode);
        verify(mClientModeManager).setCountryCode(mTelephonyCountryCode);
        verify(mSoftApManager).updateCountryCode(mTelephonyCountryCode);
    }

    @Test
    public void testCountryCodeChangedWhenSoftApManagerActive()
            throws Exception {
        // SoftApManager actived
        mModeChangeCallbackCaptor.getValue().onActiveModeManagerAdded(mSoftApManager);
        // Simulate the country code set succeeded via SoftApManager
        mChangeListenerCaptor.getValue().onSetCountryCodeSucceeded(mDefaultCountryCode);
        verify(mSoftApManager, never()).updateCountryCode(anyString());
        mWifiCountryCode.setTelephonyCountryCodeAndUpdate(mTelephonyCountryCode);
        verify(mSoftApManager).updateCountryCode(mTelephonyCountryCode);
    }

    @Test
    public void testCountryCodeChangedWhenSoftApManagerActiveAndForceSoftApRestartButCCisWorld()
            throws Exception {
        mForcedSoftApRestateWhenCountryCodeChanged = true;
        when(mSoftApManager.getSoftApModeConfiguration()).thenReturn(mSoftApModeConfiguration);
        createWifiCountryCode();
        // SoftApManager actived
        mModeChangeCallbackCaptor.getValue().onActiveModeManagerAdded(mSoftApManager);
        // Simulate the country code set succeeded via SoftApManager
        mChangeListenerCaptor.getValue().onSetCountryCodeSucceeded(
                mWorldModeCountryCode);
        verify(mSoftApManager, never()).updateCountryCode(anyString());
        mWifiCountryCode.setTelephonyCountryCodeAndUpdate(mTelephonyCountryCode);
        verify(mSoftApManager).updateCountryCode(mTelephonyCountryCode);
        verify(mSoftApManager, never()).getSoftApModeConfiguration();
        verify(mActiveModeWarden, never()).stopSoftAp(anyInt());
        verify(mActiveModeWarden, never()).startSoftAp(any(), any());
    }

    @Test
    public void testCountryCodeChangedWhenSoftApManagerActiveAndForceSoftApRestart()
            throws Exception {
        mForcedSoftApRestateWhenCountryCodeChanged = true;
        when(mSoftApManager.getSoftApModeConfiguration()).thenReturn(mSoftApModeConfiguration);
        createWifiCountryCode();
        // SoftApManager actived
        mModeChangeCallbackCaptor.getValue().onActiveModeManagerAdded(mSoftApManager);
        // Simulate the country code set succeeded via SoftApManager
        mChangeListenerCaptor.getValue().onSetCountryCodeSucceeded(mDefaultCountryCode);
        verify(mSoftApManager, never()).updateCountryCode(anyString());
        mWifiCountryCode.setTelephonyCountryCodeAndUpdate(mTelephonyCountryCode);
        verify(mSoftApManager).getSoftApModeConfiguration();
        verify(mActiveModeWarden).stopSoftAp(anyInt());
        verify(mActiveModeWarden).startSoftAp(eq(mSoftApModeConfiguration), any());
    }

    @Test
    public void testSetOverrideCountryCodeAndOnCountryCodeChangePending() {
        // External caller register the listener
        mWifiCountryCode.registerListener(mExternalChangeListener);
        mWifiCountryCode.setOverrideCountryCode(TEST_COUNTRY_CODE);
        verify(mExternalChangeListener).onCountryCodeChangePending(TEST_COUNTRY_CODE);
    }
}
