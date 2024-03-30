/*
 * Copyright (C) 2018 The Android Open Source Project
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

import static android.net.EthernetManager.ETHERNET_STATE_DISABLED;
import static android.net.EthernetManager.ETHERNET_STATE_ENABLED;
import static android.net.TestNetworkManager.TEST_TAP_PREFIX;

import static com.android.internal.annotations.VisibleForTesting.Visibility.PACKAGE;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.net.ConnectivityResources;
import android.net.EthernetManager;
import android.net.IEthernetServiceListener;
import android.net.INetd;
import android.net.INetworkInterfaceOutcomeReceiver;
import android.net.ITetheredInterfaceCallback;
import android.net.InterfaceConfigurationParcel;
import android.net.IpConfiguration;
import android.net.IpConfiguration.IpAssignment;
import android.net.IpConfiguration.ProxySettings;
import android.net.LinkAddress;
import android.net.NetworkCapabilities;
import android.net.StaticIpConfiguration;
import android.os.ConditionVariable;
import android.os.Handler;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.os.ServiceSpecificException;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.Log;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.IndentingPrintWriter;
import com.android.net.module.util.BaseNetdUnsolicitedEventListener;
import com.android.net.module.util.NetdUtils;
import com.android.net.module.util.PermissionUtils;

import java.io.FileDescriptor;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks Ethernet interfaces and manages interface configurations.
 *
 * <p>Interfaces may have different {@link android.net.NetworkCapabilities}. This mapping is defined
 * in {@code config_ethernet_interfaces}. Notably, some interfaces could be marked as restricted by
 * not specifying {@link android.net.NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED} flag.
 * Interfaces could have associated {@link android.net.IpConfiguration}.
 * Ethernet Interfaces may be present at boot time or appear after boot (e.g., for Ethernet adapters
 * connected over USB). This class supports multiple interfaces. When an interface appears on the
 * system (or is present at boot time) this class will start tracking it and bring it up. Only
 * interfaces whose names match the {@code config_ethernet_iface_regex} regular expression are
 * tracked.
 *
 * <p>All public or package private methods must be thread-safe unless stated otherwise.
 */
@VisibleForTesting(visibility = PACKAGE)
public class EthernetTracker {
    private static final int INTERFACE_MODE_CLIENT = 1;
    private static final int INTERFACE_MODE_SERVER = 2;

    private static final String TAG = EthernetTracker.class.getSimpleName();
    private static final boolean DBG = EthernetNetworkFactory.DBG;

    private static final String TEST_IFACE_REGEXP = TEST_TAP_PREFIX + "\\d+";

    /**
     * Interface names we track. This is a product-dependent regular expression, plus,
     * if setIncludeTestInterfaces is true, any test interfaces.
     */
    private volatile String mIfaceMatch;

    /**
     * Track test interfaces if true, don't track otherwise.
     */
    private boolean mIncludeTestInterfaces = false;

    /** Mapping between {iface name | mac address} -> {NetworkCapabilities} */
    private final ConcurrentHashMap<String, NetworkCapabilities> mNetworkCapabilities =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, IpConfiguration> mIpConfigurations =
            new ConcurrentHashMap<>();

    private final Context mContext;
    private final INetd mNetd;
    private final Handler mHandler;
    private final EthernetNetworkFactory mFactory;
    private final EthernetConfigStore mConfigStore;
    private final Dependencies mDeps;

    private final RemoteCallbackList<IEthernetServiceListener> mListeners =
            new RemoteCallbackList<>();
    private final TetheredInterfaceRequestList mTetheredInterfaceRequests =
            new TetheredInterfaceRequestList();

    // Used only on the handler thread
    private String mDefaultInterface;
    private int mDefaultInterfaceMode = INTERFACE_MODE_CLIENT;
    // Tracks whether clients were notified that the tethered interface is available
    private boolean mTetheredInterfaceWasAvailable = false;
    private volatile IpConfiguration mIpConfigForDefaultInterface;

    private int mEthernetState = ETHERNET_STATE_ENABLED;

    private class TetheredInterfaceRequestList extends
            RemoteCallbackList<ITetheredInterfaceCallback> {
        @Override
        public void onCallbackDied(ITetheredInterfaceCallback cb, Object cookie) {
            mHandler.post(EthernetTracker.this::maybeUntetherDefaultInterface);
        }
    }

    public static class Dependencies {
        public String getInterfaceRegexFromResource(Context context) {
            final ConnectivityResources resources = new ConnectivityResources(context);
            return resources.get().getString(
                    com.android.connectivity.resources.R.string.config_ethernet_iface_regex);
        }

        public String[] getInterfaceConfigFromResource(Context context) {
            final ConnectivityResources resources = new ConnectivityResources(context);
            return resources.get().getStringArray(
                    com.android.connectivity.resources.R.array.config_ethernet_interfaces);
        }
    }

    EthernetTracker(@NonNull final Context context, @NonNull final Handler handler,
            @NonNull final EthernetNetworkFactory factory, @NonNull final INetd netd) {
        this(context, handler, factory, netd, new Dependencies());
    }

    @VisibleForTesting
    EthernetTracker(@NonNull final Context context, @NonNull final Handler handler,
            @NonNull final EthernetNetworkFactory factory, @NonNull final INetd netd,
            @NonNull final Dependencies deps) {
        mContext = context;
        mHandler = handler;
        mFactory = factory;
        mNetd = netd;
        mDeps = deps;

        // Interface match regex.
        updateIfaceMatchRegexp();

        // Read default Ethernet interface configuration from resources
        final String[] interfaceConfigs = mDeps.getInterfaceConfigFromResource(context);
        for (String strConfig : interfaceConfigs) {
            parseEthernetConfig(strConfig);
        }

        mConfigStore = new EthernetConfigStore();
    }

    void start() {
        mFactory.register();
        mConfigStore.read();

        // Default interface is just the first one we want to track.
        mIpConfigForDefaultInterface = mConfigStore.getIpConfigurationForDefaultInterface();
        final ArrayMap<String, IpConfiguration> configs = mConfigStore.getIpConfigurations();
        for (int i = 0; i < configs.size(); i++) {
            mIpConfigurations.put(configs.keyAt(i), configs.valueAt(i));
        }

        try {
            PermissionUtils.enforceNetworkStackPermission(mContext);
            mNetd.registerUnsolicitedEventListener(new InterfaceObserver());
        } catch (RemoteException | ServiceSpecificException e) {
            Log.e(TAG, "Could not register InterfaceObserver " + e);
        }

        mHandler.post(this::trackAvailableInterfaces);
    }

    void updateIpConfiguration(String iface, IpConfiguration ipConfiguration) {
        if (DBG) {
            Log.i(TAG, "updateIpConfiguration, iface: " + iface + ", cfg: " + ipConfiguration);
        }
        writeIpConfiguration(iface, ipConfiguration);
        mHandler.post(() -> {
            mFactory.updateInterface(iface, ipConfiguration, null, null);
            broadcastInterfaceStateChange(iface);
        });
    }

    private void writeIpConfiguration(@NonNull final String iface,
            @NonNull final IpConfiguration ipConfig) {
        mConfigStore.write(iface, ipConfig);
        mIpConfigurations.put(iface, ipConfig);
    }

    private IpConfiguration getIpConfigurationForCallback(String iface, int state) {
        return (state == EthernetManager.STATE_ABSENT) ? null : getOrCreateIpConfiguration(iface);
    }

    private void ensureRunningOnEthernetServiceThread() {
        if (mHandler.getLooper().getThread() != Thread.currentThread()) {
            throw new IllegalStateException(
                    "Not running on EthernetService thread: "
                            + Thread.currentThread().getName());
        }
    }

    /**
     * Broadcast the link state or IpConfiguration change of existing Ethernet interfaces to all
     * listeners.
     */
    protected void broadcastInterfaceStateChange(@NonNull String iface) {
        ensureRunningOnEthernetServiceThread();
        final int state = getInterfaceState(iface);
        final int role = getInterfaceRole(iface);
        final IpConfiguration config = getIpConfigurationForCallback(iface, state);
        final int n = mListeners.beginBroadcast();
        for (int i = 0; i < n; i++) {
            try {
                mListeners.getBroadcastItem(i).onInterfaceStateChanged(iface, state, role, config);
            } catch (RemoteException e) {
                // Do nothing here.
            }
        }
        mListeners.finishBroadcast();
    }

    /**
     * Unicast the interface state or IpConfiguration change of existing Ethernet interfaces to a
     * specific listener.
     */
    protected void unicastInterfaceStateChange(@NonNull IEthernetServiceListener listener,
            @NonNull String iface) {
        ensureRunningOnEthernetServiceThread();
        final int state = mFactory.getInterfaceState(iface);
        final int role = getInterfaceRole(iface);
        final IpConfiguration config = getIpConfigurationForCallback(iface, state);
        try {
            listener.onInterfaceStateChanged(iface, state, role, config);
        } catch (RemoteException e) {
            // Do nothing here.
        }
    }

    @VisibleForTesting(visibility = PACKAGE)
    protected void updateConfiguration(@NonNull final String iface,
            @Nullable final IpConfiguration ipConfig,
            @Nullable final NetworkCapabilities capabilities,
            @Nullable final INetworkInterfaceOutcomeReceiver listener) {
        if (DBG) {
            Log.i(TAG, "updateConfiguration, iface: " + iface + ", capabilities: " + capabilities
                    + ", ipConfig: " + ipConfig);
        }

        final IpConfiguration localIpConfig = ipConfig == null
                ? null : new IpConfiguration(ipConfig);
        if (ipConfig != null) {
            writeIpConfiguration(iface, localIpConfig);
        }

        if (null != capabilities) {
            mNetworkCapabilities.put(iface, capabilities);
        }
        mHandler.post(() -> {
            mFactory.updateInterface(iface, localIpConfig, capabilities, listener);
            broadcastInterfaceStateChange(iface);
        });
    }

    @VisibleForTesting(visibility = PACKAGE)
    protected void connectNetwork(@NonNull final String iface,
            @Nullable final INetworkInterfaceOutcomeReceiver listener) {
        mHandler.post(() -> updateInterfaceState(iface, true, listener));
    }

    @VisibleForTesting(visibility = PACKAGE)
    protected void disconnectNetwork(@NonNull final String iface,
            @Nullable final INetworkInterfaceOutcomeReceiver listener) {
        mHandler.post(() -> updateInterfaceState(iface, false, listener));
    }

    IpConfiguration getIpConfiguration(String iface) {
        return mIpConfigurations.get(iface);
    }

    @VisibleForTesting(visibility = PACKAGE)
    protected boolean isTrackingInterface(String iface) {
        return mFactory.hasInterface(iface);
    }

    String[] getInterfaces(boolean includeRestricted) {
        return mFactory.getAvailableInterfaces(includeRestricted);
    }

    List<String> getInterfaceList() {
        final List<String> interfaceList = new ArrayList<String>();
        final String[] ifaces;
        try {
            ifaces = mNetd.interfaceGetList();
        } catch (RemoteException e) {
            Log.e(TAG, "Could not get list of interfaces " + e);
            return interfaceList;
        }
        final String ifaceMatch = mIfaceMatch;
        for (String iface : ifaces) {
            if (iface.matches(ifaceMatch)) interfaceList.add(iface);
        }
        return interfaceList;
    }

    /**
     * Returns true if given interface was configured as restricted (doesn't have
     * NET_CAPABILITY_NOT_RESTRICTED) capability. Otherwise, returns false.
     */
    boolean isRestrictedInterface(String iface) {
        final NetworkCapabilities nc = mNetworkCapabilities.get(iface);
        return nc != null && !nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED);
    }

    void addListener(IEthernetServiceListener listener, boolean canUseRestrictedNetworks) {
        mHandler.post(() -> {
            if (!mListeners.register(listener, new ListenerInfo(canUseRestrictedNetworks))) {
                // Remote process has already died
                return;
            }
            for (String iface : getInterfaces(canUseRestrictedNetworks)) {
                unicastInterfaceStateChange(listener, iface);
            }

            unicastEthernetStateChange(listener, mEthernetState);
        });
    }

    void removeListener(IEthernetServiceListener listener) {
        mHandler.post(() -> mListeners.unregister(listener));
    }

    public void setIncludeTestInterfaces(boolean include) {
        mHandler.post(() -> {
            mIncludeTestInterfaces = include;
            updateIfaceMatchRegexp();
            if (!include) {
                removeTestData();
            }
            mHandler.post(() -> trackAvailableInterfaces());
        });
    }

    private void removeTestData() {
        removeTestIpData();
        removeTestCapabilityData();
    }

    private void removeTestIpData() {
        final Iterator<String> iterator = mIpConfigurations.keySet().iterator();
        while (iterator.hasNext()) {
            final String iface = iterator.next();
            if (iface.matches(TEST_IFACE_REGEXP)) {
                mConfigStore.write(iface, null);
                iterator.remove();
            }
        }
    }

    private void removeTestCapabilityData() {
        mNetworkCapabilities.keySet().removeIf(iface -> iface.matches(TEST_IFACE_REGEXP));
    }

    public void requestTetheredInterface(ITetheredInterfaceCallback callback) {
        mHandler.post(() -> {
            if (!mTetheredInterfaceRequests.register(callback)) {
                // Remote process has already died
                return;
            }
            if (mDefaultInterfaceMode == INTERFACE_MODE_SERVER) {
                if (mTetheredInterfaceWasAvailable) {
                    notifyTetheredInterfaceAvailable(callback, mDefaultInterface);
                }
                return;
            }

            setDefaultInterfaceMode(INTERFACE_MODE_SERVER);
        });
    }

    public void releaseTetheredInterface(ITetheredInterfaceCallback callback) {
        mHandler.post(() -> {
            mTetheredInterfaceRequests.unregister(callback);
            maybeUntetherDefaultInterface();
        });
    }

    private void notifyTetheredInterfaceAvailable(ITetheredInterfaceCallback cb, String iface) {
        try {
            cb.onAvailable(iface);
        } catch (RemoteException e) {
            Log.e(TAG, "Error sending tethered interface available callback", e);
        }
    }

    private void notifyTetheredInterfaceUnavailable(ITetheredInterfaceCallback cb) {
        try {
            cb.onUnavailable();
        } catch (RemoteException e) {
            Log.e(TAG, "Error sending tethered interface available callback", e);
        }
    }

    private void maybeUntetherDefaultInterface() {
        if (mTetheredInterfaceRequests.getRegisteredCallbackCount() > 0) return;
        if (mDefaultInterfaceMode == INTERFACE_MODE_CLIENT) return;
        setDefaultInterfaceMode(INTERFACE_MODE_CLIENT);
    }

    private void setDefaultInterfaceMode(int mode) {
        Log.d(TAG, "Setting default interface mode to " + mode);
        mDefaultInterfaceMode = mode;
        if (mDefaultInterface != null) {
            removeInterface(mDefaultInterface);
            addInterface(mDefaultInterface);
            // when this broadcast is sent, any calls to notifyTetheredInterfaceAvailable or
            // notifyTetheredInterfaceUnavailable have already happened
            broadcastInterfaceStateChange(mDefaultInterface);
        }
    }

    private int getInterfaceState(final String iface) {
        if (mFactory.hasInterface(iface)) {
            return mFactory.getInterfaceState(iface);
        }
        if (getInterfaceMode(iface) == INTERFACE_MODE_SERVER) {
            // server mode interfaces are not tracked by the factory.
            // TODO(b/234743836): interface state for server mode interfaces is not tracked
            // properly; just return link up.
            return EthernetManager.STATE_LINK_UP;
        }
        return EthernetManager.STATE_ABSENT;
    }

    private int getInterfaceRole(final String iface) {
        if (mFactory.hasInterface(iface)) {
            // only client mode interfaces are tracked by the factory.
            return EthernetManager.ROLE_CLIENT;
        }
        if (getInterfaceMode(iface) == INTERFACE_MODE_SERVER) {
            return EthernetManager.ROLE_SERVER;
        }
        return EthernetManager.ROLE_NONE;
    }

    private int getInterfaceMode(final String iface) {
        if (iface.equals(mDefaultInterface)) {
            return mDefaultInterfaceMode;
        }
        return INTERFACE_MODE_CLIENT;
    }

    private void removeInterface(String iface) {
        mFactory.removeInterface(iface);
        maybeUpdateServerModeInterfaceState(iface, false);
    }

    private void stopTrackingInterface(String iface) {
        removeInterface(iface);
        if (iface.equals(mDefaultInterface)) {
            mDefaultInterface = null;
        }
        broadcastInterfaceStateChange(iface);
    }

    private void addInterface(String iface) {
        InterfaceConfigurationParcel config = null;
        // Bring up the interface so we get link status indications.
        try {
            PermissionUtils.enforceNetworkStackPermission(mContext);
            NetdUtils.setInterfaceUp(mNetd, iface);
            config = NetdUtils.getInterfaceConfigParcel(mNetd, iface);
        } catch (IllegalStateException e) {
            // Either the system is crashing or the interface has disappeared. Just ignore the
            // error; we haven't modified any state because we only do that if our calls succeed.
            Log.e(TAG, "Error upping interface " + iface, e);
        }

        if (config == null) {
            Log.e(TAG, "Null interface config parcelable for " + iface + ". Bailing out.");
            return;
        }

        final String hwAddress = config.hwAddr;

        NetworkCapabilities nc = mNetworkCapabilities.get(iface);
        if (nc == null) {
            // Try to resolve using mac address
            nc = mNetworkCapabilities.get(hwAddress);
            if (nc == null) {
                final boolean isTestIface = iface.matches(TEST_IFACE_REGEXP);
                nc = createDefaultNetworkCapabilities(isTestIface);
            }
        }

        final int mode = getInterfaceMode(iface);
        if (mode == INTERFACE_MODE_CLIENT) {
            IpConfiguration ipConfiguration = getOrCreateIpConfiguration(iface);
            Log.d(TAG, "Tracking interface in client mode: " + iface);
            mFactory.addInterface(iface, hwAddress, ipConfiguration, nc);
        } else {
            maybeUpdateServerModeInterfaceState(iface, true);
        }

        // Note: if the interface already has link (e.g., if we crashed and got
        // restarted while it was running), we need to fake a link up notification so we
        // start configuring it.
        if (NetdUtils.hasFlag(config, "running")) {
            updateInterfaceState(iface, true);
        }
    }

    private void updateInterfaceState(String iface, boolean up) {
        updateInterfaceState(iface, up, null /* listener */);
    }

    private void updateInterfaceState(@NonNull final String iface, final boolean up,
            @Nullable final INetworkInterfaceOutcomeReceiver listener) {
        final int mode = getInterfaceMode(iface);
        final boolean factoryLinkStateUpdated = (mode == INTERFACE_MODE_CLIENT)
                && mFactory.updateInterfaceLinkState(iface, up, listener);

        if (factoryLinkStateUpdated) {
            broadcastInterfaceStateChange(iface);
        }
    }

    private void maybeUpdateServerModeInterfaceState(String iface, boolean available) {
        if (available == mTetheredInterfaceWasAvailable || !iface.equals(mDefaultInterface)) return;

        Log.d(TAG, (available ? "Tracking" : "No longer tracking")
                + " interface in server mode: " + iface);

        final int pendingCbs = mTetheredInterfaceRequests.beginBroadcast();
        for (int i = 0; i < pendingCbs; i++) {
            ITetheredInterfaceCallback item = mTetheredInterfaceRequests.getBroadcastItem(i);
            if (available) {
                notifyTetheredInterfaceAvailable(item, iface);
            } else {
                notifyTetheredInterfaceUnavailable(item);
            }
        }
        mTetheredInterfaceRequests.finishBroadcast();
        mTetheredInterfaceWasAvailable = available;
    }

    private void maybeTrackInterface(String iface) {
        if (!iface.matches(mIfaceMatch)) {
            return;
        }

        // If we don't already track this interface, and if this interface matches
        // our regex, start tracking it.
        if (mFactory.hasInterface(iface) || iface.equals(mDefaultInterface)) {
            if (DBG) Log.w(TAG, "Ignoring already-tracked interface " + iface);
            return;
        }
        if (DBG) Log.i(TAG, "maybeTrackInterface: " + iface);

        // TODO: avoid making an interface default if it has configured NetworkCapabilities.
        if (mDefaultInterface == null) {
            mDefaultInterface = iface;
        }

        if (mIpConfigForDefaultInterface != null) {
            updateIpConfiguration(iface, mIpConfigForDefaultInterface);
            mIpConfigForDefaultInterface = null;
        }

        addInterface(iface);

        broadcastInterfaceStateChange(iface);
    }

    private void trackAvailableInterfaces() {
        try {
            final String[] ifaces = mNetd.interfaceGetList();
            for (String iface : ifaces) {
                maybeTrackInterface(iface);
            }
        } catch (RemoteException | ServiceSpecificException e) {
            Log.e(TAG, "Could not get list of interfaces " + e);
        }
    }

    @VisibleForTesting
    class InterfaceObserver extends BaseNetdUnsolicitedEventListener {

        @Override
        public void onInterfaceLinkStateChanged(String iface, boolean up) {
            if (DBG) {
                Log.i(TAG, "interfaceLinkStateChanged, iface: " + iface + ", up: " + up);
            }
            mHandler.post(() -> {
                if (mEthernetState == ETHERNET_STATE_DISABLED) return;
                updateInterfaceState(iface, up);
            });
        }

        @Override
        public void onInterfaceAdded(String iface) {
            if (DBG) {
                Log.i(TAG, "onInterfaceAdded, iface: " + iface);
            }
            mHandler.post(() -> {
                if (mEthernetState == ETHERNET_STATE_DISABLED) return;
                maybeTrackInterface(iface);
            });
        }

        @Override
        public void onInterfaceRemoved(String iface) {
            if (DBG) {
                Log.i(TAG, "onInterfaceRemoved, iface: " + iface);
            }
            mHandler.post(() -> {
                if (mEthernetState == ETHERNET_STATE_DISABLED) return;
                stopTrackingInterface(iface);
            });
        }
    }

    private static class ListenerInfo {

        boolean canUseRestrictedNetworks = false;

        ListenerInfo(boolean canUseRestrictedNetworks) {
            this.canUseRestrictedNetworks = canUseRestrictedNetworks;
        }
    }

    /**
     * Parses an Ethernet interface configuration
     *
     * @param configString represents an Ethernet configuration in the following format: {@code
     * <interface name|mac address>;[Network Capabilities];[IP config];[Override Transport]}
     */
    private void parseEthernetConfig(String configString) {
        final EthernetTrackerConfig config = createEthernetTrackerConfig(configString);
        NetworkCapabilities nc = createNetworkCapabilities(
                !TextUtils.isEmpty(config.mCapabilities)  /* clear default capabilities */,
                config.mCapabilities, config.mTransport).build();
        mNetworkCapabilities.put(config.mIface, nc);

        if (null != config.mIpConfig) {
            IpConfiguration ipConfig = parseStaticIpConfiguration(config.mIpConfig);
            mIpConfigurations.put(config.mIface, ipConfig);
        }
    }

    @VisibleForTesting
    static EthernetTrackerConfig createEthernetTrackerConfig(@NonNull final String configString) {
        Objects.requireNonNull(configString, "EthernetTrackerConfig requires non-null config");
        return new EthernetTrackerConfig(configString.split(";", /* limit of tokens */ 4));
    }

    private static NetworkCapabilities createDefaultNetworkCapabilities(boolean isTestIface) {
        NetworkCapabilities.Builder builder = createNetworkCapabilities(
                false /* clear default capabilities */, null, null)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_CONGESTED)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_SUSPENDED)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VCN_MANAGED);

        if (isTestIface) {
            builder.addTransportType(NetworkCapabilities.TRANSPORT_TEST);
        } else {
            builder.addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        }

        return builder.build();
    }

    /**
     * Parses a static list of network capabilities
     *
     * @param clearDefaultCapabilities Indicates whether or not to clear any default capabilities
     * @param commaSeparatedCapabilities A comma separated string list of integer encoded
     *                                   NetworkCapability.NET_CAPABILITY_* values
     * @param overrideTransport A string representing a single integer encoded override transport
     *                          type. Must be one of the NetworkCapability.TRANSPORT_*
     *                          values. TRANSPORT_VPN is not supported. Errors with input
     *                          will cause the override to be ignored.
     */
    @VisibleForTesting
    static NetworkCapabilities.Builder createNetworkCapabilities(
            boolean clearDefaultCapabilities, @Nullable String commaSeparatedCapabilities,
            @Nullable String overrideTransport) {

        final NetworkCapabilities.Builder builder = clearDefaultCapabilities
                ? NetworkCapabilities.Builder.withoutDefaultCapabilities()
                : new NetworkCapabilities.Builder();

        // Determine the transport type. If someone has tried to define an override transport then
        // attempt to add it. Since we can only have one override, all errors with it will
        // gracefully default back to TRANSPORT_ETHERNET and warn the user. VPN is not allowed as an
        // override type. Wifi Aware and LoWPAN are currently unsupported as well.
        int transport = NetworkCapabilities.TRANSPORT_ETHERNET;
        if (!TextUtils.isEmpty(overrideTransport)) {
            try {
                int parsedTransport = Integer.valueOf(overrideTransport);
                if (parsedTransport == NetworkCapabilities.TRANSPORT_VPN
                        || parsedTransport == NetworkCapabilities.TRANSPORT_WIFI_AWARE
                        || parsedTransport == NetworkCapabilities.TRANSPORT_LOWPAN) {
                    Log.e(TAG, "Override transport '" + parsedTransport + "' is not supported. "
                            + "Defaulting to TRANSPORT_ETHERNET");
                } else {
                    transport = parsedTransport;
                }
            } catch (NumberFormatException nfe) {
                Log.e(TAG, "Override transport type '" + overrideTransport + "' "
                        + "could not be parsed. Defaulting to TRANSPORT_ETHERNET");
            }
        }

        // Apply the transport. If the user supplied a valid number that is not a valid transport
        // then adding will throw an exception. Default back to TRANSPORT_ETHERNET if that happens
        try {
            builder.addTransportType(transport);
        } catch (IllegalArgumentException iae) {
            Log.e(TAG, transport + " is not a valid NetworkCapability.TRANSPORT_* value. "
                    + "Defaulting to TRANSPORT_ETHERNET");
            builder.addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET);
        }

        builder.setLinkUpstreamBandwidthKbps(100 * 1000);
        builder.setLinkDownstreamBandwidthKbps(100 * 1000);

        if (!TextUtils.isEmpty(commaSeparatedCapabilities)) {
            for (String strNetworkCapability : commaSeparatedCapabilities.split(",")) {
                if (!TextUtils.isEmpty(strNetworkCapability)) {
                    try {
                        builder.addCapability(Integer.valueOf(strNetworkCapability));
                    } catch (NumberFormatException nfe) {
                        Log.e(TAG, "Capability '" + strNetworkCapability + "' could not be parsed");
                    } catch (IllegalArgumentException iae) {
                        Log.e(TAG, strNetworkCapability + " is not a valid "
                                + "NetworkCapability.NET_CAPABILITY_* value");
                    }
                }
            }
        }
        // Ethernet networks have no way to update the following capabilities, so they always
        // have them.
        builder.addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING);
        builder.addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_CONGESTED);
        builder.addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_SUSPENDED);

        return builder;
    }

    /**
     * Parses static IP configuration.
     *
     * @param staticIpConfig represents static IP configuration in the following format: {@code
     * ip=<ip-address/mask> gateway=<ip-address> dns=<comma-sep-ip-addresses>
     *     domains=<comma-sep-domains>}
     */
    @VisibleForTesting
    static IpConfiguration parseStaticIpConfiguration(String staticIpConfig) {
        final StaticIpConfiguration.Builder staticIpConfigBuilder =
                new StaticIpConfiguration.Builder();

        for (String keyValueAsString : staticIpConfig.trim().split(" ")) {
            if (TextUtils.isEmpty(keyValueAsString)) continue;

            String[] pair = keyValueAsString.split("=");
            if (pair.length != 2) {
                throw new IllegalArgumentException("Unexpected token: " + keyValueAsString
                        + " in " + staticIpConfig);
            }

            String key = pair[0];
            String value = pair[1];

            switch (key) {
                case "ip":
                    staticIpConfigBuilder.setIpAddress(new LinkAddress(value));
                    break;
                case "domains":
                    staticIpConfigBuilder.setDomains(value);
                    break;
                case "gateway":
                    staticIpConfigBuilder.setGateway(InetAddress.parseNumericAddress(value));
                    break;
                case "dns": {
                    ArrayList<InetAddress> dnsAddresses = new ArrayList<>();
                    for (String address: value.split(",")) {
                        dnsAddresses.add(InetAddress.parseNumericAddress(address));
                    }
                    staticIpConfigBuilder.setDnsServers(dnsAddresses);
                    break;
                }
                default : {
                    throw new IllegalArgumentException("Unexpected key: " + key
                            + " in " + staticIpConfig);
                }
            }
        }
        return createIpConfiguration(staticIpConfigBuilder.build());
    }

    private static IpConfiguration createIpConfiguration(
            @NonNull final StaticIpConfiguration staticIpConfig) {
        return new IpConfiguration.Builder().setStaticIpConfiguration(staticIpConfig).build();
    }

    private IpConfiguration getOrCreateIpConfiguration(String iface) {
        IpConfiguration ret = mIpConfigurations.get(iface);
        if (ret != null) return ret;
        ret = new IpConfiguration();
        ret.setIpAssignment(IpAssignment.DHCP);
        ret.setProxySettings(ProxySettings.NONE);
        return ret;
    }

    private void updateIfaceMatchRegexp() {
        final String match = mDeps.getInterfaceRegexFromResource(mContext);
        mIfaceMatch = mIncludeTestInterfaces
                ? "(" + match + "|" + TEST_IFACE_REGEXP + ")"
                : match;
        Log.d(TAG, "Interface match regexp set to '" + mIfaceMatch + "'");
    }

    /**
     * Validate if a given interface is valid for testing.
     *
     * @param iface the name of the interface to validate.
     * @return {@code true} if test interfaces are enabled and the given {@code iface} has a test
     * interface prefix, {@code false} otherwise.
     */
    public boolean isValidTestInterface(@NonNull final String iface) {
        return mIncludeTestInterfaces && iface.matches(TEST_IFACE_REGEXP);
    }

    private void postAndWaitForRunnable(Runnable r) {
        final ConditionVariable cv = new ConditionVariable();
        if (mHandler.post(() -> {
            r.run();
            cv.open();
        })) {
            cv.block(2000L);
        }
    }

    @VisibleForTesting(visibility = PACKAGE)
    protected void setEthernetEnabled(boolean enabled) {
        mHandler.post(() -> {
            int newState = enabled ? ETHERNET_STATE_ENABLED : ETHERNET_STATE_DISABLED;
            if (mEthernetState == newState) return;

            mEthernetState = newState;

            if (enabled) {
                trackAvailableInterfaces();
            } else {
                // TODO: maybe also disable server mode interface as well.
                untrackFactoryInterfaces();
            }
            broadcastEthernetStateChange(mEthernetState);
        });
    }

    private void untrackFactoryInterfaces() {
        for (String iface : mFactory.getAvailableInterfaces(true /* includeRestricted */)) {
            stopTrackingInterface(iface);
        }
    }

    private void unicastEthernetStateChange(@NonNull IEthernetServiceListener listener,
            int state) {
        ensureRunningOnEthernetServiceThread();
        try {
            listener.onEthernetStateChanged(state);
        } catch (RemoteException e) {
            // Do nothing here.
        }
    }

    private void broadcastEthernetStateChange(int state) {
        ensureRunningOnEthernetServiceThread();
        final int n = mListeners.beginBroadcast();
        for (int i = 0; i < n; i++) {
            try {
                mListeners.getBroadcastItem(i).onEthernetStateChanged(state);
            } catch (RemoteException e) {
                // Do nothing here.
            }
        }
        mListeners.finishBroadcast();
    }

    void dump(FileDescriptor fd, IndentingPrintWriter pw, String[] args) {
        postAndWaitForRunnable(() -> {
            pw.println(getClass().getSimpleName());
            pw.println("Ethernet State: "
                    + (mEthernetState == ETHERNET_STATE_ENABLED ? "enabled" : "disabled"));
            pw.println("Ethernet interface name filter: " + mIfaceMatch);
            pw.println("Default interface: " + mDefaultInterface);
            pw.println("Default interface mode: " + mDefaultInterfaceMode);
            pw.println("Tethered interface requests: "
                    + mTetheredInterfaceRequests.getRegisteredCallbackCount());
            pw.println("Listeners: " + mListeners.getRegisteredCallbackCount());
            pw.println("IP Configurations:");
            pw.increaseIndent();
            for (String iface : mIpConfigurations.keySet()) {
                pw.println(iface + ": " + mIpConfigurations.get(iface));
            }
            pw.decreaseIndent();
            pw.println();

            pw.println("Network Capabilities:");
            pw.increaseIndent();
            for (String iface : mNetworkCapabilities.keySet()) {
                pw.println(iface + ": " + mNetworkCapabilities.get(iface));
            }
            pw.decreaseIndent();
            pw.println();

            mFactory.dump(fd, pw, args);
        });
    }

    @VisibleForTesting
    static class EthernetTrackerConfig {
        final String mIface;
        final String mCapabilities;
        final String mIpConfig;
        final String mTransport;

        EthernetTrackerConfig(@NonNull final String[] tokens) {
            Objects.requireNonNull(tokens, "EthernetTrackerConfig requires non-null tokens");
            mIface = tokens[0];
            mCapabilities = tokens.length > 1 ? tokens[1] : null;
            mIpConfig = tokens.length > 2 && !TextUtils.isEmpty(tokens[2]) ? tokens[2] : null;
            mTransport = tokens.length > 3 ? tokens[3] : null;
        }
    }
}
