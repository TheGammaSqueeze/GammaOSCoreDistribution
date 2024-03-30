/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.server.ethernet;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.ConnectivityResources;
import android.net.EthernetManager;
import android.net.EthernetNetworkManagementException;
import android.net.EthernetNetworkSpecifier;
import android.net.INetworkInterfaceOutcomeReceiver;
import android.net.IpConfiguration;
import android.net.IpConfiguration.IpAssignment;
import android.net.IpConfiguration.ProxySettings;
import android.net.LinkProperties;
import android.net.NetworkAgentConfig;
import android.net.NetworkCapabilities;
import android.net.NetworkProvider;
import android.net.NetworkRequest;
import android.net.NetworkScore;
import android.net.ip.IIpClient;
import android.net.ip.IpClientCallbacks;
import android.net.ip.IpClientManager;
import android.net.ip.IpClientUtil;
import android.net.shared.ProvisioningConfiguration;
import android.os.ConditionVariable;
import android.os.Handler;
import android.os.Looper;
import android.os.RemoteException;
import android.text.TextUtils;
import android.util.AndroidRuntimeException;
import android.util.ArraySet;
import android.util.Log;
import android.util.SparseArray;

import com.android.connectivity.resources.R;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.IndentingPrintWriter;
import com.android.net.module.util.InterfaceParams;

import java.io.FileDescriptor;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@link NetworkProvider} that manages NetworkOffers for Ethernet networks.
 */
public class EthernetNetworkFactory {
    private final static String TAG = EthernetNetworkFactory.class.getSimpleName();
    final static boolean DBG = true;

    private static final String NETWORK_TYPE = "Ethernet";

    private final ConcurrentHashMap<String, NetworkInterfaceState> mTrackingInterfaces =
            new ConcurrentHashMap<>();
    private final Handler mHandler;
    private final Context mContext;
    private final NetworkProvider mProvider;
    final Dependencies mDeps;

    public static class Dependencies {
        public void makeIpClient(Context context, String iface, IpClientCallbacks callbacks) {
            IpClientUtil.makeIpClient(context, iface, callbacks);
        }

        public IpClientManager makeIpClientManager(@NonNull final IIpClient ipClient) {
            return new IpClientManager(ipClient, TAG);
        }

        public EthernetNetworkAgent makeEthernetNetworkAgent(Context context, Looper looper,
                NetworkCapabilities nc, LinkProperties lp, NetworkAgentConfig config,
                NetworkProvider provider, EthernetNetworkAgent.Callbacks cb) {
            return new EthernetNetworkAgent(context, looper, nc, lp, config, provider, cb);
        }

        public InterfaceParams getNetworkInterfaceByName(String name) {
            return InterfaceParams.getByName(name);
        }

        public String getTcpBufferSizesFromResource(Context context) {
            final ConnectivityResources resources = new ConnectivityResources(context);
            return resources.get().getString(R.string.config_ethernet_tcp_buffers);
        }
    }

    public static class ConfigurationException extends AndroidRuntimeException {
        public ConfigurationException(String msg) {
            super(msg);
        }
    }

    public EthernetNetworkFactory(Handler handler, Context context) {
        this(handler, context, new NetworkProvider(context, handler.getLooper(), TAG),
            new Dependencies());
    }

    @VisibleForTesting
    EthernetNetworkFactory(Handler handler, Context context, NetworkProvider provider,
            Dependencies deps) {
        mHandler = handler;
        mContext = context;
        mProvider = provider;
        mDeps = deps;
    }

    /**
     * Registers the network provider with the system.
     */
    public void register() {
        mContext.getSystemService(ConnectivityManager.class).registerNetworkProvider(mProvider);
    }

    /**
     * Returns an array of available interface names. The array is sorted: unrestricted interfaces
     * goes first, then sorted by name.
     */
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
    protected String[] getAvailableInterfaces(boolean includeRestricted) {
        return mTrackingInterfaces.values()
                .stream()
                .filter(iface -> !iface.isRestricted() || includeRestricted)
                .sorted((iface1, iface2) -> {
                    int r = Boolean.compare(iface1.isRestricted(), iface2.isRestricted());
                    return r == 0 ? iface1.name.compareTo(iface2.name) : r;
                })
                .map(iface -> iface.name)
                .toArray(String[]::new);
    }

    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
    protected void addInterface(@NonNull final String ifaceName, @NonNull final String hwAddress,
            @NonNull final IpConfiguration ipConfig,
            @NonNull final NetworkCapabilities capabilities) {
        if (mTrackingInterfaces.containsKey(ifaceName)) {
            Log.e(TAG, "Interface with name " + ifaceName + " already exists.");
            return;
        }

        final NetworkCapabilities nc = new NetworkCapabilities.Builder(capabilities)
                .setNetworkSpecifier(new EthernetNetworkSpecifier(ifaceName))
                .build();

        if (DBG) {
            Log.d(TAG, "addInterface, iface: " + ifaceName + ", capabilities: " + nc);
        }

        final NetworkInterfaceState iface = new NetworkInterfaceState(
                ifaceName, hwAddress, mHandler, mContext, ipConfig, nc, mProvider, mDeps);
        mTrackingInterfaces.put(ifaceName, iface);
    }

    @VisibleForTesting
    protected int getInterfaceState(@NonNull String iface) {
        final NetworkInterfaceState interfaceState = mTrackingInterfaces.get(iface);
        if (interfaceState == null) {
            return EthernetManager.STATE_ABSENT;
        } else if (!interfaceState.mLinkUp) {
            return EthernetManager.STATE_LINK_DOWN;
        } else {
            return EthernetManager.STATE_LINK_UP;
        }
    }

    /**
     * Update a network's configuration and restart it if necessary.
     *
     * @param ifaceName the interface name of the network to be updated.
     * @param ipConfig the desired {@link IpConfiguration} for the given network or null. If
     *                 {@code null} is passed, the existing IpConfiguration is not updated.
     * @param capabilities the desired {@link NetworkCapabilities} for the given network. If
     *                     {@code null} is passed, then the network's current
     *                     {@link NetworkCapabilities} will be used in support of existing APIs as
     *                     the public API does not allow this.
     * @param listener an optional {@link INetworkInterfaceOutcomeReceiver} to notify callers of
     *                 completion.
     */
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
    protected void updateInterface(@NonNull final String ifaceName,
            @Nullable final IpConfiguration ipConfig,
            @Nullable final NetworkCapabilities capabilities,
            @Nullable final INetworkInterfaceOutcomeReceiver listener) {
        if (!hasInterface(ifaceName)) {
            maybeSendNetworkManagementCallbackForUntracked(ifaceName, listener);
            return;
        }

        final NetworkInterfaceState iface = mTrackingInterfaces.get(ifaceName);
        iface.updateInterface(ipConfig, capabilities, listener);
        mTrackingInterfaces.put(ifaceName, iface);
    }

    private static NetworkCapabilities mixInCapabilities(NetworkCapabilities nc,
            NetworkCapabilities addedNc) {
       final NetworkCapabilities.Builder builder = new NetworkCapabilities.Builder(nc);
       for (int transport : addedNc.getTransportTypes()) builder.addTransportType(transport);
       for (int capability : addedNc.getCapabilities()) builder.addCapability(capability);
       return builder.build();
    }

    private static NetworkCapabilities createDefaultNetworkCapabilities() {
        return NetworkCapabilities.Builder
                .withoutDefaultCapabilities()
                .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET).build();
    }

    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
    protected void removeInterface(String interfaceName) {
        NetworkInterfaceState iface = mTrackingInterfaces.remove(interfaceName);
        if (iface != null) {
            iface.destroy();
        }
    }

    /** Returns true if state has been modified */
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
    protected boolean updateInterfaceLinkState(@NonNull final String ifaceName, final boolean up,
            @Nullable final INetworkInterfaceOutcomeReceiver listener) {
        if (!hasInterface(ifaceName)) {
            maybeSendNetworkManagementCallbackForUntracked(ifaceName, listener);
            return false;
        }

        if (DBG) {
            Log.d(TAG, "updateInterfaceLinkState, iface: " + ifaceName + ", up: " + up);
        }

        NetworkInterfaceState iface = mTrackingInterfaces.get(ifaceName);
        return iface.updateLinkState(up, listener);
    }

    private void maybeSendNetworkManagementCallbackForUntracked(
            String ifaceName, INetworkInterfaceOutcomeReceiver listener) {
        maybeSendNetworkManagementCallback(listener, null,
                new EthernetNetworkManagementException(
                        ifaceName + " can't be updated as it is not available."));
    }

    @VisibleForTesting
    protected boolean hasInterface(String ifaceName) {
        return mTrackingInterfaces.containsKey(ifaceName);
    }

    private static void maybeSendNetworkManagementCallback(
            @Nullable final INetworkInterfaceOutcomeReceiver listener,
            @Nullable final String iface,
            @Nullable final EthernetNetworkManagementException e) {
        if (null == listener) {
            return;
        }

        try {
            if (iface != null) {
                listener.onResult(iface);
            } else {
                listener.onError(e);
            }
        } catch (RemoteException re) {
            Log.e(TAG, "Can't send onComplete for network management callback", re);
        }
    }

    @VisibleForTesting
    static class NetworkInterfaceState {
        final String name;

        private final String mHwAddress;
        private final Handler mHandler;
        private final Context mContext;
        private final NetworkProvider mNetworkProvider;
        private final Dependencies mDeps;
        private final NetworkProvider.NetworkOfferCallback mNetworkOfferCallback;

        private static String sTcpBufferSizes = null;  // Lazy initialized.

        private boolean mLinkUp;
        private int mLegacyType;
        private LinkProperties mLinkProperties = new LinkProperties();
        private Set<NetworkRequest> mRequests = new ArraySet<>();

        private volatile @Nullable IpClientManager mIpClient;
        private @NonNull NetworkCapabilities mCapabilities;
        private @Nullable EthernetIpClientCallback mIpClientCallback;
        private @Nullable EthernetNetworkAgent mNetworkAgent;
        private @Nullable IpConfiguration mIpConfig;

        /**
         * A map of TRANSPORT_* types to legacy transport types available for each type an ethernet
         * interface could propagate.
         *
         * There are no legacy type equivalents to LOWPAN or WIFI_AWARE. These types are set to
         * TYPE_NONE to match the behavior of their own network factories.
         */
        private static final SparseArray<Integer> sTransports = new SparseArray();
        static {
            sTransports.put(NetworkCapabilities.TRANSPORT_ETHERNET,
                    ConnectivityManager.TYPE_ETHERNET);
            sTransports.put(NetworkCapabilities.TRANSPORT_BLUETOOTH,
                    ConnectivityManager.TYPE_BLUETOOTH);
            sTransports.put(NetworkCapabilities.TRANSPORT_WIFI, ConnectivityManager.TYPE_WIFI);
            sTransports.put(NetworkCapabilities.TRANSPORT_CELLULAR,
                    ConnectivityManager.TYPE_MOBILE);
            sTransports.put(NetworkCapabilities.TRANSPORT_LOWPAN, ConnectivityManager.TYPE_NONE);
            sTransports.put(NetworkCapabilities.TRANSPORT_WIFI_AWARE,
                    ConnectivityManager.TYPE_NONE);
        }

        private class EthernetIpClientCallback extends IpClientCallbacks {
            private final ConditionVariable mIpClientStartCv = new ConditionVariable(false);
            private final ConditionVariable mIpClientShutdownCv = new ConditionVariable(false);
            @Nullable INetworkInterfaceOutcomeReceiver mNetworkManagementListener;

            EthernetIpClientCallback(@Nullable final INetworkInterfaceOutcomeReceiver listener) {
                mNetworkManagementListener = listener;
            }

            @Override
            public void onIpClientCreated(IIpClient ipClient) {
                mIpClient = mDeps.makeIpClientManager(ipClient);
                mIpClientStartCv.open();
            }

            private void awaitIpClientStart() {
                mIpClientStartCv.block();
            }

            private void awaitIpClientShutdown() {
                mIpClientShutdownCv.block();
            }

            // At the time IpClient is stopped, an IpClient event may have already been posted on
            // the back of the handler and is awaiting execution. Once that event is executed, the
            // associated callback object may not be valid anymore
            // (NetworkInterfaceState#mIpClientCallback points to a different object / null).
            private boolean isCurrentCallback() {
                return this == mIpClientCallback;
            }

            private void handleIpEvent(final @NonNull Runnable r) {
                mHandler.post(() -> {
                    if (!isCurrentCallback()) {
                        Log.i(TAG, "Ignoring stale IpClientCallbacks " + this);
                        return;
                    }
                    r.run();
                });
            }

            @Override
            public void onProvisioningSuccess(LinkProperties newLp) {
                handleIpEvent(() -> onIpLayerStarted(newLp, mNetworkManagementListener));
            }

            @Override
            public void onProvisioningFailure(LinkProperties newLp) {
                // This cannot happen due to provisioning timeout, because our timeout is 0. It can
                // happen due to errors while provisioning or on provisioning loss.
                handleIpEvent(() -> onIpLayerStopped(mNetworkManagementListener));
            }

            @Override
            public void onLinkPropertiesChange(LinkProperties newLp) {
                handleIpEvent(() -> updateLinkProperties(newLp));
            }

            @Override
            public void onReachabilityLost(String logMsg) {
                handleIpEvent(() -> updateNeighborLostEvent(logMsg));
            }

            @Override
            public void onQuit() {
                mIpClient = null;
                mIpClientShutdownCv.open();
            }
        }

        private class EthernetNetworkOfferCallback implements NetworkProvider.NetworkOfferCallback {
            @Override
            public void onNetworkNeeded(@NonNull NetworkRequest request) {
                if (DBG) {
                    Log.d(TAG, String.format("%s: onNetworkNeeded for request: %s", name, request));
                }
                // When the network offer is first registered, onNetworkNeeded is called with all
                // existing requests.
                // ConnectivityService filters requests for us based on the NetworkCapabilities
                // passed in the registerNetworkOffer() call.
                mRequests.add(request);
                // if the network is already started, this is a no-op.
                start();
            }

            @Override
            public void onNetworkUnneeded(@NonNull NetworkRequest request) {
                if (DBG) {
                    Log.d(TAG,
                            String.format("%s: onNetworkUnneeded for request: %s", name, request));
                }
                mRequests.remove(request);
                if (mRequests.isEmpty()) {
                    // not currently serving any requests, stop the network.
                    stop();
                }
            }
        }

        NetworkInterfaceState(String ifaceName, String hwAddress, Handler handler, Context context,
                @NonNull IpConfiguration ipConfig, @NonNull NetworkCapabilities capabilities,
                NetworkProvider networkProvider, Dependencies deps) {
            name = ifaceName;
            mIpConfig = Objects.requireNonNull(ipConfig);
            mCapabilities = Objects.requireNonNull(capabilities);
            mLegacyType = getLegacyType(mCapabilities);
            mHandler = handler;
            mContext = context;
            mNetworkProvider = networkProvider;
            mDeps = deps;
            mNetworkOfferCallback = new EthernetNetworkOfferCallback();
            mHwAddress = hwAddress;
        }

        /**
         * Determines the legacy transport type from a NetworkCapabilities transport type. Defaults
         * to legacy TYPE_NONE if there is no known conversion
         */
        private static int getLegacyType(int transport) {
            return sTransports.get(transport, ConnectivityManager.TYPE_NONE);
        }

        private static int getLegacyType(@NonNull final NetworkCapabilities capabilities) {
            final int[] transportTypes = capabilities.getTransportTypes();
            if (transportTypes.length > 0) {
                return getLegacyType(transportTypes[0]);
            }

            // Should never happen as transport is always one of ETHERNET or a valid override
            throw new ConfigurationException("Network Capabilities do not have an associated "
                    + "transport type.");
        }

        private static NetworkScore getBestNetworkScore() {
            return new NetworkScore.Builder().build();
        }

        private void setCapabilities(@NonNull final NetworkCapabilities capabilities) {
            mCapabilities = new NetworkCapabilities(capabilities);
            mLegacyType = getLegacyType(mCapabilities);

            if (mLinkUp) {
                // registering a new network offer will update the existing one, not install a
                // new one.
                mNetworkProvider.registerNetworkOffer(getBestNetworkScore(),
                        new NetworkCapabilities(capabilities), cmd -> mHandler.post(cmd),
                        mNetworkOfferCallback);
            }
        }

        void updateInterface(@Nullable final IpConfiguration ipConfig,
                @Nullable final NetworkCapabilities capabilities,
                @Nullable final INetworkInterfaceOutcomeReceiver listener) {
            if (DBG) {
                Log.d(TAG, "updateInterface, iface: " + name
                        + ", ipConfig: " + ipConfig + ", old ipConfig: " + mIpConfig
                        + ", capabilities: " + capabilities + ", old capabilities: " + mCapabilities
                        + ", listener: " + listener
                );
            }

            if (null != ipConfig){
                mIpConfig = ipConfig;
            }
            if (null != capabilities) {
                setCapabilities(capabilities);
            }
            // TODO: Update this logic to only do a restart if required. Although a restart may
            //  be required due to the capabilities or ipConfiguration values, not all
            //  capabilities changes require a restart.
            restart(listener);
        }

        boolean isRestricted() {
            return !mCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED);
        }

        private void start() {
            start(null);
        }

        private void start(@Nullable final INetworkInterfaceOutcomeReceiver listener) {
            if (mIpClient != null) {
                if (DBG) Log.d(TAG, "IpClient already started");
                return;
            }
            if (DBG) {
                Log.d(TAG, String.format("Starting Ethernet IpClient(%s)", name));
            }

            mIpClientCallback = new EthernetIpClientCallback(listener);
            mDeps.makeIpClient(mContext, name, mIpClientCallback);
            mIpClientCallback.awaitIpClientStart();

            if (sTcpBufferSizes == null) {
                sTcpBufferSizes = mDeps.getTcpBufferSizesFromResource(mContext);
            }
            provisionIpClient(mIpClient, mIpConfig, sTcpBufferSizes);
        }

        void onIpLayerStarted(@NonNull final LinkProperties linkProperties,
                @Nullable final INetworkInterfaceOutcomeReceiver listener) {
            if (mNetworkAgent != null) {
                Log.e(TAG, "Already have a NetworkAgent - aborting new request");
                stop();
                return;
            }
            mLinkProperties = linkProperties;

            // Create our NetworkAgent.
            final NetworkAgentConfig config = new NetworkAgentConfig.Builder()
                    .setLegacyType(mLegacyType)
                    .setLegacyTypeName(NETWORK_TYPE)
                    .setLegacyExtraInfo(mHwAddress)
                    .build();
            mNetworkAgent = mDeps.makeEthernetNetworkAgent(mContext, mHandler.getLooper(),
                    mCapabilities, mLinkProperties, config, mNetworkProvider,
                    new EthernetNetworkAgent.Callbacks() {
                        @Override
                        public void onNetworkUnwanted() {
                            // if mNetworkAgent is null, we have already called stop.
                            if (mNetworkAgent == null) return;

                            if (this == mNetworkAgent.getCallbacks()) {
                                stop();
                            } else {
                                Log.d(TAG, "Ignoring unwanted as we have a more modern " +
                                        "instance");
                            }
                        }
                    });
            mNetworkAgent.register();
            mNetworkAgent.markConnected();
            realizeNetworkManagementCallback(name, null);
        }

        void onIpLayerStopped(@Nullable final INetworkInterfaceOutcomeReceiver listener) {
            // There is no point in continuing if the interface is gone as stop() will be triggered
            // by removeInterface() when processed on the handler thread and start() won't
            // work for a non-existent interface.
            if (null == mDeps.getNetworkInterfaceByName(name)) {
                if (DBG) Log.d(TAG, name + " is no longer available.");
                // Send a callback in case a provisioning request was in progress.
                maybeSendNetworkManagementCallbackForAbort();
                return;
            }
            restart(listener);
        }

        private void maybeSendNetworkManagementCallbackForAbort() {
            realizeNetworkManagementCallback(null,
                    new EthernetNetworkManagementException(
                            "The IP provisioning request has been aborted."));
        }

        // Must be called on the handler thread
        private void realizeNetworkManagementCallback(@Nullable final String iface,
                @Nullable final EthernetNetworkManagementException e) {
            ensureRunningOnEthernetHandlerThread();
            if (null == mIpClientCallback) {
                return;
            }

            EthernetNetworkFactory.maybeSendNetworkManagementCallback(
                    mIpClientCallback.mNetworkManagementListener, iface, e);
            // Only send a single callback per listener.
            mIpClientCallback.mNetworkManagementListener = null;
        }

        private void ensureRunningOnEthernetHandlerThread() {
            if (mHandler.getLooper().getThread() != Thread.currentThread()) {
                throw new IllegalStateException(
                        "Not running on the Ethernet thread: "
                                + Thread.currentThread().getName());
            }
        }

        void updateLinkProperties(LinkProperties linkProperties) {
            mLinkProperties = linkProperties;
            if (mNetworkAgent != null) {
                mNetworkAgent.sendLinkPropertiesImpl(linkProperties);
            }
        }

        void updateNeighborLostEvent(String logMsg) {
            Log.i(TAG, "updateNeighborLostEvent " + logMsg);
            // Reachability lost will be seen only if the gateway is not reachable.
            // Since ethernet FW doesn't have the mechanism to scan for new networks
            // like WiFi, simply restart.
            // If there is a better network, that will become default and apps
            // will be able to use internet. If ethernet gets connected again,
            // and has backhaul connectivity, it will become default.
            restart();
        }

        /** Returns true if state has been modified */
        boolean updateLinkState(final boolean up,
                @Nullable final INetworkInterfaceOutcomeReceiver listener) {
            if (mLinkUp == up)  {
                EthernetNetworkFactory.maybeSendNetworkManagementCallback(listener, null,
                        new EthernetNetworkManagementException(
                                "No changes with requested link state " + up + " for " + name));
                return false;
            }
            mLinkUp = up;

            if (!up) { // was up, goes down
                // retract network offer and stop IpClient.
                destroy();
            } else { // was down, goes up
                // register network offer
                mNetworkProvider.registerNetworkOffer(getBestNetworkScore(),
                        new NetworkCapabilities(mCapabilities), (cmd) -> mHandler.post(cmd),
                        mNetworkOfferCallback);
            }

            EthernetNetworkFactory.maybeSendNetworkManagementCallback(listener, name, null);
            return true;
        }

        private void stop() {
            // Invalidate all previous start requests
            if (mIpClient != null) {
                mIpClient.shutdown();
                mIpClientCallback.awaitIpClientShutdown();
                mIpClient = null;
            }
            // Send an abort callback if an updateInterface request was in progress.
            maybeSendNetworkManagementCallbackForAbort();
            mIpClientCallback = null;

            if (mNetworkAgent != null) {
                mNetworkAgent.unregister();
                mNetworkAgent = null;
            }
            mLinkProperties.clear();
        }

        public void destroy() {
            mNetworkProvider.unregisterNetworkOffer(mNetworkOfferCallback);
            stop();
            mRequests.clear();
        }

        private static void provisionIpClient(@NonNull final IpClientManager ipClient,
                @NonNull final IpConfiguration config, @NonNull final String tcpBufferSizes) {
            if (config.getProxySettings() == ProxySettings.STATIC ||
                    config.getProxySettings() == ProxySettings.PAC) {
                ipClient.setHttpProxy(config.getHttpProxy());
            }

            if (!TextUtils.isEmpty(tcpBufferSizes)) {
                ipClient.setTcpBufferSizes(tcpBufferSizes);
            }

            ipClient.startProvisioning(createProvisioningConfiguration(config));
        }

        private static ProvisioningConfiguration createProvisioningConfiguration(
                @NonNull final IpConfiguration config) {
            if (config.getIpAssignment() == IpAssignment.STATIC) {
                return new ProvisioningConfiguration.Builder()
                        .withStaticConfiguration(config.getStaticIpConfiguration())
                        .build();
            }
            return new ProvisioningConfiguration.Builder()
                        .withProvisioningTimeoutMs(0)
                        .build();
        }

        void restart() {
            restart(null);
        }

        void restart(@Nullable final INetworkInterfaceOutcomeReceiver listener) {
            if (DBG) Log.d(TAG, "reconnecting Ethernet");
            stop();
            start(listener);
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + "{ "
                    + "iface: " + name + ", "
                    + "up: " + mLinkUp + ", "
                    + "hwAddress: " + mHwAddress + ", "
                    + "networkCapabilities: " + mCapabilities + ", "
                    + "networkAgent: " + mNetworkAgent + ", "
                    + "ipClient: " + mIpClient + ","
                    + "linkProperties: " + mLinkProperties
                    + "}";
        }
    }

    void dump(FileDescriptor fd, IndentingPrintWriter pw, String[] args) {
        pw.println(getClass().getSimpleName());
        pw.println("Tracking interfaces:");
        pw.increaseIndent();
        for (String iface: mTrackingInterfaces.keySet()) {
            NetworkInterfaceState ifaceState = mTrackingInterfaces.get(iface);
            pw.println(iface + ":" + ifaceState);
            pw.increaseIndent();
            if (null == ifaceState.mIpClient) {
                pw.println("IpClient is null");
            }
            pw.decreaseIndent();
        }
        pw.decreaseIndent();
    }
}
