/**
 * Copyright 2021, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#ifndef ANDROID_MEDIA_TUNERHIDLTIMEFILTER_H
#define ANDROID_MEDIA_TUNERHIDLTIMEFILTER_H

#include <aidl/android/media/tv/tuner/BnTunerTimeFilter.h>
#include <android/hardware/tv/tuner/1.0/ITimeFilter.h>
#include <utils/Log.h>

using ::android::sp;
using ::android::hardware::Return;
using ::android::hardware::Void;

using HidlITimeFilter = ::android::hardware::tv::tuner::V1_0::ITimeFilter;

namespace aidl {
namespace android {
namespace media {
namespace tv {
namespace tuner {

class TunerHidlTimeFilter : public BnTunerTimeFilter {
public:
    TunerHidlTimeFilter(sp<HidlITimeFilter> timeFilter);
    virtual ~TunerHidlTimeFilter();

    ::ndk::ScopedAStatus setTimeStamp(int64_t in_timeStamp) override;
    ::ndk::ScopedAStatus clearTimeStamp() override;
    ::ndk::ScopedAStatus getSourceTime(int64_t* _aidl_return) override;
    ::ndk::ScopedAStatus getTimeStamp(int64_t* _aidl_return) override;
    ::ndk::ScopedAStatus close() override;

private:
    sp<HidlITimeFilter> mTimeFilter;
};

}  // namespace tuner
}  // namespace tv
}  // namespace media
}  // namespace android
}  // namespace aidl

#endif  // ANDROID_MEDIA_TUNERHIDLTIMEFILTER_H
