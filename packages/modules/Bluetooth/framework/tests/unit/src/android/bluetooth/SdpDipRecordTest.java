/*
 * Copyright 2022 The Android Open Source Project
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

package android.bluetooth;

import static com.google.common.truth.Truth.assertThat;

import android.os.Parcel;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test cases for {@link SdpDipRecord}.
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class SdpDipRecordTest {

    @Test
    public void createSdpDipRecord() {
        int specificationId = 1;
        int vendorId = 1;
        int vendorIdSource = 1;
        int productId = 1;
        int version = 1;
        boolean primaryRecord = true;

        SdpDipRecord record = new SdpDipRecord(
                specificationId,
                vendorId,
                vendorIdSource,
                productId,
                version,
                primaryRecord
        );

        assertThat(record.getSpecificationId()).isEqualTo(specificationId);
        assertThat(record.getVendorId()).isEqualTo(vendorId);
        assertThat(record.getVendorIdSource()).isEqualTo(vendorIdSource);
        assertThat(record.getProductId()).isEqualTo(productId);
        assertThat(record.getVersion()).isEqualTo(version);
        assertThat(record.getPrimaryRecord()).isEqualTo(primaryRecord);
    }

    @Test
    public void writeToParcel() {
        int specificationId = 1;
        int vendorId = 1;
        int vendorIdSource = 1;
        int productId = 1;
        int version = 1;
        boolean primaryRecord = true;

        SdpDipRecord originalRecord = new SdpDipRecord(
                specificationId,
                vendorId,
                vendorIdSource,
                productId,
                version,
                primaryRecord
        );

        Parcel parcel = Parcel.obtain();
        originalRecord.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);

        SdpDipRecord recordOut = (SdpDipRecord) SdpDipRecord.CREATOR.createFromParcel(parcel);
        parcel.recycle();

        assertThat(recordOut.getSpecificationId())
                .isEqualTo(originalRecord.getSpecificationId());
        assertThat(recordOut.getVendorId())
                .isEqualTo(originalRecord.getVendorId());
        assertThat(recordOut.getVendorIdSource())
                .isEqualTo(originalRecord.getVendorIdSource());
        assertThat(recordOut.getProductId())
                .isEqualTo(originalRecord.getProductId());
        assertThat(recordOut.getVersion())
                .isEqualTo(originalRecord.getVersion());
        assertThat(recordOut.getPrimaryRecord())
                .isEqualTo(originalRecord.getPrimaryRecord());
    }
}
