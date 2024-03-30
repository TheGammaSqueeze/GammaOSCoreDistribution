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
package android.virtualdevice.cts;

import static android.Manifest.permission.ACTIVITY_EMBEDDING;
import static android.Manifest.permission.ADD_ALWAYS_UNLOCKED_DISPLAY;
import static android.Manifest.permission.ADD_TRUSTED_DISPLAY;
import static android.Manifest.permission.CAPTURE_AUDIO_OUTPUT;
import static android.Manifest.permission.CREATE_VIRTUAL_DEVICE;
import static android.Manifest.permission.MODIFY_AUDIO_ROUTING;
import static android.Manifest.permission.REAL_GET_TASKS;
import static android.Manifest.permission.WAKE_LOCK;
import static android.content.pm.PackageManager.FEATURE_FREEFORM_WINDOW_MANAGEMENT;
import static android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_TRUSTED;
import static android.media.AudioFormat.CHANNEL_IN_MONO;
import static android.media.AudioFormat.ENCODING_PCM_16BIT;
import static android.media.AudioFormat.ENCODING_PCM_FLOAT;
import static android.media.AudioRecord.READ_BLOCKING;
import static android.media.AudioRecord.RECORDSTATE_RECORDING;
import static android.media.AudioRecord.RECORDSTATE_STOPPED;
import static android.media.AudioTrack.PLAYSTATE_PLAYING;
import static android.media.AudioTrack.PLAYSTATE_STOPPED;
import static android.media.AudioTrack.WRITE_BLOCKING;
import static android.media.AudioTrack.WRITE_NON_BLOCKING;
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
import static android.virtualdevice.cts.util.TestAppHelper.MAIN_ACTIVITY_COMPONENT;
import static android.virtualdevice.cts.util.VirtualDeviceTestUtils.createActivityOptions;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import android.companion.virtual.VirtualDeviceManager;
import android.companion.virtual.VirtualDeviceManager.VirtualDevice;
import android.companion.virtual.VirtualDeviceParams;
import android.companion.virtual.audio.AudioCapture;
import android.companion.virtual.audio.AudioInjection;
import android.companion.virtual.audio.VirtualAudioDevice;
import android.companion.virtual.audio.VirtualAudioDevice.AudioConfigurationChangeCallback;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.display.VirtualDisplay;
import android.media.AudioFormat;
import android.platform.test.annotations.AppModeFull;
import android.virtualdevice.cts.common.ActivityResultReceiver;
import android.virtualdevice.cts.common.AudioHelper;
import android.virtualdevice.cts.util.FakeAssociationRule;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.AdoptShellPermissionsRule;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Tests for injection and capturing of audio from streamed apps
 */
@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "VirtualDeviceManager cannot be accessed by instant apps")
public class VirtualAudioTest {
    /**
     * Captured signal should be mostly single frequency and power of that frequency should be
     * over this much of total power.
     */
    public static final double POWER_THRESHOLD_FOR_PRESENT = 0.4f;

    /**
     * The other signals should have very weak power and should not exceed this value
     */
    public static final double POWER_THRESHOLD_FOR_ABSENT = 0.02f;

    private static final VirtualDeviceParams DEFAULT_VIRTUAL_DEVICE_PARAMS =
            new VirtualDeviceParams.Builder().build();

    @Rule
    public AdoptShellPermissionsRule mAdoptShellPermissionsRule = new AdoptShellPermissionsRule(
            InstrumentationRegistry.getInstrumentation().getUiAutomation(),
            ACTIVITY_EMBEDDING,
            ADD_ALWAYS_UNLOCKED_DISPLAY,
            ADD_TRUSTED_DISPLAY,
            CREATE_VIRTUAL_DEVICE,
            REAL_GET_TASKS,
            WAKE_LOCK,
            MODIFY_AUDIO_ROUTING,
            CAPTURE_AUDIO_OUTPUT);
    @Rule
    public FakeAssociationRule mFakeAssociationRule = new FakeAssociationRule();

    private VirtualDevice mVirtualDevice;
    private VirtualDisplay mVirtualDisplay;
    private VirtualAudioDevice mVirtualAudioDevice;

    @Mock
    private VirtualDisplay.Callback mVirtualDisplayCallback;
    @Mock
    private AudioConfigurationChangeCallback mAudioConfigurationChangeCallback;
    @Mock
    private ActivityResultReceiver.Callback mActivityResultCallback;
    @Captor
    private ArgumentCaptor<Intent> mIntentCaptor;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        Context context = getApplicationContext();
        PackageManager packageManager = context.getPackageManager();
        assumeTrue(packageManager.hasSystemFeature(PackageManager.FEATURE_COMPANION_DEVICE_SETUP));
        assumeTrue(packageManager.hasSystemFeature(
                PackageManager.FEATURE_ACTIVITIES_ON_SECONDARY_DISPLAYS));
        assumeFalse("Skipping test: not supported on automotive", isAutomotive());
        // TODO(b/261155110): Re-enable tests once freeform mode is supported in Virtual Display.
        assumeFalse("Skipping test: VirtualDisplay window policy doesn't support freeform.",
                packageManager.hasSystemFeature(FEATURE_FREEFORM_WINDOW_MANAGEMENT));

        VirtualDeviceManager vdm = context.getSystemService(VirtualDeviceManager.class);
        mVirtualDevice = vdm.createVirtualDevice(
                mFakeAssociationRule.getAssociationInfo().getId(),
                DEFAULT_VIRTUAL_DEVICE_PARAMS);
        mVirtualDisplay = mVirtualDevice.createVirtualDisplay(
                /* width= */ 100,
                /* height= */ 100,
                /* densityDpi= */ 240,
                /* surface= */ null,
                /* flags= */ VIRTUAL_DISPLAY_FLAG_TRUSTED,
                Runnable::run,
                mVirtualDisplayCallback);
    }

    @After
    public void tearDown() {
        if (mVirtualDevice != null) {
            mVirtualDevice.close();
        }
        if (mVirtualDisplay != null) {
            mVirtualDisplay.release();
        }
        if (mVirtualAudioDevice != null) {
            mVirtualAudioDevice.close();
        }
    }

    @Test
    public void audioCapture_createCorrectly() {
        mVirtualAudioDevice = mVirtualDevice.createVirtualAudioDevice(
                mVirtualDisplay, /* executor= */ null, /* callback= */ null);
        AudioFormat audioFormat = createCaptureFormat(ENCODING_PCM_16BIT);
        AudioCapture audioCapture = mVirtualAudioDevice.startAudioCapture(audioFormat);
        assertThat(audioCapture).isNotNull();
        assertThat(audioCapture.getFormat()).isEqualTo(audioFormat);
        assertThat(mVirtualAudioDevice.getAudioCapture()).isEqualTo(audioCapture);

        audioCapture.startRecording();
        assertThat(audioCapture.getRecordingState()).isEqualTo(RECORDSTATE_RECORDING);
        audioCapture.stop();
        assertThat(audioCapture.getRecordingState()).isEqualTo(RECORDSTATE_STOPPED);
    }

    @Test
    public void audioInjection_createCorrectly() {
        mVirtualAudioDevice = mVirtualDevice.createVirtualAudioDevice(
                mVirtualDisplay, /* executor= */ null, /* callback= */ null);
        AudioFormat audioFormat = createInjectionFormat(ENCODING_PCM_16BIT);
        AudioInjection audioInjection = mVirtualAudioDevice.startAudioInjection(audioFormat);
        assertThat(audioInjection).isNotNull();
        assertThat(audioInjection.getFormat()).isEqualTo(audioFormat);
        assertThat(mVirtualAudioDevice.getAudioInjection()).isEqualTo(audioInjection);

        audioInjection.play();
        assertThat(audioInjection.getPlayState()).isEqualTo(PLAYSTATE_PLAYING);
        audioInjection.stop();
        assertThat(audioInjection.getPlayState()).isEqualTo(PLAYSTATE_STOPPED);
    }

    @Test
    public void audioCapture_receivesAudioConfigurationChangeCallback() {
        mVirtualAudioDevice = mVirtualDevice.createVirtualAudioDevice(
                mVirtualDisplay, /* executor= */ null, mAudioConfigurationChangeCallback);
        AudioFormat audioFormat = createCaptureFormat(ENCODING_PCM_16BIT);
        mVirtualAudioDevice.startAudioCapture(audioFormat);

        ActivityResultReceiver activityResultReceiver = new ActivityResultReceiver(
                getApplicationContext());
        activityResultReceiver.register(mActivityResultCallback);
        InstrumentationRegistry.getInstrumentation().getTargetContext().startActivity(
                createPlayAudioIntent(BYTE_BUFFER),
                createActivityOptions(mVirtualDisplay));
        verify(mAudioConfigurationChangeCallback, timeout(5000).atLeastOnce())
                .onPlaybackConfigChanged(any());
        verify(mActivityResultCallback, timeout(5000)).onActivityResult(
                mIntentCaptor.capture());
        activityResultReceiver.unregister();
    }

    @Test
    public void audioInjection_receivesAudioConfigurationChangeCallback() {
        mVirtualAudioDevice = mVirtualDevice.createVirtualAudioDevice(
                mVirtualDisplay, /* executor= */ null, mAudioConfigurationChangeCallback);
        AudioFormat audioFormat = createInjectionFormat(ENCODING_PCM_16BIT);
        AudioInjection audioInjection = mVirtualAudioDevice.startAudioInjection(audioFormat);

        ActivityResultReceiver activityResultReceiver = new ActivityResultReceiver(
                getApplicationContext());
        activityResultReceiver.register(mActivityResultCallback);
        InstrumentationRegistry.getInstrumentation().getTargetContext().startActivity(
                createAudioRecordIntent(BYTE_BUFFER),
                createActivityOptions(mVirtualDisplay));

        ByteBuffer byteBuffer = AudioHelper.createAudioData(
                SAMPLE_RATE, NUMBER_OF_SAMPLES, CHANNEL_COUNT, FREQUENCY, AMPLITUDE);
        int remaining = byteBuffer.remaining();
        while (remaining > 0) {
            remaining -= audioInjection.write(byteBuffer, byteBuffer.remaining(), WRITE_BLOCKING);
        }

        verify(mAudioConfigurationChangeCallback, timeout(5000).atLeastOnce())
                .onRecordingConfigChanged(any());
        verify(mActivityResultCallback, timeout(5000)).onActivityResult(
                mIntentCaptor.capture());
        activityResultReceiver.unregister();
    }

    @Test
    public void audioCapture_readByteBuffer_shouldCaptureAppPlaybackFrequency() {
        runAudioCaptureTest(BYTE_BUFFER, /* readMode= */ -1);
    }

    @Test
    public void audioCapture_readByteBufferBlocking_shouldCaptureAppPlaybackFrequency() {
        runAudioCaptureTest(BYTE_BUFFER, /* readMode= */ READ_BLOCKING);
    }

    @Test
    public void audioCapture_readByteArray_shouldCaptureAppPlaybackData() {
        runAudioCaptureTest(BYTE_ARRAY, /* readMode= */ -1);
    }

    @Test
    public void audioCapture_readByteArrayBlocking_shouldCaptureAppPlaybackData() {
        runAudioCaptureTest(BYTE_ARRAY, /* readMode= */ READ_BLOCKING);
    }

    @Test
    public void audioCapture_readShortArray_shouldCaptureAppPlaybackData() {
        runAudioCaptureTest(SHORT_ARRAY, /* readMode= */ -1);
    }

    @Test
    public void audioCapture_readShortArrayBlocking_shouldCaptureAppPlaybackData() {
        runAudioCaptureTest(SHORT_ARRAY, /* readMode= */ READ_BLOCKING);
    }

    @Test
    public void audioCapture_readFloatArray_shouldCaptureAppPlaybackData() {
        runAudioCaptureTest(FLOAT_ARRAY, /* readMode= */ READ_BLOCKING);
    }

    @Test
    public void audioInjection_writeByteBuffer_appShouldRecordInjectedFrequency() {
        runAudioInjectionTest(BYTE_BUFFER, /* writeMode= */
                WRITE_BLOCKING, /* timestamp= */ 0);
    }

    @Test
    public void audioInjection_writeByteBufferWithTimestamp_appShouldRecordInjectedFrequency() {
        runAudioInjectionTest(BYTE_BUFFER, /* writeMode= */
                WRITE_BLOCKING, /* timestamp= */ 50);
    }

    @Test
    public void audioInjection_writeByteArray_appShouldRecordInjectedData() {
        runAudioInjectionTest(BYTE_ARRAY, /* writeMode= */ -1, /* timestamp= */ 0);
    }

    @Test
    public void audioInjection_writeByteArrayBlocking_appShouldRecordInjectedData() {
        runAudioInjectionTest(BYTE_ARRAY, /* writeMode= */ WRITE_BLOCKING, /* timestamp= */
                0);
    }

    @Test
    public void audioInjection_writeShortArray_appShouldRecordInjectedData() {
        runAudioInjectionTest(SHORT_ARRAY, /* writeMode= */ -1, /* timestamp= */ 0);
    }

    @Test
    public void audioInjection_writeShortArrayBlocking_appShouldRecordInjectedData() {
        runAudioInjectionTest(SHORT_ARRAY, /* writeMode= */
                WRITE_BLOCKING, /* timestamp= */ 0);
    }

    @Test
    public void audioInjection_writeFloatArray_appShouldRecordInjectedData() {
        runAudioInjectionTest(FLOAT_ARRAY, /* writeMode= */
                WRITE_BLOCKING, /* timestamp= */ 0);
    }

    private boolean isAutomotive() {
        return getApplicationContext().getPackageManager()
                .hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE);
    }

    private void runAudioCaptureTest(@AudioHelper.DataType int dataType, int readMode) {
        mVirtualAudioDevice = mVirtualDevice.createVirtualAudioDevice(
                mVirtualDisplay, /* executor= */ null, /* callback= */ null);
        int encoding = dataType == FLOAT_ARRAY ? ENCODING_PCM_FLOAT : ENCODING_PCM_16BIT;
        AudioCapture audioCapture = mVirtualAudioDevice.startAudioCapture(
                createCaptureFormat(encoding));

        ActivityResultReceiver activityResultReceiver = new ActivityResultReceiver(
                getApplicationContext());
        activityResultReceiver.register(mActivityResultCallback);
        InstrumentationRegistry.getInstrumentation().getTargetContext().startActivity(
                createPlayAudioIntent(dataType),
                createActivityOptions(mVirtualDisplay));

        AudioHelper.CapturedAudio capturedAudio = null;
        switch (dataType) {
            case BYTE_BUFFER:
                ByteBuffer byteBuffer = ByteBuffer.allocateDirect(BUFFER_SIZE_IN_BYTES).order(
                        ByteOrder.nativeOrder());
                capturedAudio = new AudioHelper.CapturedAudio(audioCapture, byteBuffer, readMode);
                assertThat(capturedAudio.getPowerSpectrum(FREQUENCY + 100))
                        .isLessThan(POWER_THRESHOLD_FOR_ABSENT);
                assertThat(capturedAudio.getPowerSpectrum(FREQUENCY))
                        .isGreaterThan(POWER_THRESHOLD_FOR_PRESENT);
                break;
            case BYTE_ARRAY:
                byte[] byteArray = new byte[BUFFER_SIZE_IN_BYTES];
                capturedAudio = new AudioHelper.CapturedAudio(audioCapture, byteArray, readMode);
                assertThat(capturedAudio.getByteValue()).isEqualTo(BYTE_VALUE);
                break;
            case SHORT_ARRAY:
                short[] shortArray = new short[BUFFER_SIZE_IN_BYTES / 2];
                capturedAudio = new AudioHelper.CapturedAudio(audioCapture, shortArray, readMode);
                assertThat(capturedAudio.getShortValue()).isEqualTo(SHORT_VALUE);
                break;
            case FLOAT_ARRAY:
                float[] floatArray = new float[BUFFER_SIZE_IN_BYTES / 4];
                capturedAudio = new AudioHelper.CapturedAudio(audioCapture, floatArray, readMode);
                float roundOffError = Math.abs(capturedAudio.getFloatValue() - FLOAT_VALUE);
                assertThat(roundOffError).isLessThan(0.001f);
                break;
        }

        verify(mActivityResultCallback, timeout(5000)).onActivityResult(
                mIntentCaptor.capture());
        activityResultReceiver.unregister();
    }

    private void runAudioInjectionTest(@AudioHelper.DataType int dataType, int writeMode,
            long timestamp) {
        mVirtualAudioDevice = mVirtualDevice.createVirtualAudioDevice(
                mVirtualDisplay, /* executor= */ null, /* callback= */ null);
        int encoding = dataType == FLOAT_ARRAY ? ENCODING_PCM_FLOAT : ENCODING_PCM_16BIT;
        AudioInjection audioInjection = mVirtualAudioDevice.startAudioInjection(
                createInjectionFormat(encoding));

        ActivityResultReceiver activityResultReceiver = new ActivityResultReceiver(
                getApplicationContext());
        activityResultReceiver.register(mActivityResultCallback);
        InstrumentationRegistry.getInstrumentation().getTargetContext().startActivity(
                createAudioRecordIntent(dataType),
                createActivityOptions(mVirtualDisplay));

        int remaining;
        switch (dataType) {
            case BYTE_BUFFER:
                ByteBuffer byteBuffer = AudioHelper.createAudioData(
                        SAMPLE_RATE, NUMBER_OF_SAMPLES, CHANNEL_COUNT, FREQUENCY, AMPLITUDE);
                remaining = byteBuffer.remaining();
                while (remaining > 0) {
                    if (timestamp != 0) {
                        remaining -= audioInjection.write(byteBuffer, byteBuffer.remaining(),
                                writeMode, timestamp);
                    } else {
                        remaining -= audioInjection.write(byteBuffer, byteBuffer.remaining(),
                                writeMode);
                    }
                }
                break;
            case BYTE_ARRAY:
                byte[] byteArray = new byte[NUMBER_OF_SAMPLES];
                for (int i = 0; i < byteArray.length; i++) {
                    byteArray[i] = BYTE_VALUE;
                }
                remaining = byteArray.length;
                while (remaining > 0) {
                    if (writeMode == WRITE_BLOCKING || writeMode == WRITE_NON_BLOCKING) {
                        remaining -= audioInjection.write(byteArray, 0, byteArray.length,
                                writeMode);
                    } else {
                        remaining -= audioInjection.write(byteArray, 0, byteArray.length);
                    }
                }
                break;
            case SHORT_ARRAY:
                short[] shortArray = new short[NUMBER_OF_SAMPLES];
                for (int i = 0; i < shortArray.length; i++) {
                    shortArray[i] = SHORT_VALUE;
                }
                remaining = shortArray.length;
                while (remaining > 0) {
                    if (writeMode == WRITE_BLOCKING || writeMode == WRITE_NON_BLOCKING) {
                        remaining -= audioInjection.write(shortArray, 0, shortArray.length,
                                writeMode);
                    } else {
                        remaining -= audioInjection.write(shortArray, 0, shortArray.length);
                    }
                }
                break;
            case FLOAT_ARRAY:
                float[] floatArray = new float[NUMBER_OF_SAMPLES];
                for (int i = 0; i < floatArray.length; i++) {
                    floatArray[i] = FLOAT_VALUE;
                }
                remaining = floatArray.length;
                while (remaining > 0) {
                    remaining -= audioInjection.write(floatArray, 0, floatArray.length, writeMode);
                }
                break;
        }

        verify(mActivityResultCallback, timeout(5000)).onActivityResult(
                mIntentCaptor.capture());
        Intent intent = mIntentCaptor.getValue();
        assertThat(intent).isNotNull();
        activityResultReceiver.unregister();

        switch (dataType) {
            case BYTE_BUFFER:
                double powerSpectrumAtFrequency = intent.getDoubleExtra(
                        EXTRA_POWER_SPECTRUM_AT_FREQUENCY,
                        0);
                double powerSpectrumNotFrequency = intent.getDoubleExtra(
                        EXTRA_POWER_SPECTRUM_NOT_FREQUENCY,
                        0);
                assertThat(powerSpectrumNotFrequency).isLessThan(POWER_THRESHOLD_FOR_ABSENT);
                assertThat(powerSpectrumAtFrequency).isGreaterThan(POWER_THRESHOLD_FOR_PRESENT);
                break;
            case BYTE_ARRAY:
                byte byteValue = intent.getByteExtra(EXTRA_LAST_RECORDED_NONZERO_VALUE,
                        Byte.MIN_VALUE);
                assertThat(byteValue).isEqualTo(BYTE_VALUE);
                break;
            case SHORT_ARRAY:
                short shortValue = intent.getShortExtra(EXTRA_LAST_RECORDED_NONZERO_VALUE,
                        Short.MIN_VALUE);
                assertThat(shortValue).isEqualTo(SHORT_VALUE);
                break;
            case FLOAT_ARRAY:
                float floatValue = intent.getFloatExtra(EXTRA_LAST_RECORDED_NONZERO_VALUE, 0);
                float roundOffError = Math.abs(floatValue - FLOAT_VALUE);
                assertThat(roundOffError).isLessThan(0.001f);
                break;
        }
    }

    private static AudioFormat createCaptureFormat(int encoding) {
        return new AudioFormat.Builder()
                .setSampleRate(SAMPLE_RATE)
                .setEncoding(encoding)
                .setChannelMask(CHANNEL_IN_MONO)
                .build();
    }

    private static AudioFormat createInjectionFormat(int encoding) {
        return new AudioFormat.Builder()
                .setSampleRate(SAMPLE_RATE)
                .setEncoding(encoding)
                .setChannelMask(CHANNEL_IN_MONO)
                .build();
    }

    private static Intent createPlayAudioIntent(@AudioHelper.DataType int dataType) {
        return new Intent(ACTION_PLAY_AUDIO)
                .putExtra(EXTRA_AUDIO_DATA_TYPE, dataType)
                .setComponent(MAIN_ACTIVITY_COMPONENT)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
    }

    private static Intent createAudioRecordIntent(@AudioHelper.DataType int dataType) {
        return new Intent(ACTION_RECORD_AUDIO)
                .putExtra(EXTRA_AUDIO_DATA_TYPE, dataType)
                .setComponent(MAIN_ACTIVITY_COMPONENT)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
    }
}
