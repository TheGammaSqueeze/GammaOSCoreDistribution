/*
 * Copyright (C) 2022 The Android Open Source Project
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

import static android.car.test.util.UserTestingHelper.setMaxSupportedUsers;
import static android.car.user.CarUserManager.USER_LIFECYCLE_EVENT_TYPE_STARTING;
import static android.car.user.CarUserManager.USER_LIFECYCLE_EVENT_TYPE_STOPPED;
import static android.car.user.CarUserManager.USER_LIFECYCLE_EVENT_TYPE_STOPPING;
import static android.car.user.CarUserManager.USER_LIFECYCLE_EVENT_TYPE_SWITCHING;
import static android.car.user.CarUserManager.USER_LIFECYCLE_EVENT_TYPE_UNLOCKED;
import static android.car.user.CarUserManager.USER_LIFECYCLE_EVENT_TYPE_UNLOCKING;

import static com.google.common.truth.Truth.assertThat;

import android.annotation.Nullable;
import android.car.testapi.BlockingUserLifecycleListener;
import android.car.user.CarUserManager.UserLifecycleEvent;
import android.car.user.UserLifecycleEventFilter;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.Log;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

// DO NOT ADD ANY TEST TO THIS CLASS
// This class will have only one test testUserLifecycleEventFilter.
public final class CarUserManagerLifecycleEventFilterTest extends CarMultiUserTestBase {

    private static final String TAG = CarUserManagerLifecycleEventFilterTest.class.getSimpleName();

    private static final int EVENTS_TIMEOUT_MS = 70_000;

    private static final int sMaxNumberUsersBefore = UserManager.getMaxSupportedUsers();
    private static boolean sChangedMaxNumberUsers;

    private Listener[] mListeners = {
            // listeners[0]: any events for any user. Expects to receive 3 events.
            new Listener(BlockingUserLifecycleListener.forSpecificEvents()
                    .addExpectedEvent(USER_LIFECYCLE_EVENT_TYPE_STARTING)
                    .addExpectedEvent(USER_LIFECYCLE_EVENT_TYPE_SWITCHING)
                    .addExpectedEvent(USER_LIFECYCLE_EVENT_TYPE_UNLOCKING)
                    .addExpectedEvent(USER_LIFECYCLE_EVENT_TYPE_UNLOCKED)
                    .addExpectedEvent(USER_LIFECYCLE_EVENT_TYPE_STOPPING)
                    .addExpectedEvent(USER_LIFECYCLE_EVENT_TYPE_STOPPED)
                    .build(),
                    new UserLifecycleEventFilter.Builder().addUser(UserHandle.CURRENT).build()),
            // listeners[1]: any events for current user. Expects to receive 3 events.
            new Listener(BlockingUserLifecycleListener.forSpecificEvents()
                    .addExpectedEvent(USER_LIFECYCLE_EVENT_TYPE_STARTING)
                    .addExpectedEvent(USER_LIFECYCLE_EVENT_TYPE_SWITCHING)
                    .addExpectedEvent(USER_LIFECYCLE_EVENT_TYPE_UNLOCKING)
                    .addExpectedEvent(USER_LIFECYCLE_EVENT_TYPE_UNLOCKED)
                    .addExpectedEvent(USER_LIFECYCLE_EVENT_TYPE_STOPPING)
                    .addExpectedEvent(USER_LIFECYCLE_EVENT_TYPE_STOPPED)
                    .build(),
                    new UserLifecycleEventFilter.Builder().addUser(UserHandle.CURRENT).build()),
            // listener[2]: starting events for any user. Expects to receive 1 events.
            new Listener(BlockingUserLifecycleListener.forSpecificEvents()
                    .addExpectedEvent(USER_LIFECYCLE_EVENT_TYPE_STARTING)
                    .build(),
                    new UserLifecycleEventFilter.Builder()
                            .addEventType(USER_LIFECYCLE_EVENT_TYPE_STARTING)
                            .build()),
            // listener[3]: stopping/stopped events for any user. Expects to receive 0 events.
            new Listener(BlockingUserLifecycleListener.forSpecificEvents()
                    .addExpectedEvent(USER_LIFECYCLE_EVENT_TYPE_STOPPING)
                    .addExpectedEvent(USER_LIFECYCLE_EVENT_TYPE_STOPPED)
                    .build(),
                    new UserLifecycleEventFilter.Builder()
                            .addEventType(USER_LIFECYCLE_EVENT_TYPE_STOPPING)
                            .addEventType(USER_LIFECYCLE_EVENT_TYPE_STOPPED)
                            .build()),
            // listener[4]: switching events for any user. Expects to receive 2 events.
            new Listener(BlockingUserLifecycleListener.forSpecificEvents()
                    .addExpectedEvent(USER_LIFECYCLE_EVENT_TYPE_SWITCHING)
                    .build(),
                    new UserLifecycleEventFilter.Builder()
                            .addEventType(USER_LIFECYCLE_EVENT_TYPE_SWITCHING)
                            .build())};

    @BeforeClass
    public static void setupMaxNumberOfUsers() {
        int requiredUsers = 3; // system user, current user, 1 extra user
        if (sMaxNumberUsersBefore < requiredUsers) {
            sChangedMaxNumberUsers = true;
            Log.i(TAG, "Increasing maximum number of users from " + sMaxNumberUsersBefore + " to "
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

    @Test(timeout = 100_000)
    public void testUserLifecycleEventFilter() throws Exception {
        int initialUserId = getCurrentUserId();
        int newUserId = createUser().id;

        for (Listener listener : mListeners) {
            Log.d(TAG, "registering listener:" + listener.listener + "%s, with filter:"
                    + listener.filter);
            if (listener.filter == null) {
                mCarUserManager.addListener(listener.executor, listener.listener);
            } else {
                mCarUserManager.addListener(listener.executor, listener.filter, listener.listener);
            }
        }

        // Switch while listener is registered
        switchUser(newUserId);

        // Switch back to the initial user
        switchUser(initialUserId);

        // Wait for all listeners to receive all expected events.
        waitUntil("Listeners have not received all expected events", EVENTS_TIMEOUT_MS,
                () -> (mListeners[0].listener.getAllReceivedEvents().size() == 3
                        && mListeners[1].listener.getAllReceivedEvents().size() == 3
                        && mListeners[4].listener.getAllReceivedEvents().size() == 2));

        // unregister listeners.
        for (Listener listener : mListeners) {
            Log.d(TAG, "unregistering listener:" + listener.listener + ", which received"
                    + " events: " + listener.listener.getAllReceivedEvents());
            mCarUserManager.removeListener(listener.listener);
        }

        // The expected events are (in order): STARTING, SWITCHING, SWITCHING
        UserLifecycleEvent[] events = buildExpectedEvents(initialUserId, newUserId);

        assertThat(mListeners[0].listener.getAllReceivedEvents()).containsExactlyElementsIn(events)
                .inOrder();
        assertThat(mListeners[1].listener.getAllReceivedEvents()).containsExactlyElementsIn(events)
                .inOrder();
        assertThat(mListeners[2].listener.getAllReceivedEvents()).containsExactly(events[0]);
        assertThat(mListeners[3].listener.getAllReceivedEvents()).isEmpty();
        assertThat(mListeners[4].listener.getAllReceivedEvents())
                .containsExactly(events[1], events[2]).inOrder();
    }

    private UserLifecycleEvent[] buildExpectedEvents(int initialUserId, int newUserId) {
        return new UserLifecycleEvent[] {
                new UserLifecycleEvent(USER_LIFECYCLE_EVENT_TYPE_STARTING, newUserId),
                new UserLifecycleEvent(USER_LIFECYCLE_EVENT_TYPE_SWITCHING,
                        /* from= */initialUserId, /* to= */newUserId),
                new UserLifecycleEvent(USER_LIFECYCLE_EVENT_TYPE_SWITCHING,
                        /* from= */newUserId, /* to= */initialUserId)};
    }

    private static class Listener {
        public AtomicInteger executeCount;
        public BlockingUserLifecycleListener listener;
        public UserLifecycleEventFilter filter;
        public Executor executor = r -> {
            executeCount.getAndIncrement();
            r.run();
        };

        Listener(BlockingUserLifecycleListener listener,
                @Nullable UserLifecycleEventFilter filter) {
            executeCount = new AtomicInteger(0);
            this.listener = listener;
            this.filter = filter;
        }
    }
}
