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

#pragma once

#include <chrono>
#include <functional>
#include <mutex>
#include <unordered_set>

#include <gtest/gtest_prod.h>

#include "android-base/macros.h"

namespace android {

/*
 * AtomicState manages updating or waiting on a state enum between multiple threads.
 */
template <typename T>
class AtomicState {
 public:
  explicit AtomicState(T state) : state_(state) {}
  ~AtomicState() = default;

  /*
   * Set the state to `to`.  Wakes up any waiters that are waiting on the new state.
   */
  void set(T to) {
    std::lock_guard<std::mutex> lock(m_);
    state_ = to;
    cv_.notify_all();
  }

  /*
   * If the state is `from`, change it to `to` and return true.  Otherwise don't change
   * it and return false.  If the state is changed, wakes up any waiters that are waiting
   * on the new state.
   */
  bool transition(T from, T to) {
    return transition_or(from, to, [&] { return state_; });
  }

  /*
   * If the state is `from`, change it to `to` and return true.  Otherwise, call `or_func`,
   * set the state to the value it returns and return false.  Wakes up any waiters that are
   * waiting on the new state.
   */
  bool transition_or(T from, T to, const std::function<T()>& orFunc) {
    std::lock_guard<std::mutex> lock(m_);

    bool failed = false;
    if (state_ == from) {
      state_ = to;
    } else {
      failed = true;
      state_ = orFunc();
    }
    cv_.notify_all();

    return !failed;
  }

  /*
   * Block until the state is either `state1` or `state2`, or the time limit is reached.
   * Returns true if the time limit was not reached, false if it was reached.
   */
  bool wait_for_either_of(T state1, T state2, std::chrono::milliseconds ms) {
    std::unique_lock<std::mutex> lock(m_);
    bool success = cv_.wait_for(lock, ms, [&] { return state_ == state1 || state_ == state2; });
    return success;
  }

 private:
  T state_;
  std::mutex m_;
  std::condition_variable cv_;

  FRIEND_TEST(AtomicStateTest, transition);
  FRIEND_TEST(AtomicStateTest, wait);

  DISALLOW_COPY_AND_ASSIGN(AtomicState);
};

}  // namespace android
