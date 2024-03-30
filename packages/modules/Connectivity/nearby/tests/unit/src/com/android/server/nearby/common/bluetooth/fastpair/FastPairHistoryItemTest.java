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

import static com.google.common.io.BaseEncoding.base16;
import static com.google.common.primitives.Bytes.concat;
import static com.google.common.truth.Truth.assertThat;

import android.platform.test.annotations.Presubmit;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SdkSuppress;
import androidx.test.filters.SmallTest;

import com.google.common.hash.Hashing;
import com.google.protobuf.ByteString;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Unit tests for {@link FastPairHistoryItem}.
 */
@Presubmit
@SmallTest
@RunWith(AndroidJUnit4.class)
public class FastPairHistoryItemTest {

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void inputMatchedPublicAddress_isMatchedReturnTrue() {
        final byte[] accountKey = base16().decode("0123456789ABCDEF");
        final byte[] publicAddress = BluetoothAddress.decode("11:22:33:44:55:66");
        final byte[] hashValue =
                Hashing.sha256().hashBytes(concat(accountKey, publicAddress)).asBytes();

        FastPairHistoryItem historyItem =
                FastPairHistoryItem
                        .create(ByteString.copyFrom(accountKey), ByteString.copyFrom(hashValue));

        assertThat(historyItem.isMatched(publicAddress)).isTrue();
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void inputNotMatchedPublicAddress_isMatchedReturnFalse() {
        final byte[] accountKey = base16().decode("0123456789ABCDEF");
        final byte[] publicAddress1 = BluetoothAddress.decode("11:22:33:44:55:66");
        final byte[] publicAddress2 = BluetoothAddress.decode("11:22:33:44:55:77");
        final byte[] hashValue =
                Hashing.sha256().hashBytes(concat(accountKey, publicAddress1)).asBytes();

        FastPairHistoryItem historyItem =
                FastPairHistoryItem
                        .create(ByteString.copyFrom(accountKey), ByteString.copyFrom(hashValue));

        assertThat(historyItem.isMatched(publicAddress2)).isFalse();
    }
}

