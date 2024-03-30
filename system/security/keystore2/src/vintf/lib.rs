// Copyright 2021, The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

//! Bindings for getting the list of HALs.

#[cxx::bridge]
mod ffi {
    unsafe extern "C++" {
        include!("vintf.hpp");

        /// Gets all HAL names.
        /// Note that this is not a zero-cost shim: it will make copies of the strings.
        fn get_hal_names() -> Vec<String>;

        /// Gets all HAL names and versions.
        /// Note that this is not a zero-cost shim: it will make copies of the strings.
        fn get_hal_names_and_versions() -> Vec<String>;

        /// Gets the instances of the given package, version, and interface tuple.
        /// Note that this is not a zero-cost shim: it will make copies of the strings.
        fn get_hidl_instances(
            package: &str,
            major_version: usize,
            minor_version: usize,
            interface_name: &str,
        ) -> Vec<String>;

        /// Gets the instances of the given package, version, and interface tuple.
        /// Note that this is not a zero-cost shim: it will make copies of the strings.
        fn get_aidl_instances(package: &str, version: usize, interface_name: &str) -> Vec<String>;
    }
}

pub use ffi::*;

#[cfg(test)]
mod tests {

    use super::*;

    #[test]
    fn test() {
        let names = get_hal_names();
        assert_ne!(names.len(), 0);

        let names_and_versions = get_hal_names_and_versions();
        assert_ne!(names_and_versions.len(), 0);

        assert!(names_and_versions.len() >= names.len());
    }
}
