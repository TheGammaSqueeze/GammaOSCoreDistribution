/*
 * Copyright (C) 2022 Rockchip Electronics Co. LTD
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

#include "C2RKExtendParam.h"

const std::vector<C2FieldDescriptor> C2NumberStruct::FieldList() {
    return _FIELD_LIST;
}
const std::vector<C2FieldDescriptor> C2NumberStruct::_FIELD_LIST = {
    { C2FieldDescriptor::INT32, 1, "number", 0, 4 }
};

const std::vector<C2FieldDescriptor> C2MaxLayersStruct::FieldList() {
    return _FIELD_LIST;
}
const std::vector<C2FieldDescriptor> C2MaxLayersStruct::_FIELD_LIST = {
    { C2FieldDescriptor::INT32, 1, "max-p-count", 0, 4 }
};

const std::vector<C2FieldDescriptor> C2ModeEnableStruct::FieldList() {
    return _FIELD_LIST;
}
const std::vector<C2FieldDescriptor> C2ModeEnableStruct::_FIELD_LIST = {
    { C2FieldDescriptor::INT32, 1, "enable", 0, 4 }
};

const std::vector<C2FieldDescriptor> C2MaxCntStruct::FieldList() {
    return _FIELD_LIST;
}
const std::vector<C2FieldDescriptor> C2MaxCntStruct::_FIELD_LIST = {
    { C2FieldDescriptor::INT32, 1, "max-count", 0, 4 }
};

const std::vector<C2FieldDescriptor> C2PreOPStruct::FieldList() {
    return _FIELD_LIST;
}
const std::vector<C2FieldDescriptor> C2PreOPStruct::_FIELD_LIST = {
    { C2FieldDescriptor::INT32, 1, "max-downscale-factor", 0, 4 },
    { C2FieldDescriptor::INT32, 1, "rotation", 4, 4 }
};

const std::vector<C2FieldDescriptor> C2ProfileStruct::FieldList() {
    return _FIELD_LIST;
}
const std::vector<C2FieldDescriptor> C2ProfileStruct::_FIELD_LIST = {
    { C2FieldDescriptor::INT32, 1, "profile", 0, 4 },
    { C2FieldDescriptor::INT32, 1, "level", 4, 4 }
};

const std::vector<C2FieldDescriptor> C2SpacingStruct::FieldList() {
    return _FIELD_LIST;
}
const std::vector<C2FieldDescriptor> C2SpacingStruct::_FIELD_LIST = {
    { C2FieldDescriptor::INT32, 1, "spacing", 0, 4 },
};

const std::vector<C2FieldDescriptor> C2NumLTRFrmsStruct::FieldList() {
    return _FIELD_LIST;
}
const std::vector<C2FieldDescriptor> C2NumLTRFrmsStruct::_FIELD_LIST = {
    { C2FieldDescriptor::INT32, 1, "num-ltr-frames", 0, 4 },
};

const std::vector<C2FieldDescriptor> C2LtrMarkStruct::FieldList() {
    return _FIELD_LIST;
}
const std::vector<C2FieldDescriptor> C2LtrMarkStruct::_FIELD_LIST = {
    { C2FieldDescriptor::INT32, 1, "mark-frame", 0, 4 },
};

const std::vector<C2FieldDescriptor> C2LtrUseStruct::FieldList() {
    return _FIELD_LIST;
}
const std::vector<C2FieldDescriptor> C2LtrUseStruct::_FIELD_LIST = {
    { C2FieldDescriptor::INT32, 1, "use-frame", 0, 4 },
};

const std::vector<C2FieldDescriptor> C2TimestampStruct::FieldList() {
    return _FIELD_LIST;
}
const std::vector<C2FieldDescriptor> C2TimestampStruct::_FIELD_LIST = {
    { C2FieldDescriptor::INT64, 1, "timestamp", 0, 8 },
};

const std::vector<C2FieldDescriptor> C2ScalarStruct::FieldList() {
    return _FIELD_LIST;
}
const std::vector<C2FieldDescriptor> C2ScalarStruct::_FIELD_LIST = {
    { C2FieldDescriptor::INT32, 1, "output-width", 0, 4 },
    { C2FieldDescriptor::INT32, 1, "output-height", 4, 4 }
};

const std::vector<C2FieldDescriptor> C2CropStruct::FieldList() {
    return _FIELD_LIST;
}
const std::vector<C2FieldDescriptor> C2CropStruct::_FIELD_LIST = {
    { C2FieldDescriptor::INT32, 1, "crop-left", 0, 4 },
    { C2FieldDescriptor::INT32, 1, "crop-right", 4, 4 },
    { C2FieldDescriptor::INT32, 1, "crop-width", 8, 4 },
    { C2FieldDescriptor::INT32, 1, "crop-height", 12, 4 }
};
