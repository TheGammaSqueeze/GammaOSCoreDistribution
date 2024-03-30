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

package android.security.cts.CVE_2022_20143;

import static org.junit.Assert.assertFalse;
import static org.junit.Assume.assumeNoException;
import static org.junit.Assume.assumeTrue;

import android.app.AutomaticZenRule;
import android.app.Instrumentation;
import android.app.NotificationManager;
import android.app.UiAutomation;
import android.content.ComponentName;
import android.content.Context;
import android.net.Uri;
import android.platform.test.annotations.AsbSecurityTest;
import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.sts.common.util.StsExtraBusinessLogicTestCase;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;

@RunWith(AndroidJUnit4.class)
public class CVE_2022_20143 extends StsExtraBusinessLogicTestCase {

    @AsbSecurityTest(cveBugId = 220735360)
    @Test
    public void testPocCVE_2022_20143() {
        final int ruleLimitPerPackage = 200;
        final int timeoutDuration = 5000;
        final int waitDuration = 100;
        Instrumentation instrumentation;
        Context context;
        NotificationManager notificationManager = null;
        String packageName = null;
        UiAutomation uiautomation = null;
        boolean isVulnerable = true;
        boolean notificationPolicyAccessGranted = false;
        int automaticZenRules = 0;
        ArrayList<String> ruleIds = new ArrayList<>();
        try {
            instrumentation = InstrumentationRegistry.getInstrumentation();
            context = instrumentation.getContext();
            notificationManager = context.getSystemService(NotificationManager.class);
            packageName = context.getPackageName();
            uiautomation = instrumentation.getUiAutomation();
            uiautomation.executeShellCommand("cmd notification allow_dnd " + packageName);
            long startTime = System.currentTimeMillis();
            while (System.currentTimeMillis() - startTime < timeoutDuration) {
                // busy wait until notification policy access is granted
                if (notificationManager.isNotificationPolicyAccessGranted()) {
                    notificationPolicyAccessGranted = true;
                    break;
                }
                Thread.sleep(waitDuration);
            }
            // storing the number of automaticZenRules present before test run
            automaticZenRules = notificationManager.getAutomaticZenRules().size();
            ComponentName component =
                    new ComponentName(packageName, PocActivity.class.getCanonicalName());
            for (int i = 0; i < ruleLimitPerPackage; ++i) {
                Uri conditionId = Uri.parse("condition://android/" + i);
                AutomaticZenRule rule = new AutomaticZenRule("ZenRuleName" + i, null, component,
                        conditionId, null, NotificationManager.INTERRUPTION_FILTER_ALL, true);
                String id = notificationManager.addAutomaticZenRule(rule);
                ruleIds.add(id);
            }
        } catch (Exception e) {
            if (e instanceof IllegalArgumentException) {
                isVulnerable = false; // expected with fix
            } else {
                assumeNoException(e);
            }
        } finally {
            try {
                if (notificationPolicyAccessGranted) {
                    /* retrieving the total number of automaticZenRules added by test so that the */
                    /* test fails only if all automaticZenRules were added successfully */
                    automaticZenRules =
                            notificationManager.getAutomaticZenRules().size() - automaticZenRules;
                    for (String id : ruleIds) {
                        notificationManager.removeAutomaticZenRule(id);
                    }
                    uiautomation
                            .executeShellCommand("cmd notification disallow_dnd " + packageName);
                }
                boolean allZenRulesAdded = ruleLimitPerPackage == automaticZenRules;
                assumeTrue("Notification policy access not granted",
                        notificationPolicyAccessGranted);
                assertFalse(
                        "Vulnerable to b/220735360!! System can be corrupted by adding many"
                                + " AutomaticZenRules via NotificationManager#addAutomaticZenRule",
                        isVulnerable && allZenRulesAdded);
            } catch (Exception e) {
                assumeNoException(e);
            }
        }
    }
}
