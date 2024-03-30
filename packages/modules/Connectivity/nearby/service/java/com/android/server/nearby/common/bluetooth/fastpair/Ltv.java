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

import static com.google.common.io.BaseEncoding.base16;

import com.google.common.primitives.Bytes;
import com.google.errorprone.annotations.FormatMethod;
import com.google.errorprone.annotations.FormatString;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A length, type, value (LTV) data block.
 */
public class Ltv {

    private static final int SIZE_OF_LEN_TYPE = 2;

    final byte mType;
    final byte[] mValue;

    /**
     * Thrown if there's an error during {@link #parse}.
     */
    public static class ParseException extends Exception {

        @FormatMethod
        private ParseException(@FormatString String format, Object... objects) {
            super(String.format(format, objects));
        }
    }

    /**
     * Constructor.
     */
    public Ltv(byte type, byte... value) {
        this.mType = type;
        this.mValue = value;
    }

    /**
     * Parses a list of LTV blocks out of the input byte block.
     */
    static List<Ltv> parse(byte[] bytes) throws ParseException {
        List<Ltv> ltvs = new ArrayList<>();
        // The "+ 2" is for the length and type bytes.
        for (int valueLength, i = 0; i < bytes.length; i += SIZE_OF_LEN_TYPE + valueLength) {
            // - 1 since the length in the packet includes the type byte.
            valueLength = bytes[i] - 1;
            if (valueLength < 0 || bytes.length < i + SIZE_OF_LEN_TYPE + valueLength) {
                throw new ParseException(
                        "Wrong length=%d at index=%d in LTVs=%s", bytes[i], i,
                        base16().encode(bytes));
            }
            ltvs.add(new Ltv(bytes[i + 1], Arrays.copyOfRange(bytes, i + SIZE_OF_LEN_TYPE,
                    i + SIZE_OF_LEN_TYPE + valueLength)));
        }
        return ltvs;
    }

    /**
     * Returns an LTV block, where length is mValue.length + 1 (for the type byte).
     */
    public byte[] getBytes() {
        return Bytes.concat(new byte[]{(byte) (mValue.length + 1), mType}, mValue);
    }
}
