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

package com.android.server.wifi;

import static android.net.wifi.WifiManager.WIFI_FEATURE_DECORATED_IDENTITY;
import static android.net.wifi.WifiManager.WIFI_FEATURE_DPP;
import static android.net.wifi.WifiManager.WIFI_FEATURE_DPP_ENROLLEE_RESPONDER;
import static android.net.wifi.WifiManager.WIFI_FEATURE_FILS_SHA256;
import static android.net.wifi.WifiManager.WIFI_FEATURE_FILS_SHA384;
import static android.net.wifi.WifiManager.WIFI_FEATURE_MBO;
import static android.net.wifi.WifiManager.WIFI_FEATURE_OCE;
import static android.net.wifi.WifiManager.WIFI_FEATURE_OWE;
import static android.net.wifi.WifiManager.WIFI_FEATURE_PASSPOINT_TERMS_AND_CONDITIONS;
import static android.net.wifi.WifiManager.WIFI_FEATURE_SAE_PK;
import static android.net.wifi.WifiManager.WIFI_FEATURE_TRUST_ON_FIRST_USE;
import static android.net.wifi.WifiManager.WIFI_FEATURE_WAPI;
import static android.net.wifi.WifiManager.WIFI_FEATURE_WFD_R2;
import static android.net.wifi.WifiManager.WIFI_FEATURE_WPA3_SAE;
import static android.net.wifi.WifiManager.WIFI_FEATURE_WPA3_SUITE_B;

import android.annotation.NonNull;
import android.content.Context;
import android.hardware.wifi.V1_6.WifiChannelWidthInMhz;
import android.hardware.wifi.supplicant.BtCoexistenceMode;
import android.hardware.wifi.supplicant.ConnectionCapabilities;
import android.hardware.wifi.supplicant.DebugLevel;
import android.hardware.wifi.supplicant.DppAkm;
import android.hardware.wifi.supplicant.DppCurve;
import android.hardware.wifi.supplicant.DppNetRole;
import android.hardware.wifi.supplicant.DppResponderBootstrapInfo;
import android.hardware.wifi.supplicant.ISupplicant;
import android.hardware.wifi.supplicant.ISupplicantStaIface;
import android.hardware.wifi.supplicant.ISupplicantStaIfaceCallback;
import android.hardware.wifi.supplicant.ISupplicantStaNetwork;
import android.hardware.wifi.supplicant.IfaceInfo;
import android.hardware.wifi.supplicant.IfaceType;
import android.hardware.wifi.supplicant.KeyMgmtMask;
import android.hardware.wifi.supplicant.LegacyMode;
import android.hardware.wifi.supplicant.MloLinksInfo;
import android.hardware.wifi.supplicant.QosPolicyClassifierParams;
import android.hardware.wifi.supplicant.QosPolicyClassifierParamsMask;
import android.hardware.wifi.supplicant.QosPolicyData;
import android.hardware.wifi.supplicant.QosPolicyRequestType;
import android.hardware.wifi.supplicant.QosPolicyStatus;
import android.hardware.wifi.supplicant.QosPolicyStatusCode;
import android.hardware.wifi.supplicant.RxFilterType;
import android.hardware.wifi.supplicant.WifiTechnology;
import android.hardware.wifi.supplicant.WpaDriverCapabilitiesMask;
import android.hardware.wifi.supplicant.WpsConfigMethods;
import android.net.DscpPolicy;
import android.net.MacAddress;
import android.net.NetworkAgent;
import android.net.wifi.ScanResult;
import android.net.wifi.SecurityParams;
import android.net.wifi.WifiAnnotations;
import android.net.wifi.WifiConfiguration;
import android.os.Handler;
import android.os.IBinder;
import android.os.IBinder.DeathRecipient;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.ServiceSpecificException;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;

import com.android.internal.annotations.VisibleForTesting;
import com.android.modules.utils.build.SdkLevel;
import com.android.server.wifi.util.NativeUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * HAL calls to set up/tear down the supplicant daemon and make requests
 * related to station mode. Uses the AIDL supplicant interface.
 * To maintain thread-safety, the locking protocol is that every non-static method (regardless of
 * access level) acquires mLock.
 */
public class SupplicantStaIfaceHalAidlImpl implements ISupplicantStaIfaceHal {
    private static final String TAG = "SupplicantStaIfaceHalAidlImpl";
    @VisibleForTesting
    private static final String HAL_INSTANCE_NAME = ISupplicant.DESCRIPTOR + "/default";

    /**
     * Regex pattern for extracting the wps device type bytes.
     * Matches a strings like the following: "<categ>-<OUI>-<subcateg>";
     */
    private static final Pattern WPS_DEVICE_TYPE_PATTERN =
            Pattern.compile("^(\\d{1,2})-([0-9a-fA-F]{8})-(\\d{1,2})$");

    private static final int MIN_PORT_NUM = 0;
    private static final int MAX_PORT_NUM = 65535;

    private final Object mLock = new Object();
    private boolean mVerboseLoggingEnabled = false;
    private boolean mVerboseHalLoggingEnabled = false;
    private boolean mServiceDeclared = false;

    // Supplicant HAL interface objects
    private ISupplicant mISupplicant = null;
    private Map<String, ISupplicantStaIface> mISupplicantStaIfaces = new HashMap<>();
    private Map<String, ISupplicantStaIfaceCallback>
            mISupplicantStaIfaceCallbacks = new HashMap<>();
    private Map<String, SupplicantStaNetworkHalAidlImpl>
            mCurrentNetworkRemoteHandles = new HashMap<>();
    private Map<String, WifiConfiguration> mCurrentNetworkLocalConfigs = new HashMap<>();
    private Map<String, List<Pair<SupplicantStaNetworkHalAidlImpl, WifiConfiguration>>>
            mLinkedNetworkLocalAndRemoteConfigs = new HashMap<>();
    @VisibleForTesting
    PmkCacheManager mPmkCacheManager;
    private WifiNative.SupplicantDeathEventHandler mDeathEventHandler;
    private SupplicantDeathRecipient mSupplicantDeathRecipient;
    private final Context mContext;
    private final WifiMonitor mWifiMonitor;
    private final Handler mEventHandler;
    private WifiNative.DppEventCallback mDppCallback = null;
    private final Clock mClock;
    private final WifiMetrics mWifiMetrics;
    private final WifiGlobals mWifiGlobals;

    private class SupplicantDeathRecipient implements DeathRecipient {
        @Override
        public void binderDied() {
            mEventHandler.post(() -> {
                synchronized (mLock) {
                    Log.w(TAG, "ISupplicant binder died.");
                    supplicantServiceDiedHandler();
                }
            });
        }
    }

    /**
     * Linked to supplicant service death on call to terminate()
     */
    private class TerminateDeathRecipient implements DeathRecipient {
        @Override
        public void binderDied() {
            mEventHandler.post(() -> {
                synchronized (mLock) {
                    Log.w(TAG, "ISupplicant was killed by terminate()");
                    // nothing more to be done here
                }
            });
        }
    }

    public SupplicantStaIfaceHalAidlImpl(Context context, WifiMonitor monitor, Handler handler,
            Clock clock, WifiMetrics wifiMetrics, WifiGlobals wifiGlobals) {
        mContext = context;
        mWifiMonitor = monitor;
        mEventHandler = handler;
        mClock = clock;
        mWifiMetrics = wifiMetrics;
        mWifiGlobals = wifiGlobals;
        mSupplicantDeathRecipient = new SupplicantDeathRecipient();
        mPmkCacheManager = new PmkCacheManager(mClock, mEventHandler);
    }

    /**
     * Enable/Disable verbose logging.
     *
     * @param verboseEnabled Verbose flag set in overlay XML.
     * @param halVerboseEnabled Verbose flag set by the user.
     */
    public void enableVerboseLogging(boolean verboseEnabled, boolean halVerboseEnabled) {
        synchronized (mLock) {
            mVerboseLoggingEnabled = verboseEnabled;
            mVerboseHalLoggingEnabled = halVerboseEnabled;
            setLogLevel(mVerboseHalLoggingEnabled);
        }
    }

    protected boolean isVerboseLoggingEnabled() {
        synchronized (mLock) {
            return mVerboseLoggingEnabled;
        }
    }

    /**
     * Checks whether the ISupplicant service is declared, and therefore should be available.
     *
     * @return true if the ISupplicant service is declared
     */
    public boolean initialize() {
        synchronized (mLock) {
            if (mISupplicant != null) {
                Log.i(TAG, "Service is already initialized, skipping initialize method");
                return true;
            }
            if (mVerboseLoggingEnabled) {
                Log.i(TAG, "Checking for ISupplicant service.");
            }
            mISupplicantStaIfaces.clear();
            mServiceDeclared = serviceDeclared();
            return mServiceDeclared;
        }
    }

    protected int getCurrentNetworkId(@NonNull String ifaceName) {
        synchronized (mLock) {
            WifiConfiguration currentConfig = getCurrentNetworkLocalConfig(ifaceName);
            if (currentConfig == null) {
                return WifiConfiguration.INVALID_NETWORK_ID;
            }
            return currentConfig.networkId;
        }
    }

    /**
     * Setup a STA interface for the specified iface name.
     *
     * @param ifaceName Name of the interface.
     * @return true on success, false otherwise.
     */
    public boolean setupIface(@NonNull String ifaceName) {
        synchronized (mLock) {
            if (getStaIface(ifaceName) != null) {
                Log.e(TAG, "Iface " + ifaceName + " already exists.");
                return false;
            }

            ISupplicantStaIface iface = addIface(ifaceName);
            if (iface == null) {
                Log.e(TAG, "Unable to add iface " + ifaceName);
                return false;
            }

            ISupplicantStaIfaceCallback callback = new SupplicantStaIfaceCallbackAidlImpl(
                    SupplicantStaIfaceHalAidlImpl.this, ifaceName,
                    new Object(), mContext, mWifiMonitor);
            if (registerCallback(iface, callback)) {
                mISupplicantStaIfaces.put(ifaceName, iface);
                // Keep callback in a store to avoid recycling by garbage collector
                mISupplicantStaIfaceCallbacks.put(ifaceName, callback);
                return true;
            } else {
                Log.e(TAG, "Unable to register callback for iface " + ifaceName);
                return false;
            }
        }
    }

    /**
     * Create a STA interface for the specified iface name.
     *
     * @param ifaceName Name of the interface.
     * @return ISupplicantStaIface object on success, null otherwise.
     */
    private ISupplicantStaIface addIface(@NonNull String ifaceName) {
        synchronized (mLock) {
            String methodStr = "addIface";
            if (!checkSupplicantAndLogFailure(methodStr)) {
                return null;
            }
            try {
                return mISupplicant.addStaInterface(ifaceName);
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            } catch (NoSuchElementException | IllegalArgumentException e) {
                Log.e(TAG, "Encountered exception at addIface: ", e);
            }
            return null;
        }
    }

    /**
     * Teardown a STA interface for the specified iface name.
     *
     * @param ifaceName Name of the interface.
     * @return true on success, false otherwise.
     */
    public boolean teardownIface(@NonNull String ifaceName) {
        synchronized (mLock) {
            final String methodStr = "teardownIface";
            if (checkStaIfaceAndLogFailure(ifaceName, methodStr) == null) {
                return false;
            }
            if (!checkSupplicantAndLogFailure(methodStr)) {
                return false;
            }

            try {
                IfaceInfo ifaceInfo = new IfaceInfo();
                ifaceInfo.name = ifaceName;
                ifaceInfo.type = IfaceType.STA;
                mISupplicant.removeInterface(ifaceInfo);
                mISupplicantStaIfaces.remove(ifaceName);
                mISupplicantStaIfaceCallbacks.remove(ifaceName);
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            } catch (NoSuchElementException e) {
                Log.e(TAG, "Encountered exception at teardownIface: ", e);
            }
            return false;
        }
    }

    /**
     * Registers a death notification for supplicant.
     * @return Returns true on success.
     */
    public boolean registerDeathHandler(@NonNull WifiNative.SupplicantDeathEventHandler handler) {
        synchronized (mLock) {
            if (mDeathEventHandler != null) {
                Log.e(TAG, "Death handler already present");
            }
            mDeathEventHandler = handler;
            return true;
        }
    }

    /**
     * Deregisters a death notification for supplicant.
     * @return Returns true on success.
     */
    public boolean deregisterDeathHandler() {
        synchronized (mLock) {
            if (mDeathEventHandler == null) {
                Log.e(TAG, "No Death handler present");
            }
            mDeathEventHandler = null;
            return true;
        }
    }

    /**
     * Signals whether initialization started successfully.
     */
    public boolean isInitializationStarted() {
        synchronized (mLock) {
            return mServiceDeclared;
        }
    }

    /**
     * Signals whether initialization completed successfully.
     */
    public boolean isInitializationComplete() {
        synchronized (mLock) {
            return mISupplicant != null;
        }
    }

    /**
     * Indicates whether the AIDL service is declared
     */
    public static boolean serviceDeclared() {
        // Service Manager API ServiceManager#isDeclared supported after T.
        if (!SdkLevel.isAtLeastT()) {
            return false;
        }
        return ServiceManager.isDeclared(HAL_INSTANCE_NAME);
    }

    private void clearState() {
        synchronized (mLock) {
            mISupplicant = null;
            mISupplicantStaIfaces.clear();
            mCurrentNetworkLocalConfigs.clear();
            mCurrentNetworkRemoteHandles.clear();
            mLinkedNetworkLocalAndRemoteConfigs.clear();
        }
    }

    private void supplicantServiceDiedHandler() {
        synchronized (mLock) {
            clearState();
            if (mDeathEventHandler != null) {
                mDeathEventHandler.onDeath();
            }
        }
    }

    /**
     * Start the supplicant daemon.
     *
     * @return true on success, false otherwise.
     */
    public boolean startDaemon() {
        final String methodStr = "startDaemon";
        if (mISupplicant != null) {
            Log.i(TAG, "Service is already initialized, skipping " + methodStr);
            return true;
        }

        mISupplicant = getSupplicantMockable();
        if (mISupplicant == null) {
            Log.e(TAG, "Unable to obtain ISupplicant binder.");
            return false;
        }
        Log.i(TAG, "Obtained ISupplicant binder.");

        try {
            IBinder serviceBinder = getServiceBinderMockable();
            if (serviceBinder == null) {
                return false;
            }
            serviceBinder.linkToDeath(mSupplicantDeathRecipient, /* flags= */  0);
            return true;
        } catch (RemoteException e) {
            handleRemoteException(e, methodStr);
            return false;
        }
    }

    /**
     * Terminate the supplicant daemon & wait for its death.
     */
    public void terminate() {
        synchronized (mLock) {
            final String methodStr = "terminate";
            if (!checkSupplicantAndLogFailure(methodStr)) {
                return;
            }
            try {
                // Register a new death listener to confirm that terminate() killed supplicant
                IBinder serviceBinder = getServiceBinderMockable();
                if (serviceBinder == null) {
                    return;
                }
                serviceBinder.linkToDeath(new TerminateDeathRecipient(), /* flags= */ 0);
            } catch (RemoteException e) {
                Log.e(TAG, "Unable to register death recipient.");
                handleRemoteException(e, methodStr);
                return;
            }

            try {
                mISupplicant.terminate();
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            }
        }
    }

    /**
     * Wrapper functions to access HAL objects, created to be mockable in unit tests
     */
    @VisibleForTesting
    protected ISupplicant getSupplicantMockable() {
        synchronized (mLock) {
            try {
                return ISupplicant.Stub.asInterface(
                        ServiceManager.waitForDeclaredService(HAL_INSTANCE_NAME));
            } catch (Exception e) {
                Log.e(TAG, "Unable to get ISupplicant service, " + e);
                return null;
            }
        }
    }

    @VisibleForTesting
    protected IBinder getServiceBinderMockable() {
        synchronized (mLock) {
            if (mISupplicant == null) {
                return null;
            }
            return mISupplicant.asBinder();
        }
    }

    /**
     * Helper method to look up the specified iface.
     */
    private ISupplicantStaIface getStaIface(@NonNull String ifaceName) {
        synchronized (mLock) {
            return mISupplicantStaIfaces.get(ifaceName);
        }
    }

    /**
     * Helper method to look up the network object for the specified iface.
     */
    private SupplicantStaNetworkHalAidlImpl getCurrentNetworkRemoteHandle(
            @NonNull String ifaceName) {
        synchronized (mLock) {
            return mCurrentNetworkRemoteHandles.get(ifaceName);
        }
    }

    /**
     * Helper method to look up the network config for the specified iface.
     */
    protected WifiConfiguration getCurrentNetworkLocalConfig(@NonNull String ifaceName) {
        synchronized (mLock) {
            return mCurrentNetworkLocalConfigs.get(ifaceName);
        }
    }

    /**
     * Add a network configuration to wpa_supplicant.
     *
     * @param config Config corresponding to the network.
     * @return a Pair object including SupplicantStaNetworkHal and WifiConfiguration objects
     * for the current network.
     */
    private Pair<SupplicantStaNetworkHalAidlImpl, WifiConfiguration>
            addNetworkAndSaveConfig(@NonNull String ifaceName, WifiConfiguration config) {
        synchronized (mLock) {
            if (config == null) {
                Log.e(TAG, "Cannot add null network.");
                return null;
            }
            SupplicantStaNetworkHalAidlImpl network = addNetwork(ifaceName);
            if (network == null) {
                Log.e(TAG, "Failed to add network.");
                return null;
            }
            boolean saveSuccess = false;
            try {
                saveSuccess = network.saveWifiConfiguration(config);
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Exception while saving config params: " + config, e);
            }
            if (!saveSuccess) {
                Log.e(TAG, "Failed to save variables for: " + config.getProfileKey());
                if (!removeAllNetworks(ifaceName)) {
                    Log.e(TAG, "Failed to remove all networks on failure.");
                }
                return null;
            }
            return new Pair(network, new WifiConfiguration(config));
        }
    }

    /**
     * Add the provided network configuration to wpa_supplicant and initiate connection to it.
     * This method does the following:
     * 1. If |config| is different to the current supplicant network, removes all supplicant
     * networks and saves |config|.
     * 2. Select the new network in wpa_supplicant.
     *
     * @param ifaceName Name of the interface.
     * @param config WifiConfiguration parameters for the provided network.
     * @return true if it succeeds, false otherwise
     */
    public boolean connectToNetwork(@NonNull String ifaceName, @NonNull WifiConfiguration config) {
        synchronized (mLock) {
            Log.d(TAG, "connectToNetwork " + config.getProfileKey());
            WifiConfiguration currentConfig = getCurrentNetworkLocalConfig(ifaceName);
            if (WifiConfigurationUtil.isSameNetwork(config, currentConfig)) {
                String networkSelectionBSSID = config.getNetworkSelectionStatus()
                        .getNetworkSelectionBSSID();
                String networkSelectionBSSIDCurrent = currentConfig.getNetworkSelectionStatus()
                        .getNetworkSelectionBSSID();
                if (Objects.equals(networkSelectionBSSID, networkSelectionBSSIDCurrent)) {
                    Log.d(TAG, "Network is already saved, will not trigger remove and add.");
                } else {
                    Log.d(TAG, "Network is already saved, but need to update BSSID.");
                    if (!setCurrentNetworkBssid(
                            ifaceName,
                            config.getNetworkSelectionStatus().getNetworkSelectionBSSID())) {
                        Log.e(TAG, "Failed to set current network BSSID.");
                        return false;
                    }
                    mCurrentNetworkLocalConfigs.put(ifaceName, new WifiConfiguration(config));
                }
            } else {
                mCurrentNetworkRemoteHandles.remove(ifaceName);
                mCurrentNetworkLocalConfigs.remove(ifaceName);
                mLinkedNetworkLocalAndRemoteConfigs.remove(ifaceName);
                if (!removeAllNetworks(ifaceName)) {
                    Log.e(TAG, "Failed to remove existing networks");
                    return false;
                }
                Pair<SupplicantStaNetworkHalAidlImpl, WifiConfiguration> pair =
                        addNetworkAndSaveConfig(ifaceName, config);
                if (pair == null) {
                    Log.e(TAG, "Failed to add/save network configuration: " + config
                            .getProfileKey());
                    return false;
                }
                mCurrentNetworkRemoteHandles.put(ifaceName, pair.first);
                mCurrentNetworkLocalConfigs.put(ifaceName, pair.second);
            }

            SupplicantStaNetworkHalAidlImpl networkHandle =
                    checkStaNetworkAndLogFailure(ifaceName, "connectToNetwork");
            if (networkHandle == null) {
                Log.e(TAG, "No valid remote network handle for network configuration: "
                        + config.getProfileKey());
                return false;
            }

            SecurityParams params = config.getNetworkSelectionStatus()
                    .getCandidateSecurityParams();
            if (params != null && !(params.isSecurityType(WifiConfiguration.SECURITY_TYPE_PSK)
                    || params.isSecurityType(WifiConfiguration.SECURITY_TYPE_DPP))) {
                List<ArrayList<Byte>> pmkDataList = mPmkCacheManager.get(config.networkId);
                if (pmkDataList != null) {
                    Log.i(TAG, "Set PMK cache for config id " + config.networkId);
                    pmkDataList.forEach(pmkData -> {
                        if (networkHandle.setPmkCache(NativeUtil.byteArrayFromArrayList(pmkData))) {
                            mWifiMetrics.setConnectionPmkCache(ifaceName, true);
                        }
                    });
                }
            }

            if (!networkHandle.select()) {
                Log.e(TAG, "Failed to select network configuration: " + config.getProfileKey());
                return false;
            }
            return true;
        }
    }

    /**
     * Initiates roaming to the already configured network in wpa_supplicant. If the network
     * configuration provided does not match the already configured network, then this triggers
     * a new connection attempt (instead of roam).
     * 1. First check if we're attempting to connect to a linked network, and select the existing
     *    supplicant network if there is one.
     * 2. Set the new bssid for the network in wpa_supplicant.
     * 3. Trigger reassociate command to wpa_supplicant.
     *
     * @param ifaceName Name of the interface.
     * @param config WifiConfiguration parameters for the provided network.
     * @return {@code true} if it succeeds, {@code false} otherwise
     */
    public boolean roamToNetwork(@NonNull String ifaceName, WifiConfiguration config) {
        synchronized (mLock) {
            if (updateOnLinkedNetworkRoaming(ifaceName, config.networkId, true)) {
                SupplicantStaNetworkHalAidlImpl networkHandle =
                        getCurrentNetworkRemoteHandle(ifaceName);
                if (networkHandle == null) {
                    Log.e(TAG, "Roaming config matches a linked config, "
                            + "but a linked network handle was not found.");
                    return false;
                }
                return networkHandle.select();
            }
            if (getCurrentNetworkId(ifaceName) != config.networkId) {
                Log.w(TAG, "Cannot roam to a different network, initiate new connection. "
                        + "Current network ID: " + getCurrentNetworkId(ifaceName));
                return connectToNetwork(ifaceName, config);
            }
            String bssid = config.getNetworkSelectionStatus().getNetworkSelectionBSSID();
            Log.d(TAG, "roamToNetwork" + config.getProfileKey() + " (bssid " + bssid + ")");

            SupplicantStaNetworkHalAidlImpl networkHandle =
                    checkStaNetworkAndLogFailure(ifaceName, "roamToNetwork");
            if (networkHandle == null || !networkHandle.setBssid(bssid)) {
                Log.e(TAG, "Failed to set new bssid on network: " + config.getProfileKey());
                return false;
            }
            if (!reassociate(ifaceName)) {
                Log.e(TAG, "Failed to trigger reassociate");
                return false;
            }
            return true;
        }
    }

    /**
     * Clean HAL cached data for |networkId| in the framework.
     *
     * @param networkId network id of the network to be removed from supplicant.
     */
    public void removeNetworkCachedData(int networkId) {
        synchronized (mLock) {
            Log.d(TAG, "Remove cached HAL data for config id " + networkId);
            removePmkCacheEntry(networkId);
        }
    }


    /**
     * Clear HAL cached data if MAC address is changed.
     *
     * @param networkId network id of the network to be checked.
     * @param curMacAddress current MAC address
     */
    public void removeNetworkCachedDataIfNeeded(int networkId, MacAddress curMacAddress) {
        synchronized (mLock) {
            mPmkCacheManager.remove(networkId, curMacAddress);
        }
    }

    /**
     * Remove all networks from supplicant
     *
     * @param ifaceName Name of the interface.
     */
    public boolean removeAllNetworks(@NonNull String ifaceName) {
        synchronized (mLock) {
            int[] networks = listNetworks(ifaceName);
            if (networks == null) {
                Log.e(TAG, "removeAllNetworks failed, got null networks");
                return false;
            }
            for (int id : networks) {
                if (!removeNetwork(ifaceName, id)) {
                    Log.e(TAG, "removeAllNetworks failed to remove network: " + id);
                    return false;
                }
            }
            // Reset current network info.
            mCurrentNetworkRemoteHandles.remove(ifaceName);
            mCurrentNetworkLocalConfigs.remove(ifaceName);
            mLinkedNetworkLocalAndRemoteConfigs.remove(ifaceName);
            return true;
        }
    }

    /**
     * Disable the current network in supplicant
     *
     * @param ifaceName Name of the interface.
     */
    public boolean disableCurrentNetwork(@NonNull String ifaceName) {
        synchronized (mLock) {
            SupplicantStaNetworkHalAidlImpl networkHandle =
                    checkStaNetworkAndLogFailure(ifaceName, "disableCurrentNetwork");
            if (networkHandle == null) {
                return false;
            }
            return networkHandle.disable();
        }
    }

    /**
     * Set the currently configured network's bssid.
     *
     * @param ifaceName Name of the interface.
     * @param bssidStr Bssid to set in the form of "XX:XX:XX:XX:XX:XX"
     * @return true if succeeds, false otherwise.
     */
    public boolean setCurrentNetworkBssid(@NonNull String ifaceName, String bssidStr) {
        synchronized (mLock) {
            SupplicantStaNetworkHalAidlImpl networkHandle =
                    checkStaNetworkAndLogFailure(ifaceName, "setCurrentNetworkBssid");
            if (networkHandle == null) {
                return false;
            }
            return networkHandle.setBssid(bssidStr);
        }
    }

    /**
     * Get the currently configured network's WPS NFC token.
     *
     * @param ifaceName Name of the interface.
     * @return Hex string corresponding to the WPS NFC token.
     */
    public String getCurrentNetworkWpsNfcConfigurationToken(@NonNull String ifaceName) {
        synchronized (mLock) {
            SupplicantStaNetworkHalAidlImpl networkHandle =
                    checkStaNetworkAndLogFailure(
                            ifaceName, "getCurrentNetworkWpsNfcConfigurationToken");
            if (networkHandle == null) {
                return null;
            }
            return networkHandle.getWpsNfcConfigurationToken();
        }
    }

    /**
     * Get the eap anonymous identity for the currently configured network.
     *
     * @param ifaceName Name of the interface.
     * @return anonymous identity string if succeeds, null otherwise.
     */
    public String getCurrentNetworkEapAnonymousIdentity(@NonNull String ifaceName) {
        synchronized (mLock) {
            SupplicantStaNetworkHalAidlImpl networkHandle =
                    checkStaNetworkAndLogFailure(
                            ifaceName, "getCurrentNetworkEapAnonymousIdentity");
            if (networkHandle == null) {
                return null;
            }
            return networkHandle.fetchEapAnonymousIdentity();
        }
    }

    /**
     * Send the eap identity response for the currently configured network.
     *
     * @param ifaceName Name of the interface.
     * @param identity identity used for EAP-Identity
     * @param encryptedIdentity encrypted identity used for EAP-AKA/EAP-SIM
     * @return true if succeeds, false otherwise.
     */
    public boolean sendCurrentNetworkEapIdentityResponse(
            @NonNull String ifaceName, @NonNull String identity, String encryptedIdentity) {
        synchronized (mLock) {
            SupplicantStaNetworkHalAidlImpl networkHandle =
                    checkStaNetworkAndLogFailure(
                            ifaceName, "sendCurrentNetworkEapIdentityResponse");
            if (networkHandle == null) {
                return false;
            }
            return networkHandle.sendNetworkEapIdentityResponse(identity, encryptedIdentity);
        }
    }

    /**
     * Send the eap sim gsm auth response for the currently configured network.
     *
     * @param ifaceName Name of the interface.
     * @param paramsStr String to send.
     * @return true if succeeds, false otherwise.
     */
    public boolean sendCurrentNetworkEapSimGsmAuthResponse(
            @NonNull String ifaceName, String paramsStr) {
        synchronized (mLock) {
            SupplicantStaNetworkHalAidlImpl networkHandle =
                    checkStaNetworkAndLogFailure(
                            ifaceName, "sendCurrentNetworkEapSimGsmAuthResponse");
            if (networkHandle == null) {
                return false;
            }
            return networkHandle.sendNetworkEapSimGsmAuthResponse(paramsStr);
        }
    }

    /**
     * Send the eap sim gsm auth failure for the currently configured network.
     *
     * @param ifaceName Name of the interface.
     * @return true if succeeds, false otherwise.
     */
    public boolean sendCurrentNetworkEapSimGsmAuthFailure(@NonNull String ifaceName) {
        synchronized (mLock) {
            SupplicantStaNetworkHalAidlImpl networkHandle =
                    checkStaNetworkAndLogFailure(
                            ifaceName, "sendCurrentNetworkEapSimGsmAuthFailure");
            if (networkHandle == null) {
                return false;
            }
            return networkHandle.sendNetworkEapSimGsmAuthFailure();
        }
    }

    /**
     * Send the eap sim umts auth response for the currently configured network.
     *
     * @param ifaceName Name of the interface.
     * @param paramsStr String to send.
     * @return true if succeeds, false otherwise.
     */
    public boolean sendCurrentNetworkEapSimUmtsAuthResponse(
            @NonNull String ifaceName, String paramsStr) {
        synchronized (mLock) {
            SupplicantStaNetworkHalAidlImpl networkHandle =
                    checkStaNetworkAndLogFailure(
                            ifaceName, "sendCurrentNetworkEapSimUmtsAuthResponse");
            if (networkHandle == null) {
                return false;
            }
            return networkHandle.sendNetworkEapSimUmtsAuthResponse(paramsStr);
        }
    }

    /**
     * Send the eap sim umts auts response for the currently configured network.
     *
     * @param ifaceName Name of the interface.
     * @param paramsStr String to send.
     * @return true if succeeds, false otherwise.
     */
    public boolean sendCurrentNetworkEapSimUmtsAutsResponse(
            @NonNull String ifaceName, String paramsStr) {
        synchronized (mLock) {
            SupplicantStaNetworkHalAidlImpl networkHandle =
                    checkStaNetworkAndLogFailure(
                            ifaceName, "sendCurrentNetworkEapSimUmtsAutsResponse");
            if (networkHandle == null) {
                return false;
            }
            return networkHandle.sendNetworkEapSimUmtsAutsResponse(paramsStr);
        }
    }

    /**
     * Send the eap sim umts auth failure for the currently configured network.
     *
     * @param ifaceName Name of the interface.
     * @return true if succeeds, false otherwise.
     */
    public boolean sendCurrentNetworkEapSimUmtsAuthFailure(@NonNull String ifaceName) {
        synchronized (mLock) {
            SupplicantStaNetworkHalAidlImpl networkHandle =
                    checkStaNetworkAndLogFailure(
                            ifaceName, "sendCurrentNetworkEapSimUmtsAuthFailure");
            if (networkHandle == null) {
                return false;
            }
            return networkHandle.sendNetworkEapSimUmtsAuthFailure();
        }
    }

    /**
     * Adds a new network.
     *
     * @return SupplicantStaNetworkHalAidlImpl object for the new network, or null if the call fails
     */
    private SupplicantStaNetworkHalAidlImpl addNetwork(@NonNull String ifaceName) {
        synchronized (mLock) {
            final String methodStr = "addNetwork";
            ISupplicantStaIface iface = checkStaIfaceAndLogFailure(ifaceName, methodStr);
            if (iface == null) {
                return null;
            }
            try {
                ISupplicantStaNetwork network = iface.addNetwork();
                // Get framework wrapper around the AIDL network object
                return getStaNetworkHalMockable(ifaceName, network);
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return null;
        }
    }

    /**
     * Remove network with specified network Id from supplicant.
     *
     * @return true if request is sent successfully, false otherwise.
     */
    private boolean removeNetwork(@NonNull String ifaceName, int id) {
        synchronized (mLock) {
            final String methodStr = "removeNetwork";
            ISupplicantStaIface iface = checkStaIfaceAndLogFailure(ifaceName, methodStr);
            if (iface == null) {
                return false;
            }
            try {
                iface.removeNetwork(id);
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    /**
     * Creates a SupplicantStaNetworkHal wrapper around an AIDL ISupplicantStaNetwork object.
     * Declared mockable for use in unit tests.
     *
     * @param ifaceName Name of the interface.
     * @param network ISupplicantStaNetwork instance retrieved from AIDL.
     * @return SupplicantStaNetworkHal object for the given network, or null if
     * the call fails
     */
    protected SupplicantStaNetworkHalAidlImpl getStaNetworkHalMockable(
            @NonNull String ifaceName, ISupplicantStaNetwork network) {
        synchronized (mLock) {
            SupplicantStaNetworkHalAidlImpl networkWrapper =
                    new SupplicantStaNetworkHalAidlImpl(network, ifaceName, mContext,
                            mWifiMonitor, mWifiGlobals, getAdvancedCapabilities(ifaceName));
            if (networkWrapper != null) {
                networkWrapper.enableVerboseLogging(
                        mVerboseLoggingEnabled, mVerboseHalLoggingEnabled);
            }
            return networkWrapper;
        }
    }

    private boolean registerCallback(
            ISupplicantStaIface iface, ISupplicantStaIfaceCallback callback) {
        synchronized (mLock) {
            String methodStr = "registerCallback";
            if (iface == null) {
                return false;
            }
            try {
                iface.registerCallback(callback);
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    /**
     * Get list of id's of all networks controlled by supplicant.
     *
     * @return list of network id's, null if failed
     */
    private int[] listNetworks(@NonNull String ifaceName) {
        synchronized (mLock) {
            final String methodStr = "listNetworks";
            ISupplicantStaIface iface = checkStaIfaceAndLogFailure(ifaceName, methodStr);
            if (iface == null) {
                return null;
            }
            try {
                return iface.listNetworks();
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return null;
        }
    }

    /**
     * Set WPS device name.
     *
     * @param ifaceName Name of the interface.
     * @param name String to be set.
     * @return true if request is sent successfully, false otherwise.
     */
    public boolean setWpsDeviceName(@NonNull String ifaceName, String name) {
        synchronized (mLock) {
            final String methodStr = "setWpsDeviceName";
            ISupplicantStaIface iface = checkStaIfaceAndLogFailure(ifaceName, methodStr);
            if (iface == null) {
                return false;
            }
            try {
                iface.setWpsDeviceName(name);
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    /**
     * Set WPS device type.
     *
     * @param ifaceName Name of the interface.
     * @param typeStr Type specified as a string. Used format: <categ>-<OUI>-<subcateg>
     * @return true if request is sent successfully, false otherwise.
     */
    public boolean setWpsDeviceType(@NonNull String ifaceName, String typeStr) {
        synchronized (mLock) {
            try {
                Matcher match = WPS_DEVICE_TYPE_PATTERN.matcher(typeStr);
                if (!match.find() || match.groupCount() != 3) {
                    Log.e(TAG, "Malformed WPS device type " + typeStr);
                    return false;
                }
                short categ = Short.parseShort(match.group(1));
                byte[] oui = NativeUtil.hexStringToByteArray(match.group(2));
                short subCateg = Short.parseShort(match.group(3));

                byte[] bytes = new byte[8];
                ByteBuffer byteBuffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);
                byteBuffer.putShort(categ);
                byteBuffer.put(oui);
                byteBuffer.putShort(subCateg);
                return setWpsDeviceType(ifaceName, bytes);
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Illegal argument " + typeStr, e);
                return false;
            }
        }
    }

    private boolean setWpsDeviceType(@NonNull String ifaceName, byte[/* 8 */] type) {
        synchronized (mLock) {
            final String methodStr = "setWpsDeviceType";
            ISupplicantStaIface iface = checkStaIfaceAndLogFailure(ifaceName, methodStr);
            if (iface == null) {
                return false;
            }
            try {
                iface.setWpsDeviceType(type);
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    /**
     * Set WPS manufacturer.
     *
     * @param ifaceName Name of the interface.
     * @param manufacturer String to be set.
     * @return true if request is sent successfully, false otherwise.
     */
    public boolean setWpsManufacturer(@NonNull String ifaceName, String manufacturer) {
        synchronized (mLock) {
            final String methodStr = "setWpsManufacturer";
            ISupplicantStaIface iface = checkStaIfaceAndLogFailure(ifaceName, methodStr);
            if (iface == null) {
                return false;
            }
            try {
                iface.setWpsManufacturer(manufacturer);
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    /**
     * Set WPS model name.
     *
     * @param ifaceName Name of the interface.
     * @param modelName String to be set.
     * @return true if request is sent successfully, false otherwise.
     */
    public boolean setWpsModelName(@NonNull String ifaceName, String modelName) {
        synchronized (mLock) {
            final String methodStr = "setWpsModelName";
            ISupplicantStaIface iface = checkStaIfaceAndLogFailure(ifaceName, methodStr);
            if (iface == null) {
                return false;
            }
            try {
                iface.setWpsModelName(modelName);
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    /**
     * Set WPS model number.
     *
     * @param ifaceName Name of the interface.
     * @param modelNumber String to be set.
     * @return true if request is sent successfully, false otherwise.
     */
    public boolean setWpsModelNumber(@NonNull String ifaceName, String modelNumber) {
        synchronized (mLock) {
            final String methodStr = "setWpsModelNumber";
            ISupplicantStaIface iface = checkStaIfaceAndLogFailure(ifaceName, methodStr);
            if (iface == null) {
                return false;
            }
            try {
                iface.setWpsModelNumber(modelNumber);
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    /**
     * Set WPS serial number.
     *
     * @param ifaceName Name of the interface.
     * @param serialNumber String to be set.
     * @return true if request is sent successfully, false otherwise.
     */
    public boolean setWpsSerialNumber(@NonNull String ifaceName, String serialNumber) {
        synchronized (mLock) {
            final String methodStr = "setWpsSerialNumber";
            ISupplicantStaIface iface = checkStaIfaceAndLogFailure(ifaceName, methodStr);
            if (iface == null) {
                return false;
            }
            try {
                iface.setWpsSerialNumber(serialNumber);
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    /**
     * Set WPS config methods
     *
     * @param ifaceName Name of the interface.
     * @param configMethodsStr List of config methods.
     * @return true if request is sent successfully, false otherwise.
     */
    public boolean setWpsConfigMethods(@NonNull String ifaceName, String configMethodsStr) {
        synchronized (mLock) {
            int configMethodsMask = 0;
            String[] configMethodsStrArr = configMethodsStr.split("\\s+");
            for (int i = 0; i < configMethodsStrArr.length; i++) {
                configMethodsMask |= stringToWpsConfigMethod(configMethodsStrArr[i]);
            }
            return setWpsConfigMethods(ifaceName, configMethodsMask);
        }
    }

    private boolean setWpsConfigMethods(@NonNull String ifaceName, int configMethods) {
        synchronized (mLock) {
            final String methodStr = "setWpsConfigMethods";
            ISupplicantStaIface iface = checkStaIfaceAndLogFailure(ifaceName, methodStr);
            if (iface == null) {
                return false;
            }
            try {
                iface.setWpsConfigMethods(configMethods);
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    /**
     * Trigger a reassociation even if the iface is currently connected.
     *
     * @param ifaceName Name of the interface.
     * @return true if request is sent successfully, false otherwise.
     */
    public boolean reassociate(@NonNull String ifaceName) {
        synchronized (mLock) {
            final String methodStr = "reassociate";
            ISupplicantStaIface iface = checkStaIfaceAndLogFailure(ifaceName, methodStr);
            if (iface == null) {
                return false;
            }
            try {
                iface.reassociate();
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    /**
     * Trigger a reconnection if the iface is disconnected.
     *
     * @param ifaceName Name of the interface.
     * @return true if request is sent successfully, false otherwise.
     */
    public boolean reconnect(@NonNull String ifaceName) {
        synchronized (mLock) {
            final String methodStr = "reconnect";
            ISupplicantStaIface iface = checkStaIfaceAndLogFailure(ifaceName, methodStr);
            if (iface == null) {
                return false;
            }
            try {
                iface.reconnect();
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }


    /**
     * Trigger a disconnection from the currently connected network.
     *
     * @param ifaceName Name of the interface.
     * @return true if request is sent successfully, false otherwise.
     */
    public boolean disconnect(@NonNull String ifaceName) {
        synchronized (mLock) {
            final String methodStr = "disconnect";
            ISupplicantStaIface iface = checkStaIfaceAndLogFailure(ifaceName, methodStr);
            if (iface == null) {
                return false;
            }
            try {
                iface.disconnect();
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    /**
     * Enable or disable power save mode.
     *
     * @param ifaceName Name of the interface.
     * @param enable true to enable, false to disable.
     * @return true if request is sent successfully, false otherwise.
     */
    public boolean setPowerSave(@NonNull String ifaceName, boolean enable) {
        synchronized (mLock) {
            final String methodStr = "setPowerSave";
            ISupplicantStaIface iface = checkStaIfaceAndLogFailure(ifaceName, methodStr);
            if (iface == null) {
                return false;
            }
            try {
                iface.setPowerSave(enable);
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    /**
     * Initiate TDLS discover with the specified AP.
     *
     * @param ifaceName Name of the interface.
     * @param macAddress MAC Address of the AP.
     * @return true if request is sent successfully, false otherwise.
     */
    public boolean initiateTdlsDiscover(@NonNull String ifaceName, String macAddress) {
        synchronized (mLock) {
            try {
                return initiateTdlsDiscover(
                        ifaceName, NativeUtil.macAddressToByteArray(macAddress));
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Illegal argument " + macAddress, e);
                return false;
            }
        }
    }

    private boolean initiateTdlsDiscover(@NonNull String ifaceName, byte[/* 6 */] macAddress) {
        synchronized (mLock) {
            final String methodStr = "initiateTdlsDiscover";
            ISupplicantStaIface iface = checkStaIfaceAndLogFailure(ifaceName, methodStr);
            if (iface == null) {
                return false;
            }
            try {
                iface.initiateTdlsDiscover(macAddress);
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    /**
     * Initiate TDLS setup with the specified AP.
     *
     * @param ifaceName Name of the interface.
     * @param macAddress MAC Address of the AP.
     * @return true if request is sent successfully, false otherwise.
     */
    public boolean initiateTdlsSetup(@NonNull String ifaceName, String macAddress) {
        synchronized (mLock) {
            try {
                return initiateTdlsSetup(ifaceName, NativeUtil.macAddressToByteArray(macAddress));
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Illegal argument " + macAddress, e);
                return false;
            }
        }
    }

    private boolean initiateTdlsSetup(@NonNull String ifaceName, byte[/* 6 */] macAddress) {
        synchronized (mLock) {
            final String methodStr = "initiateTdlsSetup";
            ISupplicantStaIface iface = checkStaIfaceAndLogFailure(ifaceName, methodStr);
            if (iface == null) {
                return false;
            }
            try {
                iface.initiateTdlsSetup(macAddress);
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    /**
     * Initiate TDLS teardown with the specified AP.
     *
     * @param ifaceName Name of the interface.
     * @param macAddress MAC Address of the AP.
     * @return true if request is sent successfully, false otherwise.
     */
    public boolean initiateTdlsTeardown(@NonNull String ifaceName, String macAddress) {
        synchronized (mLock) {
            try {
                return initiateTdlsTeardown(
                        ifaceName, NativeUtil.macAddressToByteArray(macAddress));
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Illegal argument " + macAddress, e);
                return false;
            }
        }
    }

    private boolean initiateTdlsTeardown(@NonNull String ifaceName, byte[/* 6 */] macAddress) {
        synchronized (mLock) {
            final String methodStr = "initiateTdlsTeardown";
            ISupplicantStaIface iface = checkStaIfaceAndLogFailure(ifaceName, methodStr);
            if (iface == null) {
                return false;
            }
            try {
                iface.initiateTdlsTeardown(macAddress);
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    /**
     * Request the specified ANQP elements |elements| from the specified AP |bssid|.
     *
     * @param ifaceName Name of the interface.
     * @param bssid BSSID of the AP
     * @param infoElements ANQP elements to be queried. Refer to ISupplicantStaIface.AnqpInfoId.
     * @param hs20SubTypes HS subtypes to be queried. Refer to ISupplicantStaIface.Hs20AnqpSubTypes.
     * @return true if request is sent successfully, false otherwise.
     */
    public boolean initiateAnqpQuery(@NonNull String ifaceName, String bssid,
            ArrayList<Short> infoElements,
            ArrayList<Integer> hs20SubTypes) {
        synchronized (mLock) {
            try {
                int[] infoElementsCast = new int[infoElements.size()];
                int[] hs20SubTypesCast = new int[hs20SubTypes.size()];
                for (int i = 0; i < infoElements.size(); i++) {
                    infoElementsCast[i] = infoElements.get(i);
                }
                for (int i = 0; i < hs20SubTypes.size(); i++) {
                    hs20SubTypesCast[i] = hs20SubTypes.get(i);
                }
                return initiateAnqpQuery(
                        ifaceName,
                        NativeUtil.macAddressToByteArray(bssid),
                        infoElementsCast, hs20SubTypesCast);
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Illegal argument " + bssid, e);
                return false;
            }
        }
    }

    private boolean initiateAnqpQuery(@NonNull String ifaceName, byte[/* 6 */] macAddress,
            int[] infoElements, int[] subTypes) {
        synchronized (mLock) {
            final String methodStr = "initiateAnqpQuery";
            ISupplicantStaIface iface = checkStaIfaceAndLogFailure(ifaceName, methodStr);
            if (iface == null) {
                return false;
            }
            try {
                iface.initiateAnqpQuery(macAddress, infoElements, subTypes);
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    /**
     * Request Venue URL ANQP element from the specified AP |bssid|.
     *
     * @param ifaceName Name of the interface.
     * @param bssid BSSID of the AP
     * @return true if request is sent successfully, false otherwise.
     */
    public boolean initiateVenueUrlAnqpQuery(@NonNull String ifaceName, String bssid) {
        synchronized (mLock) {
            try {
                return initiateVenueUrlAnqpQuery(
                        ifaceName, NativeUtil.macAddressToByteArray(bssid));
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Illegal argument " + bssid, e);
                return false;
            }
        }
    }

    private boolean initiateVenueUrlAnqpQuery(@NonNull String ifaceName, byte[/* 6 */] macAddress) {
        synchronized (mLock) {
            final String methodStr = "initiateVenueUrlAnqpQuery";
            ISupplicantStaIface iface = checkStaIfaceAndLogFailure(ifaceName, methodStr);
            if (iface == null) {
                return false;
            }
            try {
                iface.initiateVenueUrlAnqpQuery(macAddress);
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    /**
     * Request the specified ANQP ICON from the specified AP |bssid|.
     *
     * @param ifaceName Name of the interface.
     * @param bssid BSSID of the AP
     * @param fileName Name of the file to request.
     * @return true if request is sent successfully, false otherwise.
     */
    public boolean initiateHs20IconQuery(@NonNull String ifaceName, String bssid, String fileName) {
        synchronized (mLock) {
            try {
                return initiateHs20IconQuery(
                        ifaceName, NativeUtil.macAddressToByteArray(bssid), fileName);
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Illegal argument " + bssid, e);
                return false;
            }
        }
    }

    private boolean initiateHs20IconQuery(@NonNull String ifaceName,
            byte[/* 6 */] macAddress, String fileName) {
        synchronized (mLock) {
            final String methodStr = "initiateHs20IconQuery";
            ISupplicantStaIface iface = checkStaIfaceAndLogFailure(ifaceName, methodStr);
            if (iface == null) {
                return false;
            }
            try {
                iface.initiateHs20IconQuery(macAddress, fileName);
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    /**
     * Gets MAC Address from the supplicant.
     *
     * @param ifaceName Name of the interface.
     * @return string containing the MAC address, or null on a failed call
     */
    public String getMacAddress(@NonNull String ifaceName) {
        synchronized (mLock) {
            final String methodStr = "getMacAddress";
            ISupplicantStaIface iface = checkStaIfaceAndLogFailure(ifaceName, methodStr);
            if (iface == null) {
                return null;
            }
            try {
                byte[] macAddr = iface.getMacAddress();
                return NativeUtil.macAddressFromByteArray(macAddr);
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Invalid MAC address value", e);
            }
            return null;
        }
    }

    /**
     * Start using the added RX filters.
     *
     * @param ifaceName Name of the interface.
     * @return true if request is sent successfully, false otherwise.
     */
    public boolean startRxFilter(@NonNull String ifaceName) {
        synchronized (mLock) {
            final String methodStr = "startRxFilter";
            ISupplicantStaIface iface = checkStaIfaceAndLogFailure(ifaceName, methodStr);
            if (iface == null) {
                return false;
            }
            try {
                iface.startRxFilter();
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    /**
     * Stop using the added RX filters.
     *
     * @param ifaceName Name of the interface.
     * @return true if request is sent successfully, false otherwise.
     */
    public boolean stopRxFilter(@NonNull String ifaceName) {
        synchronized (mLock) {
            final String methodStr = "stopRxFilter";
            ISupplicantStaIface iface = checkStaIfaceAndLogFailure(ifaceName, methodStr);
            if (iface == null) {
                return false;
            }
            try {
                iface.stopRxFilter();
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    /**
     * Add an RX filter.
     *
     * @param ifaceName Name of the interface.
     * @param type one of {@link WifiNative#RX_FILTER_TYPE_V4_MULTICAST}
     *        {@link WifiNative#RX_FILTER_TYPE_V6_MULTICAST} values.
     * @return true if request is sent successfully, false otherwise.
     */
    public boolean addRxFilter(@NonNull String ifaceName, int type) {
        synchronized (mLock) {
            byte halType;
            switch (type) {
                case WifiNative.RX_FILTER_TYPE_V4_MULTICAST:
                    halType = RxFilterType.V4_MULTICAST;
                    break;
                case WifiNative.RX_FILTER_TYPE_V6_MULTICAST:
                    halType = RxFilterType.V6_MULTICAST;
                    break;
                default:
                    Log.e(TAG, "Invalid Rx Filter type: " + type);
                    return false;
            }
            return addRxFilter(ifaceName, halType);
        }
    }

    private boolean addRxFilter(@NonNull String ifaceName, byte type) {
        synchronized (mLock) {
            final String methodStr = "addRxFilter";
            ISupplicantStaIface iface = checkStaIfaceAndLogFailure(ifaceName, methodStr);
            if (iface == null) {
                return false;
            }
            try {
                iface.addRxFilter(type);
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    /**
     * Remove an RX filter.
     *
     * @param ifaceName Name of the interface.
     * @param type one of {@link WifiNative#RX_FILTER_TYPE_V4_MULTICAST}
     *        {@link WifiNative#RX_FILTER_TYPE_V6_MULTICAST} values.
     * @return true if request is sent successfully, false otherwise.
     */
    public boolean removeRxFilter(@NonNull String ifaceName, int type) {
        synchronized (mLock) {
            byte halType;
            switch (type) {
                case WifiNative.RX_FILTER_TYPE_V4_MULTICAST:
                    halType = RxFilterType.V4_MULTICAST;
                    break;
                case WifiNative.RX_FILTER_TYPE_V6_MULTICAST:
                    halType = RxFilterType.V6_MULTICAST;
                    break;
                default:
                    Log.e(TAG, "Invalid Rx Filter type: " + type);
                    return false;
            }
            return removeRxFilter(ifaceName, halType);
        }
    }

    private boolean removeRxFilter(@NonNull String ifaceName, byte type) {
        synchronized (mLock) {
            final String methodStr = "removeRxFilter";
            ISupplicantStaIface iface = checkStaIfaceAndLogFailure(ifaceName, methodStr);
            if (iface == null) {
                return false;
            }
            try {
                iface.removeRxFilter(type);
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    /**
     * Set Bt co existense mode.
     *
     * @param ifaceName Name of the interface.
     * @param mode one of the above {@link WifiNative#BLUETOOTH_COEXISTENCE_MODE_DISABLED},
     *             {@link WifiNative#BLUETOOTH_COEXISTENCE_MODE_ENABLED} or
     *             {@link WifiNative#BLUETOOTH_COEXISTENCE_MODE_SENSE}.
     * @return true if request is sent successfully, false otherwise.
     */
    public boolean setBtCoexistenceMode(@NonNull String ifaceName, int mode) {
        synchronized (mLock) {
            byte halMode;
            switch (mode) {
                case WifiNative.BLUETOOTH_COEXISTENCE_MODE_ENABLED:
                    halMode = BtCoexistenceMode.ENABLED;
                    break;
                case WifiNative.BLUETOOTH_COEXISTENCE_MODE_DISABLED:
                    halMode = BtCoexistenceMode.DISABLED;
                    break;
                case WifiNative.BLUETOOTH_COEXISTENCE_MODE_SENSE:
                    halMode = BtCoexistenceMode.SENSE;
                    break;
                default:
                    Log.e(TAG, "Invalid Bt Coex mode: " + mode);
                    return false;
            }
            return setBtCoexistenceMode(ifaceName, halMode);
        }
    }

    private boolean setBtCoexistenceMode(@NonNull String ifaceName, byte mode) {
        synchronized (mLock) {
            final String methodStr = "setBtCoexistenceMode";
            ISupplicantStaIface iface = checkStaIfaceAndLogFailure(ifaceName, methodStr);
            if (iface == null) {
                return false;
            }
            try {
                iface.setBtCoexistenceMode(mode);
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    /** Enable or disable BT coexistence mode.
     *
     * @param ifaceName Name of the interface.
     * @param enable true to enable, false to disable.
     * @return true if request is sent successfully, false otherwise.
     */
    public boolean setBtCoexistenceScanModeEnabled(@NonNull String ifaceName, boolean enable) {
        synchronized (mLock) {
            final String methodStr = "setBtCoexistenceScanModeEnabled";
            ISupplicantStaIface iface = checkStaIfaceAndLogFailure(ifaceName, methodStr);
            if (iface == null) {
                return false;
            }
            try {
                iface.setBtCoexistenceScanModeEnabled(enable);
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    /**
     * Enable or disable suspend mode optimizations.
     *
     * @param ifaceName Name of the interface.
     * @param enable true to enable, false otherwise.
     * @return true if request is sent successfully, false otherwise.
     */
    public boolean setSuspendModeEnabled(@NonNull String ifaceName, boolean enable) {
        synchronized (mLock) {
            final String methodStr = "setSuspendModeEnabled";
            ISupplicantStaIface iface = checkStaIfaceAndLogFailure(ifaceName, methodStr);
            if (iface == null) {
                return false;
            }
            try {
                iface.setSuspendModeEnabled(enable);
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    /**
     * Set country code.
     *
     * @param ifaceName Name of the interface.
     * @param codeStr 2 byte ASCII string. For ex: US, CA.
     * @return true if request is sent successfully, false otherwise.
     */
    public boolean setCountryCode(@NonNull String ifaceName, String codeStr) {
        synchronized (mLock) {
            if (TextUtils.isEmpty(codeStr)) {
                return false;
            }
            byte[] countryCodeBytes = NativeUtil.stringToByteArray(codeStr);
            if (countryCodeBytes.length != 2) {
                return false;
            }
            return setCountryCode(ifaceName, countryCodeBytes);
        }
    }

    private boolean setCountryCode(@NonNull String ifaceName, byte[/* 2 */] code) {
        synchronized (mLock) {
            final String methodStr = "setCountryCode";
            ISupplicantStaIface iface = checkStaIfaceAndLogFailure(ifaceName, methodStr);
            if (iface == null) {
                return false;
            }
            try {
                iface.setCountryCode(code);
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    /**
     * Flush all previously configured HLPs.
     *
     * @param ifaceName Name of the interface.
     * @return true if request is sent successfully, false otherwise.
     */
    public boolean flushAllHlp(@NonNull String ifaceName) {
        synchronized (mLock) {
            final String methodStr = "filsHlpFlushRequest";
            ISupplicantStaIface iface = checkStaIfaceAndLogFailure(ifaceName, methodStr);
            if (iface == null) {
                return false;
            }
            try {
                iface.filsHlpFlushRequest();
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    /**
     * Set FILS HLP packet.
     *
     * @param ifaceName Name of the interface.
     * @param dst Destination MAC address.
     * @param hlpPacket Hlp Packet data in hex.
     * @return true if request is sent successfully, false otherwise.
     */
    public boolean addHlpReq(@NonNull String ifaceName, byte[] dst, byte[] hlpPacket) {
        synchronized (mLock) {
            final String methodStr = "filsHlpAddRequest";
            ISupplicantStaIface iface = checkStaIfaceAndLogFailure(ifaceName, methodStr);
            if (iface == null) {
                return false;
            }
            try {
                iface.filsHlpAddRequest(dst, hlpPacket);
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    /**
     * Start WPS pin registrar operation with the specified peer and pin.
     *
     * @param ifaceName Name of the interface.
     * @param bssidStr BSSID of the peer.
     * @param pin Pin to be used.
     * @return true if request is sent successfully, false otherwise.
     */
    public boolean startWpsRegistrar(@NonNull String ifaceName, String bssidStr, String pin) {
        synchronized (mLock) {
            if (TextUtils.isEmpty(bssidStr) || TextUtils.isEmpty(pin)) {
                return false;
            }
            try {
                return startWpsRegistrar(
                        ifaceName, NativeUtil.macAddressToByteArray(bssidStr), pin);
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Illegal argument " + bssidStr, e);
                return false;
            }
        }
    }

    private boolean startWpsRegistrar(@NonNull String ifaceName, byte[/* 6 */] bssid, String pin) {
        synchronized (mLock) {
            final String methodStr = "startWpsRegistrar";
            ISupplicantStaIface iface = checkStaIfaceAndLogFailure(ifaceName, methodStr);
            if (iface == null) {
                return false;
            }
            try {
                iface.startWpsRegistrar(bssid, pin);
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    /**
     * Start WPS pin display operation with the specified peer.
     *
     * @param ifaceName Name of the interface.
     * @param bssidStr BSSID of the peer. Use empty bssid to indicate wildcard.
     * @return true if request is sent successfully, false otherwise.
     */
    public boolean startWpsPbc(@NonNull String ifaceName, String bssidStr) {
        synchronized (mLock) {
            try {
                return startWpsPbc(ifaceName, NativeUtil.macAddressToByteArray(bssidStr));
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Illegal argument " + bssidStr, e);
                return false;
            }
        }
    }

    private boolean startWpsPbc(@NonNull String ifaceName, byte[/* 6 */] bssid) {
        synchronized (mLock) {
            final String methodStr = "startWpsPbc";
            ISupplicantStaIface iface = checkStaIfaceAndLogFailure(ifaceName, methodStr);
            if (iface == null) {
                return false;
            }
            try {
                iface.startWpsPbc(bssid);
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    /**
     * Start WPS pin keypad operation with the specified pin.
     *
     * @param ifaceName Name of the interface.
     * @param pin Pin to be used.
     * @return true if request is sent successfully, false otherwise.
     */
    public boolean startWpsPinKeypad(@NonNull String ifaceName, String pin) {
        if (TextUtils.isEmpty(pin)) {
            return false;
        }
        synchronized (mLock) {
            final String methodStr = "startWpsPinKeypad";
            ISupplicantStaIface iface = checkStaIfaceAndLogFailure(ifaceName, methodStr);
            if (iface == null) {
                return false;
            }
            try {
                iface.startWpsPinKeypad(pin);
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    /**
     * Start WPS pin display operation with the specified peer.
     *
     * @param ifaceName Name of the interface.
     * @param bssidStr BSSID of the peer. Use empty bssid to indicate wildcard.
     * @return new pin generated on success, null otherwise.
     */
    public String startWpsPinDisplay(@NonNull String ifaceName, String bssidStr) {
        synchronized (mLock) {
            try {
                return startWpsPinDisplay(ifaceName, NativeUtil.macAddressToByteArray(bssidStr));
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Illegal argument " + bssidStr, e);
                return null;
            }
        }
    }

    private String startWpsPinDisplay(@NonNull String ifaceName, byte[/* 6 */] bssid) {
        synchronized (mLock) {
            final String methodStr = "startWpsPinDisplay";
            ISupplicantStaIface iface = checkStaIfaceAndLogFailure(ifaceName, methodStr);
            if (iface == null) {
                return null;
            }
            try {
                return iface.startWpsPinDisplay(bssid);
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return null;
        }
    }

    /**
     * Cancels any ongoing WPS requests.
     *
     * @param ifaceName Name of the interface.
     * @return true if request is sent successfully, false otherwise.
     */
    public boolean cancelWps(@NonNull String ifaceName) {
        synchronized (mLock) {
            final String methodStr = "cancelWps";
            ISupplicantStaIface iface = checkStaIfaceAndLogFailure(ifaceName, methodStr);
            if (iface == null) {
                return false;
            }
            try {
                iface.cancelWps();
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    /**
     * Sets whether to use external sim for SIM/USIM processing.
     *
     * @param ifaceName Name of the interface.
     * @param useExternalSim true to enable, false otherwise.
     * @return true if request is sent successfully, false otherwise.
     */
    public boolean setExternalSim(@NonNull String ifaceName, boolean useExternalSim) {
        synchronized (mLock) {
            final String methodStr = "setExternalSim";
            ISupplicantStaIface iface = checkStaIfaceAndLogFailure(ifaceName, methodStr);
            if (iface == null) {
                return false;
            }
            try {
                iface.setExternalSim(useExternalSim);
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    /**
     * Enable/Disable auto reconnect to networks.
     * Use this to prevent wpa_supplicant from trying to connect to networks
     * on its own.
     *
     * @param enable true to enable, false to disable.
     * @return true if no exceptions occurred, false otherwise
     */
    public boolean enableAutoReconnect(@NonNull String ifaceName, boolean enable) {
        synchronized (mLock) {
            final String methodStr = "enableAutoReconnect";
            ISupplicantStaIface iface = checkStaIfaceAndLogFailure(ifaceName, methodStr);
            if (iface == null) {
                return false;
            }
            try {
                iface.enableAutoReconnect(enable);
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    /**
     * Set the debug log level for wpa_supplicant
     *
     * @param turnOnVerbose Whether to turn on verbose logging or not.
     * @return true if request is sent successfully, false otherwise.
     */
    public boolean setLogLevel(boolean turnOnVerbose) {
        synchronized (mLock) {
            int logLevel = turnOnVerbose
                    ? DebugLevel.DEBUG
                    : DebugLevel.INFO;
            return setDebugParams(logLevel, false,
                    turnOnVerbose && mWifiGlobals.getShowKeyVerboseLoggingModeEnabled());
        }
    }

    /**
     * Set debug parameters for the ISupplicant service.
     *
     * @param level Debug logging level for the supplicant.
     *        (one of |DebugLevel| values).
     * @param showTimestamp Determines whether to show timestamps in logs or not.
     * @param showKeys Determines whether to show keys in debug logs or not.
     *        CAUTION: Do not set this param in production code!
     * @return true if no exceptions occurred, false otherwise
     */
    private boolean setDebugParams(int level, boolean showTimestamp, boolean showKeys) {
        synchronized (mLock) {
            final String methodStr = "setDebugParams";
            if (!checkSupplicantAndLogFailure(methodStr)) {
                return false;
            }
            try {
                mISupplicant.setDebugParams(level, showTimestamp, showKeys);
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    /**
     * Set concurrency priority between P2P & STA operations.
     *
     * @param isStaHigherPriority Set to true to prefer STA over P2P during concurrency operations,
     *                            false otherwise.
     * @return true if request is sent successfully, false otherwise.
     */
    public boolean setConcurrencyPriority(boolean isStaHigherPriority) {
        synchronized (mLock) {
            if (isStaHigherPriority) {
                return setConcurrencyPriority(IfaceType.STA);
            } else {
                return setConcurrencyPriority(IfaceType.P2P);
            }
        }
    }

    private boolean setConcurrencyPriority(int type) {
        synchronized (mLock) {
            final String methodStr = "setConcurrencyPriority";
            if (!checkSupplicantAndLogFailure(methodStr)) {
                return false;
            }
            try {
                mISupplicant.setConcurrencyPriority(type);
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    /**
     * Returns false if mISupplicant is null and logs failure message
     */
    private boolean checkSupplicantAndLogFailure(final String methodStr) {
        synchronized (mLock) {
            if (mISupplicant == null) {
                Log.e(TAG, "Can't call " + methodStr + ", ISupplicant is null");
                return false;
            }
            return true;
        }
    }

    /**
     * Returns specified STA iface if it exists. Otherwise, logs error and returns null.
     */
    private ISupplicantStaIface checkStaIfaceAndLogFailure(
            @NonNull String ifaceName, final String methodStr) {
        synchronized (mLock) {
            ISupplicantStaIface iface = getStaIface(ifaceName);
            if (iface == null) {
                Log.e(TAG, "Can't call " + methodStr + ", ISupplicantStaIface is null for "
                        + "iface=" + ifaceName);
                return null;
            }
            return iface;
        }
    }

    /**
     * Returns network belonging to the specified STA iface if it exists.
     * Otherwise, logs error and returns null.
     */
    private SupplicantStaNetworkHalAidlImpl checkStaNetworkAndLogFailure(
            @NonNull String ifaceName, final String methodStr) {
        synchronized (mLock) {
            SupplicantStaNetworkHalAidlImpl networkHandle =
                    getCurrentNetworkRemoteHandle(ifaceName);
            if (networkHandle == null) {
                Log.e(TAG, "Can't call " + methodStr + ", SupplicantStaNetwork for iface="
                        + ifaceName + " is null.");
                return null;
            }
            return networkHandle;
        }
    }

    /**
     * Helper function to log callback events
     */
    protected void logCallback(final String methodStr) {
        synchronized (mLock) {
            if (mVerboseLoggingEnabled) {
                Log.d(TAG, "ISupplicantStaIfaceCallback." + methodStr + " received");
            }
        }
    }

    private void handleRemoteException(RemoteException e, String methodStr) {
        synchronized (mLock) {
            clearState();
            Log.e(TAG,
                    "ISupplicantStaIface." + methodStr + " failed with remote exception: ", e);
        }
    }

    private void handleServiceSpecificException(ServiceSpecificException e, String methodStr) {
        synchronized (mLock) {
            Log.e(TAG, "ISupplicantStaIface." + methodStr + " failed with "
                    + "service specific exception: ", e);
        }
    }

    /**
     * Converts the Wps config method string to the equivalent enum value.
     */
    private static int stringToWpsConfigMethod(String configMethod) {
        switch (configMethod) {
            case "usba":
                return WpsConfigMethods.USBA;
            case "ethernet":
                return WpsConfigMethods.ETHERNET;
            case "label":
                return WpsConfigMethods.LABEL;
            case "display":
                return WpsConfigMethods.DISPLAY;
            case "int_nfc_token":
                return WpsConfigMethods.INT_NFC_TOKEN;
            case "ext_nfc_token":
                return WpsConfigMethods.EXT_NFC_TOKEN;
            case "nfc_interface":
                return WpsConfigMethods.NFC_INTERFACE;
            case "push_button":
                return WpsConfigMethods.PUSHBUTTON;
            case "keypad":
                return WpsConfigMethods.KEYPAD;
            case "virtual_push_button":
                return WpsConfigMethods.VIRT_PUSHBUTTON;
            case "physical_push_button":
                return WpsConfigMethods.PHY_PUSHBUTTON;
            case "p2ps":
                return WpsConfigMethods.P2PS;
            case "virtual_display":
                return WpsConfigMethods.VIRT_DISPLAY;
            case "physical_display":
                return WpsConfigMethods.PHY_DISPLAY;
            default:
                throw new IllegalArgumentException(
                        "Invalid WPS config method: " + configMethod);
        }
    }

    protected void addPmkCacheEntry(
            String ifaceName, int networkId,
            long expirationTimeInSec, ArrayList<Byte> serializedEntry) {
        synchronized (mLock) {
            String macAddressStr = getMacAddress(ifaceName);
            try {
                if (!mPmkCacheManager.add(MacAddress.fromString(macAddressStr),
                        networkId, expirationTimeInSec, serializedEntry)) {
                    Log.w(TAG, "Cannot add PMK cache for " + ifaceName);
                }
            } catch (IllegalArgumentException ex) {
                Log.w(TAG, "Cannot add PMK cache: " + ex);
            }
        }
    }

    protected void removePmkCacheEntry(int networkId) {
        synchronized (mLock) {
            mPmkCacheManager.remove(networkId);
        }
    }

    /**
     * Returns a bitmask of advanced capabilities: WPA3 SAE/SUITE B and OWE
     * Bitmask used is:
     * - WIFI_FEATURE_WPA3_SAE
     * - WIFI_FEATURE_WPA3_SUITE_B
     * - WIFI_FEATURE_OWE
     *
     *  @return true if successful, false otherwise.
     */
    public long getAdvancedCapabilities(@NonNull String ifaceName) {
        synchronized (mLock) {
            final String methodStr = "getAdvancedCapabilities";
            long advancedCapabilities = 0;
            int keyMgmtCapabilities = getKeyMgmtCapabilities(ifaceName);

            advancedCapabilities |= WIFI_FEATURE_PASSPOINT_TERMS_AND_CONDITIONS
                    | WIFI_FEATURE_DECORATED_IDENTITY;
            if (mVerboseLoggingEnabled) {
                Log.v(TAG, methodStr + ": Passpoint T&C supported");
                Log.v(TAG, methodStr + ": RFC 7542 decorated identity supported");
            }

            if ((keyMgmtCapabilities & KeyMgmtMask.SAE) != 0) {
                advancedCapabilities |= WIFI_FEATURE_WPA3_SAE;

                if (mVerboseLoggingEnabled) {
                    Log.v(TAG, methodStr + ": SAE supported");
                }
            }

            if ((keyMgmtCapabilities & KeyMgmtMask.SUITE_B_192) != 0) {
                advancedCapabilities |= WIFI_FEATURE_WPA3_SUITE_B;

                if (mVerboseLoggingEnabled) {
                    Log.v(TAG, methodStr + ": SUITE_B supported");
                }
            }

            if ((keyMgmtCapabilities & KeyMgmtMask.OWE) != 0) {
                advancedCapabilities |= WIFI_FEATURE_OWE;

                if (mVerboseLoggingEnabled) {
                    Log.v(TAG, methodStr + ": OWE supported");
                }
            }

            if ((keyMgmtCapabilities & KeyMgmtMask.DPP) != 0) {
                advancedCapabilities |= WIFI_FEATURE_DPP
                        | WIFI_FEATURE_DPP_ENROLLEE_RESPONDER;

                if (mVerboseLoggingEnabled) {
                    Log.v(TAG, methodStr + ": DPP supported");
                    Log.v(TAG, methodStr + ": DPP ENROLLEE RESPONDER supported");
                }
            }

            if ((keyMgmtCapabilities & KeyMgmtMask.WAPI_PSK) != 0) {
                advancedCapabilities |= WIFI_FEATURE_WAPI;

                if (mVerboseLoggingEnabled) {
                    Log.v(TAG, methodStr + ": WAPI supported");
                }
            }

            if ((keyMgmtCapabilities & KeyMgmtMask.FILS_SHA256) != 0) {
                advancedCapabilities |= WIFI_FEATURE_FILS_SHA256;

                if (mVerboseLoggingEnabled) {
                    Log.v(TAG, methodStr + ": FILS_SHA256 supported");
                }
            }

            if ((keyMgmtCapabilities & KeyMgmtMask.FILS_SHA384) != 0) {
                advancedCapabilities |= WIFI_FEATURE_FILS_SHA384;

                if (mVerboseLoggingEnabled) {
                    Log.v(TAG, methodStr + ": FILS_SHA384 supported");
                }
            }

            if (mVerboseLoggingEnabled) {
                Log.v(TAG, methodStr + ": Capability flags = " + keyMgmtCapabilities);
            }

            return advancedCapabilities;
        }
    }

    private int getKeyMgmtCapabilities(@NonNull String ifaceName) {
        synchronized (mLock) {
            final String methodStr = "getKeyMgmtCapabilities";
            ISupplicantStaIface iface = checkStaIfaceAndLogFailure(ifaceName, methodStr);
            if (iface == null) {
                return 0;
            }
            try {
                return iface.getKeyMgmtCapabilities();
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return 0;
        }
    }

    /**
     * Get the driver supported features through supplicant.
     *
     * @param ifaceName Name of the interface.
     * @return bitmask defined by WifiManager.WIFI_FEATURE_*.
     */
    public long getWpaDriverFeatureSet(@NonNull String ifaceName) {
        synchronized (mLock) {
            final String methodStr = "getWpaDriverFeatureSet";
            int drvCapabilitiesMask = getWpaDriverCapabilities(ifaceName);
            long featureSet = 0;

            if ((drvCapabilitiesMask & WpaDriverCapabilitiesMask.MBO) != 0) {
                featureSet |= WIFI_FEATURE_MBO;
                if (mVerboseLoggingEnabled) {
                    Log.v(TAG, methodStr + ": MBO supported");
                }
                if ((drvCapabilitiesMask & WpaDriverCapabilitiesMask.OCE) != 0) {
                    featureSet |= WIFI_FEATURE_OCE;
                    if (mVerboseLoggingEnabled) {
                        Log.v(TAG, methodStr + ": OCE supported");
                    }
                }
            }

            if ((drvCapabilitiesMask & WpaDriverCapabilitiesMask.SAE_PK) != 0) {
                featureSet |= WIFI_FEATURE_SAE_PK;
                if (mVerboseLoggingEnabled) {
                    Log.v(TAG, methodStr + ": SAE-PK supported");
                }
            }

            if ((drvCapabilitiesMask & WpaDriverCapabilitiesMask.WFD_R2) != 0) {
                featureSet |= WIFI_FEATURE_WFD_R2;
                if (mVerboseLoggingEnabled) {
                    Log.v(TAG, methodStr + ": WFD-R2 supported");
                }
            }

            if ((drvCapabilitiesMask
                    & WpaDriverCapabilitiesMask.TRUST_ON_FIRST_USE) != 0) {
                featureSet |= WIFI_FEATURE_TRUST_ON_FIRST_USE;
                if (mVerboseLoggingEnabled) {
                    Log.v(TAG, methodStr + ": Trust-On-First-Use supported");
                }
            }

            return featureSet;
        }
    }

    private int getWpaDriverCapabilities(@NonNull String ifaceName) {
        synchronized (mLock) {
            final String methodStr = "getWpaDriverCapabilities";
            ISupplicantStaIface iface = checkStaIfaceAndLogFailure(ifaceName, methodStr);
            if (iface == null) {
                return 0;
            }
            try {
                return iface.getWpaDriverCapabilities();
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return 0;
        }
    }

    private @WifiAnnotations.WifiStandard int getWifiStandard(int technology) {
        switch(technology) {
            case WifiTechnology.EHT:
                return ScanResult.WIFI_STANDARD_11BE;
            case WifiTechnology.HE:
                return ScanResult.WIFI_STANDARD_11AX;
            case WifiTechnology.VHT:
                return ScanResult.WIFI_STANDARD_11AC;
            case WifiTechnology.HT:
                return ScanResult.WIFI_STANDARD_11N;
            case WifiTechnology.LEGACY:
                return ScanResult.WIFI_STANDARD_LEGACY;
            default:
                return ScanResult.WIFI_STANDARD_UNKNOWN;
        }
    }

    private int getChannelBandwidth(int channelBandwidth) {
        switch(channelBandwidth) {
            case WifiChannelWidthInMhz.WIDTH_20:
                return ScanResult.CHANNEL_WIDTH_20MHZ;
            case WifiChannelWidthInMhz.WIDTH_40:
                return ScanResult.CHANNEL_WIDTH_40MHZ;
            case WifiChannelWidthInMhz.WIDTH_80:
                return ScanResult.CHANNEL_WIDTH_80MHZ;
            case WifiChannelWidthInMhz.WIDTH_160:
                return ScanResult.CHANNEL_WIDTH_160MHZ;
            case WifiChannelWidthInMhz.WIDTH_80P80:
                return ScanResult.CHANNEL_WIDTH_80MHZ_PLUS_MHZ;
            case WifiChannelWidthInMhz.WIDTH_320:
                return ScanResult.CHANNEL_WIDTH_320MHZ;
            default:
                return ScanResult.CHANNEL_WIDTH_20MHZ;
        }
    }

    private int frameworkToAidlDppAkm(int dppAkm) {
        switch(dppAkm) {
            case SupplicantStaIfaceHal.DppAkm.PSK:
                return DppAkm.PSK;
            case SupplicantStaIfaceHal.DppAkm.PSK_SAE:
                return DppAkm.PSK_SAE;
            case SupplicantStaIfaceHal.DppAkm.SAE:
                return DppAkm.SAE;
            case SupplicantStaIfaceHal.DppAkm.DPP:
                return DppAkm.DPP;
            default:
                Log.e(TAG, "Invalid DppAkm received");
                return -1;
        }
    }

    private int frameworkToAidlDppCurve(int dppCurve) {
        switch(dppCurve) {
            case SupplicantStaIfaceHal.DppCurve.PRIME256V1:
                return DppCurve.PRIME256V1;
            case SupplicantStaIfaceHal.DppCurve.SECP384R1:
                return DppCurve.SECP384R1;
            case SupplicantStaIfaceHal.DppCurve.SECP521R1:
                return DppCurve.SECP521R1;
            case SupplicantStaIfaceHal.DppCurve.BRAINPOOLP256R1:
                return DppCurve.BRAINPOOLP256R1;
            case SupplicantStaIfaceHal.DppCurve.BRAINPOOLP384R1:
                return DppCurve.BRAINPOOLP384R1;
            case SupplicantStaIfaceHal.DppCurve.BRAINPOOLP512R1:
                return DppCurve.BRAINPOOLP512R1;
            default:
                Log.e(TAG, "Invalid DppCurve received");
                return -1;
        }
    }

    private int frameworkToAidlDppNetRole(int dppNetRole) {
        switch(dppNetRole) {
            case SupplicantStaIfaceHal.DppNetRole.STA:
                return DppNetRole.STA;
            case SupplicantStaIfaceHal.DppNetRole.AP:
                return DppNetRole.AP;
            default:
                Log.e(TAG, "Invalid DppNetRole received");
                return -1;
        }
    }

    protected byte dscpPolicyToAidlQosPolicyStatusCode(int status) {
        switch (status) {
            case NetworkAgent.DSCP_POLICY_STATUS_SUCCESS:
            case NetworkAgent.DSCP_POLICY_STATUS_DELETED:
                return QosPolicyStatusCode.QOS_POLICY_SUCCESS;
            case NetworkAgent.DSCP_POLICY_STATUS_REQUEST_DECLINED:
                return QosPolicyStatusCode.QOS_POLICY_REQUEST_DECLINED;
            case NetworkAgent.DSCP_POLICY_STATUS_REQUESTED_CLASSIFIER_NOT_SUPPORTED:
                return QosPolicyStatusCode.QOS_POLICY_CLASSIFIER_NOT_SUPPORTED;
            case NetworkAgent.DSCP_POLICY_STATUS_INSUFFICIENT_PROCESSING_RESOURCES:
                return QosPolicyStatusCode.QOS_POLICY_INSUFFICIENT_RESOURCES;
            default:
                Log.e(TAG, "Invalid DSCP policy failure code received: " + status);
                return QosPolicyStatusCode.QOS_POLICY_REQUEST_DECLINED;
        }
    }

    protected static int halToFrameworkQosPolicyRequestType(byte requestType) {
        switch (requestType) {
            case QosPolicyRequestType.QOS_POLICY_ADD:
                return SupplicantStaIfaceHal.QOS_POLICY_REQUEST_ADD;
            case QosPolicyRequestType.QOS_POLICY_REMOVE:
                return SupplicantStaIfaceHal.QOS_POLICY_REQUEST_REMOVE;
            default:
                Log.e(TAG, "Invalid QosPolicyRequestType received: " + requestType);
                return -1;
        }
    }

    private static boolean qosClassifierParamHasValue(int classifierParamMask, int paramBit) {
        return (classifierParamMask & paramBit) != 0;
    }

    /**
     * Convert from a HAL QosPolicyData object to a framework QosPolicy object.
     */
    public static SupplicantStaIfaceHal.QosPolicyRequest halToFrameworkQosPolicy(
            QosPolicyData halQosPolicy) {
        QosPolicyClassifierParams classifierParams = halQosPolicy.classifierParams;
        int classifierParamMask = classifierParams.classifierParamMask;

        byte[] srcIp = null;
        byte[] dstIp = null;
        int srcPort = DscpPolicy.SOURCE_PORT_ANY;
        int[] dstPortRange = new int[]{MIN_PORT_NUM, MAX_PORT_NUM};
        int protocol = DscpPolicy.PROTOCOL_ANY;
        boolean hasSrcIp = false;
        boolean hasDstIp = false;

        if (qosClassifierParamHasValue(classifierParamMask, QosPolicyClassifierParamsMask.SRC_IP)) {
            hasSrcIp = true;
            srcIp = classifierParams.srcIp;
        }
        if (qosClassifierParamHasValue(classifierParamMask, QosPolicyClassifierParamsMask.DST_IP)) {
            hasDstIp = true;
            dstIp = classifierParams.dstIp;
        }
        if (qosClassifierParamHasValue(classifierParamMask,
                QosPolicyClassifierParamsMask.SRC_PORT)) {
            srcPort = classifierParams.srcPort;
        }
        if (qosClassifierParamHasValue(classifierParamMask,
                QosPolicyClassifierParamsMask.DST_PORT_RANGE)) {
            dstPortRange[0] = classifierParams.dstPortRange.startPort;
            dstPortRange[1] = classifierParams.dstPortRange.endPort;
        }
        if (qosClassifierParamHasValue(classifierParamMask,
                QosPolicyClassifierParamsMask.PROTOCOL_NEXT_HEADER)) {
            protocol = classifierParams.protocolNextHdr;
        }

        return new SupplicantStaIfaceHal.QosPolicyRequest(halQosPolicy.policyId,
                halToFrameworkQosPolicyRequestType(halQosPolicy.requestType), halQosPolicy.dscp,
                new SupplicantStaIfaceHal.QosPolicyClassifierParams(
                        hasSrcIp, srcIp, hasDstIp, dstIp, srcPort, dstPortRange, protocol));
    }

    /**
     * Returns connection capabilities of the current network
     *
     * @param ifaceName Name of the interface.
     * @return connection capabilities of the current network
     */
    public WifiNative.ConnectionCapabilities getConnectionCapabilities(@NonNull String ifaceName) {
        synchronized (mLock) {
            final String methodStr = "getConnectionCapabilities";
            WifiNative.ConnectionCapabilities capOut = new WifiNative.ConnectionCapabilities();
            ISupplicantStaIface iface = checkStaIfaceAndLogFailure(ifaceName, methodStr);
            if (iface == null) {
                return capOut;
            }
            try {
                ConnectionCapabilities cap = iface.getConnectionCapabilities();
                capOut.wifiStandard = getWifiStandard(cap.technology);
                capOut.channelBandwidth = getChannelBandwidth(cap.channelBandwidth);
                capOut.is11bMode = (cap.legacyMode == LegacyMode.B_MODE);
                capOut.maxNumberTxSpatialStreams = cap.maxNumberTxSpatialStreams;
                capOut.maxNumberRxSpatialStreams = cap.maxNumberRxSpatialStreams;
                return capOut;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return capOut;
        }
    }

    /**
     * Returns connection MLO links info
     *
     * @param ifaceName Name of the interface.
     * @return connection MLO links info
     */
    public WifiNative.ConnectionMloLinksInfo getConnectionMloLinksInfo(@NonNull String ifaceName) {
        synchronized (mLock) {
            final String methodStr = "getConnectionMloLinksInfo";
            ISupplicantStaIface iface = checkStaIfaceAndLogFailure(ifaceName, methodStr);
            if (iface == null) {
                return null;
            }
            try {
                MloLinksInfo halInfo = iface.getConnectionMloLinksInfo();
                if (halInfo == null) {
                    return null;
                }

                WifiNative.ConnectionMloLinksInfo nativeInfo =
                        new WifiNative.ConnectionMloLinksInfo();

                nativeInfo.links = new WifiNative.ConnectionMloLink[halInfo.links.length];

                for (int i = 0; i < halInfo.links.length; i++) {
                    nativeInfo.links[i].linkId = halInfo.links[i].linkId;
                    nativeInfo.links[i].staMacAddress = MacAddress.fromBytes(
                            halInfo.links[i].staLinkMacAddress);
                }
                return nativeInfo;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Invalid STA Mac Address received from HAL");
                return null;
            }

            return null;
        }
    }

    /**
     * Adds a DPP peer URI to the URI list.
     *
     * Returns an ID to be used later to refer to this URI (>0).
     * On error, -1 is returned.
     */
    public int addDppPeerUri(@NonNull String ifaceName, @NonNull String uri) {
        synchronized (mLock) {
            final String methodStr = "addDppPeerUri";
            ISupplicantStaIface iface = checkStaIfaceAndLogFailure(ifaceName, methodStr);
            if (iface == null) {
                return -1;
            }
            try {
                return iface.addDppPeerUri(uri);
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return -1;
        }
    }

    /**
     * Removes a DPP URI to the URI list given an ID.
     *
     * Returns true when operation is successful
     * On error, false is returned.
     */
    public boolean removeDppUri(@NonNull String ifaceName, int bootstrapId)  {
        synchronized (mLock) {
            final String methodStr = "removeDppUri";
            ISupplicantStaIface iface = checkStaIfaceAndLogFailure(ifaceName, methodStr);
            if (iface == null) {
                return false;
            }
            try {
                iface.removeDppUri(bootstrapId);
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    /**
     * Stops/aborts DPP Initiator request
     *
     * Returns true when operation is successful
     * On error, false is returned.
     */
    public boolean stopDppInitiator(@NonNull String ifaceName)  {
        synchronized (mLock) {
            final String methodStr = "stopDppInitiator";
            ISupplicantStaIface iface = checkStaIfaceAndLogFailure(ifaceName, methodStr);
            if (iface == null) {
                return false;
            }
            try {
                iface.stopDppInitiator();
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    /**
     * Starts DPP Configurator-Initiator request
     *
     * Returns true when operation is successful
     * On error, false is returned.
     */
    public boolean startDppConfiguratorInitiator(@NonNull String ifaceName, int peerBootstrapId,
            int ownBootstrapId, @NonNull String ssid, String password, String psk,
            int netRole, int securityAkm, byte[] privEcKey)  {
        synchronized (mLock) {
            final String methodStr = "startDppConfiguratorInitiator";
            ISupplicantStaIface iface = checkStaIfaceAndLogFailure(ifaceName, methodStr);
            if (iface == null) {
                return false;
            }
            try {
                byte[] key = iface.startDppConfiguratorInitiator(peerBootstrapId, ownBootstrapId,
                        ssid, password != null ? password : "", psk != null ? psk : "",
                        frameworkToAidlDppNetRole(netRole), frameworkToAidlDppAkm(securityAkm),
                        privEcKey != null ? privEcKey : new byte[] {});
                if (key != null && key.length > 0 && mDppCallback != null) {
                    mDppCallback.onDppConfiguratorKeyUpdate(key);
                }
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    /**
     * Starts DPP Enrollee-Initiator request
     *
     * Returns true when operation is successful
     * On error, false is returned.
     */
    public boolean startDppEnrolleeInitiator(@NonNull String ifaceName, int peerBootstrapId,
            int ownBootstrapId)  {
        synchronized (mLock) {
            final String methodStr = "startDppEnrolleeInitiator";
            ISupplicantStaIface iface = checkStaIfaceAndLogFailure(ifaceName, methodStr);
            if (iface == null) {
                return false;
            }
            try {
                iface.startDppEnrolleeInitiator(peerBootstrapId, ownBootstrapId);
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    /**
     * Generate a DPP QR code based boot strap info
     *
     * Returns DppResponderBootstrapInfo;
     */
    public WifiNative.DppBootstrapQrCodeInfo generateDppBootstrapInfoForResponder(
            @NonNull String ifaceName, String macAddress, @NonNull String deviceInfo,
            int dppCurve) {
        synchronized (mLock) {
            final String methodStr = "generateDppBootstrapInfoForResponder";
            WifiNative.DppBootstrapQrCodeInfo bootstrapInfoOut =
                    new WifiNative.DppBootstrapQrCodeInfo();
            ISupplicantStaIface iface = checkStaIfaceAndLogFailure(ifaceName, methodStr);
            if (iface == null) {
                return bootstrapInfoOut;
            }
            try {
                DppResponderBootstrapInfo info = iface.generateDppBootstrapInfoForResponder(
                        NativeUtil.macAddressToByteArray(macAddress), deviceInfo,
                        frameworkToAidlDppCurve(dppCurve));
                bootstrapInfoOut.bootstrapId = info.bootstrapId;
                bootstrapInfoOut.listenChannel = info.listenChannel;
                bootstrapInfoOut.uri = info.uri;
                return bootstrapInfoOut;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return bootstrapInfoOut;
        }
    }

    /**
     * Starts DPP Enrollee-Responder request
     *
     * Returns true when operation is successful
     * On error, false is returned.
     */
    public boolean startDppEnrolleeResponder(@NonNull String ifaceName, int listenChannel) {
        synchronized (mLock) {
            final String methodStr = "startDppEnrolleeResponder";
            ISupplicantStaIface iface = checkStaIfaceAndLogFailure(ifaceName, methodStr);
            if (iface == null) {
                return false;
            }
            try {
                iface.startDppEnrolleeResponder(listenChannel);
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    /**
     * Stops/aborts DPP Responder request.
     *
     * Returns true when operation is successful
     * On error, false is returned.
     */
    public boolean stopDppResponder(@NonNull String ifaceName, int ownBootstrapId)  {
        synchronized (mLock) {
            final String methodStr = "stopDppResponder";
            ISupplicantStaIface iface = checkStaIfaceAndLogFailure(ifaceName, methodStr);
            if (iface == null) {
                return false;
            }
            try {
                iface.stopDppResponder(ownBootstrapId);
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    /**
     * Register callbacks for DPP events.
     *
     * @param dppCallback DPP callback object.
     */
    public void registerDppCallback(WifiNative.DppEventCallback dppCallback) {
        synchronized (mLock) {
            mDppCallback = dppCallback;
        }
    }

    protected WifiNative.DppEventCallback getDppCallback() {
        synchronized (mLock) {
            return mDppCallback;
        }
    }

    /**
     * Set MBO cellular data availability.
     *
     * @param ifaceName Name of the interface.
     * @param available true means cellular data available, false otherwise.
     * @return true is operation is successful, false otherwise.
     */
    public boolean setMboCellularDataStatus(@NonNull String ifaceName, boolean available) {
        synchronized (mLock) {
            final String methodStr = "setMboCellularDataStatus";
            ISupplicantStaIface iface = checkStaIfaceAndLogFailure(ifaceName, methodStr);
            if (iface == null) {
                return false;
            }
            try {
                iface.setMboCellularDataStatus(available);
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    /**
     * Set whether the network-centric QoS policy feature is enabled or not for this interface.
     *
     * @param ifaceName name of the interface.
     * @param isEnabled true if feature is enabled, false otherwise.
     * @return true if operation is successful, false otherwise.
     */
    public boolean setNetworkCentricQosPolicyFeatureEnabled(@NonNull String ifaceName,
            boolean isEnabled) {
        synchronized (mLock) {
            String methodStr = "setNetworkCentricQosPolicyFeatureEnabled";
            ISupplicantStaIface iface = checkStaIfaceAndLogFailure(ifaceName, methodStr);
            if (iface == null) {
                return false;
            }
            try {
                iface.setQosPolicyFeatureEnabled(isEnabled);
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    /**
     * Check if we've roamed to a linked network and make the linked network the current network
     * if we have.
     *
     * @param ifaceName Name of the interface.
     * @param newNetworkId Network id of the new network we've roamed to. If fromFramework is
     *                     {@code true}, this will be a framework network id. Otherwise, this will
     *                     be a remote network id.
     * @param fromFramework {@code true} if the network id is a framework network id, {@code false}
                            if the network id is a remote network id.
     * @return true if we've roamed to a linked network, false if not.
     */
    public boolean updateOnLinkedNetworkRoaming(
            @NonNull String ifaceName, int newNetworkId, boolean fromFramework) {
        synchronized (mLock) {
            List<Pair<SupplicantStaNetworkHalAidlImpl, WifiConfiguration>> linkedNetworkHandles =
                    mLinkedNetworkLocalAndRemoteConfigs.get(ifaceName);
            SupplicantStaNetworkHalAidlImpl currentHandle =
                    getCurrentNetworkRemoteHandle(ifaceName);
            WifiConfiguration currentConfig = getCurrentNetworkLocalConfig(ifaceName);
            if (linkedNetworkHandles == null || currentHandle == null || currentConfig == null) {
                return false;
            }
            if (fromFramework ? currentConfig.networkId == newNetworkId
                    : currentHandle.getNetworkId() == newNetworkId) {
                return false;
            }
            for (Pair<SupplicantStaNetworkHalAidlImpl, WifiConfiguration> pair
                    : linkedNetworkHandles) {
                if (fromFramework ? pair.second.networkId == newNetworkId
                        : pair.first.getNetworkId() == newNetworkId) {
                    Log.i(TAG, "Roamed to linked network, make linked network as current network");
                    mCurrentNetworkRemoteHandles.put(ifaceName, pair.first);
                    mCurrentNetworkLocalConfigs.put(ifaceName, pair.second);
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * Updates the linked networks for the current network and sends them to the supplicant.
     *
     * @param ifaceName Name of the interface.
     * @param networkId Network id of the network to link the configurations to.
     * @param linkedConfigurations Map of config profile key to config for linking.
     * @return true if networks were successfully linked, false otherwise.
     */
    public boolean updateLinkedNetworks(@NonNull String ifaceName, int networkId,
            Map<String, WifiConfiguration> linkedConfigurations) {
        synchronized (mLock) {
            WifiConfiguration currentConfig = getCurrentNetworkLocalConfig(ifaceName);
            SupplicantStaNetworkHalAidlImpl currentHandle =
                    getCurrentNetworkRemoteHandle(ifaceName);

            if (currentConfig == null || currentHandle == null) {
                Log.e(TAG, "current network not configured yet.");
                return false;
            }

            if (networkId != currentConfig.networkId) {
                Log.e(TAG, "current config network id is not matching");
                return false;
            }

            final int remoteNetworkId = currentHandle.getNetworkId();
            if (remoteNetworkId == -1) {
                Log.e(TAG, "current handle getNetworkId failed");
                return false;
            }

            if (!removeAllNetworksExcept(ifaceName, remoteNetworkId)) {
                Log.e(TAG, "couldn't remove non-current supplicant networks");
                return false;
            }

            mLinkedNetworkLocalAndRemoteConfigs.remove(ifaceName);

            if (linkedConfigurations == null || linkedConfigurations.size() == 0) {
                Log.i(TAG, "cleared linked networks");
                return true;
            }

            List<Pair<SupplicantStaNetworkHalAidlImpl, WifiConfiguration>> linkedNetworkHandles =
                    new ArrayList<>();
            linkedNetworkHandles.add(new Pair(currentHandle, currentConfig));
            for (String linkedNetwork : linkedConfigurations.keySet()) {
                Log.i(TAG, "add linked network: " + linkedNetwork);
                Pair<SupplicantStaNetworkHalAidlImpl, WifiConfiguration> pair =
                        addNetworkAndSaveConfig(ifaceName, linkedConfigurations.get(linkedNetwork));
                if (pair == null) {
                    Log.e(TAG, "failed to add/save linked network: " + linkedNetwork);
                    return false;
                }
                pair.first.enable(true);
                linkedNetworkHandles.add(pair);
            }

            mLinkedNetworkLocalAndRemoteConfigs.put(ifaceName, linkedNetworkHandles);

            return true;
        }
    }

    /**
     * Remove all networks except the supplied network ID from supplicant
     *
     * @param ifaceName Name of the interface
     * @param networkId network id to keep
     */
    private boolean removeAllNetworksExcept(@NonNull String ifaceName, int networkId) {
        synchronized (mLock) {
            int[] networks = listNetworks(ifaceName);
            if (networks == null) {
                Log.e(TAG, "removeAllNetworksExcept failed, got null networks");
                return false;
            }
            for (int id : networks) {
                if (networkId == id) {
                    continue;
                }
                if (!removeNetwork(ifaceName, id)) {
                    Log.e(TAG, "removeAllNetworksExcept failed to remove network: " + id);
                    return false;
                }
            }
            return true;
        }
    }

    /**
     * Gets the security params of the current network associated with this interface
     *
     * @param ifaceName Name of the interface
     * @return Security params of the current network associated with the interface
     */
    public SecurityParams getCurrentNetworkSecurityParams(@NonNull String ifaceName) {
        WifiConfiguration currentConfig = getCurrentNetworkLocalConfig(ifaceName);

        if (currentConfig == null) {
            return null;
        }

        return currentConfig.getNetworkSelectionStatus().getCandidateSecurityParams();
    }

    /**
     * Sends a QoS policy response.
     *
     * @param ifaceName Name of the interface.
     * @param qosPolicyRequestId Dialog token to identify the request.
     * @param morePolicies Flag to indicate more QoS policies can be accommodated.
     * @param qosPolicyStatusList List of framework QosPolicyStatus objects.
     * @return true if response is sent successfully, false otherwise.
     */
    public boolean sendQosPolicyResponse(String ifaceName, int qosPolicyRequestId,
            boolean morePolicies,
            @NonNull List<SupplicantStaIfaceHal.QosPolicyStatus> qosPolicyStatusList) {
        synchronized (mLock) {
            final String methodStr = "sendQosPolicyResponse";
            ISupplicantStaIface iface = checkStaIfaceAndLogFailure(ifaceName, methodStr);
            if (iface == null) {
                return false;
            }

            int index = 0;
            QosPolicyStatus[] halPolicyStatusList = new QosPolicyStatus[qosPolicyStatusList.size()];
            for (SupplicantStaIfaceHal.QosPolicyStatus frameworkPolicyStatus
                    : qosPolicyStatusList) {
                if (frameworkPolicyStatus == null) {
                    return false;
                }
                QosPolicyStatus halPolicyStatus = new QosPolicyStatus();
                halPolicyStatus.policyId = (byte) frameworkPolicyStatus.policyId;
                halPolicyStatus.status = dscpPolicyToAidlQosPolicyStatusCode(
                        frameworkPolicyStatus.dscpPolicyStatus);
                halPolicyStatusList[index] = halPolicyStatus;
                index++;
            }

            try {
                iface.sendQosPolicyResponse(qosPolicyRequestId, morePolicies, halPolicyStatusList);
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    /**
     * Indicates the removal of all active QoS policies configured by the AP.
     *
     * @param ifaceName Name of the interface.
     */
    public boolean removeAllQosPolicies(String ifaceName) {
        final String methodStr = "removeAllQosPolicies";
        ISupplicantStaIface iface = checkStaIfaceAndLogFailure(ifaceName, methodStr);
        if (iface == null) {
            return false;
        }

        try {
            iface.removeAllQosPolicies();
            return true;
        } catch (RemoteException e) {
            handleRemoteException(e, methodStr);
        } catch (ServiceSpecificException e) {
            handleServiceSpecificException(e, methodStr);
        }
        return false;
    }

    /**
     * Generate DPP credential for network access
     *
     * @param ifaceName Name of the interface.
     * @param ssid ssid of the network
     * @param privEcKey Private EC Key for DPP Configurator
     * Returns true when operation is successful. On error, false is returned.
     */
    public boolean generateSelfDppConfiguration(@NonNull String ifaceName, @NonNull String ssid,
            byte[] privEcKey) {
        synchronized (mLock) {
            final String methodStr = "generateSelfDppConfiguration";
            ISupplicantStaIface iface = checkStaIfaceAndLogFailure(ifaceName, methodStr);
            if (iface == null) {
                return false;
            }
            try {
                iface.generateSelfDppConfiguration(
                        NativeUtil.removeEnclosingQuotes(ssid), privEcKey);
                return true;
            } catch (RemoteException e) {
                handleRemoteException(e, methodStr);
            } catch (ServiceSpecificException e) {
                handleServiceSpecificException(e, methodStr);
            }
            return false;
        }
    }

    /**
     * Set the currently configured network's anonymous identity.
     *
     * @param ifaceName Name of the interface.
     * @param anonymousIdentity the anonymouns identity.
     * @return true if succeeds, false otherwise.
     */
    public boolean setEapAnonymousIdentity(@NonNull String ifaceName, String anonymousIdentity) {
        synchronized (mLock) {
            SupplicantStaNetworkHalAidlImpl networkHandle =
                    checkStaNetworkAndLogFailure(ifaceName, "setEapAnonymousIdentity");
            if (networkHandle == null) return false;
            if (anonymousIdentity == null) return false;
            return networkHandle.setEapAnonymousIdentity(anonymousIdentity.getBytes());
        }
    }
}
