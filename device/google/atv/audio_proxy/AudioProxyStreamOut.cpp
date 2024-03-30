// Copyright (C) 2020 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

#include "AudioProxyStreamOut.h"

namespace audio_proxy {

AudioProxyStreamOut::AudioProxyStreamOut(audio_proxy_stream_out_t* stream,
                                         audio_proxy_device_t* device)
    : mStream(stream), mDevice(device) {}

AudioProxyStreamOut::~AudioProxyStreamOut() {
  mDevice->close_output_stream(mDevice, mStream);
}

ssize_t AudioProxyStreamOut::write(const void* buffer, size_t bytes) {
  return mStream->write(mStream, buffer, bytes);
}

void AudioProxyStreamOut::getPresentationPosition(int64_t* frames,
                                                  TimeSpec* timestamp) const {
  struct timespec ts;
  mStream->get_presentation_position(mStream,
                                     reinterpret_cast<uint64_t*>(frames), &ts);

  timestamp->tvSec = ts.tv_sec;
  timestamp->tvNSec = ts.tv_nsec;
}

void AudioProxyStreamOut::standby() { mStream->standby(mStream); }

void AudioProxyStreamOut::pause() { mStream->pause(mStream); }

void AudioProxyStreamOut::resume() { mStream->resume(mStream); }

void AudioProxyStreamOut::drain(AudioDrain type) {
  mStream->drain(mStream, static_cast<audio_proxy_drain_type_t>(type));
}

void AudioProxyStreamOut::flush() { mStream->flush(mStream); }

void AudioProxyStreamOut::setVolume(float left, float right) {
  mStream->set_volume(mStream, left, right);
}

}  // namespace audio_proxy
