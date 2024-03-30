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

import static android.media.AudioAttributes.USAGE_ALARM;
import static android.media.AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE;
import static android.media.AudioAttributes.USAGE_EMERGENCY;
import static android.media.AudioAttributes.USAGE_GAME;
import static android.media.AudioAttributes.USAGE_MEDIA;
import static android.media.AudioAttributes.USAGE_NOTIFICATION;
import static android.media.AudioAttributes.USAGE_NOTIFICATION_RINGTONE;
import static android.media.AudioAttributes.USAGE_UNKNOWN;
import static android.media.AudioAttributes.USAGE_VOICE_COMMUNICATION;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;

import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;
import static org.testng.Assert.expectThrows;

import android.annotation.UserIdInt;
import android.car.media.CarVolumeGroupInfo;
import android.car.test.AbstractExpectableTestCase;
import android.hardware.automotive.audiocontrol.AudioGainConfigInfo;
import android.hardware.automotive.audiocontrol.Reasons;
import android.media.AudioAttributes;
import android.os.UserHandle;
import android.util.SparseBooleanArray;
import android.util.SparseIntArray;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.List;

@RunWith(MockitoJUnitRunner.class)
public class CarVolumeGroupUnitTest extends AbstractExpectableTestCase {
    private static final int ZONE_ID = 0;
    private static final int GROUP_ID = 0;
    private static final int STEP_VALUE = 2;
    private static final int MIN_GAIN = 3;
    private static final int MAX_GAIN = 10;
    private static final int DEFAULT_GAIN = 5;
    private static final int DEFAULT_GAIN_INDEX = (DEFAULT_GAIN - MIN_GAIN) / STEP_VALUE;
    private static final int MIN_GAIN_INDEX = 0;
    private static final int MAX_GAIN_INDEX = (MAX_GAIN - MIN_GAIN) / STEP_VALUE;
    private static final int TEST_GAIN_INDEX = 2;
    private static final int TEST_USER_10 = 10;
    private static final int TEST_USER_11 = 11;
    private static final String MEDIA_DEVICE_ADDRESS = "music";
    private static final String NAVIGATION_DEVICE_ADDRESS = "navigation";
    private static final String OTHER_ADDRESS = "other_address";

    private static final CarAudioContext TEST_CAR_AUDIO_CONTEXT =
            new CarAudioContext(CarAudioContext.getAllContextsInfo());

    private static final @CarAudioContext.AudioContext int TEST_MEDIA_CONTEXT_ID =
            TEST_CAR_AUDIO_CONTEXT.getContextForAudioAttribute(
                    CarAudioContext.getAudioAttributeFromUsage(USAGE_MEDIA));
    private static final @CarAudioContext.AudioContext int TEST_ALARM_CONTEXT_ID =
            TEST_CAR_AUDIO_CONTEXT.getContextForAudioAttribute(
                    CarAudioContext.getAudioAttributeFromUsage(USAGE_ALARM));
    private static final @CarAudioContext.AudioContext int TEST_CALL_CONTEXT_ID =
            TEST_CAR_AUDIO_CONTEXT.getContextForAudioAttribute(
                    CarAudioContext.getAudioAttributeFromUsage(USAGE_VOICE_COMMUNICATION));
    private static final @CarAudioContext.AudioContext int TEST_CALL_RING_CONTEXT_ID =
            TEST_CAR_AUDIO_CONTEXT.getContextForAudioAttribute(
                    CarAudioContext.getAudioAttributeFromUsage(USAGE_NOTIFICATION_RINGTONE));
    private static final @CarAudioContext.AudioContext int TEST_EMERGENCY_CONTEXT_ID =
            TEST_CAR_AUDIO_CONTEXT.getContextForAudioAttribute(
                    CarAudioContext.getAudioAttributeFromUsage(USAGE_EMERGENCY));
    private static final @CarAudioContext.AudioContext int TEST_NAVIGATION_CONTEXT_ID =
            TEST_CAR_AUDIO_CONTEXT.getContextForAudioAttribute(CarAudioContext
                    .getAudioAttributeFromUsage(USAGE_ASSISTANCE_NAVIGATION_GUIDANCE));
    private static final @CarAudioContext.AudioContext int TEST_NOTIFICATION_CONTEXT_ID =
            TEST_CAR_AUDIO_CONTEXT.getContextForAudioAttribute(CarAudioContext
                    .getAudioAttributeFromUsage(USAGE_NOTIFICATION));

    private CarAudioDeviceInfo mMediaDeviceInfo;
    private CarAudioDeviceInfo mNavigationDeviceInfo;

    @Mock
    CarAudioSettings mSettingsMock;

    @Before
    public void setUp() {
        mMediaDeviceInfo = new InfoBuilder(MEDIA_DEVICE_ADDRESS).build();
        mNavigationDeviceInfo = new InfoBuilder(NAVIGATION_DEVICE_ADDRESS).build();
    }

    @Test
    public void setDeviceInfoForContext_associatesDeviceAddresses() {
        CarVolumeGroup.Builder builder = getBuilder();

        builder.setDeviceInfoForContext(TEST_MEDIA_CONTEXT_ID, mMediaDeviceInfo);
        builder.setDeviceInfoForContext(TEST_NAVIGATION_CONTEXT_ID, mNavigationDeviceInfo);
        CarVolumeGroup carVolumeGroup = builder.build();

        expectWithMessage("Addresses %s and %s", MEDIA_DEVICE_ADDRESS, NAVIGATION_DEVICE_ADDRESS)
                .that(carVolumeGroup.getAddresses()).containsExactly(MEDIA_DEVICE_ADDRESS,
                NAVIGATION_DEVICE_ADDRESS);
    }

    @Test
    public void setDeviceInfoForContext_associatesContexts() {
        CarVolumeGroup.Builder builder = getBuilder();

        builder.setDeviceInfoForContext(TEST_MEDIA_CONTEXT_ID, mMediaDeviceInfo);
        builder.setDeviceInfoForContext(TEST_NAVIGATION_CONTEXT_ID, mNavigationDeviceInfo);
        CarVolumeGroup carVolumeGroup = builder.build();

        expectWithMessage("Music[%s] and Navigation[%s] Context",
                TEST_MEDIA_CONTEXT_ID, TEST_NAVIGATION_CONTEXT_ID)
                .that(carVolumeGroup.getContexts()).asList()
                .containsExactly(TEST_MEDIA_CONTEXT_ID,
                        TEST_NAVIGATION_CONTEXT_ID);
    }

    @Test
    public void setDeviceInfoForContext_withDifferentStepSize_throws() {
        CarVolumeGroup.Builder builder = getBuilder();
        builder.setDeviceInfoForContext(TEST_MEDIA_CONTEXT_ID, mMediaDeviceInfo);
        CarAudioDeviceInfo differentStepValueDevice = new InfoBuilder(NAVIGATION_DEVICE_ADDRESS)
                .setStepValue(mMediaDeviceInfo.getStepValue() + 1).build();

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> builder.setDeviceInfoForContext(
                        TEST_NAVIGATION_CONTEXT_ID,
                        differentStepValueDevice));

        expectWithMessage("setDeviceInfoForContext failure for different step size")
                .that(thrown).hasMessageThat()
                .contains("Gain controls within one group must have same step value");
    }

    @Test
    public void setDeviceInfoForContext_withSameContext_throws() {
        CarVolumeGroup.Builder builder = getBuilder();
        builder.setDeviceInfoForContext(TEST_MEDIA_CONTEXT_ID, mMediaDeviceInfo);

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> builder.setDeviceInfoForContext(TEST_MEDIA_CONTEXT_ID,
                        mNavigationDeviceInfo));

        expectWithMessage("setDeviceInfoForSameContext failure for repeated context")
                .that(thrown).hasMessageThat().contains("has already been set to");
    }

    @Test
    public void setDeviceInfoForContext_withFirstCall_setsMinGain() {
        CarVolumeGroup.Builder builder = getBuilder();

        builder.setDeviceInfoForContext(TEST_MEDIA_CONTEXT_ID, mMediaDeviceInfo);

        expectWithMessage("Min Gain from builder")
                .that(builder.mMinGain).isEqualTo(mMediaDeviceInfo.getMinGain());
    }

    @Test
    public void setDeviceInfoForContext_withFirstCall_setsMaxGain() {
        CarVolumeGroup.Builder builder = getBuilder();

        builder.setDeviceInfoForContext(TEST_MEDIA_CONTEXT_ID, mMediaDeviceInfo);

        expectWithMessage("Max Gain from builder")
                .that(builder.mMaxGain).isEqualTo(mMediaDeviceInfo.getMaxGain());
    }

    @Test
    public void setDeviceInfoForContext_withFirstCall_setsDefaultGain() {
        CarVolumeGroup.Builder builder = getBuilder();

        builder.setDeviceInfoForContext(TEST_MEDIA_CONTEXT_ID, mMediaDeviceInfo);

        expectWithMessage("Default Gain from builder")
                .that(builder.mDefaultGain).isEqualTo(mMediaDeviceInfo.getDefaultGain());
    }

    @Test
    public void setDeviceInfoForContext_SecondCallWithSmallerMinGain_updatesMinGain() {
        CarVolumeGroup.Builder builder = getBuilder();
        builder.setDeviceInfoForContext(TEST_MEDIA_CONTEXT_ID, mMediaDeviceInfo);
        CarAudioDeviceInfo secondInfo = new InfoBuilder(NAVIGATION_DEVICE_ADDRESS)
                .setMinGain(mMediaDeviceInfo.getMinGain() - 1).build();

        builder.setDeviceInfoForContext(TEST_NAVIGATION_CONTEXT_ID, secondInfo);

        expectWithMessage("Second, smaller min gain from builder")
                .that(builder.mMinGain).isEqualTo(secondInfo.getMinGain());
    }

    @Test
    public void setDeviceInfoForContext_SecondCallWithLargerMinGain_keepsFirstMinGain() {
        CarVolumeGroup.Builder builder = getBuilder();
        builder.setDeviceInfoForContext(TEST_MEDIA_CONTEXT_ID, mMediaDeviceInfo);
        CarAudioDeviceInfo secondInfo = new InfoBuilder(NAVIGATION_DEVICE_ADDRESS)
                .setMinGain(mMediaDeviceInfo.getMinGain() + 1).build();

        builder.setDeviceInfoForContext(TEST_NAVIGATION_CONTEXT_ID, secondInfo);

        expectWithMessage("First, smaller min gain from builder")
                .that(builder.mMinGain).isEqualTo(mMediaDeviceInfo.getMinGain());
    }

    @Test
    public void setDeviceInfoForContext_SecondCallWithLargerMaxGain_updatesMaxGain() {
        CarVolumeGroup.Builder builder = getBuilder();
        builder.setDeviceInfoForContext(TEST_MEDIA_CONTEXT_ID, mMediaDeviceInfo);
        CarAudioDeviceInfo secondInfo = new InfoBuilder(NAVIGATION_DEVICE_ADDRESS)
                .setMaxGain(mMediaDeviceInfo.getMaxGain() + 1).build();

        builder.setDeviceInfoForContext(TEST_NAVIGATION_CONTEXT_ID, secondInfo);

        expectWithMessage("Second, larger max gain from builder")
                .that(builder.mMaxGain).isEqualTo(secondInfo.getMaxGain());
    }

    @Test
    public void setDeviceInfoForContext_SecondCallWithSmallerMaxGain_keepsFirstMaxGain() {
        CarVolumeGroup.Builder builder = getBuilder();
        builder.setDeviceInfoForContext(TEST_MEDIA_CONTEXT_ID, mMediaDeviceInfo);
        CarAudioDeviceInfo secondInfo = new InfoBuilder(NAVIGATION_DEVICE_ADDRESS)
                .setMaxGain(mMediaDeviceInfo.getMaxGain() - 1).build();

        builder.setDeviceInfoForContext(TEST_NAVIGATION_CONTEXT_ID, secondInfo);

        expectWithMessage("First, larger max gain from builder")
                .that(builder.mMaxGain).isEqualTo(mMediaDeviceInfo.getMaxGain());
    }

    @Test
    public void setDeviceInfoForContext_SecondCallWithLargerDefaultGain_updatesDefaultGain() {
        CarVolumeGroup.Builder builder = getBuilder();
        builder.setDeviceInfoForContext(TEST_MEDIA_CONTEXT_ID, mMediaDeviceInfo);
        CarAudioDeviceInfo secondInfo = new InfoBuilder(NAVIGATION_DEVICE_ADDRESS)
                .setDefaultGain(mMediaDeviceInfo.getDefaultGain() + 1).build();

        builder.setDeviceInfoForContext(TEST_NAVIGATION_CONTEXT_ID, secondInfo);

        expectWithMessage("Second, larger default gain from builder")
                .that(builder.mDefaultGain).isEqualTo(secondInfo.getDefaultGain());
    }

    @Test
    public void setDeviceInfoForContext_SecondCallWithSmallerDefaultGain_keepsFirstDefaultGain() {
        CarVolumeGroup.Builder builder = getBuilder();
        builder.setDeviceInfoForContext(TEST_MEDIA_CONTEXT_ID, mMediaDeviceInfo);
        CarAudioDeviceInfo secondInfo = new InfoBuilder(NAVIGATION_DEVICE_ADDRESS)
                .setDefaultGain(mMediaDeviceInfo.getDefaultGain() - 1).build();

        builder.setDeviceInfoForContext(TEST_NAVIGATION_CONTEXT_ID, secondInfo);

        expectWithMessage("Second, smaller default gain from builder")
                .that(builder.mDefaultGain).isEqualTo(mMediaDeviceInfo.getDefaultGain());
    }

    @Test
    public void builderBuild_withNoCallToSetDeviceInfoForContext_throws() {
        CarVolumeGroup.Builder builder = getBuilder();

        Exception e = expectThrows(IllegalArgumentException.class, builder::build);

        expectWithMessage("Builder build failure").that(e).hasMessageThat()
                .isEqualTo(
                        "setDeviceInfoForContext has to be called at least once before building");
    }

    @Test
    public void builderBuild_withNoStoredGain_usesDefaultGain() {
        CarVolumeGroup.Builder builder = getBuilder().setDeviceInfoForContext(
                TEST_MEDIA_CONTEXT_ID, mMediaDeviceInfo);
        when(mSettingsMock.getStoredVolumeGainIndexForUser(UserHandle.USER_CURRENT, ZONE_ID,
                GROUP_ID)).thenReturn(-1);


        CarVolumeGroup carVolumeGroup = builder.build();

        expectWithMessage("Current gain index")
                .that(carVolumeGroup.getCurrentGainIndex()).isEqualTo(DEFAULT_GAIN_INDEX);
    }

    @Test
    public void builderBuild_withTooLargeStoredGain_usesDefaultGain() {
        CarVolumeGroup.Builder builder = getBuilder().setDeviceInfoForContext(
                TEST_MEDIA_CONTEXT_ID, mMediaDeviceInfo);
        when(mSettingsMock.getStoredVolumeGainIndexForUser(UserHandle.USER_CURRENT, ZONE_ID,
                GROUP_ID)).thenReturn(MAX_GAIN_INDEX + 1);

        CarVolumeGroup carVolumeGroup = builder.build();

        expectWithMessage("Current gain index")
                .that(carVolumeGroup.getCurrentGainIndex()).isEqualTo(DEFAULT_GAIN_INDEX);
    }

    @Test
    public void builderBuild_withTooSmallStoredGain_usesDefaultGain() {
        CarVolumeGroup.Builder builder = getBuilder().setDeviceInfoForContext(
                TEST_MEDIA_CONTEXT_ID, mMediaDeviceInfo);
        when(mSettingsMock.getStoredVolumeGainIndexForUser(UserHandle.USER_CURRENT, ZONE_ID,
                GROUP_ID)).thenReturn(MIN_GAIN_INDEX - 1);

        CarVolumeGroup carVolumeGroup = builder.build();

        expectWithMessage("Current gain index")
                .that(carVolumeGroup.getCurrentGainIndex()).isEqualTo(DEFAULT_GAIN_INDEX);
    }

    @Test
    public void builderBuild_withValidStoredGain_usesStoredGain() {
        CarVolumeGroup.Builder builder = getBuilder().setDeviceInfoForContext(
                TEST_MEDIA_CONTEXT_ID, mMediaDeviceInfo);
        when(mSettingsMock.getStoredVolumeGainIndexForUser(UserHandle.USER_CURRENT, ZONE_ID,
                GROUP_ID)).thenReturn(MAX_GAIN_INDEX - 1);

        CarVolumeGroup carVolumeGroup = builder.build();

        expectWithMessage("Current gain index")
                .that(carVolumeGroup.getCurrentGainIndex()).isEqualTo(MAX_GAIN_INDEX - 1);
    }

    @Test
    public void builderConstructor_withNullCarAudioSettings_fails() {
        NullPointerException thrown = assertThrows(NullPointerException.class,
                () -> new CarVolumeGroup.Builder(/* carAudioSettings= */ null,
                        TEST_CAR_AUDIO_CONTEXT, ZONE_ID, GROUP_ID,
                        /* useCarVolumeGroupMute= */ true));

        expectWithMessage("Constructor null car audio settings exception")
                .that(thrown).hasMessageThat()
                .contains("Car audio settings");
    }

    @Test
    public void builderConstructor_withNullCarAudioContext_fails() {
        NullPointerException thrown = assertThrows(NullPointerException.class,
                () -> new CarVolumeGroup.Builder(mSettingsMock, /* carAudioContext= */ null,
                        ZONE_ID, GROUP_ID, /* useCarVolumeGroupMute= */ true));

        expectWithMessage("Constructor null car audio context exception")
                .that(thrown).hasMessageThat()
                .contains("Car audio context");
    }

    @Test
    public void getAddressForContext_withSupportedContext_returnsAddress() {
        CarVolumeGroup carVolumeGroup = getCarVolumeGroupWithMusicBound();

        expectWithMessage("Supported context's address")
                .that(carVolumeGroup.getAddressForContext(TEST_MEDIA_CONTEXT_ID))
                .isEqualTo(mMediaDeviceInfo.getAddress());
    }

    @Test
    public void getAddressForContext_withUnsupportedContext_returnsNull() {
        CarVolumeGroup carVolumeGroup = getCarVolumeGroupWithMusicBound();

        expectWithMessage("Unsupported context's address")
                .that(carVolumeGroup.getAddressForContext(
                        TEST_NAVIGATION_CONTEXT_ID)).isNull();
    }

    @Test
    public void isMuted_whenDefault_returnsFalse() {
        CarVolumeGroup carVolumeGroup = getCarVolumeGroupWithMusicBound();

        expectWithMessage("Default mute state")
                .that(carVolumeGroup.isMuted()).isFalse();
    }

    @Test
    public void isMuted_afterMuting_returnsTrue() {
        CarVolumeGroup carVolumeGroup = getCarVolumeGroupWithMusicBound();

        carVolumeGroup.setMute(true);

        expectWithMessage("Set mute state")
                .that(carVolumeGroup.isMuted()).isTrue();
    }

    @Test
    public void isMuted_afterUnMuting_returnsFalse() {
        CarVolumeGroup carVolumeGroup = getCarVolumeGroupWithMusicBound();

        carVolumeGroup.setMute(false);

        expectWithMessage("Set mute state")
                .that(carVolumeGroup.isMuted()).isFalse();
    }

    @Test
    public void setMute_withMutedState_storesValueToSetting() {
        CarAudioSettings settings = new SettingsBuilder(0, 0)
                .setMuteForUser10(false).setIsPersistVolumeGroupEnabled(true).build();
        CarVolumeGroup carVolumeGroup = getCarVolumeGroupWithNavigationBound(settings, true);
        carVolumeGroup.loadVolumesSettingsForUser(TEST_USER_10);

        carVolumeGroup.setMute(true);

        verify(settings)
                .storeVolumeGroupMuteForUser(TEST_USER_10, 0, 0, true);
    }

    @Test
    public void setMute_withUnMutedState_storesValueToSetting() {
        CarAudioSettings settings = new SettingsBuilder(0, 0)
                .setMuteForUser10(false).setIsPersistVolumeGroupEnabled(true).build();
        CarVolumeGroup carVolumeGroup = getCarVolumeGroupWithNavigationBound(settings, true);
        carVolumeGroup.loadVolumesSettingsForUser(TEST_USER_10);

        carVolumeGroup.setMute(false);

        verify(settings)
                .storeVolumeGroupMuteForUser(TEST_USER_10, 0, 0, false);
    }

    @Test
    public void getContextsForAddress_returnsContextsBoundToThatAddress() {
        CarVolumeGroup carVolumeGroup = testVolumeGroupSetup();

        List<Integer> contextsList = carVolumeGroup.getContextsForAddress(MEDIA_DEVICE_ADDRESS);

        expectWithMessage("Contexts for bounded address %s", MEDIA_DEVICE_ADDRESS)
                .that(contextsList).containsExactly(TEST_MEDIA_CONTEXT_ID,
                        TEST_CALL_CONTEXT_ID, TEST_CALL_RING_CONTEXT_ID);
    }

    @Test
    public void getContextsForAddress_returnsEmptyArrayIfAddressNotBound() {
        CarVolumeGroup carVolumeGroup = testVolumeGroupSetup();

        List<Integer> contextsList = carVolumeGroup.getContextsForAddress(OTHER_ADDRESS);

        expectWithMessage("Contexts for non-bounded address %s", OTHER_ADDRESS)
                .that(contextsList).isEmpty();
    }

    @Test
    public void getCarAudioDeviceInfoForAddress_returnsExpectedDevice() {
        CarVolumeGroup carVolumeGroup = testVolumeGroupSetup();

        CarAudioDeviceInfo actualDevice = carVolumeGroup.getCarAudioDeviceInfoForAddress(
                MEDIA_DEVICE_ADDRESS);

        expectWithMessage("Device information for bounded address %s", MEDIA_DEVICE_ADDRESS)
                .that(actualDevice).isEqualTo(mMediaDeviceInfo);
    }

    @Test
    public void getCarAudioDeviceInfoForAddress_returnsNullIfAddressNotBound() {
        CarVolumeGroup carVolumeGroup = testVolumeGroupSetup();

        CarAudioDeviceInfo actualDevice = carVolumeGroup.getCarAudioDeviceInfoForAddress(
                OTHER_ADDRESS);

        expectWithMessage("Device information for non-bounded address %s", OTHER_ADDRESS)
                .that(actualDevice).isNull();
    }

    @Test
    public void setCurrentGainIndex_setsGainOnAllBoundDevices() {
        CarVolumeGroup carVolumeGroup = testVolumeGroupSetup();

        carVolumeGroup.setCurrentGainIndex(TEST_GAIN_INDEX);

        verify(mMediaDeviceInfo).setCurrentGain(7);
        verify(mNavigationDeviceInfo).setCurrentGain(7);
    }

    @Test
    public void setCurrentGainIndex_updatesCurrentGainIndex() {
        CarVolumeGroup carVolumeGroup = testVolumeGroupSetup();

        carVolumeGroup.setCurrentGainIndex(TEST_GAIN_INDEX);

        expectWithMessage("Updated current gain index")
                .that(carVolumeGroup.getCurrentGainIndex()).isEqualTo(TEST_GAIN_INDEX);
    }

    @Test
    public void setCurrentGainIndex_checksNewGainIsAboveMin() {
        CarVolumeGroup carVolumeGroup = testVolumeGroupSetup();

        IllegalArgumentException thrown = expectThrows(IllegalArgumentException.class,
                () -> carVolumeGroup.setCurrentGainIndex(MIN_GAIN_INDEX - 1));

        expectWithMessage("Set out of bound gain index failure")
                .that(thrown).hasMessageThat()
                .contains("Gain out of range (" + MIN_GAIN + ":" + MAX_GAIN + ")");
    }

    @Test
    public void setCurrentGainIndex_checksNewGainIsBelowMax() {
        CarVolumeGroup carVolumeGroup = testVolumeGroupSetup();

        IllegalArgumentException thrown = expectThrows(IllegalArgumentException.class,
                () -> carVolumeGroup.setCurrentGainIndex(MAX_GAIN_INDEX + 1));

        expectWithMessage("Set out of bound gain index failure")
                .that(thrown).hasMessageThat()
                .contains("Gain out of range (" + MIN_GAIN + ":" + MAX_GAIN + ")");
    }

    @Test
    public void setCurrentGainIndex_setsCurrentGainIndexForUser() {
        CarAudioSettings settings = new SettingsBuilder(0, 0)
                .setGainIndexForUser(TEST_USER_11).build();
        CarVolumeGroup carVolumeGroup = getCarVolumeGroupWithNavigationBound(settings, false);
        carVolumeGroup.loadVolumesSettingsForUser(TEST_USER_11);

        carVolumeGroup.setCurrentGainIndex(MIN_GAIN);

        verify(settings).storeVolumeGainIndexForUser(TEST_USER_11, 0, 0, MIN_GAIN);
    }

    @Test
    public void setCurrentGainIndex_setsCurrentGainIndexForDefaultUser() {
        CarAudioSettings settings = new SettingsBuilder(0, 0)
                .setGainIndexForUser(UserHandle.USER_CURRENT).build();
        CarVolumeGroup carVolumeGroup = getCarVolumeGroupWithNavigationBound(settings, false);

        carVolumeGroup.setCurrentGainIndex(MIN_GAIN);

        verify(settings)
                .storeVolumeGainIndexForUser(UserHandle.USER_CURRENT, 0, 0, MIN_GAIN);
    }

    @Test
    public void loadVolumesSettingsForUser_withMutedState_loadsMuteStateForUser() {
        CarVolumeGroup carVolumeGroup = getVolumeGroupWithMuteAndNavBound(true, true, true);

        carVolumeGroup.loadVolumesSettingsForUser(TEST_USER_10);

        expectWithMessage("Saved mute state from settings")
                .that(carVolumeGroup.isMuted()).isTrue();
    }

    @Test
    public void loadVolumesSettingsForUser_withDisabledUseVolumeGroupMute_doesNotLoadMute() {
        CarVolumeGroup carVolumeGroup = getVolumeGroupWithMuteAndNavBound(true, true, false);

        carVolumeGroup.loadVolumesSettingsForUser(TEST_USER_10);

        expectWithMessage("Default mute state")
                .that(carVolumeGroup.isMuted()).isFalse();
    }

    @Test
    public void loadVolumesSettingsForUser_withUnMutedState_loadsMuteStateForUser() {
        CarVolumeGroup carVolumeGroup = getVolumeGroupWithMuteAndNavBound(false, true, true);

        carVolumeGroup.loadVolumesSettingsForUser(TEST_USER_10);

        expectWithMessage("Saved mute state from settings").that(carVolumeGroup.isMuted())
                .isFalse();
    }

    @Test
    public void loadVolumesSettingsForUser_withMutedStateAndNoPersist_returnsDefaultMuteState() {
        CarVolumeGroup carVolumeGroup = getVolumeGroupWithMuteAndNavBound(true, false, true);

        carVolumeGroup.loadVolumesSettingsForUser(TEST_USER_10);

        expectWithMessage("Default mute state").that(carVolumeGroup.isMuted()).isFalse();
    }

    @Test
    public void hasCriticalAudioContexts_withoutCriticalContexts_returnsFalse() {
        CarVolumeGroup carVolumeGroup = getCarVolumeGroupWithMusicBound();

        expectWithMessage("Group without critical audio context")
                .that(carVolumeGroup.hasCriticalAudioContexts()).isFalse();
    }

    @Test
    public void hasCriticalAudioContexts_withCriticalContexts_returnsTrue() {
        CarVolumeGroup carVolumeGroup = getBuilder()
                .setDeviceInfoForContext(TEST_EMERGENCY_CONTEXT_ID, mMediaDeviceInfo).build();

        expectWithMessage("Group with critical audio context")
                .that(carVolumeGroup.hasCriticalAudioContexts()).isTrue();
    }

    @Test
    public void getCurrentGainIndex_whileMuted_returnsMinGain() {
        CarVolumeGroup carVolumeGroup = getCarVolumeGroupWithMusicBound();
        carVolumeGroup.setCurrentGainIndex(TEST_GAIN_INDEX);

        carVolumeGroup.setMute(true);

        expectWithMessage("Muted current gain index")
                .that(carVolumeGroup.getCurrentGainIndex()).isEqualTo(MIN_GAIN_INDEX);
    }

    @Test
    public void getCurrentGainIndex_whileUnMuted_returnsLastSetGain() {
        CarVolumeGroup carVolumeGroup = getCarVolumeGroupWithMusicBound();
        carVolumeGroup.setCurrentGainIndex(TEST_GAIN_INDEX);

        carVolumeGroup.setMute(false);

        expectWithMessage("Un-muted current gain index")
                .that(carVolumeGroup.getCurrentGainIndex()).isEqualTo(TEST_GAIN_INDEX);
    }

    @Test
    public void setCurrentGainIndex_whileMuted_unMutesVolumeGroup() {
        CarVolumeGroup carVolumeGroup = getCarVolumeGroupWithMusicBound();
        carVolumeGroup.setMute(true);
        carVolumeGroup.setCurrentGainIndex(TEST_GAIN_INDEX);

        expectWithMessage("Mute state after volume change")
                .that(carVolumeGroup.isMuted()).isEqualTo(false);
    }

    @Test
    public void setBlocked_withGain_thenBackToUninitializedGain() {
        CarVolumeGroup carVolumeGroup = testVolumeGroupSetup();

        expectWithMessage("Default blocked state").that(carVolumeGroup.isBlocked()).isFalse();

        carVolumeGroup.setBlocked(10);

        expectWithMessage("Blocked state after blocked").that(carVolumeGroup.isBlocked())
                .isTrue();

        carVolumeGroup.resetBlocked();

        expectWithMessage("Blocked state after reset").that(carVolumeGroup.isBlocked())
                .isFalse();
    }

    @Test
    public void setLimited_withGain_thenBackToMaxGain() {
        CarVolumeGroup carVolumeGroup = testVolumeGroupSetup();

        expectWithMessage("Default limited state").that(carVolumeGroup.isLimited()).isFalse();

        carVolumeGroup.setLimit(carVolumeGroup.getMaxGainIndex() - 1);

        expectWithMessage("Limit state after set limit").that(carVolumeGroup.isLimited())
                .isTrue();

        carVolumeGroup.resetLimit();

        expectWithMessage("Limit state after reset").that(carVolumeGroup.isLimited())
                .isFalse();
    }

    @Test
    public void setAttenuatedGain_withGain_thenBackToUninitializedGain() {
        CarVolumeGroup carVolumeGroup = testVolumeGroupSetup();

        expectWithMessage("Default attenuated state").that(carVolumeGroup.isAttenuated()).isFalse();

        carVolumeGroup.setAttenuatedGain(10);

        expectWithMessage("Attenuated state after set attenuated").that(carVolumeGroup
                .isAttenuated()).isTrue();

        carVolumeGroup.resetAttenuation();

        expectWithMessage("Attenuated state after reset").that(carVolumeGroup.isAttenuated())
                .isFalse();
    }

    @Test
    public void getCurrentGainIndex_whileBlocked_thenUnblocked() {
        CarVolumeGroup carVolumeGroup = getCarVolumeGroupWithMusicBound();
        carVolumeGroup.setCurrentGainIndex(TEST_GAIN_INDEX);

        expectWithMessage("Initial current gain index")
                .that(carVolumeGroup.getCurrentGainIndex())
                .isEqualTo(TEST_GAIN_INDEX);

        int blockedIndex = 10;
        carVolumeGroup.setBlocked(blockedIndex);

        expectWithMessage("Blocked state after set blocked").that(carVolumeGroup.isBlocked())
                .isTrue();

        expectWithMessage("Blocked current gain index")
                .that(carVolumeGroup.getCurrentGainIndex())
                .isEqualTo(blockedIndex);

        carVolumeGroup.resetBlocked();

        expectWithMessage("Blocked state after reset").that(carVolumeGroup.isBlocked()).isFalse();

        expectWithMessage("Back to current gain index")
                .that(carVolumeGroup.getCurrentGainIndex())
                .isEqualTo(TEST_GAIN_INDEX);
    }

    @Test
    public void getCurrentGainIndex_whileLimited_thenUnlimited() {
        CarVolumeGroup carVolumeGroup = getCarVolumeGroupWithMusicBound();
        carVolumeGroup.setCurrentGainIndex(TEST_GAIN_INDEX);
        expectWithMessage("Initial current gain index")
                .that(carVolumeGroup.getCurrentGainIndex())
                .isEqualTo(TEST_GAIN_INDEX);
        expectWithMessage("Default limit state").that(carVolumeGroup.isLimited()).isFalse();

        int limitedGainIndex = carVolumeGroup.getMaxGainIndex() - 1;
        carVolumeGroup.setLimit(limitedGainIndex);

        expectWithMessage("Limit state after set limit").that(carVolumeGroup.isLimited())
                .isTrue();
        expectWithMessage("Limited current gain index")
                .that(carVolumeGroup.getCurrentGainIndex())
                .isEqualTo(limitedGainIndex);

        carVolumeGroup.resetLimit();

        expectWithMessage("Limit state after reset").that(carVolumeGroup.isLimited()).isFalse();
        expectWithMessage("Back to current gain index")
                .that(carVolumeGroup.getCurrentGainIndex())
                .isEqualTo(TEST_GAIN_INDEX);
    }

    @Test
    public void getCurrentGainIndex_whileAttenuated_thenUnattenuated() {
        CarVolumeGroup carVolumeGroup = getCarVolumeGroupWithMusicBound();
        carVolumeGroup.setCurrentGainIndex(TEST_GAIN_INDEX);
        expectWithMessage("Initial current gain index")
                .that(carVolumeGroup.getCurrentGainIndex())
                .isEqualTo(TEST_GAIN_INDEX);
        expectWithMessage("Default attenuated state").that(carVolumeGroup.isAttenuated())
                .isFalse();

        int attenuatedIndex = TEST_GAIN_INDEX - 1;
        carVolumeGroup.setAttenuatedGain(attenuatedIndex);

        expectWithMessage("Attenuated state after set attenuated").that(carVolumeGroup
                .isAttenuated()).isTrue();
        expectWithMessage("Attenuated current gain index")
                .that(carVolumeGroup.getCurrentGainIndex())
                .isEqualTo(attenuatedIndex);

        carVolumeGroup.resetAttenuation();

        expectWithMessage("Attenuated state after reset").that(carVolumeGroup.isAttenuated())
                .isFalse();
        expectWithMessage("Muted current gain index")
                .that(carVolumeGroup.getCurrentGainIndex())
                .isEqualTo(TEST_GAIN_INDEX);
    }

    @Test
    public void setCurrentGainIndex_whileBlocked_thenRemainsUnblocked() {
        CarVolumeGroup carVolumeGroup = getCarVolumeGroupWithMusicBound();
        carVolumeGroup.setCurrentGainIndex(TEST_GAIN_INDEX);

        expectWithMessage("Initial current gain index")
                .that(carVolumeGroup.getCurrentGainIndex())
                .isEqualTo(TEST_GAIN_INDEX);

        int blockedIndex = 1;
        carVolumeGroup.setBlocked(blockedIndex);

        expectWithMessage("Blocked state after set blocked").that(carVolumeGroup.isBlocked())
                .isTrue();

        carVolumeGroup.setCurrentGainIndex(blockedIndex + 1);

        expectWithMessage("Over Blocked current gain index")
                .that(carVolumeGroup.getCurrentGainIndex())
                .isEqualTo(blockedIndex);

        carVolumeGroup.setCurrentGainIndex(blockedIndex - 1);

        expectWithMessage("Under Blocked current gain index")
                .that(carVolumeGroup.getCurrentGainIndex())
                .isEqualTo(blockedIndex);
    }

    @Test
    public void setCurrentGainIndex_whileLimited_under_then_over_limit() {
        CarVolumeGroup carVolumeGroup = getCarVolumeGroupWithMusicBound();
        carVolumeGroup.setCurrentGainIndex(MAX_GAIN_INDEX);
        expectWithMessage("Initial current gain index")
                .that(carVolumeGroup.getCurrentGainIndex())
                .isEqualTo(MAX_GAIN_INDEX);
        expectWithMessage("Default limit state").that(carVolumeGroup.isLimited()).isFalse();

        int limitedGainIndex = MAX_GAIN_INDEX - 1;
        carVolumeGroup.setLimit(limitedGainIndex);

        expectWithMessage("Limit state after set limit").that(carVolumeGroup.isLimited())
                .isTrue();
        expectWithMessage("Over limit state due to over limit gain").that(carVolumeGroup
                .isOverLimit()).isTrue();

        // Underlimit
        carVolumeGroup.setCurrentGainIndex(limitedGainIndex - 1);

        expectWithMessage("Under limit current gain index")
                .that(carVolumeGroup.getCurrentGainIndex())
                .isEqualTo(limitedGainIndex - 1);

        expectWithMessage("Limit state after set limit and setting gain under limit")
                .that(carVolumeGroup.isLimited()).isTrue();
        expectWithMessage("Over limit state after set limit and setting gain under limit")
                .that(carVolumeGroup.isOverLimit()).isFalse();

        // Overlimit
        carVolumeGroup.setCurrentGainIndex(limitedGainIndex + 1);

        expectWithMessage("Over limit current gain index")
                .that(carVolumeGroup.getCurrentGainIndex())
                .isEqualTo(limitedGainIndex);

        expectWithMessage("Limit state after set limit and fail to set gain over limit")
                .that(carVolumeGroup.isLimited()).isTrue();
        // Limitation prevents to set over limited index
        expectWithMessage("Over limit state after set limit and fail to set gain over limit")
                .that(carVolumeGroup.isOverLimit()).isFalse();
    }

    @Test
    public void setCurrentGainIndex_whileAttenuated_thenUnattenuated() {
        CarVolumeGroup carVolumeGroup = getCarVolumeGroupWithMusicBound();
        carVolumeGroup.setCurrentGainIndex(TEST_GAIN_INDEX);
        expectWithMessage("Initial current gain index")
                .that(carVolumeGroup.getCurrentGainIndex())
                .isEqualTo(TEST_GAIN_INDEX);
        expectWithMessage("Default attenuated state").that(carVolumeGroup.isAttenuated())
                .isFalse();

        int attenuatedIndex = TEST_GAIN_INDEX - 2;
        carVolumeGroup.setAttenuatedGain(attenuatedIndex);

        expectWithMessage("Attenuated state after set attenuated").that(carVolumeGroup
                .isAttenuated()).isTrue();
        expectWithMessage("Attenuated current gain index")
                .that(carVolumeGroup.getCurrentGainIndex())
                .isEqualTo(attenuatedIndex);

        carVolumeGroup.setCurrentGainIndex(attenuatedIndex + 1);

        expectWithMessage("Attenuated state after reset gain index").that(carVolumeGroup
                .isAttenuated()).isFalse();
        expectWithMessage("new current gain index")
                .that(carVolumeGroup.getCurrentGainIndex())
                .isEqualTo(attenuatedIndex + 1);
    }

    @Test
    public void isOverLimit_expectedTrue() {
        CarVolumeGroup carVolumeGroup = testVolumeGroupSetup();
        carVolumeGroup.setCurrentGainIndex(MAX_GAIN_INDEX);

        List<Integer> limitReasons = List.of(Reasons.THERMAL_LIMITATION);

        AudioGainConfigInfo musicGain = new AudioGainConfigInfo();
        musicGain.zoneId = ZONE_ID;
        musicGain.devicePortAddress = MEDIA_DEVICE_ADDRESS;
        musicGain.volumeIndex = TEST_GAIN_INDEX;
        CarAudioGainConfigInfo musicCarGain = new CarAudioGainConfigInfo(musicGain);

        carVolumeGroup.onAudioGainChanged(limitReasons, musicCarGain);
        expectWithMessage("Limit state with thermal limitation")
                .that(carVolumeGroup.isLimited()).isTrue();
        expectWithMessage("Over limit state with thermal limitation")
                .that(carVolumeGroup.isOverLimit()).isTrue();
    }

    @Test
    public void isOverLimit_expectedFalse() {
        CarVolumeGroup carVolumeGroup = testVolumeGroupSetup();
        carVolumeGroup.setCurrentGainIndex(TEST_GAIN_INDEX - 1);

        List<Integer> limitReasons = List.of(Reasons.THERMAL_LIMITATION);

        AudioGainConfigInfo musicGain = new AudioGainConfigInfo();
        musicGain.zoneId = ZONE_ID;
        musicGain.devicePortAddress = MEDIA_DEVICE_ADDRESS;
        musicGain.volumeIndex = TEST_GAIN_INDEX;
        CarAudioGainConfigInfo musicCarGain = new CarAudioGainConfigInfo(musicGain);

        carVolumeGroup.onAudioGainChanged(limitReasons, musicCarGain);

        expectWithMessage("Limit state with thermal limitation while under limit")
                .that(carVolumeGroup.isLimited()).isTrue();
        expectWithMessage("Over limit state with thermal limitation while under limit")
                .that(carVolumeGroup.isOverLimit()).isFalse();
    }

    @Test
    public void onAudioGainChanged_withOverLimit_thenEndsAndRestoresVolume() {
        CarVolumeGroup carVolumeGroup = testVolumeGroupSetup();
        carVolumeGroup.setCurrentGainIndex(MAX_GAIN_INDEX);

        List<Integer> limitReasons = List.of(Reasons.THERMAL_LIMITATION);

        AudioGainConfigInfo musicGain = new AudioGainConfigInfo();
        musicGain.zoneId = ZONE_ID;
        musicGain.devicePortAddress = MEDIA_DEVICE_ADDRESS;
        musicGain.volumeIndex = DEFAULT_GAIN_INDEX;
        CarAudioGainConfigInfo musicCarGain = new CarAudioGainConfigInfo(musicGain);

        carVolumeGroup.onAudioGainChanged(limitReasons, musicCarGain);

        expectWithMessage("Over limit gain index")
                .that(carVolumeGroup.getCurrentGainIndex())
                .isEqualTo(DEFAULT_GAIN_INDEX);

        expectWithMessage("Attenuated state after set limited")
                .that(carVolumeGroup.isAttenuated()).isFalse();
        expectWithMessage("Limit state after set limited")
                .that(carVolumeGroup.isLimited()).isTrue();
        expectWithMessage("Over limit state after set limited")
                .that(carVolumeGroup.isOverLimit()).isTrue();
        expectWithMessage("BLocked state after set limited")
                .that(carVolumeGroup.isBlocked()).isFalse();

        List<Integer> noReasons = new ArrayList<>(0);
        carVolumeGroup.onAudioGainChanged(noReasons, musicCarGain);

        expectWithMessage("Attenuated state after reset limited")
                .that(carVolumeGroup.isAttenuated()).isFalse();
        expectWithMessage("Limit state after reset limited")
                .that(carVolumeGroup.isLimited()).isFalse();
        expectWithMessage("Over limit state after reset limited")
                .that(carVolumeGroup.isOverLimit()).isFalse();
        expectWithMessage("BLocked state after reset limited")
                .that(carVolumeGroup.isBlocked()).isFalse();

        expectWithMessage("Restored initial gain index")
                .that(carVolumeGroup.getCurrentGainIndex())
                .isEqualTo(MAX_GAIN_INDEX);
    }

    @Test
    public void onAudioGainChanged_withUnderLimit_thenEndsWithVolumeUnchanged() {
        CarVolumeGroup carVolumeGroup = testVolumeGroupSetup();
        carVolumeGroup.setCurrentGainIndex(MIN_GAIN_INDEX);

        List<Integer> limitReasons = List.of(Reasons.THERMAL_LIMITATION);

        AudioGainConfigInfo musicGain = new AudioGainConfigInfo();
        musicGain.zoneId = ZONE_ID;
        musicGain.devicePortAddress = MEDIA_DEVICE_ADDRESS;
        musicGain.volumeIndex = DEFAULT_GAIN_INDEX;
        CarAudioGainConfigInfo musicCarGain = new CarAudioGainConfigInfo(musicGain);

        carVolumeGroup.onAudioGainChanged(limitReasons, musicCarGain);

        expectWithMessage("Under limit gain index")
                .that(carVolumeGroup.getCurrentGainIndex())
                .isEqualTo(MIN_GAIN_INDEX);

        expectWithMessage("Attenuated state after set limited")
                .that(carVolumeGroup.isAttenuated()).isFalse();
        expectWithMessage("Limit state after set limited")
                .that(carVolumeGroup.isLimited()).isTrue();
        expectWithMessage("Over limit state after set limited")
                .that(carVolumeGroup.isOverLimit()).isFalse();
        expectWithMessage("BLocked state after set limited")
                .that(carVolumeGroup.isBlocked()).isFalse();

        List<Integer> noReasons = new ArrayList<>(0);
        carVolumeGroup.onAudioGainChanged(noReasons, musicCarGain);

        expectWithMessage("Attenuated state after reset limited")
                .that(carVolumeGroup.isAttenuated()).isFalse();
        expectWithMessage("Limit state after reset limited")
                .that(carVolumeGroup.isLimited()).isFalse();
        expectWithMessage("Over limit state after reset limited")
                .that(carVolumeGroup.isOverLimit()).isFalse();
        expectWithMessage("BLocked state after reset limited")
                .that(carVolumeGroup.isBlocked()).isFalse();

        expectWithMessage("Unchanged gain index")
                .that(carVolumeGroup.getCurrentGainIndex())
                .isEqualTo(MIN_GAIN_INDEX);
    }

    @Test
    public void onAudioGainChanged_withBlockedGain_thenEndsAndRestoresVolume() {
        CarVolumeGroup carVolumeGroup = testVolumeGroupSetup();
        carVolumeGroup.setCurrentGainIndex(DEFAULT_GAIN_INDEX);

        List<Integer> blockReasons = List.of(Reasons.TCU_MUTE);

        AudioGainConfigInfo musicGain = new AudioGainConfigInfo();
        musicGain.zoneId = ZONE_ID;
        musicGain.devicePortAddress = MEDIA_DEVICE_ADDRESS;
        musicGain.volumeIndex = MIN_GAIN_INDEX;
        CarAudioGainConfigInfo musicCarGain = new CarAudioGainConfigInfo(musicGain);

        carVolumeGroup.onAudioGainChanged(blockReasons, musicCarGain);

        expectWithMessage("Attenuated state after set blocked")
                .that(carVolumeGroup.isAttenuated()).isFalse();
        expectWithMessage("Limit state after set blocked")
                .that(carVolumeGroup.isLimited()).isFalse();
        expectWithMessage("Over limit state after set blocked")
                .that(carVolumeGroup.isOverLimit()).isFalse();
        expectWithMessage("BLocked state after set blocked")
                .that(carVolumeGroup.isBlocked()).isTrue();

        expectWithMessage("Blocked gain index")
                .that(carVolumeGroup.getCurrentGainIndex())
                .isEqualTo(MIN_GAIN_INDEX);

        List<Integer> noReasons = new ArrayList<>(0);
        carVolumeGroup.onAudioGainChanged(noReasons, musicCarGain);

        expectWithMessage("Attenuated state after reset blocked")
                .that(carVolumeGroup.isAttenuated()).isFalse();
        expectWithMessage("Limit state after reset blocked")
                .that(carVolumeGroup.isLimited()).isFalse();
        expectWithMessage("Over limit state after reset blocked")
                .that(carVolumeGroup.isOverLimit()).isFalse();
        expectWithMessage("BLocked state after reset blocked")
                .that(carVolumeGroup.isBlocked()).isFalse();

        expectWithMessage("Restored initial gain index")
                .that(carVolumeGroup.getCurrentGainIndex())
                .isEqualTo(DEFAULT_GAIN_INDEX);
    }

    @Test
    public void onAudioGainChanged_withAttenuatedGain_thenEndsAndRestoresVolume() {
        CarVolumeGroup carVolumeGroup = testVolumeGroupSetup();
        carVolumeGroup.setCurrentGainIndex(DEFAULT_GAIN_INDEX);
        int attenuatedIndex = DEFAULT_GAIN_INDEX - 1;

        List<Integer> attenuateReasons = List.of(Reasons.ADAS_DUCKING);

        AudioGainConfigInfo musicGain = new AudioGainConfigInfo();
        musicGain.zoneId = ZONE_ID;
        musicGain.devicePortAddress = MEDIA_DEVICE_ADDRESS;
        musicGain.volumeIndex = attenuatedIndex;
        CarAudioGainConfigInfo musicCarGain = new CarAudioGainConfigInfo(musicGain);

        carVolumeGroup.onAudioGainChanged(attenuateReasons, musicCarGain);

        expectWithMessage("Attenuated state after set attenuated")
                .that(carVolumeGroup.isAttenuated()).isTrue();
        expectWithMessage("Limit state after set attenuated")
                .that(carVolumeGroup.isLimited()).isFalse();
        expectWithMessage("Over limit state after set attenuated")
                .that(carVolumeGroup.isOverLimit()).isFalse();
        expectWithMessage("BLocked state after set attenuated")
                .that(carVolumeGroup.isBlocked()).isFalse();

        expectWithMessage("Attenuated gain index")
                .that(carVolumeGroup.getCurrentGainIndex())
                .isEqualTo(attenuatedIndex);

        List<Integer> noReasons = new ArrayList<>(0);
        carVolumeGroup.onAudioGainChanged(noReasons, musicCarGain);

        expectWithMessage("Attenuated state after reset attenuated")
                .that(carVolumeGroup.isAttenuated()).isFalse();
        expectWithMessage("Limit state after reset attenuated")
                .that(carVolumeGroup.isLimited()).isFalse();
        expectWithMessage("Over limit state after reset attenuated")
                .that(carVolumeGroup.isOverLimit()).isFalse();
        expectWithMessage("BLocked state after reset attenuated")
                .that(carVolumeGroup.isBlocked()).isFalse();

        expectWithMessage("Restored initial gain index")
                .that(carVolumeGroup.getCurrentGainIndex())
                .isEqualTo(DEFAULT_GAIN_INDEX);
    }

    @Test
    public void onAudioGainChanged_withBlockingLimitAndAttenuation() {
        CarVolumeGroup carVolumeGroup = testVolumeGroupSetup();

        List<Integer> allReasons =
                List.of(
                        -1,
                        -10,
                        666,
                        Reasons.FORCED_MASTER_MUTE,
                        Reasons.TCU_MUTE,
                        Reasons.REMOTE_MUTE,
                        Reasons.THERMAL_LIMITATION,
                        Reasons.SUSPEND_EXIT_VOL_LIMITATION,
                        Reasons.ADAS_DUCKING,
                        Reasons.ADAS_DUCKING);

        AudioGainConfigInfo musicGain = new AudioGainConfigInfo();
        musicGain.zoneId = ZONE_ID;
        musicGain.devicePortAddress = MEDIA_DEVICE_ADDRESS;
        musicGain.volumeIndex = DEFAULT_GAIN_INDEX;
        CarAudioGainConfigInfo musicCarGain = new CarAudioGainConfigInfo(musicGain);

        carVolumeGroup.onAudioGainChanged(allReasons, musicCarGain);

        expectWithMessage("Attenuated state while blocked, limited, and attenuated")
                .that(carVolumeGroup.isAttenuated()).isTrue();
        expectWithMessage("Limit state while blocked, limited, and attenuated")
                .that(carVolumeGroup.isLimited()).isTrue();
        expectWithMessage("Blocked state while blocked, limited, and attenuated")
                .that(carVolumeGroup.isBlocked()).isTrue();
    }

    @Test
    public void onAudioGainChanged_resettingBlockingLimitAndAttenuation() {
        CarVolumeGroup carVolumeGroup = testVolumeGroupSetup();

        List<Integer> noReasons = new ArrayList<>(0);

        AudioGainConfigInfo musicGain = new AudioGainConfigInfo();
        musicGain.zoneId = ZONE_ID;
        musicGain.devicePortAddress = MEDIA_DEVICE_ADDRESS;
        musicGain.volumeIndex = DEFAULT_GAIN_INDEX;
        CarAudioGainConfigInfo musicCarGain = new CarAudioGainConfigInfo(musicGain);

        carVolumeGroup.onAudioGainChanged(noReasons, musicCarGain);

        expectWithMessage("Attenuated state after reset of blocked, limited, and attenuated")
                .that(carVolumeGroup.isAttenuated()).isFalse();
        expectWithMessage("Limit state after reset of blocked, limited, and attenuated")
                .that(carVolumeGroup.isLimited()).isFalse();
        expectWithMessage("Blocked state after reset of blocked, limited, and attenuated")
                .that(carVolumeGroup.isBlocked()).isFalse();
    }

    @Test
    public void onAudioGainChanged_setResettingBlockingLimitAndAttenuation() {
        CarVolumeGroup carVolumeGroup = testVolumeGroupSetup();

        List<Integer> allReasons =
                List.of(
                        Reasons.FORCED_MASTER_MUTE,
                        Reasons.TCU_MUTE,
                        Reasons.REMOTE_MUTE,
                        Reasons.THERMAL_LIMITATION,
                        Reasons.SUSPEND_EXIT_VOL_LIMITATION,
                        Reasons.ADAS_DUCKING,
                        Reasons.ADAS_DUCKING);
        AudioGainConfigInfo musicGain = new AudioGainConfigInfo();
        musicGain.zoneId = ZONE_ID;
        musicGain.devicePortAddress = MEDIA_DEVICE_ADDRESS;
        musicGain.volumeIndex = DEFAULT_GAIN_INDEX;
        CarAudioGainConfigInfo musicCarGain = new CarAudioGainConfigInfo(musicGain);
        carVolumeGroup.onAudioGainChanged(allReasons, musicCarGain);


        List<Integer> noReasons = new ArrayList<>(0);

        carVolumeGroup.onAudioGainChanged(noReasons, musicCarGain);

        expectWithMessage("Attenuated state after reset of blocked, limited, and attenuated")
                .that(carVolumeGroup.isAttenuated()).isFalse();
        expectWithMessage("Limit state after reset of blocked, limited, and attenuated")
                .that(carVolumeGroup.isLimited()).isFalse();
        expectWithMessage("Blocked state after reset of blocked, limited, and attenuated")
                .that(carVolumeGroup.isBlocked()).isFalse();
    }

    @Test
    public void onAudioGainChanged_validGain() {
        CarVolumeGroup carVolumeGroup = testVolumeGroupSetup();

        List<Integer> reasons = List.of(Reasons.REMOTE_MUTE, Reasons.NAV_DUCKING);
        AudioGainConfigInfo musicGain = new AudioGainConfigInfo();
        musicGain.zoneId = ZONE_ID;
        musicGain.devicePortAddress = MEDIA_DEVICE_ADDRESS;
        musicGain.volumeIndex = DEFAULT_GAIN_INDEX;
        CarAudioGainConfigInfo musicCarGain = new CarAudioGainConfigInfo(musicGain);

        AudioGainConfigInfo navGain = new AudioGainConfigInfo();
        navGain.zoneId = ZONE_ID;
        navGain.devicePortAddress = NAVIGATION_DEVICE_ADDRESS;
        navGain.volumeIndex = DEFAULT_GAIN_INDEX;
        CarAudioGainConfigInfo navCarGain = new CarAudioGainConfigInfo(navGain);

        carVolumeGroup.onAudioGainChanged(reasons, musicCarGain);
        // Broadcasted to all CarAudioDeviceInfo
        verify(mMediaDeviceInfo).setCurrentGain(eq(DEFAULT_GAIN));
        verify(mNavigationDeviceInfo).setCurrentGain(eq(DEFAULT_GAIN));

        carVolumeGroup.onAudioGainChanged(reasons, navCarGain);
        // Broadcasted to all CarAudioDeviceInfo
        verify(mMediaDeviceInfo, times(2)).setCurrentGain(eq(DEFAULT_GAIN));
        verify(mNavigationDeviceInfo, times(2)).setCurrentGain(eq(DEFAULT_GAIN));
    }

    @Test
    public void onAudioGainChanged_invalidGain() {
        CarVolumeGroup carVolumeGroup = testVolumeGroupSetup();

        List<Integer> reasons = List.of(Reasons.REMOTE_MUTE, Reasons.NAV_DUCKING);
        AudioGainConfigInfo unknownGain = new AudioGainConfigInfo();
        unknownGain.zoneId = ZONE_ID;
        unknownGain.devicePortAddress = OTHER_ADDRESS;
        unknownGain.volumeIndex = 666;
        CarAudioGainConfigInfo unknownCarGain = new CarAudioGainConfigInfo(unknownGain);

        carVolumeGroup.onAudioGainChanged(reasons, unknownCarGain);

        verify(mMediaDeviceInfo, never()).setCurrentGain(anyInt());
        verify(mNavigationDeviceInfo, never()).setCurrentGain(anyInt());
    }

    @Test
    public void getCarVolumeGroupInfo() {
        CarVolumeGroup carVolumeGroup = testVolumeGroupSetup();
        carVolumeGroup.setCurrentGainIndex(0);

        CarVolumeGroupInfo info = carVolumeGroup.getCarVolumeGroupInfo();

        expectWithMessage("Car volume group info id")
                .that(info.getId()).isEqualTo(ZONE_ID);
        expectWithMessage("Car volume group info zone id")
                .that(info.getId()).isEqualTo(GROUP_ID);
        expectWithMessage("Car volume group info current gain")
                .that(info.getVolumeGainIndex()).isEqualTo(carVolumeGroup.getCurrentGainIndex());
        expectWithMessage("Car volume group info max gain")
                .that(info.getMaxVolumeGainIndex()).isEqualTo(carVolumeGroup.getMaxGainIndex());
        expectWithMessage("Car volume group info min gain")
                .that(info.getMinVolumeGainIndex()).isEqualTo(carVolumeGroup.getMinGainIndex());
        expectWithMessage("Car volume group info muted state")
                .that(info.isMuted()).isEqualTo(carVolumeGroup.isMuted());
        expectWithMessage("Car volume group info blocked state")
                .that(info.isBlocked()).isEqualTo(carVolumeGroup.isBlocked());
        expectWithMessage("Car volume group info attenuated state")
                .that(info.isAttenuated()).isEqualTo(carVolumeGroup.isAttenuated());
    }

    @Test
    public void getAudioAttributes() {
        CarVolumeGroup carVolumeGroup = getCarVolumeGroupWithMusicBound();

        List<AudioAttributes> audioAttributes = carVolumeGroup.getAudioAttributes();

        expectWithMessage("Group audio attributes").that(audioAttributes).containsExactly(
                CarAudioContext.getAudioAttributeFromUsage(USAGE_MEDIA),
                CarAudioContext.getAudioAttributeFromUsage(USAGE_GAME),
                CarAudioContext.getAudioAttributeFromUsage(USAGE_UNKNOWN));

    }

    private CarVolumeGroup getCarVolumeGroupWithMusicBound() {
        return getBuilder()
                .setDeviceInfoForContext(TEST_MEDIA_CONTEXT_ID, mMediaDeviceInfo)
                .build();
    }

    private CarVolumeGroup getCarVolumeGroupWithNavigationBound(CarAudioSettings settings,
            boolean useCarVolumeGroupMute) {
        return new CarVolumeGroup.Builder(settings, TEST_CAR_AUDIO_CONTEXT,
                0, 0, useCarVolumeGroupMute)
                .setDeviceInfoForContext(TEST_NAVIGATION_CONTEXT_ID, mNavigationDeviceInfo)
                .build();
    }

    CarVolumeGroup getVolumeGroupWithMuteAndNavBound(boolean isMuted, boolean persistMute,
            boolean useCarVolumeGroupMute) {
        CarAudioSettings settings = new SettingsBuilder(0, 0)
                .setMuteForUser10(isMuted)
                .setIsPersistVolumeGroupEnabled(persistMute)
                .build();
        return getCarVolumeGroupWithNavigationBound(settings, useCarVolumeGroupMute);
    }

    private CarVolumeGroup testVolumeGroupSetup() {
        CarVolumeGroup.Builder builder = getBuilder();

        builder.setDeviceInfoForContext(TEST_MEDIA_CONTEXT_ID, mMediaDeviceInfo);
        builder.setDeviceInfoForContext(TEST_CALL_CONTEXT_ID, mMediaDeviceInfo);
        builder.setDeviceInfoForContext(TEST_CALL_RING_CONTEXT_ID, mMediaDeviceInfo);

        builder.setDeviceInfoForContext(TEST_NAVIGATION_CONTEXT_ID, mNavigationDeviceInfo);
        builder.setDeviceInfoForContext(TEST_ALARM_CONTEXT_ID, mNavigationDeviceInfo);
        builder.setDeviceInfoForContext(TEST_NOTIFICATION_CONTEXT_ID, mNavigationDeviceInfo);

        return builder.build();
    }

    CarVolumeGroup.Builder getBuilder() {
        return new CarVolumeGroup.Builder(mSettingsMock, TEST_CAR_AUDIO_CONTEXT,
                ZONE_ID, GROUP_ID, /* useCarVolumeGroupMute= */ true);
    }

    private static final class SettingsBuilder {
        private final SparseIntArray mStoredGainIndexes = new SparseIntArray();
        private final SparseBooleanArray mStoreMuteStates = new SparseBooleanArray();
        private final int mZoneId;
        private final int mGroupId;

        private boolean mPersistMute;

        SettingsBuilder(int zoneId, int groupId) {
            mZoneId = zoneId;
            mGroupId = groupId;
        }

        SettingsBuilder setGainIndexForUser(@UserIdInt int userId) {
            mStoredGainIndexes.put(userId, TEST_GAIN_INDEX);
            return this;
        }

        SettingsBuilder setMuteForUser10(boolean mute) {
            mStoreMuteStates.put(CarVolumeGroupUnitTest.TEST_USER_10, mute);
            return this;
        }

        SettingsBuilder setIsPersistVolumeGroupEnabled(boolean persistMute) {
            mPersistMute = persistMute;
            return this;
        }

        CarAudioSettings build() {
            CarAudioSettings settingsMock = Mockito.mock(CarAudioSettings.class);
            for (int storeIndex = 0; storeIndex < mStoredGainIndexes.size(); storeIndex++) {
                int gainUserId = mStoredGainIndexes.keyAt(storeIndex);
                when(settingsMock
                        .getStoredVolumeGainIndexForUser(gainUserId, mZoneId,
                                mGroupId)).thenReturn(
                        mStoredGainIndexes.get(gainUserId, DEFAULT_GAIN));
            }
            for (int muteIndex = 0; muteIndex < mStoreMuteStates.size(); muteIndex++) {
                int muteUserId = mStoreMuteStates.keyAt(muteIndex);
                when(settingsMock.getVolumeGroupMuteForUser(muteUserId, mZoneId, mGroupId))
                        .thenReturn(mStoreMuteStates.get(muteUserId, false));
                when(settingsMock.isPersistVolumeGroupMuteEnabled(muteUserId))
                        .thenReturn(mPersistMute);
            }
            return settingsMock;
        }
    }

    private static final class InfoBuilder {
        private final String mAddress;

        private int mStepValue = STEP_VALUE;
        private int mDefaultGain = DEFAULT_GAIN;
        private int mMinGain = MIN_GAIN;
        private int mMaxGain = MAX_GAIN;

        InfoBuilder(String address) {
            mAddress = address;
        }

        InfoBuilder setStepValue(int stepValue) {
            mStepValue = stepValue;
            return this;
        }

        InfoBuilder setDefaultGain(int defaultGain) {
            mDefaultGain = defaultGain;
            return this;
        }

        InfoBuilder setMinGain(int minGain) {
            mMinGain = minGain;
            return this;
        }

        InfoBuilder setMaxGain(int maxGain) {
            mMaxGain = maxGain;
            return this;
        }

        CarAudioDeviceInfo build() {
            CarAudioDeviceInfo infoMock = Mockito.mock(CarAudioDeviceInfo.class);
            when(infoMock.getStepValue()).thenReturn(mStepValue);
            when(infoMock.getDefaultGain()).thenReturn(mDefaultGain);
            when(infoMock.getMaxGain()).thenReturn(mMaxGain);
            when(infoMock.getMinGain()).thenReturn(mMinGain);
            when(infoMock.getAddress()).thenReturn(mAddress);
            return infoMock;
        }
    }
}
