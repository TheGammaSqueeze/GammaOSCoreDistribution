/*
 * Copyright (C) 2020 The Android Open Source Project
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
import android.opengl.GLES20;
import android.os.Build;
import android.util.Log;
import android.util.Pair;
import android.view.Surface;

import androidx.test.filters.SmallTest;

import com.android.compatibility.common.util.ApiLevelUtil;

import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static android.media.MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface;
import static android.media.MediaCodecInfo.CodecCapabilities.COLOR_FormatYUVP010;
import static android.media.MediaCodecInfo.CodecCapabilities.COLOR_Format32bitABGR2101010;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Validate ColorAspects configuration for listed encoder components
 */
@RunWith(Parameterized.class)
public class EncoderColorAspectsTest extends CodecEncoderTestBase {
    private static final String LOG_TAG = EncoderColorAspectsTest.class.getSimpleName();

    private int mRange;
    private int mStandard;
    private int mTransferCurve;
    private boolean mUseHighBitDepth;
    private boolean mSurfaceMode;

    private Surface mInpSurface;
    private EGLWindowSurface mEGLWindowInpSurface;
    private MediaFormat mConfigFormat;

    private MediaMuxer mMuxer;
    private int mTrackID = -1;

    private int mLatency;
    private boolean mReviseLatency;

    private ArrayList<String> mCheckESList = new ArrayList<>();

    private static boolean sIsAtLeastR = ApiLevelUtil.isAtLeast(Build.VERSION_CODES.R);

    public EncoderColorAspectsTest(String encoderName, String mime, int width, int height,
            int range, int standard, int transferCurve, boolean useHighBitDepth,
            boolean surfaceMode) {
        super(encoderName, mime, new int[]{64000}, new int[]{width}, new int[]{height});
        mRange = range;
        mStandard = standard;
        mTransferCurve = transferCurve;
        mUseHighBitDepth = useHighBitDepth;
        mSurfaceMode = surfaceMode;
        mWidth = width;
        mHeight = height;
        setUpParams(1);
        mConfigFormat = mFormats.get(0);
        if (mRange >= 0) mConfigFormat.setInteger(MediaFormat.KEY_COLOR_RANGE, mRange);
        else mRange = 0;
        if (mStandard >= 0) mConfigFormat.setInteger(MediaFormat.KEY_COLOR_STANDARD, mStandard);
        else mStandard = 0;
        if (mTransferCurve >= 0)
            mConfigFormat.setInteger(MediaFormat.KEY_COLOR_TRANSFER, mTransferCurve);
        else mTransferCurve = 0;
        mCheckESList.add(MediaFormat.MIMETYPE_VIDEO_AVC);
        mCheckESList.add(MediaFormat.MIMETYPE_VIDEO_HEVC);
    }

    void dequeueOutput(int bufferIndex, MediaCodec.BufferInfo info) {
        if (info.size > 0) {
            ByteBuffer buf = mCodec.getOutputBuffer(bufferIndex);
            if (mMuxer != null) {
                if (mTrackID == -1) {
                    mTrackID = mMuxer.addTrack(mCodec.getOutputFormat());
                    mMuxer.start();
                }
                mMuxer.writeSampleData(mTrackID, buf, info);
            }
        }
        super.dequeueOutput(bufferIndex, info);
    }

    private static void prepareArgsList(List<Object[]> exhaustiveArgsList,
            List<String> stringArgsList, String[] mediaTypes, int[] ranges, int[] standards,
            int[] transfers, boolean useHighBitDepth) {
        // Assuming all combinations are supported by the standard which is true for AVC, HEVC, AV1,
        // VP8 and VP9.
        for (String mediaType : mediaTypes) {
            for (int range : ranges) {
                for (int standard : standards) {
                    for (int transfer : transfers) {
                        String currentObject =
                                mediaType + "_" + range + "_" + standard + "_" + transfer;
                        if (!stringArgsList.contains(currentObject)) {
                            exhaustiveArgsList
                                    .add(new Object[]{mediaType, 176, 144, range, standard,
                                            transfer, useHighBitDepth, false});
                            exhaustiveArgsList
                                    .add(new Object[]{mediaType, 176, 144, range, standard,
                                            transfer, useHighBitDepth, true});
                            stringArgsList.add(currentObject);
                        }
                    }
                }
            }
        }
    }

    @Parameterized.Parameters(name = "{index}({0}_{1}_{4}_{5}_{6}_{7}_{8})")
    public static Collection<Object[]> input() {
        final boolean isEncoder = true;
        final boolean needAudio = false;
        final boolean needVideo = true;
        String[] mimes = {MediaFormat.MIMETYPE_VIDEO_AVC,
                MediaFormat.MIMETYPE_VIDEO_HEVC,
                MediaFormat.MIMETYPE_VIDEO_VP8,
                MediaFormat.MIMETYPE_VIDEO_VP9};
        int[] ranges = {-1,
                UNSPECIFIED,
                MediaFormat.COLOR_RANGE_FULL,
                MediaFormat.COLOR_RANGE_LIMITED};
        int[] standards = {-1,
                UNSPECIFIED,
                MediaFormat.COLOR_STANDARD_BT709,
                MediaFormat.COLOR_STANDARD_BT601_PAL,
                MediaFormat.COLOR_STANDARD_BT601_NTSC};
        int[] transfers = {-1,
                UNSPECIFIED,
                MediaFormat.COLOR_TRANSFER_LINEAR,
                MediaFormat.COLOR_TRANSFER_SDR_VIDEO};

        String[] mediaTypesHighBitDepth = {MediaFormat.MIMETYPE_VIDEO_AVC,
                MediaFormat.MIMETYPE_VIDEO_HEVC,
                MediaFormat.MIMETYPE_VIDEO_VP9,
                MediaFormat.MIMETYPE_VIDEO_AV1};
        int[] standardsHighBitDepth = {-1,
                UNSPECIFIED,
                MediaFormat.COLOR_STANDARD_BT2020};
        int[] transfersHighBitDepth = {-1,
                UNSPECIFIED,
                MediaFormat.COLOR_TRANSFER_HLG,
                MediaFormat.COLOR_TRANSFER_ST2084};

        List<Object[]> exhaustiveArgsList = new ArrayList<>();
        List<String> stringArgsList = new ArrayList<>();
        prepareArgsList(exhaustiveArgsList, stringArgsList, mimes, ranges, standards, transfers,
                false);
        // P010 support was added in Android T, hence limit the following tests to Android T and
        // above
        if (IS_AT_LEAST_T) {
            prepareArgsList(exhaustiveArgsList, stringArgsList, mediaTypesHighBitDepth, ranges,
                    standardsHighBitDepth, transfersHighBitDepth, true);
        }
        return CodecTestBase
                .prepareParamList(exhaustiveArgsList, isEncoder, needAudio, needVideo, false);
    }
    private long computePresentationTime(int frameIndex) {
        return frameIndex * 1000000 / mFrameRate;
    }

    private void generateSurfaceFrame() {
        GLES20.glViewport(0, 0, mWidth, mHeight);
        GLES20.glEnable(GLES20.GL_SCISSOR_TEST);
        GLES20.glClearColor(128.0f, 128.0f, 128.0f, 1.0f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
    }

    private void tryEncoderOutput(long timeOutUs) throws InterruptedException {
        if (!mAsyncHandle.hasSeenError() && !mSawOutputEOS) {
            int retry = 0;
            while (mReviseLatency) {
                if (mAsyncHandle.hasOutputFormatChanged()) {
                    mReviseLatency = false;
                    int actualLatency = mAsyncHandle.getOutputFormat()
                            .getInteger(MediaFormat.KEY_LATENCY, mLatency);
                    if (mLatency < actualLatency) {
                        mLatency = actualLatency;
                        return;
                    }
                } else {
                    if (retry > RETRY_LIMIT) {
                        throw new InterruptedException(
                                "did not receive output format changed for encoder after " +
                                        Q_DEQ_TIMEOUT_US * RETRY_LIMIT + " us");
                    }
                    Thread.sleep(Q_DEQ_TIMEOUT_US / 1000);
                    retry++;
                }
            }
            Pair<Integer, MediaCodec.BufferInfo> element = mAsyncHandle.getOutput();
            if (element != null) {
                dequeueOutput(element.first, element.second);
            }
        }
    }

    void queueEOS() throws InterruptedException {
        if (!mSurfaceMode) {
            super.queueEOS();
        } else {
            if (!mAsyncHandle.hasSeenError() && !mSawInputEOS) {
                mCodec.signalEndOfInputStream();
                mSawInputEOS = true;
                if (ENABLE_LOGS) Log.d(LOG_TAG, "signalled end of stream");
            }
        }
    }

    void doWork(int frameLimit) throws IOException, InterruptedException {
        if (!mSurfaceMode) {
            super.doWork(frameLimit);
        } else {
            while (!mAsyncHandle.hasSeenError() && !mSawInputEOS &&
                    mInputCount < frameLimit) {
                if (mInputCount - mOutputCount > mLatency) {
                    tryEncoderOutput(CodecTestBase.Q_DEQ_TIMEOUT_US);
                }
                mEGLWindowInpSurface.makeCurrent();
                generateSurfaceFrame();
                mEGLWindowInpSurface
                        .setPresentationTime(computePresentationTime(mInputCount) * 1000);
                if (ENABLE_LOGS) Log.d(LOG_TAG, "inputSurface swapBuffers");
                mEGLWindowInpSurface.swapBuffers();
                mInputCount++;
            }
        }
    }

    @SmallTest
    @Test(timeout = PER_TEST_TIMEOUT_SMALL_TEST_MS)
    public void testColorAspects() throws IOException, InterruptedException {
        Assume.assumeTrue("Test introduced with Android 11", sIsAtLeastR);
        if (mSurfaceMode) {
            Assume.assumeTrue("Surface mode tests are limited to devices launching with Android T",
                    FIRST_SDK_IS_AT_LEAST_T && VNDK_IS_AT_LEAST_T);
        }

        if (mUseHighBitDepth) {
            // Check if encoder is capable of supporting HDR profiles.
            // Previous check doesn't verify this as profile isn't set in the format
            Assume.assumeTrue(mCodecName + " doesn't support HDR encoding",
                    CodecTestBase.doesCodecSupportHDRProfile(mCodecName, mMime));

            // Encoder surface mode tests are to be enabled only if an encoder supports
            // COLOR_Format32bitABGR2101010
            if (mSurfaceMode) {
                Assume.assumeTrue(mCodecName + " doesn't support RGBA1010102",
                        hasSupportForColorFormat(mCodecName, mMime, COLOR_Format32bitABGR2101010));
            }
        }

        if (mSurfaceMode) {
            mConfigFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, COLOR_FormatSurface);
        } else {
            String inputTestFile = mInputFile;
            if (mUseHighBitDepth) {
                Assume.assumeTrue(hasSupportForColorFormat(mCodecName, mMime, COLOR_FormatYUVP010));
                mConfigFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, COLOR_FormatYUVP010);
                mBytesPerSample = 2;
                inputTestFile = INPUT_VIDEO_FILE_HBD;
            }
            setUpSource(inputTestFile);
        }

        mOutputBuff = new OutputManager();
        {
            mCodec = MediaCodec.createByCodecName(mCodecName);
            mOutputBuff.reset();
            /* TODO(b/189883530) */
            if (mCodecName.equals("OMX.google.h264.encoder")) {
                Log.d(LOG_TAG, "test skipped due to b/189883530");
                mCodec.release();
                return;
            }
            String log = String.format("format: %s \n codec: %s:: ", mConfigFormat, mCodecName);
            File tmpFile;
            int muxerFormat;
            if (mMime.equals(MediaFormat.MIMETYPE_VIDEO_VP8) ||
                    mMime.equals(MediaFormat.MIMETYPE_VIDEO_VP9)) {
                muxerFormat = MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM;
                tmpFile = File.createTempFile("tmp" + (mUseHighBitDepth ? "10bit" : ""), ".webm");
            } else {
                muxerFormat = MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4;
                tmpFile = File.createTempFile("tmp" + (mUseHighBitDepth ? "10bit" : ""), ".mp4");
            }
            mMuxer = new MediaMuxer(tmpFile.getAbsolutePath(), muxerFormat);
            // When in surface mode, encoder needs to be configured in async mode
            boolean isAsync = mSurfaceMode;
            configureCodec(mConfigFormat, isAsync, true, true);

            if (mSurfaceMode) {
                mInpSurface = mCodec.createInputSurface();
                assertTrue("Surface is not valid", mInpSurface.isValid());
                mEGLWindowInpSurface = new EGLWindowSurface(mInpSurface, mUseHighBitDepth);
                if (mCodec.getInputFormat().containsKey(MediaFormat.KEY_LATENCY)) {
                    mReviseLatency = true;
                    mLatency = mCodec.getInputFormat().getInteger(MediaFormat.KEY_LATENCY);
                }
            }
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

            if (mEGLWindowInpSurface != null) {
                mEGLWindowInpSurface.release();
                mEGLWindowInpSurface = null;
            }
            if (mInpSurface != null) {
                mInpSurface.release();
                mInpSurface = null;
            }

            assertTrue(log + "unexpected error", !mAsyncHandle.hasSeenError());
            assertTrue(log + "no input sent", 0 != mInputCount);
            assertTrue(log + "output received", 0 != mOutputCount);
            // verify if the out fmt contains color aspects as expected
            MediaFormat fmt = mCodec.getOutputFormat();
            validateColorAspects(fmt, mRange, mStandard, mTransferCurve);
            mCodec.stop();
            mCodec.release();

            // verify if the muxed file contains color aspects as expected
            MediaCodecList codecList = new MediaCodecList(MediaCodecList.REGULAR_CODECS);
            String decoder = codecList.findDecoderForFormat(mConfigFormat);
            assertNotNull("Device advertises support for encoding " + mConfigFormat.toString() +
                    " but not decoding it", decoder);
            CodecDecoderTestBase cdtb = new CodecDecoderTestBase(decoder, mMime,
                    tmpFile.getAbsolutePath());
            String parent = tmpFile.getParent();
            if (parent != null) parent += File.separator;
            else parent = "";
            cdtb.validateColorAspects(decoder, parent, tmpFile.getName(), mRange, mStandard,
                    mTransferCurve, false);

            // if color metadata can also be signalled via elementary stream then verify if the
            // elementary stream contains color aspects as expected
            if (mCheckESList.contains(mMime)) {
                cdtb.validateColorAspects(decoder, parent, tmpFile.getName(), mRange, mStandard,
                        mTransferCurve, true);
            }
            tmpFile.delete();
        }
    }
}
