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

package android.car.cts;

import static android.os.Build.VERSION_CODES.TIRAMISU;

import static com.google.common.truth.Truth.assertWithMessage;

import android.car.PlatformVersion;
import android.car.annotation.ApiRequirements;
import android.os.Parcel;

import com.android.compatibility.common.util.ApiTest;

import org.junit.Test;

public final class PlatformVersionTest extends AbstractCarLessTestCase {

    @Test
    @ApiTest(apis = {"android.car.PlatformVersion.VERSION_CODES#TIRAMISU_0"})
    public void testTiramisu_0() {
        PlatformVersion version = PlatformVersion.VERSION_CODES.TIRAMISU_0;

        assertWithMessage("TIRAMISU_0").that(version).isNotNull();
        expectWithMessage("TIRAMISU_0.major").that(version.getMajorVersion())
                .isEqualTo(TIRAMISU);
        expectWithMessage("TIRAMISU_0.minor").that(version.getMinorVersion())
                .isEqualTo(0);

        PlatformVersion fromEnum = ApiRequirements.PlatformVersion.TIRAMISU_0.get();
        assertWithMessage("TIRAMISU_0 from enum").that(fromEnum).isNotNull();
        expectWithMessage("TIRAMISU_0 from enum").that(fromEnum).isSameInstanceAs(version);

        String toString = version.toString();
        expectWithMessage("TIRAMISU_0.toString()").that(toString)
                .matches(".*PlatformVersion.*name=TIRAMISU_0.*major=" + TIRAMISU + ".*minor=0.*");
        PlatformVersion clone = clone(version);
        expectWithMessage("TIRAMISU_0.toString() from parcel").that(clone.toString())
                .isEqualTo(toString);

        PlatformVersion anonymous = PlatformVersion.forMajorAndMinorVersions(
                version.getMajorVersion(), version.getMinorVersion());
        expectWithMessage("TIRAMISU_0").that(version).isEqualTo(anonymous);
        expectWithMessage("anonymous").that(anonymous).isEqualTo(version);
        expectWithMessage("TIRAMISU_0's hashcode").that(version.hashCode())
                .isEqualTo(anonymous.hashCode());
        expectWithMessage("anonymous' hashcode").that(anonymous.hashCode())
                .isEqualTo(version.hashCode());
    }

    @Test
    @ApiTest(apis = {"android.car.PlatformVersion.VERSION_CODES#TIRAMISU_1"})
    public void testTiramisu_1() {
        PlatformVersion version = PlatformVersion.VERSION_CODES.TIRAMISU_1;

        assertWithMessage("TIRAMISU_1").that(version).isNotNull();
        expectWithMessage("TIRAMISU_1.major").that(version.getMajorVersion())
                .isEqualTo(TIRAMISU);
        expectWithMessage("TIRAMISU_1.minor").that(version.getMinorVersion())
                .isEqualTo(1);

        PlatformVersion fromEnum = ApiRequirements.PlatformVersion.TIRAMISU_1.get();
        assertWithMessage("TIRAMISU_1 from enum").that(fromEnum).isNotNull();
        expectWithMessage("TIRAMISU_1 from enum").that(fromEnum).isSameInstanceAs(version);

        String toString = version.toString();
        expectWithMessage("TIRAMISU_1.toString()").that(toString)
                .matches(".*PlatformVersion.*name=TIRAMISU_1.*major=" + TIRAMISU + ".*minor=1.*");
        PlatformVersion clone = clone(version);
        expectWithMessage("TIRAMISU_1.toString() from parcel").that(clone.toString())
                .isEqualTo(toString);

        PlatformVersion anonymous = PlatformVersion.forMajorAndMinorVersions(
                version.getMajorVersion(), version.getMinorVersion());
        expectWithMessage("TIRAMISU_1").that(version).isEqualTo(anonymous);
        expectWithMessage("anonymous").that(anonymous).isEqualTo(version);
        expectWithMessage("TIRAMISU_1's hashcode").that(version.hashCode())
                .isEqualTo(anonymous.hashCode());
        expectWithMessage("anonymous' hashcode").that(anonymous.hashCode())
                .isEqualTo(version.hashCode());
    }

    @Test
    @ApiTest(apis = {"android.car.PlatformVersion#CREATOR"})
    public void testMarshalling() {
        PlatformVersion original = PlatformVersion.forMajorAndMinorVersions(66, 6);
        expectWithMessage("original.describeContents()").that(original.describeContents())
                .isEqualTo(0);

        PlatformVersion clone = clone(original);
        assertWithMessage("CREATOR.createFromParcel()").that(clone).isNotNull();
        expectWithMessage("clone.major").that(clone.getMajorVersion()).isEqualTo(66);
        expectWithMessage("clone.minor").that(clone.getMinorVersion()).isEqualTo(6);
        expectWithMessage("clone.describeContents()").that(clone.describeContents())
                .isEqualTo(0);
        expectWithMessage("clone.equals()").that(clone).isEqualTo(original);
        expectWithMessage("clone.hashCode()").that(clone.hashCode()).isEqualTo(original.hashCode());
    }

    @Test
    @ApiTest(apis = {"android.car.PlatformVersion#CREATOR"})
    public void testNewArray() {
        PlatformVersion[] array = PlatformVersion.CREATOR.newArray(42);

        assertWithMessage("CREATOR.newArray()").that(array).isNotNull();
    }

    private PlatformVersion clone(PlatformVersion original) {
        Parcel parcel =  Parcel.obtain();
        try {
            original.writeToParcel(parcel, /* flags= */ 0);
            parcel.setDataPosition(0);

            return PlatformVersion.CREATOR.createFromParcel(parcel);
        } finally {
            parcel.recycle();
        }
    }

}
