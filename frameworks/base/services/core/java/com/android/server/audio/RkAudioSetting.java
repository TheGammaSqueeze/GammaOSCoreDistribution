package com.android.server.audio;

import android.media.AudioFormat;
import android.media.AudioManager;

import android.os.Binder;
import android.os.UserHandle;
import android.os.RemoteException;
import android.content.Context;
import android.content.Intent;
import java.util.ArrayList;
import android.util.Log;

public class RkAudioSetting {
    private static native int nativeGetSelect(int device);
    private static native int nativeGetMode(int device);
    private static native int nativeGetFormat(int device, String format);

    private static native void nativeSetSelect(int device);
    private static native void nativeSetMode(int device, int mode);
    private static native void nativeSetFormat(int device, int close, String format);
    private static native void nativeupdataFormatForEdid();

    private static final String TAG = "RKAudioSetting";
    private final Context mContext;

    protected static int[] ALL_SURROUND_ENCODINGS = new int[] {
        AudioFormat.ENCODING_AC3,
        AudioFormat.ENCODING_AC4,
        AudioFormat.ENCODING_DTS,
        AudioFormat.ENCODING_E_AC3_JOC,
        AudioFormat.ENCODING_E_AC3,
        AudioFormat.ENCODING_DTS_HD,
        AudioFormat.ENCODING_DOLBY_TRUEHD,
        AudioFormat.ENCODING_IEC61937,
    };

    private int getMaxChannels(int format) {
        int channels = 2;
        switch (format) {
            case AudioFormat.ENCODING_AC3:
            case AudioFormat.ENCODING_AC4:
            case AudioFormat.ENCODING_E_AC3_JOC:
            case AudioFormat.ENCODING_DTS:
                channels = 6;
                break;
            case AudioFormat.ENCODING_E_AC3:
            case AudioFormat.ENCODING_DTS_HD:
            case AudioFormat.ENCODING_DOLBY_TRUEHD:
                channels = 8;
                break;
            default:
                channels = 2;
                break;
        }

        return channels;
    }

    public static String getEncodingName(int format) {
       switch (format) {
            case AudioFormat.ENCODING_AC3:
                return "AC3";
            case AudioFormat.ENCODING_AC4:
                return "AC4";
            case AudioFormat.ENCODING_E_AC3:
                return "EAC3";
            case AudioFormat.ENCODING_E_AC3_JOC:
                return "EAC3-JOC";
            case AudioFormat.ENCODING_DTS:
                return "DTS";
            case AudioFormat.ENCODING_DTS_HD:
                return "DTSHD";
            case AudioFormat.ENCODING_DOLBY_TRUEHD:
                return "TRUEHD";
        }

        return "unknown";
    }

    /**
    * @hide
    */
    public RkAudioSetting(Context context) {
        mContext = context;
    }

    /**
    * @hide
    */
    public int getSelect(int device) throws RemoteException {
        return nativeGetSelect(device);
    }

    /**
    * @hide
    */
    public void setSelect(int device) throws RemoteException {
        nativeSetSelect(device);
        broadcastSupportFormats(device);
    }

    /**
    * @hide
    */
    public void updataFormatForEdid() throws RemoteException {
        nativeupdataFormatForEdid();
        int hdmi = 1;
        broadcastSupportFormats(hdmi);
    }

    /**
    * @hide
    */
    public int getMode(int device) throws RemoteException {
        return nativeGetMode(device);
    }

    /**
    * @hide
    */
    public void setMode(int device, int mode) throws RemoteException {
        nativeSetMode(device, mode);
        broadcastSupportFormats(device);
    }

    /**
    * @hide
    */
    public int getFormat(int device, String format) throws RemoteException {
        return nativeGetFormat(device, format);
    }

    /**
    * @hide
    */
    public void setFormat(int device, int close, String format) throws RemoteException {
        nativeSetFormat(device, close, format);
        broadcastSupportFormats(device);
    }

    private int getFormatsAndChannels(int device, ArrayList<Integer> formats) throws RemoteException {
         int maxChannels = 2;
         for (int i = 0 ; i < ALL_SURROUND_ENCODINGS.length; i++) {
             int format = ALL_SURROUND_ENCODINGS[i];
             String name = getEncodingName(format);
             if (getFormat(device, name) == 1) {  // 1 means support
                 Log.d(TAG, "getFormatsAndChannels : add "+name+", format = "+format);
                 formats.add(format);
                 int current = getMaxChannels(format);
                 if (current > maxChannels) {
                     maxChannels = current;
                 }
             }
         }

         return maxChannels;
    }

    public void broadcastSupportFormats(int device) {
       Intent intent = new Intent();
       intent.setAction(AudioManager.ACTION_HDMI_AUDIO_PLUG);
       intent.putExtra(AudioManager.EXTRA_AUDIO_PLUG_STATE, AudioService.CONNECTION_STATE_CONNECTED);

       int decode_pcm = 0;
       int hdmi_passthrough = 1;
       int spdif_passthrough = 2;

       int maxChannels = 2;
       ArrayList<Integer> encodingList = new ArrayList();
       try {
           Log.d(TAG, "device = "+device);
           // if not decode mode, get the support formats and channels
           if (device != decode_pcm) {
               maxChannels = getFormatsAndChannels(device, encodingList);
           } else {
               maxChannels = 2;
               encodingList.add(AudioFormat.ENCODING_PCM_16BIT);
               Log.d(TAG, "decode mode: add pcm16 format");
           }

           // add support formats
           final int[] encodingArray = encodingList.stream().mapToInt(i -> i).toArray();
           intent.putExtra(AudioManager.EXTRA_ENCODINGS, encodingArray);
           // add max channels
           Log.d(TAG, "max channels = "+maxChannels);
           intent.putExtra(AudioManager.EXTRA_MAX_CHANNEL_COUNT, maxChannels);

           intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY);
           final long ident = Binder.clearCallingIdentity();
           try {
            Log.d(TAG, "broadcast ACTION_HDMI_AUDIO_PLUG");
        //        mContext.sendStickyBroadcastAsUser(intent, UserHandle.ALL);
              mContext.sendStickyBroadcast(intent);
           } finally {
               Binder.restoreCallingIdentity(ident);
           }
       } catch (RemoteException e) {
       }
    }
}
