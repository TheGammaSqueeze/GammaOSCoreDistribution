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

package com.android.server.uwb.util;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import androidx.test.filters.SmallTest;

import org.junit.Test;

@SmallTest
public class DataTypeConversionUtilTest {
    @Test
    public void byteArrayToI32_success() {
        assertThat(DataTypeConversionUtil.byteArrayToI32(new byte[]{0x01, 0x02, 0x03, 0x04}))
                .isEqualTo(0x01020304);
    }

    @Test
    public void byteArrayToI32_highByte_success() {
        assertThat(
                DataTypeConversionUtil.byteArrayToI32(
                        new byte[]{(byte) 0xFF, (byte) 0xA5, (byte) 0xAA, (byte) 0xF0}))
                .isEqualTo(0xFFA5AAF0);
    }

    @Test
    public void byteArrayToI32_shortArray_Failure() {
        assertThrows(
                NumberFormatException.class,
                () -> DataTypeConversionUtil.byteArrayToI32(new byte[]{0x01}));
    }

    @Test
    public void byteArrayToI32_longArray_failure() {
        assertThrows(
                NumberFormatException.class,
                () -> DataTypeConversionUtil.byteArrayToI32(
                        new byte[]{0x01, 0x02, 0x03, 0x04, 0x05}));
    }

    @Test
    public void i32ToByteArray_success() {
        assertThat(DataTypeConversionUtil.i32ToByteArray(0x01020304))
                .isEqualTo(new byte[] { (byte) 0x01, (byte) 0x02, (byte) 0x03, (byte) 0x04 });
    }

    @Test
    public void i32ToLeByteArray_success() {
        assertThat(DataTypeConversionUtil.i32ToLeByteArray(0x01020304))
                .isEqualTo(new byte[] { (byte) 0x04, (byte) 0x03, (byte) 0x02, (byte) 0x01 });
    }

    @Test
    public void byteArrayToHexString_byteString_success() {
        String hexString = "010203040A0B0C0D";
        assertThat(DataTypeConversionUtil.byteArrayToHexString(
                new byte[] { (byte) 0x01, (byte) 0x02, (byte) 0x03, (byte) 0x04,
                        (byte) 0x0A, (byte) 0x0B, (byte) 0x0C, (byte) 0x0D }))
                .isEqualTo(hexString);
    }

    @Test
    public void byteArrayToHexString_null() {
        assertThat(DataTypeConversionUtil.byteArrayToHexString(null)).isEmpty();
    }

    @Test
    public void hexStringToByteArray_success() {
        String hexString = "010203040A0B0C0D";
        assertThat(DataTypeConversionUtil.hexStringToByteArray(hexString))
                .isEqualTo(new byte[] { (byte) 0x01, (byte) 0x02, (byte) 0x03, (byte) 0x04,
                        (byte) 0x0A, (byte) 0x0B, (byte) 0x0C, (byte) 0x0D });
    }

    @Test
    public void oneByteArbitraryByteArrayToI32() {
        byte[] lengthBytes = {(byte) 178};
        int actual = DataTypeConversionUtil.arbitraryByteArrayToI32(lengthBytes);

        int expected = 178;
        assertEquals(expected, actual);
    }

    @Test
    public void twoBytesArbitraryByteArrayToI32() {
        byte[] lengthBytes = {(byte) 0x01, (byte) 0x1A};
        int actual = DataTypeConversionUtil.arbitraryByteArrayToI32(lengthBytes);

        int expected = 282;
        assertEquals(expected, actual);
    }

    @Test
    public void threeBytesArbitraryByteArrayToI32() {
        byte[] lengthBytes = {(byte) 0x01, (byte) 0x01, (byte) 0x1A};
        int actual = DataTypeConversionUtil.arbitraryByteArrayToI32(lengthBytes);

        int expected = 65818;
        assertEquals(expected, actual);
    }

    @Test
    public void fourBytesArbitraryByteArrayToI32() {
        byte[] lengthBytes = {(byte) 0x01, (byte) 0x01, (byte) 0x01, (byte) 0x1A};
        int actual = DataTypeConversionUtil.arbitraryByteArrayToI32(lengthBytes);

        int expected = 16843034;
        assertEquals(expected, actual);
    }

    @Test
    public void fiveBytesArbitraryByteArrayToI32() {
        assertThrows(
                NumberFormatException.class,
                () -> DataTypeConversionUtil.arbitraryByteArrayToI32(
                        new byte[] {
                                (byte) 0x01, (byte) 0x01, (byte) 0x01, (byte) 0x1A, (byte) 0x01 }));

        assertThrows(
                NumberFormatException.class,
                () -> DataTypeConversionUtil.arbitraryByteArrayToI32(
                        new byte[0]));

    }
}
