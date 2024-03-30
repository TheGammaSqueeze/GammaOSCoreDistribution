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

package android.nearby.cts;

import static com.google.common.truth.Truth.assertThat;

import android.nearby.DataElement;
import android.os.Build;
import android.os.Parcel;

import androidx.annotation.RequiresApi;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SdkSuppress;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;


@RunWith(AndroidJUnit4.class)
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
public class DataElementTest {

    private static final int KEY = 1234;
    private static final byte[] VALUE = new byte[]{1, 1, 1, 1};

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void testBuilder() {
        DataElement dataElement = new DataElement(KEY, VALUE);

        assertThat(dataElement.getKey()).isEqualTo(KEY);
        assertThat(Arrays.equals(dataElement.getValue(), VALUE)).isTrue();
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void testWriteParcel() {
        DataElement dataElement = new DataElement(KEY, VALUE);

        Parcel parcel = Parcel.obtain();
        dataElement.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        DataElement elementFromParcel = DataElement.CREATOR.createFromParcel(
                parcel);
        parcel.recycle();

        assertThat(elementFromParcel.getKey()).isEqualTo(KEY);
        assertThat(Arrays.equals(elementFromParcel.getValue(), VALUE)).isTrue();
    }
}
