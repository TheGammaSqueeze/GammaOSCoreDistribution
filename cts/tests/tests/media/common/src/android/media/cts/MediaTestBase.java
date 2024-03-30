/*
 * Copyright (C) 2011 The Android Open Source Project
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
package android.media.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.media.MediaPlayer;
import android.media.cts.TestUtils.Monitor;
import android.net.Uri;
import android.os.ConditionVariable;
import android.os.ParcelFileDescriptor;
import android.os.PersistableBundle;

import androidx.annotation.CallSuper;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.MediaUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.HttpCookie;
import java.util.List;
import java.util.logging.Logger;
import java.util.Map;
import java.util.Set;

/**
 * Base class for tests which use MediaPlayer to play audio or video.
 */
public class MediaTestBase {
    private static final Logger LOG = Logger.getLogger(MediaTestBase.class.getName());

    protected static final int SLEEP_TIME = 1000;
    protected static final int LONG_SLEEP_TIME = 6000;
    protected static final int STREAM_RETRIES = 20;
    protected static boolean sUseScaleToFitMode = false;

    protected Context mContext;

    protected ActivityScenario<MediaStubActivity> mActivityScenario;
    protected MediaStubActivity mActivity;

    @CallSuper
    protected void setUp() throws Throwable {
        mActivityScenario = ActivityScenario.launch(MediaStubActivity.class);
        ConditionVariable activityReferenceObtained = new ConditionVariable();
        mActivityScenario.onActivity(activity -> {
            mActivity = activity;
            activityReferenceObtained.open();
        });
        activityReferenceObtained.block(/* timeoutMs= */ 10000);
        assertNotNull("Failed to acquire activity reference.", mActivity);

        mContext = getInstrumentation().getTargetContext();
        getInstrumentation().waitForIdleSync();
    }

    @CallSuper
    protected void tearDown() {
        mActivityScenario.close();
        mActivity = null;
    }

    protected Instrumentation getInstrumentation() {
        return InstrumentationRegistry.getInstrumentation();
    }

    protected MediaStubActivity getActivity() {
        return mActivity;
    }

    public boolean isTv() {
        PackageManager pm = getInstrumentation().getTargetContext().getPackageManager();
        return pm.hasSystemFeature(PackageManager.FEATURE_TELEVISION)
                && pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK);
    }

    public boolean checkTv() {
        return MediaUtils.check(isTv(), "not a TV");
    }

}
