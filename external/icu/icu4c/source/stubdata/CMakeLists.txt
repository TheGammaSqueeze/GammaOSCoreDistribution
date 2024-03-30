# Minimum CMake required
cmake_minimum_required(VERSION 3.1.3)

# Project
project(icuuc_stubdata C CXX)

add_definitions("-DANDROID_LINK_SHARED_ICU4C")

# Glob icu stub source files
file(
   GLOB_RECURSE
   ICU_STUB_CC_SOURCES
   RELATIVE ${CMAKE_CURRENT_SOURCE_DIR}
   "*.h" "*.cpp"
)
message(STATUS "ICU_STUB_CC_SOURCES=${ICU_STUB_CC_SOURCES}")

# Add icuuc, android_icu4c headers
set(ICU_COMMON_SOURCE_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../common")
set(ICU_CONFIG_LOCAL_SOURCE_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../../../android_icu4c/include")

add_library(
   # name
   icuuc_stubdata

   # SHARED or STATIC
   STATIC

   # Provides a relative path to your source file(s).
   ${ICU_STUB_CC_SOURCES}
)
target_include_directories(icuuc_stubdata PRIVATE ${CMAKE_CURRENT_SOURCE_DIR})
target_include_directories(icuuc_stubdata PRIVATE ${ICU_COMMON_SOURCE_DIR})
target_include_directories(icuuc_stubdata PRIVATE ${ICU_CONFIG_LOCAL_SOURCE_DIR})

set_target_properties(icuuc_stubdata PROPERTIES LINKER_LANGUAGE CXX)
