
#include <aidl/android/hardware/graphics/allocator/BnAllocator.h>

#include <aidlcommonsupport/NativeHandle.h>
#include <aidl/android/hardware/graphics/allocator/AllocationError.h>

#pragma once

using namespace aidl::android::hardware::graphics::allocator;

namespace arm::allocator::aidl
{

class IArmAllocator
{
public:
	virtual ~IArmAllocator() {};
	virtual ndk::ScopedAStatus allocate(const std::vector<uint8_t> &in_descriptor, int32_t in_count,
					    AllocationResult *out_result) = 0;
};

class BifrostAllocator : public IArmAllocator
{
public:
	virtual ndk::ScopedAStatus allocate(const std::vector<uint8_t> &in_descriptor, int32_t in_count,
					    AllocationResult *out_result);
};

} // namespace arm::allocator::aidl

extern "C" arm::allocator::aidl::IArmAllocator *get_arm_aidl_allocator();
