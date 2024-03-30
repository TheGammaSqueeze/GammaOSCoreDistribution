add_subdirectory(angle)
add_subdirectory(astc-codec)
add_subdirectory(lz4)
add_subdirectory(perfetto-tracing-only)
add_subdirectory(renderdoc)

# For Windows: Prevent overriding the parent project's compiler/linker settings
set(gtest_force_shared_crt ON CACHE BOOL "" FORCE)
add_subdirectory(googletest)
