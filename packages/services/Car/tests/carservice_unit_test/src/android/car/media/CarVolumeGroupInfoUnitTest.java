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

package android.car.media;

import static org.junit.Assert.assertThrows;

import android.car.test.AbstractExpectableTestCase;
import android.os.Parcel;

import org.junit.Test;

public final class CarVolumeGroupInfoUnitTest extends AbstractExpectableTestCase {

    private static final int TEST_ZONE_ID = 8;
    private static final int TEST_PRIMARY_GROUP_ID = 7;
    private static final String TEST_GROUP_NAME = "3";
    private static final int TEST_PARCEL_FLAGS = 0;
    private static final int TEST_CURRENT_GAIN = 9_000;
    private static final boolean TEST_DEFAULT_MUTE_STATE = false;
    private static final boolean TEST_DEFAULT_BLOCKED_STATE = false;
    private static final boolean TEST_DEFAULT_ATTENUATED_STATE = false;
    public static final int TEST_MAX_GAIN_INDEX = 9_005;
    public static final int TEST_MIN_GAIN_INDEX = 0;

    private static final CarVolumeGroupInfo TEST_VOLUME_INFO =
            new CarVolumeGroupInfo.Builder(TEST_GROUP_NAME, TEST_ZONE_ID,
            TEST_PRIMARY_GROUP_ID).setMaxVolumeGainIndex(TEST_MAX_GAIN_INDEX)
                    .setMinVolumeGainIndex(TEST_MIN_GAIN_INDEX).build();

    private final CarVolumeGroupInfo.Builder mTestGroupInfoBuilder =
            new CarVolumeGroupInfo.Builder(TEST_GROUP_NAME,
            TEST_ZONE_ID, TEST_PRIMARY_GROUP_ID).setAttenuated(TEST_DEFAULT_ATTENUATED_STATE)
                    .setMaxVolumeGainIndex(TEST_MAX_GAIN_INDEX)
                    .setMinVolumeGainIndex(TEST_MIN_GAIN_INDEX)
                    .setVolumeGainIndex(TEST_CURRENT_GAIN).setBlocked(TEST_DEFAULT_BLOCKED_STATE)
                    .setMuted(TEST_DEFAULT_MUTE_STATE);

    @Test
    public void build_buildsGroupInfo() {
        CarVolumeGroupInfo info = new CarVolumeGroupInfo
                .Builder(TEST_GROUP_NAME, TEST_ZONE_ID, TEST_PRIMARY_GROUP_ID)
                .setMaxVolumeGainIndex(TEST_MAX_GAIN_INDEX)
                .setMinVolumeGainIndex(TEST_MIN_GAIN_INDEX)
                .setVolumeGainIndex(TEST_CURRENT_GAIN).build();

        expectWithMessage("Car volume info build info zone id")
                .that(info.getZoneId()).isEqualTo(TEST_ZONE_ID);
        expectWithMessage("Car volume info build info group id")
                .that(info.getId()).isEqualTo(TEST_PRIMARY_GROUP_ID);
        expectWithMessage("Car volume info build info group name")
                .that(info.getName()).isEqualTo(TEST_GROUP_NAME);
    }

    @Test
    public void build_withNullName_fails() {
        NullPointerException thrown = assertThrows(NullPointerException.class, () ->
                new CarVolumeGroupInfo.Builder(/* name= */ null,
                        TEST_ZONE_ID, TEST_PRIMARY_GROUP_ID)
        );

        expectWithMessage("Null volume info name exception")
                .that(thrown).hasMessageThat().contains("Volume info name");
    }

    @Test
    public void setVolumeGainIndex_buildsGroupInfo() {
        CarVolumeGroupInfo info = mTestGroupInfoBuilder.setVolumeGainIndex(9_001).build();

        expectWithMessage("Car volume info gain")
                .that(info.getVolumeGainIndex()).isEqualTo(9_001);
    }

    @Test
    public void setMinVolumeGainIndex_buildsGroupInfo() {
        CarVolumeGroupInfo info = mTestGroupInfoBuilder.setMinVolumeGainIndex(10).build();

        expectWithMessage("Car volume info min gain")
                .that(info.getMinVolumeGainIndex()).isEqualTo(10);
    }

    @Test
    public void setMaxVolumeGainIndex_buildsGroupInfo() {
        CarVolumeGroupInfo info = mTestGroupInfoBuilder.setMaxVolumeGainIndex(9_002).build();

        expectWithMessage("Car volume info max gain")
                .that(info.getMaxVolumeGainIndex()).isEqualTo(9_002);
    }

    @Test
    public void setMaxVolumeGainIndex_withMinLargerThanMax_buildFails() {
        CarVolumeGroupInfo.Builder infoBuilder =
                mTestGroupInfoBuilder.setMinVolumeGainIndex(9003)
                        .setMaxVolumeGainIndex(9_002);

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> infoBuilder.build());

        expectWithMessage("Max volume gain smaller than min gain exception")
                .that(thrown).hasMessageThat().contains("must be smaller than max");
    }

    @Test
    public void setVolumeGainIndex_withGainOutOfMinMaxRange_buildFails() {
        CarVolumeGroupInfo.Builder infoBuilder =
                mTestGroupInfoBuilder.setMinVolumeGainIndex(10)
                        .setMaxVolumeGainIndex(100).setVolumeGainIndex(0);

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> infoBuilder.build());

        expectWithMessage("Volume gain index out of range exception")
                .that(thrown).hasMessageThat().contains("out of range");
    }

    @Test
    public void setMuted_buildsGroupInfo() {
        CarVolumeGroupInfo info = mTestGroupInfoBuilder.setMuted(true).build();

        expectWithMessage("Car volume info mute state")
                .that(info.isMuted()).isTrue();
    }

    @Test
    public void setAttenuated_buildsGroupInfo() {
        CarVolumeGroupInfo info = mTestGroupInfoBuilder.setAttenuated(true).build();

        expectWithMessage("Car volume info attenuated state")
                .that(info.isAttenuated()).isTrue();
    }

    @Test
    public void setBlocked_buildsGroupInfo() {
        CarVolumeGroupInfo info = mTestGroupInfoBuilder.setBlocked(true).build();

        expectWithMessage("Car volume info blocked state")
                .that(info.isBlocked()).isTrue();
    }

    @Test
    public void builder_withReuse_fails() {
        CarVolumeGroupInfo.Builder builder = new CarVolumeGroupInfo.Builder(TEST_GROUP_NAME,
                TEST_ZONE_ID, TEST_PRIMARY_GROUP_ID)
                .setMaxVolumeGainIndex(TEST_MAX_GAIN_INDEX)
                .setMinVolumeGainIndex(TEST_MIN_GAIN_INDEX)
                .setVolumeGainIndex(TEST_CURRENT_GAIN);
        builder.build();

        IllegalStateException thrown = assertThrows(IllegalStateException.class, () ->
                builder.build()
        );

        expectWithMessage("Reuse builder exception")
                .that(thrown).hasMessageThat().contains("should not be reused");
    }

    @Test
    public void writeToParcel() {
        Parcel parcel = Parcel.obtain();

        TEST_VOLUME_INFO.writeToParcel(parcel, TEST_PARCEL_FLAGS);
        parcel.setDataPosition(/* position= */ 0);

        expectWithMessage("Car volume info from parcel")
                .that(new CarVolumeGroupInfo(parcel)).isEqualTo(TEST_VOLUME_INFO);
    }

    @Test
    public void createFromParcel() {
        Parcel parcel = Parcel.obtain();
        TEST_VOLUME_INFO.writeToParcel(parcel, TEST_PARCEL_FLAGS);
        parcel.setDataPosition(/* position= */ 0);

        expectWithMessage("Car volume info created from parcel")
                .that(CarVolumeGroupInfo.CREATOR.createFromParcel(parcel))
                .isEqualTo(TEST_VOLUME_INFO);
    }


    @Test
    public void newArray() {
        CarVolumeGroupInfo[] infos = CarVolumeGroupInfo.CREATOR.newArray(/* size= */ 3);

        expectWithMessage("Car volume infos size").that(infos)
                .hasLength(3);
    }

    @Test
    public void equals_forSameContent() {
        CarVolumeGroupInfo infoWithSameContent =
                new CarVolumeGroupInfo.Builder(TEST_VOLUME_INFO)
                        .setMaxVolumeGainIndex(TEST_MAX_GAIN_INDEX)
                        .setMinVolumeGainIndex(TEST_MIN_GAIN_INDEX).build();

        expectWithMessage("Car volume info with same content")
                .that(infoWithSameContent).isEqualTo(TEST_VOLUME_INFO);
    }

    @Test
    public void equals_forNull() {
        CarVolumeGroupInfo info = new CarVolumeGroupInfo.Builder(TEST_GROUP_NAME, TEST_ZONE_ID,
                TEST_PRIMARY_GROUP_ID).setMaxVolumeGainIndex(TEST_MAX_GAIN_INDEX)
                .setMinVolumeGainIndex(TEST_MIN_GAIN_INDEX)
                .setVolumeGainIndex(TEST_CURRENT_GAIN).build();

        expectWithMessage("Car volume info null content")
                .that(info.equals(null)).isFalse();
    }

    @Test
    public void describeContents() {
        CarVolumeGroupInfo info = new CarVolumeGroupInfo.Builder(TEST_GROUP_NAME, TEST_ZONE_ID,
                TEST_PRIMARY_GROUP_ID).setMaxVolumeGainIndex(TEST_MAX_GAIN_INDEX)
                .setMinVolumeGainIndex(TEST_MIN_GAIN_INDEX)
                .setVolumeGainIndex(TEST_CURRENT_GAIN).build();

        expectWithMessage("Car volume info contents")
                .that(info.describeContents()).isEqualTo(0);
    }

    @Test
    public void hashCode_forSameContent() {
        CarVolumeGroupInfo infoWithSameContent = new CarVolumeGroupInfo.Builder(TEST_VOLUME_INFO)
                .setMaxVolumeGainIndex(TEST_MAX_GAIN_INDEX)
                .setMinVolumeGainIndex(TEST_MIN_GAIN_INDEX)
                .build();

        expectWithMessage("Car volume info hash with same content")
                .that(infoWithSameContent.hashCode()).isEqualTo(TEST_VOLUME_INFO.hashCode());
    }

    @Test
    public void toString_forContent() {
        CarVolumeGroupInfo info = new CarVolumeGroupInfo.Builder(TEST_GROUP_NAME, TEST_ZONE_ID,
                TEST_PRIMARY_GROUP_ID).setMaxVolumeGainIndex(TEST_MAX_GAIN_INDEX)
                .setMinVolumeGainIndex(TEST_MIN_GAIN_INDEX)
                .setVolumeGainIndex(TEST_CURRENT_GAIN).build();

        expectWithMessage("Car volume info name")
                .that(info.toString()).contains(TEST_GROUP_NAME);
        expectWithMessage("Car volume info group id")
                .that(info.toString()).contains(Integer.toString(TEST_PRIMARY_GROUP_ID));
        expectWithMessage("Car volume info group zone")
                .that(info.toString()).contains(Integer.toString(TEST_ZONE_ID));
    }
}
