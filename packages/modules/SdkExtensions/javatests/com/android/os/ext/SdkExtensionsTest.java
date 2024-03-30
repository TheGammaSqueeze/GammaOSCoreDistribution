/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.os.ext;

import static android.os.Build.VERSION_CODES;
import static android.os.Build.VERSION_CODES.R;
import static android.os.Build.VERSION_CODES.S;
import static android.os.Build.VERSION_CODES.TIRAMISU;
import static com.android.os.ext.testing.CurrentVersion.ALLOWED_VERSIONS_CTS;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeTrue;

import android.os.SystemProperties;
import android.os.ext.SdkExtensions;
import androidx.test.runner.AndroidJUnit4;
import com.android.modules.utils.build.SdkLevel;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class SdkExtensionsTest {

    private static final int AD_SERVICES = 1000000;

    private static void assertCorrectVersion(int version) {
        assertThat(version).isIn(ALLOWED_VERSIONS_CTS);
    }

    private static void assertCorrectVersion(boolean expected, int version) {
        if (expected) {
            assertCorrectVersion(version);
        } else {
            assertEquals(0, version);
        }
    }

    private static void assertCorrectVersion(boolean expected, String propValue) {
        if (expected) {
            int version = Integer.parseInt(propValue);
            assertCorrectVersion(version);
        } else {
            assertEquals("", propValue);
        }
    }

    private static final void assertCorrectVersion(boolean expected, int extension, String propId) {
        String prop = "build.version.extensions." + propId;
        assertCorrectVersion(expected, SystemProperties.get(prop));
        assertCorrectVersion(expected, SdkExtensions.getExtensionVersion(extension));
        if (expected) {
            assertCorrectVersion(true, SdkExtensions.getAllExtensionVersions().get(extension));
        }
    }

    /** Verify that getExtensionVersion only accepts valid extension SDKs */
    @Test
    public void testBadArgument() throws Exception {
        // R is the first SDK version with extensions.
        for (int sdk = -1_000_000; sdk < VERSION_CODES.R; sdk++) {
            final int finalSdk = sdk;
            assertThrows(IllegalArgumentException.class,
                    () -> SdkExtensions.getExtensionVersion(finalSdk));
        }
    }

    /** Verifies that getExtensionVersion returns zero value for non-existing extensions */
    @Test
    public void testZeroValues() throws Exception {
        Set<Integer> assignedCodes = Set.of(R, S, TIRAMISU, AD_SERVICES);
        for (int sdk = VERSION_CODES.R; sdk <= 1_000_000; sdk++) {
            if (assignedCodes.contains(sdk)) {
                continue;
            }
            // No extension SDKs yet.
            assertEquals(0, SdkExtensions.getExtensionVersion(sdk));
        }
    }

    @Test
    public void testGetAllExtensionVersionsKeys() throws Exception {
        Set<Integer> expectedKeys = new HashSet<>();
        expectedKeys.add(VERSION_CODES.R);
        if (SdkLevel.isAtLeastS()) {
            expectedKeys.add(VERSION_CODES.S);
        }
        if (SdkLevel.isAtLeastT()) {
            expectedKeys.add(VERSION_CODES.TIRAMISU);
            if (SdkExtensions.getExtensionVersion(VERSION_CODES.S) >= 4) {
                // The AdServices extension was added in v4 (2022-11). Note that we check the
                // S extension version in the conditional above because the 22-11-Go train did
                // not include AdServices, so the T extension version remains 3 (S is 4 though).
                expectedKeys.add(AD_SERVICES);
            }
        }
        Set<Integer> actualKeys = SdkExtensions.getAllExtensionVersions().keySet();
        assertThat(actualKeys).containsExactlyElementsIn(expectedKeys);
    }

    @Test
    public void testExtensionR() {
        assertCorrectVersion(true, R, "r");
    }

    @Test
    public void testExtensionS() {
        assertCorrectVersion(SdkLevel.isAtLeastS(), S, "s");
    }

    @Test
    public void testExtensionT() {
        assertCorrectVersion(SdkLevel.isAtLeastT(), TIRAMISU, "t");
    }

    @Test
    public void testExtensionAdServices() {
        // The AdServices extension was added in v4.
        assumeTrue(SdkExtensions.getExtensionVersion(TIRAMISU) >= 4);

        assertCorrectVersion(SdkLevel.isAtLeastT(), AD_SERVICES, "ad_services");
    }

}
