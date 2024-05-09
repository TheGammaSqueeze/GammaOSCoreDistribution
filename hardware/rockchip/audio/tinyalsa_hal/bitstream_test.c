/*
 * Copyright 2022 Rockchip Electronics Co. LTD
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
 *
 */

#include <stdlib.h>
#include <stdio.h>
#include <string.h>
//#include <utils/Log.h>
#include <errno.h>
#include "bitstream/audio_bitstream.h"
#include "bitstream/audio_bitstream_manager.h"
#include "alsa_audio.h"
#include <sys/time.h>
#include <time.h>
#include "asoundlib.h"

#ifdef LOG_TAG
#undef LOG_TAG
#endif
#define LOG_TAG "bitstreamTest"

#define ALOGD printf
#define ALOGE printf

#define MODE_LPCM  0
#define MODE_NLPCM 1
#define MODE_HBR   2

uint64_t getRelativeUs() {
    struct timespec time = {0, 0};
    clock_gettime(CLOCK_MONOTONIC, &time);
    return (uint64_t)time.tv_sec * 1000000 + (uint64_t)time.tv_nsec / 1000; /* microseconds */
}

uint64_t getRelativeMs() {
    return getRelativeUs()/1000;    /* milliseconds */
}

struct pcm* openSoundCard(int card, int device, struct pcm_config *config) {
    struct pcm *sound = pcm_open(card, device, PCM_OUT | PCM_MONOTONIC, config);
    if (sound && !pcm_is_ready(sound)) {
        ALOGE("%s open sound card failed: %s, card = %d, device = %d",
              __FUNCTION__, pcm_get_error(sound),card, device);
        pcm_close(sound);
        return NULL;
    }
    return sound;
}

static int set_hdmi_mode(int card, int mode) {
    int ret = 0;
    struct mixer* pMixer = mixer_open_legacy((unsigned)card);
    if (!pMixer) {
        ALOGE("mMixer is a null point %s %d,CARD = %d\n",
            __func__, __LINE__, card);
        return -1;
    }

    struct mixer_ctl *pctl = mixer_get_control(pMixer, "AUDIO MODE", 0);
    if (pctl != NULL) {
        ALOGD("set mixer audio_mode is %d for drm\n", mode);
        switch (mode) {
        case MODE_HBR:
            ret = mixer_ctl_set_val(pctl , MODE_HBR);
            break;
        case MODE_NLPCM:
            ret = mixer_ctl_set_val(pctl , MODE_NLPCM);
            break;
        default:
            ret = mixer_ctl_set_val(pctl , MODE_LPCM);
            break;
        }

        if (ret != 0) {
            ALOGE("set_controls() can not set ctl!\n");
            mixer_close_legacy(pMixer);
            return -EINVAL;
        }
    }
    mixer_close_legacy(pMixer);

    return ret;
}

int gSoundCard = -1;
static void signal_handler(int sig) {
    if (gSoundCard != -1) {
        // set pcm mode to hdmi
#ifndef IEC958_FORAMT
        set_hdmi_mode(gSoundCard, MODE_LPCM);
#endif
        gSoundCard = -1;
    }
    ALOGD("Interrupt sig = %d received bitstream test exit\n", sig);
    exit(sig);
}

/*
 * test cmd:
 * bitstream_test -i /data/ac3_iec61937.bin -D 0 -d 0 -r 48000 -c 2 -f s24
 * bitstream_test -i /data/ac3_iec61937.bin -D 0 -d 0 -r 48000 -c 2 -f s16
 * bitstream_test -i /data/ac3_iec61937.bin -D 0 -d 0 -r 48000 -c 2 -f s958
 */
int main(int argc, char **argv) {
    if (argc < 2) {
        ALOGE("Usage: %s [-i file] [-D card] [-d device] [-r samplerate]"
                " [-c channels] \n", argv[0]);
        return -1;
    }

    signal(SIGINT,  signal_handler);
    signal(SIGTERM, signal_handler);
    signal(SIGABRT, signal_handler);

    int card = -1;
    int device = -1;
    int samplerate = 0;
    int channels = 0;
    int format   = PCM_FORMAT_IEC958_SUBFRAME_LE;
    int peroid   = 0;

    int size     = 0;
    int mode     = MODE_NLPCM;
    uint64_t time1, time2, startTime;
    uint64_t total = 0;
    char path[256] = {0};
    rk_bistream* bs = NULL;

    struct pcm_config config =  {
        .channels = 2,
        .rate = 48000,
        .period_size = 1024,
        .period_count = 4,
        .format = PCM_FORMAT_IEC958_SUBFRAME_LE,
    };

    // parse argments
    while (*argv) {
        if (strcmp(*argv, "-d") == 0) {  // index of device card
            argv++;
            if (*argv)
                device = atoi(*argv);
        } else if (strcmp(*argv, "-D") == 0) { // index of sound card
            argv++;
            if (*argv)
                card = atoi(*argv);
        } else if (strcmp(*argv, "-i") == 0) { // test input file(audio format=IEC61937)
            argv++;
            if (*argv) {
                ALOGD("path = %s", *argv);
                snprintf(path, sizeof(path), "%s", *argv);
            }
        } else if (strcmp(*argv, "-r") == 0) { // samplerate
            argv++;
            if (*argv) {
                samplerate = atoi(*argv);
            }
        } else if (strcmp(*argv, "-c") == 0) { // channels
            argv++;
            if (*argv) {
                channels = atoi(*argv);
                if (channels > 2) {
                    mode = MODE_HBR;
                }
            }
        } else if (strcmp(*argv, "-p") == 0) { // peroid size of sound card
            argv++;
            if (*argv) {
                peroid = atoi(*argv);
            }
        } else if (strcmp(*argv, "-f") == 0) { // format to open sound card
            argv++;
            if (!strcmp(*argv, "s16")) {          // spdif and rk616 or rk3128
                format = PCM_FORMAT_S16_LE;
            } else if (!strcmp(*argv, "s24")) {   // hdmi
                format = PCM_FORMAT_S24_LE;
            } else if (!strcmp(*argv, "s958")) {  // hdmi kernel5.1 and later version
                format = PCM_FORMAT_IEC958_SUBFRAME_LE;
            } else {
                ALOGD("%s: format = %s not support", __FUNCTION__, *argv);
                return -1;
            }
        }

        if (*argv)
            argv++;
    }

    // check input format
#ifdef IEC958_FORAMT
    if (format == PCM_FORMAT_S24_LE) {
        ALOGD("%s: PCM_FORMAT_S24_LE not support, using s958", __FUNCTION__);
        return -1;
    }
#else
    if (format == PCM_FORMAT_IEC958_SUBFRAME_LE) {
        ALOGD("%s: PCM_FORMAT_IEC958_SUBFRAME_LE not support, using s24", __FUNCTION__);
        return -1;
    }
#endif

    int period_size = (peroid != 0) ? peroid : samplerate/100;   // 10ms
    config.period_size = period_size;
    config.rate = samplerate;
    config.channels = channels;
    config.format   = format;
    gSoundCard = card;

    ALOGD("play file = %s\n", path);
    ALOGD("samplerate = %d, channels = %d, format = %d\n", config.rate, config.channels, config.format);
    ALOGD("period_size = %d, period_count = %d\n", config.period_size, config.period_count);

#ifndef IEC958_FORAMT
    // set hdmi's mode, the hdmi drvier before kernel5.1 will read it
    set_hdmi_mode(card, mode);
#endif

    // open sound card
    struct pcm *sound = openSoundCard(card, device, &config);
    if (sound == NULL) {
        ALOGE("%s:%d open sound = %d device = %d fail\n", __FUNCTION__, __LINE__, card, device);
        return -1;
    }

    int buffer_size = period_size*sizeof(short)*channels;
    ALOGD("%s:%d buffer_size = %d\n", __FUNCTION__, __LINE__, buffer_size);
    char *inBuffer  = malloc(buffer_size);
    char *outBuffer = NULL;
    int   inSize    = 0;
    int   outSize   = 0;
    int   ret = 0;

    FILE *file = fopen(path, "rb");
    if (file == NULL) {
        ALOGE("%s:%d open %s fail, %s\n", __FUNCTION__, __LINE__, path, strerror(errno));
        goto EXIT;
    }

    if (config.format != PCM_FORMAT_S16_LE) {
        bs = bitstream_init(config.format, samplerate, channels);
        if (bs == NULL) {
            ALOGE("%s:%d bitstream_init fail\n", __FUNCTION__, __LINE__);
            goto EXIT;
        }
    }

    int counter = 0;
    time1 = getRelativeMs();
    startTime = time2 = time1;

    while (1) {
        // read IEC61937 data
        inSize = fread(inBuffer, 1, buffer_size, file);
        if (inSize <= 0) {
            ALOGE("%s:%d read file eos\n", __FUNCTION__, __LINE__);
            break;
        }

        counter ++;
        total += inSize;
        outSize = 0;

        // spdif/RK616/RK3128 use S16_LE
        if (config.format == PCM_FORMAT_S16_LE) {
            outSize = inSize;
            outBuffer = inBuffer;
        } else {
            // convert IEC61937 to IEC60958
            bitstream_encode(bs, inBuffer, inSize, &outBuffer, &outSize);
        }

        if (outSize > 0) {
            ret = pcm_write(sound, (void *)outBuffer, outSize);
            if (ret != 0) {
                ALOGE("%s: %d write data fail\n", __FUNCTION__, __LINE__);
            }
        }

        // for debug print data every 1 second
        time2 = getRelativeMs();
        if (time2 - time1 >=  1000ll) {
             ALOGD("total ms = %lld, total size = %lld, counter = %d\n", time2-startTime, total, counter);
             time1 = time2;
        }
    }

EXIT:
    if (bs != NULL) {
        bitstream_destory(&bs);
    }

    if (inBuffer != NULL) {
        free(inBuffer);
        inBuffer = NULL;
    }

    if (file != NULL) {
        fclose(file);
        file = NULL;
    }

    if (sound != NULL) {
        pcm_close(sound);
        sound = NULL;
    }

#ifndef IEC958_FORAMT
    if (card != -1) {
        // set pcm mode to hdmi
        set_hdmi_mode(card, MODE_LPCM);
    }
#endif

    gSoundCard = -1;
    ALOGD("bitstream test exit\n");
    return 0;
}

