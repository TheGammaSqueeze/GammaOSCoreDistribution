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
package com.android.car.audio;

import static android.media.AudioAttributes.USAGE_ALARM;
import static android.media.AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE;
import static android.media.AudioAttributes.USAGE_ASSISTANCE_SONIFICATION;
import static android.media.AudioAttributes.USAGE_ASSISTANT;
import static android.media.AudioAttributes.USAGE_MEDIA;

import static com.android.car.audio.CarAudioContext.AudioContext;

import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.car.media.CarAudioManager;
import android.car.media.CarVolumeGroupInfo;
import android.car.test.AbstractExpectableTestCase;
import android.hardware.automotive.audiocontrol.AudioGainConfigInfo;
import android.hardware.automotive.audiocontrol.Reasons;
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioPlaybackConfiguration;
import android.util.SparseArray;

import com.google.common.collect.ImmutableList;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RunWith(MockitoJUnitRunner.class)
public class CarAudioZoneTest extends AbstractExpectableTestCase {
    private static final String MUSIC_ADDRESS = "bus0_music";
    private static final String NAV_ADDRESS = "bus1_nav";
    private static final String VOICE_ADDRESS = "bus3_voice";
    private static final String ASSISTANT_ADDRESS = "bus10_assistant";
    private static final String ALARM_ADDRESS = "bus11_alarm";
    private static final String ANNOUNCEMENT_ADDRESS = "bus12_announcement";

    private static final AudioAttributes TEST_MEDIA_ATTRIBUTE =
            CarAudioContext.getAudioAttributeFromUsage(USAGE_MEDIA);
    private static final AudioAttributes TEST_ALARM_ATTRIBUTE =
            CarAudioContext.getAudioAttributeFromUsage(USAGE_ALARM);
    private static final AudioAttributes TEST_ASSISTANT_ATTRIBUTE =
            CarAudioContext.getAudioAttributeFromUsage(USAGE_ASSISTANT);
    private static final AudioAttributes TEST_NAVIGATION_ATTRIBUTE =
            CarAudioContext.getAudioAttributeFromUsage(USAGE_ASSISTANCE_NAVIGATION_GUIDANCE);
    private static final AudioAttributes TEST_SYSTEM_ATTRIBUTE =
            CarAudioContext.getAudioAttributeFromUsage(USAGE_ASSISTANCE_SONIFICATION);

    private static final CarAudioContext TEST_CAR_AUDIO_CONTEXT =
            new CarAudioContext(CarAudioContext.getAllContextsInfo());

    @Mock
    private CarVolumeGroup mMockMusicGroup;
    @Mock
    private CarVolumeGroup mMockNavGroup;
    @Mock
    private CarVolumeGroup mMockVoiceGroup;

    private CarAudioZone mTestAudioZone = new CarAudioZone(TEST_CAR_AUDIO_CONTEXT, "Primary zone",
            CarAudioManager.PRIMARY_AUDIO_ZONE);

    private static final @AudioContext int TEST_MEDIA_CONTEXT =
            TEST_CAR_AUDIO_CONTEXT.getContextForAudioAttribute(TEST_MEDIA_ATTRIBUTE);
    private static final  @AudioContext int TEST_ALARM_CONTEXT =
            TEST_CAR_AUDIO_CONTEXT.getContextForAudioAttribute(TEST_ALARM_ATTRIBUTE);
    private static final  @AudioContext int TEST_ASSISTANT_CONTEXT =
            TEST_CAR_AUDIO_CONTEXT.getContextForAudioAttribute(TEST_ASSISTANT_ATTRIBUTE);
    private static final  @AudioContext int TEST_NAVIGATION_CONTEXT =
            TEST_CAR_AUDIO_CONTEXT.getContextForAudioAttribute(TEST_NAVIGATION_ATTRIBUTE);

    @Before
    public void setUp() {
        mMockMusicGroup = new VolumeGroupBuilder()
                .addDeviceAddressAndContexts(TEST_MEDIA_CONTEXT, MUSIC_ADDRESS).build();

        mMockNavGroup = new VolumeGroupBuilder()
                .addDeviceAddressAndContexts(TEST_NAVIGATION_CONTEXT, NAV_ADDRESS).build();

        mMockVoiceGroup = new VolumeGroupBuilder()
                .addDeviceAddressAndContexts(TEST_ASSISTANT_CONTEXT, VOICE_ADDRESS).build();
    }

    @Test
    public void getAddressForContext_returnsExpectedDeviceAddress() {
        mTestAudioZone.addVolumeGroup(mMockMusicGroup);
        mTestAudioZone.addVolumeGroup(mMockNavGroup);

        String musicAddress = mTestAudioZone.getAddressForContext(
                TEST_MEDIA_CONTEXT);
        expectWithMessage("Music volume group address")
                .that(musicAddress).isEqualTo(MUSIC_ADDRESS);

        String navAddress = mTestAudioZone.getAddressForContext(
                TEST_NAVIGATION_CONTEXT);
        expectWithMessage("Navigation volume group address")
                .that(navAddress).matches(NAV_ADDRESS);
    }

    @Test
    public void getAddressForContext_throwsOnInvalidContext() {
        IllegalArgumentException thrown =
                assertThrows(IllegalArgumentException.class,
                        () -> mTestAudioZone.getAddressForContext(CarAudioContext
                                .getInvalidContext()));

        expectWithMessage("Invalid context exception").that(thrown).hasMessageThat()
                .contains("is invalid");
    }

    @Test
    public void getAddressForContext_throwsOnNonExistentContext() {
        IllegalStateException thrown =
                assertThrows(IllegalStateException.class,
                        () -> mTestAudioZone.getAddressForContext(
                                TEST_MEDIA_CONTEXT));

        expectWithMessage("Non-existing context exception").that(thrown).hasMessageThat()
                .contains("Could not find output device in zone");
    }

    @Test
    public void findActiveAudioAttributesFromPlaybackConfigurations_withNullConfig_fails() {
        mTestAudioZone.addVolumeGroup(mMockMusicGroup);
        mTestAudioZone.addVolumeGroup(mMockNavGroup);
        mTestAudioZone.addVolumeGroup(mMockVoiceGroup);

        NullPointerException thrown =
                assertThrows(NullPointerException.class,
                        () -> mTestAudioZone
                                .findActiveAudioAttributesFromPlaybackConfigurations(null));

        expectWithMessage("Null playback configuration exception").that(thrown)
                .hasMessageThat().contains("Audio playback configurations can not be null");
    }

    @Test
    public void findActiveAudioAttributesFromPlaybackConfigurations_returnsAllActiveAttributes() {
        mTestAudioZone.addVolumeGroup(mMockMusicGroup);
        mTestAudioZone.addVolumeGroup(mMockNavGroup);
        mTestAudioZone.addVolumeGroup(mMockVoiceGroup);
        List<AudioPlaybackConfiguration> activeConfigurations = ImmutableList.of(
                new Builder().setUsage(USAGE_MEDIA).setDeviceAddress(MUSIC_ADDRESS).build(),
                new Builder().setUsage(USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                        .setDeviceAddress(NAV_ADDRESS).build()
        );

        List<AudioAttributes> activeAttributes = mTestAudioZone
                .findActiveAudioAttributesFromPlaybackConfigurations(activeConfigurations);

        expectWithMessage("Active playback audio attributes").that(activeAttributes)
                .containsExactly(TEST_MEDIA_ATTRIBUTE, TEST_NAVIGATION_ATTRIBUTE);
    }

    @Test
    public void findActiveAudioAttributesFromPlaybackConfigurations_returnsNoMatchingAttributes() {
        mTestAudioZone.addVolumeGroup(mMockMusicGroup);
        mTestAudioZone.addVolumeGroup(mMockNavGroup);
        mTestAudioZone.addVolumeGroup(mMockVoiceGroup);
        List<AudioPlaybackConfiguration> activeConfigurations = ImmutableList.of(
                new Builder().setUsage(USAGE_ASSISTANT)
                        .setDeviceAddress(ANNOUNCEMENT_ADDRESS).build(),
                new Builder().setUsage(USAGE_ALARM)
                        .setDeviceAddress(ALARM_ADDRESS).build()
        );

        List<AudioAttributes> activeAttributes = mTestAudioZone
                .findActiveAudioAttributesFromPlaybackConfigurations(activeConfigurations);

        expectWithMessage("Non matching active playback audio attributes")
                .that(activeAttributes).isEmpty();
    }

    @Test
    public void findActiveAudioAttributesFromPlaybackConfigurations_returnAllAttributes() {
        mTestAudioZone.addVolumeGroup(mMockMusicGroup);
        mTestAudioZone.addVolumeGroup(mMockNavGroup);
        mTestAudioZone.addVolumeGroup(new VolumeGroupBuilder()
                .addDeviceAddressAndContexts(TEST_ASSISTANT_CONTEXT, ASSISTANT_ADDRESS)
                .addDeviceAddressAndContexts(TEST_ALARM_CONTEXT, ALARM_ADDRESS)
                .build());
        List<AudioPlaybackConfiguration> activeConfigurations = ImmutableList.of(
                new Builder().setUsage(USAGE_ASSISTANT)
                        .setDeviceAddress(ASSISTANT_ADDRESS).build(),
                new Builder().setUsage(USAGE_ALARM)
                        .setDeviceAddress(ALARM_ADDRESS).build()
        );

        List<AudioAttributes> activeAttributes = mTestAudioZone
                .findActiveAudioAttributesFromPlaybackConfigurations(activeConfigurations);

        expectWithMessage("Single volume group active playback audio attributes")
                .that(activeAttributes)
                .containsExactly(TEST_ASSISTANT_ATTRIBUTE, TEST_ALARM_ATTRIBUTE);
    }

    @Test
    public void findActiveAudioAttributesFromPlaybackConfigurations_missingAddress_retAttribute() {
        mTestAudioZone.addVolumeGroup(new VolumeGroupBuilder()
                .addDeviceAddressAndContexts(TEST_ASSISTANT_CONTEXT, ASSISTANT_ADDRESS)
                .addDeviceAddressAndContexts(TEST_ALARM_CONTEXT, ASSISTANT_ADDRESS)
                .addDeviceAddressAndContexts(TEST_MEDIA_CONTEXT, ASSISTANT_ADDRESS)
                .build());
        List<AudioPlaybackConfiguration> activeConfigurations = ImmutableList.of(
                new Builder().setUsage(USAGE_ALARM)
                        .setDeviceAddress(ASSISTANT_ADDRESS).build(),
                new Builder().setUsage(USAGE_MEDIA)
                        .setDeviceAddress(MUSIC_ADDRESS).build()
        );

        List<AudioAttributes> activeAttributes = mTestAudioZone
                .findActiveAudioAttributesFromPlaybackConfigurations(activeConfigurations);

        expectWithMessage("Non matching address active playback audio attributes")
                .that(activeAttributes).containsExactly(TEST_ALARM_ATTRIBUTE);
    }

    @Test
    public void
            findActiveAudioAttributesFromPlaybackConfigurations_withNonMatchContext_retAttr() {
        mTestAudioZone.addVolumeGroup(mMockMusicGroup);
        mTestAudioZone.addVolumeGroup(mMockNavGroup);
        mTestAudioZone.addVolumeGroup(new VolumeGroupBuilder()
                .addDeviceAddressAndContexts(TEST_ASSISTANT_CONTEXT, ASSISTANT_ADDRESS)
                .build());
        List<AudioPlaybackConfiguration> activeConfigurations = ImmutableList.of(
                new Builder().setUsage(USAGE_ASSISTANCE_SONIFICATION)
                        .setDeviceAddress(ASSISTANT_ADDRESS).build()
        );

        List<AudioAttributes> activeAttributes = mTestAudioZone
                .findActiveAudioAttributesFromPlaybackConfigurations(activeConfigurations);

        expectWithMessage("Non matching context active playback audio attributes")
                .that(activeAttributes).containsExactly(TEST_SYSTEM_ATTRIBUTE);
    }

    @Test
    public void findActiveAudioAttributesFromPlaybackConfigurations_withMultiGroupMatch() {
        mTestAudioZone.addVolumeGroup(mMockMusicGroup);
        mTestAudioZone.addVolumeGroup(mMockNavGroup);
        mTestAudioZone.addVolumeGroup(new VolumeGroupBuilder()
                .addDeviceAddressAndContexts(TEST_ASSISTANT_CONTEXT, ASSISTANT_ADDRESS)
                .addDeviceAddressAndContexts(TEST_ALARM_CONTEXT, ALARM_ADDRESS)
                .build());
        List<AudioPlaybackConfiguration> activeConfigurations = ImmutableList.of(
                new Builder().setUsage(USAGE_ALARM)
                        .setDeviceAddress(ALARM_ADDRESS).build(),
                new Builder().setUsage(USAGE_MEDIA)
                        .setDeviceAddress(MUSIC_ADDRESS).build()
        );

        List<AudioAttributes> activeContexts = mTestAudioZone
                .findActiveAudioAttributesFromPlaybackConfigurations(activeConfigurations);

        expectWithMessage("Multi group match active playback audio attributes")
                .that(activeContexts).containsExactly(TEST_ALARM_ATTRIBUTE, TEST_MEDIA_ATTRIBUTE);
    }

    @Test
    public void
            findActiveAudioAttributesFromPlaybackConfigurations_onEmptyConfigurations_retEmpty() {
        mTestAudioZone.addVolumeGroup(mMockMusicGroup);
        mTestAudioZone.addVolumeGroup(mMockNavGroup);
        mTestAudioZone.addVolumeGroup(mMockVoiceGroup);
        List<AudioPlaybackConfiguration> activeConfigurations = ImmutableList.of();

        List<AudioAttributes> activeAttributes = mTestAudioZone
                .findActiveAudioAttributesFromPlaybackConfigurations(activeConfigurations);

        expectWithMessage("Empty active playback audio attributes")
                .that(activeAttributes).isEmpty();
    }

    @Test
    public void findActiveAudioAttributesFromPlaybackConfigurations_onNullConfigurations_fails() {
        mTestAudioZone.addVolumeGroup(mMockMusicGroup);
        mTestAudioZone.addVolumeGroup(mMockNavGroup);
        mTestAudioZone.addVolumeGroup(mMockVoiceGroup);
        List<AudioPlaybackConfiguration> activeConfigurations = null;

        assertThrows(NullPointerException.class,
                () -> mTestAudioZone
                        .findActiveAudioAttributesFromPlaybackConfigurations(activeConfigurations));
    }

    @Test
    public void isAudioDeviceInfoValidForZone_withNullAudioDeviceInfo_returnsFalse() {
        mTestAudioZone.addVolumeGroup(mMockMusicGroup);

        expectWithMessage("Null audio device info")
                .that(mTestAudioZone.isAudioDeviceInfoValidForZone(null)).isFalse();
    }

    @Test
    public void isAudioDeviceInfoValidForZone_withNullDeviceAddress_returnsFalse() {
        mTestAudioZone.addVolumeGroup(mMockMusicGroup);
        AudioDeviceInfo nullAddressDeviceInfo = Mockito.mock(AudioDeviceInfo.class);
        when(nullAddressDeviceInfo.getAddress()).thenReturn(null);

        expectWithMessage("Invalid audio device info").that(
                mTestAudioZone.isAudioDeviceInfoValidForZone(nullAddressDeviceInfo)).isFalse();
    }

    @Test
    public void isAudioDeviceInfoValidForZone_withEmptyDeviceAddress_returnsFalse() {
        mTestAudioZone.addVolumeGroup(mMockMusicGroup);
        AudioDeviceInfo nullAddressDeviceInfo = Mockito.mock(AudioDeviceInfo.class);
        when(nullAddressDeviceInfo.getAddress()).thenReturn("");

        expectWithMessage("Device info with invalid address").that(
                mTestAudioZone.isAudioDeviceInfoValidForZone(nullAddressDeviceInfo)).isFalse();
    }

    @Test
    public void isAudioDeviceInfoValidForZone_withDeviceAddressNotInZone_returnsFalse() {
        mTestAudioZone.addVolumeGroup(mMockMusicGroup);
        AudioDeviceInfo nullAddressDeviceInfo = Mockito.mock(AudioDeviceInfo.class);
        when(nullAddressDeviceInfo.getAddress()).thenReturn(VOICE_ADDRESS);

        expectWithMessage("Non zone audio device info").that(
                mTestAudioZone.isAudioDeviceInfoValidForZone(nullAddressDeviceInfo)).isFalse();
    }

    @Test
    public void isAudioDeviceInfoValidForZone_withDeviceAddressInZone_returnsTrue() {
        mTestAudioZone.addVolumeGroup(mMockMusicGroup);
        AudioDeviceInfo nullAddressDeviceInfo = Mockito.mock(AudioDeviceInfo.class);
        when(nullAddressDeviceInfo.getAddress()).thenReturn(MUSIC_ADDRESS);

        expectWithMessage("Valid audio device info").that(
                mTestAudioZone.isAudioDeviceInfoValidForZone(nullAddressDeviceInfo)).isTrue();
    }

    @Test
    public void onAudioGainChanged_withDeviceAddressesInZone() {
        mTestAudioZone.addVolumeGroup(mMockMusicGroup);
        mTestAudioZone.addVolumeGroup(mMockNavGroup);

        List<Integer> reasons = List.of(Reasons.REMOTE_MUTE, Reasons.NAV_DUCKING);

        AudioGainConfigInfo musicGainInfo = new AudioGainConfigInfo();
        musicGainInfo.zoneId = CarAudioManager.PRIMARY_AUDIO_ZONE;
        musicGainInfo.devicePortAddress = MUSIC_ADDRESS;
        musicGainInfo.volumeIndex = 666;
        CarAudioGainConfigInfo carMusicGainInfo = new CarAudioGainConfigInfo(musicGainInfo);
        AudioGainConfigInfo navGainInfo = new AudioGainConfigInfo();
        navGainInfo.zoneId = CarAudioManager.PRIMARY_AUDIO_ZONE;
        navGainInfo.devicePortAddress = NAV_ADDRESS;
        navGainInfo.volumeIndex = 999;
        CarAudioGainConfigInfo carNavGainInfo = new CarAudioGainConfigInfo(navGainInfo);

        List<CarAudioGainConfigInfo> carGains = List.of(carMusicGainInfo, carNavGainInfo);

        mTestAudioZone.onAudioGainChanged(reasons, carGains);

        verify(mMockMusicGroup).onAudioGainChanged(eq(reasons), eq(carMusicGainInfo));
        verify(mMockNavGroup).onAudioGainChanged(eq(reasons), eq(carNavGainInfo));
        verify(mMockVoiceGroup, never()).onAudioGainChanged(any(), any());
    }

    @Test
    public void onAudioGainChanged_withoutAnyDeviceAddressInZone() {
        List<Integer> reasons = List.of(Reasons.REMOTE_MUTE, Reasons.NAV_DUCKING);

        AudioGainConfigInfo musicGainInfo = new AudioGainConfigInfo();
        musicGainInfo.zoneId = CarAudioManager.PRIMARY_AUDIO_ZONE;
        musicGainInfo.devicePortAddress = MUSIC_ADDRESS;
        musicGainInfo.volumeIndex = 666;
        CarAudioGainConfigInfo carMusicGainInfo = new CarAudioGainConfigInfo(musicGainInfo);
        AudioGainConfigInfo navGainInfo = new AudioGainConfigInfo();
        navGainInfo.zoneId = CarAudioManager.PRIMARY_AUDIO_ZONE;
        navGainInfo.devicePortAddress = NAV_ADDRESS;
        navGainInfo.volumeIndex = 999;
        CarAudioGainConfigInfo carNavGainInfo = new CarAudioGainConfigInfo(navGainInfo);
        AudioGainConfigInfo voiceGainInfo = new AudioGainConfigInfo();
        voiceGainInfo.zoneId = CarAudioManager.PRIMARY_AUDIO_ZONE;
        voiceGainInfo.devicePortAddress = VOICE_ADDRESS;
        voiceGainInfo.volumeIndex = 777;
        CarAudioGainConfigInfo carVoiceGainInfo = new CarAudioGainConfigInfo(voiceGainInfo);

        List<CarAudioGainConfigInfo> carGains =
                List.of(carMusicGainInfo, carNavGainInfo, carVoiceGainInfo);

        mTestAudioZone.onAudioGainChanged(reasons, carGains);

        verify(mMockMusicGroup, never()).onAudioGainChanged(any(), any());
        verify(mMockNavGroup, never()).onAudioGainChanged(any(), any());
        verify(mMockVoiceGroup, never()).onAudioGainChanged(any(), any());
    }

    @Test
    public void getCarVolumeGroupInfos() {
        mTestAudioZone.addVolumeGroup(mMockMusicGroup);
        mTestAudioZone.addVolumeGroup(mMockNavGroup);
        mTestAudioZone.addVolumeGroup(mMockVoiceGroup);

        List<CarVolumeGroupInfo> infos = mTestAudioZone.getVolumeGroupInfos();

        expectWithMessage("Car volume group infos").that(infos).hasSize(3);
    }

    private static class VolumeGroupBuilder {
        private SparseArray<String> mDeviceAddresses = new SparseArray<>();

        VolumeGroupBuilder addDeviceAddressAndContexts(@AudioContext int context, String address) {
            mDeviceAddresses.put(context, address);
            return this;
        }

        CarVolumeGroup build() {
            CarVolumeGroup carVolumeGroup = mock(CarVolumeGroup.class);
            Map<String, ArrayList<Integer>> addressToContexts = new HashMap<>();
            @AudioContext int[] contexts = new int[mDeviceAddresses.size()];

            for (int index = 0; index < mDeviceAddresses.size(); index++) {
                @AudioContext int context = mDeviceAddresses.keyAt(index);
                String address = mDeviceAddresses.get(context);
                when(carVolumeGroup.getAddressForContext(context)).thenReturn(address);
                if (!addressToContexts.containsKey(address)) {
                    addressToContexts.put(address, new ArrayList<>());
                }
                addressToContexts.get(address).add(context);
                contexts[index] = context;
            }

            when(carVolumeGroup.getContexts()).thenReturn(contexts);

            for (String address : addressToContexts.keySet()) {
                when(carVolumeGroup.getContextsForAddress(address))
                        .thenReturn(ImmutableList.copyOf(addressToContexts.get(address)));
            }
            when(carVolumeGroup.getAddresses())
                    .thenReturn(ImmutableList.copyOf(addressToContexts.keySet()));
            return carVolumeGroup;
        }

    }

    private static class Builder {
        private @AudioAttributes.AttributeUsage int mUsage = USAGE_MEDIA;
        private boolean mIsActive = true;
        private String mDeviceAddress = "";

        Builder setUsage(@AudioAttributes.AttributeUsage int usage) {
            mUsage = usage;
            return this;
        }

        Builder setDeviceAddress(String deviceAddress) {
            mDeviceAddress = deviceAddress;
            return this;
        }

        Builder setInactive() {
            mIsActive = false;
            return this;
        }

        AudioPlaybackConfiguration build() {
            AudioPlaybackConfiguration configuration = mock(AudioPlaybackConfiguration.class);
            AudioAttributes attributes = new AudioAttributes.Builder().setUsage(mUsage).build();
            AudioDeviceInfo outputDevice = generateOutAudioDeviceInfo(mDeviceAddress);
            when(configuration.getAudioAttributes()).thenReturn(attributes);
            when(configuration.getAudioDeviceInfo()).thenReturn(outputDevice);
            when(configuration.isActive()).thenReturn(mIsActive);
            return configuration;
        }

        private AudioDeviceInfo generateOutAudioDeviceInfo(String address) {
            AudioDeviceInfo audioDeviceInfo = mock(AudioDeviceInfo.class);
            when(audioDeviceInfo.getAddress()).thenReturn(address);
            when(audioDeviceInfo.getType()).thenReturn(AudioDeviceInfo.TYPE_BUS);
            when(audioDeviceInfo.isSource()).thenReturn(false);
            when(audioDeviceInfo.isSink()).thenReturn(true);
            when(audioDeviceInfo.getInternalType()).thenReturn(AudioDeviceInfo
                    .convertDeviceTypeToInternalInputDevice(AudioDeviceInfo.TYPE_BUS));
            return audioDeviceInfo;
        }
    }
}
