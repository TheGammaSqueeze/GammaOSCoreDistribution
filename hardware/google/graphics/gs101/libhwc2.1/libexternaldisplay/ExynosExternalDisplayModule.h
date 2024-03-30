/*
 * Copyright (C) 2019 The Android Open Source Project
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
#ifndef EXYNOS_EXTERNAL_DISPLAY_MODULE_H
#define EXYNOS_EXTERNAL_DISPLAY_MODULE_H

#include "ExynosDisplay.h"
#include "ExynosExternalDisplay.h"

namespace gs101 {

class ExynosExternalDisplayModule : public ExynosExternalDisplay {
    public:
        ExynosExternalDisplayModule(uint32_t index, ExynosDevice *device);
        ~ExynosExternalDisplayModule();
        virtual int32_t validateWinConfigData();
};

}  // namespace gs101

#endif
