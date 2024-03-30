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

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.primitives.UnsignedInts;

import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.BitSet;

/**
 * A bloom filter that gives access to the underlying BitSet.
 */
public class BloomFilter {
    private static final Charset CHARSET = UTF_8;

    /**
     * Receives a value and converts it into an array of ints that will be converted to indexes for
     * the filter.
     */
    public interface Hasher {
        /**
         * Generate hash value.
         */
        int[] getHashes(byte[] value);
    }

    // The backing data for this bloom filter. As additions are made, they're OR'd until it
    // eventually reaches 0xFF.
    private final BitSet mBits;
    // The max length of bits.
    private final int mBitLength;
    // The hasher to use for converting a value into an array of hashes.
    private final Hasher mHasher;

    public BloomFilter(byte[] bytes, Hasher hasher) {
        this.mBits = BitSet.valueOf(bytes);
        this.mBitLength = bytes.length * 8;
        this.mHasher = hasher;
    }

    /**
     * Return the bloom filter check bit set as byte array.
     */
    public byte[] asBytes() {
        // BitSet.toByteArray() truncates all the unset bits after the last set bit (eg. [0,0,1,0]
        // becomes [0,0,1]) so we re-add those bytes if needed with Arrays.copy().
        byte[] b = mBits.toByteArray();
        if (b.length == mBitLength / 8) {
            return b;
        }
        return Arrays.copyOf(b, mBitLength / 8);
    }

    /**
     * Add string value to bloom filter hash.
     */
    public void add(String s) {
        add(s.getBytes(CHARSET));
    }

    /**
     * Adds value to bloom filter hash.
     */
    public void add(byte[] value) {
        int[] hashes = mHasher.getHashes(value);
        for (int hash : hashes) {
            mBits.set(UnsignedInts.remainder(hash, mBitLength));
        }
    }

    /**
     * Check if the string format has collision.
     */
    public boolean possiblyContains(String s) {
        return possiblyContains(s.getBytes(CHARSET));
    }

    /**
     * Checks if value after hash will have collision.
     */
    public boolean possiblyContains(byte[] value) {
        int[] hashes = mHasher.getHashes(value);
        for (int hash : hashes) {
            if (!mBits.get(UnsignedInts.remainder(hash, mBitLength))) {
                return false;
            }
        }
        return true;
    }
}

