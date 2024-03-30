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

package com.android.car.audio;

import static android.car.media.CarAudioManager.PRIMARY_AUDIO_ZONE;
import static android.media.AudioManager.ADJUST_LOWER;
import static android.media.AudioManager.ADJUST_MUTE;
import static android.media.AudioManager.ADJUST_RAISE;
import static android.media.AudioManager.ADJUST_SAME;
import static android.media.AudioManager.ADJUST_TOGGLE_MUTE;
import static android.media.AudioManager.ADJUST_UNMUTE;
import static android.media.AudioManager.FLAG_FROM_KEY;
import static android.media.AudioManager.FLAG_SHOW_UI;

import static com.android.car.audio.CarAudioContext.VOICE_COMMAND;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertThrows;

import android.car.media.CarVolumeGroupInfo;
import android.car.oem.OemCarVolumeChangeInfo;
import android.media.AudioManager;
import android.media.audiopolicy.AudioPolicy.Builder;

import com.android.car.CarLocalServices;
import com.android.car.oem.CarOemAudioVolumeProxyService;
import com.android.car.oem.CarOemProxyService;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.junit.MockitoRule;

import java.util.Collections;
import java.util.List;

@RunWith(MockitoJUnitRunner.class)
public class CarAudioPolicyVolumeCallbackTest {

    private static final int TEST_VOLUME_GROUP = 0;
    private static final int TEST_VOLUME = 5;
    private static final int TEST_MIN_VOLUME = 0;
    private static final int TEST_MAX_VOLUME = 10;

    private static final int TEST_EXPECTED_FLAGS = FLAG_FROM_KEY | FLAG_SHOW_UI;

    @Rule
    public MockitoRule rule = MockitoJUnit.rule();
    @Mock
    private CarVolumeInfoWrapper mCarVolumeInfoWrapper;
    @Mock
    AudioManager mMockAudioManager;
    @Mock
    Builder mMockBuilder;
    @Mock
    private CarOemProxyService mMockCarOemProxyService;
    @Mock
    private CarOemAudioVolumeProxyService mMockOemVolumeService;

    private CarAudioPolicyVolumeCallback mCarAudioPolicyVolumeCallback;

    private static final CarVolumeGroupInfo TEST_PRIMARY_GROUP_INFO =
            new CarVolumeGroupInfo.Builder("group id " + TEST_VOLUME_GROUP, PRIMARY_AUDIO_ZONE,
                    TEST_VOLUME_GROUP).setMaxVolumeGainIndex(10)
                    .setMinVolumeGainIndex(0).build();

    @Before
    public void setUp() {
        mCarAudioPolicyVolumeCallback =
                new CarAudioPolicyVolumeCallback(mMockAudioManager, mCarVolumeInfoWrapper, false);
        when(mCarVolumeInfoWrapper.getSuggestedAudioContextForPrimaryZone())
                .thenReturn(VOICE_COMMAND);
        when(mCarVolumeInfoWrapper.getGroupMaxVolume(anyInt(), anyInt()))
                .thenReturn(TEST_MAX_VOLUME);
        when(mCarVolumeInfoWrapper.getGroupMinVolume(anyInt(), anyInt()))
                .thenReturn(TEST_MIN_VOLUME);
        when(mCarVolumeInfoWrapper.getVolumeGroupInfosForZone(PRIMARY_AUDIO_ZONE))
                .thenReturn(List.of(TEST_PRIMARY_GROUP_INFO));
        when(mCarVolumeInfoWrapper.getActiveAudioAttributesForAudioZone(PRIMARY_AUDIO_ZONE))
                .thenReturn(Collections.EMPTY_LIST);

        CarLocalServices.removeServiceForTest(CarOemProxyService.class);
        CarLocalServices.addService(CarOemProxyService.class, mMockCarOemProxyService);
    }

    @After
    public void tearDown() {
        CarLocalServices.removeServiceForTest(CarOemProxyService.class);
    }

    @Test
    public void addVolumeCallbackToPolicy_withNullPolicyBuilder_fails() {
        assertThrows(NullPointerException.class, () ->
                CarAudioPolicyVolumeCallback.addVolumeCallbackToPolicy(
                        null, mMockAudioManager, mCarVolumeInfoWrapper, false));
    }

    @Test
    public void addVolumeCallbackToPolicy_withNullCarVolumeInfo_fails() {
        assertThrows(NullPointerException.class, () ->
                CarAudioPolicyVolumeCallback.addVolumeCallbackToPolicy(mMockBuilder,
                        mMockAudioManager, null, false));
    }

    @Test
    public void addVolumeCallbackToPolicy_withNullAudioManager_fails() {
        assertThrows(NullPointerException.class, () ->
                CarAudioPolicyVolumeCallback.addVolumeCallbackToPolicy(mMockBuilder,
                        null, mCarVolumeInfoWrapper, false));
    }


    @Test
    public void addVolumeCallbackToPolicy_registersVolumePolicy() {
        CarAudioPolicyVolumeCallback.addVolumeCallbackToPolicy(mMockBuilder,
                mMockAudioManager, mCarVolumeInfoWrapper, false);

        verify(mMockBuilder).setAudioPolicyVolumeCallback(any());
    }

    @Test
    public void onVolumeAdjustment_withAdjustRaise_increasesGroupVolume() {
        setGroupVolume(TEST_VOLUME);

        mCarAudioPolicyVolumeCallback.onVolumeAdjustment(ADJUST_RAISE);

        verify(mCarVolumeInfoWrapper).setGroupVolume(PRIMARY_AUDIO_ZONE,
                TEST_VOLUME_GROUP, TEST_VOLUME + 1, TEST_EXPECTED_FLAGS);
    }

    @Test
    public void onVolumeAdjustment_withAdjustLower_decreasesGroupVolume() {
        setGroupVolume(TEST_VOLUME);

        mCarAudioPolicyVolumeCallback.onVolumeAdjustment(ADJUST_LOWER);

        verify(mCarVolumeInfoWrapper).setGroupVolume(PRIMARY_AUDIO_ZONE,
                TEST_VOLUME_GROUP, TEST_VOLUME - 1, TEST_EXPECTED_FLAGS);
    }

    @Test
    public void onVolumeAdjustment_withAdjustLower_atMinVolume_setsGroupVolumeToMin() {
        setGroupVolume(TEST_MIN_VOLUME);

        mCarAudioPolicyVolumeCallback.onVolumeAdjustment(ADJUST_LOWER);

        verify(mCarVolumeInfoWrapper).setGroupVolume(PRIMARY_AUDIO_ZONE,
                TEST_VOLUME_GROUP, TEST_MIN_VOLUME, TEST_EXPECTED_FLAGS);
    }

    @Test
    public void onVolumeAdjustment_withAdjustRaise_atMaxVolume_setsGroupVolumeToMax() {
        setGroupVolume(TEST_MAX_VOLUME);

        mCarAudioPolicyVolumeCallback.onVolumeAdjustment(ADJUST_RAISE);

        verify(mCarVolumeInfoWrapper).setGroupVolume(PRIMARY_AUDIO_ZONE,
                TEST_VOLUME_GROUP, TEST_MAX_VOLUME, TEST_EXPECTED_FLAGS);
    }

    @Test
    public void onVolumeAdjustment_withAdjustRaise_whileMuted_setsGroupVolumeToMin() {
        setGroupVolume(TEST_MAX_VOLUME);
        setGroupVolumeMute(true);

        CarAudioPolicyVolumeCallback callback =
                new CarAudioPolicyVolumeCallback(mMockAudioManager, mCarVolumeInfoWrapper, true);


        callback.onVolumeAdjustment(ADJUST_RAISE);

        verify(mCarVolumeInfoWrapper).setGroupVolume(PRIMARY_AUDIO_ZONE,
                TEST_VOLUME_GROUP, TEST_MIN_VOLUME, TEST_EXPECTED_FLAGS);
    }

    @Test
    public void onVolumeAdjustment_withAdjustLower_whileMuted_setsGroupVolumeToMin() {
        setGroupVolume(TEST_MAX_VOLUME);
        setGroupVolumeMute(true);

        CarAudioPolicyVolumeCallback callback =
                new CarAudioPolicyVolumeCallback(mMockAudioManager, mCarVolumeInfoWrapper, true);

        callback.onVolumeAdjustment(ADJUST_LOWER);

        verify(mCarVolumeInfoWrapper).setGroupVolume(PRIMARY_AUDIO_ZONE,
                TEST_VOLUME_GROUP, TEST_MIN_VOLUME, TEST_EXPECTED_FLAGS);
    }

    @Test
    public void onVolumeAdjustment_withAdjustSame_doesNothing() {
        setGroupVolume(TEST_VOLUME);

        mCarAudioPolicyVolumeCallback.onVolumeAdjustment(ADJUST_SAME);

        verify(mCarVolumeInfoWrapper, never())
                .setGroupVolume(anyInt(), anyInt(), anyInt(), anyInt());
    }

    @Test
    public void onVolumeAdjustment_withAdjustMute_mutesMasterVolume() {
        mCarAudioPolicyVolumeCallback.onVolumeAdjustment(ADJUST_MUTE);

        verify(mCarVolumeInfoWrapper).setMasterMute(true, TEST_EXPECTED_FLAGS);
    }

    @Test
    public void onVolumeAdjustment_withAdjustUnMute_unMutesMasterVolume() {
        mCarAudioPolicyVolumeCallback.onVolumeAdjustment(ADJUST_UNMUTE);

        verify(mCarVolumeInfoWrapper).setMasterMute(false, TEST_EXPECTED_FLAGS);
    }

    @Test
    public void onVolumeAdjustment_withToggleMute_whileMuted_unMutesMasterVolume() {
        when(mMockAudioManager.isMasterMute()).thenReturn(true);

        mCarAudioPolicyVolumeCallback.onVolumeAdjustment(ADJUST_TOGGLE_MUTE);

        verify(mCarVolumeInfoWrapper).setMasterMute(false, TEST_EXPECTED_FLAGS);
    }

    @Test
    public void onVolumeAdjustment_withToggleMute_whileUnMuted_mutesMasterVolume() {
        when(mMockAudioManager.isMasterMute()).thenReturn(false);

        mCarAudioPolicyVolumeCallback.onVolumeAdjustment(ADJUST_TOGGLE_MUTE);

        verify(mCarVolumeInfoWrapper).setMasterMute(true, TEST_EXPECTED_FLAGS);
    }

    @Test
    public void onVolumeAdjustment_forGroupMute_withAdjustMute_mutesVolumeGroup() {
        CarAudioPolicyVolumeCallback callback =
                new CarAudioPolicyVolumeCallback(mMockAudioManager, mCarVolumeInfoWrapper, true);

        callback.onVolumeAdjustment(ADJUST_MUTE);

        verify(mCarVolumeInfoWrapper).setVolumeGroupMute(PRIMARY_AUDIO_ZONE,
                TEST_VOLUME_GROUP, true, TEST_EXPECTED_FLAGS);
    }

    @Test
    public void onVolumeAdjustment_forGroupMute_withAdjustToggleMute_togglesMutesVolumeGroup() {
        setGroupVolumeMute(true);

        CarAudioPolicyVolumeCallback callback =
                new CarAudioPolicyVolumeCallback(mMockAudioManager, mCarVolumeInfoWrapper, true);

        callback.onVolumeAdjustment(ADJUST_TOGGLE_MUTE);

        verify(mCarVolumeInfoWrapper).setVolumeGroupMute(PRIMARY_AUDIO_ZONE,
                TEST_VOLUME_GROUP, false, TEST_EXPECTED_FLAGS);
    }

    @Test
    public void onVolumeAdjustment_forGroupMute_withAdjustUnMute_UnMutesVolumeGroup() {
        setGroupVolumeMute(false);

        CarAudioPolicyVolumeCallback callback =
                new CarAudioPolicyVolumeCallback(mMockAudioManager, mCarVolumeInfoWrapper, true);

        callback.onVolumeAdjustment(ADJUST_UNMUTE);

        verify(mCarVolumeInfoWrapper).setVolumeGroupMute(PRIMARY_AUDIO_ZONE,
                TEST_VOLUME_GROUP, false, TEST_EXPECTED_FLAGS);
    }

    @Test
    public void onVolumeAdjustment_withOemVolumeService_withEmptyChange() {
        enableOemVolumeService();
        CarAudioPolicyVolumeCallback callback =
                new CarAudioPolicyVolumeCallback(mMockAudioManager, mCarVolumeInfoWrapper, true);
        when(mMockOemVolumeService.getSuggestedGroupForVolumeChange(any(), anyInt()))
                .thenReturn(OemCarVolumeChangeInfo.EMPTY_OEM_VOLUME_CHANGE);

        callback.onVolumeAdjustment(ADJUST_UNMUTE);

        verify(mCarVolumeInfoWrapper, never()).setVolumeGroupMute(anyInt(), anyInt(), anyBoolean(),
                anyInt());
    }

    @Test
    public void onVolumeAdjustment_withOemVolumeService_withNullChange() {
        enableOemVolumeService();
        CarAudioPolicyVolumeCallback callback =
                new CarAudioPolicyVolumeCallback(mMockAudioManager, mCarVolumeInfoWrapper, true);
        when(mMockOemVolumeService.getSuggestedGroupForVolumeChange(any(), anyInt()))
                .thenReturn(null);

        callback.onVolumeAdjustment(ADJUST_UNMUTE);

        verify(mCarVolumeInfoWrapper, never()).setVolumeGroupMute(anyInt(), anyInt(), anyBoolean(),
                anyInt());
    }

    @Test
    public void onVolumeAdjustment_onMute_withOemVolumeService_withChange() {
        enableOemVolumeService();
        CarAudioPolicyVolumeCallback callback =
                new CarAudioPolicyVolumeCallback(mMockAudioManager, mCarVolumeInfoWrapper, true);
        OemCarVolumeChangeInfo info = new OemCarVolumeChangeInfo.Builder(true)
                        .setChangedVolumeGroup(TEST_PRIMARY_GROUP_INFO).build();
        when(mMockOemVolumeService.getSuggestedGroupForVolumeChange(any(), anyInt()))
                .thenReturn(info);

        callback.onVolumeAdjustment(ADJUST_UNMUTE);

        verify(mCarVolumeInfoWrapper).setVolumeGroupMute(
                TEST_PRIMARY_GROUP_INFO.getZoneId(), TEST_PRIMARY_GROUP_INFO.getId(),
                TEST_PRIMARY_GROUP_INFO.isMuted(), TEST_EXPECTED_FLAGS);
    }

    @Test
    public void onVolumeAdjustment_onRaise_withOemVolumeService_withChange() {
        enableOemVolumeService();
        CarAudioPolicyVolumeCallback callback =
                new CarAudioPolicyVolumeCallback(mMockAudioManager, mCarVolumeInfoWrapper, true);
        OemCarVolumeChangeInfo info = new OemCarVolumeChangeInfo.Builder(true)
                .setChangedVolumeGroup(TEST_PRIMARY_GROUP_INFO).build();
        when(mMockOemVolumeService.getSuggestedGroupForVolumeChange(any(), anyInt()))
                .thenReturn(info);

        callback.onVolumeAdjustment(ADJUST_RAISE);

        verify(mCarVolumeInfoWrapper).setGroupVolume(TEST_PRIMARY_GROUP_INFO.getZoneId(),
                TEST_PRIMARY_GROUP_INFO.getId(), TEST_PRIMARY_GROUP_INFO.getVolumeGainIndex(),
                TEST_EXPECTED_FLAGS);
    }

    @Test
    public void onVolumeAdjustment_onSame_withOemVolumeService_muteChanged() {
        enableOemVolumeService();
        CarAudioPolicyVolumeCallback callback =
                new CarAudioPolicyVolumeCallback(mMockAudioManager, mCarVolumeInfoWrapper, true);
        CarVolumeGroupInfo changedInfo =
                new CarVolumeGroupInfo.Builder(TEST_PRIMARY_GROUP_INFO)
                        .setMuted(!TEST_PRIMARY_GROUP_INFO.isMuted()).build();
        OemCarVolumeChangeInfo info = new OemCarVolumeChangeInfo.Builder(true)
                .setChangedVolumeGroup(changedInfo).build();
        when(mMockOemVolumeService.getSuggestedGroupForVolumeChange(any(), anyInt()))
                .thenReturn(info);
        when(mCarVolumeInfoWrapper.getVolumeGroupInfo(anyInt(), anyInt()))
                .thenReturn(TEST_PRIMARY_GROUP_INFO);

        callback.onVolumeAdjustment(ADJUST_SAME);

        verify(mCarVolumeInfoWrapper).setVolumeGroupMute(changedInfo.getZoneId(),
                changedInfo.getId(), changedInfo.isMuted(), TEST_EXPECTED_FLAGS);
    }

    @Test
    public void onVolumeAdjustment_onSame_withOemVolumeService_gainChanged() {
        enableOemVolumeService();
        CarAudioPolicyVolumeCallback callback =
                new CarAudioPolicyVolumeCallback(mMockAudioManager, mCarVolumeInfoWrapper, true);
        CarVolumeGroupInfo changedInfo =
                new CarVolumeGroupInfo.Builder(TEST_PRIMARY_GROUP_INFO)
                        .setVolumeGainIndex(TEST_PRIMARY_GROUP_INFO.getVolumeGainIndex() + 1)
                        .build();
        OemCarVolumeChangeInfo info = new OemCarVolumeChangeInfo.Builder(true)
                .setChangedVolumeGroup(changedInfo).build();
        when(mMockOemVolumeService.getSuggestedGroupForVolumeChange(any(), anyInt()))
                .thenReturn(info);
        when(mCarVolumeInfoWrapper.getVolumeGroupInfo(anyInt(), anyInt()))
                .thenReturn(TEST_PRIMARY_GROUP_INFO);

        callback.onVolumeAdjustment(ADJUST_SAME);

        verify(mCarVolumeInfoWrapper).setGroupVolume(changedInfo.getZoneId(),
                changedInfo.getId(), changedInfo.getVolumeGainIndex(), TEST_EXPECTED_FLAGS);
    }

    private void setGroupVolume(int groupVolume) {
        when(mCarVolumeInfoWrapper.getGroupVolume(anyInt(), anyInt()))
                .thenReturn(groupVolume);
    }

    private void setGroupVolumeMute(boolean mute) {
        when(mCarVolumeInfoWrapper.isVolumeGroupMuted(anyInt(), anyInt()))
                .thenReturn(mute);
    }

    private void enableOemVolumeService() {
        when(mMockCarOemProxyService.isOemServiceEnabled()).thenReturn(true);
        when(mMockCarOemProxyService.isOemServiceReady()).thenReturn(true);
        when(mMockCarOemProxyService.getCarOemAudioVolumeService())
                .thenReturn(mMockOemVolumeService);
    }
}
