/*
 * Copyright (c) 2018, Fuzhou Rockchip Electronics Co., Ltd
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
#include "rkpq_api.h"
#include "cJSON.h"

namespace android {

typedef enum _rkpq_mode
{
	PQ_NORMAL = 1,
	PQ_CACL_LUMA = 2,
	PQ_LF_RANGE = 4,
	PQ_IEP = 8,
} rkpq_mode;

class rkpq {
    public:
        rkpq();
        ~rkpq();
        void updateRKPQProcParams(RKPQ_Proc_Params* params);
        bool init(uint32_t src_width, uint32_t src_height, uint32_t* src_width_stride, uint32_t dst_width, uint32_t dst_height, 
				uint32_t alignment, uint32_t src_pix_format, uint32_t src_color_space, uint32_t dst_pix_format, uint32_t dst_color_space, uint32_t flag);
        bool dopq(uint32_t src_fd, uint32_t dst_fd, uint32_t mode);
        int setDstColorSpace(uint32_t plane_id, uint32_t color_space);
        int getResolutionInfo(uint32_t* width, uint32_t* height);
    private:
        rkpq_context pqCxt_;
        RKPQ_Proc_Params* pqProcParams_;
        int pq_timeline;
        int pq_index_;
        mutable pthread_mutex_t mLock_;
};

}
