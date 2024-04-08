/*
 * Copyright (C) 2020 Rockchip Electronics Co. LTD
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
#define ROCKCHIP_LOG_TAG    "C2RKComponentFactory"

#include <C2PlatformSupport.h>

#include "C2RKMpiDec.h"
#include "C2RKMpiEnc.h"
#include "C2RKMediaUtils.h"
#include "C2RKLog.h"
#include "C2RKVersion.h"

extern "C" ::C2ComponentFactory* CreateRKCodec2Factory(std::string componentName) {
    C2Component::kind_t kind;
    C2ComponentFactory *factory = NULL;

    c2_info("in version: %s", C2_GIT_BUILD_VERSION);

    if (!C2RKMediaUtils::getKindFromComponentName(componentName, &kind)) {
        c2_err("get kind from component name failed, componentName=%s", componentName.c_str());
        goto __FAILED;
    }

    switch (kind) {
      case C2Component::KIND_DECODER:
        factory = ::android::CreateRKMpiDecFactory(componentName);
      break;
      case C2Component::KIND_ENCODER:
        factory = ::android::CreateRKMpiEncFactory(componentName);
      break;
      default:
        c2_err("the kind is unsupport for create codec2 factory, kind=%d", kind);
        goto __FAILED;
      break;
    }

    return factory;
__FAILED:
    return NULL;
}

extern "C" void DestroyRKCodec2Factory(::C2ComponentFactory* factory) {
    c2_log_func_enter();
    delete factory;
}

