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

public class RmsHelper {
    private double mRmsCurrent;
    public int mBlockSize;
    private int mShoutCount;
    public boolean mRunning = false;

    private short[] mAudioShortArray;

    private DspBufferDouble mRmsSnapshots;
    private int mShotIndex;

    private final float mMinRmsDb;
    private final float mMinRmsVal;

    private static final float MAX_VAL = (float)(1 << 15);

    public RmsHelper(int blockSize, int shotCount, float minRmsDb, float minRmsVal) {
        mBlockSize = blockSize;
        mShoutCount = shotCount;
        mMinRmsDb = minRmsDb;
        mMinRmsVal = minRmsVal;

        reset();
    }

    public void reset() {
        mAudioShortArray = new short[mBlockSize];
        mRmsSnapshots = new DspBufferDouble(mShoutCount);
        mShotIndex = 0;
        mRmsCurrent = 0;
        mRunning = false;
    }

    public void captureShot() {
        if (mShotIndex >= 0 && mShotIndex < mRmsSnapshots.getSize()) {
            mRmsSnapshots.setValue(mShotIndex++, mRmsCurrent);
        }
    }

    public void setRunning(boolean running) {
        mRunning = running;
    }

    public double getRmsCurrent() {
        return mRmsCurrent;
    }

    public DspBufferDouble getRmsSnapshots() {
        return mRmsSnapshots;
    }

    public boolean updateRms(PipeShort pipe, int channelCount, int channel) {
        if (mRunning) {
            int samplesAvailable = pipe.availableToRead();
            while (samplesAvailable >= mBlockSize) {
                pipe.read(mAudioShortArray, 0, mBlockSize);

                double rmsTempSum = 0;
                int count = 0;
                for (int i = channel; i < mBlockSize; i += channelCount) {
                    float value = mAudioShortArray[i] / MAX_VAL;

                    rmsTempSum += value * value;
                    count++;
                }
                float rms = count > 0 ? (float)Math.sqrt(rmsTempSum / count) : 0f;
                if (rms < mMinRmsVal) {
                    rms = mMinRmsVal;
                }

                double alpha = 0.9;
                double total_rms = rms * alpha + mRmsCurrent * (1.0f - alpha);
                mRmsCurrent = total_rms;

                samplesAvailable = pipe.availableToRead();
            }
            return true;
        }
        return false;
    }
}
