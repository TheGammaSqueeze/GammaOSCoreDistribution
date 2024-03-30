/*
 * Copyright (C) 2008 The Android Open Source Project
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

import static android.Manifest.permission.POST_NOTIFICATIONS;
import static android.Manifest.permission.REVOKE_POST_NOTIFICATIONS_WITHOUT_KILL;
import static android.Manifest.permission.REVOKE_RUNTIME_PERMISSIONS;
import static android.app.NotificationManager.IMPORTANCE_DEFAULT;
import static android.app.NotificationManager.IMPORTANCE_HIGH;
import static android.app.NotificationManager.IMPORTANCE_LOW;
import static android.app.NotificationManager.IMPORTANCE_MIN;
import static android.app.NotificationManager.IMPORTANCE_NONE;
import static android.app.NotificationManager.IMPORTANCE_UNSPECIFIED;
import static android.app.NotificationManager.INTERRUPTION_FILTER_ALARMS;
import static android.app.NotificationManager.INTERRUPTION_FILTER_ALL;
import static android.app.NotificationManager.INTERRUPTION_FILTER_NONE;
import static android.app.NotificationManager.INTERRUPTION_FILTER_PRIORITY;
import static android.app.NotificationManager.Policy.CONVERSATION_SENDERS_ANYONE;
import static android.app.NotificationManager.Policy.CONVERSATION_SENDERS_NONE;
import static android.app.NotificationManager.Policy.PRIORITY_CATEGORY_ALARMS;
import static android.app.NotificationManager.Policy.PRIORITY_CATEGORY_CALLS;
import static android.app.NotificationManager.Policy.PRIORITY_CATEGORY_CONVERSATIONS;
import static android.app.NotificationManager.Policy.PRIORITY_CATEGORY_EVENTS;
import static android.app.NotificationManager.Policy.PRIORITY_CATEGORY_MEDIA;
import static android.app.NotificationManager.Policy.PRIORITY_CATEGORY_MESSAGES;
import static android.app.NotificationManager.Policy.PRIORITY_CATEGORY_REMINDERS;
import static android.app.NotificationManager.Policy.PRIORITY_CATEGORY_REPEAT_CALLERS;
import static android.app.NotificationManager.Policy.PRIORITY_CATEGORY_SYSTEM;
import static android.app.NotificationManager.Policy.SUPPRESSED_EFFECT_AMBIENT;
import static android.app.NotificationManager.Policy.SUPPRESSED_EFFECT_FULL_SCREEN_INTENT;
import static android.app.NotificationManager.Policy.SUPPRESSED_EFFECT_LIGHTS;
import static android.app.NotificationManager.Policy.SUPPRESSED_EFFECT_NOTIFICATION_LIST;
import static android.app.NotificationManager.Policy.SUPPRESSED_EFFECT_PEEK;
import static android.app.NotificationManager.Policy.SUPPRESSED_EFFECT_SCREEN_OFF;
import static android.app.NotificationManager.Policy.SUPPRESSED_EFFECT_SCREEN_ON;
import static android.app.NotificationManager.Policy.SUPPRESSED_EFFECT_STATUS_BAR;
import static android.app.cts.android.app.cts.tools.NotificationHelper.MAX_WAIT_TIME;
import static android.app.cts.android.app.cts.tools.NotificationHelper.SHORT_WAIT_TIME;
import static android.content.pm.PackageManager.FEATURE_WATCH;

import static org.hamcrest.CoreMatchers.hasItem;
import static org.junit.Assert.assertThat;

import android.Manifest;
import android.app.AutomaticZenRule;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationChannelGroup;
import android.app.NotificationManager;
import android.app.NotificationManager.Policy;
import android.app.PendingIntent;
import android.app.cts.android.app.cts.tools.FutureServiceConnection;
import android.app.role.RoleManager;
import android.app.stubs.AutomaticZenRuleActivity;
import android.app.stubs.GetResultActivity;
import android.app.stubs.R;
import android.app.stubs.TestNotificationAssistant;
import android.app.stubs.TestNotificationListener;
import android.content.ComponentName;
import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.OperationApplicationException;
import android.content.ServiceConnection;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.drawable.Icon;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.session.MediaSession;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.os.UserHandle;
import android.permission.PermissionManager;
import android.permission.cts.PermissionUtils;
import android.platform.test.annotations.AsbSecurityTest;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.provider.ContactsContract.Data;
import android.provider.Settings;
import android.service.notification.Condition;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.service.notification.ZenPolicy;
import android.support.test.uiautomator.UiDevice;
import android.util.ArrayMap;
import android.util.Log;
import android.widget.RemoteViews;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.PollingCheck;
import com.android.compatibility.common.util.SystemUtil;
import com.android.compatibility.common.util.ThrowingSupplier;
import com.android.test.notificationlistener.INotificationUriAccessService;

import com.google.common.base.Preconditions;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/* This tests NotificationListenerService together with NotificationManager, as you need to have
 * notifications to manipulate in order to test the listener service. */
public class NotificationManagerTest extends BaseNotificationManagerTest {
    public static final String NOTIFICATIONPROVIDER = "com.android.test.notificationprovider";
    public static final String RICH_NOTIFICATION_ACTIVITY =
            "com.android.test.notificationprovider.RichNotificationActivity";
    final String TAG = NotificationManagerTest.class.getSimpleName();
    final boolean DEBUG = false;

    private static final String TEST_APP = "com.android.test.notificationapp";
    private static final String DELEGATE_POST_CLASS = TEST_APP + ".NotificationDelegateAndPost";
    private static final String REVOKE_CLASS = TEST_APP + ".NotificationRevoker";
    private static final String MATCHES_CALL_FILTER_CLASS =
            TEST_APP + ".MatchesCallFilterTestActivity";
    private static final String MINIMAL_LISTENER_CLASS = TEST_APP + ".TestNotificationListener";

    private static final String TRAMPOLINE_APP =
            "com.android.test.notificationtrampoline.current";
    private static final String TRAMPOLINE_APP_API_30 =
            "com.android.test.notificationtrampoline.api30";
    private static final String TRAMPOLINE_APP_API_32 =
            "com.android.test.notificationtrampoline.api32";
    private static final ComponentName TRAMPOLINE_SERVICE =
            new ComponentName(TRAMPOLINE_APP,
                    "com.android.test.notificationtrampoline.NotificationTrampolineTestService");
    private static final ComponentName TRAMPOLINE_SERVICE_API_30 =
            new ComponentName(TRAMPOLINE_APP_API_30,
                    "com.android.test.notificationtrampoline.NotificationTrampolineTestService");
    private static final ComponentName TRAMPOLINE_SERVICE_API_32 =
            new ComponentName(TRAMPOLINE_APP_API_32,
                    "com.android.test.notificationtrampoline.NotificationTrampolineTestService");

    private static final String STUB_PACKAGE_NAME = "android.app.stubs";

    private static final long TIMEOUT_LONG_MS = 10000;
    private static final long TIMEOUT_MS = 4000;
    private static final int MESSAGE_BROADCAST_NOTIFICATION = 1;
    private static final int MESSAGE_SERVICE_NOTIFICATION = 2;
    private static final int MESSAGE_CLICK_NOTIFICATION = 3;

    // Constants for creating contacts
    private static final String ALICE = "Alice";
    private static final String ALICE_PHONE = "+16175551212";
    private static final String ALICE_EMAIL = "alice@_foo._bar";
    private static final String BOB = "Bob";
    private static final String BOB_PHONE = "+16175553434";
    private static final String BOB_EMAIL = "bob@_foo._bar";

    // Constants for GetResultActivity and return codes from MatchesCallFilterTestActivity
    // the permitted/not permitted values need to stay the same as in the test activity.
    private static final int REQUEST_CODE = 42;
    private static final int MATCHES_CALL_FILTER_NOT_PERMITTED = 0;
    private static final int MATCHES_CALL_FILTER_PERMITTED = 1;

    private String mId;
    private INotificationUriAccessService mNotificationUriAccessService;
    private FutureServiceConnection mTrampolineConnection;

    @Nullable
    private List<String> mPreviousDefaultBrowser;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        PermissionUtils.grantPermission(mContext.getPackageName(), POST_NOTIFICATIONS);
        PermissionUtils.grantPermission(STUB_PACKAGE_NAME, POST_NOTIFICATIONS);
        PermissionUtils.grantPermission(TEST_APP, POST_NOTIFICATIONS);
        PermissionUtils.grantPermission(TRAMPOLINE_APP, POST_NOTIFICATIONS);
        PermissionUtils.grantPermission(TRAMPOLINE_APP_API_30, POST_NOTIFICATIONS);
        PermissionUtils.grantPermission(TRAMPOLINE_APP_API_32, POST_NOTIFICATIONS);
        PermissionUtils.grantPermission(NOTIFICATIONPROVIDER, POST_NOTIFICATIONS);
        // This will leave a set of channels on the device with each test run.
        mId = UUID.randomUUID().toString();

        // delay between tests so notifications aren't dropped by the rate limiter
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
        }
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();

        // For trampoline tests
        if (mTrampolineConnection != null) {
            mContext.unbindService(mTrampolineConnection);
            mTrampolineConnection = null;
        }
        if (mListener != null) {
            mListener.removeTestPackage(TRAMPOLINE_APP_API_30);
            mListener.removeTestPackage(TRAMPOLINE_APP);
        }
        if (mPreviousDefaultBrowser != null) {
            restoreDefaultBrowser();
        }

        // Use test API to prevent PermissionManager from killing the test process when revoking
        // permission.
        SystemUtil.runWithShellPermissionIdentity(
                () -> mContext.getSystemService(PermissionManager.class)
                        .revokePostNotificationPermissionWithoutKillForTest(
                                mContext.getPackageName(),
                                android.os.Process.myUserHandle().getIdentifier()),
                REVOKE_POST_NOTIFICATIONS_WITHOUT_KILL,
                REVOKE_RUNTIME_PERMISSIONS);
        PermissionUtils.revokePermission(STUB_PACKAGE_NAME, POST_NOTIFICATIONS);
        PermissionUtils.revokePermission(TEST_APP, POST_NOTIFICATIONS);
        PermissionUtils.revokePermission(TRAMPOLINE_APP, POST_NOTIFICATIONS);
        PermissionUtils.revokePermission(NOTIFICATIONPROVIDER, POST_NOTIFICATIONS);
    }

    private void assertNotificationCancelled(int id, boolean all) {
        for (long totalWait = 0; totalWait < MAX_WAIT_TIME; totalWait += SHORT_WAIT_TIME) {
            StatusBarNotification sbn = findNotificationNoWait(id, all);
            if (sbn == null) return;
            try {
                Thread.sleep(SHORT_WAIT_TIME);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        assertNull(findNotificationNoWait(id, all));
    }

    private void insertSingleContact(String name, String phone, String email, boolean starred) {
        final ArrayList<ContentProviderOperation> operationList =
                new ArrayList<ContentProviderOperation>();
        ContentProviderOperation.Builder builder =
                ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI);
        builder.withValue(ContactsContract.RawContacts.STARRED, starred ? 1 : 0);
        operationList.add(builder.build());

        builder = ContentProviderOperation.newInsert(Data.CONTENT_URI);
        builder.withValueBackReference(StructuredName.RAW_CONTACT_ID, 0);
        builder.withValue(Data.MIMETYPE, StructuredName.CONTENT_ITEM_TYPE);
        builder.withValue(StructuredName.DISPLAY_NAME, name);
        operationList.add(builder.build());

        if (phone != null) {
            builder = ContentProviderOperation.newInsert(Data.CONTENT_URI);
            builder.withValueBackReference(Phone.RAW_CONTACT_ID, 0);
            builder.withValue(Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE);
            builder.withValue(Phone.TYPE, Phone.TYPE_MOBILE);
            builder.withValue(Phone.NUMBER, phone);
            builder.withValue(Phone.NORMALIZED_NUMBER, phone);
            builder.withValue(Data.IS_PRIMARY, 1);
            operationList.add(builder.build());
        }
        if (email != null) {
            builder = ContentProviderOperation.newInsert(Data.CONTENT_URI);
            builder.withValueBackReference(Email.RAW_CONTACT_ID, 0);
            builder.withValue(Data.MIMETYPE, Email.CONTENT_ITEM_TYPE);
            builder.withValue(Email.TYPE, Email.TYPE_HOME);
            builder.withValue(Email.DATA, email);
            operationList.add(builder.build());
        }

        try {
            mContext.getContentResolver().applyBatch(ContactsContract.AUTHORITY, operationList);
        } catch (RemoteException e) {
            Log.e(TAG, String.format("%s: %s", e.toString(), e.getMessage()));
        } catch (OperationApplicationException e) {
            Log.e(TAG, String.format("%s: %s", e.toString(), e.getMessage()));
        }
    }

    private void deleteSingleContact(Uri uri) {
        final ArrayList<ContentProviderOperation> operationList =
                new ArrayList<ContentProviderOperation>();
        operationList.add(ContentProviderOperation.newDelete(uri).build());
        try {
            mContext.getContentResolver().applyBatch(ContactsContract.AUTHORITY, operationList);
        } catch (RemoteException e) {
            Log.e(TAG, String.format("%s: %s", e.toString(), e.getMessage()));
        } catch (OperationApplicationException e) {
            Log.e(TAG, String.format("%s: %s", e.toString(), e.getMessage()));
        }
    }

    private Uri lookupContact(String phone) {
        Cursor c = null;
        try {
            Uri phoneUri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                    Uri.encode(phone));
            String[] projection = new String[]{ContactsContract.Contacts._ID,
                    ContactsContract.Contacts.LOOKUP_KEY};
            c = mContext.getContentResolver().query(phoneUri, projection, null, null, null);
            if (c != null && c.getCount() > 0) {
                c.moveToFirst();
                int lookupIdx = c.getColumnIndex(ContactsContract.Contacts.LOOKUP_KEY);
                int idIdx = c.getColumnIndex(ContactsContract.Contacts._ID);
                String lookupKey = c.getString(lookupIdx);
                long contactId = c.getLong(idIdx);
                return ContactsContract.Contacts.getLookupUri(contactId, lookupKey);
            }
        } catch (Throwable t) {
            Log.w(TAG, "Problem getting content resolver or performing contacts query.", t);
        } finally {
            if (c != null) {
                c.close();
            }
        }
        return null;
    }

    // Simple helper function to take a phone number's string representation and make a tel: uri
    private Uri makePhoneUri(String phone) {
        return new Uri.Builder()
                .scheme("tel")
                .encodedOpaquePart(phone)  // don't re-encode anything passed in
                .build();
    }

    private StatusBarNotification findNotificationNoWait(int id, boolean all) {
        return mNotificationHelper.findNotificationNoWait(id, all);
    }

    private StatusBarNotification[] getActiveNotifications(boolean all) {
        return mNotificationHelper.getActiveNotifications(all);
    }

    private PendingIntent getPendingIntent() {
        return PendingIntent.getActivity(
                getContext(), 0, new Intent(getContext(), this.getClass()),
                PendingIntent.FLAG_MUTABLE_UNAUDITED);
    }

    private boolean isGroupSummary(Notification n) {
        return n.getGroup() != null && (n.flags & Notification.FLAG_GROUP_SUMMARY) != 0;
    }

    private void assertOnlySomeNotificationsAutogrouped(List<Integer> autoGroupedIds) {
        String expectedGroupKey = null;
        try {
            // Posting can take ~100 ms
            Thread.sleep(150);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        StatusBarNotification[] sbns = mNotificationManager.getActiveNotifications();
        for (StatusBarNotification sbn : sbns) {
            if (isGroupSummary(sbn.getNotification())
                    || autoGroupedIds.contains(sbn.getId())) {
                assertTrue(sbn.getKey() + " is unexpectedly not autogrouped",
                        sbn.getOverrideGroupKey() != null);
                if (expectedGroupKey == null) {
                    expectedGroupKey = sbn.getGroupKey();
                }
                assertEquals(expectedGroupKey, sbn.getGroupKey());
            } else {
                assertTrue(sbn.isGroup());
                assertTrue(sbn.getKey() + " is unexpectedly autogrouped,",
                        sbn.getOverrideGroupKey() == null);
                assertTrue(sbn.getKey() + " has an unusual group key",
                        sbn.getGroupKey() != expectedGroupKey);
            }
        }
    }

    private void assertAllPostedNotificationsAutogrouped() {
        String expectedGroupKey = null;
        try {
            // Posting can take ~100 ms
            Thread.sleep(150);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        StatusBarNotification[] sbns = mNotificationManager.getActiveNotifications();
        for (StatusBarNotification sbn : sbns) {
            // all notis should be in a group determined by autogrouping
            assertTrue(sbn.getOverrideGroupKey() != null);
            if (expectedGroupKey == null) {
                expectedGroupKey = sbn.getGroupKey();
            }
            // all notis should be in the same group
            assertEquals(expectedGroupKey, sbn.getGroupKey());
        }
    }

    private void cancelAndPoll(int id) {
        mNotificationManager.cancel(id);

        try {
            Thread.sleep(500);
        } catch (InterruptedException ex) {
            // pass
        }
        if (!checkNotificationExistence(id, /*shouldExist=*/ false)) {
            fail("canceled notification was still alive, id=" + id);
        }
    }

    private int getCancellationReason(String key) {
        for (int tries = 3; tries-- > 0; ) {
            if (mListener.mRemoved.containsKey(key)) {
                return mListener.mRemoved.get(key);
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ex) {
                // pass
            }
        }
        return -1;
    }

    private int getAssistantCancellationReason(String key) {
        for (int tries = 3; tries-- > 0; ) {
            if (mAssistant.mRemoved.containsKey(key)) {
                return mAssistant.mRemoved.get(key);
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ex) {
                // pass
            }
        }
        return -1;
    }

    private void assertNotificationCount(int expectedCount) {
        // notification is a bit asynchronous so it may take a few ms to appear in
        // getActiveNotifications()
        // we will check for it for up to 400ms before giving up
        int lastCount = 0;
        for (int tries = 4; tries-- > 0; ) {
            final StatusBarNotification[] sbns = mNotificationManager.getActiveNotifications();
            lastCount = sbns.length;
            if (expectedCount == lastCount) return;
            try {
                Thread.sleep(100);
            } catch (InterruptedException ex) {
                // pass
            }
        }
        fail("Expected " + expectedCount + " posted notifications, were " + lastCount);
    }

    private void compareChannels(NotificationChannel expected, NotificationChannel actual) {
        if (actual == null) {
            fail("actual channel is null");
            return;
        }
        if (expected == null) {
            fail("expected channel is null");
            return;
        }
        assertEquals(expected.getId(), actual.getId());
        assertEquals(expected.getName(), actual.getName());
        assertEquals(expected.getDescription(), actual.getDescription());
        assertEquals(expected.shouldVibrate(), actual.shouldVibrate());
        assertEquals(expected.shouldShowLights(), actual.shouldShowLights());
        assertEquals(expected.getLightColor(), actual.getLightColor());
        assertEquals(expected.getImportance(), actual.getImportance());
        if (expected.getSound() == null) {
            assertEquals(Settings.System.DEFAULT_NOTIFICATION_URI, actual.getSound());
            assertEquals(Notification.AUDIO_ATTRIBUTES_DEFAULT, actual.getAudioAttributes());
        } else {
            assertEquals(expected.getSound(), actual.getSound());
            assertEquals(expected.getAudioAttributes(), actual.getAudioAttributes());
        }
        assertTrue(Arrays.equals(expected.getVibrationPattern(), actual.getVibrationPattern()));
        assertEquals(expected.getGroup(), actual.getGroup());
        assertEquals(expected.getConversationId(), actual.getConversationId());
        assertEquals(expected.getParentChannelId(), actual.getParentChannelId());
        assertEquals(expected.isDemoted(), actual.isDemoted());
    }

    private void toggleExternalListenerAccess(ComponentName listenerComponent, boolean on)
            throws IOException {
        String command = " cmd notification " + (on ? "allow_listener " : "disallow_listener ")
                + listenerComponent.flattenToString();
        runCommand(command, InstrumentationRegistry.getInstrumentation());
    }

    private boolean hasReadContactsPermission(String pkgName) {
        return mPackageManager.checkPermission(
                Manifest.permission.READ_CONTACTS, pkgName)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void toggleReadContactsPermission(String pkgName, boolean on) {
        SystemUtil.runWithShellPermissionIdentity(() -> {
            if (on) {
                mInstrumentation.getUiAutomation().grantRuntimePermission(pkgName,
                        "android.permission.READ_CONTACTS");
            } else {
                mInstrumentation.getUiAutomation().revokeRuntimePermission(pkgName,
                        "android.permission.READ_CONTACTS");
            }
        });
    }

    private boolean areRulesSame(AutomaticZenRule a, AutomaticZenRule b) {
        return a.isEnabled() == b.isEnabled()
                && Objects.equals(a.getName(), b.getName())
                && a.getInterruptionFilter() == b.getInterruptionFilter()
                && Objects.equals(a.getConditionId(), b.getConditionId())
                && Objects.equals(a.getOwner(), b.getOwner())
                && Objects.equals(a.getZenPolicy(), b.getZenPolicy())
                && Objects.equals(a.getConfigurationActivity(), b.getConfigurationActivity());
    }

    private AutomaticZenRule createRule(String name, int filter) {
        return new AutomaticZenRule(name, null,
                new ComponentName(mContext, AutomaticZenRuleActivity.class),
                new Uri.Builder().scheme("scheme")
                        .appendPath("path")
                        .appendQueryParameter("fake_rule", "fake_value")
                        .build(), null, filter, true);
    }

    private AutomaticZenRule createRule(String name) {
        return createRule(name, INTERRUPTION_FILTER_PRIORITY);
    }

    // Creates a GetResultActivity into which one can call startActivityForResult with
    // in order to test the outcome of an activity that returns a result code.
    private GetResultActivity setUpGetResultActivity() {
        final Intent intent = new Intent(mContext, GetResultActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        GetResultActivity activity = (GetResultActivity) mInstrumentation.startActivitySync(intent);
        mInstrumentation.waitForIdleSync();
        activity.clearResult();
        return activity;
    }

    private void sendTrampolineMessage(ComponentName component, int message,
            int notificationId, Handler callback) throws Exception {
        if (mTrampolineConnection == null) {
            Intent intent = new Intent();
            intent.setComponent(component);
            mTrampolineConnection = new FutureServiceConnection();
            assertTrue(
                    mContext.bindService(intent, mTrampolineConnection, Context.BIND_AUTO_CREATE));
        }
        Messenger service = new Messenger(mTrampolineConnection.get(TIMEOUT_MS));
        service.send(Message.obtain(null, message, notificationId, -1, new Messenger(callback)));
    }

    private void setDefaultBrowser(String packageName) throws Exception {
        UserHandle user = android.os.Process.myUserHandle();
        mPreviousDefaultBrowser = SystemUtil.callWithShellPermissionIdentity(
                () -> mRoleManager.getRoleHoldersAsUser(RoleManager.ROLE_BROWSER, user));
        CompletableFuture<Boolean> set = new CompletableFuture<>();
        SystemUtil.runWithShellPermissionIdentity(
                () -> mRoleManager.addRoleHolderAsUser(RoleManager.ROLE_BROWSER, packageName, 0,
                        user, mContext.getMainExecutor(), set::complete));
        assertTrue("Failed to set " + packageName + " as default browser",
                set.get(TIMEOUT_MS, TimeUnit.MILLISECONDS));
    }

    private void restoreDefaultBrowser() throws Exception {
        Preconditions.checkState(mPreviousDefaultBrowser != null);
        UserHandle user = android.os.Process.myUserHandle();
        Executor executor = mContext.getMainExecutor();
        CompletableFuture<Boolean> restored = new CompletableFuture<>();
        SystemUtil.runWithShellPermissionIdentity(() -> {
            mRoleManager.clearRoleHoldersAsUser(RoleManager.ROLE_BROWSER, 0, user, executor,
                    restored::complete);
            for (String packageName : mPreviousDefaultBrowser) {
                mRoleManager.addRoleHolderAsUser(RoleManager.ROLE_BROWSER, packageName,
                        0, user, executor, restored::complete);
            }
        });
        assertTrue("Failed to restore default browser",
                restored.get(TIMEOUT_MS, TimeUnit.MILLISECONDS));
    }

    /**
     * Previous tests could have started activities within the grace period, so go home to avoid
     * allowing background activity starts due to this exemption.
     */
    private void deactivateGracePeriod() {
        UiDevice.getInstance(mInstrumentation).pressHome();
    }

    public void testConsolidatedNotificationPolicy() throws Exception {
        final int originalFilter = mNotificationManager.getCurrentInterruptionFilter();
        Policy origPolicy = mNotificationManager.getNotificationPolicy();
        try {
            toggleNotificationPolicyAccess(mContext.getPackageName(),
                    InstrumentationRegistry.getInstrumentation(), true);

            mNotificationManager.setNotificationPolicy(new NotificationManager.Policy(
                    PRIORITY_CATEGORY_ALARMS | PRIORITY_CATEGORY_MEDIA,
                    0, 0));
            // turn on manual DND
            mNotificationManager.setInterruptionFilter(INTERRUPTION_FILTER_PRIORITY);
            assertExpectedDndState(INTERRUPTION_FILTER_PRIORITY);

            // no custom ZenPolicy, so consolidatedPolicy should equal the default notif policy
            assertEquals(mNotificationManager.getConsolidatedNotificationPolicy(),
                    mNotificationManager.getNotificationPolicy());

            // turn off manual DND
            mNotificationManager.setInterruptionFilter(INTERRUPTION_FILTER_ALL);
            assertExpectedDndState(INTERRUPTION_FILTER_ALL);

            // setup custom ZenPolicy for an automatic rule
            AutomaticZenRule rule = createRule("test_consolidated_policy",
                    INTERRUPTION_FILTER_PRIORITY);
            rule.setZenPolicy(new ZenPolicy.Builder()
                    .allowReminders(true)
                    .build());
            String id = mNotificationManager.addAutomaticZenRule(rule);
            mRuleIds.add(id);
            // set condition of the automatic rule to TRUE
            Condition condition = new Condition(rule.getConditionId(), "summary",
                    Condition.STATE_TRUE);
            mNotificationManager.setAutomaticZenRuleState(id, condition);
            assertExpectedDndState(INTERRUPTION_FILTER_PRIORITY);

            NotificationManager.Policy consolidatedPolicy =
                    mNotificationManager.getConsolidatedNotificationPolicy();

            // alarms and media are allowed from default notification policy
            assertTrue((consolidatedPolicy.priorityCategories & PRIORITY_CATEGORY_ALARMS) != 0);
            assertTrue((consolidatedPolicy.priorityCategories & PRIORITY_CATEGORY_MEDIA) != 0);

            // reminders is allowed from the automatic rule's custom ZenPolicy
            assertTrue((consolidatedPolicy.priorityCategories & PRIORITY_CATEGORY_REMINDERS) != 0);

            // other sounds aren't allowed
            assertTrue((consolidatedPolicy.priorityCategories
                    & PRIORITY_CATEGORY_CONVERSATIONS) == 0);
            assertTrue((consolidatedPolicy.priorityCategories & PRIORITY_CATEGORY_CALLS) == 0);
            assertTrue((consolidatedPolicy.priorityCategories & PRIORITY_CATEGORY_MESSAGES) == 0);
            assertTrue((consolidatedPolicy.priorityCategories & PRIORITY_CATEGORY_SYSTEM) == 0);
            assertTrue((consolidatedPolicy.priorityCategories & PRIORITY_CATEGORY_EVENTS) == 0);
        } finally {
            mNotificationManager.setInterruptionFilter(originalFilter);
            mNotificationManager.setNotificationPolicy(origPolicy);
        }
    }

    public void testConsolidatedNotificationPolicyMultiRules() throws Exception {
        final int originalFilter = mNotificationManager.getCurrentInterruptionFilter();
        Policy origPolicy = mNotificationManager.getNotificationPolicy();
        try {
            toggleNotificationPolicyAccess(mContext.getPackageName(),
                    InstrumentationRegistry.getInstrumentation(), true);

            // default allows no sounds
            mNotificationManager.setNotificationPolicy(new NotificationManager.Policy(
                    PRIORITY_CATEGORY_ALARMS, 0, 0));

            // setup custom ZenPolicy for two automatic rules
            AutomaticZenRule rule1 = createRule("test_consolidated_policyq",
                    INTERRUPTION_FILTER_PRIORITY);
            rule1.setZenPolicy(new ZenPolicy.Builder()
                    .allowReminders(false)
                    .allowAlarms(false)
                    .allowSystem(true)
                    .build());
            AutomaticZenRule rule2 = createRule("test_consolidated_policy2",
                    INTERRUPTION_FILTER_PRIORITY);
            rule2.setZenPolicy(new ZenPolicy.Builder()
                    .allowReminders(true)
                    .allowMedia(true)
                    .build());
            String id1 = mNotificationManager.addAutomaticZenRule(rule1);
            String id2 = mNotificationManager.addAutomaticZenRule(rule2);
            Condition onCondition1 = new Condition(rule1.getConditionId(), "summary",
                    Condition.STATE_TRUE);
            Condition onCondition2 = new Condition(rule2.getConditionId(), "summary",
                    Condition.STATE_TRUE);
            mNotificationManager.setAutomaticZenRuleState(id1, onCondition1);
            mNotificationManager.setAutomaticZenRuleState(id2, onCondition2);

            Thread.sleep(300); // wait for rules to be applied - it's done asynchronously

            mRuleIds.add(id1);
            mRuleIds.add(id2);
            assertExpectedDndState(INTERRUPTION_FILTER_PRIORITY);

            NotificationManager.Policy consolidatedPolicy =
                    mNotificationManager.getConsolidatedNotificationPolicy();

            // reminders aren't allowed from rule1 overriding rule2
            // (not allowed takes precedence over allowed)
            assertTrue((consolidatedPolicy.priorityCategories & PRIORITY_CATEGORY_REMINDERS) == 0);

            // alarms aren't allowed from rule1
            // (rule's custom zenPolicy overrides default policy)
            assertTrue((consolidatedPolicy.priorityCategories & PRIORITY_CATEGORY_ALARMS) == 0);

            // system is allowed from rule1, media is allowed from rule2
            assertTrue((consolidatedPolicy.priorityCategories & PRIORITY_CATEGORY_SYSTEM) != 0);
            assertTrue((consolidatedPolicy.priorityCategories & PRIORITY_CATEGORY_MEDIA) != 0);

            // other sounds aren't allowed (from default policy)
            assertTrue((consolidatedPolicy.priorityCategories
                    & PRIORITY_CATEGORY_CONVERSATIONS) == 0);
            assertTrue((consolidatedPolicy.priorityCategories & PRIORITY_CATEGORY_CALLS) == 0);
            assertTrue((consolidatedPolicy.priorityCategories & PRIORITY_CATEGORY_MESSAGES) == 0);
            assertTrue((consolidatedPolicy.priorityCategories & PRIORITY_CATEGORY_EVENTS) == 0);
        } finally {
            mNotificationManager.setInterruptionFilter(originalFilter);
            mNotificationManager.setNotificationPolicy(origPolicy);
        }
    }

    public void testPostPCanToggleAlarmsMediaSystemTest() throws Exception {
        toggleNotificationPolicyAccess(mContext.getPackageName(),
                InstrumentationRegistry.getInstrumentation(), true);

        NotificationManager.Policy origPolicy = mNotificationManager.getNotificationPolicy();
        try {
            if (mContext.getApplicationInfo().targetSdkVersion >= Build.VERSION_CODES.P) {
                // Post-P can toggle alarms, media, system
                // toggle on alarms, media, system:
                mNotificationManager.setNotificationPolicy(new NotificationManager.Policy(
                        PRIORITY_CATEGORY_ALARMS
                                | PRIORITY_CATEGORY_MEDIA
                                | PRIORITY_CATEGORY_SYSTEM, 0, 0));
                NotificationManager.Policy policy = mNotificationManager.getNotificationPolicy();
                assertTrue((policy.priorityCategories & PRIORITY_CATEGORY_ALARMS) != 0);
                assertTrue((policy.priorityCategories & PRIORITY_CATEGORY_MEDIA) != 0);
                assertTrue((policy.priorityCategories & PRIORITY_CATEGORY_SYSTEM) != 0);

                // toggle off alarms, media, system
                mNotificationManager.setNotificationPolicy(new NotificationManager.Policy(0, 0, 0));
                policy = mNotificationManager.getNotificationPolicy();
                assertTrue((policy.priorityCategories & PRIORITY_CATEGORY_ALARMS) == 0);
                assertTrue((policy.priorityCategories & PRIORITY_CATEGORY_MEDIA) == 0);
                assertTrue((policy.priorityCategories & PRIORITY_CATEGORY_SYSTEM) == 0);
            }
        } finally {
            mNotificationManager.setNotificationPolicy(origPolicy);
        }
    }

    public void testPostRCanToggleConversationsTest() throws Exception {
        toggleNotificationPolicyAccess(mContext.getPackageName(),
                InstrumentationRegistry.getInstrumentation(), true);

        NotificationManager.Policy origPolicy = mNotificationManager.getNotificationPolicy();

        try {
            mNotificationManager.setNotificationPolicy(new NotificationManager.Policy(
                    0, 0, 0, 0));
            NotificationManager.Policy policy = mNotificationManager.getNotificationPolicy();
            assertEquals(0, (policy.priorityCategories & PRIORITY_CATEGORY_CONVERSATIONS));
            assertEquals(CONVERSATION_SENDERS_NONE, policy.priorityConversationSenders);

            mNotificationManager.setNotificationPolicy(new NotificationManager.Policy(
                    PRIORITY_CATEGORY_CONVERSATIONS, 0, 0, 0, CONVERSATION_SENDERS_ANYONE));
            policy = mNotificationManager.getNotificationPolicy();
            assertTrue((policy.priorityCategories & PRIORITY_CATEGORY_CONVERSATIONS) != 0);
            assertEquals(CONVERSATION_SENDERS_ANYONE, policy.priorityConversationSenders);

        } finally {
            mNotificationManager.setNotificationPolicy(origPolicy);
        }
    }

    public void testCreateChannelGroup() throws Exception {
        final NotificationChannelGroup ncg = new NotificationChannelGroup("a group", "a label");
        final NotificationChannel channel =
                new NotificationChannel(mId, "name", IMPORTANCE_DEFAULT);
        channel.setGroup(ncg.getId());
        mNotificationManager.createNotificationChannelGroup(ncg);
        final NotificationChannel ungrouped =
                new NotificationChannel(mId + "!", "name", IMPORTANCE_DEFAULT);
        try {
            mNotificationManager.createNotificationChannel(channel);
            mNotificationManager.createNotificationChannel(ungrouped);

            List<NotificationChannelGroup> ncgs =
                    mNotificationManager.getNotificationChannelGroups();
            assertEquals(1, ncgs.size());
            assertEquals(ncg.getName(), ncgs.get(0).getName());
            assertEquals(ncg.getDescription(), ncgs.get(0).getDescription());
            assertEquals(channel.getId(), ncgs.get(0).getChannels().get(0).getId());
        } finally {
            mNotificationManager.deleteNotificationChannelGroup(ncg.getId());
        }
    }

    public void testGetChannelGroup() throws Exception {
        final NotificationChannelGroup ncg = new NotificationChannelGroup("a group", "a label");
        ncg.setDescription("bananas");
        final NotificationChannelGroup ncg2 = new NotificationChannelGroup("group 2", "label 2");
        final NotificationChannel channel =
                new NotificationChannel(mId, "name", IMPORTANCE_DEFAULT);
        channel.setGroup(ncg.getId());

        mNotificationManager.createNotificationChannelGroup(ncg);
        mNotificationManager.createNotificationChannelGroup(ncg2);
        mNotificationManager.createNotificationChannel(channel);

        NotificationChannelGroup actual =
                mNotificationManager.getNotificationChannelGroup(ncg.getId());
        assertEquals(ncg.getId(), actual.getId());
        assertEquals(ncg.getName(), actual.getName());
        assertEquals(ncg.getDescription(), actual.getDescription());
        assertEquals(channel.getId(), actual.getChannels().get(0).getId());
    }

    public void testGetChannelGroups() throws Exception {
        final NotificationChannelGroup ncg = new NotificationChannelGroup("a group", "a label");
        ncg.setDescription("bananas");
        final NotificationChannelGroup ncg2 = new NotificationChannelGroup("group 2", "label 2");
        final NotificationChannel channel =
                new NotificationChannel(mId, "name", IMPORTANCE_DEFAULT);
        channel.setGroup(ncg2.getId());

        mNotificationManager.createNotificationChannelGroup(ncg);
        mNotificationManager.createNotificationChannelGroup(ncg2);
        mNotificationManager.createNotificationChannel(channel);

        List<NotificationChannelGroup> actual =
                mNotificationManager.getNotificationChannelGroups();
        assertEquals(2, actual.size());
        for (NotificationChannelGroup group : actual) {
            if (group.getId().equals(ncg.getId())) {
                assertEquals(group.getName(), ncg.getName());
                assertEquals(group.getDescription(), ncg.getDescription());
                assertEquals(0, group.getChannels().size());
            } else if (group.getId().equals(ncg2.getId())) {
                assertEquals(group.getName(), ncg2.getName());
                assertEquals(group.getDescription(), ncg2.getDescription());
                assertEquals(1, group.getChannels().size());
                assertEquals(channel.getId(), group.getChannels().get(0).getId());
            } else {
                fail("Extra group found " + group.getId());
            }
        }
    }

    public void testDeleteChannelGroup() throws Exception {
        final NotificationChannelGroup ncg = new NotificationChannelGroup("a group", "a label");
        final NotificationChannel channel =
                new NotificationChannel(mId, "name", IMPORTANCE_DEFAULT);
        channel.setGroup(ncg.getId());
        mNotificationManager.createNotificationChannelGroup(ncg);
        mNotificationManager.createNotificationChannel(channel);

        mNotificationManager.deleteNotificationChannelGroup(ncg.getId());

        assertNull(mNotificationManager.getNotificationChannel(channel.getId()));
        assertEquals(0, mNotificationManager.getNotificationChannelGroups().size());
    }

    public void testCreateChannel() throws Exception {
        final NotificationChannel channel =
                new NotificationChannel(mId, "name", IMPORTANCE_DEFAULT);
        channel.setDescription("bananas");
        channel.enableVibration(true);
        channel.setVibrationPattern(new long[]{5, 8, 2, 1});
        channel.setSound(new Uri.Builder().scheme("test").build(),
                new AudioAttributes.Builder().setUsage(
                        AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_DELAYED).build());
        channel.enableLights(true);
        channel.setBypassDnd(true);
        channel.setLockscreenVisibility(Notification.VISIBILITY_SECRET);
        mNotificationManager.createNotificationChannel(channel);
        final NotificationChannel createdChannel =
                mNotificationManager.getNotificationChannel(mId);
        compareChannels(channel, createdChannel);
        // Lockscreen Visibility and canBypassDnd no longer settable.
        assertTrue(createdChannel.getLockscreenVisibility() != Notification.VISIBILITY_SECRET);
        assertFalse(createdChannel.canBypassDnd());
    }

    public void testCreateChannel_rename() throws Exception {
        NotificationChannel channel =
                new NotificationChannel(mId, "name", IMPORTANCE_DEFAULT);
        mNotificationManager.createNotificationChannel(channel);
        channel.setName("new name");
        mNotificationManager.createNotificationChannel(channel);
        final NotificationChannel createdChannel =
                mNotificationManager.getNotificationChannel(mId);
        compareChannels(channel, createdChannel);

        channel.setImportance(NotificationManager.IMPORTANCE_HIGH);
        mNotificationManager.createNotificationChannel(channel);
        assertEquals(NotificationManager.IMPORTANCE_DEFAULT,
                mNotificationManager.getNotificationChannel(mId).getImportance());
    }

    public void testCreateChannel_addToGroup() throws Exception {
        String oldGroup = null;
        String newGroup = "new group";
        mNotificationManager.createNotificationChannelGroup(
                new NotificationChannelGroup(newGroup, newGroup));

        NotificationChannel channel =
                new NotificationChannel(mId, "name", IMPORTANCE_DEFAULT);
        channel.setGroup(oldGroup);
        mNotificationManager.createNotificationChannel(channel);

        channel.setGroup(newGroup);
        mNotificationManager.createNotificationChannel(channel);

        final NotificationChannel updatedChannel =
                mNotificationManager.getNotificationChannel(mId);
        assertEquals("Failed to add non-grouped channel to a group on update ",
                newGroup, updatedChannel.getGroup());
    }

    public void testCreateChannel_cannotChangeGroup() throws Exception {
        String oldGroup = "old group";
        String newGroup = "new group";
        mNotificationManager.createNotificationChannelGroup(
                new NotificationChannelGroup(oldGroup, oldGroup));
        mNotificationManager.createNotificationChannelGroup(
                new NotificationChannelGroup(newGroup, newGroup));

        NotificationChannel channel =
                new NotificationChannel(mId, "name", IMPORTANCE_DEFAULT);
        channel.setGroup(oldGroup);
        mNotificationManager.createNotificationChannel(channel);
        channel.setGroup(newGroup);
        mNotificationManager.createNotificationChannel(channel);
        final NotificationChannel updatedChannel =
                mNotificationManager.getNotificationChannel(mId);
        assertEquals("Channels should not be allowed to change groups",
                oldGroup, updatedChannel.getGroup());
    }

    public void testCreateSameChannelDoesNotUpdate() throws Exception {
        final NotificationChannel channel =
                new NotificationChannel(mId, "name", IMPORTANCE_DEFAULT);
        mNotificationManager.createNotificationChannel(channel);
        final NotificationChannel channelDupe =
                new NotificationChannel(mId, "name", IMPORTANCE_HIGH);
        mNotificationManager.createNotificationChannel(channelDupe);
        final NotificationChannel createdChannel =
                mNotificationManager.getNotificationChannel(mId);
        compareChannels(channel, createdChannel);
    }

    public void testCreateChannelAlreadyExistsNoOp() throws Exception {
        NotificationChannel channel =
                new NotificationChannel(mId, "name", IMPORTANCE_DEFAULT);
        mNotificationManager.createNotificationChannel(channel);
        NotificationChannel channelDupe =
                new NotificationChannel(mId, "name", IMPORTANCE_HIGH);
        mNotificationManager.createNotificationChannel(channelDupe);
        compareChannels(channel, mNotificationManager.getNotificationChannel(channel.getId()));
    }

    public void testCreateChannelWithGroup() throws Exception {
        NotificationChannelGroup ncg = new NotificationChannelGroup("g", "n");
        mNotificationManager.createNotificationChannelGroup(ncg);
        try {
            NotificationChannel channel =
                    new NotificationChannel(mId, "name", IMPORTANCE_DEFAULT);
            channel.setGroup(ncg.getId());
            mNotificationManager.createNotificationChannel(channel);
            compareChannels(channel, mNotificationManager.getNotificationChannel(channel.getId()));
        } finally {
            mNotificationManager.deleteNotificationChannelGroup(ncg.getId());
        }
    }

    public void testCreateChannelWithBadGroup() throws Exception {
        NotificationChannel channel =
                new NotificationChannel(mId, "name", IMPORTANCE_DEFAULT);
        channel.setGroup("garbage");
        try {
            mNotificationManager.createNotificationChannel(channel);
            fail("Created notification with bad group");
        } catch (IllegalArgumentException e) {
        }
    }

    public void testCreateChannelInvalidImportance() throws Exception {
        NotificationChannel channel =
                new NotificationChannel(mId, "name", IMPORTANCE_UNSPECIFIED);
        try {
            mNotificationManager.createNotificationChannel(channel);
        } catch (IllegalArgumentException e) {
            //success
        }
    }

    public void testDeleteChannel() throws Exception {
        NotificationChannel channel =
                new NotificationChannel(mId, "name", IMPORTANCE_LOW);
        mNotificationManager.createNotificationChannel(channel);
        compareChannels(channel, mNotificationManager.getNotificationChannel(channel.getId()));
        mNotificationManager.deleteNotificationChannel(channel.getId());
        assertNull(mNotificationManager.getNotificationChannel(channel.getId()));
    }

    public void testCannotDeleteDefaultChannel() throws Exception {
        try {
            mNotificationManager.deleteNotificationChannel(NotificationChannel.DEFAULT_CHANNEL_ID);
            fail("Deleted default channel");
        } catch (IllegalArgumentException e) {
            //success
        }
    }

    public void testGetChannel() throws Exception {
        NotificationChannel channel1 =
                new NotificationChannel(mId, "name", IMPORTANCE_DEFAULT);
        NotificationChannel channel2 =
                new NotificationChannel(
                        UUID.randomUUID().toString(), "name2", IMPORTANCE_HIGH);
        NotificationChannel channel3 =
                new NotificationChannel(
                        UUID.randomUUID().toString(), "name3", IMPORTANCE_LOW);
        NotificationChannel channel4 =
                new NotificationChannel(
                        UUID.randomUUID().toString(), "name4", IMPORTANCE_MIN);
        mNotificationManager.createNotificationChannel(channel1);
        mNotificationManager.createNotificationChannel(channel2);
        mNotificationManager.createNotificationChannel(channel3);
        mNotificationManager.createNotificationChannel(channel4);

        compareChannels(channel2,
                mNotificationManager.getNotificationChannel(channel2.getId()));
        compareChannels(channel3,
                mNotificationManager.getNotificationChannel(channel3.getId()));
        compareChannels(channel1,
                mNotificationManager.getNotificationChannel(channel1.getId()));
        compareChannels(channel4,
                mNotificationManager.getNotificationChannel(channel4.getId()));
    }

    public void testGetChannels() throws Exception {
        NotificationChannel channel1 =
                new NotificationChannel(mId, "name", IMPORTANCE_DEFAULT);
        NotificationChannel channel2 =
                new NotificationChannel(
                        UUID.randomUUID().toString(), "name2", IMPORTANCE_HIGH);
        NotificationChannel channel3 =
                new NotificationChannel(
                        UUID.randomUUID().toString(), "name3", IMPORTANCE_LOW);
        NotificationChannel channel4 =
                new NotificationChannel(
                        UUID.randomUUID().toString(), "name4", IMPORTANCE_MIN);

        Map<String, NotificationChannel> channelMap = new HashMap<>();
        channelMap.put(channel1.getId(), channel1);
        channelMap.put(channel2.getId(), channel2);
        channelMap.put(channel3.getId(), channel3);
        channelMap.put(channel4.getId(), channel4);
        mNotificationManager.createNotificationChannel(channel1);
        mNotificationManager.createNotificationChannel(channel2);
        mNotificationManager.createNotificationChannel(channel3);
        mNotificationManager.createNotificationChannel(channel4);

        mNotificationManager.deleteNotificationChannel(channel3.getId());

        List<NotificationChannel> channels = mNotificationManager.getNotificationChannels();
        for (NotificationChannel nc : channels) {
            if (NotificationChannel.DEFAULT_CHANNEL_ID.equals(nc.getId())) {
                continue;
            }
            if (NOTIFICATION_CHANNEL_ID.equals(nc.getId())) {
                continue;
            }
            assertFalse(channel3.getId().equals(nc.getId()));
            if (!channelMap.containsKey(nc.getId())) {
                // failed cleanup from prior test run; ignore
                continue;
            }
            compareChannels(channelMap.get(nc.getId()), nc);
        }
    }

    public void testRecreateDeletedChannel() throws Exception {
        NotificationChannel channel =
                new NotificationChannel(mId, "name", IMPORTANCE_DEFAULT);
        channel.setShowBadge(true);
        NotificationChannel newChannel = new NotificationChannel(
                channel.getId(), channel.getName(), IMPORTANCE_HIGH);
        mNotificationManager.createNotificationChannel(channel);
        mNotificationManager.deleteNotificationChannel(channel.getId());

        mNotificationManager.createNotificationChannel(newChannel);

        compareChannels(channel,
                mNotificationManager.getNotificationChannel(newChannel.getId()));
    }

    public void testNotify() throws Exception {
        mNotificationManager.cancelAll();

        final int id = 1;
        sendNotification(id, R.drawable.black);
        // test updating the same notification
        sendNotification(id, R.drawable.blue);
        sendNotification(id, R.drawable.yellow);

        // assume that sendNotification tested to make sure individual notifications were present
        StatusBarNotification[] sbns = mNotificationManager.getActiveNotifications();
        for (StatusBarNotification sbn : sbns) {
            if (sbn.getId() != id) {
                fail("we got back other notifications besides the one we posted: "
                        + sbn.getKey());
            }
        }
    }

    public void testSuspendPackage_withoutShellPermission() throws Exception {
        if (mActivityManager.isLowRamDevice() && !mPackageManager.hasSystemFeature(FEATURE_WATCH)) {
            return;
        }

        try {
            Process proc = Runtime.getRuntime().exec("cmd notification suspend_package "
                    + mContext.getPackageName());

            // read output of command
            BufferedReader reader =
                    new BufferedReader(new InputStreamReader(proc.getInputStream()));
            StringBuilder output = new StringBuilder();
            String line = reader.readLine();
            while (line != null) {
                output.append(line);
                line = reader.readLine();
            }
            reader.close();
            final String outputString = output.toString();

            proc.waitFor();

            // check that the output string had an error / disallowed call since it didn't have
            // shell permission to suspend the package
            assertTrue(outputString, outputString.contains("error"));
            assertTrue(outputString, outputString.contains("permission denied"));
        } catch (InterruptedException e) {
            fail("Unsuccessful shell command");
        }
    }

    public void testSuspendPackage() throws Exception {
        toggleListenerAccess(true);
        Thread.sleep(500); // wait for listener to be allowed

        mListener = TestNotificationListener.getInstance();
        assertNotNull(mListener);

        sendNotification(1, R.drawable.black);
        Thread.sleep(500); // wait for notification listener to receive notification
        assertEquals(1, mListener.mPosted.size());

        // suspend package, ranking should be updated with suspended = true
        suspendPackage(mContext.getPackageName(), InstrumentationRegistry.getInstrumentation(),
                true);
        Thread.sleep(500); // wait for notification listener to get response
        NotificationListenerService.RankingMap rankingMap = mListener.mRankingMap;
        NotificationListenerService.Ranking outRanking = new NotificationListenerService.Ranking();
        for (String key : rankingMap.getOrderedKeys()) {
            if (key.contains(mListener.getPackageName())) {
                rankingMap.getRanking(key, outRanking);
                Log.d(TAG, "key=" + key + " suspended=" + outRanking.isSuspended());
                assertTrue(outRanking.isSuspended());
            }
        }

        // unsuspend package, ranking should be updated with suspended = false
        suspendPackage(mContext.getPackageName(), InstrumentationRegistry.getInstrumentation(),
                false);
        Thread.sleep(500); // wait for notification listener to get response
        rankingMap = mListener.mRankingMap;
        for (String key : rankingMap.getOrderedKeys()) {
            if (key.contains(mListener.getPackageName())) {
                rankingMap.getRanking(key, outRanking);
                Log.d(TAG, "key=" + key + " suspended=" + outRanking.isSuspended());
                assertFalse(outRanking.isSuspended());
            }
        }

        mListener.resetData();
    }

    public void testSuspendedPackageSendsNotification() throws Exception {
        toggleListenerAccess(true);
        Thread.sleep(500); // wait for listener to be allowed

        mListener = TestNotificationListener.getInstance();
        assertNotNull(mListener);

        // suspend package, post notification while package is suspended, see notification
        // in ranking map with suspended = true
        suspendPackage(mContext.getPackageName(), InstrumentationRegistry.getInstrumentation(),
                true);
        sendNotification(1, R.drawable.black);
        Thread.sleep(500); // wait for notification listener to receive notification
        assertEquals(1, mListener.mPosted.size()); // apps targeting P receive notification
        NotificationListenerService.RankingMap rankingMap = mListener.mRankingMap;
        NotificationListenerService.Ranking outRanking = new NotificationListenerService.Ranking();
        for (String key : rankingMap.getOrderedKeys()) {
            if (key.contains(mListener.getPackageName())) {
                rankingMap.getRanking(key, outRanking);
                Log.d(TAG, "key=" + key + " suspended=" + outRanking.isSuspended());
                assertTrue(outRanking.isSuspended());
            }
        }

        // unsuspend package, ranking should be updated with suspended = false
        suspendPackage(mContext.getPackageName(), InstrumentationRegistry.getInstrumentation(),
                false);
        Thread.sleep(500); // wait for notification listener to get response
        assertEquals(1, mListener.mPosted.size()); // should see previously posted notification
        rankingMap = mListener.mRankingMap;
        for (String key : rankingMap.getOrderedKeys()) {
            if (key.contains(mListener.getPackageName())) {
                rankingMap.getRanking(key, outRanking);
                Log.d(TAG, "key=" + key + " suspended=" + outRanking.isSuspended());
                assertFalse(outRanking.isSuspended());
            }
        }

        mListener.resetData();
    }

    public void testShowBadging_ranking() throws Exception {
        final int originalBadging = Settings.Secure.getInt(
                mContext.getContentResolver(), Settings.Secure.NOTIFICATION_BADGING);

        SystemUtil.runWithShellPermissionIdentity(() ->
                Settings.Secure.putInt(mContext.getContentResolver(),
                        Settings.Secure.NOTIFICATION_BADGING, 1));
        assertEquals(1, Settings.Secure.getInt(
                mContext.getContentResolver(), Settings.Secure.NOTIFICATION_BADGING));

        toggleListenerAccess(true);
        Thread.sleep(500); // wait for listener to be allowed

        mListener = TestNotificationListener.getInstance();
        assertNotNull(mListener);
        try {
            sendNotification(1, R.drawable.black);
            Thread.sleep(500); // wait for notification listener to receive notification
            NotificationListenerService.RankingMap rankingMap = mListener.mRankingMap;
            NotificationListenerService.Ranking outRanking =
                    new NotificationListenerService.Ranking();
            for (String key : rankingMap.getOrderedKeys()) {
                if (key.contains(mListener.getPackageName())) {
                    rankingMap.getRanking(key, outRanking);
                    assertTrue(outRanking.canShowBadge());
                }
            }

            // turn off badging globally
            SystemUtil.runWithShellPermissionIdentity(() ->
                    Settings.Secure.putInt(mContext.getContentResolver(),
                            Settings.Secure.NOTIFICATION_BADGING, 0));

            Thread.sleep(500); // wait for ranking update

            rankingMap = mListener.mRankingMap;
            outRanking = new NotificationListenerService.Ranking();
            for (String key : rankingMap.getOrderedKeys()) {
                if (key.contains(mListener.getPackageName())) {
                    assertFalse(outRanking.canShowBadge());
                }
            }

            mListener.resetData();
        } finally {
            SystemUtil.runWithShellPermissionIdentity(() ->
                    Settings.Secure.putInt(mContext.getContentResolver(),
                            Settings.Secure.NOTIFICATION_BADGING, originalBadging));
        }
    }

    public void testGetSuppressedVisualEffectsOff_ranking() throws Exception {
        toggleListenerAccess(true);
        Thread.sleep(500); // wait for listener to be allowed

        mListener = TestNotificationListener.getInstance();
        assertNotNull(mListener);

        final int notificationId = 1;
        sendNotification(notificationId, R.drawable.black);
        Thread.sleep(500); // wait for notification listener to receive notification

        NotificationListenerService.RankingMap rankingMap = mListener.mRankingMap;
        NotificationListenerService.Ranking outRanking =
                new NotificationListenerService.Ranking();

        for (String key : rankingMap.getOrderedKeys()) {
            if (key.contains(mListener.getPackageName())) {
                rankingMap.getRanking(key, outRanking);

                // check notification key match
                assertEquals(0, outRanking.getSuppressedVisualEffects());
            }
        }
    }

    public void testGetSuppressedVisualEffects_ranking() throws Exception {
        final int originalFilter = mNotificationManager.getCurrentInterruptionFilter();
        NotificationManager.Policy origPolicy = mNotificationManager.getNotificationPolicy();
        try {
            toggleListenerAccess(true);
            Thread.sleep(500); // wait for listener to be allowed

            mListener = TestNotificationListener.getInstance();
            assertNotNull(mListener);

            toggleNotificationPolicyAccess(mContext.getPackageName(),
                    InstrumentationRegistry.getInstrumentation(), true);
            if (mContext.getApplicationInfo().targetSdkVersion >= Build.VERSION_CODES.P) {
                mNotificationManager.setNotificationPolicy(new NotificationManager.Policy(0, 0, 0,
                        SUPPRESSED_EFFECT_SCREEN_ON | SUPPRESSED_EFFECT_PEEK));
            } else {
                mNotificationManager.setNotificationPolicy(new NotificationManager.Policy(0, 0, 0,
                        SUPPRESSED_EFFECT_SCREEN_ON));
            }
            mNotificationManager.setInterruptionFilter(INTERRUPTION_FILTER_PRIORITY);

            final int notificationId = 1;
            // update notification
            sendNotification(notificationId, R.drawable.black);
            Thread.sleep(500); // wait for notification listener to receive notification

            NotificationListenerService.RankingMap rankingMap = mListener.mRankingMap;
            NotificationListenerService.Ranking outRanking =
                    new NotificationListenerService.Ranking();

            for (String key : rankingMap.getOrderedKeys()) {
                if (key.contains(mListener.getPackageName())) {
                    rankingMap.getRanking(key, outRanking);

                    if (mContext.getApplicationInfo().targetSdkVersion >= Build.VERSION_CODES.P) {
                        assertEquals(SUPPRESSED_EFFECT_SCREEN_ON | SUPPRESSED_EFFECT_PEEK,
                                outRanking.getSuppressedVisualEffects());
                    } else {
                        assertEquals(SUPPRESSED_EFFECT_SCREEN_ON,
                                outRanking.getSuppressedVisualEffects());
                    }
                }
            }
        } finally {
            // reset notification policy
            mNotificationManager.setInterruptionFilter(originalFilter);
            mNotificationManager.setNotificationPolicy(origPolicy);
        }

    }

    public void testKeyChannelGroupOverrideImportanceExplanation_ranking() throws Exception {
        toggleListenerAccess(true);
        Thread.sleep(500); // wait for listener to be allowed

        mListener = TestNotificationListener.getInstance();
        assertNotNull(mListener);

        final int notificationId = 1;
        sendNotification(notificationId, R.drawable.black);
        Thread.sleep(500); // wait for notification listener to receive notification

        NotificationListenerService.RankingMap rankingMap = mListener.mRankingMap;
        NotificationListenerService.Ranking outRanking =
                new NotificationListenerService.Ranking();

        StatusBarNotification sbn = findPostedNotification(notificationId, false);

        // check that the key and channel ids are the same in the ranking as the posted notification
        for (String key : rankingMap.getOrderedKeys()) {
            if (key.contains(mListener.getPackageName())) {
                rankingMap.getRanking(key, outRanking);

                // check notification key match
                assertEquals(sbn.getKey(), outRanking.getKey());

                // check notification channel ids match
                assertEquals(sbn.getNotification().getChannelId(), outRanking.getChannel().getId());

                // check override group key match
                assertEquals(sbn.getOverrideGroupKey(), outRanking.getOverrideGroupKey());

                // check importance explanation isn't null
                assertNotNull(outRanking.getImportanceExplanation());
            }
        }
    }

    public void testNotify_blockedChannel() throws Exception {
        mNotificationManager.cancelAll();

        NotificationChannel channel =
                new NotificationChannel(mId, "name", IMPORTANCE_NONE);
        mNotificationManager.createNotificationChannel(channel);

        int id = 1;
        final Notification notification =
                new Notification.Builder(mContext, mId)
                        .setSmallIcon(R.drawable.black)
                        .setWhen(System.currentTimeMillis())
                        .setContentTitle("notify#" + id)
                        .setContentText("This is #" + id + "notification  ")
                        .build();
        mNotificationManager.notify(id, notification);

        if (!checkNotificationExistence(id, /*shouldExist=*/ false)) {
            fail("found unexpected notification id=" + id);
        }
    }

    public void testCancel() throws Exception {
        final int id = 9;
        sendNotification(id, R.drawable.black);
        // Wait for the notification posted not just enqueued
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
        }
        mNotificationManager.cancel(id);

        if (!checkNotificationExistence(id, /*shouldExist=*/ false)) {
            fail("canceled notification was still alive, id=" + id);
        }
    }

    public void testCancelAll() throws Exception {
        sendNotification(1, R.drawable.black);
        sendNotification(2, R.drawable.blue);
        sendNotification(3, R.drawable.yellow);

        if (DEBUG) {
            Log.d(TAG, "posted 3 notifications, here they are: ");
            StatusBarNotification[] sbns = mNotificationManager.getActiveNotifications();
            for (StatusBarNotification sbn : sbns) {
                Log.d(TAG, "  " + sbn);
            }
            Log.d(TAG, "about to cancel...");
        }
        mNotificationManager.cancelAll();

        for (int id = 1; id <= 3; id++) {
            if (!checkNotificationExistence(id, /*shouldExist=*/ false)) {
                fail("Failed to cancel notification id=" + id);
            }
        }

    }

    public void testNotifyWithTimeout() throws Exception {
        mNotificationManager.cancelAll();
        final int id = 128;
        final long timeout = 1000;

        final Notification notification =
                new Notification.Builder(mContext, NOTIFICATION_CHANNEL_ID)
                        .setSmallIcon(R.drawable.black)
                        .setContentTitle("notify#" + id)
                        .setContentText("This is #" + id + "notification  ")
                        .setTimeoutAfter(timeout)
                        .build();
        mNotificationManager.notify(id, notification);

        if (!checkNotificationExistence(id, /*shouldExist=*/ true)) {
            fail("couldn't find posted notification id=" + id);
        }

        try {
            Thread.sleep(timeout);
        } catch (InterruptedException ex) {
            // pass
        }
        checkNotificationExistence(id, false);
    }

    public void testStyle() throws Exception {
        Notification.Style style = new Notification.Style() {
            public boolean areNotificationsVisiblyDifferent(Notification.Style other) {
                return false;
            }
        };

        Notification.Builder builder = new Notification.Builder(mContext, NOTIFICATION_CHANNEL_ID);
        style.setBuilder(builder);

        Notification notification = null;
        try {
            notification = style.build();
        } catch (IllegalArgumentException e) {
            fail(e.getMessage());
        }

        assertNotNull(notification);

        Notification builderNotification = builder.build();
        assertEquals(builderNotification, notification);
    }

    public void testStyle_getStandardView() throws Exception {
        Notification.Builder builder = new Notification.Builder(mContext, NOTIFICATION_CHANNEL_ID);
        int layoutId = 0;

        TestStyle overrideStyle = new TestStyle();
        overrideStyle.setBuilder(builder);
        RemoteViews result = overrideStyle.testGetStandardView(layoutId);

        assertNotNull(result);
        assertEquals(layoutId, result.getLayoutId());
    }

    private class TestStyle extends Notification.Style {
        public boolean areNotificationsVisiblyDifferent(Notification.Style other) {
            return false;
        }

        public RemoteViews testGetStandardView(int layoutId) {
            // Wrapper method, since getStandardView is protected and otherwise unused in Android
            return getStandardView(layoutId);
        }
    }

    public void testMediaStyle_empty() {
        Notification.MediaStyle style = new Notification.MediaStyle();
        assertNotNull(style);
    }

    public void testMediaStyle() {
        mNotificationManager.cancelAll();
        final int id = 99;
        MediaSession session = new MediaSession(getContext(), "media");

        final Notification notification =
                new Notification.Builder(mContext, NOTIFICATION_CHANNEL_ID)
                        .setSmallIcon(R.drawable.black)
                        .setContentTitle("notify#" + id)
                        .setContentText("This is #" + id + "notification  ")
                        .addAction(new Notification.Action.Builder(
                                Icon.createWithResource(getContext(), R.drawable.icon_black),
                                "play", getPendingIntent()).build())
                        .addAction(new Notification.Action.Builder(
                                Icon.createWithResource(getContext(), R.drawable.icon_blue),
                                "pause", getPendingIntent()).build())
                        .setStyle(new Notification.MediaStyle()
                                .setShowActionsInCompactView(0, 1)
                                .setMediaSession(session.getSessionToken()))
                        .build();
        mNotificationManager.notify(id, notification);

        if (!checkNotificationExistence(id, /*shouldExist=*/ true)) {
            fail("couldn't find posted notification id=" + id);
        }
    }

    public void testInboxStyle() {
        final int id = 100;

        final Notification notification =
                new Notification.Builder(mContext, NOTIFICATION_CHANNEL_ID)
                        .setSmallIcon(R.drawable.black)
                        .setContentTitle("notify#" + id)
                        .setContentText("This is #" + id + "notification  ")
                        .addAction(new Notification.Action.Builder(
                                Icon.createWithResource(getContext(), R.drawable.icon_black),
                                "a1", getPendingIntent()).build())
                        .addAction(new Notification.Action.Builder(
                                Icon.createWithResource(getContext(), R.drawable.icon_blue),
                                "a2", getPendingIntent()).build())
                        .setStyle(new Notification.InboxStyle().addLine("line")
                                .setSummaryText("summary"))
                        .build();
        mNotificationManager.notify(id, notification);

        if (!checkNotificationExistence(id, /*shouldExist=*/ true)) {
            fail("couldn't find posted notification id=" + id);
        }
    }

    public void testBigTextStyle() {
        final int id = 101;

        final Notification notification =
                new Notification.Builder(mContext, NOTIFICATION_CHANNEL_ID)
                        .setSmallIcon(R.drawable.black)
                        .setContentTitle("notify#" + id)
                        .setContentText("This is #" + id + "notification  ")
                        .addAction(new Notification.Action.Builder(
                                Icon.createWithResource(getContext(), R.drawable.icon_black),
                                "a1", getPendingIntent()).build())
                        .addAction(new Notification.Action.Builder(
                                Icon.createWithResource(getContext(), R.drawable.icon_blue),
                                "a2", getPendingIntent()).build())
                        .setStyle(new Notification.BigTextStyle()
                                .setBigContentTitle("big title")
                                .bigText("big text")
                                .setSummaryText("summary"))
                        .build();
        mNotificationManager.notify(id, notification);

        if (!checkNotificationExistence(id, /*shouldExist=*/ true)) {
            fail("couldn't find posted notification id=" + id);
        }
    }

    public void testBigPictureStyle() {
        final int id = 102;

        final Notification notification =
                new Notification.Builder(mContext, NOTIFICATION_CHANNEL_ID)
                        .setSmallIcon(R.drawable.black)
                        .setContentTitle("notify#" + id)
                        .setContentText("This is #" + id + "notification  ")
                        .addAction(new Notification.Action.Builder(
                                Icon.createWithResource(getContext(), R.drawable.icon_black),
                                "a1", getPendingIntent()).build())
                        .addAction(new Notification.Action.Builder(
                                Icon.createWithResource(getContext(), R.drawable.icon_blue),
                                "a2", getPendingIntent()).build())
                        .setStyle(new Notification.BigPictureStyle()
                                .setBigContentTitle("title")
                                .bigPicture(Bitmap.createBitmap(100, 100, Bitmap.Config.RGB_565))
                                .bigLargeIcon(
                                        Icon.createWithResource(getContext(), R.drawable.icon_blue))
                                .setSummaryText("summary")
                                .setContentDescription("content description"))
                        .build();
        mNotificationManager.notify(id, notification);

        if (!checkNotificationExistence(id, /*shouldExist=*/ true)) {
            fail("couldn't find posted notification id=" + id);
        }
    }

    public void testAutogrouping() throws Exception {
        sendNotification(801, R.drawable.black);
        sendNotification(802, R.drawable.blue);
        sendNotification(803, R.drawable.yellow);
        sendNotification(804, R.drawable.yellow);

        assertNotificationCount(5);
        assertAllPostedNotificationsAutogrouped();
    }

    public void testAutogrouping_autogroupStaysUntilAllNotificationsCanceled() throws Exception {
        sendNotification(701, R.drawable.black);
        sendNotification(702, R.drawable.blue);
        sendNotification(703, R.drawable.yellow);
        sendNotification(704, R.drawable.yellow);

        assertNotificationCount(5);
        assertAllPostedNotificationsAutogrouped();

        // Assert all notis stay in the same autogroup until all children are canceled
        for (int i = 704; i > 701; i--) {
            cancelAndPoll(i);
            assertNotificationCount(i - 700);
            assertAllPostedNotificationsAutogrouped();
        }
        cancelAndPoll(701);
        assertNotificationCount(0);
    }

    public void testAutogrouping_autogroupStaysUntilAllNotificationsAddedToGroup()
            throws Exception {
        String newGroup = "new!";
        sendNotification(901, R.drawable.black);
        sendNotification(902, R.drawable.blue);
        sendNotification(903, R.drawable.yellow);
        sendNotification(904, R.drawable.yellow);

        List<Integer> postedIds = new ArrayList<>();
        postedIds.add(901);
        postedIds.add(902);
        postedIds.add(903);
        postedIds.add(904);

        assertNotificationCount(5);
        assertAllPostedNotificationsAutogrouped();

        // Assert all notis stay in the same autogroup until all children are canceled
        for (int i = 904; i > 901; i--) {
            sendNotification(i, newGroup, R.drawable.blue);
            postedIds.remove(postedIds.size() - 1);
            assertNotificationCount(5);
            assertOnlySomeNotificationsAutogrouped(postedIds);
        }
        sendNotification(901, newGroup, R.drawable.blue);
        assertNotificationCount(4); // no more autogroup summary
        postedIds.remove(0);
        assertOnlySomeNotificationsAutogrouped(postedIds);
    }

    public void testNewNotificationsAddedToAutogroup_ifOriginalNotificationsCanceled()
            throws Exception {
        String newGroup = "new!";
        sendNotification(910, R.drawable.black);
        sendNotification(920, R.drawable.blue);
        sendNotification(930, R.drawable.yellow);
        sendNotification(940, R.drawable.yellow);

        List<Integer> postedIds = new ArrayList<>();
        postedIds.add(910);
        postedIds.add(920);
        postedIds.add(930);
        postedIds.add(940);

        assertNotificationCount(5);
        assertAllPostedNotificationsAutogrouped();

        // regroup all but one of the children
        for (int i = postedIds.size() - 1; i > 0; i--) {
            try {
                Thread.sleep(200);
            } catch (InterruptedException ex) {
                // pass
            }
            int id = postedIds.remove(i);
            sendNotification(id, newGroup, R.drawable.blue);
            assertNotificationCount(5);
            assertOnlySomeNotificationsAutogrouped(postedIds);
        }

        // send a new non-grouped notification. since the autogroup summary still exists,
        // the notification should be added to it
        sendNotification(950, R.drawable.blue);
        postedIds.add(950);
        try {
            Thread.sleep(200);
        } catch (InterruptedException ex) {
            // pass
        }
        assertOnlySomeNotificationsAutogrouped(postedIds);
    }

    public void testTotalSilenceOnlyMuteStreams() throws Exception {
        final int originalFilter = mNotificationManager.getCurrentInterruptionFilter();
        Policy origPolicy = mNotificationManager.getNotificationPolicy();
        try {
            toggleNotificationPolicyAccess(mContext.getPackageName(),
                    InstrumentationRegistry.getInstrumentation(), true);

            // ensure volume is not muted/0 to start test
            mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 1, 0);
            // exception for presidential alert
            //mAudioManager.setStreamVolume(AudioManager.STREAM_ALARM, 1, 0);
            mAudioManager.setStreamVolume(AudioManager.STREAM_SYSTEM, 1, 0);
            mAudioManager.setStreamVolume(AudioManager.STREAM_RING, 1, 0);

            mNotificationManager.setNotificationPolicy(new NotificationManager.Policy(
                    PRIORITY_CATEGORY_ALARMS | PRIORITY_CATEGORY_MEDIA, 0, 0));
            AutomaticZenRule rule = createRule("test_total_silence", INTERRUPTION_FILTER_NONE);
            String id = mNotificationManager.addAutomaticZenRule(rule);
            mRuleIds.add(id);
            Condition condition =
                    new Condition(rule.getConditionId(), "summary", Condition.STATE_TRUE);
            mNotificationManager.setAutomaticZenRuleState(id, condition);
            mNotificationManager.setInterruptionFilter(INTERRUPTION_FILTER_PRIORITY);

            // delay for streams to get into correct mute states
            Thread.sleep(1000);
            assertTrue("Music (media) stream should be muted",
                    mAudioManager.isStreamMute(AudioManager.STREAM_MUSIC));
            assertTrue("System stream should be muted",
                    mAudioManager.isStreamMute(AudioManager.STREAM_SYSTEM));
            // exception for presidential alert
            //assertTrue("Alarm stream should be muted",
            //        mAudioManager.isStreamMute(AudioManager.STREAM_ALARM));

            // Test requires that the phone's default state has no channels that can bypass dnd
            // which we can't currently guarantee (b/169267379)
            // assertTrue("Ringer stream should be muted",
            //        mAudioManager.isStreamMute(AudioManager.STREAM_RING));
        } finally {
            mNotificationManager.setInterruptionFilter(originalFilter);
            mNotificationManager.setNotificationPolicy(origPolicy);
        }
    }

    public void testAlarmsOnlyMuteStreams() throws Exception {
        final int originalFilter = mNotificationManager.getCurrentInterruptionFilter();
        Policy origPolicy = mNotificationManager.getNotificationPolicy();
        try {
            toggleNotificationPolicyAccess(mContext.getPackageName(),
                    InstrumentationRegistry.getInstrumentation(), true);

            // ensure volume is not muted/0 to start test
            mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 1, 0);
            mAudioManager.setStreamVolume(AudioManager.STREAM_ALARM, 1, 0);
            mAudioManager.setStreamVolume(AudioManager.STREAM_SYSTEM, 1, 0);
            mAudioManager.setStreamVolume(AudioManager.STREAM_RING, 1, 0);

            mNotificationManager.setNotificationPolicy(new NotificationManager.Policy(
                    PRIORITY_CATEGORY_ALARMS | PRIORITY_CATEGORY_MEDIA, 0, 0));
            AutomaticZenRule rule = createRule("test_alarms", INTERRUPTION_FILTER_ALARMS);
            String id = mNotificationManager.addAutomaticZenRule(rule);
            mRuleIds.add(id);
            Condition condition =
                    new Condition(rule.getConditionId(), "summary", Condition.STATE_TRUE);
            mNotificationManager.setAutomaticZenRuleState(id, condition);
            mNotificationManager.setInterruptionFilter(INTERRUPTION_FILTER_PRIORITY);

            // delay for streams to get into correct mute states
            Thread.sleep(1000);
            assertFalse("Music (media) stream should not be muted",
                    mAudioManager.isStreamMute(AudioManager.STREAM_MUSIC));
            assertTrue("System stream should be muted",
                    mAudioManager.isStreamMute(AudioManager.STREAM_SYSTEM));
            assertFalse("Alarm stream should not be muted",
                    mAudioManager.isStreamMute(AudioManager.STREAM_ALARM));

            // Test requires that the phone's default state has no channels that can bypass dnd
            // which we can't currently guarantee (b/169267379)
            // assertTrue("Ringer stream should be muted",
            //  mAudioManager.isStreamMute(AudioManager.STREAM_RING));
        } finally {
            mNotificationManager.setInterruptionFilter(originalFilter);
            mNotificationManager.setNotificationPolicy(origPolicy);
        }
    }

    public void testAddAutomaticZenRule_configActivity() throws Exception {
        toggleNotificationPolicyAccess(mContext.getPackageName(),
                InstrumentationRegistry.getInstrumentation(), true);

        AutomaticZenRule ruleToCreate = createRule("Rule");
        String id = mNotificationManager.addAutomaticZenRule(ruleToCreate);

        assertNotNull(id);
        mRuleIds.add(id);
        assertTrue(areRulesSame(ruleToCreate, mNotificationManager.getAutomaticZenRule(id)));
    }

    public void testUpdateAutomaticZenRule_configActivity() throws Exception {
        toggleNotificationPolicyAccess(mContext.getPackageName(),
                InstrumentationRegistry.getInstrumentation(), true);

        AutomaticZenRule ruleToCreate = createRule("Rule");
        String id = mNotificationManager.addAutomaticZenRule(ruleToCreate);
        ruleToCreate.setEnabled(false);
        mNotificationManager.updateAutomaticZenRule(id, ruleToCreate);

        assertNotNull(id);
        mRuleIds.add(id);
        assertTrue(areRulesSame(ruleToCreate, mNotificationManager.getAutomaticZenRule(id)));
    }

    public void testRemoveAutomaticZenRule_configActivity() throws Exception {
        toggleNotificationPolicyAccess(mContext.getPackageName(),
                InstrumentationRegistry.getInstrumentation(), true);

        AutomaticZenRule ruleToCreate = createRule("Rule");
        String id = mNotificationManager.addAutomaticZenRule(ruleToCreate);

        assertNotNull(id);
        mRuleIds.add(id);
        mNotificationManager.removeAutomaticZenRule(id);

        assertNull(mNotificationManager.getAutomaticZenRule(id));
        assertEquals(0, mNotificationManager.getAutomaticZenRules().size());
    }

    public void testSetAutomaticZenRuleState() throws Exception {
        toggleNotificationPolicyAccess(mContext.getPackageName(),
                InstrumentationRegistry.getInstrumentation(), true);

        AutomaticZenRule ruleToCreate = createRule("Rule");
        String id = mNotificationManager.addAutomaticZenRule(ruleToCreate);
        mRuleIds.add(id);

        // make sure DND is off
        assertExpectedDndState(INTERRUPTION_FILTER_ALL);

        // enable DND
        Condition condition =
                new Condition(ruleToCreate.getConditionId(), "summary", Condition.STATE_TRUE);
        mNotificationManager.setAutomaticZenRuleState(id, condition);

        assertExpectedDndState(ruleToCreate.getInterruptionFilter());
    }

    public void testSetAutomaticZenRuleState_turnOff() throws Exception {
        toggleNotificationPolicyAccess(mContext.getPackageName(),
                InstrumentationRegistry.getInstrumentation(), true);

        AutomaticZenRule ruleToCreate = createRule("Rule");
        String id = mNotificationManager.addAutomaticZenRule(ruleToCreate);
        mRuleIds.add(id);

        // make sure DND is off
        // make sure DND is off
        assertExpectedDndState(INTERRUPTION_FILTER_ALL);

        // enable DND
        Condition condition =
                new Condition(ruleToCreate.getConditionId(), "on", Condition.STATE_TRUE);
        mNotificationManager.setAutomaticZenRuleState(id, condition);

        assertExpectedDndState(ruleToCreate.getInterruptionFilter());

        // disable DND
        condition = new Condition(ruleToCreate.getConditionId(), "off", Condition.STATE_FALSE);

        mNotificationManager.setAutomaticZenRuleState(id, condition);

        // make sure DND is off
        assertExpectedDndState(INTERRUPTION_FILTER_ALL);
    }

    public void testSetAutomaticZenRuleState_deletedRule() throws Exception {
        toggleNotificationPolicyAccess(mContext.getPackageName(),
                InstrumentationRegistry.getInstrumentation(), true);

        AutomaticZenRule ruleToCreate = createRule("Rule");
        String id = mNotificationManager.addAutomaticZenRule(ruleToCreate);
        mRuleIds.add(id);

        // make sure DND is off
        assertExpectedDndState(INTERRUPTION_FILTER_ALL);

        // enable DND
        Condition condition =
                new Condition(ruleToCreate.getConditionId(), "summary", Condition.STATE_TRUE);
        mNotificationManager.setAutomaticZenRuleState(id, condition);

        assertExpectedDndState(ruleToCreate.getInterruptionFilter());

        mNotificationManager.removeAutomaticZenRule(id);

        // make sure DND is off
        assertExpectedDndState(INTERRUPTION_FILTER_ALL);
    }

    public void testSetAutomaticZenRuleState_multipleRules() throws Exception {
        toggleNotificationPolicyAccess(mContext.getPackageName(),
                InstrumentationRegistry.getInstrumentation(), true);

        AutomaticZenRule ruleToCreate = createRule("Rule");
        String id = mNotificationManager.addAutomaticZenRule(ruleToCreate);
        mRuleIds.add(id);

        AutomaticZenRule secondRuleToCreate = createRule("Rule 2");
        secondRuleToCreate.setInterruptionFilter(INTERRUPTION_FILTER_NONE);
        String secondId = mNotificationManager.addAutomaticZenRule(secondRuleToCreate);
        mRuleIds.add(secondId);

        // make sure DND is off
        assertExpectedDndState(INTERRUPTION_FILTER_ALL);

        // enable DND
        Condition condition =
                new Condition(ruleToCreate.getConditionId(), "summary", Condition.STATE_TRUE);
        mNotificationManager.setAutomaticZenRuleState(id, condition);
        Condition secondCondition =
                new Condition(secondRuleToCreate.getConditionId(), "summary", Condition.STATE_TRUE);
        mNotificationManager.setAutomaticZenRuleState(secondId, secondCondition);

        // the second rule has a 'more silent' DND filter, so the system wide DND should be
        // using its filter
        assertExpectedDndState(secondRuleToCreate.getInterruptionFilter());

        // remove intense rule, system should fallback to other rule
        mNotificationManager.removeAutomaticZenRule(secondId);
        assertExpectedDndState(ruleToCreate.getInterruptionFilter());
    }

    public void testSetNotificationPolicy_P_setOldFields() throws Exception {
        toggleNotificationPolicyAccess(mContext.getPackageName(),
                InstrumentationRegistry.getInstrumentation(), true);
        Policy origPolicy = mNotificationManager.getNotificationPolicy();
        try {
            if (mContext.getApplicationInfo().targetSdkVersion >= Build.VERSION_CODES.P) {
                NotificationManager.Policy appPolicy = new NotificationManager.Policy(0, 0, 0,
                        SUPPRESSED_EFFECT_SCREEN_ON | SUPPRESSED_EFFECT_SCREEN_OFF);
                mNotificationManager.setNotificationPolicy(appPolicy);

                int expected = SUPPRESSED_EFFECT_SCREEN_ON | SUPPRESSED_EFFECT_SCREEN_OFF
                        | SUPPRESSED_EFFECT_PEEK | SUPPRESSED_EFFECT_AMBIENT
                        | SUPPRESSED_EFFECT_LIGHTS | SUPPRESSED_EFFECT_FULL_SCREEN_INTENT;

                assertEquals(expected,
                        mNotificationManager.getNotificationPolicy().suppressedVisualEffects);
            }
        } finally {
            mNotificationManager.setNotificationPolicy(origPolicy);
        }
    }

    public void testSetNotificationPolicy_P_setNewFields() throws Exception {
        toggleNotificationPolicyAccess(mContext.getPackageName(),
                InstrumentationRegistry.getInstrumentation(), true);
        Policy origPolicy = mNotificationManager.getNotificationPolicy();
        try {
            if (mContext.getApplicationInfo().targetSdkVersion >= Build.VERSION_CODES.P) {
                NotificationManager.Policy appPolicy = new NotificationManager.Policy(0, 0, 0,
                        SUPPRESSED_EFFECT_NOTIFICATION_LIST | SUPPRESSED_EFFECT_AMBIENT
                                | SUPPRESSED_EFFECT_LIGHTS | SUPPRESSED_EFFECT_FULL_SCREEN_INTENT);
                mNotificationManager.setNotificationPolicy(appPolicy);

                int expected = SUPPRESSED_EFFECT_NOTIFICATION_LIST | SUPPRESSED_EFFECT_SCREEN_OFF
                        | SUPPRESSED_EFFECT_AMBIENT | SUPPRESSED_EFFECT_LIGHTS
                        | SUPPRESSED_EFFECT_FULL_SCREEN_INTENT;
                assertEquals(expected,
                        mNotificationManager.getNotificationPolicy().suppressedVisualEffects);
            }
        } finally {
            mNotificationManager.setNotificationPolicy(origPolicy);
        }
    }

    public void testSetNotificationPolicy_P_setOldNewFields() throws Exception {
        toggleNotificationPolicyAccess(mContext.getPackageName(),
                InstrumentationRegistry.getInstrumentation(), true);
        Policy origPolicy = mNotificationManager.getNotificationPolicy();
        try {
            if (mContext.getApplicationInfo().targetSdkVersion >= Build.VERSION_CODES.P) {

                NotificationManager.Policy appPolicy = new NotificationManager.Policy(0, 0, 0,
                        SUPPRESSED_EFFECT_SCREEN_ON | SUPPRESSED_EFFECT_STATUS_BAR);
                mNotificationManager.setNotificationPolicy(appPolicy);

                int expected = SUPPRESSED_EFFECT_STATUS_BAR;
                assertEquals(expected,
                        mNotificationManager.getNotificationPolicy().suppressedVisualEffects);

                appPolicy = new NotificationManager.Policy(0, 0, 0,
                        SUPPRESSED_EFFECT_SCREEN_ON | SUPPRESSED_EFFECT_AMBIENT
                                | SUPPRESSED_EFFECT_LIGHTS | SUPPRESSED_EFFECT_FULL_SCREEN_INTENT);
                mNotificationManager.setNotificationPolicy(appPolicy);

                expected = SUPPRESSED_EFFECT_SCREEN_OFF | SUPPRESSED_EFFECT_AMBIENT
                        | SUPPRESSED_EFFECT_LIGHTS | SUPPRESSED_EFFECT_FULL_SCREEN_INTENT;
                assertEquals(expected,
                        mNotificationManager.getNotificationPolicy().suppressedVisualEffects);
            }
        } finally {
            mNotificationManager.setNotificationPolicy(origPolicy);
        }
    }

    public void testPostFullScreenIntent_permission() {
        int id = 6000;

        final Notification notification =
                new Notification.Builder(mContext, NOTIFICATION_CHANNEL_ID)
                        .setSmallIcon(R.drawable.black)
                        .setWhen(System.currentTimeMillis())
                        .setFullScreenIntent(getPendingIntent(), true)
                        .setContentText("This is #FSI notification")
                        .setContentIntent(getPendingIntent())
                        .build();
        mNotificationManager.notify(id, notification);

        StatusBarNotification n = findPostedNotification(id, false);
        assertNotNull(n);
        assertEquals(notification.fullScreenIntent, n.getNotification().fullScreenIntent);
    }

    public void testNotificationPolicyVisualEffectsEqual() {
        NotificationManager.Policy policy = new NotificationManager.Policy(0, 0, 0,
                SUPPRESSED_EFFECT_SCREEN_ON);
        NotificationManager.Policy policy2 = new NotificationManager.Policy(0, 0, 0,
                SUPPRESSED_EFFECT_PEEK);
        assertTrue(policy.equals(policy2));
        assertTrue(policy2.equals(policy));

        policy = new NotificationManager.Policy(0, 0, 0,
                SUPPRESSED_EFFECT_SCREEN_ON);
        policy2 = new NotificationManager.Policy(0, 0, 0,
                0);
        assertFalse(policy.equals(policy2));
        assertFalse(policy2.equals(policy));

        policy = new NotificationManager.Policy(0, 0, 0,
                SUPPRESSED_EFFECT_SCREEN_OFF);
        policy2 = new NotificationManager.Policy(0, 0, 0,
                SUPPRESSED_EFFECT_FULL_SCREEN_INTENT | SUPPRESSED_EFFECT_AMBIENT
                        | SUPPRESSED_EFFECT_LIGHTS);
        assertTrue(policy.equals(policy2));
        assertTrue(policy2.equals(policy));

        policy = new NotificationManager.Policy(0, 0, 0,
                SUPPRESSED_EFFECT_SCREEN_OFF);
        policy2 = new NotificationManager.Policy(0, 0, 0,
                SUPPRESSED_EFFECT_LIGHTS);
        assertFalse(policy.equals(policy2));
        assertFalse(policy2.equals(policy));
    }

    public void testNotificationDelegate_grantAndPost() throws Exception {
        // grant this test permission to post
        final Intent activityIntent = new Intent();
        activityIntent.setPackage(TEST_APP);
        activityIntent.setAction(Intent.ACTION_MAIN);
        activityIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        activityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        // wait for the activity to launch and finish
        mContext.startActivity(activityIntent);
        Thread.sleep(mActivityManager.isLowRamDevice() ? 1500 : 1000);

        // send notification
        Notification n = new Notification.Builder(mContext, "channel")
                .setSmallIcon(android.R.id.icon)
                .build();
        mNotificationManager.notifyAsPackage(TEST_APP, "tag", 0, n);

        assertNotNull(findPostedNotification(0, false));
        final Intent revokeIntent = new Intent();
        revokeIntent.setClassName(TEST_APP, REVOKE_CLASS);
        revokeIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mContext.startActivity(revokeIntent);
        Thread.sleep(1000);
    }

    public void testNotificationDelegate_grantAndPostAndCancel() throws Exception {
        // grant this test permission to post
        final Intent activityIntent = new Intent();
        activityIntent.setPackage(TEST_APP);
        activityIntent.setAction(Intent.ACTION_MAIN);
        activityIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        activityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        // wait for the activity to launch and finish
        mContext.startActivity(activityIntent);
        Thread.sleep(1000);

        // send notification
        Notification n = new Notification.Builder(mContext, "channel")
                .setSmallIcon(android.R.id.icon)
                .build();
        mNotificationManager.notifyAsPackage(TEST_APP, "toBeCanceled", 10000, n);
        assertNotNull(findPostedNotification(10000, false));
        mNotificationManager.cancelAsPackage(TEST_APP, "toBeCanceled", 10000);
        assertNotificationCancelled(10000, false);
        final Intent revokeIntent = new Intent();
        revokeIntent.setClassName(TEST_APP, REVOKE_CLASS);
        revokeIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mContext.startActivity(revokeIntent);
        Thread.sleep(1000);
    }

    public void testNotificationDelegate_cannotCancelNotificationsPostedByDelegator()
            throws Exception {
        toggleListenerAccess(true);
        Thread.sleep(500); // wait for listener to be allowed

        mListener = TestNotificationListener.getInstance();
        assertNotNull(mListener);

        // grant this test permission to post
        final Intent activityIntent = new Intent();
        activityIntent.setClassName(TEST_APP, DELEGATE_POST_CLASS);
        activityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        mContext.startActivity(activityIntent);

        Thread.sleep(1000);

        assertNotNull(findPostedNotification(9, true));

        try {
            mNotificationManager.cancelAsPackage(TEST_APP, null, 9);
            fail("Delegate should not be able to cancel notification they did not post");
        } catch (SecurityException e) {
            // yay
        }

        // double check that the notification does still exist
        assertNotNull(findPostedNotification(9, true));

        final Intent revokeIntent = new Intent();
        revokeIntent.setClassName(TEST_APP, REVOKE_CLASS);
        revokeIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mContext.startActivity(revokeIntent);
        Thread.sleep(1000);
    }

    public void testNotificationDelegate_grantAndReadChannels() throws Exception {
        // grant this test permission to post
        final Intent activityIntent = new Intent();
        activityIntent.setPackage(TEST_APP);
        activityIntent.setAction(Intent.ACTION_MAIN);
        activityIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        activityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        // wait for the activity to launch and finish
        mContext.startActivity(activityIntent);
        Thread.sleep(500);

        List<NotificationChannel> channels =
                mContext.createPackageContextAsUser(TEST_APP, /* flags= */ 0, mContext.getUser())
                        .getSystemService(NotificationManager.class)
                        .getNotificationChannels();

        assertNotNull(channels);

        final Intent revokeIntent = new Intent();
        revokeIntent.setClassName(TEST_APP, REVOKE_CLASS);
        revokeIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mContext.startActivity(revokeIntent);
        Thread.sleep(500);
    }

    public void testNotificationDelegate_grantAndReadChannel() throws Exception {
        // grant this test permission to post
        final Intent activityIntent = new Intent();
        activityIntent.setPackage(TEST_APP);
        activityIntent.setAction(Intent.ACTION_MAIN);
        activityIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        activityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        // wait for the activity to launch and finish
        mContext.startActivity(activityIntent);
        Thread.sleep(2000);

        NotificationChannel channel =
                mContext.createPackageContextAsUser(TEST_APP, /* flags= */ 0, mContext.getUser())
                        .getSystemService(NotificationManager.class)
                        .getNotificationChannel("channel");

        assertNotNull(channel);

        final Intent revokeIntent = new Intent();
        revokeIntent.setClassName(TEST_APP, REVOKE_CLASS);
        revokeIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mContext.startActivity(revokeIntent);
        Thread.sleep(500);
    }

    public void testNotificationDelegate_grantAndRevoke() throws Exception {
        // grant this test permission to post
        final Intent activityIntent = new Intent();
        activityIntent.setPackage(TEST_APP);
        activityIntent.setAction(Intent.ACTION_MAIN);
        activityIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        activityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        mContext.startActivity(activityIntent);
        Thread.sleep(500);

        assertTrue(mNotificationManager.canNotifyAsPackage(TEST_APP));

        final Intent revokeIntent = new Intent();
        revokeIntent.setClassName(TEST_APP, REVOKE_CLASS);
        revokeIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mContext.startActivity(revokeIntent);
        Thread.sleep(500);

        try {
            // send notification
            Notification n = new Notification.Builder(mContext, "channel")
                    .setSmallIcon(android.R.id.icon)
                    .build();
            mNotificationManager.notifyAsPackage(TEST_APP, "tag", 0, n);
            fail("Should not be able to post as a delegate when permission revoked");
        } catch (SecurityException e) {
            // yay
        }
    }

    public void testNotificationIcon() {
        int id = 6000;

        Notification notification =
                new Notification.Builder(mContext, NOTIFICATION_CHANNEL_ID)
                        .setSmallIcon(android.R.id.icon)
                        .setWhen(System.currentTimeMillis())
                        .setFullScreenIntent(getPendingIntent(), true)
                        .setContentText("This notification has a resource icon")
                        .setContentIntent(getPendingIntent())
                        .build();
        mNotificationManager.notify(id, notification);

        notification =
                new Notification.Builder(mContext, NOTIFICATION_CHANNEL_ID)
                        .setSmallIcon(Icon.createWithResource(mContext, android.R.id.icon))
                        .setWhen(System.currentTimeMillis())
                        .setFullScreenIntent(getPendingIntent(), true)
                        .setContentText("This notification has an Icon icon")
                        .setContentIntent(getPendingIntent())
                        .build();
        mNotificationManager.notify(id, notification);

        StatusBarNotification n = findPostedNotification(id, false);
        assertNotNull(n);
    }

    public void testShouldHideSilentStatusIcons() throws Exception {
        try {
            mNotificationManager.shouldHideSilentStatusBarIcons();
            fail("Non-privileged apps should not get this information");
        } catch (SecurityException e) {
            // pass
        }

        toggleListenerAccess(true);
        // no exception this time
        mNotificationManager.shouldHideSilentStatusBarIcons();
    }

    public void testMatchesCallFilter_noPermissions() {
        // make sure we definitely don't have contacts access
        boolean hadReadPerm = hasReadContactsPermission(TEST_APP);
        try {
            toggleReadContactsPermission(TEST_APP, false);

            // start an activity that has no permissions, which will run matchesCallFilter on
            // a meaningless uri. The result code indicates whether or not the method call was
            // permitted.
            final Intent mcfIntent = new Intent();
            mcfIntent.setPackage(TEST_APP);
            mcfIntent.setClassName(TEST_APP, MATCHES_CALL_FILTER_CLASS);
            GetResultActivity grActivity = setUpGetResultActivity();
            grActivity.startActivityForResult(mcfIntent, REQUEST_CODE);
            UiDevice.getInstance(mInstrumentation).waitForIdle();

            // with no permissions, this call should not have been permitted
            GetResultActivity.Result result = grActivity.getResult();
            assertEquals(REQUEST_CODE, result.requestCode);
            assertEquals(MATCHES_CALL_FILTER_NOT_PERMITTED, result.resultCode);
            grActivity.finishActivity(REQUEST_CODE);
        } finally {
            toggleReadContactsPermission(TEST_APP, hadReadPerm);
        }
    }

    public void testMatchesCallFilter_listenerPermissionOnly() throws Exception {
        boolean hadReadPerm = hasReadContactsPermission(TEST_APP);
        // minimal listener service so that it can be given listener permissions
        final ComponentName listenerComponent =
                new ComponentName(TEST_APP, MINIMAL_LISTENER_CLASS);
        try {
            // make surethat we don't for some reason have contacts access
            toggleReadContactsPermission(TEST_APP, false);

            // grant the notification app package notification listener access;
            // give it time to succeed
            toggleExternalListenerAccess(listenerComponent, true);
            Thread.sleep(500);

            // set up & run intent
            final Intent mcfIntent = new Intent();
            mcfIntent.setPackage(TEST_APP);
            mcfIntent.setClassName(TEST_APP, MATCHES_CALL_FILTER_CLASS);
            GetResultActivity grActivity = setUpGetResultActivity();
            grActivity.startActivityForResult(mcfIntent, REQUEST_CODE);
            UiDevice.getInstance(mInstrumentation).waitForIdle();

            // with just listener permissions, this call should have been permitted
            GetResultActivity.Result result = grActivity.getResult();
            assertEquals(REQUEST_CODE, result.requestCode);
            assertEquals(MATCHES_CALL_FILTER_PERMITTED, result.resultCode);
            grActivity.finishActivity(REQUEST_CODE);
        } finally {
            // clean up listener access, reset read contacts access
            toggleExternalListenerAccess(listenerComponent, false);
            toggleReadContactsPermission(TEST_APP, hadReadPerm);
        }
    }

    public void testMatchesCallFilter_contactsPermissionOnly() throws Exception {
        // grant the notification app package contacts read access
        boolean hadReadPerm = hasReadContactsPermission(TEST_APP);
        try {
            toggleReadContactsPermission(TEST_APP, true);

            // set up & run intent
            final Intent mcfIntent = new Intent();
            mcfIntent.setPackage(TEST_APP);
            mcfIntent.setClassName(TEST_APP, MATCHES_CALL_FILTER_CLASS);
            GetResultActivity grActivity = setUpGetResultActivity();
            grActivity.startActivityForResult(mcfIntent, REQUEST_CODE);
            UiDevice.getInstance(mInstrumentation).waitForIdle();

            // with just contacts read permissions, this call should have been permitted
            GetResultActivity.Result result = grActivity.getResult();
            assertEquals(REQUEST_CODE, result.requestCode);
            assertEquals(MATCHES_CALL_FILTER_PERMITTED, result.resultCode);
            grActivity.finishActivity(REQUEST_CODE);
        } finally {
            // clean up contacts access
            toggleReadContactsPermission(TEST_APP, hadReadPerm);
        }
    }

    public void testMatchesCallFilter_zenOff() throws Exception {
        // zen mode is not on so nothing is filtered; matchesCallFilter should always pass
        toggleNotificationPolicyAccess(mContext.getPackageName(),
                InstrumentationRegistry.getInstrumentation(), true);
        int origFilter = mNotificationManager.getCurrentInterruptionFilter();
        try {
            // allowed from anyone: nothing is filtered, and make sure change went through
            mNotificationManager.setInterruptionFilter(INTERRUPTION_FILTER_ALL);
            assertExpectedDndState(INTERRUPTION_FILTER_ALL);

            // create a phone URI from which to receive a call
            Uri phoneUri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                    Uri.encode("+16175551212"));
            assertTrue(mNotificationManager.matchesCallFilter(phoneUri));
        } finally {
            mNotificationManager.setInterruptionFilter(origFilter);
        }
    }

    public void testMatchesCallFilter_noCallInterruptions() throws Exception {
        // when no call interruptions are allowed at all, or only alarms, matchesCallFilter
        // should always fail
        toggleNotificationPolicyAccess(mContext.getPackageName(),
                InstrumentationRegistry.getInstrumentation(), true);
        int origFilter = mNotificationManager.getCurrentInterruptionFilter();
        Policy origPolicy = mNotificationManager.getNotificationPolicy();
        try {
            // create a phone URI from which to receive a call
            Uri phoneUri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                    Uri.encode("+16175551212"));

            // no interruptions allowed at all
            mNotificationManager.setInterruptionFilter(INTERRUPTION_FILTER_NONE);
            assertExpectedDndState(INTERRUPTION_FILTER_NONE);
            assertFalse(mNotificationManager.matchesCallFilter(phoneUri));

            // only alarms
            mNotificationManager.setInterruptionFilter(INTERRUPTION_FILTER_ALARMS);
            assertExpectedDndState(INTERRUPTION_FILTER_ALARMS);
            assertFalse(mNotificationManager.matchesCallFilter(phoneUri));

            mNotificationManager.setNotificationPolicy(new NotificationManager.Policy(
                    PRIORITY_CATEGORY_MESSAGES, 0, 0));
            // turn on manual DND
            mNotificationManager.setInterruptionFilter(INTERRUPTION_FILTER_PRIORITY);
            assertExpectedDndState(INTERRUPTION_FILTER_PRIORITY);
            assertFalse(mNotificationManager.matchesCallFilter(phoneUri));
        } finally {
            mNotificationManager.setInterruptionFilter(origFilter);
            mNotificationManager.setNotificationPolicy(origPolicy);
        }
    }

    public void testMatchesCallFilter_someCallers() throws Exception {
        // zen mode is active; check various configurations where some calls, but not all calls,
        // are allowed
        toggleNotificationPolicyAccess(mContext.getPackageName(),
                InstrumentationRegistry.getInstrumentation(), true);
        int origFilter = mNotificationManager.getCurrentInterruptionFilter();
        Policy origPolicy = mNotificationManager.getNotificationPolicy();

        // for storing lookup URIs for deleting the contacts afterwards
        Uri aliceUri = null;
        Uri bobUri = null;
        try {
            // set up phone numbers: one starred, one regular, one unknown number
            // starred contact from whom to receive a call
            insertSingleContact(ALICE, ALICE_PHONE, ALICE_EMAIL, true);
            aliceUri = lookupContact(ALICE_PHONE);
            Uri alicePhoneUri = makePhoneUri(ALICE_PHONE);

            // non-starred contact from whom to also receive a call
            insertSingleContact(BOB, BOB_PHONE, BOB_EMAIL, false);
            bobUri = lookupContact(BOB_PHONE);
            Uri bobPhoneUri = makePhoneUri(BOB_PHONE);

            // non-contact phone URI
            Uri phoneUri = makePhoneUri("+16175555656");

            // set up: any contacts are allowed to call.
            mNotificationManager.setNotificationPolicy(new NotificationManager.Policy(
                    PRIORITY_CATEGORY_CALLS,
                    NotificationManager.Policy.PRIORITY_SENDERS_CONTACTS, 0));

            // turn on manual DND
            mNotificationManager.setInterruptionFilter(INTERRUPTION_FILTER_PRIORITY);
            assertExpectedDndState(INTERRUPTION_FILTER_PRIORITY);

            // in this case Alice and Bob should get through but not the unknown number.
            assertTrue(mNotificationManager.matchesCallFilter(alicePhoneUri));
            assertTrue(mNotificationManager.matchesCallFilter(bobPhoneUri));
            assertFalse(mNotificationManager.matchesCallFilter(phoneUri));

            // set up: only starred contacts are allowed to call.
            mNotificationManager.setNotificationPolicy(new NotificationManager.Policy(
                    PRIORITY_CATEGORY_CALLS,
                    NotificationManager.Policy.PRIORITY_SENDERS_STARRED, 0));
            assertExpectedDndState(INTERRUPTION_FILTER_PRIORITY);

            // now only Alice should be allowed to get through
            assertTrue(mNotificationManager.matchesCallFilter(alicePhoneUri));
            assertFalse(mNotificationManager.matchesCallFilter(bobPhoneUri));
            assertFalse(mNotificationManager.matchesCallFilter(phoneUri));
        } finally {
            mNotificationManager.setInterruptionFilter(origFilter);
            mNotificationManager.setNotificationPolicy(origPolicy);
            if (aliceUri != null) {
                // delete the contact
                deleteSingleContact(aliceUri);
            }
            if (bobUri != null) {
                deleteSingleContact(bobUri);
            }
        }
    }

    public void testMatchesCallFilter_repeatCallers() throws Exception {
        // if repeat callers are allowed, an unknown number calling twice should go through
        toggleNotificationPolicyAccess(mContext.getPackageName(),
                InstrumentationRegistry.getInstrumentation(), true);
        int origFilter = mNotificationManager.getCurrentInterruptionFilter();
        Policy origPolicy = mNotificationManager.getNotificationPolicy();
        long startTime = System.currentTimeMillis();
        try {
            // create phone URIs from which to receive a call; one US, one non-US,
            // both fully specified
            Uri phoneUri = makePhoneUri("+16175551212");
            Uri phoneUri2 = makePhoneUri("+81 75 350 6006");

            mNotificationManager.setNotificationPolicy(new NotificationManager.Policy(
                    PRIORITY_CATEGORY_REPEAT_CALLERS, 0, 0));
            // turn on manual DND
            mNotificationManager.setInterruptionFilter(INTERRUPTION_FILTER_PRIORITY);
            assertExpectedDndState(INTERRUPTION_FILTER_PRIORITY);

            // not repeat callers yet, so it shouldn't be allowed
            assertFalse(mNotificationManager.matchesCallFilter(phoneUri));
            assertFalse(mNotificationManager.matchesCallFilter(phoneUri2));

            // register a call from number 1, then cancel the notification, which is when
            // a call is actually recorded.
            sendNotification(1, null, R.drawable.blue, true, phoneUri);
            cancelAndPoll(1);

            // now this number should count as a repeat caller
            assertTrue(mNotificationManager.matchesCallFilter(phoneUri));
            assertFalse(mNotificationManager.matchesCallFilter(phoneUri2));

            // also, any other variants of this phone number should also count as a repeat caller
            Uri[] variants = { makePhoneUri(Uri.encode("+1-617-555-1212")),
                    makePhoneUri("+1 (617) 555-1212") };
            for (int i = 0; i < variants.length; i++) {
                assertTrue("phone variant " + variants[i] + " should still match",
                        mNotificationManager.matchesCallFilter(variants[i]));
            }

            // register call 2
            sendNotification(2, null, R.drawable.blue, true, phoneUri2);
            cancelAndPoll(2);

            // now this should be a repeat caller
            assertTrue(mNotificationManager.matchesCallFilter(phoneUri2));

            Uri[] variants2 = { makePhoneUri(Uri.encode("+81 75 350 6006")),
                    makePhoneUri("+81753506006")};
            for (int j = 0; j < variants2.length; j++) {
                assertTrue("phone variant " + variants2[j] + " should still match",
                        mNotificationManager.matchesCallFilter(variants2[j]));
            }
        } finally {
            mNotificationManager.setInterruptionFilter(origFilter);
            mNotificationManager.setNotificationPolicy(origPolicy);

            // make sure we clean up the recent call, otherwise future runs of this will fail
            // and we'll have a fake call still kicking around somewhere.
            SystemUtil.runWithShellPermissionIdentity(() ->
                    mNotificationManager.cleanUpCallersAfter(startTime));
        }
    }

    public void testMatchesCallFilter_repeatCallers_fromContact() throws Exception {
        // set up such that only repeat callers (and not any individuals) are allowed; make sure
        // that a call registered with a contact's lookup URI will return the correct info
        // when matchesCallFilter is called with their phone number
        toggleNotificationPolicyAccess(mContext.getPackageName(),
                InstrumentationRegistry.getInstrumentation(), true);
        int origFilter = mNotificationManager.getCurrentInterruptionFilter();
        Policy origPolicy = mNotificationManager.getNotificationPolicy();
        Uri aliceUri = null;
        long startTime = System.currentTimeMillis();
        try {
            mNotificationManager.setNotificationPolicy(new NotificationManager.Policy(
                    PRIORITY_CATEGORY_REPEAT_CALLERS, 0, 0));
            // turn on manual DND
            mNotificationManager.setInterruptionFilter(INTERRUPTION_FILTER_PRIORITY);
            assertExpectedDndState(INTERRUPTION_FILTER_PRIORITY);

            insertSingleContact(ALICE, ALICE_PHONE, ALICE_EMAIL, false);
            aliceUri = lookupContact(ALICE_PHONE);
            Uri alicePhoneUri = makePhoneUri(ALICE_PHONE);

            // no one has called; matchesCallFilter should return false for both URIs
            assertFalse(mNotificationManager.matchesCallFilter(aliceUri));
            assertFalse(mNotificationManager.matchesCallFilter(alicePhoneUri));

            assertTrue(aliceUri.toString()
                    .startsWith(ContactsContract.Contacts.CONTENT_LOOKUP_URI.toString()));

            // register a call from Alice via the contact lookup URI, then cancel so the call is
            // recorded accordingly.
            sendNotification(1, null, R.drawable.blue, true, aliceUri);
            // wait for contact lookup of number to finish; this can take a while because it runs
            // in the background, so give it a fair bit of time
            Thread.sleep(3000);
            cancelAndPoll(1);

            // now a phone call from Alice's phone number should match the repeat callers list
            assertTrue(mNotificationManager.matchesCallFilter(alicePhoneUri));
        } finally {
            mNotificationManager.setInterruptionFilter(origFilter);
            mNotificationManager.setNotificationPolicy(origPolicy);
            if (aliceUri != null) {
                // delete the contact
                deleteSingleContact(aliceUri);
            }

            // clean up the recorded calls
            SystemUtil.runWithShellPermissionIdentity(() ->
                    mNotificationManager.cleanUpCallersAfter(startTime));
        }
    }

    public void testRepeatCallers_repeatCallNotIntercepted_contactAfterPhone() throws Exception {
        toggleListenerAccess(true);
        Thread.sleep(500); // wait for listener to be allowed
        mListener = TestNotificationListener.getInstance();
        assertNotNull(mListener);

        // if a call is recorded with just phone number info (not a contact's uri), which may
        // happen when the same contact calls across multiple apps (or if the contact uri provided
        // is otherwise inconsistent), check for the contact's phone number
        toggleNotificationPolicyAccess(mContext.getPackageName(),
                InstrumentationRegistry.getInstrumentation(), true);
        int origFilter = mNotificationManager.getCurrentInterruptionFilter();
        Policy origPolicy = mNotificationManager.getNotificationPolicy();
        Uri aliceUri = null;
        long startTime = System.currentTimeMillis();
        try {
            mNotificationManager.setNotificationPolicy(new NotificationManager.Policy(
                    PRIORITY_CATEGORY_REPEAT_CALLERS, 0, 0));
            // turn on manual DND
            mNotificationManager.setInterruptionFilter(INTERRUPTION_FILTER_PRIORITY);
            assertExpectedDndState(INTERRUPTION_FILTER_PRIORITY);

            insertSingleContact(ALICE, ALICE_PHONE, ALICE_EMAIL, false);
            aliceUri = lookupContact(ALICE_PHONE);
            Uri alicePhoneUri = makePhoneUri(ALICE_PHONE);

            // no one has called; matchesCallFilter should return false for both URIs
            assertFalse(mNotificationManager.matchesCallFilter(aliceUri));
            assertFalse(mNotificationManager.matchesCallFilter(alicePhoneUri));

            // register a call from Alice via just the phone number
            sendNotification(1, null, R.drawable.blue, true, alicePhoneUri);
            Thread.sleep(1000); // give the listener some time to receive info

            // check that the first notification is intercepted
            StatusBarNotification sbn = findPostedNotification(1, false);
            assertNotNull(sbn);
            assertTrue(mListener.mIntercepted.containsKey(sbn.getKey()));
            assertTrue(mListener.mIntercepted.get(sbn.getKey()));  // should be intercepted

            // cancel first notification
            cancelAndPoll(1);

            // now send a call with only Alice's contact Uri as the info
            // Note that this is a test of the repeat caller check, not matchesCallFilter itself
            sendNotification(2, null, R.drawable.blue, true, aliceUri);
            // wait for contact lookup, which may take a while
            Thread.sleep(3000);

            // now check that the second notification is not intercepted
            StatusBarNotification sbn2 = findPostedNotification(2, true);
            assertTrue(mListener.mIntercepted.containsKey(sbn2.getKey()));
            assertFalse(mListener.mIntercepted.get(sbn2.getKey()));  // should not be intercepted

            // cancel second notification
            cancelAndPoll(2);
        } finally {
            mNotificationManager.setInterruptionFilter(origFilter);
            mNotificationManager.setNotificationPolicy(origPolicy);
            if (aliceUri != null) {
                // delete the contact
                deleteSingleContact(aliceUri);
            }

            // clean up the recorded calls
            SystemUtil.runWithShellPermissionIdentity(() ->
                    mNotificationManager.cleanUpCallersAfter(startTime));
        }
    }

    public void testMatchesCallFilter_allCallers() throws Exception {
        // allow all callers
        toggleNotificationPolicyAccess(mContext.getPackageName(),
                InstrumentationRegistry.getInstrumentation(), true);
        int origFilter = mNotificationManager.getCurrentInterruptionFilter();
        Policy origPolicy = mNotificationManager.getNotificationPolicy();
        Uri aliceUri = null;  // for deletion after the test is done
        try {
            NotificationManager.Policy currPolicy = mNotificationManager.getNotificationPolicy();
            NotificationManager.Policy newPolicy = new NotificationManager.Policy(
                    NotificationManager.Policy.PRIORITY_CATEGORY_CALLS
                            | PRIORITY_CATEGORY_REPEAT_CALLERS,
                    NotificationManager.Policy.PRIORITY_SENDERS_ANY,
                    currPolicy.priorityMessageSenders,
                    currPolicy.suppressedVisualEffects);
            mNotificationManager.setNotificationPolicy(newPolicy);
            mNotificationManager.setInterruptionFilter(INTERRUPTION_FILTER_PRIORITY);
            assertExpectedDndState(INTERRUPTION_FILTER_PRIORITY);

            insertSingleContact(ALICE, ALICE_PHONE, ALICE_EMAIL, false);
            aliceUri = lookupContact(ALICE_PHONE);

            Uri alicePhoneUri = makePhoneUri(ALICE_PHONE);
            assertTrue(mNotificationManager.matchesCallFilter(alicePhoneUri));
        } finally {
            mNotificationManager.setInterruptionFilter(origFilter);
            mNotificationManager.setNotificationPolicy(origPolicy);
            if (aliceUri != null) {
                // delete the contact
                deleteSingleContact(aliceUri);
            }
        }
    }

    /* Confirm that the optional methods of TestNotificationListener still exist and
     * don't fail. */
    public void testNotificationListenerMethods() {
        NotificationListenerService listener = new TestNotificationListener();
        listener.onListenerConnected();

        listener.onSilentStatusBarIconsVisibilityChanged(false);

        listener.onNotificationPosted(null);
        listener.onNotificationPosted(null, null);

        listener.onNotificationRemoved(null);
        listener.onNotificationRemoved(null, null);

        listener.onNotificationChannelGroupModified("", UserHandle.CURRENT, null,
                NotificationListenerService.NOTIFICATION_CHANNEL_OR_GROUP_ADDED);
        listener.onNotificationChannelModified("", UserHandle.CURRENT, null,
                NotificationListenerService.NOTIFICATION_CHANNEL_OR_GROUP_ADDED);

        listener.onListenerDisconnected();
    }

    private void performNotificationProviderAction(@NonNull String action) {
        // Create an intent to launch an activity which just posts or cancels notifications
        Intent activityIntent = new Intent();
        activityIntent.setClassName(NOTIFICATIONPROVIDER, RICH_NOTIFICATION_ACTIVITY);
        activityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        activityIntent.putExtra("action", action);
        mContext.startActivity(activityIntent);
    }

    public void testNotificationUriPermissionsGranted() throws Exception {
        Uri background7Uri = Uri.parse(
                "content://com.android.test.notificationprovider.provider/background7.png");
        Uri background8Uri = Uri.parse(
                "content://com.android.test.notificationprovider.provider/background8.png");

        toggleListenerAccess(true);
        Thread.sleep(500); // wait for listener to be allowed

        mListener = TestNotificationListener.getInstance();
        assertNotNull(mListener);

        try {
            // Post #7
            performNotificationProviderAction("send-7");

            assertEquals(background7Uri, getNotificationBackgroundImageUri(7));
            assertNotificationCancelled(8, true);
            assertAccessible(background7Uri);
            assertInaccessible(background8Uri);

            // Post #8
            performNotificationProviderAction("send-8");

            assertEquals(background7Uri, getNotificationBackgroundImageUri(7));
            assertEquals(background8Uri, getNotificationBackgroundImageUri(8));
            assertAccessible(background7Uri);
            assertAccessible(background8Uri);

            // Cancel #7
            performNotificationProviderAction("cancel-7");

            assertNotificationCancelled(7, true);
            assertEquals(background8Uri, getNotificationBackgroundImageUri(8));
            assertInaccessible(background7Uri);
            assertAccessible(background8Uri);

            // Cancel #8
            performNotificationProviderAction("cancel-8");

            assertNotificationCancelled(7, true);
            assertNotificationCancelled(8, true);
            assertInaccessible(background7Uri);
            assertInaccessible(background8Uri);

        } finally {
            // Clean up -- reset any remaining notifications
            performNotificationProviderAction("reset");
            Thread.sleep(500);
        }
    }

    public void testNotificationUriPermissionsGrantedToNewListeners() throws Exception {
        Uri background7Uri = Uri.parse(
                "content://com.android.test.notificationprovider.provider/background7.png");

        try {
            // Post #7
            performNotificationProviderAction("send-7");

            Thread.sleep(500);
            // Don't have access the notification yet, but we can test the URI
            assertInaccessible(background7Uri);

            toggleListenerAccess(true);
            Thread.sleep(500); // wait for listener to be allowed

            mListener = TestNotificationListener.getInstance();
            assertNotNull(mListener);

            assertEquals(background7Uri, getNotificationBackgroundImageUri(7));
            assertAccessible(background7Uri);

        } finally {
            // Clean Up -- Cancel #7
            performNotificationProviderAction("cancel-7");
            Thread.sleep(500);
        }
    }

    public void testNotificationUriPermissionsRevokedFromRemovedListeners() throws Exception {
        Uri background7Uri = Uri.parse(
                "content://com.android.test.notificationprovider.provider/background7.png");

        toggleListenerAccess(true);
        Thread.sleep(500); // wait for listener to be allowed

        try {
            // Post #7
            performNotificationProviderAction("send-7");

            mListener = TestNotificationListener.getInstance();
            assertNotNull(mListener);

            assertEquals(background7Uri, getNotificationBackgroundImageUri(7));
            assertAccessible(background7Uri);

            // Remove the listener to ensure permissions get revoked
            toggleListenerAccess(false);
            Thread.sleep(500); // wait for listener to be disabled

            assertInaccessible(background7Uri);

        } finally {
            // Clean Up -- Cancel #7
            performNotificationProviderAction("cancel-7");
            Thread.sleep(500);
        }
    }

    private class NotificationListenerConnection implements ServiceConnection {
        private final Semaphore mSemaphore = new Semaphore(0);

        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            mNotificationUriAccessService = INotificationUriAccessService.Stub.asInterface(service);
            mSemaphore.release();
        }

        @Override
        public void onServiceDisconnected(ComponentName className) {
            mNotificationUriAccessService = null;
        }

        public void waitForService() {
            try {
                if (mSemaphore.tryAcquire(5, TimeUnit.SECONDS)) {
                    return;
                }
            } catch (InterruptedException e) {
            }
            fail("failed to connec to service");
        }
    }

    public void testNotificationUriPermissionsRevokedOnlyFromRemovedListeners() throws Exception {
        Uri background7Uri = Uri.parse(
                "content://com.android.test.notificationprovider.provider/background7.png");

        // Connect to a service in the NotificationListener app which allows us to validate URI
        // permissions granted to a second app, so that we show that permissions aren't being
        // revoked too broadly.
        final Intent intent = new Intent();
        intent.setComponent(new ComponentName("com.android.test.notificationlistener",
                "com.android.test.notificationlistener.NotificationUriAccessService"));
        NotificationListenerConnection connection = new NotificationListenerConnection();
        mContext.bindService(intent, connection, Context.BIND_AUTO_CREATE);
        connection.waitForService();

        // Before starting the test, make sure the service works, that there is no listener, and
        // that the URI starts inaccessible to that process.
        mNotificationUriAccessService.ensureNotificationListenerServiceConnected(false);
        assertFalse(mNotificationUriAccessService.isFileUriAccessible(background7Uri));

        // Give the NotificationListener app access to notifications, and validate that.
        toggleExternalListenerAccess(new ComponentName("com.android.test.notificationlistener",
                "com.android.test.notificationlistener.TestNotificationListener"), true);
        Thread.sleep(500);
        mNotificationUriAccessService.ensureNotificationListenerServiceConnected(true);
        assertFalse(mNotificationUriAccessService.isFileUriAccessible(background7Uri));

        // Give the test app access to notifications, and get that listener
        toggleListenerAccess(true);
        Thread.sleep(500); // wait for listener to be allowed
        mListener = TestNotificationListener.getInstance();
        assertNotNull(mListener);

        try {
            try {
                // Post #7
                performNotificationProviderAction("send-7");

                // Check that both the test app (this code) and the external app have URI access.
                assertEquals(background7Uri, getNotificationBackgroundImageUri(7));
                assertAccessible(background7Uri);
                assertTrue(mNotificationUriAccessService.isFileUriAccessible(background7Uri));

                // Remove the listener to ensure permissions get revoked
                toggleListenerAccess(false);
                Thread.sleep(500); // wait for listener to be disabled

                // Ensure that revoking listener access to this one app does not effect the other.
                assertInaccessible(background7Uri);
                assertTrue(mNotificationUriAccessService.isFileUriAccessible(background7Uri));

            } finally {
                // Clean Up -- Cancel #7
                performNotificationProviderAction("cancel-7");
                Thread.sleep(500);
            }

            // Finally, cancelling the permission must still revoke those other permissions.
            assertFalse(mNotificationUriAccessService.isFileUriAccessible(background7Uri));

        } finally {
            // Clean Up -- Make sure the external listener is has access revoked
            toggleExternalListenerAccess(new ComponentName("com.android.test.notificationlistener",
                    "com.android.test.notificationlistener.TestNotificationListener"), false);
        }
    }

    private void assertAccessible(Uri uri)
            throws IOException {
        ContentResolver contentResolver = mContext.getContentResolver();
        try (AssetFileDescriptor fd = contentResolver.openAssetFile(uri, "r", null)) {
            assertNotNull(fd);
        } catch (SecurityException e) {
            throw new AssertionError("URI should be accessible: " + uri, e);
        }
    }

    private void assertInaccessible(Uri uri)
            throws IOException {
        ContentResolver contentResolver = mContext.getContentResolver();
        try (AssetFileDescriptor fd = contentResolver.openAssetFile(uri, "r", null)) {
            fail("URI should be inaccessible: " + uri);
        } catch (SecurityException e) {
            // pass
        }
    }

    @NonNull
    private Uri getNotificationBackgroundImageUri(int notificationId) {
        StatusBarNotification sbn = findPostedNotification(notificationId, true);
        assertNotNull(sbn);
        String imageUriString = sbn.getNotification().extras
                .getString(Notification.EXTRA_BACKGROUND_IMAGE_URI);
        assertNotNull(imageUriString);
        return Uri.parse(imageUriString);
    }

    private <T> T uncheck(ThrowingSupplier<T> supplier) {
        try {
            return supplier.get();
        } catch (Exception e) {
            throw new CompletionException(e);
        }
    }

    public void testNotificationListener_setNotificationsShown() throws Exception {
        toggleListenerAccess(true);
        Thread.sleep(500); // wait for listener to be allowed

        mListener = TestNotificationListener.getInstance();
        assertNotNull(mListener);
        final int notificationId1 = 1003;
        final int notificationId2 = 1004;

        sendNotification(notificationId1, R.drawable.black);
        sendNotification(notificationId2, R.drawable.black);
        Thread.sleep(500); // wait for notification listener to receive notification

        StatusBarNotification sbn1 = findPostedNotification(notificationId1, false);
        StatusBarNotification sbn2 = findPostedNotification(notificationId2, false);
        mListener.setNotificationsShown(new String[]{sbn1.getKey()});

        toggleListenerAccess(false);
        Thread.sleep(500); // wait for listener to be disallowed
        try {
            mListener.setNotificationsShown(new String[]{sbn2.getKey()});
            fail("Should not be able to set shown if listener access isn't granted");
        } catch (SecurityException e) {
            // expected
        }
    }

    public void testNotificationListener_getNotificationChannels() throws Exception {
        toggleListenerAccess(true);
        Thread.sleep(500); // wait for listener to be allowed

        mListener = TestNotificationListener.getInstance();
        assertNotNull(mListener);

        try {
            mListener.getNotificationChannels(mContext.getPackageName(), UserHandle.CURRENT);
            fail("Shouldn't be able get channels without CompanionDeviceManager#getAssociations()");
        } catch (SecurityException e) {
            // expected
        }
    }

    public void testNotificationListener_getNotificationChannelGroups() throws Exception {
        toggleListenerAccess(true);
        Thread.sleep(500); // wait for listener to be allowed

        mListener = TestNotificationListener.getInstance();
        assertNotNull(mListener);
        try {
            mListener.getNotificationChannelGroups(mContext.getPackageName(), UserHandle.CURRENT);
            fail("Should not be able get groups without CompanionDeviceManager#getAssociations()");
        } catch (SecurityException e) {
            // expected
        }
    }

    public void testNotificationListener_updateNotificationChannel() throws Exception {
        toggleListenerAccess(true);
        Thread.sleep(500); // wait for listener to be allowed

        mListener = TestNotificationListener.getInstance();
        assertNotNull(mListener);

        NotificationChannel channel = new NotificationChannel(
                NOTIFICATION_CHANNEL_ID, "name", IMPORTANCE_DEFAULT);
        try {
            mListener.updateNotificationChannel(mContext.getPackageName(), UserHandle.CURRENT,
                    channel);
            fail("Shouldn't be able to update channel without "
                    + "CompanionDeviceManager#getAssociations()");
        } catch (SecurityException e) {
            // expected
        }
    }

    public void testNotificationListener_getActiveNotifications() throws Exception {
        toggleListenerAccess(true);
        Thread.sleep(500); // wait for listener to be allowed

        mListener = TestNotificationListener.getInstance();
        assertNotNull(mListener);
        final int notificationId1 = 1001;
        final int notificationId2 = 1002;

        sendNotification(notificationId1, R.drawable.black);
        sendNotification(notificationId2, R.drawable.black);
        Thread.sleep(500); // wait for notification listener to receive notification

        StatusBarNotification sbn1 = findPostedNotification(notificationId1, false);
        StatusBarNotification sbn2 = findPostedNotification(notificationId2, false);
        StatusBarNotification[] notifs =
                mListener.getActiveNotifications(new String[]{sbn2.getKey(), sbn1.getKey()});
        assertEquals(sbn2.getKey(), notifs[0].getKey());
        assertEquals(sbn2.getId(), notifs[0].getId());
        assertEquals(sbn2.getPackageName(), notifs[0].getPackageName());

        assertEquals(sbn1.getKey(), notifs[1].getKey());
        assertEquals(sbn1.getId(), notifs[1].getId());
        assertEquals(sbn1.getPackageName(), notifs[1].getPackageName());
    }


    public void testNotificationListener_getCurrentRanking() throws Exception {
        toggleListenerAccess(true);
        Thread.sleep(500); // wait for listener to be allowed

        mListener = TestNotificationListener.getInstance();
        assertNotNull(mListener);

        sendNotification(1, R.drawable.black);
        Thread.sleep(500); // wait for notification listener to receive notification

        assertEquals(mListener.mRankingMap, mListener.getCurrentRanking());
    }

    public void testNotificationListener_cancelNotifications() throws Exception {
        toggleListenerAccess(true);
        Thread.sleep(500); // wait for listener to be allowed

        mListener = TestNotificationListener.getInstance();
        assertNotNull(mListener);
        final int notificationId = 1006;

        sendNotification(notificationId, R.drawable.black);
        Thread.sleep(500); // wait for notification listener to receive notification

        StatusBarNotification sbn = findPostedNotification(notificationId, false);

        mListener.cancelNotification(sbn.getPackageName(), sbn.getTag(), sbn.getId());
        if (mContext.getApplicationInfo().targetSdkVersion >= Build.VERSION_CODES.LOLLIPOP) {
            if (!checkNotificationExistence(notificationId, /*shouldExist=*/ true)) {
                fail("Notification shouldn't have been cancelled. "
                        + "cancelNotification(String, String, int) shouldn't cancel notif for L+");
            }
        } else {
            // Tested in LegacyNotificationManager20Test
            if (!checkNotificationExistence(notificationId, /*shouldExist=*/ false)) {
                fail("Notification should have been cancelled for targetSdk below L.  targetSdk="
                        + mContext.getApplicationInfo().targetSdkVersion);
            }
        }

        mListener.cancelNotifications(new String[]{sbn.getKey()});
        if (getCancellationReason(sbn.getKey())
                != NotificationListenerService.REASON_LISTENER_CANCEL) {
            fail("Failed to cancel notification id=" + notificationId);
        }
    }

    public void testNotificationAssistant_cancelNotifications() throws Exception {
        toggleAssistantAccess(true);
        Thread.sleep(500); // wait for assistant to be allowed

        mAssistant = TestNotificationAssistant.getInstance();
        assertNotNull(mAssistant);
        final int notificationId = 1006;

        sendNotification(notificationId, R.drawable.black);
        Thread.sleep(500); // wait for notification listener to receive notification

        StatusBarNotification sbn = findPostedNotification(notificationId, false);

        mAssistant.cancelNotifications(new String[]{sbn.getKey()});
        int gotReason = getAssistantCancellationReason(sbn.getKey());
        if (gotReason != NotificationListenerService.REASON_ASSISTANT_CANCEL) {
            fail("Failed cancellation from assistant, notification id=" + notificationId
                    + "; got reason=" + gotReason);
        }
    }

    public void testNotificationManagerPolicy_priorityCategoriesToString() {
        String zeroString = NotificationManager.Policy.priorityCategoriesToString(0);
        assertEquals("priorityCategories of 0 produces empty string", "", zeroString);

        String oneString = NotificationManager.Policy.priorityCategoriesToString(1);
        assertNotNull("priorityCategories of 1 returns a string", oneString);
        boolean lengthGreaterThanZero = oneString.length() > 0;
        assertTrue("priorityCategories of 1 returns a string with length greater than 0",
                lengthGreaterThanZero);

        String badNumberString = NotificationManager.Policy.priorityCategoriesToString(1234567);
        assertNotNull("priorityCategories with a non-relevant int returns a string",
                badNumberString);
    }

    public void testNotificationManagerPolicy_prioritySendersToString() {
        String zeroString = NotificationManager.Policy.prioritySendersToString(0);
        assertNotNull("prioritySenders of 1 returns a string", zeroString);
        boolean lengthGreaterThanZero = zeroString.length() > 0;
        assertTrue("prioritySenders of 1 returns a string with length greater than 0",
                lengthGreaterThanZero);

        String badNumberString = NotificationManager.Policy.prioritySendersToString(1234567);
        assertNotNull("prioritySenders with a non-relevant int returns a string", badNumberString);
    }

    public void testNotificationManagerPolicy_suppressedEffectsToString() {
        String zeroString = NotificationManager.Policy.suppressedEffectsToString(0);
        assertEquals("suppressedEffects of 0 produces empty string", "", zeroString);

        String oneString = NotificationManager.Policy.suppressedEffectsToString(1);
        assertNotNull("suppressedEffects of 1 returns a string", oneString);
        boolean lengthGreaterThanZero = oneString.length() > 0;
        assertTrue("suppressedEffects of 1 returns a string with length greater than 0",
                lengthGreaterThanZero);

        String badNumberString = NotificationManager.Policy.suppressedEffectsToString(1234567);
        assertNotNull("suppressedEffects with a non-relevant int returns a string",
                badNumberString);
    }

    public void testOriginalChannelImportance() {
        NotificationChannel channel = new NotificationChannel(mId, "my channel", IMPORTANCE_HIGH);

        mNotificationManager.createNotificationChannel(channel);

        NotificationChannel actual = mNotificationManager.getNotificationChannel(channel.getId());
        assertEquals(IMPORTANCE_HIGH, actual.getImportance());
        assertEquals(IMPORTANCE_HIGH, actual.getOriginalImportance());

        // Apps are allowed to downgrade channel importance if the user has not changed any
        // fields on this channel yet.
        channel.setImportance(IMPORTANCE_DEFAULT);
        mNotificationManager.createNotificationChannel(channel);

        actual = mNotificationManager.getNotificationChannel(channel.getId());
        assertEquals(IMPORTANCE_DEFAULT, actual.getImportance());
        assertEquals(IMPORTANCE_HIGH, actual.getOriginalImportance());
    }

    public void testCreateConversationChannel() {
        final NotificationChannel channel =
                new NotificationChannel(mId, "Messages", IMPORTANCE_DEFAULT);

        String conversationId = "person a";

        final NotificationChannel conversationChannel =
                new NotificationChannel(mId + "child",
                        "Messages from " + conversationId, IMPORTANCE_DEFAULT);
        conversationChannel.setConversationId(channel.getId(), conversationId);

        mNotificationManager.createNotificationChannel(channel);
        mNotificationManager.createNotificationChannel(conversationChannel);

        compareChannels(conversationChannel,
                mNotificationManager.getNotificationChannel(channel.getId(), conversationId));
    }

    public void testConversationRankingFields() throws Exception {
        toggleListenerAccess(true);
        Thread.sleep(500); // wait for listener to be allowed

        mListener = TestNotificationListener.getInstance();
        assertNotNull(mListener);

        createDynamicShortcut();
        mNotificationManager.notify(177, getConversationNotification().build());

        if (!checkNotificationExistence(177, /*shouldExist=*/ true)) {
            fail("couldn't find posted notification id=" + 177);
        }
        Thread.sleep(500); // wait for notification listener to receive notification
        assertEquals(1, mListener.mPosted.size());

        NotificationListenerService.RankingMap rankingMap = mListener.mRankingMap;
        NotificationListenerService.Ranking outRanking = new NotificationListenerService.Ranking();
        for (String key : rankingMap.getOrderedKeys()) {
            if (key.contains(mListener.getPackageName())) {
                rankingMap.getRanking(key, outRanking);
                assertTrue(outRanking.isConversation());
                assertEquals(SHARE_SHORTCUT_ID, outRanking.getConversationShortcutInfo().getId());
            }
        }
    }

    public void testDemoteConversationChannel() {
        final NotificationChannel channel =
                new NotificationChannel(mId, "Messages", IMPORTANCE_DEFAULT);

        String conversationId = "person a";

        final NotificationChannel conversationChannel =
                new NotificationChannel(mId + "child",
                        "Messages from " + conversationId, IMPORTANCE_DEFAULT);
        conversationChannel.setConversationId(channel.getId(), conversationId);

        mNotificationManager.createNotificationChannel(channel);
        mNotificationManager.createNotificationChannel(conversationChannel);

        conversationChannel.setDemoted(true);

        SystemUtil.runWithShellPermissionIdentity(() ->
                mNotificationManager.updateNotificationChannel(
                        mContext.getPackageName(), android.os.Process.myUid(), channel));

        assertEquals(false, mNotificationManager.getNotificationChannel(
                channel.getId(), conversationId).isDemoted());
    }

    public void testDeleteConversationChannels() throws Exception {
        setUpNotifListener();

        createDynamicShortcut();

        final NotificationChannel channel =
                new NotificationChannel(mId, "Messages", IMPORTANCE_DEFAULT);

        final NotificationChannel conversationChannel =
                new NotificationChannel(mId + "child",
                        "Messages from " + SHARE_SHORTCUT_ID, IMPORTANCE_DEFAULT);
        conversationChannel.setConversationId(channel.getId(), SHARE_SHORTCUT_ID);

        mNotificationManager.createNotificationChannel(channel);
        mNotificationManager.createNotificationChannel(conversationChannel);

        mNotificationManager.notify(177, getConversationNotification().build());

        if (!checkNotificationExistence(177, /*shouldExist=*/ true)) {
            fail("couldn't find posted notification id=" + 177);
        }
        Thread.sleep(500); // wait for notification listener to receive notification
        assertEquals(1, mListener.mPosted.size());

        deleteShortcuts();

        Thread.sleep(300); // wait for deletion to propagate

        assertFalse(mNotificationManager.getNotificationChannel(channel.getId(),
                conversationChannel.getConversationId()).isConversation());

    }

    /**
     * This method verifies that an app can't bypass background restrictions by retrieving their own
     * notification and triggering it.
     */
    @AsbSecurityTest(cveBugId = 185388103)
    public void testActivityStartFromRetrievedNotification_isBlocked() throws Exception {
        deactivateGracePeriod();
        EventCallback callback = new EventCallback();
        int notificationId = 6007;

        // Post notification and fire its pending intent
        sendTrampolineMessage(TRAMPOLINE_SERVICE_API_30, MESSAGE_SERVICE_NOTIFICATION,
                notificationId, callback);
        PollingCheck.waitFor(TIMEOUT_MS, () -> uncheck(() -> {
            sendTrampolineMessage(TRAMPOLINE_SERVICE, MESSAGE_CLICK_NOTIFICATION, notificationId,
                    callback);
            // timeoutMs = 1ms below because surrounding waitFor already handles retry & timeout.
            return callback.waitFor(EventCallback.NOTIFICATION_CLICKED, /* timeoutMs */ 1);
        }));

        assertFalse("Activity start should have been blocked",
                callback.waitFor(EventCallback.ACTIVITY_STARTED, TIMEOUT_MS));
    }

    public void testActivityStartOnBroadcastTrampoline_isBlocked() throws Exception {
        deactivateGracePeriod();
        setUpNotifListener();
        mListener.addTestPackage(TRAMPOLINE_APP);
        EventCallback callback = new EventCallback();
        int notificationId = 6001;

        // Post notification and fire its pending intent
        sendTrampolineMessage(TRAMPOLINE_SERVICE, MESSAGE_BROADCAST_NOTIFICATION, notificationId,
                callback);
        StatusBarNotification statusBarNotification = findPostedNotification(notificationId, true);
        assertNotNull("Notification not posted on time", statusBarNotification);
        statusBarNotification.getNotification().contentIntent.send();

        assertTrue("Broadcast not received on time",
                callback.waitFor(EventCallback.BROADCAST_RECEIVED, TIMEOUT_LONG_MS));
        assertFalse("Activity start should have been blocked",
                callback.waitFor(EventCallback.ACTIVITY_STARTED, TIMEOUT_MS));
    }

    public void testActivityStartOnServiceTrampoline_isBlocked() throws Exception {
        deactivateGracePeriod();
        setUpNotifListener();
        mListener.addTestPackage(TRAMPOLINE_APP);
        EventCallback callback = new EventCallback();
        int notificationId = 6002;

        // Post notification and fire its pending intent
        sendTrampolineMessage(TRAMPOLINE_SERVICE, MESSAGE_SERVICE_NOTIFICATION, notificationId,
                callback);
        StatusBarNotification statusBarNotification = findPostedNotification(notificationId, true);
        assertNotNull("Notification not posted on time", statusBarNotification);
        statusBarNotification.getNotification().contentIntent.send();

        assertTrue("Service not started on time",
                callback.waitFor(EventCallback.SERVICE_STARTED, TIMEOUT_MS));
        assertFalse("Activity start should have been blocked",
                callback.waitFor(EventCallback.ACTIVITY_STARTED, TIMEOUT_MS));
    }

    public void testActivityStartOnBroadcastTrampoline_whenApi30_isAllowed() throws Exception {
        deactivateGracePeriod();
        setUpNotifListener();
        mListener.addTestPackage(TRAMPOLINE_APP_API_30);
        EventCallback callback = new EventCallback();
        int notificationId = 6003;

        // Post notification and fire its pending intent
        sendTrampolineMessage(TRAMPOLINE_SERVICE_API_30, MESSAGE_BROADCAST_NOTIFICATION,
                notificationId, callback);
        StatusBarNotification statusBarNotification = findPostedNotification(notificationId, true);
        assertNotNull("Notification not posted on time", statusBarNotification);
        statusBarNotification.getNotification().contentIntent.send();

        assertTrue("Broadcast not received on time",
                callback.waitFor(EventCallback.BROADCAST_RECEIVED, TIMEOUT_LONG_MS));
        assertTrue("Activity not started",
                callback.waitFor(EventCallback.ACTIVITY_STARTED, TIMEOUT_MS));
    }

    public void testActivityStartOnServiceTrampoline_whenApi30_isAllowed() throws Exception {
        deactivateGracePeriod();
        setUpNotifListener();
        mListener.addTestPackage(TRAMPOLINE_APP_API_30);
        EventCallback callback = new EventCallback();
        int notificationId = 6004;

        // Post notification and fire its pending intent
        sendTrampolineMessage(TRAMPOLINE_SERVICE_API_30, MESSAGE_SERVICE_NOTIFICATION,
                notificationId, callback);
        StatusBarNotification statusBarNotification = findPostedNotification(notificationId, true);
        assertNotNull("Notification not posted on time", statusBarNotification);
        statusBarNotification.getNotification().contentIntent.send();

        assertTrue("Service not started on time",
                callback.waitFor(EventCallback.SERVICE_STARTED, TIMEOUT_MS));
        assertTrue("Activity not started",
                callback.waitFor(EventCallback.ACTIVITY_STARTED, TIMEOUT_MS));
    }

    public void testActivityStartOnBroadcastTrampoline_whenDefaultBrowser_isBlocked()
            throws Exception {
        deactivateGracePeriod();
        setDefaultBrowser(TRAMPOLINE_APP);
        setUpNotifListener();
        mListener.addTestPackage(TRAMPOLINE_APP);
        EventCallback callback = new EventCallback();
        int notificationId = 6005;

        // Post notification and fire its pending intent
        sendTrampolineMessage(TRAMPOLINE_SERVICE, MESSAGE_BROADCAST_NOTIFICATION, notificationId,
                callback);
        StatusBarNotification statusBarNotification = findPostedNotification(notificationId, true);
        assertNotNull("Notification not posted on time", statusBarNotification);
        statusBarNotification.getNotification().contentIntent.send();

        assertTrue("Broadcast not received on time",
                callback.waitFor(EventCallback.BROADCAST_RECEIVED, TIMEOUT_LONG_MS));
        assertFalse("Activity started",
                callback.waitFor(EventCallback.ACTIVITY_STARTED, TIMEOUT_MS));
    }

    public void testActivityStartOnBroadcastTrampoline_whenDefaultBrowserApi32_isAllowed()
            throws Exception {
        deactivateGracePeriod();
        setDefaultBrowser(TRAMPOLINE_APP_API_32);
        setUpNotifListener();
        mListener.addTestPackage(TRAMPOLINE_APP_API_32);
        EventCallback callback = new EventCallback();
        int notificationId = 6005;

        // Post notification and fire its pending intent
        sendTrampolineMessage(TRAMPOLINE_SERVICE_API_32, MESSAGE_BROADCAST_NOTIFICATION,
                notificationId, callback);
        StatusBarNotification statusBarNotification = findPostedNotification(notificationId, true);
        assertNotNull("Notification not posted on time", statusBarNotification);
        statusBarNotification.getNotification().contentIntent.send();

        assertTrue("Broadcast not received on time",
                callback.waitFor(EventCallback.BROADCAST_RECEIVED, TIMEOUT_LONG_MS));
        assertTrue("Activity not started",
                callback.waitFor(EventCallback.ACTIVITY_STARTED, TIMEOUT_MS));
    }

    public void testActivityStartOnServiceTrampoline_whenDefaultBrowser_isBlocked()
            throws Exception {
        deactivateGracePeriod();
        setDefaultBrowser(TRAMPOLINE_APP);
        setUpNotifListener();
        mListener.addTestPackage(TRAMPOLINE_APP);
        EventCallback callback = new EventCallback();
        int notificationId = 6006;

        // Post notification and fire its pending intent
        sendTrampolineMessage(TRAMPOLINE_SERVICE, MESSAGE_SERVICE_NOTIFICATION, notificationId,
                callback);
        StatusBarNotification statusBarNotification = findPostedNotification(notificationId, true);
        assertNotNull("Notification not posted on time", statusBarNotification);
        statusBarNotification.getNotification().contentIntent.send();

        assertTrue("Service not started on time",
                callback.waitFor(EventCallback.SERVICE_STARTED, TIMEOUT_MS));
        assertFalse("Activity started",
                callback.waitFor(EventCallback.ACTIVITY_STARTED, TIMEOUT_MS));
    }

    public void testActivityStartOnServiceTrampoline_whenDefaultBrowserApi32_isAllowed()
            throws Exception {
        deactivateGracePeriod();
        setDefaultBrowser(TRAMPOLINE_APP_API_32);
        setUpNotifListener();
        mListener.addTestPackage(TRAMPOLINE_APP_API_32);
        EventCallback callback = new EventCallback();
        int notificationId = 6006;

        // Post notification and fire its pending intent
        sendTrampolineMessage(TRAMPOLINE_SERVICE_API_32, MESSAGE_SERVICE_NOTIFICATION,
                notificationId, callback);
        StatusBarNotification statusBarNotification = findPostedNotification(notificationId, true);
        assertNotNull("Notification not posted on time", statusBarNotification);
        statusBarNotification.getNotification().contentIntent.send();

        assertTrue("Service not started on time",
                callback.waitFor(EventCallback.SERVICE_STARTED, TIMEOUT_MS));
        assertTrue("Activity not started",
                callback.waitFor(EventCallback.ACTIVITY_STARTED, TIMEOUT_MS));
    }

    public void testGrantRevokeNotificationManagerApis_works() {
        SystemUtil.runWithShellPermissionIdentity(() -> {
            ComponentName componentName = TestNotificationListener.getComponentName();
            mNotificationManager.setNotificationListenerAccessGranted(
                    componentName, true, true);

            assertThat(
                    mNotificationManager.getEnabledNotificationListeners(),
                    hasItem(componentName));

            mNotificationManager.setNotificationListenerAccessGranted(
                    componentName, false, false);

            assertThat(
                    "Non-user-set changes should not override user-set",
                    mNotificationManager.getEnabledNotificationListeners(),
                    hasItem(componentName));
        });
    }

    public void testGrantRevokeNotificationManagerApis_exclusiveToPermissionController() {
        List<PackageInfo> allPackages = mPackageManager.getInstalledPackages(
                PackageManager.MATCH_DISABLED_COMPONENTS
                        | PackageManager.MATCH_DISABLED_UNTIL_USED_COMPONENTS);
        List<String> allowedPackages = Arrays.asList(
                mPackageManager.getPermissionControllerPackageName(),
                "com.android.shell");
        for (PackageInfo pkg : allPackages) {
            if (!pkg.applicationInfo.isSystemApp()
                    && mPackageManager.checkPermission(
                    Manifest.permission.MANAGE_NOTIFICATION_LISTENERS, pkg.packageName)
                    == PackageManager.PERMISSION_GRANTED
                    && !allowedPackages.contains(pkg.packageName)) {
                fail(pkg.packageName + " can't hold "
                        + Manifest.permission.MANAGE_NOTIFICATION_LISTENERS);
            }
        }
    }

    public void testChannelDeletion_cancelReason() throws Exception {
        setUpNotifListener();

        sendNotification(566, R.drawable.black);

        Thread.sleep(500); // wait for notification listener to receive notification
        assertEquals(1, mListener.mPosted.size());
        String key = mListener.mPosted.get(0).getKey();

        mNotificationManager.deleteNotificationChannel(NOTIFICATION_CHANNEL_ID);

        assertEquals(NotificationListenerService.REASON_CHANNEL_REMOVED,
                getCancellationReason(key));
    }

    public void testMediaStyleRemotePlayback_noPermission() throws Exception {
        int id = 99;
        final String deviceName = "device name";
        final int deviceIcon = 123;
        final PendingIntent deviceIntent = getPendingIntent();
        final Notification notification =
                new Notification.Builder(mContext, NOTIFICATION_CHANNEL_ID)
                        .setSmallIcon(R.drawable.black)
                        .setStyle(new Notification.MediaStyle()
                                .setRemotePlaybackInfo(deviceName, deviceIcon, deviceIntent))
                        .build();
        mNotificationManager.notify(id, notification);

        StatusBarNotification sbn = findPostedNotification(id, false);
        assertNotNull(sbn);

        assertFalse(sbn.getNotification().extras
                .containsKey(Notification.EXTRA_MEDIA_REMOTE_DEVICE));
        assertFalse(sbn.getNotification().extras
                .containsKey(Notification.EXTRA_MEDIA_REMOTE_ICON));
        assertFalse(sbn.getNotification().extras
                .containsKey(Notification.EXTRA_MEDIA_REMOTE_INTENT));
    }

    public void testMediaStyleRemotePlayback_hasPermission() throws Exception {
        int id = 99;
        final String deviceName = "device name";
        final int deviceIcon = 123;
        final PendingIntent deviceIntent = getPendingIntent();
        final Notification notification =
                new Notification.Builder(mContext, NOTIFICATION_CHANNEL_ID)
                        .setSmallIcon(R.drawable.black)
                        .setStyle(new Notification.MediaStyle()
                                .setRemotePlaybackInfo(deviceName, deviceIcon, deviceIntent))
                        .build();

        SystemUtil.runWithShellPermissionIdentity(() -> {
            mNotificationManager.notify(id, notification);
        }, android.Manifest.permission.MEDIA_CONTENT_CONTROL);

        StatusBarNotification sbn = findPostedNotification(id, false);
        assertNotNull(sbn);
        assertEquals(deviceName, sbn.getNotification().extras
                .getString(Notification.EXTRA_MEDIA_REMOTE_DEVICE));
        assertEquals(deviceIcon, sbn.getNotification().extras
                .getInt(Notification.EXTRA_MEDIA_REMOTE_ICON));
        assertEquals(deviceIntent, sbn.getNotification().extras
                .getParcelable(Notification.EXTRA_MEDIA_REMOTE_INTENT));
    }

    public void testCustomMediaStyleRemotePlayback_noPermission() throws Exception {
        int id = 99;
        final String deviceName = "device name";
        final int deviceIcon = 123;
        final PendingIntent deviceIntent = getPendingIntent();
        final Notification notification =
                new Notification.Builder(mContext, NOTIFICATION_CHANNEL_ID)
                        .setSmallIcon(R.drawable.black)
                        .setStyle(new Notification.DecoratedMediaCustomViewStyle()
                                .setRemotePlaybackInfo(deviceName, deviceIcon, deviceIntent))
                        .build();
        mNotificationManager.notify(id, notification);

        StatusBarNotification sbn = findPostedNotification(id, false);
        assertNotNull(sbn);

        assertFalse(sbn.getNotification().extras
                .containsKey(Notification.EXTRA_MEDIA_REMOTE_DEVICE));
        assertFalse(sbn.getNotification().extras
                .containsKey(Notification.EXTRA_MEDIA_REMOTE_ICON));
        assertFalse(sbn.getNotification().extras
                .containsKey(Notification.EXTRA_MEDIA_REMOTE_INTENT));
    }

    public void testCustomMediaStyleRemotePlayback_hasPermission() throws Exception {
        int id = 99;
        final String deviceName = "device name";
        final int deviceIcon = 123;
        final PendingIntent deviceIntent = getPendingIntent();
        final Notification notification =
                new Notification.Builder(mContext, NOTIFICATION_CHANNEL_ID)
                        .setSmallIcon(R.drawable.black)
                        .setStyle(new Notification.DecoratedMediaCustomViewStyle()
                                .setRemotePlaybackInfo(deviceName, deviceIcon, deviceIntent))
                        .build();

        SystemUtil.runWithShellPermissionIdentity(() -> {
            mNotificationManager.notify(id, notification);
        }, android.Manifest.permission.MEDIA_CONTENT_CONTROL);

        StatusBarNotification sbn = findPostedNotification(id, false);
        assertNotNull(sbn);
        assertEquals(deviceName, sbn.getNotification().extras
                .getString(Notification.EXTRA_MEDIA_REMOTE_DEVICE));
        assertEquals(deviceIcon, sbn.getNotification().extras
                .getInt(Notification.EXTRA_MEDIA_REMOTE_ICON));
        assertEquals(deviceIntent, sbn.getNotification().extras
                .getParcelable(Notification.EXTRA_MEDIA_REMOTE_INTENT));
    }

    public void testNoPermission() throws Exception {
        int id = 7;
        SystemUtil.runWithShellPermissionIdentity(
                () -> mContext.getSystemService(PermissionManager.class)
                        .revokePostNotificationPermissionWithoutKillForTest(
                                mContext.getPackageName(),
                                android.os.Process.myUserHandle().getIdentifier()),
                REVOKE_POST_NOTIFICATIONS_WITHOUT_KILL,
                REVOKE_RUNTIME_PERMISSIONS);

        final Notification notification =
                new Notification.Builder(mContext, NOTIFICATION_CHANNEL_ID)
                        .setSmallIcon(R.drawable.black)
                        .build();
        mNotificationManager.notify(id, notification);

        StatusBarNotification sbn = findPostedNotification(id, false);
        assertNull(sbn);
    }

    private static class EventCallback extends Handler {
        private static final int BROADCAST_RECEIVED = 1;
        private static final int SERVICE_STARTED = 2;
        private static final int ACTIVITY_STARTED = 3;
        private static final int NOTIFICATION_CLICKED = 4;

        private final Map<Integer, CompletableFuture<Integer>> mEvents =
                Collections.synchronizedMap(new ArrayMap<>());

        private EventCallback() {
            super(Looper.getMainLooper());
        }

        @Override
        public void handleMessage(Message message) {
            mEvents.computeIfAbsent(message.what, e -> new CompletableFuture<>()).obtrudeValue(
                    message.arg1);
        }

        public boolean waitFor(int event, long timeoutMs) {
            try {
                return mEvents.computeIfAbsent(event, e -> new CompletableFuture<>()).get(timeoutMs,
                        TimeUnit.MILLISECONDS) == 0;
            } catch (InterruptedException | ExecutionException | TimeoutException e) {
                return false;
            }
        }
    }
}
