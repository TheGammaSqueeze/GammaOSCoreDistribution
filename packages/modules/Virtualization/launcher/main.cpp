/*
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include <dlfcn.h>

#include <cstdlib>
#include <iostream>
#include <string>

#include <android/dlext.h>

extern "C" {
enum {
    ANDROID_NAMESPACE_TYPE_REGULAR = 0,
    ANDROID_NAMESPACE_TYPE_ISOLATED = 1,
    ANDROID_NAMESPACE_TYPE_SHARED = 2,
};

extern struct android_namespace_t* android_create_namespace(
        const char* name, const char* ld_library_path, const char* default_library_path,
        uint64_t type, const char* permitted_when_isolated_path,
        struct android_namespace_t* parent);
} // extern "C"

static void* load(const std::string& libname);

int main(int argc, char* argv[]) {
    if (argc < 2) {
        std::cout << "Usage:\n";
        std::cout << "    " << argv[0] << " LIBNAME [ARGS...]\n";
        return EXIT_FAILURE;
    }

    const char* libname = argv[1];
    void* handle = load(libname);
    if (handle == nullptr) {
        std::cerr << "Failed to load " << libname << ": " << dlerror() << "\n";
        return EXIT_FAILURE;
    }

    int (*entry)(int argc, char* argv[]) = nullptr;
    entry = reinterpret_cast<decltype(entry)>(dlsym(handle, "android_native_main"));
    if (entry == nullptr) {
        std::cerr << "Failed to find entrypoint `android_native_main`: " << dlerror() << "\n";
        return EXIT_FAILURE;
    }

    return entry(argc - 1, argv + 1);
}

// Create a new linker namespace whose search path is set to the directory of the library. Then
// load it from there. Returns the handle to the loaded library if successful. Returns nullptr
// if failed.
void* load(const std::string& libname) {
    // Parent as nullptr means the default namespace
    android_namespace_t* parent = nullptr;
    // The search paths of the new namespace are inherited from the parent namespace.
    const uint64_t type = ANDROID_NAMESPACE_TYPE_SHARED;
    // The directory of the library is appended to the search paths
    const std::string libdir = libname.substr(0, libname.find_last_of("/"));
    const char* ld_library_path = libdir.c_str();
    const char* default_library_path = libdir.c_str();

    android_namespace_t* new_ns = nullptr;
    new_ns = android_create_namespace("microdroid_app", ld_library_path, default_library_path, type,
                                      /* permitted_when_isolated_path */ nullptr, parent);
    if (new_ns == nullptr) {
        std::cerr << "Failed to create linker namespace: " << dlerror() << "\n";
        return nullptr;
    }

    const android_dlextinfo info = {
            .flags = ANDROID_DLEXT_USE_NAMESPACE,
            .library_namespace = new_ns,
    };
    return android_dlopen_ext(libname.c_str(), RTLD_NOW, &info);
}
