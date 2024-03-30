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

#![allow(missing_docs)]
#![no_main]
#[macro_use]
extern crate libfuzzer_sys;
use keystore2::legacy_blob::LegacyBlobLoader;

fuzz_target!(|data: &[u8]| {
    if !data.is_empty() {
        let string = data.iter().filter_map(|c| std::char::from_u32(*c as u32)).collect::<String>();
        let _res = LegacyBlobLoader::decode_alias(&string);
    }
});
