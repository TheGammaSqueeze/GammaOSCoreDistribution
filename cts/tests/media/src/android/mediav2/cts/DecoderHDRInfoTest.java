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

package android.mediav2.cts;

import android.media.MediaFormat;
import android.os.Build;

import androidx.test.filters.SdkSuppress;
import androidx.test.filters.SmallTest;

import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * Test to validate hdr static metadata in decoders
 */
@RunWith(Parameterized.class)
// P010 support was added in Android T, hence limit the following tests to Android T and above
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.TIRAMISU, codeName = "Tiramisu")
public class DecoderHDRInfoTest extends CodecDecoderTestBase {
    private static final String LOG_TAG = DecoderHDRInfoTest.class.getSimpleName();
    private static final String HDR_STATIC_INFO =
            "00 d0 84 80 3e c2 33 c4 86 4c 1d b8 0b 13 3d 42 40 a0 0f 32 00 10 27 df 0d";
    private static final String HDR_STATIC_INCORRECT_INFO =
            "00 d0 84 80 3e c2 33 c4 86 10 27 d0 07 13 3d 42 40 a0 0f 32 00 10 27 df 0d";

    private final ByteBuffer mHDRStaticInfoStream;
    private final ByteBuffer mHDRStaticInfoContainer;

    public DecoderHDRInfoTest(String codecName, String mediaType, String testFile,
                              String hdrStaticInfoStream, String hdrStaticInfoContainer) {
        super(codecName, mediaType, testFile);
        mHDRStaticInfoStream = hdrStaticInfoStream != null ?
                ByteBuffer.wrap(loadByteArrayFromString(hdrStaticInfoStream)) : null;
        mHDRStaticInfoContainer = hdrStaticInfoContainer != null ?
                ByteBuffer.wrap(loadByteArrayFromString(hdrStaticInfoContainer)) : null;
    }

    @Parameterized.Parameters(name = "{index}({0}_{1})")
    public static Collection<Object[]> input() {
        final boolean isEncoder = false;
        final boolean needAudio = false;
        final boolean needVideo = true;
        final List<Object[]> exhaustiveArgsList = Arrays.asList(new Object[][]{
                // codecMediaType, testFile, hdrInfo in stream, hdrInfo in container
                {MediaFormat.MIMETYPE_VIDEO_HEVC,
                        "cosmat_352x288_hdr10_stream_and_container_correct_hevc.mkv",
                        HDR_STATIC_INFO, HDR_STATIC_INFO},
                {MediaFormat.MIMETYPE_VIDEO_HEVC,
                        "cosmat_352x288_hdr10_stream_correct_container_incorrect_hevc.mkv",
                        HDR_STATIC_INFO, HDR_STATIC_INCORRECT_INFO},
                {MediaFormat.MIMETYPE_VIDEO_HEVC, "cosmat_352x288_hdr10_only_stream_hevc.mkv",
                        HDR_STATIC_INFO, null},
                {MediaFormat.MIMETYPE_VIDEO_HEVC, "cosmat_352x288_hdr10_only_container_hevc.mkv",
                        null, HDR_STATIC_INFO},
                {MediaFormat.MIMETYPE_VIDEO_VP9, "cosmat_352x288_hdr10_only_container_vp9.mkv",
                        null, HDR_STATIC_INFO},
                {MediaFormat.MIMETYPE_VIDEO_AV1,
                        "cosmat_352x288_hdr10_stream_and_container_correct_av1.mkv",
                        HDR_STATIC_INFO, HDR_STATIC_INFO},
                {MediaFormat.MIMETYPE_VIDEO_AV1,
                        "cosmat_352x288_hdr10_stream_correct_container_incorrect_av1.mkv",
                        HDR_STATIC_INFO, HDR_STATIC_INCORRECT_INFO},
                {MediaFormat.MIMETYPE_VIDEO_AV1, "cosmat_352x288_hdr10_only_stream_av1.mkv",
                        HDR_STATIC_INFO, null},
                {MediaFormat.MIMETYPE_VIDEO_AV1, "cosmat_352x288_hdr10_only_container_av1.mkv",
                        null, HDR_STATIC_INFO},
        });
        return prepareParamList(exhaustiveArgsList, isEncoder, needAudio, needVideo, false);
    }

    @SmallTest
    @Test(timeout = PER_TEST_TIMEOUT_SMALL_TEST_MS)
    public void testHDRMetadata() throws IOException, InterruptedException {
        int[] Hdr10Profiles = mProfileHdr10Map.get(mMime);
        Assume.assumeNotNull("Test is only applicable to codecs that have HDR10 profiles",
                Hdr10Profiles);
        MediaFormat format = setUpSource(mTestFile);
        mExtractor.release();
        ArrayList<MediaFormat> formats = new ArrayList<>();
        formats.add(format);

        // When HDR metadata isn't present in the container, but included in the bitstream,
        // extractors may not be able to populate HDR10/HDR10+ profiles correctly.
        // In such cases, override the profile
        if (mHDRStaticInfoContainer == null && mHDRStaticInfoStream != null) {
            int profile = Hdr10Profiles[0];
            format.setInteger(MediaFormat.KEY_PROFILE, profile);
        }
        Assume.assumeTrue(areFormatsSupported(mCodecName, mMime, formats));

        if (mHDRStaticInfoContainer != null) {
            validateHDRStaticMetaData(format, mHDRStaticInfoContainer);
        }

        validateHDRStaticMetaData(mInpPrefix, mTestFile,
                mHDRStaticInfoStream == null ? mHDRStaticInfoContainer : mHDRStaticInfoStream,
                false);
        if (mHDRStaticInfoStream != null) {
            if (EncoderHDRInfoTest.mCheckESList.contains(mMime)) {
                validateHDRStaticMetaData(mInpPrefix, mTestFile, mHDRStaticInfoStream, true);
            }
        }
    }
}
