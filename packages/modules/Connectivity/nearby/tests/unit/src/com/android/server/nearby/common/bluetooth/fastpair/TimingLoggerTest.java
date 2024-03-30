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

package com.android.server.nearby.common.bluetooth.fastpair;

import static com.google.common.truth.Truth.assertThat;

import android.os.SystemClock;
import android.platform.test.annotations.Presubmit;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SdkSuppress;
import androidx.test.filters.SmallTest;

import com.android.server.nearby.common.bluetooth.fastpair.TimingLogger.ScopedTiming;
import com.android.server.nearby.common.bluetooth.fastpair.TimingLogger.Timing;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Unit tests for {@link TimingLogger}.
 */
@Presubmit
@SmallTest
@RunWith(AndroidJUnit4.class)
public class TimingLoggerTest {

    private final Preferences mPrefs = Preferences.builder().setEvaluatePerformance(true).build();

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void logPairedTiming() {
        String label = "start";
        TimingLogger timingLogger = new TimingLogger("paired", mPrefs);
        timingLogger.start(label);
        SystemClock.sleep(1000);
        timingLogger.end();

        assertThat(timingLogger.getTimings()).hasSize(2);

        // Calculate execution time and only store result at "start" timing.
        // Expected output:
        // <pre>
        //  I/FastPair: paired [Exclusive time] / [Total time]
        //  I/FastPair:   start 1000ms
        //  I/FastPair: paired end, 1000ms
        // </pre>
        timingLogger.dump();

        assertPairedTiming(label, timingLogger.getTimings().get(0),
                timingLogger.getTimings().get(1));
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void logScopedTiming() {
        String label = "scopedTiming";
        TimingLogger timingLogger = new TimingLogger("scoped", mPrefs);
        try (ScopedTiming scopedTiming = new ScopedTiming(timingLogger, label)) {
            SystemClock.sleep(1000);
        }

        assertThat(timingLogger.getTimings()).hasSize(2);

        // Calculate execution time and only store result at "start" timings.
        // Expected output:
        // <pre>
        //  I/FastPair: scoped [Exclusive time] / [Total time]
        //  I/FastPair:   scopedTiming 1000ms
        //  I/FastPair: scoped end, 1000ms
        // </pre>
        timingLogger.dump();

        assertPairedTiming(label, timingLogger.getTimings().get(0),
                timingLogger.getTimings().get(1));
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void logOrderedTiming() {
        String label1 = "t1";
        String label2 = "t2";
        TimingLogger timingLogger = new TimingLogger("ordered", mPrefs);
        try (ScopedTiming t1 = new ScopedTiming(timingLogger, label1)) {
            SystemClock.sleep(1000);
        }
        try (ScopedTiming t2 = new ScopedTiming(timingLogger, label2)) {
            SystemClock.sleep(1000);
        }

        assertThat(timingLogger.getTimings()).hasSize(4);

        // Calculate execution time and only store result at "start" timings.
        // Expected output:
        // <pre>
        //  I/FastPair: ordered [Exclusive time] / [Total time]
        //  I/FastPair:   t1 1000ms
        //  I/FastPair:   t2 1000ms
        //  I/FastPair: ordered end, 2000ms
        // </pre>
        timingLogger.dump();

        // We expect get timings in this order: t1 start, t1 end, t2 start, t2 end.
        Timing start1 = timingLogger.getTimings().get(0);
        Timing end1 = timingLogger.getTimings().get(1);
        Timing start2 = timingLogger.getTimings().get(2);
        Timing end2 = timingLogger.getTimings().get(3);

        // Verify the paired timings.
        assertPairedTiming(label1, start1, end1);
        assertPairedTiming(label2, start2, end2);

        // Verify the order and total time.
        assertOrderedTiming(start1, start2);
        assertThat(start1.getExclusiveTime() + start2.getExclusiveTime())
                .isEqualTo(timingLogger.getTotalTime());
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void logNestedTiming() {
        String labelOuter = "outer";
        String labelInner1 = "inner1";
        String labelInner1Inner1 = "inner1inner1";
        String labelInner2 = "inner2";
        TimingLogger timingLogger = new TimingLogger("nested", mPrefs);
        try (ScopedTiming outer = new ScopedTiming(timingLogger, labelOuter)) {
            SystemClock.sleep(1000);
            try (ScopedTiming inner1 = new ScopedTiming(timingLogger, labelInner1)) {
                SystemClock.sleep(1000);
                try (ScopedTiming inner1inner1 = new ScopedTiming(timingLogger,
                        labelInner1Inner1)) {
                    SystemClock.sleep(1000);
                }
            }
            try (ScopedTiming inner2 = new ScopedTiming(timingLogger, labelInner2)) {
                SystemClock.sleep(1000);
            }
        }

        assertThat(timingLogger.getTimings()).hasSize(8);

        // Calculate execution time and only store result at "start" timing.
        // Expected output:
        // <pre>
        //  I/FastPair: nested [Exclusive time] / [Total time]
        //  I/FastPair:   outer 1000ms / 4000ms
        //  I/FastPair:     inner1 1000ms / 2000ms
        //  I/FastPair:       inner1inner1 1000ms
        //  I/FastPair:     inner2 1000ms
        //  I/FastPair: nested end, 4000ms
        // </pre>
        timingLogger.dump();

        // We expect get timings in this order: outer start, inner1 start, inner1inner1 start,
        // inner1inner1 end, inner1 end, inner2 start, inner2 end, outer end.
        Timing startOuter = timingLogger.getTimings().get(0);
        Timing startInner1 = timingLogger.getTimings().get(1);
        Timing startInner1Inner1 = timingLogger.getTimings().get(2);
        Timing endInner1Inner1 = timingLogger.getTimings().get(3);
        Timing endInner1 = timingLogger.getTimings().get(4);
        Timing startInner2 = timingLogger.getTimings().get(5);
        Timing endInner2 = timingLogger.getTimings().get(6);
        Timing endOuter = timingLogger.getTimings().get(7);

        // Verify the paired timings.
        assertPairedTiming(labelOuter, startOuter, endOuter);
        assertPairedTiming(labelInner1, startInner1, endInner1);
        assertPairedTiming(labelInner1Inner1, startInner1Inner1, endInner1Inner1);
        assertPairedTiming(labelInner2, startInner2, endInner2);

        // Verify the order and total time.
        assertOrderedTiming(startOuter, startInner1);
        assertOrderedTiming(startInner1, startInner1Inner1);
        assertOrderedTiming(startInner1Inner1, startInner2);
        assertThat(
                startOuter.getExclusiveTime() + startInner1.getTotalTime() + startInner2
                        .getTotalTime())
                .isEqualTo(timingLogger.getTotalTime());

        // Verify the nested execution time.
        assertThat(startInner1Inner1.getTotalTime()).isAtMost(startInner1.getTotalTime());
        assertThat(startInner1.getTotalTime() + startInner2.getTotalTime())
                .isAtMost(startOuter.getTotalTime());
    }

    private void assertPairedTiming(String label, Timing start, Timing end) {
        assertThat(start.isStartTiming()).isTrue();
        assertThat(start.getName()).isEqualTo(label);
        assertThat(end.isEndTiming()).isTrue();
        assertThat(end.getTimestamp()).isAtLeast(start.getTimestamp());

        assertThat(start.getExclusiveTime() > 0).isTrue();
        assertThat(start.getTotalTime()).isAtLeast(start.getExclusiveTime());
        assertThat(end.getExclusiveTime() == 0).isTrue();
        assertThat(end.getTotalTime() == 0).isTrue();
    }

    private void assertOrderedTiming(Timing t1, Timing t2) {
        assertThat(t1.isStartTiming()).isTrue();
        assertThat(t2.isStartTiming()).isTrue();
        assertThat(t2.getTimestamp()).isAtLeast(t1.getTimestamp());
    }
}
