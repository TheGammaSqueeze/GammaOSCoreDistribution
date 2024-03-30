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

package com.android.server.wifi.hotspot2;

import static com.android.server.wifi.hotspot2.PasspointMatch.HomeProvider;

import android.annotation.NonNull;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.util.ScanResultUtil;
import android.util.LocalLog;
import android.util.Pair;

import com.android.server.wifi.NetworkUpdateResult;
import com.android.server.wifi.ScanDetail;
import com.android.server.wifi.WifiCarrierInfoManager;
import com.android.server.wifi.WifiConfigManager;
import com.android.server.wifi.hotspot2.anqp.ANQPElement;
import com.android.server.wifi.hotspot2.anqp.Constants;
import com.android.server.wifi.hotspot2.anqp.HSWanMetricsElement;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * This class is the WifiNetworkSelector.NetworkNominator implementation for
 * Passpoint networks.
 */
public class PasspointNetworkNominateHelper {
    @NonNull private final PasspointManager mPasspointManager;
    @NonNull private final WifiConfigManager mWifiConfigManager;
    @NonNull private final List<ScanDetail> mCachedScanDetails = new ArrayList<>();
    @NonNull private final LocalLog mLocalLog;
    @NonNull private final WifiCarrierInfoManager mCarrierInfoManager;

    /**
     * Contained information for a Passpoint network candidate.
     */
    private class PasspointNetworkCandidate {
        PasspointNetworkCandidate(PasspointProvider provider, PasspointMatch matchStatus,
                ScanDetail scanDetail) {
            mProvider = provider;
            mMatchStatus = matchStatus;
            mScanDetail = scanDetail;
        }
        PasspointProvider mProvider;
        PasspointMatch mMatchStatus;
        ScanDetail mScanDetail;
    }

    public PasspointNetworkNominateHelper(@NonNull PasspointManager passpointManager,
            @NonNull WifiConfigManager wifiConfigManager, @NonNull LocalLog localLog,
            WifiCarrierInfoManager carrierInfoManager) {
        mPasspointManager = passpointManager;
        mWifiConfigManager = wifiConfigManager;
        mLocalLog = localLog;
        mCarrierInfoManager = carrierInfoManager;
    }

    /**
     * Update the matched passpoint network to the WifiConfigManager.
     * Should be called each time have new scan details.
     */
    public void updatePasspointConfig(List<ScanDetail> scanDetails) {
        filterAndUpdateScanDetails(scanDetails);
        updateBestMatchScanDetailForProviders();
    }

    /**
     * Get best matched available Passpoint network candidates for scanDetails.
     * @param scanDetails List of ScanDetail.
     * @param isFromSuggestion True to indicate profile from suggestion, false for user saved.
     * @return List of pair of scanDetail and WifiConfig from matched available provider.
     */
    public List<Pair<ScanDetail, WifiConfiguration>> getPasspointNetworkCandidates(
            List<ScanDetail> scanDetails, boolean isFromSuggestion) {
        filterAndUpdateScanDetails(scanDetails);
        return findBestMatchScanDetailForProviders(isFromSuggestion);
    }

    /**
     * Filter out non-passpoint networks
     */
    private void filterAndUpdateScanDetails(List<ScanDetail> scanDetails) {
        // Sweep the ANQP cache to remove any expired ANQP entries.
        mPasspointManager.sweepCache();
        List<ScanDetail> filteredScanDetails = new ArrayList<>();
        // Filter out all invalid scanDetail
        for (ScanDetail scanDetail : scanDetails) {
            if (scanDetail.getNetworkDetail() == null
                    || !scanDetail.getNetworkDetail().isInterworking()
                    || scanDetail.getNetworkDetail().getHSRelease() == null) {
                // If scanDetail is not Passpoint network, ignore.
                continue;
            }
            filteredScanDetails.add(scanDetail);
        }
        if (!filteredScanDetails.isEmpty()) {
            mCachedScanDetails.clear();
            mCachedScanDetails.addAll(filteredScanDetails);
        }
    }

    /**
     * Check if ANQP element inside that scanDetail indicate AP WAN port link status is down.
     *
     * @param scanDetail contains ANQP element to check.
     * @return return true is link status is down, otherwise return false.
     */
    private boolean isApWanLinkStatusDown(ScanDetail scanDetail) {
        Map<Constants.ANQPElementType, ANQPElement> anqpElements =
                mPasspointManager.getANQPElements(scanDetail.getScanResult());
        if (anqpElements == null) {
            return false;
        }
        HSWanMetricsElement wm = (HSWanMetricsElement) anqpElements.get(
                Constants.ANQPElementType.HSWANMetrics);
        if (wm == null) {
            return false;
        }

        // Check if the WAN Metrics ANQP element is initialized with values other than 0's
        if (!wm.isElementInitialized()) {
            // WAN Metrics ANQP element is not initialized in this network. Ignore it.
            return false;
        }
        return wm.getStatus() != HSWanMetricsElement.LINK_STATUS_UP || wm.isAtCapacity();
    }

    /**
     * Use the latest scan details to add/update the matched passpoint to WifiConfigManager.  This
     * should be used if new profiles have been added but scan results remain the same, or new
     * ScanDetails available.
     */
    public void updateBestMatchScanDetailForProviders() {
        if (mPasspointManager.isProvidersListEmpty()
                || !mPasspointManager.isWifiPasspointEnabled() || mCachedScanDetails.isEmpty()) {
            return;
        }
        Map<PasspointProvider, List<PasspointNetworkCandidate>> candidatesPerProvider =
                getMatchedCandidateGroupByProvider(mCachedScanDetails, false);
        // For each provider find the best scanDetail(prefer home, higher RSSI) for it and update
        // it to the WifiConfigManager.
        for (List<PasspointNetworkCandidate> candidates : candidatesPerProvider.values()) {
            List<PasspointNetworkCandidate> bestCandidates = findHomeNetworksIfPossible(candidates);
            Optional<PasspointNetworkCandidate> highestRssi = bestCandidates.stream().max(
                    Comparator.comparingInt(a -> a.mScanDetail.getScanResult().level));
            if (!highestRssi.isEmpty()) {
                createWifiConfigForProvider(highestRssi.get());
            }
        }
    }

    /**
     * Match available providers for each scan detail and add their configs to WifiConfigManager.
     * Then for each available provider, find the best scan detail for it.
     * @param isFromSuggestion True to indicate profile from suggestion, false for user saved.
     * @return List of pair of scanDetail and WifiConfig from matched available provider.
     */
    private @NonNull List<Pair<ScanDetail, WifiConfiguration>> findBestMatchScanDetailForProviders(
            boolean isFromSuggestion) {
        List<ScanDetail> scanDetails = mCachedScanDetails.stream()
                .filter(a -> !isApWanLinkStatusDown(a))
                .collect(Collectors.toList());
        if (mPasspointManager.isProvidersListEmpty()
                || !mPasspointManager.isWifiPasspointEnabled() || scanDetails.isEmpty()) {
            return Collections.emptyList();
        }
        List<Pair<ScanDetail, WifiConfiguration>> results = new ArrayList<>();
        Map<PasspointProvider, List<PasspointNetworkCandidate>> candidatesPerProvider =
                getMatchedCandidateGroupByProvider(mCachedScanDetails, true);
        // For each provider find the best scanDetails(prefer home) for it and create selection
        // candidate pair.
        for (Map.Entry<PasspointProvider, List<PasspointNetworkCandidate>> candidates :
                candidatesPerProvider.entrySet()) {
            if (candidates.getKey().isFromSuggestion() != isFromSuggestion) {
                continue;
            }
            List<PasspointNetworkCandidate> bestCandidates =
                    findHomeNetworksIfPossible(candidates.getValue());
            for (PasspointNetworkCandidate candidate : bestCandidates) {
                WifiConfiguration config = createWifiConfigForProvider(candidate);
                if (config == null) {
                    continue;
                }

                if (mWifiConfigManager.isNonCarrierMergedNetworkTemporarilyDisabled(config)) {
                    mLocalLog.log("Ignoring non-carrier-merged SSID: " + config.FQDN);
                    continue;
                }
                if (mWifiConfigManager.isNetworkTemporarilyDisabledByUser(config.FQDN)) {
                    mLocalLog.log("Ignoring user disabled FQDN: " + config.FQDN);
                    continue;
                }
                results.add(Pair.create(candidate.mScanDetail, config));
            }
        }
        return results;
    }

    private Map<PasspointProvider, List<PasspointNetworkCandidate>>
            getMatchedCandidateGroupByProvider(List<ScanDetail> scanDetails,
            boolean onlyHomeIfAvailable) {
        Map<PasspointProvider, List<PasspointNetworkCandidate>> candidatesPerProvider =
                new HashMap<>();
        // Match each scanDetail with the best provider (home > roaming), and grouped by provider.
        for (ScanDetail scanDetail : scanDetails) {
            List<Pair<PasspointProvider, PasspointMatch>> matchedProviders =
                    mPasspointManager.matchProvider(scanDetail.getScanResult());
            if (matchedProviders == null) {
                continue;
            }
            if (onlyHomeIfAvailable) {
                List<Pair<PasspointProvider, PasspointMatch>> homeProviders =
                        matchedProviders.stream()
                                .filter(a -> a.second == HomeProvider)
                                .collect(Collectors.toList());
                if (!homeProviders.isEmpty()) {
                    matchedProviders = homeProviders;
                }
            }
            for (Pair<PasspointProvider, PasspointMatch> matchedProvider : matchedProviders) {
                List<PasspointNetworkCandidate> candidates = candidatesPerProvider
                        .computeIfAbsent(matchedProvider.first, k -> new ArrayList<>());
                candidates.add(new PasspointNetworkCandidate(matchedProvider.first,
                        matchedProvider.second, scanDetail));
            }
        }
        return candidatesPerProvider;
    }

    /**
     * Create and return a WifiConfiguration for the given ScanDetail and PasspointProvider.
     * The newly created WifiConfiguration will also be added to WifiConfigManager.
     *
     * @return {@link WifiConfiguration}
     */
    private WifiConfiguration createWifiConfigForProvider(
            PasspointNetworkCandidate candidate) {
        WifiConfiguration config = candidate.mProvider.getWifiConfig();
        config.SSID = ScanResultUtil.createQuotedSsid(candidate.mScanDetail.getSSID());
        config.isHomeProviderNetwork = candidate.mMatchStatus == HomeProvider;
        if (candidate.mScanDetail.getNetworkDetail().getAnt()
                == NetworkDetail.Ant.ChargeablePublic) {
            config.meteredHint = true;
        }
        if (mCarrierInfoManager.shouldDisableMacRandomization(config.SSID,
                config.carrierId, config.subscriptionId)) {
            mLocalLog.log("Disabling MAC randomization on " + config.SSID
                    + " due to CarrierConfig override");
            config.macRandomizationSetting = WifiConfiguration.RANDOMIZATION_NONE;
        }
        WifiConfiguration existingNetwork = mWifiConfigManager.getConfiguredNetwork(
                config.getProfileKey());
        if (existingNetwork != null) {
            WifiConfiguration.NetworkSelectionStatus status =
                    existingNetwork.getNetworkSelectionStatus();
            if (!(status.isNetworkEnabled()
                    || mWifiConfigManager.tryEnableNetwork(existingNetwork.networkId))) {
                mLocalLog.log("Current configuration for the Passpoint AP " + config.SSID
                        + " is disabled, skip this candidate");
                return null;
            }
        }

        // Add or update with the newly created WifiConfiguration to WifiConfigManager.
        // NOTE: if existingNetwork != null, this update is a no-op in most cases if the SSID is the
        // same (since we update the cached config in PasspointManager#addOrUpdateProvider().
        NetworkUpdateResult result = mWifiConfigManager.addOrUpdateNetwork(
                config, config.creatorUid, config.creatorName, false);

        if (!result.isSuccess()) {
            mLocalLog.log("Failed to add passpoint network");
            return existingNetwork;
        }
        mWifiConfigManager.enableNetwork(result.getNetworkId(), false, config.creatorUid, null);
        mWifiConfigManager.setNetworkCandidateScanResult(result.getNetworkId(),
                candidate.mScanDetail.getScanResult(), 0, null);
        mWifiConfigManager.updateScanDetailForNetwork(
                result.getNetworkId(), candidate.mScanDetail);
        return mWifiConfigManager.getConfiguredNetwork(result.getNetworkId());
    }

    /**
     * Given a list of Passpoint networks (with both provider and scan info), return all
     * homeProvider matching networks if there is any, otherwise return all roamingProvider matching
     * networks.
     *
     * @param networkList List of Passpoint networks
     * @return List of {@link PasspointNetworkCandidate}
     */
    private @NonNull List<PasspointNetworkCandidate> findHomeNetworksIfPossible(
            @NonNull List<PasspointNetworkCandidate> networkList) {
        List<PasspointNetworkCandidate> homeProviderCandidates = networkList.stream()
                .filter(candidate -> candidate.mMatchStatus == HomeProvider)
                .collect(Collectors.toList());
        if (homeProviderCandidates.isEmpty()) {
            return networkList;
        }
        return homeProviderCandidates;
    }
}
