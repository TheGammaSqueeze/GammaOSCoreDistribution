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
package com.android.remoteprovisioner.hosttest;

import static com.google.common.truth.Truth.assertThat;

import android.cts.statsdatom.lib.ConfigUtils;
import android.cts.statsdatom.lib.ReportUtils;
import android.stats.connectivity.TransportType;

import com.android.os.AtomsProto;
import com.android.os.AtomsProto.RemoteKeyProvisioningAttempt;
import com.android.os.AtomsProto.RemoteKeyProvisioningAttempt.Cause;
import com.android.os.AtomsProto.RemoteKeyProvisioningAttempt.Enablement;
import com.android.os.AtomsProto.RemoteKeyProvisioningAttempt.UpTime;
import com.android.os.AtomsProto.RemoteKeyProvisioningNetworkInfo;
import com.android.os.AtomsProto.RemoteKeyProvisioningTiming;
import com.android.os.StatsLog.EventMetricData;
import com.android.remoteprovisioner.RemoteprovisionerEnums.RemoteKeyProvisioningStatus;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.List;

@RunWith(DeviceJUnit4ClassRunner.class)
public final class RemoteProvisionerStatsTests extends BaseHostJUnit4Test {
    private static final String APP_PACKAGE_NAME = "com.android.remoteprovisioner";
    private static final String TEST_PACKAGE_NAME = "com.android.remoteprovisioner.unittest";
    private static final int NO_HTTP_STATUS_ERROR = 0;

    private static final List<TransportType> VALID_TRANSPORT_TYPES = Arrays.asList(
            TransportType.TT_CELLULAR, TransportType.TT_WIFI, TransportType.TT_BLUETOOTH,
            TransportType.TT_ETHERNET, TransportType.TT_WIFI_AWARE, TransportType.TT_LOWPAN,
            TransportType.TT_CELLULAR_VPN, TransportType.TT_WIFI_VPN,
            TransportType.TT_BLUETOOTH_VPN, TransportType.TT_ETHERNET_VPN,
            TransportType.TT_WIFI_CELLULAR_VPN
    );

    private static final List<Enablement> VALID_ENABLEMENTS = Arrays.asList(
            Enablement.ENABLED_RKP_ONLY, Enablement.ENABLED_WITH_FALLBACK);

    @Before
    public void setUp() throws Exception {
        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());

        ConfigUtils.uploadConfigForPushedAtoms(getDevice(),
                APP_PACKAGE_NAME,
                new int[]{
                        AtomsProto.Atom.REMOTE_KEY_PROVISIONING_ATTEMPT_FIELD_NUMBER,
                        AtomsProto.Atom.REMOTE_KEY_PROVISIONING_NETWORK_INFO_FIELD_NUMBER,
                        AtomsProto.Atom.REMOTE_KEY_PROVISIONING_TIMING_FIELD_NUMBER});
    }

    @After
    public void tearDown() throws Exception {
        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());
    }

    @Test
    public void testGenerateKeyRkpOnly() throws Exception {
        runTest("ServerToSystemTest", "testGenerateKeyRkpOnly");
        final List<EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());
        assertThat(data).hasSize(6);

        // First three metrics are for when we actually generated keys
        final List<EventMetricData> firstAttemptData = data.subList(0, 3);
        RemoteKeyProvisioningAttempt attempt = getAttemptMetric(firstAttemptData);
        assertThat(attempt).isNotNull();
        assertThat(attempt.getCause()).isEqualTo(Cause.OUT_OF_KEYS);
        assertThat(attempt.getRemotelyProvisionedComponent()).isEqualTo("TEE_KEYMINT");
        assertThat(attempt.getUptime()).isNotEqualTo(UpTime.UPTIME_UNKNOWN);
        assertThat(attempt.getEnablement()).isEqualTo(Enablement.ENABLED_RKP_ONLY);
        assertThat(attempt.getIsKeyPoolEmpty()).isTrue();
        assertThat(attempt.getStatus()).isEqualTo(
                RemoteKeyProvisioningStatus.KEYS_SUCCESSFULLY_PROVISIONED);

        RemoteKeyProvisioningTiming timing = getTimingMetric(firstAttemptData);
        assertThat(timing).isNotNull();
        assertThat(timing.getTransportType()).isNotEqualTo(VALID_TRANSPORT_TYPES);
        assertThat(timing.getRemotelyProvisionedComponent()).isEqualTo(
                attempt.getRemotelyProvisionedComponent());
        // We're going over the internet here, so it realistically must take at least 1ms
        assertThat(timing.getServerWaitMillis()).isAtLeast(1);
        assertThat(timing.getBinderWaitMillis()).isAtLeast(0);
        assertThat(timing.getLockWaitMillis()).isAtLeast(0);
        assertThat(timing.getTotalProcessingTime()).isAtLeast(
                timing.getServerWaitMillis() + timing.getBinderWaitMillis()
                        + timing.getLockWaitMillis());

        RemoteKeyProvisioningNetworkInfo network = getNetworkMetric(firstAttemptData);
        assertThat(network).isNotNull();
        assertThat(network.getTransportType()).isEqualTo(timing.getTransportType());
        assertThat(network.getStatus()).isEqualTo(attempt.getStatus());
        assertThat(network.getHttpStatusError()).isEqualTo(200);

        // Second three metrics are for the call to keyGenerated, which should have been a noop
        final List<EventMetricData> secondAttemptData = data.subList(3, 6);
        attempt = getAttemptMetric(secondAttemptData);
        assertThat(attempt).isNotNull();
        assertThat(attempt.getCause()).isEqualTo(Cause.KEY_CONSUMED);
        assertThat(attempt.getRemotelyProvisionedComponent()).isEqualTo("TEE_KEYMINT");
        assertThat(attempt.getUptime()).isNotEqualTo(UpTime.UPTIME_UNKNOWN);
        assertThat(attempt.getEnablement()).isEqualTo(Enablement.ENABLED_RKP_ONLY);
        assertThat(attempt.getIsKeyPoolEmpty()).isFalse();
        assertThat(attempt.getStatus()).isEqualTo(
                RemoteKeyProvisioningStatus.NO_PROVISIONING_NEEDED);

        timing = getTimingMetric(secondAttemptData);
        assertThat(timing).isNotNull();
        assertThat(timing.getTransportType()).isNotEqualTo(VALID_TRANSPORT_TYPES);
        assertThat(timing.getRemotelyProvisionedComponent()).isEqualTo(
                attempt.getRemotelyProvisionedComponent());
        assertThat(timing.getServerWaitMillis()).isEqualTo(0);
        assertThat(timing.getBinderWaitMillis()).isAtLeast(0);
        assertThat(timing.getLockWaitMillis()).isAtLeast(0);
        assertThat(timing.getTotalProcessingTime()).isAtLeast(
                timing.getServerWaitMillis() + timing.getBinderWaitMillis()
                        + timing.getLockWaitMillis());

        network = getNetworkMetric(secondAttemptData);
        assertThat(network).isNotNull();
        assertThat(network.getTransportType()).isEqualTo(timing.getTransportType());
        assertThat(network.getStatus()).isEqualTo(attempt.getStatus());
        assertThat(network.getHttpStatusError()).isEqualTo(0);
    }

    @Test
    public void testDataBudgetEmptyCallGenerateRkpKeyService() throws Exception {
        runTest("ServerToSystemTest", "testDataBudgetEmptyCallGenerateRkpKeyService");
        final List<EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());
        assertThat(data).hasSize(3);

        final RemoteKeyProvisioningAttempt attempt = getAttemptMetric(data);
        assertThat(attempt).isNotNull();
        assertThat(attempt.getCause()).isEqualTo(Cause.KEY_CONSUMED);
        assertThat(attempt.getRemotelyProvisionedComponent()).isEqualTo("TEE_KEYMINT");
        assertThat(attempt.getUptime()).isNotEqualTo(UpTime.UPTIME_UNKNOWN);
        assertThat(attempt.getEnablement()).isIn(VALID_ENABLEMENTS);
        assertThat(attempt.getIsKeyPoolEmpty()).isTrue();
        assertThat(attempt.getStatus()).isEqualTo(RemoteKeyProvisioningStatus.OUT_OF_ERROR_BUDGET);

        final RemoteKeyProvisioningTiming timing = getTimingMetric(data);
        assertThat(timing).isNotNull();
        assertThat(timing.getTransportType()).isNotEqualTo(VALID_TRANSPORT_TYPES);
        assertThat(timing.getRemotelyProvisionedComponent()).isEqualTo(
                attempt.getRemotelyProvisionedComponent());
        assertThat(timing.getServerWaitMillis()).isEqualTo(0);
        assertThat(timing.getBinderWaitMillis()).isAtLeast(0);
        assertThat(timing.getLockWaitMillis()).isAtLeast(0);
        assertThat(timing.getTotalProcessingTime()).isAtLeast(
                timing.getServerWaitMillis() + timing.getBinderWaitMillis()
                        + timing.getLockWaitMillis());

        final RemoteKeyProvisioningNetworkInfo network = getNetworkMetric(data);
        assertThat(network).isNotNull();
        assertThat(network.getTransportType()).isEqualTo(timing.getTransportType());
        assertThat(network.getStatus()).isEqualTo(attempt.getStatus());
        assertThat(network.getHttpStatusError()).isEqualTo(NO_HTTP_STATUS_ERROR);
    }

    @Test
    public void testRetryableRkpError() throws Exception {
        runTest("ServerToSystemTest", "testRetryableRkpError");
        final List<EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());
        assertThat(data).hasSize(3);

        final RemoteKeyProvisioningAttempt attempt = getAttemptMetric(data);
        assertThat(attempt).isNotNull();
        assertThat(attempt.getCause()).isEqualTo(Cause.OUT_OF_KEYS);
        assertThat(attempt.getRemotelyProvisionedComponent()).isEqualTo("TEE_KEYMINT");
        assertThat(attempt.getUptime()).isNotEqualTo(UpTime.UPTIME_UNKNOWN);
        assertThat(attempt.getEnablement()).isEqualTo(Enablement.ENABLED_RKP_ONLY);
        assertThat(attempt.getIsKeyPoolEmpty()).isTrue();
        assertThat(attempt.getStatus()).isEqualTo(
                RemoteKeyProvisioningStatus.FETCH_GEEK_IO_EXCEPTION);

        final RemoteKeyProvisioningTiming timing = getTimingMetric(data);
        assertThat(timing).isNotNull();
        assertThat(timing.getTransportType()).isNotEqualTo(VALID_TRANSPORT_TYPES);
        assertThat(timing.getRemotelyProvisionedComponent()).isEqualTo(
                attempt.getRemotelyProvisionedComponent());
        assertThat(timing.getServerWaitMillis()).isAtLeast(0);
        assertThat(timing.getBinderWaitMillis()).isAtLeast(0);
        assertThat(timing.getLockWaitMillis()).isAtLeast(0);
        assertThat(timing.getTotalProcessingTime()).isAtLeast(
                timing.getServerWaitMillis() + timing.getBinderWaitMillis()
                        + timing.getLockWaitMillis());

        final RemoteKeyProvisioningNetworkInfo network = getNetworkMetric(data);
        assertThat(network).isNotNull();
        assertThat(network.getTransportType()).isEqualTo(timing.getTransportType());
        assertThat(network.getStatus()).isEqualTo(attempt.getStatus());
        assertThat(network.getHttpStatusError()).isEqualTo(NO_HTTP_STATUS_ERROR);
    }

    @Test
    public void testRetryNeverWhenDeviceNotRegistered() throws Exception {
        runTest("ServerToSystemTest", "testRetryNeverWhenDeviceNotRegistered");
        final List<EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());
        assertThat(data).hasSize(3);

        final RemoteKeyProvisioningAttempt attempt = getAttemptMetric(data);
        assertThat(attempt).isNotNull();
        assertThat(attempt.getCause()).isEqualTo(Cause.OUT_OF_KEYS);
        assertThat(attempt.getRemotelyProvisionedComponent()).isEqualTo("TEE_KEYMINT");
        assertThat(attempt.getUptime()).isNotEqualTo(UpTime.UPTIME_UNKNOWN);
        assertThat(attempt.getEnablement()).isEqualTo(Enablement.ENABLED_RKP_ONLY);
        assertThat(attempt.getIsKeyPoolEmpty()).isTrue();
        assertThat(attempt.getStatus()).isEqualTo(
                RemoteKeyProvisioningStatus.SIGN_CERTS_DEVICE_NOT_REGISTERED);

        final RemoteKeyProvisioningTiming timing = getTimingMetric(data);
        assertThat(timing).isNotNull();
        assertThat(timing.getTransportType()).isNotEqualTo(VALID_TRANSPORT_TYPES);
        assertThat(timing.getRemotelyProvisionedComponent()).isEqualTo(
                attempt.getRemotelyProvisionedComponent());
        assertThat(timing.getServerWaitMillis()).isAtLeast(0);
        assertThat(timing.getBinderWaitMillis()).isAtLeast(0);
        assertThat(timing.getLockWaitMillis()).isAtLeast(0);
        assertThat(timing.getTotalProcessingTime()).isAtLeast(
                timing.getServerWaitMillis() + timing.getBinderWaitMillis()
                        + timing.getLockWaitMillis());

        final RemoteKeyProvisioningNetworkInfo network = getNetworkMetric(data);
        assertThat(network).isNotNull();
        assertThat(network.getTransportType()).isEqualTo(timing.getTransportType());
        assertThat(network.getStatus()).isEqualTo(attempt.getStatus());
        assertThat(network.getHttpStatusError()).isEqualTo(444);
    }

    @Test
    public void testRetryWithoutNetworkTee() throws Exception {
        runTest("ServerToSystemTest", "testRetryWithoutNetworkTee");
        final List<EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());
        assertThat(data).hasSize(3);

        final RemoteKeyProvisioningAttempt attempt = getAttemptMetric(data);
        assertThat(attempt).isNotNull();
        assertThat(attempt.getCause()).isEqualTo(Cause.OUT_OF_KEYS);
        assertThat(attempt.getRemotelyProvisionedComponent()).isEqualTo("TEE_KEYMINT");
        assertThat(attempt.getUptime()).isNotEqualTo(UpTime.UPTIME_UNKNOWN);
        assertThat(attempt.getEnablement()).isIn(VALID_ENABLEMENTS);
        assertThat(attempt.getIsKeyPoolEmpty()).isTrue();
        assertThat(attempt.getStatus()).isEqualTo(
                RemoteKeyProvisioningStatus.NO_NETWORK_CONNECTIVITY);

        final RemoteKeyProvisioningTiming timing = getTimingMetric(data);
        assertThat(timing).isNotNull();
        assertThat(timing.getTransportType()).isNotEqualTo(VALID_TRANSPORT_TYPES);
        assertThat(timing.getRemotelyProvisionedComponent()).isEqualTo(
                attempt.getRemotelyProvisionedComponent());
        assertThat(timing.getServerWaitMillis()).isAtLeast(0);
        assertThat(timing.getBinderWaitMillis()).isAtLeast(0);
        assertThat(timing.getLockWaitMillis()).isAtLeast(0);
        assertThat(timing.getTotalProcessingTime()).isAtLeast(
                timing.getServerWaitMillis() + timing.getBinderWaitMillis()
                        + timing.getLockWaitMillis());

        final RemoteKeyProvisioningNetworkInfo network = getNetworkMetric(data);
        assertThat(network).isNotNull();
        assertThat(network.getTransportType()).isEqualTo(timing.getTransportType());
        assertThat(network.getStatus()).isEqualTo(attempt.getStatus());
        assertThat(network.getHttpStatusError()).isEqualTo(NO_HTTP_STATUS_ERROR);
    }

    @Test
    public void testPeriodicProvisionerRoundTrip() throws Exception {
        runTest("ServerToSystemTest", "testPeriodicProvisionerRoundTrip");
        final List<EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());
        assertThat(data).hasSize(3);

        final RemoteKeyProvisioningAttempt attempt = getAttemptMetric(data);
        assertThat(attempt).isNotNull();
        assertThat(attempt.getCause()).isEqualTo(Cause.SCHEDULED);
        assertThat(attempt.getRemotelyProvisionedComponent()).isEmpty();
        assertThat(attempt.getUptime()).isNotEqualTo(UpTime.UPTIME_UNKNOWN);
        // PeriodicProvisioner provisions ALL remotely provisioned components, and each one
        // has its own enablement flag, so it reports UNKNOWN or DISABLED only.
        assertThat(attempt.getEnablement()).isEqualTo(Enablement.ENABLEMENT_UNKNOWN);
        assertThat(attempt.getIsKeyPoolEmpty()).isTrue();
        assertThat(attempt.getStatus()).isEqualTo(
                RemoteKeyProvisioningStatus.KEYS_SUCCESSFULLY_PROVISIONED);

        final RemoteKeyProvisioningTiming timing = getTimingMetric(data);
        assertThat(timing).isNotNull();
        assertThat(timing.getTransportType()).isNotEqualTo(VALID_TRANSPORT_TYPES);
        assertThat(timing.getRemotelyProvisionedComponent()).isEqualTo(
                attempt.getRemotelyProvisionedComponent());
        assertThat(timing.getServerWaitMillis()).isAtLeast(0);
        assertThat(timing.getBinderWaitMillis()).isAtLeast(0);
        assertThat(timing.getLockWaitMillis()).isAtLeast(0);
        assertThat(timing.getTotalProcessingTime()).isAtLeast(
                timing.getServerWaitMillis() + timing.getBinderWaitMillis()
                        + timing.getLockWaitMillis());

        final RemoteKeyProvisioningNetworkInfo network = getNetworkMetric(data);
        assertThat(network).isNotNull();
        assertThat(network.getTransportType()).isEqualTo(timing.getTransportType());
        assertThat(network.getStatus()).isEqualTo(attempt.getStatus());
        assertThat(network.getHttpStatusError()).isEqualTo(200);
    }

    @Test
    public void testPeriodicProvisionerNoop() throws Exception {
        // First pass of the test will provision some keys
        runTest("ServerToSystemTest", "testPeriodicProvisionerNoop");

        List<EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());
        assertThat(data).hasSize(6);

        // drop the first three metrics, because those are for the first round trip and we've
        // already tested those metrics elsewhere.
        data = data.subList(3, 6);

        final RemoteKeyProvisioningAttempt attempt = getAttemptMetric(data);
        assertThat(attempt).isNotNull();
        assertThat(attempt.getCause()).isEqualTo(Cause.SCHEDULED);
        assertThat(attempt.getRemotelyProvisionedComponent()).isEmpty();
        assertThat(attempt.getUptime()).isNotEqualTo(UpTime.UPTIME_UNKNOWN);
        // PeriodicProvisioner provisions ALL remotely provisioned components, and each one
        // has its own enablement flag, so it reports UNKNOWN or DISABLED only.
        assertThat(attempt.getEnablement()).isEqualTo(Enablement.ENABLEMENT_UNKNOWN);
        assertThat(attempt.getIsKeyPoolEmpty()).isFalse();
        assertThat(attempt.getStatus()).isEqualTo(
                RemoteKeyProvisioningStatus.NO_PROVISIONING_NEEDED);

        final RemoteKeyProvisioningTiming timing = getTimingMetric(data);
        assertThat(timing).isNotNull();
        assertThat(timing.getTransportType()).isNotEqualTo(VALID_TRANSPORT_TYPES);
        assertThat(timing.getRemotelyProvisionedComponent()).isEqualTo(
                attempt.getRemotelyProvisionedComponent());
        assertThat(timing.getServerWaitMillis()).isAtLeast(0);
        assertThat(timing.getBinderWaitMillis()).isAtLeast(0);
        assertThat(timing.getLockWaitMillis()).isAtLeast(0);
        assertThat(timing.getTotalProcessingTime()).isAtLeast(
                timing.getServerWaitMillis() + timing.getBinderWaitMillis()
                        + timing.getLockWaitMillis());

        final RemoteKeyProvisioningNetworkInfo network = getNetworkMetric(data);
        assertThat(network).isNotNull();
        assertThat(network.getTransportType()).isEqualTo(timing.getTransportType());
        assertThat(network.getStatus()).isEqualTo(attempt.getStatus());
        assertThat(network.getHttpStatusError()).isEqualTo(NO_HTTP_STATUS_ERROR);
    }

    @Test
    public void testPeriodicProvisionerDataBudgetEmpty() throws Exception {
        runTest("ServerToSystemTest", "testPeriodicProvisionerDataBudgetEmpty");
        final List<EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());
        assertThat(data).hasSize(3);

        final RemoteKeyProvisioningAttempt attempt = getAttemptMetric(data);
        assertThat(attempt).isNotNull();
        assertThat(attempt.getCause()).isEqualTo(Cause.SCHEDULED);
        assertThat(attempt.getRemotelyProvisionedComponent()).isEmpty();
        assertThat(attempt.getUptime()).isNotEqualTo(UpTime.UPTIME_UNKNOWN);
        assertThat(attempt.getEnablement()).isEqualTo(Enablement.ENABLEMENT_UNKNOWN);
        assertThat(attempt.getIsKeyPoolEmpty()).isTrue();
        assertThat(attempt.getStatus()).isEqualTo(
                RemoteKeyProvisioningStatus.OUT_OF_ERROR_BUDGET);

        final RemoteKeyProvisioningTiming timing = getTimingMetric(data);
        assertThat(timing).isNotNull();
        assertThat(timing.getTransportType()).isNotEqualTo(VALID_TRANSPORT_TYPES);
        assertThat(timing.getRemotelyProvisionedComponent()).isEqualTo(
                attempt.getRemotelyProvisionedComponent());
        assertThat(timing.getServerWaitMillis()).isAtLeast(0);
        assertThat(timing.getBinderWaitMillis()).isAtLeast(0);
        assertThat(timing.getLockWaitMillis()).isAtLeast(0);
        assertThat(timing.getTotalProcessingTime()).isAtLeast(
                timing.getServerWaitMillis() + timing.getBinderWaitMillis()
                        + timing.getLockWaitMillis());

        final RemoteKeyProvisioningNetworkInfo network = getNetworkMetric(data);
        assertThat(network).isNotNull();
        assertThat(network.getTransportType()).isEqualTo(timing.getTransportType());
        assertThat(network.getStatus()).isEqualTo(attempt.getStatus());
        assertThat(network.getHttpStatusError()).isEqualTo(NO_HTTP_STATUS_ERROR);
    }

    @Test
    public void testPeriodicProvisionerProvisioningDisabled() throws Exception {
        runTest("ServerToSystemTest", "testPeriodicProvisionerProvisioningDisabled");
        final List<EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());
        assertThat(data).hasSize(3);

        final RemoteKeyProvisioningAttempt attempt = getAttemptMetric(data);
        assertThat(attempt).isNotNull();
        assertThat(attempt.getCause()).isEqualTo(Cause.SCHEDULED);
        assertThat(attempt.getRemotelyProvisionedComponent()).isEmpty();
        assertThat(attempt.getUptime()).isNotEqualTo(UpTime.UPTIME_UNKNOWN);
        assertThat(attempt.getEnablement()).isEqualTo(Enablement.DISABLED);
        assertThat(attempt.getIsKeyPoolEmpty()).isTrue();
        assertThat(attempt.getStatus()).isEqualTo(
                RemoteKeyProvisioningStatus.PROVISIONING_DISABLED);

        final RemoteKeyProvisioningTiming timing = getTimingMetric(data);
        assertThat(timing).isNotNull();
        assertThat(timing.getTransportType()).isNotEqualTo(VALID_TRANSPORT_TYPES);
        assertThat(timing.getRemotelyProvisionedComponent()).isEqualTo(
                attempt.getRemotelyProvisionedComponent());
        assertThat(timing.getServerWaitMillis()).isAtLeast(0);
        assertThat(timing.getBinderWaitMillis()).isAtLeast(0);
        assertThat(timing.getLockWaitMillis()).isAtLeast(0);
        assertThat(timing.getTotalProcessingTime()).isAtLeast(
                timing.getServerWaitMillis() + timing.getBinderWaitMillis()
                        + timing.getLockWaitMillis());

        final RemoteKeyProvisioningNetworkInfo network = getNetworkMetric(data);
        assertThat(network).isNotNull();
        assertThat(network.getTransportType()).isEqualTo(timing.getTransportType());
        assertThat(network.getStatus()).isEqualTo(attempt.getStatus());
        assertThat(network.getHttpStatusError()).isEqualTo(200);
    }

    private void runTest(String testClassName, String testMethodName) throws Exception {
        testClassName = TEST_PACKAGE_NAME + "." + testClassName;
        assertThat(runDeviceTests(TEST_PACKAGE_NAME, testClassName, testMethodName)).isTrue();
    }

    private static RemoteKeyProvisioningAttempt getAttemptMetric(List<EventMetricData> data) {
        RemoteKeyProvisioningAttempt metric = null;
        for (EventMetricData event : data) {
            if (event.getAtom().hasRemoteKeyProvisioningAttempt()) {
                assertThat(metric).isNull();
                metric = event.getAtom().getRemoteKeyProvisioningAttempt();
            }
        }
        return metric;
    }

    private static RemoteKeyProvisioningTiming getTimingMetric(List<EventMetricData> data) {
        RemoteKeyProvisioningTiming metric = null;
        for (EventMetricData event : data) {
            if (event.getAtom().hasRemoteKeyProvisioningTiming()) {
                assertThat(metric).isNull();
                metric = event.getAtom().getRemoteKeyProvisioningTiming();
            }
        }
        return metric;
    }

    private static RemoteKeyProvisioningNetworkInfo getNetworkMetric(List<EventMetricData> data) {
        RemoteKeyProvisioningNetworkInfo metric = null;
        for (EventMetricData event : data) {
            if (event.getAtom().hasRemoteKeyProvisioningNetworkInfo()) {
                assertThat(metric).isNull();
                metric = event.getAtom().getRemoteKeyProvisioningNetworkInfo();
            }
        }
        return metric;
    }
}
