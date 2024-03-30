/*
 * Copyright 2021 The Android Open Source Project
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

import static com.google.common.primitives.Bytes.concat;

import com.google.common.hash.Hashing;
import com.google.protobuf.ByteString;

import java.util.Arrays;

/**
 * It contains the sha256 of "account key + headset's public address" to identify the headset which
 * has paired with the account. Previously, account key is the only information for Fast Pair to
 * identify the headset, but Fast Pair can't identify the headset in initial pairing, there is no
 * account key data advertising from headset.
 */
public class FastPairHistoryItem {

    private final ByteString mAccountKey;
    private final ByteString mSha256AccountKeyPublicAddress;

    FastPairHistoryItem(ByteString accountkey, ByteString sha256AccountKeyPublicAddress) {
        mAccountKey = accountkey;
        mSha256AccountKeyPublicAddress = sha256AccountKeyPublicAddress;
    }

    /**
     * Creates an instance of {@link FastPairHistoryItem}.
     *
     * @param accountKey key of an account that has paired with the headset.
     * @param sha256AccountKeyPublicAddress hash value of account key and headset's public address.
     */
    public static FastPairHistoryItem create(
            ByteString accountKey, ByteString sha256AccountKeyPublicAddress) {
        return new FastPairHistoryItem(accountKey, sha256AccountKeyPublicAddress);
    }

    ByteString accountKey() {
        return mAccountKey;
    }

    ByteString sha256AccountKeyPublicAddress() {
        return mSha256AccountKeyPublicAddress;
    }

    // Return true if the input public address is considered the same as this history item. Because
    // of privacy concern, Fast Pair does not really store the public address, it is identified by
    // the SHA256 of the account key and the public key.
    final boolean isMatched(byte[] publicAddress) {
        return Arrays.equals(
                sha256AccountKeyPublicAddress().toByteArray(),
                Hashing.sha256().hashBytes(concat(accountKey().toByteArray(), publicAddress))
                        .asBytes());
    }
}

