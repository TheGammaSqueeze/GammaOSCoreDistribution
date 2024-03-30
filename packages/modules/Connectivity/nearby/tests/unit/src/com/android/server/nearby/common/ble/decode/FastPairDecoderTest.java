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

package com.android.server.nearby.common.ble.decode;

import static com.android.server.nearby.common.ble.BleRecord.parseFromBytes;
import static com.android.server.nearby.common.ble.testing.FastPairTestData.FAST_PAIR_MODEL_ID;
import static com.android.server.nearby.common.ble.testing.FastPairTestData.getFastPairRecord;
import static com.android.server.nearby.common.ble.testing.FastPairTestData.newFastPairRecord;

import static com.google.common.truth.Truth.assertThat;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.server.nearby.common.ble.BleRecord;
import com.android.server.nearby.util.Hex;

import com.google.common.primitives.Bytes;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class FastPairDecoderTest {
    private static final String LONG_MODEL_ID = "1122334455667788";
    private final FastPairDecoder mDecoder = new FastPairDecoder();
    // Bits 3-6 are model ID length bits = 0b1000 = 8
    private static final byte LONG_MODEL_ID_HEADER = 0b00010000;
    private static final String PADDED_LONG_MODEL_ID = "00001111";
    // Bits 3-6 are model ID length bits = 0b0100 = 4
    private static final byte PADDED_LONG_MODEL_ID_HEADER = 0b00001000;
    private static final String TRIMMED_LONG_MODEL_ID = "001111";
    private static final byte MODEL_ID_HEADER = 0b00000110;
    private static final String MODEL_ID = "112233";
    private static final byte BLOOM_FILTER_HEADER = 0b01100000;
    private static final String BLOOM_FILTER = "112233445566";
    private static final byte BLOOM_FILTER_SALT_HEADER = 0b00010001;
    private static final String BLOOM_FILTER_SALT = "01";
    private static final byte RANDOM_RESOLVABLE_DATA_HEADER = 0b01000110;
    private static final String RANDOM_RESOLVABLE_DATA = "11223344";
    private static final byte BLOOM_FILTER_NO_NOTIFICATION_HEADER = 0b01100010;


    @Test
    public void getModelId() {
        assertThat(mDecoder.getBeaconIdBytes(parseFromBytes(getFastPairRecord())))
                .isEqualTo(FAST_PAIR_MODEL_ID);
        FastPairServiceData fastPairServiceData1 =
                new FastPairServiceData(LONG_MODEL_ID_HEADER,
                        LONG_MODEL_ID);
        assertThat(
                mDecoder.getBeaconIdBytes(
                        newBleRecord(fastPairServiceData1.createServiceData())))
                .isEqualTo(Hex.stringToBytes(LONG_MODEL_ID));
        FastPairServiceData fastPairServiceData =
                new FastPairServiceData(PADDED_LONG_MODEL_ID_HEADER,
                        PADDED_LONG_MODEL_ID);
        assertThat(
                mDecoder.getBeaconIdBytes(
                        newBleRecord(fastPairServiceData.createServiceData())))
                .isEqualTo(Hex.stringToBytes(TRIMMED_LONG_MODEL_ID));
    }

    @Test
    public void getBloomFilter() {
        FastPairServiceData fastPairServiceData = new FastPairServiceData(MODEL_ID_HEADER,
                MODEL_ID);
        fastPairServiceData.mExtraFieldHeaders.add(BLOOM_FILTER_HEADER);
        fastPairServiceData.mExtraFields.add(BLOOM_FILTER);
        assertThat(FastPairDecoder.getBloomFilter(fastPairServiceData.createServiceData()))
                .isEqualTo(Hex.stringToBytes(BLOOM_FILTER));
    }

    @Test
    public void getBloomFilter_smallModelId() {
        FastPairServiceData fastPairServiceData = new FastPairServiceData(null, MODEL_ID);
        assertThat(FastPairDecoder.getBloomFilter(fastPairServiceData.createServiceData()))
                .isNull();
    }

    @Test
    public void getBloomFilterSalt_modelIdAndMultipleExtraFields() {
        FastPairServiceData fastPairServiceData = new FastPairServiceData(MODEL_ID_HEADER,
                MODEL_ID);
        fastPairServiceData.mExtraFieldHeaders.add(BLOOM_FILTER_HEADER);
        fastPairServiceData.mExtraFieldHeaders.add(BLOOM_FILTER_SALT_HEADER);
        fastPairServiceData.mExtraFields.add(BLOOM_FILTER);
        fastPairServiceData.mExtraFields.add(BLOOM_FILTER_SALT);
        assertThat(
                FastPairDecoder.getBloomFilterSalt(fastPairServiceData.createServiceData()))
                .isEqualTo(Hex.stringToBytes(BLOOM_FILTER_SALT));
    }

    @Test
    public void getRandomResolvableData_whenContainConnectionState() {
        FastPairServiceData fastPairServiceData = new FastPairServiceData(MODEL_ID_HEADER,
                MODEL_ID);
        fastPairServiceData.mExtraFieldHeaders.add(RANDOM_RESOLVABLE_DATA_HEADER);
        fastPairServiceData.mExtraFields.add(RANDOM_RESOLVABLE_DATA);
        assertThat(
                FastPairDecoder.getRandomResolvableData(fastPairServiceData
                                .createServiceData()))
                .isEqualTo(Hex.stringToBytes(RANDOM_RESOLVABLE_DATA));
    }

    @Test
    public void getBloomFilterNoNotification() {
        FastPairServiceData fastPairServiceData =
                new FastPairServiceData(MODEL_ID_HEADER, MODEL_ID);
        fastPairServiceData.mExtraFieldHeaders.add(BLOOM_FILTER_NO_NOTIFICATION_HEADER);
        fastPairServiceData.mExtraFields.add(BLOOM_FILTER);
        assertThat(FastPairDecoder.getBloomFilterNoNotification(fastPairServiceData
                        .createServiceData())).isEqualTo(Hex.stringToBytes(BLOOM_FILTER));
    }

    private static BleRecord newBleRecord(byte[] serviceDataBytes) {
        return parseFromBytes(newFastPairRecord(serviceDataBytes));
    }
    class FastPairServiceData {
        private Byte mHeader;
        private String mModelId;
        List<Byte> mExtraFieldHeaders = new ArrayList<>();
        List<String> mExtraFields = new ArrayList<>();

        FastPairServiceData(Byte header, String modelId) {
            this.mHeader = header;
            this.mModelId = modelId;
        }
        private byte[] createServiceData() {
            if (mExtraFieldHeaders.size() != mExtraFields.size()) {
                throw new RuntimeException("Number of headers and extra fields must match.");
            }
            byte[] serviceData =
                    Bytes.concat(
                            mHeader == null ? new byte[0] : new byte[] {mHeader},
                            mModelId == null ? new byte[0] : Hex.stringToBytes(mModelId));
            for (int i = 0; i < mExtraFieldHeaders.size(); i++) {
                serviceData =
                        Bytes.concat(
                                serviceData,
                                mExtraFieldHeaders.get(i) != null
                                        ? new byte[] {mExtraFieldHeaders.get(i)}
                                        : new byte[0],
                                mExtraFields.get(i) != null
                                        ? Hex.stringToBytes(mExtraFields.get(i))
                                        : new byte[0]);
            }
            return serviceData;
        }
    }


}
