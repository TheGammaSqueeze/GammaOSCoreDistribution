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

package com.android.server.nearby.common.bluetooth.fastpair;

import static com.google.common.truth.Truth.assertThat;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.os.Parcel;
import android.platform.test.annotations.Presubmit;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SdkSuppress;
import androidx.test.filters.SmallTest;

import com.android.server.nearby.intdefs.NearbyEventIntDefs.EventCode;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Unit tests for {@link Event}.
 */
@Presubmit
@SmallTest
@RunWith(AndroidJUnit4.class)
public class EventTest {

    private static final String EXCEPTION_MESSAGE = "Test exception";
    private static final long TIMESTAMP = 1234L;
    private static final @EventCode int EVENT_CODE = EventCode.CREATE_BOND;
    private static final BluetoothDevice BLUETOOTH_DEVICE = BluetoothAdapter.getDefaultAdapter()
            .getRemoteDevice("11:22:33:44:55:66");
    private static final Short PROFILE = (short) 1;

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void createAndReadFromParcel() {
        Event event =
                Event.builder()
                        .setException(new Exception(EXCEPTION_MESSAGE))
                        .setTimestamp(TIMESTAMP)
                        .setEventCode(EVENT_CODE)
                        .setBluetoothDevice(BLUETOOTH_DEVICE)
                        .setProfile(PROFILE)
                        .build();

        Parcel parcel = Parcel.obtain();
        event.writeToParcel(parcel, event.describeContents());
        parcel.setDataPosition(0);
        Event result = Event.CREATOR.createFromParcel(parcel);

        assertThat(result.getException()).hasMessageThat()
                .isEqualTo(event.getException().getMessage());
        assertThat(result.getTimestamp()).isEqualTo(event.getTimestamp());
        assertThat(result.getEventCode()).isEqualTo(event.getEventCode());
        assertThat(result.getBluetoothDevice()).isEqualTo(event.getBluetoothDevice());
        assertThat(result.getProfile()).isEqualTo(event.getProfile());
    }
}
