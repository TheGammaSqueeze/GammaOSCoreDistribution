project(gfxstream-protocols)
cmake_minimum_required(VERSION 3.11)

add_library(gfxstream_vulkan_headers INTERFACE)
target_include_directories(
    gfxstream_vulkan_headers
    INTERFACE include/vulkan/include)
