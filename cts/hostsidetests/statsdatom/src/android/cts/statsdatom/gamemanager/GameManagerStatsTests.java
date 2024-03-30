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

package android.cts.statsdatom.gamemanager;

import static com.google.common.truth.Truth.assertThat;

import android.cts.statsdatom.lib.AtomTestUtils;
import android.cts.statsdatom.lib.ConfigUtils;
import android.cts.statsdatom.lib.DeviceUtils;
import android.cts.statsdatom.lib.ReportUtils;

import com.android.os.AtomsProto;
import com.android.os.AtomsProto.GameStateChanged.State;
import com.android.os.StatsLog;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.testtype.DeviceTestCase;
import com.android.tradefed.testtype.IBuildReceiver;

import java.util.List;

/**
 * Test for Game Manager stats.
 *
 *  <p>Build/Install/Run:
 *  atest CtsStatsdAtomHostTestCases:GameManagerStatsTests
 */
public class GameManagerStatsTests extends DeviceTestCase implements IBuildReceiver {
    private IBuildInfo mCtsBuild;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        assertThat(mCtsBuild).isNotNull();
        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());
        DeviceUtils.installStatsdTestApp(getDevice(), mCtsBuild);
        Thread.sleep(AtomTestUtils.WAIT_TIME_LONG);
    }

    @Override
    protected void tearDown() throws Exception {
        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());
        DeviceUtils.uninstallStatsdTestApp(getDevice());
        super.tearDown();
    }

    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mCtsBuild = buildInfo;
    }

    public void testGameStateStatsd() throws Exception {
        ConfigUtils.uploadConfigForPushedAtom(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                AtomsProto.Atom.GAME_STATE_CHANGED_FIELD_NUMBER);
        DeviceUtils.runDeviceTestsOnStatsdApp(getDevice(), ".AtomTests", "testGameState");
        Thread.sleep(AtomTestUtils.WAIT_TIME_LONG);

        List<StatsLog.EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());
        assertThat(data.size()).isAtLeast(1);
        AtomsProto.GameStateChanged a0 = data.get(0).getAtom().getGameStateChanged();
        assertThat(a0.getUid()).isGreaterThan(10000);  // Not a system service UID.
        assertThat(a0.getPackageName()).isEqualTo(DeviceUtils.STATSD_ATOM_TEST_PKG);
        assertThat(a0.getBoostEnabled()).isEqualTo(false);  // Test app does not qualify.
        assertThat(a0.getIsLoading()).isEqualTo(true);
        assertThat(a0.getState()).isEqualTo(State.MODE_CONTENT);
        assertThat(a0.getLabel()).isEqualTo(1);
        assertThat(a0.getQuality()).isEqualTo(2);
    }
}
