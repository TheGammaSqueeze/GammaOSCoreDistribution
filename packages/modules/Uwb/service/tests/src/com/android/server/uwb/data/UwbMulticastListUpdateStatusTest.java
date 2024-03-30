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

package com.android.server.uwb.data;

import static com.google.common.truth.Truth.assertThat;

import android.platform.test.annotations.Presubmit;
import android.test.suitebuilder.annotation.SmallTest;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;

/**
 * Unit tests for {@link com.android.server.uwb.data.UwbMulticastListUpdateStatus}.
 */
@RunWith(AndroidJUnit4.class)
@SmallTest
@Presubmit
public class UwbMulticastListUpdateStatusTest {
    private static final long TEST_SESSION_ID = 1;
    private static final int TEST_REMAINING_SIZE = 2;
    private static final int TEST_NUM_OF_CONTROLLEES = 1;
    private static final int[] TEST_CONTROLEE_ADDRESS = new int[] {0x0A, 0x04};
    private static final long[] TEST_SUB_SESSION_ID = new long[] {1, 1};
    private static final int[] TEST_STATUS = new int[] {0};

    private UwbMulticastListUpdateStatus mUwbMulticastListUpdateStatus;

    @Test
    public void testInitializeUwbMulticastListUpdateStatus() throws Exception {
        mUwbMulticastListUpdateStatus = new UwbMulticastListUpdateStatus(TEST_SESSION_ID,
                TEST_REMAINING_SIZE, TEST_NUM_OF_CONTROLLEES, TEST_CONTROLEE_ADDRESS,
                TEST_SUB_SESSION_ID, TEST_STATUS);

        assertThat(mUwbMulticastListUpdateStatus.getSessionId()).isEqualTo(TEST_SESSION_ID);
        assertThat(mUwbMulticastListUpdateStatus.getRemainingSize()).isEqualTo(TEST_REMAINING_SIZE);
        assertThat(mUwbMulticastListUpdateStatus.getNumOfControlee())
                .isEqualTo(TEST_NUM_OF_CONTROLLEES);
        assertThat(mUwbMulticastListUpdateStatus.getContolleeMacAddress())
                .isEqualTo(TEST_CONTROLEE_ADDRESS);
        assertThat(mUwbMulticastListUpdateStatus.getSubSessionId()).isEqualTo(TEST_SUB_SESSION_ID);
        assertThat(mUwbMulticastListUpdateStatus.getStatus()).isEqualTo(TEST_STATUS);

        final String testString = "UwbMulticastListUpdateEvent { "
                + " SessionID =" + TEST_SESSION_ID
                + ", RemainingSize =" + TEST_REMAINING_SIZE
                + ", NumOfControlee =" + TEST_NUM_OF_CONTROLLEES
                + ", MacAddress =" + Arrays.toString(TEST_CONTROLEE_ADDRESS)
                + ", SubSessionId =" + Arrays.toString(TEST_SUB_SESSION_ID)
                + ", Status =" + Arrays.toString(TEST_STATUS)
                + '}';

        assertThat(mUwbMulticastListUpdateStatus.toString()).isEqualTo(testString);
    }
}
