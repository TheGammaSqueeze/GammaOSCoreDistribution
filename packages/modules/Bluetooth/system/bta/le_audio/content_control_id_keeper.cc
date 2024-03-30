/*
 * Copyright 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include "content_control_id_keeper.h"

#include <bitset>
#include <map>

#include "le_audio_types.h"
#include "osi/include/log.h"

namespace {

using le_audio::types::LeAudioContextType;

}  // namespace

namespace le_audio {

struct ccid_keeper {
 public:
  ccid_keeper() {}

  ~ccid_keeper() {}

  void SetCcid(uint16_t context_type, int ccid) {
    LOG_DEBUG("Ccid: %d, context type %d", ccid, context_type);

    std::bitset<16> test{context_type};
    if (test.count() > 1 ||
        context_type >=
            static_cast<std::underlying_type<LeAudioContextType>::type>(
                LeAudioContextType::RFU)) {
      LOG_ERROR("Unknownd context type %d", context_type);
      return;
    }

    auto ctx_type = static_cast<LeAudioContextType>(context_type);
    ccids_.insert_or_assign(ctx_type, ccid);
  }

  int GetCcid(uint16_t context_type) const {
    std::bitset<16> test{context_type};
    if (test.count() > 1 ||
        context_type >=
            static_cast<std::underlying_type<LeAudioContextType>::type>(
                LeAudioContextType::RFU)) {
      LOG_ERROR("Unknownd context type %d", context_type);
      return -1;
    }

    auto ctx_type = static_cast<LeAudioContextType>(context_type);

    if (ccids_.count(ctx_type) == 0) {
      return -1;
    }

    return ccids_.at(ctx_type);
  }

 private:
  /* Ccid informations */
  std::map<LeAudioContextType /* context */, int /*ccid */> ccids_;
};

struct ContentControlIdKeeper::impl {
  impl(const ContentControlIdKeeper& ccid_keeper) : ccid_keeper_(ccid_keeper) {}

  void Start() {
    LOG_ASSERT(!ccid_keeper_impl_);
    ccid_keeper_impl_ = std::make_unique<ccid_keeper>();
  }

  void Stop() {
    LOG_ASSERT(ccid_keeper_impl_);
    ccid_keeper_impl_.reset();
  }

  bool IsRunning() { return ccid_keeper_impl_ ? true : false; }

  const ContentControlIdKeeper& ccid_keeper_;
  std::unique_ptr<ccid_keeper> ccid_keeper_impl_;
};

ContentControlIdKeeper::ContentControlIdKeeper()
    : pimpl_(std::make_unique<impl>(*this)) {}

void ContentControlIdKeeper::Start() {
  if (!pimpl_->IsRunning()) pimpl_->Start();
}

void ContentControlIdKeeper::Stop() {
  if (pimpl_->IsRunning()) pimpl_->Stop();
}

int ContentControlIdKeeper::GetCcid(uint16_t context_type) const {
  if (!pimpl_->IsRunning()) {
    return -1;
  }

  return pimpl_->ccid_keeper_impl_->GetCcid(context_type);
}

void ContentControlIdKeeper::SetCcid(uint16_t context_type, int ccid) {
  if (pimpl_->IsRunning()) {
    pimpl_->ccid_keeper_impl_->SetCcid(context_type, ccid);
  }
}

}  // namespace le_audio
