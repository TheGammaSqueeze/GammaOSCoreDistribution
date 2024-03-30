/*
 * Copyright (C) 2018 The Android Open Source Project
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
package android.platform.test.rule;

import android.util.Log;
import androidx.annotation.VisibleForTesting;

import com.android.helpers.GarbageCollectionHelper;

import org.junit.runner.Description;

/**
 * This rule will gc the provided apps before running each test method.
 */
public class GarbageCollectRule extends TestWatcher {

    private static final String TAG = GarbageCollectRule.class.getSimpleName();

    private GarbageCollectionHelper mGcHelper;

    @VisibleForTesting
    static final String GC_APPS = "gc-app-package-names";

    @VisibleForTesting
    static final String GC_RULE_END = "gc-on-rule-end";

    public GarbageCollectRule() {
        mGcHelper = initGcHelper();
    }

    public GarbageCollectRule(String... applications) {
        mGcHelper = initGcHelper(applications);
    }

    private GarbageCollectionHelper initGcHelper(String... applications) {
        // Check if package names are passed via test arguments and split the comma separated
        // package names.
        if (applications == null || applications.length == 0) {
            if (getArguments() != null && getArguments().getString(GC_APPS) != null) {
                applications = getArguments().getString(GC_APPS).split(",");
            } else {
                Log.e(TAG, "Cannot force garbage collection because package names are empty.");
                return null;
            }
        }

        mGcHelper = getGcHelper();
        mGcHelper.setUp(applications);
        return mGcHelper;
    }

    @VisibleForTesting
    GarbageCollectionHelper getGcHelper() {
        return new GarbageCollectionHelper();
    }

    @Override
    protected void starting(Description description) {
        if (mGcHelper == null) {
            return;
        }
        Log.v(TAG, "Force Garbage collection at the starting of the rule.");
        mGcHelper.garbageCollect();
    }

    @Override
    protected void finished(Description description) {

        // By default do not force the GC at the end of the rule.
        if (mGcHelper == null || getArguments() == null)
            return;

        // Check if GC is needed at the end of the rule.
        boolean gcRuleEnd = Boolean.parseBoolean(getArguments().getString(GC_RULE_END, "false"));
        if (!gcRuleEnd) {
            return;
        }

        Log.v(TAG, "Force Garbage collection at the end of the rule.");
        mGcHelper.garbageCollect();
    }
}
