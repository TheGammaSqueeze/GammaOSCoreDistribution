/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.car.pm;

import static android.car.user.CarUserManager.USER_LIFECYCLE_EVENT_TYPE_POST_UNLOCKED;
import static android.car.user.CarUserManager.USER_LIFECYCLE_EVENT_TYPE_SWITCHING;
import static android.car.user.CarUserManager.USER_LIFECYCLE_EVENT_TYPE_UNLOCKED;
import static android.content.Context.BIND_AUTO_CREATE;
import static android.os.Process.INVALID_UID;

import static com.android.car.CarLog.TAG_AM;
import static com.android.car.util.Utils.isEventAnyOfTypes;

import android.annotation.Nullable;
import android.annotation.UserIdInt;
import android.app.ActivityManager;
import android.car.builtin.util.Slogf;
import android.car.user.CarUserManager.UserLifecycleEvent;
import android.car.user.CarUserManager.UserLifecycleListener;
import android.car.user.UserLifecycleEventFilter;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.UserHandle;
import android.os.UserManager;
import android.text.TextUtils;
import android.util.Log;

import com.android.car.CarLocalServices;
import com.android.car.CarLog;
import com.android.car.R;
import com.android.car.user.CarUserService;
import com.android.internal.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/**
 * Class that responsible for controlling vendor services that was opted in to be bound/started
 * by the Car Service.
 *
 * <p>Thread-safety note: all code runs in the {@code Handler} provided in the constructor, whenever
 * possible pass {@link #mHandler} when subscribe for callbacks otherwise redirect code to the
 * handler.
 */
final class VendorServiceController implements UserLifecycleListener {

    @VisibleForTesting
    static final String TAG = CarLog.tagFor(VendorServiceController.class);

    private static final boolean DBG = Slogf.isLoggable(TAG, Log.DEBUG);
    private static final String PACKAGE_DATA_SCHEME = "package";

    private final List<VendorServiceInfo> mVendorServiceInfos = new ArrayList<>();
    // TODO(b/240607225): Synchronize access to mConnections. It can lead to unexpected behavior.
    private final Map<ConnectionKey, VendorServiceConnection> mConnections =
            new ConcurrentHashMap<>();
    private final Context mContext;
    private final UserManager mUserManager;
    private final Handler mHandler;
    private CarUserService mCarUserService;

    private final BroadcastReceiver mPackageChangeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (DBG) {
                Slogf.d(TAG_AM, "Package change received with action = %s", action);
            }

            Uri packageData = intent.getData();
            if (packageData == null) {
                Slogf.wtf(TAG_AM, "null packageData");
                return;
            }
            String packageName = packageData.getSchemeSpecificPart();
            if (packageName == null) {
                Slogf.w(TAG_AM, "null packageName");
                return;
            }
            int uid = intent.getIntExtra(Intent.EXTRA_UID, INVALID_UID);
            int userId = UserHandle.getUserHandleForUid(uid).getIdentifier();

            switch (action) {
                case Intent.ACTION_PACKAGE_CHANGED:
                    // Fall through
                case Intent.ACTION_PACKAGE_REPLACED:
                    // Fall through
                case Intent.ACTION_PACKAGE_ADDED:
                    tryToRebindConnectionsForUser(userId);
                    break;
                case Intent.ACTION_PACKAGE_REMOVED:
                    stopOrUnbindService(packageName, userId);
                    break;
                default:
                    Slogf.w(TAG_AM, "This package change event (%s) can't be handled.",
                            action);
            }
        }
    };

    VendorServiceController(Context context, Looper looper) {
        mContext = context;
        mUserManager = context.getSystemService(UserManager.class);
        mHandler = new Handler(looper);
    }

    void init() {
        if (!loadXmlConfiguration()) {
            return;  // Nothing to do
        }

        mCarUserService = CarLocalServices.getService(CarUserService.class);
        UserLifecycleEventFilter userSwitchingOrUnlockingEventFilter =
                new UserLifecycleEventFilter.Builder()
                        .addEventType(USER_LIFECYCLE_EVENT_TYPE_SWITCHING)
                        .addEventType(USER_LIFECYCLE_EVENT_TYPE_UNLOCKED)
                        .addEventType(USER_LIFECYCLE_EVENT_TYPE_POST_UNLOCKED).build();
        mCarUserService.addUserLifecycleListener(userSwitchingOrUnlockingEventFilter, this);

        startOrBindServicesIfNeeded();
        registerPackageChangeReceiver();
    }

    void release() {
        if (mVendorServiceInfos.isEmpty()) {
            Slogf.d(TAG_AM, "Releasing VendorServiceController without deep cleaning as no vendor "
                    + "service info present. ");
            return;
        }
        if (mCarUserService != null) {
            mCarUserService.removeUserLifecycleListener(this);
        }
        unregisterPackageChangeReceiver();
        for (ConnectionKey key : mConnections.keySet()) {
            stopOrUnbindService(key.mVendorServiceInfo, key.mUserHandle);
        }
        mVendorServiceInfos.clear();
        mConnections.clear();
    }

    @Override
    public void onEvent(UserLifecycleEvent event) {
        if (!isEventAnyOfTypes(TAG, event, USER_LIFECYCLE_EVENT_TYPE_SWITCHING,
                USER_LIFECYCLE_EVENT_TYPE_UNLOCKED, USER_LIFECYCLE_EVENT_TYPE_POST_UNLOCKED)) {
            return;
        }
        if (DBG) {
            Slogf.d(TAG, "onEvent(" + event + ")");
        }
        int userId = event.getUserId();
        switch (event.getEventType()) {
            case USER_LIFECYCLE_EVENT_TYPE_SWITCHING:
                mHandler.post(() -> handleOnUserSwitching(userId));
                break;
            case USER_LIFECYCLE_EVENT_TYPE_UNLOCKED:
                mHandler.post(() -> handleOnUserUnlocked(userId, /* forPostUnlock= */ false));
                break;
            case USER_LIFECYCLE_EVENT_TYPE_POST_UNLOCKED:
                mHandler.post(() -> handleOnUserUnlocked(userId, /* forPostUnlock= */ true));
                break;
            default:
                // Shouldn't happen as listener was registered with filter
                Slogf.wtf(TAG, "Invalid event: %s", event);
        }
    }

    private void registerPackageChangeReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        filter.addAction(Intent.ACTION_PACKAGE_REPLACED);
        filter.addAction(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addDataScheme(PACKAGE_DATA_SCHEME);
        mContext.registerReceiverForAllUsers(mPackageChangeReceiver, filter,
                /* broadcastPermission= */ null, /* scheduler= */ null,
                Context.RECEIVER_NOT_EXPORTED);
    }

    private void unregisterPackageChangeReceiver() {
        mContext.unregisterReceiver(mPackageChangeReceiver);
    }

    private void tryToRebindConnectionsForUser(@UserIdInt int userId) {
        for (VendorServiceConnection connection : mConnections.values()) {
            if (connection.isUser(userId)) {
                Slogf.d(TAG, "Trying to rebind connection to %s",
                        connection.mVendorServiceInfo);
                connection.tryToRebind();
            }
        }
    }

    private void handleOnUserSwitching(@UserIdInt int userId) {
        // Stop all services which do not run under foreground or system user.
        int fgUser = ActivityManager.getCurrentUser();
        if (fgUser != userId) {
            Slogf.w(TAG, "Received userSwitch event for user " + userId
                    + " while current foreground user is " + fgUser + "."
                    + " Ignore the switch user event.");
            return;
        }

        for (VendorServiceConnection connection : mConnections.values()) {
            int connectedUserId = connection.mUser.getIdentifier();
            if (connectedUserId != UserHandle.SYSTEM.getIdentifier() && connectedUserId != userId) {
                connection.stopOrUnbindService();
            }
        }

        if (userId != UserHandle.SYSTEM.getIdentifier()) {
            startOrBindServicesForUser(UserHandle.of(userId), /* forPostUnlock= */ null);
        } else {
            Slogf.wtf(TAG, "Unexpected to receive switch user event for system user");
        }
    }

    private void handleOnUserUnlocked(@UserIdInt int userId, boolean forPostUnlock) {
        int currentUserId = ActivityManager.getCurrentUser();

        if (DBG) {
            Slogf.i(TAG, "handleOnUserUnlocked(): user=%d, currentUser=%d", userId, currentUserId);
        }
        if ((userId == currentUserId || userId == UserHandle.SYSTEM.getIdentifier())) {
            startOrBindServicesForUser(UserHandle.of(userId), forPostUnlock);
        }
    }

    private void startOrBindServicesForUser(UserHandle user, @Nullable Boolean forPostUnlock) {
        boolean unlocked = mUserManager.isUserUnlockingOrUnlocked(user);
        boolean systemUser = UserHandle.SYSTEM.equals(user);
        for (VendorServiceInfo service: mVendorServiceInfos) {
            if (forPostUnlock != null && service.shouldStartOnPostUnlock() != forPostUnlock) {
                continue;
            }
            boolean userScopeChecked = (!systemUser && service.isForegroundUserService())
                    || (systemUser && service.isSystemUserService());
            boolean triggerChecked = service.shouldStartAsap() || unlocked;

            if (userScopeChecked && triggerChecked) {
                startOrBindService(service, user);
            }
        }
    }

    private void startOrBindServicesIfNeeded() {
        int userId = ActivityManager.getCurrentUser();
        startOrBindServicesForUser(UserHandle.SYSTEM, /* forPostUnlock= */ null);
        if (userId > 0) {
            startOrBindServicesForUser(UserHandle.of(userId), /* forPostUnlock= */ null);
        }
    }

    private void startOrBindService(VendorServiceInfo service, UserHandle user) {
        ConnectionKey key = ConnectionKey.of(service, user);
        VendorServiceConnection connection = getOrCreateConnection(key);
        if (!connection.startOrBindService()) {
            Slogf.e(TAG, "Failed to start or bind service " + service);
            mConnections.remove(key);
        }
    }

    private void stopOrUnbindService(VendorServiceInfo service, UserHandle user) {
        ConnectionKey key = ConnectionKey.of(service, user);
        VendorServiceConnection connection = mConnections.get(key);
        if (connection != null) {
            connection.stopOrUnbindService();
        }
    }

    /**
     * Unbinds the VendorServiceController from all the services with the given {@code packageName}
     * and running as {@code userId}.
     */
    private void stopOrUnbindService(String packageName, @UserIdInt int userId) {
        for (VendorServiceConnection connection : mConnections.values()) {
            if (connection.isUser(userId)
                    && packageName.equals(connection.mVendorServiceInfo.getIntent().getComponent()
                    .getPackageName())) {
                Slogf.d(TAG, "Stopping the connection to service %s",
                         connection.mVendorServiceInfo);
                connection.stopOrUnbindService();
            }
        }
    }

    private VendorServiceConnection getOrCreateConnection(ConnectionKey key) {
        VendorServiceConnection connection = mConnections.get(key);
        if (connection == null) {
            connection = new VendorServiceConnection(mContext, mHandler, key.mVendorServiceInfo,
                    key.mUserHandle);
            mConnections.put(key, connection);
        }

        return connection;
    }

    /** Loads data from XML resources and returns true if any services needs to be started/bound. */
    private boolean loadXmlConfiguration() {
        final Resources res = mContext.getResources();
        for (String rawServiceInfo: res.getStringArray(R.array.config_earlyStartupServices)) {
            if (TextUtils.isEmpty(rawServiceInfo)) {
                continue;
            }
            VendorServiceInfo service = VendorServiceInfo.parse(rawServiceInfo);
            mVendorServiceInfos.add(service);
            if (DBG) {
                Slogf.i(TAG, "Registered vendor service: " + service);
            }
        }
        Slogf.i(TAG, "Found " + mVendorServiceInfos.size()
                + " services to be started/bound");

        return !mVendorServiceInfos.isEmpty();
    }

    /**
     * Represents connection to the vendor service.
     */
    @VisibleForTesting
    public static final class VendorServiceConnection implements ServiceConnection, Executor {
        private static final int REBIND_DELAY_MS = 5000;
        private static final int MAX_RECENT_FAILURES = 5;
        private static final int FAILURE_COUNTER_RESET_TIMEOUT = 5 * 60 * 1000; // 5 min.
        private static final int MSG_REBIND = 0;
        private static final int MSG_FAILURE_COUNTER_RESET = 1;

        private int mRecentFailures = 0;
        private boolean mBound = false;
        private boolean mStarted = false;
        private boolean mStopRequested = false;
        private final VendorServiceInfo mVendorServiceInfo;
        private final UserHandle mUser;
        private final Context mUserContext;
        private final Handler mHandler;
        private final Handler mFailureHandler;

        VendorServiceConnection(Context context, Handler handler,
                VendorServiceInfo vendorServiceInfo, UserHandle user) {
            mHandler = handler;
            mVendorServiceInfo = vendorServiceInfo;
            mUser = user;
            mUserContext = context.createContextAsUser(mUser, /* flags= */ 0);

            mFailureHandler = new Handler(handler.getLooper()) {
                @Override
                public void handleMessage(Message msg) {
                    handleFailureMessage(msg);
                }
            };
        }

        @VisibleForTesting
        public boolean isPendingRebind() {
            return mFailureHandler.hasMessages(MSG_REBIND);
        }

        @Override
        public String toString() {
            return "VendorServiceConnection[user=" + mUser
                    + ", service=" + mVendorServiceInfo + "]";
        }

        private boolean isUser(@UserIdInt int userId) {
            return mUser.getIdentifier() == userId;
        }

        boolean startOrBindService() {
            if (mStarted || mBound) {
                return true;  // Already started or bound
            }

            if (DBG) {
                Slogf.d(TAG, "startOrBindService "
                        + mVendorServiceInfo.toShortString() + ", as user: " + mUser + ", bind: "
                        + mVendorServiceInfo.shouldBeBound());
            }
            mStopRequested = false;

            Intent intent = mVendorServiceInfo.getIntent();
            if (mVendorServiceInfo.shouldBeBound()) {
                return mUserContext.bindService(intent, BIND_AUTO_CREATE, /* executor= */ this,
                        /* conn= */ this);
            } else if (mVendorServiceInfo.shouldBeStartedInForeground()) {
                mStarted = mUserContext.startForegroundService(intent) != null;
                return mStarted;
            } else {
                mStarted = mUserContext.startService(intent) != null;
                return mStarted;
            }
        }

        void stopOrUnbindService() {
            mStopRequested = true;
            if (mStarted) {
                if (DBG) Slogf.d(TAG, "Stopping %s", this);
                mUserContext.stopService(mVendorServiceInfo.getIntent());
                mStarted = false;
            } else if (mBound) {
                if (DBG) Slogf.d(TAG, "Unbinding %s", this);
                mUserContext.unbindService(this);
                mBound = false;
            }
        }

        @Override // From Executor
        public void execute(Runnable command) {
            mHandler.post(command);
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mBound = true;
            if (DBG) {
                Slogf.d(TAG, "onServiceConnected, name: %s", name);
            }
            if (mStopRequested) {
                stopOrUnbindService();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mBound = false;
            if (DBG) {
                Slogf.d(TAG, "onServiceDisconnected, name: " + name);
            }
            tryToRebind();
        }

        @Override
        public void onBindingDied(ComponentName name) {
            mBound = false;
            if (DBG) {
                Slogf.d(TAG, "onBindingDied, name: " + name);
            }
            tryToRebind();
        }

        private void tryToRebind() {
            if (mStopRequested) {
                return;
            }

            if (mFailureHandler.hasMessages(MSG_REBIND)) {
                if (DBG) {
                    Slogf.d(TAG, "Rebind already scheduled for "
                            + mVendorServiceInfo.toShortString());
                }
                return;
            }

            if (UserHandle.of(ActivityManager.getCurrentUser()).equals(mUser)
                    || UserHandle.SYSTEM.equals(mUser)) {
                mFailureHandler.sendMessageDelayed(
                        mFailureHandler.obtainMessage(MSG_REBIND), REBIND_DELAY_MS);
                scheduleResetFailureCounter();
            } else {
                Slogf.w(TAG, "No need to rebind anymore as the user " + mUser
                        + " is no longer in foreground.");
            }
        }

        private void scheduleResetFailureCounter() {
            mFailureHandler.removeMessages(MSG_FAILURE_COUNTER_RESET);
            mFailureHandler.sendMessageDelayed(
                    mFailureHandler.obtainMessage(MSG_FAILURE_COUNTER_RESET),
                    FAILURE_COUNTER_RESET_TIMEOUT);
        }

        private void handleFailureMessage(Message msg) {
            switch (msg.what) {
                case MSG_REBIND: {
                    if (mRecentFailures < MAX_RECENT_FAILURES && !mBound) {
                        Slogf.i(TAG, "Attempting to rebind to the service "
                                + mVendorServiceInfo.toShortString());
                        ++mRecentFailures;
                        startOrBindService();
                    } else {
                        Slogf.w(TAG, "Exceeded maximum number of attempts to rebind"
                                + "to the service " + mVendorServiceInfo.toShortString());
                    }
                    break;
                }
                case MSG_FAILURE_COUNTER_RESET:
                    mRecentFailures = 0;
                    break;
                default:
                    Slogf.e(TAG, "Unexpected message received in failure handler: " + msg.what);
            }
        }
    }

    /** Defines a key in the HashMap to store connection on per user and vendor service basis */
    private static class ConnectionKey {
        private final UserHandle mUserHandle;
        private final VendorServiceInfo mVendorServiceInfo;

        private ConnectionKey(VendorServiceInfo service, UserHandle user) {
            mVendorServiceInfo = service;
            mUserHandle = user;
        }

        static ConnectionKey of(VendorServiceInfo service, UserHandle user) {
            return new ConnectionKey(service, user);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof ConnectionKey)) {
                return false;
            }
            ConnectionKey that = (ConnectionKey) o;
            return Objects.equals(mUserHandle, that.mUserHandle)
                    && Objects.equals(mVendorServiceInfo, that.mVendorServiceInfo);
        }

        @Override
        public int hashCode() {
            return Objects.hash(mUserHandle, mVendorServiceInfo);
        }
    }
}
