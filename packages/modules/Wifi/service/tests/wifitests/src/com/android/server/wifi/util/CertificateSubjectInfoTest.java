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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import androidx.test.filters.SmallTest;

import com.android.server.wifi.WifiBaseTest;

import org.junit.Test;

/**
 * Unit tests for {@link com.android.server.wifi.util.CertificateSubjectInfo}.
 */
@SmallTest
public class CertificateSubjectInfoTest extends WifiBaseTest {

    private static final String TEST_SUBJECT_INFO =
            "CN=androidwifi.dev,1.2.840.113549.1.9.1=#1614696e666f40616e64726f6964776966692e646576,"
            + "O=AndroidWiFi,L=TW,ST=Taiwan,C=TW";
    private static final String TEST_COMMON_NAME = "androidwifi.dev";
    private static final String TEST_COUNTRY = "TW";
    private static final String TEST_STATE = "Taiwan";
    private static final String TEST_LOCATION = "TW";
    private static final String TEST_ORGANIZATION = "AndroidWiFi";
    private static final String TEST_EMAIL = "info@androidwifi.dev";

    /** Verifies that normal subject string could be parsed correctly. */
    @Test
    public void verifyNormalSubjectInfoString() {
        CertificateSubjectInfo info = CertificateSubjectInfo.parse(TEST_SUBJECT_INFO);
        assertEquals(info.commonName, TEST_COMMON_NAME);
        assertEquals(info.organization, TEST_ORGANIZATION);
        assertEquals(info.location, TEST_LOCATION);
        assertEquals(info.state, TEST_STATE);
        assertEquals(info.country, TEST_COUNTRY);
        assertEquals(info.email, TEST_EMAIL);
    }

    /** Verifies that null is returned for a subject string without a common name. */
    @Test
    public void verifySubjectInfoStringWithoutCommonName() {
        final String subjectInfo =
                "C=TW,ST=Taiwan,L=TW,O=AndroidWiFi";
        assertNull(CertificateSubjectInfo.parse(subjectInfo));
    }

    /** Verifies that escaped string can be restored correctly. */
    @Test
    public void testEscpaedSubjectString() {
        final String subjectInfo =
                "C=TW,ST=Taiwan,L=TW,O=AndroidWiFi,CN=commonName/emailAddress\\=test@wifi.com";
        CertificateSubjectInfo info = CertificateSubjectInfo.parse(subjectInfo);
        assertEquals("commonName/emailAddress=test@wifi.com", info.commonName);
    }

    /** Verifies that escaped string can be restored correctly. */
    @Test
    public void testEscpaedSubjectStringWithDoubleTrailingSlash() {
        final String subjectInfo =
                "C=TW,ST=Taiwan,L=TW,O=AndroidWiFi,CN=commonName/emailAddress=test@wifi.com\\\\";
        CertificateSubjectInfo info = CertificateSubjectInfo.parse(subjectInfo);
        assertEquals("commonName/emailAddress=test@wifi.com\\", info.commonName);
    }

    /** Verifies that invalid escaped string returns null. */
    @Test
    public void testInvalidEscpaedSubjectString() {
        // trailing '\'
        assertNull(CertificateSubjectInfo.parse(
                "C=TW,ST=Taiwan,L=TW,O=AndroidWiFi,CN=commonName/emailAddress\\=test@wifi.com\\"));
        // illegal escaped character 'z'
        assertNull(CertificateSubjectInfo.parse(
                "C=TW,ST=Taiwan,L=TW,O=AndroidWiFi,CN=Name/email\\=\\ztest@wifi.com"));
    }
}
