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

package android.mediapc.cts;

import static android.media.MediaFormat.MIMETYPE_VIDEO_AV1;
import static android.mediapc.cts.CodecTestBase.SELECT_HARDWARE;
import static android.mediapc.cts.CodecTestBase.SELECT_VIDEO;
import static android.mediapc.cts.CodecTestBase.getMimesOfAvailableCodecs;
import static android.mediapc.cts.CodecTestBase.selectHardwareCodecs;
import static org.junit.Assert.assertTrue;

import android.media.MediaCodec;
import android.media.MediaCodecInfo.CodecCapabilities;
import android.media.MediaCodecInfo.VideoCapabilities.PerformancePoint;
import android.media.MediaFormat;
import android.mediapc.cts.common.PerformanceClassEvaluator;
import android.mediapc.cts.common.Utils;
import android.util.Log;
import androidx.test.filters.LargeTest;
import com.android.compatibility.common.util.CddTest;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

public class VideoCodecRequirementsTest {
    private static final String LOG_TAG = VideoCodecRequirementsTest.class.getSimpleName();
    private static final String FILE_AV1_REQ_SUPPORT =
            "dpov_1920x1080_60fps_av1_10bit_film_grain.mp4";

    @Rule
    public final TestName mTestName = new TestName();

    @Before
    public void isPerformanceClassCandidate() {
        Utils.assumeDeviceMeetsPerformanceClassPreconditions();
    }

    private Set<String> get4k60HwCodecSet(boolean isEncoder) throws IOException {
        Set<String> codecSet = new HashSet<>();
        Set<String> codecMediaTypes = getMimesOfAvailableCodecs(SELECT_VIDEO, SELECT_HARDWARE);
        PerformancePoint PP4k60 = new PerformancePoint(3840, 2160, 60);
        for (String codecMediaType : codecMediaTypes) {
            ArrayList<String> hwVideoCodecs =
                    selectHardwareCodecs(codecMediaType, null, null, isEncoder);
            for (String hwVideoCodec : hwVideoCodecs) {
                MediaCodec codec = MediaCodec.createByCodecName(hwVideoCodec);
                CodecCapabilities capabilities =
                        codec.getCodecInfo().getCapabilitiesForType(codecMediaType);
                List<PerformancePoint> pps =
                        capabilities.getVideoCapabilities().getSupportedPerformancePoints();
                assertTrue(hwVideoCodec + " doesn't advertise performance points", pps.size() > 0);
                for (PerformancePoint pp : pps) {
                    if (pp.covers(PP4k60)) {
                        codecSet.add(hwVideoCodec);
                        Log.d(LOG_TAG,
                                "Performance point 4k60 supported by codec: " + hwVideoCodec);
                        break;
                    }
                }
                codec.release();
            }
        }
        return codecSet;
    }

    /**
     * Validates AV1 hardware decoder is present and supports: Main 10, Level 4.1, Film Grain
     */
    @LargeTest
    @Test(timeout = CodecTestBase.PER_TEST_TIMEOUT_LARGE_TEST_MS)
    @CddTest(requirement = "2.2.7.1/5.1/H-1-14")
    public void testAV1HwDecoderRequirements() throws Exception {
        MediaFormat format = MediaFormat.createVideoFormat(MIMETYPE_VIDEO_AV1, 1920, 1080);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, 60);
        ArrayList<MediaFormat> formats = new ArrayList<>();
        formats.add(format);
        ArrayList<String> av1HwDecoders =
                selectHardwareCodecs(MIMETYPE_VIDEO_AV1, formats, null, false);
        boolean oneCodecDecoding = false;
        for (String codec : av1HwDecoders) {
            Decode decode = new Decode(MIMETYPE_VIDEO_AV1, FILE_AV1_REQ_SUPPORT, codec, true);
            double achievedRate = decode.doDecode();
            if (achievedRate > 0) {
                oneCodecDecoding = true;
            }
        }

        PerformanceClassEvaluator pce = new PerformanceClassEvaluator(this.mTestName);
        PerformanceClassEvaluator.VideoCodecRequirement rAV1DecoderReq = pce.addRAV1DecoderReq();
        rAV1DecoderReq.setAv1DecoderReq(oneCodecDecoding);

        pce.submitAndCheck();
    }

    /**
     * Validates if a hardware decoder that supports 4k60 is present
     */
    @LargeTest
    @Test(timeout = CodecTestBase.PER_TEST_TIMEOUT_LARGE_TEST_MS)
    @CddTest(requirement = "2.2.7.1/5.1/H-1-15")
    public void test4k60Decoder() throws IOException {
        Set<String> decoderSet = get4k60HwCodecSet(false);

        PerformanceClassEvaluator pce = new PerformanceClassEvaluator(this.mTestName);
        PerformanceClassEvaluator.VideoCodecRequirement r4k60HwDecoder = pce.addR4k60HwDecoder();
        r4k60HwDecoder.set4kHwDecoders(decoderSet.size());

        pce.submitAndCheck();
    }

    /**
     * Validates if a hardware encoder that supports 4k60 is present
     */
    @LargeTest
    @Test(timeout = CodecTestBase.PER_TEST_TIMEOUT_LARGE_TEST_MS)
    @CddTest(requirement = "2.2.7.1/5.1/H-1-16")
    public void test4k60Encoder() throws IOException {
        Set<String> encoderSet = get4k60HwCodecSet(true);

        PerformanceClassEvaluator pce = new PerformanceClassEvaluator(this.mTestName);
        PerformanceClassEvaluator.VideoCodecRequirement r4k60HwEncoder = pce.addR4k60HwEncoder();
        r4k60HwEncoder.set4kHwEncoders(encoderSet.size());

        pce.submitAndCheck();
    }
}
