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

package com.android.server.nearby.util;

/**
 * Hex class that contains hex related functions.
 */
public class Hex {

    private static final char[] HEX_UPPERCASE = {
            '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'
    };

    private static final char[] HEX_LOWERCASE = {
            '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'
    };

    /**
     * Bytes array to lower case string.
     */
    public static String bytesToStringLowercase(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        int j = 0;
        for (byte aByte : bytes) {
            int v = aByte & 0xFF;
            hexChars[j++] = HEX_LOWERCASE[v >>> 4];
            hexChars[j++] = HEX_LOWERCASE[v & 0x0F];
        }
        return new String(hexChars);
    }

    /**
     * Encodes the byte array to string.
     */
    public static String bytesToStringUppercase(byte[] bytes) {
        return bytesToStringUppercase(bytes, false /* zeroTerminated */);
    }

    /** Encodes a byte array as a hexadecimal representation of bytes. */
    public static String bytesToStringUppercase(byte[] bytes, boolean zeroTerminated) {
        int length = bytes.length;
        StringBuilder out = new StringBuilder(length * 2);
        for (int i = 0; i < length; i++) {
            if (zeroTerminated && i == length - 1 && (bytes[i] & 0xff) == 0) {
                break;
            }
            out.append(HEX_UPPERCASE[(bytes[i] & 0xf0) >>> 4]);
            out.append(HEX_UPPERCASE[bytes[i] & 0x0f]);
        }
        return out.toString();
    }
    /**
     * Converts string to byte array.
     */
    public static byte[] stringToBytes(String hex) throws IllegalArgumentException {
        int length = hex.length();
        if (length % 2 != 0) {
            throw new IllegalArgumentException("Hex string has odd number of characters");
        }
        byte[] out = new byte[length / 2];
        for (int i = 0; i < length; i += 2) {
            // Byte.parseByte() doesn't work here because it expects a hex value in -128, 127, and
            // our hex values are in 0, 255.
            out[i / 2] = (byte) Integer.parseInt(hex.substring(i, i + 2), 16);
        }
        return out;
    }
}
