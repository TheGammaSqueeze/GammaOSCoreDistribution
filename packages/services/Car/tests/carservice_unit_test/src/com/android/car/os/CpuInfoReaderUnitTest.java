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

package com.android.car.os;

import static com.android.car.os.CpuInfoReader.FLAG_CPUSET_CATEGORY_BACKGROUND;
import static com.android.car.os.CpuInfoReader.FLAG_CPUSET_CATEGORY_TOP_APP;

import static com.google.common.truth.Truth.assertWithMessage;

import android.car.test.mocks.AbstractExtendedMockitoTestCase;
import android.content.Context;
import android.content.res.AssetManager;
import android.util.Slog;

import androidx.test.platform.app.InstrumentationRegistry;

import libcore.io.Streams;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Objects;

/**
 * <p>This class contains unit tests for the {@link CarPerformanceService}.
 */
@RunWith(MockitoJUnitRunner.class)
public final class CpuInfoReaderUnitTest extends AbstractExtendedMockitoTestCase {
    private static final String TAG = CpuInfoReaderUnitTest.class.getSimpleName();
    private static final String ROOT_DIR_NAME = "os";
    private static final String VALID_CPUSET_DIR = "valid_cpuset";
    private static final String VALID_CPUFREQ_WITH_TIME_IN_STATE_DIR =
            "valid_cpufreq_with_time_in_state";
    private static final String VALID_CPUFREQ_WITH_TIME_IN_STATE_2_DIR =
            "valid_cpufreq_with_time_in_state_2";
    private static final String VALID_CPUFREQ_WITHOUT_TIME_IN_STATE_DIR =
            "valid_cpufreq_without_time_in_state";
    private static final String VALID_CPUFREQ_WITHOUT_TIME_IN_STATE_2_DIR =
            "valid_cpufreq_without_time_in_state_2";
    private static final String VALID_PROC_STAT = "valid_proc_stat";
    private static final String VALID_PROC_STAT_2 = "valid_proc_stat_2";
    private static final String CORRUPTED_CPUFREQ_DIR = "corrupted_cpufreq";
    private static final String CORRUPTED_CPUSET_DIR = "corrupted_cpuset";
    private static final String CORRUPTED_PROC_STAT = "corrupted_proc_stat";
    private static final String EMPTY_DIR = "empty_dir";
    private static final String EMPTY_FILE = "empty_file";

    private final Context mContext =
            InstrumentationRegistry.getInstrumentation().getTargetContext();
    private final File mCacheRoot = new File(mContext.getCacheDir(), ROOT_DIR_NAME);
    private final AssetManager mAssetManager = mContext.getAssets();

    private CpuInfoReader mCpuInfoReader;

    public CpuInfoReaderUnitTest() {
        super(CpuInfoReaderUnitTest.TAG);
    }

    @Before
    public void setUp() throws Exception {
        copyAssets(ROOT_DIR_NAME, mContext.getCacheDir());
        assertWithMessage("Cache root dir %s", mCacheRoot.getAbsolutePath())
                .that(mCacheRoot.exists()).isTrue();
    }

    @After
    public void tearDown() throws Exception {
        if (!deleteDirectory(mCacheRoot)) {
            Slog.e(TAG, "Failed to delete cache root directory " + mCacheRoot.getAbsolutePath());
        }
    }

    @Test
    public void testReadCpuInfoWithTimeInState() throws Exception {
        mCpuInfoReader = new CpuInfoReader(getCacheFile(VALID_CPUSET_DIR),
                getCacheFile(VALID_CPUFREQ_WITH_TIME_IN_STATE_DIR), getCacheFile(VALID_PROC_STAT));
        mCpuInfoReader.init();
        List<CpuInfoReader.CpuInfo> actualCpuInfos = mCpuInfoReader.readCpuInfos();
        List<CpuInfoReader.CpuInfo> expectedCpuInfos = List.of(
                new CpuInfoReader.CpuInfo(/* cpuCore= */ 0, FLAG_CPUSET_CATEGORY_TOP_APP,
                        /* curCpuFreqKHz= */ 488095, /* maxCpuFreqKHz= */ 2500000,
                        new CpuInfoReader.CpuUsageStats(/* userTimeMillis= */ 32249610,
                                /* niceTimeMillis= */ 7950930, /* systemTimeMillis= */ 52227050,
                                /* idleTimeMillis= */ 409036950, /* iowaitTimeMillis= */ 1322810,
                                /* irqTimeMillis= */ 8146740, /* softirqTimeMillis= */ 428970,
                                /* stealTimeMillis= */ 81950, /* guestTimeMillis= */ 0,
                                /* guestNiceTimeMillis= */ 0)),
                new CpuInfoReader.CpuInfo(/* cpuCore= */ 1, FLAG_CPUSET_CATEGORY_TOP_APP,
                        /* curCpuFreqKHz= */ 502380, /* maxCpuFreqKHz= */ 2800000,
                        new CpuInfoReader.CpuUsageStats(/* userTimeMillis= */ 28949280,
                                /* niceTimeMillis= */ 7799450, /* systemTimeMillis= */ 54004020,
                                /* idleTimeMillis= */ 402707120, /* iowaitTimeMillis= */ 1186960,
                                /* irqTimeMillis= */ 14786940, /* softirqTimeMillis= */ 1498130,
                                /* stealTimeMillis= */ 78780, /* guestTimeMillis= */ 0,
                                /* guestNiceTimeMillis= */ 0)),
                new CpuInfoReader.CpuInfo(/* cpuCore= */ 2,
                        FLAG_CPUSET_CATEGORY_TOP_APP | FLAG_CPUSET_CATEGORY_BACKGROUND,
                        /* curCpuFreqKHz= */ 464285, /* maxCpuFreqKHz= */ 2000000,
                        new CpuInfoReader.CpuUsageStats(/* userTimeMillis= */ 28959280,
                                /* niceTimeMillis= */ 7789450, /* systemTimeMillis= */ 54014020,
                                /* idleTimeMillis= */ 402717120, /* iowaitTimeMillis= */ 1166960,
                                /* irqTimeMillis= */ 14796940, /* softirqTimeMillis= */ 1478130,
                                /* stealTimeMillis= */ 88780, /* guestTimeMillis= */ 0,
                                /* guestNiceTimeMillis= */ 0)),
                new CpuInfoReader.CpuInfo(/* cpuCore= */ 3,
                        FLAG_CPUSET_CATEGORY_TOP_APP | FLAG_CPUSET_CATEGORY_BACKGROUND,
                        /* curCpuFreqKHz= */ 464285, /* maxCpuFreqKHz= */ 2000000,
                        new CpuInfoReader.CpuUsageStats(/* userTimeMillis= */ 32349610,
                                /* niceTimeMillis= */ 7850930, /* systemTimeMillis= */ 52127050,
                                /* idleTimeMillis= */ 409136950, /* iowaitTimeMillis= */ 1332810,
                                /* irqTimeMillis= */ 8136740, /* softirqTimeMillis= */ 438970,
                                /* stealTimeMillis= */ 71950, /* guestTimeMillis= */ 0,
                                /* guestNiceTimeMillis= */ 0)));

        assertWithMessage("Cpu infos").that(actualCpuInfos)
                .containsExactlyElementsIn(expectedCpuInfos);

        mCpuInfoReader.setCpuFreqDir(getCacheFile(VALID_CPUFREQ_WITH_TIME_IN_STATE_2_DIR));
        mCpuInfoReader.setProcStatFile(getCacheFile(VALID_PROC_STAT_2));

        actualCpuInfos = mCpuInfoReader.readCpuInfos();
        expectedCpuInfos = List.of(
                new CpuInfoReader.CpuInfo(/* cpuCore= */ 0, FLAG_CPUSET_CATEGORY_TOP_APP,
                        /* curCpuFreqKHz= */ 419354, /* maxCpuFreqKHz= */ 2500000,
                        new CpuInfoReader.CpuUsageStats(/* userTimeMillis= */ 10000000,
                                /* niceTimeMillis= */ 1000000, /* systemTimeMillis= */ 10000000,
                                /* idleTimeMillis= */ 110000000, /* iowaitTimeMillis= */ 1100000,
                                /* irqTimeMillis= */ 1400000, /* softirqTimeMillis= */ 80000,
                                /* stealTimeMillis= */ 21000, /* guestTimeMillis= */ 0,
                                /* guestNiceTimeMillis= */ 0)),
                new CpuInfoReader.CpuInfo(/* cpuCore= */ 1, FLAG_CPUSET_CATEGORY_TOP_APP,
                        /* curCpuFreqKHz= */ 429032, /* maxCpuFreqKHz= */ 2800000,
                        new CpuInfoReader.CpuUsageStats(/* userTimeMillis= */ 900000,
                                /* niceTimeMillis= */ 1000000, /* systemTimeMillis= */ 10000000,
                                /* idleTimeMillis= */ 1000000, /* iowaitTimeMillis= */ 90000,
                                /* irqTimeMillis= */ 200000, /* softirqTimeMillis= */ 100000,
                                /* stealTimeMillis= */ 100000, /* guestTimeMillis= */ 0,
                                /* guestNiceTimeMillis= */ 0)),
                new CpuInfoReader.CpuInfo(/* cpuCore= */ 2,
                        FLAG_CPUSET_CATEGORY_TOP_APP | FLAG_CPUSET_CATEGORY_BACKGROUND,
                        /* curCpuFreqKHz= */ 403225, /* maxCpuFreqKHz= */ 2000000,
                        new CpuInfoReader.CpuUsageStats(/* userTimeMillis= */ 10000000,
                                /* niceTimeMillis= */ 2000000, /* systemTimeMillis= */ 0,
                                /* idleTimeMillis= */ 10000000, /* iowaitTimeMillis= */ 1000000,
                                /* irqTimeMillis= */ 20000000, /* softirqTimeMillis= */ 1000000,
                                /* stealTimeMillis= */ 100000, /* guestTimeMillis= */ 0,
                                /* guestNiceTimeMillis= */ 0)),
                new CpuInfoReader.CpuInfo(/* cpuCore= */ 3,
                        FLAG_CPUSET_CATEGORY_TOP_APP | FLAG_CPUSET_CATEGORY_BACKGROUND,
                        /* curCpuFreqKHz= */ 403225, /* maxCpuFreqKHz= */ 2000000,
                        new CpuInfoReader.CpuUsageStats(/* userTimeMillis= */ 2000000,
                                /* niceTimeMillis= */ 1000000, /* systemTimeMillis= */ 1000000,
                                /* idleTimeMillis= */ 100000, /* iowaitTimeMillis= */ 100000,
                                /* irqTimeMillis= */ 100000, /* softirqTimeMillis= */ 1000000,
                                /* stealTimeMillis= */ 1000, /* guestTimeMillis= */ 0,
                                /* guestNiceTimeMillis= */ 0)));

        assertWithMessage("Second snapshot of cpu infos").that(actualCpuInfos)
                .containsExactlyElementsIn(expectedCpuInfos);
    }

    @Test
    public void testReadCpuInfoWithoutTimeInState() throws Exception {
        mCpuInfoReader = new CpuInfoReader(getCacheFile(VALID_CPUSET_DIR),
                getCacheFile(VALID_CPUFREQ_WITHOUT_TIME_IN_STATE_DIR),
                getCacheFile(VALID_PROC_STAT));
        mCpuInfoReader.init();
        List<CpuInfoReader.CpuInfo> actualCpuInfos = mCpuInfoReader.readCpuInfos();
        List<CpuInfoReader.CpuInfo> expectedCpuInfos = List.of(
                new CpuInfoReader.CpuInfo(/* cpuCore= */ 0, FLAG_CPUSET_CATEGORY_TOP_APP,
                        /* curCpuFreqKHz= */ 1230000, /* maxCpuFreqKHz= */ 2500000,
                        new CpuInfoReader.CpuUsageStats(/* userTimeMillis= */ 32249610,
                                /* niceTimeMillis= */ 7950930, /* systemTimeMillis= */ 52227050,
                                /* idleTimeMillis= */ 409036950, /* iowaitTimeMillis= */ 1322810,
                                /* irqTimeMillis= */ 8146740, /* softirqTimeMillis= */ 428970,
                                /* stealTimeMillis= */ 81950, /* guestTimeMillis= */ 0,
                                /* guestNiceTimeMillis= */ 0)),
                new CpuInfoReader.CpuInfo(/* cpuCore= */ 1, FLAG_CPUSET_CATEGORY_TOP_APP,
                        /* curCpuFreqKHz= */ 1450000, /* maxCpuFreqKHz= */ 2800000,
                        new CpuInfoReader.CpuUsageStats(/* userTimeMillis= */ 28949280,
                                /* niceTimeMillis= */ 7799450, /* systemTimeMillis= */ 54004020,
                                /* idleTimeMillis= */ 402707120, /* iowaitTimeMillis= */ 1186960,
                                /* irqTimeMillis= */ 14786940, /* softirqTimeMillis= */ 1498130,
                                /* stealTimeMillis= */ 78780, /* guestTimeMillis= */ 0,
                                /* guestNiceTimeMillis= */ 0)),
                new CpuInfoReader.CpuInfo(/* cpuCore= */ 2,
                        FLAG_CPUSET_CATEGORY_TOP_APP | FLAG_CPUSET_CATEGORY_BACKGROUND,
                        /* curCpuFreqKHz= */ 1000000, /* maxCpuFreqKHz= */ 2000000,
                        new CpuInfoReader.CpuUsageStats(/* userTimeMillis= */ 28959280,
                                /* niceTimeMillis= */ 7789450, /* systemTimeMillis= */ 54014020,
                                /* idleTimeMillis= */ 402717120, /* iowaitTimeMillis= */ 1166960,
                                /* irqTimeMillis= */ 14796940, /* softirqTimeMillis= */ 1478130,
                                /* stealTimeMillis= */ 88780, /* guestTimeMillis= */ 0,
                                /* guestNiceTimeMillis= */ 0)),
                new CpuInfoReader.CpuInfo(/* cpuCore= */ 3,
                        FLAG_CPUSET_CATEGORY_TOP_APP | FLAG_CPUSET_CATEGORY_BACKGROUND,
                        /* curCpuFreqKHz= */ 1000000, /* maxCpuFreqKHz= */ 2000000,
                        new CpuInfoReader.CpuUsageStats(/* userTimeMillis= */ 32349610,
                                /* niceTimeMillis= */ 7850930, /* systemTimeMillis= */ 52127050,
                                /* idleTimeMillis= */ 409136950, /* iowaitTimeMillis= */ 1332810,
                                /* irqTimeMillis= */ 8136740, /* softirqTimeMillis= */ 438970,
                                /* stealTimeMillis= */ 71950, /* guestTimeMillis= */ 0,
                                /* guestNiceTimeMillis= */ 0)));

        assertWithMessage("Cpu infos").that(actualCpuInfos)
                .containsExactlyElementsIn(expectedCpuInfos);

        mCpuInfoReader.setCpuFreqDir(getCacheFile(VALID_CPUFREQ_WITHOUT_TIME_IN_STATE_2_DIR));
        mCpuInfoReader.setProcStatFile(getCacheFile(VALID_PROC_STAT_2));

        actualCpuInfos = mCpuInfoReader.readCpuInfos();
        expectedCpuInfos = List.of(
                new CpuInfoReader.CpuInfo(/* cpuCore= */ 0, FLAG_CPUSET_CATEGORY_TOP_APP,
                        /* curCpuFreqKHz= */ 1000000, /* maxCpuFreqKHz= */ 2500000,
                        new CpuInfoReader.CpuUsageStats(/* userTimeMillis= */ 10000000,
                                /* niceTimeMillis= */ 1000000, /* systemTimeMillis= */ 10000000,
                                /* idleTimeMillis= */ 110000000, /* iowaitTimeMillis= */ 1100000,
                                /* irqTimeMillis= */ 1400000, /* softirqTimeMillis= */ 80000,
                                /* stealTimeMillis= */ 21000, /* guestTimeMillis= */ 0,
                                /* guestNiceTimeMillis= */ 0)),
                new CpuInfoReader.CpuInfo(/* cpuCore= */ 1, FLAG_CPUSET_CATEGORY_TOP_APP,
                        /* curCpuFreqKHz= */ 2800000, /* maxCpuFreqKHz= */ 2800000,
                        new CpuInfoReader.CpuUsageStats(/* userTimeMillis= */ 900000,
                                /* niceTimeMillis= */ 1000000, /* systemTimeMillis= */ 10000000,
                                /* idleTimeMillis= */ 1000000, /* iowaitTimeMillis= */ 90000,
                                /* irqTimeMillis= */ 200000, /* softirqTimeMillis= */ 100000,
                                /* stealTimeMillis= */ 100000, /* guestTimeMillis= */ 0,
                                /* guestNiceTimeMillis= */ 0)),
                new CpuInfoReader.CpuInfo(/* cpuCore= */ 2,
                        FLAG_CPUSET_CATEGORY_TOP_APP | FLAG_CPUSET_CATEGORY_BACKGROUND,
                        /* curCpuFreqKHz= */ 2000000, /* maxCpuFreqKHz= */ 2000000,
                        new CpuInfoReader.CpuUsageStats(/* userTimeMillis= */ 10000000,
                                /* niceTimeMillis= */ 2000000, /* systemTimeMillis= */ 0,
                                /* idleTimeMillis= */ 10000000, /* iowaitTimeMillis= */ 1000000,
                                /* irqTimeMillis= */ 20000000, /* softirqTimeMillis= */ 1000000,
                                /* stealTimeMillis= */ 100000, /* guestTimeMillis= */ 0,
                                /* guestNiceTimeMillis= */ 0)),
                new CpuInfoReader.CpuInfo(/* cpuCore= */ 3,
                        FLAG_CPUSET_CATEGORY_TOP_APP | FLAG_CPUSET_CATEGORY_BACKGROUND,
                        /* curCpuFreqKHz= */ 2000000, /* maxCpuFreqKHz= */ 2000000,
                        new CpuInfoReader.CpuUsageStats(/* userTimeMillis= */ 2000000,
                                /* niceTimeMillis= */ 1000000, /* systemTimeMillis= */ 1000000,
                                /* idleTimeMillis= */ 100000, /* iowaitTimeMillis= */ 100000,
                                /* irqTimeMillis= */ 100000, /* softirqTimeMillis= */ 1000000,
                                /* stealTimeMillis= */ 1000, /* guestTimeMillis= */ 0,
                                /* guestNiceTimeMillis= */ 0)));

        assertWithMessage("Second snapshot of cpu infos").that(actualCpuInfos)
                .containsExactlyElementsIn(expectedCpuInfos);
    }

    @Test
    public void testReadCpuInfoWithCorruptedCpuset() throws Exception {
        mCpuInfoReader = new CpuInfoReader(getCacheFile(CORRUPTED_CPUSET_DIR),
                getCacheFile(VALID_CPUFREQ_WITH_TIME_IN_STATE_DIR),
                getCacheFile(VALID_PROC_STAT));
        mCpuInfoReader.init();
        List<CpuInfoReader.CpuInfo> actualCpuInfos = mCpuInfoReader.readCpuInfos();
        List<CpuInfoReader.CpuInfo> expectedCpuInfos = List.of(
                new CpuInfoReader.CpuInfo(/* cpuCore= */ 0, FLAG_CPUSET_CATEGORY_TOP_APP,
                        /* curCpuFreqKHz= */ 488095, /* maxCpuFreqKHz= */ 2500000,
                        new CpuInfoReader.CpuUsageStats(/* userTimeMillis= */ 32249610,
                                /* niceTimeMillis= */ 7950930, /* systemTimeMillis= */ 52227050,
                                /* idleTimeMillis= */ 409036950, /* iowaitTimeMillis= */ 1322810,
                                /* irqTimeMillis= */ 8146740, /* softirqTimeMillis= */ 428970,
                                /* stealTimeMillis= */ 81950, /* guestTimeMillis= */ 0,
                                /* guestNiceTimeMillis= */ 0)),
                new CpuInfoReader.CpuInfo(/* cpuCore= */ 1, FLAG_CPUSET_CATEGORY_TOP_APP,
                        /* curCpuFreqKHz= */ 502380, /* maxCpuFreqKHz= */ 2800000,
                        new CpuInfoReader.CpuUsageStats(/* userTimeMillis= */ 28949280,
                                /* niceTimeMillis= */ 7799450, /* systemTimeMillis= */ 54004020,
                                /* idleTimeMillis= */ 402707120, /* iowaitTimeMillis= */ 1186960,
                                /* irqTimeMillis= */ 14786940, /* softirqTimeMillis= */ 1498130,
                                /* stealTimeMillis= */ 78780, /* guestTimeMillis= */ 0,
                                /* guestNiceTimeMillis= */ 0)),
                new CpuInfoReader.CpuInfo(/* cpuCore= */ 2, FLAG_CPUSET_CATEGORY_TOP_APP,
                        /* curCpuFreqKHz= */ 464285, /* maxCpuFreqKHz= */ 2000000,
                        new CpuInfoReader.CpuUsageStats(/* userTimeMillis= */ 28959280,
                                /* niceTimeMillis= */ 7789450, /* systemTimeMillis= */ 54014020,
                                /* idleTimeMillis= */ 402717120, /* iowaitTimeMillis= */ 1166960,
                                /* irqTimeMillis= */ 14796940, /* softirqTimeMillis= */ 1478130,
                                /* stealTimeMillis= */ 88780, /* guestTimeMillis= */ 0,
                                /* guestNiceTimeMillis= */ 0)));

        assertWithMessage("Cpu infos").that(actualCpuInfos)
                .containsExactlyElementsIn(expectedCpuInfos);
    }

    @Test
    public void testReadCpuInfoWithCorruptedCpufreq() throws Exception {
        mCpuInfoReader = new CpuInfoReader(getCacheFile(VALID_CPUSET_DIR),
                getCacheFile(CORRUPTED_CPUFREQ_DIR), getCacheFile(VALID_PROC_STAT));
        mCpuInfoReader.init();
        List<CpuInfoReader.CpuInfo> actualCpuInfos = mCpuInfoReader.readCpuInfos();
        List<CpuInfoReader.CpuInfo> expectedCpuInfos = List.of(
                new CpuInfoReader.CpuInfo(/* cpuCore= */ 1, FLAG_CPUSET_CATEGORY_TOP_APP,
                        /* curCpuFreqKHz= */ 3000000, /* maxCpuFreqKHz= */ 1000000,
                        new CpuInfoReader.CpuUsageStats(/* userTimeMillis= */ 28949280,
                                /* niceTimeMillis= */ 7799450, /* systemTimeMillis= */ 54004020,
                                /* idleTimeMillis= */ 402707120, /* iowaitTimeMillis= */ 1186960,
                                /* irqTimeMillis= */ 14786940, /* softirqTimeMillis= */ 1498130,
                                /* stealTimeMillis= */ 78780, /* guestTimeMillis= */ 0,
                                /* guestNiceTimeMillis= */ 0)),
                new CpuInfoReader.CpuInfo(/* cpuCore= */ 2,
                        FLAG_CPUSET_CATEGORY_TOP_APP | FLAG_CPUSET_CATEGORY_BACKGROUND,
                        /* curCpuFreqKHz= */ 9, /* maxCpuFreqKHz= */ 2,
                        new CpuInfoReader.CpuUsageStats(/* userTimeMillis= */ 28959280,
                                /* niceTimeMillis= */ 7789450, /* systemTimeMillis= */ 54014020,
                                /* idleTimeMillis= */ 402717120, /* iowaitTimeMillis= */ 1166960,
                                /* irqTimeMillis= */ 14796940, /* softirqTimeMillis= */ 1478130,
                                /* stealTimeMillis= */ 88780, /* guestTimeMillis= */ 0,
                                /* guestNiceTimeMillis= */ 0)),
                new CpuInfoReader.CpuInfo(/* cpuCore= */ 3,
                        FLAG_CPUSET_CATEGORY_TOP_APP | FLAG_CPUSET_CATEGORY_BACKGROUND,
                        /* curCpuFreqKHz= */ 9, /* maxCpuFreqKHz= */ 2,
                        new CpuInfoReader.CpuUsageStats(/* userTimeMillis= */ 32349610,
                                /* niceTimeMillis= */ 7850930, /* systemTimeMillis= */ 52127050,
                                /* idleTimeMillis= */ 409136950, /* iowaitTimeMillis= */ 1332810,
                                /* irqTimeMillis= */ 8136740, /* softirqTimeMillis= */ 438970,
                                /* stealTimeMillis= */ 71950, /* guestTimeMillis= */ 0,
                                /* guestNiceTimeMillis= */ 0)));

        assertWithMessage("Cpu infos").that(actualCpuInfos)
                .containsExactlyElementsIn(expectedCpuInfos);
    }

    @Test
    public void testReadCpuInfoCorruptedProcStat() throws Exception {
        mCpuInfoReader = new CpuInfoReader(getCacheFile(VALID_CPUSET_DIR),
                getCacheFile(VALID_CPUFREQ_WITH_TIME_IN_STATE_DIR),
                getCacheFile(CORRUPTED_PROC_STAT));
        mCpuInfoReader.init();
        List<CpuInfoReader.CpuInfo> actualCpuInfos = mCpuInfoReader.readCpuInfos();
        List<CpuInfoReader.CpuInfo> expectedCpuInfos = List.of(
                new CpuInfoReader.CpuInfo(/* cpuCore= */ 0, FLAG_CPUSET_CATEGORY_TOP_APP,
                        /* curCpuFreqKHz= */ 488095, /* maxCpuFreqKHz= */ 2500000,
                        new CpuInfoReader.CpuUsageStats(/* userTimeMillis= */ 32249610,
                                /* niceTimeMillis= */ 7950930, /* systemTimeMillis= */ 52227050,
                                /* idleTimeMillis= */ 409036950, /* iowaitTimeMillis= */ 1322810,
                                /* irqTimeMillis= */ 8146740, /* softirqTimeMillis= */ 428970,
                                /* stealTimeMillis= */ 81950, /* guestTimeMillis= */ 0,
                                /* guestNiceTimeMillis= */ 0)),
                new CpuInfoReader.CpuInfo(/* cpuCore= */ 1,
                        FLAG_CPUSET_CATEGORY_TOP_APP,
                        /* curCpuFreqKHz= */ 502380, /* maxCpuFreqKHz= */ 2800000,
                        new CpuInfoReader.CpuUsageStats(/* userTimeMillis= */ 28949280,
                                /* niceTimeMillis= */ 7799450, /* systemTimeMillis= */ 54004020,
                                /* idleTimeMillis= */ 402707120, /* iowaitTimeMillis= */ 1186960,
                                /* irqTimeMillis= */ 14786940, /* softirqTimeMillis= */ 1498130,
                                /* stealTimeMillis= */ 78780, /* guestTimeMillis= */ 0,
                                /* guestNiceTimeMillis= */ 0)));

        assertWithMessage("Cpu infos").that(actualCpuInfos)
                .containsExactlyElementsIn(expectedCpuInfos);
    }

    @Test
    public void testReadCpuInfoWithEmptyCpuset() throws Exception {
        File emptyDir = getCacheFile(EMPTY_DIR);
        assertWithMessage("Make empty dir %s", emptyDir).that(emptyDir.mkdir()).isTrue();
        mCpuInfoReader = new CpuInfoReader(emptyDir, getCacheFile(
                VALID_CPUFREQ_WITH_TIME_IN_STATE_DIR),
                getCacheFile(VALID_PROC_STAT));

        assertWithMessage("Init CPU reader info").that(mCpuInfoReader.init()).isFalse();

        assertWithMessage("Cpu infos").that(mCpuInfoReader.readCpuInfos()).isEmpty();
    }

    @Test
    public void testReadCpuInfoWithEmptyCpufreq() throws Exception {
        File emptyDir = getCacheFile(EMPTY_DIR);
        assertWithMessage("Make empty dir %s", emptyDir).that(emptyDir.mkdir()).isTrue();
        mCpuInfoReader = new CpuInfoReader(getCacheFile(VALID_CPUSET_DIR), emptyDir,
                getCacheFile(VALID_PROC_STAT));

        assertWithMessage("Init CPU reader info").that(mCpuInfoReader.init()).isFalse();

        assertWithMessage("Cpu infos").that(mCpuInfoReader.readCpuInfos()).isEmpty();
    }

    @Test
    public void testReadCpuInfoWithEmptyProcStat() throws Exception {
        File emptyFile = getCacheFile(EMPTY_FILE);
        assertWithMessage("Create empty file %s", emptyFile).that(emptyFile.createNewFile())
                .isTrue();
        mCpuInfoReader = new CpuInfoReader(getCacheFile(VALID_CPUSET_DIR),
                getCacheFile(VALID_CPUFREQ_WITH_TIME_IN_STATE_DIR), getCacheFile(EMPTY_FILE));

        assertWithMessage("Cpu infos").that(mCpuInfoReader.readCpuInfos()).isEmpty();
    }

    private File getCacheFile(String assetName) {
        return new File(mCacheRoot, assetName);
    }

    private void copyAssets(String assetPath, File targetRoot) throws Exception {
        File target = new File(targetRoot, assetPath);
        String[] assets = mAssetManager.list(assetPath);
        if (assets == null || assets.length == 0) {
            try (InputStream in = mAssetManager.open(assetPath);
                 OutputStream out = new FileOutputStream(target)) {
                Streams.copy(in, out);
            }
            return;
        }
        assertWithMessage("Make target directory %s", target).that(target.mkdir()).isTrue();
        for (int i = 0; i < assets.length; i++) {
            copyAssets(String.format("%s%s%s", assetPath, File.separator, assets[i]), targetRoot);
        }
    }

    private static boolean deleteDirectory(File rootDir) {
        if (!rootDir.exists() || !rootDir.isDirectory()) {
            return false;
        }
        for (File file : Objects.requireNonNull(rootDir.listFiles())) {
            if (file.isDirectory()) {
                deleteDirectory(file);
            } else if (!file.delete()) {
                return false;
            }
        }
        return rootDir.delete();
    }
}
