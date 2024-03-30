/*
 * Copyright (C) 2020 The Android Open Source Project
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
package android.car.apitest;

import static android.car.test.util.UserTestingHelper.clearUserLockCredentials;
import static android.car.test.util.UserTestingHelper.setMaxSupportedUsers;
import static android.car.test.util.UserTestingHelper.setUserLockCredentials;
import static android.car.user.CarUserManager.USER_LIFECYCLE_EVENT_TYPE_CREATED;
import static android.car.user.CarUserManager.USER_LIFECYCLE_EVENT_TYPE_REMOVED;
import static android.car.user.CarUserManager.USER_LIFECYCLE_EVENT_TYPE_STARTING;
import static android.car.user.CarUserManager.USER_LIFECYCLE_EVENT_TYPE_SWITCHING;
import static android.car.user.CarUserManager.USER_LIFECYCLE_EVENT_TYPE_UNLOCKED;
import static android.car.user.CarUserManager.USER_LIFECYCLE_EVENT_TYPE_UNLOCKING;

import static com.google.common.truth.Truth.assertWithMessage;

import android.annotation.UserIdInt;
import android.app.ActivityManager;
import android.app.IActivityManager;
import android.car.Car;
import android.car.test.ApiCheckerRule;
import android.car.test.ApiCheckerRule.SupportedVersionTest;
import android.car.test.ApiCheckerRule.UnsupportedVersionTest;
import android.car.test.ApiCheckerRule.UnsupportedVersionTest.Behavior;
import android.car.testapi.BlockingUserLifecycleListener;
import android.car.user.CarUserManager;
import android.car.user.CarUserManager.UserLifecycleEvent;
import android.car.user.CarUserManager.UserLifecycleListener;
import android.car.user.UserLifecycleEventFilter;
import android.content.pm.UserInfo;
import android.os.Process;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.Log;

import com.android.compatibility.common.util.ApiTest;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public final class CarUserManagerTest extends CarMultiUserTestBase {

    private static final String TAG = CarUserManagerTest.class.getSimpleName();

    private static final int PIN = 2345;

    private static final int START_TIMEOUT_MS = 20_000;
    private static final int SWITCH_TIMEOUT_MS = 70_000;

    private static final int sMaxNumberUsersBefore = UserManager.getMaxSupportedUsers();
    private static boolean sChangedMaxNumberUsers;

    // TODO(b/242350638): move to super class (although it would need to call
    // disableAnnotationsCheck()
    @Rule
    public final ApiCheckerRule mApiCheckerRule = new ApiCheckerRule.Builder().build();

    @BeforeClass
    public static void setupMaxNumberOfUsers() {
        int requiredUsers = 3; // system user, current user, 1 extra user
        if (sMaxNumberUsersBefore < requiredUsers) {
            sChangedMaxNumberUsers = true;
            Log.i(TAG, "Increasing maximing number of users from " + sMaxNumberUsersBefore + " to "
                    + requiredUsers);
            setMaxSupportedUsers(requiredUsers);
        }
    }

    @AfterClass
    public static void restoreMaxNumberOfUsers() {
        if (sChangedMaxNumberUsers) {
            Log.i(TAG, "Restoring maximum number of users to " + sMaxNumberUsersBefore);
            setMaxSupportedUsers(sMaxNumberUsersBefore);
        }
    }

    @Test
    @ApiTest(apis = {"android.car.user.CarUserManager#createUser(String, int)"})
    public void testCreateUser() throws Exception {
        UserInfo newUser = createUser("DaNewUserInTheBlock");
        assertWithMessage("(%s).isGuest()", newUser.toFullString()).that(newUser.isGuest())
                .isFalse();

        assertWithMessage("user(%s).name", newUser.toFullString()).that(newUser.name)
                .contains("DaNewUserInTheBlock");

        // Make sure the user exists
        UserInfo loadedUser = getUser(newUser.id);
        assertUserInfo(newUser, loadedUser);
    }

    @Test
    @ApiTest(apis = {"android.car.user.CarUserManager#createGuest(String)"})
    public void testCreateGuest() throws Exception {
        UserInfo newGuest = createGuest("DaNewGuestInTheBlock");
        assertWithMessage("(%s).isGuest()", newGuest.toFullString()).that(newGuest.isGuest())
                .isTrue();

        assertWithMessage("guest(%s).name ", newGuest.toFullString()).that(newGuest.name)
                .contains("DaNewGuestInTheBlock");

        // Make sure the guest exists
        UserInfo loadedGuest = getUser(newGuest.id);
        assertUserInfo(newGuest, loadedGuest);
    }

    @Test
    @ApiTest(apis = {"android.car.user.CarUserManager#USER_LIFECYCLE_EVENT_TYPE_CREATED"})
    @SupportedVersionTest(unsupportedVersionTest =
            "testLifecycleUserCreatedListener_unsupportedVersion")
    public void testLifecycleUserCreatedListener_supportedVersion() throws Exception {
        Car car = Car.createCar(getContext().getApplicationContext());
        CarUserManager mgr = (CarUserManager) car.getCarManager(Car.CAR_USER_SERVICE);

        BlockingUserLifecycleListener listener = BlockingUserLifecycleListener
                .forSpecificEvents()
                .setTimeout(START_TIMEOUT_MS)
                .addExpectedEvent(USER_LIFECYCLE_EVENT_TYPE_CREATED)
                .build();

        int newUserId = UserHandle.USER_NULL;
        try {
            Log.d(TAG, "registering listener: " + listener);
            mgr.addListener(Runnable::run, listener);
            Log.v(TAG, "ok");

            newUserId = createUser("TestUserToCreate").id;

            Log.d(TAG, "Waiting for events");
            List<UserLifecycleEvent> events = listener.waitForEvents();
            Log.d(TAG, "events: " + events);
            assertWithMessage("events").that(events).hasSize(1);
            UserLifecycleEvent event = events.get(0);
            assertWithMessage("type of event %s", event).that(event.getEventType())
                    .isEqualTo(USER_LIFECYCLE_EVENT_TYPE_CREATED);
            assertWithMessage("user id on %s", event).that(event.getUserId())
                    .isEqualTo(newUserId);
        } finally {
            Log.d(TAG, "unregistering listener: " + listener);
            mgr.removeListener(listener);
            Log.v(TAG, "ok");

            if (newUserId != UserHandle.USER_NULL) {
                removeUser(newUserId);
            }
        }
    }

    @Test
    @ApiTest(apis = {"android.car.user.CarUserManager#USER_LIFECYCLE_EVENT_TYPE_CREATED"})
    @UnsupportedVersionTest(behavior = Behavior.EXPECT_PASS,
            supportedVersionTest = "testLifecycleUserCreatedListener_supportedVersion")
    public void testLifecycleUserCreatedListener_unsupportedVersion() throws Exception {
        Car car = Car.createCar(getContext().getApplicationContext());
        CarUserManager mgr = (CarUserManager) car.getCarManager(Car.CAR_USER_SERVICE);

        LifecycleListener listener = new LifecycleListener();

        int newUserId = UserHandle.USER_NULL;
        try {
            mgr.addListener(Runnable::run, listener);
            Log.v(TAG, "ok");

            newUserId = createUser("TestUserToCreate").id;

            Log.d(TAG, "Waiting for events");
            listener.assertEventNotReceived(
                    newUserId, CarUserManager.USER_LIFECYCLE_EVENT_TYPE_CREATED);
        } finally {
            Log.d(TAG, "unregistering listener: " + listener);
            mgr.removeListener(listener);
            Log.v(TAG, "ok");

            if (newUserId != UserHandle.USER_NULL) {
                removeUser(newUserId);
            }
        }
    }

    @Test
    @SupportedVersionTest(unsupportedVersionTest =
            "testLifecycleUserRemovedListener_unsupportedVersion")
    @ApiTest(apis = {"android.car.user.CarUserManager#USER_LIFECYCLE_EVENT_TYPE_REMOVED"})
    public void testLifecycleUserRemovedListener_supportedVersion() throws Exception {
        int newUserId = createUser("TestUserToRemove").id;
        Car car = Car.createCar(getContext().getApplicationContext());
        CarUserManager mgr = (CarUserManager) car.getCarManager(Car.CAR_USER_SERVICE);

        BlockingUserLifecycleListener listener = BlockingUserLifecycleListener
                .forSpecificEvents()
                .forUser(newUserId)
                .setTimeout(START_TIMEOUT_MS)
                .addExpectedEvent(USER_LIFECYCLE_EVENT_TYPE_REMOVED)
                .build();

        try {
            Log.d(TAG, "registering listener: " + listener);
            mgr.addListener(Runnable::run, listener);
            Log.v(TAG, "ok");

            removeUser(newUserId);

            Log.d(TAG, "Waiting for events");
            List<UserLifecycleEvent> events = listener.waitForEvents();
            Log.d(TAG, "events: " + events);
            assertWithMessage("events").that(events).hasSize(1);
            UserLifecycleEvent event = events.get(0);
            assertWithMessage("type of event %s", event).that(event.getEventType())
                    .isEqualTo(USER_LIFECYCLE_EVENT_TYPE_REMOVED);
            assertWithMessage("user id on %s", event).that(event.getUserId())
                    .isEqualTo(newUserId);
        } finally {
            Log.d(TAG, "unregistering listener: " + listener);
            mgr.removeListener(listener);
            Log.v(TAG, "ok");
        }
    }

    @Test
    @UnsupportedVersionTest(behavior = Behavior.EXPECT_PASS, supportedVersionTest =
            "testLifecycleUserRemovedListener_supportedVersion")
    @ApiTest(apis = {"android.car.user.CarUserManager#USER_LIFECYCLE_EVENT_TYPE_REMOVED"})
    public void testLifecycleUserRemovedListener_unsupportedVersion() throws Exception {
        int newUserId = createUser("TestUserToRemove").id;
        Car car = Car.createCar(getContext().getApplicationContext());
        CarUserManager mgr = (CarUserManager) car.getCarManager(Car.CAR_USER_SERVICE);

        LifecycleListener listener = new LifecycleListener();

        try {
            Log.d(TAG, "registering listener: " + listener);
            mgr.addListener(Runnable::run, listener);
            Log.v(TAG, "ok");

            removeUser(newUserId);

            Log.d(TAG, "Waiting for events");
            listener.assertEventNotReceived(
                    newUserId, CarUserManager.USER_LIFECYCLE_EVENT_TYPE_CREATED);
        } finally {
            Log.d(TAG, "unregistering listener: " + listener);
            mgr.removeListener(listener);
            Log.v(TAG, "ok");
        }
    }

    @Test
    @ApiTest(apis = {
            "android.car.user.CarUserManager#addListener(Executor,UserLifecycleListener)",
            "android.car.user.CarUserManager#removeListener(UserLifecycleListener)"
    })
    public void testLifecycleMultipleListeners() throws Exception {
        int newUserId = createUser("Test").id;
        Car car2 = Car.createCar(getContext().getApplicationContext());
        CarUserManager mgr2 = (CarUserManager) car2.getCarManager(Car.CAR_USER_SERVICE);
        CarUserManager mgr1 = mCarUserManager;
        Log.d(TAG, "myUid=" + Process.myUid() + ",mgr1=" + mgr1 + ", mgr2=" + mgr2);
        assertWithMessage("mgrs").that(mgr1).isNotSameInstanceAs(mgr2);

        BlockingUserLifecycleListener listener1 = BlockingUserLifecycleListener
                .forSpecificEvents()
                .forUser(newUserId)
                .setTimeout(START_TIMEOUT_MS)
                .addExpectedEvent(USER_LIFECYCLE_EVENT_TYPE_STARTING)
                .build();
        BlockingUserLifecycleListener listener2 = BlockingUserLifecycleListener
                .forSpecificEvents()
                .forUser(newUserId)
                .setTimeout(START_TIMEOUT_MS)
                .addExpectedEvent(USER_LIFECYCLE_EVENT_TYPE_STARTING)
                .build();
        BlockingUserLifecycleListener listener3 = BlockingUserLifecycleListener
                .forSpecificEvents()
                .forUser(newUserId)
                .setTimeout(START_TIMEOUT_MS)
                .addExpectedEvent(USER_LIFECYCLE_EVENT_TYPE_STARTING)
                .build();
        UserLifecycleEventFilter unlockingEventFilter = new UserLifecycleEventFilter.Builder()
                .addEventType(USER_LIFECYCLE_EVENT_TYPE_UNLOCKING).build();
        UserLifecycleEventFilter userFilter = new UserLifecycleEventFilter.Builder()
                .addUser(UserHandle.of(newUserId)).build();

        Log.d(TAG, "registering listener1: " + listener1);
        mgr1.addListener(Runnable::run, listener1);
        Log.v(TAG, "ok");
        try {
            Log.d(TAG, "registering listener2: " + listener2 + " with filter: "
                    + unlockingEventFilter);
            mgr2.addListener(Runnable::run, unlockingEventFilter, listener2);
            Log.v(TAG, "ok");
            Log.d(TAG, "registering listener3: " + listener3 + " with filter: " + userFilter);
            mgr2.addListener(Runnable::run, userFilter, listener3);
            Log.v(TAG, "ok");
            try {
                IActivityManager am = ActivityManager.getService();
                Log.d(TAG, "Starting user " + newUserId);
                am.startUserInBackground(newUserId);
                Log.v(TAG, "ok");

                Log.d(TAG, "Waiting for events");
                List<UserLifecycleEvent> events1 = listener1.waitForEvents();
                Log.d(TAG, "events1: " + events1);
                List<UserLifecycleEvent> events3 = listener3.waitForEvents();
                Log.d(TAG, "events2: " + events3);
                assertStartUserEvent(events1, newUserId);
                assertStartUserEvent(events3, newUserId);
                assertWithMessage("all events received by listener %s", listener2)
                        .that(listener2.getAllReceivedEvents()).isEmpty();
            } finally {
                Log.d(TAG, "unregistering listener2: " + listener2);
                mgr2.removeListener(listener2);
                Log.v(TAG, "ok");
                Log.d(TAG, "unregistering listener3: " + listener3);
                mgr2.removeListener(listener3);
                Log.v(TAG, "ok");
            }
        } finally {
            Log.d(TAG, "unregistering listener1: " + listener1);
            mgr1.removeListener(listener1);
            Log.v(TAG, "ok");
        }
    }

    private void assertStartUserEvent(List<UserLifecycleEvent> events, @UserIdInt int userId) {
        assertWithMessage("events").that(events).hasSize(1);

        UserLifecycleEvent event = events.get(0);
        assertWithMessage("type").that(event.getEventType())
                .isEqualTo(USER_LIFECYCLE_EVENT_TYPE_STARTING);
        assertWithMessage("user id on %s", event).that(event.getUserId()).isEqualTo(userId);
        assertWithMessage("user handle on %s", event).that(event.getUserHandle().getIdentifier())
                .isEqualTo(userId);
        assertWithMessage("previous user id on %s", event).that(event.getPreviousUserId())
                .isEqualTo(UserHandle.USER_NULL);
        assertWithMessage("previous user handle on %s", event).that(event.getPreviousUserHandle())
                .isNull();
    }

    /**
     * Tests resume behavior when current user is ephemeral guest, a new guest user should be
     * created and switched to.
     */
    @Test
    @ApiTest(apis = {
            "android.car.user.CarUserManager#USER_LIFECYCLE_EVENT_TYPE_UNLOCKED",
            "android.car.user.CarUserManager#USER_LIFECYCLE_EVENT_TYPE_SWITCHING",
    })
    public void testGuestUserResumeToNewGuestUser() throws Exception {
        // TODO(b/241837415): Create a host-side test and move this test there.
        if (!isDeviceEmulator()) return;

        // Create new guest user
        UserInfo guestUser = createGuest();
        int guestUserId = guestUser.id;

        // Wait for this user to be active
        BlockingUserLifecycleListener listener1 = BlockingUserLifecycleListener
                .forSpecificEvents()
                .forUser(guestUserId)
                .setTimeout(SWITCH_TIMEOUT_MS)
                .addExpectedEvent(USER_LIFECYCLE_EVENT_TYPE_UNLOCKED)
                .build();
        mCarUserManager.addListener(Runnable::run, listener1);
        try {
            switchUser(guestUserId);
            listener1.waitForEvents();
        } finally {
            mCarUserManager.removeListener(listener1);
        }

        BlockingUserLifecycleListener listener2 = BlockingUserLifecycleListener
                .forSpecificEvents()
                .forPreviousUser(guestUserId)
                .setTimeout(SWITCH_TIMEOUT_MS)
                .addExpectedEvent(USER_LIFECYCLE_EVENT_TYPE_SWITCHING)
                .build();
        // Make sure listener callback was executed in the proper thread
        mCarUserManager.addListener(Runnable::run, listener2);

        try {
            // Emulate suspend to RAM
            suspendToRamAndResume();
            UserLifecycleEvent event = listener2.waitForEvents().get(0);

            int newGuestId = event.getUserId();

            assertWithMessage("userId on event %s", event).that(newGuestId)
                    .isNotEqualTo(guestUserId);
            assertWithMessage("current user id").that(newGuestId).isEqualTo(getCurrentUserId());
            UserInfo newGuest = mUserManager.getUserInfo(newGuestId);
            assertWithMessage("new user (%s) is a guest", newGuest.toFullString())
                    .that(newGuest.isGuest()).isTrue();
            assertWithMessage("name of new guest(%s)", newGuest.toFullString())
                    .that(newGuest.name).isNotEqualTo(guestUser.name);
        } finally {
            mCarUserManager.removeListener(listener2);
        }
    }

    /**
     * Tests resume behavior when current user is guest but with secured lock screen,
     * resume to same guest user.
     */
    @Test
    @ApiTest(apis = {
            "android.car.user.CarUserManager#USER_LIFECYCLE_EVENT_TYPE_UNLOCKED",
    })
    public void testSecuredGuestUserResumeToSameUser() throws Exception {
        // TODO(b/241837415): Create a host-side test and move this test there.
        if (!isDeviceEmulator()) return;

        // Create new guest user
        UserInfo guestUser = createGuest();
        int guestUserId = guestUser.id;

        // Wait for this user to be active
        BlockingUserLifecycleListener listener = BlockingUserLifecycleListener
                .forSpecificEvents()
                .forUser(guestUserId)
                .setTimeout(SWITCH_TIMEOUT_MS)
                .addExpectedEvent(USER_LIFECYCLE_EVENT_TYPE_UNLOCKED)
                .build();
        mCarUserManager.addListener(Runnable::run, listener);

        try {
            switchUser(guestUserId);

            listener.waitForEvents();
        } finally {
            mCarUserManager.removeListener(listener);
        }

        setUserLockCredentials(guestUserId, PIN);
        try {
            // Emulate suspend to RAM
            suspendToRamAndResume();

            assertWithMessage("current user remains guest user (%s)", guestUser)
                    .that(getCurrentUserId()).isEqualTo(guestUserId);
        } finally {
            clearUserLockCredentials(guestUserId, PIN);
        }
    }

    /**
     * Tests resume behavior when current user is persistent user.
     */
    @Test
    @ApiTest(apis = {
            "android.car.user.CarUserManager#USER_LIFECYCLE_EVENT_TYPE_UNLOCKED",
    })
    public void testPersistentUserResumeToUser() throws Exception {
        // TODO(b/241837415): Create a host-side test and move this test there.
        if (!isDeviceEmulator()) return;

        int newUserId = createUser().id;
        BlockingUserLifecycleListener listener = BlockingUserLifecycleListener
                .forSpecificEvents()
                .forUser(newUserId)
                .setTimeout(SWITCH_TIMEOUT_MS)
                .addExpectedEvent(USER_LIFECYCLE_EVENT_TYPE_UNLOCKED)
                .build();
        mCarUserManager.addListener(Runnable::run, listener);
        try {
            switchUser(newUserId);
            listener.waitForEvents();

            // Emulate suspend to RAM
            suspendToRamAndResume();

            listener.waitForEvents();
            assertWithMessage("current user remains new user (%s)", newUserId)
                    .that(ActivityManager.getCurrentUser()).isEqualTo(newUserId);
        } finally {
            mCarUserManager.removeListener(listener);
        }
    }

    private static boolean isDeviceEmulator() {
        return SystemProperties.get("ro.product.system.device").equals("generic");
    }

    // TODO(b/244594590): Clean this listener up once BlockingUserLifecycleListener supports
    // no events received.
    private final class LifecycleListener implements UserLifecycleListener {
        private static final int TIMEOUT_MS = 60_000;
        private static final int WAIT_TIME_MS = 1_000;

        private final List<UserLifecycleEvent> mEvents =
                new ArrayList<CarUserManager.UserLifecycleEvent>();

        private final Object mLock = new Object();

        @Override
        public void onEvent(UserLifecycleEvent event) {
            Log.d(TAG, "Event received: " + event);
            synchronized (mLock) {
                mEvents.add(event);
            }
        }

        public void assertEventNotReceived(int userId, int eventType)
                throws InterruptedException {
            long startTime = SystemClock.elapsedRealtime();
            while (SystemClock.elapsedRealtime() - startTime < TIMEOUT_MS) {
                boolean result = checkEvent(userId, eventType);
                if (result) {
                    fail("Event" + eventType
                            + " was not expected but was received within timeoutMs: " + TIMEOUT_MS);
                }
                Thread.sleep(WAIT_TIME_MS);
            }
        }

        private boolean checkEvent(int userId, int eventType) {
            synchronized (mLock) {
                for (int i = 0; i < mEvents.size(); i++) {
                    if (mEvents.get(i).getUserHandle().getIdentifier() == userId
                            && mEvents.get(i).getEventType() == eventType) {
                        return true;
                    }
                }
            }
            return false;
        }
    }
}
