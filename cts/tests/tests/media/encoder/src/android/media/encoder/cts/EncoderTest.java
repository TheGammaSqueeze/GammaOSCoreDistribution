/*
 * Copyright (C) 2012 The Android Open Source Project
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

package android.media.encoder.cts;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.media.cts.Preconditions;
import android.media.cts.TestArgs;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.RequiresDevice;
import android.util.Log;

import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.MediaUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@SmallTest
@RequiresDevice
@AppModeFull(reason = "Instant apps cannot access the SD card")
@RunWith(Parameterized.class)
public class EncoderTest {
    private static final String TAG = "EncoderTest";
    private static final boolean VERBOSE = false;

    static final String mInpPrefix = WorkDir.getMediaDirString();
    private static final int kNumInputBytes = 512 * 1024;
    private static final long kTimeoutUs = 100;

    // not all combinations are valid
    private static final int MODE_SILENT = 0;
    private static final int MODE_RANDOM = 1;
    private static final int MODE_RESOURCE = 2;
    private static final int MODE_QUIET = 4;
    private static final int MODE_SILENTLEAD = 8;

    /*
     * Set this to true to save the encoding results to /data/local/tmp
     * You will need to make /data/local/tmp writeable, run "setenforce 0",
     * and remove files left from a previous run.
     */
    private static boolean sSaveResults = false;
    static final Map<String, String> mDefaultEncoders = new HashMap<>();

    private final String mEncoderName;
    private final String mMime;
    private final int[] mProfiles;
    private final int[] mBitrates;
    private final int[] mSampleRates;
    private final int[] mChannelCounts;
    private ArrayList<MediaFormat> mFormats;

    static boolean isDefaultCodec(String codecName, String mime)
            throws IOException {
        if (mDefaultEncoders.containsKey(mime)) {
            return mDefaultEncoders.get(mime).equalsIgnoreCase(codecName);
        }
        MediaCodec codec = MediaCodec.createEncoderByType(mime);
        boolean isDefault = codec.getName().equalsIgnoreCase(codecName);
        mDefaultEncoders.put(mime, codec.getName());
        codec.release();
        return isDefault;
    }

    static private List<Object[]> prepareParamList(List<Object[]> exhaustiveArgsList) {
        final List<Object[]> argsList = new ArrayList<>();
        int argLength = exhaustiveArgsList.get(0).length;
        for (Object[] arg : exhaustiveArgsList) {
            String mediaType = (String)arg[0];
            if (TestArgs.shouldSkipMediaType(mediaType)) {
                continue;
            }
            String[] componentNames = MediaUtils.getEncoderNamesForMime(mediaType);
            for (String name : componentNames) {
                if (TestArgs.shouldSkipCodec(name)) {
                    continue;
                }
                Object[] testArgs = new Object[argLength + 1];
                testArgs[0] = name;
                System.arraycopy(arg, 0, testArgs, 1, argLength);
                argsList.add(testArgs);
            }
        }
        return argsList;
    }

    @Parameterized.Parameters(name = "{index}({0}_{1})")
    public static Collection<Object[]> input() {
        final List<Object[]> exhaustiveArgsList = Arrays.asList(new Object[][]{
                // Audio - CodecMime, arrays of profiles, bit-rates, sample rates, channel counts
                {MediaFormat.MIMETYPE_AUDIO_AAC, new int[]{2, 5, 39}, new int[]{64000, 128000},
                        new int[]{8000, 11025, 22050, 44100, 48000}, new int[]{1, 2}},
                {MediaFormat.MIMETYPE_AUDIO_OPUS, new int[]{-1}, new int[]{8000, 12000, 16000,
                        24000, 48000}, new int[]{16000}, new int[]{1, 2}},
                {MediaFormat.MIMETYPE_AUDIO_AMR_NB, new int[]{-1}, new int[]{4750, 5150, 5900, 6700
                        , 7400, 7950, 10200, 12200}, new int[]{8000}, new int[]{1}},
                {MediaFormat.MIMETYPE_AUDIO_AMR_WB, new int[]{-1}, new int[]{6600, 8850, 12650,
                        14250, 15850, 18250, 19850, 23050, 23850}, new int[]{16000}, new int[]{1}},
        });
        return prepareParamList(exhaustiveArgsList);
    }

    public EncoderTest(String encodername, String mime, int[] profiles, int[] bitrates,
            int samplerates[], int channelcounts[]) {
        mEncoderName = encodername;
        mMime = mime;
        mProfiles = profiles;
        mBitrates = bitrates;
        mSampleRates = samplerates;
        mChannelCounts = channelcounts;
    }

    private void setUpFormats() {
        mFormats = new ArrayList<MediaFormat>();
        // TODO(b/218887182) Explore parameterizing based on the following loop params as well
        for (int profile : mProfiles) {
            for (int rate : mSampleRates) {
                if (mMime.equals(MediaFormat.MIMETYPE_AUDIO_AAC) && profile == 5 && rate < 22050) {
                    // Is this right? HE does not support sample rates < 22050Hz?
                    continue;
                }
                for (int bitrate : mBitrates) {
                    for (int channels : mChannelCounts) {
                        MediaFormat format = new MediaFormat();
                        format.setString(MediaFormat.KEY_MIME, mMime);
                        format.setInteger(MediaFormat.KEY_SAMPLE_RATE, rate);
                        format.setInteger(MediaFormat.KEY_CHANNEL_COUNT, channels);
                        format.setInteger(MediaFormat.KEY_AAC_PROFILE, profile);
                        format.setInteger(MediaFormat.KEY_BIT_RATE, bitrate);
                        mFormats.add(format);
                    }
                }
            }
        }
    }

    @Test
    public void testEncoders() {
        setUpFormats();
        testEncoderWithFormats();
    }

    private void testEncoderWithFormatsParallel(String mime, ArrayList<MediaFormat> formats,
            String componentName, int ThreadCount) {
        int testsStarted = 0;
        int totalDurationSeconds = 0;
        ExecutorService pool = Executors.newFixedThreadPool(ThreadCount);

        for (MediaFormat format : formats) {
            assertEquals(mime, format.getString(MediaFormat.KEY_MIME));
            pool.execute(new EncoderRun(componentName, format));
            int sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
            int channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
            int bytesQueuedPerSecond = 2 * channelCount * sampleRate;
            int durationSeconds =
                    (kNumInputBytes + bytesQueuedPerSecond - 1) / bytesQueuedPerSecond;
            totalDurationSeconds += durationSeconds * kNumEncoderTestsPerRun;
            testsStarted++;
        }
        try {
            pool.shutdown();
            Log.i(TAG, "waiting up to " + totalDurationSeconds + " seconds for "
                            + testsStarted + " sub-tests to finish");
            assertTrue("timed out waiting for encoder threads",
                    pool.awaitTermination(totalDurationSeconds, TimeUnit.SECONDS));
        } catch (InterruptedException e) {
            fail("interrupted while waiting for encoder threads");
        }
    }

    private void testEncoderWithFormats() {
        for (MediaFormat fmt : mFormats) {
            if (!MediaUtils.supports(mEncoderName, fmt)) {
                MediaUtils.skipTest("no encoders found for " + fmt.toString());
                return;
            }
        }
        final int ThreadPoolCount = 3;
        int instances = ThreadPoolCount;
        MediaCodec codec = null;
        try {
            codec = MediaCodec.createByCodecName(mEncoderName);
            MediaCodecInfo info = codec.getCodecInfo();
            MediaCodecInfo.CodecCapabilities cap = info.getCapabilitiesForType(mMime);
            instances = Math.min(cap.getMaxSupportedInstances(), instances);
            assertTrue(instances >= 1);
        } catch (Exception e) {
            fail("codec '" + mEncoderName + "' failed construction.");
        } finally {
            codec.release();
        }
        testEncoderWithFormatsParallel(mMime, mFormats, mEncoderName, instances);
    }

    // See bug 25843966
    private static long[] mBadSeeds = {
            101833462733980l, // fail @ 23680 in all-random mode
            273262699095706l, // fail @ 58880 in all-random mode
            137295510492957l, // fail @ 35840 in zero-lead mode
            57821391502855l,  // fail @ 32000 in zero-lead mode
    };

    private int queueInputBuffer(
            MediaCodec codec, ByteBuffer[] inputBuffers, int index,
            InputStream istream, int mode, long timeUs, Random random) {
        ByteBuffer buffer = inputBuffers[index];
        buffer.rewind();
        int size = buffer.limit();

        if ((mode & MODE_RESOURCE) != 0 && istream != null) {
            while (buffer.hasRemaining()) {
                try {
                    int next = istream.read();
                    if (next < 0) {
                        break;
                    }
                    buffer.put((byte) next);
                } catch (Exception ex) {
                    Log.i(TAG, "caught exception writing: " + ex);
                    break;
                }
            }
        } else if ((mode & MODE_RANDOM) != 0) {
            if ((mode & MODE_SILENTLEAD) != 0) {
                buffer.putInt(0);
                buffer.putInt(0);
                buffer.putInt(0);
                buffer.putInt(0);
            }
            while (true) {
                try {
                    int next = random.nextInt();
                    buffer.putInt(random.nextInt());
                } catch (BufferOverflowException ex) {
                    break;
                }
            }
        } else {
            byte[] zeroes = new byte[size];
            buffer.put(zeroes);
        }

        if ((mode & MODE_QUIET) != 0) {
            int n = buffer.limit();
            for (int i = 0; i < n; i += 2) {
                short s = buffer.getShort(i);
                s /= 8;
                buffer.putShort(i, s);
            }
        }

        codec.queueInputBuffer(index, 0 /* offset */, size, timeUs, 0 /* flags */);

        return size;
    }

    private void dequeueOutputBuffer(
            MediaCodec codec, ByteBuffer[] outputBuffers,
            int index, MediaCodec.BufferInfo info) {
        codec.releaseOutputBuffer(index, false /* render */);
    }

    class EncoderRun implements Runnable {
        String mComponentName;
        MediaFormat mFormat;

        EncoderRun(String componentName, MediaFormat format) {
            mComponentName = componentName;
            mFormat = format;
        }
        @Override
        public void run() {
            try {
                testEncoder(mComponentName, mFormat);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
                fail("Received exception " + e);
            }
        }
    }

    // Number of tests called in testEncoder(String componentName, MediaFormat format)
    private static int kNumEncoderTestsPerRun = 5 + mBadSeeds.length * 2;
    private void testEncoder(String componentName, MediaFormat format)
            throws FileNotFoundException {
        Log.i(TAG, "testEncoder " + componentName + "/" + format);
        // test with all zeroes/silence
        testEncoder(componentName, format, 0, null, MODE_SILENT);

        // test with pcm input file
        testEncoder(componentName, format, 0, "okgoogle123_good.wav", MODE_RESOURCE);
        testEncoder(componentName, format, 0, "okgoogle123_good.wav", MODE_RESOURCE | MODE_QUIET);
        testEncoder(componentName, format, 0, "tones.wav", MODE_RESOURCE);
        testEncoder(componentName, format, 0, "tones.wav", MODE_RESOURCE | MODE_QUIET);

        // test with random data, with and without a few leading zeroes
        for (int i = 0; i < mBadSeeds.length; i++) {
            testEncoder(componentName, format, mBadSeeds[i], null, MODE_RANDOM);
            testEncoder(componentName, format, mBadSeeds[i], null, MODE_RANDOM | MODE_SILENTLEAD);
        }
    }

    private void testEncoder(String componentName, MediaFormat format,
            long startSeed, final String res, int mode) throws FileNotFoundException {

        Log.i(TAG, "testEncoder " + componentName + "/" + mode + "/" + format);
        int sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
        int channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
        int inBitrate = sampleRate * channelCount * 16;  // bit/sec
        int outBitrate = format.getInteger(MediaFormat.KEY_BIT_RATE);

        MediaMuxer muxer = null;
        int muxidx = -1;
        if (sSaveResults) {
            try {
                String outFile = "/data/local/tmp/transcoded-" + componentName +
                        "-" + sampleRate + "Hz-" + channelCount + "ch-" + outBitrate +
                        "bps-" + mode + "-" + res + "-" + startSeed + "-" +
                        (android.os.Process.is64Bit() ? "64bit" : "32bit") + ".mp4";
                new File(outFile).delete();
                muxer = new MediaMuxer(outFile, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
                // The track can't be added until we have the codec specific data
            } catch (Exception e) {
                Log.i(TAG, "couldn't create muxer: " + e);
            }
        }

        InputStream istream = null;
        if ((mode & MODE_RESOURCE) != 0) {
            Preconditions.assertTestFileExists(mInpPrefix + res);
            istream = new FileInputStream(mInpPrefix + res);
        }

        Random random = new Random(startSeed);
        MediaCodec codec;
        try {
            codec = MediaCodec.createByCodecName(componentName);
            String mime = format.getString(MediaFormat.KEY_MIME);
            MediaCodecInfo codecInfo = codec.getCodecInfo();
            MediaCodecInfo.CodecCapabilities caps = codecInfo.getCapabilitiesForType(mime);
            if (!caps.isFormatSupported(format)) {
                codec.release();
                codec = null;
                assertFalse(
                    "Default codec doesn't support " + format.toString(),
                    isDefaultCodec(componentName, mime));
                MediaUtils.skipTest(componentName + " doesn't support " + format.toString());
                return;
            }
        } catch (Exception e) {
            fail("codec '" + componentName + "' failed construction.");
            return; /* does not get here, but avoids warning */
        }
        try {
            codec.configure(
                    format,
                    null /* surface */,
                    null /* crypto */,
                    MediaCodec.CONFIGURE_FLAG_ENCODE);
        } catch (IllegalStateException e) {
            fail("codec '" + componentName + "' failed configuration.");
        }

        codec.start();
        ByteBuffer[] codecInputBuffers = codec.getInputBuffers();
        ByteBuffer[] codecOutputBuffers = codec.getOutputBuffers();

        int numBytesSubmitted = 0;
        boolean doneSubmittingInput = false;
        int numBytesDequeued = 0;

        while (true) {
            int index;

            if (!doneSubmittingInput) {
                index = codec.dequeueInputBuffer(kTimeoutUs /* timeoutUs */);

                if (index != MediaCodec.INFO_TRY_AGAIN_LATER) {
                    long timeUs =
                            (long)numBytesSubmitted * 1000000 / (2 * channelCount * sampleRate);
                    if (numBytesSubmitted >= kNumInputBytes) {
                        codec.queueInputBuffer(
                                index,
                                0 /* offset */,
                                0 /* size */,
                                timeUs,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM);

                        if (VERBOSE) {
                            Log.d(TAG, "queued input EOS.");
                        }

                        doneSubmittingInput = true;
                    } else {
                        int size = queueInputBuffer(
                                codec, codecInputBuffers, index, istream, mode, timeUs, random);

                        numBytesSubmitted += size;

                        if (VERBOSE) {
                            Log.d(TAG, "queued " + size + " bytes of input data.");
                        }
                    }
                }
            }

            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            index = codec.dequeueOutputBuffer(info, kTimeoutUs /* timeoutUs */);

            if (index == MediaCodec.INFO_TRY_AGAIN_LATER) {
            } else if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
            } else if (index == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                codecOutputBuffers = codec.getOutputBuffers();
            } else {
                if (muxer != null) {
                    ByteBuffer buffer = codec.getOutputBuffer(index);
                    if (muxidx < 0) {
                        MediaFormat trackFormat = codec.getOutputFormat();
                        muxidx = muxer.addTrack(trackFormat);
                        muxer.start();
                    }
                    muxer.writeSampleData(muxidx, buffer, info);
                }

                dequeueOutputBuffer(codec, codecOutputBuffers, index, info);

                numBytesDequeued += info.size;

                if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    if (VERBOSE) {
                        Log.d(TAG, "dequeued output EOS.");
                    }
                    break;
                }

                if (VERBOSE) {
                    Log.d(TAG, "dequeued " + info.size + " bytes of output data.");
                }
            }
        }

        if (VERBOSE) {
            Log.d(TAG, "queued a total of " + numBytesSubmitted + "bytes, "
                    + "dequeued " + numBytesDequeued + " bytes.");
        }

        float desiredRatio = (float)outBitrate / (float)inBitrate;
        float actualRatio = (float)numBytesDequeued / (float)numBytesSubmitted;

        if (actualRatio < 0.9 * desiredRatio || actualRatio > 1.1 * desiredRatio) {
            Log.w(TAG, "desiredRatio = " + desiredRatio
                    + ", actualRatio = " + actualRatio);
        }

        codec.release();
        codec = null;
        if (muxer != null) {
            muxer.stop();
            muxer.release();
            muxer = null;
        }
    }
}
