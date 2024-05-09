/*
 * Copyright (C) 2023 Rockchip Electronics Co. LTD
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

#ifndef ANDROID_C2_RK_DUMP_H__
#define ANDROID_C2_RK_DUMP_H__

#include <stdio.h>
#include <utils/Timers.h>


#define C2_DUMP_LOG_TRACE                   (0x00000001)
#define C2_DUMP_LOG_DETAIL                  (0x00000002)
#define C2_DUMP_FPS_SHOW_INPUT              (0x00000004)
#define C2_DUMP_FPS_SHOW_OUTPUT             (0x00000008)

#define C2_DUMP_RECORD_ENC_IN               (0x00000010)
#define C2_DUMP_RECORD_ENC_OUT              (0x00000020)
#define C2_DUMP_RECORD_DEC_IN               (0x00000040)
#define C2_DUMP_RECORD_DEC_OUT              (0x00000080)


enum C2RecRawType {
    RAW_TYPE_YUV420SP = 0,
    RAW_TYPE_RGBA,
};

enum C2DumpRole {
    DUMP_ROLE_INPUT = 0,     // for input buffer fps show
    DUMP_ROLE_OUTPUT,        // for output buffer fps show
    DUMP_ROLE_BUTT,
};

class C2RKDump {
public:
    C2RKDump();
    ~C2RKDump();

    void initDump(uint32_t width, uint32_t height, bool isEncoder);

    void recordInFile(void *data, size_t size);
    void recordInFile(void *data, uint32_t w, uint32_t h, C2RecRawType type);
    void recordOutFile(void *data, size_t size);
    void recordOutFile(void *data, uint32_t w, uint32_t h, C2RecRawType type);

    void showDebugFps(C2DumpRole role);

    static uint32_t getDumpFlag() { return mFlag; }

private:
    static uint32_t mFlag;
    bool mIsEncoder;

    FILE *mInFile;
    FILE *mOutFile;

    /* debug show fps */
    uint32_t mFrameCount[DUMP_ROLE_BUTT];
    uint32_t mLastFrameCount[DUMP_ROLE_BUTT];
    nsecs_t  mLastFpsTime[DUMP_ROLE_BUTT];
};

#endif // ANDROID_C2_RK_DUMP_H__
