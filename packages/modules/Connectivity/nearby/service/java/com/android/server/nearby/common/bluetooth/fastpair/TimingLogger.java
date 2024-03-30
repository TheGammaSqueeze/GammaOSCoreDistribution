/*
 * Copyright 2021 The Android Open Source Project
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

package com.android.server.nearby.common.bluetooth.fastpair;

import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.VisibleForTesting;

import java.util.ArrayDeque;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A profiler for performance metrics.
 *
 * <p>This class aim to break down the execution time for each steps of process to figure out the
 * bottleneck.
 */
public class TimingLogger {

    private static final String TAG = TimingLogger.class.getSimpleName();

    /**
     * The name of this session.
     */
    private final String mName;

    private final Preferences mPreference;

    /**
     * The ordered timing sequence data. It's composed by a paired {@link Timing} generated from
     * {@link #start} and {@link #end}.
     */
    private final List<Timing> mTimings;

    private final long mStartTimestampMs;

    /** Constructor. */
    public TimingLogger(String name, Preferences mPreference) {
        this.mName = name;
        this.mPreference = mPreference;
        mTimings = new CopyOnWriteArrayList<>();
        mStartTimestampMs = SystemClock.elapsedRealtime();
    }

    @VisibleForTesting
    List<Timing> getTimings() {
        return mTimings;
    }

    /**
     * Start a new paired timing.
     *
     * @param label The split name of paired timing.
     */
    public void start(String label) {
        if (mPreference.getEvaluatePerformance()) {
            mTimings.add(new Timing(label));
        }
    }

    /**
     * End a paired timing.
     */
    public void end() {
        if (mPreference.getEvaluatePerformance()) {
            mTimings.add(new Timing(Timing.END_LABEL));
        }
    }

    /**
     * Print out the timing data.
     */
    public void dump() {
        if (!mPreference.getEvaluatePerformance()) {
            return;
        }

        calculateTiming();
        Log.i(TAG, mName + "[Exclusive time] / [Total time] ([Timestamp])");
        int indentCount = 0;
        for (Timing timing : mTimings) {
            if (timing.isEndTiming()) {
                indentCount--;
                continue;
            }
            indentCount++;
            if (timing.mExclusiveTime == timing.mTotalTime) {
                Log.i(TAG, getIndentString(indentCount) + timing.mName + " " + timing.mExclusiveTime
                        + "ms (" + getRelativeTimestamp(timing.getTimestamp()) + ")");
            } else {
                Log.i(TAG, getIndentString(indentCount) + timing.mName + " " + timing.mExclusiveTime
                        + "ms / " + timing.mTotalTime + "ms (" + getRelativeTimestamp(
                        timing.getTimestamp()) + ")");
            }
        }
        Log.i(TAG, mName + "end, " + getTotalTime() + "ms");
    }

    private void calculateTiming() {
        ArrayDeque<Timing> arrayDeque = new ArrayDeque<>();
        for (Timing timing : mTimings) {
            if (timing.isStartTiming()) {
                arrayDeque.addFirst(timing);
                continue;
            }

            Timing timingStart = arrayDeque.removeFirst();
            final long time = timing.mTimestamp - timingStart.mTimestamp;
            timingStart.mExclusiveTime += time;
            timingStart.mTotalTime += time;
            if (!arrayDeque.isEmpty()) {
                arrayDeque.peekFirst().mExclusiveTime -= time;
            }
        }
    }

    private String getIndentString(int indentCount) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < indentCount; i++) {
            sb.append("  ");
        }
        return sb.toString();
    }

    private long getRelativeTimestamp(long timestamp) {
        return timestamp - mTimings.get(0).mTimestamp;
    }

    @VisibleForTesting
    long getTotalTime() {
        return mTimings.get(mTimings.size() - 1).mTimestamp - mTimings.get(0).mTimestamp;
    }

    /**
     * Gets the current latency since this object was created.
     */
    public long getLatencyMs() {
        return SystemClock.elapsedRealtime() - mStartTimestampMs;
    }

    @VisibleForTesting
    static class Timing {

        private static final String END_LABEL = "END_LABEL";

        /**
         * The name of this paired timing.
         */
        private final String mName;

        /**
         * System uptime in millisecond.
         */
        private final long mTimestamp;

        /**
         * The execution time exclude inner split timings.
         */
        private long mExclusiveTime;

        /**
         * The execution time within a start and an end timing.
         */
        private long mTotalTime;

        private Timing(String name) {
            this.mName = name;
            mTimestamp = SystemClock.elapsedRealtime();
            mExclusiveTime = 0;
            mTotalTime = 0;
        }

        @VisibleForTesting
        String getName() {
            return mName;
        }

        @VisibleForTesting
        long getTimestamp() {
            return mTimestamp;
        }

        @VisibleForTesting
        long getExclusiveTime() {
            return mExclusiveTime;
        }

        @VisibleForTesting
        long getTotalTime() {
            return mTotalTime;
        }

        @VisibleForTesting
        boolean isStartTiming() {
            return !isEndTiming();
        }

        @VisibleForTesting
        boolean isEndTiming() {
            return END_LABEL.equals(mName);
        }
    }

    /**
     * This class ensures each split timing is paired with a start and an end timing.
     */
    public static class ScopedTiming implements AutoCloseable {

        private final TimingLogger mTimingLogger;

        public ScopedTiming(TimingLogger logger, String label) {
            mTimingLogger = logger;
            mTimingLogger.start(label);
        }

        @Override
        public void close() {
            mTimingLogger.end();
        }
    }
}
