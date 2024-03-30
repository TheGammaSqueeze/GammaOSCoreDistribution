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

import android.net.Uri;
import android.os.Parcel;
import android.platform.test.annotations.Presubmit;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SdkSuppress;
import androidx.test.filters.SmallTest;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Unit tests for {@link HeadsetPiece}.
 */
@Presubmit
@SmallTest
@RunWith(AndroidJUnit4.class)
public class HeadsetPieceTest {

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void parcelAndUnparcel() {
        HeadsetPiece headsetPiece = createDefaultHeadset().build();
        Parcel expectedParcel = Parcel.obtain();
        headsetPiece.writeToParcel(expectedParcel, 0);
        expectedParcel.setDataPosition(0);

        HeadsetPiece fromParcel = HeadsetPiece.CREATOR.createFromParcel(expectedParcel);

        assertThat(fromParcel).isEqualTo(headsetPiece);
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void parcelAndUnparcel_nullImageContentUri() {
        HeadsetPiece headsetPiece = createDefaultHeadset().setImageContentUri(null).build();
        Parcel expectedParcel = Parcel.obtain();
        headsetPiece.writeToParcel(expectedParcel, 0);
        expectedParcel.setDataPosition(0);

        HeadsetPiece fromParcel = HeadsetPiece.CREATOR.createFromParcel(expectedParcel);

        assertThat(fromParcel).isEqualTo(headsetPiece);
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void equals() {
        HeadsetPiece headsetPiece = createDefaultHeadset().build();

        HeadsetPiece compareTo = createDefaultHeadset().build();

        assertThat(headsetPiece).isEqualTo(compareTo);
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void equals_nullImageContentUri() {
        HeadsetPiece headsetPiece = createDefaultHeadset().setImageContentUri(null).build();

        HeadsetPiece compareTo = createDefaultHeadset().setImageContentUri(null).build();

        assertThat(headsetPiece).isEqualTo(compareTo);
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void notEquals_differentLowLevelThreshold() {
        HeadsetPiece headsetPiece = createDefaultHeadset().build();

        HeadsetPiece compareTo = createDefaultHeadset().setLowLevelThreshold(1).build();

        assertThat(headsetPiece).isNotEqualTo(compareTo);
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void notEquals_differentBatteryLevel() {
        HeadsetPiece headsetPiece = createDefaultHeadset().build();

        HeadsetPiece compareTo = createDefaultHeadset().setBatteryLevel(99).build();

        assertThat(headsetPiece).isNotEqualTo(compareTo);
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void notEquals_differentImageUrl() {
        HeadsetPiece headsetPiece = createDefaultHeadset().build();

        HeadsetPiece compareTo =
                createDefaultHeadset().setImageUrl("http://fake.image.path/different.png").build();

        assertThat(headsetPiece).isNotEqualTo(compareTo);
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void notEquals_differentChargingState() {
        HeadsetPiece headsetPiece = createDefaultHeadset().build();

        HeadsetPiece compareTo = createDefaultHeadset().setCharging(false).build();

        assertThat(headsetPiece).isNotEqualTo(compareTo);
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void notEquals_differentImageContentUri() {
        HeadsetPiece headsetPiece = createDefaultHeadset().build();

        HeadsetPiece compareTo =
                createDefaultHeadset().setImageContentUri(Uri.parse("content://different.png"))
                        .build();

        assertThat(headsetPiece).isNotEqualTo(compareTo);
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void notEquals_nullImageContentUri() {
        HeadsetPiece headsetPiece = createDefaultHeadset().build();

        HeadsetPiece compareTo = createDefaultHeadset().setImageContentUri(null).build();

        assertThat(headsetPiece).isNotEqualTo(compareTo);
    }

    private static HeadsetPiece.Builder createDefaultHeadset() {
        return HeadsetPiece.builder()
                .setLowLevelThreshold(30)
                .setBatteryLevel(18)
                .setImageUrl("http://fake.image.path/image.png")
                .setImageContentUri(Uri.parse("content://image.png"))
                .setCharging(true);
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void isLowBattery() {
        HeadsetPiece headsetPiece =
                HeadsetPiece.builder()
                        .setLowLevelThreshold(30)
                        .setBatteryLevel(18)
                        .setImageUrl("http://fake.image.path/image.png")
                        .setCharging(false)
                        .build();

        assertThat(headsetPiece.isBatteryLow()).isTrue();
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void isNotLowBattery() {
        HeadsetPiece headsetPiece =
                HeadsetPiece.builder()
                        .setLowLevelThreshold(30)
                        .setBatteryLevel(31)
                        .setImageUrl("http://fake.image.path/image.png")
                        .setCharging(false)
                        .build();

        assertThat(headsetPiece.isBatteryLow()).isFalse();
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void isNotLowBattery_whileCharging() {
        HeadsetPiece headsetPiece =
                HeadsetPiece.builder()
                        .setLowLevelThreshold(30)
                        .setBatteryLevel(18)
                        .setImageUrl("http://fake.image.path/image.png")
                        .setCharging(true)
                        .build();

        assertThat(headsetPiece.isBatteryLow()).isFalse();
    }
}

