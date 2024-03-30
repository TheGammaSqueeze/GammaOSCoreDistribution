/*
 * Copyright 2020 The Android Open Source Project
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

package com.google.android.iwlan;

import static android.net.NetworkCapabilities.TRANSPORT_CELLULAR;
import static android.net.NetworkCapabilities.TRANSPORT_WIFI;

import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.support.annotation.IntRange;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.telephony.AccessNetworkConstants.AccessNetworkType;
import android.telephony.CarrierConfigManager;
import android.telephony.CellInfo;
import android.telephony.CellInfoGsm;
import android.telephony.CellInfoLte;
import android.telephony.CellInfoNr;
import android.telephony.CellInfoWcdma;
import android.telephony.DataFailCause;
import android.telephony.TelephonyManager;
import android.telephony.data.ApnSetting;
import android.telephony.data.DataCallResponse;
import android.telephony.data.DataProfile;
import android.telephony.data.DataService;
import android.telephony.data.DataServiceCallback;
import android.telephony.data.NetworkSliceInfo;
import android.telephony.data.TrafficDescriptor;
import android.util.Log;

import com.android.internal.annotations.VisibleForTesting;

import com.google.android.iwlan.epdg.EpdgSelector;
import com.google.android.iwlan.epdg.EpdgTunnelManager;
import com.google.android.iwlan.epdg.TunnelLinkProperties;
import com.google.android.iwlan.epdg.TunnelSetupRequest;
import com.google.android.iwlan.proto.MetricsAtom;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.LongSummaryStatistics;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class IwlanDataService extends DataService {

    private static final String TAG = IwlanDataService.class.getSimpleName();
    private static Context mContext;
    private IwlanNetworkMonitorCallback mNetworkMonitorCallback;
    private static boolean sNetworkConnected = false;
    private static Network sNetwork = null;
    @VisibleForTesting Handler mIwlanDataServiceHandler;
    private HandlerThread mIwlanDataServiceHandlerThread;
    private static final Map<Integer, IwlanDataServiceProvider> sIwlanDataServiceProviders =
            new ConcurrentHashMap<>();

    private static final int EVENT_BASE = IwlanEventListener.DATA_SERVICE_INTERNAL_EVENT_BASE;
    private static final int EVENT_TUNNEL_OPENED = EVENT_BASE;
    private static final int EVENT_TUNNEL_CLOSED = EVENT_BASE + 1;
    private static final int EVENT_SETUP_DATA_CALL = EVENT_BASE + 2;
    private static final int EVENT_DEACTIVATE_DATA_CALL = EVENT_BASE + 3;
    private static final int EVENT_DATA_CALL_LIST_REQUEST = EVENT_BASE + 4;
    private static final int EVENT_FORCE_CLOSE_TUNNEL = EVENT_BASE + 5;
    private static final int EVENT_ADD_DATA_SERVICE_PROVIDER = EVENT_BASE + 6;
    private static final int EVENT_REMOVE_DATA_SERVICE_PROVIDER = EVENT_BASE + 7;
    private static final int EVENT_TUNNEL_OPENED_METRICS = EVENT_BASE + 8;
    private static final int EVENT_TUNNEL_CLOSED_METRICS = EVENT_BASE + 9;

    @VisibleForTesting
    enum Transport {
        UNSPECIFIED_NETWORK,
        MOBILE,
        WIFI;
    }

    private static Transport sDefaultDataTransport = Transport.UNSPECIFIED_NETWORK;

    enum LinkProtocolType {
        UNKNOWN,
        IPV4,
        IPV6,
        IPV4V6;
    }

    private static LinkProtocolType sLinkProtocolType = LinkProtocolType.UNKNOWN;

    // TODO: see if network monitor callback impl can be shared between dataservice and
    // networkservice
    // This callback runs in the same thread as IwlanDataServiceHandler
    static class IwlanNetworkMonitorCallback extends ConnectivityManager.NetworkCallback {

        /** Called when the framework connects and has declared a new network ready for use. */
        @Override
        public void onAvailable(Network network) {
            Log.d(TAG, "onAvailable: " + network);
        }

        /**
         * Called when the network is about to be lost, typically because there are no outstanding
         * requests left for it. This may be paired with a {@link NetworkCallback#onAvailable} call
         * with the new replacement network for graceful handover. This method is not guaranteed to
         * be called before {@link NetworkCallback#onLost} is called, for example in case a network
         * is suddenly disconnected.
         */
        @Override
        public void onLosing(Network network, int maxMsToLive) {
            Log.d(TAG, "onLosing: maxMsToLive: " + maxMsToLive + " network: " + network);
        }

        /**
         * Called when a network disconnects or otherwise no longer satisfies this request or
         * callback.
         */
        @Override
        public void onLost(Network network) {
            Log.d(TAG, "onLost: " + network);
            IwlanDataService.setNetworkConnected(false, network, Transport.UNSPECIFIED_NETWORK);
        }

        /** Called when the network corresponding to this request changes {@link LinkProperties}. */
        @Override
        public void onLinkPropertiesChanged(Network network, LinkProperties linkProperties) {
            Log.d(TAG, "onLinkPropertiesChanged: " + linkProperties);
            if (isLinkProtocolTypeChanged(linkProperties)) {
                for (IwlanDataServiceProvider dp : sIwlanDataServiceProviders.values()) {
                    dp.dnsPrefetchCheck();
                }
            }
        }

        /** Called when access to the specified network is blocked or unblocked. */
        @Override
        public void onBlockedStatusChanged(Network network, boolean blocked) {
            // TODO: check if we need to handle this
            Log.d(TAG, "onBlockedStatusChanged: " + network + " BLOCKED:" + blocked);
        }

        @Override
        public void onCapabilitiesChanged(
                Network network, NetworkCapabilities networkCapabilities) {
            // onCapabilitiesChanged is guaranteed to be called immediately after onAvailable per
            // API
            Log.d(TAG, "onCapabilitiesChanged: " + network + " " + networkCapabilities);
            if (networkCapabilities != null) {
                if (networkCapabilities.hasTransport(TRANSPORT_CELLULAR)) {
                    Log.d(TAG, "Network " + network + " connected using transport MOBILE");
                    IwlanDataService.setNetworkConnected(true, network, Transport.MOBILE);
                } else if (networkCapabilities.hasTransport(TRANSPORT_WIFI)) {
                    Log.d(TAG, "Network " + network + " connected using transport WIFI");
                    IwlanDataService.setNetworkConnected(true, network, Transport.WIFI);
                } else {
                    Log.w(TAG, "Network does not have cellular or wifi capability");
                }
            }
        }
    }

    @VisibleForTesting
    class IwlanDataServiceProvider extends DataService.DataServiceProvider {

        private static final int CALLBACK_TYPE_SETUP_DATACALL_COMPLETE = 1;
        private static final int CALLBACK_TYPE_DEACTIVATE_DATACALL_COMPLETE = 2;
        private static final int CALLBACK_TYPE_GET_DATACALL_LIST_COMPLETE = 3;
        private final String SUB_TAG;
        private final IwlanDataService mIwlanDataService;
        private final IwlanTunnelCallback mIwlanTunnelCallback;
        private final IwlanTunnelCallbackMetrics mIwlanTunnelCallbackMetrics;
        private boolean mWfcEnabled = false;
        private boolean mCarrierConfigReady = false;
        private EpdgSelector mEpdgSelector;
        private IwlanDataTunnelStats mTunnelStats;
        private CellInfo mCellInfo = null;
        private long mProcessingStartTime = 0;

        // apn to TunnelState
        // Access should be serialized inside IwlanDataServiceHandler
        private Map<String, TunnelState> mTunnelStateForApn = new ConcurrentHashMap<>();
        private Map<String, MetricsAtom> mMetricsAtomForApn = new ConcurrentHashMap<>();

        // Holds the state of a tunnel (for an APN)
        @VisibleForTesting
        class TunnelState {

            // this should be ideally be based on path MTU discovery. 1280 is the minimum packet
            // size ipv6 routers have to handle so setting it to 1280 is the safest approach.
            // ideally it should be 1280 - tunnelling overhead ?
            private static final int LINK_MTU =
                    1280; // TODO: need to substract tunnelling overhead?
            private static final int LINK_MTU_CST = 1200; // Reserve 80 bytes for VCN.
            static final int TUNNEL_DOWN = 1;
            static final int TUNNEL_IN_BRINGUP = 2;
            static final int TUNNEL_UP = 3;
            static final int TUNNEL_IN_BRINGDOWN = 4;
            static final int TUNNEL_IN_FORCE_CLEAN_WAS_IN_BRINGUP = 5;
            private DataServiceCallback dataServiceCallback;
            private int mState;
            private int mPduSessionId;
            private TunnelLinkProperties mTunnelLinkProperties;
            private boolean mIsHandover;
            private Date mBringUpStateTime = null;
            private Date mUpStateTime = null;

            public int getPduSessionId() {
                return mPduSessionId;
            }

            public void setPduSessionId(int mPduSessionId) {
                this.mPduSessionId = mPduSessionId;
            }

            public int getProtocolType() {
                return mProtocolType;
            }

            public int getLinkMtu() {
                if ((sDefaultDataTransport == Transport.MOBILE) && sNetworkConnected) {
                    return LINK_MTU_CST;
                } else {
                    return LINK_MTU; // TODO: need to substract tunnelling overhead
                }
            }

            public void setProtocolType(int protocolType) {
                mProtocolType = protocolType;
            }

            private int mProtocolType; // from DataProfile

            public TunnelLinkProperties getTunnelLinkProperties() {
                return mTunnelLinkProperties;
            }

            public void setTunnelLinkProperties(TunnelLinkProperties tunnelLinkProperties) {
                mTunnelLinkProperties = tunnelLinkProperties;
            }

            public DataServiceCallback getDataServiceCallback() {
                return dataServiceCallback;
            }

            public void setDataServiceCallback(DataServiceCallback dataServiceCallback) {
                this.dataServiceCallback = dataServiceCallback;
            }

            public TunnelState(DataServiceCallback callback) {
                dataServiceCallback = callback;
                mState = TUNNEL_DOWN;
            }

            public int getState() {
                return mState;
            }

            /**
             * @param state (TunnelState.TUNNEL_DOWN|TUNNEL_UP|TUNNEL_DOWN)
             */
            public void setState(int state) {
                mState = state;
                if (mState == TunnelState.TUNNEL_IN_BRINGUP) {
                    mBringUpStateTime = Calendar.getInstance().getTime();
                }
                if (mState == TunnelState.TUNNEL_UP) {
                    mUpStateTime = Calendar.getInstance().getTime();
                }
            }

            public void setIsHandover(boolean isHandover) {
                mIsHandover = isHandover;
            }

            public boolean getIsHandover() {
                return mIsHandover;
            }

            public Date getBringUpStateTime() {
                return mBringUpStateTime;
            }

            public Date getUpStateTime() {
                return mUpStateTime;
            }

            @Override
            public String toString() {
                StringBuilder sb = new StringBuilder();
                String tunnelState = "UNKNOWN";
                switch (mState) {
                    case TUNNEL_DOWN:
                        tunnelState = "DOWN";
                        break;
                    case TUNNEL_IN_BRINGUP:
                        tunnelState = "IN BRINGUP";
                        break;
                    case TUNNEL_UP:
                        tunnelState = "UP";
                        break;
                    case TUNNEL_IN_BRINGDOWN:
                        tunnelState = "IN BRINGDOWN";
                        break;
                    case TUNNEL_IN_FORCE_CLEAN_WAS_IN_BRINGUP:
                        tunnelState = "IN FORCE CLEAN WAS IN BRINGUP";
                        break;
                }
                sb.append("\tCurrent State of this tunnel: " + mState + " " + tunnelState);
                sb.append("\n\tTunnel state is in Handover: " + mIsHandover);
                if (mBringUpStateTime != null) {
                    sb.append("\n\tTunnel bring up initiated at: " + mBringUpStateTime);
                } else {
                    sb.append("\n\tPotential leak. Null mBringUpStateTime");
                }
                if (mUpStateTime != null) {
                    sb.append("\n\tTunnel is up at: " + mUpStateTime);
                }
                if (mUpStateTime != null && mBringUpStateTime != null) {
                    long tunnelUpTime = mUpStateTime.getTime() - mBringUpStateTime.getTime();
                    sb.append("\n\tTime taken for the tunnel to come up in ms: " + tunnelUpTime);
                }
                return sb.toString();
            }
        }

        @VisibleForTesting
        class IwlanTunnelCallback implements EpdgTunnelManager.TunnelCallback {

            DataServiceProvider mDataServiceProvider;

            public IwlanTunnelCallback(DataServiceProvider dsp) {
                mDataServiceProvider = dsp;
            }

            // TODO: full implementation

            public void onOpened(String apnName, TunnelLinkProperties linkProperties) {
                Log.d(
                        SUB_TAG,
                        "Tunnel opened!. APN: " + apnName + "linkproperties: " + linkProperties);
                mIwlanDataServiceHandler.sendMessage(
                        mIwlanDataServiceHandler.obtainMessage(
                                EVENT_TUNNEL_OPENED,
                                new TunnelOpenedData(
                                        apnName, linkProperties, IwlanDataServiceProvider.this)));
            }

            public void onClosed(String apnName, IwlanError error) {
                Log.d(SUB_TAG, "Tunnel closed!. APN: " + apnName + " Error: " + error);
                // this is called, when a tunnel that is up, is closed.
                // the expectation is error==NO_ERROR for user initiated/normal close.
                mIwlanDataServiceHandler.sendMessage(
                        mIwlanDataServiceHandler.obtainMessage(
                                EVENT_TUNNEL_CLOSED,
                                new TunnelClosedData(
                                        apnName, error, IwlanDataServiceProvider.this)));
            }
        }

        @VisibleForTesting
        class IwlanTunnelCallbackMetrics implements EpdgTunnelManager.TunnelCallbackMetrics {

            DataServiceProvider mDataServiceProvider;

            public IwlanTunnelCallbackMetrics(DataServiceProvider dsp) {
                mDataServiceProvider = dsp;
            }

            public void onOpened(
                    String apnName,
                    String epdgServerAddress,
                    int epdgServerSelectionDuration,
                    int ikeTunnelEstablishmentDuration) {
                mIwlanDataServiceHandler.sendMessage(
                        mIwlanDataServiceHandler.obtainMessage(
                                EVENT_TUNNEL_OPENED_METRICS,
                                new TunnelOpenedMetricsData(
                                        apnName,
                                        epdgServerAddress,
                                        System.currentTimeMillis() - mProcessingStartTime,
                                        epdgServerSelectionDuration,
                                        ikeTunnelEstablishmentDuration,
                                        IwlanDataServiceProvider.this)));
            }

            public void onClosed(
                    String apnName,
                    String epdgServerAddress,
                    int epdgServerSelectionDuration,
                    int ikeTunnelEstablishmentDuration) {
                mIwlanDataServiceHandler.sendMessage(
                        mIwlanDataServiceHandler.obtainMessage(
                                EVENT_TUNNEL_CLOSED_METRICS,
                                new TunnelClosedMetricsData(
                                        apnName,
                                        epdgServerAddress,
                                        mProcessingStartTime > 0
                                                ? System.currentTimeMillis() - mProcessingStartTime
                                                : 0,
                                        epdgServerSelectionDuration,
                                        ikeTunnelEstablishmentDuration,
                                        IwlanDataServiceProvider.this)));
            }
        }

        /** Holds all tunnel related time and count statistics for this IwlanDataServiceProvider */
        @VisibleForTesting
        class IwlanDataTunnelStats {

            // represents the start time from when the following events are recorded
            private Date mStartTime;

            // Stats for TunnelSetup Success time (BRING_UP -> UP state)
            @VisibleForTesting
            Map<String, LongSummaryStatistics> mTunnelSetupSuccessStats =
                    new HashMap<String, LongSummaryStatistics>();
            // Count for Tunnel Setup failures onClosed when in BRING_UP
            @VisibleForTesting
            Map<String, Long> mTunnelSetupFailureCounts = new HashMap<String, Long>();

            // Count for unsol tunnel down onClosed when in UP without deactivate
            @VisibleForTesting
            Map<String, Long> mUnsolTunnelDownCounts = new HashMap<String, Long>();

            // Stats for how long the tunnel is in up state onClosed when in UP
            @VisibleForTesting
            Map<String, LongSummaryStatistics> mTunnelUpStats =
                    new HashMap<String, LongSummaryStatistics>();

            private long statCount;
            private final long COUNT_MAX = 1000;
            private final int APN_COUNT_MAX = 10;

            public IwlanDataTunnelStats() {
                mStartTime = Calendar.getInstance().getTime();
                statCount = 0L;
            }

            public void reportTunnelSetupSuccess(String apn, TunnelState tunnelState) {
                if (statCount > COUNT_MAX || maxApnReached()) {
                    reset();
                }
                statCount++;

                Date bringUpTime = tunnelState.getBringUpStateTime();
                Date upTime = tunnelState.getUpStateTime();

                if (bringUpTime != null && upTime != null) {
                    long tunnelUpTime = upTime.getTime() - bringUpTime.getTime();
                    if (!mTunnelSetupSuccessStats.containsKey(apn)) {
                        mTunnelSetupSuccessStats.put(apn, new LongSummaryStatistics());
                    }
                    LongSummaryStatistics stats = mTunnelSetupSuccessStats.get(apn);
                    stats.accept(tunnelUpTime);
                    mTunnelSetupSuccessStats.put(apn, stats);
                }
            }

            public void reportTunnelDown(String apn, TunnelState tunnelState) {
                if (statCount > COUNT_MAX || maxApnReached()) {
                    reset();
                }
                statCount++;

                // Setup fail
                if (tunnelState.getState() == TunnelState.TUNNEL_IN_BRINGUP) {
                    if (!mTunnelSetupFailureCounts.containsKey(apn)) {
                        mTunnelSetupFailureCounts.put(apn, 0L);
                    }
                    long count = mTunnelSetupFailureCounts.get(apn);
                    mTunnelSetupFailureCounts.put(apn, ++count);
                    return;
                }

                // Unsolicited tunnel down as tunnel has to be in BRINGDOWN if
                // there is a deactivate call associated with this.
                if (tunnelState.getState() == TunnelState.TUNNEL_UP) {
                    if (!mUnsolTunnelDownCounts.containsKey(apn)) {
                        mUnsolTunnelDownCounts.put(apn, 0L);
                    }
                    long count = mUnsolTunnelDownCounts.get(apn);
                    mUnsolTunnelDownCounts.put(apn, ++count);
                }
                Date currentTime = Calendar.getInstance().getTime();
                Date upTime = tunnelState.getUpStateTime();
                if (upTime != null) {
                    if (!mTunnelUpStats.containsKey(apn)) {
                        mTunnelUpStats.put(apn, new LongSummaryStatistics());
                    }
                    LongSummaryStatistics stats = mTunnelUpStats.get(apn);
                    stats.accept(currentTime.getTime() - upTime.getTime());
                    mTunnelUpStats.put(apn, stats);
                }
            }

            boolean maxApnReached() {
                if (mTunnelSetupSuccessStats.size() >= APN_COUNT_MAX
                        || mTunnelSetupFailureCounts.size() >= APN_COUNT_MAX
                        || mUnsolTunnelDownCounts.size() >= APN_COUNT_MAX
                        || mTunnelUpStats.size() >= APN_COUNT_MAX) {
                    return true;
                }
                return false;
            }

            @Override
            public String toString() {
                StringBuilder sb = new StringBuilder();
                sb.append("IwlanDataTunnelStats:");
                sb.append("\n\tmStartTime: " + mStartTime);
                sb.append("\n\ttunnelSetupSuccessStats:");
                for (Map.Entry<String, LongSummaryStatistics> entry :
                        mTunnelSetupSuccessStats.entrySet()) {
                    sb.append("\n\t  Apn: " + entry.getKey());
                    sb.append("\n\t  " + entry.getValue());
                }
                sb.append("\n\ttunnelUpStats:");
                for (Map.Entry<String, LongSummaryStatistics> entry : mTunnelUpStats.entrySet()) {
                    sb.append("\n\t  Apn: " + entry.getKey());
                    sb.append("\n\t  " + entry.getValue());
                }

                sb.append("\n\ttunnelSetupFailureCounts: ");
                for (Map.Entry<String, Long> entry : mTunnelSetupFailureCounts.entrySet()) {
                    sb.append("\n\t  Apn: " + entry.getKey());
                    sb.append("\n\t  counts: " + entry.getValue());
                }
                sb.append("\n\tunsolTunnelDownCounts: ");
                for (Map.Entry<String, Long> entry : mTunnelSetupFailureCounts.entrySet()) {
                    sb.append("\n\t  Apn: " + entry.getKey());
                    sb.append("\n\t  counts: " + entry.getValue());
                }
                sb.append("\n\tendTime: " + Calendar.getInstance().getTime());
                return sb.toString();
            }

            private void reset() {
                mStartTime = Calendar.getInstance().getTime();
                mTunnelSetupSuccessStats = new HashMap<String, LongSummaryStatistics>();
                mTunnelUpStats = new HashMap<String, LongSummaryStatistics>();
                mTunnelSetupFailureCounts = new HashMap<String, Long>();
                mUnsolTunnelDownCounts = new HashMap<String, Long>();
                statCount = 0L;
            }
        }

        /**
         * Constructor
         *
         * @param slotIndex SIM slot index the data service provider associated with.
         */
        public IwlanDataServiceProvider(int slotIndex, IwlanDataService iwlanDataService) {
            super(slotIndex);
            SUB_TAG = TAG + "[" + slotIndex + "]";

            // TODO:
            // get reference carrier config for this sub
            // get reference to resolver
            mIwlanDataService = iwlanDataService;
            mIwlanTunnelCallback = new IwlanTunnelCallback(this);
            mIwlanTunnelCallbackMetrics = new IwlanTunnelCallbackMetrics(this);
            mEpdgSelector = EpdgSelector.getSelectorInstance(mContext, slotIndex);
            mTunnelStats = new IwlanDataTunnelStats();

            // Register IwlanEventListener
            List<Integer> events = new ArrayList<Integer>();
            events.add(IwlanEventListener.CARRIER_CONFIG_CHANGED_EVENT);
            events.add(IwlanEventListener.CARRIER_CONFIG_UNKNOWN_CARRIER_EVENT);
            events.add(IwlanEventListener.WIFI_CALLING_ENABLE_EVENT);
            events.add(IwlanEventListener.WIFI_CALLING_DISABLE_EVENT);
            events.add(IwlanEventListener.CELLINFO_CHANGED_EVENT);
            IwlanEventListener.getInstance(mContext, slotIndex)
                    .addEventListener(events, mIwlanDataServiceHandler);
        }

        @VisibleForTesting
        EpdgTunnelManager getTunnelManager() {
            return EpdgTunnelManager.getInstance(mContext, getSlotIndex());
        }

        // creates a DataCallResponse for an apn irrespective of state
        private DataCallResponse apnTunnelStateToDataCallResponse(String apn) {
            TunnelState tunnelState = mTunnelStateForApn.get(apn);
            if (tunnelState == null) {
                return null;
            }

            DataCallResponse.Builder responseBuilder = new DataCallResponse.Builder();
            responseBuilder
                    .setId(apn.hashCode())
                    .setProtocolType(tunnelState.getProtocolType())
                    .setCause(DataFailCause.NONE);

            responseBuilder.setLinkStatus(DataCallResponse.LINK_STATUS_INACTIVE);
            int state = tunnelState.getState();

            if (state == TunnelState.TUNNEL_UP) {
                responseBuilder.setLinkStatus(DataCallResponse.LINK_STATUS_ACTIVE);
            }

            TunnelLinkProperties tunnelLinkProperties = tunnelState.getTunnelLinkProperties();
            if (tunnelLinkProperties == null) {
                Log.d(TAG, "PDN with empty linkproperties. TunnelState : " + state);
                return responseBuilder.build();
            }

            // fill wildcard address for gatewayList (used by DataConnection to add routes)
            List<InetAddress> gatewayList = new ArrayList<>();
            List<LinkAddress> linkAddrList = tunnelLinkProperties.internalAddresses();
            if (linkAddrList.stream().anyMatch(t -> t.isIpv4())) {
                try {
                    gatewayList.add(Inet4Address.getByName("0.0.0.0"));
                } catch (UnknownHostException e) {
                    // should never happen for static string 0.0.0.0
                }
            }
            if (linkAddrList.stream().anyMatch(t -> t.isIpv6())) {
                try {
                    gatewayList.add(Inet6Address.getByName("::"));
                } catch (UnknownHostException e) {
                    // should never happen for static string ::
                }
            }

            if (tunnelLinkProperties.sliceInfo().isPresent()) {
                responseBuilder.setSliceInfo(tunnelLinkProperties.sliceInfo().get());
            }

            return responseBuilder
                    .setAddresses(linkAddrList)
                    .setDnsAddresses(tunnelLinkProperties.dnsAddresses())
                    .setPcscfAddresses(tunnelLinkProperties.pcscfAddresses())
                    .setInterfaceName(tunnelLinkProperties.ifaceName())
                    .setGatewayAddresses(gatewayList)
                    .setMtu(tunnelState.getLinkMtu())
                    .setMtuV4(tunnelState.getLinkMtu())
                    .setMtuV6(tunnelState.getLinkMtu())
                    .setPduSessionId(tunnelState.getPduSessionId())
                    .build(); // underlying n/w is same
        }

        private List<DataCallResponse> getCallList() {
            List<DataCallResponse> dcList = new ArrayList<>();
            for (String key : mTunnelStateForApn.keySet()) {
                DataCallResponse dcRsp = apnTunnelStateToDataCallResponse(key);
                if (dcRsp != null) {
                    Log.d(SUB_TAG, "Apn: " + key + "Link state: " + dcRsp.getLinkStatus());
                    dcList.add(dcRsp);
                }
            }
            return dcList;
        }

        private void deliverCallback(
                int callbackType, int result, DataServiceCallback callback, DataCallResponse rsp) {
            if (callback == null) {
                Log.d(SUB_TAG, "deliverCallback: callback is null.  callbackType:" + callbackType);
                return;
            }
            Log.d(
                    SUB_TAG,
                    "Delivering callbackType:"
                            + callbackType
                            + " result:"
                            + result
                            + " rsp:"
                            + rsp);
            switch (callbackType) {
                case CALLBACK_TYPE_DEACTIVATE_DATACALL_COMPLETE:
                    callback.onDeactivateDataCallComplete(result);
                    // always update current datacalllist
                    notifyDataCallListChanged(getCallList());
                    break;

                case CALLBACK_TYPE_SETUP_DATACALL_COMPLETE:
                    if (result == DataServiceCallback.RESULT_SUCCESS && rsp == null) {
                        Log.d(SUB_TAG, "Warning: null rsp for success case");
                    }
                    callback.onSetupDataCallComplete(result, rsp);
                    // always update current datacalllist
                    notifyDataCallListChanged(getCallList());
                    break;

                case CALLBACK_TYPE_GET_DATACALL_LIST_COMPLETE:
                    callback.onRequestDataCallListComplete(result, getCallList());
                    // TODO: add code for the rest of the cases
            }
        }

        /**
         * Setup a data connection.
         *
         * @param accessNetworkType Access network type that the data call will be established on.
         *     Must be one of {@link android.telephony.AccessNetworkConstants.AccessNetworkType}.
         * @param dataProfile Data profile used for data call setup. See {@link DataProfile}
         * @param isRoaming True if the device is data roaming.
         * @param allowRoaming True if data roaming is allowed by the user.
         * @param reason The reason for data setup. Must be {@link #REQUEST_REASON_NORMAL} or {@link
         *     #REQUEST_REASON_HANDOVER}.
         * @param linkProperties If {@code reason} is {@link #REQUEST_REASON_HANDOVER}, this is the
         *     link properties of the existing data connection, otherwise null.
         * @param pduSessionId The pdu session id to be used for this data call. The standard range
         *     of values are 1-15 while 0 means no pdu session id was attached to this call.
         *     Reference: 3GPP TS 24.007 section 11.2.3.1b.
         * @param sliceInfo The slice info related to this data call.
         * @param trafficDescriptor TrafficDescriptor for which data connection needs to be
         *     established. It is used for URSP traffic matching as described in 3GPP TS 24.526
         *     Section 4.2.2. It includes an optional DNN which, if present, must be used for
         *     traffic matching; it does not specify the end point to be used for the data call.
         * @param matchAllRuleAllowed Indicates if using default match-all URSP rule for this
         *     request is allowed. If false, this request must not use the match-all URSP rule and
         *     if a non-match-all rule is not found (or if URSP rules are not available) then {@link
         *     DataCallResponse#getCause()} is {@link
         *     android.telephony.DataFailCause#MATCH_ALL_RULE_NOT_ALLOWED}. This is needed as some
         *     requests need to have a hard failure if the intention cannot be met, for example, a
         *     zero-rating slice.
         * @param callback The result callback for this request.
         */
        @Override
        public void setupDataCall(
                int accessNetworkType,
                @NonNull DataProfile dataProfile,
                boolean isRoaming,
                boolean allowRoaming,
                int reason,
                @Nullable LinkProperties linkProperties,
                @IntRange(from = 0, to = 15) int pduSessionId,
                @Nullable NetworkSliceInfo sliceInfo,
                @Nullable TrafficDescriptor trafficDescriptor,
                boolean matchAllRuleAllowed,
                @NonNull DataServiceCallback callback) {

            mProcessingStartTime = System.currentTimeMillis();
            Log.d(
                    SUB_TAG,
                    "Setup data call with network: "
                            + accessNetworkType
                            + ", DataProfile: "
                            + dataProfile
                            + ", isRoaming:"
                            + isRoaming
                            + ", allowRoaming: "
                            + allowRoaming
                            + ", reason: "
                            + reason
                            + ", linkProperties: "
                            + linkProperties
                            + ", pduSessionId: "
                            + pduSessionId);

            SetupDataCallData setupDataCallData =
                    new SetupDataCallData(
                            accessNetworkType,
                            dataProfile,
                            isRoaming,
                            allowRoaming,
                            reason,
                            linkProperties,
                            pduSessionId,
                            sliceInfo,
                            trafficDescriptor,
                            matchAllRuleAllowed,
                            callback,
                            this);

            int networkTransport = -1;
            if (sDefaultDataTransport == Transport.MOBILE) {
                networkTransport = TRANSPORT_CELLULAR;
            } else if (sDefaultDataTransport == Transport.MOBILE) {
                networkTransport = TRANSPORT_WIFI;
            }

            if (dataProfile != null) {
                this.setMetricsAtom(
                        // ApnName
                        dataProfile.getApn(),
                        // ApnType
                        dataProfile.getSupportedApnTypesBitmask(),
                        // IsHandover
                        (reason == DataService.REQUEST_REASON_HANDOVER),
                        // Source Rat
                        getCurrentCellularRat(),
                        // IsRoaming
                        isRoaming,
                        // Is Network Connected
                        sNetworkConnected,
                        // Transport Type
                        networkTransport);
            }

            mIwlanDataServiceHandler.sendMessage(
                    mIwlanDataServiceHandler.obtainMessage(
                            EVENT_SETUP_DATA_CALL, setupDataCallData));
        }

        /**
         * Deactivate a data connection. The data service provider must implement this method to
         * support data connection tear down. When completed or error, the service must invoke the
         * provided callback to notify the platform.
         *
         * @param cid Call id returned in the callback of {@link
         *     DataServiceProvider#setupDataCall(int, DataProfile, boolean, boolean, int,
         *     LinkProperties, DataServiceCallback)}.
         * @param reason The reason for data deactivation. Must be {@link #REQUEST_REASON_NORMAL},
         *     {@link #REQUEST_REASON_SHUTDOWN} or {@link #REQUEST_REASON_HANDOVER}.
         * @param callback The result callback for this request. Null if the client does not care
         */
        @Override
        public void deactivateDataCall(int cid, int reason, DataServiceCallback callback) {
            Log.d(
                    SUB_TAG,
                    "Deactivate data call "
                            + " reason: "
                            + reason
                            + " cid: "
                            + cid
                            + "callback: "
                            + callback);

            DeactivateDataCallData deactivateDataCallData =
                    new DeactivateDataCallData(cid, reason, callback, this);

            mIwlanDataServiceHandler.sendMessage(
                    mIwlanDataServiceHandler.obtainMessage(
                            EVENT_DEACTIVATE_DATA_CALL, deactivateDataCallData));
        }

        public void forceCloseTunnelsInDeactivatingState() {
            for (Map.Entry<String, TunnelState> entry : mTunnelStateForApn.entrySet()) {
                TunnelState tunnelState = entry.getValue();
                if (tunnelState.getState() == TunnelState.TUNNEL_IN_BRINGDOWN) {
                    getTunnelManager().closeTunnel(entry.getKey(), true);
                }
            }
        }

        void forceCloseTunnels() {
            for (Map.Entry<String, TunnelState> entry : mTunnelStateForApn.entrySet()) {
                getTunnelManager().closeTunnel(entry.getKey(), true);
            }
        }

        /**
         * Get the active data call list.
         *
         * @param callback The result callback for this request.
         */
        @Override
        public void requestDataCallList(DataServiceCallback callback) {
            mIwlanDataServiceHandler.sendMessage(
                    mIwlanDataServiceHandler.obtainMessage(
                            EVENT_DATA_CALL_LIST_REQUEST,
                            new DataCallRequestData(callback, IwlanDataServiceProvider.this)));
        }

        @VisibleForTesting
        void setTunnelState(
                DataProfile dataProfile,
                DataServiceCallback callback,
                int tunnelStatus,
                TunnelLinkProperties linkProperties,
                boolean isHandover,
                int pduSessionId) {
            TunnelState tunnelState = new TunnelState(callback);
            tunnelState.setState(tunnelStatus);
            tunnelState.setProtocolType(dataProfile.getProtocolType());
            tunnelState.setTunnelLinkProperties(linkProperties);
            tunnelState.setIsHandover(isHandover);
            tunnelState.setPduSessionId(pduSessionId);
            mTunnelStateForApn.put(dataProfile.getApn(), tunnelState);
        }

        @VisibleForTesting
        void setMetricsAtom(
                String apnName,
                int apntype,
                boolean isHandover,
                int sourceRat,
                boolean isRoaming,
                boolean isNetworkConnected,
                int transportType) {
            MetricsAtom metricsAtom = new MetricsAtom();
            metricsAtom.setApnType(apntype);
            metricsAtom.setIsHandover(isHandover);
            metricsAtom.setSourceRat(sourceRat);
            metricsAtom.setIsCellularRoaming(isRoaming);
            metricsAtom.setIsNetworkConnected(isNetworkConnected);
            metricsAtom.setTransportType(transportType);
            mMetricsAtomForApn.put(apnName, metricsAtom);
        }

        @VisibleForTesting
        @Nullable
        public MetricsAtom getMetricsAtomByApn(String apnName) {
            return mMetricsAtomForApn.get(apnName);
        }

        @VisibleForTesting
        public IwlanTunnelCallback getIwlanTunnelCallback() {
            return mIwlanTunnelCallback;
        }

        @VisibleForTesting
        public IwlanTunnelCallbackMetrics getIwlanTunnelCallbackMetrics() {
            return mIwlanTunnelCallbackMetrics;
        }

        @VisibleForTesting
        IwlanDataTunnelStats getTunnelStats() {
            return mTunnelStats;
        }

        private void updateNetwork(Network network) {
            if (network != null) {
                for (Map.Entry<String, TunnelState> entry : mTunnelStateForApn.entrySet()) {
                    TunnelState tunnelState = entry.getValue();
                    if (tunnelState.getState() == TunnelState.TUNNEL_IN_BRINGUP) {
                        // force close tunnels in bringup since IKE lib only supports
                        // updating network for tunnels that are already up.
                        // This may not result in actual closing of Ike Session since
                        // epdg selection may not be complete yet.
                        tunnelState.setState(TunnelState.TUNNEL_IN_FORCE_CLEAN_WAS_IN_BRINGUP);
                        getTunnelManager().closeTunnel(entry.getKey(), true);
                    } else {
                        if (mIwlanDataService.isNetworkConnected(
                                IwlanHelper.isDefaultDataSlot(mContext, getSlotIndex()),
                                IwlanHelper.isCrossSimCallingEnabled(mContext, getSlotIndex()))) {
                            getTunnelManager().updateNetwork(network, entry.getKey());
                        }
                    }
                }
            }
        }

        private boolean isRegisteredCellInfoChanged(List<CellInfo> cellInfoList) {
            for (CellInfo cellInfo : cellInfoList) {
                if (!cellInfo.isRegistered()) {
                    continue;
                }

                if (mCellInfo == null || mCellInfo != cellInfo) {
                    mCellInfo = cellInfo;
                    Log.d(TAG, " Update cached cellinfo");
                    return true;
                }
            }
            return false;
        }

        private void dnsPrefetchCheck() {
            boolean networkConnected =
                    mIwlanDataService.isNetworkConnected(
                            IwlanHelper.isDefaultDataSlot(mContext, getSlotIndex()),
                            IwlanHelper.isCrossSimCallingEnabled(mContext, getSlotIndex()));
            /* Check if we need to do prefecting */
            if (networkConnected == true
                    && mCarrierConfigReady == true
                    && mWfcEnabled == true
                    && mTunnelStateForApn.isEmpty()) {

                // Get roaming status
                TelephonyManager telephonyManager =
                        mContext.getSystemService(TelephonyManager.class);
                telephonyManager =
                        telephonyManager.createForSubscriptionId(
                                IwlanHelper.getSubId(mContext, getSlotIndex()));
                boolean isRoaming = telephonyManager.isNetworkRoaming();
                Log.d(TAG, "Trigger EPDG prefetch. Roaming=" + isRoaming);

                prefetchEpdgServerList(mIwlanDataService.sNetwork, isRoaming);
            }
        }

        private void prefetchEpdgServerList(Network network, boolean isRoaming) {
            mEpdgSelector.getValidatedServerList(
                    0, EpdgSelector.PROTO_FILTER_IPV4V6, isRoaming, false, network, null);
            mEpdgSelector.getValidatedServerList(
                    0, EpdgSelector.PROTO_FILTER_IPV4V6, isRoaming, true, network, null);
        }

        private int getCurrentCellularRat() {
            TelephonyManager telephonyManager = mContext.getSystemService(TelephonyManager.class);
            telephonyManager =
                    telephonyManager.createForSubscriptionId(
                            IwlanHelper.getSubId(mContext, getSlotIndex()));
            List<CellInfo> cellInfoList = telephonyManager.getAllCellInfo();
            if (cellInfoList == null) {
                Log.e(TAG, "cellInfoList is NULL");
                return 0;
            }

            for (CellInfo cellInfo : cellInfoList) {
                if (!cellInfo.isRegistered()) {
                    continue;
                }
                if (cellInfo instanceof CellInfoGsm) {
                    return TelephonyManager.NETWORK_TYPE_GSM;
                } else if (cellInfo instanceof CellInfoWcdma) {
                    return TelephonyManager.NETWORK_TYPE_UMTS;
                } else if (cellInfo instanceof CellInfoLte) {
                    return TelephonyManager.NETWORK_TYPE_LTE;
                } else if (cellInfo instanceof CellInfoNr) {
                    return TelephonyManager.NETWORK_TYPE_NR;
                }
            }
            return TelephonyManager.NETWORK_TYPE_UNKNOWN;
        }

        /**
         * Called when the instance of data service is destroyed (e.g. got unbind or binder died) or
         * when the data service provider is removed.
         */
        @Override
        public void close() {
            // TODO: call epdgtunnelmanager.releaseInstance or equivalent
            mIwlanDataService.removeDataServiceProvider(this);
            IwlanEventListener.getInstance(mContext, getSlotIndex())
                    .removeEventListener(mIwlanDataServiceHandler);
        }

        public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
            pw.println("---- IwlanDataServiceProvider[" + getSlotIndex() + "] ----");
            boolean isDDS = IwlanHelper.isDefaultDataSlot(mContext, getSlotIndex());
            boolean isCSTEnabled = IwlanHelper.isCrossSimCallingEnabled(mContext, getSlotIndex());
            pw.println("isDefaultDataSub: " + isDDS + " isCrossSimEnabled: " + isCSTEnabled);
            pw.println(
                    "isNetworkConnected: "
                            + isNetworkConnected(isDDS, isCSTEnabled)
                            + " Wfc enabled: "
                            + mWfcEnabled);
            for (Map.Entry<String, TunnelState> entry : mTunnelStateForApn.entrySet()) {
                pw.println("Tunnel state for APN: " + entry.getKey());
                pw.println(entry.getValue());
            }
            pw.println(mTunnelStats);
            EpdgTunnelManager.getInstance(mContext, getSlotIndex()).dump(fd, pw, args);
            ErrorPolicyManager.getInstance(mContext, getSlotIndex()).dump(fd, pw, args);
            pw.println("-------------------------------------");
        }
    }

    private final class IwlanDataServiceHandler extends Handler {
        private final String TAG = IwlanDataServiceHandler.class.getSimpleName();

        @Override
        public void handleMessage(Message msg) {
            Log.d(TAG, "msg.what = " + eventToString(msg.what));

            String apnName;
            IwlanDataServiceProvider iwlanDataServiceProvider;
            IwlanDataServiceProvider.TunnelState tunnelState;
            DataServiceCallback callback;
            int reason;
            int retryTimeMillis;
            int errorCause;
            MetricsAtom metricsAtom;

            switch (msg.what) {
                case EVENT_TUNNEL_OPENED:
                    TunnelOpenedData tunnelOpenedData = (TunnelOpenedData) msg.obj;
                    iwlanDataServiceProvider = tunnelOpenedData.mIwlanDataServiceProvider;
                    apnName = tunnelOpenedData.mApnName;
                    TunnelLinkProperties tunnelLinkProperties =
                            tunnelOpenedData.mTunnelLinkProperties;

                    tunnelState = iwlanDataServiceProvider.mTunnelStateForApn.get(apnName);
                    // tunnelstate should not be null, design violation.
                    // if its null, we should crash and debug.
                    tunnelState.setTunnelLinkProperties(tunnelLinkProperties);
                    tunnelState.setState(IwlanDataServiceProvider.TunnelState.TUNNEL_UP);
                    iwlanDataServiceProvider.mTunnelStats.reportTunnelSetupSuccess(
                            apnName, tunnelState);

                    // Record setup result for the Metrics
                    metricsAtom = iwlanDataServiceProvider.mMetricsAtomForApn.get(apnName);
                    metricsAtom.setSetupRequestResult(DataServiceCallback.RESULT_SUCCESS);
                    metricsAtom.setIwlanError(IwlanError.NO_ERROR);
                    metricsAtom.setDataCallFailCause(DataFailCause.NONE);
                    metricsAtom.setTunnelState(tunnelState.getState());
                    metricsAtom.setHandoverFailureMode(-1);
                    metricsAtom.setRetryDurationMillis(0);
                    metricsAtom.setMessageId(IwlanStatsLog.IWLAN_SETUP_DATA_CALL_RESULT_REPORTED);

                    iwlanDataServiceProvider.deliverCallback(
                            IwlanDataServiceProvider.CALLBACK_TYPE_SETUP_DATACALL_COMPLETE,
                            DataServiceCallback.RESULT_SUCCESS,
                            tunnelState.getDataServiceCallback(),
                            iwlanDataServiceProvider.apnTunnelStateToDataCallResponse(apnName));
                    break;

                case EVENT_TUNNEL_CLOSED:
                    TunnelClosedData tunnelClosedData = (TunnelClosedData) msg.obj;
                    iwlanDataServiceProvider = tunnelClosedData.mIwlanDataServiceProvider;
                    apnName = tunnelClosedData.mApnName;
                    IwlanError iwlanError = tunnelClosedData.mIwlanError;

                    tunnelState = iwlanDataServiceProvider.mTunnelStateForApn.get(apnName);
                    iwlanDataServiceProvider.mTunnelStats.reportTunnelDown(apnName, tunnelState);
                    iwlanDataServiceProvider.mTunnelStateForApn.remove(apnName);
                    metricsAtom = iwlanDataServiceProvider.mMetricsAtomForApn.get(apnName);

                    if (tunnelState.getState()
                                    == IwlanDataServiceProvider.TunnelState.TUNNEL_IN_BRINGUP
                            || tunnelState.getState()
                                    == IwlanDataServiceProvider.TunnelState
                                            .TUNNEL_IN_FORCE_CLEAN_WAS_IN_BRINGUP) {
                        DataCallResponse.Builder respBuilder = new DataCallResponse.Builder();
                        respBuilder
                                .setId(apnName.hashCode())
                                .setProtocolType(tunnelState.getProtocolType());

                        if (tunnelState.getIsHandover()) {
                            respBuilder.setHandoverFailureMode(
                                    DataCallResponse
                                            .HANDOVER_FAILURE_MODE_NO_FALLBACK_RETRY_HANDOVER);
                            metricsAtom.setHandoverFailureMode(
                                    DataCallResponse
                                            .HANDOVER_FAILURE_MODE_NO_FALLBACK_RETRY_HANDOVER);
                        } else {
                            respBuilder.setHandoverFailureMode(
                                    DataCallResponse
                                            .HANDOVER_FAILURE_MODE_NO_FALLBACK_RETRY_SETUP_NORMAL);
                            metricsAtom.setHandoverFailureMode(
                                    DataCallResponse
                                            .HANDOVER_FAILURE_MODE_NO_FALLBACK_RETRY_SETUP_NORMAL);
                        }

                        if (tunnelState.getState()
                                == IwlanDataServiceProvider.TunnelState.TUNNEL_IN_BRINGUP) {
                            errorCause =
                                    ErrorPolicyManager.getInstance(
                                                    mContext,
                                                    iwlanDataServiceProvider.getSlotIndex())
                                            .getDataFailCause(apnName);
                            respBuilder.setCause(errorCause);
                            metricsAtom.setDataCallFailCause(errorCause);

                            retryTimeMillis =
                                    (int)
                                            ErrorPolicyManager.getInstance(
                                                            mContext,
                                                            iwlanDataServiceProvider.getSlotIndex())
                                                    .getCurrentRetryTimeMs(apnName);
                            respBuilder.setRetryDurationMillis(retryTimeMillis);
                            metricsAtom.setRetryDurationMillis(retryTimeMillis);

                        } else if (tunnelState.getState()
                                == IwlanDataServiceProvider.TunnelState
                                        .TUNNEL_IN_FORCE_CLEAN_WAS_IN_BRINGUP) {
                            respBuilder.setCause(DataFailCause.IWLAN_NETWORK_FAILURE);
                            metricsAtom.setDataCallFailCause(DataFailCause.IWLAN_NETWORK_FAILURE);
                            respBuilder.setRetryDurationMillis(5000);
                            metricsAtom.setRetryDurationMillis(5000);
                        }

                        // Record setup result for the Metrics
                        metricsAtom.setSetupRequestResult(DataServiceCallback.RESULT_SUCCESS);
                        metricsAtom.setIwlanError(iwlanError.getErrorType());

                        metricsAtom.setIwlanErrorWrappedClassnameAndStack(iwlanError);

                        metricsAtom.setTunnelState(tunnelState.getState());
                        metricsAtom.setMessageId(
                                IwlanStatsLog.IWLAN_SETUP_DATA_CALL_RESULT_REPORTED);

                        iwlanDataServiceProvider.deliverCallback(
                                IwlanDataServiceProvider.CALLBACK_TYPE_SETUP_DATACALL_COMPLETE,
                                DataServiceCallback.RESULT_SUCCESS,
                                tunnelState.getDataServiceCallback(),
                                respBuilder.build());
                        return;
                    }

                    // iwlan service triggered teardown
                    if (tunnelState.getState()
                            == IwlanDataServiceProvider.TunnelState.TUNNEL_IN_BRINGDOWN) {

                        // IO exception happens when IKE library fails to retransmit requests.
                        // This can happen for multiple reasons:
                        // 1. Network disconnection due to wifi off.
                        // 2. Epdg server does not respond.
                        // 3. Socket send/receive fails.
                        // Ignore this during tunnel bring down.
                        if (iwlanError.getErrorType() != IwlanError.NO_ERROR
                                && iwlanError.getErrorType()
                                        != IwlanError.IKE_INTERNAL_IO_EXCEPTION) {
                            Log.e(TAG, "Unexpected error during tunnel bring down: " + iwlanError);
                        }

                        iwlanDataServiceProvider.deliverCallback(
                                IwlanDataServiceProvider.CALLBACK_TYPE_DEACTIVATE_DATACALL_COMPLETE,
                                DataServiceCallback.RESULT_SUCCESS,
                                tunnelState.getDataServiceCallback(),
                                null);

                        return;
                    }

                    // just update list of data calls. No way to send error up
                    iwlanDataServiceProvider.notifyDataCallListChanged(
                            iwlanDataServiceProvider.getCallList());

                    // Report IwlanPdnDisconnectedReason due to the disconnection is neither for
                    // SETUP_DATA_CALL nor DEACTIVATE_DATA_CALL request.
                    metricsAtom.setDataCallFailCause(
                            ErrorPolicyManager.getInstance(
                                            mContext, iwlanDataServiceProvider.getSlotIndex())
                                    .getDataFailCause(apnName));

                    WifiManager wifiManager = mContext.getSystemService(WifiManager.class);
                    if (wifiManager == null) {
                        Log.e(TAG, "Could not find wifiManager");
                        return;
                    }

                    WifiInfo wifiInfo = wifiManager.getConnectionInfo();
                    if (wifiInfo == null) {
                        Log.e(TAG, "wifiInfo is null");
                        return;
                    }

                    metricsAtom.setWifiSignalValue(wifiInfo.getRssi());
                    metricsAtom.setMessageId(IwlanStatsLog.IWLAN_PDN_DISCONNECTED_REASON_REPORTED);
                    break;

                case IwlanEventListener.CARRIER_CONFIG_CHANGED_EVENT:
                    iwlanDataServiceProvider =
                            (IwlanDataServiceProvider) getDataServiceProvider(msg.arg1);

                    iwlanDataServiceProvider.mCarrierConfigReady = true;
                    iwlanDataServiceProvider.dnsPrefetchCheck();
                    break;

                case IwlanEventListener.CARRIER_CONFIG_UNKNOWN_CARRIER_EVENT:
                    iwlanDataServiceProvider =
                            (IwlanDataServiceProvider) getDataServiceProvider(msg.arg1);

                    iwlanDataServiceProvider.mCarrierConfigReady = false;
                    break;

                case IwlanEventListener.WIFI_CALLING_ENABLE_EVENT:
                    iwlanDataServiceProvider =
                            (IwlanDataServiceProvider) getDataServiceProvider(msg.arg1);

                    iwlanDataServiceProvider.mWfcEnabled = true;
                    iwlanDataServiceProvider.dnsPrefetchCheck();
                    break;

                case IwlanEventListener.WIFI_CALLING_DISABLE_EVENT:
                    iwlanDataServiceProvider =
                            (IwlanDataServiceProvider) getDataServiceProvider(msg.arg1);

                    iwlanDataServiceProvider.mWfcEnabled = false;
                    break;

                case IwlanEventListener.CELLINFO_CHANGED_EVENT:
                    List<CellInfo> cellInfolist = (List<CellInfo>) msg.obj;
                    iwlanDataServiceProvider =
                            (IwlanDataServiceProvider) getDataServiceProvider(msg.arg1);

                    if (cellInfolist != null
                            && iwlanDataServiceProvider.isRegisteredCellInfoChanged(cellInfolist)) {
                        int[] addrResolutionMethods =
                                IwlanHelper.getConfig(
                                        CarrierConfigManager.Iwlan
                                                .KEY_EPDG_ADDRESS_PRIORITY_INT_ARRAY,
                                        mContext,
                                        iwlanDataServiceProvider.getSlotIndex());
                        for (int addrResolutionMethod : addrResolutionMethods) {
                            if (addrResolutionMethod
                                    == CarrierConfigManager.Iwlan.EPDG_ADDRESS_CELLULAR_LOC) {
                                iwlanDataServiceProvider.dnsPrefetchCheck();
                            }
                        }
                    }
                    break;

                case EVENT_SETUP_DATA_CALL:
                    SetupDataCallData setupDataCallData = (SetupDataCallData) msg.obj;
                    int accessNetworkType = setupDataCallData.mAccessNetworkType;
                    @NonNull DataProfile dataProfile = setupDataCallData.mDataProfile;
                    boolean isRoaming = setupDataCallData.mIsRoaming;
                    reason = setupDataCallData.mReason;
                    LinkProperties linkProperties = setupDataCallData.mLinkProperties;
                    @IntRange(from = 0, to = 15)
                    int pduSessionId = setupDataCallData.mPduSessionId;
                    callback = setupDataCallData.mCallback;
                    iwlanDataServiceProvider = setupDataCallData.mIwlanDataServiceProvider;

                    if ((accessNetworkType != AccessNetworkType.IWLAN)
                            || (dataProfile == null)
                            || (linkProperties == null
                                    && reason == DataService.REQUEST_REASON_HANDOVER)) {

                        iwlanDataServiceProvider.deliverCallback(
                                IwlanDataServiceProvider.CALLBACK_TYPE_SETUP_DATACALL_COMPLETE,
                                DataServiceCallback.RESULT_ERROR_INVALID_ARG,
                                callback,
                                null);
                        return;
                    }

                    boolean isDDS =
                            IwlanHelper.isDefaultDataSlot(
                                    mContext, iwlanDataServiceProvider.getSlotIndex());
                    boolean isCSTEnabled =
                            IwlanHelper.isCrossSimCallingEnabled(
                                    mContext, iwlanDataServiceProvider.getSlotIndex());
                    boolean networkConnected = isNetworkConnected(isDDS, isCSTEnabled);
                    Log.d(
                            TAG,
                            "isDds: "
                                    + isDDS
                                    + ", isCstEnabled: "
                                    + isCSTEnabled
                                    + ", transport: "
                                    + sDefaultDataTransport);

                    if (networkConnected == false) {
                        iwlanDataServiceProvider.deliverCallback(
                                IwlanDataServiceProvider.CALLBACK_TYPE_SETUP_DATACALL_COMPLETE,
                                5 /* DataServiceCallback.RESULT_ERROR_TEMPORARILY_UNAVAILABLE
                                   */,
                                callback,
                                null);
                        return;
                    }

                    tunnelState =
                            iwlanDataServiceProvider.mTunnelStateForApn.get(dataProfile.getApn());

                    // Return the existing PDN if the pduSessionId is the same and the tunnel
                    // state is
                    // TUNNEL_UP.
                    if (tunnelState != null) {
                        if (tunnelState.getPduSessionId() == pduSessionId
                                && tunnelState.getState()
                                        == IwlanDataServiceProvider.TunnelState.TUNNEL_UP) {
                            Log.w(
                                    TAG,
                                    "The tunnel for " + dataProfile.getApn() + " already exists.");
                            iwlanDataServiceProvider.deliverCallback(
                                    IwlanDataServiceProvider.CALLBACK_TYPE_SETUP_DATACALL_COMPLETE,
                                    DataServiceCallback.RESULT_SUCCESS,
                                    callback,
                                    iwlanDataServiceProvider.apnTunnelStateToDataCallResponse(
                                            dataProfile.getApn()));
                            return;
                        } else {
                            Log.e(
                                    TAG,
                                    "Force close the existing PDN. pduSessionId = "
                                            + tunnelState.getPduSessionId()
                                            + " Tunnel State = "
                                            + tunnelState.getState());
                            iwlanDataServiceProvider
                                    .getTunnelManager()
                                    .closeTunnel(dataProfile.getApn(), true /* forceClose */);
                            iwlanDataServiceProvider.deliverCallback(
                                    IwlanDataServiceProvider.CALLBACK_TYPE_SETUP_DATACALL_COMPLETE,
                                    5 /* DataServiceCallback
                                      .RESULT_ERROR_TEMPORARILY_UNAVAILABLE */,
                                    callback,
                                    null);
                            return;
                        }
                    }

                    TunnelSetupRequest.Builder tunnelReqBuilder =
                            TunnelSetupRequest.builder()
                                    .setApnName(dataProfile.getApn())
                                    .setNetwork(sNetwork)
                                    .setIsRoaming(isRoaming)
                                    .setPduSessionId(pduSessionId)
                                    .setApnIpProtocol(
                                            isRoaming
                                                    ? dataProfile.getRoamingProtocolType()
                                                    : dataProfile.getProtocolType());

                    if (reason == DataService.REQUEST_REASON_HANDOVER) {
                        // for now assume that, at max,  only one address of eachtype (v4/v6).
                        // TODO: Check if multiple ips can be sent in ike tunnel setup
                        for (LinkAddress lAddr : linkProperties.getLinkAddresses()) {
                            if (lAddr.isIpv4()) {
                                tunnelReqBuilder.setSrcIpv4Address(lAddr.getAddress());
                            } else if (lAddr.isIpv6()) {
                                tunnelReqBuilder.setSrcIpv6Address(lAddr.getAddress());
                                tunnelReqBuilder.setSrcIpv6AddressPrefixLength(
                                        lAddr.getPrefixLength());
                            }
                        }
                    }

                    int apnTypeBitmask = dataProfile.getSupportedApnTypesBitmask();
                    boolean isIMS = (apnTypeBitmask & ApnSetting.TYPE_IMS) == ApnSetting.TYPE_IMS;
                    boolean isEmergency =
                            (apnTypeBitmask & ApnSetting.TYPE_EMERGENCY)
                                    == ApnSetting.TYPE_EMERGENCY;
                    tunnelReqBuilder.setRequestPcscf(isIMS || isEmergency);
                    tunnelReqBuilder.setIsEmergency(isEmergency);

                    iwlanDataServiceProvider.setTunnelState(
                            dataProfile,
                            callback,
                            IwlanDataServiceProvider.TunnelState.TUNNEL_IN_BRINGUP,
                            null,
                            (reason == DataService.REQUEST_REASON_HANDOVER),
                            pduSessionId);

                    boolean result =
                            iwlanDataServiceProvider
                                    .getTunnelManager()
                                    .bringUpTunnel(
                                            tunnelReqBuilder.build(),
                                            iwlanDataServiceProvider.getIwlanTunnelCallback(),
                                            iwlanDataServiceProvider
                                                    .getIwlanTunnelCallbackMetrics());
                    Log.d(TAG, "bringup Tunnel with result:" + result);
                    if (!result) {
                        iwlanDataServiceProvider.deliverCallback(
                                IwlanDataServiceProvider.CALLBACK_TYPE_SETUP_DATACALL_COMPLETE,
                                DataServiceCallback.RESULT_ERROR_INVALID_ARG,
                                callback,
                                null);
                        return;
                    }
                    break;

                case EVENT_DEACTIVATE_DATA_CALL:
                    DeactivateDataCallData deactivateDataCallData =
                            (DeactivateDataCallData) msg.obj;
                    int cid = deactivateDataCallData.mCid;
                    reason = deactivateDataCallData.mReason;
                    callback = deactivateDataCallData.mCallback;
                    iwlanDataServiceProvider = deactivateDataCallData.mIwlanDataServiceProvider;

                    for (String tunnelApnName :
                            iwlanDataServiceProvider.mTunnelStateForApn.keySet()) {
                        if (tunnelApnName.hashCode() == cid) {
                            /*
                            No need to check state since dataconnection in framework serializes
                            setup and deactivate calls using callId/cid.
                            */
                            iwlanDataServiceProvider
                                    .mTunnelStateForApn
                                    .get(tunnelApnName)
                                    .setState(
                                            IwlanDataServiceProvider.TunnelState
                                                    .TUNNEL_IN_BRINGDOWN);
                            iwlanDataServiceProvider
                                    .mTunnelStateForApn
                                    .get(tunnelApnName)
                                    .setDataServiceCallback(callback);
                            boolean isConnected =
                                    isNetworkConnected(
                                            IwlanHelper.isDefaultDataSlot(
                                                    mContext,
                                                    iwlanDataServiceProvider.getSlotIndex()),
                                            IwlanHelper.isCrossSimCallingEnabled(
                                                    mContext,
                                                    iwlanDataServiceProvider.getSlotIndex()));
                            iwlanDataServiceProvider
                                    .getTunnelManager()
                                    .closeTunnel(tunnelApnName, !isConnected);
                            return;
                        }
                    }

                    iwlanDataServiceProvider.deliverCallback(
                            IwlanDataServiceProvider.CALLBACK_TYPE_DEACTIVATE_DATACALL_COMPLETE,
                            DataServiceCallback.RESULT_ERROR_INVALID_ARG,
                            callback,
                            null);
                    break;

                case EVENT_DATA_CALL_LIST_REQUEST:
                    DataCallRequestData dataCallRequestData = (DataCallRequestData) msg.obj;
                    callback = dataCallRequestData.mCallback;
                    iwlanDataServiceProvider = dataCallRequestData.mIwlanDataServiceProvider;

                    iwlanDataServiceProvider.deliverCallback(
                            IwlanDataServiceProvider.CALLBACK_TYPE_GET_DATACALL_LIST_COMPLETE,
                            DataServiceCallback.RESULT_SUCCESS,
                            callback,
                            null);
                    break;

                case EVENT_FORCE_CLOSE_TUNNEL:
                    for (IwlanDataServiceProvider dp : sIwlanDataServiceProviders.values()) {
                        dp.forceCloseTunnels();
                    }
                    break;

                case EVENT_ADD_DATA_SERVICE_PROVIDER:
                    iwlanDataServiceProvider = (IwlanDataServiceProvider) msg.obj;
                    addIwlanDataServiceProvider(iwlanDataServiceProvider);
                    break;

                case EVENT_REMOVE_DATA_SERVICE_PROVIDER:
                    iwlanDataServiceProvider = (IwlanDataServiceProvider) msg.obj;

                    IwlanDataServiceProvider dsp =
                            sIwlanDataServiceProviders.remove(
                                    iwlanDataServiceProvider.getSlotIndex());
                    if (dsp == null) {
                        Log.w(
                                TAG,
                                "No DataServiceProvider exists for slot "
                                        + iwlanDataServiceProvider.getSlotIndex());
                    }

                    if (sIwlanDataServiceProviders.isEmpty()) {
                        deinitNetworkCallback();
                    }
                    break;

                case EVENT_TUNNEL_OPENED_METRICS:
                    TunnelOpenedMetricsData tunnelOpenedMetricsData =
                            (TunnelOpenedMetricsData) msg.obj;
                    iwlanDataServiceProvider = tunnelOpenedMetricsData.mIwlanDataServiceProvider;
                    apnName = tunnelOpenedMetricsData.mApnName;

                    metricsAtom = iwlanDataServiceProvider.mMetricsAtomForApn.get(apnName);
                    metricsAtom.setEpdgServerAddress(tunnelOpenedMetricsData.mEpdgServerAddress);
                    metricsAtom.setProcessingDurationMillis(
                            tunnelOpenedMetricsData.mProcessingDuration);
                    metricsAtom.setEpdgServerSelectionDurationMillis(
                            tunnelOpenedMetricsData.mEpdgServerSelectionDuration);
                    metricsAtom.setIkeTunnelEstablishmentDurationMillis(
                            tunnelOpenedMetricsData.mIkeTunnelEstablishmentDuration);

                    metricsAtom.sendMetricsData();
                    break;

                case EVENT_TUNNEL_CLOSED_METRICS:
                    TunnelClosedMetricsData tunnelClosedMetricsData =
                            (TunnelClosedMetricsData) msg.obj;
                    iwlanDataServiceProvider = tunnelClosedMetricsData.mIwlanDataServiceProvider;
                    apnName = tunnelClosedMetricsData.mApnName;

                    metricsAtom = iwlanDataServiceProvider.mMetricsAtomForApn.get(apnName);
                    metricsAtom.setEpdgServerAddress(tunnelClosedMetricsData.mEpdgServerAddress);
                    metricsAtom.setProcessingDurationMillis(
                            tunnelClosedMetricsData.mProcessingDuration);
                    metricsAtom.setEpdgServerSelectionDurationMillis(
                            tunnelClosedMetricsData.mEpdgServerSelectionDuration);
                    metricsAtom.setIkeTunnelEstablishmentDurationMillis(
                            tunnelClosedMetricsData.mIkeTunnelEstablishmentDuration);

                    metricsAtom.sendMetricsData();
                    iwlanDataServiceProvider.mMetricsAtomForApn.remove(apnName);
                    break;

                default:
                    throw new IllegalStateException("Unexpected value: " + msg.what);
            }
        }

        IwlanDataServiceHandler(Looper looper) {
            super(looper);
        }
    }

    private static final class TunnelOpenedData {
        final String mApnName;
        final TunnelLinkProperties mTunnelLinkProperties;
        final IwlanDataServiceProvider mIwlanDataServiceProvider;

        private TunnelOpenedData(
                String apnName,
                TunnelLinkProperties tunnelLinkProperties,
                IwlanDataServiceProvider dsp) {
            mApnName = apnName;
            mTunnelLinkProperties = tunnelLinkProperties;
            mIwlanDataServiceProvider = dsp;
        }
    }

    private static final class TunnelClosedData {
        final String mApnName;
        final IwlanError mIwlanError;
        final IwlanDataServiceProvider mIwlanDataServiceProvider;

        private TunnelClosedData(
                String apnName, IwlanError iwlanError, IwlanDataServiceProvider dsp) {
            mApnName = apnName;
            mIwlanError = iwlanError;
            mIwlanDataServiceProvider = dsp;
        }
    }

    private static final class TunnelOpenedMetricsData {
        final String mApnName;
        final IwlanDataServiceProvider mIwlanDataServiceProvider;
        final String mEpdgServerAddress;
        final int mProcessingDuration;
        final int mEpdgServerSelectionDuration;
        final int mIkeTunnelEstablishmentDuration;

        private TunnelOpenedMetricsData(
                String apnName,
                String epdgServerAddress,
                long processingDuration,
                int epdgServerSelectionDuration,
                int ikeTunnelEstablishmentDuration,
                IwlanDataServiceProvider dsp) {
            mApnName = apnName;
            mIwlanDataServiceProvider = dsp;
            mEpdgServerAddress = epdgServerAddress;
            mProcessingDuration = (int) processingDuration;
            mEpdgServerSelectionDuration = epdgServerSelectionDuration;
            mIkeTunnelEstablishmentDuration = ikeTunnelEstablishmentDuration;
        }
    }

    private static final class TunnelClosedMetricsData {
        final String mApnName;
        final IwlanDataServiceProvider mIwlanDataServiceProvider;
        final String mEpdgServerAddress;
        final int mProcessingDuration;
        final int mEpdgServerSelectionDuration;
        final int mIkeTunnelEstablishmentDuration;

        private TunnelClosedMetricsData(
                String apnName,
                String epdgServerAddress,
                long processingDuration,
                int epdgServerSelectionDuration,
                int ikeTunnelEstablishmentDuration,
                IwlanDataServiceProvider dsp) {
            mApnName = apnName;
            mIwlanDataServiceProvider = dsp;
            mEpdgServerAddress = epdgServerAddress;
            mProcessingDuration = (int) processingDuration;
            mEpdgServerSelectionDuration = epdgServerSelectionDuration;
            mIkeTunnelEstablishmentDuration = ikeTunnelEstablishmentDuration;
        }
    }

    private static final class SetupDataCallData {
        final int mAccessNetworkType;
        @NonNull final DataProfile mDataProfile;
        final boolean mIsRoaming;
        final boolean mAllowRoaming;
        final int mReason;
        @Nullable final LinkProperties mLinkProperties;

        @IntRange(from = 0, to = 15)
        final int mPduSessionId;

        @Nullable final NetworkSliceInfo mSliceInfo;
        @Nullable final TrafficDescriptor mTrafficDescriptor;
        final boolean mMatchAllRuleAllowed;
        @NonNull final DataServiceCallback mCallback;
        final IwlanDataServiceProvider mIwlanDataServiceProvider;

        private SetupDataCallData(
                int accessNetworkType,
                DataProfile dataProfile,
                boolean isRoaming,
                boolean allowRoaming,
                int reason,
                LinkProperties linkProperties,
                int pduSessionId,
                NetworkSliceInfo sliceInfo,
                TrafficDescriptor trafficDescriptor,
                boolean matchAllRuleAllowed,
                DataServiceCallback callback,
                IwlanDataServiceProvider dsp) {
            mAccessNetworkType = accessNetworkType;
            mDataProfile = dataProfile;
            mIsRoaming = isRoaming;
            mAllowRoaming = allowRoaming;
            mReason = reason;
            mLinkProperties = linkProperties;
            mPduSessionId = pduSessionId;
            mSliceInfo = sliceInfo;
            mTrafficDescriptor = trafficDescriptor;
            mMatchAllRuleAllowed = matchAllRuleAllowed;
            mCallback = callback;
            mIwlanDataServiceProvider = dsp;
        }
    }

    private static final class DeactivateDataCallData {
        final int mCid;
        final int mReason;
        final DataServiceCallback mCallback;
        final IwlanDataServiceProvider mIwlanDataServiceProvider;

        private DeactivateDataCallData(
                int cid, int reason, DataServiceCallback callback, IwlanDataServiceProvider dsp) {
            mCid = cid;
            mReason = reason;
            mCallback = callback;
            mIwlanDataServiceProvider = dsp;
        }
    }

    private static final class DataCallRequestData {
        final DataServiceCallback mCallback;
        final IwlanDataServiceProvider mIwlanDataServiceProvider;

        private DataCallRequestData(DataServiceCallback callback, IwlanDataServiceProvider dsp) {
            mCallback = callback;
            mIwlanDataServiceProvider = dsp;
        }
    }

    @VisibleForTesting
    static boolean isNetworkConnected(boolean isDds, boolean isCstEnabled) {
        if (!isDds && isCstEnabled) {
            // Only Non-DDS sub with CST enabled, can use any transport.
            return sNetworkConnected;
        } else {
            // For all other cases, only wifi transport can be used.
            return ((sDefaultDataTransport == Transport.WIFI) && sNetworkConnected);
        }
    }

    @VisibleForTesting
    /* Note: this api should have valid transport if networkConnected==true */
    static void setNetworkConnected(
            boolean networkConnected, Network network, Transport transport) {

        boolean hasNetworkChanged = false;
        boolean hasTransportChanged = false;
        boolean hasNetworkConnectedChanged = false;

        if (sNetworkConnected == networkConnected
                && network.equals(sNetwork)
                && sDefaultDataTransport == transport) {
            // Nothing changed
            return;
        }

        // safety check
        if (networkConnected && transport == Transport.UNSPECIFIED_NETWORK) {
            Log.e(TAG, "setNetworkConnected: Network connected but transport unspecified");
            return;
        }

        if (!network.equals(sNetwork)) {
            Log.e(TAG, "setNetworkConnected NW changed from: " + sNetwork + " TO: " + network);
            hasNetworkChanged = true;
        }

        if (transport != sDefaultDataTransport) {
            Log.d(
                    TAG,
                    "Transport was changed from "
                            + sDefaultDataTransport.name()
                            + " to "
                            + transport.name());
            hasTransportChanged = true;
        }

        if (sNetworkConnected != networkConnected) {
            Log.d(
                    TAG,
                    "Network connected state change from "
                            + sNetworkConnected
                            + " to "
                            + networkConnected);
            hasNetworkConnectedChanged = true;
        }

        sNetworkConnected = networkConnected;
        sDefaultDataTransport = transport;
        sNetwork = network;
        if (!networkConnected) {
            // reset link protocol type
            sLinkProtocolType = LinkProtocolType.UNKNOWN;
        }

        if (networkConnected) {
            if (hasTransportChanged) {
                // Perform forceClose for tunnels in bringdown.
                // let framework handle explicit teardown
                for (IwlanDataServiceProvider dp : sIwlanDataServiceProviders.values()) {
                    dp.forceCloseTunnelsInDeactivatingState();
                }
            }

            if (transport == Transport.WIFI && hasNetworkConnectedChanged) {
                IwlanEventListener.onWifiConnected(mContext);
            }
            // only prefetch dns and updateNetwork if Network has changed
            if (hasNetworkChanged) {
                for (IwlanDataServiceProvider dp : sIwlanDataServiceProviders.values()) {
                    dp.dnsPrefetchCheck();
                    dp.updateNetwork(sNetwork);
                }
                IwlanHelper.updateCountryCodeWhenNetworkConnected();
            }
        } else {
            for (IwlanDataServiceProvider dp : sIwlanDataServiceProviders.values()) {
                // once network is disconnected, even NAT KA offload fails
                // But we should still let framework do an explicit teardown
                // so as to not affect an ongoing handover
                // only force close tunnels in bring down state
                dp.forceCloseTunnelsInDeactivatingState();
            }
        }
    }

    static boolean isLinkProtocolTypeChanged(LinkProperties linkProperties) {
        boolean hasIPV4 = false;
        boolean hasIPV6 = false;

        LinkProtocolType linkProtocolType = null;
        if (linkProperties != null) {
            for (LinkAddress linkAddress : linkProperties.getLinkAddresses()) {
                InetAddress inetaddr = linkAddress.getAddress();
                // skip linklocal and loopback addresses
                if (!inetaddr.isLoopbackAddress() && !inetaddr.isLinkLocalAddress()) {
                    if (inetaddr instanceof Inet4Address) {
                        hasIPV4 = true;
                    } else if (inetaddr instanceof Inet6Address) {
                        hasIPV6 = true;
                    }
                }
            }

            if (hasIPV4 && hasIPV6) {
                linkProtocolType = LinkProtocolType.IPV4V6;
            } else if (hasIPV4) {
                linkProtocolType = LinkProtocolType.IPV4;
            } else if (hasIPV6) {
                linkProtocolType = LinkProtocolType.IPV6;
            }

            if (sLinkProtocolType != linkProtocolType) {
                Log.d(
                        TAG,
                        "LinkProtocolType was changed from "
                                + sLinkProtocolType
                                + " to "
                                + linkProtocolType);
                sLinkProtocolType = linkProtocolType;
                return true;
            }
            return false;
        }
        Log.w(TAG, "linkProperties is NULL.");
        return false;
    }

    /**
     * Get the DataServiceProvider associated with the slotId
     *
     * @param slotId slot index
     * @return DataService.DataServiceProvider associated with the slot
     */
    public static DataService.DataServiceProvider getDataServiceProvider(int slotId) {
        return sIwlanDataServiceProviders.get(slotId);
    }

    public static Context getContext() {
        return mContext;
    }

    @Override
    public DataServiceProvider onCreateDataServiceProvider(int slotIndex) {
        // TODO: validity check on slot index
        Log.d(TAG, "Creating provider for " + slotIndex);

        if (mIwlanDataServiceHandler == null) {
            initHandler();
        }

        if (mNetworkMonitorCallback == null) {
            // start monitoring network and register for default network callback
            ConnectivityManager connectivityManager =
                    mContext.getSystemService(ConnectivityManager.class);
            mNetworkMonitorCallback = new IwlanNetworkMonitorCallback();
            connectivityManager.registerSystemDefaultNetworkCallback(
                    mNetworkMonitorCallback, mIwlanDataServiceHandler);
            Log.d(TAG, "Registered with Connectivity Service");
        }

        IwlanDataServiceProvider dp = new IwlanDataServiceProvider(slotIndex, this);

        mIwlanDataServiceHandler.sendMessage(
                mIwlanDataServiceHandler.obtainMessage(EVENT_ADD_DATA_SERVICE_PROVIDER, dp));
        return dp;
    }

    public void removeDataServiceProvider(IwlanDataServiceProvider dp) {
        mIwlanDataServiceHandler.sendMessage(
                mIwlanDataServiceHandler.obtainMessage(EVENT_REMOVE_DATA_SERVICE_PROVIDER, dp));
    }

    @VisibleForTesting
    void addIwlanDataServiceProvider(IwlanDataServiceProvider dp) {
        int slotIndex = dp.getSlotIndex();
        if (sIwlanDataServiceProviders.containsKey(slotIndex)) {
            throw new IllegalStateException(
                    "DataServiceProvider already exists for slot " + slotIndex);
        }
        sIwlanDataServiceProviders.put(slotIndex, dp);
    }

    void deinitNetworkCallback() {
        // deinit network related stuff
        ConnectivityManager connectivityManager =
                mContext.getSystemService(ConnectivityManager.class);
        connectivityManager.unregisterNetworkCallback(mNetworkMonitorCallback);
        mNetworkMonitorCallback = null;
    }

    @VisibleForTesting
    void setAppContext(Context appContext) {
        mContext = appContext;
    }

    @VisibleForTesting
    IwlanNetworkMonitorCallback getNetworkMonitorCallback() {
        return mNetworkMonitorCallback;
    }

    @VisibleForTesting
    void initHandler() {
        mIwlanDataServiceHandler = new IwlanDataServiceHandler(getLooper());
    }

    @VisibleForTesting
    Looper getLooper() {
        mIwlanDataServiceHandlerThread = new HandlerThread("IwlanDataServiceThread");
        mIwlanDataServiceHandlerThread.start();
        return mIwlanDataServiceHandlerThread.getLooper();
    }

    private static String eventToString(int event) {
        switch (event) {
            case EVENT_TUNNEL_OPENED:
                return "EVENT_TUNNEL_OPENED";
            case EVENT_TUNNEL_CLOSED:
                return "EVENT_TUNNEL_CLOSED";
            case EVENT_SETUP_DATA_CALL:
                return "EVENT_SETUP_DATA_CALL";
            case EVENT_DEACTIVATE_DATA_CALL:
                return "EVENT_DEACTIVATE_DATA_CALL";
            case EVENT_DATA_CALL_LIST_REQUEST:
                return "EVENT_DATA_CALL_LIST_REQUEST";
            case EVENT_FORCE_CLOSE_TUNNEL:
                return "EVENT_FORCE_CLOSE_TUNNEL";
            case EVENT_ADD_DATA_SERVICE_PROVIDER:
                return "EVENT_ADD_DATA_SERVICE_PROVIDER";
            case EVENT_REMOVE_DATA_SERVICE_PROVIDER:
                return "EVENT_REMOVE_DATA_SERVICE_PROVIDER";
            case IwlanEventListener.CARRIER_CONFIG_CHANGED_EVENT:
                return "CARRIER_CONFIG_CHANGED_EVENT";
            case IwlanEventListener.CARRIER_CONFIG_UNKNOWN_CARRIER_EVENT:
                return "CARRIER_CONFIG_UNKNOWN_CARRIER_EVENT";
            case IwlanEventListener.WIFI_CALLING_ENABLE_EVENT:
                return "WIFI_CALLING_ENABLE_EVENT";
            case IwlanEventListener.WIFI_CALLING_DISABLE_EVENT:
                return "WIFI_CALLING_DISABLE_EVENT";
            case IwlanEventListener.CELLINFO_CHANGED_EVENT:
                return "CELLINFO_CHANGED_EVENT";
            case EVENT_TUNNEL_OPENED_METRICS:
                return "EVENT_TUNNEL_OPENED_METRICS";
            case EVENT_TUNNEL_CLOSED_METRICS:
                return "EVENT_TUNNEL_CLOSED_METRICS";
            default:
                return "Unknown(" + event + ")";
        }
    }

    @Override
    public void onCreate() {
        setAppContext(getApplicationContext());
        IwlanBroadcastReceiver.startListening(mContext);
        IwlanHelper.startCountryDetector(mContext);
    }

    @Override
    public void onDestroy() {
        IwlanBroadcastReceiver.stopListening(mContext);
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "Iwlanservice onBind");
        return super.onBind(intent);
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.d(TAG, "IwlanService onUnbind");
        mIwlanDataServiceHandler.sendMessage(
                mIwlanDataServiceHandler.obtainMessage(EVENT_FORCE_CLOSE_TUNNEL));
        return super.onUnbind(intent);
    }

    @Override
    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        String transport = "UNSPECIFIED";
        if (sDefaultDataTransport == Transport.MOBILE) {
            transport = "CELLULAR";
        } else if (sDefaultDataTransport == Transport.WIFI) {
            transport = "WIFI";
        }
        pw.println("Default transport: " + transport);
        for (IwlanDataServiceProvider provider : sIwlanDataServiceProviders.values()) {
            pw.println();
            provider.dump(fd, pw, args);
            pw.println();
            pw.println();
        }
    }
}
