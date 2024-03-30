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

#include <android-base/thread_annotations.h>
#include <android/net/mdns/aidl/IMDnsEventListener.h>

#include <set>

class MDnsEventReporter final {
  public:
    class EventListener : public android::IBinder::DeathRecipient {
      public:
        EventListener(MDnsEventReporter* eventReporter,
                      const android::sp<android::net::mdns::aidl::IMDnsEventListener>& listener)
            : mEventReporter(eventReporter), mListener(listener) {}
        ~EventListener() override = default;
        void binderDied(const android::wp<android::IBinder>& /* who */) override {
            mEventReporter->removeEventListenerImpl(mListener);
        }
        android::sp<android::net::mdns::aidl::IMDnsEventListener> getListener() {
            return mListener;
        }

      private:
        MDnsEventReporter* mEventReporter;
        android::sp<android::net::mdns::aidl::IMDnsEventListener> mListener;
    };

    MDnsEventReporter(const MDnsEventReporter&) = delete;
    MDnsEventReporter& operator=(const MDnsEventReporter&) = delete;

    using EventListenerSet = std::set<android::sp<EventListener>>;

    // Get the instance of the singleton MDnsEventReporter.
    static MDnsEventReporter& getInstance();

    // Return registered binder services from the singleton MDnsEventReporter. This method is
    // threadsafe.
    const EventListenerSet& getEventListeners() const;

    // Add the binder to the singleton MDnsEventReporter. This method is threadsafe.
    int addEventListener(const android::sp<android::net::mdns::aidl::IMDnsEventListener>& listener);

    // Remove the binder from the singleton MDnsEventReporter. This method is threadsafe.
    int removeEventListener(
            const android::sp<android::net::mdns::aidl::IMDnsEventListener>& listener);

  private:
    MDnsEventReporter() = default;
    ~MDnsEventReporter() = default;

    mutable std::mutex mMutex;
    EventListenerSet mEventListeners GUARDED_BY(mMutex);

    int addEventListenerImpl(
            const android::sp<android::net::mdns::aidl::IMDnsEventListener>& listener)
            EXCLUDES(mMutex);
    int removeEventListenerImpl(
            const android::sp<android::net::mdns::aidl::IMDnsEventListener>& listener)
            EXCLUDES(mMutex);
    const EventListenerSet& getEventListenersImpl() const EXCLUDES(mMutex);
};
