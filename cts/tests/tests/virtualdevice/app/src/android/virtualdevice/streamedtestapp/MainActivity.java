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

package android.virtualdevice.streamedtestapp;

import static android.hardware.camera2.CameraAccessException.CAMERA_DISABLED;
import static android.hardware.camera2.CameraAccessException.CAMERA_DISCONNECTED;
import static android.media.AudioFormat.CHANNEL_OUT_MONO;
import static android.media.AudioFormat.ENCODING_PCM_16BIT;
import static android.media.AudioFormat.ENCODING_PCM_FLOAT;
import static android.media.AudioRecord.READ_BLOCKING;
import static android.virtualdevice.cts.common.ActivityResultReceiver.ACTION_SEND_ACTIVITY_RESULT;
import static android.virtualdevice.cts.common.ActivityResultReceiver.EXTRA_LAST_RECORDED_NONZERO_VALUE;
import static android.virtualdevice.cts.common.ActivityResultReceiver.EXTRA_POWER_SPECTRUM_AT_FREQUENCY;
import static android.virtualdevice.cts.common.ActivityResultReceiver.EXTRA_POWER_SPECTRUM_NOT_FREQUENCY;
import static android.virtualdevice.cts.common.AudioHelper.ACTION_PLAY_AUDIO;
import static android.virtualdevice.cts.common.AudioHelper.ACTION_RECORD_AUDIO;
import static android.virtualdevice.cts.common.AudioHelper.AMPLITUDE;
import static android.virtualdevice.cts.common.AudioHelper.BUFFER_SIZE_IN_BYTES;
import static android.virtualdevice.cts.common.AudioHelper.BYTE_ARRAY;
import static android.virtualdevice.cts.common.AudioHelper.BYTE_BUFFER;
import static android.virtualdevice.cts.common.AudioHelper.BYTE_VALUE;
import static android.virtualdevice.cts.common.AudioHelper.CHANNEL_COUNT;
import static android.virtualdevice.cts.common.AudioHelper.EXTRA_AUDIO_DATA_TYPE;
import static android.virtualdevice.cts.common.AudioHelper.FLOAT_ARRAY;
import static android.virtualdevice.cts.common.AudioHelper.FLOAT_VALUE;
import static android.virtualdevice.cts.common.AudioHelper.FREQUENCY;
import static android.virtualdevice.cts.common.AudioHelper.NUMBER_OF_SAMPLES;
import static android.virtualdevice.cts.common.AudioHelper.SAMPLE_RATE;
import static android.virtualdevice.cts.common.AudioHelper.SHORT_ARRAY;
import static android.virtualdevice.cts.common.AudioHelper.SHORT_VALUE;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.Activity;
import android.app.KeyguardManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.ResultReceiver;
import android.util.Log;
import android.virtualdevice.cts.common.AudioHelper;

import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

/**
 * Test activity to be streamed in the virtual device.
 */
public class MainActivity extends Activity {

    private static final String TAG = "StreamedTestApp";
    static final String PACKAGE_NAME = "android.virtualdevice.streamedtestapp";

    /**
     * Tell this activity to call the {@link #EXTRA_ACTIVITY_LAUNCHED_RECEIVER} with
     * {@link #RESULT_OK} when it is launched.
     */
    static final String ACTION_CALL_RESULT_RECEIVER =
            PACKAGE_NAME + ".CALL_RESULT_RECEIVER";

    /**
     * Tell this activity to call the API (KeyguardManager.isDeviceSecure) when launched.
     */
    static final String ACTION_CALL_IS_DEVICE_SECURE =
            PACKAGE_NAME + ".ACTION_CALL_IS_DEVICE_SECURE";

    /**
     * Extra in the result data that contains the integer display ID when the receiver for
     * {@link #ACTION_CALL_RESULT_RECEIVER} is called.
     */
    static final String EXTRA_DISPLAY = "display";

    /**
     * Tell this activity to test camera access when it is launched. It will get the String camera
     * id to try opening from {@link #EXTRA_CAMERA_ID}, and put the test outcome in
     * {@link #EXTRA_CAMERA_RESULT} on the activity result intent. If the result was that the
     * onError callback happened, then {@link #EXTRA_CAMERA_ON_ERROR_CODE} will contain the error
     * code.
     */
    static final String ACTION_TEST_CAMERA =
            "android.virtualdevice.streamedtestapp.TEST_CAMERA";
    static final String EXTRA_CAMERA_ID = "cameraId";
    static final String EXTRA_CAMERA_RESULT = "cameraResult";
    public static final String EXTRA_CAMERA_ON_ERROR_CODE = "cameraOnErrorCode";

    /**
     * Tell this activity to test clipboard when it is launched. This will attempt to read the
     * existing string in clipboard, put that in the activity result (as
     * {@link #EXTRA_CLIPBOARD_STRING}), and add the string in {@link #EXTRA_CLIPBOARD_STRING} in
     * the intent extra to the clipboard.
     */
    static final String ACTION_TEST_CLIPBOARD =
            PACKAGE_NAME + ".TEST_CLIPBOARD";
    static final String EXTRA_ACTIVITY_LAUNCHED_RECEIVER = "activityLaunchedReceiver";
    static final String EXTRA_CLIPBOARD_STRING = "clipboardString";
    static final String EXTRA_IS_DEVICE_SECURE = "isDeviceSecure";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        setTitle(getClass().getSimpleName());
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void onResume() {
        super.onResume();
        String action = getIntent().getAction();
        if (action != null) {
            switch (action) {
                case ACTION_CALL_RESULT_RECEIVER:
                    Log.d(TAG, "Handling intent receiver");
                    ResultReceiver resultReceiver =
                            getIntent().getParcelableExtra(EXTRA_ACTIVITY_LAUNCHED_RECEIVER);
                    Bundle result = new Bundle();
                    result.putInt(EXTRA_DISPLAY, getDisplay().getDisplayId());
                    resultReceiver.send(Activity.RESULT_OK, result);
                    finish();
                    break;
                case ACTION_TEST_CLIPBOARD:
                    Log.d(TAG, "Testing clipboard");
                    testClipboard();
                    break;
                case ACTION_TEST_CAMERA:
                    Log.d(TAG, "Testing camera");
                    testCamera();
                    break;
                case ACTION_CALL_IS_DEVICE_SECURE:
                    Log.d(TAG, "Handling ACTION_CALL_IS_DEVICE_SECURE");
                    Intent resultData = new Intent();
                    KeyguardManager km = getSystemService(KeyguardManager.class);
                    boolean isDeviceSecure = km.isDeviceSecure();
                    resultData.putExtra(EXTRA_IS_DEVICE_SECURE, isDeviceSecure);
                    setResult(RESULT_OK, resultData);
                    finish();
                    break;
                case ACTION_PLAY_AUDIO:
                    @AudioHelper.DataType int playDataType = getIntent().getIntExtra(
                            EXTRA_AUDIO_DATA_TYPE, -1);
                    int playEncoding =
                            playDataType == FLOAT_ARRAY ? ENCODING_PCM_FLOAT : ENCODING_PCM_16BIT;
                    int bufferSize = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_OUT_MONO,
                            playEncoding);
                    AudioTrack audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, SAMPLE_RATE,
                            CHANNEL_OUT_MONO, playEncoding, bufferSize, AudioTrack.MODE_STREAM);
                    audioTrack.play();
                    switch (playDataType) {
                        case BYTE_BUFFER:
                            playAudioFromByteBuffer(audioTrack);
                            break;
                        case BYTE_ARRAY:
                            playAudioFromByteArray(audioTrack);
                            break;
                        case SHORT_ARRAY:
                            playAudioFromShortArray(audioTrack);
                            break;
                        case FLOAT_ARRAY:
                            playAudioFromFloatArray(audioTrack);
                            break;
                    }
                    break;
                case ACTION_RECORD_AUDIO:
                    @AudioHelper.DataType int recordDataType = getIntent().getIntExtra(
                            EXTRA_AUDIO_DATA_TYPE, -1);
                    int recordEncoding =
                            recordDataType == FLOAT_ARRAY ? ENCODING_PCM_FLOAT : ENCODING_PCM_16BIT;
                    AudioRecord audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC,
                            SAMPLE_RATE,
                            AudioFormat.CHANNEL_IN_MONO, recordEncoding, BUFFER_SIZE_IN_BYTES);
                    audioRecord.startRecording();
                    switch (recordDataType) {
                        case BYTE_BUFFER:
                            recordAudioToByteBuffer(audioRecord);
                            break;
                        case BYTE_ARRAY:
                            recordAudioToByteArray(audioRecord);
                            break;
                        case SHORT_ARRAY:
                            recordAudioToShortArray(audioRecord);
                            break;
                        case FLOAT_ARRAY:
                            recordAudioToFloatArray(audioRecord);
                            break;
                    }
                    break;
                default:
                    Log.w(TAG, "Unknown action: " + action);
            }
        }
    }

    private void testClipboard() {
        Intent resultData = new Intent();
        ClipboardManager clipboardManager = getSystemService(ClipboardManager.class);
        resultData.putExtra(EXTRA_CLIPBOARD_STRING, clipboardManager.getPrimaryClip());

        String clipboardContent = getIntent().getStringExtra(EXTRA_CLIPBOARD_STRING);
        if (clipboardContent != null) {
            clipboardManager.setPrimaryClip(
                    new ClipData(
                            "CTS clip data",
                            new String[]{"application/text"},
                            new ClipData.Item(clipboardContent)));
            Log.d(TAG, "Wrote \"" + clipboardContent + "\" to clipboard");
        } else {
            Log.w(TAG, "Clipboard content is null");
        }

        setResult(Activity.RESULT_OK, resultData);
        finish();
    }

    private void testCamera() {
        Intent resultData = new Intent();
        CameraManager cameraManager = getSystemService(CameraManager.class);
        String cameraId = null;
        try {
            cameraId = getIntent().getStringExtra(EXTRA_CAMERA_ID);
            Log.d(TAG, "opening camera " + cameraId);
            cameraManager.openCamera(cameraId,
                    new CameraDevice.StateCallback() {
                        @Override
                        public void onOpened(@NonNull CameraDevice camera) {
                            Log.d(TAG, "onOpened");
                        }

                        @Override
                        public void onDisconnected(@NonNull CameraDevice camera) {
                            Log.d(TAG, "onDisconnected");
                            resultData.putExtra(EXTRA_CAMERA_RESULT, "onDisconnected");
                            setResult(Activity.RESULT_OK, resultData);
                            finish();
                        }

                        @Override
                        public void onError(@NonNull CameraDevice camera, int error) {
                            Log.d(TAG, "onError " + error);
                            resultData.putExtra(EXTRA_CAMERA_RESULT, "onError");
                            resultData.putExtra(EXTRA_CAMERA_ON_ERROR_CODE, error);
                            setResult(Activity.RESULT_OK, resultData);
                            finish();
                        }
                    }, null);
        } catch (CameraAccessException e) {
            int reason = e.getReason();
            if (reason == CAMERA_DISABLED || reason == CAMERA_DISCONNECTED) {
                // ok to ignore - we should get one of the onDisconnected or onError callbacks above
                Log.d(TAG, "saw expected CameraAccessException for reason:" + reason);
            } else {
                Log.e(TAG, "got unexpected CameraAccessException with reason:" + reason, e);
            }
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "IllegalArgumentException - maybe invalid camera id? (" + cameraId + ")", e);
        }
    }

    private void playAudioFromByteBuffer(AudioTrack audioTrack) {
        // Write to the audio track asynchronously to avoid ANRs.
        Future<?> unusedFuture = CompletableFuture.runAsync(() -> {
            ByteBuffer audioData = AudioHelper.createAudioData(
                    SAMPLE_RATE, NUMBER_OF_SAMPLES, CHANNEL_COUNT, FREQUENCY, AMPLITUDE);

            int remainingSamples = NUMBER_OF_SAMPLES;
            while (remainingSamples > 0) {
                remainingSamples -= audioTrack.write(audioData, audioData.remaining(),
                        AudioTrack.WRITE_BLOCKING);
            }
            audioTrack.release();

            Intent intent = new Intent(ACTION_SEND_ACTIVITY_RESULT);
            sendBroadcast(intent);
            finish();
        });
    }

    private void playAudioFromByteArray(AudioTrack audioTrack) {
        // Write to the audio track asynchronously to avoid ANRs.
        Future<?> unusedFuture = CompletableFuture.runAsync(() -> {
            byte[] audioData = new byte[NUMBER_OF_SAMPLES];
            for (int i = 0; i < audioData.length; i++) {
                audioData[i] = BYTE_VALUE;
            }

            int remainingSamples = audioData.length;
            while (remainingSamples > 0) {
                remainingSamples -= audioTrack.write(audioData, 0, audioData.length);
            }
            audioTrack.release();

            Intent intent = new Intent(ACTION_SEND_ACTIVITY_RESULT);
            sendBroadcast(intent);
            finish();
        });
    }

    private void playAudioFromShortArray(AudioTrack audioTrack) {
        // Write to the audio track asynchronously to avoid ANRs.
        Future<?> unusedFuture = CompletableFuture.runAsync(() -> {
            short[] audioData = new short[NUMBER_OF_SAMPLES];
            for (int i = 0; i < audioData.length; i++) {
                audioData[i] = SHORT_VALUE;
            }

            int remainingSamples = audioData.length;
            while (remainingSamples > 0) {
                remainingSamples -= audioTrack.write(audioData, 0, audioData.length);
            }
            audioTrack.release();

            Intent intent = new Intent(ACTION_SEND_ACTIVITY_RESULT);
            sendBroadcast(intent);
            finish();
        });
    }

    private void playAudioFromFloatArray(AudioTrack audioTrack) {
        // Write to the audio track asynchronously to avoid ANRs.
        Future<?> unusedFuture = CompletableFuture.runAsync(() -> {
            float[] audioData = new float[NUMBER_OF_SAMPLES];
            for (int i = 0; i < audioData.length; i++) {
                audioData[i] = FLOAT_VALUE;
            }

            int remainingSamples = audioData.length;
            while (remainingSamples > 0) {
                remainingSamples -= audioTrack.write(audioData, 0, audioData.length,
                        AudioTrack.WRITE_BLOCKING);
            }
            audioTrack.release();

            Intent intent = new Intent(ACTION_SEND_ACTIVITY_RESULT);
            sendBroadcast(intent);
            finish();
        });
    }

    private void recordAudioToByteBuffer(AudioRecord audioRecord) {
        // Read from the audio record asynchronously to avoid ANRs.
        Future<?> unusedFuture = CompletableFuture.runAsync(() -> {
            AudioHelper.CapturedAudio capturedAudio = new AudioHelper.CapturedAudio(audioRecord);
            double powerSpectrumNotFrequency = capturedAudio.getPowerSpectrum(FREQUENCY + 100);
            double powerSpectrumAtFrequency = capturedAudio.getPowerSpectrum(FREQUENCY);
            audioRecord.release();

            Intent intent = new Intent(ACTION_SEND_ACTIVITY_RESULT);
            intent.putExtra(EXTRA_POWER_SPECTRUM_NOT_FREQUENCY, powerSpectrumNotFrequency);
            intent.putExtra(EXTRA_POWER_SPECTRUM_AT_FREQUENCY, powerSpectrumAtFrequency);
            sendBroadcast(intent);
            finish();
        });
    }

    private void recordAudioToByteArray(AudioRecord audioRecord) {
        // Read from the audio record asynchronously to avoid ANRs.
        Future<?> unusedFuture = CompletableFuture.runAsync(() -> {
            byte[] audioData = new byte[BUFFER_SIZE_IN_BYTES];
            while (true) {
                int bytesRead = audioRecord.read(audioData, 0, audioData.length);
                if (bytesRead == 0) {
                    continue;
                }
                break;
            }
            byte value = 0;
            for (int i = 0; i < audioData.length; i++) {
                if (audioData[i] == BYTE_VALUE) {
                    value = audioData[i];
                    break;
                }
            }
            audioRecord.release();

            Intent intent = new Intent(ACTION_SEND_ACTIVITY_RESULT);
            intent.putExtra(EXTRA_LAST_RECORDED_NONZERO_VALUE, value);
            sendBroadcast(intent);
            finish();
        });
    }

    private void recordAudioToShortArray(AudioRecord audioRecord) {
        // Read from the audio record asynchronously to avoid ANRs.
        Future<?> unusedFuture = CompletableFuture.runAsync(() -> {
            short[] audioData = new short[BUFFER_SIZE_IN_BYTES / 2];
            while (true) {
                int bytesRead = audioRecord.read(audioData, 0, audioData.length);
                if (bytesRead == 0) {
                    continue;
                }
                break;
            }
            short value = 0;
            for (int i = 0; i < audioData.length; i++) {
                if (audioData[i] == SHORT_VALUE) {
                    value = audioData[i];
                    break;
                }
            }
            audioRecord.release();

            Intent intent = new Intent(ACTION_SEND_ACTIVITY_RESULT);
            intent.putExtra(EXTRA_LAST_RECORDED_NONZERO_VALUE, value);
            sendBroadcast(intent);
            finish();
        });
    }

    private void recordAudioToFloatArray(AudioRecord audioRecord) {
        // Read from the audio record asynchronously to avoid ANRs.
        Future<?> unusedFuture = CompletableFuture.runAsync(() -> {
            float[] audioData = new float[BUFFER_SIZE_IN_BYTES / 4];
            while (true) {
                int bytesRead = audioRecord.read(audioData, 0, audioData.length, READ_BLOCKING);
                if (bytesRead == 0) {
                    continue;
                }
                break;
            }
            float value = 0f;
            for (int i = 0; i < audioData.length; i++) {
                float roundOffDiff = Math.abs(audioData[i] - FLOAT_VALUE);
                if (roundOffDiff < 0.001f) {
                    value = audioData[i];
                    break;
                }
            }
            audioRecord.release();

            Intent intent = new Intent(ACTION_SEND_ACTIVITY_RESULT);
            intent.putExtra(EXTRA_LAST_RECORDED_NONZERO_VALUE, value);
            sendBroadcast(intent);
            finish();
        });
    }
}
