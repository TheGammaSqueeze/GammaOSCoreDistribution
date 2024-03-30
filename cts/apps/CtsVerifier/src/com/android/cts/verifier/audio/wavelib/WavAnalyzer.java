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
package com.android.cts.verifier.audio.wavelib;

import com.android.cts.verifier.audio.audiolib.AudioCommon;
import com.android.cts.verifier.audio.Util;

import org.apache.commons.math.complex.Complex;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Class contains the analysis to calculate frequency response.
 */
public class WavAnalyzer {
  final double SILENCE_THRESHOLD = Short.MAX_VALUE / 100.0f;

  private final Listener listener;
  private final int sampleRate;  // Recording sampling rate.
  private double[] data;  // Whole recording data.
  private double[] dB;  // Average response
  private double[][] power;  // power of each trial
  private double[] noiseDB;  // background noise
  private double[][] noisePower;
  private double threshold;  // threshold of passing, drop off compared to 2000 kHz
  private boolean result = false;  // result of the test

  /**
   * Constructor of WavAnalyzer.
   */
  public WavAnalyzer(byte[] byteData, int sampleRate, Listener listener) {
    this.listener = listener;
    this.sampleRate = sampleRate;

    short[] shortData = new short[byteData.length >> 1];
    ByteBuffer.wrap(byteData).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shortData);
    this.data = Util.toDouble(shortData);
    for (int i = 0; i < data.length; i++) {
      data[i] = data[i] / Short.MAX_VALUE;
    }
  }

  /**
   * Do the analysis. Returns true if passing, false if failing.
   */
  public boolean doWork() {
    if (isClipped()) {
      return false;
    }
    // Calculating the pip strength.
    listener.sendMessage("Calculating... Please wait...\n");
    try {
      dB = measurePipStrength();
    } catch (IndexOutOfBoundsException e) {
      listener.sendMessage("WARNING: May have missed the prefix."
          + " Turn up the volume of the playback device or move to a quieter location.\n");
      return false;
    }
    if (!isConsistent()) {
      return false;
    }
    result = responsePassesHifiTest(dB);
    return result;
  }

  /**
   * Check if the recording is clipped.
   */
  public boolean isClipped() {
    for (int i = 1; i < data.length; i++) {
      if ((Math.abs(data[i]) >= Short.MAX_VALUE) && (Math.abs(data[i - 1]) >= Short.MAX_VALUE)) {
        listener.sendMessage("WARNING: Data is clipped."
            + " Turn down the volume of the playback device and redo the procedure.\n");
        return true;
      }
    }
    return false;
  }

  /**
   * Check if the result is consistant across trials.
   */
  public boolean isConsistent() {
    double[] coeffOfVar = new double[AudioCommon.PIP_NUM];
    for (int i = 0; i < AudioCommon.PIP_NUM; i++) {
      double[] powerAtFreq = new double[AudioCommon.REPETITIONS];
      for (int j = 0; j < AudioCommon.REPETITIONS; j++) {
        powerAtFreq[j] = power[i][j];
      }
      coeffOfVar[i] = Util.std(powerAtFreq) / Util.mean(powerAtFreq);
    }
    if (Util.mean(coeffOfVar) > 1.0) {
      listener.sendMessage("WARNING: Inconsistent result across trials."
          + " Turn up the volume of the playback device or move to a quieter location.\n");
      return false;
    }
    return true;
  }

  /**
   * Determine test pass/fail using the frequency response. Package visible for unit testing.
   */
  public boolean responsePassesHifiTest(double[] dB) {
    for (int i = 0; i < dB.length; i++) {
      // Precautionary; NaN should not happen.
      if (Double.isNaN(dB[i])) {
        listener.sendMessage(
            "WARNING: Unexpected NaN in result. Redo the test.\n");
        return false;
      }
    }

    if (Util.mean(dB) - Util.mean(noiseDB) < AudioCommon.SIGNAL_MIN_STRENGTH_DB_ABOVE_NOISE) {
      listener.sendMessage("WARNING: Signal is too weak or background noise is too strong."
          + " Turn up the volume of the playback device or move to a quieter location.\n");
      return false;
    }

    int indexOf2000Hz = Util.findClosest(AudioCommon.FREQUENCIES_ORIGINAL, 2000.0);
    threshold = dB[indexOf2000Hz] + AudioCommon.PASSING_THRESHOLD_DB;
    int indexOf18500Hz = Util.findClosest(AudioCommon.FREQUENCIES_ORIGINAL, 18500.0);
    int indexOf20000Hz = Util.findClosest(AudioCommon.FREQUENCIES_ORIGINAL, 20000.0);
    double[] responseInRange = new double[indexOf20000Hz - indexOf18500Hz];
    System.arraycopy(dB, indexOf18500Hz, responseInRange, 0, responseInRange.length);
    if (Util.mean(responseInRange) < threshold) {
      listener.sendMessage(
          "WARNING: Failed. Retry with different orientations or report failed.\n");
      return false;
    }
    return true;
  }

  /**
   * Calculate the Fourier Coefficient at the pip frequency to calculate the frequency response.
   * Package visible for unit testing.
   */
  public double[] measurePipStrength() {
    listener.sendMessage("Aligning data... Please wait...\n");
    final int dataStartI = alignData();
    final int prefixTotalLength = dataStartI
        + Util.toLength(AudioCommon.PREFIX_LENGTH_S + AudioCommon.PAUSE_AFTER_PREFIX_DURATION_S, sampleRate);
    listener.sendMessage("Done.\n");
    listener.sendMessage("Prefix starts at " + (double) dataStartI / sampleRate + " s \n");
    if (dataStartI > Math.round(sampleRate * (AudioCommon.PREFIX_LENGTH_S
            + AudioCommon.PAUSE_BEFORE_PREFIX_DURATION_S + AudioCommon.PAUSE_AFTER_PREFIX_DURATION_S))) {
      listener.sendMessage("WARNING: Unexpected prefix start time. May have missed the prefix.\n"
          + "PLAY button should be pressed on the playback device within one second"
          + " after RECORD is pressed on the recording device.\n"
          + "If this happens repeatedly,"
          + " turn up the volume of the playback device or move to a quieter location.\n");
    }

    listener.sendMessage("Analyzing noise strength... Please wait...\n");
    noisePower = new double[AudioCommon.PIP_NUM][AudioCommon.NOISE_SAMPLES];
    noiseDB = new double[AudioCommon.PIP_NUM];
    for (int s = 0; s < AudioCommon.NOISE_SAMPLES; s++) {
      double[] noisePoints = new double[AudioCommon.WINDOW_FOR_RECORDER.length];
      System.arraycopy(data, dataStartI - (s + 1) * noisePoints.length - 1,
          noisePoints, 0, noisePoints.length);
      for (int j = 0; j < noisePoints.length; j++) {
        noisePoints[j] = noisePoints[j] * AudioCommon.WINDOW_FOR_RECORDER[j];
      }
      for (int i = 0; i < AudioCommon.PIP_NUM; i++) {
        double freq = AudioCommon.FREQUENCIES_ORIGINAL[i];
        Complex fourierCoeff = new Complex(0, 0);
        final Complex rotator = new Complex(0,
            -2.0 * Math.PI * freq / sampleRate).exp();
        Complex phasor = new Complex(1, 0);
        for (int j = 0; j < noisePoints.length; j++) {
          fourierCoeff = fourierCoeff.add(phasor.multiply(noisePoints[j]));
          phasor = phasor.multiply(rotator);
        }
        fourierCoeff = fourierCoeff.multiply(1.0 / noisePoints.length);
        noisePower[i][s] = fourierCoeff.multiply(fourierCoeff.conjugate()).abs();
      }
    }
    for (int i = 0; i < AudioCommon.PIP_NUM; i++) {
      double meanNoisePower = 0;
      for (int j = 0; j < AudioCommon.NOISE_SAMPLES; j++) {
        meanNoisePower += noisePower[i][j];
      }
      meanNoisePower /= AudioCommon.NOISE_SAMPLES;
      noiseDB[i] = 10 * Math.log10(meanNoisePower);
    }

    listener.sendMessage("Analyzing pips... Please wait...\n");
    power = new double[AudioCommon.PIP_NUM][AudioCommon.REPETITIONS];
    for (int i = 0; i < AudioCommon.PIP_NUM * AudioCommon.REPETITIONS; i++) {
      if (i % AudioCommon.PIP_NUM == 0) {
        listener.sendMessage("#" + (i / AudioCommon.PIP_NUM + 1) + "\n");
      }

      int pipExpectedStartI;
      pipExpectedStartI = prefixTotalLength
          + Util.toLength(i * (AudioCommon.PIP_DURATION_S + AudioCommon.PAUSE_DURATION_S), sampleRate);
      // Cut out the data points for the current pip.
      double[] pipPoints = new double[AudioCommon.WINDOW_FOR_RECORDER.length];
      System.arraycopy(data, pipExpectedStartI, pipPoints, 0, pipPoints.length);
      for (int j = 0; j < AudioCommon.WINDOW_FOR_RECORDER.length; j++) {
        pipPoints[j] = pipPoints[j] * AudioCommon.WINDOW_FOR_RECORDER[j];
      }
      Complex fourierCoeff = new Complex(0, 0);
      final Complex rotator = new Complex(0,
          -2.0 * Math.PI * AudioCommon.FREQUENCIES[i] / sampleRate).exp();
      Complex phasor = new Complex(1, 0);
      for (int j = 0; j < pipPoints.length; j++) {
        fourierCoeff = fourierCoeff.add(phasor.multiply(pipPoints[j]));
        phasor = phasor.multiply(rotator);
      }
      fourierCoeff = fourierCoeff.multiply(1.0 / pipPoints.length);
      int j = AudioCommon.ORDER[i];
      power[j % AudioCommon.PIP_NUM][j / AudioCommon.PIP_NUM] =
          fourierCoeff.multiply(fourierCoeff.conjugate()).abs();
    }

    // Calculate median of trials.
    double[] dB = new double[AudioCommon.PIP_NUM];
    for (int i = 0; i < AudioCommon.PIP_NUM; i++) {
      dB[i] = 10 * Math.log10(Util.median(power[i]));
    }
    return dB;
  }

  /**
   * Align data using prefix. Package visible for unit testing.
   */
  public int alignData() {
    // Zeropadding samples to add in the correlation to avoid FFT wraparound.
    final int zeroPad =
            Util.toLength(AudioCommon.PREFIX_LENGTH_S, AudioCommon.RECORDING_SAMPLE_RATE_HZ) - 1;
    int fftSize = Util.nextPowerOfTwo((int) Math.round(sampleRate * (AudioCommon.PREFIX_LENGTH_S
              + AudioCommon.PAUSE_BEFORE_PREFIX_DURATION_S
              + AudioCommon.PAUSE_AFTER_PREFIX_DURATION_S + 0.5))
        + zeroPad);

    double[] dataCut = new double[fftSize - zeroPad];
    System.arraycopy(data, 0, dataCut, 0, fftSize - zeroPad);
    double[] xCorrDataPrefix = Util.computeCrossCorrelation(
        Util.padZeros(Util.toComplex(dataCut), fftSize),
        Util.padZeros(Util.toComplex(AudioCommon.PREFIX_FOR_RECORDER), fftSize));
    return Util.findMaxIndex(xCorrDataPrefix);
  }

  public double[] getDB() {
    return dB;
  }

  public double[][] getPower() {
    return power;
  }

  public double[] getNoiseDB() {
    return noiseDB;
  }

  public double getThreshold() {
    return threshold;
  }

  public boolean getResult() {
    return result;
  }

  public boolean isSilence() {
    for (int i = 0; i < data.length; i++) {
      if (Math.abs(data[i]) > SILENCE_THRESHOLD) {
        return false;
      }
    }
    return true;
  }

  /**
   * An interface for listening a message publishing the progress of the analyzer.
   */
  public interface Listener {

    void sendMessage(String message);
  }
}
