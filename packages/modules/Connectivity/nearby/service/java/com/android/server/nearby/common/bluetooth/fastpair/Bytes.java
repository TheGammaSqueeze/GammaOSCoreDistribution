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

import androidx.annotation.Nullable;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.util.Arrays;

/** Represents a block of bytes, with hashCode and equals. */
public abstract class Bytes {
    private static final char[] sHexDigits = "0123456789abcdef".toCharArray();
    private final byte[] mBytes;

    /**
     * A logical value consisting of one or more bytes in the given order (little-endian, i.e.
     * LSO...MSO, or big-endian, i.e. MSO...LSO). E.g. the Fast Pair Model ID is a 3-byte value,
     * and a Bluetooth device address is a 6-byte value.
     */
    public static class Value extends Bytes {
        private final ByteOrder mByteOrder;

        /**
         * Constructor.
         */
        public Value(byte[] bytes, ByteOrder byteOrder) {
            super(bytes);
            this.mByteOrder = byteOrder;
        }

        /**
         * Gets bytes.
         */
        public byte[] getBytes(ByteOrder byteOrder) {
            return this.mByteOrder.equals(byteOrder) ? getBytes() : reverse(getBytes());
        }

        private static byte[] reverse(byte[] bytes) {
            byte[] reversedBytes = new byte[bytes.length];
            for (int i = 0; i < bytes.length; i++) {
                reversedBytes[i] = bytes[bytes.length - i - 1];
            }
            return reversedBytes;
        }
    }

    Bytes(byte[] bytes) {
        mBytes = bytes;
    }

    private static String toHexString(byte[] bytes) {
        StringBuilder sb = new StringBuilder(2 * bytes.length);
        for (byte b : bytes) {
            sb.append(sHexDigits[(b >> 4) & 0xf]).append(sHexDigits[b & 0xf]);
        }
        return sb.toString();
    }

    /** Returns 2-byte values in the same order, each using the given byte order. */
    public static byte[] toBytes(ByteOrder byteOrder, short... shorts) {
        ByteBuffer byteBuffer = ByteBuffer.allocate(shorts.length * 2).order(byteOrder);
        for (short s : shorts) {
            byteBuffer.putShort(s);
        }
        return byteBuffer.array();
    }

    /** Returns the shorts in the same order, each converted using the given byte order. */
    static short[] toShorts(ByteOrder byteOrder, byte[] bytes) {
        ShortBuffer shortBuffer = ByteBuffer.wrap(bytes).order(byteOrder).asShortBuffer();
        short[] shorts = new short[shortBuffer.remaining()];
        shortBuffer.get(shorts);
        return shorts;
    }

    /** @return The bytes. */
    public byte[] getBytes() {
        return mBytes;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Bytes)) {
            return false;
        }
        Bytes that = (Bytes) o;
        return Arrays.equals(mBytes, that.mBytes);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(mBytes);
    }

    @Override
    public String toString() {
        return toHexString(mBytes);
    }
}
