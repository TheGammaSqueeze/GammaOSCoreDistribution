
#include "arm_allocator.h"
#include "idl_common/descriptor.h"
#include "idl_common/allocator.h"

#include <aidlcommonsupport/NativeHandle.h>
#include <aidl/android/hardware/graphics/allocator/AllocationError.h>

using android::hardware::hidl_vec;

namespace arm::allocator::aidl
{

ndk::ScopedAStatus BifrostAllocator::allocate(const std::vector<uint8_t> &in_descriptor, int32_t in_count,
                                              AllocationResult *out_result)
{
        buffer_descriptor_t buffer_descriptor;
	hidl_vec<uint8_t> hidl_descriptor(in_descriptor);
	if (!::arm::mapper::common::grallocDecodeBufferDescriptor(hidl_descriptor, buffer_descriptor))
	{
		return ndk::ScopedAStatus::fromServiceSpecificError(static_cast<int32_t>(AllocationError::BAD_DESCRIPTOR));
	}

	buffer_descriptor.flags |= GPU_DATA_BUFFER_WITH_ANY_FORMAT | SUPPORTS_R8 | USE_AIDL_FRONTBUFFER_USAGE;
	CHECK_EQ(buffer_descriptor.flags, ::arm::mapper::common::DESCRIPTOR_ALLOCATOR_FLAGS);

	auto result = ::arm::allocator::common::allocate(&buffer_descriptor, in_count);
	if (!result.has_value())
	{
		switch (result.error())
		{
		case ::android::NO_ERROR:
			break;
		case ::android::NO_MEMORY:
			return ndk::ScopedAStatus::fromServiceSpecificError(static_cast<int32_t>(AllocationError::NO_RESOURCES));
		case ::android::BAD_VALUE:
			return ndk::ScopedAStatus::fromServiceSpecificError(static_cast<int32_t>(AllocationError::UNSUPPORTED));
		default:
			MALI_GRALLOC_LOGE("Unknown allocation error %d\n", result.error());
			return ndk::ScopedAStatus::fromServiceSpecificError(static_cast<int32_t>(AllocationError::UNSUPPORTED));
		}
	}

	CHECK_EQ(in_count, result->size());

	out_result->stride = buffer_descriptor.pixel_stride;
	out_result->buffers.reserve(in_count);
	/* Pass ownership when returning the created handles. */
	for (auto &handle : *result)
	{
		private_handle_t* private_handle = handle.release();

		out_result->buffers.emplace_back(::android::makeToAidl(private_handle));
		native_handle_delete(private_handle);
	}

	return ndk::ScopedAStatus::ok();

}

} // namespace arm::allocator::aidl


static arm::allocator::aidl::IArmAllocator* s_allocator = NULL;

extern "C" arm::allocator::aidl::IArmAllocator *get_arm_aidl_allocator()
{
        if ( NULL == s_allocator )
        {
                s_allocator = new arm::allocator::aidl::BifrostAllocator();
        }

        return s_allocator;
}

