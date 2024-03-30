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

#ifndef ANDROID_MEDIA_TUNERHIDLDEMUX_H
#define ANDROID_MEDIA_TUNERHIDLDEMUX_H

#include <aidl/android/media/tv/tuner/BnTunerDemux.h>
#include <android/hardware/tv/tuner/1.0/ITuner.h>

using ::aidl::android::hardware::tv::tuner::DemuxFilterType;
using ::aidl::android::hardware::tv::tuner::DvrType;
using ::android::sp;
using ::android::hardware::Return;
using ::android::hardware::Void;
using ::android::hardware::tv::tuner::V1_0::IDemux;
using ::std::shared_ptr;
using ::std::vector;

using HidlIDemux = ::android::hardware::tv::tuner::V1_0::IDemux;

namespace aidl {
namespace android {
namespace media {
namespace tv {
namespace tuner {

class TunerHidlDemux : public BnTunerDemux {
public:
    TunerHidlDemux(sp<HidlIDemux> demux, int demuxId);
    virtual ~TunerHidlDemux();

    ::ndk::ScopedAStatus setFrontendDataSource(
            const shared_ptr<ITunerFrontend>& in_frontend) override;
    ::ndk::ScopedAStatus setFrontendDataSourceById(int frontendId) override;
    ::ndk::ScopedAStatus openFilter(const DemuxFilterType& in_type, int32_t in_bufferSize,
                                    const shared_ptr<ITunerFilterCallback>& in_cb,
                                    shared_ptr<ITunerFilter>* _aidl_return) override;
    ::ndk::ScopedAStatus openTimeFilter(shared_ptr<ITunerTimeFilter>* _aidl_return) override;
    ::ndk::ScopedAStatus getAvSyncHwId(const shared_ptr<ITunerFilter>& in_tunerFilter,
                                       int32_t* _aidl_return) override;
    ::ndk::ScopedAStatus getAvSyncTime(int32_t in_avSyncHwId, int64_t* _aidl_return) override;
    ::ndk::ScopedAStatus openDvr(DvrType in_dvbType, int32_t in_bufferSize,
                                 const shared_ptr<ITunerDvrCallback>& in_cb,
                                 shared_ptr<ITunerDvr>* _aidl_return) override;
    ::ndk::ScopedAStatus connectCiCam(int32_t in_ciCamId) override;
    ::ndk::ScopedAStatus disconnectCiCam() override;
    ::ndk::ScopedAStatus close() override;

    int getId() { return mDemuxId; }

private:
    sp<HidlIDemux> mDemux;
    int mDemuxId;
};

}  // namespace tuner
}  // namespace tv
}  // namespace media
}  // namespace android
}  // namespace aidl

#endif  // ANDROID_MEDIA_TUNERHIDLDEMUX_H
