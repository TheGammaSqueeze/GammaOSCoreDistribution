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

import android.os.Parcel;

import androidx.test.filters.SmallTest;

import org.junit.Test;

import java.util.Arrays;

/** Unit tests for {@link android.adservices.GetTopicsResponse} */
@SmallTest
public final class GetTopicsResponseTest {
    @Test
    public void testWriteToParcel() throws Exception {
        GetTopicsResponse response =
                new GetTopicsResponse.Builder()
                        .setTaxonomyVersions(Arrays.asList(1L, 2L))
                        .setModelVersions(Arrays.asList(3L, 4L))
                        .setTopics(Arrays.asList("topic1", "topic2"))
                        .build();
        Parcel p = Parcel.obtain();
        response.writeToParcel(p, 0);
        p.setDataPosition(0);

        GetTopicsResponse fromParcel = GetTopicsResponse.CREATOR.createFromParcel(p);

        assertThat(fromParcel.getTaxonomyVersions()).containsExactly(1L, 2L).inOrder();
        assertThat(fromParcel.getModelVersions()).containsExactly(3L, 4L).inOrder();
        assertThat(fromParcel.getTopics()).containsExactly("topic1", "topic2").inOrder();
    }

    @Test
    public void testWriteToParcel_emptyResponse() throws Exception {
        GetTopicsResponse response = new GetTopicsResponse.Builder().build();
        Parcel p = Parcel.obtain();
        response.writeToParcel(p, 0);
        p.setDataPosition(0);

        GetTopicsResponse fromParcel = GetTopicsResponse.CREATOR.createFromParcel(p);

        assertThat(fromParcel.getTaxonomyVersions()).isEmpty();
        assertThat(fromParcel.getModelVersions()).isEmpty();
        assertThat(fromParcel.getTopics()).isEmpty();
    }

    @Test
    public void testWriteToParcel_nullableThrows() throws Exception {
        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    GetTopicsResponse unusedResponse =
                            new GetTopicsResponse.Builder().setTopics(null).build();
                });

        // This should not throw.
        GetTopicsResponse unusedResponse =
                new GetTopicsResponse.Builder()
                        // Not setting anything default to empty.
                        .build();
    }

    @Test
    public void testWriteToParcel_misMatchSizeThrows() {
        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    GetTopicsResponse unusedResponse =
                            new GetTopicsResponse.Builder()
                                    .setTaxonomyVersions(Arrays.asList(1L))
                                    .setModelVersions(Arrays.asList(3L, 4L))
                                    .setTopics(Arrays.asList("topic1", "topic2"))
                                    .build();
                });

        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    GetTopicsResponse unusedResponse =
                            new GetTopicsResponse.Builder()
                                    // Not setting TaxonomyVersions implies empty.
                                    .setModelVersions(Arrays.asList(3L, 4L))
                                    .setTopics(Arrays.asList("topic1", "topic2"))
                                    .build();
                });

        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    GetTopicsResponse unusedResponse =
                            new GetTopicsResponse.Builder()
                                    .setTaxonomyVersions(Arrays.asList(1L, 2L))
                                    .setModelVersions(Arrays.asList(3L, 4L))
                                    .setTopics(Arrays.asList("topic1"))
                                    .build();
                });
    }
}
