/*
 * Copyright (C) 2015 The Android Open Source Project
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

#include "VolumeBase.h"
#include "Utils.h"
#include "VolumeManager.h"

#include <android-base/logging.h>
#include <android-base/stringprintf.h>

#include <fcntl.h>
#include <stdlib.h>
#include <sys/mount.h>
#include <sys/stat.h>
#include <sys/types.h>

using android::base::StringPrintf;

namespace android {
namespace vold {

// Begin global index manager logic

static bool sIndicesInitialized = false;
static std::set<int> sFreeIndices;
static int sVolumeCount = 0;  // Track how many volumes are currently active.

// Initialize with a range of indices
static void initializeIndices() {
    sFreeIndices.clear();
    for (int i = 1; i <= 100; i++) {
        sFreeIndices.insert(i);
    }
    sIndicesInitialized = true;
}

int VolumeBase::allocateIndexForVolume(const std::string& /*volId*/) {
    if (!sIndicesInitialized) {
        initializeIndices();
    }

    if (sFreeIndices.empty()) {
        LOG(ERROR) << "No more free indices available!";
        return -1;
    }

    int idx = *sFreeIndices.begin();
    sFreeIndices.erase(sFreeIndices.begin());
    return idx;
}

void VolumeBase::freeIndexForVolume(int idx) {
    if (idx > 0 && idx <= 100) {
        sFreeIndices.insert(idx);
    }
}

// Reset indices when no volumes remain
static void resetIndicesIfNoVolumes() {
    if (sVolumeCount == 0) {
        initializeIndices();
    }
}

static void incrementVolumeCount() {
    sVolumeCount++;
}

static void decrementVolumeCount() {
    if (sVolumeCount > 0) {
        sVolumeCount--;
    }
    resetIndicesIfNoVolumes();
}

// End global index manager logic

VolumeBase::VolumeBase(Type type)
    : mType(type),
      mMountFlags(0),
      mMountUserId(USER_UNKNOWN),
      mCreated(false),
      mState(State::kUnmounted),
      mSilent(false) {}

VolumeBase::~VolumeBase() {
    CHECK(!mCreated);
}

void VolumeBase::setState(State state) {
    mState = state;

    auto listener = getListener();
    if (listener) {
        listener->onVolumeStateChanged(getId(), static_cast<int32_t>(mState));
    }
}

status_t VolumeBase::setDiskId(const std::string& diskId) {
    if (mCreated) {
        LOG(WARNING) << getId() << " diskId change requires destroyed";
        return -EBUSY;
    }

    mDiskId = diskId;
    return OK;
}

status_t VolumeBase::setPartGuid(const std::string& partGuid) {
    if (mCreated) {
        LOG(WARNING) << getId() << " partGuid change requires destroyed";
        return -EBUSY;
    }

    mPartGuid = partGuid;
    return OK;
}

status_t VolumeBase::setMountFlags(int mountFlags) {
    if ((mState != State::kUnmounted) && (mState != State::kUnmountable)) {
        LOG(WARNING) << getId() << " flags change requires state unmounted or unmountable";
        return -EBUSY;
    }

    mMountFlags = mountFlags;
    return OK;
}

status_t VolumeBase::setMountUserId(userid_t mountUserId) {
    if ((mState != State::kUnmounted) && (mState != State::kUnmountable)) {
        LOG(WARNING) << getId() << " user change requires state unmounted or unmountable";
        return -EBUSY;
    }

    mMountUserId = mountUserId;
    return OK;
}

status_t VolumeBase::setSilent(bool silent) {
    if (mCreated) {
        LOG(WARNING) << getId() << " silence change requires destroyed";
        return -EBUSY;
    }

    mSilent = silent;
    return OK;
}

status_t VolumeBase::setId(const std::string& id) {
    if (mCreated) {
        LOG(WARNING) << getId() << " id change requires not created";
        return -EBUSY;
    }

    mId = id;
    return OK;
}

status_t VolumeBase::setPath(const std::string& path) {
    if (mState != State::kChecking) {
        LOG(WARNING) << getId() << " path change requires state checking";
        return -EBUSY;
    }

    mPath = path;

    auto listener = getListener();
    if (listener) listener->onVolumePathChanged(getId(), mPath);

    return OK;
}

status_t VolumeBase::setInternalPath(const std::string& internalPath) {
    if (mState != State::kChecking) {
        LOG(WARNING) << getId() << " internal path change requires state checking";
        return -EBUSY;
    }

    mInternalPath = internalPath;

    auto listener = getListener();
    if (listener) {
        listener->onVolumeInternalPathChanged(getId(), mInternalPath);
    }

    return OK;
}

status_t VolumeBase::setMountCallback(
        const android::sp<android::os::IVoldMountCallback>& callback) {
    mMountCallback = callback;
    return OK;
}

sp<android::os::IVoldMountCallback> VolumeBase::getMountCallback() const {
    return mMountCallback;
}

android::sp<android::os::IVoldListener> VolumeBase::getListener() const {
    if (mSilent) {
        return nullptr;
    } else {
        return VolumeManager::Instance()->getListener();
    }
}

void VolumeBase::addVolume(const std::shared_ptr<VolumeBase>& volume) {
    mVolumes.push_back(volume);
}

void VolumeBase::removeVolume(const std::shared_ptr<VolumeBase>& volume) {
    mVolumes.remove(volume);
}

std::shared_ptr<VolumeBase> VolumeBase::findVolume(const std::string& id) {
    for (auto vol : mVolumes) {
        if (vol->getId() == id) {
            return vol;
        }
    }
    return nullptr;
}

status_t VolumeBase::create() {
    CHECK(!mCreated);

    mCreated = true;
    status_t res = doCreate();

    auto listener = getListener();
    if (listener) {
        listener->onVolumeCreated(getId(), static_cast<int32_t>(mType), mDiskId, mPartGuid,
                                  mMountUserId);
    }

    setState(State::kUnmounted);

    // Increment volume count when volume is created
    incrementVolumeCount();

    return res;
}

status_t VolumeBase::doCreate() {
    return OK;
}

status_t VolumeBase::destroy() {
    CHECK(mCreated);

    if (mState == State::kMounted) {
        unmount();
        setState(State::kBadRemoval);
    } else {
        setState(State::kRemoved);
    }

    auto listener = getListener();
    if (listener) {
        listener->onVolumeDestroyed(getId());
    }

    status_t res = doDestroy();
    mCreated = false;

    // Decrement volume count when volume is destroyed
    decrementVolumeCount();

    return res;
}

status_t VolumeBase::doDestroy() {
    return OK;
}

status_t VolumeBase::mount() {
    if ((mState != State::kUnmounted) && (mState != State::kUnmountable)) {
        LOG(WARNING) << getId() << " mount requires state unmounted or unmountable";
        return -EBUSY;
    }

    setState(State::kChecking);
    status_t res = doMount();
    setState(res == OK ? State::kMounted : State::kUnmountable);

    if (res == OK) {
        doPostMount();
    }
    return res;
}

void VolumeBase::doPostMount() {}

status_t VolumeBase::unmount() {
    if (mState != State::kMounted) {
        LOG(WARNING) << getId() << " unmount requires state mounted";
        return -EBUSY;
    }

    setState(State::kEjecting);
    for (const auto& vol : mVolumes) {
        if (vol->destroy()) {
            LOG(WARNING) << getId() << " failed to destroy " << vol->getId() << " stacked above";
        }
    }
    mVolumes.clear();

    status_t res = doUnmount();
    setState(State::kUnmounted);
    return res;
}

status_t VolumeBase::format(const std::string& fsType) {
    if (mState == State::kMounted) {
        unmount();
    }

    if ((mState != State::kUnmounted) && (mState != State::kUnmountable)) {
        LOG(WARNING) << getId() << " format requires state unmounted or unmountable";
        return -EBUSY;
    }

    setState(State::kFormatting);
    status_t res = doFormat(fsType);
    setState(State::kUnmounted);
    return res;
}

status_t VolumeBase::doFormat(const std::string& fsType) {
    return -ENOTSUP;
}

std::string VolumeBase::getRootPath() const {
    // Usually the same as the internal path, except for emulated volumes.
    return getInternalPath();
}

std::ostream& VolumeBase::operator<<(std::ostream& stream) const {
    return stream << " VolumeBase{id=" << mId << ",mountFlags=" << mMountFlags
                  << ",mountUserId=" << mMountUserId << "}";
}

}  // namespace vold
}  // namespace android
