/*
 * Copyright 2022 The Android Open Source Project
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

package com.android.bluetooth.opp;

import static com.android.bluetooth.opp.BluetoothOppNotification.NOTIFICATION_ID_INBOUND_COMPLETE;
import static com.android.bluetooth.opp.BluetoothOppNotification.NOTIFICATION_ID_OUTBOUND_COMPLETE;
import static com.android.bluetooth.opp.BluetoothOppNotification.NOTIFICATION_ID_PROGRESS;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import android.app.NotificationManager;
import android.content.Context;
import android.content.ContextWrapper;
import android.database.MatrixCursor;
import android.graphics.drawable.Icon;
import android.util.Log;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.espresso.intent.Intents;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.bluetooth.BluetoothMethodProxy;
import com.android.bluetooth.R;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Objects;

@RunWith(AndroidJUnit4.class)
public class BluetoothOppNotificationTest {
    @Mock
    BluetoothMethodProxy mMethodProxy;

    Context mTargetContext;

    BluetoothOppNotification mOppNotification;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mTargetContext = spy(new ContextWrapper(
                ApplicationProvider.getApplicationContext()));
        mMethodProxy = spy(BluetoothMethodProxy.getInstance());
        BluetoothMethodProxy.setInstanceForTesting(mMethodProxy);

        InstrumentationRegistry.getInstrumentation().runOnMainSync(() ->
                mOppNotification = new BluetoothOppNotification(mTargetContext));

        Intents.init();
    }

    @After
    public void tearDown() {
        BluetoothMethodProxy.setInstanceForTesting(null);
        Intents.release();
    }

    @Test
    public void updateActiveNotification() {
        long timestamp = 10L;
        int dir = BluetoothShare.DIRECTION_INBOUND;
        int id = 0;
        long total = 200;
        long current = 100;
        int status = BluetoothShare.STATUS_RUNNING;
        int confirmation = BluetoothShare.USER_CONFIRMATION_CONFIRMED;
        int confirmationHandoverInitiated = BluetoothShare.USER_CONFIRMATION_HANDOVER_CONFIRMED;
        String destination = "AA:BB:CC:DD:EE:FF";
        NotificationManager mockNotificationManager = mock(NotificationManager.class);
        mOppNotification.mNotificationMgr = mockNotificationManager;
        MatrixCursor cursor = new MatrixCursor(new String[]{
                BluetoothShare.TIMESTAMP, BluetoothShare.DIRECTION, BluetoothShare._ID,
                BluetoothShare.TOTAL_BYTES, BluetoothShare.CURRENT_BYTES, BluetoothShare._DATA,
                BluetoothShare.FILENAME_HINT, BluetoothShare.USER_CONFIRMATION,
                BluetoothShare.DESTINATION, BluetoothShare.STATUS
        });
        cursor.addRow(new Object[]{
                timestamp, dir, id, total, current, null, null, confirmation, destination, status
        });
        cursor.addRow(new Object[]{
                timestamp + 10L, dir, id, total, current, null, null, confirmationHandoverInitiated,
                destination, status
        });
        doReturn(cursor).when(mMethodProxy).contentResolverQuery(any(),
                eq(BluetoothShare.CONTENT_URI), any(), any(), any(), any());

        mOppNotification.updateActiveNotification();

        //confirm handover case does broadcast
        verify(mTargetContext).sendBroadcast(any(), eq(Constants.HANDOVER_STATUS_PERMISSION),
                any());
        // Todo: find a better way to verify the notification
        // getContentIntent doesn't work because it requires signature permission
        verify(mockNotificationManager).notify(eq(NOTIFICATION_ID_PROGRESS), argThat(
                arg -> arg.getSmallIcon().sameAs(Icon.createWithResource(mTargetContext,
                        android.R.drawable.stat_sys_download))
        ));
    }

    @Test
    public void updateCompletedNotification_withOutBoundShare_showsNoti() {
        long timestamp = 10L;
        int status = BluetoothShare.STATUS_SUCCESS;
        int statusError = BluetoothShare.STATUS_CONNECTION_ERROR;
        int dir = BluetoothShare.DIRECTION_OUTBOUND;
        int id = 0;
        long total = 200;
        long current = 100;
        int confirmation = BluetoothShare.USER_CONFIRMATION_CONFIRMED;
        String destination = "AA:BB:CC:DD:EE:FF";
        NotificationManager mockNotificationManager = mock(NotificationManager.class);
        mOppNotification.mNotificationMgr = mockNotificationManager;
        MatrixCursor cursor = new MatrixCursor(new String[]{
                BluetoothShare.TIMESTAMP, BluetoothShare.DIRECTION, BluetoothShare._ID,
                BluetoothShare.TOTAL_BYTES, BluetoothShare.CURRENT_BYTES, BluetoothShare._DATA,
                BluetoothShare.FILENAME_HINT, BluetoothShare.USER_CONFIRMATION,
                BluetoothShare.DESTINATION, BluetoothShare.STATUS
        });
        cursor.addRow(new Object[]{
                timestamp, dir, id, total, current, null, null, confirmation, destination, status
        });
        cursor.addRow(new Object[]{
                timestamp + 10L, dir, id, total, current, null, null, confirmation,
                destination, statusError
        });
        doReturn(cursor).when(mMethodProxy).contentResolverQuery(any(),
                eq(BluetoothShare.CONTENT_URI), any(), any(), any(), any());

        mOppNotification.updateCompletedNotification();

        // Todo: find a better way to verify the notification
        // getContentIntent doesn't work because it requires signature permission
        verify(mockNotificationManager).notify(eq(NOTIFICATION_ID_OUTBOUND_COMPLETE), argThat(
                arg -> arg.getSmallIcon().sameAs(Icon.createWithResource(mTargetContext,
                        android.R.drawable.stat_sys_upload_done))
        ));
    }

    @Test
    public void updateCompletedNotification_withInBoundShare_showsNoti() {
        long timestamp = 10L;
        int status = BluetoothShare.STATUS_SUCCESS;
        int statusError = BluetoothShare.STATUS_CONNECTION_ERROR;
        int dir = BluetoothShare.DIRECTION_INBOUND;
        int id = 0;
        long total = 200;
        long current = 100;
        int confirmation = BluetoothShare.USER_CONFIRMATION_CONFIRMED;
        String destination = "AA:BB:CC:DD:EE:FF";
        NotificationManager mockNotificationManager = mock(NotificationManager.class);
        mOppNotification.mNotificationMgr = mockNotificationManager;
        MatrixCursor cursor = new MatrixCursor(new String[]{
                BluetoothShare.TIMESTAMP, BluetoothShare.DIRECTION, BluetoothShare._ID,
                BluetoothShare.TOTAL_BYTES, BluetoothShare.CURRENT_BYTES, BluetoothShare._DATA,
                BluetoothShare.FILENAME_HINT, BluetoothShare.USER_CONFIRMATION,
                BluetoothShare.DESTINATION, BluetoothShare.STATUS
        });
        cursor.addRow(new Object[]{
                timestamp, dir, id, total, current, null, null, confirmation, destination, status
        });
        cursor.addRow(new Object[]{
                timestamp + 10L, dir, id, total, current, null, null, confirmation,
                destination, statusError
        });
        doReturn(cursor).when(mMethodProxy).contentResolverQuery(any(),
                eq(BluetoothShare.CONTENT_URI), any(), any(), any(), any());

        mOppNotification.updateCompletedNotification();

        // Todo: find a better way to verify the notification
        // getContentIntent doesn't work because it requires signature permission
        verify(mockNotificationManager).notify(eq(NOTIFICATION_ID_INBOUND_COMPLETE), argThat(
                arg -> arg.getSmallIcon().sameAs(Icon.createWithResource(mTargetContext,
                            android.R.drawable.stat_sys_download_done)
        )));
    }

    @Test
    public void updateIncomingFileConfirmationNotification() {
        long timestamp = 10L;
        int dir = BluetoothShare.DIRECTION_INBOUND;
        int id = 0;
        long total = 200;
        long current = 100;
        int confirmation = BluetoothShare.USER_CONFIRMATION_PENDING;
        int status = BluetoothShare.STATUS_SUCCESS;
        String url = "content:///abc/xyz";
        String destination = "AA:BB:CC:DD:EE:FF";
        String mimeType = "text/plain";
        NotificationManager mockNotificationManager = mock(NotificationManager.class);
        mOppNotification.mNotificationMgr = mockNotificationManager;
        MatrixCursor cursor = new MatrixCursor(new String[]{
                BluetoothShare.TIMESTAMP, BluetoothShare.DIRECTION, BluetoothShare._ID,
                BluetoothShare.TOTAL_BYTES, BluetoothShare.CURRENT_BYTES, BluetoothShare._DATA,
                BluetoothShare.FILENAME_HINT, BluetoothShare.USER_CONFIRMATION, BluetoothShare.URI,
                BluetoothShare.DESTINATION, BluetoothShare.STATUS, BluetoothShare.MIMETYPE
        });
        cursor.addRow(new Object[]{
                timestamp, dir, id, total, current, null, null, confirmation, url, destination,
                status, mimeType
        });
        doReturn(cursor).when(mMethodProxy).contentResolverQuery(any(),
                eq(BluetoothShare.CONTENT_URI), any(), any(), any(), any());

        mOppNotification.updateIncomingFileConfirmNotification();

        // Todo: find a better way to verify the notification
        // getContentIntent doesn't work because it requires signature permission
        verify(mockNotificationManager).notify(eq(NOTIFICATION_ID_PROGRESS), argThat(
                arg -> arg.getSmallIcon().sameAs(Icon.createWithResource(mTargetContext,
                    R.drawable.bt_incomming_file_notification))
        ));
    }
}
