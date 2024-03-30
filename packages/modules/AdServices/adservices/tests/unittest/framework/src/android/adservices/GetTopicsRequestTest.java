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
package android.adservices;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.content.AttributionSource;

import androidx.test.filters.SmallTest;

import org.junit.Test;

/** Unit tests for {@link android.adservices.GetTopicsRequest} */
@SmallTest
public final class GetTopicsRequestTest {
    private static final String SOME_PACKAGE_NAME = "SomePackageName";
    private static final String SOME_ATTRIBUTION_TAG = "SomeAttributionTag";
    private static final int SOME_UID = 11;

    @Test
    public void testNonNullAttributionSource() {
        AttributionSource source =
                new AttributionSource.Builder(SOME_UID)
                        .setPackageName(SOME_PACKAGE_NAME)
                        .setAttributionTag(SOME_ATTRIBUTION_TAG)
                        .build();
        GetTopicsRequest request =
                new GetTopicsRequest.Builder().setAttributionSource(source).build();

        AttributionSource source2 = request.getAttributionSource();
        assertThat(source2).isNotNull();
        assertThat(source2.getUid()).isEqualTo(SOME_UID);
        assertThat(source2.getPackageName()).isEqualTo(SOME_PACKAGE_NAME);
        assertThat(source2.getAttributionTag()).isEqualTo(SOME_ATTRIBUTION_TAG);
    }

    @Test
    public void testNullAttributionSource() {
        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    GetTopicsRequest unusedRequest =
                            new GetTopicsRequest.Builder()
                                    // Not setting AttributionSource making it null.
                                    .build();
                });
    }
}
