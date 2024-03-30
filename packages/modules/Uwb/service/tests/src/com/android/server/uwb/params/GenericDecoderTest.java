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

package com.android.server.uwb.params;

import static com.google.common.truth.Truth.assertThat;

import android.platform.test.annotations.Presubmit;
import android.test.suitebuilder.annotation.SmallTest;

import androidx.test.runner.AndroidJUnit4;

import com.android.server.uwb.util.UwbUtil;

import com.google.uwb.support.generic.GenericSpecificationParams;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Unit tests for {@link com.android.server.uwb.params.GenericDecoder}.
 */
@RunWith(AndroidJUnit4.class)
@SmallTest
@Presubmit
public class GenericDecoderTest {
    private static final byte[] TEST_GENERC_SPECIFICATION_TLV_DATA =
            UwbUtil.getByteArray("C00101"
                    + FiraDecoderTest.TEST_FIRA_SPECIFICATION_TLV_STRING
                    + CccDecoderTest.TEST_CCC_SPECIFICATION_TLV_DATA_STRING);
    private static final int TEST_GENERIC_SPECIFICATION_TLV_NUM_PARAMS = 1
            + FiraDecoderTest.TEST_FIRA_SPECIFICATION_TLV_NUM_PARAMS
            + CccDecoderTest.TEST_CCC_SPECIFICATION_TLV_NUM_PARAMS;

    private final GenericDecoder mGenericDecoder = new GenericDecoder();

    @Test
    public void testGetGenericSpecification() throws Exception {
        TlvDecoderBuffer tlvDecoderBuffer =
                new TlvDecoderBuffer(
                        TEST_GENERC_SPECIFICATION_TLV_DATA,
                        TEST_GENERIC_SPECIFICATION_TLV_NUM_PARAMS);
        assertThat(tlvDecoderBuffer.parse()).isTrue();

        GenericSpecificationParams genericSpecificationParams = mGenericDecoder.getParams(
                tlvDecoderBuffer, GenericSpecificationParams.class);
        assertThat(genericSpecificationParams.hasPowerStatsSupport()).isTrue();
        FiraDecoderTest.verifyFiraSpecification(
                genericSpecificationParams.getFiraSpecificationParams());
        CccDecoderTest.verifyCccSpecification(
                genericSpecificationParams.getCccSpecificationParams());
    }

    @Test
    public void testGetGenericSpecificationViaTlvDecoder() throws Exception {
        TlvDecoderBuffer tlvDecoderBuffer =
                new TlvDecoderBuffer(
                        TEST_GENERC_SPECIFICATION_TLV_DATA,
                        TEST_GENERIC_SPECIFICATION_TLV_NUM_PARAMS);
        assertThat(tlvDecoderBuffer.parse()).isTrue();

        GenericSpecificationParams genericSpecificationParams = mGenericDecoder.getParams(
                tlvDecoderBuffer, GenericSpecificationParams.class);
        assertThat(genericSpecificationParams.hasPowerStatsSupport()).isTrue();
        FiraDecoderTest.verifyFiraSpecification(
                genericSpecificationParams.getFiraSpecificationParams());
        CccDecoderTest.verifyCccSpecification(
                genericSpecificationParams.getCccSpecificationParams());
    }
}
