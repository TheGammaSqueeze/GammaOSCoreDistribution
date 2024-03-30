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

import android.media.MediaCodec;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Build;
import android.os.Bundle;

import androidx.test.filters.SdkSuppress;
import androidx.test.filters.SmallTest;

import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static android.media.MediaCodecInfo.CodecCapabilities.COLOR_FormatYUVP010;
import static android.media.MediaCodecInfo.CodecProfileLevel.*;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Test to validate hdr static and dynamic metadata in encoders
 */
@RunWith(Parameterized.class)
// P010 support was added in Android T, hence limit the following tests to Android T and above
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.TIRAMISU, codeName = "Tiramisu")
public class EncoderHDRInfoTest extends CodecEncoderTestBase {
    private static final String LOG_TAG = EncoderHDRInfoTest.class.getSimpleName();
    private MediaMuxer mMuxer;
    private int mTrackID = -1;

    static final ArrayList<String> mCheckESList = new ArrayList<>();

    static {
        mCheckESList.add(MediaFormat.MIMETYPE_VIDEO_AV1);
        mCheckESList.add(MediaFormat.MIMETYPE_VIDEO_AVC);
        mCheckESList.add(MediaFormat.MIMETYPE_VIDEO_HEVC);
    }

    public EncoderHDRInfoTest(String encoderName, String mediaType, int bitrate, int width,
            int height, boolean testDynamicMetadata) {
        super(encoderName, mediaType, new int[]{bitrate}, new int[]{width}, new int[]{height});
        mTestDynamicMetadata = testDynamicMetadata;
    }

    void enqueueInput(int bufferIndex) {
        if(mTestDynamicMetadata){
            final Bundle params = new Bundle();
            byte[] info = loadByteArrayFromString(HDR_DYNAMIC_INFO[mInputCount]);
            params.putByteArray(MediaFormat.KEY_HDR10_PLUS_INFO, info);
            mCodec.setParameters(params);
            if (mInputCount >= HDR_DYNAMIC_INFO.length) {
                mSawInputEOS = true;
            }
        }
        super.enqueueInput(bufferIndex);
    }
    void dequeueOutput(int bufferIndex, MediaCodec.BufferInfo info) {
        MediaFormat bufferFormat = mCodec.getOutputFormat(bufferIndex);
        if (info.size > 0) {
            ByteBuffer buf = mCodec.getOutputBuffer(bufferIndex);
            if (mMuxer != null) {
                if (mTrackID == -1) {
                    mTrackID = mMuxer.addTrack(bufferFormat);
                    mMuxer.start();
                }
                mMuxer.writeSampleData(mTrackID, buf, info);
            }
        }
        super.dequeueOutput(bufferIndex, info);
        // verify if the out fmt contains HDR Dynamic metadata as expected
        if (mTestDynamicMetadata && mOutputCount > 0) {
            validateHDRDynamicMetaData(bufferFormat,
                    ByteBuffer.wrap(loadByteArrayFromString(HDR_DYNAMIC_INFO[mOutputCount - 1])));
        }
    }

    @Parameterized.Parameters(name = "{index}({0}_{1})")
    public static Collection<Object[]> input() {
        final boolean isEncoder = true;
        final boolean needAudio = false;
        final boolean needVideo = true;

        final List<Object[]> exhaustiveArgsList = Arrays.asList(new Object[][]{
                {MediaFormat.MIMETYPE_VIDEO_AV1, 512000, 352, 288, false},
                {MediaFormat.MIMETYPE_VIDEO_VP9, 512000, 352, 288, false},
                {MediaFormat.MIMETYPE_VIDEO_HEVC, 512000, 352, 288, false},

                {MediaFormat.MIMETYPE_VIDEO_AV1, 512000, 352, 288, true},
                {MediaFormat.MIMETYPE_VIDEO_VP9, 512000, 352, 288, true},
                {MediaFormat.MIMETYPE_VIDEO_HEVC, 512000, 352, 288, true},
        });

        return prepareParamList(exhaustiveArgsList, isEncoder, needAudio, needVideo, false);
    }

    @SmallTest
    @Test(timeout = PER_TEST_TIMEOUT_SMALL_TEST_MS)
    public void testHDRMetadata() throws IOException, InterruptedException {
        int profile;
        setUpParams(1);
        MediaFormat format = mFormats.get(0);
        final ByteBuffer hdrStaticInfo = ByteBuffer.wrap(loadByteArrayFromString(HDR_STATIC_INFO));
        if (mTestDynamicMetadata) {
            profile = mProfileHdr10PlusMap.getOrDefault(mMime, new int[]{-1})[0];
        } else {
            profile = mProfileHdr10Map.getOrDefault(mMime, new int[]{-1})[0];
        }
        format.setInteger(MediaFormat.KEY_PROFILE, profile);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, COLOR_FormatYUVP010);
        format.setInteger(MediaFormat.KEY_COLOR_RANGE, MediaFormat.COLOR_RANGE_LIMITED);
        format.setInteger(MediaFormat.KEY_COLOR_STANDARD, MediaFormat.COLOR_STANDARD_BT2020);
        format.setInteger(MediaFormat.KEY_COLOR_TRANSFER, MediaFormat.COLOR_TRANSFER_ST2084);
        format.setByteBuffer(MediaFormat.KEY_HDR_STATIC_INFO, hdrStaticInfo);
        mFormats.clear();
        mFormats.add(format);
        Assume.assumeTrue(mCodecName + " does not support this HDR profile",
                areFormatsSupported(mCodecName, mMime, mFormats));
        Assume.assumeTrue(mCodecName + " does not support color format COLOR_FormatYUVP010",
                hasSupportForColorFormat(mCodecName, mMime, COLOR_FormatYUVP010));
        mBytesPerSample = 2;
        setUpSource(INPUT_VIDEO_FILE_HBD);
        mOutputBuff = new OutputManager();
        mCodec = MediaCodec.createByCodecName(mCodecName);
        mOutputBuff.reset();
        String log = String.format("format: %s \n codec: %s:: ", format, mCodecName);
        File tmpFile;
        int muxerFormat;
        if (mMime.equals(MediaFormat.MIMETYPE_VIDEO_VP9)) {
            muxerFormat = MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM;
            tmpFile = File.createTempFile("tmp10bit", ".webm");
        } else {
            muxerFormat = MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4;
            tmpFile = File.createTempFile("tmp10bit", ".mp4");
        }
        mMuxer = new MediaMuxer(tmpFile.getAbsolutePath(), muxerFormat);
        configureCodec(format, true, true, true);
        mCodec.start();
        doWork(4);
        queueEOS();
        waitForAllOutputs();
        if (mTrackID != -1) {
            mMuxer.stop();
            mTrackID = -1;
        }
        if (mMuxer != null) {
            mMuxer.release();
            mMuxer = null;
        }
        assertTrue(log + "unexpected error", !mAsyncHandle.hasSeenError());
        assertTrue(log + "no input sent", 0 != mInputCount);
        assertTrue(log + "output received", 0 != mOutputCount);

        MediaFormat fmt = mCodec.getOutputFormat();
        mCodec.stop();
        mCodec.release();

        // verify if the out fmt contains HDR Static metadata as expected
        validateHDRStaticMetaData(fmt, hdrStaticInfo);

        // verify if the muxed file contains HDR metadata as expected
        MediaCodecList codecList = new MediaCodecList(MediaCodecList.REGULAR_CODECS);
        String decoder = codecList.findDecoderForFormat(format);
        assertNotNull("Device advertises support for encoding " + format.toString() +
                " but not decoding it", decoder);
        CodecDecoderTestBase cdtb =
                new CodecDecoderTestBase(decoder, mMime, tmpFile.getAbsolutePath());
        String parent = tmpFile.getParent();
        if (parent != null) parent += File.separator;
        else parent = "";
        cdtb.validateHDRStaticMetaData(parent, tmpFile.getName(), hdrStaticInfo, false);
        if (mTestDynamicMetadata) {
            cdtb.validateHDRDynamicMetaData(parent, tmpFile.getName(), false);
        }

        // if HDR static metadata can also be signalled via elementary stream then verify if
        // the elementary stream contains HDR static data as expected
        if (mCheckESList.contains(mMime)) {
            cdtb.validateHDRStaticMetaData(parent, tmpFile.getName(), hdrStaticInfo, true);

            // since HDR static metadata is signalled via elementary stream then verify if
            // the elementary stream contains HDR static data as expected
            if (mTestDynamicMetadata) {
                cdtb.validateHDRDynamicMetaData(parent, tmpFile.getName(), true);
            }
        }

        tmpFile.delete();
    }
}
