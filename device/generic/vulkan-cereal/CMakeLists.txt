cmake_policy(SET CMP0048 NEW)
project(gfx-streaming-kit)
cmake_minimum_required(VERSION 3.18)

option(BUILD_ASAN_WIN32 "Build with ASAN on Windows platform" OFF)

if (WIN32)
    add_definitions("-DUNICODE -D_UNICODE -DNOMINMAX -DEMUGL_BUILD -DVK_USE_PLATFORM_WIN32_KHR -DBUILDING_EMUGL_COMMON_SHARED")

    option(RECORDER_DELEGATE_LIB "Use recorder_delegate_lib dll" OFF)
    if(RECORDER_DELEGATE_LIB)
        add_definitions(-DRECORDER_DELEGATE_LIB)
    endif()
endif()

option(VIRGL_RENDERER_UNSTABLE_APIS "Use unstable virglrenderer APIs" OFF)
if(VIRGL_RENDERER_UNSTABLE_APIS)
    add_definitions(-DVIRGL_RENDERER_UNSTABLE_APIS)
endif()

if(UNIX AND NOT APPLE)
    set(LINUX TRUE)
endif()

find_package(Threads)
include(ExternalProject)

include(GoogleTest)
enable_testing()
# Disable test discovery after build.
# By default, `gtest_discover_tests()` adds a post-build step to run the test executables in order to discover the test
# targets. This is problematic in some build environments. (for example: if cross-compiling)
set(CMAKE_GTEST_DISCOVER_TESTS_DISCOVERY_MODE "PRE_TEST")

# set(CMAKE_INTERPROCEDURAL_OPTIMIZATION TRUE)
set(CMAKE_RUNTIME_OUTPUT_DIRECTORY ${CMAKE_BINARY_DIR})
set(CMAKE_LIBRARY_OUTPUT_DIRECTORY ${CMAKE_BINARY_DIR})
set(CMAKE_INSTALL_PREFIX ${CMAKE_BINARY_DIR}/distribution)
if (WIN32)
    SET(CMAKE_CXX_FLAGS_RELEASE "${CMAKE_CXX_FLAGS_RELEASE} /Zi")
else()
    SET(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -g3 -O3")
endif()
set(CMAKE_POSITION_INDEPENDENT_CODE ON)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_C_STANDARD 11)

if (APPLE)
    add_compile_definitions(VK_USE_PLATFORM_MACOS_MVK)
elseif(UNIX)
    # TODO(kaiyili, b/179477624): Add Linux specific Vulkan platform macro definitions
elseif(WIN32)
    add_compile_definitions(VK_USE_PLATFORM_WIN32_KHR)
endif()

add_compile_definitions(GLM_FORCE_RADIANS)
add_compile_definitions(GLM_FORCE_DEFAULT_ALIGNED_GENTYPES)

if (MSVC)
    # ask msvc not to warn not secure C ISO functions
    add_compile_definitions(_CRT_SECURE_NO_WARNINGS)
    # ask msvc not to warn non C ISO POSIX functions
    add_compile_definitions(_CRT_NONSTDC_NO_DEPRECATE)
endif()

# Macro to easily set the TEST_INCLUDE_FILES properties to point to `test_properties.cmake`
# This macro should be called at the end of any CMakeLists.txt file that defines test targets.
macro("set_test_include_files")
set_property(DIRECTORY APPEND PROPERTY TEST_INCLUDE_FILES ${CMAKE_SOURCE_DIR}/test_properties.cmake)
endmacro()

# Uncomment for ASAN support
# set (CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -fno-omit-frame-pointer -fsanitize=address")
# set (CMAKE_LINKER_FLAGS "${CMAKE_LINKER_FLAGS} -fno-omit-frame-pointer -fsanitize=address")

if (WIN32)
    if (BUILD_ASAN_WIN32)
        set (CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -fsanitize=address")
        # ASAN does not work with flag /MDd, replace it with /MD
        string(REPLACE "/MDd" "/MD" CMAKE_CXX_FLAGS_DEBUG "${CMAKE_CXX_FLAGS_DEBUG}")
        set(CMAKE_CXX_FLAGS_DEBUG ${CMAKE_CXX_FLAGS_DEBUG})

        # ASAN linker
        # User needs to use -D ASAN_LIB_DIR:STRING=/path/to/asan_libs to add library directory
        if (NOT DEFINED ASAN_LIB_DIR)
            message(FATAL_ERROR "Please input ASAN library path with -D ASAN_LIB_DIR:STRING=/path/to/asan_lib_dir")
        endif()
        link_libraries(clang_rt.asan_dynamic-x86_64.lib clang_rt.asan_dynamic_runtime_thunk-x86_64.lib)
        message("Linking ASAN libraries from: ${ASAN_LIB_DIR}")
        link_directories(${ASAN_LIB_DIR})
    endif()
endif()

set(GFXSTREAM_REPO_ROOT ${CMAKE_CURRENT_SOURCE_DIR})

include(android.cmake)

# Third party dependencies
add_subdirectory(third-party)

# Common base libraries for host################################################

add_subdirectory(base)
add_subdirectory(snapshot)
add_subdirectory(host-common)

# Backends######################################################################

add_subdirectory(stream-servers)

# Protocols and associated code generators######################################

add_subdirectory(protocols)

# Fake Android guest#########################3##################################

if (NOT WIN32)
    add_subdirectory(fake-android-guest)
endif()
