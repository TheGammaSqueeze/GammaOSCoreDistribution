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

package com.android.bedstead.testapp;

import android.accounts.AccountManager;
import android.accounts.RemoteAccountManager;
import android.accounts.RemoteAccountManagerWrapper;
import android.app.admin.DevicePolicyManager;
import android.app.admin.RemoteDevicePolicyManager;
import android.app.admin.RemoteDevicePolicyManagerWrapper;
import android.bluetooth.BluetoothManager;
import android.bluetooth.RemoteBluetoothManager;
import android.bluetooth.RemoteBluetoothManagerWrapper;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.content.RemoteContext;
import android.content.RemoteContextWrapper;
import android.content.pm.CrossProfileApps;
import android.content.pm.PackageManager;
import android.content.pm.RemoteCrossProfileApps;
import android.content.pm.RemoteCrossProfileAppsWrapper;
import android.content.pm.RemoteLauncherApps;
import android.content.pm.RemoteLauncherAppsWrapper;
import android.content.pm.RemotePackageManager;
import android.content.pm.RemotePackageManagerWrapper;
import android.net.wifi.RemoteWifiManager;
import android.net.wifi.RemoteWifiManagerWrapper;
import android.net.wifi.WifiManager;
import android.os.HardwarePropertiesManager;
import android.os.RemoteHardwarePropertiesManager;
import android.os.RemoteHardwarePropertiesManagerWrapper;
import android.os.RemoteUserManager;
import android.os.RemoteUserManagerWrapper;
import android.os.UserManager;
import android.security.KeyChain;
import android.security.RemoteKeyChain;
import android.security.RemoteKeyChainWrapper;

import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.exceptions.NeneException;
import com.android.bedstead.nene.packages.ProcessReference;
import com.android.bedstead.nene.users.UserReference;

import com.google.android.enterprise.connectedapps.ConnectionListener;
import com.google.android.enterprise.connectedapps.CrossProfileConnector;
import com.google.android.enterprise.connectedapps.exceptions.UnavailableProfileException;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nullable;

/**
 * A reference to a specific instance of a {@link TestApp} on a given user.
 *
 * <p>The user may not exist, or the test app may not be installed on the user.
 */
public class TestAppInstance implements AutoCloseable, ConnectionListener {

    private final TestApp mTestApp;
    private final UserReference mUser;
    private final CrossProfileConnector mConnector;
    private final Map<IntentFilter, Long> mRegisteredBroadcastReceivers = new HashMap<>();
    private final ProfileTestAppController mTestAppController;
    private final TestAppActivities mTestAppActivities;
    private boolean mKeepAliveManually = false;
    private final TestAppInstancePermissions mTestAppInstancePermissions =
            new TestAppInstancePermissions(this);

    /**
     * Use {@link TestApp#install} or {@link TestApp#instance} to get an instance of
     * {@link TestAppInstance}.
     */
    public TestAppInstance(TestApp testApp, UserReference user) {
        if (testApp == null || user == null) {
            throw new NullPointerException();
        }
        mTestApp = testApp;
        mUser = user;
        mConnector = CrossProfileConnector.builder(TestApis.context().instrumentedContext())
                .setBinder(new TestAppBinder(this))
                .build();
        mConnector.registerConnectionListener(this);
        mTestAppController =
                ProfileTestAppController.create(mConnector);
        mTestAppActivities = TestAppActivities.create(this);
    }

    CrossProfileConnector connector() {
        return mConnector;
    }

    /**
     * Access activities on the test app.
     */
    public TestAppActivities activities() {
        return mTestAppActivities;
    }

    /**
     * The {@link TestApp} this instance refers to.
     */
    public TestApp testApp() {
        return mTestApp;
    }

    /**
     * See {@link TestApp#packageName()}.
     */
    public String packageName() {
       return testApp().packageName();
    }

    /**
     * The {@link UserReference} this instance refers to.
     */
    public UserReference user() {
        return mUser;
    }

    /**
     * Uninstall the {@link TestApp} from the user referenced by
     * this {@link TestAppInstance}.
     */
    public void uninstall() {
        mTestApp.uninstall(mUser);
    }

    /**
     * Register a {@link BroadcastReceiver} for a given {@link IntentFilter}.
     *
     * <p>A new {@link BroadcastReceiver} instance will be created for each {@link IntentFilter}.
     *
     * <p>Note that {@link IntentFilter} does not override {@code equals} and one broadcast receiver
     * will be registered for each instance of {@link IntentFilter} regardless of the content of the
     * {@link IntentFilter}.
     *
     * <p>As registered receivers are only active while the application is open, calling this method
     * will have the same effect as calling {@link #keepAlive()}.
     */
    public void registerReceiver(IntentFilter intentFilter) {
        registerReceiver(intentFilter, 0);
    }

    /**
     * See {@link registerReceiver(IntentFilter)}.
     */
    public void registerReceiver(IntentFilter intentFilter, int flags) {
        if (mRegisteredBroadcastReceivers.containsKey(intentFilter)) {
            return;
        }

        long receiverId = UUID.randomUUID().getMostSignificantBits();
        registerReceiver(intentFilter, receiverId, flags);
        keepAlive(/* manualKeepAlive= */ false);
    }

    private void registerReceiver(IntentFilter intentFilter, long receiverId) {
        registerReceiver(intentFilter, receiverId, 0);
    }

    private void registerReceiver(IntentFilter intentFilter, long receiverId, int flags) {
        try {
            mConnector.connect();
            mTestAppController.other().registerReceiver(receiverId, intentFilter, flags);
            mRegisteredBroadcastReceivers.put(intentFilter, receiverId);
        } catch (UnavailableProfileException e) {
            throw new IllegalStateException("Could not connect to test app", e);
        } finally {
            mConnector.stopManualConnectionManagement();
        }
    }

    /**
     * Unregister the receiver
     */
    public TestAppInstance unregisterReceiver(IntentFilter intentFilter) {
        if (!mRegisteredBroadcastReceivers.containsKey(intentFilter)) {
            return this;
        }

        long receiverId = mRegisteredBroadcastReceivers.remove(intentFilter);

        try {
            mConnector.connect();
            mTestAppController.other().unregisterReceiver(receiverId);
            mRegisteredBroadcastReceivers.put(intentFilter, receiverId);
        } catch (UnavailableProfileException e) {
            throw new IllegalStateException("Could not connect to test app", e);
        } finally {
            mConnector.stopManualConnectionManagement();
        }

        if (mRegisteredBroadcastReceivers.isEmpty() && !mKeepAliveManually) {
            stopKeepAlive();
        }

        return this;
    }

    /**
     * Starts keeping the test app process alive.
     *
     * <p>This ensures that it will receive broadcasts using registered broadcast receivers.
     *
     * @see {@link #stopKeepAlive()}.
     */
    public TestAppInstance keepAlive() {
        keepAlive(/* manualKeepAlive=*/ true);
        return this;
    }

    /**
     * Starts keep alive mode and marks it as manual so that it won't be automatically ended if
     * the last broadcast receiver is unregistered.
     */
    private void keepAlive(boolean manualKeepAlive) {
        mKeepAliveManually = manualKeepAlive;
        try {
            connector().connect();
        } catch (UnavailableProfileException e) {
            throw new IllegalStateException("Could not connect to test app. Is it installed?", e);
        }
    }

    /**
     * Stops keeping the target app alive.
     *
     * <p>This will not kill the app immediately. To do that see {@link #stop()}.
     */
    public TestAppInstance stopKeepAlive() {
        mKeepAliveManually = false;
        connector().stopManualConnectionManagement();
        return this;
    }

    /**
     * Immediately force stops the app.
     *
     * <p>This will also stop keeping the target app alive (see {@link #stopKeepAlive()}.
     */
    public TestAppInstance stop() {
        stopKeepAlive();

        ProcessReference process = mTestApp.pkg().runningProcess(mUser);
        if (process != null) {
            try {
                process.kill();
            } catch (NeneException e) {
                throw new NeneException("Error killing process... process is " + process(), e);
            }
        }

        return this;
    }

    /**
     * Gets the {@link ProcessReference} of the app, if any.
     */
    @Nullable
    public ProcessReference process() {
        return mTestApp.pkg().runningProcess(mUser);
    }

    @Override
    public void close() {
        stopKeepAlive();
        uninstall();
    }

    @Override
    public void connectionChanged() {
        if (mConnector.isConnected()) {
            // re-register broadcast receivers when re-connected
            for (Map.Entry<IntentFilter, Long> entry : mRegisteredBroadcastReceivers.entrySet()) {
                registerReceiver(entry.getKey(), entry.getValue());
            }
        }
    }

    /** Access events related to this test app. */
    public TestAppEvents events() {
        return new TestAppEvents(this);
    }

    /**
     * Access {@link DevicePolicyManager} using this test app.
     *
     * <p>Almost all methods are available. Those that are not will be missing from the interface.
     */
    public RemoteDevicePolicyManager devicePolicyManager() {
        return new RemoteDevicePolicyManagerWrapper(mConnector);
    }

    /**
     * Access {@link UserManager} using this test app.
     *
     * <p>Almost all methods are available. Those that are not will be missing from the interface.
     */
    public RemoteUserManager userManager() {
        return new RemoteUserManagerWrapper(mConnector);
    }

    /**
     * Access {@link WifiManager} using this test app.
     *
     * <p>Almost all methods are available. Those that are not will be missing from the interface.
     */
    public RemoteWifiManager wifiManager() {
        return new RemoteWifiManagerWrapper(mConnector);
    }

    /**
     * Access {@link HardwarePropertiesManager} using this test app.
     *
     * <p>Almost all methods are available. Those that are not will be missing from the interface.
     */
    public RemoteHardwarePropertiesManager hardwarePropertiesManager() {
        return new RemoteHardwarePropertiesManagerWrapper(mConnector);
    }

    /**
     * Access {@link PackageManager} using this test app.
     *
     * <p>Almost all methods are available. Those that are not will be missing from the interface.
     */
    public RemotePackageManager packageManager() {
        return new RemotePackageManagerWrapper(mConnector);
    }

    /**
     * Access {@link CrossProfileApps} using this test app.
     *
     * <p>Almost all methods are available. Those that are not will be missing from the interface.
     */
    public RemoteCrossProfileApps crossProfileApps() {
        return new RemoteCrossProfileAppsWrapper(mConnector);
    }

    /**
     * Access {@link android.content.pm.LauncherApps} using this test app.
     *
     * <p>Almost all methods are available. Those that are not will be missing from the interface.
     */
    public RemoteLauncherApps launcherApps() {
        return new RemoteLauncherAppsWrapper(mConnector);
    }

    /**
     * Access {@link AccountManager} using this test app.
     *
     * <p>Almost all methods are available. Those that are not will be missing from the interface.
     */
    public RemoteAccountManager accountManager() {
        return new RemoteAccountManagerWrapper(mConnector);
    }

    /**
     * Access the application {@link Context} using this test app.
     *
     * <p>Almost all methods are available. Those that are not will be missing from the interface.
     */
    public RemoteContext context() {
        return new RemoteContextWrapper(mConnector);
    }

    /**
     * Access the {@link KeyChain} using this test app.
     *
     * <p>Almost all methods are available. Those that are not will be missing from the interface.
     */
    public RemoteKeyChain keyChain() {
        return new RemoteKeyChainWrapper(mConnector);
    }

    /**
     * Access the {@link BluetoothManager} using this test app.
     *
     * <p>Almost all methods are available. Those that are not will be missing from the interface.
     */
    public RemoteBluetoothManager bluetoothManager() {
        return new RemoteBluetoothManagerWrapper(mConnector);
    }

    /**
     * Access permissions for this test app.
     */
    public TestAppInstancePermissions permissions() {
        return mTestAppInstancePermissions;
    }

    @Override
    public String toString() {
        return "TestAppInstance{"
                + "testApp=" + mTestApp
                + ", user=" + mUser
                + ", registeredBroadcastReceivers=" + mRegisteredBroadcastReceivers
                + ", keepAliveManually=" + mKeepAliveManually
                + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TestAppInstance)) return false;
        TestAppInstance that = (TestAppInstance) o;
        return mTestApp.equals(that.mTestApp) && mUser.equals(that.mUser);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mTestApp, mUser);
    }
}
