/*
*
* Copyright 2023 Rockchip Electronics S.LSI Co. LTD
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
package android.media;

import android.annotation.NonNull;

import android.media.AudioTrack;
import android.media.AudioManager;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.media.AudioAttributes;
import android.util.Log;
import android.content.Context;
import android.app.Service;
import android.os.SystemClock;
import android.os.Environment;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.io.BufferedOutputStream;

public class AudioStream implements Closeable {
    private static final String TAG = "AudioStream";

    private boolean isRecording = true;
    private boolean mIsStartup = false;
    private Thread record;
    private Context mContext;
    private final AudioManager mAudioManager;
    private AudioDeviceInfo mPreferredDevice = null;

    public AudioStream(@NonNull Context context) {
        if (context == null) {
            throw new IllegalArgumentException("Illegal null Context argument");
        }
        mContext = context;
        mAudioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
    }

    public boolean start() {
        Log.d(TAG, "start");

        if (mIsStartup) {
            Log.w(TAG, "already startup, return");
            return true;
        }

        mIsStartup = true;
        isRecording = true;
        record = new Thread(new recordSound());
        record.start();

        return true;
    }

    public void stop() {
        Log.d(TAG, "stop");

        isRecording = false;

        if (record == null)
            return;

        try {
            //wait thread finish
            record.join(300);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        mIsStartup = false;
    }

    @Override
    public void close() {
        // No need to close, but must implement abstract method.
    }
    class recordSound implements Runnable
    {
        AudioRecord m_in_rec;
        AudioTrack m_out_trk;

        public short[] toShortArray(byte[] src) {
            int count = src.length >> 1;
            short[] dest = new short[count];
            for (int i = 0; i < count; i++) {
                dest[i] = (short) (src[i * 2 + 1] << 8 | src[2 * i + 0] & 0xff);
            }
            return dest;
        }

        public byte[] toByteArray(short[] src) {
            int count = src.length;
            byte[] dest = new byte[count << 1];
            for (int i = 0; i < count; i++) {
                dest[i * 2 + 0] = (byte) (src[i] >> 0);
                dest[i * 2 + 1] = (byte) (src[i] >> 8);
            }
            return dest;
        }

        public void toByteArray(byte[] dest, short[] src) {
            int count = src.length;
            if (dest.length / 2 < count)
                count = dest.length / 2;
            for (int i = 0; i < count; i++) {
                dest[i * 2 + 0] = (byte) (src[i] >> 0);
                dest[i * 2 + 1] = (byte) (src[i] >> 8);
            }
        }

        private void rampVolume(byte[] inBytes, boolean up)
        {
            short[] inShorts = toShortArray(inBytes);
            int frameCount = inShorts.length / 2;
            Log.d(TAG, "ramp volume count: " + frameCount);
            float vl = up ? 0.0f : 1.0f;
            float vlInc = (up ? 1.0f : -1.0f) / frameCount;
            for (int i = 0; i < frameCount; i++) {
                float a = vl * (float)inShorts[i * 2];
                inShorts[i * 2] = (short)a;
                inShorts[i * 2 + 1] = (short)a;
                vl += vlInc;
            }
            toByteArray(inBytes, inShorts);
        }

        public void run()
        {
            synchronized (this) {

            int frequence = 44100;
            int channelConfig = AudioFormat.CHANNEL_IN_STEREO;
            int audioEncoding = AudioFormat.ENCODING_PCM_16BIT;
            AudioFormat audioFormat = (new AudioFormat.Builder())
                    .setChannelMask(channelConfig)
                    .setEncoding(audioEncoding)
                    .setSampleRate(frequence).build();
            int m_out_buf_size = AudioTrack.getMinBufferSize(frequence,
                                        channelConfig,audioEncoding);
            if (m_out_buf_size < 8192) {
                Log.w(TAG, "Track buffer="+m_out_buf_size+", set to 8192");
                m_out_buf_size = 8192;
            }
            m_out_trk = new AudioTrack(
                    new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setAllowedCapturePolicy(AudioAttributes.ALLOW_CAPTURE_BY_ALL)
                        .build(),
                        audioFormat, m_out_buf_size, AudioTrack.MODE_STREAM, AudioManager.AUDIO_SESSION_ID_GENERATE);

            if (m_out_trk != null && m_out_trk.getState() != AudioTrack.STATE_UNINITIALIZED) {
                m_out_trk.play();
            } else {
                throw new UnsupportedOperationException("AudioStream: Cannot create the AudioTrack");
            }

            byte[] m_in_bytes;
            int m_in_buf_size = AudioRecord.getMinBufferSize(frequence, channelConfig, audioEncoding);
            Log.i(TAG, "out min: " + m_out_buf_size + ", in min: " + m_in_buf_size);
            AudioDeviceInfo[] deviceList = mAudioManager.getDevices(AudioManager.GET_DEVICES_INPUTS);
            for (AudioDeviceInfo device : deviceList) {
                if (device.getInternalType() == AudioManager.DEVICE_IN_HDMI) {
                    Log.d(TAG, "find hdmiin");
                    mPreferredDevice = device;
                }
            }
            m_in_rec = new AudioRecord.Builder()
                            .setAudioFormat(new AudioFormat.Builder()
                                    .setSampleRate(frequence)
                                    .setChannelMask(channelConfig)
                                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                    .build())
                            .setAudioSource(MediaRecorder.AudioSource.DEFAULT)
                            .setBufferSizeInBytes(m_in_buf_size)
                            .build();
            m_in_rec.setPreferredDevice(mPreferredDevice);
            m_in_bytes = new byte[m_in_buf_size];

            if (m_in_rec != null && m_in_rec.getState() != AudioRecord.STATE_UNINITIALIZED) {
                m_in_rec.startRecording();
            } else {
                throw new UnsupportedOperationException("AudioStream: Cannot create the AudioRecord");
            }
/*
            File file = null;
            DataOutputStream dos = null;
            try {
                file = new File(Environment.getExternalStorageDirectory(),
                    "hdmiinauido.pcm");
                OutputStream os = new FileOutputStream(file);
                BufferedOutputStream bos = new BufferedOutputStream(os);
                dos = new DataOutputStream(bos);
            } catch (Exception e) {
                e.printStackTrace();
            }
*/
            int readBytes = 0;

            // discard 500ms audio data
            int pre_read_count = 1 + (frequence * 2 * 2) / 2 / m_in_buf_size;
            Log.d(TAG, "pre read count " + pre_read_count);
            while (isRecording && pre_read_count-- >= 0)
                readBytes = m_in_rec.read(m_in_bytes, 0, m_in_buf_size);
            Log.d(TAG, "pre read end");
            if (!isRecording) {
                Log.d(TAG, "exit hdmiin audio");
                m_in_rec.release();
                m_in_rec = null;
                m_out_trk.release();
                m_out_trk = null;
                return;
            }

            // ramp volume for begin
            rampVolume(m_in_bytes, true);

            while (isRecording) {
                if( (readBytes > 0) && (m_out_trk != null))
                    m_out_trk.write(m_in_bytes, 0, readBytes);
/*
                try {
                    for (int i = 0; i < bufferReadResult; i++)
                        dos.write(m_in_bytes[i]);
                } catch (Exception e) {
                    e.printStackTrace();
                }
*/
                readBytes = m_in_rec.read(m_in_bytes, 0, m_in_buf_size);
            }
/*
            try {
                dos.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
*/
            }

            Log.d(TAG, "exit hdmiin audio");
            m_in_rec.release();
            m_in_rec = null;

            // ramp volume for end
            //Log.d(TAG, "AudioTrack setVolume 0\n");
            m_out_trk.setVolume(0.0f);
            //Log.d(TAG, "AudioTrack pause\n");
            m_out_trk.pause();
            SystemClock.sleep(50);
            m_out_trk.release();
            Log.d(TAG, "AudioTrack stop\n");
            m_out_trk = null;
        }
    }
}
