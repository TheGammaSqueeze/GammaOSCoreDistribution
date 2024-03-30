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
#include "ExynosHWCDebug.h"
#include "ExynosDisplay.h"
#include <sync/sync.h>
#include "exynos_sync.h"

uint32_t mErrLogSize = 0;
uint32_t mFenceLogSize = 0;

int32_t saveErrorLog(const String8 &errString, ExynosDisplay *display)
{
    int32_t ret = NO_ERROR;
    if (mErrLogSize >= ERR_LOG_SIZE)
        return -1;

    FILE *pFile = NULL;
    char filePath[128];
    sprintf(filePath, "%s/hwc_error_log.txt", ERROR_LOG_PATH0);
    pFile = fopen(filePath, "a");
    if (pFile == NULL) {
        ALOGE("Fail to open file %s/hwc_error_log.txt, error: %s", ERROR_LOG_PATH0, strerror(errno));
        sprintf(filePath, "%s/hwc_error_log.txt", ERROR_LOG_PATH1);
        pFile = fopen(filePath, "a");
    }
    if (pFile == NULL) {
        ALOGE("Fail to open file %s/hwc_error_log.txt, error: %s", ERROR_LOG_PATH1, strerror(errno));
        return -errno;
    }

    mErrLogSize = ftell(pFile);
    if (mErrLogSize >= ERR_LOG_SIZE) {
        if (pFile != NULL)
            fclose(pFile);
        return -1;
    }

    String8 saveString;
    struct timeval tv;
    gettimeofday(&tv, NULL);

    if (display != NULL) {
        saveString.appendFormat("%s %s %" PRIu64 ": %s\n", getLocalTimeStr(tv).string(),
                                display->mDisplayName.string(), display->mErrorFrameCount,
                                errString.string());
    } else {
        saveString.appendFormat("%s : %s\n", getLocalTimeStr(tv).string(), errString.string());
    }

    if (pFile != NULL) {
        fwrite(saveString.string(), 1, saveString.size(), pFile);
        mErrLogSize = (uint32_t)ftell(pFile);
        ret = mErrLogSize;
        fclose(pFile);
    }
    return ret;
}

int32_t saveFenceTrace(ExynosDisplay *display) {
    int32_t ret = NO_ERROR;

    if (mFenceLogSize >= FENCE_ERR_LOG_SIZE)
        return -1;

    FILE *pFile = NULL;
    char filePath[128];
    sprintf(filePath, "%s/hwc_fence_state.txt", ERROR_LOG_PATH0);
    pFile = fopen(filePath, "a");
    if (pFile == NULL) {
        ALOGE("Fail to open file %s/hwc_fence_state.txt, error: %s", ERROR_LOG_PATH0, strerror(errno));
        sprintf(filePath, "%s/hwc_fence_state.txt", ERROR_LOG_PATH1);
        pFile = fopen(filePath, "a");
    }
    if (pFile == NULL) {
        ALOGE("Fail to open file %s, error: %s", ERROR_LOG_PATH1, strerror(errno));
        return -errno;
    }

    mFenceLogSize = ftell(pFile);
    if (mFenceLogSize >= FENCE_ERR_LOG_SIZE) {
        if (pFile != NULL)
            fclose(pFile);
        return -1;
    }

    ExynosDevice *device = display->mDevice;

    String8 saveString;

    struct timeval tv;
    gettimeofday(&tv, NULL);
    saveString.appendFormat("\n====== Fences at time:%s ======\n", getLocalTimeStr(tv).string());

    if (device != NULL) {
        for (const auto &[fd, info] : device->mFenceInfos) {
            saveString.appendFormat("---- Fence FD : %d, Display(%d) ----\n", fd, info.displayId);
            saveString.appendFormat("usage: %d, dupFrom: %d, pendingAllowed: %d, leaking: %d\n",
                                    info.usage, info.dupFrom, info.pendingAllowed, info.leaking);

            for (const auto &trace : info.traces) {
                saveString.appendFormat("> dir: %d, type: %d, ip: %d, time:%s\n", trace.direction,
                                        trace.type, trace.ip, getLocalTimeStr(trace.time).string());
            }
        }
    }

    if (pFile != NULL) {
        fwrite(saveString.string(), 1, saveString.size(), pFile);
        mFenceLogSize = (uint32_t)ftell(pFile);
        ret = mFenceLogSize;
        fclose(pFile);
    }

    return ret;
}
