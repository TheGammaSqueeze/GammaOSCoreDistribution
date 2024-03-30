// Copyright 2022, The Android Open Source Project
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

use std::env;
use std::process::Command;

fn main() {
    println!("cargo:rerun-if-changed=uci_packets.pdl");

    // Generate the rust code by bluetooth_packetgen.
    // The binary should be compiled by `m bluetooth_packetgen -j32` before calling cargo.
    let out_dir = env::var_os("OUT_DIR").unwrap();
    let output = Command::new("env")
        .arg("bluetooth_packetgen")
        .arg("--out=".to_owned() + out_dir.as_os_str().to_str().unwrap())
        .arg("--include=.")
        .arg("--rust")
        .arg("uci_packets.pdl")
        .output()
        .unwrap();

    eprintln!(
        "Status: {}, stdout: {}, stderr: {}",
        output.status,
        String::from_utf8_lossy(output.stdout.as_slice()),
        String::from_utf8_lossy(output.stderr.as_slice())
    );
}
