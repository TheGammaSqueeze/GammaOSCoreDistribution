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

import static org.junit.Assert.assertTrue;

import android.media.MediaFormat;
import android.mediapc.cts.common.PerformanceClassEvaluator;
import android.mediapc.cts.common.Utils;
import android.util.Pair;
import android.view.Surface;

import androidx.test.filters.LargeTest;
import androidx.test.rule.ActivityTestRule;

import com.android.compatibility.common.util.CddTest;

import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * The following test class validates the maximum number of concurrent Transcode sessions that
 * it can support by the (mime, decoder - mime, encoder) pair calculated via the
 * CodecCapabilities.getMaxSupportedInstances() and
 * VideoCapabilities.getSupportedPerformancePoints() methods. If maximum instances is odd, create
 * one additional decoder which decodes to surface and render. Also ensures that all the supported
 * sessions succeed in transcoding/decoding with meeting the expected frame rate.
 */
@RunWith(Parameterized.class)
public class MultiTranscoderPerfTest extends MultiCodecPerfTestBase {
    private static final String LOG_TAG = MultiTranscoderPerfTest.class.getSimpleName();

    private final Pair<String, String> mDecoderPair;
    private final Pair<String, String> mEncoderPair;

    @Rule
    public ActivityTestRule<TestActivity> mActivityRule =
            new ActivityTestRule<>(TestActivity.class);

    public MultiTranscoderPerfTest(Pair<String, String> decoderPair,
            Pair<String, String> encoderPair, boolean isAsync) {
        super(null, null, isAsync);
        mDecoderPair = decoderPair;
        mEncoderPair = encoderPair;
    }

    @Rule
    public final TestName mTestName = new TestName();

    // Parameters {0}_{1}_{2} -- Pair(Mime DecoderName)_Pair(Mime EncoderName)_isAsync
    @Parameterized.Parameters(name = "{index}({0}_{1}_{2})")
    public static Collection<Object[]> inputParams() {
        // Prepares the params list with the supported Hardware decoders/encoders in the device
        final List<Object[]> argsList = new ArrayList<>();
        ArrayList<Pair<String, String>> mimeTypeDecoderPairs = new ArrayList<>();
        ArrayList<Pair<String, String>> mimeTypeEncoderPairs = new ArrayList<>();
        for (String mime : mMimeList) {
            ArrayList<String> listOfDecoders = getHardwareCodecsForMime(mime, false);
            for (String decoder : listOfDecoders) {
                mimeTypeDecoderPairs.add(Pair.create(mime, decoder));
            }
            ArrayList<String> listOfEncoders = getHardwareCodecsForMime(mime, true);
            for (String encoder : listOfEncoders) {
                mimeTypeEncoderPairs.add(Pair.create(mime, encoder));
            }
        }
        for (Pair<String, String> mimeTypeDecoderPair : mimeTypeDecoderPairs) {
            for (Pair<String, String> mimeTypeEncoderPair : mimeTypeEncoderPairs) {
                for (boolean isAsync : boolStates) {
                    argsList.add(new Object[]{mimeTypeDecoderPair, mimeTypeEncoderPair, isAsync});
                }
            }
        }
        return argsList;
    }

    /**
     * This test calculates the validates number of concurrent 720p Transcode sessions that
     * it can support by the (mime, decoder - mime, encoder) pairs. Creates maxInstances / 2
     * Transcode sessions. If maximum instances is odd, creates one additional decoder which decodes
     * to surface and render. And ensures that all the supported sessions succeed in
     * transcoding/decoding with meeting the expected frame rate.
     */
    @LargeTest
    @Test(timeout = CodecTestBase.PER_TEST_TIMEOUT_LARGE_TEST_MS)
    @CddTest(requirement = "2.2.7.1/5.1/H-1-5,H-1-6")
    public void test720p() throws Exception {
        Assume.assumeTrue(Utils.isSPerfClass() || Utils.isRPerfClass() || !Utils.isPerfClass());

        boolean hasVP9 = mDecoderPair.first.equals(MediaFormat.MIMETYPE_VIDEO_VP9)
                || mEncoderPair.first.equals(MediaFormat.MIMETYPE_VIDEO_VP9);
        int requiredMinInstances = getRequiredMinConcurrentInstances720p(hasVP9);
        testCodec(m720pTestFiles, 720, 1280, requiredMinInstances);
    }

    /**
     * This test calculates the validates number of concurrent 1080p Transcode sessions that
     * it can support by the (mime, decoder - mime, encoder) pairs. Creates maxInstances / 2
     * Transcode sessions. If maximum instances is odd, creates one additional decoder which decodes
     * to surface and render. And ensures that all the supported sessions succeed in
     * transcoding/decoding with meeting the expected frame rate.
     */
    @LargeTest
    @Test(timeout = CodecTestBase.PER_TEST_TIMEOUT_LARGE_TEST_MS)
    @CddTest(requirement = "2.2.7.1/5.1/H-1-5,H-1-6")
    public void test1080p() throws Exception {
        Assume.assumeTrue(Utils.isTPerfClass() || !Utils.isPerfClass());
        testCodec(m1080pTestFiles, 1080, 1920, REQUIRED_MIN_CONCURRENT_INSTANCES);
    }

    private void testCodec(Map<String, String> testFiles, int height, int width,
            int requiredMinInstances) throws Exception {
        mTestFiles = testFiles;
        ArrayList<Pair<String, String>> mimeCodecPairs = new ArrayList<>();
        mimeCodecPairs.add(mDecoderPair);
        mimeCodecPairs.add(mEncoderPair);
        int maxInstances =
                checkAndGetMaxSupportedInstancesForCodecCombinations(height, width, mimeCodecPairs,
                        requiredMinInstances);
        double achievedFrameRate = 0.0;
        if (maxInstances >= requiredMinInstances) {
            ExecutorService pool =
                    Executors.newFixedThreadPool(maxInstances / 2 + maxInstances % 2);
            List<Transcode> transcodeList = new ArrayList<>();
            for (int i = 0; i < maxInstances / 2; i++) {
                transcodeList
                        .add(new Transcode(mEncoderPair.first, mTestFiles.get(mDecoderPair.first),
                                mDecoderPair.second, mEncoderPair.second, mIsAsync));
            }
            List<Future<Double>> decodeResultList = null;
            if (maxInstances % 2 == 1) {
                List<DecodeToSurface> decodeList = new ArrayList<>();
                mActivityRule.getActivity().waitTillSurfaceIsCreated();
                Surface surface = mActivityRule.getActivity().getSurface();
                assertTrue("Surface created is null.", surface != null);
                assertTrue("Surface created is invalid.", surface.isValid());
                mActivityRule.getActivity().setScreenParams(width, height, true);
                decodeList.add(new DecodeToSurface(mDecoderPair.first,
                        mTestFiles.get(mDecoderPair.first), mDecoderPair.second, surface,
                        mIsAsync));
                decodeResultList = pool.invokeAll(decodeList);
            }
            List<Future<Double>> transcodeResultList = pool.invokeAll(transcodeList);
            for (Future<Double> result : transcodeResultList) {
                achievedFrameRate += result.get();
            }
            if (decodeResultList != null) {
                for (Future<Double> result : decodeResultList) {
                    achievedFrameRate += result.get();
                }
            }
        }

        PerformanceClassEvaluator pce = new PerformanceClassEvaluator(this.mTestName);
        PerformanceClassEvaluator.ConcurrentCodecRequirement r5_1__H_1_5;
        PerformanceClassEvaluator.ConcurrentCodecRequirement r5_1__H_1_6;
        if (height >= 1080) {
            r5_1__H_1_5 = pce.addR5_1__H_1_5_1080p();
            r5_1__H_1_6 = pce.addR5_1__H_1_6_1080p();
            r5_1__H_1_5.setConcurrentInstances(maxInstances);
            r5_1__H_1_6.setConcurrentFps(achievedFrameRate);
        } else {
            r5_1__H_1_5 = pce.addR5_1__H_1_5_720p(mDecoderPair.first, mEncoderPair.first, height);
            r5_1__H_1_6 = pce.addR5_1__H_1_6_720p(mDecoderPair.first, mEncoderPair.first, height);
            r5_1__H_1_5.setConcurrentInstances(maxInstances);
            r5_1__H_1_6.setConcurrentFps(achievedFrameRate);
        }

        pce.submitAndCheck();
    }
}
