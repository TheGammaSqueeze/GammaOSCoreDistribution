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

package android.devicepolicy.cts;

import static com.android.bedstead.nene.permissions.CommonPermissions.INTERNET;

import static com.google.common.truth.Truth.assertThat;

import static org.testng.Assert.assertThrows;

import android.stats.devicepolicy.EventId;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.EnsureHasNoSecondaryUser;
import com.android.bedstead.harrier.annotations.Postsubmit;
import com.android.bedstead.harrier.annotations.enterprise.CanSetPolicyTest;
import com.android.bedstead.harrier.annotations.enterprise.CannotSetPolicyTest;
import com.android.bedstead.harrier.annotations.enterprise.PolicyAppliesTest;
import com.android.bedstead.harrier.policies.NetworkLogging;
import com.android.bedstead.metricsrecorder.EnterpriseMetricsRecorder;
import com.android.bedstead.metricsrecorder.truth.MetricQueryBuilderSubject;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.permissions.PermissionContext;

import org.junit.AssumptionViolatedException;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.runner.RunWith;

import java.net.HttpURLConnection;
import java.net.URL;
import java.net.UnknownHostException;

// These tests currently only cover checking that the appropriate methods are callable. They should
// be replaced with more complete tests once the other network logging tests are ready to be
// migrated to the new infrastructure
@RunWith(BedsteadJUnit4.class)
public final class NetworkLoggingTest {

    private static final String TAG = "NetworkLoggingTest";

    @ClassRule @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    private static final String[] URL_LIST = {
            "example.edu",
            "google.co.jp",
            "google.fr",
            "google.com.br",
            "google.com.tr",
            "google.co.uk",
            "google.de"
    };

    @Postsubmit(reason = "new test")
    @CannotSetPolicyTest(policy = NetworkLogging.class)
    public void isNetworkLoggingEnabled_notAllowed_throwsException() {
        assertThrows(SecurityException.class, () -> sDeviceState.dpc().devicePolicyManager()
                .isNetworkLoggingEnabled(sDeviceState.dpc().componentName()));
    }

    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(policy = NetworkLogging.class)
    @EnsureHasNoSecondaryUser
    public void isNetworkLoggingEnabled_networkLoggingIsEnabled_returnsTrue() throws Exception {
        try {
            sDeviceState.dpc().devicePolicyManager().setNetworkLoggingEnabled(
                    sDeviceState.dpc().componentName(), true);

            assertThat(sDeviceState.dpc().devicePolicyManager().isNetworkLoggingEnabled(
                    sDeviceState.dpc().componentName())).isTrue();
        } finally {
            sDeviceState.dpc().devicePolicyManager().setNetworkLoggingEnabled(
                    sDeviceState.dpc().componentName(), false);
        }
    }

    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(policy = NetworkLogging.class)
    public void isNetworkLoggingEnabled_networkLoggingIsNotEnabled_returnsFalse() throws Exception {
        sDeviceState.dpc().devicePolicyManager().setNetworkLoggingEnabled(
                sDeviceState.dpc().componentName(), false);

        assertThat(sDeviceState.dpc().devicePolicyManager().isNetworkLoggingEnabled(
                sDeviceState.dpc().componentName())).isFalse();
    }

    @Postsubmit(reason = "new test")
    @PolicyAppliesTest(policy = NetworkLogging.class)
    public void setNetworkLoggingEnabled_networkLoggingIsEnabled() throws Exception {
        try {
            sDeviceState.dpc().devicePolicyManager().setNetworkLoggingEnabled(
                    sDeviceState.dpc().componentName(), true);
            for (String url : URL_LIST) {
                connectToWebsite(url);
            }

            TestApis.devicePolicy().forceNetworkLogs();

            long batchToken = waitForBatchToken();

            assertThat(sDeviceState.dpc().devicePolicyManager().retrieveNetworkLogs(
                    sDeviceState.dpc().componentName(), batchToken)).isNotEmpty();
        } finally {
            sDeviceState.dpc().devicePolicyManager().setNetworkLoggingEnabled(
                    sDeviceState.dpc().componentName(), false);
        }
    }

    private long waitForBatchToken() {
        if (sDeviceState.dpc().isDelegate()) {
            return sDeviceState.dpc().events().delegateNetworkLogsAvailable()
                    .waitForEvent().batchToken();
        } else {
            return sDeviceState.dpc().events().networkLogsAvailable().waitForEvent().batchToken();
        }
    }

    private void connectToWebsite(String server) throws Exception {
        try (PermissionContext p = TestApis.permissions().withPermission(INTERNET)) {
            final URL url = new URL("http://" + server);
            HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
            try {
                urlConnection.setConnectTimeout(2000);
                urlConnection.setReadTimeout(2000);
                urlConnection.getResponseCode();
            } catch (UnknownHostException e) {
                throw new AssumptionViolatedException("Could not resolve host " + server);
            } finally {
                urlConnection.disconnect();
            }
        }
    }

    @Postsubmit(reason = "new test")
    @CannotSetPolicyTest(policy = NetworkLogging.class)
    public void setNetworkLoggingEnabled_notAllowed_throwsException() {
        assertThrows(SecurityException.class, () -> sDeviceState.dpc().devicePolicyManager()
                .setNetworkLoggingEnabled(sDeviceState.dpc().componentName(), true));
    }

    @Postsubmit(reason = "new test")
    @CannotSetPolicyTest(policy = NetworkLogging.class)
    public void retrieveNetworkLogs_notAllowed_throwsException() {
        assertThrows(SecurityException.class, () -> sDeviceState.dpc().devicePolicyManager()
                .retrieveNetworkLogs(sDeviceState.dpc().componentName(), /*batchToken= */ 0));
    }

    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(policy = NetworkLogging.class)
    public void setNetworkLoggingEnabled_true_logsEvent() {
        try (EnterpriseMetricsRecorder metrics = EnterpriseMetricsRecorder.create()) {
            sDeviceState.dpc().devicePolicyManager().setNetworkLoggingEnabled(
                    sDeviceState.dpc().componentName(), true);

            MetricQueryBuilderSubject.assertThat(metrics.query()
                    .whereType().isEqualTo(EventId.SET_NETWORK_LOGGING_ENABLED_VALUE)
                    .whereAdminPackageName().isEqualTo(
                            sDeviceState.dpc().packageName())
                            .whereBoolean().isEqualTo(sDeviceState.dpc().isDelegate())
                            .whereInteger().isEqualTo(1) // Enabled
                    ).wasLogged();
        } finally {
            sDeviceState.dpc().devicePolicyManager().setNetworkLoggingEnabled(
                    sDeviceState.dpc().componentName(), false);
        }
    }

    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(policy = NetworkLogging.class)
    public void setNetworkLoggingEnabled_false_logsEvent() {
        sDeviceState.dpc().devicePolicyManager().setNetworkLoggingEnabled(
                sDeviceState.dpc().componentName(), true);

        try (EnterpriseMetricsRecorder metrics = EnterpriseMetricsRecorder.create()) {
            sDeviceState.dpc().devicePolicyManager().setNetworkLoggingEnabled(
                    sDeviceState.dpc().componentName(), false);

            MetricQueryBuilderSubject.assertThat(metrics.query()
                    .whereType().isEqualTo(EventId.SET_NETWORK_LOGGING_ENABLED_VALUE)
                    .whereAdminPackageName().isEqualTo(
                            sDeviceState.dpc().packageName())
                    .whereBoolean().isEqualTo(sDeviceState.dpc().isDelegate())
                    .whereInteger().isEqualTo(0) // Disabled
            ).wasLogged();
        }
    }

    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(policy = NetworkLogging.class)
    public void retrieveNetworkLogs_logsEvent() throws Exception {
        try (EnterpriseMetricsRecorder metrics = EnterpriseMetricsRecorder.create()) {
            sDeviceState.dpc().devicePolicyManager().setNetworkLoggingEnabled(
                    sDeviceState.dpc().componentName(), true);
            for (String url : URL_LIST) {
                connectToWebsite(url);
            }

            TestApis.devicePolicy().forceNetworkLogs();

            long batchToken = waitForBatchToken();

            sDeviceState.dpc().devicePolicyManager().retrieveNetworkLogs(
                    sDeviceState.dpc().componentName(), batchToken);

            MetricQueryBuilderSubject.assertThat(metrics.query()
                    .whereType().isEqualTo(EventId.RETRIEVE_NETWORK_LOGS_VALUE)
                    .whereAdminPackageName().isEqualTo(
                            sDeviceState.dpc().packageName())
                    .whereBoolean().isEqualTo(sDeviceState.dpc().isDelegate())
            ).wasLogged();
        } finally {
            sDeviceState.dpc().devicePolicyManager().setNetworkLoggingEnabled(
                    sDeviceState.dpc().componentName(), false);
        }
    }
}