/*
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
 *
 */

#ifndef SRC_DUMMY_REGISTER_EXT_ADEC_H_
#define SRC_DUMMY_REGISTER_EXT_ADEC_H_

#include "RTLibDefine.h"

namespace android {

class DummyDec {
 public:
    static int32_t open(void *pDecoderAttr, void **ppDecoder);
    // decode audio frames
    static int32_t decode(void *pDecoder, void *pDecParam);
    // get audio frames infor
    static int32_t getFrameInfo(void *pDecoder, void *pInfo);
    // close audio decoder
    static int32_t close(void *pDecoder);
    // reset audio decoder
    static int32_t reset(void *pDecoder);
};

}

#endif  // SRC_DUMMY_REGISTER_EXT_ADEC_H_