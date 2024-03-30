package device.google.atv.audio_proxy;

import device.google.atv.audio_proxy.AudioChannelMask;
import device.google.atv.audio_proxy.AudioFormat;

/**
 * Config for the output stream.
 */
@VintfStability
parcelable AudioConfig {
    AudioFormat format;
    int sampleRateHz;
    AudioChannelMask channelMask;
}