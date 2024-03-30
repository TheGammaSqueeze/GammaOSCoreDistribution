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

package com.android.server.nearby.common.bloomfilter;

import com.google.common.hash.Hashing;

import java.nio.ByteBuffer;

/**
 * Hasher which hashes a value using SHA-256 and splits it into parts, each of which can be
 * converted to an index.
 */
public class FastPairBloomFilterHasher implements BloomFilter.Hasher {

    private static final int NUM_INDEXES = 8;

    @Override
    public int[] getHashes(byte[] value) {
        byte[] hash = Hashing.sha256().hashBytes(value).asBytes();
        ByteBuffer buffer = ByteBuffer.wrap(hash);
        int[] hashes = new int[NUM_INDEXES];
        for (int i = 0; i < NUM_INDEXES; i++) {
            hashes[i] = buffer.getInt();
        }
        return hashes;
    }
}
