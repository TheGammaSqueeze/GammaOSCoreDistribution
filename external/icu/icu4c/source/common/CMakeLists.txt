# Minimum CMake required
cmake_minimum_required(VERSION 3.1.3)

# Project
project(icuuc C CXX)

add_definitions("-DANDROID_LINK_SHARED_ICU4C")
add_definitions("-DU_COMMON_IMPLEMENTATION")
add_definitions("-D_REENTRANT")

# To suppress deprecated declarations for 'uidna_toASCII_68'
add_definitions("-Wno-deprecated-declarations")

# Compile icuuc_stubdata
set(ICUUC_STUB_SOURCE_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../stubdata")
set(ICUUC_STUB_TARGET_BINARY_DIR "${CMAKE_CURRENT_BINARY_DIR}/libicuuc_stubdata-target")
add_subdirectory(${ICUUC_STUB_SOURCE_DIR} ${ICUUC_STUB_TARGET_BINARY_DIR})

# Compile androidicuinit
set(ANDROIDICUINIT_SOURCE_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../../../libandroidicuinit")
set(ANDROIDICUINIT_TARGET_BINARY_DIR "${CMAKE_CURRENT_BINARY_DIR}/androidicu_init-target")
add_subdirectory(${ANDROIDICUINIT_SOURCE_DIR} ${ANDROIDICUINIT_TARGET_BINARY_DIR})

# Add android_icu4c headers
set(ICU_CONFIG_LOCAL_SOURCE_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../../../android_icu4c/include")

# Glob icu common source files
file(
   GLOB_RECURSE
   ICU_COMMON_CC_SOURCES
   RELATIVE ${CMAKE_CURRENT_SOURCE_DIR}
   "*.h" "*.cpp"
)
message(STATUS "ICU_COMMON_CC_SOURCES=${ICU_COMMON_CC_SOURCES}")

add_library(
   # name
   icuuc

   # SHARED or STATIC
   STATIC

   # Provides a relative path to your source file(s).
   ${ICU_COMMON_CC_SOURCES}
)
target_include_directories(icuuc PRIVATE ${CMAKE_CURRENT_SOURCE_DIR})
target_include_directories(icuuc PRIVATE ${ICU_CONFIG_LOCAL_SOURCE_DIR})
target_include_directories(icuuc PRIVATE "${ANDROIDICUINIT_SOURCE_DIR}/include")
target_link_libraries(icuuc icuuc_stubdata androidicuinit)

set_target_properties(icuuc PROPERTIES LINKER_LANGUAGE CXX)

