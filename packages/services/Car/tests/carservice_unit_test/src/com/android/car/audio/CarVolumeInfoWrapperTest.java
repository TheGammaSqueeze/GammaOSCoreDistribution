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

package com.android.car.audio;

import static android.car.media.CarAudioManager.PRIMARY_AUDIO_ZONE;
import static android.media.AudioAttributes.USAGE_ALARM;
import static android.media.AudioAttributes.USAGE_MEDIA;

import static com.android.car.audio.CarAudioContext.CALL;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.car.media.CarVolumeGroupInfo;
import android.media.AudioAttributes;
import android.telephony.TelephonyManager;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

@RunWith(AndroidJUnit4.class)
public final class CarVolumeInfoWrapperTest {

    private static final int TEST_GROUP_ID = 0;
    private static final int TEST_SECONDARY_GROUP = 1;
    private static final int TEST_GROUP_VOLUME = 5;
    private static final int TEST_MIN_GROUP_VOLUME = 0;
    private static final int TEST_MAX_GROUP_VOLUME = 9000;
    private static final boolean TEST_VOLUME_GROUP_MUTE = true;
    private static final CarVolumeGroupInfo TEST_PRIMARY_GROUP_INFO =
            new CarVolumeGroupInfo.Builder("group id " + TEST_GROUP_ID, PRIMARY_AUDIO_ZONE,
                    TEST_GROUP_ID).setMaxVolumeGainIndex(TEST_MAX_GROUP_VOLUME)
                    .setMinVolumeGainIndex(TEST_MIN_GROUP_VOLUME).build();

    private static final CarVolumeGroupInfo TEST_SECONDARY_VOLUME_INFO =
            new CarVolumeGroupInfo.Builder("group id " + TEST_SECONDARY_GROUP,
                    PRIMARY_AUDIO_ZONE, TEST_SECONDARY_GROUP)
                    .setMaxVolumeGainIndex(TEST_MAX_GROUP_VOLUME)
                    .setMinVolumeGainIndex(TEST_MIN_GROUP_VOLUME).build();
    private static final AudioAttributes TEST_MEDIA_AUDIO_ATTRIBUTE =
            new AudioAttributes.Builder().setUsage(USAGE_MEDIA).build();
    private static final AudioAttributes TEST_ALARM_AUDIO_ATTRIBUTE =
            new AudioAttributes.Builder().setUsage(USAGE_ALARM).build();

    private CarVolumeInfoWrapper mCarVolumeInfoWrapper;

    @Before
    public void setUp() {
        CarAudioService carAudioService = mock(CarAudioService.class);

        when(carAudioService.getSuggestedAudioContextForPrimaryZone()).thenReturn(CALL);
        when(carAudioService.getVolumeGroupIdForAudioContext(PRIMARY_AUDIO_ZONE, CALL))
                .thenReturn(TEST_GROUP_ID);
        when(carAudioService.getVolumeGroupIdForAudioAttribute(PRIMARY_AUDIO_ZONE,
                TEST_MEDIA_AUDIO_ATTRIBUTE)).thenReturn(TEST_GROUP_ID);
        when(carAudioService.getGroupVolume(PRIMARY_AUDIO_ZONE, TEST_GROUP_ID))
                .thenReturn(TEST_GROUP_VOLUME);
        when(carAudioService.getGroupMinVolume(PRIMARY_AUDIO_ZONE, TEST_GROUP_ID))
                .thenReturn(TEST_MIN_GROUP_VOLUME);
        when(carAudioService.getGroupMaxVolume(PRIMARY_AUDIO_ZONE, TEST_GROUP_ID))
                .thenReturn(TEST_MAX_GROUP_VOLUME);
        when(carAudioService.isVolumeGroupMuted(PRIMARY_AUDIO_ZONE, TEST_GROUP_ID))
                .thenReturn(TEST_VOLUME_GROUP_MUTE);
        when(carAudioService.getMutedVolumeGroups(PRIMARY_AUDIO_ZONE))
                .thenReturn(List.of(TEST_PRIMARY_GROUP_INFO));
        when(carAudioService.getVolumeGroupInfo(PRIMARY_AUDIO_ZONE, TEST_SECONDARY_GROUP))
                .thenReturn(TEST_SECONDARY_VOLUME_INFO);
        when(carAudioService.getVolumeGroupInfosForZone(PRIMARY_AUDIO_ZONE))
                .thenReturn(List.of(TEST_PRIMARY_GROUP_INFO, TEST_SECONDARY_VOLUME_INFO));
        when(carAudioService.getActiveAudioAttributesForZone(PRIMARY_AUDIO_ZONE))
                .thenReturn(List.of(TEST_MEDIA_AUDIO_ATTRIBUTE, TEST_ALARM_AUDIO_ATTRIBUTE));
        when(carAudioService.getCallStateForZone(PRIMARY_AUDIO_ZONE))
                .thenReturn(TelephonyManager.CALL_STATE_OFFHOOK);

        mCarVolumeInfoWrapper = new CarVolumeInfoWrapper(carAudioService);
    }

    @Test
    public void constructor_withNullServices_fails() {
        NullPointerException thrown = assertThrows(NullPointerException.class, () ->
                new CarVolumeInfoWrapper(null));

        assertWithMessage("Car Audio Info Construction")
                .that(thrown).hasMessageThat().contains("Car Audio Service");
    }

    @Test
    public void getSuggestedAudioContextForPrimaryZone_returnsAudioContext() {
        int context = mCarVolumeInfoWrapper.getSuggestedAudioContextForPrimaryZone();

        assertWithMessage("Car Audio Context")
                .that(context).isEqualTo(CALL);
    }

    @Test
    public void getVolumeGroupIdForAudioContext_returnsGroupIdForContext() {
        int groupId = mCarVolumeInfoWrapper
                .getVolumeGroupIdForAudioZone(PRIMARY_AUDIO_ZONE);

        assertWithMessage("Car Audio Group Id")
                .that(groupId).isEqualTo(TEST_GROUP_ID);
    }

    @Test
    public void getVolumeGroupIdForAudioAttribute_returnsGroupId() {
        int groupId = mCarVolumeInfoWrapper
                .getVolumeGroupIdForAudioAttribute(PRIMARY_AUDIO_ZONE, TEST_MEDIA_AUDIO_ATTRIBUTE);

        assertWithMessage("Car Audio Group Id for Media Audio Attribute")
                .that(groupId).isEqualTo(TEST_GROUP_ID);
    }

    @Test
    public void getGroupVolume_returnsVolumeGroup() {
        int groupVolume = mCarVolumeInfoWrapper.getGroupVolume(PRIMARY_AUDIO_ZONE, TEST_GROUP_ID);

        assertWithMessage("Current Car Audio Group Volume")
                .that(groupVolume).isEqualTo(TEST_GROUP_VOLUME);
    }

    @Test
    public void getGroupMinVolume_returnsMinVolume() {
        int groupMinVolume = mCarVolumeInfoWrapper
                .getGroupMinVolume(PRIMARY_AUDIO_ZONE, TEST_GROUP_ID);

        assertWithMessage("Car Audio Group Min Volume")
                .that(groupMinVolume).isEqualTo(TEST_MIN_GROUP_VOLUME);
    }

    @Test
    public void getGroupMaxVolume_returnsMaxVolume() {
        int groupMaxVolume = mCarVolumeInfoWrapper
                .getGroupMaxVolume(PRIMARY_AUDIO_ZONE, TEST_GROUP_ID);

        assertWithMessage("Car Audio Group Max Volume")
                .that(groupMaxVolume).isEqualTo(TEST_MAX_GROUP_VOLUME);
    }

    @Test
    public void isVolumeGroupMuted_returnsGroupMuteState() {
        boolean isVolumeGroupMuted = mCarVolumeInfoWrapper
                .isVolumeGroupMuted(PRIMARY_AUDIO_ZONE, TEST_GROUP_ID);

        assertWithMessage("Car Audio Group Volume Mute")
                .that(isVolumeGroupMuted).isEqualTo(TEST_VOLUME_GROUP_MUTE);
    }

    @Test
    public void getMutedVolumeGroups_withMutedGroups() {
        assertWithMessage("Muted volume groups")
                .that(mCarVolumeInfoWrapper.getMutedVolumeGroups(PRIMARY_AUDIO_ZONE))
                .containsExactly(TEST_PRIMARY_GROUP_INFO);
    }

    @Test
    public void getVolumeGroupInfo() {
        assertWithMessage("Car volume group volume group")
                .that(mCarVolumeInfoWrapper.getVolumeGroupInfo(PRIMARY_AUDIO_ZONE,
                        TEST_SECONDARY_GROUP)).isEqualTo(TEST_SECONDARY_VOLUME_INFO);
    }

    @Test
    public void getVolumeGroupInfosForZone() {
        assertWithMessage("Car volume group volume groups")
                .that(mCarVolumeInfoWrapper.getVolumeGroupInfosForZone(PRIMARY_AUDIO_ZONE))
                .containsExactly(TEST_PRIMARY_GROUP_INFO, TEST_SECONDARY_VOLUME_INFO);
    }

    @Test
    public void getActiveAudioAttributesForZone() {
        assertWithMessage("Car volume group active audio attributes")
                .that(mCarVolumeInfoWrapper.getActiveAudioAttributesForZone(PRIMARY_AUDIO_ZONE))
                .containsExactly(TEST_MEDIA_AUDIO_ATTRIBUTE, TEST_ALARM_AUDIO_ATTRIBUTE);
    }

    @Test
    public void getCallStateForZone() {
        assertWithMessage("Car volume telephony state")
                .that(mCarVolumeInfoWrapper.getCallStateForZone(PRIMARY_AUDIO_ZONE))
                .isEqualTo(TelephonyManager.CALL_STATE_OFFHOOK);
    }
}
