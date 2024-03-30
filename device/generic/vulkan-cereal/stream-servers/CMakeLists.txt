# Codec common sources
add_subdirectory(apigen-codec-common)
add_subdirectory(compressedTextureFormats)

# Vulkan
add_subdirectory(vulkan)

# GLES translator
add_subdirectory(glestranslator)
add_subdirectory(libGLSnapshot)

# GLES decoder
add_subdirectory(gles1_dec)
add_subdirectory(gles2_dec)

# GLES dispatch based on Translator
add_subdirectory(OpenGLESDispatch)

# RenderControl decoder
add_subdirectory(renderControl_dec)

# Stream server core
set(stream-server-core-sources
    GfxStreamAgents.cpp
    VirtioGpuTimelines.cpp
    ChannelStream.cpp
    ColorBuffer.cpp
    CompositorVk.cpp
    Debug.cpp
    DisplayVk.cpp
    FbConfig.cpp
    FenceSync.cpp
    GLESVersionDetector.cpp
    PostWorker.cpp
    ReadbackWorker.cpp
    ReadBuffer.cpp
    render_api.cpp
    RenderChannelImpl.cpp
    RenderThreadInfo.cpp
    RingStream.cpp
    SwapChainStateVk.cpp
    SyncThread.cpp
    TextureDraw.cpp
    TextureResize.cpp
    WindowSurface.cpp
    YUVConverter.cpp
    RenderThread.cpp
    RenderContext.cpp
    RenderControl.cpp
    RenderWindow.cpp
    RenderLibImpl.cpp
    RendererImpl.cpp
    FrameBuffer.cpp
    vulkan/vk_util.cpp)
if (APPLE)
    set(stream-server-core-platform-sources NativeSubWindow_cocoa.m)
elseif (WIN32)
    set(stream-server-core-platform-sources NativeSubWindow_win32.cpp)
else()
    set(stream-server-core-platform-sources NativeSubWindow_x11.cpp)
endif()

# Compile everything as a static library first so that unit tests can call non-exported functions
add_library(
        gfxstream_backend_static
        STATIC
        ${stream-server-core-sources}
        ${stream-server-core-platform-sources}
)
target_link_libraries(
        gfxstream_backend_static
        PUBLIC
        gfxstream-host-common
        gfxstream-base
        OpenGLESDispatch
        gles1_dec
        gles2_dec
        renderControl_dec
        gfxstream-vulkan-server
        gfxstream-snapshot
        apigen-codec-common
        perfetto-tracing-only)

target_include_directories(
        gfxstream_backend_static
        PUBLIC
        ${GFXSTREAM_REPO_ROOT}
        ${GFXSTREAM_REPO_ROOT}/include
        ${GFXSTREAM_REPO_ROOT}/stream-servers
        ${GFXSTREAM_REPO_ROOT}/stream-servers/apigen-codec-common
        ${GFXSTREAM_REPO_ROOT}/stream-servers/vulkan)

if (WIN32)
    target_link_libraries(gfxstream_backend_static PRIVATE D3d9.lib)
endif()

# gfxstream_backend.dll
add_library(
    gfxstream_backend
    SHARED
    GfxStreamBackend.cpp
    virtio-gpu-gfxstream-renderer.cpp)

target_link_libraries(
    gfxstream_backend
    PUBLIC
    gfxstream_backend_static)

if (WIN32)
    target_link_options(gfxstream_backend PRIVATE /DEBUG)
endif()

android_install_shared(gfxstream_backend)

# Testing libraries
add_subdirectory(testlibs)

# Backend unit tests
add_executable(
    gfxstream_backend_unittests
    gfxstream_unittest.cpp)
target_link_libraries(
    gfxstream_backend_unittests
    PRIVATE
    OSWindow
    gfxstream_backend
    gfxstream-base
    gtest_main)
gtest_discover_tests(gfxstream_backend_unittests)

# More functional tests#########################################################

# Common testing support library################################################
# This includes the server core and testing sources
add_library(
    stream-server-testing-support
    tests/SampleApplication.cpp
    tests/GLSnapshotTesting.cpp
    tests/OpenGLTestContext.cpp
    tests/GLTestUtils.cpp
    tests/ShaderUtils.cpp
    tests/GLSnapshotTestDispatch.cpp
    tests/GLSnapshotTestStateUtils.cpp
    tests/HelloTriangleImp.cpp)
target_include_directories(
    stream-server-testing-support
    PUBLIC
    ${GFXSTREAM_REPO_ROOT}
    ${GFXSTREAM_REPO_ROOT}/base/testing
    ${GFXSTREAM_REPO_ROOT}/include
    ${GFXSTREAM_REPO_ROOT}/stream-servers
    ${GFXSTREAM_REPO_ROOT}/stream-servers/apigen-codec-common
    ${GFXSTREAM_REPO_ROOT}/stream-servers/vulkan)
target_link_libraries(
    stream-server-testing-support
    PUBLIC
    gfxstream_backend_static
    OSWindow
    gtest)

if (LINUX)
add_library(
    x11_testing_support
    tests/X11TestingSupport.cpp)
target_link_libraries(
    x11_testing_support
    gfxstream-base)
endif()

# Basic opengl rendering tests##################################################
add_executable(
    OpenglRender_unittests
    tests/FrameBuffer_unittest.cpp
    tests/DefaultFramebufferBlit_unittest.cpp
    tests/TextureDraw_unittest.cpp
    tests/StalePtrRegistry_unittest.cpp)
target_link_libraries(
    OpenglRender_unittests
    PRIVATE
    gfxstream_backend_static
    stream-server-testing-support
    gfxstream-base-testing-support
    gfxstream-host-common-testing-support
    gtest_main)
if (LINUX)
    target_link_libraries(
        OpenglRender_unittests
        PRIVATE x11_testing_support)
endif()
gtest_discover_tests(OpenglRender_unittests)

# Snapshot tests################################################################
add_executable(
    OpenglRender_snapshot_unittests
    tests/GLSnapshotBuffers_unittest.cpp
    tests/GLSnapshotFramebufferControl_unittest.cpp
    tests/GLSnapshotFramebuffers_unittest.cpp
    tests/GLSnapshotMultisampling_unittest.cpp
    tests/GLSnapshotPixelOperations_unittest.cpp
    tests/GLSnapshotPixels_unittest.cpp
    tests/GLSnapshotPrograms_unittest.cpp
    tests/GLSnapshotRasterization_unittest.cpp
    tests/GLSnapshotRenderbuffers_unittest.cpp
    tests/GLSnapshotRendering_unittest.cpp
    tests/GLSnapshotShaders_unittest.cpp
    tests/GLSnapshotTextures_unittest.cpp
    tests/GLSnapshotTransformation_unittest.cpp
    tests/GLSnapshotVertexAttributes_unittest.cpp
    tests/GLSnapshot_unittest.cpp)
target_link_libraries(
    OpenglRender_snapshot_unittests
    PRIVATE
    gfxstream_backend_static
    stream-server-testing-support
    gfxstream-base-testing-support
    gfxstream-host-common-testing-support
    gtest_main)
gtest_discover_tests(OpenglRender_snapshot_unittests)

# Vulkan tests##################################################################
add_executable(
    Vulkan_unittests
    tests/Vulkan_unittest.cpp
    tests/CompositorVk_unittest.cpp
    tests/SwapChainStateVk_unittest.cpp
    tests/DisplayVk_unittest.cpp
    tests/VirtioGpuTimelines_unittest.cpp
    vulkan/vk_util_unittest.cpp
    vulkan/VkQsriTimeline_unittest.cpp
)
target_link_libraries(
    Vulkan_unittests
    PRIVATE
    gfxstream_backend_static
    stream-server-testing-support
    gfxstream-base-testing-support
    gfxstream-host-common-testing-support
    gtest
    gtest_main
    gmock)
gtest_discover_tests(Vulkan_unittests)

set_test_include_files()
