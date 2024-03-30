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

#pragma once

#include <memory>

namespace le_audio {

class ContentControlIdKeeper {
 public:
  ContentControlIdKeeper();
  virtual ~ContentControlIdKeeper() = default;
  static ContentControlIdKeeper* GetInstance(void) {
    static ContentControlIdKeeper* instance = new ContentControlIdKeeper();
    return instance;
  }
  void Start(void);
  void Stop(void);
  virtual void SetCcid(uint16_t context_type, int ccid);
  virtual int GetCcid(uint16_t context_type) const;

 private:
  struct impl;
  std::unique_ptr<impl> pimpl_;
};
}  // namespace le_audio
