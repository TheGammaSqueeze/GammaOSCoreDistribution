# Minimum CMake required
cmake_minimum_required(VERSION 3.1.3)

# Project
project(icui18n C CXX)

add_definitions("-DU_I18N_IMPLEMENTATION")
add_definitions("-D_REENTRANT")

# Add other source dirs that icuuc uses
set(ICU_CONFIG_LOCAL_SOURCE_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../../../android_icu4c/include")

# Compile icuuc
set(ICUUC_SOURCE_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../common")
set(ICUUC_TARGET_BINARY_DIR "${CMAKE_CURRENT_BINARY_DIR}/libicuuc-target")
add_subdirectory(${ICUUC_SOURCE_DIR} ${ICUUC_TARGET_BINARY_DIR})

# Glob icu common source files
file(
   GLOB_RECURSE
   ICU_I18N_CC_SOURCES
   RELATIVE ${CMAKE_CURRENT_SOURCE_DIR}
   "*.h" "*.cpp"
)
message(STATUS "ICU_I18N_CC_SOURCES=${ICU_I18N_CC_SOURCES}")

add_library(
   # name
   icui18n

   # SHARED or STATIC
   STATIC

   # Provides a relative path to your source file(s).
   ${ICU_I18N_CC_SOURCES}
)
target_include_directories(icui18n PRIVATE ${CMAKE_CURRENT_SOURCE_DIR})
target_include_directories(icui18n PRIVATE ${ICUUC_SOURCE_DIR})
target_include_directories(icui18n PRIVATE ${ICU_CONFIG_LOCAL_SOURCE_DIR})
target_link_libraries(icui18n icuuc)

set_target_properties(icui18n PROPERTIES LINKER_LANGUAGE CXX)
