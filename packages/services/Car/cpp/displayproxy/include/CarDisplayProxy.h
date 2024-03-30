/*
 * Copyright (C) 2022 The Android Open Source Project
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

#ifndef CPP_DISPLAYPROXY_INCLUDE_CARDISPLAYPROXYSERVICE_H_
#define CPP_DISPLAYPROXY_INCLUDE_CARDISPLAYPROXYSERVICE_H_

#include <aidl/android/frameworks/automotive/display/BnCarDisplayProxy.h>
#include <gui/SurfaceControl.h>
#include <ui/DisplayMode.h>
#include <ui/DisplayState.h>

namespace aidl::android::frameworks::automotive::display::implementation {

struct DisplayRecord {
    ::android::sp<::android::IBinder> token;
    ::android::sp<::android::SurfaceControl> surfaceControl;
};

class CarDisplayProxy : public ::aidl::android::frameworks::automotive::display::BnCarDisplayProxy {
public:
    // Methods from ::aidl::android::frameworks::automotive::display::ICarDisplayProxy
    ::ndk::ScopedAStatus getDisplayIdList(std::vector<int64_t>* _aidl_return) override;
    ::ndk::ScopedAStatus getDisplayInfo(
            int64_t id,
            ::aidl::android::frameworks::automotive::display::DisplayDesc* _aidl_return) override;
    ::ndk::ScopedAStatus getHGraphicBufferProducer(
            int64_t id, ::aidl::android::hardware::common::NativeHandle* _aidl_return) override;
    ::ndk::ScopedAStatus hideWindow(int64_t id) override;
    ::ndk::ScopedAStatus showWindow(int64_t id) override;

private:
    uint8_t getDisplayPort(uint64_t id) { return (id & 0xF); }
    ::android::sp<::android::IBinder> getDisplayInfoFromSurfaceComposerClient(
            int64_t id, ::android::ui::DisplayMode* displayMode,
            ::android::ui::DisplayState* displayState);

    std::unordered_map<uint64_t, DisplayRecord> mDisplays;
};

}  // namespace aidl::android::frameworks::automotive::display::implementation

#endif  // CPP_DISPLAYPROXY_INCLUDE_CARDISPLAYPROXYSERVICE_H_
