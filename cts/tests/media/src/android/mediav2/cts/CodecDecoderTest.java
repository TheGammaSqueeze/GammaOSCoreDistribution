/*
 * Copyright (C) 2019 The Android Open Source Project
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

import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.util.Log;
import android.view.Surface;

import androidx.test.filters.LargeTest;
import androidx.test.filters.SmallTest;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static android.mediav2.cts.CodecTestBase.SupportClass.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Validate decode functionality of listed decoder components
 *
 * The test aims to test all decoders advertised in MediaCodecList. Hence we are not using
 * MediaCodecList#findDecoderForFormat to create codec. Further, it can so happen that the
 * test clip chosen is not supported by component (codecCapabilities.isFormatSupported()
 * fails), then it is better to replace the clip but not skip testing the component. The idea
 * of these tests are not to cover CDD requirements but to test components and their plugins
 */
@RunWith(Parameterized.class)
public class CodecDecoderTest extends CodecDecoderTestBase {
    private static final String LOG_TAG = CodecDecoderTest.class.getSimpleName();
    private static final float RMS_ERROR_TOLERANCE = 1.05f;        // 5%

    private final String mRefFile;
    private final String mReconfigFile;
    private final float mRmsError;
    private final long mRefCRC;
    private final SupportClass mSupportRequirements;

    public CodecDecoderTest(String decoder, String mime, String testFile, String refFile,
            String reconfigFile, float rmsError, long refCRC, SupportClass supportRequirements) {
        super(decoder, mime, testFile);
        mRefFile = refFile;
        mReconfigFile = reconfigFile;
        mRmsError = rmsError;
        mRefCRC = refCRC;
        mSupportRequirements = supportRequirements;
    }

    static ByteBuffer readAudioReferenceFile(String file) throws IOException {
        File refFile = new File(file);
        ByteBuffer refBuffer;
        try (FileInputStream refStream = new FileInputStream(refFile)) {
            FileChannel fileChannel = refStream.getChannel();
            int length = (int) refFile.length();
            refBuffer = ByteBuffer.allocate(length);
            refBuffer.order(ByteOrder.LITTLE_ENDIAN);
            fileChannel.read(refBuffer);
        }
        return refBuffer;
    }

    private ArrayList<MediaCodec.BufferInfo> createSubFrames(ByteBuffer buffer, int sfCount) {
        int size = (int) mExtractor.getSampleSize();
        if (size < 0) return null;
        mExtractor.readSampleData(buffer, 0);
        long pts = mExtractor.getSampleTime();
        int flags = mExtractor.getSampleFlags();
        if (size < sfCount) sfCount = size;
        ArrayList<MediaCodec.BufferInfo> list = new ArrayList<>();
        int offset = 0;
        for (int i = 0; i < sfCount; i++) {
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            info.offset = offset;
            info.presentationTimeUs = pts;
            info.flags = 0;
            if ((flags & MediaExtractor.SAMPLE_FLAG_SYNC) != 0) {
                info.flags |= MediaCodec.BUFFER_FLAG_KEY_FRAME;
            }
            if ((flags & MediaExtractor.SAMPLE_FLAG_PARTIAL_FRAME) != 0) {
                info.flags |= MediaCodec.BUFFER_FLAG_PARTIAL_FRAME;
            }
            if (i != sfCount - 1) {
                info.size = size / sfCount;
                info.flags |= MediaExtractor.SAMPLE_FLAG_PARTIAL_FRAME;
            } else {
                info.size = size - offset;
            }
            list.add(info);
            offset += info.size;
        }
        return list;
    }

    @Parameterized.Parameters(name = "{index}({0}_{1})")
    public static Collection<Object[]> input() {
        final boolean isEncoder = false;
        final boolean needAudio = true;
        final boolean needVideo = true;
        // mediaType, testClip, referenceClip, reconfigureTestClip, refRmsError, refCRC32,
        // SupportClass
        final List<Object[]> exhaustiveArgsList = new ArrayList<>(Arrays.asList(new Object[][]{
                {MediaFormat.MIMETYPE_AUDIO_MPEG, "bbb_1ch_8kHz_lame_cbr.mp3",
                        "bbb_1ch_8kHz_s16le.raw", "bbb_2ch_44kHz_lame_vbr.mp3", 91.026749f, -1L,
                        CODEC_ALL},
                {MediaFormat.MIMETYPE_AUDIO_MPEG, "bbb_2ch_44kHz_lame_cbr.mp3",
                        "bbb_2ch_44kHz_s16le.raw", "bbb_1ch_16kHz_lame_vbr.mp3", 103.603081f, -1L,
                        CODEC_ALL},
                {MediaFormat.MIMETYPE_AUDIO_AMR_WB, "bbb_1ch_16kHz_16kbps_amrwb.3gp",
                        "bbb_1ch_16kHz_s16le.raw", "bbb_1ch_16kHz_23kbps_amrwb.3gp", 2393.5979f,
                        -1L, CODEC_ALL},
                {MediaFormat.MIMETYPE_AUDIO_AMR_NB, "bbb_1ch_8kHz_10kbps_amrnb.3gp",
                        "bbb_1ch_8kHz_s16le.raw", "bbb_1ch_8kHz_8kbps_amrnb.3gp", -1.0f, -1L,
                        CODEC_ALL},
                {MediaFormat.MIMETYPE_AUDIO_FLAC, "bbb_1ch_16kHz_flac.mka",
                        "bbb_1ch_16kHz_s16le.raw", "bbb_2ch_44kHz_flac.mka", 0.0f, -1L, CODEC_ALL},
                {MediaFormat.MIMETYPE_AUDIO_FLAC, "bbb_2ch_44kHz_flac.mka",
                        "bbb_2ch_44kHz_s16le.raw", "bbb_1ch_16kHz_flac.mka", 0.0f, -1L, CODEC_ALL},
                {MediaFormat.MIMETYPE_AUDIO_RAW, "bbb_1ch_16kHz.wav", "bbb_1ch_16kHz_s16le.raw",
                        "bbb_2ch_44kHz.wav", 0.0f, -1L, CODEC_ALL},
                {MediaFormat.MIMETYPE_AUDIO_RAW, "bbb_2ch_44kHz.wav", "bbb_2ch_44kHz_s16le.raw",
                        "bbb_1ch_16kHz.wav", 0.0f, -1L, CODEC_ALL},
                {MediaFormat.MIMETYPE_AUDIO_G711_ALAW, "bbb_1ch_8kHz_alaw.wav",
                        "bbb_1ch_8kHz_s16le.raw", "bbb_2ch_8kHz_alaw.wav", 23.087402f, -1L,
                        CODEC_ALL},
                {MediaFormat.MIMETYPE_AUDIO_G711_MLAW, "bbb_1ch_8kHz_mulaw.wav",
                        "bbb_1ch_8kHz_s16le.raw", "bbb_2ch_8kHz_mulaw.wav", 24.413954f, -1L,
                        CODEC_ALL},
                {MediaFormat.MIMETYPE_AUDIO_MSGSM, "bbb_1ch_8kHz_gsm.wav",
                        "bbb_1ch_8kHz_s16le.raw", "bbb_1ch_8kHz_gsm.wav", 946.026978f, -1L,
                        CODEC_ALL},
                {MediaFormat.MIMETYPE_AUDIO_VORBIS, "bbb_1ch_16kHz_vorbis.mka",
                        "bbb_1ch_8kHz_s16le.raw", "bbb_2ch_44kHz_vorbis.mka", -1.0f, -1L,
                        CODEC_ALL},
                {MediaFormat.MIMETYPE_AUDIO_OPUS, "bbb_2ch_48kHz_opus.mka",
                        "bbb_2ch_48kHz_s16le.raw", "bbb_1ch_48kHz_opus.mka", -1.0f, -1L, CODEC_ALL},
                {MediaFormat.MIMETYPE_AUDIO_AAC, "bbb_1ch_16kHz_aac.mp4",
                        "bbb_1ch_16kHz_s16le.raw", "bbb_2ch_44kHz_aac.mp4", -1.0f, -1L, CODEC_ALL},
                {MediaFormat.MIMETYPE_VIDEO_MPEG2, "bbb_340x280_768kbps_30fps_mpeg2.mp4", null,
                        "bbb_520x390_1mbps_30fps_mpeg2.mp4", -1.0f, -1L, CODEC_ALL},
                {MediaFormat.MIMETYPE_VIDEO_AVC, "bbb_340x280_768kbps_30fps_avc.mp4", null,
                        "bbb_520x390_1mbps_30fps_avc.mp4", -1.0f, 1746312400L, CODEC_ALL},
                {MediaFormat.MIMETYPE_VIDEO_HEVC, "bbb_520x390_1mbps_30fps_hevc.mp4", null,
                        "bbb_340x280_768kbps_30fps_hevc.mp4", -1.0f, 3061322606L, CODEC_ALL},
                {MediaFormat.MIMETYPE_VIDEO_MPEG4, "bbb_128x96_64kbps_12fps_mpeg4.mp4",
                        null, "bbb_176x144_192kbps_15fps_mpeg4.mp4", -1.0f, -1L, CODEC_ALL},
                {MediaFormat.MIMETYPE_VIDEO_H263, "bbb_176x144_128kbps_15fps_h263.3gp",
                        null, "bbb_176x144_192kbps_10fps_h263.3gp", -1.0f, -1L, CODEC_ALL},
                {MediaFormat.MIMETYPE_VIDEO_VP8, "bbb_340x280_768kbps_30fps_vp8.webm", null,
                        "bbb_520x390_1mbps_30fps_vp8.webm", -1.0f, 2030620796L, CODEC_ALL},
                {MediaFormat.MIMETYPE_VIDEO_VP9, "bbb_340x280_768kbps_30fps_vp9.webm", null,
                        "bbb_520x390_1mbps_30fps_vp9.webm", -1.0f, 4122701060L, CODEC_ALL},
                {MediaFormat.MIMETYPE_VIDEO_AV1, "bbb_340x280_768kbps_30fps_av1.mp4", null,
                        "bbb_520x390_1mbps_30fps_av1.mp4", -1.0f, 400672933L, CODEC_ALL},
        }));
        // P010 support was added in Android T, hence limit the following tests to Android T and
        // above
        if (IS_AT_LEAST_T) {
            exhaustiveArgsList.addAll(Arrays.asList(new Object[][]{
                    {MediaFormat.MIMETYPE_VIDEO_AVC, "cosmat_340x280_24fps_crf22_avc_10bit.mkv",
                            null, "cosmat_520x390_24fps_crf22_avc_10bit.mkv", -1.0f, 1462636611L,
                            CODEC_OPTIONAL},
                    {MediaFormat.MIMETYPE_VIDEO_HEVC, "cosmat_340x280_24fps_crf22_hevc_10bit.mkv",
                            null, "cosmat_520x390_24fps_crf22_hevc_10bit.mkv", -1.0f, 2611796790L,
                            CODEC_OPTIONAL},
                    {MediaFormat.MIMETYPE_VIDEO_VP9, "cosmat_340x280_24fps_crf22_vp9_10bit.mkv",
                            null, "cosmat_520x390_24fps_crf22_vp9_10bit.mkv", -1.0f, 2419292938L,
                            CODEC_OPTIONAL},
                    {MediaFormat.MIMETYPE_VIDEO_AV1, "cosmat_340x280_24fps_512kbps_av1_10bit.mkv",
                            null, "cosmat_520x390_24fps_768kbps_av1_10bit.mkv", -1.0f, 1021109556L,
                            CODEC_ALL},
                    {MediaFormat.MIMETYPE_VIDEO_AVC, "cosmat_340x280_24fps_crf22_avc_10bit.mkv",
                            null, "bbb_520x390_1mbps_30fps_avc.mp4", -1.0f, 1462636611L,
                            CODEC_OPTIONAL},
                    {MediaFormat.MIMETYPE_VIDEO_HEVC, "cosmat_340x280_24fps_crf22_hevc_10bit.mkv",
                            null, "bbb_520x390_1mbps_30fps_hevc.mp4", -1.0f, 2611796790L,
                            CODEC_OPTIONAL},
                    {MediaFormat.MIMETYPE_VIDEO_VP9, "cosmat_340x280_24fps_crf22_vp9_10bit.mkv",
                            null, "bbb_520x390_1mbps_30fps_vp9.webm", -1.0f, 2419292938L,
                            CODEC_OPTIONAL},
                    {MediaFormat.MIMETYPE_VIDEO_AV1, "cosmat_340x280_24fps_512kbps_av1_10bit.mkv",
                            null, "bbb_520x390_1mbps_30fps_av1.mp4", -1.0f, 1021109556L,
                            CODEC_ALL},
                    {MediaFormat.MIMETYPE_VIDEO_AVC, "cosmat_520x390_24fps_crf22_avc_10bit.mkv",
                            null, "bbb_340x280_768kbps_30fps_avc.mp4", -1.0f, 2245243696L,
                            CODEC_OPTIONAL},
                    {MediaFormat.MIMETYPE_VIDEO_HEVC, "cosmat_520x390_24fps_crf22_hevc_10bit.mkv"
                            , null, "bbb_340x280_768kbps_30fps_hevc.mp4", -1.0f, 2486118612L,
                            CODEC_OPTIONAL},
                    {MediaFormat.MIMETYPE_VIDEO_VP9, "cosmat_520x390_24fps_crf22_vp9_10bit.mkv",
                            null, "bbb_340x280_768kbps_30fps_vp9.webm", -1.0f, 3677982654L,
                            CODEC_OPTIONAL},
                    {MediaFormat.MIMETYPE_VIDEO_AV1, "cosmat_520x390_24fps_768kbps_av1_10bit.mkv",
                            null, "bbb_340x280_768kbps_30fps_av1.mp4", -1.0f, 1139081423L,
                            CODEC_ALL},
            }));
        }
        return prepareParamList(exhaustiveArgsList, isEncoder, needAudio, needVideo, true);
    }

    private native boolean nativeTestSimpleDecode(String decoder, Surface surface, String mime,
            String testFile, String refFile, int colorFormat, float rmsError, long checksum);

    static void verify(OutputManager outBuff, String refFile, float rmsError, int audioFormat,
            long refCRC) throws IOException {
        if (rmsError >= 0) {
            int bytesPerSample = AudioFormat.getBytesPerSample(audioFormat);
            ByteBuffer bb = readAudioReferenceFile(mInpPrefix + refFile);
            bb.position(0);
            int bufferSize = bb.limit();
            assertEquals (0, bufferSize % bytesPerSample);
            Object refObject = null;
            int refObjectLen = bufferSize / bytesPerSample;
            switch (audioFormat) {
                case AudioFormat.ENCODING_PCM_8BIT:
                    refObject = new byte[refObjectLen];
                    bb.get((byte[]) refObject);
                    break;
                case AudioFormat.ENCODING_PCM_16BIT:
                    refObject = new short[refObjectLen];
                    bb.asShortBuffer().get((short[]) refObject);
                    break;
                case AudioFormat.ENCODING_PCM_24BIT_PACKED:
                    refObject = new int[refObjectLen];
                    int[] refArray = (int[]) refObject;
                    for (int i = 0, j = 0; i < bufferSize; i += 3, j++) {
                        int byte1 = (bb.get() & 0xff);
                        int byte2 = (bb.get() & 0xff);
                        int byte3 = (bb.get() & 0xff);
                        refArray[j] =  byte1 | (byte2 << 8) | (byte3 << 16);
                    }
                    break;
                case AudioFormat.ENCODING_PCM_32BIT:
                    refObject = new int[refObjectLen];
                    bb.asIntBuffer().get((int[]) refObject);
                    break;
                case AudioFormat.ENCODING_PCM_FLOAT:
                    refObject = new float[refObjectLen];
                    bb.asFloatBuffer().get((float[]) refObject);
                    break;
                default:
                    fail("unrecognized audio encoding type " + audioFormat);
            }
            float currError = outBuff.getRmsError(refObject, audioFormat);
            float errMargin = rmsError * RMS_ERROR_TOLERANCE;
            assertTrue(String.format("%s rms error too high ref/exp/got %f/%f/%f", refFile,
                    rmsError, errMargin, currError), currError <= errMargin);
        } else if (refCRC >= 0) {
            assertEquals("checksum mismatch", refCRC, outBuff.getCheckSumImage());
        }
    }

    @Before
    public void setUp() throws IOException {
        MediaFormat format = setUpSource(mTestFile);
        mExtractor.release();
        ArrayList<MediaFormat> formatList = new ArrayList<>();
        formatList.add(format);
        checkFormatSupport(mCodecName, mMime, false, formatList, null, mSupportRequirements);
    }

    /**
     * Tests decoder for combinations:
     * 1. Codec Sync Mode, Signal Eos with Last frame
     * 2. Codec Sync Mode, Signal Eos Separately
     * 3. Codec Async Mode, Signal Eos with Last frame
     * 4. Codec Async Mode, Signal Eos Separately
     * In all these scenarios, Timestamp ordering is verified, For audio the Rms of output has to be
     * within the allowed tolerance. The output has to be consistent (not flaky) in all runs.
     */
    @LargeTest
    @Test(timeout = PER_TEST_TIMEOUT_LARGE_TEST_MS)
    public void testSimpleDecode() throws IOException, InterruptedException {
        MediaFormat format = setUpSource(mTestFile);
        boolean[] boolStates = {true, false};
        mSaveToMem = true;
        OutputManager ref = new OutputManager();
        OutputManager test = new OutputManager();
        {
            mCodec = MediaCodec.createByCodecName(mCodecName);
            assertTrue("codec name act/got: " + mCodec.getName() + '/' + mCodecName,
                    mCodec.getName().equals(mCodecName));
            assertTrue("error! codec canonical name is null",
                    mCodec.getCanonicalName() != null && !mCodec.getCanonicalName().isEmpty());
            validateMetrics(mCodecName);
            int loopCounter = 0;
            for (boolean eosType : boolStates) {
                for (boolean isAsync : boolStates) {
                    boolean validateFormat = true;
                    String log = String.format("codec: %s, file: %s, mode: %s, eos type: %s:: ",
                            mCodecName, mTestFile, (isAsync ? "async" : "sync"),
                            (eosType ? "eos with last frame" : "eos separate"));
                    mOutputBuff = loopCounter == 0 ? ref : test;
                    mOutputBuff.reset();
                    mExtractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
                    configureCodec(format, isAsync, eosType, false);
                    MediaFormat defFormat = mCodec.getOutputFormat();
                    if (isFormatSimilar(format, defFormat)) {
                        if (ENABLE_LOGS) {
                            Log.d("Input format is same as default for format for %s", mCodecName);
                        }
                        validateFormat = false;
                    }
                    mCodec.start();
                    doWork(Integer.MAX_VALUE);
                    queueEOS();
                    waitForAllOutputs();
                    validateMetrics(mCodecName, format);
                    /* TODO(b/147348711) */
                    if (false) mCodec.stop();
                    else mCodec.reset();
                    assertTrue(log + " unexpected error", !mAsyncHandle.hasSeenError());
                    assertTrue(log + "no input sent", 0 != mInputCount);
                    assertTrue(log + "output received", 0 != mOutputCount);
                    if (loopCounter != 0) {
                        assertTrue(log + "decoder output is flaky", ref.equals(test));
                    } else {
                        if (mIsAudio) {
                            assertTrue(log + " pts is not strictly increasing",
                                    ref.isPtsStrictlyIncreasing(mPrevOutputPts));
                        } else {
                            // TODO: Timestamps for deinterlaced content are under review.
                            // (E.g. can decoders produce multiple progressive frames?)
                            // For now, do not verify timestamps.
                            if (!mIsInterlaced) {
                                    assertTrue(
                                        log +
                                        " input pts list and output pts list are not identical",
                                        ref.isOutPtsListIdenticalToInpPtsList(false));
                            }
                        }
                    }
                    if (validateFormat) {
                        assertTrue(log + "not received format change",
                                mIsCodecInAsyncMode ? mAsyncHandle.hasOutputFormatChanged() :
                                        mSignalledOutFormatChanged);
                        assertTrue(log + "configured format and output format are not similar",
                                isFormatSimilar(format,
                                        mIsCodecInAsyncMode ? mAsyncHandle.getOutputFormat() :
                                                mOutFormat));
                    }
                    loopCounter++;
                }
            }
            mCodec.release();
            mExtractor.release();
            int colorFormat = mIsAudio ? 0 : format.getInteger(MediaFormat.KEY_COLOR_FORMAT);
            assertTrue(nativeTestSimpleDecode(mCodecName, null, mMime, mInpPrefix + mTestFile,
                    mInpPrefix + mRefFile, colorFormat, mRmsError, ref.getCheckSumBuffer()));
            if (mSaveToMem) {
                int audioEncoding = mIsAudio ? format.getInteger(MediaFormat.KEY_PCM_ENCODING,
                        AudioFormat.ENCODING_PCM_16BIT) : AudioFormat.ENCODING_INVALID;
                Assume.assumeFalse("skip checksum due to tone mapping", mSkipChecksumVerification);
                verify(mOutputBuff, mRefFile, mRmsError, audioEncoding, mRefCRC);
            }
        }
    }

    /**
     * Tests flush when codec is in sync and async mode. In these scenarios, Timestamp
     * ordering is verified. The output has to be consistent (not flaky) in all runs
     */
    @Ignore("TODO(b/147576107)")
    @LargeTest
    @Test(timeout = PER_TEST_TIMEOUT_LARGE_TEST_MS)
    public void testFlush() throws IOException, InterruptedException {
        MediaFormat format = setUpSource(mTestFile);
        mExtractor.release();
        mCsdBuffers.clear();
        for (int i = 0; ; i++) {
            String csdKey = "csd-" + i;
            if (format.containsKey(csdKey)) {
                mCsdBuffers.add(format.getByteBuffer(csdKey));
            } else break;
        }
        final long pts = 500000;
        final int mode = MediaExtractor.SEEK_TO_CLOSEST_SYNC;
        boolean[] boolStates = {true, false};
        OutputManager test = new OutputManager();
        {
            decodeToMemory(mTestFile, mCodecName, pts, mode, Integer.MAX_VALUE);
            OutputManager ref = mOutputBuff;
            if (mIsAudio) {
                assertTrue("reference output pts is not strictly increasing",
                        ref.isPtsStrictlyIncreasing(mPrevOutputPts));
            } else {
                // TODO: Timestamps for deinterlaced content are under review. (E.g. can decoders
                // produce multiple progressive frames?) For now, do not verify timestamps.
                if (!mIsInterlaced) {
                    assertTrue("input pts list and output pts list are not identical",
                            ref.isOutPtsListIdenticalToInpPtsList(false));
                }
            }
            mOutputBuff = test;
            setUpSource(mTestFile);
            mCodec = MediaCodec.createByCodecName(mCodecName);
            for (boolean isAsync : boolStates) {
                String log = String.format("decoder: %s, input file: %s, mode: %s:: ", mCodecName,
                        mTestFile, (isAsync ? "async" : "sync"));
                mExtractor.seekTo(0, mode);
                configureCodec(format, isAsync, true, false);
                MediaFormat defFormat = mCodec.getOutputFormat();
                boolean validateFormat = true;
                if (isFormatSimilar(format, defFormat)) {
                    if (ENABLE_LOGS) {
                        Log.d("Input format is same as default for format for %s", mCodecName);
                    }
                    validateFormat = false;
                }
                mCodec.start();

                /* test flush in running state before queuing input */
                flushCodec();
                if (mIsCodecInAsyncMode) mCodec.start();
                queueCodecConfig(); /* flushed codec too soon after start, resubmit csd */

                doWork(1);
                flushCodec();
                if (mIsCodecInAsyncMode) mCodec.start();
                queueCodecConfig(); /* flushed codec too soon after start, resubmit csd */

                mExtractor.seekTo(0, mode);
                test.reset();
                doWork(23);
                if (!mIsInterlaced) {
                    assertTrue(log + " pts is not strictly increasing",
                                test.isPtsStrictlyIncreasing(mPrevOutputPts));
                }

                boolean checkMetrics = (mOutputCount != 0);

                /* test flush in running state */
                flushCodec();
                if (checkMetrics) validateMetrics(mCodecName, format);
                if (mIsCodecInAsyncMode) mCodec.start();
                mSaveToMem = true;
                test.reset();
                mExtractor.seekTo(pts, mode);
                doWork(Integer.MAX_VALUE);
                queueEOS();
                waitForAllOutputs();
                assertTrue(log + " unexpected error", !mAsyncHandle.hasSeenError());
                assertTrue(log + "no input sent", 0 != mInputCount);
                assertTrue(log + "output received", 0 != mOutputCount);
                assertTrue(log + "decoder output is flaky", ref.equals(test));

                /* test flush in eos state */
                flushCodec();
                if (mIsCodecInAsyncMode) mCodec.start();
                test.reset();
                mExtractor.seekTo(pts, mode);
                doWork(Integer.MAX_VALUE);
                queueEOS();
                waitForAllOutputs();
                /* TODO(b/147348711) */
                if (false) mCodec.stop();
                else mCodec.reset();
                assertTrue(log + " unexpected error", !mAsyncHandle.hasSeenError());
                assertTrue(log + "no input sent", 0 != mInputCount);
                assertTrue(log + "output received", 0 != mOutputCount);
                assertTrue(log + "decoder output is flaky", ref.equals(test));
                if (validateFormat) {
                    assertTrue(log + "not received format change",
                            mIsCodecInAsyncMode ? mAsyncHandle.hasOutputFormatChanged() :
                                    mSignalledOutFormatChanged);
                    assertTrue(log + "configured format and output format are not similar",
                            isFormatSimilar(format,
                                    mIsCodecInAsyncMode ? mAsyncHandle.getOutputFormat() :
                                            mOutFormat));
                }
                mSaveToMem = false;
            }
            mCodec.release();
            mExtractor.release();
        }
    }

    private native boolean nativeTestFlush(String decoder, Surface surface, String mime,
            String testFile, int colorFormat);

    @Ignore("TODO(b/147576107)")
    @LargeTest
    @Test(timeout = PER_TEST_TIMEOUT_LARGE_TEST_MS)
    public void testFlushNative() throws IOException {
        int colorFormat = 0;
        if (!mIsAudio) {
            MediaFormat format = setUpSource(mTestFile);
            mExtractor.release();
            colorFormat = format.getInteger(MediaFormat.KEY_COLOR_FORMAT);
        }
        assertTrue(nativeTestFlush(mCodecName, null, mMime, mInpPrefix + mTestFile, colorFormat));
    }

    /**
     * Tests reconfigure when codec is in sync and async mode. In these scenarios, Timestamp
     * ordering is verified. The output has to be consistent (not flaky) in all runs
     */
    @LargeTest
    @Test(timeout = PER_TEST_TIMEOUT_LARGE_TEST_MS)
    public void testReconfigure() throws IOException, InterruptedException {
        Assume.assumeTrue("Test needs Android 11", IS_AT_LEAST_R);

        MediaFormat format = setUpSource(mTestFile);
        mExtractor.release();
        MediaFormat newFormat = setUpSource(mReconfigFile);
        mExtractor.release();
        ArrayList<MediaFormat> formatList = new ArrayList<>();
        formatList.add(newFormat);
        checkFormatSupport(mCodecName, mMime, false, formatList, null, mSupportRequirements);
        final long startTs = 0;
        final long seekTs = 500000;
        final int mode = MediaExtractor.SEEK_TO_CLOSEST_SYNC;
        boolean[] boolStates = {true, false};
        OutputManager test = new OutputManager();
        {
            decodeToMemory(mTestFile, mCodecName, startTs, mode, Integer.MAX_VALUE);
            OutputManager ref = mOutputBuff;
            decodeToMemory(mReconfigFile, mCodecName, seekTs, mode, Integer.MAX_VALUE);
            OutputManager configRef = mOutputBuff;
            if (mIsAudio) {
                assertTrue("reference output pts is not strictly increasing",
                        ref.isPtsStrictlyIncreasing(mPrevOutputPts));
                assertTrue("config reference output pts is not strictly increasing",
                        configRef.isPtsStrictlyIncreasing(mPrevOutputPts));
            } else {
                // TODO: Timestamps for deinterlaced content are under review. (E.g. can decoders
                // produce multiple progressive frames?) For now, do not verify timestamps.
                if (!mIsInterlaced) {
                    assertTrue("input pts list and reference pts list are not identical",
                            ref.isOutPtsListIdenticalToInpPtsList(false));
                    assertTrue("input pts list and reconfig ref output pts list are not identical",
                            ref.isOutPtsListIdenticalToInpPtsList(false));
                }
            }
            mOutputBuff = test;
            mCodec = MediaCodec.createByCodecName(mCodecName);
            for (boolean isAsync : boolStates) {
                setUpSource(mTestFile);
                String log = String.format("decoder: %s, input file: %s, mode: %s:: ", mCodecName,
                        mTestFile, (isAsync ? "async" : "sync"));
                mExtractor.seekTo(startTs, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
                configureCodec(format, isAsync, true, false);
                MediaFormat defFormat = mCodec.getOutputFormat();
                boolean validateFormat = true;
                if (isFormatSimilar(format, defFormat)) {
                    if (ENABLE_LOGS) {
                        Log.d("Input format is same as default for format for %s", mCodecName);
                    }
                    validateFormat = false;
                }

                /* test reconfigure in stopped state */
                reConfigureCodec(format, !isAsync, false, false);
                mCodec.start();

                /* test reconfigure in running state before queuing input */
                reConfigureCodec(format, !isAsync, false, false);
                mCodec.start();
                doWork(23);

                if (mOutputCount != 0) {
                    if (validateFormat) {
                        assertTrue(log + "not received format change",
                                mIsCodecInAsyncMode ? mAsyncHandle.hasOutputFormatChanged() :
                                        mSignalledOutFormatChanged);
                        assertTrue(log + "configured format and output format are not similar",
                                isFormatSimilar(format,
                                        mIsCodecInAsyncMode ? mAsyncHandle.getOutputFormat() :
                                                mOutFormat));
                    }
                    validateMetrics(mCodecName, format);
                }

                /* test reconfigure codec in running state */
                reConfigureCodec(format, isAsync, true, false);
                mCodec.start();
                mSaveToMem = true;
                test.reset();
                mExtractor.seekTo(startTs, mode);
                doWork(Integer.MAX_VALUE);
                queueEOS();
                waitForAllOutputs();
                /* TODO(b/147348711) */
                if (false) mCodec.stop();
                else mCodec.reset();
                assertTrue(log + " unexpected error", !mAsyncHandle.hasSeenError());
                assertTrue(log + "no input sent", 0 != mInputCount);
                assertTrue(log + "output received", 0 != mOutputCount);
                assertTrue(log + "decoder output is flaky", ref.equals(test));
                if (validateFormat) {
                    assertTrue(log + "not received format change",
                            mIsCodecInAsyncMode ? mAsyncHandle.hasOutputFormatChanged() :
                                    mSignalledOutFormatChanged);
                    assertTrue(log + "configured format and output format are not similar",
                            isFormatSimilar(format,
                                    mIsCodecInAsyncMode ? mAsyncHandle.getOutputFormat() :
                                            mOutFormat));
                }

                /* test reconfigure codec at eos state */
                reConfigureCodec(format, !isAsync, false, false);
                mCodec.start();
                test.reset();
                mExtractor.seekTo(startTs, mode);
                doWork(Integer.MAX_VALUE);
                queueEOS();
                waitForAllOutputs();
                /* TODO(b/147348711) */
                if (false) mCodec.stop();
                else mCodec.reset();
                assertTrue(log + " unexpected error", !mAsyncHandle.hasSeenError());
                assertTrue(log + "no input sent", 0 != mInputCount);
                assertTrue(log + "output received", 0 != mOutputCount);
                assertTrue(log + "decoder output is flaky", ref.equals(test));
                if (validateFormat) {
                    assertTrue(log + "not received format change",
                            mIsCodecInAsyncMode ? mAsyncHandle.hasOutputFormatChanged() :
                                    mSignalledOutFormatChanged);
                    assertTrue(log + "configured format and output format are not similar",
                            isFormatSimilar(format,
                                    mIsCodecInAsyncMode ? mAsyncHandle.getOutputFormat() :
                                            mOutFormat));
                }
                mExtractor.release();

                /* test reconfigure codec for new file */
                setUpSource(mReconfigFile);
                log = String.format("decoder: %s, input file: %s, mode: %s:: ", mCodecName,
                        mReconfigFile, (isAsync ? "async" : "sync"));
                reConfigureCodec(newFormat, isAsync, false, false);
                if (isFormatSimilar(newFormat, defFormat)) {
                    if (ENABLE_LOGS) {
                        Log.d("Input format is same as default for format for %s", mCodecName);
                    }
                    validateFormat = false;
                }
                mCodec.start();
                test.reset();
                mExtractor.seekTo(seekTs, mode);
                doWork(Integer.MAX_VALUE);
                queueEOS();
                waitForAllOutputs();
                validateMetrics(mCodecName, newFormat);
                /* TODO(b/147348711) */
                if (false) mCodec.stop();
                else mCodec.reset();
                assertTrue(log + " unexpected error", !mAsyncHandle.hasSeenError());
                assertTrue(log + "no input sent", 0 != mInputCount);
                assertTrue(log + "output received", 0 != mOutputCount);
                assertTrue(log + "decoder output is flaky", configRef.equals(test));
                if (validateFormat) {
                    assertTrue(log + "not received format change",
                            mIsCodecInAsyncMode ? mAsyncHandle.hasOutputFormatChanged() :
                                    mSignalledOutFormatChanged);
                    assertTrue(log + "configured format and output format are not similar",
                            isFormatSimilar(newFormat,
                                    mIsCodecInAsyncMode ? mAsyncHandle.getOutputFormat() :
                                            mOutFormat));
                }
                mSaveToMem = false;
                mExtractor.release();
            }
            mCodec.release();
        }
    }

    /**
     * Tests decoder for only EOS frame
     */
    @SmallTest
    @Test(timeout = PER_TEST_TIMEOUT_SMALL_TEST_MS)
    public void testOnlyEos() throws IOException, InterruptedException {
        MediaFormat format = setUpSource(mTestFile);
        boolean[] boolStates = {true, false};
        OutputManager ref = new OutputManager();
        OutputManager test = new OutputManager();
        mSaveToMem = true;
        {
            mCodec = MediaCodec.createByCodecName(mCodecName);
            int loopCounter = 0;
            for (boolean isAsync : boolStates) {
                String log = String.format("decoder: %s, input file: %s, mode: %s:: ", mCodecName,
                        mTestFile, (isAsync ? "async" : "sync"));
                configureCodec(format, isAsync, false, false);
                mOutputBuff = loopCounter == 0 ? ref : test;
                mOutputBuff.reset();
                mCodec.start();
                queueEOS();
                waitForAllOutputs();
                mCodec.stop();
                assertTrue(log + " unexpected error", !mAsyncHandle.hasSeenError());
                if (loopCounter != 0) {
                    assertTrue(log + "decoder output is flaky", ref.equals(test));
                } else {
                    if (mIsAudio) {
                        assertTrue(log + " pts is not strictly increasing",
                                ref.isPtsStrictlyIncreasing(mPrevOutputPts));
                    } else {
                        // TODO: Timestamps for deinterlaced content are under review.
                        // (E.g. can decoders produce multiple progressive frames?)
                        // For now, do not verify timestamps.
                        if (!mIsInterlaced) {
                            assertTrue(
                                    log + " input pts list and output pts list are not identical",
                                    ref.isOutPtsListIdenticalToInpPtsList(false));
                        }
                    }
                }
                loopCounter++;
            }
            mCodec.release();
        }
        mExtractor.release();
    }

    private native boolean nativeTestOnlyEos(String decoder, String mime, String testFile,
            int colorFormat);

    @SmallTest
    @Test
    public void testOnlyEosNative() throws IOException {
        int colorFormat = 0;
        if (!mIsAudio) {
            MediaFormat format = setUpSource(mTestFile);
            mExtractor.release();
            colorFormat = format.getInteger(MediaFormat.KEY_COLOR_FORMAT);
        }
        assertTrue(nativeTestOnlyEos(mCodecName, mMime, mInpPrefix + mTestFile, colorFormat));
    }

    /**
     * Test Decoder by Queuing CSD separately
     */
    @LargeTest
    @Test(timeout = PER_TEST_TIMEOUT_LARGE_TEST_MS)
    public void testSimpleDecodeQueueCSD() throws IOException, InterruptedException {
        MediaFormat format = setUpSource(mTestFile);
        if (!hasCSD(format)) {
            mExtractor.release();
            return;
        }
        ArrayList<MediaFormat> formats = new ArrayList<>();
        formats.add(format);
        formats.add(new MediaFormat(format));
        for (int i = 0; ; i++) {
            String csdKey = "csd-" + i;
            if (format.containsKey(csdKey)) {
                mCsdBuffers.add(format.getByteBuffer(csdKey).duplicate());
                format.removeKey(csdKey);
            } else break;
        }
        boolean[] boolStates = {true, false};
        mSaveToMem = true;
        OutputManager ref = new OutputManager();
        OutputManager test = new OutputManager();
        {
            mCodec = MediaCodec.createByCodecName(mCodecName);
            int loopCounter = 0;
            for (int i = 0; i < formats.size(); i++) {
                MediaFormat fmt = formats.get(i);
                for (boolean eosMode : boolStates) {
                    for (boolean isAsync : boolStates) {
                        boolean validateFormat = true;
                        String log = String.format("codec: %s, file: %s, mode: %s, eos type: %s:: ",
                                mCodecName, mTestFile, (isAsync ? "async" : "sync"),
                                (eosMode ? "eos with last frame" : "eos separate"));
                        mOutputBuff = loopCounter == 0 ? ref : test;
                        mOutputBuff.reset();
                        mExtractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
                        configureCodec(fmt, isAsync, eosMode, false);
                        MediaFormat defFormat = mCodec.getOutputFormat();
                        if (isFormatSimilar(defFormat, format)) {
                            if (ENABLE_LOGS) {
                                Log.d("Input format is same as default for format for %s",
                                        mCodecName);
                            }
                            validateFormat = false;
                        }
                        mCodec.start();
                        if (i == 0) queueCodecConfig();
                        doWork(Integer.MAX_VALUE);
                        queueEOS();
                        waitForAllOutputs();
                        validateMetrics(mCodecName);
                        /* TODO(b/147348711) */
                        if (false) mCodec.stop();
                        else mCodec.reset();
                        assertTrue(log + " unexpected error", !mAsyncHandle.hasSeenError());
                        assertTrue(log + "no input sent", 0 != mInputCount);
                        assertTrue(log + "output received", 0 != mOutputCount);
                        if (loopCounter != 0) {
                            assertTrue(log + "decoder output is flaky", ref.equals(test));
                        } else {
                            if (mIsAudio) {
                                assertTrue(log + " pts is not strictly increasing",
                                        ref.isPtsStrictlyIncreasing(mPrevOutputPts));
                            } else {
                                // TODO: Timestamps for deinterlaced content are under review.
                                // (E.g. can decoders produce multiple progressive frames?)
                                // For now, do not verify timestamps.
                                if (!mIsInterlaced) {
                                    assertTrue(
                                           log +
                                           " input pts list and output pts list are not identical",
                                           ref.isOutPtsListIdenticalToInpPtsList(false));
                                }
                            }
                        }
                        if (validateFormat) {
                            assertTrue(log + "not received format change",
                                    mIsCodecInAsyncMode ? mAsyncHandle.hasOutputFormatChanged() :
                                            mSignalledOutFormatChanged);
                            assertTrue(log + "configured format and output format are not similar",
                                    isFormatSimilar(format,
                                            mIsCodecInAsyncMode ? mAsyncHandle.getOutputFormat() :
                                                    mOutFormat));
                        }
                        loopCounter++;
                    }
                }
            }
            mCodec.release();
        }
        mExtractor.release();
    }

    private native boolean nativeTestSimpleDecodeQueueCSD(String decoder, String mime,
            String testFile, int colorFormat);

    @LargeTest
    @Test(timeout = PER_TEST_TIMEOUT_LARGE_TEST_MS)
    public void testSimpleDecodeQueueCSDNative() throws IOException {
        MediaFormat format = setUpSource(mTestFile);
        if (!hasCSD(format)) {
            mExtractor.release();
            return;
        }
        mExtractor.release();
        int colorFormat = mIsAudio ? 0 : format.getInteger(MediaFormat.KEY_COLOR_FORMAT);
        assertTrue(nativeTestSimpleDecodeQueueCSD(mCodecName, mMime, mInpPrefix + mTestFile,
                colorFormat));
    }

    /**
     * Test decoder for partial frame
     */
    @LargeTest
    @Test(timeout = PER_TEST_TIMEOUT_LARGE_TEST_MS)
    public void testDecodePartialFrame() throws IOException, InterruptedException {
        Assume.assumeTrue(isFeatureSupported(mCodecName, mMime,
                MediaCodecInfo.CodecCapabilities.FEATURE_PartialFrame));
        MediaFormat format = setUpSource(mTestFile);
        boolean[] boolStates = {true, false};
        int frameLimit = 10;
        ByteBuffer buffer = ByteBuffer.allocate(4 * 1024 * 1024);
        OutputManager test = new OutputManager();
        {
            decodeToMemory(mTestFile, mCodecName, 0, MediaExtractor.SEEK_TO_CLOSEST_SYNC,
                    frameLimit);
            mCodec = MediaCodec.createByCodecName(mCodecName);
            OutputManager ref = mOutputBuff;
            if (mIsAudio) {
                assertTrue("reference output pts is not strictly increasing",
                        ref.isPtsStrictlyIncreasing(mPrevOutputPts));
            } else {
                // TODO: Timestamps for deinterlaced content are under review. (E.g. can decoders
                // produce multiple progressive frames?) For now, do not verify timestamps.
                if (!mIsInterlaced) {
                    assertTrue("input pts list and output pts list are not identical",
                            ref.isOutPtsListIdenticalToInpPtsList(false));
                }
            }
            mSaveToMem = true;
            mOutputBuff = test;
            for (boolean isAsync : boolStates) {
                String log = String.format("decoder: %s, input file: %s, mode: %s:: ", mCodecName,
                        mTestFile, (isAsync ? "async" : "sync"));
                mExtractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
                test.reset();
                configureCodec(format, isAsync, true, false);
                mCodec.start();
                doWork(frameLimit - 1);
                ArrayList<MediaCodec.BufferInfo> list = createSubFrames(buffer, 4);
                assertTrue("no sub frames in list received for " + mTestFile,
                        list != null && list.size() > 0);
                doWork(buffer, list);
                queueEOS();
                waitForAllOutputs();
                /* TODO(b/147348711) */
                if (false) mCodec.stop();
                else mCodec.reset();
                assertTrue(log + " unexpected error", !mAsyncHandle.hasSeenError());
                assertTrue(log + "no input sent", 0 != mInputCount);
                assertTrue(log + "output received", 0 != mOutputCount);
                assertTrue(log + "decoder output is not consistent with ref", ref.equals(test));
            }
            mCodec.release();
        }
        mExtractor.release();
    }
}
