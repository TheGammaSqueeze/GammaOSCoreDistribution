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
package com.android.cts.verifier.audio.soundio;

import com.android.cts.verifier.audio.audiolib.AudioCommon;
import com.android.cts.verifier.audio.Util;

/**
 * Sound generator.
 */
public class SoundGenerator {

  private static SoundGenerator instance;

  private final byte[] generatedSound;
  private final double[] sample;

  private SoundGenerator() {
    // Initialize sample.
    int pipNum = AudioCommon.PIP_NUM;
    int prefixTotalLength =
          Util.toLength(AudioCommon.PREFIX_LENGTH_S, AudioCommon.PLAYING_SAMPLE_RATE_HZ)
        + Util.toLength(AudioCommon.PAUSE_BEFORE_PREFIX_DURATION_S,
                    AudioCommon.PLAYING_SAMPLE_RATE_HZ)
        + Util.toLength(AudioCommon.PAUSE_AFTER_PREFIX_DURATION_S,
                    AudioCommon.PLAYING_SAMPLE_RATE_HZ);
    int repetitionLength = pipNum * Util.toLength(
        AudioCommon.PIP_DURATION_S + AudioCommon.PAUSE_DURATION_S,
            AudioCommon.PLAYING_SAMPLE_RATE_HZ);
    int sampleLength = prefixTotalLength + AudioCommon.REPETITIONS * repetitionLength;
    sample = new double[sampleLength];

    // Fill sample with prefix.
    System.arraycopy(AudioCommon.PREFIX_FOR_PLAYER, 0, sample,
        Util.toLength(AudioCommon.PAUSE_BEFORE_PREFIX_DURATION_S,
                AudioCommon.PLAYING_SAMPLE_RATE_HZ),
        AudioCommon.PREFIX_FOR_PLAYER.length);

    // Fill the sample.
    for (int i = 0; i < pipNum * AudioCommon.REPETITIONS; i++) {
      double[] pip = getPip(AudioCommon.WINDOW_FOR_PLAYER, AudioCommon.FREQUENCIES[i]);
      System.arraycopy(pip, 0, sample,
          prefixTotalLength + i * Util.toLength(
              AudioCommon.PIP_DURATION_S + AudioCommon.PAUSE_DURATION_S,
                  AudioCommon.PLAYING_SAMPLE_RATE_HZ),
          pip.length);
    }

    // Convert sample to byte.
    generatedSound = new byte[2 * sample.length];
    int i = 0;
    for (double dVal : sample) {
      short val = (short) ((dVal * 32767));
      generatedSound[i++] = (byte) (val & 0x00ff);
      generatedSound[i++] = (byte) ((val & 0xff00) >>> 8);
    }
  }

  public static SoundGenerator getInstance() {
    if (instance == null) {
      instance = new SoundGenerator();
    }
    return instance;
  }

  /**
   * Gets a pip sample.
   */
  private static double[] getPip(double[] window, double frequency) {
    int pipArrayLength = window.length;
    double[] pipArray = new double[pipArrayLength];
    double radPerSample = 2 * Math.PI / (AudioCommon.PLAYING_SAMPLE_RATE_HZ / frequency);
    for (int i = 0; i < pipArrayLength; i++) {
      pipArray[i] = window[i] * Math.sin(i * radPerSample);
    }
    return pipArray;
  }

  /**
   * Get generated sound in byte[].
   */
  public byte[] getByte() {
    return generatedSound;
  }

  /**
   * Get sample in double[].
   */
  public double[] getSample() {
    return sample;
  }
}
