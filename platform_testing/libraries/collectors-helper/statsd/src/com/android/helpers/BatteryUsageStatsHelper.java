/*
 * Copyright (C) 2022 Android Open Source Project
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

package com.android.helpers;

import android.content.pm.PackageManager;
import android.util.Log;

import androidx.test.InstrumentationRegistry;

import com.android.os.nano.AtomsProto;
import com.android.os.nano.AtomsProto.BatteryUsageStatsAtomsProto;
import com.android.os.nano.AtomsProto.BatteryUsageStatsAtomsProto.BatteryConsumerData;
import com.android.os.nano.AtomsProto.BatteryUsageStatsAtomsProto.BatteryConsumerData.PowerComponentUsage;
import com.android.os.nano.AtomsProto.BatteryUsageStatsAtomsProto.UidBatteryConsumer;
import com.android.os.nano.StatsLog;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Consists of helper methods for collecting the BatteryUsageStatsSinceReset atom from stats and
 * processing that data into metrics related to power consumption attributed to specific packages
 * and components of the phone.
 */
public class BatteryUsageStatsHelper implements ICollectorHelper<Long> {

    private static final String LOG_TAG = BatteryUsageStatsHelper.class.getSimpleName();

    private StatsdHelper mStatsdHelper = new StatsdHelper();

    @Override
    public boolean startCollecting() {
        Log.i(LOG_TAG, "Adding BatteryUsageStats config to statsd.");
        List<Integer> atomIdList = new ArrayList<>();
        atomIdList.add(AtomsProto.Atom.BATTERY_USAGE_STATS_SINCE_RESET_FIELD_NUMBER);
        return mStatsdHelper.addGaugeConfig(atomIdList);
    }

    private Map<String, Long> batteryUsageStatsFromBucket(StatsLog.GaugeBucketInfo bucket) {
        PackageManager packageManager =
                InstrumentationRegistry.getInstrumentation().getContext().getPackageManager();

        List<BatteryUsageStatsAtomsProto> atoms =
                Arrays.stream(bucket.atom)
                        .filter(a -> a.hasBatteryUsageStatsSinceReset())
                        .map(a -> a.getBatteryUsageStatsSinceReset().batteryUsageStats)
                        .collect(Collectors.toList());
        if (atoms.size() != 1) {
            throw new IllegalStateException(
                    String.format(
                            "Expected exactly 1 BatteryUsageStats atom, but has %d.",
                            atoms.size()));
        }
        BatteryUsageStatsAtomsProto atom = atoms.get(0);

        Map<String, Long> results = new HashMap<>();

        // Collect the top-level consumer data.
        BatteryConsumerData totalConsumerData = atom.deviceBatteryConsumer;
        if (totalConsumerData != null) {
            results.put(
                    totalConsumptionMetricKey(), totalConsumerData.totalConsumedPowerDeciCoulombs);
            if (totalConsumerData.powerComponents != null) {
                for (PowerComponentUsage usage : totalConsumerData.powerComponents) {
                    results.put(
                            totalConsumptionByComponentMetricKey(usage.component),
                            usage.powerDeciCoulombs);
                    results.put(
                            totalDurationByComponentMetricKey(usage.component),
                            usage.durationMillis);
                }
            } else {
                Log.w(LOG_TAG, "Device consumer data doesn't have specific component data.");
            }
        } else {
            Log.w(LOG_TAG, "Atom doesn't have the expected device consumer data.");
        }

        // Collect the per-UID consumer data.
        if (atom.uidBatteryConsumers != null) {
            for (UidBatteryConsumer perUidConsumer : atom.uidBatteryConsumers) {
                String[] packagesForUid = packageManager.getPackagesForUid(perUidConsumer.uid);
                String packageNamesForMetrics =
                        (packagesForUid == null || packagesForUid.length == 0)
                                ? "unknown"
                                : String.join("_", packagesForUid);
                long timeInForeground = perUidConsumer.timeInForegroundMillis;
                long timeInBackground = perUidConsumer.timeInBackgroundMillis;
                results.put("time-in-fg-by-" + packageNamesForMetrics + "-ms", timeInForeground);
                results.put("time-in-bg-by-" + packageNamesForMetrics + "-ms", timeInBackground);

                BatteryConsumerData perUidData = perUidConsumer.batteryConsumerData;
                if (perUidData != null && perUidData.powerComponents != null) {
                    for (PowerComponentUsage componentPerUid : perUidData.powerComponents) {
                        results.put(
                                attributedConsumptionMetricKey(
                                        packageNamesForMetrics, componentPerUid.component),
                                componentPerUid.powerDeciCoulombs);
                        results.put(
                                attributedDurationMetricKey(
                                        packageNamesForMetrics, componentPerUid.component),
                                componentPerUid.durationMillis);
                    }
                } else {
                    Log.w(
                            LOG_TAG,
                            String.format(
                                    "Per-UID consumer data was missing for: %s",
                                    packageNamesForMetrics));
                }
            }
        } else {
            Log.w(LOG_TAG, "Atom doesn't have the expected per-UID consumer data.");
        }

        results.put("session-start-ms", atom.sessionStartMillis);
        results.put("session-end-ms", atom.sessionEndMillis);
        results.put("session-duration-ms", atom.sessionDurationMillis);
        results.put("session-discharge-pct", (long) atom.sessionDischargePercentage);

        return results;
    }

    private String totalConsumptionMetricKey() {
        return "power-consumed-total-dC";
    }

    private String attributedConsumptionMetricKey(String packages, int component) {
        return String.format("power-consumed-by-%s-on-%s-dC", packages, componentName(component));
    }

    private String totalConsumptionByComponentMetricKey(int component) {
        return String.format("power-consumed-total-on-%s-dC", componentName(component));
    }

    private String attributedDurationMetricKey(String packages, int component) {
        return String.format("duration-by-%s-on-%s-ms", packages, componentName(component));
    }

    private String totalDurationByComponentMetricKey(int component) {
        return String.format("duration-total-on-%s-ms", componentName(component));
    }

    private String componentName(int component) {
        switch (component) {
            case 0:
                return "screen";
            case 1:
                return "cpu";
            case 2:
                return "bluetooth";
            case 3:
                return "camera";
            case 4:
                return "audio";
            case 5:
                return "video";
            case 6:
                return "flashlight";
            case 7:
                return "system_services";
            case 8:
                return "mobile_radio";
            case 9:
                return "sensors";
            case 10:
                return "gnss";
            case 11:
                return "wifi";
            case 12:
                return "wakelock";
            case 13:
                return "memory";
            case 14:
                return "phone";
            case 15:
                return "ambient_display";
            case 16:
                return "idle";
            case 17:
                return "reattributed_to_other_consumers";
            default:
                return "unknown_component_" + component;
        }
    }

    @Override
    public Map<String, Long> getMetrics() {
        List<StatsLog.GaugeMetricData> gaugeMetricList = mStatsdHelper.getGaugeMetrics();
        if (gaugeMetricList.size() != 1) {
            throw new IllegalStateException(
                    String.format(
                            "Expected exactly 1 gauge metric data, but has %d.",
                            gaugeMetricList.size()));
        }

        StatsLog.GaugeMetricData gaugeMetricData = gaugeMetricList.get(0);
        if (gaugeMetricData.bucketInfo.length != 2) {
            throw new IllegalStateException(
                    String.format(
                            "Expected exactly 2 buckets in data, but has %d.",
                            gaugeMetricData.bucketInfo.length));
        }
        Map<String, Long> beforeData = batteryUsageStatsFromBucket(gaugeMetricData.bucketInfo[0]);
        Map<String, Long> afterData = batteryUsageStatsFromBucket(gaugeMetricData.bucketInfo[1]);

        printEntries(0, beforeData);
        printEntries(1, afterData);

        Map<String, Long> results = new HashMap<>();
        for (String sharedKey : beforeData.keySet()) {
            if (!afterData.containsKey(sharedKey)) {
                continue;
            }

            results.put(sharedKey, afterData.get(sharedKey) - beforeData.get(sharedKey));
        }
        return results;
    }

    @Override
    public boolean stopCollecting() {
        return mStatsdHelper.removeStatsConfig();
    }

    private void printEntries(int bucket, Map<String, Long> data) {
        for (Map.Entry<String, Long> datum : data.entrySet()) {
            Log.e(
                    LOG_TAG,
                    String.format(
                            "Bucket: %d\t|\t%s = %s", bucket, datum.getKey(), datum.getValue()));
        }
    }
}
