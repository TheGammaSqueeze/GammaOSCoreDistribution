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

package com.android.server.wifi.util;

import android.net.wifi.util.HexEncoding;
import android.text.TextUtils;
import android.util.Log;

import java.nio.charset.StandardCharsets;

/** Utilities for parsing information from a certificate subject. */
public class CertificateSubjectInfo {
    private static final String TAG = "CertificateSubjectInfo";
    private static final String COMMON_NAME_PREFIX = "CN=";
    private static final String ORGANIZATION_PREFIX = "O=";
    private static final String LOCATION_PREFIX = "L=";
    private static final String STATE_PREFIX = "ST=";
    private static final String COUNTRY_PREFIX = "C=";
    // This is hex-encoded string.
    private static final String EMAILADDRESS_OID_PREFIX = "1.2.840.113549.1.9.1=#1614";

    public String rawData = "";
    public String commonName = "";
    public String organization = "";
    public String location = "";
    public String state = "";
    public String country = "";
    public String email = "";

    private CertificateSubjectInfo() {
    }

    /**
     * Parse the subject of a certificate.
     *
     * @param subject the subject string
     * @return CertificateSubjectInfo object if the subject is valid; otherwise, null.
     */
    public static CertificateSubjectInfo parse(String subject) {
        CertificateSubjectInfo info = new CertificateSubjectInfo();
        info.rawData = unescapeString(subject);
        if (null == info.rawData) return null;

        String[] parts = info.rawData.split(",");
        for (String s : parts) {
            if (s.startsWith(COMMON_NAME_PREFIX)) {
                info.commonName = s.substring(COMMON_NAME_PREFIX.length());
            } else if (s.startsWith(ORGANIZATION_PREFIX)) {
                info.organization = s.substring(ORGANIZATION_PREFIX.length());
            } else if (s.startsWith(LOCATION_PREFIX)) {
                info.location = s.substring(LOCATION_PREFIX.length());
            } else if (s.startsWith(STATE_PREFIX)) {
                info.state = s.substring(STATE_PREFIX.length());
            } else if (s.startsWith(COUNTRY_PREFIX)) {
                info.country = s.substring(COUNTRY_PREFIX.length());
            } else if (s.startsWith(EMAILADDRESS_OID_PREFIX)) {
                String hexStr = s.substring(EMAILADDRESS_OID_PREFIX.length());
                try {
                    info.email = new String(
                            HexEncoding.decode(hexStr.toCharArray(), false),
                            StandardCharsets.UTF_8);
                } catch (IllegalArgumentException ex) {
                    Log.w(TAG, "failed to decode email: " + ex);
                }
            } else {
                Log.d(TAG, "Unhandled subject info: " + s);
            }
        }
        return TextUtils.isEmpty(info.commonName) ? null : info;
    }

    /**
     * The characters in a subject string will be escaped based on RFC2253.
     * To restore the original string, this method unescapes escaped
     * characters.
     */
    private static String unescapeString(String s) {
        final String escapees = ",=+<>#;\"\\";
        StringBuilder res = new StringBuilder();
        char[] chars = s.toCharArray();
        boolean isEscaped = false;
        for (char c: chars) {
            if (c == '\\' && !isEscaped) {
                isEscaped = true;
                continue;
            }
            // An illegal escaped character is founded.
            if (isEscaped && escapees.indexOf(c) == -1) {
                Log.d(TAG, "Unable to unescape string: " + s);
                return null;
            }
            res.append(c);
            isEscaped = false;
        }
        // There is a trailing '\' without a escaped character.
        if (isEscaped) {
            Log.d(TAG, "Unable to unescape string: " + s);
            return null;
        }
        return res.toString();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Raw=").append(rawData)
            .append(", Common Name=").append(commonName)
            .append(", Organization=").append(organization)
            .append(", Location=").append(location)
            .append(", State=").append(state)
            .append(", Country=").append(country)
            .append(", Contact=").append(email);
        return sb.toString();
    }
}
