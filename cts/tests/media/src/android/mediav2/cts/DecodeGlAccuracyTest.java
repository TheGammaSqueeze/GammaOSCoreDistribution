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

import static android.media.MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface;

import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.opengl.GLES20;
import android.util.Log;

import androidx.test.filters.LargeTest;

import com.android.compatibility.common.util.ApiTest;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import javax.microedition.khronos.opengles.GL10;

/**
 * Validates the correctness of color conversion in the decode followed by OpenGL
 * rendering scenarios. The input video files fed to the decoders contain the pixel
 * data in compressed YUV format. The output of the decoders is shared with OpenGL
 * as external textures. And OpenGL outputs RGB pixels. The class validates whether
 * the conversion of input YUV to output RGB is in accordance with the chosen color
 * aspects. Video files used in the test do not have any color aspects info coded in
 * the bitstreams
 */
@RunWith(Parameterized.class)
public class DecodeGlAccuracyTest extends CodecDecoderTestBase {
    private static final String LOG_TAG = DecodeGlAccuracyTest.class.getSimpleName();

    // Allowed color tolerance to account for differences in the conversion process
    private static final int ALLOWED_COLOR_DELTA = 8;

    // The test video assets were generated with a set of color bars.
    // Depending on the color aspects, the values from OpenGL pbuffer
    // should not differ from the reference color values for the
    // given color aspects below by more than the allowed tolerance.
    //
    // The reference RGB values were computed using the process described below.
    //
    // RGB = Transpose(FLOOR_CLIP_PIXEL(CONV_CSC * (Transpose(YUV) - LVL_OFFSET)))
    // The matrices LVL_OFFSET and CONV_CSC for different color aspects are below.
    //
    // YUV values in the 8bit color bar test videos are in COLOR_BARS_YUV below
    //
    // The color conversion matrices (CONV_CSC) for the RGB equation above:
    // MULTIPLY_ROW_WISE_LR = Transpose({255/219, 255/224, 255/224})
    // CONV_FLOAT_601_FR =
    //     {{1, 0, 1.402},
    //      {1, -0.344136, -0.714136},
    //      {1, 1.772, 0},}
    // CONV_FLOAT_709_FR =
    //     {{1, 0, 1.5748},
    //      {1, -0.1873, -0.4681},
    //      {1, 1.8556, 0},}
    // CONV_FLOAT_601_LR = MULTIPLY_ROW_WISE_LR . CONV_FLOAT_601_FR
    // CONV_FLOAT_709_LR = MULTIPLY_ROW_WISE_LR . CONV_FLOAT_709_FR
    //
    // The level shift matrices (LVL_OFFSET) for the RGB equation above:
    // LVL_OFFSET_LR = Transpose({16, 128, 128})
    // LVL_OFFSET_FR = Transpose({0, 128, 128})

    private static final int[][] COLOR_BARS_YUV = new int[][]{
            {126, 191, 230},
            {98, 104, 204},
            {180, 20, 168},
            {121, 109, 60},
            {114, 179, 172},
            {133, 138, 118},
            {183, 93, 153},
            {203, 20, 33},
            {147, 131, 183},
            {40, 177, 202},
            {170, 82, 96},
    };

    // Reference RGB values for 601 Limited Range
    private static final int[][] COLOR_BARS_601LR = new int[][]{
            {255, 17, 252},
            {219, 40, 44},
            {255, 196, 0},
            {11, 182, 81},
            {185, 55, 214},
            {119, 137, 153},
            {235, 183, 119},
            {62, 255, 0},
            {242, 103, 155},
            {148, 0, 126},
            {127, 219, 82},
    };
    // Reference RGB values for 601 Full Range
    private static final int[][] COLOR_BARS_601FR = new int[][]{
            {255, 31, 237},
            {204, 51, 55},
            {236, 188, 0},
            {25, 176, 87},
            {175, 65, 204},
            {118, 136, 150},
            {218, 177, 120},
            {69, 255, 11},
            {224, 106, 152},
            {143, 0, 126},
            {125, 208, 88},
    };
    // Reference RGB values for 709 Limited Range
    private static final int[][] COLOR_BARS_709LR = new int[][]{
            {255, 57, 255},
            {234, 57, 42},
            {255, 188, 0},
            {0, 159, 79},
            {194, 77, 219},
            {117, 136, 154},
            {240, 184, 116},
            {43, 255, 0},
            {253, 119, 155},
            {163, 0, 130},
            {120, 202, 78},
    };

    // The test videos were generated with the above color bars. Each bar is of width 16.
    private static final int COLOR_BAR_WIDTH = 16;
    private static final int COLOR_BAR_OFFSET_X = 8;
    private static final int COLOR_BAR_OFFSET_Y = 64;

    private int[][] mColorBars;

    private final String mCompName;
    private final String mFileName;
    private int mWidth;
    private int mHeight;
    private final int mRange;
    private final int mStandard;
    private final int mTransferCurve;
    private final boolean mUseYuvSampling;

    private OutputSurface mEGLWindowOutSurface;
    private int mBadFrames = 0;

    public DecodeGlAccuracyTest(String decoder, String mediaType, String fileName, int range,
            int standard, int transfer, boolean useYuvSampling) {
        super(null, mediaType, null);
        mCompName = decoder;
        mFileName = fileName;
        mRange = range;
        mStandard = standard;
        mTransferCurve = transfer;
        mUseYuvSampling = useYuvSampling;

        if (!mUseYuvSampling) {
            mColorBars = COLOR_BARS_601LR;
            if ((mStandard == MediaFormat.COLOR_STANDARD_BT601_NTSC) &&
                    (mRange == MediaFormat.COLOR_RANGE_LIMITED)) {
                mColorBars = COLOR_BARS_601LR;
            } else if ((mStandard == MediaFormat.COLOR_STANDARD_BT601_NTSC) &&
                    (mRange == MediaFormat.COLOR_RANGE_FULL)) {
                mColorBars = COLOR_BARS_601FR;
            } else if ((mStandard == MediaFormat.COLOR_STANDARD_BT709) &&
                    (mRange == MediaFormat.COLOR_RANGE_LIMITED)) {
                mColorBars = COLOR_BARS_709LR;
            } else {
                Log.e(LOG_TAG, "Unsupported Color Aspects.");
            }
        } else {
            mColorBars = COLOR_BARS_YUV;
        }
    }

    @Parameterized.Parameters(name = "{index}({0}_{1}_{3}_{4}_{5}_{6})")
    public static Collection<Object[]> input() {
        final boolean isEncoder = false;
        final boolean needAudio = false;
        final boolean needVideo = true;

        final List<Object[]> argsList = Arrays.asList(new Object[][]{
                // mediaType, asset, range, standard, transfer
                // 601LR
                {MediaFormat.MIMETYPE_VIDEO_AVC, "color_bands_176x176_h264_8bit.mp4",
                        MediaFormat.COLOR_RANGE_LIMITED,
                        MediaFormat.COLOR_STANDARD_BT601_NTSC,
                        MediaFormat.COLOR_TRANSFER_SDR_VIDEO},
                {MediaFormat.MIMETYPE_VIDEO_HEVC, "color_bands_176x176_hevc_8bit.mp4",
                        MediaFormat.COLOR_RANGE_LIMITED,
                        MediaFormat.COLOR_STANDARD_BT601_NTSC,
                        MediaFormat.COLOR_TRANSFER_SDR_VIDEO},
                {MediaFormat.MIMETYPE_VIDEO_VP8, "color_bands_176x176_vp8_8bit.webm",
                        MediaFormat.COLOR_RANGE_LIMITED,
                        MediaFormat.COLOR_STANDARD_BT601_NTSC,
                        MediaFormat.COLOR_TRANSFER_SDR_VIDEO},
                {MediaFormat.MIMETYPE_VIDEO_VP9, "color_bands_176x176_vp9_8bit.webm",
                        MediaFormat.COLOR_RANGE_LIMITED,
                        MediaFormat.COLOR_STANDARD_BT601_NTSC,
                        MediaFormat.COLOR_TRANSFER_SDR_VIDEO},
                {MediaFormat.MIMETYPE_VIDEO_AV1, "color_bands_176x176_av1_8bit.webm",
                        MediaFormat.COLOR_RANGE_LIMITED,
                        MediaFormat.COLOR_STANDARD_BT601_NTSC,
                        MediaFormat.COLOR_TRANSFER_SDR_VIDEO},

                // 601FR
                {MediaFormat.MIMETYPE_VIDEO_AVC, "color_bands_176x176_h264_8bit_fr.mp4",
                        MediaFormat.COLOR_RANGE_FULL,
                        MediaFormat.COLOR_STANDARD_BT601_NTSC,
                        MediaFormat.COLOR_TRANSFER_SDR_VIDEO},
                {MediaFormat.MIMETYPE_VIDEO_HEVC, "color_bands_176x176_hevc_8bit_fr.mp4",
                        MediaFormat.COLOR_RANGE_FULL,
                        MediaFormat.COLOR_STANDARD_BT601_NTSC,
                        MediaFormat.COLOR_TRANSFER_SDR_VIDEO},
                {MediaFormat.MIMETYPE_VIDEO_VP8, "color_bands_176x176_vp8_8bit_fr.webm",
                        MediaFormat.COLOR_RANGE_FULL,
                        MediaFormat.COLOR_STANDARD_BT601_NTSC,
                        MediaFormat.COLOR_TRANSFER_SDR_VIDEO},
                {MediaFormat.MIMETYPE_VIDEO_VP9, "color_bands_176x176_vp9_8bit_fr.webm",
                        MediaFormat.COLOR_RANGE_FULL,
                        MediaFormat.COLOR_STANDARD_BT601_NTSC,
                        MediaFormat.COLOR_TRANSFER_SDR_VIDEO},
                {MediaFormat.MIMETYPE_VIDEO_AV1, "color_bands_176x176_av1_8bit_fr.webm",
                        MediaFormat.COLOR_RANGE_FULL,
                        MediaFormat.COLOR_STANDARD_BT601_NTSC,
                        MediaFormat.COLOR_TRANSFER_SDR_VIDEO},

                // 709LR
                {MediaFormat.MIMETYPE_VIDEO_AVC, "color_bands_176x176_h264_8bit.mp4",
                        MediaFormat.COLOR_RANGE_LIMITED,
                        MediaFormat.COLOR_STANDARD_BT709,
                        MediaFormat.COLOR_TRANSFER_SDR_VIDEO},
                {MediaFormat.MIMETYPE_VIDEO_HEVC, "color_bands_176x176_hevc_8bit.mp4",
                        MediaFormat.COLOR_RANGE_LIMITED,
                        MediaFormat.COLOR_STANDARD_BT709,
                        MediaFormat.COLOR_TRANSFER_SDR_VIDEO},
                {MediaFormat.MIMETYPE_VIDEO_VP8, "color_bands_176x176_vp8_8bit.webm",
                        MediaFormat.COLOR_RANGE_LIMITED,
                        MediaFormat.COLOR_STANDARD_BT709,
                        MediaFormat.COLOR_TRANSFER_SDR_VIDEO},
                {MediaFormat.MIMETYPE_VIDEO_VP9, "color_bands_176x176_vp9_8bit.webm",
                        MediaFormat.COLOR_RANGE_LIMITED,
                        MediaFormat.COLOR_STANDARD_BT709,
                        MediaFormat.COLOR_TRANSFER_SDR_VIDEO},
                {MediaFormat.MIMETYPE_VIDEO_AV1, "color_bands_176x176_av1_8bit.webm",
                        MediaFormat.COLOR_RANGE_LIMITED,
                        MediaFormat.COLOR_STANDARD_BT709,
                        MediaFormat.COLOR_TRANSFER_SDR_VIDEO},

                // Note: OpenGL is not required to support 709 FR. So we are not testing it.
        });
        final List<Object[]> exhaustiveArgsList = new ArrayList<>();
        for (Object[] arg : argsList) {
            int argLength = argsList.get(0).length;
            boolean[] boolStates = {true, false};
            for (boolean useYuvSampling : boolStates) {
                Object[] testArgs = new Object[argLength + 1];
                System.arraycopy(arg, 0, testArgs, 0, argLength);
                testArgs[argLength] = useYuvSampling;
                exhaustiveArgsList.add(testArgs);
            }
        }
        return CodecTestBase.prepareParamList(exhaustiveArgsList, isEncoder, needAudio, needVideo,
                false);
    }

    boolean isColorClose(int actual, int expected) {
        int delta = Math.abs(actual - expected);
        return (delta <= ALLOWED_COLOR_DELTA);
    }

    private boolean checkSurfaceFrame(int frameIndex) {
        ByteBuffer pixelBuf = ByteBuffer.allocateDirect(4);
        boolean frameFailed = false;
        for (int i = 0; i < mColorBars.length; i++) {
            int x = COLOR_BAR_WIDTH * i + COLOR_BAR_OFFSET_X;
            int y = COLOR_BAR_OFFSET_Y;
            GLES20.glReadPixels(x, y, 1, 1, GL10.GL_RGBA, GL10.GL_UNSIGNED_BYTE, pixelBuf);
            int r = pixelBuf.get(0) & 0xff;
            int g = pixelBuf.get(1) & 0xff;
            int b = pixelBuf.get(2) & 0xff;
            if (!(isColorClose(r, mColorBars[i][0]) &&
                    isColorClose(g, mColorBars[i][1]) &&
                    isColorClose(b, mColorBars[i][2]))) {
                Log.w(LOG_TAG, "Bad frame " + frameIndex + " (rect={" + x + " " + y + "} :rgb=" +
                        r + "," + g + "," + b + " vs. expected " + mColorBars[i][0] +
                        "," + mColorBars[i][1] + "," + mColorBars[i][2] + ")");
                frameFailed = true;
            }
        }
        return frameFailed;
    }

    void dequeueOutput(int bufferIndex, MediaCodec.BufferInfo info) {
        if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
            mSawOutputEOS = true;
        }
        if (ENABLE_LOGS) {
            Log.v(LOG_TAG, "output: id: " + bufferIndex + " flags: " + info.flags + " size: " +
                    info.size + " timestamp: " + info.presentationTimeUs);
        }
        if (info.size > 0 && (info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
            mOutputBuff.saveOutPTS(info.presentationTimeUs);
            mOutputCount++;
        }
        mCodec.releaseOutputBuffer(bufferIndex, mSurface != null);
        if (info.size > 0) {
            mEGLWindowOutSurface.awaitNewImage();
            mEGLWindowOutSurface.drawImage();
            if (checkSurfaceFrame(mOutputCount - 1)) mBadFrames++;
        }
    }

    /**
     * The test decodes video assets with color bars and outputs frames to OpenGL input surface.
     * The OpenGL fragment shader reads the frame buffers as externl textures and renders to
     * a pbuffer. The output RGB values are read and compared against the expected values.
     */
    @ApiTest(apis = {"android.media.MediaCodec#dequeueOutputBuffer",
                     "android.media.MediaCodec#releaseOutputBuffer",
                     "android.media.MediaCodec.Callback#onOutputBufferAvailable",
                     "android.media.MediaFormat#setInteger(KEY_COLOR_RANGE)",
                     "android.media.MediaFormat#setInteger(KEY_COLOR_STANDARD)",
                     "android.media.MediaFormat#setInteger(KEY_COLOR_TRANSFER)"})
    @LargeTest
    @Test(timeout = CodecTestBase.PER_TEST_TIMEOUT_LARGE_TEST_MS)
    public void testDecodeGlAccuracyRGB() throws IOException, InterruptedException {
        if (mRange != MediaFormat.COLOR_RANGE_LIMITED
                || mStandard != MediaFormat.COLOR_STANDARD_BT601_NTSC) {
            // This test was added in Android T, but some upgrading devices fail the test. Hence
            // limit the test to devices launching with T
            assumeTrue("Skipping color range " + mRange + " and color standard " + mStandard +
                            " for devices upgrading to T",
                    FIRST_SDK_IS_AT_LEAST_T && VNDK_IS_AT_LEAST_T);

            // TODO (b/219748700): Android software codecs work only with 601LR. Skip for now.
            assumeTrue("Skipping " + mCompName + " for color range " + mRange
                            + " and color standard " + mStandard,
                    isVendorCodec(mCompName));
        }

        MediaFormat format = setUpSource(mFileName);

        // Set color parameters
        format.setInteger(MediaFormat.KEY_COLOR_RANGE, mRange);
        format.setInteger(MediaFormat.KEY_COLOR_STANDARD, mStandard);
        format.setInteger(MediaFormat.KEY_COLOR_TRANSFER, mTransferCurve);

        // Set the format to surface mode
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, COLOR_FormatSurface);

        mWidth = format.getInteger(MediaFormat.KEY_WIDTH);
        mHeight = format.getInteger(MediaFormat.KEY_HEIGHT);
        mEGLWindowOutSurface = new OutputSurface(mWidth, mHeight, false, mUseYuvSampling);

        // If device supports HDR editing, then GL_EXT_YUV_target extension support is mandatory
        if (mUseYuvSampling) {
            String message = "Device doesn't support EXT_YUV_target GL extension";
            if (IS_AT_LEAST_T && IS_HDR_EDITING_SUPPORTED) {
                assertTrue(message, mEGLWindowOutSurface.getEXTYuvTargetSupported());
            } else {
                assumeTrue(message, mEGLWindowOutSurface.getEXTYuvTargetSupported());
            }
        }

        mSurface = mEGLWindowOutSurface.getSurface();

        mCodec = MediaCodec.createByCodecName(mCompName);
        configureCodec(format, true, true, false);
        mOutputBuff = new OutputManager();
        mCodec.start();
        doWork(Integer.MAX_VALUE);
        queueEOS();
        waitForAllOutputs();
        validateColorAspects(mCodec.getOutputFormat(), mRange, mStandard, mTransferCurve);
        mCodec.stop();
        mCodec.release();
        mEGLWindowOutSurface.release();

        assertTrue("color difference exceeds allowed tolerance in " + mBadFrames + " out of " +
                mOutputCount + " frames", 0 == mBadFrames);
    }
}

