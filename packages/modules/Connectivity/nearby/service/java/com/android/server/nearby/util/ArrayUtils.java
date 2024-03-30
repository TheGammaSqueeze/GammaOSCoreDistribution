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

import java.util.Arrays;

/**
 * ArrayUtils class that help manipulate array.
 */
public class ArrayUtils {
    /** Concatenate N arrays of bytes into a single array. */
    public static byte[] concatByteArrays(byte[]... arrays) {
        // Degenerate case - no input provided.
        if (arrays.length == 0) {
            return new byte[0];
        }

        // Compute the total size.
        int totalSize = 0;
        for (int i = 0; i < arrays.length; i++) {
            totalSize += arrays[i].length;
        }

        // Copy the arrays into the new array.
        byte[] result = Arrays.copyOf(arrays[0], totalSize);
        int pos = arrays[0].length;
        for (int i = 1; i < arrays.length; i++) {
            byte[] current = arrays[i];
            System.arraycopy(current, 0, result, pos, current.length);
            pos += current.length;
        }
        return result;
    }
}
