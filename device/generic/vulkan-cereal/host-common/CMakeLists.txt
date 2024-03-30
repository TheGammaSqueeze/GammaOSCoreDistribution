if (APPLE)
    set(gfxstream-host-common-opengles-platform-sources
        opengl/macTouchOpengl.m
        opengl/NativeGpuInfo_darwin.cpp)
elseif (WIN32)
    set(gfxstream-host-common-opengles-platform-sources
        opengl/NativeGpuInfo_windows.cpp)
else()
    set(gfxstream-host-common-opengles-platform-sources
        opengl/NativeGpuInfo_linux.cpp)
endif()

if (RECORDER_DELEGATE_LIB)
    set(gfxstream-base-metrics-sources
        ../base/MetricsRecorderDelegate.cpp)
    set(gfxstream-base-metrics-link-libraries
        recorder_delegate_lib)
else()
    set(gfxstream-base-metrics-sources
        ../base/MetricsNoOp.cpp)
    set(gfxstream-base-metrics-link-libraries)
endif()

# TODO(gregschlom) move this to base
add_library(logging-base
    STATIC
    logging.cpp
    GfxstreamFatalError.cpp
    ${gfxstream-base-metrics-sources})
target_include_directories(
    logging-base
    PRIVATE
    ${GFXSTREAM_REPO_ROOT}
    ${GFXSTREAM_REPO_ROOT}/include)
target_link_libraries(
    logging-base
    PRIVATE
    ${gfxstream-base-metrics-link-libraries})

add_library(
    gfxstream-host-common
    STATIC

    # emugl glue
    crash_reporter.cpp
    vm_operations.cpp
    feature_control.cpp
    dma_device.cpp
    sync_device.cpp
    misc.cpp
    window_operations.cpp

    # What used to be android-emu
    AndroidPipe.cpp
    HostmemIdMapping.cpp
    RefcountPipe.cpp
    AndroidAgentFactory.cpp

    # goldfish sync
    GoldfishSyncCommandQueue.cpp
    goldfish_sync.cpp

    # goldfish dma
    DmaMap.cpp
    GoldfishDma.cpp

    # Address space device
    address_space_device_control_ops.cpp
    address_space_device.cpp
    address_space_host_memory_allocator.cpp
    address_space_shared_slots_host_memory_allocator.cpp
    address_space_graphics.cpp
    address_space_host_media.cpp

    # android-emu avd globals
    avd/info.cpp
    avd/hw-config.cpp

    # general opengles host stuff, incl process pipe
    # and opengl es pipe
    opengles.cpp
    opengl/EmuglBackendList.cpp
    # opengl/EmuglBackendList_unittest.cpp
    opengl/emugl_config.cpp
    # opengl/emugl_config_unittest.cpp
    opengl/GLProcessPipe.cpp
    opengl/GpuFrameBridge.cpp
    # opengl/GpuFrameBridge_unittest.cpp
    opengl/gpuinfo.cpp
    # opengl/gpuinfo_unittest.cpp
    opengl/logger.cpp
    opengl/OpenglEsPipe.cpp
    ${gfxstream-host-common-opengles-platform-sources}
    )

target_include_directories(
    gfxstream-host-common
    PRIVATE
    ${GFXSTREAM_REPO_ROOT}
    ${GFXSTREAM_REPO_ROOT}/include
    ${GFXSTREAM_REPO_ROOT}/stream-servers)
target_link_libraries(
    gfxstream-host-common
    PRIVATE
    gfxstream-base
    logging-base
)

# Tests
add_library(
    gfxstream-host-common-testing-support
    testing/HostAddressSpace.cpp
    testing/MockAndroidAgentFactory.cpp
    testing/MockAndroidEmulatorWindowAgent.cpp
    testing/MockAndroidMultiDisplayAgent.cpp
    testing/MockAndroidVmOperations.cpp)
target_include_directories(
    gfxstream-host-common-testing-support
    PRIVATE
    ${GFXSTREAM_REPO_ROOT})
target_link_libraries(
    gfxstream-host-common-testing-support
    PRIVATE
    gtest
    gmock)

add_executable(
    gfxstream-host-common_unittests
    address_space_graphics_unittests.cpp
    address_space_host_memory_allocator_unittests.cpp
    address_space_shared_slots_host_memory_allocator_unittests.cpp
    HostAddressSpace_unittest.cpp
    HostmemIdMapping_unittest.cpp
    logging_unittest.cpp
    GfxstreamFatalError_unittest.cpp
    RenderDoc_unittest.cpp)

target_include_directories(
    gfxstream-host-common_unittests
    PRIVATE
    ${GFXSTREAM_REPO_ROOT}/include)

target_link_libraries(
    gfxstream-host-common_unittests
    PRIVATE
    gfxstream-base
    gfxstream-host-common
    gfxstream-host-common-testing-support
    gtest_main
    gmock_main)

gtest_discover_tests(gfxstream-host-common_unittests)

set_test_include_files()
