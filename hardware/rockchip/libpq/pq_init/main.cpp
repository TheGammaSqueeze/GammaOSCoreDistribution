/*
 *
 * Copyright 2022 Rockchip Electronics S.LSI Co. LTD
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

#include <stdio.h>
#include <fcntl.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <errno.h>
#include <unistd.h>
#include <cutils/log.h>
#include <rkpq.h>

using namespace android;
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG , "pq_init", __VA_ARGS__)    // DEBUG

int main() {
    rkpq* pq = new rkpq();
    uint32_t width_stride[3];
    for (int i = 0; i < 3; i++) {
      width_stride[i] = 0;
    }
    bool ret = pq->init(1920, 1080, width_stride, 1920, 1080, 0,
        RKPQ_IMG_FMT_RGBA, RKPQ_CLR_SPC_RGB_FULL, RKPQ_IMG_FMT_NV24, RKPQ_CLR_SPC_YUV_601_FULL,
        RKPQ_FLAG_CALC_MEAN_LUMA | RKPQ_FLAG_HIGH_PERFORM);
    LOGD("pq_init ret %d ", ret);
    delete(pq);
    return 0;
}
