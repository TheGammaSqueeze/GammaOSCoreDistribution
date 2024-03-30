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

package com.android.server.uwb.secure.csml;

import static com.android.server.uwb.secure.iso7816.Iso7816Constants.EXTENDED_HEAD_LIST;
import static com.android.server.uwb.secure.iso7816.Iso7816Constants.TAG_LIST;

import androidx.annotation.NonNull;

import com.android.server.uwb.secure.iso7816.TlvDatum;
import com.android.server.uwb.secure.iso7816.TlvDatum.Tag;
import com.android.server.uwb.util.ObjectIdentifier;

import com.google.common.primitives.Bytes;

/**
 * Utils created for working with {@link AtomicFile}.
 */
public final class CsmlUtil {
    private CsmlUtil() {}

    static final Tag OID_TAG = new Tag((byte) 0x06);
    private static final Tag TAG_LIST_TAG = new Tag(TAG_LIST);
    private static final Tag EXTENDED_HEAD_LIST_TAG = new Tag(EXTENDED_HEAD_LIST);
    // FiRa CSML 8.2.2.7.1.4
    private static final Tag TERMINATE_SESSION_DO_TAG = new Tag((byte) 0x80);
    private static final Tag TERMINATE_SESSION_TOP_DO_TAG = new Tag((byte) 0xBF, (byte) 0x79);

    /**
     * Encode the {@link ObjectIdentifier} as TLV format, which is used as the payload of TlvDatum
     * @param oid the ObjectIdentifier
     * @return The instance of TlvDatum.
     */
    @NonNull
    public static TlvDatum encodeObjectIdentifierAsTlv(@NonNull ObjectIdentifier oid) {
        return new TlvDatum(OID_TAG, oid.value);
    }

    /**
     * Construct the TLV payload for {@link getDoCommand} with
     * TAG LIST defined in ISO7816-4.
     */
    @NonNull
    public static TlvDatum constructGetDoTlv(@NonNull Tag tag) {
        return new TlvDatum(TAG_LIST_TAG, tag.literalValue);
    }

    /**
     * Get the TLV for terminate session command.
     */
    @NonNull
    public static TlvDatum constructTerminateSessionGetDoTlv() {
        // TODO: confirm the structure defined in CSML 8.2.2.7.1.4, which is not clear.
        byte[] value = constructDeepestTagOfGetDoAllContent(TERMINATE_SESSION_DO_TAG);
        return constructGetOrPutDoTlv(
                new TlvDatum(TERMINATE_SESSION_TOP_DO_TAG, value));
    }

    /**
     * Construct the TLV payload for @link getDoCommand} with
     * EXTENTED HEADER LIST defined in ISO7816-4.
     */
    @NonNull
    public static TlvDatum constructGetOrPutDoTlv(TlvDatum tlvDatum) {
        return new TlvDatum(EXTENDED_HEAD_LIST_TAG, tlvDatum);
    }

    /**
     * Get all content for a specific/deepest Tag in the DO tree with Extented Header List.
     */
    @NonNull
    public static byte[] constructDeepestTagOfGetDoAllContent(Tag tag) {
        return Bytes.concat(tag.literalValue, new byte[] {(byte) 0x00});
    }

    /**
     * Get part of content for a specific/deepest Tag with Extenteed Header List.
     */
    @NonNull
    public static byte[] constructDeepestTagOfGetDoPartContent(Tag tag, int len) {
        if (len > 256) {
            throw new IllegalArgumentException("The content length can not be over 256 bytes");
        }

        return Bytes.concat(tag.literalValue, new byte[] { (byte) len});
    }
}
