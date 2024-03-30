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

import static android.media.AudioFormat.CHANNEL_OUT_MONO;
import static android.media.AudioFormat.CHANNEL_OUT_QUAD;
import static android.media.AudioFormat.CHANNEL_OUT_STEREO;
import static android.media.AudioFormat.ENCODING_PCM_16BIT;

import static com.android.car.audio.CarAudioDeviceInfo.DEFAULT_SAMPLE_RATE;
import static com.android.car.audio.GainBuilder.MAX_GAIN;
import static com.android.car.audio.GainBuilder.MIN_GAIN;
import static com.android.car.audio.GainBuilder.STEP_SIZE;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.media.AudioDeviceInfo;
import android.media.AudioGain;
import android.media.AudioManager;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;

@RunWith(AndroidJUnit4.class)
public class CarAudioDeviceInfoTest {

    private static final String TEST_ADDRESS = "test address";

    @Mock
    private AudioManager mAudioManager;

    @Test
    public void constructor_requiresNonNullGain() {
        AudioDeviceInfo audioDeviceInfo = mock(AudioDeviceInfo.class);
        when(audioDeviceInfo.getPort()).thenReturn(null);

        Throwable thrown = assertThrows(NullPointerException.class,
                () -> new CarAudioDeviceInfo(mAudioManager, audioDeviceInfo));

        assertWithMessage("Constructor exception")
                .that(thrown).hasMessageThat().contains("Audio device port");
    }

    @Test
    public void constructor_requiresJointModeGain() {
        AudioGain gainWithChannelMode = new GainBuilder().setMode(AudioGain.MODE_CHANNELS).build();
        AudioDeviceInfo audioDeviceInfo = getMockAudioDeviceInfo(
                new AudioGain[]{gainWithChannelMode});

        Throwable thrown = assertThrows(IllegalStateException.class,
                () -> new CarAudioDeviceInfo(mAudioManager, audioDeviceInfo));

        assertWithMessage("Constructor exception")
                .that(thrown).hasMessageThat().contains("audio gain");
    }

    @Test
    public void constructor_requiresMaxGainLargerThanMin() {
        AudioGain gainWithChannelMode = new GainBuilder().setMaxValue(10).setMinValue(20).build();
        AudioDeviceInfo audioDeviceInfo = getMockAudioDeviceInfo(
                new AudioGain[]{gainWithChannelMode});

        Throwable thrown = assertThrows(IllegalArgumentException.class,
                () -> new CarAudioDeviceInfo(mAudioManager, audioDeviceInfo));

        assertWithMessage("Constructor exception")
                .that(thrown).hasMessageThat().contains("lower than");
    }

    @Test
    public void constructor_requiresDefaultGainLargerThanMin() {
        AudioGain gainWithChannelMode = new GainBuilder().setDefaultValue(10).setMinValue(
                20).build();
        AudioDeviceInfo audioDeviceInfo = getMockAudioDeviceInfo(
                new AudioGain[]{gainWithChannelMode});

        Throwable thrown = assertThrows(IllegalArgumentException.class,
                () -> new CarAudioDeviceInfo(mAudioManager, audioDeviceInfo));

        assertWithMessage("Constructor exception")
                .that(thrown).hasMessageThat().contains("not in range");
    }

    @Test
    public void constructor_requiresDefaultGainSmallerThanMax() {
        AudioGain gainWithChannelMode = new GainBuilder().setDefaultValue(15).setMaxValue(
                10).build();
        AudioDeviceInfo audioDeviceInfo = getMockAudioDeviceInfo(
                new AudioGain[]{gainWithChannelMode});

        Throwable thrown = assertThrows(IllegalArgumentException.class,
                () -> new CarAudioDeviceInfo(mAudioManager, audioDeviceInfo));

        assertWithMessage("Constructor exception")
                .that(thrown).hasMessageThat().contains("not in range");
    }

    @Test
    public void constructor_requiresGainStepSizeFactorOfRange() {
        AudioGain gainWithChannelMode = new GainBuilder().setStepSize(7).build();
        AudioDeviceInfo audioDeviceInfo = getMockAudioDeviceInfo(
                new AudioGain[]{gainWithChannelMode});

        Throwable thrown = assertThrows(IllegalArgumentException.class,
                () -> new CarAudioDeviceInfo(mAudioManager, audioDeviceInfo));

        assertWithMessage("Constructor exception")
                .that(thrown).hasMessageThat().contains("greater than min gain to max gain range");
    }

    @Test
    public void constructor_requiresGainStepSizeFactorOfRangeToDefault() {
        AudioGain gainWithChannelMode = new GainBuilder().setStepSize(7).setMaxValue(98).build();
        AudioDeviceInfo audioDeviceInfo = getMockAudioDeviceInfo(
                new AudioGain[]{gainWithChannelMode});

        Throwable thrown = assertThrows(IllegalArgumentException.class,
                () -> new CarAudioDeviceInfo(mAudioManager, audioDeviceInfo));

        assertWithMessage("Constructor exception").that(thrown).hasMessageThat()
                .contains("greater than min gain to default gain range");
    }

    @Test
    public void getSampleRate_withMultipleSampleRates_returnsMax() {
        AudioDeviceInfo audioDeviceInfo = getMockAudioDeviceInfo();
        int[] sampleRates = new int[]{48000, 96000, 16000, 8000};
        when(audioDeviceInfo.getSampleRates()).thenReturn(sampleRates);
        CarAudioDeviceInfo info = new CarAudioDeviceInfo(mAudioManager, audioDeviceInfo);

        int sampleRate = info.getSampleRate();

        assertWithMessage("Sample rate").that(sampleRate).isEqualTo(96000);
    }

    @Test
    public void getSampleRate_withNullSampleRate_returnsDefault() {
        AudioDeviceInfo audioDeviceInfo = getMockAudioDeviceInfo();
        when(audioDeviceInfo.getSampleRates()).thenReturn(null);
        CarAudioDeviceInfo info = new CarAudioDeviceInfo(mAudioManager, audioDeviceInfo);

        int sampleRate = info.getSampleRate();

        assertWithMessage("Sample Rate").that(sampleRate).isEqualTo(DEFAULT_SAMPLE_RATE);
    }

    @Test
    public void getAddress_returnsValueFromDeviceInfo() {
        AudioDeviceInfo audioDeviceInfo = getMockAudioDeviceInfo();
        CarAudioDeviceInfo info = new CarAudioDeviceInfo(mAudioManager, audioDeviceInfo);

        assertWithMessage("Device Info Address").that(info.getAddress()).isEqualTo(TEST_ADDRESS);
    }

    @Test
    public void getMaxGain_returnsValueFromDeviceInfo() {
        AudioDeviceInfo audioDeviceInfo = getMockAudioDeviceInfo();
        CarAudioDeviceInfo info = new CarAudioDeviceInfo(mAudioManager, audioDeviceInfo);

        assertWithMessage("Device Info Max Gain")
                .that(info.getMaxGain()).isEqualTo(MAX_GAIN);
    }

    @Test
    public void getMinGain_returnsValueFromDeviceInfo() {
        AudioDeviceInfo audioDeviceInfo = getMockAudioDeviceInfo();
        CarAudioDeviceInfo info = new CarAudioDeviceInfo(mAudioManager, audioDeviceInfo);

        assertWithMessage("Device Info Min Gain")
                .that(info.getMinGain()).isEqualTo(MIN_GAIN);
    }

    @Test
    public void getDefaultGain_returnsValueFromDeviceInfo() {
        AudioDeviceInfo audioDeviceInfo = getMockAudioDeviceInfo();
        CarAudioDeviceInfo info = new CarAudioDeviceInfo(mAudioManager, audioDeviceInfo);

        assertWithMessage("Device Info Default Gain").that(info.getDefaultGain())
                .isEqualTo(GainBuilder.DEFAULT_GAIN);
    }

    @Test
    public void getStepValue_returnsValueFromDeviceInfo() {
        AudioDeviceInfo audioDeviceInfo = getMockAudioDeviceInfo();
        CarAudioDeviceInfo info = new CarAudioDeviceInfo(mAudioManager, audioDeviceInfo);

        assertWithMessage("Device Info Step Vale").that(info.getStepValue())
                .isEqualTo(STEP_SIZE);
    }

    @Test
    public void getChannelCount_withNoChannelMasks_returnsOne() {
        AudioDeviceInfo audioDeviceInfo = getMockAudioDeviceInfo();
        CarAudioDeviceInfo info = new CarAudioDeviceInfo(mAudioManager, audioDeviceInfo);

        int channelCount = info.getChannelCount();

        assertWithMessage("Channel Count").that(channelCount).isEqualTo(1);
    }

    @Test
    public void getChannelCount_withMultipleChannels_returnsHighestCount() {
        AudioDeviceInfo audioDeviceInfo = getMockAudioDeviceInfo();
        when(audioDeviceInfo.getChannelMasks()).thenReturn(new int[]{CHANNEL_OUT_STEREO,
                CHANNEL_OUT_QUAD, CHANNEL_OUT_MONO});
        CarAudioDeviceInfo info = new CarAudioDeviceInfo(mAudioManager, audioDeviceInfo);

        int channelCount = info.getChannelCount();

        assertWithMessage("Channel Count").that(channelCount).isEqualTo(4);
    }

    @Test
    public void getAudioDeviceInfo_returnsConstructorParameter() {
        AudioDeviceInfo audioDeviceInfo = getMockAudioDeviceInfo();
        CarAudioDeviceInfo info = new CarAudioDeviceInfo(mAudioManager, audioDeviceInfo);

        assertWithMessage("Device Info Audio Device Information")
                .that(info.getAudioDeviceInfo()).isEqualTo(audioDeviceInfo);
    }

    @Test
    public void getEncodingFormat_returnsPCM16() {
        AudioDeviceInfo audioDeviceInfo = getMockAudioDeviceInfo();
        CarAudioDeviceInfo info = new CarAudioDeviceInfo(mAudioManager, audioDeviceInfo);

        assertWithMessage("Device Info Audio Encoding Format")
                .that(info.getEncodingFormat()).isEqualTo(ENCODING_PCM_16BIT);
    }

    private AudioDeviceInfo getMockAudioDeviceInfo() {
        AudioGain mockGain = new GainBuilder().build();
        return getMockAudioDeviceInfo(new AudioGain[]{mockGain});
    }

    private AudioDeviceInfo getMockAudioDeviceInfo(AudioGain[] gains) {
        return new AudioDeviceInfoBuilder()
                .setAddressName(TEST_ADDRESS)
                .setAudioGains(gains)
                .build();
    }
}
