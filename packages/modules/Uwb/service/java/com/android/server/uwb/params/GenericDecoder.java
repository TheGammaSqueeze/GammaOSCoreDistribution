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

import static com.android.server.uwb.config.CapabilityParam.SUPPORTED_POWER_STATS_QUERY;

import com.google.uwb.support.base.Params;
import com.google.uwb.support.ccc.CccParams;
import com.google.uwb.support.ccc.CccSpecificationParams;
import com.google.uwb.support.fira.FiraParams;
import com.google.uwb.support.fira.FiraSpecificationParams;
import com.google.uwb.support.generic.GenericSpecificationParams;

public class GenericDecoder extends TlvDecoder {
    @Override
    public <T extends Params> T getParams(TlvDecoderBuffer tlvs, Class<T> paramType) {
        if (GenericSpecificationParams.class.equals(paramType)) {
            return (T) getSpecificationParamsFromTlvBuffer(tlvs);
        }
        return null;
    }

    private GenericSpecificationParams getSpecificationParamsFromTlvBuffer(TlvDecoderBuffer tlvs) {
        GenericSpecificationParams.Builder builder = new GenericSpecificationParams.Builder();
        FiraSpecificationParams firaSpecificationParams =
                TlvDecoder.getDecoder(FiraParams.PROTOCOL_NAME).getParams(
                        tlvs, FiraSpecificationParams.class);
        builder.setFiraSpecificationParams(firaSpecificationParams);
        CccSpecificationParams cccSpecificationParams =
                TlvDecoder.getDecoder(CccParams.PROTOCOL_NAME).getParams(
                        tlvs, CccSpecificationParams.class);
        builder.setCccSpecificationParams(cccSpecificationParams);
        try {
            byte supported_power_stats_query = tlvs.getByte(SUPPORTED_POWER_STATS_QUERY);
            if (supported_power_stats_query != 0) {
                builder.hasPowerStatsSupport(true);
            }
        } catch (IllegalArgumentException e) {
            // Do nothing. By default, hasPowerStatsSupport() returns false.
        }
        return builder.build();
    }
}
