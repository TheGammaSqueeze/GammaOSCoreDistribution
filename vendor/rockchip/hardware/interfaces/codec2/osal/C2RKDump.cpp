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

#undef  ROCKCHIP_LOG_TAG
#define ROCKCHIP_LOG_TAG    "C2RKDump"

#include <string.h>
#include <sys/syscall.h>
#include <unistd.h>
#include <errno.h>

#include "C2RKDump.h"
#include "C2RKEnv.h"
#include "C2RKLog.h"

#define  C2_RECORD_DIR   "/data/video/"

uint32_t C2RKDump::mFlag = 0;

const char *toStr_DumpRole(uint32_t role) {
    switch (role) {
        case DUMP_ROLE_INPUT:       return "input";
        case DUMP_ROLE_OUTPUT:      return "output";
    }

    c2_warn("unsupport dump role %d", role);
    return "unknown";
}

const char *toStr_RawType(uint32_t type) {
    switch (type) {
        case RAW_TYPE_YUV420SP:     return "yuv";
        case RAW_TYPE_RGBA:         return "rgba";
    }

    c2_warn("unsupport raw type %d", type);
    return "unknown";
}

C2RKDump::C2RKDump()
    : mIsEncoder(false),
      mInFile(nullptr),
      mOutFile(nullptr) {
    Rockchip_C2_GetEnvU32("vendor.dump.c2.log", &mFlag, 0);
    c2_info("dump flag: 0x%08x", mFlag);

    for (int i = 0; i < DUMP_ROLE_BUTT; i++) {
        mFrameCount[i] = 0;
        mLastFrameCount[i] = 0;
        mLastFpsTime[i] = 0;
    }
}

C2RKDump::~C2RKDump() {
    if (mInFile != nullptr) {
        fclose(mInFile);
        mInFile = nullptr;
    }

    if (mOutFile != nullptr) {
        fclose(mOutFile);
        mOutFile = nullptr;
    }
}

void C2RKDump::initDump(uint32_t width, uint32_t height, bool isEncoder) {
    char fileName[128];

    if (((mFlag & C2_DUMP_RECORD_ENC_IN) && isEncoder) ||
        ((mFlag & C2_DUMP_RECORD_DEC_IN) && !isEncoder)) {
        memset(fileName, 0, 128);

        sprintf(fileName, "%s%s_in_%dx%d_%ld.bin", C2_RECORD_DIR,
                isEncoder ? "enc" : "dec", width, height, syscall(SYS_gettid));
        mInFile = fopen(fileName, "wb");
        if (mInFile == nullptr) {
            c2_err("failed to open input file, err %s", strerror(errno));
        } else {
            c2_info("recording input to %s", fileName);
        }
    }

    if (((mFlag & C2_DUMP_RECORD_ENC_OUT) && isEncoder) ||
        ((mFlag & C2_DUMP_RECORD_DEC_OUT) && !isEncoder)) {
        memset(fileName, 0, 128);

        sprintf(fileName, "%s%s_out_%dx%d_%ld.bin", C2_RECORD_DIR,
                isEncoder ? "enc" : "dec", width, height, syscall(SYS_gettid));
        mOutFile = fopen(fileName, "wb");
        if (mOutFile == nullptr) {
            c2_err("failed to open output file, err %s", strerror(errno));
        } else {
            c2_info("recording output to %s", fileName);
        }
    }

    mIsEncoder = isEncoder;
}

void C2RKDump::recordInFile(void *data, size_t size) {
    if (mInFile) {
        fwrite(data, 1, size, mInFile);
        fflush(mInFile);
    }
}

void C2RKDump::recordInFile(void *data, uint32_t w, uint32_t h, C2RecRawType type) {
    if (mInFile) {
        size_t size = 0;

        if (type == RAW_TYPE_RGBA) {
            size = w * h * 4;
        } else {
            size = w * h * 3 / 2;
        }

        fwrite(data, 1, size, mInFile);
        fflush(mInFile);

        c2_info("dump_input_%s: data 0x%08x w:h [%d:%d]",
                toStr_RawType(type), data, w, h);
    }
}

void C2RKDump::recordOutFile(void *data, size_t size) {
    if (mOutFile) {
        fwrite(data, 1, size, mOutFile);
        fflush(mOutFile);
    }
}

void C2RKDump::recordOutFile(void *data, uint32_t w, uint32_t h, C2RecRawType type) {
    if (mOutFile) {
        size_t size = 0;

        if (type == RAW_TYPE_RGBA) {
            size = w * h * 4;
        } else {
            size = w * h * 3 / 2;
        }

        fwrite(data, 1, size, mOutFile);
        fflush(mOutFile);

        c2_info("dump_output_%s: data 0x%08x w:h [%d:%d]",
               toStr_RawType(type), data, w, h);
    }
}

void C2RKDump::showDebugFps(C2DumpRole role) {
    if ((role == DUMP_ROLE_INPUT && !(mFlag & C2_DUMP_FPS_SHOW_INPUT)) ||
        (role == DUMP_ROLE_OUTPUT && !(mFlag & C2_DUMP_FPS_SHOW_OUTPUT))) {
        return;
    }

    nsecs_t now = systemTime();
    nsecs_t diff = now - mLastFpsTime[role];

    mFrameCount[role]++;

    if (diff > ms2ns(500)) {
        float fps = ((mFrameCount[role] - mLastFrameCount[role]) * float(s2ns(1))) / diff;
        mLastFpsTime[role] = now;
        mLastFrameCount[role] = mFrameCount[role];
        c2_info("[%s] %s frameCount %d fps = %2.3f", mIsEncoder ? "enc" : "dec",
                toStr_DumpRole(role), mFrameCount[role], fps);
    }
}

