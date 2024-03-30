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

import static android.media.MediaCodecInfo.CodecCapabilities.FEATURE_SecurePlayback;
import static android.mediapc.cts.CodecDecoderTestBase.WIDEVINE_UUID;
import static android.mediapc.cts.CodecTestBase.selectHardwareCodecs;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecInfo.VideoCapabilities.PerformancePoint;
import android.media.MediaDrm;
import android.media.MediaFormat;
import android.media.UnsupportedSchemeException;
import android.mediapc.cts.common.Utils;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.Network;
import android.os.Build;
import android.util.Log;
import android.util.Pair;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Assume;
import org.junit.Before;

public class MultiCodecPerfTestBase {
    private static final String LOG_TAG = MultiCodecPerfTestBase.class.getSimpleName();
    static final boolean[] boolStates = {true, false};
    static final int REQUIRED_MIN_CONCURRENT_INSTANCES = 6;
    static final int REQUIRED_MIN_CONCURRENT_INSTANCES_FOR_VP9 = 2;
    static final int REQUIRED_MIN_CONCURRENT_SECURE_INSTANCES = 2;

    static ArrayList<String> mMimeList = new ArrayList<>();
    static Map<String, String> mTestFiles = new HashMap<>();
    static Map<String, String> m720pTestFiles = new HashMap<>();
    static Map<String, String> m1080pTestFiles = new HashMap<>();
    static Map<String, String> m1080pWidevineTestFiles = new HashMap<>();

    static {
        mMimeList.add(MediaFormat.MIMETYPE_VIDEO_AVC);
        mMimeList.add(MediaFormat.MIMETYPE_VIDEO_HEVC);

        m720pTestFiles.put(MediaFormat.MIMETYPE_VIDEO_AVC, "bbb_1280x720_3mbps_30fps_avc.mp4");
        m720pTestFiles.put(MediaFormat.MIMETYPE_VIDEO_HEVC, "bbb_1280x720_3mbps_30fps_hevc.mp4");

        // Test VP9 and AV1 as well for Build.VERSION_CODES.S and beyond
        if (Utils.getPerfClass() >= Build.VERSION_CODES.S) {
            mMimeList.add(MediaFormat.MIMETYPE_VIDEO_VP9);
            mMimeList.add(MediaFormat.MIMETYPE_VIDEO_AV1);

            m720pTestFiles.put(MediaFormat.MIMETYPE_VIDEO_VP9, "bbb_1280x720_3mbps_30fps_vp9.webm");
            m720pTestFiles.put(MediaFormat.MIMETYPE_VIDEO_AV1, "bbb_1280x720_3mbps_30fps_av1.mp4");
        }
        m1080pTestFiles.put(MediaFormat.MIMETYPE_VIDEO_AVC, "bbb_1920x1080_6mbps_30fps_avc.mp4");
        m1080pTestFiles.put(MediaFormat.MIMETYPE_VIDEO_HEVC, "bbb_1920x1080_4mbps_30fps_hevc.mp4");
        m1080pTestFiles.put(MediaFormat.MIMETYPE_VIDEO_VP9, "bbb_1920x1080_4mbps_30fps_vp9.webm");
        m1080pTestFiles.put(MediaFormat.MIMETYPE_VIDEO_AV1, "bbb_1920x1080_4mbps_30fps_av1.mp4");

        m1080pWidevineTestFiles
                .put(MediaFormat.MIMETYPE_VIDEO_AVC, "bbb_1920x1080_6mbps_30fps_avc_cenc.mp4");
        m1080pWidevineTestFiles
                .put(MediaFormat.MIMETYPE_VIDEO_HEVC, "bbb_1920x1080_4mbps_30fps_hevc_cenc.mp4");
        // TODO(b/230682028)
        // m1080pWidevineTestFiles
        //         .put(MediaFormat.MIMETYPE_VIDEO_VP9, "bbb_1920x1080_4mbps_30fps_vp9_cenc.webm");
        m1080pWidevineTestFiles
                .put(MediaFormat.MIMETYPE_VIDEO_AV1, "bbb_1920x1080_4mbps_30fps_av1_cenc.mp4");
    }

    String mMime;
    String mTestFile;
    final boolean mIsAsync;

    @Before
    public void isPerformanceClassCandidate() {
        Utils.assumeDeviceMeetsPerformanceClassPreconditions();
    }

    public MultiCodecPerfTestBase(String mime, String testFile, boolean isAsync) {
        mMime = mime;
        mTestFile = testFile;
        mIsAsync = isAsync;
    }

    // Returns the list of hardware codecs for given mime
    public static ArrayList<String> getHardwareCodecsForMime(String mime, boolean isEncoder) {
        return getHardwareCodecsForMime(mime, isEncoder, false);
    }

    public static ArrayList<String> getHardwareCodecsForMime(String mime, boolean isEncoder,
            boolean allCodecs) {
        // All the multi-instance tests are limited to codecs that support at least 1280x720 @ 30fps
        // This will exclude hevc constant quality encoders that are limited to max resolution of
        // 512x512
        MediaFormat fmt = MediaFormat.createVideoFormat(mime, 1280, 720);
        fmt.setInteger(MediaFormat.KEY_FRAME_RATE, 30);
        ArrayList<MediaFormat> formatsList = new ArrayList<>();
        formatsList.add(fmt);
        return selectHardwareCodecs(mime, formatsList, null, isEncoder, allCodecs);
    }

    // Returns the max number of 30 fps instances that the given list of mimeCodecPairs
    // supports. It also checks that the each codec supports a PerformancePoint that covers
    // required number of 30 fps instances.
    public int checkAndGetMaxSupportedInstancesForCodecCombinations(int height, int width,
            ArrayList<Pair<String, String>> mimeCodecPairs, int requiredMinInstances)
            throws IOException {
        int[] maxInstances = new int[mimeCodecPairs.size()];
        int[] maxFrameRates = new int[mimeCodecPairs.size()];
        int[] maxMacroBlockRates = new int[mimeCodecPairs.size()];
        int loopCount = 0;
        for (Pair<String, String> mimeCodecPair : mimeCodecPairs) {
            MediaCodec codec = MediaCodec.createByCodecName(mimeCodecPair.second);
            MediaCodecInfo.CodecCapabilities cap = codec.getCodecInfo()
                    .getCapabilitiesForType(mimeCodecPair.first);
            List<PerformancePoint> pps = cap.getVideoCapabilities().getSupportedPerformancePoints();
            assertTrue(pps.size() > 0);

            boolean hasVP9 = mimeCodecPair.first.equals(MediaFormat.MIMETYPE_VIDEO_VP9);
            int requiredFrameRate = requiredMinInstances * 30;

            maxInstances[loopCount] = cap.getMaxSupportedInstances();
            PerformancePoint PPRes = new PerformancePoint(width, height, requiredFrameRate);

            maxMacroBlockRates[loopCount] = 0;
            boolean supportsResolutionPerformance = false;
            for (PerformancePoint pp : pps) {
                if (pp.covers(PPRes)) {
                    supportsResolutionPerformance = true;
                    if (pp.getMaxMacroBlockRate() > maxMacroBlockRates[loopCount]) {
                        maxMacroBlockRates[loopCount] = (int) pp.getMaxMacroBlockRate();
                        maxFrameRates[loopCount] = pp.getMaxFrameRate();
                    }
                }
            }
            codec.release();
            if (!supportsResolutionPerformance) {
                Log.e(LOG_TAG,
                        "Codec " + mimeCodecPair.second + " doesn't support " + height + "p/" +
                                requiredFrameRate + " performance point");
                return 0;
            }
            loopCount++;
        }
        Arrays.sort(maxInstances);
        Arrays.sort(maxFrameRates);
        Arrays.sort(maxMacroBlockRates);
        int minOfMaxInstances = maxInstances[0];
        int minOfMaxFrameRates = maxFrameRates[0];
        int minOfMaxMacroBlockRates = maxMacroBlockRates[0];

        // Calculate how many 30fps max instances it can support from it's mMaxFrameRate
        // amd maxMacroBlockRate. (assuming 16x16 macroblocks)
        return Math.min(minOfMaxInstances, Math.min((int) (minOfMaxFrameRates / 30.0),
                (int) (minOfMaxMacroBlockRates / ((width / 16) * (height / 16)) / 30.0)));
    }

    public int getRequiredMinConcurrentInstances720p(boolean hasVP9) throws IOException {
        // Below T, VP9 requires 60 fps at 720p and minimum of 2 instances
        if (!Utils.isTPerfClass() && hasVP9) {
            return REQUIRED_MIN_CONCURRENT_INSTANCES_FOR_VP9;
        }
        return REQUIRED_MIN_CONCURRENT_INSTANCES;
    }

    boolean isSecureSupportedCodec(String codecName, String mime) throws IOException {
        boolean isSecureSupported;
        MediaCodec codec = MediaCodec.createByCodecName(codecName);
        isSecureSupported = codec.getCodecInfo().getCapabilitiesForType(mime).isFeatureSupported(
                FEATURE_SecurePlayback);
        codec.release();
        return isSecureSupported;
    }

    boolean isWidevineSupported() {
        return MediaDrm.isCryptoSchemeSupported(WIDEVINE_UUID);
    }

    boolean isWidevineL1Supported() throws UnsupportedSchemeException {
        boolean isL1Supported = false;
        if (isWidevineSupported()) {
            MediaDrm mediaDrm = new MediaDrm(WIDEVINE_UUID);
            isL1Supported = mediaDrm.getPropertyString("securityLevel").equals("L1");
            mediaDrm.close();
        }
        return isL1Supported;
    }

    boolean isInternetAvailable() {
        Context context = androidx.test.core.app.ApplicationProvider.getApplicationContext();
        ConnectivityManager cm = context.getSystemService(ConnectivityManager.class);
        NetworkCapabilities cap = cm.getNetworkCapabilities(cm.getActiveNetwork());
        if (cap == null) return false;
        return cap.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }

    boolean meetsSecureDecodePreconditions() throws UnsupportedSchemeException {
        Assume.assumeTrue("Skipping secure decoder performance tests as Widevine is not supported",
                isWidevineSupported());

        if (Utils.isTPerfClass()) {
            assertTrue("If Widevine is supported, L1 support is required for media performance " +
                            "class T devices",
                    isWidevineL1Supported());
            assertTrue("Test requires internet connection for validating secure decoder " +
                            "requirements for media performance class T devices",
                    isInternetAvailable());
            return true;
        }

        return isWidevineL1Supported() && isInternetAvailable();
    }
}
