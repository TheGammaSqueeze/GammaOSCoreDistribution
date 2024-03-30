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

package android.server.wm.jetpack.signed;

import static android.server.wm.jetpack.utils.ActivityEmbeddingUtil.DEFAULT_SPLIT_RATIO;
import static android.server.wm.jetpack.utils.ActivityEmbeddingUtil.EMBEDDED_ACTIVITY_ID;
import static android.server.wm.jetpack.utils.ActivityEmbeddingUtil.createSplitPairRuleBuilderWithJava8Predicate;
import static android.server.wm.jetpack.utils.ActivityEmbeddingUtil.startActivityCrossUidInSplit;
import static android.server.wm.jetpack.utils.ExtensionUtil.assumeExtensionSupportedDevice;
import static android.server.wm.jetpack.utils.ExtensionUtil.getWindowExtensions;
import static android.server.wm.jetpack.utils.WindowManagerJetpackTestBase.EXTRA_EMBED_ACTIVITY;
import static android.server.wm.jetpack.utils.WindowManagerJetpackTestBase.EXTRA_SPLIT_RATIO;

import static org.junit.Assume.assumeNotNull;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.server.wm.jetpack.utils.JavaConsumerAdapter;
import android.server.wm.jetpack.utils.TestActivityKnownEmbeddingCerts;
import android.server.wm.jetpack.utils.TestValueCountConsumer;

import androidx.window.extensions.WindowExtensions;
import androidx.window.extensions.embedding.ActivityEmbeddingComponent;
import androidx.window.extensions.embedding.SplitInfo;
import androidx.window.extensions.embedding.SplitPairRule;

import org.junit.AssumptionViolatedException;

import java.util.Collections;
import java.util.List;

/**
 * A test activity that attempts to embed {@link TestActivityKnownEmbeddingCerts} when created.
 * The app it belongs to is signed with a certificate that is recognized by the target app as a
 * trusted embedding host.
 */
public class SignedEmbeddingActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getIntent().getBooleanExtra(EXTRA_EMBED_ACTIVITY, false)) {
            startActivityInSplit();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        if (intent.getBooleanExtra(EXTRA_EMBED_ACTIVITY, false)) {
            startActivityInSplit();
        }
    }

    void startActivityInSplit() {
        ActivityEmbeddingComponent embeddingComponent;
        try {
            assumeExtensionSupportedDevice();
            WindowExtensions windowExtensions = getWindowExtensions();
            assumeNotNull(windowExtensions);
            embeddingComponent = windowExtensions.getActivityEmbeddingComponent();
            assumeNotNull(embeddingComponent);
        } catch (AssumptionViolatedException e) {
            // Embedding not supported
            finish();
            return;
        }

        TestValueCountConsumer<List<SplitInfo>> splitInfoConsumer = new TestValueCountConsumer<>();
        if (getWindowExtensions().getVendorApiLevel() >= 2) {
            embeddingComponent.setSplitInfoCallback(splitInfoConsumer);
        } else {
            embeddingComponent.setSplitInfoCallback(new JavaConsumerAdapter(splitInfoConsumer));
        }

        SplitPairRule splitPairRule = createSplitPairRuleBuilderWithJava8Predicate(
                activityActivityPair -> true /* activityActivityPredicate */,
                activityIntentPair -> true /* activityIntentPredicate */,
                parentWindowMetrics -> true /* parentWindowMetricsPredicate */)
                .setSplitRatio(getIntent().getFloatExtra(EXTRA_SPLIT_RATIO, DEFAULT_SPLIT_RATIO))
                .build();
        embeddingComponent.setEmbeddingRules(Collections.singleton(splitPairRule));

        // Launch an activity from a different UID that recognizes this package's signature and
        // verify that it is split with this activity.
        startActivityCrossUidInSplit(this,
                new ComponentName("android.server.wm.jetpack",
                        "android.server.wm.jetpack.utils.TestActivityKnownEmbeddingCerts"),
                splitPairRule, splitInfoConsumer, EMBEDDED_ACTIVITY_ID, false /* verify */);
    }
}
