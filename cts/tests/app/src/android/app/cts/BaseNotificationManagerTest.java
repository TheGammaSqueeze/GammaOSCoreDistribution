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

package android.app.cts;

import static android.app.Notification.CATEGORY_CALL;
import static android.app.NotificationManager.IMPORTANCE_DEFAULT;
import static android.app.NotificationManager.INTERRUPTION_FILTER_ALL;

import static org.junit.Assert.assertNotEquals;

import android.app.ActivityManager;
import android.app.Instrumentation;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationChannelGroup;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Person;
import android.app.UiAutomation;
import android.app.cts.android.app.cts.tools.NotificationHelper;
import android.app.role.RoleManager;
import android.app.stubs.BubbledActivity;
import android.app.stubs.R;
import android.app.stubs.TestNotificationAssistant;
import android.app.stubs.TestNotificationListener;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.drawable.Icon;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.provider.Telephony;
import android.service.notification.StatusBarNotification;
import android.test.AndroidTestCase;
import android.util.ArraySet;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/* Base class for NotificationManager tests. Handles some of the common set up logic for tests. */
public abstract class BaseNotificationManagerTest extends AndroidTestCase {

    protected static final String NOTIFICATION_CHANNEL_ID = "NotificationManagerTest";
    protected static final String SHARE_SHORTCUT_CATEGORY =
            "android.app.stubs.SHARE_SHORTCUT_CATEGORY";
    protected static final String SHARE_SHORTCUT_ID = "shareShortcut";

    private static final String TAG = BaseNotificationManagerTest.class.getSimpleName();

    protected PackageManager mPackageManager;
    protected AudioManager mAudioManager;
    protected RoleManager mRoleManager;
    protected NotificationManager mNotificationManager;
    protected ActivityManager mActivityManager;
    protected TestNotificationAssistant mAssistant;
    protected TestNotificationListener mListener;
    protected List<String> mRuleIds;
    protected Instrumentation mInstrumentation;
    protected NotificationHelper mNotificationHelper;

    public static void toggleListenerAccess(Context context, boolean on) throws IOException {
        String command = " cmd notification " + (on ? "allow_listener " : "disallow_listener ")
                + TestNotificationListener.getId();

        runCommand(command, InstrumentationRegistry.getInstrumentation());

        final NotificationManager nm = context.getSystemService(NotificationManager.class);
        final ComponentName listenerComponent = TestNotificationListener.getComponentName();
        assertEquals(listenerComponent + " has incorrect listener access",
                on, nm.isNotificationListenerAccessGranted(listenerComponent));
    }

    @SuppressWarnings("StatementWithEmptyBody")
    protected static void runCommand(String command, Instrumentation instrumentation)
            throws IOException {
        UiAutomation uiAutomation = instrumentation.getUiAutomation();
        // Execute command
        try (ParcelFileDescriptor fd = uiAutomation.executeShellCommand(command)) {
            assertNotNull("Failed to execute shell command: " + command, fd);
            // Wait for the command to finish by reading until EOF
            try (InputStream in = new FileInputStream(fd.getFileDescriptor())) {
                byte[] buffer = new byte[4096];
                while (in.read(buffer) > 0) {
                    // discard output
                }
            } catch (IOException e) {
                throw new IOException("Could not read stdout of command: " + command, e);
            }
        }
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mNotificationManager = mContext.getSystemService(NotificationManager.class);
        mNotificationHelper = new NotificationHelper(mContext, () -> mListener);
        // clear the deck so that our getActiveNotifications results are predictable
        mNotificationManager.cancelAll();

        assertEquals("Previous test left system in a bad state",
                0, mNotificationManager.getActiveNotifications().length);

        mNotificationManager.createNotificationChannel(new NotificationChannel(
                NOTIFICATION_CHANNEL_ID, "name", IMPORTANCE_DEFAULT));
        mActivityManager = mContext.getSystemService(ActivityManager.class);
        mPackageManager = mContext.getPackageManager();
        mAudioManager = mContext.getSystemService(AudioManager.class);
        mRoleManager = mContext.getSystemService(RoleManager.class);
        mRuleIds = new ArrayList<>();

        // ensure listener access isn't allowed before test runs (other tests could put
        // TestListener in an unexpected state)
        toggleListenerAccess(false);
        toggleAssistantAccess(false);
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        toggleNotificationPolicyAccess(mContext.getPackageName(), mInstrumentation, true);
        mNotificationManager.setInterruptionFilter(INTERRUPTION_FILTER_ALL);
        toggleNotificationPolicyAccess(mContext.getPackageName(), mInstrumentation, false);

        // Ensure that the tests are exempt from global service-related rate limits
        setEnableServiceNotificationRateLimit(false);
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();

        setEnableServiceNotificationRateLimit(true);

        mNotificationManager.cancelAll();
        for (String id : mRuleIds) {
            mNotificationManager.removeAutomaticZenRule(id);
        }

        assertExpectedDndState(INTERRUPTION_FILTER_ALL);

        List<NotificationChannel> channels = mNotificationManager.getNotificationChannels();
        // Delete all channels.
        for (NotificationChannel nc : channels) {
            if (NotificationChannel.DEFAULT_CHANNEL_ID.equals(nc.getId())) {
                continue;
            }
            mNotificationManager.deleteNotificationChannel(nc.getId());
        }

        // Unsuspend package if it was suspended in the test
        suspendPackage(mContext.getPackageName(), mInstrumentation, false);

        toggleListenerAccess(false);
        toggleNotificationPolicyAccess(mContext.getPackageName(), mInstrumentation, false);

        List<NotificationChannelGroup> groups = mNotificationManager.getNotificationChannelGroups();
        // Delete all groups.
        for (NotificationChannelGroup ncg : groups) {
            mNotificationManager.deleteNotificationChannelGroup(ncg.getId());
        }
    }

    protected StatusBarNotification findPostedNotification(int id, boolean all) {
        return mNotificationHelper.findPostedNotification(id, all);
    }

    protected void setUpNotifListener() {
        try {
            toggleListenerAccess(true);
            mListener = TestNotificationListener.getInstance();
            assertNotNull(mListener);
            mListener.resetData();
        } catch (IOException e) {
        }
    }

    protected boolean checkNotificationExistence(int id, boolean shouldExist) {
        // notification is a bit asynchronous so it may take a few ms to appear in
        // getActiveNotifications()
        // we will check for it for up to 300ms before giving up
        boolean found = false;
        for (int tries = 3; tries-- > 0; ) {
            // Need reset flag.
            found = false;
            final StatusBarNotification[] sbns = mNotificationManager.getActiveNotifications();
            for (StatusBarNotification sbn : sbns) {
                Log.d(TAG, "Found " + sbn.getKey());
                if (sbn.getId() == id) {
                    found = true;
                    break;
                }
            }
            if (found == shouldExist) break;
            try {
                Thread.sleep(100);
            } catch (InterruptedException ex) {
                // pass
            }
        }
        return found == shouldExist;
    }

    protected void toggleListenerAccess(boolean on) throws IOException {
        toggleListenerAccess(mContext, on);
    }

    protected void toggleAssistantAccess(boolean on) throws IOException {
        final ComponentName assistantComponent = TestNotificationAssistant.getComponentName();

        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .adoptShellPermissionIdentity("android.permission.STATUS_BAR_SERVICE",
                    "android.permission.REQUEST_NOTIFICATION_ASSISTANT_SERVICE");
        mNotificationManager.setNotificationAssistantAccessGranted(assistantComponent, on);

        assertTrue(assistantComponent + " has not been " + (on ? "allowed" : "disallowed"),
                mNotificationManager.isNotificationAssistantAccessGranted(assistantComponent)
                        == on);
        if (on) {
            assertEquals(assistantComponent,
                    mNotificationManager.getAllowedNotificationAssistant());
        } else {
            assertNotEquals(assistantComponent,
                    mNotificationManager.getAllowedNotificationAssistant());
        }

        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation().dropShellPermissionIdentity();
    }

    protected void assertExpectedDndState(int expectedState) {
        int tries = 3;
        for (int i = tries; i >= 0; i--) {
            if (expectedState
                    == mNotificationManager.getCurrentInterruptionFilter()) {
                break;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        assertEquals(expectedState, mNotificationManager.getCurrentInterruptionFilter());
    }

    /** Creates a dynamic, longlived, sharing shortcut. Call {@link #deleteShortcuts()} after. */
    protected void createDynamicShortcut() {
        Person person = new Person.Builder()
                .setBot(false)
                .setIcon(Icon.createWithResource(mContext, R.drawable.icon_black))
                .setName("BubbleBot")
                .setImportant(true)
                .build();

        Set<String> categorySet = new ArraySet<>();
        categorySet.add(SHARE_SHORTCUT_CATEGORY);
        Intent shortcutIntent = new Intent(mContext, BubbledActivity.class);
        shortcutIntent.setAction(Intent.ACTION_VIEW);

        ShortcutInfo shortcut = new ShortcutInfo.Builder(mContext, SHARE_SHORTCUT_ID)
                .setShortLabel(SHARE_SHORTCUT_ID)
                .setIcon(Icon.createWithResource(mContext, R.drawable.icon_black))
                .setIntent(shortcutIntent)
                .setPerson(person)
                .setCategories(categorySet)
                .setLongLived(true)
                .build();

        ShortcutManager scManager = mContext.getSystemService(ShortcutManager.class);
        scManager.addDynamicShortcuts(Arrays.asList(shortcut));
    }

    protected void deleteShortcuts() {
        ShortcutManager scManager = mContext.getSystemService(ShortcutManager.class);
        scManager.removeAllDynamicShortcuts();
        scManager.removeLongLivedShortcuts(Collections.singletonList(SHARE_SHORTCUT_ID));
    }

    /**
     * Notification fulfilling conversation policy; for the shortcut to be valid
     * call {@link #createDynamicShortcut()}
     */
    protected Notification.Builder getConversationNotification() {
        Person person = new Person.Builder()
                .setName("bubblebot")
                .build();
        return new Notification.Builder(mContext, NOTIFICATION_CHANNEL_ID)
                .setContentTitle("foo")
                .setShortcutId(SHARE_SHORTCUT_ID)
                .setStyle(new Notification.MessagingStyle(person)
                        .setConversationTitle("Bubble Chat")
                        .addMessage("Hello?",
                                SystemClock.currentThreadTimeMillis() - 300000, person)
                        .addMessage("Is it me you're looking for?",
                                SystemClock.currentThreadTimeMillis(), person)
                )
                .setSmallIcon(android.R.drawable.sym_def_app_icon);
    }

    protected void sendNotification(final int id,
            final int icon) throws Exception {
        sendNotification(id, null, icon);
    }

    protected void sendNotification(final int id,
            String groupKey, final int icon) {
        sendNotification(id, groupKey, icon, false, null);
    }

    protected void sendNotification(final int id,
            String groupKey, final int icon,
            boolean isCall, Uri phoneNumber) {
        final Intent intent = new Intent(Intent.ACTION_MAIN, Telephony.Threads.CONTENT_URI);

        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP
                | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.setAction(Intent.ACTION_MAIN);

        final PendingIntent pendingIntent = PendingIntent.getActivity(mContext, 0, intent,
                PendingIntent.FLAG_MUTABLE);
        Notification.Builder nb = new Notification.Builder(mContext, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(icon)
                .setWhen(System.currentTimeMillis())
                .setContentTitle("notify#" + id)
                .setContentText("This is #" + id + "notification  ")
                .setContentIntent(pendingIntent)
                .setGroup(groupKey);

        if (isCall) {
            nb.setCategory(CATEGORY_CALL);
            if (phoneNumber != null) {
                Bundle extras = new Bundle();
                ArrayList<Person> pList = new ArrayList<>();
                pList.add(new Person.Builder().setUri(phoneNumber.toString()).build());
                extras.putParcelableArrayList(Notification.EXTRA_PEOPLE_LIST, pList);
                nb.setExtras(extras);
            }
        }

        final Notification notification = nb.build();
        mNotificationManager.notify(id, notification);

        if (!checkNotificationExistence(id, /*shouldExist=*/ true)) {
            fail("couldn't find posted notification id=" + id);
        }
    }

    protected void setEnableServiceNotificationRateLimit(boolean enable) throws IOException {
        String command = "cmd activity fgs-notification-rate-limit "
                + (enable ? "enable" : "disable");

        runCommand(command, InstrumentationRegistry.getInstrumentation());
    }

    protected void suspendPackage(String packageName,
            Instrumentation instrumentation, boolean suspend) throws IOException {
        int userId = mContext.getUserId();
        String command = " cmd package " + (suspend ? "suspend " : "unsuspend ")
                + "--user " + userId + " " + packageName;

        runCommand(command, instrumentation);
    }

    protected void toggleNotificationPolicyAccess(String packageName,
            Instrumentation instrumentation, boolean on) throws IOException {

        String command = " cmd notification " + (on ? "allow_dnd " : "disallow_dnd ") + packageName;

        runCommand(command, instrumentation);

        NotificationManager nm = mContext.getSystemService(NotificationManager.class);
        assertEquals("Notification Policy Access Grant is "
                + nm.isNotificationPolicyAccessGranted() + " not " + on + " for "
                + packageName, on, nm.isNotificationPolicyAccessGranted());
    }
}
