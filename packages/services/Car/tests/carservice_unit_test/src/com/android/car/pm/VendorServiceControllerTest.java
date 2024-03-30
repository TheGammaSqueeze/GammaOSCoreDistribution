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

import static android.car.test.mocks.CarArgumentMatchers.isUserHandle;


import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.fail;
import static org.mockito.Mockito.when;

import android.annotation.UserIdInt;
import android.app.ActivityManager;
import android.car.test.mocks.AbstractExtendedMockitoTestCase;
import android.car.testapi.BlockingUserLifecycleListener;
import android.car.user.CarUserManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.UserInfo;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.test.core.app.ApplicationProvider;

import com.android.car.CarLocalServices;
import com.android.car.CarUxRestrictionsManagerService;
import com.android.car.hal.UserHalService;
import com.android.car.internal.common.CommonConstants.UserLifecycleEventType;
import com.android.car.user.CarUserService;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.util.Preconditions;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

public final class VendorServiceControllerTest extends AbstractExtendedMockitoTestCase {
    private static final String TAG = VendorServiceControllerTest.class.getSimpleName();

    // TODO(b/152069895): decrease value once refactored. In fact, it should not even use
    // runWithScissors(), but only rely on CountdownLatches
    private static final long DEFAULT_TIMEOUT_MS = 5_000;

    private static final int FG_USER_ID = 13;

    private static final String SERVICE_BIND_ALL_USERS_ASAP = "com.android.car/.AllUsersService";
    private static final String SERVICE_BIND_FG_USER_UNLOCKED = "com.android.car/.ForegroundUsers";
    private static final String SERVICE_BIND_FG_USER_POST_UNLOCKED =
            "com.android.car/.ForegroundUsersPostUnlocked";
    private static final String SERVICE_START_SYSTEM_UNLOCKED = "com.android.car/.SystemUser";

    private static final String[] FAKE_SERVICES = new String[] {
            SERVICE_BIND_ALL_USERS_ASAP + "#bind=bind,user=all,trigger=asap",
            SERVICE_BIND_FG_USER_UNLOCKED + "#bind=bind,user=foreground,trigger=userUnlocked",
            SERVICE_BIND_FG_USER_POST_UNLOCKED
                    + "#bind=bind,user=foreground,trigger=userPostUnlocked",
            SERVICE_START_SYSTEM_UNLOCKED + "#bind=start,user=system,trigger=userUnlocked"
    };

    @Mock
    private Resources mResources;

    @Mock
    private UserManager mUserManager;

    @Mock
    private UserHalService mUserHal;

    @Mock
    private CarUxRestrictionsManagerService mUxRestrictionService;

    @Mock
    private CarPackageManagerService mCarPackageManagerService;

    private ServiceLauncherContext mContext;
    private CarUserService mCarUserService;
    private VendorServiceController mController;

    public VendorServiceControllerTest() {
        super(VendorServiceController.TAG);
    }

    @Override
    protected void onSessionBuilder(CustomMockitoSessionBuilder session) {
        session.spyStatic(ActivityManager.class);
    }

    @Before
    public void setUp() {
        mContext = new ServiceLauncherContext(ApplicationProvider.getApplicationContext());

        mCarUserService = new CarUserService(mContext, mUserHal, mUserManager,
                /* maxRunningUsers= */ 2, mUxRestrictionService, mCarPackageManagerService);
        CarLocalServices.addService(CarUserService.class, mCarUserService);

        mController = new VendorServiceController(mContext, Looper.getMainLooper());

        UserInfo persistentFgUser = new UserInfo(FG_USER_ID, "persistent user", /* flags= */ 0);
        when(mUserManager.getUserInfo(FG_USER_ID)).thenReturn(persistentFgUser);

        when(mResources.getStringArray(com.android.car.R.array.config_earlyStartupServices))
                .thenReturn(FAKE_SERVICES);
    }

    @After
    public void tearDown() {
        CarLocalServices.removeServiceForTest(CarUserService.class);
    }

    @Test
    public void init_nothingConfigured() {
        when(mResources.getStringArray(com.android.car.R.array.config_earlyStartupServices))
                .thenReturn(new String[0]);

        mController.init();

        mContext.verifyNoMoreServiceLaunches();
    }

    @Test
    public void init_systemUser() throws Exception {
        mContext.expectServices(SERVICE_BIND_ALL_USERS_ASAP);
        mockGetCurrentUser(UserHandle.USER_SYSTEM);
        mController.init();

        mContext.assertRecentBoundService(SERVICE_BIND_ALL_USERS_ASAP);
        mContext.verifyNoMoreServiceLaunches();
    }

    @Test
    public void systemUserUnlocked() throws Exception {
        mController.init();
        mContext.reset();

        // TODO(b/152069895): must refactor this test because
        // SERVICE_BIND_ALL_USERS_ASAP is bound twice (users 0 and 10)
        mContext.expectServices(SERVICE_START_SYSTEM_UNLOCKED);

        // Unlock system user
        mockUserUnlock(UserHandle.USER_SYSTEM);
        sendUserLifecycleEvent(CarUserManager.USER_LIFECYCLE_EVENT_TYPE_UNLOCKED,
                UserHandle.USER_SYSTEM);

        mContext.assertRecentStartedService(SERVICE_START_SYSTEM_UNLOCKED);
        mContext.verifyNoMoreServiceLaunches();
    }

    @Test
    public void fgUserUnlocked() throws Exception {
        mockGetCurrentUser(UserHandle.USER_SYSTEM);
        mController.init();
        mContext.reset();

        mContext.expectServices(SERVICE_BIND_ALL_USERS_ASAP, SERVICE_BIND_FG_USER_UNLOCKED,
                SERVICE_BIND_FG_USER_POST_UNLOCKED);

        // Switch user to foreground
        mockGetCurrentUser(FG_USER_ID);
        // TODO(b/155918094): Update this test,
        UserInfo nullUser = new UserInfo(UserHandle.USER_NULL, "null user", /* flags= */ 0);
        when(mUserManager.getUserInfo(UserHandle.USER_NULL)).thenReturn(nullUser);
        sendUserLifecycleEvent(CarUserManager.USER_LIFECYCLE_EVENT_TYPE_SWITCHING, FG_USER_ID);

        // Expect only services with ASAP trigger to be started
        mContext.assertRecentBoundService(SERVICE_BIND_ALL_USERS_ASAP);
        mContext.verifyNoMoreServiceLaunches();

        // Unlock foreground user
        mockUserUnlock(FG_USER_ID);
        sendUserLifecycleEvent(CarUserManager.USER_LIFECYCLE_EVENT_TYPE_UNLOCKED, FG_USER_ID);

        mContext.assertRecentBoundService(SERVICE_BIND_FG_USER_UNLOCKED);
        mContext.verifyNoMoreServiceLaunches();

        // Send USER_POST_UNLOCKED event.
        sendUserLifecycleEvent(CarUserManager.USER_LIFECYCLE_EVENT_TYPE_POST_UNLOCKED, FG_USER_ID);

        mContext.assertRecentBoundService(SERVICE_BIND_FG_USER_POST_UNLOCKED);
        mContext.verifyNoMoreServiceLaunches();
    }

    @Test
    public void packageChanged_attemptsRebind() throws Exception {
        mockGetCurrentUser(UserHandle.USER_SYSTEM);
        mController.init();
        mContext.reset();

        mContext.expectServices(SERVICE_BIND_ALL_USERS_ASAP, SERVICE_BIND_FG_USER_UNLOCKED,
                SERVICE_BIND_FG_USER_POST_UNLOCKED);

        // Switch user to foreground
        mockGetCurrentUser(FG_USER_ID);
        UserInfo nullUser = new UserInfo(UserHandle.USER_NULL, "null user", /* flags= */ 0);
        when(mUserManager.getUserInfo(UserHandle.USER_NULL)).thenReturn(nullUser);
        sendUserLifecycleEvent(CarUserManager.USER_LIFECYCLE_EVENT_TYPE_SWITCHING, FG_USER_ID);
        mContext.assertRecentBoundService(SERVICE_BIND_ALL_USERS_ASAP);
        mockUserUnlock(FG_USER_ID);

        // assertRecentBoundService() is important after every sendUserLifecycleEvent to ensure
        // that the event has been handled completely.
        sendUserLifecycleEvent(CarUserManager.USER_LIFECYCLE_EVENT_TYPE_UNLOCKED, FG_USER_ID);
        mContext.assertRecentBoundService(SERVICE_BIND_FG_USER_UNLOCKED);
        sendUserLifecycleEvent(CarUserManager.USER_LIFECYCLE_EVENT_TYPE_POST_UNLOCKED, FG_USER_ID);
        mContext.assertRecentBoundService(SERVICE_BIND_FG_USER_POST_UNLOCKED);

        Intent packageIntent = new Intent(Intent.ACTION_PACKAGE_CHANGED);
        int appId = 123;
        packageIntent.setData(new Uri.Builder().path("Any package").build());
        packageIntent.putExtra(Intent.EXTRA_UID, UserHandle.getUid(FG_USER_ID, appId));
        mContext.mPackageChangeReceiver.onReceive(mContext, packageIntent);
        runOnMainThreadAndWaitForIdle(() -> {});

        assertThat(((VendorServiceController.VendorServiceConnection)
                mContext.mBoundServiceToConnectionMap.get(SERVICE_BIND_FG_USER_POST_UNLOCKED))
                .isPendingRebind()).isTrue();
        assertThat(((VendorServiceController.VendorServiceConnection)
                mContext.mBoundServiceToConnectionMap.get(SERVICE_BIND_FG_USER_UNLOCKED))
                .isPendingRebind()).isTrue();
    }

    @Test
    public void packageRemoved_unbindsTheService() throws Exception {
        mockGetCurrentUser(UserHandle.USER_SYSTEM);
        mController.init();
        mContext.reset();
        mContext.expectServices(SERVICE_BIND_ALL_USERS_ASAP, SERVICE_BIND_FG_USER_UNLOCKED,
                SERVICE_BIND_FG_USER_POST_UNLOCKED);

        // Switch user to foreground
        mockGetCurrentUser(FG_USER_ID);
        UserInfo nullUser = new UserInfo(UserHandle.USER_NULL, "null user", /* flags= */ 0);
        when(mUserManager.getUserInfo(UserHandle.USER_NULL)).thenReturn(nullUser);
        sendUserLifecycleEvent(CarUserManager.USER_LIFECYCLE_EVENT_TYPE_SWITCHING, FG_USER_ID);
        mContext.assertRecentBoundService(SERVICE_BIND_ALL_USERS_ASAP);
        mockUserUnlock(FG_USER_ID);

        // assertRecentBoundService() is important after every sendUserLifecycleEvent to ensure
        // that the event has been handled completely.
        sendUserLifecycleEvent(CarUserManager.USER_LIFECYCLE_EVENT_TYPE_UNLOCKED, FG_USER_ID);
        mContext.assertRecentBoundService(SERVICE_BIND_FG_USER_UNLOCKED);
        sendUserLifecycleEvent(CarUserManager.USER_LIFECYCLE_EVENT_TYPE_POST_UNLOCKED, FG_USER_ID);
        mContext.assertRecentBoundService(SERVICE_BIND_FG_USER_POST_UNLOCKED);

        Intent packageIntent = new Intent(Intent.ACTION_PACKAGE_REMOVED);
        int appId = 123;
        packageIntent.setData(new Uri.Builder().path("com.android.car").build());
        packageIntent.putExtra(Intent.EXTRA_UID, UserHandle.getUid(FG_USER_ID, appId));
        mContext.mPackageChangeReceiver.onReceive(mContext, packageIntent);
        runOnMainThreadAndWaitForIdle(() -> {});

        mContext.assertServiceNotBound(SERVICE_BIND_FG_USER_POST_UNLOCKED);
        mContext.assertServiceNotBound(SERVICE_BIND_FG_USER_UNLOCKED);
    }

    private static void runOnMainThreadAndWaitForIdle(Runnable r) {
        Handler.getMain().runWithScissors(r, DEFAULT_TIMEOUT_MS);
        // Run empty runnable to make sure that all posted handlers are done.
        Handler.getMain().runWithScissors(() -> { }, DEFAULT_TIMEOUT_MS);
    }

    private void mockUserUnlock(@UserIdInt int userId) {
        when(mUserManager.isUserUnlockingOrUnlocked(isUserHandle(userId))).thenReturn(true);
        when(mUserManager.isUserUnlockingOrUnlocked(userId)).thenReturn(true);
    }

    private static void assertHasService(List<ComponentName> recentServices, String service,
            String action) {
        assertWithMessage("Number of recent %s services", action).that(recentServices)
                .hasSize(1);
        assertWithMessage("Recent service").that(recentServices.get(0))
                .isEqualTo(ComponentName.unflattenFromString(service));
        recentServices.clear();
    }

    private void sendUserLifecycleEvent(@UserLifecycleEventType int eventType,
            @UserIdInt int userId) throws InterruptedException {
        // Adding a blocking listener to ensure CarUserService event notification is completed
        // before proceeding with test execution.
        BlockingUserLifecycleListener blockingListener =
                BlockingUserLifecycleListener.forAnyEvent().build();
        mCarUserService.addUserLifecycleListener(/* filter= */null, blockingListener);

        runOnMainThreadAndWaitForIdle(() -> mCarUserService.onUserLifecycleEvent(eventType,
                /* fromUserId= */ UserHandle.USER_NULL, userId));
        blockingListener.waitForAnyEvent();
    }

    /** Overrides framework behavior to succeed on binding/starting processes. */
    public final class ServiceLauncherContext extends ContextWrapper {

        private final Object mLock = new Object();

        @GuardedBy("mLock")
        private Map<ServiceConnection, ComponentName> mBoundConnectionToServiceMap =
                new HashMap<>();
        @GuardedBy("mLock")
        private List<ComponentName> mRecentBoundServices = new ArrayList<>();
        @GuardedBy("mLock")
        private List<ComponentName> mRecentStartedServices = new ArrayList<>();

        private final Map<String, CountDownLatch> mBoundLatches = new HashMap<>();
        private final Map<String, CountDownLatch> mStartedLatches = new HashMap<>();
        private final Map<String, ServiceConnection> mBoundServiceToConnectionMap =
                new HashMap<>();
        private BroadcastReceiver mPackageChangeReceiver;

        ServiceLauncherContext(Context base) {
            super(base);
        }

        @Override
        public Context createContextAsUser(UserHandle user, int flags) {
            Log.v(TAG, "using same context for user " + user);
            return this;
        }

        @Override
        public ComponentName startService(Intent service) {
            synchronized (mLock) {
                mRecentStartedServices.add(service.getComponent());
            }
            countdown(mStartedLatches, service, "started");
            return service.getComponent();
        }

        @Override
        public boolean bindService(Intent service, int flags, Executor executor,
                ServiceConnection conn) {
            synchronized (mLock) {
                mRecentBoundServices.add(service.getComponent());
                mBoundServiceToConnectionMap.put(service.getComponent().flattenToShortString(),
                        conn);
                mBoundConnectionToServiceMap.put(conn, service.getComponent());
                Log.v(TAG, "Added service (" + service + ") to bound intents");
            }
            conn.onServiceConnected(service.getComponent(), null);
            countdown(mBoundLatches, service, "bound");
            return true;
        }

        @Override
        public void unbindService(ServiceConnection conn) {
            synchronized (mLock) {
                ComponentName serviceComponent = mBoundConnectionToServiceMap.get(conn);
                Log.v(TAG, "Remove service (" + serviceComponent + ") from bound services");
                mRecentBoundServices.remove(serviceComponent);
                mBoundServiceToConnectionMap.remove(serviceComponent.flattenToShortString());
                mBoundConnectionToServiceMap.remove(conn);
            }
        }

        @Override
        public Resources getResources() {
            return mResources;
        }

        private void expectServices(String... services) {
            for (String service : services) {
                Log.v(TAG, "expecting service " + service);
                mBoundLatches.put(service, new CountDownLatch(1));
                mStartedLatches.put(service, new CountDownLatch(1));
            }
        }

        private void await(Map<String, CountDownLatch> latches, String service, String method)
                throws InterruptedException {
            CountDownLatch latch = latches.get(service);
            Preconditions.checkArgument(latch != null,
                    "no latch set for %s - did you call expectBoundServices()?", service);
            Log.d(TAG, "waiting " + DEFAULT_TIMEOUT_MS + "ms for " + method);
            if (!latch.await(DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                String errorMessage = method + " not called in " + DEFAULT_TIMEOUT_MS + "ms";
                Log.e(TAG, errorMessage);
                fail(errorMessage);
            }
            Log.v(TAG, "latch.await for service (" + service + ") and method ("
                    + method + ") called fine");
        }

        private void countdown(Map<String, CountDownLatch> latches, Intent service, String action) {
            String serviceName = service.getComponent().flattenToShortString();
            CountDownLatch latch = latches.get(serviceName);
            if (latch == null) {
                Log.e(TAG, "unexpected service (" + serviceName + ") " + action + ". Expected only "
                        + mBoundLatches.keySet());
            } else {
                latch.countDown();
                Log.v(TAG, "latch.countDown for service (" + service + ") and action ("
                        + action + ") called fine");
            }
        }

        void assertRecentBoundService(String service) throws InterruptedException {
            await(mBoundLatches, service, "bind()");
            synchronized (mLock) {
                assertHasService(mRecentBoundServices, service, "bound");
            }
        }

        void assertServiceNotBound(String service) throws InterruptedException {
            synchronized (mLock) {
                assertWithMessage("Service is binded.").that(mRecentBoundServices)
                        .doesNotContain(ComponentName.unflattenFromString(service));
            }
        }

        void assertRecentStartedService(String service) throws InterruptedException {
            await(mStartedLatches, service, "start()");
            synchronized (mLock) {
                assertHasService(mRecentStartedServices, service, "started");
            }
        }

        void verifyNoMoreServiceLaunches() {
            synchronized (mLock) {
                assertThat(mRecentStartedServices).isEmpty();
                assertThat(mRecentBoundServices).isEmpty();
            }
        }

        void reset() {
            synchronized (mLock) {
                mRecentStartedServices.clear();
                mRecentBoundServices.clear();
                mBoundServiceToConnectionMap.clear();
                mBoundConnectionToServiceMap.clear();
            }
        }

        @Override
        public Object getSystemService(String name) {
            if (Context.USER_SERVICE.equals(name)) {
                return mUserManager;
            }
            return super.getSystemService(name);
        }

        @Nullable
        @Override
        public Intent registerReceiverForAllUsers(@Nullable BroadcastReceiver receiver,
                IntentFilter filter, @Nullable String broadcastPermission,
                @Nullable Handler scheduler, int flags) {
            mPackageChangeReceiver = receiver;
            return null;
        }
    }
}
