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

package android.mediapc.cts;

import android.media.MediaFormat;
import android.mediapc.cts.common.PerformanceClassEvaluator;
import android.mediapc.cts.common.Utils;
import android.util.Pair;
import androidx.test.filters.LargeTest;
import com.android.compatibility.common.util.CddTest;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * The following test class validates the maximum number of concurrent decode sessions that it can
 * support by the hardware decoders calculated via the CodecCapabilities.getMaxSupportedInstances()
 * and VideoCapabilities.getSupportedPerformancePoints() methods. And also ensures that the maximum
 * supported sessions succeed in decoding with meeting the expected frame rate.
 */
@RunWith(Parameterized.class)
public class MultiDecoderPerfTest extends MultiCodecPerfTestBase {
    private static final String LOG_TAG = MultiDecoderPerfTest.class.getSimpleName();

    private final String mDecoderName;

    public MultiDecoderPerfTest(String mimeType, String decoderName, boolean isAsync) {
        super(mimeType, null, isAsync);
        mDecoderName = decoderName;
    }

    @Rule
    public final TestName mTestName = new TestName();

    // Returns the params list with the mime and corresponding hardware decoders in
    // both sync and async modes.
    // Parameters {0}_{1}_{2} -- Mime_DecoderName_isAsync
    @Parameterized.Parameters(name = "{index}({0}_{1}_{2})")
    public static Collection<Object[]> inputParams() {
        final List<Object[]> argsList = new ArrayList<>();
        for (String mime : mMimeList) {
            ArrayList<String> listOfDecoders = getHardwareCodecsForMime(mime, false, true);
            for (String decoder : listOfDecoders) {
                for (boolean isAsync : boolStates) {
                    argsList.add(new Object[]{mime, decoder, isAsync});
                }
            }
        }
        return argsList;
    }

    /**
     * This test validates that the decoder can support at least 6 concurrent 720p 30fps
     * decoder instances. Also ensures that all the concurrent sessions succeed in decoding
     * with meeting the expected frame rate.
     */
    @LargeTest
    @Test(timeout = CodecTestBase.PER_TEST_TIMEOUT_LARGE_TEST_MS)
    @CddTest(requirement = "2.2.7.1/5.1/H-1-1,H-1-2")
    public void test720p() throws Exception {
        Assume.assumeTrue(Utils.isSPerfClass() || Utils.isRPerfClass() || !Utils.isPerfClass());
        Assume.assumeFalse("Skipping regular performance tests for secure codecs",
                isSecureSupportedCodec(mDecoderName, mMime));
        boolean hasVP9 = mMime.equals(MediaFormat.MIMETYPE_VIDEO_VP9);
        int requiredMinInstances = getRequiredMinConcurrentInstances720p(hasVP9);
        testCodec(m720pTestFiles, 720, 1280, requiredMinInstances);
    }

    /**
     * This test validates that the decoder can support at least 6 non-secure/2 secure concurrent
     * 1080p 30fps decoder instances. Also ensures that all the concurrent sessions succeed in
     * decoding with meeting the expected frame rate.
     */
    @LargeTest
    @Test(timeout = CodecTestBase.PER_TEST_TIMEOUT_LARGE_TEST_MS)
    @CddTest(requirements = {
            "2.2.7.1/5.1/H-1-1",
            "2.2.7.1/5.1/H-1-2",
            "2.2.7.1/5.1/H-1-9",})
    public void test1080p() throws Exception {
        Assume.assumeTrue(Utils.isTPerfClass() || !Utils.isPerfClass());
        if (isSecureSupportedCodec(mDecoderName, mMime)) {
            testCodec(m1080pWidevineTestFiles, 1080, 1920,
                    REQUIRED_MIN_CONCURRENT_SECURE_INSTANCES);
        } else {
            testCodec(m1080pTestFiles, 1080, 1920, REQUIRED_MIN_CONCURRENT_INSTANCES);
        }
    }

    private void testCodec(Map<String, String> testFiles, int height, int width,
            int requiredMinInstances) throws Exception {
        mTestFile = testFiles.get(mMime);
        Assume.assumeTrue("Add test vector for mime: " + mMime, mTestFile != null);
        ArrayList<Pair<String, String>> mimeDecoderPairs = new ArrayList<>();
        mimeDecoderPairs.add(Pair.create(mMime, mDecoderName));
        boolean isSecure = isSecureSupportedCodec(mDecoderName, mMime);
        int maxInstances =
                checkAndGetMaxSupportedInstancesForCodecCombinations(height, width,
                        mimeDecoderPairs, requiredMinInstances);
        double achievedFrameRate = 0.0;
        boolean meetsPreconditions = isSecure ? meetsSecureDecodePreconditions() : true;

        if (meetsPreconditions && maxInstances >= requiredMinInstances) {
            ExecutorService pool = Executors.newFixedThreadPool(maxInstances);
            List<Decode> testList = new ArrayList<>();
            for (int i = 0; i < maxInstances; i++) {
                testList.add(new Decode(mMime, mTestFile, mDecoderName, mIsAsync, isSecure));
            }
            List<Future<Double>> resultList = pool.invokeAll(testList);
            for (Future<Double> result : resultList) {
                achievedFrameRate += result.get();
            }
        }

        PerformanceClassEvaluator pce = new PerformanceClassEvaluator(this.mTestName);
        if (isSecure) {
            PerformanceClassEvaluator.ConcurrentCodecRequirement r5_1__H_1_9 = pce.addR5_1__H_1_9();
            r5_1__H_1_9.setConcurrentFps(achievedFrameRate);
        } else {
            PerformanceClassEvaluator.ConcurrentCodecRequirement r5_1__H_1_1;
            PerformanceClassEvaluator.ConcurrentCodecRequirement r5_1__H_1_2;
            if (height >= 1080) {
                r5_1__H_1_1 = pce.addR5_1__H_1_1_1080p();
                r5_1__H_1_2 = pce.addR5_1__H_1_2_1080p();
                r5_1__H_1_1.setConcurrentInstances(maxInstances);
                r5_1__H_1_2.setConcurrentFps(achievedFrameRate);
            } else {
                r5_1__H_1_1 = pce.addR5_1__H_1_1_720p(mMime, mMime, height);
                r5_1__H_1_2 = pce.addR5_1__H_1_2_720p(mMime, mMime, height);
                r5_1__H_1_1.setConcurrentInstances(maxInstances);
                r5_1__H_1_2.setConcurrentFps(achievedFrameRate);
            }
        }
        pce.submitAndCheck();
    }
}
