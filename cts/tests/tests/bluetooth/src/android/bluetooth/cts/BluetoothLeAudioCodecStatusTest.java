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

package android.bluetooth.cts;

import android.bluetooth.BluetoothLeAudioCodecConfig;
import android.bluetooth.BluetoothLeAudioCodecStatus;
import android.os.Parcel;
import android.test.AndroidTestCase;

import java.util.ArrayList;
import java.util.List;

public class BluetoothLeAudioCodecStatusTest extends AndroidTestCase {
    private static final BluetoothLeAudioCodecConfig LC3_16KHZ_CONFIG =
            new BluetoothLeAudioCodecConfig.Builder()
                .setCodecType(BluetoothLeAudioCodecConfig.SOURCE_CODEC_TYPE_LC3)
                .setSampleRate(BluetoothLeAudioCodecConfig.SAMPLE_RATE_16000)
                .build();
    private static final BluetoothLeAudioCodecConfig LC3_48KHZ_CONFIG =
            new BluetoothLeAudioCodecConfig.Builder()
                .setCodecType(BluetoothLeAudioCodecConfig.SOURCE_CODEC_TYPE_LC3)
                .setSampleRate(BluetoothLeAudioCodecConfig.SAMPLE_RATE_48000)
                .build();

    private static final BluetoothLeAudioCodecConfig LC3_48KHZ_16KHZ_CONFIG =
             new BluetoothLeAudioCodecConfig.Builder()
               .setCodecType(BluetoothLeAudioCodecConfig.SOURCE_CODEC_TYPE_LC3)
               .setSampleRate(BluetoothLeAudioCodecConfig.SAMPLE_RATE_48000
                                | BluetoothLeAudioCodecConfig.SAMPLE_RATE_16000)
               .build();
    private static final List<BluetoothLeAudioCodecConfig> INPUT_CAPABILITIES_CONFIG =
            new ArrayList() {{
                    add(LC3_48KHZ_16KHZ_CONFIG);
            }};

    private static final List<BluetoothLeAudioCodecConfig> OUTPUT_CAPABILITIES_CONFIG =
            new ArrayList() {{
                    add(LC3_48KHZ_16KHZ_CONFIG);
            }};

    private static final List<BluetoothLeAudioCodecConfig> INPUT_SELECTABLE_CONFIG =
            new ArrayList() {{
                    add(LC3_16KHZ_CONFIG);
            }};

    private static final List<BluetoothLeAudioCodecConfig> OUTPUT_SELECTABLE_CONFIG =
            new ArrayList() {{
                    add(LC3_48KHZ_16KHZ_CONFIG);
            }};

    private static final BluetoothLeAudioCodecStatus LE_CODEC_STATUS =
            new BluetoothLeAudioCodecStatus(LC3_16KHZ_CONFIG, LC3_48KHZ_CONFIG,
                        INPUT_CAPABILITIES_CONFIG, OUTPUT_CAPABILITIES_CONFIG,
                        INPUT_SELECTABLE_CONFIG, OUTPUT_SELECTABLE_CONFIG);

    public void testGetInputCodecConfig() {
        assertTrue(LE_CODEC_STATUS.getInputCodecConfig().equals(LC3_16KHZ_CONFIG));
    }

    public void testGetOutputCodecConfig() {
        assertTrue(LE_CODEC_STATUS.getOutputCodecConfig().equals(LC3_48KHZ_CONFIG));
    }

    public void testGetInputCodecLocalCapabilities() {
        assertTrue(
                LE_CODEC_STATUS.getInputCodecLocalCapabilities()
                                .equals(INPUT_CAPABILITIES_CONFIG));
    }

    public void testGetOutputCodecLocalCapabilities() {
        assertTrue(
                LE_CODEC_STATUS.getOutputCodecLocalCapabilities()
                                .equals(OUTPUT_CAPABILITIES_CONFIG));
    }

    public void testGetInputCodecSelectableCapabilities() {
        assertTrue(
                LE_CODEC_STATUS.getInputCodecSelectableCapabilities()
                        .equals(INPUT_SELECTABLE_CONFIG));
    }

    public void testGetOutputCodecSelectableCapabilities() {
        assertTrue(
                LE_CODEC_STATUS.getOutputCodecSelectableCapabilities()
                        .equals(OUTPUT_SELECTABLE_CONFIG));
    }

    public void testIsInputCodecConfigSelectable() {
        assertTrue(LE_CODEC_STATUS.isInputCodecConfigSelectable(LC3_16KHZ_CONFIG));
        assertTrue(!(LE_CODEC_STATUS.isInputCodecConfigSelectable(LC3_48KHZ_CONFIG)));
    }

    public void testIsOutputCodecConfigSelectable() {
        assertTrue(LE_CODEC_STATUS.isOutputCodecConfigSelectable(LC3_16KHZ_CONFIG));
        assertTrue(LE_CODEC_STATUS.isOutputCodecConfigSelectable(LC3_48KHZ_CONFIG));
    }

    public void testDescribeContents() {
        assertEquals(0, LE_CODEC_STATUS.describeContents());
    }

    public void testReadWriteParcel() {
        Parcel parcel = Parcel.obtain();
        LE_CODEC_STATUS.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        BluetoothLeAudioCodecStatus codecStatusFromParcel =
                BluetoothLeAudioCodecStatus.CREATOR.createFromParcel(parcel);
        assertTrue(codecStatusFromParcel.getInputCodecConfig().equals(LC3_16KHZ_CONFIG));
        assertTrue(codecStatusFromParcel.getOutputCodecConfig().equals(LC3_48KHZ_CONFIG));
        assertTrue(
                codecStatusFromParcel.getInputCodecLocalCapabilities()
                                .equals(INPUT_CAPABILITIES_CONFIG));
        assertTrue(
                codecStatusFromParcel.getOutputCodecLocalCapabilities()
                                .equals(OUTPUT_CAPABILITIES_CONFIG));
        assertTrue(
                codecStatusFromParcel.getInputCodecSelectableCapabilities()
                                .equals(INPUT_SELECTABLE_CONFIG));
        assertTrue(
                codecStatusFromParcel.getOutputCodecSelectableCapabilities()
                                .equals(OUTPUT_SELECTABLE_CONFIG));
    }
}
