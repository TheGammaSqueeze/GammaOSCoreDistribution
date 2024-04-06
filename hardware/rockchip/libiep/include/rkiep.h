/*
 * Copyright (c) 2022, Fuzhou Rockchip Electronics Co., Ltd
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

namespace android {

#define IEP_VERSION "IEP-1.0.1"

class rkiep {
    public:
        rkiep();
        ~rkiep();
        int iep2_init(int width, int height, int format);
		int iep2_deinterlace(int srcfd0, int srcfd1, int srcfd2, int dstfd0, int dstfd1, int *dil_order);
		int iep2_deinit();
};

}
