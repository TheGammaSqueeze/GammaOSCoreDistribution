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

#define LOG_TAG "MDnsEventReporter"

#include "MDnsEventReporter.h"

using android::IInterface;
using android::sp;
using android::net::mdns::aidl::IMDnsEventListener;

MDnsEventReporter& MDnsEventReporter::getInstance() {
    // It should be initialized only once.
    static MDnsEventReporter instance;
    return instance;
}

const MDnsEventReporter::EventListenerSet& MDnsEventReporter::getEventListeners() const {
    return getEventListenersImpl();
}

int MDnsEventReporter::addEventListener(const sp<IMDnsEventListener>& listener) {
    return addEventListenerImpl(listener);
}

int MDnsEventReporter::removeEventListener(const sp<IMDnsEventListener>& listener) {
    return removeEventListenerImpl(listener);
}

const MDnsEventReporter::EventListenerSet& MDnsEventReporter::getEventListenersImpl() const {
    std::lock_guard lock(mMutex);
    return mEventListeners;
}

int MDnsEventReporter::addEventListenerImpl(const sp<IMDnsEventListener>& listener) {
    if (listener == nullptr) {
        ALOGE("The event listener should not be null");
        return -EINVAL;
    }

    std::lock_guard lock(mMutex);

    for (const auto& it : mEventListeners) {
        if (IInterface::asBinder(it->getListener()).get() == IInterface::asBinder(listener).get()) {
            ALOGW("The event listener was already subscribed");
            return -EEXIST;
        }
    }

    auto eventListener = sp<EventListener>::make(this, listener);
    IInterface::asBinder(listener)->linkToDeath(eventListener);
    mEventListeners.insert(eventListener);
    return 0;
}

int MDnsEventReporter::removeEventListenerImpl(const sp<IMDnsEventListener>& listener) {
    if (listener == nullptr) {
        ALOGE("The event listener should not be null");
        return -EINVAL;
    }

    std::lock_guard lock(mMutex);

    for (const auto& it : mEventListeners) {
        const auto& binder = IInterface::asBinder(it->getListener());
        if (binder == IInterface::asBinder(listener)) {
            binder->unlinkToDeath(it);
            mEventListeners.erase(it);
            return 0;
        }
    }
    ALOGE("The event listener does not exist");
    return -ENOENT;
}