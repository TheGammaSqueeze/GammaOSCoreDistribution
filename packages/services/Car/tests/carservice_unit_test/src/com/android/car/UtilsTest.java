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

package com.android.car;

import static android.car.user.CarUserManager.USER_LIFECYCLE_EVENT_TYPE_STARTING;
import static android.car.user.CarUserManager.USER_LIFECYCLE_EVENT_TYPE_STOPPING;
import static android.car.user.CarUserManager.USER_LIFECYCLE_EVENT_TYPE_SWITCHING;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.when;

import android.car.test.mocks.AbstractExtendedMockitoTestCase.ExpectWtf;
import android.car.user.CarUserManager.UserLifecycleEvent;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Process;
import android.text.TextUtils;

import com.android.car.util.TransitionLog;
import com.android.car.util.Utils;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.UUID;

@RunWith(MockitoJUnitRunner.class)
public final class UtilsTest {

    private static final String TAG = UtilsTest.class.getSimpleName();

    private static final UserLifecycleEvent USER_STARTING_EVENT =
            new UserLifecycleEvent(USER_LIFECYCLE_EVENT_TYPE_STARTING, 111);

    @Mock
    private Context mContext;

    @Mock
    private PackageManager mPm;

    @Before
    public void setFixtures() {
        when(mContext.getPackageManager()).thenReturn(mPm);
        when(mContext.getSystemService(PackageManager.class)).thenReturn(mPm);
    }

    @Test
    public void testTransitionLogToString() {
        TransitionLog transitionLog =
                new TransitionLog("serviceName", "state1", "state2", 1623777864000L);
        String result = transitionLog.toString();

        // Should match the date pattern "MM-dd HH:mm:ss".
        assertThat(result).matches("^[01]\\d-[0-3]\\d [0-2]\\d:[0-6]\\d:[0-6]\\d\\s+.*");
        assertThat(result).contains("serviceName:");
        assertThat(result).contains("from state1 to state2");
    }

    @Test
    public void testTransitionLogToString_withExtra() {
        TransitionLog transitionLog =
                new TransitionLog("serviceName", "state1", "state2", 1623777864000L, "extra");
        String result = transitionLog.toString();

        // Should match the date pattern "MM-dd HH:mm:ss".
        assertThat(result).matches("^[01]\\d-[0-3]\\d [0-2]\\d:[0-6]\\d:[0-6]\\d\\s+.*");
        assertThat(result).contains("serviceName:");
        assertThat(result).contains("extra");
        assertThat(result).contains("from state1 to state2");
    }

    @Test
    public void testLongToBytes() {
        long longValue = 1234567890L;
        byte[] expected = new byte[] {0, 0, 0, 0, 73, -106, 2, -46};

        assertThat(Utils.longToBytes(longValue)).isEqualTo(expected);
    }

    @Test
    public void testBytesToLong() {
        byte[] bytes = new byte[] {0, 0, 0, 0, 73, -106, 2, -46};
        long expected = 1234567890L;

        assertThat(Utils.bytesToLong(bytes)).isEqualTo(expected);
    }

    @Test
    public void testByteArrayToHexString() {
        assertThat(Utils.byteArrayToHexString(new byte[] {0, 1, 2, -3})).isEqualTo("000102fd");
    }

    @Test
    public void testUuidToBytes() {
        UUID uuid = new UUID(123456789L, 987654321L);
        byte[] expected = new byte[] {0, 0, 0, 0, 7, 91, -51, 21, 0, 0, 0, 0, 58, -34, 104, -79};

        assertThat(Utils.uuidToBytes(uuid)).isEqualTo(expected);
    }

    @Test
    public void testBytesToUUID() {
        byte[] bytes = new byte[] {1, 2, 3, 4, 5, 6, 7, 8, 9, -9, -8, -7, -6, -5, -4, -3};
        UUID expected = new UUID(72623859790382856L, 718316418130246909L);

        assertThat(Utils.bytesToUUID(bytes).getLeastSignificantBits())
                .isEqualTo(718316418130246909L);
        assertThat(Utils.bytesToUUID(bytes).getMostSignificantBits()).isEqualTo(72623859790382856L);
        assertThat(Utils.bytesToUUID(bytes)).isEqualTo(expected);
    }

    @Test
    public void testBytesToUUID_invalidLength() {
        byte[] bytes = new byte[] {0};

        assertThat(Utils.bytesToUUID(bytes)).isNull();
    }

    @Test
    public void testGenerateRandomNumberString() {
        String result = Utils.generateRandomNumberString(25);

        assertThat(result).hasLength(25);
        assertThat(TextUtils.isDigitsOnly(result)).isTrue();
    }

    @Test
    public void testConcatByteArrays() {
        byte[] bytes1 = new byte[] {1, 2, 3};
        byte[] bytes2 = new byte[] {4, 5, 6};
        Byte[] expected = new Byte[] {1, 2, 3, 4, 5, 6};

        assertThat(Utils.concatByteArrays(bytes1, bytes2)).asList()
                .containsExactlyElementsIn(expected).inOrder();
    }

    @Test
    public void testIsEventOfType_returnsTrue() {
        assertThat(Utils.isEventOfType(TAG, USER_STARTING_EVENT,
                USER_LIFECYCLE_EVENT_TYPE_STARTING)).isTrue();
    }

    @Test
    @ExpectWtf
    public void testIsEventOfType_returnsFalse() {
        assertThat(Utils.isEventOfType(TAG, USER_STARTING_EVENT,
                USER_LIFECYCLE_EVENT_TYPE_SWITCHING)).isFalse();
    }

    @Test
    public void testIsEventAnyOfTypes_returnsTrue() {
        assertThat(Utils.isEventAnyOfTypes(TAG, USER_STARTING_EVENT,
                USER_LIFECYCLE_EVENT_TYPE_SWITCHING, USER_LIFECYCLE_EVENT_TYPE_STARTING)).isTrue();
    }

    @Test
    @ExpectWtf
    public void testIsEventAnyOfTypes_emptyEventTypes_returnsFalse() {
        assertThat(Utils.isEventAnyOfTypes(TAG, USER_STARTING_EVENT)).isFalse();
    }

    @Test
    @ExpectWtf
    public void testIsEventAnyOfTypes_returnsFalse() {
        assertThat(Utils.isEventAnyOfTypes(TAG, USER_STARTING_EVENT,
                USER_LIFECYCLE_EVENT_TYPE_SWITCHING, USER_LIFECYCLE_EVENT_TYPE_STOPPING)).isFalse();
    }

    @Test
    public void testCheckCalledByPackage_nullPackages() {
        String packageName = "Bond.James.Bond";
        int myUid = Process.myUid();
        // Don't need to mock pm call, it will return null

        SecurityException e = assertThrows(SecurityException.class,
                () -> Utils.checkCalledByPackage(mContext, packageName));

        String msg = e.getMessage();
        assertWithMessage("exception message (pkg)").that(msg).contains(packageName);
        assertWithMessage("exception message (uid)").that(msg).contains(String.valueOf(myUid));
    }

    @Test
    public void testCheckCalledByPackage_emptyPackages() {
        String packageName = "Bond.James.Bond";
        int myUid = Process.myUid();
        when(mPm.getPackagesForUid(myUid)).thenReturn(new String[] {});

        // Don't need to mock pm call, it will return null

        SecurityException e = assertThrows(SecurityException.class,
                () -> Utils.checkCalledByPackage(mContext, packageName));

        String msg = e.getMessage();
        assertWithMessage("exception message (pkg)").that(msg).contains(packageName);
        assertWithMessage("exception message (uid)").that(msg).contains(String.valueOf(myUid));
    }

    @Test
    public void testCheckCalledByPackage_wrongPackages() {
        String packageName = "Bond.James.Bond";
        int myUid = Process.myUid();
        when(mPm.getPackagesForUid(myUid)).thenReturn(new String[] {"Bond, James Bond"});

        SecurityException e = assertThrows(SecurityException.class,
                () -> Utils.checkCalledByPackage(mContext, packageName));

        String msg = e.getMessage();
        assertWithMessage("exception message (pkg)").that(msg).contains(packageName);
        assertWithMessage("exception message (uid)").that(msg).contains(String.valueOf(myUid));
    }

    @Test
    public void testCheckCalledByPackage_ok() {
        String packageName = "Bond.James.Bond";
        int myUid = Process.myUid();
        when(mPm.getPackagesForUid(myUid)).thenReturn(new String[] {
                "Bond, James Bond", packageName, "gold.finger"
        });

        Utils.checkCalledByPackage(mContext, packageName);

        // No need to assert, test would fail if it threw
    }
}
